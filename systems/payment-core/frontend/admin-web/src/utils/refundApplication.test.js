import assert from "node:assert/strict";
import test from "node:test";
import {
  clearRefundApplicationAttempt,
  resolveRefundApplicationAttempt,
  UnverifiableRefundApplicationAttemptError
} from "./refundApplication.js";

const baseRequest = {
  paymentOrderId: "PAY-001",
  refundAmount: "68.00",
  refundMethod: "原路退款",
  refundReason: "客户取消服务",
  operator: "finance-operator"
};

function memoryStorage() {
  const values = new Map();
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
    values
  };
}

test("restores an unresolved application after reload without storing business details", async () => {
  const storage = memoryStorage();
  const first = await resolveRefundApplicationAttempt(baseRequest, storage);
  assert.equal(first.idempotencyKey.length, 36);
  assert.equal(storage.values.size, 1);
  const saved = [...storage.values.values()][0];
  assert.equal(saved.includes("PAY-001"), false);
  assert.equal(saved.includes("客户取消服务"), false);

  const retry = await resolveRefundApplicationAttempt({ ...baseRequest }, storage);
  assert.deepEqual(retry, first);
  clearRefundApplicationAttempt(first, storage);
  assert.equal(storage.values.size, 0);
  const nextApplication = await resolveRefundApplicationAttempt(baseRequest, storage);
  assert.notEqual(nextApplication.idempotencyKey, first.idempotencyKey);
});

test("keeps different pending applications partitioned by the complete request", async () => {
  const storage = memoryStorage();
  const first = await resolveRefundApplicationAttempt(baseRequest, storage);
  const changedRequests = [
    { ...baseRequest, paymentOrderId: "PAY-002" },
    { ...baseRequest, refundAmount: "69.00" },
    { ...baseRequest, refundMethod: "线下打款" },
    { ...baseRequest, refundReason: "服务未履约" },
    { ...baseRequest, operator: "another-operator" }
  ];

  for (const request of changedRequests) {
    const next = await resolveRefundApplicationAttempt(request, storage);
    assert.notEqual(next.idempotencyKey, first.idempotencyKey);
    assert.deepEqual(await resolveRefundApplicationAttempt(baseRequest, storage), first);
  }
  assert.equal(storage.values.size, 6);
  clearRefundApplicationAttempt(first, storage);
  assert.equal(storage.values.size, 5);
});

test("refuses damaged, conflicting or inaccessible pending records", async () => {
  const storage = memoryStorage();
  const first = await resolveRefundApplicationAttempt(baseRequest, storage);
  const key = [...storage.values.keys()][0];
  storage.setItem(key, "{broken-json");
  await assert.rejects(resolveRefundApplicationAttempt(baseRequest, storage), UnverifiableRefundApplicationAttemptError);
  storage.setItem(key, JSON.stringify({ digest: "0".repeat(64), idempotencyKey: first.idempotencyKey }));
  await assert.rejects(resolveRefundApplicationAttempt(baseRequest, storage), UnverifiableRefundApplicationAttemptError);
  storage.setItem(key, JSON.stringify({ digest: first.digest, idempotencyKey: [first.idempotencyKey] }));
  await assert.rejects(resolveRefundApplicationAttempt(baseRequest, storage), UnverifiableRefundApplicationAttemptError);
  storage.removeItem(key);
  await assert.rejects(resolveRefundApplicationAttempt(baseRequest, null), UnverifiableRefundApplicationAttemptError);
  await assert.rejects(resolveRefundApplicationAttempt(baseRequest, { getItem() { throw new Error("blocked"); } }), UnverifiableRefundApplicationAttemptError);
  await assert.rejects(resolveRefundApplicationAttempt(baseRequest, { getItem: () => null, setItem() { throw new Error("quota"); } }), UnverifiableRefundApplicationAttemptError);
  await assert.rejects(resolveRefundApplicationAttempt(baseRequest, { getItem: () => null, setItem() {} }), UnverifiableRefundApplicationAttemptError);
});

test("refuses missing digest or secure random source before sending a request", async () => {
  const storage = memoryStorage();
  await assert.rejects(resolveRefundApplicationAttempt(baseRequest, storage, {}), UnverifiableRefundApplicationAttemptError);
  const cryptoProvider = { subtle: globalThis.crypto.subtle };
  await assert.rejects(resolveRefundApplicationAttempt(baseRequest, storage, cryptoProvider), UnverifiableRefundApplicationAttemptError);
  await assert.rejects(resolveRefundApplicationAttempt(baseRequest, storage, {
    subtle: { digest() { throw new Error("disabled"); } },
    randomUUID: () => "00000000-0000-4000-8000-000000000000"
  }), UnverifiableRefundApplicationAttemptError);
  assert.equal(storage.values.size, 0);
});

test("uses secure random bytes when randomUUID is unavailable", async () => {
  const storage = memoryStorage();
  const cryptoProvider = {
    subtle: globalThis.crypto.subtle,
    getRandomValues: (bytes) => globalThis.crypto.getRandomValues(bytes)
  };
  const attempt = await resolveRefundApplicationAttempt(baseRequest, storage, cryptoProvider);
  assert.match(attempt.idempotencyKey, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
});

test("marks an accepted application as confirmed if removing its key is silently ignored", async () => {
  const storage = memoryStorage();
  const attempt = await resolveRefundApplicationAttempt(baseRequest, storage);
  storage.removeItem = () => {};
  assert.throws(() => clearRefundApplicationAttempt(attempt, storage), UnverifiableRefundApplicationAttemptError);
  assert.equal(JSON.parse([...storage.values.values()][0]).confirmed, true);
  await assert.rejects(resolveRefundApplicationAttempt(baseRequest, storage), UnverifiableRefundApplicationAttemptError);
  assert.equal(storage.values.size, 1);
});

test("reports cleanup failure rather than silently replaying an accepted application", async () => {
  const storage = memoryStorage();
  const attempt = await resolveRefundApplicationAttempt(baseRequest, storage);
  storage.removeItem = () => { throw new Error("blocked"); };
  storage.setItem = () => { throw new Error("blocked"); };
  assert.throws(() => clearRefundApplicationAttempt(attempt, storage), UnverifiableRefundApplicationAttemptError);
  assert.equal(storage.values.size, 1);
});
