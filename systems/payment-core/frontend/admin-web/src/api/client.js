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
const PAYMENT_STATUS_TYPES = new Set(["info", "success", "warn", "danger"]);
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
const PAYMENT_CASHIER_STATUSES = new Map([
  ["PREPAY_CREATED", new Set(["待支付", "已失效"])],
  ["WAIT_CALLBACK", new Set(["待回调", "结果待确认"])],
  ["RISK_REVIEW", new Set(["待风控复核"])],
  ["RISK_BLOCKED", new Set(["风控拦截"])],
  ["RISK_REJECTED", new Set(["风控拒绝"])],
  ["PAYING", new Set(["支付中"])],
  ["SUCCESS", new Set(["支付成功"])],
  ["CLOSED", new Set(["已关闭", "已失效"])],
  ["FAIL", new Set(["结果待确认"])]
]);

function normalizeBusinessIdentifier(value) {
  return typeof value === "string" ? value.trim() : value;
}

function matchesExpectedBusinessIdentifier(actual, expected) {
  return !expected || normalizeBusinessIdentifier(actual) === expected;
}

function matchesPreservedBusinessIdentifier(snapshotValue, resultValue) {
  return snapshotValue == null
    || resultValue == null
    || normalizeBusinessIdentifier(snapshotValue) === normalizeBusinessIdentifier(resultValue);
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

function isValidHourBucket(value) {
  if (typeof value !== "string") {
    return false;
  }
  const normalized = value.trim();
  return /^\d{4}-\d{2}-\d{2} \d{2}:00$/.test(normalized)
    && isValidDateTime(`${normalized}:00`);
}

function isValidDateOnly(value) {
  if (typeof value !== "string") {
    return false;
  }
  const normalized = value.trim();
  if (!/^\d{4}-\d{2}-\d{2}$/.test(normalized)) {
    return false;
  }
  return isValidDateTime(`${normalized} 00:00:00`);
}

function normalizeAmount(value) {
  if (typeof value !== "string" && typeof value !== "number") {
    return NaN;
  }
  const normalizedText = String(value)
    .trim()
    .replace(/^¥\s*/, "")
    .replace(/,/g, "");
  if (!/^\d+(?:\.\d{1,2})?$/.test(normalizedText)) {
    return NaN;
  }
  const normalized = Number(normalizedText);
  return Number.isFinite(normalized) && normalized > 0 ? normalized : NaN;
}

function normalizeAmountForComparison(value) {
  if (typeof value !== "string" && typeof value !== "number") {
    return null;
  }
  const normalizedText = String(value)
    .trim()
    .replace(/^¥\s*/, "")
    .replace(/,/g, "");
  const match = normalizedText.match(/^(\d+)(?:\.(\d{1,2}))?$/);
  if (!match) {
    return null;
  }
  const integerPart = match[1].replace(/^0+(?=\d)/, "");
  const fractionalPart = (match[2] || "").padEnd(2, "0");
  if (integerPart === "0" && fractionalPart === "00") {
    return null;
  }
  return `${integerPart}.${fractionalPart}`;
}

function normalizeNonNegativeAmount(value) {
  if (typeof value !== "string" || !value.trim()) {
    return NaN;
  }
  const normalizedText = value.trim()
    .replace(/^¥\s*/, "")
    .replace(/,/g, "");
  if (!/^\d{1,16}(?:\.\d{1,2})?$/.test(normalizedText)) {
    return NaN;
  }
  const normalized = Number(normalizedText);
  return Number.isFinite(normalized) && normalized >= 0 ? normalized : NaN;
}

function normalizeNonNegativeAmountForComparison(value) {
  if (typeof value !== "string" || !value.trim()) {
    return null;
  }
  const normalizedText = value.trim()
    .replace(/^¥\s*/, "")
    .replace(/,/g, "");
  const match = normalizedText.match(/^(\d{1,16})(?:\.(\d{1,2}))?$/);
  if (!match) {
    return null;
  }
  const integerPart = match[1].replace(/^0+(?=\d)/, "");
  return `${integerPart}.${(match[2] || "").padEnd(2, "0")}`;
}

function normalizeSignedAmountForComparison(value) {
  if (typeof value !== "string" || !value.trim()) {
    return null;
  }
  const normalizedText = value.trim()
    .replace(/^¥\s*/, "")
    .replace(/,/g, "");
  const match = normalizedText.match(/^(-?)(\d{1,16})(?:\.(\d{1,2}))?$/);
  if (!match) {
    return null;
  }
  const integerPart = match[2].replace(/^0+(?=\d)/, "");
  const fractionalPart = (match[3] || "").padEnd(2, "0");
  if (integerPart === "0" && fractionalPart === "00") {
    return "0.00";
  }
  return `${match[1] ? "-" : ""}${integerPart}.${fractionalPart}`;
}

function subtractNormalizedAmounts(left, right) {
  if (!left || !right) {
    return null;
  }
  const toCents = (value) => {
    const negative = value.startsWith("-");
    const unsigned = negative ? value.slice(1) : value;
    const [integer, fraction] = unsigned.split(".");
    const cents = BigInt(integer) * 100n + BigInt(fraction);
    return negative ? -cents : cents;
  };
  const fromCents = (cents) => {
    const negative = cents < 0n;
    const unsigned = negative ? -cents : cents;
    return `${negative ? "-" : ""}${(unsigned / 100n).toString()}.${(unsigned % 100n).toString().padStart(2, "0")}`;
  };
  return fromCents(toCents(left) - toCents(right));
}

function addNormalizedAmounts(left, right) {
  if (!left || !right) {
    return null;
  }
  const [leftInteger, leftFraction] = left.split(".");
  const [rightInteger, rightFraction] = right.split(".");
  const cents = BigInt(leftFraction) + BigInt(rightFraction);
  const carry = cents >= 100n ? 1n : 0n;
  const normalizedCents = cents % 100n;
  const integer = BigInt(leftInteger) + BigInt(rightInteger) + carry;
  return `${integer.toString()}.${normalizedCents.toString().padStart(2, "0")}`;
}

function isNormalizedAmountAtMost(left, right) {
  if (!left || !right) {
    return false;
  }
  const toCents = (value) => {
    const [integer, fraction] = value.split(".");
    return BigInt(integer) * 100n + BigInt(fraction);
  };
  return toCents(left) <= toCents(right);
}

function isValidPercentage(value) {
  if (typeof value !== "string" || !value.trim()) {
    return false;
  }
  const normalizedText = value.trim();
  if (!/^\d{1,3}(?:\.\d{1,2})?%$/.test(normalizedText)) {
    return false;
  }
  const percentage = Number(normalizedText.slice(0, -1));
  return Number.isFinite(percentage) && percentage >= 0 && percentage <= 100;
}

function isConsistentSuccessRate(successCount, totalCount, successRate) {
  if (!isValidPercentage(successRate)) {
    return false;
  }
  const actualBasisPoints = Math.round(Number(successRate.trim().slice(0, -1)) * 100);
  const expectedBasisPoints = totalCount === 0
    ? 0
    : Math.round(successCount * 10000 / totalCount);
  return actualBasisPoints === expectedBasisPoints;
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

export function normalizePagePayload(data) {
  if (data && typeof data === "object"
      && Object.prototype.hasOwnProperty.call(data, "items")
      && !Array.isArray(data.items)) {
    return { ...data, items: [] };
  }
  return data;
}

function normalizePageResponse(data) {
  if (Array.isArray(data)) {
    return {
      items: data,
      total: data.length,
      pageNo: 1,
      pageSize: 20
    };
  }
  if (!data || typeof data !== "object") {
    return {
      items: [],
      total: 0,
      pageNo: 1,
      pageSize: 20
    };
  }
  if (!Object.prototype.hasOwnProperty.call(data, "items")
      || (data.items != null && !Array.isArray(data.items))) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-PAGE" },
      "支付服务返回了无法确认的分页结果"
    );
  }
  const normalized = normalizePagePayload(data);
  const items = normalized.items.filter((item) => item != null);
  const total = Number(normalized.total);
  const pageNo = Number(normalized.pageNo);
  const pageSize = Number(normalized.pageSize);
  return {
    ...normalized,
    items,
    total: Number.isInteger(total) && total >= items.length ? total : items.length,
    pageNo: Number.isInteger(pageNo) && pageNo > 0 ? pageNo : 1,
    pageSize: Number.isInteger(pageSize) && pageSize > 0 ? pageSize : 20
  };
}

export function normalizeObjectPayload(data) {
  return data && typeof data === "object" && !Array.isArray(data) ? data : {};
}

export function normalizeTaskActionResult(data, expectedTaskCode = "") {
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-TASK-ACTION" },
      "支付任务返回了无法确认的执行结果"
    );
  }
  if (["taskCode", "taskName", "summaryComment"].some(
    (field) => typeof data[field] !== "string" || !data[field].trim()
  )) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-TASK-ACTION" },
      "支付任务返回了不完整的任务身份或执行摘要"
    );
  }
  if (expectedTaskCode
      && normalizeBusinessIdentifier(data.taskCode) !== expectedTaskCode) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-TASK-ACTION" },
      "支付任务返回了与请求不一致的任务身份"
    );
  }
  const countFields = ["processedCount", "successCount", "warningCount", "failCount"];
  if (countFields.some((field) => !Number.isInteger(data[field]) || data[field] < 0)) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-TASK-ACTION" },
      "支付任务返回了不完整的执行计数"
    );
  }
  if (!isConsistentTaskRunCounts(
    data.processedCount, data.successCount, data.warningCount, data.failCount
  )) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-TASK-ACTION" },
      "支付任务返回了相互矛盾的执行计数"
    );
  }
  if (data.overview !== undefined) {
    try {
      normalizePaymentTaskCenterOverviewResult(data.overview);
    } catch (error) {
      throw createBusinessError(
        { code: "PAYMENT-CLIENT-INVALID-TASK-ACTION" },
        error.rawMessage || "支付任务返回了无法确认的任务中心总览"
      );
    }
  }
  return data;
}

export function mergePaymentTaskCenterActionOverview(currentOverview, actionResult) {
  const actionOverview = actionResult?.overview;
  if (!actionOverview) {
    return currentOverview;
  }
  return {
    ...currentOverview,
    ...actionOverview,
    queryTrends: Array.isArray(actionOverview.queryTrends)
      ? actionOverview.queryTrends
      : currentOverview.queryTrends,
    focusAlerts: Array.isArray(actionOverview.focusAlerts)
      ? actionOverview.focusAlerts
      : currentOverview.focusAlerts,
    recentTaskRuns: Array.isArray(actionOverview.recentTaskRuns)
      ? actionOverview.recentTaskRuns
      : currentOverview.recentTaskRuns
  };
}

export function normalizeRefundActionResult(
  data,
  expectedRefundOrderId,
  expectedPaymentOrderId,
  expectedRefundAmount,
  expectedSnapshot = null
) {
  if (!data || typeof data !== "object" || Array.isArray(data)
      || typeof data.refundOrderId !== "string"
      || !data.refundOrderId.trim()
      || !matchesExpectedBusinessIdentifier(data.refundOrderId, expectedRefundOrderId)
      || typeof data.paymentOrderId !== "string"
      || !data.paymentOrderId.trim()
      || typeof data.businessChainId !== "string"
      || !data.businessChainId.trim()
      || data.businessChainId.trim() !== `CHAIN-${data.paymentOrderId.trim()}`
      || typeof data.orderNo !== "string"
      || !data.orderNo.trim()
      || !matchesExpectedBusinessIdentifier(data.paymentOrderId, expectedPaymentOrderId)
      || !Number.isFinite(normalizeNonNegativeAmount(data.paidAmount))
      || !Number.isFinite(normalizeAmount(data.refundAmount))
      || !isNormalizedAmountAtMost(
        normalizeAmountForComparison(data.refundAmount),
        normalizeNonNegativeAmountForComparison(data.paidAmount))
      || (expectedRefundAmount != null
        && normalizeAmountForComparison(data.refundAmount)
          !== normalizeAmountForComparison(expectedRefundAmount))
      || typeof data.status !== "string"
      || !data.status.trim()
      || !isConsistentRefundStatus(data.status, data.statusType)
      || !isConsistentRefundChannelCode(data.status, data.channelCode)
      || !isConsistentRefundChannelRefundNo(data.status, data.channelRefundNo)
      || !isValidDateTime(data.appliedAt)
      || !isConsistentRefundSuccessTime(data.status, data.successAt)
      || !isConsistentRefundTerminalTime(data.status, data.terminalAt)
      || !isConsistentRefundLifecycleTimeOrder(data.appliedAt, data.successAt, data.terminalAt)
      || !isConsistentRefundChannelSubmittedAt(data)
      || (expectedSnapshot && !matchesRefundActionSnapshot(expectedSnapshot, data))) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-REFUND-ACTION" },
      "退款动作返回了无法确认的退款金额事实"
    );
  }
  return data;
}

export function matchesRefundActionSnapshot(snapshot, result) {
  return Boolean(snapshot
    && result
    && normalizeBusinessIdentifier(result.refundOrderId)
      === normalizeBusinessIdentifier(snapshot.refundOrderId)
    && normalizeBusinessIdentifier(result.paymentOrderId)
      === normalizeBusinessIdentifier(snapshot.paymentOrderId)
    && (snapshot.payScene == null
      || normalizeBusinessIdentifier(result.payScene)
        === normalizeBusinessIdentifier(snapshot.payScene))
    && (snapshot.businessChainId == null
      || normalizeBusinessIdentifier(result.businessChainId)
        === normalizeBusinessIdentifier(snapshot.businessChainId))
    && normalizeBusinessIdentifier(result.orderNo)
      === normalizeBusinessIdentifier(snapshot.orderNo)
    && normalizeAmountForComparison(result.refundAmount)
      === normalizeAmountForComparison(snapshot.refundAmount)
    && (snapshot.paidAmount == null
      || normalizeNonNegativeAmountForComparison(result.paidAmount)
        === normalizeNonNegativeAmountForComparison(snapshot.paidAmount))
    && (snapshot.refundMethod == null
      || normalizeBusinessIdentifier(result.refundMethod)
        === normalizeBusinessIdentifier(snapshot.refundMethod))
    && (snapshot.refundReason == null
      || normalizeBusinessIdentifier(result.refundReason)
        === normalizeBusinessIdentifier(snapshot.refundReason))
    && matchesPreservedBusinessIdentifier(snapshot.customerName, result.customerName)
    && matchesPreservedBusinessIdentifier(snapshot.paymentMethod, result.paymentMethod)
    && (snapshot.payScene == null
      || normalizeBusinessIdentifier(result.payScene)
        === normalizeBusinessIdentifier(snapshot.payScene))
    && (snapshot.channelCode == null
      || normalizeBusinessIdentifier(result.channelCode)
        === normalizeBusinessIdentifier(snapshot.channelCode))
    && (snapshot.channelRefundNo == null
      || normalizeBusinessIdentifier(result.channelRefundNo)
        === normalizeBusinessIdentifier(snapshot.channelRefundNo)));
}

export function normalizeRefundDetailResult(data, expectedRefundOrderId) {
  if (!data || typeof data !== "object" || Array.isArray(data)
      || typeof data.refundOrderId !== "string"
      || !data.refundOrderId.trim()
      || typeof data.paymentOrderId !== "string"
      || !data.paymentOrderId.trim()
      || typeof data.businessChainId !== "string"
      || !data.businessChainId.trim()
      || data.businessChainId.trim() !== `CHAIN-${data.paymentOrderId.trim()}`
      || typeof data.orderNo !== "string"
      || !data.orderNo.trim()
      || !Number.isFinite(normalizeNonNegativeAmount(data.paidAmount))
      || !Number.isFinite(normalizeAmount(data.refundAmount))
      || !isNormalizedAmountAtMost(
        normalizeAmountForComparison(data.refundAmount),
        normalizeNonNegativeAmountForComparison(data.paidAmount))
      || typeof data.status !== "string"
      || !data.status.trim()
      || !isConsistentRefundStatus(data.status, data.statusType)
      || !isConsistentRefundChannelCode(data.status, data.channelCode)
      || !isConsistentRefundChannelRefundNo(data.status, data.channelRefundNo)
      || !isValidDateTime(data.appliedAt)
      || !isConsistentRefundSuccessTime(data.status, data.successAt)
      || !isConsistentRefundTerminalTime(data.status, data.terminalAt)
      || !isConsistentRefundLifecycleTimeOrder(data.appliedAt, data.successAt, data.terminalAt)
      || !isConsistentRefundChannelSubmittedAt(data)
      || !isOptionalCollection(data.attempts)
      || !isOptionalCollection(data.operationLogs)
      || !matchesExpectedBusinessIdentifier(data.refundOrderId, expectedRefundOrderId)) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-REFUND-DETAIL" },
      "退款详情返回了无法确认的金额状态事实"
    );
  }
  return normalizeRefundDetail(data);
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
  return isValidDateTime(successAt) && successAt.trim() !== "-";
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

function isConsistentRefundTerminalTime(status, terminalAt) {
  if (typeof status !== "string") {
    return false;
  }
  const terminal = ["SUCCESS", "FAIL"].includes(status.trim().toUpperCase());
  if (!terminal) {
    return terminalAt == null || terminalAt === "-";
  }
  return isValidDateTime(terminalAt) && terminalAt.trim() !== "-";
}

function isConsistentRefundLifecycleTimeOrder(appliedAt, successAt, terminalAt) {
  const appliedTimestamp = Date.parse(appliedAt.trim().replace(" ", "T"));
  const successTimestamp = isValidDateTime(successAt)
    ? Date.parse(successAt.trim().replace(" ", "T"))
    : null;
  const terminalTimestamp = isValidDateTime(terminalAt)
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
  if (!isValidDateTime(submittedAt)) {
    return false;
  }
  const appliedTimestamp = Date.parse(detail.appliedAt.trim().replace(" ", "T"));
  const submittedTimestamp = Date.parse(submittedAt.trim().replace(" ", "T"));
  if (submittedTimestamp < appliedTimestamp) {
    return false;
  }
  if (isValidDateTime(detail.terminalAt)) {
    return submittedTimestamp <= Date.parse(detail.terminalAt.trim().replace(" ", "T"));
  }
  return true;
}

function isReadableDiagnosticRefundFacts(item, refundStatus) {
  if (!["SUCCESS", "FAIL"].includes(item.status)
      || (refundStatus !== "TERMINAL_FACT_MISMATCH" && item.status !== "SUCCESS")) {
    return false;
  }
  return (item.statusType == null || typeof item.statusType === "string")
    && (item.channelCode == null || typeof item.channelCode === "string")
    && (item.channelRefundNo == null || typeof item.channelRefundNo === "string")
    && (item.channelSubmittedAt == null || item.channelSubmittedAt === "-" || isValidDateTime(item.channelSubmittedAt))
    && (item.successAt == null || item.successAt === "-" || isValidDateTime(item.successAt))
    && (item.terminalAt == null || item.terminalAt === "-" || isValidDateTime(item.terminalAt));
}

function isReadableDiagnosticRefundAmount(value) {
  return value === null
    || (typeof value === "string"
      && /^¥-?(?:\d+|\d{1,3}(?:,\d{3})+)\.\d{2}$/.test(value));
}

export function normalizeRefundListPage(data, refundStatus = "全部") {
  const normalizedPage = normalizePageResponse(data);
  const diagnostic = ["SUCCESS_TIME_MISSING", "TERMINAL_FACT_MISMATCH", "UNVERIFIED_SUCCESS"]
    .includes(refundStatus);
  return {
    ...normalizedPage,
    items: normalizedPage.items.map((item) => {
      if (!item || typeof item !== "object" || Array.isArray(item)
          || typeof item.refundOrderId !== "string"
          || !item.refundOrderId.trim()
          || typeof item.paymentOrderId !== "string"
          || !item.paymentOrderId.trim()
          || typeof item.businessChainId !== "string"
          || !item.businessChainId.trim()
          || item.businessChainId.trim() !== `CHAIN-${item.paymentOrderId.trim()}`
          || typeof item.orderNo !== "string"
          || !item.orderNo.trim()
          || typeof item.customerName !== "string"
          || !item.customerName.trim()
          || (diagnostic
            ? !isReadableDiagnosticRefundAmount(item.refundAmount)
            : !Number.isFinite(normalizeAmount(item.refundAmount)))
          || typeof item.refundMethod !== "string"
      || !item.refundMethod.trim()
      || !isValidDateTime(item.appliedAt)
      || (diagnostic
        ? !isReadableDiagnosticRefundFacts(item, refundStatus)
        : (!isConsistentRefundStatus(item.status, item.statusType)
          || !isConsistentRefundChannelCode(item.status, item.channelCode)
          || !isConsistentRefundChannelRefundNo(item.status, item.channelRefundNo)
          || !isConsistentRefundSuccessTime(item.status, item.successAt)
          || !isConsistentRefundTerminalTime(item.status, item.terminalAt)
          || !isConsistentRefundLifecycleTimeOrder(item.appliedAt, item.successAt, item.terminalAt)
          || !isConsistentRefundChannelSubmittedAt(item)))) {
        throw createBusinessError(
          { code: "PAYMENT-CLIENT-INVALID-REFUND-LIST" },
          "退款列表返回了无法确认的金额状态事实"
        );
      }
      return item;
    })
  };
}

export function normalizePaymentActionResult(
  data,
  expectedPaymentOrderId,
  expectedOrderNo,
  expectedAmount,
  expectedPrepayOrderNo = null,
  expectedSnapshot = null
) {
  if (!data || typeof data !== "object" || Array.isArray(data)
      || !matchesExpectedBusinessIdentifier(data.paymentOrderId, expectedPaymentOrderId)
      || typeof data.prepayOrderNo !== "string"
      || !data.prepayOrderNo.trim()
      || (expectedPrepayOrderNo != null
        && !matchesExpectedBusinessIdentifier(data.prepayOrderNo, expectedPrepayOrderNo))
      || typeof data.businessChainId !== "string"
      || !data.businessChainId.trim()
      || data.businessChainId.trim() !== `CHAIN-${data.paymentOrderId.trim()}`
      || typeof data.orderNo !== "string"
      || !data.orderNo.trim()
      || !matchesExpectedBusinessIdentifier(data.orderNo, expectedOrderNo)
      || !Number.isFinite(normalizeAmount(data.amount))
      || (expectedAmount != null
        && normalizeAmountForComparison(data.amount) !== normalizeAmountForComparison(expectedAmount))
      || typeof data.status !== "string"
      || !data.status.trim()
      || !isConsistentPaymentOrderStatus(data.status, data.statusType)
      || !isConsistentPaymentSuccessTime(data.status, data.successAt)
      || !isConsistentPaymentChannel(data.status, data.channel)
      || !isConsistentPaymentChannelTransaction(data.status, data.channelTransactionNo)
      || !isValidDateTime(data.createdAt)
      || !isConsistentPaymentLifecycleTimeOrder(data.createdAt, data.successAt)
      || !isValidOptionalStatusPair(data.latestAttemptStatus, data.latestAttemptStatusType)
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
      || (expectedSnapshot && !matchesPaymentDetailSnapshot(expectedSnapshot, data))) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION" },
      "支付动作返回了无法确认的订单金额事实"
    );
  }
  return data;
}

export function normalizePaymentDetailResult(data, expectedPaymentOrderId) {
  if (!data || typeof data !== "object" || Array.isArray(data)
      || typeof data.paymentOrderId !== "string"
      || !data.paymentOrderId.trim()
      || typeof data.businessChainId !== "string"
      || !data.businessChainId.trim()
      || data.businessChainId.trim() !== `CHAIN-${data.paymentOrderId.trim()}`
      || typeof data.prepayOrderNo !== "string"
      || !data.prepayOrderNo.trim()
      || typeof data.orderNo !== "string"
      || !data.orderNo.trim()
      || typeof data.customerName !== "string"
      || !data.customerName.trim()
      || !Number.isFinite(normalizeAmount(data.amount))
      || typeof data.paymentMethod !== "string"
      || !data.paymentMethod.trim()
      || typeof data.channel !== "string"
      || !data.channel.trim()
      || !isConsistentPaymentOrderStatus(data.status, data.statusType)
      || !isConsistentPaymentSuccessTime(data.status, data.successAt)
      || !isConsistentPaymentChannelTransaction(data.status, data.channelTransactionNo)
      || !isValidOptionalStatusPair(data.latestAttemptStatus, data.latestAttemptStatusType)
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
      || !isOptionalCollection(data.routeLogs)
      || !isOptionalCollection(data.notifyLogs)
      || !isOptionalCollection(data.eventLogs)
      || !isValidDateTime(data.createdAt)
      || !isConsistentPaymentLifecycleTimeOrder(data.createdAt, data.successAt)
      || !matchesExpectedBusinessIdentifier(data.paymentOrderId, expectedPaymentOrderId)) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-PAYMENT-DETAIL" },
      "支付详情返回了无法确认的业务事实"
    );
  }
  return data;
}

function isOptionalCollection(value) {
  return value == null || Array.isArray(value);
}

function isConsistentPaymentOrderStatus(status, statusType) {
  if (typeof status !== "string" || !status.trim()
      || typeof statusType !== "string" || !statusType.trim()) {
    return false;
  }
  const expectedType = {
    PREPAY_CREATED: "info",
    WAIT_CALLBACK: "warn",
    RISK_REVIEW: "warn",
    RISK_BLOCKED: "danger",
    RISK_REJECTED: "danger",
    PAYING: "info",
    SUCCESS: "success",
    CLOSED: "danger",
    FAIL: "danger"
  }[status.trim().toUpperCase()];
  return Boolean(expectedType) && statusType.trim().toLowerCase() === expectedType;
}

function isConsistentPaymentSuccessTime(status, successAt) {
  if (typeof status !== "string") {
    return false;
  }
  if (status.trim().toUpperCase() !== "SUCCESS") {
    return successAt == null;
  }
  return isValidDateTime(successAt);
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

function isConsistentSuccessfulPaymentAttempt(
  paymentStatus, attemptStatus, attemptStatusType, attemptCompletedAt
) {
  if (typeof paymentStatus !== "string") {
    return false;
  }
  const normalizedPaymentStatus = paymentStatus.trim().toUpperCase();
  if (normalizedPaymentStatus !== "SUCCESS") {
    return !isPaymentAttemptSuccessStatus(attemptStatus)
      && (!["PREPAY_CREATED", "RISK_REVIEW", "WAIT_CALLBACK", "PAYING"]
        .includes(normalizedPaymentStatus)
        || !isPaymentAttemptFailureStatus(attemptStatus));
  }
  return attemptStatus === "成功"
    && attemptStatusType === "success"
    && isValidDateTime(attemptCompletedAt);
}

function isConsistentPaymentLifecycleTimeOrder(createdAt, successAt) {
  if (!isValidDateTime(createdAt) || !isValidDateTime(successAt)) {
    return true;
  }
  return Date.parse(createdAt.trim().replace(" ", "T"))
    <= Date.parse(successAt.trim().replace(" ", "T"));
}

function isConsistentPaymentAttemptCompletedAt(status, completedAt, createdAt) {
  if (status == null) {
    return completedAt == null;
  }
  if (typeof status !== "string") {
    return false;
  }
  if (status.trim() === "成功") {
    return isValidDateTime(completedAt)
      && (!isValidDateTime(createdAt)
        || Date.parse(createdAt.trim().replace(" ", "T"))
          <= Date.parse(completedAt.trim().replace(" ", "T")));
  }
  return completedAt == null;
}

function isConsistentPaymentAttemptSuccessTime(attemptStatus, completedAt, successAt) {
  if (typeof attemptStatus !== "string" || attemptStatus.trim() !== "成功") {
    return true;
  }
  if (!isValidDateTime(completedAt) || !isValidDateTime(successAt)) {
    return true;
  }
  return Date.parse(completedAt.trim().replace(" ", "T"))
    <= Date.parse(successAt.trim().replace(" ", "T"));
}

function isValidPaymentSuccessFactIssue(value) {
  return value === null || ["SUCCESS_FACT_MISMATCH", "SUCCESS_TIME_MISSING"].includes(value);
}

function isReadableDiagnosticPaymentAmount(value) {
  return value === null
    || (typeof value === "string"
      && /^¥-?(?:\d+|\d{1,3}(?:,\d{3})+)\.\d{2}$/.test(value));
}

function isValidPaymentListPrepayBinding(item) {
  if (item.prepayBindingIssue == null) {
    return typeof item.payScene === "string" && Boolean(item.payScene.trim());
  }
  return ["MULTIPLE_PREPAY_BINDINGS", "PREPAY_BINDING_MISSING"].includes(item.prepayBindingIssue)
    && item.payScene === null;
}

export function normalizePaymentListPage(data) {
  const normalizedPage = normalizePageResponse(data);
  return {
    ...normalizedPage,
    items: normalizedPage.items.map((item) => {
      const isSuccessFactIssue = ["SUCCESS_FACT_MISMATCH", "SUCCESS_TIME_MISSING"]
        .includes(item?.successFactIssue);
      if (!item || typeof item !== "object" || Array.isArray(item)
          || typeof item.paymentOrderId !== "string"
          || !item.paymentOrderId.trim()
          || typeof item.businessChainId !== "string"
          || !item.businessChainId.trim()
          || item.businessChainId.trim() !== `CHAIN-${item.paymentOrderId.trim()}`
          || typeof item.orderNo !== "string"
          || !item.orderNo.trim()
          || !isValidPaymentListPrepayBinding(item)
          || typeof item.customerName !== "string"
          || !item.customerName.trim()
          || (isSuccessFactIssue
            ? !isReadableDiagnosticPaymentAmount(item.amount)
            : !Number.isFinite(normalizeAmount(item.amount)))
          || typeof item.paymentMethod !== "string"
          || !item.paymentMethod.trim()
          || !isValidPaymentSuccessFactIssue(item.successFactIssue)
          || (isSuccessFactIssue && item.status !== "SUCCESS")
          || (isSuccessFactIssue && (
            (item.statusType !== null && typeof item.statusType !== "string")
            || (item.channel !== null && typeof item.channel !== "string")
            || (item.channelTransactionNo !== null && typeof item.channelTransactionNo !== "string")
            || (item.latestAttemptStatus !== null && typeof item.latestAttemptStatus !== "string")
            || (item.latestAttemptStatusType !== null && typeof item.latestAttemptStatusType !== "string")
            || (item.latestAttemptCompletedAt !== null && !isValidDateTime(item.latestAttemptCompletedAt))
          ))
          || (!isSuccessFactIssue && (!isConsistentPaymentOrderStatus(item.status, item.statusType)
            || typeof item.channel !== "string"
            || !item.channel.trim()
            || !isConsistentPaymentChannelTransaction(item.status, item.channelTransactionNo)
            || !isValidOptionalStatusPair(item.latestAttemptStatus, item.latestAttemptStatusType)
            || !isConsistentSuccessfulPaymentAttempt(
              item.status,
              item.latestAttemptStatus,
              item.latestAttemptStatusType,
              item.latestAttemptCompletedAt
            )
            || !isConsistentPaymentAttemptCompletedAt(
              item.latestAttemptStatus, item.latestAttemptCompletedAt, item.createdAt
            )))
          || !isValidDateTime(item.createdAt)) {
        throw createBusinessError(
          { code: "PAYMENT-CLIENT-INVALID-PAYMENT-LIST" },
          "支付单列表返回了无法确认的金额状态事实"
        );
      }
      return item;
    })
  };
}

export function matchesPaymentDetailSnapshot(snapshot, result) {
  return Boolean(snapshot
    && result
    && normalizeBusinessIdentifier(result.paymentOrderId)
      === normalizeBusinessIdentifier(snapshot.paymentOrderId)
    && (snapshot.businessChainId == null
      || normalizeBusinessIdentifier(result.businessChainId)
        === normalizeBusinessIdentifier(snapshot.businessChainId))
    && (snapshot.prepayOrderNo == null
      || normalizeBusinessIdentifier(result.prepayOrderNo)
        === normalizeBusinessIdentifier(snapshot.prepayOrderNo))
    && (snapshot.payScene == null
      || normalizeBusinessIdentifier(result.payScene)
        === normalizeBusinessIdentifier(snapshot.payScene))
    && normalizeBusinessIdentifier(result.orderNo)
      === normalizeBusinessIdentifier(snapshot.orderNo)
    && matchesPreservedBusinessIdentifier(snapshot.billNo, result.billNo)
    && matchesPreservedBusinessIdentifier(snapshot.customerName, result.customerName)
    && normalizeAmountForComparison(result.amount)
      === normalizeAmountForComparison(snapshot.amount)
    && (snapshot.paymentMethod == null
      || normalizeBusinessIdentifier(result.paymentMethod)
        === normalizeBusinessIdentifier(snapshot.paymentMethod))
    && (snapshot.channel == null
      || normalizeBusinessIdentifier(result.channel)
        === normalizeBusinessIdentifier(snapshot.channel))
    && (snapshot.channelTransactionNo == null
      || normalizeBusinessIdentifier(result.channelTransactionNo)
        === normalizeBusinessIdentifier(snapshot.channelTransactionNo)));
}

export function normalizePaymentRecordDetailResult(data, expectedPaymentOrderId) {
  const paymentAmount = normalizeNonNegativeAmountForComparison(data?.paymentAmount);
  const refundedAmount = normalizeNonNegativeAmountForComparison(data?.refundedAmount);
  if (!data || typeof data !== "object" || Array.isArray(data)
      || typeof data.paymentOrderId !== "string"
      || !data.paymentOrderId.trim()
      || typeof data.businessChainId !== "string"
      || !data.businessChainId.trim()
      || data.businessChainId.trim() !== `CHAIN-${data.paymentOrderId.trim()}`
      || typeof data.businessOrderNo !== "string"
      || !data.businessOrderNo.trim()
      || !isValidPaymentRecordPrepayBinding(data)
      || !Number.isFinite(normalizeAmount(data.paymentAmount))
      || typeof data.paymentStatus !== "string"
      || !data.paymentStatus.trim()
      || !isConsistentPaymentRecordStatus(data.paymentStatus, data.statusType)
      || !isValidPaymentRecordFactSnapshot(data)
      || !isConsistentPaymentRecordTimes(data.expireTime, data.createdAt, data.updatedAt,
        data.prepayBindingIssue != null)
      || !isConsistentPaymentRecordAttemptStatus(
        data.latestAttemptStatus, data.latestAttemptStatusType
      )
      || !isNullableRecordedText(data.bankName)
      || !isNullableRecordedText(data.cardNo)
      || !isNullableRecordedText(data.receivingAccount)
      || !isNullableRecordedText(data.channelReturnCode)
      || !isNullableRecordedText(data.paymentGateway)
      || !isNullableRecordedText(data.notifyUrl)
      || !isNullableRecordedText(data.callbackMqTopic)
      || !isNullableRecordedText(data.businessLineId)
      || !isNullableRecordedText(data.productName)
      || !isNullableRecordedText(data.userPaymentChannelId)
      || !isNullableRecordedText(data.userId)
      || !Number.isFinite(normalizeNonNegativeAmount(data.paymentAmount))
      || !Number.isInteger(data.refundCount)
      || data.refundCount < 0
      || !Number.isInteger(data.unverifiedRefundCount)
      || data.unverifiedRefundCount < 0
      || !Number.isFinite(normalizeNonNegativeAmount(data.refundedAmount))
      || !isNormalizedAmountAtMost(refundedAmount, paymentAmount)
      || (expectedPaymentOrderId
        && !matchesExpectedBusinessIdentifier(data.paymentOrderId, normalizeBusinessIdentifier(expectedPaymentOrderId)))) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-DETAIL" },
      "支付记录详情返回了无法确认的金额状态事实"
    );
  }
  return data;
}

export function matchesPaymentRecordDetailSnapshot(snapshot, result) {
  return Boolean(snapshot
    && result
    && normalizeBusinessIdentifier(result.paymentOrderId)
      === normalizeBusinessIdentifier(snapshot.paymentOrderId)
    && (snapshot.businessChainId == null
      || normalizeBusinessIdentifier(result.businessChainId)
        === normalizeBusinessIdentifier(snapshot.businessChainId))
    && normalizeBusinessIdentifier(result.businessOrderNo)
      === normalizeBusinessIdentifier(snapshot.businessOrderNo)
    && (snapshot.payScene == null
      || normalizeBusinessIdentifier(result.payScene)
        === normalizeBusinessIdentifier(snapshot.payScene))
    && matchesPreservedBusinessIdentifier(snapshot.paymentMethod, result.paymentMethod)
    && normalizeAmountForComparison(result.paymentAmount)
      === normalizeAmountForComparison(snapshot.paymentAmount));
}

function isConsistentPaymentRecordStatus(status, statusType) {
  if (typeof status !== "string" || !status.trim()
      || typeof statusType !== "string" || !statusType.trim()) {
    return false;
  }
  const expectedType = {
    "支付成功": "success",
    "支付中": "warn",
    "已关闭": "danger",
    "支付失败": "danger"
  }[status.trim()];
  return Boolean(expectedType) && statusType.trim().toLowerCase() === expectedType;
}

function isConsistentPaymentRecordExternalTransactionNo(status, externalTransactionNo) {
  if (typeof status !== "string") {
    return false;
  }
  if (status.trim() !== "支付成功") {
    return externalTransactionNo == null || typeof externalTransactionNo === "string";
  }
  return typeof externalTransactionNo === "string" && Boolean(externalTransactionNo.trim());
}

function isPaymentAttemptSuccessStatus(status) {
  return typeof status === "string"
    && ["SUCCESS", "成功", "支付成功"].includes(status.trim().toUpperCase());
}

function isPaymentAttemptFailureStatus(status) {
  return typeof status === "string"
    && ["FAIL", "FAILED", "失败", "支付失败"].includes(status.trim().toUpperCase());
}

function isConsistentPaymentRecordAttemptCompletedAt(status, completedAt, createdAt) {
  if (status == null) {
    return completedAt == null;
  }
  if (typeof status !== "string" || !status.trim()) {
    return false;
  }
  if (!isPaymentAttemptSuccessStatus(status)) {
    return completedAt == null;
  }
  return isValidDateTime(completedAt)
    && isValidDateTime(createdAt)
    && Date.parse(createdAt.trim().replace(" ", "T"))
      <= Date.parse(completedAt.trim().replace(" ", "T"));
}

function isConsistentPaymentRecordAttemptStatus(status, statusType) {
  if (status == null && statusType == null) {
    return true;
  }
  if (typeof status !== "string" || !status.trim()
      || typeof statusType !== "string" || !statusType.trim()) {
    return false;
  }
  const normalizedStatus = status.trim().toUpperCase();
  const normalizedType = statusType.trim().toLowerCase();
  if (isPaymentAttemptSuccessStatus(normalizedStatus)) {
    return normalizedType === "success";
  }
  if (isPaymentAttemptFailureStatus(normalizedStatus)) {
    return normalizedType === "danger";
  }
  return PAYMENT_STATUS_TYPES.has(normalizedType);
}

function isConsistentPaymentRecordSuccessfulAttempt(
  paymentStatus, attemptStatus, attemptStatusType, attemptCompletedAt, updatedAt
) {
  if (typeof paymentStatus !== "string" || paymentStatus.trim() !== "支付成功") {
    return !isPaymentAttemptSuccessStatus(attemptStatus);
  }
  return isPaymentAttemptSuccessStatus(attemptStatus)
    && attemptStatusType.trim().toLowerCase() === "success"
    && isValidDateTime(attemptCompletedAt)
    && isValidDateTime(updatedAt)
    && Date.parse(attemptCompletedAt.trim().replace(" ", "T"))
      <= Date.parse(updatedAt.trim().replace(" ", "T"));
}

function isConsistentPaymentRecordPaidAt(status, paidAt, createdAt, updatedAt) {
  if (typeof status !== "string") {
    return false;
  }
  if (status.trim() !== "支付成功") {
    return paidAt == null;
  }
  return isValidDateTime(paidAt)
    && isValidDateTime(createdAt)
    && isValidDateTime(updatedAt)
    && Date.parse(createdAt.trim().replace(" ", "T"))
      <= Date.parse(paidAt.trim().replace(" ", "T"))
    && Date.parse(paidAt.trim().replace(" ", "T"))
      <= Date.parse(updatedAt.trim().replace(" ", "T"));
}

function isValidPaymentRecordFactSnapshot(record) {
  const issue = record.successFactIssue;
  if (!isValidPaymentSuccessFactIssue(issue)) {
    return false;
  }
  if (issue) {
    return record.paymentStatus === "支付成功"
      && isNullableRecordedText(record.externalTransactionNo)
      && (issue === "SUCCESS_TIME_MISSING" ? record.paidAt == null : isValidDateTime(record.paidAt))
      && (record.latestAttemptCompletedAt == null
        || isValidDateTime(record.latestAttemptCompletedAt));
  }
  return isConsistentPaymentRecordExternalTransactionNo(record.paymentStatus, record.externalTransactionNo)
    && isConsistentPaymentRecordPaidAt(
      record.paymentStatus, record.paidAt, record.createdAt, record.updatedAt
    )
    && isConsistentPaymentRecordSuccessfulAttempt(
      record.paymentStatus, record.latestAttemptStatus, record.latestAttemptStatusType,
      record.latestAttemptCompletedAt, record.updatedAt
    )
    && isConsistentPaymentRecordAttemptCompletedAt(
      record.latestAttemptStatus, record.latestAttemptCompletedAt, record.createdAt
    );
}

function isConsistentPaymentRecordTimes(expireTime, createdAt, updatedAt, missingExpiry = false) {
  if ((missingExpiry ? expireTime !== null : !isValidDateTime(expireTime))
      || !isValidDateTime(createdAt)
      || !isValidDateTime(updatedAt)) {
    return false;
  }
  const createdTimestamp = Date.parse(createdAt.trim().replace(" ", "T"));
  return (missingExpiry || Date.parse(expireTime.trim().replace(" ", "T")) >= createdTimestamp)
    && Date.parse(updatedAt.trim().replace(" ", "T")) >= createdTimestamp;
}

function isNullableRecordedText(value) {
  return value === null || (typeof value === "string" && Boolean(value.trim()));
}

function isValidPaymentRecordPrepayBinding(record) {
  if (record.prepayBindingIssue == null) {
    return typeof record.payScene === "string" && Boolean(record.payScene.trim());
  }
  return ["MULTIPLE_PREPAY_BINDINGS", "PREPAY_BINDING_MISSING"].includes(record.prepayBindingIssue)
    && record.payScene === null
    && record.expireTime === null
    && record.validityPeriod === null
    && record.paymentType === "未分类";
}

export function normalizePaymentRecordListPage(data) {
  const normalizedPage = normalizePageResponse(data);
  return {
    ...normalizedPage,
    items: normalizedPage.items.map((item) => {
      const paymentAmount = normalizeNonNegativeAmountForComparison(item?.paymentAmount);
      const refundedAmount = normalizeNonNegativeAmountForComparison(item?.refundedAmount);
      if (!item || typeof item !== "object" || Array.isArray(item)
          || !Number.isInteger(item.serialNo)
          || item.serialNo <= 0
          || typeof item.paymentOrderId !== "string"
          || !item.paymentOrderId.trim()
          || typeof item.businessChainId !== "string"
          || !item.businessChainId.trim()
          || item.businessChainId.trim() !== `CHAIN-${item.paymentOrderId.trim()}`
          || typeof item.businessOrderNo !== "string"
          || !item.businessOrderNo.trim()
          || !isValidPaymentRecordPrepayBinding(item)
          || !isNullableRecordedText(item.paymentRequestNo)
          || !isNullableRecordedText(item.applicationSourceId)
          || (item.latestAttemptStatus != null
            && (item.paymentRequestNo === null || item.applicationSourceId === null))
          || !isNullableRecordedText(item.businessLineId)
          || !isNullableRecordedText(item.paymentGateway)
          || typeof item.paymentChannel !== "string"
          || !item.paymentChannel.trim()
          || typeof item.paymentType !== "string"
          || !item.paymentType.trim()
          || !isConsistentPaymentRecordStatus(item.paymentStatus, item.statusType)
          || !isValidPaymentRecordFactSnapshot(item)
          || !isConsistentPaymentRecordAttemptStatus(
            item.latestAttemptStatus, item.latestAttemptStatusType
          )
          || !isNullableRecordedText(item.bankName)
          || !isNullableRecordedText(item.cardNo)
          || !isNullableRecordedText(item.channelReturnCode)
          || !isNullableRecordedText(item.returnParameterType)
          || (item.prepayBindingIssue == null
            && (typeof item.validityPeriod !== "string" || !item.validityPeriod.trim()))
          || !Number.isFinite(normalizeAmount(item.paymentAmount))
          || !Number.isInteger(item.refundCount)
          || item.refundCount < 0
          || !Number.isInteger(item.unverifiedRefundCount)
          || item.unverifiedRefundCount < 0
          || !refundedAmount
          || !isNormalizedAmountAtMost(refundedAmount, paymentAmount)
          || !isNullableRecordedText(item.productName)
          || !isNullableRecordedText(item.userPaymentChannelId)
          || !isNullableRecordedText(item.receivingAccount)
          || !isNullableRecordedText(item.notifyUrl)
          || !isNullableRecordedText(item.callbackMqTopic)
          || !isConsistentPaymentRecordTimes(item.expireTime, item.createdAt, item.updatedAt,
            item.prepayBindingIssue != null)
          || !isNullableRecordedText(item.userId)) {
        throw createBusinessError(
          { code: "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-LIST" },
          "支付记录列表返回了无法确认的金额状态事实"
        );
      }
      return item;
    })
  };
}

export function normalizePaymentEventDetailResult(data, expectedEventNo) {
  if (!data || typeof data !== "object" || Array.isArray(data)
      || typeof data.eventNo !== "string"
      || !data.eventNo.trim()
      || typeof data.eventType !== "string"
      || !data.eventType.trim()
      || typeof data.paymentOrderId !== "string"
      || !data.paymentOrderId.trim()
      || typeof data.businessChainId !== "string"
      || !data.businessChainId.trim()
      || data.businessChainId.trim() !== `CHAIN-${data.paymentOrderId.trim()}`
      || !isValidPaymentSuccessFactIssue(data.successFactIssue)
      || typeof data.bizNo !== "string"
      || !data.bizNo.trim()
      || !isNullableRecordedText(data.standardOrderNo)
      || typeof data.eventTopic !== "string"
      || !data.eventTopic.trim()
      || typeof data.downstreamSystem !== "string"
      || !data.downstreamSystem.trim()
      || typeof data.publishStatus !== "string"
      || !data.publishStatus.trim()
      || !isKnownPaymentEventPublishStatus(data.publishStatus)
      || !isConsistentPaymentEventPublishStatusType(data.publishStatus, data.publishStatusType)
      || !isConsistentPaymentSuccessHttpStage(data.eventType, data.paymentSuccessHttpStage)
      || !isConsistentPaymentEventPublishTimes(
        data.publishStatus,
        data.lastPublishedAt,
        data.nextRetryAt,
        data.lastRepublishOperator,
        data.lastRepublishedAt,
        data.createdAt
      )
      || !Number.isInteger(data.retryCount)
      || data.retryCount < 0
      || !isValidPaymentEventPayload(data.eventPayload)
      || !isValidDateTime(data.createdAt)
      || !matchesExpectedBusinessIdentifier(data.eventNo, expectedEventNo)) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-EVENT-DETAIL" },
      "支付事件详情返回了无法确认的路由状态事实"
    );
  }
  return data;
}

function isValidPaymentEventPayload(value) {
  if (typeof value !== "string" || !value.trim()) {
    return false;
  }
  if (value === "[REDACTED]") {
    return true;
  }
  try {
    const parsed = JSON.parse(value);
    return parsed !== null && typeof parsed === "object";
  } catch (error) {
    return false;
  }
}

function isValidOptionalPaymentPayload(value) {
  return value == null || isValidPaymentEventPayload(value);
}

function isKnownPaymentEventPublishStatus(value) {
  return typeof value === "string"
    && ["PENDING", "SUCCESS", "FAILED", "DEAD_LETTER", "UNKNOWN"].includes(value.trim().toUpperCase());
}

function isConsistentPaymentEventPublishStatusType(status, statusType) {
  if (typeof statusType !== "string" || !statusType.trim()) {
    return false;
  }
  const normalizedStatus = status.trim().toUpperCase();
  const normalizedType = statusType.trim().toLowerCase();
  if (normalizedStatus === "SUCCESS") {
    return normalizedType === "success";
  }
  if (["FAILED", "DEAD_LETTER"].includes(normalizedStatus)) {
    return normalizedType === "danger";
  }
  return normalizedType === "warn";
}

function isConsistentPaymentEventPublishTimes(
  status,
  lastPublishedAt,
  nextRetryAt,
  lastRepublishOperator,
  lastRepublishedAt,
  createdAt
) {
  const normalizedStatus = status.trim().toUpperCase();
  const hasRepublishOperator = lastRepublishOperator != null;
  const hasRepublishedAt = lastRepublishedAt != null;
  if (hasRepublishOperator !== hasRepublishedAt
      || (hasRepublishOperator
        && (typeof lastRepublishOperator !== "string"
          || !lastRepublishOperator.trim()
          || !isValidDateTime(lastRepublishedAt)))) {
    return false;
  }
  if (normalizedStatus === "PENDING") {
    return lastPublishedAt == null
      && (nextRetryAt == null
        || (isValidDateTime(nextRetryAt)
          && (createdAt == null || (isValidDateTime(createdAt)
            && Date.parse(createdAt.trim().replace(" ", "T"))
              <= Date.parse(nextRetryAt.trim().replace(" ", "T"))))));
  }
  if (!isValidDateTime(lastPublishedAt)) {
    return false;
  }
  if (hasRepublishedAt
      && ((createdAt != null
        && (!isValidDateTime(createdAt)
          || Date.parse(createdAt.trim().replace(" ", "T"))
            > Date.parse(lastRepublishedAt.trim().replace(" ", "T"))))
        || Date.parse(lastRepublishedAt.trim().replace(" ", "T"))
          > Date.parse(lastPublishedAt.trim().replace(" ", "T")))) {
    return false;
  }
  if (createdAt != null
      && (!isValidDateTime(createdAt)
        || Date.parse(createdAt.trim().replace(" ", "T"))
          > Date.parse(lastPublishedAt.trim().replace(" ", "T")))) {
    return false;
  }
  if (normalizedStatus === "FAILED") {
    return isValidDateTime(nextRetryAt)
      && Date.parse(lastPublishedAt.trim().replace(" ", "T"))
        <= Date.parse(nextRetryAt.trim().replace(" ", "T"));
  }
  return nextRetryAt == null;
}

function isConsistentPaymentSuccessHttpStage(eventType, stage) {
  if (eventType !== "PAYMENT_SUCCESS") {
    return stage == null || stage === "" || stage === "PENDING";
  }
  return typeof stage === "string"
    && ["PENDING", "CLEARING_ACCEPTED", "COMPLETED"].includes(stage.trim().toUpperCase());
}

export function normalizePaymentEventListPage(data) {
  const normalizedPage = normalizePageResponse(data);
  return {
    ...normalizedPage,
    items: normalizedPage.items.map((item) => {
      if (!item || typeof item !== "object" || Array.isArray(item)
          || typeof item.eventNo !== "string"
          || !item.eventNo.trim()
          || typeof item.eventType !== "string"
          || !item.eventType.trim()
          || typeof item.paymentOrderId !== "string"
          || !item.paymentOrderId.trim()
          || typeof item.businessChainId !== "string"
          || !item.businessChainId.trim()
          || item.businessChainId.trim() !== `CHAIN-${item.paymentOrderId.trim()}`
          || !isValidPaymentSuccessFactIssue(item.successFactIssue)
          || typeof item.bizNo !== "string"
          || !item.bizNo.trim()
          || typeof item.eventTopic !== "string"
          || !item.eventTopic.trim()
          || typeof item.downstreamSystem !== "string"
          || !item.downstreamSystem.trim()
          || typeof item.publishStatus !== "string"
          || !item.publishStatus.trim()
          || !isKnownPaymentEventPublishStatus(item.publishStatus)
          || !isConsistentPaymentEventPublishStatusType(item.publishStatus, item.publishStatusType)
          || !isConsistentPaymentSuccessHttpStage(item.eventType, item.paymentSuccessHttpStage)
          || !isConsistentPaymentEventPublishTimes(
            item.publishStatus,
            item.lastPublishedAt,
            item.nextRetryAt,
            item.lastRepublishOperator,
            item.lastRepublishedAt,
            item.createdAt
          )
          || !Number.isInteger(item.retryCount)
          || item.retryCount < 0
          || !isValidPaymentEventPayload(item.eventPayload)
          || !isValidDateTime(item.createdAt)) {
        throw createBusinessError(
          { code: "PAYMENT-CLIENT-INVALID-EVENT-LIST" },
          "支付事件列表返回了无法确认的路由状态事实"
        );
      }
      return item;
    })
  };
}

export function normalizePaymentEventRepublishResult(
  data, expectedEventNo = "", requireExpectedEvent = true
) {
  try {
    const page = normalizePaymentEventListPage(requirePageResult(
      data,
      "PAYMENT-CLIENT-INVALID-EVENT-ACTION",
      "支付事件重发返回了无法确认的分页结果"
    ));
    if (requireExpectedEvent && expectedEventNo && !page.items.some((item) => (
      normalizeBusinessIdentifier(item.eventNo) === expectedEventNo
    ))) {
      throw createBusinessError(
        { code: "PAYMENT-CLIENT-INVALID-EVENT-ACTION" },
        "支付事件重发结果未包含被操作的事件"
      );
    }
    return page;
  } catch (error) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-EVENT-ACTION" },
      error.rawMessage || "支付事件重发返回了无法确认的事件事实"
    );
  }
}

export function matchesPaymentEventDetailSnapshot(snapshot, result) {
  return Boolean(snapshot
    && result
    && normalizeBusinessIdentifier(result.eventNo)
      === normalizeBusinessIdentifier(snapshot.eventNo)
    && normalizeBusinessIdentifier(result.eventType)
      === normalizeBusinessIdentifier(snapshot.eventType)
    && normalizeBusinessIdentifier(result.paymentOrderId)
      === normalizeBusinessIdentifier(snapshot.paymentOrderId)
    && (snapshot.businessChainId == null
      || normalizeBusinessIdentifier(result.businessChainId)
        === normalizeBusinessIdentifier(snapshot.businessChainId))
    && normalizeBusinessIdentifier(result.bizNo)
      === normalizeBusinessIdentifier(snapshot.bizNo)
    && normalizeBusinessIdentifier(result.standardOrderNo)
      === normalizeBusinessIdentifier(snapshot.standardOrderNo)
    && normalizeBusinessIdentifier(result.eventTopic)
      === normalizeBusinessIdentifier(snapshot.eventTopic)
    && normalizeBusinessIdentifier(result.downstreamSystem)
      === normalizeBusinessIdentifier(snapshot.downstreamSystem));
}

export function normalizePaymentRequestDetailResult(data, expectedRequestNo) {
  if (!data || typeof data !== "object" || Array.isArray(data)
      || typeof data.requestNo !== "string"
      || !data.requestNo.trim()
      || typeof data.paymentOrderId !== "string"
      || !data.paymentOrderId.trim()
      || typeof data.payScene !== "string"
      || !data.payScene.trim()
      || typeof data.businessChainId !== "string"
      || !data.businessChainId.trim()
      || data.businessChainId.trim() !== `CHAIN-${data.paymentOrderId.trim()}`
      || typeof data.requestStatus !== "string"
      || !data.requestStatus.trim()
      || !isConsistentPaymentRequestStatus(data.requestStatus, data.requestStatusType)
      || !isValidPaymentSuccessFactIssue(data.successFactIssue)
      || !isValidPaymentEventPayload(data.requestPayload)
      || !isValidOptionalPaymentPayload(data.responsePayload)
      || !isValidDateTime(data.createdAt)
      || !isConsistentPaymentRequestCompletedAt(
        data.requestStatus, data.completedAt, data.createdAt
      )
      || !matchesExpectedBusinessIdentifier(data.requestNo, expectedRequestNo)) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-PAYMENT-REQUEST-DETAIL" },
      "支付请求详情返回了无法确认的请求状态事实"
    );
  }
  return data;
}

function isConsistentPaymentRequestCompletedAt(status, completedAt, createdAt) {
  if (typeof status !== "string") {
    return false;
  }
  if (status.trim() === "成功") {
    return isValidDateTime(completedAt)
      && isValidDateTime(createdAt)
      && Date.parse(createdAt.trim().replace(" ", "T"))
        <= Date.parse(completedAt.trim().replace(" ", "T"));
  }
  return completedAt == null;
}

function isConsistentPaymentRequestStatus(status, statusType) {
  if (typeof status !== "string" || !status.trim()
      || typeof statusType !== "string" || !statusType.trim()) {
    return false;
  }
  const expectedType = {
    "成功": "success",
    "失败": "danger",
    "处理中": "warn",
    "等待回调": "warn",
    "渠道提交中": "warn",
    "待人工确认": "warn",
    "已关闭": "danger"
  }[status.trim()];
  return Boolean(expectedType) && statusType.trim().toLowerCase() === expectedType;
}

export function normalizePaymentRequestListPage(data) {
  const normalizedPage = normalizePageResponse(data);
  return {
    ...normalizedPage,
    items: normalizedPage.items.map((item) => {
      if (!item || typeof item !== "object" || Array.isArray(item)
          || typeof item.requestNo !== "string"
          || !item.requestNo.trim()
          || typeof item.paymentOrderId !== "string"
          || !item.paymentOrderId.trim()
          || typeof item.businessChainId !== "string"
          || !item.businessChainId.trim()
          || item.businessChainId.trim() !== `CHAIN-${item.paymentOrderId.trim()}`
          || typeof item.prepayOrderNo !== "string"
          || !item.prepayOrderNo.trim()
          || typeof item.payScene !== "string"
          || !item.payScene.trim()
          || typeof item.orderNo !== "string"
          || !item.orderNo.trim()
          || typeof item.paymentMethod !== "string"
          || !item.paymentMethod.trim()
          || typeof item.channelCode !== "string"
          || !item.channelCode.trim()
          || !isConsistentPaymentRequestStatus(item.requestStatus, item.requestStatusType)
          || !isValidPaymentSuccessFactIssue(item.successFactIssue)
          || !isValidPaymentEventPayload(item.requestPayload)
          || !isValidOptionalPaymentPayload(item.responsePayload)
          || !isValidDateTime(item.createdAt)) {
        throw createBusinessError(
          { code: "PAYMENT-CLIENT-INVALID-PAYMENT-REQUEST-LIST" },
          "支付请求列表返回了无法确认的关联状态事实"
        );
      }
      return item;
    })
  };
}

export function matchesPaymentRequestDetailSnapshot(snapshot, result) {
  return Boolean(snapshot
    && result
    && normalizeBusinessIdentifier(result.requestNo)
      === normalizeBusinessIdentifier(snapshot.requestNo)
    && normalizeBusinessIdentifier(result.paymentOrderId)
      === normalizeBusinessIdentifier(snapshot.paymentOrderId)
    && (snapshot.payScene == null
      || normalizeBusinessIdentifier(result.payScene)
        === normalizeBusinessIdentifier(snapshot.payScene))
    && (snapshot.businessChainId == null
      || normalizeBusinessIdentifier(result.businessChainId)
        === normalizeBusinessIdentifier(snapshot.businessChainId))
    && normalizeBusinessIdentifier(result.prepayOrderNo)
      === normalizeBusinessIdentifier(snapshot.prepayOrderNo)
    && normalizeBusinessIdentifier(result.orderNo)
      === normalizeBusinessIdentifier(snapshot.orderNo)
    && normalizeBusinessIdentifier(result.standardOrderNo)
      === normalizeBusinessIdentifier(snapshot.standardOrderNo)
    && normalizeBusinessIdentifier(result.billNo)
      === normalizeBusinessIdentifier(snapshot.billNo)
    && matchesPreservedBusinessIdentifier(snapshot.paymentMethod, result.paymentMethod)
    && normalizeBusinessIdentifier(result.routeNo)
      === normalizeBusinessIdentifier(snapshot.routeNo));
}

export function normalizePaymentFlowDetailResult(data, expectedFlowSource, expectedFlowNo) {
  const flowSource = typeof data?.flowSource === "string" ? data.flowSource.trim().toUpperCase() : "";
  const isBusinessEvent = flowSource === "BUSINESS_EVENT";
  if (!data || typeof data !== "object" || Array.isArray(data)
      || typeof data.flowSource !== "string"
      || !data.flowSource.trim()
      || typeof data.flowNo !== "string"
      || !data.flowNo.trim()
      || typeof data.paymentOrderId !== "string"
      || !data.paymentOrderId.trim()
      || typeof data.businessChainId !== "string"
      || !data.businessChainId.trim()
      || data.businessChainId.trim() !== `CHAIN-${data.paymentOrderId.trim()}`
      || typeof data.flowType !== "string"
      || !data.flowType.trim()
      || !isConsistentPaymentFlowIdentity(flowSource, data.flowType, data.flowTypeTag)
      || typeof data.businessStatus !== "string"
      || !data.businessStatus.trim()
      || !isKnownPaymentStatusType(data.businessStatusType)
      || !isValidPaymentSuccessFactIssue(data.successFactIssue)
      || !isConsistentPaymentFlowCompletedAt(
        flowSource, data.businessStatus, data.completedAt, data.createdAt
      )
      || !isValidDateTime(data.createdAt)
      || (isBusinessEvent
        && (typeof data.downstreamSystem !== "string"
          || !data.downstreamSystem.trim()
          || typeof data.eventTopic !== "string"
          || !data.eventTopic.trim()
          || !isKnownPaymentEventPublishStatus(data.publishStatus)
          || !Number.isInteger(data.retryCount)
          || data.retryCount < 0
          || !isValidPaymentEventPayload(data.requestPayload)))
      || (!isBusinessEvent && (data.publishStatus != null || data.retryCount != null))
      || (expectedFlowSource
        && normalizeBusinessIdentifier(data.flowSource) !== expectedFlowSource)
      || !matchesExpectedBusinessIdentifier(data.flowNo, expectedFlowNo)) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-PAYMENT-FLOW-DETAIL" },
      "支付流水详情返回了无法确认的类型状态事实"
    );
  }
  return data;
}

function isKnownPaymentFlowSource(value) {
  return typeof value === "string"
    && ["PAYMENT_ATTEMPT", "CHANNEL_NOTIFY", "ROUTE_RECORD", "BUSINESS_EVENT"].includes(value.trim().toUpperCase());
}

function isConsistentPaymentFlowIdentity(flowSource, flowType, flowTypeTag) {
  if (!isKnownPaymentFlowSource(flowSource)
      || typeof flowType !== "string"
      || !flowType.trim()
      || typeof flowTypeTag !== "string"
      || !flowTypeTag.trim()) {
    return false;
  }
  const metadata = {
    PAYMENT_ATTEMPT: { flowType: "支付尝试", flowTypeTag: "info" },
    CHANNEL_NOTIFY: { flowType: "渠道回调", flowTypeTag: "warn" },
    ROUTE_RECORD: { flowType: "路由记录", flowTypeTag: "info" },
    BUSINESS_EVENT: { flowType: "业务事件", flowTypeTag: "success" }
  }[flowSource.trim().toUpperCase()];
  return metadata.flowType === flowType.trim()
    && metadata.flowTypeTag === flowTypeTag.trim().toLowerCase();
}

function isConsistentPaymentFlowCompletedAt(flowSource, businessStatus, completedAt, createdAt) {
  if (flowSource !== "PAYMENT_ATTEMPT") {
    return completedAt == null;
  }
  if (typeof businessStatus !== "string") {
    return false;
  }
  if (businessStatus.trim() === "成功") {
    return isValidDateTime(completedAt)
      && isValidDateTime(createdAt)
      && Date.parse(createdAt.trim().replace(" ", "T"))
        <= Date.parse(completedAt.trim().replace(" ", "T"));
  }
  return completedAt == null;
}

function isKnownPaymentStatusType(value) {
  return typeof value === "string"
    && ["info", "success", "warn", "danger"].includes(value.trim().toLowerCase());
}

export function normalizePaymentFlowListPage(data) {
  const normalizedPage = normalizePageResponse(data);
  return {
    ...normalizedPage,
    items: normalizedPage.items.map((item) => {
      const flowSource = typeof item?.flowSource === "string" ? item.flowSource.trim().toUpperCase() : "";
      const isBusinessEvent = flowSource === "BUSINESS_EVENT";
      if (!item || typeof item !== "object" || Array.isArray(item)
          || !isConsistentPaymentFlowIdentity(flowSource, item.flowType, item.flowTypeTag)
          || typeof item.flowNo !== "string"
          || !item.flowNo.trim()
          || typeof item.paymentOrderId !== "string"
          || !item.paymentOrderId.trim()
          || typeof item.businessChainId !== "string"
          || !item.businessChainId.trim()
          || item.businessChainId.trim() !== `CHAIN-${item.paymentOrderId.trim()}`
          || typeof item.businessStatus !== "string"
          || !item.businessStatus.trim()
          || !isKnownPaymentStatusType(item.businessStatusType)
          || !isValidPaymentSuccessFactIssue(item.successFactIssue)
          || typeof item.summary !== "string"
          || !item.summary.trim()
          || !isValidDateTime(item.createdAt)
          || (isBusinessEvent
            && (typeof item.downstreamSystem !== "string"
              || !item.downstreamSystem.trim()
              || typeof item.eventTopic !== "string"
              || !item.eventTopic.trim()
              || !isKnownPaymentEventPublishStatus(item.publishStatus)
              || !Number.isInteger(item.retryCount)
              || item.retryCount < 0
              || !isValidPaymentEventPayload(item.requestPayload)))
          || (!isBusinessEvent && (item.publishStatus != null || item.retryCount != null))) {
        throw createBusinessError(
          { code: "PAYMENT-CLIENT-INVALID-PAYMENT-FLOW-LIST" },
          "支付流水列表返回了无法确认的来源状态事实"
        );
      }
      return item;
    })
  };
}

export function matchesPaymentFlowDetailSnapshot(snapshot, result) {
  return Boolean(snapshot
    && result
    && normalizeBusinessIdentifier(result.flowSource)
      === normalizeBusinessIdentifier(snapshot.flowSource)
    && normalizeBusinessIdentifier(result.flowNo)
      === normalizeBusinessIdentifier(snapshot.flowNo)
    && normalizeBusinessIdentifier(result.paymentOrderId)
      === normalizeBusinessIdentifier(snapshot.paymentOrderId)
    && (snapshot.businessChainId == null
      || normalizeBusinessIdentifier(result.businessChainId)
        === normalizeBusinessIdentifier(snapshot.businessChainId))
    && normalizeBusinessIdentifier(result.orderNo)
      === normalizeBusinessIdentifier(snapshot.orderNo)
    && normalizeBusinessIdentifier(result.standardOrderNo)
      === normalizeBusinessIdentifier(snapshot.standardOrderNo)
    && normalizeBusinessIdentifier(result.prepayOrderNo)
      === normalizeBusinessIdentifier(snapshot.prepayOrderNo)
    && (snapshot.payScene == null
      || normalizeBusinessIdentifier(result.payScene)
        === normalizeBusinessIdentifier(snapshot.payScene))
    && matchesPreservedBusinessIdentifier(snapshot.billNo, result.billNo)
    && matchesPreservedBusinessIdentifier(snapshot.paymentMethod, result.paymentMethod)
    && normalizeBusinessIdentifier(result.paymentRequestNo)
      === normalizeBusinessIdentifier(snapshot.paymentRequestNo)
    && normalizeBusinessIdentifier(result.routeNo)
      === normalizeBusinessIdentifier(snapshot.routeNo));
}

export function normalizePaymentRouteDetailResult(data, expectedRouteNo) {
  if (!data || typeof data !== "object" || Array.isArray(data)
      || typeof data.routeNo !== "string"
      || !data.routeNo.trim()
      || typeof data.requestNo !== "string"
      || !data.requestNo.trim()
      || typeof data.paymentOrderId !== "string"
      || !data.paymentOrderId.trim()
      || typeof data.businessChainId !== "string"
      || !data.businessChainId.trim()
      || data.businessChainId.trim() !== `CHAIN-${data.paymentOrderId.trim()}`
      || typeof data.orderNo !== "string"
      || !data.orderNo.trim()
      || typeof data.prepayOrderNo !== "string"
      || !data.prepayOrderNo.trim()
      || !Number.isFinite(normalizeAmount(data.amount))
      || typeof data.channelCode !== "string"
      || !data.channelCode.trim()
      || typeof data.routeRule !== "string"
      || !data.routeRule.trim()
      || !isValidOptionalPaymentPayload(data.requestPayload)
      || !isValidOptionalPaymentPayload(data.responsePayload)
      || typeof data.routeResult !== "string"
      || !data.routeResult.trim()
      || !isConsistentPaymentRouteResultType(data.routeResult, data.routeResultType)
      || !isValidPaymentSuccessFactIssue(data.successFactIssue)
      || !isValidDateTime(data.createdAt)
      || !matchesExpectedBusinessIdentifier(data.routeNo, expectedRouteNo)) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-PAYMENT-ROUTE-DETAIL" },
      "支付路由详情返回了无法确认的路由结果事实"
    );
  }
  return data;
}

function isConsistentPaymentRouteResultType(routeResult, routeResultType) {
  if (typeof routeResult !== "string" || !routeResult.trim()
      || typeof routeResultType !== "string" || !routeResultType.trim()) {
    return false;
  }
  const normalizedResult = routeResult.trim();
  const normalizedType = routeResultType.trim().toLowerCase();
  if (normalizedResult.includes("兜底")) {
    return normalizedType === "warn";
  }
  if (normalizedResult.includes("线下")) {
    return normalizedType === "info";
  }
  return normalizedType === "success";
}

export function normalizePaymentRouteListPage(data) {
  const normalizedPage = normalizePageResponse(data);
  return {
    ...normalizedPage,
    items: normalizedPage.items.map((item) => {
      if (!item || typeof item !== "object" || Array.isArray(item)
          || typeof item.routeNo !== "string"
          || !item.routeNo.trim()
          || typeof item.paymentOrderId !== "string"
          || !item.paymentOrderId.trim()
          || typeof item.businessChainId !== "string"
          || !item.businessChainId.trim()
          || item.businessChainId.trim() !== `CHAIN-${item.paymentOrderId.trim()}`
          || !Number.isFinite(normalizeAmount(item.amount))
          || typeof item.channelCode !== "string"
          || !item.channelCode.trim()
          || typeof item.routeRule !== "string"
          || !item.routeRule.trim()
          || !isValidOptionalPaymentPayload(item.requestPayload)
          || !isValidOptionalPaymentPayload(item.responsePayload)
          || !isConsistentPaymentRouteResultType(item.routeResult, item.routeResultType)
          || !isValidPaymentSuccessFactIssue(item.successFactIssue)
          || !isValidDateTime(item.createdAt)) {
        throw createBusinessError(
          { code: "PAYMENT-CLIENT-INVALID-PAYMENT-ROUTE-LIST" },
          "支付路由列表返回了无法确认的路由结果事实"
        );
      }
      return item;
    })
  };
}

export function matchesPaymentRouteDetailSnapshot(snapshot, result) {
  return Boolean(snapshot
    && result
    && normalizeBusinessIdentifier(result.routeNo)
      === normalizeBusinessIdentifier(snapshot.routeNo)
    && normalizeBusinessIdentifier(result.paymentOrderId)
      === normalizeBusinessIdentifier(snapshot.paymentOrderId)
    && (snapshot.businessChainId == null
      || normalizeBusinessIdentifier(result.businessChainId)
        === normalizeBusinessIdentifier(snapshot.businessChainId))
    && normalizeBusinessIdentifier(result.requestNo)
      === normalizeBusinessIdentifier(snapshot.requestNo)
    && normalizeBusinessIdentifier(result.orderNo)
      === normalizeBusinessIdentifier(snapshot.orderNo)
    && matchesPreservedBusinessIdentifier(snapshot.paymentMethod, result.paymentMethod)
    && matchesPreservedBusinessIdentifier(snapshot.customerName, result.customerName)
    && normalizeAmountForComparison(result.amount)
      === normalizeAmountForComparison(snapshot.amount)
    && (snapshot.channelCode == null
      || normalizeBusinessIdentifier(result.channelCode)
        === normalizeBusinessIdentifier(snapshot.channelCode)));
}

function isConsistentCashierSessionStatus(status, statusType) {
  if (typeof status !== "string" || !status.trim()
      || typeof statusType !== "string" || !statusType.trim()) {
    return false;
  }
  if (status.trim() === "支付中" && statusType.trim().toLowerCase() === "info") {
    return true;
  }
  const expectedType = {
    "待支付": "warn",
    "待风控复核": "warn",
    "风控拦截": "danger",
    "风控拒绝": "danger",
    "支付中": "warn",
    "待回调": "warn",
    "结果待确认": "danger",
    "成功事实异常": "danger",
    "支付成功": "success",
    "已完成": "success",
    "已关闭": "danger",
    "已失效": "danger"
  }[status.trim()];
  return Boolean(expectedType) && statusType.trim().toLowerCase() === expectedType;
}

function isConsistentCashierSessionTimes(createdAt, expiresAt) {
  if (!isValidDateTime(createdAt) || !isValidDateTime(expiresAt)) {
    return false;
  }
  return Date.parse(expiresAt.trim().replace(" ", "T"))
    >= Date.parse(createdAt.trim().replace(" ", "T"));
}

export function normalizeCashierSessionListPage(data) {
  const normalizedPage = normalizePageResponse(data);
  return {
    ...normalizedPage,
    items: normalizedPage.items.map((item) => {
      if (!item || typeof item !== "object" || Array.isArray(item)
          || typeof item.sessionNo !== "string"
          || !item.sessionNo.trim()
          || typeof item.prepayOrderNo !== "string"
          || !item.prepayOrderNo.trim()
          || typeof item.paymentOrderId !== "string"
          || !item.paymentOrderId.trim()
          || typeof item.businessChainId !== "string"
          || !item.businessChainId.trim()
          || item.businessChainId.trim() !== `CHAIN-${item.paymentOrderId.trim()}`
          || typeof item.orderNo !== "string"
          || !item.orderNo.trim()
          || typeof item.billNo !== "string"
          || !item.billNo.trim()
          || typeof item.customerName !== "string"
          || !item.customerName.trim()
          || typeof item.cashierTitle !== "string"
          || !item.cashierTitle.trim()
          || typeof item.payScene !== "string"
          || !item.payScene.trim()
          || typeof item.terminal !== "string"
          || !item.terminal.trim()
          || !Number.isFinite(normalizeAmount(item.amount))
          || !isConsistentCashierSessionStatus(item.sessionStatus, item.sessionStatusType)
          || !isConsistentCashierSessionTimes(item.createdAt, item.expiresAt)) {
        throw createBusinessError(
          { code: "PAYMENT-CLIENT-INVALID-CASHIER-SESSION-LIST" },
          "收银台会话列表返回了无法确认的金额状态事实"
        );
      }
      return item;
    })
  };
}

export function normalizeCashierSessionDetailResult(data, expectedSessionNo) {
  if (!data || typeof data !== "object" || Array.isArray(data)
      || typeof data.sessionNo !== "string"
      || !data.sessionNo.trim()
      || typeof data.prepayOrderNo !== "string"
      || !data.prepayOrderNo.trim()
      || (data.paymentOrderId != null
        && (typeof data.paymentOrderId !== "string" || !data.paymentOrderId.trim()))
      || (data.paymentOrderId != null
        && (typeof data.businessChainId !== "string"
          || !data.businessChainId.trim()
          || data.businessChainId.trim() !== `CHAIN-${data.paymentOrderId.trim()}`))
      || typeof data.orderNo !== "string"
      || !data.orderNo.trim()
      || typeof data.billNo !== "string"
      || !data.billNo.trim()
      || typeof data.customerName !== "string"
      || !data.customerName.trim()
      || typeof data.cashierTitle !== "string"
      || !data.cashierTitle.trim()
      || typeof data.payScene !== "string"
      || !data.payScene.trim()
      || typeof data.terminal !== "string"
      || !data.terminal.trim()
      || !Number.isFinite(normalizeAmount(data.amount))
      || typeof data.sessionStatus !== "string"
      || !data.sessionStatus.trim()
      || !isConsistentCashierSessionStatus(data.sessionStatus, data.sessionStatusType)
      || !isConsistentCashierSessionTimes(data.createdAt, data.expiresAt)
      || (expectedSessionNo
        && !matchesExpectedBusinessIdentifier(data.sessionNo, normalizeBusinessIdentifier(expectedSessionNo)))) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-CASHIER-SESSION-DETAIL" },
      "收银台会话详情返回了无法确认的金额状态事实"
    );
  }
  return data;
}

export function matchesCashierSessionDetailSnapshot(snapshot, result) {
  return Boolean(snapshot
    && result
    && normalizeBusinessIdentifier(result.sessionNo)
      === normalizeBusinessIdentifier(snapshot.sessionNo)
    && normalizeBusinessIdentifier(result.prepayOrderNo)
      === normalizeBusinessIdentifier(snapshot.prepayOrderNo)
    && normalizeBusinessIdentifier(result.paymentOrderId)
      === normalizeBusinessIdentifier(snapshot.paymentOrderId)
    && (snapshot.businessChainId == null
      || normalizeBusinessIdentifier(result.businessChainId)
        === normalizeBusinessIdentifier(snapshot.businessChainId))
    && normalizeBusinessIdentifier(result.orderNo)
      === normalizeBusinessIdentifier(snapshot.orderNo)
    && matchesPreservedBusinessIdentifier(snapshot.billNo, result.billNo)
    && matchesPreservedBusinessIdentifier(snapshot.customerName, result.customerName)
    && normalizeAmountForComparison(result.amount)
      === normalizeAmountForComparison(snapshot.amount));
}

export function normalizeOrderDetailResult(data, expectedOrderNo) {
  const orderAmount = normalizeNonNegativeAmountForComparison(data?.orderAmount);
  const paidAmount = normalizeNonNegativeAmountForComparison(data?.paidAmount);
  const outstandingAmount = normalizeNonNegativeAmountForComparison(data?.outstandingAmount);
  if (!data || typeof data !== "object" || Array.isArray(data)
      || typeof data.orderNo !== "string"
      || !data.orderNo.trim()
      || typeof data.customerName !== "string"
      || !data.customerName.trim()
      || typeof data.serviceType !== "string"
      || !data.serviceType.trim()
      || typeof data.workerName !== "string"
      || !data.workerName.trim()
      || !orderAmount
      || orderAmount === "0.00"
      || !paidAmount
      || !outstandingAmount
      || addNormalizedAmounts(paidAmount, outstandingAmount) !== orderAmount
      || typeof data.orderStatus !== "string"
      || !data.orderStatus.trim()
      || !isKnownPaymentStatusType(data.orderStatusType)
      || typeof data.fulfillmentStatus !== "string"
      || !data.fulfillmentStatus.trim()
      || !isKnownPaymentStatusType(data.fulfillmentStatusType)
      || !isConsistentOptionalPaymentOrderStatus(data.latestPaymentStatus, data.latestPaymentStatusType)
      || !isConsistentOptionalCashierStatus(data.latestCashierStatus, data.latestCashierStatusType)
      || !isValidDateTime(data.createdAt)
      || (expectedOrderNo
        && !matchesExpectedBusinessIdentifier(data.orderNo, normalizeBusinessIdentifier(expectedOrderNo)))) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-ORDER-DETAIL" },
      "订单详情返回了无法确认的金额状态事实"
    );
  }
  return data;
}

function isValidOptionalStatusPair(status, statusType) {
  if (status == null && statusType == null) {
    return true;
  }
  if (typeof status !== "string"
      || !status.trim()
      || !isKnownPaymentStatusType(statusType)) {
    return false;
  }
  const normalizedStatus = status.trim();
  const normalizedType = statusType.trim().toLowerCase();
  if (isPaymentAttemptSuccessStatus(normalizedStatus)) {
    return normalizedType === "success";
  }
  if (isPaymentAttemptFailureStatus(normalizedStatus)) {
    return normalizedType === "danger";
  }
  return true;
}

function isConsistentOptionalPaymentOrderStatus(status, statusType) {
  if (status == null && statusType == null) {
    return true;
  }
  return isConsistentPaymentOrderStatus(status, statusType);
}

function isConsistentOptionalCashierStatus(status, statusType) {
  if (status == null && statusType == null) {
    return true;
  }
  if (typeof status !== "string" || !status.trim()
      || typeof statusType !== "string" || !statusType.trim()) {
    return false;
  }
  const expectedType = {
    "待支付": "warn",
    "支付中": "info",
    "结果待确认": "danger",
    "待回调": "warn",
    "支付成功": "success",
    "已关闭": "danger",
    "已失效": "danger",
    "待风控复核": "warn",
    "风控拦截": "danger",
    "风控拒绝": "danger"
  }[status.trim()];
  return Boolean(expectedType) && statusType.trim().toLowerCase() === expectedType;
}

export function normalizeOrderListPage(data) {
  const normalizedPage = normalizePageResponse(data);
  return {
    ...normalizedPage,
    items: normalizedPage.items.map((item) => {
      const orderAmount = normalizeNonNegativeAmountForComparison(item?.orderAmount);
      const paidAmount = normalizeNonNegativeAmountForComparison(item?.paidAmount);
      if (!item || typeof item !== "object" || Array.isArray(item)
          || typeof item.orderNo !== "string"
          || !item.orderNo.trim()
          || typeof item.customerName !== "string"
          || !item.customerName.trim()
          || typeof item.serviceType !== "string"
          || !item.serviceType.trim()
          || typeof item.workerName !== "string"
          || !item.workerName.trim()
          || !orderAmount
          || orderAmount === "0.00"
          || !paidAmount
          || !isNormalizedAmountAtMost(paidAmount, orderAmount)
          || typeof item.orderStatus !== "string"
          || !item.orderStatus.trim()
          || !isKnownPaymentStatusType(item.orderStatusType)
          || typeof item.fulfillmentStatus !== "string"
          || !item.fulfillmentStatus.trim()
          || !isKnownPaymentStatusType(item.fulfillmentStatusType)
          || !isConsistentOptionalPaymentOrderStatus(item.latestPaymentStatus, item.latestPaymentStatusType)
          || !isConsistentOptionalCashierStatus(item.latestCashierStatus, item.latestCashierStatusType)
          || !isValidDateTime(item.createdAt)) {
        throw createBusinessError(
          { code: "PAYMENT-CLIENT-INVALID-ORDER-LIST" },
          "订单列表返回了无法确认的金额状态事实"
        );
      }
      return item;
    })
  };
}

export function matchesOrderDetailSnapshot(snapshot, result) {
  return Boolean(snapshot
    && result
    && normalizeBusinessIdentifier(result.orderNo)
      === normalizeBusinessIdentifier(snapshot.orderNo)
    && normalizeAmountForComparison(result.orderAmount)
      === normalizeAmountForComparison(snapshot.orderAmount)
    && (snapshot.latestPayScene == null
      || normalizeBusinessIdentifier(result.latestPayScene)
        === normalizeBusinessIdentifier(snapshot.latestPayScene)));
}

export function normalizeBillDetailResult(data, expectedBillNo) {
  const billAmount = normalizeNonNegativeAmountForComparison(data?.billAmount);
  const paidAmount = normalizeNonNegativeAmountForComparison(data?.paidAmount);
  const unpaidAmount = normalizeNonNegativeAmountForComparison(data?.unpaidAmount);
  if (!data || typeof data !== "object" || Array.isArray(data)
      || typeof data.billNo !== "string"
      || !data.billNo.trim()
      || typeof data.orderNo !== "string"
      || !data.orderNo.trim()
      || !billAmount
      || billAmount === "0.00"
      || !paidAmount
      || !unpaidAmount
      || addNormalizedAmounts(paidAmount, unpaidAmount) !== billAmount
      || !isConsistentBillStatusType(data.billStatus, data.billStatusType)
      || !isConsistentOptionalPaymentOrderStatus(data.latestPaymentStatus, data.latestPaymentStatusType)
      || !isConsistentOptionalCashierStatus(data.latestCashierStatus, data.latestCashierStatusType)
      || !isConsistentBillTimes(data.createdAt, data.dueAt)
      || (expectedBillNo
        && !matchesExpectedBusinessIdentifier(data.billNo, normalizeBusinessIdentifier(expectedBillNo)))) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-BILL-DETAIL" },
      "账单详情返回了无法确认的金额状态事实"
    );
  }
  return data;
}

function isConsistentBillStatusType(billStatus, billStatusType) {
  if (typeof billStatus !== "string" || !billStatus.trim()
      || typeof billStatusType !== "string" || !billStatusType.trim()) {
    return false;
  }
  const expectedType = {
    "已支付": "success",
    "已结清": "success",
    "待支付": "warn",
    "部分支付": "warn",
    "已关闭": "danger"
  }[billStatus.trim()];
  return Boolean(expectedType) && billStatusType.trim().toLowerCase() === expectedType;
}

function isConsistentBillTimes(createdAt, dueAt) {
  if (!isValidDateTime(createdAt) || !isValidDateTime(dueAt)) {
    return false;
  }
  return Date.parse(dueAt.trim().replace(" ", "T"))
    >= Date.parse(createdAt.trim().replace(" ", "T"));
}

export function normalizeBillListPage(data) {
  const normalizedPage = normalizePageResponse(data);
  return {
    ...normalizedPage,
    items: normalizedPage.items.map((item) => {
      const billAmount = normalizeNonNegativeAmountForComparison(item?.billAmount);
      const paidAmount = normalizeNonNegativeAmountForComparison(item?.paidAmount);
      const unpaidAmount = normalizeNonNegativeAmountForComparison(item?.unpaidAmount);
      if (!item || typeof item !== "object" || Array.isArray(item)
          || typeof item.billNo !== "string"
          || !item.billNo.trim()
          || typeof item.orderNo !== "string"
          || !item.orderNo.trim()
          || !billAmount
          || billAmount === "0.00"
          || !paidAmount
          || !unpaidAmount
          || addNormalizedAmounts(paidAmount, unpaidAmount) !== billAmount
          || !isConsistentBillStatusType(item.billStatus, item.billStatusType)
          || !isConsistentOptionalPaymentOrderStatus(item.latestPaymentStatus, item.latestPaymentStatusType)
          || !isConsistentOptionalCashierStatus(item.latestCashierStatus, item.latestCashierStatusType)
          || !isConsistentBillTimes(item.createdAt, item.dueAt)) {
        throw createBusinessError(
          { code: "PAYMENT-CLIENT-INVALID-BILL-LIST" },
          "账单列表返回了无法确认的金额状态事实"
        );
      }
      return item;
    })
  };
}

export function matchesBillDetailSnapshot(snapshot, result) {
  return Boolean(snapshot
    && result
    && normalizeBusinessIdentifier(result.billNo)
      === normalizeBusinessIdentifier(snapshot.billNo)
    && normalizeBusinessIdentifier(result.orderNo)
      === normalizeBusinessIdentifier(snapshot.orderNo)
    && normalizeBusinessIdentifier(result.standardOrderNo)
      === normalizeBusinessIdentifier(snapshot.standardOrderNo)
    && normalizeAmountForComparison(result.billAmount)
      === normalizeAmountForComparison(snapshot.billAmount)
    && (snapshot.paidAmount == null
      || normalizeNonNegativeAmountForComparison(result.paidAmount)
        === normalizeNonNegativeAmountForComparison(snapshot.paidAmount))
    && (snapshot.unpaidAmount == null
      || normalizeNonNegativeAmountForComparison(result.unpaidAmount)
        === normalizeNonNegativeAmountForComparison(snapshot.unpaidAmount))
    && (snapshot.latestPayScene == null
      || normalizeBusinessIdentifier(result.latestPayScene)
        === normalizeBusinessIdentifier(snapshot.latestPayScene)));
}

export function normalizePaymentIssueDetailResult(data, expectedIssueNo) {
  if (!data || typeof data !== "object" || Array.isArray(data)
      || typeof data.issueNo !== "string"
      || !data.issueNo.trim()
      || typeof data.paymentOrderId !== "string"
      || !data.paymentOrderId.trim()
      || typeof data.businessChainId !== "string"
      || !data.businessChainId.trim()
      || data.businessChainId.trim() !== `CHAIN-${data.paymentOrderId.trim()}`
      || typeof data.orderNo !== "string"
      || !data.orderNo.trim()
      || typeof data.customerName !== "string"
      || !data.customerName.trim()
      || typeof data.paymentMethod !== "string"
      || !data.paymentMethod.trim()
      || typeof data.channelCode !== "string"
      || !data.channelCode.trim()
      || typeof data.issueType !== "string"
      || !data.issueType.trim()
      || typeof data.severity !== "string"
      || !data.severity.trim()
      || typeof data.handlingStatus !== "string"
      || !data.handlingStatus.trim()
      || !isConsistentPaymentIssueDetailStatus(data)
      || ["issueSummary", "rootCauseHint", "recommendedAction", "recommendedRoute",
        "responsibilityHint", "alertChannelSummary", "alertDeliverySummary",
        "latestAlertProviderSummary", "latestAlertReceiptSummary", "slaTimeLeft",
        "escalationSuggestion", "latestActionSummary"]
        .some((field) => typeof data[field] !== "string" || !data[field].trim())
      || typeof data.assignee !== "string"
      || !data.assignee.trim()
      || typeof data.responsibilityGroup !== "string"
      || !data.responsibilityGroup.trim()
      || typeof data.alertReceiver !== "string"
      || !data.alertReceiver.trim()
      || !isConsistentPaymentIssueTimes(data.createdAt, data.latestActionAt)
      || (expectedIssueNo
        && !matchesExpectedBusinessIdentifier(data.issueNo, normalizeBusinessIdentifier(expectedIssueNo)))) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-DETAIL" },
      "支付交易异常详情返回了无法确认的类型等级状态事实"
    );
  }
  return data;
}

function isConsistentPaymentIssueDetailStatus(data) {
  return isConsistentPaymentIssueStatus(data.issueType, data.issueTypeTag, {
    "待回调未收口": "danger",
    "回调处理待跟进": "warn",
    "下游事件发布失败": "info",
    "退款提交结果待核查": "danger",
    "命中停用渠道": "warn",
    "订单成功收款超额": "danger"
  })
    && isConsistentPaymentIssueStatus(data.severity, data.severityType, { P1: "danger", P2: "warn" })
    && isConsistentPaymentOrderStatus(data.paymentStatus, data.paymentStatusType)
    && isConsistentPaymentIssueStatus(data.handlingStatus, data.handlingStatusType, {
      "待分派": "warn",
      "已分派": "info",
      "跟进中": "warn",
      "已处理": "success",
      "已备注": "info"
    })
    && isKnownPaymentStatusType(data.responsibilityGroupType)
    && isConsistentPaymentIssueStatus(data.alertStatus, data.alertStatusType, {
      "未通知": "info",
      "已生成": "info",
      "已派发": "success",
      "部分失败": "warn",
      "派发失败": "danger"
    })
    && isConsistentPaymentIssueStatus(data.alertAckStatus, data.alertAckStatusType, {
      "无需回执": "info",
      "待确认": "warn",
      "已确认": "success"
    })
    && isConsistentPaymentIssueStatus(data.slaStatus, data.slaStatusType, {
      "SLA内": "success",
      "已超时": "danger"
    })
    && isConsistentPaymentIssueStatus(data.escalationStatus, data.escalationStatusType, {
      "当班跟进": "info",
      "升级值班负责人": "danger",
      "升级支付运营负责人": "danger"
    });
}

function isConsistentPaymentIssueStatus(value, type, expectedTypes) {
  if (typeof value !== "string" || !value.trim()
      || typeof type !== "string" || !type.trim()) {
    return false;
  }
  return expectedTypes[value.trim()] === type.trim().toLowerCase();
}

export function normalizePaymentIssueListPage(data) {
  const normalizedPage = normalizePageResponse(data);
  const issueTypeTypes = {
    "待回调未收口": "danger",
    "回调处理待跟进": "warn",
    "下游事件发布失败": "info",
    "退款提交结果待核查": "danger",
    "命中停用渠道": "warn",
    "订单成功收款超额": "danger"
  };
  const severityTypes = { P1: "danger", P2: "warn" };
  const handlingStatusTypes = {
    "待分派": "warn",
    "已分派": "info",
    "跟进中": "warn",
    "已处理": "success",
    "已备注": "info"
  };
  const alertStatusTypes = {
    "未通知": "info",
    "已生成": "info",
    "已派发": "success",
    "部分失败": "warn",
    "派发失败": "danger"
  };
  const ackStatusTypes = { "无需回执": "info", "待确认": "warn", "已确认": "success" };
  const receiptQueryStates = new Set(["QUERYING", "EXHAUSTED", "INITIAL_WAIT", "BACKING_OFF", "DUE"]);
  const slaStatusTypes = { "SLA内": "success", "已超时": "danger" };
  const escalationStatusTypes = { "当班跟进": "info", "升级值班负责人": "danger", "升级支付运营负责人": "danger" };
  return {
    ...normalizedPage,
    items: normalizedPage.items.map((item) => {
      if (!item || typeof item !== "object" || Array.isArray(item)
          || typeof item.issueNo !== "string"
          || !item.issueNo.trim()
          || typeof item.paymentOrderId !== "string"
          || !item.paymentOrderId.trim()
          || typeof item.businessChainId !== "string"
          || !item.businessChainId.trim()
          || item.businessChainId.trim() !== `CHAIN-${item.paymentOrderId.trim()}`
          || typeof item.orderNo !== "string"
          || !item.orderNo.trim()
          || typeof item.customerName !== "string"
          || !item.customerName.trim()
          || typeof item.paymentMethod !== "string"
          || !item.paymentMethod.trim()
          || typeof item.channelCode !== "string"
          || !item.channelCode.trim()
          || !isConsistentPaymentIssueStatus(item.issueType, item.issueTypeTag, issueTypeTypes)
          || !isConsistentPaymentIssueStatus(item.severity, item.severityType, severityTypes)
          || !isConsistentPaymentOrderStatus(item.paymentStatus, item.paymentStatusType)
          || !isConsistentPaymentIssueStatus(item.handlingStatus, item.handlingStatusType, handlingStatusTypes)
          || typeof item.assignee !== "string"
          || !item.assignee.trim()
          || typeof item.responsibilityGroup !== "string"
          || !item.responsibilityGroup.trim()
          || typeof item.responsibilityGroupType !== "string"
          || !item.responsibilityGroupType.trim()
          || !isConsistentPaymentIssueStatus(item.alertStatus, item.alertStatusType, alertStatusTypes)
          || !isConsistentPaymentIssueStatus(item.alertAckStatus, item.alertAckStatusType, ackStatusTypes)
          || typeof item.alertReceiver !== "string"
          || !item.alertReceiver.trim()
          || !isConsistentPaymentIssueStatus(item.slaStatus, item.slaStatusType, slaStatusTypes)
          || !isConsistentPaymentIssueStatus(item.escalationStatus, item.escalationStatusType, escalationStatusTypes)
          || ["issueSummary", "rootCauseHint", "recommendedAction", "recommendedRoute",
            "responsibilityHint", "alertChannelSummary", "alertDeliverySummary",
            "latestAlertProviderSummary", "latestAlertReceiptSummary", "slaTimeLeft",
            "escalationSuggestion", "latestActionSummary"]
            .some((field) => typeof item[field] !== "string" || !item[field].trim())) {
        throw createBusinessError(
          { code: "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-LIST" },
          "支付交易异常列表返回了无法确认的风险状态事实"
        );
      }
      if (!isConsistentPaymentIssueTimes(item.createdAt, item.latestActionAt)) {
        throw createBusinessError(
          { code: "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-LIST" },
          "支付交易异常列表返回了无法确认的处理时间事实"
        );
      }
      return item;
    })
  };
}

function isConsistentPaymentIssueTimes(createdAt, latestActionAt) {
  if (!isValidDateTime(createdAt)) {
    return false;
  }
  if (latestActionAt == null) {
    return true;
  }
  return isValidDateTime(latestActionAt)
    && Date.parse(createdAt.trim().replace(" ", "T"))
      <= Date.parse(latestActionAt.trim().replace(" ", "T"));
}

export function matchesPaymentIssueDetailSnapshot(snapshot, result) {
  return Boolean(snapshot
    && result
    && normalizeBusinessIdentifier(result.issueNo)
      === normalizeBusinessIdentifier(snapshot.issueNo)
    && normalizeBusinessIdentifier(result.paymentOrderId)
      === normalizeBusinessIdentifier(snapshot.paymentOrderId)
    && (snapshot.businessChainId == null
      || normalizeBusinessIdentifier(result.businessChainId)
        === normalizeBusinessIdentifier(snapshot.businessChainId))
    && normalizeBusinessIdentifier(result.orderNo)
      === normalizeBusinessIdentifier(snapshot.orderNo)
    && matchesPreservedBusinessIdentifier(snapshot.customerName, result.customerName)
    && matchesPreservedBusinessIdentifier(snapshot.paymentMethod, result.paymentMethod)
    && (snapshot.channelCode == null
      || normalizeBusinessIdentifier(result.channelCode)
        === normalizeBusinessIdentifier(snapshot.channelCode))
    && normalizeBusinessIdentifier(result.issueType)
      === normalizeBusinessIdentifier(snapshot.issueType));
}

export function normalizePaymentIssueAlertDetailResult(data, expectedAlertNo) {
  const alertStatusTypes = {
    "已生成": "info",
    "已派发": "success",
    "部分失败": "warn",
    "派发失败": "danger"
  };
  const ackStatusTypes = { "无需回执": "info", "待确认": "warn", "已确认": "success" };
  const receiptQueryStates = new Set(["QUERYING", "EXHAUSTED", "INITIAL_WAIT", "BACKING_OFF", "DUE"]);
  if (!data || typeof data !== "object" || Array.isArray(data)
      || typeof data.alertNo !== "string"
      || !data.alertNo.trim()
      || typeof data.paymentOrderId !== "string"
      || !data.paymentOrderId.trim()
      || typeof data.businessChainId !== "string"
      || !data.businessChainId.trim()
      || data.businessChainId.trim() !== `CHAIN-${data.paymentOrderId.trim()}`
      || typeof data.issueNo !== "string"
      || !data.issueNo.trim()
      || typeof data.issueType !== "string"
      || !data.issueType.trim()
      || typeof data.severity !== "string"
      || !data.severity.trim()
      || typeof data.responsibilityGroup !== "string"
      || !data.responsibilityGroup.trim()
      || typeof data.alertChannel !== "string"
      || !data.alertChannel.trim()
      || !isConsistentPaymentIssueAlertSource(data.alertChannel, data.sourceAlertNo)
      || typeof data.receiver !== "string"
      || !data.receiver.trim()
      || typeof data.alertStatus !== "string"
      || !data.alertStatus.trim()
      || typeof data.ackStatus !== "string"
      || !data.ackStatus.trim()
      || typeof data.alertContent !== "string"
      || !data.alertContent.trim()
      || typeof data.triggeredBy !== "string"
      || !data.triggeredBy.trim()
      || !isConsistentPaymentIssueStatus(data.alertStatus, data.alertStatusType, alertStatusTypes)
      || !isConsistentPaymentIssueStatus(data.ackStatus, data.ackStatusType, ackStatusTypes)
      || !isConsistentPaymentIssueAlertAcknowledgement(
        data.ackStatus, data.ackOperator, data.ackAt, data.createdAt
      )
      || !Number.isInteger(data.receiptQueryCount)
      || data.receiptQueryCount < 0
      || (data.receiptQueryState != null
        && !receiptQueryStates.has(data.receiptQueryState))
      || !isConsistentPaymentIssueAlertQueryTimes(
        data.createdAt, data.lastReceiptQueryAt, data.nextReceiptQueryAt
      )
      || (expectedAlertNo
        && !matchesExpectedBusinessIdentifier(data.alertNo, normalizeBusinessIdentifier(expectedAlertNo)))) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-ALERT-DETAIL" },
      "支付异常告警详情返回了无法确认的告警回执状态事实"
    );
  }
  return data;
}

export function normalizePaymentIssueAlertListPage(data) {
  const normalizedPage = normalizePageResponse(data);
  const alertStatusTypes = {
    "已生成": "info",
    "已派发": "success",
    "部分失败": "warn",
    "派发失败": "danger"
  };
  const ackStatusTypes = { "无需回执": "info", "待确认": "warn", "已确认": "success" };
  const receiptQueryStates = new Set(["QUERYING", "EXHAUSTED", "INITIAL_WAIT", "BACKING_OFF", "DUE"]);
  return {
    ...normalizedPage,
    items: normalizedPage.items.map((item) => {
      if (!item || typeof item !== "object" || Array.isArray(item)
          || typeof item.alertNo !== "string"
          || !item.alertNo.trim()
          || typeof item.issueNo !== "string"
          || !item.issueNo.trim()
          || typeof item.paymentOrderId !== "string"
          || !item.paymentOrderId.trim()
          || typeof item.businessChainId !== "string"
          || !item.businessChainId.trim()
          || item.businessChainId.trim() !== `CHAIN-${item.paymentOrderId.trim()}`
          || typeof item.issueType !== "string"
          || !item.issueType.trim()
          || typeof item.severity !== "string"
          || !item.severity.trim()
          || typeof item.responsibilityGroup !== "string"
          || !item.responsibilityGroup.trim()
          || typeof item.alertChannel !== "string"
          || !item.alertChannel.trim()
          || !isConsistentPaymentIssueAlertSource(item.alertChannel, item.sourceAlertNo)
          || typeof item.receiver !== "string"
          || !item.receiver.trim()
          || !isConsistentPaymentIssueStatus(item.alertStatus, item.alertStatusType, alertStatusTypes)
          || !isConsistentPaymentIssueStatus(item.ackStatus, item.ackStatusType, ackStatusTypes)
          || !isConsistentPaymentIssueAlertAcknowledgement(
            item.ackStatus, item.ackOperator, item.ackAt, item.createdAt
          )
          || typeof item.alertContent !== "string"
          || !item.alertContent.trim()
          || !Number.isInteger(item.receiptQueryCount)
          || item.receiptQueryCount < 0
          || (item.receiptQueryState != null
            && !receiptQueryStates.has(item.receiptQueryState))
          || !isConsistentPaymentIssueAlertQueryTimes(
            item.createdAt, item.lastReceiptQueryAt, item.nextReceiptQueryAt
          )) {
        throw createBusinessError(
          { code: "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-ALERT-LIST" },
          "支付异常告警列表返回了无法确认的派发回执事实"
        );
      }
      return item;
    })
  };
}

function isConsistentPaymentIssueAlertQueryTimes(createdAt, lastReceiptQueryAt, nextReceiptQueryAt) {
  if (!isValidDateTime(createdAt)) {
    return false;
  }
  const createdTimestamp = Date.parse(createdAt.trim().replace(" ", "T"));
  const hasLastQuery = lastReceiptQueryAt != null;
  const hasNextQuery = nextReceiptQueryAt != null;
  if ((hasLastQuery && !isValidDateTime(lastReceiptQueryAt))
      || (hasNextQuery && !isValidDateTime(nextReceiptQueryAt))) {
    return false;
  }
  const lastQueryTimestamp = hasLastQuery
    ? Date.parse(lastReceiptQueryAt.trim().replace(" ", "T"))
    : createdTimestamp;
  const nextQueryTimestamp = hasNextQuery
    ? Date.parse(nextReceiptQueryAt.trim().replace(" ", "T"))
    : null;
  return lastQueryTimestamp >= createdTimestamp
    && (nextQueryTimestamp == null || nextQueryTimestamp >= lastQueryTimestamp);
}

function isConsistentPaymentIssueAlertAcknowledgement(ackStatus, ackOperator, ackAt, createdAt) {
  const hasOperator = ackOperator != null;
  const hasAcknowledgedAt = ackAt != null;
  if (hasOperator !== hasAcknowledgedAt) {
    return false;
  }
  if (ackStatus !== "已确认") {
    return !hasOperator;
  }
  return typeof ackOperator === "string"
    && Boolean(ackOperator.trim())
    && isValidDateTime(createdAt)
    && typeof ackAt === "string"
    && isValidDateTime(ackAt)
    && Date.parse(ackAt.trim().replace(" ", "T"))
      >= Date.parse(createdAt.trim().replace(" ", "T"));
}

function isConsistentPaymentIssueAlertSource(alertChannel, sourceAlertNo) {
  if (alertChannel.trim() === "IN_APP_OUTBOX") {
    return sourceAlertNo == null;
  }
  return typeof sourceAlertNo === "string" && Boolean(sourceAlertNo.trim());
}

export function normalizePaymentIssueAlertActionResult(data, expectedAlertNo, expectedSnapshot = null) {
  try {
    normalizePaymentIssueAlertDetailResult(data, expectedAlertNo);
  } catch (error) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-ISSUE-ACTION" },
      "告警确认返回了无法确认的告警状态回执事实"
    );
  }
  if (expectedSnapshot && !matchesPaymentIssueAlertDetailSnapshot(expectedSnapshot, data)) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-ISSUE-ACTION" },
      "告警确认返回了与当前支付异常归属不一致的事实"
    );
  }
  return data;
}

export function normalizePaymentIssueResponsibilitySummaryResult(data) {
  if (!Array.isArray(data)
      || data.some((item) => !item || typeof item !== "object" || Array.isArray(item)
        || typeof item.groupName !== "string" || !item.groupName.trim()
        || typeof item.groupType !== "string" || !item.groupType.trim()
        || !Number.isInteger(item.totalCount) || item.totalCount < 0
        || !Number.isInteger(item.overdueCount) || item.overdueCount < 0
        || !Number.isInteger(item.p1Count) || item.p1Count < 0
        || !Number.isInteger(item.p2Count) || item.p2Count < 0
        || typeof item.suggestedAction !== "string" || !item.suggestedAction.trim()
        || item.overdueCount > item.totalCount
        || item.p1Count + item.p2Count > item.totalCount)) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-ISSUE-RESPONSIBILITY-SUMMARY" },
      "支付异常责任组汇总返回了无法确认的统计事实"
    );
  }
  return data;
}

export function normalizePaymentIssueBatchActionResult(data, expectedIssueNos = []) {
  try {
    const result = requirePageResult(
      data,
      "PAYMENT-CLIENT-INVALID-ISSUE-BATCH-ACTION",
      "异常批量处理返回了无法确认的分页结果"
    );
    const normalizedExpectedIssueNos = Array.isArray(expectedIssueNos)
      ? expectedIssueNos.map((issueNo) => normalizeBusinessIdentifier(issueNo))
      : [];
    const expectedSet = new Set(normalizedExpectedIssueNos);
    if (!normalizedExpectedIssueNos.length
      || normalizedExpectedIssueNos.some((issueNo) => typeof issueNo !== "string" || !issueNo)
      || expectedSet.size !== normalizedExpectedIssueNos.length
      || result.items.length !== normalizedExpectedIssueNos.length
      || result.total !== normalizedExpectedIssueNos.length
      || result.pageNo !== 1
      || result.pageSize !== normalizedExpectedIssueNos.length) {
      throw createBusinessError(
        { code: "PAYMENT-CLIENT-INVALID-ISSUE-BATCH-ACTION" },
        "异常批量处理结果与所选目标数量不一致"
      );
    }
    const page = normalizePaymentIssueListPage(result);
    if (page.items.some((item) => !expectedSet.has(normalizeBusinessIdentifier(item.issueNo)))
      || new Set(page.items.map((item) => normalizeBusinessIdentifier(item.issueNo))).size
        !== normalizedExpectedIssueNos.length) {
      throw createBusinessError(
        { code: "PAYMENT-CLIENT-INVALID-ISSUE-BATCH-ACTION" },
        "异常批量处理结果与所选目标集合不一致"
      );
    }
    return page;
  } catch (error) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-ISSUE-BATCH-ACTION" },
      error.rawMessage || "异常批量处理返回了无法确认的异常事实"
    );
  }
}

export function matchesPaymentIssueAlertDetailSnapshot(snapshot, result) {
  return Boolean(snapshot
    && result
    && normalizeBusinessIdentifier(result.alertNo)
      === normalizeBusinessIdentifier(snapshot.alertNo)
    && normalizeBusinessIdentifier(result.sourceAlertNo)
      === normalizeBusinessIdentifier(snapshot.sourceAlertNo)
    && normalizeBusinessIdentifier(result.issueNo)
      === normalizeBusinessIdentifier(snapshot.issueNo)
    && normalizeBusinessIdentifier(result.paymentOrderId)
      === normalizeBusinessIdentifier(snapshot.paymentOrderId)
    && normalizeBusinessIdentifier(result.businessChainId)
      === normalizeBusinessIdentifier(snapshot.businessChainId)
    && normalizeBusinessIdentifier(result.issueType)
      === normalizeBusinessIdentifier(snapshot.issueType)
    && normalizeBusinessIdentifier(result.severity)
      === normalizeBusinessIdentifier(snapshot.severity));
}

export function normalizePaymentLogDetailResult(data, expectedLogNo) {
  if (!data || typeof data !== "object" || Array.isArray(data)
      || typeof data.logNo !== "string"
      || !data.logNo.trim()
      || typeof data.paymentOrderId !== "string"
      || !data.paymentOrderId.trim()
      || typeof data.businessChainId !== "string"
      || !data.businessChainId.trim()
      || data.businessChainId.trim() !== `CHAIN-${data.paymentOrderId.trim()}`
      || typeof data.processStage !== "string"
      || !data.processStage.trim()
      || typeof data.logLevel !== "string"
      || !data.logLevel.trim()
      || !isConsistentPaymentLogLevel(data.logLevel, data.logLevelType)
      || !isValidPaymentSuccessFactIssue(data.successFactIssue)
      || !isValidDateTime(data.createdAt)
      || (expectedLogNo
        && !matchesExpectedBusinessIdentifier(data.logNo, normalizeBusinessIdentifier(expectedLogNo)))) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-PAYMENT-LOG-DETAIL" },
      "支付处理日志详情返回了无法确认的阶段级别事实"
    );
  }
  return data;
}

function isConsistentPaymentLogLevel(logLevel, logLevelType) {
  if (typeof logLevel !== "string" || !logLevel.trim()
      || typeof logLevelType !== "string" || !logLevelType.trim()) {
    return false;
  }
  const expectedType = {
    INFO: ["info", "success"],
    WARN: ["warn"],
    ERROR: ["danger"]
  }[logLevel.trim().toUpperCase()];
  return Boolean(expectedType) && expectedType.includes(logLevelType.trim().toLowerCase());
}

export function normalizePaymentLogListPage(data) {
  const normalizedPage = normalizePageResponse(data);
  return {
    ...normalizedPage,
    items: normalizedPage.items.map((item) => {
      if (!item || typeof item !== "object" || Array.isArray(item)
          || typeof item.logNo !== "string"
          || !item.logNo.trim()
          || typeof item.paymentOrderId !== "string"
          || !item.paymentOrderId.trim()
          || typeof item.businessChainId !== "string"
          || !item.businessChainId.trim()
          || item.businessChainId.trim() !== `CHAIN-${item.paymentOrderId.trim()}`
          || typeof item.orderNo !== "string"
          || !item.orderNo.trim()
          || typeof item.processStage !== "string"
          || !item.processStage.trim()
          || !isConsistentPaymentLogLevel(item.logLevel, item.logLevelType)
          || !isValidPaymentSuccessFactIssue(item.successFactIssue)
          || typeof item.message !== "string"
          || !item.message.trim()
          || typeof item.source !== "string"
          || !item.source.trim()
          || !isValidDateTime(item.createdAt)) {
        throw createBusinessError(
          { code: "PAYMENT-CLIENT-INVALID-PAYMENT-LOG-LIST" },
          "支付处理日志列表返回了无法确认的来源级别事实"
        );
      }
      return item;
    })
  };
}

export function matchesPaymentLogDetailSnapshot(snapshot, result) {
  return Boolean(snapshot
    && result
    && normalizeBusinessIdentifier(result.logNo)
      === normalizeBusinessIdentifier(snapshot.logNo)
    && normalizeBusinessIdentifier(result.paymentOrderId)
      === normalizeBusinessIdentifier(snapshot.paymentOrderId)
    && (snapshot.businessChainId == null
      || normalizeBusinessIdentifier(result.businessChainId)
        === normalizeBusinessIdentifier(snapshot.businessChainId))
    && normalizeBusinessIdentifier(result.orderNo)
      === normalizeBusinessIdentifier(snapshot.orderNo)
    && normalizeBusinessIdentifier(result.standardOrderNo)
      === normalizeBusinessIdentifier(snapshot.standardOrderNo)
    && normalizeBusinessIdentifier(result.requestNo)
      === normalizeBusinessIdentifier(snapshot.requestNo)
    && normalizeBusinessIdentifier(result.sourceType)
      === normalizeBusinessIdentifier(snapshot.sourceType)
    && normalizeBusinessIdentifier(result.sourceNo)
      === normalizeBusinessIdentifier(snapshot.sourceNo)
    && normalizeBusinessIdentifier(result.processStage)
      === normalizeBusinessIdentifier(snapshot.processStage));
}

export function normalizePaymentTaskRunDetailResult(data, expectedTaskLogNo) {
  if (!data || typeof data !== "object" || Array.isArray(data)
      || typeof data.taskLogNo !== "string"
      || !data.taskLogNo.trim()
      || (data.bizDate != null && !isValidDateOnly(data.bizDate))
      || typeof data.taskStatus !== "string"
      || !data.taskStatus.trim()
      || !Number.isInteger(data.processedCount) || data.processedCount < 0
      || !Number.isInteger(data.successCount) || data.successCount < 0
      || !Number.isInteger(data.warningCount) || data.warningCount < 0
      || !Number.isInteger(data.failCount) || data.failCount < 0
      || !isConsistentTaskRunCounts(
        data.processedCount, data.successCount, data.warningCount, data.failCount
      )
      || !["SUCCESS", "WARNING"].includes(data.taskStatus)
      || !isConsistentPaymentTaskRunStatusType(data.taskStatus, data.taskStatusType)
      || !isConsistentPaymentTaskRunTimes(data.startedAt, data.completedAt)
      || (data.taskStatus === "SUCCESS" && (data.warningCount > 0 || data.failCount > 0))
      || (data.taskStatus === "WARNING"
        && data.warningCount === 0
        && data.failCount === 0
        && data.processedCount > 0)
      || (expectedTaskLogNo
        && !matchesExpectedBusinessIdentifier(data.taskLogNo, normalizeBusinessIdentifier(expectedTaskLogNo)))) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-TASK-RUN-DETAIL" },
      "支付任务执行详情返回了无法确认的状态计数事实"
    );
  }
  return data;
}

function isConsistentPaymentTaskRunStatusType(status, statusType) {
  if (typeof status !== "string" || typeof statusType !== "string"
      || !status.trim() || !statusType.trim()) {
    return false;
  }
  const expectedType = { SUCCESS: "success", WARNING: "warn" }[status.trim().toUpperCase()];
  return Boolean(expectedType) && statusType.trim().toLowerCase() === expectedType;
}

function isConsistentTaskRunCounts(processedCount, successCount, warningCount, failCount) {
  return successCount + warningCount + failCount === processedCount;
}

function isConsistentPaymentTaskRunTimes(startedAt, completedAt) {
  if (!isValidDateTime(startedAt) || !isValidDateTime(completedAt)) {
    return false;
  }
  return Date.parse(completedAt.trim().replace(" ", "T"))
    >= Date.parse(startedAt.trim().replace(" ", "T"));
}

export function normalizePaymentTaskRunsPage(data) {
  const normalizedPage = normalizePageResponse(data);
  return {
    ...normalizedPage,
    items: normalizedPage.items.map((item) => normalizePaymentTaskRunDetailResult(item))
  };
}

export function matchesPaymentTaskRunDetailSnapshot(snapshot, result) {
  return Boolean(snapshot
    && result
    && normalizeBusinessIdentifier(result.taskLogNo)
      === normalizeBusinessIdentifier(snapshot.taskLogNo)
    && normalizeBusinessIdentifier(result.taskCode)
      === normalizeBusinessIdentifier(snapshot.taskCode)
    && normalizeBusinessIdentifier(result.bizDate)
      === normalizeBusinessIdentifier(snapshot.bizDate)
    && normalizeBusinessIdentifier(result.businessRefNo)
      === normalizeBusinessIdentifier(snapshot.businessRefNo));
}

export function normalizePaymentDeadLetterTaskDetailResult(data, expectedTaskNo) {
  if (!data || typeof data !== "object" || Array.isArray(data)
      || typeof data.taskNo !== "string"
      || !data.taskNo.trim()
      || typeof data.messageId !== "string"
      || !data.messageId.trim()
      || typeof data.targetSystem !== "string"
      || !data.targetSystem.trim()
      || typeof data.deadLetterRoutingKey !== "string"
      || !data.deadLetterRoutingKey.trim()
      || typeof data.replayExchange !== "string"
      || !data.replayExchange.trim()
      || typeof data.replayRoutingKey !== "string"
      || !data.replayRoutingKey.trim()
      || typeof data.taskStatus !== "string"
      || !data.taskStatus.trim()
      || !isKnownPaymentDeadLetterTaskStatus(data.taskStatus)
      || !isConsistentPaymentDeadLetterTaskStatusType(data.taskStatus, data.taskStatusType)
      || !Number.isInteger(data.replayCount) || data.replayCount < 0
      || !isConsistentPaymentDeadLetterTaskTimes(
        data.taskStatus, data.createdAt, data.updatedAt, data.lastReplayAt, data.resolvedAt
      )
      || !isConsistentPaymentDeadLetterTaskAudit(
        data.taskStatus, data.operator, data.resolutionNote
      )
      || (expectedTaskNo
        && !matchesExpectedBusinessIdentifier(data.taskNo, normalizeBusinessIdentifier(expectedTaskNo)))) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-DEAD-LETTER-TASK-DETAIL" },
      "支付死信任务详情返回了无法确认的状态计数事实"
    );
  }
  return data;
}

function isKnownPaymentDeadLetterTaskStatus(value) {
  return typeof value === "string"
    && [
      "PENDING_REVIEW", "READY_TO_REPLAY", "REPLAYING",
      "REPLAYED", "REPLAY_FAILED", "MANUAL_RESOLVED"
    ].includes(value.trim().toUpperCase());
}

function isConsistentPaymentDeadLetterTaskStatusType(status, statusType) {
  if (typeof statusType !== "string" || !statusType.trim()) {
    return false;
  }
  const normalizedStatus = status.trim().toUpperCase();
  const normalizedType = statusType.trim().toLowerCase();
  if (normalizedStatus === "REPLAY_FAILED") {
    return normalizedType === "danger";
  }
  if (["REPLAYED", "MANUAL_RESOLVED"].includes(normalizedStatus)) {
    return normalizedType === "success";
  }
  return normalizedType === "warn";
}

function isConsistentPaymentDeadLetterTaskTimes(
  status, createdAt, updatedAt, lastReplayAt, resolvedAt
) {
  if (!isValidDateTime(createdAt) || !isValidDateTime(updatedAt)) {
    return false;
  }
  const createdTimestamp = Date.parse(createdAt.trim().replace(" ", "T"));
  const updatedTimestamp = Date.parse(updatedAt.trim().replace(" ", "T"));
  const isOptionalTimeInRange = (value) => value == null
    || (isValidDateTime(value)
      && createdTimestamp <= Date.parse(value.trim().replace(" ", "T"))
      && Date.parse(value.trim().replace(" ", "T")) <= updatedTimestamp);
  const normalizedStatus = status.trim().toUpperCase();
  const hasLastReplayAt = lastReplayAt != null;
  const hasResolvedAt = resolvedAt != null;
  const statusTimesArePresent = {
    PENDING_REVIEW: !hasLastReplayAt && !hasResolvedAt,
    READY_TO_REPLAY: hasResolvedAt,
    REPLAYING: hasLastReplayAt && hasResolvedAt,
    REPLAYED: hasLastReplayAt && hasResolvedAt,
    REPLAY_FAILED: hasLastReplayAt && hasResolvedAt,
    MANUAL_RESOLVED: hasResolvedAt
  }[normalizedStatus] === true;
  return statusTimesArePresent
    && createdTimestamp <= updatedTimestamp
    && isOptionalTimeInRange(lastReplayAt)
    && isOptionalTimeInRange(resolvedAt);
}

function isConsistentPaymentDeadLetterTaskAudit(status, operator, resolutionNote) {
  const hasOperator = operator != null;
  const hasResolutionNote = resolutionNote != null;
  if (hasOperator !== hasResolutionNote) {
    return false;
  }
  const normalizedStatus = status.trim().toUpperCase();
  if (normalizedStatus === "PENDING_REVIEW") {
    return !hasOperator;
  }
  return hasOperator
    && typeof operator === "string"
    && Boolean(operator.trim())
    && typeof resolutionNote === "string"
    && Boolean(resolutionNote.trim());
}

export function normalizePaymentDeadLetterTaskListPage(data) {
  const normalizedPage = normalizePageResponse(data);
  return {
    ...normalizedPage,
    items: normalizedPage.items.map((item) => normalizePaymentDeadLetterTaskDetailResult(item))
  };
}

export function matchesPaymentDeadLetterTaskDetailSnapshot(snapshot, result) {
  return Boolean(snapshot
    && result
    && normalizeBusinessIdentifier(result.taskNo)
      === normalizeBusinessIdentifier(snapshot.taskNo)
    && normalizeBusinessIdentifier(result.messageId)
      === normalizeBusinessIdentifier(snapshot.messageId)
    && normalizeBusinessIdentifier(result.correlationId)
      === normalizeBusinessIdentifier(snapshot.correlationId)
    && normalizeBusinessIdentifier(result.targetSystem)
      === normalizeBusinessIdentifier(snapshot.targetSystem)
    && normalizeBusinessIdentifier(result.deadLetterRoutingKey)
      === normalizeBusinessIdentifier(snapshot.deadLetterRoutingKey)
    && normalizeBusinessIdentifier(result.replayExchange)
      === normalizeBusinessIdentifier(snapshot.replayExchange)
    && normalizeBusinessIdentifier(result.replayRoutingKey)
      === normalizeBusinessIdentifier(snapshot.replayRoutingKey));
}

export function normalizePaymentDeadLetterTaskActionResult(
  data,
  expectedTaskNo,
  expectedSnapshot = null,
  expectedStatuses = []
) {
  if (!data || typeof data !== "object" || Array.isArray(data)
      || typeof data.taskNo !== "string"
      || !data.taskNo.trim()
      || typeof data.taskStatus !== "string"
      || !data.taskStatus.trim()
      || !isKnownPaymentDeadLetterTaskStatus(data.taskStatus)
      || !isConsistentPaymentDeadLetterTaskStatusType(data.taskStatus, data.taskStatusType)
      || !Number.isInteger(data.replayCount)
      || data.replayCount < 0
      || !isConsistentPaymentDeadLetterTaskTimes(
        data.taskStatus, data.createdAt, data.updatedAt, data.lastReplayAt, data.resolvedAt
      )
      || !isConsistentPaymentDeadLetterTaskAudit(
        data.taskStatus, data.operator, data.resolutionNote
      )
      || (expectedTaskNo
        && !matchesExpectedBusinessIdentifier(data.taskNo, normalizeBusinessIdentifier(expectedTaskNo)))) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-DEAD-LETTER-ACTION" },
      "死信任务动作返回了无法确认的状态计数事实"
    );
  }
  if (expectedSnapshot && !matchesPaymentDeadLetterTaskDetailSnapshot(expectedSnapshot, data)) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-DEAD-LETTER-ACTION" },
      "死信任务动作返回了无法确认的消息路由事实"
    );
  }
  if (expectedStatuses.length > 0 && !expectedStatuses.includes(data.taskStatus)) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-DEAD-LETTER-ACTION" },
      "死信任务动作返回了与当前操作不一致的状态"
    );
  }
  return data;
}

export function normalizePaymentDayEndDetailResult(data, expectedBatchNo) {
  try {
    normalizePaymentDayEndRecentBatchResult(data);
  } catch (error) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-DAY-END-DETAIL" },
      error.rawMessage || "支付日终批次详情返回了无法确认的批次事实"
    );
  }
  if (expectedBatchNo
      && !matchesExpectedBusinessIdentifier(data.batchNo, normalizeBusinessIdentifier(expectedBatchNo))) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-DAY-END-DETAIL" },
      "支付日终批次详情返回了与当前批次不一致的事实"
    );
  }
  return data;
}

export function matchesPaymentDayEndDetailSnapshot(snapshot, result) {
  return Boolean(snapshot
    && result
    && normalizeBusinessIdentifier(result.batchNo)
      === normalizeBusinessIdentifier(snapshot.batchNo)
    && normalizeBusinessIdentifier(result.bizDate)
      === normalizeBusinessIdentifier(snapshot.bizDate));
}

export function normalizePaymentConfigReviewActionResult(data, expectedRequestNo) {
  if (!Array.isArray(data) || data.length === 0
      || data.some((item) => !item || typeof item !== "object" || Array.isArray(item)
        || typeof item.requestNo !== "string"
        || !item.requestNo.trim()
        || typeof item.reviewStatus !== "string"
        || !item.reviewStatus.trim()
        || !isConsistentPaymentConfigReviewStatus(item.reviewStatus, item.reviewStatusType)
        || !isConsistentOptionalPaymentConfigCandidateStatus(item)
        || !isConsistentOptionalPaymentCertificateStatus(item)
        || !isConsistentOptionalPaymentConfigReviewTimes(item))
      || (expectedRequestNo
        && !data.some((item) => matchesExpectedBusinessIdentifier(
          item.requestNo,
          normalizeBusinessIdentifier(expectedRequestNo)
        )))) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-CONFIG-ACTION" },
      "支付配置复核动作返回了无法确认的复核事实"
    );
  }
  return data;
}

export function normalizePaymentConfigReviewListResult(data) {
  if (!Array.isArray(data)
      || data.some((item) => !item || typeof item !== "object" || Array.isArray(item)
        || typeof item.requestNo !== "string"
        || !item.requestNo.trim()
        || typeof item.reviewStatus !== "string"
        || !item.reviewStatus.trim()
        || !isConsistentPaymentConfigReviewStatus(item.reviewStatus, item.reviewStatusType)
        || !isConsistentOptionalPaymentConfigCandidateStatus(item)
        || !isConsistentOptionalPaymentCertificateStatus(item)
        || !isConsistentPaymentConfigReviewTimes(
          item.reviewStatus, item.createdAt, item.reviewedAt
        ))) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-CONFIG-REVIEWS" },
      "支付配置复核列表返回了无法确认的复核事实"
    );
  }
  return data;
}

function isConsistentPaymentConfigReviewStatus(status, statusType) {
  if (typeof status !== "string" || !status.trim()
      || typeof statusType !== "string" || !statusType.trim()) {
    return false;
  }
  const expectedType = {
    PENDING_REVIEW: "warn",
    APPROVED: "success",
    REJECTED: "danger"
  }[status.trim().toUpperCase()];
  return Boolean(expectedType) && statusType.trim().toLowerCase() === expectedType;
}

function isConsistentPaymentConfigReviewedAt(status, reviewedAt) {
  if (typeof status !== "string") {
    return false;
  }
  if (status.trim().toUpperCase() === "PENDING_REVIEW") {
    return reviewedAt == null;
  }
  return isValidDateTime(reviewedAt);
}

function isConsistentPaymentConfigReviewTimes(status, createdAt, reviewedAt) {
  if (!isValidDateTime(createdAt)
      || !isConsistentPaymentConfigReviewedAt(status, reviewedAt)) {
    return false;
  }
  if (reviewedAt == null) {
    return true;
  }
  return Date.parse(createdAt.trim().replace(" ", "T"))
    <= Date.parse(reviewedAt.trim().replace(" ", "T"));
}

function isConsistentOptionalPaymentCertificateStatus(item) {
  const hasCertificateFact = item.certificateStatus != null || item.certificateStatusType != null;
  return !hasCertificateFact
    || isConsistentPaymentCertificateStatus(item.certificateStatus, item.certificateStatusType);
}

function isConsistentOptionalPaymentConfigCandidateStatus(item) {
  const hasCandidateFact = item.candidateStatus != null || item.candidateStatusType != null;
  return !hasCandidateFact
    || isConsistentPaymentConfigEntityStatus(item.candidateStatus, item.candidateStatusType);
}

function isConsistentOptionalPaymentConfigReviewTimes(item) {
  const hasTimeFact = item.createdAt != null || item.reviewedAt != null;
  return !hasTimeFact
    || isConsistentPaymentConfigReviewTimes(item.reviewStatus, item.createdAt, item.reviewedAt);
}

function normalizePaymentConfigActionCollection(data, expectedFacts, message) {
  const expectedEntries = Object.entries(expectedFacts || {})
    .filter(([, value]) => value);
  if (!Array.isArray(data) || data.length === 0
      || data.some((item) => !item || typeof item !== "object" || Array.isArray(item)
        || Object.keys(expectedFacts).some((field) => typeof item[field] !== "string"
          || !item[field].trim())
        || data.some((item) => (item.reviewStatus != null || item.reviewStatusType != null)
          && !isConsistentPaymentConfigReviewStatus(item.reviewStatus, item.reviewStatusType))
        || data.some((item) => (item.candidateStatus != null || item.candidateStatusType != null)
          && !isConsistentPaymentConfigEntityStatus(item.candidateStatus, item.candidateStatusType))
        || data.some((item) => (item.certificateStatus != null || item.certificateStatusType != null)
          && !isConsistentPaymentCertificateStatus(item.certificateStatus, item.certificateStatusType)))
      || (expectedEntries.length > 0
        && !data.some((item) => expectedEntries.every(([field, value]) =>
          matchesExpectedBusinessIdentifier(item[field], normalizeBusinessIdentifier(value)))))) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-CONFIG-ACTION" },
      message
    );
  }
  return data;
}

export function normalizePaymentConfigOverviewResult(data) {
  const collections = [
    ["channels", "channelCode"],
    ["routeRules", "ruleCode"],
    ["protocols", "protocolCode"],
    ["protocolTypeOptions", "protocolType"],
    ["returnCodeMappings", "channelCode"],
    ["gateways", "gatewayCode"],
    ["controlPolicies", "sourceAppId"],
    ["alertProviders", "providerCode"],
    ["issueDutyRosters", "rosterCode"]
  ];
  const invalid = !data || typeof data !== "object" || Array.isArray(data)
    || collections.some(([collection, identityField]) => {
      if (!Array.isArray(data[collection])) {
        return true;
      }
      return data[collection].some((item) => {
        if (!item || typeof item !== "object" || Array.isArray(item)
            || typeof item[identityField] !== "string" || !item[identityField].trim()) {
          return true;
        }
        if (collection === "protocolTypeOptions") {
          return typeof item.protocolTypeName !== "string" || !item.protocolTypeName.trim();
        }
        if (!isValidDateTime(item.updatedAt)) {
          return true;
        }
        if (collection === "returnCodeMappings"
            && (typeof item.channelReturnCode !== "string" || !item.channelReturnCode.trim())) {
          return true;
        }
        if (collection === "gateways"
            && !isConsistentPaymentCertificateStatus(item.certificateStatus, item.certificateStatusType)) {
          return true;
        }
        if (collection === "controlPolicies"
            && !isConsistentPaymentControlPolicySelfCheck(
              item.selfCheckStatus, item.selfCheckStatusType
            )) {
          return true;
        }
        return typeof item.status !== "string"
          || !item.status.trim()
          || !isConsistentPaymentConfigEntityStatus(item.status, item.statusType);
      });
    });
  if (invalid) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-CONFIG-OVERVIEW" },
      "支付配置总览返回了无法确认的配置事实"
    );
  }
  return data;
}

export function normalizePaymentConfigVersionsResult(data, expectedIdentity, identityField) {
  if (!Array.isArray(data)
      || data.some((item) => !item || typeof item !== "object" || Array.isArray(item)
        || typeof item[identityField] !== "string"
        || !item[identityField].trim()
        || (expectedIdentity
          && !matchesExpectedBusinessIdentifier(
            item[identityField],
            normalizeBusinessIdentifier(expectedIdentity)
          ))
        || !Number.isInteger(item.versionNo)
        || item.versionNo <= 0
        || typeof item.status !== "string"
        || !item.status.trim()
        || !isConsistentPaymentConfigEntityStatus(item.status, item.statusType)
        || (identityField === "gatewayCode"
          && !isConsistentPaymentCertificateStatus(item.certificateStatus, item.certificateStatusType))
        || !isValidDateTime(item.createdAt))) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-CONFIG-VERSIONS" },
      "支付配置版本历史返回了无法确认的版本事实"
    );
  }
  return data;
}

function isConsistentPaymentConfigEntityStatus(status, statusType) {
  if (typeof status !== "string" || !status.trim()
      || typeof statusType !== "string" || !statusType.trim()) {
    return false;
  }
  const expectedType = {
    ENABLED: "success",
    DISABLED: "danger",
    ACTIVE: "success",
    INACTIVE: "danger"
  }[status.trim().toUpperCase()];
  return Boolean(expectedType) && statusType.trim().toLowerCase() === expectedType;
}

function isConsistentPaymentCertificateStatus(status, statusType) {
  if (typeof status !== "string" || !status.trim()
      || typeof statusType !== "string" || !statusType.trim()) {
    return false;
  }
  const expectedType = {
    VALID: "success",
    EXPIRING_SOON: "warn",
    EXPIRED: "danger",
    ARCHIVED: "danger"
  }[status.trim().toUpperCase()];
  return Boolean(expectedType) && statusType.trim().toLowerCase() === expectedType;
}

function isConsistentPaymentControlPolicySelfCheck(status, statusType) {
  if (typeof status !== "string" || !status.trim()
      || typeof statusType !== "string" || !statusType.trim()) {
    return false;
  }
  const expectedType = {
    PASS: "success",
    WARN: "warn",
    FAIL: "danger"
  }[status.trim().toUpperCase()];
  return Boolean(expectedType) && statusType.trim().toLowerCase() === expectedType;
}

export function normalizePaymentConfigChannelRollbackResult(data, expectedChannelCode) {
  return normalizePaymentConfigActionCollection(
    data, { channelCode: expectedChannelCode }, "支付渠道回滚申请返回了无法确认的渠道事实"
  );
}

export function normalizePaymentConfigChannelReviewSubmitResult(data, expectedChannelCode) {
  return normalizePaymentConfigActionCollection(
    data, { channelCode: expectedChannelCode }, "支付渠道启停申请返回了无法确认的渠道事实"
  );
}

export function normalizePaymentConfigGatewayRollbackResult(data, expectedGatewayCode) {
  return normalizePaymentConfigActionCollection(
    data, { gatewayCode: expectedGatewayCode }, "支付网关回滚申请返回了无法确认的网关事实"
  );
}

export function normalizePaymentConfigGatewayReviewSubmitResult(data, expectedGatewayCode) {
  return normalizePaymentConfigActionCollection(
    data, { gatewayCode: expectedGatewayCode }, "支付网关启停申请返回了无法确认的网关事实"
  );
}

export function normalizePaymentConfigGatewayReviewApplicationResult(data, expectedGatewayCode) {
  return normalizePaymentConfigActionCollection(
    data, { gatewayCode: expectedGatewayCode }, "支付网关复核申请返回了无法确认的网关事实"
  );
}

export function normalizePaymentConfigProtocolRollbackResult(data, expectedProtocolCode) {
  return normalizePaymentConfigActionCollection(
    data, { protocolCode: expectedProtocolCode }, "支付协议回滚申请返回了无法确认的协议事实"
  );
}

export function normalizePaymentConfigProtocolReviewSubmitResult(data, expectedProtocolCode) {
  return normalizePaymentConfigActionCollection(
    data, { protocolCode: expectedProtocolCode }, "支付协议启停申请返回了无法确认的协议事实"
  );
}

export function normalizePaymentConfigProtocolReviewApplicationResult(data, expectedProtocolCode) {
  return normalizePaymentConfigActionCollection(
    data, { protocolCode: expectedProtocolCode }, "支付协议复核申请返回了无法确认的协议事实"
  );
}

export function normalizePaymentConfigControlPolicyRollbackResult(data, expectedSourceAppId) {
  return normalizePaymentConfigActionCollection(
    data, { sourceAppId: expectedSourceAppId }, "支付控制策略回滚申请返回了无法确认的策略事实"
  );
}

export function normalizePaymentConfigControlPolicyReviewSubmitResult(data, expectedSourceAppId) {
  return normalizePaymentConfigActionCollection(
    data, { sourceAppId: expectedSourceAppId }, "支付控制策略启停申请返回了无法确认的策略事实"
  );
}

export function normalizePaymentConfigControlPolicyReviewApplicationResult(data, expectedSourceAppId) {
  return normalizePaymentConfigActionCollection(
    data, { sourceAppId: expectedSourceAppId }, "支付控制策略复核申请返回了无法确认的策略事实"
  );
}

export function normalizePaymentConfigAlertProviderReviewSubmitResult(data, expectedProviderCode) {
  return normalizePaymentConfigActionCollection(
    data, { providerCode: expectedProviderCode }, "支付告警供应商启停申请返回了无法确认的供应商事实"
  );
}

export function normalizePaymentConfigIssueDutyRosterReviewSubmitResult(data, expectedRosterCode) {
  return normalizePaymentConfigActionCollection(
    data, { rosterCode: expectedRosterCode }, "支付值班路由启停申请返回了无法确认的值班路由事实"
  );
}

export function normalizePaymentConfigIssueDutyRosterReviewApplicationResult(data, expectedRosterCode) {
  return normalizePaymentConfigActionCollection(
    data, { rosterCode: expectedRosterCode }, "支付值班路由新增申请返回了无法确认的值班路由事实"
  );
}

export function normalizePaymentConfigIssueDutyRosterReviewUpdateResult(data, expectedRosterCode) {
  return normalizePaymentConfigActionCollection(
    data, { rosterCode: expectedRosterCode }, "支付值班路由修改申请返回了无法确认的值班路由事实"
  );
}

export function normalizePaymentConfigAlertProviderReviewApplicationResult(data, expectedProviderCode) {
  return normalizePaymentConfigActionCollection(
    data, { providerCode: expectedProviderCode }, "支付告警供应商新增申请返回了无法确认的供应商事实"
  );
}

export function normalizePaymentConfigAlertProviderReviewUpdateResult(data, expectedProviderCode) {
  return normalizePaymentConfigActionCollection(
    data, { providerCode: expectedProviderCode }, "支付告警供应商修改申请返回了无法确认的供应商事实"
  );
}

export function normalizePaymentConfigReturnCodeReviewSubmitResult(data, expectedChannelCode, expectedChannelReturnCode) {
  return normalizePaymentConfigActionCollection(
    data,
    { channelCode: expectedChannelCode, channelReturnCode: expectedChannelReturnCode },
    "支付返回码启停申请返回了无法确认的返回码事实"
  );
}

export function normalizePaymentConfigReturnCodeImportReviewSubmitResult(data, expectedBaselineFingerprint) {
  return normalizePaymentConfigActionCollection(
    data, { baselineFingerprint: expectedBaselineFingerprint }, "支付返回码导入复核申请返回了无法确认的基线事实"
  );
}

export function normalizePaymentConfigRouteRuleRollbackResult(data, expectedRuleCode) {
  return normalizePaymentConfigActionCollection(
    data, { ruleCode: expectedRuleCode }, "支付路由规则回滚申请返回了无法确认的规则事实"
  );
}

export function normalizePaymentConfigRouteRuleReviewSubmitResult(data, expectedRuleCode) {
  return normalizePaymentConfigActionCollection(
    data, { ruleCode: expectedRuleCode }, "支付路由规则复核申请返回了无法确认的规则事实"
  );
}

export function normalizePaymentRouteRuleConflictsResult(data) {
  const severityTypes = { BLOCKER: "danger", WARNING: "warn", REVIEW: "info" };
  if (!Array.isArray(data) || data.some((item) => {
    const hasConflictingCode = item && item.conflictingRuleCode != null;
    const hasConflictingName = item && item.conflictingRuleName != null;
    const hasConflictingPriority = item && item.conflictingPriority != null;
    return !item || typeof item !== "object" || Array.isArray(item)
      || typeof item.conflictType !== "string" || !item.conflictType.trim()
      || typeof item.severity !== "string" || !severityTypes[item.severity.trim().toUpperCase()]
      || typeof item.severityType !== "string"
      || item.severityType.trim().toLowerCase() !== severityTypes[item.severity.trim().toUpperCase()]
      || typeof item.ruleCode !== "string" || !item.ruleCode.trim()
      || typeof item.ruleName !== "string" || !item.ruleName.trim()
      || !Number.isInteger(item.priority) || item.priority < 0
      || typeof item.overlappingScene !== "string" || !item.overlappingScene.trim()
      || typeof item.message !== "string" || !item.message.trim()
      || hasConflictingCode !== hasConflictingName
      || hasConflictingCode !== hasConflictingPriority
      || (hasConflictingCode && (typeof item.conflictingRuleCode !== "string"
        || !item.conflictingRuleCode.trim()
        || typeof item.conflictingRuleName !== "string"
        || !item.conflictingRuleName.trim()
        || !Number.isInteger(item.conflictingPriority)
        || item.conflictingPriority < 0));
  })) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-ROUTE-RULE-CONFLICTS" },
      "支付路由规则冲突检测返回了无法确认的冲突事实"
    );
  }
  return data;
}

export function normalizePaymentPrepayResult(
  data,
  expectedOrderNo,
  expectedAmount,
  expectedCustomerName,
  expectedPayScene = null
) {
  if (!data || typeof data !== "object" || Array.isArray(data)
      || typeof data.prepayOrderNo !== "string"
      || !data.prepayOrderNo.trim()
      || typeof data.paymentOrderId !== "string"
      || !data.paymentOrderId.trim()
      || typeof data.businessChainId !== "string"
      || !data.businessChainId.trim()
      || data.businessChainId.trim() !== `CHAIN-${data.paymentOrderId.trim()}`
      || typeof data.orderNo !== "string"
      || !data.orderNo.trim()
      || !Number.isFinite(normalizeAmount(data.amount))
      || typeof data.paymentStatus !== "string"
      || !PAYMENT_STATUSES.has(data.paymentStatus.trim().toUpperCase())
      || typeof data.cashierStatus !== "string"
      || CASHIER_STATUS_TYPES.get(data.cashierStatus.trim()) !== data.cashierStatusType
      || !isConsistentPaymentCashierStatus(data.paymentStatus, data.cashierStatus)
      || !isConsistentPaymentSuccessTime(data.paymentStatus, data.successAt)
      || !isConsistentPaymentChannelTransaction(data.paymentStatus, data.channelTransactionNo)
      || !isValidOptionalStatusPair(data.latestAttemptStatus, data.latestAttemptStatusType)
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
      || !isConsistentPaymentLifecycleTimeOrder(data.createdAt, data.successAt)
      || !isValidDateTime(data.createdAt)
      || !isValidDateTime(data.expiresAt)
      || Date.parse(data.createdAt.trim().replace(" ", "T"))
        > Date.parse(data.expiresAt.trim().replace(" ", "T"))
      || (expectedOrderNo
        && !matchesExpectedBusinessIdentifier(data.orderNo, normalizeBusinessIdentifier(expectedOrderNo)))
      || (expectedCustomerName
        && !matchesExpectedBusinessIdentifier(data.customerName, normalizeBusinessIdentifier(expectedCustomerName)))
      || (expectedPayScene != null
        && !matchesExpectedBusinessIdentifier(data.payScene, normalizeBusinessIdentifier(expectedPayScene)))
      || (expectedAmount != null
        && normalizeAmountForComparison(data.amount) !== normalizeAmountForComparison(expectedAmount))) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-PREPAY" },
      "预付单创建返回了无法确认的支付事实"
    );
  }
  return data;
}

function isConsistentPaymentCashierStatus(paymentStatus, cashierStatus) {
  const expectedCashierStatuses = PAYMENT_CASHIER_STATUSES.get(
    paymentStatus.trim().toUpperCase()
  );
  return !expectedCashierStatuses || expectedCashierStatuses.has(cashierStatus.trim());
}

async function request(url, options = {}) {
  return requestInternal(url, options, false);
}

async function requestPage(url, options = {}) {
  return requestInternal(url, options, false).then(normalizePageResponse);
}

function normalizeCollectionPayload(data) {
  if (data == null) {
    return [];
  }
  if (!Array.isArray(data)) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-COLLECTION" },
      "支付服务返回了无法确认的集合结果"
    );
  }
  return data.filter((item) => item != null);
}

function requestCollection(url, options = {}) {
  return request(url, options).then(normalizeCollectionPayload);
}

function requestObject(url, options = {}) {
  return request(url, options).then(normalizeObjectPayload);
}

async function requestInternal(url, options = {}, normalizePage = false) {
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
    return normalizePage ? normalizePagePayload(payload.data) : payload.data;
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

function postCollection(url, payload) {
  return requestCollection(url, buildJsonRequestOptions("POST", payload));
}

export function requireNonEmptyCollection(data, errorCode, message) {
  if (!Array.isArray(data) || data.length === 0) {
    throw createBusinessError({ code: errorCode }, message);
  }
  return data;
}

export function requireObjectResult(data, errorCode, message) {
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    throw createBusinessError({ code: errorCode }, message);
  }
  return data;
}

export function requirePageResult(data, errorCode, message) {
  if (!data || typeof data !== "object" || Array.isArray(data) || !Array.isArray(data.items)) {
    throw createBusinessError({ code: errorCode }, message);
  }
  return data;
}

export function normalizeDayEndRunResult(data) {
  if (!data || typeof data !== "object" || Array.isArray(data)
      || typeof data.latestBatchStatus !== "string"
      || !isKnownPaymentDayEndBatchStatus(data.latestBatchStatus)) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-DAY-END-ACTION" },
      "支付日终处理返回了无法确认的批次结果"
    );
  }
  return data;
}

export function normalizePaymentDayEndRunResult(data, expectedBizDate) {
  let overview;
  try {
    overview = normalizePaymentDayEndOverviewResult(data);
  } catch (error) {
    if (data && typeof data === "object" && !Array.isArray(data)
        && (Array.isArray(data.alerts) || Array.isArray(data.recentBatches))) {
      throw createBusinessError(
        { code: "PAYMENT-CLIENT-INVALID-DAY-END-ACTION" },
        error.rawMessage || "支付日终处理返回了无法确认的批次结果"
      );
    }
    throw error;
  }
  if (expectedBizDate
      && (typeof overview.latestBizDate !== "string"
        || overview.latestBizDate !== expectedBizDate)) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-DAY-END-ACTION" },
      "支付日终处理返回了与请求业务日不一致的批次结果"
    );
  }
  if (!isKnownPaymentDayEndBatchStatus(overview.latestBatchStatus)) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-DAY-END-ACTION" },
      "支付日终处理返回了无法确认的批次结果"
    );
  }
  return overview;
}

export function normalizeControlPolicySelfCheckResult(data, expectedConfigCode) {
  let overview;
  try {
    overview = normalizePaymentConfigOverviewResult(data);
  } catch (error) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-CONTROL-POLICY-SELF-CHECK" },
      error.rawMessage || "支付控制策略自检返回了无法确认的配置结果"
    );
  }
  if (expectedConfigCode && !overview.controlPolicies.some((item) => item.sourceAppId === expectedConfigCode)) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-CONTROL-POLICY-SELF-CHECK" },
      "支付控制策略自检返回了无法确认的配置结果"
    );
  }
  return overview;
}

export function normalizeRouteRuleValidationResult(data) {
  if (!data || typeof data !== "object" || Array.isArray(data)
      || typeof data.valid !== "boolean"
      || typeof data.matched !== "boolean"
      || typeof data.message !== "string" || !data.message.trim()) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-ROUTE-RULE-VALIDATION" },
      "路由规则校验返回了无法确认的结果"
    );
  }
  return data;
}

export function normalizeReturnCodeImportPreviewResult(data) {
  const countFields = ["totalCount", "createCount", "updateCount", "unchangedCount", "invalidCount", "appliedCount"];
  const actionCounts = { CREATE: 0, UPDATE: 0, UNCHANGED: 0, INVALID: 0 };
  let invalidItem = false;
  if (data && typeof data === "object" && !Array.isArray(data) && Array.isArray(data.items)) {
    for (const item of data.items) {
      const candidate = item?.candidate;
      const candidateFields = [
        "channelCode", "channelReturnCode", "standardizedCode", "standardizedMessage",
        "handlingSuggestion", "retryable", "manualInterventionRequired", "mappingVersion",
        "archiveStatus", "archiveStatusType", "status", "statusType"
      ];
      const candidateValid = candidate && typeof candidate === "object" && !Array.isArray(candidate)
        && candidateFields.every((field) => typeof candidate[field] === "string" && candidate[field].trim())
        && isConsistentPaymentConfigEntityStatus(candidate.archiveStatus, candidate.archiveStatusType)
        && isConsistentPaymentConfigEntityStatus(candidate.status, candidate.statusType)
        && Number.isInteger(candidate.priority) && candidate.priority >= 0;
      const itemValid = item && typeof item === "object" && !Array.isArray(item)
        && Number.isInteger(item.rowNumber) && item.rowNumber > 0
        && ["CREATE", "UPDATE", "UNCHANGED", "INVALID"].includes(item.action)
        && typeof item.valid === "boolean"
        && item.valid === (item.action !== "INVALID")
        && typeof item.message === "string" && item.message.trim()
        && Array.isArray(item.changedFields)
        && item.changedFields.every((field) => typeof field === "string" && field.trim())
        && candidateValid;
      if (!itemValid) {
        invalidItem = true;
      } else {
        actionCounts[item.action] += 1;
      }
    }
  }
  if (!data || typeof data !== "object" || Array.isArray(data)
      || typeof data.baselineFingerprint !== "string" || !data.baselineFingerprint.trim()
      || typeof data.valid !== "boolean" || typeof data.applied !== "boolean"
      || !Array.isArray(data.items)
      || invalidItem
      || countFields.some((field) => !Number.isInteger(data[field]) || data[field] < 0)
      || data.items.length !== data.totalCount
      || data.createCount !== actionCounts.CREATE
      || data.updateCount !== actionCounts.UPDATE
      || data.unchangedCount !== actionCounts.UNCHANGED
      || data.invalidCount !== actionCounts.INVALID
      || data.createCount + data.updateCount + data.unchangedCount + data.invalidCount !== data.totalCount
      || (data.valid !== (data.invalidCount === 0))
      || (data.applied && !data.valid)
      || (!data.applied && data.appliedCount !== 0)
      || data.appliedCount > data.createCount + data.updateCount) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-RETURN-CODE-IMPORT-PREVIEW" },
      "支付返回码导入预检返回了无法确认的结果"
    );
  }
  return data;
}

export function normalizePaymentMonitorOverviewResult(data) {
  const countFields = [
    "totalCount",
    "successCount",
    "pendingPaymentCount",
    "closedPaymentCount",
    "failedRefundCount",
    "processingRefundCount",
    "disabledChannelCount",
    "alertCount"
  ];
  const requiredSummaryCountFields = [
    "totalCount", "successCount", "pendingPaymentCount", "closedPaymentCount",
    "failedRefundCount", "processingRefundCount", "disabledChannelCount", "alertCount"
  ];
  const invalid = !data || typeof data !== "object" || Array.isArray(data)
    || !data.summary || typeof data.summary !== "object" || Array.isArray(data.summary)
    || !Number.isFinite(normalizeNonNegativeAmount(data.summary.successAmount))
    || !isConsistentSuccessRate(
      data.summary.successCount,
      data.summary.totalCount,
      data.summary.successRate
    )
    || ["trends", "channelMetrics", "alerts", "metricDefinitions"]
      .some((field) => !Array.isArray(data[field]))
    || data.trends.some((item) => !item || typeof item !== "object" || Array.isArray(item)
      || !isValidDateOnly(item.statDate)
      || !Number.isInteger(item.totalCount) || item.totalCount < 0
      || !Number.isInteger(item.successCount) || item.successCount < 0
      || item.successCount > item.totalCount
      || !Number.isFinite(normalizeNonNegativeAmount(item.successAmount)))
    || !isDescendingUniqueDateSeries(data.trends)
    || data.channelMetrics.some((item) => !item || typeof item !== "object" || Array.isArray(item)
      || typeof item.channelCode !== "string" || !item.channelCode.trim()
      || typeof item.paymentMethod !== "string" || !item.paymentMethod.trim()
      || !Number.isInteger(item.totalCount) || item.totalCount < 0
      || !Number.isInteger(item.successCount) || item.successCount < 0
      || item.successCount > item.totalCount
      || !Number.isInteger(item.pendingCount) || item.pendingCount < 0
      || item.pendingCount > item.totalCount
      || !Number.isInteger(item.unacceptedCount) || item.unacceptedCount < 0
      || item.unacceptedCount > item.totalCount - item.pendingCount
      || !isConsistentSuccessRate(item.successCount, item.totalCount, item.successRate)
      || !Number.isFinite(normalizeNonNegativeAmount(item.successAmount))
      || !isConsistentChannelRiskLevel(item.riskLevel, item.riskLevelType)
      || typeof item.riskHint !== "string" || !item.riskHint.trim())
    || !isUniqueChannelMetricSeries(data.channelMetrics)
    || data.alerts.some((item) => !item || typeof item !== "object" || Array.isArray(item)
      || typeof item.alertType !== "string" || !item.alertType.trim()
      || typeof item.alertTitle !== "string" || !item.alertTitle.trim()
      || typeof item.alertMessage !== "string" || !item.alertMessage.trim()
      || !["P1", "P2", "P3"].includes(item.alertLevel)
      || !["danger", "warn", "success"].includes(item.alertLevelType)
      || !Number.isInteger(item.affectedCount) || item.affectedCount < 0
      || (item.affectedCount === 0
        && (item.alertLevel !== "P3" || item.alertLevelType !== "success"))
      || (item.affectedCount > 0
        && (item.alertLevel === "P3" || item.alertLevelType === "success"))
      || typeof item.suggestedAction !== "string" || !item.suggestedAction.trim()
      || typeof item.actionRoute !== "string" || !item.actionRoute.trim())
    || data.metricDefinitions.some((item) => !item || typeof item !== "object" || Array.isArray(item)
      || ["metricCode", "metricName", "definition", "unit", "defaultThreshold",
        "alertLevel", "owner", "actionRoute"]
        .some((field) => typeof item[field] !== "string" || !item[field].trim()))
    || requiredSummaryCountFields.some((field) => !Number.isInteger(data.summary[field]) || data.summary[field] < 0)
    || countFields.some((field) => data.summary[field] != null
      && (!Number.isInteger(data.summary[field]) || data.summary[field] < 0))
    || data.summary.successCount > data.summary.totalCount
    || data.summary.pendingPaymentCount > data.summary.totalCount
    || data.summary.closedPaymentCount > data.summary.totalCount
    || data.summary.alertCount !== data.alerts.length;
  if (invalid) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-MONITOR-OVERVIEW" },
      "支付监控总览返回了无法确认的统计事实"
    );
  }
  return data;
}

function isDescendingUniqueDateSeries(items) {
  return items.every((item, index) => index === 0
    || items[index - 1].statDate > item.statDate);
}

function isConsistentChannelRiskLevel(riskLevel, riskLevelType) {
  if (typeof riskLevel !== "string" || typeof riskLevelType !== "string") {
    return false;
  }
  return {
    P1: "danger",
    P2: "warn",
    正常: "success"
  }[riskLevel.trim()] === riskLevelType.trim().toLowerCase();
}

function isUniqueChannelMetricSeries(items) {
  const keys = new Set();
  return items.every((item) => {
    const key = `${item.channelCode.trim()}\u0000${item.paymentMethod.trim()}`;
    if (keys.has(key)) {
      return false;
    }
    keys.add(key);
    return true;
  });
}

export function normalizePaymentTaskCenterOverviewResult(data) {
  const countFields = [
    "expiredPaymentCount", "dueExpiredPaymentCount", "backingOffExpiredPaymentCount",
    "pendingCallbackCount", "pendingQueryBacklogCount", "duePendingQueryCount",
    "pendingRefundQueryBacklogCount", "duePendingRefundQueryCount", "queryMonitorWindowHours",
    "recentQueryCount", "recentQueryFailureCount", "queryFailureCountThreshold",
    "queryFailureRateThresholdPercent", "queryFailureRateMinimumSampleCount",
    "recentQueryRateLimitRejectionCount", "queryRateLimitRejectionCountThreshold",
    "actionableQueryMonitorAlertCount", "pendingIssueAlertDispatchCount",
    "failedIssueAlertDispatchCount", "oldestPendingIssueAlertDispatchMinutes",
    "issueAlertDispatchCountThreshold", "issueAlertDispatchTimeoutMinutes",
    "pendingIssueAlertReceiptCount", "manualIssueAlertReceiptCount",
    "dueIssueAlertReceiptCount", "backingOffIssueAlertReceiptCount",
    "queryingIssueAlertReceiptCount", "exhaustedIssueAlertReceiptCount",
    "oldestPendingIssueAlertReceiptMinutes", "issueAlertReceiptCountThreshold",
    "issueAlertReceiptTimeoutMinutes", "failedEventCount", "retryableFailedEventCount",
    "deadLetterEventCount", "unknownRefundSubmitEventCount", "failedRefundCount",
    "retryableFailedRefundCount", "blockedFailedRefundCount", "warningDayEndBatchCount",
    "enabledControlPolicyCount", "controlPolicyWarningCount", "overdueIssueCount",
    "pendingIssueAlertGenerationCount", "pendingIssueAlertEscalationCount",
    "actionableIssueSlaCount"
  ];
  const positiveThresholdFields = [
    "queryMonitorWindowHours", "queryFailureCountThreshold",
    "queryFailureRateThresholdPercent", "queryFailureRateMinimumSampleCount",
    "queryRateLimitRejectionCountThreshold", "issueAlertDispatchCountThreshold",
    "issueAlertDispatchTimeoutMinutes", "issueAlertReceiptCountThreshold",
    "issueAlertReceiptTimeoutMinutes"
  ];
  let invalidRecentTaskRun = false;
  let invalidQueryTrend = false;
  let invalidFocusAlert = false;
  if (data && typeof data === "object" && !Array.isArray(data)
      && Array.isArray(data.recentTaskRuns)) {
    try {
      data.recentTaskRuns.forEach((item) => normalizePaymentTaskRunDetailResult(item));
    } catch (error) {
      invalidRecentTaskRun = true;
    }
  }
  if (data && typeof data === "object" && !Array.isArray(data)
      && Array.isArray(data.queryTrends)) {
    invalidQueryTrend = data.queryTrends.some((item) => !item || typeof item !== "object" || Array.isArray(item)
      || !isValidHourBucket(item.timeBucket)
      || !Number.isInteger(item.queryCount) || item.queryCount < 0
      || !Number.isInteger(item.failureCount) || item.failureCount < 0
      || item.failureCount > item.queryCount
      || !isConsistentSuccessRate(item.failureCount, item.queryCount, item.failureRate));
  }
  if (data && typeof data === "object" && !Array.isArray(data)
      && Array.isArray(data.focusAlerts)) {
    invalidFocusAlert = data.focusAlerts.some((item) => !item || typeof item !== "object" || Array.isArray(item)
      || typeof item.alertType !== "string" || !item.alertType.trim()
      || typeof item.alertTitle !== "string" || !item.alertTitle.trim()
      || typeof item.alertMessage !== "string" || !item.alertMessage.trim()
      || !["P1", "P2", "P3"].includes(item.alertLevel)
      || !["danger", "warn", "success"].includes(item.alertLevelType)
      || !Number.isInteger(item.affectedCount) || item.affectedCount < 0
      || (item.affectedCount === 0
        && (item.alertLevel !== "P3" || item.alertLevelType !== "success"))
      || (item.affectedCount > 0
        && (item.alertLevel === "P3" || item.alertLevelType === "success"))
      || typeof item.suggestedAction !== "string" || !item.suggestedAction.trim()
      || typeof item.actionRoute !== "string" || !item.actionRoute.trim());
  }
  const invalid = !data || typeof data !== "object" || Array.isArray(data)
    || ["queryTrends", "focusAlerts", "recentTaskRuns"]
      .some((field) => !Array.isArray(data[field]))
    || invalidRecentTaskRun
    || invalidQueryTrend
    || invalidFocusAlert
    || countFields.some((field) => !Number.isInteger(data[field]) || data[field] < 0)
    || positiveThresholdFields.some((field) => !Number.isInteger(data[field]) || data[field] <= 0)
    || data.queryFailureRateThresholdPercent > 100
    || typeof data.recentQueryFailureRate !== "string"
    || !isConsistentSuccessRate(
      data.recentQueryFailureCount,
      data.recentQueryCount,
      data.recentQueryFailureRate
    )
    || (data.expiredPaymentCount != null
      && data.dueExpiredPaymentCount != null
      && data.backingOffExpiredPaymentCount != null
      && data.dueExpiredPaymentCount + data.backingOffExpiredPaymentCount > data.expiredPaymentCount)
    || (data.pendingQueryBacklogCount != null
      && data.duePendingQueryCount != null
      && data.duePendingQueryCount > data.pendingQueryBacklogCount)
    || (data.pendingRefundQueryBacklogCount != null
      && data.duePendingRefundQueryCount != null
      && data.duePendingRefundQueryCount > data.pendingRefundQueryBacklogCount)
    || (data.recentQueryCount != null
      && data.recentQueryFailureCount != null
      && data.recentQueryFailureCount > data.recentQueryCount)
    || (data.failedEventCount != null
      && data.retryableFailedEventCount != null
      && data.deadLetterEventCount != null
      && data.retryableFailedEventCount + data.deadLetterEventCount !== data.failedEventCount)
    || (data.failedRefundCount != null
      && data.retryableFailedRefundCount != null
      && data.blockedFailedRefundCount != null
      && data.retryableFailedRefundCount + data.blockedFailedRefundCount !== data.failedRefundCount)
    || (data.pendingIssueAlertGenerationCount != null
      && data.pendingIssueAlertEscalationCount != null
      && data.actionableIssueSlaCount != null
      && data.pendingIssueAlertGenerationCount + data.pendingIssueAlertEscalationCount
        !== data.actionableIssueSlaCount);
  if (invalid) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-TASK-CENTER-OVERVIEW" },
      "支付任务中心总览返回了无法确认的任务统计事实"
    );
  }
  return data;
}

export function normalizePaymentDayEndOverviewResult(data) {
  const countFields = [
    "totalBatchCount", "completedBatchCount", "abnormalBatchCount",
    "openChannelAbnormalCount", "openInternalAbnormalCount", "openPendingPaymentCount",
    "openMissingPaymentSuccessTimeCount", "openPaymentSuccessFactMismatchCount",
    "openPendingRefundCount", "openRefundFailCount", "openMissingRefundSuccessTimeCount",
    "openRefundTerminalFactMismatchCount", "latestChannelSuccessCount",
    "latestInternalSuccessCount", "latestPaymentSuccessGapCount"
  ];
  const requiredCountFields = [
    "totalBatchCount", "completedBatchCount", "abnormalBatchCount",
    "openChannelAbnormalCount", "openInternalAbnormalCount", "openPendingRefundCount",
    "latestChannelSuccessCount", "latestInternalSuccessCount", "latestPaymentSuccessGapCount"
  ];
  const amountFields = [
    "latestChannelSuccessAmount", "latestInternalSuccessAmount",
    "latestPaymentSuccessGapAmount", "latestPendingPaymentAmount",
    "latestPendingRefundAmount", "latestRefundFailAmount"
  ];
  let invalidRecentBatch = false;
  let invalidAlert = false;
  if (data && typeof data === "object" && !Array.isArray(data)
      && Array.isArray(data.recentBatches)) {
    try {
      data.recentBatches.forEach((item) => normalizePaymentDayEndRecentBatchResult(item));
    } catch (error) {
      invalidRecentBatch = true;
    }
  }
  if (data && typeof data === "object" && !Array.isArray(data)
      && Array.isArray(data.alerts)) {
    try {
      data.alerts.forEach((item) => {
        if (!item || typeof item !== "object" || Array.isArray(item)
            || typeof item.alertType !== "string" || !item.alertType.trim()
            || typeof item.alertTitle !== "string" || !item.alertTitle.trim()
            || !Number.isInteger(item.affectedCount) || item.affectedCount < 0
            || typeof item.alertMessage !== "string" || !item.alertMessage.trim()
            || typeof item.suggestedAction !== "string" || !item.suggestedAction.trim()
            || typeof item.actionRoute !== "string" || !item.actionRoute.trim()
            || !["P1", "P2", "P3"].includes(item.severityLevel)
            || !["danger", "warn", "success"].includes(item.severityLevelType)) {
          throw new Error("invalid day-end alert");
        }
        if ((item.affectedCount === 0
          && (item.severityLevel !== "P3" || item.severityLevelType !== "success"))
          || (item.affectedCount > 0
            && (item.severityLevel === "P3" || item.severityLevelType === "success"))) {
          throw new Error("inconsistent day-end alert");
        }
      });
    } catch (error) {
      invalidAlert = true;
    }
  }
  const invalid = !data || typeof data !== "object" || Array.isArray(data)
    || !Array.isArray(data.alerts)
    || !Array.isArray(data.recentBatches)
    || invalidRecentBatch
    || invalidAlert
    || requiredCountFields.some((field) => !Number.isInteger(data[field]) || data[field] < 0)
    || countFields.some((field) => data[field] != null
      && (!Number.isInteger(data[field]) || data[field] < 0))
    || (data.latestBizDate != null && !isValidDateOnly(data.latestBizDate))
    || typeof data.latestBatchStatus !== "string"
    || (!isKnownPaymentDayEndBatchStatus(data.latestBatchStatus)
      && data.latestBatchStatus.trim() !== "未跑批")
    || amountFields.some((field) => data[field] != null && !Number.isFinite(normalizeNonNegativeAmount(data[field])))
    || [
      "reconciliationReadinessStatus", "reconciliationReadinessType",
      "reconciliationReadinessSummary", "reconciliationSuggestedAction",
      "reconciliationBlockingOwner"
    ].some((field) => typeof data[field] !== "string" || !data[field].trim())
    || !["danger", "warn", "success"].includes(data.reconciliationReadinessType)
    || data.completedBatchCount + data.abnormalBatchCount !== data.totalBatchCount;
  if (invalid) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-DAY-END-OVERVIEW" },
      "支付日终总览返回了无法确认的批次统计事实"
    );
  }
  return data;
}

export function normalizePaymentDayEndRecentBatchResult(data) {
  const countFields = [
    "paymentTotalCount", "paymentSuccessCount", "channelSuccessCount", "internalSuccessCount",
    "paymentSuccessGapCount", "pendingPaymentCount", "missingPaymentSuccessTimeCount",
    "paymentSuccessFactMismatchCount", "refundSuccessCount", "refundFailCount",
    "missingRefundSuccessTimeCount", "refundTerminalFactMismatchCount", "channelAbnormalCount",
    "internalAbnormalCount", "pendingRefundCount"
  ];
  const requiredCountFields = [
    "paymentTotalCount", "paymentSuccessCount", "channelSuccessCount", "internalSuccessCount",
    "paymentSuccessGapCount", "refundSuccessCount", "channelAbnormalCount",
    "internalAbnormalCount", "pendingRefundCount"
  ];
  const amountFields = [
    "paymentSuccessAmount", "channelSuccessAmount", "internalSuccessAmount",
    "paymentSuccessGapAmount", "pendingPaymentAmount", "refundSuccessAmount",
    "refundFailAmount", "pendingRefundAmount"
  ];
  const requiredAmountFields = [
    "paymentSuccessAmount", "channelSuccessAmount", "internalSuccessAmount",
    "paymentSuccessGapAmount", "refundSuccessAmount", "pendingRefundAmount"
  ];
  const invalid = !data || typeof data !== "object" || Array.isArray(data)
    || typeof data.batchNo !== "string" || !data.batchNo.trim()
    || !isValidDateOnly(data.bizDate)
    || typeof data.runMode !== "string" || !data.runMode.trim()
    || typeof data.batchStatus !== "string"
    || !isKnownPaymentDayEndBatchStatus(data.batchStatus)
    || typeof data.batchStatusType !== "string"
    || !isConsistentPaymentDayEndBatchStatusType(data.batchStatus, data.batchStatusType)
    || requiredCountFields.some((field) => !Number.isInteger(data[field]) || data[field] < 0)
    || countFields.some((field) => data[field] != null
      && (!Number.isInteger(data[field]) || data[field] < 0))
    || requiredAmountFields.some((field) => !Number.isFinite(normalizeNonNegativeAmount(data[field])))
    || amountFields.some((field) => data[field] != null
      && !Number.isFinite(normalizeNonNegativeAmount(data[field])))
    || typeof data.reconciliationReadinessStatus !== "string"
    || !data.reconciliationReadinessStatus.trim()
    || !["danger", "warn", "success"].includes(data.reconciliationReadinessType)
    || typeof data.reconciliationReadinessSummary !== "string"
    || !data.reconciliationReadinessSummary.trim()
    || typeof data.summaryComment !== "string"
    || typeof data.triggeredBy !== "string" || !data.triggeredBy.trim()
    || !isConsistentPaymentDayEndBatchTimes(data.createdAt, data.completedAt)
    || data.paymentSuccessCount > data.paymentTotalCount
    || data.channelSuccessCount > data.paymentTotalCount
    || data.internalSuccessCount > data.paymentTotalCount
    || data.paymentSuccessGapCount > data.paymentTotalCount
    || data.refundSuccessCount < 0;
  if (invalid) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-DAY-END-OVERVIEW" },
      "支付日终最近批次返回了无法确认的批次事实"
    );
  }
  return data;
}

function isKnownPaymentDayEndBatchStatus(value) {
  return typeof value === "string"
    && ["COMPLETED", "WARNING"].includes(value.trim().toUpperCase());
}

function isConsistentPaymentDayEndBatchStatusType(status, statusType) {
  if (typeof statusType !== "string" || !statusType.trim()) {
    return false;
  }
  const normalizedStatus = status.trim().toUpperCase();
  const normalizedType = statusType.trim().toLowerCase();
  return (normalizedStatus === "WARNING" && normalizedType === "warn")
    || (normalizedStatus === "COMPLETED" && normalizedType === "success");
}

function isConsistentPaymentDayEndBatchTimes(createdAt, completedAt) {
  if (!isValidDateTime(createdAt) || !isValidDateTime(completedAt)) {
    return false;
  }
  return Date.parse(completedAt.trim().replace(" ", "T"))
    >= Date.parse(createdAt.trim().replace(" ", "T"));
}

export function normalizePaymentMetricsResult(data) {
  const countFields = ["totalCount", "successCount", "pendingCount", "closedCount"];
  if (!data || typeof data !== "object" || Array.isArray(data)
      || countFields.some((field) => !Number.isInteger(data[field]) || data[field] < 0)
      || data.successCount > data.totalCount
      || data.pendingCount > data.totalCount
      || data.closedCount > data.totalCount
      || !Number.isFinite(normalizeNonNegativeAmount(data.successAmount))
      || !isConsistentSuccessRate(data.successCount, data.totalCount, data.successRate)) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-METRICS" },
      "支付核心指标返回了无法确认的统计事实"
    );
  }
  return data;
}

export function normalizePaymentEventOverviewResult(data) {
  const countFields = [
    "totalEventCount", "successEventCount", "pendingEventCount", "failedEventCount",
    "deadLetterEventCount", "unknownEventCount", "failedOrDeadLetterCount",
    "distinctDownstreamCount", "dueRetryEventCount", "paymentSuccessEventCount"
  ];
  if (!data || typeof data !== "object" || Array.isArray(data)
      || countFields.some((field) => !Number.isInteger(data[field]) || data[field] < 0)
      || data.successEventCount + data.pendingEventCount + data.failedEventCount
        + data.deadLetterEventCount + data.unknownEventCount !== data.totalEventCount
      || data.failedOrDeadLetterCount !== data.failedEventCount + data.deadLetterEventCount
      || data.distinctDownstreamCount > data.totalEventCount
      || data.paymentSuccessEventCount > data.totalEventCount
      || data.paymentSuccessEventCount > data.successEventCount
      || data.dueRetryEventCount > data.failedEventCount
      || (data.latestPublishedAt != null && !isValidDateTime(data.latestPublishedAt))) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-EVENT-OVERVIEW" },
      "支付事件总览返回了无法确认的统计事实"
    );
  }
  return data;
}

export function normalizePaymentRequestOverviewResult(data) {
  const countFields = [
    "totalRequestCount", "successRequestCount", "failedRequestCount",
    "processingRequestCount", "waitingCallbackRequestCount", "distinctTerminalCount",
    "distinctChannelCount", "repeatedPaymentOrderCount", "missingResponseCount"
  ];
  if (!data || typeof data !== "object" || Array.isArray(data)
      || countFields.some((field) => !Number.isInteger(data[field]) || data[field] < 0)
      || data.successRequestCount + data.failedRequestCount + data.processingRequestCount
        + data.waitingCallbackRequestCount !== data.totalRequestCount
      || data.distinctTerminalCount > data.totalRequestCount
      || data.distinctChannelCount > data.totalRequestCount
      || data.repeatedPaymentOrderCount > data.totalRequestCount
      || data.missingResponseCount > data.totalRequestCount
      || (data.latestRequestAt != null && !isValidDateTime(data.latestRequestAt))) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-REQUEST-OVERVIEW" },
      "支付请求总览返回了无法确认的统计事实"
    );
  }
  return data;
}

export function normalizePaymentLogOverviewResult(data) {
  const countFields = [
    "totalLogCount", "errorLogCount", "warnLogCount", "infoLogCount",
    "distinctStageCount", "distinctSourceCount", "callbackErrorCount",
    "eventWarnCount", "callbackKeywordCount"
  ];
  if (!data || typeof data !== "object" || Array.isArray(data)
      || countFields.some((field) => !Number.isInteger(data[field]) || data[field] < 0)
      || data.errorLogCount + data.warnLogCount + data.infoLogCount > data.totalLogCount
      || data.distinctStageCount > data.totalLogCount
      || data.distinctSourceCount > data.totalLogCount
      || data.callbackErrorCount > data.totalLogCount
      || data.eventWarnCount > data.totalLogCount
      || data.callbackKeywordCount > data.totalLogCount
      || (data.latestLogAt != null && !isValidDateTime(data.latestLogAt))) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-LOG-OVERVIEW" },
      "支付日志总览返回了无法确认的统计事实"
    );
  }
  return data;
}

export function normalizePaymentRouteOverviewResult(data) {
  const countFields = [
    "totalRouteCount", "successRouteCount", "warnRouteCount", "distinctChannelCount",
    "offlineRouteCount", "wechatRouteCount", "alipayRouteCount"
  ];
  const policyFields = [
    "routeHealthLookbackHours", "routeHealthFailureCountThreshold",
    "routeHealthFailureRateThresholdPercent", "routeHealthMinimumSampleCount"
  ];
  if (!data || typeof data !== "object" || Array.isArray(data)
      || countFields.some((field) => !Number.isInteger(data[field]) || data[field] < 0)
      || data.successRouteCount + data.warnRouteCount !== data.totalRouteCount
      || data.distinctChannelCount > data.totalRouteCount
      || data.offlineRouteCount > data.totalRouteCount
      || data.wechatRouteCount > data.totalRouteCount
      || data.alipayRouteCount > data.totalRouteCount
      || typeof data.routeHealthEnabled !== "boolean"
      || policyFields.some((field) => !Number.isInteger(data[field]) || data[field] <= 0)
      || data.routeHealthFailureRateThresholdPercent > 100
      || (data.latestRouteAt != null && !isValidDateTime(data.latestRouteAt))) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-ROUTE-OVERVIEW" },
      "支付路由总览返回了无法确认的统计或健康策略"
    );
  }
  return data;
}

export function normalizeCashierSessionOverviewResult(data) {
  const countFields = [
    "totalSessionCount", "expiredSessionCount", "successSessionCount", "payingSessionCount",
    "pendingSessionCount", "otherSessionCount", "distinctTerminalCount", "expiringSoonCount"
  ];
  const amount = normalizeNonNegativeAmount(data?.totalAmount);
  if (!data || typeof data !== "object" || Array.isArray(data)
      || countFields.some((field) => !Number.isInteger(data[field]) || data[field] < 0)
      || data.expiredSessionCount + data.successSessionCount + data.payingSessionCount
        + data.pendingSessionCount + data.otherSessionCount !== data.totalSessionCount
      || data.distinctTerminalCount > data.totalSessionCount
      || data.expiringSoonCount > data.totalSessionCount
      || typeof data.totalAmount !== "string" || !data.totalAmount.trim()
      || !Number.isFinite(amount) || amount < 0) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-CASHIER-OVERVIEW" },
      "收银台会话总览返回了无法确认的统计事实"
    );
  }
  return data;
}

export function normalizeBillOverviewResult(data) {
  const countFields = [
    "totalBillCount", "paidBillCount", "unpaidBillCount", "partialPaidBillCount", "overdueBillCount"
  ];
  const amountFields = ["totalBillAmount", "totalPaidAmount", "totalUnpaidAmount"];
  const invalidAmount = (field) => !Number.isFinite(normalizeNonNegativeAmount(data?.[field]));
  const totalAmount = normalizeNonNegativeAmount(data?.totalBillAmount);
  const paidAmount = normalizeNonNegativeAmount(data?.totalPaidAmount);
  const unpaidAmount = normalizeNonNegativeAmount(data?.totalUnpaidAmount);
  const totalAmountForComparison = normalizeNonNegativeAmountForComparison(data?.totalBillAmount);
  const paidAmountForComparison = normalizeNonNegativeAmountForComparison(data?.totalPaidAmount);
  const unpaidAmountForComparison = normalizeNonNegativeAmountForComparison(data?.totalUnpaidAmount);
  if (!data || typeof data !== "object" || Array.isArray(data)
      || countFields.some((field) => !Number.isInteger(data[field]) || data[field] < 0)
      || data.paidBillCount + data.unpaidBillCount + data.partialPaidBillCount !== data.totalBillCount
      || data.overdueBillCount > data.totalBillCount
      || amountFields.some(invalidAmount)) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-BILL-OVERVIEW" },
      "账单总览返回了无法确认的统计事实"
    );
  }
  if (totalAmountForComparison
      !== addNormalizedAmounts(paidAmountForComparison, unpaidAmountForComparison)) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-BILL-OVERVIEW" },
      "账单总览返回了无法确认的金额连续性事实"
    );
  }
  return data;
}

export function normalizeWorkerSettlementOverviewResult(data) {
  const countFields = [
    "totalSettlementCount", "pendingAuditCount", "payoutPendingCount",
    "payingCount", "payoutSuccessCount", "negativeNetSettleCount"
  ];
  const invalidNetSettleAmount = !normalizeSignedAmountForComparison(data?.totalNetSettleAmount);
  const invalidDeductAmount = !Number.isFinite(normalizeNonNegativeAmount(data?.totalDeductAmount));
  const invalidDepositImpactAmount = !normalizeSignedAmountForComparison(data?.totalDepositImpactAmount);
  if (!data || typeof data !== "object" || Array.isArray(data)
      || countFields.some((field) => !Number.isInteger(data[field]) || data[field] < 0)
      || data.pendingAuditCount > data.totalSettlementCount
      || data.payoutPendingCount + data.payingCount + data.payoutSuccessCount
        > data.totalSettlementCount
      || data.negativeNetSettleCount > data.totalSettlementCount
      || invalidNetSettleAmount
      || invalidDeductAmount
      || invalidDepositImpactAmount) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-SETTLEMENT-OVERVIEW" },
      "服务者结算总览返回了无法确认的统计事实"
    );
  }
  return data;
}

export function normalizeWorkerSettlementListPage(data) {
  const normalizedPage = normalizePageResponse(data);
  const settlementStatusTypes = { "待审核": "warn", "待出款": "info", "已完成": "success" };
  const payoutStatusTypes = { "待出款": "info", "出款中": "warn", "出款成功": "success" };
  return {
    ...normalizedPage,
    items: normalizedPage.items.map((item) => {
      const shouldSettle = normalizeNonNegativeAmountForComparison(item?.amountShouldSettle);
      const deduct = normalizeNonNegativeAmountForComparison(item?.deductAmount);
      const netSettle = normalizeSignedAmountForComparison(item?.amountNetSettle);
      const depositImpact = normalizeSignedAmountForComparison(item?.depositImpactAmount);
      if (!item || typeof item !== "object" || Array.isArray(item)
          || typeof item.settlementOrderId !== "string"
          || !item.settlementOrderId.trim()
          || typeof item.workerName !== "string"
          || !item.workerName.trim()
          || typeof item.period !== "string"
          || !item.period.trim()
          || !shouldSettle
          || !deduct
          || !netSettle
          || !depositImpact
          || netSettle !== subtractNormalizedAmounts(shouldSettle, deduct)
          || !isConsistentPaymentIssueStatus(item.status, item.statusType, settlementStatusTypes)
          || !isConsistentPaymentIssueStatus(item.payoutStatus, item.payoutStatusType, payoutStatusTypes)) {
        throw createBusinessError(
          { code: "PAYMENT-CLIENT-INVALID-SETTLEMENT-LIST" },
          "服务者结算列表返回了无法确认的金额状态事实"
        );
      }
      return item;
    })
  };
}

export function normalizeDashboardSummaryResult(data) {
  if (!data || typeof data !== "object" || Array.isArray(data)
      || !Array.isArray(data.cards)
      || data.cards.some((card) => !card || typeof card !== "object" || Array.isArray(card)
        || ["key", "title", "value", "badgeType", "badgeText"]
          .some((field) => typeof card[field] !== "string" || !card[field].trim()))) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-DASHBOARD-SUMMARY" },
      "支付工作台总览返回了无法确认的卡片事实"
    );
  }
  return data;
}

export const dashboardApi = {
  getSummary: () => request("/api/dashboard/summary").then(normalizeDashboardSummaryResult)
};

export const paymentMetricsApi = {
  getSummary: () => request("/api/payment-metrics/summary").then(normalizePaymentMetricsResult)
};

export const paymentMonitorApi = {
  getOverview: () => request("/api/payment-monitor/overview").then(normalizePaymentMonitorOverviewResult),
  buildExportUrl: () => "/api/payment-monitor/export"
};

export const paymentIssueApi = {
  getDetail: (issueNo) => {
    const normalizedIssueNo = normalizeBusinessIdentifier(issueNo);
    return request(`/api/payment-issues/${encodeURIComponent(normalizedIssueNo)}`)
      .then((data) => normalizePaymentIssueDetailResult(data, normalizedIssueNo));
  },
  getAlertDetail: (alertNo) => {
    const normalizedAlertNo = normalizeBusinessIdentifier(alertNo);
    return request(`/api/payment-issues/alerts/${encodeURIComponent(normalizedAlertNo)}`)
      .then((data) => normalizePaymentIssueAlertDetailResult(data, normalizedAlertNo));
  },
  getList: ({
    paymentOrderId = "",
    orderNo = "",
    issueType = "全部",
    severity = "全部",
    channelCode = "",
    paymentMethod = "全部",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const normalizedPaymentOrderId = normalizeBusinessIdentifier(paymentOrderId) || "";
    const normalizedOrderNo = normalizeBusinessIdentifier(orderNo) || "";
    const params = new URLSearchParams({
      paymentOrderId: normalizedPaymentOrderId,
      orderNo: normalizedOrderNo,
      issueType,
      severity,
      channelCode,
      paymentMethod,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/payment-issues?${params.toString()}`)
      .then(normalizePaymentIssueListPage);
  },
  getResponsibilitySummary: ({
    paymentOrderId = "",
    orderNo = "",
    issueType = "全部",
    severity = "全部",
    channelCode = "",
    paymentMethod = "全部"
  } = {}) => {
    const normalizedPaymentOrderId = normalizeBusinessIdentifier(paymentOrderId) || "";
    const normalizedOrderNo = normalizeBusinessIdentifier(orderNo) || "";
    const params = new URLSearchParams({
      paymentOrderId: normalizedPaymentOrderId,
      orderNo: normalizedOrderNo,
      issueType,
      severity,
      channelCode,
      paymentMethod
    });
    return requestCollection(`/api/payment-issues/responsibility-summary?${params.toString()}`)
      .then(normalizePaymentIssueResponsibilitySummaryResult);
  },
  getAlertLogs: ({
    alertNo = "",
    issueNo = "",
    paymentOrderId = "",
    alertChannel = "全部",
    alertStatus = "全部",
    ackStatus = "全部",
    providerDeliveryStatus = "全部",
    receiptQueryState = "全部",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const normalizedAlertNo = normalizeBusinessIdentifier(alertNo) || "";
    const normalizedIssueNo = normalizeBusinessIdentifier(issueNo) || "";
    const normalizedPaymentOrderId = normalizeBusinessIdentifier(paymentOrderId) || "";
    const params = new URLSearchParams({
      alertNo: normalizedAlertNo,
      issueNo: normalizedIssueNo,
      paymentOrderId: normalizedPaymentOrderId,
      alertChannel,
      alertStatus,
      ackStatus,
      providerDeliveryStatus,
      receiptQueryState,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/payment-issues/alerts?${params.toString()}`)
      .then(normalizePaymentIssueAlertListPage);
  },
  buildAlertExportUrl: ({
    alertNo = "",
    issueNo = "",
    paymentOrderId = "",
    alertChannel = "全部",
    alertStatus = "全部",
    ackStatus = "全部",
    providerDeliveryStatus = "全部",
    receiptQueryState = "全部"
  } = {}) => {
    const normalizedAlertNo = normalizeBusinessIdentifier(alertNo) || "";
    const normalizedIssueNo = normalizeBusinessIdentifier(issueNo) || "";
    const normalizedPaymentOrderId = normalizeBusinessIdentifier(paymentOrderId) || "";
    const params = new URLSearchParams({
      alertNo: normalizedAlertNo,
      issueNo: normalizedIssueNo,
      paymentOrderId: normalizedPaymentOrderId,
      alertChannel,
      alertStatus,
      ackStatus,
      providerDeliveryStatus,
      receiptQueryState
    });
    return `/api/payment-issues/alerts/export?${params.toString()}`;
  },
  acknowledgeAlert: (alertNo, payload, expectedSnapshot = null) => {
    const normalizedAlertNo = normalizeBusinessIdentifier(alertNo);
    return postJson(
      `/api/payment-issues/alerts/${encodeURIComponent(normalizedAlertNo)}/acknowledge`,
      payload
    ).then((data) => normalizePaymentIssueAlertActionResult(data, normalizedAlertNo, expectedSnapshot));
  },
  reconcileAlertReceipt: (alertNo, payload) => {
    const normalizedAlertNo = normalizeBusinessIdentifier(alertNo);
    return postJson(
      `/api/payment-task-center/reconcile-issue-alert-receipts/${encodeURIComponent(normalizedAlertNo)}`,
      payload
    ).then((data) => normalizeTaskActionResult(
      data,
      "PAYMENT_ISSUE_ALERT_RECEIPT_RECONCILE"
    ));
  },
  batchAction: (payload) => {
    const issueNos = Array.isArray(payload?.issueNos)
      ? payload.issueNos.map((issueNo) => normalizeBusinessIdentifier(issueNo))
      : payload?.issueNos;
    return postJson("/api/payment-issues/actions", { ...payload, issueNos })
      .then((data) => normalizePaymentIssueBatchActionResult(data, issueNos));
  }
};

export const paymentDayEndApi = {
  getOverview: () => request("/api/payment-day-end/overview")
    .then(normalizePaymentDayEndOverviewResult),
  getDetail: (batchNo) => {
    const normalizedBatchNo = normalizeBusinessIdentifier(batchNo);
    return request(`/api/payment-day-end/batches/${encodeURIComponent(normalizedBatchNo)}`)
      .then((data) => normalizePaymentDayEndDetailResult(data, normalizedBatchNo));
  },
  runBatch: (payload) => {
    const normalizedPayload = payload && typeof payload === "object"
      ? {
        ...payload,
        bizDate: normalizeBusinessIdentifier(payload.bizDate)
      }
      : payload;
    return request("/api/payment-day-end/run", buildJsonRequestOptions("POST", normalizedPayload))
      .then((data) => normalizePaymentDayEndRunResult(data, normalizedPayload?.bizDate));
  },
  buildExportUrl: () => "/api/payment-day-end/export"
};

export const paymentTaskCenterApi = {
  getOverview: () => request("/api/payment-task-center/overview")
    .then(normalizePaymentTaskCenterOverviewResult),
  getTaskRunDetail: (taskLogNo) => {
    const normalizedTaskLogNo = normalizeBusinessIdentifier(taskLogNo);
    return request(`/api/payment-task-center/task-runs/${encodeURIComponent(normalizedTaskLogNo)}`)
      .then((data) => normalizePaymentTaskRunDetailResult(data, normalizedTaskLogNo));
  },
  getTaskRuns: ({
    taskCode = "",
    bizDate = "",
    runMode = "全部",
    taskStatus = "全部",
    severityLevel = "全部",
    pageNo = 1,
    pageSize = 10
  } = {}) => {
    const params = new URLSearchParams({
      taskCode,
      bizDate,
      runMode,
      taskStatus,
      severityLevel,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/payment-task-center/task-runs?${params.toString()}`)
      .then(normalizePaymentTaskRunsPage);
  },
  buildExportUrl: ({
    taskCode = "",
    bizDate = "",
    runMode = "全部",
    taskStatus = "全部",
    severityLevel = "全部"
  } = {}) => {
    const params = new URLSearchParams({
      taskCode,
      bizDate,
      runMode,
      taskStatus,
      severityLevel
    });
    return `/api/payment-task-center/task-runs/export?${params.toString()}`;
  },
  runCloseExpiredPayments: (payload, expectedTaskCode = "") => postJson("/api/payment-task-center/close-expired-payments", payload).then((data) => normalizeTaskActionResult(data, expectedTaskCode)),
  runPendingPaymentQueries: (payload, expectedTaskCode = "") => postJson("/api/payment-task-center/query-pending-payments", payload).then((data) => normalizeTaskActionResult(data, expectedTaskCode)),
  runPendingRefundQueries: (payload, expectedTaskCode = "") => postJson("/api/payment-task-center/query-pending-refunds", payload).then((data) => normalizeTaskActionResult(data, expectedTaskCode)),
  runGenerateQueryMonitorAlerts: (payload, expectedTaskCode = "") => postJson("/api/payment-task-center/generate-query-monitor-alerts", payload).then((data) => normalizeTaskActionResult(data, expectedTaskCode)),
  runRepublishFailedEvents: (payload, expectedTaskCode = "") => postJson("/api/payment-task-center/republish-failed-events", payload).then((data) => normalizeTaskActionResult(data, expectedTaskCode)),
  runRetryFailedRefunds: (payload, expectedTaskCode = "") => postJson("/api/payment-task-center/retry-failed-refunds", payload).then((data) => normalizeTaskActionResult(data, expectedTaskCode)),
  runEscalateOverdueIssues: (payload, expectedTaskCode = "") => postJson("/api/payment-task-center/escalate-overdue-issues", payload).then((data) => normalizeTaskActionResult(data, expectedTaskCode)),
  runDispatchIssueAlerts: (payload, expectedTaskCode = "") => postJson("/api/payment-task-center/dispatch-issue-alerts", payload).then((data) => normalizeTaskActionResult(data, expectedTaskCode)),
  runReconcileIssueAlertReceipts: (payload, expectedTaskCode = "") => postJson("/api/payment-task-center/reconcile-issue-alert-receipts", payload).then((data) => normalizeTaskActionResult(data, expectedTaskCode)),
  runControlPolicySelfChecks: (payload, expectedTaskCode = "") => postJson("/api/payment-task-center/control-policy-self-checks", payload).then((data) => normalizeTaskActionResult(data, expectedTaskCode))
};

export const orderApi = {
  getList: ({
    orderNo = "",
    serviceType = "全部",
    orderStatus = "全部",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      orderNo,
      serviceType,
      orderStatus,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/orders?${params.toString()}`)
      .then(normalizeOrderListPage);
  },
  buildExportUrl: ({
    orderNo = "",
    serviceType = "全部",
    orderStatus = "全部"
  } = {}) => {
    const params = new URLSearchParams({
      orderNo,
      serviceType,
      orderStatus
    });
    return `/api/orders/export?${params.toString()}`;
  },
  getDetail: (orderNo) => {
    const normalizedOrderNo = normalizeBusinessIdentifier(orderNo);
    return request(`/api/orders/${encodeURIComponent(normalizedOrderNo)}`)
      .then((data) => normalizeOrderDetailResult(data, normalizedOrderNo));
  }
};

export const billApi = {
  getOverview: ({
    billNo = "",
    orderNo = "",
    customerName = "",
    billStatus = "全部",
    sortField = "createdAt",
    sortOrder = "desc"
  } = {}) => {
    const params = new URLSearchParams({
      billNo,
      orderNo,
      customerName,
      billStatus,
      sortField,
      sortOrder
    });
    return request(`/api/bills/overview?${params.toString()}`)
      .then(normalizeBillOverviewResult);
  },
  getList: ({
    billNo = "",
    orderNo = "",
    customerName = "",
    billStatus = "全部",
    sortField = "createdAt",
    sortOrder = "desc",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      billNo,
      orderNo,
      customerName,
      billStatus,
      sortField,
      sortOrder,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/bills?${params.toString()}`)
      .then(normalizeBillListPage);
  },
  buildExportUrl: ({
    billNo = "",
    orderNo = "",
    customerName = "",
    billStatus = "全部",
    sortField = "createdAt",
    sortOrder = "desc"
  } = {}) => {
    const params = new URLSearchParams({
      billNo,
      orderNo,
      customerName,
      billStatus,
      sortField,
      sortOrder
    });
    return `/api/bills/export?${params.toString()}`;
  },
  getDetail: (billNo) => {
    const normalizedBillNo = normalizeBusinessIdentifier(billNo);
    return request(`/api/bills/${encodeURIComponent(normalizedBillNo)}`)
      .then((data) => normalizeBillDetailResult(data, normalizedBillNo));
  }
};

export const paymentFlowApi = {
  getList: ({
    paymentOrderId = "",
    orderNo = "",
    flowType = "全部",
    channelCode = "",
    terminal = "全部",
    businessStatus = "全部",
    keyword = "",
    sortField = "createdAt",
    sortOrder = "desc",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      orderNo,
      flowType,
      channelCode,
      terminal,
      businessStatus,
      keyword,
      sortField,
      sortOrder,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/payment-flows?${params.toString()}`)
      .then(normalizePaymentFlowListPage);
  },
  buildExportUrl: ({
    paymentOrderId = "",
    orderNo = "",
    flowType = "全部",
    channelCode = "",
    terminal = "全部",
    businessStatus = "全部",
    keyword = "",
    sortField = "createdAt",
    sortOrder = "desc"
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      orderNo,
      flowType,
      channelCode,
      terminal,
      businessStatus,
      keyword,
      sortField,
      sortOrder
    });
    return `/api/payment-flows/export?${params.toString()}`;
  },
  getDetail: (flowSource, flowNo) => {
    const normalizedFlowSource = normalizeBusinessIdentifier(flowSource);
    const normalizedFlowNo = normalizeBusinessIdentifier(flowNo);
    return request(
      `/api/payment-flows/${encodeURIComponent(normalizedFlowSource)}/${encodeURIComponent(normalizedFlowNo)}`
    ).then((data) => normalizePaymentFlowDetailResult(data, normalizedFlowSource, normalizedFlowNo));
  }
};

export const paymentRouteApi = {
  getOverview: ({
    paymentOrderId = "",
    orderNo = "",
    routeRule = "",
    channelCode = "",
    paymentMethod = "全部",
    terminal = "全部",
    routeResult = "全部",
    sortField = "createdAt",
    sortOrder = "desc"
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      orderNo,
      routeRule,
      channelCode,
      paymentMethod,
      terminal,
      routeResult,
      sortField,
      sortOrder
    });
    return request(`/api/payment-routes/overview?${params.toString()}`)
      .then(normalizePaymentRouteOverviewResult);
  },
  getList: ({
    paymentOrderId = "",
    orderNo = "",
    routeRule = "",
    channelCode = "",
    paymentMethod = "全部",
    terminal = "全部",
    routeResult = "全部",
    sortField = "createdAt",
    sortOrder = "desc",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      orderNo,
      routeRule,
      channelCode,
      paymentMethod,
      terminal,
      routeResult,
      sortField,
      sortOrder,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/payment-routes?${params.toString()}`)
      .then(normalizePaymentRouteListPage);
  },
  buildExportUrl: ({
    paymentOrderId = "",
    orderNo = "",
    routeRule = "",
    channelCode = "",
    paymentMethod = "全部",
    terminal = "全部",
    routeResult = "全部",
    sortField = "createdAt",
    sortOrder = "desc"
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      orderNo,
      routeRule,
      channelCode,
      paymentMethod,
      terminal,
      routeResult,
      sortField,
      sortOrder
    });
    return `/api/payment-routes/export?${params.toString()}`;
  },
  getDetail: (routeNo) => {
    const normalizedRouteNo = normalizeBusinessIdentifier(routeNo);
    return request(`/api/payment-routes/${encodeURIComponent(normalizedRouteNo)}`)
      .then((data) => normalizePaymentRouteDetailResult(data, normalizedRouteNo));
  }
};

export const paymentEventApi = {
  getDetail: (eventNo) => {
    const normalizedEventNo = normalizeBusinessIdentifier(eventNo);
    return request(`/api/payment-events/${encodeURIComponent(normalizedEventNo)}`)
      .then((data) => normalizePaymentEventDetailResult(data, normalizedEventNo));
  },
  getOverview: ({
    paymentOrderId = "",
    eventType = "全部",
    publishStatus = "全部",
    downstreamSystem = "全部",
    eventTopic = "",
    sortField = "createdAt",
    sortOrder = "desc"
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      eventType,
      publishStatus,
      downstreamSystem,
      eventTopic,
      sortField,
      sortOrder
    });
    return request(`/api/payment-events/overview?${params.toString()}`)
      .then(normalizePaymentEventOverviewResult);
  },
  getList: ({
    paymentOrderId = "",
    eventType = "全部",
    publishStatus = "全部",
    downstreamSystem = "全部",
    eventTopic = "",
    sortField = "createdAt",
    sortOrder = "desc",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      eventType,
      publishStatus,
      downstreamSystem,
      eventTopic,
      sortField,
      sortOrder,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/payment-events?${params.toString()}`)
      .then(normalizePaymentEventListPage);
  },
  buildExportUrl: ({
    paymentOrderId = "",
    eventType = "全部",
    publishStatus = "全部",
    downstreamSystem = "全部",
    eventTopic = "",
    sortField = "createdAt",
    sortOrder = "desc"
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      eventType,
      publishStatus,
      downstreamSystem,
      eventTopic,
      sortField,
      sortOrder
    });
    return `/api/payment-events/export?${params.toString()}`;
  },
  republish: (eventNo, operator, {
    paymentOrderId = "",
    eventType = "全部",
    publishStatus = "全部",
    downstreamSystem = "全部",
    eventTopic = "",
    sortField = "createdAt",
    sortOrder = "desc",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      eventType,
      publishStatus,
      downstreamSystem,
      eventTopic,
      sortField,
      sortOrder,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    const normalizedEventNo = normalizeBusinessIdentifier(eventNo);
    return postJson(`/api/payment-events/republish?${params.toString()}`, {
      eventNo: normalizedEventNo,
      operator
    })
      .then((data) => normalizePaymentEventRepublishResult(
        data, normalizedEventNo, publishStatus === "全部"
      ));
  }
};

export const paymentDeadLetterTaskApi = {
  getDetail: (taskNo) => {
    const normalizedTaskNo = typeof taskNo === "string" ? taskNo.trim() : taskNo;
    return request(`/api/payment-dead-letter-tasks/${encodeURIComponent(normalizedTaskNo)}`)
      .then((data) => normalizePaymentDeadLetterTaskDetailResult(data, normalizedTaskNo));
  },
  getList: ({
    taskStatus = "",
    targetSystem = "",
    messageId = "",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      taskStatus,
      targetSystem,
      messageId,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/payment-dead-letter-tasks?${params.toString()}`)
      .then(normalizePaymentDeadLetterTaskListPage);
  },
  markReadyToReplay: (taskNo, payload, expectedSnapshot) => {
    const normalizedTaskNo = typeof taskNo === "string" ? taskNo.trim() : taskNo;
    return postJson(
      `/api/payment-dead-letter-tasks/${encodeURIComponent(normalizedTaskNo)}/ready-to-replay`,
      payload
    ).then((data) => normalizePaymentDeadLetterTaskActionResult(
      data, normalizedTaskNo, expectedSnapshot, ["READY_TO_REPLAY"]
    ));
  },
  resolveManually: (taskNo, payload, expectedSnapshot) => {
    const normalizedTaskNo = typeof taskNo === "string" ? taskNo.trim() : taskNo;
    return postJson(
      `/api/payment-dead-letter-tasks/${encodeURIComponent(normalizedTaskNo)}/resolve-manually`,
      payload
    ).then((data) => normalizePaymentDeadLetterTaskActionResult(
      data, normalizedTaskNo, expectedSnapshot, ["MANUAL_RESOLVED"]
    ));
  },
  replay: (taskNo, payload, expectedSnapshot) => {
    const normalizedTaskNo = typeof taskNo === "string" ? taskNo.trim() : taskNo;
    return postJson(
      `/api/payment-dead-letter-tasks/${encodeURIComponent(normalizedTaskNo)}/replay`,
      payload
    ).then((data) => normalizePaymentDeadLetterTaskActionResult(
      data, normalizedTaskNo, expectedSnapshot, ["REPLAYED", "REPLAY_FAILED"]
    ));
  }
};

export const cashierSessionApi = {
  getDetail: (sessionNo) => {
    const normalizedSessionNo = normalizeBusinessIdentifier(sessionNo);
    return request(`/api/cashier-sessions/${encodeURIComponent(normalizedSessionNo)}`)
      .then((data) => normalizeCashierSessionDetailResult(data, normalizedSessionNo));
  },
  getOverview: ({
    sessionNo = "",
    paymentOrderId = "",
    orderNo = "",
    customerName = "",
    terminal = "全部",
    sessionStatus = "全部",
    sortField = "createdAt",
    sortOrder = "desc"
  } = {}) => {
    const params = new URLSearchParams({
      sessionNo,
      paymentOrderId,
      orderNo,
      customerName,
      terminal,
      sessionStatus,
      sortField,
      sortOrder
    });
    return request(`/api/cashier-sessions/overview?${params.toString()}`)
      .then(normalizeCashierSessionOverviewResult);
  },
  getList: ({
    sessionNo = "",
    paymentOrderId = "",
    orderNo = "",
    customerName = "",
    terminal = "全部",
    sessionStatus = "全部",
    sortField = "createdAt",
    sortOrder = "desc",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      sessionNo,
      paymentOrderId,
      orderNo,
      customerName,
      terminal,
      sessionStatus,
      sortField,
      sortOrder,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/cashier-sessions?${params.toString()}`)
      .then(normalizeCashierSessionListPage);
  },
  buildExportUrl: ({
    sessionNo = "",
    paymentOrderId = "",
    orderNo = "",
    customerName = "",
    terminal = "全部",
    sessionStatus = "全部",
    sortField = "createdAt",
    sortOrder = "desc"
  } = {}) => {
    const params = new URLSearchParams({
      sessionNo,
      paymentOrderId,
      orderNo,
      customerName,
      terminal,
      sessionStatus,
      sortField,
      sortOrder
    });
    return `/api/cashier-sessions/export?${params.toString()}`;
  }
};

export const paymentRequestApi = {
  getDetail: (requestNo) => {
    const normalizedRequestNo = normalizeBusinessIdentifier(requestNo);
    return request(`/api/payment-requests/${encodeURIComponent(normalizedRequestNo)}`)
      .then((data) => normalizePaymentRequestDetailResult(data, normalizedRequestNo));
  },
  getOverview: ({
    requestNo = "",
    paymentOrderId = "",
    orderNo = "",
    channelCode = "",
    terminal = "全部",
    clientIp = "",
    requestStatus = "全部",
    sortField = "createdAt",
    sortOrder = "desc"
  } = {}) => {
    const params = new URLSearchParams({
      requestNo,
      paymentOrderId,
      orderNo,
      channelCode,
      terminal,
      clientIp,
      requestStatus,
      sortField,
      sortOrder
    });
    return request(`/api/payment-requests/overview?${params.toString()}`)
      .then(normalizePaymentRequestOverviewResult);
  },
  getList: ({
    requestNo = "",
    paymentOrderId = "",
    orderNo = "",
    channelCode = "",
    terminal = "全部",
    clientIp = "",
    requestStatus = "全部",
    sortField = "createdAt",
    sortOrder = "desc",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      requestNo,
      paymentOrderId,
      orderNo,
      channelCode,
      terminal,
      clientIp,
      requestStatus,
      sortField,
      sortOrder,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/payment-requests?${params.toString()}`)
      .then(normalizePaymentRequestListPage);
  },
  buildExportUrl: ({
    requestNo = "",
    paymentOrderId = "",
    orderNo = "",
    channelCode = "",
    terminal = "全部",
    clientIp = "",
    requestStatus = "全部",
    sortField = "createdAt",
    sortOrder = "desc"
  } = {}) => {
    const params = new URLSearchParams({
      requestNo,
      paymentOrderId,
      orderNo,
      channelCode,
      terminal,
      clientIp,
      requestStatus,
      sortField,
      sortOrder
    });
    return `/api/payment-requests/export?${params.toString()}`;
  }
};

export const paymentLogApi = {
  getDetail: (logNo) => {
    const normalizedLogNo = normalizeBusinessIdentifier(logNo);
    return request(`/api/payment-logs/${encodeURIComponent(normalizedLogNo)}`)
      .then((data) => normalizePaymentLogDetailResult(data, normalizedLogNo));
  },
  getOverview: ({
    paymentOrderId = "",
    orderNo = "",
    processStage = "全部",
    logLevel = "全部",
    source = "",
    keyword = "",
    sortField = "createdAt",
    sortOrder = "desc"
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      orderNo,
      processStage,
      logLevel,
      source,
      keyword,
      sortField,
      sortOrder
    });
    return request(`/api/payment-logs/overview?${params.toString()}`)
      .then(normalizePaymentLogOverviewResult);
  },
  getList: ({
    paymentOrderId = "",
    orderNo = "",
    processStage = "全部",
    logLevel = "全部",
    source = "",
    keyword = "",
    sortField = "createdAt",
    sortOrder = "desc",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      orderNo,
      processStage,
      logLevel,
      source,
      keyword,
      sortField,
      sortOrder,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/payment-logs?${params.toString()}`)
      .then(normalizePaymentLogListPage);
  },
  buildExportUrl: ({
    paymentOrderId = "",
    orderNo = "",
    processStage = "全部",
    logLevel = "全部",
    source = "",
    keyword = "",
    sortField = "createdAt",
    sortOrder = "desc"
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      orderNo,
      processStage,
      logLevel,
      source,
      keyword,
      sortField,
      sortOrder
    });
    return `/api/payment-logs/export?${params.toString()}`;
  }
};

export const paymentRecordApi = {
  getList: ({
    recordType = "ALL",
    customerName = "",
    businessOrderNo = "",
    paymentType = "",
    paymentStatus = "全部",
    paymentChannel = "",
    sortField = "createdAt",
    sortOrder = "desc",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      recordType,
      customerName,
      businessOrderNo,
      paymentType,
      paymentStatus,
      paymentChannel,
      sortField,
      sortOrder,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/payment-records?${params.toString()}`)
      .then(normalizePaymentRecordListPage);
  },
  buildExportUrl: ({
    recordType = "ALL",
    customerName = "",
    businessOrderNo = "",
    paymentType = "",
    paymentStatus = "全部",
    paymentChannel = "",
    sortField = "createdAt",
    sortOrder = "desc"
  } = {}) => {
    const params = new URLSearchParams({
      recordType,
      customerName,
      businessOrderNo,
      paymentType,
      paymentStatus,
      paymentChannel,
      sortField,
      sortOrder
    });
    return `/api/payment-records/export?${params.toString()}`;
  },
  getDetail: (paymentOrderId) => {
    const normalizedPaymentOrderId = normalizeBusinessIdentifier(paymentOrderId);
    return request(`/api/payment-records/${encodeURIComponent(normalizedPaymentOrderId)}`)
      .then((data) => normalizePaymentRecordDetailResult(data, normalizedPaymentOrderId));
  }
};

export const paymentApi = {
  getList: ({
    paymentOrderId = "",
    orderNo = "",
    channelCode = "",
    paymentMethod = "全部",
    status = "全部",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      orderNo,
      channelCode,
      paymentMethod,
      status,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/payments?${params.toString()}`)
      .then(normalizePaymentListPage);
  },
  buildExportUrl: ({
    paymentOrderId = "",
    orderNo = "",
    channelCode = "",
    paymentMethod = "全部",
    status = "全部"
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      orderNo,
      channelCode,
      paymentMethod,
      status
    });
    return `/api/payments/export?${params.toString()}`;
  },
  getDetail: (paymentOrderId) => {
    const normalizedPaymentOrderId = normalizeBusinessIdentifier(paymentOrderId);
    return request(`/api/payments/${encodeURIComponent(normalizedPaymentOrderId)}`)
      .then((data) => normalizePaymentDetailResult(data, normalizedPaymentOrderId));
  },
  prepay: (payload) => {
    const normalizedPayload = {
      ...payload,
      orderNo: normalizeBusinessIdentifier(payload?.orderNo),
      customerName: normalizeBusinessIdentifier(payload?.customerName)
    };
    return postJson("/api/payments/prepay", normalizedPayload)
      .then((data) => normalizePaymentPrepayResult(
        data,
        normalizedPayload.orderNo,
        normalizedPayload.amount,
        normalizedPayload.customerName,
        normalizedPayload.payScene
      ));
  },
  query: (paymentOrderId, operator, snapshot = {}) => {
    const normalizedPaymentOrderId = normalizeBusinessIdentifier(paymentOrderId);
    return postJson("/api/payments/admin/query", { paymentOrderId: normalizedPaymentOrderId, operator })
      .then((data) => normalizePaymentActionResult(
        data, normalizedPaymentOrderId, snapshot.orderNo, snapshot.amount,
        snapshot.prepayOrderNo, snapshot
      ));
  },
  close: (paymentOrderId, operator, snapshot = {}) => {
    const normalizedPaymentOrderId = normalizeBusinessIdentifier(paymentOrderId);
    return postJson("/api/payments/admin/close", { paymentOrderId: normalizedPaymentOrderId, operator })
      .then((data) => normalizePaymentActionResult(
        data, normalizedPaymentOrderId, snapshot.orderNo, snapshot.amount,
        snapshot.prepayOrderNo, snapshot
      ));
  }
};

export const refundApi = {
  getList: ({
    refundOrderId = "",
    paymentOrderId = "",
    refundStatus = "全部",
    refundMethod = "全部",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      refundOrderId,
      paymentOrderId,
      refundStatus,
      refundMethod,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/refunds?${params.toString()}`)
      .then((data) => normalizeRefundListPage(data, refundStatus));
  },
  buildExportUrl: ({
    refundOrderId = "",
    paymentOrderId = "",
    refundStatus = "全部",
    refundMethod = "全部"
  } = {}) => {
    const params = new URLSearchParams({
      refundOrderId,
      paymentOrderId,
      refundStatus,
      refundMethod
    });
    return `/api/refunds/export?${params.toString()}`;
  },
  getDetail: (refundOrderId) => {
    const normalizedRefundOrderId = normalizeBusinessIdentifier(refundOrderId);
    return request(`/api/refunds/${encodeURIComponent(normalizedRefundOrderId)}`)
      .then((data) => normalizeRefundDetailResult(data, normalizedRefundOrderId));
  },
  apply: (payload) => {
    const normalizedPayload = {
      ...payload,
      paymentOrderId: normalizeBusinessIdentifier(payload?.paymentOrderId)
    };
    return postJson("/api/refunds/apply", normalizedPayload)
      .then((data) => normalizeRefundActionResult(
        data, "", normalizedPayload.paymentOrderId, normalizedPayload.refundAmount
      ));
  },
  approve: (refundOrderId, operator, remark = "", expectedSnapshot = null) => {
    const normalizedRefundOrderId = normalizeBusinessIdentifier(refundOrderId);
    return postJson("/api/refunds/approve", {
      refundOrderId: normalizedRefundOrderId, operator, remark
    }).then((data) => normalizeRefundActionResult(
      data, normalizedRefundOrderId, "", null, expectedSnapshot
    ));
  },
  query: (refundOrderId, operator, expectedSnapshot = null) => {
    const normalizedRefundOrderId = normalizeBusinessIdentifier(refundOrderId);
    return postJson("/api/refunds/query", {
      refundOrderId: normalizedRefundOrderId, operator
    }).then((data) => normalizeRefundActionResult(
      data, normalizedRefundOrderId, "", null, expectedSnapshot
    ));
  },
  retry: (refundOrderId, operator, remark = "", expectedSnapshot = null) => {
    const normalizedRefundOrderId = normalizeBusinessIdentifier(refundOrderId);
    return postJson("/api/refunds/retry", {
      refundOrderId: normalizedRefundOrderId, operator, remark
    }).then((data) => normalizeRefundActionResult(
      data, normalizedRefundOrderId, "", null, expectedSnapshot
    ));
  }
};

function normalizeRefundDetail(detail) {
  if (!detail || typeof detail !== "object") {
    return detail;
  }
  const attempts = Array.isArray(detail.attempts) ? detail.attempts.filter((item) => item != null) : [];
  const operationLogs = Array.isArray(detail.operationLogs)
    ? detail.operationLogs.filter((item) => item != null)
    : [];
  if (!isConsistentRefundAttempts(
    attempts, detail.appliedAt, detail.successAt, detail.terminalAt
  )) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-REFUND-DETAIL" },
      "退款详情返回了无法确认的退款尝试时间事实"
    );
  }
  if (!isConsistentRefundOperationLogs(
    operationLogs, detail.appliedAt, detail.successAt, detail.terminalAt
  )) {
    throw createBusinessError(
      { code: "PAYMENT-CLIENT-INVALID-REFUND-DETAIL" },
      "退款详情返回了无法确认的退款操作日志事实"
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
    && isValidDateTime(log.createdAt)
    && isValidDateTime(appliedAt)
    && Date.parse(appliedAt.trim().replace(" ", "T"))
      <= Date.parse(log.createdAt.trim().replace(" ", "T")));
}

function isConsistentRefundOperationLogs(operationLogs, appliedAt, successAt, terminalAt) {
  let previousTimestamp = null;
  const terminalTimestamp = isValidDateTime(terminalAt)
    ? Date.parse(terminalAt.trim().replace(" ", "T"))
    : (isValidDateTime(successAt)
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
      || !isValidDateTime(attempt.createdAt)) {
    return false;
  }
  const submittedAt = attempt.submittedAt;
  const completedAt = attempt.completedAt;
  const hasSubmittedAt = submittedAt !== "-" && submittedAt != null;
  const hasCompletedAt = completedAt !== "-" && completedAt != null;
  if ((hasSubmittedAt && !isValidDateTime(submittedAt))
      || (hasCompletedAt && !isValidDateTime(completedAt))) {
    return false;
  }
  const createdTimestamp = Date.parse(attempt.createdAt.trim().replace(" ", "T"));
  const appliedTimestamp = isValidDateTime(appliedAt)
    ? Date.parse(appliedAt.trim().replace(" ", "T"))
    : null;
  const submittedTimestamp = hasSubmittedAt
    ? Date.parse(submittedAt.trim().replace(" ", "T"))
    : createdTimestamp;
  const terminalStatus = ["SUCCESS", "FAIL"].includes(attempt.attemptStatus.trim().toUpperCase());
  const terminalTimestamp = isValidDateTime(terminalAt)
    ? Date.parse(terminalAt.trim().replace(" ", "T"))
    : (isValidDateTime(successAt)
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

export const settlementApi = {
  getWorkerOverview: ({
    settlementOrderId = "",
    workerKeyword = "",
    settlementStatus = "全部",
    payoutStatus = "全部"
  } = {}) => {
    const params = new URLSearchParams({
      settlementOrderId,
      workerKeyword,
      settlementStatus,
      payoutStatus
    });
    return request(`/api/settlements/workers/overview?${params.toString()}`)
      .then(normalizeWorkerSettlementOverviewResult);
  },
  getWorkerList: ({
    settlementOrderId = "",
    workerKeyword = "",
    settlementStatus = "全部",
    payoutStatus = "全部",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      settlementOrderId,
      workerKeyword,
      settlementStatus,
      payoutStatus,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/settlements/workers?${params.toString()}`)
      .then(normalizeWorkerSettlementListPage);
  },
  buildWorkerExportUrl: ({
    settlementOrderId = "",
    workerKeyword = "",
    settlementStatus = "全部",
    payoutStatus = "全部"
  } = {}) => {
    const params = new URLSearchParams({
      settlementOrderId,
      workerKeyword,
      settlementStatus,
      payoutStatus
    });
    return `/api/settlements/workers/export?${params.toString()}`;
  }
};

export const paymentConfigApi = {
  getOverview: () => request("/api/payment-config").then(normalizePaymentConfigOverviewResult),
  buildExportUrl: (section = "ALL") => `/api/payment-config/export?section=${encodeURIComponent(section)}`,
  submitChannelStatusReview: (payload) => {
    const normalizedPayload = payload && typeof payload === "object"
      ? { ...payload, channelCode: normalizeBusinessIdentifier(payload.channelCode) }
      : payload;
    return postCollection("/api/payment-config/channels/toggle", normalizedPayload)
      .then((data) => normalizePaymentConfigChannelReviewSubmitResult(
        data, normalizedPayload?.channelCode
      ));
  },
  submitChannelReview: (payload) => {
    const normalizedPayload = payload && typeof payload === "object"
      ? {
        ...payload,
        candidate: payload.candidate && typeof payload.candidate === "object"
          ? {
            ...payload.candidate,
            channelCode: normalizeBusinessIdentifier(payload.candidate.channelCode)
          }
          : payload.candidate
      }
      : payload;
    return postCollection("/api/payment-config/channels/reviews", normalizedPayload)
      .then((data) => normalizePaymentConfigChannelReviewSubmitResult(
        data, normalizedPayload?.candidate?.channelCode
      ));
  },
  getChannelReviews: () => requestCollection("/api/payment-config/channels/reviews")
    .then(normalizePaymentConfigReviewListResult),
  approveChannelReview: (requestNo, payload) => postCollection(
    `/api/payment-config/channels/reviews/${encodeURIComponent(normalizeBusinessIdentifier(requestNo))}/approve`,
    payload
  ).then((data) => normalizePaymentConfigReviewActionResult(data, normalizeBusinessIdentifier(requestNo))),
  rejectChannelReview: (requestNo, payload) => postCollection(
    `/api/payment-config/channels/reviews/${encodeURIComponent(normalizeBusinessIdentifier(requestNo))}/reject`,
    payload
  ).then((data) => normalizePaymentConfigReviewActionResult(data, normalizeBusinessIdentifier(requestNo))),
  getChannelVersions: (channelCode) => requestCollection(
    `/api/payment-config/channels/${encodeURIComponent(normalizeBusinessIdentifier(channelCode))}/versions`
  ).then((data) => normalizePaymentConfigVersionsResult(data, normalizeBusinessIdentifier(channelCode), "channelCode")),
  rollbackChannel: (channelCode, payload) => postCollection(
    `/api/payment-config/channels/${encodeURIComponent(normalizeBusinessIdentifier(channelCode))}/rollback`,
    payload
  ).then((data) => normalizePaymentConfigChannelRollbackResult(data, normalizeBusinessIdentifier(channelCode))),
  getGatewayVersions: (gatewayCode) => requestCollection(
    `/api/payment-config/gateways/${encodeURIComponent(normalizeBusinessIdentifier(gatewayCode))}/versions`
  ).then((data) => normalizePaymentConfigVersionsResult(data, normalizeBusinessIdentifier(gatewayCode), "gatewayCode")),
  rollbackGateway: (gatewayCode, payload) => postCollection(
    `/api/payment-config/gateways/${encodeURIComponent(normalizeBusinessIdentifier(gatewayCode))}/rollback`,
    payload
  ).then((data) => normalizePaymentConfigGatewayRollbackResult(data, normalizeBusinessIdentifier(gatewayCode))),
  submitRouteRuleStatusReview: (payload) => {
    const normalizedPayload = payload && typeof payload === "object"
      ? { ...payload, ruleCode: normalizeBusinessIdentifier(payload.ruleCode) }
      : payload;
    return postCollection("/api/payment-config/route-rules/toggle", normalizedPayload)
      .then((data) => normalizePaymentConfigRouteRuleReviewSubmitResult(data, normalizedPayload?.ruleCode));
  },
  validateRouteRule: (payload) => postJson("/api/payment-config/route-rules/validate", payload)
    .then(normalizeRouteRuleValidationResult),
  getRouteRuleConflicts: () => requestCollection("/api/payment-config/route-rules/conflicts")
    .then(normalizePaymentRouteRuleConflictsResult),
  getRouteRuleReviews: () => requestCollection("/api/payment-config/route-rules/reviews")
    .then(normalizePaymentConfigReviewListResult),
  submitRouteRuleReview: (payload) => {
    const normalizedPayload = payload && typeof payload === "object"
      ? {
        ...payload,
        candidate: payload.candidate && typeof payload.candidate === "object"
          ? {
            ...payload.candidate,
            ruleCode: normalizeBusinessIdentifier(payload.candidate.ruleCode)
          }
          : payload.candidate
      }
      : payload;
    return postCollection("/api/payment-config/route-rules/reviews", normalizedPayload)
      .then((data) => normalizePaymentConfigRouteRuleReviewSubmitResult(
        data, normalizedPayload?.candidate?.ruleCode
      ));
  },
  approveRouteRuleReview: (requestNo, payload) => postCollection(
    `/api/payment-config/route-rules/reviews/${encodeURIComponent(normalizeBusinessIdentifier(requestNo))}/approve`,
    payload
  ).then((data) => normalizePaymentConfigReviewActionResult(data, normalizeBusinessIdentifier(requestNo))),
  rejectRouteRuleReview: (requestNo, payload) => postCollection(
    `/api/payment-config/route-rules/reviews/${encodeURIComponent(normalizeBusinessIdentifier(requestNo))}/reject`,
    payload
  ).then((data) => normalizePaymentConfigReviewActionResult(data, normalizeBusinessIdentifier(requestNo))),
  getRouteRuleVersions: (ruleCode) => requestCollection(
    `/api/payment-config/route-rules/${encodeURIComponent(normalizeBusinessIdentifier(ruleCode))}/versions`
  ).then((data) => normalizePaymentConfigVersionsResult(data, normalizeBusinessIdentifier(ruleCode), "ruleCode")),
  submitRouteRuleRollbackReview: (ruleCode, payload) => postCollection(
    `/api/payment-config/route-rules/${encodeURIComponent(normalizeBusinessIdentifier(ruleCode))}/rollback`,
    payload
  ).then((data) => normalizePaymentConfigRouteRuleRollbackResult(data, normalizeBusinessIdentifier(ruleCode))),
  getProtocolReviews: () => requestCollection("/api/payment-config/protocols/reviews")
    .then(normalizePaymentConfigReviewListResult),
  submitProtocolReview: (payload) => {
    const normalizedPayload = payload && typeof payload === "object"
      ? {
        ...payload,
        candidate: payload.candidate && typeof payload.candidate === "object"
          ? {
            ...payload.candidate,
            protocolCode: normalizeBusinessIdentifier(payload.candidate.protocolCode)
          }
          : payload.candidate
      }
      : payload;
    return postCollection("/api/payment-config/protocols/reviews", normalizedPayload)
      .then((data) => normalizePaymentConfigProtocolReviewApplicationResult(
        data, normalizedPayload?.candidate?.protocolCode
      ));
  },
  approveProtocolReview: (requestNo, payload) => postCollection(
    `/api/payment-config/protocols/reviews/${encodeURIComponent(normalizeBusinessIdentifier(requestNo))}/approve`,
    payload
  ).then((data) => normalizePaymentConfigReviewActionResult(data, normalizeBusinessIdentifier(requestNo))),
  rejectProtocolReview: (requestNo, payload) => postCollection(
    `/api/payment-config/protocols/reviews/${encodeURIComponent(normalizeBusinessIdentifier(requestNo))}/reject`,
    payload
  ).then((data) => normalizePaymentConfigReviewActionResult(data, normalizeBusinessIdentifier(requestNo))),
  toggleProtocol: (payload) => {
    const normalizedPayload = payload && typeof payload === "object"
      ? { ...payload, protocolCode: normalizeBusinessIdentifier(payload.protocolCode) }
      : payload;
    return postCollection("/api/payment-config/protocols/toggle", normalizedPayload)
      .then((data) => normalizePaymentConfigProtocolReviewSubmitResult(data, normalizedPayload?.protocolCode));
  },
  getProtocolVersions: (protocolCode) => requestCollection(
    `/api/payment-config/protocols/${encodeURIComponent(normalizeBusinessIdentifier(protocolCode))}/versions`
  ).then((data) => normalizePaymentConfigVersionsResult(data, normalizeBusinessIdentifier(protocolCode), "protocolCode")),
  rollbackProtocol: (protocolCode, payload) => postCollection(
    `/api/payment-config/protocols/${encodeURIComponent(normalizeBusinessIdentifier(protocolCode))}/rollback`,
    payload
  ).then((data) => normalizePaymentConfigProtocolRollbackResult(data, normalizeBusinessIdentifier(protocolCode))),
  createIssueDutyRoster: (payload) => {
    const normalizedPayload = payload && typeof payload === "object"
      ? { ...payload, rosterCode: normalizeBusinessIdentifier(payload.rosterCode) }
      : payload;
    return postCollection("/api/payment-config/issue-duty-rosters", normalizedPayload)
      .then((data) => normalizePaymentConfigIssueDutyRosterReviewApplicationResult(
        data, normalizedPayload?.rosterCode
      ));
  },
  updateIssueDutyRoster: (rosterCode, payload) => requestCollection(
    `/api/payment-config/issue-duty-rosters/${encodeURIComponent(normalizeBusinessIdentifier(rosterCode))}`,
    buildJsonRequestOptions("PUT", payload)
  ).then((data) => normalizePaymentConfigIssueDutyRosterReviewUpdateResult(data, normalizeBusinessIdentifier(rosterCode))),
  getIssueDutyRosterReviews: () => requestCollection("/api/payment-config/issue-duty-rosters/reviews")
    .then(normalizePaymentConfigReviewListResult),
  approveIssueDutyRosterReview: (requestNo, payload) => postCollection(
    `/api/payment-config/issue-duty-rosters/reviews/${encodeURIComponent(normalizeBusinessIdentifier(requestNo))}/approve`,
    payload
  ).then((data) => normalizePaymentConfigReviewActionResult(data, normalizeBusinessIdentifier(requestNo))),
  rejectIssueDutyRosterReview: (requestNo, payload) => postCollection(
    `/api/payment-config/issue-duty-rosters/reviews/${encodeURIComponent(normalizeBusinessIdentifier(requestNo))}/reject`,
    payload
  ).then((data) => normalizePaymentConfigReviewActionResult(data, normalizeBusinessIdentifier(requestNo))),
  createAlertProvider: (payload) => {
    const normalizedPayload = payload && typeof payload === "object"
      ? { ...payload, providerCode: normalizeBusinessIdentifier(payload.providerCode) }
      : payload;
    return postCollection("/api/payment-config/alert-providers", normalizedPayload)
      .then((data) => normalizePaymentConfigAlertProviderReviewApplicationResult(
        data, normalizedPayload?.providerCode
      ));
  },
  updateAlertProvider: (providerCode, payload) => requestCollection(
    `/api/payment-config/alert-providers/${encodeURIComponent(normalizeBusinessIdentifier(providerCode))}`,
    buildJsonRequestOptions("PUT", payload)
  ).then((data) => normalizePaymentConfigAlertProviderReviewUpdateResult(data, normalizeBusinessIdentifier(providerCode))),
  getAlertProviderReviews: () => requestCollection("/api/payment-config/alert-providers/reviews")
    .then(normalizePaymentConfigReviewListResult),
  approveAlertProviderReview: (requestNo, payload) => postCollection(
    `/api/payment-config/alert-providers/reviews/${encodeURIComponent(normalizeBusinessIdentifier(requestNo))}/approve`,
    payload
  ).then((data) => normalizePaymentConfigReviewActionResult(data, normalizeBusinessIdentifier(requestNo))),
  rejectAlertProviderReview: (requestNo, payload) => postCollection(
    `/api/payment-config/alert-providers/reviews/${encodeURIComponent(normalizeBusinessIdentifier(requestNo))}/reject`,
    payload
  ).then((data) => normalizePaymentConfigReviewActionResult(data, normalizeBusinessIdentifier(requestNo))),
  submitReturnCodeStatusReview: (payload) => {
    const normalizedPayload = payload && typeof payload === "object"
      ? {
        ...payload,
        channelCode: normalizeBusinessIdentifier(payload.channelCode),
        channelReturnCode: normalizeBusinessIdentifier(payload.channelReturnCode)
      }
      : payload;
    return postCollection("/api/payment-config/return-codes/toggle", normalizedPayload)
      .then((data) => normalizePaymentConfigReturnCodeReviewSubmitResult(
        data,
        normalizedPayload?.channelCode,
        normalizedPayload?.channelReturnCode
      ));
  },
  getReturnCodeReviews: () => requestCollection("/api/payment-config/return-codes/reviews")
    .then(normalizePaymentConfigReviewListResult),
  approveReturnCodeReview: (requestNo, payload) => postCollection(
    `/api/payment-config/return-codes/reviews/${encodeURIComponent(normalizeBusinessIdentifier(requestNo))}/approve`,
    payload
  ).then((data) => normalizePaymentConfigReviewActionResult(data, normalizeBusinessIdentifier(requestNo))),
  rejectReturnCodeReview: (requestNo, payload) => postCollection(
    `/api/payment-config/return-codes/reviews/${encodeURIComponent(normalizeBusinessIdentifier(requestNo))}/reject`,
    payload
  ).then((data) => normalizePaymentConfigReviewActionResult(data, normalizeBusinessIdentifier(requestNo))),
  previewReturnCodeImport: (payload) => postJson("/api/payment-config/return-codes/import/preview", payload)
    .then(normalizeReturnCodeImportPreviewResult),
  getReturnCodeImportReviews: () => requestCollection("/api/payment-config/return-codes/import/reviews")
    .then(normalizePaymentConfigReviewListResult),
  submitReturnCodeImportReview: (payload) => {
    const normalizedPayload = payload && typeof payload === "object"
      ? { ...payload, baselineFingerprint: normalizeBusinessIdentifier(payload.baselineFingerprint) }
      : payload;
    return postCollection("/api/payment-config/return-codes/import/reviews", normalizedPayload)
      .then((data) => normalizePaymentConfigReturnCodeImportReviewSubmitResult(
        data, normalizedPayload?.baselineFingerprint
      ));
  },
  approveReturnCodeImportReview: (requestNo, payload) => postCollection(
    `/api/payment-config/return-codes/import/reviews/${encodeURIComponent(normalizeBusinessIdentifier(requestNo))}/approve`,
    payload
  ).then((data) => normalizePaymentConfigReviewActionResult(data, normalizeBusinessIdentifier(requestNo))),
  rejectReturnCodeImportReview: (requestNo, payload) => postCollection(
    `/api/payment-config/return-codes/import/reviews/${encodeURIComponent(normalizeBusinessIdentifier(requestNo))}/reject`,
    payload
  ).then((data) => normalizePaymentConfigReviewActionResult(data, normalizeBusinessIdentifier(requestNo))),
  submitGatewayStatusReview: (payload) => {
    const normalizedPayload = payload && typeof payload === "object"
      ? { ...payload, gatewayCode: normalizeBusinessIdentifier(payload.gatewayCode) }
      : payload;
    return postCollection("/api/payment-config/gateways/toggle", normalizedPayload)
      .then((data) => normalizePaymentConfigGatewayReviewSubmitResult(
        data, normalizedPayload?.gatewayCode
      ));
  },
  getGatewayReviews: () => requestCollection("/api/payment-config/gateways/reviews")
    .then(normalizePaymentConfigReviewListResult),
  submitGatewayReview: (payload) => {
    const normalizedPayload = payload && typeof payload === "object"
      ? {
        ...payload,
        candidate: payload.candidate && typeof payload.candidate === "object"
          ? {
            ...payload.candidate,
            gatewayCode: normalizeBusinessIdentifier(payload.candidate.gatewayCode)
          }
          : payload.candidate
      }
      : payload;
    return postCollection("/api/payment-config/gateways/reviews", normalizedPayload)
      .then((data) => normalizePaymentConfigGatewayReviewApplicationResult(
        data, normalizedPayload?.candidate?.gatewayCode
      ));
  },
  approveGatewayReview: (requestNo, payload) => postCollection(
    `/api/payment-config/gateways/reviews/${encodeURIComponent(normalizeBusinessIdentifier(requestNo))}/approve`,
    payload
  ).then((data) => normalizePaymentConfigReviewActionResult(data, normalizeBusinessIdentifier(requestNo))),
  rejectGatewayReview: (requestNo, payload) => postCollection(
    `/api/payment-config/gateways/reviews/${encodeURIComponent(normalizeBusinessIdentifier(requestNo))}/reject`,
    payload
  ).then((data) => normalizePaymentConfigReviewActionResult(data, normalizeBusinessIdentifier(requestNo))),
  submitControlPolicyStatusReview: (payload) => {
    const normalizedPayload = payload && typeof payload === "object"
      ? { ...payload, sourceAppId: normalizeBusinessIdentifier(payload.sourceAppId) }
      : payload;
    return postCollection("/api/payment-config/control-policies/toggle", normalizedPayload)
      .then((data) => normalizePaymentConfigControlPolicyReviewSubmitResult(
        data, normalizedPayload?.sourceAppId
      ));
  },
  getControlPolicyReviews: () => requestCollection("/api/payment-config/control-policies/reviews")
    .then(normalizePaymentConfigReviewListResult),
  submitControlPolicyReview: (payload) => {
    const normalizedPayload = payload && typeof payload === "object"
      ? {
        ...payload,
        candidate: payload.candidate && typeof payload.candidate === "object"
          ? {
            ...payload.candidate,
            sourceAppId: normalizeBusinessIdentifier(payload.candidate.sourceAppId)
          }
          : payload.candidate
      }
      : payload;
    return postCollection("/api/payment-config/control-policies/reviews", normalizedPayload)
      .then((data) => normalizePaymentConfigControlPolicyReviewApplicationResult(
        data, normalizedPayload?.candidate?.sourceAppId
      ));
  },
  approveControlPolicyReview: (requestNo, payload) => postCollection(
    `/api/payment-config/control-policies/reviews/${encodeURIComponent(normalizeBusinessIdentifier(requestNo))}/approve`,
    payload
  ).then((data) => normalizePaymentConfigReviewActionResult(data, normalizeBusinessIdentifier(requestNo))),
  rejectControlPolicyReview: (requestNo, payload) => postCollection(
    `/api/payment-config/control-policies/reviews/${encodeURIComponent(normalizeBusinessIdentifier(requestNo))}/reject`,
    payload
  ).then((data) => normalizePaymentConfigReviewActionResult(data, normalizeBusinessIdentifier(requestNo))),
  getControlPolicyVersions: (sourceAppId) => requestCollection(
    `/api/payment-config/control-policies/${encodeURIComponent(normalizeBusinessIdentifier(sourceAppId))}/versions`
  ).then((data) => normalizePaymentConfigVersionsResult(data, normalizeBusinessIdentifier(sourceAppId), "sourceAppId")),
  rollbackControlPolicy: (sourceAppId, payload) => postCollection(
    `/api/payment-config/control-policies/${encodeURIComponent(normalizeBusinessIdentifier(sourceAppId))}/rollback`,
    payload
  ).then((data) => normalizePaymentConfigControlPolicyRollbackResult(data, normalizeBusinessIdentifier(sourceAppId))),
  toggleAlertProvider: (payload) => {
    const normalizedPayload = payload && typeof payload === "object"
      ? { ...payload, providerCode: normalizeBusinessIdentifier(payload.providerCode) }
      : payload;
    return postCollection("/api/payment-config/alert-providers/toggle", normalizedPayload)
      .then((data) => normalizePaymentConfigAlertProviderReviewSubmitResult(
        data, normalizedPayload?.providerCode
      ));
  },
  toggleIssueDutyRoster: (payload) => {
    const normalizedPayload = payload && typeof payload === "object"
      ? { ...payload, rosterCode: normalizeBusinessIdentifier(payload.rosterCode) }
      : payload;
    return postCollection("/api/payment-config/issue-duty-rosters/toggle", normalizedPayload)
      .then((data) => normalizePaymentConfigIssueDutyRosterReviewSubmitResult(
        data, normalizedPayload?.rosterCode
      ));
  },
  runControlPolicySelfCheck: (configCode) => {
    const normalizedConfigCode = normalizeBusinessIdentifier(configCode);
    return postJson("/api/payment-config/control-policies/self-check", {
      configCode: normalizedConfigCode
    }).then((data) => normalizeControlPolicySelfCheckResult(data, normalizedConfigCode));
  }
};
