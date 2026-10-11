function normalizeEntityId(value) {
  return typeof value === "string" ? value.trim() : "";
}

function normalizeAmount(value) {
  if (typeof value !== "string" && typeof value !== "number") {
    return "";
  }
  const normalized = String(value).replace(/[,¥]/g, "").trim();
  if (!/^(?:\d{1,16}(?:\.\d{1,2})?|\.\d{1,2})$/.test(normalized)) {
    return "";
  }
  const [integerPart, fractionPart] = normalized.split(".");
  const normalizedInteger = (integerPart || "").replace(/^0+/, "") || "0";
  return `${normalizedInteger}.${(fractionPart || "").padEnd(2, "0")}`;
}

function matchesOptionalText(snapshotValue, entityValue) {
  return snapshotValue == null
    || normalizeEntityId(snapshotValue) === normalizeEntityId(entityValue);
}

function matchesPreservedText(snapshotValue, entityValue) {
  return snapshotValue == null
    || entityValue == null
    || normalizeEntityId(snapshotValue) === normalizeEntityId(entityValue);
}

function matchesOptionalTimestamp(snapshotValue, entityValue) {
  if (snapshotValue == null || snapshotValue === "-") {
    return true;
  }
  return normalizeEntityId(snapshotValue).replace("T", " ")
    === normalizeEntityId(entityValue).replace("T", " ");
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
    && typeof snapshot.businessChainId === "string"
    && snapshot.businessChainId.trim() === `CHAIN-${normalizeEntityId(snapshot.paymentOrderId)}`
    && normalizeEntityId(entity?.businessChainId) === snapshot.businessChainId.trim()
    && (!normalizeEntityId(snapshot.orderNo)
      || normalizeEntityId(entity?.orderNo) === normalizeEntityId(snapshot.orderNo))
    && (snapshot.amount == null
      || (normalizeAmount(snapshot.amount)
        && normalizeAmount(entity?.amount)
        && normalizeAmount(snapshot.amount) === normalizeAmount(entity.amount)))
    && matchesOptionalTimestamp(snapshot.createdAt, entity?.createdAt)
    && matchesOptionalTimestamp(snapshot.successAt, entity?.successAt)
    && matchesOptionalTimestamp(snapshot.latestAttemptCompletedAt, entity?.latestAttemptCompletedAt)
    && matchesOptionalText(snapshot.channel, entity?.channel)
    && matchesOptionalText(snapshot.channelTransactionNo, entity?.channelTransactionNo)
    && matchesPreservedText(snapshot.paymentMethod, entity?.paymentMethod)
    && matchesOptionalText(snapshot.refundOrderId, entity?.refundOrderId)
    && matchesPreservedText(snapshot.billNo, entity?.billNo)
    && matchesPreservedText(snapshot.customerName, entity?.customerName)
    && matchesOptionalText(snapshot.payScene, entity?.payScene)
  );
}
