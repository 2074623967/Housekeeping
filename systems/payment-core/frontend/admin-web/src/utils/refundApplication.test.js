import assert from "node:assert/strict";
import test from "node:test";
import { resolveRefundApplicationAttempt } from "./refundApplication.js";

const baseRequest = {
  paymentOrderId: "PAY-001",
  refundAmount: "68.00",
  refundMethod: "原路退款",
  refundReason: "客户取消服务",
  operator: "finance-operator"
};

test("reuses the refund application key for an unchanged retry", () => {
  const firstAttempt = resolveRefundApplicationAttempt(baseRequest, {
    idempotencyKey: "",
    requestFingerprint: ""
  }, () => "REFUND-KEY-001");
  let generatedCount = 0;

  const retryAttempt = resolveRefundApplicationAttempt(
    { ...baseRequest },
    firstAttempt,
    () => {
      generatedCount += 1;
      return "REFUND-KEY-002";
    }
  );

  assert.deepEqual(retryAttempt, firstAttempt);
  assert.equal(generatedCount, 0);
});

test("creates the first refund application attempt without previous state", () => {
  const attempt = resolveRefundApplicationAttempt(
    baseRequest,
    undefined,
    () => "REFUND-KEY-001"
  );

  assert.equal(attempt.idempotencyKey, "REFUND-KEY-001");
  assert.equal(attempt.requestFingerprint, JSON.stringify(baseRequest));
});

test("generates a new refund application key after business content changes", () => {
  const currentAttempt = resolveRefundApplicationAttempt(baseRequest, {
    idempotencyKey: "",
    requestFingerprint: ""
  }, () => "REFUND-KEY-001");
  const changedRequests = [
    { ...baseRequest, paymentOrderId: "PAY-002" },
    { ...baseRequest, refundAmount: "69.00" },
    { ...baseRequest, refundMethod: "线下打款" },
    { ...baseRequest, refundReason: "服务未履约" },
    { ...baseRequest, operator: "another-operator" }
  ];

  changedRequests.forEach((request, index) => {
    const nextAttempt = resolveRefundApplicationAttempt(
      request,
      currentAttempt,
      () => `REFUND-KEY-${index + 2}`
    );
    assert.notEqual(nextAttempt.idempotencyKey, currentAttempt.idempotencyKey);
    assert.notEqual(nextAttempt.requestFingerprint, currentAttempt.requestFingerprint);
  });
});
