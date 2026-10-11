function createBusinessError(payload, fallbackMessage) {
  const message = payload?.message || fallbackMessage || "Request failed";
  const displayMessage = `${message} [${payload?.code || "SETTLEMENT-CLIENT"}${payload?.requestId ? ` / ${payload.requestId}` : ""}]`;
  const error = new Error(displayMessage);
  error.code = payload?.code || "SETTLEMENT-CLIENT";
  error.requestId = payload?.requestId || "";
  return error;
}

async function parseResponsePayload(response) {
  try {
    return await response.json();
  } catch (error) {
    const parseError = new Error("结算服务返回了无法解析的响应");
    parseError.code = "SETTLEMENT-CLIENT-INVALID-RESPONSE";
    parseError.status = response.status;
    parseError.cause = error;
    throw parseError;
  }
}

const CLIENT_TIMEOUT_MS = 10000;

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

export function normalizePayoutBatchPage(data) {
  const normalized = normalizePagePayload(data);
  if (normalized.items.some((item) => !isValidPayoutBatch(item))) {
    throw invalidSettlementResult("出款批次列表返回了无法确认的金额状态事实");
  }
  return normalized;
}

function isValidPayoutBatch(item) {
  if (!item || typeof item !== "object"
      || typeof item.payoutBatchNo !== "string" || !item.payoutBatchNo.trim()
      || typeof item.batchNo !== "string" || !item.batchNo.trim()
      || typeof item.payoutChannel !== "string" || !item.payoutChannel.trim()
      || !isStatusFact(item.payoutStatus, item.payoutStatusType)
      || !Number.isInteger(item.payoutCount) || item.payoutCount < 0
      || !Number.isInteger(item.successCount) || item.successCount < 0
      || !Number.isInteger(item.failedCount) || item.failedCount < 0
      || item.successCount + item.failedCount > item.payoutCount
      || !isValidAmount(item.totalAmount, true)
      || typeof item.createdBy !== "string" || !item.createdBy.trim()
      || typeof item.createdAt !== "string" || !item.createdAt.trim()) {
    return false;
  }
  const terminal = item.payoutStatus.includes("完成") || item.payoutStatus.includes("失败");
  return !terminal || (typeof item.finishedAt === "string" && item.finishedAt.trim());
}

export function normalizePayoutRecordPage(data, expectedPayoutBatchNo = "") {
  const normalized = normalizePagePayload(data);
  if (normalized.items.some((item) => !isValidPayoutRecord(item, expectedPayoutBatchNo))) {
    throw invalidSettlementResult("出款记录列表返回了无法确认的归属金额事实");
  }
  return normalized;
}

function isValidPayoutRecord(item, expectedPayoutBatchNo) {
  return Boolean(item && typeof item === "object"
    && typeof item.payoutNo === "string" && item.payoutNo.trim()
    && typeof item.payoutBatchNo === "string" && item.payoutBatchNo.trim()
    && (!expectedPayoutBatchNo || item.payoutBatchNo === expectedPayoutBatchNo)
    && typeof item.settlementNo === "string" && item.settlementNo.trim()
    && typeof item.targetNo === "string" && item.targetNo.trim()
    && typeof item.targetName === "string" && item.targetName.trim()
    && isValidAmount(item.payoutAmount, false)
    && isStatusFact(item.payoutStatus, item.payoutStatusType)
    && ((typeof item.retryCount === "string" && /^\d+$/.test(item.retryCount.trim()))
      || (Number.isInteger(item.retryCount) && item.retryCount >= 0))
    && (typeof item.retryCount === "number" ? item.retryCount >= 0 : true)
    && typeof item.createdAt === "string" && item.createdAt.trim());
}

export function normalizeDashboardPayload(data) {
  if (!data || typeof data !== "object") {
    return data;
  }
  return {
    ...data,
    recentBatches: Array.isArray(data.recentBatches) ? data.recentBatches : [],
    recentOrders: Array.isArray(data.recentOrders) ? data.recentOrders : [],
    riskPayouts: Array.isArray(data.riskPayouts) ? data.riskPayouts : [],
    recentEvents: Array.isArray(data.recentEvents) ? data.recentEvents : []
  };
}

function normalizeOrderDetail(data) {
  if (!data || typeof data !== "object") {
    return data;
  }
  return {
    ...data,
    items: Array.isArray(data.items) ? data.items : [],
    auditLogs: Array.isArray(data.auditLogs) ? data.auditLogs : []
  };
}

function invalidSettlementResult(message) {
  const error = createBusinessError(
    { code: "SETTLEMENT-CLIENT-INVALID-BUSINESS-RESULT" },
    message
  );
  return error;
}

export function normalizeSettlementBatchResult(data, expectedBatchNo = "") {
  if (!data || typeof data !== "object" || typeof data.batchNo !== "string"
      || !data.batchNo.trim() || (expectedBatchNo && data.batchNo !== expectedBatchNo)) {
    throw invalidSettlementResult("结算批次响应未确认当前批次");
  }
  return data;
}

export function normalizeSettlementOrderResult(data, expectedSettlementNo = "") {
  if (!data || typeof data !== "object" || typeof data.settlementNo !== "string"
      || !data.settlementNo.trim()
      || typeof data.businessChainId !== "string" || !data.businessChainId.trim()
      || typeof data.clearingNo !== "string" || !data.clearingNo.trim()
      || (data.payScene != null && typeof data.payScene !== "string")
      || typeof data.targetType !== "string" || !data.targetType.trim()
      || typeof data.targetNo !== "string" || !data.targetNo.trim()
      || !isValidAmount(data.shouldSettleAmount, false)
      || !isValidAmount(data.deductAmount, true)
      || !isValidAmount(data.netSettleAmount, true)
      || !isAmountBreakdownConsistent(data)
      || !isStatusFact(data.settlementStatus, data.settlementStatusType)
      || !isStatusFact(data.auditStatus, data.auditStatusType)
      || !isStatusFact(data.payoutStatus, data.payoutStatusType)
      || (expectedSettlementNo && data.settlementNo !== expectedSettlementNo)) {
    throw invalidSettlementResult("结算单响应未确认当前结算单");
  }
  return data;
}

function isValidAmount(value, allowZero) {
  if (typeof value !== "string" && typeof value !== "number") {
    return false;
  }
  const normalized = String(value).trim().replace(/^¥\s*/, "");
  if (!/^(?:0|[1-9]\d{0,15})(?:\.\d{1,2})?$/.test(normalized)) {
    return false;
  }
  return allowZero || Number(normalized) > 0;
}

function toMinorUnits(value) {
  const [integerPart, fractionPart = ""] = String(value).trim()
    .replace(/^¥\s*/, "")
    .split(".");
  return BigInt(integerPart) * 100n + BigInt(fractionPart.padEnd(2, "0"));
}

function isAmountBreakdownConsistent(data) {
  return toMinorUnits(data.shouldSettleAmount) - toMinorUnits(data.deductAmount)
    === toMinorUnits(data.netSettleAmount)
    && toMinorUnits(data.deductAmount) <= toMinorUnits(data.shouldSettleAmount);
}

function isStatusFact(status, statusType) {
  return typeof status === "string" && Boolean(status.trim())
    && typeof statusType === "string" && Boolean(statusType.trim());
}

export function normalizeSettlementOrderDetailResult(data, expectedSettlementNo) {
  if (!data || typeof data !== "object" || !data.order
      || typeof data.order !== "object") {
    throw invalidSettlementResult("结算单详情响应未确认当前结算单");
  }
  normalizeSettlementOrderResult(data.order, expectedSettlementNo);
  return normalizeOrderDetail(data);
}

export function normalizePayoutBatchResult(data, expectedPayoutBatchNo = "") {
  if (!data || typeof data !== "object" || typeof data.payoutBatchNo !== "string"
      || !data.payoutBatchNo.trim()
      || (expectedPayoutBatchNo && data.payoutBatchNo !== expectedPayoutBatchNo)) {
    throw invalidSettlementResult("出款批次响应未确认当前批次");
  }
  return data;
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
      ? "SETTLEMENT-CLIENT-TIMEOUT"
      : "SETTLEMENT-CLIENT-NETWORK";
    throw createBusinessError({ code }, error?.name === "AbortError" ? "结算服务请求超时" : "无法连接结算服务");
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

export const batchApi = {
  getList: ({ batchDate = "", batchStatus = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({ batchDate, batchStatus, pageNo: String(pageNo), pageSize: String(pageSize) });
    return requestPage(`/api/settlements/batches?${params.toString()}`);
  },
  getDetail: (batchNo) => request(`/api/settlements/batches/${batchNo}`)
    .then((data) => normalizeSettlementBatchResult(data, batchNo)),
  create: (payload) => postJson("/api/settlements/batches", payload)
    .then(normalizeSettlementBatchResult)
};

export const dashboardApi = {
  get: () => request("/api/settlements/dashboard").then(normalizeDashboardPayload)
};

export const orderApi = {
  getList: ({ batchNo = "", targetType = "", settlementStatus = "", clearingNo = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({ batchNo, targetType, settlementStatus, clearingNo, pageNo: String(pageNo), pageSize: String(pageSize) });
    return requestPage(`/api/settlements/orders?${params.toString()}`);
  },
  create: (payload) => postJson("/api/settlements/orders", payload)
    .then(normalizeSettlementOrderResult),
  audit: (settlementNo, payload) => postJson(`/api/settlements/orders/${settlementNo}/audit`, payload)
    .then((data) => normalizeSettlementOrderResult(data, settlementNo)),
  reject: (settlementNo, payload) => postJson(`/api/settlements/orders/${settlementNo}/reject`, payload)
    .then((data) => normalizeSettlementOrderResult(data, settlementNo)),
  getDetail: (settlementNo) => request(`/api/settlements/orders/${settlementNo}/full`)
    .then((data) => normalizeSettlementOrderDetailResult(data, settlementNo))
};

export const payoutApi = {
  getList: ({ batchNo = "", payoutStatus = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({ batchNo, payoutStatus, pageNo: String(pageNo), pageSize: String(pageSize) });
    return request(`/api/settlements/payouts?${params.toString()}`).then(normalizePayoutBatchPage);
  },
  buildBatchExportUrl: ({ batchNo = "", payoutStatus = "" } = {}) => {
    const params = new URLSearchParams({ batchNo, payoutStatus });
    return `/api/settlements/payouts/export?${params.toString()}`;
  },
  create: (payload) => postJson("/api/settlements/payouts", payload)
    .then(normalizePayoutBatchResult),
  inspect: () => postJson("/api/settlements/payouts/inspect", {}),
  execute: (payoutBatchNo, payload) => postJson(`/api/settlements/payouts/${payoutBatchNo}/execute`, payload)
    .then((data) => normalizePayoutBatchResult(data, payoutBatchNo)),
  retry: (payoutBatchNo, payload) => postJson(`/api/settlements/payouts/${payoutBatchNo}/retry`, payload)
    .then((data) => normalizePayoutBatchResult(data, payoutBatchNo)),
  records: (payoutBatchNo, { payoutStatus = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({ payoutStatus, pageNo: String(pageNo), pageSize: String(pageSize) });
    return request(`/api/settlements/payouts/${payoutBatchNo}/records?${params.toString()}`)
      .then((data) => normalizePayoutRecordPage(data, payoutBatchNo));
  },
  buildRecordExportUrl: (payoutBatchNo, { payoutStatus = "" } = {}) => {
    const params = new URLSearchParams({ payoutStatus });
    return `/api/settlements/payouts/${payoutBatchNo}/records/export?${params.toString()}`;
  }
};

export const eventApi = {
  getList: ({ eventType = "", bizNo = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({ eventType, bizNo, pageNo: String(pageNo), pageSize: String(pageSize) });
    return requestPage(`/api/settlements/events?${params.toString()}`);
  },
  consumeClearingGenerated: (payload) => postJson("/api/settlements/events/clearing/generated", payload)
};
