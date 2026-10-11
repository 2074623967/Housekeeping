const assert = require("node:assert/strict");
const test = require("node:test");

const { normalizePaymentSubmitResult, paymentApi } = require("../utils/request");

test("renders the cashier bill number", () => {
  const source = require("node:fs").readFileSync(require("node:path").join(__dirname, "../pages/cashier/index.wxml"), "utf8");
  assert.match(source, /账单号.*cashier\.billNo/);
});

test("shows the original method and transaction for an already submitted cashier", () => {
  const source = require("node:fs").readFileSync(require("node:path").join(__dirname, "../pages/cashier/index.wxml"), "utf8");
  assert.match(source, /原渠道流水号[^\n]*cashier\.channelTransactionNo/);
  assert.match(source, /channel-name">\{\{cashier\.paymentMethod \|\| cashier\.reviewPaymentMethod \|\| '微信支付'\}\}/);
  assert.match(source, /wx:if="\{\{cashier\.paymentMethod\}\}"[^>]*>原支付渠道：\{\{cashier\.channel\}\}/);
  assert.match(source, /WAIT_CALLBACK' \? '等待原渠道回调'/);
  assert.match(source, /SUCCESS' \? '支付已完成'/);
  assert.match(source, /disabled="\{\{!canSubmit \|\| loading \|\| actionName\}\}" bindtap="submitPayment"/);
  assert.doesNotMatch(source, /bindtap="regenerateIdempotencyKey"/);
});

test("binds cashier loads to the selected mini-program scene", () => {
  const source = require("node:fs").readFileSync(require("node:path").join(__dirname, "../pages/cashier/index.js"), "utf8");
  assert.match(source, /paymentApi\.getCashier\([\s\S]*this\.data\.scene\.payScene/);
  assert.match(source, /paymentApi\.submit\([\s\S]*this\.data\.scene\.payScene/);
});

let pageDefinition;
const originalPage = global.Page;
global.Page = (definition) => {
  pageDefinition = definition;
};
require("../pages/cashier/index");
global.Page = originalPage;

function deferred() {
  let resolve;
  const promise = new Promise((resolvePromise) => {
    resolve = resolvePromise;
  });
  return { promise, resolve };
}

function flushPromises() {
  return new Promise((resolve) => setImmediate(resolve));
}

function createPage() {
  return {
    ...pageDefinition,
    data: { ...pageDefinition.data },
    setData(update) {
      Object.assign(this.data, update);
    }
  };
}

function cashier() {
  return {
    prepayOrderNo: "PRE-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    payScene: "BALANCE_PAY_MINI_PROGRAM",
    orderNo: "ORDER-001",
    amount: "68.00",
    channels: ["微信支付"],
    status: "待支付",
    paymentStatus: "PREPAY_CREATED",
    latestAttemptCompletedAt: "2026-09-27 10:01:00",
    expiresAt: "2099-01-01 00:00:00"
  };
}

function pendingCashier() {
  return {
    ...cashier(), status: "待回调", paymentStatus: "WAIT_CALLBACK",
    paymentMethod: "微信支付", channel: "wx_h5", channelTransactionNo: "WX-LOCAL-001",
    latestAttemptStatus: "等待回调", latestAttemptCompletedAt: null
  };
}

function terminalSubmitResult(paymentStatus) {
  const cashierStatuses = {
    SUCCESS: "支付成功", CLOSED: "已关闭", RISK_BLOCKED: "风控拦截",
    RISK_REJECTED: "风控拒绝", FAIL: "结果待确认"
  };
  const result = {
    ...cashier(), paymentMethod: "微信支付", channel: "WX_H5", paymentStatus,
    createdAt: "2026-09-27 10:01:00", cashierStatus: cashierStatuses[paymentStatus],
    cashierStatusType: paymentStatus === "SUCCESS" ? "success" : "danger",
    latestAttemptCompletedAt: null,
    nativePaymentParams: {
      timeStamp: "123", nonceStr: "nonce", package: "prepay_id=local",
      signType: "RSA", paySign: "signature"
    }
  };
  if (paymentStatus === "SUCCESS") {
    Object.assign(result, {
      successAt: "2026-09-27 10:02:00", channelTransactionNo: "WX-001",
      latestAttemptStatus: "成功", latestAttemptStatusType: "success",
      latestAttemptCompletedAt: "2026-09-27 10:02:00"
    });
  }
  return normalizePaymentSubmitResult(
    result, "PRE-001", "微信支付", "WX_H5", "BALANCE_PAY_MINI_PROGRAM"
  );
}

test("does not query or submit a cashier for an unsupported external scene", async () => {
  let requestCount = 0;
  paymentApi.getCashier = () => {
    requestCount += 1;
    return Promise.resolve(cashier());
  };
  for (const options of [
    { prepayOrderNo: "PRE-001", scene: "unknown" },
    { prepayOrderNo: "PRE-001", payScene: "HOME_CLEAN_MINI_PROGRAM" },
    { prepayOrderNo: "PRE-001", scene: "balance-pay", payScene: "WALLET_RECHARGE_MINI_PROGRAM" }
  ]) {
    const page = createPage();
    page.onLoad(options);
    await page.loadCashier();
    await page.submitPayment();
    assert.equal(page.data.cashier, null);
    assert.equal(page.data.canSubmit, false);
    assert.match(page.data.errorMessage, /不支持该业务场景/);
    page.onUnload();
  }
  assert.equal(requestCount, 0);
});

test("does not write an in-flight cashier response after page unload", async () => {
  const request = deferred();
  paymentApi.getCashier = () => request.promise;
  const page = createPage();

  page.onLoad({ prepayOrderNo: "PRE-001" });
  page.onUnload();
  request.resolve(cashier());
  await flushPromises();

  assert.equal(page.data.cashier, null);
  assert.equal(page.data.loading, true);
});

test("reloads the cashier after a hidden request without applying its old response", async () => {
  const firstRequest = deferred();
  let requestCount = 0;
  paymentApi.getCashier = () => {
    requestCount += 1;
    return requestCount === 1 ? firstRequest.promise : Promise.resolve(cashier());
  };
  const page = createPage();

  page.onLoad({ prepayOrderNo: "PRE-001" });
  page.onShow();
  page.onHide();
  firstRequest.resolve(pendingCashier());
  await flushPromises();

  assert.equal(page.data.cashier, null);
  assert.equal(page.data.canSubmit, false);
  page.onShow();
  await flushPromises();
  assert.equal(requestCount, 2);
  assert.equal(page.data.cashier.paymentStatus, "PREPAY_CREATED");
  assert.equal(page.data.canSubmit, true);
  assert.equal(page.data.idempotencyKey, "MINI-PRE-001");
  page.onUnload();
});

test("ignores a hidden cashier response that arrives after the resumed readback", async () => {
  const firstRequest = deferred();
  let requestCount = 0;
  paymentApi.getCashier = () => {
    requestCount += 1;
    return requestCount === 1 ? firstRequest.promise : Promise.resolve(cashier());
  };
  const page = createPage();

  page.onLoad({ prepayOrderNo: "PRE-001" });
  page.onHide();
  page.onShow();
  await flushPromises();
  firstRequest.resolve(pendingCashier());
  await flushPromises();

  assert.equal(requestCount, 2);
  assert.equal(page.data.cashier.paymentStatus, "PREPAY_CREATED");
  assert.equal(page.data.canSubmit, true);
  assert.equal(page.data.idempotencyKey, "MINI-PRE-001");
  page.onUnload();
});

test("ignores an old submit response after hide and show while retaining the original key", async () => {
  const previousGetApp = global.getApp;
  const previousWx = global.wx;
  const submitRequest = deferred();
  let nativeCalls = 0;
  let redirects = 0;
  let submits = 0;
  global.getApp = () => ({ globalData: { paymentAccessToken: "mini-token" } });
  global.wx = {
    requestPayment: ({ success }) => { nativeCalls += 1; success(); },
    redirectTo: () => { redirects += 1; }
  };
  paymentApi.submit = () => {
    submits += 1;
    return submitRequest.promise;
  };
  paymentApi.getCashier = () => Promise.resolve(cashier());
  const page = createPage();
  page.pageActive = true;
  page.cashierRequestVersion = 0;
  page.setData({ prepayOrderNo: "PRE-001", cashier: cashier(),
    idempotencyKey: "MINI-PRE-001", loading: false, canSubmit: true });
  try {
    const action = page.submitPayment();
    page.onHide();
    page.onShow();
    await flushPromises();
    assert.equal(page.data.canSubmit, false);
    submitRequest.resolve(terminalSubmitResult("SUCCESS"));
    await action;
    await flushPromises();

    assert.equal(submits, 1);
    assert.equal(nativeCalls, 0);
    assert.equal(redirects, 0);
    assert.equal(page.data.cashier.paymentStatus, "PREPAY_CREATED");
    assert.equal(page.data.canSubmit, true);
    assert.equal(page.data.idempotencyKey, "MINI-PRE-001");
  } finally {
    page.onUnload();
    global.getApp = previousGetApp;
    global.wx = previousWx;
  }
});

test("uses the refreshed cashier instead of a late native payment callback after returning", async () => {
  const previousGetApp = global.getApp;
  const previousWx = global.wx;
  let nativeSuccess;
  let redirects = 0;
  global.getApp = () => ({ globalData: { paymentAccessToken: "mini-token" } });
  global.wx = {
    requestPayment: ({ success }) => { nativeSuccess = success; },
    redirectTo: () => { redirects += 1; }
  };
  paymentApi.submit = () => Promise.resolve({
    ...pendingCashier(),
    nativePaymentParams: {
      timeStamp: "123", nonceStr: "nonce", package: "prepay_id=local",
      signType: "RSA", paySign: "signature"
    }
  });
  paymentApi.getCashier = () => Promise.resolve(pendingCashier());
  const page = createPage();
  page.pageActive = true;
  page.cashierRequestVersion = 0;
  page.setData({ prepayOrderNo: "PRE-001", cashier: cashier(),
    idempotencyKey: "MINI-PRE-001", loading: false, canSubmit: true });
  try {
    const action = page.submitPayment();
    await flushPromises();
    assert.equal(typeof nativeSuccess, "function");
    page.onHide();
    page.onShow();
    await flushPromises();
    assert.equal(redirects, 1);
    nativeSuccess();
    await action;
    assert.equal(redirects, 1);
    assert.equal(page.data.cashier.paymentStatus, "WAIT_CALLBACK");
    assert.equal(page.data.idempotencyKey, "MINI-PRE-001");
  } finally {
    page.onUnload();
    global.getApp = previousGetApp;
    global.wx = previousWx;
  }
});

test("keeps the cashier latest attempt completion time in the loaded snapshot", async () => {
  paymentApi.getCashier = () => Promise.resolve(cashier());
  const page = createPage();

  page.onLoad({ prepayOrderNo: "PRE-001" });
  await flushPromises();

  assert.equal(page.data.cashier.latestAttemptCompletedAt, "2026-09-27 10:01:00");
  page.onUnload();
});

test("rejects a manual cashier refresh that changes the payment identity", async () => {
  let response = cashier();
  paymentApi.getCashier = () => Promise.resolve(response);
  const page = createPage();

  page.onLoad({ prepayOrderNo: "PRE-001" });
  await flushPromises();
  response = { ...cashier(), paymentOrderId: "PAY-OTHER", businessChainId: "CHAIN-PAY-OTHER" };
  await page.loadCashier();

  assert.equal(page.data.cashier, null);
  assert.equal(page.data.canSubmit, false);
  assert.match(page.data.errorMessage, /当前支付单不一致/);
  page.onUnload();
});

test("accepts a manual cashier refresh for the same payment", async () => {
  let response = cashier();
  paymentApi.getCashier = () => Promise.resolve(response);
  const page = createPage();

  page.onLoad({ prepayOrderNo: "PRE-001" });
  await flushPromises();
  response = { ...cashier(), latestAttemptCompletedAt: "2026-09-27 10:02:00" };
  await page.loadCashier();

  assert.equal(page.data.cashier.latestAttemptCompletedAt, "2026-09-27 10:02:00");
  assert.equal(page.data.errorMessage, "");
  page.onUnload();
});

test("trims the prepay order number from the cashier route", async () => {
  let requestedPrepayOrderNo = "";
  paymentApi.getCashier = (prepayOrderNo) => {
    requestedPrepayOrderNo = prepayOrderNo;
    return Promise.resolve(cashier());
  };
  const page = createPage();

  page.onLoad({ prepayOrderNo: "%20PRE-001%20" });
  await flushPromises();

  assert.equal(requestedPrepayOrderNo, "PRE-001");
  assert.equal(page.data.prepayOrderNo, "PRE-001");
  page.onUnload();
});

test("reuses the same mini-program payment key after page recreation and an uncertain submit", async () => {
  const previousGetApp = global.getApp;
  const previousWx = global.wx;
  const submitted = [];
  global.getApp = () => ({ globalData: { paymentAccessToken: "mini-token" } });
  global.wx = { redirectTo() {} };
  paymentApi.getCashier = () => Promise.resolve(cashier());
  paymentApi.submit = (request) => {
    submitted.push(request);
    return Promise.reject(new Error("提交响应丢失"));
  };
  try {
    for (let index = 0; index < 2; index += 1) {
      const page = createPage();
      page.onLoad({ prepayOrderNo: "PRE-001" });
      try {
        await flushPromises();
        assert.equal(page.data.idempotencyKey, "MINI-PRE-001");
        assert.equal(page.data.canSubmit, true);
        await page.submitPayment();
      } finally {
        page.onUnload();
      }
    }
    assert.equal(submitted.length, 2);
    assert.deepEqual(submitted[1], submitted[0]);
  } finally {
    global.getApp = previousGetApp;
    global.wx = previousWx;
  }
});

test("does not invoke native payment for an unchanged prepay submit response", async () => {
  const previousGetApp = global.getApp;
  const previousWx = global.wx;
  let nativeCalls = 0;
  let redirects = 0;
  global.getApp = () => ({ globalData: { paymentAccessToken: "mini-token" } });
  global.wx = {
    requestPayment: ({ success }) => { nativeCalls += 1; success(); },
    redirectTo: () => { redirects += 1; }
  };
  paymentApi.submit = () => Promise.resolve({
    ...cashier(), paymentMethod: "微信支付", channel: "WX_MINI_PROGRAM",
    nativePaymentParams: {
      timeStamp: "123", nonceStr: "nonce", package: "prepay_id=local",
      signType: "RSA", paySign: "signature"
    }
  });
  paymentApi.getCashier = () => Promise.resolve(cashier());
  const page = createPage();
  page.pageActive = true;
  page.cashierRequestVersion = 0;
  page.setData({ prepayOrderNo: "PRE-001", cashier: cashier(),
    idempotencyKey: "MINI-PRE-001", loading: false, canSubmit: true });
  try {
    await page.submitPayment();
    assert.equal(nativeCalls, 0);
    assert.equal(redirects, 0);
    assert.equal(page.data.idempotencyKey, "MINI-PRE-001");
    assert.equal(page.data.cashier.paymentStatus, "PREPAY_CREATED");
    assert.match(page.data.errorMessage, /状态未推进/);
  } finally {
    page.onUnload();
    global.getApp = previousGetApp;
    global.wx = previousWx;
  }
});

test("does not invoke native payment while a submit is still processing", async () => {
  const previousGetApp = global.getApp;
  const previousWx = global.wx;
  let nativeCalls = 0;
  let redirects = 0;
  global.getApp = () => ({ globalData: { paymentAccessToken: "mini-token" } });
  global.wx = {
    requestPayment: ({ success }) => { nativeCalls += 1; success(); },
    redirectTo: () => { redirects += 1; }
  };
  paymentApi.submit = () => Promise.resolve({
    ...cashier(), paymentMethod: "微信支付", paymentStatus: "PAYING",
    nativePaymentParams: {
      timeStamp: "123", nonceStr: "nonce", package: "prepay_id=local",
      signType: "RSA", paySign: "signature"
    }
  });
  paymentApi.getCashier = () => Promise.resolve({
    ...cashier(), status: "支付中", paymentStatus: "PAYING"
  });
  const page = createPage();
  page.pageActive = true;
  page.cashierRequestVersion = 0;
  page.setData({ prepayOrderNo: "PRE-001", cashier: cashier(),
    idempotencyKey: "MINI-PRE-001", loading: false, canSubmit: true });
  try {
    await page.submitPayment();
    assert.equal(nativeCalls, 0);
    assert.equal(redirects, 0);
    assert.equal(page.data.idempotencyKey, "MINI-PRE-001");
    assert.equal(page.data.cashier.paymentStatus, "PAYING");
    assert.equal(page.data.canSubmit, false);
    assert.match(page.data.errorMessage, /状态未推进/);
  } finally {
    page.onUnload();
    global.getApp = previousGetApp;
    global.wx = previousWx;
  }
});

test("routes completed submit responses without invoking native payment again", async () => {
  const previousGetApp = global.getApp;
  const previousWx = global.wx;
  let nativeCalls = 0;
  let readbacks = 0;
  let redirects = 0;
  global.getApp = () => ({ globalData: { paymentAccessToken: "mini-token" } });
  global.wx = {
    requestPayment: ({ success }) => { nativeCalls += 1; success(); },
    redirectTo: () => { redirects += 1; }
  };
  paymentApi.getCashier = () => { readbacks += 1; return Promise.resolve(cashier()); };
  try {
    for (const paymentStatus of ["SUCCESS", "CLOSED"]) {
      paymentApi.submit = () => Promise.resolve(terminalSubmitResult(paymentStatus));
      const page = createPage();
      page.pageActive = true;
      page.setData({ prepayOrderNo: "PRE-001", cashier: cashier(),
        loading: false, canSubmit: true });
      try {
        await page.submitPayment();
        assert.equal(page.data.errorMessage, "");
        page.startCountdown(cashier().expiresAt);
        assert.equal(page.data.canSubmit, false);
      } finally {
        page.onUnload();
      }
    }
    assert.equal(nativeCalls, 0);
    assert.equal(readbacks, 0);
    assert.equal(redirects, 2);
  } finally {
    global.getApp = previousGetApp;
    global.wx = previousWx;
  }
});

test("does not invoke native payment for blocked or failed submit states", async () => {
  const previousGetApp = global.getApp;
  const previousWx = global.wx;
  let nativeCalls = 0;
  let redirects = 0;
  global.getApp = () => ({ globalData: { paymentAccessToken: "mini-token" } });
  global.wx = {
    requestPayment: ({ success }) => { nativeCalls += 1; success(); },
    redirectTo: () => { redirects += 1; }
  };
  try {
    for (const paymentStatus of ["RISK_BLOCKED", "RISK_REJECTED", "FAIL"]) {
      paymentApi.submit = () => Promise.resolve(terminalSubmitResult(paymentStatus));
      paymentApi.getCashier = () => Promise.resolve({ ...cashier(), paymentStatus });
      const page = createPage();
      page.pageActive = true;
      page.cashierRequestVersion = 0;
      page.setData({ prepayOrderNo: "PRE-001", cashier: cashier(),
        loading: false, canSubmit: true });
      try {
        await page.submitPayment();
        assert.match(page.data.errorMessage, /不允许拉起微信支付/);
      } finally {
        page.onUnload();
      }
    }
    assert.equal(nativeCalls, 0);
    assert.equal(redirects, 0);
  } finally {
    global.getApp = previousGetApp;
    global.wx = previousWx;
  }
});

test("still invokes native payment once for an accepted pending submit", async () => {
  const previousGetApp = global.getApp;
  const previousWx = global.wx;
  let nativeCalls = 0;
  let redirects = 0;
  global.getApp = () => ({ globalData: { paymentAccessToken: "mini-token" } });
  global.wx = {
    requestPayment: ({ success }) => { nativeCalls += 1; success(); },
    redirectTo: () => { redirects += 1; }
  };
  paymentApi.submit = () => Promise.resolve({
    ...pendingCashier(),
    nativePaymentParams: {
      timeStamp: "123", nonceStr: "nonce", package: "prepay_id=local",
      signType: "RSA", paySign: "signature"
    }
  });
  const page = createPage();
  page.pageActive = true;
  page.setData({ prepayOrderNo: "PRE-001", cashier: cashier(),
    loading: false, canSubmit: true });
  try {
    await page.submitPayment();
    assert.equal(nativeCalls, 1);
    assert.equal(redirects, 1);
    assert.equal(page.data.errorMessage, "");
    page.startCountdown(cashier().expiresAt);
    assert.equal(page.data.canSubmit, false);
  } finally {
    page.onUnload();
    global.getApp = previousGetApp;
    global.wx = previousWx;
  }
});

test("does not write submit state after page unload", async () => {
  const submitRequest = deferred();
  const previousGetApp = global.getApp;
  global.getApp = () => ({ globalData: { paymentAccessToken: "mini-token" } });
  paymentApi.submit = () => submitRequest.promise;
  const page = createPage();
  page.pageActive = true;
  page.setData({
    prepayOrderNo: "PRE-001",
    cashier: cashier(),
    loading: false,
    canSubmit: true,
    actionName: ""
  });

  const submit = page.submitPayment();
  page.onUnload();
  submitRequest.resolve({
    prepayOrderNo: "PRE-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    amount: "68.00"
  });
  await submit;

  global.getApp = previousGetApp;
  assert.equal(page.data.actionName, "submit");
  assert.equal(page.data.errorMessage, "");
});

test("sends reviewed submissions to the result page without invoking native payment", async () => {
  const previousGetApp = global.getApp;
  const previousWx = global.wx;
  let redirectedUrl = "";
  global.getApp = () => ({ globalData: { paymentAccessToken: "mini-token" } });
  global.wx = {
    redirectTo: ({ url }) => { redirectedUrl = url; },
    requestPayment: () => { throw new Error("native payment must not start during review"); }
  };
  paymentApi.submit = () => Promise.resolve({
    prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001", amount: "68.00", payScene: "BALANCE_PAY_MINI_PROGRAM",
    paymentMethod: "微信支付", paymentStatus: "RISK_REVIEW"
  });
  const page = createPage();
  page.pageActive = true;
  page.setData({ prepayOrderNo: "PRE-001", cashier: {
    ...cashier(), status: "待风控复核", paymentStatus: "RISK_REVIEW",
    reviewPaymentMethod: "微信支付", reviewChannelCode: "WX_H5"
  }, loading: false, canSubmit: true });
  try {
    await page.submitPayment();
    assert.match(redirectedUrl, /paymentOrderId=PAY-001/);
    assert.equal(page.data.errorMessage, "");
  } finally {
    global.getApp = previousGetApp;
    global.wx = previousWx;
  }
});

test("reports a post-action cashier refresh for another payment", async () => {
  paymentApi.getCashier = () => Promise.resolve({
    ...cashier(),
    paymentOrderId: "PAY-OTHER",
    orderNo: "ORDER-OTHER"
  });
  const page = createPage();
  page.pageActive = true;
  page.cashierRequestVersion = 0;
  page.setData({
    prepayOrderNo: "PRE-001",
    cashier: cashier(),
    canSubmit: false,
    actionName: "submit"
  });

  await page.loadCashierAfterAction();

  assert.equal(page.data.cashier.paymentOrderId, "PAY-001");
  assert.match(page.data.errorMessage, /当前支付单不一致/);
  assert.equal(page.data.canSubmit, false);
  assert.equal(page.data.staleCashier, true);
});

test("redirects to the result page when a lost submit response reads back pending or successful", async () => {
  const previousGetApp = global.getApp;
  const previousWx = global.wx;
  let redirectedUrl = "";
  global.getApp = () => ({ globalData: { paymentAccessToken: "mini-token" } });
  global.wx = { redirectTo: ({ url }) => { redirectedUrl = url; } };
  paymentApi.submit = () => Promise.reject(new Error("提交响应丢失"));
  try {
    for (const expectedStatus of ["WAIT_CALLBACK", "SUCCESS"]) {
      redirectedUrl = "";
      paymentApi.getCashier = () => Promise.resolve(expectedStatus === "SUCCESS" ? {
        ...pendingCashier(), status: "支付成功", paymentStatus: "SUCCESS",
        latestAttemptStatus: "成功", latestAttemptCompletedAt: "2026-09-27 10:02:00",
        successAt: "2026-09-27 10:02:00"
      } : pendingCashier());
      const page = createPage();
      page.pageActive = true;
      page.cashierRequestVersion = 0;
      page.setData({ prepayOrderNo: "PRE-001", cashier: cashier(),
        idempotencyKey: "IDEMP-LOCAL-001", loading: false, canSubmit: true });
      try {
        await page.submitPayment();
        assert.equal(page.data.cashier.paymentStatus, expectedStatus);
        assert.equal(page.data.staleCashier, false);
        assert.equal(page.data.canSubmit, false);
        assert.equal(page.data.idempotencyKey, "IDEMP-LOCAL-001");
        assert.match(redirectedUrl, /paymentOrderId=PAY-001.*prepayOrderNo=PRE-001.*paymentMethod=/);
        assert.match(page.data.errorMessage, /提交响应丢失/);
      } finally {
        page.onUnload();
      }
    }
  } finally {
    global.getApp = previousGetApp;
    global.wx = previousWx;
  }
});

test("marks an unverified submit stale and routes only after a trusted manual refresh", async () => {
  const source = require("node:fs").readFileSync(
    require("node:path").join(__dirname, "../pages/cashier/index.wxml"), "utf8");
  assert.match(source, /staleCashier \? '待核查' : cashier\.status/);
  assert.match(source, /wx:if="\{\{staleCashier\}\}"[^>]*>当前仅展示上次收银台快照，刷新确认前请勿重复付款/);
  const previousGetApp = global.getApp;
  const previousWx = global.wx;
  let redirectedUrl = "";
  global.getApp = () => ({ globalData: { paymentAccessToken: "mini-token" } });
  global.wx = { redirectTo: ({ url }) => { redirectedUrl = url; } };
  paymentApi.submit = () => Promise.reject(new Error("提交响应丢失"));
  paymentApi.getCashier = () => Promise.reject(new Error("收银台读回失败"));
  const page = createPage();
  page.pageActive = true;
  page.cashierRequestVersion = 0;
  page.setData({ prepayOrderNo: "PRE-001", cashier: cashier(),
    loading: false, canSubmit: true });
  try {
    await page.submitPayment();
    assert.equal(page.data.cashier.paymentStatus, "PREPAY_CREATED");
    assert.equal(page.data.staleCashier, true);
    assert.equal(page.data.canSubmit, false);
    assert.equal(redirectedUrl, "");
    assert.match(page.data.errorMessage, /提交响应丢失.*收银台读回失败.*支付状态未经确认/);

    paymentApi.getCashier = () => Promise.resolve(pendingCashier());
    await page.loadCashier();
    assert.equal(page.data.staleCashier, false);
    assert.equal(page.data.canSubmit, false);
    assert.match(redirectedUrl, /paymentOrderId=PAY-001/);
  } finally {
    page.onUnload();
    global.getApp = previousGetApp;
    global.wx = previousWx;
  }
});

test("preserves the reviewed method when a failed submit reads back risk review", async () => {
  const previousWx = global.wx;
  let redirectedUrl = "";
  global.wx = { redirectTo: ({ url }) => { redirectedUrl = url; } };
  paymentApi.getCashier = () => Promise.resolve({
    ...cashier(), status: "待风控复核", paymentStatus: "RISK_REVIEW",
    reviewPaymentMethod: "支付宝", reviewChannelCode: "alipay_h5"
  });
  const page = createPage();
  page.pageActive = true;
  page.cashierRequestVersion = 0;
  page.setData({ prepayOrderNo: "PRE-001", cashier: cashier() });
  try {
    await page.loadCashierAfterAction(new Error("响应丢失"));
    assert.ok(redirectedUrl.includes(`paymentMethod=${encodeURIComponent("支付宝")}`));
  } finally {
    page.onUnload();
    global.wx = previousWx;
  }
});

test("ignores a delayed submit readback after the cashier page unloads", async () => {
  const previousGetApp = global.getApp;
  const previousWx = global.wx;
  let redirects = 0;
  const readback = deferred();
  global.getApp = () => ({ globalData: { paymentAccessToken: "mini-token" } });
  global.wx = { redirectTo: () => { redirects += 1; } };
  paymentApi.submit = () => Promise.reject(new Error("提交响应丢失"));
  paymentApi.getCashier = () => readback.promise;
  const page = createPage();
  page.pageActive = true;
  page.cashierRequestVersion = 0;
  page.setData({ prepayOrderNo: "PRE-001", cashier: cashier(),
    loading: false, canSubmit: true });
  try {
    const action = page.submitPayment();
    await flushPromises();
    page.onUnload();
    readback.resolve(pendingCashier());
    await action;
    assert.equal(page.data.cashier.paymentStatus, "PREPAY_CREATED");
    assert.equal(redirects, 0);
  } finally {
    global.getApp = previousGetApp;
    global.wx = previousWx;
  }
});
