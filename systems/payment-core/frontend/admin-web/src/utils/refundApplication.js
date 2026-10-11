const STORAGE_KEY = "refund-application-attempt:v1";
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const DIGEST_PATTERN = /^[0-9a-f]{64}$/;

export class UnverifiableRefundApplicationAttemptError extends Error {
  constructor() {
    super("未决退款申请无法核实，请先核对退款单与支付单，勿重新生成申请键。");
  }
}

function availableStorage() {
  try {
    return globalThis.sessionStorage;
  } catch {
    return null;
  }
}

async function requestDigest(payload, cryptoProvider) {
  if (!cryptoProvider?.subtle) throw new UnverifiableRefundApplicationAttemptError();
  try {
    const fingerprint = JSON.stringify(payload);
    const digest = new Uint8Array(await cryptoProvider.subtle.digest(
      "SHA-256", new TextEncoder().encode(fingerprint)
    ));
    if (digest.length !== 32) throw new Error("invalid digest");
    return Array.from(digest, (byte) => byte.toString(16).padStart(2, "0")).join("");
  } catch {
    throw new UnverifiableRefundApplicationAttemptError();
  }
}

function generateRefundApplicationIdempotencyKey(cryptoProvider) {
  if (cryptoProvider?.randomUUID) return cryptoProvider.randomUUID();
  const bytes = cryptoProvider.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

export async function resolveRefundApplicationAttempt(
  requestPayload,
  storage = availableStorage(),
  cryptoProvider = globalThis.crypto
) {
  if (!storage) throw new UnverifiableRefundApplicationAttemptError();
  const digest = await requestDigest(requestPayload, cryptoProvider);
  const storageKey = `${STORAGE_KEY}.${digest}`;
  try {
    const rawSaved = storage.getItem(storageKey);
    if (rawSaved !== null) {
      const saved = JSON.parse(rawSaved);
      if (!saved || typeof saved.digest !== "string" || saved.digest !== digest
          || typeof saved.idempotencyKey !== "string" || !UUID_PATTERN.test(saved.idempotencyKey)
          || saved.confirmed !== undefined) {
        throw new Error("invalid pending refund application");
      }
      return { digest, idempotencyKey: saved.idempotencyKey };
    }
    const idempotencyKey = generateRefundApplicationIdempotencyKey(cryptoProvider);
    if (!UUID_PATTERN.test(idempotencyKey)) throw new Error("invalid refund application key");
    const saved = JSON.stringify({ digest, idempotencyKey });
    storage.setItem(storageKey, saved);
    if (storage.getItem(storageKey) !== saved) throw new Error("pending refund application not saved");
    return { digest, idempotencyKey };
  } catch {
    throw new UnverifiableRefundApplicationAttemptError();
  }
}

export function clearRefundApplicationAttempt(attempt, storage = availableStorage()) {
  if (!attempt || !storage) throw new UnverifiableRefundApplicationAttemptError();
  try {
    const storageKey = `${STORAGE_KEY}.${attempt.digest}`;
    const rawSaved = storage.getItem(storageKey);
    const saved = JSON.parse(rawSaved);
    if (saved?.digest !== attempt.digest || saved.idempotencyKey !== attempt.idempotencyKey
        || saved.confirmed !== undefined) {
      throw new Error("refund application key changed before cleanup");
    }
    try {
      storage.removeItem(storageKey);
    } catch {}
    const remaining = storage.getItem(storageKey);
    if (remaining === null) return;
    if (remaining !== rawSaved) throw new Error("refund application key changed during cleanup");
    const confirmed = JSON.stringify({ ...saved, confirmed: true });
    storage.setItem(storageKey, confirmed);
    if (storage.getItem(storageKey) !== confirmed) {
      throw new Error("confirmed refund application not saved");
    }
  } catch {
    throw new UnverifiableRefundApplicationAttemptError();
  }
  throw new UnverifiableRefundApplicationAttemptError();
}
