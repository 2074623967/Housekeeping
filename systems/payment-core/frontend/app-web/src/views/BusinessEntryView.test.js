import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const viewPath = join(dirname(fileURLToPath(import.meta.url)), "BusinessEntryView.vue");

test("ignores a prepay response after the business scene changes", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /const submittedSceneType = props\.sceneType;/);
  assert.match(
    source,
    /if \(submittedSceneType !== props\.sceneType\) \{\n\s+return;\n\s+\}/
  );
});

test("forwards the validated prepay scene into the cashier route", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /buildCashierRoute\(prepay\.prepayOrderNo, \{[\s\S]*bizType: props\.sceneType,[\s\S]*payScene: prepay\.payScene/);
});
