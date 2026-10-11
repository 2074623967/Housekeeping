import assert from "node:assert/strict";
import { createServer } from "node:http";
import { spawn } from "node:child_process";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

const scriptPath = fileURLToPath(new URL("./dlq-compensation-smoke.mjs", import.meta.url));

function startMockServer() {
  const tasks = new Map();
  const server = createServer(async (request, response) => {
    const chunks = [];
    for await (const chunk of request) {
      chunks.push(chunk);
    }
    const body = chunks.length ? JSON.parse(Buffer.concat(chunks).toString("utf8")) : null;
    const url = new URL(request.url, "http://127.0.0.1");
    const json = (status, payload) => {
      response.writeHead(status, { "Content-Type": "application/json" });
      response.end(JSON.stringify(payload));
    };

    if (url.pathname.endsWith("/publish")) {
      const messageId = body.properties.message_id;
      if (!String(body.payload).startsWith("{invalid-")) {
        const payload = JSON.parse(body.payload);
        assert.equal(payload.eventNo, messageId);
        assert.equal(payload.schemaVersion, "1");
        assert.equal(body.properties.headers.schemaVersion, "1");
      }
      const eventKind = body.properties.headers.eventType === "CLEARING_GENERATED"
        ? "clearing-generated" : "payment-success";
      const targetSystem = eventKind === "clearing-generated" ? "settlement" : "accounting";
      const taskNo = `TASK-${messageId}`;
      const replayRoutingKey = eventKind === "clearing-generated"
        ? "clearing.generated.settlement.replay.mock-a.v1"
        : "payment.success.accounting.replay.mock-a.v1";
      tasks.set(messageId, { taskNo, taskStatus: "PENDING_REVIEW", replayRoutingKey });
      json(200, { routed: true });
      return;
    }

    if (url.pathname === "/api/payment-dead-letter-tasks") {
      const messageId = url.searchParams.get("messageId");
      const task = tasks.get(messageId);
      json(200, { code: "0", data: { items: task ? [{ messageId, ...task }] : [] } });
      return;
    }

    const taskMatch = url.pathname.match(/\/api\/payment-dead-letter-tasks\/([^/]+)\/(ready-to-replay|replay|resolve-manually)$/);
    if (taskMatch) {
      const task = [...tasks.values()].find((item) => item.taskNo === taskMatch[1]);
      assert.ok(task, `unknown task ${taskMatch[1]}`);
      if (taskMatch[2] === "ready-to-replay") {
        task.taskStatus = "READY_TO_REPLAY";
      } else if (taskMatch[2] === "replay") {
        task.taskStatus = "REPLAYED";
        task.replayCount = 1;
      } else {
        task.taskStatus = "MANUAL_RESOLVED";
      }
      json(200, { code: "0", data: task });
      return;
    }

    if (url.pathname.endsWith("/accounting/events") || url.pathname.endsWith("/settlements/events")) {
      json(200, { code: "0", data: { items: [{ eventType: url.searchParams.get("eventType"), bizNo: url.searchParams.get("bizNo") }] } });
      return;
    }

    json(404, { code: "NOT_FOUND" });
  });
  return { server, tasks };
}

async function runSmoke(env, server) {
  const address = server.address();
  const baseUrl = `http://127.0.0.1:${address.port}`;
  const child = spawn(process.execPath, [scriptPath], {
    env: {
      ...process.env,
      ...env,
      HSP_PAYMENT_CORE_BASE_URL: baseUrl,
      HSP_ACCOUNTING_BASE_URL: baseUrl,
      HSP_SETTLEMENT_BASE_URL: baseUrl,
      HSP_RABBIT_MANAGEMENT_URL: `${baseUrl}/api`
    },
    stdio: ["ignore", "pipe", "pipe"]
  });
  const stdout = [];
  const stderr = [];
  child.stdout.on("data", (chunk) => stdout.push(chunk));
  child.stderr.on("data", (chunk) => stderr.push(chunk));
  const exitCode = await new Promise((resolve) => child.on("close", resolve));
  return {
    exitCode,
    stdout: Buffer.concat(stdout).toString("utf8"),
    stderr: Buffer.concat(stderr).toString("utf8")
  };
}

test("covers payment.success replay and manual resolution contracts", async (t) => {
  const { server } = startMockServer();
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  t.after(() => server.close());

  const result = await runSmoke({
    HSP_DLQ_SUFFIX: "mock-a",
    HSP_DLQ_EVENT_KIND: "payment-success",
    HSP_DLQ_TARGET_SYSTEM: "accounting",
    HSP_DLQ_INCLUDE_MANUAL: "true",
    HSP_DLQ_TIMEOUT_MS: "1000"
  }, server);

  assert.equal(result.exitCode, 0, result.stderr || result.stdout);
  assert.match(result.stdout, /current-batch DLQ compensation smoke/);
  assert.match(result.stdout, /MANUAL_RESOLVED/);
  assert.match(result.stdout, /REPLAYED/);
});

test("covers clearing.generated settlement replay contract", async (t) => {
  const { server } = startMockServer();
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  t.after(() => server.close());

  const result = await runSmoke({
    HSP_DLQ_SUFFIX: "mock-a",
    HSP_DLQ_EVENT_KIND: "clearing-generated",
    HSP_DLQ_TARGET_SYSTEM: "settlement",
    HSP_DLQ_TIMEOUT_MS: "1000"
  }, server);

  assert.equal(result.exitCode, 0, result.stderr || result.stdout);
  assert.match(result.stdout, /clearing-generated/);
  assert.match(result.stdout, /REPLAYED/);
});

test("uses the business id from a custom payment payload", async (t) => {
  const { server } = startMockServer();
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  t.after(() => server.close());

  const result = await runSmoke({
    HSP_DLQ_SUFFIX: "mock-a",
    HSP_DLQ_EVENT_KIND: "payment-success",
    HSP_DLQ_TARGET_SYSTEM: "accounting",
    HSP_DLQ_PAYLOAD_JSON: JSON.stringify({
      eventType: "PAYMENT_SUCCESS",
      paymentOrderId: "PAY-CUSTOM-PAYLOAD-001",
      orderNo: "ORD-CUSTOM-PAYLOAD-001",
      accountNo: "ACT10003",
      amount: 7.5
    }),
    HSP_DLQ_TIMEOUT_MS: "1000"
  }, server);

  assert.equal(result.exitCode, 0, result.stderr || result.stdout);
  assert.match(result.stdout, /PAY-CUSTOM-PAYLOAD-001/);
});

test("rejects a custom payload with a mismatched event type", async (t) => {
  const { server } = startMockServer();
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  t.after(() => server.close());

  const result = await runSmoke({
    HSP_DLQ_SUFFIX: "mock-a",
    HSP_DLQ_EVENT_KIND: "payment-success",
    HSP_DLQ_TARGET_SYSTEM: "accounting",
    HSP_DLQ_PAYLOAD_JSON: JSON.stringify({
      eventType: "CLEARING_GENERATED",
      paymentOrderId: "PAY-WRONG-EVENT-001"
    }),
    HSP_DLQ_TIMEOUT_MS: "1000"
  }, server);

  assert.notEqual(result.exitCode, 0);
  assert.match(result.stderr, /eventType must be PAYMENT_SUCCESS/);
});

test("rejects invalid timeout configuration before publishing", async (t) => {
  const { server } = startMockServer();
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  t.after(() => server.close());

  const result = await runSmoke({
    HSP_DLQ_SUFFIX: "mock-a",
    HSP_DLQ_EVENT_KIND: "payment-success",
    HSP_DLQ_TARGET_SYSTEM: "accounting",
    HSP_DLQ_TIMEOUT_MS: "not-a-number"
  }, server);

  assert.notEqual(result.exitCode, 0);
  assert.match(result.stderr, /HSP_DLQ_TIMEOUT_MS must be a positive number/);
});

test("reports malformed custom payload JSON clearly", async (t) => {
  const { server } = startMockServer();
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  t.after(() => server.close());

  const result = await runSmoke({
    HSP_DLQ_SUFFIX: "mock-a",
    HSP_DLQ_EVENT_KIND: "payment-success",
    HSP_DLQ_TARGET_SYSTEM: "accounting",
    HSP_DLQ_PAYLOAD_JSON: "{invalid-json"
  }, server);

  assert.notEqual(result.exitCode, 0);
  assert.match(result.stderr, /HSP_DLQ_PAYLOAD_JSON must be valid JSON/);
});

test("reports unavailable RabbitMQ management clearly", async () => {
  const result = await runSmoke({
    HSP_DLQ_SUFFIX: "unavailable-broker",
    HSP_DLQ_EVENT_KIND: "payment-success",
    HSP_DLQ_TARGET_SYSTEM: "accounting",
    HSP_RABBIT_MANAGEMENT_URL: "http://127.0.0.1:1/api",
    HSP_DLQ_TIMEOUT_MS: "1000"
  }, { address: () => ({ port: 1 }) });

  assert.notEqual(result.exitCode, 0);
  assert.match(result.stderr, /request failed/);
});

test("uses localhost defaults for IPv6-compatible local services", async () => {
  const source = await import("node:fs/promises").then((fs) =>
    fs.readFile(scriptPath, "utf8"));
  assert.match(source, /HSP_RABBIT_MANAGEMENT_URL \|\| "http:\/\/localhost:15672\/api"/);
  assert.match(source, /HSP_PAYMENT_CORE_BASE_URL \|\| "http:\/\/localhost:18081"/);
  assert.match(source, /HSP_ACCOUNTING_BASE_URL \|\| "http:\/\/localhost:18110"/);
  assert.match(source, /HSP_SETTLEMENT_BASE_URL \|\| "http:\/\/localhost:18130"/);
  assert.match(source, /HSP_CLEARING_BASE_URL \|\| "http:\/\/localhost:18120"/);
});
