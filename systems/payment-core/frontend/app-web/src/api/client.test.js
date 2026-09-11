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

  assert.deepEqual(urls, [
    "/api/payments/cashier/PRE%2F1%3F%23%25",
    "/api/payments/PAY%2F1%3F%23%25"
  ]);
});
