const createRequestError = (code, message, cause) => {
  const error = new Error(`${message} [${code}]`);
  error.code = code;
  error.cause = cause;
  return error;
};

const CLIENT_TIMEOUT_MS = 10000;

const request = async (url, options = {}) => {
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
      ? "DEPOSIT-CLIENT-TIMEOUT"
      : "DEPOSIT-CLIENT-NETWORK";
    throw createRequestError(code, error?.name === "AbortError" ? "保证金服务请求超时" : "无法连接保证金服务", error);
  } finally {
    clearTimeout(timeoutId);
  }
  let body;
  try {
    body = await response.json();
  } catch (error) {
    throw createRequestError("DEPOSIT-CLIENT-INVALID-RESPONSE", `保证金服务返回了无法解析的响应 [HTTP ${response.status}]`, error);
  }
  if (!response.ok || body?.code !== "0") {
    throw createRequestError(body?.code || `DEPOSIT-CLIENT-HTTP-${response.status}`, body?.message || "请求失败");
  }
  return body.data;
};

const normalizeList = (value) => (Array.isArray(value) ? value : []);

const normalizeTotalAccountDetail = (value) => {
  if (!value || typeof value !== "object") return value;
  return {
    ...value,
    bindings: normalizeList(value.bindings),
    migrations: normalizeList(value.migrations)
  };
};

const normalizePositiveInteger = (value, fallback) => {
  const number = Number(value);
  return Number.isInteger(number) && number > 0 ? number : fallback;
};

const normalizeNonNegativeInteger = (value, fallback) => {
  const number = Number(value);
  return Number.isInteger(number) && number >= 0 ? number : fallback;
};

const normalizePage = (page, pageNo = 1, pageSize = 20) => {
  const normalizedPageNo = normalizePositiveInteger(pageNo, 1);
  const normalizedPageSize = normalizePositiveInteger(pageSize, 20);
  if (Array.isArray(page)) {
    return {
      items: page,
      total: page.length,
      pageNo: normalizedPageNo,
      pageSize: normalizedPageSize
    };
  }
  if (!page || typeof page !== "object") {
    return { items: [], total: 0, pageNo: normalizedPageNo, pageSize: normalizedPageSize };
  }
  const items = normalizeList(page.items);
  return {
    ...page,
    items,
    total: normalizeNonNegativeInteger(page.total, items.length),
    pageNo: normalizePositiveInteger(page.pageNo, normalizedPageNo),
    pageSize: normalizePositiveInteger(page.pageSize, normalizedPageSize)
  };
};

export const getAccounts = () => request("/api/deposits/accounts");
export const getOverview = () => request("/api/deposits/overview");
export const getV2Overview = () => request("/api/deposits/v2/overview");
export const getV2TotalAccounts = () => request("/api/deposits/v2/total-accounts").then(normalizeList);
export const getV2TotalAccountDetail = (totalAccountNo) =>
  request(`/api/deposits/v2/total-accounts/${encodeURIComponent(totalAccountNo)}`).then(normalizeTotalAccountDetail);
export const getV2Migrations = () => request("/api/deposits/v2/migrations").then(normalizeList);
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
  return request(`/api/deposits/accounts${suffix}`).then((page) =>
    normalizePage(page, query.pageNo, query.pageSize)
  );
};
export const buildAccountsExportUrl = (query = {}) => {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      params.set(key, value);
    }
  });
  const suffix = params.toString() ? `?${params.toString()}` : "";
  return `/api/deposits/accounts/export${suffix}`;
};
export const getFlows = (accountNo, query = {}) => {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      params.set(key, value);
    }
  });
  const suffix = params.toString() ? `?${params.toString()}` : "";
  return request(`/api/deposits/accounts/${encodeURIComponent(accountNo)}/flows${suffix}`).then((page) =>
    normalizePage(page, query.pageNo, query.pageSize)
  );
};
export const buildFlowsExportUrl = (accountNo, query = {}) => {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      params.set(key, value);
    }
  });
  const suffix = params.toString() ? `?${params.toString()}` : "";
  return `/api/deposits/accounts/${encodeURIComponent(accountNo)}/flows/export${suffix}`;
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
