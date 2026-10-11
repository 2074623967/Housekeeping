import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const source = fs.readFileSync(new URL("./PaymentRequestDetailView.vue", import.meta.url), "utf8");

test("preserves payment scene in payment request details and cashier entry", () => {
  assert.match(source, /prepayOrderNo: detail\.value\.prepayOrderNo,[\s\S]*payScene: detail\.value\.payScene/);
  assert.match(source, /buildAppWebCashierUrl\(detail\.value\.prepayOrderNo, undefined, detail\.value\.payScene\)/);
  assert.match(source, /<strong>支付场景：<\/strong>\{\{ formatValue\(detail\.payScene\) \}\}/);
});

test("keeps anomalous request detail readable without the failed payment drill-down", () => {
  assert.match(source, /payment: detail\.value\?\.paymentOrderId && !detail\.value\.successFactIssue[\s\S]*?paymentDetailRoute\(detail\.value\.paymentOrderId\) : null/);
  assert.match(source, /:disabled="!canUseSnapshot \|\| !relatedRoutes\.payment"/);
  assert.match(source, /canUseSnapshot && detail\?\.successFactIssue/);
  assert.match(source, /paymentLog: detail\.value\?\.paymentOrderId/);
});
