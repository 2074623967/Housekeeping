function createBusinessError(payload, fallbackMessage) {
  const message = payload?.message || fallbackMessage || "Request failed";
  const displayMessage = `${message} [${payload?.code || "PAYMENT-CLIENT"}${payload?.requestId ? ` / ${payload.requestId}` : ""}]`;
  const error = new Error(displayMessage);
  error.code = payload?.code || "PAYMENT-CLIENT";
  error.requestId = payload?.requestId || "";
  error.rawMessage = message;
  error.displayMessage = displayMessage;
  return error;
}

const CLIENT_TIMEOUT_MS = 10000;
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

function isKnownStatus(value, statuses) {
  return typeof value === "string" && statuses.has(value.trim().toUpperCase());
}

function isConsistentPaymentStatus(status, statusType, latestAttemptStatus) {
  if (typeof status !== "string" || !status.trim()
      || typeof statusType !== "string" || !statusType.trim()) {
    return false;
  }
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
  }[status.trim().toUpperCase()];
  if (["WAIT_CALLBACK", "PAYING"].includes(status.trim().toUpperCase())
      && statusType.trim().toLowerCase() === "danger"
      && latestAttemptStatus === "渠道流水归属冲突，待人工核查") {
    return true;
  }
  return Boolean(expectedType) && statusType.trim().toLowerCase() === expectedType;
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
  if (["成功", "支付成功", "SUCCESS"].includes(normalizedStatus)) {
    return normalizedType === "success";
  }
  if (["失败", "支付失败", "FAIL", "FAILED"].includes(normalizedStatus)) {
    return normalizedType === "danger";
  }
  return PAYMENT_STATUS_TYPES.has(normalizedType);
}

function isConsistentSuccessfulPaymentAttempt(
  paymentStatus, attemptStatus, attemptStatusType, attemptCompletedAt
) {
  const normalizedStatus = typeof paymentStatus === "string" ? paymentStatus.trim().toUpperCase() : "";
  const normalizedAttemptStatus = typeof attemptStatus === "string" ? attemptStatus.trim().toUpperCase() : "";
  if (PENDING_PAYMENT_STATUSES.has(normalizedStatus)
      && TERMINAL_PAYMENT_ATTEMPT_STATUSES.has(normalizedAttemptStatus)) {
    return false;
  }
  if (normalizedStatus !== "SUCCESS") {
    return !["成功", "支付成功", "SUCCESS"].includes(normalizedAttemptStatus);
  }
  return attemptStatus === "成功"
    && attemptStatusType === "success"
    && isValidExpiryTime(attemptCompletedAt)
    && attemptCompletedAt.trim() !== "-";
}

function isConsistentPaymentSuccessTime(status, successAt) {
  if (typeof status !== "string") {
    return false;
  }
  if (status.trim().toUpperCase() === "SUCCESS") {
    return isValidExpiryTime(successAt) && successAt.trim() !== "-";
  }
  return successAt == null || successAt === "-";
}

function isConsistentPaymentChannelTransaction(status, channelTransactionNo) {
  if (typeof status !== "string") {
    return false;
  }
  if (status.trim().toUpperCase() !== "SUCCESS") {
    return channelTransactionNo == null || typeof channelTransactionNo === "string";
  }
  return typeof channelTransactionNo === "string" && Boolean(channelTransactionNo.trim());
}

function isConsistentPaymentChannel(status, channel) {
  if (typeof status !== "string") {
    return false;
  }
  if (status.trim().toUpperCase() !== "SUCCESS") {
    return channel == null || typeof channel === "string";
  }
  return typeof channel === "string" && Boolean(channel.trim());
}

function isConsistentPaymentAttemptCompletedAt(status, completedAt, createdAt) {
  if (status == null) {
    return completedAt == null;
  }
  if (typeof status !== "string") {
    return false;
  }
  if (status.trim() === "成功") {
    return isValidExpiryTime(completedAt)
      && completedAt.trim() !== "-"
      && isValidExpiryTime(createdAt)
      && wallClockTimestamp(createdAt)
        <= wallClockTimestamp(completedAt);
  }
  return completedAt == null;
}

function isConsistentCashierAttemptFacts(paymentStatus, status, statusType, completedAt) {
  if (!isConsistentSuccessfulPaymentAttempt(paymentStatus, status, statusType, completedAt)) {
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
  if (status.trim() === "成功") {
    return statusType.trim().toLowerCase() === "success"
      && isValidExpiryTime(completedAt)
      && completedAt.trim() !== "-";
  }
  return completedAt == null;
}

function isConsistentPaymentCashierStatus(paymentStatus, cashierStatus, latestAttemptStatus) {
  if (typeof paymentStatus !== "string" || typeof cashierStatus !== "string") {
    return false;
  }
  if (paymentStatus.trim().toUpperCase() === "PAYING" && cashierStatus.trim() === "结果待确认") {
    return latestAttemptStatus === "渠道流水归属冲突，待人工核查";
  }
  const expectedCashierStatuses = PAYMENT_CASHIER_STATUSES.get(
    paymentStatus.trim().toUpperCase()
  );
  return !expectedCashierStatuses || expectedCashierStatuses.has(cashierStatus.trim());
}

function isConsistentCashierChannels(channels) {
  if (!Array.isArray(channels)) {
    return false;
  }
  const seen = new Set();
  return channels.every((channel) => typeof channel === "string"
    && CASHIER_CHANNELS.has(channel)
    && !seen.has(channel)
    && seen.add(channel));
}

function isConsistentRefundStatus(status, statusType) {
  if (typeof status !== "string" || !status.trim()
      || typeof statusType !== "string" || !statusType.trim()) {
    return false;
  }
  const expectedType = {
    REVIEWING: "warn",
    PROCESSING: "warn",
    SUCCESS: "success",
    FAIL: "danger"
  }[status.trim().toUpperCase()];
  return Boolean(expectedType) && statusType.trim().toLowerCase() === expectedType;
}

function isConsistentRefundSuccessTime(status, successAt) {
  if (typeof status !== "string") {
    return false;
  }
  if (status.trim().toUpperCase() !== "SUCCESS") {
    return successAt == null || successAt === "-";
  }
  return isValidExpiryTime(successAt) && successAt.trim() !== "-";
}

function isConsistentRefundChannelRefundNo(status, channelRefundNo) {
  if (typeof status !== "string") {
    return false;
  }
  if (!["SUCCESS", "FAIL"].includes(status.trim().toUpperCase())) {
    return channelRefundNo == null || typeof channelRefundNo === "string";
  }
  return typeof channelRefundNo === "string" && Boolean(channelRefundNo.trim());
}

function isConsistentRefundChannelCode(status, channelCode) {
  if (typeof status !== "string") {
    return false;
  }
  if (["SUCCESS", "FAIL"].includes(status.trim().toUpperCase())) {
    return typeof channelCode === "string" && Boolean(channelCode.trim());
  }
  return channelCode == null || typeof channelCode === "string";
}

function isConsistentPaymentLifecycleTimeOrder(createdAt, successAt) {
  if (!isValidExpiryTime(createdAt) || !isValidExpiryTime(successAt)) {
    return true;
  }
  return wallClockTimestamp(createdAt)
    <= wallClockTimestamp(successAt);
}

function isConsistentPaymentAttemptSuccessTime(attemptStatus, completedAt, successAt) {
  if (typeof attemptStatus !== "string" || attemptStatus.trim() !== "成功") {
    return true;
  }
  if (!isValidExpiryTime(completedAt) || !isValidExpiryTime(successAt)) {
    return true;
  }
  return wallClockTimestamp(completedAt)
    <= wallClockTimestamp(successAt);
}

function isConsistentPrepayExpiryTimeOrder(createdAt, expiresAt) {
  if (!isValidExpiryTime(createdAt) || !isValidExpiryTime(expiresAt)) {
    return true;
  }
  return wallClockTimestamp(createdAt)
    <= wallClockTimestamp(expiresAt);
}

function isConsistentRefundTerminalTime(status, terminalAt) {
  if (typeof status !== "string") {
    return false;
  }
  const terminal = ["SUCCESS", "FAIL"].includes(status.trim().toUpperCase());
  if (!terminal) {
    return terminalAt == null || terminalAt === "-";
  }
  return isValidExpiryTime(terminalAt) && terminalAt.trim() !== "-";
}

function isConsistentRefundLifecycleTimeOrder(appliedAt, successAt, terminalAt) {
  const appliedTimestamp = wallClockTimestamp(appliedAt);
  const successTimestamp = isValidExpiryTime(successAt)
    ? wallClockTimestamp(successAt)
    : null;
  const terminalTimestamp = isValidExpiryTime(terminalAt)
    ? wallClockTimestamp(terminalAt)
    : null;
  return (successTimestamp == null || appliedTimestamp <= successTimestamp)
    && (terminalTimestamp == null || appliedTimestamp <= terminalTimestamp)
    && (successTimestamp == null || terminalTimestamp == null || successTimestamp <= terminalTimestamp);
}

function isConsistentRefundChannelSubmittedAt(detail) {
  const submittedAt = detail.channelSubmittedAt;
  const terminal = typeof detail.status === "string"
    && ["SUCCESS", "FAIL"].includes(detail.status.trim().toUpperCase());
  if (submittedAt == null || submittedAt === "-") {
    return !terminal;
  }
  if (!isValidExpiryTime(submittedAt)) {
    return false;
  }
  const appliedTimestamp = wallClockTimestamp(detail.appliedAt);
  const submittedTimestamp = wallClockTimestamp(submittedAt);
  if (submittedTimestamp < appliedTimestamp) {
    return false;
  }
  if (isValidExpiryTime(detail.terminalAt)) {
    return submittedTimestamp <= wallClockTimestamp(detail.terminalAt);
  }
  return true;
}

function wallClockTimestamp(value) {
  return Date.parse(`${value.trim().replace(" ", "T")}Z`);
}

function isValidExpiryTime(value) {
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

function isValidAmount(value) {
  if (typeof value !== "string" || !value.trim()) {
    return false;
  }
  const rawValue = value.trim().replace(/^¥\s*/, "");
  const amountParts = rawValue.split(".");
  if (amountParts.length > 2) {
    return false;
  }
  const [integerPart, fractionPart] = amountParts;
  if (fractionPart !== undefined && !/^\d{1,2}$/.test(fractionPart)) {
    return false;
  }
  if (!integerPart) {
    return false;
  }
  const integerDigits = integerPart.replace(/,/g, "");
  const validInteger = integerPart
    ? (integerPart.includes(",")
      ? /^\d{1,3}(?:,\d{3})+$/.test(integerPart)
      : /^\d+$/.test(integerPart))
    : true;
  if (!validInteger || integerDigits.length > 16) {
    return false;
  }
  const normalizedInteger = integerDigits.replace(/^0+/, "") || "0";
  const normalizedFraction = (fractionPart || "").padEnd(2, "0");
  return normalizedInteger !== "0" || normalizedFraction !== "00";
}

function amountsMatch(left, right) {
  const normalizedLeft = normalizeAmountForComparison(left);
  const normalizedRight = normalizeAmountForComparison(right);
  return normalizedLeft != null && normalizedLeft === normalizedRight;
}

function normalizeAmountForComparison(value) {
  if (!isValidAmount(value)) {
    return null;
  }
  const [integerPart, fractionPart] = String(value).trim()
    .replace(/^¥\s*/, "")
    .replace(/,/g, "")
    .split(".");
  const normalizedInteger = integerPart.replace(/^0+/, "") || "0";
  return `${normalizedInteger}${(fractionPart || "").padEnd(2, "0")}`;
}

function isAmountNotGreater(left, right) {
  const normalizedLeft = normalizeAmountForComparison(left);
  const normalizedRight = normalizeAmountForComparison(right);
  if (normalizedLeft == null || normalizedRight == null) {
    return false;
  }
  if (normalizedLeft.length !== normalizedRight.length) {
    return normalizedLeft.length < normalizedRight.length;
  }
  return normalizedLeft <= normalizedRight;
}

async function parseResponsePayload(response) {
  try {
    return await response.json();
  } catch (error) {
    const parseError = new Error("支付服务返回了无法解析的响应");
    parseError.code = "PAYMENT-CLIENT-INVALID-RESPONSE";
    parseError.status = response.status;
    parseError.cause = error;
    throw parseError;
  }
}

async function request(url, options = {}) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), CLIENT_TIMEOUT_MS);
  try {
    let response;
    try {
      response = await fetch(url, {
        headers: { "Content-Type": "application/json" },
        ...options,
        signal: controller.signal
      });
    } catch (error) {
      const transportCode = error?.name === "AbortError"
        ? "PAYMENT-CLIENT-TIMEOUT"
        : "PAYMENT-CLIENT-NETWORK";
      throw createBusinessError({ code: transportCode }, error?.name === "AbortError" ? "支付服务请求超时" : "无法连接支付服务");
    }
    let payload;
    try {
      payload = await parseResponsePayload(response);
    } catch (error) {
      throw createBusinessError({ code: error.code }, `${error.message} [HTTP ${error.status}]`);
    }
    if (!response.ok) {
      throw createBusinessError(payload, `Request failed: ${response.status}`);
    }
    if (!payload || payload.code !== "0") {
      throw createBusinessError(payload, "Request failed");
    }
    return payload.data;
  } finally {
    clearTimeout(timeoutId);
  }
}

function buildJsonRequestOptions(method, payload) {
  return {
    method,
    body: JSON.stringify(payload)
  };
}

function postJson(url, payload) {
  return request(url, buildJsonRequestOptions("POST", payload));
}

function normalizePayloadIdentifier(payload, fieldName) {
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
    return payload;
  }
  const value = payload[fieldName];
  if (typeof value !== "string") {
    return payload;
  }
  return { ...payload, [fieldName]: value.trim() };
}

function matchesExpectedIdentifier(actual, expected) {
  return !expected || (typeof actual === "string" && actual.trim() === expected);
}

export const paymentApi = {
  prepay: (payload) => {
    const normalizedPayload = normalizePayloadIdentifier(payload, "orderNo");
    const normalizedScenePayload = normalizePayloadIdentifier(normalizedPayload, "payScene");
    const normalizedCustomerPayload = normalizePayloadIdentifier(normalizedScenePayload, "customerName");
    return postJson("/api/payments/prepay", normalizedCustomerPayload)
      .then((data) => normalizePrepayResult(
        data,
        normalizedCustomerPayload?.orderNo,
        normalizedCustomerPayload?.amount,
        normalizedCustomerPayload?.customerName,
        normalizedCustomerPayload?.payScene
      ));
  },
  getCashier: (prepayOrderNo, terminal, expectedPayScene = null) => {
    const normalizedPrepayOrderNo = typeof prepayOrderNo === "string"
      ? prepayOrderNo.trim()
      : prepayOrderNo;
    const normalizedTerminal = typeof terminal === "string"
      ? terminal.trim()
      : terminal;
    return request(`/api/payments/cashier/${encodeURIComponent(normalizedPrepayOrderNo)}${normalizedTerminal ? `?terminal=${encodeURIComponent(normalizedTerminal)}` : ""}`)
      .then((data) => normalizeCashierPageResult(
        data, normalizedPrepayOrderNo, expectedPayScene
      ));
  },
  getDetail: (paymentOrderId, expectedPayScene = null) => {
    const normalizedPaymentOrderId = typeof paymentOrderId === "string"
      ? paymentOrderId.trim()
      : paymentOrderId;
    return request(`/api/payments/${encodeURIComponent(normalizedPaymentOrderId)}`)
      .then((data) => normalizePaymentDetailResult(
        data, normalizedPaymentOrderId, expectedPayScene
      ));
  },
  getRefundDetail: (refundOrderId, expectedPayScene = null) => {
    const normalizedRefundOrderId = typeof refundOrderId === "string"
      ? refundOrderId.trim()
      : refundOrderId;
    const normalizedPayScene = typeof expectedPayScene === "string"
      ? expectedPayScene.trim()
      : expectedPayScene;
    return request(`/api/refunds/${encodeURIComponent(normalizedRefundOrderId)}`)
      .then((data) => normalizeRefundDetailResult(
        data, normalizedRefundOrderId, normalizedPayScene
      ));
  },
  submit: (payload, expectedPayScene = null) => {
    const normalizedPayload = normalizePayloadIdentifier(
      normalizePayloadIdentifier(
        normalizePayloadIdentifier(payload, "prepayOrderNo"),
        "paymentMethod"
      ),
      "channelCode"
    );
    return postJson("/api/payments/submit", normalizedPayload)
      .then((data) => normalizePaymentSubmitResult(
        data,
        normalizedPayload?.prepayOrderNo,
        normalizedPayload?.paymentMethod,
        normalizedPayload?.channelCode,
        expectedPayScene
      ));
  },
  query: (payload, expectedSnapshot = null, expectedPayScene = null) => {
    const normalizedPayload = normalizePayloadIdentifier(payload, "paymentOrderId");
    return postJson("/api/payments/query", normalizedPayload)
      .then((data) => normalizePaymentActionResult(
        data, normalizedPayload?.paymentOrderId, expectedSnapshot, expectedPayScene
      ));
  },
  close: (payload, expectedSnapshot = null, expectedPayScene = null) => {
    const normalizedPayload = normalizePayloadIdentifier(payload, "paymentOrderId");
    return postJson("/api/payments/close", normalizedPayload)
      .then((data) => normalizePaymentActionResult(
        data, normalizedPayload?.paymentOrderId, expectedSnapshot, expectedPayScene
      ));
  }
};

export function normalizePrepayResult(
  data, expectedOrderNo, expectedAmount, expectedCustomerName, expectedPayScene = null
) {
  if (!data || typeof data !== "object" || Array.isArray(data)
      || typeof data.prepayOrderNo !== "string" || !data.prepayOrderNo.trim()
      || typeof data.paymentOrderId !== "string" || !data.paymentOrderId.trim()
      || typeof data.businessChainId !== "string"
      || data.businessChainId.trim() !== `CHAIN-${data.paymentOrderId.trim()}`
      || typeof data.orderNo !== "string" || !data.orderNo.trim()
      || !isValidAmount(data.amount)
      || !isKnownStatus(data.paymentStatus, PAYMENT_STATUSES)
      || !isConsistentPaymentSuccessTime(data.paymentStatus, data.successAt)
      || !isConsistentPaymentChannelTransaction(data.paymentStatus, data.channelTransactionNo)
      || !isConsistentPaymentAttemptStatus(data.latestAttemptStatus, data.latestAttemptStatusType)
      || !isConsistentSuccessfulPaymentAttempt(
        data.paymentStatus,
        data.latestAttemptStatus,
        data.latestAttemptStatusType,
        data.latestAttemptCompletedAt
      )
      || !isConsistentPaymentAttemptCompletedAt(
        data.latestAttemptStatus, data.latestAttemptCompletedAt, data.createdAt
      )
      || !isConsistentPaymentAttemptSuccessTime(
        data.latestAttemptStatus, data.latestAttemptCompletedAt, data.successAt
      )
      || !isValidExpiryTime(data.createdAt)
      || !isConsistentPaymentLifecycleTimeOrder(data.createdAt, data.successAt)
      || !isValidExpiryTime(data.expiresAt)
      || !isConsistentPrepayExpiryTimeOrder(data.createdAt, data.expiresAt)
      || (expectedPayScene != null && !matchesExpectedIdentifier(data.payScene, expectedPayScene))
      || typeof data.cashierStatus !== "string"
      || CASHIER_STATUS_TYPES.get(data.cashierStatus.trim()) !== data.cashierStatusType
      || !isConsistentPaymentCashierStatus(data.paymentStatus, data.cashierStatus, data.latestAttemptStatus)
      || !matchesExpectedIdentifier(data.orderNo, expectedOrderNo)
      || !matchesExpectedIdentifier(data.customerName, expectedCustomerName)
      || (expectedAmount != null && !amountsMatch(data.amount, expectedAmount))) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-PREPAY" },
      "预付单响应为空或缺少订单金额事实"
    );
  }
  return data;
}

export function normalizePaymentSubmitResult(
  data, expectedPrepayOrderNo, expectedPaymentMethod, expectedChannelCode, expectedPayScene = null
) {
  if (!data || typeof data !== "object" || Array.isArray(data)
      || typeof data.prepayOrderNo !== "string" || !data.prepayOrderNo.trim()
      || typeof data.paymentOrderId !== "string" || !data.paymentOrderId.trim()
      || typeof data.businessChainId !== "string"
      || data.businessChainId.trim() !== `CHAIN-${data.paymentOrderId.trim()}`
      || typeof data.orderNo !== "string" || !data.orderNo.trim()
      || !isValidAmount(data.amount)
      || !isKnownStatus(data.paymentStatus, PAYMENT_STATUSES)
      || ["PREPAY_CREATED", "PAYING"].includes(data.paymentStatus.trim().toUpperCase())
      || !isConsistentPaymentSuccessTime(data.paymentStatus, data.successAt)
      || !isConsistentPaymentChannel(data.paymentStatus, data.channel)
      || !isConsistentPaymentChannelTransaction(data.paymentStatus, data.channelTransactionNo)
      || !isConsistentPaymentAttemptStatus(data.latestAttemptStatus, data.latestAttemptStatusType)
      || !isConsistentSuccessfulPaymentAttempt(
        data.paymentStatus,
        data.latestAttemptStatus,
        data.latestAttemptStatusType,
        data.latestAttemptCompletedAt
      )
      || !isConsistentPaymentAttemptCompletedAt(
        data.latestAttemptStatus, data.latestAttemptCompletedAt, data.createdAt
      )
      || !isConsistentPaymentAttemptSuccessTime(
        data.latestAttemptStatus, data.latestAttemptCompletedAt, data.successAt
      )
      || !isValidExpiryTime(data.createdAt)
      || !isConsistentPaymentLifecycleTimeOrder(data.createdAt, data.successAt)
      || !isValidExpiryTime(data.expiresAt)
      || !isConsistentPrepayExpiryTimeOrder(data.createdAt, data.expiresAt)
      || typeof data.cashierStatus !== "string"
      || CASHIER_STATUS_TYPES.get(data.cashierStatus.trim()) !== data.cashierStatusType
      || !isConsistentPaymentCashierStatus(data.paymentStatus, data.cashierStatus, data.latestAttemptStatus)
      || (expectedPayScene != null && !matchesExpectedIdentifier(data.payScene, expectedPayScene))
      || !matchesExpectedIdentifier(data.prepayOrderNo, expectedPrepayOrderNo)
      || !matchesExpectedIdentifier(data.paymentMethod, expectedPaymentMethod)
      || !matchesExpectedIdentifier(data.channel, expectedChannelCode)) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-PAYMENT-SUBMIT" },
      "支付提交响应为空、状态未推进或缺少可信订单事实"
    );
  }
  return data;
}

export function normalizePaymentActionResult(
  data, expectedPaymentOrderId, expectedSnapshot = null, expectedPayScene = null
) {
  if (!data || typeof data !== "object" || Array.isArray(data)
      || typeof data.prepayOrderNo !== "string" || !data.prepayOrderNo.trim()
      || typeof data.paymentOrderId !== "string" || !data.paymentOrderId.trim()
      || typeof data.businessChainId !== "string"
      || data.businessChainId.trim() !== `CHAIN-${data.paymentOrderId.trim()}`
      || typeof data.orderNo !== "string" || !data.orderNo.trim()
      || !isValidAmount(data.amount)
      || typeof data.status !== "string" || !data.status.trim()
      || !isKnownStatus(data.status, PAYMENT_STATUSES)
      || !isConsistentPaymentStatus(data.status, data.statusType, data.latestAttemptStatus)
      || !isConsistentPaymentSuccessTime(data.status, data.successAt)
      || !isConsistentPaymentChannel(data.status, data.channel)
      || !isConsistentPaymentChannelTransaction(data.status, data.channelTransactionNo)
      || !isValidExpiryTime(data.createdAt)
      || !isConsistentPaymentLifecycleTimeOrder(data.createdAt, data.successAt)
      || !isConsistentPaymentAttemptStatus(data.latestAttemptStatus, data.latestAttemptStatusType)
      || !isConsistentSuccessfulPaymentAttempt(
        data.status,
        data.latestAttemptStatus,
        data.latestAttemptStatusType,
        data.latestAttemptCompletedAt
      )
      || !isConsistentPaymentAttemptCompletedAt(
        data.latestAttemptStatus, data.latestAttemptCompletedAt, data.createdAt
      )
      || !isConsistentPaymentAttemptSuccessTime(
        data.latestAttemptStatus, data.latestAttemptCompletedAt, data.successAt
      )
      || (expectedPayScene != null && !matchesExpectedIdentifier(data.payScene, expectedPayScene))
      || !matchesExpectedIdentifier(data.paymentOrderId, expectedPaymentOrderId)
      || (expectedSnapshot?.prepayOrderNo != null
        && !matchesExpectedIdentifier(data.prepayOrderNo, expectedSnapshot.prepayOrderNo))
      || (expectedSnapshot?.orderNo != null
        && !matchesExpectedIdentifier(data.orderNo, expectedSnapshot.orderNo))
      || (expectedSnapshot?.amount != null
        && !amountsMatch(data.amount, expectedSnapshot.amount))
      || (expectedSnapshot?.channel != null
        && !matchesExpectedIdentifier(data.channel, expectedSnapshot.channel))
      || (expectedSnapshot?.channelTransactionNo != null
        && !matchesExpectedIdentifier(data.channelTransactionNo, expectedSnapshot.channelTransactionNo))
      || (expectedSnapshot?.paymentMethod != null
        && !matchesExpectedIdentifier(data.paymentMethod, expectedSnapshot.paymentMethod))) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION" },
      "支付操作响应为空或缺少状态事实"
    );
  }
  return data;
}

function normalizeList(value) {
  return Array.isArray(value) ? value.filter((item) => item != null) : [];
}

function isOptionalCollection(value) {
  return value == null || Array.isArray(value);
}

function isOptionalStringCollection(value) {
  return value == null || (Array.isArray(value)
    && value.every((item) => item == null || typeof item === "string"));
}

function normalizeCashierPage(page) {
  if (!page || typeof page !== "object" || Array.isArray(page)) {
    return page;
  }
  return { ...page, channels: normalizeList(page.channels) };
}

export function normalizeCashierPageResult(page, expectedPrepayOrderNo, expectedPayScene = null) {
  if (!page || typeof page !== "object" || Array.isArray(page)
      || typeof page.prepayOrderNo !== "string" || !page.prepayOrderNo.trim()
      || typeof page.paymentOrderId !== "string" || !page.paymentOrderId.trim()
      || typeof page.businessChainId !== "string"
      || page.businessChainId.trim() !== `CHAIN-${page.paymentOrderId.trim()}`
      || typeof page.orderNo !== "string" || !page.orderNo.trim()
      || !isValidAmount(page.amount)
      || !isConsistentCashierChannels(page.channels)
      || (expectedPayScene != null && !matchesExpectedIdentifier(page.payScene, expectedPayScene))
      || typeof page.status !== "string" || !CASHIER_STATUS_TYPES.has(page.status.trim())
      || page.statusType !== CASHIER_STATUS_TYPES.get(page.status.trim())
      || typeof page.paymentStatus !== "string" || !isKnownStatus(page.paymentStatus, PAYMENT_STATUSES)
      || !isConsistentPaymentCashierStatus(page.paymentStatus, page.status, page.latestAttemptStatus)
      || (["WAIT_CALLBACK", "SUCCESS"].includes(page.paymentStatus)
        && (typeof page.channel !== "string" || !page.channel.trim()))
      || (["WAIT_CALLBACK", "SUCCESS"].includes(page.paymentStatus)
        && (typeof page.paymentMethod !== "string" || !page.paymentMethod.trim()))
      || (page.paymentStatus === "SUCCESS"
        && (typeof page.channelTransactionNo !== "string" || !page.channelTransactionNo.trim()))
      || !isValidExpiryTime(page.expiresAt)
      || !isConsistentCashierAttemptFacts(
        page.paymentStatus,
        page.latestAttemptStatus,
        page.latestAttemptStatusType,
        page.latestAttemptCompletedAt
      )
      || !matchesExpectedIdentifier(page.prepayOrderNo, expectedPrepayOrderNo)) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-CASHIER" },
      "收银台响应为空或缺少订单金额事实"
    );
  }
  return normalizeCashierPage(page);
}

function normalizePaymentDetail(detail) {
  if (!detail || typeof detail !== "object" || Array.isArray(detail)) {
    return detail;
  }
  return {
    ...detail,
    routeLogs: normalizeList(detail.routeLogs),
    notifyLogs: normalizeList(detail.notifyLogs),
    eventLogs: normalizeList(detail.eventLogs)
  };
}

export function normalizePaymentDetailResult(
  detail, expectedPaymentOrderId, expectedPayScene = null
) {
  if (!detail || typeof detail !== "object" || Array.isArray(detail)
      || typeof detail.paymentOrderId !== "string" || !detail.paymentOrderId.trim()
      || typeof detail.businessChainId !== "string"
      || detail.businessChainId.trim() !== `CHAIN-${detail.paymentOrderId.trim()}`
      || typeof detail.prepayOrderNo !== "string" || !detail.prepayOrderNo.trim()
      || typeof detail.orderNo !== "string" || !detail.orderNo.trim()
      || !isValidAmount(detail.amount)
      || typeof detail.status !== "string" || !detail.status.trim()
      || !isKnownStatus(detail.status, PAYMENT_STATUSES)
      || !isConsistentPaymentStatus(detail.status, detail.statusType, detail.latestAttemptStatus)
      || !isConsistentPaymentSuccessTime(detail.status, detail.successAt)
      || !isConsistentPaymentChannel(detail.status, detail.channel)
      || !isConsistentPaymentChannelTransaction(detail.status, detail.channelTransactionNo)
      || !isValidExpiryTime(detail.createdAt)
      || !isConsistentPaymentLifecycleTimeOrder(detail.createdAt, detail.successAt)
      || !isConsistentPaymentAttemptStatus(detail.latestAttemptStatus, detail.latestAttemptStatusType)
      || !isConsistentSuccessfulPaymentAttempt(
        detail.status,
        detail.latestAttemptStatus,
        detail.latestAttemptStatusType,
        detail.latestAttemptCompletedAt
      )
      || !isConsistentPaymentAttemptCompletedAt(
        detail.latestAttemptStatus, detail.latestAttemptCompletedAt, detail.createdAt
      )
      || !isConsistentPaymentAttemptSuccessTime(
        detail.latestAttemptStatus, detail.latestAttemptCompletedAt, detail.successAt
      )
      || !isOptionalStringCollection(detail.routeLogs)
      || !isOptionalStringCollection(detail.notifyLogs)
      || !isOptionalStringCollection(detail.eventLogs)
      || (expectedPayScene != null && !matchesExpectedIdentifier(detail.payScene, expectedPayScene))
      || !matchesExpectedIdentifier(detail.paymentOrderId, expectedPaymentOrderId)) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-PAYMENT-DETAIL" },
      "支付详情响应为空或缺少业务事实"
    );
  }
  return normalizePaymentDetail(detail);
}

function normalizeRefundDetail(detail) {
  if (!detail || typeof detail !== "object" || Array.isArray(detail)) {
    return detail;
  }
  const attempts = normalizeList(detail.attempts);
  const operationLogs = normalizeList(detail.operationLogs);
  if (!isConsistentRefundAttempts(
    attempts, detail.appliedAt, detail.successAt, detail.terminalAt
  ) || !isConsistentRefundTerminalAttempt(detail, attempts)) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-REFUND-DETAIL" },
      "退款详情响应包含无法确认的退款尝试或渠道归属事实"
    );
  }
  if (!isConsistentRefundOperationLogs(
    operationLogs, detail.appliedAt, detail.successAt, detail.terminalAt
  )) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-REFUND-DETAIL" },
      "退款详情响应包含无法确认的退款操作日志事实"
    );
  }
  return {
    ...detail,
    attempts,
    operationLogs
  };
}

function isConsistentRefundOperationLog(log, appliedAt) {
  return Boolean(log && typeof log === "object" && !Array.isArray(log)
    && typeof log.logNo === "string"
    && log.logNo.trim()
    && (typeof log.actionName === "string" && log.actionName.trim()
      || typeof log.actionCode === "string" && log.actionCode.trim())
    && isValidExpiryTime(log.createdAt)
    && isValidExpiryTime(appliedAt)
    && wallClockTimestamp(appliedAt)
      <= wallClockTimestamp(log.createdAt));
}

function isConsistentRefundOperationLogs(operationLogs, appliedAt, successAt, terminalAt) {
  let previousTimestamp = null;
  const terminalTimestamp = isValidExpiryTime(terminalAt)
    ? wallClockTimestamp(terminalAt)
    : (isValidExpiryTime(successAt)
      ? wallClockTimestamp(successAt)
      : null);
  for (const log of operationLogs) {
    if (!isConsistentRefundOperationLog(log, appliedAt)) {
      return false;
    }
    const createdTimestamp = wallClockTimestamp(log.createdAt);
    if ((previousTimestamp != null && createdTimestamp > previousTimestamp)
        || (terminalTimestamp != null && createdTimestamp > terminalTimestamp)) {
      return false;
    }
    previousTimestamp = createdTimestamp;
  }
  return true;
}

function isConsistentRefundAttemptTimeline(attempt, appliedAt, successAt, terminalAt) {
  if (!attempt || typeof attempt !== "object" || Array.isArray(attempt)
      || typeof attempt.refundAttemptNo !== "string"
      || !attempt.refundAttemptNo.trim()
      || typeof attempt.attemptStatus !== "string"
      || !REFUND_ATTEMPT_STATUS_TYPES.has(attempt.attemptStatus.trim().toUpperCase())
      || typeof attempt.attemptStatusType !== "string"
      || attempt.attemptStatusType.trim().toLowerCase()
        !== REFUND_ATTEMPT_STATUS_TYPES.get(attempt.attemptStatus.trim().toUpperCase())
      || !isValidExpiryTime(attempt.createdAt)) {
    return false;
  }
  const submittedAt = attempt.submittedAt;
  const completedAt = attempt.completedAt;
  const hasSubmittedAt = submittedAt !== "-" && submittedAt != null;
  const hasCompletedAt = completedAt !== "-" && completedAt != null;
  if ((hasSubmittedAt && !isValidExpiryTime(submittedAt))
      || (hasCompletedAt && !isValidExpiryTime(completedAt))) {
    return false;
  }
  const createdTimestamp = wallClockTimestamp(attempt.createdAt);
  const appliedTimestamp = isValidExpiryTime(appliedAt)
    ? wallClockTimestamp(appliedAt)
    : null;
  const submittedTimestamp = hasSubmittedAt
    ? wallClockTimestamp(submittedAt)
    : createdTimestamp;
  const terminalStatus = ["SUCCESS", "FAIL"].includes(attempt.attemptStatus.trim().toUpperCase());
  const terminalTimestamp = isValidExpiryTime(terminalAt)
    ? wallClockTimestamp(terminalAt)
    : (isValidExpiryTime(successAt)
      ? wallClockTimestamp(successAt)
      : null);
  const completedTimestamp = hasCompletedAt
    ? wallClockTimestamp(completedAt)
    : null;
  return (appliedTimestamp == null || appliedTimestamp <= createdTimestamp)
    && (!hasSubmittedAt || submittedTimestamp >= createdTimestamp)
    && (terminalStatus ? hasCompletedAt : !hasCompletedAt)
    && (!hasCompletedAt
      || completedTimestamp >= submittedTimestamp)
    && (terminalTimestamp == null
      || (createdTimestamp <= terminalTimestamp
        && submittedTimestamp <= terminalTimestamp
        && (!hasCompletedAt || completedTimestamp <= terminalTimestamp)));
}

function isConsistentRefundAttempts(attempts, appliedAt, successAt, terminalAt) {
  let previousCreatedTimestamp = null;
  for (const attempt of attempts) {
    if (!isConsistentRefundAttemptTimeline(attempt, appliedAt, successAt, terminalAt)) {
      return false;
    }
    const createdTimestamp = wallClockTimestamp(attempt.createdAt);
    if (previousCreatedTimestamp != null && createdTimestamp > previousCreatedTimestamp) {
      return false;
    }
    previousCreatedTimestamp = createdTimestamp;
  }
  return true;
}

function isConsistentRefundTerminalAttempt(detail, attempts) {
  const status = detail.status?.trim().toUpperCase();
  if (!["SUCCESS", "FAIL"].includes(status) || attempts.length === 0) {
    return true;
  }
  const latestAttempt = attempts[0];
  return latestAttempt.attemptStatus.trim().toUpperCase() === status
    && typeof latestAttempt.channelCode === "string"
    && latestAttempt.channelCode.trim() === detail.channelCode.trim()
    && typeof latestAttempt.channelRefundNo === "string"
    && latestAttempt.channelRefundNo.trim() === detail.channelRefundNo.trim()
    && (status !== "SUCCESS"
      || wallClockTimestamp(latestAttempt.completedAt) <= wallClockTimestamp(detail.successAt));
}

export function normalizeRefundDetailResult(detail, expectedRefundOrderId, expectedPayScene = null) {
  const normalizedExpectedPayScene = typeof expectedPayScene === "string"
    ? expectedPayScene.trim()
    : expectedPayScene;
  if (!detail || typeof detail !== "object" || Array.isArray(detail)
      || typeof detail.refundOrderId !== "string" || !detail.refundOrderId.trim()
      || typeof detail.paymentOrderId !== "string" || !detail.paymentOrderId.trim()
      || typeof detail.businessChainId !== "string"
      || detail.businessChainId.trim() !== `CHAIN-${detail.paymentOrderId.trim()}`
      || typeof detail.orderNo !== "string" || !detail.orderNo.trim()
      || !isValidAmount(detail.paidAmount)
      || !isValidAmount(detail.refundAmount)
      || !isAmountNotGreater(detail.refundAmount, detail.paidAmount)
      || typeof detail.refundMethod !== "string" || !detail.refundMethod.trim()
      || typeof detail.refundReason !== "string"
      || typeof detail.status !== "string" || !detail.status.trim()
      || !isKnownStatus(detail.status, REFUND_STATUSES)
      || !isConsistentRefundStatus(detail.status, detail.statusType)
      || !isConsistentRefundChannelCode(detail.status, detail.channelCode)
      || !isConsistentRefundChannelRefundNo(detail.status, detail.channelRefundNo)
      || !isValidExpiryTime(detail.appliedAt)
      || !isConsistentRefundSuccessTime(detail.status, detail.successAt)
      || !isConsistentRefundTerminalTime(detail.status, detail.terminalAt)
      || !isConsistentRefundLifecycleTimeOrder(detail.appliedAt, detail.successAt, detail.terminalAt)
      || !isConsistentRefundChannelSubmittedAt(detail)
      || !isOptionalCollection(detail.attempts)
      || !isOptionalCollection(detail.operationLogs)
      || !matchesExpectedIdentifier(detail.refundOrderId, expectedRefundOrderId)
      || (normalizedExpectedPayScene != null
        && !matchesExpectedIdentifier(detail.payScene, normalizedExpectedPayScene))) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-REFUND-DETAIL" },
      "退款详情响应为空或缺少金额状态事实"
    );
  }
  return normalizeRefundDetail(detail);
}
