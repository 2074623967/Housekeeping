import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const source = fs.readFileSync(new URL("./BillsView.vue", import.meta.url), "utf8");

test("surfaces the latest payment scene in bill list and snapshot", () => {
  assert.match(source, /<th>支付场景<\/th>[\s\S]*item\.latestPayScene/);
  assert.match(source, /<span>支付场景<\/span><strong>\{\{ selectedItem\.latestPayScene/);
});
