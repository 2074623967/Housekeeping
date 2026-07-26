async function request(url) {
  const response = await fetch(url, { headers: { "Content-Type": "application/json" } });
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
  })
};
