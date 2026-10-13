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

function expectData(response, predicate, message) {
  if (response?.code !== "0" || !predicate(response.data)) throw new Error(message);
  return response.data;
}

async function freePort() {
  const server = createTcpServer();
  await new Promise((resolve, reject) => server.once("error", reject).listen(0, "127.0.0.1", resolve));
  const port = server.address().port;
  await new Promise((resolve) => server.close(resolve));
  return port;
}

async function request(url, body) {
  const response = await fetch(url, {
    method: body === undefined ? "GET" : "POST",
    headers: { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(5000)
  });
  if (!response.ok) throw new Error(`Local HTTP ${response.status} at ${new URL(url).pathname}`);
  return response.json();
}

async function waitForIdentity(url, child, name, identity) {
  for (let attempt = 0; attempt < 90; attempt += 1) {
    if (child.exitCode !== null) throw new Error(`${name} exited during startup: ${child.exitCode}`);
    try {
      const response = await request(`${url}/api/demo/identity`);
      if (response?.code === "0" && response.data === identity) return;
      if (response?.code === "0") throw new Error(`${name} identity mismatch`);
    } catch (error) {
      if (error.message === `${name} identity mismatch`) throw error;
      await new Promise((resolve) => setTimeout(resolve, 1000));
    }
  }
  throw new Error(`${name} did not confirm its isolated H2 identity`);
}

function startBackend(name, directory, config, logPath, demo) {
  const options = ["-q", "-Dspring-boot.run.fork=false"];
  if (repository) options.push(`-Dmaven.repo.local=${repository}`);
  if (demo) options.push("-Pdemo");
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
  const directory = await mkdtemp(path.join(os.tmpdir(), "payment-gateway-submit-"));
  const upstreamCalls = [];
  const upstreamQueries = [];
  const upstream = createServer(async (incoming, outgoing) => {
    if (incoming.method !== "POST"
        || (incoming.url !== "/payment/submit" && incoming.url !== "/payment/query")) {
      outgoing.writeHead(404).end();
      return;
    }
    const chunks = [];
    for await (const chunk of incoming) chunks.push(chunk);
    const submitted = JSON.parse(Buffer.concat(chunks).toString("utf8"));
    if (incoming.url === "/payment/query") {
      upstreamQueries.push(submitted);
      outgoing.writeHead(200, { "Content-Type": "application/json" });
      outgoing.end(JSON.stringify({ code: "0", data: {
        paymentOrderId: upstreamQueries.length === 1
          ? upstreamCalls[0].paymentOrderId : submitted.paymentOrderId,
        orderNo: submitted.orderNo,
        tradeStatus: "SUCCESS",
        channelTransactionNo: upstreamQueries.length === 2 ? "LOCAL-TX-1" : "LOCAL-TX-2",
        querySource: "LOCAL_UPSTREAM_STUB"
      } }));
      return;
    }
    upstreamCalls.push(submitted);
    outgoing.writeHead(200, { "Content-Type": "application/json" });
    outgoing.end(JSON.stringify({ code: "0", data: {
      paymentOrderId: submitted.paymentOrderId,
      prepayOrderNo: submitted.prepayOrderNo,
      orderNo: submitted.orderNo,
      idempotencyKey: upstreamCalls.length === 2 ? upstreamCalls[0].idempotencyKey : submitted.idempotencyKey,
      channelTransactionNo: `LOCAL-TX-${upstreamCalls.length}`,
      attemptStatus: "处理中", attemptStatusType: "info", channelReturnCode: "USERPAYING"
    } }));
  });
  try {
    await new Promise((resolve, reject) => upstream.once("error", reject).listen(0, "127.0.0.1", resolve));
    const gatewayPort = await freePort();
    const paymentPort = await freePort();
    const accessKey = `local-${randomUUID()}`;
    const gatewayData = path.join(directory, "gateway.sql");
    await writeFile(gatewayData, "UPDATE t_gateway_certificate SET expire_at = DATEADD('DAY', 30, CURRENT_DATE) "
      + "WHERE certificate_code = 'CERT_WX_2026';\n");
    const gatewayUrl = `http://127.0.0.1:${gatewayPort}`;
    const paymentUrl = `http://127.0.0.1:${paymentPort}`;
    const gateway = startBackend("gateway-access", "systems/gateway-access/backend", {
      server: { address: "127.0.0.1", port: gatewayPort },
      spring: {
        profiles: { active: "local-payment-chain" },
        datasource: { url: "jdbc:h2:mem:gateway_access;"
          + "MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE" },
        sql: { init: { "data-locations": `classpath:data.sql,file:${gatewayData}` } }
      },
      gateway: { transaction: {
        "access-key": accessKey,
        channels: { wx_h5: {
          "payment-submit-url": `http://127.0.0.1:${upstream.address().port}/payment/submit`,
          "payment-query-url": `http://127.0.0.1:${upstream.address().port}/payment/query`
        } }
      } }
    }, path.join(directory, "gateway.log"), false);
    await waitForIdentity(gatewayUrl, gateway, "gateway-access", "gateway-access-h2-local-payment-chain");

    const payment = startBackend("payment-core", "systems/payment-core/backend", {
      server: { address: "127.0.0.1", port: paymentPort },
      spring: {
        profiles: { active: "demo" },
        datasource: {
          "driver-class-name": "org.h2.Driver",
          url: "jdbc:h2:mem:payment_core;"
            + "MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE"
        },
        sql: { init: {
          mode: "always", "schema-locations": "classpath:schema.sql,classpath:schema-demo.sql",
          "data-locations": "classpath:data.sql"
        } },
        rabbitmq: { listener: { simple: { "auto-startup": false }, direct: { "auto-startup": false } } }
      },
      payment: {
        scheduling: { enabled: false },
        "risk-control": { enabled: false },
        "ops-config": { enabled: false },
        submit: { "gateway-access": {
          enabled: true,
          "submit-url": `${gatewayUrl}/api/gateway/payment-orders/submit`,
          "supported-channels": "wx_h5", "access-key": accessKey
        } },
        "channel-query": { "gateway-access": {
          enabled: true,
          "query-url": `${gatewayUrl}/api/gateway/payment-orders/query`,
          "supported-channels": "wx_h5", "access-key": accessKey
        } }
      }
    }, path.join(directory, "payment.log"), true);
    await waitForIdentity(paymentUrl, payment, "payment-core", "payment-core-h2-demo");

    async function prepayAndSubmit(label) {
      const orderNo = `LOCAL-GATEWAY-${label}-${randomUUID()}`;
      const prepay = expectData(await request(`${paymentUrl}/api/payments/prepay`, {
        orderNo, payScene: "HOME_CLEAN", customerName: "local gateway submit",
        amount: 88, cashierTitle: "local gateway submit"
      }), (data) => data?.paymentStatus === "PREPAY_CREATED" && data?.paymentOrderId
        && data?.prepayOrderNo, `${label} prepay failed`);
      const payload = {
        prepayOrderNo: prepay.prepayOrderNo,
        paymentMethod: "微信支付", channelCode: "wx_h5",
        sourceAppId: "housekeeping-h5-web", merchantNo: "MCH_WX_10001",
        accessToken: "token-housekeeping-h5-web", terminal: "H5",
        idempotencyKey: orderNo
      };
      const response = await request(`${paymentUrl}/api/payments/submit`, payload);
      return { orderNo, prepay, payload, response };
    }

    const accepted = await prepayAndSubmit("accepted");
    expectData(accepted.response, (data) => data?.paymentOrderId === accepted.prepay.paymentOrderId
      && data?.channelTransactionNo === "LOCAL-TX-1", "Matching gateway submit did not bind the payment");
    const acceptedDetail = expectData(await request(`${paymentUrl}/api/payments/`
      + accepted.prepay.paymentOrderId),
    (data) => data?.channelTransactionNo === "LOCAL-TX-1" && data?.status === "WAIT_CALLBACK",
    "Accepted payment was not retained as waiting for callback");
    if (upstreamCalls.length !== 1 || upstreamCalls[0].paymentOrderId !== acceptedDetail.paymentOrderId
        || upstreamCalls[0].prepayOrderNo !== accepted.prepay.prepayOrderNo
        || upstreamCalls[0].orderNo !== accepted.orderNo
        || upstreamCalls[0].idempotencyKey !== accepted.payload.idempotencyKey) {
      throw new Error("Matching payment submit lost its request identity");
    }

    const mismatched = await prepayAndSubmit("mismatched");
    if (mismatched.response.code !== "PAYMENT-1048") {
      throw new Error(`Cross-attempt payment submit was not rejected: ${mismatched.response.code}`);
    }
    const pending = expectData(await request(`${paymentUrl}/api/payments/`
      + mismatched.prepay.paymentOrderId),
    (data) => !data?.channelTransactionNo && data?.latestAttemptStatus === "结果未知，待查单",
    "Cross-attempt acceptance was bound or did not remain unknown");
    if (pending.status === "SUCCESS" || upstreamCalls.length !== 2
        || upstreamCalls[1].paymentOrderId !== mismatched.prepay.paymentOrderId
        || upstreamCalls[1].idempotencyKey !== mismatched.payload.idempotencyKey) {
      throw new Error("Mismatched payment submit changed the original attempt");
    }
    const repeated = await request(`${paymentUrl}/api/payments/submit`, mismatched.payload);
    if (upstreamCalls.length !== 2 || (repeated.code === "0"
        && (repeated.data?.channelTransactionNo || repeated.data?.paymentStatus === "SUCCESS"))) {
      throw new Error("Unknown payment acceptance was blindly resubmitted or confirmed");
    }
    const queryRequest = { paymentOrderId: mismatched.prepay.paymentOrderId };
    const rejectedQuery = await request(`${paymentUrl}/api/payments/query`, queryRequest);
    if (rejectedQuery.code !== "PAYMENT-1026") {
      throw new Error("Cross-payment query receipt was accepted");
    }
    expectData(await request(`${paymentUrl}/api/payments/` + mismatched.prepay.paymentOrderId),
      (data) => data?.status === "WAIT_CALLBACK" && !data?.channelTransactionNo
        && data?.latestAttemptStatus === "结果未知，待查单",
      "Rejected query changed the original payment attempt");
    const collidingQuery = await request(`${paymentUrl}/api/payments/query`, queryRequest);
    if (collidingQuery.code !== "PAYMENT-1026") {
      throw new Error(`Another payment's channel transaction was not rejected safely: ${collidingQuery.code}`);
    }
    expectData(await request(`${paymentUrl}/api/payments/` + mismatched.prepay.paymentOrderId),
      (data) => data?.status === "WAIT_CALLBACK" && !data?.channelTransactionNo
        && data?.latestAttemptStatus === "结果未知，待查单",
      "Colliding query changed the original payment attempt");
    expectData(await request(`${paymentUrl}/api/payments/query`, queryRequest),
      (data) => data?.status === "SUCCESS" && data?.channelTransactionNo === "LOCAL-TX-2"
        && data?.latestAttemptStatus === "成功" && data?.latestAttemptStatusType === "success",
      "Original payment query did not recover the unknown submission");
    expectData(await request(`${paymentUrl}/api/payments/query`, queryRequest),
      (data) => data?.status === "SUCCESS" && data?.channelTransactionNo === "LOCAL-TX-2",
      "Terminal payment query was not idempotent");
    if (upstreamQueries.length !== 3 || upstreamCalls.length !== 2
        || upstreamQueries.some((item) => item.paymentOrderId !== mismatched.prepay.paymentOrderId
          || item.orderNo !== mismatched.orderNo || item.channelCode !== "wx_h5"
          || item.channelTransactionNo)) {
      throw new Error("Original payment query lost its identity or resubmitted upstream");
    }
    const audits = expectData(await request(`${gatewayUrl}/api/gateway-access/audit-logs?pageSize=20`),
      (data) => Array.isArray(data?.records), "Gateway submit audits missing");
    const submitAudits = audits.records.filter((item) => item.operationType === "PAYMENT_SUBMIT"
      && item.clientIp === "127.0.0.1");
    if (submitAudits.filter((item) => item.resultStatus === "SUCCESS").length !== 1
        || submitAudits.filter((item) => item.resultStatus === "UNKNOWN").length !== 1) {
      throw new Error("Gateway did not separate accepted and mismatched submit receipts");
    }
    const queryAudits = audits.records.filter((item) => item.operationType === "PAYMENT_QUERY"
      && item.clientIp === "127.0.0.1");
    if (queryAudits.filter((item) => item.resultStatus === "FAILED").length !== 1
        || queryAudits.filter((item) => item.resultStatus === "SUCCESS").length !== 2) {
      throw new Error("Gateway did not audit upstream query failures and accepted receipts");
    }
    console.log("[PASS] payment-core -> gateway-access -> local payment upstream: foreign query transaction rejected, original unknown submit recovered without blind retry");
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
