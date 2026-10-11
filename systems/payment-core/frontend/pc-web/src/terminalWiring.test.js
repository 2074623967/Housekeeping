import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const source = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), "main.js"),
  "utf8"
);

test("wires PC cashier and result pages to terminal wrappers", () => {
  assert.match(source, /component: \(\) => import\("\.\/views\/CashierView\.vue"\)/);
  assert.match(source, /component: \(\) => import\("\.\/views\/ResultView\.vue"\)/);
});

test("keeps PC terminal identity on shared refund progress view", () => {
  assert.match(source, /props: \{ terminalVariant: "pc" \}/);
});
