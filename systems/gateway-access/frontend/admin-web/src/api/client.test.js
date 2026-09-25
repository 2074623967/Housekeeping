import assert from "node:assert/strict";
import test from "node:test";
import { gatewayAccessApi } from "./client.js";

test("keeps the gateway access service envelope on success", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: { status: "READY", metrics: [], highlights: [] } };
    }
  });

  try {
    assert.deepEqual(await gatewayAccessApi.getSummary(), { status: "READY", metrics: [], highlights: [] });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("normalizes nullable gateway access summary collections", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: { metrics: null, highlights: null } };
    }
  });

  try {
    assert.deepEqual(await gatewayAccessApi.getSummary(), { metrics: [], highlights: [] });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("normalizes nullable gateway access collections", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: null };
    }
  });

  try {
    assert.deepEqual(await gatewayAccessApi.getApplications(), {
      records: [],
      total: 0,
      pageNo: 1,
      pageSize: 0
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("preserves gateway access pagination metadata for list views", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return {
        code: "0",
        data: {
          records: [{ appCode: "APP_PAY_CORE" }],
          total: 4,
          pageNo: 2,
          pageSize: 1
        }
      };
    }
  });

  try {
    assert.deepEqual(await gatewayAccessApi.getApplications(), {
      records: [{ appCode: "APP_PAY_CORE" }],
      total: 4,
      pageNo: 2,
      pageSize: 1
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("normalizes malformed gateway access pagination metadata", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: { records: [{ appCode: "APP-002" }], total: "invalid", pageNo: 0, pageSize: -1 } };
    }
  });
  try {
    assert.deepEqual(await gatewayAccessApi.getApplications(), {
      records: [{ appCode: "APP-002" }], total: 1, pageNo: 1, pageSize: 1
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports non-JSON gateway access responses with a stable client error", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response("upstream unavailable", { status: 502 });

  try {
    await assert.rejects(
      gatewayAccessApi.getSummary(),
      (error) => error.code === "GATEWAY-CLIENT-INVALID-RESPONSE"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports gateway access network failures with a stable client error", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => {
    throw new TypeError("fetch failed");
  };

  try {
    await assert.rejects(
      gatewayAccessApi.getSummary(),
      (error) => error.code === "GATEWAY-CLIENT-NETWORK"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});
