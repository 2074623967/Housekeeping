export const businessEntryRoutes = [
  { path: "/recharge", sceneType: "recharge" },
  { path: "/withdraw", sceneType: "withdraw" },
  { path: "/transfer", sceneType: "transfer" },
  { path: "/balance-pay", sceneType: "balance-pay" }
];

export const cashierRoutePath = "/cashier/:prepayOrderNo";
export const paymentResultRoutePath = "/payment-result/:paymentOrderId";
export const refundProgressRoutePath = "/refund-progress/:refundOrderId";

const entryPathsByPayScene = new Map([
  ["WALLET_RECHARGE", "/recharge"],
  ["WITHDRAW_REPAY", "/withdraw"],
  ["ACCOUNT_TRANSFER", "/transfer"],
  ["BALANCE_PAY", "/balance-pay"]
]);

export function resolvePaymentBusinessEntryPath(payScene) {
  if (typeof payScene !== "string") {
    return null;
  }
  const matchedScene = /^(.+)_(APP|H5|PC)$/.exec(payScene.trim());
  return matchedScene ? entryPathsByPayScene.get(matchedScene[1]) || null : null;
}

function normalizeRouteIdentifier(value) {
  return typeof value === "string" ? value.trim() : value;
}

export function buildCashierRoute(prepayOrderNo, query = {}) {
  return { name: "cashier", params: { prepayOrderNo: normalizeRouteIdentifier(prepayOrderNo) }, query };
}

export function buildPaymentResultRoute(paymentOrderId, query = {}) {
  return { name: "payment-result", params: { paymentOrderId: normalizeRouteIdentifier(paymentOrderId) }, query };
}

export function buildRefundProgressRoute(refundOrderId, query = {}) {
  return { name: "refund-progress", params: { refundOrderId: normalizeRouteIdentifier(refundOrderId) }, query };
}
