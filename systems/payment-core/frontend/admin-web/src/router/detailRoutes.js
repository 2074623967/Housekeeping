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

export function paymentDetailRoute(paymentOrderId) {
  return {
    name: "payment-detail",
    params: { paymentOrderId }
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
