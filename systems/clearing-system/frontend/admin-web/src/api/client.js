function createBusinessError(payload, fallbackMessage) {
  const message = payload?.message || fallbackMessage || "Request failed";
  const displayMessage = `${message} [${payload?.code || "CLEARING-CLIENT"}${payload?.requestId ? ` / ${payload.requestId}` : ""}]`;
  const error = new Error(displayMessage);
  error.code = payload?.code || "CLEARING-CLIENT";
  error.requestId = payload?.requestId || "";
  return error;
}

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
  let response;
  try {
    response = await fetch(url, {
      headers: { "Content-Type": "application/json" },
      ...options
    });
  } catch (error) {
    const code = error?.name === "AbortError"
      ? "CLEARING-CLIENT-TIMEOUT"
      : "CLEARING-CLIENT-NETWORK";
    throw createBusinessError({ code }, error?.name === "AbortError" ? "清分服务请求超时" : "无法连接清分服务");
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
  return normalizePagePayload(payload.data);
}

function postJson(url, payload) {
  return request(url, {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function normalizePagePayload(data) {
  if (data && typeof data === "object"
      && Object.prototype.hasOwnProperty.call(data, "items")
      && !Array.isArray(data.items)) {
    return { ...data, items: [] };
  }
  return data;
}

export const batchApi = {
  getList: ({ batchDate = "", batchStatus = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({ batchDate, batchStatus, pageNo: String(pageNo), pageSize: String(pageSize) });
    return request(`/api/clearing/batches?${params.toString()}`);
  },
  getDetail: (batchNo) => request(`/api/clearing/batches/${batchNo}`),
  create: (payload) => postJson("/api/clearing/batches", payload),
  rerun: (batchNo, payload) => postJson(`/api/clearing/batches/${batchNo}/rerun`, payload)
};

export const orderApi = {
  getList: ({ batchNo = "", orderNo = "", paymentOrderId = "", clearingStatus = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({ batchNo, orderNo, paymentOrderId, clearingStatus, pageNo: String(pageNo), pageSize: String(pageSize) });
    return request(`/api/clearing/orders?${params.toString()}`);
  },
  buildListExportUrl: ({ batchNo = "", orderNo = "", paymentOrderId = "", clearingStatus = "" } = {}) => {
    const params = new URLSearchParams({ batchNo, orderNo, paymentOrderId, clearingStatus });
    return `/api/clearing/orders/export?${params.toString()}`;
  },
  getDetail: (clearingNo) => request(`/api/clearing/orders/${clearingNo}`),
  buildDetailExportUrl: (clearingNo) => `/api/clearing/orders/${clearingNo}/export`
};

export const ruleApi = {
  getList: ({ ruleType = "", ruleStatus = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({ ruleType, ruleStatus, pageNo: String(pageNo), pageSize: String(pageSize) });
    return request(`/api/clearing/rules?${params.toString()}`);
  },
  create: (payload) => postJson("/api/clearing/rules", payload),
  enable: (ruleNo) => postJson(`/api/clearing/rules/${ruleNo}/enable`, {}),
  disable: (ruleNo) => postJson(`/api/clearing/rules/${ruleNo}/disable`, {})
};

export const feeApi = {
  getList: ({ feeType = "", status = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({ feeType, status, pageNo: String(pageNo), pageSize: String(pageSize) });
    return request(`/api/clearing/fees?${params.toString()}`);
  },
  create: (payload) => postJson("/api/clearing/fees", payload)
};

export const shareApi = {
  getList: ({ clearingNo = "", shareType = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({ clearingNo, shareType, pageNo: String(pageNo), pageSize: String(pageSize) });
    return request(`/api/clearing/shares?${params.toString()}`);
  }
};

export const eventApi = {
  getList: ({ eventType = "", bizNo = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({ eventType, bizNo, pageNo: String(pageNo), pageSize: String(pageSize) });
    return request(`/api/clearing/events?${params.toString()}`);
  },
  consumePaymentSuccess: (payload) => postJson("/api/clearing/events/payments/success", payload),
  replayFailed: () => postJson("/api/clearing/events/replay-failed", {})
};
