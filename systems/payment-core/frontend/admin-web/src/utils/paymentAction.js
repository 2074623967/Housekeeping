export function canQueryPaymentChannel(status, latestAttemptStatus) {
  if (["SUCCESS", "CLOSED"].includes(status)) {
    return true;
  }
  const normalizedAttemptStatus = typeof latestAttemptStatus === "string"
    ? latestAttemptStatus.trim()
    : "";
  return status === "WAIT_CALLBACK" && Boolean(normalizedAttemptStatus && normalizedAttemptStatus !== "-");
}
