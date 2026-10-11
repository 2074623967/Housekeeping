import assert from "node:assert/strict";
import test from "node:test";
import {
  canClosePaymentLocally,
  canRetryReviewedPayment,
  canQueryPaymentChannel,
  canShowDesktopQrPlaceholder,
  isCashierExpired,
  isPaymentSubmissionPending,
  PAYMENT_RESULT_STATE_META,
  resolvePaymentResultState,
  resolvePaymentMerchantNo,
  resolvePaymentDetailRefreshDelay,
  shouldAutoRefreshPaymentDetail
} from "./payment.js";

test("uses the merchant configured for each payment channel", () => {
  assert.equal(resolvePaymentMerchantNo("微信支付"), "MCH_WX_10001");
  assert.equal(resolvePaymentMerchantNo("支付宝"), "MCH_ALI_20001");
  assert.equal(resolvePaymentMerchantNo("银行卡"), "BANK_OFFLINE_30001");
});

test("only retries a reviewed cashier with its original method and channel", () => {
  const cashier = { paymentStatus: "RISK_REVIEW", status: "待风控复核",
    channels: ["微信支付", "支付宝"], reviewPaymentMethod: "微信支付", reviewChannelCode: "WX_H5" };
  assert.equal(canRetryReviewedPayment(cashier, "微信支付"), true);
  assert.equal(canRetryReviewedPayment({ ...cashier, reviewChannelCode: "wx_h5" }, "微信支付"), true);
  assert.equal(canRetryReviewedPayment(cashier, "支付宝"), false);
  assert.equal(canRetryReviewedPayment({ ...cashier, reviewChannelCode: "ALIPAY_H5" }, "微信支付"), false);
  assert.equal(canRetryReviewedPayment({ ...cashier, reviewPaymentMethod: null }, "微信支付"), false);
  assert.equal(canRetryReviewedPayment({ ...cashier, paymentStatus: "WAIT_CALLBACK" }, "微信支付"), false);
  assert.equal(canRetryReviewedPayment({ ...cashier, channels: ["支付宝"] }, "微信支付"), false);
});

test("only queries readable payments or open submitted attempts", () => {
  assert.equal(canQueryPaymentChannel("SUCCESS", null), true);
  assert.equal(canQueryPaymentChannel("CLOSED", null), true);
  assert.equal(canQueryPaymentChannel("CLOSED", null, null, "WX-1"), false);
  assert.equal(canQueryPaymentChannel("CLOSED", "处理中", null, null, "wx_h5"), true);
  assert.equal(canQueryPaymentChannel("CLOSED", "失败"), false);
  assert.equal(canQueryPaymentChannel("WAIT_CALLBACK", "处理中", null, "WX-1", "wx_h5"), true);
  assert.equal(canQueryPaymentChannel("WAIT_CALLBACK", "处理中", null, "WX-1", null), false);
  assert.equal(canQueryPaymentChannel("CLOSED", "处理中", null, "WX-1", " "), false);
  assert.equal(canQueryPaymentChannel("PAYING", "处理中"), false);
  for (const attemptStatus of ["成功", "支付成功", " 支付成功 ", "失败", "支付失败", "SUCCESS", "FAIL", "FAILED", " FAILED "]) {
    assert.equal(canQueryPaymentChannel("WAIT_CALLBACK", attemptStatus, null, "WX-1", "wx_h5"), false);
  }
  assert.equal(canQueryPaymentChannel("WAIT_CALLBACK", "处理中", "2026-10-07 09:00:00", "WX-1", "wx_h5"), false);
  assert.equal(canQueryPaymentChannel("PAYING", null), false);
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

test("treats the server-side expired cashier status as unavailable", () => {
  assert.equal(isCashierExpired("已失效"), true);
  assert.equal(isCashierExpired("待支付"), false);
  assert.equal(canClosePaymentLocally("已失效", "", "-"), true);
});

test("only shows the desktop QR placeholder for a fresh valid submission", () => {
  assert.equal(canShowDesktopQrPlaceholder({
    terminalVariant: "pc",
    hasValidCashierSnapshot: true,
    canSubmit: true
  }), true);
  assert.equal(canShowDesktopQrPlaceholder({
    terminalVariant: "pc",
    hasValidCashierSnapshot: true,
    canSubmit: false
  }), false);
  assert.equal(canShowDesktopQrPlaceholder({
    terminalVariant: "app",
    hasValidCashierSnapshot: true,
    canSubmit: true
  }), false);
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

test("uncertain and failed payment results never direct unavailable channel queries", () => {
  assert.equal(resolvePaymentResultState("FAIL"), "fail");
  assert.equal(resolvePaymentResultState("UNKNOWN"), "pending");
  assert.match(PAYMENT_RESULT_STATE_META.fail.hint, /刷新支付详情|联系/);
  assert.doesNotMatch(PAYMENT_RESULT_STATE_META.fail.hint, /主动查单|支付失败/);
  assert.doesNotMatch(PAYMENT_RESULT_STATE_META.pending.hint, /主动查单/);
  assert.equal(canQueryPaymentChannel("FAIL", "处理中", null, "WX-1", "wx_h5"), false);
});

test("keeps channel submission in progress distinct from accepted callback waiting", () => {
  assert.equal(resolvePaymentResultState("PAYING"), "paying");
  assert.equal(resolvePaymentResultState("WAIT_CALLBACK"), "wait_callback");
  assert.match(PAYMENT_RESULT_STATE_META.paying.hint, /刷新/);
  assert.doesNotMatch(PAYMENT_RESULT_STATE_META.paying.hint, /已提交到渠道|主动查单/);
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
