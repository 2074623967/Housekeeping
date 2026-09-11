import assert from "node:assert/strict";
import test from "node:test";
import { normalizePaymentAmountInput } from "./paymentAmount.js";

test("keeps payment amounts as exact decimal strings", () => {
  const maximumAmount = "9999999999999999.99";

  assert.equal(normalizePaymentAmountInput(maximumAmount), maximumAmount);
  assert.notEqual(Number(maximumAmount).toString(), maximumAmount);
  assert.equal(normalizePaymentAmountInput(" 0001.20 "), "1.20");
  assert.equal(normalizePaymentAmountInput(".50"), "0.50");
});

test("rejects amounts outside the frontend money contract", () => {
  assert.equal(normalizePaymentAmountInput(""), null);
  assert.equal(normalizePaymentAmountInput("0.00"), null);
  assert.equal(normalizePaymentAmountInput("-1"), null);
  assert.equal(normalizePaymentAmountInput("1.001"), null);
  assert.equal(normalizePaymentAmountInput("1e3"), null);
  assert.equal(normalizePaymentAmountInput("10000000000000000.00"), null);
});
