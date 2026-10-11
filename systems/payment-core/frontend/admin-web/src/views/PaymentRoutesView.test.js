import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const source = fs.readFileSync(new URL("./PaymentRoutesView.vue", import.meta.url), "utf8");

test("keeps anomalous route snapshots and details without a failed payment link", () => {
  assert.match(source, /v-if="!item\.successFactIssue"[^>]*@click="openPaymentDetail\(item\.paymentOrderId\)"/);
  assert.match(source, /v-else>\{\{ item\.paymentOrderId \}\} · \{\{ item\.successFactIssue \}\}<\/span>/);
  assert.match(source, /:disabled="!canUseSnapshot \|\| !!selectedItem\.successFactIssue"[^>]*@click="openPaymentDetail\(selectedItem\.paymentOrderId\)"/);
  assert.match(source, /items\.value\.some\(\(item\) => item\.paymentOrderId === paymentOrderId && !item\.successFactIssue\)/);
  assert.match(source, /openRouteDetail\(selectedItem\.routeNo\)/);
  assert.match(source, /openPaymentRequests\(selectedItem\.paymentOrderId\)/);
});
