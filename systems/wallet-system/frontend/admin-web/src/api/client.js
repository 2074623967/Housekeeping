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

export function normalizeWalletActionResult(data, key, expectedValue = "", message) {
  if (!data || typeof data !== "object" || typeof data[key] !== "string"
      || !data[key].trim() || (expectedValue && data[key] !== expectedValue)) {
    throw createRequestError(
      "WALLET-CLIENT-INVALID-BUSINESS-RESULT",
      message
    );
  }
  return data;
}

function normalizeWalletDetailResult(data, expectedAccountNo) {
  const account = data?.account && typeof data.account === "object" ? data.account : data;
  if (!account || typeof account !== "object" || account.accountNo !== expectedAccountNo) {
    throw createRequestError(
      "WALLET-CLIENT-INVALID-BUSINESS-RESULT",
      "钱包账户详情响应未确认当前账户"
    );
  }
  return normalizeDetail(data);
}

const CLIENT_TIMEOUT_MS = 10000;

async function request(url, options = {}) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), CLIENT_TIMEOUT_MS);
  let response;
  try {
    response = await fetch(url, {
      headers: { "Content-Type": "application/json" },
      ...options,
      signal: controller.signal
    });
  } catch (error) {
    const code = error?.name === "AbortError"
      ? "WALLET-CLIENT-TIMEOUT"
      : "WALLET-CLIENT-NETWORK";
    throw createRequestError(code, error?.name === "AbortError" ? "钱包服务请求超时" : "无法连接钱包服务", error);
  } finally {
    clearTimeout(timeoutId);
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
  getDetail: (accountNo) => request(`/api/wallet/accounts/${encodeURIComponent(accountNo)}`)
    .then((data) => normalizeWalletDetailResult(data, accountNo)),
  balancePayment: (payload) => request("/api/wallet/accounts/balance-payments", {
    method: "POST",
    body: JSON.stringify(payload)
  }).then((data) => normalizeWalletActionResult(
    data, "balancePaymentNo", "", "余额支付响应未确认业务单号"
  )),
  recharge: (payload) => request("/api/wallet/accounts/recharges", {
    method: "POST",
    body: JSON.stringify(payload)
  }).then((data) => normalizeWalletActionResult(data, "rechargeNo", "", "充值响应未确认业务单号")),
  withdraw: (payload) => request("/api/wallet/accounts/withdrawals", {
    method: "POST",
    body: JSON.stringify(payload)
  }).then((data) => normalizeWalletActionResult(data, "withdrawNo", "", "提现响应未确认业务单号")),
  transfer: (payload) => request("/api/wallet/accounts/transfers", {
    method: "POST",
    body: JSON.stringify(payload)
  }).then((data) => normalizeWalletActionResult(data, "transferNo", "", "转账响应未确认业务单号")),
  getRedPackets: async () => normalizeList(await request("/api/wallet/red-packets")),
  issueRedPacket: (payload) => request("/api/wallet/red-packets", {
    method: "POST",
    body: JSON.stringify(payload)
  }).then((data) => normalizeWalletActionResult(data, "redPacketNo", "", "红包响应未确认业务单号")),
  getRiskEvents: async () => normalizeList(await request("/api/wallet/risk-events")),
  approveRiskEvent: (payload) => request("/api/wallet/risk-events/approve", {
    method: "POST",
    body: JSON.stringify(payload)
  }).then((data) => normalizeWalletActionResult(data, "eventNo", payload?.eventNo, "风控事件响应未确认当前事件")),
  getMarketingFundDashboard: () => request("/api/wallet/marketing-funds/dashboard").then(normalizeDashboard)
};
