import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const source = fs.readFileSync(new URL("./PaymentEventsView.vue", import.meta.url), "utf8");

test("keeps anomalous events readable without a rejected payment-detail link", () => {
  assert.match(source, /v-if="item\.successFactIssue"[^>]*> · \{\{ item\.successFactIssue \}\}/);
  assert.match(source, /v-if="selectedItem\.successFactIssue"[^>]*><span>支付成功事实/);
  assert.match(source, /:disabled="!canUseSnapshot \|\| !!selectedItem\.successFactIssue"[^>]*@click="openDrillDown\(paymentDetailRoute\(selectedItem\.paymentOrderId\)\)"/);
  assert.match(source, /openEventDetail\(selectedItem\.eventNo\)/);
  assert.match(source, /paymentLogsRoute\(\{ paymentOrderId: selectedItem\.paymentOrderId \}\)/);
});

test("refreshes the event snapshot after a failed manual republish", () => {
  const handler = source.match(/async function republish\(item\) \{([\s\S]*?)\n\}\n\nfunction goToPage/)?.[1];
  assert.ok(handler);
  assert.match(handler, /catch \(error\) \{[\s\S]*?activeEventNo\.value = "";\s*const refreshed = await loadEvents\(\{ \.\.\.loadedQuery\.value \}\);\s*actionErrorMessage\.value = refreshed/);
  assert.match(source, /:disabled="!canUseSnapshot \|\| \['SUCCESS', 'UNKNOWN'\]\.includes\(item\.publishStatus\)"/);
});
