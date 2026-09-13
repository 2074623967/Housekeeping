import assert from "node:assert/strict";
import test from "node:test";
import { canClosePaymentLocally, canQueryPaymentChannel } from "./paymentAction.js";

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
