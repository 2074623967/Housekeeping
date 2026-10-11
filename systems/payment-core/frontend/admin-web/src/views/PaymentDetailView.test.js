import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const source = fs.readFileSync(new URL("./PaymentDetailView.vue", import.meta.url), "utf8");

test("preserves and displays payment scene in the detail snapshot", () => {
  assert.match(source, /customerName: detail\.value\.customerName,[\s\S]*payScene: detail\.value\.payScene,[\s\S]*amount:/);
  assert.match(source, /<div class="detail-label">支付场景<\/div>[\s\S]*detail\.payScene/);
  assert.match(source, /<strong>支付场景：<\/strong>\{\{ detail\.payScene \|\| "-" \}\}/);
});

test("preserves the route payment id for a failed detail's read-only fallback", () => {
  assert.match(source, /router\.push\(paymentsRoute\(\{ paymentOrderId: currentPaymentOrderId \}\)\)/);
  assert.match(source, /v-if="loadErrorMessage && !hasLoadedData && currentPaymentOrderId"[^>]*router\.push\(paymentLogsRoute\(\{ paymentOrderId: currentPaymentOrderId \}\)\)/);
  assert.match(source, /按支付单号查看处理日志/);
});

test("refreshes the current payment detail after failed query and close requests", () => {
  for (const action of ["handleQuery", "handleClose"]) {
    const handler = source.match(new RegExp(`async function ${action}\\(\\) \\{([\\s\\S]*?)\\n\\}\\n`))?.[1];
    assert.ok(handler);
    assert.match(handler, /catch \(error\) \{\s*if \(paymentOrderId !== currentPaymentOrderId\.value\) \{\s*return;\s*\}[\s\S]*?await loadDetail\(true, paymentOrderId\);/);
  }
  assert.match(source, /支付动作后详情刷新失败，当前状态未经确认/);
});

test("distinguishes unaccepted submission from eligible callback on payment detail", () => {
  assert.match(source, /paymentDetailOperationSuggestions\(detail\.value\)/);
  assert.match(source, /v-if="isChannelSubmissionUnaccepted\(detail\.status, detail\.latestAttemptStatus, detail\.channelTransactionNo\)"[^>]*>渠道受理未确认/);
  assert.match(source, /function queryActionAvailabilityHint\(\) \{[\s\S]*?canRunQuery\(\)[\s\S]*?isChannelSubmissionUnaccepted\([\s\S]*?渠道受理未确认，当前不可查单/);
  assert.match(source, /\{\{ queryActionAvailabilityHint\(\) \}\}/);
});
