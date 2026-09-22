import assert from "node:assert/strict";
import test from "node:test";
import { subjectApi } from "./client.js";

test("keeps the accounting service envelope on success", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (url) => ({
    ok: true,
    url,
    async json() {
      return { code: "0", data: { postedCount: 2 } };
    }
  });

  try {
    assert.deepEqual(await subjectApi.getList(), { postedCount: 2 });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports non-JSON accounting responses with a stable client error", async () => {
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
      subjectApi.getList(),
      (error) => error.code === "ACCOUNTING-CLIENT-INVALID-RESPONSE"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports accounting network failures with a stable client error", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => {
    throw new TypeError("fetch failed");
  };

  try {
    await assert.rejects(
      subjectApi.getList(),
      (error) => error.code === "ACCOUNTING-CLIENT-NETWORK"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});
