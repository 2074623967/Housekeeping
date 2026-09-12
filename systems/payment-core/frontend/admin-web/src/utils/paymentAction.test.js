import assert from "node:assert/strict";
import test from "node:test";
import { canQueryPaymentChannel } from "./paymentAction.js";

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
