function listRoute(name, query) {
  return { name, query };
}

export const ordersRoute = (query) => listRoute("orders", query);
export const billsRoute = (query) => listRoute("bills", query);
export const paymentFlowsRoute = (query) => listRoute("payment-flows", query);
export const paymentRoutesRoute = (query) => listRoute("payment-routes", query);
export const paymentEventsRoute = (query) => listRoute("payment-events", query);
export const paymentIssuesRoute = (query) => listRoute("payment-issues", query);
export const paymentIssueAlertsRoute = (query) => listRoute("payment-issue-alerts", query);
export const cashierSessionsRoute = (query) => listRoute("cashier-sessions", query);
export const paymentRequestsRoute = (query) => listRoute("payment-requests", query);
export const paymentLogsRoute = (query) => listRoute("payment-logs", query);
export const paymentsRoute = (query) => listRoute("payments", query);
