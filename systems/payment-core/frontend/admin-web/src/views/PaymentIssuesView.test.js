import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import test from "node:test";

const source = readFileSync(fileURLToPath(new URL("./PaymentIssuesView.vue", import.meta.url)), "utf8");

test("locks issue actions if either list or responsibility summary is stale", () => {
  assert.match(source, /hasLoadedData\.value && \(loadErrorMessage\.value \|\| actionRefreshErrorMessage\.value \|\| summaryErrorMessage\.value\)/);
  assert.match(source, /const isWriteDisabled = computed\(\(\) => \([\s\S]*?isSnapshotStale\.value/);
  assert.match(source, /:disabled="isWriteDisabled"\s+@click="submitBatchAction"/);
});

test("rechecks an ambiguous batch request and clears the old selection", () => {
  const handler = source.match(/async function submitBatchAction\(\) \{([\s\S]*?)\n\}\n\nonMounted/)?.[1];
  assert.ok(handler);
  assert.match(handler, /catch \(error\) \{\s*selectedIssueNos\.value = \[\];\s*const refreshed = await loadIssues\(true, query\);/);
  assert.match(handler, /actionErrorMessage\.value = refreshed[\s\S]*?旧快照不可操作/);
  assert.match(source, /批量处理后的异常列表刷新失败，当前状态未经确认/);
});

test("reuses the same idempotency key for a matching ambiguous batch attempt", () => {
  assert.match(source, /let pendingActionAttempt = null;/);
  assert.match(source, /pendingActionAttempt = await resolvePaymentIssueActionAttempt\(actionPayload, pendingActionAttempt\)/);
  assert.match(source, /paymentIssueApi\.batchAction\(\{ \.\.\.actionPayload, idempotencyKey: pendingActionAttempt\.idempotencyKey \}\)/);
  assert.match(source, /clearPaymentIssueActionAttempt\(pendingActionAttempt\)/);
  assert.match(source, /pendingActionAttempt = null;\s*selectedIssueNos\.value = \[\];/);
  assert.match(source, /UnverifiablePaymentIssueActionAttemptError[\s\S]*?批量处理未发送：/);
});

test("does not call a confirmed batch cleanup failure an unsent action", () => {
  assert.match(source, /let cleanupFailed = false;[\s\S]*?await paymentIssueApi\.batchAction[\s\S]*?try \{\s*clearPaymentIssueActionAttempt\(pendingActionAttempt\);/);
  assert.match(source, /cleanupFailed = true;\s*pendingActionAttempt = \{ \.\.\.pendingActionAttempt, confirmed: true \};/);
  assert.match(source, /const failureLabel = cleanupFailed\s*\? "批量处理响应已成功，但本机未决键清理失败："/);
});
