import assert from "node:assert/strict";
import test from "node:test";
import { normalizeWalletActionResult, walletApi } from "./client.js";

test("validates wallet action business keys and rejects a mismatched key", () => {
  assert.deepEqual(
    normalizeWalletActionResult({ rechargeNo: "RCH-001", status: "SUCCESS" }, "rechargeNo"),
    { rechargeNo: "RCH-001", status: "SUCCESS" }
  );
  assert.throws(
    () => normalizeWalletActionResult({ rechargeNo: "" }, "rechargeNo"),
    (error) => error.code === "WALLET-CLIENT-INVALID-BUSINESS-RESULT"
  );
  assert.throws(
    () => normalizeWalletActionResult({ eventNo: "EVENT-002" }, "eventNo", "EVENT-001"),
    (error) => error.code === "WALLET-CLIENT-INVALID-BUSINESS-RESULT"
  );
});

test("keeps the wallet service envelope on success", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: { accountNo: "WAL-001" } };
    }
  });

  try {
    assert.deepEqual(await walletApi.getDetail("WAL-001"), { accountNo: "WAL-001", ledgers: [] });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("binds wallet detail and risk approval responses to requested business keys", async () => {
  const originalFetch = globalThis.fetch;
  const requests = [];
  const responses = [
    { code: "0", data: { account: { accountNo: "WAL/001" }, ledgers: [] } },
    { code: "0", data: { eventNo: "EVENT-001", status: "APPROVED" } }
  ];
  globalThis.fetch = async (url, options) => {
    requests.push({ url, options });
    return { ok: true, async json() { return responses.shift(); } };
  };

  try {
    assert.equal((await walletApi.getDetail("WAL/001")).account.accountNo, "WAL/001");
    assert.equal(
      (await walletApi.approveRiskEvent({ eventNo: "EVENT-001", action: "APPROVE" })).eventNo,
      "EVENT-001"
    );
    assert.equal(requests[0].url, "/api/wallet/accounts/WAL%2F001");
    assert.match(requests[1].options.body, /EVENT-001/);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports non-JSON wallet responses with a stable client error", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response("upstream unavailable", { status: 502 });

  try {
    await assert.rejects(
      walletApi.getAccounts(),
      (error) => error.code === "WALLET-CLIENT-INVALID-RESPONSE"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports wallet network failures with a stable client error", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => {
    throw new TypeError("fetch failed");
  };

  try {
    await assert.rejects(
      walletApi.getAccounts(),
      (error) => error.code === "WALLET-CLIENT-NETWORK"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("normalizes empty and wrapped wallet collections at the API boundary", async () => {
  const originalFetch = globalThis.fetch;
  const responses = [
    { code: "0", data: null },
    { code: "0", data: { records: [{ ledgerNo: "LED-001" }] } },
    { code: "0", data: { items: [{ eventNo: "RISK-001" }] } }
  ];
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return responses.shift();
    }
  });

  try {
    assert.deepEqual(await walletApi.getAccounts(), []);
    assert.deepEqual(await walletApi.getLedgers(), [{ ledgerNo: "LED-001" }]);
    assert.deepEqual(await walletApi.getRiskEvents(), [{ eventNo: "RISK-001" }]);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("normalizes nullable detail and marketing dashboard collections", async () => {
  const originalFetch = globalThis.fetch;
  const responses = [
    { code: "0", data: { account: { accountNo: "WAL-001" }, ledgers: null } },
    { code: "0", data: { redPackets: null, riskEvents: { records: [] }, outLedgers: null } }
  ];
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return responses.shift();
    }
  });

  try {
    assert.deepEqual(await walletApi.getDetail("WAL-001"), {
      account: { accountNo: "WAL-001" },
      ledgers: []
    });
    assert.deepEqual(await walletApi.getMarketingFundDashboard(), {
      redPackets: [],
      riskEvents: [],
      outLedgers: []
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("aborts slow wallet requests and clears the timeout", async () => {
  const originalFetch = globalThis.fetch;
  const originalSetTimeout = globalThis.setTimeout;
  const originalClearTimeout = globalThis.clearTimeout;
  let clearCount = 0;
  globalThis.setTimeout = (callback) => {
    callback();
    return "wallet-timeout";
  };
  globalThis.clearTimeout = (timeoutId) => {
    assert.equal(timeoutId, "wallet-timeout");
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
      walletApi.getAccounts(),
      (error) => error.code === "WALLET-CLIENT-TIMEOUT"
    );
    assert.equal(clearCount, 1);
  } finally {
    globalThis.fetch = originalFetch;
    globalThis.setTimeout = originalSetTimeout;
    globalThis.clearTimeout = originalClearTimeout;
  }
});
