import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const source = fs.readFileSync(new URL("./PaymentEventDetailView.vue", import.meta.url), "utf8");

test("keeps anomalous event details and safe drill-downs", () => {
  assert.match(source, /payment: detail\.value\?\.paymentOrderId && !detail\.value\.successFactIssue[\s\S]*?paymentDetailRoute\(detail\.value\.paymentOrderId\) : null/);
  assert.match(source, /:disabled="!canUseSnapshot \|\| !relatedRoutes\.payment"/);
  assert.match(source, /v-if="detail\.successFactIssue"/);
  assert.match(source, /logs: detail\.value\?\.paymentOrderId/);
});
