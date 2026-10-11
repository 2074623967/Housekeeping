const STORAGE_KEY = "payment-issue-action-attempt:v1";
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const DIGEST_PATTERN = /^[0-9a-f]{64}$/;

export class UnverifiablePaymentIssueActionAttemptError extends Error {
  constructor() {
    super("未决批量动作无法核实，请先核对处理记录，勿重复提交。");
  }
}

function availableStorage() {
  try {
    return globalThis.sessionStorage;
  } catch {
    return null;
  }
}

function requestFingerprint(payload) {
  return JSON.stringify({ ...payload, issueNos: [...payload.issueNos].sort() });
}

async function fingerprintDigest(fingerprint, cryptoProvider) {
  if (!cryptoProvider?.subtle) return null;
  try {
    const digest = await cryptoProvider.subtle.digest("SHA-256", new TextEncoder().encode(fingerprint));
    const bytes = new Uint8Array(digest);
    if (bytes.length !== 32) return null;
    return Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
  } catch {
    return null;
  }
}

function generateKey(cryptoProvider) {
  if (cryptoProvider?.randomUUID) return cryptoProvider.randomUUID();
  const bytes = cryptoProvider.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function scopedStorageKey(digest) {
  return `${STORAGE_KEY}.${digest}`;
}

function readSavedAttempt(storage, key) {
  let rawSaved;
  try {
    rawSaved = storage.getItem(key);
  } catch {
    throw new UnverifiablePaymentIssueActionAttemptError();
  }
  if (rawSaved === null) return null;
  try {
    const saved = JSON.parse(rawSaved);
    if (saved && typeof saved.digest === "string" && DIGEST_PATTERN.test(saved.digest)
        && typeof saved.idempotencyKey === "string" && UUID_PATTERN.test(saved.idempotencyKey)) {
      return saved;
    }
  } catch {}
  throw new UnverifiablePaymentIssueActionAttemptError();
}

export async function resolvePaymentIssueActionAttempt(
  payload,
  currentAttempt = null,
  storage = availableStorage(),
  cryptoProvider = globalThis.crypto
) {
  const fingerprint = requestFingerprint(payload);
  if (currentAttempt?.fingerprint === fingerprint) return currentAttempt;
  if (!storage) throw new UnverifiablePaymentIssueActionAttemptError();
  const digest = await fingerprintDigest(fingerprint, cryptoProvider);
  if (!digest) throw new UnverifiablePaymentIssueActionAttemptError();
  const legacy = readSavedAttempt(storage, STORAGE_KEY);
  const saved = readSavedAttempt(storage, scopedStorageKey(digest));
  if (saved && saved.digest !== digest) throw new UnverifiablePaymentIssueActionAttemptError();
  if (legacy?.digest === digest) {
    if (saved && saved.idempotencyKey !== legacy.idempotencyKey) {
      throw new UnverifiablePaymentIssueActionAttemptError();
    }
    return { fingerprint, digest, idempotencyKey: legacy.idempotencyKey };
  }
  if (saved) {
    return { fingerprint, digest, idempotencyKey: saved.idempotencyKey };
  }
  try {
    const attempt = { fingerprint, digest, idempotencyKey: generateKey(cryptoProvider) };
    if (typeof attempt.idempotencyKey !== "string" || !UUID_PATTERN.test(attempt.idempotencyKey)) {
      throw new Error("invalid batch action key");
    }
    const savedAttempt = JSON.stringify({ digest, idempotencyKey: attempt.idempotencyKey });
    storage.setItem(scopedStorageKey(digest), savedAttempt);
    if (storage.getItem(scopedStorageKey(digest)) !== savedAttempt) {
      throw new Error("pending batch action was not saved");
    }
    return attempt;
  } catch {
    throw new UnverifiablePaymentIssueActionAttemptError();
  }
}

export function clearPaymentIssueActionAttempt(attempt, storage = availableStorage()) {
  if (!attempt || !storage) return;
  try {
    for (const key of [STORAGE_KEY, scopedStorageKey(attempt.digest)]) {
      const saved = readSavedAttempt(storage, key);
      if (saved?.digest === attempt.digest && saved.idempotencyKey === attempt.idempotencyKey) {
        storage.removeItem(key);
      }
    }
  } catch {}
}
