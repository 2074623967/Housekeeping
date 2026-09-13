import assert from "node:assert/strict";
import test from "node:test";
import {
  buildRouteRuleReviewActionPayload,
  isRouteRuleCollisionAcknowledged
} from "./paymentRouteReview.js";

test("scopes route collision acknowledgement to the selected review request", () => {
  assert.equal(isRouteRuleCollisionAcknowledged("ROUTE-REVIEW-001", "ROUTE-REVIEW-001"), true);
  assert.equal(isRouteRuleCollisionAcknowledged("ROUTE-REVIEW-002", "ROUTE-REVIEW-001"), false);
  assert.equal(isRouteRuleCollisionAcknowledged("", ""), false);
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
