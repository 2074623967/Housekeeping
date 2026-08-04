async function request(url, options = {}) {
  const response = await fetch(url, {
    headers: { "Content-Type": "application/json" },
    ...options
  });
  const payload = await response.json();
  if (!response.ok || payload.code !== "0") {
    throw new Error(payload.message || "请求失败");
  }
  return payload.data;
}

const postJson = (url, payload) => request(url, { method: "POST", body: JSON.stringify(payload) });

export const riskControlApi = {
  getSummary: () => request("/api/risk-control/summary"),
  getPolicies: () => request("/api/risk-control/policies"),
  getLimitRules: () => request("/api/risk-control/limit-rules"),
  getBlocklists: () => request("/api/risk-control/blocklists"),
  getInterceptEvents: () => request("/api/risk-control/intercept-events"),
  getReviewOrders: () => request("/api/risk-control/review-orders"),
  getMonitorRules: () => request("/api/risk-control/monitor-rules"),
  togglePolicy: (configCode, enabled) => postJson("/api/risk-control/policies/toggle", { configCode, enabled }),
  toggleLimitRule: (configCode, enabled) => postJson("/api/risk-control/limit-rules/toggle", { configCode, enabled }),
  toggleBlocklist: (configCode, enabled) => postJson("/api/risk-control/blocklists/toggle", { configCode, enabled }),
  toggleMonitorRule: (configCode, enabled) => postJson("/api/risk-control/monitor-rules/toggle", { configCode, enabled }),
  reviewAction: (reviewNo, action, remark) => postJson("/api/risk-control/review-orders/action", { reviewNo, action, remark })
};

