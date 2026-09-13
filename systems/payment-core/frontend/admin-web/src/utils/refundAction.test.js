import assert from "node:assert/strict";
import test from "node:test";
import { canRunRefundAction } from "./refundAction.js";

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
