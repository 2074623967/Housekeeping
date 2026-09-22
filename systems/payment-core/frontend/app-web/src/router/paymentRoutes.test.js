import assert from "node:assert/strict";
import test from "node:test";
import { createMemoryHistory, createRouter } from "vue-router";
import {
  businessEntryRoutes,
  buildCashierRoute,
  buildPaymentResultRoute
} from "./paymentRoutes.js";

test("keeps App business entry routes complete", () => {
  assert.deepEqual(businessEntryRoutes.map((route) => route.sceneType), [
    "recharge", "withdraw", "transfer", "balance-pay"
  ]);
});

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/cashier/:prepayOrderNo", name: "cashier", component: {} },
      { path: "/payment-result/:paymentOrderId", name: "payment-result", component: {} }
    ]
  });
}

test("resolves payment identifiers as encoded route parameters", () => {
  const router = createTestRouter();

  assert.equal(
    router.resolve(buildCashierRoute("PRE/1?#%", { bizType: "balance-pay" })).href,
    "/cashier/PRE%2F1%3F%23%25?bizType=balance-pay"
  );
  assert.equal(
    router.resolve(buildPaymentResultRoute("PAY/1?#%", { prepayOrderNo: "PRE/1?#%" })).href,
    "/payment-result/PAY%2F1%3F%23%25?prepayOrderNo=PRE/1?%23%25"
  );
});
