import assert from "node:assert/strict";
import test from "node:test";

import {
  buildRequest,
  executeRequest,
  parseArguments,
  resolveBaseUrl,
  validateCommandResponse
} from "../payment-api.mjs";

test("compares API payment and refund wall-clock timelines across daylight saving time", (context) => {
  const previousTimezone = process.env.TZ;
  process.env.TZ = "America/Los_Angeles";
  context.after(() => {
    if (previousTimezone == null) delete process.env.TZ;
    else process.env.TZ = previousTimezone;
  });
  const prepay = {
    prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001", amount: "68.00", paymentStatus: "PREPAY_CREATED",
    createdAt: "2026-03-08 02:59:00", expiresAt: "2026-03-08 03:00:00",
    cashierStatus: "待支付", cashierStatusType: "warn"
  };
  const validatePrepay = (data) => validateCommandResponse("prepay", {}, { code: "0", data });
  assert.doesNotThrow(() => validatePrepay(prepay));
  assert.throws(() => validatePrepay({ ...prepay, expiresAt: "2026-03-08 02:58:00" }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE");

  const refund = {
    refundOrderId: "REF-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001", paidAmount: "68.00", refundAmount: "18.00",
    status: "SUCCESS", statusType: "success", channelCode: "WX_H5", channelRefundNo: "CH-REF-001",
    appliedAt: "2026-03-08 02:59:00", channelSubmittedAt: "2026-03-08 03:00:00",
    successAt: "2026-03-08 03:01:00", terminalAt: "2026-03-08 03:02:00",
    attempts: [{ refundAttemptNo: "ATT-001", attemptStatus: "SUCCESS", attemptStatusType: "success",
      createdAt: "2026-03-08 02:59:00", submittedAt: "2026-03-08 03:00:00",
      completedAt: "2026-03-08 03:01:00" }],
    operationLogs: [{ logNo: "LOG-001", actionName: "申请退款", createdAt: "2026-03-08 03:00:00" }]
  };
  const validateRefund = (data) => validateCommandResponse("refund-detail", {}, { code: "0", data });
  assert.doesNotThrow(() => validateRefund(refund));
  assert.throws(() => validateRefund({ ...refund, successAt: "2026-03-08 02:58:00" }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE");
});

test("rejects terminal attempts in pending API payment snapshots", () => {
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
    for (const [command, data] of [["prepay", prepay], ["cashier", cashier], ["submit", submission]]) {
      assert.throws(() => validateCommandResponse(command, {}, { code: "0", data: { ...data, ...attempt } }),
        (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE");
    }
  }
});

test("keeps API failed payment attempt styles and success facts consistent", () => {
  const failedPayment = {
    prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001", businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001", amount: "68.00", status: "FAIL", statusType: "danger",
    createdAt: "2026-09-27 11:59:00", latestAttemptStatus: "失败",
    latestAttemptStatusType: "danger"
  };
  for (const command of ["detail", "query", "close"]) {
    const validate = (payment) => validateCommandResponse(command, {}, { code: "0", data: payment });
    assert.doesNotThrow(() => validate(failedPayment));
    assert.doesNotThrow(() => validate({ ...failedPayment, latestAttemptStatus: " FAILED " }));
    for (const latestAttemptStatus of [" FAIL ", "fAiLeD", "支付失败", "支付成功", " SUCCESS "]) {
      assert.throws(() => validate({
        ...failedPayment, latestAttemptStatus, latestAttemptStatusType: "success"
      }), (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE");
    }
  }
});

test("parses spaced and inline command options", () => {
  assert.deepEqual(
    parseArguments(["detail", "--payment-order-id=PAY/1", "--base-url", "http://localhost:18081/"]),
    {
      command: "detail",
      options: {
        "payment-order-id": "PAY/1",
        "base-url": "http://localhost:18081/"
      }
    }
  );
  assert.throws(() => parseArguments(["unknown"]), /命令仅支持/);
  assert.throws(() => parseArguments(["detail", "--payment-order-id"]), /缺少值/);
  assert.throws(() => parseArguments([
    "submit", "--prepay-order-no", "PRE-001", "--access-token", "token",
    "--idempotency-ke", "original-key"
  ]), /submit 不支持参数 --idempotency-ke/);
  assert.throws(() => parseArguments([
    "submit", "--prepay-order-no", "PRE-001", "--refund-amount", "12"
  ]), /submit 不支持参数 --refund-amount/);
  assert.deepEqual(parseArguments([
    "submit", "--prepay-order-no", "PRE-001", "--access-token", "token",
    "--idempotency-key", "original-key", "--base-url", "http:\/\/localhost:18081"
  ]).options["idempotency-key"], "original-key");
});

test("accepts supported options for each API command without ignoring unrelated flags", () => {
  const commandOptions = [
    ["prepay", ["order-no", "pay-scene", "customer-name", "amount", "cashier-title", "remark"]],
    ["cashier", ["prepay-order-no"]],
    ["submit", ["prepay-order-no", "payment-method", "channel-code", "source-app-id",
      "merchant-no", "access-token", "client-device-id", "idempotency-key"]],
    ["detail", ["payment-order-id"]],
    ["query", ["payment-order-id"]],
    ["close", ["payment-order-id"]],
    ["refund-apply", ["payment-order-id", "idempotency-key", "refund-amount",
      "refund-method", "refund-reason", "operator"]],
    ["refund-detail", ["refund-order-id"]],
    ["refund-approve", ["refund-order-id", "operator", "remark"]],
    ["refund-query", ["refund-order-id", "operator"]],
    ["refund-retry", ["refund-order-id", "operator", "remark"]]
  ];
  for (const [command, keys] of commandOptions) {
    const tokens = keys.flatMap((key) => [`--${key}`, "value"]);
    assert.equal(Object.keys(parseArguments([command, ...tokens, "--base-url", "http://localhost"]).options).length,
      keys.length + 1);
    assert.throws(() => parseArguments([command, "--unexpected", "value"]),
      new RegExp(`${command} 不支持参数 --unexpected`));
  }
});

test("builds a complete prepay contract", () => {
  assert.deepEqual(buildRequest("prepay", {
    "order-no": "ORD-API-1",
    "pay-scene": "SERVICE_ORDER_API",
    "customer-name": "API 客户",
    amount: "168.00",
    "cashier-title": "API 支付"
  }), {
    method: "POST",
    path: "/api/payments/prepay",
    data: {
      orderNo: "ORD-API-1",
      payScene: "SERVICE_ORDER_API",
      customerName: "API 客户",
      amount: "168.00",
      cashierTitle: "API 支付"
    }
  });
});

test("passes an optional prepay remark without making it required", () => {
  const request = buildRequest("prepay", {
    "order-no": "ORD-API-2",
    "pay-scene": "SERVICE_ORDER_API",
    "customer-name": "API 客户",
    amount: "99.00",
    "cashier-title": "API 支付",
    remark: "  会员储值  "
  });
  assert.equal(request.data.remark, "会员储值");
  const withoutRemark = buildRequest("prepay", {
    "order-no": "ORD-API-3",
    "pay-scene": "SERVICE_ORDER_API",
    "customer-name": "API 客户",
    amount: "99.00",
    "cashier-title": "API 支付"
  });
  assert.equal(Object.prototype.hasOwnProperty.call(withoutRemark.data, "remark"), false);
});

test("builds an API submit contract with stable explicit facts", () => {
  const request = buildRequest("submit", {
    "prepay-order-no": "PRE-API-1",
    "access-token": "demo-token"
  });
  assert.equal(request.data.terminal, "API");
  assert.equal(request.data.sourceAppId, "housekeeping-api");
  assert.equal(request.data.channelCode, "ALIPAY_H5");
  assert.equal(request.data.idempotencyKey, "API-PRE-API-1");
  assert.deepEqual(buildRequest("submit", {
    "prepay-order-no": "PRE-API-1", "access-token": "demo-token"
  }), request);
  for (const idempotencyKey of ["", "   "]) {
    assert.throws(() => buildRequest("submit", {
      "prepay-order-no": "PRE-API-1",
      "access-token": "demo-token",
      "idempotency-key": idempotencyKey
    }), /缺少必填参数 --idempotency-key/);
  }
  const customized = buildRequest("submit", {
    "prepay-order-no": " PRE-API-2 ",
    "access-token": " token ",
    "payment-method": " 微信支付 ",
    "channel-code": " WX_H5 ",
    "source-app-id": " app-1 ",
    "merchant-no": " merchant-1 ",
    "client-device-id": " device-1 ",
    "idempotency-key": " idem-1 "
  });
  assert.deepEqual(customized.data, {
    prepayOrderNo: "PRE-API-2",
    paymentMethod: "微信支付",
    channelCode: "WX_H5",
    sourceAppId: "app-1",
    merchantNo: "merchant-1",
    accessToken: "token",
    terminal: "API",
    clientDeviceId: "device-1",
    idempotencyKey: "idem-1"
  });
});

test("builds refund application and operation contracts", () => {
  assert.throws(() => buildRequest("refund-apply", {
    "payment-order-id": "PAY-REF-1",
    "refund-amount": "20.00",
    operator: "api-operator"
  }), /缺少必填参数 --idempotency-key/);
  assert.throws(() => buildRequest("refund-apply", {
    "payment-order-id": "PAY-REF-1",
    "refund-amount": "20.00",
    operator: "api-operator",
    "idempotency-key": "  "
  }), /缺少必填参数 --idempotency-key/);
  assert.deepEqual(buildRequest("refund-apply", {
    "payment-order-id": " PAY-REF-2 ",
    "refund-amount": "20.00",
    operator: "api-operator",
    "idempotency-key": " REF-IDEM-2 ",
    "refund-method": " 原路退款 ",
    "refund-reason": " 服务取消 "
  }).data, {
    paymentOrderId: "PAY-REF-2",
    idempotencyKey: "REF-IDEM-2",
    refundAmount: "20.00",
    refundMethod: "原路退款",
    refundReason: "服务取消",
    operator: "api-operator"
  });
  assert.deepEqual(buildRequest("refund-detail", { "refund-order-id": "REF/1?#" }).path,
    "/api/refunds/REF%2F1%3F%23");
  assert.deepEqual(buildRequest("refund-approve", {
    "refund-order-id": "REF-1",
    operator: "checker-operator",
    remark: "  approved  "
  }), {
    method: "POST",
    path: "/api/refunds/approve",
    data: { refundOrderId: "REF-1", operator: "checker-operator", remark: "approved" }
  });
  assert.deepEqual(buildRequest("refund-query", {
    "refund-order-id": "REF-1",
    operator: "api-operator"
  }), {
    method: "POST",
    path: "/api/refunds/query",
    data: { refundOrderId: "REF-1", operator: "api-operator" }
  });
  assert.deepEqual(buildRequest("refund-retry", {
    "refund-order-id": "REF-1",
    operator: "api-operator",
    remark: "  retry  "
  }), {
    method: "POST",
    path: "/api/refunds/retry",
    data: { refundOrderId: "REF-1", operator: "api-operator", remark: "retry" }
  });
});

test("encodes identifiers as one API path segment", () => {
  assert.equal(
    buildRequest("cashier", { "prepay-order-no": "PRE/1?#" }).path,
    "/api/payments/cashier/PRE%2F1%3F%23"
  );
  assert.equal(
    buildRequest("detail", { "payment-order-id": "PAY/1?#" }).path,
    "/api/payments/PAY%2F1%3F%23"
  );
});

test("keeps trimmed request identifiers aligned with response identity checks", () => {
  assert.equal(
    buildRequest("detail", { "payment-order-id": "  PAY-001  " }).path,
    "/api/payments/PAY-001"
  );
  const payload = {
    code: "0",
    data: {
      paymentOrderId: " PAY-001 ",
      prepayOrderNo: " PRE-001 ",
      businessChainId: "CHAIN-PAY-001",
      orderNo: " ORDER-001 ",
      amount: "68.00",
      status: "WAIT_CALLBACK",
      statusType: "warn",
      createdAt: "2026-09-27 12:00:00"
    }
  };
  assert.deepEqual(
    validateCommandResponse("detail", { "payment-order-id": "  PAY-001  " }, payload),
    payload
  );
});

test("resolves and normalizes the API base URL", () => {
  assert.equal(resolveBaseUrl({}, {}), "http://localhost:18081");
  assert.equal(resolveBaseUrl({}, { PAYMENT_API_BASE_URL: "https://pay.example.com/" }), "https://pay.example.com");
  assert.equal(resolveBaseUrl({ "base-url": "http://localhost:19000/" }, {}), "http://localhost:19000");
  assert.equal(resolveBaseUrl({ "base-url": "  http://localhost:19001/  " }, {}), "http://localhost:19001");
  assert.equal(resolveBaseUrl({}, { PAYMENT_API_BASE_URL: "  https://pay.example.com/  " }), "https://pay.example.com");
  assert.equal(resolveBaseUrl({ "base-url": "http://localhost:19002////" }, {}), "http://localhost:19002");
  assert.equal(resolveBaseUrl({}, { PAYMENT_API_BASE_URL: "  https://pay.example.com////  " }), "https://pay.example.com");
});

test("keeps the original service envelope and rejects business failures", async () => {
  const successPayload = { code: "0", message: "success", data: { status: "WAIT_CALLBACK" } };
  const success = await executeRequest("http://localhost", { method: "GET", path: "/detail" }, async () => ({
    ok: true,
    json: async () => successPayload
  }));
  assert.equal(success, successPayload);

  await assert.rejects(
    executeRequest("http://localhost", { method: "GET", path: "/detail" }, async () => ({
      ok: true,
      json: async () => ({ code: "PAYMENT-1002", message: "支付单不存在" })
    })),
    (error) => error.payload.code === "PAYMENT-1002"
  );
});

test("rejects command responses that belong to another payment or refund", () => {
  const prepayPayload = {
    code: "0",
    data: {
      orderNo: "ORDER-002",
      prepayOrderNo: "PRE-001",
      paymentOrderId: "PAY-001",
      businessChainId: "CHAIN-PAY-001",
      amount: "88.00"
    }
  };
  assert.throws(
    () => validateCommandResponse("prepay", { "order-no": "ORDER-001" }, prepayPayload),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  assert.equal(
    validateCommandResponse("prepay", {
      "order-no": "ORDER-001",
      amount: "88"
    }, {
      code: "0",
      data: {
        orderNo: "ORDER-001",
        prepayOrderNo: "PRE-001",
        paymentOrderId: "PAY-001",
        businessChainId: "CHAIN-PAY-001",
        amount: "¥88.00",
        paymentStatus: "PREPAY_CREATED",
        createdAt: "2026-09-27 11:59:00",
        expiresAt: "2026-09-27 12:00:00",
        cashierStatus: "待支付",
        cashierStatusType: "warn"
      }
    }).data.amount,
    "¥88.00"
  );
  for (const invalidAttemptFacts of [
    { latestAttemptStatus: "处理中", latestAttemptStatusType: "invalid", latestAttemptCompletedAt: null },
    { latestAttemptStatus: "处理中", latestAttemptStatusType: "info", latestAttemptCompletedAt: "2026-09-27 12:00:00" },
    { latestAttemptStatus: "成功", latestAttemptStatusType: "success", latestAttemptCompletedAt: "2026-09-27 12:00:00" }
  ]) {
    assert.throws(
      () => validateCommandResponse("prepay", { "order-no": "ORDER-001", amount: "88.00" }, {
        code: "0",
        data: {
          orderNo: "ORDER-001",
          prepayOrderNo: "PRE-001",
          paymentOrderId: "PAY-001",
          businessChainId: "CHAIN-PAY-001",
          amount: "88.00",
          paymentStatus: "PREPAY_CREATED",
          createdAt: "2026-09-27 11:59:00",
          expiresAt: "2026-09-27 12:00:00",
          cashierStatus: "待支付",
          cashierStatusType: "warn",
          ...invalidAttemptFacts
        }
      }),
      (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
    );
  }
  assert.throws(
    () => validateCommandResponse("prepay", { "order-no": "ORDER-001", amount: "88.00" }, {
      code: "0",
      data: {
        orderNo: "ORDER-001",
        prepayOrderNo: "PRE-001",
        paymentOrderId: "PAY-001",
        amount: "88.00",
        paymentStatus: "PREPAY_CREATED",
        createdAt: "2026-09-27 12:00:00",
        expiresAt: "2026-09-27 11:59:00",
        cashierStatus: "待支付",
        cashierStatusType: "warn"
      }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  assert.throws(
    () => validateCommandResponse("prepay", { "order-no": "ORDER-001", amount: "88.00" }, {
      code: "0",
      data: {
        orderNo: "ORDER-001",
        prepayOrderNo: "PRE-001",
        paymentOrderId: "PAY-001",
        amount: "88.00",
        paymentStatus: "PREPAY_CREATED",
        createdAt: "2026-09-27 11:59:00",
        expiresAt: "2026-09-27 12:00:00",
        cashierStatus: "支付成功",
        cashierStatusType: "success"
      }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  assert.equal(
    validateCommandResponse("prepay", {
      "order-no": "ORDER-001",
      amount: "9007199254740992.01"
    }, {
      code: "0",
      data: {
        orderNo: "ORDER-001",
        prepayOrderNo: "PRE-001",
        paymentOrderId: "PAY-001",
        businessChainId: "CHAIN-PAY-001",
        amount: "9007199254740992.01",
        paymentStatus: "PREPAY_CREATED",
        createdAt: "2026-09-27 11:59:00",
        expiresAt: "2026-09-27 12:00:00",
        cashierStatus: "待支付",
        cashierStatusType: "warn"
      }
    }).data.amount,
    "9007199254740992.01"
  );
  assert.throws(
    () => validateCommandResponse("prepay", {
      "order-no": "ORDER-001",
      amount: "9007199254740992.02"
    }, {
      code: "0",
      data: {
        orderNo: "ORDER-001",
        prepayOrderNo: "PRE-001",
        paymentOrderId: "PAY-001",
        amount: "9007199254740992.01"
      }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  assert.throws(
    () => validateCommandResponse("prepay", { "order-no": "ORDER-001", amount: "88.00" }, {
      code: "0",
      data: {
        orderNo: "ORDER-001",
        prepayOrderNo: "PRE-001",
        paymentOrderId: "PAY-001",
        amount: "88.00",
        paymentStatus: "UNKNOWN_STATUS",
        expiresAt: "2026-09-27 12:00:00",
        cashierStatus: "待支付",
        cashierStatusType: "warn"
      }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  assert.throws(
    () => validateCommandResponse("prepay", { "order-no": "ORDER-001", amount: "88.00" }, {
      code: "0",
      data: {
        orderNo: "ORDER-001",
        prepayOrderNo: "PRE-001",
        paymentOrderId: "PAY-001",
        amount: "88.00",
        paymentStatus: "SUCCESS",
        expiresAt: "2026-09-27 12:00:00",
        cashierStatus: "待支付",
        cashierStatusType: "warn"
      }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  assert.throws(
    () => validateCommandResponse("prepay", { "order-no": "ORDER-001", amount: "88.00" }, {
      code: "0",
      data: {
        orderNo: "ORDER-001",
        prepayOrderNo: "PRE-001",
        paymentOrderId: "PAY-001",
        amount: "88.00",
        paymentStatus: "PREPAY_CREATED",
        expiresAt: "2026-02-30 12:00:00",
        cashierStatus: "待支付",
        cashierStatusType: "warn"
      }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  assert.throws(
    () => validateCommandResponse("prepay", { "order-no": "ORDER-001", amount: "88.00" }, {
      code: "0",
      data: {
        orderNo: "ORDER-001",
        prepayOrderNo: "PRE-001",
        paymentOrderId: "PAY-001",
        amount: "88.00",
        paymentStatus: "PREPAY_CREATED",
        expiresAt: "not-a-time",
        cashierStatus: "待支付",
        cashierStatusType: "warn"
      }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  assert.throws(
    () => validateCommandResponse("prepay", { "order-no": "ORDER-001", amount: "88.00" }, {
      code: "0",
      data: {
        orderNo: "ORDER-001",
        prepayOrderNo: "PRE-001",
        paymentOrderId: "PAY-001",
        amount: "88.00",
        paymentStatus: "PREPAY_CREATED",
        expiresAt: "2026-09-27 12:00:00",
        cashierStatus: "待支付",
        cashierStatusType: "success"
      }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  assert.throws(
    () => validateCommandResponse("prepay", {
      "order-no": "ORDER-001"
    }, {
      code: "0",
      data: {
        orderNo: "ORDER-001",
        prepayOrderNo: "PRE-001",
        paymentOrderId: "PAY-001",
        amount: 9007199254740992
      }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  assert.throws(
    () => validateCommandResponse("prepay", {
      "order-no": "ORDER-001",
      amount: "88.00"
    }, {
      code: "0",
      data: {
        orderNo: "ORDER-001",
        prepayOrderNo: "PRE-001",
        paymentOrderId: "PAY-001",
        amount: "87.99"
      }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  assert.throws(
    () => validateCommandResponse("query", { "payment-order-id": "PAY-001" }, {
      code: "0",
      data: { paymentOrderId: "PAY-002" }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  const validPaymentResult = {
    paymentOrderId: "PAY-001",
    prepayOrderNo: "PRE-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    amount: "68.00",
    status: "WAIT_CALLBACK",
    statusType: "warn",
    createdAt: "2026-09-27 12:00:00"
  };
  for (const command of ["detail", "query", "close"]) {
    assert.deepEqual(
      validateCommandResponse(command, { "payment-order-id": "PAY-001" }, { code: "0", data: validPaymentResult }),
      { code: "0", data: validPaymentResult }
    );
    for (const latestAttemptStatus of ["成功", "失败", "支付失败", " SUCCESS ", " FAIL ", "fAiLeD"]) {
      assert.throws(
        () => validateCommandResponse(command, { "payment-order-id": "PAY-001" }, {
          code: "0", data: {
            ...validPaymentResult, latestAttemptStatus, latestAttemptStatusType: "danger"
          }
        }),
        (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
      );
    }
    assert.deepEqual(
      validateCommandResponse(command, { "payment-order-id": "PAY-001" }, {
        code: "0",
        data: { ...validPaymentResult, routeLogs: ["路由完成"], notifyLogs: [], eventLogs: ["事件完成"] }
      }),
      { code: "0", data: { ...validPaymentResult, routeLogs: ["路由完成"], notifyLogs: [], eventLogs: ["事件完成"] } }
    );
    for (const field of ["routeLogs", "notifyLogs", "eventLogs"]) {
      for (const invalidLogs of ["invalid", [""], [null]]) {
        assert.throws(
          () => validateCommandResponse(command, { "payment-order-id": "PAY-001" }, {
            code: "0",
            data: { ...validPaymentResult, [field]: invalidLogs }
          }),
          (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
        );
      }
    }
    const validAttemptResult = {
      ...validPaymentResult,
      latestAttemptStatus: "等待回调",
      latestAttemptStatusType: "warn"
    };
    assert.deepEqual(
      validateCommandResponse(command, { "payment-order-id": "PAY-001" }, { code: "0", data: validAttemptResult }),
      { code: "0", data: validAttemptResult }
    );
    for (const invalidResult of [
      { ...validPaymentResult, prepayOrderNo: "" },
      { ...validPaymentResult, amount: "" },
      { ...validPaymentResult, status: "UNKNOWN_STATUS" },
      { ...validPaymentResult, statusType: "" },
      { ...validPaymentResult, statusType: "invalid" },
      { ...validPaymentResult, status: "SUCCESS", statusType: "warn" },
      { ...validPaymentResult, createdAt: "2026-02-30 12:00:00" },
      { ...validPaymentResult, status: "SUCCESS", statusType: "success" },
      { ...validPaymentResult, status: "SUCCESS", statusType: "success", successAt: "not-a-time" },
      {
        ...validPaymentResult,
        status: "SUCCESS",
        statusType: "success",
        successAt: "2026-09-27 11:59:00"
      },
      { ...validPaymentResult, latestAttemptStatus: "等待回调" },
      { ...validPaymentResult, latestAttemptStatus: 123 },
      { ...validPaymentResult, latestAttemptStatus: "等待回调", latestAttemptStatusType: "invalid" },
      { ...validPaymentResult, latestAttemptStatus: "成功", latestAttemptStatusType: "danger", latestAttemptCompletedAt: "2026-09-27 12:01:00" },
      { ...validPaymentResult, latestAttemptStatus: "失败", latestAttemptStatusType: "success" },
      { ...validPaymentResult, latestAttemptStatusType: "warn" },
      {
        ...validPaymentResult,
        latestAttemptStatus: "成功",
        latestAttemptStatusType: "success",
        latestAttemptCompletedAt: "2026-09-27 11:59:59"
      },
      {
        ...validPaymentResult,
        status: "SUCCESS",
        statusType: "success",
        successAt: "2026-09-27 12:00:00",
        latestAttemptStatus: "成功",
        latestAttemptStatusType: "success",
        latestAttemptCompletedAt: "2026-09-27 12:00:01",
        channel: "WX_H5",
        channelTransactionNo: "WX-001"
      }
    ]) {
      assert.throws(
        () => validateCommandResponse(command, { "payment-order-id": "PAY-001" }, { code: "0", data: invalidResult }),
        (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
      );
    }
  }
  const validSuccessfulPaymentResult = {
    ...validPaymentResult,
    status: "SUCCESS",
    statusType: "success",
    channel: "WX_H5",
    channelTransactionNo: "WX-001",
    successAt: "2026-09-27 12:01:00",
    latestAttemptStatus: "成功",
    latestAttemptStatusType: "success",
    latestAttemptCompletedAt: "2026-09-27 12:00:01"
  };
  for (const command of ["detail", "query", "close"]) {
    assert.deepEqual(
      validateCommandResponse(command, { "payment-order-id": "PAY-001" }, {
        code: "0",
        data: validSuccessfulPaymentResult
      }),
      { code: "0", data: validSuccessfulPaymentResult }
    );
    for (const field of ["channel", "channelTransactionNo"]) {
      assert.throws(
        () => validateCommandResponse(command, { "payment-order-id": "PAY-001" }, {
          code: "0",
          data: { ...validSuccessfulPaymentResult, [field]: undefined }
        }),
        (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
      );
    }
  }
  for (const invalidAttemptFacts of [
    { latestAttemptStatus: undefined, latestAttemptStatusType: undefined, latestAttemptCompletedAt: undefined },
    { latestAttemptStatus: "处理中", latestAttemptStatusType: "info", latestAttemptCompletedAt: null },
    { latestAttemptStatus: "成功", latestAttemptStatusType: "warn", latestAttemptCompletedAt: "2026-09-27 12:00:01" },
    { latestAttemptStatus: "成功", latestAttemptStatusType: "success", latestAttemptCompletedAt: "2026-09-27 11:58:59" },
    { latestAttemptStatus: "成功", latestAttemptStatusType: "success", latestAttemptCompletedAt: "2026-09-27 12:01:01" }
  ]) {
    assert.throws(
      () => validateCommandResponse("detail", { "payment-order-id": "PAY-001" }, {
        code: "0",
        data: { ...validSuccessfulPaymentResult, ...invalidAttemptFacts }
      }),
      (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
    );
  }
  assert.throws(
    () => validateCommandResponse("detail", { "payment-order-id": "PAY-001" }, {
      code: "0",
      data: { ...validPaymentResult, businessChainId: undefined }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  assert.throws(
    () => validateCommandResponse("detail", { "payment-order-id": "PAY-001" }, {
      code: "0",
      data: { ...validPaymentResult, businessChainId: "CHAIN-OTHER" }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  for (const command of ["query", "close"]) {
    assert.throws(
      () => validateCommandResponse(command, { "payment-order-id": "PAY-001" }, {
        code: "0",
        data: { ...validPaymentResult, businessChainId: "CHAIN-OTHER" }
      }),
      (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
    );
  }
  assert.throws(
    () => validateCommandResponse("refund-detail", { "refund-order-id": "REF-001" }, {
      code: "0",
      data: { refundOrderId: "REF-002" }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  const validRefundApplyData = {
    refundOrderId: "REF-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    refundAmount: "¥18.00",
    refundMethod: "原路退款",
    paidAmount: "68.00",
    status: "REVIEWING",
    statusType: "warn",
    appliedAt: "2026-09-27 12:00:00"
  };
  const refundApplySummary = { ...validRefundApplyData };
  delete refundApplySummary.paidAmount;
  assert.equal(
    validateCommandResponse("refund-apply", {
      "payment-order-id": "PAY-001", "refund-amount": "18"
    }, { code: "0", data: refundApplySummary }).data.refundMethod,
    "原路退款"
  );
  assert.equal(
    validateCommandResponse("refund-apply", {
      "payment-order-id": "PAY-001",
      "refund-amount": "18"
    }, { code: "0", data: validRefundApplyData }).data.refundAmount,
    "¥18.00"
  );
  for (const invalidResult of [
    { ...validRefundApplyData, businessChainId: "CHAIN-OTHER" },
    { ...validRefundApplyData, paidAmount: "17.99" },
    { ...validRefundApplyData, paidAmount: undefined },
    { ...validRefundApplyData, refundMethod: undefined },
    { ...validRefundApplyData, refundMethod: "线下打款" }
  ]) {
    assert.throws(
      () => validateCommandResponse("refund-apply", {
        "payment-order-id": "PAY-001",
        "refund-amount": "18.00"
      }, { code: "0", data: invalidResult }),
      (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
    );
  }
  assert.equal(
    validateCommandResponse("refund-apply", {
      "payment-order-id": "PAY-001",
      "refund-amount": "18.00",
      "refund-method": " 线下打款 "
    }, { code: "0", data: { ...validRefundApplyData, refundMethod: "线下打款" } }).data.refundMethod,
    "线下打款"
  );
  assert.throws(
    () => validateCommandResponse("refund-query", { "refund-order-id": "REF-001" }, {
      code: "0",
      data: {
        refundOrderId: "REF-001",
        paymentOrderId: "PAY-001",
        orderNo: "ORDER-001"
      }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  const validRefundResult = {
    refundOrderId: "REF-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    refundAmount: "18.00",
    paidAmount: "68.00",
    status: "PROCESSING",
    statusType: "warn",
    appliedAt: "2026-09-27 12:00:00"
  };
  const refundActionSummary = { ...validRefundResult };
  delete refundActionSummary.paidAmount;
  for (const command of ["refund-approve", "refund-query", "refund-retry"]) {
    assert.deepEqual(
      validateCommandResponse(command, { "refund-order-id": "REF-001" }, { code: "0", data: refundActionSummary }),
      { code: "0", data: refundActionSummary }
    );
  }
  assert.throws(
    () => validateCommandResponse("refund-detail", { "refund-order-id": "REF-001" },
      { code: "0", data: refundActionSummary }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  const validTerminalRefundResult = {
    ...validRefundResult,
    status: "SUCCESS",
    statusType: "success",
    channelCode: "WX_H5",
    channelRefundNo: "CH-REF-001",
    channelSubmittedAt: "2026-09-27 12:00:30",
    successAt: "2026-09-27 12:01:00",
    terminalAt: "2026-09-27 12:01:01"
  };
  for (const command of ["refund-detail", "refund-approve", "refund-query", "refund-retry"]) {
    assert.deepEqual(
      validateCommandResponse(command, { "refund-order-id": "REF-001" }, { code: "0", data: validRefundResult }),
      { code: "0", data: validRefundResult }
    );
  for (const invalidResult of [
      { ...validTerminalRefundResult, channelSubmittedAt: undefined },
      { ...validRefundResult, status: "UNKNOWN_STATUS" },
      { ...validRefundResult, statusType: "" },
    { ...validRefundResult, statusType: "invalid" },
    { ...validRefundResult, status: "SUCCESS", statusType: "warn" },
    { ...validRefundResult, businessChainId: "CHAIN-OTHER" },
    { ...validRefundResult, paidAmount: "17.99" },
    { ...validRefundResult, terminalAt: "2026-09-27 12:01:00" },
    { ...validRefundResult, appliedAt: "2026-02-30 12:00:00" },
    { ...validRefundResult, channelSubmittedAt: "not-a-time" },
    { ...validRefundResult, channelSubmittedAt: "2026-09-27 11:59:59" },
    {
      ...validRefundResult,
      status: "SUCCESS",
      statusType: "success",
      successAt: "2026-09-27 11:59:00"
    }
  ]) {
      assert.throws(
        () => validateCommandResponse(command, { "refund-order-id": "REF-001" }, { code: "0", data: invalidResult }),
        (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
      );
    }
  }
  for (const command of ["refund-detail", "refund-approve", "refund-query", "refund-retry"]) {
    assert.deepEqual(
      validateCommandResponse(command, { "refund-order-id": "REF-001" }, { code: "0", data: validTerminalRefundResult }),
      { code: "0", data: validTerminalRefundResult }
    );
    assert.throws(
      () => validateCommandResponse(command, { "refund-order-id": "REF-001" }, {
        code: "0", data: { ...validTerminalRefundResult, terminalAt: undefined }
      }),
      (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
    );
    for (const field of ["channelCode", "channelRefundNo"]) {
      assert.throws(
        () => validateCommandResponse(command, { "refund-order-id": "REF-001" }, {
          code: "0",
          data: { ...validTerminalRefundResult, [field]: undefined }
        }),
        (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
      );
    }
  }
  assert.throws(
    () => validateCommandResponse("refund-apply", {
      "payment-order-id": "PAY-001", "refund-amount": "18"
    }, { code: "0", data: { ...validTerminalRefundResult, terminalAt: undefined, refundMethod: "原路退款" } }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  const validAttempt = {
    refundAttemptNo: "ATT-001",
    attemptStatus: "PENDING",
    attemptStatusType: "warn",
    createdAt: "2026-09-27 12:00:01",
    submittedAt: "-",
    completedAt: "-"
  };
  const validRefundDetailResult = { ...validRefundResult, attempts: [validAttempt] };
  assert.deepEqual(
    validateCommandResponse("refund-detail", { "refund-order-id": "REF-001" }, { code: "0", data: validRefundDetailResult }),
    { code: "0", data: validRefundDetailResult }
  );
  for (const [attemptStatus, attemptStatusType, submittedAt] of [
    ["FAILED", "danger", "-"],
    ["UNKNOWN", "warn", "2026-09-27 12:00:02"],
    ["ACCEPTED", "warn", "2026-09-27 12:00:02"]
  ]) {
    const attempt = { ...validAttempt, attemptStatus, attemptStatusType, submittedAt };
    assert.deepEqual(
      validateCommandResponse("refund-detail", { "refund-order-id": "REF-001" }, {
        code: "0", data: { ...validRefundResult, attempts: [attempt] }
      }).data.attempts,
      [attempt]
    );
  }
  const validRefundOperationLog = {
    logNo: "LOG-001",
    actionName: "申请退款",
    createdAt: "2026-09-27 12:00:00"
  };
  assert.deepEqual(
    validateCommandResponse("refund-detail", { "refund-order-id": "REF-001" }, {
      code: "0",
      data: { ...validRefundDetailResult, operationLogs: [validRefundOperationLog] }
    }),
    { code: "0", data: { ...validRefundDetailResult, operationLogs: [validRefundOperationLog] } }
  );
  assert.throws(
    () => validateCommandResponse("refund-detail", { "refund-order-id": "REF-001" }, {
      code: "0",
      data: {
        ...validRefundDetailResult,
        status: "SUCCESS",
        statusType: "success",
        successAt: "2026-09-27 12:01:00",
        terminalAt: "2026-09-27 12:01:01",
        channelSubmittedAt: "2026-09-27 12:01:02"
      }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  for (const invalidOperationLogs of [
    "invalid",
    [{ logNo: "LOG-001" }],
    [{ logNo: "LOG-001", actionName: "申请退款", createdAt: "not-a-time" }],
    [{ logNo: "LOG-001", actionName: "申请退款", createdAt: "2026-09-27 11:59:59" }]
  ]) {
    assert.throws(
      () => validateCommandResponse("refund-detail", { "refund-order-id": "REF-001" }, {
        code: "0",
        data: { ...validRefundDetailResult, operationLogs: invalidOperationLogs }
      }),
      (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
    );
  }
  assert.throws(
    () => validateCommandResponse("refund-detail", { "refund-order-id": "REF-001" }, {
      code: "0",
      data: {
        ...validTerminalRefundResult,
        operationLogs: [{
          ...validRefundOperationLog,
          createdAt: "2026-09-27 12:01:02"
        }]
      }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  assert.throws(
    () => validateCommandResponse("refund-detail", { "refund-order-id": "REF-001" }, {
      code: "0",
      data: {
        ...validRefundDetailResult,
        operationLogs: [
          validRefundOperationLog,
          { ...validRefundOperationLog, logNo: "LOG-002", createdAt: "2026-09-27 12:00:01" }
        ]
      }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  for (const invalidAttempts of [
    [{ ...validAttempt, refundAttemptNo: "" }],
    [
      validAttempt,
      { ...validAttempt, refundAttemptNo: "ATT-002", createdAt: "2026-09-27 12:00:02", submittedAt: "2026-09-27 12:00:03" }
    ],
    [{ ...validAttempt, attemptStatusType: "success" }],
    [{ ...validAttempt, attemptStatus: "SUCCESS", attemptStatusType: "success" }],
    [{ ...validAttempt, submittedAt: "2026-09-27 12:00:02" }],
    [{ ...validAttempt, attemptStatus: "FAILED", attemptStatusType: "danger", submittedAt: "2026-09-27 12:00:02" }],
    [{ ...validAttempt, attemptStatus: "UNKNOWN", submittedAt: "-" }],
    [{ ...validAttempt, attemptStatus: "ACCEPTED", submittedAt: "-" }],
    [{ ...validAttempt, completedAt: "2026-09-27 12:00:03" }],
    [{ ...validAttempt, createdAt: "not-a-time" }],
    [{ ...validAttempt, submittedAt: "2026-09-27 12:00:00" }],
    [{ ...validAttempt, completedAt: "2026-09-27 12:00:01" }],
    [{ ...validAttempt, completedAt: "not-a-time" }],
    "invalid"
  ]) {
    assert.throws(
      () => validateCommandResponse("refund-detail", { "refund-order-id": "REF-001" }, {
        code: "0",
        data: { ...validRefundResult, attempts: invalidAttempts }
      }),
      (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
    );
  }
  assert.throws(
    () => validateCommandResponse("refund-detail", { "refund-order-id": "REF-001" }, {
      code: "0",
      data: {
        ...validTerminalRefundResult,
        attempts: [{
          ...validAttempt,
          attemptStatus: "SUCCESS",
          attemptStatusType: "success",
          completedAt: "2026-09-27 12:01:02"
        }]
      }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  assert.throws(
    () => validateCommandResponse("refund-detail", { "refund-order-id": "REF-001" }, {
      code: "0",
      data: {
        ...validRefundResult,
        status: "SUCCESS",
        statusType: "success",
        successAt: "2026-09-27 12:01:00"
      }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  assert.throws(
    () => validateCommandResponse("refund-detail", { "refund-order-id": "REF-001" }, {
      code: "0",
      data: {
        ...validRefundResult,
        status: "SUCCESS",
        statusType: "success",
        successAt: "2026-09-27 12:01:00",
        terminalAt: "2026-09-27 12:00:00"
      }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  assert.throws(
    () => validateCommandResponse("refund-detail", { "refund-order-id": "REF-001" }, {
      code: "0",
      data: {
        ...validRefundResult,
        status: "FAIL",
        statusType: "danger",
        terminalAt: "2026-09-27 11:59:00"
      }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  assert.equal(
    validateCommandResponse("submit", { "prepay-order-no": "PRE-001" }, {
      code: "0",
      data: {
        prepayOrderNo: "PRE-001",
        paymentOrderId: "PAY-001",
        businessChainId: "CHAIN-PAY-001",
        orderNo: "ORDER-001",
        amount: "68.00",
        paymentStatus: "WAIT_CALLBACK",
        createdAt: "2026-09-27 11:59:00",
        expiresAt: "2026-09-27 12:00:00",
        cashierStatus: "支付中",
        cashierStatusType: "info"
      }
    }).data.paymentOrderId,
    "PAY-001"
  );
  assert.equal(
    validateCommandResponse("submit", {
      "prepay-order-no": "PRE-001",
      "channel-code": "WX_H5"
    }, {
      code: "0",
      data: {
        prepayOrderNo: "PRE-001",
        paymentOrderId: "PAY-001",
        businessChainId: "CHAIN-PAY-001",
        orderNo: "ORDER-001",
        amount: "68.00",
        paymentStatus: "WAIT_CALLBACK",
        channel: "WX_H5",
        createdAt: "2026-09-27 11:59:00",
        expiresAt: "2026-09-27 12:00:00",
        cashierStatus: "支付中",
        cashierStatusType: "info"
      }
    }).data.channel,
    "WX_H5"
  );
  assert.throws(
    () => validateCommandResponse("submit", {
      "prepay-order-no": "PRE-001",
      "channel-code": "WX_H5"
    }, {
      code: "0",
      data: {
        prepayOrderNo: "PRE-001",
        paymentOrderId: "PAY-001",
        businessChainId: "CHAIN-PAY-001",
        orderNo: "ORDER-001",
        amount: "68.00",
        paymentStatus: "WAIT_CALLBACK",
        channel: "ALI_H5",
        createdAt: "2026-09-27 11:59:00",
        expiresAt: "2026-09-27 12:00:00",
        cashierStatus: "支付中",
        cashierStatusType: "info"
      }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  for (const invalidAttemptFacts of [
    { latestAttemptStatus: "处理中", latestAttemptStatusType: "invalid", latestAttemptCompletedAt: null },
    { latestAttemptStatus: "处理中", latestAttemptStatusType: "info", latestAttemptCompletedAt: "2026-09-27 12:00:00" },
    { latestAttemptStatus: "成功", latestAttemptStatusType: "success", latestAttemptCompletedAt: "2026-09-27 12:00:00" }
  ]) {
    assert.throws(
      () => validateCommandResponse("submit", { "prepay-order-no": "PRE-001" }, {
        code: "0",
        data: {
          prepayOrderNo: "PRE-001",
          paymentOrderId: "PAY-001",
          businessChainId: "CHAIN-PAY-001",
          orderNo: "ORDER-001",
          amount: "68.00",
          paymentStatus: "WAIT_CALLBACK",
          createdAt: "2026-09-27 11:59:00",
          expiresAt: "2026-09-27 12:00:00",
          cashierStatus: "支付中",
          cashierStatusType: "info",
          ...invalidAttemptFacts
        }
      }),
      (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
    );
  }
  const validSuccessfulSubmit = {
    prepayOrderNo: "PRE-001",
    paymentOrderId: "PAY-001",
    businessChainId: "CHAIN-PAY-001",
    orderNo: "ORDER-001",
    amount: "68.00",
    paymentStatus: "SUCCESS",
    createdAt: "2026-09-27 11:59:00",
    expiresAt: "2026-09-27 12:00:00",
    cashierStatus: "支付成功",
    cashierStatusType: "success",
    successAt: "2026-09-27 12:01:00",
    latestAttemptStatus: "成功",
    latestAttemptStatusType: "success",
    latestAttemptCompletedAt: "2026-09-27 12:00:01",
    channel: "WX_H5",
    channelTransactionNo: "WX-001"
  };
  assert.deepEqual(
    validateCommandResponse("submit", { "prepay-order-no": "PRE-001" }, {
      code: "0", data: validSuccessfulSubmit
    }),
    { code: "0", data: validSuccessfulSubmit }
  );
  for (const field of ["channel", "channelTransactionNo"]) {
    assert.throws(
      () => validateCommandResponse("submit", { "prepay-order-no": "PRE-001" }, {
        code: "0", data: { ...validSuccessfulSubmit, [field]: undefined }
      }),
      (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
    );
  }
  for (const invalidAttemptFacts of [
    { latestAttemptStatus: undefined, latestAttemptStatusType: undefined, latestAttemptCompletedAt: undefined },
    { latestAttemptStatus: "处理中", latestAttemptStatusType: "info", latestAttemptCompletedAt: null },
    { latestAttemptStatus: "成功", latestAttemptStatusType: "warn", latestAttemptCompletedAt: "2026-09-27 12:00:01" },
    { latestAttemptStatus: "成功", latestAttemptStatusType: "success", latestAttemptCompletedAt: "2026-09-27 12:01:01" }
  ]) {
    assert.throws(
      () => validateCommandResponse("submit", { "prepay-order-no": "PRE-001" }, {
        code: "0", data: { ...validSuccessfulSubmit, ...invalidAttemptFacts }
      }),
      (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
    );
  }
  assert.throws(
    () => validateCommandResponse("submit", { "prepay-order-no": "PRE-001" }, {
      code: "0",
      data: {
        prepayOrderNo: "PRE-001",
        paymentOrderId: "PAY-001",
        orderNo: "ORDER-001",
        amount: "68.00",
        paymentStatus: "WAIT_CALLBACK",
        createdAt: "2026-09-27 11:59:00",
        expiresAt: "2026-09-27 12:00:00",
        cashierStatus: "支付中",
        cashierStatusType: "info"
      }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  assert.throws(
    () => validateCommandResponse("submit", { "prepay-order-no": "PRE-001" }, {
      code: "0",
      data: {
        prepayOrderNo: "PRE-001",
        paymentOrderId: "PAY-001",
        businessChainId: "CHAIN-PAY-001",
        orderNo: "ORDER-001",
        amount: "68.00",
        paymentStatus: "SUCCESS",
        createdAt: "2026-09-27 11:59:00",
        expiresAt: "2026-09-27 12:00:00",
        cashierStatus: "支付成功",
        cashierStatusType: "success",
        successAt: "2026-09-27 11:58:00"
      }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  assert.throws(
    () => validateCommandResponse("submit", { "prepay-order-no": "PRE-001" }, {
      code: "0",
      data: {
        prepayOrderNo: "PRE-001",
        paymentOrderId: "PAY-001",
        businessChainId: "CHAIN-PAY-001",
        orderNo: "ORDER-001",
        amount: "68.00",
        paymentStatus: "WAIT_CALLBACK",
        createdAt: "2026-02-30 11:59:00",
        expiresAt: "2026-09-27 12:00:00",
        cashierStatus: "支付中",
        cashierStatusType: "info"
      }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  assert.throws(
    () => validateCommandResponse("submit", { "prepay-order-no": "PRE-001" }, {
      code: "0",
      data: {
        prepayOrderNo: "PRE-001",
        paymentOrderId: "PAY-001",
        businessChainId: "CHAIN-PAY-001",
        orderNo: "ORDER-001",
        amount: "68.00",
        paymentStatus: "UNKNOWN_STATUS",
        expiresAt: "2026-09-27 12:00:00",
        cashierStatus: "支付中",
        cashierStatusType: "info"
      }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  assert.throws(
    () => validateCommandResponse("submit", { "prepay-order-no": "PRE-001" }, {
      code: "0",
      data: {
        prepayOrderNo: "PRE-001",
        paymentOrderId: "PAY-001",
        businessChainId: "CHAIN-PAY-001",
        orderNo: "ORDER-001",
        amount: "68.00",
        paymentStatus: "WAIT_CALLBACK",
        expiresAt: "not-a-time",
        cashierStatus: "待回调",
        cashierStatusType: "success"
      }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  assert.equal(
    validateCommandResponse("cashier", { "prepay-order-no": "PRE-001" }, {
      code: "0",
      data: {
        prepayOrderNo: "PRE-001",
        paymentOrderId: "PAY-001",
        businessChainId: "CHAIN-PAY-001",
        orderNo: "ORDER-001",
        amount: "¥68.00",
        status: "待支付",
        statusType: "warn",
        paymentStatus: "PREPAY_CREATED",
        expiresAt: "2026-09-27 12:00:00",
        channels: ["微信支付"]
      }
    }).data.orderNo,
    "ORDER-001"
  );
  assert.throws(
    () => validateCommandResponse("cashier", { "prepay-order-no": "PRE-001" }, {
      code: "0",
      data: { prepayOrderNo: "PRE-001", paymentOrderId: "PAY-001" }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  const validCashier = {
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
  assert.deepEqual(
    validateCommandResponse("cashier", { "prepay-order-no": "PRE-001" }, {
      code: "0",
      data: {
        ...validCashier,
        status: "支付成功",
        statusType: "success",
        paymentStatus: "SUCCESS",
        latestAttemptStatus: "成功",
        latestAttemptStatusType: "success",
        latestAttemptCompletedAt: "2026-09-27 12:00:01"
      }
    }).data.latestAttemptCompletedAt,
    "2026-09-27 12:00:01"
  );
  const validSuccessfulCashier = {
    ...validCashier,
    status: "支付成功",
    statusType: "success",
    paymentStatus: "SUCCESS",
    latestAttemptStatus: "成功",
    latestAttemptStatusType: "success",
    latestAttemptCompletedAt: "2026-09-27 12:00:01"
  };
  for (const invalidSuccessfulCashier of [
    { ...validSuccessfulCashier, latestAttemptStatus: undefined },
    { ...validSuccessfulCashier, latestAttemptStatusType: undefined },
    { ...validSuccessfulCashier, latestAttemptStatus: "失败", latestAttemptStatusType: "danger" },
    { ...validSuccessfulCashier, latestAttemptStatus: "成功", latestAttemptStatusType: "warn" },
    { ...validSuccessfulCashier, latestAttemptCompletedAt: undefined },
    { ...validSuccessfulCashier, latestAttemptCompletedAt: "not-a-time" }
  ]) {
    assert.throws(
      () => validateCommandResponse("cashier", { "prepay-order-no": "PRE-001" }, {
        code: "0", data: invalidSuccessfulCashier
      }),
      (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
    );
  }
  for (const invalidCashier of [
    { ...validCashier, status: "未知状态" },
    { ...validCashier, statusType: "success" },
    { ...validCashier, paymentStatus: "UNKNOWN_STATUS" },
    { ...validCashier, paymentStatus: "SUCCESS", status: "待支付", statusType: "warn" },
    { ...validCashier, expiresAt: "" },
    { ...validCashier, expiresAt: "not-a-time" },
    { ...validCashier, expiresAt: "2026-02-30 12:00:00" },
    { ...validCashier, latestAttemptStatus: 123 },
    { ...validCashier, latestAttemptStatus: "" },
    { ...validCashier, latestAttemptStatus: "成功", latestAttemptStatusType: "warn", latestAttemptCompletedAt: "2026-09-27 12:00:01" },
    { ...validCashier, latestAttemptStatus: "成功", latestAttemptStatusType: "success" },
    { ...validCashier, latestAttemptStatus: "处理中", latestAttemptStatusType: "info", latestAttemptCompletedAt: "2026-09-27 12:00:01" },
    { ...validCashier, latestAttemptStatusType: "warn" },
    { ...validCashier, channels: ["未知渠道"] },
    { ...validCashier, channels: [null] }
  ]) {
    assert.throws(
      () => validateCommandResponse("cashier", { "prepay-order-no": "PRE-001" }, { code: "0", data: invalidCashier }),
      (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
    );
  }
  assert.throws(
    () => validateCommandResponse("submit", { "prepay-order-no": "PRE-001" }, {
      code: "0",
      data: {
        prepayOrderNo: "PRE-001",
        paymentOrderId: "PAY-001",
        businessChainId: "CHAIN-PAY-001",
        orderNo: "ORDER-001",
        amount: "68.00",
        paymentStatus: "SUCCESS",
        expiresAt: "2026-09-27 12:00:00",
        cashierStatus: "待支付",
        cashierStatusType: "warn"
      }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
  assert.throws(
    () => validateCommandResponse("submit", { "prepay-order-no": "PRE-001" }, {
      code: "0",
      data: {
        prepayOrderNo: "PRE-001",
        paymentOrderId: "PAY-001",
        businessChainId: "CHAIN-PAY-001",
        orderNo: "ORDER-001",
        amount: "invalid"
      }
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-INVALID-RESPONSE"
  );
});

test("normalizes non-JSON upstream responses into a client error", async () => {
  await assert.rejects(
    executeRequest("http://localhost", { method: "GET", path: "/detail" }, async () => ({
      ok: false,
      status: 502,
      json: async () => {
        throw new SyntaxError("Unexpected token < in JSON");
      }
    })),
    (error) => {
      assert.equal(error.code, "PAYMENT-API-CLIENT-INVALID-RESPONSE");
      assert.equal(error.status, 502);
      assert.match(error.message, /无法解析/);
      return true;
    }
  );
});

test("normalizes network failures and timeouts into transport errors", async () => {
  await assert.rejects(
    executeRequest("http://localhost", { method: "GET", path: "/detail" }, async () => {
      throw new TypeError("fetch failed");
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-NETWORK"
  );

  await assert.rejects(
    executeRequest("http://localhost", { method: "GET", path: "/detail" }, async () => {
      const error = new Error("aborted");
      error.name = "AbortError";
      throw error;
    }),
    (error) => error.code === "PAYMENT-API-CLIENT-TIMEOUT"
  );
});
