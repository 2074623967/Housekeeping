import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const source = readFileSync(fileURLToPath(new URL("./PaymentDeadLetterTasksView.vue", import.meta.url)), "utf8");

test("gates dead-letter task selection and detail navigation on a current snapshot", () => {
  assert.match(source, /const canUseSnapshot = computed\(\(\) => hasLoadedData\.value && !isInteractionLocked\.value && !isSnapshotStale\.value\)/);
  assert.match(source, /function pickItem\(item\) \{\s*if \(!canUseSnapshot\.value\)/);
  assert.match(source, /function openDetail\(item\) \{\s*if \(!canUseSnapshot\.value \|\| !item\?\.taskNo\)/);
  assert.match(source, /<button class="table-link" :disabled="!canUseSnapshot" @click="pickItem\(item\)">/);
  assert.match(source, /class="link-button"\s+:disabled="!canUseSnapshot"\s+@click="openDetail\(item\)"/);
});

test("rechecks dead-letter status after an ambiguous action error and locks a failed readback", () => {
  const actionHandler = source.match(/async function runAction\(item, action\) \{([\s\S]*?)\n\}\n\nfunction goToPage/)?.[1];
  assert.ok(actionHandler);
  assert.match(actionHandler, /catch \(error\) \{\s*const refreshed = await loadTasks\(item\.taskNo, \{ \.\.\.loadedQuery\.value \}\);/);
  assert.match(actionHandler, /actionErrorMessage\.value = refreshed[\s\S]*?列表复核失败，旧快照不可操作/);
  assert.match(source, /const isSnapshotStale = computed\(\(\) => hasLoadedData\.value && Boolean\(loadErrorMessage\.value\)\)/);
  assert.match(source, /if \(isInteractionLocked\.value \|\| isSnapshotStale\.value \|\| !hasLoadedData\.value/);
});
