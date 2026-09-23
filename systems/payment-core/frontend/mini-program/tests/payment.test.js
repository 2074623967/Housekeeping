const assert = require("node:assert/strict");
const test = require("node:test");
const {
  canClosePayment,
  canQueryPayment,
  canSubmitPayment,
  createIdempotencyKey,
  createOrderNo,
  normalizeAmount,
  normalizeNativePaymentParams,
  requestNativePayment,
  resolvePaymentDetailRefreshDelay,
  resolveRemainingSeconds,
  resolveScene,
  resultMeta,
  shouldAutoRefreshPaymentDetail
} = require("../utils/payment");

test("normalizes valid mini program payment amounts", () => {
  assert.equal(normalizeAmount("68"), "68.00");
  assert.equal(normalizeAmount("68.5"), "68.50");
  assert.equal(normalizeAmount("0"), "");
  assert.equal(normalizeAmount("1.234"), "");
  assert.equal(normalizeAmount("10000000000000000"), "");
});

test("resolves supported business scenes and stable identifiers", () => {
  assert.equal(resolveScene("recharge").payScene, "WALLET_RECHARGE_MINI_PROGRAM");
  assert.equal(resolveScene("unknown").key, "balance-pay");
  assert.equal(createOrderNo("transfer", 123), "ACCOUNT-TRANSFER-MINI-123");
  assert.equal(createIdempotencyKey("PRE-1", 123, "abc"), "MINI-PRE-1-123-abc");
});

test("keeps query and close operations on proven channel facts", () => {
  assert.equal(canQueryPayment("WAIT_CALLBACK", "等待回调"), true);
  assert.equal(canQueryPayment("WAIT_CALLBACK", "-"), false);
  assert.equal(canQueryPayment("PREPAY_CREATED", null), false);
  assert.equal(canClosePayment("PREPAY_CREATED", null, null), true);
  assert.equal(canClosePayment("RISK_REVIEW", "", "-"), true);
  assert.equal(canClosePayment("WAIT_CALLBACK", null, null), false);
  assert.equal(canClosePayment("PREPAY_CREATED", "WX-1", null), false);
});

test("submits only an unexpired untouched prepay with an allowed channel", () => {
  const now = new Date("2026-09-14T10:00:00").getTime();
  const cashier = {
    channels: ["微信支付"],
    status: "待支付",
    paymentStatus: "PREPAY_CREATED",
    latestAttemptStatus: "-",
    expiresAt: "2026-09-14 10:05:00"
  };
  assert.equal(resolveRemainingSeconds(cashier.expiresAt, now), 300);
  assert.equal(canSubmitPayment(cashier, now), true);
  assert.equal(canSubmitPayment({ ...cashier, status: "待风控复核" }, now), false);
  assert.equal(canSubmitPayment({ ...cashier, paymentStatus: "WAIT_CALLBACK" }, now), false);
  assert.equal(canSubmitPayment({ ...cashier, latestAttemptStatus: "等待回调" }, now), false);
  assert.equal(canSubmitPayment({ ...cashier, channels: ["支付宝"] }, now), false);
  assert.equal(canSubmitPayment({ ...cashier, expiresAt: "2026-09-14 09:59:59" }, now), false);
});

test("normalizes complete native payment parameters and ignores incomplete payloads", () => {
  assert.deepEqual(normalizeNativePaymentParams({
    nativePaymentParams: {
      timeStamp: " 1710000000 ",
      nonceStr: "nonce",
      package: "prepay_id=wx-prepay",
      signType: "RSA",
      paySign: "signature"
    }
  }), {
    timeStamp: "1710000000",
    nonceStr: "nonce",
    package: "prepay_id=wx-prepay",
    signType: "RSA",
    paySign: "signature"
  });
  assert.equal(normalizeNativePaymentParams({ paymentParams: { timeStamp: "1" } }), null);
});

test("keeps the local demo path when native payment parameters are absent", async () => {
  assert.deepEqual(await requestNativePayment({ paymentOrderId: "PAY-DEMO" }), { invoked: false });
});

test("maps payment terminal states without inventing success", () => {
  assert.equal(resultMeta("SUCCESS").tone, "success");
  assert.equal(resultMeta("CLOSED").tone, "danger");
  assert.equal(resultMeta("WAIT_CALLBACK").title, "支付处理中");
  assert.equal(resultMeta("UNKNOWN").title, "支付处理中");
});

test("auto refreshes only asynchronously advancing payment states", () => {
  assert.equal(shouldAutoRefreshPaymentDetail("WAIT_CALLBACK"), true);
  assert.equal(shouldAutoRefreshPaymentDetail(" paying "), true);
  assert.equal(shouldAutoRefreshPaymentDetail("risk_review"), true);
  assert.equal(shouldAutoRefreshPaymentDetail("PREPAY_CREATED"), false);
  assert.equal(shouldAutoRefreshPaymentDetail("RISK_BLOCKED"), false);
  assert.equal(shouldAutoRefreshPaymentDetail("RISK_REJECTED"), false);
  assert.equal(shouldAutoRefreshPaymentDetail("SUCCESS"), false);
  assert.equal(shouldAutoRefreshPaymentDetail("CLOSED"), false);
  assert.equal(shouldAutoRefreshPaymentDetail(null), false);
});

test("limits automatic payment detail refresh to five minutes", () => {
  assert.equal(resolvePaymentDetailRefreshDelay("WAIT_CALLBACK", 0), 5000);
  assert.equal(resolvePaymentDetailRefreshDelay("WAIT_CALLBACK", 59), 5000);
  assert.equal(resolvePaymentDetailRefreshDelay("WAIT_CALLBACK", 60), null);
  assert.equal(resolvePaymentDetailRefreshDelay("SUCCESS", 0), null);
  assert.equal(resolvePaymentDetailRefreshDelay("WAIT_CALLBACK", -1), null);
  assert.equal(resolvePaymentDetailRefreshDelay("WAIT_CALLBACK", 1.5), null);
});
