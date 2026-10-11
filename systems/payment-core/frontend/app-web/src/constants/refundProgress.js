export const REFUND_PROGRESS_MAX_POLL_ATTEMPTS = 5;

export function isUnknownRefundSubmission(detail) {
  return detail?.status === "PROCESSING"
    && detail.attempts?.[0]?.attemptStatus === "UNKNOWN";
}

export function formatRefundProgressTime(value) {
  if (typeof value !== "string" || !value.trim()) {
    return "-";
  }
  return value.trim().replace("T", " ");
}

function normalizeAmount(value) {
  const normalized = String(value == null ? "" : value).replace(/[,¥]/g, "").trim();
  if (!/^(?:\d{1,16}(?:\.\d{1,2})?|\.\d{1,2})$/.test(normalized)) {
    return "";
  }
  const [integerPart, fractionPart] = normalized.split(".");
  const normalizedInteger = (integerPart || "").replace(/^0+/, "") || "0";
  const normalizedFraction = (fractionPart || "").padEnd(2, "0");
  return normalizedInteger === "0" && normalizedFraction === "00"
    ? ""
    : `${normalizedInteger}.${normalizedFraction}`;
}

function normalizeIdentifier(value) {
  return typeof value === "string" ? value.trim() : "";
}

function matchesOptionalText(snapshotValue, detailValue) {
  return snapshotValue == null
    || normalizeIdentifier(snapshotValue) === normalizeIdentifier(detailValue);
}

function matchesPreservedText(snapshotValue, detailValue) {
  return snapshotValue == null
    || detailValue == null
    || normalizeIdentifier(snapshotValue) === normalizeIdentifier(detailValue);
}

function matchesOptionalTimestamp(snapshotValue, detailValue) {
  if (snapshotValue == null || snapshotValue === "-") {
    return true;
  }
  return normalizeIdentifier(snapshotValue).replace("T", " ")
    === normalizeIdentifier(detailValue).replace("T", " ");
}

export function matchesRefundProgressDetail(snapshot, detail) {
  const snapshotRefundOrderId = normalizeIdentifier(snapshot?.refundOrderId);
  const snapshotPaymentOrderId = normalizeIdentifier(snapshot?.paymentOrderId);
  const snapshotBusinessChainId = normalizeIdentifier(snapshot?.businessChainId);
  const snapshotOrderNo = normalizeIdentifier(snapshot?.orderNo);
  return Boolean(snapshot
    && detail
    && normalizeIdentifier(detail.refundOrderId) === snapshotRefundOrderId
    && normalizeIdentifier(detail.paymentOrderId) === snapshotPaymentOrderId
    && snapshotPaymentOrderId
    && snapshotBusinessChainId === `CHAIN-${snapshotPaymentOrderId}`
    && normalizeIdentifier(detail.businessChainId) === snapshotBusinessChainId
    && (!snapshotOrderNo || normalizeIdentifier(detail.orderNo) === snapshotOrderNo)
    && normalizeAmount(detail.refundAmount) === normalizeAmount(snapshot.refundAmount)
    && normalizeAmount(detail.paidAmount) === normalizeAmount(snapshot.paidAmount)
    && matchesOptionalTimestamp(snapshot.appliedAt, detail.appliedAt)
    && matchesOptionalTimestamp(snapshot.channelSubmittedAt, detail.channelSubmittedAt)
    && matchesOptionalTimestamp(snapshot.successAt, detail.successAt)
    && matchesOptionalTimestamp(snapshot.terminalAt, detail.terminalAt)
    && matchesOptionalText(snapshot.refundMethod, detail.refundMethod)
    && matchesOptionalText(snapshot.refundReason, detail.refundReason)
    && matchesPreservedText(snapshot.paymentMethod, detail.paymentMethod)
    && matchesPreservedText(snapshot.customerName, detail.customerName)
    && matchesOptionalText(snapshot.payScene, detail.payScene)
    && matchesOptionalText(snapshot.channelCode, detail.channelCode)
    && matchesOptionalText(snapshot.channelRefundNo, detail.channelRefundNo));
}

export function shouldRetryRefundProgress({ automatic, requestMatches, isTerminal, attempts }) {
  return automatic
    && requestMatches
    && !isTerminal
    && Number.isInteger(attempts)
    && attempts < REFUND_PROGRESS_MAX_POLL_ATTEMPTS;
}
