const createRequestError = (code, message, cause) => {
  const error = new Error(`${message} [${code}]`);
  error.code = code;
  error.cause = cause;
  return error;
};

const request = async (url, options = {}) => {
  let response;
  try {
    response = await fetch(url, {
      headers: { "Content-Type": "application/json" },
      ...options
    });
  } catch (error) {
    const code = error?.name === "AbortError"
      ? "RECONCILIATION-CLIENT-TIMEOUT"
      : "RECONCILIATION-CLIENT-NETWORK";
    throw createRequestError(code, error?.name === "AbortError" ? "对账服务请求超时" : "无法连接对账服务", error);
  }
  let body;
  try {
    body = await response.json();
  } catch (error) {
    throw createRequestError("RECONCILIATION-CLIENT-INVALID-RESPONSE", `对账服务返回了无法解析的响应 [HTTP ${response.status}]`, error);
  }
  if (!response.ok || body?.code !== "0") {
    throw createRequestError(body?.code || `RECONCILIATION-CLIENT-HTTP-${response.status}`, body?.message || "请求失败");
  }
  return body.data;
};

export const getOverview = () => request("/api/reconciliation/overview");
export const getBatches = () => request("/api/reconciliation/batches");
export const getBatchDetail = (batchNo) =>
  request(`/api/reconciliation/batches/${encodeURIComponent(batchNo)}`);
export const downloadBatchDetail = (batchNo) =>
  fetch(`/api/reconciliation/batches/${encodeURIComponent(batchNo)}/export`)
    .then(async (response) => {
      if (!response.ok) throw new Error("导出失败");
      return response.blob();
    });
export const getDifferences = (query = {}) => {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => value && params.set(key, value));
  return request(`/api/reconciliation/differences?${params.toString()}`);
};
export const runBatch = (batchNo) =>
  request(`/api/reconciliation/batches/${encodeURIComponent(batchNo)}/run`, { method: "POST" });
export const checkBatchReadiness = (batchNo) =>
  request(`/api/reconciliation/batches/${encodeURIComponent(batchNo)}/readiness-check`, {
    method: "POST"
  });
export const resolveDifference = (differenceNo, payload) =>
  request(`/api/reconciliation/differences/${encodeURIComponent(differenceNo)}/resolve`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
