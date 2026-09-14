import assert from "node:assert/strict";
import test from "node:test";
import { createMemoryHistory, createRouter } from "vue-router";
import {
  normalizePaymentRecordType,
  orderDetailRoute,
  paymentDetailRoute,
  paymentRecordDetailRoute,
  refundDetailRoute
} from "./detailRoutes.js";

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/orders/:orderNo", name: "order-detail", component: {} },
      { path: "/payments/:paymentOrderId", name: "payment-detail", component: {} },
      { path: "/payment-records/:paymentOrderId", name: "payment-record-detail", component: {} },
      { path: "/refunds/:refundOrderId", name: "refund-detail", component: {} }
    ]
  });
}

test("resolves admin detail identifiers as encoded route parameters", () => {
  const router = createTestRouter();

  assert.equal(router.resolve(orderDetailRoute("ORDER/1?#%")).href, "/orders/ORDER%2F1%3F%23%25");
  assert.equal(router.resolve(paymentDetailRoute("PAY/1?#%")).href, "/payments/PAY%2F1%3F%23%25");
  assert.equal(
    router.resolve(paymentRecordDetailRoute("PAY/1?#%", "BANK_CARD")).href,
    "/payment-records/PAY%2F1%3F%23%25?recordType=BANK_CARD"
  );
  assert.equal(router.resolve(refundDetailRoute("REF/1?#%")).href, "/refunds/REF%2F1%3F%23%25");
});

test("normalizes payment record source types before building detail routes", () => {
  const router = createTestRouter();

  assert.equal(normalizePaymentRecordType("WECHAT"), "WECHAT");
  assert.equal(normalizePaymentRecordType("BANK_CARD"), "BANK_CARD");
  assert.equal(normalizePaymentRecordType("UNKNOWN"), "ALL");
  assert.equal(normalizePaymentRecordType(["WECHAT"]), "ALL");
  assert.equal(normalizePaymentRecordType(null), "ALL");
  assert.equal(
    router.resolve(paymentRecordDetailRoute("PAY-1", "UNKNOWN")).href,
    "/payment-records/PAY-1?recordType=ALL"
  );
});
