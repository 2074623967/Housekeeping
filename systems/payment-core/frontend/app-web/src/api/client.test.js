import assert from "node:assert/strict";
import test from "node:test";
import { paymentApi } from "./client.js";

function successfulResponse() {
  return {
    ok: true,
    json: async () => ({ code: "0", data: {} })
  };
}

test("encodes cashier and payment detail identifiers as single path segments", async (context) => {
  const urls = [];
  context.mock.method(globalThis, "fetch", async (url) => {
    urls.push(url);
    return successfulResponse();
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
