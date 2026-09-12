import assert from "node:assert/strict";
import test from "node:test";
import { createMemoryHistory, createRouter } from "vue-router";
import { billsRoute, paymentLogsRoute, paymentsRoute } from "./listRoutes.js";

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/bills", name: "bills", component: {} },
      { path: "/payment-logs", name: "payment-logs", component: {} },
      { path: "/payments", name: "payments", component: {} }
    ]
  });
}

test("keeps admin list filters in structured query fields", () => {
  const router = createTestRouter();
  const paymentOrderId = "PAY/1?#%&=";
  const resolvedLogs = router.resolve(paymentLogsRoute({ paymentOrderId }));
  const resolvedBills = router.resolve(billsRoute({ billNo: "BILL&1", orderNo: "ORDER#1" }));
  const resolvedPayments = router.resolve(paymentsRoute({
    channelCode: "wx_h5",
    paymentMethod: "微信支付",
    status: "WAIT_CALLBACK"
  }));

  assert.equal(resolvedLogs.query.paymentOrderId, paymentOrderId);
  assert.equal(resolvedLogs.href, "/payment-logs?paymentOrderId=PAY/1?%23%25%26=");
  assert.deepEqual(resolvedBills.query, { billNo: "BILL&1", orderNo: "ORDER#1" });
  assert.equal(resolvedBills.href, "/bills?billNo=BILL%261&orderNo=ORDER%231");
  assert.deepEqual(resolvedPayments.query, {
    channelCode: "wx_h5",
    paymentMethod: "微信支付",
    status: "WAIT_CALLBACK"
  });
});
