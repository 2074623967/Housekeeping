import assert from "node:assert/strict";
import test from "node:test";
import { ledgerApi, subjectApi } from "./client.js";

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

test("passes ledger filters and pagination to the accounting API", async () => {
  const originalFetch = globalThis.fetch;
  let requestedUrl = "";
  globalThis.fetch = async (url) => {
    requestedUrl = String(url);
    return {
      ok: true,
      async json() {
        return { code: "0", data: { items: [], total: 41, pageNo: 3, pageSize: 20 } };
      }
    };
  };

  try {
    const result = await ledgerApi.getList({ accountNo: "ACC-1", bizNo: "PAY-1", bizType: "PAYMENT", pageNo: 3, pageSize: 20 });
    const query = new URL(requestedUrl, "http://localhost").searchParams;
    assert.equal(query.get("accountNo"), "ACC-1");
    assert.equal(query.get("bizNo"), "PAY-1");
    assert.equal(query.get("bizType"), "PAYMENT");
    assert.equal(query.get("pageNo"), "3");
    assert.equal(query.get("pageSize"), "20");
    assert.deepEqual(result, { items: [], total: 41, pageNo: 3, pageSize: 20 });
  } finally {
    globalThis.fetch = originalFetch;
  }
});
