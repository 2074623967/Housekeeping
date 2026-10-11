import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const source = fs.readFileSync(new URL("./RefundProgressView.vue", import.meta.url), "utf8");

test("binds an externally supplied payment scene to refund detail loads", () => {
  assert.match(source, /route\.query\.payScene/);
  assert.match(source, /getRefundDetail\(refundOrderId, expectedPayScene\.value \|\| null\)/);
  assert.match(source, /currentRefundOrderId\.value,[\s\S]*expectedPayScene\.value \|\| null/);
  assert.match(source, /watch\(\[currentRefundOrderId, expectedPayScene\]/);
});
