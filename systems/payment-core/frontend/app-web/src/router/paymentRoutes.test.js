import assert from "node:assert/strict";
import test from "node:test";
import { createMemoryHistory, createRouter } from "vue-router";
import {
  businessEntryRoutes,
  buildCashierRoute,
  buildPaymentResultRoute,
  buildRefundProgressRoute,
  resolvePaymentBusinessEntryPath,
  refundProgressRoutePath
} from "./paymentRoutes.js";

test("only links a recorded payment scene to its matching business entry", () => {
  for (const terminal of ["APP", "H5", "PC"]) {
    assert.equal(resolvePaymentBusinessEntryPath(`WALLET_RECHARGE_${terminal}`), "/recharge");
    assert.equal(resolvePaymentBusinessEntryPath(`WITHDRAW_REPAY_${terminal}`), "/withdraw");
    assert.equal(resolvePaymentBusinessEntryPath(`ACCOUNT_TRANSFER_${terminal}`), "/transfer");
    assert.equal(resolvePaymentBusinessEntryPath(`BALANCE_PAY_${terminal}`), "/balance-pay");
  }
  for (const payScene of ["HOME_CLEAN", "HOME_REPAIR", "WALLET_RECHARGE_OTHER", "constructor_APP", "", null]) {
    assert.equal(resolvePaymentBusinessEntryPath(payScene), null);
  }
});

test("keeps App business entry routes complete", () => {
  assert.deepEqual(businessEntryRoutes.map((route) => route.sceneType), [
    "recharge", "withdraw", "transfer", "balance-pay"
  ]);
});

test("keeps the refund progress route available for user terminals", () => {
  assert.equal(refundProgressRoutePath, "/refund-progress/:refundOrderId");
});

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/cashier/:prepayOrderNo", name: "cashier", component: {} },
      { path: "/payment-result/:paymentOrderId", name: "payment-result", component: {} },
      { path: "/refund-progress/:refundOrderId", name: "refund-progress", component: {} }
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

test("resolves refund identifiers as encoded route parameters", () => {
  const router = createTestRouter();
  assert.equal(
    router.resolve(buildRefundProgressRoute("REF/1?#%", { terminal: "app" })).href,
    "/refund-progress/REF%2F1%3F%23%25?terminal=app"
  );
});

test("trims route identifiers before building user payment routes", () => {
  const router = createTestRouter();
  assert.equal(router.resolve(buildCashierRoute("  PRE-1  ")).href, "/cashier/PRE-1");
  assert.equal(router.resolve(buildPaymentResultRoute("  PAY-1  ")).href, "/payment-result/PAY-1");
  assert.equal(router.resolve(buildRefundProgressRoute("  REF-1  ")).href, "/refund-progress/REF-1");
});
