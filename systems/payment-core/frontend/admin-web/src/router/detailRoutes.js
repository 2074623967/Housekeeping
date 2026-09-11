export function paymentDetailRoute(paymentOrderId) {
  return {
    name: "payment-detail",
    params: { paymentOrderId }
  };
}

export function paymentRecordDetailRoute(paymentOrderId, recordType = "ALL") {
  return {
    name: "payment-record-detail",
    params: { paymentOrderId },
    query: { recordType }
  };
}

export function refundDetailRoute(refundOrderId) {
  return {
    name: "refund-detail",
    params: { refundOrderId }
  };
}
