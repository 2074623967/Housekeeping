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
