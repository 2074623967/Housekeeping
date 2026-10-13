#!/usr/bin/env node

import { pathToFileURL } from "node:url";

const COMMAND_OPTIONS = new Map([
  ["prepay", ["order-no", "pay-scene", "customer-name", "amount", "cashier-title", "remark"]],
  ["cashier", ["prepay-order-no"]],
  ["submit", ["prepay-order-no", "payment-method", "channel-code", "source-app-id",
    "merchant-no", "access-token", "client-device-id", "idempotency-key"]],
  ["detail", ["payment-order-id"]],
  ["query", ["payment-order-id"]],
  ["close", ["payment-order-id"]],
  ["refund-apply", ["payment-order-id", "idempotency-key", "refund-amount",
    "refund-method", "refund-reason", "operator"]],
  ["refund-detail", ["refund-order-id"]],
  ["refund-approve", ["refund-order-id", "operator", "remark"]],
  ["refund-query", ["refund-order-id", "operator"]],
  ["refund-retry", ["refund-order-id", "operator", "remark"]]
]);
const CASHIER_STATUS_TYPES = new Map([
  ["待支付", "warn"],
  ["支付中", "info"],
  ["结果待确认", "danger"],
  ["待回调", "warn"],
  ["支付成功", "success"],
  ["已关闭", "danger"],
  ["已失效", "danger"],
  ["待风控复核", "warn"],
  ["风控拦截", "danger"],
  ["风控拒绝", "danger"]
]);
const CASHIER_CHANNELS = new Set(["微信支付", "支付宝", "银行卡"]);
const PAYMENT_STATUSES = new Set([
  "PREPAY_CREATED", "RISK_REVIEW", "RISK_BLOCKED", "RISK_REJECTED",
  "WAIT_CALLBACK", "PAYING", "SUCCESS", "CLOSED", "FAIL"
]);
const PENDING_PAYMENT_STATUSES = new Set(["PREPAY_CREATED", "RISK_REVIEW", "WAIT_CALLBACK", "PAYING"]);
const TERMINAL_PAYMENT_ATTEMPT_STATUSES = new Set(["成功", "支付成功", "失败", "支付失败", "SUCCESS", "FAIL", "FAILED"]);
const REFUND_STATUSES = new Set(["REVIEWING", "PROCESSING", "SUCCESS", "FAIL"]);
const PAYMENT_STATUS_TYPES = new Set(["info", "success", "warn", "danger"]);
const REFUND_STATUS_TYPES = new Set(["info", "success", "warn", "danger"]);
const REFUND_ATTEMPT_STATUS_TYPES = new Map([
  ["PENDING", "warn"],
  ["FAILED", "danger"],
  ["ACCEPTED", "warn"],
  ["UNKNOWN", "warn"],
  ["SUCCESS", "success"],
  ["FAIL", "danger"]
]);
const PAYMENT_CASHIER_STATUSES = new Map([
  ["PREPAY_CREATED", new Set(["待支付", "已失效"])],
  ["WAIT_CALLBACK", new Set(["待回调", "结果待确认", "支付中"])],
  ["RISK_REVIEW", new Set(["待风控复核"])],
  ["RISK_BLOCKED", new Set(["风控拦截"])],
  ["RISK_REJECTED", new Set(["风控拒绝"])],
  ["PAYING", new Set(["支付中"])],
  ["SUCCESS", new Set(["支付成功"])],
  ["CLOSED", new Set(["已关闭", "已失效"])],
  ["FAIL", new Set(["结果待确认"])]
]);

function wallClockTimestamp(value) {
  return Date.parse(`${value.trim().replace(" ", "T")}Z`);
}

function isValidDateTime(value) {
  if (typeof value !== "string") {
    return false;
  }
  const match = /^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})$/.exec(value.trim());
  if (!match) {
    return false;
  }
  const [, year, month, day, hour, minute, second] = match.map(Number);
  const date = new Date(0);
  date.setUTCFullYear(year, month - 1, day);
  date.setUTCHours(hour, minute, second, 0);
  return date.getUTCFullYear() === year
    && date.getUTCMonth() === month - 1
    && date.getUTCDate() === day
    && date.getUTCHours() === hour
    && date.getUTCMinutes() === minute
    && date.getUTCSeconds() === second;
}

function isConsistentPaymentCashierStatus(paymentStatus, cashierStatus) {
  if (typeof paymentStatus !== "string" || typeof cashierStatus !== "string") {
    return false;
  }
  const expectedCashierStatuses = PAYMENT_CASHIER_STATUSES.get(
    paymentStatus.trim().toUpperCase()
  );
  return !expectedCashierStatuses || expectedCashierStatuses.has(cashierStatus.trim());
}

export function parseArguments(argv) {
  const [command, ...tokens] = argv;
  const allowedOptions = COMMAND_OPTIONS.get(command);
  if (!allowedOptions) {
    throw new Error("命令仅支持 prepay、cashier、submit、detail、query、close、refund-apply、refund-detail、refund-approve、refund-query 或 refund-retry");
  }
  const options = {};
  for (let index = 0; index < tokens.length; index += 1) {
    const token = tokens[index];
    if (!token.startsWith("--")) {
      throw new Error(`无法识别参数：${token}`);
    }
    const separatorIndex = token.indexOf("=");
    const key = separatorIndex > 2 ? token.slice(2, separatorIndex) : token.slice(2);
    const value = separatorIndex > 2 ? token.slice(separatorIndex + 1) : tokens[++index];
    if (!key || value == null || value.startsWith("--")) {
      throw new Error(`参数 --${key || "unknown"} 缺少值`);
    }
    if (key !== "base-url" && !allowedOptions.includes(key)) {
      throw new Error(`${command} 不支持参数 --${key}`);
    }
    if (Object.prototype.hasOwnProperty.call(options, key)) {
      throw new Error(`参数 --${key} 不能重复`);
    }
    options[key] = value;
  }
  return { command, options };
}

function requireOption(options, key) {
  const value = normalizeOption(options, key);
  if (!value) {
    throw new Error(`缺少必填参数 --${key}`);
  }
  return value;
}

function normalizeOption(options, key) {
  return typeof options[key] === "string" ? options[key].trim() : options[key];
}

function optionalOption(options, key, fallback) {
  const value = normalizeOption(options, key);
  return value || fallback;
}

export function buildRequest(command, options) {
  if (command === "prepay") {
    return {
      method: "POST",
      path: "/api/payments/prepay",
      data: {
        orderNo: requireOption(options, "order-no"),
        payScene: requireOption(options, "pay-scene"),
        customerName: requireOption(options, "customer-name"),
        amount: requireOption(options, "amount"),
        cashierTitle: requireOption(options, "cashier-title"),
        ...(options.remark && options.remark.trim() ? { remark: options.remark.trim() } : {})
      }
    };
  }
  if (command === "cashier") {
    return {
      method: "GET",
      path: `/api/payments/cashier/${encodeURIComponent(requireOption(options, "prepay-order-no"))}`
    };
  }
  if (command === "submit") {
    const prepayOrderNo = requireOption(options, "prepay-order-no");
    return {
      method: "POST",
      path: "/api/payments/submit",
      data: {
        prepayOrderNo,
        paymentMethod: optionalOption(options, "payment-method", "支付宝"),
        channelCode: optionalOption(options, "channel-code", "ALIPAY_H5"),
        sourceAppId: optionalOption(options, "source-app-id", "housekeeping-api"),
        merchantNo: optionalOption(options, "merchant-no", "MCH_ALI_20001"),
        accessToken: requireOption(options, "access-token"),
        terminal: "API",
        clientDeviceId: optionalOption(options, "client-device-id", "payment-api-client"),
        idempotencyKey: Object.prototype.hasOwnProperty.call(options, "idempotency-key")
          ? requireOption(options, "idempotency-key")
          : `API-${prepayOrderNo}`
      }
    };
  }
  if (command === "refund-apply") {
    const paymentOrderId = requireOption(options, "payment-order-id");
    return {
      method: "POST",
      path: "/api/refunds/apply",
      data: {
        paymentOrderId,
        idempotencyKey: requireOption(options, "idempotency-key"),
        refundAmount: requireOption(options, "refund-amount"),
        refundMethod: optionalOption(options, "refund-method", "原路退款"),
        refundReason: optionalOption(options, "refund-reason", "服务调用端退款申请"),
        operator: requireOption(options, "operator")
      }
    };
  }
  if (command === "refund-detail") {
    return {
      method: "GET",
      path: `/api/refunds/${encodeURIComponent(requireOption(options, "refund-order-id"))}`
    };
  }
  if (command === "refund-approve") {
    return {
      method: "POST",
      path: "/api/refunds/approve",
      data: {
        refundOrderId: requireOption(options, "refund-order-id"),
        operator: requireOption(options, "operator"),
        ...(options.remark && options.remark.trim() ? { remark: options.remark.trim() } : {})
      }
    };
  }
  if (command === "refund-query") {
    return {
      method: "POST",
      path: "/api/refunds/query",
      data: {
        refundOrderId: requireOption(options, "refund-order-id"),
        operator: requireOption(options, "operator")
      }
    };
  }
  if (command === "refund-retry") {
    return {
      method: "POST",
      path: "/api/refunds/retry",
      data: {
        refundOrderId: requireOption(options, "refund-order-id"),
        operator: requireOption(options, "operator"),
        ...(options.remark && options.remark.trim() ? { remark: options.remark.trim() } : {})
      }
    };
  }
  const paymentOrderId = requireOption(options, "payment-order-id");
  if (command === "detail") {
    return {
      method: "GET",
      path: `/api/payments/${encodeURIComponent(paymentOrderId)}`
    };
  }
  return {
    method: "POST",
    path: `/api/payments/${command}`,
    data: { paymentOrderId }
  };
}

export function resolveBaseUrl(options, environment = process.env) {
  const value = normalizeOption(options, "base-url")
    || (typeof environment.PAYMENT_API_BASE_URL === "string"
      ? environment.PAYMENT_API_BASE_URL.trim()
      : environment.PAYMENT_API_BASE_URL)
    || "http://localhost:18081";
  return value.replace(/\/+$/, "");
}

async function readResponsePayload(response) {
  try {
    return await response.json();
  } catch (error) {
    const parseError = new Error("支付服务返回了无法解析的响应");
    parseError.code = "PAYMENT-API-CLIENT-INVALID-RESPONSE";
    parseError.status = response.status;
    parseError.cause = error;
    throw parseError;
  }
}

export async function executeRequest(baseUrl, request, fetchImpl = fetch) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 10000);
  try {
    let response;
    try {
      response = await fetchImpl(`${baseUrl}${request.path}`, {
        method: request.method,
        headers: {
          "Content-Type": "application/json",
          "X-Terminal": "API"
        },
        body: request.data ? JSON.stringify(request.data) : undefined,
        signal: controller.signal
      });
    } catch (error) {
      const transportError = new Error(error?.name === "AbortError" ? "支付服务请求超时" : "无法连接支付服务");
      transportError.code = error?.name === "AbortError"
        ? "PAYMENT-API-CLIENT-TIMEOUT"
        : "PAYMENT-API-CLIENT-NETWORK";
      transportError.cause = error;
      throw transportError;
    }
    const payload = await readResponsePayload(response);
    if (!response.ok || !payload || payload.code !== "0") {
      const error = new Error(payload && payload.message ? payload.message : `HTTP ${response.status}`);
      error.payload = payload;
      throw error;
    }
    return payload;
  } finally {
    clearTimeout(timeoutId);
  }
}

function invalidCommandResponse(message) {
  const error = new Error(message);
  error.code = "PAYMENT-API-CLIENT-INVALID-RESPONSE";
  return error;
}

function requireResponseObject(data, command) {
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    throw invalidCommandResponse(`${command} 响应缺少可确认的业务实体`);
  }
  return data;
}

function requireResponseIdentity(data, field, expected, command) {
  if (typeof data[field] !== "string" || !data[field].trim()
      || (expected && data[field].trim() !== expected)) {
    throw invalidCommandResponse(`${command} 响应的 ${field} 与请求不一致`);
  }
}

function requireResponseAmount(data, expected, command, field = "amount") {
  const actualCents = parseResponseAmountCents(data[field]);
  const expectedCents = expected == null ? null : parseResponseAmountCents(expected);
  if (actualCents == null || actualCents <= 0n
      || (expected != null && (expectedCents == null || actualCents !== expectedCents))) {
    throw invalidCommandResponse(`${command} 响应的 ${field} 与请求不一致`);
  }
}

function parseResponseAmountCents(value) {
  if (typeof value === "number"
      && (!Number.isFinite(value) || !Number.isSafeInteger(value * 100))) {
    return null;
  }
  const normalized = String(value == null ? "" : value).replace(/[,¥]/g, "").trim();
  if (!/^(?:\d{1,16}(?:\.\d{1,2})?|\.\d{1,2})$/.test(normalized)) {
    return null;
  }
  const [integerPart = "0", fractionPart = ""] = normalized.split(".");
  return BigInt(integerPart || "0") * 100n + BigInt(fractionPart.padEnd(2, "0"));
}

function requireResponsePaymentFacts(data, command) {
  requireResponseIdentity(data, "paymentOrderId", "", command);
  requireResponseIdentity(data, "prepayOrderNo", "", command);
  requireResponseIdentity(data, "orderNo", "", command);
  requireResponseAmount(data, null, command);
}

function requireResponsePaymentStatusCode(data, command) {
  if (typeof data.paymentStatus !== "string"
      || !PAYMENT_STATUSES.has(data.paymentStatus.trim().toUpperCase())) {
    throw invalidCommandResponse(`${command} 响应缺少可确认的支付状态事实`);
  }
}

function requireResponseExpiry(data, command) {
  if (!isValidDateTime(data.expiresAt)) {
    throw invalidCommandResponse(`${command} 响应缺少可确认的失效时间事实`);
  }
  if (isValidDateTime(data.createdAt)
      && wallClockTimestamp(data.createdAt)
        > wallClockTimestamp(data.expiresAt)) {
    throw invalidCommandResponse(`${command} 响应的失效时间早于创建时间`);
  }
}

function requireResponseSubmissionTimes(data, command) {
  requireResponseCreationTime(data, command);
  const status = typeof data.paymentStatus === "string"
    ? data.paymentStatus.trim().toUpperCase()
    : "";
  if (status === "SUCCESS") {
    if (!isValidDateTime(data.successAt)) {
      throw invalidCommandResponse(`${command} 响应缺少可确认的支付成功时间事实`);
    }
    if (wallClockTimestamp(data.createdAt)
        > wallClockTimestamp(data.successAt)) {
      throw invalidCommandResponse(`${command} 响应的支付成功时间早于创建时间`);
    }
    if (data.latestAttemptStatus !== "成功"
        || data.latestAttemptStatusType !== "success"
        || !isValidDateTime(data.latestAttemptCompletedAt)
        || wallClockTimestamp(data.createdAt)
          > wallClockTimestamp(data.latestAttemptCompletedAt)
        || wallClockTimestamp(data.latestAttemptCompletedAt)
          > wallClockTimestamp(data.successAt)) {
      throw invalidCommandResponse(`${command} 响应缺少可确认的成功支付尝试事实`);
    }
  } else if (data.successAt != null && data.successAt !== "-") {
    throw invalidCommandResponse(`${command} 响应包含与支付状态矛盾的成功时间事实`);
  }
  if (status !== "SUCCESS" && !isConsistentSubmissionAttemptFacts(
    data.latestAttemptStatus,
    data.latestAttemptStatusType,
    data.latestAttemptCompletedAt
  )) {
    throw invalidCommandResponse(`${command} 响应包含无法确认的支付尝试状态事实`);
  }
}

function isConsistentSubmissionAttemptFacts(status, statusType, completedAt) {
  if (status == null) {
    return statusType == null && completedAt == null;
  }
  if (typeof status !== "string" || !status.trim()
      || typeof statusType !== "string" || !statusType.trim()
      || !isConsistentPaymentAttemptStatus(status, statusType)) {
    return false;
  }
  if (isSuccessfulPaymentAttemptStatus(status)) {
    return false;
  }
  return completedAt == null;
}

function requireResponseCreationTime(data, command) {
  if (!isValidDateTime(data.createdAt)) {
    throw invalidCommandResponse(`${command} 响应缺少可确认的支付创建时间事实`);
  }
}

function requireResponseCashierStatus(data, command) {
  const status = typeof data.cashierStatus === "string" ? data.cashierStatus.trim() : "";
  if (!CASHIER_STATUS_TYPES.has(status)
      || data.cashierStatusType !== CASHIER_STATUS_TYPES.get(status)
      || !isConsistentPaymentCashierStatus(data.paymentStatus, status)
      || !isConsistentCashierAttemptFacts(
        data.paymentStatus,
        data.latestAttemptStatus,
        data.latestAttemptStatusType,
        data.latestAttemptCompletedAt
      )) {
    throw invalidCommandResponse(`${command} 响应缺少可确认的收银台状态事实`);
  }
}

function requireResponsePaymentStatus(data, command) {
  const expectedType = {
    PREPAY_CREATED: "info",
    RISK_REVIEW: "warn",
    RISK_BLOCKED: "danger",
    RISK_REJECTED: "danger",
    WAIT_CALLBACK: "warn",
    PAYING: "info",
    SUCCESS: "success",
    CLOSED: "danger",
    FAIL: "danger"
  }[typeof data.status === "string" ? data.status.trim().toUpperCase() : ""];
  if (typeof data.status !== "string"
      || !PAYMENT_STATUSES.has(data.status.trim().toUpperCase())
      || typeof data.statusType !== "string"
      || !expectedType
      || data.statusType.trim().toLowerCase() !== expectedType
      || !isConsistentPaymentAttemptStatus(data.latestAttemptStatus, data.latestAttemptStatusType)
      || (data.status.trim().toUpperCase() !== "SUCCESS"
        && isSuccessfulPaymentAttemptStatus(data.latestAttemptStatus))
      || (PENDING_PAYMENT_STATUSES.has(data.status.trim().toUpperCase())
        && typeof data.latestAttemptStatus === "string"
        && TERMINAL_PAYMENT_ATTEMPT_STATUSES.has(data.latestAttemptStatus.trim().toUpperCase()))) {
    throw invalidCommandResponse(`${command} 响应缺少可确认的支付状态事实`);
  }
}

function requireResponsePaymentChannelFacts(data, command) {
  const status = typeof data.status === "string" ? data.status.trim().toUpperCase() : "";
  if (status === "SUCCESS") {
    if (typeof data.channel !== "string" || !data.channel.trim()
        || typeof data.channelTransactionNo !== "string"
        || !data.channelTransactionNo.trim()) {
      throw invalidCommandResponse(`${command} 响应缺少可确认的支付渠道事实`);
    }
    return;
  }
  if (data.channel != null && typeof data.channel !== "string") {
    throw invalidCommandResponse(`${command} 响应包含无法确认的支付渠道事实`);
  }
  if (data.channelTransactionNo != null && typeof data.channelTransactionNo !== "string") {
    throw invalidCommandResponse(`${command} 响应包含无法确认的渠道交易号事实`);
  }
}

function requireResponseSubmissionChannelFacts(data, command, expectedChannelCode = "") {
  const status = typeof data.paymentStatus === "string"
    ? data.paymentStatus.trim().toUpperCase()
    : "";
  if (status === "SUCCESS") {
    if (typeof data.channel !== "string" || !data.channel.trim()
        || typeof data.channelTransactionNo !== "string"
        || !data.channelTransactionNo.trim()) {
      throw invalidCommandResponse(`${command} 响应缺少可确认的支付渠道事实`);
    }
    if (expectedChannelCode
        && data.channel.trim() !== expectedChannelCode) {
      throw invalidCommandResponse(`${command} 响应的支付渠道与请求声明不一致`);
    }
    return;
  }
  if (data.channel != null && typeof data.channel !== "string") {
    throw invalidCommandResponse(`${command} 响应包含无法确认的支付渠道事实`);
  }
  if (data.channelTransactionNo != null && typeof data.channelTransactionNo !== "string") {
    throw invalidCommandResponse(`${command} 响应包含无法确认的渠道交易号事实`);
  }
  if (expectedChannelCode
      && (typeof data.channel !== "string" || data.channel.trim() !== expectedChannelCode)) {
    throw invalidCommandResponse(`${command} 响应的支付渠道与请求声明不一致`);
  }
}

function isSuccessfulPaymentAttemptStatus(status) {
  return typeof status === "string" && ["成功", "支付成功", "SUCCESS"].includes(status.trim().toUpperCase());
}

function isConsistentPaymentAttemptStatus(status, statusType) {
  if (status == null && statusType == null) {
    return true;
  }
  if (typeof status !== "string" || !status.trim()
      || typeof statusType !== "string" || !statusType.trim()) {
    return false;
  }
  const normalizedStatus = status.trim().toUpperCase();
  const normalizedType = statusType.trim().toLowerCase();
  if (isSuccessfulPaymentAttemptStatus(status)) {
    return normalizedType === "success";
  }
  if (["失败", "支付失败", "FAIL", "FAILED"].includes(normalizedStatus)) {
    return normalizedType === "danger";
  }
  return PAYMENT_STATUS_TYPES.has(normalizedType);
}

function requireResponsePaymentTimes(data, command) {
  if (!isValidDateTime(data.createdAt)) {
    throw invalidCommandResponse(`${command} 响应缺少可确认的支付创建时间事实`);
  }
  const status = typeof data.status === "string" ? data.status.trim().toUpperCase() : "";
  if (status === "SUCCESS") {
    if (!isValidDateTime(data.successAt)) {
      throw invalidCommandResponse(`${command} 响应缺少可确认的支付成功时间事实`);
    }
    if (wallClockTimestamp(data.createdAt)
        > wallClockTimestamp(data.successAt)) {
      throw invalidCommandResponse(`${command} 响应的支付成功时间早于创建时间`);
    }
    if (data.latestAttemptStatus !== "成功"
        || data.latestAttemptStatusType !== "success"
        || !isValidDateTime(data.latestAttemptCompletedAt)) {
      throw invalidCommandResponse(`${command} 响应缺少可确认的成功支付尝试事实`);
    }
  } else if (data.successAt != null && data.successAt !== "-") {
    throw invalidCommandResponse(`${command} 响应包含与支付状态矛盾的成功时间事实`);
  }
  if (data.latestAttemptStatus == null) {
    if (data.latestAttemptCompletedAt != null) {
      throw invalidCommandResponse(`${command} 响应包含无对应尝试的完成时间事实`);
    }
  } else if (typeof data.latestAttemptStatus !== "string"
      || !data.latestAttemptStatus.trim()) {
    throw invalidCommandResponse(`${command} 响应包含无法确认的支付尝试状态事实`);
  } else if (data.latestAttemptStatus.trim() === "成功") {
    if (!isValidDateTime(data.latestAttemptCompletedAt)) {
      throw invalidCommandResponse(`${command} 响应缺少可确认的支付尝试完成时间事实`);
    }
    if (wallClockTimestamp(data.createdAt)
        > wallClockTimestamp(data.latestAttemptCompletedAt)) {
      throw invalidCommandResponse(`${command} 响应的支付尝试完成时间早于支付创建时间`);
    }
    if (status === "SUCCESS"
        && wallClockTimestamp(data.latestAttemptCompletedAt)
          > wallClockTimestamp(data.successAt)) {
      throw invalidCommandResponse(`${command} 响应的支付尝试完成时间晚于支付成功时间`);
    }
  } else if (data.latestAttemptCompletedAt != null) {
    throw invalidCommandResponse(`${command} 响应包含与支付尝试状态矛盾的完成时间事实`);
  }
}

function requireResponsePaymentLogs(data, command) {
  for (const field of ["routeLogs", "notifyLogs", "eventLogs"]) {
    const logs = data[field];
    if (logs == null) {
      continue;
    }
    if (!Array.isArray(logs)
        || logs.some((log) => typeof log !== "string" || !log.trim())) {
      throw invalidCommandResponse(`${command} 响应包含无法确认的支付轨迹事实`);
    }
  }
}

function requireBusinessChainId(data, command) {
  if (typeof data.businessChainId !== "string"
      || data.businessChainId.trim() !== `CHAIN-${data.paymentOrderId.trim()}`) {
    throw invalidCommandResponse(`${command} 响应缺少或包含不一致的业务链路事实`);
  }
}

function requireResponseRefundFacts(data, expectedAmount, command) {
  requireResponseIdentity(data, "paymentOrderId", "", command);
  requireBusinessChainId(data, command);
  requireResponseIdentity(data, "orderNo", "", command);
  requireResponseAmount(data, expectedAmount, command, "refundAmount");
  if (command === "refund-detail" || Object.prototype.hasOwnProperty.call(data, "paidAmount")) {
    requireResponseAmount(data, null, command, "paidAmount");
    if (parseResponseAmountCents(data.refundAmount) > parseResponseAmountCents(data.paidAmount)) {
      throw invalidCommandResponse(`${command} 响应的退款金额超过原支付金额`);
    }
  }
}

function requireResponseRefundStatus(data, command) {
  const expectedType = {
    REVIEWING: "warn",
    PROCESSING: "warn",
    SUCCESS: "success",
    FAIL: "danger"
  }[typeof data.status === "string" ? data.status.trim().toUpperCase() : ""];
  if (typeof data.status !== "string"
      || !REFUND_STATUSES.has(data.status.trim().toUpperCase())
      || typeof data.statusType !== "string"
      || !expectedType
      || data.statusType.trim().toLowerCase() !== expectedType) {
    throw invalidCommandResponse(`${command} 响应缺少可确认的退款状态事实`);
  }
}

function requireResponseRefundChannelFacts(data, command) {
  const status = typeof data.status === "string" ? data.status.trim().toUpperCase() : "";
  if (["SUCCESS", "FAIL"].includes(status)) {
    if (typeof data.channelCode !== "string" || !data.channelCode.trim()
        || typeof data.channelRefundNo !== "string" || !data.channelRefundNo.trim()) {
      throw invalidCommandResponse(`${command} 响应缺少可确认的退款渠道事实`);
    }
    return;
  }
  if (data.channelCode != null && typeof data.channelCode !== "string") {
    throw invalidCommandResponse(`${command} 响应包含无法确认的退款渠道编码事实`);
  }
  if (data.channelRefundNo != null && typeof data.channelRefundNo !== "string") {
    throw invalidCommandResponse(`${command} 响应包含无法确认的渠道退款单号事实`);
  }
}

function requireResponseRefundTimes(data, command) {
  if (!isValidDateTime(data.appliedAt)) {
    throw invalidCommandResponse(`${command} 响应缺少可确认的退款申请时间事实`);
  }
  const terminalStatus = typeof data.status === "string"
    && ["SUCCESS", "FAIL"].includes(data.status.trim().toUpperCase());
  if (terminalStatus && (data.channelSubmittedAt == null || data.channelSubmittedAt === "-")) {
    throw invalidCommandResponse(`${command} 响应缺少可确认的渠道受理时间事实`);
  }
  if (data.channelSubmittedAt != null && data.channelSubmittedAt !== "-") {
    if (!isValidDateTime(data.channelSubmittedAt)
        || wallClockTimestamp(data.channelSubmittedAt)
          < wallClockTimestamp(data.appliedAt)) {
      throw invalidCommandResponse(`${command} 响应包含无法确认的渠道受理时间事实`);
    }
    if (isValidDateTime(data.terminalAt)
        && wallClockTimestamp(data.channelSubmittedAt)
          > wallClockTimestamp(data.terminalAt)) {
      throw invalidCommandResponse(`${command} 响应的渠道受理时间晚于退款终态时间`);
    }
  }
  const status = typeof data.status === "string" ? data.status.trim().toUpperCase() : "";
  if (status === "SUCCESS") {
    if (!isValidDateTime(data.successAt)) {
      throw invalidCommandResponse(`${command} 响应缺少可确认的退款成功时间事实`);
    }
    if (wallClockTimestamp(data.appliedAt)
        > wallClockTimestamp(data.successAt)) {
      throw invalidCommandResponse(`${command} 响应的退款成功时间早于申请时间`);
    }
  } else if (data.successAt != null && data.successAt !== "-") {
    throw invalidCommandResponse(`${command} 响应包含与退款状态矛盾的成功时间事实`);
  }
  if (["SUCCESS", "FAIL"].includes(status)) {
    if (!isValidDateTime(data.terminalAt)) {
      throw invalidCommandResponse(`${command} 响应缺少可确认的退款终态时间事实`);
    }
    const terminalTimestamp = wallClockTimestamp(data.terminalAt);
    const comparisonTimestamp = status === "SUCCESS"
      ? wallClockTimestamp(data.successAt)
      : wallClockTimestamp(data.appliedAt);
    if (comparisonTimestamp > terminalTimestamp) {
      throw invalidCommandResponse(`${command} 响应的退款终态时间早于生命周期前置时间`);
    }
  } else if (data.terminalAt != null && data.terminalAt !== "-") {
    throw invalidCommandResponse(`${command} 响应包含与退款状态矛盾的终态时间事实`);
  }
}

function requireResponseRefundAttempts(data, command) {
  if (data.attempts == null) {
    return;
  }
  if (!Array.isArray(data.attempts)) {
    throw invalidCommandResponse(`${command} 响应包含无法确认的退款尝试时间事实`);
  }
  let previousCreatedTimestamp = null;
  for (const attempt of data.attempts) {
    if (!attempt || typeof attempt !== "object" || Array.isArray(attempt)
        || typeof attempt.refundAttemptNo !== "string"
        || !attempt.refundAttemptNo.trim()
        || typeof attempt.attemptStatus !== "string"
        || !REFUND_ATTEMPT_STATUS_TYPES.has(attempt.attemptStatus.trim().toUpperCase())
        || typeof attempt.attemptStatusType !== "string"
        || attempt.attemptStatusType.trim().toLowerCase()
          !== REFUND_ATTEMPT_STATUS_TYPES.get(attempt.attemptStatus.trim().toUpperCase())
        || !isValidDateTime(attempt.createdAt)) {
      throw invalidCommandResponse(`${command} 响应包含无法确认的退款尝试时间事实`);
    }
    const hasSubmittedAt = attempt.submittedAt !== "-" && attempt.submittedAt != null;
    const hasCompletedAt = attempt.completedAt !== "-" && attempt.completedAt != null;
    if ((hasSubmittedAt && !isValidDateTime(attempt.submittedAt))
        || (hasCompletedAt && !isValidDateTime(attempt.completedAt))) {
      throw invalidCommandResponse(`${command} 响应包含无法确认的退款尝试时间事实`);
    }
    const createdTimestamp = wallClockTimestamp(attempt.createdAt);
    if (previousCreatedTimestamp != null && createdTimestamp > previousCreatedTimestamp) {
      throw invalidCommandResponse(`${command} 响应包含无法确认的退款尝试顺序事实`);
    }
    previousCreatedTimestamp = createdTimestamp;
    const submittedTimestamp = hasSubmittedAt
      ? wallClockTimestamp(attempt.submittedAt)
      : createdTimestamp;
    const attemptStatus = attempt.attemptStatus.trim().toUpperCase();
    const requiresSubmittedAt = !["PENDING", "FAILED"].includes(attemptStatus);
    const terminalStatus = ["SUCCESS", "FAIL"].includes(attemptStatus);
    const terminalTimestamp = isValidDateTime(data.terminalAt)
      ? wallClockTimestamp(data.terminalAt)
      : null;
    const successTimestamp = isValidDateTime(data.successAt)
      ? wallClockTimestamp(data.successAt)
      : null;
    const completedTimestamp = hasCompletedAt
      ? wallClockTimestamp(attempt.completedAt)
      : null;
    if ((hasSubmittedAt !== requiresSubmittedAt)
        || (hasSubmittedAt && submittedTimestamp < createdTimestamp)
        || (terminalStatus ? !hasCompletedAt : hasCompletedAt)
        || (hasCompletedAt && completedTimestamp < submittedTimestamp)
        || (terminalTimestamp != null
          && ((hasSubmittedAt && submittedTimestamp > terminalTimestamp)
            || (hasCompletedAt && completedTimestamp > terminalTimestamp)))
        || (successTimestamp != null && hasCompletedAt && completedTimestamp > successTimestamp)) {
      throw invalidCommandResponse(`${command} 响应包含无法确认的退款尝试时间事实`);
    }
  }
  const status = data.status?.trim().toUpperCase();
  if (["SUCCESS", "FAIL"].includes(status) && data.attempts.length > 0) {
    const latestAttempt = data.attempts[0];
    if (latestAttempt.attemptStatus.trim().toUpperCase() !== status
        || typeof latestAttempt.channelCode !== "string"
        || latestAttempt.channelCode.trim() !== data.channelCode.trim()
        || typeof latestAttempt.channelRefundNo !== "string"
        || latestAttempt.channelRefundNo.trim() !== data.channelRefundNo.trim()) {
      throw invalidCommandResponse(`${command} 响应包含无法确认的退款尝试或渠道归属事实`);
    }
  }
}

function requireResponseRefundOperationLogs(data, command) {
  if (data.operationLogs == null) {
    return;
  }
  if (!Array.isArray(data.operationLogs)) {
    throw invalidCommandResponse(`${command} 响应包含无法确认的退款操作日志事实`);
  }
  const appliedTimestamp = isValidDateTime(data.appliedAt)
    ? wallClockTimestamp(data.appliedAt)
    : null;
  const terminalTimestamp = isValidDateTime(data.terminalAt)
    ? wallClockTimestamp(data.terminalAt)
    : (isValidDateTime(data.successAt)
      ? wallClockTimestamp(data.successAt)
      : null);
  let previousTimestamp = null;
  for (const log of data.operationLogs) {
    if (!log || typeof log !== "object" || Array.isArray(log)
        || typeof log.logNo !== "string"
        || !log.logNo.trim()
        || !((typeof log.actionName === "string" && log.actionName.trim())
          || (typeof log.actionCode === "string" && log.actionCode.trim()))
        || !isValidDateTime(log.createdAt)) {
      throw invalidCommandResponse(`${command} 响应包含无法确认的退款操作日志事实`);
    }
    const createdTimestamp = wallClockTimestamp(log.createdAt);
    if ((previousTimestamp != null && createdTimestamp > previousTimestamp)
        || (appliedTimestamp != null && createdTimestamp < appliedTimestamp)
        || (terminalTimestamp != null && createdTimestamp > terminalTimestamp)) {
      throw invalidCommandResponse(`${command} 响应包含无法确认的退款操作日志时间事实`);
    }
    previousTimestamp = createdTimestamp;
  }
}

function requireResponseCashierFacts(data, command) {
  const status = typeof data.status === "string" ? data.status.trim() : "";
  if (!CASHIER_STATUS_TYPES.has(status)
      || data.statusType !== CASHIER_STATUS_TYPES.get(status)
      || typeof data.paymentStatus !== "string"
      || !PAYMENT_STATUSES.has(data.paymentStatus.trim().toUpperCase())
      || !isConsistentPaymentCashierStatus(data.paymentStatus, status)
      || !isValidDateTime(data.expiresAt)
      || !Array.isArray(data.channels)
      || data.channels.some((channel) => typeof channel !== "string"
        || !CASHIER_CHANNELS.has(channel.trim()))
      || !isConsistentCashierAttemptFacts(
        data.paymentStatus,
        data.latestAttemptStatus,
        data.latestAttemptStatusType,
        data.latestAttemptCompletedAt
      )) {
    throw invalidCommandResponse(`${command} 响应缺少可确认的收银台状态或渠道事实`);
  }
}

function isConsistentCashierAttemptFacts(paymentStatus, status, statusType, completedAt) {
  const normalizedPaymentStatus = paymentStatus.trim().toUpperCase();
  if (normalizedPaymentStatus === "SUCCESS") {
    return status === "成功"
      && statusType === "success"
      && isValidDateTime(completedAt);
  }
  if (PENDING_PAYMENT_STATUSES.has(normalizedPaymentStatus)
      && typeof status === "string"
      && TERMINAL_PAYMENT_ATTEMPT_STATUSES.has(status.trim().toUpperCase())) {
    return false;
  }
  if (status == null) {
    return statusType == null && completedAt == null;
  }
  if (typeof status !== "string" || !status.trim()
      || typeof statusType !== "string"
      || !PAYMENT_STATUS_TYPES.has(statusType.trim().toLowerCase())) {
    return false;
  }
  if (!isConsistentPaymentAttemptStatus(status, statusType)) {
    return false;
  }
  if (isSuccessfulPaymentAttemptStatus(status)) {
    return false;
  }
  return completedAt == null;
}

export function validateCommandResponse(command, options, payload) {
  const data = requireResponseObject(payload?.data, command);
  if (command === "prepay") {
    requireResponseIdentity(data, "prepayOrderNo", "", command);
    requireResponseIdentity(data, "paymentOrderId", "", command);
    requireBusinessChainId(data, command);
    requireResponseIdentity(data, "orderNo", normalizeOption(options, "order-no"), command);
    requireResponseAmount(data, options.amount, command);
    requireResponsePaymentStatusCode(data, command);
    requireResponseCreationTime(data, command);
    requireResponseExpiry(data, command);
    requireResponseCashierStatus(data, command);
  } else if (command === "cashier") {
    requireResponseIdentity(data, "prepayOrderNo", normalizeOption(options, "prepay-order-no"), command);
    requireResponsePaymentFacts(data, command);
    requireBusinessChainId(data, command);
    requireResponseCashierFacts(data, command);
  } else if (command === "submit") {
    requireResponseIdentity(data, "prepayOrderNo", normalizeOption(options, "prepay-order-no"), command);
    requireResponsePaymentFacts(data, command);
    requireBusinessChainId(data, command);
    requireResponsePaymentStatusCode(data, command);
    requireResponseSubmissionChannelFacts(
      data, command, normalizeOption(options, "channel-code")
    );
    requireResponseSubmissionTimes(data, command);
    requireResponseExpiry(data, command);
    requireResponseCashierStatus(data, command);
  } else if (["detail", "query", "close"].includes(command)) {
    requireResponseIdentity(data, "paymentOrderId", normalizeOption(options, "payment-order-id"), command);
    requireResponsePaymentFacts(data, command);
    requireBusinessChainId(data, command);
    requireResponsePaymentStatus(data, command);
    requireResponsePaymentChannelFacts(data, command);
    requireResponsePaymentTimes(data, command);
    requireResponsePaymentLogs(data, command);
  } else if (command === "refund-apply") {
    requireResponseIdentity(data, "refundOrderId", "", command);
    requireResponseIdentity(data, "paymentOrderId", normalizeOption(options, "payment-order-id"), command);
    requireResponseRefundFacts(data, normalizeOption(options, "refund-amount"), command);
    requireResponseIdentity(data, "refundMethod", optionalOption(options, "refund-method", "原路退款"), command);
    requireResponseRefundStatus(data, command);
    requireResponseRefundChannelFacts(data, command);
    requireResponseRefundTimes(data, command);
  } else if (["refund-detail", "refund-approve", "refund-query", "refund-retry"].includes(command)) {
    requireResponseIdentity(data, "refundOrderId", normalizeOption(options, "refund-order-id"), command);
    requireResponseRefundFacts(data, null, command);
    requireResponseRefundStatus(data, command);
    requireResponseRefundChannelFacts(data, command);
    requireResponseRefundTimes(data, command);
    if (command === "refund-detail") {
      requireResponseRefundAttempts(data, command);
      requireResponseRefundOperationLogs(data, command);
    }
  }
  return payload;
}

export async function main(argv = process.argv.slice(2), environment = process.env) {
  const { command, options } = parseArguments(argv);
  const request = buildRequest(command, options);
  const payload = await executeRequest(resolveBaseUrl(options, environment), request);
  process.stdout.write(`${JSON.stringify(validateCommandResponse(command, options, payload), null, 2)}\n`);
}

if (process.argv[1] && pathToFileURL(process.argv[1]).href === import.meta.url) {
  main().catch((error) => {
    const output = error.payload || {
      code: error.code || "PAYMENT-API-CLIENT",
      message: error.message,
      ...(error.status ? { status: error.status } : {})
    };
    process.stderr.write(`${JSON.stringify(output, null, 2)}\n`);
    process.exitCode = 1;
  });
}
