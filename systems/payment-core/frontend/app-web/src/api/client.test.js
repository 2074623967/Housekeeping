import assert from "node:assert/strict";
import test from "node:test";
import {
  normalizePaymentActionResult,
  normalizePaymentDetailResult,
  normalizePaymentSubmitResult,
  normalizePrepayResult,
  normalizeCashierPageResult,
  normalizeRefundDetailResult,
  paymentApi
} from "./client.js";

test("rejects terminal attempts in pending user payment snapshots", () => {
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
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY");
    assert.throws(() => normalizeCashierPageResult({ ...cashier, ...attempt }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER");
    assert.throws(() => normalizePaymentSubmitResult({ ...submission, ...attempt }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-SUBMIT");
  }
});

test("keeps failed payment attempt styles and success facts consistent", () => {
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
      }), (error) => error.code.startsWith("PAYMENT-CLIENT-INVALID-PAYMENT-"));
    }
  }
});

function successfulResponse(data = {
  paymentOrderId: "PAY-001",
  businessChainId: "CHAIN-PAY-001",
  prepayOrderNo: "PRE-001",
  orderNo: "ORDER-001",
  amount: "68.00",
  channels: [],
  status: "SUCCESS",
  statusType: "success",
  paidAmount: "68.00",
  refundAmount: "18.00",
  refundMethod: "原路退款",
  refundReason: "客户申请",
  refundOrderId: "REF-001"
}) {
  return {
    ok: true,
    json: async () => ({
      code: "0",
      data
    })
  };
}

test("encodes cashier and payment detail identifiers as single path segments", async (context) => {
  const urls = [];
  context.mock.method(globalThis, "fetch", async (url) => {
    urls.push(url);
    if (url.startsWith("/api/payments/cashier/")) {
      return successfulResponse({ paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", prepayOrderNo: "PRE/1?#%", orderNo: "ORDER-001", amount: "68.00", status: "待支付", statusType: "warn", paymentStatus: "PREPAY_CREATED", expiresAt: "2026-09-27 12:00:00", channels: [], refundOrderId: "REF-001" });
    }
    if (url.startsWith("/api/payments/")) {
      return successfulResponse({ paymentOrderId: "PAY/1?#%", businessChainId: "CHAIN-PAY/1?#%", prepayOrderNo: "PRE-001", orderNo: "ORDER-001", amount: "68.00", status: "SUCCESS", statusType: "success", channel: "WX_H5", channelTransactionNo: "WX-001", successAt: "2026-09-27 12:01:00", createdAt: "2026-09-27 11:59:00", latestAttemptStatus: "成功", latestAttemptStatusType: "success", latestAttemptCompletedAt: "2026-09-27 12:00:00", refundOrderId: "REF-001" });
    }
      return successfulResponse({ paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", prepayOrderNo: "PRE-001", orderNo: "ORDER-001", amount: "68.00", status: "SUCCESS", statusType: "success", paidAmount: "68.00", refundAmount: "18.00", refundMethod: "原路退款", refundReason: "客户申请", refundOrderId: "REF/1?#%", channelCode: "WX_H5", channelRefundNo: "CH-REF-001", channelSubmittedAt: "2026-09-27 12:00:30", appliedAt: "2026-09-27 12:00:00", successAt: "2026-09-27 12:01:00", terminalAt: "2026-09-27 12:01:01" });
  });

  await paymentApi.getCashier("PRE/1?#%");
  await paymentApi.getDetail("PAY/1?#%");
  await paymentApi.getRefundDetail("REF/1?#%");

  assert.deepEqual(urls, [
    "/api/payments/cashier/PRE%2F1%3F%23%25",
    "/api/payments/PAY%2F1%3F%23%25",
    "/api/refunds/REF%2F1%3F%23%25"
  ]);
});

test("passes known terminal context to the cashier endpoint", async (context) => {
  let requestedUrl = "";
  context.mock.method(globalThis, "fetch", async (url) => {
    requestedUrl = url;
    return successfulResponse({
      paymentOrderId: "PAY-001",
      businessChainId: "CHAIN-PAY-001",
      prepayOrderNo: "PRE-001",
      orderNo: "ORDER-001",
      amount: "68.00",
      status: "待支付",
      statusType: "warn",
      paymentStatus: "PREPAY_CREATED",
      payScene: "BALANCE_PAY_APP",
      expiresAt: "2026-09-27 12:00:00",
      channels: []
    });
  });

  await paymentApi.getCashier("PRE-001", "  PC_WEB  ", "BALANCE_PAY_APP");

  assert.equal(requestedUrl, "/api/payments/cashier/PRE-001?terminal=PC_WEB");
});

test("binds cashier responses to the requested payment scene", async (context) => {
  context.mock.method(globalThis, "fetch", async () => successfulResponse({
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    prepayOrderNo: "PRE-001",
    orderNo: "ORDER-001",
    amount: "68.00",
    status: "待支付",
    statusType: "warn",
    paymentStatus: "PREPAY_CREATED",
    payScene: "WALLET_RECHARGE_APP",
    expiresAt: "2026-09-27 12:00:00",
    channels: []
  }));

  await assert.rejects(
    paymentApi.getCashier("PRE-001", "APP_WEB", "BALANCE_PAY_APP"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER"
  );
});

test("trims cashier route identifiers before requesting and matching the response", async (context) => {
  let requestedUrl = "";
  context.mock.method(globalThis, "fetch", async (url) => {
    requestedUrl = url;
    return successfulResponse({
      paymentOrderId: "PAY-001",
      businessChainId: "CHAIN-PAY-001",
      prepayOrderNo: "PRE-001",
      orderNo: "ORDER-001",
      amount: "68.00",
      status: "待支付",
      statusType: "warn",
      paymentStatus: "PREPAY_CREATED",
      expiresAt: "2026-09-27 12:00:00",
      channels: []
    });
  });

  await paymentApi.getCashier("  PRE-001  ");

  assert.equal(requestedUrl, "/api/payments/cashier/PRE-001");
});

test("trims refund progress route identifiers before requesting and matching the response", async (context) => {
  let requestedUrl = "";
  context.mock.method(globalThis, "fetch", async (url) => {
    requestedUrl = url;
    return successfulResponse({
      refundOrderId: "REF-001",
      paymentOrderId: "PAY-001",
      businessChainId: "CHAIN-PAY-001",
      prepayOrderNo: "PRE-001",
      orderNo: "ORDER-001",
      paidAmount: "68.00",
      refundAmount: "18.00",
      refundMethod: "原路退款",
      refundReason: "客户申请",
      status: "PROCESSING",
      statusType: "warn",
      appliedAt: "2026-09-27 12:00:00",
      successAt: "-",
      terminalAt: "-",
      attempts: [],
      operationLogs: []
    });
  });

  await paymentApi.getRefundDetail("  REF-001  ");

  assert.equal(requestedUrl, "/api/refunds/REF-001");
});

test("trims payment action identifiers before sending and matching responses", async (context) => {
  const requests = [];
  context.mock.method(globalThis, "fetch", async (url, options) => {
    requests.push({ url, body: JSON.parse(options.body) });
    if (url === "/api/payments/submit") {
      return successfulResponse({
        prepayOrderNo: "PRE-001",
        paymentOrderId: "PAY-001",
        businessChainId: "CHAIN-PAY-001",
        orderNo: "ORDER-001",
        amount: "68.00",
        paymentStatus: "WAIT_CALLBACK",
        paymentMethod: "CARD",
        channel: "WX",
        createdAt: "2026-09-27 11:59:00",
        expiresAt: "2026-09-27 12:00:00",
        cashierStatus: "支付中",
        cashierStatusType: "info"
      });
    }
    return successfulResponse({
      paymentOrderId: "PAY-001",
      businessChainId: "CHAIN-PAY-001",
      prepayOrderNo: "PRE-001",
      orderNo: "ORDER-001",
      amount: "68.00",
      status: "SUCCESS",
      statusType: "success",
      channel: "WX_H5",
      channelTransactionNo: "WX-001",
      successAt: "2026-09-27 12:01:00",
      createdAt: "2026-09-27 11:59:00",
      latestAttemptStatus: "成功",
      latestAttemptStatusType: "success",
      latestAttemptCompletedAt: "2026-09-27 12:00:00"
    });
  });

  await paymentApi.submit({
    prepayOrderNo: "  PRE-001  ",
    paymentMethod: "  CARD  ",
    channelCode: "  WX  "
  });
  await paymentApi.query({ paymentOrderId: "  PAY-001  " });
  await paymentApi.close({ paymentOrderId: "  PAY-001  " });

  assert.deepEqual(requests, [
    {
      url: "/api/payments/submit",
      body: { prepayOrderNo: "PRE-001", paymentMethod: "CARD", channelCode: "WX" }
    },
    { url: "/api/payments/query", body: { paymentOrderId: "PAY-001" } },
    { url: "/api/payments/close", body: { paymentOrderId: "PAY-001" } }
  ]);
});

test("trims the prepay order identifier before creating a prepay order", async (context) => {
  let requestBody;
  context.mock.method(globalThis, "fetch", async (url, options) => {
    assert.equal(url, "/api/payments/prepay");
    requestBody = JSON.parse(options.body);
    return successfulResponse({
      prepayOrderNo: "PRE-001",
      paymentOrderId: "PAY-001",
      businessChainId: "CHAIN-PAY-001",
      orderNo: "ORDER-001",
      payScene: "BALANCE_PAY_APP",
      amount: "68.00",
      paymentStatus: "PREPAY_CREATED",
      createdAt: "2026-09-27 11:59:00",
      expiresAt: "2026-09-27 12:00:00",
      cashierStatus: "待支付",
      cashierStatusType: "warn"
    });
  });

  await paymentApi.prepay({ orderNo: "  ORDER-001  ", amount: "68.00", payScene: "  BALANCE_PAY_APP  " });

  assert.deepEqual(requestBody, {
    orderNo: "ORDER-001",
    amount: "68.00",
    payScene: "BALANCE_PAY_APP"
  });
});

test("rejects a prepay response with a different payment scene", () => {
  assert.throws(
    () => normalizePrepayResult({
      prepayOrderNo: "PRE-001",
      paymentOrderId: "PAY-001",
      businessChainId: "CHAIN-PAY-001",
      orderNo: "ORDER-001",
      customerName: "张三",
      payScene: "WALLET_RECHARGE_APP",
      amount: "68.00",
      paymentStatus: "PREPAY_CREATED",
      createdAt: "2026-09-27 11:59:00",
      expiresAt: "2026-09-27 12:00:00",
      cashierStatus: "待支付",
      cashierStatusType: "warn"
    }, "ORDER-001", "68.00", "张三", "BALANCE_PAY_APP"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
  );
});

test("keeps the prepay customer name bound to the request", () => {
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
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
  );
});

test("accepts a prepay response whose business identifiers contain boundary whitespace", () => {
  assert.doesNotThrow(() => normalizePrepayResult({
    prepayOrderNo: " PRE-001 ",
    paymentOrderId: " PAY-001 ",
    businessChainId: "CHAIN-PAY-001",
    orderNo: " ORDER-001 ",
    amount: "68.00",
    paymentStatus: "PREPAY_CREATED",
    createdAt: "2026-09-27 11:59:00",
    expiresAt: "2026-09-27 12:00:00",
    cashierStatus: "待支付",
    cashierStatusType: "warn"
  }, "ORDER-001", "68.00"));
});

test("trims the payment result identifier before requesting and matching the response", async (context) => {
  let requestedUrl = "";
  context.mock.method(globalThis, "fetch", async (url) => {
    requestedUrl = url;
    return successfulResponse({
      paymentOrderId: "PAY-001",
      businessChainId: "CHAIN-PAY-001",
      prepayOrderNo: "PRE-001",
      orderNo: "ORDER-001",
      amount: "68.00",
      status: "SUCCESS",
      statusType: "success",
      channel: "WX_H5",
      channelTransactionNo: "WX-001",
      successAt: "2026-09-27 12:01:00",
      createdAt: "2026-09-27 11:59:00",
      latestAttemptStatus: "成功",
      latestAttemptStatusType: "success",
      latestAttemptCompletedAt: "2026-09-27 12:00:00",
      routeLogs: [],
      notifyLogs: [],
      eventLogs: []
    });
  });

  await paymentApi.getDetail("  PAY-001  ");

  assert.equal(requestedUrl, "/api/payments/PAY-001");
});

test("rejects cashier responses with unknown status, style, or channel facts", () => {
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
    channels: ["微信支付"]
  };
  assert.deepEqual(normalizeCashierPageResult(validResult), validResult);
  const waitingResult = {
    ...validResult, status: "待回调", statusType: "warn", paymentStatus: "WAIT_CALLBACK",
    latestAttemptStatus: "处理中", latestAttemptStatusType: "info",
    paymentMethod: "支付宝", channel: "alipay_h5"
  };
  assert.deepEqual(normalizeCashierPageResult(waitingResult), waitingResult);
  for (const channel of [null, " ", 123]) {
    assert.throws(() => normalizeCashierPageResult({ ...waitingResult, channel }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER");
  }
  for (const paymentMethod of [undefined, " "]) {
    assert.throws(() => normalizeCashierPageResult({ ...waitingResult, paymentMethod }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER");
  }
  assert.throws(
    () => normalizeCashierPageResult({ ...validResult, payScene: "WALLET_RECHARGE_APP" }, "PRE-001", "BALANCE_PAY_APP"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER"
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
      (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER"
    );
  }
  for (const invalidResult of [
    { ...validResult, status: "未知状态" },
    { ...validResult, statusType: "success" },
    { ...validResult, paymentStatus: "UNKNOWN" },
    { ...validResult, paymentStatus: "SUCCESS", status: "待支付", statusType: "warn" },
    { ...validResult, expiresAt: "" },
    { ...validResult, expiresAt: "not-a-time" },
    { ...validResult, latestAttemptStatus: 123 },
    { ...validResult, latestAttemptStatus: "" },
    { ...validResult, latestAttemptStatus: "成功", latestAttemptStatusType: "warn", latestAttemptCompletedAt: "2026-09-27 12:01:00" },
    { ...validResult, latestAttemptStatus: "成功", latestAttemptStatusType: "danger", latestAttemptCompletedAt: "2026-09-27 12:01:00" },
    { ...validResult, latestAttemptStatus: "失败", latestAttemptStatusType: "success" },
    { ...validResult, latestAttemptStatus: "成功", latestAttemptStatusType: "success" },
    { ...validResult, latestAttemptStatus: "处理中", latestAttemptStatusType: "info", latestAttemptCompletedAt: "2026-09-27 12:01:00" },
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
      (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER"
    );
  }
});

test("reports non-JSON upstream responses with a stable client error", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: false,
    status: 502,
    json: async () => {
      throw new SyntaxError("Unexpected token < in JSON");
    }
  }));

  await assert.rejects(
    paymentApi.getDetail("PAY-INVALID-RESPONSE"),
    (error) => {
      assert.equal(error.code, "PAYMENT-CLIENT-INVALID-RESPONSE");
      assert.match(error.message, /无法解析/);
      return true;
    }
  );
});

test("rejects a prepay response without a prepay order number", async (context) => {
  assert.deepEqual(
    normalizePrepayResult({ prepayOrderNo: " PRE-001 ", paymentOrderId: " PAY-001 ", businessChainId: "CHAIN-PAY-001", orderNo: " ORDER-001 ", amount: "68.00", paymentStatus: "PREPAY_CREATED", createdAt: "2026-09-27 11:59:00", expiresAt: "2026-09-27 12:00:00", cashierStatus: "待支付", cashierStatusType: "warn" }),
    { prepayOrderNo: " PRE-001 ", paymentOrderId: " PAY-001 ", businessChainId: "CHAIN-PAY-001", orderNo: " ORDER-001 ", amount: "68.00", paymentStatus: "PREPAY_CREATED", createdAt: "2026-09-27 11:59:00", expiresAt: "2026-09-27 12:00:00", cashierStatus: "待支付", cashierStatusType: "warn" }
  );
  for (const businessChainId of [undefined, "CHAIN-OTHER"]) {
    assert.throws(
      () => normalizePrepayResult({ prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", businessChainId, orderNo: "ORDER-001", amount: "68.00", paymentStatus: "PREPAY_CREATED", createdAt: "2026-09-27 11:59:00", expiresAt: "2026-09-27 12:00:00", cashierStatus: "待支付", cashierStatusType: "warn" }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
    );
  }
  assert.throws(
    () => normalizePrepayResult(null),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePrepayResult({ prepayOrderNo: "PRE-001" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePrepayResult({ prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", orderNo: "ORDER-002" }, "ORDER-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePrepayResult({ prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", amount: "" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePrepayResult({ prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", amount: "1.234" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePrepayResult({ prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", amount: "1e3" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePrepayResult({ prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", amount: "6¥8" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePrepayResult({ prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", amount: "1,23" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePrepayResult({ prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", amount: "1.2.3" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePrepayResult({ prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", amount: ".5" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
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
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
  );
  assert.equal(
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
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
    );
  }
  assert.throws(
    () => normalizePrepayResult(
      { prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", amount: "68.00", paymentStatus: "PREPAY_CREATED", createdAt: "2026-09-27 12:00:00", expiresAt: "2026-09-27 11:59:00", cashierStatus: "待支付", cashierStatusType: "warn" },
      "ORDER-001",
      "68.00"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePrepayResult(
      { prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", amount: "67.99" },
      "ORDER-001",
      "68.00"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
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
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
  );
  assert.throws(
    () => normalizePrepayResult({
      prepayOrderNo: "PRE-001",
      paymentOrderId: "PAY-001",
      orderNo: "ORDER-001",
      amount: "68.00",
      paymentStatus: "PREPAY_CREATED",
      expiresAt: "2026-02-30 12:00:00"
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
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
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
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
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
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
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
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
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  await assert.rejects(
    paymentApi.prepay({ orderNo: "ORDER-001" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PREPAY"
  );
});

test("rejects a payment submit response without payment and cashier facts", async (context) => {
  const validResult = { prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", orderNo: "ORDER-001", amount: "68.00", paymentStatus: "WAIT_CALLBACK", createdAt: "2026-09-27 11:59:00", expiresAt: "2026-09-27 12:00:00", cashierStatus: "支付中", cashierStatusType: "info", status: "SUCCESS" };
  assert.deepEqual(normalizePaymentSubmitResult(validResult), validResult);
  assert.throws(
    () => normalizePaymentSubmitResult({
      ...validResult,
      paymentStatus: "PREPAY_CREATED",
      cashierStatus: "待支付",
      cashierStatusType: "warn",
      paymentMethod: "微信支付",
      channel: "WX_H5"
    }, "PRE-001", "微信支付", "WX_H5"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-SUBMIT"
      && error.message.includes("状态未推进")
  );
  assert.throws(
    () => normalizePaymentSubmitResult({
      ...validResult,
      paymentStatus: "PAYING",
      paymentMethod: "微信支付",
      channel: "WX_H5"
    }, "PRE-001", "微信支付", "WX_H5"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-SUBMIT"
      && error.message.includes("状态未推进")
  );
  assert.throws(
    () => normalizePaymentSubmitResult(
      { ...validResult, payScene: "WALLET_RECHARGE_APP" },
      "PRE-001", null, null, "BALANCE_PAY_APP"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-SUBMIT"
  );
  for (const invalidAttemptFacts of [
    { latestAttemptStatus: "处理中", latestAttemptStatusType: "invalid", latestAttemptCompletedAt: null },
    { latestAttemptStatus: "处理中", latestAttemptStatusType: "info", latestAttemptCompletedAt: "2026-09-27 12:00:00" },
    { latestAttemptStatus: "成功", latestAttemptStatusType: "success", latestAttemptCompletedAt: "2026-09-27 12:00:00" }
  ]) {
    assert.throws(
      () => normalizePaymentSubmitResult({ ...validResult, ...invalidAttemptFacts }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-SUBMIT"
    );
  }
  assert.throws(
    () => normalizePaymentSubmitResult({ ...validResult, businessChainId: undefined }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-SUBMIT"
  );
  assert.throws(
    () => normalizePaymentSubmitResult({ ...validResult, createdAt: "2026-09-27 12:00:00", expiresAt: "2026-09-27 11:59:00" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-SUBMIT"
  );
  assert.throws(
    () => normalizePaymentSubmitResult({ prepayOrderNo: "PRE-001" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-SUBMIT"
  );
  assert.throws(
    () => normalizePaymentSubmitResult(validResult, "PRE-002"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-SUBMIT"
  );
  assert.doesNotThrow(() => normalizePaymentSubmitResult(
    { ...validResult, paymentMethod: "微信支付" },
    "PRE-001",
    "微信支付"
  ));
  assert.throws(
    () => normalizePaymentSubmitResult(
      { ...validResult, paymentMethod: "支付宝" },
      "PRE-001",
      "微信支付"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-SUBMIT"
  );
  assert.doesNotThrow(() => normalizePaymentSubmitResult(
    { ...validResult, channel: "WX_H5" }, "PRE-001", null, "WX_H5"
  ));
  assert.throws(
    () => normalizePaymentSubmitResult(
      { ...validResult, channel: "ALI_H5" }, "PRE-001", null, "WX_H5"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-SUBMIT"
  );
  assert.throws(
    () => normalizePaymentSubmitResult({ ...validResult, amount: "" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-SUBMIT"
  );
  assert.throws(
    () => normalizePaymentSubmitResult({ ...validResult, amount: "invalid" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-SUBMIT"
  );
  assert.throws(
    () => normalizePaymentSubmitResult({ ...validResult, paymentStatus: "UNKNOWN_STATUS" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-SUBMIT"
  );
  assert.throws(
    () => normalizePaymentSubmitResult({ ...validResult, cashierStatusType: "success" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-SUBMIT"
  );
  assert.throws(
    () => normalizePaymentSubmitResult({ ...validResult, expiresAt: "not-a-time" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-SUBMIT"
  );
  assert.throws(
    () => normalizePaymentSubmitResult({ ...validResult, createdAt: "2026-02-30 11:59:00" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-SUBMIT"
  );
  assert.throws(
    () => normalizePaymentSubmitResult({
      ...validResult,
      paymentStatus: "SUCCESS",
      successAt: "2026-09-27 11:58:00"
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-SUBMIT"
  );
  assert.throws(
    () => normalizePaymentSubmitResult({ ...validResult, paymentStatus: "SUCCESS", cashierStatus: "待回调" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-SUBMIT"
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
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-SUBMIT"
  );
  for (const invalidSuccessfulResult of [
    { paymentStatus: "SUCCESS", successAt: "2026-09-27 12:01:00", cashierStatus: "支付成功", cashierStatusType: "success", channel: "WX_H5", channelTransactionNo: "WX-001" },
    { paymentStatus: "SUCCESS", successAt: "2026-09-27 12:01:00", cashierStatus: "支付成功", cashierStatusType: "success", channel: "WX_H5", channelTransactionNo: "WX-001", latestAttemptStatus: "失败", latestAttemptStatusType: "danger" },
    { paymentStatus: "SUCCESS", successAt: "2026-09-27 12:01:00", cashierStatus: "支付成功", cashierStatusType: "success", channel: "WX_H5", channelTransactionNo: "WX-001", latestAttemptStatus: "成功", latestAttemptStatusType: "warn", latestAttemptCompletedAt: "2026-09-27 12:00:00" },
    { paymentStatus: "SUCCESS", successAt: "2026-09-27 12:01:00", cashierStatus: "支付成功", cashierStatusType: "success", channel: "WX_H5", channelTransactionNo: "WX-001", latestAttemptStatus: "成功", latestAttemptStatusType: "success", latestAttemptCompletedAt: "2026-09-27 12:02:00" }
  ]) {
    assert.throws(
      () => normalizePaymentSubmitResult({ ...validResult, ...invalidSuccessfulResult }),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-SUBMIT"
    );
  }
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  await assert.rejects(
    paymentApi.submit({ prepayOrderNo: "PRE-001" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-SUBMIT"
  );
});

test("rejects payment query and close responses without both payment identifiers", async (context) => {
  const validResult = { prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", orderNo: "ORDER-001", amount: "68.00", status: "SUCCESS", statusType: "success", channel: "WX_H5", channelTransactionNo: "WX-001", successAt: "2026-09-27 12:01:00", createdAt: "2026-09-27 11:59:00", latestAttemptStatus: "成功", latestAttemptStatusType: "success", latestAttemptCompletedAt: "2026-09-27 12:00:00" };
  assert.deepEqual(normalizePaymentActionResult(validResult), validResult);
  assert.throws(
    () => normalizePaymentActionResult(
      { ...validResult, payScene: "WALLET_RECHARGE_APP" },
      "PAY-001",
      null,
      "BALANCE_PAY_APP"
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult({ ...validResult, channelTransactionNo: undefined }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult({ ...validResult, channel: undefined }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult({ ...validResult, latestAttemptStatus: undefined, latestAttemptStatusType: undefined, latestAttemptCompletedAt: undefined }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult({ paymentOrderId: "PAY-001" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult(validResult, "PAY-002"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
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
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult(validResult, "PAY-001", {
      prepayOrderNo: "PRE-001", orderNo: "ORDER-001", amount: "68.00", channel: "WX_H5", channelTransactionNo: "WX-002"
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult(validResult, "PAY-001", {
      prepayOrderNo: "PRE-002", orderNo: "ORDER-001", amount: "68.00"
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult(validResult, "PAY-001", {
      prepayOrderNo: "PRE-001", orderNo: "ORDER-002", amount: "68.00"
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult(validResult, "PAY-001", {
      prepayOrderNo: "PRE-001", orderNo: "ORDER-001", amount: "67.99"
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
  assert.doesNotThrow(() => normalizePaymentActionResult(
    { ...validResult, paymentMethod: "微信支付" },
    "PAY-001",
    { prepayOrderNo: "PRE-001", orderNo: "ORDER-001", amount: "68.00", paymentMethod: "微信支付" }
  ));
  assert.throws(
    () => normalizePaymentActionResult(
      { ...validResult, paymentMethod: "支付宝" },
      "PAY-001",
      { prepayOrderNo: "PRE-001", orderNo: "ORDER-001", amount: "68.00", paymentMethod: "微信支付" }
    ),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult({ ...validResult, orderNo: "" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult({ ...validResult, status: "" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult({ ...validResult, status: "UNKNOWN_STATUS" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult({ ...validResult, statusType: undefined }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
  assert.throws(
    () => normalizePaymentActionResult({ ...validResult, status: "WAIT_CALLBACK", statusType: "success" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
  for (const invalidResult of [
    { ...validResult, businessChainId: undefined },
    { ...validResult, businessChainId: "CHAIN-OTHER" },
    { ...validResult, successAt: undefined },
    { ...validResult, status: "WAIT_CALLBACK", statusType: "warn", successAt: "2026-09-27 12:01:00" },
    { ...validResult, successAt: "2026-09-27 11:58:00" },
    { ...validResult, latestAttemptStatus: "成功", latestAttemptCompletedAt: undefined },
    { ...validResult, latestAttemptStatus: "成功", latestAttemptCompletedAt: "2026-09-27 11:58:59" },
    { ...validResult, latestAttemptStatus: "成功", latestAttemptStatusType: "danger", latestAttemptCompletedAt: "2026-09-27 12:01:00" },
    { ...validResult, latestAttemptStatus: "失败", latestAttemptStatusType: "success" },
    { ...validResult, latestAttemptStatus: "等待回调", latestAttemptCompletedAt: "2026-09-27 12:01:00" },
    { ...validResult, createdAt: undefined },
    { ...validResult, createdAt: "2026-02-30 11:59:00" }
  ]) {
    assert.throws(
      () => normalizePaymentActionResult(invalidResult),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
    );
  }
  let call = 0;
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({
      code: "0",
      data: call++ === 0 ? null : { paymentOrderId: "PAY-001" }
    })
  }));

  await assert.rejects(
    paymentApi.query({ paymentOrderId: "PAY-001" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
  await assert.rejects(
    paymentApi.close({ paymentOrderId: "PAY-001" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-ACTION"
  );
});

test("rejects a refund detail response without a refund order number", async (context) => {
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
    normalizeRefundDetailResult({ ...validResult, payScene: "HOME_CLEAN" }, "REF-001", " HOME_CLEAN "),
    { ...validResult, payScene: "HOME_CLEAN" }
  );
  assert.throws(
    () => normalizeRefundDetailResult({ ...validResult, payScene: "HOME_REPAIR" }, "REF-001", "HOME_CLEAN"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult(validResult, "REF-002"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult({ paymentOrderId: "PAY-001" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult({ refundOrderId: "REF-001", paymentOrderId: "" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult({ ...validResult, orderNo: undefined }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult({ ...validResult, orderNo: " " }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult({ ...validResult, businessChainId: undefined }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult({ ...validResult, paidAmount: "17.99" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult({ ...validResult, refundMethod: undefined }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult({ ...validResult, refundReason: undefined }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult({ ...validResult, refundAmount: "68.01" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult({ ...validResult, refundAmount: "invalid" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult({ ...validResult, status: "UNKNOWN_STATUS" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult({ ...validResult, statusType: undefined }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult({ ...validResult, status: "SUCCESS", statusType: "warn" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult({ ...validResult, status: "SUCCESS", statusType: "success" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
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
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult({ ...validResult, status: "SUCCESS", statusType: "success", successAt: "-" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult({ ...validResult, status: "SUCCESS", statusType: "success", successAt: "not-a-time" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  for (const invalidResult of [
    { ...validResult, attempts: "退款尝试损坏" },
    { ...validResult, attempts: [{ ...validAttempt, attemptStatusType: "success" }] },
    { ...validResult, attempts: [{ ...validAttempt, attemptStatus: "SUCCESS", attemptStatusType: "success" }] },
    { ...validResult, attempts: [{ ...validAttempt, completedAt: "2026-09-27 12:00:03" }] },
    { ...validResult, operationLogs: {} },
    { ...validResult, operationLogs: ["退款日志损坏"] },
    { ...validResult, attempts: [{ ...validAttempt, createdAt: "2026-09-27 11:59:59" }] },
    {
      ...validResult,
      attempts: [
        validAttempt,
        { ...validAttempt, refundAttemptNo: "ATT-002", createdAt: "2026-09-27 12:00:02", submittedAt: "2026-09-27 12:00:03" }
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
      successAt: "2026-09-27 12:01:00",
      terminalAt: "2026-09-27 12:01:01",
      operationLogs: [{ logNo: "LOG-003", actionName: "退款成功", createdAt: "2026-09-27 12:01:02" }]
    },
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
      (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
    );
  }
  assert.throws(
    () => normalizeRefundDetailResult({
      ...validResult,
      attempts: [{ ...validAttempt, submittedAt: "2026-09-27 12:00:00" }]
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult({ ...validResult, status: "FAIL", statusType: "danger" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult({
      ...validResult,
      status: "FAIL",
      statusType: "danger",
      channelCode: "WX_H5",
      channelSubmittedAt: "2026-09-27 12:00:30",
      terminalAt: "2026-09-27 12:01:01"
    }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  assert.throws(
    () => normalizeRefundDetailResult({ ...validResult, status: "SUCCESS", statusType: "success", successAt: "2026-09-27 12:01:00", terminalAt: "-" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
      json: async () => ({ code: "0", data: null })
  }));

  await assert.rejects(
    paymentApi.getRefundDetail("REF-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-REFUND-DETAIL"
  );
});

test("rejects a payment detail response without a payment order number", async (context) => {
  const validResult = { paymentOrderId: "PAY-001", prepayOrderNo: "PRE-001", businessChainId: "CHAIN-PAY-001", orderNo: "ORDER-001", amount: "68.00", status: "SUCCESS", statusType: "success", channel: "WX_H5", channelTransactionNo: "WX-001", successAt: "2026-09-27 12:01:00", createdAt: "2026-09-27 11:59:00", latestAttemptStatus: "成功", latestAttemptStatusType: "success", latestAttemptCompletedAt: "2026-09-27 12:00:00", routeLogs: [], notifyLogs: [], eventLogs: [] };
  assert.deepEqual(normalizePaymentDetailResult(validResult), validResult);
  assert.throws(
    () => normalizePaymentDetailResult({ ...validResult, payScene: "WALLET_RECHARGE_APP" }, "PAY-001", "BALANCE_PAY_APP"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDetailResult({ ...validResult, channelTransactionNo: undefined }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDetailResult({ ...validResult, latestAttemptStatus: undefined, latestAttemptStatusType: undefined, latestAttemptCompletedAt: undefined }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDetailResult({ ...validResult, channel: undefined }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-DETAIL"
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
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDetailResult({ prepayOrderNo: "PRE-001" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDetailResult({ ...validResult, status: "" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDetailResult({ ...validResult, statusType: undefined }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDetailResult({ ...validResult, status: "WAIT_CALLBACK", statusType: "success" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-DETAIL"
  );
  assert.throws(
    () => normalizePaymentDetailResult({ ...validResult, latestAttemptStatus: "渠道提交中" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-DETAIL"
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
    { ...successfulAttemptResult, latestAttemptCompletedAt: "2026-09-27 11:58:59" },
    { ...successfulAttemptResult, latestAttemptCompletedAt: "2026-09-27 12:01:01" },
    { ...validResult, latestAttemptStatus: "等待回调", latestAttemptStatusType: "warn", latestAttemptCompletedAt: "2026-09-27 12:01:05" }
  ]) {
    assert.throws(
      () => normalizePaymentDetailResult(invalidResult),
      (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-DETAIL"
    );
  }
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  await assert.rejects(
    paymentApi.getDetail("PAY-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-PAYMENT-DETAIL"
  );
});

test("rejects a cashier response without a prepay order number", async (context) => {
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
  assert.throws(
    () => normalizeCashierPageResult(validResult, "PRE-002"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER"
  );
  assert.throws(
    () => normalizeCashierPageResult({ channels: [] }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER"
  );
  assert.throws(
    () => normalizeCashierPageResult({ prepayOrderNo: "PRE-001", paymentOrderId: "" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER"
  );
  assert.throws(
    () => normalizeCashierPageResult({ prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", amount: "" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER"
  );
  assert.throws(
    () => normalizeCashierPageResult({ prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", orderNo: "ORDER-001", amount: "68.00" }),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER"
  );
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  await assert.rejects(
    paymentApi.getCashier("PRE-001"),
    (error) => error.code === "PAYMENT-CLIENT-INVALID-CASHIER"
  );
});

test("reports network failures with a stable client error", async (context) => {
  context.mock.method(globalThis, "fetch", async () => {
    throw new TypeError("fetch failed");
  });

  await assert.rejects(
    paymentApi.getDetail("PAY-NETWORK-ERROR"),
    (error) => error.code === "PAYMENT-CLIENT-NETWORK"
  );
});

test("maps aborted user-terminal requests to a stable timeout error", async (context) => {
  context.mock.method(globalThis, "fetch", async (_url, options) => {
    assert.ok(options.signal instanceof AbortSignal);
    const error = new Error("request aborted");
    error.name = "AbortError";
    throw error;
  });

  await assert.rejects(
    paymentApi.getDetail("PAY-TIMEOUT"),
    (error) => error.code === "PAYMENT-CLIENT-TIMEOUT"
  );
});

test("filters null rows from cashier, payment, and refund detail responses", async (context) => {
  let call = 0;
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({
      code: "0",
      data: call++ === 0
        ? { prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", orderNo: "ORDER-001", amount: "68.00", status: "待支付", statusType: "warn", paymentStatus: "PREPAY_CREATED", expiresAt: "2026-09-27 12:00:00", channels: ["微信支付"] }
        : call === 2
          ? { paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", prepayOrderNo: "PRE-001", orderNo: "ORDER-001", amount: "68.00", status: "SUCCESS", statusType: "success", channel: "WX_H5", channelTransactionNo: "WX-001", successAt: "2026-09-27 12:01:00", createdAt: "2026-09-27 11:59:00", latestAttemptStatus: "成功", latestAttemptStatusType: "success", latestAttemptCompletedAt: "2026-09-27 12:00:00", routeLogs: [null, "路由完成"], notifyLogs: null, eventLogs: ["事件完成"] }
          : { refundOrderId: "REF-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001", orderNo: "ORDER-001", paidAmount: "68.00", refundAmount: "18.00", refundMethod: "原路退款", refundReason: "客户申请", status: "PROCESSING", statusType: "warn", appliedAt: "2026-09-27 12:00:00", attempts: [null, { refundAttemptNo: "ATT-001", attemptStatus: "PENDING", attemptStatusType: "warn", createdAt: "2026-09-27 12:00:01", submittedAt: "2026-09-27 12:00:02", completedAt: "-" }], operationLogs: [null, { logNo: "LOG-001", actionName: "申请退款", createdAt: "2026-09-27 12:00:00" }] }
    })
  }));

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
    submittedAt: "2026-09-27 12:00:02",
    completedAt: "-"
  }]);
  assert.deepEqual(refund.operationLogs, [{
    logNo: "LOG-001",
    actionName: "申请退款",
    createdAt: "2026-09-27 12:00:00"
  }]);
});
