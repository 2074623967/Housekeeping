function createRequestError(code, message, cause) {
  const error = new Error(`${message} [${code}]`);
  error.code = code;
  error.cause = cause;
  return error;
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
      ? "GATEWAY-CLIENT-TIMEOUT"
      : "GATEWAY-CLIENT-NETWORK";
    throw createRequestError(code, error?.name === "AbortError" ? "网关接入服务请求超时" : "无法连接网关接入服务", error);
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
  getSummary: () => request("/api/gateway-access/summary"),
  getApplications: () => request("/api/gateway-access/applications"),
  getGateways: ({ keyword = "", channelType = "全部", status = "全部" } = {}) => {
    const params = new URLSearchParams({ keyword, channelType, status });
    return request(`/api/gateway-access/gateways?${params.toString()}`);
  },
  getCertificates: (riskLevel = "全部") => {
    const params = new URLSearchParams({ riskLevel });
    return request(`/api/gateway-access/certificates?${params.toString()}`);
  },
  getPermissions: () => request("/api/gateway-access/permissions"),
  getAuditLogs: ({ keyword = "", appCode = "全部", resultStatus = "全部" } = {}) => {
    const params = new URLSearchParams({ keyword, appCode, resultStatus });
    return request(`/api/gateway-access/audit-logs?${params.toString()}`);
  },
  downloadAuditLogs: ({ keyword = "", appCode = "全部", resultStatus = "全部" } = {}) => {
    const params = new URLSearchParams({ keyword, appCode, resultStatus });
    return fetch(`/api/gateway-access/audit-logs/export?${params.toString()}`).then(async (response) => {
      if (!response.ok) {
        throw new Error("审计快照导出失败");
      }
      return response.blob();
    });
  },
  getReleaseRoutes: ({ environment = "全部", status = "全部" } = {}) => {
    const params = new URLSearchParams({ environment, status });
    return request(`/api/gateway-access/release-routes?${params.toString()}`);
  },
  toggleApplication: (configCode, enabled) => postJson("/api/gateway-access/applications/toggle", { configCode, enabled }),
  toggleGateway: (configCode, enabled) => postJson("/api/gateway-access/gateways/toggle", { configCode, enabled }),
  toggleCertificate: (configCode, enabled) => postJson("/api/gateway-access/certificates/toggle", { configCode, enabled }),
  togglePermission: (configCode, enabled) => postJson("/api/gateway-access/permissions/toggle", { configCode, enabled }),
  toggleReleaseRoute: (configCode, enabled) => postJson("/api/gateway-access/release-routes/toggle", { configCode, enabled })
};
