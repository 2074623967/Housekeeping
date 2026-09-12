const REFUND_ATTEMPT_STATUS_TYPES = new Set(["success", "warn", "danger", "info"]);

export function refundAttemptStatusType(value) {
  const normalized = typeof value === "string" ? value.trim().toLowerCase() : "";
  return REFUND_ATTEMPT_STATUS_TYPES.has(normalized) ? normalized : "info";
}
