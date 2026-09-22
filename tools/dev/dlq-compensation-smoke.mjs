#!/usr/bin/env node

/**
 * Run a current-batch payment.success.* DLQ -> task -> replay smoke.
 *
 * The script intentionally requires an isolated RabbitMQ suffix. It never
 * falls back to historical task numbers or the default shared topology.
 */

const suffix = requiredEnv("HSP_DLQ_SUFFIX");
const managementBaseUrl = process.env.HSP_RABBIT_MANAGEMENT_URL || "http://127.0.0.1:15672/api";
const managementUser = process.env.HSP_RABBIT_MANAGEMENT_USER || "hsp";
const managementPassword = process.env.HSP_RABBIT_MANAGEMENT_PASSWORD || "hsp-local-drill-20260801";
const paymentCoreBaseUrl = process.env.HSP_PAYMENT_CORE_BASE_URL || "http://127.0.0.1:18081";
const accountingBaseUrl = process.env.HSP_ACCOUNTING_BASE_URL || "http://127.0.0.1:18110";
const targetSystem = process.env.HSP_DLQ_TARGET_SYSTEM || "accounting";
const timeoutMs = Number(process.env.HSP_DLQ_TIMEOUT_MS || 30000);
const pollMs = Number(process.env.HSP_DLQ_POLL_MS || 500);

const targetConfig = {
  accounting: {
    routingKey: `payment.success.accounting.dlq.${suffix}.v1`,
    replayRoutingKey: `payment.success.accounting.replay.${suffix}.v1`,
    downstreamBaseUrl: accountingBaseUrl,
    eventType: "PAYMENT_SUCCESS"
  },
  clearing: {
    routingKey: `payment.success.clearing.dlq.${suffix}.v1`,
    replayRoutingKey: `payment.success.clearing.replay.${suffix}.v1`,
    downstreamBaseUrl: process.env.HSP_CLEARING_BASE_URL || "http://127.0.0.1:18120",
    eventType: "PAYMENT_SUCCESS"
  }
};

const config = targetConfig[targetSystem];
if (!config) {
  throw new Error(`HSP_DLQ_TARGET_SYSTEM must be accounting or clearing, got ${targetSystem}`);
}

function requiredEnv(name) {
  const value = process.env[name];
  if (!value || !value.trim()) {
    throw new Error(`${name} is required; use an isolated RabbitMQ topology suffix`);
  }
  return value.trim();
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function basicAuth() {
  return `Basic ${Buffer.from(`${managementUser}:${managementPassword}`).toString("base64")}`;
}

async function requestJson(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {})
    }
  });
  const text = await response.text();
  let body = null;
  try {
    body = text ? JSON.parse(text) : null;
  } catch (error) {
    throw new Error(`${url} returned non-JSON response: ${text.slice(0, 160)}`);
  }
  assert(response.ok, `${url} returned HTTP ${response.status}: ${JSON.stringify(body)}`);
  return body;
}

async function paymentApi(path, options = {}) {
  const body = await requestJson(`${paymentCoreBaseUrl}${path}`, options);
  assert(body && body.code === "0", `${path} returned ${body && body.code}: ${body && body.message}`);
  return body.data;
}

async function publishDeadLetter(messageId, payload) {
  const result = await requestJson(
    `${managementBaseUrl}/exchanges/%2F/payment.trade.dlq/publish`,
    {
      method: "POST",
      headers: { Authorization: basicAuth() },
      body: JSON.stringify({
        properties: {
          message_id: messageId,
          correlation_id: messageId,
          content_type: "application/json",
          headers: {
            "x-retry-count": 3,
            eventType: "PAYMENT_SUCCESS"
          }
        },
        routing_key: config.routingKey,
        payload: JSON.stringify(payload),
        payload_encoding: "string"
      })
    }
  );
  assert(result.routed === true, `RabbitMQ did not route ${messageId}: ${JSON.stringify(result)}`);
  return result;
}

async function waitFor(description, action, predicate) {
  const deadline = Date.now() + timeoutMs;
  let latest;
  while (Date.now() < deadline) {
    latest = await action();
    if (predicate(latest)) {
      return latest;
    }
    await new Promise((resolve) => setTimeout(resolve, pollMs));
  }
  throw new Error(`${description} not ready within ${timeoutMs}ms: ${JSON.stringify(latest)}`);
}

function taskFromPage(page, messageId) {
  return page && Array.isArray(page.items)
    ? page.items.find((item) => item.messageId === messageId)
    : undefined;
}

async function replayCurrentBatch() {
  const runId = `${Date.now()}-${process.pid}`;
  const messageId = `DLQ-SMOKE-${runId}`;
  const defaultPaymentOrderId = `PAY-DLQ-SMOKE-${runId}`;
  const payload = process.env.HSP_DLQ_PAYLOAD_JSON
    ? JSON.parse(process.env.HSP_DLQ_PAYLOAD_JSON)
    : {
      eventType: "PAYMENT_SUCCESS",
      eventNo: `EVT-DLQ-SMOKE-${runId}`,
      paymentOrderId: defaultPaymentOrderId,
      orderNo: `ORD-DLQ-SMOKE-${runId}`,
      accountNo: "ACT10003",
      amount: 9.99
    };
  const paymentOrderId = String(payload.paymentOrderId || "").trim();
  assert(paymentOrderId, "DLQ payload must contain paymentOrderId");

  await publishDeadLetter(messageId, payload);
  const pending = await waitFor(
    "dead-letter task intake",
    () => paymentApi(`/api/payment-dead-letter-tasks?messageId=${encodeURIComponent(messageId)}&pageNo=1&pageSize=20`),
    (page) => Boolean(taskFromPage(page, messageId))
  );
  const task = taskFromPage(pending, messageId);
  assert(task.taskStatus === "PENDING_REVIEW", `unexpected initial task status: ${task.taskStatus}`);
  assert(task.replayRoutingKey === config.replayRoutingKey,
    `unexpected replay route: ${task.replayRoutingKey}`);

  const action = {
    operator: process.env.HSP_DLQ_OPERATOR || "支付运营A",
    resolutionNote: `当前批次 ${runId} 已核对业务字段，允许定向重放`
  };
  await paymentApi(`/api/payment-dead-letter-tasks/${encodeURIComponent(task.taskNo)}/ready-to-replay`, {
    method: "POST",
    body: JSON.stringify(action)
  });
  const replayed = await paymentApi(`/api/payment-dead-letter-tasks/${encodeURIComponent(task.taskNo)}/replay`, {
    method: "POST",
    body: JSON.stringify(action)
  });
  assert(replayed.taskStatus === "REPLAYED", `unexpected replay task status: ${replayed.taskStatus}`);
  assert(Number(replayed.replayCount) === 1, `unexpected replay count: ${replayed.replayCount}`);

  const downstream = await waitFor(
    "downstream event fact",
    () => requestJson(`${config.downstreamBaseUrl}/api/${targetSystem === "accounting" ? "accounting" : "clearing"}/events?eventType=${encodeURIComponent(config.eventType)}&bizNo=${encodeURIComponent(paymentOrderId)}&pageNo=1&pageSize=20`),
    (body) => body && body.code === "0" && body.data && Array.isArray(body.data.items) && body.data.items.length > 0
  );

  return {
    messageId,
    taskNo: task.taskNo,
    taskStatus: replayed.taskStatus,
    replayCount: replayed.replayCount,
    replayRoutingKey: replayed.replayRoutingKey,
    downstreamFactCount: downstream.data.items.length,
    targetSystem,
    suffix
  };
}

replayCurrentBatch()
  .then((result) => {
    console.log("[PASS] current-batch DLQ compensation smoke");
    console.log(JSON.stringify(result, null, 2));
  })
  .catch((error) => {
    console.error(`[FAIL] ${error.message}`);
    process.exitCode = 1;
  });
