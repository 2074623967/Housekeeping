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
  getLedgers: (params = {}) => {
    const query = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== "") {
        query.set(key, value);
      }
    });
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return request(`/api/wallet/accounts/ledgers${suffix}`);
  },
  getDetail: (accountNo) => request(`/api/wallet/accounts/${accountNo}`),
  balancePayment: (payload) => request("/api/wallet/accounts/balance-payments", {
    method: "POST",
    body: JSON.stringify(payload)
  }),
  recharge: (payload) => request("/api/wallet/accounts/recharges", {
    method: "POST",
    body: JSON.stringify(payload)
  }),
  withdraw: (payload) => request("/api/wallet/accounts/withdrawals", {
    method: "POST",
    body: JSON.stringify(payload)
  }),
  transfer: (payload) => request("/api/wallet/accounts/transfers", {
    method: "POST",
    body: JSON.stringify(payload)
  }),
  getRedPackets: () => request("/api/wallet/red-packets"),
  issueRedPacket: (payload) => request("/api/wallet/red-packets", {
    method: "POST",
    body: JSON.stringify(payload)
  }),
  getRiskEvents: () => request("/api/wallet/risk-events"),
  approveRiskEvent: (payload) => request("/api/wallet/risk-events/approve", {
    method: "POST",
    body: JSON.stringify(payload)
  })
};
