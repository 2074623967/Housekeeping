function normalizeEntityId(value) {
  return typeof value === "string" ? value.trim() : "";
}

export function matchesPrepayOrder(prepayOrderNo, entity) {
  const expectedPrepayOrderNo = normalizeEntityId(prepayOrderNo);
  return Boolean(
    expectedPrepayOrderNo
    && entity
    && !Array.isArray(entity)
    && normalizeEntityId(entity.prepayOrderNo) === expectedPrepayOrderNo
  );
}

export function matchesPaymentOrder(paymentOrderId, entity) {
  const expectedPaymentOrderId = normalizeEntityId(paymentOrderId);
  return Boolean(
    expectedPaymentOrderId
    && entity
    && !Array.isArray(entity)
    && normalizeEntityId(entity.paymentOrderId) === expectedPaymentOrderId
  );
}

export function matchesPaymentSnapshot(snapshot, entity) {
  return Boolean(
    snapshot
    && matchesPrepayOrder(snapshot.prepayOrderNo, entity)
    && matchesPaymentOrder(snapshot.paymentOrderId, entity)
  );
}
