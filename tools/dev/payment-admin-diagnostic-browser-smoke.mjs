#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { existsSync } from "node:fs";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const backendUrl = "http://localhost:18081";
const adminUrl = "http://localhost:5174";
const chromeBinary = process.env.HSP_CHROME_BIN
  || "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const anomalousPaymentOrderId = "PAY202607190001";
const normalPaymentOrderId = "PAY202607190002";
const refundTriagePaymentOrderId = "PAY202607180074";
const refundTriageOrderId = "REF202607180019";
const verifyRefundMissingTime = process.argv.includes("--refund-missing-time");
const verifyRefundCumulative = process.argv.includes("--refund-cumulative");
const verifyRefundInvalidAmount = process.argv.includes("--refund-invalid-amount");
const refundTriageOrderIds = verifyRefundCumulative || verifyRefundInvalidAmount
  ? [refundTriageOrderId, "REF202607180020"] : [refundTriageOrderId];
const verifyRefundTriage = process.argv.includes("--refund-triage")
  || verifyRefundMissingTime || verifyRefundCumulative || verifyRefundInvalidAmount;
const verifyMissingSuccessTime = process.argv.includes("--success-time-missing");
const verifyPaymentInvalidAmount = process.argv.includes("--payment-invalid-amount");
const verifyCashierPaying = process.argv.includes("--cashier-paying");
const verifyCashierLifecycle = process.argv.includes("--cashier-lifecycle");
const verifyCashierSuccessFact = process.argv.includes("--cashier-success-fact");
const verifyMonitorSourceDrift = process.argv.includes("--monitor-source-drift");
const verifyMonitorAdvice = process.argv.includes("--monitor-advice") || verifyMonitorSourceDrift;
const verifyResultRefreshFailure = process.argv.includes("--result-refresh-failure");
const verifyResultAutoRefreshFailure = process.argv.includes("--result-auto-refresh-failure");
const verifyRefundProgressRefreshFailure = process.argv.includes("--refund-progress-refresh-failure");
const cashierSessions = verifyCashierLifecycle ? [
  { sessionNo: "PRE202607190002", paymentOrderId: normalPaymentOrderId, status: "已关闭", type: "danger" },
  { sessionNo: "PRE202607190001", paymentOrderId: anomalousPaymentOrderId, status: "待风控复核", type: "warn" },
  { sessionNo: "PRE202607180118", paymentOrderId: refundTriagePaymentOrderId, status: "结果待确认", type: "danger" }
] : verifyCashierSuccessFact ? [
  { sessionNo: "PRE202607190001", paymentOrderId: anomalousPaymentOrderId, status: "成功事实异常", type: "danger" },
  { sessionNo: "PRE202607180118", paymentOrderId: refundTriagePaymentOrderId, status: "已完成", type: "success" }
] : [
  { sessionNo: "PRE202607190002", paymentOrderId: normalPaymentOrderId, status: "支付中", type: "info" }
];
const invalidPaymentAmounts = new Map([
  [anomalousPaymentOrderId, "¥0.00"],
  [refundTriagePaymentOrderId, "¥-5.00"]
]);

const delay = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));

async function waitFor(condition, label, describeFailure, maxAttempts = 100) {
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    if (await condition()) return;
    await delay(100);
  }
  throw new Error(`等待 ${label} 超时${describeFailure ? `：${await describeFailure()}` : ""}`);
}

async function verifyDemoFixture() {
  const identity = await fetch(`${backendUrl}/api/demo/identity`, { redirect: "error" });
  assert.equal(identity.status, 200);
  assert.equal((await identity.json()).data, "payment-core-h2-demo");

  if (verifyCashierPaying || verifyCashierLifecycle || verifyCashierSuccessFact) {
    for (const { sessionNo, paymentOrderId, status, type } of cashierSessions) {
      for (const endpoint of [
        `cashier-sessions?sessionNo=${sessionNo}&sessionStatus=${encodeURIComponent(status)}`,
        `cashier-sessions/${sessionNo}`
      ]) {
        const response = await fetch(`${backendUrl}/api/${endpoint}`, { redirect: "error" });
        assert.equal(response.status, 200);
        const payload = await response.json();
        assert.equal(payload.code, "0");
        const session = endpoint.includes("?")
          ? payload.data.items.find((item) => item.sessionNo === sessionNo) : payload.data;
        assert.equal(session?.sessionNo, sessionNo);
        assert.equal(session.paymentOrderId, paymentOrderId);
        assert.equal(session.sessionStatus, status);
        assert.equal(session.sessionStatusType, type);
      }
    }
    if (verifyCashierSuccessFact) {
      for (const [sessionNo, expectedSuccess, expectedOther] of [
        ["PRE202607190001", 0, 1], ["PRE202607180118", 1, 0]
      ]) {
        const response = await fetch(`${backendUrl}/api/cashier-sessions/overview?sessionNo=${sessionNo}`,
          { redirect: "error" });
        assert.equal(response.status, 200);
        const payload = await response.json();
        assert.equal(payload.code, "0");
        assert.equal(payload.data.successSessionCount, expectedSuccess);
        assert.equal(payload.data.otherSessionCount, expectedOther);
      }
    }
    return;
  }

  const response = await fetch(`${backendUrl}/api/payments?paymentOrderId=PAY20260719000`,
    { redirect: "error" });
  assert.equal(response.status, 200);
  const payload = await response.json();
  assert.equal(payload.code, "0");
  const anomalous = payload.data.items.find((item) => item.paymentOrderId === anomalousPaymentOrderId);
  const normal = payload.data.items.find((item) => item.paymentOrderId === normalPaymentOrderId);
  assert.equal(anomalous?.successFactIssue, "SUCCESS_FACT_MISMATCH");
  assert.equal(normal?.successFactIssue, null);
  if (verifyResultRefreshFailure) {
    const trusted = await fetch(`${backendUrl}/api/payments/${refundTriagePaymentOrderId}`,
      { redirect: "error" });
    assert.equal(trusted.status, 200);
    const trustedPayload = await trusted.json();
    assert.equal(trustedPayload.code, "0");
    assert.equal(trustedPayload.data.paymentOrderId, refundTriagePaymentOrderId);
    assert.equal(trustedPayload.data.status, "SUCCESS");
  }
  if (verifyResultAutoRefreshFailure) {
    const pending = await fetch(`${backendUrl}/api/payments/${normalPaymentOrderId}`,
      { redirect: "error" });
    assert.equal(pending.status, 200);
    const pendingPayload = await pending.json();
    assert.equal(pendingPayload.code, "0");
    assert.equal(pendingPayload.data.paymentOrderId, normalPaymentOrderId);
    assert.equal(pendingPayload.data.payScene, "H5");
    assert.equal(pendingPayload.data.status, "WAIT_CALLBACK");
  }
  if (verifyRefundProgressRefreshFailure) {
    const processing = await fetch(`${backendUrl}/api/refunds/REF202607190001`,
      { redirect: "error" });
    assert.equal(processing.status, 200);
    const processingPayload = await processing.json();
    assert.equal(processingPayload.code, "0");
    assert.equal(processingPayload.data.refundOrderId, "REF202607190001");
    assert.equal(processingPayload.data.payScene, "H5");
    assert.equal(processingPayload.data.status, "PROCESSING");
    const succeeded = await fetch(`${backendUrl}/api/refunds/${refundTriageOrderId}`,
      { redirect: "error" });
    assert.equal(succeeded.status, 200);
    const succeededPayload = await succeeded.json();
    assert.equal(succeededPayload.code, "0");
    assert.equal(succeededPayload.data.payScene, "PC");
    assert.equal(succeededPayload.data.status, "SUCCESS");
  }
  if (verifyMonitorAdvice) {
    const monitorResponse = await fetch(`${backendUrl}/api/payment-monitor/overview`, { redirect: "error" });
    assert.equal(monitorResponse.status, 200);
    const monitor = (await monitorResponse.json()).data;
    const alert = monitor.alerts.find((item) => item.alertType === "PAYMENT_SUCCESS_FACT_MISMATCH");
    assert.ok(alert, "演示数据缺少成功事实异常 P1 告警");
    assert.equal(alert.alertLevel, "P1");
    assert.equal(alert.actionRoute, "/payments?status=SUCCESS_FACT_MISMATCH");
    assert.match(alert.suggestedAction, /预付单、订单来源、最新尝试、渠道流水和操作日志，不得自动补标/);
    if (verifyMonitorSourceDrift) {
      assert.equal(alert.affectedCount, 2);
      const drifted = await fetch(`${backendUrl}/api/payments?status=SUCCESS_FACT_MISMATCH&paymentOrderId=${refundTriagePaymentOrderId}`,
        { redirect: "error" });
      assert.equal(drifted.status, 200);
      const driftedPage = (await drifted.json()).data;
      assert.deepEqual(driftedPage.items.map((item) => item.paymentOrderId), [refundTriagePaymentOrderId]);
      assert.equal(driftedPage.items[0].successFactIssue, "SUCCESS_FACT_MISMATCH");
      const detail = await fetch(`${backendUrl}/api/payments/${refundTriagePaymentOrderId}`,
        { redirect: "error" });
      assert.equal(detail.status, 200);
      const detailPayload = await detail.json();
      assert.equal(detailPayload.code, "PAYMENT-500");
      assert.equal(detailPayload.data, null);
      const cashier = await fetch(`${backendUrl}/api/cashier-sessions/PRE202607180118`,
        { redirect: "error" });
      assert.equal(cashier.status, 200);
      const cashierDetail = (await cashier.json()).data;
      assert.equal(cashierDetail.sessionStatus, "成功事实异常");
      assert.equal(cashierDetail.sessionStatusType, "danger");
      const metrics = await fetch(`${backendUrl}/api/payment-metrics/summary`, { redirect: "error" });
      assert.equal(metrics.status, 200);
      const summary = (await metrics.json()).data;
      assert.equal(summary.successCount, 0);
      assert.equal(summary.successAmount, "¥0.00");
    }
  }
  if (verifyPaymentInvalidAmount) {
    for (const [paymentOrderId, amount] of invalidPaymentAmounts) {
      const filtered = await fetch(`${backendUrl}/api/payments?status=SUCCESS_FACT_MISMATCH&paymentOrderId=${paymentOrderId}`,
        { redirect: "error" });
      assert.equal(filtered.status, 200);
      const page = (await filtered.json()).data;
      assert.deepEqual(page.items.map((item) => item.paymentOrderId), [paymentOrderId]);
      assert.equal(page.items[0].amount, amount);
      assert.equal(page.items[0].successFactIssue, "SUCCESS_FACT_MISMATCH");
      const detail = await fetch(`${backendUrl}/api/payments/${paymentOrderId}`, { redirect: "error" });
      assert.equal(detail.status, 200);
      const detailPayload = await detail.json();
      assert.equal(detailPayload.code, "PAYMENT-500");
      assert.equal(detailPayload.data, null);
    }
    for (const endpoint of ["payment-metrics/summary", "payment-monitor/overview"]) {
      const metrics = await fetch(`${backendUrl}/api/${endpoint}`, { redirect: "error" });
      assert.equal(metrics.status, 200);
      const data = (await metrics.json()).data;
      const summary = endpoint.startsWith("payment-monitor") ? data.summary : data;
      assert.equal(summary.successCount, 0);
      assert.equal(summary.successAmount, "¥0.00");
    }
  }
  if (verifyMissingSuccessTime) {
    const missingResponse = await fetch(`${backendUrl}/api/payments?status=SUCCESS_TIME_MISSING&paymentOrderId=${refundTriagePaymentOrderId}`,
      { redirect: "error" });
    assert.equal(missingResponse.status, 200);
    const missingPayload = await missingResponse.json();
    assert.deepEqual(missingPayload.data.items.map((item) => item.paymentOrderId), [refundTriagePaymentOrderId]);
    assert.equal(missingPayload.data.items[0].successFactIssue, "SUCCESS_TIME_MISSING");
  }
  if (verifyRefundTriage) {
    const recordResponse = await fetch(`${backendUrl}/api/payment-records/${refundTriagePaymentOrderId}`,
      { redirect: "error" });
    assert.equal(recordResponse.status, 200);
    const record = (await recordResponse.json()).data;
    assert.equal(record.refundCount, 0);
    assert.equal(record.unverifiedRefundCount, refundTriageOrderIds.length);
    assert.equal(record.refundedAmount, "¥0.00");
    const unverifiedResponse = await fetch(`${backendUrl}/api/refunds?paymentOrderId=${refundTriagePaymentOrderId}&refundStatus=UNVERIFIED_SUCCESS`,
      { redirect: "error" });
    assert.equal(unverifiedResponse.status, 200);
    const unverified = await unverifiedResponse.json();
    assert.equal(unverified.code, "0");
    assert.deepEqual(new Set(unverified.data.items.map((item) => item.refundOrderId)),
      new Set(refundTriageOrderIds));
    if (verifyRefundInvalidAmount) {
      const amounts = new Map(unverified.data.items.map((item) => [item.refundOrderId, item.refundAmount]));
      assert.equal(amounts.get(refundTriageOrderId), "¥0.00");
      assert.equal(amounts.get("REF202607180020"), "¥-5.00");
    }
    if (verifyRefundCumulative || verifyRefundInvalidAmount) {
      const mismatchResponse = await fetch(`${backendUrl}/api/refunds?paymentOrderId=${refundTriagePaymentOrderId}&refundStatus=TERMINAL_FACT_MISMATCH`,
        { redirect: "error" });
      assert.equal(mismatchResponse.status, 200);
      assert.deepEqual(new Set((await mismatchResponse.json()).data.items.map((item) => item.refundOrderId)),
        new Set(refundTriageOrderIds));
    }
    if (verifyRefundMissingTime) {
      const missingResponse = await fetch(`${backendUrl}/api/refunds?paymentOrderId=${refundTriagePaymentOrderId}&refundStatus=SUCCESS_TIME_MISSING`,
        { redirect: "error" });
      const mismatchResponse = await fetch(`${backendUrl}/api/refunds?paymentOrderId=${refundTriagePaymentOrderId}&refundStatus=TERMINAL_FACT_MISMATCH`,
        { redirect: "error" });
      assert.equal(missingResponse.status, 200);
      assert.equal(mismatchResponse.status, 200);
      assert.deepEqual((await missingResponse.json()).data.items.map((item) => item.refundOrderId),
        [refundTriageOrderId]);
      assert.equal((await mismatchResponse.json()).data.total, 0);
    }
  }
}

async function verifyCashierSessionBrowser({ send, evaluate, responses, apiRequests }) {
  for (const { sessionNo, paymentOrderId, status, type } of cashierSessions) {
    await send("Page.navigate", {
      url: `${adminUrl}/cashier-sessions?sessionNo=${sessionNo}&sessionStatus=${encodeURIComponent(status)}`
    });
    const sessionRow = `Array.from(document.querySelectorAll('tbody tr'))
      .find((row) => row.querySelector('td')?.textContent.trim() === ${JSON.stringify(sessionNo)})`;
    await waitFor(() => evaluate(`(() => {
      const row = ${sessionRow};
      return row?.querySelector('.badge.${type}')?.textContent.trim() === ${JSON.stringify(status)}
        && !document.querySelector('.error-banner');
    })()`), `${sessionNo} 会话列表渲染`, () => evaluate("document.body.innerText.slice(0, 1600)"));
    await evaluate(`Array.from((${sessionRow}).querySelectorAll('button'))
      .find((button) => button.textContent.trim() === '查看快照').click()`);
    assert.ok(await evaluate(`document.querySelector('.detail-side')?.innerText.includes(${JSON.stringify(paymentOrderId)})
      && document.querySelector('.detail-side')?.innerText.includes(${JSON.stringify(status)})`));
    await evaluate(`Array.from((${sessionRow}).querySelectorAll('button'))
      .find((button) => button.textContent.trim() === '唯一详情').click()`);
    await waitFor(() => evaluate(`location.pathname === '/cashier-sessions/${sessionNo}'
      && Array.from(document.querySelectorAll('.detail-grid .badge.${type}'))
        .some((badge) => badge.textContent.trim() === ${JSON.stringify(status)})
      && document.body.innerText.includes(${JSON.stringify(paymentOrderId)})
      && !document.querySelector('.error-banner')`),
    `${sessionNo} 会话唯一详情渲染`, () => evaluate("document.body.innerText.slice(0, 1600)"));
    assert.ok(responses.some((response) => response.method === "GET" && response.status === 200
      && new URL(response.url).pathname === `/api/cashier-sessions/${sessionNo}`));
  }
  assert.ok(responses.some((response) => response.method === "GET" && response.status === 200
    && new URL(response.url).pathname === "/api/cashier-sessions"));
  assert.ok(apiRequests.length > 0 && apiRequests.every((request) => request.method === "GET"
    && new URL(request.url).origin === adminUrl), "浏览器不得发出写请求或跨源 API 请求");
  console.log(verifyCashierLifecycle
    ? "[PASS] 真实 H2/demo 关闭、风控及待确认会话列表、快照及唯一详情，仅 GET"
    : verifyCashierSuccessFact
      ? "[PASS] 真实 H2/demo 异常与可信成功会话列表、快照及唯一详情，仅 GET"
      : "[PASS] 真实 H2/demo 支付中/info 会话列表、快照及唯一详情，仅 GET");
}

async function verifyInvalidPaymentAmountBrowser({ send, evaluate, responses, apiRequests, rowExpression, rowButtons }) {
  for (const [paymentOrderId, amount] of invalidPaymentAmounts) {
    await send("Page.navigate", {
      url: `${adminUrl}/payments?status=SUCCESS_FACT_MISMATCH&paymentOrderId=${paymentOrderId}`
    });
    await waitFor(async () => Boolean(await rowButtons(paymentOrderId))
      && await evaluate(`(${rowExpression(paymentOrderId)})?.querySelector('td:nth-child(6)')?.textContent.trim() === ${JSON.stringify(amount)}`),
    `${paymentOrderId} 异常金额筛选行`,
    () => evaluate("JSON.stringify({ path: location.pathname, text: document.body.innerText.slice(0, 1400) })"));
    const buttons = await rowButtons(paymentOrderId);
    assert.deepEqual(buttons.map((button) => button.label), ["快照", "处理日志", "查单", "关闭"]);
    assert.equal(buttons.find((button) => button.label === "查单").disabled, true);
    assert.ok(await evaluate(`document.body.innerText.includes('本页实付金额（异常值不汇总）')
      && Array.from(document.querySelectorAll('.card-grid .card')).some((card) =>
        card.innerText.includes('本页实付金额') && card.querySelector('.card-value')?.textContent.trim() === '—')
      && (${rowExpression(paymentOrderId)}).innerText.includes('成功事实异常')`));
    await evaluate(`(${rowExpression(paymentOrderId)}).querySelector('button').click()`);
    assert.ok(await evaluate(`document.querySelector('.detail-side')?.innerText.includes(${JSON.stringify(amount)})
      && document.querySelector('.detail-side')?.innerText.includes('成功事实异常')`));
    await evaluate(`Array.from((${rowExpression(paymentOrderId)}).querySelectorAll('button'))
      .find((button) => button.textContent.trim() === '处理日志').click()`);
    await waitFor(() => evaluate(`location.pathname === '/payment-logs'
      && new URLSearchParams(location.search).get('paymentOrderId') === ${JSON.stringify(paymentOrderId)}
      && Array.from(document.querySelectorAll('tbody tr')).some((row) => row.innerText.includes(${JSON.stringify(paymentOrderId)}))`),
    `${paymentOrderId} 异常日志可读`,
    () => evaluate("JSON.stringify({ path: location.pathname, text: document.body.innerText.slice(0, 1400) })"));
  }
  assert.ok(responses.some((response) => response.method === "GET"
    && response.status === 200 && new URL(response.url).pathname === "/api/payment-logs"));
  assert.ok(apiRequests.length > 0 && apiRequests.every((request) => request.method === "GET"
    && new URL(request.url).origin === adminUrl), "浏览器不得发出写请求或跨源 API 请求");
  console.log("[PASS] 真实 H2/demo 零额与负额支付筛选、金额卡片、快照及日志只读联查，仅 GET");
}

async function verifyMonitorAdviceBrowser({ send, evaluate, responses, apiRequests }) {
  await send("Page.navigate", { url: `${adminUrl}/payment-monitor` });
  await waitFor(() => responses.some((response) => response.method === "GET"
    && response.status === 200 && new URL(response.url).pathname === "/api/payment-monitor/overview"),
  "真实支付监控 GET");
  const alertRow = `Array.from(document.querySelectorAll('tbody tr'))
    .find((row) => row.querySelector('button.table-link')?.textContent.trim() === '支付成功事实不一致')`;
  await waitFor(() => evaluate(`Boolean(${alertRow})`), "支付成功事实异常告警渲染");
  await evaluate(`(${alertRow}).querySelector('button.table-link').click()`);
  await waitFor(() => evaluate(`(() => {
    const cards = Array.from(document.querySelectorAll('.detail-panel .ops-card'));
    const advice = cards.find((card) => card.querySelector('.ops-title')?.textContent.trim() === '告警处理建议');
    const snapshot = cards.find((card) => card.querySelector('.ops-title')?.textContent.trim() === '告警快照');
    return advice?.innerText.includes('不得按当前订单行自动补标来源或重发付款')
      && advice.innerText.includes('预付单绑定、订单来源、支付尝试与渠道流水')
      && snapshot?.innerText.includes('不得自动补标');
  })()`), "监控页异常来源处置建议", () => evaluate("document.body.innerText.slice(0, 2200)"));
  await evaluate(`(${alertRow}).querySelector('button.link-button').click()`);
  await waitFor(() => evaluate(`location.pathname === '/payments'
    && new URLSearchParams(location.search).get('status') === 'SUCCESS_FACT_MISMATCH'`),
  "P1 告警进入成功事实异常筛选");
  await waitFor(() => responses.some((response) => response.method === "GET"
    && response.status === 200
    && new URL(response.url).pathname === "/api/payments"
    && new URL(response.url).searchParams.get("status") === "SUCCESS_FACT_MISMATCH"),
  "真实异常支付列表 GET");
  await waitFor(() => evaluate(`Array.from(document.querySelectorAll('tbody tr')).some((row) =>
    row.querySelector('td')?.textContent.trim() === '${anomalousPaymentOrderId}')`),
  "告警下钻显示异常支付单");
  if (verifyMonitorSourceDrift) {
    await waitFor(() => evaluate(`Array.from(document.querySelectorAll('tbody tr')).some((row) =>
      row.querySelector('td')?.textContent.trim() === '${refundTriagePaymentOrderId}'
        && row.querySelector('td:nth-child(10)')?.innerText.includes('成功事实异常'))`),
    "告警下钻显示来源漂移支付单", () => evaluate("document.body.innerText.slice(0, 2200)"));
  }
  assert.ok(apiRequests.every((request) => request.method === "GET"
    && new URL(request.url).origin === adminUrl), "监控页浏览器不得发出写请求或跨源 API 请求");
  if (verifyMonitorSourceDrift) {
    for (const [terminal, port] of [["App", 5175], ["H5", 5176], ["PC", 5177]]) {
      const terminalUrl = `http://localhost:${port}`;
      const priorRequests = apiRequests.length;
      await send("Page.navigate", {
        url: `${terminalUrl}/payment-result/${refundTriagePaymentOrderId}`
      });
      await waitFor(() => responses.some((response) => response.method === "GET"
        && response.status === 200
        && new URL(response.url).origin === terminalUrl
        && new URL(response.url).pathname === `/api/payments/${refundTriagePaymentOrderId}`),
      `${terminal} 异常支付详情 GET`);
      await waitFor(() => evaluate(`document.querySelector('h1')?.textContent.trim() === '支付结果待核查'
        && document.querySelector('.hero-amount.status-text')?.textContent.trim() === '待核查'
        && document.querySelector('.terminal-state-card')?.innerText.includes('重试加载支付结果')
        && document.body.innerText.includes('请勿重复付款')`),
      `${terminal} 失败闭合结果页`, () => evaluate("document.body.innerText.slice(0, 1600)"));
      assert.ok(apiRequests.slice(priorRequests).every((request) => request.method === "GET"
        && new URL(request.url).origin === terminalUrl), `${terminal} 不得发出写请求或跨源 API 请求`);
    }
  }
  console.log(verifyMonitorSourceDrift
    ? "[PASS] 真实 H2/demo 小写来源漂移、监控 P1、异常支付下钻与三端结果页待核查，仅 GET"
    : "[PASS] 真实 H2/demo 支付监控 P1 告警、来源核查建议与异常支付下钻，仅 GET");
}

async function verifyResultRefreshFailureBrowser({ send, evaluate, responses, apiRequests }) {
  for (const [terminal, port] of [["App", 5175], ["H5", 5176], ["PC", 5177]]) {
    const terminalUrl = `http://localhost:${port}`;
    const priorRequests = apiRequests.length;
    await send("Page.navigate", { url: `${terminalUrl}/payment-result/${refundTriagePaymentOrderId}?payScene=PC` });
    await waitFor(() => responses.some((response) => response.method === "GET"
      && response.status === 200
      && new URL(response.url).origin === terminalUrl
      && new URL(response.url).pathname === `/api/payments/${refundTriagePaymentOrderId}`),
    `${terminal} 可信成功详情 GET`);
    await waitFor(() => evaluate(`document.querySelector('h1')?.textContent.trim() === '支付成功'
      && document.querySelector('.hero-amount.status-text')?.textContent.trim() === 'SUCCESS'`),
    `${terminal} 首次可信成功页面`, () => evaluate("document.body.innerText.slice(0, 1800)"));
    await send("Network.emulateNetworkConditions", {
      offline: true, latency: 0, downloadThroughput: 0, uploadThroughput: 0
    });
    await evaluate(`Array.from(document.querySelectorAll('.terminal-actions button'))
      .find((button) => button.textContent.trim() === '刷新支付详情').click()`);
    await waitFor(() => evaluate(`document.querySelector('h1')?.textContent.trim() === '支付结果待核查'
      && document.querySelector('.hero-amount.status-text')?.textContent.trim() === '待核查'
      && document.querySelector('.terminal-state-card button')?.textContent.trim() === '重试加载支付结果'
      && !document.querySelector('.terminal-actions')`),
    `${terminal} 刷新失败不保留旧成功`, () => evaluate("document.body.innerText.slice(0, 1800)"));
    await send("Network.emulateNetworkConditions", {
      offline: false, latency: 0, downloadThroughput: -1, uploadThroughput: -1
    });
    await evaluate("document.querySelector('.terminal-state-card button').click()");
    await waitFor(() => evaluate(`document.querySelector('h1')?.textContent.trim() === '支付成功'
      && document.querySelector('.hero-amount.status-text')?.textContent.trim() === 'SUCCESS'`),
    `${terminal} 恢复后重新验证成功`, () => evaluate("document.body.innerText.slice(0, 1800)"));
    assert.ok(apiRequests.slice(priorRequests).every((request) => request.method === "GET"
      && new URL(request.url).origin === terminalUrl), `${terminal} 不得发出写请求或跨源 API 请求`);
  }
  console.log("[PASS] 真实 H2/demo 三端先可信成功、手动刷新断网失效、恢复后重验成功，仅 GET");
}

async function verifyResultAutoRefreshFailureBrowser({ send, evaluate, responses, apiRequests }) {
  for (const [terminal, port] of [["App", 5175], ["H5", 5176], ["PC", 5177]]) {
    const terminalUrl = `http://localhost:${port}`;
    const priorRequests = apiRequests.length;
    await send("Page.navigate", { url: `${terminalUrl}/payment-result/${normalPaymentOrderId}?payScene=H5` });
    await waitFor(() => responses.some((response) => response.method === "GET"
      && response.status === 200
      && new URL(response.url).origin === terminalUrl
      && new URL(response.url).pathname === `/api/payments/${normalPaymentOrderId}`),
    `${terminal} 首次待回调详情 GET`);
    await waitFor(() => evaluate(`document.querySelector('h1')?.textContent.trim() === '支付处理中'
      && document.querySelector('.hero-amount.status-text')?.textContent.trim() === 'WAIT_CALLBACK'`),
    `${terminal} 首次待回调页面`, () => evaluate("document.body.innerText.slice(0, 1500)"));
    await send("Network.emulateNetworkConditions", {
      offline: true, latency: 0, downloadThroughput: 0, uploadThroughput: 0
    });
    await waitFor(() => evaluate(`document.querySelector('h1')?.textContent.trim() === '支付结果待核查'
      && document.querySelector('.hero-amount.status-text')?.textContent.trim() === '待核查'
      && document.body.innerText.includes('详情仅供核对上次读取的事实')
      && Array.from(document.querySelectorAll('.terminal-actions button'))
        .filter((button) => button.textContent.trim() !== '刷新支付详情')
        .every((button) => button.disabled)`),
    `${terminal} 后台刷新失败旧状态隔离`, () => evaluate("document.body.innerText.slice(0, 1700)"));
    await send("Network.emulateNetworkConditions", {
      offline: false, latency: 0, downloadThroughput: -1, uploadThroughput: -1
    });
    await waitFor(() => evaluate(`document.querySelector('h1')?.textContent.trim() === '支付处理中'
      && document.querySelector('.hero-amount.status-text')?.textContent.trim() === 'WAIT_CALLBACK'
      && !document.body.innerText.includes('详情仅供核对上次读取的事实')
      && Array.from(document.querySelectorAll('.terminal-actions button'))
        .some((button) => button.textContent.trim() !== '刷新支付详情' && !button.disabled)`),
    `${terminal} 自动重试恢复最新状态`, () => evaluate("document.body.innerText.slice(0, 1700)"));
    assert.ok(apiRequests.slice(priorRequests).every((request) => request.method === "GET"
      && new URL(request.url).origin === terminalUrl), `${terminal} 不得发出写请求或跨源 API 请求`);
  }
  console.log("[PASS] 真实 H2/demo 三端后台轮询断网标旧与自动恢复，仅同源 GET");
}

async function verifyRefundProgressRefreshFailureBrowser({ send, evaluate, responses, apiRequests }) {
  for (const [terminal, port] of [["App", 5175], ["H5", 5176], ["PC", 5177]]) {
    const terminalUrl = `http://localhost:${port}`;
    const priorRequests = apiRequests.length;
    await send("Page.navigate", { url: `${terminalUrl}/refund-progress/REF202607190001?payScene=H5` });
    await waitFor(() => responses.some((response) => response.method === "GET"
      && response.status === 200
      && new URL(response.url).origin === terminalUrl
      && new URL(response.url).pathname === "/api/refunds/REF202607190001"),
    `${terminal} 首次退款详情 GET`);
    const verified = `document.querySelector('h1')?.textContent.trim() === '退款处理中'
      && document.querySelector('.hero-amount.status-text')?.textContent.trim() === 'PROCESSING'
      && !document.body.innerText.includes('最新退款进度未能确认')`;
    const unverified = `document.querySelector('h1')?.textContent.trim() === '退款进度待核查'
      && document.querySelector('.hero-amount.status-text')?.textContent.trim() === '待核查'
      && document.body.innerText.includes('当前详情仅供核对上次读取的事实')`;
    await waitFor(() => evaluate(verified), `${terminal} 首次退款处理中页面`,
      () => evaluate("document.body.innerText.slice(0, 1600)"));
    await send("Network.emulateNetworkConditions", {
      offline: true, latency: 0, downloadThroughput: 0, uploadThroughput: 0
    });
    await evaluate("document.querySelector('.terminal-actions button').click()");
    await waitFor(() => evaluate(unverified), `${terminal} 手动刷新失败标旧`,
      () => evaluate("document.body.innerText.slice(0, 1600)"));
    await send("Network.emulateNetworkConditions", {
      offline: false, latency: 0, downloadThroughput: -1, uploadThroughput: -1
    });
    await evaluate("document.querySelector('.terminal-actions button').click()");
    await waitFor(() => evaluate(verified), `${terminal} 手动刷新恢复`,
      () => evaluate("document.body.innerText.slice(0, 1600)"));
    await send("Network.emulateNetworkConditions", {
      offline: true, latency: 0, downloadThroughput: 0, uploadThroughput: 0
    });
    await waitFor(() => evaluate(unverified), `${terminal} 自动轮询失败标旧`,
      () => evaluate("document.body.innerText.slice(0, 1600)"), 150);
    await send("Network.emulateNetworkConditions", {
      offline: false, latency: 0, downloadThroughput: -1, uploadThroughput: -1
    });
    await waitFor(() => evaluate(verified), `${terminal} 自动轮询恢复`,
      () => evaluate("document.body.innerText.slice(0, 1600)"), 150);
    await send("Page.navigate", { url: `${terminalUrl}/refund-progress/${refundTriageOrderId}?payScene=PC` });
    const trustedSuccess = `document.querySelector('h1')?.textContent.trim() === '退款成功'
      && document.querySelector('.hero-amount.status-text')?.textContent.trim() === 'SUCCESS'`;
    await waitFor(() => evaluate(trustedSuccess), `${terminal} 首次成功退款页面`,
      () => evaluate("document.body.innerText.slice(0, 1600)"));
    await send("Network.emulateNetworkConditions", {
      offline: true, latency: 0, downloadThroughput: 0, uploadThroughput: 0
    });
    await evaluate("document.querySelector('.terminal-actions button').click()");
    await waitFor(() => evaluate(unverified), `${terminal} 成功退款刷新失败标旧`,
      () => evaluate("document.body.innerText.slice(0, 1600)"));
    await send("Network.emulateNetworkConditions", {
      offline: false, latency: 0, downloadThroughput: -1, uploadThroughput: -1
    });
    await evaluate("document.querySelector('.terminal-actions button').click()");
    await waitFor(() => evaluate(trustedSuccess), `${terminal} 成功退款重新核验`,
      () => evaluate("document.body.innerText.slice(0, 1600)"));
    assert.ok(apiRequests.slice(priorRequests).every((request) => request.method === "GET"
      && new URL(request.url).origin === terminalUrl), `${terminal} 不得发出写请求或跨源 API 请求`);
  }
  console.log("[PASS] 真实 H2/demo 三端处理中轮询及成功退款刷新失败标旧并恢复，仅同源 GET");
}

async function runBrowser() {
  assert.ok(existsSync(chromeBinary), `未找到 Chrome：${chromeBinary}`);
  const profile = await mkdtemp(path.join(os.tmpdir(), "hsp-payment-admin-diagnostic-"));
  const chrome = spawn(chromeBinary, [
    "--headless", "--disable-gpu", "--disable-background-networking",
    "--disable-component-update", "--no-first-run", "--no-default-browser-check",
    "--no-proxy-server", "--remote-allow-origins=*", "--remote-debugging-port=0",
    `--user-data-dir=${profile}`, "about:blank"
  ], { stdio: "ignore" });
  let socket;
  try {
    let port;
    await waitFor(async () => {
      try {
        port = (await readFile(path.join(profile, "DevToolsActivePort"), "utf8")).split("\n")[0];
        return Boolean(port);
      } catch {
        return false;
      }
    }, "Chrome 调试端口");
    const targets = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
    const target = targets.find((item) => item.type === "page");
    assert.ok(target, "Chrome 未创建页面目标");
    socket = new WebSocket(target.webSocketDebuggerUrl);
    await new Promise((resolve, reject) => {
      socket.addEventListener("open", resolve, { once: true });
      socket.addEventListener("error", reject, { once: true });
    });

    let commandId = 0;
    const pending = new Map();
    const methods = new Map();
    const responses = [];
    const apiRequests = [];
    socket.addEventListener("message", ({ data }) => {
      const message = JSON.parse(data);
      if (message.method === "Network.requestWillBeSent") {
        const request = message.params.request;
        methods.set(message.params.requestId, request.method);
        if (new URL(request.url).pathname.startsWith("/api/")) {
          apiRequests.push({ method: request.method, url: request.url });
        }
      }
      if (message.method === "Network.responseReceived") {
        responses.push({
          url: message.params.response.url,
          status: message.params.response.status,
          method: methods.get(message.params.requestId)
        });
      }
      const command = pending.get(message.id);
      if (!command) return;
      pending.delete(message.id);
      message.error ? command.reject(new Error(message.error.message)) : command.resolve(message.result);
    });
    const send = (method, params = {}) => new Promise((resolve, reject) => {
      const id = ++commandId;
      pending.set(id, { resolve, reject });
      socket.send(JSON.stringify({ id, method, params }));
    });
    const evaluate = async (expression) => {
      const result = await send("Runtime.evaluate", { expression, returnByValue: true });
      if (result.exceptionDetails) throw new Error(result.exceptionDetails.text);
      return result.result.value;
    };
    const rowExpression = (paymentOrderId) => `Array.from(document.querySelectorAll('tbody tr'))
      .find((row) => row.querySelector('td')?.textContent.trim() === ${JSON.stringify(paymentOrderId)})`;
    const rowButtons = (paymentOrderId) => evaluate(`(() => {
      const row = ${rowExpression(paymentOrderId)};
      return row && Array.from(row.querySelectorAll('button'))
        .map((button) => ({ label: button.textContent.trim(), disabled: button.disabled }));
    })()`);
    await send("Page.enable");
    await send("Runtime.enable");
    await send("Network.enable");
    if (verifyResultRefreshFailure) {
      await verifyResultRefreshFailureBrowser({ send, evaluate, responses, apiRequests });
      return;
    }
    if (verifyResultAutoRefreshFailure) {
      await verifyResultAutoRefreshFailureBrowser({ send, evaluate, responses, apiRequests });
      return;
    }
    if (verifyRefundProgressRefreshFailure) {
      await verifyRefundProgressRefreshFailureBrowser({ send, evaluate, responses, apiRequests });
      return;
    }
    if (verifyCashierPaying || verifyCashierLifecycle || verifyCashierSuccessFact) {
      await verifyCashierSessionBrowser({ send, evaluate, responses, apiRequests });
      return;
    }
    if (verifyPaymentInvalidAmount) {
      await verifyInvalidPaymentAmountBrowser({ send, evaluate, responses, apiRequests, rowExpression, rowButtons });
      return;
    }
    if (verifyMonitorAdvice) {
      await verifyMonitorAdviceBrowser({ send, evaluate, responses, apiRequests });
      return;
    }

    await send("Page.navigate", {
      url: `${adminUrl}/payments?status=%E5%85%A8%E9%83%A8&paymentOrderId=PAY20260719000`
    });
    await waitFor(() => responses.some((response) => response.method === "GET"
      && response.status === 200 && new URL(response.url).pathname === "/api/payments"),
    "真实支付列表 GET");
    await waitFor(async () => Boolean(await rowButtons(anomalousPaymentOrderId))
      && Boolean(await rowButtons(normalPaymentOrderId)), "混合支付行渲染");

    const anomalousButtons = await rowButtons(anomalousPaymentOrderId);
    const normalButtons = await rowButtons(normalPaymentOrderId);
    assert.deepEqual(anomalousButtons.map((button) => button.label),
      ["快照", "处理日志", "查单", "关闭"]);
    assert.equal(anomalousButtons.find((button) => button.label === "查单").disabled, true);
    assert.deepEqual(normalButtons.map((button) => button.label),
      ["快照", "详情", "查单", "关闭"]);
    assert.equal(normalButtons.find((button) => button.label === "查单").disabled, false);
    assert.ok(await evaluate(`(${rowExpression(anomalousPaymentOrderId)})?.innerText.includes('成功事实异常')`));

    await evaluate(`(${rowExpression(anomalousPaymentOrderId)}).querySelector('button').click()`);
    assert.ok(await evaluate("document.querySelector('.detail-side')?.innerText.includes('成功事实异常')"));
    await evaluate(`Array.from((${rowExpression(anomalousPaymentOrderId)}).querySelectorAll('button'))
      .find((button) => button.textContent.trim() === '处理日志').click()`);
    await waitFor(() => responses.some((response) => response.method === "GET"
      && response.status === 200 && new URL(response.url).pathname === "/api/payment-logs"),
    "真实支付日志 GET");
    await waitFor(() => evaluate(`location.pathname === '/payment-logs'
      && new URLSearchParams(location.search).get('paymentOrderId') === '${anomalousPaymentOrderId}'
      && document.body.innerText.includes('${anomalousPaymentOrderId}')`), "按异常单号联查日志",
    () => evaluate("JSON.stringify({ path: location.pathname, search: location.search, text: document.body.innerText.slice(0, 1200) })"));
    assert.ok(await evaluate(`Array.from(document.querySelectorAll('tbody tr')).every((row) =>
      row.querySelector('td:nth-child(2)')?.innerText.includes('${anomalousPaymentOrderId}')
      && !row.querySelector('td:nth-child(2) button'))`));
    assert.ok(await evaluate(`Array.from(document.querySelectorAll('.detail-side button'))
      .some((button) => button.textContent.trim() === '查看支付单' && button.disabled)`));
    await evaluate(`document.querySelector('tbody tr button')?.click()`);
    await waitFor(() => responses.some((response) => response.method === "GET"
      && response.status === 200 && new URL(response.url).pathname.startsWith("/api/payment-logs/")),
    "真实支付日志详情 GET");
    await waitFor(() => evaluate(`location.pathname.startsWith('/payment-logs/')
      && document.body.innerText.includes('成功事实异常')
      && Array.from(document.querySelectorAll('.toolbar-actions button'))
        .some((button) => button.textContent.trim() === '支付单' && button.disabled)`),
    "异常日志详情禁用支付单钻取");

    await send("Page.navigate", {
      url: `${adminUrl}/payment-flows?paymentOrderId=${anomalousPaymentOrderId}`
    });
    await waitFor(() => responses.some((response) => response.method === "GET"
      && response.status === 200 && new URL(response.url).pathname === "/api/payment-flows"),
    "真实支付流水 GET");
    await waitFor(async () => Boolean(await rowButtons("ATT202607190001")), "异常支付流水渲染");
    assert.ok(await evaluate(`(() => {
      const row = ${rowExpression("ATT202607190001")};
      return row.querySelector('td:nth-child(2)').innerText.includes('SUCCESS_FACT_MISMATCH')
        && !row.querySelector('td:nth-child(2) button');
    })()`));
    await evaluate(`Array.from((${rowExpression("ATT202607190001")}).querySelectorAll('button'))
      .find((button) => button.textContent.trim() === '查看快照').click()`);
    assert.ok(await evaluate(`document.querySelector('.detail-side')?.innerText.includes('成功支付详情不可用')
      && Array.from(document.querySelectorAll('.detail-side button'))
        .some((button) => button.textContent.trim() === '查看支付单' && button.disabled)`));
    await evaluate(`Array.from((${rowExpression("ATT202607190001")}).querySelectorAll('button'))
      .find((button) => button.textContent.trim() === '查看详情').click()`);
    await waitFor(() => responses.some((response) => response.method === "GET"
      && response.status === 200
      && new URL(response.url).pathname === "/api/payment-flows/PAYMENT_ATTEMPT/ATT202607190001"),
    "真实支付流水详情 GET");
    await waitFor(() => evaluate(`location.pathname === '/payment-flows/PAYMENT_ATTEMPT/ATT202607190001'
      && document.body.innerText.includes('SUCCESS_FACT_MISMATCH')
      && Array.from(document.querySelectorAll('.toolbar-actions button'))
        .some((button) => button.textContent.trim() === '支付单' && button.disabled)`),
    "异常流水详情禁用支付单钻取",
    () => evaluate("JSON.stringify({ path: location.pathname, text: document.body.innerText.slice(0, 1300), buttons: Array.from(document.querySelectorAll('.toolbar-actions button')).map((button) => ({ label: button.textContent.trim(), disabled: button.disabled })) })"));

    await send("Page.navigate", {
      url: `${adminUrl}/payment-flows?paymentOrderId=${normalPaymentOrderId}`
    });
    await waitFor(() => evaluate(`(() => {
      const row = ${rowExpression("ATT202607190002")};
      return row && Array.from(row.querySelectorAll('td:nth-child(2) button'))
        .some((button) => button.textContent.trim() === '${normalPaymentOrderId}' && !button.disabled);
    })()`), "普通流水保留支付详情入口");

    await send("Page.navigate", {
      url: `${adminUrl}/payment-requests?paymentOrderId=${anomalousPaymentOrderId}`
    });
    await waitFor(() => responses.some((response) => response.method === "GET"
      && response.status === 200 && new URL(response.url).pathname === "/api/payment-requests"),
    "真实支付请求 GET");
    await waitFor(async () => Boolean(await rowButtons("ATT202607190001")), "异常支付请求渲染");
    assert.ok(await evaluate(`(() => {
      const row = ${rowExpression("ATT202607190001")};
      return row.querySelector('td:nth-child(2)').innerText.includes('SUCCESS_FACT_MISMATCH')
        && !row.querySelector('td:nth-child(2) button');
    })()`));
    await evaluate(`Array.from((${rowExpression("ATT202607190001")}).querySelectorAll('button'))
      .find((button) => button.textContent.trim() === '选中快照').click()`);
    assert.ok(await evaluate(`document.querySelector('.detail-side')?.innerText.includes('成功支付详情不可用')
      && Array.from(document.querySelectorAll('.detail-side button'))
        .some((button) => button.textContent.trim() === '查看支付单' && button.disabled)`));
    await evaluate(`Array.from((${rowExpression("ATT202607190001")}).querySelectorAll('button'))
      .find((button) => button.textContent.trim() === '唯一详情').click()`);
    await waitFor(() => responses.some((response) => response.method === "GET"
      && response.status === 200
      && new URL(response.url).pathname === "/api/payment-requests/ATT202607190001"),
    "真实支付请求详情 GET");
    await waitFor(() => evaluate(`location.pathname === '/payment-requests/ATT202607190001'
      && document.body.innerText.includes('SUCCESS_FACT_MISMATCH')
      && Array.from(document.querySelectorAll('.toolbar-actions button'))
        .some((button) => button.textContent.trim() === '支付单' && button.disabled)`),
    "异常请求详情禁用支付单钻取",
    () => evaluate("JSON.stringify({ path: location.pathname, text: document.body.innerText.slice(0, 1300) })"));

    await send("Page.navigate", {
      url: `${adminUrl}/payment-requests?paymentOrderId=${normalPaymentOrderId}`
    });
    await waitFor(() => evaluate(`(() => {
      const row = ${rowExpression("ATT202607190002")};
      return row && Array.from(row.querySelectorAll('td:nth-child(2) button'))
        .some((button) => button.textContent.trim() === '${normalPaymentOrderId}' && !button.disabled);
    })()`), "普通请求保留支付详情入口");

    await send("Page.navigate", {
      url: `${adminUrl}/payment-routes?paymentOrderId=${anomalousPaymentOrderId}`
    });
    await waitFor(() => responses.some((response) => response.method === "GET"
      && response.status === 200 && new URL(response.url).pathname === "/api/payment-routes"),
    "真实支付路由 GET");
    await waitFor(async () => Boolean(await rowButtons("RTR202607190001")), "异常支付路由渲染");
    assert.ok(await evaluate(`(() => {
      const row = ${rowExpression("RTR202607190001")};
      return row.querySelector('td:nth-child(2)').innerText.includes('SUCCESS_FACT_MISMATCH')
        && !row.querySelector('td:nth-child(2) button');
    })()`));
    await evaluate(`Array.from((${rowExpression("RTR202607190001")}).querySelectorAll('button'))
      .find((button) => button.textContent.trim() === '查看快照').click()`);
    assert.ok(await evaluate(`document.querySelector('.detail-side')?.innerText.includes('成功支付详情不可用')
      && Array.from(document.querySelectorAll('.detail-side button'))
        .some((button) => button.textContent.trim() === '查看支付单' && button.disabled)`));
    await evaluate(`Array.from((${rowExpression("RTR202607190001")}).querySelectorAll('button'))
      .find((button) => button.textContent.trim() === '查看详情').click()`);
    await waitFor(() => responses.some((response) => response.method === "GET"
      && response.status === 200
      && new URL(response.url).pathname === "/api/payment-routes/RTR202607190001"),
    "真实支付路由详情 GET");
    await waitFor(() => evaluate(`location.pathname === '/payment-routes/RTR202607190001'
      && document.body.innerText.includes('SUCCESS_FACT_MISMATCH')
      && Array.from(document.querySelectorAll('.toolbar-actions button'))
        .some((button) => button.textContent.trim() === '支付单' && button.disabled)`),
    "异常路由详情禁用支付单钻取",
    () => evaluate("JSON.stringify({ path: location.pathname, text: document.body.innerText.slice(0, 1300) })"));

    await send("Page.navigate", {
      url: `${adminUrl}/payment-routes?paymentOrderId=${normalPaymentOrderId}`
    });
    await waitFor(() => evaluate(`(() => {
      const row = ${rowExpression("RTR202607190002")};
      return row && Array.from(row.querySelectorAll('td:nth-child(2) button'))
        .some((button) => button.textContent.trim() === '${normalPaymentOrderId}' && !button.disabled);
    })()`), "普通路由保留支付详情入口");

    await send("Page.navigate", {
      url: `${adminUrl}/payment-events?paymentOrderId=${anomalousPaymentOrderId}`
    });
    await waitFor(() => responses.some((response) => response.method === "GET"
      && response.status === 200 && new URL(response.url).pathname === "/api/payment-events"),
    "真实支付事件 GET");
    await waitFor(async () => Boolean(await rowButtons("EVT202607190001")), "异常支付事件渲染");
    assert.ok(await evaluate(`(${rowExpression("EVT202607190001")})
      .querySelector('td:nth-child(3)').innerText.includes('SUCCESS_FACT_MISMATCH')`));
    await evaluate(`Array.from((${rowExpression("EVT202607190001")}).querySelectorAll('button'))
      .find((button) => button.textContent.trim() === '快照').click()`);
    assert.ok(await evaluate(`document.querySelector('.detail-side')?.innerText.includes('SUCCESS_FACT_MISMATCH')
      && Array.from(document.querySelectorAll('.detail-side button'))
        .some((button) => button.textContent.trim() === '查看支付单' && button.disabled)`));
    await evaluate(`Array.from((${rowExpression("EVT202607190001")}).querySelectorAll('button'))
      .find((button) => button.textContent.trim() === '详情').click()`);
    await waitFor(() => responses.some((response) => response.method === "GET"
      && response.status === 200
      && new URL(response.url).pathname === "/api/payment-events/EVT202607190001"),
    "真实支付事件详情 GET");
    await waitFor(() => evaluate(`location.pathname === '/payment-events/EVT202607190001'
      && document.body.innerText.includes('SUCCESS_FACT_MISMATCH')
      && Array.from(document.querySelectorAll('.toolbar-actions button'))
        .some((button) => button.textContent.trim() === '支付单' && button.disabled)`),
    "异常事件详情禁用支付单钻取",
    () => evaluate("JSON.stringify({ path: location.pathname, text: document.body.innerText.slice(0, 1300) })"));

    await send("Page.navigate", {
      url: `${adminUrl}/payment-events?paymentOrderId=${normalPaymentOrderId}`
    });
    await waitFor(async () => Boolean(await rowButtons("EVT202607190002")), "普通支付事件渲染",
    () => evaluate("JSON.stringify({ path: location.pathname, text: document.body.innerText.slice(0, 1600) })"));
    await evaluate(`Array.from((${rowExpression("EVT202607190002")}).querySelectorAll('button'))
      .find((button) => button.textContent.trim() === '快照').click()`);
    assert.ok(await evaluate(`Array.from(document.querySelectorAll('.detail-side button'))
      .some((button) => button.textContent.trim() === '查看支付单' && !button.disabled)`));
    await evaluate(`Array.from((${rowExpression("EVT202607190002")}).querySelectorAll('button'))
      .find((button) => button.textContent.trim() === '详情').click()`);
    await waitFor(() => responses.some((response) => response.method === "GET"
      && response.status === 200
      && new URL(response.url).pathname === "/api/payment-events/EVT202607190002"),
    "真实普通支付事件详情 GET");
    await waitFor(() => evaluate(`location.pathname === '/payment-events/EVT202607190002'
      && Array.from(document.querySelectorAll('.toolbar-actions button'))
        .some((button) => button.textContent.trim() === '支付单' && !button.disabled)`),
    "普通事件详情保留支付单钻取",
    () => evaluate("JSON.stringify({ path: location.pathname, text: document.body.innerText.slice(0, 1300) })"));

    await send("Page.navigate", { url: `${adminUrl}/payment-records` });
    await waitFor(() => responses.some((response) => response.method === "GET"
      && response.status === 200 && new URL(response.url).pathname === "/api/payment-records"),
    "真实支付记录 GET");
    const recordRow = (paymentOrderId) => `Array.from(document.querySelectorAll('tbody tr'))
      .find((row) => row.querySelector('td:nth-child(2)')?.textContent.trim() === ${JSON.stringify(paymentOrderId)})`;
    await waitFor(() => evaluate(`Boolean(${recordRow(anomalousPaymentOrderId)})
      && Boolean(${recordRow(normalPaymentOrderId)})`), "异常与普通支付记录渲染",
    () => evaluate("JSON.stringify({ path: location.pathname, text: document.body.innerText.slice(0, 1600) })"));
    assert.ok(await evaluate(`(${recordRow(anomalousPaymentOrderId)})
      .innerText.includes('SUCCESS_FACT_MISMATCH')`));
    await evaluate(`Array.from((${recordRow(anomalousPaymentOrderId)}).querySelectorAll('button'))
      .find((button) => button.textContent.trim() === '详情').click()`);
    await waitFor(() => responses.some((response) => response.method === "GET"
      && response.status === 200
      && new URL(response.url).pathname === `/api/payment-records/${anomalousPaymentOrderId}`),
    "真实异常支付记录详情 GET",
    async () => JSON.stringify({ page: await evaluate("JSON.stringify({ path: location.pathname, text: document.body.innerText.slice(0, 1500) })"),
      recentResponses: responses.slice(-8) }));
    await waitFor(() => evaluate(`location.pathname === '/payment-records/${anomalousPaymentOrderId}'
      && document.body.innerText.includes('SUCCESS_FACT_MISMATCH')
      && Array.from(document.querySelectorAll('.toolbar-actions button'))
        .some((button) => button.textContent.trim() === '查看支付单详情' && button.disabled)`),
    "异常记录详情禁用支付单钻取",
    () => evaluate("JSON.stringify({ path: location.pathname, text: document.body.innerText.slice(0, 1600) })"));

    await send("Page.navigate", { url: `${adminUrl}/payment-records/${normalPaymentOrderId}` });
    await waitFor(() => responses.some((response) => response.method === "GET"
      && response.status === 200
      && new URL(response.url).pathname === `/api/payment-records/${normalPaymentOrderId}`),
    "真实普通支付记录详情 GET");
    await waitFor(() => evaluate(`location.pathname === '/payment-records/${normalPaymentOrderId}'
      && Array.from(document.querySelectorAll('.toolbar-actions button'))
        .some((button) => button.textContent.trim() === '查看支付单详情' && !button.disabled)`),
    "普通记录详情保留支付单钻取",
    () => evaluate("JSON.stringify({ path: location.pathname, text: document.body.innerText.slice(0, 1600) })"));
    if (verifyMissingSuccessTime) {
      await send("Page.navigate", {
        url: `${adminUrl}/payments?status=SUCCESS_TIME_MISSING&paymentOrderId=${refundTriagePaymentOrderId}`
      });
      await waitFor(async () => Boolean(await rowButtons(refundTriagePaymentOrderId)),
        "缺失成功时间的支付单筛选行",
        () => evaluate("JSON.stringify({ path: location.pathname, text: document.body.innerText.slice(0, 1600) })"));
      const missingButtons = await rowButtons(refundTriagePaymentOrderId);
      assert.deepEqual(missingButtons.map((button) => button.label), ["快照", "处理日志", "查单", "关闭"]);
      assert.equal(missingButtons.find((button) => button.label === "查单").disabled, true);
      assert.ok(await evaluate(`(${rowExpression(refundTriagePaymentOrderId)}).innerText.includes('成功时间缺失')`));
      await evaluate(`Array.from((${rowExpression(refundTriagePaymentOrderId)}).querySelectorAll('button'))
        .find((button) => button.textContent.trim() === '处理日志').click()`);
      await waitFor(() => evaluate(`location.pathname === '/payment-logs'
        && new URLSearchParams(location.search).get('paymentOrderId') === '${refundTriagePaymentOrderId}'
        && Array.from(document.querySelectorAll('tbody tr'))
          .some((row) => row.innerText.includes('SUCCESS_TIME_MISSING'))`),
      "缺失成功时间支付单的处理日志",
      () => evaluate("JSON.stringify({ path: location.pathname, search: location.search, text: document.body.innerText.slice(0, 1800) })"));

      await send("Page.navigate", { url: `${adminUrl}/payment-records/${refundTriagePaymentOrderId}` });
      await waitFor(() => evaluate(`location.pathname === '/payment-records/${refundTriagePaymentOrderId}'
        && document.body.innerText.includes('SUCCESS_TIME_MISSING')
        && Array.from(document.querySelectorAll('.toolbar-actions button'))
          .some((button) => button.textContent.trim() === '查看支付单详情' && button.disabled)`),
      "缺失成功时间收款记录只读详情",
      () => evaluate("JSON.stringify({ path: location.pathname, text: document.body.innerText.slice(0, 1800) })"));
    }
    if (verifyRefundTriage) {
      await send("Page.navigate", { url: `${adminUrl}/payment-records` });
      await waitFor(() => evaluate(`Boolean(${recordRow(refundTriagePaymentOrderId)})
        && (${recordRow(refundTriagePaymentOrderId)}).innerText.includes('${refundTriageOrderIds.length} 笔待核查')`),
      "异常成功退款收款行提示",
      () => evaluate("JSON.stringify({ path: location.pathname, text: document.body.innerText.slice(0, 1800) })"));
      await evaluate(`Array.from((${recordRow(refundTriagePaymentOrderId)}).querySelectorAll('button'))
        .find((button) => button.textContent.trim() === '查看快照').click()`);
      await waitFor(() => evaluate(`document.querySelector('.detail-side')?.innerText.includes('${refundTriageOrderIds.length} 笔未计入已退')
        && Array.from(document.querySelectorAll('.detail-side button'))
          .some((button) => button.textContent.trim() === '查看待核查退款' && !button.disabled)`),
      "异常退款记录快照及核查入口");
      await evaluate(`Array.from(document.querySelectorAll('.detail-side button'))
        .find((button) => button.textContent.trim() === '查看待核查退款').click()`);
      await waitFor(() => evaluate(`location.pathname === '/refunds'
        && new URLSearchParams(location.search).get('paymentOrderId') === '${refundTriagePaymentOrderId}'
        && new URLSearchParams(location.search).get('refundStatus') === 'UNVERIFIED_SUCCESS'
        && ${JSON.stringify(refundTriageOrderIds)}.every((refundId) => Array.from(document.querySelectorAll('tbody tr'))
          .some((row) => row.querySelector('td')?.textContent.trim() === refundId))
        && document.body.innerText.includes('诊断筛选仅供核查和导出')
        && Array.from(document.querySelectorAll('.card')).some((card) =>
          card.querySelector('.card-title')?.textContent.trim() === '本页退款金额（异常值不汇总）'
          && card.querySelector('.card-value')?.textContent.trim() === '—')`),
      "记录快照下钻退款异常筛选",
      () => evaluate("JSON.stringify({ path: location.pathname, search: location.search, text: document.body.innerText.slice(0, 1800) })"));
      assert.ok(await evaluate(`(() => ${JSON.stringify(refundTriageOrderIds)}.every((refundId) => {
        const row = Array.from(document.querySelectorAll('tbody tr'))
          .find((item) => item.querySelector('td')?.textContent.trim() === refundId);
        if (!row) return false;
        const buttons = Array.from(row.querySelectorAll('button'));
        return buttons.some((button) => button.textContent.trim() === '快照' && !button.disabled)
          && buttons.filter((button) => button.textContent.trim() !== '快照').every((button) => button.disabled);
      }))()`));
      if (verifyRefundInvalidAmount) {
        assert.ok(await evaluate(`(() => {
          const rows = Array.from(document.querySelectorAll('tbody tr'));
          return rows.find((row) => row.querySelector('td')?.textContent.trim() === '${refundTriageOrderId}')
            ?.querySelector('td:nth-child(7)')?.textContent.trim() === '¥0.00'
            && rows.find((row) => row.querySelector('td')?.textContent.trim() === 'REF202607180020')
              ?.querySelector('td:nth-child(7)')?.textContent.trim() === '¥-5.00';
        })()`));
      }

      await send("Page.navigate", { url: `${adminUrl}/payment-records/${refundTriagePaymentOrderId}` });
      await waitFor(() => evaluate(`location.pathname === '/payment-records/${refundTriagePaymentOrderId}'
        && document.body.innerText.includes('${refundTriageOrderIds.length} 笔成功退款事实待核查')
        && Array.from(document.querySelectorAll('.toolbar-actions button'))
          .some((button) => button.textContent.trim() === '查看待核查退款' && !button.disabled)`),
      "记录详情退款异常提示与入口",
      () => evaluate("JSON.stringify({ path: location.pathname, text: document.body.innerText.slice(0, 1800) })"));
      await evaluate(`Array.from(document.querySelectorAll('.toolbar-actions button'))
        .find((button) => button.textContent.trim() === '查看待核查退款').click()`);
      await waitFor(() => evaluate(`location.pathname === '/refunds'
        && new URLSearchParams(location.search).get('paymentOrderId') === '${refundTriagePaymentOrderId}'
        && new URLSearchParams(location.search).get('refundStatus') === 'UNVERIFIED_SUCCESS'
        && ${JSON.stringify(refundTriageOrderIds)}.every((refundId) => Array.from(document.querySelectorAll('tbody tr'))
          .some((row) => row.querySelector('td')?.textContent.trim() === refundId))`),
      "记录详情下钻退款异常筛选");
    }
    assert.ok(apiRequests.every((request) => request.method === "GET"
      && new URL(request.url).origin === adminUrl), "浏览器不得发出写请求或跨源 API 请求");
    console.log(verifyRefundTriage
      ? `[PASS] 真实 H2/demo ${verifyRefundMissingTime ? "成功时间缺失" : verifyRefundCumulative ? "累计超额" : verifyRefundInvalidAmount ? "异常金额" : "终态异常"}从收款行、快照、详情下钻待核查退款筛选，仅 GET 联查`
      : verifyMissingSuccessTime
        ? "[PASS] 真实 H2/demo 缺失成功时间的支付筛选、处理日志及收款详情只读联查，仅 GET"
        : "[PASS] 真实 H2/demo 支付、日志、流水、请求、路由、事件及记录异常与普通行分流，仅 GET 联查，禁用异常支付详情");
  } finally {
    socket?.close();
    chrome.kill("SIGTERM");
    await Promise.race([new Promise((resolve) => chrome.once("exit", resolve)), delay(3000)]);
    if (chrome.exitCode == null) chrome.kill("SIGKILL");
    await rm(profile, { recursive: true, force: true });
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  if (!process.argv.includes("--demo")) {
    throw new Error("仅限本地演示栈；确认 demo profile 后显式传入 --demo");
  }
  await verifyDemoFixture();
  await runBrowser();
}
