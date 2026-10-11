import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const orderSource = fs.readFileSync(new URL("./OrderDetailView.vue", import.meta.url), "utf8");
const billSource = fs.readFileSync(new URL("./BillDetailView.vue", import.meta.url), "utf8");
const ordersSource = fs.readFileSync(new URL("./OrdersView.vue", import.meta.url), "utf8");

test("preserves returned payment scenes when opening order and bill cashiers", () => {
  assert.match(orderSource, /buildAppWebCashierUrl\(detail\.value\.latestPrepayOrderNo, undefined, detail\.value\.latestPayScene\)/);
  assert.match(billSource, /buildAppWebCashierUrl\(detail\.value\.latestPrepayOrderNo, undefined, detail\.value\.latestPayScene\)/);
  assert.match(orderSource, /orderAmount: detail\.value\.orderAmount,[\s\S]*latestPayScene: detail\.value\.latestPayScene/);
  assert.match(billSource, /unpaidAmount: detail\.value\.unpaidAmount,[\s\S]*latestPayScene: detail\.value\.latestPayScene/);
  assert.match(ordersSource, /buildAppWebCashierUrl\(prepay\.prepayOrderNo, undefined, prepay\.payScene\)/);
  assert.match(ordersSource, /buildAppWebCashierUrl\(orderItem\.latestPrepayOrderNo, undefined, orderItem\.latestPayScene\)/);
  assert.match(ordersSource, /<th>支付场景<\/th>[\s\S]*item\.latestPayScene/);
});

test("only enables confirmed standard-order links and explains unavailable links without guessing the source", () => {
  for (const name of [
    "BillDetailView",
    "CashierSessionDetailView",
    "PaymentEventDetailView",
    "PaymentFlowDetailView",
    "PaymentRequestDetailView",
    "PaymentLogDetailView"
  ]) {
    const detailSource = fs.readFileSync(new URL(`./${name}.vue`, import.meta.url), "utf8");
    assert.match(detailSource, /detail\.value\?\.standardOrderNo[\s\S]*?orderDetailRoute\(detail\.value\.standardOrderNo\)/);
    assert.match(detailSource, /:disabled="!canUseSnapshot \|\| !(?:orderRoute|relatedRoutes\.order)"/);
    assert.match(detailSource, /detail\.standardOrderNo \? "归属已确认" : "归属未确认，暂不可联查"/);
    assert.doesNotMatch(detailSource, /当前业务号不是标准订单|当前业务订单号未命中标准订单/);
  }
});
