import assert from "node:assert/strict";
import test from "node:test";

test("encodes wallet account detail identifiers as one path segment", async () => {
  const originalFetch = globalThis.fetch;
  const urls = [];
  globalThis.fetch = async (url) => {
    urls.push(url);
    return new Response(JSON.stringify({ code: "0", data: { walletAccountNo: "WAL/001?#" } }), {
      status: 200,
      headers: { "Content-Type": "application/json" }
    });
  };
  try {
    const { fetchWalletAccountDetail } = await import(`./api.js?path=${Date.now()}`);
    await fetchWalletAccountDetail("WAL/001?#");
    assert.equal(urls[0], "/api/wallet/accounts/WAL%2F001%3F%23");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("normalizes null wallet account page records", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response(JSON.stringify({
    code: "0",
    data: { records: null, total: 0, pageNo: 1, pageSize: 20 }
  }), { status: 200, headers: { "Content-Type": "application/json" } });
  try {
    const { fetchWalletAccounts } = await import(`./api.js?empty-page=${Date.now()}`);
    const result = await fetchWalletAccounts({});
    assert.deepEqual(result.records, []);
    assert.equal(result.total, 0);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports non-JSON wallet account responses with a stable client error", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response("upstream unavailable", { status: 502 });
  try {
    const { fetchWalletAccounts } = await import(`./api.js?invalid=${Date.now()}`);
    await assert.rejects(
      fetchWalletAccounts({}),
      (error) => error.code === "WALLET-ACCOUNT-CLIENT-INVALID-RESPONSE"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports wallet account network failures with a stable client error", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => {
    throw new TypeError("fetch failed");
  };
  try {
    const { fetchWalletAccounts } = await import(`./api.js?network=${Date.now()}`);
    await assert.rejects(
      fetchWalletAccounts({}),
      (error) => error.code === "WALLET-ACCOUNT-CLIENT-NETWORK"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});
