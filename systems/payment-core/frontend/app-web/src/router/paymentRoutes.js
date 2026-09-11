export function buildCashierRoute(prepayOrderNo, query = {}) {
  return {
    name: "cashier",
    params: { prepayOrderNo },
    query
  };
}

export function buildPaymentResultRoute(paymentOrderId, query = {}) {
  return {
    name: "payment-result",
    params: { paymentOrderId },
    query
  };
}
