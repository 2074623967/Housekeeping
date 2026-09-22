const createRequestError = (code, message, cause) => {
  const error = new Error(`${message} [${code}]`);
  error.code = code;
  error.cause = cause;
  return error;
};

const jsonRequest = async (url, options = {}) => {
  let response;
  try {
    response = await fetch(url, {
      headers: { "Content-Type": "application/json" },
      ...options
    });
  } catch (error) {
    const code = error?.name === "AbortError"
      ? "REFUND-CLIENT-TIMEOUT"
      : "REFUND-CLIENT-NETWORK";
    throw createRequestError(code, error?.name === "AbortError" ? "退款服务请求超时" : "无法连接退款服务", error);
  }
  let body;
  try {
    body = await response.json();
  } catch (error) {
    throw createRequestError("REFUND-CLIENT-INVALID-RESPONSE", `退款服务返回了无法解析的响应 [HTTP ${response.status}]`, error);
  }
  if (!response.ok || body.code !== "0") {
    const code = body?.code || `REFUND-CLIENT-HTTP-${response.status}`;
    throw createRequestError(code, body?.message || "请求失败");
  }
  return body.data;
};

export const getOverview = () => jsonRequest("/api/refunds/overview");

export const getRefunds = (query) => {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      params.set(key, value);
    }
  });
  return jsonRequest(`/api/refunds?${params.toString()}`).then(normalizePage);
};

export const getRefundDetail = (refundOrderId) =>
  jsonRequest(`/api/refunds/${encodeURIComponent(refundOrderId)}`).then(normalizeRefundDetail);

export const downloadRefundDetail = (refundOrderId) =>
  fetch(`/api/refunds/${encodeURIComponent(refundOrderId)}/export`).then(async (response) => {
    if (!response.ok) {
      throw new Error("导出失败");
    }
    return response.blob();
  });

export const postRefundAction = (refundOrderId, action, payload = {}) =>
  jsonRequest(`/api/refunds/${encodeURIComponent(refundOrderId)}/${action}`, {
    method: "POST",
    body: JSON.stringify(payload)
  });

export const getRefundOutbox = (query) => {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      params.set(key, value);
    }
  });
  return jsonRequest(`/api/refunds/outbox?${params.toString()}`).then(normalizePage);
};

const normalizePage = (page) => ({
  ...page,
  items: Array.isArray(page?.items) ? page.items : []
});

const normalizeRefundDetail = (detail) => {
  if (!detail || typeof detail !== "object") return detail;
  return {
    ...detail,
    operationLogs: Array.isArray(detail.operationLogs) ? detail.operationLogs : []
  };
};

export const dispatchRefundOutbox = (eventId, payload = {}) =>
  jsonRequest(`/api/refunds/outbox/${encodeURIComponent(eventId)}/dispatch`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
