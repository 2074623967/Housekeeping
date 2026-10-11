import assert from "node:assert/strict";
import test from "node:test";
import { resolvePaymentDayEndRunMessage } from "./paymentDayEnd.js";

test("reports a completed day-end result with readiness", () => {
  assert.equal(
    resolvePaymentDayEndRunMessage({
      latestBizDate: "2026-09-25",
      latestBatchStatus: "COMPLETED",
      reconciliationReadinessStatus: "READY"
    }, "2026-09-25"),
    "2026-09-25 日终处理已完成（状态：COMPLETED，对账准入：READY）。"
  );
});

test("does not present a warning batch as clean completion", () => {
  assert.match(
    resolvePaymentDayEndRunMessage({
      latestBatchStatus: "WARNING",
      reconciliationReadinessStatus: "BLOCKED"
    }, "2026-09-25"),
    /存在未收口风险.*WARNING.*BLOCKED/
  );
});

test("falls back to an explicit unknown result state", () => {
  assert.equal(
    resolvePaymentDayEndRunMessage({}, "2026-09-25"),
    "2026-09-25 日终处理已返回结果（批次状态：未知，对账准入：未生成），请核对最近批次。"
  );
});
