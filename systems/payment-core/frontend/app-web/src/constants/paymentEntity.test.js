import assert from "node:assert/strict";
import test from "node:test";
import {
  matchesPaymentOrder,
  matchesPaymentSnapshot,
  matchesPrepayOrder
} from "./paymentEntity.js";

test("matches a cashier response to the current prepay route", () => {
  assert.equal(matchesPrepayOrder("PRE-001", { prepayOrderNo: "PRE-001" }), true);
  assert.equal(matchesPrepayOrder(" PRE-001 ", { prepayOrderNo: " PRE-001 " }), true);
  assert.equal(matchesPrepayOrder("PRE-001", { prepayOrderNo: "PRE-002" }), false);
});

test("matches a payment detail response to the current payment route", () => {
  assert.equal(matchesPaymentOrder("PAY-001", { paymentOrderId: "PAY-001" }), true);
  assert.equal(matchesPaymentOrder("PAY-001", { paymentOrderId: "PAY-002" }), false);
});

test("requires query and close responses to match the loaded payment snapshot", () => {
  const snapshot = { prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001" };
  assert.equal(matchesPaymentSnapshot(snapshot, {
    prepayOrderNo: "PRE-001",
    paymentOrderId: "PAY-001"
  }), true);
  assert.equal(matchesPaymentSnapshot(snapshot, {
    prepayOrderNo: "PRE-002",
    paymentOrderId: "PAY-001"
  }), false);
  assert.equal(matchesPaymentSnapshot(snapshot, {
    prepayOrderNo: "PRE-001",
    paymentOrderId: "PAY-002"
  }), false);
});

test("fails closed for empty, array, non-string and incomplete identities", () => {
  const snapshot = { prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001" };
  for (const value of [null, undefined, {}, [], "PRE-001"]) {
    assert.equal(matchesPrepayOrder("PRE-001", value), false);
    assert.equal(matchesPaymentOrder("PAY-001", value), false);
    assert.equal(matchesPaymentSnapshot(snapshot, value), false);
  }
  assert.equal(matchesPrepayOrder([], { prepayOrderNo: "PRE-001" }), false);
  assert.equal(matchesPaymentOrder([], { paymentOrderId: "PAY-001" }), false);
  assert.equal(matchesPaymentSnapshot({ prepayOrderNo: "", paymentOrderId: "PAY-001" }, {
    prepayOrderNo: "PRE-001",
    paymentOrderId: "PAY-001"
  }), false);
});
