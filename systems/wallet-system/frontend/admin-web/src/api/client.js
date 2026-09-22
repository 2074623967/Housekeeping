function createRequestError(code, message, cause) {
  const error = new Error(`${message} [${code}]`);
  error.code = code;
  error.cause = cause;
  return error;
}

function normalizeList(value) {
  if (Array.isArray(value)) {
    return value;
  }
  if (Array.isArray(value?.items)) {
    return value.items;
  }
  if (Array.isArray(value?.records)) {
    return value.records;
  }
  return [];
}

function normalizeDetail(value) {
  if (!value || typeof value !== "object") {
    return value;
  }
  return {
    ...value,
    ledgers: normalizeList(value.ledgers)
  };
}

function normalizeDashboard(value) {
  if (!value || typeof value !== "object") {
    return value;
  }
  return {
    ...value,
    redPackets: normalizeList(value.redPackets),
    riskEvents: normalizeList(value.riskEvents),
    outLedgers: normalizeList(value.outLedgers)
  };
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
      ? "WALLET-CLIENT-TIMEOUT"
      : "WALLET-CLIENT-NETWORK";
    throw createRequestError(code, error?.name === "AbortError" ? "钱包服务请求超时" : "无法连接钱包服务", error);
  }
  let payload;
  try {
    payload = await response.json();
  } catch (error) {
    throw createRequestError("WALLET-CLIENT-INVALID-RESPONSE", `钱包服务返回了无法解析的响应 [HTTP ${response.status}]`, error);
  }
  if (!response.ok || payload?.code !== "0") {
    throw createRequestError(payload?.code || `WALLET-CLIENT-HTTP-${response.status}`, payload?.message || "Request failed");
  }
  return payload.data;
}

export const walletApi = {
  getAccounts: async () => normalizeList(await request("/api/wallet/accounts")),
  getBalanceSnapshots: async () => normalizeList(await request("/api/wallet/accounts/balance-snapshots")),
  getLedgers: (params = {}) => {
    const query = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== "") {
        query.set(key, value);
      }
    });
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return request(`/api/wallet/accounts/ledgers${suffix}`).then(normalizeList);
  },
  buildLedgerExportUrl: (params = {}) => {
    const query = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== "") {
        query.set(key, value);
      }
    });
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return `/api/wallet/accounts/ledgers/export${suffix}`;
  },
  getDetail: (accountNo) => request(`/api/wallet/accounts/${accountNo}`).then(normalizeDetail),
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
  getRedPackets: async () => normalizeList(await request("/api/wallet/red-packets")),
  issueRedPacket: (payload) => request("/api/wallet/red-packets", {
    method: "POST",
    body: JSON.stringify(payload)
  }),
  getRiskEvents: async () => normalizeList(await request("/api/wallet/risk-events")),
  approveRiskEvent: (payload) => request("/api/wallet/risk-events/approve", {
    method: "POST",
    body: JSON.stringify(payload)
  }),
  getMarketingFundDashboard: () => request("/api/wallet/marketing-funds/dashboard").then(normalizeDashboard)
};
