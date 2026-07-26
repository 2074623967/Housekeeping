async function request(url, options = {}) {
  const response = await fetch(url, {
    headers: { "Content-Type": "application/json" },
    ...options
  });
  const payload = await response.json();
  if (!response.ok || payload.code !== "0") {
    throw new Error(payload.message || "Request failed");
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
  toggleApplication: (configCode, enabled) => postJson("/api/gateway-access/applications/toggle", { configCode, enabled }),
  toggleGateway: (configCode, enabled) => postJson("/api/gateway-access/gateways/toggle", { configCode, enabled }),
  toggleCertificate: (configCode, enabled) => postJson("/api/gateway-access/certificates/toggle", { configCode, enabled }),
  togglePermission: (configCode, enabled) => postJson("/api/gateway-access/permissions/toggle", { configCode, enabled })
};
