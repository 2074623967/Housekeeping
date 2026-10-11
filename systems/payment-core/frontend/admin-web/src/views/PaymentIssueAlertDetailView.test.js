import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const source = readFileSync(fileURLToPath(new URL("./PaymentIssueAlertDetailView.vue", import.meta.url)), "utf8");

test("gates alert detail navigation when the displayed snapshot is stale", () => {
  assert.match(source, /const isSnapshotStale = computed\(\(\) => Boolean\(hasLoadedData\.value && loadErrorMessage\.value\)\)/);
  assert.match(source, /const canUseSnapshot = computed\(\(\) => Boolean\(/);
  assert.match(source, /detail\.value\?\.alertNo === currentAlertNo\.value/);
  assert.equal((source.match(/:disabled="!canUseSnapshot \|\| !relatedRoutes\./g) || []).length, 3);
});
