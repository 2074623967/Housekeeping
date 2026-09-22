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
export const getBatches = ({ pageNo = 1, pageSize = 20 } = {}) => {
  const params = new URLSearchParams({ pageNo: String(pageNo), pageSize: String(pageSize) });
  return request(`/api/reconciliation/batches?${params.toString()}`)
    .then((page) => normalizePage(page, pageNo, pageSize));
};
export const getBatchDetail = (batchNo) =>
  request(`/api/reconciliation/batches/${encodeURIComponent(batchNo)}`).then(normalizeBatchDetail);
export const downloadBatchDetail = (batchNo) =>
  fetch(`/api/reconciliation/batches/${encodeURIComponent(batchNo)}/export`)
    .then(async (response) => {
      if (!response.ok) throw new Error("导出失败");
      return response.blob();
    });
export const getDifferences = (query = {}) => {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => value && params.set(key, value));
  return request(`/api/reconciliation/differences?${params.toString()}`)
    .then((page) => normalizePage(page, query.pageNo, query.pageSize));
};

const normalizePage = (page, pageNo = 1, pageSize = 20) => ({
  ...page,
  items: Array.isArray(page?.items) ? page.items : [],
  total: Number.isFinite(Number(page?.total)) ? Number(page.total) : 0,
  pageNo: Number.isInteger(Number(page?.pageNo)) ? Number(page.pageNo) : Number(pageNo) || 1,
  pageSize: Number.isInteger(Number(page?.pageSize)) ? Number(page.pageSize) : Number(pageSize) || 20
});

const normalizeBatchDetail = (detail) => {
  if (!detail || typeof detail !== "object") return detail;
  return {
    ...detail,
    channelRecords: Array.isArray(detail.channelRecords) ? detail.channelRecords : [],
    internalRecords: Array.isArray(detail.internalRecords) ? detail.internalRecords : [],
    differences: Array.isArray(detail.differences) ? detail.differences : []
  };
};
export const runBatch = (batchNo) =>
  request(`/api/reconciliation/batches/${encodeURIComponent(batchNo)}/run`, { method: "POST" });
export const checkBatchReadiness = (batchNo) =>
  request(`/api/reconciliation/batches/${encodeURIComponent(batchNo)}/readiness-check`, {
    method: "POST"
  });
export const getCrossSystemChain = (businessChainId) =>
  request(`/api/reconciliation/cross-system/chains/${encodeURIComponent(businessChainId)}`);
export const resolveDifference = (differenceNo, payload) =>
  request(`/api/reconciliation/differences/${encodeURIComponent(differenceNo)}/resolve`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
