import assert from "node:assert/strict";
import { createHmac } from "node:crypto";
import { readFileSync } from "node:fs";
import test from "node:test";

import {
  api,
  assertLocalDemoBaseUrl,
  buildLocalOrderNo,
  assertAccountingEvent,
  assertAccountingLedger,
  assertAccountingLedgerAmount,
  assertCashierPage,
  assertBoundCashierPage,
  assertAmountFlow,
  assertClearingIdentity,
  assertClearingPaymentSuccessEvent,
  assertSingleClearingPaymentSuccessEvent,
  assertClearingTarget,
  assertClearingAccountingAmount,
  assertIdempotentPaymentSubmission,
  assertSuccessfulPaymentResubmission,
  assertIdempotentPaymentCallback,
  assertPaymentSuccessDetail,
  assertPaymentRecordProjection,
  assertPaymentRecordEventTopic,
  assertPaymentRequestDiagnostics,
  assertIdempotentRefundApplication,
  assertIdempotentRefundCallback,
  refundCallbackHeaders,
  assertRefundSuccessDetail,
  assertPaymentAmount,
  assertBillPaymentFacts,
  assertPaymentBusinessFacts,
  assertPaymentIdentity,
  assertPaymentSubmission,
  assertPrepayOrder,
  assertReconciliationChainIdentity,
  assertReconciliationCompletion,
  assertReconciliationFactAmounts,
  assertReconciliationFactPayload,
  assertReconciliationFactPayloadText,
  assertReconciliationFactPayScene,
  findReconciliationFacts,
  assertReconciliationLastFactTime,
  assertRefundAmount,
  assertRefundCustomerName,
  assertRefundPayScene,
  assertRefundChannelRefundId,
  assertRefundPaidAmount,
  assertRefundMethod,
  assertRefundReason,
  assertRefundLifecycleTime,
  assertRefundIdentity,
  assertRefundOutboxDispatch,
  assertRefundWithinPayment,
  assertRefundStatus,
  assertSinglePaymentSuccessEvent,
  isPaymentSuccessEventDispatched,
  assertSingleRefundSuccessOutbox,
  assertSettlementDraft,
  assertSettlementFullDetail,
  assertSettlementGeneratedEvent,
  assertClearingGeneratedEvent,
  findSettlementOrder,
  assertSettlementApproved,
  assertSettlementClosed,
  assertSettlementIdentity,
  assertPayoutBatchExecuted,
  assertPayoutBatchPendingRecords,
  assertPayoutBatchRecords,
  findPayoutBatch,
  findClearingOrder,
  assertPayoutRecord,
  findPayoutRecord,
  formatRequestError,
  normalizeBaseUrl,
  normalizeIdentifier,
  verifyLocalDemoTargets
} from "./cross-system-happy-path.mjs";

test("keeps special-order coverage opt-in for the isolated payment chain", () => {
  assert.equal(buildLocalOrderNo("123-456"), "LOCAL-HAPPY-123-456");
  assert.equal(buildLocalOrderNo("123-456", true), 'LOCAL-HAPPY-123-456"\\');
});

test("signs local refund callbacks with the exact order id and body", () => {
  const body = '{"result":"SUCCESS"}';
  const headers = refundCallbackHeaders("REF-1", body, "local-secret");
  const expected = createHmac("sha256", "local-secret")
    .update(`${headers["X-HSP-Refund-Callback-Timestamp"]}\nREF-1\n${body}`, "utf8")
    .digest("base64");
  assert.equal(headers["X-HSP-Refund-Callback-Signature"], expected);
  assert.notEqual(refundCallbackHeaders("REF-2", body, "local-secret")["X-HSP-Refund-Callback-Signature"], expected);
  assert.throws(() => refundCallbackHeaders("REF-1", body, " "), /HSP_REFUND_CALLBACK_HTTP_SECRET/);
});

test("binds the happy-path scene before using it across the live flow", () => {
  const source = readFileSync(new URL("./cross-system-happy-path.mjs", import.meta.url), "utf8");
  const mainSource = source.slice(source.indexOf("async function main() {"));
  assert.match(mainSource, /async function main\(\) \{\s+await verifyLocalDemoTargets/);
  assert.ok(mainSource.indexOf("await verifyLocalDemoTargets(")
    < mainSource.indexOf('api(`${paymentBaseUrl}/api/payments/prepay`'));
  assert.match(mainSource, /const expectedPayScene = "HOME_CLEAN";/);
  assert.equal((mainSource.match(/"HOME_CLEAN"/g) || []).length, 1);
  assert.match(mainSource, /payScene: expectedPayScene/);
});

test("rejects non-local or redirected happy-path targets", () => {
  for (const baseUrl of ["http://localhost:18081", "http://127.0.0.1:18140", "http://[::1]:18110"]) {
    assert.equal(assertLocalDemoBaseUrl(baseUrl, "payment-core"), baseUrl);
  }
  for (const baseUrl of ["https://localhost:18081", "http://payments.example:18081",
    "http://192.168.1.2:18081", "http://user:pass@localhost:18081",
    "http://localhost:18081/other", "http://localhost:18081?redirect=1", "not-a-url"]) {
    assert.throws(() => assertLocalDemoBaseUrl(baseUrl, "payment-core"), /loopback HTTP service root/);
  }
});

test("refuses all happy-path writes until every target and the H2 demo identity are verified", async () => {
  const targets = {
    paymentBaseUrl: "http://localhost:18081", opsConfigBaseUrl: "http://localhost:18170",
    riskBaseUrl: "http://localhost:18180", gatewayBaseUrl: "http://localhost:8090",
    clearingBaseUrl: "http://localhost:18120",
    settlementBaseUrl: "http://localhost:18130", accountingBaseUrl: "http://localhost:18110",
    refundBaseUrl: "http://localhost:18140", reconciliationBaseUrl: "http://localhost:18150"
  };
  const calls = [];
  const identities = new Map([
    ["http://localhost:18081/api/demo/identity", "payment-core-h2-demo"],
    ["http://localhost:18170/api/demo/identity", "ops-config-system-h2-local-payment-chain"],
    ["http://localhost:18180/api/demo/identity", "risk-control-system-h2-local-payment-chain"],
    ["http://localhost:8090/api/demo/identity", "gateway-access-h2-local-payment-chain"],
    ["http://localhost:18110/api/demo/identity", "accounting-system-h2-local-payment-chain"],
    ["http://localhost:18120/api/demo/identity", "clearing-system-h2-local-payment-chain"],
    ["http://localhost:18130/api/demo/identity", "settlement-system-h2-local-payment-chain"],
    ["http://localhost:18140/api/demo/identity", "refund-center-h2-local-payment-chain"],
    ["http://localhost:18150/api/demo/identity", "reconciliation-system-h2-local-payment-chain"]
  ]);
  const request = async (url) => {
    calls.push(url);
    return identities.get(url);
  };
  const scopedEnvironment = { HSP_LOCAL_PAYMENT_CHAIN_SCOPED: "true" };
  await assert.rejects(verifyLocalDemoTargets(targets, request, {}), /only through local-stack verify/);
  assert.deepEqual(calls, []);
  await verifyLocalDemoTargets(targets, request, scopedEnvironment);
  assert.deepEqual(calls, [...identities.keys()]);
  await assert.rejects(verifyLocalDemoTargets({ ...targets, refundBaseUrl: "https://refund.example" }, request, scopedEnvironment),
    /loopback HTTP service root/);
  assert.equal(calls.length, identities.size);
  await assert.rejects(verifyLocalDemoTargets({ ...targets, refundBaseUrl: "http://localhost:19999" }, request, scopedEnvironment),
    /isolated local-stack port/);
  await assert.rejects(verifyLocalDemoTargets({ ...targets, reconciliationBaseUrl: undefined }, request, scopedEnvironment),
    /loopback HTTP service root/);
  assert.equal(calls.length, identities.size);
  await assert.rejects(verifyLocalDemoTargets(targets, async (url) => {
    calls.push(url);
    return url.includes(":18120/") ? "other-backend" : identities.get(url);
  }, scopedEnvironment), /clearing-system did not confirm local H2 identity/);
  assert.equal(calls.length, identities.size + 6);
  for (const identityUrl of identities.keys()) {
    const inspected = [];
    await assert.rejects(verifyLocalDemoTargets(targets, async (url) => {
      inspected.push(url);
      return url === identityUrl ? "other-backend" : identities.get(url);
    }, scopedEnvironment), /did not confirm local H2 identity/);
    assert.equal(inspected[inspected.length - 1], identityUrl);
  }
});

test("normalizes cross-system service base URLs", () => {
  assert.equal(normalizeBaseUrl("  http://localhost:18081///  ", "fallback"), "http://localhost:18081");
  assert.equal(normalizeBaseUrl("   ", "http://localhost:18120"), "http://localhost:18120");
  assert.equal(normalizeBaseUrl(undefined, "http://localhost:18130/"), "http://localhost:18130");
});

test("normalizes identifiers before constructing resource paths", () => {
  assert.equal(normalizeIdentifier("  SET-001  "), "SET-001");
  assert.equal(normalizeIdentifier("  PRE-001  "), "PRE-001");
  assert.equal(normalizeIdentifier("  PAY-001  "), "PAY-001");
  assert.equal(normalizeIdentifier("  CLR-001  "), "CLR-001");
  assert.equal(normalizeIdentifier("  SET-001  "), "SET-001");
  assert.equal(normalizeIdentifier("  PBT-001  "), "PBT-001");
  assert.equal(`CHAIN-${normalizeIdentifier("  PAY-001  ")}`, "CHAIN-PAY-001");
  assert.equal(normalizeIdentifier("SET-001"), "SET-001");
  assert.equal(normalizeIdentifier(undefined), undefined);
});

test("ties the payment request diagnostics to one real submitted attempt and its status bucket", () => {
  const request = {
    requestNo: "ATT-001", paymentOrderId: "PAY-001", requestStatus: "处理中", requestStatusType: "info"
  };
  const page = { total: 1, items: [request] };
  const overview = {
    totalRequestCount: 1, successRequestCount: 0, failedRequestCount: 0,
    processingRequestCount: 1, waitingCallbackRequestCount: 0
  };
  assert.equal(assertPaymentRequestDiagnostics(page, overview, request, "PAY-001",
    "处理中", "info", "processingRequestCount"), request);
  const successful = { ...request, requestStatus: "成功", requestStatusType: "success" };
  assert.equal(assertPaymentRequestDiagnostics({ total: 1, items: [successful] }, {
    ...overview, successRequestCount: 1, processingRequestCount: 0
  }, successful, "PAY-001", "成功", "success", "successRequestCount"), successful);
  assert.throws(() => assertPaymentRequestDiagnostics({ total: 2, items: [request, request] }, overview,
    request, "PAY-001", "处理中", "info", "processingRequestCount"), /exactly one attempt/);
  assert.throws(() => assertPaymentRequestDiagnostics(page, overview, { ...request, requestNo: "ATT-OTHER" },
    "PAY-001", "处理中", "info", "processingRequestCount"), /did not match the submitted attempt/);
  assert.throws(() => assertPaymentRequestDiagnostics(page, overview, { ...request, requestStatusType: "warn" },
    "PAY-001", "处理中", "info", "processingRequestCount"), /status did not match/);
  assert.throws(() => assertPaymentRequestDiagnostics(page, {
    ...overview, waitingCallbackRequestCount: 1
  }, request, "PAY-001", "处理中", "info", "processingRequestCount"), /overview did not match/);
});

test("requires payment record list and detail to retain the paid scene and business type", () => {
  const record = {
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    businessOrderNo: "ORDER-001",
    payScene: "HOME_CLEAN",
    paymentType: "消费支付",
    paymentStatus: "支付成功",
    paymentRequestNo: "ATT-001",
    applicationSourceId: "housekeeping-h5-web",
    latestAttemptStatus: "成功",
    latestAttemptCompletedAt: "2026-10-02 11:59:59",
    channelReturnCode: "WX-OK",
    paymentGateway: null,
    returnParameterType: "渠道交易号",
    parameterValue: "CHANNEL-001",
    expireTime: "2026-10-02 13:00:00",
    paymentAmount: "¥88.00",
    updatedAt: "2026-10-02 12:00:00",
    paidAt: "2026-10-02 12:00:00"
  };
  assert.equal(assertPaymentRecordProjection(
    { total: 1, items: [record] }, record, "PAY-001", "ORDER-001", "HOME_CLEAN", "88.00", "WX-OK", "2026-10-02 13:00:00", "CHANNEL-001"
  ), record);
  const capturedGatewayRecord = { ...record, paymentGateway: "GW_WX" };
  assert.equal(assertPaymentRecordProjection(
    { total: 1, items: [capturedGatewayRecord] }, capturedGatewayRecord,
    "PAY-001", "ORDER-001", "HOME_CLEAN", "88.00", "WX-OK", "2026-10-02 13:00:00", "CHANNEL-001", "GW_WX"
  ), capturedGatewayRecord);
  assert.throws(() => assertPaymentRecordProjection(
    { total: 1, items: [capturedGatewayRecord] }, { ...capturedGatewayRecord, paymentGateway: "GW_ALI" },
    "PAY-001", "ORDER-001", "HOME_CLEAN", "88.00", "WX-OK", "2026-10-02 13:00:00", "CHANNEL-001", "GW_WX"
  ), /captured attempt gateway/);
  assert.throws(() => assertPaymentRecordProjection(
    { total: 1, items: [capturedGatewayRecord] }, capturedGatewayRecord,
    "PAY-001", "ORDER-001", "HOME_CLEAN", "88.00", "WX-OK", "2026-10-02 13:00:00", "CHANNEL-001"
  ), /captured attempt gateway/);
  assert.throws(() => assertPaymentRecordProjection(
    { total: 1, items: [{ ...record, paymentType: "充值支付" }] }, record,
    "PAY-001", "ORDER-001", "HOME_CLEAN", "88.00", "WX-OK", "2026-10-02 13:00:00", "CHANNEL-001"
  ), /type, status/);
  assert.throws(() => assertPaymentRecordProjection(
    { total: 1, items: [record] }, { ...record, payScene: "HOME_REPAIR" },
    "PAY-001", "ORDER-001", "HOME_CLEAN", "88.00", "WX-OK", "2026-10-02 13:00:00", "CHANNEL-001"
  ), /identity and scene/);
  assert.throws(() => assertPaymentRecordProjection(
    { total: 2, items: [record, record] }, record,
    "PAY-001", "ORDER-001", "HOME_CLEAN", "88.00", "WX-OK", "2026-10-02 13:00:00", "CHANNEL-001"
  ), /exactly one record/);
  assert.throws(() => assertPaymentRecordProjection(
    { total: 1, items: [{ ...record, channelReturnCode: "200" }] }, record,
    "PAY-001", "ORDER-001", "HOME_CLEAN", "88.00", "WX-OK", "2026-10-02 13:00:00", "CHANNEL-001"
  ), /channel return code/);
  assert.throws(() => assertPaymentRecordProjection(
    { total: 1, items: [{ ...record, paymentGateway: "微信支付网关" }] }, record,
    "PAY-001", "ORDER-001", "HOME_CLEAN", "88.00", "WX-OK", "2026-10-02 13:00:00", "CHANNEL-001"
  ), /captured attempt gateway/);
  assert.throws(() => assertPaymentRecordProjection(
    { total: 1, items: [{ ...record, paymentRequestNo: "PAY-001-REQ" }] }, record,
    "PAY-001", "ORDER-001", "HOME_CLEAN", "88.00", "WX-OK", "2026-10-02 13:00:00", "CHANNEL-001"
  ), /latest payment attempt/);
  assert.throws(() => assertPaymentRecordProjection(
    { total: 1, items: [record] }, { ...record, expireTime: "2026-10-02 12:00:00" },
    "PAY-001", "ORDER-001", "HOME_CLEAN", "88.00", "WX-OK", "2026-10-02 13:00:00", "CHANNEL-001"
  ), /expiry/);
  for (const wrongParameter of [
    { returnParameterType: "预支付标识" }, { parameterValue: "OTHER-CHANNEL" }
  ]) {
    assert.throws(() => assertPaymentRecordProjection(
      { total: 1, items: [record] }, { ...record, ...wrongParameter },
      "PAY-001", "ORDER-001", "HOME_CLEAN", "88.00", "WX-OK", "2026-10-02 13:00:00", "CHANNEL-001"
    ), /channel transaction parameter/);
  }
});

test("binds the payment record event topic to the persisted success event", () => {
  const record = { callbackMqTopic: "payment.trade.succeeded.v1" };
  const event = { eventTopic: "payment.trade.succeeded.v1" };
  assert.equal(assertPaymentRecordEventTopic(record, event), record);
  assert.throws(
    () => assertPaymentRecordEventTopic({ callbackMqTopic: "payment.payment-result" }, event),
    /event topic did not match/
  );
  assert.throws(() => assertPaymentRecordEventTopic(record, {}), /event topic did not match/);
});

test("requires reconciliation facts to expose the payment scene as a top-level field", () => {
  const snapshot = {
    facts: [{
      systemCode: "accounting-system",
      factType: "ACCOUNTING_POSTED",
      factNo: "EVT-001",
      payScene: "HOME_CLEAN"
    }]
  };
  assert.equal(
    assertReconciliationFactPayScene(snapshot, "accounting-system", "ACCOUNTING_POSTED", "EVT-001", "HOME_CLEAN").payScene,
    "HOME_CLEAN"
  );
  assert.throws(
    () => assertReconciliationFactPayScene(snapshot, "accounting-system", "ACCOUNTING_POSTED", "EVT-001", "HOME_REPAIR"),
    /top-level pay scene/
  );
});

test("requires a newly generated settlement order to remain in the approval queue", () => {
  const lifecycle = { createdAt: "2026-09-28 10:00:00" };
  assert.equal(assertSettlementDraft({
    settlementStatus: "待审核",
    auditStatus: "待审核",
    payoutStatus: "待出款",
    settlementStatusType: "warn",
    auditStatusType: "warn",
    payoutStatusType: "warn",
    ...lifecycle
  }).payoutStatus, "待出款");
  assert.equal(findSettlementOrder([
    { settlementNo: "SET-001", clearingNo: "  CLR-001  ", settlementStatus: "待审核" },
    { settlementNo: "SET-002", clearingNo: "CLR-002", settlementStatus: "待审核" }
  ], "CLR-001").settlementNo, "SET-001");
  assert.throws(
    () => findSettlementOrder([
      { settlementNo: "SET-001", clearingNo: "CLR-001", settlementStatus: "待审核" },
      { settlementNo: "SET-002", clearingNo: "CLR-001", settlementStatus: "待审核" }
    ], "CLR-001"),
    /found duplicate settlement order facts/
  );
  assert.equal(findSettlementOrder([{ clearingNo: "CLR-002", settlementStatus: "待审核" }], "CLR-001"), undefined);
  assert.equal(findSettlementOrder([
    { settlementNo: "SET-001", clearingNo: "CLR-001", businessChainId: "CHAIN-OTHER", settlementStatus: "待审核" }
  ], "CLR-001", "CHAIN-CLR-001"), undefined);
  assert.equal(findSettlementOrder([
    { settlementNo: "SET-001", clearingNo: "CLR-001", businessChainId: "  CHAIN-CLR-001  ", settlementStatus: "待审核" }
  ], "CLR-001", "CHAIN-CLR-001").settlementNo, "SET-001");
  for (const invalidSettlement of [
    { settlementStatus: "待审核", auditStatus: "待审核", payoutStatus: "待出款", settlementStatusType: "warn", auditStatusType: "warn", payoutStatusType: "warn" },
    { settlementStatus: "待审核", auditStatus: "待审核", payoutStatus: "待出款", settlementStatusType: "warn", auditStatusType: "warn", payoutStatusType: "warn", createdAt: "2026-02-30 10:00:00" }
  ]) {
    assert.throws(
      () => assertSettlementDraft(invalidSettlement),
      /usable creation time/
    );
  }
});

test("requires payment identifiers before continuing the cross-system flow", () => {
  const lifecycle = {
    createdAt: "2026-09-28 10:00:00",
    expiresAt: "2026-09-28 10:30:00"
  };
  assert.equal(
    assertPrepayOrder(
      { orderNo: "ORDER-001", prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", amount: "88.00", payScene: "HOME_CLEAN", paymentStatus: "PREPAY_CREATED", cashierStatus: "待支付", cashierStatusType: "warn", ...lifecycle },
      "ORDER-001",
      "88.00",
      "HOME_CLEAN"
    ).paymentOrderId,
    "PAY-001"
  );
  assert.equal(
    assertPrepayOrder(
      { orderNo: "ORDER-001", prepayOrderNo: "PRE-001", paymentOrderId: "  PAY-001  ", businessChainId: "CHAIN-PAY-001", amount: "88.00", paymentStatus: "PREPAY_CREATED", cashierStatus: "待支付", cashierStatusType: "warn", ...lifecycle },
      "ORDER-001",
      "88.00"
    ).paymentOrderId,
    "  PAY-001  "
  );
  assert.equal(
    assertPrepayOrder(
      { orderNo: "ORDER-001", prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", businessChainId: "  CHAIN-PAY-001  ", amount: "88.00", paymentStatus: "PREPAY_CREATED", cashierStatus: "待支付", cashierStatusType: "warn", ...lifecycle },
      "ORDER-001",
      "88.00"
    ).businessChainId,
    "  CHAIN-PAY-001  "
  );
  assert.equal(
    assertPaymentIdentity(
      { paymentOrderId: " PAY-001 ", prepayOrderNo: " PRE-001 ", orderNo: " ORDER-001 " },
      "PAY-001",
      "PRE-001",
      "ORDER-001"
    ).paymentOrderId,
    " PAY-001 "
  );
  assert.throws(
    () => assertPrepayOrder({ prepayOrderNo: "PRE-001" }),
    /did not contain a usable payment order id/
  );
  assert.throws(
    () => assertPrepayOrder({ paymentOrderId: "PAY-001" }),
    /did not contain a usable prepay order number/
  );
  assert.throws(
    () => assertPrepayOrder({ orderNo: "ORDER-002", prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", ...lifecycle }, "ORDER-001"),
    /did not match the requested order/
  );
  assert.throws(
    () => assertPrepayOrder(
      { orderNo: "ORDER-001", prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", amount: "87.99", ...lifecycle },
      "ORDER-001",
      "88.00"
    ),
    /did not match expected amount/
  );
  for (const invalidPrepay of [
    { orderNo: "ORDER-001", prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", amount: "88.00", createdAt: undefined },
    { orderNo: "ORDER-001", prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", amount: "88.00", createdAt: "2026-02-30 10:00:00", expiresAt: "2026-09-28 10:30:00" },
    { orderNo: "ORDER-001", prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", amount: "88.00", createdAt: "2026-09-28 10:30:00", expiresAt: "2026-09-28 10:00:00" }
  ]) {
    assert.throws(
      () => assertPrepayOrder(invalidPrepay, "ORDER-001", "88.00"),
      /usable creation time|expiry time|preceded/
    );
  }
  assert.throws(
    () => assertPrepayOrder(
      { orderNo: "ORDER-001", prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", amount: "88.00", createdAt: "2026-09-28 10:00:00" },
      "ORDER-001",
      "88.00"
    ),
    /usable expiry time/
  );
  assert.throws(
    () => assertPrepayOrder(
      { orderNo: "ORDER-001", prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", amount: "88.00", paymentStatus: "PREPAY_CREATED", cashierStatus: "待支付", cashierStatusType: "warn", ...lifecycle },
      "ORDER-001",
      "88.00",
      "HOME_CLEAN"
    ),
    /did not contain a usable payment scene/
  );
  assert.throws(
    () => assertPrepayOrder(
      { orderNo: "ORDER-001", prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", amount: "88.00", payScene: "HOME_REPAIR", paymentStatus: "PREPAY_CREATED", cashierStatus: "待支付", cashierStatusType: "warn", ...lifecycle },
      "ORDER-001",
      "88.00",
      "HOME_CLEAN"
    ),
    /payment scene did not match request/
  );
  for (const invalidPaymentStatus of [undefined, "WAIT_CALLBACK"]) {
    assert.throws(
      () => assertPrepayOrder(
        { orderNo: "ORDER-001", prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", amount: "88.00", paymentStatus: invalidPaymentStatus, ...lifecycle },
        "ORDER-001",
        "88.00"
      ),
      /was not ready for payment/
    );
  }
  for (const invalidCashierState of [
    { cashierStatus: "支付中", cashierStatusType: "info" },
    { cashierStatus: "待支付", cashierStatusType: "success" },
    { cashierStatus: undefined, cashierStatusType: undefined }
  ]) {
    assert.throws(
      () => assertPrepayOrder(
        { orderNo: "ORDER-001", prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", amount: "88.00", paymentStatus: "PREPAY_CREATED", ...lifecycle, ...invalidCashierState },
        "ORDER-001",
        "88.00"
      ),
      /cashier state was not pending payment/
    );
  }
  for (const invalidAttemptFacts of [
    { latestAttemptStatus: "处理中", latestAttemptStatusType: undefined },
    { latestAttemptStatus: undefined, latestAttemptStatusType: "info" },
    { latestAttemptStatus: "处理中", latestAttemptStatusType: "invalid" },
    { latestAttemptStatus: "成功", latestAttemptStatusType: "success", latestAttemptCompletedAt: "2026-09-28 10:01:00" },
    { latestAttemptStatus: "处理中", latestAttemptStatusType: "info", latestAttemptCompletedAt: "2026-09-28 10:01:00" }
  ]) {
    assert.throws(
      () => assertPrepayOrder(
        { orderNo: "ORDER-001", prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", amount: "88.00", paymentStatus: "PREPAY_CREATED", cashierStatus: "待支付", cashierStatusType: "warn", ...lifecycle, ...invalidAttemptFacts },
        "ORDER-001",
        "88.00"
      ),
      /attempt facts|attempt completion|pending state|completion time/
    );
  }
});

test("requires payment projections to preserve bill and customer facts", () => {
  const payment = { billNo: "BILL-001", customerName: "客户一" };
  assert.equal(assertPaymentBusinessFacts(payment, "BILL-001", "客户一").billNo, "BILL-001");
  assert.equal(
    assertPaymentBusinessFacts({ ...payment, customerName: "客*" }, "BILL-001", "客户一").customerName,
    "客*"
  );
  assert.throws(
    () => assertPaymentBusinessFacts({ ...payment, billNo: "BILL-002" }, "BILL-001", "客户一"),
    /did not preserve bill number/
  );
  assert.throws(
    () => assertPaymentBusinessFacts({ ...payment, customerName: "客户二" }, "BILL-001", "客户一"),
    /did not preserve customer name/
  );
  assert.throws(
    () => assertPaymentBusinessFacts({ billNo: "BILL-001" }, "BILL-001", "客户一"),
    /did not preserve customer name/
  );
});

test("checks the same bill before submission and after successful completion", () => {
  const source = readFileSync(new URL("./cross-system-happy-path.mjs", import.meta.url), "utf8");
  const mainSource = source.slice(source.indexOf("async function main() {"));
  const firstBillCheck = mainSource.indexOf("assertBillPaymentFacts(");
  const secondBillCheck = mainSource.lastIndexOf("assertBillPaymentFacts(");
  assert.ok(firstBillCheck > mainSource.indexOf('const billNo = prepay.billNo.trim();'));
  assert.ok(firstBillCheck < mainSource.indexOf('api(`${paymentBaseUrl}/api/payments/submit`'));
  assert.ok(secondBillCheck > mainSource.indexOf('api(`${paymentBaseUrl}/api/payments/callback/wx_h5`'));
  assert.equal((mainSource.match(/assertBillPaymentFacts\(/g) || []).length, 2);

  const pendingBill = {
    billNo: "BILL-001", orderNo: "ORDER-001", latestPrepayOrderNo: "PRE-001",
    latestPaymentPrepayOrderNo: "PRE-001", latestPaymentOrderId: "PAY-001",
    latestPayScene: "HOME_CLEAN", billAmount: "¥88.00", latestPrepayAmount: "¥88.00",
    latestPaymentAmount: "¥88.00", paidAmount: "¥0.00", unpaidAmount: "¥88.00",
    billStatus: "待支付", billStatusType: "warn", latestPaymentStatus: "PREPAY_CREATED",
    latestPaymentStatusType: "info", latestCashierStatus: "待支付", latestCashierStatusType: "warn"
  };
  const checkPending = (bill) => assertBillPaymentFacts(
    bill, "BILL-001", "ORDER-001", "PRE-001", "PAY-001", "HOME_CLEAN",
    "88.00", "0.00", "88.00", "待支付", "warn", "PREPAY_CREATED"
  );
  assert.equal(checkPending(pendingBill).billNo, "BILL-001");
  assert.throws(() => checkPending({ ...pendingBill, latestPaymentPrepayOrderNo: "PRE-OTHER" }),
    /did not preserve payment identity/);
  assert.throws(() => checkPending({ ...pendingBill, paidAmount: "¥88.00" }),
    /amount did not match expected amount/);
  assert.throws(() => checkPending({ ...pendingBill, latestPaymentStatus: "SUCCESS" }),
    /bill detail state did not match payment/);
  assert.throws(() => checkPending({ ...pendingBill, latestCashierStatusType: "success" }),
    /bill detail state did not match payment/);

  const paidBill = {
    ...pendingBill, paidAmount: "¥88.00", unpaidAmount: "¥0.00",
    billStatus: "已结清", billStatusType: "success", latestPaymentStatus: "SUCCESS",
    latestPaymentStatusType: "success", latestCashierStatus: "支付成功", latestCashierStatusType: "success"
  };
  assert.equal(assertBillPaymentFacts(
    paidBill, "BILL-001", "ORDER-001", "PRE-001", "PAY-001", "HOME_CLEAN",
    "88.00", "88.00", "0.00", "已结清", "success", "SUCCESS"
  ).billStatus, "已结清");
  assert.throws(() => checkPending(paidBill), /amount did not match expected amount/);
});

test("keeps the full bill receivable separate from a partial standard-order payment", () => {
  const partialBill = {
    billNo: "BILL-PARTIAL", orderNo: "ORDER-PARTIAL",
    latestPrepayOrderNo: "PRE-PARTIAL", latestPaymentPrepayOrderNo: "PRE-PARTIAL",
    latestPaymentOrderId: "PAY-PARTIAL", latestPayScene: "HOME_CLEAN",
    billAmount: "¥88.00", latestPrepayAmount: "¥68.00", latestPaymentAmount: "¥68.00",
    paidAmount: "¥20.00", unpaidAmount: "¥68.00", billStatus: "部分支付",
    billStatusType: "warn", latestPaymentStatus: "PREPAY_CREATED",
    latestPaymentStatusType: "info", latestCashierStatus: "待支付", latestCashierStatusType: "warn"
  };
  const checkPartial = (bill) => assertBillPaymentFacts(
    bill, "BILL-PARTIAL", "ORDER-PARTIAL", "PRE-PARTIAL", "PAY-PARTIAL", "HOME_CLEAN",
    "68.00", "20.00", "68.00", "部分支付", "warn", "PREPAY_CREATED", "88.00"
  );
  assert.equal(checkPartial(partialBill).billNo, "BILL-PARTIAL");
  assert.throws(() => checkPartial({ ...partialBill, billAmount: "¥68.00" }), /amount did not match/);
  assert.throws(() => checkPartial({ ...partialBill, latestPaymentAmount: "¥88.00" }), /amount did not match/);
  assert.throws(() => checkPartial({ ...partialBill, paidAmount: "¥0.00" }), /amount did not match/);
  assert.throws(() => checkPartial({ ...partialBill, billStatus: "待支付" }), /bill detail state/);
  const settledBill = {
    ...partialBill, paidAmount: "¥88.00", unpaidAmount: "¥0.00", billStatus: "已结清",
    billStatusType: "success", latestPaymentStatus: "SUCCESS",
    latestPaymentStatusType: "success", latestCashierStatus: "支付成功", latestCashierStatusType: "success"
  };
  assert.equal(assertBillPaymentFacts(
    settledBill, "BILL-PARTIAL", "ORDER-PARTIAL", "PRE-PARTIAL", "PAY-PARTIAL", "HOME_CLEAN",
    "68.00", "88.00", "0.00", "已结清", "success", "SUCCESS", "88.00"
  ).billStatus, "已结清");
});

test("query-chain replaces only payment completion and verifies the gateway audit", () => {
  const source = readFileSync(new URL("./cross-system-happy-path.mjs", import.meta.url), "utf8");
  const mainSource = source.slice(source.indexOf("async function main() {"));
  assert.ok(mainSource.indexOf("await verifyLocalDemoTargets(")
    < mainSource.indexOf('api(`${upstreamBaseUrl}/arm`'));
  assert.ok(mainSource.indexOf('api(`${upstreamBaseUrl}/health`)')
    < mainSource.indexOf('api(`${upstreamBaseUrl}/arm`'));
  assert.match(mainSource, /if \(queryMode\) \{[\s\S]*api\(`\$\{paymentBaseUrl\}\/api\/payments\/admin\/query`/);
  assert.match(mainSource, /queryFailure\?\.message\?\.includes\("PAYMENT-1026"\)/);
  assert.ok(mainSource.indexOf("rejectedEvents.total === 0")
    < mainSource.indexOf("paid = assertPaymentIdentity(await api(`${paymentBaseUrl}/api/payments/admin/query`"));
  assert.match(mainSource, /gatewayAudits\.records\?\.filter\(\(item\) => item\.operationType === "PAYMENT_QUERY"\)/);
  assert.match(mainSource, /\} else \{\s+const callbackPayload/);
});

test("requires a payment-ready cashier snapshot before submission", () => {
  const validCashier = {
    prepayOrderNo: "PRE-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    amount: "88.00",
    payScene: "HOME_CLEAN",
    status: "待支付",
    statusType: "warn",
    paymentStatus: "PREPAY_CREATED",
    expiresAt: "2026-09-27 12:00:00",
    channels: ["微信支付"]
  };
  assert.equal(
    assertCashierPage(validCashier, "PRE-001", "PAY-001", "ORDER-001", "88.00", validCashier.expiresAt, "HOME_CLEAN").paymentOrderId,
    "PAY-001"
  );
  assert.equal(
    assertCashierPage({ ...validCashier, businessChainId: "  CHAIN-PAY-001  " }, "PRE-001", "PAY-001", "ORDER-001", "88.00", validCashier.expiresAt).businessChainId,
    "  CHAIN-PAY-001  "
  );
  assert.equal(
    assertCashierPage({ ...validCashier, paymentOrderId: "  PAY-001  " }, "PRE-001", "  PAY-001  ", "ORDER-001", "88.00", validCashier.expiresAt).paymentOrderId,
    "  PAY-001  "
  );
  for (const invalidCashier of [
    { ...validCashier, paymentOrderId: "PAY-002" },
    { ...validCashier, status: "支付中" },
    { ...validCashier, statusType: "success" },
    { ...validCashier, statusType: undefined },
    { ...validCashier, paymentStatus: "WAIT_CALLBACK" },
    { ...validCashier, expiresAt: "" },
    { ...validCashier, expiresAt: "2026-02-30 12:00:00" },
    { ...validCashier, channels: [] },
    { ...validCashier, channels: ["未知渠道"] },
    { ...validCashier, channels: ["微信支付", "未知渠道"] }
  ]) {
    assert.throws(
      () => assertCashierPage(invalidCashier, "PRE-001", "PAY-001", "ORDER-001", "88.00", validCashier.expiresAt),
      /cashier response/
    );
  }
  for (const invalidAttemptFacts of [
    { latestAttemptStatus: "处理中", latestAttemptStatusType: undefined },
    { latestAttemptStatus: undefined, latestAttemptStatusType: "info" },
    { latestAttemptStatus: "成功", latestAttemptStatusType: "success", latestAttemptCompletedAt: "2026-09-27 12:00:01" },
    { latestAttemptStatus: "处理中", latestAttemptStatusType: "info", latestAttemptCompletedAt: "2026-09-27 12:00:01" }
  ]) {
    assert.throws(
      () => assertCashierPage({ ...validCashier, ...invalidAttemptFacts }, "PRE-001", "PAY-001", "ORDER-001", "88.00", validCashier.expiresAt),
      /cashier response/
    );
  }
  assert.throws(
    () => assertCashierPage(validCashier, "PRE-001", "PAY-001", "ORDER-001", "88.00", "2026-09-27 12:01:00"),
    /expiry time did not match prepay order/
  );
  assert.throws(
    () => assertCashierPage({ ...validCashier, payScene: undefined }, "PRE-001", "PAY-001", "ORDER-001", "88.00", validCashier.expiresAt, "HOME_CLEAN"),
    /did not contain a usable payment scene/
  );
  assert.throws(
    () => assertCashierPage({ ...validCashier, payScene: "HOME_REPAIR" }, "PRE-001", "PAY-001", "ORDER-001", "88.00", validCashier.expiresAt, "HOME_CLEAN"),
    /payment scene did not match request/
  );
});

test("requires accepted and successful cashier reads to preserve the original payment facts", () => {
  const detail = {
    prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001", payScene: "HOME_CLEAN", amount: "¥88.00",
    status: "WAIT_CALLBACK", paymentMethod: "支付宝", channel: "alipay_h5",
    channelTransactionNo: "ALI-001", latestAttemptStatus: "处理中",
    latestAttemptStatusType: "info", latestAttemptCompletedAt: null
  };
  const cashier = {
    ...detail, paymentStatus: "WAIT_CALLBACK", status: "待回调", statusType: "warn"
  };
  assert.equal(assertBoundCashierPage(cashier, detail, "WAIT_CALLBACK", "88.00"), cashier);
  for (const invalidCashier of [
    { ...cashier, paymentStatus: "PREPAY_CREATED" },
    { ...cashier, paymentMethod: "微信支付" },
    { ...cashier, paymentMethod: "支付宝 " },
    { ...cashier, channel: "ALIPAY_H5" },
    { ...cashier, channelTransactionNo: "ALI-002" },
    { ...cashier, latestAttemptStatus: "成功" },
    { ...cashier, paymentOrderId: "PAY-OTHER" }
  ]) {
    assert.throws(() => assertBoundCashierPage(invalidCashier, detail, "WAIT_CALLBACK", "88.00"));
  }
  const paidDetail = {
    ...detail, status: "SUCCESS", latestAttemptStatus: "成功",
    latestAttemptStatusType: "success", latestAttemptCompletedAt: "2026-10-10 00:01:00"
  };
  const paidCashier = {
    ...paidDetail, paymentStatus: "SUCCESS", status: "支付成功", statusType: "success"
  };
  assert.equal(assertBoundCashierPage(paidCashier, paidDetail, "SUCCESS", "88.00"), paidCashier);
  for (const invalidCashier of [
    { ...paidCashier, paymentMethod: "" },
    { ...paidCashier, channelTransactionNo: null },
    { ...paidCashier, latestAttemptCompletedAt: null },
    { ...paidCashier, status: "待回调" }
  ]) {
    assert.throws(() => assertBoundCashierPage(invalidCashier, paidDetail, "SUCCESS", "88.00"));
  }
});

test("keeps payment and prepay identities continuous across the payment flow", () => {
  assert.equal(
    assertPaymentIdentity(
      { paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", orderNo: "ORDER-001" },
      "PAY-001",
      "PRE-001",
      "ORDER-001"
    ).paymentOrderId,
    "PAY-001"
  );
  assert.equal(
    assertPaymentIdentity(
      { paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", orderNo: "ORDER-001", businessChainId: "CHAIN-PAY-001" },
      "PAY-001",
      "PRE-001",
      "ORDER-001",
      "CHAIN-PAY-001"
    ).businessChainId,
    "CHAIN-PAY-001"
  );
  assert.equal(
    assertPaymentIdentity(
      { paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", orderNo: "ORDER-001", businessChainId: "  CHAIN-PAY-001  " },
      "PAY-001",
      "PRE-001",
      "ORDER-001",
      "CHAIN-PAY-001"
    ).businessChainId,
    "  CHAIN-PAY-001  "
  );
  assert.equal(
    assertPaymentIdentity(
      { paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", orderNo: "ORDER-001", businessChainId: "CHAIN-PAY-001", payScene: " HOME_CLEAN " },
      "PAY-001",
      "PRE-001",
      "ORDER-001",
      "CHAIN-PAY-001",
      "HOME_CLEAN"
    ).payScene,
    " HOME_CLEAN "
  );
  assert.throws(
    () => assertPaymentIdentity(
      { paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", orderNo: "ORDER-001", businessChainId: "CHAIN-PAY-001" },
      "PAY-001",
      "PRE-001",
      "ORDER-001",
      "CHAIN-PAY-001",
      "HOME_CLEAN"
    ),
    /did not contain a usable payment scene/
  );
  assert.throws(
    () => assertPaymentIdentity(
      { paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", orderNo: "ORDER-001", businessChainId: "CHAIN-PAY-001", payScene: "HOME_REPAIR" },
      "PAY-001",
      "PRE-001",
      "ORDER-001",
      "CHAIN-PAY-001",
      "HOME_CLEAN"
    ),
    /payment scene did not match request/
  );
  assert.throws(
    () => assertPaymentIdentity(
      { paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", orderNo: "ORDER-001", businessChainId: "CHAIN-OTHER" },
      "PAY-001",
      "PRE-001",
      "ORDER-001",
      "CHAIN-PAY-001"
    ),
    /did not match business chain/
  );
  assert.throws(
    () => assertPaymentIdentity({ paymentOrderId: "PAY-002", prepayOrderNo: "PRE-001" }, "PAY-001", "PRE-001"),
    /did not match payment order/
  );
  assert.throws(
    () => assertPaymentIdentity({ paymentOrderId: "PAY-001", prepayOrderNo: "PRE-002" }, "PAY-001", "PRE-001"),
    /did not match prepay order/
  );
  assert.throws(
    () => assertPaymentIdentity(
      { paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", orderNo: "ORDER-002" },
      "PAY-001",
      "PRE-001",
      "ORDER-001"
    ),
    /did not match business order/
  );
});

test("requires payment submission responses to preserve identity and amount", () => {
  const submission = {
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    prepayOrderNo: "PRE-001",
    orderNo: "ORDER-001",
    amount: "88.00",
    payScene: "HOME_CLEAN",
    paymentStatus: "WAIT_CALLBACK",
    cashierStatus: "支付中",
    cashierStatusType: "info",
    paymentMethod: "微信支付",
    channel: "wx_h5",
    channelTransactionNo: "WX-001",
    latestAttemptStatus: "处理中",
    latestAttemptStatusType: "info",
    createdAt: "2026-09-27 11:59:00",
    expiresAt: "2026-09-27 12:00:00",
    businessChainId: "CHAIN-PAY-001"
  };
  assert.equal(
    assertPaymentSubmission(
      submission, "PAY-001", "PRE-001", "ORDER-001", "88.00", submission.expiresAt,
      "CHAIN-PAY-001", "微信支付", "wx_h5", "HOME_CLEAN"
    ).paymentOrderId,
    "PAY-001"
  );
  assert.throws(
    () => assertPaymentSubmission(
      { ...submission, channel: "alipay_h5" },
      "PAY-001", "PRE-001", "ORDER-001", "88.00", submission.expiresAt,
      "CHAIN-PAY-001", "微信支付", "wx_h5"
    ),
    /channel did not match request/
  );
  assert.throws(
    () => assertPaymentSubmission(
      { ...submission, businessChainId: "CHAIN-OTHER" },
      "PAY-001", "PRE-001", "ORDER-001", "88.00", submission.expiresAt, "CHAIN-PAY-001"
    ),
    /did not match business chain/
  );
  assert.throws(
    () => assertPaymentSubmission({ ...submission, paymentOrderId: "PAY-002" }, "PAY-001", "PRE-001", "ORDER-001", "88.00"),
    /did not match payment order/
  );
  assert.throws(
    () => assertPaymentSubmission({ ...submission, amount: "87.99" }, "PAY-001", "PRE-001", "ORDER-001", "88.00"),
    /did not match expected amount/
  );
  assert.throws(
    () => assertPaymentSubmission({ ...submission, paymentStatus: "SUCCESS" }, "PAY-001", "PRE-001", "ORDER-001", "88.00"),
    /did not remain pending callback/
  );
  assert.throws(
    () => assertPaymentSubmission({ ...submission, paymentStatus: "PREPAY_CREATED" }, "PAY-001", "PRE-001", "ORDER-001", "88.00"),
    /did not remain pending callback/
  );
  assert.throws(
    () => assertPaymentSubmission({ ...submission, cashierStatusType: "success" }, "PAY-001", "PRE-001", "ORDER-001", "88.00"),
    /cashier state was not pending callback/
  );
  assert.throws(
    () => assertPaymentSubmission(
      {
        ...submission,
        latestAttemptStatus: "成功",
        latestAttemptStatusType: "success",
        latestAttemptCompletedAt: "2026-09-27 12:00:01"
      },
      "PAY-001", "PRE-001", "ORDER-001", "88.00"
    ),
    /pending state carried terminal attempt facts/
  );
  assert.throws(
    () => assertPaymentSubmission(
      { ...submission, latestAttemptCompletedAt: "2026-09-27 12:00:01" },
      "PAY-001", "PRE-001", "ORDER-001", "88.00"
    ),
    /non-successful attempt carried completion time/
  );
  for (const invalidSubmission of [
    { ...submission, paymentMethod: undefined },
    { ...submission, channel: undefined },
    { ...submission, channelTransactionNo: undefined },
    { ...submission, latestAttemptStatus: undefined },
    { ...submission, latestAttemptStatusType: "invalid" },
    { ...submission, latestAttemptStatus: "失败", latestAttemptStatusType: "success" }
  ]) {
    assert.throws(
      () => assertPaymentSubmission(invalidSubmission, "PAY-001", "PRE-001", "ORDER-001", "88.00"),
      /channel facts|attempt facts/
    );
  }
  for (const invalidSubmission of [
    { ...submission, createdAt: undefined },
    { ...submission, createdAt: "2026-02-30 11:59:00" }
  ]) {
    assert.throws(
      () => assertPaymentSubmission(invalidSubmission, "PAY-001", "PRE-001", "ORDER-001", "88.00"),
      /did not contain a usable creation time/
    );
  }
  assert.throws(
    () => assertPaymentSubmission({ ...submission, expiresAt: "" }, "PAY-001", "PRE-001", "ORDER-001", "88.00"),
    /did not contain a usable expiry time/
  );
  assert.throws(
    () => assertPaymentSubmission({ ...submission, expiresAt: "2026-02-30 12:00:00" }, "PAY-001", "PRE-001", "ORDER-001", "88.00"),
    /did not contain a usable expiry time/
  );
  assert.throws(
    () => assertPaymentSubmission(submission, "PAY-001", "PRE-001", "ORDER-001", "88.00", "2026-09-27 12:01:00"),
    /expiry time did not match prepay order/
  );
  assert.throws(
    () => assertPaymentSubmission(
      { ...submission, createdAt: "2026-09-27 12:00:00", expiresAt: "2026-09-27 11:59:00" },
      "PAY-001",
      "PRE-001",
      "ORDER-001",
      "88.00"
    ),
    /expiry time preceded/
  );
  assert.throws(
    () => assertPaymentSubmission({ ...submission, payScene: undefined }, "PAY-001", "PRE-001", "ORDER-001", "88.00", submission.expiresAt, "CHAIN-PAY-001", "微信支付", "wx_h5", "HOME_CLEAN"),
    /did not contain a usable payment scene/
  );
  assert.throws(
    () => assertPaymentSubmission({ ...submission, payScene: "HOME_REPAIR" }, "PAY-001", "PRE-001", "ORDER-001", "88.00", submission.expiresAt, "CHAIN-PAY-001", "微信支付", "wx_h5", "HOME_CLEAN"),
    /payment scene did not match request/
  );
});

test("rejects terminal attempt statuses in pending prepay, cashier and submission snapshots", () => {
  const prepay = {
    orderNo: "ORDER-001", prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001", amount: "88.00", paymentStatus: "PREPAY_CREATED",
    cashierStatus: "待支付", cashierStatusType: "warn",
    createdAt: "2026-09-27 11:59:00", expiresAt: "2026-09-27 12:00:00"
  };
  const cashier = {
    ...prepay, status: "待支付", statusType: "warn", channels: ["微信支付"]
  };
  const submission = {
    ...prepay, paymentStatus: "WAIT_CALLBACK", cashierStatus: "支付中",
    cashierStatusType: "info", paymentMethod: "微信支付", channel: "wx_h5",
    channelTransactionNo: "WX-001"
  };
  for (const status of ["成功", "支付成功", " 支付成功 ", " SUCCESS ", "success", "失败", "支付失败", " FAIL ", "fAiLeD"]) {
    const attempt = { latestAttemptStatus: status, latestAttemptStatusType: "danger" };
    assert.throws(() => assertPrepayOrder({ ...prepay, ...attempt }, "ORDER-001", "88.00"),
      /terminal attempt facts/, `prepay accepted ${status}`);
    assert.throws(() => assertCashierPage({ ...cashier, ...attempt }, "PRE-001", "PAY-001", "ORDER-001", "88.00"),
      /terminal attempt facts/, `cashier accepted ${status}`);
    assert.throws(() => assertPaymentSubmission({ ...submission, ...attempt }, "PAY-001", "PRE-001", "ORDER-001", "88.00"),
      /terminal attempt facts/, `submission accepted ${status}`);
  }
});

test("requires repeated successful callbacks to return the original payment entity", () => {
  const first = {
    paymentOrderId: "PAY-001",
    prepayOrderNo: "PRE-001",
    orderNo: "ORDER-001",
    paymentMethod: "微信支付",
    channel: "WX_H5",
    channelTransactionNo: "WX-001",
    amount: "88.00",
    createdAt: "2026-09-27 11:59:00",
    status: "SUCCESS",
    statusType: "success",
    successAt: "2026-09-27 12:01:01",
    latestAttemptStatus: "成功",
    latestAttemptStatusType: "success",
    latestAttemptCompletedAt: "2026-09-27 12:01:01",
    terminalAt: "2026-09-27 12:01:01",
    businessChainId: "CHAIN-PAY-001"
  };
  assert.equal(
    assertIdempotentPaymentCallback(first, { ...first, amount: "88.00" }, "  PAY-001  ", "PRE-001", "ORDER-001", "88.00", "微信支付", "WX_H5").status,
    "SUCCESS"
  );
  assert.throws(
    () => assertIdempotentPaymentCallback(
      { ...first, channel: "ALIPAY_H5" },
      { ...first, channel: "ALIPAY_H5" },
      "PAY-001", "PRE-001", "ORDER-001", "88.00", "微信支付", "WX_H5"
    ),
    /channel did not match the submitted payment/
  );
  assert.equal(
    assertPaymentSuccessDetail(first, { ...first }, "PAY-001", "PRE-001", "ORDER-001", "88.00").status,
    "SUCCESS"
  );
  assert.throws(
    () => assertPaymentSuccessDetail(
      first,
      { ...first, successAt: "2026-09-27 12:01:02" },
      "PAY-001",
      "PRE-001",
      "ORDER-001",
      "88.00"
    ),
    /payment detail did not preserve callback success time/
  );
  assert.equal(
    assertIdempotentPaymentCallback(first, { ...first, channelTransactionNo: "  WX-001  ", amount: "88.00" }, "PAY-001", "PRE-001", "ORDER-001", "88.00").channelTransactionNo,
    "  WX-001  "
  );
  assert.throws(
    () => assertIdempotentPaymentCallback(
      first,
      { ...first, channelTransactionNo: "WX-002", amount: "88.00" },
      "PAY-001",
      "PRE-001",
      "ORDER-001",
      "88.00"
    ),
    /changed the original payment entity/
  );
  assert.throws(
    () => assertIdempotentPaymentCallback(
      first,
      { ...first, channel: "ALIPAY_H5" },
      "PAY-001",
      "PRE-001",
      "ORDER-001",
      "88.00"
    ),
    /changed the original payment entity/
  );
  assert.throws(
    () => assertIdempotentPaymentCallback(
      first,
      { ...first, paymentMethod: "支付宝" },
      "PAY-001",
      "PRE-001",
      "ORDER-001",
      "88.00"
    ),
    /changed the original payment entity/
  );
  assert.throws(
    () => assertIdempotentPaymentCallback(
      { ...first, latestAttemptCompletedAt: "2026-09-27 12:01:02" },
      { ...first, latestAttemptCompletedAt: "2026-09-27 12:01:02" },
      "PAY-001",
      "PRE-001",
      "ORDER-001",
      "88.00"
    ),
    /latest attempt completion followed its success time/
  );
  assert.throws(
    () => assertIdempotentPaymentCallback(
      first,
      { ...first, amount: "87.99" },
      "PAY-001",
      "PRE-001",
      "ORDER-001",
      "88.00"
    ),
    /did not match expected amount/
  );
  for (const invalidTerminalDisplay of [
    { ...first, statusType: "warn" },
    { ...first, businessChainId: "CHAIN-OTHER" }
  ]) {
    assert.throws(
      () => assertIdempotentPaymentCallback(
        invalidTerminalDisplay,
        first,
        "PAY-001",
        "PRE-001",
        "ORDER-001",
        "88.00"
      ),
      invalidTerminalDisplay.businessChainId === "CHAIN-OTHER"
        ? /did not match business chain/
        : /terminal display state was inconsistent/
    );
  }
  assert.throws(
    () => assertIdempotentPaymentCallback(
      { ...first, channelTransactionNo: "" },
      { ...first, channelTransactionNo: "" },
      "PAY-001",
      "PRE-001",
      "ORDER-001",
      "88.00"
    ),
    /changed the original payment entity/
  );
  assert.throws(
    () => assertIdempotentPaymentCallback(
      { ...first, channelTransactionNo: undefined },
      first,
      "PAY-001",
      "PRE-001",
      "ORDER-001",
      "88.00"
    ),
    /changed the original payment entity/
  );
  assert.throws(
    () => assertIdempotentPaymentCallback(
      { ...first, channel: "" },
      { ...first, channel: "" },
      "PAY-001",
      "PRE-001",
      "ORDER-001",
      "88.00"
    ),
    /changed the original payment entity/
  );
  assert.throws(
    () => assertIdempotentPaymentCallback(
      { ...first, createdAt: undefined },
      first,
      "PAY-001",
      "PRE-001",
      "ORDER-001",
      "88.00"
    ),
    /did not contain a usable creation time/
  );
  assert.throws(
    () => assertIdempotentPaymentCallback(
      { ...first, createdAt: "2026-02-30 11:59:00" },
      first,
      "PAY-001",
      "PRE-001",
      "ORDER-001",
      "88.00"
    ),
    /did not contain a usable creation time/
  );
  assert.throws(
    () => assertIdempotentPaymentCallback(
      { ...first, successAt: undefined },
      first,
      "PAY-001",
      "PRE-001",
      "ORDER-001",
      "88.00"
    ),
    /did not contain a usable success time/
  );
  assert.throws(
    () => assertIdempotentPaymentCallback(
      { ...first, successAt: "2026-02-30 12:01:00" },
      first,
      "PAY-001",
      "PRE-001",
      "ORDER-001",
      "88.00"
    ),
    /did not contain a usable success time/
  );
  assert.throws(
    () => assertIdempotentPaymentCallback(
      { ...first, successAt: "2026-09-27 11:58:00" },
      first,
      "PAY-001",
      "PRE-001",
      "ORDER-001",
      "88.00"
    ),
    /success time preceded its creation time/
  );
  assert.throws(
    () => assertIdempotentPaymentCallback(
      first,
      { ...first, createdAt: "2026-09-27 12:02:00", successAt: "2026-09-27 12:01:00" },
      "PAY-001",
      "PRE-001",
      "ORDER-001",
      "88.00"
    ),
    /repeated payment callback success time preceded its creation time/
  );
  for (const invalidAttempt of [
    { latestAttemptStatus: undefined },
    { latestAttemptStatusType: "warn" },
    { latestAttemptCompletedAt: undefined },
    { latestAttemptCompletedAt: "2026-09-27 11:58:00" }
  ]) {
    assert.throws(
      () => assertIdempotentPaymentCallback(
        { ...first, ...invalidAttempt },
        first,
        "PAY-001",
        "PRE-001",
        "ORDER-001",
        "88.00"
      ),
      /latest attempt (was not successful|completion)/
    );
  }
});

test("requires repeated payment submissions to return the original entity", () => {
  const first = {
    paymentOrderId: "PAY-001",
    prepayOrderNo: "PRE-001",
    orderNo: "ORDER-001",
    amount: "88.00",
    status: "WAIT_CALLBACK",
    statusType: "warn",
    paymentStatus: "WAIT_CALLBACK",
    cashierStatus: "支付中",
    cashierStatusType: "info",
    paymentMethod: "微信支付",
    channel: "wx_h5",
    channelTransactionNo: "WX-001",
    latestAttemptStatus: "处理中",
    latestAttemptStatusType: "info",
    createdAt: "2026-09-27 11:59:00",
    expiresAt: "2026-09-27 12:00:00",
    businessChainId: "CHAIN-PAY-001"
  };
  assert.equal(
    assertIdempotentPaymentSubmission(
      first,
      { ...first, amount: "¥88.00" },
      "PAY-001",
      "PRE-001",
      "ORDER-001",
      "88.00",
      undefined,
      "微信支付",
      "wx_h5"
    ).paymentOrderId,
    "PAY-001"
  );
  assert.equal(
    assertIdempotentPaymentSubmission(
      first,
      { ...first, paymentOrderId: "  PAY-001  ", amount: "¥88.00" },
      "PAY-001",
      "PRE-001",
      "ORDER-001",
      "88.00"
    ).paymentOrderId,
    "  PAY-001  "
  );
  assert.throws(
    () => assertIdempotentPaymentSubmission(
      first,
      { ...first, paymentOrderId: "PAY-002" },
      "PAY-001",
      "PRE-001",
      "ORDER-001",
      "88.00"
    ),
    /did not match payment order/
  );
  for (const changedFact of [
    { paymentMethod: "支付宝" },
    { channel: "alipay_h5" },
    { channelTransactionNo: "WX-002" },
    { latestAttemptStatus: "失败", latestAttemptStatusType: "danger" },
    { createdAt: "2026-09-27 11:59:01" },
    { expiresAt: "2026-09-27 12:00:01" }
  ]) {
    assert.throws(
      () => assertIdempotentPaymentSubmission(
        first,
        { ...first, ...changedFact },
        "PAY-001",
        "PRE-001",
        "ORDER-001",
        "88.00"
      ),
      changedFact.latestAttemptStatus ? /terminal attempt facts/ : /did not return the original payment entity/
    );
  }
});

test("requires post-success submission to retain payment facts", () => {
  const detail = {
    paymentOrderId: "PAY-001", amount: "¥88.00", paymentMethod: "微信支付",
    channel: "wx_h5", channelTransactionNo: "WX-001",
    latestAttemptCompletedAt: "2026-10-02 10:00:00", successAt: "2026-10-02 10:00:00"
  };
  const repeated = {
    paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", orderNo: "ORDER-001",
    businessChainId: "CHAIN-PAY-001", amount: "¥88.00", paymentStatus: "SUCCESS",
    cashierStatus: "支付成功", cashierStatusType: "success", paymentMethod: "微信支付",
    channel: "wx_h5", channelTransactionNo: "WX-001", latestAttemptStatus: "成功",
    latestAttemptStatusType: "success", latestAttemptCompletedAt: detail.latestAttemptCompletedAt,
    successAt: detail.successAt
  };
  assert.equal(assertSuccessfulPaymentResubmission(
    detail, repeated, "PRE-001", "ORDER-001", "CHAIN-PAY-001"
  ), repeated);
  for (const changedFact of [
    { paymentStatus: "WAIT_CALLBACK" },
    { cashierStatus: "支付中" },
    { channelTransactionNo: "WX-OTHER" },
    { latestAttemptStatus: "处理中" },
    { latestAttemptCompletedAt: "2026-10-02 10:00:01" },
    { successAt: null }
  ]) {
    assert.throws(() => assertSuccessfulPaymentResubmission(
      detail, { ...repeated, ...changedFact }, "PRE-001", "ORDER-001", "CHAIN-PAY-001"
    ));
  }
});

test("requires the payment amount to match the requested amount", () => {
  assert.equal(assertPaymentAmount({ amount: "¥88.00" }, "88.00").amount, "¥88.00");
  assert.equal(
    assertPaymentAmount({ amount: "9007199254740992.01" }, "9007199254740992.01").amount,
    "9007199254740992.01"
  );
  assert.throws(
    () => assertPaymentAmount({ amount: "9007199254740992.01" }, "9007199254740992.02"),
    /did not match expected amount/
  );
  assert.throws(
    () => assertPaymentAmount({ amount: "¥87.99" }, "88.00"),
    /did not match expected amount/
  );
  assert.throws(
    () => assertPaymentAmount({ amount: "not-a-number" }, "88.00"),
    /did not match expected amount/
  );
  assert.throws(
    () => assertPaymentAmount({ amount: "88oops" }, "88.00"),
    /did not match expected amount/
  );
  assert.throws(
    () => assertPaymentAmount({ amount: ".5" }, "0.50"),
    /did not match expected amount/
  );
});

test("requires the refund amount to remain continuous across refund stages", () => {
  assert.equal(assertRefundAmount({ refundAmount: "¥18.00" }, "18.00").refundAmount, "¥18.00");
  assert.equal(assertRefundAmount({ refundAmount: 18 }, "18.00").refundAmount, 18);
  assert.throws(
    () => assertRefundAmount({ refundAmount: "¥17.99" }, "18.00"),
    /did not match expected amount/
  );
  assert.throws(
    () => assertRefundAmount({ refundAmount: "not-a-number" }, "18.00"),
    /did not match expected amount/
  );
  assert.throws(
    () => assertRefundAmount({ refundAmount: "18oops" }, "18.00"),
    /did not match expected amount/
  );
});

test("requires refund responses to preserve the projected customer name", () => {
  assert.equal(
    assertRefundCustomerName({ customerName: "local happy path" }, "local happy path").customerName,
    "local happy path"
  );
  assert.equal(
    assertRefundCustomerName({ customerName: "l*" }, "local happy path", "detail").customerName,
    "l*"
  );
  assert.throws(
    () => assertRefundCustomerName({ customerName: "another customer" }, "local happy path", "callback"),
    /refund callback did not preserve customer name/
  );
  assert.throws(
    () => assertRefundCustomerName({}, "local happy path"),
    /refund response did not preserve customer name/
  );
});

test("requires refund responses to preserve the payment scene", () => {
  assert.equal(
    assertRefundPayScene({ payScene: " HOME_CLEAN " }, "HOME_CLEAN").payScene,
    " HOME_CLEAN "
  );
  assert.throws(
    () => assertRefundPayScene({ payScene: "HOME_REPAIR" }, "HOME_CLEAN", "callback"),
    /refund callback did not preserve payment scene/
  );
  assert.throws(
    () => assertRefundPayScene({}, "HOME_CLEAN"),
    /refund response did not preserve payment scene/
  );
});

test("requires repeated refund applications to return the original entity", () => {
  const first = {
    refundOrderId: "REF-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    refundAmount: "18.00",
    status: "REVIEWING",
    statusType: "warn",
    appliedAt: "2026-09-27 12:00:00"
  };
  assert.equal(
    assertIdempotentRefundApplication(first, { ...first, refundAmount: "¥18.00" }, "PAY-001", "18.00", "ORDER-001").refundOrderId,
    "REF-001"
  );
  assert.throws(
    () => assertIdempotentRefundApplication(first, { ...first, refundOrderId: "REF-002" }, "PAY-001", "18.00", "ORDER-001"),
    /did not return the original refund entity/
  );
  assert.throws(
    () => assertIdempotentRefundApplication(first, { ...first, status: "APPROVED" }, "PAY-001", "18.00", "ORDER-001"),
    /did not reach REVIEWING/
  );
  assert.throws(
    () => assertIdempotentRefundApplication(
      { ...first, channelSubmittedAt: "2026-09-27 12:00:30" },
      { ...first, channelSubmittedAt: "2026-09-27 12:00:31" },
      "PAY-001",
      "18.00",
      "ORDER-001"
    ),
    /changed the original channelSubmittedAt fact/
  );
});

test("requires repeated successful refund callbacks to return the original entity", () => {
  const first = {
    refundOrderId: "REF-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    refundAmount: "18.00",
    status: "SUCCESS",
    statusType: "success",
    channelRefundId: "CHANNEL-REF-001",
    channelCode: "wx_h5",
    appliedAt: "2026-09-27 12:00:00",
    channelSubmittedAt: "2026-09-27 12:00:30",
    successAt: "2026-09-27 12:01:00",
    terminalAt: "2026-09-27 12:01:00"
  };
  assert.equal(
    assertIdempotentRefundCallback(first, { ...first }, "REF-001", "PAY-001", "18.00", "ORDER-001").status,
    "SUCCESS"
  );
  assert.equal(
    assertRefundSuccessDetail(first, { ...first }, "REF-001", "PAY-001", "18.00", "ORDER-001").status,
    "SUCCESS"
  );
  assert.throws(
    () => assertRefundSuccessDetail(
      first,
      { ...first, terminalAt: "2026-09-27 12:01:01" },
      "REF-001",
      "PAY-001",
      "18.00",
      "ORDER-001"
    ),
    /refund detail did not preserve callback terminal time|repeated callback changed the original terminalAt fact/
  );
  assert.equal(
    assertIdempotentRefundCallback(
      first,
      { ...first, channelRefundId: "  CHANNEL-REF-001  " },
      "REF-001",
      "PAY-001",
      "18.00",
      "ORDER-001"
    ).channelRefundId,
    "  CHANNEL-REF-001  "
  );
  assert.throws(
    () => assertIdempotentRefundCallback(first, { ...first, refundOrderId: "REF-002" }, "REF-001", "PAY-001", "18.00", "ORDER-001"),
    /did not match refund order/
  );
  assert.throws(
    () => assertIdempotentRefundCallback(first, { ...first, channelRefundId: "CHANNEL-REF-002" }, "REF-001", "PAY-001", "18.00", "ORDER-001"),
    /changed the original channel refund identifier/
  );
  assert.throws(
    () => assertIdempotentRefundCallback(first, { ...first, channelCode: "alipay_h5" }, "REF-001", "PAY-001", "18.00", "ORDER-001"),
    /changed the original channel code/
  );
  assert.throws(
    () => assertIdempotentRefundCallback(first, { ...first, channelRefundId: "" }, "REF-001", "PAY-001", "18.00"),
    /changed the original channel refund identifier|did not contain a usable channel refund identifier/
  );
  assert.throws(
    () => assertIdempotentRefundCallback(
      { ...first, channelSubmittedAt: "2026-09-27 12:00:30" },
      { ...first, channelSubmittedAt: "2026-09-27 12:00:31" },
      "REF-001",
      "PAY-001",
      "18.00",
      "ORDER-001"
    ),
    /changed the original channelSubmittedAt fact/
  );
});

test("requires refund lifecycle timestamps across terminal states", () => {
  assertRefundLifecycleTime({ status: "PROCESSING", appliedAt: "2026-09-27 12:00:00", channelSubmittedAt: "2026-09-27 12:00:30" }, "processing");
  assertRefundLifecycleTime({ status: "SUCCESS", appliedAt: "2026-09-27 12:00:00", channelSubmittedAt: "2026-09-27 12:00:30", successAt: "2026-09-27 12:01:00", terminalAt: "2026-09-27 12:01:00" }, "success");
  assertRefundLifecycleTime({ status: "FAIL", appliedAt: "2026-09-27 12:00:00", channelSubmittedAt: "2026-09-27 12:00:30", terminalAt: "2026-09-27 12:01:00" }, "failure");
  for (const invalidRefund of [
    { status: "PROCESSING", successAt: "2026-09-27 12:01:00" },
    { status: "PROCESSING", appliedAt: "2026-02-30 12:00:00" },
    { status: "SUCCESS", appliedAt: "2026-09-27 12:00:00" },
    { status: "SUCCESS", appliedAt: "2026-09-27 12:00:00", successAt: "not-a-time", terminalAt: "2026-09-27 12:01:00" },
    { status: "SUCCESS", appliedAt: "2026-09-27 12:00:00", successAt: "2026-09-27 12:01:00" },
    { status: "PROCESSING", appliedAt: "2026-09-27 12:00:00", terminalAt: "2026-09-27 12:01:00" },
    { status: "SUCCESS", appliedAt: "2026-09-27 12:01:00", successAt: "2026-09-27 12:00:00", terminalAt: "2026-09-27 12:02:00" },
    { status: "SUCCESS", appliedAt: "2026-09-27 12:00:00", successAt: "2026-09-27 12:02:00", terminalAt: "2026-09-27 12:01:00" },
    { status: "FAIL", appliedAt: "2026-09-27 12:01:00", terminalAt: "2026-09-27 12:00:00" },
    { status: "PROCESSING", appliedAt: "2026-09-27 12:00:00", channelSubmittedAt: "not-a-time" },
    { status: "PROCESSING", appliedAt: "2026-09-27 12:00:00", channelSubmittedAt: "2026-09-27 11:59:59" },
    { status: "SUCCESS", appliedAt: "2026-09-27 12:00:00", channelSubmittedAt: "2026-09-27 12:02:00", successAt: "2026-09-27 12:03:00", terminalAt: "2026-09-27 12:01:00" },
    { status: "FAIL", appliedAt: "2026-09-27 12:00:00", terminalAt: "2026-09-27 12:01:00" }
  ]) {
    assert.throws(() => assertRefundLifecycleTime(invalidRefund, "invalid"), /refund invalid/);
  }
});

test("rejects a refund that exceeds the captured payment amount", () => {
  assert.equal(
    assertRefundWithinPayment({ refundAmount: "18.00" }, "88.00").refundAmount,
    "18.00"
  );
  assert.throws(
    () => assertRefundWithinPayment({ refundAmount: "88.01" }, "88.00"),
    /exceeded payment amount/
  );
  assert.throws(
    () => assertRefundWithinPayment({ refundAmount: "0.00" }, "88.00"),
    /exceeded payment amount/
  );
  assert.throws(
    () => assertRefundWithinPayment({ refundAmount: "18oops" }, "88.00"),
    /exceeded payment amount/
  );
});

test("rejects a settlement order that bypassed approval or payout controls", () => {
  assert.throws(
    () => assertSettlementDraft({
      settlementStatus: "已出款",
      auditStatus: "审核通过",
      payoutStatus: "已完成"
    }),
    /未处于|not awaiting audit/
  );
  assert.throws(
    () => assertSettlementDraft({
      settlementStatus: "待审核",
      auditStatus: "待审核",
      payoutStatus: "待出款",
      settlementStatusType: "success",
      auditStatusType: "warn",
      payoutStatusType: "warn"
    }),
    /pending status styles/
  );
});

test("requires settlement full detail to preserve amounts and approval audit", () => {
  const detail = {
    order: {
      settlementNo: "SET-001",
      businessChainId: "CHAIN-PAY-001",
      shouldSettleAmount: "¥71.16",
      deductAmount: "¥0.00",
      netSettleAmount: "¥71.16"
    },
    items: [
      { settlementNo: "SET-001", itemType: "应结金额", itemAmount: "¥71.16", createdAt: "2026-09-28 10:01:00" },
      { settlementNo: "SET-001", itemType: "扣减金额", itemAmount: "¥0.00", createdAt: "2026-09-28 10:01:00" }
    ],
    auditLogs: [
      { settlementNo: "SET-001", auditAction: "创建结算单", auditResult: "待审核", operatorName: "系统", createdAt: "2026-09-28 10:01:00" },
      { settlementNo: "SET-001", auditAction: "审核通过", auditResult: "通过", operatorName: "审核员", createdAt: "2026-09-28 10:02:00" }
    ]
  };
  assert.equal(
    assertSettlementFullDetail(detail, "SET-001", "CHAIN-PAY-001", "71.16", "0.00", "71.16", "审核员").order.settlementNo,
    "SET-001"
  );
  assert.throws(
    () => assertSettlementFullDetail({ ...detail, items: [{ ...detail.items[0], itemAmount: "¥70.16" }, detail.items[1]] }, "SET-001", "CHAIN-PAY-001", "71.16", "0.00", "71.16", "审核员"),
    /amount did not match/
  );
  assert.throws(
    () => assertSettlementFullDetail({ ...detail, auditLogs: detail.auditLogs.slice(0, 1) }, "SET-001", "CHAIN-PAY-001", "71.16", "0.00", "71.16", "审核员"),
    /approval audit log/
  );
  assert.throws(
    () => assertSettlementFullDetail({ ...detail, order: { ...detail.order, businessChainId: "CHAIN-OTHER" } }, "SET-001", "CHAIN-PAY-001", "71.16", "0.00", "71.16", "审核员"),
    /order identity/
  );
});

test("requires the consumed settlement event to preserve clearing identity", () => {
  const event = {
    eventNo: "SVE-001",
    eventType: "CLEARING_GENERATED",
    bizNo: "CLR-001",
    businessChainId: "CHAIN-PAY-001",
    eventStatus: "已消费",
    statusType: "success",
    payload: JSON.stringify({ clearingNo: "CLR-001" }),
    createdAt: "2026-09-28 10:00:01"
  };
  assert.equal(
    assertSettlementGeneratedEvent(event, "CLR-001", "CHAIN-PAY-001", "2026-09-28 10:00:00").eventNo,
    "SVE-001"
  );
  assert.throws(
    () => assertSettlementGeneratedEvent({ ...event, payload: JSON.stringify({ clearingNo: "CLR-002" }) }, "CLR-001", "CHAIN-PAY-001"),
    /payload did not preserve clearing number/
  );
  assert.throws(
    () => assertSettlementGeneratedEvent({ ...event, businessChainId: "CHAIN-OTHER" }, "CLR-001", "CHAIN-PAY-001"),
    /did not match business chain/
  );
  assert.throws(
    () => assertSettlementGeneratedEvent({ ...event, eventStatus: "待消费", statusType: "warn" }, "CLR-001", "CHAIN-PAY-001"),
    /did not reach consumed state/
  );
  assert.throws(
    () => assertSettlementGeneratedEvent({ ...event, payload: "not-json" }, "CLR-001", "CHAIN-PAY-001"),
    /payload did not preserve clearing number/
  );
});

test("requires the clearing outbox event to preserve downstream payload identity", () => {
  const event = {
    eventNo: "EVT-001",
    eventType: "CLEARING_GENERATED",
    bizNo: "CLR-001",
    businessChainId: "CHAIN-PAY-001",
    publishStatus: "SUCCESS",
    payload: JSON.stringify({
      eventNo: "EVT-001",
      clearingNo: "CLR-001",
      paymentOrderId: "PAY-001",
      businessChainId: "CHAIN-PAY-001",
      targetType: "WORKER",
      targetNo: "WRK-001",
      shouldSettleAmount: 71.16,
      deductAmount: 0,
      netSettleAmount: 71.16,
      amount: 71.16
    }),
    createdAt: "2026-09-28 10:00:01"
  };
  assert.equal(
    assertClearingGeneratedEvent(event, "CLR-001", "PAY-001", "CHAIN-PAY-001", "WRK-001", "71.16", "2026-09-28 10:00:00").eventNo,
    "EVT-001"
  );
  assert.throws(
    () => assertClearingGeneratedEvent({ ...event, payload: JSON.stringify({ ...JSON.parse(event.payload), paymentOrderId: "PAY-002" }) }, "CLR-001", "PAY-001", "CHAIN-PAY-001", "WRK-001", "71.16"),
    /payload did not preserve identity/
  );
  assert.throws(
    () => assertClearingGeneratedEvent({ ...event, publishStatus: "PENDING" }, "CLR-001", "PAY-001", "CHAIN-PAY-001", "WRK-001", "71.16"),
    /did not reach published state/
  );
  assert.throws(
    () => assertClearingGeneratedEvent({ ...event, payload: JSON.stringify({ ...JSON.parse(event.payload), amount: 70 }) }, "CLR-001", "PAY-001", "CHAIN-PAY-001", "WRK-001", "71.16"),
    /amount did not match/
  );
  assert.throws(
    () => assertClearingGeneratedEvent({ ...event, payload: JSON.stringify({ ...JSON.parse(event.payload), targetType: "MERCHANT" }) }, "CLR-001", "PAY-001", "CHAIN-PAY-001", "WRK-001", "71.16"),
    /payload did not preserve identity/
  );
  assert.throws(
    () => assertClearingGeneratedEvent({ ...event, payload: JSON.stringify({ ...JSON.parse(event.payload), netSettleAmount: 70 }) }, "CLR-001", "PAY-001", "CHAIN-PAY-001", "WRK-001", "71.16"),
    /amount did not match/
  );
  assert.throws(
    () => assertClearingGeneratedEvent({ ...event, payload: "not-json" }, "CLR-001", "PAY-001", "CHAIN-PAY-001", "WRK-001", "71.16"),
    /payload did not preserve identity/
  );
});

test("requires the clearing payment success event to preserve payment scene", () => {
  const event = {
    eventNo: "EVT-PAYMENT-001",
    eventType: "PAYMENT_SUCCESS",
    bizNo: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    eventStatus: "已消费",
    payload: JSON.stringify({
      paymentOrderId: "PAY-001",
      orderNo: "ORDER-001",
      businessChainId: "CHAIN-PAY-001",
      payScene: "HOME_CLEAN"
    }),
    createdAt: "2026-09-28 10:00:01"
  };
  assert.equal(
    assertClearingPaymentSuccessEvent(
      event,
      "PAY-001",
      "ORDER-001",
      "CHAIN-PAY-001",
      "HOME_CLEAN",
      "2026-09-28 10:00:00"
    ).eventNo,
    "EVT-PAYMENT-001"
  );
  assert.throws(
    () => assertClearingPaymentSuccessEvent(
      { ...event, payload: JSON.stringify({ ...JSON.parse(event.payload), payScene: "HOME_REPAIR" }) },
      "PAY-001",
      "ORDER-001",
      "CHAIN-PAY-001",
      "HOME_CLEAN"
    ),
    /did not preserve identity or payment scene/
  );
});

test("requires exactly one consumed clearing payment success event", () => {
  const event = {
    eventNo: "EVT-PAYMENT-001",
    eventType: "PAYMENT_SUCCESS",
    bizNo: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    eventStatus: "已消费",
    payload: JSON.stringify({
      paymentOrderId: "PAY-001", orderNo: "ORDER-001",
      businessChainId: "CHAIN-PAY-001", payScene: "HOME_CLEAN"
    }),
    createdAt: "2026-09-28 10:00:01"
  };
  const assertOne = (page) => assertSingleClearingPaymentSuccessEvent(
    page, "PAY-001", "ORDER-001", "CHAIN-PAY-001", "HOME_CLEAN", "2026-09-28 10:00:00"
  );
  assert.equal(assertOne({ total: 1, items: [event] }).eventNo, event.eventNo);
  assert.throws(() => assertOne({ total: 0, items: [] }), /exactly one PAYMENT_SUCCESS/);
  assert.throws(() => assertOne({ total: 2, items: [event, { ...event, eventNo: "EVT-PAYMENT-002" }] }),
    /exactly one PAYMENT_SUCCESS/);
  assert.throws(() => assertOne({ total: 2, items: [event] }), /exactly one PAYMENT_SUCCESS/);
});

test("keeps settlement and clearing identities continuous", () => {
  const businessChainId = "CHAIN-PAY-001";
  const target = { targetType: "WORKER", targetNo: "WRK1001", batchNo: "BAT-001" };
  assert.equal(
    assertSettlementIdentity({ settlementNo: "SET-001", clearingNo: "CLR-001", businessChainId, ...target }, "CLR-001", businessChainId).settlementNo,
    "SET-001"
  );
  assert.equal(
    assertSettlementIdentity({ settlementNo: "SET-001", clearingNo: "CLR-001", businessChainId, createdAt: "2026-09-28 10:01:00", ...target }, "CLR-001", businessChainId, "2026-09-28 10:00:00").settlementNo,
    "SET-001"
  );
  assert.equal(
    assertSettlementIdentity({ settlementNo: "SET-001", clearingNo: "  CLR-001  ", businessChainId, ...target }, "CLR-001", businessChainId).clearingNo,
    "  CLR-001  "
  );
  assert.equal(
    assertSettlementIdentity({ settlementNo: "SET-001", clearingNo: "CLR-001", businessChainId: `  ${businessChainId}  `, ...target }, "CLR-001", businessChainId).businessChainId,
    `  ${businessChainId}  `
  );
  assert.throws(
    () => assertSettlementIdentity({ settlementNo: "SET-001", clearingNo: "CLR-002", businessChainId, ...target }, "CLR-001", businessChainId),
    /did not match clearing order/
  );
  assert.throws(
    () => assertSettlementIdentity({ clearingNo: "CLR-001", businessChainId, ...target }, "CLR-001", businessChainId),
    /usable settlement number/
  );
  assert.throws(
    () => assertSettlementIdentity({ settlementNo: "SET-001", clearingNo: "CLR-001", businessChainId: "CHAIN-PAY-002", ...target }, "CLR-001", businessChainId),
    /did not match business chain/
  );
  assert.throws(
    () => assertSettlementIdentity({ settlementNo: "SET-001", clearingNo: "CLR-001", businessChainId, targetType: "WORKER", targetNo: "WRK1001" }, "CLR-001", businessChainId),
    /usable settlement batch number/
  );
  assert.throws(
    () => assertSettlementIdentity({ settlementNo: "SET-001", clearingNo: "CLR-001", ...target }, "CLR-001", businessChainId),
    /did not match business chain/
  );
  assert.throws(
    () => assertSettlementIdentity({ settlementNo: "SET-001", clearingNo: "CLR-001", businessChainId, batchNo: "BAT-001", targetType: "MERCHANT", targetNo: "MCH1001" }, "CLR-001", businessChainId),
    /did not target a worker/
  );
  assert.throws(
    () => assertSettlementIdentity({ settlementNo: "SET-001", clearingNo: "CLR-001", businessChainId, batchNo: "BAT-001", targetType: "WORKER" }, "CLR-001", businessChainId),
    /usable worker target number/
  );
  assert.throws(
    () => assertSettlementIdentity({ settlementNo: "SET-001", clearingNo: "CLR-001", businessChainId, createdAt: "2026-09-28 09:59:00", ...target }, "CLR-001", businessChainId, "2026-09-28 10:00:00"),
    /created before the clearing order/
  );
});

test("requires settlement approval and payout execution to close the current record", () => {
  const auditedSettlement = {
    settlementNo: "SET-001",
    clearingNo: "CLR-001",
    businessChainId: "CHAIN-PAY-001",
    batchNo: "BAT-001",
    targetType: "WORKER",
    targetNo: "WRK-001",
    createdAt: "2026-09-28 10:01:00",
    netSettleAmount: "¥71.16",
    settlementStatus: "待出款",
    settlementStatusType: "warn",
    auditStatus: "已通过",
    auditStatusType: "success",
    payoutStatus: "待出款",
    payoutStatusType: "warn"
  };
  const audited = assertSettlementApproved(
    auditedSettlement,
    "SET-001",
    "CLR-001",
    "CHAIN-PAY-001",
    "71.16",
    "BAT-001",
    "WRK-001",
    "2026-09-28 10:01:00"
  );
  assert.equal(audited.settlementNo, "SET-001");
  assert.equal(
    assertSettlementApproved(
      { ...auditedSettlement, settlementNo: "  SET-001  " },
      "SET-001",
      "CLR-001",
      "CHAIN-PAY-001",
      "71.16"
    ).settlementNo,
    "  SET-001  "
  );
  assert.equal(
    assertSettlementApproved(
      { ...auditedSettlement, clearingNo: "  CLR-001  " },
      "SET-001",
      "CLR-001",
      "CHAIN-PAY-001",
      "71.16"
    ).clearingNo,
    "  CLR-001  "
  );
  assert.equal(
    assertSettlementApproved(
      { ...auditedSettlement, businessChainId: "  CHAIN-PAY-001  " },
      "SET-001",
      "CLR-001",
      "CHAIN-PAY-001",
      "71.16"
    ).businessChainId,
    "  CHAIN-PAY-001  "
  );
  assert.throws(
    () => assertSettlementIdentity(
      { ...auditedSettlement, settlementStatus: "待审核", auditStatus: "待审核", payoutStatus: "待出款" },
      "CLR-001",
      "CHAIN-PAY-001",
      "2026-09-28 10:00:00",
      "WRK-002"
    ),
    /preserve clearing target/
  );
  for (const invalidSettlement of [
    { ...auditedSettlement, clearingNo: "CLR-002" },
    { ...auditedSettlement, businessChainId: "CHAIN-OTHER" },
    { ...auditedSettlement, netSettleAmount: "71.17" },
    { ...auditedSettlement, batchNo: "BAT-002" },
    { ...auditedSettlement, targetNo: "WRK-002" },
    { ...auditedSettlement, targetType: "MERCHANT" },
    { ...auditedSettlement, createdAt: "2026-09-28 10:02:00" }
  ]) {
    assert.throws(
      () => assertSettlementApproved(
        invalidSettlement,
        "SET-001",
        "CLR-001",
        "CHAIN-PAY-001",
        "71.16",
        invalidSettlement.batchNo === "BAT-002" ? "BAT-001" : null,
        invalidSettlement.targetNo === "WRK-002" ? "WRK-001" : null,
        invalidSettlement.createdAt === "2026-09-28 10:02:00" ? "2026-09-28 10:01:00" : null
      ),
      /audited settlement response|amount did not match/
    );
  }
  const closedSettlement = {
    ...auditedSettlement,
    settlementStatus: "已出款",
    settlementStatusType: "success",
    payoutStatus: "已发放",
    payoutStatusType: "success"
  };
  assert.equal(
    assertSettlementClosed(
      closedSettlement,
      "SET-001",
      "CLR-001",
      "CHAIN-PAY-001",
      "71.16"
    ).settlementStatus,
    "已出款"
  );
  assert.equal(
    assertSettlementClosed(
      { ...closedSettlement, settlementNo: "  SET-001  " },
      "SET-001",
      "CLR-001",
      "CHAIN-PAY-001",
      "71.16"
    ).settlementNo,
    "  SET-001  "
  );
  assert.equal(
    assertSettlementClosed(
      { ...closedSettlement, clearingNo: "  CLR-001  " },
      "SET-001",
      "CLR-001",
      "CHAIN-PAY-001",
      "71.16"
    ).clearingNo,
    "  CLR-001  "
  );
  assert.equal(
    assertSettlementClosed(
      { ...closedSettlement, businessChainId: "  CHAIN-PAY-001  " },
      "SET-001",
      "CLR-001",
      "CHAIN-PAY-001",
      "71.16"
    ).businessChainId,
    "  CHAIN-PAY-001  "
  );
  assert.equal(
    assertSettlementClosed(
      { ...closedSettlement, batchNo: "  BAT-001  " },
      "SET-001",
      "CLR-001",
      "CHAIN-PAY-001",
      "71.16",
      "BAT-001",
      "WRK-001",
      "2026-09-28 10:01:00"
    ).batchNo,
    "  BAT-001  "
  );
  for (const invalidSettlement of [
    { ...closedSettlement, clearingNo: "CLR-002" },
    { ...closedSettlement, businessChainId: "CHAIN-OTHER" },
    { ...closedSettlement, netSettleAmount: "71.17" },
    { ...closedSettlement, batchNo: "BAT-002" },
    { ...closedSettlement, targetNo: "WRK-002" },
    { ...closedSettlement, targetType: "MERCHANT" },
    { ...closedSettlement, createdAt: "2026-09-28 10:02:00" },
    { ...closedSettlement, payoutStatusType: "warn" }
  ]) {
    assert.throws(
      () => assertSettlementClosed(
        invalidSettlement,
        "SET-001",
        "CLR-001",
        "CHAIN-PAY-001",
        "71.16",
        invalidSettlement.batchNo === "BAT-002" ? "BAT-001" : null,
        invalidSettlement.targetNo === "WRK-002" ? "WRK-001" : null,
        invalidSettlement.createdAt === "2026-09-28 10:02:00" ? "2026-09-28 10:01:00" : null
      ),
      /closed settlement response|amount did not match|status styles/
    );
  }

  const pendingRecord = {
    payoutNo: "POU-001",
    payoutBatchNo: "PBT-001",
    settlementNo: "SET-001",
    targetNo: "WRK-001",
    payoutAmount: "¥71.16",
    payoutStatus: "待出款",
    payoutStatusType: "warn",
    createdAt: "2026-09-28 10:02:00"
  };
  assert.equal(assertPayoutBatchPendingRecords([pendingRecord], "PBT-001", 1, "71.16", "WRK-001")[0].payoutNo, "POU-001");
  assert.throws(
    () => assertPayoutBatchPendingRecords([pendingRecord], "PBT-001", 1, "71.16", "WRK-002"),
    /pending payout record did not match settlement target/
  );
  assert.throws(
    () => assertPayoutBatchPendingRecords([], "PBT-001", 1),
    /pending payout records count was 0, expected 1/
  );
  assert.throws(
    () => assertPayoutBatchPendingRecords([{ ...pendingRecord, payoutStatus: "已发放", payoutStatusType: "success" }], "PBT-001", 1),
    /incomplete or non-pending record/
  );
  assert.throws(
    () => assertPayoutBatchPendingRecords([pendingRecord], "PBT-001", 1, "71.17"),
    /pending payout batch records total did not match batch total/
  );
  assert.equal(assertPayoutRecord(
    pendingRecord, "PBT-001", "SET-001", "71.16", "待出款", "2026-09-28 10:00:00", "WRK-001"
  ).payoutNo, "POU-001");
  assert.equal(
    assertPayoutRecord({ ...pendingRecord, payoutBatchNo: "  PBT-001  " }, "PBT-001", "SET-001", "71.16").payoutBatchNo,
    "  PBT-001  "
  );
  assert.equal(
    assertPayoutRecord({ ...pendingRecord, settlementNo: "  SET-001  " }, "PBT-001", "SET-001", "71.16").settlementNo,
    "  SET-001  "
  );
  assert.equal(findPayoutRecord([{ ...pendingRecord, settlementNo: "  SET-001  " }], "SET-001").settlementNo, "  SET-001  ");
  assert.equal(findPayoutRecord([{ ...pendingRecord, settlementNo: "SET-002" }], "SET-001"), undefined);
  assert.equal(assertPayoutRecord({ ...pendingRecord, payoutStatus: "已发放", payoutStatusType: "success" }, "PBT-001", "SET-001", "71.16", "已发放").payoutStatus, "已发放");
  assert.throws(
    () => assertPayoutRecord({ ...pendingRecord, payoutStatusType: "success" }, "PBT-001", "SET-001", "71.16"),
    /status style/
  );
  assert.throws(
    () => assertPayoutRecord({ ...pendingRecord, settlementNo: "SET-002" }, "PBT-001", "SET-001", "71.16"),
    /batch and settlement identity/
  );
  assert.throws(
    () => assertPayoutRecord({ ...pendingRecord, targetNo: "" }, "PBT-001", "SET-001", "71.16"),
    /usable payout target/
  );
  assert.throws(
    () => assertPayoutRecord(pendingRecord, "PBT-001", "SET-001", "71.16", "待出款", null, "WRK-002"),
    /did not match settlement target/
  );
  assert.throws(
    () => assertPayoutRecord({ ...pendingRecord, payoutAmount: "71.17" }, "PBT-001", "SET-001", "71.16"),
    /amount did not match/
  );
  assert.throws(
    () => assertPayoutRecord(
      pendingRecord, "PBT-001", "SET-001", "71.16", "待出款", "2026-09-28 10:03:00"
    ),
    /payout record was created before the settlement order/
  );

  assert.equal(assertPayoutBatchExecuted({
    payoutBatchNo: "PBT-001",
    batchNo: "BAT-001",
    payoutChannel: "银行卡",
    createdAt: "2026-09-28 10:01:00",
    payoutStatus: "已完成",
    payoutStatusType: "success",
    payoutCount: 1,
    successCount: 1,
    failedCount: 0,
    finishedAt: "2026-09-28 10:03:00"
  }, "PBT-001", null, "BAT-001").payoutStatus, "已完成");
  assert.throws(
    () => assertPayoutBatchExecuted({
      payoutBatchNo: "PBT-001",
      batchNo: "BAT-002",
      payoutChannel: "银行卡",
      createdAt: "2026-09-28 10:01:00",
      payoutStatus: "已完成",
      payoutStatusType: "success",
      payoutCount: 1,
      successCount: 1,
      failedCount: 0,
      finishedAt: "2026-09-28 10:03:00"
    }, "PBT-001", null, "BAT-001"),
    /did not match settlement batch/
  );
  assert.throws(
    () => assertPayoutBatchExecuted({
      payoutBatchNo: "PBT-001",
      payoutChannel: "银行卡",
      createdAt: "2026-09-28 10:01:00",
      payoutStatus: "已完成",
      payoutStatusType: "success",
      payoutCount: 1,
      successCount: 1,
      failedCount: 0,
      finishedAt: "2026-09-28 10:02:00"
    }, "PBT-001", "2026-09-28 10:03:00"),
    /finished before its payout record was created/
  );
  assert.throws(
    () => assertPayoutBatchExecuted({
      payoutBatchNo: "PBT-001",
      payoutChannel: "银行卡",
      createdAt: "2026-09-28 10:04:00",
      payoutStatus: "已完成",
      payoutStatusType: "success",
      payoutCount: 1,
      successCount: 1,
      failedCount: 0,
      finishedAt: "2026-09-28 10:03:00"
    }, "PBT-001"),
    /finished before it was created/
  );
  assert.deepEqual(
    assertPayoutBatchRecords([
      { payoutNo: "PO-001", payoutBatchNo: "PBT-001", settlementNo: "SET-001", targetNo: "WRK-001", payoutAmount: "40.00", payoutStatus: "已发放", payoutStatusType: "success", createdAt: "2026-09-28 10:02:00" },
      { payoutNo: "PO-002", payoutBatchNo: "PBT-001", settlementNo: "SET-002", targetNo: "WRK-002", payoutAmount: "31.16", payoutStatus: "已发放", payoutStatusType: "success", createdAt: "2026-09-28 10:02:01" }
    ], "PBT-001", 2, "71.16", "2026-09-28 10:03:00").map((record) => record.payoutNo),
    ["PO-001", "PO-002"]
  );
  assert.throws(
    () => assertPayoutBatchRecords([
      { payoutNo: "PO-001", payoutBatchNo: "PBT-001", payoutStatus: "已发放", payoutStatusType: "success", createdAt: "2026-09-28 10:02:00" }
    ], "PBT-001", 2),
    /records count was 1, expected 2/
  );
  assert.throws(
    () => assertPayoutBatchRecords([
      { payoutNo: "PO-001", payoutBatchNo: "PBT-001", payoutStatus: "待出款", payoutStatusType: "warn", createdAt: "2026-09-28 10:02:00" }
    ], "PBT-001", 1),
    /incomplete or non-success record/
  );
  assert.throws(
    () => assertPayoutBatchRecords([
      { ...pendingRecord, payoutStatus: "已发放", payoutStatusType: "success" }
    ], "PBT-001", 1, "71.16", null, "WRK-002"),
    /payout record did not match settlement target/
  );
  assert.throws(
    () => assertPayoutBatchRecords([
      { payoutNo: "PO-001", payoutBatchNo: "PBT-001", settlementNo: "SET-001", targetNo: "WRK-001", payoutAmount: "71.17", payoutStatus: "已发放", payoutStatusType: "success", createdAt: "2026-09-28 10:02:00" }
    ], "PBT-001", 1, "71.16"),
    /records total did not match batch total/
  );
  assert.throws(
    () => assertPayoutBatchRecords([
      { payoutNo: "PO-001", payoutBatchNo: "PBT-001", settlementNo: "", payoutAmount: "0.00", payoutStatus: "已发放", payoutStatusType: "success", createdAt: "2026-09-28 10:02:00" }
    ], "PBT-001", 1),
    /incomplete or non-success record/
  );
  assert.throws(
    () => assertPayoutBatchRecords([
      { payoutNo: "PO-001", payoutBatchNo: "PBT-001", settlementNo: "SET-001", targetNo: "", payoutAmount: "71.16", payoutStatus: "已发放", payoutStatusType: "success", createdAt: "2026-09-28 10:02:00" }
    ], "PBT-001", 1),
    /incomplete or non-success record/
  );
  assert.throws(
    () => assertPayoutBatchRecords([
      { payoutNo: "PO-001", payoutBatchNo: "PBT-001", settlementNo: "SET-001", targetNo: "WRK-001", payoutAmount: "71.16", payoutStatus: "已发放", payoutStatusType: "success", createdAt: "2026-09-28 10:04:00" }
    ], "PBT-001", 1, "71.16", "2026-09-28 10:03:00"),
    /incomplete or non-success record/
  );
  assert.throws(
    () => assertPayoutBatchRecords([
      { payoutNo: "PO-001", payoutBatchNo: "PBT-001", settlementNo: "SET-001", targetNo: "WRK-001", payoutAmount: "40.00", payoutStatus: "已发放", payoutStatusType: "success", createdAt: "2026-09-28 10:02:00" },
      { payoutNo: "  PO-001  ", payoutBatchNo: "PBT-001", settlementNo: "SET-002", targetNo: "WRK-002", payoutAmount: "31.16", payoutStatus: "已发放", payoutStatusType: "success", createdAt: "2026-09-28 10:02:01" }
    ], "PBT-001", 2, "71.16"),
    /duplicate payout number/
  );
  assert.throws(
    () => assertPayoutBatchRecords([
      { payoutNo: "PO-001", payoutBatchNo: "PBT-001", settlementNo: "SET-001", targetNo: "WRK-001", payoutAmount: "40.00", payoutStatus: "已发放", payoutStatusType: "success", createdAt: "2026-09-28 10:02:00" },
      { payoutNo: "PO-002", payoutBatchNo: "PBT-001", settlementNo: "  SET-001  ", targetNo: "WRK-001", payoutAmount: "31.16", payoutStatus: "已发放", payoutStatusType: "success", createdAt: "2026-09-28 10:02:01" }
    ], "PBT-001", 2, "71.16"),
    /duplicate settlement number/
  );
  assert.equal(assertPayoutBatchExecuted({
    payoutBatchNo: "  PBT-001  ",
    payoutChannel: "银行卡",
    createdAt: "2026-09-28 10:01:00",
    payoutStatus: "已完成",
    payoutStatusType: "success",
    payoutCount: 1,
    successCount: 1,
    failedCount: 0,
    finishedAt: "2026-09-28 10:03:00"
  }, "PBT-001").payoutBatchNo, "  PBT-001  ");
  assert.equal(findPayoutBatch([
    { payoutBatchNo: "  PBT-001  ", batchNo: "BAT-001", payoutStatus: "待出款" },
    { payoutBatchNo: "PBT-002", payoutStatus: "待出款" }
  ], "PBT-001").payoutBatchNo, "  PBT-001  ");
  assert.equal(findPayoutBatch([
    { payoutBatchNo: "PBT-001", batchNo: "  BAT-001  ", payoutStatus: "待出款" }
  ], "PBT-001", "待出款", "BAT-001").batchNo, "  BAT-001  ");
  assert.equal(findPayoutBatch([
    { payoutBatchNo: "PBT-001", batchNo: "  BAT-001  ", payoutStatus: "待出款" }
  ], null, "待出款", "BAT-001").payoutBatchNo, "PBT-001");
  assert.equal(findPayoutBatch([
    { payoutBatchNo: "PBT-001", batchNo: "BAT-002", payoutStatus: "待出款" }
  ], "PBT-001", "待出款", "BAT-001"), undefined);
  assert.equal(findPayoutBatch([{ payoutBatchNo: "PBT-002", payoutStatus: "待出款" }], "PBT-001"), undefined);
  for (const invalidBatch of [
    { payoutBatchNo: "PBT-001", payoutChannel: "银行卡", createdAt: "2026-09-28 10:01:00", payoutStatus: "待出款", payoutStatusType: "warn", payoutCount: 1, successCount: 1, failedCount: 0, finishedAt: "2026-09-28 10:03:00" },
    { payoutBatchNo: "PBT-001", payoutChannel: "银行卡", createdAt: "2026-09-28 10:01:00", payoutStatus: "已完成", payoutStatusType: "success", payoutCount: 1, successCount: 0, failedCount: 1, finishedAt: "2026-09-28 10:03:00" },
    { payoutBatchNo: "PBT-001", payoutStatus: "已完成", payoutStatusType: "success", payoutCount: 1, successCount: 1, failedCount: 0 }
  ]) {
    assert.throws(() => assertPayoutBatchExecuted(invalidBatch, "PBT-001"), /payout batch|creation time|finish time/);
  }
});

test("keeps clearing and payment identities continuous", () => {
  const lifecycle = { businessChainId: "CHAIN-PAY-001", clearingStatus: "清分成功", clearingStatusType: "success", createdAt: "2026-09-28 10:00:00" };
  const clearingOrder = { clearingNo: "CLR-001", businessChainId: "CHAIN-PAY-001", workerAmount: "¥68.00" };
  const clearingDetail = {
    order: clearingOrder,
    shareItems: [{ clearingNo: "CLR-001", shareType: "WORKER", shareTargetNo: "  WRK-001  ", shareAmount: "68.00" }]
  };
  assert.equal(assertClearingTarget(clearingDetail, clearingOrder), "WRK-001");
  assert.throws(
    () => assertClearingTarget({ ...clearingDetail, shareItems: [] }, clearingOrder),
    /usable worker payout target/
  );
  assert.throws(
    () => assertClearingTarget({ ...clearingDetail, order: { ...clearingOrder, clearingNo: "CLR-OTHER" } }, clearingOrder),
    /selected clearing order/
  );
  assert.throws(
    () => assertClearingTarget({ ...clearingDetail, shareItems: [{ ...clearingDetail.shareItems[0], shareAmount: "67.99" }] }, clearingOrder),
    /target amount/
  );
  assert.equal(
    assertClearingIdentity({ clearingNo: "CLR-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", ...lifecycle }, "PAY-001", "ORDER-001").clearingNo,
    "CLR-001"
  );
  assert.equal(
    assertClearingIdentity(
      { clearingNo: "CLR-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", payScene: "HOME_CLEAN", ...lifecycle },
      "PAY-001",
      "ORDER-001",
      null,
      "HOME_CLEAN"
    ).payScene,
    "HOME_CLEAN"
  );
  assert.throws(
    () => assertClearingIdentity(
      { clearingNo: "CLR-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", payScene: "HOME_REPAIR", ...lifecycle },
      "PAY-001",
      "ORDER-001",
      null,
      "HOME_CLEAN"
    ),
    /did not preserve payment scene/
  );
  assert.equal(findClearingOrder([
    { clearingNo: "CLR-001", paymentOrderId: "  PAY-001  ", clearingStatus: "清分成功" },
    { clearingNo: "CLR-002", paymentOrderId: "PAY-002", clearingStatus: "清分成功" }
  ], "PAY-001").clearingNo, "CLR-001");
  assert.equal(findClearingOrder([{ paymentOrderId: "PAY-002", clearingStatus: "清分成功" }], "PAY-001"), undefined);
  assert.equal(findClearingOrder([
    { clearingNo: "CLR-001", paymentOrderId: "PAY-001", orderNo: "ORDER-002", clearingStatus: "清分成功" }
  ], "PAY-001", "清分成功", "ORDER-001"), undefined);
  assert.equal(findClearingOrder([
    { clearingNo: "CLR-001", paymentOrderId: "PAY-001", orderNo: "  ORDER-001  ", clearingStatus: "清分成功" }
  ], "PAY-001", "清分成功", "ORDER-001").clearingNo, "CLR-001");
  assert.equal(
    assertClearingIdentity({ clearingNo: "CLR-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", ...lifecycle, businessChainId: "  CHAIN-PAY-001  " }, "PAY-001", "ORDER-001").businessChainId,
    "  CHAIN-PAY-001  "
  );
  assert.equal(
    assertClearingIdentity({ clearingNo: "CLR-001", paymentOrderId: "  PAY-001  ", orderNo: "ORDER-001", ...lifecycle }, "  PAY-001  ", "ORDER-001").paymentOrderId,
    "  PAY-001  "
  );
  assert.equal(
    assertClearingIdentity({ clearingNo: "CLR-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", ...lifecycle }, "PAY-001", "ORDER-001", "2026-09-28 09:59:00").clearingNo,
    "CLR-001"
  );
  assert.throws(
    () => assertClearingIdentity({ clearingNo: "CLR-001", paymentOrderId: "PAY-002" }, "PAY-001"),
    /did not match payment order/
  );
  assert.throws(
    () => assertClearingIdentity({ clearingNo: "CLR-001", paymentOrderId: "PAY-001", orderNo: "ORDER-002", ...lifecycle }, "PAY-001", "ORDER-001"),
    /did not match business order/
  );
  assert.throws(
    () => assertClearingIdentity({ clearingNo: "CLR-001", paymentOrderId: "PAY-001", ...lifecycle, businessChainId: "CHAIN-PAY-002" }, "PAY-001"),
    /did not match business chain/
  );
  assert.throws(
    () => assertClearingIdentity({ clearingNo: "CLR-001", paymentOrderId: "PAY-001", clearingStatus: "清分成功", clearingStatusType: "success", createdAt: lifecycle.createdAt }, "PAY-001"),
    /did not match business chain/
  );
  assert.throws(
    () => assertClearingIdentity({ paymentOrderId: "PAY-001" }, "PAY-001"),
    /usable clearing number/
  );
  assert.throws(
    () => assertClearingIdentity({ clearingNo: "CLR-001", paymentOrderId: "PAY-001", businessChainId: lifecycle.businessChainId, clearingStatus: "清分中", clearingStatusType: "warn", createdAt: lifecycle.createdAt }, "PAY-001"),
    /did not reach success/
  );
  assert.throws(
    () => assertClearingIdentity({ clearingNo: "CLR-001", paymentOrderId: "PAY-001", businessChainId: lifecycle.businessChainId, clearingStatus: "清分成功", clearingStatusType: "warn", createdAt: lifecycle.createdAt }, "PAY-001"),
    /success status style/
  );
  assert.throws(
    () => assertClearingIdentity({ clearingNo: "CLR-001", paymentOrderId: "PAY-001", businessChainId: lifecycle.businessChainId, status: "清分成功", clearingStatusType: "success", createdAt: lifecycle.createdAt }, "PAY-001"),
    /did not reach success/
  );
  for (const invalidOrder of [
    { clearingNo: "CLR-001", paymentOrderId: "PAY-001", ...lifecycle, createdAt: "2026-02-30 10:00:00" },
    { clearingNo: "CLR-001", paymentOrderId: "PAY-001", ...lifecycle, createdAt: undefined }
  ]) {
    assert.throws(
      () => assertClearingIdentity(invalidOrder, "PAY-001"),
      /usable creation time/
    );
  }
  assert.throws(
    () => assertClearingIdentity({ clearingNo: "CLR-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", ...lifecycle }, "PAY-001", "ORDER-001", "2026-09-28 10:01:00"),
    /created before the payment success event/
  );
});

test("requires refund approval and channel submission statuses", () => {
  assert.equal(assertRefundStatus({ status: "APPROVED", statusType: "success" }, "APPROVED", "approval").status, "APPROVED");
  assert.equal(assertRefundStatus({ status: "PROCESSING", statusType: "warn" }, "PROCESSING", "channel submission").status, "PROCESSING");
  assert.equal(assertRefundStatus({ status: "SUCCESS", statusType: "success", channelRefundId: "CHANNEL-REF-001", channelCode: "wx_h5" }, "SUCCESS", "callback").status, "SUCCESS");
  assert.equal(assertRefundStatus({ status: "FAIL", statusType: "danger", channelRefundId: "CHANNEL-REF-001", channelCode: "wx_h5" }, "FAIL", "callback").status, "FAIL");
  assert.throws(
    () => assertRefundStatus({ status: "SUCCESS", statusType: "warn" }, "SUCCESS", "callback"),
    /did not contain the success status style/
  );
  assert.throws(
    () => assertRefundStatus({ status: "SUCCESS", statusType: "success" }, "SUCCESS", "detail query"),
    /did not contain a usable channel refund identifier/
  );
  assert.throws(
    () => assertRefundStatus({ status: "SUCCESS", statusType: "success", channelRefundId: "CHANNEL-REF-001" }, "SUCCESS", "detail query"),
    /did not contain a usable channel code/
  );
  assert.throws(
    () => assertRefundStatus({ status: "FAIL", statusType: "danger", channelCode: "wx_h5" }, "FAIL", "detail query"),
    /did not contain a usable channel refund identifier/
  );
  assert.throws(
    () => assertRefundStatus({ status: "REVIEWING" }, "APPROVED", "approval"),
    /did not reach APPROVED/
  );
});

test("keeps refund and payment identities continuous across refund stages", () => {
  assert.equal(
    assertRefundIdentity({ refundOrderId: "REF-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", orderNo: "ORDER-001" }, "REF-001", "PAY-001", "ORDER-001", "CHAIN-PAY-001").refundOrderId,
    "REF-001"
  );
  assert.equal(
    assertRefundIdentity({ refundOrderId: "REF-001", paymentOrderId: "PAY-001", businessChainId: "  CHAIN-PAY-001  ", orderNo: "ORDER-001" }, "REF-001", "PAY-001", "ORDER-001", "CHAIN-PAY-001").businessChainId,
    "  CHAIN-PAY-001  "
  );
  assert.throws(
    () => assertRefundIdentity({ refundOrderId: "REF-002", paymentOrderId: "PAY-001", orderNo: "ORDER-001" }, "REF-001", "PAY-001", "ORDER-001"),
    /did not match refund order/
  );
  assert.throws(
    () => assertRefundIdentity({ refundOrderId: "REF-001", paymentOrderId: "PAY-002", orderNo: "ORDER-001" }, "REF-001", "PAY-001", "ORDER-001"),
    /did not match payment order/
  );
  assert.throws(
    () => assertRefundIdentity({ refundOrderId: "REF-001", paymentOrderId: "PAY-001", orderNo: "ORDER-002" }, "REF-001", "PAY-001", "ORDER-001"),
    /did not match business order/
  );
  assert.throws(
    () => assertRefundIdentity({ refundOrderId: "REF-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-OTHER", orderNo: "ORDER-001" }, "REF-001", "PAY-001", "ORDER-001", "CHAIN-PAY-001"),
    /did not match business chain/
  );
  assert.throws(
    () => assertRefundIdentity({ refundOrderId: "REF-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001" }, "REF-001", "PAY-001", "ORDER-001", "CHAIN-PAY-001"),
    /did not match business chain/
  );
});

test("keeps the original paid amount continuous across refund responses", () => {
  const refund = { paidAmount: "¥88.00", refundAmount: "¥18.00" };
  assert.equal(assertRefundPaidAmount(refund, "88.00").paidAmount, "¥88.00");
  assert.throws(
    () => assertRefundPaidAmount({ ...refund, paidAmount: "¥80.00" }, "88.00"),
    /payment response amount did not match expected amount/
  );
});

test("keeps the refund method continuous across refund responses", () => {
  assert.equal(assertRefundMethod({ refundMethod: "ORIGINAL" }, "ORIGINAL").refundMethod, "ORIGINAL");
  assert.throws(
    () => assertRefundMethod({ refundMethod: "BALANCE" }, "ORIGINAL"),
    /did not preserve refund method/
  );
});

test("keeps the refund reason continuous across refund responses", () => {
  assert.equal(
    assertRefundReason({ refundReason: "  customer request  " }, "customer request").refundReason,
    "  customer request  "
  );
  assert.throws(
    () => assertRefundReason({ refundReason: "merchant request" }, "customer request"),
    /did not preserve refund reason/
  );
  assert.throws(
    () => assertRefundReason({ refundReason: "" }, "customer request"),
    /did not preserve refund reason/
  );
});

test("keeps the channel refund identifier continuous through refund detail", () => {
  assert.equal(
    assertRefundChannelRefundId(
      { channelRefundId: "  CHANNEL-REF-001  " },
      "CHANNEL-REF-001",
      "detail query"
    ).channelRefundId,
    "  CHANNEL-REF-001  "
  );
  assert.throws(
    () => assertRefundChannelRefundId({ channelRefundId: "CHANNEL-REF-002" }, "CHANNEL-REF-001", "detail query"),
    /did not preserve the channel refund identifier/
  );
  assert.throws(
    () => assertRefundChannelRefundId({ channelRefundId: undefined }, "CHANNEL-REF-001", "detail query"),
    /did not preserve the channel refund identifier/
  );
});

test("requires one correctly directed accounting ledger per business fact", () => {
  const lifecycle = { ledgerNo: "LEDGER-001", ledgerStatus: "已记账", statusType: "success", createdAt: "2026-09-28 10:00:00", amount: "88.00", beforeBalance: "¥0.00", afterBalance: "¥88.00" };
  const ledger = assertAccountingLedger({
    items: [{ accountNo: "ACC-WORKER-001", bizNo: "PAY-001", bizType: "PAYMENT_SUCCESS_EVENT", direction: "贷方", ...lifecycle }]
  }, "PAY-001", "PAYMENT_SUCCESS_EVENT", "贷方", "2026-09-28 09:59:00");
  assert.equal(ledger.bizNo, "PAY-001");
  assert.equal(
    assertAccountingLedger({
      items: [{ accountNo: "ACC-WORKER-001", bizNo: "  PAY-001  ", bizType: "PAYMENT_SUCCESS_EVENT", direction: "贷方", ...lifecycle }]
    }, "PAY-001", "PAYMENT_SUCCESS_EVENT", "贷方").bizNo,
    "  PAY-001  "
  );
  assert.equal(
    assertAccountingLedger({
      items: [{ accountNo: "ACC-WORKER-001", bizNo: "PAY-001", bizType: "  PAYMENT_SUCCESS_EVENT  ", direction: "贷方", ...lifecycle }]
    }, "PAY-001", "PAYMENT_SUCCESS_EVENT", "贷方").bizType,
    "  PAYMENT_SUCCESS_EVENT  "
  );
  assert.throws(
    () => assertAccountingLedger({ items: [] }, "PAY-001", "PAYMENT_SUCCESS_EVENT", "贷方"),
    /did not create exactly one ledger/
  );
  assert.throws(
    () => assertAccountingLedger({ items: [{ accountNo: "ACC-WORKER-001", bizNo: "PAY-001", bizType: "PAYMENT_SUCCESS_EVENT", direction: "贷方", ...lifecycle }] }, "PAY-001", "PAYMENT_SUCCESS_EVENT", "贷方", "2026-09-28 10:01:00"),
    /ledger was created before its source event/
  );
  for (const invalidLedger of [
    { accountNo: "", bizNo: "PAY-001", bizType: "PAYMENT_SUCCESS_EVENT", direction: "贷方", ...lifecycle },
    { accountNo: "ACC-WORKER-001", bizNo: "PAY-001", bizType: "PAYMENT_SUCCESS_EVENT", direction: "贷方", ...lifecycle, ledgerNo: "" },
    { bizNo: "PAY-001", bizType: "PAYMENT_SUCCESS_EVENT", direction: "贷方", ...lifecycle, ledgerStatus: "待记账" },
    { bizNo: "PAY-001", bizType: "PAYMENT_SUCCESS_EVENT", direction: "贷方", ...lifecycle, statusType: "warn" },
    { bizNo: "PAY-001", bizType: "PAYMENT_SUCCESS_EVENT", direction: "贷方", ...lifecycle, createdAt: "2026-02-30 10:00:00" },
    { bizNo: "PAY-001", bizType: "PAYMENT_SUCCESS_EVENT", direction: "贷方", ...lifecycle, afterBalance: "¥87.99" },
    { bizNo: "PAY-001", bizType: "PAYMENT_SUCCESS_EVENT", direction: "贷方", ...lifecycle, amount: "0.00", afterBalance: "¥0.00" },
    { bizNo: "PAY-001", bizType: "PAYMENT_SUCCESS_EVENT", direction: "贷方", ...lifecycle, amount: "-1.00", afterBalance: "¥-1.00" },
    { bizNo: "PAY-001", bizType: "PAYMENT_SUCCESS_EVENT", direction: "贷方", ...lifecycle, beforeBalance: "¥-1.00", afterBalance: "¥87.00" },
    { bizNo: "PAY-001", bizType: "PAYMENT_SUCCESS_EVENT", direction: "贷方", ...lifecycle, afterBalance: "¥-1.00" }
  ]) {
    assert.throws(
      () => assertAccountingLedger({ items: [invalidLedger] }, "PAY-001", "PAYMENT_SUCCESS_EVENT", "贷方"),
      /usable ledger number|usable account number|posted ledger state|creation time|balance movement|ledger amount was not positive/
    );
  }
});

test("keeps accounting ledger amounts continuous with business amounts", () => {
  assert.equal(
    assertAccountingLedgerAmount(
      { items: [{ ledgerNo: "LEDGER-002", accountNo: "ACC-WORKER-001", bizNo: "REF-001", bizType: "REFUND_SUCCESS_EVENT", direction: "借方", amount: "¥18.00", beforeBalance: "¥100.00", afterBalance: "¥82.00", ledgerStatus: "已记账", statusType: "success", createdAt: "2026-09-28 10:00:00" }] },
      "REF-001",
      "REFUND_SUCCESS_EVENT",
      "借方",
      "18.00"
    ).amount,
    "¥18.00"
  );
  assert.throws(
    () => assertAccountingLedgerAmount(
      { items: [{ ledgerNo: "LEDGER-002", accountNo: "ACC-WORKER-001", bizNo: "REF-001", bizType: "REFUND_SUCCESS_EVENT", direction: "借方", amount: "17.99", beforeBalance: "¥100.00", afterBalance: "¥82.01", ledgerStatus: "已记账", statusType: "success", createdAt: "2026-09-28 10:00:00" }] },
      "REF-001",
      "REFUND_SUCCESS_EVENT",
      "借方",
      "18.00"
    ),
    /amount was|balance movement/
  );
  assert.throws(
    () => assertAccountingLedgerAmount(
      { items: [{ ledgerNo: "LEDGER-002", accountNo: "ACC-WORKER-001", bizNo: "REF-001", bizType: "REFUND_SUCCESS_EVENT", direction: "借方", amount: "invalid", beforeBalance: "¥100.00", afterBalance: "¥82.00", ledgerStatus: "已记账", statusType: "success", createdAt: "2026-09-28 10:00:00" }] },
      "REF-001",
      "REFUND_SUCCESS_EVENT",
      "借方",
      "18.00"
    ),
    /amount was|balance movement/
  );
  assert.throws(
    () => assertAccountingLedgerAmount(
      { items: [{ ledgerNo: "LEDGER-002", accountNo: "ACC-WORKER-001", bizNo: "REF-001", bizType: "REFUND_SUCCESS_EVENT", direction: "借方", amount: "18oops", beforeBalance: "¥100.00", afterBalance: "¥82.00", ledgerStatus: "已记账", statusType: "success", createdAt: "2026-09-28 10:00:00" }] },
      "REF-001",
      "REFUND_SUCCESS_EVENT",
      "借方",
      "18.00"
    ),
    /amount was|balance movement/
  );
});

test("uses the worker clearing share for the clearing accounting ledger", () => {
  const ledger = assertClearingAccountingAmount(
    { items: [{ ledgerNo: "LEDGER-003", accountNo: "ACC-WORKER-001", bizNo: "CLR-001", bizType: "CLEARING_GENERATED_EVENT", direction: "贷方", amount: "¥71.16", beforeBalance: "¥0.00", afterBalance: "¥71.16", ledgerStatus: "已记账", statusType: "success", createdAt: "2026-09-28 10:00:00" }] },
    { clearingNo: "CLR-001", workerAmount: "¥71.16" }
  );
  assert.equal(ledger.amount, "¥71.16");
  assert.throws(
    () => assertClearingAccountingAmount(
      { items: [{ ledgerNo: "LEDGER-003", accountNo: "ACC-WORKER-001", bizNo: "CLR-001", bizType: "CLEARING_GENERATED_EVENT", direction: "贷方", amount: "¥88.00", beforeBalance: "¥0.00", afterBalance: "¥88.00", ledgerStatus: "已记账", statusType: "success", createdAt: "2026-09-28 10:00:00" }] },
      { clearingNo: "CLR-001", workerAmount: "¥71.16" }
    ),
    /amount was/
  );
});

test("requires accounting events to match their business fact", () => {
  const lifecycle = { eventStatus: "已消费", statusType: "success", createdAt: "2026-09-28 10:00:00" };
  assert.equal(
    assertAccountingEvent({
      items: [{ eventType: "PAYMENT_SUCCESS", bizNo: "PAY-001", eventNo: "EVENT-001", ...lifecycle, businessChainId: "CHAIN-PAY-001", payload: JSON.stringify({ amount: 88, payScene: "HOME_CLEAN" }) }]
    }, "PAY-001", "PAYMENT_SUCCESS", "CHAIN-PAY-001", "2026-09-28 09:59:00", "88.00", null, null, null, "HOME_CLEAN").eventNo,
    "EVENT-001"
  );
  assert.equal(
    assertAccountingEvent({
      items: [{ eventType: "PAYMENT_SUCCESS", bizNo: "PAY-001", eventNo: "EVENT-001", ...lifecycle, businessChainId: "CHAIN-PAY-001", payload: JSON.stringify({ orderNo: "ORDER-001", amount: 88 }) }]
    }, "PAY-001", "PAYMENT_SUCCESS", "CHAIN-PAY-001", "2026-09-28 09:59:00", "88.00", null, "ORDER-001").eventNo,
    "EVENT-001"
  );
  assert.throws(
    () => assertAccountingEvent({
      items: [{ eventType: "PAYMENT_SUCCESS", bizNo: "PAY-001", eventNo: "EVENT-001", ...lifecycle, businessChainId: "CHAIN-PAY-001", payload: JSON.stringify({ orderNo: "ORDER-002", amount: 88 }) }]
    }, "PAY-001", "PAYMENT_SUCCESS", "CHAIN-PAY-001", "2026-09-28 09:59:00", "88.00", null, "ORDER-001"),
    /payload did not preserve business order/
  );
  assert.equal(
    assertAccountingEvent({
      items: [{ eventType: "CLEARING_GENERATED", bizNo: "CLR-001", eventNo: "EVENT-CLR-001", ...lifecycle, businessChainId: "CHAIN-PAY-001", payload: JSON.stringify({ bizNo: "ORDER-001", amount: 71.16 }) }]
    }, "CLR-001", "CLEARING_GENERATED", "CHAIN-PAY-001", "2026-09-28 09:59:00", "71.16", null, null, "ORDER-001").eventNo,
    "EVENT-CLR-001"
  );
  assert.throws(
    () => assertAccountingEvent({
      items: [{ eventType: "CLEARING_GENERATED", bizNo: "CLR-001", eventNo: "EVENT-CLR-001", ...lifecycle, businessChainId: "CHAIN-PAY-001", payload: JSON.stringify({ bizNo: "ORDER-002", amount: 71.16 }) }]
    }, "CLR-001", "CLEARING_GENERATED", "CHAIN-PAY-001", "2026-09-28 09:59:00", "71.16", null, null, "ORDER-001"),
    /payload did not preserve business order/
  );
  assert.equal(
    assertAccountingEvent({
      items: [{ eventType: "REFUND_SUCCESS", bizNo: "REF-001", eventNo: "EVENT-REF-001", ...lifecycle, businessChainId: "CHAIN-PAY-001", payload: JSON.stringify({ amount: 18 }) }]
    }, "REF-001", "REFUND_SUCCESS", "CHAIN-PAY-001", null, "18.00").eventNo,
    "EVENT-REF-001"
  );
  assert.equal(
    assertAccountingEvent({
      items: [{ eventType: "REFUND_SUCCESS", bizNo: "REF-001", eventNo: "EVENT-REF-001", ...lifecycle, businessChainId: "CHAIN-PAY-001", payload: JSON.stringify({ paymentOrderId: "PAY-001", amount: 18 }) }]
    }, "REF-001", "REFUND_SUCCESS", "CHAIN-PAY-001", null, "18.00", "PAY-001").eventNo,
    "EVENT-REF-001"
  );
  assert.throws(
    () => assertAccountingEvent({
      items: [{ eventType: "REFUND_SUCCESS", bizNo: "REF-001", eventNo: "EVENT-REF-001", ...lifecycle, businessChainId: "CHAIN-PAY-001", payload: JSON.stringify({ paymentOrderId: "PAY-002", amount: 18 }) }]
    }, "REF-001", "REFUND_SUCCESS", "CHAIN-PAY-001", null, "18.00", "PAY-001"),
    /payload did not preserve payment order/
  );
  assert.throws(
    () => assertAccountingEvent({
      items: [{ eventType: "REFUND_SUCCESS", bizNo: "REF-001", eventNo: "EVENT-REF-001", ...lifecycle, businessChainId: "CHAIN-PAY-001", payload: JSON.stringify({ amount: 17.99 }) }]
    }, "REF-001", "REFUND_SUCCESS", "CHAIN-PAY-001", null, "18.00"),
    /payload amount did not match expected amount/
  );
  assert.throws(
    () => assertAccountingEvent({
      items: [{ eventType: "REFUND_SUCCESS", bizNo: "REF-001", eventNo: "EVENT-REF-001", ...lifecycle, businessChainId: "CHAIN-PAY-001", payload: "not-json" }]
    }, "REF-001", "REFUND_SUCCESS", "CHAIN-PAY-001", null, "18.00"),
    /payload amount did not match expected amount/
  );
  assert.equal(
    assertAccountingEvent({
      items: [{ eventType: "PAYMENT_SUCCESS", bizNo: "  PAY-001  ", eventNo: "EVENT-001", ...lifecycle, businessChainId: "CHAIN-PAY-001" }]
    }, "PAY-001", "PAYMENT_SUCCESS", "CHAIN-PAY-001").bizNo,
    "  PAY-001  "
  );
  assert.equal(
    assertAccountingEvent({
      items: [{ eventType: "  PAYMENT_SUCCESS  ", bizNo: "PAY-001", eventNo: "EVENT-001", ...lifecycle, businessChainId: "CHAIN-PAY-001" }]
    }, "PAY-001", "PAYMENT_SUCCESS", "CHAIN-PAY-001").eventType,
    "  PAYMENT_SUCCESS  "
  );
  assert.equal(
    assertAccountingEvent({
      items: [{ eventType: "PAYMENT_SUCCESS", bizNo: "PAY-001", eventNo: "EVENT-001", ...lifecycle, businessChainId: "  CHAIN-PAY-001  " }]
    }, "PAY-001", "PAYMENT_SUCCESS", "CHAIN-PAY-001").businessChainId,
    "  CHAIN-PAY-001  "
  );
  assert.throws(
    () => assertAccountingEvent({
      items: [{ eventType: "PAYMENT_SUCCESS", bizNo: "PAY-002" }]
    }, "PAY-001", "PAYMENT_SUCCESS"),
    /did not produce exactly one consumed matching event/
  );
  assert.throws(
    () => assertAccountingEvent({
      items: [{ eventType: "PAYMENT_SUCCESS", bizNo: "PAY-001", eventNo: "EVENT-001", eventStatus: "待消费", statusType: "warn", createdAt: lifecycle.createdAt }]
    }, "PAY-001", "PAYMENT_SUCCESS"),
    /did not produce exactly one consumed matching event/
  );
  assert.throws(
    () => assertAccountingEvent({
      items: [{ eventType: "PAYMENT_SUCCESS", bizNo: "PAY-001", eventStatus: "已消费", statusType: "success", createdAt: lifecycle.createdAt }]
    }, "PAY-001", "PAYMENT_SUCCESS"),
    /did not contain a usable event number/
  );
  assert.throws(
    () => assertAccountingEvent({
      items: [
        { eventType: "PAYMENT_SUCCESS", bizNo: "PAY-001", ...lifecycle },
        { eventType: "PAYMENT_SUCCESS", bizNo: "PAY-001", ...lifecycle }
      ]
    }, "PAY-001", "PAYMENT_SUCCESS"),
    /did not produce exactly one consumed matching event/
  );
  assert.throws(
    () => assertAccountingEvent({
      items: [{ eventType: "PAYMENT_SUCCESS", bizNo: "PAY-001", eventNo: "EVENT-001", ...lifecycle, businessChainId: "CHAIN-PAY-002" }]
    }, "PAY-001", "PAYMENT_SUCCESS", "CHAIN-PAY-001"),
    /did not match business chain/
  );
  assert.throws(
    () => assertAccountingEvent({
      items: [{ eventType: "PAYMENT_SUCCESS", bizNo: "PAY-001", eventNo: "EVENT-001", ...lifecycle }]
    }, "PAY-001", "PAYMENT_SUCCESS", null, "2026-09-28 10:01:00"),
    /was created before its source fact/
  );
  for (const invalidEvent of [
    { eventType: "PAYMENT_SUCCESS", bizNo: "PAY-001", eventNo: "EVENT-001", ...lifecycle, statusType: "warn" },
    { eventType: "PAYMENT_SUCCESS", bizNo: "PAY-001", eventNo: "EVENT-001", ...lifecycle, createdAt: "2026-02-30 10:00:00" },
    { eventType: "PAYMENT_SUCCESS", bizNo: "PAY-001", eventNo: "EVENT-001", eventStatus: "已消费", statusType: "success" }
  ]) {
    assert.throws(
      () => assertAccountingEvent({ items: [invalidEvent] }, "PAY-001", "PAYMENT_SUCCESS"),
      /consumed event success style|usable creation time/
    );
  }
});

test("requires one completed payment success event after repeated callbacks", () => {
  const lifecycle = { createdAt: "2026-09-28 10:00:00", lastPublishedAt: "2026-09-28 10:01:00" };
  const event = assertSinglePaymentSuccessEvent({
    items: [{
      eventNo: "EVENT-001",
      paymentOrderId: "PAY-001",
      businessChainId: "CHAIN-PAY-001",
      bizNo: "ORDER-001",
      eventType: "PAYMENT_SUCCESS",
      publishStatus: "SUCCESS",
      publishStatusType: "success",
      paymentSuccessHttpStage: "COMPLETED",
      amount: "88.00",
      eventPayload: JSON.stringify({ channel: "wx_h5", payScene: "HOME_CLEAN" }),
      ...lifecycle
    }]
  }, "PAY-001", "ORDER-001", "CHAIN-PAY-001", null, "88.00", "wx_h5", "HOME_CLEAN");
  assert.equal(event.paymentOrderId, "PAY-001");
  assert.equal(event.bizNo, "ORDER-001");
  assert.throws(
    () => assertSinglePaymentSuccessEvent({
      items: [{
        eventNo: "EVENT-001",
        paymentOrderId: "PAY-001",
        businessChainId: "CHAIN-PAY-001",
        bizNo: "ORDER-001",
        eventType: "PAYMENT_SUCCESS",
        publishStatus: "SUCCESS",
        publishStatusType: "success",
        paymentSuccessHttpStage: "COMPLETED",
        amount: "88.00",
        eventPayload: JSON.stringify({ channel: "wx_h5", payScene: "HOME_REPAIR" }),
        ...lifecycle
      }]
    }, "PAY-001", "ORDER-001", "CHAIN-PAY-001", null, "88.00", "wx_h5", "HOME_CLEAN"),
    /did not preserve the payment scene/
  );
  assert.throws(
    () => assertSinglePaymentSuccessEvent({
      items: [{
        eventNo: "EVENT-001",
        paymentOrderId: "PAY-001",
        businessChainId: "CHAIN-PAY-001",
        bizNo: "ORDER-001",
        eventType: "PAYMENT_SUCCESS",
        publishStatus: "SUCCESS",
        publishStatusType: "success",
        paymentSuccessHttpStage: "COMPLETED",
        amount: "88.00",
        eventPayload: JSON.stringify({ channel: "alipay_h5" }),
        ...lifecycle
      }]
    }, "PAY-001", "ORDER-001", "CHAIN-PAY-001", null, "88.00", "wx_h5"),
    /did not preserve the callback channel/
  );
  assert.throws(
    () => assertSinglePaymentSuccessEvent({
      items: [{
        eventNo: "EVENT-001",
        paymentOrderId: "PAY-001",
        businessChainId: "CHAIN-PAY-001",
        bizNo: "ORDER-001",
        eventType: "PAYMENT_SUCCESS",
        publishStatus: "SUCCESS",
        publishStatusType: "success",
        paymentSuccessHttpStage: "COMPLETED",
        amount: "88.00",
        eventPayload: "not-json",
        ...lifecycle
      }]
    }, "PAY-001", "ORDER-001", "CHAIN-PAY-001", null, "88.00", "wx_h5"),
    /did not preserve the callback channel/
  );
  assert.throws(
    () => assertSinglePaymentSuccessEvent({
      items: [{
        eventNo: "EVENT-001",
        paymentOrderId: "PAY-001",
        businessChainId: "CHAIN-PAY-001",
        bizNo: "ORDER-001",
        eventType: "PAYMENT_SUCCESS",
        publishStatus: "SUCCESS",
        publishStatusType: "success",
        paymentSuccessHttpStage: "COMPLETED",
        amount: "87.99",
        ...lifecycle
      }]
    }, "PAY-001", "ORDER-001", "CHAIN-PAY-001", null, "88.00"),
    /payment response amount did not match expected amount/
  );
  assert.equal(
    assertSinglePaymentSuccessEvent({
      items: [{
        eventNo: "EVENT-001",
        paymentOrderId: "PAY-001",
        bizNo: "ORDER-001",
        eventType: "PAYMENT_SUCCESS",
        publishStatus: "SUCCESS",
        publishStatusType: "success",
        paymentSuccessHttpStage: "COMPLETED",
        createdAt: "2026-09-28 10:01:00",
        lastPublishedAt: "2026-09-28 10:02:00"
      }]
    }, "PAY-001", "ORDER-001", null, "2026-09-28 10:01:00").eventNo,
    "EVENT-001"
  );
  assert.throws(
    () => assertSinglePaymentSuccessEvent({
      items: [{
        eventNo: "EVENT-001",
        paymentOrderId: "PAY-001",
        bizNo: "ORDER-001",
        eventType: "PAYMENT_SUCCESS",
        publishStatus: "SUCCESS",
        publishStatusType: "success",
        paymentSuccessHttpStage: "COMPLETED",
        ...lifecycle
      }]
    }, "PAY-001", "ORDER-001", null, "2026-09-28 10:01:00"),
    /created before the payment success fact/
  );
  assert.equal(isPaymentSuccessEventDispatched({
    eventType: "  PAYMENT_SUCCESS  ",
    publishStatus: "SUCCESS",
    paymentSuccessHttpStage: "COMPLETED"
  }), true);
  assert.equal(isPaymentSuccessEventDispatched({
    eventType: "PAYMENT_SUCCESS",
    publishStatus: "PENDING",
    paymentSuccessHttpStage: "COMPLETED"
  }), false);
  const dispatchedEvent = {
    paymentOrderId: "PAY-001",
    bizNo: "ORDER-001",
    eventType: "PAYMENT_SUCCESS",
    publishStatus: "SUCCESS",
    paymentSuccessHttpStage: "COMPLETED"
  };
  assert.equal(isPaymentSuccessEventDispatched(dispatchedEvent, "PAY-001", "ORDER-001"), true);
  assert.equal(isPaymentSuccessEventDispatched({ ...dispatchedEvent, paymentOrderId: "PAY-002" }, "PAY-001", "ORDER-001"), false);
  assert.equal(isPaymentSuccessEventDispatched({ ...dispatchedEvent, bizNo: "ORDER-002" }, "PAY-001", "ORDER-001"), false);
  assert.equal(
    assertSinglePaymentSuccessEvent({
      items: [{
        eventNo: "EVENT-001",
        paymentOrderId: "PAY-001",
        businessChainId: "CHAIN-PAY-001",
        bizNo: "ORDER-001",
        eventType: "  PAYMENT_SUCCESS  ",
        publishStatus: "SUCCESS",
        publishStatusType: "success",
        paymentSuccessHttpStage: "COMPLETED",
        ...lifecycle
      }]
    }, "PAY-001", "ORDER-001", "CHAIN-PAY-001").eventType,
    "  PAYMENT_SUCCESS  "
  );
  assert.equal(
    assertSinglePaymentSuccessEvent({
      items: [{
        eventNo: "EVENT-001",
        paymentOrderId: "PAY-001",
        businessChainId: "CHAIN-PAY-001",
        bizNo: "  ORDER-001  ",
        eventType: "PAYMENT_SUCCESS",
        publishStatus: "SUCCESS",
        publishStatusType: "success",
        paymentSuccessHttpStage: "COMPLETED",
        ...lifecycle
      }]
    }, "PAY-001", "ORDER-001", "CHAIN-PAY-001").bizNo,
    "  ORDER-001  "
  );
  assert.equal(
    assertSinglePaymentSuccessEvent({
      items: [{
        eventNo: "EVENT-001",
        paymentOrderId: "  PAY-001  ",
        businessChainId: "CHAIN-PAY-001",
        bizNo: "ORDER-001",
        eventType: "PAYMENT_SUCCESS",
        publishStatus: "SUCCESS",
        publishStatusType: "success",
        paymentSuccessHttpStage: "COMPLETED",
        ...lifecycle
      }]
    }, "PAY-001", "ORDER-001", "CHAIN-PAY-001").paymentOrderId,
    "  PAY-001  "
  );
  assert.equal(
    assertSinglePaymentSuccessEvent({
      items: [{
        eventNo: "EVENT-001",
        paymentOrderId: "PAY-001",
        businessChainId: "  CHAIN-PAY-001  ",
        bizNo: "ORDER-001",
        eventType: "PAYMENT_SUCCESS",
        publishStatus: "SUCCESS",
        publishStatusType: "success",
        paymentSuccessHttpStage: "COMPLETED",
        ...lifecycle
      }]
    }, "PAY-001", "ORDER-001", "CHAIN-PAY-001").businessChainId,
    "  CHAIN-PAY-001  "
  );
  assert.throws(
    () => assertSinglePaymentSuccessEvent({
      items: [{
        eventNo: "EVENT-001",
        paymentOrderId: "PAY-001",
        bizNo: "ORDER-001",
        eventType: "PAYMENT_SUCCESS",
        publishStatus: "SUCCESS",
        publishStatusType: "success",
        paymentSuccessHttpStage: "COMPLETED",
        ...lifecycle
      }]
    }, "PAY-001", "ORDER-001", "CHAIN-PAY-001"),
    /did not match business chain/
  );
  assert.throws(
    () => assertSinglePaymentSuccessEvent({
      items: [{
        eventNo: "EVENT-001",
        paymentOrderId: "PAY-001",
        businessChainId: "CHAIN-OTHER",
        bizNo: "ORDER-001",
        eventType: "PAYMENT_SUCCESS",
        publishStatus: "SUCCESS",
        publishStatusType: "success",
        paymentSuccessHttpStage: "COMPLETED",
        ...lifecycle
      }]
    }, "PAY-001", "ORDER-001", "CHAIN-PAY-001"),
    /did not match business chain/
  );
  assert.throws(
    () => assertSinglePaymentSuccessEvent({
      items: [{
        eventNo: "EVENT-001",
        paymentOrderId: "PAY-001",
        bizNo: "ORDER-002",
        eventType: "PAYMENT_SUCCESS",
        publishStatus: "SUCCESS",
        publishStatusType: "success",
        paymentSuccessHttpStage: "COMPLETED",
        ...lifecycle
      }]
    }, "PAY-001", "ORDER-001"),
    /exactly one completed PAYMENT_SUCCESS event with an event number/
  );
  assert.throws(
    () => assertSinglePaymentSuccessEvent({
    items: [
        { eventNo: "EVENT-001", paymentOrderId: "PAY-001", eventType: "PAYMENT_SUCCESS", publishStatus: "SUCCESS", publishStatusType: "success", paymentSuccessHttpStage: "COMPLETED", ...lifecycle },
        { eventNo: "EVENT-002", paymentOrderId: "PAY-001", eventType: "PAYMENT_SUCCESS", publishStatus: "SUCCESS", publishStatusType: "success", paymentSuccessHttpStage: "COMPLETED", ...lifecycle }
      ]
    }, "PAY-001"),
    /exactly one completed PAYMENT_SUCCESS event with an event number/
  );
  assert.throws(
    () => assertSinglePaymentSuccessEvent({
      items: [{
        paymentOrderId: "PAY-001",
        eventType: "PAYMENT_SUCCESS",
        publishStatus: "SUCCESS",
        publishStatusType: "success",
        paymentSuccessHttpStage: "COMPLETED",
        ...lifecycle
      }]
    }, "PAY-001"),
    /exactly one completed PAYMENT_SUCCESS event with an event number/
  );
  assert.throws(
    () => assertSinglePaymentSuccessEvent({
      items: [
        {
          eventNo: "EVENT-001",
          paymentOrderId: "PAY-001",
          eventType: "PAYMENT_SUCCESS",
          publishStatus: "SUCCESS",
          paymentSuccessHttpStage: "COMPLETED",
          ...lifecycle
        },
        {
          eventNo: "EVENT-002",
          paymentOrderId: "PAY-001",
          eventType: "PAYMENT_SUCCESS",
          publishStatus: "FAIL",
          paymentSuccessHttpStage: "FAILED",
          ...lifecycle
        }
      ]
    }, "PAY-001"),
    /exactly one completed PAYMENT_SUCCESS event with an event number/
  );
  assert.throws(
    () => assertSinglePaymentSuccessEvent({
      items: [{
        eventNo: "EVENT-001",
        paymentOrderId: "PAY-001",
        bizNo: "ORDER-001",
        eventType: "PAYMENT_SUCCESS",
        publishStatus: "SUCCESS",
        publishStatusType: "warn",
        paymentSuccessHttpStage: "COMPLETED"
      }]
    }, "PAY-001", "ORDER-001"),
    /exactly one completed PAYMENT_SUCCESS event with an event number/
  );
  for (const invalidEvent of [
    { eventNo: "EVENT-001", paymentOrderId: "PAY-001", bizNo: "ORDER-001", eventType: "PAYMENT_SUCCESS", publishStatus: "SUCCESS", publishStatusType: "success", paymentSuccessHttpStage: "COMPLETED", lastPublishedAt: lifecycle.lastPublishedAt },
    { eventNo: "EVENT-001", paymentOrderId: "PAY-001", bizNo: "ORDER-001", eventType: "PAYMENT_SUCCESS", publishStatus: "SUCCESS", publishStatusType: "success", paymentSuccessHttpStage: "COMPLETED", ...lifecycle, createdAt: "2026-02-30 10:00:00" },
    { eventNo: "EVENT-001", paymentOrderId: "PAY-001", bizNo: "ORDER-001", eventType: "PAYMENT_SUCCESS", publishStatus: "SUCCESS", publishStatusType: "success", paymentSuccessHttpStage: "COMPLETED", ...lifecycle, lastPublishedAt: "2026-09-28 25:00:00" },
    { eventNo: "EVENT-001", paymentOrderId: "PAY-001", bizNo: "ORDER-001", eventType: "PAYMENT_SUCCESS", publishStatus: "SUCCESS", publishStatusType: "success", paymentSuccessHttpStage: "COMPLETED", ...lifecycle, lastPublishedAt: "2026-09-28 09:59:00" }
  ]) {
    assert.throws(
      () => assertSinglePaymentSuccessEvent({ items: [invalidEvent] }, "PAY-001", "ORDER-001"),
      /exactly one completed PAYMENT_SUCCESS event/
    );
  }
});

test("requires one refund success outbox event for the current refund aggregate", () => {
  const lifecycle = { createdAt: "2026-09-28 10:00:00", updatedAt: "2026-09-28 10:00:00" };
  const event = assertSingleRefundSuccessOutbox({
    items: [{
      eventId: "REVT-001",
      eventType: "REFUND_SUCCESS",
      aggregateId: "REF-001",
      payloadJson: JSON.stringify({ refundOrderId: "REF-001", paymentOrderId: "PAY-001", payScene: "HOME_CLEAN", refundAmount: "18.00" }),
      status: "PENDING",
      ...lifecycle
    }]
  }, "REF-001", null, "PAY-001", "18.00", "HOME_CLEAN");
  assert.equal(event.eventId, "REVT-001");
  for (const payloadJson of [
    JSON.stringify({ refundOrderId: "REF-002", paymentOrderId: "PAY-001", payScene: "HOME_CLEAN", refundAmount: "18.00" }),
    JSON.stringify({ refundOrderId: "REF-001", paymentOrderId: "PAY-002", payScene: "HOME_CLEAN", refundAmount: "18.00" }),
    JSON.stringify({ refundOrderId: "REF-001", paymentOrderId: "PAY-001", payScene: "HOME_CLEAN", refundAmount: "17.99" }),
    "not-json"
  ]) {
    assert.throws(
      () => assertSingleRefundSuccessOutbox({
        items: [{ eventId: "REVT-001", eventType: "REFUND_SUCCESS", aggregateId: "REF-001", payloadJson, status: "PENDING", ...lifecycle }]
      }, "REF-001", null, "PAY-001", "18.00", "HOME_CLEAN"),
      /outbox payload did not preserve refund identity and amount/
    );
  }
  assert.equal(
    assertSingleRefundSuccessOutbox({
      items: [{
        eventId: "REVT-001",
        eventType: "REFUND_SUCCESS",
        aggregateId: "REF-001",
        status: "PENDING",
        createdAt: "2026-09-28 10:01:00",
        updatedAt: "2026-09-28 10:01:00"
      }]
    }, "REF-001", "2026-09-28 10:01:00").eventId,
    "REVT-001"
  );
  assert.throws(
    () => assertSingleRefundSuccessOutbox({ items: [{ ...lifecycle, eventId: "REVT-001", eventType: "REFUND_SUCCESS", aggregateId: "REF-001", status: "PENDING" }] }, "REF-001", "2026-09-28 10:01:00"),
    /created before the refund terminal fact/
  );
  assert.equal(
    assertSingleRefundSuccessOutbox({
      items: [{ eventId: "REVT-001", eventType: "  REFUND_SUCCESS  ", aggregateId: "REF-001", status: "PENDING", ...lifecycle }]
    }, "REF-001").eventType,
    "  REFUND_SUCCESS  "
  );
  assert.equal(
    assertSingleRefundSuccessOutbox({
      items: [{ eventId: "REVT-001", eventType: "REFUND_SUCCESS", aggregateId: "  REF-001  ", status: "PENDING", ...lifecycle }]
    }, "REF-001").aggregateId,
    "  REF-001  "
  );
  assert.throws(
    () => assertSingleRefundSuccessOutbox({
      items: [{ eventId: "REVT-001", eventType: "REFUND_SUCCESS", aggregateId: "REF-002", status: "PENDING", ...lifecycle }]
    }, "REF-001"),
    /exactly one pending REFUND_SUCCESS outbox event/
  );
  assert.throws(
    () => assertSingleRefundSuccessOutbox({
      items: [{ eventId: "REVT-001", eventType: "REFUND_SUCCESS", aggregateId: "REF-001", status: "SENT", ...lifecycle }]
    }, "REF-001"),
    /exactly one pending REFUND_SUCCESS outbox event/
  );
  assert.throws(
    () => assertSingleRefundSuccessOutbox({
      items: [{ eventId: "REVT-001", eventType: "REFUND_SUCCESS", aggregateId: "REF-001", ...lifecycle }]
    }, "REF-001"),
    /exactly one pending REFUND_SUCCESS outbox event/
  );
  assert.throws(
    () => assertSingleRefundSuccessOutbox({
      items: [
        { eventId: "REVT-001", eventType: "REFUND_SUCCESS", aggregateId: "REF-001", status: "PENDING", ...lifecycle },
        { eventId: "REVT-002", eventType: "REFUND_SUCCESS", aggregateId: "REF-001", status: "SENT", ...lifecycle }
      ]
    }, "REF-001"),
    /exactly one pending REFUND_SUCCESS outbox event/
  );
  for (const invalidEvent of [
    { eventId: "REVT-001", eventType: "REFUND_SUCCESS", aggregateId: "REF-001", status: "PENDING", updatedAt: lifecycle.updatedAt },
    { eventId: "REVT-001", eventType: "REFUND_SUCCESS", aggregateId: "REF-001", status: "PENDING", ...lifecycle, createdAt: "2026-02-30 10:00:00" },
    { eventId: "REVT-001", eventType: "REFUND_SUCCESS", aggregateId: "REF-001", status: "PENDING", ...lifecycle, updatedAt: "2026-09-28 25:00:00" },
    { eventId: "REVT-001", eventType: "REFUND_SUCCESS", aggregateId: "REF-001", status: "PENDING", ...lifecycle, updatedAt: "2026-09-28 09:59:00" },
    { eventId: "REVT-001", eventType: "REFUND_SUCCESS", aggregateId: "REF-001", status: "PENDING", ...lifecycle, lastRelayAt: "2026-09-28 10:01:00" }
  ]) {
    assert.throws(
      () => assertSingleRefundSuccessOutbox({ items: [invalidEvent] }, "REF-001"),
      /pending outbox event/
    );
  }
});

test("requires refund outbox dispatch responses to preserve event identity", () => {
  const source = {
    eventId: "REVT-001",
    eventType: "REFUND_SUCCESS",
    aggregateId: "REF-001",
    payloadJson: JSON.stringify({ refundOrderId: "REF-001", paymentOrderId: "PAY-001", payScene: "HOME_CLEAN", refundAmount: "18.00" }),
    status: "PENDING",
    createdAt: "2026-09-28 10:00:00",
    updatedAt: "2026-09-28 10:00:00"
  };
  const dispatched = assertRefundOutboxDispatch({
    eventId: "REVT-001",
    eventType: "REFUND_SUCCESS",
    aggregateId: "REF-001",
    payloadJson: source.payloadJson,
    status: "SENT",
    createdAt: "2026-09-28 10:00:00",
    updatedAt: "2026-09-28 10:01:00",
    lastRelayAt: "2026-09-28 10:01:00"
  }, source, "REF-001", "PAY-001", "18.00", "HOME_CLEAN");
  assert.equal(dispatched.status, "SENT");
  assert.throws(
    () => assertRefundOutboxDispatch({ ...dispatched, payloadJson: JSON.stringify({ ...JSON.parse(source.payloadJson), refundAmount: "17.99" }) }, source, "REF-001", "PAY-001", "18.00"),
    /outbox payload did not preserve refund identity and amount/
  );
  assert.equal(
    assertRefundOutboxDispatch({ ...dispatched, eventId: "  REVT-001  " }, source, "REF-001").eventId,
    "  REVT-001  "
  );
  assert.equal(
    assertRefundOutboxDispatch({ ...dispatched, aggregateId: "  REF-001  " }, source, "REF-001").aggregateId,
    "  REF-001  "
  );
  assert.equal(
    assertRefundOutboxDispatch({ ...dispatched, eventType: "  REFUND_SUCCESS  " }, source, "REF-001").eventType,
    "  REFUND_SUCCESS  "
  );
  for (const response of [
    { eventId: "REVT-002", eventType: "REFUND_SUCCESS", aggregateId: "REF-001", status: "SENT", createdAt: "2026-09-28 10:00:00", updatedAt: "2026-09-28 10:01:00", lastRelayAt: "2026-09-28 10:01:00" },
    { eventId: "REVT-001", eventType: "PAYMENT_SUCCESS", aggregateId: "REF-001", status: "SENT", createdAt: "2026-09-28 10:00:00", updatedAt: "2026-09-28 10:01:00", lastRelayAt: "2026-09-28 10:01:00" },
    { eventId: "REVT-001", eventType: "REFUND_SUCCESS", aggregateId: "REF-002", status: "SENT", createdAt: "2026-09-28 10:00:00", updatedAt: "2026-09-28 10:01:00", lastRelayAt: "2026-09-28 10:01:00" },
    { eventId: "REVT-001", eventType: "REFUND_SUCCESS", aggregateId: "REF-001", status: "SENT", createdAt: "2026-09-28 10:00:01", updatedAt: "2026-09-28 10:01:00", lastRelayAt: "2026-09-28 10:01:00" },
    { eventId: "REVT-001", eventType: "REFUND_SUCCESS", aggregateId: "REF-001", status: "PENDING", createdAt: "2026-09-28 10:00:00", updatedAt: "2026-09-28 10:00:00" }
  ]) {
    assert.throws(
      () => assertRefundOutboxDispatch(response, source, "REF-001"),
      /refund outbox dispatch/
    );
  }
  for (const invalidResponse of [
    { ...dispatched, createdAt: "2026-02-30 10:00:00" },
    { ...dispatched, updatedAt: "2026-09-28 25:00:00" },
    { ...dispatched, updatedAt: "2026-09-28 10:02:00", lastRelayAt: "2026-09-28 10:01:00" },
    { ...dispatched, lastRelayAt: null }
  ]) {
    assert.throws(
      () => assertRefundOutboxDispatch(invalidResponse, source, "REF-001"),
      /outbox dispatch did not contain usable lifecycle timestamps|outbox dispatch lifecycle timestamps were out of order/
    );
  }
  assert.throws(
    () => assertRefundOutboxDispatch({ ...dispatched, createdAt: "2026-09-28 10:00:01" }, source, "REF-001"),
    /changed the original creation time/
  );
});

test("requires reconciliation snapshots to match one business chain", () => {
  const snapshot = assertReconciliationChainIdentity({
    businessChainId: "CHAIN-PAY-001",
    factCount: 1,
    facts: [{ businessChainId: "CHAIN-PAY-001" }]
  }, "CHAIN-PAY-001");
  assert.equal(snapshot.businessChainId, "CHAIN-PAY-001");
  assert.equal(
    assertReconciliationChainIdentity({
      businessChainId: "  CHAIN-PAY-001  ",
      factCount: 1,
      facts: [{ businessChainId: "  CHAIN-PAY-001  " }]
    }, "CHAIN-PAY-001").businessChainId,
    "  CHAIN-PAY-001  "
  );
  assert.throws(
    () => assertReconciliationChainIdentity({
      businessChainId: "CHAIN-PAY-002",
      factCount: 1,
      facts: [{ businessChainId: "CHAIN-PAY-002" }]
    }, "CHAIN-PAY-001"),
    /did not match requested business chain/
  );
  assert.throws(
    () => assertReconciliationChainIdentity({
      businessChainId: "CHAIN-PAY-001",
      factCount: 2,
      facts: [{ businessChainId: "CHAIN-PAY-001" }]
    }, "CHAIN-PAY-001"),
    /fact count did not match/
  );
});

test("requires completed reconciliation snapshots to contain no abnormal facts", () => {
  assert.equal(
    assertReconciliationCompletion({ hasAbnormalFact: false, abnormalFactCount: 0 }).abnormalFactCount,
    0
  );
  for (const invalidSnapshot of [
    { hasAbnormalFact: true, abnormalFactCount: 1 },
    { hasAbnormalFact: false, abnormalFactCount: 1 },
    { hasAbnormalFact: undefined, abnormalFactCount: 0 }
  ]) {
    assert.throws(
      () => assertReconciliationCompletion(invalidSnapshot),
      /contained abnormal facts/
    );
  }
});

test("requires completed reconciliation snapshots to expose a latest fact time", () => {
  const snapshot = assertReconciliationLastFactTime({
    lastFactAt: "2026-09-28T10:00:00",
    facts: [
      { occurredAt: "2026-09-28T09:59:00" },
      { occurredAt: "2026-09-28T10:00:00" }
    ]
  });
  assert.equal(snapshot.lastFactAt, "2026-09-28T10:00:00");
  for (const invalidSnapshot of [{}, { lastFactAt: "2026-02-30T10:00:00" }, { lastFactAt: "not-a-time" }]) {
    assert.throws(
      () => assertReconciliationLastFactTime(invalidSnapshot),
      /usable last fact time/
    );
  }
  assert.throws(
    () => assertReconciliationLastFactTime({
      lastFactAt: "2026-09-28T10:00:00",
      facts: [{ occurredAt: "2026-09-28T10:00:01" }]
    }),
    /did not cover every fact occurrence/
  );
  assert.throws(
    () => assertReconciliationLastFactTime({
      lastFactAt: "2026-09-28T10:01:00",
      facts: [{ occurredAt: "2026-09-28T10:00:00" }]
    }),
    /did not equal the latest fact occurrence/
  );
  assert.throws(
    () => assertReconciliationLastFactTime({
      lastFactAt: "2026-09-28T10:00:00",
      facts: [{ occurredAt: "not-a-time" }]
    }),
    /did not cover every fact occurrence/
  );
});

test("requires reconciliation facts to preserve their business amounts", () => {
  const lifecycle = { occurredAt: "2026-09-28T10:00:00", createdAt: "2026-09-28T10:00:01" };
  const snapshot = {
    facts: [{
      systemCode: "payment-core",
      factType: "PAYMENT_SUCCESS",
      factNo: "PAY-001",
      businessNo: "ORDER-001",
      factStatus: "SUCCESS",
      amount: "¥88.00",
      ...lifecycle
    }]
  };
  assert.equal(
    assertReconciliationFactAmounts(snapshot, [["payment-core", "PAYMENT_SUCCESS", "PAY-001", "88.00", "ORDER-001"]]),
    snapshot
  );
  assert.equal(
    assertReconciliationFactAmounts({
      facts: [{ ...snapshot.facts[0], businessNo: "  ORDER-001  " }]
    }, [["payment-core", "PAYMENT_SUCCESS", "PAY-001", "88.00", "ORDER-001"]]).facts[0].businessNo,
    "  ORDER-001  "
  );
  assert.equal(
    assertReconciliationFactAmounts({
      facts: [{ ...snapshot.facts[0], factNo: "  PAY-001  " }]
    }, [["payment-core", "PAYMENT_SUCCESS", "PAY-001", "88.00", "ORDER-001"]]).facts[0].factNo,
    "  PAY-001  "
  );
  assert.equal(
    findReconciliationFacts([{ ...snapshot.facts[0], factNo: "  PAY-001  " }], "payment-core", "PAYMENT_SUCCESS", "PAY-001").length,
    1
  );
  assert.equal(
    findReconciliationFacts([{ ...snapshot.facts[0], factNo: "PAY-002" }], "payment-core", "PAYMENT_SUCCESS", "PAY-001").length,
    0
  );
  assert.equal(
    assertReconciliationFactAmounts({
      facts: [{ ...snapshot.facts[0], factType: "  PAYMENT_SUCCESS  " }]
    }, [["payment-core", "PAYMENT_SUCCESS", "PAY-001", "88.00", "ORDER-001"]]).facts[0].factType,
    "  PAYMENT_SUCCESS  "
  );
  assert.equal(
    assertReconciliationFactAmounts({
      facts: [{ ...snapshot.facts[0], systemCode: "  payment-core  " }]
    }, [["payment-core", "PAYMENT_SUCCESS", "PAY-001", "88.00", "ORDER-001"]]).facts[0].systemCode,
    "  payment-core  "
  );
  assert.throws(
    () => assertReconciliationFactAmounts(snapshot, [["payment-core", "PAYMENT_SUCCESS", "PAY-001", "87.99", "ORDER-001"]]),
    /amount was/
  );
  assert.throws(
    () => assertReconciliationFactAmounts({
      facts: [{ systemCode: "payment-core", factType: "PAYMENT_SUCCESS", factNo: "PAY-001", businessNo: "ORDER-001", factStatus: "SUCCESS", amount: "88oops", ...lifecycle }]
    }, [["payment-core", "PAYMENT_SUCCESS", "PAY-001", "88.00", "ORDER-001"]]),
    /amount was/
  );
  assert.throws(
    () => assertReconciliationFactAmounts({ facts: [] }, [["payment-core", "PAYMENT_SUCCESS", "PAY-001", "88.00", "ORDER-001"]]),
    /fact count was 0, expected 1|exactly one SUCCESS fact/
  );
  assert.throws(
    () => assertReconciliationFactAmounts({
      facts: [
        { systemCode: "payment-core", factType: "PAYMENT_SUCCESS", factNo: "PAY-001", businessNo: "ORDER-001", factStatus: "SUCCESS", amount: "88.00", ...lifecycle },
        { systemCode: "payment-core", factType: "PAYMENT_SUCCESS", factNo: "PAY-001", businessNo: "ORDER-001", factStatus: "SUCCESS", amount: "88.00", ...lifecycle }
      ]
    }, [["payment-core", "PAYMENT_SUCCESS", "PAY-001", "88.00", "ORDER-001"]]),
    /fact count was 2, expected 1|exactly one SUCCESS fact/
  );
  assert.throws(
    () => assertReconciliationFactAmounts({
      facts: [
        { ...snapshot.facts[0] },
        { systemCode: "settlement-system", factType: "UNEXPECTED_SUCCESS", factNo: "UNEXPECTED-001", businessNo: "ORDER-001", factStatus: "SUCCESS", amount: "0.01", ...lifecycle }
      ]
    }, [["payment-core", "PAYMENT_SUCCESS", "PAY-001", "88.00", "ORDER-001"]]),
    /fact count was 2, expected 1/
  );
  assert.throws(
    () => assertReconciliationFactAmounts({
      facts: [{ systemCode: "payment-core", factType: "PAYMENT_SUCCESS", factNo: "PAY-001", businessNo: "ORDER-001", factStatus: "PENDING", amount: "88.00" }]
    }, [["payment-core", "PAYMENT_SUCCESS", "PAY-001", "88.00", "ORDER-001"]]),
    /exactly one SUCCESS fact/
  );
  for (const invalidFact of [
    { systemCode: "payment-core", factType: "PAYMENT_SUCCESS", factNo: "PAY-001", businessNo: "ORDER-001", factStatus: "SUCCESS", amount: "88.00", ...lifecycle, occurredAt: "2026-02-30T10:00:00" },
    { systemCode: "payment-core", factType: "PAYMENT_SUCCESS", factNo: "PAY-001", businessNo: "ORDER-001", factStatus: "SUCCESS", amount: "88.00", ...lifecycle, createdAt: "2026-09-28T25:00:00" },
    { systemCode: "payment-core", factType: "PAYMENT_SUCCESS", factNo: "PAY-001", businessNo: "ORDER-001", factStatus: "SUCCESS", amount: "88.00", occurredAt: lifecycle.occurredAt }
  ]) {
    assert.throws(
      () => assertReconciliationFactAmounts({ facts: [invalidFact] }, [["payment-core", "PAYMENT_SUCCESS", "PAY-001", "88.00", "ORDER-001"]]),
      /usable lifecycle timestamps/
    );
  }
  assert.throws(
    () => assertReconciliationFactAmounts({
      facts: [{ systemCode: "payment-core", factType: "PAYMENT_SUCCESS", factNo: "PAY-001", businessNo: "ORDER-001", factStatus: "SUCCESS", amount: "88.00", occurredAt: "2026-09-28T10:02:00", createdAt: "2026-09-28T10:00:01" }]
    }, [["payment-core", "PAYMENT_SUCCESS", "PAY-001", "88.00", "ORDER-001"]]),
    /occurred before its record creation time/
  );
  assert.throws(
    () => assertReconciliationFactAmounts({ facts: [{ ...snapshot.facts[0], businessNo: "ORDER-002" }] },
      [["payment-core", "PAYMENT_SUCCESS", "PAY-001", "88.00", "ORDER-001"]]),
    /business number was/
  );
  assert.throws(
    () => assertReconciliationFactAmounts(snapshot,
      [["payment-core", "PAYMENT_SUCCESS", "PAY-001", "88.00", "ORDER-001", "2026-09-28T10:00:01"]]),
    /occurred before its source fact/
  );
  assert.throws(
    () => assertReconciliationFactAmounts(snapshot,
      [["payment-core", "PAYMENT_SUCCESS", "PAY-001", "88.00", "ORDER-001", "2026-02-30T10:00:00"]]),
    /occurred before its source fact/
  );
});

test("requires reconciliation clearing facts to preserve their payload identity", () => {
  const fact = {
    systemCode: "clearing-system",
    factType: "CLEARING_GENERATED",
    factNo: "CLR-001",
    payload: JSON.stringify({ clearingNo: "CLR-001" })
  };
  assert.equal(
    assertReconciliationFactPayload({ facts: [fact] }, "clearing-system", "CLEARING_GENERATED", "CLR-001", "clearingNo", "CLR-001").factNo,
    "CLR-001"
  );
  assert.throws(
    () => assertReconciliationFactPayload({ facts: [{ ...fact, payload: JSON.stringify({ clearingNo: "CLR-002" }) }] }, "clearing-system", "CLEARING_GENERATED", "CLR-001", "clearingNo", "CLR-001"),
    /payload clearingNo did not match expected value/
  );
  assert.throws(
    () => assertReconciliationFactPayload({ facts: [{ ...fact, payload: "not-json" }] }, "clearing-system", "CLEARING_GENERATED", "CLR-001", "clearingNo", "CLR-001"),
    /payload clearingNo did not match expected value/
  );
  const settlementFact = {
    systemCode: "settlement-system",
    factType: "SETTLEMENT_CREATED",
    factNo: "SET-001",
    payload: JSON.stringify({ settlementNo: "SET-001" })
  };
  assert.equal(
    assertReconciliationFactPayload({ facts: [settlementFact] }, "settlement-system", "SETTLEMENT_CREATED", "SET-001", "settlementNo", "SET-001").factNo,
    "SET-001"
  );
  assert.throws(
    () => assertReconciliationFactPayload({ facts: [{ ...settlementFact, payload: JSON.stringify({ settlementNo: "SET-002" }) }] }, "settlement-system", "SETTLEMENT_CREATED", "SET-001", "settlementNo", "SET-001"),
    /payload settlementNo did not match expected value/
  );
  const accountingFact = {
    systemCode: "accounting-system",
    factType: "ACCOUNTING_POSTED",
    factNo: "EVENT-001",
    payload: JSON.stringify({ eventNo: "EVENT-001", summary: "posted" })
  };
  assert.equal(
    assertReconciliationFactPayload({ facts: [accountingFact] }, "accounting-system", "ACCOUNTING_POSTED", "EVENT-001", "eventNo", "EVENT-001").factNo,
    "EVENT-001"
  );
  assert.throws(
    () => assertReconciliationFactPayload({ facts: [{ ...accountingFact, payload: JSON.stringify({ eventNo: "EVENT-002" }) }] }, "accounting-system", "ACCOUNTING_POSTED", "EVENT-001", "eventNo", "EVENT-001"),
    /payload eventNo did not match expected value/
  );
  const paymentFact = {
    systemCode: "payment-core",
    factType: "PAYMENT_SUCCESS",
    factNo: "PAY-001",
    payload: JSON.stringify({ paymentOrderId: "PAY-001" })
  };
  assert.equal(
    assertReconciliationFactPayload({ facts: [paymentFact] }, "payment-core", "PAYMENT_SUCCESS", "PAY-001", "paymentOrderId", "PAY-001").factNo,
    "PAY-001"
  );
  assert.throws(
    () => assertReconciliationFactPayload({ facts: [{ ...paymentFact, payload: JSON.stringify({ paymentOrderId: "PAY-002" }) }] }, "payment-core", "PAYMENT_SUCCESS", "PAY-001", "paymentOrderId", "PAY-001"),
    /payload paymentOrderId did not match expected value/
  );
  const refundFact = {
    systemCode: "refund-center",
    factType: "REFUND_SUCCESS",
    factNo: "REVT-001",
    payload: "退款成功事实，原支付单号=PAY-001"
  };
  assert.equal(
    assertReconciliationFactPayloadText({ facts: [refundFact] }, "refund-center", "REFUND_SUCCESS", "REVT-001", "PAY-001").factNo,
    "REVT-001"
  );
  assert.throws(
    () => assertReconciliationFactPayloadText({ facts: [{ ...refundFact, payload: "退款成功事实，原支付单号=PAY-002" }] }, "refund-center", "REFUND_SUCCESS", "REVT-001", "PAY-001"),
    /payload did not contain expected text/
  );
});

test("requires clearing and settlement amounts to remain aligned", () => {
  const settlement = assertAmountFlow(
    { orderAmount: "¥88.00", workerAmount: "¥71.16" },
    { shouldSettleAmount: "¥71.16", deductAmount: "¥0.00", netSettleAmount: "¥71.16" },
    "¥88.00"
  );
  assert.equal(settlement.netSettleAmount, "¥71.16");
  assert.throws(
    () => assertAmountFlow(
      { orderAmount: "¥88.00", workerAmount: "¥70.00" },
      { shouldSettleAmount: "¥71.16", deductAmount: "¥0.00", netSettleAmount: "¥71.16" },
      "¥88.00"
    ),
    /did not match settlement net amount/
  );
  assert.throws(
    () => assertAmountFlow(
      { orderAmount: "¥88.00", workerAmount: "¥90.00" },
      { shouldSettleAmount: "¥90.00", deductAmount: "¥0.00", netSettleAmount: "¥90.00" },
      "¥88.00"
    ),
    /did not match settlement net amount/
  );
  assert.throws(
    () => assertAmountFlow(
      { orderAmount: "invalid", workerAmount: "¥71.16" },
      { shouldSettleAmount: "¥71.16", deductAmount: "¥0.00", netSettleAmount: "¥71.16" },
      "¥88.00"
    ),
    /clearing order amount was/
  );
  assert.throws(
    () => assertAmountFlow(
      { orderAmount: "88oops", workerAmount: "¥71.16" },
      { shouldSettleAmount: "¥71.16", deductAmount: "¥0.00", netSettleAmount: "¥71.16" },
      "¥88.00"
    ),
    /clearing order amount was/
  );
  assert.throws(
    () => assertAmountFlow(
      { orderAmount: "¥88.00", workerAmount: "¥71.16" },
      { shouldSettleAmount: "¥80.00", deductAmount: "¥8.00", netSettleAmount: "¥71.16" },
      "¥88.00"
    ),
    /settlement amount breakdown/
  );
  assert.throws(
    () => assertAmountFlow(
      { orderAmount: "¥88.00", workerAmount: "¥71.16" },
      { shouldSettleAmount: "¥71.16", netSettleAmount: "¥71.16" },
      "¥88.00"
    ),
    /settlement amount breakdown/
  );
});

test("includes the failed endpoint and recovery hint for unreachable services", () => {
  assert.equal(
    formatRequestError("http://localhost:18081/api/payments/prepay", new TypeError("fetch failed")),
    "http://localhost:18081/api/payments/prepay request failed: fetch failed; check that the target service is running and reachable"
  );
});

test("preserves non-Error request failure details", () => {
  assert.equal(
    formatRequestError("http://localhost:18120/api/clearing/orders", "connection refused"),
    "http://localhost:18120/api/clearing/orders request failed: connection refused; check that the target service is running and reachable"
  );
});

test("wraps non-JSON service responses with the failed endpoint", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    status: 200,
    json: async () => {
      throw new SyntaxError("Unexpected token < in JSON");
    }
  });
  try {
    await assert.rejects(
      api("http://localhost:18140/api/refunds/apply"),
      (error) => error.message === "http://localhost:18140/api/refunds/apply returned invalid JSON: Unexpected token < in JSON; check the target service response"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("does not follow redirects for local payment writes", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (_url, options) => {
    assert.equal(options.method, "POST");
    assert.equal(options.redirect, "error");
    return { ok: true, status: 200, json: async () => ({ code: "0", data: "local" }) };
  };
  try {
    assert.equal(await api("http://localhost:18081/api/payments/prepay",
      { method: "POST", redirect: "follow" }), "local");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("rejects malformed JSON envelopes with the failed endpoint", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    status: 200,
    json: async () => null
  });
  try {
    await assert.rejects(
      api("http://localhost:18140/api/refunds/apply"),
      (error) => error.message
        === "http://localhost:18140/api/refunds/apply returned an invalid JSON envelope: expected an object; check the target service response"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("aborts unreachable happy-path requests and clears the timeout", async () => {
  const originalFetch = globalThis.fetch;
  const originalSetTimeout = globalThis.setTimeout;
  const originalClearTimeout = globalThis.clearTimeout;
  let clearCount = 0;
  globalThis.setTimeout = (callback) => {
    callback();
    return "happy-path-timeout";
  };
  globalThis.clearTimeout = (timeoutId) => {
    assert.equal(timeoutId, "happy-path-timeout");
    clearCount += 1;
  };
  globalThis.fetch = async (_url, options) => {
    assert.equal(options.signal.aborted, true);
    const error = new Error("request aborted");
    error.name = "AbortError";
    throw error;
  };
  try {
    await assert.rejects(
      api("http://localhost:18081/api/payments/prepay"),
      (error) => error.message === "http://localhost:18081/api/payments/prepay request failed: request timed out after 10000ms; check that the target service is running and reachable"
    );
    assert.equal(clearCount, 1);
  } finally {
    globalThis.fetch = originalFetch;
    globalThis.setTimeout = originalSetTimeout;
    globalThis.clearTimeout = originalClearTimeout;
  }
});
