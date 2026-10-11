import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const source = readFileSync(fileURLToPath(new URL("./PaymentRecordsView.vue", import.meta.url)), "utf8");

test("gates payment record selection and detail navigation on a current snapshot", () => {
  assert.match(source, /const canUseSnapshot = computed\(\(\) => hasLoadedData\.value && !isLoading\.value && !isSnapshotStale\.value\)/);
  assert.match(source, /function openPayment\(row\) \{\s*if \(!canUseSnapshot\.value \|\| !row\?\.paymentOrderId\)/);
  assert.match(source, /function pickRow\(row\) \{\s*if \(!canUseSnapshot\.value\)/);
  assert.equal((source.match(/:disabled="!canUseSnapshot"/g) || []).length, 3);
});

test("surfaces the payment scene in the selected record summary", () => {
  assert.match(source, /<span>支付场景<\/span><strong>\{\{ selectedRow\.payScene \|\| "—" \}\}<\/strong>/);
});

test("surfaces multiple prepay binding conflicts in lists and snapshots", () => {
  assert.match(source, /column\.key === 'paymentType' && row\.prepayBindingIssue/);
  assert.match(source, /v-if="selectedRow\.prepayBindingIssue"/);
  assert.match(source, /预付缺失待核查/);
});

test("offers the business payment types projected from payment scenes", () => {
  for (const paymentType of ["消费支付", "充值支付", "提现补款", "转账补款", "余额补款", "未分类"]) {
    assert.ok(source.includes(`<option value="${paymentType}">${paymentType}</option>`));
  }
});

test("sends the customer-name filter with its actual semantics", () => {
  assert.match(source, /<label for="record-customer-name">客户姓名<\/label>/);
  assert.match(source, /v-model="filters\.customerName" placeholder="请输入客户姓名"/);
});

test("reset clears the customer-name filter rather than the legacy user-id key", () => {
  const resetSource = source.match(/function resetFilters\(\) \{[\s\S]*?\n\}/)?.[0] || "";
  assert.match(resetSource, /filters\.value = \{\s*customerName: ""/);
  assert.doesNotMatch(resetSource, /userId:/);
});

test("marks anomalous successful records while keeping record details available", () => {
  assert.match(source, /item\.paymentStatus === "支付成功" && !item\.successFactIssue/);
  assert.match(source, /column\.key === 'paymentStatus' && row\.successFactIssue/);
  assert.match(source, /v-if="selectedRow\.successFactIssue"/);
  assert.match(source, /openPayment\(row\)/);
});

test("links unverified successful refunds to the matching refund review filter", () => {
  assert.match(source, /row\.unverifiedRefundCount > 0/);
  assert.match(source, /!canUseSnapshot\.value \|\| !row\?\.paymentOrderId \|\| !\(row\.unverifiedRefundCount > 0\)/);
  assert.match(source, /refundsRoute\(\{\s*paymentOrderId: row\.paymentOrderId,\s*refundStatus: "UNVERIFIED_SUCCESS"/);
  assert.match(source, /@click="openUnverifiedRefunds\(selectedRow\)"/);
});
