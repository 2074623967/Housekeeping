function createRequestError(code, message, cause) {
  const error = new Error(`${message} [${code}]`);
  error.code = code;
  error.cause = cause;
  return error;
}

const CLIENT_TIMEOUT_MS = 10000;

function normalizeListPayload(data) {
  const records = Array.isArray(data)
    ? data
    : Array.isArray(data?.records)
      ? data.records
      : Array.isArray(data?.items)
        ? data.items
        : [];
  const totalValue = Number(data?.total);
  const pageNoValue = Number(data?.pageNo);
  const pageSizeValue = Number(data?.pageSize);
  const total = Number.isFinite(totalValue) && totalValue >= 0 ? totalValue : records.length;
  const pageNo = Number.isInteger(pageNoValue) && pageNoValue > 0 ? pageNoValue : 1;
  const pageSize = Number.isInteger(pageSizeValue) && pageSizeValue >= 0 ? pageSizeValue : records.length;
  return { records, total, pageNo, pageSize };
}

function normalizeSummaryPayload(data) {
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    return { metrics: [], highlights: [] };
  }
  return {
    ...data,
    metrics: Array.isArray(data.metrics) ? data.metrics : [],
    highlights: Array.isArray(data.highlights) ? data.highlights : []
  };
}

export function normalizeReviewActionResult(data, expectedReviewNo) {
  const normalized = normalizeListPayload(data);
  if (!normalized.records.some((item) => item && typeof item.reviewNo === "string"
      && item.reviewNo.trim() && (!expectedReviewNo || item.reviewNo === expectedReviewNo))) {
    throw createRequestError(
      "RISK-CLIENT-INVALID-BUSINESS-RESULT",
      "人工复核动作响应未确认当前复核单"
    );
  }
  return normalized;
}

async function request(url, options = {}) {
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
      ? "RISK-CLIENT-TIMEOUT"
      : "RISK-CLIENT-NETWORK";
    throw createRequestError(code, error?.name === "AbortError" ? "风控服务请求超时" : "无法连接风控服务", error);
  } finally {
    clearTimeout(timeoutId);
  }
  let payload;
  try {
    payload = await response.json();
  } catch (error) {
    throw createRequestError("RISK-CLIENT-INVALID-RESPONSE", `风控服务返回了无法解析的响应 [HTTP ${response.status}]`, error);
  }
  if (!response.ok || payload?.code !== "0") {
    throw createRequestError(payload?.code || `RISK-CLIENT-HTTP-${response.status}`, payload?.message || "请求失败");
  }
  return payload.data;
}

const postJson = (url, payload) => request(url, { method: "POST", body: JSON.stringify(payload) });

export const riskControlApi = {
  getSummary: () => request("/api/risk-control/summary").then(normalizeSummaryPayload),
  getPolicies: () => request("/api/risk-control/policies").then(normalizeListPayload),
  getLimitRules: () => request("/api/risk-control/limit-rules").then(normalizeListPayload),
  getBlocklists: () => request("/api/risk-control/blocklists").then(normalizeListPayload),
  getInterceptEvents: () => request("/api/risk-control/intercept-events").then(normalizeListPayload),
  getReviewOrders: () => request("/api/risk-control/review-orders").then(normalizeListPayload),
  downloadReviewOrders: () => {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), CLIENT_TIMEOUT_MS);
    return fetch("/api/risk-control/review-orders/export", {
      signal: controller.signal
    }).then(async (response) => {
      if (!response.ok) {
        throw createRequestError(`RISK-CLIENT-HTTP-${response.status}`, "人工复核单快照导出失败");
      }
      return response.blob();
    }).catch((error) => {
      if (error?.code) throw error;
      const code = error?.name === "AbortError"
        ? "RISK-CLIENT-TIMEOUT"
        : "RISK-CLIENT-NETWORK";
      throw createRequestError(code, error?.name === "AbortError" ? "风控服务导出请求超时" : "无法连接风控服务", error);
    }).finally(() => {
      clearTimeout(timeoutId);
    });
  },
  getMonitorRules: () => request("/api/risk-control/monitor-rules").then(normalizeListPayload),
  togglePolicy: (configCode, enabled) => postJson("/api/risk-control/policies/toggle", { configCode, enabled }),
  toggleLimitRule: (configCode, enabled) => postJson("/api/risk-control/limit-rules/toggle", { configCode, enabled }),
  toggleBlocklist: (configCode, enabled) => postJson("/api/risk-control/blocklists/toggle", { configCode, enabled }),
  toggleMonitorRule: (configCode, enabled) => postJson("/api/risk-control/monitor-rules/toggle", { configCode, enabled }),
  reviewAction: (reviewNo, action, remark) => postJson("/api/risk-control/review-orders/action", { reviewNo, action, remark })
    .then((data) => normalizeReviewActionResult(data, reviewNo))
};
