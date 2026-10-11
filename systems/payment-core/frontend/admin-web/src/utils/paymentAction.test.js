import assert from "node:assert/strict";
import test from "node:test";
import { canClosePaymentLocally, canQueryPaymentChannel, isChannelSubmissionUnaccepted, paymentDetailOperationSuggestions, paymentRecordQuerySuggestion } from "./paymentAction.js";

test("distinguishes persisted callback waiting from unaccepted channel submission", () => {
  assert.equal(isChannelSubmissionUnaccepted("WAIT_CALLBACK", "渠道提交中", null), true);
  assert.equal(isChannelSubmissionUnaccepted("支付中", " 渠道提交中 ", " "), true);
  assert.equal(isChannelSubmissionUnaccepted("WAIT_CALLBACK", "渠道提交中", "WX-1"), false);
  assert.equal(isChannelSubmissionUnaccepted("WAIT_CALLBACK", "等待回调", null), false);
  assert.equal(isChannelSubmissionUnaccepted("CLOSED", "渠道提交中", null), false);
});

test("only queries readable payments or open submitted attempts", () => {
  assert.equal(canQueryPaymentChannel("SUCCESS", null), true);
  assert.equal(canQueryPaymentChannel("支付成功", "成功", "2026-10-07 09:00:00", "WX-1"), true);
  assert.equal(canQueryPaymentChannel("CLOSED", null), true);
  assert.equal(canQueryPaymentChannel("已关闭", null), true);
  assert.equal(canQueryPaymentChannel("已关闭", null, null, "WX-1"), false);
  assert.equal(canQueryPaymentChannel("CLOSED", "处理中", null, null, "wx_h5"), true);
  assert.equal(canQueryPaymentChannel("CLOSED", "FAILED"), false);
  assert.equal(canQueryPaymentChannel("WAIT_CALLBACK", "处理中", null, "WX-1", "wx_h5"), true);
  assert.equal(canQueryPaymentChannel("WAIT_CALLBACK", "渠道提交中", null, null, "wx_h5"), false);
  assert.equal(canQueryPaymentChannel("支付中", "渠道提交中", null, "", "wx_h5"), false);
  assert.equal(canQueryPaymentChannel("WAIT_CALLBACK", "渠道提交中", null, "WX-1", "wx_h5"), true);
  assert.equal(canQueryPaymentChannel("WAIT_CALLBACK", "处理中", null, "WX-1", null), false);
  assert.equal(canQueryPaymentChannel("CLOSED", "处理中", null, "WX-1", " "), false);
  assert.equal(canQueryPaymentChannel("支付中", "处理中", null, "WX-1", "wx_h5"), true);
  assert.equal(canQueryPaymentChannel("PAYING", "处理中"), false);
  for (const attemptStatus of ["成功", "支付成功", "失败", "支付失败", "SUCCESS", "FAIL", "FAILED", " FAILED "]) {
    assert.equal(canQueryPaymentChannel("WAIT_CALLBACK", attemptStatus, null, "WX-1", "wx_h5"), false);
  }
  assert.equal(canQueryPaymentChannel("支付中", "处理中", "2026-10-07 09:00:00", "WX-1", "wx_h5"), false);
  assert.equal(canQueryPaymentChannel("PAYING", null), false);
  assert.equal(canQueryPaymentChannel("WAIT_CALLBACK", null), false);
  assert.equal(canQueryPaymentChannel("WAIT_CALLBACK", "-"), false);
  assert.equal(canQueryPaymentChannel("WAIT_CALLBACK", " - "), false);
  assert.equal(canQueryPaymentChannel("PREPAY_CREATED", "处理中"), false);
  assert.equal(canQueryPaymentChannel("RISK_REJECTED", null), false);
});

test("only closes unpaid local payment states without a channel attempt", () => {
  assert.equal(canClosePaymentLocally("PREPAY_CREATED", null, null), true);
  assert.equal(canClosePaymentLocally("RISK_REVIEW", "", "-"), true);
  assert.equal(canClosePaymentLocally("RISK_BLOCKED", " ", " "), true);
  assert.equal(canClosePaymentLocally("RISK_REJECTED", null, null), true);

  assert.equal(canClosePaymentLocally("WAIT_CALLBACK", null, null), false);
  assert.equal(canClosePaymentLocally("SUCCESS", null, null), false);
  assert.equal(canClosePaymentLocally("PREPAY_CREATED", "CHANNEL-1", null), false);
  assert.equal(canClosePaymentLocally("RISK_REVIEW", null, "处理中"), false);
});

test("payment detail guidance follows channel acceptance and actual query eligibility", () => {
  const waiting = {
    status: "WAIT_CALLBACK", latestAttemptStatus: "渠道提交中",
    latestAttemptCompletedAt: null, channelTransactionNo: null, channel: "wx_h5", notifyLogs: []
  };
  assert.match(paymentDetailOperationSuggestions(waiting)[0], /渠道受理未确认.*勿主动查单或重复提交/);
  assert.equal(paymentDetailOperationSuggestions(waiting).length, 1);
  assert.match(paymentDetailOperationSuggestions({ ...waiting, channelTransactionNo: "WX-ACCEPTED" })[0], /可主动查单/);
  assert.match(paymentDetailOperationSuggestions({ ...waiting, latestAttemptStatus: "处理中", channel: null })[0], /缺少可查单的渠道提交事实/);
  assert.deepEqual(paymentDetailOperationSuggestions(null), []);
});

test("payment record guidance follows channel acceptance and query eligibility", () => {
  const waiting = {
    paymentStatus: "支付中", latestAttemptStatus: "渠道提交中",
    latestAttemptCompletedAt: null, externalTransactionNo: null, paymentChannel: "wx_h5"
  };
  assert.match(paymentRecordQuerySuggestion(waiting), /渠道受理未确认.*勿主动查单或重复提交/);
  assert.match(paymentRecordQuerySuggestion({ ...waiting, externalTransactionNo: "WX-ACCEPTED" }), /建议先主动查单/);
  assert.match(paymentRecordQuerySuggestion({ ...waiting, latestAttemptStatus: "处理中", paymentChannel: null }), /缺少可查单的渠道提交事实/);
  assert.equal(paymentRecordQuerySuggestion({ ...waiting, prepayBindingIssue: "PREPAY_BINDING_MISSING", externalTransactionNo: "WX-ACCEPTED" }), null);
  assert.equal(paymentRecordQuerySuggestion({ ...waiting, paymentStatus: "已关闭", latestAttemptStatus: null }), null);
  assert.equal(paymentRecordQuerySuggestion(null), null);
});
