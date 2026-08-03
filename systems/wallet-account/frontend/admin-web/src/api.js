const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://127.0.0.1:8095";

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {})
    },
    ...options
  });
  const payload = await response.json();
  if (!response.ok || payload.code !== "0") {
    throw new Error(payload.message || "请求失败");
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
  return request(`/api/wallet/accounts/${walletAccountNo}`);
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
