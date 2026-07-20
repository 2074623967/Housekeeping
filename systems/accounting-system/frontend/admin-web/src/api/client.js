function createBusinessError(payload, fallbackMessage) {
  const message = payload?.message || fallbackMessage || "Request failed";
  const displayMessage = `${message} [${payload?.code || "ACCOUNTING-CLIENT"}${payload?.requestId ? ` / ${payload.requestId}` : ""}]`;
  const error = new Error(displayMessage);
  error.code = payload?.code || "ACCOUNTING-CLIENT";
  error.requestId = payload?.requestId || "";
  error.rawMessage = message;
  return error;
}

async function parseResponsePayload(response) {
  try {
    return await response.json();
  } catch (error) {
    return null;
  }
}

async function request(url, options = {}) {
  const response = await fetch(url, {
    headers: { "Content-Type": "application/json" },
    ...options
  });
  const payload = await parseResponsePayload(response);
  if (!response.ok) {
    throw createBusinessError(payload, `Request failed: ${response.status}`);
  }
  if (!payload || payload.code !== "0") {
    throw createBusinessError(payload, "Request failed");
  }
  return payload.data;
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
    return request(`/api/accounting/subjects?${params.toString()}`);
  },
  create: (payload) => postJson("/api/accounting/subjects", payload),
  getDetail: (subjectId) => request(`/api/accounting/subjects/${subjectId}`)
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
    return request(`/api/accounting/accounts?${params.toString()}`);
  },
  open: (payload) => postJson("/api/accounting/accounts/open", payload),
  getDetail: (accountNo) => request(`/api/accounting/accounts/${accountNo}`)
};

export const balanceApi = {
  getDetail: (accountNo) => request(`/api/accounting/balances/${accountNo}`),
  credit: (payload) => postJson("/api/accounting/balances/credit", payload),
  freeze: (payload) => postJson("/api/accounting/balances/freeze", payload),
  unfreeze: (payload) => postJson("/api/accounting/balances/unfreeze", payload)
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
    return request(`/api/accounting/ledgers?${params.toString()}`);
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
    return request(`/api/accounting/freezes?${params.toString()}`);
  },
  create: (payload) => postJson("/api/accounting/freezes", payload),
  unfreeze: (freezeNo, payload) => postJson(`/api/accounting/freezes/${freezeNo}/unfreeze`, payload)
};

export const adjustmentApi = {
  getList: ({ accountNo = "", adjustStatus = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({
      accountNo,
      adjustStatus,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/accounting/adjustments?${params.toString()}`);
  },
  create: (payload) => postJson("/api/accounting/adjustments", payload),
  approve: (adjustNo, payload) => postJson(`/api/accounting/adjustments/${adjustNo}/approve`, payload)
};

export const eventApi = {
  getList: ({ eventType = "", bizNo = "", pageNo = 1, pageSize = 20 } = {}) => {
    const params = new URLSearchParams({
      eventType,
      bizNo,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/accounting/events?${params.toString()}`);
  },
  consumePaymentSuccess: (payload) => postJson("/api/accounting/events/payments/success", payload),
  consumeClearingGenerated: (payload) => postJson("/api/accounting/events/clearing/generated", payload)
};
