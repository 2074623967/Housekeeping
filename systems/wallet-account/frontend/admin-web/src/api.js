const API_BASE_URL = import.meta.env?.VITE_API_BASE_URL || "";

async function request(path, options = {}) {
  let response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      headers: {
        "Content-Type": "application/json",
        ...(options.headers || {})
      },
      ...options
    });
  } catch (error) {
    const code = error?.name === "AbortError"
      ? "WALLET-ACCOUNT-CLIENT-TIMEOUT"
      : "WALLET-ACCOUNT-CLIENT-NETWORK";
    const message = error?.name === "AbortError" ? "钱包账户服务请求超时" : "无法连接钱包账户服务";
    const transportError = new Error(`${message} [${code}]`);
    transportError.code = code;
    transportError.cause = error;
    throw transportError;
  }
  let payload;
  try {
    payload = await response.json();
  } catch (error) {
    const parseError = new Error(`钱包账户服务返回了无法解析的响应 [HTTP ${response.status}] [WALLET-ACCOUNT-CLIENT-INVALID-RESPONSE]`);
    parseError.code = "WALLET-ACCOUNT-CLIENT-INVALID-RESPONSE";
    parseError.cause = error;
    throw parseError;
  }
  if (!response.ok || payload?.code !== "0") {
    const code = payload?.code || `WALLET-ACCOUNT-CLIENT-HTTP-${response.status}`;
    const businessError = new Error(`${payload?.message || "请求失败"} [${code}]`);
    businessError.code = code;
    throw businessError;
  }
  return payload.data;
}

async function post(path, body) {
  return request(path, {
    method: "POST",
    body: JSON.stringify(body)
  });
}

export async function fetchWalletAccounts(params) {
  const query = new URLSearchParams();
  if (params.keyword) {
    query.set("keyword", params.keyword);
  }
  if (params.ownerType) {
    query.set("ownerType", params.ownerType);
  }
  if (params.accountStatus) {
    query.set("accountStatus", params.accountStatus);
  }
  query.set("pageNo", String(params.pageNo || 1));
  query.set("pageSize", String(params.pageSize || 20));
  return request(`/api/wallet/accounts?${query.toString()}`);
}

export async function fetchWalletAccountDetail(walletAccountNo) {
  return request(`/api/wallet/accounts/${encodeURIComponent(walletAccountNo)}`);
}

export async function fetchWalletBalances(walletAccountNos) {
  return post("/api/wallet/accounts/balances/query", walletAccountNos);
}

export async function fetchWalletFlows(params) {
  const query = new URLSearchParams();
  if (params.walletAccountNo) {
    query.set("walletAccountNo", params.walletAccountNo);
  }
  if (params.sourceSystem) {
    query.set("sourceSystem", params.sourceSystem);
  }
  if (params.sourceBizNo) {
    query.set("sourceBizNo", params.sourceBizNo);
  }
  query.set("pageNo", String(params.pageNo || 1));
  query.set("pageSize", String(params.pageSize || 20));
  return request(`/api/wallet/flows?${query.toString()}`);
}

export async function openWalletAccount(payload) {
  return post("/api/wallet/accounts", payload);
}

export async function changeWalletAccountStatus(walletAccountNo, payload) {
  return post(`/api/wallet/accounts/${encodeURIComponent(walletAccountNo)}/status-change`, payload);
}

export async function exportWalletFlows(payload) {
  return post("/api/wallet/flows/export", payload);
}

export async function fetchWalletFlowExportTasks(params) {
  const query = new URLSearchParams();
  if (params.operatorId) {
    query.set("operatorId", params.operatorId);
  }
  if (params.taskStatus) {
    query.set("taskStatus", params.taskStatus);
  }
  query.set("operatorRole", params.operatorRole || "FINANCE");
  query.set("pageNo", String(params.pageNo || 1));
  query.set("pageSize", String(params.pageSize || 10));
  return request(`/api/wallet/flows/export-tasks?${query.toString()}`);
}

export function buildWalletFlowExportDownloadUrl(exportTaskNo, operatorRole = "FINANCE") {
  return `${API_BASE_URL}/api/wallet/flows/export-tasks/${encodeURIComponent(exportTaskNo)}/download?operatorRole=${encodeURIComponent(operatorRole)}`;
}
