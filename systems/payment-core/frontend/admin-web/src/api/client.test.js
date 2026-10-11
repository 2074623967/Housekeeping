import test from "node:test";
import assert from "node:assert/strict";
import {
  dashboardApi,
  normalizePagePayload,
  normalizeObjectPayload,
  normalizeDayEndRunResult,
  normalizePaymentDayEndRunResult,
  normalizeControlPolicySelfCheckResult,
  normalizeRouteRuleValidationResult,
  normalizeReturnCodeImportPreviewResult,
  normalizePaymentMonitorOverviewResult,
  normalizePaymentTaskCenterOverviewResult,
  normalizeBlockedExpiredPaymentsPage,
  normalizeExpiredActiveSubmitTokensPage,
  normalizePaymentDayEndOverviewResult,
  normalizePaymentDayEndRecentBatchResult,
  normalizePaymentMetricsResult,
  normalizePaymentEventOverviewResult,
  normalizePaymentRequestOverviewResult,
  normalizePaymentLogOverviewResult,
  normalizePaymentRouteOverviewResult,
  normalizeCashierSessionOverviewResult,
  normalizeBillOverviewResult,
  normalizeWorkerSettlementOverviewResult,
  normalizeWorkerSettlementListPage,
  normalizeDashboardSummaryResult,
  normalizePaymentActionResult,
  normalizePaymentDetailResult,
  normalizePaymentListPage,
  matchesPaymentDetailSnapshot,
  normalizePaymentRecordDetailResult,
  normalizePaymentRecordListPage,
  matchesPaymentRecordDetailSnapshot,
  matchesCashierSessionDetailSnapshot,
  normalizePaymentEventDetailResult,
  normalizePaymentEventListPage,
  normalizePaymentEventRepublishResult,
  matchesPaymentEventDetailSnapshot,
  normalizePaymentRequestDetailResult,
  normalizePaymentRequestListPage,
  matchesPaymentRequestDetailSnapshot,
  normalizePaymentFlowDetailResult,
  normalizePaymentFlowListPage,
  matchesPaymentFlowDetailSnapshot,
  normalizePaymentRouteDetailResult,
  normalizePaymentRouteListPage,
  matchesPaymentRouteDetailSnapshot,
  normalizeCashierSessionListPage,
  normalizeCashierSessionDetailResult,
  normalizeOrderDetailResult,
  normalizeOrderListPage,
  matchesOrderDetailSnapshot,
  normalizeBillDetailResult,
  normalizeBillListPage,
  matchesBillDetailSnapshot,
  normalizePaymentIssueDetailResult,
  normalizePaymentIssueListPage,
  matchesPaymentIssueDetailSnapshot,
  normalizePaymentIssueAlertDetailResult,
  normalizePaymentIssueAlertListPage,
  matchesPaymentIssueAlertDetailSnapshot,
  normalizePaymentIssueAlertActionResult,
  normalizePaymentIssueResponsibilitySummaryResult,
  normalizePaymentIssueBatchActionResult,
  normalizePaymentLogDetailResult,
  normalizePaymentLogListPage,
  matchesPaymentLogDetailSnapshot,
  normalizePaymentTaskRunDetailResult,
  normalizePaymentTaskRunsPage,
  matchesPaymentTaskRunDetailSnapshot,
  normalizePaymentDeadLetterTaskDetailResult,
  normalizePaymentDeadLetterTaskListPage,
  matchesPaymentDeadLetterTaskDetailSnapshot,
  normalizePaymentDeadLetterTaskActionResult,
  normalizePaymentDayEndDetailResult,
  matchesPaymentDayEndDetailSnapshot,
  normalizePaymentConfigReviewActionResult,
  normalizePaymentConfigReviewListResult,
  normalizePaymentConfigOverviewResult,
  normalizePaymentConfigVersionsResult,
  normalizePaymentConfigChannelRollbackResult,
  normalizePaymentConfigChannelReviewSubmitResult,
  normalizePaymentConfigGatewayRollbackResult,
  normalizePaymentConfigGatewayReviewSubmitResult,
  normalizePaymentConfigGatewayReviewApplicationResult,
  normalizePaymentConfigProtocolRollbackResult,
  normalizePaymentConfigProtocolReviewSubmitResult,
  normalizePaymentConfigProtocolReviewApplicationResult,
  normalizePaymentConfigControlPolicyRollbackResult,
  normalizePaymentConfigControlPolicyReviewSubmitResult,
  normalizePaymentConfigControlPolicyReviewApplicationResult,
  normalizePaymentConfigAlertProviderReviewSubmitResult,
  normalizePaymentConfigIssueDutyRosterReviewSubmitResult,
  normalizePaymentConfigIssueDutyRosterReviewApplicationResult,
  normalizePaymentConfigIssueDutyRosterReviewUpdateResult,
  normalizePaymentConfigAlertProviderReviewApplicationResult,
  normalizePaymentConfigAlertProviderReviewUpdateResult,
  normalizePaymentConfigReturnCodeReviewSubmitResult,
  normalizePaymentConfigReturnCodeImportReviewSubmitResult,
  normalizePaymentConfigRouteRuleRollbackResult,
  normalizePaymentConfigRouteRuleReviewSubmitResult,
  normalizePaymentRouteRuleConflictsResult,
  normalizePaymentPrepayResult,
  normalizeRefundActionResult,
  matchesRefundActionSnapshot,
  normalizeRefundDetailResult,
  normalizeRefundListPage,
  normalizeTaskActionResult,
  mergePaymentTaskCenterActionOverview,
  requireNonEmptyCollection,
  requireObjectResult,
  requirePageResult,
  orderApi,
  billApi,
  paymentDayEndApi,
  paymentConfigApi,
  paymentDeadLetterTaskApi,
  paymentEventApi,
  paymentFlowApi,
  paymentIssueApi,
  paymentLogApi,
  paymentMonitorApi,
  paymentApi,
  paymentRecordApi,
  paymentRequestApi,
  paymentRouteApi,
  paymentTaskCenterApi,
  cashierSessionApi,
  refundApi,
  settlementApi
} from "./client.js";

test("rejects unverified blocked expired payment pages and accepts bounded facts", () => {
  const item = {
    paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", orderNo: "ORD-001",
    expiresAt: "2026-09-26 10:00:00"
  };
  const page = { items: [item], total: 1, pageNo: 1, pageSize: 10 };
  assert.deepEqual(normalizeBlockedExpiredPaymentsPage(page, 1, 10), page);
  for (const invalid of [
    { ...page, total: 0 },
    { ...page, pageNo: 2 },
    { ...page, items: [{ ...item, paymentOrderId: "" }] },
    { ...page, items: [{ ...item, expiresAt: "invalid" }] },
    { ...page, items: null }
  ]) {
    assert.throws(() => normalizeBlockedExpiredPaymentsPage(invalid, 1, 10),
      /PAYMENT-CLIENT-INVALID-BLOCKED-EXPIRED-PAYMENTS/);
  }
});

test("accepts historical expired tokens but rejects secret fields and invalid pages", () => {
  const item = {
    paymentOrderId: "PAY-ORPHAN", prepayOrderNo: "PRE-ORPHAN", sourceAppId: "APP-A",
    expireAt: "2026-09-26 10:00:00"
  };
  const page = { items: [item], total: 1, pageNo: 1, pageSize: 10 };
  assert.deepEqual(normalizeExpiredActiveSubmitTokensPage(page, 1, 10), page);
  for (const invalid of [
    { ...page, total: 0 },
    { ...page, pageSize: 20 },
    { ...page, items: [{ ...item, holderIdempotencyKey: "secret" }] },
    { ...page, items: [{ ...item, expireAt: "invalid" }] },
    { ...page, items: [null] }
  ]) {
    assert.throws(() => normalizeExpiredActiveSubmitTokensPage(invalid, 1, 10),
      /PAYMENT-CLIENT-INVALID-EXPIRED-ACTIVE-TOKENS/);
  }
});

test("normalizes a null paged items field to an empty array", () => {
  const payload = normalizePagePayload({ items: null, total: 0, pageNo: 1, pageSize: 20 });

  assert.deepEqual(payload.items, []);
  assert.equal(payload.total, 0);
});

test("rejects malformed paged responses instead of showing an empty page", () => {
  for (const payload of [
    { items: {}, total: 0 },
    { items: 1, total: 0 },
    { total: 0 }
  ]) {
    assert.throws(
      () => normalizePaymentListPage(payload),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAGE"
    );
  }
  assert.deepEqual(normalizePaymentListPage(null).items, []);
  assert.deepEqual(normalizePaymentListPage({ items: null, total: 0 }).items, []);
});

test("rejects task action responses without authoritative counters", async (context) => {
  for (const field of ["taskCode", "taskName", "summaryComment"]) {
    assert.throws(
      () => normalizeTaskActionResult({
        taskCode: "TASK-CODE",
        taskName: "任务名称",
        summaryComment: "任务已完成",
        processedCount: 1,
        successCount: 1,
        warningCount: 0,
        failCount: 0,
        [field]: ""
      }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-ACTION"
    );
  }
  assert.throws(
    () => normalizeTaskActionResult(null),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-ACTION"
  );
  assert.throws(
    () => normalizeTaskActionResult({ taskCode: "TASK-CODE", taskName: "任务名称", summaryComment: "任务已完成", processedCount: 1, successCount: 1, warningCount: 1, failCount: 0 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-ACTION"
  );
  assert.throws(
    () => normalizeTaskActionResult({ taskCode: "TASK-CODE", taskName: "任务名称", summaryComment: "任务已完成", processedCount: 2, successCount: 1, warningCount: 0, failCount: 0 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-ACTION"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: {} })
  }));

  await assert.rejects(
    paymentTaskCenterApi.runCloseExpiredPayments({ operator: "运营" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-ACTION"
  );
});

test("binds task action responses to the requested task code", () => {
  const action = {
    taskCode: "PAYMENT_EXPIRE_CLOSE",
    taskName: "支付超时关单",
    summaryComment: "任务已完成",
    processedCount: 1,
    successCount: 1,
    warningCount: 0,
    failCount: 0
  };
  assert.deepEqual(
    normalizeTaskActionResult(action, "PAYMENT_EXPIRE_CLOSE"),
    action
  );
  assert.throws(
    () => normalizeTaskActionResult(action, "PAYMENT_PENDING_QUERY"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-ACTION"
  );
});

test("preserves task center overview collections when an action omits its overview", () => {
  const currentOverview = {
    queryTrends: [{ timeBucket: "10:00" }],
    focusAlerts: [{ alertType: "PENDING_QUERY" }],
    recentTaskRuns: [{ taskLogNo: "TASK-001" }],
    dueExpiredPaymentCount: 2
  };
  assert.deepEqual(
    mergePaymentTaskCenterActionOverview(currentOverview, { processedCount: 1 }),
    currentOverview
  );
  assert.deepEqual(
    mergePaymentTaskCenterActionOverview(currentOverview, {
      overview: { ...currentOverview, dueExpiredPaymentCount: 0 }
    }),
    { ...currentOverview, dueExpiredPaymentCount: 0 }
  );
});

test("rejects refund actions that do not confirm the requested refund order", () => {
  assert.doesNotThrow(() => normalizeRefundActionResult(
    { refundOrderId: " REF-001 ", paymentOrderId: " PAY-001 ", businessChainId: "CHAIN-PAY-001", orderNo: " ORDER-001 ", paidAmount: "68.00", refundAmount: "18.00", status: "PROCESSING", statusType: "warn", appliedAt: "2026-09-27 10:00:00", successAt: null },
    "REF-001",
    "PAY-001"
  ));
  assert.deepEqual(
    normalizeRefundActionResult(
      { refundOrderId: "REF-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", orderNo: "ORDER-001", paidAmount: "68.00", refundAmount: "18.00", status: "PROCESSING", statusType: "warn", appliedAt: "2026-09-27 10:00:00", successAt: null },
      "REF-001",
      "PAY-001"
    ),
    { refundOrderId: "REF-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", orderNo: "ORDER-001", paidAmount: "68.00", refundAmount: "18.00", status: "PROCESSING", statusType: "warn", appliedAt: "2026-09-27 10:00:00", successAt: null }
  );
  assert.throws(
    () => normalizeRefundActionResult({
      refundOrderId: "REF-001",
      paymentOrderId: "PAY-001",
      businessChainId: "CHAIN-PAY-001",
      orderNo: "ORDER-001",
      paidAmount: "17.99",
      refundAmount: "18.00",
      status: "PROCESSING",
      statusType: "warn",
      appliedAt: "2026-09-27 10:00:00",
      successAt: null
    }, "REF-001", "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-ACTION"
  );
  assert.throws(
    () => normalizeRefundActionResult({
      refundOrderId: "REF-001",
      paymentOrderId: "PAY-001",
      businessChainId: "CHAIN-PAY-001",
      orderNo: "ORDER-001",
      paidAmount: "68.00",
      refundAmount: "18.00",
      status: "SUCCESS",
      statusType: "success",
      channelCode: "WX_H5",
      channelRefundNo: "CH-REF-001",
      appliedAt: "2026-09-27 10:00:00",
      successAt: "2026-09-27 10:01:00",
      terminalAt: "2026-09-27 10:02:00"
    }, "REF-001", "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-ACTION"
  );
  assert.throws(
    () => normalizeRefundActionResult({
      refundOrderId: "REF-001",
      paymentOrderId: "PAY-001",
      businessChainId: "CHAIN-PAY-001",
      orderNo: "ORDER-001",
      paidAmount: "68.00",
      refundAmount: "68.01",
      status: "PROCESSING",
      statusType: "warn",
      appliedAt: "2026-09-27 10:00:00",
      successAt: null
    }, "REF-001", "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-ACTION"
  );
  assert.throws(
    () => normalizeRefundActionResult(
      { refundOrderId: "REF-002", paymentOrderId: "PAY-001", refundAmount: "18.00", status: "SUCCESS", statusType: "success" },
      "REF-001",
      "PAY-001"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-ACTION"
  );
  assert.throws(
    () => normalizeRefundActionResult(
      { refundOrderId: "REF-001", paymentOrderId: "PAY-002", refundAmount: "18.00", status: "REVIEWING", statusType: "warn" },
      "REF-001",
      "PAY-001"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-ACTION"
  );
  assert.throws(
    () => normalizeRefundActionResult({ refundOrderId: "REF-001", status: "REVIEWING", statusType: "warn" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-ACTION"
  );
  for (const invalidResult of [
    { refundOrderId: "REF-001", paymentOrderId: "PAY-001", refundAmount: "18.00", status: "REVIEWING" },
    { refundOrderId: "REF-001", paymentOrderId: "PAY-001", refundAmount: "18.00", status: "REVIEWING", statusType: "invalid" },
    { refundOrderId: "REF-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", refundAmount: "18.00", status: "SUCCESS", statusType: "warn", appliedAt: "2026-09-27 10:00:00", successAt: "2026-09-27 10:01:00" },
    { refundOrderId: "REF-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", refundAmount: "18.00", status: "PROCESSING", statusType: "warn", successAt: "2026-09-27 10:01:00" },
    { refundOrderId: "REF-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", refundAmount: "18.00", status: "SUCCESS", statusType: "success", appliedAt: "2026-09-27 10:00:00" },
    { refundOrderId: "REF-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", refundAmount: "18.00", status: "PROCESSING", statusType: "warn", appliedAt: "2026-02-30 10:00:00" },
    { refundOrderId: "REF-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", refundAmount: "18.00", status: "SUCCESS", statusType: "success", appliedAt: "2026-09-27 10:00:00", successAt: "2026-09-27 09:59:59" },
    { refundOrderId: "REF-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", refundAmount: "18.00", status: "PROCESSING", statusType: "warn", appliedAt: "2026-09-27 10:00:00", channelSubmittedAt: "not-a-time" },
    { refundOrderId: "REF-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", refundAmount: "18.00", status: "PROCESSING", statusType: "warn", appliedAt: "2026-09-27 10:00:00", channelSubmittedAt: "2026-09-27 09:59:59" }
  ]) {
    assert.throws(
      () => normalizeRefundActionResult(invalidResult, "REF-001", "PAY-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-ACTION"
    );
  }
});

test("preserves terminal times for terminal refund actions and rejects invalid values", () => {
  for (const status of ["SUCCESS", "FAIL"]) {
    const terminalAt = "2026-09-27 10:02:00";
    const result = normalizeRefundActionResult({
      refundOrderId: "REF-001",
      paymentOrderId: "PAY-001",
      businessChainId: "CHAIN-PAY-001",
      orderNo: "ORDER-001",
      paidAmount: "68.00",
      refundAmount: "18.00",
      status,
      statusType: status === "SUCCESS" ? "success" : "danger",
      channelCode: "WX_H5",
      channelRefundNo: "CH-REF-001",
      channelSubmittedAt: "2026-09-27 10:00:30",
      appliedAt: "2026-09-27 10:00:00",
      successAt: status === "SUCCESS" ? "2026-09-27 10:01:00" : null,
      terminalAt
    }, "REF-001", "PAY-001");
    assert.equal(result.terminalAt, terminalAt);
  }
  assert.throws(
    () => normalizeRefundActionResult({
      refundOrderId: "REF-001",
      paymentOrderId: "PAY-001",
      businessChainId: "CHAIN-PAY-001",
      orderNo: "ORDER-001",
      paidAmount: "68.00",
      refundAmount: "18.00",
      status: "SUCCESS",
      statusType: "success",
      channelCode: "WX_H5",
      channelRefundNo: "CH-REF-001",
      appliedAt: "2026-09-27 10:00:00",
      successAt: "2026-09-27 10:01:00",
      terminalAt: "2026-09-27 10:02:00"
    }, "REF-001", "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-ACTION"
  );
  for (const terminalAt of ["not-a-time", "2026-09-27 09:59:59", null]) {
    assert.throws(
      () => normalizeRefundActionResult({
        refundOrderId: "REF-001",
        paymentOrderId: "PAY-001",
        businessChainId: "CHAIN-PAY-001",
        orderNo: "ORDER-001",
        paidAmount: "68.00",
        refundAmount: "18.00",
        status: "SUCCESS",
        statusType: "success",
        channelCode: "WX_H5",
        channelRefundNo: "CH-REF-001",
        channelSubmittedAt: "2026-09-27 10:00:30",
        appliedAt: "2026-09-27 10:00:00",
        successAt: "2026-09-27 10:01:00",
        terminalAt
      }, "REF-001", "PAY-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-ACTION"
    );
  }
});

test("requires refund actions to preserve the loaded payment and amount snapshot", () => {
  const snapshot = {
    refundOrderId: "REF-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    customerName: "张三",
    paymentMethod: "微信支付",
    payScene: "HOME_SERVICE",
    refundAmount: "18.00",
    channelCode: "WX_H5",
    channelRefundNo: "CH-REF-001"
  };
  assert.equal(matchesRefundActionSnapshot(snapshot, { ...snapshot, refundAmount: "¥18.00" }), true);
  assert.equal(matchesRefundActionSnapshot(snapshot, { ...snapshot, payScene: "OTHER_SCENE" }), false);
  const detailedSnapshot = {
    ...snapshot,
    paidAmount: "68.00",
    refundMethod: "原路退款",
    refundReason: "客户申请"
  };
  assert.equal(matchesRefundActionSnapshot(detailedSnapshot, {
    ...detailedSnapshot,
    paidAmount: "¥68.00"
  }), true);
  assert.equal(matchesRefundActionSnapshot(detailedSnapshot, {
    ...detailedSnapshot,
    customerName: "李四"
  }), false);
  assert.equal(matchesRefundActionSnapshot(detailedSnapshot, {
    ...detailedSnapshot,
    paymentMethod: "支付宝"
  }), false);
  assert.equal(matchesRefundActionSnapshot(detailedSnapshot, {
    ...detailedSnapshot,
    paymentMethod: undefined
  }), true);
  assert.equal(matchesRefundActionSnapshot(detailedSnapshot, {
    ...detailedSnapshot,
    paidAmount: "67.99"
  }), false);
  assert.equal(matchesRefundActionSnapshot(detailedSnapshot, {
    ...detailedSnapshot,
    refundMethod: "余额退款"
  }), false);
  assert.equal(matchesRefundActionSnapshot(detailedSnapshot, {
    ...detailedSnapshot,
    refundReason: "商户原因"
  }), false);
  assert.equal(matchesRefundActionSnapshot(detailedSnapshot, {
    ...detailedSnapshot,
    businessChainId: "CHAIN-OTHER"
  }), false);
  assert.equal(matchesRefundActionSnapshot(detailedSnapshot, {
    ...detailedSnapshot,
    channelCode: "ALI_H5"
  }), false);
  assert.equal(matchesRefundActionSnapshot(detailedSnapshot, {
    ...detailedSnapshot,
    channelRefundNo: "CH-REF-002"
  }), false);
  assert.equal(matchesRefundActionSnapshot(snapshot, {
    ...snapshot,
    refundOrderId: " REF-001 ",
    paymentOrderId: " PAY-001 ",
    orderNo: " ORDER-001 "
  }), true);
  assert.equal(matchesRefundActionSnapshot(snapshot, { ...snapshot, paymentOrderId: "PAY-002" }), false);
  assert.equal(matchesRefundActionSnapshot(snapshot, { ...snapshot, orderNo: "ORDER-002" }), false);
  assert.equal(matchesRefundActionSnapshot(snapshot, { ...snapshot, refundAmount: "17.99" }), false);
  assert.equal(
    matchesRefundActionSnapshot(
      { ...snapshot, refundAmount: "9007199254740992.01" },
      { ...snapshot, refundAmount: "9007199254740992.02" }
    ),
    false
  );
});

test("binds refund action normalization to the loaded business snapshot", () => {
  const snapshot = {
    refundOrderId: "REF-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    customerName: "张三",
    paymentMethod: "微信支付",
    refundAmount: "18.00",
    paidAmount: "68.00",
    refundMethod: "原路退款",
    refundReason: "客户申请",
    channelCode: "WX_H5",
    channelRefundNo: "CH-REF-001"
  };
  const result = {
    ...snapshot,
    status: "PROCESSING",
    statusType: "warn",
    appliedAt: "2026-09-27 10:00:00",
    successAt: null
  };
  assert.deepEqual(
    normalizeRefundActionResult(result, "REF-001", "PAY-001", "18.00", snapshot),
    result
  );
  assert.throws(
    () => normalizeRefundActionResult(
      { ...result, paymentMethod: "支付宝" },
      "REF-001", "PAY-001", "18.00", snapshot
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-ACTION"
  );
  assert.throws(
    () => normalizeRefundActionResult(
      { ...result, customerName: "李四" },
      "REF-001", "PAY-001", "18.00", snapshot
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-ACTION"
  );
});

test("rejects refund apply responses attached to another payment order", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({
      code: "0",
      data: { refundOrderId: "REF-001", paymentOrderId: "PAY-002", refundAmount: "18.00", status: "REVIEWING", statusType: "warn", appliedAt: "2026-09-27 10:00:00", successAt: null }
    })
  }));

  await assert.rejects(
    refundApi.apply({ paymentOrderId: "PAY-001", refundAmount: "18.00" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-ACTION"
  );
});

test("rejects refund apply responses with a mismatched amount", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({
      code: "0",
      data: { refundOrderId: "REF-001", paymentOrderId: "PAY-001", refundAmount: "17.99", status: "REVIEWING", statusType: "warn", appliedAt: "2026-09-27 10:00:00", successAt: null }
    })
  }));

  await assert.rejects(
    refundApi.apply({ paymentOrderId: "PAY-001", refundAmount: "18.00" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-ACTION"
  );
});

test("rejects payment actions that do not confirm the requested payment order", () => {
  assert.doesNotThrow(() => normalizePaymentActionResult({
    paymentOrderId: " PAY-001 ",
    prepayOrderNo: " PRE-001 ",
    businessChainId: "CHAIN-PAY-001",
    orderNo: " ORDER-001 ",
    amount: "68.00",
    status: "SUCCESS",
    statusType: "success",
    channel: "WX_H5",
    channelTransactionNo: "WX-001",
    successAt: "2026-09-27 10:01:00",
    createdAt: "2026-09-27 10:00:00",
    latestAttemptStatus: "成功",
    latestAttemptStatusType: "success",
    latestAttemptCompletedAt: "2026-09-27 10:00:30"
  }, "PAY-001", "ORDER-001", "68.00"));
  assert.throws(
    () => normalizePaymentActionResult({
      paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", businessChainId: "CHAIN-PAY-001",
      orderNo: "ORDER-001", amount: "68.00", status: "SUCCESS", statusType: "success",
      successAt: "2026-09-27 10:01:00", createdAt: "2026-09-27 10:00:00"
    }, "PAY-001", "ORDER-001", "68.00"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
  assert.deepEqual(
    normalizePaymentActionResult({ paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", businessChainId: "CHAIN-PAY-001", orderNo: "ORDER-001", amount: "68.00", status: "SUCCESS", statusType: "success", channel: "WX_H5", channelTransactionNo: "WX-001", successAt: "2026-09-27 10:01:00", createdAt: "2026-09-27 10:00:00", latestAttemptStatus: "成功", latestAttemptStatusType: "success", latestAttemptCompletedAt: "2026-09-27 10:00:30" }, "PAY-001"),
    { paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", businessChainId: "CHAIN-PAY-001", orderNo: "ORDER-001", amount: "68.00", status: "SUCCESS", statusType: "success", channel: "WX_H5", channelTransactionNo: "WX-001", successAt: "2026-09-27 10:01:00", createdAt: "2026-09-27 10:00:00", latestAttemptStatus: "成功", latestAttemptStatusType: "success", latestAttemptCompletedAt: "2026-09-27 10:00:30" }
  );
  assert.throws(
    () => normalizePaymentActionResult({ paymentOrderId: "PAY-002", status: "CLOSED" }, "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
  const validAction = {
    paymentOrderId: "PAY-001",
    prepayOrderNo: " PRE-001 ",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    amount: "68.00",
    status: "SUCCESS",
    statusType: "success",
    channel: "WX_H5",
    channelTransactionNo: "WX-001",
    successAt: "2026-09-27 10:01:00",
    createdAt: "2026-09-27 10:00:00",
    latestAttemptStatus: "成功",
    latestAttemptStatusType: "success",
    latestAttemptCompletedAt: "2026-09-27 10:00:30"
  };
  assert.doesNotThrow(() => normalizePaymentActionResult(
    validAction, "PAY-001", "ORDER-001", "68.00", "PRE-001"
  ));
  assert.throws(
    () => normalizePaymentActionResult(
      { ...validAction, prepayOrderNo: "PRE-002" },
      "PAY-001", "ORDER-001", "68.00", "PRE-001"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult({
      paymentOrderId: "PAY-001",
      prepayOrderNo: "PRE-001",
      businessChainId: "CHAIN-OTHER",
      orderNo: "ORDER-001",
      amount: "68.00",
      status: "SUCCESS",
      statusType: "success",
      successAt: "2026-09-27 10:01:00",
      createdAt: "2026-09-27 10:00:00"
    }, "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult({ paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", orderNo: "ORDER-001", amount: "68.00", status: "SUCCESS", statusType: "success", successAt: "2026-09-27 10:01:00", createdAt: "2026-09-27 10:00:00" }, "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult({ paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", businessChainId: "CHAIN-PAY-001", orderNo: "ORDER-001", amount: "68.00", status: "SUCCESS", statusType: "success", channelTransactionNo: "WX-001", successAt: "2026-09-27 10:01:00", createdAt: "2026-09-27 10:00:00" }, "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult({ paymentOrderId: "PAY-001", orderNo: "ORDER-001", amount: "invalid", status: "SUCCESS" }, "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult(
      { paymentOrderId: "PAY-001", orderNo: "ORDER-002", amount: "68.00", status: "SUCCESS" },
      "PAY-001",
      "ORDER-001",
      "68.00"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
  for (const invalidResult of [
    { paymentOrderId: "PAY-001", prepayOrderNo: "", orderNo: "ORDER-001", amount: "68.00", status: "SUCCESS" },
    { paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", orderNo: "ORDER-001", amount: "68.00", status: "SUCCESS", statusType: "invalid" },
    { paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", orderNo: "ORDER-001", amount: "68.00", status: "WAIT_CALLBACK", statusType: "success" },
    { paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", orderNo: "ORDER-001", amount: "68.00", status: "SUCCESS", statusType: "success", latestAttemptStatus: "等待回调" },
    { paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", orderNo: "ORDER-001", amount: "68.00", status: "SUCCESS", statusType: "success", latestAttemptStatus: "成功", latestAttemptStatusType: "danger", latestAttemptCompletedAt: "2026-09-27 10:01:00" },
    { paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", orderNo: "ORDER-001", amount: "68.00", status: "SUCCESS", statusType: "success", latestAttemptStatus: "失败", latestAttemptStatusType: "success" },
    { paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", orderNo: "ORDER-001", amount: "68.00", status: "SUCCESS", statusType: "success", latestAttemptStatusType: "warn" },
    { paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", orderNo: "ORDER-001", amount: "68.00", status: "SUCCESS", statusType: "success", latestAttemptStatus: "成功", latestAttemptCompletedAt: undefined },
    { paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", orderNo: "ORDER-001", amount: "68.00", status: "SUCCESS", statusType: "success", successAt: "2026-09-27 10:01:00", latestAttemptStatus: "成功", latestAttemptStatusType: "success", latestAttemptCompletedAt: "2026-09-27 10:02:00", createdAt: "2026-09-27 10:00:00" },
    { paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", orderNo: "ORDER-001", amount: "68.00", status: "SUCCESS", statusType: "success", latestAttemptStatus: "等待回调", latestAttemptCompletedAt: "2026-09-27 10:01:00" },
    { paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", orderNo: "ORDER-001", amount: "68.00", status: "SUCCESS", statusType: "success", successAt: undefined, createdAt: "2026-09-27 10:00:00" },
    { paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", orderNo: "ORDER-001", amount: "68.00", status: "SUCCESS", statusType: "success", successAt: "2026-09-27 09:59:00", createdAt: "2026-09-27 10:00:00" },
    { paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", orderNo: "ORDER-001", amount: "68.00", status: "WAIT_CALLBACK", statusType: "warn", successAt: "2026-09-27 10:01:00", createdAt: "2026-09-27 10:00:00" },
    { paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", orderNo: "ORDER-001", amount: "68.00", status: "SUCCESS", statusType: "success", createdAt: undefined },
    { paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", orderNo: "ORDER-001", amount: "68.00", status: "SUCCESS", statusType: "success", createdAt: "2026-02-30 10:00:00" }
  ]) {
    assert.throws(
      () => normalizePaymentActionResult(invalidResult, "PAY-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
    );
  }
  assert.throws(
    () => normalizePaymentActionResult(
      { paymentOrderId: "PAY-001", orderNo: "ORDER-001", amount: "67.99", status: "SUCCESS" },
      "PAY-001",
      "ORDER-001",
      "68.00"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult(
      { paymentOrderId: "PAY-001", orderNo: "ORDER-001", amount: "6.8e1", status: "SUCCESS" },
      "PAY-001",
      "ORDER-001",
      "68.00"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult(
      { paymentOrderId: "PAY-001", orderNo: "ORDER-001", amount: "68.001", status: "SUCCESS" },
      "PAY-001",
      "ORDER-001",
      "68.00"
    ),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult(
      {
        paymentOrderId: "PAY-001",
        orderNo: "ORDER-001",
        amount: "9007199254740992.02",
        status: "SUCCESS"
      },
      "PAY-001",
      "ORDER-001",
      "9007199254740992.01"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
});

test("payment action responses remain summaries rather than detail snapshots", () => {
  const actionResult = normalizePaymentActionResult({
    paymentOrderId: "PAY-001",
    prepayOrderNo: "PRE-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    amount: "68.00",
    status: "SUCCESS",
    statusType: "success",
    channel: "WX_H5",
    channelTransactionNo: "WX-001",
    successAt: "2026-09-27 10:01:00",
    createdAt: "2026-09-27 10:00:00",
    latestAttemptStatus: "成功",
    latestAttemptStatusType: "success",
    latestAttemptCompletedAt: "2026-09-27 10:00:30"
  }, "PAY-001", "ORDER-001", "68.00");
  assert.equal(actionResult.routeLogs, undefined);
  assert.equal(actionResult.notifyLogs, undefined);
  assert.equal(actionResult.eventLogs, undefined);
});

test("binds payment action normalization to the loaded channel snapshot", () => {
  const snapshot = {
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    prepayOrderNo: "PRE-001",
    orderNo: "ORDER-001",
    amount: "68.00",
    paymentMethod: "微信支付",
    channel: "WX_H5",
    channelTransactionNo: "WX-001"
  };
  const result = {
    ...snapshot,
    status: "SUCCESS",
    statusType: "success",
    successAt: "2026-09-27 10:01:00",
    createdAt: "2026-09-27 10:00:00",
    latestAttemptStatus: "成功",
    latestAttemptStatusType: "success",
    latestAttemptCompletedAt: "2026-09-27 10:00:30"
  };
  assert.deepEqual(
    normalizePaymentActionResult(result, "PAY-001", "ORDER-001", "68.00", "PRE-001", snapshot),
    result
  );
  for (const field of ["paymentMethod", "channel", "channelTransactionNo"]) {
    assert.throws(
      () => normalizePaymentActionResult(
        { ...result, [field]: field === "paymentMethod" ? "支付宝" : "ALI-001" },
        "PAY-001", "ORDER-001", "68.00", "PRE-001", snapshot
      ),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
    );
  }
});

test("refund action responses remain summaries rather than detail snapshots", () => {
  const actionResult = normalizeRefundActionResult({
    refundOrderId: "REF-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    paidAmount: "68.00",
    refundAmount: "18.00",
    status: "SUCCESS",
    statusType: "success",
    channelCode: "WX_H5",
    appliedAt: "2026-09-27 10:00:00",
    successAt: "2026-09-27 10:01:00",
    terminalAt: "2026-09-27 10:01:00",
    channelRefundNo: "CH-REF-001",
    channelSubmittedAt: "2026-09-27 10:00:30"
  }, "REF-001", "PAY-001", "18.00");
  assert.equal(actionResult.operationLogs, undefined);
  assert.equal(actionResult.attempts, undefined);
  assert.equal(actionResult.channelCode, "WX_H5");
});

test("rejects empty or mismatched payment detail responses", async (context) => {
  const validDetail = {
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    prepayOrderNo: "PREPAY-001",
    payScene: "HOME_CLEAN",
    orderNo: "ORDER-001",
    customerName: "客户一",
    amount: "¥68.00",
    paymentMethod: "微信",
    channel: "wx_h5",
    channelTransactionNo: "WX-001",
    status: "SUCCESS",
    statusType: "success",
    channelTransactionNo: "WX-001",
    latestAttemptStatus: "成功",
    latestAttemptStatusType: "success",
    latestAttemptCompletedAt: "2026-09-27 10:01:00",
    successAt: "2026-09-27 10:01:00",
    createdAt: "2026-09-27 10:00:00"
  };
  assert.deepEqual(
    normalizePaymentDetailResult(validDetail, "PAY-001"),
    validDetail
  );
  assert.throws(
    () => normalizePaymentDetailResult({ ...validDetail, channelTransactionNo: undefined }, "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDetailResult({ paymentOrderId: "PAY-002" }, "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDetailResult({ ...validDetail, businessChainId: "CHAIN-OTHER" }, "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDetailResult({ ...validDetail, amount: "invalid" }, "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-DETAIL"
  );
  for (const field of ["routeLogs", "notifyLogs", "eventLogs"]) {
    assert.throws(
      () => normalizePaymentDetailResult({ ...validDetail, [field]: "malformed" }, "PAY-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-DETAIL"
    );
  }
  assert.throws(
    () => normalizePaymentDetailResult({ ...validDetail, status: "WAIT_CALLBACK", statusType: "success" }, "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDetailResult({ ...validDetail, successAt: undefined }, "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDetailResult({ ...validDetail, successAt: "not-a-time" }, "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDetailResult({ ...validDetail, successAt: "2026-02-30 10:01:00" }, "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDetailResult({ ...validDetail, successAt: "2026-09-27 09:59:00" }, "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDetailResult({ ...validDetail, latestAttemptStatusType: "danger" }, "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDetailResult({ ...validDetail, latestAttemptCompletedAt: undefined }, "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDetailResult({ ...validDetail, latestAttemptCompletedAt: "not-a-time" }, "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDetailResult({
      ...validDetail,
      latestAttemptCompletedAt: "2026-09-27 10:02:00"
    }, "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-DETAIL"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  await assert.rejects(
    paymentApi.getDetail("PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-DETAIL"
  );
});

test("rejects payment pages with inconsistent status and payment facts", () => {
  const validRow = {
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    payScene: "HOME_CLEAN",
    prepayOrderNo: "PREPAY-001",
    customerName: "客户一",
    amount: "¥68.00",
    paymentMethod: "微信",
    channel: "wx_h5",
    channelTransactionNo: "WX-001",
    status: "SUCCESS",
    statusType: "success",
    successFactIssue: null,
    latestAttemptStatus: "成功",
    latestAttemptStatusType: "success",
    latestAttemptCompletedAt: "2026-09-27 10:01:00",
    createdAt: "2026-09-27 10:00:00"
  };
  assert.deepEqual(
    normalizePaymentListPage({ items: [validRow], total: 1 }).items,
    [validRow]
  );
  for (const prepayBindingIssue of ["MULTIPLE_PREPAY_BINDINGS", "PREPAY_BINDING_MISSING"]) {
    const diagnostic = { ...validRow, prepayBindingIssue, payScene: null,
      successFactIssue: "SUCCESS_FACT_MISMATCH" };
    assert.deepEqual(normalizePaymentListPage({ items: [diagnostic], total: 1 }).items,
      [diagnostic]);
    for (const invalidBinding of [
      { prepayBindingIssue: null },
      { prepayBindingIssue: "UNKNOWN" },
      { payScene: "HOME_CLEAN" }
    ]) {
      assert.throws(() => normalizePaymentListPage({
        items: [{ ...diagnostic, ...invalidBinding }], total: 1
      }), (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LIST");
    }
  }
  for (const successFactIssue of ["SUCCESS_FACT_MISMATCH", "SUCCESS_TIME_MISSING"]) {
    const anomalousRow = {
      ...validRow,
      successFactIssue,
      channelTransactionNo: null,
      latestAttemptCompletedAt: "2026-09-27 09:59:00"
    };
    assert.deepEqual(
      normalizePaymentListPage({ items: [validRow, anomalousRow], total: 2 }).items,
      [validRow, anomalousRow]
    );
  }
  const missingAttempt = {
    ...validRow,
    successFactIssue: "SUCCESS_FACT_MISMATCH",
    channel: null,
    channelTransactionNo: null,
    statusType: null,
    latestAttemptStatus: null,
    latestAttemptStatusType: null,
    latestAttemptCompletedAt: null
  };
  assert.deepEqual(
    normalizePaymentListPage({ items: [missingAttempt], total: 1 }).items,
    [missingAttempt]
  );
  assert.throws(
    () => normalizePaymentListPage({ items: [{ ...missingAttempt, channel: undefined }], total: 1 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LIST"
  );
  for (const amount of ["¥0.00", "¥-5.00", null]) {
    const abnormalAmount = { ...validRow, amount, successFactIssue: "SUCCESS_FACT_MISMATCH" };
    assert.deepEqual(
      normalizePaymentListPage({ items: [abnormalAmount], total: 1 }).items,
      [abnormalAmount]
    );
    assert.throws(
      () => normalizePaymentListPage({ items: [{ ...validRow, amount }], total: 1 }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LIST"
    );
  }
  for (const amount of [undefined, "-5.00", "¥-5", "¥-5.000", "¥oops"]) {
    assert.throws(
      () => normalizePaymentListPage({
        items: [{ ...validRow, amount, successFactIssue: "SUCCESS_FACT_MISMATCH" }], total: 1
      }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LIST"
    );
  }
  for (const successFactIssue of [undefined, "UNKNOWN", "SUCCESS_FACT_MISMATCH"]) {
    const row = { ...validRow, successFactIssue };
    if (successFactIssue === "SUCCESS_FACT_MISMATCH") {
      row.status = "WAIT_CALLBACK";
    }
    assert.throws(
      () => normalizePaymentListPage({ items: [row], total: 1 }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LIST"
    );
  }
  assert.throws(
    () => normalizePaymentListPage({
      items: [{ ...validRow, channelTransactionNo: undefined }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LIST"
  );
  for (const businessChainId of [undefined, "CHAIN-OTHER"]) {
    assert.throws(
      () => normalizePaymentListPage({ items: [{ ...validRow, businessChainId }], total: 1 }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LIST"
    );
  }
  assert.throws(
    () => normalizePaymentListPage({
      items: [{ ...validRow, status: "WAIT_CALLBACK", statusType: "success" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LIST"
  );
  assert.throws(
    () => normalizePaymentListPage({
      items: [{ ...validRow, amount: "0" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LIST"
  );
  assert.throws(
    () => normalizePaymentListPage({
      items: [{ ...validRow, orderNo: "" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LIST"
  );
  assert.throws(
    () => normalizePaymentListPage({
      items: [{ ...validRow, payScene: undefined }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LIST"
  );
  assert.throws(
    () => normalizePaymentListPage({
      items: [{ ...validRow, latestAttemptStatusType: undefined }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LIST"
  );
  assert.throws(
    () => normalizePaymentListPage({
      items: [{ ...validRow, latestAttemptStatusType: "danger" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LIST"
  );
  assert.throws(
    () => normalizePaymentListPage({
      items: [{ ...validRow, latestAttemptCompletedAt: undefined }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LIST"
  );
  assert.throws(
    () => normalizePaymentListPage({
      items: [{ ...validRow, latestAttemptCompletedAt: "2026-09-27 09:59:00" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LIST"
  );
});

test("rejects contradictory English terminal attempts in payment actions, details, and lists", () => {
  const failed = {
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    prepayOrderNo: "PRE-001",
    orderNo: "ORDER-001",
    payScene: "HOME_CLEAN",
    customerName: "客户一",
    amount: "68.00",
    paymentMethod: "微信",
    channel: "wx_h5",
    channelTransactionNo: null,
    status: "FAIL",
    statusType: "danger",
    successAt: null,
    createdAt: "2026-09-27 10:00:00",
    latestAttemptStatus: "FAILED",
    latestAttemptStatusType: "danger",
    latestAttemptCompletedAt: null,
    successFactIssue: null
  };
  const normalizers = [
    ["PAYMENT-CLIENT-INVALID-PAYMENT-ACTION", (value) => normalizePaymentActionResult(value, "PAY-001")],
    ["PAYMENT-CLIENT-INVALID-PAYMENT-DETAIL", (value) => normalizePaymentDetailResult(value, "PAY-001")],
    ["PAYMENT-CLIENT-INVALID-PAYMENT-LIST", (value) => normalizePaymentListPage({ items: [value], total: 1 })]
  ];
  for (const [errorCode, normalize] of normalizers) {
    assert.doesNotThrow(() => normalize(failed));
    for (const [latestAttemptStatus, latestAttemptStatusType] of [
      ["FAILED", "success"], ["FAIL", "success"], ["SUCCESS", "success"]
    ]) {
      assert.throws(
        () => normalize({ ...failed, latestAttemptStatus, latestAttemptStatusType }),
        (error) => error.code === errorCode
      );
    }
    assert.throws(
      () => normalize({ ...failed, status: "WAIT_CALLBACK", statusType: "warn" }),
      (error) => error.code === errorCode
    );
  }
});

test("requires payment detail refreshes to preserve the order and amount snapshot", () => {
  const snapshot = {
    paymentOrderId: "PAY-001",
    orderNo: "ORDER-001",
    billNo: "BILL-001",
    customerName: "张三",
    amount: "68.00"
  };
  assert.equal(matchesPaymentDetailSnapshot(snapshot, { ...snapshot, amount: "¥68.00" }), true);
  assert.equal(matchesPaymentDetailSnapshot(snapshot, { ...snapshot, billNo: "BILL-002" }), false);
  assert.equal(matchesPaymentDetailSnapshot(snapshot, { ...snapshot, customerName: "李四" }), false);
  assert.equal(matchesPaymentDetailSnapshot(snapshot, { ...snapshot, billNo: undefined, customerName: undefined }), true);
  assert.equal(matchesPaymentDetailSnapshot({ ...snapshot, paymentMethod: "微信支付" }, {
    ...snapshot,
    paymentMethod: "微信支付"
  }), true);
  assert.equal(matchesPaymentDetailSnapshot({ ...snapshot, paymentMethod: "微信支付" }, {
    ...snapshot,
    paymentMethod: "支付宝"
  }), false);
  assert.equal(
    matchesPaymentDetailSnapshot(
      snapshot,
      { ...snapshot, paymentOrderId: " PAY-001 ", orderNo: " ORDER-001 ", amount: "¥68.00" }
    ),
    true
  );
  assert.equal(matchesPaymentDetailSnapshot(snapshot, { ...snapshot, orderNo: "ORDER-002" }), false);
  assert.equal(matchesPaymentDetailSnapshot(snapshot, { ...snapshot, amount: "67.99" }), false);
});

test("requires payment detail refreshes to preserve the business and channel snapshot", () => {
  const snapshot = {
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    prepayOrderNo: "PRE-001",
    payScene: "HOME_CLEAN",
    orderNo: "ORDER-001",
    amount: "68.00",
    paymentMethod: "微信支付",
    channel: "WX_H5",
    channelTransactionNo: "WX-001"
  };
  assert.equal(matchesPaymentDetailSnapshot(snapshot, { ...snapshot, amount: "¥68.00" }), true);
  for (const field of ["businessChainId", "prepayOrderNo", "payScene", "channel", "channelTransactionNo"]) {
    assert.equal(matchesPaymentDetailSnapshot(snapshot, {
      ...snapshot,
      [field]: field === "businessChainId"
        ? "CHAIN-OTHER"
        : field === "prepayOrderNo"
          ? "PRE-OTHER"
          : field === "channel"
            ? "ALI_H5"
            : "ALI-001"
    }), false);
  }
});

test("preserves large cent-level amounts across admin detail snapshots", () => {
  const amount = "9007199254740992.01";
  const adjacentAmount = "9007199254740992.02";
  assert.equal(
    matchesPaymentDetailSnapshot(
      { paymentOrderId: "PAY-001", orderNo: "ORDER-001", amount },
      { paymentOrderId: "PAY-001", orderNo: "ORDER-001", amount: adjacentAmount }
    ),
    false
  );
  assert.equal(
    matchesPaymentRecordDetailSnapshot(
      { paymentOrderId: "PAY-001", businessOrderNo: "ORDER-001", paymentAmount: amount },
      { paymentOrderId: "PAY-001", businessOrderNo: "ORDER-001", paymentAmount: adjacentAmount }
    ),
    false
  );
  assert.equal(
    matchesPaymentRouteDetailSnapshot(
      { routeNo: "ROUTE-001", paymentOrderId: "PAY-001", requestNo: "REQ-001", orderNo: "ORDER-001", amount },
      { routeNo: "ROUTE-001", paymentOrderId: "PAY-001", requestNo: "REQ-001", orderNo: "ORDER-001", amount: adjacentAmount }
    ),
    false
  );
  assert.equal(
    matchesCashierSessionDetailSnapshot(
      { sessionNo: "SESSION-001", prepayOrderNo: "PREPAY-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", amount },
      { sessionNo: "SESSION-001", prepayOrderNo: "PREPAY-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", amount: adjacentAmount }
    ),
    false
  );
  assert.equal(
    matchesOrderDetailSnapshot(
      { orderNo: "ORDER-001", orderAmount: amount },
      { orderNo: "ORDER-001", orderAmount: adjacentAmount }
    ),
    false
  );
  assert.equal(
    matchesBillDetailSnapshot(
      { billNo: "BILL-001", orderNo: "ORDER-001", standardOrderNo: "STD-001", billAmount: amount },
      { billNo: "BILL-001", orderNo: "ORDER-001", standardOrderNo: "STD-001", billAmount: adjacentAmount }
    ),
    false
  );
});

test("rejects empty or mismatched payment record detail responses", async (context) => {
  assert.deepEqual(
    normalizePaymentRecordDetailResult({
      paymentOrderId: "PAY-001",
      successFactIssue: null,
      businessChainId: "CHAIN-PAY-001",
      businessOrderNo: "ORDER-001",
      payScene: "HOME_CLEAN",
      paymentAmount: "68.00",
      channelReturnCode: null,
      paymentGateway: null,
      bankName: null,
      cardNo: null,
      receivingAccount: null,
      notifyUrl: null,
      callbackMqTopic: null,
      businessLineId: null,
      productName: null,
      userPaymentChannelId: null,
      userId: null,
      externalTransactionNo: "WX-001",
      refundCount: 1,
      refundedAmount: "¥18.00",
      unverifiedRefundCount: 0,
      paymentStatus: "支付成功",
      statusType: "success",
      paidAt: "2026-09-27 10:01:00",
      expireTime: "2026-09-27 10:30:00",
      createdAt: "2026-09-27 10:00:00",
      updatedAt: "2026-09-27 10:02:00",
      latestAttemptStatus: "SUCCESS",
      latestAttemptStatusType: "success",
      latestAttemptCompletedAt: "2026-09-27 10:01:05",
      recordType: "PAYMENT"
    }, "PAY-001"),
    {
      paymentOrderId: "PAY-001",
      successFactIssue: null,
      businessChainId: "CHAIN-PAY-001",
      businessOrderNo: "ORDER-001",
      payScene: "HOME_CLEAN",
      paymentAmount: "68.00",
      channelReturnCode: null,
      paymentGateway: null,
      bankName: null,
      cardNo: null,
      receivingAccount: null,
      notifyUrl: null,
      callbackMqTopic: null,
      businessLineId: null,
      productName: null,
      userPaymentChannelId: null,
      userId: null,
      externalTransactionNo: "WX-001",
      refundCount: 1,
      refundedAmount: "¥18.00",
      unverifiedRefundCount: 0,
      paymentStatus: "支付成功",
      statusType: "success",
      paidAt: "2026-09-27 10:01:00",
      expireTime: "2026-09-27 10:30:00",
      createdAt: "2026-09-27 10:00:00",
      updatedAt: "2026-09-27 10:02:00",
      latestAttemptStatus: "SUCCESS",
      latestAttemptStatusType: "success",
      latestAttemptCompletedAt: "2026-09-27 10:01:05",
      recordType: "PAYMENT"
    }
  );
  assert.equal(
    normalizePaymentRecordDetailResult({
      paymentOrderId: " PAY-001 ",
      successFactIssue: null,
      businessChainId: "CHAIN-PAY-001",
      businessOrderNo: " ORDER-001 ",
      payScene: " HOME_CLEAN ",
      paymentAmount: "68.00",
      channelReturnCode: null,
      paymentGateway: null,
      bankName: null,
      cardNo: null,
      receivingAccount: null,
      notifyUrl: null,
      callbackMqTopic: null,
      businessLineId: null,
      productName: null,
      userPaymentChannelId: null,
      userId: null,
      externalTransactionNo: "WX-001",
      refundCount: 1,
      refundedAmount: "¥18.00",
      unverifiedRefundCount: 0,
      paymentStatus: "支付成功",
      statusType: "success",
      paidAt: "2026-09-27 10:01:00",
      expireTime: "2026-09-27 10:30:00",
      createdAt: "2026-09-27 10:00:00",
      updatedAt: "2026-09-27 10:02:00",
      latestAttemptStatus: "SUCCESS",
      latestAttemptStatusType: "success",
      latestAttemptCompletedAt: "2026-09-27 10:01:05",
      recordType: "PAYMENT"
    }, " PAY-001 ").paymentOrderId,
    " PAY-001 "
  );
  const successfulRecord = {
    paymentOrderId: "PAY-001",
    successFactIssue: null,
    businessChainId: "CHAIN-PAY-001",
    businessOrderNo: "ORDER-001",
    payScene: "HOME_CLEAN",
    paymentAmount: "68.00",
    channelReturnCode: null,
    paymentGateway: null,
    bankName: null,
    cardNo: null,
    receivingAccount: null,
    notifyUrl: null,
    callbackMqTopic: null,
    businessLineId: null,
    productName: null,
    userPaymentChannelId: null,
    userId: null,
    externalTransactionNo: "WX-001",
    refundCount: 0,
    refundedAmount: "0.00",
    unverifiedRefundCount: 0,
    paymentStatus: "支付成功",
    statusType: "success",
    paidAt: "2026-09-27 10:01:00",
    expireTime: "2026-09-27 10:30:00",
    createdAt: "2026-09-27 10:00:00",
    updatedAt: "2026-09-27 10:02:00",
    latestAttemptStatus: "SUCCESS",
    latestAttemptStatusType: "success",
    latestAttemptCompletedAt: "2026-09-27 10:01:05",
    recordType: "PAYMENT"
  };
  const failedRecord = {
    ...successfulRecord,
    externalTransactionNo: null,
    paymentStatus: "支付失败",
    statusType: "danger",
    paidAt: null,
    latestAttemptStatus: "FAILED",
    latestAttemptStatusType: "danger",
    latestAttemptCompletedAt: null
  };
  const ambiguousRecord = {
    ...successfulRecord,
    successFactIssue: "SUCCESS_FACT_MISMATCH",
    prepayBindingIssue: "MULTIPLE_PREPAY_BINDINGS",
    payScene: null,
    paymentType: "未分类",
    validityPeriod: null,
    expireTime: null
  };
  assert.equal(normalizePaymentRecordDetailResult(ambiguousRecord, "PAY-001").payScene, null);
  assert.equal(normalizePaymentRecordDetailResult({
    ...ambiguousRecord, prepayBindingIssue: "PREPAY_BINDING_MISSING"
  }, "PAY-001").prepayBindingIssue, "PREPAY_BINDING_MISSING");
  for (const invalidBinding of [
    { prepayBindingIssue: null },
    { prepayBindingIssue: "UNKNOWN" },
    { payScene: "HOME_CLEAN" },
    { paymentType: "消费支付" },
    { expireTime: successfulRecord.expireTime },
    { validityPeriod: "1小时" }
  ]) {
    assert.throws(
      () => normalizePaymentRecordDetailResult({ ...ambiguousRecord, ...invalidBinding }, "PAY-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-DETAIL"
    );
  }
  assert.equal(normalizePaymentRecordDetailResult(failedRecord, "PAY-001").paymentStatus, "支付失败");
  assert.throws(
    () => normalizePaymentRecordDetailResult({ ...failedRecord, latestAttemptStatusType: "success" }, "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-DETAIL"
  );
  assert.equal(normalizePaymentRecordDetailResult({
    ...successfulRecord, unverifiedRefundCount: 2
  }, "PAY-001").unverifiedRefundCount, 2);
  for (const unverifiedRefundCount of [undefined, -1, 1.5, "1"]) {
    assert.throws(
      () => normalizePaymentRecordDetailResult({ ...successfulRecord, unverifiedRefundCount }, "PAY-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-DETAIL"
    );
  }
  for (const successFactIssue of ["SUCCESS_FACT_MISMATCH", "SUCCESS_TIME_MISSING"]) {
    const anomalous = { ...successfulRecord, successFactIssue,
      paidAt: successFactIssue === "SUCCESS_TIME_MISSING" ? null : successfulRecord.paidAt,
      latestAttemptCompletedAt: "2026-09-27 10:02:01" };
    assert.equal(normalizePaymentRecordDetailResult(anomalous, "PAY-001").successFactIssue,
      successFactIssue);
  }
  for (const successFactIssue of [undefined, "UNKNOWN"]) {
    assert.throws(() => normalizePaymentRecordDetailResult({ ...successfulRecord, successFactIssue }, "PAY-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-DETAIL");
  }
  assert.throws(() => normalizePaymentRecordDetailResult({
    ...successfulRecord, successFactIssue: "SUCCESS_FACT_MISMATCH", paidAt: null
  }, "PAY-001"), (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-DETAIL");
  assert.throws(
    () => normalizePaymentRecordDetailResult({ ...successfulRecord, payScene: undefined }, "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-DETAIL"
  );
  for (const channelReturnCode of [undefined, "", "  ", 200]) {
    assert.throws(
      () => normalizePaymentRecordDetailResult({ ...successfulRecord, channelReturnCode }, "PAY-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-DETAIL"
    );
  }
  for (const paymentGateway of [undefined, "", "  ", 123]) {
    assert.throws(
      () => normalizePaymentRecordDetailResult({ ...successfulRecord, paymentGateway }, "PAY-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-DETAIL"
    );
  }
  assert.equal(
    normalizePaymentRecordDetailResult({ ...successfulRecord, paymentGateway: "GW_WX" }, "PAY-001")
      .paymentGateway,
    "GW_WX"
  );
  for (const field of ["bankName", "cardNo", "receivingAccount", "notifyUrl", "callbackMqTopic",
    "businessLineId", "productName", "userPaymentChannelId", "userId"]) {
    for (const invalidValue of [undefined, "", "  ", 123]) {
      assert.throws(
        () => normalizePaymentRecordDetailResult({ ...successfulRecord, [field]: invalidValue }, "PAY-001"),
        (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-DETAIL"
      );
    }
  }
  assert.throws(
    () => normalizePaymentRecordDetailResult(
      { ...successfulRecord, latestAttemptStatus: undefined, latestAttemptStatusType: undefined, latestAttemptCompletedAt: undefined },
      "PAY-001"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-DETAIL"
  );
  assert.throws(
    () => normalizePaymentRecordDetailResult(
      { ...successfulRecord, latestAttemptStatus: "等待回调", latestAttemptStatusType: "warn", latestAttemptCompletedAt: undefined },
      "PAY-001"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-DETAIL"
  );
  assert.throws(
    () => normalizePaymentRecordDetailResult(
      { ...successfulRecord, latestAttemptCompletedAt: "2026-09-27 10:02:01" },
      "PAY-001"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-DETAIL"
  );
  assert.throws(
    () => normalizePaymentRecordDetailResult({
      paymentOrderId: "PAY-001",
      businessChainId: "CHAIN-OTHER",
      businessOrderNo: "ORDER-001",
      paymentAmount: "68.00",
    refundCount: 1,
    refundedAmount: "¥18.00",
    unverifiedRefundCount: 0,
    paymentStatus: "支付成功",
      statusType: "success",
      paidAt: "2026-09-27 10:01:00",
      expireTime: "2026-09-27 10:30:00",
      createdAt: "2026-09-27 10:00:00",
      updatedAt: "2026-09-27 10:02:00",
      latestAttemptStatus: "SUCCESS",
      latestAttemptStatusType: "success",
      latestAttemptCompletedAt: "2026-09-27 10:01:05"
    }, "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-DETAIL"
  );
  assert.throws(
    () => normalizePaymentRecordDetailResult({ paymentOrderId: "PAY-002" }, "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-DETAIL"
  );
  assert.throws(
    () => normalizePaymentRecordDetailResult({
      paymentOrderId: "PAY-001",
      paymentAmount: "0",
      paymentStatus: "支付成功",
      statusType: "success"
    }, "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-DETAIL"
  );
  assert.throws(
    () => normalizePaymentRecordDetailResult({
      paymentOrderId: "PAY-001",
      paymentAmount: "68.00",
      paymentStatus: "支付成功",
      statusType: "success",
      paidAt: "2026-09-27 10:03:00",
      expireTime: "2026-09-27 10:30:00",
      createdAt: "2026-09-27 10:00:00",
      updatedAt: "2026-09-27 10:02:00"
    }, "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-DETAIL"
  );
  for (const invalidRefundSummary of [
    { refundCount: -1, refundedAmount: "¥0.00" },
    { refundCount: 1, refundedAmount: "not-an-amount" },
    { refundCount: 1, refundedAmount: "¥68.01" }
  ]) {
    assert.throws(
      () => normalizePaymentRecordDetailResult({
        paymentOrderId: "PAY-001",
        paymentAmount: "68.00",
        ...invalidRefundSummary,
        paymentStatus: "支付成功",
        statusType: "success",
        paidAt: "2026-09-27 10:01:00",
        expireTime: "2026-09-27 10:30:00",
        createdAt: "2026-09-27 10:00:00",
        updatedAt: "2026-09-27 10:02:00"
      }, "PAY-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-DETAIL"
    );
  }
  assert.throws(
    () => normalizePaymentRecordDetailResult({
      paymentOrderId: "PAY-001",
      paymentAmount: "68.00",
      paymentStatus: "",
      statusType: "success"
    }, "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-DETAIL"
  );
  for (const invalidDetail of [
    {
      paymentOrderId: "PAY-001",
      paymentAmount: "68.00",
      paymentStatus: "支付成功"
    },
    {
      paymentOrderId: "PAY-001",
      paymentAmount: "68.00",
      paymentStatus: "支付成功",
      statusType: "danger"
    },
    {
      paymentOrderId: "PAY-001",
      paymentAmount: "68.00",
      paymentStatus: "支付成功",
      statusType: "success",
      latestAttemptStatus: "支付成功"
    },
    {
      paymentOrderId: "PAY-001",
      paymentAmount: "68.00",
      paymentStatus: "支付成功",
      statusType: "success",
      latestAttemptStatusType: "success"
    },
    {
      paymentOrderId: "PAY-001",
      paymentAmount: "68.00",
      paymentStatus: "支付成功",
      statusType: "success",
      paidAt: "2026-09-27 10:01:00",
      expireTime: "2026-09-27 10:30:00",
      createdAt: "2026-09-27 10:00:00",
      updatedAt: "2026-09-27 10:02:00",
      latestAttemptStatus: "SUCCESS",
      latestAttemptStatusType: "success",
      latestAttemptCompletedAt: "2026-09-27 09:59:00"
    },
    {
      paymentOrderId: "PAY-001",
      paymentAmount: "68.00",
      paymentStatus: "支付成功",
      statusType: "success",
      paidAt: "2026-09-27 10:01:00",
      expireTime: "2026-09-27 10:30:00",
      createdAt: "2026-09-27 10:00:00",
      updatedAt: "2026-09-27 10:02:00",
      latestAttemptStatus: "等待回调",
      latestAttemptStatusType: "warn",
      latestAttemptCompletedAt: "2026-09-27 10:01:00"
    },
    {
      paymentOrderId: "PAY-001",
      paymentAmount: "68.00",
      paymentStatus: "支付成功",
      statusType: "success",
      paidAt: "2026-09-27 10:01:00",
      expireTime: "2026-09-27 10:30:00",
      createdAt: "2026-09-27 10:00:00",
      updatedAt: "2026-09-27 10:02:00",
      latestAttemptStatus: "SUCCESS",
      latestAttemptStatusType: "warn",
      latestAttemptCompletedAt: "2026-09-27 10:01:05"
    }
  ]) {
    assert.throws(
      () => normalizePaymentRecordDetailResult(invalidDetail, "PAY-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-DETAIL"
    );
  }
  assert.throws(
    () => normalizePaymentRecordDetailResult({
      paymentOrderId: "PAY-001",
      paymentAmount: "68.00",
      paymentStatus: "支付成功",
      statusType: "success",
      paidAt: "not-a-time"
    }, "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-DETAIL"
  );
  assert.throws(
    () => normalizePaymentRecordDetailResult({
      paymentOrderId: "PAY-001",
      paymentAmount: "68.00",
      paymentStatus: "支付成功",
      statusType: "success",
      paidAt: "2026-09-27 09:59:00",
      expireTime: "2026-09-27 10:30:00",
      createdAt: "2026-09-27 10:00:00",
      updatedAt: "2026-09-27 10:02:00"
    }, "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-DETAIL"
  );
  assert.throws(
    () => normalizePaymentRecordDetailResult({
      paymentOrderId: "PAY-001",
      paymentAmount: "68.00",
      paymentStatus: "支付成功",
      statusType: "success",
      paidAt: "2026-09-27 10:01:00",
      expireTime: "2026-09-27 09:59:00",
      createdAt: "2026-09-27 10:00:00",
      updatedAt: "2026-09-27 10:02:00"
    }, "PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-DETAIL"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  await assert.rejects(
    paymentRecordApi.getDetail("PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-DETAIL"
  );
});

test("rejects payment record pages with inconsistent amount and status facts", () => {
  const validRow = {
    serialNo: 1,
    paymentOrderId: "PAY-001",
    successFactIssue: null,
    businessChainId: "CHAIN-PAY-001",
    businessOrderNo: "ORDER-001",
    payScene: "HOME_CLEAN",
    paymentRequestNo: "PAY-001-REQ",
    applicationSourceId: "housekeeping-admin",
    businessLineId: "housekeeping-service",
    externalTransactionNo: "WX-001",
    paymentGateway: "微信支付网关",
    paymentChannel: "wx_h5",
    paymentType: "消费支付",
    paymentStatus: "支付成功",
    bankName: "—",
    cardNo: "—",
    channelReturnCode: "200",
    returnParameterType: "预支付标识",
    parameterValue: null,
    validityPeriod: "1小时",
    paymentAmount: "¥68.00",
    refundCount: 1,
    refundedAmount: "¥18.00",
    unverifiedRefundCount: 0,
    productName: "家政服务-ORDER-001-订单支付",
    userPaymentChannelId: "user-客户一",
    receivingAccount: "123",
    notifyUrl: "https://pay.housekeeping.local/callback",
    callbackMqTopic: "payment.payment-result",
    expireTime: "2026-09-27 11:00:00",
    createdAt: "2026-09-27 10:00:00",
    updatedAt: "2026-09-27 10:01:00",
    paidAt: "2026-09-27 10:01:00",
    latestAttemptStatus: "SUCCESS",
    latestAttemptStatusType: "success",
    latestAttemptCompletedAt: "2026-09-27 10:00:30",
    userId: "user-客户一",
    statusType: "success"
  };
  assert.deepEqual(
    normalizePaymentRecordListPage({ items: [validRow], total: 1 }).items,
    [validRow]
  );
  const ambiguousRow = {
    ...validRow,
    successFactIssue: "SUCCESS_FACT_MISMATCH",
    prepayBindingIssue: "MULTIPLE_PREPAY_BINDINGS",
    payScene: null,
    paymentType: "未分类",
    validityPeriod: null,
    expireTime: null
  };
  assert.deepEqual(normalizePaymentRecordListPage({ items: [ambiguousRow], total: 1 }).items,
    [ambiguousRow]);
  assert.equal(normalizePaymentRecordListPage({ items: [{
    ...ambiguousRow, prepayBindingIssue: "PREPAY_BINDING_MISSING"
  }], total: 1 }).items[0].prepayBindingIssue, "PREPAY_BINDING_MISSING");
  for (const invalidBinding of [
    { prepayBindingIssue: null },
    { prepayBindingIssue: "UNKNOWN" },
    { payScene: "HOME_CLEAN" },
    { paymentType: "消费支付" },
    { expireTime: validRow.expireTime },
    { validityPeriod: "1小时" }
  ]) {
    assert.throws(() => normalizePaymentRecordListPage({
      items: [{ ...ambiguousRow, ...invalidBinding }], total: 1
    }), (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-LIST");
  }
  const failedRow = {
    ...validRow,
    externalTransactionNo: null,
    paymentStatus: "支付失败",
    statusType: "danger",
    paidAt: null,
    latestAttemptStatus: "FAILED",
    latestAttemptStatusType: "danger",
    latestAttemptCompletedAt: null
  };
  assert.deepEqual(normalizePaymentRecordListPage({ items: [failedRow], total: 1 }).items, [failedRow]);
  assert.throws(
    () => normalizePaymentRecordListPage({
      items: [{ ...failedRow, latestAttemptStatusType: "success" }], total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-LIST"
  );
  assert.equal(normalizePaymentRecordListPage({
    items: [{ ...validRow, unverifiedRefundCount: 2 }], total: 1
  }).items[0].unverifiedRefundCount, 2);
  for (const unverifiedRefundCount of [undefined, -1, 1.5, "1"]) {
    assert.throws(
      () => normalizePaymentRecordListPage({
        items: [{ ...validRow, unverifiedRefundCount }], total: 1
      }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-LIST"
    );
  }
  for (const successFactIssue of ["SUCCESS_FACT_MISMATCH", "SUCCESS_TIME_MISSING"]) {
    const anomalous = { ...validRow, successFactIssue,
      paidAt: successFactIssue === "SUCCESS_TIME_MISSING" ? null : validRow.paidAt,
      latestAttemptCompletedAt: "2026-09-27 10:01:01" };
    assert.equal(normalizePaymentRecordListPage({ items: [anomalous], total: 1 }).items[0]
      .successFactIssue, successFactIssue);
  }
  for (const successFactIssue of [undefined, "UNKNOWN"]) {
    assert.throws(() => normalizePaymentRecordListPage({
      items: [{ ...validRow, successFactIssue }], total: 1
    }), (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-LIST");
  }
  assert.throws(() => normalizePaymentRecordListPage({
    items: [{ ...validRow, paymentStatus: "支付中", statusType: "warn",
      successFactIssue: "SUCCESS_FACT_MISMATCH" }], total: 1
  }), (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-LIST");
  const unsubmittedRow = {
    ...validRow,
    paymentRequestNo: null,
    applicationSourceId: null,
    paymentStatus: "支付中",
    statusType: "warn",
    externalTransactionNo: null,
    paidAt: null,
    latestAttemptStatus: null,
    latestAttemptStatusType: null,
    latestAttemptCompletedAt: null
  };
  assert.deepEqual(
    normalizePaymentRecordListPage({ items: [unsubmittedRow], total: 1 }).items,
    [unsubmittedRow]
  );
  for (const field of ["paymentRequestNo", "applicationSourceId"]) {
    assert.throws(
      () => normalizePaymentRecordListPage({ items: [{ ...validRow, [field]: null }], total: 1 }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-LIST"
    );
    for (const invalidValue of [undefined, "", "  "]) {
      assert.throws(
        () => normalizePaymentRecordListPage({ items: [{ ...validRow, [field]: invalidValue }], total: 1 }),
        (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-LIST"
      );
    }
  }
  assert.equal(
    normalizePaymentRecordListPage({ items: [{ ...validRow, channelReturnCode: null }], total: 1 }).items[0].channelReturnCode,
    null
  );
  for (const channelReturnCode of [undefined, "", "  ", 200]) {
    assert.throws(
      () => normalizePaymentRecordListPage({ items: [{ ...validRow, channelReturnCode }], total: 1 }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-LIST"
    );
  }
  const unavailableBankFacts = { ...validRow, bankName: null, cardNo: null, receivingAccount: null };
  assert.equal(
    normalizePaymentRecordListPage({ items: [{ ...validRow, paymentGateway: null }], total: 1 })
      .items[0].paymentGateway,
    null
  );
  assert.equal(
    normalizePaymentRecordListPage({ items: [{ ...validRow, paymentGateway: "GW_WX" }], total: 1 })
      .items[0].paymentGateway,
    "GW_WX"
  );
  for (const paymentGateway of [undefined, "", "  ", 123]) {
    assert.throws(
      () => normalizePaymentRecordListPage({ items: [{ ...validRow, paymentGateway }], total: 1 }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-LIST"
    );
  }
  assert.equal(
    normalizePaymentRecordListPage({ items: [{ ...validRow, returnParameterType: null }], total: 1 })
      .items[0].returnParameterType,
    null
  );
  for (const returnParameterType of [undefined, "", "  ", 123]) {
    assert.throws(
      () => normalizePaymentRecordListPage({ items: [{ ...validRow, returnParameterType }], total: 1 }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-LIST"
    );
  }
  assert.deepEqual(
    normalizePaymentRecordListPage({ items: [unavailableBankFacts], total: 1 }).items,
    [unavailableBankFacts]
  );
  for (const field of ["bankName", "cardNo", "receivingAccount"]) {
    for (const invalidValue of [undefined, "", "  ", 123]) {
      assert.throws(
        () => normalizePaymentRecordListPage({ items: [{ ...validRow, [field]: invalidValue }], total: 1 }),
        (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-LIST"
      );
    }
  }
  const unavailableCallbackFacts = { ...validRow, notifyUrl: null, callbackMqTopic: null };
  assert.deepEqual(
    normalizePaymentRecordListPage({ items: [unavailableCallbackFacts], total: 1 }).items,
    [unavailableCallbackFacts]
  );
  for (const field of ["notifyUrl", "callbackMqTopic"]) {
    for (const invalidValue of [undefined, "", "  ", 123]) {
      assert.throws(
        () => normalizePaymentRecordListPage({ items: [{ ...validRow, [field]: invalidValue }], total: 1 }),
        (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-LIST"
      );
    }
  }
  const unavailableUserAndProductFacts = {
    ...validRow,
    businessLineId: null,
    productName: null,
    userPaymentChannelId: null,
    userId: null
  };
  assert.deepEqual(
    normalizePaymentRecordListPage({ items: [unavailableUserAndProductFacts], total: 1 }).items,
    [unavailableUserAndProductFacts]
  );
  for (const field of ["businessLineId", "productName", "userPaymentChannelId", "userId"]) {
    for (const invalidValue of [undefined, "", "  ", 123]) {
      assert.throws(
        () => normalizePaymentRecordListPage({ items: [{ ...validRow, [field]: invalidValue }], total: 1 }),
        (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-LIST"
      );
    }
  }
  assert.throws(
    () => normalizePaymentRecordListPage({
      items: [{ ...validRow, externalTransactionNo: null }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-LIST"
  );
  for (const businessChainId of [undefined, "CHAIN-OTHER"]) {
    assert.throws(
      () => normalizePaymentRecordListPage({
        items: [{ ...validRow, businessChainId }],
        total: 1
      }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-LIST"
    );
  }
  assert.throws(
    () => normalizePaymentRecordListPage({
      items: [{ ...validRow, paymentStatus: "支付中", statusType: "success" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-LIST"
  );
  assert.throws(
    () => normalizePaymentRecordListPage({
      items: [{
        ...validRow,
        paymentStatus: "支付中",
        statusType: "warn",
        externalTransactionNo: null,
        paidAt: null,
        latestAttemptStatus: "SUCCESS",
        latestAttemptStatusType: "success",
        latestAttemptCompletedAt: "2026-09-27 10:00:30"
      }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-LIST"
  );
  assert.throws(
    () => normalizePaymentRecordListPage({
      items: [{ ...validRow, refundedAmount: "¥70.00" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-LIST"
  );
  assert.throws(
    () => normalizePaymentRecordListPage({
      items: [{ ...validRow, refundCount: -1 }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-LIST"
  );
  assert.throws(
    () => normalizePaymentRecordListPage({
      items: [{ ...validRow, paymentOrderId: "" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-LIST"
  );
  assert.throws(
    () => normalizePaymentRecordListPage({
      items: [{ ...validRow, paidAt: "not-a-time" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-LIST"
  );
  assert.throws(
    () => normalizePaymentRecordListPage({
      items: [{ ...validRow, paidAt: "2026-09-27 09:59:00" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-LIST"
  );
  assert.throws(
    () => normalizePaymentRecordListPage({
      items: [{ ...validRow, paidAt: "2026-09-27 10:02:00" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-LIST"
  );
});

test("applies payment record list validation to API responses", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({
      code: "0",
      data: {
        items: [{
          serialNo: 1,
          paymentOrderId: "PAY-001",
          successFactIssue: null,
          businessChainId: "CHAIN-PAY-001",
          businessOrderNo: "ORDER-001",
          payScene: "HOME_CLEAN",
          paymentRequestNo: "PAY-001-REQ",
          applicationSourceId: "housekeeping-admin",
          businessLineId: "housekeeping-service",
          externalTransactionNo: null,
          paymentGateway: "微信支付网关",
          paymentChannel: "wx_h5",
          paymentType: "消费支付",
          paymentStatus: "支付中",
          bankName: "—",
          cardNo: "—",
          channelReturnCode: "100",
          returnParameterType: "预支付标识",
          parameterValue: null,
          validityPeriod: "1小时",
          paymentAmount: "¥68.00",
          refundCount: 0,
          refundedAmount: "¥0.00",
          unverifiedRefundCount: 0,
          productName: "家政服务-ORDER-001-订单支付",
          userPaymentChannelId: "user-客户一",
          receivingAccount: "123",
          notifyUrl: "https://pay.housekeeping.local/callback",
          callbackMqTopic: "payment.payment-result",
          expireTime: "2026-09-27 11:00:00",
          createdAt: "2026-09-27 10:00:00",
          updatedAt: "2026-09-27 10:00:00",
          paidAt: null,
          userId: "user-客户一",
          statusType: "warn"
        }],
        total: 1,
        pageNo: 1,
        pageSize: 20
      }
    })
  }));

  const result = await paymentRecordApi.getList();
  assert.equal(result.items[0].paymentOrderId, "PAY-001");
});

test("uses customerName for payment record list and export filters", async (context) => {
  let requestedUrl;
  context.mock.method(globalThis, "fetch", async (url) => {
    requestedUrl = url;
    return {
      ok: true,
      json: async () => ({ code: "0", data: { items: [], total: 0, pageNo: 1, pageSize: 20 } })
    };
  });

  await paymentRecordApi.getList({ customerName: "张女士" });
  const listParams = new URL(requestedUrl, "http://localhost").searchParams;
  assert.equal(listParams.get("customerName"), "张女士");
  assert.equal(listParams.has("userId"), false);

  const exportParams = new URL(paymentRecordApi.buildExportUrl({ customerName: "张女士" }), "http://localhost").searchParams;
  assert.equal(exportParams.get("customerName"), "张女士");
  assert.equal(exportParams.has("userId"), false);
});

test("rejects payment record rows with invalid or reversed lifecycle timestamps", () => {
  const validRow = {
    serialNo: 1,
    paymentOrderId: "PAY-001",
    successFactIssue: null,
    businessChainId: "CHAIN-PAY-001",
    businessOrderNo: "ORDER-001",
    payScene: "HOME_CLEAN",
    paymentRequestNo: "REQ-001",
    applicationSourceId: "APP-001",
    businessLineId: "LINE-001",
    externalTransactionNo: "TX-001",
    paymentGateway: "统一支付网关",
    paymentChannel: "wx_pay",
    paymentType: "消费支付",
    paymentStatus: "支付中",
    statusType: "warn",
    bankName: "—",
    cardNo: "—",
    channelReturnCode: "100",
    returnParameterType: "预支付标识",
    parameterValue: "TX-001",
    validityPeriod: "1小时",
    paymentAmount: "¥68.00",
    refundCount: 0,
    refundedAmount: "¥0.00",
    unverifiedRefundCount: 0,
    productName: "家政服务-ORDER-001-订单支付",
    userPaymentChannelId: "user-客户一",
    receivingAccount: "123",
    notifyUrl: "https://pay.housekeeping.local/callback",
    callbackMqTopic: "payment.payment-result",
    expireTime: "2026-09-27 11:00:00",
    createdAt: "2026-09-27 10:00:00",
    updatedAt: "2026-09-27 10:00:00",
    paidAt: null,
    userId: "user-客户一"
  };

  assert.deepEqual(
    normalizePaymentRecordListPage({ items: [validRow], total: 1 }).items,
    [validRow]
  );
  for (const invalidRow of [
    { ...validRow, expireTime: "not-a-datetime" },
    { ...validRow, updatedAt: "2026-09-27 09:59:59" },
    { ...validRow, expireTime: "2026-09-27 09:59:59" }
  ]) {
    assert.throws(
      () => normalizePaymentRecordListPage({ items: [invalidRow], total: 1 }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-RECORD-LIST"
    );
  }
});

test("requires payment record detail refreshes to preserve the order and amount snapshot", () => {
  const snapshot = {
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    businessOrderNo: "ORDER-001",
    payScene: "HOME_CLEAN",
    paymentMethod: "微信支付",
    paymentAmount: "68.00"
  };
  assert.equal(matchesPaymentRecordDetailSnapshot(snapshot, { ...snapshot, paymentAmount: "¥68.00" }), true);
  assert.equal(
    matchesPaymentRecordDetailSnapshot(
      snapshot,
      { ...snapshot, paymentOrderId: " PAY-001 ", businessOrderNo: " ORDER-001 ", paymentAmount: "¥68.00" }
    ),
    true
  );
  assert.equal(matchesPaymentRecordDetailSnapshot(snapshot, { ...snapshot, businessOrderNo: "ORDER-002" }), false);
  assert.equal(matchesPaymentRecordDetailSnapshot(snapshot, { ...snapshot, paymentMethod: "支付宝" }), false);
  assert.equal(matchesPaymentRecordDetailSnapshot(snapshot, { ...snapshot, paymentMethod: undefined }), true);
  assert.equal(matchesPaymentRecordDetailSnapshot(snapshot, { ...snapshot, payScene: "HOME_REPAIR" }), false);
  assert.equal(matchesPaymentRecordDetailSnapshot(snapshot, { ...snapshot, paymentAmount: "67.99" }), false);
  assert.equal(matchesPaymentRecordDetailSnapshot(snapshot, { ...snapshot, businessChainId: "CHAIN-OTHER" }), false);
});

test("rejects empty or mismatched payment event detail responses", async (context) => {
  const validDetail = {
    eventNo: "EVENT-001",
    eventType: "PAYMENT_SUCCESS",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    successFactIssue: null,
    bizNo: "ORDER-001",
    standardOrderNo: "ORDER-001",
    eventTopic: "payment.trade.succeeded.v1",
    downstreamSystem: "ACCOUNTING",
    publishStatus: "PENDING",
    publishStatusType: "warn",
    paymentSuccessHttpStage: "PENDING",
    retryCount: 0,
    eventPayload: "{\"channel\":\"wx_h5\"}",
    createdAt: "2026-09-27 09:59:00"
  };
  assert.deepEqual(
    normalizePaymentEventDetailResult(validDetail, "EVENT-001"),
    validDetail
  );
  assert.equal(normalizePaymentEventDetailResult({
    ...validDetail,
    eventType: "REFUND_SUBMIT",
    standardOrderNo: null
  }, "EVENT-001").standardOrderNo, null);
  assert.equal(normalizePaymentEventDetailResult({
    ...validDetail,
    standardOrderNo: null
  }, "EVENT-001").standardOrderNo, null);
  assert.equal(normalizePaymentEventDetailResult({
    ...validDetail,
    eventType: "REFUND_SUBMIT",
    standardOrderNo: null,
    eventPayload: "[REDACTED]"
  }, "EVENT-001").eventPayload, "[REDACTED]");
  for (const successFactIssue of ["SUCCESS_FACT_MISMATCH", "SUCCESS_TIME_MISSING"]) {
    assert.equal(normalizePaymentEventDetailResult({ ...validDetail, successFactIssue }, "EVENT-001")
      .successFactIssue, successFactIssue);
  }
  for (const successFactIssue of [undefined, "UNKNOWN"]) {
    assert.throws(() => normalizePaymentEventDetailResult({ ...validDetail, successFactIssue }, "EVENT-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-DETAIL");
  }
  assert.doesNotThrow(() => normalizePaymentEventDetailResult({
    ...validDetail, eventType: "PAYMENT_PENDING", paymentSuccessHttpStage: "PENDING"
  }, "EVENT-001"));
  assert.throws(() => normalizePaymentEventDetailResult({
    ...validDetail, eventType: "PAYMENT_PENDING", paymentSuccessHttpStage: "COMPLETED"
  }, "EVENT-001"), (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-DETAIL");
  assert.doesNotThrow(() => normalizePaymentEventDetailResult({
    ...validDetail,
    eventNo: " EVENT-001 ",
    paymentOrderId: " PAY-001 ",
    bizNo: " ORDER-001 ",
    standardOrderNo: " ORDER-001 "
  }, "EVENT-001"));
  assert.throws(
    () => normalizePaymentEventDetailResult({ ...validDetail, businessChainId: "CHAIN-OTHER" }, "EVENT-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentEventDetailResult({ eventNo: "EVENT-002" }, "EVENT-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentEventDetailResult({ ...validDetail, eventType: "" }, "EVENT-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentEventDetailResult({ ...validDetail, paymentOrderId: "" }, "EVENT-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentEventDetailResult({ ...validDetail, bizNo: "" }, "EVENT-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentEventDetailResult({ ...validDetail, standardOrderNo: "" }, "EVENT-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentEventDetailResult({ ...validDetail, standardOrderNo: undefined }, "EVENT-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentEventDetailResult({ ...validDetail, publishStatus: "" }, "EVENT-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentEventDetailResult({ ...validDetail, publishStatus: "FAILED", publishStatusType: "warn" }, "EVENT-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentEventDetailResult({ ...validDetail, paymentSuccessHttpStage: "UNKNOWN" }, "EVENT-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentEventDetailResult({ ...validDetail, paymentSuccessHttpStage: "" }, "EVENT-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-DETAIL"
  );
  for (const eventPayload of [undefined, "", "not-json", "null", "\"payload\"", "[REDACTED]extra"]) {
    assert.throws(
      () => normalizePaymentEventDetailResult({ ...validDetail, eventPayload }, "EVENT-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-DETAIL"
    );
  }
  assert.equal(
    normalizePaymentEventDetailResult({
      ...validDetail,
      publishStatus: "FAILED",
      publishStatusType: "danger",
      lastPublishedAt: "2026-09-27 10:00:00",
      nextRetryAt: "2026-09-27 10:05:00"
    }, "EVENT-001").publishStatus,
    "FAILED"
  );
  for (const invalidDetail of [
    { ...validDetail, publishStatus: "SUCCESS", publishStatusType: "success" },
    { ...validDetail, publishStatus: "FAILED", publishStatusType: "danger", lastPublishedAt: "2026-09-27 10:00:00" },
    { ...validDetail, publishStatus: "FAILED", publishStatusType: "danger", lastPublishedAt: "2026-09-27 09:58:00", createdAt: "2026-09-27 09:59:00" },
    { ...validDetail, lastRepublishOperator: "ops" },
    { ...validDetail, lastRepublishedAt: "not-a-time" },
    {
      ...validDetail,
      publishStatus: "SUCCESS",
      publishStatusType: "success",
      lastPublishedAt: "2026-09-27 10:00:00",
      lastRepublishOperator: "ops",
      lastRepublishedAt: "2026-09-27 10:01:00"
    }
  ]) {
    assert.throws(
      () => normalizePaymentEventDetailResult(invalidDetail, "EVENT-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-DETAIL"
    );
  }
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  await assert.rejects(
    paymentEventApi.getDetail("EVENT-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-DETAIL"
  );
});

test("rejects payment event pages with an invalid row", () => {
  const validRow = {
    eventNo: "EVENT-001",
    eventType: "PAYMENT_SUCCESS",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    successFactIssue: null,
    bizNo: "ORDER-001",
    eventTopic: "payment.trade.succeeded.v1",
    downstreamSystem: "ACCOUNTING",
    publishStatus: "SUCCESS",
    publishStatusType: "success",
    paymentSuccessHttpStage: "COMPLETED",
    retryCount: 1,
    eventPayload: "{\"paymentOrderId\":\"PAY-001\"}",
    createdAt: "2026-09-27 10:00:00",
    lastPublishedAt: "2026-09-27 10:00:01"
  };
  assert.deepEqual(
    normalizePaymentEventListPage({ items: [validRow], total: 1 }).items,
    [validRow]
  );
  assert.equal(normalizePaymentEventListPage({ items: [{
    ...validRow,
    eventType: "REFUND_SUBMIT",
    paymentSuccessHttpStage: "PENDING",
    eventPayload: "[REDACTED]"
  }], total: 1 }).items[0].eventPayload, "[REDACTED]");
  for (const successFactIssue of ["SUCCESS_FACT_MISMATCH", "SUCCESS_TIME_MISSING"]) {
    assert.equal(normalizePaymentEventListPage({ items: [{ ...validRow, successFactIssue }], total: 1 })
      .items[0].successFactIssue, successFactIssue);
  }
  for (const successFactIssue of [undefined, "UNKNOWN"]) {
    assert.throws(() => normalizePaymentEventListPage({ items: [{ ...validRow, successFactIssue }], total: 1 }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-LIST");
  }
  assert.doesNotThrow(() => normalizePaymentEventListPage({
    items: [{ ...validRow, eventType: "PAYMENT_PENDING", paymentSuccessHttpStage: "PENDING" }], total: 1
  }));
  assert.doesNotThrow(() => normalizePaymentEventListPage({
    items: [{ ...validRow, eventType: "PAYMENT_PENDING", paymentSuccessHttpStage: "PENDING",
      publishStatus: "PENDING", publishStatusType: "warn", lastPublishedAt: null,
      nextRetryAt: "2026-09-28 10:00:00" }], total: 1
  }));
  assert.throws(() => normalizePaymentEventListPage({
    items: [{ ...validRow, eventType: "PAYMENT_PENDING", paymentSuccessHttpStage: "PENDING",
      publishStatus: "PENDING", publishStatusType: "warn", lastPublishedAt: null,
      nextRetryAt: "2026-09-27 09:59:59" }], total: 1
  }), (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-LIST");
  assert.throws(() => normalizePaymentEventListPage({
    items: [{ ...validRow, eventType: "PAYMENT_PENDING", paymentSuccessHttpStage: "COMPLETED" }], total: 1
  }), (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-LIST");
  for (const businessChainId of [undefined, "CHAIN-OTHER"]) {
    assert.throws(
      () => normalizePaymentEventListPage({
        items: [{ ...validRow, businessChainId }],
        total: 1
      }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-LIST"
    );
  }
  assert.throws(
    () => normalizePaymentEventListPage({
      items: [{ ...validRow, publishStatus: "UNCONFIRMED", publishStatusType: "warn" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-LIST"
  );
  assert.throws(
    () => normalizePaymentEventListPage({
      items: [{ ...validRow, eventNo: "" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-LIST"
  );
  assert.throws(
    () => normalizePaymentEventListPage({
      items: [{ ...validRow, paymentOrderId: "" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-LIST"
  );
  for (const eventPayload of [undefined, "", "not-json", "null", "\"payload\"", "[REDACTED]extra"]) {
    assert.throws(
      () => normalizePaymentEventListPage({
        items: [{ ...validRow, eventPayload }],
        total: 1
      }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-LIST"
    );
  }
  assert.throws(
    () => normalizePaymentEventListPage({
      items: [{ ...validRow, publishStatus: "FAILED", publishStatusType: "warn" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-LIST"
  );
  assert.throws(
    () => normalizePaymentEventListPage({
      items: [{ ...validRow, paymentSuccessHttpStage: "UNKNOWN" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-LIST"
  );
  assert.throws(
    () => normalizePaymentEventListPage({
      items: [{ ...validRow, createdAt: "not-a-datetime" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-LIST"
  );
  assert.throws(
    () => normalizePaymentEventListPage({
      items: [{ ...validRow, lastPublishedAt: "2026-09-27 09:59:59" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-LIST"
  );
  assert.throws(
    () => normalizePaymentEventListPage({
      items: [{
        ...validRow,
        lastRepublishOperator: "ops",
        lastRepublishedAt: "2026-09-27 10:00:02",
        lastPublishedAt: "2026-09-27 10:00:01"
      }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-LIST"
  );
  assert.throws(
    () => normalizePaymentEventListPage({
      items: [{
        ...validRow,
        publishStatus: "FAILED",
        publishStatusType: "danger",
        nextRetryAt: "2026-09-27 09:59:59"
      }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-LIST"
  );
});

test("requires payment event detail refreshes to preserve payment associations", () => {
  const snapshot = {
    eventNo: "EVENT-001",
    eventType: "PAYMENT_SUCCESS",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    bizNo: "ORDER-001",
    standardOrderNo: "ORDER-001",
    eventTopic: "payment.success",
    downstreamSystem: "ACCOUNTING"
  };
  assert.equal(matchesPaymentEventDetailSnapshot(snapshot, { ...snapshot }), true);
  assert.equal(matchesPaymentEventDetailSnapshot(snapshot, {
    ...snapshot,
    eventNo: " EVENT-001 ",
    eventType: " PAYMENT_SUCCESS ",
    paymentOrderId: " PAY-001 ",
    bizNo: " ORDER-001 ",
    standardOrderNo: " ORDER-001 ",
    eventTopic: " payment.success ",
    downstreamSystem: " ACCOUNTING "
  }), true);
  assert.equal(
    matchesPaymentEventDetailSnapshot(snapshot, { ...snapshot, paymentOrderId: "PAY-002" }),
    false
  );
  assert.equal(
    matchesPaymentEventDetailSnapshot(snapshot, { ...snapshot, bizNo: "ORDER-002" }),
    false
  );
  assert.equal(
    matchesPaymentEventDetailSnapshot(snapshot, { ...snapshot, businessChainId: "CHAIN-OTHER" }),
    false
  );
  assert.equal(
    matchesPaymentEventDetailSnapshot(snapshot, { ...snapshot, downstreamSystem: "SETTLEMENT" }),
    false
  );
});

test("rejects empty or mismatched payment request detail responses", async (context) => {
  assert.deepEqual(
    normalizePaymentRequestDetailResult({ requestNo: "REQ-001", paymentOrderId: "PAY-001", payScene: "HOME_CLEAN", businessChainId: "CHAIN-PAY-001", requestStatus: "成功", requestStatusType: "success", successFactIssue: null, requestPayload: "{}", responsePayload: null, completedAt: "2026-09-27 10:01:00", createdAt: "2026-09-27 10:00:00" }, "REQ-001"),
    { requestNo: "REQ-001", paymentOrderId: "PAY-001", payScene: "HOME_CLEAN", businessChainId: "CHAIN-PAY-001", requestStatus: "成功", requestStatusType: "success", successFactIssue: null, requestPayload: "{}", responsePayload: null, completedAt: "2026-09-27 10:01:00", createdAt: "2026-09-27 10:00:00" }
  );
  assert.doesNotThrow(() => normalizePaymentRequestDetailResult({
    requestNo: " REQ-001 ",
    paymentOrderId: " PAY-001 ",
    payScene: " HOME_CLEAN ",
    businessChainId: "CHAIN-PAY-001",
    requestStatus: "成功",
    requestStatusType: "success",
    successFactIssue: null,
    requestPayload: "{}",
    responsePayload: null,
    completedAt: "2026-09-27 10:01:00",
    createdAt: "2026-09-27 10:00:00"
  }, "REQ-001"));
  const validDetail = {
    requestNo: "REQ-001", paymentOrderId: "PAY-001", payScene: "HOME_CLEAN",
    businessChainId: "CHAIN-PAY-001", requestStatus: "成功", requestStatusType: "success",
    requestPayload: "{}", responsePayload: null, completedAt: "2026-09-27 10:01:00",
    createdAt: "2026-09-27 10:00:00"
  };
  for (const requestStatus of ["渠道提交中", "待人工确认", "待回调", "已关闭"]) {
    const statusType = requestStatus === "已关闭" ? "danger" : "warn";
    assert.equal(normalizePaymentRequestDetailResult({
      ...validDetail, successFactIssue: null, requestStatus,
      requestStatusType: statusType, completedAt: null
    }, "REQ-001").requestStatus, requestStatus);
  }
  assert.throws(() => normalizePaymentRequestDetailResult({
    ...validDetail, successFactIssue: null, requestStatus: "渠道提交中",
    requestStatusType: "success", completedAt: null
  }, "REQ-001"), (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-REQUEST-DETAIL");
  for (const successFactIssue of ["SUCCESS_FACT_MISMATCH", "SUCCESS_TIME_MISSING"]) {
    assert.equal(normalizePaymentRequestDetailResult({ ...validDetail, successFactIssue }, "REQ-001")
      .successFactIssue, successFactIssue);
  }
  for (const successFactIssue of [undefined, "UNKNOWN", ""]) {
    assert.throws(() => normalizePaymentRequestDetailResult({ ...validDetail, successFactIssue }, "REQ-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-REQUEST-DETAIL");
  }
  assert.throws(
    () => normalizePaymentRequestDetailResult({ requestNo: "REQ-001", paymentOrderId: "PAY-001", payScene: "HOME_CLEAN", businessChainId: "CHAIN-OTHER", requestStatus: "成功", requestStatusType: "success", completedAt: "2026-09-27 10:01:00", createdAt: "2026-09-27 10:00:00" }, "REQ-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-REQUEST-DETAIL"
  );
  assert.throws(
    () => normalizePaymentRequestDetailResult({ requestNo: "REQ-002" }, "REQ-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-REQUEST-DETAIL"
  );
  assert.throws(
    () => normalizePaymentRequestDetailResult({ requestNo: "REQ-001", requestStatus: "", requestStatusType: "success" }, "REQ-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-REQUEST-DETAIL"
  );
  assert.throws(
    () => normalizePaymentRequestDetailResult({ requestNo: "REQ-001", requestStatus: "成功", requestStatusType: "success" }, "REQ-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-REQUEST-DETAIL"
  );
  for (const invalidDetail of [
    { requestNo: "REQ-001", paymentOrderId: "PAY-001", requestStatus: "成功" },
    { requestNo: "REQ-001", paymentOrderId: "PAY-001", requestStatus: "成功", requestStatusType: "warn" },
    { requestNo: "REQ-001", paymentOrderId: "PAY-001", requestStatus: "未知", requestStatusType: "warn" },
    { requestNo: "REQ-001", paymentOrderId: "PAY-001", requestStatus: "成功", requestStatusType: "success", completedAt: "not-a-time" },
    { requestNo: "REQ-001", paymentOrderId: "PAY-001", requestStatus: "成功", requestStatusType: "success", completedAt: "2026-09-27 09:59:59", createdAt: "2026-09-27 10:00:00" },
    { requestNo: "REQ-001", paymentOrderId: "PAY-001", requestStatus: "处理中", requestStatusType: "warn", completedAt: "2026-09-27 10:01:00" }
  ]) {
    assert.throws(
      () => normalizePaymentRequestDetailResult(invalidDetail, "REQ-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-REQUEST-DETAIL"
    );
  }
  for (const [field, values] of [
    ["requestPayload", [undefined, "", "not-json", "null", "\"payload\""]],
    ["responsePayload", ["not-json", "null", "\"payload\""]]
  ]) {
    for (const payload of values) {
      assert.throws(
        () => normalizePaymentRequestDetailResult({
          requestNo: "REQ-001",
          paymentOrderId: "PAY-001",
          businessChainId: "CHAIN-PAY-001",
          requestStatus: "成功",
          requestStatusType: "success",
          requestPayload: "{}",
          responsePayload: null,
          [field]: payload,
          completedAt: "2026-09-27 10:01:00",
          createdAt: "2026-09-27 10:00:00"
        }, "REQ-001"),
        (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-REQUEST-DETAIL"
      );
    }
  }
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  await assert.rejects(
    paymentRequestApi.getDetail("REQ-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-REQUEST-DETAIL"
  );
});

test("rejects payment request pages with inconsistent status and associations", () => {
  const validRow = {
    requestNo: "REQ-001",
    paymentOrderId: "PAY-001",
    payScene: "HOME_CLEAN",
    businessChainId: "CHAIN-PAY-001",
    businessChainId: "CHAIN-PAY-001",
    prepayOrderNo: "PREPAY-001",
    orderNo: "ORDER-001",
    paymentMethod: "微信",
    channelCode: "wx_h5",
    routeResult: "主路由命中",
    terminal: "wx_h5",
    clientIp: "192.0.2.1",
    idempotencyKey: "IDEMP-001",
    requestStatus: "处理中",
    requestStatusType: "warn",
    successFactIssue: null,
    requestPayload: "{}",
    responsePayload: null,
    createdAt: "2026-09-27 10:00:00"
  };
  assert.deepEqual(
    normalizePaymentRequestListPage({ items: [validRow], total: 1 }).items,
    [validRow]
  );
  for (const requestStatus of ["渠道提交中", "待人工确认", "待回调", "已关闭"]) {
    const statusType = requestStatus === "已关闭" ? "danger" : "warn";
    assert.equal(normalizePaymentRequestListPage({ items: [{
      ...validRow, requestStatus, requestStatusType: statusType
    }], total: 1 }).items[0].requestStatus, requestStatus);
  }
  assert.throws(() => normalizePaymentRequestListPage({ items: [{
    ...validRow, requestStatus: "渠道提交中", requestStatusType: "success"
  }], total: 1 }), (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-REQUEST-LIST");
  for (const successFactIssue of ["SUCCESS_FACT_MISMATCH", "SUCCESS_TIME_MISSING"]) {
    assert.equal(normalizePaymentRequestListPage({ items: [{ ...validRow, successFactIssue }], total: 1 })
      .items[0].successFactIssue, successFactIssue);
  }
  for (const successFactIssue of [undefined, "UNKNOWN", ""]) {
    assert.throws(() => normalizePaymentRequestListPage({
      items: [{ ...validRow, successFactIssue }], total: 1
    }), (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-REQUEST-LIST");
  }
  for (const businessChainId of [undefined, "CHAIN-OTHER"]) {
    assert.throws(
      () => normalizePaymentRequestListPage({ items: [{ ...validRow, businessChainId }], total: 1 }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-REQUEST-LIST"
    );
  }
  assert.throws(
    () => normalizePaymentRequestListPage({
      items: [{ ...validRow, requestStatus: "未知", requestStatusType: "warn" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-REQUEST-LIST"
  );
  assert.throws(
    () => normalizePaymentRequestListPage({
      items: [{ ...validRow, requestStatus: "失败", requestStatusType: "warn" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-REQUEST-LIST"
  );
  assert.throws(
    () => normalizePaymentRequestListPage({
      items: [{ ...validRow, paymentOrderId: "" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-REQUEST-LIST"
  );
  assert.throws(
    () => normalizePaymentRequestListPage({
      items: [{ ...validRow, channelCode: "" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-REQUEST-LIST"
  );
  for (const [field, values] of [
    ["requestPayload", [undefined, "", "not-json", "null", "\"payload\""]],
    ["responsePayload", ["not-json", "null", "\"payload\""]]
  ]) {
    for (const payload of values) {
      assert.throws(
        () => normalizePaymentRequestListPage({
          items: [{ ...validRow, [field]: payload }],
          total: 1
        }),
        (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-REQUEST-LIST"
      );
    }
  }
  assert.throws(
    () => normalizePaymentRequestListPage({
      items: [{ ...validRow, createdAt: "not-a-datetime" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-REQUEST-LIST"
  );
});

test("applies payment request list validation to API responses", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({
      code: "0",
      data: {
        items: [{
          requestNo: "REQ-001",
          paymentOrderId: "PAY-001",
          businessChainId: "CHAIN-PAY-001",
          prepayOrderNo: "PREPAY-001",
          payScene: "HOME_CLEAN",
          orderNo: "ORDER-001",
          paymentMethod: "微信",
          channelCode: "wx_h5",
          routeResult: null,
          terminal: null,
          clientIp: "192.0.2.*",
          idempotencyKey: "IDEMP-***",
          requestStatus: "等待回调",
          requestStatusType: "warn",
          successFactIssue: null,
          requestPayload: "{}",
          responsePayload: null,
          createdAt: "2026-09-27 10:00:00"
        }],
        total: 1,
        pageNo: 1,
        pageSize: 20
      }
    })
  }));

  const result = await paymentRequestApi.getList();
  assert.equal(result.items[0].requestNo, "REQ-001");
});

test("requires payment request detail refreshes to preserve payment associations", () => {
  const snapshot = {
    requestNo: "REQ-001",
    paymentOrderId: "PAY-001",
    payScene: "HOME_CLEAN",
    businessChainId: "CHAIN-PAY-001",
    prepayOrderNo: "PRE-001",
    orderNo: "ORDER-001",
    standardOrderNo: "ORDER-001",
    billNo: "BILL-001",
    paymentMethod: "微信支付",
    routeNo: "ROUTE-001"
  };
  assert.equal(matchesPaymentRequestDetailSnapshot(snapshot, { ...snapshot }), true);
  assert.equal(matchesPaymentRequestDetailSnapshot(snapshot, {
    ...snapshot,
    requestNo: " REQ-001 ",
    paymentOrderId: " PAY-001 ",
    payScene: " HOME_CLEAN ",
    prepayOrderNo: " PRE-001 ",
    orderNo: " ORDER-001 ",
    standardOrderNo: " ORDER-001 ",
    billNo: " BILL-001 ",
    routeNo: " ROUTE-001 "
  }), true);
  assert.equal(
    matchesPaymentRequestDetailSnapshot(snapshot, { ...snapshot, paymentOrderId: "PAY-002" }),
    false
  );
  assert.equal(
    matchesPaymentRequestDetailSnapshot(snapshot, { ...snapshot, payScene: "HOME_REPAIR" }),
    false
  );
  assert.equal(
    matchesPaymentRequestDetailSnapshot(snapshot, { ...snapshot, orderNo: "ORDER-002" }),
    false
  );
  assert.equal(
    matchesPaymentRequestDetailSnapshot(snapshot, { ...snapshot, businessChainId: "CHAIN-OTHER" }),
    false
  );
  assert.equal(
    matchesPaymentRequestDetailSnapshot(snapshot, { ...snapshot, routeNo: "ROUTE-002" }),
    false
  );
  assert.equal(
    matchesPaymentRequestDetailSnapshot(snapshot, { ...snapshot, paymentMethod: "支付宝" }),
    false
  );
  assert.equal(
    matchesPaymentRequestDetailSnapshot(snapshot, { ...snapshot, paymentMethod: undefined }),
    true
  );
});

test("rejects empty or mismatched payment flow detail responses", async (context) => {
  assert.deepEqual(
    normalizePaymentFlowDetailResult({
      flowSource: "PAYMENT_ATTEMPT",
      flowNo: "FLOW-001",
      paymentOrderId: "PAY-001",
      businessChainId: "CHAIN-PAY-001",
      flowType: "支付尝试",
      flowTypeTag: "info",
      businessStatus: "成功",
      businessStatusType: "success",
      successFactIssue: null,
      completedAt: "2026-09-27 10:01:00",
      createdAt: "2026-09-27 10:00:00"
    }, "PAYMENT_ATTEMPT", "FLOW-001"),
    {
      flowSource: "PAYMENT_ATTEMPT",
      flowNo: "FLOW-001",
      paymentOrderId: "PAY-001",
      businessChainId: "CHAIN-PAY-001",
      flowType: "支付尝试",
      flowTypeTag: "info",
      businessStatus: "成功",
      businessStatusType: "success",
      successFactIssue: null,
      completedAt: "2026-09-27 10:01:00",
      createdAt: "2026-09-27 10:00:00"
    }
  );
  assert.doesNotThrow(() => normalizePaymentFlowDetailResult({
    flowSource: " PAYMENT_ATTEMPT ",
    flowNo: " FLOW-001 ",
    paymentOrderId: " PAY-001 ",
    businessChainId: "CHAIN-PAY-001",
    flowType: "支付尝试",
    flowTypeTag: "info",
    businessStatus: "成功",
    businessStatusType: "success",
    successFactIssue: null,
    completedAt: "2026-09-27 10:01:00",
    createdAt: "2026-09-27 10:00:00"
  }, "PAYMENT_ATTEMPT", "FLOW-001"));
  assert.throws(
    () => normalizePaymentFlowDetailResult({
      flowSource: "PAYMENT_ATTEMPT",
      flowNo: "FLOW-001",
      paymentOrderId: "PAY-001",
      businessChainId: "CHAIN-OTHER",
      flowType: "支付尝试",
      flowTypeTag: "info",
      businessStatus: "成功",
      businessStatusType: "success",
      completedAt: "2026-09-27 10:01:00",
      createdAt: "2026-09-27 10:00:00"
    }, "PAYMENT_ATTEMPT", "FLOW-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-FLOW-DETAIL"
  );
  assert.throws(
    () => normalizePaymentFlowDetailResult({ flowSource: "ROUTE_RECORD", flowNo: "FLOW-001" }, "PAYMENT_ATTEMPT", "FLOW-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-FLOW-DETAIL"
  );
  assert.throws(
    () => normalizePaymentFlowDetailResult({
      flowSource: "PAYMENT_ATTEMPT",
      flowNo: "FLOW-001",
      flowType: "",
      flowTypeTag: "info",
      businessStatus: "SUCCESS"
    }, "PAYMENT_ATTEMPT", "FLOW-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-FLOW-DETAIL"
  );
  assert.throws(
    () => normalizePaymentFlowDetailResult({
      flowSource: "PAYMENT_ATTEMPT",
      flowNo: "FLOW-001",
      flowType: "支付尝试",
      flowTypeTag: "info",
      businessStatus: "SUCCESS",
      businessStatusType: "success"
    }, "PAYMENT_ATTEMPT", "FLOW-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-FLOW-DETAIL"
  );
  assert.throws(
    () => normalizePaymentFlowDetailResult({
      flowSource: "PAYMENT_ATTEMPT",
      flowNo: "FLOW-001",
      flowType: "支付尝试",
      flowTypeTag: "info",
      businessStatus: "成功",
      businessStatusType: "success",
      completedAt: "not-a-time"
    }, "PAYMENT_ATTEMPT", "FLOW-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-FLOW-DETAIL"
  );
  assert.throws(
    () => normalizePaymentFlowDetailResult({
      flowSource: "PAYMENT_ATTEMPT",
      flowNo: "FLOW-001",
      paymentOrderId: "PAY-001",
      flowType: "支付尝试",
      flowTypeTag: "info",
      businessStatus: "成功",
      businessStatusType: "success",
      completedAt: "2026-09-27 09:59:59",
      createdAt: "2026-09-27 10:00:00"
    }, "PAYMENT_ATTEMPT", "FLOW-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-FLOW-DETAIL"
  );
  assert.throws(
    () => normalizePaymentFlowDetailResult({
      flowSource: "PAYMENT_ATTEMPT",
      flowNo: "FLOW-001",
      flowType: "支付尝试",
      flowTypeTag: "info",
      businessStatus: ""
    }, "PAYMENT_ATTEMPT", "FLOW-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-FLOW-DETAIL"
  );
  assert.throws(
    () => normalizePaymentFlowDetailResult({
      flowSource: "PAYMENT_ATTEMPT",
      flowNo: "FLOW-001",
      flowType: "支付尝试",
      flowTypeTag: "success",
      businessStatus: "SUCCESS",
      businessStatusType: "success"
    }, "PAYMENT_ATTEMPT", "FLOW-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-FLOW-DETAIL"
  );
  const validBusinessEventDetail = {
    flowSource: "BUSINESS_EVENT",
    flowNo: "EVENT-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    flowType: "业务事件",
    flowTypeTag: "success",
    businessStatus: "PAYMENT_SUCCESS",
    businessStatusType: "success",
    successFactIssue: null,
    downstreamSystem: "ACCOUNTING",
    eventTopic: "payment.trade.succeeded.v1",
    publishStatus: "SUCCESS",
    retryCount: 1,
    requestPayload: "{\"channel\":\"wx_h5\"}",
    createdAt: "2026-09-27 10:00:00"
  };
  assert.doesNotThrow(() => normalizePaymentFlowDetailResult(
    validBusinessEventDetail,
    "BUSINESS_EVENT",
    "EVENT-001"
  ));
  assert.equal(normalizePaymentFlowDetailResult({
    ...validBusinessEventDetail,
    businessStatus: "REFUND_SUBMIT",
    businessStatusType: "info",
    requestPayload: "[REDACTED]"
  }, "BUSINESS_EVENT", "EVENT-001").requestPayload, "[REDACTED]");
  for (const successFactIssue of ["SUCCESS_FACT_MISMATCH", "SUCCESS_TIME_MISSING"]) {
    assert.equal(normalizePaymentFlowDetailResult({ ...validBusinessEventDetail, successFactIssue },
      "BUSINESS_EVENT", "EVENT-001").successFactIssue, successFactIssue);
  }
  for (const successFactIssue of [undefined, "UNKNOWN", ""]) {
    assert.throws(() => normalizePaymentFlowDetailResult({ ...validBusinessEventDetail, successFactIssue },
      "BUSINESS_EVENT", "EVENT-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-FLOW-DETAIL");
  }
  for (const requestPayload of [undefined, "", "not-json", "null", "\"payload\""]) {
    assert.throws(
      () => normalizePaymentFlowDetailResult(
        { ...validBusinessEventDetail, requestPayload },
        "BUSINESS_EVENT",
        "EVENT-001"
      ),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-FLOW-DETAIL"
    );
  }
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  await assert.rejects(
    paymentFlowApi.getDetail("PAYMENT_ATTEMPT", "FLOW-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-FLOW-DETAIL"
  );
});

test("rejects payment flow pages with inconsistent source facts", () => {
  const validAttempt = {
    flowSource: "PAYMENT_ATTEMPT",
    flowNo: "FLOW-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    flowType: "支付尝试",
    flowTypeTag: "info",
    businessStatus: "处理中",
    businessStatusType: "warn",
    successFactIssue: null,
    summary: "方式=wx_h5 / 状态=处理中",
    createdAt: "2026-09-27 10:00:00",
    publishStatus: null,
    retryCount: null
  };
  const validEvent = {
    flowSource: "BUSINESS_EVENT",
    flowNo: "EVENT-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    flowType: "业务事件",
    flowTypeTag: "success",
    businessStatus: "PAYMENT_SUCCESS",
    businessStatusType: "success",
    successFactIssue: null,
    downstreamSystem: "ACCOUNTING",
    eventTopic: "payment.trade.succeeded.v1",
    publishStatus: "SUCCESS",
    retryCount: 1,
    requestPayload: "{\"channel\":\"wx_h5\"}",
    summary: "事件=PAYMENT_SUCCESS / 发布=SUCCESS",
    createdAt: "2026-09-27 10:00:00"
  };
  assert.deepEqual(
    normalizePaymentFlowListPage({ items: [validAttempt, validEvent], total: 2 }).items,
    [validAttempt, validEvent]
  );
  assert.equal(normalizePaymentFlowListPage({ items: [{
    ...validEvent,
    businessStatus: "REFUND_SUBMIT",
    businessStatusType: "info",
    requestPayload: "[REDACTED]"
  }], total: 1 }).items[0].requestPayload, "[REDACTED]");
  for (const successFactIssue of ["SUCCESS_FACT_MISMATCH", "SUCCESS_TIME_MISSING"]) {
    assert.equal(normalizePaymentFlowListPage({ items: [{ ...validAttempt, successFactIssue }], total: 1 })
      .items[0].successFactIssue, successFactIssue);
  }
  for (const successFactIssue of [undefined, "UNKNOWN", ""]) {
    assert.throws(() => normalizePaymentFlowListPage({
      items: [{ ...validAttempt, successFactIssue }], total: 1
    }), (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-FLOW-LIST");
  }
  for (const businessChainId of [undefined, "CHAIN-OTHER"]) {
    assert.throws(
      () => normalizePaymentFlowListPage({ items: [{ ...validAttempt, businessChainId }], total: 1 }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-FLOW-LIST"
    );
  }
  assert.throws(
    () => normalizePaymentFlowListPage({
      items: [{ ...validAttempt, flowTypeTag: "success" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-FLOW-LIST"
  );
  assert.throws(
    () => normalizePaymentFlowListPage({
      items: [{ ...validEvent, publishStatus: "UNKNOWN_STATUS" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-FLOW-LIST"
  );
  assert.throws(
    () => normalizePaymentFlowListPage({
      items: [{ ...validAttempt, retryCount: 1 }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-FLOW-LIST"
  );
  for (const requestPayload of [undefined, "", "not-json", "null", "\"payload\""]) {
    assert.throws(
      () => normalizePaymentFlowListPage({
        items: [{ ...validEvent, requestPayload }],
        total: 1
      }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-FLOW-LIST"
    );
  }
  assert.throws(
    () => normalizePaymentFlowListPage({
      items: [{ ...validAttempt, createdAt: "not-a-datetime" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-FLOW-LIST"
  );
});

test("requires payment flow detail refreshes to preserve payment associations", () => {
  const snapshot = {
    flowSource: "PAYMENT_ATTEMPT",
    flowNo: "FLOW-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    standardOrderNo: "ORDER-001",
    prepayOrderNo: "PRE-001",
    payScene: "HOME_CLEAN",
    billNo: "BILL-001",
    paymentMethod: "微信支付",
    paymentRequestNo: "REQ-001",
    routeNo: "ROUTE-001"
  };
  assert.equal(matchesPaymentFlowDetailSnapshot(snapshot, { ...snapshot }), true);
  assert.equal(matchesPaymentFlowDetailSnapshot(snapshot, {
    ...snapshot,
    flowSource: " PAYMENT_ATTEMPT ",
    flowNo: " FLOW-001 ",
    paymentOrderId: " PAY-001 ",
    orderNo: " ORDER-001 ",
    standardOrderNo: " ORDER-001 ",
    prepayOrderNo: " PRE-001 ",
    paymentRequestNo: " REQ-001 ",
    routeNo: " ROUTE-001 "
  }), true);
  assert.equal(
    matchesPaymentFlowDetailSnapshot(snapshot, { ...snapshot, paymentOrderId: "PAY-002" }),
    false
  );
  assert.equal(
    matchesPaymentFlowDetailSnapshot(snapshot, { ...snapshot, payScene: "HOME_REPAIR" }),
    false
  );
  assert.equal(
    matchesPaymentFlowDetailSnapshot(snapshot, { ...snapshot, orderNo: "ORDER-002" }),
    false
  );
  assert.equal(
    matchesPaymentFlowDetailSnapshot(snapshot, { ...snapshot, routeNo: "ROUTE-002" }),
    false
  );
  assert.equal(
    matchesPaymentFlowDetailSnapshot(snapshot, { ...snapshot, businessChainId: "CHAIN-OTHER" }),
    false
  );
  assert.equal(
    matchesPaymentFlowDetailSnapshot(snapshot, { ...snapshot, billNo: "BILL-002" }),
    false
  );
  assert.equal(
    matchesPaymentFlowDetailSnapshot(snapshot, { ...snapshot, paymentMethod: "支付宝" }),
    false
  );
  assert.equal(
    matchesPaymentFlowDetailSnapshot(snapshot, { ...snapshot, billNo: undefined, paymentMethod: undefined }),
    true
  );
});

test("rejects empty or mismatched payment route detail responses", async (context) => {
  const validDetail = {
    routeNo: "ROUTE-001",
    requestNo: "REQ-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    prepayOrderNo: "PREPAY-001",
    amount: "68.00",
    channelCode: "wx_h5",
    routeRule: "customer_channel=wechat",
    routeResult: "微信JSAPI",
    routeResultType: "success",
    successFactIssue: null,
    createdAt: "2026-09-27 10:00:00"
  };
  assert.deepEqual(
    normalizePaymentRouteDetailResult(validDetail, "ROUTE-001"),
    validDetail
  );
  assert.doesNotThrow(() => normalizePaymentRouteDetailResult({
    ...validDetail,
    routeNo: " ROUTE-001 ",
    requestNo: " REQ-001 ",
    paymentOrderId: " PAY-001 ",
    orderNo: " ORDER-001 ",
    prepayOrderNo: " PREPAY-001 "
  }, "ROUTE-001"));
  for (const successFactIssue of ["SUCCESS_FACT_MISMATCH", "SUCCESS_TIME_MISSING"]) {
    assert.equal(normalizePaymentRouteDetailResult({ ...validDetail, successFactIssue }, "ROUTE-001")
      .successFactIssue, successFactIssue);
  }
  for (const successFactIssue of [undefined, "UNKNOWN", ""]) {
    assert.throws(() => normalizePaymentRouteDetailResult({ ...validDetail, successFactIssue }, "ROUTE-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ROUTE-DETAIL");
  }
  assert.throws(
    () => normalizePaymentRouteDetailResult({ ...validDetail, businessChainId: "CHAIN-OTHER" }, "ROUTE-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ROUTE-DETAIL"
  );
  assert.throws(
    () => normalizePaymentRouteDetailResult({ routeNo: "ROUTE-002" }, "ROUTE-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ROUTE-DETAIL"
  );
  assert.throws(
    () => normalizePaymentRouteDetailResult({ ...validDetail, amount: "0" }, "ROUTE-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ROUTE-DETAIL"
  );
  assert.throws(
    () => normalizePaymentRouteDetailResult({ ...validDetail, routeResult: "" }, "ROUTE-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ROUTE-DETAIL"
  );
  assert.throws(
    () => normalizePaymentRouteDetailResult({ ...validDetail, routeResultType: "warn" }, "ROUTE-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ROUTE-DETAIL"
  );
  assert.throws(
    () => normalizePaymentRouteDetailResult({ ...validDetail, requestNo: "" }, "ROUTE-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ROUTE-DETAIL"
  );
  assert.throws(
    () => normalizePaymentRouteDetailResult({ ...validDetail, orderNo: "" }, "ROUTE-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ROUTE-DETAIL"
  );
  assert.throws(
    () => normalizePaymentRouteDetailResult({ ...validDetail, prepayOrderNo: "" }, "ROUTE-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ROUTE-DETAIL"
  );
  for (const field of ["requestPayload", "responsePayload"]) {
    for (const payload of ["not-json", "null", "\"payload\""]) {
      assert.throws(
        () => normalizePaymentRouteDetailResult({ ...validDetail, [field]: payload }, "ROUTE-001"),
        (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ROUTE-DETAIL"
      );
    }
  }
  assert.throws(
    () => normalizePaymentRouteDetailResult({ ...validDetail, createdAt: "not-a-time" }, "ROUTE-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ROUTE-DETAIL"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  await assert.rejects(
    paymentRouteApi.getDetail("ROUTE-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ROUTE-DETAIL"
  );
});

test("rejects payment route pages with inconsistent route facts", () => {
  const validRow = {
    routeNo: "ROUTE-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    amount: "68.00",
    channelCode: "wx_h5",
    routeRule: "customer_channel=wechat",
    routeResult: "微信JSAPI",
    routeResultType: "success",
    successFactIssue: null,
    createdAt: "2026-09-27 10:00:00"
  };
  assert.deepEqual(
    normalizePaymentRouteListPage({ items: [validRow], total: 1 }).items,
    [validRow]
  );
  for (const successFactIssue of ["SUCCESS_FACT_MISMATCH", "SUCCESS_TIME_MISSING"]) {
    assert.equal(normalizePaymentRouteListPage({ items: [{ ...validRow, successFactIssue }], total: 1 })
      .items[0].successFactIssue, successFactIssue);
  }
  for (const successFactIssue of [undefined, "UNKNOWN", ""]) {
    assert.throws(() => normalizePaymentRouteListPage({
      items: [{ ...validRow, successFactIssue }], total: 1
    }), (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ROUTE-LIST");
  }
  assert.throws(
    () => normalizePaymentRouteListPage({
      items: [{ ...validRow, routeResult: "兜底路由", routeResultType: "success" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ROUTE-LIST"
  );
  assert.throws(
    () => normalizePaymentRouteListPage({
      items: [{ ...validRow, routeNo: "" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ROUTE-LIST"
  );
  assert.throws(
    () => normalizePaymentRouteListPage({
      items: [{ ...validRow, amount: "0" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ROUTE-LIST"
  );
  for (const field of ["requestPayload", "responsePayload"]) {
    for (const payload of ["not-json", "null", "\"payload\""]) {
      assert.throws(
        () => normalizePaymentRouteListPage({
          items: [{ ...validRow, [field]: payload }],
          total: 1
        }),
        (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ROUTE-LIST"
      );
    }
  }
  assert.throws(
    () => normalizePaymentRouteListPage({
      items: [{ ...validRow, createdAt: "not-a-time" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ROUTE-LIST"
  );
  assert.throws(
    () => normalizePaymentRouteListPage({
      items: [{ ...validRow, businessChainId: "CHAIN-OTHER" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ROUTE-LIST"
  );
});

test("requires payment route detail refreshes to preserve payment associations and amount", () => {
  const snapshot = {
    routeNo: "ROUTE-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    requestNo: "REQ-001",
    orderNo: "ORDER-001",
    paymentMethod: "微信支付",
    customerName: "张三",
    amount: "68.00",
    channelCode: "WX_H5"
  };
  assert.equal(
    matchesPaymentRouteDetailSnapshot(snapshot, { ...snapshot, amount: "¥68.00" }),
    true
  );
  assert.equal(matchesPaymentRouteDetailSnapshot(snapshot, {
    ...snapshot,
    routeNo: " ROUTE-001 ",
    paymentOrderId: " PAY-001 ",
    requestNo: " REQ-001 ",
    orderNo: " ORDER-001 "
  }), true);
  assert.equal(
    matchesPaymentRouteDetailSnapshot(snapshot, { ...snapshot, paymentOrderId: "PAY-002" }),
    false
  );
  assert.equal(matchesPaymentRouteDetailSnapshot(snapshot, {
    ...snapshot,
    businessChainId: "CHAIN-OTHER"
  }), false);
  assert.equal(matchesPaymentRouteDetailSnapshot(snapshot, {
    ...snapshot,
    channelCode: "ALI_H5"
  }), false);
  assert.equal(matchesPaymentRouteDetailSnapshot(snapshot, {
    ...snapshot,
    paymentMethod: "支付宝"
  }), false);
  assert.equal(matchesPaymentRouteDetailSnapshot(snapshot, {
    ...snapshot,
    customerName: "李四"
  }), false);
  assert.equal(matchesPaymentRouteDetailSnapshot(snapshot, {
    ...snapshot,
    paymentMethod: undefined,
    customerName: undefined
  }), true);
  assert.equal(
    matchesPaymentRouteDetailSnapshot(snapshot, { ...snapshot, orderNo: "ORDER-002" }),
    false
  );
  assert.equal(
    matchesPaymentRouteDetailSnapshot(snapshot, { ...snapshot, amount: "67.99" }),
    false
  );
});

test("rejects empty or mismatched cashier session detail responses", async (context) => {
  const validDetail = {
    sessionNo: "SESSION-001",
    prepayOrderNo: "PREPAY-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    billNo: "BILL-001",
    customerName: "客户一",
    cashierTitle: "家政服务收银台",
    payScene: "HOME_CLEAN",
    terminal: "wx_h5",
    amount: "68.00",
    sessionStatus: "待支付",
    sessionStatusType: "warn",
    createdAt: "2026-09-27 10:00:00",
    expiresAt: "2026-09-27 11:00:00"
  };
  assert.deepEqual(
    normalizeCashierSessionDetailResult(validDetail, "SESSION-001"),
    validDetail
  );
  const submittedDetail = { ...validDetail, sessionStatus: "支付中", sessionStatusType: "info" };
  assert.deepEqual(
    normalizeCashierSessionDetailResult(submittedDetail, "SESSION-001"),
    submittedDetail
  );
  for (const [sessionStatus, sessionStatusType] of [
    ["待回调", "warn"], ["结果待确认", "danger"], ["已关闭", "danger"],
    ["待风控复核", "warn"], ["风控拦截", "danger"], ["风控拒绝", "danger"],
    ["成功事实异常", "danger"]
  ]) {
    const detail = { ...validDetail, sessionStatus, sessionStatusType };
    assert.deepEqual(normalizeCashierSessionDetailResult(detail, "SESSION-001"), detail);
    assert.throws(
      () => normalizeCashierSessionDetailResult({ ...detail, sessionStatusType: "success" }, "SESSION-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER-SESSION-DETAIL"
    );
  }
  assert.throws(
    () => normalizeCashierSessionDetailResult(
      { ...validDetail, sessionStatusType: "info" }, "SESSION-001"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER-SESSION-DETAIL"
  );
  assert.equal(
    normalizeCashierSessionDetailResult({ ...validDetail, sessionNo: " SESSION-001 " }, " SESSION-001 ").sessionNo,
    " SESSION-001 "
  );
  assert.equal(
    normalizeCashierSessionDetailResult({ ...validDetail, paymentOrderId: null }, "SESSION-001").paymentOrderId,
    null
  );
  assert.throws(
    () => normalizeCashierSessionDetailResult({ ...validDetail, businessChainId: "CHAIN-OTHER" }, "SESSION-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER-SESSION-DETAIL"
  );
  assert.throws(
    () => normalizeCashierSessionDetailResult({ sessionNo: "SESSION-002" }, "SESSION-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER-SESSION-DETAIL"
  );
  assert.throws(
    () => normalizeCashierSessionDetailResult({ sessionNo: "SESSION-001", amount: "0", sessionStatus: "待支付", sessionStatusType: "warn" }, "SESSION-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER-SESSION-DETAIL"
  );
  assert.throws(
    () => normalizeCashierSessionDetailResult({ sessionNo: "SESSION-001", amount: "68.00", sessionStatus: "", sessionStatusType: "warn" }, "SESSION-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER-SESSION-DETAIL"
  );
  assert.throws(
    () => normalizeCashierSessionDetailResult({ sessionNo: "SESSION-001", amount: "68.00", sessionStatus: "待支付", sessionStatusType: "warn" }, "SESSION-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER-SESSION-DETAIL"
  );
  assert.throws(
    () => normalizeCashierSessionDetailResult({ ...validDetail, createdAt: "2026-09-27 11:00:00", expiresAt: "2026-09-27 10:00:00" }, "SESSION-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER-SESSION-DETAIL"
  );
  for (const field of [
    "prepayOrderNo", "orderNo", "billNo", "customerName", "cashierTitle", "payScene", "terminal"
  ]) {
    assert.throws(
      () => normalizeCashierSessionDetailResult({ ...validDetail, [field]: "" }, "SESSION-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER-SESSION-DETAIL"
    );
  }
  for (const invalidDetail of [
    { sessionNo: "SESSION-001", amount: "68.00", sessionStatus: "待支付" },
    { sessionNo: "SESSION-001", amount: "68.00", sessionStatus: "待支付", sessionStatusType: "success" },
    { sessionNo: "SESSION-001", amount: "68.00", sessionStatus: "未知", sessionStatusType: "warn" }
  ]) {
    assert.throws(
      () => normalizeCashierSessionDetailResult(invalidDetail, "SESSION-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER-SESSION-DETAIL"
    );
  }
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  await assert.rejects(
    cashierSessionApi.getDetail("SESSION-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER-SESSION-DETAIL"
  );
});

test("rejects cashier session pages with inconsistent amount and status facts", () => {
  const validRow = {
    sessionNo: "SESSION-001",
    prepayOrderNo: "PREPAY-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    billNo: "BILL-001",
    customerName: "客户一",
    cashierTitle: "家政服务收银台",
    payScene: "HOME_CLEAN",
    terminal: "wx_h5",
    amount: "¥68.00",
    sessionStatus: "支付中",
    sessionStatusType: "warn",
    createdAt: "2026-09-27 10:00:00",
    expiresAt: "2026-09-27 11:00:00"
  };
  assert.deepEqual(
    normalizeCashierSessionListPage({ items: [validRow], total: 1 }).items,
    [validRow]
  );
  const submittedRow = { ...validRow, sessionStatusType: "info" };
  assert.deepEqual(
    normalizeCashierSessionListPage({ items: [submittedRow], total: 1 }).items,
    [submittedRow]
  );
  for (const [sessionStatus, sessionStatusType] of [
    ["待回调", "warn"], ["结果待确认", "danger"], ["已关闭", "danger"],
    ["待风控复核", "warn"], ["风控拦截", "danger"], ["风控拒绝", "danger"],
    ["成功事实异常", "danger"]
  ]) {
    const row = { ...validRow, sessionStatus, sessionStatusType };
    assert.deepEqual(normalizeCashierSessionListPage({ items: [row], total: 1 }).items, [row]);
    assert.throws(
      () => normalizeCashierSessionListPage({ items: [{ ...row, sessionStatusType: "success" }], total: 1 }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER-SESSION-LIST"
    );
  }
  assert.throws(
    () => normalizeCashierSessionListPage({
      items: [{ ...validRow, sessionStatus: "待支付", sessionStatusType: "info" }], total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER-SESSION-LIST"
  );
  assert.throws(
    () => normalizeCashierSessionListPage({
      items: [{ ...validRow, sessionStatus: "UNKNOWN", sessionStatusType: "warn" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER-SESSION-LIST"
  );
  assert.throws(
    () => normalizeCashierSessionListPage({
      items: [{ ...validRow, sessionStatus: "已失效", sessionStatusType: "warn" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER-SESSION-LIST"
  );
  assert.throws(
    () => normalizeCashierSessionListPage({
      items: [{ ...validRow, amount: "0" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER-SESSION-LIST"
  );
  assert.throws(
    () => normalizeCashierSessionListPage({
      items: [{ ...validRow, terminal: "" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER-SESSION-LIST"
  );
  assert.throws(
    () => normalizeCashierSessionListPage({
      items: [{ ...validRow, expiresAt: "not-a-time" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER-SESSION-LIST"
  );
  assert.throws(
    () => normalizeCashierSessionListPage({
      items: [{ ...validRow, businessChainId: "CHAIN-OTHER" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER-SESSION-LIST"
  );
});

test("applies cashier session list validation to API responses", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({
      code: "0",
      data: {
        items: [{
          sessionNo: "SESSION-001",
          prepayOrderNo: "PREPAY-001",
          paymentOrderId: "PAY-001",
          businessChainId: "CHAIN-PAY-001",
          orderNo: "ORDER-001",
          billNo: "BILL-001",
          customerName: "客户一",
          cashierTitle: "家政服务收银台",
          payScene: "HOME_CLEAN",
          terminal: "wx_h5",
          amount: "¥68.00",
          sessionStatus: "支付成功",
          sessionStatusType: "success",
          createdAt: "2026-09-27 10:00:00",
          expiresAt: "2026-09-27 11:00:00"
        }],
        total: 1,
        pageNo: 1,
        pageSize: 20
      }
    })
  }));

  const result = await cashierSessionApi.getList();
  assert.equal(result.items[0].sessionNo, "SESSION-001");
});

test("requires cashier session detail refreshes to preserve payment associations and amount", () => {
  const snapshot = {
    sessionNo: "SESSION-001",
    prepayOrderNo: "PRE-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    billNo: "BILL-001",
    customerName: "张三",
    amount: "68.00"
  };
  assert.equal(
    matchesCashierSessionDetailSnapshot(snapshot, { ...snapshot, amount: "¥68.00" }),
    true
  );
  assert.equal(
    matchesCashierSessionDetailSnapshot(
      snapshot,
      {
        ...snapshot,
        sessionNo: " SESSION-001 ",
        prepayOrderNo: " PRE-001 ",
        paymentOrderId: " PAY-001 ",
        orderNo: " ORDER-001 ",
        amount: "¥68.00"
      }
    ),
    true
  );
  assert.equal(
    matchesCashierSessionDetailSnapshot(snapshot, { ...snapshot, paymentOrderId: "PAY-002" }),
    false
  );
  assert.equal(
    matchesCashierSessionDetailSnapshot(snapshot, { ...snapshot, billNo: "BILL-002" }),
    false
  );
  assert.equal(
    matchesCashierSessionDetailSnapshot(snapshot, { ...snapshot, customerName: "李四" }),
    false
  );
  assert.equal(
    matchesCashierSessionDetailSnapshot(snapshot, { ...snapshot, billNo: undefined, customerName: undefined }),
    true
  );
  assert.equal(
    matchesCashierSessionDetailSnapshot(snapshot, { ...snapshot, orderNo: "ORDER-002" }),
    false
  );
  assert.equal(
    matchesCashierSessionDetailSnapshot(snapshot, { ...snapshot, amount: "67.99" }),
    false
  );
  assert.equal(
    matchesCashierSessionDetailSnapshot(snapshot, { ...snapshot, businessChainId: "CHAIN-OTHER" }),
    false
  );
});

test("rejects empty results from channel status write actions", async (context) => {
  assert.deepEqual(
    normalizePaymentConfigIssueDutyRosterReviewUpdateResult([{ rosterCode: "ROSTER-001" }], "ROSTER-001"),
    [{ rosterCode: "ROSTER-001" }]
  );
  assert.throws(
    () => normalizePaymentConfigIssueDutyRosterReviewUpdateResult([{ rosterCode: "ROSTER-002" }], "ROSTER-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  assert.deepEqual(
    normalizePaymentConfigAlertProviderReviewUpdateResult([{ providerCode: "PROVIDER-001" }], "PROVIDER-001"),
    [{ providerCode: "PROVIDER-001" }]
  );
  assert.throws(
    () => normalizePaymentConfigAlertProviderReviewUpdateResult([{ providerCode: "PROVIDER-002" }], "PROVIDER-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  assert.deepEqual(
    requireNonEmptyCollection([{ requestNo: "REQ-001" }], "CONFIG", "missing"),
    [{ requestNo: "REQ-001" }]
  );
  assert.throws(
    () => requireNonEmptyCollection([], "PAYMENT-CLIENT-INVALID-CONFIG-ACTION", "missing"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));
  await assert.rejects(
    paymentConfigApi.submitChannelStatusReview({ channelCode: "wx_h5" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.submitChannelReview({ candidate: { channelCode: "wx_h5" } }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.submitRouteRuleStatusReview({ ruleCode: "RULE-001" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.toggleProtocol({ protocolCode: "PROTOCOL-001" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.submitRouteRuleReview({ candidate: { ruleCode: "RULE-001" } }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.submitProtocolReview({ candidate: { protocolCode: "PROTOCOL-001" } }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.submitGatewayStatusReview({ gatewayCode: "GATEWAY-001" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.submitGatewayReview({ candidate: { gatewayCode: "GATEWAY-001" } }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.submitControlPolicyStatusReview({ sourceAppId: "APP-001" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.submitControlPolicyReview({ candidate: { sourceAppId: "APP-001" } }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.submitReturnCodeStatusReview({ code: "RETURN-001" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.submitReturnCodeImportReview({ importNo: "IMPORT-001" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.toggleIssueDutyRoster({ rosterCode: "ROSTER-001" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.createIssueDutyRoster({ rosterCode: "ROSTER-001" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.toggleAlertProvider({ providerCode: "PROVIDER-001" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.createAlertProvider({ providerCode: "PROVIDER-001" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.updateAlertProvider("PROVIDER-001", { displayName: "updated" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.updateIssueDutyRoster("ROSTER-001", { displayName: "updated" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.rollbackChannel("wx_h5", { versionNo: 1 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.rollbackGateway("gateway_default", { versionNo: 1 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.rollbackProtocol("protocol_default", { versionNo: 1 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.rollbackControlPolicy("APP-001", { versionNo: 1 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.submitRouteRuleRollbackReview("RULE-001", { versionNo: 1 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.approveRouteRuleReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.rejectRouteRuleReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.approveProtocolReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.approveControlPolicyReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.rejectProtocolReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.approveChannelReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.rejectChannelReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.approveIssueDutyRosterReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.rejectIssueDutyRosterReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.approveAlertProviderReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.rejectAlertProviderReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.approveReturnCodeReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.rejectReturnCodeReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.approveReturnCodeImportReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.rejectReturnCodeImportReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.approveGatewayReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.rejectGatewayReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.rejectControlPolicyReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects an empty alert acknowledgement result", async (context) => {
  assert.deepEqual(
    normalizePaymentIssueAlertActionResult(
      { alertNo: "ALERT-001", sourceAlertNo: null, issueNo: "ISSUE-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", issueType: "待回调未收口", severity: "P1", responsibilityGroup: "支付后端值班组", alertChannel: "IN_APP_OUTBOX", receiver: "支付运营", alertStatus: "已派发", alertStatusType: "success", ackStatus: "已确认", ackStatusType: "success", alertContent: "支付单仍处于待回调状态", receiptQueryCount: 0, triggeredBy: "payment-task-center", ackOperator: "ops", ackAt: "2026-09-27 10:01:00", createdAt: "2026-09-27 10:00:00", lastReceiptQueryAt: null, nextReceiptQueryAt: null },
      "ALERT-001"
    ),
    { alertNo: "ALERT-001", sourceAlertNo: null, issueNo: "ISSUE-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", issueType: "待回调未收口", severity: "P1", responsibilityGroup: "支付后端值班组", alertChannel: "IN_APP_OUTBOX", receiver: "支付运营", alertStatus: "已派发", alertStatusType: "success", ackStatus: "已确认", ackStatusType: "success", alertContent: "支付单仍处于待回调状态", receiptQueryCount: 0, triggeredBy: "payment-task-center", ackOperator: "ops", ackAt: "2026-09-27 10:01:00", createdAt: "2026-09-27 10:00:00", lastReceiptQueryAt: null, nextReceiptQueryAt: null }
  );
  assert.throws(
    () => normalizePaymentIssueAlertActionResult({ alertNo: "ALERT-002" }, "ALERT-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ISSUE-ACTION"
  );
  assert.throws(
    () => normalizePaymentIssueAlertActionResult(
      { alertNo: "ALERT-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", alertStatus: "SENT", ackStatus: "" },
      "ALERT-001"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ISSUE-ACTION"
  );
  for (const businessChainId of [undefined, "CHAIN-OTHER"]) {
    assert.throws(
      () => normalizePaymentIssueAlertActionResult(
        { alertNo: "ALERT-001", paymentOrderId: "PAY-001", businessChainId, alertStatus: "已派发", alertStatusType: "success", ackStatus: "已确认", ackStatusType: "success" },
        "ALERT-001"
      ),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-ISSUE-ACTION"
    );
  }
  assert.deepEqual(
    requireObjectResult({ alertNo: "ALERT-001" }, "ISSUE", "missing"),
    { alertNo: "ALERT-001" }
  );
  assert.throws(
    () => requireObjectResult(null, "PAYMENT-CLIENT-INVALID-ISSUE-ACTION", "missing"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ISSUE-ACTION"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  await assert.rejects(
    paymentIssueApi.acknowledgeAlert("ALERT-001", { operator: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ISSUE-ACTION"
  );
});

test("rejects an alert acknowledgement that drifts from the loaded payment issue snapshot", () => {
  const snapshot = {
    alertNo: "ALERT-001",
    sourceAlertNo: null,
    issueNo: "ISSUE-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    issueType: "PAYMENT_CALLBACK_TIMEOUT",
    severity: "P1",
    responsibilityGroup: "支付后端值班组",
    alertChannel: "IN_APP_OUTBOX",
    receiver: "支付运营",
    alertStatus: "已派发",
    alertStatusType: "success",
    ackStatus: "已确认",
    ackStatusType: "success",
    ackOperator: "ops",
    ackAt: "2026-09-27 10:01:00",
    alertContent: "支付单仍处于待回调状态",
    receiptQueryCount: 0,
    triggeredBy: "payment-task-center",
    createdAt: "2026-09-27 10:00:00",
    lastReceiptQueryAt: null,
    nextReceiptQueryAt: null
  };
  assert.deepEqual(
    normalizePaymentIssueAlertActionResult(
      { ...snapshot },
      "ALERT-001",
      snapshot
    ).paymentOrderId,
    "PAY-001"
  );
  assert.throws(
    () => normalizePaymentIssueAlertActionResult(
      { ...snapshot, paymentOrderId: "PAY-002", alertStatus: "已派发", alertStatusType: "success", ackStatus: "已确认", ackStatusType: "success" },
      "ALERT-001",
      snapshot
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ISSUE-ACTION"
  );
  assert.throws(
    () => normalizePaymentIssueAlertActionResult(
      { ...snapshot, businessChainId: "CHAIN-OTHER", alertStatus: "已派发", alertStatusType: "success", ackStatus: "已确认", ackStatusType: "success" },
      "ALERT-001",
      snapshot
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ISSUE-ACTION"
  );
  assert.throws(
    () => normalizePaymentIssueAlertActionResult(
      { ...snapshot, severity: "P2", alertStatus: "已派发", alertStatusType: "success", ackStatus: "已确认", ackStatusType: "success" },
      "ALERT-001",
      snapshot
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ISSUE-ACTION"
  );
  assert.throws(
    () => normalizePaymentIssueAlertActionResult(
      { alertNo: "ALERT-001", alertStatus: "已派发", alertStatusType: "success", ackStatus: "已确认", ackStatusType: "warn" },
      "ALERT-001"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ISSUE-ACTION"
  );
});

test("rejects an empty alert receipt query result", async (context) => {
  assert.deepEqual(
    normalizeTaskActionResult({ taskCode: "TASK-CODE", taskName: "任务名称", summaryComment: "任务已完成", processedCount: 1, successCount: 1, warningCount: 0, failCount: 0 }),
    { taskCode: "TASK-CODE", taskName: "任务名称", summaryComment: "任务已完成", processedCount: 1, successCount: 1, warningCount: 0, failCount: 0 }
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  await assert.rejects(
    paymentIssueApi.reconcileAlertReceipt("ALERT-001", { operator: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-ACTION"
  );
});

test("rejects an alert receipt query result for another task", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({
      code: "0",
      data: {
        taskCode: "PAYMENT_PENDING_QUERY",
        taskName: "待回调主动查单",
        summaryComment: "任务已完成",
        processedCount: 1,
        successCount: 1,
        warningCount: 0,
        failCount: 0
      }
    })
  }));

  await assert.rejects(
    paymentIssueApi.reconcileAlertReceipt("ALERT-001", { operator: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-ACTION"
  );
});

test("rejects empty dead-letter task action results", async (context) => {
  assert.deepEqual(
    normalizePaymentDeadLetterTaskActionResult(
      {
        taskNo: "DLQ-001", taskStatus: "REPLAYED", taskStatusType: "success", replayCount: 1,
        createdAt: "2026-09-27 10:00:00", updatedAt: "2026-09-27 10:02:00",
        lastReplayAt: "2026-09-27 10:01:00", resolvedAt: "2026-09-27 10:02:00",
        operator: "支付运营", resolutionNote: "核对完成"
      },
      "DLQ-001"
    ),
    {
      taskNo: "DLQ-001", taskStatus: "REPLAYED", taskStatusType: "success", replayCount: 1,
      createdAt: "2026-09-27 10:00:00", updatedAt: "2026-09-27 10:02:00",
      lastReplayAt: "2026-09-27 10:01:00", resolvedAt: "2026-09-27 10:02:00",
      operator: "支付运营", resolutionNote: "核对完成"
    }
  );
  assert.equal(
    normalizePaymentDeadLetterTaskActionResult(
      {
        taskNo: " DLQ-001 ", taskStatus: "REPLAYED", taskStatusType: "success", replayCount: 1,
        createdAt: "2026-09-27 10:00:00", updatedAt: "2026-09-27 10:02:00",
        lastReplayAt: "2026-09-27 10:01:00", resolvedAt: "2026-09-27 10:02:00",
        operator: "支付运营", resolutionNote: "核对完成"
      },
      " DLQ-001 "
    ).taskNo,
    " DLQ-001 "
  );
  assert.throws(
    () => normalizePaymentDeadLetterTaskActionResult(
      {
        taskNo: "DLQ-001", taskStatus: "REPLAYED", taskStatusType: "success", replayCount: 1,
        createdAt: "2026-09-27 10:00:00", updatedAt: "2026-09-27 10:02:00",
        lastReplayAt: "2026-09-27 10:03:00", resolvedAt: null
      },
      "DLQ-001"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DEAD-LETTER-ACTION"
  );
  assert.throws(
    () => normalizePaymentDeadLetterTaskActionResult(
      {
        taskNo: "DLQ-001", taskStatus: "REPLAYED", taskStatusType: "success", replayCount: 1,
        createdAt: "2026-09-27 10:00:00", updatedAt: "2026-09-27 10:02:00",
        lastReplayAt: "2026-09-27 10:01:00", resolvedAt: "2026-09-27 10:02:00",
        resolutionNote: "核对完成"
      },
      "DLQ-001"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DEAD-LETTER-ACTION"
  );
  assert.throws(
    () => normalizePaymentDeadLetterTaskActionResult({ taskNo: "DLQ-002" }, "DLQ-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DEAD-LETTER-ACTION"
  );
  assert.throws(
    () => normalizePaymentDeadLetterTaskActionResult(
      { taskNo: "DLQ-001", taskStatus: "REPLAYED", replayCount: -1 },
      "DLQ-001"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DEAD-LETTER-ACTION"
  );
  assert.throws(
    () => normalizePaymentDeadLetterTaskActionResult(
      { taskNo: "DLQ-001", taskStatus: "REPLAYED", replayCount: 1 },
      "DLQ-001"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DEAD-LETTER-ACTION"
  );
  assert.throws(
    () => normalizePaymentDeadLetterTaskActionResult(
      { taskNo: "DLQ-001", taskStatus: "REPLAY_FAILED", taskStatusType: "warn", replayCount: 1 },
      "DLQ-001"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DEAD-LETTER-ACTION"
  );
  assert.throws(
    () => normalizePaymentDeadLetterTaskActionResult(
      { taskNo: "DLQ-001", taskStatus: "MANUAL_RESOLVED", replayCount: 0 },
      "DLQ-001",
      null,
      ["READY_TO_REPLAY"]
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DEAD-LETTER-ACTION"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  await assert.rejects(
    paymentDeadLetterTaskApi.markReadyToReplay("DLQ-001", { operator: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DEAD-LETTER-ACTION"
  );
  await assert.rejects(
    paymentDeadLetterTaskApi.resolveManually("DLQ-001", { operator: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DEAD-LETTER-ACTION"
  );
  await assert.rejects(
    paymentDeadLetterTaskApi.replay("DLQ-001", { operator: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DEAD-LETTER-ACTION"
  );
});

test("rejects an empty payment event republish result", async (context) => {
  assert.throws(
    () => normalizePaymentEventRepublishResult({ items: [{ eventNo: "EVENT-001" }] }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-ACTION"
  );
  assert.deepEqual(
    requirePageResult({ items: [] }, "EVENT", "missing"),
    { items: [] }
  );
  assert.throws(
    () => requirePageResult(null, "PAYMENT-CLIENT-INVALID-EVENT-ACTION", "missing"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentEventRepublishResult({ items: [] }, "EVENT-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-ACTION"
  );
  assert.deepEqual(
    normalizePaymentEventRepublishResult({ items: [] }, "EVENT-001", false),
    { items: [], total: 0, pageNo: 1, pageSize: 20 }
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  await assert.rejects(
    paymentEventApi.republish("EVENT-001", "ops"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-ACTION"
  );
});

test("rejects an empty issue batch action result", async (context) => {
  assert.throws(
    () => normalizePaymentIssueBatchActionResult({ items: [{ issueNo: "ISSUE-001" }] }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ISSUE-BATCH-ACTION"
  );
  let requestOptions;
  context.mock.method(globalThis, "fetch", async (_url, options) => {
    requestOptions = options;
    return {
      ok: true,
      json: async () => ({ code: "0", data: null })
    };
  });

  await assert.rejects(
    paymentIssueApi.batchAction({ issueNos: [" ISSUE-001 "], actionType: "跟进", idempotencyKey: "attempt-001" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ISSUE-BATCH-ACTION"
  );
  assert.deepEqual(JSON.parse(requestOptions.body).issueNos, ["ISSUE-001"]);
  assert.equal(JSON.parse(requestOptions.body).idempotencyKey, "attempt-001");
});

test("requires every submitted issue in the batch action result", () => {
  assert.throws(
    () => normalizePaymentIssueBatchActionResult(
      { items: [{ issueNo: "ISSUE-001" }] },
      ["ISSUE-001", "ISSUE-002"]
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ISSUE-BATCH-ACTION"
  );
});

test("rejects an empty day-end run result", async (context) => {
  assert.deepEqual(
    normalizeDayEndRunResult({ latestBatchStatus: "COMPLETED" }),
    { latestBatchStatus: "COMPLETED" }
  );
  assert.throws(
    () => normalizeDayEndRunResult({}),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DAY-END-ACTION"
  );
  assert.throws(
    () => normalizeDayEndRunResult({ latestBatchStatus: "UNKNOWN" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DAY-END-ACTION"
  );
  assert.throws(
    () => normalizePaymentDayEndRunResult({ latestBatchStatus: "COMPLETED", alerts: [], recentBatches: [] }, "2026-09-26"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DAY-END-ACTION"
  );
  assert.throws(
    () => normalizePaymentDayEndRunResult({
      latestBizDate: "2026-09-26",
      latestBatchStatus: "UNKNOWN",
      alerts: [],
      recentBatches: []
    }, "2026-09-26"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DAY-END-ACTION"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  await assert.rejects(
    paymentDayEndApi.runBatch({ bizDate: "2026-09-26", triggeredBy: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DAY-END-OVERVIEW"
  );
});

test("trims the day-end business date before binding the run result", async (context) => {
  let requestBody;
  const overview = {
    totalBatchCount: 1,
    completedBatchCount: 1,
    abnormalBatchCount: 0,
    openChannelAbnormalCount: 0,
    openInternalAbnormalCount: 0,
    openPendingRefundCount: 0,
    latestChannelSuccessCount: 0,
    latestChannelSuccessAmount: "¥0.00",
    latestInternalSuccessCount: 0,
    latestInternalSuccessAmount: "¥0.00",
    latestPaymentSuccessGapCount: 0,
    latestPaymentSuccessGapAmount: "¥0.00",
    latestPendingRefundAmount: "¥0.00",
    latestBizDate: "2026-09-26",
    latestBatchStatus: "COMPLETED",
    reconciliationReadinessStatus: "可进入对账",
    reconciliationReadinessType: "success",
    reconciliationReadinessSummary: "事实已收口",
    reconciliationSuggestedAction: "进入正式对账",
    reconciliationBlockingOwner: "财务值班",
    alerts: [],
    recentBatches: []
  };
  context.mock.method(globalThis, "fetch", async (url, options) => {
    requestBody = JSON.parse(options.body);
    return {
      ok: true,
      json: async () => ({ code: "0", data: overview })
    };
  });

  assert.deepEqual(
    await paymentDayEndApi.runBatch({ bizDate: " 2026-09-26 ", triggeredBy: "ops" }),
    overview
  );
  assert.equal(requestBody.bizDate, "2026-09-26");
});

test("rejects an empty control-policy self-check result", async (context) => {
  const validOverview = {
    channels: [],
    routeRules: [],
    protocols: [],
    protocolTypeOptions: [],
    returnCodeMappings: [],
    gateways: [],
    controlPolicies: [],
    alertProviders: [],
    issueDutyRosters: []
  };
  assert.deepEqual(
    normalizeControlPolicySelfCheckResult(validOverview),
    validOverview
  );
  assert.throws(
    () => normalizeControlPolicySelfCheckResult({}),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONTROL-POLICY-SELF-CHECK"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: validOverview })
  }));

  await assert.rejects(
    paymentConfigApi.runControlPolicySelfCheck("APP-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONTROL-POLICY-SELF-CHECK"
  );
});

test("rejects an incomplete route-rule validation result", async (context) => {
  const validResult = { valid: true, matched: false, message: "样例上下文未命中" };
  assert.deepEqual(normalizeRouteRuleValidationResult(validResult), validResult);
  assert.throws(
    () => normalizeRouteRuleValidationResult({ valid: true }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ROUTE-RULE-VALIDATION"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  await assert.rejects(
    paymentConfigApi.validateRouteRule({ matchExpression: "amount >= 0" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ROUTE-RULE-VALIDATION"
  );
});

test("rejects an incomplete return-code import preview", async (context) => {
  const validItem = {
    rowNumber: 2,
    action: "CREATE",
    valid: true,
    message: "新增映射",
    changedFields: [],
    candidate: {
      channelCode: "wx_h5",
      channelReturnCode: "USERPAYING",
      standardizedCode: "PAYMENT-CHANNEL-1001",
      standardizedMessage: "用户支付中",
      handlingSuggestion: "等待回调或主动查单",
      retryable: "是",
      manualInterventionRequired: "否",
      mappingVersion: "v2026.09.1",
      archiveStatus: "ACTIVE",
      archiveStatusType: "success",
      status: "ENABLED",
      statusType: "success",
      priority: 1
    }
  };
  const validResult = {
    baselineFingerprint: "BASELINE-001",
    valid: true,
    applied: false,
    totalCount: 1,
    createCount: 1,
    updateCount: 0,
    unchangedCount: 0,
    invalidCount: 0,
    appliedCount: 0,
    items: [validItem]
  };
  assert.deepEqual(normalizeReturnCodeImportPreviewResult(validResult), validResult);
  assert.throws(
    () => normalizeReturnCodeImportPreviewResult({ valid: true, items: [] }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-RETURN-CODE-IMPORT-PREVIEW"
  );
  assert.throws(
    () => normalizeReturnCodeImportPreviewResult({ ...validResult, totalCount: 2 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-RETURN-CODE-IMPORT-PREVIEW"
  );
  assert.throws(
    () => normalizeReturnCodeImportPreviewResult({
      ...validResult,
      items: [{
        ...validItem,
        candidate: { ...validItem.candidate, archiveStatus: "INACTIVE", archiveStatusType: "success" }
      }]
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-RETURN-CODE-IMPORT-PREVIEW"
  );
  assert.throws(
    () => normalizeReturnCodeImportPreviewResult({
      ...validResult,
      items: [{
        ...validItem,
        candidate: { ...validItem.candidate, status: "DISABLED", statusType: "success" }
      }]
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-RETURN-CODE-IMPORT-PREVIEW"
  );
  assert.throws(
    () => normalizeReturnCodeImportPreviewResult({ ...validResult, invalidCount: 1 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-RETURN-CODE-IMPORT-PREVIEW"
  );
  assert.throws(
    () => normalizeReturnCodeImportPreviewResult({ ...validResult, appliedCount: 2 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-RETURN-CODE-IMPORT-PREVIEW"
  );
  assert.throws(
    () => normalizeReturnCodeImportPreviewResult({ ...validResult, appliedCount: 1 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-RETURN-CODE-IMPORT-PREVIEW"
  );
  assert.throws(
    () => normalizeReturnCodeImportPreviewResult({ ...validResult, applied: true, valid: false }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-RETURN-CODE-IMPORT-PREVIEW"
  );
  assert.throws(
    () => normalizeReturnCodeImportPreviewResult({ ...validResult, items: [] }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-RETURN-CODE-IMPORT-PREVIEW"
  );
  assert.throws(
    () => normalizeReturnCodeImportPreviewResult({
      ...validResult,
      items: [{ ...validItem, candidate: null }]
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-RETURN-CODE-IMPORT-PREVIEW"
  );
  assert.throws(
    () => normalizeReturnCodeImportPreviewResult({
      ...validResult,
      items: [{ ...validItem, action: "INVALID", valid: true }],
      invalidCount: 1,
      createCount: 0,
      valid: false
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-RETURN-CODE-IMPORT-PREVIEW"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  await assert.rejects(
    paymentConfigApi.previewReturnCodeImport({ csvContent: "csv" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-RETURN-CODE-IMPORT-PREVIEW"
  );
});

test("keeps non-paged payloads and existing arrays unchanged", () => {
  const summary = { totalCount: 0 };
  const items = [{ id: "PAY-001" }];

  assert.equal(normalizePagePayload(summary), summary);
  assert.equal(normalizePagePayload({ items }).items, items);
});

test("normalizes nullable object overviews without changing detail null semantics", async (context) => {
  assert.deepEqual(normalizeObjectPayload(null), {});
  assert.deepEqual(normalizeObjectPayload([]), {});
  const overview = { totalCount: 3 };
  assert.equal(normalizeObjectPayload(overview), overview);

  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  await assert.rejects(
    paymentMonitorApi.getOverview(),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-MONITOR-OVERVIEW"
  );
  await assert.rejects(
    paymentTaskCenterApi.getOverview(),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-CENTER-OVERVIEW"
  );
  await assert.rejects(
    paymentDayEndApi.getOverview(),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DAY-END-OVERVIEW"
  );
  await assert.rejects(
    dashboardApi.getSummary(),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DASHBOARD-SUMMARY"
  );
  await assert.rejects(
    paymentEventApi.getOverview(),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-OVERVIEW"
  );
  await assert.rejects(
    paymentRequestApi.getOverview(),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REQUEST-OVERVIEW"
  );
  await assert.rejects(
    paymentLogApi.getOverview(),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-LOG-OVERVIEW"
  );
  await assert.rejects(
    paymentRouteApi.getOverview(),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ROUTE-OVERVIEW"
  );
  await assert.rejects(
    cashierSessionApi.getOverview(),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER-OVERVIEW"
  );
  await assert.rejects(
    billApi.getOverview(),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-BILL-OVERVIEW"
  );
  await assert.rejects(
    settlementApi.getWorkerOverview(),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-SETTLEMENT-OVERVIEW"
  );
});

test("requires non-negative payment event overview counters", () => {
  const overview = {
    totalEventCount: 10,
    successEventCount: 8,
    pendingEventCount: 1,
    failedEventCount: 1,
    deadLetterEventCount: 0,
    unknownEventCount: 0,
    failedOrDeadLetterCount: 1,
    distinctDownstreamCount: 3,
    dueRetryEventCount: 0,
    paymentSuccessEventCount: 8,
    latestPublishedAt: null
  };
  assert.deepEqual(normalizePaymentEventOverviewResult(overview), overview);
  assert.throws(
    () => normalizePaymentEventOverviewResult({ ...overview, failedEventCount: -1 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentEventOverviewResult({ ...overview, totalEventCount: null }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentEventOverviewResult({ ...overview, failedOrDeadLetterCount: 2 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentEventOverviewResult({ ...overview, successEventCount: 10 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentEventOverviewResult({ ...overview, pendingEventCount: 0 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentEventOverviewResult({ ...overview, paymentSuccessEventCount: 9 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentEventOverviewResult({ ...overview, latestPublishedAt: 123 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentEventOverviewResult({ ...overview, latestPublishedAt: "not-a-datetime" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-EVENT-OVERVIEW"
  );
});

test("requires non-negative payment request overview counters", () => {
  const overview = {
    totalRequestCount: 10,
    successRequestCount: 8,
    failedRequestCount: 1,
    processingRequestCount: 1,
    waitingCallbackRequestCount: 0,
    distinctTerminalCount: 2,
    distinctChannelCount: 2,
    repeatedPaymentOrderCount: 0,
    missingResponseCount: 0,
    latestRequestAt: null
  };
  assert.deepEqual(normalizePaymentRequestOverviewResult(overview), overview);
  assert.throws(
    () => normalizePaymentRequestOverviewResult({ ...overview, failedRequestCount: -1 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REQUEST-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentRequestOverviewResult({ ...overview, distinctTerminalCount: undefined }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REQUEST-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentRequestOverviewResult({ ...overview, repeatedPaymentOrderCount: 11 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REQUEST-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentRequestOverviewResult({ ...overview, successRequestCount: 10 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REQUEST-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentRequestOverviewResult({ ...overview, processingRequestCount: 0 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REQUEST-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentRequestOverviewResult({ ...overview, latestRequestAt: "not-a-datetime" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REQUEST-OVERVIEW"
  );
});

test("requires non-negative payment log overview counters", () => {
  const overview = {
    totalLogCount: 10,
    errorLogCount: 1,
    warnLogCount: 2,
    infoLogCount: 7,
    distinctStageCount: 3,
    distinctSourceCount: 2,
    callbackErrorCount: 1,
    eventWarnCount: 1,
    callbackKeywordCount: 0,
    latestLogAt: null
  };
  assert.deepEqual(normalizePaymentLogOverviewResult(overview), overview);
  assert.throws(
    () => normalizePaymentLogOverviewResult({ ...overview, errorLogCount: -1 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-LOG-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentLogOverviewResult({ ...overview, callbackKeywordCount: undefined }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-LOG-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentLogOverviewResult({ ...overview, callbackKeywordCount: 11 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-LOG-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentLogOverviewResult({ ...overview, errorLogCount: 8 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-LOG-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentLogOverviewResult({ ...overview, latestLogAt: "not-a-datetime" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-LOG-OVERVIEW"
  );
});

test("requires payment route overview counters and health policy", () => {
  const overview = {
    totalRouteCount: 10,
    successRouteCount: 8,
    warnRouteCount: 2,
    distinctChannelCount: 2,
    offlineRouteCount: 1,
    wechatRouteCount: 6,
    alipayRouteCount: 3,
    latestRouteAt: null,
    routeHealthEnabled: true,
    routeHealthLookbackHours: 24,
    routeHealthFailureCountThreshold: 3,
    routeHealthFailureRateThresholdPercent: 50,
    routeHealthMinimumSampleCount: 10
  };
  assert.deepEqual(normalizePaymentRouteOverviewResult(overview), overview);
  assert.throws(
    () => normalizePaymentRouteOverviewResult({ ...overview, warnRouteCount: -1 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ROUTE-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentRouteOverviewResult({ ...overview, routeHealthEnabled: undefined }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ROUTE-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentRouteOverviewResult({ ...overview, routeHealthFailureRateThresholdPercent: 101 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ROUTE-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentRouteOverviewResult({ ...overview, latestRouteAt: "not-a-datetime" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ROUTE-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentRouteOverviewResult({ ...overview, successRouteCount: 7 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ROUTE-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentRouteOverviewResult({ ...overview, distinctChannelCount: 11 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ROUTE-OVERVIEW"
  );
});

test("requires cashier session overview counters and a non-negative amount", () => {
  const overview = {
    totalSessionCount: 10,
    expiredSessionCount: 1,
    successSessionCount: 6,
    payingSessionCount: 2,
    pendingSessionCount: 1,
    otherSessionCount: 0,
    distinctTerminalCount: 3,
    expiringSoonCount: 1,
    totalAmount: "¥680.00"
  };
  assert.deepEqual(normalizeCashierSessionOverviewResult(overview), overview);
  assert.deepEqual(
    normalizeCashierSessionOverviewResult({ ...overview, pendingSessionCount: 0, otherSessionCount: 1 }).otherSessionCount,
    1
  );
  assert.throws(
    () => normalizeCashierSessionOverviewResult({ ...overview, otherSessionCount: undefined }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER-OVERVIEW"
  );
  assert.deepEqual(
    normalizeCashierSessionOverviewResult({ ...overview, totalAmount: "¥0.00" }).totalAmount,
    "¥0.00"
  );
  assert.throws(
    () => normalizeCashierSessionOverviewResult({ ...overview, expiredSessionCount: -1 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER-OVERVIEW"
  );
  assert.throws(
    () => normalizeCashierSessionOverviewResult({ ...overview, totalAmount: "invalid" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER-OVERVIEW"
  );
  assert.throws(
    () => normalizeCashierSessionOverviewResult({ ...overview, totalAmount: "6.8e2" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER-OVERVIEW"
  );
  assert.throws(
    () => normalizeCashierSessionOverviewResult({ ...overview, expiringSoonCount: 11 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER-OVERVIEW"
  );
  assert.throws(
    () => normalizeCashierSessionOverviewResult({ ...overview, successSessionCount: 10 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER-OVERVIEW"
  );
  assert.throws(
    () => normalizeCashierSessionOverviewResult({ ...overview, successSessionCount: 5 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER-OVERVIEW"
  );
});

test("requires bill overview counters and non-negative amounts", () => {
  const overview = {
    totalBillCount: 10,
    paidBillCount: 6,
    unpaidBillCount: 2,
    partialPaidBillCount: 2,
    overdueBillCount: 1,
    totalBillAmount: "¥880.00",
    totalPaidAmount: "¥680.00",
    totalUnpaidAmount: "¥200.00"
  };
  assert.deepEqual(normalizeBillOverviewResult(overview), overview);
  assert.throws(
    () => normalizeBillOverviewResult({ ...overview, overdueBillCount: -1 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-BILL-OVERVIEW"
  );
  assert.throws(
    () => normalizeBillOverviewResult({ ...overview, totalUnpaidAmount: "invalid" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-BILL-OVERVIEW"
  );
  assert.throws(
    () => normalizeBillOverviewResult({ ...overview, totalUnpaidAmount: "2e2" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-BILL-OVERVIEW"
  );
  assert.throws(
    () => normalizeBillOverviewResult({ ...overview, totalUnpaidAmount: "¥300.00" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-BILL-OVERVIEW"
  );
  assert.deepEqual(
    normalizeBillOverviewResult({
      ...overview,
      totalBillAmount: "9007199254740992.03",
      totalPaidAmount: "9007199254740992.01",
      totalUnpaidAmount: "0.02"
    }).totalBillAmount,
    "9007199254740992.03"
  );
  assert.throws(
    () => normalizeBillOverviewResult({
      ...overview,
      totalBillAmount: "9007199254740992.03",
      totalPaidAmount: "9007199254740992.01",
      totalUnpaidAmount: "0.01"
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-BILL-OVERVIEW"
  );
  assert.throws(
    () => normalizeBillOverviewResult({ ...overview, paidBillCount: 8 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-BILL-OVERVIEW"
  );
  assert.throws(
    () => normalizeBillOverviewResult({ ...overview, paidBillCount: 5 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-BILL-OVERVIEW"
  );
});

test("requires worker settlement overview counters and amounts", () => {
  const overview = {
    totalSettlementCount: 10,
    pendingAuditCount: 2,
    payoutPendingCount: 3,
    payingCount: 1,
    payoutSuccessCount: 4,
    totalNetSettleAmount: "¥680.00",
    totalDeductAmount: "¥120.00",
    totalDepositImpactAmount: "¥80.00",
    negativeNetSettleCount: 1
  };
  assert.deepEqual(normalizeWorkerSettlementOverviewResult(overview), overview);
  assert.deepEqual(
    normalizeWorkerSettlementOverviewResult({
      ...overview,
      totalNetSettleAmount: "¥-20.00",
      totalDepositImpactAmount: "¥-10.00"
    }).totalNetSettleAmount,
    "¥-20.00"
  );
  assert.throws(
    () => normalizeWorkerSettlementOverviewResult({ ...overview, negativeNetSettleCount: -1 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-SETTLEMENT-OVERVIEW"
  );
  assert.throws(
    () => normalizeWorkerSettlementOverviewResult({ ...overview, totalNetSettleAmount: "invalid" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-SETTLEMENT-OVERVIEW"
  );
  assert.throws(
    () => normalizeWorkerSettlementOverviewResult({ ...overview, totalNetSettleAmount: "6.8e2" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-SETTLEMENT-OVERVIEW"
  );
  assert.throws(
    () => normalizeWorkerSettlementOverviewResult({ ...overview, payoutSuccessCount: 8 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-SETTLEMENT-OVERVIEW"
  );
  assert.throws(
    () => normalizeWorkerSettlementOverviewResult({ ...overview, negativeNetSettleCount: 11 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-SETTLEMENT-OVERVIEW"
  );
});

test("rejects worker settlement pages with inconsistent amount and payout facts", () => {
  const validRow = {
    settlementOrderId: "SETW-001",
    workerName: "李师傅",
    period: "2026-09-20 ~ 2026-09-26",
    amountShouldSettle: "¥100.00",
    deductAmount: "¥120.00",
    amountNetSettle: "¥-20.00",
    depositImpactAmount: "¥10.00",
    status: "待审核",
    statusType: "warn",
    payoutStatus: "待出款",
    payoutStatusType: "info"
  };
  assert.deepEqual(
    normalizeWorkerSettlementListPage({ items: [validRow], total: 1 }).items,
    [validRow]
  );
  assert.throws(
    () => normalizeWorkerSettlementListPage({
      items: [{ ...validRow, amountNetSettle: "¥-19.99" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-SETTLEMENT-LIST"
  );
  assert.throws(
    () => normalizeWorkerSettlementListPage({
      items: [{ ...validRow, status: "待审核", statusType: "success" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-SETTLEMENT-LIST"
  );
  assert.throws(
    () => normalizeWorkerSettlementListPage({
      items: [{ ...validRow, payoutStatus: "出款成功", payoutStatusType: "warn" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-SETTLEMENT-LIST"
  );
  assert.throws(
    () => normalizeWorkerSettlementListPage({
      items: [{ ...validRow, deductAmount: "¥-1.00" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-SETTLEMENT-LIST"
  );
});

test("applies worker settlement list validation to API responses", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({
      code: "0",
      data: {
        items: [{
          settlementOrderId: "SETW-001",
          workerName: "李师傅",
          period: "2026-09-20 ~ 2026-09-26",
          amountShouldSettle: "¥100.00",
          deductAmount: "¥20.00",
          amountNetSettle: "¥80.00",
          depositImpactAmount: "¥10.00",
          status: "已完成",
          statusType: "success",
          payoutStatus: "出款成功",
          payoutStatusType: "success"
        }],
        total: 1,
        pageNo: 1,
        pageSize: 20
      }
    })
  }));

  const result = await settlementApi.getWorkerList();
  assert.equal(result.items[0].settlementOrderId, "SETW-001");
});

const validOrderListRow = {
  orderNo: "ORD-001",
  customerName: "客户一",
  serviceType: "深度保洁",
  workerName: "服务者一",
  orderAmount: "¥100.00",
  paidAmount: "¥20.00",
  orderStatus: "待支付",
  orderStatusType: "warn",
  fulfillmentStatus: "待确认",
  fulfillmentStatusType: "warn",
  latestPaymentStatus: null,
  latestPaymentStatusType: null,
  latestPayScene: null,
  latestCashierStatus: null,
  latestCashierStatusType: null,
  createdAt: "2026-09-27 10:00:00"
};

test("normalizes a null list response to an empty page", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  assert.deepEqual(await orderApi.getList(), {
    items: [],
    total: 0,
    pageNo: 1,
    pageSize: 20
  });
});

test("normalizes malformed list responses without leaking an invalid page shape", async (context) => {
  let call = 0;
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({
      code: "0",
      data: call++ === 0 ? [validOrderListRow] : "unexpected"
    })
  }));

  assert.deepEqual(await orderApi.getList(), {
    items: [validOrderListRow],
    total: 1,
    pageNo: 1,
    pageSize: 20
  });
  assert.deepEqual(await orderApi.getList(), {
    items: [],
    total: 0,
    pageNo: 1,
    pageSize: 20
  });
});

test("normalizes malformed pagination metadata for list consumers", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({
      code: "0",
      data: { items: [{ ...validOrderListRow, orderNo: "ORD-002" }], total: "invalid", pageNo: 0, pageSize: -1 }
    })
  }));

  assert.deepEqual(await orderApi.getList(), {
    items: [{ ...validOrderListRow, orderNo: "ORD-002" }],
    total: 1,
    pageNo: 1,
    pageSize: 20
  });
});

test("does not accept a page total smaller than its returned rows", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({
      code: "0",
      data: { items: [{ ...validOrderListRow, orderNo: "ORD-003" }, { ...validOrderListRow, orderNo: "ORD-004" }], total: 1, pageNo: 1, pageSize: 20 }
    })
  }));

  const result = await orderApi.getList();
  assert.equal(result.items.length, 2);
  assert.equal(result.total, 2);
});

test("rejects order pages with inconsistent amount or status facts", () => {
  assert.deepEqual(
    normalizeOrderListPage({ items: [validOrderListRow], total: 1 }).items,
    [validOrderListRow]
  );
  assert.throws(
    () => normalizeOrderListPage({
      items: [{ ...validOrderListRow, paidAmount: "¥120.00" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ORDER-LIST"
  );
  assert.throws(
    () => normalizeOrderListPage({
      items: [{ ...validOrderListRow, orderStatusType: "invalid" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ORDER-LIST"
  );
  assert.throws(
    () => normalizeOrderListPage({
      items: [{ ...validOrderListRow, latestPaymentStatus: "SUCCESS", latestPaymentStatusType: null }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ORDER-LIST"
  );
  assert.throws(
    () => normalizeOrderListPage({
      items: [{ ...validOrderListRow, latestPaymentStatus: "UNKNOWN", latestPaymentStatusType: "warn" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ORDER-LIST"
  );
  assert.throws(
    () => normalizeOrderListPage({
      items: [{ ...validOrderListRow, latestCashierStatus: "支付中", latestCashierStatusType: "warn" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ORDER-LIST"
  );
  assert.throws(
    () => normalizeOrderListPage({
      items: [{ ...validOrderListRow, createdAt: "not-a-datetime" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ORDER-LIST"
  );
});

test("rejects empty or mismatched order detail responses", async (context) => {
  const validDetail = {
    orderNo: "ORD-001",
    customerName: "客户一",
    serviceType: "深度保洁",
    workerName: "服务者一",
    orderAmount: "¥100.00",
    paidAmount: "¥20.00",
    outstandingAmount: "¥80.00",
    orderStatus: "待支付",
    orderStatusType: "warn",
    fulfillmentStatus: "待确认",
    fulfillmentStatusType: "warn",
    latestPaymentStatus: null,
    latestPaymentStatusType: null,
    latestPayScene: "HOME_CLEAN",
    latestCashierStatus: null,
    latestCashierStatusType: null,
    createdAt: "2026-09-27 10:00:00"
  };
  assert.deepEqual(
    normalizeOrderDetailResult(validDetail, "ORD-001"),
    validDetail
  );
  assert.equal(
    normalizeOrderDetailResult({ ...validDetail, orderNo: " ORD-001 " }, " ORD-001 ").orderNo,
    " ORD-001 "
  );
  assert.throws(
    () => normalizeOrderDetailResult({ orderNo: "ORD-002" }, "ORD-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ORDER-DETAIL"
  );
  assert.throws(
    () => normalizeOrderDetailResult({
      ...validDetail,
      latestPaymentStatus: "UNKNOWN",
      latestPaymentStatusType: "warn"
    }, "ORD-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ORDER-DETAIL"
  );
  assert.throws(
    () => normalizeOrderDetailResult({
      ...validDetail,
      latestCashierStatus: "支付中",
      latestCashierStatusType: "warn"
    }, "ORD-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ORDER-DETAIL"
  );
  assert.throws(
    () => normalizeOrderDetailResult({ ...validDetail, orderAmount: "0" }, "ORD-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ORDER-DETAIL"
  );
  assert.throws(
    () => normalizeOrderDetailResult({ ...validDetail, orderStatus: "" }, "ORD-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ORDER-DETAIL"
  );
  assert.throws(
    () => normalizeOrderDetailResult({ ...validDetail, outstandingAmount: "¥70.00" }, "ORD-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ORDER-DETAIL"
  );
  assert.throws(
    () => normalizeOrderDetailResult({ ...validDetail, createdAt: "not-a-datetime" }, "ORD-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ORDER-DETAIL"
  );
  assert.throws(
    () => normalizeOrderDetailResult(null, "ORD-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ORDER-DETAIL"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  await assert.rejects(
    orderApi.getDetail("ORD-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ORDER-DETAIL"
  );
});

test("requires order detail refreshes to preserve the receivable amount snapshot", () => {
  const snapshot = { orderNo: "ORD-001", orderAmount: "68.00", latestPayScene: "HOME_CLEAN" };
  assert.equal(matchesOrderDetailSnapshot(snapshot, { ...snapshot, orderAmount: "¥68.00" }), true);
  assert.equal(
    matchesOrderDetailSnapshot(snapshot, { ...snapshot, orderNo: " ORD-001 ", orderAmount: "¥68.00" }),
    true
  );
  assert.equal(matchesOrderDetailSnapshot(snapshot, { ...snapshot, orderNo: "ORD-002" }), false);
  assert.equal(matchesOrderDetailSnapshot(snapshot, { ...snapshot, orderAmount: "67.99" }), false);
  assert.equal(matchesOrderDetailSnapshot(snapshot, { ...snapshot, latestPayScene: "HOME_REPAIR" }), false);
});

test("rejects empty or mismatched bill detail responses", async (context) => {
  const validDetail = {
    billNo: "BILL-001",
    orderNo: "ORDER-001",
    billAmount: "¥100.00",
    paidAmount: "¥20.00",
    unpaidAmount: "¥80.00",
    billStatus: "部分支付",
    billStatusType: "warn",
    latestPaymentStatus: null,
    latestPaymentStatusType: null,
    latestPayScene: "HOME_CLEAN",
    latestCashierStatus: null,
    latestCashierStatusType: null,
    dueAt: "2026-09-28 23:59:59",
    createdAt: "2026-09-27 10:00:00"
  };
  assert.deepEqual(
    normalizeBillDetailResult(validDetail, "BILL-001"),
    validDetail
  );
  assert.equal(
    normalizeBillDetailResult({ ...validDetail, billNo: " BILL-001 ", orderNo: " ORDER-001 " }, " BILL-001 ").billNo,
    " BILL-001 "
  );
  assert.throws(
    () => normalizeBillDetailResult({ billNo: "BILL-002" }, "BILL-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-BILL-DETAIL"
  );
  assert.throws(
    () => normalizeBillDetailResult({ ...validDetail, billAmount: "0" }, "BILL-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-BILL-DETAIL"
  );
  assert.throws(
    () => normalizeBillDetailResult({ ...validDetail, billStatus: "" }, "BILL-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-BILL-DETAIL"
  );
  assert.throws(
    () => normalizeBillDetailResult({ ...validDetail, unpaidAmount: "¥70.00" }, "BILL-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-BILL-DETAIL"
  );
  assert.throws(
    () => normalizeBillDetailResult({
      ...validDetail,
      latestPaymentStatus: "UNKNOWN",
      latestPaymentStatusType: "warn"
    }, "BILL-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-BILL-DETAIL"
  );
  assert.throws(
    () => normalizeBillDetailResult({
      ...validDetail,
      latestCashierStatus: "支付中",
      latestCashierStatusType: "warn"
    }, "BILL-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-BILL-DETAIL"
  );
  assert.throws(
    () => normalizeBillDetailResult({ ...validDetail, dueAt: "2026-09-27 09:00:00" }, "BILL-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-BILL-DETAIL"
  );
  assert.throws(
    () => normalizeBillDetailResult([], "BILL-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-BILL-DETAIL"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  await assert.rejects(
    billApi.getDetail("BILL-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-BILL-DETAIL"
  );
});

test("rejects bill pages with inconsistent amount and status facts", () => {
  const validRow = {
    billNo: "BILL-001",
    orderNo: "ORDER-001",
    billAmount: "¥100.00",
    paidAmount: "¥20.00",
    unpaidAmount: "¥80.00",
    billStatus: "部分支付",
    billStatusType: "warn",
    latestPaymentStatus: null,
    latestPaymentStatusType: null,
    latestCashierStatus: null,
    latestCashierStatusType: null,
    dueAt: "2026-09-28 23:59:59",
    createdAt: "2026-09-27 10:00:00"
  };
  assert.deepEqual(
    normalizeBillListPage({ items: [validRow], total: 1 }).items,
    [validRow]
  );
  assert.throws(
    () => normalizeBillListPage({
      items: [{ ...validRow, unpaidAmount: "¥70.00" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-BILL-LIST"
  );
  assert.throws(
    () => normalizeBillListPage({
      items: [{ ...validRow, billStatus: "已结清", billStatusType: "warn" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-BILL-LIST"
  );
  assert.throws(
    () => normalizeBillListPage({
      items: [{ ...validRow, billAmount: "¥0.00" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-BILL-LIST"
  );
  assert.throws(
    () => normalizeBillListPage({
      items: [{ ...validRow, latestPaymentStatus: "UNKNOWN", latestPaymentStatusType: "warn" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-BILL-LIST"
  );
  assert.throws(
    () => normalizeBillListPage({
      items: [{ ...validRow, createdAt: "not-a-time" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-BILL-LIST"
  );
  assert.throws(
    () => normalizeBillListPage({
      items: [{ ...validRow, latestCashierStatus: "支付中", latestCashierStatusType: "warn" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-BILL-LIST"
  );
});

test("requires bill detail refreshes to preserve the order and receivable snapshot", () => {
  const snapshot = {
    billNo: "BILL-001",
    orderNo: "ORD-001",
    standardOrderNo: "ORD-001",
    billAmount: "68.00",
    paidAmount: "20.00",
    unpaidAmount: "48.00"
  };
  assert.equal(matchesBillDetailSnapshot(snapshot, { ...snapshot, billAmount: "¥68.00" }), true);
  assert.equal(
    matchesBillDetailSnapshot(
      snapshot,
      { ...snapshot, billNo: " BILL-001 ", orderNo: " ORD-001 ", standardOrderNo: " ORD-001 ", billAmount: "¥68.00" }
    ),
    true
  );
  assert.equal(matchesBillDetailSnapshot(snapshot, { ...snapshot, orderNo: "ORD-002" }), false);
  assert.equal(matchesBillDetailSnapshot(snapshot, { ...snapshot, billAmount: "67.99" }), false);
  assert.equal(matchesBillDetailSnapshot(snapshot, { ...snapshot, paidAmount: "21.00", unpaidAmount: "47.00" }), false);
  assert.equal(matchesBillDetailSnapshot(snapshot, { ...snapshot, unpaidAmount: "47.99" }), false);
});

test("rejects empty or mismatched payment issue detail responses", async (context) => {
  assert.deepEqual(
    normalizePaymentIssueDetailResult({
      issueNo: "ISSUE-001",
      paymentOrderId: "PAY-001",
      businessChainId: "CHAIN-PAY-001",
      orderNo: "ORDER-001",
      customerName: "客户",
      paymentMethod: "微信",
      channelCode: "wx_h5",
      issueType: "待回调未收口",
      issueTypeTag: "danger",
      severity: "P1",
      severityType: "danger",
      paymentStatus: "SUCCESS",
      paymentStatusType: "success",
      handlingStatus: "待分派",
      handlingStatusType: "warn",
      responsibilityGroupType: "info",
      alertStatus: "未通知",
      alertStatusType: "info",
      alertAckStatus: "待确认",
      alertAckStatusType: "warn",
      slaStatus: "SLA内",
      slaStatusType: "success",
      escalationStatus: "当班跟进",
      escalationStatusType: "info",
      issueSummary: "支付单仍处于待回调状态",
      rootCauseHint: "建议主动查单",
      recommendedAction: "核对回调",
      recommendedRoute: "/payments/PAY-001",
      responsibilityHint: "支付后端值班组",
      alertChannelSummary: "未派发",
      alertDeliverySummary: "暂无派发明细",
      latestAlertProviderSummary: "暂无供应商派发记录",
      latestAlertReceiptSummary: "暂无投递回执",
      slaTimeLeft: "SLA内",
      escalationSuggestion: "当班跟进",
      latestActionSummary: "暂无处理动作",
      assignee: "未分派",
      responsibilityGroup: "支付后端值班组",
      alertReceiver: "未分配",
      createdAt: "2026-09-27 10:00:00",
      latestActionAt: null
    }, "ISSUE-001"),
    {
      issueNo: "ISSUE-001",
      paymentOrderId: "PAY-001",
      businessChainId: "CHAIN-PAY-001",
      orderNo: "ORDER-001",
      customerName: "客户",
      paymentMethod: "微信",
      channelCode: "wx_h5",
      issueType: "待回调未收口",
      issueTypeTag: "danger",
      severity: "P1",
      severityType: "danger",
      paymentStatus: "SUCCESS",
      paymentStatusType: "success",
      handlingStatus: "待分派",
      handlingStatusType: "warn",
      responsibilityGroupType: "info",
      alertStatus: "未通知",
      alertStatusType: "info",
      alertAckStatus: "待确认",
      alertAckStatusType: "warn",
      slaStatus: "SLA内",
      slaStatusType: "success",
      escalationStatus: "当班跟进",
      escalationStatusType: "info",
      issueSummary: "支付单仍处于待回调状态",
      rootCauseHint: "建议主动查单",
      recommendedAction: "核对回调",
      recommendedRoute: "/payments/PAY-001",
      responsibilityHint: "支付后端值班组",
      alertChannelSummary: "未派发",
      alertDeliverySummary: "暂无派发明细",
      latestAlertProviderSummary: "暂无供应商派发记录",
      latestAlertReceiptSummary: "暂无投递回执",
      slaTimeLeft: "SLA内",
      escalationSuggestion: "当班跟进",
      latestActionSummary: "暂无处理动作",
      assignee: "未分派",
      responsibilityGroup: "支付后端值班组",
      alertReceiver: "未分配",
      createdAt: "2026-09-27 10:00:00",
      latestActionAt: null
    }
  );
  assert.equal(
    normalizePaymentIssueDetailResult({
      issueNo: " ISSUE-001 ",
      paymentOrderId: "PAY-001",
      businessChainId: "CHAIN-PAY-001",
      orderNo: "ORDER-001",
      customerName: "客户",
      paymentMethod: "微信",
      channelCode: "wx_h5",
      issueType: "待回调未收口",
      issueTypeTag: "danger",
      severity: "P1",
      severityType: "danger",
      paymentStatus: "SUCCESS",
      paymentStatusType: "success",
      handlingStatus: "待分派",
      handlingStatusType: "warn",
      responsibilityGroupType: "info",
      alertStatus: "未通知",
      alertStatusType: "info",
      alertAckStatus: "待确认",
      alertAckStatusType: "warn",
      slaStatus: "SLA内",
      slaStatusType: "success",
      escalationStatus: "当班跟进",
      escalationStatusType: "info",
      issueSummary: "支付单仍处于待回调状态",
      rootCauseHint: "建议主动查单",
      recommendedAction: "核对回调",
      recommendedRoute: "/payments/PAY-001",
      responsibilityHint: "支付后端值班组",
      alertChannelSummary: "未派发",
      alertDeliverySummary: "暂无派发明细",
      latestAlertProviderSummary: "暂无供应商派发记录",
      latestAlertReceiptSummary: "暂无投递回执",
      slaTimeLeft: "SLA内",
      escalationSuggestion: "当班跟进",
      latestActionSummary: "暂无处理动作",
      assignee: "未分派",
      responsibilityGroup: "支付后端值班组",
      alertReceiver: "未分配",
      createdAt: "2026-09-27 10:00:00",
      latestActionAt: null
    }, " ISSUE-001 ").issueNo,
    " ISSUE-001 "
  );
  assert.throws(
    () => normalizePaymentIssueDetailResult({ issueNo: "ISSUE-002" }, "ISSUE-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-DETAIL"
  );
  assert.throws(
    () => normalizePaymentIssueDetailResult({
      issueNo: "ISSUE-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-OTHER",
      orderNo: "ORDER-001", issueType: "待回调未收口", issueTypeTag: "danger",
      severity: "P1", severityType: "danger", paymentStatus: "SUCCESS", paymentStatusType: "success",
      handlingStatus: "待分派", handlingStatusType: "warn", responsibilityGroupType: "info",
      alertStatus: "未通知", alertStatusType: "info", alertAckStatus: "待确认", alertAckStatusType: "warn",
      slaStatus: "SLA内", slaStatusType: "success", escalationStatus: "当班跟进", escalationStatusType: "info"
    }, "ISSUE-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-DETAIL"
  );
  assert.throws(
    () => normalizePaymentIssueDetailResult({
      issueNo: "ISSUE-001",
      orderNo: "ORDER-001",
      issueType: "待回调未收口",
      issueTypeTag: "danger",
      severity: "P1",
      severityType: "danger",
      paymentStatus: "SUCCESS",
      paymentStatusType: "success",
      handlingStatus: "待分派",
      handlingStatusType: "warn",
      responsibilityGroupType: "info",
      alertStatus: "未通知",
      alertStatusType: "info",
      alertAckStatus: "待确认",
      alertAckStatusType: "warn",
      slaStatus: "SLA内",
      slaStatusType: "success",
      escalationStatus: "当班跟进",
      escalationStatusType: "info"
    }, "ISSUE-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-DETAIL"
  );
  assert.throws(
    () => normalizePaymentIssueDetailResult({
      issueNo: "ISSUE-001",
      issueType: "",
      severity: "P1",
      severityType: "danger",
      paymentStatus: "SUCCESS",
      paymentStatusType: "success",
      handlingStatus: "待分派",
      handlingStatusType: "warn"
    }, "ISSUE-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-DETAIL"
  );
  assert.throws(
    () => normalizePaymentIssueDetailResult({
      issueNo: "ISSUE-001",
      issueType: "待回调未收口",
      issueTypeTag: "danger",
      severity: "P1",
      severityType: "danger",
      paymentStatus: "SUCCESS",
      paymentStatusType: "success",
      handlingStatus: "",
      handlingStatusType: "warn"
    }, "ISSUE-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-DETAIL"
  );
  assert.throws(
    () => normalizePaymentIssueDetailResult({
      issueNo: "ISSUE-001",
      paymentOrderId: "PAY-001",
      orderNo: "ORDER-001",
      issueType: "待回调未收口",
      issueTypeTag: "warn",
      severity: "P1",
      severityType: "danger",
      paymentStatus: "SUCCESS",
      paymentStatusType: "success",
      handlingStatus: "待分派",
      handlingStatusType: "warn",
      responsibilityGroupType: "info",
      alertStatus: "未通知",
      alertStatusType: "info",
      alertAckStatus: "待确认",
      alertAckStatusType: "warn",
      slaStatus: "SLA内",
      slaStatusType: "success",
      escalationStatus: "当班跟进",
      escalationStatusType: "info"
    }, "ISSUE-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-DETAIL"
  );
  assert.throws(
    () => normalizePaymentIssueDetailResult(null, "ISSUE-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-DETAIL"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  await assert.rejects(
    paymentIssueApi.getDetail("ISSUE-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-DETAIL"
  );
});

test("rejects payment issue pages with inconsistent risk status facts", () => {
  const validRow = {
    issueNo: "ISSUE-WAIT-PAY-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    customerName: "客户一",
    paymentMethod: "微信",
    channelCode: "wx_h5",
    issueType: "待回调未收口",
    issueTypeTag: "danger",
    severity: "P1",
    severityType: "danger",
    paymentStatus: "WAIT_CALLBACK",
    paymentStatusType: "warn",
    issueSummary: "支付单仍处于待回调状态",
    rootCauseHint: "建议主动查单",
    recommendedAction: "查看支付单",
    recommendedRoute: "/payments/PAY-001",
    handlingStatus: "待分派",
    handlingStatusType: "warn",
    assignee: "未分派",
    responsibilityGroup: "支付后端值班组",
    responsibilityGroupType: "danger",
    responsibilityHint: "优先核对查单",
    alertStatus: "未通知",
    alertStatusType: "info",
    alertAckStatus: "无需回执",
    alertAckStatusType: "info",
    alertReceiver: "未分配",
    alertChannelSummary: "未派发",
    alertDeliverySummary: "暂无派发明细",
    latestAlertProviderSummary: "暂无供应商派发记录",
    latestAlertReceiptSummary: "暂无投递回执",
    slaStatus: "SLA内",
    slaStatusType: "success",
    slaTimeLeft: "P1 SLA 30分钟",
    escalationStatus: "当班跟进",
    escalationStatusType: "info",
    escalationSuggestion: "由当前处理人继续跟进",
    latestActionSummary: "暂无处理动作",
    latestActionAt: null,
    createdAt: "2026-09-27 10:00:00"
  };
  assert.deepEqual(
    normalizePaymentIssueListPage({ items: [validRow], total: 1 }).items,
    [validRow]
  );
  const secondRow = {
    ...validRow,
    issueNo: "ISSUE-WAIT-PAY-002",
    paymentOrderId: "PAY-002",
    businessChainId: "CHAIN-PAY-002"
  };
  const batchPage = { items: [validRow, secondRow], total: 2, pageNo: 1, pageSize: 2 };
  assert.deepEqual(
    normalizePaymentIssueBatchActionResult(batchPage, [secondRow.issueNo, validRow.issueNo]).items,
    batchPage.items
  );
  const invalidBatchPages = [
    [{ ...batchPage }, [validRow.issueNo]],
    [{ ...batchPage, items: [validRow, validRow] }, [validRow.issueNo, secondRow.issueNo]],
    [{ ...batchPage, total: 3 }, [validRow.issueNo, secondRow.issueNo]],
    [{ ...batchPage, pageSize: 20 }, [validRow.issueNo, secondRow.issueNo]],
    [{ ...batchPage, pageNo: 2 }, [validRow.issueNo, secondRow.issueNo]],
    [{ ...batchPage, items: [validRow, null] }, [validRow.issueNo, secondRow.issueNo]],
    [{ ...batchPage }, [validRow.issueNo, validRow.issueNo]],
    [{ ...batchPage }, []]
  ];
  invalidBatchPages.forEach(([page, expected]) => {
    assert.throws(
      () => normalizePaymentIssueBatchActionResult(page, expected),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-ISSUE-BATCH-ACTION"
    );
  });
  assert.throws(
    () => normalizePaymentIssueListPage({
      items: [{ ...validRow, issueType: "未知异常", issueTypeTag: "danger" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-LIST"
  );
  assert.throws(
    () => normalizePaymentIssueListPage({
      items: [{ ...validRow, severity: "P1", severityType: "warn" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-LIST"
  );
  assert.throws(
    () => normalizePaymentIssueListPage({
      items: [{ ...validRow, paymentStatus: "SUCCESS", paymentStatusType: "warn" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-LIST"
  );
  assert.throws(
    () => normalizePaymentIssueListPage({
      items: [{ ...validRow, handlingStatus: "已处理", handlingStatusType: "warn" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-LIST"
  );
  assert.throws(
    () => normalizePaymentIssueListPage({
      items: [{ ...validRow, latestActionAt: "not-a-datetime" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-LIST"
  );
  assert.throws(
    () => normalizePaymentIssueListPage({
      items: [{ ...validRow, latestActionAt: "2026-09-27 09:59:59" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-LIST"
  );
  assert.throws(
    () => normalizePaymentIssueListPage({
      items: [{ ...validRow, createdAt: "not-a-datetime" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-LIST"
  );
  assert.throws(
    () => normalizePaymentIssueListPage({
      items: [{ ...validRow, businessChainId: "CHAIN-OTHER" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-LIST"
  );
});

test("applies payment issue list validation to API responses", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({
      code: "0",
      data: {
        items: [{
          issueNo: "ISSUE-WAIT-PAY-001",
          paymentOrderId: "PAY-001",
          businessChainId: "CHAIN-PAY-001",
          orderNo: "ORDER-001",
          customerName: "客户一",
          paymentMethod: "微信",
          channelCode: "wx_h5",
          issueType: "待回调未收口",
          issueTypeTag: "danger",
          severity: "P1",
          severityType: "danger",
          paymentStatus: "WAIT_CALLBACK",
          paymentStatusType: "warn",
          issueSummary: "支付单仍处于待回调状态",
          rootCauseHint: "建议主动查单",
          recommendedAction: "查看支付单",
          recommendedRoute: "/payments/PAY-001",
          handlingStatus: "跟进中",
          handlingStatusType: "warn",
          assignee: "支付运营",
          responsibilityGroup: "支付后端值班组",
          responsibilityGroupType: "danger",
          responsibilityHint: "优先核对查单",
          alertStatus: "已生成",
          alertStatusType: "info",
          alertAckStatus: "待确认",
          alertAckStatusType: "warn",
          alertReceiver: "支付运营",
          alertChannelSummary: "未派发",
          alertDeliverySummary: "待派发通道",
          latestAlertProviderSummary: "暂无供应商派发记录",
          latestAlertReceiptSummary: "暂无投递回执",
          slaStatus: "SLA内",
          slaStatusType: "success",
          slaTimeLeft: "P1 SLA 30分钟",
          escalationStatus: "当班跟进",
          escalationStatusType: "info",
          escalationSuggestion: "由当前处理人继续跟进",
          latestActionSummary: "分派处理人：支付运营",
          latestActionAt: "2026-09-27 10:01:00",
          createdAt: "2026-09-27 10:00:00"
        }],
        total: 1,
        pageNo: 1,
        pageSize: 20
      }
    })
  }));

  const result = await paymentIssueApi.getList();
  assert.equal(result.items[0].issueNo, "ISSUE-WAIT-PAY-001");
});

test("requires payment issue detail refreshes to preserve payment ownership", () => {
  const snapshot = {
    issueNo: "ISSUE-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    customerName: "张三",
    paymentMethod: "微信支付",
    channelCode: "WX_H5",
    issueType: "CALLBACK_TIMEOUT"
  };
  assert.equal(matchesPaymentIssueDetailSnapshot(snapshot, { ...snapshot }), true);
  assert.equal(
    matchesPaymentIssueDetailSnapshot(
      snapshot,
      {
        ...snapshot,
        issueNo: " ISSUE-001 ",
        paymentOrderId: " PAY-001 ",
        orderNo: " ORDER-001 ",
        issueType: " CALLBACK_TIMEOUT "
      }
    ),
    true
  );
  assert.equal(
    matchesPaymentIssueDetailSnapshot(snapshot, { ...snapshot, paymentOrderId: "PAY-002" }),
    false
  );
  assert.equal(
    matchesPaymentIssueDetailSnapshot(snapshot, { ...snapshot, orderNo: "ORDER-002" }),
    false
  );
  assert.equal(
    matchesPaymentIssueDetailSnapshot(snapshot, { ...snapshot, issueType: "CHANNEL_ERROR" }),
    false
  );
  assert.equal(
    matchesPaymentIssueDetailSnapshot(snapshot, { ...snapshot, businessChainId: "CHAIN-OTHER" }),
    false
  );
  assert.equal(
    matchesPaymentIssueDetailSnapshot(snapshot, { ...snapshot, customerName: "李四" }),
    false
  );
  assert.equal(
    matchesPaymentIssueDetailSnapshot(snapshot, { ...snapshot, paymentMethod: "支付宝" }),
    false
  );
  assert.equal(
    matchesPaymentIssueDetailSnapshot(snapshot, { ...snapshot, customerName: undefined, paymentMethod: undefined }),
    true
  );
  assert.equal(
    matchesPaymentIssueDetailSnapshot(snapshot, { ...snapshot, channelCode: "ALI_H5" }),
    false
  );
});

test("rejects empty or mismatched payment issue alert detail responses", async (context) => {
  const validDetail = {
    alertNo: "ALERT-001",
    sourceAlertNo: null,
    issueNo: "ISSUE-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    issueType: "待回调未收口",
    severity: "P1",
    responsibilityGroup: "支付后端值班组",
    alertChannel: "IN_APP_OUTBOX",
    receiver: "支付运营",
    alertStatus: "已派发",
    alertStatusType: "success",
    ackStatus: "待确认",
    ackStatusType: "warn",
    alertContent: "支付单仍处于待回调状态",
    receiptQueryCount: 0,
    triggeredBy: "payment-task-center",
    createdAt: "2026-09-27 10:00:00",
    lastReceiptQueryAt: null,
    nextReceiptQueryAt: null
  };
  assert.deepEqual(
    normalizePaymentIssueAlertDetailResult(validDetail, "ALERT-001"),
    validDetail
  );
  assert.equal(
    normalizePaymentIssueAlertDetailResult({ ...validDetail, alertNo: " ALERT-001 " }, " ALERT-001 ").alertNo,
    " ALERT-001 "
  );
  assert.throws(
    () => normalizePaymentIssueAlertDetailResult({ ...validDetail, businessChainId: "CHAIN-OTHER" }, "ALERT-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-ALERT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentIssueAlertDetailResult({ ...validDetail, sourceAlertNo: "SOURCE-ALERT-001" }, "ALERT-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-ALERT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentIssueAlertDetailResult({ ...validDetail, alertChannel: "SMS" }, "ALERT-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-ALERT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentIssueAlertDetailResult({ alertNo: "ALERT-002" }, "ALERT-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-ALERT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentIssueAlertDetailResult({ ...validDetail, alertStatus: "" }, "ALERT-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-ALERT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentIssueAlertDetailResult({ ...validDetail, ackStatus: "" }, "ALERT-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-ALERT-DETAIL"
  );
  for (const field of ["issueNo", "issueType", "severity", "responsibilityGroup", "alertChannel", "receiver", "alertContent", "triggeredBy"]) {
    assert.throws(
      () => normalizePaymentIssueAlertDetailResult({ ...validDetail, [field]: "" }, "ALERT-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-ALERT-DETAIL"
    );
  }
  assert.throws(
    () => normalizePaymentIssueAlertDetailResult({ ...validDetail, receiptQueryCount: -1 }, "ALERT-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-ALERT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentIssueAlertDetailResult({ ...validDetail, createdAt: "not-a-time" }, "ALERT-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-ALERT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentIssueAlertDetailResult({ ...validDetail, alertStatusType: "warn" }, "ALERT-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-ALERT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentIssueAlertDetailResult({ ...validDetail, ackStatusType: "info" }, "ALERT-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-ALERT-DETAIL"
  );
  assert.deepEqual(
    normalizePaymentIssueAlertDetailResult({ ...validDetail, receiptQueryState: "DUE" }, "ALERT-001").receiptQueryState,
    "DUE"
  );
  assert.throws(
    () => normalizePaymentIssueAlertDetailResult({ ...validDetail, receiptQueryState: "UNKNOWN" }, "ALERT-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-ALERT-DETAIL"
  );
  const acknowledgedDetail = {
    ...validDetail,
    ackStatus: "已确认",
    ackStatusType: "success",
    ackOperator: "ops",
    ackAt: "2026-09-27 10:01:00"
  };
  assert.deepEqual(
    normalizePaymentIssueAlertDetailResult(acknowledgedDetail, "ALERT-001"),
    acknowledgedDetail
  );
  for (const invalidAcknowledgement of [
    { ackOperator: null, ackAt: "2026-09-27 10:01:00" },
    { ackOperator: "ops", ackAt: null },
    { ackOperator: "", ackAt: "2026-09-27 10:01:00" },
    { ackOperator: "ops", ackAt: "not-a-time" },
    { ackOperator: "ops", ackAt: "2026-09-27 09:59:00" }
  ]) {
    assert.throws(
      () => normalizePaymentIssueAlertDetailResult(
        { ...acknowledgedDetail, ...invalidAcknowledgement },
        "ALERT-001"
      ),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-ALERT-DETAIL"
    );
  }
  assert.throws(
    () => normalizePaymentIssueAlertDetailResult([], "ALERT-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-ALERT-DETAIL"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  await assert.rejects(
    paymentIssueApi.getAlertDetail("ALERT-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-ALERT-DETAIL"
  );
});

test("rejects payment issue alert pages with inconsistent dispatch facts", () => {
  const validRow = {
    alertNo: "ALERT-001",
    sourceAlertNo: null,
    issueNo: "ISSUE-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    issueType: "待回调未收口",
    severity: "P1",
    responsibilityGroup: "支付后端值班组",
    alertChannel: "IN_APP_OUTBOX",
    receiver: "支付运营",
    alertStatus: "已生成",
    alertStatusType: "info",
    ackStatus: "待确认",
    ackStatusType: "warn",
    alertContent: "支付单仍处于待回调状态",
    providerCode: null,
    providerName: null,
    endpointAlias: null,
    templateCode: null,
    providerReceiptSnapshot: null,
    providerReceiptNo: null,
    providerDeliveryStatus: null,
    providerDeliveryMessage: null,
    receiptQueryState: null,
    receiptQueryCount: 0,
    lastReceiptQueryAt: null,
    nextReceiptQueryAt: null,
    receiptQueryResult: null,
    renderedContentSnapshot: "支付单仍处于待回调状态",
    triggeredBy: "payment-task-center",
    ackOperator: null,
    ackAt: null,
    createdAt: "2026-09-27 10:00:00"
  };
  assert.deepEqual(
    normalizePaymentIssueAlertListPage({ items: [validRow], total: 1 }).items,
    [validRow]
  );
  assert.throws(
    () => normalizePaymentIssueAlertListPage({
      items: [{ ...validRow, sourceAlertNo: "SOURCE-ALERT-001" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-ALERT-LIST"
  );
  assert.throws(
    () => normalizePaymentIssueAlertListPage({
      items: [{ ...validRow, alertChannel: "SMS" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-ALERT-LIST"
  );
  assert.throws(
    () => normalizePaymentIssueAlertListPage({
      items: [{ ...validRow, alertStatus: "派发失败", alertStatusType: "warn" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-ALERT-LIST"
  );
  assert.throws(
    () => normalizePaymentIssueAlertListPage({
      items: [{ ...validRow, ackStatus: "已确认", ackStatusType: "warn" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-ALERT-LIST"
  );
  assert.throws(
    () => normalizePaymentIssueAlertListPage({
      items: [{ ...validRow, receiptQueryState: "UNKNOWN" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-ALERT-LIST"
  );
  assert.throws(
    () => normalizePaymentIssueAlertListPage({
      items: [{ ...validRow, receiptQueryCount: -1 }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-ALERT-LIST"
  );
  assert.throws(
    () => normalizePaymentIssueAlertListPage({
      items: [{
        ...validRow,
        lastReceiptQueryAt: "2026-09-27 10:01:00",
        nextReceiptQueryAt: "2026-09-27 10:00:59"
      }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-ALERT-LIST"
  );
  assert.throws(
    () => normalizePaymentIssueAlertListPage({
      items: [{ ...validRow, createdAt: "not-a-datetime" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-ALERT-LIST"
  );
  assert.throws(
    () => normalizePaymentIssueAlertListPage({
      items: [{ ...validRow, businessChainId: "CHAIN-OTHER" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-ALERT-LIST"
  );
  const acknowledgedRow = {
    ...validRow,
    ackStatus: "已确认",
    ackStatusType: "success",
    ackOperator: "ops",
    ackAt: "2026-09-27 10:01:00"
  };
  assert.deepEqual(
    normalizePaymentIssueAlertListPage({ items: [acknowledgedRow], total: 1 }).items,
    [acknowledgedRow]
  );
  for (const invalidAcknowledgement of [
    { ackOperator: null, ackAt: "2026-09-27 10:01:00" },
    { ackOperator: "ops", ackAt: null },
    { ackOperator: "ops", ackAt: "not-a-time" },
    { ackOperator: "ops", ackAt: "2026-09-27 09:59:00" }
  ]) {
    assert.throws(
      () => normalizePaymentIssueAlertListPage({
        items: [{ ...acknowledgedRow, ...invalidAcknowledgement }],
        total: 1
      }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ISSUE-ALERT-LIST"
    );
  }
});

test("applies payment issue alert list validation to API responses", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({
      code: "0",
      data: {
        items: [{
          alertNo: "ALERT-001",
          sourceAlertNo: "SOURCE-ALERT-001",
          issueNo: "ISSUE-001",
          paymentOrderId: "PAY-001",
          businessChainId: "CHAIN-PAY-001",
          issueType: "待回调未收口",
          severity: "P1",
          responsibilityGroup: "支付后端值班组",
          alertChannel: "SMS",
          receiver: "支付运营",
          alertStatus: "已派发",
          alertStatusType: "success",
          ackStatus: "无需回执",
          ackStatusType: "info",
          alertContent: "支付单仍处于待回调状态",
          providerCode: "SMS-001",
          providerName: "短信供应商",
          endpointAlias: "sms-main",
          templateCode: "PAY-ISSUE-001",
          providerReceiptSnapshot: null,
          providerReceiptNo: "RECEIPT-001",
          providerDeliveryStatus: "ACCEPTED",
          providerDeliveryMessage: "accepted",
          receiptQueryState: null,
          receiptQueryCount: 0,
          lastReceiptQueryAt: null,
          nextReceiptQueryAt: null,
          receiptQueryResult: null,
          renderedContentSnapshot: "支付单仍处于待回调状态",
          triggeredBy: "payment-task-center",
          ackOperator: null,
          ackAt: null,
          createdAt: "2026-09-27 10:00:00"
        }],
        total: 1,
        pageNo: 1,
        pageSize: 20
      }
    })
  }));

  const result = await paymentIssueApi.getAlertLogs();
  assert.equal(result.items[0].alertNo, "ALERT-001");
});

test("requires payment issue alert detail refreshes to preserve alert ownership", () => {
  const snapshot = {
    alertNo: "ALERT-001",
    sourceAlertNo: "ALERT-SOURCE-001",
    issueNo: "ISSUE-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    issueType: "CALLBACK_TIMEOUT",
    severity: "HIGH"
  };
  assert.equal(matchesPaymentIssueAlertDetailSnapshot(snapshot, { ...snapshot }), true);
  assert.equal(
    matchesPaymentIssueAlertDetailSnapshot(
      snapshot,
      {
        ...snapshot,
        alertNo: " ALERT-001 ",
        sourceAlertNo: " ALERT-SOURCE-001 ",
        issueNo: " ISSUE-001 ",
        paymentOrderId: " PAY-001 ",
        businessChainId: " CHAIN-PAY-001 ",
        issueType: " CALLBACK_TIMEOUT ",
        severity: " HIGH "
      }
    ),
    true
  );
  assert.equal(
    matchesPaymentIssueAlertDetailSnapshot(snapshot, { ...snapshot, paymentOrderId: "PAY-002" }),
    false
  );
  assert.equal(
    matchesPaymentIssueAlertDetailSnapshot(snapshot, { ...snapshot, businessChainId: "CHAIN-OTHER" }),
    false
  );
  assert.equal(
    matchesPaymentIssueAlertDetailSnapshot(snapshot, { ...snapshot, issueNo: "ISSUE-002" }),
    false
  );
  assert.equal(
    matchesPaymentIssueAlertDetailSnapshot(snapshot, { ...snapshot, severity: "P1" }),
    false
  );
});

test("rejects empty or mismatched payment log detail responses", async (context) => {
  assert.deepEqual(
    normalizePaymentLogDetailResult({ logNo: "LOG-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", processStage: "SUBMIT", logLevel: "INFO", logLevelType: "info", successFactIssue: null, createdAt: "2026-09-27 10:00:00" }, "LOG-001"),
    { logNo: "LOG-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", processStage: "SUBMIT", logLevel: "INFO", logLevelType: "info", successFactIssue: null, createdAt: "2026-09-27 10:00:00" }
  );
  assert.equal(
    normalizePaymentLogDetailResult({ logNo: " LOG-001 ", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", processStage: "SUBMIT", logLevel: "INFO", logLevelType: "info", successFactIssue: null, createdAt: "2026-09-27 10:00:00" }, " LOG-001 ").logNo,
    " LOG-001 "
  );
  assert.equal(
    normalizePaymentLogDetailResult({ logNo: "LOG-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", processStage: "渠道回调", logLevel: "INFO", logLevelType: "success", successFactIssue: "SUCCESS_FACT_MISMATCH", createdAt: "2026-09-27 10:00:00" }, "LOG-001").logLevelType,
    "success"
  );
  for (const successFactIssue of [undefined, "UNKNOWN"]) {
    assert.throws(
      () => normalizePaymentLogDetailResult({
        logNo: "LOG-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001",
        processStage: "渠道回调", logLevel: "INFO", logLevelType: "success",
        successFactIssue, createdAt: "2026-09-27 10:00:00"
      }, "LOG-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LOG-DETAIL"
    );
  }
  assert.throws(
    () => normalizePaymentLogDetailResult({ logNo: "LOG-002" }, "LOG-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LOG-DETAIL"
  );
  assert.throws(
    () => normalizePaymentLogDetailResult({ logNo: "LOG-001", processStage: "", logLevel: "INFO", logLevelType: "info" }, "LOG-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LOG-DETAIL"
  );
  assert.throws(
    () => normalizePaymentLogDetailResult({ logNo: "LOG-001", processStage: "SUBMIT", logLevel: "", logLevelType: "info" }, "LOG-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LOG-DETAIL"
  );
  for (const invalidDetail of [
    { logNo: "LOG-001", processStage: "SUBMIT", logLevel: "INFO" },
    { logNo: "LOG-001", processStage: "SUBMIT", logLevel: "INFO", logLevelType: "warn" },
    { logNo: "LOG-001", processStage: "SUBMIT", logLevel: "WARN", logLevelType: "success" },
    { logNo: "LOG-001", processStage: "SUBMIT", logLevel: "TRACE", logLevelType: "info" },
    { logNo: "LOG-001", processStage: "SUBMIT", logLevel: "INFO", logLevelType: "info", createdAt: "not-a-time" }
  ]) {
    assert.throws(
      () => normalizePaymentLogDetailResult(invalidDetail, "LOG-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LOG-DETAIL"
    );
  }
  assert.throws(
    () => normalizePaymentLogDetailResult(null, "LOG-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LOG-DETAIL"
  );
  assert.throws(
    () => normalizePaymentLogDetailResult({
      logNo: "LOG-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-OTHER",
      processStage: "SUBMIT", logLevel: "INFO", logLevelType: "info", createdAt: "2026-09-27 10:00:00"
    }, "LOG-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LOG-DETAIL"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  await assert.rejects(
    paymentLogApi.getDetail("LOG-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LOG-DETAIL"
  );
});

test("rejects payment log pages with inconsistent level and source facts", () => {
  const validRow = {
    logNo: "LOG-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    processStage: "渠道回调",
    logLevel: "WARN",
    logLevelType: "warn",
    successFactIssue: null,
    message: "等待渠道回调",
    source: "payment-core",
    createdAt: "2026-09-27 10:00:00"
  };
  assert.deepEqual(
    normalizePaymentLogListPage({ items: [validRow], total: 1 }).items,
    [validRow]
  );
  const successfulInfo = {
    ...validRow, logLevel: "INFO", logLevelType: "success",
    successFactIssue: "SUCCESS_FACT_MISMATCH"
  };
  assert.deepEqual(
    normalizePaymentLogListPage({ items: [validRow, successfulInfo], total: 2 }).items,
    [validRow, successfulInfo]
  );
  assert.equal(normalizePaymentLogListPage({
    items: [{ ...successfulInfo, successFactIssue: "SUCCESS_TIME_MISSING" }], total: 1
  }).items[0].successFactIssue, "SUCCESS_TIME_MISSING");
  for (const successFactIssue of [undefined, "UNKNOWN"]) {
    assert.throws(
      () => normalizePaymentLogListPage({ items: [{ ...validRow, successFactIssue }], total: 1 }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LOG-LIST"
    );
  }
  assert.throws(
    () => normalizePaymentLogListPage({
      items: [{ ...validRow, logLevel: "TRACE", logLevelType: "info" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LOG-LIST"
  );
  assert.throws(
    () => normalizePaymentLogListPage({
      items: [{ ...validRow, logLevel: "ERROR", logLevelType: "warn" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LOG-LIST"
  );
  assert.throws(
    () => normalizePaymentLogListPage({ items: [{ ...successfulInfo, logLevel: "ERROR" }], total: 1 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LOG-LIST"
  );
  assert.throws(
    () => normalizePaymentLogListPage({
      items: [{ ...validRow, paymentOrderId: "" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LOG-LIST"
  );
  assert.throws(
    () => normalizePaymentLogListPage({
      items: [{ ...validRow, message: "" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LOG-LIST"
  );
  assert.throws(
    () => normalizePaymentLogListPage({
      items: [{ ...validRow, createdAt: "not-a-time" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LOG-LIST"
  );
  assert.throws(
    () => normalizePaymentLogListPage({
      items: [{ ...validRow, businessChainId: "CHAIN-OTHER" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-LOG-LIST"
  );
});

test("applies payment log list validation to API responses", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({
      code: "0",
      data: {
        items: [{
          logNo: "LOG-001",
          paymentOrderId: "PAY-001",
          businessChainId: "CHAIN-PAY-001",
          orderNo: "ORDER-001",
          processStage: "支付提交",
          logLevel: "INFO",
          logLevelType: "info",
          successFactIssue: null,
          message: "支付请求已创建",
          source: "payment-core",
          createdAt: "2026-09-27 10:00:00"
        }],
        total: 1,
        pageNo: 1,
        pageSize: 20
      }
    })
  }));

  const result = await paymentLogApi.getList();
  assert.equal(result.items[0].logNo, "LOG-001");
});

test("requires payment log detail refreshes to preserve source and payment associations", () => {
  const snapshot = {
    logNo: "LOG-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    standardOrderNo: "ORDER-001",
    requestNo: "REQ-001",
    sourceType: "PAYMENT_ATTEMPT",
    sourceNo: "ATTEMPT-001",
    processStage: "SUBMIT"
  };
  assert.equal(matchesPaymentLogDetailSnapshot(snapshot, { ...snapshot }), true);
  assert.equal(
    matchesPaymentLogDetailSnapshot(
      snapshot,
      Object.fromEntries(Object.entries(snapshot).map(([key, value]) => [key, ` ${value} `]))
    ),
    true
  );
  assert.equal(
    matchesPaymentLogDetailSnapshot(snapshot, { ...snapshot, paymentOrderId: "PAY-002" }),
    false
  );
  assert.equal(
    matchesPaymentLogDetailSnapshot(snapshot, { ...snapshot, orderNo: "ORDER-002" }),
    false
  );
  assert.equal(
    matchesPaymentLogDetailSnapshot(snapshot, { ...snapshot, sourceNo: "ATTEMPT-002" }),
    false
  );
  assert.equal(
    matchesPaymentLogDetailSnapshot(snapshot, { ...snapshot, businessChainId: "CHAIN-OTHER" }),
    false
  );
});

test("rejects empty or mismatched payment task run detail responses", async (context) => {
  assert.deepEqual(
    normalizePaymentTaskRunDetailResult({
      taskLogNo: "TASK-001",
      taskStatus: "WARNING",
      taskStatusType: "warn",
      processedCount: 4,
      successCount: 3,
      warningCount: 1,
      failCount: 0,
      startedAt: "2026-09-27 10:00:00",
      completedAt: "2026-09-27 10:00:05"
    }, "TASK-001"),
    {
      taskLogNo: "TASK-001",
      taskStatus: "WARNING",
      taskStatusType: "warn",
      processedCount: 4,
      successCount: 3,
      warningCount: 1,
      failCount: 0,
      startedAt: "2026-09-27 10:00:00",
      completedAt: "2026-09-27 10:00:05"
    }
  );
  assert.equal(
    normalizePaymentTaskRunDetailResult({
      taskLogNo: " TASK-001 ",
      taskStatus: "WARNING",
      taskStatusType: "warn",
      processedCount: 4,
      successCount: 3,
      warningCount: 1,
      failCount: 0,
      startedAt: "2026-09-27 10:00:00",
      completedAt: "2026-09-27 10:00:05"
    }, " TASK-001 ").taskLogNo,
    " TASK-001 "
  );
  assert.throws(
    () => normalizePaymentTaskRunDetailResult({ taskLogNo: "TASK-002" }, "TASK-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-RUN-DETAIL"
  );
  assert.deepEqual(
    normalizePaymentTaskRunDetailResult({
      taskLogNo: "TASK-LEASE-001",
      taskStatus: "WARNING",
      taskStatusType: "warn",
      processedCount: 0,
      successCount: 0,
      warningCount: 0,
      failCount: 0,
      startedAt: "2026-09-27 10:00:00",
      completedAt: "2026-09-27 10:00:00"
    }, "TASK-LEASE-001").taskStatus,
    "WARNING"
  );
  for (const invalidDetail of [
    {
      taskLogNo: "TASK-001",
      taskStatus: "SUCCESS",
      processedCount: 1,
      successCount: 0,
      warningCount: 1,
      failCount: 0
    },
    {
      taskLogNo: "TASK-001",
      taskStatus: "WARNING",
      processedCount: 1,
      successCount: 1,
      warningCount: 0,
      failCount: 0
    },
    {
      taskLogNo: "TASK-001",
      taskStatus: "UNKNOWN",
      processedCount: 0,
      successCount: 0,
      warningCount: 0,
      failCount: 0
    }
  ]) {
    assert.throws(
      () => normalizePaymentTaskRunDetailResult(invalidDetail, "TASK-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-RUN-DETAIL"
    );
  }
  assert.throws(
    () => normalizePaymentTaskRunDetailResult({
      taskLogNo: "TASK-001",
      taskStatus: "SUCCESS",
      processedCount: -1,
      successCount: 0,
      warningCount: 0,
      failCount: 0
    }, "TASK-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-RUN-DETAIL"
  );
  assert.throws(
    () => normalizePaymentTaskRunDetailResult({
      taskLogNo: "TASK-001",
      taskStatus: "WARNING",
      taskStatusType: "success",
      processedCount: 0,
      successCount: 0,
      warningCount: 0,
      failCount: 0
    }, "TASK-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-RUN-DETAIL"
  );
  assert.throws(
    () => normalizePaymentTaskRunDetailResult({
      taskLogNo: "TASK-001",
      taskStatus: "",
      processedCount: 0,
      successCount: 0,
      warningCount: 0,
      failCount: 0
    }, "TASK-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-RUN-DETAIL"
  );
  assert.throws(
    () => normalizePaymentTaskRunDetailResult({
      taskLogNo: "TASK-001",
      taskStatus: "SUCCESS",
      processedCount: 2,
      successCount: 1,
      warningCount: 1,
      failCount: 1
    }, "TASK-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-RUN-DETAIL"
  );
  assert.throws(
    () => normalizePaymentTaskRunDetailResult({
      taskLogNo: "TASK-001",
      taskStatus: "SUCCESS",
      taskStatusType: "success",
      processedCount: 2,
      successCount: 1,
      warningCount: 0,
      failCount: 0,
      startedAt: "2026-09-27 10:00:00",
      completedAt: "2026-09-27 10:00:05"
    }, "TASK-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-RUN-DETAIL"
  );
  assert.throws(
    () => normalizePaymentTaskRunDetailResult(null, "TASK-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-RUN-DETAIL"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  await assert.rejects(
    paymentTaskCenterApi.getTaskRunDetail("TASK-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-RUN-DETAIL"
  );
});

test("rejects payment task run pages with an inconsistent row", () => {
  const validRow = {
    taskLogNo: "TASK-001",
    taskStatus: "WARNING",
    taskStatusType: "warn",
    processedCount: 2,
    successCount: 1,
    warningCount: 1,
    failCount: 0,
    startedAt: "2026-09-27 10:00:00",
    completedAt: "2026-09-27 10:00:05"
  };
  assert.deepEqual(
    normalizePaymentTaskRunsPage({ items: [validRow], total: 1 }).items,
    [validRow]
  );
  assert.throws(
    () => normalizePaymentTaskRunsPage({
      items: [{ ...validRow, taskStatus: "SUCCESS" }],
      total: 1
    }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-RUN-DETAIL"
  );
  assert.throws(
    () => normalizePaymentTaskRunsPage({
      items: [{ ...validRow, completedAt: "2026-09-27 09:59:59" }],
      total: 1
    }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-RUN-DETAIL"
  );
  assert.throws(
    () => normalizePaymentTaskRunsPage({
      items: [{ ...validRow, processedCount: 3 }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-RUN-DETAIL"
  );
  for (const bizDate of ["2026-02-30", "2026-09-27 10:00", ""] ) {
    assert.throws(
      () => normalizePaymentTaskRunsPage({
        items: [{ ...validRow, bizDate }],
        total: 1
      }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-RUN-DETAIL"
    );
  }
});

test("requires payment task run detail refreshes to preserve execution context", () => {
  const snapshot = {
    taskLogNo: "TASK-001",
    taskCode: "PAYMENT_DAY_END",
    bizDate: "2026-09-27",
    businessRefNo: "BATCH-001"
  };
  assert.equal(matchesPaymentTaskRunDetailSnapshot(snapshot, { ...snapshot }), true);
  assert.equal(
    matchesPaymentTaskRunDetailSnapshot(
      snapshot,
      Object.fromEntries(Object.entries(snapshot).map(([key, value]) => [key, ` ${value} `]))
    ),
    true
  );
  assert.equal(
    matchesPaymentTaskRunDetailSnapshot(snapshot, { ...snapshot, taskCode: "PAYMENT_RETRY" }),
    false
  );
  assert.equal(
    matchesPaymentTaskRunDetailSnapshot(snapshot, { ...snapshot, bizDate: "2026-09-28" }),
    false
  );
  assert.equal(
    matchesPaymentTaskRunDetailSnapshot(snapshot, { ...snapshot, businessRefNo: "BATCH-002" }),
    false
  );
});

test("rejects empty or mismatched dead-letter task detail responses", async (context) => {
  assert.deepEqual(
    normalizePaymentDeadLetterTaskDetailResult({
      taskNo: "DL-001",
      messageId: "MSG-001",
      targetSystem: "ACCOUNTING",
      deadLetterRoutingKey: "payment.dead-letter",
      replayExchange: "payment.exchange",
      replayRoutingKey: "payment.replay",
      taskStatus: "PENDING_REVIEW",
      taskStatusType: "warn",
      replayCount: 2,
      lastReplayAt: null,
      resolvedAt: null,
      createdAt: "2026-09-27 10:00:00",
      updatedAt: "2026-09-27 10:00:00"
    }, "DL-001"),
    {
      taskNo: "DL-001",
      messageId: "MSG-001",
      targetSystem: "ACCOUNTING",
      deadLetterRoutingKey: "payment.dead-letter",
      replayExchange: "payment.exchange",
      replayRoutingKey: "payment.replay",
      taskStatus: "PENDING_REVIEW",
      taskStatusType: "warn",
      replayCount: 2,
      lastReplayAt: null,
      resolvedAt: null,
      createdAt: "2026-09-27 10:00:00",
      updatedAt: "2026-09-27 10:00:00"
    }
  );
  assert.equal(
    normalizePaymentDeadLetterTaskDetailResult({
      taskNo: " DL-001 ",
      messageId: "MSG-001",
      targetSystem: "ACCOUNTING",
      deadLetterRoutingKey: "payment.dead-letter",
      replayExchange: "payment.exchange",
      replayRoutingKey: "payment.replay",
      taskStatus: "PENDING_REVIEW",
      taskStatusType: "warn",
      replayCount: 2,
      lastReplayAt: null,
      resolvedAt: null,
      createdAt: "2026-09-27 10:00:00",
      updatedAt: "2026-09-27 10:00:00"
    }, " DL-001 ").taskNo,
    " DL-001 "
  );
  assert.throws(
    () => normalizePaymentDeadLetterTaskDetailResult({
      taskNo: "DL-001",
      messageId: "MSG-001",
      targetSystem: "ACCOUNTING",
      deadLetterRoutingKey: "payment.dead-letter",
      replayExchange: "payment.exchange",
      replayRoutingKey: "payment.replay",
      taskStatus: "PENDING_REVIEW",
      taskStatusType: "warn",
      replayCount: 2,
      createdAt: "2026-09-27 10:01:00",
      updatedAt: "2026-09-27 10:00:00"
    }, "DL-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DEAD-LETTER-TASK-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDeadLetterTaskDetailResult({
      taskNo: "DL-001",
      messageId: "MSG-001",
      targetSystem: "ACCOUNTING",
      deadLetterRoutingKey: "payment.dead-letter",
      replayExchange: "payment.exchange",
      replayRoutingKey: "payment.replay",
      taskStatus: "REPLAYED",
      taskStatusType: "success",
      replayCount: 1,
      lastReplayAt: "2026-09-27 09:59:00",
      resolvedAt: null,
      createdAt: "2026-09-27 10:00:00",
      updatedAt: "2026-09-27 10:02:00"
    }, "DL-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DEAD-LETTER-TASK-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDeadLetterTaskDetailResult({
      taskNo: "DL-001",
      messageId: "MSG-001",
      targetSystem: "ACCOUNTING",
      deadLetterRoutingKey: "payment.dead-letter",
      replayExchange: "payment.exchange",
      replayRoutingKey: "payment.replay",
      taskStatus: "MANUAL_RESOLVED",
      taskStatusType: "success",
      replayCount: 1,
      lastReplayAt: "2026-09-27 10:01:00",
      resolvedAt: "2026-09-27 10:03:00",
      createdAt: "2026-09-27 10:00:00",
      updatedAt: "2026-09-27 10:02:00"
    }, "DL-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DEAD-LETTER-TASK-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDeadLetterTaskDetailResult({ taskNo: "DL-002" }, "DL-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DEAD-LETTER-TASK-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDeadLetterTaskDetailResult({ taskNo: "DL-001", taskStatus: "PENDING_REVIEW", replayCount: -1 }, "DL-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DEAD-LETTER-TASK-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDeadLetterTaskDetailResult({ taskNo: "DL-001", taskStatus: "", replayCount: 0 }, "DL-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DEAD-LETTER-TASK-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDeadLetterTaskDetailResult({ taskNo: "DL-001", taskStatus: "UNKNOWN", replayCount: 0 }, "DL-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DEAD-LETTER-TASK-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDeadLetterTaskDetailResult({
      taskNo: "DL-001",
      messageId: "MSG-001",
      targetSystem: "ACCOUNTING",
      deadLetterRoutingKey: "payment.dead-letter",
      replayExchange: "payment.exchange",
      replayRoutingKey: "payment.replay",
      taskStatus: "PENDING_REVIEW",
      replayCount: 0
    }, "DL-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DEAD-LETTER-TASK-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDeadLetterTaskDetailResult({
      taskNo: "DL-001",
      messageId: "MSG-001",
      targetSystem: "ACCOUNTING",
      deadLetterRoutingKey: "payment.dead-letter",
      replayExchange: "payment.exchange",
      replayRoutingKey: "payment.replay",
      taskStatus: "PENDING_REVIEW",
      taskStatusType: "warn",
      replayCount: 0,
      createdAt: "not-a-datetime",
      updatedAt: "2026-09-27 10:00:00"
    }, "DL-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DEAD-LETTER-TASK-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDeadLetterTaskDetailResult({
      taskNo: "DL-001",
      messageId: "MSG-001",
      targetSystem: "ACCOUNTING",
      deadLetterRoutingKey: "payment.dead-letter",
      replayExchange: "payment.exchange",
      replayRoutingKey: "payment.replay",
      taskStatus: "REPLAY_FAILED",
      taskStatusType: "warn",
      replayCount: 0
    }, "DL-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DEAD-LETTER-TASK-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDeadLetterTaskDetailResult([], "DL-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DEAD-LETTER-TASK-DETAIL"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  await assert.rejects(
    paymentDeadLetterTaskApi.getDetail("DL-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DEAD-LETTER-TASK-DETAIL"
  );
});

test("trims dead-letter task numbers before detail requests", async (context) => {
  let requestedUrl = "";
  context.mock.method(globalThis, "fetch", async (url) => {
    requestedUrl = url;
    return {
      ok: true,
      json: async () => ({
        code: "0",
        data: {
          taskNo: "DL-001",
          messageId: "MSG-001",
          targetSystem: "ACCOUNTING",
          deadLetterRoutingKey: "payment.dead-letter",
          replayExchange: "payment.exchange",
          replayRoutingKey: "payment.replay",
          taskStatus: "PENDING_REVIEW",
          taskStatusType: "warn",
          replayCount: 0,
          lastReplayAt: null,
          resolvedAt: null,
          createdAt: "2026-09-27 10:00:00",
          updatedAt: "2026-09-27 10:00:00"
        }
      })
    };
  });

  await paymentDeadLetterTaskApi.getDetail("  DL-001  ");

  assert.equal(requestedUrl, "/api/payment-dead-letter-tasks/DL-001");
});

test("trims business identifiers before admin detail requests", async (context) => {
  const requests = [];
  context.mock.method(globalThis, "fetch", async (url) => {
    requests.push(url);
    return {
      ok: true,
      json: async () => ({ code: "0", data: null })
    };
  });

  const detailRequests = [
    [paymentIssueApi.getDetail, ["  ISSUE-001  "], "/api/payment-issues/ISSUE-001"],
    [paymentIssueApi.getAlertDetail, ["  ALERT-001  "], "/api/payment-issues/alerts/ALERT-001"],
    [paymentDayEndApi.getDetail, ["  BATCH-001  "], "/api/payment-day-end/batches/BATCH-001"],
    [paymentTaskCenterApi.getTaskRunDetail, ["  RUN-001  "], "/api/payment-task-center/task-runs/RUN-001"],
    [orderApi.getDetail, ["  ORD-001  "], "/api/orders/ORD-001"],
    [billApi.getDetail, ["  BILL-001  "], "/api/bills/BILL-001"],
    [paymentFlowApi.getDetail, [" PAYMENT_ATTEMPT ", " FLOW-001 "], "/api/payment-flows/PAYMENT_ATTEMPT/FLOW-001"],
    [paymentRouteApi.getDetail, ["  ROUTE-001  "], "/api/payment-routes/ROUTE-001"],
    [paymentEventApi.getDetail, ["  EVENT-001  "], "/api/payment-events/EVENT-001"],
    [cashierSessionApi.getDetail, ["  SESSION-001  "], "/api/cashier-sessions/SESSION-001"],
    [paymentRequestApi.getDetail, ["  REQUEST-001  "], "/api/payment-requests/REQUEST-001"],
    [paymentLogApi.getDetail, ["  LOG-001  "], "/api/payment-logs/LOG-001"],
    [paymentRecordApi.getDetail, ["  PAY-001  "], "/api/payment-records/PAY-001"],
    [paymentApi.getDetail, ["  PAY-001  "], "/api/payments/PAY-001"],
    [refundApi.getDetail, ["  REF-001  "], "/api/refunds/REF-001"]
  ];

  for (const [requestDetail, args, expectedUrl] of detailRequests) {
    await assert.rejects(requestDetail(...args));
    assert.equal(requests.at(-1), expectedUrl);
  }
});

test("trims payment issue identifiers before admin query requests", async (context) => {
  const requests = [];
  context.mock.method(globalThis, "fetch", async (url) => {
    requests.push(url);
    const data = url.includes("responsibility-summary")
      ? []
      : { items: [], total: 0, pageNo: 1, pageSize: 20 };
    return {
      ok: true,
      json: async () => ({
        code: "0",
        data
      })
    };
  });

  await paymentIssueApi.getList({ paymentOrderId: "  PAY-001  ", orderNo: "  ORDER-001  " });
  await paymentIssueApi.getResponsibilitySummary({ paymentOrderId: "  PAY-002  ", orderNo: "  ORDER-002  " });
  await paymentIssueApi.getAlertLogs({
    alertNo: "  ALERT-001  ",
    issueNo: "  ISSUE-001  ",
    paymentOrderId: "  PAY-003  "
  });
  assert.equal(
    paymentIssueApi.buildAlertExportUrl({
      alertNo: "  ALERT-002  ",
      issueNo: "  ISSUE-002  ",
      paymentOrderId: "  PAY-004  "
    }),
    "/api/payment-issues/alerts/export?alertNo=ALERT-002&issueNo=ISSUE-002&paymentOrderId=PAY-004&alertChannel=%E5%85%A8%E9%83%A8&alertStatus=%E5%85%A8%E9%83%A8&ackStatus=%E5%85%A8%E9%83%A8&providerDeliveryStatus=%E5%85%A8%E9%83%A8&receiptQueryState=%E5%85%A8%E9%83%A8"
  );
  assert.equal(requests[0], "/api/payment-issues?paymentOrderId=PAY-001&orderNo=ORDER-001&issueType=%E5%85%A8%E9%83%A8&severity=%E5%85%A8%E9%83%A8&channelCode=&paymentMethod=%E5%85%A8%E9%83%A8&pageNo=1&pageSize=20");
  assert.equal(requests[1], "/api/payment-issues/responsibility-summary?paymentOrderId=PAY-002&orderNo=ORDER-002&issueType=%E5%85%A8%E9%83%A8&severity=%E5%85%A8%E9%83%A8&channelCode=&paymentMethod=%E5%85%A8%E9%83%A8");
  assert.equal(requests[2], "/api/payment-issues/alerts?alertNo=ALERT-001&issueNo=ISSUE-001&paymentOrderId=PAY-003&alertChannel=%E5%85%A8%E9%83%A8&alertStatus=%E5%85%A8%E9%83%A8&ackStatus=%E5%85%A8%E9%83%A8&providerDeliveryStatus=%E5%85%A8%E9%83%A8&receiptQueryState=%E5%85%A8%E9%83%A8&pageNo=1&pageSize=20");
});

test("binds payment action responses to the snapshot prepay identity", async (context) => {
  const actionResponse = {
    paymentOrderId: "PAY-001",
    prepayOrderNo: "PRE-002",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    amount: "68.00",
    status: "SUCCESS",
    statusType: "success",
    channel: "WX_H5",
    channelTransactionNo: "WX-001",
    successAt: "2026-09-27 10:01:00",
    createdAt: "2026-09-27 10:00:00",
    latestAttemptStatus: "成功",
    latestAttemptStatusType: "success",
    latestAttemptCompletedAt: "2026-09-27 10:00:30"
  };
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: actionResponse })
  }));

  await assert.rejects(
    paymentApi.query("PAY-001", "ops", {
      orderNo: "ORDER-001", amount: "68.00", prepayOrderNo: "PRE-001"
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
  await assert.rejects(
    paymentApi.close("PAY-001", "ops", {
      orderNo: "ORDER-001", amount: "68.00", prepayOrderNo: "PRE-001"
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
});

test("trims business identifiers before admin action requests", async (context) => {
  const calls = [];
  context.mock.method(globalThis, "fetch", async (url, options) => {
    calls.push({ url, body: JSON.parse(options.body) });
    return {
      ok: true,
      json: async () => ({ code: "0", data: null })
    };
  });

  await assert.rejects(paymentApi.query("  PAY-001  ", "ops"));
  await assert.rejects(paymentApi.close("  PAY-002  ", "ops"));
  await assert.rejects(refundApi.approve("  REF-001  ", "ops"));
  await assert.rejects(refundApi.query("  REF-002  ", "ops"));
  await assert.rejects(refundApi.retry("  REF-003  ", "ops"));
  await assert.rejects(paymentEventApi.republish("  EVENT-001  ", "ops"));

  assert.deepEqual(calls.map(({ body }) => body), [
    { paymentOrderId: "PAY-001", operator: "ops" },
    { paymentOrderId: "PAY-002", operator: "ops" },
    { refundOrderId: "REF-001", operator: "ops", remark: "" },
    { refundOrderId: "REF-002", operator: "ops" },
    { refundOrderId: "REF-003", operator: "ops", remark: "" },
    { eventNo: "EVENT-001", operator: "ops" }
  ]);
});

test("trims payment configuration identifiers before admin requests", async (context) => {
  const calls = [];
  context.mock.method(globalThis, "fetch", async (url, options) => {
    calls.push({ url, body: options?.body ? JSON.parse(options.body) : null });
    return {
      ok: true,
      json: async () => ({ code: "0", data: null })
    };
  });

  const requests = [
    [paymentConfigApi.approveChannelReview, ["  REQ-001  ", {}], "/api/payment-config/channels/reviews/REQ-001/approve"],
    [paymentConfigApi.getChannelVersions, ["  WECHAT  "], "/api/payment-config/channels/WECHAT/versions"],
    [paymentConfigApi.rollbackGateway, ["  GW-001  ", {}], "/api/payment-config/gateways/GW-001/rollback"],
    [paymentConfigApi.getRouteRuleVersions, ["  RULE-001  "], "/api/payment-config/route-rules/RULE-001/versions"],
    [paymentConfigApi.approveProtocolReview, ["  REQ-002  ", {}], "/api/payment-config/protocols/reviews/REQ-002/approve"],
    [paymentConfigApi.updateIssueDutyRoster, ["  ROSTER-001  ", {}], "/api/payment-config/issue-duty-rosters/ROSTER-001"],
    [paymentConfigApi.updateAlertProvider, ["  PROVIDER-001  ", {}], "/api/payment-config/alert-providers/PROVIDER-001"],
    [paymentConfigApi.approveReturnCodeReview, ["  REQ-003  ", {}], "/api/payment-config/return-codes/reviews/REQ-003/approve"],
    [paymentConfigApi.getControlPolicyVersions, ["  APP-001  "], "/api/payment-config/control-policies/APP-001/versions"],
    [paymentConfigApi.runControlPolicySelfCheck, ["  APP-002  "], "/api/payment-config/control-policies/self-check"]
  ];

  for (const [requestMethod, args, expectedUrl] of requests) {
    await requestMethod(...args).catch(() => undefined);
    assert.equal(calls.at(-1).url, expectedUrl);
  }
  assert.deepEqual(calls.at(-1).body, { configCode: "APP-002" });

  await paymentConfigApi.submitChannelStatusReview({ channelCode: "  WECHAT  " }).catch(() => undefined);
  assert.equal(calls.at(-1).url, "/api/payment-config/channels/toggle");
  assert.equal(calls.at(-1).body.channelCode, "WECHAT");

  await paymentConfigApi.submitGatewayStatusReview({ gatewayCode: "  GW-001  " }).catch(() => undefined);
  assert.equal(calls.at(-1).url, "/api/payment-config/gateways/toggle");
  assert.equal(calls.at(-1).body.gatewayCode, "GW-001");

  await paymentConfigApi.submitProtocolReview({ candidate: { protocolCode: "  PROTOCOL-001  " } }).catch(() => undefined);
  assert.equal(calls.at(-1).url, "/api/payment-config/protocols/reviews");
  assert.equal(calls.at(-1).body.candidate.protocolCode, "PROTOCOL-001");

  await paymentConfigApi.submitGatewayReview({ candidate: { gatewayCode: "  GW-002  " } }).catch(() => undefined);
  assert.equal(calls.at(-1).url, "/api/payment-config/gateways/reviews");
  assert.equal(calls.at(-1).body.candidate.gatewayCode, "GW-002");

  await paymentConfigApi.submitChannelReview({ candidate: { channelCode: "  CHANNEL-002  " } }).catch(() => undefined);
  assert.equal(calls.at(-1).url, "/api/payment-config/channels/reviews");
  assert.equal(calls.at(-1).body.candidate.channelCode, "CHANNEL-002");

  await paymentConfigApi.submitRouteRuleStatusReview({ ruleCode: "  RULE-002  " }).catch(() => undefined);
  assert.equal(calls.at(-1).url, "/api/payment-config/route-rules/toggle");
  assert.equal(calls.at(-1).body.ruleCode, "RULE-002");

  await paymentConfigApi.submitRouteRuleReview({ candidate: { ruleCode: "  RULE-003  " } }).catch(() => undefined);
  assert.equal(calls.at(-1).url, "/api/payment-config/route-rules/reviews");
  assert.equal(calls.at(-1).body.candidate.ruleCode, "RULE-003");

  await paymentConfigApi.toggleProtocol({ protocolCode: "  PROTOCOL-002  " }).catch(() => undefined);
  assert.equal(calls.at(-1).url, "/api/payment-config/protocols/toggle");
  assert.equal(calls.at(-1).body.protocolCode, "PROTOCOL-002");

  await paymentConfigApi.submitReturnCodeStatusReview({
    channelCode: "  CHANNEL-003  ",
    channelReturnCode: "  SUCCESS  "
  }).catch(() => undefined);
  assert.equal(calls.at(-1).url, "/api/payment-config/return-codes/toggle");
  assert.equal(calls.at(-1).body.channelCode, "CHANNEL-003");
  assert.equal(calls.at(-1).body.channelReturnCode, "SUCCESS");

  await paymentConfigApi.createIssueDutyRoster({ rosterCode: "  ROSTER-002  " }).catch(() => undefined);
  assert.equal(calls.at(-1).url, "/api/payment-config/issue-duty-rosters");
  assert.equal(calls.at(-1).body.rosterCode, "ROSTER-002");

  await paymentConfigApi.createAlertProvider({ providerCode: "  PROVIDER-002  " }).catch(() => undefined);
  assert.equal(calls.at(-1).url, "/api/payment-config/alert-providers");
  assert.equal(calls.at(-1).body.providerCode, "PROVIDER-002");

  await paymentConfigApi.toggleAlertProvider({ providerCode: "  PROVIDER-003  " }).catch(() => undefined);
  assert.equal(calls.at(-1).url, "/api/payment-config/alert-providers/toggle");
  assert.equal(calls.at(-1).body.providerCode, "PROVIDER-003");

  await paymentConfigApi.toggleIssueDutyRoster({ rosterCode: "  ROSTER-003  " }).catch(() => undefined);
  assert.equal(calls.at(-1).url, "/api/payment-config/issue-duty-rosters/toggle");
  assert.equal(calls.at(-1).body.rosterCode, "ROSTER-003");

  await paymentConfigApi.submitControlPolicyStatusReview({ sourceAppId: "  APP-004  " }).catch(() => undefined);
  assert.equal(calls.at(-1).url, "/api/payment-config/control-policies/toggle");
  assert.equal(calls.at(-1).body.sourceAppId, "APP-004");

  await paymentConfigApi.submitReturnCodeImportReview({ baselineFingerprint: "  BASELINE-002  " }).catch(() => undefined);
  assert.equal(calls.at(-1).url, "/api/payment-config/return-codes/import/reviews");
  assert.equal(calls.at(-1).body.baselineFingerprint, "BASELINE-002");

  await paymentConfigApi.submitControlPolicyReview({ candidate: { sourceAppId: "  APP-003  " } }).catch(() => undefined);
  assert.equal(calls.at(-1).url, "/api/payment-config/control-policies/reviews");
  assert.equal(calls.at(-1).body.candidate.sourceAppId, "APP-003");
});

test("rejects dead-letter task pages with an invalid row", () => {
  const validRow = {
    taskNo: "DL-001",
    messageId: "MSG-001",
    targetSystem: "ACCOUNTING",
    deadLetterRoutingKey: "payment.dead-letter",
    replayExchange: "payment.exchange",
    replayRoutingKey: "payment.replay",
    taskStatus: "PENDING_REVIEW",
    taskStatusType: "warn",
    replayCount: 2,
    lastReplayAt: null,
    resolvedAt: null,
    createdAt: "2026-09-27 10:00:00",
    updatedAt: "2026-09-27 10:00:00"
  };
  assert.deepEqual(
    normalizePaymentDeadLetterTaskListPage({ items: [validRow], total: 1 }).items,
    [validRow]
  );
  assert.throws(
    () => normalizePaymentDeadLetterTaskListPage({
      items: [{ taskStatus: "PENDING_REVIEW", replayCount: 2 }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DEAD-LETTER-TASK-DETAIL"
  );
});

test("requires dead-letter task detail refreshes to preserve message routing", () => {
  const snapshot = {
    taskNo: "DL-001",
    messageId: "MSG-001",
    correlationId: "CORR-001",
    targetSystem: "ACCOUNTING",
    deadLetterRoutingKey: "payment.dead-letter",
    replayExchange: "payment.exchange",
    replayRoutingKey: "payment.replay"
  };
  assert.equal(matchesPaymentDeadLetterTaskDetailSnapshot(snapshot, { ...snapshot }), true);
  assert.equal(
    matchesPaymentDeadLetterTaskDetailSnapshot(
      snapshot,
      Object.fromEntries(Object.entries(snapshot).map(([key, value]) => [key, ` ${value} `]))
    ),
    true
  );
  assert.equal(
    matchesPaymentDeadLetterTaskDetailSnapshot(snapshot, { ...snapshot, messageId: "MSG-002" }),
    false
  );
  assert.equal(
    matchesPaymentDeadLetterTaskDetailSnapshot(snapshot, { ...snapshot, targetSystem: "SETTLEMENT" }),
    false
  );
  assert.equal(
    matchesPaymentDeadLetterTaskDetailSnapshot(snapshot, { ...snapshot, replayRoutingKey: "payment.other" }),
    false
  );
});

test("rejects dead-letter actions that drift from the loaded message routing snapshot", () => {
  const snapshot = {
    taskNo: "DLQ-001",
    messageId: "MSG-001",
    correlationId: "CORR-001",
    targetSystem: "ACCOUNTING",
    deadLetterRoutingKey: "payment.dead-letter",
    replayExchange: "payment.exchange",
    replayRoutingKey: "payment.replay"
  };
  assert.throws(
    () => normalizePaymentDeadLetterTaskActionResult(
      { ...snapshot, taskStatus: "REPLAYED", replayCount: 1, replayRoutingKey: "payment.other" },
      "DLQ-001",
      snapshot
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DEAD-LETTER-ACTION"
  );
});

test("rejects empty or mismatched payment day-end detail responses", async (context) => {
  const validDetail = {
    batchNo: "BATCH-001",
    bizDate: "2026-09-27",
    runMode: "MANUAL",
    batchStatus: "COMPLETED",
    batchStatusType: "success",
    paymentTotalCount: 10,
    paymentSuccessCount: 8,
    paymentSuccessAmount: "¥80.00",
    channelSuccessCount: 8,
    channelSuccessAmount: "¥80.00",
    internalSuccessCount: 8,
    internalSuccessAmount: "¥80.00",
    paymentSuccessGapCount: 0,
    paymentSuccessGapAmount: "¥0.00",
    refundSuccessCount: 1,
    refundSuccessAmount: "¥10.00",
    channelAbnormalCount: 0,
    internalAbnormalCount: 0,
    pendingRefundCount: 0,
    pendingRefundAmount: "¥0.00",
    reconciliationReadinessStatus: "READY",
    reconciliationReadinessType: "success",
    reconciliationReadinessSummary: "前置事实已收口，可进入正式对账。",
    summaryComment: "日终处理完成",
    triggeredBy: "ops",
    createdAt: "2026-09-27 01:00:00",
    completedAt: "2026-09-27 01:01:00"
  };
  assert.deepEqual(
    normalizePaymentDayEndDetailResult(validDetail, "BATCH-001"),
    validDetail
  );
  assert.equal(
    normalizePaymentDayEndDetailResult({ ...validDetail, batchNo: " BATCH-001 " }, " BATCH-001 ").batchNo,
    " BATCH-001 "
  );
  assert.throws(
    () => normalizePaymentDayEndDetailResult({ batchNo: "BATCH-002" }, "BATCH-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DAY-END-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDayEndDetailResult({ batchNo: "BATCH-001", bizDate: "", batchStatus: "COMPLETED" }, "BATCH-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DAY-END-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDayEndDetailResult({ batchNo: "BATCH-001", bizDate: "2026-09-27", batchStatus: "" }, "BATCH-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DAY-END-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDayEndDetailResult({ batchNo: "BATCH-001", bizDate: "2026-09-27", batchStatus: "UNKNOWN" }, "BATCH-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DAY-END-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDayEndDetailResult({
      batchNo: "BATCH-001",
      bizDate: "2026-09-27",
      batchStatus: "WARNING",
      batchStatusType: "success"
    }, "BATCH-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DAY-END-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDayEndDetailResult({
      ...validDetail,
      completedAt: "2026-09-27 00:59:59"
    }, "BATCH-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DAY-END-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDayEndDetailResult(null, "BATCH-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DAY-END-DETAIL"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  await assert.rejects(
    paymentDayEndApi.getDetail("BATCH-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DAY-END-DETAIL"
  );
});

test("requires payment day-end detail refreshes to preserve the business date", () => {
  const snapshot = { batchNo: "BATCH-001", bizDate: "2026-09-27" };
  assert.equal(matchesPaymentDayEndDetailSnapshot(snapshot, { ...snapshot }), true);
  assert.equal(
    matchesPaymentDayEndDetailSnapshot(snapshot, { batchNo: " BATCH-001 ", bizDate: " 2026-09-27 " }),
    true
  );
  assert.equal(
    matchesPaymentDayEndDetailSnapshot(snapshot, { ...snapshot, batchNo: "BATCH-002" }),
    false
  );
  assert.equal(
    matchesPaymentDayEndDetailSnapshot(snapshot, { ...snapshot, bizDate: "2026-09-28" }),
    false
  );
});

test("rejects channel review actions without the requested review fact", async (context) => {
  assert.deepEqual(
    normalizePaymentConfigReviewActionResult([{
      requestNo: "REQ-001",
      reviewStatus: "APPROVED",
      reviewStatusType: "success"
    }], "REQ-001"),
    [{ requestNo: "REQ-001", reviewStatus: "APPROVED", reviewStatusType: "success" }]
  );
  assert.equal(
    normalizePaymentConfigReviewActionResult([{
      requestNo: " REQ-001 ",
      reviewStatus: "APPROVED",
      reviewStatusType: "success"
    }], " REQ-001 ")[0].requestNo,
    " REQ-001 "
  );
  assert.throws(
    () => normalizePaymentConfigReviewActionResult([{ requestNo: "REQ-002" }], "REQ-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  assert.throws(
    () => normalizePaymentConfigReviewActionResult([{ requestNo: "REQ-001" }], "REQ-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  assert.throws(
    () => normalizePaymentConfigReviewActionResult([{
      requestNo: "REQ-001",
      reviewStatus: "APPROVED",
      reviewStatusType: "invalid"
    }], "REQ-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  assert.throws(
    () => normalizePaymentConfigReviewActionResult([{
      requestNo: "REQ-001",
      reviewStatus: "APPROVED",
      reviewStatusType: "warn"
    }], "REQ-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  assert.throws(
    () => normalizePaymentConfigReviewActionResult([{
      requestNo: "REQ-001",
      reviewStatus: "APPROVED",
      reviewStatusType: "success",
      candidateStatus: "DISABLED",
      candidateStatusType: "success"
    }], "REQ-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  assert.throws(
    () => normalizePaymentConfigReviewActionResult([{
      requestNo: "REQ-001",
      reviewStatus: "APPROVED",
      reviewStatusType: "success",
      createdAt: "2026-09-27 10:00:00",
      reviewedAt: "2026-09-27 09:59:59"
    }], "REQ-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ requestNo: "REQ-002" }] })
  }));

  await assert.rejects(
    paymentConfigApi.approveChannelReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects malformed payment configuration review list rows", async (context) => {
  assert.deepEqual(normalizePaymentConfigReviewListResult([]), []);
  assert.deepEqual(
    normalizePaymentConfigReviewListResult([{
      requestNo: "REQ-001",
      reviewStatus: "PENDING_REVIEW",
      reviewStatusType: "warn",
      createdAt: "2026-09-27 10:00:00",
      reviewedAt: null
    }]),
    [{
      requestNo: "REQ-001",
      reviewStatus: "PENDING_REVIEW",
      reviewStatusType: "warn",
      createdAt: "2026-09-27 10:00:00",
      reviewedAt: null
    }]
  );
  assert.throws(
    () => normalizePaymentConfigReviewListResult([{ requestNo: "REQ-001" }]),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-REVIEWS"
  );
  assert.throws(
    () => normalizePaymentConfigReviewListResult([{
      requestNo: "REQ-001",
      reviewStatus: "PENDING_REVIEW",
      reviewStatusType: "invalid"
    }]),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-REVIEWS"
  );
  assert.throws(
    () => normalizePaymentConfigReviewListResult([{
      requestNo: "REQ-001",
      reviewStatus: "PENDING_REVIEW",
      reviewStatusType: "warn",
      createdAt: "2026-09-27 10:00:00",
      reviewedAt: null,
      certificateStatus: "VALID",
      certificateStatusType: "danger"
    }]),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-REVIEWS"
  );
  assert.throws(
    () => normalizePaymentConfigReviewListResult([{
      requestNo: "REQ-001",
      reviewStatus: "PENDING_REVIEW",
      reviewStatusType: "warn",
      createdAt: "2026-09-27 10:00:00",
      reviewedAt: null,
      candidateStatus: "DISABLED",
      candidateStatusType: "success"
    }]),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-REVIEWS"
  );
  assert.throws(
    () => normalizePaymentConfigReviewListResult([{
      requestNo: "REQ-001",
      reviewStatus: "REJECTED",
      reviewStatusType: "success"
    }]),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-REVIEWS"
  );
  assert.throws(
    () => normalizePaymentConfigReviewListResult([{
      requestNo: "REQ-001",
      reviewStatus: "PENDING_REVIEW",
      reviewStatusType: "warn",
      createdAt: "not-a-datetime"
    }]),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-REVIEWS"
  );
  assert.deepEqual(
    normalizePaymentConfigReviewListResult([{
      requestNo: "REQ-001",
      reviewStatus: "APPROVED",
      reviewStatusType: "success",
      createdAt: "2026-09-27 10:00:00",
      reviewedAt: "2026-09-27 10:05:00"
    }])[0].reviewedAt,
    "2026-09-27 10:05:00"
  );
  assert.throws(
    () => normalizePaymentConfigReviewListResult([{
      requestNo: "REQ-001",
      reviewStatus: "APPROVED",
      reviewStatusType: "success",
      createdAt: "2026-09-27 10:00:00",
      reviewedAt: null
    }]),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-REVIEWS"
  );
  assert.throws(
    () => normalizePaymentConfigReviewListResult([{
      requestNo: "REQ-001",
      reviewStatus: "APPROVED",
      reviewStatusType: "success",
      createdAt: "2026-09-27 10:00:00",
      reviewedAt: "2026-09-27 09:59:59"
    }]),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-REVIEWS"
  );
  assert.throws(
    () => normalizePaymentConfigReviewListResult([{
      requestNo: "REQ-001",
      reviewStatus: "PENDING_REVIEW",
      reviewStatusType: "warn",
      createdAt: "2026-09-27 10:00:00",
      reviewedAt: "2026-09-27 10:05:00"
    }]),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-REVIEWS"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ requestNo: "REQ-001" }] })
  }));

  await assert.rejects(
    paymentConfigApi.getChannelReviews(),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-REVIEWS"
  );
});

test("normalizes route-rule conflict facts before the admin page consumes them", async (context) => {
  const validConflict = {
    conflictType: "PRIORITY_COLLISION",
    severity: "BLOCKER",
    severityType: "danger",
    ruleCode: "RULE-001",
    ruleName: "主渠道",
    conflictingRuleCode: "RULE-002",
    conflictingRuleName: "备用渠道",
    priority: 10,
    conflictingPriority: 10,
    overlappingScene: "HOME_CLEAN",
    message: "规则存在优先级冲突"
  };
  assert.deepEqual(normalizePaymentRouteRuleConflictsResult([]), []);
  assert.deepEqual(normalizePaymentRouteRuleConflictsResult([validConflict]), [validConflict]);
  for (const invalidConflict of [
    { ...validConflict, severityType: "warn" },
    { ...validConflict, conflictingRuleName: undefined },
    { ...validConflict, priority: -1 },
    { ...validConflict, message: "" }
  ]) {
    assert.throws(
      () => normalizePaymentRouteRuleConflictsResult([invalidConflict]),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-ROUTE-RULE-CONFLICTS"
    );
  }
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ ...validConflict, severityType: "invalid" }] })
  }));
  await assert.rejects(
    paymentConfigApi.getRouteRuleConflicts(),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ROUTE-RULE-CONFLICTS"
  );
});

test("rejects configuration review actions containing malformed sibling records", () => {
  assert.throws(
    () => normalizePaymentConfigReviewActionResult([
      { requestNo: "REQ-001", reviewStatus: "APPROVED", reviewStatusType: "success" },
      { requestNo: "REQ-002" }
    ], "REQ-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  assert.throws(
    () => normalizePaymentConfigReviewActionResult([
      { requestNo: "REQ-001", reviewStatus: "APPROVED", reviewStatusType: "success" },
      { requestNo: "REQ-002", reviewStatus: "", reviewStatusType: "success" }
    ], "REQ-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  assert.throws(
    () => normalizePaymentConfigReviewActionResult([{
      requestNo: "REQ-001",
      reviewStatus: "APPROVED",
      reviewStatusType: "success",
      certificateStatus: "EXPIRED",
      certificateStatusType: "success"
    }], "REQ-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects malformed sibling records across payment configuration actions", () => {
  const cases = [
    {
      valid: { channelCode: "CHANNEL-001" },
      validate: (data) => normalizePaymentConfigChannelRollbackResult(data, "CHANNEL-001")
    },
    {
      valid: { gatewayCode: "GATEWAY-001" },
      validate: (data) => normalizePaymentConfigGatewayReviewSubmitResult(data, "GATEWAY-001")
    },
    {
      valid: { protocolCode: "PROTOCOL-001" },
      validate: (data) => normalizePaymentConfigProtocolReviewApplicationResult(data, "PROTOCOL-001")
    },
    {
      valid: { sourceAppId: "APP-001" },
      validate: (data) => normalizePaymentConfigControlPolicyReviewSubmitResult(data, "APP-001")
    },
    {
      valid: { providerCode: "PROVIDER-001" },
      validate: (data) => normalizePaymentConfigAlertProviderReviewUpdateResult(data, "PROVIDER-001")
    },
    {
      valid: { rosterCode: "ROSTER-001" },
      validate: (data) => normalizePaymentConfigIssueDutyRosterReviewApplicationResult(data, "ROSTER-001")
    },
    {
      valid: { channelCode: "CHANNEL-001", channelReturnCode: "SUCCESS" },
      validate: (data) => normalizePaymentConfigReturnCodeReviewSubmitResult(
        data, "CHANNEL-001", "SUCCESS"
      )
    },
    {
      valid: { baselineFingerprint: "BASELINE-001" },
      validate: (data) => normalizePaymentConfigReturnCodeImportReviewSubmitResult(
        data, "BASELINE-001"
      )
    },
    {
      valid: { ruleCode: "RULE-001" },
      validate: (data) => normalizePaymentConfigRouteRuleReviewSubmitResult(data, "RULE-001")
    }
  ];

  for (const { valid, validate } of cases) {
    assert.deepEqual(validate([valid]), [valid]);
    assert.throws(
      () => validate([valid, { ...valid, ...Object.fromEntries(Object.keys(valid).map((key) => [key, ""])) }]),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
    );
  }
});

test("rejects channel rollback actions without the requested channel fact", async (context) => {
  assert.deepEqual(
    normalizePaymentConfigChannelRollbackResult(
      [{ channelCode: "wx_h5", requestNo: "REQ-001" }],
      "wx_h5"
    ),
    [{ channelCode: "wx_h5", requestNo: "REQ-001" }]
  );
  assert.throws(
    () => normalizePaymentConfigChannelRollbackResult(
      [{ channelCode: "alipay", requestNo: "REQ-002" }],
      "wx_h5"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  assert.throws(
    () => normalizePaymentConfigChannelRollbackResult(
      [{ channelCode: "wx_h5", reviewStatus: "APPROVED", reviewStatusType: "warn" }],
      "wx_h5"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  assert.throws(
    () => normalizePaymentConfigChannelRollbackResult(
      [{ channelCode: "wx_h5", candidateStatus: "DISABLED", candidateStatusType: "success" }],
      "wx_h5"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ channelCode: "alipay" }] })
  }));

  await assert.rejects(
    paymentConfigApi.rollbackChannel("wx_h5", { versionNo: 1 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects gateway rollback actions without the requested gateway fact", async (context) => {
  assert.deepEqual(
    normalizePaymentConfigGatewayRollbackResult(
      [{ gatewayCode: "gateway_default", requestNo: "REQ-001" }],
      "gateway_default"
    ),
    [{ gatewayCode: "gateway_default", requestNo: "REQ-001" }]
  );
  assert.throws(
    () => normalizePaymentConfigGatewayRollbackResult(
      [{ gatewayCode: "gateway_other", requestNo: "REQ-002" }],
      "gateway_default"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ gatewayCode: "gateway_other" }] })
  }));

  await assert.rejects(
    paymentConfigApi.rollbackGateway("gateway_default", { versionNo: 1 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects gateway configuration actions with an inconsistent certificate fact", () => {
  assert.throws(
    () => normalizePaymentConfigGatewayReviewSubmitResult([{
      gatewayCode: "GATEWAY-001",
      certificateStatus: "EXPIRED",
      certificateStatusType: "success"
    }], "GATEWAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects protocol rollback actions without the requested protocol fact", async (context) => {
  assert.deepEqual(
    normalizePaymentConfigProtocolRollbackResult(
      [{ protocolCode: "protocol_default", requestNo: "REQ-001" }],
      "protocol_default"
    ),
    [{ protocolCode: "protocol_default", requestNo: "REQ-001" }]
  );
  assert.throws(
    () => normalizePaymentConfigProtocolRollbackResult(
      [{ protocolCode: "protocol_other", requestNo: "REQ-002" }],
      "protocol_default"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ protocolCode: "protocol_other" }] })
  }));

  await assert.rejects(
    paymentConfigApi.rollbackProtocol("protocol_default", { versionNo: 1 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects control-policy rollback actions without the requested policy fact", async (context) => {
  assert.deepEqual(
    normalizePaymentConfigControlPolicyRollbackResult(
      [{ sourceAppId: "APP-001", requestNo: "REQ-001" }],
      "APP-001"
    ),
    [{ sourceAppId: "APP-001", requestNo: "REQ-001" }]
  );
  assert.throws(
    () => normalizePaymentConfigControlPolicyRollbackResult(
      [{ sourceAppId: "APP-002", requestNo: "REQ-002" }],
      "APP-001"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ sourceAppId: "APP-002" }] })
  }));

  await assert.rejects(
    paymentConfigApi.rollbackControlPolicy("APP-001", { versionNo: 1 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects route-rule rollback actions without the requested rule fact", async (context) => {
  assert.deepEqual(
    normalizePaymentConfigRouteRuleRollbackResult(
      [{ ruleCode: "RULE-001", requestNo: "REQ-001" }],
      "RULE-001"
    ),
    [{ ruleCode: "RULE-001", requestNo: "REQ-001" }]
  );
  assert.throws(
    () => normalizePaymentConfigRouteRuleRollbackResult(
      [{ ruleCode: "RULE-002", requestNo: "REQ-002" }],
      "RULE-001"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ ruleCode: "RULE-002" }] })
  }));

  await assert.rejects(
    paymentConfigApi.submitRouteRuleRollbackReview("RULE-001", { versionNo: 1 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects route-rule review submissions without the requested rule fact", async (context) => {
  assert.deepEqual(
    normalizePaymentConfigRouteRuleReviewSubmitResult(
      [{ ruleCode: "RULE-001", requestNo: "REQ-001" }],
      "RULE-001"
    ),
    [{ ruleCode: "RULE-001", requestNo: "REQ-001" }]
  );
  assert.throws(
    () => normalizePaymentConfigRouteRuleReviewSubmitResult(
      [{ ruleCode: "RULE-002", requestNo: "REQ-002" }],
      "RULE-001"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ ruleCode: "RULE-002" }] })
  }));

  await assert.rejects(
    paymentConfigApi.submitRouteRuleReview({ candidate: { ruleCode: "RULE-001" } }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects route-rule status submissions without the requested rule fact", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ ruleCode: "RULE-002" }] })
  }));

  await assert.rejects(
    paymentConfigApi.submitRouteRuleStatusReview({ ruleCode: "RULE-001", enabled: true }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects channel status submissions without the requested channel fact", async (context) => {
  assert.deepEqual(
    normalizePaymentConfigChannelReviewSubmitResult(
      [{ channelCode: "wx_h5", requestNo: "REQ-001" }],
      "wx_h5"
    ),
    [{ channelCode: "wx_h5", requestNo: "REQ-001" }]
  );
  assert.deepEqual(
    normalizePaymentConfigChannelReviewSubmitResult(
      [{ channelCode: " wx_h5 ", requestNo: "REQ-001" }],
      " wx_h5 "
    ),
    [{ channelCode: " wx_h5 ", requestNo: "REQ-001" }]
  );
  assert.throws(
    () => normalizePaymentConfigChannelReviewSubmitResult(
      [{ channelCode: "alipay", requestNo: "REQ-002" }],
      "wx_h5"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ channelCode: "alipay" }] })
  }));

  await assert.rejects(
    paymentConfigApi.submitChannelStatusReview({ channelCode: "wx_h5" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects channel review submissions without the requested channel fact", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ channelCode: "alipay" }] })
  }));

  await assert.rejects(
    paymentConfigApi.submitChannelReview({ candidate: { channelCode: "wx_h5" } }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects gateway status submissions without the requested gateway fact", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ gatewayCode: "gateway_other" }] })
  }));

  await assert.rejects(
    paymentConfigApi.submitGatewayStatusReview({ gatewayCode: "gateway_default" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects protocol status submissions without the requested protocol fact", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ protocolCode: "protocol_other" }] })
  }));

  await assert.rejects(
    paymentConfigApi.toggleProtocol({ protocolCode: "protocol_default" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects control-policy status submissions without the requested policy fact", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ sourceAppId: "APP-002" }] })
  }));

  await assert.rejects(
    paymentConfigApi.submitControlPolicyStatusReview({ sourceAppId: "APP-001" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects protocol review submissions without the requested protocol fact", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ protocolCode: "protocol_other" }] })
  }));

  await assert.rejects(
    paymentConfigApi.submitProtocolReview({ candidate: { protocolCode: "protocol_default" } }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects gateway review submissions without the requested gateway fact", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ gatewayCode: "gateway_other" }] })
  }));

  await assert.rejects(
    paymentConfigApi.submitGatewayReview({ candidate: { gatewayCode: "gateway_default" } }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects control-policy review submissions without the requested policy fact", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ sourceAppId: "APP-002" }] })
  }));

  await assert.rejects(
    paymentConfigApi.submitControlPolicyReview({ candidate: { sourceAppId: "APP-001" } }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects alert-provider status submissions without the requested provider fact", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ providerCode: "provider_other" }] })
  }));

  await assert.rejects(
    paymentConfigApi.toggleAlertProvider({ providerCode: "provider_default" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects issue-duty roster status submissions without the requested roster fact", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ rosterCode: "roster_other" }] })
  }));

  await assert.rejects(
    paymentConfigApi.toggleIssueDutyRoster({ rosterCode: "roster_default" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects alert-provider creation without the requested provider fact", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ providerCode: "provider_other" }] })
  }));

  await assert.rejects(
    paymentConfigApi.createAlertProvider({ providerCode: "provider_default" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects issue-duty roster creation without the requested roster fact", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ rosterCode: "roster_other" }] })
  }));

  await assert.rejects(
    paymentConfigApi.createIssueDutyRoster({ rosterCode: "roster_default" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects return-code status submissions without the requested return-code fact", async (context) => {
  assert.deepEqual(
    normalizePaymentConfigReturnCodeReviewSubmitResult(
      [{ channelCode: "wx_h5", channelReturnCode: "SUCCESS" }],
      "wx_h5",
      "SUCCESS"
    ),
    [{ channelCode: "wx_h5", channelReturnCode: "SUCCESS" }]
  );
  assert.throws(
    () => normalizePaymentConfigReturnCodeReviewSubmitResult(
      [{ channelCode: "alipay", channelReturnCode: "SUCCESS" }],
      "wx_h5",
      "SUCCESS"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ channelCode: "wx_h5", channelReturnCode: "FAIL" }] })
  }));

  await assert.rejects(
    paymentConfigApi.submitReturnCodeStatusReview({ channelCode: "wx_h5", channelReturnCode: "SUCCESS" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects return-code import submissions without the requested baseline fact", async (context) => {
  assert.deepEqual(
    normalizePaymentConfigReturnCodeImportReviewSubmitResult(
      [{ baselineFingerprint: "BASELINE-001", requestNo: "REQ-001" }],
      "BASELINE-001"
    ),
    [{ baselineFingerprint: "BASELINE-001", requestNo: "REQ-001" }]
  );
  assert.throws(
    () => normalizePaymentConfigReturnCodeImportReviewSubmitResult(
      [{ baselineFingerprint: "BASELINE-002" }],
      "BASELINE-001"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ baselineFingerprint: "BASELINE-002" }] })
  }));

  await assert.rejects(
    paymentConfigApi.submitReturnCodeImportReview({ baselineFingerprint: "BASELINE-001" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects route-rule review actions without the requested review fact", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ requestNo: "REQ-002" }] })
  }));

  await assert.rejects(
    paymentConfigApi.approveRouteRuleReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.rejectRouteRuleReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects protocol review actions without the requested review fact", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ requestNo: "REQ-002" }] })
  }));

  await assert.rejects(
    paymentConfigApi.approveProtocolReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.rejectProtocolReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects control-policy review actions without the requested review fact", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ requestNo: "REQ-002" }] })
  }));

  await assert.rejects(
    paymentConfigApi.approveControlPolicyReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.rejectControlPolicyReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects control-policy self-check overviews without the requested policy", async (context) => {
  const validOverview = {
    channels: [],
    routeRules: [],
    protocols: [],
    protocolTypeOptions: [],
    returnCodeMappings: [],
    gateways: [],
    controlPolicies: [{ sourceAppId: "APP-001", status: "ENABLED", statusType: "success", selfCheckStatus: "PASS", selfCheckStatusType: "success", updatedAt: "2026-09-27 10:00:00" }],
    alertProviders: [],
    issueDutyRosters: []
  };
  assert.deepEqual(
    normalizeControlPolicySelfCheckResult(validOverview, "APP-001"),
    validOverview
  );
  assert.throws(
    () => normalizeControlPolicySelfCheckResult({
      ...validOverview,
      controlPolicies: [{ sourceAppId: "APP-002", status: "ENABLED", statusType: "success", selfCheckStatus: "PASS", selfCheckStatusType: "success", updatedAt: "2026-09-27 10:00:00" }]
    }, "APP-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONTROL-POLICY-SELF-CHECK"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({
      code: "0",
      data: {
        ...validOverview,
        controlPolicies: [{ sourceAppId: "APP-002", status: "ENABLED", statusType: "success" }]
      }
    })
  }));

  await assert.rejects(
    paymentConfigApi.runControlPolicySelfCheck("APP-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONTROL-POLICY-SELF-CHECK"
  );
});

test("rejects alert-provider review actions without the requested review fact", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ requestNo: "REQ-002" }] })
  }));

  await assert.rejects(
    paymentConfigApi.approveAlertProviderReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.rejectAlertProviderReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects return-code review actions without the requested review fact", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ requestNo: "REQ-002" }] })
  }));

  await assert.rejects(
    paymentConfigApi.approveReturnCodeReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.rejectReturnCodeReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects return-code import review actions without the requested review fact", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ requestNo: "REQ-002" }] })
  }));

  await assert.rejects(
    paymentConfigApi.approveReturnCodeImportReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.rejectReturnCodeImportReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects gateway review actions without the requested review fact", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ requestNo: "REQ-002" }] })
  }));

  await assert.rejects(
    paymentConfigApi.approveGatewayReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.rejectGatewayReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects issue-duty roster review actions without the requested review fact", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: [{ requestNo: "REQ-002" }] })
  }));

  await assert.rejects(
    paymentConfigApi.approveIssueDutyRosterReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.rejectIssueDutyRosterReview("REQ-001", { reviewer: "ops" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
});

test("rejects incomplete or mismatched admin prepay responses", async (context) => {
  assert.deepEqual(
    normalizePaymentPrepayResult({
      prepayOrderNo: "PRE-001",
      paymentOrderId: "PAY-001",
      businessChainId: "CHAIN-PAY-001",
      orderNo: "ORD-001",
      amount: "68.00",
      paymentStatus: "PREPAY_CREATED",
      cashierStatus: "待支付",
      cashierStatusType: "warn",
      createdAt: "2026-09-27 11:59:00",
      expiresAt: "2026-09-27 12:00:00"
    }, "ORD-001"),
    {
      prepayOrderNo: "PRE-001",
      paymentOrderId: "PAY-001",
      businessChainId: "CHAIN-PAY-001",
      orderNo: "ORD-001",
      amount: "68.00",
      paymentStatus: "PREPAY_CREATED",
      cashierStatus: "待支付",
      cashierStatusType: "warn",
      createdAt: "2026-09-27 11:59:00",
      expiresAt: "2026-09-27 12:00:00"
    }
  );
  assert.equal(
    normalizePaymentPrepayResult(
      {
        prepayOrderNo: "PRE-001",
        paymentOrderId: "PAY-001",
        businessChainId: "CHAIN-PAY-001",
        orderNo: "ORD-001",
        amount: "¥68.00",
        paymentStatus: "PREPAY_CREATED",
        cashierStatus: "待支付",
        cashierStatusType: "warn",
        createdAt: "2026-09-27 11:59:00",
        expiresAt: "2026-09-27 12:00:00"
      },
      "ORD-001",
      "68"
    ).amount,
    "¥68.00"
  );
  assert.equal(
    normalizePaymentPrepayResult({
      prepayOrderNo: "PRE-001",
      paymentOrderId: "PAY-001",
      businessChainId: "CHAIN-PAY-001",
      orderNo: " ORD-001 ",
      amount: "68.00",
      paymentStatus: "PREPAY_CREATED",
      cashierStatus: "待支付",
      cashierStatusType: "warn",
      createdAt: "2026-09-27 11:59:00",
      expiresAt: "2026-09-27 12:00:00"
    }, " ORD-001 ").orderNo,
    " ORD-001 "
  );
  assert.throws(
    () => normalizePaymentPrepayResult({ prepayOrderNo: "PRE-001", orderNo: "ORD-001" }, "ORD-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePaymentPrepayResult({ prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", amount: "68.00" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePaymentPrepayResult({ prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", orderNo: "ORD-002" }, "ORD-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePaymentPrepayResult({ prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", orderNo: "ORD-001" }, "ORD-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePaymentPrepayResult(
      {
        prepayOrderNo: "PRE-001",
        paymentOrderId: "PAY-001",
        orderNo: "ORD-001",
        amount: "68.00",
        createdAt: "2026-09-27 12:00:00",
        expiresAt: "2026-09-27 11:59:00"
      },
      "ORD-001"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
  );
  const validPrepay = {
    prepayOrderNo: "PRE-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORD-001",
    payScene: "HOME_CLEAN",
    amount: "68.00",
    paymentStatus: "PREPAY_CREATED",
    cashierStatus: "待支付",
    cashierStatusType: "warn",
    createdAt: "2026-09-27 11:59:00",
    expiresAt: "2026-09-27 12:00:00"
  };
  const failedPrepay = {
    ...validPrepay,
    paymentStatus: "FAIL",
    cashierStatus: "结果待确认",
    cashierStatusType: "danger",
    latestAttemptStatus: "FAILED",
    latestAttemptStatusType: "danger",
    latestAttemptCompletedAt: null
  };
  assert.doesNotThrow(() => normalizePaymentPrepayResult(failedPrepay, "ORD-001"));
  for (const attemptFacts of [
    { latestAttemptStatus: "FAILED", latestAttemptStatusType: "success" },
    { latestAttemptStatus: "SUCCESS", latestAttemptStatusType: "success" }
  ]) {
    assert.throws(
      () => normalizePaymentPrepayResult({ ...failedPrepay, ...attemptFacts }, "ORD-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
    );
  }
  assert.throws(
    () => normalizePaymentPrepayResult({ ...validPrepay, latestAttemptStatus: "FAILED",
      latestAttemptStatusType: "danger", latestAttemptCompletedAt: null }, "ORD-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
  );
  assert.equal(
    normalizePaymentPrepayResult(validPrepay, "ORD-001", "68.00", null, "HOME_CLEAN").payScene,
    "HOME_CLEAN"
  );
  assert.throws(
    () => normalizePaymentPrepayResult({ ...validPrepay, payScene: undefined }, "ORD-001", "68.00", null, "HOME_CLEAN"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePaymentPrepayResult({ ...validPrepay, payScene: "HOME_REPAIR" }, "ORD-001", "68.00", null, "HOME_CLEAN"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
  );
  for (const invalidAttemptFacts of [
    { latestAttemptStatus: "处理中", latestAttemptStatusType: "invalid", latestAttemptCompletedAt: null },
    { latestAttemptStatus: "处理中", latestAttemptStatusType: "info", latestAttemptCompletedAt: "2026-09-27 12:00:00" },
    { latestAttemptStatus: "成功", latestAttemptStatusType: "success", latestAttemptCompletedAt: "2026-09-27 12:00:00" }
  ]) {
    assert.throws(
      () => normalizePaymentPrepayResult({ ...validPrepay, ...invalidAttemptFacts }, "ORD-001", "68.00"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
    );
  }
  for (const invalidPrepay of [
    { ...validPrepay, businessChainId: undefined },
    { ...validPrepay, businessChainId: "CHAIN-OTHER" },
    { ...validPrepay, paymentStatus: "UNKNOWN" },
    { ...validPrepay, cashierStatusType: "success" },
    { ...validPrepay, paymentStatus: "WAIT_CALLBACK", cashierStatus: "支付成功", cashierStatusType: "success" },
    { ...validPrepay, paymentStatus: "PAYING", cashierStatus: "待支付", cashierStatusType: "warn" },
    { ...validPrepay, paymentStatus: "SUCCESS", cashierStatus: "待支付" },
    { ...validPrepay, paymentStatus: "SUCCESS", cashierStatus: "支付成功", cashierStatusType: "success" },
    { ...validPrepay, paymentStatus: "SUCCESS", cashierStatus: "支付成功", cashierStatusType: "success", successAt: undefined },
    { ...validPrepay, paymentStatus: "SUCCESS", cashierStatus: "支付成功", cashierStatusType: "success", successAt: "2026-09-27 11:58:00" }
  ]) {
    assert.throws(
      () => normalizePaymentPrepayResult(invalidPrepay, "ORD-001", "68.00"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
    );
  }
  assert.doesNotThrow(() => normalizePaymentPrepayResult({
    ...validPrepay,
    paymentStatus: "SUCCESS",
    cashierStatus: "支付成功",
    cashierStatusType: "success",
    successAt: "2026-09-27 12:00:00",
    latestAttemptStatus: "成功",
    latestAttemptStatusType: "success",
    latestAttemptCompletedAt: "2026-09-27 11:59:30",
    channelTransactionNo: "WX-001"
  }, "ORD-001", "68.00"));
  assert.throws(
    () => normalizePaymentPrepayResult(
      { prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", orderNo: "ORD-001", amount: "67.99" },
      "ORD-001",
      "68.00"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePaymentPrepayResult(
      {
        prepayOrderNo: "PRE-001",
        paymentOrderId: "PAY-001",
        orderNo: "ORD-001",
        amount: "9007199254740992.02"
      },
      "ORD-001",
      "9007199254740992.01"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: { prepayOrderNo: "PRE-001" } })
  }));

  await assert.rejects(
    paymentApi.prepay({ orderNo: "ORD-001", payScene: "HOME_CLEAN" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
  );
});

test("binds admin prepay responses to the requested customer name", async (context) => {
  let requestBody;
  context.mock.method(globalThis, "fetch", async (url, options) => {
    assert.equal(url, "/api/payments/prepay");
    requestBody = JSON.parse(options.body);
    return {
      ok: true,
      json: async () => ({
        code: "0",
        data: {
          prepayOrderNo: "PRE-001",
          paymentOrderId: "PAY-001",
          businessChainId: "CHAIN-PAY-001",
          orderNo: "ORD-001",
          customerName: "客户一",
          payScene: "HOME_CLEAN",
          amount: "68.00",
          paymentStatus: "PREPAY_CREATED",
          cashierStatus: "待支付",
          cashierStatusType: "warn",
          createdAt: "2026-09-27 11:59:00",
          expiresAt: "2026-09-27 12:00:00"
        }
      })
    };
  });

  await paymentApi.prepay({
    orderNo: " ORD-001 ",
    customerName: " 客户一 ",
    amount: "68.00",
    payScene: "HOME_CLEAN"
  });
  assert.deepEqual(requestBody, {
    orderNo: "ORD-001",
    customerName: "客户一",
    amount: "68.00",
    payScene: "HOME_CLEAN"
  });
  assert.throws(
    () => normalizePaymentPrepayResult(
      {
        prepayOrderNo: "PRE-001",
        paymentOrderId: "PAY-001",
        businessChainId: "CHAIN-PAY-001",
        orderNo: "ORD-001",
        customerName: "客户二",
        amount: "68.00",
        paymentStatus: "PREPAY_CREATED",
        cashierStatus: "待支付",
        cashierStatusType: "warn",
        createdAt: "2026-09-27 11:59:00",
        expiresAt: "2026-09-27 12:00:00"
      },
      "ORD-001",
      "68.00",
      "客户一"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
  );
});

test("filters null rows from paged list responses", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({
      code: "0",
      data: { items: [null, { ...validOrderListRow, orderNo: "ORD-003" }], total: 2, pageNo: 1, pageSize: 20 }
    })
  }));

  assert.deepEqual(await orderApi.getList(), {
    items: [{ ...validOrderListRow, orderNo: "ORD-003" }],
    total: 2,
    pageNo: 1,
    pageSize: 20
  });
});

test("preserves null detail responses", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  await assert.rejects(
    dashboardApi.getSummary(),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DASHBOARD-SUMMARY"
  );
});

test("requires structured dashboard cards while allowing an empty card list", () => {
  const summary = { cards: [] };
  assert.deepEqual(normalizeDashboardSummaryResult(summary), summary);
  assert.throws(
    () => normalizeDashboardSummaryResult({ cards: [{ key: "payment", title: "支付", value: "-" }] }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DASHBOARD-SUMMARY"
  );
});

test("normalizes nullable payment configuration collections", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  assert.deepEqual(await paymentConfigApi.getChannelReviews(), []);
  await assert.rejects(
    paymentConfigApi.submitChannelStatusReview({}),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-ACTION"
  );
  await assert.rejects(
    paymentConfigApi.getOverview(),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-OVERVIEW"
  );
});

test("rejects malformed object payloads for collection endpoints", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: { requestNo: "REQ-001" } })
  }));

  await assert.rejects(
    paymentConfigApi.getChannelReviews(),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-COLLECTION"
  );
});

test("requires a complete payment configuration overview contract", () => {
  const overview = {
    channels: [{ channelCode: "CHANNEL-001", status: "ENABLED", statusType: "success", updatedAt: "2026-09-27 10:00:00" }],
    routeRules: [{ ruleCode: "RULE-001", status: "ENABLED", statusType: "success", updatedAt: "2026-09-27 10:00:00" }],
    protocols: [{ protocolCode: "PROTOCOL-001", status: "ENABLED", statusType: "success", updatedAt: "2026-09-27 10:00:00" }],
    protocolTypeOptions: [{ protocolType: "DIRECT", protocolTypeName: "直连" }],
    returnCodeMappings: [{ channelCode: "CHANNEL-001", channelReturnCode: "SUCCESS", status: "ACTIVE", statusType: "success", updatedAt: "2026-09-27 10:00:00" }],
    gateways: [{ gatewayCode: "GATEWAY-001", status: "ENABLED", statusType: "success", certificateStatus: "VALID", certificateStatusType: "success", updatedAt: "2026-09-27 10:00:00" }],
    controlPolicies: [{ sourceAppId: "APP-001", status: "ENABLED", statusType: "success", selfCheckStatus: "PASS", selfCheckStatusType: "success", updatedAt: "2026-09-27 10:00:00" }],
    alertProviders: [{ providerCode: "PROVIDER-001", status: "ENABLED", statusType: "success", updatedAt: "2026-09-27 10:00:00" }],
    issueDutyRosters: [{ rosterCode: "ROSTER-001", status: "ENABLED", statusType: "success", updatedAt: "2026-09-27 10:00:00" }]
  };

  assert.deepEqual(normalizePaymentConfigOverviewResult(overview), overview);
  assert.throws(
    () => normalizePaymentConfigOverviewResult({ ...overview, gateways: [{ gatewayCode: "GATEWAY-001" }] }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentConfigOverviewResult({
      ...overview,
      returnCodeMappings: [{ channelCode: "CHANNEL-001", status: "ACTIVE" }]
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentConfigOverviewResult({
      ...overview,
      channels: [{ channelCode: "CHANNEL-001", status: "ENABLED", statusType: "invalid" }]
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentConfigOverviewResult({
      ...overview,
      channels: [{ channelCode: "CHANNEL-001", status: "UNKNOWN", statusType: "success" }]
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentConfigOverviewResult({
      ...overview,
      gateways: [{ gatewayCode: "GATEWAY-001", status: "DISABLED", statusType: "success" }]
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentConfigOverviewResult({
      ...overview,
      gateways: [{ ...overview.gateways[0], certificateStatusType: "danger" }]
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentConfigOverviewResult({
      ...overview,
      controlPolicies: [{ ...overview.controlPolicies[0], selfCheckStatusType: "danger" }]
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentConfigOverviewResult({
      ...overview,
      channels: [{ ...overview.channels[0], updatedAt: "not-a-datetime" }]
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-OVERVIEW"
  );
  assert.deepEqual(
    normalizePaymentConfigOverviewResult({ ...overview, protocolTypeOptions: [] }).protocolTypeOptions,
    []
  );
});

test("requires a structured payment monitor overview", () => {
  const overview = {
    summary: {
      totalCount: 10,
      successCount: 8,
      successRate: "80.00%",
      successAmount: "¥80.00",
      pendingPaymentCount: 1,
      closedPaymentCount: 1,
      failedRefundCount: 0,
      processingRefundCount: 0,
      disabledChannelCount: 0,
      alertCount: 0
    },
    trends: [],
    channelMetrics: [],
    alerts: [],
    metricDefinitions: []
  };

  assert.deepEqual(normalizePaymentMonitorOverviewResult(overview), overview);
  const validAlert = {
    alertType: "WAIT_CALLBACK_TIMEOUT",
    alertTitle: "支付回调待收口",
    alertMessage: "存在待处理支付单",
    alertLevel: "P1",
    alertLevelType: "danger",
    affectedCount: 1,
    suggestedAction: "主动查单",
    actionRoute: "/payment-issues"
  };
  assert.deepEqual(
    normalizePaymentMonitorOverviewResult({
      ...overview,
      summary: { ...overview.summary, alertCount: 1 },
      alerts: [validAlert]
    }).alerts,
    [validAlert]
  );
  for (const invalidAlert of [
    { ...validAlert, alertLevel: "P3", alertLevelType: "success" },
    { ...validAlert, affectedCount: 0 },
    { ...validAlert, affectedCount: 0, alertLevel: "P2", alertLevelType: "warn" },
    { ...validAlert, alertLevel: "P4" },
    { ...validAlert, alertLevelType: "notice" }
  ]) {
    assert.throws(
      () => normalizePaymentMonitorOverviewResult({
        ...overview,
        summary: { ...overview.summary, alertCount: 1 },
        alerts: [invalidAlert]
      }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-MONITOR-OVERVIEW"
    );
  }
  assert.throws(
    () => normalizePaymentMonitorOverviewResult({ ...overview, summary: { totalCount: -1 } }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-MONITOR-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentMonitorOverviewResult({
      ...overview,
      summary: { ...overview.summary, pendingPaymentCount: undefined }
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-MONITOR-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentMonitorOverviewResult({
      ...overview,
      summary: { ...overview.summary, alertCount: 1 }
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-MONITOR-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentMonitorOverviewResult({
      ...overview,
      summary: { ...overview.summary, successAmount: "6.8e1" }
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-MONITOR-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentMonitorOverviewResult({
      ...overview,
      summary: { ...overview.summary, successRate: "101.00%" }
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-MONITOR-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentMonitorOverviewResult({
      ...overview,
      summary: { ...overview.summary, successRate: "79.99%" }
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-MONITOR-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentMonitorOverviewResult({
      ...overview,
      channelMetrics: [{
        channelCode: "WX_H5",
        paymentMethod: "微信支付",
        totalCount: 10,
        successCount: 8,
        pendingCount: 1,
        unacceptedCount: 0,
        successRate: "79.99%",
        successAmount: "¥80.00",
        riskLevel: "正常",
        riskLevelType: "success",
        riskHint: "正常"
      }]
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-MONITOR-OVERVIEW"
  );
  const channelMetric = {
    channelCode: "WX_H5",
    paymentMethod: "微信支付",
    totalCount: 10,
    successCount: 8,
    pendingCount: 1,
    unacceptedCount: 0,
    successRate: "80.00%",
    successAmount: "¥80.00",
    riskLevel: "P1",
    riskLevelType: "danger",
    riskHint: "存在待收口支付单"
  };
  assert.deepEqual(
    normalizePaymentMonitorOverviewResult({ ...overview, channelMetrics: [channelMetric] }).channelMetrics,
    [channelMetric]
  );
  assert.deepEqual(
    normalizePaymentMonitorOverviewResult({
      ...overview,
      channelMetrics: [{ ...channelMetric, pendingCount: 0, unacceptedCount: 1, riskLevel: "P2", riskLevelType: "warn" }]
    }).channelMetrics[0].unacceptedCount,
    1
  );
  for (const unacceptedCount of [undefined, -1, 10]) {
    assert.throws(
      () => normalizePaymentMonitorOverviewResult({
        ...overview,
        channelMetrics: [{ ...channelMetric, unacceptedCount }]
      }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-MONITOR-OVERVIEW"
    );
  }
  assert.deepEqual(
    normalizePaymentMonitorOverviewResult({
      ...overview,
      channelMetrics: [{ ...channelMetric, riskLevel: "P2", riskLevelType: "warn" }]
    }).channelMetrics[0].riskLevel,
    "P2"
  );
  for (const invalidRisk of [
    { riskLevel: "P1", riskLevelType: "success" },
    { riskLevel: "P2", riskLevelType: "danger" },
    { riskLevel: "UNKNOWN", riskLevelType: "warn" },
    { riskLevel: "正常", riskLevelType: "warn" }
  ]) {
    assert.throws(
      () => normalizePaymentMonitorOverviewResult({
        ...overview,
        channelMetrics: [{ ...channelMetric, ...invalidRisk }]
      }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-MONITOR-OVERVIEW"
    );
  }
  assert.throws(
    () => normalizePaymentMonitorOverviewResult({
      ...overview,
      channelMetrics: [channelMetric, { ...channelMetric }]
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-MONITOR-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentMonitorOverviewResult({
      ...overview,
      channelMetrics: [channelMetric, { ...channelMetric, paymentMethod: " 微信支付 " }]
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-MONITOR-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentMonitorOverviewResult({
      ...overview,
      channelMetrics: [{
        channelCode: "WX_H5",
        paymentMethod: "微信支付",
        totalCount: 1,
        successCount: 2,
        pendingCount: 0,
        unacceptedCount: 0,
        successRate: "200.00%",
        successAmount: "¥80.00",
        riskLevel: "正常",
        riskLevelType: "success",
        riskHint: "正常"
      }]
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-MONITOR-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentMonitorOverviewResult({
      ...overview,
      trends: [{
        statDate: "2026-09-27",
        totalCount: 1,
        successCount: 2,
        successAmount: "¥80.00"
      }]
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-MONITOR-OVERVIEW"
  );
  for (const statDate of ["2026-02-30", "2026-09-27 00:00", ""]) {
    assert.throws(
      () => normalizePaymentMonitorOverviewResult({
        ...overview,
        trends: [{
          statDate,
          totalCount: 1,
          successCount: 1,
          successAmount: "¥80.00"
        }]
      }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-MONITOR-OVERVIEW"
    );
  }
  const trendRows = [
    { statDate: "2026-09-27", totalCount: 1, successCount: 1, successAmount: "¥80.00" },
    { statDate: "2026-09-26", totalCount: 1, successCount: 0, successAmount: "¥0.00" }
  ];
  assert.deepEqual(
    normalizePaymentMonitorOverviewResult({ ...overview, trends: trendRows }).trends,
    trendRows
  );
  for (const invalidTrends of [
    [...trendRows].reverse(),
    [trendRows[0], { ...trendRows[0] }]
  ]) {
    assert.throws(
      () => normalizePaymentMonitorOverviewResult({ ...overview, trends: invalidTrends }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-MONITOR-OVERVIEW"
    );
  }
  assert.throws(
    () => normalizePaymentMonitorOverviewResult({
      ...overview,
      alerts: [{
        alertType: "WAIT_CALLBACK_TIMEOUT",
        alertTitle: "支付回调待收口",
        alertMessage: "存在待处理支付单",
        alertLevel: "P1",
        alertLevelType: "danger",
        affectedCount: -1,
        suggestedAction: "主动查单",
        actionRoute: "/payment-issues"
      }]
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-MONITOR-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentMonitorOverviewResult({
      ...overview,
      metricDefinitions: [{
        metricCode: "payment_success_rate",
        metricName: "支付成功率",
        definition: "成功支付单量 / 支付单总量",
        unit: "%",
        defaultThreshold: ">= 95%",
        alertLevel: "P1",
        owner: "支付运营",
        actionRoute: ""
      }]
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-MONITOR-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentMonitorOverviewResult(null),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-MONITOR-OVERVIEW"
  );
});

function createValidPaymentTaskCenterOverview(overrides = {}) {
  return {
    expiredPaymentCount: 5,
    dueExpiredPaymentCount: 2,
    backingOffExpiredPaymentCount: 3,
    blockedExpiredPaymentCount: 0,
    pendingCallbackCount: 1,
    pendingQueryBacklogCount: 4,
    duePendingQueryCount: 1,
    pendingRefundQueryBacklogCount: 3,
    duePendingRefundQueryCount: 2,
    queryMonitorWindowHours: 24,
    recentQueryCount: 8,
    recentQueryFailureCount: 2,
    recentQueryFailureRate: "25.00%",
    queryFailureCountThreshold: 5,
    queryFailureRateThresholdPercent: 30,
    queryFailureRateMinimumSampleCount: 10,
    recentQueryRateLimitRejectionCount: 0,
    queryRateLimitRejectionCountThreshold: 10,
    actionableQueryMonitorAlertCount: 0,
    pendingIssueAlertDispatchCount: 0,
    failedIssueAlertDispatchCount: 0,
    oldestPendingIssueAlertDispatchMinutes: 0,
    issueAlertDispatchCountThreshold: 10,
    issueAlertDispatchTimeoutMinutes: 15,
    pendingIssueAlertReceiptCount: 0,
    manualIssueAlertReceiptCount: 0,
    dueIssueAlertReceiptCount: 0,
    backingOffIssueAlertReceiptCount: 0,
    queryingIssueAlertReceiptCount: 0,
    exhaustedIssueAlertReceiptCount: 0,
    oldestPendingIssueAlertReceiptMinutes: 0,
    issueAlertReceiptCountThreshold: 10,
    issueAlertReceiptTimeoutMinutes: 30,
    queryTrends: [],
    failedEventCount: 4,
    retryableFailedEventCount: 3,
    deadLetterEventCount: 1,
    unknownRefundSubmitEventCount: 0,
    failedRefundCount: 4,
    retryableFailedRefundCount: 2,
    blockedFailedRefundCount: 2,
    warningDayEndBatchCount: 0,
    enabledControlPolicyCount: 0,
    controlPolicyWarningCount: 0,
    overdueIssueCount: 0,
    pendingIssueAlertGenerationCount: 1,
    pendingIssueAlertEscalationCount: 2,
    actionableIssueSlaCount: 3,
    focusAlerts: [],
    recentTaskRuns: [],
    ...overrides
  };
}

test("requires structured payment task center counters", () => {
  const overview = createValidPaymentTaskCenterOverview();

  assert.deepEqual(normalizePaymentTaskCenterOverviewResult(overview), overview);
  assert.deepEqual(
    normalizePaymentTaskCenterOverviewResult({
      ...overview,
      recentTaskRuns: [{
        taskLogNo: "TASK-001",
        taskStatus: "SUCCESS",
        taskStatusType: "success",
        processedCount: 1,
        successCount: 1,
        warningCount: 0,
        failCount: 0,
        startedAt: "2026-09-27 10:00:00",
        completedAt: "2026-09-27 10:00:01"
      }]
    }).recentTaskRuns.length,
    1
  );
  assert.throws(
    () => normalizePaymentTaskCenterOverviewResult({ ...overview, duePendingQueryCount: -1 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-CENTER-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentTaskCenterOverviewResult({ ...overview, recentQueryFailureRate: undefined }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-CENTER-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentTaskCenterOverviewResult({ ...overview, queryFailureRateThresholdPercent: 101 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-CENTER-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentTaskCenterOverviewResult({ ...overview, recentQueryFailureRate: "24.99%" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-CENTER-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentTaskCenterOverviewResult({
      ...overview,
      queryTrends: [{ timeBucket: "2026-09-27 10:00", queryCount: 1, failureCount: 2, failureRate: "200.00%" }]
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-CENTER-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentTaskCenterOverviewResult({
      ...overview,
      queryTrends: [{ timeBucket: "2026-09-27 10:00", queryCount: 4, failureCount: 1, failureRate: "24.99%" }]
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-CENTER-OVERVIEW"
  );
  for (const timeBucket of ["2026-02-30 10:00", "2026-09-27 10:30"]) {
    assert.throws(
      () => normalizePaymentTaskCenterOverviewResult({
        ...overview,
        queryTrends: [{ timeBucket, queryCount: 4, failureCount: 1, failureRate: "25.00%" }]
      }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-CENTER-OVERVIEW"
    );
  }
  assert.throws(
    () => normalizePaymentTaskCenterOverviewResult({
      ...overview,
      focusAlerts: [{
        alertType: "PAYMENT_EVENT",
        alertTitle: "失败事件待处置",
        alertMessage: "存在失败事件",
        alertLevel: "P1",
        alertLevelType: "danger",
        affectedCount: 1,
        suggestedAction: "处理失败事件"
      }]
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-CENTER-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentTaskCenterOverviewResult({ ...overview, recentTaskRuns: null }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-CENTER-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentTaskCenterOverviewResult({
      ...overview,
      recentTaskRuns: [{
        taskLogNo: "TASK-001",
        taskStatus: "SUCCESS",
        processedCount: 1,
        successCount: 0,
        warningCount: 1,
        failCount: 0
      }]
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-CENTER-OVERVIEW"
  );
});

test("enforces payment task center counter relationships", () => {
  const overview = createValidPaymentTaskCenterOverview();
  const valid = {
    ...overview,
    expiredPaymentCount: 5,
    dueExpiredPaymentCount: 2,
    backingOffExpiredPaymentCount: 3,
    pendingQueryBacklogCount: 4,
    duePendingQueryCount: 1,
    pendingRefundQueryBacklogCount: 3,
    duePendingRefundQueryCount: 2,
    recentQueryCount: 8,
    recentQueryFailureCount: 2,
    failedEventCount: 4,
    retryableFailedEventCount: 3,
    deadLetterEventCount: 1,
    failedRefundCount: 4,
    retryableFailedRefundCount: 2,
    blockedFailedRefundCount: 2,
    pendingIssueAlertGenerationCount: 1,
    pendingIssueAlertEscalationCount: 2,
    actionableIssueSlaCount: 3
  };

  assert.deepEqual(normalizePaymentTaskCenterOverviewResult(valid), valid);
  assert.equal(normalizePaymentTaskCenterOverviewResult({
    ...valid, backingOffExpiredPaymentCount: 2, blockedExpiredPaymentCount: 1
  }).blockedExpiredPaymentCount, 1);
  for (const invalid of [
    { ...valid, dueExpiredPaymentCount: 4, backingOffExpiredPaymentCount: 2 },
    { ...valid, blockedExpiredPaymentCount: 1 },
    { ...valid, duePendingQueryCount: 5 },
    { ...valid, duePendingRefundQueryCount: 4 },
    { ...valid, recentQueryFailureCount: 9 },
    { ...valid, retryableFailedEventCount: 4 },
    { ...valid, blockedFailedRefundCount: 3 },
    { ...valid, actionableIssueSlaCount: 2 }
  ]) {
    assert.throws(
      () => normalizePaymentTaskCenterOverviewResult(invalid),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-CENTER-OVERVIEW"
    );
  }
});

test("requires payment task focus alert severity to match affected count", () => {
  const overview = createValidPaymentTaskCenterOverview();
  const alert = {
    alertType: "PAYMENT_EVENT",
    alertTitle: "失败事件待处置",
    alertMessage: "存在失败事件",
    alertLevel: "P1",
    alertLevelType: "danger",
    affectedCount: 1,
    suggestedAction: "处理失败事件",
    actionRoute: "/payment-events"
  };

  assert.deepEqual(
    normalizePaymentTaskCenterOverviewResult({ ...overview, focusAlerts: [alert] }).focusAlerts,
    [alert]
  );
  for (const invalidAlert of [
    { ...alert, alertLevel: "P3", alertLevelType: "success" },
    { ...alert, affectedCount: 0 },
    { ...alert, affectedCount: 0, alertLevel: "P2", alertLevelType: "warn" }
  ]) {
    assert.throws(
      () => normalizePaymentTaskCenterOverviewResult({
        ...overview,
        focusAlerts: [invalidAlert]
      }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-CENTER-OVERVIEW"
    );
  }
});

test("validates a task action's nested task center overview before merge", () => {
  const action = {
    taskCode: "PAYMENT_TASK",
    taskName: "支付任务",
    summaryComment: "任务已完成",
    processedCount: 1,
    successCount: 1,
    warningCount: 0,
    failCount: 0,
    overview: createValidPaymentTaskCenterOverview()
  };
  assert.deepEqual(normalizeTaskActionResult(action), action);
  assert.throws(
    () => normalizeTaskActionResult({
      ...action,
      overview: { queryTrends: null, focusAlerts: [], recentTaskRuns: [] }
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-TASK-ACTION"
  );
});

test("requires structured payment day-end overview counters", () => {
  const overview = {
    totalBatchCount: 3,
    completedBatchCount: 2,
    abnormalBatchCount: 1,
    openChannelAbnormalCount: 0,
    openInternalAbnormalCount: 0,
    openPendingRefundCount: 0,
    latestChannelSuccessCount: 0,
    latestChannelSuccessAmount: "¥0.00",
    latestInternalSuccessCount: 0,
    latestInternalSuccessAmount: "¥0.00",
    latestPaymentSuccessGapCount: 0,
    latestPaymentSuccessGapAmount: "¥0.00",
    latestPendingRefundAmount: "¥0.00",
    latestBatchStatus: "COMPLETED",
    reconciliationReadinessStatus: "可进入对账",
    reconciliationReadinessType: "success",
    reconciliationReadinessSummary: "事实已收口",
    reconciliationSuggestedAction: "进入正式对账",
    reconciliationBlockingOwner: "财务值班",
    alerts: [],
    recentBatches: []
  };

  assert.deepEqual(normalizePaymentDayEndOverviewResult(overview), overview);
  assert.throws(
    () => normalizePaymentDayEndOverviewResult({ ...overview, completedBatchCount: 1 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DAY-END-OVERVIEW"
  );
  assert.equal(
    normalizePaymentDayEndRecentBatchResult({
      batchNo: "BATCH-001",
      bizDate: "2026-09-27",
      runMode: "MANUAL",
      batchStatus: "COMPLETED",
      batchStatusType: "success",
      paymentTotalCount: 10,
      paymentSuccessCount: 8,
      paymentSuccessAmount: "¥80.00",
      channelSuccessCount: 8,
      channelSuccessAmount: "¥80.00",
      internalSuccessCount: 8,
      internalSuccessAmount: "¥80.00",
      paymentSuccessGapCount: 0,
      paymentSuccessGapAmount: "¥0.00",
      refundSuccessCount: 1,
      refundSuccessAmount: "¥10.00",
      channelAbnormalCount: 0,
      internalAbnormalCount: 0,
      pendingRefundCount: 0,
      pendingRefundAmount: "¥0.00",
      reconciliationReadinessStatus: "READY",
      reconciliationReadinessType: "success",
      reconciliationReadinessSummary: "前置事实已收口，可进入正式对账。",
      summaryComment: "日终处理完成",
      triggeredBy: "ops",
      createdAt: "2026-09-27 01:00:00",
      completedAt: "2026-09-27 01:01:00"
    }).batchNo,
    "BATCH-001"
  );
  assert.throws(
    () => normalizePaymentDayEndRecentBatchResult({
      batchNo: "BATCH-001",
      bizDate: "2026-09-27",
      runMode: "MANUAL",
      batchStatus: "COMPLETED",
      batchStatusType: "success",
      paymentTotalCount: 10,
      paymentSuccessCount: 8
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DAY-END-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentDayEndRecentBatchResult({
      batchNo: "BATCH-001",
      bizDate: "2026-09-27",
      runMode: "MANUAL",
      batchStatus: "COMPLETED",
      batchStatusType: "success",
      paymentTotalCount: 10,
      paymentSuccessCount: 8,
      paymentSuccessAmount: "¥80.00",
      channelSuccessCount: 8,
      channelSuccessAmount: "¥80.00",
      internalSuccessCount: 8,
      internalSuccessAmount: "¥80.00",
      paymentSuccessGapCount: 0,
      paymentSuccessGapAmount: "¥0.00",
      refundSuccessCount: 1,
      refundSuccessAmount: "¥10.00",
      channelAbnormalCount: 0,
      internalAbnormalCount: 0,
      pendingRefundCount: 0,
      pendingRefundAmount: "¥0.00",
      reconciliationReadinessStatus: "READY",
      reconciliationReadinessType: "success",
      reconciliationReadinessSummary: "事实已收口",
      summaryComment: "日终处理完成",
      triggeredBy: "ops",
      createdAt: "2026-09-27 01:01:00",
      completedAt: "2026-09-27 01:00:00"
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DAY-END-OVERVIEW"
  );
  assert.deepEqual(
    normalizePaymentDayEndRunResult({ ...overview, latestBizDate: "2026-09-26", latestBatchStatus: "COMPLETED" }, "2026-09-26").latestBizDate,
    "2026-09-26"
  );
  assert.throws(
    () => normalizePaymentDayEndOverviewResult({ ...overview, abnormalBatchCount: -1 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DAY-END-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentDayEndOverviewResult({ ...overview, latestBizDate: "2026-02-30" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DAY-END-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentDayEndOverviewResult({ ...overview, alerts: null }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DAY-END-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentDayEndOverviewResult({
      ...overview,
      recentBatches: [{
        batchNo: "BATCH-001",
        bizDate: "2026-09-27",
        batchStatus: "UNKNOWN",
        batchStatusType: "info"
      }]
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DAY-END-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentDayEndOverviewResult({
      ...overview,
      recentBatches: [{
        batchNo: "BATCH-001",
        bizDate: "2026-09-27",
        batchStatus: "WARNING",
        batchStatusType: "success"
      }]
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DAY-END-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentDayEndOverviewResult({ ...overview, abnormalBatchCount: 4 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DAY-END-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentDayEndOverviewResult({ ...overview, latestChannelSuccessAmount: "not-an-amount" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DAY-END-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentDayEndOverviewResult({ ...overview, reconciliationReadinessType: "info" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DAY-END-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentDayEndOverviewResult({ ...overview, latestBatchStatus: undefined }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DAY-END-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentDayEndOverviewResult({ ...overview, openPendingRefundCount: undefined }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DAY-END-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentDayEndOverviewResult({
      ...overview,
      alerts: [{
        alertType: "PENDING_PAYMENT",
        alertTitle: "支付待收口",
        severityLevel: "P3",
        severityLevelType: "success",
        affectedCount: 1,
        alertMessage: "存在待收口支付",
        suggestedAction: "处理支付单",
        actionRoute: "/payments"
      }]
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DAY-END-OVERVIEW"
  );
  assert.throws(
    () => normalizePaymentDayEndRecentBatchResult({
      batchNo: "BATCH-001",
      bizDate: "2026-02-30",
      runMode: "MANUAL",
      batchStatus: "COMPLETED",
      batchStatusType: "success",
      paymentTotalCount: 0,
      paymentSuccessCount: 0,
      paymentSuccessAmount: "¥0.00",
      channelSuccessCount: 0,
      channelSuccessAmount: "¥0.00",
      internalSuccessCount: 0,
      internalSuccessAmount: "¥0.00",
      paymentSuccessGapCount: 0,
      paymentSuccessGapAmount: "¥0.00",
      refundSuccessCount: 0,
      refundSuccessAmount: "¥0.00",
      channelAbnormalCount: 0,
      internalAbnormalCount: 0,
      pendingRefundCount: 0,
      pendingRefundAmount: "¥0.00",
      reconciliationReadinessStatus: "READY",
      reconciliationReadinessType: "success",
      reconciliationReadinessSummary: "事实已收口",
      summaryComment: "日终处理完成",
      triggeredBy: "ops",
      createdAt: "2026-09-27 01:00:00",
      completedAt: "2026-09-27 01:01:00"
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-DAY-END-OVERVIEW"
  );
});

test("requires complete payment metrics for dashboard health cards", () => {
  const metrics = {
    totalCount: 10,
    successCount: 8,
    pendingCount: 1,
    closedCount: 1,
    successAmount: "¥80.00",
    successRate: "80.00%"
  };

  assert.deepEqual(normalizePaymentMetricsResult(metrics), metrics);
  for (const invalid of [
    { ...metrics, successCount: 11 },
    { ...metrics, pendingCount: 11 },
    { ...metrics, closedCount: 11 }
  ]) {
    assert.throws(
      () => normalizePaymentMetricsResult(invalid),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-METRICS"
    );
  }
  assert.throws(
    () => normalizePaymentMetricsResult({ ...metrics, successRate: "79.99%" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-METRICS"
  );
  assert.throws(
    () => normalizePaymentMetricsResult({ ...metrics, pendingCount: -1 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-METRICS"
  );
  assert.throws(
    () => normalizePaymentMetricsResult({ ...metrics, successRate: "" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-METRICS"
  );
  assert.throws(
    () => normalizePaymentMetricsResult({ ...metrics, successAmount: "6.8e1" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-METRICS"
  );
  assert.throws(
    () => normalizePaymentMetricsResult({ ...metrics, successRate: "101.00%" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-METRICS"
  );
});

test("requires identity, positive version, and status for payment configuration history", () => {
  const versions = [{
    channelCode: "CHANNEL-001",
    versionNo: 2,
    status: "ACTIVE",
    statusType: "success",
    createdAt: "2026-09-27 10:00:00"
  }];

  assert.deepEqual(
    normalizePaymentConfigVersionsResult(versions, "CHANNEL-001", "channelCode"),
    versions
  );
  assert.equal(
    normalizePaymentConfigVersionsResult(
      [{ ...versions[0], channelCode: " CHANNEL-001 " }],
      " CHANNEL-001 ",
      "channelCode"
    )[0].channelCode,
    " CHANNEL-001 "
  );
  assert.throws(
    () => normalizePaymentConfigVersionsResult(
      [{ channelCode: "CHANNEL-002", versionNo: 2, status: "ACTIVE", statusType: "success" }],
      "CHANNEL-001",
      "channelCode"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-VERSIONS"
  );
  assert.throws(
    () => normalizePaymentConfigVersionsResult(
      [{ channelCode: "CHANNEL-001", versionNo: 0, status: "ACTIVE", statusType: "success" }],
      "CHANNEL-001",
      "channelCode"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-VERSIONS"
  );
  assert.throws(
    () => normalizePaymentConfigVersionsResult(
      [{ channelCode: "CHANNEL-001", versionNo: 2, status: "ACTIVE" }],
      "CHANNEL-001",
      "channelCode"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-VERSIONS"
  );
  assert.throws(
    () => normalizePaymentConfigVersionsResult(
      [{ channelCode: "CHANNEL-001", versionNo: 2, status: "UNKNOWN", statusType: "success" }],
      "CHANNEL-001",
      "channelCode"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-VERSIONS"
  );
  assert.throws(
    () => normalizePaymentConfigVersionsResult(
      [{ channelCode: "CHANNEL-001", versionNo: 2, status: "INACTIVE", statusType: "success" }],
      "CHANNEL-001",
      "channelCode"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-VERSIONS"
  );
  assert.throws(
    () => normalizePaymentConfigVersionsResult(
      [{ ...versions[0], createdAt: "not-a-datetime" }],
      "CHANNEL-001",
      "channelCode"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-VERSIONS"
  );

  const gatewayVersion = {
    gatewayCode: "GATEWAY-001",
    versionNo: 2,
    status: "ENABLED",
    statusType: "success",
    certificateStatus: "VALID",
    certificateStatusType: "success",
    createdAt: "2026-09-27 10:00:00"
  };
  assert.deepEqual(
    normalizePaymentConfigVersionsResult([gatewayVersion], "GATEWAY-001", "gatewayCode"),
    [gatewayVersion]
  );
  assert.throws(
    () => normalizePaymentConfigVersionsResult(
      [{ ...gatewayVersion, certificateStatusType: "danger" }],
      "GATEWAY-001",
      "gatewayCode"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CONFIG-VERSIONS"
  );
});

test("normalizes a nullable payment issue responsibility summary", async (context) => {
  assert.deepEqual(normalizePaymentIssueResponsibilitySummaryResult([]), []);
  assert.deepEqual(
    normalizePaymentIssueResponsibilitySummaryResult([{
      groupName: "支付后端值班组",
      groupType: "danger",
      totalCount: 3,
      overdueCount: 1,
      p1Count: 1,
      p2Count: 1,
      suggestedAction: "优先核对查单"
    }]),
    [{
      groupName: "支付后端值班组",
      groupType: "danger",
      totalCount: 3,
      overdueCount: 1,
      p1Count: 1,
      p2Count: 1,
      suggestedAction: "优先核对查单"
    }]
  );
  assert.throws(
    () => normalizePaymentIssueResponsibilitySummaryResult([{
      groupName: "支付后端值班组",
      groupType: "danger",
      totalCount: 1,
      overdueCount: 2,
      p1Count: 0,
      p2Count: 0,
      suggestedAction: "优先核对查单"
    }]),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ISSUE-RESPONSIBILITY-SUMMARY"
  );
  assert.throws(
    () => normalizePaymentIssueResponsibilitySummaryResult([{
      groupName: "支付后端值班组",
      groupType: "danger",
      totalCount: 1,
      overdueCount: 0,
      p1Count: 1,
      p2Count: 1,
      suggestedAction: "优先核对查单"
    }]),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ISSUE-RESPONSIBILITY-SUMMARY"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  assert.deepEqual(await paymentIssueApi.getResponsibilitySummary(), []);
});

test("rejects malformed payment issue responsibility summary rows", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({
      code: "0",
      data: [{ groupName: "支付后端值班组", totalCount: 1 }]
    })
  }));

  await assert.rejects(
    paymentIssueApi.getResponsibilitySummary(),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-ISSUE-RESPONSIBILITY-SUMMARY"
  );
});

test("normalizes nullable refund detail collections", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({
      code: "0",
      data: {
        refundOrderId: "REF-001",
        paymentOrderId: "PAY-001",
        businessChainId: "CHAIN-PAY-001",
        orderNo: "ORDER-001",
        paidAmount: "68.00",
        refundAmount: "18.00",
        status: "REVIEWING",
        statusType: "warn",
        appliedAt: "2026-09-27 10:00:00",
        attempts: null,
        operationLogs: null
      }
    })
  }));

  assert.deepEqual(await refundApi.getDetail("REF-001"), {
    refundOrderId: "REF-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    paidAmount: "68.00",
    refundAmount: "18.00",
    status: "REVIEWING",
    statusType: "warn",
    appliedAt: "2026-09-27 10:00:00",
    attempts: [],
    operationLogs: []
  });
});

test("rejects empty or mismatched refund detail responses", async (context) => {
  assert.deepEqual(
    normalizeRefundDetailResult({
      refundOrderId: "REF-001",
      paymentOrderId: "PAY-001",
      businessChainId: "CHAIN-PAY-001",
      orderNo: "ORDER-001",
      paidAmount: "68.00",
      refundAmount: "18.00",
      status: "REVIEWING",
      statusType: "warn",
      appliedAt: "2026-09-27 10:00:00",
      attempts: [],
      operationLogs: []
    }, "REF-001"),
    {
      refundOrderId: "REF-001",
      paymentOrderId: "PAY-001",
      businessChainId: "CHAIN-PAY-001",
      orderNo: "ORDER-001",
      paidAmount: "68.00",
      refundAmount: "18.00",
      status: "REVIEWING",
      statusType: "warn",
      appliedAt: "2026-09-27 10:00:00",
      attempts: [],
      operationLogs: []
    }
  );
  const validAttempt = {
    refundAttemptNo: "ATT-001",
    attemptStatus: "PENDING",
    attemptStatusType: "warn",
    createdAt: "2026-09-27 10:00:01",
    submittedAt: "2026-09-27 10:00:02",
    completedAt: "-"
  };
  const validAttemptDetail = {
    refundOrderId: "REF-001",
      paymentOrderId: "PAY-001",
      businessChainId: "CHAIN-PAY-001",
      orderNo: "ORDER-001",
      paidAmount: "68.00",
      refundAmount: "18.00",
    status: "REVIEWING",
    statusType: "warn",
    appliedAt: "2026-09-27 10:00:00",
    attempts: [validAttempt],
    operationLogs: []
  };
  assert.deepEqual(normalizeRefundDetailResult(validAttemptDetail, "REF-001"), validAttemptDetail);
  for (const invalidFinancialFacts of [
    { paidAmount: undefined },
    { paidAmount: "17.99" }
  ]) {
    assert.throws(
      () => normalizeRefundDetailResult({ ...validAttemptDetail, ...invalidFinancialFacts }, "REF-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
    );
  }
  for (const invalidAttempts of [
    [{ ...validAttempt, refundAttemptNo: "" }],
    [
      validAttempt,
      { ...validAttempt, refundAttemptNo: "ATT-002", createdAt: "2026-09-27 10:00:02", submittedAt: "2026-09-27 10:00:03" }
    ],
    [{ ...validAttempt, attemptStatusType: "success" }],
    [{ ...validAttempt, attemptStatus: "SUCCESS", attemptStatusType: "success" }],
    [{ ...validAttempt, completedAt: "2026-09-27 10:00:03" }],
    [{ ...validAttempt, createdAt: "not-a-time" }],
    [{ ...validAttempt, createdAt: "2026-09-27 09:59:59" }],
    [{ ...validAttempt, submittedAt: "2026-09-27 10:00:00" }],
    [{ ...validAttempt, completedAt: "2026-09-27 10:00:01" }],
    [{ ...validAttempt, completedAt: "not-a-time" }]
  ]) {
    assert.throws(
      () => normalizeRefundDetailResult({ ...validAttemptDetail, attempts: invalidAttempts }, "REF-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
    );
  }
  for (const field of ["attempts", "operationLogs"]) {
    assert.throws(
      () => normalizeRefundDetailResult({ ...validAttemptDetail, [field]: "malformed" }, "REF-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
    );
  }
  assert.throws(
    () => normalizeRefundDetailResult({ ...validAttemptDetail, operationLogs: [{ logNo: "LOG-001" }] }, "REF-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult({
      ...validAttemptDetail,
      operationLogs: [{ logNo: "LOG-001", actionName: "申请退款", createdAt: "2026-09-27 09:59:59" }]
    }, "REF-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult({
      ...validAttemptDetail,
      status: "SUCCESS",
      statusType: "success",
      channelCode: "WX_H5",
      channelRefundNo: "CH-REF-001",
      successAt: "2026-09-27 10:01:00",
      terminalAt: "2026-09-27 10:01:01",
      attempts: [{
        ...validAttempt,
        attemptStatus: "SUCCESS",
        attemptStatusType: "success",
        submittedAt: "2026-09-27 10:01:02",
        completedAt: "2026-09-27 10:01:03"
      }]
    }, "REF-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult({
      ...validAttemptDetail,
      operationLogs: [
        { logNo: "LOG-001", actionName: "申请退款", createdAt: "2026-09-27 10:00:00" },
        { logNo: "LOG-002", actionName: "审核退款", createdAt: "2026-09-27 10:00:01" }
      ]
    }, "REF-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult({
      ...validAttemptDetail,
      status: "SUCCESS",
      statusType: "success",
      channelCode: "WX_H5",
      channelRefundNo: "CH-REF-001",
      successAt: "2026-09-27 10:01:00",
      terminalAt: "2026-09-27 10:01:01",
      operationLogs: [{ logNo: "LOG-003", actionName: "退款成功", createdAt: "2026-09-27 10:01:02" }]
    }, "REF-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult({
      ...validAttemptDetail,
      status: "SUCCESS",
      statusType: "success",
      channelRefundNo: "CH-REF-001",
      successAt: "2026-09-27 10:01:00",
      terminalAt: "2026-09-27 10:02:00"
    }, "REF-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult({ refundOrderId: "REF-002" }, "REF-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult({
      refundOrderId: "REF-001",
      paymentOrderId: "PAY-001",
      orderNo: "ORDER-001",
      refundAmount: "0",
      status: "REVIEWING",
      statusType: "warn"
    }, "REF-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  for (const invalidDetail of [
    {
      refundOrderId: "REF-001",
      paymentOrderId: "PAY-001",
      orderNo: "ORDER-001",
      refundAmount: "18.00",
      status: "REVIEWING",
      statusType: "warn",
      appliedAt: undefined
    },
    {
      refundOrderId: "REF-001",
      paymentOrderId: "PAY-001",
      orderNo: "ORDER-001",
      refundAmount: "18.00",
      status: "REVIEWING",
      statusType: "warn",
      appliedAt: "2026-02-30 10:00:00"
    }
  ]) {
    assert.throws(
      () => normalizeRefundDetailResult(invalidDetail, "REF-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
    );
  }
  assert.throws(
    () => normalizeRefundDetailResult({
      refundOrderId: "REF-001",
      paymentOrderId: "PAY-001",
      orderNo: "ORDER-001",
      refundAmount: "18.00",
      status: "SUCCESS",
      statusType: "success"
    }, "REF-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  for (const invalidLifecycle of [
    {
      refundOrderId: "REF-001",
      paymentOrderId: "PAY-001",
      orderNo: "ORDER-001",
      refundAmount: "18.00",
      status: "SUCCESS",
      statusType: "success",
      appliedAt: "2026-09-27 10:00:00",
      successAt: "2026-09-27 09:59:00",
      terminalAt: "2026-09-27 10:01:00"
    },
    {
      refundOrderId: "REF-001",
      paymentOrderId: "PAY-001",
      orderNo: "ORDER-001",
      refundAmount: "18.00",
      status: "SUCCESS",
      statusType: "success",
      appliedAt: "2026-09-27 10:00:00",
      successAt: "2026-09-27 10:01:00",
      terminalAt: "2026-09-27 10:00:30"
    }
  ]) {
    assert.throws(
      () => normalizeRefundDetailResult(invalidLifecycle, "REF-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
    );
  }
  for (const invalidChannelSubmittedAt of [
    { ...validAttemptDetail, channelSubmittedAt: "not-a-time" },
    { ...validAttemptDetail, channelSubmittedAt: "2026-09-27 09:59:59" },
    {
      ...validAttemptDetail,
      status: "SUCCESS",
      statusType: "success",
      successAt: "2026-09-27 10:01:00",
      terminalAt: "2026-09-27 10:01:01",
      channelSubmittedAt: "2026-09-27 10:01:02"
    }
  ]) {
    assert.throws(
      () => normalizeRefundDetailResult(invalidChannelSubmittedAt, "REF-001"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
    );
  }
  assert.throws(
    () => normalizeRefundDetailResult({
      refundOrderId: "REF-001",
      paymentOrderId: "PAY-001",
      orderNo: "ORDER-001",
      refundAmount: "18.00",
      status: "SUCCESS",
      statusType: "success",
      successAt: "2026-09-27 10:01:00",
      terminalAt: "-"
    }, "REF-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult({
      refundOrderId: "REF-001",
      paymentOrderId: "PAY-001",
      orderNo: "ORDER-001",
      refundAmount: "18.00",
      status: "REVIEWING",
      statusType: "success"
    }, "REF-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  await assert.rejects(
    refundApi.getDetail("REF-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
});

test("rejects refund pages with inconsistent amount and status facts", () => {
  const validRow = {
    refundOrderId: "REF-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    customerName: "客户一",
    refundAmount: "¥18.00",
    refundMethod: "原路退款",
    status: "PROCESSING",
    statusType: "warn",
    channelRefundNo: undefined,
    appliedAt: "2026-09-27 10:00:00",
    successAt: "-",
    terminalAt: "-"
  };
  assert.deepEqual(
    normalizeRefundListPage({ items: [validRow], total: 1 }).items,
    [validRow]
  );
  assert.throws(
    () => normalizeRefundListPage({
      items: [{ ...validRow, status: "UNKNOWN", statusType: "warn" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-LIST"
  );
  assert.throws(
    () => normalizeRefundListPage({
      items: [{ ...validRow, status: "SUCCESS", statusType: "warn", successAt: "2026-09-27 10:01:00" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-LIST"
  );
  assert.throws(
    () => normalizeRefundListPage({
      items: [{ ...validRow, status: "SUCCESS", statusType: "success", successAt: "-" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-LIST"
  );
  assert.throws(
    () => normalizeRefundListPage({
      items: [{ ...validRow, status: "SUCCESS", statusType: "success", successAt: "2026-09-27 09:59:59" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-LIST"
  );
  assert.throws(
    () => normalizeRefundListPage({
      items: [{ ...validRow, refundAmount: "0" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-LIST"
  );
  assert.throws(
    () => normalizeRefundListPage({
      items: [{ ...validRow, paymentOrderId: "" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-LIST"
  );
  assert.throws(
    () => normalizeRefundListPage({
      items: [{ ...validRow, appliedAt: "not-a-datetime" }],
      total: 1
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-LIST"
  );
  assert.deepEqual(
    normalizeRefundListPage({
      items: [{
        ...validRow,
        status: "SUCCESS",
        statusType: "success",
        channelCode: "WX_H5",
        channelRefundNo: "CH-REF-001",
        channelSubmittedAt: "2026-09-27 10:00:30",
        successAt: "2026-09-27 10:01:00",
        terminalAt: "2026-09-27 10:02:00"
      }],
      total: 1
    }).items[0].terminalAt,
    "2026-09-27 10:02:00"
  );
  for (const invalidFact of [
    { channelCode: undefined },
    { channelSubmittedAt: undefined },
    { channelSubmittedAt: "2026-09-27 09:59:59" }
  ]) {
    assert.throws(
      () => normalizeRefundListPage({
        items: [{
          ...validRow,
          status: "SUCCESS",
          statusType: "success",
          channelCode: "WX_H5",
          channelRefundNo: "CH-REF-001",
          channelSubmittedAt: "2026-09-27 10:00:30",
          successAt: "2026-09-27 10:01:00",
          terminalAt: "2026-09-27 10:02:00",
          ...invalidFact
        }],
        total: 1
      }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-LIST"
    );
  }
  for (const terminalAt of ["not-a-time", "2026-09-27 09:59:59", undefined]) {
    assert.throws(
      () => normalizeRefundListPage({
        items: [{
          ...validRow,
          status: "SUCCESS",
          statusType: "success",
          channelCode: "WX_H5",
          channelRefundNo: "CH-REF-001",
          channelSubmittedAt: "2026-09-27 10:00:30",
          successAt: "2026-09-27 10:01:00",
          terminalAt
        }],
        total: 1
      }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-LIST"
    );
  }
});

test("reads anomalous terminal refunds only through diagnostic filters", async (context) => {
  const missingTime = {
    refundOrderId: "REF-MISSING",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    customerName: "客户一",
    refundAmount: "¥18.00",
    refundMethod: "原路退款",
    status: "SUCCESS",
    statusType: "success",
    channelCode: "wx_h5",
    channelRefundNo: "CH-001",
    channelSubmittedAt: "2026-09-27 10:00:30",
    appliedAt: "2026-09-27 10:00:00",
    successAt: "-",
    terminalAt: "2026-09-27 10:02:00"
  };
  assert.throws(
    () => normalizeRefundListPage({ items: [missingTime], total: 1 }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-LIST"
  );
  for (const refundStatus of ["SUCCESS_TIME_MISSING", "UNVERIFIED_SUCCESS"]) {
    assert.deepEqual(normalizeRefundListPage({ items: [missingTime], total: 1 }, refundStatus).items,
      [missingTime]);
  }
  const wrongChannel = { ...missingTime, refundOrderId: "REF-CHANNEL", successAt: "2026-09-27 10:01:00", channelCode: null };
  assert.deepEqual(normalizeRefundListPage({ items: [wrongChannel], total: 1 }, "TERMINAL_FACT_MISMATCH").items,
    [wrongChannel]);
  for (const refundAmount of ["¥0.00", "¥-5.00", null]) {
    const anomalousAmount = { ...missingTime, refundAmount };
    for (const refundStatus of ["TERMINAL_FACT_MISMATCH", "UNVERIFIED_SUCCESS"]) {
      assert.deepEqual(normalizeRefundListPage({ items: [anomalousAmount], total: 1 }, refundStatus).items,
        [anomalousAmount]);
    }
    assert.throws(
      () => normalizeRefundListPage({ items: [anomalousAmount], total: 1 }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-LIST"
    );
  }
  for (const malformed of [
    { ...missingTime, refundAmount: "-18.00" },
    { ...missingTime, refundAmount: "¥-5.000" },
    { ...missingTime, refundAmount: undefined },
    { ...missingTime, paymentOrderId: "" }
  ]) {
    assert.throws(
      () => normalizeRefundListPage({ items: [malformed], total: 1 }, "UNVERIFIED_SUCCESS"),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-LIST"
    );
  }
  let requestedUrl;
  context.mock.method(globalThis, "fetch", async (url) => {
    requestedUrl = String(url);
    return {
      ok: true,
      json: async () => ({ code: "0", data: { items: [missingTime], total: 1, pageNo: 1, pageSize: 20 } })
    };
  });
  const page = await refundApi.getList({ paymentOrderId: "PAY-001", refundStatus: "UNVERIFIED_SUCCESS" });
  assert.deepEqual(page.items, [missingTime]);
  assert.match(requestedUrl, /paymentOrderId=PAY-001&refundStatus=UNVERIFIED_SUCCESS/);
});

test("applies refund list validation to API responses", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({
      code: "0",
      data: {
        items: [{
          refundOrderId: "REF-001",
          paymentOrderId: "PAY-001",
          businessChainId: "CHAIN-PAY-001",
          orderNo: "ORDER-001",
          customerName: "客户一",
          refundAmount: "¥18.00",
          refundMethod: "原路退款",
          status: "SUCCESS",
          statusType: "success",
          channelCode: "WX_H5",
          channelRefundNo: "CH-REF-001",
          channelSubmittedAt: "2026-09-27 10:00:30",
          appliedAt: "2026-09-27 10:00:00",
          successAt: "2026-09-27 10:01:00",
          terminalAt: "2026-09-27 10:02:00"
        }],
        total: 1,
        pageNo: 1,
        pageSize: 20
      }
    })
  }));

  const result = await refundApi.getList();
  assert.equal(result.items[0].refundOrderId, "REF-001");
});

test("filters null rows from refund detail and collection responses", async (context) => {
  let call = 0;
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({
      code: "0",
      data: call++ === 0
        ? {
          refundOrderId: "REF-002",
          paymentOrderId: "PAY-002",
          businessChainId: "CHAIN-PAY-002",
          orderNo: "ORDER-002",
          paidAmount: "68.00",
          refundAmount: "18.00",
          status: "REVIEWING",
          statusType: "warn",
          appliedAt: "2026-09-27 10:00:00",
          attempts: [null, {
            refundAttemptNo: "A-1",
            attemptStatus: "PENDING",
            attemptStatusType: "warn",
            createdAt: "2026-09-27 10:00:01",
            submittedAt: "-",
            completedAt: "-"
          }],
          operationLogs: [null, { logNo: "L-1", actionName: "退款处理中", createdAt: "2026-09-27 10:00:00" }]
        }
        : [null, {
          requestNo: "REQ-001",
          reviewStatus: "PENDING_REVIEW",
          reviewStatusType: "warn",
          createdAt: "2026-09-27 10:00:00",
          reviewedAt: null
        }]
    })
  }));

  const detail = await refundApi.getDetail("REF-002");
  assert.equal(detail.paidAmount, "68.00");
  assert.deepEqual(detail.attempts, [{
    refundAttemptNo: "A-1",
    attemptStatus: "PENDING",
    attemptStatusType: "warn",
    createdAt: "2026-09-27 10:00:01",
    submittedAt: "-",
    completedAt: "-"
  }]);
  assert.deepEqual(detail.operationLogs, [{ logNo: "L-1", actionName: "退款处理中", createdAt: "2026-09-27 10:00:00" }]);
  assert.deepEqual(
    await paymentConfigApi.getChannelReviews(),
    [{
      requestNo: "REQ-001",
      reviewStatus: "PENDING_REVIEW",
      reviewStatusType: "warn",
      createdAt: "2026-09-27 10:00:00",
      reviewedAt: null
    }]
  );
});

test("reports non-JSON upstream responses with a stable client error", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: false,
    status: 502,
    json: async () => {
      throw new SyntaxError("Unexpected token < in JSON");
    }
  }));

  const { dashboardApi } = await import("./client.js");
  await assert.rejects(
    dashboardApi.getSummary(),
    (error) => {
      assert.equal(error.code, "PAYMENT-CLIENT-INVALID-RESPONSE");
      assert.match(error.message, /无法解析/);
      return true;
    }
  );
});

test("reports network failures with a stable client error", async (context) => {
  context.mock.method(globalThis, "fetch", async () => {
    throw new TypeError("fetch failed");
  });

  const { dashboardApi } = await import("./client.js");
  await assert.rejects(
    dashboardApi.getSummary(),
    (error) => error.code === "PAYMENT-CLIENT-NETWORK"
  );
});

test("maps aborted admin requests to a stable timeout error", async (context) => {
  context.mock.method(globalThis, "fetch", async (_url, options) => {
    assert.ok(options.signal instanceof AbortSignal);
    const error = new Error("request aborted");
    error.name = "AbortError";
    throw error;
  });

  const { dashboardApi } = await import("./client.js");
  await assert.rejects(
    dashboardApi.getSummary(),
    (error) => error.code === "PAYMENT-CLIENT-TIMEOUT"
  );
});
