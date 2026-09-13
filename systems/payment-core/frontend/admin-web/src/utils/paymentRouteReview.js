export function isRouteRuleCollisionAcknowledged(reviewRequestNo, acknowledgedRequestNo) {
  return typeof reviewRequestNo === "string"
    && reviewRequestNo.length > 0
    && reviewRequestNo === acknowledgedRequestNo;
}

export function buildRouteRuleReviewActionPayload({
  reviewer,
  reviewComment,
  approved,
  reviewRequestNo,
  acknowledgedRequestNo
}) {
  return {
    reviewer,
    reviewComment,
    priorityCollisionAcknowledged: Boolean(approved)
      && isRouteRuleCollisionAcknowledged(reviewRequestNo, acknowledgedRequestNo)
  };
}
