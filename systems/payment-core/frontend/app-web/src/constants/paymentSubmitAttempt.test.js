import assert from "node:assert/strict";
import test from "node:test";
import {
  UnverifiablePaymentSubmitAttemptError,
  clearPaymentSubmitAttempt,
  discardPaymentSubmitAttempt,
  generatePaymentSubmitKey,
  readPaymentSubmitAttemptForPrepay,
  resolvePaymentSubmitAttempt
} from "./paymentSubmitAttempt.js";

function memoryStorage() {
  const values = new Map();
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
    values
  };
}

const payload = {
  prepayOrderNo: "PRE-001",
  paymentMethod: "支付宝",
  channelCode: "alipay_h5",
  sourceAppId: "housekeeping-app-web",
  merchantNo: "MERCHANT-001",
  accessToken: "private-local-token",
  terminal: "APP_WEB"
};

test("restores a submit key after reload for the same payment request only", async () => {
  const storage = memoryStorage();
  const first = await resolvePaymentSubmitAttempt(payload, generatePaymentSubmitKey(), storage);
  const saved = [...storage.values.values()][0];
  assert.equal(JSON.parse(saved).digest.length, 64);
  assert.equal(saved.includes("private-local-token"), false);
  assert.equal(saved.includes("PRE-001"), false);
  const restored = await resolvePaymentSubmitAttempt({ ...payload }, generatePaymentSubmitKey(), storage);
  assert.equal(restored.idempotencyKey, first.idempotencyKey);
  assert.equal((await readPaymentSubmitAttemptForPrepay("PRE-001", storage)).paymentMethod, "支付宝");
  assert.equal(await readPaymentSubmitAttemptForPrepay("PRE-002", storage), null);
  const variations = [
    { paymentMethod: "微信支付" },
    { channelCode: "wx_h5" },
    { sourceAppId: "housekeeping-h5-web" },
    { merchantNo: "MERCHANT-002" },
    { accessToken: "another-local-token" },
    { terminal: "H5_WEB" }
  ];
  for (const variation of variations) {
    await assert.rejects(
      resolvePaymentSubmitAttempt({ ...payload, ...variation }, generatePaymentSubmitKey(), storage),
      /待核实的支付提交/
    );
  }
  const otherPrepay = await resolvePaymentSubmitAttempt(
    { ...payload, prepayOrderNo: "PRE-002" }, generatePaymentSubmitKey(), storage
  );
  assert.notEqual(otherPrepay.idempotencyKey, first.idempotencyKey);
  assert.equal(storage.values.size, 2);
  assert.equal((await readPaymentSubmitAttemptForPrepay("PRE-001", storage)).idempotencyKey,
    first.idempotencyKey);
  clearPaymentSubmitAttempt(first, storage);
  assert.equal(storage.values.size, 1);
  assert.equal((await readPaymentSubmitAttemptForPrepay("PRE-002", storage)).idempotencyKey,
    otherPrepay.idempotencyKey);
  await discardPaymentSubmitAttempt("PRE-002", storage);
  assert.equal(storage.values.size, 0);
});

test("blocks a malformed saved attempt instead of issuing a new key", async () => {
  const storage = memoryStorage();
  const first = await resolvePaymentSubmitAttempt(payload, generatePaymentSubmitKey(), storage);
  const storageKey = [...storage.values.keys()][0];
  for (const invalidRecord of [
    JSON.stringify({ ...first, idempotencyKey: "invalid\nkey" }),
    JSON.stringify({ ...first, digest: null }),
    "{broken-json"
  ]) {
    storage.setItem(storageKey, invalidRecord);
    await assert.rejects(readPaymentSubmitAttemptForPrepay("PRE-001", storage),
      UnverifiablePaymentSubmitAttemptError);
    await assert.rejects(resolvePaymentSubmitAttempt(payload, generatePaymentSubmitKey(), storage),
      UnverifiablePaymentSubmitAttemptError);
    assert.equal(storage.getItem(storageKey), invalidRecord);
    assert.equal(await readPaymentSubmitAttemptForPrepay("PRE-002", storage), null);
  }
});

test("fails closed when request digest is unavailable even with a saved pending attempt", async () => {
  const storage = memoryStorage();
  const saved = await resolvePaymentSubmitAttempt(payload, generatePaymentSubmitKey(), storage);
  const cryptoProvider = {
    getRandomValues: (bytes) => bytes.fill(7),
    subtle: { digest: async () => { throw new Error("unavailable"); } }
  };
  assert.match(generatePaymentSubmitKey(cryptoProvider), /^07070707-0707-4707-8707-070707070707$/);
  await assert.rejects(readPaymentSubmitAttemptForPrepay("PRE-001", storage, cryptoProvider),
    UnverifiablePaymentSubmitAttemptError);
  await assert.rejects(resolvePaymentSubmitAttempt(payload, generatePaymentSubmitKey(), storage, cryptoProvider),
    UnverifiablePaymentSubmitAttemptError);
  await assert.rejects(discardPaymentSubmitAttempt("PRE-001", storage, cryptoProvider),
    UnverifiablePaymentSubmitAttemptError);
  assert.equal((await readPaymentSubmitAttemptForPrepay("PRE-001", storage)).idempotencyKey,
    saved.idempotencyKey);
  await discardPaymentSubmitAttempt("PRE-001", storage);
  await assert.rejects(resolvePaymentSubmitAttempt(payload, generatePaymentSubmitKey(), storage, cryptoProvider),
    UnverifiablePaymentSubmitAttemptError);
  assert.throws(() => generatePaymentSubmitKey(null), TypeError);
});

test("blocks a fresh submit key when session storage cannot be read or opened", async () => {
  const unavailableStorage = {
    getItem: () => { throw new Error("blocked"); },
    setItem: () => { throw new Error("blocked"); },
    removeItem: () => { throw new Error("blocked"); }
  };
  const currentKey = generatePaymentSubmitKey();
  await assert.rejects(readPaymentSubmitAttemptForPrepay("PRE-001", unavailableStorage),
    UnverifiablePaymentSubmitAttemptError);
  await assert.rejects(resolvePaymentSubmitAttempt(payload, currentKey, unavailableStorage),
    UnverifiablePaymentSubmitAttemptError);
  await assert.rejects(discardPaymentSubmitAttempt("PRE-001", unavailableStorage),
    UnverifiablePaymentSubmitAttemptError);
  await assert.rejects(readPaymentSubmitAttemptForPrepay("PRE-001", null),
    UnverifiablePaymentSubmitAttemptError);
  await assert.rejects(resolvePaymentSubmitAttempt(payload, currentKey, null),
    UnverifiablePaymentSubmitAttemptError);
});

test("blocks submit and reset when session storage cannot persist the attempt", async () => {
  const storage = memoryStorage();
  storage.setItem = () => { throw new Error("quota exceeded"); };
  storage.removeItem = () => { throw new Error("blocked"); };
  await assert.rejects(resolvePaymentSubmitAttempt(payload, generatePaymentSubmitKey(), storage),
    UnverifiablePaymentSubmitAttemptError);
  await assert.rejects(discardPaymentSubmitAttempt("PRE-001", storage),
    UnverifiablePaymentSubmitAttemptError);
});

test("does not submit when storage silently drops a new pending key", async () => {
  const storage = memoryStorage();
  storage.setItem = () => {};
  await assert.rejects(resolvePaymentSubmitAttempt(payload, generatePaymentSubmitKey(), storage),
    UnverifiablePaymentSubmitAttemptError);
  assert.equal(storage.values.size, 0);
});

test("does not report a reset when storage silently retains a pending key", async () => {
  const storage = memoryStorage();
  await resolvePaymentSubmitAttempt(payload, generatePaymentSubmitKey(), storage);
  storage.removeItem = () => {};
  await assert.rejects(discardPaymentSubmitAttempt("PRE-001", storage),
    UnverifiablePaymentSubmitAttemptError);
  assert.equal(storage.values.size, 1);
});

test("does not report a confirmed submit key as cleared when storage retains it", async () => {
  const storage = memoryStorage();
  const attempt = await resolvePaymentSubmitAttempt(payload, generatePaymentSubmitKey(), storage);
  storage.removeItem = () => {};
  assert.throws(() => clearPaymentSubmitAttempt(attempt, storage),
    (error) => error instanceof UnverifiablePaymentSubmitAttemptError
      && error.message.includes("支付响应已返回"));
  assert.equal(JSON.parse([...storage.values.values()][0]).confirmed, true);
  assert.equal((await readPaymentSubmitAttemptForPrepay("PRE-001", storage)).confirmed, true);
  await assert.rejects(resolvePaymentSubmitAttempt(payload, generatePaymentSubmitKey(), storage),
    UnverifiablePaymentSubmitAttemptError);
  await assert.rejects(discardPaymentSubmitAttempt("PRE-001", storage),
    UnverifiablePaymentSubmitAttemptError);
  await resolvePaymentSubmitAttempt({ ...payload, prepayOrderNo: "PRE-002" }, generatePaymentSubmitKey(), storage);
});

test("does not clear a different pending key after a confirmed submit", async () => {
  const storage = memoryStorage();
  const attempt = await resolvePaymentSubmitAttempt(payload, generatePaymentSubmitKey(), storage);
  const storageKey = [...storage.values.keys()][0];
  const differentAttempt = { ...attempt, idempotencyKey: generatePaymentSubmitKey() };
  storage.setItem(storageKey, JSON.stringify(differentAttempt));
  assert.throws(() => clearPaymentSubmitAttempt(attempt, storage),
    UnverifiablePaymentSubmitAttemptError);
  assert.equal((await readPaymentSubmitAttemptForPrepay("PRE-001", storage)).idempotencyKey,
    differentAttempt.idempotencyKey);
});

test("fails closed when confirmed submit cleanup storage throws", async () => {
  const storage = memoryStorage();
  const attempt = await resolvePaymentSubmitAttempt(payload, generatePaymentSubmitKey(), storage);
  storage.removeItem = () => { throw new Error("blocked"); };
  assert.throws(() => clearPaymentSubmitAttempt(attempt, storage),
    UnverifiablePaymentSubmitAttemptError);
  assert.equal(JSON.parse([...storage.values.values()][0]).confirmed, true);
  await assert.rejects(resolvePaymentSubmitAttempt(payload, generatePaymentSubmitKey(), storage),
    UnverifiablePaymentSubmitAttemptError);
});

test("does not claim a confirmed marker when storage silently ignores its write", async () => {
  const storage = memoryStorage();
  const attempt = await resolvePaymentSubmitAttempt(payload, generatePaymentSubmitKey(), storage);
  storage.removeItem = () => {};
  storage.setItem = () => {};
  assert.throws(() => clearPaymentSubmitAttempt(attempt, storage), UnverifiablePaymentSubmitAttemptError);
  assert.equal(JSON.parse([...storage.values.values()][0]).confirmed, undefined);
});

test("rejects a saved UUID with a non-string representation", async () => {
  const storage = memoryStorage();
  const attempt = await resolvePaymentSubmitAttempt(payload, generatePaymentSubmitKey(), storage);
  const storageKey = [...storage.values.keys()][0];
  storage.setItem(storageKey, JSON.stringify({ ...attempt, idempotencyKey: [attempt.idempotencyKey] }));
  await assert.rejects(resolvePaymentSubmitAttempt(payload, generatePaymentSubmitKey(), storage),
    UnverifiablePaymentSubmitAttemptError);
});
