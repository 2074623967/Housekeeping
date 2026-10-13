#!/usr/bin/env node

import { createHmac } from "node:crypto";
import { pathToFileURL } from "node:url";
import {
  normalizePaymentRecordDetailResult,
  normalizePaymentRecordListPage
} from "../../systems/payment-core/frontend/admin-web/src/api/client.js";
import {
  normalizeCashierPageResult,
  normalizePaymentDetailResult,
  normalizePaymentSubmitResult,
  normalizePrepayResult
} from "../../systems/payment-core/frontend/app-web/src/api/client.js";
import miniProgramPayment from "../../systems/payment-core/frontend/mini-program/utils/request.js";
import { validateCommandResponse } from "../../systems/payment-core/frontend/api-client/payment-api.mjs";

export function normalizeBaseUrl(value, fallback) {
  const normalized = typeof value === "string" ? value.trim() : value;
  return (normalized || fallback).replace(/\/+$/, "");
}

export function assertLocalDemoBaseUrl(value, service) {
  let url;
  try {
    url = new URL(value);
  } catch {
    throw new Error(`${service} must use a loopback HTTP service root for local happy-path`);
  }
  if (url.protocol !== "http:"
      || !["localhost", "127.0.0.1", "[::1]"].includes(url.hostname)
      || url.username || url.password || url.pathname !== "/" || url.search || url.hash) {
    throw new Error(`${service} must use a loopback HTTP service root for local happy-path`);
  }
  return value;
}

export function normalizeIdentifier(value) {
  return typeof value === "string" ? value.trim() : value;
}

export function buildLocalOrderNo(suffix, specialCharacters = false) {
  return `LOCAL-HAPPY-${suffix}${specialCharacters ? '"\\' : ""}`;
}

export function refundCallbackHeaders(refundOrderId, body, secret = process.env.HSP_REFUND_CALLBACK_HTTP_SECRET) {
  if (!secret || !secret.trim()) {
    throw new Error("HSP_REFUND_CALLBACK_HTTP_SECRET is required for the local refund callback");
  }
  const timestamp = String(Math.floor(Date.now() / 1000));
  const signature = createHmac("sha256", secret)
    .update(`${timestamp}\n${refundOrderId}\n${body}`, "utf8")
    .digest("base64");
  return {
    "X-HSP-Refund-Callback-Timestamp": timestamp,
    "X-HSP-Refund-Callback-Signature": signature
  };
}

const paymentBaseUrl = normalizeBaseUrl(process.env.HSP_PAYMENT_CORE_BASE_URL, "http://localhost:18081");
const clearingBaseUrl = normalizeBaseUrl(process.env.HSP_CLEARING_BASE_URL, "http://localhost:18120");
const settlementBaseUrl = normalizeBaseUrl(process.env.HSP_SETTLEMENT_BASE_URL, "http://localhost:18130");
const accountingBaseUrl = normalizeBaseUrl(process.env.HSP_ACCOUNTING_BASE_URL, "http://localhost:18110");
const refundBaseUrl = normalizeBaseUrl(process.env.HSP_REFUND_CENTER_BASE_URL, "http://localhost:18140");
const reconciliationBaseUrl = normalizeBaseUrl(process.env.HSP_RECONCILIATION_BASE_URL, "http://localhost:18150");
const opsConfigBaseUrl = normalizeBaseUrl(process.env.HSP_OPS_CONFIG_BASE_URL, "http://localhost:18170");
const riskBaseUrl = normalizeBaseUrl(process.env.HSP_RISK_CONTROL_BASE_URL, "http://localhost:18180");
const gatewayBaseUrl = normalizeBaseUrl(process.env.HSP_GATEWAY_BASE_URL, "http://localhost:8090");
const LOCAL_PAYMENT_CHAIN_TARGETS = {
  paymentBaseUrl: "http://localhost:18081",
  opsConfigBaseUrl: "http://localhost:18170",
  riskBaseUrl: "http://localhost:18180",
  gatewayBaseUrl: "http://localhost:8090",
  accountingBaseUrl: "http://localhost:18110",
  clearingBaseUrl: "http://localhost:18120",
  settlementBaseUrl: "http://localhost:18130",
  refundBaseUrl: "http://localhost:18140",
  reconciliationBaseUrl: "http://localhost:18150"
};
const LOCAL_PAYMENT_CHAIN_IDENTITIES = {
  paymentBaseUrl: "payment-core-h2-demo",
  opsConfigBaseUrl: "ops-config-system-h2-local-payment-chain",
  riskBaseUrl: "risk-control-system-h2-local-payment-chain",
  gatewayBaseUrl: "gateway-access-h2-local-payment-chain",
  accountingBaseUrl: "accounting-system-h2-local-payment-chain",
  clearingBaseUrl: "clearing-system-h2-local-payment-chain",
  settlementBaseUrl: "settlement-system-h2-local-payment-chain",
  refundBaseUrl: "refund-center-h2-local-payment-chain",
  reconciliationBaseUrl: "reconciliation-system-h2-local-payment-chain"
};
const REQUEST_TIMEOUT_MS = 10000;
const CASHIER_CHANNELS = new Set(["微信支付", "支付宝", "银行卡"]);
const TERMINAL_PAYMENT_ATTEMPT_STATUSES = new Set(["成功", "支付成功", "失败", "支付失败", "SUCCESS", "FAIL", "FAILED"]);

const suffix = `${Date.now()}-${process.pid}`;
const orderNo = buildLocalOrderNo(suffix, process.env.HSP_LOCAL_PAYMENT_CHAIN_SPECIAL_ORDER_NO === "true");

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function matchesIdentifier(actual, expected) {
  return typeof actual === "string" && actual.trim() === normalizeIdentifier(expected);
}

function matchesCustomerName(actual, expected) {
  const normalizedActual = typeof actual === "string" ? actual.trim() : "";
  const normalizedExpected = normalizeIdentifier(expected);
  if (!normalizedActual || !normalizedExpected) {
    return false;
  }
  const maskedExpected = normalizedExpected.length <= 1
    ? "*"
    : `${normalizedExpected.slice(0, 1)}*`;
  return normalizedActual === normalizedExpected || normalizedActual === maskedExpected;
}

function paymentBusinessChainId(paymentOrderId) {
  return `CHAIN-${normalizeIdentifier(paymentOrderId)}`;
}

function isTerminalPaymentAttemptStatus(status) {
  return TERMINAL_PAYMENT_ATTEMPT_STATUSES.has(status.trim().toUpperCase());
}

function assertPendingPaymentAttemptFacts(payment, subject) {
  const status = payment.latestAttemptStatus;
  const statusType = payment.latestAttemptStatusType;
  if (status == null) {
    assert(statusType == null && payment.latestAttemptCompletedAt == null,
      `${subject} contained an attempt completion without a usable attempt status`);
    return;
  }
  assert(typeof status === "string" && status.trim()
    && typeof statusType === "string" && statusType.trim()
    && ["info", "warn", "danger", "success"].includes(statusType.trim().toLowerCase()),
  `${subject} contained unusable attempt facts`);
  assert(!isTerminalPaymentAttemptStatus(status),
    `${subject} pending state carried terminal attempt facts`);
  assert(payment.latestAttemptCompletedAt == null,
    `${subject} non-successful attempt carried completion time`);
}

function isValidDateTime(value) {
  if (typeof value !== "string") {
    return false;
  }
  const normalizedValue = value.trim().replace("T", " ");
  const match = /^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})(?:\.\d{1,9})?$/.exec(normalizedValue);
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

function isValidLocalDateTime(value) {
  return isValidDateTime(typeof value === "string" ? value.trim().replace("T", " ") : value);
}

function toLocalTimestamp(value) {
  return Date.parse(typeof value === "string" ? value.trim().replace("T", " ") : "");
}

export function formatRequestError(url, error) {
  const detail = error instanceof Error ? error.message : String(error);
  return `${url} request failed: ${detail}; check that the target service is running and reachable`;
}

export function assertPrepayOrder(prepay, expectedOrderNo, expectedAmount, expectedPayScene = null) {
  assert(typeof prepay?.prepayOrderNo === "string" && prepay.prepayOrderNo.trim(),
    "prepay response did not contain a usable prepay order number");
  assert(typeof prepay?.paymentOrderId === "string" && prepay.paymentOrderId.trim(),
    "prepay response did not contain a usable payment order id");
  assert(matchesIdentifier(prepay.businessChainId, `CHAIN-${prepay.paymentOrderId.trim()}`),
    `prepay response did not match business chain: ${prepay?.businessChainId}`);
  assert(isValidDateTime(prepay.createdAt),
    "prepay response did not contain a usable creation time");
  assert(isValidDateTime(prepay.expiresAt),
    "prepay response did not contain a usable expiry time");
  assert(toLocalTimestamp(prepay.createdAt) <= toLocalTimestamp(prepay.expiresAt),
    "prepay response expiry time preceded its creation time");
  assert(!expectedOrderNo || matchesIdentifier(prepay.orderNo, expectedOrderNo),
    `prepay response order number did not match the requested order: ${prepay?.orderNo}`);
  if (expectedAmount != null) {
    assertPaymentAmount(prepay, expectedAmount);
  }
  assert(prepay.paymentStatus === "PREPAY_CREATED",
    `prepay response was not ready for payment: ${prepay?.paymentStatus}`);
  assert(prepay.cashierStatus === "待支付" && prepay.cashierStatusType === "warn",
    `prepay response cashier state was not pending payment: ${prepay?.cashierStatus}/${prepay?.cashierStatusType}`);
  if (expectedPayScene != null) {
    assert(typeof prepay.payScene === "string" && prepay.payScene.trim(),
      "prepay response did not contain a usable payment scene");
    assert(matchesIdentifier(prepay.payScene, expectedPayScene),
      `prepay response payment scene did not match request: ${prepay?.payScene}`);
  }
  assertPendingPaymentAttemptFacts(prepay, "prepay response");
  return prepay;
}

export function assertCashierPage(
  cashier,
  expectedPrepayOrderNo,
  expectedPaymentOrderId,
  expectedOrderNo,
  expectedAmount,
  expectedExpiresAt,
  expectedPayScene = null
) {
  assert(matchesIdentifier(cashier?.prepayOrderNo, expectedPrepayOrderNo),
  `cashier response did not match prepay order: ${cashier?.prepayOrderNo}`);
  assert(matchesIdentifier(cashier?.paymentOrderId, expectedPaymentOrderId),
  `cashier response did not match payment order: ${cashier?.paymentOrderId}`);
  assert(matchesIdentifier(cashier.businessChainId, paymentBusinessChainId(expectedPaymentOrderId)),
    `cashier response did not match business chain: ${cashier?.businessChainId}`);
  assert(matchesIdentifier(cashier?.orderNo, expectedOrderNo),
    `cashier response did not match business order: ${cashier?.orderNo}`);
  assertPaymentAmount(cashier, expectedAmount);
  assert(cashier.status === "待支付"
    && cashier.statusType === "warn"
    && cashier.paymentStatus === "PREPAY_CREATED",
  `cashier response was not ready for payment: ${cashier?.status}/${cashier?.statusType}/${cashier?.paymentStatus}`);
  assertPendingPaymentAttemptFacts(cashier, "cashier response");
  assert(isValidDateTime(cashier.expiresAt),
    "cashier response did not contain a usable expiry time");
  assert(!expectedExpiresAt || cashier.expiresAt === expectedExpiresAt,
    `cashier response expiry time did not match prepay order: ${cashier?.expiresAt}`);
  if (expectedPayScene != null) {
    assert(typeof cashier.payScene === "string" && cashier.payScene.trim(),
      "cashier response did not contain a usable payment scene");
    assert(matchesIdentifier(cashier.payScene, expectedPayScene),
      `cashier response payment scene did not match request: ${cashier?.payScene}`);
  }
  assert(Array.isArray(cashier.channels) && cashier.channels.length > 0,
    "cashier response did not contain an available payment channel");
  assert(cashier.channels.every((channel) => typeof channel === "string"
    && CASHIER_CHANNELS.has(channel.trim())),
  `cashier response contained an unknown payment channel: ${cashier.channels}`);
  return cashier;
}

export function assertBoundCashierPage(cashier, detail, expectedStatus, expectedAmount) {
  assert(["WAIT_CALLBACK", "SUCCESS"].includes(expectedStatus) && detail?.status === expectedStatus,
    "bound cashier reference did not contain the expected payment state");
  assertPaymentIdentity(cashier, detail.paymentOrderId, detail.prepayOrderNo,
    detail.orderNo, detail.businessChainId, detail.payScene);
  assertPaymentAmount(cashier, expectedAmount);
  assertPaymentAmount(detail, expectedAmount);
  const expectedDisplay = expectedStatus === "SUCCESS"
    ? { status: "支付成功", type: "success" }
    : { status: "待回调", type: "warn" };
  assert(cashier.paymentStatus === expectedStatus
    && cashier.status === expectedDisplay.status && cashier.statusType === expectedDisplay.type,
  "bound cashier did not preserve the payment display state");
  for (const field of ["paymentMethod", "channel", "channelTransactionNo"]) {
    assert(typeof detail[field] === "string" && detail[field].trim()
      && cashier[field] === detail[field],
    `bound cashier did not preserve the original ${field}`);
  }
  assert(typeof detail.latestAttemptStatus === "string" && detail.latestAttemptStatus.trim()
    && cashier.latestAttemptStatus === detail.latestAttemptStatus
    && cashier.latestAttemptStatusType === detail.latestAttemptStatusType
    && cashier.latestAttemptCompletedAt === detail.latestAttemptCompletedAt,
  "bound cashier did not preserve the latest channel attempt");
  if (expectedStatus === "SUCCESS") {
    assert(detail.latestAttemptStatus === "成功"
      && detail.latestAttemptStatusType === "success"
      && isValidDateTime(detail.latestAttemptCompletedAt),
    "bound cashier success lacked completed attempt facts");
  } else {
    assert(!isTerminalPaymentAttemptStatus(detail.latestAttemptStatus)
      && detail.latestAttemptCompletedAt == null,
    "bound cashier waiting state carried a terminal attempt");
  }
  return cashier;
}

export function assertPaymentIdentity(
  payment,
  expectedPaymentOrderId,
  expectedPrepayOrderNo,
  expectedOrderNo,
  expectedBusinessChainId = null,
  expectedPayScene = null
) {
  assert(matchesIdentifier(payment?.paymentOrderId, expectedPaymentOrderId),
  `payment response did not match payment order: ${payment?.paymentOrderId}`);
  assert(matchesIdentifier(payment?.prepayOrderNo, expectedPrepayOrderNo),
  `payment response did not match prepay order: ${payment?.prepayOrderNo}`);
  assert(!expectedOrderNo || matchesIdentifier(payment?.orderNo, expectedOrderNo),
  `payment response did not match business order: ${payment?.orderNo}`);
  if (expectedBusinessChainId != null) {
    assert(matchesIdentifier(payment?.businessChainId, expectedBusinessChainId),
      `payment response did not match business chain: ${payment?.businessChainId}`);
  }
  if (expectedPayScene != null) {
    assert(typeof payment?.payScene === "string" && payment.payScene.trim(),
      "payment response did not contain a usable payment scene");
    assert(matchesIdentifier(payment.payScene, expectedPayScene),
      `payment response payment scene did not match request: ${payment?.payScene}`);
  }
  return payment;
}

export function assertPaymentBusinessFacts(payment, expectedBillNo, expectedCustomerName, subject = "payment response") {
  assert(typeof payment?.customerName === "string" && payment.customerName.trim()
    && matchesCustomerName(payment.customerName, expectedCustomerName),
  `${subject} did not preserve customer name: ${payment?.customerName}`);
  if (expectedBillNo != null) {
    assert(typeof payment?.billNo === "string" && payment.billNo.trim()
      && matchesIdentifier(payment.billNo, expectedBillNo),
    `${subject} did not preserve bill number: ${payment?.billNo}`);
  }
  return payment;
}

export function assertBillPaymentFacts(
  bill, expectedBillNo, expectedOrderNo, expectedPrepayOrderNo,
  expectedPaymentOrderId, expectedPayScene, expectedAmount,
  expectedPaidAmount, expectedUnpaidAmount, expectedBillStatus,
  expectedBillStatusType, expectedPaymentStatus
) {
  assert(matchesIdentifier(bill?.billNo, expectedBillNo)
    && matchesIdentifier(bill?.orderNo, expectedOrderNo)
    && matchesIdentifier(bill?.latestPrepayOrderNo, expectedPrepayOrderNo)
    && matchesIdentifier(bill?.latestPaymentPrepayOrderNo, expectedPrepayOrderNo)
    && matchesIdentifier(bill?.latestPaymentOrderId, expectedPaymentOrderId)
    && matchesIdentifier(bill?.latestPayScene, expectedPayScene),
  "bill detail did not preserve payment identity or scene");
  assertPaymentAmount({ amount: bill.billAmount }, expectedAmount);
  assertPaymentAmount({ amount: bill.latestPrepayAmount }, expectedAmount);
  assertPaymentAmount({ amount: bill.latestPaymentAmount }, expectedAmount);
  assertPaymentAmount({ amount: bill.paidAmount }, expectedPaidAmount);
  assertPaymentAmount({ amount: bill.unpaidAmount }, expectedUnpaidAmount);
  const successful = expectedPaymentStatus === "SUCCESS";
  assert(bill.billStatus === expectedBillStatus
    && bill.billStatusType === expectedBillStatusType
    && bill.latestPaymentStatus === expectedPaymentStatus
    && bill.latestPaymentStatusType === (successful ? "success" : "info")
    && bill.latestCashierStatus === (successful ? "支付成功" : "待支付")
    && bill.latestCashierStatusType === (successful ? "success" : "warn"),
  `bill detail state did not match payment: ${bill?.billStatus}/${bill?.billStatusType}/${bill?.latestPaymentStatus}`);
  return bill;
}

export function assertPaymentSubmission(
  payment,
  expectedPaymentOrderId,
  expectedPrepayOrderNo,
  expectedOrderNo,
  expectedAmount,
  expectedExpiresAt,
  expectedBusinessChainId = null,
  expectedPaymentMethod = null,
  expectedChannel = null,
  expectedPayScene = null
) {
  assertPaymentIdentity(
    payment,
    expectedPaymentOrderId,
    expectedPrepayOrderNo,
    expectedOrderNo,
    expectedBusinessChainId
  );
  assertPaymentAmount(payment, expectedAmount);
  assert(payment.paymentStatus === "WAIT_CALLBACK",
    `payment submission cashier state did not remain pending callback: ${payment?.paymentStatus}`);
  assert(payment.cashierStatus === "支付中" && payment.cashierStatusType === "info",
    `payment submission cashier state was not pending callback: ${payment?.cashierStatus}/${payment?.cashierStatusType}`);
  if (expectedPayScene != null) {
    assert(typeof payment.payScene === "string" && payment.payScene.trim(),
      "payment submission did not contain a usable payment scene");
    assert(matchesIdentifier(payment.payScene, expectedPayScene),
      `payment submission payment scene did not match request: ${payment?.payScene}`);
  }
  assert(typeof payment.paymentMethod === "string" && payment.paymentMethod.trim()
    && typeof payment.channel === "string" && payment.channel.trim()
    && typeof payment.channelTransactionNo === "string" && payment.channelTransactionNo.trim(),
  "payment submission did not contain usable payment method or channel facts");
  if (expectedPaymentMethod != null) {
    assert(matchesIdentifier(payment.paymentMethod, expectedPaymentMethod),
      `payment submission payment method did not match request: ${payment?.paymentMethod}`);
  }
  if (expectedChannel != null) {
    assert(matchesIdentifier(payment.channel, expectedChannel),
      `payment submission channel did not match request: ${payment?.channel}`);
  }
  assert(typeof payment.latestAttemptStatus === "string" && payment.latestAttemptStatus.trim()
    && typeof payment.latestAttemptStatusType === "string"
    && ["info", "warn", "danger", "success"].includes(payment.latestAttemptStatusType.trim().toLowerCase()),
  "payment submission did not contain usable attempt facts");
  assert(!isTerminalPaymentAttemptStatus(payment.latestAttemptStatus),
    "payment submission pending state carried terminal attempt facts");
  assert(payment.latestAttemptCompletedAt == null,
    "payment submission non-successful attempt carried completion time");
  assert(isValidDateTime(payment.createdAt),
    "payment submission did not contain a usable creation time");
  assert(isValidDateTime(payment.expiresAt),
    "payment submission did not contain a usable expiry time");
  assert(toLocalTimestamp(payment.createdAt) <= toLocalTimestamp(payment.expiresAt),
    "payment submission expiry time preceded its creation time");
  assert(!expectedExpiresAt || payment.expiresAt === expectedExpiresAt,
    `payment submission expiry time did not match prepay order: ${payment?.expiresAt}`);
  return payment;
}

export function assertIdempotentPaymentCallback(
  first,
  repeated,
  expectedPaymentOrderId,
  expectedPrepayOrderNo,
  expectedOrderNo,
  expectedAmount,
  expectedPaymentMethod = null,
  expectedChannel = null,
  expectedPayScene = null
) {
  const expectedBusinessChainId = paymentBusinessChainId(expectedPaymentOrderId);
  assertPaymentIdentity(first, expectedPaymentOrderId, expectedPrepayOrderNo, expectedOrderNo, expectedBusinessChainId, expectedPayScene);
  assertPaymentIdentity(repeated, expectedPaymentOrderId, expectedPrepayOrderNo, expectedOrderNo, expectedBusinessChainId, expectedPayScene);
  assertPaymentAmount(first, expectedAmount);
  assertPaymentAmount(repeated, expectedAmount);
  assert(isValidDateTime(first.createdAt) && isValidDateTime(repeated.createdAt),
    "payment callback did not contain a usable creation time");
  assert(first.status === "SUCCESS" && repeated.status === "SUCCESS",
    `payment callback did not remain successful: ${first.status}/${repeated.status}`);
  assert(first.statusType === "success" && repeated.statusType === "success",
  `payment callback terminal display state was inconsistent: ${first.status}/${first.statusType}/${repeated.status}/${repeated.statusType}`);
  assert(isValidDateTime(first.successAt) && isValidDateTime(repeated.successAt),
    "payment callback did not contain a usable success time");
  assert(toLocalTimestamp(first.createdAt) <= toLocalTimestamp(first.successAt),
    "payment callback success time preceded its creation time");
  assert(toLocalTimestamp(repeated.createdAt) <= toLocalTimestamp(repeated.successAt),
    "repeated payment callback success time preceded its creation time");
  assert(first.latestAttemptStatus === "成功" && repeated.latestAttemptStatus === "成功"
    && first.latestAttemptStatusType === "success"
    && repeated.latestAttemptStatusType === "success",
  `payment callback latest attempt was not successful: ${first.latestAttemptStatus}/${first.latestAttemptStatusType}`);
  assert(isValidDateTime(first.latestAttemptCompletedAt)
    && isValidDateTime(repeated.latestAttemptCompletedAt),
  "payment callback did not contain a usable latest attempt completion time");
  assert(toLocalTimestamp(first.createdAt) <= toLocalTimestamp(first.latestAttemptCompletedAt),
    "payment callback latest attempt completion preceded its creation time");
  assert(toLocalTimestamp(repeated.createdAt) <= toLocalTimestamp(repeated.latestAttemptCompletedAt),
    "repeated payment callback latest attempt completion preceded its creation time");
  assert(toLocalTimestamp(first.latestAttemptCompletedAt) <= toLocalTimestamp(first.successAt),
    "payment callback latest attempt completion followed its success time");
  assert(toLocalTimestamp(repeated.latestAttemptCompletedAt) <= toLocalTimestamp(repeated.successAt),
    "repeated payment callback latest attempt completion followed its success time");
  assert(typeof first.channel === "string" && first.channel.trim()
    && typeof repeated.channel === "string" && repeated.channel.trim()
    && typeof first.paymentMethod === "string" && first.paymentMethod.trim()
    && typeof repeated.paymentMethod === "string" && repeated.paymentMethod.trim()
    && typeof first.channelTransactionNo === "string" && first.channelTransactionNo.trim()
    && typeof repeated.channelTransactionNo === "string" && repeated.channelTransactionNo.trim()
    && matchesIdentifier(repeated.paymentOrderId, first.paymentOrderId.trim())
    && matchesIdentifier(repeated.paymentMethod, first.paymentMethod.trim())
    && matchesIdentifier(repeated.channel, first.channel.trim())
    && matchesIdentifier(repeated.channelTransactionNo, first.channelTransactionNo.trim()),
  "repeated payment callback changed the original payment entity");
  if (expectedPaymentMethod != null) {
    assert(matchesIdentifier(first.paymentMethod, expectedPaymentMethod)
      && matchesIdentifier(repeated.paymentMethod, expectedPaymentMethod),
    "payment callback payment method did not match the submitted payment");
  }
  if (expectedChannel != null) {
    assert(matchesIdentifier(first.channel, expectedChannel)
      && matchesIdentifier(repeated.channel, expectedChannel),
    "payment callback channel did not match the submitted payment");
  }
  return repeated;
}

export function assertPaymentSuccessDetail(
  callback,
  detail,
  expectedPaymentOrderId,
  expectedPrepayOrderNo,
  expectedOrderNo,
  expectedAmount,
  expectedPayScene = null
) {
  assertIdempotentPaymentCallback(
    callback,
    detail,
    expectedPaymentOrderId,
    expectedPrepayOrderNo,
    expectedOrderNo,
    expectedAmount,
    null,
    null,
    expectedPayScene
  );
  assertPaymentIdentity(
    detail,
    expectedPaymentOrderId,
    expectedPrepayOrderNo,
    expectedOrderNo,
    paymentBusinessChainId(expectedPaymentOrderId),
    expectedPayScene
  );
  assert(matchesIdentifier(detail.successAt, callback.successAt),
    "payment detail did not preserve callback success time");
  assert(matchesIdentifier(detail.channel, callback.channel)
    && matchesIdentifier(detail.channelTransactionNo, callback.channelTransactionNo),
  "payment detail did not preserve callback channel facts");
  return detail;
}

export function assertPaymentRecordProjection(
  page, detail, expectedPaymentOrderId, expectedOrderNo, expectedPayScene, expectedAmount,
  expectedChannelReturnCode, expectedPrepayExpiresAt, expectedChannelTransactionNo,
  expectedGatewayCode = null
) {
  const records = Array.isArray(page?.items) ? page.items : [];
  assert(page?.total === 1 && records.length === 1,
    `payment record query did not return exactly one record: ${page?.total}/${records.length}`);
  for (const record of [records[0], detail]) {
    assert(matchesIdentifier(record?.paymentOrderId, expectedPaymentOrderId)
      && matchesIdentifier(record?.businessChainId, paymentBusinessChainId(expectedPaymentOrderId))
      && matchesIdentifier(record?.businessOrderNo, expectedOrderNo)
      && matchesIdentifier(record?.payScene, expectedPayScene),
    "payment record did not preserve the paid payment identity and scene");
    assert(record.paymentType === "消费支付" && record.paymentStatus === "支付成功"
      && parseMoneyAmount(record.paymentAmount) === parseMoneyAmount(expectedAmount)
      && isValidDateTime(record.updatedAt) && isValidDateTime(record.paidAt),
    "payment record did not preserve paid amount, type, status and lifecycle times");
    assert(record.channelReturnCode === expectedChannelReturnCode,
      "payment record channel return code did not match the payment snapshot");
    assert(record.paymentGateway === expectedGatewayCode,
      "payment record gateway did not match the captured attempt gateway");
    assert(record.returnParameterType === "渠道交易号"
      && matchesIdentifier(record.parameterValue, expectedChannelTransactionNo),
    "payment record channel transaction parameter did not match the payment snapshot");
    assert(matchesIdentifier(record.expireTime, expectedPrepayExpiresAt),
      "payment record expiry did not match the prepay fact");
  }
  assert(matchesIdentifier(records[0].paymentRequestNo, detail.paymentRequestNo)
    && matchesIdentifier(records[0].applicationSourceId, detail.applicationSourceId)
    && matchesIdentifier(records[0].latestAttemptStatus, detail.latestAttemptStatus)
    && matchesIdentifier(records[0].latestAttemptCompletedAt, detail.latestAttemptCompletedAt),
  "payment record list did not preserve the detail's latest payment attempt");
  return detail;
}

export function assertPaymentRecordEventTopic(record, event) {
  assert(typeof event?.eventTopic === "string" && event.eventTopic.trim()
    && matchesIdentifier(record?.callbackMqTopic, event.eventTopic),
  "payment record event topic did not match the persisted success event");
  return record;
}

export function assertPaymentRequestDiagnostics(
  page, overview, detail, paymentOrderId, expectedStatus, expectedStyle, expectedCountField
) {
  assert(page?.total === 1 && page.items?.length === 1,
    "payment request list did not contain exactly one attempt");
  const request = page.items[0];
  assert(matchesIdentifier(request.paymentOrderId, paymentOrderId)
    && typeof request.requestNo === "string" && request.requestNo.trim()
    && matchesIdentifier(detail?.requestNo, request.requestNo)
    && matchesIdentifier(detail?.paymentOrderId, paymentOrderId),
  "payment request detail did not match the submitted attempt");
  assert(request.requestStatus === expectedStatus && request.requestStatusType === expectedStyle
    && detail.requestStatus === expectedStatus && detail.requestStatusType === expectedStyle,
  "payment request list and detail status did not match the persisted attempt");
  assert(overview?.totalRequestCount === 1
    && overview[expectedCountField] === 1
    && ["successRequestCount", "failedRequestCount", "processingRequestCount", "waitingCallbackRequestCount"]
      .filter((field) => field !== expectedCountField).every((field) => overview[field] === 0),
  "payment request overview did not match the filtered attempt status");
  return request;
}

export function assertIdempotentPaymentSubmission(
  first,
  repeated,
  expectedPaymentOrderId,
  expectedPrepayOrderNo,
  expectedOrderNo,
  expectedAmount,
  expectedExpiresAt,
  expectedPaymentMethod = null,
  expectedChannel = null,
  expectedPayScene = null
) {
  const expectedBusinessChainId = paymentBusinessChainId(expectedPaymentOrderId);
  assertPaymentSubmission(
    first, expectedPaymentOrderId, expectedPrepayOrderNo, expectedOrderNo,
    expectedAmount, expectedExpiresAt, expectedBusinessChainId,
    expectedPaymentMethod, expectedChannel, expectedPayScene
  );
  assertPaymentSubmission(
    repeated, expectedPaymentOrderId, expectedPrepayOrderNo, expectedOrderNo,
    expectedAmount, expectedExpiresAt, expectedBusinessChainId,
    expectedPaymentMethod, expectedChannel, expectedPayScene
  );
  assert(matchesIdentifier(repeated.paymentOrderId, first.paymentOrderId.trim())
    && matchesIdentifier(repeated.prepayOrderNo, first.prepayOrderNo.trim())
    && matchesIdentifier(repeated.orderNo, first.orderNo.trim())
    && matchesIdentifier(repeated.paymentMethod, first.paymentMethod.trim())
    && matchesIdentifier(repeated.channel, first.channel.trim())
    && matchesIdentifier(repeated.channelTransactionNo, first.channelTransactionNo.trim())
    && matchesIdentifier(repeated.latestAttemptStatus, first.latestAttemptStatus.trim())
    && matchesIdentifier(repeated.latestAttemptStatusType, first.latestAttemptStatusType.trim())
    && matchesIdentifier(repeated.createdAt, first.createdAt.trim())
    && matchesIdentifier(repeated.expiresAt, first.expiresAt.trim()),
  "repeated payment submission did not return the original payment entity");
  return repeated;
}

export function assertSuccessfulPaymentResubmission(
  detail, repeated, expectedPrepayOrderNo, expectedOrderNo, expectedBusinessChainId
) {
  assertPaymentIdentity(
    repeated, detail.paymentOrderId, expectedPrepayOrderNo, expectedOrderNo, expectedBusinessChainId
  );
  assertPaymentAmount(repeated, detail.amount);
  assert(repeated.paymentStatus === "SUCCESS"
    && repeated.cashierStatus === "支付成功"
    && repeated.cashierStatusType === "success",
  "repeated submission did not preserve the successful cashier state");
  assert(matchesIdentifier(repeated.paymentMethod, detail.paymentMethod)
    && matchesIdentifier(repeated.channel, detail.channel)
    && matchesIdentifier(repeated.channelTransactionNo, detail.channelTransactionNo)
    && repeated.latestAttemptStatus === "成功"
    && repeated.latestAttemptStatusType === "success"
    && isValidDateTime(repeated.latestAttemptCompletedAt)
    && matchesIdentifier(repeated.latestAttemptCompletedAt, detail.latestAttemptCompletedAt)
    && isValidDateTime(repeated.successAt)
    && matchesIdentifier(repeated.successAt, detail.successAt),
  "repeated submission changed the successful payment facts");
  return repeated;
}

export function assertPaymentAmount(payment, expectedAmount) {
  const normalizedAmount = parseMoneyAmount(payment?.amount);
  const normalizedExpectedAmount = parseMoneyAmount(expectedAmount);
  assert(normalizedAmount != null
    && normalizedExpectedAmount != null
    && normalizedAmount === normalizedExpectedAmount,
  `payment response amount did not match expected amount: ${payment?.amount}`);
  return payment;
}

export function assertRefundAmount(refundOrder, expectedAmount) {
  const normalizedAmount = parseMoneyAmount(refundOrder?.refundAmount);
  const normalizedExpectedAmount = parseMoneyAmount(expectedAmount);
  assert(normalizedAmount != null
    && normalizedExpectedAmount != null
    && normalizedAmount === normalizedExpectedAmount,
  `refund response amount did not match expected amount: ${refundOrder?.refundAmount}`);
  return refundOrder;
}

export function assertRefundPaidAmount(refundOrder, expectedAmount) {
  assertPaymentAmount({ amount: refundOrder?.paidAmount }, expectedAmount);
  return refundOrder;
}

export function assertRefundCustomerName(refundOrder, expectedCustomerName, stage = "refund response") {
  assert(typeof refundOrder?.customerName === "string"
    && refundOrder.customerName.trim()
    && matchesCustomerName(refundOrder.customerName, expectedCustomerName),
  `refund ${stage} did not preserve customer name: ${refundOrder?.customerName}`);
  return refundOrder;
}

export function assertRefundPayScene(refundOrder, expectedPayScene, stage = "refund response") {
  assert(typeof refundOrder?.payScene === "string"
    && refundOrder.payScene.trim()
    && matchesIdentifier(refundOrder.payScene, expectedPayScene),
  `refund ${stage} did not preserve payment scene: ${refundOrder?.payScene}`);
  return refundOrder;
}

export function assertRefundMethod(refundOrder, expectedMethod) {
  assert(matchesIdentifier(refundOrder?.refundMethod, expectedMethod),
    `refund response did not preserve refund method: ${refundOrder?.refundMethod}`);
  return refundOrder;
}

export function assertRefundReason(refundOrder, expectedReason) {
  assert(typeof refundOrder?.refundReason === "string"
    && refundOrder.refundReason.trim() === normalizeIdentifier(expectedReason),
  `refund response did not preserve refund reason: ${refundOrder?.refundReason}`);
  return refundOrder;
}

export function assertRefundChannelRefundId(refundOrder, expectedChannelRefundId, stage) {
  assert(typeof expectedChannelRefundId === "string" && expectedChannelRefundId.trim()
    && typeof refundOrder?.channelRefundId === "string"
    && refundOrder.channelRefundId.trim()
    && matchesIdentifier(refundOrder.channelRefundId, expectedChannelRefundId),
  `refund ${stage} did not preserve the channel refund identifier: ${refundOrder?.channelRefundId}`);
  return refundOrder;
}

export function assertRefundLifecycleTime(refundOrder, stage) {
  assert(isValidDateTime(refundOrder?.appliedAt),
    `refund ${stage} did not contain a usable applied time`);
  const terminal = ["SUCCESS", "FAIL"].includes(refundOrder?.status);
  if (terminal) {
    assert(isValidDateTime(refundOrder?.channelSubmittedAt)
        && refundOrder.channelSubmittedAt !== "-",
    `refund ${stage} did not contain a usable channel submitted time`);
  }
  if (refundOrder?.channelSubmittedAt != null && refundOrder.channelSubmittedAt !== "-") {
    assert(isValidDateTime(refundOrder.channelSubmittedAt),
      `refund ${stage} did not contain a usable channel submitted time`);
    assert(toLocalTimestamp(refundOrder.appliedAt) <= toLocalTimestamp(refundOrder.channelSubmittedAt),
      `refund ${stage} channel submitted time preceded its applied time`);
  }
  if (refundOrder?.status === "SUCCESS") {
    assert(isValidDateTime(refundOrder.successAt),
      `refund ${stage} did not contain a usable success time`);
  } else {
    assert(refundOrder?.successAt == null,
      `refund ${stage} contained a success time before terminal success`);
  }
  if (["SUCCESS", "FAIL"].includes(refundOrder?.status)) {
    assert(isValidDateTime(refundOrder.terminalAt),
      `refund ${stage} did not contain a usable terminal time`);
  } else {
    assert(refundOrder?.terminalAt == null,
      `refund ${stage} contained a terminal time before terminal status`);
  }
  if (refundOrder?.channelSubmittedAt != null && refundOrder.channelSubmittedAt !== "-"
      && isValidDateTime(refundOrder.terminalAt)) {
    assert(toLocalTimestamp(refundOrder.channelSubmittedAt) <= toLocalTimestamp(refundOrder.terminalAt),
      `refund ${stage} channel submitted time followed its terminal time`);
  }
  const appliedTimestamp = toLocalTimestamp(refundOrder.appliedAt);
  if (refundOrder?.status === "SUCCESS") {
    assert(appliedTimestamp <= toLocalTimestamp(refundOrder.successAt),
      `refund ${stage} success time preceded its applied time`);
    assert(toLocalTimestamp(refundOrder.successAt) <= toLocalTimestamp(refundOrder.terminalAt),
      `refund ${stage} terminal time preceded its success time`);
  } else if (["FAIL"].includes(refundOrder?.status)) {
    assert(appliedTimestamp <= toLocalTimestamp(refundOrder.terminalAt),
      `refund ${stage} terminal time preceded its applied time`);
  }
  return refundOrder;
}

function assertRefundLifecycleContinuity(first, repeated, stage) {
  for (const field of ["appliedAt", "channelSubmittedAt", "successAt", "terminalAt"]) {
    const firstValue = first?.[field] === "-" ? null : first?.[field];
    const repeatedValue = repeated?.[field] === "-" ? null : repeated?.[field];
    assert((firstValue == null && repeatedValue == null)
      || (typeof firstValue === "string" && typeof repeatedValue === "string"
        && matchesIdentifier(repeatedValue, firstValue.trim())),
    `refund ${stage} changed the original ${field} fact`);
  }
}

export function assertIdempotentRefundApplication(
  first, repeated, expectedPaymentOrderId, expectedAmount, expectedOrderNo, expectedReason
) {
  const expectedBusinessChainId = paymentBusinessChainId(expectedPaymentOrderId);
  assertRefundIdentity(first, "", expectedPaymentOrderId, expectedOrderNo, expectedBusinessChainId);
  assertRefundIdentity(repeated, "", expectedPaymentOrderId, expectedOrderNo, expectedBusinessChainId);
  assertRefundAmount(first, expectedAmount);
  assertRefundAmount(repeated, expectedAmount);
  assertRefundLifecycleTime(first, "application");
  assertRefundLifecycleTime(repeated, "repeated application");
  assertRefundLifecycleContinuity(first, repeated, "repeated application");
  assertRefundStatus(first, "REVIEWING", "application");
  assertRefundStatus(repeated, "REVIEWING", "repeated application");
  if (expectedReason != null) {
    assertRefundReason(first, expectedReason);
    assertRefundReason(repeated, expectedReason);
  }
  assert(matchesIdentifier(repeated.refundOrderId, first.refundOrderId.trim()),
    "repeated refund application did not return the original refund entity");
  return repeated;
}

export function assertIdempotentRefundCallback(
  first, repeated, expectedRefundOrderId, expectedPaymentOrderId, expectedAmount, expectedOrderNo, expectedReason
) {
  const expectedBusinessChainId = paymentBusinessChainId(expectedPaymentOrderId);
  assertRefundIdentity(first, expectedRefundOrderId, expectedPaymentOrderId, expectedOrderNo, expectedBusinessChainId);
  assertRefundIdentity(repeated, expectedRefundOrderId, expectedPaymentOrderId, expectedOrderNo, expectedBusinessChainId);
  assertRefundAmount(first, expectedAmount);
  assertRefundAmount(repeated, expectedAmount);
  assertRefundLifecycleTime(first, "callback");
  assertRefundLifecycleTime(repeated, "repeated callback");
  assertRefundLifecycleContinuity(first, repeated, "repeated callback");
  assertRefundStatus(first, "SUCCESS", "callback");
  assertRefundStatus(repeated, "SUCCESS", "repeated callback");
  assert(first.status === "SUCCESS" && repeated.status === "SUCCESS",
    `refund callback did not remain successful: ${first.status}/${repeated.status}`);
  if (expectedReason != null) {
    assertRefundReason(first, expectedReason);
    assertRefundReason(repeated, expectedReason);
  }
  assert(matchesIdentifier(repeated.refundOrderId, first.refundOrderId.trim()),
    "repeated refund callback changed the original refund entity");
  assert(typeof first.channelRefundId === "string" && first.channelRefundId.trim()
    && typeof repeated.channelRefundId === "string" && repeated.channelRefundId.trim()
    && typeof first.channelCode === "string" && first.channelCode.trim()
    && typeof repeated.channelCode === "string" && repeated.channelCode.trim()
    && matchesIdentifier(repeated.channelRefundId, first.channelRefundId.trim()),
  "repeated refund callback changed the original channel refund identifier");
  assert(matchesIdentifier(repeated.channelCode, first.channelCode.trim()),
    "repeated refund callback changed the original channel code");
  return repeated;
}

export function assertRefundSuccessDetail(
  callback,
  detail,
  expectedRefundOrderId,
  expectedPaymentOrderId,
  expectedAmount,
  expectedOrderNo,
  expectedReason
) {
  assertIdempotentRefundCallback(
    callback,
    detail,
    expectedRefundOrderId,
    expectedPaymentOrderId,
    expectedAmount,
    expectedOrderNo,
    expectedReason
  );
  assert(matchesIdentifier(detail.successAt, callback.successAt),
    "refund detail did not preserve callback success time");
  assert(matchesIdentifier(detail.terminalAt, callback.terminalAt),
    "refund detail did not preserve callback terminal time");
  assert(matchesIdentifier(detail.channelRefundId, callback.channelRefundId),
    "refund detail did not preserve callback channel refund identifier");
  return detail;
}

export function assertRefundWithinPayment(refundOrder, paymentAmount) {
  const refundAmount = parseMoneyAmount(refundOrder?.refundAmount);
  const paidAmount = parseMoneyAmount(paymentAmount);
  assert(refundAmount != null && refundAmount > 0n
    && paidAmount != null && paidAmount > 0n
    && refundAmount <= paidAmount,
  `refund amount ${refundOrder?.refundAmount} exceeded payment amount ${paymentAmount}`);
  return refundOrder;
}

export function assertSettlementDraft(settlementOrder) {
  assert(settlementOrder?.settlementStatus === "待审核",
    `settlement order was not awaiting audit: ${settlementOrder?.settlementStatus}`);
  assert(settlementOrder?.auditStatus === "待审核",
    `settlement audit was not awaiting audit: ${settlementOrder?.auditStatus}`);
  assert(settlementOrder?.payoutStatus === "待出款",
    `settlement payout was not awaiting payout: ${settlementOrder?.payoutStatus}`);
  assert(settlementOrder?.settlementStatusType === "warn"
    && settlementOrder?.auditStatusType === "warn"
    && settlementOrder?.payoutStatusType === "warn",
  "settlement draft did not contain the expected pending status styles");
  assert(isValidDateTime(settlementOrder.createdAt),
    "settlement draft did not contain a usable creation time");
  return settlementOrder;
}

export function findSettlementOrder(orders, expectedClearingNo, expectedBusinessChainId = null) {
  return findUniqueRecord(
    orders,
    (order) => matchesIdentifier(order?.clearingNo, expectedClearingNo)
      && order?.settlementStatus === "待审核"
      && (expectedBusinessChainId == null
        || matchesIdentifier(order?.businessChainId, expectedBusinessChainId)),
    "settlement order"
  );
}

export function assertSettlementIdentity(
  settlementOrder,
  expectedClearingNo,
  expectedBusinessChainId,
  expectedClearingCreatedAt,
  expectedTargetNo = null,
  expectedPayScene = null
) {
  assert(typeof settlementOrder?.settlementNo === "string" && settlementOrder.settlementNo.trim(),
    "settlement response did not contain a usable settlement number");
  assert(typeof settlementOrder?.clearingNo === "string"
    && matchesIdentifier(settlementOrder.clearingNo, expectedClearingNo),
  `settlement response did not match clearing order: ${settlementOrder?.clearingNo}`);
  assert(matchesIdentifier(settlementOrder.businessChainId, expectedBusinessChainId),
    `settlement response did not match business chain: ${settlementOrder?.businessChainId}`);
  assert(typeof settlementOrder.batchNo === "string" && settlementOrder.batchNo.trim(),
    "settlement response did not contain a usable settlement batch number");
  assert(settlementOrder.targetType === "WORKER",
    `settlement response did not target a worker: ${settlementOrder?.targetType}`);
  assert(typeof settlementOrder.targetNo === "string" && settlementOrder.targetNo.trim(),
    "settlement response did not contain a usable worker target number");
  if (expectedTargetNo != null) {
    assert(matchesIdentifier(settlementOrder.targetNo, expectedTargetNo),
      `settlement response did not preserve clearing target: ${settlementOrder?.targetNo}`);
  }
  if (expectedPayScene != null) {
    assert(matchesIdentifier(settlementOrder.payScene, expectedPayScene),
      `settlement response did not preserve payment scene: ${settlementOrder?.payScene}`);
  }
  if (expectedClearingCreatedAt != null) {
    assert(isValidDateTime(settlementOrder.createdAt),
      "settlement response did not contain a usable creation time");
    assert(toLocalTimestamp(expectedClearingCreatedAt) <= toLocalTimestamp(settlementOrder.createdAt),
      "settlement response was created before the clearing order");
  }
  return settlementOrder;
}

export function assertSettlementApproved(
  settlementOrder,
  expectedSettlementNo,
  expectedClearingNo = null,
  expectedBusinessChainId = null,
  expectedNetSettleAmount = null,
  expectedBatchNo = null,
  expectedTargetNo = null,
  expectedCreatedAt = null,
  expectedPayScene = null
) {
  assert(typeof settlementOrder?.settlementNo === "string"
    && matchesIdentifier(settlementOrder.settlementNo, expectedSettlementNo),
  `audited settlement response did not match settlement order: ${settlementOrder?.settlementNo}`);
  if (expectedClearingNo != null) {
    assert(matchesIdentifier(settlementOrder.clearingNo, expectedClearingNo),
      `audited settlement response did not match clearing order: ${settlementOrder?.clearingNo}`);
  }
  if (expectedBusinessChainId != null) {
    assert(matchesIdentifier(settlementOrder.businessChainId, expectedBusinessChainId),
      `audited settlement response did not match business chain: ${settlementOrder?.businessChainId}`);
  }
  if (expectedBatchNo != null) {
    assert(matchesIdentifier(settlementOrder.batchNo, expectedBatchNo),
      `audited settlement response did not match settlement batch: ${settlementOrder?.batchNo}`);
  }
  if (expectedTargetNo != null) {
    assert(matchesIdentifier(settlementOrder.targetNo, expectedTargetNo),
      `audited settlement response did not match settlement target: ${settlementOrder?.targetNo}`);
  }
  if (expectedPayScene != null) {
    assert(matchesIdentifier(settlementOrder.payScene, expectedPayScene),
      `audited settlement response did not preserve payment scene: ${settlementOrder?.payScene}`);
  }
  assert(settlementOrder.targetType === "WORKER",
    `audited settlement response did not preserve worker target type: ${settlementOrder?.targetType}`);
  if (expectedCreatedAt != null) {
    assert(isValidDateTime(settlementOrder.createdAt)
      && isValidDateTime(expectedCreatedAt)
      && toLocalTimestamp(settlementOrder.createdAt) === toLocalTimestamp(expectedCreatedAt),
    `audited settlement response did not preserve creation time: ${settlementOrder?.createdAt}`);
  }
  if (expectedNetSettleAmount != null) {
    assertPaymentAmount({ amount: settlementOrder.netSettleAmount }, expectedNetSettleAmount);
  }
  assert(settlementOrder.settlementStatus === "待出款"
    && settlementOrder.auditStatus === "已通过"
    && settlementOrder.payoutStatus === "待出款",
  `settlement order did not enter the payout queue: ${settlementOrder?.settlementStatus}/${settlementOrder?.auditStatus}/${settlementOrder?.payoutStatus}`);
  assert(settlementOrder.settlementStatusType === "warn"
    && settlementOrder.auditStatusType === "success"
    && settlementOrder.payoutStatusType === "warn",
  "audited settlement response did not contain the expected status styles");
  return settlementOrder;
}

export function assertSettlementFullDetail(
  detail,
  expectedSettlementNo,
  expectedBusinessChainId,
  expectedShouldSettleAmount,
  expectedDeductAmount,
  expectedNetSettleAmount,
  expectedOperatorName
) {
  const order = detail?.order;
  assert(matchesIdentifier(order?.settlementNo, expectedSettlementNo)
    && matchesIdentifier(order?.businessChainId, expectedBusinessChainId),
  `settlement detail did not preserve order identity: ${order?.settlementNo}/${order?.businessChainId}`);
  assertPaymentAmount({ amount: order?.shouldSettleAmount }, expectedShouldSettleAmount);
  assertPaymentAmount({ amount: order?.deductAmount }, expectedDeductAmount);
  assertPaymentAmount({ amount: order?.netSettleAmount }, expectedNetSettleAmount);
  const items = Array.isArray(detail?.items) ? detail.items : [];
  assert(items.length === 2
    && items.every((item) => matchesIdentifier(item?.settlementNo, expectedSettlementNo)
      && isValidDateTime(item?.createdAt)),
  "settlement detail did not contain two usable amount items");
  assertPaymentAmount(
    { amount: items.find((item) => item?.itemType === "应结金额")?.itemAmount },
    expectedShouldSettleAmount
  );
  assertPaymentAmount(
    { amount: items.find((item) => item?.itemType === "扣减金额")?.itemAmount },
    expectedDeductAmount
  );
  const approvalLogs = Array.isArray(detail?.auditLogs)
    ? detail.auditLogs.filter((log) => log?.auditAction === "审核通过" && log?.auditResult === "通过")
    : [];
  assert(approvalLogs.length === 1
    && matchesIdentifier(approvalLogs[0].settlementNo, expectedSettlementNo)
    && matchesIdentifier(approvalLogs[0].operatorName, expectedOperatorName)
    && isValidDateTime(approvalLogs[0].createdAt),
  "settlement detail did not contain exactly one usable approval audit log");
  return detail;
}

export function assertSettlementGeneratedEvent(
  event,
  expectedClearingNo,
  expectedBusinessChainId,
  expectedSourceCreatedAt,
  expectedPayScene = null
) {
  assert(matchesIdentifier(event?.eventType, "CLEARING_GENERATED")
    && matchesIdentifier(event?.bizNo, expectedClearingNo)
    && typeof event?.eventNo === "string" && event.eventNo.trim(),
  `settlement event did not preserve clearing identity: ${event?.eventType}/${event?.bizNo}`);
  assert(matchesIdentifier(event.businessChainId, expectedBusinessChainId),
    `settlement event did not match business chain: ${event?.businessChainId}`);
  assert(event.eventStatus === "已消费" && event.statusType === "success",
    `settlement event did not reach consumed state: ${event?.eventStatus}/${event?.statusType}`);
  assert(isValidDateTime(event.createdAt),
    "settlement event did not contain a usable creation time");
  if (expectedSourceCreatedAt != null) {
    assert(toLocalTimestamp(expectedSourceCreatedAt) <= toLocalTimestamp(event.createdAt),
      "settlement event was created before its clearing source");
  }
  let payload;
  try {
    payload = JSON.parse(event.payload);
  } catch (error) {
    payload = null;
  }
  assert(payload && matchesIdentifier(payload.clearingNo, expectedClearingNo),
    `settlement event payload did not preserve clearing number: ${payload?.clearingNo}`);
  if (expectedPayScene != null) {
    assert(matchesIdentifier(payload.payScene, expectedPayScene),
      `settlement event payload did not preserve payment scene: ${payload?.payScene}`);
  }
  return event;
}

export function assertClearingGeneratedEvent(
  event,
  expectedClearingNo,
  expectedPaymentOrderId,
  expectedBusinessChainId,
  expectedTargetNo,
  expectedAmount,
  expectedSourceCreatedAt,
  expectedTargetType = "WORKER",
  expectedPayScene = null
) {
  assert(matchesIdentifier(event?.eventType, "CLEARING_GENERATED")
    && matchesIdentifier(event?.bizNo, expectedClearingNo)
    && typeof event?.eventNo === "string" && event.eventNo.trim()
    && event?.publishStatus === "SUCCESS",
  `clearing event did not reach published state: ${event?.eventType}/${event?.bizNo}/${event?.publishStatus}`);
  assert(matchesIdentifier(event.businessChainId, expectedBusinessChainId),
    `clearing event did not match business chain: ${event?.businessChainId}`);
  assert(isValidDateTime(event.createdAt),
    "clearing event did not contain a usable creation time");
  if (expectedSourceCreatedAt != null) {
    assert(toLocalTimestamp(expectedSourceCreatedAt) <= toLocalTimestamp(event.createdAt),
      "clearing event was created before its payment source");
  }
  let payload;
  try {
    payload = JSON.parse(event.payload);
  } catch (error) {
    payload = null;
  }
  assert(payload
    && matchesIdentifier(payload.eventNo, event.eventNo)
    && matchesIdentifier(payload.clearingNo, expectedClearingNo)
    && matchesIdentifier(payload.paymentOrderId, expectedPaymentOrderId)
    && matchesIdentifier(payload.businessChainId, expectedBusinessChainId)
    && (expectedPayScene == null || matchesIdentifier(payload.payScene, expectedPayScene))
    && matchesIdentifier(payload.targetType, expectedTargetType)
    && matchesIdentifier(payload.targetNo, expectedTargetNo),
  `clearing event payload did not preserve identity: ${event?.payload}`);
  assertPaymentAmount({ amount: payload?.amount }, expectedAmount);
  assertPaymentAmount({ amount: payload?.shouldSettleAmount }, expectedAmount);
  assertPaymentAmount({ amount: payload?.netSettleAmount }, expectedAmount);
  assertPaymentAmount({ amount: payload?.deductAmount }, "0.00");
  return event;
}

export function assertClearingPaymentSuccessEvent(
  event,
  expectedPaymentOrderId,
  expectedOrderNo,
  expectedBusinessChainId,
  expectedPayScene,
  expectedSourceCreatedAt = null
) {
  assert(matchesIdentifier(event?.eventType, "PAYMENT_SUCCESS")
    && matchesIdentifier(event?.bizNo, expectedPaymentOrderId)
    && typeof event?.eventNo === "string" && event.eventNo.trim()
    && event?.eventStatus === "已消费",
  `clearing payment success event was not consumed: ${event?.eventType}/${event?.bizNo}/${event?.eventStatus}`);
  assert(matchesIdentifier(event.businessChainId, expectedBusinessChainId),
    `clearing payment success event did not match business chain: ${event?.businessChainId}`);
  assert(isValidDateTime(event.createdAt),
    "clearing payment success event did not contain a usable creation time");
  if (expectedSourceCreatedAt != null) {
    assert(toLocalTimestamp(expectedSourceCreatedAt) <= toLocalTimestamp(event.createdAt),
      "clearing payment success event was created before its payment source");
  }
  let payload;
  try {
    payload = JSON.parse(event.payload);
  } catch (error) {
    payload = null;
  }
  assert(payload
    && matchesIdentifier(payload.paymentOrderId, expectedPaymentOrderId)
    && matchesIdentifier(payload.orderNo, expectedOrderNo)
    && matchesIdentifier(payload.businessChainId, expectedBusinessChainId)
    && matchesIdentifier(payload.payScene, expectedPayScene),
  `clearing payment success event payload did not preserve identity or payment scene: ${event?.payload}`);
  return event;
}

export function assertSingleClearingPaymentSuccessEvent(page, paymentOrderId, orderNo, businessChainId, payScene, sourceCreatedAt) {
  assert(page?.total === 1 && Array.isArray(page.items) && page.items.length === 1,
    `clearing payment ${paymentOrderId} did not produce exactly one PAYMENT_SUCCESS event`);
  return assertClearingPaymentSuccessEvent(
    page.items[0], paymentOrderId, orderNo, businessChainId, payScene, sourceCreatedAt
  );
}

export function assertPayoutRecord(
  record,
  expectedPayoutBatchNo,
  expectedSettlementNo,
  expectedAmount,
  expectedStatus = "待出款",
  expectedSettlementCreatedAt = null,
  expectedTargetNo = null
) {
  assert(typeof record?.payoutNo === "string" && record.payoutNo.trim(),
    "payout response did not contain a usable payout number");
  assert(matchesIdentifier(record.payoutBatchNo, expectedPayoutBatchNo)
    && matchesIdentifier(record.settlementNo, expectedSettlementNo),
  `payout record did not preserve batch and settlement identity: ${record?.payoutBatchNo}/${record?.settlementNo}`);
  assert(typeof record?.targetNo === "string" && record.targetNo.trim(),
    "payout record did not contain a usable payout target");
  if (expectedTargetNo != null) {
    assert(matchesIdentifier(record.targetNo, expectedTargetNo),
      `payout record did not match settlement target: ${record?.targetNo}`);
  }
  assertPaymentAmount({ amount: record.payoutAmount }, expectedAmount);
  assert(record.payoutStatus === expectedStatus,
    `payout record did not reach ${expectedStatus}: ${record?.payoutStatus}`);
  const expectedStatusType = expectedStatus === "已发放" ? "success" : "warn";
  assert(record.payoutStatusType === expectedStatusType,
    `payout record status style was ${record?.payoutStatusType}, expected ${expectedStatusType}`);
  assert(isValidDateTime(record.createdAt),
    "payout record did not contain a usable creation time");
  if (expectedSettlementCreatedAt != null) {
    assert(toLocalTimestamp(expectedSettlementCreatedAt) <= toLocalTimestamp(record.createdAt),
      "payout record was created before the settlement order");
  }
  return record;
}

export function findPayoutRecord(records, expectedSettlementNo) {
  const normalizedSettlementNo = typeof expectedSettlementNo === "string"
    ? expectedSettlementNo.trim()
    : expectedSettlementNo;
  return findUniqueRecord(
    records,
    (record) => matchesIdentifier(record?.settlementNo, normalizedSettlementNo),
    "payout record"
  );
}

export function assertPayoutBatchPendingRecords(
  records,
  expectedPayoutBatchNo,
  expectedPayoutCount,
  expectedTotalAmount = null,
  expectedTargetNo = null
) {
  assert(Array.isArray(records) && records.length === expectedPayoutCount,
    `pending payout records count was ${records?.length}, expected ${expectedPayoutCount}`);
  const payoutNumbers = new Set();
  const settlementNumbers = new Set();
  let totalAmount = 0n;
  for (const record of records) {
    assert(typeof record?.payoutNo === "string" && record.payoutNo.trim()
      && matchesIdentifier(record.payoutBatchNo, expectedPayoutBatchNo)
      && typeof record?.settlementNo === "string" && record.settlementNo.trim()
      && typeof record?.targetNo === "string" && record.targetNo.trim()
      && record.payoutStatus === "待出款"
      && record.payoutStatusType === "warn"
      && isValidDateTime(record.createdAt)
      && parseMoneyAmount(record.payoutAmount) > 0n,
    "pending payout batch contained an incomplete or non-pending record");
    if (expectedTargetNo != null) {
      assert(matchesIdentifier(record.targetNo, expectedTargetNo),
        `pending payout record did not match settlement target: ${record.targetNo}`);
    }
    assert(!payoutNumbers.has(record.payoutNo.trim())
      && !settlementNumbers.has(record.settlementNo.trim()),
    "pending payout batch contained duplicate payout or settlement number");
    payoutNumbers.add(record.payoutNo.trim());
    settlementNumbers.add(record.settlementNo.trim());
    totalAmount += parseMoneyAmount(record.payoutAmount);
  }
  if (expectedTotalAmount != null) {
    const normalizedExpectedTotal = parseMoneyAmount(expectedTotalAmount);
    assert(normalizedExpectedTotal != null && totalAmount === normalizedExpectedTotal,
      `pending payout batch records total did not match batch total: ${totalAmount}/${expectedTotalAmount}`);
  }
  return records;
}

export function assertPayoutBatchRecords(
  records,
  expectedPayoutBatchNo,
  expectedPayoutCount,
  expectedTotalAmount = null,
  expectedFinishedAt = null,
  expectedTargetNo = null
) {
  assert(Array.isArray(records) && records.length === expectedPayoutCount,
    `payout batch records count was ${records?.length}, expected ${expectedPayoutCount}`);
  let totalAmount = 0n;
  const payoutNumbers = new Set();
  const settlementNumbers = new Set();
  for (const record of records) {
    assert(typeof record?.payoutNo === "string" && record.payoutNo.trim()
      && matchesIdentifier(record.payoutBatchNo, expectedPayoutBatchNo)
      && typeof record?.settlementNo === "string" && record.settlementNo.trim()
      && typeof record?.targetNo === "string" && record.targetNo.trim()
      && record.payoutStatus === "已发放"
      && record.payoutStatusType === "success"
      && isValidDateTime(record.createdAt)
      && (expectedFinishedAt == null
        || (isValidDateTime(expectedFinishedAt)
          && toLocalTimestamp(record.createdAt) <= toLocalTimestamp(expectedFinishedAt)))
      && parseMoneyAmount(record.payoutAmount) > 0n,
    "payout batch contained an incomplete or non-success record");
    if (expectedTargetNo != null) {
      assert(matchesIdentifier(record.targetNo, expectedTargetNo),
        `payout record did not match settlement target: ${record.targetNo}`);
    }
    assert(!payoutNumbers.has(record.payoutNo.trim()),
      `payout batch contained duplicate payout number: ${record.payoutNo}`);
    assert(!settlementNumbers.has(record.settlementNo.trim()),
      `payout batch contained duplicate settlement number: ${record.settlementNo}`);
    payoutNumbers.add(record.payoutNo.trim());
    settlementNumbers.add(record.settlementNo.trim());
    totalAmount += parseMoneyAmount(record.payoutAmount);
  }
  if (expectedTotalAmount != null) {
    const normalizedExpectedTotal = parseMoneyAmount(expectedTotalAmount);
    assert(normalizedExpectedTotal != null && totalAmount === normalizedExpectedTotal,
      `payout batch records total did not match batch total: ${totalAmount}/${expectedTotalAmount}`);
  }
  return records;
}

export function assertSettlementClosed(
  settlementOrder,
  expectedSettlementNo,
  expectedClearingNo,
  expectedBusinessChainId,
  expectedNetSettleAmount,
  expectedBatchNo = null,
  expectedTargetNo = null,
  expectedCreatedAt = null,
  expectedPayScene = null
) {
  assert(matchesIdentifier(settlementOrder?.settlementNo, expectedSettlementNo),
    `closed settlement response did not match settlement order: ${settlementOrder?.settlementNo}`);
  assert(matchesIdentifier(settlementOrder?.clearingNo, expectedClearingNo),
    `closed settlement response did not match clearing order: ${settlementOrder?.clearingNo}`);
  if (expectedBatchNo != null) {
    assert(matchesIdentifier(settlementOrder?.batchNo, expectedBatchNo),
      `closed settlement response did not match settlement batch: ${settlementOrder?.batchNo}`);
  }
  if (expectedTargetNo != null) {
    assert(matchesIdentifier(settlementOrder?.targetNo, expectedTargetNo),
      `closed settlement response did not match settlement target: ${settlementOrder?.targetNo}`);
  }
  if (expectedPayScene != null) {
    assert(matchesIdentifier(settlementOrder?.payScene, expectedPayScene),
      `closed settlement response did not preserve payment scene: ${settlementOrder?.payScene}`);
  }
  if (expectedCreatedAt != null) {
    assert(isValidDateTime(settlementOrder?.createdAt)
      && isValidDateTime(expectedCreatedAt)
      && toLocalTimestamp(settlementOrder.createdAt) === toLocalTimestamp(expectedCreatedAt),
    `closed settlement response did not preserve creation time: ${settlementOrder?.createdAt}`);
  }
  assert(matchesIdentifier(settlementOrder?.businessChainId, expectedBusinessChainId),
    `closed settlement response did not match business chain: ${settlementOrder?.businessChainId}`);
  assert(settlementOrder?.targetType === "WORKER",
    `closed settlement response did not preserve worker target type: ${settlementOrder?.targetType}`);
  assertPaymentAmount({ amount: settlementOrder?.netSettleAmount }, expectedNetSettleAmount);
  assert(settlementOrder?.settlementStatus === "已出款"
    && settlementOrder?.auditStatus === "已通过"
    && settlementOrder?.payoutStatus === "已发放",
  `settlement order did not close after payout: ${settlementOrder?.settlementStatus}/${settlementOrder?.payoutStatus}/${settlementOrder?.auditStatus}`);
  assert(settlementOrder?.settlementStatusType === "success"
    && settlementOrder?.auditStatusType === "success"
    && settlementOrder?.payoutStatusType === "success",
  "closed settlement response did not contain the expected status styles");
  return settlementOrder;
}

export function assertPayoutBatchExecuted(
  batch,
  expectedPayoutBatchNo,
  expectedPayoutRecordCreatedAt = null,
  expectedBatchNo = null
) {
  assert(matchesIdentifier(batch?.payoutBatchNo, expectedPayoutBatchNo),
    `executed payout batch did not match requested batch: ${batch?.payoutBatchNo}`);
  if (expectedBatchNo != null) {
    assert(matchesIdentifier(batch?.batchNo, expectedBatchNo),
      `executed payout batch did not match settlement batch: ${batch?.batchNo}`);
  }
  assert(typeof batch.payoutChannel === "string" && batch.payoutChannel.trim(),
    "completed payout batch did not contain a usable payout channel");
  assert(batch.payoutStatus === "已完成"
    && batch.payoutStatusType === "success",
  `payout batch did not reach completed state: ${batch?.payoutStatus}/${batch?.payoutStatusType}`);
  assert(Number.isInteger(batch.payoutCount) && batch.payoutCount > 0
    && batch.successCount === batch.payoutCount
    && batch.failedCount === 0,
  `payout batch counts were inconsistent: ${batch?.payoutCount}/${batch?.successCount}/${batch?.failedCount}`);
  assert(isValidDateTime(batch.createdAt),
    "completed payout batch did not contain a usable creation time");
  assert(isValidDateTime(batch.finishedAt),
    "completed payout batch did not contain a usable finish time");
  assert(toLocalTimestamp(batch.createdAt) <= toLocalTimestamp(batch.finishedAt),
    "completed payout batch finished before it was created");
  if (expectedPayoutRecordCreatedAt != null) {
    assert(isValidDateTime(expectedPayoutRecordCreatedAt)
      && toLocalTimestamp(expectedPayoutRecordCreatedAt) <= toLocalTimestamp(batch.finishedAt),
    "completed payout batch finished before its payout record was created");
  }
  return batch;
}

export function findPayoutBatch(
  batches,
  expectedPayoutBatchNo,
  expectedStatus = "待出款",
  expectedBatchNo = null
) {
  return findUniqueRecord(
    batches,
    (batch) => (expectedPayoutBatchNo == null || matchesIdentifier(batch?.payoutBatchNo, expectedPayoutBatchNo))
      && batch?.payoutStatus === expectedStatus
      && (expectedBatchNo == null || matchesIdentifier(batch?.batchNo, expectedBatchNo)),
    "payout batch"
  );
}

export function findClearingOrder(
  orders,
  expectedPaymentOrderId,
  expectedStatus = "清分成功",
  expectedOrderNo = null
) {
  return findUniqueRecord(
    orders,
    (order) => matchesIdentifier(order?.paymentOrderId, expectedPaymentOrderId)
      && order?.clearingStatus === expectedStatus
      && (expectedOrderNo == null || matchesIdentifier(order?.orderNo, expectedOrderNo)),
    "clearing order"
  );
}

function findUniqueRecord(records, predicate, recordType) {
  if (!Array.isArray(records)) {
    return undefined;
  }
  const matches = records.filter(predicate);
  if (matches.length > 1) {
    throw new Error(`cross-system happy-path found duplicate ${recordType} facts`);
  }
  return matches[0];
}

export function assertClearingIdentity(
  clearingOrder,
  expectedPaymentOrderId,
  expectedOrderNo,
  expectedPaymentEventCreatedAt,
  expectedPayScene = null
) {
  assert(typeof clearingOrder?.clearingNo === "string" && clearingOrder.clearingNo.trim(),
    "clearing response did not contain a usable clearing number");
  assert(matchesIdentifier(clearingOrder?.paymentOrderId, expectedPaymentOrderId),
  `clearing response did not match payment order: ${clearingOrder?.paymentOrderId}`);
  assert(matchesIdentifier(clearingOrder.businessChainId, paymentBusinessChainId(expectedPaymentOrderId)),
    `clearing response did not match business chain: ${clearingOrder?.businessChainId}`);
  if (expectedOrderNo != null) {
    assert(matchesIdentifier(clearingOrder.orderNo, expectedOrderNo),
      `clearing response did not match business order: ${clearingOrder?.orderNo}`);
  }
  if (expectedPayScene != null) {
    assert(matchesIdentifier(clearingOrder.payScene, expectedPayScene),
      `clearing response did not preserve payment scene: ${clearingOrder?.payScene}`);
  }
  assert(clearingOrder.clearingStatus === "清分成功",
    `clearing response did not reach success: ${clearingOrder?.clearingStatus}`);
  assert(clearingOrder.clearingStatusType === "success",
    `clearing response did not contain the success status style: ${clearingOrder?.clearingStatusType}`);
  assert(isValidDateTime(clearingOrder.createdAt),
    "clearing response did not contain a usable creation time");
  if (expectedPaymentEventCreatedAt != null) {
    assert(toLocalTimestamp(expectedPaymentEventCreatedAt) <= toLocalTimestamp(clearingOrder.createdAt),
      "clearing response was created before the payment success event");
  }
  return clearingOrder;
}

export function assertClearingTarget(clearingDetail, clearingOrder) {
  assert(matchesIdentifier(clearingDetail?.order?.clearingNo, clearingOrder?.clearingNo)
    && matchesIdentifier(clearingDetail?.order?.businessChainId, clearingOrder?.businessChainId),
  "clearing detail did not match the selected clearing order");
  const workerShares = Array.isArray(clearingDetail.shareItems)
    ? clearingDetail.shareItems.filter((item) => matchesIdentifier(item?.shareType, "WORKER")
      && matchesIdentifier(item?.clearingNo, clearingOrder.clearingNo))
    : [];
  assert(workerShares.length === 1
    && typeof workerShares[0].shareTargetNo === "string"
    && workerShares[0].shareTargetNo.trim(),
  "clearing detail did not contain exactly one usable worker payout target");
  const workerAmount = parseMoneyAmount(workerShares[0].shareAmount);
  assert(workerAmount != null && workerAmount === parseMoneyAmount(clearingOrder.workerAmount),
    "clearing worker payout target amount did not match the clearing order");
  return workerShares[0].shareTargetNo.trim();
}

export function assertRefundStatus(refundOrder, expectedStatus, stage) {
  assert(refundOrder?.status === expectedStatus,
    `refund ${stage} did not reach ${expectedStatus}: ${refundOrder?.status}`);
  const expectedType = {
    REVIEWING: "warn",
    APPROVED: "success",
    PROCESSING: "warn",
    SUCCESS: "success",
    FAIL: "danger"
  }[expectedStatus];
  assert(refundOrder?.statusType === expectedType,
    `refund ${stage} did not contain the ${expectedType} status style: ${refundOrder?.statusType}`);
  if (expectedStatus === "SUCCESS" || expectedStatus === "FAIL") {
    assert(typeof refundOrder?.channelRefundId === "string" && refundOrder.channelRefundId.trim(),
      `refund ${stage} did not contain a usable channel refund identifier`);
    assert(typeof refundOrder?.channelCode === "string" && refundOrder.channelCode.trim(),
      `refund ${stage} did not contain a usable channel code`);
  }
  return refundOrder;
}

export function assertRefundIdentity(
  refundOrder,
  expectedRefundOrderId,
  expectedPaymentOrderId,
  expectedOrderNo,
  expectedBusinessChainId = null
) {
  assert(typeof refundOrder?.refundOrderId === "string"
    && refundOrder.refundOrderId.trim()
    && (!expectedRefundOrderId || matchesIdentifier(refundOrder.refundOrderId, expectedRefundOrderId)),
  `refund response did not match refund order: ${refundOrder?.refundOrderId}`);
  assert(matchesIdentifier(refundOrder?.paymentOrderId, expectedPaymentOrderId),
  `refund response did not match payment order: ${refundOrder?.paymentOrderId}`);
  assert(typeof refundOrder?.orderNo === "string"
    && refundOrder.orderNo.trim()
    && (!expectedOrderNo || matchesIdentifier(refundOrder.orderNo, expectedOrderNo)),
  `refund response did not match business order: ${refundOrder?.orderNo}`);
  if (expectedBusinessChainId != null) {
    assert(matchesIdentifier(refundOrder?.businessChainId, expectedBusinessChainId),
      `refund response did not match business chain: ${refundOrder?.businessChainId}`);
  }
  return refundOrder;
}

export function assertAccountingLedger(page, bizNo, bizType, direction, expectedSourceCreatedAt, expectedPayScene = null) {
  assert(page?.items?.length === 1,
    `accounting ${bizType}/${bizNo} did not create exactly one ledger`);
  const ledger = page.items[0];
  assert(typeof ledger.ledgerNo === "string" && ledger.ledgerNo.trim(),
    `accounting ${bizType}/${bizNo} did not contain a usable ledger number`);
  assert(matchesIdentifier(ledger.bizNo, bizNo) && matchesIdentifier(ledger.bizType, bizType),
    `accounting ledger identity mismatch: ${ledger.bizType}/${ledger.bizNo}`);
  assert(typeof ledger.accountNo === "string" && ledger.accountNo.trim(),
    `accounting ${bizType}/${bizNo} did not contain a usable account number`);
  assert(ledger.direction === direction,
    `accounting ${bizType}/${bizNo} direction was ${ledger.direction}, expected ${direction}`);
  assert(ledger.ledgerStatus === "已记账" && ledger.statusType === "success",
    `accounting ${bizType}/${bizNo} did not reach the posted ledger state: ${ledger.ledgerStatus}/${ledger.statusType}`);
  if (expectedPayScene != null) {
    assert(ledger.payScene === expectedPayScene,
      `accounting ${bizType}/${bizNo} ledger pay scene was ${ledger.payScene}, expected ${expectedPayScene}`);
  }
  assert(isValidDateTime(ledger.createdAt),
    `accounting ${bizType}/${bizNo} did not contain a usable creation time`);
  if (expectedSourceCreatedAt != null) {
    assert(toLocalTimestamp(expectedSourceCreatedAt) <= toLocalTimestamp(ledger.createdAt),
      `accounting ${bizType}/${bizNo} ledger was created before its source event`);
  }
  const ledgerAmount = parseMoneyAmount(ledger.amount);
  const beforeBalance = parseMoneyAmount(ledger.beforeBalance);
  const afterBalance = parseMoneyAmount(ledger.afterBalance);
  const expectedAfterBalance = ledger.direction === "贷方"
    ? beforeBalance == null || ledgerAmount == null ? null : beforeBalance + ledgerAmount
    : beforeBalance == null || ledgerAmount == null ? null : beforeBalance - ledgerAmount;
  assert(ledgerAmount != null && ledgerAmount > 0n,
    `accounting ${bizType}/${bizNo} ledger amount was not positive: ${ledger.amount}`);
  assert(beforeBalance != null && beforeBalance >= 0n
    && afterBalance != null && afterBalance >= 0n
    && expectedAfterBalance === afterBalance,
  `accounting ${bizType}/${bizNo} balance movement did not match its direction and amount`);
  return ledger;
}

export function assertAccountingLedgerAmount(page, bizNo, bizType, direction, expectedAmount, expectedSourceCreatedAt, expectedPayScene = null) {
  const ledger = assertAccountingLedger(page, bizNo, bizType, direction, expectedSourceCreatedAt, expectedPayScene);
  const normalizedAmount = parseMoneyAmount(ledger.amount);
  const normalizedExpectedAmount = parseMoneyAmount(expectedAmount);
  assert(normalizedAmount != null
    && normalizedExpectedAmount != null
    && normalizedAmount === normalizedExpectedAmount,
  `accounting ${bizType}/${bizNo} amount was ${ledger.amount}, expected ${expectedAmount}`);
  return ledger;
}

export function assertClearingAccountingAmount(page, clearingOrder, expectedSourceCreatedAt, expectedPayScene = null) {
  return assertAccountingLedgerAmount(
    page,
    clearingOrder?.clearingNo,
    "CLEARING_GENERATED_EVENT",
    "贷方",
    clearingOrder?.workerAmount,
    expectedSourceCreatedAt,
    expectedPayScene
  );
}

export function assertAccountingEvent(
  page,
  bizNo,
  eventType,
  expectedBusinessChainId,
  expectedSourceCreatedAt,
  expectedAmount = null,
  expectedPaymentOrderId = null,
  expectedOrderNo = null,
  expectedBusinessNo = null,
  expectedPayScene = null
) {
  const events = Array.isArray(page?.items)
    ? page.items.filter((item) => matchesIdentifier(item?.eventType, eventType)
      && matchesIdentifier(item?.bizNo, bizNo))
    : [];
  assert(events.length === 1 && events[0]?.eventStatus === "已消费",
    `accounting ${eventType}/${bizNo} did not produce exactly one consumed matching event`);
  assert(events[0].statusType === "success",
    `accounting ${eventType}/${bizNo} did not contain the consumed event success style: ${events[0]?.statusType}`);
  assert(typeof events[0].eventNo === "string" && events[0].eventNo.trim(),
    `accounting ${eventType}/${bizNo} did not contain a usable event number`);
  assert(isValidDateTime(events[0].createdAt),
    `accounting ${eventType}/${bizNo} did not contain a usable creation time`);
  if (expectedBusinessChainId != null) {
    assert(matchesIdentifier(events[0].businessChainId, expectedBusinessChainId),
      `accounting ${eventType}/${bizNo} did not match business chain: ${events[0].businessChainId}`);
  }
  if (expectedSourceCreatedAt != null) {
    assert(toLocalTimestamp(expectedSourceCreatedAt) <= toLocalTimestamp(events[0].createdAt),
      `accounting ${eventType}/${bizNo} was created before its source fact`);
  }
  if (expectedAmount != null || expectedPaymentOrderId != null || expectedOrderNo != null
      || expectedBusinessNo != null || expectedPayScene != null) {
    let payload;
    try {
      payload = JSON.parse(events[0].payload);
    } catch (error) {
      payload = null;
    }
    if (expectedAmount != null) {
      assert(payload && parseMoneyAmount(payload.amount) === parseMoneyAmount(expectedAmount),
        `accounting ${eventType}/${bizNo} payload amount did not match expected amount`);
    }
    if (expectedPaymentOrderId != null) {
      assert(payload && matchesIdentifier(payload.paymentOrderId, expectedPaymentOrderId),
        `accounting ${eventType}/${bizNo} payload did not preserve payment order: ${payload?.paymentOrderId}`);
    }
    if (expectedOrderNo != null) {
      assert(payload && matchesIdentifier(payload.orderNo, expectedOrderNo),
        `accounting ${eventType}/${bizNo} payload did not preserve business order: ${payload?.orderNo}`);
    }
    if (expectedBusinessNo != null) {
      assert(payload && matchesIdentifier(payload.bizNo, expectedBusinessNo),
        `accounting ${eventType}/${bizNo} payload did not preserve business order: ${payload?.bizNo}`);
    }
    if (expectedPayScene != null) {
      assert(payload && matchesIdentifier(payload.payScene, expectedPayScene),
        `accounting ${eventType}/${bizNo} payload did not preserve payment scene: ${payload?.payScene}`);
    }
  }
  return events[0];
}

export function assertSinglePaymentSuccessEvent(
  page,
  paymentOrderId,
  expectedOrderNo,
  expectedBusinessChainId = null,
  expectedPaymentSuccessAt = null,
  expectedAmount = null,
  expectedChannel = null,
  expectedPayScene = null
) {
  const events = Array.isArray(page?.items)
    ? page.items.filter((item) => matchesIdentifier(item?.eventType, "PAYMENT_SUCCESS")
      && matchesIdentifier(item?.paymentOrderId, paymentOrderId)
      && (!expectedOrderNo || matchesIdentifier(item?.bizNo, expectedOrderNo)))
    : [];
  assert(events.length === 1
    && typeof events[0]?.eventNo === "string"
    && events[0].eventNo.trim()
    && events[0]?.publishStatus === "SUCCESS"
    && events[0]?.publishStatusType === "success"
    && events[0]?.paymentSuccessHttpStage === "COMPLETED"
    && isValidDateTime(events[0]?.createdAt)
    && isValidDateTime(events[0]?.lastPublishedAt)
    && toLocalTimestamp(events[0].createdAt) <= toLocalTimestamp(events[0].lastPublishedAt),
    `payment ${paymentOrderId} did not produce exactly one completed PAYMENT_SUCCESS event with an event number`);
  if (expectedBusinessChainId != null) {
    assert(matchesIdentifier(events[0].businessChainId, expectedBusinessChainId),
      `payment ${paymentOrderId} event did not match business chain: ${events[0].businessChainId}`);
  }
  if (expectedPaymentSuccessAt != null) {
    assert(isValidDateTime(expectedPaymentSuccessAt)
      && toLocalTimestamp(events[0].createdAt) >= toLocalTimestamp(expectedPaymentSuccessAt),
    `payment ${paymentOrderId} success event was created before the payment success fact`);
  }
  if (expectedAmount != null && events[0].amount != null) {
    assertPaymentAmount({ amount: events[0].amount }, expectedAmount);
  }
  if (expectedChannel != null || expectedPayScene != null) {
    let payload = null;
    try {
      payload = JSON.parse(events[0].eventPayload);
    } catch (error) {
      payload = null;
    }
    if (expectedChannel != null) {
      assert(payload && typeof payload.channel === "string" && payload.channel.trim()
        && matchesIdentifier(payload.channel, expectedChannel),
      `payment ${paymentOrderId} success event payload did not preserve the callback channel: ${events[0].eventPayload}`);
    }
    if (expectedPayScene != null) {
      assert(payload && typeof payload.payScene === "string" && payload.payScene.trim()
        && matchesIdentifier(payload.payScene, expectedPayScene),
      `payment ${paymentOrderId} success event payload did not preserve the payment scene: ${events[0].eventPayload}`);
    }
  }
  return events[0];
}

export function isPaymentSuccessEventDispatched(item, expectedPaymentOrderId = null, expectedOrderNo = null) {
  return matchesIdentifier(item?.eventType, "PAYMENT_SUCCESS")
    && (expectedPaymentOrderId == null || matchesIdentifier(item?.paymentOrderId, expectedPaymentOrderId))
    && (expectedOrderNo == null || matchesIdentifier(item?.bizNo, expectedOrderNo))
    && item?.publishStatus === "SUCCESS"
    && item?.paymentSuccessHttpStage === "COMPLETED";
}

function assertRefundOutboxPayload(
  event, expectedRefundOrderId, expectedPaymentOrderId, expectedAmount, expectedPayScene = null
) {
  let payload = null;
  try {
    payload = JSON.parse(event?.payloadJson);
  } catch (error) {
    payload = null;
  }
  assert(payload && matchesIdentifier(payload.refundOrderId, expectedRefundOrderId)
    && matchesIdentifier(payload.paymentOrderId, expectedPaymentOrderId)
    && parseMoneyAmount(payload.refundAmount) === parseMoneyAmount(expectedAmount)
    && (expectedPayScene == null || matchesIdentifier(payload.payScene, expectedPayScene)),
  `refund outbox payload did not preserve refund identity and amount or payment scene: ${event?.payloadJson}`);
}

export function assertSingleRefundSuccessOutbox(
  page,
  refundOrderId,
  expectedRefundTerminalAt = null,
  expectedPaymentOrderId = null,
  expectedAmount = null,
  expectedPayScene = null
) {
  const events = Array.isArray(page?.items)
    ? page.items.filter((item) => matchesIdentifier(item?.eventType, "REFUND_SUCCESS")
      && matchesIdentifier(item?.aggregateId, refundOrderId)
      && typeof item?.eventId === "string"
      && item.eventId.trim())
    : [];
  assert(events.length === 1 && events[0]?.status === "PENDING",
    `refund ${refundOrderId} did not produce exactly one pending REFUND_SUCCESS outbox event`);
  assert(isValidDateTime(events[0].createdAt) && isValidDateTime(events[0].updatedAt),
    `refund ${refundOrderId} pending outbox event did not contain usable lifecycle timestamps`);
  assert(toLocalTimestamp(events[0].createdAt) <= toLocalTimestamp(events[0].updatedAt),
    `refund ${refundOrderId} pending outbox event updated time preceded its creation time`);
  assert(events[0].lastRelayAt == null,
    `refund ${refundOrderId} pending outbox event unexpectedly contained a relay timestamp`);
  if (expectedRefundTerminalAt != null) {
    assert(isValidDateTime(expectedRefundTerminalAt)
      && toLocalTimestamp(events[0].createdAt) >= toLocalTimestamp(expectedRefundTerminalAt),
    `refund ${refundOrderId} success outbox event was created before the refund terminal fact`);
  }
  if (expectedPaymentOrderId != null || expectedAmount != null || expectedPayScene != null) {
    assertRefundOutboxPayload(
      events[0], refundOrderId, expectedPaymentOrderId, expectedAmount, expectedPayScene
    );
  }
  return events[0];
}

export function assertRefundOutboxDispatch(
  outbox,
  source,
  refundOrderId,
  expectedPaymentOrderId = null,
  expectedAmount = null,
  expectedPayScene = null
) {
  assert(matchesIdentifier(outbox?.eventId, source?.eventId),
    `refund outbox dispatch returned a different event: ${outbox?.eventId}`);
  assert(matchesIdentifier(outbox?.eventType, "REFUND_SUCCESS"),
    `refund outbox dispatch returned an unexpected event type: ${outbox?.eventType}`);
  assert(matchesIdentifier(outbox?.aggregateId, refundOrderId),
    `refund outbox dispatch returned a different refund aggregate: ${outbox?.aggregateId}`);
  assert(outbox?.status === "SENT",
    `refund outbox dispatch did not reach SENT: ${outbox?.status}`);
  assert(isValidDateTime(outbox.createdAt)
    && isValidDateTime(outbox.updatedAt)
    && isValidDateTime(outbox.lastRelayAt),
  "refund outbox dispatch did not contain usable lifecycle timestamps");
  assert(toLocalTimestamp(outbox.createdAt) <= toLocalTimestamp(outbox.updatedAt)
    && toLocalTimestamp(outbox.updatedAt) <= toLocalTimestamp(outbox.lastRelayAt),
  "refund outbox dispatch lifecycle timestamps were out of order");
  assert(isValidDateTime(source?.createdAt)
    && toLocalTimestamp(outbox.createdAt) === toLocalTimestamp(source.createdAt),
  "refund outbox dispatch changed the original creation time");
  if (expectedPaymentOrderId != null || expectedAmount != null || expectedPayScene != null) {
    assertRefundOutboxPayload(
      source, refundOrderId, expectedPaymentOrderId, expectedAmount, expectedPayScene
    );
    assertRefundOutboxPayload(
      outbox, refundOrderId, expectedPaymentOrderId, expectedAmount, expectedPayScene
    );
  }
  return outbox;
}

export function assertReconciliationChainIdentity(snapshot, expectedBusinessChainId) {
  assert(matchesIdentifier(snapshot?.businessChainId, expectedBusinessChainId),
    `reconciliation chain did not match requested business chain: ${snapshot?.businessChainId}`);
  assert(Array.isArray(snapshot.facts) && snapshot.factCount === snapshot.facts.length,
    "reconciliation chain fact count did not match its facts");
  assert(snapshot.facts.every((fact) => matchesIdentifier(fact?.businessChainId, expectedBusinessChainId)),
    "reconciliation chain contained a fact from another business chain");
  return snapshot;
}

export function assertReconciliationCompletion(snapshot) {
  assert(snapshot?.hasAbnormalFact === false && snapshot.abnormalFactCount === 0,
    "reconciliation chain contained abnormal facts despite completion checks");
  return snapshot;
}

export function assertReconciliationLastFactTime(snapshot) {
  assert(isValidLocalDateTime(snapshot?.lastFactAt),
    "reconciliation chain did not contain a usable last fact time");
  if (Array.isArray(snapshot?.facts)) {
    const lastFactTimestamp = toLocalTimestamp(snapshot.lastFactAt);
    assert(snapshot.facts.every((fact) => isValidLocalDateTime(fact?.occurredAt)
      && toLocalTimestamp(fact.occurredAt) <= lastFactTimestamp),
    "reconciliation chain last fact time did not cover every fact occurrence");
    if (snapshot.facts.length > 0) {
      const latestFactTimestamp = Math.max(...snapshot.facts.map((fact) => toLocalTimestamp(fact.occurredAt)));
      assert(latestFactTimestamp === lastFactTimestamp,
        "reconciliation chain last fact time did not equal the latest fact occurrence");
    }
  }
  return snapshot;
}

export function findReconciliationFacts(facts, systemCode, factType, factNo) {
  return Array.isArray(facts)
    ? facts.filter((item) => matchesIdentifier(item?.systemCode, systemCode)
      && matchesIdentifier(item?.factType, factType)
      && matchesIdentifier(item?.factNo, factNo))
    : [];
}

export function assertReconciliationFactAmounts(snapshot, expectedFacts) {
  assert(Array.isArray(snapshot?.facts) && snapshot.facts.length === expectedFacts.length,
    `reconciliation fact count was ${snapshot?.facts?.length}, expected ${expectedFacts.length}`);
  for (const [systemCode, factType, factNo, expectedAmount, expectedBusinessNo, expectedSourceCreatedAt] of expectedFacts) {
    const matches = findReconciliationFacts(snapshot?.facts, systemCode, factType, factNo);
    assert(matches.length === 1 && matches[0]?.factStatus === "SUCCESS",
    `reconciliation ${systemCode}/${factType}/${factNo} did not contain exactly one SUCCESS fact`);
    const fact = matches[0];
    if (expectedBusinessNo != null) {
      assert(matchesIdentifier(fact.businessNo, expectedBusinessNo),
        `reconciliation ${systemCode}/${factType}/${factNo} business number was ${fact.businessNo}, expected ${expectedBusinessNo}`);
    }
    assert(isValidLocalDateTime(fact.occurredAt) && isValidLocalDateTime(fact.createdAt),
      `reconciliation ${systemCode}/${factType}/${factNo} did not contain usable lifecycle timestamps`);
    assert(toLocalTimestamp(fact.occurredAt) <= toLocalTimestamp(fact.createdAt),
      `reconciliation ${systemCode}/${factType}/${factNo} occurred before its record creation time`);
    if (expectedSourceCreatedAt != null) {
      assert(isValidLocalDateTime(expectedSourceCreatedAt)
        && toLocalTimestamp(expectedSourceCreatedAt) <= toLocalTimestamp(fact.occurredAt),
      `reconciliation ${systemCode}/${factType}/${factNo} occurred before its source fact`);
    }
    const normalizedAmount = parseMoneyAmount(fact?.amount);
    const normalizedExpectedAmount = parseMoneyAmount(expectedAmount);
    assert(normalizedAmount != null
      && normalizedExpectedAmount != null
      && normalizedAmount === normalizedExpectedAmount,
    `reconciliation ${systemCode}/${factType}/${factNo} amount was ${fact?.amount}, expected ${expectedAmount}`);
  }
  return snapshot;
}

export function assertReconciliationFactPayload(snapshot, systemCode, factType, factNo, field, expectedValue) {
  const matches = findReconciliationFacts(snapshot?.facts, systemCode, factType, factNo);
  assert(matches.length === 1, `reconciliation ${systemCode}/${factType}/${factNo} did not contain one fact for payload validation`);
  let payload = null;
  try {
    payload = JSON.parse(matches[0].payload);
  } catch (error) {
    payload = null;
  }
  assert(payload && matchesIdentifier(payload[field], expectedValue),
    `reconciliation ${systemCode}/${factType}/${factNo} payload ${field} did not match expected value: ${payload?.[field]}`);
  if (field === "payScene") {
    assert(matches[0].payScene === expectedValue,
      `reconciliation ${systemCode}/${factType}/${factNo} top-level pay scene was ${matches[0].payScene}, expected ${expectedValue}`);
  }
  return matches[0];
}

export function assertReconciliationFactPayScene(snapshot, systemCode, factType, factNo, expectedPayScene) {
  const matches = findReconciliationFacts(snapshot?.facts, systemCode, factType, factNo);
  assert(matches.length === 1 && matches[0].payScene === expectedPayScene,
    `reconciliation ${systemCode}/${factType}/${factNo} top-level pay scene was ${matches[0]?.payScene}, expected ${expectedPayScene}`);
  return matches[0];
}

export function assertReconciliationFactPayloadText(snapshot, systemCode, factType, factNo, expectedText) {
  const matches = findReconciliationFacts(snapshot?.facts, systemCode, factType, factNo);
  assert(matches.length === 1 && typeof matches[0].payload === "string"
    && matches[0].payload.includes(normalizeIdentifier(expectedText)),
  `reconciliation ${systemCode}/${factType}/${factNo} payload did not contain expected text: ${matches[0]?.payload}`);
  return matches[0];
}

export function assertAmountFlow(clearingOrder, settlementOrder, expectedOrderAmount) {
  const orderAmount = parseMoneyAmount(clearingOrder?.orderAmount);
  const expectedAmount = parseMoneyAmount(expectedOrderAmount);
  const workerAmount = parseMoneyAmount(clearingOrder?.workerAmount);
  const shouldSettleAmount = parseMoneyAmount(settlementOrder?.shouldSettleAmount);
  const deductAmount = parseMoneyAmount(settlementOrder?.deductAmount);
  const netSettleAmount = parseMoneyAmount(settlementOrder?.netSettleAmount);
  assert(orderAmount != null && expectedAmount != null
    && orderAmount === expectedAmount,
    `clearing order amount was ${clearingOrder?.orderAmount}, expected ${expectedOrderAmount}`);
  assert(workerAmount != null && netSettleAmount != null
    && netSettleAmount >= 0n
    && netSettleAmount <= orderAmount
    && workerAmount === netSettleAmount,
    `worker clearing amount ${clearingOrder?.workerAmount} did not match settlement net amount ${settlementOrder?.netSettleAmount}`);
  assert(shouldSettleAmount != null && deductAmount != null
    && shouldSettleAmount >= 0n
    && deductAmount >= 0n
    && shouldSettleAmount <= orderAmount
    && shouldSettleAmount - deductAmount === netSettleAmount,
  `settlement amount breakdown did not match net amount: ${settlementOrder?.shouldSettleAmount}-${settlementOrder?.deductAmount} != ${settlementOrder?.netSettleAmount}`);
  return settlementOrder;
}

function parseMoneyAmount(value) {
  const normalized = String(value == null ? "" : value).replace(/[,¥]/g, "").trim();
  if (!/^\d{1,16}(?:\.\d{1,2})?$/.test(normalized)) {
    return null;
  }
  const [integerPart, fractionPart] = normalized.split(".");
  return BigInt(integerPart) * 100n + BigInt((fractionPart || "").padEnd(2, "0"));
}

export async function api(url, options = {}) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  let response;
  try {
    response = await fetch(url, {
      ...options,
      redirect: "error",
      headers: {
        "Content-Type": "application/json",
        ...(options.headers || {})
      },
      signal: controller.signal
    });
  } catch (error) {
    const timeoutError = error?.name === "AbortError"
      ? new Error(`request timed out after ${REQUEST_TIMEOUT_MS}ms`)
      : error;
    throw new Error(formatRequestError(url, timeoutError), { cause: error });
  } finally {
    clearTimeout(timeoutId);
  }
  let payload;
  try {
    payload = await response.json();
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    throw new Error(
      `${url} returned invalid JSON: ${detail}; check the target service response`,
      { cause: error }
    );
  }
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
    throw new Error(
      `${url} returned an invalid JSON envelope: expected an object; check the target service response`
    );
  }
  assert(response.ok, `${url} returned HTTP ${response.status}`);
  assert(payload.code === "0", `${url} returned ${payload.code}: ${payload.message}`);
  return payload.data;
}

async function waitFor(description, action, predicate, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;
  let latest;
  while (Date.now() < deadline) {
    latest = await action();
    if (predicate(latest)) {
      return latest;
    }
    await new Promise((resolve) => setTimeout(resolve, 300));
  }
  throw new Error(`${description} not ready within ${timeoutMs}ms: ${JSON.stringify(latest)}`);
}

export async function verifyLocalDemoTargets(targets, request = api, environment = process.env) {
  assert(environment.HSP_LOCAL_PAYMENT_CHAIN_SCOPED === "true",
    "run only through local-stack verify payment-chain or verify all");
  assert(targets && Object.keys(targets).length === Object.keys(LOCAL_PAYMENT_CHAIN_TARGETS).length,
    "local happy-path requires all isolated service targets");
  for (const [service, expectedBaseUrl] of Object.entries(LOCAL_PAYMENT_CHAIN_TARGETS)) {
    const baseUrl = targets[service];
    assertLocalDemoBaseUrl(baseUrl, service);
    assert(baseUrl === expectedBaseUrl, `${service} must use its isolated local-stack port`);
  }
  for (const [service, expectedIdentity] of Object.entries(LOCAL_PAYMENT_CHAIN_IDENTITIES)) {
    const identity = await request(`${targets[service]}/api/demo/identity`);
    assert(identity === expectedIdentity,
      `${expectedIdentity.split("-h2-")[0]} did not confirm local H2 identity; refusing local happy-path writes`);
  }
}

async function verifyPaymentRequestStatus(paymentOrderId, orderNo, status, style, countField) {
  const filter = `paymentOrderId=${encodeURIComponent(paymentOrderId)}&orderNo=${encodeURIComponent(orderNo)}&requestStatus=${encodeURIComponent(status)}`;
  const page = await api(`${paymentBaseUrl}/api/payment-requests?${filter}`);
  const overview = await api(`${paymentBaseUrl}/api/payment-requests/overview?${filter}`);
  const requestNo = page?.items?.[0]?.requestNo;
  const detail = requestNo
    ? await api(`${paymentBaseUrl}/api/payment-requests/${encodeURIComponent(requestNo)}`) : null;
  return assertPaymentRequestDiagnostics(page, overview, detail,
    paymentOrderId, status, style, countField);
}

async function main() {
  await verifyLocalDemoTargets({
    paymentBaseUrl, opsConfigBaseUrl, riskBaseUrl, gatewayBaseUrl,
    clearingBaseUrl, settlementBaseUrl, accountingBaseUrl, refundBaseUrl, reconciliationBaseUrl
  });
  if (!process.env.HSP_REFUND_CALLBACK_HTTP_SECRET?.trim()) {
    throw new Error("HSP_REFUND_CALLBACK_HTTP_SECRET is required for the local refund callback");
  }
  const queryMode = process.env.HSP_LOCAL_PAYMENT_QUERY_CHAIN_SCOPED === "true";
  const expectedPayScene = "HOME_CLEAN";
  const prepay = assertPrepayOrder(await api(`${paymentBaseUrl}/api/payments/prepay`, {
    method: "POST",
    body: JSON.stringify({
      orderNo,
      payScene: expectedPayScene,
      customerName: "local happy path",
      amount: 88,
      cashierTitle: "home service payment"
    })
  }), orderNo, "88.00", expectedPayScene);
  normalizePrepayResult(prepay, orderNo, "88.00", "local happy path", expectedPayScene);
  miniProgramPayment.normalizePrepayResult(prepay, orderNo, "88.00", "local happy path", expectedPayScene);
  validateCommandResponse("prepay", { "order-no": orderNo, amount: "88.00" }, { data: prepay });
  assertPaymentBusinessFacts(prepay, null, "local happy path", "prepay response");
  assert(typeof prepay.billNo === "string" && prepay.billNo.trim(),
    "prepay response did not contain a usable bill number");
  const billNo = prepay.billNo.trim();
  const paymentBusinessChainId = `CHAIN-${normalizeIdentifier(prepay.paymentOrderId)}`;

  assertBillPaymentFacts(
    await api(`${paymentBaseUrl}/api/bills/${encodeURIComponent(normalizeIdentifier(billNo))}`),
    billNo, orderNo, prepay.prepayOrderNo, prepay.paymentOrderId, expectedPayScene,
    "88.00", "0.00", "88.00", "待支付", "warn", "PREPAY_CREATED"
  );

  const cashier = assertCashierPage(await api(`${paymentBaseUrl}/api/payments/cashier/${encodeURIComponent(normalizeIdentifier(prepay.prepayOrderNo))}?terminal=H5_WEB`),
    prepay.prepayOrderNo, prepay.paymentOrderId, orderNo, "88.00", prepay.expiresAt, expectedPayScene);
  normalizeCashierPageResult(cashier, prepay.prepayOrderNo, expectedPayScene);
  miniProgramPayment.normalizeCashierPageResult(cashier, prepay.prepayOrderNo, expectedPayScene);
  validateCommandResponse("cashier", { "prepay-order-no": prepay.prepayOrderNo }, { data: cashier });
  assertPaymentBusinessFacts(cashier, billNo, "local happy path", "cashier response");

  const submitPayload = {
    prepayOrderNo: normalizeIdentifier(prepay.prepayOrderNo),
    paymentMethod: "微信支付",
    channelCode: "wx_h5",
    sourceAppId: "housekeeping-h5-web",
    merchantNo: "MCH_WX_10001",
    accessToken: "token-housekeeping-h5-web",
    terminal: "H5",
    clientDeviceId: "local-happy-path",
    payerPhone: "13800000000",
    idempotencyKey: orderNo
  };
  const submitResult = assertPaymentSubmission(await api(`${paymentBaseUrl}/api/payments/submit`, {
    method: "POST",
    body: JSON.stringify(submitPayload)
  }), prepay.paymentOrderId, prepay.prepayOrderNo, orderNo, "88.00", prepay.expiresAt,
    paymentBusinessChainId, submitPayload.paymentMethod, submitPayload.channelCode, expectedPayScene);
  normalizePaymentSubmitResult(
    submitResult, prepay.prepayOrderNo, submitPayload.paymentMethod, submitPayload.channelCode,
    expectedPayScene
  );
  miniProgramPayment.normalizePaymentSubmitResult(
    submitResult, prepay.prepayOrderNo, submitPayload.paymentMethod, submitPayload.channelCode,
    expectedPayScene
  );
  validateCommandResponse("submit", {
    "prepay-order-no": prepay.prepayOrderNo,
    "channel-code": submitPayload.channelCode
  }, { data: submitResult });
  assertPaymentBusinessFacts(submitResult, billNo, "local happy path", "payment submission");
  const repeatedSubmitResult = assertIdempotentPaymentSubmission(
    submitResult,
    await api(`${paymentBaseUrl}/api/payments/submit`, {
      method: "POST",
      body: JSON.stringify(submitPayload)
    }),
    prepay.paymentOrderId,
    prepay.prepayOrderNo,
    orderNo,
    "88.00",
    prepay.expiresAt,
    submitPayload.paymentMethod,
    submitPayload.channelCode,
    expectedPayScene
  );
  assertPaymentBusinessFacts(repeatedSubmitResult, billNo, "local happy path", "repeated payment submission");
  assert(matchesIdentifier(repeatedSubmitResult.paymentOrderId, submitResult.paymentOrderId.trim()),
    "repeated payment submission changed payment order");

  const submitted = assertPaymentIdentity(
    await api(`${paymentBaseUrl}/api/payments/${encodeURIComponent(normalizeIdentifier(prepay.paymentOrderId))}`),
    prepay.paymentOrderId,
    prepay.prepayOrderNo,
    orderNo,
    paymentBusinessChainId,
    expectedPayScene
  );
  normalizePaymentDetailResult(submitted, prepay.paymentOrderId, expectedPayScene);
  miniProgramPayment.normalizePaymentDetailResult(submitted, prepay.paymentOrderId, expectedPayScene);
  validateCommandResponse("detail", { "payment-order-id": prepay.paymentOrderId }, { data: submitted });
  assertPaymentBusinessFacts(submitted, billNo, "local happy path", "submitted payment detail");
  assert(submitted.status === "WAIT_CALLBACK" && submitted.statusType === "warn",
    `unexpected submitted payment state: ${submitted.status}/${submitted.statusType}`);
  assert(submitted.channelTransactionNo, "channel transaction number was not created");
  const pendingRequest = await verifyPaymentRequestStatus(
    prepay.paymentOrderId, orderNo, "处理中", "info", "processingRequestCount"
  );

  const waitingCashier = assertBoundCashierPage(
    await api(`${paymentBaseUrl}/api/payments/cashier/${encodeURIComponent(normalizeIdentifier(prepay.prepayOrderNo))}?terminal=H5_WEB`),
    submitted, "WAIT_CALLBACK", "88.00"
  );
  normalizeCashierPageResult(waitingCashier, prepay.prepayOrderNo, expectedPayScene);
  miniProgramPayment.normalizeCashierPageResult(waitingCashier, prepay.prepayOrderNo, expectedPayScene);
  assertPaymentBusinessFacts(waitingCashier, billNo, "local happy path", "waiting cashier");

  let paid;
  if (queryMode) {
    const upstreamBaseUrl = "http://127.0.0.1:18190";
    assert(await api(`${upstreamBaseUrl}/health`) === "local-payment-query-upstream",
      "query upstream did not confirm its local identity before fixture writes");
    await api(`${upstreamBaseUrl}/arm`, {
      method: "POST",
      body: JSON.stringify({
        paymentOrderId: normalizeIdentifier(prepay.paymentOrderId),
        orderNo,
        channelTransactionNo: submitted.channelTransactionNo
      })
    });
    const queryRequest = { paymentOrderId: normalizeIdentifier(prepay.paymentOrderId), operator: "local-query-chain" };
    let queryFailure;
    try {
      await api(`${paymentBaseUrl}/api/payments/admin/query`, {
        method: "POST", body: JSON.stringify(queryRequest)
      });
    } catch (error) {
      queryFailure = error;
    }
    assert(queryFailure?.message?.includes("PAYMENT-1026"),
      "invalid upstream status was not rejected by the payment query chain");
    const rejectedPayment = await api(`${paymentBaseUrl}/api/payments/${encodeURIComponent(prepay.paymentOrderId)}`);
    assert(rejectedPayment.status === "WAIT_CALLBACK", "rejected upstream status changed payment state");
    const rejectedEvents = await api(`${paymentBaseUrl}/api/payment-events?paymentOrderId=${encodeURIComponent(prepay.paymentOrderId)}&eventType=PAYMENT_SUCCESS`);
    assert(rejectedEvents.total === 0, "rejected upstream status emitted a payment success event");
    const rejectedBill = await api(`${paymentBaseUrl}/api/bills/${encodeURIComponent(normalizeIdentifier(billNo))}`);
    assert(matchesIdentifier(rejectedBill.billNo, billNo)
      && matchesIdentifier(rejectedBill.latestPaymentOrderId, prepay.paymentOrderId)
      && rejectedBill.latestPaymentStatus === "WAIT_CALLBACK" && rejectedBill.billStatus === "待支付",
    "rejected query changed the bound bill state");
    assertPaymentAmount({ amount: rejectedBill.paidAmount }, "0.00");
    assertPaymentAmount({ amount: rejectedBill.unpaidAmount }, "88.00");
    paid = assertPaymentIdentity(await api(`${paymentBaseUrl}/api/payments/admin/query`, {
      method: "POST", body: JSON.stringify(queryRequest)
    }), prepay.paymentOrderId, prepay.prepayOrderNo, orderNo, paymentBusinessChainId, expectedPayScene);
    assert(paid.querySource === "LOCAL_QUERY_CHAIN_STUB", "payment did not use the controlled gateway query");
    const replay = await api(`${paymentBaseUrl}/api/payments/admin/query`, {
      method: "POST", body: JSON.stringify(queryRequest)
    });
    assert(matchesIdentifier(replay.paymentOrderId, paid.paymentOrderId) && replay.status === "SUCCESS",
      "terminal payment query changed the settled payment");
    const calls = await api(`${upstreamBaseUrl}/calls`);
    assert(calls.total === 2 && calls.items?.every((item) => item.paymentOrderId === prepay.paymentOrderId
      && item.orderNo === orderNo && item.channelCode === "wx_h5"
      && item.channelTransactionNo === submitted.channelTransactionNo),
    "terminal replay re-queried the upstream or lost the bound transaction");
    const gatewayAudits = await api(`${gatewayBaseUrl}/api/gateway-access/audit-logs?resultStatus=SUCCESS&pageSize=20`);
    assert(gatewayAudits.records?.filter((item) => item.operationType === "PAYMENT_QUERY").length === 1,
      "gateway query audit did not record exactly one successful proxy call");
    const failedGatewayAudits = await api(`${gatewayBaseUrl}/api/gateway-access/audit-logs?resultStatus=FAILED&pageSize=20`);
    assert(failedGatewayAudits.records?.filter((item) => item.operationType === "PAYMENT_QUERY").length === 1,
      "gateway query did not audit the rejected upstream status");
  } else {
    const callbackPayload = {
      paymentOrderId: normalizeIdentifier(prepay.paymentOrderId),
      channelTransactionNo: submitted.channelTransactionNo,
      tradeStatus: "SUCCESS"
    };
    paid = assertPaymentIdentity(await api(`${paymentBaseUrl}/api/payments/callback/wx_h5`, {
      method: "POST",
      body: JSON.stringify(callbackPayload)
    }), prepay.paymentOrderId, prepay.prepayOrderNo, orderNo, paymentBusinessChainId, expectedPayScene);
    assertIdempotentPaymentCallback(
      paid,
      await api(`${paymentBaseUrl}/api/payments/callback/wx_h5`, {
        method: "POST",
        body: JSON.stringify(callbackPayload)
      }),
      prepay.paymentOrderId,
      prepay.prepayOrderNo,
      orderNo,
      "88.00",
      submitPayload.paymentMethod,
      submitPayload.channelCode,
      expectedPayScene
    );
  }
  assertPaymentBusinessFacts(paid, billNo, "local happy path", queryMode ? "payment query" : "payment callback");
  assert(paid.status === "SUCCESS" && paid.statusType === "success",
    `unexpected payment terminal display state: ${paid.status}/${paid.statusType}`);
  assertPaymentAmount(paid, "88.00");
  const paidDetail = assertPaymentSuccessDetail(
    paid,
    await api(`${paymentBaseUrl}/api/payments/${encodeURIComponent(normalizeIdentifier(prepay.paymentOrderId))}`),
    prepay.paymentOrderId,
    prepay.prepayOrderNo,
    orderNo,
    "88.00",
    expectedPayScene
  );
  const successfulRequest = await verifyPaymentRequestStatus(
    prepay.paymentOrderId, orderNo, "成功", "success", "successRequestCount"
  );
  assert(successfulRequest.requestNo === pendingRequest.requestNo,
    "payment completion changed the original submitted request number");
  const oldProcessingPage = await api(`${paymentBaseUrl}/api/payment-requests?paymentOrderId=${encodeURIComponent(prepay.paymentOrderId)}&requestStatus=${encodeURIComponent("处理中")}`);
  assert(oldProcessingPage.total === 0 && oldProcessingPage.items?.length === 0,
    "completed payment request remained in the processing filter");
  assertBillPaymentFacts(
    await api(`${paymentBaseUrl}/api/bills/${encodeURIComponent(normalizeIdentifier(billNo))}`),
    billNo, orderNo, prepay.prepayOrderNo, prepay.paymentOrderId, expectedPayScene,
    "88.00", "88.00", "0.00", "已结清", "success", "SUCCESS"
  );
  normalizePaymentDetailResult(paidDetail, prepay.paymentOrderId, expectedPayScene);
  miniProgramPayment.normalizePaymentDetailResult(paidDetail, prepay.paymentOrderId, expectedPayScene);
  validateCommandResponse("detail", { "payment-order-id": prepay.paymentOrderId }, { data: paidDetail });
  assertPaymentBusinessFacts(paidDetail, billNo, "local happy path", "paid payment detail");

  const paidCashier = assertBoundCashierPage(
    await api(`${paymentBaseUrl}/api/payments/cashier/${encodeURIComponent(normalizeIdentifier(prepay.prepayOrderNo))}?terminal=H5_WEB`),
    paidDetail, "SUCCESS", "88.00"
  );
  normalizeCashierPageResult(paidCashier, prepay.prepayOrderNo, expectedPayScene);
  miniProgramPayment.normalizeCashierPageResult(paidCashier, prepay.prepayOrderNo, expectedPayScene);
  assertPaymentBusinessFacts(paidCashier, billNo, "local happy path", "successful cashier");

  const paymentRecordQuery = new URLSearchParams({
    businessOrderNo: orderNo,
    customerName: "local happy path",
    paymentType: "消费支付",
    pageNo: "1",
    pageSize: "20"
  });
  const paymentRecord = assertPaymentRecordProjection(
    normalizePaymentRecordListPage(await api(`${paymentBaseUrl}/api/payment-records?${paymentRecordQuery}`)),
    normalizePaymentRecordDetailResult(
      await api(`${paymentBaseUrl}/api/payment-records/${encodeURIComponent(normalizeIdentifier(prepay.paymentOrderId))}`),
      prepay.paymentOrderId
    ),
    prepay.paymentOrderId, orderNo, expectedPayScene, "88.00", paidDetail.channelReturnCode,
    prepay.expiresAt, paidDetail.channelTransactionNo
  );
  const unrelatedCustomerRecordQuery = new URLSearchParams(paymentRecordQuery);
  unrelatedCustomerRecordQuery.set("customerName", "another customer");
  const unrelatedCustomerRecordPage = normalizePaymentRecordListPage(await api(
    `${paymentBaseUrl}/api/payment-records?${unrelatedCustomerRecordQuery}`
  ));
  assert(unrelatedCustomerRecordPage.total === 0 && unrelatedCustomerRecordPage.items.length === 0,
    "payment record customer-name filter returned another customer's payment");

  const paymentEvents = await waitFor(
    "payment event dispatch",
    () => api(`${paymentBaseUrl}/api/payment-events?paymentOrderId=${encodeURIComponent(normalizeIdentifier(prepay.paymentOrderId))}&pageNo=1&pageSize=20`),
    (page) => page.items.some((item) => isPaymentSuccessEventDispatched(
      item,
      prepay.paymentOrderId,
      orderNo
    ))
  );
  const paymentEvent = assertSinglePaymentSuccessEvent(
    paymentEvents,
    prepay.paymentOrderId,
    orderNo,
    paymentBusinessChainId,
    paidDetail.successAt,
    paidDetail.amount,
    paidDetail.channel,
    expectedPayScene
  );
  const successfulResubmission = assertSuccessfulPaymentResubmission(
    paidDetail,
    await api(`${paymentBaseUrl}/api/payments/submit`, {
      method: "POST",
      body: JSON.stringify(submitPayload)
    }),
    prepay.prepayOrderNo,
    orderNo,
    paymentBusinessChainId
  );
  assertPaymentBusinessFacts(successfulResubmission, billNo, "local happy path", "successful resubmission");
  const eventsAfterResubmission = await api(
    `${paymentBaseUrl}/api/payment-events?paymentOrderId=${encodeURIComponent(normalizeIdentifier(prepay.paymentOrderId))}&pageNo=1&pageSize=20`
  );
  const eventAfterResubmission = assertSinglePaymentSuccessEvent(
    eventsAfterResubmission,
    prepay.paymentOrderId,
    orderNo,
    paymentBusinessChainId,
    paidDetail.successAt,
    paidDetail.amount,
    paidDetail.channel,
    expectedPayScene
  );
  assert(matchesIdentifier(eventAfterResubmission.eventNo, paymentEvent.eventNo),
    "successful resubmission changed the payment success event");
  assertPaymentRecordEventTopic(paymentRecord, paymentEvent);
  const clearingPaymentEvents = await api(
    `${clearingBaseUrl}/api/clearing/events?eventType=PAYMENT_SUCCESS&bizNo=${encodeURIComponent(normalizeIdentifier(prepay.paymentOrderId))}&pageNo=1&pageSize=20`
  );
  assertSingleClearingPaymentSuccessEvent(
    clearingPaymentEvents,
    prepay.paymentOrderId,
    orderNo,
    paymentBusinessChainId,
    expectedPayScene,
    paymentEvent.createdAt
  );

  const clearingOrders = await waitFor(
    "clearing order",
    () => api(`${clearingBaseUrl}/api/clearing/orders?paymentOrderId=${encodeURIComponent(normalizeIdentifier(prepay.paymentOrderId))}&pageNo=1&pageSize=20`),
    (page) => findClearingOrder(page.items, prepay.paymentOrderId, "清分成功", orderNo) != null
  );
  const clearingOrder = assertClearingIdentity(
    findClearingOrder(clearingOrders.items, prepay.paymentOrderId, "清分成功", orderNo),
    prepay.paymentOrderId,
    orderNo,
    paymentEvent.createdAt,
    expectedPayScene
  );
  const clearingDetail = await api(
    `${clearingBaseUrl}/api/clearing/orders/${encodeURIComponent(normalizeIdentifier(clearingOrder.clearingNo))}`
  );
  const clearingTargetNo = assertClearingTarget(clearingDetail, clearingOrder);
  const clearingEvents = await api(
    `${clearingBaseUrl}/api/clearing/events?eventType=CLEARING_GENERATED&bizNo=${encodeURIComponent(normalizeIdentifier(clearingOrder.clearingNo))}&pageNo=1&pageSize=20`
  );
  assertClearingGeneratedEvent(
    clearingEvents.items?.[0],
    clearingOrder.clearingNo,
    clearingOrder.paymentOrderId,
    clearingOrder.businessChainId,
    clearingTargetNo,
    clearingOrder.workerAmount,
    paymentEvent.createdAt,
    "WORKER",
    expectedPayScene
  );
  const settlementEvents = await api(
    `${settlementBaseUrl}/api/settlements/events?eventType=CLEARING_GENERATED&bizNo=${encodeURIComponent(normalizeIdentifier(clearingOrder.clearingNo))}&pageNo=1&pageSize=20`
  );
  assertSettlementGeneratedEvent(
    settlementEvents.items?.[0],
    clearingOrder.clearingNo,
    clearingOrder.businessChainId,
    clearingOrder.createdAt,
    expectedPayScene
  );

  const settlementOrders = await waitFor(
    "settlement order",
    () => api(`${settlementBaseUrl}/api/settlements/orders?clearingNo=${encodeURIComponent(normalizeIdentifier(clearingOrder.clearingNo))}&pageNo=1&pageSize=20`),
    (page) => findSettlementOrder(
      page.items,
      clearingOrder.clearingNo,
      clearingOrder.businessChainId
    ) != null
  );
  const settlementOrder = assertSettlementIdentity(
    assertSettlementDraft(findSettlementOrder(
      settlementOrders.items,
      clearingOrder.clearingNo,
      clearingOrder.businessChainId
    )),
    clearingOrder.clearingNo,
    clearingOrder.businessChainId,
    clearingOrder.createdAt,
    null,
    expectedPayScene
  );
  assertAmountFlow(clearingOrder, settlementOrder, "¥88.00");

  const auditedSettlement = assertSettlementApproved(await api(
    `${settlementBaseUrl}/api/settlements/orders/${encodeURIComponent(normalizeIdentifier(settlementOrder.settlementNo))}/audit`,
    {
      method: "POST",
      body: JSON.stringify({ operatorName: "local happy path auditor", auditRemark: "local happy path approved" })
    }
  ),
    settlementOrder.settlementNo,
    settlementOrder.clearingNo,
    settlementOrder.businessChainId,
    settlementOrder.netSettleAmount,
    settlementOrder.batchNo,
    settlementOrder.targetNo,
    settlementOrder.createdAt,
    expectedPayScene
  );
  assertSettlementFullDetail(
    await api(`${settlementBaseUrl}/api/settlements/orders/${encodeURIComponent(normalizeIdentifier(settlementOrder.settlementNo))}/full`),
    settlementOrder.settlementNo,
    settlementOrder.businessChainId,
    settlementOrder.shouldSettleAmount,
    settlementOrder.deductAmount,
    settlementOrder.netSettleAmount,
    "local happy path auditor"
  );
  const payoutBatches = await waitFor(
    "settlement payout batch",
    () => api(`${settlementBaseUrl}/api/settlements/payouts?batchNo=${encodeURIComponent(normalizeIdentifier(auditedSettlement.batchNo))}&pageNo=1&pageSize=20`),
    (page) => findPayoutBatch(page.items, null, "待出款", auditedSettlement.batchNo) != null
  );
  const payoutBatch = findPayoutBatch(
    payoutBatches.items,
    null,
    "待出款",
    auditedSettlement.batchNo
  );
  const pendingPayoutRecords = await api(
    `${settlementBaseUrl}/api/settlements/payouts/${encodeURIComponent(normalizeIdentifier(payoutBatch.payoutBatchNo))}/records?pageNo=1&pageSize=100`
  );
  assertPayoutBatchPendingRecords(
    pendingPayoutRecords.items,
    payoutBatch.payoutBatchNo,
    payoutBatch.payoutCount,
    payoutBatch.totalAmount,
    settlementOrder.targetNo
  );
  assertPayoutRecord(
    findPayoutRecord(pendingPayoutRecords.items, auditedSettlement.settlementNo),
    payoutBatch.payoutBatchNo,
    auditedSettlement.settlementNo,
    auditedSettlement.netSettleAmount,
    "待出款",
    auditedSettlement.createdAt,
    settlementOrder.targetNo
  );
  const executedPayoutBatch = await api(
    `${settlementBaseUrl}/api/settlements/payouts/${encodeURIComponent(normalizeIdentifier(payoutBatch.payoutBatchNo))}/execute`,
    {
      method: "POST",
      body: JSON.stringify({ operatorName: "local happy path operator", remark: "local happy path payout", executionResult: "SUCCESS" })
    }
  );
  const executedPayoutRecords = await api(
    `${settlementBaseUrl}/api/settlements/payouts/${encodeURIComponent(normalizeIdentifier(executedPayoutBatch.payoutBatchNo))}/records?pageNo=1&pageSize=100`
  );
  assertPayoutBatchRecords(
    executedPayoutRecords.items,
    executedPayoutBatch.payoutBatchNo,
    executedPayoutBatch.payoutCount,
    executedPayoutBatch.totalAmount,
    executedPayoutBatch.finishedAt,
    settlementOrder.targetNo
  );
  const executedPayoutRecord = assertPayoutRecord(
    findPayoutRecord(executedPayoutRecords.items, auditedSettlement.settlementNo),
    executedPayoutBatch.payoutBatchNo,
    auditedSettlement.settlementNo,
    auditedSettlement.netSettleAmount,
    "已发放",
    auditedSettlement.createdAt,
    settlementOrder.targetNo
  );
  assertPayoutBatchExecuted(
    executedPayoutBatch,
    payoutBatch.payoutBatchNo,
    executedPayoutRecord.createdAt,
    auditedSettlement.batchNo
  );
  const paidSettlement = await api(
    `${settlementBaseUrl}/api/settlements/orders/${encodeURIComponent(normalizeIdentifier(auditedSettlement.settlementNo))}`
  );
  assertSettlementClosed(
    paidSettlement,
    auditedSettlement.settlementNo,
    clearingOrder.clearingNo,
    clearingOrder.businessChainId,
    auditedSettlement.netSettleAmount,
    auditedSettlement.batchNo,
    settlementOrder.targetNo,
    settlementOrder.createdAt,
    expectedPayScene
  );

  const accountingPaymentEvents = await api(
    `${accountingBaseUrl}/api/accounting/events?bizNo=${encodeURIComponent(normalizeIdentifier(prepay.paymentOrderId))}&pageNo=1&pageSize=20`
  );
  const accountingClearingEvents = await api(
    `${accountingBaseUrl}/api/accounting/events?bizNo=${encodeURIComponent(normalizeIdentifier(clearingOrder.clearingNo))}&pageNo=1&pageSize=20`
  );
  const accountingPaymentEvent = assertAccountingEvent(
    accountingPaymentEvents,
    prepay.paymentOrderId,
    "PAYMENT_SUCCESS",
    paymentBusinessChainId,
    paymentEvent.createdAt,
    paid.amount,
    null,
    orderNo,
    null,
    expectedPayScene
  );
  const accountingClearingEvent = assertAccountingEvent(
    accountingClearingEvents,
    clearingOrder.clearingNo,
    "CLEARING_GENERATED",
    paymentBusinessChainId,
    clearingOrder.createdAt,
    clearingOrder.workerAmount,
    null,
    null,
    prepay.paymentOrderId,
    expectedPayScene
  );
  const accountingClearingLedgers = await api(
    `${accountingBaseUrl}/api/accounting/ledgers?bizNo=${encodeURIComponent(normalizeIdentifier(clearingOrder.clearingNo))}&bizType=CLEARING_GENERATED_EVENT&pageNo=1&pageSize=20`
  );
  assertClearingAccountingAmount(accountingClearingLedgers, clearingOrder, accountingClearingEvent.createdAt, expectedPayScene);
  const accountingPaymentLedgers = await api(
    `${accountingBaseUrl}/api/accounting/ledgers?bizNo=${encodeURIComponent(normalizeIdentifier(prepay.paymentOrderId))}&bizType=PAYMENT_SUCCESS_EVENT&pageNo=1&pageSize=20`
  );
  assertAccountingLedgerAmount(
    accountingPaymentLedgers,
    prepay.paymentOrderId,
    "PAYMENT_SUCCESS_EVENT",
    "贷方",
    "88.00",
    accountingPaymentEvent.createdAt,
    expectedPayScene
  );

  const refundApplyPayload = {
    paymentOrderId: normalizeIdentifier(prepay.paymentOrderId),
    refundAmount: 18,
    refundMethod: "ORIGINAL",
    refundReason: "local happy path refund",
    idempotencyKey: `REFUND-${orderNo}`
  };
  const refund = assertRefundPaidAmount(assertRefundAmount(assertRefundIdentity(await api(`${refundBaseUrl}/api/refunds/apply`, {
    method: "POST",
    body: JSON.stringify(refundApplyPayload)
  }), "", prepay.paymentOrderId, orderNo, paymentBusinessChainId), "18.00"), paid.amount);
  assertRefundCustomerName(refund, "local happy path", "application");
  assertRefundPayScene(refund, expectedPayScene, "application");
  assertRefundMethod(refund, "ORIGINAL");
  assertRefundReason(refund, refundApplyPayload.refundReason);
  const repeatedRefundApplication = assertRefundPaidAmount(await api(`${refundBaseUrl}/api/refunds/apply`, {
      method: "POST",
    body: JSON.stringify(refundApplyPayload)
  }), paid.amount);
  assertRefundCustomerName(repeatedRefundApplication, "local happy path", "repeated application");
  assertRefundPayScene(repeatedRefundApplication, expectedPayScene, "repeated application");
  assertRefundMethod(repeatedRefundApplication, "ORIGINAL");
  assertRefundReason(repeatedRefundApplication, refundApplyPayload.refundReason);
  assertIdempotentRefundApplication(
    refund,
    repeatedRefundApplication,
    prepay.paymentOrderId,
    "18.00",
    orderNo,
    refundApplyPayload.refundReason
  );
  assertRefundWithinPayment(refund, paid.amount);
  assert(typeof refund.refundOrderId === "string" && refund.refundOrderId.trim(),
    "refund application did not create a usable refund order number");
  const approvedRefund = assertRefundPaidAmount(assertRefundAmount(assertRefundIdentity(await api(`${refundBaseUrl}/api/refunds/${encodeURIComponent(normalizeIdentifier(refund.refundOrderId))}/approve`, {
    method: "POST",
    body: JSON.stringify({ remark: "local happy path approved" })
  }), refund.refundOrderId, prepay.paymentOrderId, orderNo, paymentBusinessChainId), "18.00"), paid.amount);
  assertRefundCustomerName(approvedRefund, "local happy path", "approval");
  assertRefundPayScene(approvedRefund, expectedPayScene, "approval");
  assertRefundMethod(approvedRefund, "ORIGINAL");
  assertRefundReason(approvedRefund, refundApplyPayload.refundReason);
  assertRefundLifecycleTime(approvedRefund, "approval");
  assertRefundStatus(approvedRefund, "APPROVED", "approval");
  const submittedRefund = assertRefundPaidAmount(assertRefundAmount(assertRefundIdentity(await api(`${refundBaseUrl}/api/refunds/${encodeURIComponent(normalizeIdentifier(refund.refundOrderId))}/submit`, {
    method: "POST",
    body: JSON.stringify({ remark: "local happy path submitted" })
  }), refund.refundOrderId, prepay.paymentOrderId, orderNo, paymentBusinessChainId), "18.00"), paid.amount);
  assertRefundCustomerName(submittedRefund, "local happy path", "channel submission");
  assertRefundPayScene(submittedRefund, expectedPayScene, "channel submission");
  assertRefundMethod(submittedRefund, "ORIGINAL");
  assertRefundReason(submittedRefund, refundApplyPayload.refundReason);
  assertRefundLifecycleTime(submittedRefund, "channel submission");
  assertRefundStatus(submittedRefund, "PROCESSING", "channel submission");
  const refundCallbackPayload = {
    result: "SUCCESS",
    channelRefundId: `LOCAL-CHANNEL-REFUND-${suffix}`,
    rawMessage: "local happy path success"
  };
  const callbackRefundOrderId = normalizeIdentifier(refund.refundOrderId);
  const callbackBody = JSON.stringify(refundCallbackPayload);
  const callbackUrl = `${refundBaseUrl}/api/refunds/${encodeURIComponent(callbackRefundOrderId)}/callback`;
  for (const options of [
    { method: "POST", body: callbackBody },
    {
      method: "POST",
      body: JSON.stringify({ ...refundCallbackPayload, result: "FAIL" }),
      headers: refundCallbackHeaders(callbackRefundOrderId, callbackBody)
    }
  ]) {
    let rejected;
    try {
      await api(callbackUrl, options);
    } catch (error) {
      rejected = error;
    }
    assert(rejected?.message?.includes("returned HTTP 403"),
      "untrusted refund callback was not rejected before terminal state");
  }
  const afterRejectedCallback = await api(`${refundBaseUrl}/api/refunds/${encodeURIComponent(callbackRefundOrderId)}`);
  assertRefundStatus(afterRejectedCallback, "PROCESSING", "untrusted callback");
  assert(!afterRejectedCallback.terminalAt && !afterRejectedCallback.channelRefundId,
    "untrusted refund callback changed terminal facts");
  const refunded = assertRefundPaidAmount(assertRefundAmount(assertRefundIdentity(await api(callbackUrl, {
    method: "POST",
    body: callbackBody,
    headers: refundCallbackHeaders(callbackRefundOrderId, callbackBody)
  }), refund.refundOrderId, prepay.paymentOrderId, orderNo, paymentBusinessChainId), "18.00"), paid.amount);
  assertRefundCustomerName(refunded, "local happy path", "callback");
  assertRefundPayScene(refunded, expectedPayScene, "callback");
  assertRefundMethod(refunded, "ORIGINAL");
  assertRefundReason(refunded, refundApplyPayload.refundReason);
  assertRefundLifecycleTime(refunded, "callback");
  assert(refunded.status === "SUCCESS", `unexpected refund status: ${refunded.status}`);
  const repeatedRefundCallback = assertRefundPaidAmount(await api(callbackUrl, {
      method: "POST",
      body: callbackBody,
      headers: refundCallbackHeaders(callbackRefundOrderId, callbackBody)
    }), paid.amount);
  assertRefundCustomerName(repeatedRefundCallback, "local happy path", "repeated callback");
  assertRefundPayScene(repeatedRefundCallback, expectedPayScene, "repeated callback");
  assertRefundMethod(repeatedRefundCallback, "ORIGINAL");
  assertRefundReason(repeatedRefundCallback, refundApplyPayload.refundReason);
  assertIdempotentRefundCallback(
    refunded,
    repeatedRefundCallback,
    refund.refundOrderId,
    prepay.paymentOrderId,
    "18.00",
    orderNo,
    refundApplyPayload.refundReason
  );

  const refundDetail = assertRefundPaidAmount(assertRefundAmount(assertRefundIdentity(
    assertRefundSuccessDetail(
      refunded,
      await api(`${refundBaseUrl}/api/refunds/${encodeURIComponent(normalizeIdentifier(refund.refundOrderId))}`),
      refund.refundOrderId,
      prepay.paymentOrderId,
      "18.00",
      orderNo,
      refundApplyPayload.refundReason
    ),
    refund.refundOrderId,
    prepay.paymentOrderId,
    orderNo,
    paymentBusinessChainId
  ), "18.00"), paid.amount);
  assertRefundCustomerName(refundDetail, "local happy path", "detail query");
  assertRefundPayScene(refundDetail, expectedPayScene, "detail query");
  assertRefundMethod(refundDetail, "ORIGINAL");
  assertRefundReason(refundDetail, refundApplyPayload.refundReason);
  assertRefundChannelRefundId(refundDetail, refunded.channelRefundId, "detail query");
  assertRefundLifecycleTime(refundDetail, "detail query");
  assertRefundStatus(refundDetail, "SUCCESS", "detail query");

  const refundOutboxPage = await api(
    `${refundBaseUrl}/api/refunds/outbox?aggregateId=${encodeURIComponent(normalizeIdentifier(refund.refundOrderId))}&pageNo=1&pageSize=20`
  );
  const refundOutboxSource = assertSingleRefundSuccessOutbox(
    refundOutboxPage,
    refund.refundOrderId,
    refunded.terminalAt,
    prepay.paymentOrderId,
    "18.00",
    expectedPayScene
  );
  const refundOutbox = await api(
    `${refundBaseUrl}/api/refunds/outbox/${encodeURIComponent(normalizeIdentifier(refundOutboxSource.eventId))}/dispatch`,
    { method: "POST", body: JSON.stringify({ remark: "local happy path dispatch" }) }
  );
  assertRefundOutboxDispatch(
    refundOutbox,
    refundOutboxSource,
    refund.refundOrderId,
    prepay.paymentOrderId,
    "18.00",
    expectedPayScene
  );
  const repeatedRefundOutbox = await api(
    `${refundBaseUrl}/api/refunds/outbox/${encodeURIComponent(normalizeIdentifier(refundOutbox.eventId))}/dispatch`,
    { method: "POST", body: JSON.stringify({ remark: "local happy path repeated dispatch" }) }
  );
  assertRefundOutboxDispatch(
    repeatedRefundOutbox,
    refundOutboxSource,
    refund.refundOrderId,
    prepay.paymentOrderId,
    "18.00",
    expectedPayScene
  );

  const accountingRefundEvents = await api(
    `${accountingBaseUrl}/api/accounting/events?bizNo=${encodeURIComponent(normalizeIdentifier(refund.refundOrderId))}&pageNo=1&pageSize=20`
  );
  const accountingRefundEvent = assertAccountingEvent(
    accountingRefundEvents,
    refund.refundOrderId,
    "REFUND_SUCCESS",
    paymentBusinessChainId,
    null,
    "18.00",
    prepay.paymentOrderId,
    null,
    null,
    expectedPayScene
  );
  const accountingRefundLedgers = await api(
    `${accountingBaseUrl}/api/accounting/ledgers?bizNo=${encodeURIComponent(normalizeIdentifier(refund.refundOrderId))}&bizType=REFUND_SUCCESS_EVENT&pageNo=1&pageSize=20`
  );
  assertAccountingLedgerAmount(
    accountingRefundLedgers,
    refund.refundOrderId,
    "REFUND_SUCCESS_EVENT",
    "借方",
    "18.00",
    accountingRefundEvent.createdAt,
    expectedPayScene
  );
  const reconciliationChain = assertReconciliationChainIdentity(await api(
    `${reconciliationBaseUrl}/api/reconciliation/cross-system/chains/${encodeURIComponent(paymentBusinessChainId)}`
  ), paymentBusinessChainId);
  assertReconciliationCompletion(reconciliationChain);
  const requiredReconciliationFacts = [
    ["payment-core", "PAYMENT_SUCCESS", prepay.paymentOrderId, "88.00", orderNo, paymentEvent.createdAt],
    ["clearing-system", "CLEARING_GENERATED", clearingOrder.clearingNo, "88.00", prepay.paymentOrderId, clearingOrder.createdAt],
    ["settlement-system", "SETTLEMENT_CREATED", settlementOrder.settlementNo, settlementOrder.netSettleAmount, prepay.paymentOrderId, settlementOrder.createdAt],
    ["accounting-system", "ACCOUNTING_POSTED", accountingPaymentEvent.eventNo, "88.00", prepay.paymentOrderId, accountingPaymentEvent.createdAt],
    ["accounting-system", "ACCOUNTING_POSTED", accountingClearingEvent.eventNo, clearingOrder.workerAmount, clearingOrder.clearingNo, accountingClearingEvent.createdAt],
    ["accounting-system", "ACCOUNTING_POSTED", accountingRefundEvent.eventNo, "18.00", prepay.paymentOrderId, accountingRefundEvent.createdAt],
    ["refund-center", "REFUND_SUCCESS", refundOutbox.eventId, "18.00", refund.refundOrderId, refundOutboxSource.createdAt]
  ];
  for (const [systemCode, factType, factNo] of requiredReconciliationFacts) {
    assert(findReconciliationFacts(reconciliationChain.facts, systemCode, factType, factNo)
      .some((item) => item.factStatus === "SUCCESS"),
    `reconciliation ${systemCode}/${factType}/${factNo} fact was not recorded as SUCCESS`);
  }
  assertReconciliationFactAmounts(reconciliationChain, requiredReconciliationFacts);
  assertReconciliationFactPayload(
    reconciliationChain,
    "clearing-system",
    "CLEARING_GENERATED",
    clearingOrder.clearingNo,
    "clearingNo",
    clearingOrder.clearingNo
  );
  assertReconciliationFactPayload(
    reconciliationChain,
    "clearing-system",
    "CLEARING_GENERATED",
    clearingOrder.clearingNo,
    "payScene",
    expectedPayScene
  );
  assertReconciliationFactPayload(
    reconciliationChain,
    "settlement-system",
    "SETTLEMENT_CREATED",
    settlementOrder.settlementNo,
    "settlementNo",
    settlementOrder.settlementNo
  );
  assertReconciliationFactPayload(
    reconciliationChain,
    "settlement-system",
    "SETTLEMENT_CREATED",
    settlementOrder.settlementNo,
    "payScene",
    expectedPayScene
  );
  assertReconciliationFactPayload(
    reconciliationChain,
    "payment-core",
    "PAYMENT_SUCCESS",
    prepay.paymentOrderId,
    "paymentOrderId",
    prepay.paymentOrderId
  );
  assertReconciliationFactPayload(
    reconciliationChain,
    "payment-core",
    "PAYMENT_SUCCESS",
    prepay.paymentOrderId,
    "payScene",
    expectedPayScene
  );
  for (const eventNo of [accountingPaymentEvent.eventNo, accountingClearingEvent.eventNo, accountingRefundEvent.eventNo]) {
    assertReconciliationFactPayload(
      reconciliationChain,
      "accounting-system",
      "ACCOUNTING_POSTED",
      eventNo,
      "eventNo",
      eventNo
    );
  }
  assertReconciliationFactPayloadText(
    reconciliationChain,
    "refund-center",
    "REFUND_SUCCESS",
    refundOutbox.eventId,
    `${prepay.paymentOrderId}，支付场景=${expectedPayScene}`
  );
  assertReconciliationFactPayScene(
    reconciliationChain,
    "refund-center",
    "REFUND_SUCCESS",
    refundOutbox.eventId,
    expectedPayScene
  );
  assert(findReconciliationFacts(reconciliationChain.facts, "refund-center", "REFUND_SUCCESS", refundOutbox.eventId).length === 1,
  "reconciliation REFUND_SUCCESS fact was not recorded idempotently");
  assert(reconciliationChain.expectedStageCount === 4,
    `reconciliation expected stage count was ${reconciliationChain.expectedStageCount}, expected 4`);
  assert(reconciliationChain.completedStageCount === reconciliationChain.expectedStageCount,
    `reconciliation completed stage count was ${reconciliationChain.completedStageCount}`);
  assert(reconciliationChain.chainStatus === "COMPLETED",
    `reconciliation chain was not COMPLETED: ${reconciliationChain.chainStatus}`);
  assert(reconciliationChain.responsibilityStatus === "CLOSED",
    `reconciliation responsibility was not CLOSED: ${reconciliationChain.responsibilityStatus}`);
  assert(reconciliationChain.financialCloseable === true,
    "reconciliation chain was not financial-closeable");
  assertReconciliationLastFactTime(reconciliationChain);

  console.log(queryMode ? "[PASS] cross-system local query happy path" : "[PASS] cross-system local happy path");
  console.log(JSON.stringify({
    orderNo,
    paymentOrderId: prepay.paymentOrderId,
    paymentCompletion: queryMode ? "GATEWAY_QUERY" : "CALLBACK",
    paymentEventNo: paymentEvent.eventNo,
    clearingNo: clearingOrder.clearingNo,
    settlementNo: settlementOrder.settlementNo,
    refundOrderId: refund.refundOrderId,
    refundEventId: refundOutbox.eventId,
    accountingEventTypes: ["PAYMENT_SUCCESS", "CLEARING_GENERATED", "REFUND_SUCCESS"],
    reconciliationFactTypes: requiredReconciliationFacts.map(([, factType]) => factType),
    reconciliationChainStatus: reconciliationChain.chainStatus,
    reconciliationCompletedStageCount: reconciliationChain.completedStageCount,
    reconciliationExpectedStageCount: reconciliationChain.expectedStageCount,
    reconciliationFinancialCloseable: reconciliationChain.financialCloseable
  }, null, 2));
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(`[FAIL] ${error.message}`);
    process.exitCode = 1;
  });
}
