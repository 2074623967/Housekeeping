import assert from "node:assert/strict";
import test from "node:test";
import { opsConfigApi } from "./client.js";

test("keeps the ops config service envelope on success", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: { status: "READY", metrics: [], highlights: [] } };
    }
  });

  try {
    assert.deepEqual(await opsConfigApi.getSummary(), { status: "READY", metrics: [], highlights: [] });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("normalizes nullable ops config summary collections", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: { metrics: null, highlights: null } };
    }
  });

  try {
    assert.deepEqual(await opsConfigApi.getSummary(), { metrics: [], highlights: [] });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("normalizes nullable ops config collections", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: null };
    }
  });

  try {
    assert.deepEqual(await opsConfigApi.getBusinessLines(), {
      records: [],
      total: 0,
      pageNo: 1,
      pageSize: 0
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("preserves ops config pagination metadata for list views", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return {
        code: "0",
        data: {
          records: [{ configCode: "ROUTE_HOME" }],
          total: 2,
          pageNo: 2,
          pageSize: 1
        }
      };
    }
  });

  try {
    assert.deepEqual(await opsConfigApi.getBusinessLines(), {
      records: [{ configCode: "ROUTE_HOME" }],
      total: 2,
      pageNo: 2,
      pageSize: 1
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("normalizes malformed ops config pagination metadata", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: { records: [{ configCode: "CFG-002" }], total: "invalid", pageNo: 0, pageSize: -1 } };
    }
  });
  try {
    assert.deepEqual(await opsConfigApi.getBusinessLines(), {
      records: [{ configCode: "CFG-002" }], total: 1, pageNo: 1, pageSize: 1
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports non-JSON ops config responses with a stable client error", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response("upstream unavailable", { status: 502 });

  try {
    await assert.rejects(
      opsConfigApi.getSummary(),
      (error) => error.code === "OPS-CONFIG-CLIENT-INVALID-RESPONSE"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports ops config network failures with a stable client error", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => {
    throw new TypeError("fetch failed");
  };

  try {
    await assert.rejects(
      opsConfigApi.getSummary(),
      (error) => error.code === "OPS-CONFIG-CLIENT-NETWORK"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});
