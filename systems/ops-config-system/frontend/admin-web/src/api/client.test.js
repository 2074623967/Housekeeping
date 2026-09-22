import assert from "node:assert/strict";
import test from "node:test";
import { opsConfigApi } from "./client.js";

test("keeps the ops config service envelope on success", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: { status: "READY" } };
    }
  });

  try {
    assert.deepEqual(await opsConfigApi.getSummary(), { status: "READY" });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports non-JSON ops config responses with a stable client error", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response("upstream unavailable", { status: 502 });

  try {
    await assert.rejects(
      opsConfigApi.getSummary(),
      (error) => error.code === "OPS-CONFIG-CLIENT-INVALID-RESPONSE"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports ops config network failures with a stable client error", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => {
    throw new TypeError("fetch failed");
  };

  try {
    await assert.rejects(
      opsConfigApi.getSummary(),
      (error) => error.code === "OPS-CONFIG-CLIENT-NETWORK"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});
