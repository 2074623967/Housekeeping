import assert from "node:assert/strict";
import test from "node:test";
import {
  accountApi,
  eventApi,
  ledgerApi,
  normalizeAccountingAccountDetailResult,
  normalizeAccountingEventResult,
  normalizeAccountingResult,
  normalizePagePayload,
  subjectApi
} from "./client.js";

test("requires accounting business keys on detail and mutation responses", () => {
  assert.equal(normalizeAccountingResult({ subjectId: "SUB-001" }, "subjectId", "SUB-001").subjectId, "SUB-001");
  assert.equal(normalizeAccountingResult({ accountNo: "ACC-001" }, "accountNo", "ACC-001").accountNo, "ACC-001");
  assert.equal(normalizeAccountingResult({ freezeNo: "FRZ-001" }, "freezeNo").freezeNo, "FRZ-001");
  assert.equal(normalizeAccountingResult({ adjustNo: "ADJ-001" }, "adjustNo", "ADJ-001").adjustNo, "ADJ-001");
  assert.equal(normalizeAccountingAccountDetailResult({ account: { accountNo: "ACC-001" } }, "ACC-001").account.accountNo, "ACC-001");
  for (const invalidResult of [
    () => normalizeAccountingResult({ subjectId: "SUB-002" }, "subjectId", "SUB-001"),
    () => normalizeAccountingResult({ accountNo: "ACC-002" }, "accountNo", "ACC-001"),
    () => normalizeAccountingResult({ freezeNo: "FRZ-002" }, "freezeNo", "FRZ-001"),
    () => normalizeAccountingResult({ adjustNo: "ADJ-002" }, "adjustNo", "ADJ-001"),
    () => normalizeAccountingAccountDetailResult({ account: { accountNo: "ACC-002" } }, "ACC-001")
  ]) {
    assert.throws(invalidResult, (error) => error.code === "ACCOUNTING-CLIENT-INVALID-BUSINESS-RESULT");
  }
});

test("requires accounting event identity and consumption facts", () => {
  const validEvent = {
    businessChainId: "CHAIN-PAY-001",
    eventNo: "EVT-001",
    eventType: "PAYMENT_SUCCESS",
    bizNo: "PAY-001",
    eventStatus: "已消费",
    statusType: "success",
    createdAt: "2026-09-30 10:00:00"
  };
  assert.equal(
    normalizeAccountingEventResult(validEvent, "PAYMENT_SUCCESS", "PAY-001").eventNo,
    "EVT-001"
  );
  for (const invalidEvent of [
    { ...validEvent, eventNo: "" },
    { ...validEvent, eventType: "CLEARING_GENERATED" },
    { ...validEvent, bizNo: "PAY-002" },
    { ...validEvent, eventStatus: "" },
    { ...validEvent, statusType: undefined },
    { ...validEvent, createdAt: undefined }
  ]) {
    assert.throws(
      () => normalizeAccountingEventResult(invalidEvent, "PAYMENT_SUCCESS", "PAY-001"),
      (error) => error.code === "ACCOUNTING-CLIENT-INVALID-BUSINESS-RESULT"
    );
  }
});

test("keeps service-to-service accounting event consumption out of the browser client", () => {
  assert.deepEqual(Object.keys(eventApi), ["getList"]);
});

test("normalizes accounting event pages only when event facts are consistent", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return {
        code: "0",
        data: {
          items: [{
            businessChainId: "CHAIN-PAY-001",
            eventNo: "EVT-001",
            eventType: "PAYMENT_SUCCESS",
            bizNo: "PAY-001",
            eventStatus: "已消费",
            statusType: "success",
            createdAt: "2026-09-30 10:00:00"
          }],
          total: 1,
          pageNo: 1,
          pageSize: 20
        }
      };
    }
  });

  try {
    assert.equal((await eventApi.getList()).items[0].businessChainId, "CHAIN-PAY-001");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("rejects accounting event pages with missing chain, invalid time, or mismatched status style", async () => {
  const originalFetch = globalThis.fetch;
  const invalidEvent = {
    businessChainId: "CHAIN-PAY-001",
    eventNo: "EVT-001",
    eventType: "PAYMENT_SUCCESS",
    bizNo: "PAY-001",
    eventStatus: "已消费",
    statusType: "success",
    createdAt: "2026-09-30 10:00:00"
  };
  for (const mutation of [
    (event) => ({ ...event, businessChainId: "" }),
    (event) => ({ ...event, createdAt: "not-a-time" }),
    (event) => ({ ...event, statusType: "info" })
  ]) {
    globalThis.fetch = async () => ({
      ok: true,
      async json() {
        return { code: "0", data: { items: [mutation(invalidEvent)], total: 1, pageNo: 1, pageSize: 20 } };
      }
    });
    await assert.rejects(
      eventApi.getList(),
      (error) => error.code === "ACCOUNTING-CLIENT-INVALID-BUSINESS-RESULT"
    );
  }
  globalThis.fetch = originalFetch;
});

test("normalizes null accounting page items without changing summaries", () => {
  assert.deepEqual(normalizePagePayload({ items: null, total: 0 }).items, []);
  const summary = { postedCount: 2 };
  assert.equal(normalizePagePayload(summary), summary);
});

test("normalizes a null accounting page response for list consumers", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: null };
    }
  });

  try {
    assert.deepEqual(await ledgerApi.getList(), {
      items: [],
      total: 0,
      pageNo: 1,
      pageSize: 20
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("normalizes malformed accounting pagination metadata", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: { items: [{
        ledgerNo: "LDG-002", accountNo: "ACC-001", bizType: "PAYMENT_SUCCESS_EVENT", bizNo: "PAY-001",
        payScene: "HOME_CLEAN",
        direction: "贷方", amount: "18.00", beforeBalance: "0.00", afterBalance: "18.00",
        ledgerStatus: "已记账", statusType: "success"
      }], total: "invalid", pageNo: 0, pageSize: -1 } };
    }
  });
  try {
    assert.deepEqual(await ledgerApi.getList(), {
      items: [{
        ledgerNo: "LDG-002", accountNo: "ACC-001", bizType: "PAYMENT_SUCCESS_EVENT", bizNo: "PAY-001",
        payScene: "HOME_CLEAN",
        direction: "贷方", amount: "18.00", beforeBalance: "0.00", afterBalance: "18.00",
        ledgerStatus: "已记账", statusType: "success"
      }], total: 1, pageNo: 1, pageSize: 20
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("normalizes null accounting detail ledgers", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: { account: { accountNo: "ACC-001" }, recentLedgers: null } };
    }
  });

  try {
    assert.deepEqual(await accountApi.getDetail("ACC-001"), {
      account: { accountNo: "ACC-001" },
      recentLedgers: []
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("keeps the accounting service envelope on success", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (url) => ({
    ok: true,
    url,
    async json() {
      return { code: "0", data: { postedCount: 2 } };
    }
  });

  try {
    assert.deepEqual(await subjectApi.getList(), { postedCount: 2 });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports non-JSON accounting responses with a stable client error", async () => {
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
      subjectApi.getList(),
      (error) => error.code === "ACCOUNTING-CLIENT-INVALID-RESPONSE"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports accounting network failures with a stable client error", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => {
    throw new TypeError("fetch failed");
  };

  try {
    await assert.rejects(
      subjectApi.getList(),
      (error) => error.code === "ACCOUNTING-CLIENT-NETWORK"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("passes ledger filters and pagination to the accounting API", async () => {
  const originalFetch = globalThis.fetch;
  let requestedUrl = "";
  globalThis.fetch = async (url) => {
    requestedUrl = String(url);
    return {
      ok: true,
      async json() {
        return { code: "0", data: { items: [], total: 41, pageNo: 3, pageSize: 20 } };
      }
    };
  };

  try {
    const result = await ledgerApi.getList({ accountNo: "ACC-1", bizNo: "PAY-1", bizType: "PAYMENT", pageNo: 3, pageSize: 20 });
    const query = new URL(requestedUrl, "http://localhost").searchParams;
    assert.equal(query.get("accountNo"), "ACC-1");
    assert.equal(query.get("bizNo"), "PAY-1");
    assert.equal(query.get("bizType"), "PAYMENT");
    assert.equal(query.get("pageNo"), "3");
    assert.equal(query.get("pageSize"), "20");
    assert.deepEqual(result, { items: [], total: 41, pageNo: 3, pageSize: 20 });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("rejects accounting ledger rows with inconsistent balance movement", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: { items: [{
        ledgerNo: "LDG-001", accountNo: "ACC-001", bizType: "PAYMENT_SUCCESS_EVENT", bizNo: "PAY-001",
        direction: "贷方", amount: "18.00", beforeBalance: "0.00", afterBalance: "17.99",
        ledgerStatus: "已记账", statusType: "success"
      }], total: 1, pageNo: 1, pageSize: 20 } };
    }
  });
  try {
    await assert.rejects(
      ledgerApi.getList(),
      (error) => error.code === "ACCOUNTING-CLIENT-INVALID-BUSINESS-RESULT"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("rejects accounting ledger rows with malformed payment scenes", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: { items: [{
        ledgerNo: "LDG-001", accountNo: "ACC-001", bizType: "PAYMENT_SUCCESS_EVENT", bizNo: "PAY-001",
        payScene: 123, direction: "贷方", amount: "18.00", beforeBalance: "0.00", afterBalance: "18.00",
        ledgerStatus: "已记账", statusType: "success"
      }], total: 1, pageNo: 1, pageSize: 20 } };
    }
  });
  try {
    await assert.rejects(
      ledgerApi.getList(),
      (error) => error.code === "ACCOUNTING-CLIENT-INVALID-BUSINESS-RESULT"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("aborts slow accounting requests and clears the timeout", async () => {
  const originalFetch = globalThis.fetch;
  const originalSetTimeout = globalThis.setTimeout;
  const originalClearTimeout = globalThis.clearTimeout;
  let clearCount = 0;
  globalThis.setTimeout = (callback) => {
    callback();
    return "accounting-timeout";
  };
  globalThis.clearTimeout = (timeoutId) => {
    assert.equal(timeoutId, "accounting-timeout");
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
      subjectApi.getList(),
      (error) => error.code === "ACCOUNTING-CLIENT-TIMEOUT"
    );
    assert.equal(clearCount, 1);
  } finally {
    globalThis.fetch = originalFetch;
    globalThis.setTimeout = originalSetTimeout;
    globalThis.clearTimeout = originalClearTimeout;
  }
});

test("aborts slow accounting exports and clears the timeout", async () => {
  const originalFetch = globalThis.fetch;
  const originalSetTimeout = globalThis.setTimeout;
  const originalClearTimeout = globalThis.clearTimeout;
  let clearCount = 0;
  globalThis.setTimeout = (callback) => {
    callback();
    return "accounting-export-timeout";
  };
  globalThis.clearTimeout = (timeoutId) => {
    assert.equal(timeoutId, "accounting-export-timeout");
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
      accountApi.downloadDetail("ACC-TIMEOUT"),
      (error) => error.code === "ACCOUNTING-CLIENT-TIMEOUT"
    );
    assert.equal(clearCount, 1);
  } finally {
    globalThis.fetch = originalFetch;
    globalThis.setTimeout = originalSetTimeout;
    globalThis.clearTimeout = originalClearTimeout;
  }
});
