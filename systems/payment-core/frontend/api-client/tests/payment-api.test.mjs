import assert from "node:assert/strict";
import test from "node:test";

import {
  buildRequest,
  executeRequest,
  parseArguments,
  resolveBaseUrl
} from "../payment-api.mjs";

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
  }, 123);
  assert.equal(request.data.terminal, "API");
  assert.equal(request.data.sourceAppId, "housekeeping-api");
  assert.equal(request.data.channelCode, "ALIPAY_H5");
  assert.equal(request.data.idempotencyKey, "API-PRE-API-1-123");
});

test("builds refund application and operation contracts", () => {
  assert.deepEqual(buildRequest("refund-apply", {
    "payment-order-id": "PAY-REF-1",
    "refund-amount": "20.00",
    operator: "api-operator"
  }, 456), {
    method: "POST",
    path: "/api/refunds/apply",
    data: {
      paymentOrderId: "PAY-REF-1",
      idempotencyKey: "API-REFUND-PAY-REF-1-456",
      refundAmount: "20.00",
      refundMethod: "原路退款",
      refundReason: "服务调用端退款申请",
      operator: "api-operator"
    }
  });
  assert.deepEqual(buildRequest("refund-detail", { "refund-order-id": "REF/1?#" }).path,
    "/api/refunds/REF%2F1%3F%23");
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

test("resolves and normalizes the API base URL", () => {
  assert.equal(resolveBaseUrl({}, {}), "http://127.0.0.1:18081");
  assert.equal(resolveBaseUrl({}, { PAYMENT_API_BASE_URL: "https://pay.example.com/" }), "https://pay.example.com");
  assert.equal(resolveBaseUrl({ "base-url": "http://localhost:19000/" }, {}), "http://localhost:19000");
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
