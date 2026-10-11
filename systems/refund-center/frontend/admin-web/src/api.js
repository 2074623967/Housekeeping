const createRequestError = (code, message, cause) => {
  const error = new Error(`${message} [${code}]`);
  error.code = code;
  error.cause = cause;
  return error;
};

const CLIENT_TIMEOUT_MS = 10000;

export const formatRefundTime = (value) => {
  if (typeof value !== "string" || !value.trim()) {
    return "-";
  }
  return value.trim().replace("T", " ");
};

const jsonRequest = async (url, options = {}) => {
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
      ? "REFUND-CLIENT-TIMEOUT"
      : "REFUND-CLIENT-NETWORK";
    throw createRequestError(code, error?.name === "AbortError" ? "退款服务请求超时" : "无法连接退款服务", error);
  } finally {
    clearTimeout(timeoutId);
  }
  let body;
  try {
    body = await response.json();
  } catch (error) {
    throw createRequestError("REFUND-CLIENT-INVALID-RESPONSE", `退款服务返回了无法解析的响应 [HTTP ${response.status}]`, error);
  }
  if (!response.ok || body?.code !== "0") {
    const code = body?.code || `REFUND-CLIENT-HTTP-${response.status}`;
    throw createRequestError(code, body?.message || "请求失败");
  }
  return body.data;
};

export const getOverview = () => jsonRequest("/api/refunds/overview");

export const getRefunds = (query = {}) => {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      params.set(key, value);
    }
  });
  return jsonRequest(`/api/refunds?${params.toString()}`)
    .then((page) => normalizePage(page, query.pageNo, query.pageSize))
    .then((page) => ({
      ...page,
      items: page.items.map((item) => normalizeRefundResult(item))
    }));
};

export const getRefundDetail = (refundOrderId) =>
  jsonRequest(`/api/refunds/${encodeURIComponent(refundOrderId)}`)
    .then((data) => normalizeRefundResult(data, refundOrderId))
    .then(normalizeRefundDetail);

export const applyRefund = (request) =>
  jsonRequest("/api/refunds/apply", {
    method: "POST",
    body: JSON.stringify(request)
  }).then((data) => normalizeRefundResult(data));

export const downloadRefundDetail = async (refundOrderId) => {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), CLIENT_TIMEOUT_MS);
  try {
    let response;
    try {
      response = await fetch(`/api/refunds/${encodeURIComponent(refundOrderId)}/export`, {
        signal: controller.signal
      });
    } catch (error) {
      const code = error?.name === "AbortError"
        ? "REFUND-CLIENT-TIMEOUT"
        : "REFUND-CLIENT-NETWORK";
      throw createRequestError(code, error?.name === "AbortError" ? "退款服务导出请求超时" : "无法连接退款服务", error);
    }
    if (!response.ok) {
      throw createRequestError(`REFUND-CLIENT-HTTP-${response.status}`, "导出失败");
    }
    return response.blob();
  } finally {
    clearTimeout(timeoutId);
  }
};

export const postRefundAction = (refundOrderId, action, payload = {}) =>
  jsonRequest(`/api/refunds/${encodeURIComponent(refundOrderId)}/${action}`, {
    method: "POST",
    body: JSON.stringify(payload)
  }).then((data) => normalizeRefundResult(data, refundOrderId));

export const getRefundOutbox = (query = {}) => {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      params.set(key, value);
    }
  });
  return jsonRequest(`/api/refunds/outbox?${params.toString()}`)
    .then((page) => normalizePage(page, query.pageNo, query.pageSize));
};

const normalizePage = (page, pageNo = 1, pageSize = 20) => {
  const fallbackPageNo = Number.isInteger(Number(pageNo)) && Number(pageNo) > 0 ? Number(pageNo) : 1;
  const fallbackPageSize = Number.isInteger(Number(pageSize)) && Number(pageSize) > 0 ? Number(pageSize) : 20;
  if (Array.isArray(page)) {
    return { items: page, total: page.length, pageNo: fallbackPageNo, pageSize: fallbackPageSize };
  }
  if (!page || typeof page !== "object") {
    return { items: [], total: 0, pageNo: fallbackPageNo, pageSize: fallbackPageSize };
  }
  return {
    ...page,
    items: Array.isArray(page.items) ? page.items : [],
    total: Number.isFinite(Number(page.total)) ? Number(page.total) : 0,
    pageNo: Number.isInteger(Number(page.pageNo)) && Number(page.pageNo) > 0
      ? Number(page.pageNo)
      : fallbackPageNo,
    pageSize: Number.isInteger(Number(page.pageSize)) && Number(page.pageSize) > 0
      ? Number(page.pageSize)
      : fallbackPageSize
  };
};

export const normalizeRefundResult = (data, expectedRefundOrderId = "") => {
  if (!data || typeof data !== "object" || typeof data.refundOrderId !== "string"
      || !data.refundOrderId.trim()
      || typeof data.paymentOrderId !== "string" || !data.paymentOrderId.trim()
      || typeof data.businessChainId !== "string"
      || data.businessChainId.trim() !== `CHAIN-${data.paymentOrderId.trim()}`
      || typeof data.orderNo !== "string" || !data.orderNo.trim()
      || !isValidMoney(data.paidAmount)
      || !isValidMoney(data.refundAmount)
      || !isAmountNotGreater(data.refundAmount, data.paidAmount)
      || !isConsistentRefundStatus(data.status, data.statusType)
      || !isConsistentRefundLifecycleTimes(data)
      || (expectedRefundOrderId && data.refundOrderId !== expectedRefundOrderId)) {
    throw createRequestError(
      "REFUND-CLIENT-INVALID-BUSINESS-RESULT",
      "退款响应未确认当前退款单"
    );
  }
  return data;
};

const isValidMoney = (value) => {
  if (typeof value !== "string" && typeof value !== "number") {
    return false;
  }
  const normalized = String(value).trim().replace(/^¥\s*/, "");
  return /^(?:0|[1-9]\d{0,15})(?:\.\d{1,2})?$/.test(normalized)
    && Number(normalized) > 0;
};

const isAmountNotGreater = (left, right) => {
  if (!isValidMoney(left) || !isValidMoney(right)) {
    return false;
  }
  const toMinorUnits = (value) => {
    const [integerPart, fractionPart = ""] = String(value).trim()
      .replace(/^¥\s*/, "")
      .split(".");
    return BigInt(integerPart) * 100n + BigInt(fractionPart.padEnd(2, "0"));
  };
  return toMinorUnits(left) <= toMinorUnits(right);
};

const isConsistentRefundStatus = (status, statusType) => {
  if (typeof status !== "string" || !status.trim()
      || typeof statusType !== "string" || !statusType.trim()) {
    return false;
  }
  const expectedType = {
    REVIEWING: "warn",
    APPROVED: "success",
    PROCESSING: "warn",
    SUCCESS: "success",
    FAIL: "danger"
  }[status.trim().toUpperCase()];
  return Boolean(expectedType) && statusType.trim().toLowerCase() === expectedType;
};

const isValidRefundTime = (value) => {
  if (typeof value !== "string") {
    return false;
  }
  const normalized = value.trim().replace("T", " ");
  const match = /^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})(?:\.\d{1,9})?$/.exec(normalized);
  if (!match) {
    return false;
  }
  const [, year, month, day, hour, minute, second] = match.map(Number);
  const date = new Date(0);
  date.setUTCFullYear(year, month - 1, day);
  date.setUTCHours(hour, minute, second, 0);
  return date.getUTCFullYear() === year
    && date.getUTCMonth() === month - 1
    && date.getUTCDate() === day
    && date.getUTCHours() === hour
    && date.getUTCMinutes() === minute
    && date.getUTCSeconds() === second;
};

const isConsistentRefundLifecycleTimes = (data) => {
  if (!isValidRefundTime(data.appliedAt)) {
    return false;
  }
  const appliedAt = Date.parse(data.appliedAt.trim().replace("T", " "));
  const hasSuccessAt = data.successAt != null;
  const hasTerminalAt = data.terminalAt != null;
  if ((hasSuccessAt && !isValidRefundTime(data.successAt))
      || (hasTerminalAt && !isValidRefundTime(data.terminalAt))) {
    return false;
  }
  const successAt = hasSuccessAt ? Date.parse(data.successAt.trim().replace("T", " ")) : null;
  const terminalAt = hasTerminalAt ? Date.parse(data.terminalAt.trim().replace("T", " ")) : null;
  const status = data.status.trim().toUpperCase();
  if (status === "SUCCESS") {
    return successAt != null && terminalAt != null
      && appliedAt <= successAt && successAt <= terminalAt;
  }
  if (status === "FAIL") {
    return successAt == null && terminalAt != null && appliedAt <= terminalAt;
  }
  return successAt == null && terminalAt == null;
};

export const normalizeRefundOutboxResult = (data, expectedEventId = "") => {
  if (!data || typeof data !== "object" || typeof data.eventId !== "string"
      || !data.eventId.trim() || (expectedEventId && data.eventId !== expectedEventId)) {
    throw createRequestError(
      "REFUND-CLIENT-INVALID-BUSINESS-RESULT",
      "退款出站响应未确认当前事件"
    );
  }
  return data;
};

const normalizeRefundDetail = (detail) => {
  if (!detail || typeof detail !== "object") return detail;
  return {
    ...detail,
    operationLogs: Array.isArray(detail.operationLogs) ? detail.operationLogs : []
  };
};

export const dispatchRefundOutbox = (eventId, payload = {}) =>
  jsonRequest(`/api/refunds/outbox/${encodeURIComponent(eventId)}/dispatch`, {
    method: "POST",
    body: JSON.stringify(payload)
  }).then((data) => normalizeRefundOutboxResult(data, eventId));
