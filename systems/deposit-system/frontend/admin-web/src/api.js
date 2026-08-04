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
export const getFlows = (accountNo) =>
  request(`/api/deposits/accounts/${encodeURIComponent(accountNo)}/flows`);
export const openAccount = (payload) =>
  request("/api/deposits/accounts", { method: "POST", body: JSON.stringify(payload) });
export const postAction = (accountNo, action, payload) =>
  request(`/api/deposits/accounts/${encodeURIComponent(accountNo)}/${action}`, {
    method: "POST",
    body: JSON.stringify({ ...payload, accountNo })
  });
export const offsetDebt = (payload) =>
  request("/api/deposits/offset-debt", { method: "POST", body: JSON.stringify(payload) });
