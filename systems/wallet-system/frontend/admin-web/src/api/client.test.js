import assert from "node:assert/strict";
import test from "node:test";
import { walletApi } from "./client.js";

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
