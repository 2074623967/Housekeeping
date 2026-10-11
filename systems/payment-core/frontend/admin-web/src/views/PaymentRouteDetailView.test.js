import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const source = fs.readFileSync(new URL("./PaymentRouteDetailView.vue", import.meta.url), "utf8");

test("keeps anomalous route detail readable without the payment-detail navigation", () => {
  assert.match(source, /payment: detail\.value\?\.paymentOrderId && !detail\.value\.successFactIssue[\s\S]*?paymentDetailRoute\(detail\.value\.paymentOrderId\) : null/);
  assert.match(source, /:disabled="!canUseSnapshot \|\| !relatedRoutes\.payment"/);
  assert.match(source, /canUseSnapshot && detail\?\.successFactIssue/);
  assert.match(source, /logs: detail\.value\?\.paymentOrderId/);
});
