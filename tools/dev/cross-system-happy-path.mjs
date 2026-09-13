#!/usr/bin/env node

const paymentBaseUrl = process.env.HSP_PAYMENT_CORE_BASE_URL || "http://127.0.0.1:18081";
const clearingBaseUrl = process.env.HSP_CLEARING_BASE_URL || "http://127.0.0.1:18120";
const settlementBaseUrl = process.env.HSP_SETTLEMENT_BASE_URL || "http://127.0.0.1:18130";
const accountingBaseUrl = process.env.HSP_ACCOUNTING_BASE_URL || "http://127.0.0.1:18110";

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

  console.log("[PASS] cross-system local happy path");
  console.log(JSON.stringify({
    orderNo,
    paymentOrderId: prepay.paymentOrderId,
    paymentEventNo: paymentEvent.eventNo,
    clearingNo: clearingOrder.clearingNo,
    settlementNo: settlementOrder.settlementNo,
    accountingEventTypes: ["PAYMENT_SUCCESS", "CLEARING_GENERATED"]
  }, null, 2));
}

main().catch((error) => {
  console.error(`[FAIL] ${error.message}`);
  process.exitCode = 1;
});
