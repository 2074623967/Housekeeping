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
