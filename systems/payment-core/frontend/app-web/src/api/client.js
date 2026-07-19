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

export const paymentApi = {
  prepay: (data) => request("/api/payments/prepay", { method: "POST", body: JSON.stringify(data) }),
  getCashier: (prepayOrderNo) => request(`/api/payments/cashier/${prepayOrderNo}`),
  getDetail: (paymentOrderId) => request(`/api/payments/${paymentOrderId}`),
  submit: (data) => request("/api/payments/submit", { method: "POST", body: JSON.stringify(data) }),
  callback: (channel, data) => request(`/api/payments/callback/${channel}`, { method: "POST", body: JSON.stringify(data) }),
  query: (data) => request("/api/payments/query", { method: "POST", body: JSON.stringify(data) }),
  close: (data) => request("/api/payments/close", { method: "POST", body: JSON.stringify(data) })
};
