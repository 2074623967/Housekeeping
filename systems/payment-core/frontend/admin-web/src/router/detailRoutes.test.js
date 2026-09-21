import assert from "node:assert/strict";
import test from "node:test";
import { createMemoryHistory, createRouter } from "vue-router";
import {
  billDetailRoute,
  cashierSessionDetailRoute,
  normalizePaymentRecordType,
  orderDetailRoute,
  paymentDetailRoute,
  paymentEventDetailRoute,
  paymentFlowDetailRoute,
  paymentLogDetailRoute,
  paymentRequestDetailRoute,
  paymentRouteDetailRoute,
  paymentRecordDetailRoute,
  refundDetailRoute
} from "./detailRoutes.js";

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/orders/:orderNo", name: "order-detail", component: {} },
      { path: "/bills/:billNo", name: "bill-detail", component: {} },
      { path: "/cashier-sessions/:sessionNo", name: "cashier-session-detail", component: {} },
      { path: "/payment-requests/:requestNo", name: "payment-request-detail", component: {} },
      { path: "/payments/:paymentOrderId", name: "payment-detail", component: {} },
      { path: "/payment-flows/:flowSource/:flowNo", name: "payment-flow-detail", component: {} },
      { path: "/payment-routes/:routeNo", name: "payment-route-detail", component: {} },
      { path: "/payment-logs/:logNo", name: "payment-log-detail", component: {} },
      { path: "/payment-events/:eventNo", name: "payment-event-detail", component: {} },
      { path: "/payment-records/:paymentOrderId", name: "payment-record-detail", component: {} },
      { path: "/refunds/:refundOrderId", name: "refund-detail", component: {} }
    ]
  });
}

test("resolves admin detail identifiers as encoded route parameters", () => {
  const router = createTestRouter();

  assert.equal(router.resolve(orderDetailRoute("ORDER/1?#%")).href, "/orders/ORDER%2F1%3F%23%25");
  assert.equal(router.resolve(billDetailRoute("BILL/1?#%")).href, "/bills/BILL%2F1%3F%23%25");
  assert.equal(
    router.resolve(cashierSessionDetailRoute("PRE/1?#%")).href,
    "/cashier-sessions/PRE%2F1%3F%23%25"
  );
  assert.equal(router.resolve(paymentDetailRoute("PAY/1?#%")).href, "/payments/PAY%2F1%3F%23%25");
  assert.equal(
    router.resolve(paymentFlowDetailRoute("route/source", "FLOW/1?#%")).href,
    "/payment-flows/route%2Fsource/FLOW%2F1%3F%23%25"
  );
  assert.equal(
    router.resolve(paymentRequestDetailRoute("REQ/1?#%")).href,
    "/payment-requests/REQ%2F1%3F%23%25"
  );
  assert.equal(
    router.resolve(paymentRouteDetailRoute("ROUTE/1?#%")).href,
    "/payment-routes/ROUTE%2F1%3F%23%25"
  );
  assert.equal(
    router.resolve(paymentLogDetailRoute("LOG/1?#%")).href,
    "/payment-logs/LOG%2F1%3F%23%25"
  );
  assert.equal(
    router.resolve(paymentEventDetailRoute("EVT/1?#%")).href,
    "/payment-events/EVT%2F1%3F%23%25"
  );
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
