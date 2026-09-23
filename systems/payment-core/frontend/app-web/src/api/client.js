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

const CLIENT_TIMEOUT_MS = 10000;

async function parseResponsePayload(response) {
  try {
    return await response.json();
  } catch (error) {
    const parseError = new Error("支付服务返回了无法解析的响应");
    parseError.code = "PAYMENT-CLIENT-INVALID-RESPONSE";
    parseError.status = response.status;
    parseError.cause = error;
    throw parseError;
  }
}

async function request(url, options = {}) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), CLIENT_TIMEOUT_MS);
  try {
    let response;
    try {
      response = await fetch(url, {
        headers: { "Content-Type": "application/json" },
        ...options,
        signal: controller.signal
      });
    } catch (error) {
      const transportCode = error?.name === "AbortError"
        ? "PAYMENT-CLIENT-TIMEOUT"
        : "PAYMENT-CLIENT-NETWORK";
      throw createBusinessError({ code: transportCode }, error?.name === "AbortError" ? "支付服务请求超时" : "无法连接支付服务");
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
  } finally {
    clearTimeout(timeoutId);
  }
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
  getCashier: (prepayOrderNo) => request(`/api/payments/cashier/${encodeURIComponent(prepayOrderNo)}`),
  getDetail: (paymentOrderId) => request(`/api/payments/${encodeURIComponent(paymentOrderId)}`),
  getRefundDetail: (refundOrderId) => request(`/api/refunds/${encodeURIComponent(refundOrderId)}`),
  submit: (payload) => postJson("/api/payments/submit", payload),
  query: (payload) => postJson("/api/payments/query", payload),
  close: (payload) => postJson("/api/payments/close", payload)
};
