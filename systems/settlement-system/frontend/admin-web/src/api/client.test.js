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
