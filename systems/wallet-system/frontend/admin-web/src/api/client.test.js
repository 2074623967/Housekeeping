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
    assert.deepEqual(await walletApi.getDetail("WAL-001"), { accountNo: "WAL-001" });
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
