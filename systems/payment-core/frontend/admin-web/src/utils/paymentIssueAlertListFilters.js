function queryString(value, fallback) {
  return typeof value === "string" && value ? value : fallback;
}

export function paymentIssueAlertListFiltersFromQuery(query = {}) {
  return {
    alertNo: queryString(query.alertNo, ""),
    issueNo: queryString(query.issueNo, ""),
    paymentOrderId: queryString(query.paymentOrderId, ""),
    alertChannel: queryString(query.alertChannel, "全部"),
    alertStatus: queryString(query.alertStatus, "全部"),
    ackStatus: queryString(query.ackStatus, "全部"),
    providerDeliveryStatus: queryString(query.providerDeliveryStatus, "全部"),
    receiptQueryState: queryString(query.receiptQueryState, "全部")
  };
}
