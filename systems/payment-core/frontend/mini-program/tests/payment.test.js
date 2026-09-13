const assert = require("node:assert/strict");
const test = require("node:test");
const {
  canClosePayment,
  canQueryPayment,
  canSubmitPayment,
  createIdempotencyKey,
  createOrderNo,
  normalizeAmount,
  resolveRemainingSeconds,
  resolveScene,
  resultMeta
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

test("maps payment terminal states without inventing success", () => {
  assert.equal(resultMeta("SUCCESS").tone, "success");
  assert.equal(resultMeta("CLOSED").tone, "danger");
  assert.equal(resultMeta("WAIT_CALLBACK").title, "支付处理中");
  assert.equal(resultMeta("UNKNOWN").title, "支付处理中");
});
