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

export const walletApi = {
  getAccounts: () => request("/api/wallet/accounts"),
  getDetail: (accountNo) => request(`/api/wallet/accounts/${accountNo}`),
  recharge: (payload) => request("/api/wallet/recharges", {
    method: "POST",
    body: JSON.stringify(payload)
  }),
  withdraw: (payload) => request("/api/wallet/withdrawals", {
    method: "POST",
    body: JSON.stringify(payload)
  }),
  transfer: (payload) => request("/api/wallet/transfers", {
    method: "POST",
    body: JSON.stringify(payload)
  })
};
