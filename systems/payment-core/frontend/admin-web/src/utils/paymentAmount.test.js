import assert from "node:assert/strict";
import test from "node:test";
import {
  normalizeNonNegativePaymentAmountInput,
  normalizePaymentAmountInput,
  sumPaymentAmounts
} from "./paymentAmount.js";

test("keeps refund amounts as exact decimal strings", () => {
  const maximumAmount = "9999999999999999.99";

  assert.equal(normalizePaymentAmountInput(maximumAmount), maximumAmount);
  assert.notEqual(Number(maximumAmount).toString(), maximumAmount);
  assert.equal(normalizePaymentAmountInput(" 0001.20 "), "1.20");
  assert.equal(normalizePaymentAmountInput(".50"), "0.50");
});

test("rejects refund amounts outside the frontend money contract", () => {
  assert.equal(normalizePaymentAmountInput(""), null);
  assert.equal(normalizePaymentAmountInput("0.00"), null);
  assert.equal(normalizePaymentAmountInput("-1"), null);
  assert.equal(normalizePaymentAmountInput("1.001"), null);
  assert.equal(normalizePaymentAmountInput("1e3"), null);
  assert.equal(normalizePaymentAmountInput("10000000000000000.00"), null);
});

test("allows exact zero and maximum channel daily limits", () => {
  assert.equal(normalizeNonNegativePaymentAmountInput("0"), "0");
  assert.equal(normalizeNonNegativePaymentAmountInput("000.00"), "0.00");
  assert.equal(
    normalizeNonNegativePaymentAmountInput("9999999999999999.99"),
    "9999999999999999.99"
  );
  assert.equal(normalizeNonNegativePaymentAmountInput("-1"), null);
  assert.equal(normalizeNonNegativePaymentAmountInput("1.001"), null);
  assert.equal(normalizeNonNegativePaymentAmountInput("10000000000000000.00"), null);
});

test("sums formatted payment amounts without binary floating point", () => {
  assert.equal(sumPaymentAmounts(["¥0.10", "¥0.20"]), "0.30");
  assert.equal(sumPaymentAmounts(["¥1,234.50", "¥0.50", null, "invalid"]), "1235.00");
  assert.equal(
    sumPaymentAmounts(["¥9,999,999,999,999,999.99", "9999999999999999.99"]),
    "19999999999999999.98"
  );
});
