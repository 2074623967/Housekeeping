import assert from "node:assert/strict";
import test from "node:test";
import { gatewayAccessApi } from "./client.js";

test("keeps the gateway access service envelope on success", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: { status: "READY" } };
    }
  });

  try {
    assert.deepEqual(await gatewayAccessApi.getSummary(), { status: "READY" });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports non-JSON gateway access responses with a stable client error", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response("upstream unavailable", { status: 502 });

  try {
    await assert.rejects(
      gatewayAccessApi.getSummary(),
      (error) => error.code === "GATEWAY-CLIENT-INVALID-RESPONSE"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports gateway access network failures with a stable client error", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => {
    throw new TypeError("fetch failed");
  };

  try {
    await assert.rejects(
      gatewayAccessApi.getSummary(),
      (error) => error.code === "GATEWAY-CLIENT-NETWORK"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});
