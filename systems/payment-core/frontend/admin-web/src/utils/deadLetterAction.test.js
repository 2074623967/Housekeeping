import assert from "node:assert/strict";
import test from "node:test";
import { replayActionMessage } from "./deadLetterAction.js";

test("reports replay success only after the server records REPLAYED", () => {
  assert.deepEqual(replayActionMessage({ taskNo: "DLQ-1", taskStatus: "REPLAYED" }), {
    tone: "success",
    message: "任务 DLQ-1 已完成消息发布确认，请继续核对目标系统"
  });
});

test("does not present a failed replay as successful", () => {
  const result = replayActionMessage({ taskNo: "DLQ-2", taskStatus: "REPLAY_FAILED" });

  assert.equal(result.tone, "error");
  assert.match(result.message, /重放失败/);
});

test("keeps transitional replay states explicit", () => {
  assert.equal(
    replayActionMessage({ taskNo: "DLQ-3", taskStatus: "REPLAYING" }).tone,
    "warning"
  );
  assert.equal(
    replayActionMessage({ taskNo: "DLQ-4", taskStatus: "UNKNOWN" }).tone,
    "warning"
  );
});
