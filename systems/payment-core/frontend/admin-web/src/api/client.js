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
  getTaskRuns: ({
    taskCode = "",
    runMode = "全部",
    taskStatus = "全部",
    severityLevel = "全部",
    pageNo = 1,
    pageSize = 10
  } = {}) => {
    const params = new URLSearchParams({
      taskCode,
      runMode,
      taskStatus,
      severityLevel,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/payment-task-center/task-runs?${params.toString()}`);
  },
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
    customerName = "",
    billStatus = "全部",
    sortField = "createdAt",
    sortOrder = "desc",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      billNo,
      orderNo,
      customerName,
      billStatus,
      sortField,
      sortOrder,
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
    terminal = "全部",
    businessStatus = "全部",
    keyword = "",
    sortField = "createdAt",
    sortOrder = "desc",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      orderNo,
      flowType,
      channelCode,
      terminal,
      businessStatus,
      keyword,
      sortField,
      sortOrder,
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
    paymentMethod = "全部",
    terminal = "全部",
    routeResult = "全部",
    sortField = "createdAt",
    sortOrder = "desc",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      orderNo,
      routeRule,
      channelCode,
      paymentMethod,
      terminal,
      routeResult,
      sortField,
      sortOrder,
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
    eventTopic = "",
    sortField = "createdAt",
    sortOrder = "desc",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      eventType,
      publishStatus,
      downstreamSystem,
      eventTopic,
      sortField,
      sortOrder,
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
    eventTopic = "",
    sortField = "createdAt",
    sortOrder = "desc",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      eventType,
      publishStatus,
      downstreamSystem,
      eventTopic,
      sortField,
      sortOrder,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return postJson(`/api/payment-events/republish?${params.toString()}`, { eventNo });
  }
};

export const cashierSessionApi = {
  getList: ({
    sessionNo = "",
    paymentOrderId = "",
    orderNo = "",
    customerName = "",
    terminal = "全部",
    sessionStatus = "全部",
    sortField = "createdAt",
    sortOrder = "desc",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      sessionNo,
      paymentOrderId,
      orderNo,
      customerName,
      terminal,
      sessionStatus,
      sortField,
      sortOrder,
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
    clientIp = "",
    requestStatus = "全部",
    sortField = "createdAt",
    sortOrder = "desc",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      requestNo,
      paymentOrderId,
      orderNo,
      channelCode,
      terminal,
      clientIp,
      requestStatus,
      sortField,
      sortOrder,
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
    sortField = "createdAt",
    sortOrder = "desc",
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
      sortField,
      sortOrder,
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
    paymentStatus = "全部",
    paymentChannel = "",
    sortField = "createdAt",
    sortOrder = "desc",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      recordType,
      userId,
      businessOrderNo,
      paymentType,
      paymentStatus,
      paymentChannel,
      sortField,
      sortOrder,
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
  getDetail: (refundOrderId) => request(`/api/refunds/${refundOrderId}`),
  apply: (payload) => postJson("/api/refunds/apply", payload),
  approve: (refundOrderId, remark = "") => postJson("/api/refunds/approve", { refundOrderId, remark }),
  markSuccess: (refundOrderId, remark = "") => postJson("/api/refunds/success", { refundOrderId, remark }),
  markFail: (refundOrderId, remark = "") => postJson("/api/refunds/fail", { refundOrderId, remark }),
  retry: (refundOrderId, remark = "") => postJson("/api/refunds/retry", { refundOrderId, remark })
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
  toggleGateway: (configCode, enabled) => postJson("/api/payment-config/gateways/toggle", { configCode, enabled }),
  toggleControlPolicy: (configCode, enabled) => postJson("/api/payment-config/control-policies/toggle", { configCode, enabled }),
  runControlPolicySelfCheck: (configCode) => postJson("/api/payment-config/control-policies/self-check", { configCode })
};
