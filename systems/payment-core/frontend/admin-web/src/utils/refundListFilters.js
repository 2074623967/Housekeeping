function queryString(value, fallback) {
  return typeof value === "string" && value ? value : fallback;
}

export function refundListFiltersFromQuery(query = {}) {
  return {
    refundOrderId: queryString(query.refundOrderId, ""),
    paymentOrderId: queryString(query.paymentOrderId, ""),
    refundStatus: queryString(query.refundStatus, "全部"),
    refundMethod: queryString(query.refundMethod, "全部")
  };
}
