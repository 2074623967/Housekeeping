import test from "node:test";
import assert from "node:assert/strict";
import { normalizePagePayload, payoutApi } from "./client.js";

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
