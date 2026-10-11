import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const source = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), "main.js"),
  "utf8"
);

test("wires H5 cashier and result pages to shared terminal views", () => {
  assert.match(source, /import CashierView from "\.\/views\/CashierView\.vue";/);
  assert.match(source, /import ResultView from "\.\/views\/ResultView\.vue";/);
  assert.match(source, /component: CashierView/);
  assert.match(source, /component: ResultView/);
});

test("keeps H5 terminal identity on shared refund progress view", () => {
  assert.match(source, /props: \{ terminalVariant: "h5" \}/);
});
