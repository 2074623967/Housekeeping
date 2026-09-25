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

function normalizePagePayload(data, defaultPageSize = 20) {
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    return { records: [], total: 0, pageNo: 1, pageSize: defaultPageSize };
  }
  const records = Array.isArray(data.records) ? data.records : [];
  const totalValue = Number(data.total);
  const pageNoValue = Number(data.pageNo);
  const pageSizeValue = Number(data.pageSize);
  return {
    ...data,
    records,
    total: Number.isInteger(totalValue) && totalValue >= 0 ? totalValue : records.length,
    pageNo: Number.isInteger(pageNoValue) && pageNoValue > 0 ? pageNoValue : 1,
    pageSize: Number.isInteger(pageSizeValue) && pageSizeValue > 0 ? pageSizeValue : defaultPageSize
  };
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
  if (params.accountType) {
    query.set("accountType", params.accountType);
  }
  if (params.accountStatus) {
    query.set("accountStatus", params.accountStatus);
  }
  if (params.extRefNo) {
    query.set("extRefNo", params.extRefNo);
  }
  if (params.bizLineCode) {
    query.set("bizLineCode", params.bizLineCode);
  }
  if (params.tenantCode) {
    query.set("tenantCode", params.tenantCode);
  }
  if (params.openedStartTime) {
    query.set("openedStartTime", params.openedStartTime);
  }
  if (params.openedEndTime) {
    query.set("openedEndTime", params.openedEndTime);
  }
  query.set("pageNo", String(params.pageNo || 1));
  query.set("pageSize", String(params.pageSize || 20));
  return normalizePagePayload(await request(`/api/wallet/accounts?${query.toString()}`));
}

export async function fetchWalletAccountDetail(walletAccountNo) {
  const detail = await request(`/api/wallet/accounts/${encodeURIComponent(walletAccountNo)}`);
  if (!detail || typeof detail !== "object") {
    return detail;
  }
  return {
    ...detail,
    recentFlows: Array.isArray(detail.recentFlows) ? detail.recentFlows : [],
    statusLogs: Array.isArray(detail.statusLogs) ? detail.statusLogs : []
  };
}

export async function fetchWalletBalances(walletAccountNos) {
  const balances = await post("/api/wallet/accounts/balances/query", walletAccountNos);
  return Array.isArray(balances) ? balances : [];
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
  return normalizePagePayload(await request(`/api/wallet/flows?${query.toString()}`));
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
  return normalizePagePayload(await request(`/api/wallet/flows/export-tasks?${query.toString()}`), 10);
}

export function buildWalletFlowExportDownloadUrl(exportTaskNo, operatorRole = "FINANCE") {
  return `${API_BASE_URL}/api/wallet/flows/export-tasks/${encodeURIComponent(exportTaskNo)}/download?operatorRole=${encodeURIComponent(operatorRole)}`;
}
