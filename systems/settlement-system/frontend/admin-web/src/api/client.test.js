import test from "node:test";
import assert from "node:assert/strict";
import { batchApi, dashboardApi, eventApi, normalizeDashboardPayload, normalizePagePayload, orderApi, payoutApi } from "./client.js";

test("normalizes empty settlement page items", () => {
  const result = normalizePagePayload({ items: null, total: 0, pageNo: 1, pageSize: 20 });

  assert.deepEqual(result.items, []);
  assert.equal(result.total, 0);
});

test("keeps settlement summaries and populated pages unchanged", () => {
  const summary = { pendingCount: 0 };
  const items = [{ settlementNo: "SET-001" }];

  assert.equal(normalizePagePayload(summary), summary);
  assert.equal(normalizePagePayload({ items }).items, items);
});

test("normalizes nullable settlement dashboard collections", async () => {
  assert.deepEqual(normalizeDashboardPayload(null), null);
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: { recentBatches: null, riskPayouts: { items: [] } } };
    }
  });

  try {
    assert.deepEqual(await dashboardApi.get(), {
      recentBatches: [],
      recentOrders: [],
      riskPayouts: [],
      recentEvents: []
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("normalizes null settlement order detail collections", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: { order: { settlementNo: "SET-001" }, items: null, auditLogs: null } };
    }
  });

  try {
    assert.deepEqual(await orderApi.getDetail("SET-001"), {
      order: { settlementNo: "SET-001" },
      items: [],
      auditLogs: []
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("posts a read-only payout inspection request", async () => {
  const originalFetch = globalThis.fetch;
  let request;
  globalThis.fetch = async (url, options) => {
    request = { url, options };
    return {
      ok: true,
      async json() {
        return {
          code: "0",
          data: {
            totalBatchCount: 2,
            pendingBatchCount: 1,
            failedBatchCount: 1,
            completedBatchCount: 0,
            pendingBatchNos: ["PBT-1"],
            failedBatchNos: ["PBT-2"]
          }
        };
      }
    };
  };

  try {
    const result = await payoutApi.inspect();

    assert.equal(request.url, "/api/settlements/payouts/inspect");
    assert.equal(request.options.method, "POST");
    assert.equal(request.options.body, "{}");
    assert.equal(result.failedBatchNos[0], "PBT-2");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("keeps payout list pagination in the query contract", async () => {
  const originalFetch = globalThis.fetch;
  let requestUrl = "";
  globalThis.fetch = async (url) => {
    requestUrl = url;
    return {
      ok: true,
      async json() {
        return { code: "0", data: { items: [], total: 41, pageNo: 2, pageSize: 20 } };
      }
    };
  };

  try {
    const result = await payoutApi.getList({ batchNo: "SET-001", payoutStatus: "待出款", pageNo: 2, pageSize: 20 });
    const query = new URL(requestUrl, "http://127.0.0.1").searchParams;
    assert.equal(query.get("batchNo"), "SET-001");
    assert.equal(query.get("payoutStatus"), "待出款");
    assert.equal(query.get("pageNo"), "2");
    assert.equal(query.get("pageSize"), "20");
    assert.equal(result.total, 41);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("passes pagination to settlement batch, order, and event lists", async () => {
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
    await orderApi.getList({ pageNo: 3, pageSize: 20 });
    await eventApi.getList({ pageNo: 3, pageSize: 20 });
    assert.equal(requestedUrls.length, 3);
    for (const requestedUrl of requestedUrls) {
      const query = new URL(requestedUrl, "http://localhost").searchParams;
      assert.equal(query.get("pageNo"), "3");
      assert.equal(query.get("pageSize"), "20");
    }
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports non-JSON settlement responses with a stable client error", async () => {
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
      payoutApi.inspect(),
      (error) => error.code === "SETTLEMENT-CLIENT-INVALID-RESPONSE"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports settlement network failures with a stable client error", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => {
    throw new TypeError("fetch failed");
  };

  try {
    await assert.rejects(
      payoutApi.inspect(),
      (error) => error.code === "SETTLEMENT-CLIENT-NETWORK"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});
