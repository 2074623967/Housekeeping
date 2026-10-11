import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const source = fs.readFileSync(new URL("./PaymentLogDetailView.vue", import.meta.url), "utf8");

test("blocks payment detail navigation for an anomalous log without hiding other links", () => {
  assert.match(source, /payment: detail\.value\?\.paymentOrderId && !detail\.value\.successFactIssue[\s\S]*?paymentDetailRoute\(detail\.value\.paymentOrderId\) : null/);
  assert.match(source, /:disabled="!canUseSnapshot \|\| !relatedRoutes\.payment"/);
  assert.match(source, /v-if="detail\.successFactIssue"[^>]*><strong>成功事实异常/);
  assert.match(source, /detail\.value\?\.paymentOrderId \? paymentFlowsRoute/);
});
