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
    const parseError = new Error("支付服务返回了无法解析的响应");
    parseError.code = "PAYMENT-CLIENT-INVALID-RESPONSE";
    parseError.status = response.status;
    parseError.cause = error;
    throw parseError;
  }
}

export function normalizePagePayload(data) {
  if (data && typeof data === "object"
      && Object.prototype.hasOwnProperty.call(data, "items")
      && !Array.isArray(data.items)) {
    return { ...data, items: [] };
  }
  return data;
}

async function request(url, options = {}) {
  let response;
  try {
    response = await fetch(url, {
      headers: { "Content-Type": "application/json" },
      ...options
    });
  } catch (error) {
    const transportCode = error?.name === "AbortError"
      ? "PAYMENT-CLIENT-TIMEOUT"
      : "PAYMENT-CLIENT-NETWORK";
    throw createBusinessError({ code: transportCode }, error?.name === "AbortError" ? "支付服务请求超时" : "无法连接支付服务");
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
  return normalizePagePayload(payload.data);
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

export const dashboardApi = {
  getSummary: () => request("/api/dashboard/summary")
};

export const paymentMetricsApi = {
  getSummary: () => request("/api/payment-metrics/summary")
};

export const paymentMonitorApi = {
  getOverview: () => request("/api/payment-monitor/overview"),
  buildExportUrl: () => "/api/payment-monitor/export"
};

export const paymentIssueApi = {
  getDetail: (issueNo) => request(`/api/payment-issues/${encodeURIComponent(issueNo)}`),
  getAlertDetail: (alertNo) => request(`/api/payment-issues/alerts/${encodeURIComponent(alertNo)}`),
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
  },
  getResponsibilitySummary: ({
    paymentOrderId = "",
    orderNo = "",
    issueType = "全部",
    severity = "全部",
    channelCode = "",
    paymentMethod = "全部"
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      orderNo,
      issueType,
      severity,
      channelCode,
      paymentMethod
    });
    return request(`/api/payment-issues/responsibility-summary?${params.toString()}`);
  },
  getAlertLogs: ({
    alertNo = "",
    issueNo = "",
    paymentOrderId = "",
    alertChannel = "全部",
    alertStatus = "全部",
    ackStatus = "全部",
    providerDeliveryStatus = "全部",
    receiptQueryState = "全部",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      alertNo,
      issueNo,
      paymentOrderId,
      alertChannel,
      alertStatus,
      ackStatus,
      providerDeliveryStatus,
      receiptQueryState,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/payment-issues/alerts?${params.toString()}`);
  },
  buildAlertExportUrl: ({
    alertNo = "",
    issueNo = "",
    paymentOrderId = "",
    alertChannel = "全部",
    alertStatus = "全部",
    ackStatus = "全部",
    providerDeliveryStatus = "全部",
    receiptQueryState = "全部"
  } = {}) => {
    const params = new URLSearchParams({
      alertNo,
      issueNo,
      paymentOrderId,
      alertChannel,
      alertStatus,
      ackStatus,
      providerDeliveryStatus,
      receiptQueryState
    });
    return `/api/payment-issues/alerts/export?${params.toString()}`;
  },
  acknowledgeAlert: (alertNo, payload) => postJson(
    `/api/payment-issues/alerts/${encodeURIComponent(alertNo)}/acknowledge`,
    payload
  ),
  reconcileAlertReceipt: (alertNo, payload) => postJson(
    `/api/payment-task-center/reconcile-issue-alert-receipts/${encodeURIComponent(alertNo)}`,
    payload
  ),
  batchAction: (payload) => postJson("/api/payment-issues/actions", payload)
};

export const paymentDayEndApi = {
  getOverview: () => request("/api/payment-day-end/overview"),
  getDetail: (batchNo) => request(`/api/payment-day-end/batches/${encodeURIComponent(batchNo)}`),
  runBatch: (payload) => postJson("/api/payment-day-end/run", payload),
  buildExportUrl: () => "/api/payment-day-end/export"
};

export const paymentTaskCenterApi = {
  getOverview: () => request("/api/payment-task-center/overview"),
  getTaskRunDetail: (taskLogNo) => request(`/api/payment-task-center/task-runs/${encodeURIComponent(taskLogNo)}`),
  getTaskRuns: ({
    taskCode = "",
    bizDate = "",
    runMode = "全部",
    taskStatus = "全部",
    severityLevel = "全部",
    pageNo = 1,
    pageSize = 10
  } = {}) => {
    const params = new URLSearchParams({
      taskCode,
      bizDate,
      runMode,
      taskStatus,
      severityLevel,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/payment-task-center/task-runs?${params.toString()}`);
  },
  buildExportUrl: ({
    taskCode = "",
    bizDate = "",
    runMode = "全部",
    taskStatus = "全部",
    severityLevel = "全部"
  } = {}) => {
    const params = new URLSearchParams({
      taskCode,
      bizDate,
      runMode,
      taskStatus,
      severityLevel
    });
    return `/api/payment-task-center/task-runs/export?${params.toString()}`;
  },
  runCloseExpiredPayments: (payload) => postJson("/api/payment-task-center/close-expired-payments", payload),
  runPendingPaymentQueries: (payload) => postJson("/api/payment-task-center/query-pending-payments", payload),
  runPendingRefundQueries: (payload) => postJson("/api/payment-task-center/query-pending-refunds", payload),
  runGenerateQueryMonitorAlerts: (payload) => postJson("/api/payment-task-center/generate-query-monitor-alerts", payload),
  runRepublishFailedEvents: (payload) => postJson("/api/payment-task-center/republish-failed-events", payload),
  runRetryFailedRefunds: (payload) => postJson("/api/payment-task-center/retry-failed-refunds", payload),
  runEscalateOverdueIssues: (payload) => postJson("/api/payment-task-center/escalate-overdue-issues", payload),
  runDispatchIssueAlerts: (payload) => postJson("/api/payment-task-center/dispatch-issue-alerts", payload),
  runReconcileIssueAlertReceipts: (payload) => postJson("/api/payment-task-center/reconcile-issue-alert-receipts", payload),
  runControlPolicySelfChecks: (payload) => postJson("/api/payment-task-center/control-policy-self-checks", payload)
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
  },
  buildExportUrl: ({
    orderNo = "",
    serviceType = "全部",
    orderStatus = "全部"
  } = {}) => {
    const params = new URLSearchParams({
      orderNo,
      serviceType,
      orderStatus
    });
    return `/api/orders/export?${params.toString()}`;
  },
  getDetail: (orderNo) => request(`/api/orders/${encodeURIComponent(orderNo)}`)
};

export const billApi = {
  getOverview: ({
    billNo = "",
    orderNo = "",
    customerName = "",
    billStatus = "全部",
    sortField = "createdAt",
    sortOrder = "desc"
  } = {}) => {
    const params = new URLSearchParams({
      billNo,
      orderNo,
      customerName,
      billStatus,
      sortField,
      sortOrder
    });
    return request(`/api/bills/overview?${params.toString()}`);
  },
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
  },
  buildExportUrl: ({
    billNo = "",
    orderNo = "",
    customerName = "",
    billStatus = "全部",
    sortField = "createdAt",
    sortOrder = "desc"
  } = {}) => {
    const params = new URLSearchParams({
      billNo,
      orderNo,
      customerName,
      billStatus,
      sortField,
      sortOrder
    });
    return `/api/bills/export?${params.toString()}`;
  },
  getDetail: (billNo) => request(`/api/bills/${encodeURIComponent(billNo)}`)
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
  },
  buildExportUrl: ({
    paymentOrderId = "",
    orderNo = "",
    flowType = "全部",
    channelCode = "",
    terminal = "全部",
    businessStatus = "全部",
    keyword = "",
    sortField = "createdAt",
    sortOrder = "desc"
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
      sortOrder
    });
    return `/api/payment-flows/export?${params.toString()}`;
  },
  getDetail: (flowSource, flowNo) => request(
    `/api/payment-flows/${encodeURIComponent(flowSource)}/${encodeURIComponent(flowNo)}`
  )
};

export const paymentRouteApi = {
  getOverview: ({
    paymentOrderId = "",
    orderNo = "",
    routeRule = "",
    channelCode = "",
    paymentMethod = "全部",
    terminal = "全部",
    routeResult = "全部",
    sortField = "createdAt",
    sortOrder = "desc"
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
      sortOrder
    });
    return request(`/api/payment-routes/overview?${params.toString()}`);
  },
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
  },
  buildExportUrl: ({
    paymentOrderId = "",
    orderNo = "",
    routeRule = "",
    channelCode = "",
    paymentMethod = "全部",
    terminal = "全部",
    routeResult = "全部",
    sortField = "createdAt",
    sortOrder = "desc"
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
      sortOrder
    });
    return `/api/payment-routes/export?${params.toString()}`;
  },
  getDetail: (routeNo) => request(`/api/payment-routes/${encodeURIComponent(routeNo)}`)
};

export const paymentEventApi = {
  getDetail: (eventNo) => request(`/api/payment-events/${encodeURIComponent(eventNo)}`),
  getOverview: ({
    paymentOrderId = "",
    eventType = "全部",
    publishStatus = "全部",
    downstreamSystem = "全部",
    eventTopic = "",
    sortField = "createdAt",
    sortOrder = "desc"
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      eventType,
      publishStatus,
      downstreamSystem,
      eventTopic,
      sortField,
      sortOrder
    });
    return request(`/api/payment-events/overview?${params.toString()}`);
  },
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
  buildExportUrl: ({
    paymentOrderId = "",
    eventType = "全部",
    publishStatus = "全部",
    downstreamSystem = "全部",
    eventTopic = "",
    sortField = "createdAt",
    sortOrder = "desc"
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      eventType,
      publishStatus,
      downstreamSystem,
      eventTopic,
      sortField,
      sortOrder
    });
    return `/api/payment-events/export?${params.toString()}`;
  },
  republish: (eventNo, operator, {
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
    return postJson(`/api/payment-events/republish?${params.toString()}`, { eventNo, operator });
  }
};

export const paymentDeadLetterTaskApi = {
  getDetail: (taskNo) => request(`/api/payment-dead-letter-tasks/${encodeURIComponent(taskNo)}`),
  getList: ({
    taskStatus = "",
    targetSystem = "",
    messageId = "",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      taskStatus,
      targetSystem,
      messageId,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/payment-dead-letter-tasks?${params.toString()}`);
  },
  markReadyToReplay: (taskNo, payload) => postJson(
    `/api/payment-dead-letter-tasks/${encodeURIComponent(taskNo)}/ready-to-replay`,
    payload
  ),
  resolveManually: (taskNo, payload) => postJson(
    `/api/payment-dead-letter-tasks/${encodeURIComponent(taskNo)}/resolve-manually`,
    payload
  ),
  replay: (taskNo, payload) => postJson(
    `/api/payment-dead-letter-tasks/${encodeURIComponent(taskNo)}/replay`,
    payload
  )
};

export const cashierSessionApi = {
  getDetail: (sessionNo) => request(`/api/cashier-sessions/${encodeURIComponent(sessionNo)}`),
  getOverview: ({
    sessionNo = "",
    paymentOrderId = "",
    orderNo = "",
    customerName = "",
    terminal = "全部",
    sessionStatus = "全部",
    sortField = "createdAt",
    sortOrder = "desc"
  } = {}) => {
    const params = new URLSearchParams({
      sessionNo,
      paymentOrderId,
      orderNo,
      customerName,
      terminal,
      sessionStatus,
      sortField,
      sortOrder
    });
    return request(`/api/cashier-sessions/overview?${params.toString()}`);
  },
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
  },
  buildExportUrl: ({
    sessionNo = "",
    paymentOrderId = "",
    orderNo = "",
    customerName = "",
    terminal = "全部",
    sessionStatus = "全部",
    sortField = "createdAt",
    sortOrder = "desc"
  } = {}) => {
    const params = new URLSearchParams({
      sessionNo,
      paymentOrderId,
      orderNo,
      customerName,
      terminal,
      sessionStatus,
      sortField,
      sortOrder
    });
    return `/api/cashier-sessions/export?${params.toString()}`;
  }
};

export const paymentRequestApi = {
  getDetail: (requestNo) => request(`/api/payment-requests/${encodeURIComponent(requestNo)}`),
  getOverview: ({
    requestNo = "",
    paymentOrderId = "",
    orderNo = "",
    channelCode = "",
    terminal = "全部",
    clientIp = "",
    requestStatus = "全部",
    sortField = "createdAt",
    sortOrder = "desc"
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
      sortOrder
    });
    return request(`/api/payment-requests/overview?${params.toString()}`);
  },
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
  },
  buildExportUrl: ({
    requestNo = "",
    paymentOrderId = "",
    orderNo = "",
    channelCode = "",
    terminal = "全部",
    clientIp = "",
    requestStatus = "全部",
    sortField = "createdAt",
    sortOrder = "desc"
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
      sortOrder
    });
    return `/api/payment-requests/export?${params.toString()}`;
  }
};

export const paymentLogApi = {
  getDetail: (logNo) => request(`/api/payment-logs/${encodeURIComponent(logNo)}`),
  getOverview: ({
    paymentOrderId = "",
    orderNo = "",
    processStage = "全部",
    logLevel = "全部",
    source = "",
    keyword = "",
    sortField = "createdAt",
    sortOrder = "desc"
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      orderNo,
      processStage,
      logLevel,
      source,
      keyword,
      sortField,
      sortOrder
    });
    return request(`/api/payment-logs/overview?${params.toString()}`);
  },
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
  },
  buildExportUrl: ({
    paymentOrderId = "",
    orderNo = "",
    processStage = "全部",
    logLevel = "全部",
    source = "",
    keyword = "",
    sortField = "createdAt",
    sortOrder = "desc"
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      orderNo,
      processStage,
      logLevel,
      source,
      keyword,
      sortField,
      sortOrder
    });
    return `/api/payment-logs/export?${params.toString()}`;
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
  buildExportUrl: ({
    recordType = "ALL",
    userId = "",
    businessOrderNo = "",
    paymentType = "",
    paymentStatus = "全部",
    paymentChannel = "",
    sortField = "createdAt",
    sortOrder = "desc"
  } = {}) => {
    const params = new URLSearchParams({
      recordType,
      userId,
      businessOrderNo,
      paymentType,
      paymentStatus,
      paymentChannel,
      sortField,
      sortOrder
    });
    return `/api/payment-records/export?${params.toString()}`;
  },
  getDetail: (paymentOrderId) => request(`/api/payment-records/${encodeURIComponent(paymentOrderId)}`)
};

export const paymentApi = {
  getList: ({
    paymentOrderId = "",
    orderNo = "",
    channelCode = "",
    paymentMethod = "全部",
    status = "全部",
    pageNo = 1,
    pageSize = 20
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      orderNo,
      channelCode,
      paymentMethod,
      status,
      pageNo: String(pageNo),
      pageSize: String(pageSize)
    });
    return request(`/api/payments?${params.toString()}`);
  },
  buildExportUrl: ({
    paymentOrderId = "",
    orderNo = "",
    channelCode = "",
    paymentMethod = "全部",
    status = "全部"
  } = {}) => {
    const params = new URLSearchParams({
      paymentOrderId,
      orderNo,
      channelCode,
      paymentMethod,
      status
    });
    return `/api/payments/export?${params.toString()}`;
  },
  getDetail: (paymentOrderId) => request(`/api/payments/${encodeURIComponent(paymentOrderId)}`),
  prepay: (payload) => postJson("/api/payments/prepay", payload),
  query: (paymentOrderId, operator) =>
    postJson("/api/payments/admin/query", { paymentOrderId, operator }),
  close: (paymentOrderId, operator) =>
    postJson("/api/payments/admin/close", { paymentOrderId, operator })
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
  buildExportUrl: ({
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
    return `/api/refunds/export?${params.toString()}`;
  },
  getDetail: (refundOrderId) => request(`/api/refunds/${encodeURIComponent(refundOrderId)}`)
    .then(normalizeRefundDetail),
  apply: (payload) => postJson("/api/refunds/apply", payload),
  approve: (refundOrderId, operator, remark = "") =>
    postJson("/api/refunds/approve", { refundOrderId, operator, remark }),
  query: (refundOrderId, operator) => postJson("/api/refunds/query", { refundOrderId, operator }),
  retry: (refundOrderId, operator, remark = "") =>
    postJson("/api/refunds/retry", { refundOrderId, operator, remark })
};

function normalizeRefundDetail(detail) {
  if (!detail || typeof detail !== "object") {
    return detail;
  }
  return {
    ...detail,
    attempts: Array.isArray(detail.attempts) ? detail.attempts : [],
    operationLogs: Array.isArray(detail.operationLogs) ? detail.operationLogs : []
  };
}

export const settlementApi = {
  getWorkerOverview: ({
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
    return request(`/api/settlements/workers/overview?${params.toString()}`);
  },
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
  },
  buildWorkerExportUrl: ({
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
    return `/api/settlements/workers/export?${params.toString()}`;
  }
};

export const paymentConfigApi = {
  getOverview: () => request("/api/payment-config"),
  buildExportUrl: (section = "ALL") => `/api/payment-config/export?section=${encodeURIComponent(section)}`,
  submitChannelStatusReview: (payload) => postJson("/api/payment-config/channels/toggle", payload),
  submitChannelReview: (payload) => postJson("/api/payment-config/channels/reviews", payload),
  getChannelReviews: () => request("/api/payment-config/channels/reviews"),
  approveChannelReview: (requestNo, payload) => postJson(
    `/api/payment-config/channels/reviews/${encodeURIComponent(requestNo)}/approve`,
    payload
  ),
  rejectChannelReview: (requestNo, payload) => postJson(
    `/api/payment-config/channels/reviews/${encodeURIComponent(requestNo)}/reject`,
    payload
  ),
  getChannelVersions: (channelCode) => request(
    `/api/payment-config/channels/${encodeURIComponent(channelCode)}/versions`
  ),
  rollbackChannel: (channelCode, payload) => postJson(
    `/api/payment-config/channels/${encodeURIComponent(channelCode)}/rollback`,
    payload
  ),
  getGatewayVersions: (gatewayCode) => request(
    `/api/payment-config/gateways/${encodeURIComponent(gatewayCode)}/versions`
  ),
  rollbackGateway: (gatewayCode, payload) => postJson(
    `/api/payment-config/gateways/${encodeURIComponent(gatewayCode)}/rollback`,
    payload
  ),
  submitRouteRuleStatusReview: (payload) => postJson("/api/payment-config/route-rules/toggle", payload),
  validateRouteRule: (payload) => postJson("/api/payment-config/route-rules/validate", payload),
  getRouteRuleConflicts: () => request("/api/payment-config/route-rules/conflicts"),
  getRouteRuleReviews: () => request("/api/payment-config/route-rules/reviews"),
  submitRouteRuleReview: (payload) => postJson("/api/payment-config/route-rules/reviews", payload),
  approveRouteRuleReview: (requestNo, payload) => postJson(
    `/api/payment-config/route-rules/reviews/${encodeURIComponent(requestNo)}/approve`,
    payload
  ),
  rejectRouteRuleReview: (requestNo, payload) => postJson(
    `/api/payment-config/route-rules/reviews/${encodeURIComponent(requestNo)}/reject`,
    payload
  ),
  getRouteRuleVersions: (ruleCode) => request(`/api/payment-config/route-rules/${encodeURIComponent(ruleCode)}/versions`),
  submitRouteRuleRollbackReview: (ruleCode, payload) => postJson(`/api/payment-config/route-rules/${encodeURIComponent(ruleCode)}/rollback`, payload),
  getProtocolReviews: () => request("/api/payment-config/protocols/reviews"),
  submitProtocolReview: (payload) => postJson("/api/payment-config/protocols/reviews", payload),
  approveProtocolReview: (requestNo, payload) => postJson(
    `/api/payment-config/protocols/reviews/${encodeURIComponent(requestNo)}/approve`,
    payload
  ),
  rejectProtocolReview: (requestNo, payload) => postJson(
    `/api/payment-config/protocols/reviews/${encodeURIComponent(requestNo)}/reject`,
    payload
  ),
  toggleProtocol: (payload) => postJson("/api/payment-config/protocols/toggle", payload),
  getProtocolVersions: (protocolCode) => request(`/api/payment-config/protocols/${encodeURIComponent(protocolCode)}/versions`),
  rollbackProtocol: (protocolCode, payload) => postJson(`/api/payment-config/protocols/${encodeURIComponent(protocolCode)}/rollback`, payload),
  createIssueDutyRoster: (payload) => postJson("/api/payment-config/issue-duty-rosters", payload),
  updateIssueDutyRoster: (rosterCode, payload) => request(`/api/payment-config/issue-duty-rosters/${encodeURIComponent(rosterCode)}`, buildJsonRequestOptions("PUT", payload)),
  getIssueDutyRosterReviews: () => request("/api/payment-config/issue-duty-rosters/reviews"),
  approveIssueDutyRosterReview: (requestNo, payload) => postJson(
    `/api/payment-config/issue-duty-rosters/reviews/${encodeURIComponent(requestNo)}/approve`,
    payload
  ),
  rejectIssueDutyRosterReview: (requestNo, payload) => postJson(
    `/api/payment-config/issue-duty-rosters/reviews/${encodeURIComponent(requestNo)}/reject`,
    payload
  ),
  createAlertProvider: (payload) => postJson("/api/payment-config/alert-providers", payload),
  updateAlertProvider: (providerCode, payload) => request(
    `/api/payment-config/alert-providers/${encodeURIComponent(providerCode)}`,
    buildJsonRequestOptions("PUT", payload)
  ),
  getAlertProviderReviews: () => request("/api/payment-config/alert-providers/reviews"),
  approveAlertProviderReview: (requestNo, payload) => postJson(
    `/api/payment-config/alert-providers/reviews/${encodeURIComponent(requestNo)}/approve`,
    payload
  ),
  rejectAlertProviderReview: (requestNo, payload) => postJson(
    `/api/payment-config/alert-providers/reviews/${encodeURIComponent(requestNo)}/reject`,
    payload
  ),
  submitReturnCodeStatusReview: (payload) => postJson("/api/payment-config/return-codes/toggle", payload),
  getReturnCodeReviews: () => request("/api/payment-config/return-codes/reviews"),
  approveReturnCodeReview: (requestNo, payload) => postJson(
    `/api/payment-config/return-codes/reviews/${encodeURIComponent(requestNo)}/approve`,
    payload
  ),
  rejectReturnCodeReview: (requestNo, payload) => postJson(
    `/api/payment-config/return-codes/reviews/${encodeURIComponent(requestNo)}/reject`,
    payload
  ),
  previewReturnCodeImport: (payload) => postJson("/api/payment-config/return-codes/import/preview", payload),
  getReturnCodeImportReviews: () => request("/api/payment-config/return-codes/import/reviews"),
  submitReturnCodeImportReview: (payload) => postJson("/api/payment-config/return-codes/import/reviews", payload),
  approveReturnCodeImportReview: (requestNo, payload) => postJson(
    `/api/payment-config/return-codes/import/reviews/${encodeURIComponent(requestNo)}/approve`,
    payload
  ),
  rejectReturnCodeImportReview: (requestNo, payload) => postJson(
    `/api/payment-config/return-codes/import/reviews/${encodeURIComponent(requestNo)}/reject`,
    payload
  ),
  submitGatewayStatusReview: (payload) => postJson("/api/payment-config/gateways/toggle", payload),
  getGatewayReviews: () => request("/api/payment-config/gateways/reviews"),
  submitGatewayReview: (payload) => postJson("/api/payment-config/gateways/reviews", payload),
  approveGatewayReview: (requestNo, payload) => postJson(
    `/api/payment-config/gateways/reviews/${encodeURIComponent(requestNo)}/approve`,
    payload
  ),
  rejectGatewayReview: (requestNo, payload) => postJson(
    `/api/payment-config/gateways/reviews/${encodeURIComponent(requestNo)}/reject`,
    payload
  ),
  submitControlPolicyStatusReview: (payload) => postJson("/api/payment-config/control-policies/toggle", payload),
  getControlPolicyReviews: () => request("/api/payment-config/control-policies/reviews"),
  submitControlPolicyReview: (payload) => postJson("/api/payment-config/control-policies/reviews", payload),
  approveControlPolicyReview: (requestNo, payload) => postJson(
    `/api/payment-config/control-policies/reviews/${encodeURIComponent(requestNo)}/approve`,
    payload
  ),
  rejectControlPolicyReview: (requestNo, payload) => postJson(
    `/api/payment-config/control-policies/reviews/${encodeURIComponent(requestNo)}/reject`,
    payload
  ),
  getControlPolicyVersions: (sourceAppId) => request(
    `/api/payment-config/control-policies/${encodeURIComponent(sourceAppId)}/versions`
  ),
  rollbackControlPolicy: (sourceAppId, payload) => postJson(
    `/api/payment-config/control-policies/${encodeURIComponent(sourceAppId)}/rollback`,
    payload
  ),
  toggleAlertProvider: (payload) => postJson("/api/payment-config/alert-providers/toggle", payload),
  toggleIssueDutyRoster: (payload) => postJson("/api/payment-config/issue-duty-rosters/toggle", payload),
  runControlPolicySelfCheck: (configCode) => postJson("/api/payment-config/control-policies/self-check", { configCode })
};
