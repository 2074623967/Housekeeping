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
      ? "OPS-CONFIG-CLIENT-TIMEOUT"
      : "OPS-CONFIG-CLIENT-NETWORK";
    throw createRequestError(code, error?.name === "AbortError" ? "运营配置服务请求超时" : "无法连接运营配置服务", error);
  }
  let payload;
  try {
    payload = await response.json();
  } catch (error) {
    throw createRequestError("OPS-CONFIG-CLIENT-INVALID-RESPONSE", `运营配置服务返回了无法解析的响应 [HTTP ${response.status}]`, error);
  }
  if (!response.ok || payload?.code !== "0") {
    throw createRequestError(payload?.code || `OPS-CONFIG-CLIENT-HTTP-${response.status}`, payload?.message || "请求失败");
  }
  return payload.data;
}

const postJson = (url, payload) => request(url, { method: "POST", body: JSON.stringify(payload) });

export const opsConfigApi = {
  getSummary: () => request("/api/ops-config/summary"),
  getAgreementTemplates: () => request("/api/ops-config/agreement-templates").then(normalizeListPayload),
  getBusinessLines: () => request("/api/ops-config/business-lines").then(normalizeListPayload),
  getPaymentTypes: () => request("/api/ops-config/payment-types").then(normalizeListPayload),
  getCashierTemplates: () => request("/api/ops-config/cashier-templates").then(normalizeListPayload),
  getChannelProfiles: () => request("/api/ops-config/channel-profiles").then(normalizeListPayload),
  getRoutingRules: () => request("/api/ops-config/routing-rules").then(normalizeListPayload),
  getSystemControls: () => request("/api/ops-config/system-controls").then(normalizeListPayload),
  getConfigChanges: () => request("/api/ops-config/changes").then(normalizeListPayload),
  getRefreshEvents: () => request("/api/ops-config/refresh-events").then(normalizeListPayload),
  getEffectiveSnapshot: ({ businessCode = "", payType = "", terminalType = "" } = {}) => {
    const params = new URLSearchParams({ businessCode, payType, terminalType });
    return request(`/api/ops-config/effective-snapshot?${params.toString()}`);
  },
  downloadEffectiveSnapshot: ({ businessCode = "", payType = "", terminalType = "" } = {}) => {
    const params = new URLSearchParams({ businessCode, payType, terminalType });
    return fetch(`/api/ops-config/effective-snapshot/export?${params.toString()}`).then(async (response) => {
      if (!response.ok) {
        throw new Error("有效配置快照导出失败");
      }
      return response.blob();
    });
  },
  toggleAgreementTemplate: (configCode, enabled) => postJson("/api/ops-config/agreement-templates/toggle", { configCode, enabled }),
  toggleBusinessLine: (configCode, enabled) => postJson("/api/ops-config/business-lines/toggle", { configCode, enabled }),
  togglePaymentType: (configCode, enabled) => postJson("/api/ops-config/payment-types/toggle", { configCode, enabled }),
  toggleCashierTemplate: (configCode, enabled) => postJson("/api/ops-config/cashier-templates/toggle", { configCode, enabled }),
  toggleChannelProfile: (configCode, enabled) => postJson("/api/ops-config/channel-profiles/toggle", { configCode, enabled }),
  toggleRoutingRule: (configCode, enabled) => postJson("/api/ops-config/routing-rules/toggle", { configCode, enabled }),
  toggleSystemControl: (configCode, enabled) => postJson("/api/ops-config/system-controls/toggle", { configCode, enabled }),
  createConfigChange: (payload) => postJson("/api/ops-config/changes", payload),
  approveConfigChange: (payload) => postJson("/api/ops-config/changes/approve", payload),
  publishConfigChange: (payload) => postJson("/api/ops-config/changes/publish", payload),
  acknowledgeRefreshEvent: (payload) => postJson("/api/ops-config/refresh-events/ack", payload)
};
