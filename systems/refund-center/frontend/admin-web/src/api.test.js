import test from "node:test";
import assert from "node:assert/strict";

test("normalizes null refund page items", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response(JSON.stringify({
    code: "0",
    data: { total: 0, pageNo: 1, pageSize: 20, items: null }
  }), { status: 200, headers: { "Content-Type": "application/json" } });
  try {
    const { getRefunds } = await import(`./api.js?test=${Date.now()}`);
    const result = await getRefunds({ pageNo: 1, pageSize: 20 });
    assert.deepEqual(result.items, []);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("normalizes null refund outbox page items", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response(JSON.stringify({
    code: "0",
    data: { total: 0, pageNo: 1, pageSize: 20, items: null }
  }), { status: 200, headers: { "Content-Type": "application/json" } });
  try {
    const { getRefundOutbox } = await import(`./api.js?test=${Date.now()}`);
    const result = await getRefundOutbox({ pageNo: 1, pageSize: 20 });
    assert.deepEqual(result.items, []);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
