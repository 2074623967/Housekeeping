import assert from "node:assert/strict";
import test from "node:test";
import {
  assertPendingReview,
  assertChangedContextDenied,
  assertReviewApproved,
  assertReviewedCashier,
  assertOriginalRequestContinued,
  assertHardBlockedBeforeReview
} from "./cross-system-risk-review.mjs";

const prepay = {
  orderNo: "LOCAL-RISK-001",
  prepayOrderNo: "PRE-RISK-001",
  paymentOrderId: "PAY-RISK-001"
};

test("binds the pending review to the submitted payment and unique review order", () => {
  const submitted = {
    prepayOrderNo: prepay.prepayOrderNo,
    paymentOrderId: prepay.paymentOrderId,
    paymentStatus: "RISK_REVIEW",
    cashierStatus: "待风控复核"
  };
  const page = { records: [{ businessNo: prepay.paymentOrderId,
    reviewNo: "REVIEW-001", status: "PENDING" }] };
  assert.equal(assertPendingReview(prepay, submitted, page), "REVIEW-001");
  assert.throws(() => assertPendingReview(prepay,
    { ...submitted, paymentOrderId: "PAY-OTHER" }, page));
  assert.throws(() => assertPendingReview(prepay, submitted,
    { records: [{ ...page.records[0], businessNo: "PAY-OTHER" }] }));
  assert.throws(() => assertPendingReview(prepay, submitted,
    { records: [page.records[0], page.records[0]] }));
});

test("requires a failed context change without a payment attempt", () => {
  const envelope = { code: "PAYMENT-1024", data: null };
  const detail = { paymentOrderId: prepay.paymentOrderId, status: "RISK_REVIEW",
    paymentMethod: "微信支付", channel: "wx_h5",
    channelTransactionNo: null, latestAttemptStatus: null };
  assert.doesNotThrow(() => assertChangedContextDenied(envelope, detail, prepay.paymentOrderId));
  assert.throws(() => assertChangedContextDenied({ code: "0", data: {} }, detail, prepay.paymentOrderId));
  assert.throws(() => assertChangedContextDenied(envelope,
    { ...detail, status: "WAIT_CALLBACK" }, prepay.paymentOrderId));
  assert.throws(() => assertChangedContextDenied(envelope,
    { ...detail, latestAttemptStatus: "待回调" }, prepay.paymentOrderId));
  assert.throws(() => assertChangedContextDenied(envelope,
    { ...detail, channel: "alipay_h5" }, prepay.paymentOrderId));
});

test("requires approval of this review and continuation of this prepay", () => {
  const approved = { records: [{ reviewNo: "REVIEW-001", status: "APPROVED" }] };
  assert.doesNotThrow(() => assertReviewApproved(approved, "REVIEW-001"));
  assert.throws(() => assertReviewApproved(approved, "REVIEW-OTHER"));
  const continued = { prepayOrderNo: prepay.prepayOrderNo,
    paymentOrderId: prepay.paymentOrderId, paymentStatus: "WAIT_CALLBACK" };
  assert.doesNotThrow(() => assertOriginalRequestContinued(prepay, continued));
  assert.throws(() => assertOriginalRequestContinued(prepay,
    { ...continued, prepayOrderNo: "PRE-OTHER" }));
  assert.throws(() => assertOriginalRequestContinued(prepay,
    { ...continued, paymentStatus: "RISK_REVIEW" }));
});

test("binds the reviewed cashier to its original method and channel", () => {
  const cashier = { prepayOrderNo: prepay.prepayOrderNo, paymentOrderId: prepay.paymentOrderId,
    paymentStatus: "RISK_REVIEW", reviewPaymentMethod: "微信支付", reviewChannelCode: "wx_h5",
    latestAttemptStatus: null };
  assert.doesNotThrow(() => assertReviewedCashier(cashier, prepay, "微信支付", "wx_h5"));
  assert.throws(() => assertReviewedCashier({ ...cashier, reviewPaymentMethod: "支付宝" }, prepay, "微信支付", "wx_h5"));
  assert.throws(() => assertReviewedCashier({ ...cashier, reviewChannelCode: "alipay_h5" }, prepay, "微信支付", "wx_h5"));
  assert.throws(() => assertReviewedCashier({ ...cashier, channelTransactionNo: "WX-001" }, prepay, "微信支付", "wx_h5"));
});

test("requires a hard policy to block before review without payment attempts", () => {
  const submitted = { prepayOrderNo: prepay.prepayOrderNo,
    paymentOrderId: prepay.paymentOrderId, paymentStatus: "RISK_BLOCKED", cashierStatus: "风控拦截" };
  const detail = { paymentOrderId: prepay.paymentOrderId, status: "RISK_BLOCKED",
    channelTransactionNo: null, latestAttemptStatus: null };
  const reviews = { records: [{ businessNo: "PAY-OTHER", reviewNo: "REVIEW-OTHER" }] };
  const events = { records: [{ paymentOrderId: prepay.paymentOrderId,
    hitPolicy: "POLICY_DEVICE_RISK", decisionResult: "已拦截", eventNo: "RISK-EVT-001" }] };
  assert.doesNotThrow(() => assertHardBlockedBeforeReview(prepay, submitted, detail, reviews, events));
  assert.throws(() => assertHardBlockedBeforeReview(prepay,
    { ...submitted, paymentStatus: "RISK_REVIEW" }, detail, reviews, events));
  assert.throws(() => assertHardBlockedBeforeReview(prepay, submitted,
    { ...detail, latestAttemptStatus: "待回调" }, reviews, events));
  assert.throws(() => assertHardBlockedBeforeReview(prepay, submitted, detail,
    { records: [{ businessNo: prepay.paymentOrderId }] }, events));
  assert.throws(() => assertHardBlockedBeforeReview(prepay, submitted, detail, reviews,
    { records: [{ ...events.records[0], hitPolicy: "OTHER_POLICY" }] }));
});
