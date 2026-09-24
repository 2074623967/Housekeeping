import test from "node:test";
import assert from "node:assert/strict";
import {
  dashboardApi,
  normalizePagePayload,
  normalizeObjectPayload,
  orderApi,
  paymentDayEndApi,
  paymentConfigApi,
  paymentIssueApi,
  paymentMonitorApi,
  paymentTaskCenterApi,
  refundApi
} from "./client.js";

test("normalizes a null paged items field to an empty array", () => {
  const payload = normalizePagePayload({ items: null, total: 0, pageNo: 1, pageSize: 20 });

  assert.deepEqual(payload.items, []);
  assert.equal(payload.total, 0);
});

test("keeps non-paged payloads and existing arrays unchanged", () => {
  const summary = { totalCount: 0 };
  const items = [{ id: "PAY-001" }];

  assert.equal(normalizePagePayload(summary), summary);
  assert.equal(normalizePagePayload({ items }).items, items);
});

test("normalizes nullable object overviews without changing detail null semantics", async (context) => {
  assert.deepEqual(normalizeObjectPayload(null), {});
  assert.deepEqual(normalizeObjectPayload([]), {});
  const overview = { totalCount: 3 };
  assert.equal(normalizeObjectPayload(overview), overview);

  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  assert.deepEqual(await paymentMonitorApi.getOverview(), {});
  assert.deepEqual(await paymentTaskCenterApi.getOverview(), {});
  assert.deepEqual(await paymentDayEndApi.getOverview(), {});
  assert.equal(await dashboardApi.getSummary(), null);
});

test("normalizes a null list response to an empty page", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  assert.deepEqual(await orderApi.getList(), {
    items: [],
    total: 0,
    pageNo: 1,
    pageSize: 20
  });
});

test("preserves null detail responses", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  assert.equal(await dashboardApi.getSummary(), null);
});

test("normalizes nullable payment configuration collections", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  assert.deepEqual(await paymentConfigApi.getChannelReviews(), []);
  assert.deepEqual(await paymentConfigApi.submitChannelStatusReview({}), []);
  assert.deepEqual(await paymentConfigApi.getOverview(), {});
});

test("normalizes a nullable payment issue responsibility summary", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({ code: "0", data: null })
  }));

  assert.deepEqual(await paymentIssueApi.getResponsibilitySummary(), []);
});

test("normalizes nullable refund detail collections", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => ({
      code: "0",
      data: { refundOrderId: "REF-001", attempts: null, operationLogs: null }
    })
  }));

  assert.deepEqual(await refundApi.getDetail("REF-001"), {
    refundOrderId: "REF-001",
    attempts: [],
    operationLogs: []
  });
});

test("reports non-JSON upstream responses with a stable client error", async (context) => {
  context.mock.method(globalThis, "fetch", async () => ({
    ok: false,
    status: 502,
    json: async () => {
      throw new SyntaxError("Unexpected token < in JSON");
    }
  }));

  const { dashboardApi } = await import("./client.js");
  await assert.rejects(
    dashboardApi.getSummary(),
    (error) => {
      assert.equal(error.code, "PAYMENT-CLIENT-INVALID-RESPONSE");
      assert.match(error.message, /无法解析/);
      return true;
    }
  );
});

test("reports network failures with a stable client error", async (context) => {
  context.mock.method(globalThis, "fetch", async () => {
    throw new TypeError("fetch failed");
  });

  const { dashboardApi } = await import("./client.js");
  await assert.rejects(
    dashboardApi.getSummary(),
    (error) => error.code === "PAYMENT-CLIENT-NETWORK"
  );
});

test("maps aborted admin requests to a stable timeout error", async (context) => {
  context.mock.method(globalThis, "fetch", async (_url, options) => {
    assert.ok(options.signal instanceof AbortSignal);
    const error = new Error("request aborted");
    error.name = "AbortError";
    throw error;
  });

  const { dashboardApi } = await import("./client.js");
  await assert.rejects(
    dashboardApi.getSummary(),
    (error) => error.code === "PAYMENT-CLIENT-TIMEOUT"
  );
});
