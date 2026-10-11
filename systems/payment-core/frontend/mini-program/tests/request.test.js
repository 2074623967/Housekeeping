const assert = require("node:assert/strict");
const test = require("node:test");

const {
  normalizePaymentActionResult,
  normalizePaymentDetailResult,
  normalizePaymentSubmitResult,
  normalizePrepayResult,
  normalizeCashierPageResult,
  normalizeRefundDetailResult,
  paymentApi
} = require("../utils/request");

test("rejects terminal attempts in pending mini program payment snapshots", () => {
  const prepay = {
    prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001", amount: "68.00", paymentStatus: "PREPAY_CREATED",
    createdAt: "2026-09-27 11:59:00", expiresAt: "2026-09-27 12:00:00",
    cashierStatus: "待支付", cashierStatusType: "warn"
  };
  const cashier = {
    ...prepay, status: "待支付", statusType: "warn", channels: ["微信支付"]
  };
  const submission = {
    ...prepay, paymentStatus: "WAIT_CALLBACK", cashierStatus: "支付中", cashierStatusType: "info"
  };
  for (const latestAttemptStatus of ["成功", "支付成功", "失败", "支付失败", " FAIL ", "fAiLeD", " SUCCESS "]) {
    const attempt = { latestAttemptStatus, latestAttemptStatusType: "danger" };
    assert.throws(() => normalizePrepayResult({ ...prepay, ...attempt }),
      (error) => error.code === "PAYMENT-MINI-INVALID-PREPAY");
    assert.throws(() => normalizeCashierPageResult({ ...cashier, ...attempt }),
      (error) => error.code === "PAYMENT-MINI-INVALID-CASHIER");
    assert.throws(() => normalizePaymentSubmitResult({ ...submission, ...attempt }),
      (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-SUBMIT");
  }
});

test("keeps mini program failed payment attempt styles and success facts consistent", () => {
  const failedPayment = {
    prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001", amount: "68.00", status: "FAIL", statusType: "danger",
    createdAt: "2026-09-27 11:59:00", latestAttemptStatus: "失败",
    latestAttemptStatusType: "danger"
  };
  for (const normalize of [normalizePaymentDetailResult, normalizePaymentActionResult]) {
    assert.doesNotThrow(() => normalize(failedPayment));
    assert.doesNotThrow(() => normalize({ ...failedPayment, latestAttemptStatus: " FAILED " }));
    for (const latestAttemptStatus of [" FAIL ", "fAiLeD", "支付失败", "支付成功", " SUCCESS "]) {
      assert.throws(() => normalize({
        ...failedPayment, latestAttemptStatus, latestAttemptStatusType: "success"
      }), (error) => error.code.startsWith("PAYMENT-MINI-INVALID-PAYMENT-"));
    }
  }
});

function installRequestMock(result) {
  global.wx = {
    getStorageSync() {
      return "http://localhost:18081";
    },
    request(options) {
      result(options);
    }
  };
  global.getApp = () => ({ globalData: {} });
}

test("normalizes mini program network failures", async () => {
  installRequestMock((options) => options.fail({ errMsg: "request:fail network unavailable" }));

  await assert.rejects(
    paymentApi.getDetail("PAY-NETWORK"),
    (error) => error.code === "PAYMENT-MINI-NETWORK"
  );
});

test("normalizes mini program timeout failures", async () => {
  installRequestMock((options) => {
    assert.equal(options.timeout, 10000);
    options.fail({ errMsg: "request:fail timeout" });
  });

  await assert.rejects(
    paymentApi.getDetail("PAY-TIMEOUT"),
    (error) => error.code === "PAYMENT-MINI-TIMEOUT"
  );
});

test("trims mini program identifiers and API base URL across the payment flow", async () => {
  const calls = [];
  const paymentResult = {
    prepayOrderNo: "PRE-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    amount: "68.00",
    paymentStatus: "WAIT_CALLBACK",
    cashierStatus: "支付中",
    cashierStatusType: "info",
    createdAt: "2026-09-27 11:59:00",
    expiresAt: "2026-09-27 12:00:00"
  };
  const actionResult = {
    prepayOrderNo: "PRE-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    amount: "68.00",
    status: "WAIT_CALLBACK",
    statusType: "warn",
    createdAt: "2026-09-27 11:59:00"
  };
  const cashierResult = {
    prepayOrderNo: "PRE-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    amount: "68.00",
    status: "待回调",
    statusType: "warn",
    paymentStatus: "WAIT_CALLBACK",
    paymentMethod: "微信支付",
    channel: "wx_h5",
    latestAttemptStatus: "处理中",
    latestAttemptStatusType: "info",
    expiresAt: "2026-09-27 12:00:00",
    channels: ["微信支付"]
  };
  const detailResult = {
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    prepayOrderNo: "PRE-001",
    orderNo: "ORDER-001",
    amount: "68.00",
    status: "WAIT_CALLBACK",
    statusType: "warn",
    createdAt: "2026-09-27 11:59:00"
  };
  const refundResult = {
    refundOrderId: "REF-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    paidAmount: "68.00",
    refundAmount: "18.00",
    refundMethod: "原路退款",
    refundReason: "客户申请",
    status: "PROCESSING",
    statusType: "warn",
    appliedAt: "2026-09-27 12:00:00"
  };
  global.wx = {
    getStorageSync() {
      return "  http://localhost:18081///  ";
    },
    request(options) {
      calls.push(options);
      const path = options.url.replace("http://localhost:18081", "").split("?")[0];
      const data = path.endsWith("/prepay") || path.endsWith("/submit")
        ? paymentResult
        : path.endsWith("/cashier/PRE-001")
          ? cashierResult
          : path === "/api/payments/PAY-001"
            ? detailResult
            : path === "/api/refunds/REF-001"
              ? refundResult
              : actionResult;
      options.success({ statusCode: 200, data: { code: "0", data } });
    }
  };
  global.getApp = () => ({ globalData: {} });

  await paymentApi.prepay({ orderNo: " ORDER-001 " });
  await paymentApi.submit({ prepayOrderNo: " PRE-001 " });
  await paymentApi.query(" PAY-001 ");
  await paymentApi.close(" PAY-001 ");
  await paymentApi.getCashier(" PRE-001 ");
  await paymentApi.getDetail(" PAY-001 ");
  await paymentApi.getRefundDetail(" REF-001 ");

  assert.deepEqual(calls.map(({ url }) => url), [
    "http://localhost:18081/api/payments/prepay",
    "http://localhost:18081/api/payments/submit",
    "http://localhost:18081/api/payments/query",
    "http://localhost:18081/api/payments/close",
    "http://localhost:18081/api/payments/cashier/PRE-001?terminal=MINI_PROGRAM",
    "http://localhost:18081/api/payments/PAY-001",
    "http://localhost:18081/api/refunds/REF-001"
  ]);
  assert.equal(calls[0].data.orderNo, "ORDER-001");
  assert.equal(calls[1].data.prepayOrderNo, "PRE-001");
  assert.equal(calls[2].data.paymentOrderId, "PAY-001");
  assert.equal(calls[3].data.paymentOrderId, "PAY-001");
});

test("rejects mini program prepay responses without a prepay order number", async () => {
  assert.deepEqual(
    normalizePrepayResult({ prepayOrderNo: " PRE-001 ", paymentOrderId: " PAY-001 ", businessChainId: "CHAIN-PAY-001", orderNo: " ORDER-001 ", amount: "68.00", paymentStatus: "PREPAY_CREATED", createdAt: "2026-09-27 11:59:00", expiresAt: "2026-09-27 12:00:00", cashierStatus: "待支付", cashierStatusType: "warn" }),
    { prepayOrderNo: " PRE-001 ", paymentOrderId: " PAY-001 ", businessChainId: "CHAIN-PAY-001", orderNo: " ORDER-001 ", amount: "68.00", paymentStatus: "PREPAY_CREATED", createdAt: "2026-09-27 11:59:00", expiresAt: "2026-09-27 12:00:00", cashierStatus: "待支付", cashierStatusType: "warn" }
  );
  for (const businessChainId of [undefined, "CHAIN-OTHER"]) {
    assert.throws(
      () => normalizePrepayResult({ prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", businessChainId, orderNo: "ORDER-001", amount: "68.00", paymentStatus: "PREPAY_CREATED", createdAt: "2026-09-27 11:59:00", expiresAt: "2026-09-27 12:00:00", cashierStatus: "待支付", cashierStatusType: "warn" }),
      (error) => error.code === "PAYMENT-MINI-INVALID-PREPAY"
    );
  }
  assert.throws(
    () => normalizePrepayResult(null),
    (error) => error.code === "PAYMENT-MINI-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePrepayResult({ prepayOrderNo: "PRE-001" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePrepayResult({ prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", orderNo: "ORDER-002" }, "ORDER-001"),
    (error) => error.code === "PAYMENT-MINI-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePrepayResult({ prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", amount: "" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePrepayResult({ prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", amount: "1.234" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePrepayResult({ prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", amount: "1e3" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePrepayResult({ prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", amount: "6¥8" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePrepayResult({ prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", amount: "1,23" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePrepayResult({ prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", amount: "1.2.3" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePrepayResult({ prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", amount: ".5" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePrepayResult({
      prepayOrderNo: "PRE-001",
      paymentOrderId: "PAY-001",
      businessChainId: "CHAIN-PAY-001",
      orderNo: "ORDER-001",
      amount: "68.00",
      paymentStatus: "PREPAY_CREATED",
      successAt: "2026-09-27 12:01:00",
      createdAt: "2026-09-27 11:59:00",
      expiresAt: "2026-09-27 12:00:00",
      cashierStatus: "待支付",
      cashierStatusType: "warn"
    }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PREPAY"
  );
  assert.deepEqual(
    normalizePrepayResult(
      { prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", orderNo: "ORDER-001", amount: "¥68.00", paymentStatus: "PREPAY_CREATED", createdAt: "2026-09-27 11:59:00", expiresAt: "2026-09-27 12:00:00", cashierStatus: "待支付", cashierStatusType: "warn" },
      "ORDER-001",
      "68"
    ).amount,
    "¥68.00"
  );
  for (const invalidAttemptFacts of [
    { latestAttemptStatus: "处理中", latestAttemptStatusType: "invalid", latestAttemptCompletedAt: null },
    { latestAttemptStatus: "处理中", latestAttemptStatusType: "info", latestAttemptCompletedAt: "2026-09-27 12:00:00" },
    { latestAttemptStatus: "成功", latestAttemptStatusType: "success", latestAttemptCompletedAt: "2026-09-27 12:00:00" }
  ]) {
    assert.throws(
      () => normalizePrepayResult({
        prepayOrderNo: "PRE-001",
        paymentOrderId: "PAY-001",
        businessChainId: "CHAIN-PAY-001",
        orderNo: "ORDER-001",
        amount: "68.00",
        paymentStatus: "PREPAY_CREATED",
        createdAt: "2026-09-27 11:59:00",
        expiresAt: "2026-09-27 12:00:00",
        cashierStatus: "待支付",
        cashierStatusType: "warn",
        ...invalidAttemptFacts
      }),
      (error) => error.code === "PAYMENT-MINI-INVALID-PREPAY"
    );
  }
  assert.throws(
    () => normalizePrepayResult(
      { prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", amount: "68.00", paymentStatus: "PREPAY_CREATED", createdAt: "2026-09-27 12:00:00", expiresAt: "2026-09-27 11:59:00", cashierStatus: "待支付", cashierStatusType: "warn" },
      "ORDER-001",
      "68.00"
    ),
    (error) => error.code === "PAYMENT-MINI-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePrepayResult(
      { prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", amount: "67.99" },
      "ORDER-001",
      "68.00"
    ),
    (error) => error.code === "PAYMENT-MINI-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePrepayResult({
      prepayOrderNo: "PRE-001",
      paymentOrderId: "PAY-001",
      orderNo: "ORDER-001",
      amount: "68.00",
      paymentStatus: "PREPAY_CREATED",
      expiresAt: "not-a-time"
    }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePrepayResult({
      prepayOrderNo: "PRE-001",
      paymentOrderId: "PAY-001",
      orderNo: "ORDER-001",
      amount: "68.00",
      paymentStatus: "PREPAY_CREATED",
      expiresAt: "2026-09-27 12:00:00",
      cashierStatus: "待支付",
      cashierStatusType: "success"
    }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePrepayResult({
      prepayOrderNo: "PRE-001",
      paymentOrderId: "PAY-001",
      orderNo: "ORDER-001",
      amount: "68.00",
      paymentStatus: "PREPAY_CREATED",
      createdAt: "2026-09-27 11:59:00",
      expiresAt: "2026-09-27 12:00:00",
      cashierStatus: "支付成功",
      cashierStatusType: "success"
    }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePrepayResult({
      prepayOrderNo: "PRE-001",
      paymentOrderId: "PAY-001",
      orderNo: "ORDER-001",
      amount: "68.00",
      paymentStatus: "SUCCESS",
      expiresAt: "2026-09-27 12:00:00",
      cashierStatus: "待支付",
      cashierStatusType: "warn"
    }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePrepayResult(
      {
        prepayOrderNo: "PRE-001",
        paymentOrderId: "PAY-001",
        orderNo: "ORDER-001",
        amount: "9007199254740992.01"
      },
      "ORDER-001",
      "9007199254740992.02"
    ),
    (error) => error.code === "PAYMENT-MINI-INVALID-PREPAY"
  );
  installRequestMock((options) => {
    options.success({ statusCode: 200, data: { code: "0", data: null } });
  });

  await assert.rejects(
    paymentApi.prepay({ orderNo: "ORDER-001" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PREPAY"
  );
});

test("keeps the mini program prepay customer name bound to the request", () => {
  const result = {
    prepayOrderNo: "PRE-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    customerName: "张三",
    amount: "68.00",
    paymentStatus: "PREPAY_CREATED",
    createdAt: "2026-09-27 11:59:00",
    expiresAt: "2026-09-27 12:00:00",
    cashierStatus: "待支付",
    cashierStatusType: "warn"
  };
  assert.doesNotThrow(() => normalizePrepayResult(result, "ORDER-001", "68.00", "张三"));
  assert.throws(
    () => normalizePrepayResult({ ...result, customerName: "李四" }, "ORDER-001", "68.00", "张三"),
    (error) => error.code === "PAYMENT-MINI-INVALID-PREPAY"
  );
});

test("rejects mini program payment submit responses without payment and cashier facts", async () => {
  const validResult = { prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", orderNo: "ORDER-001", amount: "68.00", paymentStatus: "WAIT_CALLBACK", createdAt: "2026-09-27 11:59:00", expiresAt: "2026-09-27 12:00:00", cashierStatus: "支付中", cashierStatusType: "info", status: "SUCCESS" };
  assert.deepEqual(normalizePaymentSubmitResult(validResult), validResult);
  assert.throws(
    () => normalizePaymentSubmitResult({
      ...validResult,
      paymentStatus: "PREPAY_CREATED",
      cashierStatus: "待支付",
      cashierStatusType: "warn",
      paymentMethod: "微信支付",
      channel: "WX_MINI_PROGRAM"
    }, "PRE-001", "微信支付", "WX_MINI_PROGRAM"),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-SUBMIT"
  );
  assert.throws(
    () => normalizePaymentSubmitResult(
      { ...validResult, payScene: "WALLET_RECHARGE_MINI_PROGRAM" },
      "PRE-001", null, null, "BALANCE_PAY_MINI_PROGRAM"
    ),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-SUBMIT"
  );
  assert.equal(
    normalizePaymentSubmitResult({ ...validResult, paymentMethod: "微信支付" }, "PRE-001", "微信支付").paymentMethod,
    "微信支付"
  );
  for (const invalidPaymentMethodResult of [
    { ...validResult },
    { ...validResult, paymentMethod: "支付宝" }
  ]) {
    assert.throws(
      () => normalizePaymentSubmitResult(invalidPaymentMethodResult, "PRE-001", "微信支付"),
      (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-SUBMIT"
    );
  }
  assert.doesNotThrow(() => normalizePaymentSubmitResult(
    { ...validResult, channel: "WX_MINI_PROGRAM" },
    "PRE-001", null, "WX_MINI_PROGRAM"
  ));
  assert.throws(
    () => normalizePaymentSubmitResult(
      { ...validResult, channel: "ALI_H5" },
      "PRE-001", null, "WX_MINI_PROGRAM"
    ),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-SUBMIT"
  );
  for (const invalidAttemptFacts of [
    { latestAttemptStatus: "处理中", latestAttemptStatusType: "invalid", latestAttemptCompletedAt: null },
    { latestAttemptStatus: "处理中", latestAttemptStatusType: "info", latestAttemptCompletedAt: "2026-09-27 12:00:00" },
    { latestAttemptStatus: "成功", latestAttemptStatusType: "success", latestAttemptCompletedAt: "2026-09-27 12:00:00" }
  ]) {
    assert.throws(
      () => normalizePaymentSubmitResult({ ...validResult, ...invalidAttemptFacts }),
      (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-SUBMIT"
    );
  }
  assert.deepEqual(
    normalizePaymentSubmitResult({
      ...validResult,
      nativePaymentParams: {
        timeStamp: "1710000000",
        nonceStr: "nonce",
        package: "prepay_id=wx-prepay",
        signType: "RSA",
        paySign: "signature"
      }
    }).nativePaymentParams.signType,
    "RSA"
  );
  assert.throws(
    () => normalizePaymentSubmitResult({ ...validResult, businessChainId: undefined }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-SUBMIT"
  );
  assert.throws(
    () => normalizePaymentSubmitResult({ ...validResult, createdAt: "2026-09-27 12:00:00", expiresAt: "2026-09-27 11:59:00" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-SUBMIT"
  );
  assert.throws(
    () => normalizePaymentSubmitResult({ prepayOrderNo: "PRE-001" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-SUBMIT"
  );
  assert.throws(
    () => normalizePaymentSubmitResult(validResult, "PRE-002"),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-SUBMIT"
  );
  assert.throws(
    () => normalizePaymentSubmitResult({ ...validResult, orderNo: "" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-SUBMIT"
  );
  assert.throws(
    () => normalizePaymentSubmitResult({ ...validResult, amount: "invalid" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-SUBMIT"
  );
  assert.throws(
    () => normalizePaymentSubmitResult({ ...validResult, paymentStatus: "UNKNOWN_STATUS" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-SUBMIT"
  );
  assert.throws(
    () => normalizePaymentSubmitResult({ ...validResult, cashierStatusType: "success" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-SUBMIT"
  );
  assert.throws(
    () => normalizePaymentSubmitResult({ ...validResult, expiresAt: "not-a-time" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-SUBMIT"
  );
  assert.throws(
    () => normalizePaymentSubmitResult({ ...validResult, createdAt: "2026-02-30 11:59:00" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-SUBMIT"
  );
  assert.throws(
    () => normalizePaymentSubmitResult({
      ...validResult,
      paymentStatus: "SUCCESS",
      successAt: "2026-09-27 11:58:00"
    }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-SUBMIT"
  );
  assert.throws(
    () => normalizePaymentSubmitResult({ ...validResult, paymentStatus: "SUCCESS", cashierStatus: "待回调" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-SUBMIT"
  );
  assert.deepEqual(
    normalizePaymentSubmitResult({
      ...validResult,
      paymentStatus: "SUCCESS",
      successAt: "2026-09-27 12:01:00",
      cashierStatus: "支付成功",
      cashierStatusType: "success",
      channel: "WX_H5",
      channelTransactionNo: "WX-001",
      latestAttemptStatus: "成功",
      latestAttemptStatusType: "success",
      latestAttemptCompletedAt: "2026-09-27 12:00:00"
    }).channelTransactionNo,
    "WX-001"
  );
  assert.throws(
    () => normalizePaymentSubmitResult({
      ...validResult,
      paymentStatus: "SUCCESS",
      successAt: "2026-09-27 12:01:00",
      cashierStatus: "支付成功",
      cashierStatusType: "success",
      channelTransactionNo: "WX-001",
      latestAttemptStatus: "成功",
      latestAttemptStatusType: "success",
      latestAttemptCompletedAt: "2026-09-27 12:00:00"
    }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-SUBMIT"
  );
  for (const invalidSuccessfulResult of [
    { paymentStatus: "SUCCESS", successAt: "2026-09-27 12:01:00", cashierStatus: "支付成功", cashierStatusType: "success", channel: "WX_H5", channelTransactionNo: "WX-001" },
    { paymentStatus: "SUCCESS", successAt: "2026-09-27 12:01:00", cashierStatus: "支付成功", cashierStatusType: "success", channel: "WX_H5", channelTransactionNo: "WX-001", latestAttemptStatus: "失败", latestAttemptStatusType: "danger" },
    { paymentStatus: "SUCCESS", successAt: "2026-09-27 12:01:00", cashierStatus: "支付成功", cashierStatusType: "success", channel: "WX_H5", channelTransactionNo: "WX-001", latestAttemptStatus: "成功", latestAttemptStatusType: "warn", latestAttemptCompletedAt: "2026-09-27 12:00:00" },
    { paymentStatus: "SUCCESS", successAt: "2026-09-27 12:01:00", cashierStatus: "支付成功", cashierStatusType: "success", channel: "WX_H5", channelTransactionNo: "WX-001", latestAttemptStatus: "成功", latestAttemptStatusType: "success", latestAttemptCompletedAt: "2026-09-27 12:02:00" }
  ]) {
    assert.throws(
      () => normalizePaymentSubmitResult({ ...validResult, ...invalidSuccessfulResult }),
      (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-SUBMIT"
    );
  }
  for (const nativePaymentParams of [
    [],
    { timeStamp: "not-a-timestamp", nonceStr: "nonce", package: "prepay_id=wx-prepay", signType: "RSA", paySign: "signature" },
    { timeStamp: "1710000000", nonceStr: "nonce", package: "wx-prepay", signType: "RSA", paySign: "signature" },
    { timeStamp: "1710000000", nonceStr: "nonce", package: "prepay_id=wx-prepay", signType: "MD5", paySign: "signature" },
    { timeStamp: "1710000000", nonceStr: "nonce", package: "prepay_id=wx-prepay", signType: "RSA" }
  ]) {
    assert.throws(
      () => normalizePaymentSubmitResult({ ...validResult, nativePaymentParams }),
      (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-SUBMIT"
    );
  }
  installRequestMock((options) => {
    options.success({ statusCode: 200, data: { code: "0", data: null } });
  });

  await assert.rejects(
    paymentApi.submit({ prepayOrderNo: "PRE-001" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-SUBMIT"
  );
});

test("rejects mini program query and close responses without status presentation facts", async () => {
  const validResult = { prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", orderNo: "ORDER-001", amount: "68.00", status: "SUCCESS", statusType: "success", channel: "WX_H5", channelTransactionNo: "WX-001", successAt: "2026-09-27 12:01:00", createdAt: "2026-09-27 11:59:00", latestAttemptStatus: "成功", latestAttemptStatusType: "success", latestAttemptCompletedAt: "2026-09-27 12:00:00" };
  assert.deepEqual(normalizePaymentActionResult(validResult), validResult);
  assert.throws(
    () => normalizePaymentActionResult({ ...validResult, channelTransactionNo: undefined }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult({ ...validResult, channel: undefined }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult({ ...validResult, latestAttemptStatus: undefined, latestAttemptStatusType: undefined, latestAttemptCompletedAt: undefined }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult({ paymentOrderId: "PAY-001" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult(validResult, "PAY-002"),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-ACTION"
  );
  assert.doesNotThrow(() => normalizePaymentActionResult(validResult, "PAY-001", {
    prepayOrderNo: "PRE-001", orderNo: "ORDER-001", amount: "68.00"
  }));
  assert.doesNotThrow(() => normalizePaymentActionResult(validResult, "PAY-001", {
    prepayOrderNo: "PRE-001", orderNo: "ORDER-001", amount: "68.00", channel: "WX_H5"
  }));
  assert.doesNotThrow(() => normalizePaymentActionResult(validResult, "PAY-001", {
    prepayOrderNo: "PRE-001", orderNo: "ORDER-001", amount: "68.00", channel: "WX_H5", channelTransactionNo: "WX-001"
  }));
  assert.throws(
    () => normalizePaymentActionResult(validResult, "PAY-001", {
      prepayOrderNo: "PRE-001", orderNo: "ORDER-001", amount: "68.00", channel: "ALIPAY_H5"
    }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult(validResult, "PAY-001", {
      prepayOrderNo: "PRE-001", orderNo: "ORDER-001", amount: "68.00", channel: "WX_H5", channelTransactionNo: "WX-002"
    }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-ACTION"
  );
  const preservedFacts = {
    billNo: "BILL-001",
    customerName: "张三",
    paymentMethod: "微信支付"
  };
  assert.doesNotThrow(() => normalizePaymentActionResult(
    { ...validResult, ...preservedFacts },
    "PAY-001",
    { ...preservedFacts }
  ));
  for (const field of Object.keys(preservedFacts)) {
    assert.throws(
      () => normalizePaymentActionResult(
        { ...validResult, ...preservedFacts },
        "PAY-001",
        { ...preservedFacts, [field]: field === "customerName" ? "李四" : `${preservedFacts[field]}-OTHER` }
      ),
      (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-ACTION"
    );
  }
  assert.doesNotThrow(() => normalizePaymentActionResult(
    validResult,
    "PAY-001",
    { ...preservedFacts }
  ));
  assert.throws(
    () => normalizePaymentActionResult(validResult, "PAY-001", {
      prepayOrderNo: "PRE-002", orderNo: "ORDER-001", amount: "68.00"
    }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult(validResult, "PAY-001", {
      prepayOrderNo: "PRE-001", orderNo: "ORDER-002", amount: "68.00"
    }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult(validResult, "PAY-001", {
      prepayOrderNo: "PRE-001", orderNo: "ORDER-001", amount: "67.99"
    }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-ACTION"
  );
  assert.deepEqual(
    normalizePaymentActionResult({
      ...validResult,
      latestAttemptStatus: "成功",
      latestAttemptStatusType: "success",
      latestAttemptCompletedAt: "2026-09-27 12:00:00"
    }),
    {
      ...validResult,
      latestAttemptStatus: "成功",
      latestAttemptStatusType: "success",
      latestAttemptCompletedAt: "2026-09-27 12:00:00"
    }
  );
  assert.throws(
    () => normalizePaymentActionResult({ ...validResult, amount: "" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult({ ...validResult, status: "" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult({ ...validResult, status: "UNKNOWN_STATUS" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-ACTION"
  );
  for (const invalidResult of [
    { ...validResult, businessChainId: undefined },
    { ...validResult, businessChainId: "CHAIN-OTHER" },
    { ...validResult, statusType: "" },
    { ...validResult, statusType: "invalid" },
    { ...validResult, status: "WAIT_CALLBACK", statusType: "success" },
    { ...validResult, successAt: undefined },
    { ...validResult, status: "WAIT_CALLBACK", statusType: "warn", successAt: "2026-09-27 12:01:00" },
    { ...validResult, successAt: "2026-09-27 11:58:00" },
    { ...validResult, latestAttemptStatus: "等待回调" },
    { ...validResult, latestAttemptStatus: "等待回调", latestAttemptStatusType: "" },
    { ...validResult, latestAttemptStatus: "等待回调", latestAttemptStatusType: "invalid" },
    { ...validResult, latestAttemptStatusType: "warn" },
    { ...validResult, latestAttemptStatus: "成功", latestAttemptCompletedAt: undefined },
    { ...validResult, latestAttemptStatus: "成功", latestAttemptStatusType: "success", latestAttemptCompletedAt: "2026-09-27 11:58:00" },
    { ...validResult, latestAttemptStatus: "成功", latestAttemptStatusType: "danger", latestAttemptCompletedAt: "2026-09-27 12:01:00" },
    { ...validResult, latestAttemptStatus: "失败", latestAttemptStatusType: "success" },
    { ...validResult, latestAttemptStatus: "等待回调", latestAttemptCompletedAt: "2026-09-27 12:01:00" },
    { ...validResult, createdAt: undefined },
    { ...validResult, createdAt: "2026-02-30 11:59:00" }
  ]) {
    assert.throws(
      () => normalizePaymentActionResult(invalidResult),
      (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-ACTION"
    );
  }
  let call = 0;
  installRequestMock((options) => {
    options.success({
      statusCode: 200,
      data: { code: "0", data: call++ === 0 ? null : { paymentOrderId: "PAY-001" } }
    });
  });

  await assert.rejects(
    paymentApi.query("PAY-001"),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-ACTION"
  );
  await assert.rejects(
    paymentApi.close("PAY-001"),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-ACTION"
  );
});

test("rejects a mini program refund detail response without status presentation facts", async () => {
  const validAttempt = {
    refundAttemptNo: "ATT-001",
    attemptStatus: "PENDING",
    attemptStatusType: "warn",
    createdAt: "2026-09-27 12:00:01",
    submittedAt: "2026-09-27 12:00:02",
    completedAt: "-"
  };
  const validResult = { refundOrderId: "REF-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", orderNo: "ORDER-001", paidAmount: "68.00", refundAmount: "18.00", refundMethod: "原路退款", refundReason: "客户申请", status: "PROCESSING", statusType: "warn", appliedAt: "2026-09-27 12:00:00", attempts: [validAttempt], operationLogs: [] };
  assert.deepEqual(normalizeRefundDetailResult(validResult), validResult);
  assert.deepEqual(
    normalizeRefundDetailResult({ ...validResult, payScene: "BALANCE_PAY_MINI_PROGRAM" }, "REF-001", " BALANCE_PAY_MINI_PROGRAM "),
    { ...validResult, payScene: "BALANCE_PAY_MINI_PROGRAM" }
  );
  assert.throws(
    () => normalizeRefundDetailResult({ ...validResult, payScene: "WALLET_RECHARGE_MINI_PROGRAM" }, "REF-001", "BALANCE_PAY_MINI_PROGRAM"),
    (error) => error.code === "PAYMENT-MINI-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult(validResult, "REF-002"),
    (error) => error.code === "PAYMENT-MINI-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult({ paymentOrderId: "PAY-001" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult({ refundOrderId: "REF-001", paymentOrderId: "" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-REFUND-DETAIL"
  );
  for (const invalidResult of [
    { ...validResult, orderNo: undefined },
    { ...validResult, orderNo: " " },
    { ...validResult, businessChainId: undefined },
    { ...validResult, businessChainId: "CHAIN-OTHER" },
    { ...validResult, paidAmount: undefined },
    { ...validResult, paidAmount: "17.99" },
    { ...validResult, refundMethod: undefined },
    { ...validResult, refundReason: undefined },
    { ...validResult, refundAmount: "68.01" }
  ]) {
    assert.throws(
      () => normalizeRefundDetailResult(invalidResult),
      (error) => error.code === "PAYMENT-MINI-INVALID-REFUND-DETAIL"
    );
  }
  assert.throws(
    () => normalizeRefundDetailResult({ ...validResult, status: "" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult({ ...validResult, status: "UNKNOWN_STATUS" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-REFUND-DETAIL"
  );
  for (const invalidResult of [
    { ...validResult, statusType: "" },
    { ...validResult, statusType: "invalid" },
    { ...validResult, status: "SUCCESS", statusType: "warn" }
  ]) {
    assert.throws(
      () => normalizeRefundDetailResult(invalidResult),
      (error) => error.code === "PAYMENT-MINI-INVALID-REFUND-DETAIL"
    );
  }
  for (const invalidResult of [
    { ...validResult, attempts: "退款尝试损坏" },
    {
      ...validResult,
      attempts: [
        validAttempt,
        { ...validAttempt, refundAttemptNo: "ATT-002", createdAt: "2026-09-27 12:00:02", submittedAt: "2026-09-27 12:00:03" }
      ]
    },
    { ...validResult, operationLogs: {} },
    { ...validResult, operationLogs: ["退款日志损坏"] },
    {
      ...validResult,
      status: "SUCCESS",
      statusType: "success",
      channelCode: "WX_H5",
      channelRefundNo: "CH-REF-001",
      channelSubmittedAt: "2026-09-27 12:00:30",
      successAt: "2026-09-27 12:01:00",
      terminalAt: "2026-09-27 12:01:01",
      attempts: [{
        ...validAttempt,
        attemptStatus: "SUCCESS",
        attemptStatusType: "success",
        submittedAt: "2026-09-27 12:01:02",
        completedAt: "2026-09-27 12:01:03"
      }]
    },
    { ...validResult, operationLogs: [{ logNo: "LOG-001", actionName: "申请退款", createdAt: "2026-09-27 11:59:59" }] },
    {
      ...validResult,
      operationLogs: [
        { logNo: "LOG-001", actionName: "申请退款", createdAt: "2026-09-27 12:00:00" },
        { logNo: "LOG-002", actionName: "审核退款", createdAt: "2026-09-27 12:00:01" }
      ]
    },
    {
      ...validResult,
      status: "SUCCESS",
      statusType: "success",
      channelCode: "WX_H5",
      channelRefundNo: "CH-REF-001",
      channelSubmittedAt: "2026-09-27 12:00:30",
      successAt: "2026-09-27 12:01:00",
      terminalAt: "2026-09-27 12:01:01",
      operationLogs: [{ logNo: "LOG-003", actionName: "退款成功", createdAt: "2026-09-27 12:01:02" }]
    },
    { ...validResult, status: "SUCCESS", statusType: "success" },
    { ...validResult, status: "SUCCESS", statusType: "success", successAt: "-" },
    { ...validResult, status: "SUCCESS", statusType: "success", successAt: "not-a-time" },
    { ...validResult, appliedAt: undefined },
    { ...validResult, appliedAt: "2026-02-30 12:00:00" },
    { ...validResult, status: "SUCCESS", statusType: "success", successAt: "2026-09-27 11:59:00", terminalAt: "2026-09-27 12:01:01" },
    { ...validResult, status: "SUCCESS", statusType: "success", successAt: "2026-09-27 12:01:00", terminalAt: "2026-09-27 12:00:30" },
    { ...validResult, status: "FAIL", statusType: "danger", channelCode: "WX_H5", channelSubmittedAt: "2026-09-27 12:00:30", terminalAt: "2026-09-27 11:59:00" },
    { ...validResult, channelSubmittedAt: "not-a-time" },
    { ...validResult, channelSubmittedAt: "2026-09-27 11:59:59" },
    {
      ...validResult,
      status: "SUCCESS",
      statusType: "success",
      successAt: "2026-09-27 12:01:00",
      terminalAt: "2026-09-27 12:01:01",
      channelSubmittedAt: "2026-09-27 12:01:02"
    }
  ]) {
    assert.throws(
      () => normalizeRefundDetailResult(invalidResult),
      (error) => error.code === "PAYMENT-MINI-INVALID-REFUND-DETAIL"
    );
  }
  for (const invalidResult of [
    { ...validResult, attempts: [{ ...validAttempt, refundAttemptNo: "" }] },
    { ...validResult, attempts: [{ ...validAttempt, attemptStatus: "SUCCESS", attemptStatusType: "success" }] },
    { ...validResult, attempts: [{ ...validAttempt, attemptStatus: "FAILED", attemptStatusType: "success" }] },
    { ...validResult, attempts: [{ ...validAttempt, completedAt: "2026-09-27 12:00:03" }] },
    { ...validResult, attempts: [{ ...validAttempt, createdAt: "not-a-time" }] },
    { ...validResult, attempts: [{ ...validAttempt, createdAt: "2026-09-27 11:59:59" }] },
    { ...validResult, attempts: [{ ...validAttempt, submittedAt: "2026-09-27 12:00:00" }] },
    { ...validResult, attempts: [{ ...validAttempt, completedAt: "2026-09-27 12:00:01" }] },
    { ...validResult, attempts: [{ ...validAttempt, completedAt: "not-a-time" }] }
  ]) {
    assert.throws(
      () => normalizeRefundDetailResult(invalidResult),
      (error) => error.code === "PAYMENT-MINI-INVALID-REFUND-DETAIL"
    );
  }
  for (const invalidResult of [
    { ...validResult, status: "FAIL", statusType: "danger" },
    { ...validResult, status: "SUCCESS", statusType: "success", successAt: "2026-09-27 12:01:00", terminalAt: "2026-09-27 12:01:01" },
    { ...validResult, status: "SUCCESS", statusType: "success", successAt: "2026-09-27 12:01:00", terminalAt: "-" }
  ]) {
    assert.throws(
      () => normalizeRefundDetailResult(invalidResult),
      (error) => error.code === "PAYMENT-MINI-INVALID-REFUND-DETAIL"
    );
  }
  assert.throws(
    () => normalizeRefundDetailResult({
      ...validResult,
      status: "FAIL",
      statusType: "danger",
      channelCode: "WX_H5",
      channelSubmittedAt: "2026-09-27 12:00:30",
      terminalAt: "2026-09-27 12:01:01"
    }),
    (error) => error.code === "PAYMENT-MINI-INVALID-REFUND-DETAIL"
  );
  assert.deepEqual(
    normalizeRefundDetailResult({
      ...validResult,
      status: "SUCCESS",
      statusType: "success",
      channelCode: "WX_H5",
      channelRefundNo: "CH-REF-001",
      channelSubmittedAt: "2026-09-27 12:00:30",
      successAt: "2026-09-27 12:01:00",
      terminalAt: "2026-09-27 12:01:01"
    }),
    {
      ...validResult,
      status: "SUCCESS",
      statusType: "success",
      channelCode: "WX_H5",
      channelRefundNo: "CH-REF-001",
      channelSubmittedAt: "2026-09-27 12:00:30",
      successAt: "2026-09-27 12:01:00",
      terminalAt: "2026-09-27 12:01:01"
    }
  );
  assert.throws(
    () => normalizeRefundDetailResult({
      ...validResult,
      status: "SUCCESS",
      statusType: "success",
      channelRefundNo: "CH-REF-001",
      successAt: "2026-09-27 12:01:00",
      terminalAt: "2026-09-27 12:01:01"
    }),
    (error) => error.code === "PAYMENT-MINI-INVALID-REFUND-DETAIL"
  );
  installRequestMock((options) => {
    options.success({ statusCode: 200, data: { code: "0", data: null } });
  });

  await assert.rejects(
    paymentApi.getRefundDetail("REF-001"),
    (error) => error.code === "PAYMENT-MINI-INVALID-REFUND-DETAIL"
  );
});

test("rejects a mini program payment detail response without status presentation facts", async () => {
  const validResult = { paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", businessChainId: "CHAIN-PAY-001", orderNo: "ORDER-001", amount: "68.00", status: "SUCCESS", statusType: "success", channel: "WX_H5", channelTransactionNo: "WX-001", successAt: "2026-09-27 12:01:00", createdAt: "2026-09-27 11:59:00", latestAttemptStatus: "成功", latestAttemptStatusType: "success", latestAttemptCompletedAt: "2026-09-27 12:00:00", routeLogs: [], notifyLogs: [], eventLogs: [] };
  assert.deepEqual(normalizePaymentDetailResult(validResult), validResult);
  assert.throws(
    () => normalizePaymentDetailResult({ ...validResult, channelTransactionNo: undefined }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDetailResult({ ...validResult, latestAttemptStatus: undefined, latestAttemptStatusType: undefined, latestAttemptCompletedAt: undefined }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDetailResult({ ...validResult, channel: undefined }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-DETAIL"
  );
  const successfulAttemptResult = {
    ...validResult,
    latestAttemptStatus: "成功",
    latestAttemptStatusType: "success",
    latestAttemptCompletedAt: "2026-09-27 12:01:00"
  };
  assert.deepEqual(normalizePaymentDetailResult(successfulAttemptResult), successfulAttemptResult);
  assert.throws(
    () => normalizePaymentDetailResult(validResult, "PAY-002"),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDetailResult({ prepayOrderNo: "PRE-001" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDetailResult({ ...validResult, amount: "invalid" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-DETAIL"
  );
  for (const invalidResult of [
    { ...validResult, businessChainId: undefined },
    { ...validResult, businessChainId: "CHAIN-OTHER" },
    { ...validResult, routeLogs: "路由轨迹损坏" },
    { ...validResult, notifyLogs: {} },
    { ...validResult, eventLogs: 123 },
    { ...validResult, routeLogs: ["路由完成", { malformed: true }] },
    { ...validResult, notifyLogs: ["通知完成", 2] },
    { ...validResult, eventLogs: ["事件完成", false] },
    { ...validResult, successAt: "-" },
    { ...validResult, successAt: "not-a-time" },
    { ...validResult, createdAt: undefined },
    { ...validResult, createdAt: "2026-02-30 11:59:00" },
    { ...validResult, status: "WAIT_CALLBACK", statusType: "warn", successAt: "2026-09-27 12:01:00" },
    { ...validResult, successAt: "2026-09-27 11:58:00" },
    { ...successfulAttemptResult, latestAttemptCompletedAt: undefined },
    { ...successfulAttemptResult, latestAttemptCompletedAt: "not-a-time" },
    { ...successfulAttemptResult, latestAttemptCompletedAt: "2026-09-27 11:58:00" },
    { ...successfulAttemptResult, latestAttemptCompletedAt: "2026-09-27 12:01:01" },
    { ...validResult, latestAttemptStatus: "等待回调", latestAttemptStatusType: "warn", latestAttemptCompletedAt: "2026-09-27 12:01:05" },
    { ...validResult, latestAttemptStatus: "成功", latestAttemptStatusType: "danger", latestAttemptCompletedAt: "2026-09-27 12:01:05" },
    { ...validResult, latestAttemptStatus: "失败", latestAttemptStatusType: "success" }
  ]) {
    assert.throws(
      () => normalizePaymentDetailResult(invalidResult),
      (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-DETAIL"
    );
  }
  for (const invalidResult of [
    { ...validResult, statusType: "" },
    { ...validResult, statusType: "invalid" },
    { ...validResult, status: "WAIT_CALLBACK", statusType: "success" },
    { ...validResult, latestAttemptStatus: "等待回调" },
    { ...validResult, latestAttemptStatus: "等待回调", latestAttemptStatusType: "" },
    { ...validResult, latestAttemptStatus: "等待回调", latestAttemptStatusType: "invalid" },
    { ...validResult, latestAttemptStatusType: "warn" }
  ]) {
    assert.throws(
      () => normalizePaymentDetailResult(invalidResult),
      (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-DETAIL"
    );
  }
  installRequestMock((options) => {
    options.success({ statusCode: 200, data: { code: "0", data: null } });
  });

  await assert.rejects(
    paymentApi.getDetail("PAY-001"),
    (error) => error.code === "PAYMENT-MINI-INVALID-PAYMENT-DETAIL"
  );
});

test("rejects a mini program cashier response without a prepay order number", async () => {
  const validResult = {
    prepayOrderNo: "PRE-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    amount: "68.00",
    status: "待支付",
    statusType: "warn",
    paymentStatus: "PREPAY_CREATED",
    expiresAt: "2026-09-27 12:00:00",
    channels: []
  };
  assert.deepEqual(normalizeCashierPageResult(validResult), validResult);
  const waitingResult = {
    ...validResult, status: "待回调", statusType: "warn", paymentStatus: "WAIT_CALLBACK",
    latestAttemptStatus: "处理中", latestAttemptStatusType: "info",
    paymentMethod: "微信支付", channel: "wx_h5"
  };
  assert.deepEqual(normalizeCashierPageResult(waitingResult), waitingResult);
  for (const channel of [null, " ", 123]) {
    assert.throws(() => normalizeCashierPageResult({ ...waitingResult, channel }),
      (error) => error.code === "PAYMENT-MINI-INVALID-CASHIER");
  }
  for (const paymentMethod of [undefined, " "]) {
    assert.throws(() => normalizeCashierPageResult({ ...waitingResult, paymentMethod }),
      (error) => error.code === "PAYMENT-MINI-INVALID-CASHIER");
  }
  assert.throws(
    () => normalizeCashierPageResult(
      { ...validResult, payScene: "WALLET_RECHARGE_MINI_PROGRAM" },
      "PRE-001",
      "BALANCE_PAY_MINI_PROGRAM"
    ),
    (error) => error.code === "PAYMENT-MINI-INVALID-CASHIER"
  );
  const successfulCashierResult = {
    ...validResult,
    status: "支付成功",
    statusType: "success",
    paymentStatus: "SUCCESS",
    channel: "wx_h5",
    paymentMethod: "微信支付",
    channelTransactionNo: "WX-001",
    latestAttemptStatus: "成功",
    latestAttemptStatusType: "success",
    latestAttemptCompletedAt: "2026-09-27 12:00:01"
  };
  assert.deepEqual(normalizeCashierPageResult(successfulCashierResult), successfulCashierResult);
  for (const invalidSuccessfulResult of [
    { ...successfulCashierResult, channel: undefined },
    { ...successfulCashierResult, channel: " " },
    { ...successfulCashierResult, paymentMethod: undefined },
    { ...successfulCashierResult, paymentMethod: " " },
    { ...successfulCashierResult, channelTransactionNo: undefined },
    { ...successfulCashierResult, channelTransactionNo: " " },
    { ...successfulCashierResult, latestAttemptStatus: undefined, latestAttemptStatusType: undefined, latestAttemptCompletedAt: undefined },
    { ...successfulCashierResult, latestAttemptStatus: "失败", latestAttemptStatusType: "danger" },
    { ...successfulCashierResult, latestAttemptStatusType: "warn" },
    { ...successfulCashierResult, latestAttemptCompletedAt: undefined },
    { ...successfulCashierResult, latestAttemptCompletedAt: "not-a-time" }
  ]) {
    assert.throws(
      () => normalizeCashierPageResult(invalidSuccessfulResult),
      (error) => error.code === "PAYMENT-MINI-INVALID-CASHIER"
    );
  }
  assert.throws(
    () => normalizeCashierPageResult(validResult, "PRE-002"),
    (error) => error.code === "PAYMENT-MINI-INVALID-CASHIER"
  );
  assert.throws(
    () => normalizeCashierPageResult({ channels: [] }),
    (error) => error.code === "PAYMENT-MINI-INVALID-CASHIER"
  );
  assert.throws(
    () => normalizeCashierPageResult({ prepayOrderNo: "PRE-001", paymentOrderId: "" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-CASHIER"
  );
  assert.throws(
    () => normalizeCashierPageResult({ prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", amount: "" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-CASHIER"
  );
  assert.throws(
    () => normalizeCashierPageResult({ prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", amount: "68.00" }),
    (error) => error.code === "PAYMENT-MINI-INVALID-CASHIER"
  );
  for (const invalidResult of [
    { ...validResult, status: "未知状态" },
    { ...validResult, statusType: "success" },
    { ...validResult, paymentStatus: "UNKNOWN_STATUS" },
    { ...validResult, paymentStatus: "SUCCESS", status: "待支付", statusType: "warn" },
    { ...validResult, expiresAt: "" },
    { ...validResult, expiresAt: "not-a-time" },
    { ...validResult, expiresAt: "2026-02-30 12:00:00" },
    { ...validResult, latestAttemptStatus: 123 },
    { ...validResult, latestAttemptStatus: "" },
    { ...validResult, latestAttemptStatus: "成功", latestAttemptStatusType: "warn", latestAttemptCompletedAt: "2026-09-27 12:00:01" },
    { ...validResult, latestAttemptStatus: "成功", latestAttemptStatusType: "success" },
    { ...validResult, latestAttemptStatus: "处理中", latestAttemptStatusType: "info", latestAttemptCompletedAt: "2026-09-27 12:00:01" },
    { ...validResult, latestAttemptStatusType: "warn" },
    { ...validResult, channels: ["未知渠道"] },
    { ...validResult, channels: [" 微信支付"] },
    { ...validResult, channels: ["微信支付", "微信支付"] },
    { ...validResult, channels: [""] },
    { ...validResult, channels: [null] },
    { ...validResult, businessChainId: undefined },
    { ...validResult, businessChainId: "CHAIN-OTHER" }
  ]) {
    assert.throws(
      () => normalizeCashierPageResult(invalidResult),
      (error) => error.code === "PAYMENT-MINI-INVALID-CASHIER"
    );
  }
  installRequestMock((options) => {
    options.success({ statusCode: 200, data: { code: "0", data: null } });
  });

  await assert.rejects(
    paymentApi.getCashier("PRE-001"),
    (error) => error.code === "PAYMENT-MINI-INVALID-CASHIER"
  );
});

test("filters null rows from mini program detail responses", async () => {
  let call = 0;
  installRequestMock((options) => {
    const data = call++ === 0
        ? { prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", orderNo: "ORDER-001", amount: "68.00", status: "待支付", statusType: "warn", paymentStatus: "PREPAY_CREATED", expiresAt: "2026-09-27 12:00:00", channels: ["微信支付"] }
      : call === 2
        ? { paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", prepayOrderNo: "PRE-001", orderNo: "ORDER-001", amount: "68.00", status: "SUCCESS", statusType: "success", channel: "WX_H5", channelTransactionNo: "WX-001", successAt: "2026-09-27 12:01:00", createdAt: "2026-09-27 11:59:00", latestAttemptStatus: "成功", latestAttemptStatusType: "success", latestAttemptCompletedAt: "2026-09-27 12:00:00", routeLogs: [null, "路由完成"], notifyLogs: null, eventLogs: ["事件完成"] }
        : { refundOrderId: "REF-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", orderNo: "ORDER-001", paidAmount: "68.00", refundAmount: "18.00", refundMethod: "原路退款", refundReason: "客户申请", status: "PROCESSING", statusType: "warn", appliedAt: "2026-09-27 12:00:00", attempts: [null, { refundAttemptNo: "ATT-001", attemptStatus: "PENDING", attemptStatusType: "warn", createdAt: "2026-09-27 12:00:01", submittedAt: "-", completedAt: "-" }], operationLogs: [null, { logNo: "LOG-001", actionName: "申请退款", createdAt: "2026-09-27 12:00:00" }] };
    options.success({ statusCode: 200, data: { code: "0", data } });
  });

  const cashier = await paymentApi.getCashier("PRE-001");
  const payment = await paymentApi.getDetail("PAY-001");
  const refund = await paymentApi.getRefundDetail("REF-001");
  assert.deepEqual(cashier.channels, ["微信支付"]);
  assert.deepEqual(payment.routeLogs, ["路由完成"]);
  assert.deepEqual(payment.notifyLogs, []);
  assert.deepEqual(refund.attempts, [{
    refundAttemptNo: "ATT-001",
    attemptStatus: "PENDING",
    attemptStatusType: "warn",
    createdAt: "2026-09-27 12:00:01",
    submittedAt: "-",
    completedAt: "-"
  }]);
  assert.deepEqual(refund.operationLogs, [{
    logNo: "LOG-001",
    actionName: "申请退款",
    createdAt: "2026-09-27 12:00:00"
  }]);
});
