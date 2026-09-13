function normalizePreviewRuleCodes(review = {}) {
  const ruleCodes = review.previewPriorityCollisionRuleCodes;
  if (!Array.isArray(ruleCodes)
    || ruleCodes.some((ruleCode) => typeof ruleCode !== "string" || !ruleCode.trim())) {
    return [];
  }
  const normalizedRuleCodes = ruleCodes.map((ruleCode) => ruleCode.trim());
  return new Set(normalizedRuleCodes).size === normalizedRuleCodes.length
    ? normalizedRuleCodes
    : [];
}

export function routeRuleBlockingConflicts(review = {}) {
  if (!Array.isArray(review.previewBlockingConflicts)) {
    return [];
  }
  return review.previewBlockingConflicts.filter((conflict) => (
    conflict
      && conflict.severity === "BLOCKER"
      && typeof conflict.message === "string"
      && conflict.message.trim()
  ));
}

export function hasInvalidRouteRuleBlockingPreview(review = {}) {
  const conflicts = review.previewBlockingConflicts;
  if (conflicts == null) {
    return false;
  }
  return !Array.isArray(conflicts) || conflicts.some((conflict) => (
    !conflict
      || conflict.severity !== "BLOCKER"
      || typeof conflict.message !== "string"
      || !conflict.message.trim()
  ));
}

export function canApproveRouteRuleReview(review = {}) {
  return !hasInvalidRouteRuleBlockingPreview(review)
    && routeRuleBlockingConflicts(review).length === 0;
}

export function formatRouteRuleBlockingConflict(review = {}, conflict = {}) {
  const counterpartRuleCode = [conflict.ruleCode, conflict.conflictingRuleCode]
    .find((ruleCode) => typeof ruleCode === "string" && ruleCode && ruleCode !== review.ruleCode);
  const conflictType = typeof conflict.conflictType === "string" && conflict.conflictType
    ? conflict.conflictType
    : "BLOCKER";
  return `${conflictType} / ${counterpartRuleCode || "候选规则"}：${conflict.message.trim()}`;
}

export function buildRouteRuleCollisionAcknowledgement(review = {}) {
  const ruleCodes = normalizePreviewRuleCodes(review);
  if (!canApproveRouteRuleReview(review)
    || typeof review.requestNo !== "string" || !review.requestNo || !ruleCodes.length) {
    return null;
  }
  return { requestNo: review.requestNo, ruleCodes };
}

export function isRouteRuleCollisionAcknowledged(review = {}, acknowledgement = null) {
  const previewRuleCodes = normalizePreviewRuleCodes(review);
  return Boolean(acknowledgement)
    && canApproveRouteRuleReview(review)
    && review.requestNo === acknowledgement.requestNo
    && previewRuleCodes.length > 0
    && previewRuleCodes.length === acknowledgement.ruleCodes?.length
    && previewRuleCodes.every((ruleCode, index) => ruleCode === acknowledgement.ruleCodes[index]);
}

export function buildRouteRuleReviewActionPayload({
  reviewer,
  reviewComment,
  approved,
  review,
  acknowledgement
}) {
  const priorityCollisionAcknowledged = Boolean(approved)
    && isRouteRuleCollisionAcknowledged(review, acknowledgement);
  return {
    reviewer,
    reviewComment,
    priorityCollisionAcknowledged,
    acknowledgedPriorityCollisionRuleCodes: priorityCollisionAcknowledged
      ? normalizePreviewRuleCodes(review)
      : []
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
