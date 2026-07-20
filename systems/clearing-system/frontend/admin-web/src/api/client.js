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
    return request(`/api/clearing/batches?${params.toString()}`);
  },
  create: (payload) => postJson("/api/clearing/batches", payload),
  rerun: (batchNo, payload) => postJson(`/api/clearing/batches/${batchNo}/rerun`, payload)
};

export const orderApi = {
  getList: ({ batchNo = "", orderNo = "", clearingStatus = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({ batchNo, orderNo, clearingStatus, pageNo: String(pageNo), pageSize: String(pageSize) });
    return request(`/api/clearing/orders?${params.toString()}`);
  }
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
  consumePaymentSuccess: (payload) => postJson("/api/clearing/events/payments/success", payload)
};
