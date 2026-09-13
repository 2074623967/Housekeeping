import assert from "node:assert/strict";
import test from "node:test";
import {
  buildRouteRuleReviewActionPayload,
  formatRouteRuleConflictAudit,
  isRouteRuleCollisionAcknowledged
} from "./paymentRouteReview.js";

test("scopes route collision acknowledgement to the selected review request", () => {
  assert.equal(isRouteRuleCollisionAcknowledged("ROUTE-REVIEW-001", "ROUTE-REVIEW-001"), true);
  assert.equal(isRouteRuleCollisionAcknowledged("ROUTE-REVIEW-002", "ROUTE-REVIEW-001"), false);
  assert.equal(isRouteRuleCollisionAcknowledged("", ""), false);
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

test("only submits collision acknowledgement for the approved selected review", () => {
  const baseAction = {
    reviewer: "reviewer-b",
    reviewComment: "checked",
    approved: true,
    acknowledgedRequestNo: "ROUTE-REVIEW-001"
  };

  assert.deepEqual(buildRouteRuleReviewActionPayload({
    ...baseAction,
    reviewRequestNo: "ROUTE-REVIEW-001"
  }), {
    reviewer: "reviewer-b",
    reviewComment: "checked",
    priorityCollisionAcknowledged: true
  });
  assert.equal(buildRouteRuleReviewActionPayload({
    ...baseAction,
    reviewRequestNo: "ROUTE-REVIEW-002"
  }).priorityCollisionAcknowledged, false);
  assert.equal(buildRouteRuleReviewActionPayload({
    ...baseAction,
    approved: false,
    reviewRequestNo: "ROUTE-REVIEW-001"
  }).priorityCollisionAcknowledged, false);
});
