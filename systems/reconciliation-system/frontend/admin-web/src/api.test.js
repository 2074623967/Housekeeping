import assert from "node:assert/strict";
import test from "node:test";

test("keeps the reconciliation service envelope on success", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response(JSON.stringify({
    code: "0",
    data: { batchNo: "REC-001" }
  }), { status: 200, headers: { "Content-Type": "application/json" } });
  try {
    const { getBatchDetail } = await import(`./api.js?success=${Date.now()}`);
    assert.deepEqual(await getBatchDetail("REC-001"), { batchNo: "REC-001" });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports non-JSON reconciliation responses with a stable client error", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response("upstream unavailable", { status: 502 });
  try {
    const { getOverview } = await import(`./api.js?invalid=${Date.now()}`);
    await assert.rejects(
      getOverview(),
      (error) => error.code === "RECONCILIATION-CLIENT-INVALID-RESPONSE"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports reconciliation network failures with a stable client error", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => {
    throw new TypeError("fetch failed");
  };
  try {
    const { getOverview } = await import(`./api.js?network=${Date.now()}`);
    await assert.rejects(
      getOverview(),
      (error) => error.code === "RECONCILIATION-CLIENT-NETWORK"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("encodes business chain identifiers when loading cross-system facts", async () => {
  const originalFetch = globalThis.fetch;
  let requestedUrl = "";
  globalThis.fetch = async (url) => {
    requestedUrl = url;
    return new Response(JSON.stringify({
      code: "0",
      data: { businessChainId: "CHAIN/001", chainStatus: "COMPLETED" }
    }), { status: 200, headers: { "Content-Type": "application/json" } });
  };
  try {
    const { getCrossSystemChain } = await import(`./api.js?chain=${Date.now()}`);
    assert.deepEqual(await getCrossSystemChain("CHAIN/001"), {
      businessChainId: "CHAIN/001",
      chainStatus: "COMPLETED"
    });
    assert.equal(requestedUrl, "/api/reconciliation/cross-system/chains/CHAIN%2F001");
  } finally {
    globalThis.fetch = originalFetch;
  }
});
