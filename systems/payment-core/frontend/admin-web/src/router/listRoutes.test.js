import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { createMemoryHistory, createRouter } from "vue-router";
import { billsRoute, cashierSessionsRoute, paymentIssuesRoute, paymentLogsRoute, paymentRequestsRoute, paymentsRoute, refundsRoute } from "./listRoutes.js";

test("registers the refund list name used by real payment operations drill-downs", () => {
  const source = readFileSync(new URL("./index.js", import.meta.url), "utf8");
  assert.match(source, /path: "refunds", name: "refunds", component: RefundsView/);
});

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/bills", name: "bills", component: {} },
      { path: "/payment-logs", name: "payment-logs", component: {} },
      { path: "/payment-requests", name: "payment-requests", component: {} },
      { path: "/cashier-sessions", name: "cashier-sessions", component: {} },
      { path: "/payments", name: "payments", component: {} },
      { path: "/refunds", name: "refunds", component: {} },
      { path: "/payment-issues", name: "payment-issues", component: {} }
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

test("keeps the blocked failed refund status group in the refund list route", () => {
  const router = createTestRouter();

  const resolved = router.resolve(refundsRoute({ refundStatus: "TERMINAL_FACT_MISMATCH" }));

  assert.deepEqual(resolved.query, { refundStatus: "TERMINAL_FACT_MISMATCH" });
  assert.equal(resolved.href, "/refunds?refundStatus=TERMINAL_FACT_MISMATCH");
});

test("keeps unverified successful refunds scoped to the original payment", () => {
  const router = createTestRouter();
  const unverified = router.resolve(refundsRoute({ paymentOrderId: "PAY-001", refundStatus: "UNVERIFIED_SUCCESS" }));
  assert.deepEqual(unverified.query, { paymentOrderId: "PAY-001", refundStatus: "UNVERIFIED_SUCCESS" });
  assert.equal(unverified.href, "/refunds?paymentOrderId=PAY-001&refundStatus=UNVERIFIED_SUCCESS");
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

test("keeps expired cashier session drilldowns in the session list route", () => {
  const router = createTestRouter();

  const resolved = router.resolve(cashierSessionsRoute({ sessionStatus: "已失效" }));

  assert.deepEqual(resolved.query, { sessionStatus: "已失效" });
  assert.equal(resolved.href, "/cashier-sessions?sessionStatus=%E5%B7%B2%E5%A4%B1%E6%95%88");
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

test("keeps the unknown refund submit issue drilldown in the issue list route", () => {
  const router = createTestRouter();

  const resolved = router.resolve(paymentIssuesRoute({ issueType: "退款提交结果待核查" }));

  assert.deepEqual(resolved.query, { issueType: "退款提交结果待核查" });
  assert.equal(resolved.href, "/payment-issues?issueType=%E9%80%80%E6%AC%BE%E6%8F%90%E4%BA%A4%E7%BB%93%E6%9E%9C%E5%BE%85%E6%A0%B8%E6%9F%A5");
});
