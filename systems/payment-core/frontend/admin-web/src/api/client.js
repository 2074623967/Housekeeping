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

export const dashboardApi = {
  getSummary: () => request("/api/dashboard/summary")
};

export const orderApi = {
  getList: () => request("/api/orders")
};

export const paymentApi = {
  getList: () => request("/api/payments"),
  getDetail: (paymentOrderId) => request(`/api/payments/${paymentOrderId}`),
  prepay: (payload) => request("/api/payments/prepay", { method: "POST", body: JSON.stringify(payload) }),
  query: (paymentOrderId) => request("/api/payments/query", { method: "POST", body: JSON.stringify({ paymentOrderId }) }),
  close: (paymentOrderId) => request("/api/payments/close", { method: "POST", body: JSON.stringify({ paymentOrderId }) }),
  callback: (channel, paymentOrderId) =>
    request(`/api/payments/callback/${channel}`, {
      method: "POST",
      body: JSON.stringify({
        paymentOrderId,
        channelTransactionNo: `SIM${Date.now()}`,
        tradeStatus: "SUCCESS"
      })
    })
};

export const refundApi = {
  getList: () => request("/api/refunds")
};

export const settlementApi = {
  getWorkerList: () => request("/api/settlements/workers")
};
