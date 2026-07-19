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

export const paymentApi = {
  prepay: (payload) => postJson("/api/payments/prepay", payload),
  getCashier: (prepayOrderNo) => request(`/api/payments/cashier/${prepayOrderNo}`),
  getDetail: (paymentOrderId) => request(`/api/payments/${paymentOrderId}`),
  submit: (payload) => postJson("/api/payments/submit", payload),
  callback: (channelCode, payload) => postJson(`/api/payments/callback/${channelCode}`, payload),
  query: (payload) => postJson("/api/payments/query", payload),
  close: (payload) => postJson("/api/payments/close", payload)
};
