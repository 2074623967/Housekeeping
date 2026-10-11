import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const source = fs.readFileSync(new URL("./PaymentsView.vue", import.meta.url), "utf8");

test("preserves pay scene when payment actions rebuild the selected snapshot", () => {
  assert.match(source, /function updatePaymentSnapshot\(paymentDetail\) \{[\s\S]*payScene: paymentDetail\.payScene,[\s\S]*items\.value = items\.value\.map/);
});

test("shows pay scene in the payment list and selected snapshot", () => {
  assert.match(source, /<th>支付场景<\/th>/);
  assert.match(source, /<td>\{\{ item\.payScene \|\| "—" \}\}/);
  assert.match(source, /<span>支付场景<\/span><strong>\{\{ selectedItem\.payScene \|\| "—" \}\}<\/strong>/);
});

test("keeps unaccepted channel submissions out of callback metrics and query guidance", () => {
  assert.match(source, /callbackPendingTotal: items\.value\.filter\(\(item\) => item\.status === "WAIT_CALLBACK"[\s\S]*!isChannelSubmissionUnaccepted/);
  assert.match(source, /item\.status \}\}<\/span> <span v-if="isChannelSubmissionUnaccepted\(item\.status/);
  assert.match(source, /v-else-if="isChannelSubmissionUnaccepted\(selectedItem\.status[\s\S]*渠道受理尚未确认，请等待状态推进/);
});

test("keeps anomalous success rows on the snapshot and links to logs in every list mode", () => {
  assert.doesNotMatch(source, /isSuccessFactDiagnosticView/);
  assert.match(source, /function openLogs\(paymentOrderId\) \{[\s\S]*?if \(!canUseSnapshot\.value\)[\s\S]*?router\.push\(paymentLogsRoute\(\{ paymentOrderId \}\)\)/);
  assert.match(source, /v-if="item\.successFactIssue \|\| item\.prepayBindingIssue"[^>]*:disabled="!canUseSnapshot"[^>]*@click="openLogs\(item\.paymentOrderId\)"[^>]*>处理日志<\/button>/);
  assert.match(source, /v-else[^>]*@click="openDetail\(item\.paymentOrderId\)"[^>]*>详情<\/button>/);
  assert.match(source, /function openDetail\(paymentOrderId\) \{[\s\S]*?items\.value\.find\(\(item\) => item\.paymentOrderId === paymentOrderId\)[\s\S]*?!paymentItem \|\| paymentItem\.successFactIssue/);
  assert.match(source, /return !item\.successFactIssue && !item\.prepayBindingIssue && canQueryPaymentChannel/);
  assert.match(source, /!loadedQuery\.value \|\| !paymentItem \|\| !canRunQuery\(paymentItem\)/);
  assert.match(source, /:disabled="!canUseSnapshot \|\| !canRunQuery\(item\)"/);
  assert.match(source, /selectedItem\.successFactIssue/);
  assert.match(source, /successFactIssue: null/);
  assert.match(source, /successTotal: items\.value\.filter\(\(item\) => item\.status === "SUCCESS"[\s\S]*?!item\.successFactIssue && !item\.prepayBindingIssue\)\.length/);
  assert.match(source, /成功事实异常或预付归属未确认时/);
  assert.match(source, /hasUnverifiablePaymentAmount = computed\(\(\) => items\.value\.some/);
  assert.match(source, /本页实付金额（异常值不汇总）/);
});

test("keeps payment binding anomalies readable but blocks inferred payment actions", () => {
  assert.match(source, /v-if="item\.prepayBindingIssue"/);
  assert.match(source, /v-if="selectedItem\.prepayBindingIssue"/);
  assert.match(source, /paymentItem\.successFactIssue \|\| paymentItem\.prepayBindingIssue/);
  assert.match(source, /return !item\.prepayBindingIssue[\s\S]*?canClosePaymentLocally/);
  assert.match(source, /prepayBindingIssue: null/);
});

test("refreshes the payment list after failed query and close requests", () => {
  for (const action of ["handleQuery", "handleClose"]) {
    const handler = source.match(new RegExp(`async function ${action}\\(paymentOrderId\\) \\{([\\s\\S]*?)\\n\\}\\n`))?.[1];
    assert.ok(handler);
    assert.match(handler, /catch \(error\) \{[\s\S]*?await refreshList\(paymentOrderId, true, query\);/);
  }
  assert.match(source, /支付动作后列表刷新失败，当前状态未经确认/);
});
