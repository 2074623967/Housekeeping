import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const viewPath = join(dirname(fileURLToPath(import.meta.url)), "DashboardView.vue");

test("blocks dashboard navigation when its displayed snapshot is stale", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /const canUseSnapshot = computed\(\(\) => Boolean\(/);
  assert.match(source, /function openRoute\(route\) \{\s*if \(!canUseSnapshot\.value \|\| !route\)/);
  assert.equal((source.match(/:disabled="!canUseSnapshot"/g) || []).length, 2);
});
