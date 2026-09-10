import assert from "node:assert/strict";
import test from "node:test";
import {
  canClosePaymentLocally,
  isPaymentSubmissionPending,
  PAYMENT_RESULT_STATE_META
} from "./payment.js";

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
