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

test("sends wallet account type and external reference filters", async () => {
  const originalFetch = globalThis.fetch;
  let requestedUrl;
  globalThis.fetch = async (url) => {
    requestedUrl = url;
    return new Response(JSON.stringify({
      code: "0",
      data: { records: [], total: 0, pageNo: 1, pageSize: 20 }
    }), { status: 200, headers: { "Content-Type": "application/json" } });
  };
  try {
    const { fetchWalletAccounts } = await import(`./api.js?filters=${Date.now()}`);
    await fetchWalletAccounts({
      accountType: "MAIN",
      extRefNo: "USER-001",
      pageNo: 2,
      pageSize: 50
    });
    const query = new URL(requestedUrl, "http://wallet-account.test").searchParams;
    assert.equal(query.get("accountType"), "MAIN");
    assert.equal(query.get("extRefNo"), "USER-001");
    assert.equal(query.get("pageNo"), "2");
    assert.equal(query.get("pageSize"), "50");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("normalizes null wallet account page responses without changing detail semantics", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response(JSON.stringify({
    code: "0",
    data: null
  }), { status: 200, headers: { "Content-Type": "application/json" } });
  try {
    const {
      fetchWalletAccounts,
      fetchWalletAccountDetail,
      fetchWalletFlows,
      fetchWalletFlowExportTasks
    } = await import(`./api.js?null-page=${Date.now()}`);
    assert.deepEqual(await fetchWalletAccounts({}), {
      records: [],
      total: 0,
      pageNo: 1,
      pageSize: 20
    });
    assert.deepEqual(await fetchWalletFlows({}), {
      records: [],
      total: 0,
      pageNo: 1,
      pageSize: 20
    });
    assert.deepEqual(await fetchWalletFlowExportTasks({}), {
      records: [],
      total: 0,
      pageNo: 1,
      pageSize: 10
    });
    assert.equal(await fetchWalletAccountDetail("WAL-404"), null);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("normalizes nullable wallet detail collections and batch balances", async () => {
  const originalFetch = globalThis.fetch;
  let call = 0;
  globalThis.fetch = async () => new Response(JSON.stringify({
    code: "0",
    data: call++ === 0
      ? { account: { walletAccountNo: "WAL-001" }, recentFlows: null, statusLogs: null }
      : null
  }), { status: 200, headers: { "Content-Type": "application/json" } });
  try {
    const { fetchWalletAccountDetail, fetchWalletBalances } = await import(`./api.js?detail-empty=${Date.now()}`);
    assert.deepEqual(await fetchWalletAccountDetail("WAL-001"), {
      account: { walletAccountNo: "WAL-001" },
      recentFlows: [],
      statusLogs: []
    });
    assert.deepEqual(await fetchWalletBalances(["WAL-001"]), []);
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
