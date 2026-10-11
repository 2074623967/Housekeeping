import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const source = fs.readFileSync(new URL("./PaymentLogsView.vue", import.meta.url), "utf8");

test("keeps anomalous payment logs readable without linking to rejected payment detail", () => {
  assert.match(source, /function openPaymentDetail\(paymentOrderId\) \{[\s\S]*?items\.value\.find\(\(item\) => item\.paymentOrderId === paymentOrderId\)[\s\S]*?paymentItem\.successFactIssue/);
  assert.match(source, /v-if="item\.successFactIssue"[^>]*>\{\{ item\.paymentOrderId \}\}<\/span>/);
  assert.match(source, /v-if="item\.successFactIssue" class="badge danger">\{\{ item\.successFactIssue \}\}<\/span>/);
  assert.match(source, /selectedItem\.successFactIssue \? ` · \$\{selectedItem\.successFactIssue\}` : ""/);
  assert.match(source, /v-else[^>]*@click="openPaymentDetail\(item\.paymentOrderId\)"/);
  assert.match(source, /:disabled="!canUseSnapshot \|\| !!selectedItem\.successFactIssue"[^>]*@click="openPaymentDetail\(selectedItem\.paymentOrderId\)"/);
  assert.match(source, /selectedItem\.successFactIssue \? "保留日志快照/);
});
