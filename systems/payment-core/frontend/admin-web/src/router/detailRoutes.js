export function orderDetailRoute(orderNo) {
  return {
    name: "order-detail",
    params: { orderNo }
  };
}

export function billDetailRoute(billNo) {
  return {
    name: "bill-detail",
    params: { billNo }
  };
}

export function cashierSessionDetailRoute(sessionNo) {
  return {
    name: "cashier-session-detail",
    params: { sessionNo }
  };
}

export function paymentRequestDetailRoute(requestNo) {
  return {
    name: "payment-request-detail",
    params: { requestNo }
  };
}

export function paymentLogDetailRoute(logNo) {
  return {
    name: "payment-log-detail",
    params: { logNo }
  };
}

export function paymentEventDetailRoute(eventNo) {
  return {
    name: "payment-event-detail",
    params: { eventNo }
  };
}

export function paymentRouteDetailRoute(routeNo) {
  return {
    name: "payment-route-detail",
    params: { routeNo }
  };
}

export function paymentIssueDetailRoute(issueNo) {
  return {
    name: "payment-issue-detail",
    params: { issueNo }
  };
}

export function paymentDetailRoute(paymentOrderId) {
  return {
    name: "payment-detail",
    params: { paymentOrderId }
  };
}

export function paymentFlowDetailRoute(flowSource, flowNo) {
  return {
    name: "payment-flow-detail",
    params: { flowSource, flowNo }
  };
}

export function normalizePaymentRecordType(recordType) {
  return ["ALL", "WECHAT", "BANK_CARD"].includes(recordType) ? recordType : "ALL";
}

export function paymentRecordDetailRoute(paymentOrderId, recordType = "ALL") {
  return {
    name: "payment-record-detail",
    params: { paymentOrderId },
    query: { recordType: normalizePaymentRecordType(recordType) }
  };
}

export function refundDetailRoute(refundOrderId) {
  return {
    name: "refund-detail",
    params: { refundOrderId }
  };
}
