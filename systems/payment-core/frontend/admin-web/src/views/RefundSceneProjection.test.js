import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const listSource = fs.readFileSync(new URL("./RefundsView.vue", import.meta.url), "utf8");
const detailSource = fs.readFileSync(new URL("./RefundDetailView.vue", import.meta.url), "utf8");

test("surfaces the payment scene in refund list snapshots", () => {
  assert.match(listSource, /payScene: refund\.payScene/);
  assert.match(listSource, /<th>支付场景<\/th>/);
  assert.match(listSource, /<td>\{\{ item\.payScene \|\| "-" \}\}<\/td>/);
});

test("binds refund detail refresh snapshots to the payment scene", () => {
  assert.match(detailSource, /paymentOrderId: detail\.value\.paymentOrderId,[\s\S]*payScene: detail\.value\.payScene/);
  assert.match(detailSource, /<strong>支付场景：<\/strong>\{\{ detail\.payScene \|\| "-" \}\}/);
});

test("preserves the payment scene when opening user refund progress", () => {
  assert.match(detailSource, /buildAppWebRefundProgressUrl\([\s\S]*detail\.value\.refundOrderId,[\s\S]*detail\.value\.payScene/);
});

test("binds refund operation advice to the latest detail snapshot", () => {
  assert.match(detailSource, /computed\(\(\) => refundOperationSuggestions\(detail\.value\)\)/);
  assert.match(detailSource, /v-for="item in operationSuggestions"/);
});
