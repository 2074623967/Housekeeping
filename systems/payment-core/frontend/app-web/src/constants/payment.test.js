import assert from "node:assert/strict";
import test from "node:test";
import { isPaymentSubmissionPending } from "./payment.js";

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
