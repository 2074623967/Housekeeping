function createBusinessError(payload, fallbackMessage) {
  const message = payload?.message || fallbackMessage || "Request failed";
  const displayMessage = `${message} [${payload?.code || "ACCOUNTING-CLIENT"}${payload?.requestId ? ` / ${payload.requestId}` : ""}]`;
  const error = new Error(displayMessage);
  error.code = payload?.code || "ACCOUNTING-CLIENT";
  error.requestId = payload?.requestId || "";
  error.rawMessage = message;
  return error;
}

const CLIENT_TIMEOUT_MS = 10000;

async function parseResponsePayload(response) {
  try {
    return await response.json();
  } catch (error) {
    const parseError = new Error("账务服务返回了无法解析的响应");
    parseError.code = "ACCOUNTING-CLIENT-INVALID-RESPONSE";
    parseError.status = response.status;
    parseError.cause = error;
    throw parseError;
  }
}

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
      ? "ACCOUNTING-CLIENT-TIMEOUT"
      : "ACCOUNTING-CLIENT-NETWORK";
    throw createBusinessError({ code }, error?.name === "AbortError" ? "账务服务请求超时" : "无法连接账务服务");
  } finally {
    clearTimeout(timeoutId);
  }
  let payload;
  try {
    payload = await parseResponsePayload(response);
  } catch (error) {
    throw createBusinessError({ code: error.code }, `${error.message} [HTTP ${error.status}]`);
  }
  if (!response.ok) {
    throw createBusinessError(payload, `Request failed: ${response.status}`);
  }
  if (!payload || payload.code !== "0") {
    throw createBusinessError(payload, "Request failed");
  }
  return payload.data;
}

export function normalizePagePayload(data) {
  if (Array.isArray(data)) {
    return { items: data, total: data.length, pageNo: 1, pageSize: 20 };
  }
  if (!data || typeof data !== "object") {
    return { items: [], total: 0, pageNo: 1, pageSize: 20 };
  }
  if (!Object.prototype.hasOwnProperty.call(data, "items")) {
    return data;
  }
  const items = Array.isArray(data.items) ? data.items : [];
  const total = Number(data.total);
  const pageNo = Number(data.pageNo);
  const pageSize = Number(data.pageSize);
  return {
    ...data,
    items,
    total: Number.isInteger(total) && total >= 0 ? total : items.length,
    pageNo: Number.isInteger(pageNo) && pageNo > 0 ? pageNo : 1,
    pageSize: Number.isInteger(pageSize) && pageSize > 0 ? pageSize : 20
  };
}

function requestPage(url, options = {}) {
  return request(url, options).then(normalizePagePayload);
}

function normalizeAccountingEventPage(page) {
  const normalized = normalizePagePayload(page);
  if (normalized.items.some((item) => !isValidAccountingEvent(item))) {
    throw invalidAccountingResult("账务事件列表响应未确认消费事实");
  }
  return normalized;
}

function normalizeLedgerPage(page) {
  const normalized = normalizePagePayload(page);
  if (normalized.items.some((item) => !isValidLedgerItem(item))) {
    throw invalidAccountingResult("账务流水响应缺少可核对的金额方向事实");
  }
  return normalized;
}

function isValidAccountingEvent(item) {
  if (!item || typeof item !== "object"
      || typeof item.businessChainId !== "string" || !/^CHAIN-[^\s]+$/.test(item.businessChainId.trim())
      || typeof item.eventNo !== "string" || !item.eventNo.trim()
      || typeof item.eventType !== "string" || !item.eventType.trim()
      || typeof item.bizNo !== "string" || !item.bizNo.trim()
      || typeof item.eventStatus !== "string" || !item.eventStatus.trim()
      || typeof item.statusType !== "string"
      || !["success", "info"].includes(item.statusType)
      || (item.eventStatus === "已消费" ? item.statusType !== "success" : item.statusType !== "info")
      || typeof item.createdAt !== "string" || !item.createdAt.trim()
      || Number.isNaN(Date.parse(item.createdAt))) {
    return false;
  }
  return true;
}

function isValidLedgerItem(item) {
  if (!item || typeof item !== "object"
      || typeof item.ledgerNo !== "string" || !item.ledgerNo.trim()
      || typeof item.accountNo !== "string" || !item.accountNo.trim()
      || typeof item.bizType !== "string" || !item.bizType.trim()
      || typeof item.bizNo !== "string" || !item.bizNo.trim()
      || (item.payScene != null && typeof item.payScene !== "string")
      || !["借方", "贷方"].includes(item.direction)
      || !isValidLedgerAmount(item.amount, false)
      || !isValidLedgerAmount(item.beforeBalance, true)
      || !isValidLedgerAmount(item.afterBalance, true)
      || typeof item.ledgerStatus !== "string" || !item.ledgerStatus.trim()
      || typeof item.statusType !== "string" || !item.statusType.trim()) {
    return false;
  }
  const amount = toLedgerMinorUnits(item.amount);
  const before = toLedgerMinorUnits(item.beforeBalance);
  const after = toLedgerMinorUnits(item.afterBalance);
  return item.direction === "贷方" ? after === before + amount : after === before - amount;
}

function isValidLedgerAmount(value, allowZero) {
  if (typeof value !== "string" && typeof value !== "number") {
    return false;
  }
  const normalized = String(value).trim().replace(/^¥\s*/, "");
  if (!/^(?:0|[1-9]\d{0,15})(?:\.\d{1,2})?$/.test(normalized)) {
    return false;
  }
  return allowZero || Number(normalized) > 0;
}

function toLedgerMinorUnits(value) {
  const [integerPart, fractionPart = ""] = String(value).trim()
    .replace(/^¥\s*/, "")
    .split(".");
  return BigInt(integerPart) * 100n + BigInt(fractionPart.padEnd(2, "0"));
}

function normalizeAccountDetail(data) {
  if (!data || typeof data !== "object") {
    return data;
  }
  return {
    ...data,
    recentLedgers: Array.isArray(data.recentLedgers) ? data.recentLedgers : []
  };
}

function invalidAccountingResult(message) {
  return createBusinessError(
    { code: "ACCOUNTING-CLIENT-INVALID-BUSINESS-RESULT" },
    message
  );
}

export function normalizeAccountingResult(data, field, expectedValue = "", message = "账务响应未确认当前业务实体") {
  if (!data || typeof data !== "object" || typeof data[field] !== "string"
      || !data[field].trim() || (expectedValue && data[field] !== expectedValue)) {
    throw invalidAccountingResult(message);
  }
  return data;
}

export function normalizeAccountingAccountDetailResult(data, expectedAccountNo) {
  if (!data || typeof data !== "object" || !data.account || typeof data.account !== "object") {
    throw invalidAccountingResult("账户详情响应未确认当前账户");
  }
  normalizeAccountingResult(data.account, "accountNo", expectedAccountNo, "账户详情响应未确认当前账户");
  return normalizeAccountDetail(data);
}

export function normalizeAccountingEventResult(data, expectedEventType = "", expectedBizNo = "") {
  if (!isValidAccountingEvent(data)
      || (expectedEventType && data.eventType !== expectedEventType)
      || (expectedBizNo && data.bizNo !== expectedBizNo)) {
    throw invalidAccountingResult("账务事件响应未确认消费事实");
  }
  return data;
}

function postJson(url, payload) {
  return request(url, {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export const subjectApi = {
  getList: ({ keyword = "", subjectType = "", status = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({
      keyword,
      subjectType,
      status,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return requestPage(`/api/accounting/subjects?${params.toString()}`);
  },
  create: (payload) => postJson("/api/accounting/subjects", payload)
    .then((data) => normalizeAccountingResult(data, "subjectId", "", "账户主体响应未确认主体")),
  getDetail: (subjectId) => request(`/api/accounting/subjects/${subjectId}`)
    .then((data) => normalizeAccountingResult(data, "subjectId", subjectId, "账户主体详情未确认主体"))
};

export const accountApi = {
  getList: ({ subjectId = "", accountType = "", status = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({
      subjectId,
      accountType,
      status,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return requestPage(`/api/accounting/accounts?${params.toString()}`);
  },
  open: (payload) => postJson("/api/accounting/accounts/open", payload)
    .then((data) => normalizeAccountingResult(data, "accountNo", "", "开户响应未确认当前账户")),
  getDetail: (accountNo) => request(`/api/accounting/accounts/${accountNo}`)
    .then((data) => normalizeAccountingAccountDetailResult(data, accountNo)),
  downloadDetail: async (accountNo) => {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), CLIENT_TIMEOUT_MS);
    try {
      let response;
      try {
        response = await fetch(`/api/accounting/accounts/${encodeURIComponent(accountNo)}/export`, {
          signal: controller.signal
        });
      } catch (error) {
        const code = error?.name === "AbortError"
          ? "ACCOUNTING-CLIENT-TIMEOUT"
          : "ACCOUNTING-CLIENT-NETWORK";
        throw createBusinessError({ code }, error?.name === "AbortError" ? "账务服务导出请求超时" : "无法连接账务服务");
      }
      if (!response.ok) {
        throw createBusinessError({ code: `ACCOUNTING-CLIENT-HTTP-${response.status}` }, "账户详情导出失败");
      }
      return response.blob();
    } finally {
      clearTimeout(timeoutId);
    }
  }
};

export const balanceApi = {
  getDetail: (accountNo) => request(`/api/accounting/balances/${accountNo}`)
    .then((data) => normalizeAccountingResult(data, "accountNo", accountNo, "余额详情未确认当前账户")),
  credit: (payload) => postJson("/api/accounting/balances/credit", payload)
    .then((data) => normalizeAccountingResult(data, "accountNo", payload?.accountNo, "余额入账响应未确认当前账户")),
  freeze: (payload) => postJson("/api/accounting/balances/freeze", payload)
    .then((data) => normalizeAccountingResult(data, "accountNo", payload?.accountNo, "余额冻结响应未确认当前账户")),
  unfreeze: (payload) => postJson("/api/accounting/balances/unfreeze", payload)
    .then((data) => normalizeAccountingResult(data, "accountNo", payload?.accountNo, "余额解冻响应未确认当前账户"))
};

export const ledgerApi = {
  getList: ({ accountNo = "", bizNo = "", bizType = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({
      accountNo,
      bizNo,
      bizType,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/accounting/ledgers?${params.toString()}`).then(normalizeLedgerPage);
  },
  buildExportUrl: ({ accountNo = "", bizNo = "", bizType = "" } = {}) => {
    const params = new URLSearchParams({
      accountNo,
      bizNo,
      bizType
    });
    return `/api/accounting/ledgers/export?${params.toString()}`;
  }
};

export const freezeApi = {
  getList: ({ accountNo = "", freezeStatus = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({
      accountNo,
      freezeStatus,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return requestPage(`/api/accounting/freezes?${params.toString()}`);
  },
  create: (payload) => postJson("/api/accounting/freezes", payload)
    .then((data) => normalizeAccountingResult(data, "freezeNo", "", "冻结单响应未确认冻结单")),
  unfreeze: (freezeNo, payload) => postJson(`/api/accounting/freezes/${freezeNo}/unfreeze`, payload)
    .then((data) => normalizeAccountingResult(data, "freezeNo", freezeNo, "解冻响应未确认当前冻结单"))
};

export const adjustmentApi = {
  getList: ({ accountNo = "", adjustStatus = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({
      accountNo,
      adjustStatus,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return requestPage(`/api/accounting/adjustments?${params.toString()}`);
  },
  create: (payload) => postJson("/api/accounting/adjustments", payload)
    .then((data) => normalizeAccountingResult(data, "adjustNo", "", "调账单响应未确认调账单")),
  approve: (adjustNo, payload) => postJson(`/api/accounting/adjustments/${adjustNo}/approve`, payload)
    .then((data) => normalizeAccountingResult(data, "adjustNo", adjustNo, "调账审核响应未确认当前调账单"))
};

export const eventApi = {
  getList: ({ eventType = "", bizNo = "", paymentOrderId = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({
      eventType,
      bizNo,
      paymentOrderId,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/accounting/events?${params.toString()}`).then(normalizeAccountingEventPage);
  }
};
