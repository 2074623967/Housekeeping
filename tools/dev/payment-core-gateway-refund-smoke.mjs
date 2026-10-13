#!/usr/bin/env node

import { spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { closeSync, openSync } from "node:fs";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { createServer } from "node:http";
import { createServer as createTcpServer } from "node:net";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { assertLocalGatewaySmokeEnvironment } from "./local-gateway-smoke-guard.mjs";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const maven = process.env.HSP_MAVEN_BIN || "mvn";
const javaHome = process.env.HSP_JAVA_HOME || process.env.JAVA_HOME;
const repository = process.env.HSP_MAVEN_REPO;
const launched = [];

function assertResponse(response, predicate, message) {
  if (response?.code !== "0" || !predicate(response.data)) {
    throw new Error(message);
  }
  return response.data;
}

async function freePort() {
  const server = createTcpServer();
  await new Promise((resolve, reject) => server.once("error", reject).listen(0, "127.0.0.1", resolve));
  const port = server.address().port;
  await new Promise((resolve) => server.close(resolve));
  return port;
}

async function request(url, body, headers = {}) {
  const response = await fetch(url, {
    method: body === undefined ? "GET" : "POST",
    headers: { "Content-Type": "application/json", ...headers },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(5000)
  });
  if (!response.ok) {
    throw new Error(`Local HTTP ${response.status} at ${new URL(url).pathname}`);
  }
  return response.json();
}

async function waitFor(url, child, name) {
  for (let attempt = 0; attempt < 90; attempt += 1) {
    if (child.exitCode !== null) {
      throw new Error(`${name} exited during startup: ${child.exitCode}`);
    }
    try {
      if ((await request(url))?.code === "0") {
        return;
      }
    } catch {
      await new Promise((resolve) => setTimeout(resolve, 1000));
    }
  }
  throw new Error(`${name} did not become ready`);
}

function startBackend(name, directory, config, logPath, useDemoDependency) {
  const options = ["-q", "-Dspring-boot.run.fork=false"];
  if (repository) options.push(`-Dmaven.repo.local=${repository}`);
  if (useDemoDependency) options.push("-Pdemo");
  options.push("spring-boot:run");
  const logFd = openSync(logPath, "w");
  let child;
  try {
    child = spawn(maven, options, {
      cwd: path.join(root, directory),
      env: {
        ...process.env,
        ...(javaHome ? { JAVA_HOME: javaHome, PATH: `${javaHome}/bin:${process.env.PATH}` } : {}),
        SPRING_APPLICATION_JSON: JSON.stringify(config)
      },
      stdio: ["ignore", logFd, logFd]
    });
  } finally {
    closeSync(logFd);
  }
  launched.push({ name, child, logPath });
  return child;
}

async function stopBackends() {
  for (const { child } of launched) {
    if (child.exitCode === null) child.kill("SIGTERM");
  }
  await Promise.all(launched.map(async ({ child }) => {
    if (child.exitCode === null) {
      await Promise.race([
        new Promise((resolve) => child.once("exit", resolve)),
        new Promise((resolve) => setTimeout(resolve, 5000))
      ]);
    }
    if (child.exitCode === null) child.kill("SIGKILL");
  }));
}

async function main() {
  assertLocalGatewaySmokeEnvironment(process.env);
  const directory = await mkdtemp(path.join(os.tmpdir(), "payment-gateway-refund-"));
  const upstreamCalls = [];
  const upstreamQueries = [];
  const upstream = createServer(async (incoming, outgoing) => {
    if (incoming.method !== "POST"
        || (incoming.url !== "/refund/submit" && incoming.url !== "/refund/query")) {
      outgoing.writeHead(404).end();
      return;
    }
    const chunks = [];
    for await (const chunk of incoming) chunks.push(chunk);
    const requestBody = JSON.parse(Buffer.concat(chunks).toString("utf8"));
    if (incoming.url === "/refund/submit") {
      upstreamCalls.push(requestBody);
      if (upstreamCalls.length === 2) {
        outgoing.destroy();
        return;
      }
      outgoing.writeHead(200, { "Content-Type": "application/json" });
      outgoing.end(JSON.stringify({
        code: "0",
        data: upstreamCalls.length === 3
          ? { refundOrderId: requestBody.refundOrderId, refundAttemptNo: requestBody.refundAttemptNo,
            channelRefundNo: "LOCAL-UPSTREAM-REF-003", status: "SUCCESS", statusType: "success" }
          : upstreamCalls.length === 4
            ? { refundOrderId: requestBody.refundOrderId,
              refundAttemptNo: upstreamCalls[0].refundAttemptNo,
              channelRefundNo: "LOCAL-UPSTREAM-REF-004", status: "PROCESSING", statusType: "warn" }
          : { refundOrderId: requestBody.refundOrderId, refundAttemptNo: requestBody.refundAttemptNo,
            channelRefundNo: "LOCAL-UPSTREAM-REF-001", status: "PROCESSING", statusType: "warn" }
      }));
    } else {
      upstreamQueries.push(requestBody);
      const submitIndex = upstreamCalls.findIndex((item) => item.refundOrderId === requestBody.refundOrderId);
      const channelRefundNo = submitIndex === 0
        ? upstreamQueries.length === 1 ? "LOCAL-UPSTREAM-WRONG-REF" : "LOCAL-UPSTREAM-REF-001"
        : submitIndex === 1 ? "LOCAL-UPSTREAM-REF-002"
          : submitIndex === 2 ? "LOCAL-UPSTREAM-REF-003" : "LOCAL-UPSTREAM-REF-004";
      outgoing.writeHead(200, { "Content-Type": "application/json" });
      outgoing.end(JSON.stringify({
        code: "0",
        data: { refundOrderId: requestBody.refundOrderId,
          refundAttemptNo: submitIndex === 1 && upstreamQueries.length === 3
            ? upstreamCalls[0].refundAttemptNo : requestBody.refundAttemptNo,
          channelRefundNo, refundStatus: "SUCCESS",
          querySource: "LOCAL_UPSTREAM_STUB" }
      }));
    }
  });
  try {
    await new Promise((resolve, reject) => upstream.once("error", reject).listen(0, "127.0.0.1", resolve));
    const gatewayPort = await freePort();
    const paymentPort = await freePort();
    const accessKey = `local-${randomUUID()}`;
    const gatewayData = path.join(directory, "gateway.sql");
    const paymentData = path.join(directory, "payment.sql");
    await writeFile(gatewayData, "UPDATE t_gateway_certificate SET expire_at = '2030-01-01' "
      + "WHERE certificate_code = 'CERT_WX_2026';\n");
    await writeFile(paymentData, "UPDATE t_payment_order SET channel_code = 'wx_h5' "
      + "WHERE payment_order_id = 'PAY202607190001';\n"
      + "UPDATE t_payment_attempt SET channel_code = 'wx_h5', "
      + "completed_at = '2026-07-19 09:21:22' "
      + "WHERE payment_order_id = 'PAY202607190001';\n");
    const gatewayUrl = `http://127.0.0.1:${gatewayPort}`;
    const paymentUrl = `http://127.0.0.1:${paymentPort}`;
    const gateway = startBackend("gateway-access", "systems/gateway-access/backend", {
      server: { address: "127.0.0.1", port: gatewayPort },
      spring: {
        datasource: { url: `jdbc:h2:mem:refund_gateway_${randomUUID().replaceAll("-", "")};`
          + "MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE" },
        sql: { init: { "data-locations": `classpath:data.sql,file:${gatewayData}` } }
      },
      gateway: { transaction: {
        "access-key": accessKey,
        channels: { wx_h5: {
          "refund-submit-url": `http://127.0.0.1:${upstream.address().port}/refund/submit`,
          "refund-query-url": `http://127.0.0.1:${upstream.address().port}/refund/query`
        } }
      } }
    }, path.join(directory, "gateway.log"), false);
    await waitFor(`${gatewayUrl}/api/gateway-access/summary`, gateway, "gateway-access");

    const payment = startBackend("payment-core", "systems/payment-core/backend", {
      server: { address: "127.0.0.1", port: paymentPort },
      spring: {
        datasource: {
          "driver-class-name": "org.h2.Driver",
          url: `jdbc:h2:mem:refund_payment_${randomUUID().replaceAll("-", "")};`
            + "MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE"
        },
        sql: { init: {
          mode: "always",
          "schema-locations": "classpath:schema.sql,classpath:schema-demo.sql",
          "data-locations": `classpath:data.sql,file:${paymentData}`
        } },
        rabbitmq: { listener: { simple: { "auto-startup": false }, direct: { "auto-startup": false } } }
      },
      payment: {
        scheduling: { enabled: false },
        "refund-submit": { "gateway-access": {
          enabled: true,
          "submit-url": `${gatewayUrl}/api/gateway/refunds/submit`,
          "supported-channels": "wx_h5",
          "access-key": accessKey
        } },
        "refund-query": { "gateway-access": {
          enabled: true,
          "query-url": `${gatewayUrl}/api/gateway/refunds/query`,
          "supported-channels": "wx_h5",
          "access-key": accessKey
        } }
      }
    }, path.join(directory, "payment.log"), true);
    await waitFor(`${paymentUrl}/api/payment-events?pageSize=1`, payment, "payment-core");

    const application = assertResponse(await request(`${paymentUrl}/api/refunds/apply`, {
      paymentOrderId: "PAY202607190001",
      idempotencyKey: `gateway-smoke-${randomUUID()}`,
      refundAmount: 20,
      refundMethod: "原路退款",
      refundReason: "isolated gateway smoke",
      operator: "local-smoke"
    }), (data) => data?.status === "REVIEWING" && data?.refundOrderId, "Refund application failed");
    assertResponse(await request(`${paymentUrl}/api/refunds/approve`, {
      refundOrderId: application.refundOrderId, operator: "local-smoke"
    }), (data) => data?.status === "PROCESSING", "Refund approval failed");
    const eventPage = assertResponse(await request(`${paymentUrl}/api/payment-events?`
      + "paymentOrderId=PAY202607190001&eventType=REFUND_SUBMIT"),
    (data) => data?.total === 1 && data?.items?.length === 1, "Refund outbox missing");
    const event = eventPage.items[0];
    if (event.publishStatus !== "SUCCESS" || upstreamCalls.length !== 1
        || upstreamCalls[0].refundOrderId !== application.refundOrderId
        || upstreamCalls[0].refundAttemptNo !== event.bizNo) {
      throw new Error("Gateway dispatch did not preserve the refund attempt and accepted event");
    }
    const auditPage = assertResponse(await request(`${gatewayUrl}/api/gateway-access/audit-logs?`
      + "resultStatus=SUCCESS&pageSize=20"),
    (data) => Array.isArray(data?.records), "Gateway audit listing failed");
    if (!auditPage.records.some((item) => item.operationType === "REFUND_SUBMIT"
        && item.resultStatus === "SUCCESS" && item.clientIp === "127.0.0.1")) {
      throw new Error("Gateway did not audit the refund submit acceptance");
    }
    const queryRequest = { refundOrderId: application.refundOrderId, operator: "local-smoke" };
    const rejectedQuery = await request(`${paymentUrl}/api/refunds/query`, queryRequest);
    if (rejectedQuery.code !== "PAYMENT-1046") {
      throw new Error("Mismatched upstream refund identity was not rejected");
    }
    const pending = assertResponse(await request(`${paymentUrl}/api/refunds/${application.refundOrderId}`),
      (data) => data?.status === "PROCESSING" && Array.isArray(data?.operationLogs),
      "Rejected query changed refund state");
    if (!pending.operationLogs.some((item) => item.actionCode === "CHANNEL_QUERY_FAILED")) {
      throw new Error("Rejected query did not record an operator-visible failure");
    }
    const failedAudits = assertResponse(await request(`${gatewayUrl}/api/gateway-access/audit-logs?`
      + "resultStatus=FAILED"),
    (data) => Array.isArray(data?.records), "Gateway failed audit listing failed");
    if (!failedAudits.records.some((item) => item.operationType === "REFUND_QUERY"
        && item.resultStatus === "FAILED" && item.clientIp === "127.0.0.1")) {
      throw new Error("Gateway did not audit the invalid upstream refund result");
    }
    assertResponse(await request(`${paymentUrl}/api/refunds/query`, queryRequest),
      (data) => data?.status === "SUCCESS", "Refund query did not close the payment-core order");
    assertResponse(await request(`${paymentUrl}/api/refunds/query`, queryRequest),
      (data) => data?.status === "SUCCESS", "Terminal refund query was not idempotent");
    if (upstreamQueries.length !== 2
        || upstreamQueries.some((item) => item.refundOrderId !== application.refundOrderId
          || item.refundAttemptNo !== event.bizNo
          || item.channelRefundNo !== "LOCAL-UPSTREAM-REF-001")
        || upstreamCalls.length !== 1) {
      throw new Error("Refund query did not preserve attempt identity or terminal idempotency");
    }
    const queryAudits = assertResponse(await request(`${gatewayUrl}/api/gateway-access/audit-logs?`
      + "resultStatus=SUCCESS"),
    (data) => Array.isArray(data?.records), "Gateway query audit listing failed");
    if (!queryAudits.records.some((item) => item.operationType === "REFUND_QUERY"
        && item.resultStatus === "SUCCESS" && item.clientIp === "127.0.0.1")) {
      throw new Error("Gateway did not audit the refund query");
    }

    const unknownApplication = assertResponse(await request(`${paymentUrl}/api/refunds/apply`, {
      paymentOrderId: "PAY202607190001",
      idempotencyKey: `gateway-unknown-${randomUUID()}`,
      refundAmount: 20,
      refundMethod: "原路退款",
      refundReason: "isolated unknown result smoke",
      operator: "local-smoke"
    }), (data) => data?.status === "REVIEWING" && data?.refundOrderId,
    "Unknown-result refund application failed");
    assertResponse(await request(`${paymentUrl}/api/refunds/approve`, {
      refundOrderId: unknownApplication.refundOrderId, operator: "local-smoke"
    }), (data) => data?.status === "PROCESSING", "Unknown-result refund approval failed");
    const unknownDetail = assertResponse(await request(`${paymentUrl}/api/refunds/`
      + unknownApplication.refundOrderId),
    (data) => data?.status === "PROCESSING" && data?.attempts?.length === 1
      && data.attempts[0].attemptStatus === "UNKNOWN", "Unknown submit attempt was not preserved");
    const unknownAttemptNo = unknownDetail.attempts[0].refundAttemptNo;
    const unknownEvents = assertResponse(await request(`${paymentUrl}/api/payment-events?`
      + "paymentOrderId=PAY202607190001&eventType=REFUND_SUBMIT"),
    (data) => data?.total === 2 && data?.items?.length === 2, "Unknown refund outbox missing");
    const unknownEvent = unknownEvents.items.find((item) => item.bizNo === unknownAttemptNo);
    if (unknownEvent?.publishStatus !== "UNKNOWN" || upstreamCalls.length !== 2
        || upstreamCalls[1].refundOrderId !== unknownApplication.refundOrderId
        || upstreamCalls[1].refundAttemptNo !== unknownAttemptNo) {
      throw new Error("Unknown gateway result was not tied to the second attempt");
    }
    const unknownAudits = assertResponse(await request(`${gatewayUrl}/api/gateway-access/audit-logs?`
      + "resultStatus=UNKNOWN"),
    (data) => Array.isArray(data?.records), "Gateway unknown audit listing failed");
    if (!unknownAudits.records.some((item) => item.operationType === "REFUND_SUBMIT"
        && item.resultStatus === "UNKNOWN" && item.clientIp === "127.0.0.1")) {
      throw new Error("Gateway did not audit the unknown upstream refund submit");
    }
    const republish = await request(`${paymentUrl}/api/payment-events/republish`, {
      eventNo: unknownEvent.eventNo, operator: "local-smoke"
    });
    if (republish.code !== "PAYMENT-1050" || upstreamCalls.length !== 2) {
      throw new Error("Unknown refund submit was blindly retried");
    }
    const unknownQueryRequest = { refundOrderId: unknownApplication.refundOrderId, operator: "local-smoke" };
    const wrongAttemptQuery = await request(`${paymentUrl}/api/refunds/query`, unknownQueryRequest);
    if (wrongAttemptQuery.code !== "PAYMENT-1046") {
      throw new Error("Another refund attempt's result was accepted without a channel refund number");
    }
    const stillUnknown = assertResponse(await request(`${paymentUrl}/api/refunds/`
      + unknownApplication.refundOrderId),
    (data) => data?.status === "PROCESSING" && data?.attempts?.length === 1
      && data.attempts[0].attemptStatus === "UNKNOWN" && !data.channelRefundNo,
    "Cross-attempt query changed an unknown refund state");
    if (!stillUnknown.operationLogs?.some((item) => item.actionCode === "CHANNEL_QUERY_FAILED")) {
      throw new Error("Cross-attempt rejection was not visible to the operator");
    }
    assertResponse(await request(`${paymentUrl}/api/refunds/query`, unknownQueryRequest),
      (data) => data?.status === "SUCCESS", "Unknown refund query did not recover acceptance");
    assertResponse(await request(`${paymentUrl}/api/refunds/query`, unknownQueryRequest),
      (data) => data?.status === "SUCCESS", "Recovered terminal query was not idempotent");
    const recovered = assertResponse(await request(`${paymentUrl}/api/refunds/`
      + unknownApplication.refundOrderId),
    (data) => data?.status === "SUCCESS" && data?.attempts?.length === 1
      && data.attempts[0].attemptStatus === "SUCCESS", "Unknown refund recovery was not atomic");
    if (!recovered.operationLogs?.some((item) => item.actionCode === "CHANNEL_ACCEPTANCE_RECOVERED")
        || upstreamCalls.length !== 2 || upstreamQueries.length !== 4
        || upstreamQueries.slice(2).some((item) => item.refundAttemptNo !== unknownAttemptNo
          || item.channelRefundNo)) {
      throw new Error("Unknown refund acceptance recovery lost its attempt identity");
    }
    const recoveredEvent = assertResponse(await request(`${paymentUrl}/api/payment-events/`
      + unknownEvent.eventNo), (data) => data?.publishStatus === "SUCCESS",
    "Unknown refund outbox was not recovered");
    if (recoveredEvent.bizNo !== unknownAttemptNo) {
      throw new Error("Recovered outbox changed refund attempt identity");
    }

    const synchronousApplication = assertResponse(await request(`${paymentUrl}/api/refunds/apply`, {
      paymentOrderId: "PAY202607190001",
      idempotencyKey: `gateway-synchronous-${randomUUID()}`,
      refundAmount: 20,
      refundMethod: "原路退款",
      refundReason: "isolated synchronous success smoke",
      operator: "local-smoke"
    }), (data) => data?.status === "REVIEWING" && data?.refundOrderId,
    "Synchronous-result refund application failed");
    assertResponse(await request(`${paymentUrl}/api/refunds/approve`, {
      refundOrderId: synchronousApplication.refundOrderId, operator: "local-smoke"
    }), (data) => data?.status === "PROCESSING", "Synchronous success was prematurely finalized");
    const synchronousDetail = assertResponse(await request(`${paymentUrl}/api/refunds/`
      + synchronousApplication.refundOrderId),
    (data) => data?.status === "PROCESSING" && data?.attempts?.length === 1
      && data.attempts[0].attemptStatus === "UNKNOWN" && !data.channelRefundNo,
    "Synchronous success did not preserve an unbound unknown attempt");
    const synchronousAttemptNo = synchronousDetail.attempts[0].refundAttemptNo;
    const synchronousEvents = assertResponse(await request(`${paymentUrl}/api/payment-events?`
      + "paymentOrderId=PAY202607190001&eventType=REFUND_SUBMIT"),
    (data) => data?.total === 3 && data?.items?.length === 3,
    "Synchronous success refund outbox missing");
    const synchronousEvent = synchronousEvents.items.find((item) => item.bizNo === synchronousAttemptNo);
    if (synchronousEvent?.publishStatus !== "UNKNOWN" || upstreamCalls.length !== 3
        || upstreamCalls[2].refundOrderId !== synchronousApplication.refundOrderId
        || upstreamCalls[2].refundAttemptNo !== synchronousAttemptNo) {
      throw new Error("Synchronous success was accepted without querying the original attempt");
    }
    const synchronousRepublish = await request(`${paymentUrl}/api/payment-events/republish`, {
      eventNo: synchronousEvent.eventNo, operator: "local-smoke"
    });
    if (synchronousRepublish.code !== "PAYMENT-1050" || upstreamCalls.length !== 3) {
      throw new Error("Synchronous success was blindly retried");
    }
    assertResponse(await request(`${paymentUrl}/api/refunds/query`, {
      refundOrderId: synchronousApplication.refundOrderId, operator: "local-smoke"
    }), (data) => data?.status === "SUCCESS", "Synchronous success query did not close the original attempt");
    assertResponse(await request(`${paymentUrl}/api/refunds/query`, {
      refundOrderId: synchronousApplication.refundOrderId, operator: "local-smoke"
    }), (data) => data?.status === "SUCCESS", "Synchronous terminal query was not idempotent");
    const synchronousSettled = assertResponse(await request(`${paymentUrl}/api/refunds/`
      + synchronousApplication.refundOrderId),
    (data) => data?.status === "SUCCESS" && data?.channelRefundNo === "LOCAL-UPSTREAM-REF-003"
      && data?.attempts?.length === 1 && data.attempts[0].attemptStatus === "SUCCESS",
    "Synchronous refund query did not bind the original attempt");
    const synchronousRecoveredEvent = assertResponse(await request(`${paymentUrl}/api/payment-events/`
      + synchronousEvent.eventNo), (data) => data?.publishStatus === "SUCCESS",
    "Synchronous refund outbox was not recovered");
    if (synchronousRecoveredEvent.bizNo !== synchronousAttemptNo
        || !synchronousSettled.operationLogs?.some((item) => item.actionCode === "CHANNEL_ACCEPTANCE_RECOVERED")
        || upstreamCalls.length !== 3 || upstreamQueries.length !== 5
        || upstreamQueries[4].refundAttemptNo !== synchronousAttemptNo
        || upstreamQueries[4].channelRefundNo) {
      throw new Error("Synchronous refund recovery lost its attempt identity");
    }
    const mismatchedApplication = assertResponse(await request(`${paymentUrl}/api/refunds/apply`, {
      paymentOrderId: "PAY202607190001",
      idempotencyKey: `gateway-mismatched-submit-${randomUUID()}`,
      refundAmount: 20,
      refundMethod: "原路退款",
      refundReason: "isolated cross-attempt submit smoke",
      operator: "local-smoke"
    }), (data) => data?.status === "REVIEWING" && data?.refundOrderId,
    "Mismatched submit refund application failed");
    assertResponse(await request(`${paymentUrl}/api/refunds/approve`, {
      refundOrderId: mismatchedApplication.refundOrderId, operator: "local-smoke"
    }), (data) => data?.status === "PROCESSING", "Mismatched submit approval failed");
    const mismatchedDetail = assertResponse(await request(`${paymentUrl}/api/refunds/`
      + mismatchedApplication.refundOrderId),
    (data) => data?.status === "PROCESSING" && data?.attempts?.length === 1
      && data.attempts[0].attemptStatus === "UNKNOWN" && !data.channelRefundNo,
    "Another attempt's submit acceptance was bound to this refund");
    const mismatchedAttemptNo = mismatchedDetail.attempts[0].refundAttemptNo;
    const mismatchedEvents = assertResponse(await request(`${paymentUrl}/api/payment-events?`
      + "paymentOrderId=PAY202607190001&eventType=REFUND_SUBMIT"),
    (data) => data?.total === 4 && data?.items?.length === 4,
    "Mismatched submit event missing");
    const mismatchedEvent = mismatchedEvents.items.find((item) => item.bizNo === mismatchedAttemptNo);
    if (mismatchedEvent?.publishStatus !== "UNKNOWN" || upstreamCalls.length !== 4
        || upstreamCalls[3].refundAttemptNo !== mismatchedAttemptNo) {
      throw new Error("Cross-attempt upstream acceptance was not quarantined");
    }
    assertResponse(await request(`${paymentUrl}/api/refunds/query`, {
      refundOrderId: mismatchedApplication.refundOrderId, operator: "local-smoke"
    }), (data) => data?.status === "SUCCESS", "Original attempt query did not recover the refund");
    const mismatchedRecovered = assertResponse(await request(`${paymentUrl}/api/refunds/`
      + mismatchedApplication.refundOrderId),
    (data) => data?.status === "SUCCESS" && data?.attempts?.length === 1
      && data.attempts[0].attemptStatus === "SUCCESS" && data.channelRefundNo === "LOCAL-UPSTREAM-REF-004",
    "Cross-attempt submit recovery did not preserve the original attempt");
    if (!mismatchedRecovered.operationLogs?.some((item) => item.actionCode === "CHANNEL_ACCEPTANCE_RECOVERED")
        || upstreamQueries.length !== 6 || upstreamQueries[5].refundAttemptNo !== mismatchedAttemptNo
        || upstreamQueries[5].channelRefundNo || upstreamCalls.length !== 4) {
      throw new Error("Cross-attempt submit recovery lost its unknown-result boundary");
    }
    console.log("[PASS] payment-core -> gateway-access -> local upstream: reject mismatched submit/query receipts and recover original attempts without blind retry");
  } catch (error) {
    for (const { name, logPath } of launched) {
      const log = await readFile(logPath, "utf8").catch(() => "");
      console.error(`[${name}] ${log.split("\n").slice(-18).join("\n")}`);
    }
    throw error;
  } finally {
    await stopBackends();
    await new Promise((resolve) => upstream.close(resolve));
    await rm(directory, { recursive: true, force: true });
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
