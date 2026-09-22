import assert from "node:assert/strict";
import test from "node:test";
import { riskControlApi } from "./client.js";

test("keeps the risk control service envelope on success", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: { status: "READY" } };
    }
  });

  try {
    assert.deepEqual(await riskControlApi.getSummary(), { status: "READY" });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports non-JSON risk control responses with a stable client error", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response("upstream unavailable", { status: 502 });

  try {
    await assert.rejects(
      riskControlApi.getSummary(),
      (error) => error.code === "RISK-CLIENT-INVALID-RESPONSE"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports risk control network failures with a stable client error", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => {
    throw new TypeError("fetch failed");
  };

  try {
    await assert.rejects(
      riskControlApi.getSummary(),
      (error) => error.code === "RISK-CLIENT-NETWORK"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});
