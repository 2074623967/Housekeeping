import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = readFileSync(new URL("./RefundDetailView.vue", import.meta.url), "utf8");

test("refreshes the current refund detail after an action request fails", () => {
  const handler = source.match(/async function runAction\([^)]*\) \{([\s\S]*?)\n\}\n\nonMounted/)?.[1];
  assert.ok(handler);
  assert.match(handler, /catch \(error\) \{\s*if \(refundOrderId !== currentRefundOrderId\.value\) \{\s*return;\s*\}[\s\S]*?await loadDetail\(true, refundOrderId\);/);
  assert.match(source, /hasLoadedData\.value && \(loadErrorMessage\.value \|\| actionRefreshErrorMessage\.value\)/);
  assert.match(source, /退款动作后详情刷新失败，当前状态未经确认/);
});
