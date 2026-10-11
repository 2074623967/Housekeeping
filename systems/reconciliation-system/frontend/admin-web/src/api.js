const createRequestError = (code, message, cause) => {
  const error = new Error(`${message} [${code}]`);
  error.code = code;
  error.cause = cause;
  return error;
};

const CLIENT_TIMEOUT_MS = 10000;

const request = async (url, options = {}) => {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), CLIENT_TIMEOUT_MS);
  let response;
  try {
    response = await fetch(url, {
      headers: { "Content-Type": "application/json" },
      ...options,
      signal: controller.signal
    });
  } catch (error) {
    const code = error?.name === "AbortError"
      ? "RECONCILIATION-CLIENT-TIMEOUT"
      : "RECONCILIATION-CLIENT-NETWORK";
    throw createRequestError(code, error?.name === "AbortError" ? "对账服务请求超时" : "无法连接对账服务", error);
  } finally {
    clearTimeout(timeoutId);
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
  request(`/api/reconciliation/batches/${encodeURIComponent(batchNo)}`)
    .then((data) => normalizeReconciliationBatchResult(data, batchNo))
    .then(normalizeBatchDetail);
export const downloadBatchDetail = async (batchNo) => {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), CLIENT_TIMEOUT_MS);
  try {
    let response;
    try {
      response = await fetch(`/api/reconciliation/batches/${encodeURIComponent(batchNo)}/export`, {
        signal: controller.signal
      });
    } catch (error) {
      const code = error?.name === "AbortError"
        ? "RECONCILIATION-CLIENT-TIMEOUT"
        : "RECONCILIATION-CLIENT-NETWORK";
      throw createRequestError(code, error?.name === "AbortError" ? "对账服务导出请求超时" : "无法连接对账服务", error);
    }
    if (!response.ok) throw createRequestError(`RECONCILIATION-CLIENT-HTTP-${response.status}`, "导出失败");
    return response.blob();
  } finally {
    clearTimeout(timeoutId);
  }
};
export const getDifferences = (query = {}) => {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => value && params.set(key, value));
  return request(`/api/reconciliation/differences?${params.toString()}`)
    .then((page) => normalizePage(page, query.pageNo, query.pageSize));
};

const normalizePage = (page, pageNo = 1, pageSize = 20) => ({
  ...(Array.isArray(page) ? {} : page && typeof page === "object" ? page : {}),
  items: Array.isArray(page) ? page : Array.isArray(page?.items) ? page.items : [],
  total: Array.isArray(page)
    ? page.length
    : Number.isFinite(Number(page?.total)) ? Number(page.total) : 0,
  pageNo: Number.isInteger(Number(page?.pageNo)) && Number(page.pageNo) > 0
    ? Number(page.pageNo)
    : Number.isInteger(Number(pageNo)) && Number(pageNo) > 0 ? Number(pageNo) : 1,
  pageSize: Number.isInteger(Number(page?.pageSize)) && Number(page.pageSize) > 0
    ? Number(page.pageSize)
    : Number.isInteger(Number(pageSize)) && Number(pageSize) > 0 ? Number(pageSize) : 20
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

export const normalizeReconciliationBatchResult = (data, expectedBatchNo = "") => {
  if (!data || typeof data !== "object" || typeof data.batchNo !== "string"
      || !data.batchNo.trim() || (expectedBatchNo && data.batchNo !== expectedBatchNo)) {
    throw createRequestError(
      "RECONCILIATION-CLIENT-INVALID-BUSINESS-RESULT",
      "对账批次响应未确认当前批次"
    );
  }
  return data;
};

export const normalizeCrossSystemChainResult = (data, expectedBusinessChainId = "") => {
  if (!data || typeof data !== "object" || typeof data.businessChainId !== "string"
      || !data.businessChainId.trim()
      || !Number.isInteger(data.factCount) || data.factCount < 0
      || !Array.isArray(data.facts) || data.facts.length !== data.factCount
      || data.facts.some((fact) => !isValidChainFact(fact, data.businessChainId))
      || !CHAIN_STATUS_TYPES.has(data.chainStatus)
      || !RESPONSIBILITY_STATUS_TYPES.has(data.responsibilityStatus)
      || typeof data.diagnosis !== "string" || !data.diagnosis.trim()
      || typeof data.nextAction !== "string" || !data.nextAction.trim()
      || !Number.isInteger(data.abnormalFactCount) || data.abnormalFactCount < 0
      || data.abnormalFactCount > data.factCount
      || data.hasAbnormalFact !== (data.abnormalFactCount > 0)
      || !Number.isInteger(data.expectedStageCount) || data.expectedStageCount <= 0
      || !Number.isInteger(data.completedStageCount)
      || data.completedStageCount < 0
      || data.completedStageCount > data.expectedStageCount
      || !isConsistentChainState(data)
      || (expectedBusinessChainId && data.businessChainId !== expectedBusinessChainId)) {
    throw createRequestError(
      "RECONCILIATION-CLIENT-INVALID-BUSINESS-RESULT",
      "跨系统链路响应未确认当前业务链路"
    );
  }
  return data;
};

const CHAIN_STATUS_TYPES = new Set(["WAITING", "IN_PROGRESS", "EXCEPTION", "COMPLETED"]);
const RESPONSIBILITY_STATUS_TYPES = new Set(["UNASSIGNED", "PENDING", "MANUAL_REVIEW", "CLOSED"]);

const isValidChainFact = (fact, businessChainId) => Boolean(
  fact && typeof fact === "object"
    && typeof fact.businessChainId === "string"
    && fact.businessChainId.trim() === businessChainId.trim()
    && typeof fact.systemCode === "string" && fact.systemCode.trim()
    && typeof fact.factType === "string" && fact.factType.trim()
    && typeof fact.factNo === "string" && fact.factNo.trim()
    && typeof fact.factStatus === "string" && fact.factStatus.trim()
    && (fact.payScene == null || typeof fact.payScene === "string")
);

const isConsistentChainState = (data) => {
  const expectedResponsibility = {
    WAITING: "UNASSIGNED",
    IN_PROGRESS: "PENDING",
    EXCEPTION: "MANUAL_REVIEW",
    COMPLETED: "CLOSED"
  }[data.chainStatus];
  if (data.responsibilityStatus !== expectedResponsibility) {
    return false;
  }
  if (data.chainStatus === "EXCEPTION") {
    return data.abnormalFactCount > 0 && data.financialCloseable === false;
  }
  if (data.chainStatus === "COMPLETED") {
    return data.abnormalFactCount === 0
      && data.completedStageCount === data.expectedStageCount
      && data.financialCloseable === true;
  }
  return data.abnormalFactCount === 0 && data.financialCloseable === false;
};
export const runBatch = (batchNo) =>
  request(`/api/reconciliation/batches/${encodeURIComponent(batchNo)}/run`, { method: "POST" })
    .then((data) => normalizeReconciliationBatchResult(data, batchNo));
export const checkBatchReadiness = (batchNo) =>
  request(`/api/reconciliation/batches/${encodeURIComponent(batchNo)}/readiness-check`, {
    method: "POST"
  }).then((data) => normalizeReconciliationBatchResult(data, batchNo));
export const getCrossSystemChain = (businessChainId) =>
  request(`/api/reconciliation/cross-system/chains/${encodeURIComponent(businessChainId)}`)
    .then((data) => normalizeCrossSystemChainResult(data, businessChainId));
export const resolveDifference = (differenceNo, payload) =>
  request(`/api/reconciliation/differences/${encodeURIComponent(differenceNo)}/resolve`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
