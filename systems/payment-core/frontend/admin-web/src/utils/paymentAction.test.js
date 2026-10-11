import assert from "node:assert/strict";
import test from "node:test";
import { canClosePaymentLocally, canQueryPaymentChannel } from "./paymentAction.js";

test("only queries readable payments or open submitted attempts", () => {
  assert.equal(canQueryPaymentChannel("SUCCESS", null), true);
  assert.equal(canQueryPaymentChannel("支付成功", "成功", "2026-10-07 09:00:00", "WX-1"), true);
  assert.equal(canQueryPaymentChannel("CLOSED", null), true);
  assert.equal(canQueryPaymentChannel("已关闭", null), true);
  assert.equal(canQueryPaymentChannel("已关闭", null, null, "WX-1"), false);
  assert.equal(canQueryPaymentChannel("CLOSED", "处理中", null, null, "wx_h5"), true);
  assert.equal(canQueryPaymentChannel("CLOSED", "FAILED"), false);
  assert.equal(canQueryPaymentChannel("WAIT_CALLBACK", "处理中", null, "WX-1", "wx_h5"), true);
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
