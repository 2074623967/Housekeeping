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

async function request(url, options = {}) {
  let response;
  try {
    response = await fetch(url, {
      headers: { "Content-Type": "application/json" },
      ...options
    });
  } catch (error) {
    const code = error?.name === "AbortError"
      ? "SETTLEMENT-CLIENT-TIMEOUT"
      : "SETTLEMENT-CLIENT-NETWORK";
    throw createBusinessError({ code }, error?.name === "AbortError" ? "结算服务请求超时" : "无法连接结算服务");
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
  getDetail: (batchNo) => request(`/api/settlements/batches/${batchNo}`),
  create: (payload) => postJson("/api/settlements/batches", payload)
};

export const dashboardApi = {
  get: () => request("/api/settlements/dashboard").then(normalizeDashboardPayload)
};

export const orderApi = {
  getList: ({ batchNo = "", targetType = "", settlementStatus = "", clearingNo = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({ batchNo, targetType, settlementStatus, clearingNo, pageNo: String(pageNo), pageSize: String(pageSize) });
    return requestPage(`/api/settlements/orders?${params.toString()}`);
  },
  create: (payload) => postJson("/api/settlements/orders", payload),
  audit: (settlementNo, payload) => postJson(`/api/settlements/orders/${settlementNo}/audit`, payload),
  reject: (settlementNo, payload) => postJson(`/api/settlements/orders/${settlementNo}/reject`, payload),
  getDetail: (settlementNo) => request(`/api/settlements/orders/${settlementNo}/full`).then(normalizeOrderDetail)
};

export const payoutApi = {
  getList: ({ batchNo = "", payoutStatus = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({ batchNo, payoutStatus, pageNo: String(pageNo), pageSize: String(pageSize) });
    return requestPage(`/api/settlements/payouts?${params.toString()}`);
  },
  buildBatchExportUrl: ({ batchNo = "", payoutStatus = "" } = {}) => {
    const params = new URLSearchParams({ batchNo, payoutStatus });
    return `/api/settlements/payouts/export?${params.toString()}`;
  },
  create: (payload) => postJson("/api/settlements/payouts", payload),
  inspect: () => postJson("/api/settlements/payouts/inspect", {}),
  execute: (payoutBatchNo, payload) => postJson(`/api/settlements/payouts/${payoutBatchNo}/execute`, payload),
  retry: (payoutBatchNo, payload) => postJson(`/api/settlements/payouts/${payoutBatchNo}/retry`, payload),
  records: (payoutBatchNo, { payoutStatus = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({ payoutStatus, pageNo: String(pageNo), pageSize: String(pageSize) });
    return requestPage(`/api/settlements/payouts/${payoutBatchNo}/records?${params.toString()}`);
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
