function createBusinessError(payload, fallbackMessage) {
  const message = payload?.message || fallbackMessage || "Request failed";
  const displayMessage = `${message} [${payload?.code || "PAYMENT-CLIENT"}${payload?.requestId ? ` / ${payload.requestId}` : ""}]`;
  const error = new Error(displayMessage);
  error.code = payload?.code || "PAYMENT-CLIENT";
  error.requestId = payload?.requestId || "";
  error.rawMessage = message;
  error.displayMessage = displayMessage;
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

export const paymentMonitorApi = {
  getOverview: () => request("/api/payment-monitor/overview")
};

export const paymentIssueApi = {
  getList: ({
    paymentOrderId = "",
    orderNo = "",
    issueType = "全部",
    severity = "全部",
    channelCode = "",
    paymentMethod = "全部",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      orderNo,
      issueType,
      severity,
      channelCode,
      paymentMethod,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/payment-issues?${params.toString()}`);
  }
};

export const paymentDayEndApi = {
  getOverview: () => request("/api/payment-day-end/overview"),
  runBatch: (payload) => postJson("/api/payment-day-end/run", payload)
};

export const paymentTaskCenterApi = {
  getOverview: () => request("/api/payment-task-center/overview"),
  runCloseExpiredPayments: () => postJson("/api/payment-task-center/close-expired-payments", {}),
  runRepublishFailedEvents: () => postJson("/api/payment-task-center/republish-failed-events", {}),
  runRetryFailedRefunds: () => postJson("/api/payment-task-center/retry-failed-refunds", {})
};

export const orderApi = {
  getList: ({
    orderNo = "",
    serviceType = "全部",
    orderStatus = "全部",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      orderNo,
      serviceType,
      orderStatus,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/orders?${params.toString()}`);
  }
};

export const billApi = {
  getList: ({
    billNo = "",
    orderNo = "",
    billStatus = "全部",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      billNo,
      orderNo,
      billStatus,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/bills?${params.toString()}`);
  }
};

export const paymentFlowApi = {
  getList: ({
    paymentOrderId = "",
    orderNo = "",
    flowType = "全部",
    channelCode = "",
    businessStatus = "全部",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      orderNo,
      flowType,
      channelCode,
      businessStatus,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/payment-flows?${params.toString()}`);
  }
};

export const paymentRouteApi = {
  getList: ({
    paymentOrderId = "",
    orderNo = "",
    routeRule = "",
    channelCode = "",
    routeResult = "全部",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      orderNo,
      routeRule,
      channelCode,
      routeResult,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/payment-routes?${params.toString()}`);
  }
};

export const paymentEventApi = {
  getList: ({
    paymentOrderId = "",
    eventType = "全部",
    publishStatus = "全部",
    downstreamSystem = "全部",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      eventType,
      publishStatus,
      downstreamSystem,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/payment-events?${params.toString()}`);
  },
  republish: (eventNo, {
    paymentOrderId = "",
    eventType = "全部",
    publishStatus = "全部",
    downstreamSystem = "全部",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      eventType,
      publishStatus,
      downstreamSystem,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return postJson(`/api/payment-events/republish?${params.toString()}`, { eventNo });
  }
};

export const cashierSessionApi = {
  getList: ({
    sessionNo = "",
    orderNo = "",
    terminal = "全部",
    sessionStatus = "全部",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      sessionNo,
      orderNo,
      terminal,
      sessionStatus,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/cashier-sessions?${params.toString()}`);
  }
};

export const paymentRequestApi = {
  getList: ({
    requestNo = "",
    paymentOrderId = "",
    orderNo = "",
    channelCode = "",
    terminal = "全部",
    requestStatus = "全部",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      requestNo,
      paymentOrderId,
      orderNo,
      channelCode,
      terminal,
      requestStatus,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/payment-requests?${params.toString()}`);
  }
};

export const paymentLogApi = {
  getList: ({
    paymentOrderId = "",
    orderNo = "",
    processStage = "全部",
    logLevel = "全部",
    source = "",
    keyword = "",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      orderNo,
      processStage,
      logLevel,
      source,
      keyword,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
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
  },
  getDetail: (paymentOrderId) => request(`/api/payment-records/${paymentOrderId}`)
};

export const paymentApi = {
  getList: ({
    paymentOrderId = "",
    orderNo = "",
    paymentMethod = "全部",
    status = "全部",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      orderNo,
      paymentMethod,
      status,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/payments?${params.toString()}`);
  },
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
    refundMethod = "全部",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      refundOrderId,
      paymentOrderId,
      refundStatus,
      refundMethod,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/refunds?${params.toString()}`);
  },
  apply: (payload) => postJson("/api/refunds/apply", payload),
  approve: (refundOrderId) => postJson("/api/refunds/approve", { refundOrderId }),
  markSuccess: (refundOrderId) => postJson("/api/refunds/success", { refundOrderId }),
  markFail: (refundOrderId) => postJson("/api/refunds/fail", { refundOrderId }),
  retry: (refundOrderId) => postJson("/api/refunds/retry", { refundOrderId })
};

export const settlementApi = {
  getWorkerList: ({
    settlementOrderId = "",
    workerKeyword = "",
    settlementStatus = "全部",
    payoutStatus = "全部",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      settlementOrderId,
      workerKeyword,
      settlementStatus,
      payoutStatus,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/settlements/workers?${params.toString()}`);
  }
};

export const paymentConfigApi = {
  getOverview: () => request("/api/payment-config"),
  toggleChannel: (configCode, enabled) => postJson("/api/payment-config/channels/toggle", { configCode, enabled }),
  toggleRouteRule: (configCode, enabled) => postJson("/api/payment-config/route-rules/toggle", { configCode, enabled }),
  createProtocol: (payload) => postJson("/api/payment-config/protocols", payload),
  updateProtocol: (protocolCode, payload) => request(`/api/payment-config/protocols/${protocolCode}`, buildJsonRequestOptions("PUT", payload)),
  toggleProtocol: (configCode, enabled) => postJson("/api/payment-config/protocols/toggle", { configCode, enabled }),
  toggleReturnCodeMapping: (configCode, subCode, enabled) => postJson("/api/payment-config/return-codes/toggle", { configCode, subCode, enabled }),
  toggleGateway: (configCode, enabled) => postJson("/api/payment-config/gateways/toggle", { configCode, enabled })
};
