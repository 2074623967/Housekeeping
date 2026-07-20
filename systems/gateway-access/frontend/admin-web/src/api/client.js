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
  getGateways: () => request("/api/gateway-access/gateways"),
  getCertificates: () => request("/api/gateway-access/certificates"),
  getPermissions: () => request("/api/gateway-access/permissions"),
  toggleApplication: (configCode, enabled) => postJson("/api/gateway-access/applications/toggle", { configCode, enabled }),
  toggleGateway: (configCode, enabled) => postJson("/api/gateway-access/gateways/toggle", { configCode, enabled }),
  toggleCertificate: (configCode, enabled) => postJson("/api/gateway-access/certificates/toggle", { configCode, enabled }),
  togglePermission: (configCode, enabled) => postJson("/api/gateway-access/permissions/toggle", { configCode, enabled })
};
