import assert from "node:assert/strict";
import test from "node:test";
import {
  formatRefundProgressTime,
  REFUND_PROGRESS_MAX_POLL_ATTEMPTS,
  isUnknownRefundSubmission,
  matchesRefundProgressDetail,
  shouldRetryRefundProgress
} from "./refundProgress.js";

test("distinguishes an unknown channel submit from confirmed refund processing", () => {
  assert.equal(isUnknownRefundSubmission({ status: "PROCESSING", attempts: [{ attemptStatus: "UNKNOWN" }] }), true);
  assert.equal(isUnknownRefundSubmission({ status: "PROCESSING", attempts: [{ attemptStatus: "ACCEPTED" }] }), false);
  assert.equal(isUnknownRefundSubmission({ status: "SUCCESS", attempts: [{ attemptStatus: "UNKNOWN" }] }), false);
  assert.equal(isUnknownRefundSubmission(null), false);
});

test("formats refund lifecycle timestamps for the user progress view", () => {
  assert.equal(formatRefundProgressTime("2026-09-29T06:03:17.693"), "2026-09-29 06:03:17.693");
  assert.equal(formatRefundProgressTime("2026-09-29 06:03:17"), "2026-09-29 06:03:17");
  assert.equal(formatRefundProgressTime(null), "-");
});

test("keeps refund progress bound to the loaded payment and amount snapshot", () => {
  const snapshot = {
    refundOrderId: "REF-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    customerName: "张三",
    paymentMethod: "微信支付",
    payScene: "BALANCE_PAY_APP",
    refundAmount: "18.00",
    paidAmount: "88.00",
    appliedAt: "2026-09-29T06:00:00",
    channelSubmittedAt: "2026-09-29T06:01:00",
    successAt: "2026-09-29T06:02:00",
    terminalAt: "2026-09-29T06:03:00",
    refundMethod: "原路退款",
    refundReason: "客户申请",
    channelCode: "WX_H5",
    channelRefundNo: "CH-REF-001"
  };
  assert.equal(matchesRefundProgressDetail(snapshot, {
    ...snapshot,
    refundAmount: "¥18.00"
  }), true);
  assert.equal(matchesRefundProgressDetail({
    ...snapshot,
    refundOrderId: " REF-001 ",
    paymentOrderId: " PAY-001 ",
    businessChainId: " CHAIN-PAY-001 ",
    orderNo: " ORDER-001 "
  }, {
    ...snapshot,
    refundOrderId: " REF-001 ",
    paymentOrderId: " PAY-001 ",
    businessChainId: " CHAIN-PAY-001 ",
    orderNo: " ORDER-001 "
  }), true);
  assert.equal(matchesRefundProgressDetail(snapshot, { ...snapshot, paymentOrderId: "PAY-002" }), false);
  assert.equal(matchesRefundProgressDetail(snapshot, { ...snapshot, businessChainId: "CHAIN-OTHER" }), false);
  assert.equal(matchesRefundProgressDetail({ ...snapshot, businessChainId: "CHAIN-OTHER" }, { ...snapshot, businessChainId: "CHAIN-OTHER" }), false);
  assert.equal(matchesRefundProgressDetail(snapshot, { ...snapshot, orderNo: "ORDER-002" }), false);
  assert.equal(matchesRefundProgressDetail(snapshot, { ...snapshot, refundAmount: "17.99" }), false);
  assert.equal(matchesRefundProgressDetail(snapshot, { ...snapshot, paidAmount: "87.99" }), false);
  assert.equal(matchesRefundProgressDetail(snapshot, { ...snapshot, refundMethod: "余额退款" }), false);
  assert.equal(matchesRefundProgressDetail(snapshot, { ...snapshot, refundReason: "商户原因" }), false);
  assert.equal(matchesRefundProgressDetail(snapshot, { ...snapshot, channelCode: "ALI_H5" }), false);
  assert.equal(matchesRefundProgressDetail(snapshot, { ...snapshot, channelRefundNo: "CH-REF-002" }), false);
  assert.equal(matchesRefundProgressDetail(snapshot, { ...snapshot, terminalAt: "2026-09-29 06:04:00" }), false);
  assert.equal(matchesRefundProgressDetail({ ...snapshot, successAt: "-", terminalAt: "-" }, {
    ...snapshot,
    successAt: "2026-09-29T06:02:00",
    terminalAt: "2026-09-29T06:03:00"
  }), true);
  assert.equal(matchesRefundProgressDetail(snapshot, { ...snapshot, refundAmount: "18.001" }), false);
  assert.equal(matchesRefundProgressDetail(snapshot, { ...snapshot, refundAmount: "1e2" }), false);
  assert.equal(matchesRefundProgressDetail({
    ...snapshot,
    refundAmount: "9007199254740992.01"
  }, {
    ...snapshot,
    refundAmount: "9007199254740992.02"
  }), false);
  assert.equal(matchesRefundProgressDetail(snapshot, {
    ...snapshot,
    customerName: "李四"
  }), false);
  assert.equal(matchesRefundProgressDetail(snapshot, {
    ...snapshot,
    customerName: undefined
  }), true);
  assert.equal(matchesRefundProgressDetail(snapshot, {
    ...snapshot,
    paymentMethod: "支付宝"
  }), false);
  assert.equal(matchesRefundProgressDetail(snapshot, {
    ...snapshot,
    payScene: "WALLET_RECHARGE_APP"
  }), false);
});

test("derives the refund business chain from a trimmed payment order number", () => {
  const snapshot = {
    refundOrderId: "REF-001",
    paymentOrderId: " PAY-001 ",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    refundAmount: "18.00",
    paidAmount: "88.00",
    refundMethod: "原路退款",
    refundReason: "客户申请"
  };
  assert.equal(matchesRefundProgressDetail(snapshot, {
    ...snapshot,
    businessChainId: "CHAIN-PAY-001"
  }), true);
  assert.equal(matchesRefundProgressDetail(snapshot, {
    ...snapshot,
    businessChainId: "CHAIN- PAY-001 "
  }), false);
});

test("retries automatic refund progress after a transient failure", () => {
  assert.equal(shouldRetryRefundProgress({
    automatic: true,
    requestMatches: true,
    isTerminal: false,
    attempts: 1
  }), true);
});

test("does not retry mismatched, terminal, manual, or exhausted requests", () => {
  const base = { automatic: true, requestMatches: true, isTerminal: false, attempts: 1 };
  assert.equal(shouldRetryRefundProgress({ ...base, requestMatches: false }), false);
  assert.equal(shouldRetryRefundProgress({ ...base, isTerminal: true }), false);
  assert.equal(shouldRetryRefundProgress({ ...base, automatic: false }), false);
  assert.equal(shouldRetryRefundProgress({ ...base, attempts: REFUND_PROGRESS_MAX_POLL_ATTEMPTS }), false);
});
