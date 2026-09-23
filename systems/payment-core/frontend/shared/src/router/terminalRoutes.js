export const businessEntryRoutes = [
  { path: "/recharge", sceneType: "recharge" },
  { path: "/withdraw", sceneType: "withdraw" },
  { path: "/transfer", sceneType: "transfer" },
  { path: "/balance-pay", sceneType: "balance-pay" }
];

export const cashierRoutePath = "/cashier/:prepayOrderNo";
export const paymentResultRoutePath = "/payment-result/:paymentOrderId";
export const refundProgressRoutePath = "/refund-progress/:refundOrderId";

export function buildCashierRoute(prepayOrderNo, query = {}) {
  return { name: "cashier", params: { prepayOrderNo }, query };
}

export function buildPaymentResultRoute(paymentOrderId, query = {}) {
  return { name: "payment-result", params: { paymentOrderId }, query };
}

export function buildRefundProgressRoute(refundOrderId, query = {}) {
  return { name: "refund-progress", params: { refundOrderId }, query };
}
