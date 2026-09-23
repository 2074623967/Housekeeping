const assert = require("node:assert/strict");
const test = require("node:test");

const { paymentApi } = require("../utils/request");

let pageDefinition;
const originalPage = global.Page;
global.Page = (definition) => {
  pageDefinition = definition;
};
require("../pages/refund-progress/index");
global.Page = originalPage;

function flushPromises() {
  return new Promise((resolve) => setImmediate(resolve));
}

function createPage() {
  const page = {
    ...pageDefinition,
    data: { ...pageDefinition.data },
    setData(update) {
      Object.assign(this.data, update);
    }
  };
  return page;
}

function refundDetail(status = "PROCESSING") {
  return {
    refundOrderId: "REF-1",
    paymentOrderId: "PAY-1",
    orderNo: "ORD-1",
    refundAmount: "¥20.00",
    refundMethod: "原路退款",
    status,
    statusType: status === "SUCCESS" ? "success" : status === "FAIL" ? "danger" : "warn",
    operationLogs: [{ logNo: "LOG-1", actionName: "退款处理中", fromStatus: "REVIEWING", toStatus: status, createdAt: "2026-09-23 10:00:00" }],
    attempts: [{ refundAttemptNo: "ATT-1", attemptStatus: status, channelCode: "WX_H5", createdAt: "2026-09-23 10:00:01" }]
  };
}

test("loads a matching refund detail and builds a readable timeline", async () => {
  paymentApi.getRefundDetail = () => Promise.resolve(refundDetail());
  const page = createPage();
  page.onLoad({ refundOrderId: "REF-1" });
  await flushPromises();

  assert.equal(page.data.detail.refundOrderId, "REF-1");
  assert.equal(page.data.meta.title, "退款处理中");
  assert.equal(page.data.timeline.length, 2);
  assert.equal(page.data.loading, false);
  page.onUnload();
});

test("fails closed when a refund response belongs to another order", async () => {
  paymentApi.getRefundDetail = () => Promise.resolve({ ...refundDetail(), refundOrderId: "REF-OTHER" });
  const page = createPage();
  page.onLoad({ refundOrderId: "REF-1" });
  await flushPromises();

  assert.equal(page.data.detail, null);
  assert.match(page.data.errorMessage, /不一致/);
  page.onUnload();
});

test("treats the tap event as a manual refresh", async () => {
  let requestCount = 0;
  paymentApi.getRefundDetail = () => {
    requestCount += 1;
    return Promise.resolve(refundDetail(requestCount === 2 ? "SUCCESS" : "PROCESSING"));
  };
  const page = createPage();
  page.onLoad({ refundOrderId: "REF-1" });
  await flushPromises();
  page.refreshDetail({ type: "tap" });
  await flushPromises();

  assert.equal(requestCount, 2);
  assert.equal(page.data.detail.status, "SUCCESS");
  assert.match(page.data.successMessage, /已刷新/);
  page.onUnload();
});
