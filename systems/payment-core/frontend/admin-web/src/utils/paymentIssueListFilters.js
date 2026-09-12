function queryString(value, fallback) {
  return typeof value === "string" && value ? value : fallback;
}

export function paymentIssueListFiltersFromQuery(query = {}) {
  return {
    paymentOrderId: queryString(query.paymentOrderId, ""),
    orderNo: queryString(query.orderNo, ""),
    issueType: queryString(query.issueType, "全部"),
    severity: queryString(query.severity, "全部"),
    channelCode: queryString(query.channelCode, ""),
    paymentMethod: queryString(query.paymentMethod, "全部")
  };
}
