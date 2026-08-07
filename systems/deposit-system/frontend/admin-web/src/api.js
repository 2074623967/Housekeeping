const request = async (url, options = {}) => {
  const response = await fetch(url, {
    headers: { "Content-Type": "application/json" },
    ...options
  });
  const body = await response.json();
  if (!response.ok || body.code !== "0") {
    throw new Error(body.message || "请求失败");
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
export const openAccount = (payload) =>
  request("/api/deposits/accounts", { method: "POST", body: JSON.stringify(payload) });
export const postAction = (accountNo, action, payload) =>
  request(`/api/deposits/accounts/${encodeURIComponent(accountNo)}/${action}`, {
    method: "POST",
    body: JSON.stringify({ ...payload, accountNo })
  });
export const offsetDebt = (payload) =>
  request("/api/deposits/offset-debt", { method: "POST", body: JSON.stringify(payload) });
