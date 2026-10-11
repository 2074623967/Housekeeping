import assert from "node:assert/strict";
import test from "node:test";
import { canRunRefundAction, refundOperationSuggestions } from "./refundAction.js";

test("allows each refund operation only in its eligible state", () => {
  assert.equal(canRunRefundAction("PROCESSING", "query"), true);
  assert.equal(canRunRefundAction("REVIEWING", "approve"), true);
  assert.equal(canRunRefundAction("FAIL", "retry"), true);

  assert.equal(canRunRefundAction("SUCCESS", "query"), false);
  assert.equal(canRunRefundAction("PROCESSING", "approve"), false);
  assert.equal(canRunRefundAction("REVIEWING", "retry"), false);
  assert.equal(canRunRefundAction("FAIL", "unknown"), false);
  assert.equal(canRunRefundAction(null, "query"), false);
});

test("warns operators to query unknown refund submissions without blind retry", () => {
  const suggestions = refundOperationSuggestions({
    status: "PROCESSING",
    attempts: [{ attemptStatus: "UNKNOWN" }],
    operationLogs: [{ actionCode: "CHANNEL_SUBMIT_UNKNOWN" }]
  });

  assert.match(suggestions[0], /优先主动查单/);
  assert.match(suggestions[0], /不得重发退款提交/);
  assert.doesNotMatch(suggestions[0], /自动重试/);
  assert.match(refundOperationSuggestions({ status: "PROCESSING", attempts: [] })[0], /勿重复提交渠道/);
  assert.match(refundOperationSuggestions({ status: "FAIL", operationLogs: [] })[0], /失败重试/);
  assert.deepEqual(refundOperationSuggestions(null), []);
});
