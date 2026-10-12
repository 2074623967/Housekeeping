const { isChannelTransactionConflict } = require("./payment");

function resolveApiBaseUrl() {
  const configured = wx.getStorageSync("paymentApiBaseUrl");
  const app = getApp();
  const baseUrl = configured || (app.globalData && app.globalData.apiBaseUrl) || "";
  return String(baseUrl).trim().replace(/\/+$/, "");
}

function createBusinessError(payload, fallbackMessage) {
  const message = payload && payload.message ? payload.message : fallbackMessage;
  const code = payload && payload.code ? payload.code : "PAYMENT-MINI";
  const requestId = payload && payload.requestId ? payload.requestId : "";
  const suffix = requestId ? ` / ${requestId}` : "";
  const error = new Error(`${message} [${code}${suffix}]`);
  error.code = code;
  error.requestId = requestId;
  return error;
}

const MINI_PROGRAM_TIMEOUT_MS = 10000;
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
const CASHIER_ATTEMPT_STATUS_TYPES = new Map([
  ["成功", "success"]
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
  if (status.trim().toUpperCase() === "PAYING" && isChannelTransactionConflict(latestAttemptStatus)) {
    return statusType.trim().toLowerCase() === "danger";
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
      && Date.parse(createdAt.trim().replace(" ", "T"))
        <= Date.parse(completedAt.trim().replace(" ", "T"));
  }
  return completedAt == null;
}

function isConsistentPaymentCashierStatus(paymentStatus, cashierStatus, latestAttemptStatus) {
  if (typeof paymentStatus !== "string" || typeof cashierStatus !== "string") {
    return false;
  }
  if (paymentStatus.trim().toUpperCase() === "PAYING") {
    return isChannelTransactionConflict(latestAttemptStatus)
      ? cashierStatus.trim() === "结果待确认"
      : cashierStatus.trim() === "支付中";
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
  if (CASHIER_ATTEMPT_STATUS_TYPES.has(status.trim())) {
    return statusType.trim().toLowerCase() === CASHIER_ATTEMPT_STATUS_TYPES.get(status.trim())
      && isValidExpiryTime(completedAt)
      && completedAt.trim() !== "-";
  }
  return completedAt == null;
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
  return Date.parse(createdAt.trim().replace(" ", "T"))
    <= Date.parse(successAt.trim().replace(" ", "T"));
}

function isConsistentPaymentAttemptSuccessTime(attemptStatus, completedAt, successAt) {
  if (typeof attemptStatus !== "string" || attemptStatus.trim() !== "成功") {
    return true;
  }
  if (!isValidExpiryTime(completedAt) || !isValidExpiryTime(successAt)) {
    return true;
  }
  return Date.parse(completedAt.trim().replace(" ", "T"))
    <= Date.parse(successAt.trim().replace(" ", "T"));
}

function isConsistentPrepayExpiryTimeOrder(createdAt, expiresAt) {
  if (!isValidExpiryTime(createdAt) || !isValidExpiryTime(expiresAt)) {
    return true;
  }
  return Date.parse(createdAt.trim().replace(" ", "T"))
    <= Date.parse(expiresAt.trim().replace(" ", "T"));
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
  const appliedTimestamp = Date.parse(appliedAt.trim().replace(" ", "T"));
  const successTimestamp = isValidExpiryTime(successAt)
    ? Date.parse(successAt.trim().replace(" ", "T"))
    : null;
  const terminalTimestamp = isValidExpiryTime(terminalAt)
    ? Date.parse(terminalAt.trim().replace(" ", "T"))
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
  const appliedTimestamp = Date.parse(detail.appliedAt.trim().replace(" ", "T"));
  const submittedTimestamp = Date.parse(submittedAt.trim().replace(" ", "T"));
  if (submittedTimestamp < appliedTimestamp) {
    return false;
  }
  if (isValidExpiryTime(detail.terminalAt)) {
    return submittedTimestamp <= Date.parse(detail.terminalAt.trim().replace(" ", "T"));
  }
  return true;
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
  if (!isValidAmount(left) || !isValidAmount(right)) {
    return false;
  }
  const normalize = (value) => {
    const [integerPart, fractionPart] = String(value).trim()
      .replace(/^¥\s*/, "")
      .replace(/,/g, "")
      .split(".");
    const normalizedInteger = integerPart.replace(/^0+/, "") || "0";
    return `${normalizedInteger}.${(fractionPart || "").padEnd(2, "0")}`;
  };
  return normalize(left) === normalize(right);
}

function isAmountNotGreater(left, right) {
  const normalize = (value) => {
    if (!isValidAmount(value)) {
      return null;
    }
    const [integerPart, fractionPart] = String(value).trim()
      .replace(/^¥\s*/, "")
      .replace(/,/g, "")
      .split(".");
    const normalizedInteger = integerPart.replace(/^0+/, "") || "0";
    return `${normalizedInteger}${(fractionPart || "").padEnd(2, "0")}`;
  };
  const normalizedLeft = normalize(left);
  const normalizedRight = normalize(right);
  if (normalizedLeft == null || normalizedRight == null) {
    return false;
  }
  if (normalizedLeft.length !== normalizedRight.length) {
    return normalizedLeft.length < normalizedRight.length;
  }
  return normalizedLeft <= normalizedRight;
}

function request(path, options) {
  const requestOptions = options || {};
  return new Promise((resolve, reject) => {
    wx.request({
      url: `${resolveApiBaseUrl()}${path}`,
      method: requestOptions.method || "GET",
      data: requestOptions.data,
      timeout: MINI_PROGRAM_TIMEOUT_MS,
      header: {
        "Content-Type": "application/json",
        "X-Terminal": "MINI_PROGRAM"
      },
      success(response) {
        const payload = response.data;
        if (response.statusCode < 200 || response.statusCode >= 300) {
          reject(createBusinessError(payload, `请求失败：HTTP ${response.statusCode}`));
          return;
        }
        if (!payload || payload.code !== "0") {
          reject(createBusinessError(payload, "支付服务返回失败"));
          return;
        }
        resolve(payload.data);
      },
      fail(error) {
        const message = error && error.errMsg ? error.errMsg : "支付服务连接失败";
        const code = /timeout|超时/i.test(message)
          ? "PAYMENT-MINI-TIMEOUT"
          : "PAYMENT-MINI-NETWORK";
        reject(createBusinessError({ code }, message));
      }
    });
  });
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

function normalizeIdentifier(value) {
  return typeof value === "string" ? value.trim() : value;
}

function matchesExpectedIdentifier(actual, expected) {
  return !expected || (typeof actual === "string" && actual.trim() === normalizeIdentifier(expected));
}

function matchesPreservedSnapshotText(expected, actual) {
  return expected == null
    || actual == null
    || normalizeIdentifier(expected) === normalizeIdentifier(actual);
}

const paymentApi = {
  prepay(payload) {
    const normalizedPayload = normalizePayloadIdentifier(payload, "orderNo");
    const normalizedScenePayload = normalizePayloadIdentifier(normalizedPayload, "payScene");
    const normalizedCustomerPayload = normalizePayloadIdentifier(normalizedScenePayload, "customerName");
    return request("/api/payments/prepay", { method: "POST", data: normalizedCustomerPayload })
      .then((data) => normalizePrepayResult(
        data,
        normalizedCustomerPayload && normalizedCustomerPayload.orderNo,
        normalizedCustomerPayload && normalizedCustomerPayload.amount,
        normalizedCustomerPayload && normalizedCustomerPayload.customerName,
        normalizedCustomerPayload && normalizedCustomerPayload.payScene
      ));
  },
  getCashier(prepayOrderNo, terminal = "MINI_PROGRAM", expectedPayScene = null) {
    const normalizedPrepayOrderNo = normalizeIdentifier(prepayOrderNo);
    const normalizedTerminal = normalizeIdentifier(terminal);
    const query = normalizedTerminal ? `?terminal=${encodeURIComponent(normalizedTerminal)}` : "";
    return request(`/api/payments/cashier/${encodeURIComponent(normalizedPrepayOrderNo)}${query}`)
      .then((data) => normalizeCashierPageResult(
        data, normalizedPrepayOrderNo, expectedPayScene
      ));
  },
  getDetail(paymentOrderId, expectedPayScene = null) {
    const normalizedPaymentOrderId = normalizeIdentifier(paymentOrderId);
    return request(`/api/payments/${encodeURIComponent(normalizedPaymentOrderId)}`)
      .then((data) => normalizePaymentDetailResult(
        data, normalizedPaymentOrderId, expectedPayScene
      ));
  },
  getRefundDetail(refundOrderId, expectedPayScene = null) {
    const normalizedRefundOrderId = normalizeIdentifier(refundOrderId);
    return request(`/api/refunds/${encodeURIComponent(normalizedRefundOrderId)}`)
      .then((data) => normalizeRefundDetailResult(
        data, normalizedRefundOrderId, expectedPayScene
      ));
  },
  submit(payload, expectedPayScene = null) {
    const normalizedPayload = normalizePayloadIdentifier(payload, "prepayOrderNo");
    return request("/api/payments/submit", { method: "POST", data: normalizedPayload })
      .then((data) => normalizePaymentSubmitResult(
        data,
        normalizedPayload && normalizedPayload.prepayOrderNo,
        normalizedPayload && normalizedPayload.paymentMethod,
        normalizedPayload && normalizedPayload.channelCode,
        expectedPayScene
      ));
  },
  query(paymentOrderId, expectedSnapshot = null, expectedPayScene = null) {
    const normalizedPaymentOrderId = normalizeIdentifier(paymentOrderId);
    return request("/api/payments/query", { method: "POST", data: { paymentOrderId: normalizedPaymentOrderId } })
      .then((data) => normalizePaymentActionResult(
        data, normalizedPaymentOrderId, expectedSnapshot, expectedPayScene
      ));
  },
  close(paymentOrderId, expectedSnapshot = null, expectedPayScene = null) {
    const normalizedPaymentOrderId = normalizeIdentifier(paymentOrderId);
    return request("/api/payments/close", { method: "POST", data: { paymentOrderId: normalizedPaymentOrderId } })
      .then((data) => normalizePaymentActionResult(
        data, normalizedPaymentOrderId, expectedSnapshot, expectedPayScene
      ));
  }
};

function normalizePrepayResult(
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
      || typeof data.cashierStatus !== "string"
      || CASHIER_STATUS_TYPES.get(data.cashierStatus.trim()) !== data.cashierStatusType
      || !isConsistentPaymentCashierStatus(data.paymentStatus, data.cashierStatus, data.latestAttemptStatus)
      || (expectedPayScene != null && !matchesExpectedIdentifier(data.payScene, expectedPayScene))
      || (expectedOrderNo && data.orderNo !== expectedOrderNo)
      || (expectedCustomerName && data.customerName !== expectedCustomerName)
      || (expectedAmount != null && !amountsMatch(data.amount, expectedAmount))) {
    throw createBusinessError(
      { code: "PAYMENT-MINI-INVALID-PREPAY" },
      "预付单响应为空或缺少订单金额事实"
    );
  }
  return data;
}

function normalizePaymentSubmitResult(
  data, expectedPrepayOrderNo, expectedPaymentMethod = null, expectedChannelCode = null,
  expectedPayScene = null
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
      || (data.nativePaymentParams != null
        && !isValidNativePaymentParams(data.nativePaymentParams))
      || (expectedPrepayOrderNo && data.prepayOrderNo !== expectedPrepayOrderNo)
      || (expectedPaymentMethod != null
        && (typeof data.paymentMethod !== "string"
          || data.paymentMethod.trim() !== normalizeIdentifier(expectedPaymentMethod)))
      || (expectedChannelCode != null
        && (typeof data.channel !== "string"
          || data.channel.trim() !== normalizeIdentifier(expectedChannelCode)))) {
    throw createBusinessError(
      { code: "PAYMENT-MINI-INVALID-PAYMENT-SUBMIT" },
      "支付提交响应为空或缺少订单金额事实"
    );
  }
  if (["PREPAY_CREATED", "PAYING"].includes(data.paymentStatus.trim().toUpperCase())) {
    throw createBusinessError(
      { code: "PAYMENT-MINI-INVALID-PAYMENT-SUBMIT" },
      "支付提交状态未推进，请刷新收银台后使用原请求键重试"
    );
  }
  return data;
}

function isValidNativePaymentParams(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return false;
  }
  const requiredFields = ["timeStamp", "nonceStr", "package", "signType", "paySign"];
  if (requiredFields.some((field) => typeof value[field] !== "string" || !value[field].trim())) {
    return false;
  }
  return /^\d+$/.test(value.timeStamp.trim())
    && /^prepay_id=.+$/.test(value.package.trim())
    && ["RSA", "HMAC-SHA256"].includes(value.signType.trim());
}

function normalizePaymentActionResult(
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
      || (expectedPaymentOrderId && data.paymentOrderId !== expectedPaymentOrderId)
      || (expectedSnapshot && expectedSnapshot.prepayOrderNo != null
        && data.prepayOrderNo.trim() !== normalizeIdentifier(expectedSnapshot.prepayOrderNo))
      || (expectedSnapshot && expectedSnapshot.orderNo != null
        && data.orderNo.trim() !== normalizeIdentifier(expectedSnapshot.orderNo))
      || (expectedSnapshot && expectedSnapshot.amount != null
        && !amountsMatch(data.amount, expectedSnapshot.amount))
      || (expectedSnapshot && expectedSnapshot.channel != null
        && normalizeIdentifier(data.channel) !== normalizeIdentifier(expectedSnapshot.channel))
      || (expectedSnapshot && expectedSnapshot.channelTransactionNo != null
        && normalizeIdentifier(data.channelTransactionNo) !== normalizeIdentifier(expectedSnapshot.channelTransactionNo))
      || (expectedSnapshot && !matchesPreservedSnapshotText(expectedSnapshot.billNo, data.billNo))
      || (expectedSnapshot && !matchesPreservedSnapshotText(expectedSnapshot.customerName, data.customerName))
      || (expectedSnapshot && !matchesPreservedSnapshotText(expectedSnapshot.paymentMethod, data.paymentMethod))) {
    throw createBusinessError(
      { code: "PAYMENT-MINI-INVALID-PAYMENT-ACTION" },
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

function normalizeCashierPageResult(page, expectedPrepayOrderNo, expectedPayScene = null) {
  if (!page || typeof page !== "object" || Array.isArray(page)
      || typeof page.prepayOrderNo !== "string" || !page.prepayOrderNo.trim()
      || typeof page.paymentOrderId !== "string" || !page.paymentOrderId.trim()
      || typeof page.businessChainId !== "string"
      || page.businessChainId.trim() !== `CHAIN-${page.paymentOrderId.trim()}`
      || typeof page.orderNo !== "string" || !page.orderNo.trim()
      || !isValidAmount(page.amount)
      || !isConsistentCashierChannels(page.channels)
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
      || (expectedPayScene != null && !matchesExpectedIdentifier(page.payScene, expectedPayScene))
      || (expectedPrepayOrderNo && page.prepayOrderNo !== expectedPrepayOrderNo)) {
    throw createBusinessError(
      { code: "PAYMENT-MINI-INVALID-CASHIER" },
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

function normalizePaymentDetailResult(detail, expectedPaymentOrderId, expectedPayScene = null) {
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
      || (expectedPaymentOrderId && detail.paymentOrderId !== expectedPaymentOrderId)) {
    throw createBusinessError(
      { code: "PAYMENT-MINI-INVALID-PAYMENT-DETAIL" },
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
  )) {
    throw createBusinessError(
      { code: "PAYMENT-MINI-INVALID-REFUND-DETAIL" },
      "退款详情响应包含无法确认的退款尝试时间事实"
    );
  }
  if (!isConsistentRefundOperationLogs(
    operationLogs, detail.appliedAt, detail.successAt, detail.terminalAt
  )) {
    throw createBusinessError(
      { code: "PAYMENT-MINI-INVALID-REFUND-DETAIL" },
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
    && Date.parse(appliedAt.trim().replace(" ", "T"))
      <= Date.parse(log.createdAt.trim().replace(" ", "T")));
}

function isConsistentRefundOperationLogs(operationLogs, appliedAt, successAt, terminalAt) {
  let previousTimestamp = null;
  const terminalTimestamp = isValidExpiryTime(terminalAt)
    ? Date.parse(terminalAt.trim().replace(" ", "T"))
    : (isValidExpiryTime(successAt)
      ? Date.parse(successAt.trim().replace(" ", "T"))
      : null);
  for (const log of operationLogs) {
    if (!isConsistentRefundOperationLog(log, appliedAt)) {
      return false;
    }
    const createdTimestamp = Date.parse(log.createdAt.trim().replace(" ", "T"));
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
  const createdTimestamp = Date.parse(attempt.createdAt.trim().replace(" ", "T"));
  const appliedTimestamp = isValidExpiryTime(appliedAt)
    ? Date.parse(appliedAt.trim().replace(" ", "T"))
    : null;
  const submittedTimestamp = hasSubmittedAt
    ? Date.parse(submittedAt.trim().replace(" ", "T"))
    : createdTimestamp;
  const terminalStatus = ["SUCCESS", "FAIL"].includes(attempt.attemptStatus.trim().toUpperCase());
  const terminalTimestamp = isValidExpiryTime(terminalAt)
    ? Date.parse(terminalAt.trim().replace(" ", "T"))
    : (isValidExpiryTime(successAt)
      ? Date.parse(successAt.trim().replace(" ", "T"))
      : null);
  const completedTimestamp = hasCompletedAt
    ? Date.parse(completedAt.trim().replace(" ", "T"))
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
    const createdTimestamp = Date.parse(attempt.createdAt.trim().replace(" ", "T"));
    if (previousCreatedTimestamp != null && createdTimestamp > previousCreatedTimestamp) {
      return false;
    }
    previousCreatedTimestamp = createdTimestamp;
  }
  return true;
}

function normalizeRefundDetailResult(detail, expectedRefundOrderId, expectedPayScene = null) {
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
      || (expectedRefundOrderId && detail.refundOrderId !== expectedRefundOrderId)
      || (expectedPayScene != null
        && !matchesExpectedIdentifier(detail.payScene, expectedPayScene))) {
    throw createBusinessError(
      { code: "PAYMENT-MINI-INVALID-REFUND-DETAIL" },
      "退款详情响应为空或缺少金额状态事实"
    );
  }
  return normalizeRefundDetail(detail);
}

module.exports = {
  createBusinessError,
  normalizePaymentActionResult,
  normalizeCashierPage,
  normalizePaymentSubmitResult,
  normalizePaymentDetailResult,
  normalizePaymentDetail,
  normalizeCashierPageResult,
  normalizePrepayResult,
  normalizeRefundDetailResult,
  normalizeRefundDetail,
  paymentApi
};
