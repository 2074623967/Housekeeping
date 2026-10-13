import assert from "node:assert/strict";
import test from "node:test";
import {
  batchApi,
  eventApi,
  feeApi,
  normalizeClearingBatchResult,
  normalizeClearingEventListPage,
  normalizeClearingOrderDetailResult,
  normalizeClearingRuleResult,
  normalizeFeeRuleResult,
  normalizePagePayload,
  orderApi,
  ruleApi,
  shareApi
} from "./client.js";

test("exposes clearing events to the admin page as read-only", () => {
  assert.deepEqual(Object.keys(eventApi), ["getList"]);
});

test("requires clearing business keys on detail and mutation responses", () => {
  assert.equal(normalizeClearingBatchResult({ batchNo: "CLB-001" }, "CLB-001").batchNo, "CLB-001");
  assert.equal(normalizeClearingRuleResult({ ruleNo: "RULE-001" }, "RULE-001").ruleNo, "RULE-001");
  assert.equal(normalizeFeeRuleResult({ feeRuleNo: "FEE-001" }, "FEE-001").feeRuleNo, "FEE-001");
  const validOrder = {
    clearingNo: "CLO-001", businessChainId: "CHAIN-PAY-001", paymentOrderId: "PAY-001", payScene: "HOME_CLEAN",
    orderNo: "ORD-001", batchNo: "CLB-001", ruleNo: "RULE-001",
    orderAmount: "88.00", merchantAmount: "8.80", workerAmount: "71.16",
    platformAmount: "7.04", channelFeeAmount: "1.00", subsidyAmount: "0.00",
    clearingStatus: "清分成功", clearingStatusType: "success", createdAt: "2026-09-30 10:00:00"
  };
  assert.equal(normalizeClearingOrderDetailResult({ order: validOrder }, "CLO-001").order.clearingNo, "CLO-001");
  for (const invalidResult of [
    () => normalizeClearingBatchResult({ batchNo: "CLB-002" }, "CLB-001"),
    () => normalizeClearingRuleResult({ ruleNo: "RULE-002" }, "RULE-001"),
    () => normalizeFeeRuleResult({ feeRuleNo: "FEE-002" }, "FEE-001"),
    () => normalizeClearingOrderDetailResult({ order: { ...validOrder, clearingNo: "CLO-002" } }, "CLO-001")
  ]) {
    assert.throws(invalidResult, (error) => error.code === "CLEARING-CLIENT-INVALID-BUSINESS-RESULT");
  }
  for (const invalidOrder of [
    { ...validOrder, businessChainId: "CHAIN-OTHER" },
    { ...validOrder, payScene: 123 },
    { ...validOrder, workerAmount: "71.15" },
    { ...validOrder, clearingStatusType: "warn" }
  ]) {
    assert.throws(
      () => normalizeClearingOrderDetailResult({ order: invalidOrder }, "CLO-001"),
      (error) => error.code === "CLEARING-CLIENT-INVALID-BUSINESS-RESULT"
    );
  }
});

test("rejects clearing event rows without lifecycle and retry facts", () => {
  const validEvent = {
    businessChainId: "CHAIN-PAY-001",
    eventNo: "EVT-001",
    eventType: "PAYMENT_SUCCESS",
    bizNo: "PAY-001",
    eventStatus: "已消费",
    statusType: "success",
    publishStatus: "NOT_APPLICABLE",
    retryCount: 0,
    createdAt: "2026-09-30 10:00:00"
  };
  assert.equal(normalizeClearingEventListPage({ items: [validEvent], total: 1 }).items[0].eventNo, "EVT-001");
  for (const invalidEvent of [
    { ...validEvent, businessChainId: "" },
    { ...validEvent, eventStatus: "" },
    { ...validEvent, retryCount: -1 },
    { ...validEvent, createdAt: undefined }
  ]) {
    assert.throws(
      () => normalizeClearingEventListPage({ items: [invalidEvent], total: 1 }),
      (error) => error.code === "CLEARING-CLIENT-INVALID-BUSINESS-RESULT"
    );
  }
});

test("normalizes null clearing page items without changing non-page payloads", () => {
  const page = normalizePagePayload({ items: null, total: 0, pageNo: 1, pageSize: 20 });
  assert.deepEqual(page.items, []);
  const detail = { clearingNo: "CLO-001" };
  assert.equal(normalizePagePayload(detail), detail);
});

test("normalizes a null clearing page response for list consumers", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: null };
    }
  });

  try {
    assert.deepEqual(await batchApi.getList(), {
      items: [],
      total: 0,
      pageNo: 1,
      pageSize: 20
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("normalizes malformed clearing pagination metadata", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: { items: [{
        batchNo: "CLB-002", batchDate: "2026-09-30", sourceType: "PAYMENT_SUCCESS",
        batchStatus: "已完成", batchStatusType: "success", totalOrderCount: 1,
        successOrderCount: 1, failedOrderCount: 0, totalAmount: "88.00",
        versionNo: "V1", createdBy: "system", createdAt: "2026-09-30 10:00:00",
        finishedAt: "2026-09-30 10:01:00"
      }], total: "invalid", pageNo: 0, pageSize: -1 } };
    }
  });
  try {
    assert.deepEqual(await batchApi.getList(), {
      items: [{
        batchNo: "CLB-002", batchDate: "2026-09-30", sourceType: "PAYMENT_SUCCESS",
        batchStatus: "已完成", batchStatusType: "success", totalOrderCount: 1,
        successOrderCount: 1, failedOrderCount: 0, totalAmount: "88.00",
        versionNo: "V1", createdBy: "system", createdAt: "2026-09-30 10:00:00",
        finishedAt: "2026-09-30 10:01:00"
      }], total: 1, pageNo: 1, pageSize: 20
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("rejects clearing batch pages with inconsistent counts", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: { items: [{
        batchNo: "CLB-003", batchDate: "2026-09-30", sourceType: "PAYMENT_SUCCESS",
        batchStatus: "处理中", batchStatusType: "warn", totalOrderCount: 1,
        successOrderCount: 1, failedOrderCount: 1, totalAmount: "88.00",
        versionNo: "V1", createdBy: "system", createdAt: "2026-09-30 10:00:00"
      }], total: 1, pageNo: 1, pageSize: 20 } };
    }
  });
  try {
    await assert.rejects(
      batchApi.getList(),
      (error) => error.code === "CLEARING-CLIENT-INVALID-BUSINESS-RESULT"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
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

test("normalizes null clearing order detail collections", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: { order: {
        clearingNo: "CLO-001", businessChainId: "CHAIN-PAY-001", paymentOrderId: "PAY-001",
        orderNo: "ORD-001", batchNo: "CLB-001", ruleNo: "RULE-001",
        orderAmount: "88.00", merchantAmount: "8.80", workerAmount: "71.16",
        platformAmount: "7.04", channelFeeAmount: "1.00", subsidyAmount: "0.00",
        clearingStatus: "清分成功", clearingStatusType: "success", createdAt: "2026-09-30 10:00:00"
      }, shareItems: null, feeRules: null } };
    }
  });

  try {
    assert.deepEqual(await orderApi.getDetail("CLO-001"), {
      order: {
        clearingNo: "CLO-001", businessChainId: "CHAIN-PAY-001", paymentOrderId: "PAY-001",
        orderNo: "ORD-001", batchNo: "CLB-001", ruleNo: "RULE-001",
        orderAmount: "88.00", merchantAmount: "8.80", workerAmount: "71.16",
        platformAmount: "7.04", channelFeeAmount: "1.00", subsidyAmount: "0.00",
        clearingStatus: "清分成功", clearingStatusType: "success", createdAt: "2026-09-30 10:00:00"
      },
      shareItems: [],
      feeRules: []
    });
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

test("aborts slow clearing requests and clears the timeout", async () => {
  const originalFetch = globalThis.fetch;
  const originalSetTimeout = globalThis.setTimeout;
  const originalClearTimeout = globalThis.clearTimeout;
  let clearCount = 0;
  globalThis.setTimeout = (callback) => {
    callback();
    return "clearing-timeout";
  };
  globalThis.clearTimeout = (timeoutId) => {
    assert.equal(timeoutId, "clearing-timeout");
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
      batchApi.getDetail("CLR-TIMEOUT"),
      (error) => error.code === "CLEARING-CLIENT-TIMEOUT"
    );
    assert.equal(clearCount, 1);
  } finally {
    globalThis.fetch = originalFetch;
    globalThis.setTimeout = originalSetTimeout;
    globalThis.clearTimeout = originalClearTimeout;
  }
});
