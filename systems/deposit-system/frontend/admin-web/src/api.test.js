import assert from "node:assert/strict";
import test from "node:test";

test("keeps the deposit service envelope on success", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response(JSON.stringify({
    code: "0",
    data: { accountNo: "DEP-001" }
  }), { status: 200, headers: { "Content-Type": "application/json" } });
  try {
    const { getAccountDetail } = await import(`./api.js?success=${Date.now()}`);
    assert.deepEqual(await getAccountDetail("DEP-001"), { accountNo: "DEP-001" });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("normalizes nullable V2 deposit collections", async () => {
  const originalFetch = globalThis.fetch;
  let call = 0;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      call += 1;
      return call === 1
        ? { code: "0", data: null }
        : { code: "0", data: { bindings: null, migrations: null } };
    }
  });
  try {
    const { getV2TotalAccounts, getV2TotalAccountDetail } = await import(`./api.js?v2=${Date.now()}`);
    assert.deepEqual(await getV2TotalAccounts(), []);
    assert.deepEqual(await getV2TotalAccountDetail("TA-001"), { bindings: [], migrations: [] });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("normalizes deposit account pages to a stable empty-page shape", async () => {
  const originalFetch = globalThis.fetch;
  let call = 0;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      call += 1;
      return { code: "0", data: call === 1 ? null : ["unexpected-item"] };
    }
  });
  try {
    const { getAccountsByQuery, getFlows } = await import(`./api.js?page-null=${Date.now()}`);
    assert.deepEqual(await getAccountsByQuery({ pageNo: 2, pageSize: 10 }), {
      items: [], total: 0, pageNo: 2, pageSize: 10
    });
    assert.deepEqual(await getFlows("DEP-001", { pageNo: 3, pageSize: 5 }), {
      items: ["unexpected-item"], total: 1, pageNo: 3, pageSize: 5
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("does not leak array indexes or malformed page metadata", async () => {
  const originalFetch = globalThis.fetch;
  let call = 0;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      call += 1;
      return call === 1
        ? { code: "0", data: ["unexpected-item"] }
        : { code: "0", data: { items: "invalid", total: "invalid", pageNo: 0, pageSize: -1 } };
    }
  });
  try {
    const { getAccountsByQuery, getFlows } = await import(`./api.js?page-shape=${Date.now()}`);
    assert.deepEqual(await getAccountsByQuery(), {
      items: ["unexpected-item"], total: 1, pageNo: 1, pageSize: 20
    });
    assert.deepEqual(await getFlows("DEP-001"), {
      items: [], total: 0, pageNo: 1, pageSize: 20
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports non-JSON deposit responses with a stable client error", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response("upstream unavailable", { status: 502 });
  try {
    const { getOverview } = await import(`./api.js?invalid=${Date.now()}`);
    await assert.rejects(
      getOverview(),
      (error) => error.code === "DEPOSIT-CLIENT-INVALID-RESPONSE"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports deposit network failures with a stable client error", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => {
    throw new TypeError("fetch failed");
  };
  try {
    const { getOverview } = await import(`./api.js?network=${Date.now()}`);
    await assert.rejects(
      getOverview(),
      (error) => error.code === "DEPOSIT-CLIENT-NETWORK"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("aborts slow deposit requests and clears the timeout", async () => {
  const originalFetch = globalThis.fetch;
  const originalSetTimeout = globalThis.setTimeout;
  const originalClearTimeout = globalThis.clearTimeout;
  let clearCount = 0;
  globalThis.setTimeout = (callback) => {
    callback();
    return "deposit-timeout";
  };
  globalThis.clearTimeout = (timeoutId) => {
    assert.equal(timeoutId, "deposit-timeout");
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
      (error) => error.code === "DEPOSIT-CLIENT-TIMEOUT"
    );
    assert.equal(clearCount, 1);
  } finally {
    globalThis.fetch = originalFetch;
    globalThis.setTimeout = originalSetTimeout;
    globalThis.clearTimeout = originalClearTimeout;
  }
});
