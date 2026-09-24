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

test("normalizes array refund pages without leaking array indexes", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response(JSON.stringify({
    code: "0",
    data: [{ refundOrderId: "REF-001" }]
  }), { status: 200, headers: { "Content-Type": "application/json" } });
  try {
    const { getRefunds } = await import(`./api.js?array-page=${Date.now()}`);
    assert.deepEqual(await getRefunds({ pageNo: 2, pageSize: 10 }), {
      items: [{ refundOrderId: "REF-001" }],
      total: 1,
      pageNo: 2,
      pageSize: 10
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("normalizes malformed refund pages to the requested empty page", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response(JSON.stringify({
    code: "0",
    data: "not-a-page"
  }), { status: 200, headers: { "Content-Type": "application/json" } });
  try {
    const { getRefunds } = await import(`./api.js?malformed-page=${Date.now()}`);
    assert.deepEqual(await getRefunds({ pageNo: 3, pageSize: 10 }), {
      items: [],
      total: 0,
      pageNo: 3,
      pageSize: 10
    });
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

test("normalizes null refund detail operation logs", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response(JSON.stringify({
    code: "0",
    data: { refundOrderId: "REF-001", operationLogs: null }
  }), { status: 200, headers: { "Content-Type": "application/json" } });
  try {
    const { getRefundDetail } = await import(`./api.js?detail-empty=${Date.now()}`);
    assert.deepEqual(await getRefundDetail("REF-001"), {
      refundOrderId: "REF-001",
      operationLogs: []
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("submits a refund application through the refund API", async () => {
  const originalFetch = globalThis.fetch;
  let request;
  globalThis.fetch = async (url, options) => {
    request = { url, options };
    return new Response(JSON.stringify({
      code: "0",
      data: { refundOrderId: "REF-NEW-001", status: "REVIEWING" }
    }), { status: 200, headers: { "Content-Type": "application/json" } });
  };
  try {
    const { applyRefund } = await import(`./api.js?apply=${Date.now()}`);
    assert.deepEqual(await applyRefund({
      paymentOrderId: "PAY-001",
      refundAmount: 12.5,
      refundMethod: "ORIGINAL"
    }), { refundOrderId: "REF-NEW-001", status: "REVIEWING" });
    assert.equal(request.url, "/api/refunds/apply");
    assert.equal(request.options.method, "POST");
    assert.deepEqual(JSON.parse(request.options.body), {
      paymentOrderId: "PAY-001",
      refundAmount: 12.5,
      refundMethod: "ORIGINAL"
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports non-JSON refund responses with a stable client error", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response("upstream unavailable", { status: 502 });
  try {
    const { getOverview } = await import(`./api.js?invalid=${Date.now()}`);
    await assert.rejects(
      getOverview(),
      (error) => error.code === "REFUND-CLIENT-INVALID-RESPONSE"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports null refund envelopes with a stable client error", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response("null", {
    status: 200,
    headers: { "Content-Type": "application/json" }
  });
  try {
    const { getOverview } = await import(`./api.js?null-envelope=${Date.now()}`);
    await assert.rejects(
      getOverview(),
      (error) => error.code === "REFUND-CLIENT-HTTP-200"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports refund network failures with a stable client error", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => {
    throw new TypeError("fetch failed");
  };
  try {
    const { getOverview } = await import(`./api.js?network=${Date.now()}`);
    await assert.rejects(
      getOverview(),
      (error) => error.code === "REFUND-CLIENT-NETWORK"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});
