import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const source = fs.readFileSync(new URL("./PaymentRecordDetailView.vue", import.meta.url), "utf8");

test("uses record-specific payment and transaction facts for query eligibility", () => {
  assert.match(source, /canQueryPaymentChannel\(\s*detail\.value\?\.paymentStatus,\s*detail\.value\?\.latestAttemptStatus,\s*detail\.value\?\.latestAttemptCompletedAt,\s*detail\.value\?\.externalTransactionNo/);
  assert.match(source, /if \(canRunQuery\.value && detail\.value\.latestAttemptStatus/);
});

test("surfaces the payment scene in payment record details", () => {
  assert.match(source, /<strong>支付场景：<\/strong>\{\{ formatValue\(detail\.payScene\) \}\}/);
});

test("binds refresh snapshots to the payment scene", () => {
  assert.match(source, /businessOrderNo: detail\.value\.businessOrderNo,[\s\S]*payScene: detail\.value\.payScene,[\s\S]*paymentMethod:/);
});

test("binds payment-record queries to the payment scene snapshot", () => {
  assert.match(source, /paymentApi\.query\(paymentOrderId, operator, \{[\s\S]*amount: detail\.value\?\.paymentAmount,[\s\S]*payScene: detail\.value\?\.payScene/);
});

test("keeps anomalous record details readable without payment detail or query actions", () => {
  assert.match(source, /!detail\.value\?\.successFactIssue/);
  assert.match(source, /!canUseSnapshot\.value \|\| detail\.value\?\.successFactIssue/);
  assert.match(source, /:disabled="!canUseSnapshot \|\| !!detail\?\.successFactIssue \|\| !!detail\?\.prepayBindingIssue"[^>]*@click="openPaymentDetail"/);
  assert.match(source, /v-if="detail\.successFactIssue"/);
  assert.match(source, /@click="openPaymentRequests"/);
  assert.match(source, /@click="openPaymentLogs"/);
  assert.match(source, /if \(detail\.value\.successFactIssue\) \{[\s\S]*?勿将当前状态视为可信成功/);
});

test("surfaces multiple prepay bindings and blocks inferred payment actions", () => {
  assert.match(source, /!detail\.value\?\.prepayBindingIssue/);
  assert.match(source, /if \(!canUseSnapshot\.value \|\| detail\.value\?\.successFactIssue \|\| detail\.value\?\.prepayBindingIssue\)/);
  assert.match(source, /v-if="detail\.prepayBindingIssue"/);
  assert.match(source, /同一支付单关联多张预付单/);
  assert.match(source, /当前支付单缺少精确预付归属/);
});

test("shows unverified successful refunds with a stale-snapshot-safe refund drill-down", () => {
  assert.match(source, /detail\.value\.unverifiedRefundCount > 0/);
  assert.match(source, /!canUseSnapshot\.value \|\| !\(detail\.value\?\.unverifiedRefundCount > 0\)/);
  assert.match(source, /refundsRoute\(\{\s*paymentOrderId: detail\.value\.paymentOrderId,\s*refundStatus: "UNVERIFIED_SUCCESS"/);
  assert.match(source, /:disabled="!canUseSnapshot" @click="openUnverifiedRefunds"/);
});
