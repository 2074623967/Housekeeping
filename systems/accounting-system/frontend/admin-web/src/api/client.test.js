import assert from "node:assert/strict";
import test from "node:test";
import { accountApi, ledgerApi, normalizePagePayload, subjectApi } from "./client.js";

test("normalizes null accounting page items without changing summaries", () => {
  assert.deepEqual(normalizePagePayload({ items: null, total: 0 }).items, []);
  const summary = { postedCount: 2 };
  assert.equal(normalizePagePayload(summary), summary);
});

test("normalizes a null accounting page response for list consumers", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: null };
    }
  });

  try {
    assert.deepEqual(await ledgerApi.getList(), {
      items: [],
      total: 0,
      pageNo: 1,
      pageSize: 20
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("normalizes null accounting detail ledgers", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: { account: { accountNo: "ACC-001" }, recentLedgers: null } };
    }
  });

  try {
    assert.deepEqual(await accountApi.getDetail("ACC-001"), {
      account: { accountNo: "ACC-001" },
      recentLedgers: []
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

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
