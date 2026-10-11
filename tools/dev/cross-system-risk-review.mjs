#!/usr/bin/env node

import { pathToFileURL } from "node:url";
import { api, assertLocalDemoBaseUrl, assertPaymentSubmission } from "./cross-system-happy-path.mjs";

const paymentBaseUrl = "http://localhost:18081";
const riskBaseUrl = "http://localhost:18180";

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

export function assertPendingReview(prepay, submitted, reviewPage) {
  assert(typeof prepay?.prepayOrderNo === "string" && prepay.prepayOrderNo.trim()
    && typeof prepay?.paymentOrderId === "string" && prepay.paymentOrderId.trim(),
  "prepay lacked payment identity");
  assert(submitted?.prepayOrderNo === prepay.prepayOrderNo
    && submitted?.paymentOrderId === prepay.paymentOrderId
    && submitted?.paymentStatus === "RISK_REVIEW"
    && submitted?.cashierStatus === "待风控复核", "risk submit was not held for review");
  const matching = reviewPage?.records?.filter((row) => row?.businessNo === prepay.paymentOrderId
    && row?.status === "PENDING");
  assert(matching?.length === 1 && typeof matching[0].reviewNo === "string"
    && matching[0].reviewNo.trim(), "risk review order was not uniquely pending");
  return matching[0].reviewNo;
}

export function assertChangedContextDenied(envelope, detail, paymentOrderId) {
  assert(envelope?.code === "PAYMENT-1024" && envelope.data == null,
    "changed review context did not fail closed");
  assert(detail?.paymentOrderId === paymentOrderId
    && detail?.status === "RISK_REVIEW"
    && detail?.paymentMethod === "微信支付"
    && detail?.channel === "wx_h5"
    && detail?.channelTransactionNo == null
    && detail?.latestAttemptStatus == null,
  "changed review context altered payment or attempt facts");
}

export function assertReviewApproved(page, reviewNo) {
  assert(page?.records?.some((row) => row?.reviewNo === reviewNo && row?.status === "APPROVED"),
    "risk review approval did not persist");
}

export function assertReviewedCashier(cashier, prepay, paymentMethod, channelCode) {
  assert(cashier?.prepayOrderNo === prepay.prepayOrderNo
    && cashier?.paymentOrderId === prepay.paymentOrderId
    && cashier?.paymentStatus === "RISK_REVIEW"
    && cashier?.reviewPaymentMethod === paymentMethod
    && cashier?.reviewChannelCode === channelCode
    && cashier?.channelTransactionNo == null
    && cashier?.latestAttemptStatus == null,
  "reviewed cashier did not retain the original request method and channel");
}

export function assertOriginalRequestContinued(prepay, submitted) {
  assert(submitted?.prepayOrderNo === prepay.prepayOrderNo
    && submitted?.paymentOrderId === prepay.paymentOrderId
    && submitted?.paymentStatus === "WAIT_CALLBACK", "approved original request did not continue");
}

export function assertHardBlockedBeforeReview(prepay, submitted, detail, reviewPage, eventPage) {
  assert(submitted?.prepayOrderNo === prepay.prepayOrderNo
    && submitted?.paymentOrderId === prepay.paymentOrderId
    && submitted?.paymentStatus === "RISK_BLOCKED"
    && submitted?.cashierStatus === "风控拦截", "hard risk submit was not blocked");
  assert(detail?.paymentOrderId === prepay.paymentOrderId
    && detail?.status === "RISK_BLOCKED"
    && detail?.channelTransactionNo == null
    && detail?.latestAttemptStatus == null, "hard risk submit created payment attempt facts");
  assert(Array.isArray(reviewPage?.records)
    && !reviewPage.records.some((row) => row?.businessNo === prepay.paymentOrderId),
  "hard risk submit created a review order");
  const matchingEvents = eventPage?.records?.filter((row) => row?.paymentOrderId === prepay.paymentOrderId
    && row?.hitPolicy === "POLICY_DEVICE_RISK" && row?.decisionResult === "已拦截");
  assert(matchingEvents?.length === 1 && typeof matchingEvents[0].eventNo === "string"
    && matchingEvents[0].eventNo.trim(), "hard risk submit lacked its intercept event");
}

async function rejectedSubmit(payload) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 10000);
  try {
    const response = await fetch(`${paymentBaseUrl}/api/payments/submit`, {
      method: "POST",
      redirect: "error",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
      signal: controller.signal
    });
    assert(response.ok, `changed risk submit returned HTTP ${response.status}`);
    return await response.json();
  } finally {
    clearTimeout(timeoutId);
  }
}

async function main() {
  assert(process.env.HSP_LOCAL_RISK_REVIEW_SCOPED === "true"
    && process.env.HSP_PAYMENT_RISK_CONTROL_ENABLED === "true",
  "run only through local-stack verify risk-review");
  assertLocalDemoBaseUrl(paymentBaseUrl, "payment-core");
  assertLocalDemoBaseUrl(riskBaseUrl, "risk-control-system");
  assert(await api(`${paymentBaseUrl}/api/demo/identity`) === "payment-core-h2-demo",
    "payment-core did not confirm H2/demo identity");
  assert(await api(`${riskBaseUrl}/api/demo/identity`) === "risk-control-h2-local-review",
    "risk-control-system did not confirm local H2 identity");
  const riskSummary = await api(`${riskBaseUrl}/api/risk-control/summary`);
  assert(Array.isArray(riskSummary?.metrics), "risk-control summary was unavailable");

  const orderNo = `LOCAL-RISK-REVIEW-${Date.now()}-${process.pid}`;
  const prepay = await api(`${paymentBaseUrl}/api/payments/prepay`, {
    method: "POST",
    body: JSON.stringify({
      orderNo, payScene: "HOME_CLEAN", customerName: "local risk review",
      amount: 88, cashierTitle: "local risk review"
    })
  });
  assert(prepay?.orderNo === orderNo && prepay?.paymentStatus === "PREPAY_CREATED",
    "risk review prepay did not match its local order");
  const submitPayload = {
    prepayOrderNo: prepay.prepayOrderNo,
    paymentMethod: "微信支付",
    channelCode: "wx_h5",
    sourceAppId: "housekeeping-h5-web",
    merchantNo: "MCH_WX_10001",
    accessToken: "token-housekeeping-h5-web",
    terminal: "H5",
    clientDeviceId: "local-review-device",
    payerPhone: "13800008888"
  };
  const submitted = await api(`${paymentBaseUrl}/api/payments/submit`, {
    method: "POST", body: JSON.stringify(submitPayload)
  });
  const reviewNo = assertPendingReview(prepay, submitted,
    await api(`${riskBaseUrl}/api/risk-control/review-orders`));
  const cashierUrl = `${paymentBaseUrl}/api/payments/cashier/${encodeURIComponent(prepay.prepayOrderNo)}?terminal=H5`;
  assertReviewedCashier(await api(cashierUrl), prepay, submitPayload.paymentMethod, submitPayload.channelCode);

  const paymentDetailUrl = `${paymentBaseUrl}/api/payments/${encodeURIComponent(prepay.paymentOrderId)}`;
  const changedContexts = [
    ["payment method, channel and merchant", {
      ...submitPayload, paymentMethod: "支付宝", channelCode: "alipay_h5", merchantNo: "MCH_ALI_20001"
    }],
    ["source application", {
      ...submitPayload, sourceAppId: "housekeeping-app-web", accessToken: "token-housekeeping-app-web"
    }],
    ["terminal", { ...submitPayload, terminal: "APP" }],
    ["device", { ...submitPayload, clientDeviceId: "other-local-device" }],
    ["payer phone", { ...submitPayload, payerPhone: "13800007777" }]
  ];
  async function assertChangedContextsDenied() {
    for (const [contextName, payload] of changedContexts) {
      try {
        assertChangedContextDenied(await rejectedSubmit(payload),
          await api(paymentDetailUrl), prepay.paymentOrderId);
      } catch (error) {
        throw new Error(`${contextName}: ${error.message}`);
      }
    }
  }
  await assertChangedContextsDenied();
  assertReviewedCashier(await api(cashierUrl), prepay, submitPayload.paymentMethod, submitPayload.channelCode);

  assertReviewApproved(await api(`${riskBaseUrl}/api/risk-control/review-orders/action`, {
    method: "POST", body: JSON.stringify({ reviewNo, action: "APPROVE" })
  }), reviewNo);
  await assertChangedContextsDenied();

  const continued = await api(`${paymentBaseUrl}/api/payments/submit`, {
    method: "POST", body: JSON.stringify(submitPayload)
  });
  assertOriginalRequestContinued(prepay, continued);
  assertPaymentSubmission(continued, prepay.paymentOrderId, prepay.prepayOrderNo,
    orderNo, "88.00", prepay.expiresAt, `CHAIN-${prepay.paymentOrderId}`,
    submitPayload.paymentMethod, submitPayload.channelCode, "HOME_CLEAN");

  const hardOrderNo = `${orderNo}-HARD`;
  const hardPrepay = await api(`${paymentBaseUrl}/api/payments/prepay`, {
    method: "POST",
    body: JSON.stringify({
      orderNo: hardOrderNo, payScene: "HOME_CLEAN", customerName: "local hard risk",
      amount: 88, cashierTitle: "local hard risk"
    })
  });
  assert(hardPrepay?.orderNo === hardOrderNo && hardPrepay?.paymentStatus === "PREPAY_CREATED",
    "hard risk prepay did not match its local order");
  const hardSubmitted = await api(`${paymentBaseUrl}/api/payments/submit`, {
    method: "POST",
    body: JSON.stringify({
      ...submitPayload, prepayOrderNo: hardPrepay.prepayOrderNo, clientDeviceId: "RISK-DEVICE-LOCAL-001"
    })
  });
  assertHardBlockedBeforeReview(hardPrepay, hardSubmitted,
    await api(`${paymentBaseUrl}/api/payments/${encodeURIComponent(hardPrepay.paymentOrderId)}`),
    await api(`${riskBaseUrl}/api/risk-control/review-orders`),
    await api(`${riskBaseUrl}/api/risk-control/intercept-events`));
  console.log("[PASS] cross-system local risk review HTTP cycle");
  console.log(JSON.stringify({ orderNo, paymentOrderId: prepay.paymentOrderId, reviewNo,
    changedContextCode: "PAYMENT-1024", approvedRetryStatus: "WAIT_CALLBACK",
    hardPaymentOrderId: hardPrepay.paymentOrderId, hardStatus: "RISK_BLOCKED" }, null, 2));
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(`[FAIL] ${error.message}`);
    process.exitCode = 1;
  });
}
