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
      ? "GATEWAY-CLIENT-TIMEOUT"
      : "GATEWAY-CLIENT-NETWORK";
    throw createRequestError(code, error?.name === "AbortError" ? "网关接入服务请求超时" : "无法连接网关接入服务", error);
  } finally {
    clearTimeout(timeoutId);
  }
  let payload;
  try {
    payload = await response.json();
  } catch (error) {
    throw createRequestError("GATEWAY-CLIENT-INVALID-RESPONSE", `网关接入服务返回了无法解析的响应 [HTTP ${response.status}]`, error);
  }
  if (!response.ok || payload?.code !== "0") {
    throw createRequestError(payload?.code || `GATEWAY-CLIENT-HTTP-${response.status}`, payload?.message || "Request failed");
  }
  return payload.data;
}

const postJson = (url, payload) => request(url, { method: "POST", body: JSON.stringify(payload) });

export const gatewayAccessApi = {
  getSummary: () => request("/api/gateway-access/summary").then(normalizeSummaryPayload),
  getApplications: () => request("/api/gateway-access/applications").then(normalizeListPayload),
  getGateways: ({ keyword = "", channelType = "全部", status = "全部" } = {}) => {
    const params = new URLSearchParams({ keyword, channelType, status });
    return request(`/api/gateway-access/gateways?${params.toString()}`).then(normalizeListPayload);
  },
  getCertificates: (riskLevel = "全部") => {
    const params = new URLSearchParams({ riskLevel });
    return request(`/api/gateway-access/certificates?${params.toString()}`).then(normalizeListPayload);
  },
  getPermissions: () => request("/api/gateway-access/permissions").then(normalizeListPayload),
  getAuditLogs: ({ keyword = "", appCode = "全部", resultStatus = "全部" } = {}) => {
    const params = new URLSearchParams({ keyword, appCode, resultStatus });
    return request(`/api/gateway-access/audit-logs?${params.toString()}`).then(normalizeListPayload);
  },
  downloadAuditLogs: ({ keyword = "", appCode = "全部", resultStatus = "全部" } = {}) => {
    const params = new URLSearchParams({ keyword, appCode, resultStatus });
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), CLIENT_TIMEOUT_MS);
    return fetch(`/api/gateway-access/audit-logs/export?${params.toString()}`, {
      signal: controller.signal
    }).then(async (response) => {
      if (!response.ok) {
        throw createRequestError(`GATEWAY-CLIENT-HTTP-${response.status}`, "审计快照导出失败");
      }
      return response.blob();
    }).catch((error) => {
      if (error?.code) throw error;
      const code = error?.name === "AbortError"
        ? "GATEWAY-CLIENT-TIMEOUT"
        : "GATEWAY-CLIENT-NETWORK";
      throw createRequestError(code, error?.name === "AbortError" ? "网关接入服务导出请求超时" : "无法连接网关接入服务", error);
    }).finally(() => {
      clearTimeout(timeoutId);
    });
  },
  getReleaseRoutes: ({ environment = "全部", status = "全部" } = {}) => {
    const params = new URLSearchParams({ environment, status });
    return request(`/api/gateway-access/release-routes?${params.toString()}`).then(normalizeListPayload);
  },
  toggleApplication: (configCode, enabled) => postJson("/api/gateway-access/applications/toggle", { configCode, enabled }),
  toggleGateway: (configCode, enabled) => postJson("/api/gateway-access/gateways/toggle", { configCode, enabled }),
  toggleCertificate: (configCode, enabled) => postJson("/api/gateway-access/certificates/toggle", { configCode, enabled }),
  togglePermission: (configCode, enabled) => postJson("/api/gateway-access/permissions/toggle", { configCode, enabled }),
  toggleReleaseRoute: (configCode, enabled) => postJson("/api/gateway-access/release-routes/toggle", { configCode, enabled })
};
