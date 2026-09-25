import assert from "node:assert/strict";
import test from "node:test";
import { riskControlApi } from "./client.js";

test("keeps the risk control service envelope on success", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: { status: "READY", metrics: [], highlights: [] } };
    }
  });

  try {
    assert.deepEqual(await riskControlApi.getSummary(), { status: "READY", metrics: [], highlights: [] });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("normalizes nullable risk control summary collections", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: { metrics: null, highlights: null } };
    }
  });

  try {
    assert.deepEqual(await riskControlApi.getSummary(), { metrics: [], highlights: [] });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("normalizes nullable risk control collections", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: null };
    }
  });

  try {
    assert.deepEqual(await riskControlApi.getPolicies(), {
      records: [],
      total: 0,
      pageNo: 1,
      pageSize: 0
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("preserves risk control pagination metadata for list views", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return {
        code: "0",
        data: {
          records: [{ policyCode: "RISK-001" }],
          total: 3,
          pageNo: 2,
          pageSize: 1
        }
      };
    }
  });

  try {
    assert.deepEqual(await riskControlApi.getPolicies(), {
      records: [{ policyCode: "RISK-001" }],
      total: 3,
      pageNo: 2,
      pageSize: 1
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("normalizes malformed risk control pagination metadata", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    async json() {
      return { code: "0", data: { records: [{ policyCode: "RISK-002" }], total: "invalid", pageNo: 0, pageSize: -1 } };
    }
  });
  try {
    assert.deepEqual(await riskControlApi.getPolicies(), {
      records: [{ policyCode: "RISK-002" }], total: 1, pageNo: 1, pageSize: 1
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports non-JSON risk control responses with a stable client error", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response("upstream unavailable", { status: 502 });

  try {
    await assert.rejects(
      riskControlApi.getSummary(),
      (error) => error.code === "RISK-CLIENT-INVALID-RESPONSE"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports risk control network failures with a stable client error", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => {
    throw new TypeError("fetch failed");
  };

  try {
    await assert.rejects(
      riskControlApi.getSummary(),
      (error) => error.code === "RISK-CLIENT-NETWORK"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});
