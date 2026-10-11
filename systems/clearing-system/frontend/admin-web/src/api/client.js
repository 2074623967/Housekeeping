function createBusinessError(payload, fallbackMessage) {
  const message = payload?.message || fallbackMessage || "Request failed";
  const displayMessage = `${message} [${payload?.code || "CLEARING-CLIENT"}${payload?.requestId ? ` / ${payload.requestId}` : ""}]`;
  const error = new Error(displayMessage);
  error.code = payload?.code || "CLEARING-CLIENT";
  error.requestId = payload?.requestId || "";
  return error;
}

const CLIENT_TIMEOUT_MS = 10000;

async function parseResponsePayload(response) {
  try {
    return await response.json();
  } catch (error) {
    const parseError = new Error("清分服务返回了无法解析的响应");
    parseError.code = "CLEARING-CLIENT-INVALID-RESPONSE";
    parseError.status = response.status;
    parseError.cause = error;
    throw parseError;
  }
}

async function request(url, options = {}) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), CLIENT_TIMEOUT_MS);
  let response;
  try {
    response = await fetch(url, {
      headers: { "Content-Type": "application/json" },
      ...options,
      signal: controller.signal
    });
  } catch (error) {
    const code = error?.name === "AbortError"
      ? "CLEARING-CLIENT-TIMEOUT"
      : "CLEARING-CLIENT-NETWORK";
    throw createBusinessError({ code }, error?.name === "AbortError" ? "清分服务请求超时" : "无法连接清分服务");
  } finally {
    clearTimeout(timeoutId);
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
}

function postJson(url, payload) {
  return request(url, {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function normalizePagePayload(data) {
  if (Array.isArray(data)) {
    return { items: data, total: data.length, pageNo: 1, pageSize: 20 };
  }
  if (!data || typeof data !== "object") {
    return { items: [], total: 0, pageNo: 1, pageSize: 20 };
  }
  if (!Object.prototype.hasOwnProperty.call(data, "items")) {
    return data;
  }
  const items = Array.isArray(data.items) ? data.items : [];
  const total = Number(data.total);
  const pageNo = Number(data.pageNo);
  const pageSize = Number(data.pageSize);
  return {
    ...data,
    items,
    total: Number.isInteger(total) && total >= 0 ? total : items.length,
    pageNo: Number.isInteger(pageNo) && pageNo > 0 ? pageNo : 1,
    pageSize: Number.isInteger(pageSize) && pageSize > 0 ? pageSize : 20
  };
}

function requestPage(url, options = {}) {
  return request(url, options).then(normalizePagePayload);
}

function normalizeClearingBatchPage(page) {
  const normalized = normalizePagePayload(page);
  if (normalized.items.some((item) => !isValidClearingBatch(item))) {
    throw invalidClearingResult("清分批次列表返回了无法确认的计数金额事实");
  }
  return normalized;
}

function isValidClearingBatch(item) {
  if (!item || typeof item !== "object"
      || typeof item.batchNo !== "string" || !item.batchNo.trim()
      || typeof item.batchDate !== "string" || !item.batchDate.trim()
      || typeof item.batchStatus !== "string" || !item.batchStatus.trim()
      || typeof item.batchStatusType !== "string" || !item.batchStatusType.trim()
      || !Number.isInteger(item.totalOrderCount) || item.totalOrderCount < 0
      || !Number.isInteger(item.successOrderCount) || item.successOrderCount < 0
      || !Number.isInteger(item.failedOrderCount) || item.failedOrderCount < 0
      || item.successOrderCount + item.failedOrderCount > item.totalOrderCount
      || !isValidClearingAmount(item.totalAmount, true)
      || typeof item.versionNo !== "string" || !item.versionNo.trim()
      || typeof item.createdBy !== "string" || !item.createdBy.trim()
      || typeof item.createdAt !== "string" || !item.createdAt.trim()) {
    return false;
  }
  const terminal = item.batchStatus.includes("完成") || item.batchStatus.includes("失败");
  return !terminal || (typeof item.finishedAt === "string" && item.finishedAt.trim());
}

function normalizeOrderDetail(data) {
  if (!data || typeof data !== "object") {
    return data;
  }
  return {
    ...data,
    shareItems: Array.isArray(data.shareItems) ? data.shareItems : [],
    feeRules: Array.isArray(data.feeRules) ? data.feeRules : []
  };
}

function normalizeClearingOrderPage(page) {
  const normalized = normalizePagePayload(page);
  if (normalized.items.some((item) => !isValidClearingOrder(item))) {
    throw invalidClearingResult("清分单列表返回了无法确认的归属金额事实");
  }
  return normalized;
}

function isValidClearingOrder(item) {
  const amounts = [
    item?.orderAmount, item?.merchantAmount, item?.workerAmount,
    item?.platformAmount, item?.channelFeeAmount, item?.subsidyAmount
  ];
  if (!item || typeof item !== "object"
      || typeof item.clearingNo !== "string" || !item.clearingNo.trim()
      || typeof item.businessChainId !== "string" || !item.businessChainId.trim()
      || typeof item.paymentOrderId !== "string" || !item.paymentOrderId.trim()
      || item.businessChainId.trim() !== `CHAIN-${item.paymentOrderId.trim()}`
      || (item.payScene != null && typeof item.payScene !== "string")
      || typeof item.orderNo !== "string" || !item.orderNo.trim()
      || typeof item.batchNo !== "string" || !item.batchNo.trim()
      || typeof item.ruleNo !== "string" || !item.ruleNo.trim()
      || amounts.some((value, index) => !isValidClearingAmount(value, index !== 0))
      || !isConsistentClearingStatus(item.clearingStatus, item.clearingStatusType)
      || typeof item.createdAt !== "string" || !item.createdAt.trim()) {
    return false;
  }
  const [order, merchant, worker, platform, channelFee, subsidy] = amounts.map(toClearingMinorUnits);
  return merchant + worker + platform + channelFee === order + subsidy;
}

function isValidClearingAmount(value, allowZero) {
  if (typeof value !== "string" && typeof value !== "number") {
    return false;
  }
  const normalized = String(value).trim().replace(/^¥\s*/, "");
  if (!/^(?:0|[1-9]\d{0,15})(?:\.\d{1,2})?$/.test(normalized)) {
    return false;
  }
  return allowZero || Number(normalized) > 0;
}

function toClearingMinorUnits(value) {
  const [integerPart, fractionPart = ""] = String(value).trim()
    .replace(/^¥\s*/, "")
    .split(".");
  return BigInt(integerPart) * 100n + BigInt(fractionPart.padEnd(2, "0"));
}

function isConsistentClearingStatus(status, statusType) {
  if (typeof status !== "string" || !status.trim()
      || typeof statusType !== "string" || !statusType.trim()) {
    return false;
  }
  const normalizedStatus = status.trim();
  const expectedType = normalizedStatus.includes("成功") || normalizedStatus.includes("完成")
    ? "success"
    : normalizedStatus.includes("失败") ? "danger"
      : normalizedStatus.includes("待") || normalizedStatus.includes("进行") ? "warn" : "info";
  return statusType.trim().toLowerCase() === expectedType;
}

function invalidClearingResult(message) {
  return createBusinessError(
    { code: "CLEARING-CLIENT-INVALID-BUSINESS-RESULT" },
    message
  );
}

export function normalizeClearingBatchResult(data, expectedBatchNo = "") {
  if (!data || typeof data !== "object" || typeof data.batchNo !== "string"
      || !data.batchNo.trim() || (expectedBatchNo && data.batchNo !== expectedBatchNo)) {
    throw invalidClearingResult("清分批次响应未确认当前批次");
  }
  return data;
}

export function normalizeClearingRuleResult(data, expectedRuleNo = "") {
  if (!data || typeof data !== "object" || typeof data.ruleNo !== "string"
      || !data.ruleNo.trim() || (expectedRuleNo && data.ruleNo !== expectedRuleNo)) {
    throw invalidClearingResult("清分规则响应未确认当前规则");
  }
  return data;
}

export function normalizeFeeRuleResult(data, expectedFeeRuleNo = "") {
  if (!data || typeof data !== "object" || typeof data.feeRuleNo !== "string"
      || !data.feeRuleNo.trim() || (expectedFeeRuleNo && data.feeRuleNo !== expectedFeeRuleNo)) {
    throw invalidClearingResult("费用规则响应未确认当前规则");
  }
  return data;
}

export function normalizeClearingOrderDetailResult(data, expectedClearingNo) {
  if (!data || typeof data !== "object" || !data.order || typeof data.order !== "object"
      || !isValidClearingOrder(data.order)
      || data.order.clearingNo !== expectedClearingNo) {
    throw invalidClearingResult("清分单详情响应未确认当前清分单");
  }
  return normalizeOrderDetail(data);
}

export function normalizeClearingEventResult(data, expectedPaymentOrderId = "") {
  if (!data || typeof data !== "object" || typeof data.eventNo !== "string"
      || !data.eventNo.trim() || typeof data.bizNo !== "string" || !data.bizNo.trim()
      || (expectedPaymentOrderId && data.bizNo !== expectedPaymentOrderId)) {
    throw invalidClearingResult("清分事件响应未确认当前业务事件");
  }
  return data;
}

export function normalizeClearingEventListPage(data) {
  const normalized = normalizePagePayload(data);
  if (normalized.items.some((item) => !isValidClearingEvent(item))) {
    throw invalidClearingResult("清分事件列表返回了无法确认的链路状态事实");
  }
  return normalized;
}

export function normalizeClearingReplayResult(data) {
  if (!data || typeof data !== "object"
      || !Number.isInteger(data.attemptedCount) || data.attemptedCount < 0
      || typeof data.message !== "string" || !data.message.trim()) {
    throw invalidClearingResult("清分事件重放响应未确认执行结果");
  }
  return data;
}

function isValidClearingEvent(item) {
  return Boolean(item && typeof item === "object"
    && typeof item.businessChainId === "string" && item.businessChainId.trim()
    && typeof item.eventNo === "string" && item.eventNo.trim()
    && typeof item.eventType === "string" && item.eventType.trim()
    && typeof item.bizNo === "string" && item.bizNo.trim()
    && typeof item.eventStatus === "string" && item.eventStatus.trim()
    && typeof item.statusType === "string" && item.statusType.trim()
    && typeof item.publishStatus === "string" && item.publishStatus.trim()
    && Number.isInteger(item.retryCount) && item.retryCount >= 0
    && typeof item.createdAt === "string" && item.createdAt.trim());
}

export const batchApi = {
  getList: ({ batchDate = "", batchStatus = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({ batchDate, batchStatus, pageNo: String(pageNo), pageSize: String(pageSize) });
    return request(`/api/clearing/batches?${params.toString()}`).then(normalizeClearingBatchPage);
  },
  getDetail: (batchNo) => request(`/api/clearing/batches/${batchNo}`)
    .then((data) => normalizeClearingBatchResult(data, batchNo)),
  create: (payload) => postJson("/api/clearing/batches", payload)
    .then(normalizeClearingBatchResult),
  rerun: (batchNo, payload) => postJson(`/api/clearing/batches/${batchNo}/rerun`, payload)
    .then((data) => normalizeClearingBatchResult(data, batchNo))
};

export const orderApi = {
  getList: ({ batchNo = "", orderNo = "", paymentOrderId = "", clearingStatus = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({ batchNo, orderNo, paymentOrderId, clearingStatus, pageNo: String(pageNo), pageSize: String(pageSize) });
    return request(`/api/clearing/orders?${params.toString()}`).then(normalizeClearingOrderPage);
  },
  buildListExportUrl: ({ batchNo = "", orderNo = "", paymentOrderId = "", clearingStatus = "" } = {}) => {
    const params = new URLSearchParams({ batchNo, orderNo, paymentOrderId, clearingStatus });
    return `/api/clearing/orders/export?${params.toString()}`;
  },
  getDetail: (clearingNo) => request(`/api/clearing/orders/${clearingNo}`)
    .then((data) => normalizeClearingOrderDetailResult(data, clearingNo)),
  buildDetailExportUrl: (clearingNo) => `/api/clearing/orders/${clearingNo}/export`
};

export const ruleApi = {
  getList: ({ ruleType = "", ruleStatus = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({ ruleType, ruleStatus, pageNo: String(pageNo), pageSize: String(pageSize) });
    return requestPage(`/api/clearing/rules?${params.toString()}`);
  },
  create: (payload) => postJson("/api/clearing/rules", payload)
    .then(normalizeClearingRuleResult),
  enable: (ruleNo) => postJson(`/api/clearing/rules/${ruleNo}/enable`, {})
    .then((data) => normalizeClearingRuleResult(data, ruleNo)),
  disable: (ruleNo) => postJson(`/api/clearing/rules/${ruleNo}/disable`, {})
    .then((data) => normalizeClearingRuleResult(data, ruleNo))
};

export const feeApi = {
  getList: ({ feeType = "", status = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({ feeType, status, pageNo: String(pageNo), pageSize: String(pageSize) });
    return requestPage(`/api/clearing/fees?${params.toString()}`);
  },
  create: (payload) => postJson("/api/clearing/fees", payload)
    .then(normalizeFeeRuleResult)
};

export const shareApi = {
  getList: ({ clearingNo = "", shareType = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({ clearingNo, shareType, pageNo: String(pageNo), pageSize: String(pageSize) });
    return requestPage(`/api/clearing/shares?${params.toString()}`);
  }
};

export const eventApi = {
  getList: ({ eventType = "", bizNo = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({ eventType, bizNo, pageNo: String(pageNo), pageSize: String(pageSize) });
    return request(`/api/clearing/events?${params.toString()}`).then(normalizeClearingEventListPage);
  },
  consumePaymentSuccess: (payload) => postJson("/api/clearing/events/payments/success", payload)
    .then((data) => normalizeClearingEventResult(data, payload?.paymentOrderId)),
  replayFailed: () => postJson("/api/clearing/events/replay-failed", {})
    .then(normalizeClearingReplayResult)
};
