import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const source = fs.readFileSync(new URL("./PaymentRequestsView.vue", import.meta.url), "utf8");

test("shows unaccepted channel submissions within processing requests without recommending queries", () => {
  assert.match(source, /<option>渠道提交中<\/option>/);
  assert.match(source, /item\.requestStatus === '渠道提交中'[^>]*>渠道受理未确认/);
  assert.match(source, /selectedItem\.requestStatus === '渠道提交中'[\s\S]*勿主动查单或重复提交/);
  assert.match(source, /处理中请求[\s\S]*渠道提交中/);
});

test("shows payment scene in payment request list snapshots", () => {
  assert.match(source, /<th>支付场景<\/th>/);
  assert.match(source, /<td>\{\{ item\.payScene \}\}<\/td>/);
  assert.match(source, /<span>支付场景<\/span><strong>\{\{ selectedItem\.payScene \}\}<\/strong>/);
});

test("keeps anomalous request snapshots and detail without an invalid payment link", () => {
  assert.match(source, /v-if="!item\.successFactIssue"[^>]*@click="openRoute\(paymentDetailRoute\(item\.paymentOrderId\)\)"/);
  assert.match(source, /v-else>\{\{ item\.paymentOrderId \}\} · \{\{ item\.successFactIssue \}\}<\/span>/);
  assert.match(source, /:disabled="!canUseSnapshot \|\| !!selectedItem\.successFactIssue"[^>]*@click="openRoute\(paymentDetailRoute\(selectedItem\.paymentOrderId\)\)"/);
  assert.match(source, /paymentRequestDetailRoute\(selectedItem\.requestNo\)/);
  assert.match(source, /paymentLogsRoute\(\{ paymentOrderId: selectedItem\.paymentOrderId \}\)/);
});
