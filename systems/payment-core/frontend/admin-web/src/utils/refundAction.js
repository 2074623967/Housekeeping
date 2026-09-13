export function canRunRefundAction(status, actionName) {
  return (actionName === "query" && status === "PROCESSING")
    || (actionName === "approve" && status === "REVIEWING")
    || (actionName === "retry" && status === "FAIL");
}
