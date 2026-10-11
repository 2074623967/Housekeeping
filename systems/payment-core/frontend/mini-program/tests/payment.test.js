const assert = require("node:assert/strict");
const test = require("node:test");
const {
  canClosePayment,
  canQueryPayment,
  canRedirectAfterNativePayment,
  canSubmitPayment,
  matchesPaymentSubmission,
  matchesCashierSnapshot,
  resolveCashierStatusClass,
  createIdempotencyKey,
  createOrderNo,
  normalizeAmount,
  normalizeNativePaymentParams,
  requestNativePayment,
  resolvePaymentAccessToken,
  resolvePaymentDetailRefreshDelay,
  resolveLinkedScene,
  resolveRemainingSeconds,
  resolveScene,
  resultMeta,
  shouldAutoRefreshPaymentDetail
} = require("../utils/payment");

test("normalizes valid mini program payment amounts", () => {
  assert.equal(normalizeAmount("68"), "68.00");
  assert.equal(normalizeAmount("68.5"), "68.50");
  assert.equal(normalizeAmount("0"), "");
  assert.equal(normalizeAmount("1.234"), "");
  assert.equal(normalizeAmount("10000000000000000"), "");
  assert.equal(normalizeAmount("9007199254740992.01"), "9007199254740992.01");
});

test("resolves supported business scenes and stable identifiers", () => {
  assert.equal(resolveScene("recharge").payScene, "WALLET_RECHARGE_MINI_PROGRAM");
  assert.equal(resolveScene("unknown").key, "balance-pay");
  assert.equal(createOrderNo("transfer", 123), "ACCOUNT-TRANSFER-MINI-123");
  assert.equal(createIdempotencyKey("PRE-1"), "MINI-PRE-1");
  assert.equal(createIdempotencyKey("PRE-1"), createIdempotencyKey("PRE-1"));
  assert.notEqual(createIdempotencyKey("PRE-1"), createIdempotencyKey("PRE-2"));
  assert.equal(createIdempotencyKey("P".repeat(64)).length, 69);
  assert.equal(createIdempotencyKey(""), "");
});

test("rejects unsupported linked scenes rather than treating them as balance payment", () => {
  assert.equal(resolveLinkedScene(null).key, "balance-pay");
  assert.equal(resolveLinkedScene("recharge", "WALLET_RECHARGE_MINI_PROGRAM").key, "recharge");
  for (const sceneKey of ["", "unknown", "__proto__", "constructor", "HOME_CLEAN_MINI_PROGRAM"]) {
    assert.equal(resolveLinkedScene(sceneKey).payScene, "");
  }
  assert.equal(resolveScene("__proto__").key, "balance-pay");
  assert.equal(resolveLinkedScene(null, "HOME_CLEAN_MINI_PROGRAM").payScene, "");
  assert.equal(resolveLinkedScene("balance-pay", "WALLET_RECHARGE_MINI_PROGRAM").payScene, "");
});

test("reads the mini program payment token from controlled app state", () => {
  const previousGetApp = global.getApp;
  global.getApp = () => ({ globalData: { paymentAccessToken: "  configured-mini-token " } });
  try {
    assert.equal(resolvePaymentAccessToken(), "configured-mini-token");
  } finally {
    global.getApp = previousGetApp;
  }
});

test("fails closed when the mini program payment token is absent", () => {
  const previousGetApp = global.getApp;
  global.getApp = () => ({ globalData: {} });
  try {
    assert.equal(resolvePaymentAccessToken(), "");
  } finally {
    global.getApp = previousGetApp;
  }
});

test("keeps query and close operations on proven channel facts", () => {
  assert.equal(canQueryPayment("WAIT_CALLBACK", "等待回调", null, "WX-1", "wx_h5"), true);
  assert.equal(canQueryPayment("WAIT_CALLBACK", "等待回调", null, "WX-1", null), false);
  assert.equal(canQueryPayment("CLOSED", "处理中", null, "WX-1", " "), false);
  assert.equal(canQueryPayment("CLOSED", null), true);
  assert.equal(canQueryPayment("CLOSED", null, null, "WX-1"), false);
  assert.equal(canQueryPayment("CLOSED", "处理中", null, null, "wx_h5"), true);
  assert.equal(canQueryPayment("CLOSED", "失败"), false);
  assert.equal(canQueryPayment("PAYING", "处理中"), false);
  for (const attemptStatus of ["成功", "支付成功", " 支付成功 ", "失败", "支付失败", "SUCCESS", "FAIL", "FAILED", " FAILED "]) {
    assert.equal(canQueryPayment("WAIT_CALLBACK", attemptStatus, null, "WX-1", "wx_h5"), false);
  }
  assert.equal(canQueryPayment("WAIT_CALLBACK", "处理中", "2026-10-07 09:00:00", "WX-1", "wx_h5"), false);
  assert.equal(canQueryPayment("PAYING", null), false);
  assert.equal(canQueryPayment("WAIT_CALLBACK", "-"), false);
  assert.equal(canQueryPayment("PREPAY_CREATED", null), false);
  assert.equal(canClosePayment("PREPAY_CREATED", null, null), true);
  assert.equal(canClosePayment("RISK_REVIEW", "", "-"), true);
  assert.equal(canClosePayment("WAIT_CALLBACK", null, null), false);
  assert.equal(canClosePayment("PREPAY_CREATED", "WX-1", null), false);
});

test("submits only an unexpired untouched prepay with an allowed channel", () => {
  const now = new Date("2026-09-14T10:00:00").getTime();
  const cashier = {
    channels: ["微信支付"],
    status: "待支付",
    paymentStatus: "PREPAY_CREATED",
    latestAttemptStatus: "-",
    expiresAt: "2026-09-14 10:05:00"
  };
  assert.equal(resolveRemainingSeconds(cashier.expiresAt, now), 300);
  assert.equal(canSubmitPayment(cashier, now), true);
  assert.equal(resolveRemainingSeconds("2026-02-30 10:05:00", now), 0);
  assert.equal(canSubmitPayment({ ...cashier, expiresAt: "2026-02-30 10:05:00" }, now), false);
  assert.equal(canSubmitPayment({ ...cashier, status: "待风控复核" }, now), false);
  const reviewed = { ...cashier, status: "待风控复核", paymentStatus: "RISK_REVIEW",
    reviewPaymentMethod: "微信支付", reviewChannelCode: "WX_H5" };
  assert.equal(canSubmitPayment(reviewed, now), true);
  assert.equal(canSubmitPayment({ ...reviewed, reviewChannelCode: "wx_h5" }, now), true);
  assert.equal(canSubmitPayment({ ...reviewed, reviewPaymentMethod: "支付宝" }, now), false);
  assert.equal(canSubmitPayment({ ...reviewed, reviewChannelCode: "ALIPAY_H5" }, now), false);
  assert.equal(canSubmitPayment({ ...reviewed, latestAttemptStatus: "处理中" }, now), false);
  assert.equal(canSubmitPayment({ ...reviewed, channelTransactionNo: "WX-STALE" }, now), false);
  assert.equal(canSubmitPayment({ ...cashier, paymentStatus: "WAIT_CALLBACK" }, now), false);
  assert.equal(canSubmitPayment({ ...cashier, status: "支付成功", paymentStatus: "SUCCESS",
    paymentMethod: "支付宝", channelTransactionNo: "ALI-001" }, now), false);
  assert.equal(canSubmitPayment({ ...cashier, latestAttemptStatus: "等待回调" }, now), false);
  assert.equal(canSubmitPayment({ ...cashier, latestAttemptStatus: "处理中" }, now), false);
  assert.equal(canSubmitPayment({ ...cashier, channelTransactionNo: "WX-001" }, now), false);
  assert.equal(canSubmitPayment({ ...cashier, channels: ["支付宝"] }, now), false);
  assert.equal(canSubmitPayment({ ...cashier, expiresAt: "2026-09-14 09:59:59" }, now), false);
});

test("preserves danger styling for server-side cashier failures", () => {
  assert.equal(resolveCashierStatusClass("success"), "status-success");
  assert.equal(resolveCashierStatusClass("danger"), "status-danger");
  assert.equal(resolveCashierStatusClass("warn"), "status-warn");
  assert.equal(resolveCashierStatusClass(undefined), "status-warn");
});

test("normalizes complete native payment parameters and ignores incomplete payloads", () => {
  assert.deepEqual(normalizeNativePaymentParams({
    nativePaymentParams: {
      timeStamp: " 1710000000 ",
      nonceStr: "nonce",
      package: "prepay_id=wx-prepay",
      signType: "RSA",
      paySign: "signature"
    }
  }), {
    timeStamp: "1710000000",
    nonceStr: "nonce",
    package: "prepay_id=wx-prepay",
    signType: "RSA",
    paySign: "signature"
  });
  assert.equal(normalizeNativePaymentParams({ paymentParams: { timeStamp: "1" } }), null);
  assert.equal(normalizeNativePaymentParams({
    nativePaymentParams: {
      timeStamp: "not-a-timestamp",
      nonceStr: "nonce",
      package: "prepay_id=wx-prepay",
      signType: "RSA",
      paySign: "signature"
    }
  }), null);
  assert.equal(normalizeNativePaymentParams({
    nativePaymentParams: {
      timeStamp: "1710000000",
      nonceStr: "nonce",
      package: "wx-prepay",
      signType: "RSA",
      paySign: "signature"
    }
  }), null);
  assert.equal(normalizeNativePaymentParams({
    nativePaymentParams: {
      timeStamp: "1710000000",
      nonceStr: "nonce",
      package: "prepay_id=wx-prepay",
      signType: "MD5",
      paySign: "signature"
    }
  }), null);
});

test("keeps the local demo path when native payment parameters are absent", async () => {
  assert.deepEqual(await requestNativePayment({ paymentOrderId: "PAY-DEMO" }), { invoked: false });
  assert.equal(canRedirectAfterNativePayment({ invoked: false }), false);
});

test("redirects only after native payment was actually invoked", () => {
  assert.equal(canRedirectAfterNativePayment({ invoked: true }), true);
  assert.equal(canRedirectAfterNativePayment(null), false);
});

test("requires mini program payment submission to match the current business order", () => {
  const result = {
    prepayOrderNo: "PRE-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    billNo: "BILL-001",
    customerName: "张三",
    paymentMethod: "微信支付",
    payScene: "BALANCE_PAY_MINI_PROGRAM",
    amount: "68.00"
  };
  assert.equal(matchesPaymentSubmission("PRE-001", "PAY-001", "ORDER-001", result, "68.00", "BILL-001", "张三", "微信支付"), true);
  assert.equal(matchesPaymentSubmission("PRE-001", "PAY-001", "ORDER-002", result), false);
  assert.equal(matchesPaymentSubmission("PRE-002", "PAY-001", "ORDER-001", result), false);
  assert.equal(matchesPaymentSubmission("PRE-001", "PAY-002", "ORDER-001", result), false);
  assert.equal(matchesPaymentSubmission("PRE-001", "PAY-001", "ORDER-001", { ...result, paymentOrderId: "" }), false);
  assert.equal(matchesPaymentSubmission("PRE-001", "PAY-001", "ORDER-001", { ...result, businessChainId: "CHAIN-OTHER" }), false);
  assert.equal(matchesPaymentSubmission("PRE-001", "PAY-001", "ORDER-001", { ...result, amount: "67.99" }, "68.00"), false);
  assert.equal(matchesPaymentSubmission("PRE-001", "PAY-001", "ORDER-001", result, "68.01"), false);
  assert.equal(matchesPaymentSubmission("PRE-001", "PAY-001", "ORDER-001", { ...result, billNo: "BILL-002" }, "68.00", "BILL-001", "张三"), false);
  assert.equal(matchesPaymentSubmission("PRE-001", "PAY-001", "ORDER-001", { ...result, customerName: "李四" }, "68.00", "BILL-001", "张三"), false);
  assert.equal(matchesPaymentSubmission("PRE-001", "PAY-001", "ORDER-001", { ...result, paymentMethod: "支付宝" }, "68.00", "BILL-001", "张三", "微信支付"), false);
  assert.equal(matchesPaymentSubmission(
    " PRE-001 ",
    " PAY-001 ",
    " ORDER-001 ",
    {
      ...result,
      prepayOrderNo: " PRE-001 ",
      paymentOrderId: " PAY-001 ",
      businessChainId: " CHAIN-PAY-001 ",
      orderNo: " ORDER-001 "
    },
    "68.00"
  ), true);
  assert.equal(matchesPaymentSubmission(
    "PRE-001", "PAY-001", "ORDER-001", result,
    "68.00", "BILL-001", "张三", "微信支付", "BALANCE_PAY_MINI_PROGRAM"
  ), true);
  assert.equal(matchesPaymentSubmission(
    "PRE-001", "PAY-001", "ORDER-001", {
      ...result,
      payScene: "WALLET_RECHARGE_MINI_PROGRAM"
    },
    "68.00", "BILL-001", "张三", "微信支付", "BALANCE_PAY_MINI_PROGRAM"
  ), false);
});

test("requires post-submit cashier refreshes to match the loaded entity snapshot", () => {
  const snapshot = {
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    billNo: "BILL-001",
    customerName: "张三",
    payScene: "BALANCE_PAY_MINI_PROGRAM",
    amount: "68.00"
  };
  const cashier = { prepayOrderNo: "PRE-001", ...snapshot };
  assert.equal(matchesCashierSnapshot("PRE-001", snapshot, cashier, "BALANCE_PAY_MINI_PROGRAM"), true);
  assert.equal(matchesCashierSnapshot(
    "PRE-001", snapshot,
    { ...cashier, payScene: "WALLET_RECHARGE_MINI_PROGRAM" },
    "BALANCE_PAY_MINI_PROGRAM"
  ), false);
  assert.equal(matchesCashierSnapshot("PRE-001", snapshot, { ...cashier, paymentOrderId: "PAY-002" }), false);
  assert.equal(matchesCashierSnapshot("PRE-001", snapshot, { ...cashier, businessChainId: "CHAIN-OTHER" }), false);
  assert.equal(matchesCashierSnapshot("PRE-001", snapshot, { ...cashier, orderNo: "ORDER-002" }), false);
  assert.equal(matchesCashierSnapshot("PRE-001", snapshot, { ...cashier, amount: "67.99" }), false);
  assert.equal(matchesCashierSnapshot("PRE-001", snapshot, { ...cashier, billNo: "BILL-002" }), false);
  assert.equal(matchesCashierSnapshot("PRE-001", snapshot, { ...cashier, customerName: "李四" }), false);
  assert.equal(matchesCashierSnapshot(
    " PRE-001 ",
    { ...snapshot, paymentOrderId: " PAY-001 ", businessChainId: " CHAIN-PAY-001 ", orderNo: " ORDER-001 " },
    {
      ...cashier,
      prepayOrderNo: " PRE-001 ",
      paymentOrderId: " PAY-001 ",
      businessChainId: " CHAIN-PAY-001 ",
      orderNo: " ORDER-001 "
    }
  ), true);
});

test("passes complete native payment parameters to wx.requestPayment", async () => {
  const previousWx = global.wx;
  let requestOptions;
  global.wx = {
    requestPayment(options) {
      requestOptions = options;
      options.success();
    }
  };
  try {
    const result = await requestNativePayment({
      nativePaymentParams: {
        timeStamp: "1710000000",
        nonceStr: "nonce",
        package: "prepay_id=wx-prepay",
        signType: "RSA",
        paySign: "signature"
      }
    });
    assert.deepEqual(result, { invoked: true });
    assert.equal(requestOptions.package, "prepay_id=wx-prepay");
    assert.equal(typeof requestOptions.success, "function");
  } finally {
    global.wx = previousWx;
  }
});

test("maps a native payment cancellation to a stable client error", async () => {
  const previousWx = global.wx;
  global.wx = {
    requestPayment(options) {
      options.fail({ errMsg: "requestPayment:fail cancel" });
    }
  };
  try {
    await assert.rejects(
      requestNativePayment({
        paymentParams: {
          timeStamp: "1710000000",
          nonceStr: "nonce",
          package: "prepay_id=wx-prepay",
          signType: "RSA",
          paySign: "signature"
        }
      }),
      (error) => error.code === "PAYMENT-MINI-NATIVE-CANCEL"
    );
  } finally {
    global.wx = previousWx;
  }
});

test("maps payment terminal states without inventing success", () => {
  assert.equal(resultMeta("SUCCESS").tone, "success");
  assert.equal(resultMeta("CLOSED").tone, "danger");
  assert.equal(resultMeta("WAIT_CALLBACK").title, "支付处理中");
  assert.equal(resultMeta("UNKNOWN").title, "支付处理中");
});

test("auto refreshes only asynchronously advancing payment states", () => {
  assert.equal(shouldAutoRefreshPaymentDetail("WAIT_CALLBACK"), true);
  assert.equal(shouldAutoRefreshPaymentDetail(" paying "), true);
  assert.equal(shouldAutoRefreshPaymentDetail("risk_review"), true);
  assert.equal(shouldAutoRefreshPaymentDetail("PREPAY_CREATED"), false);
  assert.equal(shouldAutoRefreshPaymentDetail("RISK_BLOCKED"), false);
  assert.equal(shouldAutoRefreshPaymentDetail("RISK_REJECTED"), false);
  assert.equal(shouldAutoRefreshPaymentDetail("SUCCESS"), false);
  assert.equal(shouldAutoRefreshPaymentDetail("CLOSED"), false);
  assert.equal(shouldAutoRefreshPaymentDetail(null), false);
});

test("limits automatic payment detail refresh to five minutes", () => {
  assert.equal(resolvePaymentDetailRefreshDelay("WAIT_CALLBACK", 0), 5000);
  assert.equal(resolvePaymentDetailRefreshDelay("WAIT_CALLBACK", 59), 5000);
  assert.equal(resolvePaymentDetailRefreshDelay("WAIT_CALLBACK", 60), null);
  assert.equal(resolvePaymentDetailRefreshDelay("SUCCESS", 0), null);
  assert.equal(resolvePaymentDetailRefreshDelay("WAIT_CALLBACK", -1), null);
  assert.equal(resolvePaymentDetailRefreshDelay("WAIT_CALLBACK", 1.5), null);
});
