export function generateRefundApplicationIdempotencyKey() {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID();
  }
  return `REFUND-APPLY-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function resolveRefundApplicationAttempt(
  requestPayload,
  currentAttempt = {},
  generateKey = generateRefundApplicationIdempotencyKey
) {
  const requestFingerprint = JSON.stringify(requestPayload);
  if (currentAttempt.idempotencyKey
    && currentAttempt.requestFingerprint === requestFingerprint) {
    return currentAttempt;
  }
  return {
    idempotencyKey: generateKey(),
    requestFingerprint
  };
}
