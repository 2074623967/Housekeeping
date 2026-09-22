import assert from "node:assert/strict";
import test from "node:test";
import { batchApi, feeApi, normalizePagePayload, orderApi, ruleApi, shareApi } from "./client.js";

test("normalizes null clearing page items without changing non-page payloads", () => {
  const page = normalizePagePayload({ items: null, total: 0, pageNo: 1, pageSize: 20 });
  assert.deepEqual(page.items, []);
  const detail = { clearingNo: "CLO-001" };
  assert.equal(normalizePagePayload(detail), detail);
});

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

test("sends all clearing order filters and pagination parameters", async () => {
  const originalFetch = globalThis.fetch;
  let requestedUrl = "";
  globalThis.fetch = async (url) => {
    requestedUrl = String(url);
    return {
      ok: true,
      async json() {
        return { code: "0", data: { items: [], total: 0, pageNo: 2, pageSize: 20 } };
      }
    };
  };

  try {
    await orderApi.getList({
      batchNo: "CLB-001",
      orderNo: "ORD-001",
      paymentOrderId: "PAY-001",
      clearingStatus: "清分成功",
      pageNo: 2,
      pageSize: 20
    });
    const query = new URL(requestedUrl, "http://localhost").searchParams;
    assert.equal(query.get("batchNo"), "CLB-001");
    assert.equal(query.get("orderNo"), "ORD-001");
    assert.equal(query.get("paymentOrderId"), "PAY-001");
    assert.equal(query.get("clearingStatus"), "清分成功");
    assert.equal(query.get("pageNo"), "2");
    assert.equal(query.get("pageSize"), "20");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("passes pagination to clearing batch, rule, fee, and share lists", async () => {
  const originalFetch = globalThis.fetch;
  const requestedUrls = [];
  globalThis.fetch = async (url) => {
    requestedUrls.push(String(url));
    return {
      ok: true,
      async json() {
        return { code: "0", data: { items: [], total: 41, pageNo: 3, pageSize: 20 } };
      }
    };
  };

  try {
    await batchApi.getList({ pageNo: 3, pageSize: 20 });
    await ruleApi.getList({ pageNo: 3, pageSize: 20 });
    await feeApi.getList({ pageNo: 3, pageSize: 20 });
    await shareApi.getList({ pageNo: 3, pageSize: 20 });
    assert.equal(requestedUrls.length, 4);
    for (const requestedUrl of requestedUrls) {
      const query = new URL(requestedUrl, "http://localhost").searchParams;
      assert.equal(query.get("pageNo"), "3");
      assert.equal(query.get("pageSize"), "20");
    }
  } finally {
    globalThis.fetch = originalFetch;
  }
});
