import assert from "node:assert/strict";
import test from "node:test";
import {
  buildRouteRuleCollisionAcknowledgement,
  buildRouteRuleReviewActionPayload,
  canApproveRouteRuleReview,
  formatRouteRulePreviewConflict,
  formatRouteRuleConflictAudit,
  hasInvalidRouteRuleBlockingPreview,
  hasInvalidRouteRuleWarningPreview,
  isRouteRuleCollisionAcknowledged,
  routeRuleBlockingConflicts,
  routeRuleWarningConflicts
} from "./paymentRouteReview.js";

test("binds route collision acknowledgement to the request and exact preview", () => {
  const review = {
    requestNo: "ROUTE-REVIEW-001",
    previewPriorityCollisionRuleCodes: ["RULE_A_APP", "RULE_Z_APP"]
  };
  const acknowledgement = buildRouteRuleCollisionAcknowledgement(review);

  assert.deepEqual(acknowledgement, {
    requestNo: "ROUTE-REVIEW-001",
    ruleCodes: ["RULE_A_APP", "RULE_Z_APP"]
  });
  assert.equal(isRouteRuleCollisionAcknowledged(review, acknowledgement), true);
  assert.equal(isRouteRuleCollisionAcknowledged({ ...review, requestNo: "ROUTE-REVIEW-002" }, acknowledgement), false);
  assert.equal(isRouteRuleCollisionAcknowledged({
    ...review,
    previewPriorityCollisionRuleCodes: ["RULE_A_APP", "RULE_NEW_APP", "RULE_Z_APP"]
  }, acknowledgement), false);
  assert.equal(buildRouteRuleCollisionAcknowledgement({
    requestNo: "ROUTE-REVIEW-003",
    previewPriorityCollisionRuleCodes: []
  }), null);
});

test("blocks approval and clears acknowledgement when the preview contains a blocker", () => {
  const review = {
    requestNo: "ROUTE-REVIEW-001",
    ruleCode: "RULE_HOME_APP",
    previewPriorityCollisionRuleCodes: ["RULE_A_APP"],
    previewBlockingConflicts: [{
      conflictType: "ROUTE_DIVERGENCE",
      severity: "BLOCKER",
      ruleCode: "RULE_BLOCKING_APP",
      conflictingRuleCode: "RULE_HOME_APP",
      message: "同场景、同优先级和同表达式指向不同渠道"
    }]
  };
  const oldAcknowledgement = {
    requestNo: "ROUTE-REVIEW-001",
    ruleCodes: ["RULE_A_APP"]
  };

  assert.equal(canApproveRouteRuleReview(review), false);
  assert.equal(routeRuleBlockingConflicts(review).length, 1);
  assert.equal(buildRouteRuleCollisionAcknowledgement(review), null);
  assert.equal(isRouteRuleCollisionAcknowledged(review, oldAcknowledgement), false);
  assert.equal(
    formatRouteRulePreviewConflict(review, review.previewBlockingConflicts[0]),
    "ROUTE_DIVERGENCE / RULE_BLOCKING_APP：同场景、同优先级和同表达式指向不同渠道"
  );
});

test("fails closed for malformed or non-blocking route conflict previews", () => {
  assert.equal(canApproveRouteRuleReview({ previewBlockingConflicts: null }), true);
  const malformedReview = {
    previewBlockingConflicts: [null, { severity: "WARNING", message: "warning" }, { severity: "BLOCKER" }]
  };
  assert.equal(hasInvalidRouteRuleBlockingPreview(malformedReview), true);
  assert.equal(canApproveRouteRuleReview(malformedReview), false);
  assert.deepEqual(routeRuleBlockingConflicts(malformedReview), []);
});

test("shows valid warning previews without blocking approval", () => {
  const review = {
    requestNo: "ROUTE-REVIEW-001",
    ruleCode: "RULE_HOME_APP",
    previewWarningConflicts: [{
      conflictType: "DUPLICATE_RULE",
      severity: "WARNING",
      ruleCode: "RULE_DUPLICATE_APP",
      conflictingRuleCode: "RULE_HOME_APP",
      message: "场景、优先级、表达式和目标渠道完全相同"
    }]
  };

  assert.equal(canApproveRouteRuleReview(review), true);
  assert.equal(hasInvalidRouteRuleWarningPreview(review), false);
  assert.equal(routeRuleWarningConflicts(review).length, 1);
  assert.equal(
    formatRouteRulePreviewConflict(review, review.previewWarningConflicts[0]),
    "DUPLICATE_RULE / RULE_DUPLICATE_APP：场景、优先级、表达式和目标渠道完全相同"
  );
});

test("fails closed when a returned warning preview is malformed", () => {
  const review = { previewWarningConflicts: [{ severity: "WARNING", message: "" }] };

  assert.equal(hasInvalidRouteRuleWarningPreview(review), true);
  assert.equal(canApproveRouteRuleReview(review), false);
  assert.deepEqual(routeRuleWarningConflicts(review), []);
});

test("formats persisted route collision targets without inventing missing evidence", () => {
  assert.equal(formatRouteRuleConflictAudit({
    priorityCollisionDetected: true,
    priorityCollisionAcknowledged: true,
    priorityCollisionRuleCodesJson: '["RULE_APP","RULE,H5"]'
  }), "同优先级风险已确认：RULE_APP / RULE,H5");
  assert.equal(formatRouteRuleConflictAudit({
    priorityCollisionDetected: true,
    priorityCollisionAcknowledged: false,
    priorityCollisionRuleCodesJson: null
  }), "同优先级风险未确认（冲突对象未记录）");
  assert.equal(formatRouteRuleConflictAudit({
    priorityCollisionDetected: true,
    priorityCollisionAcknowledged: true,
    priorityCollisionRuleCodesJson: "RULE_APP,RULE_H5"
  }), "同优先级风险已确认（冲突对象快照异常）");
  assert.equal(formatRouteRuleConflictAudit({
    priorityCollisionDetected: false
  }), "未发现同优先级潜在冲突");
  assert.equal(formatRouteRuleConflictAudit({}), "冲突复验未执行");
});

test("only submits the exact collision preview for the approved selected review", () => {
  const review = {
    requestNo: "ROUTE-REVIEW-001",
    previewPriorityCollisionRuleCodes: ["RULE_A_APP", "RULE_Z_APP"]
  };
  const baseAction = {
    reviewer: "reviewer-b",
    reviewComment: "checked",
    approved: true,
    review,
    acknowledgement: buildRouteRuleCollisionAcknowledgement(review)
  };

  assert.deepEqual(buildRouteRuleReviewActionPayload(baseAction), {
    reviewer: "reviewer-b",
    reviewComment: "checked",
    priorityCollisionAcknowledged: true,
    acknowledgedPriorityCollisionRuleCodes: ["RULE_A_APP", "RULE_Z_APP"]
  });
  assert.equal(buildRouteRuleReviewActionPayload({
    ...baseAction,
    review: { ...review, requestNo: "ROUTE-REVIEW-002" }
  }).priorityCollisionAcknowledged, false);
  assert.deepEqual(buildRouteRuleReviewActionPayload({
    ...baseAction,
    approved: false
  }).acknowledgedPriorityCollisionRuleCodes, []);
});
