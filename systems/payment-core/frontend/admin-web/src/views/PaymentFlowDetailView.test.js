import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const source = fs.readFileSync(new URL("./PaymentFlowDetailView.vue", import.meta.url), "utf8");

test("keeps anomalous flow detail readable without a payment detail link", () => {
  assert.match(source, /payment: detail\.value\?\.paymentOrderId && !detail\.value\.successFactIssue[\s\S]*?paymentDetailRoute\(detail\.value\.paymentOrderId\) : null/);
  assert.match(source, /:disabled="!canUseSnapshot \|\| !relatedRoutes\.payment"/);
  assert.match(source, /canUseSnapshot && detail\?\.successFactIssue/);
  assert.match(source, /detail\.value\?\.paymentOrderId\s*\? paymentLogsRoute/);
});
