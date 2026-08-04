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

export const opsConfigApi = {
  getSummary: () => request("/api/ops-config/summary"),
  getAgreementTemplates: () => request("/api/ops-config/agreement-templates"),
  getBusinessLines: () => request("/api/ops-config/business-lines"),
  getPaymentTypes: () => request("/api/ops-config/payment-types"),
  getCashierTemplates: () => request("/api/ops-config/cashier-templates"),
  getChannelProfiles: () => request("/api/ops-config/channel-profiles"),
  getRoutingRules: () => request("/api/ops-config/routing-rules"),
  getSystemControls: () => request("/api/ops-config/system-controls"),
  toggleAgreementTemplate: (configCode, enabled) => postJson("/api/ops-config/agreement-templates/toggle", { configCode, enabled }),
  toggleBusinessLine: (configCode, enabled) => postJson("/api/ops-config/business-lines/toggle", { configCode, enabled }),
  togglePaymentType: (configCode, enabled) => postJson("/api/ops-config/payment-types/toggle", { configCode, enabled }),
  toggleCashierTemplate: (configCode, enabled) => postJson("/api/ops-config/cashier-templates/toggle", { configCode, enabled }),
  toggleChannelProfile: (configCode, enabled) => postJson("/api/ops-config/channel-profiles/toggle", { configCode, enabled }),
  toggleRoutingRule: (configCode, enabled) => postJson("/api/ops-config/routing-rules/toggle", { configCode, enabled }),
  toggleSystemControl: (configCode, enabled) => postJson("/api/ops-config/system-controls/toggle", { configCode, enabled })
};
