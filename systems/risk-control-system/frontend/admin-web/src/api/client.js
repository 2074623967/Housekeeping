function createRequestError(code, message, cause) {
  const error = new Error(`${message} [${code}]`);
  error.code = code;
  error.cause = cause;
  return error;
}

function normalizeListPayload(data) {
  if (Array.isArray(data)) {
    return data;
  }
  if (Array.isArray(data?.items)) {
    return data.items;
  }
  if (Array.isArray(data?.records)) {
    return data.records;
  }
  return [];
}

async function request(url, options = {}) {
  let response;
  try {
    response = await fetch(url, {
      headers: { "Content-Type": "application/json" },
      ...options
    });
  } catch (error) {
    const code = error?.name === "AbortError"
      ? "RISK-CLIENT-TIMEOUT"
      : "RISK-CLIENT-NETWORK";
    throw createRequestError(code, error?.name === "AbortError" ? "风控服务请求超时" : "无法连接风控服务", error);
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
  getSummary: () => request("/api/risk-control/summary"),
  getPolicies: () => request("/api/risk-control/policies").then(normalizeListPayload),
  getLimitRules: () => request("/api/risk-control/limit-rules").then(normalizeListPayload),
  getBlocklists: () => request("/api/risk-control/blocklists").then(normalizeListPayload),
  getInterceptEvents: () => request("/api/risk-control/intercept-events").then(normalizeListPayload),
  getReviewOrders: () => request("/api/risk-control/review-orders").then(normalizeListPayload),
  downloadReviewOrders: () =>
    fetch("/api/risk-control/review-orders/export").then(async (response) => {
      if (!response.ok) {
        throw new Error("人工复核单快照导出失败");
      }
      return response.blob();
    }),
  getMonitorRules: () => request("/api/risk-control/monitor-rules").then(normalizeListPayload),
  togglePolicy: (configCode, enabled) => postJson("/api/risk-control/policies/toggle", { configCode, enabled }),
  toggleLimitRule: (configCode, enabled) => postJson("/api/risk-control/limit-rules/toggle", { configCode, enabled }),
  toggleBlocklist: (configCode, enabled) => postJson("/api/risk-control/blocklists/toggle", { configCode, enabled }),
  toggleMonitorRule: (configCode, enabled) => postJson("/api/risk-control/monitor-rules/toggle", { configCode, enabled }),
  reviewAction: (reviewNo, action, remark) => postJson("/api/risk-control/review-orders/action", { reviewNo, action, remark })
};
