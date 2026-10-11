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
      ? "OPS-CONFIG-CLIENT-TIMEOUT"
      : "OPS-CONFIG-CLIENT-NETWORK";
    throw createRequestError(code, error?.name === "AbortError" ? "运营配置服务请求超时" : "无法连接运营配置服务", error);
  } finally {
    clearTimeout(timeoutId);
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
  getSummary: () => request("/api/ops-config/summary").then(normalizeSummaryPayload),
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
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), CLIENT_TIMEOUT_MS);
    return fetch(`/api/ops-config/effective-snapshot/export?${params.toString()}`, {
      signal: controller.signal
    }).then(async (response) => {
      if (!response.ok) {
        throw createRequestError(`OPS-CONFIG-CLIENT-HTTP-${response.status}`, "有效配置快照导出失败");
      }
      return response.blob();
    }).catch((error) => {
      if (error?.code) throw error;
      const code = error?.name === "AbortError"
        ? "OPS-CONFIG-CLIENT-TIMEOUT"
        : "OPS-CONFIG-CLIENT-NETWORK";
      throw createRequestError(code, error?.name === "AbortError" ? "运营配置服务导出请求超时" : "无法连接运营配置服务", error);
    }).finally(() => {
      clearTimeout(timeoutId);
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
