import assert from "node:assert/strict";
import test from "node:test";
import {
  canClosePaymentLocally,
  canQueryPaymentChannel,
  isPaymentSubmissionPending,
  PAYMENT_RESULT_STATE_META,
  resolvePaymentMerchantNo,
  resolvePaymentDetailRefreshDelay,
  shouldAutoRefreshPaymentDetail
} from "./payment.js";

test("uses the merchant configured for each payment channel", () => {
  assert.equal(resolvePaymentMerchantNo("微信支付"), "MCH_WX_10001");
  assert.equal(resolvePaymentMerchantNo("支付宝"), "MCH_ALI_20001");
  assert.equal(resolvePaymentMerchantNo("银行卡"), "BANK_OFFLINE_30001");
});

test("only queries the channel for terminal payments or submitted pending payments", () => {
  assert.equal(canQueryPaymentChannel("SUCCESS", null), true);
  assert.equal(canQueryPaymentChannel("CLOSED", null), true);
  assert.equal(canQueryPaymentChannel("WAIT_CALLBACK", "处理中"), true);
  assert.equal(canQueryPaymentChannel("WAIT_CALLBACK", null), false);
  assert.equal(canQueryPaymentChannel("WAIT_CALLBACK", "-"), false);
  assert.equal(canQueryPaymentChannel("WAIT_CALLBACK", " - "), false);
  assert.equal(canQueryPaymentChannel("PREPAY_CREATED", "处理中"), false);
  assert.equal(canQueryPaymentChannel("RISK_REJECTED", null), false);
});

test("locks submission while cashier status is not final", () => {
  assert.equal(isPaymentSubmissionPending("支付中", "-"), true);
  assert.equal(isPaymentSubmissionPending("待回调", "-"), true);
  assert.equal(isPaymentSubmissionPending("结果待确认", "-"), true);
});

test("locks submission while latest attempt result is unknown", () => {
  assert.equal(isPaymentSubmissionPending("待支付", "渠道提交中"), true);
  assert.equal(isPaymentSubmissionPending("待支付", "结果未知，待查单"), true);
});

test("allows a fresh unpaid submission", () => {
  assert.equal(isPaymentSubmissionPending("待支付", "-"), false);
});

test("only allows local close before channel submission", () => {
  assert.equal(canClosePaymentLocally("PREPAY_CREATED", null, null), true);
  assert.equal(canClosePaymentLocally("待风控复核", "", "-"), true);
  assert.equal(canClosePaymentLocally("WAIT_CALLBACK", "", "待回调"), false);
  assert.equal(canClosePaymentLocally("PREPAY_CREATED", "WX-001", null), false);
  assert.equal(canClosePaymentLocally("PREPAY_CREATED", "", "渠道提交中"), false);
});

test("pending result directs users to trusted payment facts", () => {
  assert.match(PAYMENT_RESULT_STATE_META.pending.hint, /主动查单/);
  assert.doesNotMatch(PAYMENT_RESULT_STATE_META.pending.hint, /模拟回调/);
});

test("only auto refreshes payment states that can advance asynchronously", () => {
  assert.equal(shouldAutoRefreshPaymentDetail("WAIT_CALLBACK"), true);
  assert.equal(shouldAutoRefreshPaymentDetail(" paying "), true);
  assert.equal(shouldAutoRefreshPaymentDetail("risk_review"), true);
  assert.equal(shouldAutoRefreshPaymentDetail("PREPAY_CREATED"), false);
  assert.equal(shouldAutoRefreshPaymentDetail("RISK_BLOCKED"), false);
  assert.equal(shouldAutoRefreshPaymentDetail("RISK_REJECTED"), false);
  assert.equal(shouldAutoRefreshPaymentDetail("SUCCESS"), false);
  assert.equal(shouldAutoRefreshPaymentDetail("CLOSED"), false);
  assert.equal(shouldAutoRefreshPaymentDetail([]), false);
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
