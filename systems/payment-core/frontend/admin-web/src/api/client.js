async function request(url, options = {}) {
  const response = await fetch(url, {
    headers: { "Content-Type": "application/json" },
    ...options
  });
  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`);
  }
  const payload = await response.json();
  if (payload.code !== "0") {
    throw new Error(payload.message || "Request failed");
  }
  return payload.data;
}

function buildJsonRequestOptions(method, payload) {
  return {
    method,
    body: JSON.stringify(payload)
  };
}

function postJson(url, payload) {
  return request(url, buildJsonRequestOptions("POST", payload));
}

function buildSuccessCallbackPayload(paymentOrderId) {
  return {
    paymentOrderId,
    channelTransactionNo: `SIM${Date.now()}`,
    tradeStatus: "SUCCESS"
  };
}

export const dashboardApi = {
  getSummary: () => request("/api/dashboard/summary")
};

export const paymentMetricsApi = {
  getSummary: () => request("/api/payment-metrics/summary")
};

export const orderApi = {
  getList: () => request("/api/orders")
};

export const billApi = {
  getList: ({
    billNo = "",
    orderNo = "",
    billStatus = "全部"
  } = {}) => {
    const params = new URLSearchParams({
      billNo,
      orderNo,
      billStatus
    });
    return request(`/api/bills?${params.toString()}`);
  }
};

export const paymentFlowApi = {
  getList: ({
    paymentOrderId = "",
    orderNo = "",
    flowType = "全部"
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      orderNo,
      flowType
    });
    return request(`/api/payment-flows?${params.toString()}`);
  }
};

export const cashierSessionApi = {
  getList: ({
    sessionNo = "",
    orderNo = "",
    terminal = "全部",
    sessionStatus = "全部"
  } = {}) => {
    const params = new URLSearchParams({
      sessionNo,
      orderNo,
      terminal,
      sessionStatus
    });
    return request(`/api/cashier-sessions?${params.toString()}`);
  }
};

export const paymentRequestApi = {
  getList: ({
    requestNo = "",
    paymentOrderId = "",
    requestStatus = "全部"
  } = {}) => {
    const params = new URLSearchParams({
      requestNo,
      paymentOrderId,
      requestStatus
    });
    return request(`/api/payment-requests?${params.toString()}`);
  }
};

export const paymentLogApi = {
  getList: ({
    paymentOrderId = "",
    processStage = "全部",
    logLevel = "全部"
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      processStage,
      logLevel
    });
    return request(`/api/payment-logs?${params.toString()}`);
  }
};

export const paymentRecordApi = {
  getList: ({
    recordType = "ALL",
    userId = "",
    businessOrderNo = "",
    paymentType = "",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      recordType,
      userId,
      businessOrderNo,
      paymentType,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/payment-records?${params.toString()}`);
  }
};

export const paymentApi = {
  getList: () => request("/api/payments"),
  getDetail: (paymentOrderId) => request(`/api/payments/${paymentOrderId}`),
  prepay: (payload) => postJson("/api/payments/prepay", payload),
  query: (paymentOrderId) => postJson("/api/payments/query", { paymentOrderId }),
  close: (paymentOrderId) => postJson("/api/payments/close", { paymentOrderId }),
  callback: (channelCode, paymentOrderId) => postJson(
    `/api/payments/callback/${channelCode}`,
    buildSuccessCallbackPayload(paymentOrderId)
  )
};

export const refundApi = {
  getList: ({
    refundOrderId = "",
    paymentOrderId = "",
    refundStatus = "全部",
    refundMethod = "全部"
  } = {}) => {
    const params = new URLSearchParams({
      refundOrderId,
      paymentOrderId,
      refundStatus,
      refundMethod
    });
    return request(`/api/refunds?${params.toString()}`);
  }
};

export const settlementApi = {
  getWorkerList: ({
    settlementOrderId = "",
    workerKeyword = "",
    settlementStatus = "全部",
    payoutStatus = "全部"
  } = {}) => {
    const params = new URLSearchParams({
      settlementOrderId,
      workerKeyword,
      settlementStatus,
      payoutStatus
    });
    return request(`/api/settlements/workers?${params.toString()}`);
  }
};
