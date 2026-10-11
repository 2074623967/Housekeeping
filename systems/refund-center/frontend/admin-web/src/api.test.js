import test from "node:test";
import assert from "node:assert/strict";
import { formatRefundTime, normalizeRefundOutboxResult, normalizeRefundResult } from "./api.js";

const validRefund = {
  refundOrderId: "REF-001",
  paymentOrderId: "PAY-001",
  businessChainId: "CHAIN-PAY-001",
  orderNo: "ORDER-001",
  paidAmount: "68.00",
  refundAmount: "18.00",
  status: "REVIEWING",
  statusType: "warn",
  appliedAt: "2026-09-29 06:03:17",
  successAt: null,
  terminalAt: null
};

test("formats refund lifecycle timestamps for the operations desk", () => {
  assert.equal(formatRefundTime("2026-09-29T06:03:17.693"), "2026-09-29 06:03:17.693");
  assert.equal(formatRefundTime("2026-09-29 06:03:17"), "2026-09-29 06:03:17");
  assert.equal(formatRefundTime(null), "-");
});

test("requires refund and outbox business keys on management responses", () => {
  assert.equal(normalizeRefundResult(validRefund, "REF-001").refundOrderId, "REF-001");
  assert.equal(normalizeRefundOutboxResult({ eventId: "EVT-001" }, "EVT-001").eventId, "EVT-001");
  assert.throws(
    () => normalizeRefundResult({ ...validRefund, refundOrderId: "REF-002" }, "REF-001"),
    (error) => error.code === "REFUND-CLIENT-INVALID-BUSINESS-RESULT"
  );
  assert.throws(
    () => normalizeRefundResult({ ...validRefund, status: "SUCCESS", statusType: "warn" }),
    (error) => error.code === "REFUND-CLIENT-INVALID-BUSINESS-RESULT"
  );
  for (const invalidRefund of [
    { ...validRefund, businessChainId: "CHAIN-OTHER" },
    { ...validRefund, paidAmount: "" },
    { ...validRefund, refundAmount: "68.01" },
    { ...validRefund, appliedAt: "2026-02-30 06:03:17" },
    { ...validRefund, status: "SUCCESS", statusType: "success", successAt: null, terminalAt: "2026-09-29 06:04:17" },
    { ...validRefund, status: "SUCCESS", statusType: "success", successAt: "2026-09-29 06:04:17", terminalAt: "2026-09-29 06:03:17" },
    { ...validRefund, status: "FAIL", statusType: "danger", terminalAt: null },
    { ...validRefund, status: "PROCESSING", statusType: "warn", terminalAt: "2026-09-29 06:04:17" }
  ]) {
    assert.throws(
      () => normalizeRefundResult(invalidRefund),
      (error) => error.code === "REFUND-CLIENT-INVALID-BUSINESS-RESULT"
    );
  }
  assert.throws(
    () => normalizeRefundOutboxResult({ eventId: "EVT-002" }, "EVT-001"),
    (error) => error.code === "REFUND-CLIENT-INVALID-BUSINESS-RESULT"
  );
});

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
    data: [validRefund]
  }), { status: 200, headers: { "Content-Type": "application/json" } });
  try {
    const { getRefunds } = await import(`./api.js?array-page=${Date.now()}`);
    assert.deepEqual(await getRefunds({ pageNo: 2, pageSize: 10 }), {
      items: [validRefund],
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
    data: { ...validRefund, operationLogs: null }
  }), { status: 200, headers: { "Content-Type": "application/json" } });
  try {
    const { getRefundDetail } = await import(`./api.js?detail-empty=${Date.now()}`);
    assert.deepEqual(await getRefundDetail("REF-001"), {
      ...validRefund,
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
      data: { ...validRefund, refundOrderId: "REF-NEW-001" }
    }), { status: 200, headers: { "Content-Type": "application/json" } });
  };
  try {
    const { applyRefund } = await import(`./api.js?apply=${Date.now()}`);
    assert.deepEqual(await applyRefund({
      paymentOrderId: "PAY-001",
      refundAmount: 12.5,
      refundMethod: "ORIGINAL"
    }), { ...validRefund, refundOrderId: "REF-NEW-001" });
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

test("aborts slow refund requests and clears the timeout", async () => {
  const originalFetch = globalThis.fetch;
  const originalSetTimeout = globalThis.setTimeout;
  const originalClearTimeout = globalThis.clearTimeout;
  let clearCount = 0;
  globalThis.setTimeout = (callback) => {
    callback();
    return "refund-timeout";
  };
  globalThis.clearTimeout = (timeoutId) => {
    assert.equal(timeoutId, "refund-timeout");
    clearCount += 1;
  };
  globalThis.fetch = async (_url, options) => {
    assert.equal(options.signal.aborted, true);
    const error = new Error("request aborted");
    error.name = "AbortError";
    throw error;
  };
  try {
    const { getOverview } = await import(`./api.js?timeout=${Date.now()}`);
    await assert.rejects(
      getOverview(),
      (error) => error.code === "REFUND-CLIENT-TIMEOUT"
    );
    assert.equal(clearCount, 1);
  } finally {
    globalThis.fetch = originalFetch;
    globalThis.setTimeout = originalSetTimeout;
    globalThis.clearTimeout = originalClearTimeout;
  }
});

test("aborts slow refund exports and clears the timeout", async () => {
  const originalFetch = globalThis.fetch;
  const originalSetTimeout = globalThis.setTimeout;
  const originalClearTimeout = globalThis.clearTimeout;
  let clearCount = 0;
  globalThis.setTimeout = (callback) => {
    callback();
    return "refund-export-timeout";
  };
  globalThis.clearTimeout = (timeoutId) => {
    assert.equal(timeoutId, "refund-export-timeout");
    clearCount += 1;
  };
  globalThis.fetch = async (_url, options) => {
    assert.equal(options.signal.aborted, true);
    const error = new Error("request aborted");
    error.name = "AbortError";
    throw error;
  };
  try {
    const { downloadRefundDetail } = await import(`./api.js?export-timeout=${Date.now()}`);
    await assert.rejects(
      downloadRefundDetail("REF-TIMEOUT"),
      (error) => error.code === "REFUND-CLIENT-TIMEOUT"
    );
    assert.equal(clearCount, 1);
  } finally {
    globalThis.fetch = originalFetch;
    globalThis.setTimeout = originalSetTimeout;
    globalThis.clearTimeout = originalClearTimeout;
  }
});
