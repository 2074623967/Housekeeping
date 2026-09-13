import assert from "node:assert/strict";
import test from "node:test";
import {
  buildRouteRuleCollisionAcknowledgement,
  buildRouteRuleReviewActionPayload,
  formatRouteRuleConflictAudit,
  isRouteRuleCollisionAcknowledged
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
