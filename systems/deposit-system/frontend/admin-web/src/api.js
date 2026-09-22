const createRequestError = (code, message, cause) => {
  const error = new Error(`${message} [${code}]`);
  error.code = code;
  error.cause = cause;
  return error;
};

const request = async (url, options = {}) => {
  let response;
  try {
    response = await fetch(url, {
      headers: { "Content-Type": "application/json" },
      ...options
    });
  } catch (error) {
    const code = error?.name === "AbortError"
      ? "DEPOSIT-CLIENT-TIMEOUT"
      : "DEPOSIT-CLIENT-NETWORK";
    throw createRequestError(code, error?.name === "AbortError" ? "保证金服务请求超时" : "无法连接保证金服务", error);
  }
  let body;
  try {
    body = await response.json();
  } catch (error) {
    throw createRequestError("DEPOSIT-CLIENT-INVALID-RESPONSE", `保证金服务返回了无法解析的响应 [HTTP ${response.status}]`, error);
  }
  if (!response.ok || body?.code !== "0") {
    throw createRequestError(body?.code || `DEPOSIT-CLIENT-HTTP-${response.status}`, body?.message || "请求失败");
  }
  return body.data;
};

export const getAccounts = () => request("/api/deposits/accounts");
export const getOverview = () => request("/api/deposits/overview");
export const getV2Overview = () => request("/api/deposits/v2/overview");
export const getV2TotalAccounts = () => request("/api/deposits/v2/total-accounts");
export const getV2TotalAccountDetail = (totalAccountNo) =>
  request(`/api/deposits/v2/total-accounts/${encodeURIComponent(totalAccountNo)}`);
export const getV2Migrations = () => request("/api/deposits/v2/migrations");
export const getAccountDetail = (accountNo) =>
  request(`/api/deposits/accounts/${encodeURIComponent(accountNo)}`);
export const getAccountsByQuery = (query = {}) => {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      params.set(key, value);
    }
  });
  const suffix = params.toString() ? `?${params.toString()}` : "";
  return request(`/api/deposits/accounts${suffix}`);
};
export const buildAccountsExportUrl = (query = {}) => {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      params.set(key, value);
    }
  });
  const suffix = params.toString() ? `?${params.toString()}` : "";
  return `/api/deposits/accounts/export${suffix}`;
};
export const getFlows = (accountNo, query = {}) => {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      params.set(key, value);
    }
  });
  const suffix = params.toString() ? `?${params.toString()}` : "";
  return request(`/api/deposits/accounts/${encodeURIComponent(accountNo)}/flows${suffix}`);
};
export const buildFlowsExportUrl = (accountNo, query = {}) => {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      params.set(key, value);
    }
  });
  const suffix = params.toString() ? `?${params.toString()}` : "";
  return `/api/deposits/accounts/${encodeURIComponent(accountNo)}/flows/export${suffix}`;
};
export const openAccount = (payload) =>
  request("/api/deposits/accounts", { method: "POST", body: JSON.stringify(payload) });
export const postAction = (accountNo, action, payload) =>
  request(`/api/deposits/accounts/${encodeURIComponent(accountNo)}/${action}`, {
    method: "POST",
    body: JSON.stringify({ ...payload, accountNo })
  });
export const offsetDebt = (payload) =>
  request("/api/deposits/offset-debt", { method: "POST", body: JSON.stringify(payload) });
