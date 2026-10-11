const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { join } = require("node:path");
const test = require("node:test");

const { paymentApi } = require("../utils/request");
const { PAYMENT_DETAIL_REFRESH_MAX_ATTEMPTS } = require("../utils/payment");

test("checks the attempt completion, channel transaction and bound channel before offering a query", () => {
  const source = readFileSync(join(__dirname, "../pages/result/index.js"), "utf8");
  assert.match(source, /canQueryPayment\(\s*mergedDetail\.status, mergedDetail\.latestAttemptStatus,\s*mergedDetail\.latestAttemptCompletedAt, mergedDetail\.channelTransactionNo,\s*mergedDetail\.channel/);
});

test("renders the latest payment attempt completion time", () => {
  const source = readFileSync(join(__dirname, "../pages/result/index.wxml"), "utf8");
  assert.match(source, /最近尝试完成时间.*detail\.latestAttemptCompletedAt/);
});

test("binds result loads and actions to the selected mini-program scene", () => {
  const source = readFileSync(join(__dirname, "../pages/result/index.js"), "utf8");
  assert.match(source, /paymentApi\.getDetail\([\s\S]*this\.data\.scene\.payScene/);
  assert.match(source, /paymentApi\.query\([\s\S]*this\.data\.scene\.payScene/);
  assert.match(source, /paymentApi\.close\([\s\S]*this\.data\.scene\.payScene/);
  assert.match(source, /customerName: this\.data\.detail\.customerName,[\s\S]*payScene: this\.data\.detail\.payScene/);
  assert.match(source, /normalizedSnapshot\.payScene[\s\S]*normalizedDetail\.payScene/);
});

test("renders the preserved bill and customer facts", () => {
  const source = readFileSync(join(__dirname, "../pages/result/index.wxml"), "utf8");
  assert.match(source, /账单号.*detail\.billNo/);
  assert.match(source, /客户名称.*detail\.customerName/);
});

test("renders the server-provided channel handling suggestion", () => {
  const source = readFileSync(join(__dirname, "../pages/result/index.wxml"), "utf8");
  assert.match(source, /渠道处理建议[\s\S]*detail\.handlingSuggestion/);
});

test("renders server-provided retry and manual-intervention decisions", () => {
  const source = readFileSync(join(__dirname, "../pages/result/index.wxml"), "utf8");
  assert.match(source, /是否可重试[\s\S]*detail\.retryable/);
  assert.match(source, /需要人工介入[\s\S]*detail\.manualInterventionRequired/);
});

test("renders the server-provided standardized error message", () => {
  const source = readFileSync(join(__dirname, "../pages/result/index.wxml"), "utf8");
  assert.match(source, /标准错误说明[\s\S]*detail\.standardizedMessage/);
});

test("renders whether the return-code mapping matched", () => {
  const source = readFileSync(join(__dirname, "../pages/result/index.wxml"), "utf8");
  assert.match(source, /映射命中[\s\S]*detail\.returnCodeMappingMatched/);
  assert.match(source, /detail\.returnCodeMappingMatched \? '是' : '否'/);
});

test("renders the return-code mapping version", () => {
  const source = readFileSync(join(__dirname, "../pages/result/index.wxml"), "utf8");
  assert.match(source, /映射版本[\s\S]*detail\.returnCodeMappingVersion/);
});

test("renders the server-provided query source", () => {
  const source = readFileSync(join(__dirname, "../pages/result/index.wxml"), "utf8");
  assert.match(source, /查单来源[\s\S]*detail\.querySource/);
});

test("renders the latest payment attempt operational snapshot", () => {
  const source = readFileSync(join(__dirname, "../pages/result/index.wxml"), "utf8");
  assert.match(source, /发起终端[\s\S]*detail\.latestTerminal/);
  assert.match(source, /客户端 IP[\s\S]*detail\.latestClientIp/);
  assert.match(source, /幂等键[\s\S]*detail\.latestIdempotencyKey/);
});

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
    businessChainId: "CHAIN-PAY-1",
    prepayOrderNo: "PRE-TRUSTED",
    billNo: "BILL-1",
    orderNo: "ORDER-1",
    customerName: "张三",
    payScene: "BALANCE_PAY_MINI_PROGRAM",
    amount: "68.00",
    createdAt: "2026-09-27 10:00:00",
    paymentMethod: "微信支付",
    channel: "wx_h5",
    status,
    successAt: status === "SUCCESS" ? "2026-09-27 10:01:00" : null,
    latestAttemptCompletedAt: status === "SUCCESS" ? "2026-09-27 10:00:30" : null,
    latestAttemptStatus: "等待回调",
    channelTransactionNo: "WX-1",
    refundOrderId: status === "SUCCESS" ? "REF-1" : null
  };
}

test("keeps PAYING result read-only until channel acceptance is confirmed", async () => {
  let status = "PAYING";
  let queryCalls = 0;
  paymentApi.getDetail = () => Promise.resolve({
    ...paymentDetail(status),
    channelTransactionNo: status === "PAYING" ? null : "WX-1",
    latestAttemptStatus: status === "PAYING" ? "渠道提交中" : "处理中"
  });
  paymentApi.query = () => { queryCalls += 1; return Promise.resolve(paymentDetail()); };
  const page = createPage();
  page.onLoad({ paymentOrderId: "PAY-1", prepayOrderNo: "PRE-TRUSTED", scene: "balance-pay" });
  try {
    await flushPromises();
    assert.equal(page.data.meta.title, "支付提交处理中");
    assert.match(page.data.meta.hint, /刷新/);
    assert.equal(page.data.canQuery, false);
    assert.equal(page.data.canClose, false);
    await page.queryPayment();
    assert.equal(queryCalls, 0);

    status = "WAIT_CALLBACK";
    await page.loadDetail();
    assert.equal(page.data.meta.title, "支付处理中");
    assert.equal(page.data.canQuery, true);
  } finally {
    page.onUnload();
  }
});

test("does not load or act on a payment result for an unsupported external scene", async () => {
  let requestCount = 0;
  paymentApi.getDetail = () => {
    requestCount += 1;
    return Promise.resolve(paymentDetail());
  };
  for (const options of [
    { paymentOrderId: "PAY-1", scene: "unknown" },
    { paymentOrderId: "PAY-1", payScene: "HOME_CLEAN_MINI_PROGRAM" },
    { paymentOrderId: "PAY-1", scene: "balance-pay", payScene: "WALLET_RECHARGE_MINI_PROGRAM" }
  ]) {
    const page = createPage();
    page.onLoad(options);
    await page.loadDetail();
    await page.queryPayment();
    assert.equal(page.data.detail, null);
    assert.match(page.data.errorMessage, /不支持该业务场景/);
    page.onHide();
    page.onShow();
    page.onUnload();
  }
  assert.equal(requestCount, 0);
});

test("reloads after an initial detail request is invalidated while hidden", async () => {
  const firstRequest = deferred();
  let requestCount = 0;
  paymentApi.getDetail = () => {
    requestCount += 1;
    return requestCount === 1 ? firstRequest.promise : Promise.resolve(paymentDetail());
  };
  const page = createPage();

  page.onLoad({ paymentOrderId: "PAY-1", prepayOrderNo: "PRE-TRUSTED" });
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

test("trims result route and detail identifiers before binding payment state", async () => {
  let requestedPaymentOrderId = "";
  paymentApi.getDetail = (paymentOrderId) => {
    requestedPaymentOrderId = paymentOrderId;
    return Promise.resolve({
      ...paymentDetail(),
      paymentOrderId: " PAY-1 ",
      businessChainId: " CHAIN-PAY-1 ",
      prepayOrderNo: " PRE-TRUSTED ",
      orderNo: " ORDER-1 "
    });
  };
  const page = createPage();
  page.onLoad({ paymentOrderId: "%20PAY-1%20", prepayOrderNo: "%20PRE-TRUSTED%20" });
  await flushPromises();

  assert.equal(requestedPaymentOrderId, "PAY-1");
  assert.equal(page.data.paymentOrderId, "PAY-1");
  assert.equal(page.data.prepayOrderNo, "PRE-TRUSTED");
  assert.equal(page.data.detail.paymentOrderId, "PAY-1");
  assert.equal(page.data.detail.businessChainId, "CHAIN-PAY-1");
  page.onUnload();
});

test("rejects an initial detail response for another prepay order", async () => {
  paymentApi.getDetail = () => Promise.resolve({ ...paymentDetail(), prepayOrderNo: "PRE-OTHER" });
  const page = createPage();

  page.onLoad({ paymentOrderId: "PAY-1", prepayOrderNo: "PRE-TRUSTED" });
  await flushPromises();

  assert.equal(page.data.detail, null);
  assert.match(page.data.errorMessage, /当前预付单不一致/);
  page.onUnload();
});

test("rejects an initial detail response for another payment method", async () => {
  paymentApi.getDetail = () => Promise.resolve({ ...paymentDetail(), paymentMethod: "支付宝" });
  const page = createPage();

  page.onLoad({ paymentOrderId: "PAY-1", paymentMethod: "微信支付" });
  await flushPromises();

  assert.equal(page.data.detail, null);
  assert.match(page.data.errorMessage, /当前支付方式不一致/);
  page.onUnload();
});

test("offers a retry when the initial payment detail cannot load", async () => {
  const source = readFileSync(join(__dirname, "../pages/result/index.wxml"), "utf8");
  assert.match(source, /重试加载支付结果<\/button>/);
  let requests = 0;
  paymentApi.getDetail = () => {
    requests += 1;
    return requests === 1 ? Promise.reject(new Error("网络不可用")) : Promise.resolve(paymentDetail());
  };
  const page = createPage();
  page.onLoad({ paymentOrderId: "PAY-1", prepayOrderNo: "PRE-TRUSTED" });
  await flushPromises();
  assert.equal(page.data.detail, null);
  assert.equal(page.data.meta.title, "支付结果待核查");
  assert.match(page.data.errorMessage, /网络不可用/);

  await page.loadDetail();
  assert.equal(requests, 2);
  assert.equal(page.data.detail.status, "WAIT_CALLBACK");
  assert.equal(page.data.meta.title, "支付处理中");
  page.onUnload();
});

test("marks a failed automatic payment refresh stale and restores actions after a matching retry", async () => {
  const source = readFileSync(join(__dirname, "../pages/result/index.wxml"), "utf8");
  assert.match(source, /staleDetail \? '待核查' : detail\.status/);
  assert.match(source, /wx:if="\{\{staleDetail\}\}"[^>]*>最新支付结果未能确认/);
  paymentApi.getDetail = () => Promise.resolve(paymentDetail());
  const page = createPage();
  page.onLoad({ paymentOrderId: "PAY-1", prepayOrderNo: "PRE-TRUSTED" });
  await flushPromises();

  paymentApi.getDetail = () => Promise.reject(new Error("网络不可用"));
  await page.refreshDetailInBackground();
  assert.equal(page.data.detail.status, "WAIT_CALLBACK");
  assert.equal(page.data.staleDetail, true);
  assert.equal(page.data.meta.title, "支付结果待核查");
  assert.equal(page.data.canQuery, false);
  assert.equal(page.data.canClose, false);
  assert.equal(page.autoRefreshAttempts, 1);
  assert.ok(page.autoRefreshTimer);

  paymentApi.getDetail = () => Promise.resolve(paymentDetail());
  await page.refreshDetailInBackground();
  assert.equal(page.data.staleDetail, false);
  assert.equal(page.data.meta.title, "支付处理中");
  assert.equal(page.data.canQuery, true);
  assert.equal(page.data.errorMessage, "");
  page.onUnload();
});

test("does not present an old payment success after manual refresh fails", async () => {
  const source = readFileSync(join(__dirname, "../pages/result/index.wxml"), "utf8");
  assert.match(source, /disabled="\{\{actionName \|\| staleDetail \|\| !prepayOrderNo\}\}" bindtap="backToCashier"/);
  paymentApi.getDetail = () => Promise.resolve(paymentDetail("SUCCESS"));
  const page = createPage();
  page.onLoad({ paymentOrderId: "PAY-1", prepayOrderNo: "PRE-TRUSTED" });
  await flushPromises();
  assert.equal(page.data.meta.title, "支付成功");

  paymentApi.getDetail = () => Promise.reject(new Error("网络不可用"));
  await page.loadDetail();
  assert.equal(page.data.detail.status, "SUCCESS");
  assert.equal(page.data.staleDetail, true);
  assert.equal(page.data.meta.title, "支付结果待核查");
  assert.equal(page.data.canQuery, false);
  const originalWx = global.wx;
  let redirected = false;
  global.wx = { redirectTo: () => { redirected = true; }, reLaunch: () => { redirected = true; } };
  try {
    page.backToCashier();
    page.backToEntry();
    assert.equal(redirected, false);
  } finally {
    global.wx = originalWx;
  }

  paymentApi.getDetail = () => Promise.resolve(paymentDetail("SUCCESS"));
  await page.loadDetail();
  assert.equal(page.data.staleDetail, false);
  assert.equal(page.data.meta.title, "支付成功");
  assert.equal(page.data.errorMessage, "");
  page.onUnload();
});

test("keeps the last verified result and paused warning when a manual refresh fails", async () => {
  const source = readFileSync(join(__dirname, "../pages/result/index.wxml"), "utf8");
  assert.match(source, /wx:if="\{\{autoRefreshPaused\}\}"[^>]*>自动刷新已暂停/);
  paymentApi.getDetail = () => Promise.reject(new Error("网络不可用"));
  const page = createPage();
  page.pageActive = true;
  page.detailRequestVersion = 0;
  page.autoRefreshAttempts = PAYMENT_DETAIL_REFRESH_MAX_ATTEMPTS;
  page.setData({ paymentOrderId: "PAY-1", prepayOrderNo: "PRE-TRUSTED", detail: paymentDetail("RISK_REVIEW"), loading: false });
  page.scheduleAutoRefresh();
  assert.equal(page.data.autoRefreshPaused, true);

  await page.loadDetail();
  assert.equal(page.data.detail.status, "RISK_REVIEW");
  assert.equal(page.data.autoRefreshPaused, true);
  assert.equal(page.autoRefreshAttempts, PAYMENT_DETAIL_REFRESH_MAX_ATTEMPTS);
  assert.match(page.data.errorMessage, /上次成功查询状态.*网络不可用/);
  page.onUnload();
});

test("manual refresh verifies continuity and restarts bounded polling only on success", async () => {
  paymentApi.getDetail = () => Promise.resolve({ ...paymentDetail("RISK_REVIEW"), prepayOrderNo: "PRE-OTHER" });
  const page = createPage();
  page.pageActive = true;
  page.detailRequestVersion = 0;
  page.autoRefreshAttempts = PAYMENT_DETAIL_REFRESH_MAX_ATTEMPTS;
  page.setData({ paymentOrderId: "PAY-1", prepayOrderNo: "PRE-TRUSTED", detail: paymentDetail("RISK_REVIEW"), loading: false });

  await page.loadDetail();
  assert.equal(page.data.detail.prepayOrderNo, "PRE-TRUSTED");
  assert.equal(page.autoRefreshAttempts, PAYMENT_DETAIL_REFRESH_MAX_ATTEMPTS);
  assert.match(page.data.errorMessage, /当前预付单不一致/);

  paymentApi.getDetail = () => Promise.resolve(paymentDetail("RISK_REVIEW"));
  await page.loadDetail();
  assert.equal(page.autoRefreshAttempts, 0);
  assert.equal(page.data.autoRefreshPaused, false);
  assert.equal(page.data.errorMessage, "");
  assert.ok(page.autoRefreshTimer);
  page.onUnload();
});

test("only a successful manual channel query resumes paused polling", async () => {
  paymentApi.getDetail = () => Promise.reject(new Error("读回暂不可用"));
  const page = createPage();
  page.pageActive = true;
  page.detailRequestVersion = 0;
  page.autoRefreshAttempts = PAYMENT_DETAIL_REFRESH_MAX_ATTEMPTS;
  page.setData({
    paymentOrderId: "PAY-1",
    prepayOrderNo: "PRE-TRUSTED",
    detail: paymentDetail(),
    loading: false,
    canQuery: true
  });
  page.scheduleAutoRefresh();
  paymentApi.query = () => Promise.reject(new Error("渠道暂不可用"));
  await page.queryPayment();
  assert.equal(page.autoRefreshAttempts, PAYMENT_DETAIL_REFRESH_MAX_ATTEMPTS);
  assert.equal(page.data.autoRefreshPaused, true);
  assert.equal(page.data.staleDetail, true);
  assert.equal(page.data.canQuery, false);
  page.onUnload();

  const verifiedPage = createPage();
  verifiedPage.pageActive = true;
  verifiedPage.detailRequestVersion = 0;
  verifiedPage.autoRefreshAttempts = PAYMENT_DETAIL_REFRESH_MAX_ATTEMPTS;
  verifiedPage.setData({ paymentOrderId: "PAY-1", prepayOrderNo: "PRE-TRUSTED",
    detail: paymentDetail(), loading: false, canQuery: true });
  verifiedPage.scheduleAutoRefresh();
  paymentApi.query = () => Promise.resolve(paymentDetail());
  await verifiedPage.queryPayment();
  assert.equal(verifiedPage.autoRefreshAttempts, 0);
  assert.equal(verifiedPage.data.autoRefreshPaused, false);
  assert.ok(verifiedPage.autoRefreshTimer);
  verifiedPage.onUnload();
});

test("reads back the callback or close winner after an action response is lost", async () => {
  for (const [action, finalStatus] of [["queryPayment", "SUCCESS"], ["closePayment", "CLOSED"]]) {
    const page = createPage();
    page.pageActive = true;
    page.detailRequestVersion = 0;
    page.autoRefreshAttempts = 0;
    page.setData({ paymentOrderId: "PAY-1", prepayOrderNo: "PRE-TRUSTED",
      detail: paymentDetail(), loading: false, canQuery: true, canClose: true });
    paymentApi[action === "queryPayment" ? "query" : "close"] = () => Promise.reject(new Error("动作响应丢失"));
    let readCount = 0;
    paymentApi.getDetail = (paymentOrderId, payScene) => {
      assert.equal(paymentOrderId, "PAY-1");
      assert.equal(payScene, "BALANCE_PAY_MINI_PROGRAM");
      readCount += 1;
      return Promise.resolve(paymentDetail(finalStatus));
    };

    await page[action]();

    assert.equal(readCount, 1);
    assert.equal(page.data.detail.status, finalStatus);
    assert.equal(page.data.staleDetail, false);
    assert.equal(page.data.canClose, false);
    assert.match(page.data.errorMessage, /动作响应丢失.*支付详情已重新读取/);
    page.onUnload();
  }
});

test("locks an action result when the post-error readback fails or changes identity", async () => {
  for (const readback of [
    () => Promise.reject(new Error("读回暂不可用")),
    () => Promise.resolve({ ...paymentDetail("SUCCESS"), prepayOrderNo: "PRE-OTHER" })
  ]) {
    const page = createPage();
    page.pageActive = true;
    page.detailRequestVersion = 0;
    page.setData({ paymentOrderId: "PAY-1", prepayOrderNo: "PRE-TRUSTED",
      detail: paymentDetail(), loading: false, canQuery: true, canClose: true });
    paymentApi.query = () => Promise.reject(new Error("查单响应丢失"));
    paymentApi.getDetail = readback;

    await page.queryPayment();

    assert.equal(page.data.detail.status, "WAIT_CALLBACK");
    assert.equal(page.data.staleDetail, true);
    assert.equal(page.data.canQuery, false);
    assert.equal(page.data.canClose, false);
    assert.match(page.data.errorMessage, /查单响应丢失.*支付状态未经确认/);
    page.onUnload();
  }
});

test("ignores a delayed post-error readback after the result page hides", async () => {
  const readback = deferred();
  const page = createPage();
  page.pageActive = true;
  page.detailRequestVersion = 0;
  page.setData({ paymentOrderId: "PAY-1", prepayOrderNo: "PRE-TRUSTED",
    detail: paymentDetail(), loading: false, canQuery: true });
  paymentApi.query = () => Promise.reject(new Error("查单响应丢失"));
  paymentApi.getDetail = () => readback.promise;

  const action = page.queryPayment();
  await flushPromises();
  page.onHide();
  readback.resolve(paymentDetail("SUCCESS"));
  await action;

  assert.equal(page.data.detail.status, "WAIT_CALLBACK");
  assert.equal(page.data.actionName, "");
  page.onUnload();
});

test("keeps the trusted payment success time on a successful result", () => {
  const page = createPage();
  page.setData({ paymentOrderId: "PAY-1" });

  const detail = paymentDetail("SUCCESS");
  page.applyDetail(detail);

  assert.equal(page.data.detail.successAt, "2026-09-27 10:01:00");
  assert.equal(page.data.meta.tone, "success");
});

test("keeps full detail fields when an action returns only a status summary", () => {
  const page = createPage();
  page.setData({ paymentOrderId: "PAY-1" });
  page.applyDetail({
    ...paymentDetail(),
    paymentMethod: "微信支付",
    channel: "wx_h5"
  });

  page.applyDetail({
    ...paymentDetail("SUCCESS")
  });

  assert.equal(page.data.detail.status, "SUCCESS");
  assert.equal(page.data.detail.paymentMethod, "微信支付");
  assert.equal(page.data.detail.channel, "wx_h5");
});

test("rejects a result response that changes the loaded business order", () => {
  const page = createPage();
  page.pageActive = true;
  page.detailRequestVersion = 1;
  page.setData({
    paymentOrderId: "PAY-1",
    detail: paymentDetail(),
    prepayOrderNo: "PRE-TRUSTED"
  });

  assert.equal(page.isCurrentDetailResponse({
    paymentOrderId: "PAY-1",
    businessChainId: "CHAIN-PAY-1",
    prepayOrderNo: "PRE-TRUSTED",
    orderNo: "ORDER-1",
    amount: "68.00"
  }, 1, paymentDetail()), true);
  assert.equal(page.isCurrentDetailResponse({
    paymentOrderId: "PAY-1",
    businessChainId: "CHAIN-PAY-1",
    prepayOrderNo: "PRE-TRUSTED",
    orderNo: "ORDER-1",
    amount: "68.00"
  }, 1, { ...paymentDetail(), businessChainId: "CHAIN-OTHER" }), false);
  assert.equal(page.isCurrentDetailResponse({
    paymentOrderId: "PAY-1",
    prepayOrderNo: "PRE-TRUSTED",
    orderNo: "ORDER-1"
  }, 1, { ...paymentDetail(), orderNo: "ORDER-2" }), false);
  assert.equal(page.isCurrentDetailResponse({
    paymentOrderId: "PAY-1",
    prepayOrderNo: "PRE-TRUSTED",
    orderNo: "ORDER-1",
    amount: "68.00"
  }, 1, { ...paymentDetail(), amount: "67.99" }), false);
  assert.equal(page.isCurrentDetailResponse({
    paymentOrderId: "PAY-1",
    businessChainId: "CHAIN-PAY-1",
    prepayOrderNo: "PRE-TRUSTED",
    orderNo: "ORDER-1",
    amount: "68.00",
    paymentMethod: "微信支付"
  }, 1, { ...paymentDetail(), paymentMethod: "支付宝" }), false);
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

test("rejects query and close responses that change the loaded amount", async () => {
  paymentApi.getDetail = () => Promise.reject(new Error("读回暂不可用"));
  for (const [action, status] of [["queryPayment", "SUCCESS"], ["closePayment", "CLOSED"]]) {
    const page = createPage();
    page.pageActive = true;
    page.detailRequestVersion = 0;
    page.setData({ paymentOrderId: "PAY-1", prepayOrderNo: "PRE-TRUSTED",
      detail: paymentDetail(), loading: false, canQuery: true, canClose: true });
    paymentApi[action === "queryPayment" ? "query" : "close"] = () => Promise.resolve({
      ...paymentDetail(status), amount: "67.99"
    });
    await page[action]();
    assert.equal(page.data.detail.status, "WAIT_CALLBACK");
    assert.equal(page.data.staleDetail, true);
    assert.equal(page.data.canQuery, false);
    assert.equal(page.data.canClose, false);
    assert.match(page.data.errorMessage, /当前支付单不一致/);
    page.onUnload();
  }
});

test("rejects action responses that change established bill or customer facts", async () => {
  paymentApi.getDetail = () => Promise.reject(new Error("读回暂不可用"));
  for (const [action, status, changedFact] of [
    ["queryPayment", "SUCCESS", { billNo: "BILL-2" }],
    ["closePayment", "CLOSED", { customerName: "李四" }]
  ]) {
    const page = createPage();
    page.pageActive = true;
    page.detailRequestVersion = 0;
    page.setData({ paymentOrderId: "PAY-1", prepayOrderNo: "PRE-TRUSTED",
      detail: paymentDetail(), loading: false, canQuery: true, canClose: true });
    paymentApi[action === "queryPayment" ? "query" : "close"] = () => Promise.resolve({
      ...paymentDetail(status), ...changedFact
    });
    await page[action]();
    assert.equal(page.data.detail.status, "WAIT_CALLBACK");
    assert.equal(page.data.staleDetail, true);
    assert.match(page.data.errorMessage, /当前支付单不一致/);
    page.onUnload();
  }
});

test("rejects query responses that change established payment lifecycle timestamps", async () => {
  paymentApi.getDetail = () => Promise.reject(new Error("读回暂不可用"));
  const page = createPage();
  page.pageActive = true;
  page.detailRequestVersion = 0;
  page.setData({
    paymentOrderId: "PAY-1",
    prepayOrderNo: "PRE-TRUSTED",
    detail: paymentDetail("SUCCESS"),
    loading: false,
    canQuery: true
  });

  paymentApi.query = () => Promise.resolve({
    ...paymentDetail("SUCCESS"),
    successAt: "2026-09-27 10:02:00"
  });
  await page.queryPayment();

  assert.equal(page.data.detail.successAt, "2026-09-27 10:01:00");
  assert.match(page.data.errorMessage, /当前支付单不一致/);
  page.onUnload();
});

test("rejects query responses that change the linked refund order", async () => {
  paymentApi.getDetail = () => Promise.reject(new Error("读回暂不可用"));
  const page = createPage();
  page.pageActive = true;
  page.detailRequestVersion = 0;
  page.setData({
    paymentOrderId: "PAY-1",
    prepayOrderNo: "PRE-TRUSTED",
    detail: paymentDetail("SUCCESS"),
    loading: false,
    canQuery: true
  });

  paymentApi.query = () => Promise.resolve({
    ...paymentDetail("SUCCESS"),
    refundOrderId: "REF-OTHER"
  });
  await page.queryPayment();

  assert.equal(page.data.detail.refundOrderId, "REF-1");
  assert.match(page.data.errorMessage, /当前支付单不一致/);
  page.onUnload();
});

test("rejects query responses that change the payment scene", async () => {
  paymentApi.getDetail = () => Promise.reject(new Error("读回暂不可用"));
  const page = createPage();
  page.pageActive = true;
  page.detailRequestVersion = 0;
  page.setData({
    paymentOrderId: "PAY-1",
    prepayOrderNo: "PRE-TRUSTED",
    detail: paymentDetail("SUCCESS"),
    loading: false,
    canQuery: true
  });

  paymentApi.query = () => Promise.resolve({
    ...paymentDetail("SUCCESS"),
    payScene: "WALLET_RECHARGE_MINI_PROGRAM"
  });
  await page.queryPayment();

  assert.equal(page.data.detail.payScene, "BALANCE_PAY_MINI_PROGRAM");
  assert.match(page.data.errorMessage, /当前支付单不一致/);
  page.onUnload();
});
