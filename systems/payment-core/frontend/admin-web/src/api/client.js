async function request(url, options = {}) {
  const response = await fetch(url, {
    headers: { "Content-Type": "application/json" },
    ...options
  });
  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`);
  }
  const payload = await response.json();
  if (payload.code !== "0") {
    throw new Error(payload.message || "Request failed");
  }
  return payload.data;
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

function buildSuccessCallbackPayload(paymentOrderId) {
  return {
    paymentOrderId,
    channelTransactionNo: `SIM${Date.now()}`,
    tradeStatus: "SUCCESS"
  };
}

export const dashboardApi = {
  getSummary: () => request("/api/dashboard/summary")
};

export const orderApi = {
  getList: () => request("/api/orders")
};

export const billApi = {
  getList: () => request("/api/bills")
};

export const paymentFlowApi = {
  getList: () => request("/api/payment-flows")
};

export const paymentApi = {
  getList: () => request("/api/payments"),
  getDetail: (paymentOrderId) => request(`/api/payments/${paymentOrderId}`),
  prepay: (payload) => postJson("/api/payments/prepay", payload),
  query: (paymentOrderId) => postJson("/api/payments/query", { paymentOrderId }),
  close: (paymentOrderId) => postJson("/api/payments/close", { paymentOrderId }),
  callback: (channelCode, paymentOrderId) => postJson(
    `/api/payments/callback/${channelCode}`,
    buildSuccessCallbackPayload(paymentOrderId)
  )
};

export const refundApi = {
  getList: () => request("/api/refunds")
};

export const settlementApi = {
  getWorkerList: () => request("/api/settlements/workers")
};
