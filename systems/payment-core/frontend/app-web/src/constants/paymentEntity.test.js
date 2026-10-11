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
  const snapshot = {
    prepayOrderNo: "PRE-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    billNo: "BILL-001",
    customerName: "张三",
    createdAt: "2026-09-27T11:59:00",
    successAt: "2026-09-27T12:01:00",
    latestAttemptCompletedAt: "2026-09-27T12:00:00"
  };
  assert.equal(matchesPaymentSnapshot(snapshot, {
    ...snapshot
  }), true);
  assert.equal(matchesPaymentSnapshot({ ...snapshot, orderNo: "ORDER-001" }, {
    ...snapshot,
    orderNo: "ORDER-001"
  }), true);
  assert.equal(matchesPaymentSnapshot({ ...snapshot, orderNo: "ORDER-001" }, {
    ...snapshot,
    orderNo: "ORDER-002"
  }), false);
  assert.equal(matchesPaymentSnapshot({ ...snapshot, amount: "¥68.00" }, {
    ...snapshot,
    amount: "68.00"
  }), true);
  assert.equal(matchesPaymentSnapshot({ ...snapshot, amount: "¥68.00" }, {
    ...snapshot,
    amount: "67.99"
  }), false);
  assert.equal(matchesPaymentSnapshot({ ...snapshot, paymentMethod: "微信支付" }, {
    ...snapshot,
    paymentMethod: "微信支付"
  }), true);
  assert.equal(matchesPaymentSnapshot({ ...snapshot, paymentMethod: "微信支付" }, {
    ...snapshot,
    paymentMethod: "支付宝"
  }), false);
  assert.equal(matchesPaymentSnapshot({ ...snapshot, paymentMethod: "微信支付" }, {
    ...snapshot,
    paymentMethod: undefined
  }), true);
  assert.equal(matchesPaymentSnapshot({ ...snapshot, channel: "WX_H5" }, {
    ...snapshot,
    channel: "WX_H5"
  }), true);
  assert.equal(matchesPaymentSnapshot({ ...snapshot, channel: "WX_H5" }, {
    ...snapshot,
    channel: "ALIPAY_H5"
  }), false);
  assert.equal(matchesPaymentSnapshot({ ...snapshot, channelTransactionNo: "WX-001" }, {
    ...snapshot,
    channelTransactionNo: "WX-001"
  }), true);
  assert.equal(matchesPaymentSnapshot(snapshot, {
    ...snapshot,
    successAt: "2026-09-27 12:02:00"
  }), false);
  assert.equal(matchesPaymentSnapshot(snapshot, {
    ...snapshot,
    latestAttemptCompletedAt: "2026-09-27 12:00:01"
  }), false);
  assert.equal(matchesPaymentSnapshot({ ...snapshot, successAt: "-", latestAttemptCompletedAt: "-" }, {
    ...snapshot,
    successAt: "2026-09-27 12:01:00",
    latestAttemptCompletedAt: "2026-09-27 12:00:00"
  }), true);
  assert.equal(matchesPaymentSnapshot({ ...snapshot, channelTransactionNo: "WX-001" }, {
    ...snapshot,
    channelTransactionNo: "WX-002"
  }), false);
  assert.equal(matchesPaymentSnapshot({ ...snapshot, refundOrderId: "REF-001" }, {
    ...snapshot,
    refundOrderId: "REF-001"
  }), true);
  assert.equal(matchesPaymentSnapshot({ ...snapshot, refundOrderId: "REF-001" }, {
    ...snapshot,
    refundOrderId: "REF-002"
  }), false);
  assert.equal(matchesPaymentSnapshot(snapshot, {
    ...snapshot,
    billNo: "BILL-002"
  }), false);
  assert.equal(matchesPaymentSnapshot(snapshot, {
    ...snapshot,
    customerName: "李四"
  }), false);
  assert.equal(matchesPaymentSnapshot(snapshot, {
    ...snapshot,
    billNo: undefined,
    customerName: undefined
  }), true);
  assert.equal(matchesPaymentSnapshot({ ...snapshot, amount: "1.23" }, {
    ...snapshot,
    amount: "1.234"
  }), false);
  assert.equal(matchesPaymentSnapshot({ ...snapshot, amount: "1e3" }, {
    ...snapshot,
    amount: "1000.00"
  }), false);
  assert.equal(matchesPaymentSnapshot({
    ...snapshot,
    amount: "9007199254740992.01"
  }, {
    ...snapshot,
    amount: "9007199254740992.02"
  }), false);
  assert.equal(matchesPaymentSnapshot(snapshot, {
    ...snapshot,
    businessChainId: "CHAIN-OTHER"
  }), false);
  assert.equal(matchesPaymentSnapshot({
    ...snapshot,
    businessChainId: "CHAIN-OTHER"
  }, {
    ...snapshot,
    businessChainId: "CHAIN-OTHER"
  }), false);
  assert.equal(matchesPaymentSnapshot(snapshot, {
    ...snapshot,
    prepayOrderNo: "PRE-002"
  }), false);
  assert.equal(matchesPaymentSnapshot(snapshot, {
    ...snapshot,
    paymentOrderId: "PAY-002",
    businessChainId: "CHAIN-PAY-002"
  }), false);
});

test("fails closed for empty, array, non-string and incomplete identities", () => {
  const snapshot = {
    prepayOrderNo: "PRE-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001"
  };
  for (const value of [null, undefined, {}, [], "PRE-001"]) {
    assert.equal(matchesPrepayOrder("PRE-001", value), false);
    assert.equal(matchesPaymentOrder("PAY-001", value), false);
    assert.equal(matchesPaymentSnapshot(snapshot, value), false);
  }
  assert.equal(matchesPrepayOrder([], { prepayOrderNo: "PRE-001" }), false);
  assert.equal(matchesPaymentOrder([], { paymentOrderId: "PAY-001" }), false);
  assert.equal(matchesPaymentSnapshot({ prepayOrderNo: "", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001" }, {
    prepayOrderNo: "PRE-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001"
  }), false);
  assert.equal(matchesPaymentSnapshot({ prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001" }, {
    prepayOrderNo: "PRE-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001"
  }), false);
});

test("rejects payment action responses from another payment scene", () => {
  const snapshot = {
    prepayOrderNo: "PRE-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    payScene: "BALANCE_PAY_APP"
  };
  assert.equal(matchesPaymentSnapshot(snapshot, {
    ...snapshot,
    payScene: "BALANCE_PAY_APP"
  }), true);
  assert.equal(matchesPaymentSnapshot(snapshot, {
    ...snapshot,
    payScene: "WALLET_RECHARGE_APP"
  }), false);
  assert.equal(matchesPaymentSnapshot(snapshot, {
    ...snapshot,
    payScene: undefined
  }), false);
});
