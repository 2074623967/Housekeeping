#!/usr/bin/env node

/**
 * Run a current-batch payment.success.* or clearing.generated.* DLQ smoke.
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
const settlementBaseUrl = process.env.HSP_SETTLEMENT_BASE_URL || "http://127.0.0.1:18130";
const targetSystem = process.env.HSP_DLQ_TARGET_SYSTEM || "accounting";
const eventKind = process.env.HSP_DLQ_EVENT_KIND || "payment-success";
const timeoutMs = positiveEnvNumber("HSP_DLQ_TIMEOUT_MS", 30000);
const pollMs = positiveEnvNumber("HSP_DLQ_POLL_MS", 500);

const targetConfig = {
  "payment-success": {
    exchange: "payment.trade.dlq",
    eventType: "PAYMENT_SUCCESS",
    accounting: {
      routingKey: `payment.success.accounting.dlq.${suffix}.v1`,
      replayRoutingKey: `payment.success.accounting.replay.${suffix}.v1`,
      downstreamBaseUrl: accountingBaseUrl,
      downstreamPath: "accounting"
    },
    clearing: {
      routingKey: `payment.success.clearing.dlq.${suffix}.v1`,
      replayRoutingKey: `payment.success.clearing.replay.${suffix}.v1`,
      downstreamBaseUrl: process.env.HSP_CLEARING_BASE_URL || "http://127.0.0.1:18120",
      downstreamPath: "clearing"
    }
  },
  "clearing-generated": {
    exchange: "clearing.trade.dlq",
    eventType: "CLEARING_GENERATED",
    accounting: {
      routingKey: `clearing.generated.accounting.dlq.${suffix}.v1`,
      replayRoutingKey: `clearing.generated.accounting.replay.${suffix}.v1`,
      downstreamBaseUrl: accountingBaseUrl,
      downstreamPath: "accounting"
    },
    settlement: {
      routingKey: `clearing.generated.settlement.dlq.${suffix}.v1`,
      replayRoutingKey: `clearing.generated.settlement.replay.${suffix}.v1`,
      downstreamBaseUrl: settlementBaseUrl,
      downstreamPath: "settlements"
    }
  }
};

const eventConfig = targetConfig[eventKind];
const config = eventConfig && eventConfig[targetSystem];
if (!config) {
  throw new Error(`unsupported event/target: ${eventKind}/${targetSystem}`);
}

function requiredEnv(name) {
  const value = process.env[name];
  if (!value || !value.trim()) {
    throw new Error(`${name} is required; use an isolated RabbitMQ topology suffix`);
  }
  return value.trim();
}

function positiveEnvNumber(name, fallback) {
  const raw = process.env[name];
  if (raw === undefined || raw.trim() === "") {
    return fallback;
  }
  const value = Number(raw);
  if (!Number.isFinite(value) || value <= 0) {
    throw new Error(`${name} must be a positive number`);
  }
  return value;
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

async function publishDeadLetter(messageId, payload, rawPayload = false) {
  const result = await requestJson(
    `${managementBaseUrl}/exchanges/%2F/${encodeURIComponent(eventConfig.exchange)}/publish`,
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
            eventType: eventConfig.eventType
          }
        },
        routing_key: config.routingKey,
        payload: rawPayload ? payload : JSON.stringify(payload),
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
  const defaultBizNo = eventKind === "clearing-generated"
    ? `CLO-DLQ-SMOKE-${runId}`
    : `PAY-DLQ-SMOKE-${runId}`;
  const payload = process.env.HSP_DLQ_PAYLOAD_JSON
    ? parsePayload(process.env.HSP_DLQ_PAYLOAD_JSON)
    : eventKind === "clearing-generated"
      ? {
        eventType: "CLEARING_GENERATED",
        eventNo: `EVT-DLQ-SMOKE-${runId}`,
        businessChainId: `CHAIN-DLQ-SMOKE-${runId}`,
        clearingNo: defaultBizNo,
        clearingOrderNo: defaultBizNo,
        paymentOrderId: `PAY-DLQ-SMOKE-${runId}`,
        accountNo: "ACT10003",
        bizNo: defaultBizNo,
        summary: "当前批次 clearing.generated 补偿复核",
        targetType: "WORKER",
        targetNo: "WRK1001",
        targetName: "李阿姨",
        shouldSettleAmount: 120.00,
        deductAmount: 8.00,
        netSettleAmount: 112.00,
        amount: 112.00
      }
      : {
        eventType: "PAYMENT_SUCCESS",
        eventNo: `EVT-DLQ-SMOKE-${runId}`,
        paymentOrderId: defaultBizNo,
        orderNo: `ORD-DLQ-SMOKE-${runId}`,
        accountNo: "ACT10003",
        amount: 9.99
      };
  assert(payload && typeof payload === "object" && !Array.isArray(payload),
    "DLQ payload must be a JSON object");
  assert(payload.eventType === eventConfig.eventType,
    `DLQ payload eventType must be ${eventConfig.eventType}`);
  const bizNo = eventKind === "clearing-generated"
    ? String(payload.clearingOrderNo || payload.clearingNo || "").trim()
    : String(payload.paymentOrderId || "").trim();
  assert(bizNo, `DLQ payload must contain ${eventKind === "clearing-generated" ? "clearingOrderNo or clearingNo" : "paymentOrderId"}`);

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
    () => requestJson(`${config.downstreamBaseUrl}/api/${config.downstreamPath}/events?eventType=${encodeURIComponent(eventConfig.eventType)}&bizNo=${encodeURIComponent(bizNo)}&pageNo=1&pageSize=20`),
    (body) => body && body.code === "0" && body.data && Array.isArray(body.data.items) && body.data.items.length > 0
  );

  return {
    messageId,
    taskNo: task.taskNo,
    taskStatus: replayed.taskStatus,
    replayCount: replayed.replayCount,
    replayRoutingKey: replayed.replayRoutingKey,
    downstreamFactCount: downstream.data.items.length,
    eventKind,
    targetSystem,
    bizNo,
    suffix
  };
}

function parsePayload(rawPayload) {
  try {
    return JSON.parse(rawPayload);
  } catch (error) {
    throw new Error(`HSP_DLQ_PAYLOAD_JSON must be valid JSON: ${error.message}`);
  }
}

async function resolveInvalidCurrentBatch() {
  const runId = `${Date.now()}-${process.pid}`;
  const messageId = `DLQ-MANUAL-SMOKE-${runId}`;
  await publishDeadLetter(messageId, `{invalid-${eventKind}-${runId}`, true);
  const pending = await waitFor(
    "invalid dead-letter task intake",
    () => paymentApi(`/api/payment-dead-letter-tasks?messageId=${encodeURIComponent(messageId)}&pageNo=1&pageSize=20`),
    (page) => Boolean(taskFromPage(page, messageId))
  );
  const task = taskFromPage(pending, messageId);
  assert(task.taskStatus === "PENDING_REVIEW", `unexpected invalid task status: ${task.taskStatus}`);
  const resolved = await paymentApi(`/api/payment-dead-letter-tasks/${encodeURIComponent(task.taskNo)}/resolve-manually`, {
    method: "POST",
    body: JSON.stringify({
      operator: process.env.HSP_DLQ_OPERATOR || "支付运营A",
      resolutionNote: `当前批次 ${runId} 报文非法，保留审计副本后人工结案`
    })
  });
  assert(resolved.taskStatus === "MANUAL_RESOLVED",
    `unexpected manual resolution status: ${resolved.taskStatus}`);
  return {
    messageId,
    taskNo: task.taskNo,
    taskStatus: resolved.taskStatus,
    eventKind,
    targetSystem,
    suffix
  };
}

Promise.all([
  replayCurrentBatch(),
  process.env.HSP_DLQ_INCLUDE_MANUAL === "true" ? resolveInvalidCurrentBatch() : Promise.resolve(null)
])
  .then(([result, manual]) => {
    console.log("[PASS] current-batch DLQ compensation smoke");
    console.log(JSON.stringify({ replay: result, manual }, null, 2));
  })
  .catch((error) => {
    console.error(`[FAIL] ${error.message}`);
    process.exitCode = 1;
  });
