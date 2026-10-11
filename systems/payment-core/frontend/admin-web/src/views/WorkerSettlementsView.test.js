import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const source = readFileSync(fileURLToPath(new URL("./WorkerSettlementsView.vue", import.meta.url)), "utf8");

test("gates worker settlement snapshot selection on a current list snapshot", () => {
  assert.match(source, /const canUseSnapshot = computed\(\(\) => hasLoadedData\.value && !isLoading\.value && !isSnapshotStale\.value\)/);
  assert.match(source, /function pickItem\(item\) \{\s*if \(!canUseSnapshot\.value\)/);
  assert.match(source, /<button class="link-button" :disabled="!canUseSnapshot" @click="pickItem\(item\)">查看快照<\/button>/);
});
