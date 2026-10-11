import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import test from "node:test";
import {
  assertDemoBackend, assertDemoPendingSubmit, assertDemoPrepay, assertDemoRefund, assertDemoRepeatedSubmit,
  browserCases, createDemoPrepay, demoRequest, matchesDetailResponse
} from "./payment-terminal-browser-smoke.mjs";

const prepay = {
  orderNo: "LOCAL-ORDER", prepayOrderNo: "PRE-1", paymentOrderId: "PAY-1"
};

test("rejects an unverified backend before creating any demo order", () => {
  assert.doesNotThrow(() => assertDemoBackend({ code: "0", data: "payment-core-h2-demo" }));
  for (const response of [null, { code: "0", data: "payment-core" },
    { code: "error", data: "payment-core-h2-demo" }]) {
    assert.throws(() => assertDemoBackend(response), /拒绝创建演示订单/);
  }
});

test("requires matching local prepay identifiers", () => {
  assert.deepEqual(assertDemoPrepay({ data: prepay }, prepay.orderNo), prepay);
  assert.throws(() => assertDemoPrepay({ data: { ...prepay, orderNo: "OTHER" } }, prepay.orderNo));
  assert.throws(() => assertDemoPrepay({ data: { ...prepay, paymentOrderId: "" } }, prepay.orderNo));
});

test("requires a bound open channel attempt before pending browser checks", () => {
  const pending = {
    paymentOrderId: prepay.paymentOrderId, prepayOrderNo: prepay.prepayOrderNo,
    paymentStatus: "WAIT_CALLBACK", paymentMethod: "支付宝",
    channel: "alipay_h5", channelTransactionNo: "ALI-1",
    latestAttemptStatus: "处理中", latestAttemptStatusType: "info", latestAttemptCompletedAt: null
  };
  assert.deepEqual(assertDemoPendingSubmit(prepay, pending), pending);
  for (const invalid of [
    { ...pending, paymentOrderId: "PAY-OTHER" },
    { ...pending, paymentStatus: "SUCCESS" },
    { ...pending, paymentMethod: "微信支付" },
    { ...pending, channel: null },
    { ...pending, channelTransactionNo: null },
    { ...pending, latestAttemptStatus: "成功" },
    { ...pending, latestAttemptStatusType: "success" },
    { ...pending, latestAttemptCompletedAt: "2026-10-09 10:00:00" }
  ]) {
    assert.throws(() => assertDemoPendingSubmit(prepay, invalid), /待回调受理事实/);
  }
});

test("requires repeated submit to reuse complete successful payment facts", () => {
  const paid = {
    paymentOrderId: "PAY-1", status: "SUCCESS", channelTransactionNo: "WX-1",
    latestAttemptCompletedAt: "2026-10-02 10:00:00", successAt: "2026-10-02 10:00:00"
  };
  const repeated = {
    paymentOrderId: "PAY-1", paymentStatus: "SUCCESS", channelTransactionNo: "WX-1",
    latestAttemptStatus: "成功", latestAttemptStatusType: "success",
    latestAttemptCompletedAt: paid.latestAttemptCompletedAt, successAt: paid.successAt
  };
  assert.deepEqual(assertDemoRepeatedSubmit(prepay, paid, repeated), repeated);
  for (const invalid of [
    { ...repeated, paymentOrderId: "PAY-OTHER" },
    { ...repeated, paymentStatus: "WAIT_CALLBACK" },
    { ...repeated, channelTransactionNo: "WX-OTHER" },
    { ...repeated, latestAttemptStatus: "待回调" },
    { ...repeated, latestAttemptCompletedAt: "2026-10-02 10:00:01" },
    { ...repeated, successAt: null }
  ]) {
    assert.throws(() => assertDemoRepeatedSubmit(prepay, paid, invalid), /未复用匹配的支付成功事实/);
  }
});

test("demo writes refuse redirects before sending to another destination", async () => {
  const originalFetch = globalThis.fetch;
  try {
    globalThis.fetch = async (url, options) => {
      assert.equal(options.method, "POST");
      assert.equal(options.redirect, "error");
      if (url.endsWith("/api/payments/prepay")) {
        const orderNo = JSON.parse(options.body).orderNo;
        return { ok: true, json: async () => ({ data: {
          orderNo, prepayOrderNo: "PRE-1", paymentOrderId: "PAY-1"
        } }) };
      }
      return { ok: true, json: async () => ({ data: { paymentOrderId: "PAY-1" } }) };
    };
    assert.equal((await createDemoPrepay()).paymentOrderId, "PAY-1");
    assert.equal((await demoRequest("/api/payments/submit", {})).paymentOrderId, "PAY-1");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("covers cashier and result reads on all three terminal origins", () => {
  const pages = browserCases(prepay);
  assert.equal(pages.length, 6);
  assert.deepEqual(pages.map((page) => page.queryLabel), [
    "主动查单", "查询最新状态", "主动查单", "刷新支付结果", "主动查单", "刷新桌面端支付结果"
  ]);
  assert.deepEqual(pages.map((page) => page.port), [5175, 5175, 5176, 5176, 5177, 5177]);
  assert.deepEqual(pages.map((page) => page.apiPath), [
    "/api/payments/cashier/PRE-1", "/api/payments/PAY-1",
    "/api/payments/cashier/PRE-1", "/api/payments/PAY-1",
    "/api/payments/cashier/PRE-1", "/api/payments/PAY-1"
  ]);
});

test("adds only same-source processing refund reads with a matching payment", () => {
  const refund = { refundOrderId: "REF-1", paymentOrderId: "PAY-1", status: "PROCESSING" };
  assert.deepEqual(assertDemoRefund({ data: refund }, prepay), refund);
  assert.throws(() => assertDemoRefund({ data: { ...refund, paymentOrderId: "PAY-OTHER" } }, prepay));
  assert.throws(() => assertDemoRefund({ data: { ...refund, status: "REVIEWING" } }, prepay));
  const completed = { ...refund, status: "SUCCESS" };
  assert.deepEqual(assertDemoRefund({ data: completed }, prepay, "SUCCESS"), completed);
  assert.throws(() => assertDemoRefund({ data: refund }, prepay, "SUCCESS"));
  const failed = { ...refund, status: "FAIL" };
  assert.deepEqual(assertDemoRefund({ data: failed }, prepay, "FAIL"), failed);
  assert.throws(() => assertDemoRefund({ data: refund }, prepay, "FAIL"));
  const pages = browserCases(prepay, refund);
  assert.equal(pages.length, 9);
  assert.deepEqual(pages.filter((page) => page.page === "refund-progress").map((page) => ({
    port: page.port, apiPath: page.apiPath, amount: page.amount, status: page.status
  })), [5175, 5176, 5177].map((port) => ({
    port, apiPath: "/api/refunds/REF-1", amount: "¥18.00", status: "PROCESSING"
  })));
  assert.deepEqual(browserCases(prepay, completed).filter((page) => page.page === "refund-progress")
    .map((page) => page.status), ["SUCCESS", "SUCCESS", "SUCCESS"]);
  assert.deepEqual(browserCases(prepay, failed).filter((page) => page.page === "refund-progress")
    .map((page) => page.status), ["FAIL", "FAIL", "FAIL"]);
});

test("does not accept a stale response from another terminal or a write request", () => {
  const response = {
    url: "http://localhost:5176/api/payments/PAY-1?terminal=H5_WEB", status: 200, method: "GET"
  };
  assert.equal(matchesDetailResponse(response, 5176, "/api/payments/PAY-1"), true);
  assert.equal(matchesDetailResponse(response, 5177, "/api/payments/PAY-1"), false);
  assert.equal(matchesDetailResponse({ ...response, method: "POST" }, 5176, "/api/payments/PAY-1"), false);
  assert.equal(matchesDetailResponse({ ...response, status: 500 }, 5176, "/api/payments/PAY-1"), false);
});

test("requires explicit demo intent before creating a prepay order", () => {
  const script = fileURLToPath(new URL("./payment-terminal-browser-smoke.mjs", import.meta.url));
  for (const args of [[], ["--refund-demo"], ["--pending-demo"]]) {
    const result = spawnSync(process.execPath, [script, ...args], { encoding: "utf8" });
    assert.equal(result.status, 1);
    assert.match(result.stderr, /显式传入 --demo/);
  }
  for (const terminalFlag of ["--refund-success-demo", "--refund-fail-demo"]) {
    const incomplete = spawnSync(process.execPath, [script, "--demo", terminalFlag],
      { encoding: "utf8" });
    assert.equal(incomplete.status, 1);
    assert.match(incomplete.stderr, /需要先显式传入 --refund-demo/);
  }
  const conflicting = spawnSync(process.execPath,
    [script, "--demo", "--refund-demo", "--refund-success-demo", "--refund-fail-demo"],
    { encoding: "utf8" });
  assert.equal(conflicting.status, 1);
  assert.match(conflicting.stderr, /不能同时选用/);
  const pendingRefund = spawnSync(process.execPath,
    [script, "--demo", "--pending-demo", "--refund-demo"], { encoding: "utf8" });
  assert.equal(pendingRefund.status, 1);
  assert.match(pendingRefund.stderr, /不能同时选用/);
});
