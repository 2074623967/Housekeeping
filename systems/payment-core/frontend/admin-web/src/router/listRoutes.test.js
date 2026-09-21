import assert from "node:assert/strict";
import test from "node:test";
import { createMemoryHistory, createRouter } from "vue-router";
import { billsRoute, paymentLogsRoute, paymentRequestsRoute, paymentsRoute, refundsRoute } from "./listRoutes.js";

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/bills", name: "bills", component: {} },
      { path: "/payment-logs", name: "payment-logs", component: {} },
      { path: "/payment-requests", name: "payment-requests", component: {} },
      { path: "/payments", name: "payments", component: {} },
      { path: "/refunds", name: "refunds", component: {} }
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

test("keeps the processing refund status group in the refund list route", () => {
  const router = createTestRouter();

  const resolved = router.resolve(refundsRoute({ refundStatus: "PROCESSING" }));

  assert.deepEqual(resolved.query, { refundStatus: "PROCESSING" });
  assert.equal(resolved.href, "/refunds?refundStatus=PROCESSING");
});

test("keeps structured payment log risk filters in the log list route", () => {
  const router = createTestRouter();

  const resolved = router.resolve(paymentLogsRoute({ processStage: "渠道回调", logLevel: "ERROR" }));

  assert.deepEqual(resolved.query, { processStage: "渠道回调", logLevel: "ERROR" });
  assert.equal(resolved.href, "/payment-logs?processStage=%E6%B8%A0%E9%81%93%E5%9B%9E%E8%B0%83&logLevel=ERROR");
});

test("keeps payment request status drilldowns in the request list route", () => {
  const router = createTestRouter();

  const resolved = router.resolve(paymentRequestsRoute({ requestStatus: "等待回调" }));

  assert.deepEqual(resolved.query, { requestStatus: "等待回调" });
  assert.equal(resolved.href, "/payment-requests?requestStatus=%E7%AD%89%E5%BE%85%E5%9B%9E%E8%B0%83");
});

test("keeps the day-end pending payment status group in the payment list route", () => {
  const router = createTestRouter();

  const resolved = router.resolve(paymentsRoute({ status: "DAY_END_PENDING" }));

  assert.deepEqual(resolved.query, { status: "DAY_END_PENDING" });
  assert.equal(resolved.href, "/payments?status=DAY_END_PENDING");
});

test("keeps the missing success-time status group in the payment list route", () => {
  const router = createTestRouter();

  const resolved = router.resolve(paymentsRoute({ status: "SUCCESS_TIME_MISSING" }));

  assert.deepEqual(resolved.query, { status: "SUCCESS_TIME_MISSING" });
  assert.equal(resolved.href, "/payments?status=SUCCESS_TIME_MISSING");
});

test("keeps the payment success-fact mismatch group in the payment list route", () => {
  const router = createTestRouter();

  const resolved = router.resolve(paymentsRoute({ status: "SUCCESS_FACT_MISMATCH" }));

  assert.deepEqual(resolved.query, { status: "SUCCESS_FACT_MISMATCH" });
  assert.equal(resolved.href, "/payments?status=SUCCESS_FACT_MISMATCH");
});
