export function canQueryPaymentChannel(status, latestAttemptStatus) {
  if (["SUCCESS", "CLOSED"].includes(status)) {
    return true;
  }
  const normalizedAttemptStatus = typeof latestAttemptStatus === "string"
    ? latestAttemptStatus.trim()
    : "";
  return status === "WAIT_CALLBACK" && Boolean(normalizedAttemptStatus && normalizedAttemptStatus !== "-");
}

export function canClosePaymentLocally(status, channelTransactionNo, latestAttemptStatus) {
  const hasChannelTransaction = typeof channelTransactionNo === "string"
    ? Boolean(channelTransactionNo.trim())
    : Boolean(channelTransactionNo);
  const hasChannelAttempt = typeof latestAttemptStatus === "string"
    ? Boolean(latestAttemptStatus.trim() && latestAttemptStatus.trim() !== "-")
    : Boolean(latestAttemptStatus);
  return ["PREPAY_CREATED", "RISK_REVIEW", "RISK_BLOCKED", "RISK_REJECTED"].includes(status)
    && !hasChannelTransaction
    && !hasChannelAttempt;
}
