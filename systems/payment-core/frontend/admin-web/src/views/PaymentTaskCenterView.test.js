import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const viewPath = join(dirname(fileURLToPath(import.meta.url)), "PaymentTaskCenterView.vue");

test("blocks task execution when the task-run snapshot is stale", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /const canUseTaskRuns = computed\(\(\) => canUseOverview\.value/);
  assert.match(source, /async function runTask\(taskCode, taskName, candidateCount, actionRunner\) \{\s*if \(!canUseTaskRuns\.value \|\| candidateCount <= 0\)/);
  assert.equal((source.match(/:disabled="!canUseTaskRuns \|\| overview\./g) || []).length, 10);
});

test("rechecks overview and task runs after an ambiguous task execution error", () => {
  const source = readFileSync(viewPath, "utf8");
  const actionHandler = source.match(/async function runTask\([^)]*\) \{([\s\S]*?)\n\}\n\nfunction openOverviewRoute/)?.[1];
  assert.ok(actionHandler);
  assert.match(actionHandler, /catch \(error\) \{\s*const refreshed = await refreshSnapshots\(query, true\);/);
  assert.match(actionHandler, /actionErrorMessage\.value = refreshed[\s\S]*?旧快照不可操作/);
  assert.match(source, /async function refreshSnapshots\(query, afterAction = false\) \{[\s\S]*?Promise\.allSettled\(\[\s*paymentTaskCenterApi\.getOverview\(\),\s*fetchTaskRuns\(query\)/);
  assert.match(source, /const canUseTaskRuns = computed\(\(\) => canUseOverview\.value[\s\S]*?!taskRunsErrorMessage\.value\)/);
});

test("shows blocked expired payments separately from runnable close candidates", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /超时支付令牌阻断待核对[\s\S]*?overview\.blockedExpiredPaymentCount/);
  assert.match(source, /活跃提交令牌阻断的记录/);
  assert.match(source, /overview\.dueExpiredPaymentCount === 0/);
});

test("opens only a freshly loaded blocked payment fact through the read-only detail route", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /async function loadBlockedPayments\(nextPageNo = 1\)/);
  assert.match(source, /invalidateBlockedPayments\(\);[\s\S]*?Promise\.allSettled/);
  assert.match(source, /blockedSnapshotCount\.value === overview\.value\.blockedExpiredPaymentCount/);
  assert.match(source, /blockedPayments\.value\.some\(\(item\) => item\.paymentOrderId === paymentOrderId\)/);
  assert.match(source, /router\.push\(paymentDetailRoute\(paymentOrderId\)\)/);
});

test("invalidates historical token inventory on overview refresh and task execution", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /async function refreshSnapshots\([^)]*\) \{\s*invalidateBlockedPayments\(\);\s*invalidateExpiredTokens\(\);/);
  assert.match(source, /activeTaskCode\.value = taskCode;\s*invalidateBlockedPayments\(\);\s*invalidateExpiredTokens\(\);/);
  assert.match(source, /const canUseExpiredTokens = computed\(\(\) => canUseOverview\.value/);
  assert.match(source, /router\.push\(paymentDetailRoute\(paymentOrderId\)\)/);
  assert.match(source, /过期不代表渠道未受理/);
  assert.match(source, /overview\.expiredActiveSubmitTokenCount/);
  assert.match(source, /alert\.alertType === 'PAYMENT_SUBMIT_TOKEN_EXPIRY' \? loadExpiredTokens\(\)/);
});
