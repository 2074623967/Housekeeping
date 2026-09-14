const assert = require("node:assert/strict");
const test = require("node:test");

const { paymentApi } = require("../utils/request");

let pageDefinition;
const originalPage = global.Page;
global.Page = (definition) => {
  pageDefinition = definition;
};
require("../pages/result/index");
global.Page = originalPage;

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, reject, resolve };
}

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

function paymentDetail(status = "WAIT_CALLBACK") {
  return {
    paymentOrderId: "PAY-1",
    prepayOrderNo: "PRE-TRUSTED",
    status,
    latestAttemptStatus: "等待回调",
    channelTransactionNo: "WX-1"
  };
}

test("reloads after an initial detail request is invalidated while hidden", async () => {
  const firstRequest = deferred();
  let requestCount = 0;
  paymentApi.getDetail = () => {
    requestCount += 1;
    return requestCount === 1 ? firstRequest.promise : Promise.resolve(paymentDetail());
  };
  const page = createPage();

  page.onLoad({ paymentOrderId: "PAY-1", prepayOrderNo: "PRE-URL" });
  page.onShow();
  page.onHide();
  firstRequest.resolve(paymentDetail("SUCCESS"));
  await flushPromises();

  assert.equal(page.data.detail, null);
  page.onShow();
  await flushPromises();
  assert.equal(requestCount, 2);
  assert.equal(page.data.detail.status, "WAIT_CALLBACK");
  assert.equal(page.data.prepayOrderNo, "PRE-TRUSTED");
  page.onUnload();
});

test("keeps a manual query result when an older automatic refresh finishes later", async () => {
  const automaticRequest = deferred();
  paymentApi.getDetail = () => automaticRequest.promise;
  paymentApi.query = () => Promise.resolve(paymentDetail("SUCCESS"));
  const page = createPage();
  page.pageActive = true;
  page.detailRequestVersion = 0;
  page.autoRefreshAttempts = 0;
  page.setData({
    paymentOrderId: "PAY-1",
    prepayOrderNo: "PRE-TRUSTED",
    detail: paymentDetail(),
    loading: false,
    canQuery: true
  });

  const automaticRefresh = page.refreshDetailInBackground();
  await page.queryPayment();
  automaticRequest.resolve(paymentDetail("WAIT_CALLBACK"));
  await automaticRefresh;

  assert.equal(page.data.detail.status, "SUCCESS");
  assert.match(page.data.successMessage, /已完成主动查单/);
  page.onUnload();
});

test("reloads current detail after a manual query is interrupted by hiding", async () => {
  const queryRequest = deferred();
  paymentApi.query = () => queryRequest.promise;
  paymentApi.getDetail = () => Promise.resolve(paymentDetail("SUCCESS"));
  const page = createPage();
  page.pageActive = true;
  page.detailRequestVersion = 0;
  page.autoRefreshAttempts = 0;
  page.setData({
    paymentOrderId: "PAY-1",
    prepayOrderNo: "PRE-TRUSTED",
    detail: paymentDetail(),
    loading: false,
    canQuery: true
  });

  const query = page.queryPayment();
  page.onHide();
  page.onShow();
  await flushPromises();
  queryRequest.resolve(paymentDetail("WAIT_CALLBACK"));
  await query;

  assert.equal(page.data.detail.status, "SUCCESS");
  assert.equal(page.data.actionName, "");
  page.onUnload();
});

test("rejects an automatic refresh response for another prepay order", async () => {
  paymentApi.getDetail = () => Promise.resolve({
    ...paymentDetail("SUCCESS"),
    prepayOrderNo: "PRE-OTHER"
  });
  const page = createPage();
  page.pageActive = true;
  page.detailRequestVersion = 0;
  page.autoRefreshAttempts = 0;
  page.setData({
    paymentOrderId: "PAY-1",
    prepayOrderNo: "PRE-TRUSTED",
    detail: paymentDetail(),
    loading: false
  });

  await page.refreshDetailInBackground();

  assert.equal(page.data.detail.status, "WAIT_CALLBACK");
  assert.equal(page.data.detail.prepayOrderNo, "PRE-TRUSTED");
  page.onUnload();
});
