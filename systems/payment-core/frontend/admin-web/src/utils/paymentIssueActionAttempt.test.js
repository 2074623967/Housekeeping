import assert from "node:assert/strict";
import test from "node:test";
import {
  clearPaymentIssueActionAttempt,
  resolvePaymentIssueActionAttempt
} from "./paymentIssueActionAttempt.js";

const payload = {
  issueNos: ["ISSUE-002", "ISSUE-001"],
  actionType: "分派处理人",
  assignee: "后端值班",
  operator: "支付运营",
  remark: "核对回调"
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

test("restores an unresolved batch key after reload without storing action contents", async () => {
  const storage = memoryStorage();
  const first = await resolvePaymentIssueActionAttempt(payload, null, storage);
  const saved = [...storage.values.values()][0];
  assert.equal(first.idempotencyKey.length, 36);
  assert.equal(JSON.parse(saved).digest.length, 64);
  assert.equal(saved.includes("支付运营"), false);
  assert.equal(saved.includes("ISSUE-001"), false);

  const restored = await resolvePaymentIssueActionAttempt(
    { ...payload, issueNos: [...payload.issueNos].reverse() }, null, storage
  );
  assert.equal(restored.idempotencyKey, first.idempotencyKey);
  const differentTarget = await resolvePaymentIssueActionAttempt(
    { ...payload, issueNos: ["ISSUE-002"] }, null, storage
  );
  assert.notEqual(differentTarget.idempotencyKey, first.idempotencyKey);
  assert.equal(storage.values.size, 2);
  assert.equal((await resolvePaymentIssueActionAttempt(payload, null, storage)).idempotencyKey,
    first.idempotencyKey);
  clearPaymentIssueActionAttempt(first, storage);
  assert.equal(storage.values.size, 1);
  clearPaymentIssueActionAttempt(differentTarget, storage);
  assert.equal(storage.values.size, 0);
});

test("preserves and replays a legacy single-key attempt while storing a different action", async () => {
  const storage = memoryStorage();
  const first = await resolvePaymentIssueActionAttempt(payload, null, storage);
  const scopedKey = [...storage.values.keys()][0];
  storage.setItem("payment-issue-action-attempt:v1", storage.getItem(scopedKey));
  storage.removeItem(scopedKey);

  const secondPayload = { ...payload, issueNos: ["ISSUE-002"] };
  const second = await resolvePaymentIssueActionAttempt(secondPayload, null, storage);
  assert.equal(storage.values.size, 2);
  assert.equal((await resolvePaymentIssueActionAttempt(payload, null, storage)).idempotencyKey,
    first.idempotencyKey);
  assert.equal((await resolvePaymentIssueActionAttempt(secondPayload, null, storage)).idempotencyKey,
    second.idempotencyKey);
  clearPaymentIssueActionAttempt(second, storage);
  assert.equal(storage.values.size, 1);
  clearPaymentIssueActionAttempt(first, storage);
  assert.equal(storage.values.size, 0);
});

test("isolates a damaged scoped action without hiding another pending key", async () => {
  const storage = memoryStorage();
  const first = await resolvePaymentIssueActionAttempt(payload, null, storage);
  const secondPayload = { ...payload, issueNos: ["ISSUE-002"] };
  const second = await resolvePaymentIssueActionAttempt(secondPayload, null, storage);
  const secondKey = [...storage.values.keys()].find((key) => key.endsWith(second.digest));
  storage.setItem(secondKey, "{broken-json");

  assert.equal((await resolvePaymentIssueActionAttempt(payload, null, storage)).idempotencyKey,
    first.idempotencyKey);
  await assert.rejects(resolvePaymentIssueActionAttempt(secondPayload, null, storage),
    /未决批量动作无法核实/);
  assert.equal(storage.getItem(secondKey), "{broken-json");
});

test("blocks conflicting legacy and scoped keys for the same action", async () => {
  const storage = memoryStorage();
  const attempt = await resolvePaymentIssueActionAttempt(payload, null, storage);
  storage.setItem("payment-issue-action-attempt:v1", JSON.stringify({
    digest: attempt.digest,
    idempotencyKey: "00000000-0000-4000-8000-000000000001"
  }));
  await assert.rejects(resolvePaymentIssueActionAttempt(payload, null, storage),
    /未决批量动作无法核实/);
});

test("marks both legacy and scoped batch keys after a confirmed action cannot remove them", async () => {
  const storage = memoryStorage();
  const attempt = await resolvePaymentIssueActionAttempt(payload, null, storage);
  const scopedKey = [...storage.values.keys()][0];
  storage.setItem("payment-issue-action-attempt:v1", storage.getItem(scopedKey));
  storage.removeItem = () => { throw new Error("blocked"); };
  assert.throws(() => clearPaymentIssueActionAttempt(attempt, storage),
    /未决批量动作无法核实/);
  assert.equal(JSON.parse(storage.getItem(scopedKey)).confirmed, true);
  assert.equal(JSON.parse(storage.getItem("payment-issue-action-attempt:v1")).confirmed, true);
  await assert.rejects(resolvePaymentIssueActionAttempt(payload, null, storage),
    /未决批量动作无法核实/);
});

test("fails closed when the confirmed marker cannot be persisted", async () => {
  const storage = memoryStorage();
  const attempt = await resolvePaymentIssueActionAttempt(payload, null, storage);
  storage.removeItem = () => {};
  storage.setItem = () => {};
  assert.throws(() => clearPaymentIssueActionAttempt(attempt, storage),
    /未决批量动作无法核实/);
  await assert.rejects(resolvePaymentIssueActionAttempt(payload,
    { ...attempt, confirmed: true }, storage), /未决批量动作无法核实/);
});

test("blocks a new batch key when browser storage cannot be read", async () => {
  const unavailableStorage = {
    getItem: () => { throw new Error("blocked"); },
    setItem: () => { throw new Error("blocked"); },
    removeItem: () => { throw new Error("blocked"); }
  };
  await assert.rejects(resolvePaymentIssueActionAttempt(payload, null, unavailableStorage),
    /未决批量动作无法核实/);
  await assert.rejects(resolvePaymentIssueActionAttempt(payload, null, null),
    /未决批量动作无法核实/);
  const persisted = await resolvePaymentIssueActionAttempt(payload, null, memoryStorage());
  assert.equal((await resolvePaymentIssueActionAttempt(payload, persisted, unavailableStorage)).idempotencyKey,
    persisted.idempotencyKey);
});

test("blocks a new batch key when digest or storage write fails", async () => {
  const storage = memoryStorage();
  const cryptoProvider = { randomUUID: () => "local-key-001", subtle: {
    digest: async () => { throw new Error("unavailable"); }
  } };
  await assert.rejects(resolvePaymentIssueActionAttempt(payload, null, storage, cryptoProvider),
    /未决批量动作无法核实/);
  storage.setItem = () => { throw new Error("quota exceeded"); };
  await assert.rejects(resolvePaymentIssueActionAttempt(payload, null, storage),
    /未决批量动作无法核实/);
});

test("blocks malformed saved attempts instead of generating a new key", async () => {
  const storage = memoryStorage();
  const first = await resolvePaymentIssueActionAttempt(payload, null, storage);
  const storageKey = [...storage.values.keys()][0];
  storage.setItem(storageKey, JSON.stringify({
    digest: first.digest,
    idempotencyKey: "invalid\nkey"
  }));
  await assert.rejects(resolvePaymentIssueActionAttempt(payload, null, storage),
    /未决批量动作无法核实/);
  assert.equal(JSON.parse(storage.values.get(storageKey)).idempotencyKey, "invalid\nkey");
});

test("does not send a new batch action when storage silently ignores the write", async () => {
  const storage = memoryStorage();
  storage.setItem = () => {};
  await assert.rejects(resolvePaymentIssueActionAttempt(payload, null, storage),
    /未决批量动作无法核实/);
  assert.equal(storage.values.size, 0);
});

test("marks a confirmed batch action when storage silently retains its key", async () => {
  const storage = memoryStorage();
  const attempt = await resolvePaymentIssueActionAttempt(payload, null, storage);
  storage.removeItem = () => {};
  assert.throws(() => clearPaymentIssueActionAttempt(attempt, storage),
    /未决批量动作无法核实/);
  const saved = JSON.parse([...storage.values.values()][0]);
  assert.equal(saved.confirmed, true);
  await assert.rejects(resolvePaymentIssueActionAttempt(payload, null, storage),
    /未决批量动作无法核实/);
  const other = await resolvePaymentIssueActionAttempt({ ...payload, remark: "另一笔动作" }, null, storage);
  assert.notEqual(other.idempotencyKey, attempt.idempotencyKey);
});

test("does not clear a replaced batch action key", async () => {
  const storage = memoryStorage();
  const attempt = await resolvePaymentIssueActionAttempt(payload, null, storage);
  const storageKey = [...storage.values.keys()][0];
  const replaced = JSON.stringify({ digest: attempt.digest,
    idempotencyKey: "00000000-0000-4000-8000-000000000001" });
  storage.setItem(storageKey, replaced);
  assert.throws(() => clearPaymentIssueActionAttempt(attempt, storage),
    /未决批量动作无法核实/);
  assert.equal(storage.getItem(storageKey), replaced);
});

test("rejects a pending batch UUID that is not a string", async () => {
  const storage = memoryStorage();
  const first = await resolvePaymentIssueActionAttempt(payload, null, storage);
  const storageKey = [...storage.values.keys()][0];
  storage.setItem(storageKey, JSON.stringify({
    digest: first.digest,
    idempotencyKey: [first.idempotencyKey]
  }));
  await assert.rejects(resolvePaymentIssueActionAttempt(payload, null, storage),
    /未决批量动作无法核实/);
});

test("reports a missing secure random source as an unverifiable batch attempt", async () => {
  const storage = memoryStorage();
  await assert.rejects(resolvePaymentIssueActionAttempt(payload, null, storage, {
    subtle: globalThis.crypto.subtle
  }), /未决批量动作无法核实/);
  assert.equal(storage.values.size, 0);
});

test("rejects malformed digest output and non-string generated keys", async () => {
  const storage = memoryStorage();
  await assert.rejects(resolvePaymentIssueActionAttempt(payload, null, storage, {
    subtle: { digest: async () => new Uint8Array(4).buffer },
    randomUUID: () => "00000000-0000-4000-8000-000000000000"
  }), /未决批量动作无法核实/);
  await assert.rejects(resolvePaymentIssueActionAttempt(payload, null, storage, {
    subtle: globalThis.crypto.subtle,
    randomUUID: () => ["00000000-0000-4000-8000-000000000000"]
  }), /未决批量动作无法核实/);
  assert.equal(storage.values.size, 0);
});
