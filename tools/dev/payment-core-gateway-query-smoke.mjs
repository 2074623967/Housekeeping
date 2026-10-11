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

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const maven = process.env.HSP_MAVEN_BIN || "mvn";
const javaHome = process.env.HSP_JAVA_HOME || process.env.JAVA_HOME;
const repository = process.env.HSP_MAVEN_REPO;
const launched = [];
const paymentOrderId = "PAY202607190002";
const conflictingLateQuery = process.env.HSP_QUERY_RACE_MODE === "conflicting";

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

async function request(url, body, headers = {}) {
  const response = await fetch(url, {
    method: body === undefined ? "GET" : "POST",
    headers: { "Content-Type": "application/json", ...headers },
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
  const directory = await mkdtemp(path.join(os.tmpdir(), "payment-gateway-query-"));
  const upstreamQueries = [];
  const downstreamEvents = [];
  let signalSuccessQuery;
  let releaseSuccessQuery;
  const successQueryArrived = new Promise((resolve) => { signalSuccessQuery = resolve; });
  const upstream = createServer(async (incoming, outgoing) => {
    if (incoming.method !== "POST" || !["/payment/query", "/clearing", "/accounting"].includes(incoming.url)) {
      outgoing.writeHead(404).end();
      return;
    }
    const chunks = [];
    for await (const chunk of incoming) chunks.push(chunk);
    const body = JSON.parse(Buffer.concat(chunks).toString("utf8"));
    outgoing.writeHead(200, { "Content-Type": "application/json" });
    if (incoming.url === "/payment/query") {
      upstreamQueries.push(body);
      if (upstreamQueries.length === 3) {
        signalSuccessQuery();
        await new Promise((resolve) => { releaseSuccessQuery = resolve; });
      }
      const tradeStatus = upstreamQueries.length === 1 ? "PAID" : "SUCCESS";
      const channelTransactionNo = upstreamQueries.length === 2
        || (conflictingLateQuery && upstreamQueries.length === 3) ? "ALI-WRONG" : "ALI77665544";
      outgoing.end(JSON.stringify({ code: "0", data: {
        tradeStatus, channelTransactionNo, querySource: "LOCAL_UPSTREAM_STUB"
      } }));
      return;
    }
    downstreamEvents.push({ path: incoming.url, body });
    outgoing.end(JSON.stringify({ code: "0", data: {
      eventType: body.eventType, bizNo: body.paymentOrderId, businessChainId: body.businessChainId,
      eventNo: body.eventNo, eventStatus: "已消费"
    } }));
  });
  try {
    await new Promise((resolve, reject) => upstream.once("error", reject).listen(0, "127.0.0.1", resolve));
    const gatewayPort = await freePort();
    const paymentPort = await freePort();
    const accessKey = `local-${randomUUID()}`;
    const gatewayData = path.join(directory, "gateway.sql");
    await writeFile(gatewayData, "UPDATE t_gateway_certificate SET expire_at = "
      + "DATEADD('DAY', 30, CURRENT_DATE) "
      + "WHERE certificate_code = 'CERT_ALI_2026';\n");
    const gatewayUrl = `http://127.0.0.1:${gatewayPort}`;
    const paymentUrl = `http://127.0.0.1:${paymentPort}`;
    const upstreamUrl = `http://127.0.0.1:${upstream.address().port}`;

    const gateway = startBackend("gateway-access", "systems/gateway-access/backend", {
      server: { address: "127.0.0.1", port: gatewayPort },
      spring: {
        profiles: { active: "local-payment-chain" },
        datasource: {
          "driver-class-name": "org.h2.Driver",
          url: "jdbc:h2:mem:gateway_access;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
          username: "sa", password: ""
        },
        sql: { init: { "data-locations": `classpath:data.sql,file:${gatewayData}` } }
      },
      gateway: { transaction: {
        "access-key": accessKey,
        environment: "GRAY",
        channels: { alipay_h5: { "payment-query-url": `${upstreamUrl}/payment/query` } }
      } }
    }, path.join(directory, "gateway.log"), false);
    await waitForIdentity(gatewayUrl, gateway, "gateway-access", "gateway-access-h2-local-payment-chain");

    const payment = startBackend("payment-core", "systems/payment-core/backend", {
      server: { address: "127.0.0.1", port: paymentPort },
      spring: {
        profiles: { active: "demo" },
        datasource: {
          "driver-class-name": "org.h2.Driver",
          url: "jdbc:h2:mem:payment_core;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE",
          username: "sa", password: ""
        },
        sql: { init: {
          mode: "always",
          "schema-locations": "classpath:schema.sql,classpath:schema-demo.sql",
          "data-locations": "classpath:data.sql"
        } },
        rabbitmq: { listener: { simple: { "auto-startup": false }, direct: { "auto-startup": false } } }
      },
      payment: {
        scheduling: { enabled: false },
        "event-dispatch": { mode: "http" },
        "risk-control": { enabled: false },
        "ops-config": { enabled: false },
        "channel-query": { "gateway-access": {
          enabled: true,
          "query-url": `${gatewayUrl}/api/gateway/payment-orders/query`,
          "supported-channels": "alipay_h5",
          "access-key": accessKey
        } },
        downstream: {
          clearing: { "payment-success-url": `${upstreamUrl}/clearing` },
          accounting: { "payment-success-url": `${upstreamUrl}/accounting` },
          reconciliation: { "fact-url": "" }
        }
      }
    }, path.join(directory, "payment.log"), true);
    await waitForIdentity(paymentUrl, payment, "payment-core", "payment-core-h2-demo");

    const query = { paymentOrderId, operator: "local-query-smoke" };
    expectData(await request(`${paymentUrl}/api/payments/${paymentOrderId}`),
      (data) => data?.status === "WAIT_CALLBACK", "Seed payment is not waiting for callback");
    for (let attempt = 1; attempt <= 2; attempt += 1) {
      const rejected = await request(`${paymentUrl}/api/payments/admin/query`, query);
      if (rejected.code !== "PAYMENT-1026") throw new Error(`Unexpected query rejection: ${rejected.code}`);
      expectData(await request(`${paymentUrl}/api/payments/${paymentOrderId}`),
        (data) => data?.status === "WAIT_CALLBACK", "Rejected gateway result changed payment state");
      if (upstreamQueries.length !== attempt || downstreamEvents.length !== 0) {
        throw new Error("Rejected query triggered an unexpected upstream or downstream call");
      }
    }
    const rejectedLogs = expectData(await request(`${paymentUrl}/api/payment-logs?`
      + `paymentOrderId=${paymentOrderId}&processStage=${encodeURIComponent("主动查单")}`),
    (data) => Array.isArray(data?.items), "Failed query audit listing failed");
    if (rejectedLogs.items.filter((item) => item.message?.includes("QUERY_FAILED")).length !== 2) {
      throw new Error("Rejected queries did not persist two payment-core failure audits");
    }
    const pendingSuccessQuery = request(`${paymentUrl}/api/payments/admin/query`, query)
      .then((response) => ({ response }), (error) => ({ error }));
    await Promise.race([
      successQueryArrived,
      new Promise((_, reject) => setTimeout(() => reject(new Error("Gateway query did not reach upstream")), 5000))
    ]);
    const callback = expectData(await request(`${paymentUrl}/api/payments/callback/alipay_h5`, {
      paymentOrderId, channelTransactionNo: "ALI77665544", tradeStatus: "SUCCESS"
    }), (data) => data?.status === "SUCCESS", "Callback did not win the pending gateway query");
    releaseSuccessQuery();
    const queryOutcome = await pendingSuccessQuery;
    if (queryOutcome.error) throw queryOutcome.error;
    if (conflictingLateQuery) {
      if (queryOutcome.response?.code !== "PAYMENT-1026" || queryOutcome.response?.data != null) {
        throw new Error("Conflicting delayed gateway query was not rejected after callback");
      }
      const failedLogs = expectData(await request(`${paymentUrl}/api/payment-logs?`
        + `paymentOrderId=${paymentOrderId}&processStage=${encodeURIComponent("主动查单")}`),
      (data) => Array.isArray(data?.items), "Late query failure audit listing failed");
      if (failedLogs.items.filter((item) => item.message?.includes("QUERY_FAILED")).length !== 3) {
        throw new Error("Conflicting late query did not retain its failure audit");
      }
    } else {
      const settled = expectData(queryOutcome.response,
        (data) => data?.status === "SUCCESS" && data?.channelTransactionNo === "ALI77665544"
          && data?.querySource === "LOCAL_UPSTREAM_STUB", "Delayed gateway query did not return callback winner");
      if (callback.paymentOrderId !== settled.paymentOrderId) {
        throw new Error("Callback and query did not return the same payment winner");
      }
    }
    expectData(await request(`${paymentUrl}/api/payments/admin/query`, query),
      (data) => data?.status === "SUCCESS", "Terminal query was not idempotent");
    expectData(await request(`${paymentUrl}/api/payments/callback/alipay_h5`, {
      paymentOrderId, channelTransactionNo: "ALI77665544", tradeStatus: "SUCCESS"
    }), (data) => data?.status === "SUCCESS", "Callback replay did not retain terminal payment");
    expectData(await request(`${paymentUrl}/api/payments/cashier/PRE202607190002?terminal=H5_WEB`),
      (data) => data?.paymentStatus === "SUCCESS" && data?.latestAttemptStatus === "成功",
      "Gateway query did not settle the bound prepay and attempt facts");
    expectData(await request(`${paymentUrl}/api/payment-requests/ATT202607190002`),
      (data) => data?.requestStatus === "成功" && data?.completedAt,
      "Gateway query did not complete the original payment attempt");
    if (upstreamQueries.length !== 3 || upstreamQueries.some((item) => item.paymentOrderId !== paymentOrderId
        || item.orderNo !== "ORD202607190002" || item.channelCode !== "alipay_h5"
        || item.channelTransactionNo !== "ALI77665544")) {
      throw new Error("Query did not preserve the bound transaction across gateway and upstream");
    }
    const audits = expectData(await request(`${gatewayUrl}/api/gateway-access/audit-logs?pageSize=20`),
      (data) => Array.isArray(data?.records), "Gateway query audit listing failed");
    const queryAudits = audits.records.filter((item) => item.operationType === "PAYMENT_QUERY"
      && item.clientIp === "127.0.0.1");
    if (queryAudits.filter((item) => item.resultStatus === "FAILED").length !== 1
        || queryAudits.filter((item) => item.resultStatus === "SUCCESS").length !== 2) {
      throw new Error("Gateway did not audit rejected and accepted upstream facts distinctly");
    }
    const eventPage = expectData(await request(`${paymentUrl}/api/payment-events?`
      + `paymentOrderId=${paymentOrderId}&eventType=PAYMENT_SUCCESS`),
    (data) => data?.total === 1 && data?.items?.length === 1, "Payment success outbox was not unique");
    if (eventPage.items[0].paymentSuccessHttpStage !== "COMPLETED"
        || eventPage.items[0].publishStatus !== "SUCCESS"
        || downstreamEvents.length !== 2
        || downstreamEvents[0].path !== "/clearing" || downstreamEvents[1].path !== "/accounting"
        || downstreamEvents.some((item) => item.body.paymentOrderId !== paymentOrderId)) {
      throw new Error("Success event did not reach only the controlled local downstream stubs");
    }
    expectData(await request(`${paymentUrl}/api/bills/BILL202607190002`),
      (data) => data?.billStatus === "已结清", "Queried payment did not settle the bill");
    console.log(`[PASS] payment-core -> gateway-access -> local query upstream raced callback for ${callback.paymentOrderId}; `
      + `${conflictingLateQuery ? "late conflicting query rejected" : "matching callback winner retained"}, `
      + "one local success event delivered");
  } catch (error) {
    for (const { name, logPath } of launched) {
      const log = await readFile(logPath, "utf8").catch(() => "");
      console.error(`[${name}] ${log.split("\n").slice(-18).join("\n")}`);
    }
    throw error;
  } finally {
    if (releaseSuccessQuery) releaseSuccessQuery();
    await stopBackends();
    await new Promise((resolve) => upstream.close(resolve));
    await rm(directory, { recursive: true, force: true });
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
