function createBusinessError(payload, fallbackMessage) {
  const message = payload?.message || fallbackMessage || "Request failed";
  const displayMessage = `${message} [${payload?.code || "PAYMENT-CLIENT"}${payload?.requestId ? ` / ${payload.requestId}` : ""}]`;
  const error = new Error(displayMessage);
  error.code = payload?.code || "PAYMENT-CLIENT";
  error.requestId = payload?.requestId || "";
  error.rawMessage = message;
  error.displayMessage = displayMessage;
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
