#!/usr/bin/env node

const paymentBaseUrl = process.env.HSP_PAYMENT_CORE_BASE_URL || "http://127.0.0.1:18081";
const clearingBaseUrl = process.env.HSP_CLEARING_BASE_URL || "http://127.0.0.1:18120";
const settlementBaseUrl = process.env.HSP_SETTLEMENT_BASE_URL || "http://127.0.0.1:18130";
const accountingBaseUrl = process.env.HSP_ACCOUNTING_BASE_URL || "http://127.0.0.1:18110";
const refundBaseUrl = process.env.HSP_REFUND_CENTER_BASE_URL || "http://127.0.0.1:18140";
const reconciliationBaseUrl = process.env.HSP_RECONCILIATION_BASE_URL || "http://127.0.0.1:18150";

const suffix = `${Date.now()}-${process.pid}`;
const orderNo = `LOCAL-HAPPY-${suffix}`;

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

async function api(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {})
    }
  });
  const payload = await response.json();
  assert(response.ok, `${url} returned HTTP ${response.status}`);
  assert(payload.code === "0", `${url} returned ${payload.code}: ${payload.message}`);
  return payload.data;
}

async function waitFor(description, action, predicate, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;
  let latest;
  while (Date.now() < deadline) {
    latest = await action();
    if (predicate(latest)) {
      return latest;
    }
    await new Promise((resolve) => setTimeout(resolve, 300));
  }
  throw new Error(`${description} not ready within ${timeoutMs}ms: ${JSON.stringify(latest)}`);
}

async function main() {
  const prepay = await api(`${paymentBaseUrl}/api/payments/prepay`, {
    method: "POST",
    body: JSON.stringify({
      orderNo,
      payScene: "HOME_CLEAN",
      customerName: "local happy path",
      amount: 88,
      cashierTitle: "home service payment"
    })
  });

  await api(`${paymentBaseUrl}/api/payments/submit`, {
    method: "POST",
    body: JSON.stringify({
      prepayOrderNo: prepay.prepayOrderNo,
      paymentMethod: "微信支付",
      channelCode: "wx_h5",
      sourceAppId: "housekeeping-h5-web",
      merchantNo: "MCH_WX_10001",
      accessToken: "token-housekeeping-h5-web",
      terminal: "H5",
      clientDeviceId: "local-happy-path",
      payerPhone: "13800000000",
      idempotencyKey: orderNo
    })
  });

  const submitted = await api(`${paymentBaseUrl}/api/payments/${prepay.paymentOrderId}`);
  assert(submitted.status === "WAIT_CALLBACK", `unexpected submitted status: ${submitted.status}`);
  assert(submitted.channelTransactionNo, "channel transaction number was not created");

  const paid = await api(`${paymentBaseUrl}/api/payments/callback/wx_h5`, {
    method: "POST",
    body: JSON.stringify({
      paymentOrderId: prepay.paymentOrderId,
      channelTransactionNo: submitted.channelTransactionNo,
      tradeStatus: "SUCCESS"
    })
  });
  assert(paid.status === "SUCCESS", `unexpected callback status: ${paid.status}`);

  const paymentEvents = await waitFor(
    "payment event dispatch",
    () => api(`${paymentBaseUrl}/api/payment-events?paymentOrderId=${encodeURIComponent(prepay.paymentOrderId)}&pageNo=1&pageSize=20`),
    (page) => page.items.some((item) => item.eventType === "PAYMENT_SUCCESS"
      && item.publishStatus === "SUCCESS"
      && item.paymentSuccessHttpStage === "COMPLETED")
  );
  const paymentEvent = paymentEvents.items.find((item) => item.eventType === "PAYMENT_SUCCESS");

  const clearingOrders = await waitFor(
    "clearing order",
    () => api(`${clearingBaseUrl}/api/clearing/orders?paymentOrderId=${encodeURIComponent(prepay.paymentOrderId)}&pageNo=1&pageSize=20`),
    (page) => page.items.length === 1 && page.items[0].clearingStatus === "清分成功"
  );
  const clearingOrder = clearingOrders.items[0];

  const settlementOrders = await waitFor(
    "settlement order",
    () => api(`${settlementBaseUrl}/api/settlements/orders?clearingNo=${encodeURIComponent(clearingOrder.clearingNo)}&pageNo=1&pageSize=20`),
    (page) => page.items.length === 1
  );
  const settlementOrder = settlementOrders.items[0];

  const accountingPaymentEvents = await api(
    `${accountingBaseUrl}/api/accounting/events?bizNo=${encodeURIComponent(prepay.paymentOrderId)}&pageNo=1&pageSize=20`
  );
  const accountingClearingEvents = await api(
    `${accountingBaseUrl}/api/accounting/events?bizNo=${encodeURIComponent(clearingOrder.clearingNo)}&pageNo=1&pageSize=20`
  );
  assert(accountingPaymentEvents.items.some((item) => item.eventType === "PAYMENT_SUCCESS"),
    "accounting PAYMENT_SUCCESS event was not consumed");
  assert(accountingClearingEvents.items.some((item) => item.eventType === "CLEARING_GENERATED"),
    "accounting CLEARING_GENERATED event was not consumed");
  const accountingPaymentEvent = accountingPaymentEvents.items.find((item) => item.eventType === "PAYMENT_SUCCESS");

  await api(`${refundBaseUrl}/api/refunds/internal/payment-success`, {
    method: "POST",
    body: JSON.stringify({
      paymentOrderId: prepay.paymentOrderId,
      orderNo,
      customerName: "local happy path",
      paidAmount: 88,
      channelCode: "wx_h5"
    })
  });
  const refund = await api(`${refundBaseUrl}/api/refunds/apply`, {
    method: "POST",
    body: JSON.stringify({
      paymentOrderId: prepay.paymentOrderId,
      refundAmount: 18,
      refundMethod: "ORIGINAL",
      refundReason: "local happy path refund",
      idempotencyKey: `REFUND-${orderNo}`
    })
  });
  await api(`${refundBaseUrl}/api/refunds/${encodeURIComponent(refund.refundOrderId)}/approve`, {
    method: "POST",
    body: JSON.stringify({ remark: "local happy path approved" })
  });
  await api(`${refundBaseUrl}/api/refunds/${encodeURIComponent(refund.refundOrderId)}/submit`, {
    method: "POST",
    body: JSON.stringify({ remark: "local happy path submitted" })
  });
  const refunded = await api(`${refundBaseUrl}/api/refunds/${encodeURIComponent(refund.refundOrderId)}/callback`, {
    method: "POST",
    body: JSON.stringify({
      result: "SUCCESS",
      channelRefundId: `LOCAL-CHANNEL-REFUND-${suffix}`,
      rawMessage: "local happy path success"
    })
  });
  assert(refunded.status === "SUCCESS", `unexpected refund status: ${refunded.status}`);

  const refundOutboxPage = await api(
    `${refundBaseUrl}/api/refunds/outbox?aggregateId=${encodeURIComponent(refund.refundOrderId)}&pageNo=1&pageSize=20`
  );
  assert(refundOutboxPage.items.length === 1, "REFUND_SUCCESS outbox event was not created");
  const refundOutbox = await api(
    `${refundBaseUrl}/api/refunds/outbox/${encodeURIComponent(refundOutboxPage.items[0].eventId)}/dispatch`,
    { method: "POST", body: JSON.stringify({ remark: "local happy path dispatch" }) }
  );
  assert(refundOutbox.status === "SENT", `unexpected refund outbox status: ${refundOutbox.status}`);
  const repeatedRefundOutbox = await api(
    `${refundBaseUrl}/api/refunds/outbox/${encodeURIComponent(refundOutbox.eventId)}/dispatch`,
    { method: "POST", body: JSON.stringify({ remark: "local happy path repeated dispatch" }) }
  );
  assert(repeatedRefundOutbox.status === "SENT", "repeated refund dispatch was not idempotent");

  const accountingRefundEvents = await api(
    `${accountingBaseUrl}/api/accounting/events?bizNo=${encodeURIComponent(refund.refundOrderId)}&pageNo=1&pageSize=20`
  );
  assert(accountingRefundEvents.items.filter((item) => item.eventType === "REFUND_SUCCESS").length === 1,
    "accounting REFUND_SUCCESS event was not consumed idempotently");
  const accountingRefundLedgers = await api(
    `${accountingBaseUrl}/api/accounting/ledgers?bizNo=${encodeURIComponent(refund.refundOrderId)}&bizType=REFUND_SUCCESS_EVENT&pageNo=1&pageSize=20`
  );
  assert(accountingRefundLedgers.items.length === 1, "refund success did not create exactly one accounting ledger");
  assert(accountingRefundLedgers.items[0].direction === "借方", "refund success ledger was not a debit entry");
  const reconciliationChain = await api(
    `${reconciliationBaseUrl}/api/reconciliation/cross-system/chains/${encodeURIComponent(`CHAIN-${prepay.paymentOrderId}`)}`
  );
  const requiredReconciliationFacts = [
    ["payment-core", "PAYMENT_SUCCESS", prepay.paymentOrderId],
    ["clearing-system", "CLEARING_GENERATED", clearingOrder.clearingNo],
    ["settlement-system", "SETTLEMENT_CREATED", settlementOrder.settlementNo],
    ["accounting-system", "ACCOUNTING_POSTED", accountingPaymentEvent.eventNo],
    ["refund-center", "REFUND_SUCCESS", refundOutbox.eventId]
  ];
  for (const [systemCode, factType, factNo] of requiredReconciliationFacts) {
    assert(reconciliationChain.facts.some((item) => item.systemCode === systemCode
      && item.factType === factType
      && item.factNo === factNo
      && item.factStatus === "SUCCESS"),
    `reconciliation ${systemCode}/${factType}/${factNo} fact was not recorded as SUCCESS`);
  }
  assert(reconciliationChain.facts.filter((item) => item.systemCode === "refund-center"
    && item.factType === "REFUND_SUCCESS"
    && item.factNo === refundOutbox.eventId).length === 1,
  "reconciliation REFUND_SUCCESS fact was not recorded idempotently");
  assert(reconciliationChain.expectedStageCount === 4,
    `reconciliation expected stage count was ${reconciliationChain.expectedStageCount}, expected 4`);
  assert(reconciliationChain.completedStageCount === reconciliationChain.expectedStageCount,
    `reconciliation completed stage count was ${reconciliationChain.completedStageCount}`);
  assert(reconciliationChain.chainStatus === "COMPLETED",
    `reconciliation chain was not COMPLETED: ${reconciliationChain.chainStatus}`);
  assert(reconciliationChain.responsibilityStatus === "CLOSED",
    `reconciliation responsibility was not CLOSED: ${reconciliationChain.responsibilityStatus}`);
  assert(reconciliationChain.financialCloseable === true,
    "reconciliation chain was not financial-closeable");

  console.log("[PASS] cross-system local happy path");
  console.log(JSON.stringify({
    orderNo,
    paymentOrderId: prepay.paymentOrderId,
    paymentEventNo: paymentEvent.eventNo,
    clearingNo: clearingOrder.clearingNo,
    settlementNo: settlementOrder.settlementNo,
    refundOrderId: refund.refundOrderId,
    refundEventId: refundOutbox.eventId,
    accountingEventTypes: ["PAYMENT_SUCCESS", "CLEARING_GENERATED", "REFUND_SUCCESS"],
    reconciliationFactTypes: requiredReconciliationFacts.map(([, factType]) => factType),
    reconciliationChainStatus: reconciliationChain.chainStatus,
    reconciliationCompletedStageCount: reconciliationChain.completedStageCount,
    reconciliationExpectedStageCount: reconciliationChain.expectedStageCount,
    reconciliationFinancialCloseable: reconciliationChain.financialCloseable
  }, null, 2));
}

main().catch((error) => {
  console.error(`[FAIL] ${error.message}`);
  process.exitCode = 1;
});
