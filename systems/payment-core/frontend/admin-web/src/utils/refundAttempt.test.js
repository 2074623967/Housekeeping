import assert from "node:assert/strict";
import test from "node:test";
import { refundAttemptStatusType } from "./refundAttempt.js";

test("keeps supported refund attempt status styles and falls back safely", () => {
  assert.equal(refundAttemptStatusType(" success "), "success");
  assert.equal(refundAttemptStatusType("WARN"), "warn");
  assert.equal(refundAttemptStatusType("danger"), "danger");
  assert.equal(refundAttemptStatusType("unexpected"), "info");
  assert.equal(refundAttemptStatusType(null), "info");
});
