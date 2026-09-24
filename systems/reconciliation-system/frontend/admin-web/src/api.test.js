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
    assert.deepEqual(await getBatchDetail("REC-001"), {
      batchNo: "REC-001",
      channelRecords: [],
      internalRecords: [],
      differences: []
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("normalizes null reconciliation detail collections", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response(JSON.stringify({
    code: "0",
    data: { batchNo: "REC-001", channelRecords: null, internalRecords: null, differences: null }
  }), { status: 200, headers: { "Content-Type": "application/json" } });
  try {
    const { getBatchDetail } = await import(`./api.js?detail-empty=${Date.now()}`);
    assert.deepEqual(await getBatchDetail("REC-001"), {
      batchNo: "REC-001",
      channelRecords: [],
      internalRecords: [],
      differences: []
    });
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

test("keeps reconciliation difference pagination in the query contract", async () => {
  const originalFetch = globalThis.fetch;
  let requestedUrl = "";
  globalThis.fetch = async (url) => {
    requestedUrl = url;
    return new Response(JSON.stringify({ code: "0", data: { items: [], total: 41, pageNo: 2, pageSize: 20 } }), {
      status: 200,
      headers: { "Content-Type": "application/json" }
    });
  };
  try {
    const { getDifferences } = await import(`./api.js?differences=${Date.now()}`);
    const result = await getDifferences({ differenceType: "AMOUNT_MISMATCH", status: "OPEN", pageNo: 2, pageSize: 20 });
    const query = new URL(requestedUrl, "http://127.0.0.1").searchParams;
    assert.equal(query.get("differenceType"), "AMOUNT_MISMATCH");
    assert.equal(query.get("status"), "OPEN");
    assert.equal(query.get("pageNo"), "2");
    assert.equal(query.get("pageSize"), "20");
    assert.equal(result.total, 41);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("keeps reconciliation batch pagination in the query contract", async () => {
  const originalFetch = globalThis.fetch;
  let requestedUrl = "";
  globalThis.fetch = async (url) => {
    requestedUrl = url;
    return new Response(JSON.stringify({ code: "0", data: { items: [], total: 41, pageNo: 2, pageSize: 20 } }), {
      status: 200,
      headers: { "Content-Type": "application/json" }
    });
  };
  try {
    const { getBatches } = await import(`./api.js?batches=${Date.now()}`);
    const result = await getBatches({ pageNo: 2, pageSize: 20 });
    const query = new URL(requestedUrl, "http://127.0.0.1").searchParams;
    assert.equal(query.get("pageNo"), "2");
    assert.equal(query.get("pageSize"), "20");
    assert.equal(result.total, 41);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("normalizes empty reconciliation pages for the admin view", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response(JSON.stringify({ code: "0", data: null }), {
    status: 200,
    headers: { "Content-Type": "application/json" }
  });
  try {
    const { getBatches, getDifferences } = await import(`./api.js?empty-pages=${Date.now()}`);
    assert.deepEqual(await getBatches({ pageNo: 3, pageSize: 10 }), {
      items: [],
      total: 0,
      pageNo: 3,
      pageSize: 10
    });
    assert.deepEqual(await getDifferences({ pageNo: 2, pageSize: 15 }), {
      items: [],
      total: 0,
      pageNo: 2,
      pageSize: 15
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("normalizes array reconciliation pages without leaking array indexes", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response(JSON.stringify({
    code: "0",
    data: [{ batchNo: "REC-001" }]
  }), { status: 200, headers: { "Content-Type": "application/json" } });
  try {
    const { getBatches } = await import(`./api.js?array-page=${Date.now()}`);
    assert.deepEqual(await getBatches({ pageNo: 2, pageSize: 10 }), {
      items: [{ batchNo: "REC-001" }],
      total: 1,
      pageNo: 2,
      pageSize: 10
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});
