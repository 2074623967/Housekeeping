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

export function formatRouteRuleConflictAudit(review = {}) {
  if (review.priorityCollisionDetected === true) {
    let ruleCodes = [];
    if (typeof review.priorityCollisionRuleCodesJson === "string"
      && review.priorityCollisionRuleCodesJson.trim()) {
      try {
        const parsed = JSON.parse(review.priorityCollisionRuleCodesJson);
        if (!Array.isArray(parsed) || parsed.some((item) => typeof item !== "string" || !item.trim())) {
          throw new TypeError("invalid route collision target snapshot");
        }
        ruleCodes = parsed.map((item) => item.trim());
      } catch {
        const status = review.priorityCollisionAcknowledged === true ? "已确认" : "未确认";
        return `同优先级风险${status}（冲突对象快照异常）`;
      }
    }
    const targetSummary = ruleCodes.length
      ? `：${ruleCodes.join(" / ")}`
      : "（冲突对象未记录）";
    return review.priorityCollisionAcknowledged === true
      ? `同优先级风险已确认${targetSummary}`
      : `同优先级风险未确认${targetSummary}`;
  }
  if (review.priorityCollisionDetected === false) {
    return "未发现同优先级潜在冲突";
  }
  return "冲突复验未执行";
}
