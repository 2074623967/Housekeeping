export function canQueryPaymentChannel(status, latestAttemptStatus, latestAttemptCompletedAt, channelTransactionNo, channel) {
  if (["SUCCESS", "支付成功"].includes(status)) {
    return true;
  }
  const normalizedAttemptStatus = typeof latestAttemptStatus === "string"
    ? latestAttemptStatus.trim()
    : "";
  if (["CLOSED", "已关闭"].includes(status) && !normalizedAttemptStatus && !channelTransactionNo) {
    return true;
  }
  return ["WAIT_CALLBACK", "支付中", "CLOSED", "已关闭"].includes(status)
    && Boolean(normalizedAttemptStatus && normalizedAttemptStatus !== "-")
    && !["成功", "支付成功", "失败", "支付失败", "SUCCESS", "FAIL", "FAILED"]
      .includes(normalizedAttemptStatus.toUpperCase())
    && typeof channel === "string" && Boolean(channel.trim())
    && latestAttemptCompletedAt == null;
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
