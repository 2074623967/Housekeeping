const STORAGE_KEY = "hsp.paymentSubmitAttempt.v1";
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const DIGEST_PATTERN = /^[0-9a-f]{64}$/;

export class UnverifiablePaymentSubmitAttemptError extends Error {
  constructor(message = "本次预付单的未决支付记录无法核实，请勿重复付款，联系运营核对原支付结果。") {
    super(message);
  }
}

function availableStorage() {
  try {
    return globalThis.sessionStorage;
  } catch {
    return null;
  }
}

export function generatePaymentSubmitKey(cryptoProvider = globalThis.crypto) {
  if (cryptoProvider?.randomUUID) return cryptoProvider.randomUUID();
  const bytes = cryptoProvider.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

async function digestText(text, cryptoProvider) {
  try {
    const bytes = await cryptoProvider.subtle.digest("SHA-256", new TextEncoder().encode(text));
    return Array.from(new Uint8Array(bytes), (byte) => byte.toString(16).padStart(2, "0")).join("");
  } catch {
    return null;
  }
}

function storageKey(scopeHash) {
  return `${STORAGE_KEY}.${scopeHash}`;
}

function readSavedAttempt(storage, scopeHash) {
  let rawAttempt;
  try {
    rawAttempt = storage.getItem(storageKey(scopeHash));
  } catch {
    throw new UnverifiablePaymentSubmitAttemptError();
  }
  if (rawAttempt === null) return null;
  try {
    const saved = JSON.parse(rawAttempt);
    if (saved && typeof saved.digest === "string" && DIGEST_PATTERN.test(saved.digest)
        && saved.scopeHash === scopeHash
        && typeof saved.idempotencyKey === "string" && UUID_PATTERN.test(saved.idempotencyKey)
        && typeof saved.paymentMethod === "string" && saved.paymentMethod.trim()
        && (saved.confirmed === undefined || saved.confirmed === true)) {
      return saved;
    }
  } catch {}
  throw new UnverifiablePaymentSubmitAttemptError();
}

export async function readPaymentSubmitAttemptForPrepay(
  prepayOrderNo,
  storage = availableStorage(),
  cryptoProvider = globalThis.crypto
) {
  if (!prepayOrderNo) return null;
  if (!storage) throw new UnverifiablePaymentSubmitAttemptError();
  const scopeHash = await digestText(prepayOrderNo, cryptoProvider);
  if (!scopeHash) throw new UnverifiablePaymentSubmitAttemptError();
  return readSavedAttempt(storage, scopeHash);
}

export async function resolvePaymentSubmitAttempt(
  payload,
  currentKey,
  storage = availableStorage(),
  cryptoProvider = globalThis.crypto
) {
  if (!storage) throw new UnverifiablePaymentSubmitAttemptError();
  const [digest, scopeHash] = await Promise.all([
    digestText(JSON.stringify(payload), cryptoProvider),
    digestText(payload.prepayOrderNo, cryptoProvider)
  ]);
  if (!digest || !scopeHash) throw new UnverifiablePaymentSubmitAttemptError();
  const saved = readSavedAttempt(storage, scopeHash);
  if (saved) {
    if (saved.confirmed) throw new UnverifiablePaymentSubmitAttemptError();
    if (saved.digest !== digest) {
      throw new Error("本次预付单存在待核实的支付提交，请沿用原方式查单，不要更换渠道或请求键。");
    }
    return saved;
  }
  try {
    const attempt = {
      digest,
      scopeHash,
      paymentMethod: payload.paymentMethod,
      idempotencyKey: typeof currentKey === "string" && UUID_PATTERN.test(currentKey)
        ? currentKey : generatePaymentSubmitKey(cryptoProvider)
    };
    if (typeof attempt.idempotencyKey !== "string" || !UUID_PATTERN.test(attempt.idempotencyKey)) {
      throw new Error("invalid payment submit key");
    }
    const saved = JSON.stringify(attempt);
    storage.setItem(storageKey(scopeHash), saved);
    if (storage.getItem(storageKey(scopeHash)) !== saved) {
      throw new Error("pending payment submit was not saved");
    }
    return attempt;
  } catch {
    throw new UnverifiablePaymentSubmitAttemptError();
  }
}

export function clearPaymentSubmitAttempt(attempt, storage = availableStorage()) {
  try {
    if (!attempt?.scopeHash || !storage) throw new Error("pending payment submit unavailable");
    const saved = readSavedAttempt(storage, attempt.scopeHash);
    if (!saved) return;
    if (saved.idempotencyKey !== attempt.idempotencyKey || saved.digest !== attempt.digest
        || saved.confirmed) {
      throw new Error("pending payment submit changed");
    }
    const key = storageKey(attempt.scopeHash);
    const rawSaved = storage.getItem(key);
    try {
      storage.removeItem(key);
    } catch {}
    const remaining = storage.getItem(key);
    if (remaining === null) return;
    const retained = JSON.parse(remaining);
    if (remaining !== rawSaved || retained.digest !== saved.digest
        || retained.scopeHash !== saved.scopeHash || retained.paymentMethod !== saved.paymentMethod
        || retained.idempotencyKey !== saved.idempotencyKey || retained.confirmed !== undefined) {
      throw new Error("pending payment submit changed during cleanup");
    }
    const confirmed = JSON.stringify({ ...saved, confirmed: true });
    storage.setItem(key, confirmed);
    if (storage.getItem(key) !== confirmed) throw new Error("confirmed payment submit not saved");
    throw new Error("confirmed payment submit retained for review");
  } catch {
    throw new UnverifiablePaymentSubmitAttemptError(
      "支付响应已返回，但本机未决记录未能清理；请刷新核对支付结果，勿重复付款。"
    );
  }
}

export async function discardPaymentSubmitAttempt(
  prepayOrderNo,
  storage = availableStorage(),
  cryptoProvider = globalThis.crypto
) {
  if (!prepayOrderNo) return;
  if (!storage) throw new UnverifiablePaymentSubmitAttemptError();
  const scopeHash = await digestText(prepayOrderNo, cryptoProvider);
  if (!scopeHash) throw new UnverifiablePaymentSubmitAttemptError();
  try {
    if (readSavedAttempt(storage, scopeHash)?.confirmed) {
      throw new Error("confirmed payment submit cannot be reset");
    }
    storage.removeItem(storageKey(scopeHash));
    if (storage.getItem(storageKey(scopeHash)) !== null) {
      throw new Error("pending payment submit was not removed");
    }
  } catch {
    throw new UnverifiablePaymentSubmitAttemptError();
  }
}
