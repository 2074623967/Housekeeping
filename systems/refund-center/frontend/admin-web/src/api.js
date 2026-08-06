const jsonRequest = async (url, options = {}) => {
  const response = await fetch(url, {
    headers: { "Content-Type": "application/json" },
    ...options
  });
  const body = await response.json();
  if (!response.ok || body.code !== "0") {
    throw new Error(body.message || "请求失败");
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
  return jsonRequest(`/api/refunds?${params.toString()}`);
};

export const getRefundDetail = (refundOrderId) =>
  jsonRequest(`/api/refunds/${encodeURIComponent(refundOrderId)}`);

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
  return jsonRequest(`/api/refunds/outbox?${params.toString()}`);
};

export const dispatchRefundOutbox = (eventId, payload = {}) =>
  jsonRequest(`/api/refunds/outbox/${encodeURIComponent(eventId)}/dispatch`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
