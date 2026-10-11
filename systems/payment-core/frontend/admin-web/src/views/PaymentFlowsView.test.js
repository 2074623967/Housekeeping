import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const source = fs.readFileSync(new URL("./PaymentFlowsView.vue", import.meta.url), "utf8");

test("blocks every payment-flow drill-down control when the snapshot is stale", () => {
  assert.match(source, /const canUseSnapshot = computed\(\(\) => hasLoadedData\.value && !isLoading\.value && !isSnapshotStale\.value\)/);
  assert.equal((source.match(/:disabled="!canUseSnapshot"/g) || []).length, 6);
  assert.doesNotMatch(source, /:disabled="isSnapshotStale"/);
});

test("keeps anomalous flow snapshots and detail while blocking payment detail", () => {
  assert.match(source, /v-if="!item\.successFactIssue"[^>]*@click="openPaymentDetail\(item\.paymentOrderId\)"/);
  assert.match(source, /v-else>\{\{ item\.paymentOrderId \}\} · \{\{ item\.successFactIssue \}\}<\/span>/);
  assert.match(source, /:disabled="!canUseSnapshot \|\| !!selectedItem\.successFactIssue"[^>]*@click="openPaymentDetail\(selectedItem\.paymentOrderId\)"/);
  assert.match(source, /items\.value\.some\(\(item\) => item\.paymentOrderId === paymentOrderId && !item\.successFactIssue\)/);
});

test("surfaces the payment scene in flow list and snapshot", () => {
  assert.match(source, /<th>支付场景<\/th>[\s\S]*item\.payScene/);
  assert.match(source, /<span>支付场景<\/span><strong>\{\{ formatValue\(selectedItem\.payScene\) \}\}<\/strong>/);
});
