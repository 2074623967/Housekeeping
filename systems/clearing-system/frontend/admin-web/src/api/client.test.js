import assert from "node:assert/strict";
import test from "node:test";
import { batchApi } from "./client.js";

test("keeps the clearing service envelope on success", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: { batchNo: "CLR-001" } };
    }
  });

  try {
    assert.deepEqual(await batchApi.getDetail("CLR-001"), { batchNo: "CLR-001" });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports non-JSON clearing responses with a stable client error", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: false,
    status: 502,
    async json() {
      throw new SyntaxError("Unexpected token < in JSON");
    }
  });

  try {
    await assert.rejects(
      batchApi.getDetail("CLR-INVALID"),
      (error) => error.code === "CLEARING-CLIENT-INVALID-RESPONSE"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports clearing network failures with a stable client error", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => {
    throw new TypeError("fetch failed");
  };

  try {
    await assert.rejects(
      batchApi.getDetail("CLR-NETWORK"),
      (error) => error.code === "CLEARING-CLIENT-NETWORK"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});
