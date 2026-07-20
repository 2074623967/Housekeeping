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
    return null;
  }
}

async function request(url, options = {}) {
  const response = await fetch(url, {
    headers: { "Content-Type": "application/json" },
    ...options
  });
  const payload = await parseResponsePayload(response);
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
    return request(`/api/settlements/batches?${params.toString()}`);
  },
  create: (payload) => postJson("/api/settlements/batches", payload)
};

export const orderApi = {
  getList: ({ batchNo = "", targetType = "", settlementStatus = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({ batchNo, targetType, settlementStatus, pageNo: String(pageNo), pageSize: String(pageSize) });
    return request(`/api/settlements/orders?${params.toString()}`);
  },
  create: (payload) => postJson("/api/settlements/orders", payload),
  audit: (settlementNo, payload) => postJson(`/api/settlements/orders/${settlementNo}/audit`, payload),
  reject: (settlementNo, payload) => postJson(`/api/settlements/orders/${settlementNo}/reject`, payload),
  getDetail: (settlementNo) => request(`/api/settlements/orders/${settlementNo}/full`)
};

export const payoutApi = {
  getList: ({ batchNo = "", payoutStatus = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({ batchNo, payoutStatus, pageNo: String(pageNo), pageSize: String(pageSize) });
    return request(`/api/settlements/payouts?${params.toString()}`);
  },
  create: (payload) => postJson("/api/settlements/payouts", payload),
  retry: (payoutBatchNo, payload) => postJson(`/api/settlements/payouts/${payoutBatchNo}/retry`, payload),
  records: (payoutBatchNo, { payoutStatus = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({ payoutStatus, pageNo: String(pageNo), pageSize: String(pageSize) });
    return request(`/api/settlements/payouts/${payoutBatchNo}/records?${params.toString()}`);
  }
};

export const eventApi = {
  getList: ({ eventType = "", bizNo = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({ eventType, bizNo, pageNo: String(pageNo), pageSize: String(pageSize) });
    return request(`/api/settlements/events?${params.toString()}`);
  },
  consumeClearingGenerated: (payload) => postJson("/api/settlements/events/clearing/generated", payload)
};
