import assert from "node:assert/strict";
import test from "node:test";
import {
  businessEntryRoutes,
  cashierRoutePath,
  paymentResultRoutePath,
  buildCashierRoute,
  buildPaymentResultRoute
} from "../../shared/src/router/terminalRoutes.js";

test("keeps PC business entry routes complete", () => {
  assert.deepEqual(businessEntryRoutes.map((route) => route.sceneType), [
    "recharge", "withdraw", "transfer", "balance-pay"
  ]);
  assert.equal(cashierRoutePath, "/cashier/:prepayOrderNo");
  assert.equal(paymentResultRoutePath, "/payment-result/:paymentOrderId");
});

test("builds PC payment navigation descriptors", () => {
  assert.deepEqual(buildCashierRoute("PRE-1"), {
    name: "cashier",
    params: { prepayOrderNo: "PRE-1" },
    query: {}
  });
  assert.deepEqual(buildPaymentResultRoute("PAY-1", { terminal: "pc" }), {
    name: "payment-result",
    params: { paymentOrderId: "PAY-1" },
    query: { terminal: "pc" }
  });
});
