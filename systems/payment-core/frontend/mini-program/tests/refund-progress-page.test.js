const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { join } = require("node:path");
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

function deferred() {
  let resolve;
  const promise = new Promise((resolvePromise) => {
    resolve = resolvePromise;
  });
  return { promise, resolve };
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

test("prepares and renders the authoritative channel acceptance time", () => {
  const source = readFileSync(join(__dirname, "../pages/refund-progress/index.wxml"), "utf8");
  assert.match(source, /渠道受理时间.*detail\.channelSubmittedAtDisplay/);
});

test("describes stale detail polling as refresh rather than a refund retry", () => {
  const source = readFileSync(join(__dirname, "../pages/refund-progress/index.wxml"), "utf8");
  assert.match(source, /等待自动刷新或手动刷新/);
  assert.doesNotMatch(source, /等待自动重试/);
});

test("renders the preserved customer and payment method facts", () => {
  const source = readFileSync(join(__dirname, "../pages/refund-progress/index.wxml"), "utf8");
  assert.match(source, /客户名称.*detail\.customerName/);
  assert.match(source, /支付方式.*detail\.paymentMethod/);
  assert.match(source, /支付场景.*detail\.payScene/);
});

test("renders the authoritative original paid amount", () => {
  const source = readFileSync(join(__dirname, "../pages/refund-progress/index.wxml"), "utf8");
  assert.match(source, /原支付金额.*detail\.paidAmount/);
});

function refundDetail(status = "PROCESSING") {
  return {
    refundOrderId: "REF-1",
    paymentOrderId: "PAY-1",
    businessChainId: "CHAIN-PAY-1",
    orderNo: "ORD-1",
    customerName: "张三",
    paymentMethod: "微信支付",
    payScene: "BALANCE_PAY_MINI_PROGRAM",
    refundAmount: "¥20.00",
    paidAmount: "¥88.00",
    refundMethod: "原路退款",
    refundReason: "客户申请",
    appliedAt: "2026-09-23T10:00:00.000",
    channelSubmittedAt: "2026-09-23T10:00:30.000",
    successAt: status === "SUCCESS" ? "2026-09-23T10:01:00.000" : null,
    terminalAt: ["SUCCESS", "FAIL"].includes(status) ? "2026-09-23T10:01:01.000" : null,
    status,
    statusType: status === "SUCCESS" ? "success" : status === "FAIL" ? "danger" : "warn",
    operationLogs: [{ logNo: "LOG-1", actionName: "退款处理中", fromStatus: "REVIEWING", toStatus: status, createdAt: "2026-09-23 10:00:00" }],
    attempts: [{ refundAttemptNo: "ATT-1", attemptStatus: status, channelCode: "WX_H5", createdAt: "2026-09-23 10:00:01" }]
  };
}

test("does not query a refund for an unsupported external scene", async () => {
  let requestCount = 0;
  paymentApi.getRefundDetail = () => {
    requestCount += 1;
    return Promise.resolve(refundDetail());
  };
  for (const options of [
    { refundOrderId: "REF-1", scene: "__proto__" },
    { refundOrderId: "REF-1", payScene: "HOME_CLEAN_MINI_PROGRAM" },
    { refundOrderId: "REF-1", scene: "balance-pay", payScene: "WALLET_RECHARGE_MINI_PROGRAM" }
  ]) {
    const page = createPage();
    page.onLoad(options);
    await page.loadDetail();
    await page.refreshDetail();
    assert.equal(page.data.detail, null);
    assert.match(page.data.errorMessage, /不支持该业务场景/);
    page.onHide();
    page.onShow();
    page.onUnload();
  }
  assert.equal(requestCount, 0);
});

test("loads a matching refund detail and builds a readable timeline", async () => {
  let requestedPayScene = "";
  paymentApi.getRefundDetail = (_refundOrderId, payScene) => {
    requestedPayScene = payScene;
    return Promise.resolve(refundDetail());
  };
  const page = createPage();
  page.onLoad({ refundOrderId: "REF-1", scene: "balance-pay" });
  await flushPromises();

  assert.equal(requestedPayScene, "BALANCE_PAY_MINI_PROGRAM");
  assert.equal(page.data.detail.refundOrderId, "REF-1");
  assert.equal(page.data.meta.title, "退款处理中");
  assert.equal(page.data.detail.appliedAtDisplay, "2026-09-23 10:00:00.000");
  assert.equal(page.data.detail.terminalAtDisplay, "-");
  assert.equal(page.data.detail.channelSubmittedAtDisplay, "2026-09-23 10:00:30.000");
  assert.equal(page.data.timeline.length, 2);
  assert.equal(page.data.loading, false);
  page.onUnload();
});

test("shows unknown channel acceptance as pending verification until refreshed", async () => {
  let requestCount = 0;
  paymentApi.getRefundDetail = () => {
    requestCount += 1;
    const detail = refundDetail();
    if (requestCount === 1) {
      detail.channelSubmittedAt = null;
      detail.attempts[0].attemptStatus = "UNKNOWN";
    }
    return Promise.resolve(detail);
  };
  const page = createPage();
  page.onLoad({ refundOrderId: "REF-1", scene: "balance-pay" });
  await flushPromises();

  assert.equal(page.data.detail.status, "PROCESSING");
  assert.equal(page.data.meta.title, "退款渠道结果待核查");
  assert.match(page.data.meta.hint, /渠道是否受理尚未确认/);
  assert.doesNotMatch(page.data.meta.hint, /已进入渠道处理/);

  await page.refreshDetail({ type: "tap" });
  assert.equal(page.data.meta.title, "退款处理中");
  page.onUnload();
});

test("offers a retry after the initial refund detail request fails", async () => {
  const source = readFileSync(join(__dirname, "../pages/refund-progress/index.wxml"), "utf8");
  assert.match(source, /重试加载退款进度<\/button>/);
  let requestCount = 0;
  paymentApi.getRefundDetail = () => {
    requestCount += 1;
    return requestCount === 1 ? Promise.reject(new Error("网络不可用")) : Promise.resolve(refundDetail());
  };
  const page = createPage();
  page.onLoad({ refundOrderId: "REF-1" });
  await flushPromises();
  assert.equal(page.data.detail, null);
  assert.equal(page.data.meta.title, "退款进度待核查");
  assert.match(page.data.errorMessage, /网络不可用/);

  await page.loadDetail();
  assert.equal(requestCount, 2);
  assert.equal(page.data.detail.status, "PROCESSING");
  assert.equal(page.data.meta.title, "退款处理中");
  page.onUnload();
});

test("marks a failed automatic refund refresh stale and clears it after a matching retry", async () => {
  const source = readFileSync(join(__dirname, "../pages/refund-progress/index.wxml"), "utf8");
  assert.match(source, /staleDetail \? '待核查' : detail\.status/);
  assert.match(source, /wx:if="\{\{staleDetail\}\}"[^>]*>最新退款进度未能确认/);
  paymentApi.getRefundDetail = () => Promise.resolve(refundDetail());
  const page = createPage();
  page.onLoad({ refundOrderId: "REF-1" });
  await flushPromises();

  paymentApi.getRefundDetail = () => Promise.reject(new Error("网络不可用"));
  await page.refreshDetail(true);
  assert.equal(page.data.detail.status, "PROCESSING");
  assert.equal(page.data.staleDetail, true);
  assert.equal(page.data.meta.title, "退款进度待核查");
  assert.equal(page.pollAttempts, 1);
  assert.ok(page.pollTimer);
  assert.match(page.data.errorMessage, /自动刷新未能确认最新退款状态/);

  paymentApi.getRefundDetail = () => Promise.resolve(refundDetail());
  await page.refreshDetail(true);
  assert.equal(page.data.staleDetail, false);
  assert.equal(page.data.meta.title, "退款处理中");
  assert.equal(page.data.errorMessage, "");
  page.onUnload();
});

test("does not present an old successful refund as current after a failed manual refresh", async () => {
  const source = readFileSync(join(__dirname, "../pages/refund-progress/index.wxml"), "utf8");
  assert.match(source, /disabled="\{\{refreshing \|\| staleDetail\}\}" bindtap="backToEntry"/);
  paymentApi.getRefundDetail = () => Promise.resolve(refundDetail("SUCCESS"));
  const page = createPage();
  page.onLoad({ refundOrderId: "REF-1" });
  await flushPromises();
  assert.equal(page.data.meta.title, "退款成功");

  paymentApi.getRefundDetail = () => Promise.reject(new Error("网络不可用"));
  await page.refreshDetail();
  assert.equal(page.data.detail.status, "SUCCESS");
  assert.equal(page.data.staleDetail, true);
  assert.equal(page.data.meta.title, "退款进度待核查");
  const originalWx = global.wx;
  let redirected = false;
  global.wx = { reLaunch: () => { redirected = true; } };
  try {
    page.backToEntry();
    assert.equal(redirected, false);
  } finally {
    global.wx = originalWx;
  }

  paymentApi.getRefundDetail = () => Promise.resolve(refundDetail("SUCCESS"));
  await page.refreshDetail();
  assert.equal(page.data.staleDetail, false);
  assert.equal(page.data.meta.title, "退款成功");
  assert.equal(page.data.errorMessage, "");
  page.onUnload();
});

test("shows bounded polling exhaustion and only resumes after a successful manual refresh", async () => {
  const source = readFileSync(join(__dirname, "../pages/refund-progress/index.wxml"), "utf8");
  assert.match(source, /wx:if="\{\{pollingPaused\}\}"[^>]*>自动刷新已暂停/);
  paymentApi.getRefundDetail = () => Promise.resolve(refundDetail());
  const page = createPage();
  page.onLoad({ refundOrderId: "REF-1" });
  await flushPromises();

  for (let attempt = 0; attempt < 5; attempt += 1) {
    await page.refreshDetail(true);
  }
  assert.equal(page.pollAttempts, 5);
  assert.equal(page.data.pollingPaused, true);
  assert.equal(page.pollTimer, null);

  paymentApi.getRefundDetail = () => Promise.reject(new Error("网络不可用"));
  await page.refreshDetail();
  assert.equal(page.data.detail.status, "PROCESSING");
  assert.equal(page.data.pollingPaused, true);
  assert.equal(page.pollAttempts, 5);
  assert.match(page.data.errorMessage, /上次成功查询状态.*网络不可用/);

  paymentApi.getRefundDetail = () => Promise.resolve(refundDetail());
  await page.refreshDetail();
  assert.equal(page.pollAttempts, 0);
  assert.equal(page.data.pollingPaused, false);
  assert.ok(page.pollTimer);
  page.onUnload();
});

test("reentry keeps a verified snapshot when loading fails and ignores an older request", async () => {
  paymentApi.getRefundDetail = () => Promise.resolve(refundDetail());
  const page = createPage();
  page.onLoad({ refundOrderId: "REF-1" });
  await flushPromises();
  page.pollAttempts = 5;
  page.schedulePolling();
  const olderRequest = deferred();
  paymentApi.getRefundDetail = () => olderRequest.promise;
  const refresh = page.refreshDetail();
  page.onHide();
  assert.equal(page.data.refreshing, false);

  paymentApi.getRefundDetail = () => Promise.reject(new Error("网络不可用"));
  page.onShow();
  await flushPromises();
  olderRequest.resolve(refundDetail("SUCCESS"));
  await refresh;
  assert.equal(page.data.detail.status, "PROCESSING");
  assert.equal(page.data.staleDetail, true);
  assert.equal(page.data.meta.title, "退款进度待核查");
  assert.equal(page.pollAttempts, 5);
  assert.equal(page.data.pollingPaused, true);
  assert.match(page.data.errorMessage, /上次成功查询状态.*网络不可用/);

  paymentApi.getRefundDetail = () => Promise.resolve({ ...refundDetail("SUCCESS"), paymentOrderId: "PAY-OTHER" });
  await page.loadDetail();
  assert.equal(page.data.detail.paymentOrderId, "PAY-1");
  assert.equal(page.pollAttempts, 5);

  paymentApi.getRefundDetail = () => Promise.resolve(refundDetail("SUCCESS"));
  await page.loadDetail();
  assert.equal(page.data.detail.status, "SUCCESS");
  assert.equal(page.data.staleDetail, false);
  assert.equal(page.data.meta.title, "退款成功");
  assert.equal(page.pollAttempts, 0);
  assert.equal(page.data.pollingPaused, false);
  page.onUnload();
});

test("trims refund route and response identifiers before binding progress state", async () => {
  let requestedRefundOrderId = "";
  paymentApi.getRefundDetail = (refundOrderId) => {
    requestedRefundOrderId = refundOrderId;
    return Promise.resolve({
      ...refundDetail(),
      refundOrderId: " REF-1 ",
      paymentOrderId: " PAY-1 ",
      businessChainId: " CHAIN-PAY-1 "
    });
  };
  const page = createPage();
  page.onLoad({ refundOrderId: "%20REF-1%20" });
  await flushPromises();

  assert.equal(requestedRefundOrderId, "REF-1");
  assert.equal(page.data.refundOrderId, "REF-1");
  assert.equal(page.data.detail.refundOrderId, "REF-1");
  assert.equal(page.data.detail.paymentOrderId, "PAY-1");
  assert.equal(page.data.detail.businessChainId, "CHAIN-PAY-1");
  page.onUnload();
});

test("preserves the selected business scene when returning to the entry page", () => {
  const originalWx = global.wx;
  let redirectedUrl = "";
  global.wx = { reLaunch: ({ url }) => { redirectedUrl = url; } };
  try {
    const page = createPage();
    page.pageActive = true;
    page.data.refundOrderId = "REF-1";
    page.data.scene = { key: "recharge" };
    page.backToEntry();
    assert.equal(redirectedUrl, "/pages/entry/index?scene=recharge");
  } finally {
    global.wx = originalWx;
  }
});

test("keeps the terminal time visible after a successful refund refresh", async () => {
  paymentApi.getRefundDetail = () => Promise.resolve(refundDetail("SUCCESS"));
  const page = createPage();
  page.onLoad({ refundOrderId: "REF-1" });
  await flushPromises();

  assert.equal(page.data.detail.successAtDisplay, "2026-09-23 10:01:00.000");
  assert.equal(page.data.detail.terminalAtDisplay, "2026-09-23 10:01:01.000");
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

test("fails closed when a refresh changes the payment identity or refund amount", async () => {
  let requestCount = 0;
  paymentApi.getRefundDetail = () => {
    requestCount += 1;
    return Promise.resolve(requestCount === 1
      ? refundDetail()
      : { ...refundDetail("SUCCESS"), paymentOrderId: "PAY-OTHER", refundAmount: "¥19.99" });
  };
  const page = createPage();
  page.onLoad({ refundOrderId: "REF-1" });
  await flushPromises();
  page.refreshDetail({ type: "tap" });
  await flushPromises();

  assert.equal(page.data.detail.status, "PROCESSING");
  assert.equal(page.data.detail.paymentOrderId, "PAY-1");
  assert.match(page.data.errorMessage, /不一致/);
  assert.equal(page.data.refreshing, false);
  page.onUnload();
});

test("fails closed when a refresh changes established refund lifecycle timestamps", async () => {
  let requestCount = 0;
  paymentApi.getRefundDetail = () => {
    requestCount += 1;
    return Promise.resolve(requestCount === 1
      ? refundDetail()
      : { ...refundDetail("SUCCESS"), appliedAt: "2026-09-23 10:00:01" });
  };
  const page = createPage();
  page.onLoad({ refundOrderId: "REF-1" });
  await flushPromises();
  page.refreshDetail({ type: "tap" });
  await flushPromises();

  assert.equal(page.data.detail.status, "PROCESSING");
  assert.equal(page.data.detail.appliedAtDisplay, "2026-09-23 10:00:00.000");
  assert.match(page.data.errorMessage, /不一致/);
  page.onUnload();
});

test("fails closed when a refresh changes refund financial or reason facts", async () => {
  let requestCount = 0;
  paymentApi.getRefundDetail = () => {
    requestCount += 1;
    return Promise.resolve(requestCount === 1
      ? refundDetail()
      : { ...refundDetail("SUCCESS"), paidAmount: "¥87.99", refundMethod: "余额退款", refundReason: "商户原因" });
  };
  const page = createPage();
  page.onLoad({ refundOrderId: "REF-1" });
  await flushPromises();
  page.refreshDetail({ type: "tap" });
  await flushPromises();

  assert.equal(page.data.detail.status, "PROCESSING");
  assert.equal(page.data.detail.paidAmount, "¥88.00");
  assert.equal(page.data.detail.refundMethod, "原路退款");
  assert.match(page.data.errorMessage, /不一致/);
  page.onUnload();
});

test("fails closed when a terminal refresh changes refund channel facts", async () => {
  let requestCount = 0;
  paymentApi.getRefundDetail = () => {
    requestCount += 1;
    return Promise.resolve(requestCount === 1
      ? { ...refundDetail(), channelCode: "WX_H5", channelRefundNo: "CH-REF-001" }
      : { ...refundDetail("SUCCESS"), channelCode: "ALI_H5", channelRefundNo: "CH-REF-002" });
  };
  const page = createPage();
  page.onLoad({ refundOrderId: "REF-1" });
  await flushPromises();
  page.refreshDetail({ type: "tap" });
  await flushPromises();

  assert.equal(page.data.detail.status, "PROCESSING");
  assert.equal(page.data.detail.channelCode, "WX_H5");
  assert.equal(page.data.detail.channelRefundNo, "CH-REF-001");
  assert.match(page.data.errorMessage, /不一致/);
  page.onUnload();
});

test("fails closed when a refresh changes the customer fact", async () => {
  let requestCount = 0;
  paymentApi.getRefundDetail = () => {
    requestCount += 1;
    return Promise.resolve(requestCount === 1
      ? refundDetail()
      : { ...refundDetail(), customerName: "李四" });
  };
  const page = createPage();
  page.onLoad({ refundOrderId: "REF-1" });
  await flushPromises();
  await page.refreshDetail();

  assert.equal(page.data.detail.customerName, "张三");
  assert.match(page.data.errorMessage, /不一致/);
  page.onUnload();
});

test("fails closed when a refresh changes the payment method fact", async () => {
  let requestCount = 0;
  paymentApi.getRefundDetail = () => {
    requestCount += 1;
    return Promise.resolve(requestCount === 1
      ? refundDetail()
      : { ...refundDetail(), paymentMethod: "支付宝" });
  };
  const page = createPage();
  page.onLoad({ refundOrderId: "REF-1" });
  await flushPromises();
  await page.refreshDetail();

  assert.equal(page.data.detail.paymentMethod, "微信支付");
  assert.match(page.data.errorMessage, /不一致/);
  page.onUnload();
});

test("fails closed when a refresh changes the payment scene fact", async () => {
  let requestCount = 0;
  paymentApi.getRefundDetail = () => {
    requestCount += 1;
    return Promise.resolve(requestCount === 1
      ? refundDetail()
      : { ...refundDetail(), payScene: "WALLET_RECHARGE_MINI_PROGRAM" });
  };
  const page = createPage();
  page.onLoad({ refundOrderId: "REF-1" });
  await flushPromises();
  await page.refreshDetail();

  assert.equal(page.data.detail.payScene, "BALANCE_PAY_MINI_PROGRAM");
  assert.match(page.data.errorMessage, /不一致/);
  page.onUnload();
});

test("fails closed when a refresh changes the business chain", async () => {
  let requestCount = 0;
  paymentApi.getRefundDetail = () => {
    requestCount += 1;
    return Promise.resolve(requestCount === 1
      ? refundDetail()
      : { ...refundDetail("SUCCESS"), businessChainId: "CHAIN-OTHER" });
  };
  const page = createPage();
  page.onLoad({ refundOrderId: "REF-1" });
  await flushPromises();
  page.refreshDetail({ type: "tap" });
  await flushPromises();

  assert.equal(page.data.detail.status, "PROCESSING");
  assert.equal(page.data.detail.businessChainId, "CHAIN-PAY-1");
  assert.match(page.data.errorMessage, /不一致/);
  page.onUnload();
});

test("fails closed when a refresh returns an over-precision refund amount", async () => {
  let requestCount = 0;
  paymentApi.getRefundDetail = () => {
    requestCount += 1;
    return Promise.resolve(requestCount === 1
      ? refundDetail()
      : { ...refundDetail("SUCCESS"), refundAmount: "¥20.001" });
  };
  const page = createPage();
  page.onLoad({ refundOrderId: "REF-1" });
  await flushPromises();
  page.refreshDetail({ type: "tap" });
  await flushPromises();

  assert.equal(page.data.detail.status, "PROCESSING");
  assert.equal(page.data.detail.refundAmount, "¥20.00");
  assert.match(page.data.errorMessage, /不一致/);
  page.onUnload();
});

test("fails closed when a refresh changes a large refund amount by one cent", async () => {
  let requestCount = 0;
  paymentApi.getRefundDetail = () => {
    requestCount += 1;
    return Promise.resolve(requestCount === 1
      ? { ...refundDetail(), refundAmount: "¥9007199254740992.01" }
      : { ...refundDetail("SUCCESS"), refundAmount: "¥9007199254740992.02" });
  };
  const page = createPage();
  page.onLoad({ refundOrderId: "REF-1" });
  await flushPromises();
  page.refreshDetail({ type: "tap" });
  await flushPromises();

  assert.equal(page.data.detail.refundAmount, "¥9007199254740992.01");
  assert.match(page.data.errorMessage, /不一致/);
  page.onUnload();
});
