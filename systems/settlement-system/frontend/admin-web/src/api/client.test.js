import test from "node:test";
import assert from "node:assert/strict";
import {
  batchApi,
  dashboardApi,
  eventApi,
  normalizeDashboardPayload,
  normalizePagePayload,
  normalizePayoutBatchPage,
  normalizePayoutBatchResult,
  normalizePayoutRecordPage,
  normalizeSettlementBatchResult,
  normalizeSettlementOrderDetailResult,
  normalizeSettlementOrderResult,
  orderApi,
  payoutApi
} from "./client.js";

const validSettlementOrder = {
  settlementNo: "SET-001",
  businessChainId: "CHAIN-PAY-001",
  clearingNo: "CLR-001",
  payScene: "HOME_CLEAN",
  targetType: "WORKER",
  targetNo: "WORKER-001",
  shouldSettleAmount: "88.00",
  deductAmount: "8.00",
  netSettleAmount: "80.00",
  settlementStatus: "待审核",
  settlementStatusType: "warn",
  auditStatus: "待审核",
  auditStatusType: "warn",
  payoutStatus: "待出款",
  payoutStatusType: "warn"
};

const validPayoutBatch = {
  payoutBatchNo: "PBT-001",
  batchNo: "SET-BATCH-001",
  payoutChannel: "银行代付",
  payoutStatus: "待出款",
  payoutStatusType: "warn",
  payoutCount: 2,
  successCount: 0,
  failedCount: 0,
  totalAmount: "80.00",
  createdBy: "operator-001",
  createdAt: "2026-09-30 10:00:00",
  finishedAt: ""
};

const validPayoutRecord = {
  payoutNo: "PO-001",
  payoutBatchNo: "PBT-001",
  settlementNo: "SET-001",
  targetNo: "WORKER-001",
  targetName: "服务者甲",
  payoutAmount: "80.00",
  payoutStatus: "待出款",
  payoutStatusType: "warn",
  retryCount: "0",
  createdAt: "2026-09-30 10:01:00"
};

test("validates payout batch and record list business facts", () => {
  assert.equal(normalizePayoutBatchPage({ items: [validPayoutBatch] }).items[0].payoutBatchNo, "PBT-001");
  assert.equal(normalizePayoutRecordPage({ items: [validPayoutRecord] }, "PBT-001").items[0].payoutNo, "PO-001");
  assert.throws(
    () => normalizePayoutBatchPage({ items: [{ ...validPayoutBatch, successCount: 2, failedCount: 1 }] }),
    (error) => error.code === "SETTLEMENT-CLIENT-INVALID-BUSINESS-RESULT"
  );
  assert.throws(
    () => normalizePayoutBatchPage({ items: [{ ...validPayoutBatch, totalAmount: "80.001" }] }),
    (error) => error.code === "SETTLEMENT-CLIENT-INVALID-BUSINESS-RESULT"
  );
  assert.throws(
    () => normalizePayoutRecordPage({ items: [{ ...validPayoutRecord, retryCount: "-1" }] }, "PBT-001"),
    (error) => error.code === "SETTLEMENT-CLIENT-INVALID-BUSINESS-RESULT"
  );
  assert.throws(
    () => normalizePayoutRecordPage({ items: [{ ...validPayoutRecord, payoutBatchNo: "PBT-002" }] }, "PBT-001"),
    (error) => error.code === "SETTLEMENT-CLIENT-INVALID-BUSINESS-RESULT"
  );
  assert.deepEqual(normalizePayoutBatchPage({ items: null, total: 0 }).items, []);
  assert.deepEqual(normalizePayoutRecordPage({ items: null, total: 0 }, "PBT-001").items, []);
});

test("requires settlement business keys on detail and mutation responses", () => {
  assert.equal(normalizeSettlementBatchResult({ batchNo: "SET-BATCH-001" }, "SET-BATCH-001").batchNo, "SET-BATCH-001");
  assert.equal(normalizeSettlementOrderResult(validSettlementOrder, "SET-001").settlementNo, "SET-001");
  assert.equal(normalizePayoutBatchResult({ payoutBatchNo: "PBT-001" }, "PBT-001").payoutBatchNo, "PBT-001");
  assert.equal(normalizeSettlementOrderDetailResult({ order: validSettlementOrder }, "SET-001").order.settlementNo, "SET-001");
  assert.throws(
    () => normalizeSettlementBatchResult({ batchNo: "SET-BATCH-002" }, "SET-BATCH-001"),
    (error) => error.code === "SETTLEMENT-CLIENT-INVALID-BUSINESS-RESULT"
  );
  assert.throws(
    () => normalizeSettlementOrderResult({ ...validSettlementOrder, settlementNo: "SET-002" }, "SET-001"),
    (error) => error.code === "SETTLEMENT-CLIENT-INVALID-BUSINESS-RESULT"
  );
  assert.throws(
    () => normalizePayoutBatchResult({ payoutBatchNo: "PBT-002" }, "PBT-001"),
    (error) => error.code === "SETTLEMENT-CLIENT-INVALID-BUSINESS-RESULT"
  );
  assert.throws(
    () => normalizeSettlementOrderDetailResult({ order: { ...validSettlementOrder, settlementNo: "SET-002" } }, "SET-001"),
    (error) => error.code === "SETTLEMENT-CLIENT-INVALID-BUSINESS-RESULT"
  );
  for (const invalidOrder of [
    { ...validSettlementOrder, businessChainId: "" },
    { ...validSettlementOrder, payScene: 123 },
    { ...validSettlementOrder, deductAmount: "9.00", netSettleAmount: "80.00" },
    { ...validSettlementOrder, netSettleAmount: "80.001" },
    { ...validSettlementOrder, payoutStatusType: "" }
  ]) {
    assert.throws(
      () => normalizeSettlementOrderResult(invalidOrder),
      (error) => error.code === "SETTLEMENT-CLIENT-INVALID-BUSINESS-RESULT"
    );
  }
});

test("normalizes empty settlement page items", () => {
  const result = normalizePagePayload({ items: null, total: 0, pageNo: 1, pageSize: 20 });

  assert.deepEqual(result.items, []);
  assert.equal(result.total, 0);
});

test("normalizes malformed settlement pagination metadata", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: { items: [{ settlementNo: "SET-002" }], total: "invalid", pageNo: 0, pageSize: -1 } };
    }
  });
  try {
    assert.deepEqual(await batchApi.getList(), {
      items: [{ settlementNo: "SET-002" }], total: 1, pageNo: 1, pageSize: 20
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
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
      return { code: "0", data: { order: validSettlementOrder, items: null, auditLogs: null } };
    }
  });

  try {
    assert.deepEqual(await orderApi.getDetail("SET-001"), {
      order: validSettlementOrder,
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

test("rejects payout records that do not belong to the requested batch", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: { items: [{ ...validPayoutRecord, payoutBatchNo: "PBT-002" }] } };
    }
  });

  try {
    await assert.rejects(
      payoutApi.records("PBT-001"),
      (error) => error.code === "SETTLEMENT-CLIENT-INVALID-BUSINESS-RESULT"
    );
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

test("aborts slow settlement requests and clears the timeout", async () => {
  const originalFetch = globalThis.fetch;
  const originalSetTimeout = globalThis.setTimeout;
  const originalClearTimeout = globalThis.clearTimeout;
  let clearCount = 0;
  globalThis.setTimeout = (callback) => {
    callback();
    return "settlement-timeout";
  };
  globalThis.clearTimeout = (timeoutId) => {
    assert.equal(timeoutId, "settlement-timeout");
    clearCount += 1;
  };
  globalThis.fetch = async (_url, options) => {
    assert.equal(options.signal.aborted, true);
    const error = new Error("request aborted");
    error.name = "AbortError";
    throw error;
  };
  try {
    await assert.rejects(
      payoutApi.inspect(),
      (error) => error.code === "SETTLEMENT-CLIENT-TIMEOUT"
    );
    assert.equal(clearCount, 1);
  } finally {
    globalThis.fetch = originalFetch;
    globalThis.setTimeout = originalSetTimeout;
    globalThis.clearTimeout = originalClearTimeout;
  }
});
