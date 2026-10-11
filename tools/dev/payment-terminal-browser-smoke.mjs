#!/usr/bin/env node

import { spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { existsSync } from "node:fs";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const paymentBaseUrl = "http://localhost:18081";
const chromeBinary = process.env.HSP_CHROME_BIN
  || "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";

export function assertDemoBackend(response) {
  if (response?.code !== "0" || response?.data !== "payment-core-h2-demo") {
    throw new Error("本地支付核心未确认 H2/demo 身份，拒绝创建演示订单");
  }
}

export function assertDemoPrepay(response, orderNo) {
  const prepay = response?.data;
  if (prepay?.orderNo !== orderNo
      || typeof prepay.prepayOrderNo !== "string" || !prepay.prepayOrderNo.trim()
      || typeof prepay.paymentOrderId !== "string" || !prepay.paymentOrderId.trim()) {
    throw new Error("本地预付响应缺少匹配的订单号、预付单号或支付单号");
  }
  return prepay;
}

export function assertDemoRefund(response, prepay, expectedStatus = "PROCESSING") {
  const refund = response?.data;
  if (typeof refund?.refundOrderId !== "string" || !refund.refundOrderId.trim()
      || refund.paymentOrderId !== prepay.paymentOrderId
      || refund.status !== expectedStatus) {
    throw new Error("本地退款详情缺少匹配的退款单、原支付单或预期状态");
  }
  return refund;
}

export function assertDemoRepeatedSubmit(prepay, paid, repeated) {
  if (paid?.paymentOrderId !== prepay.paymentOrderId || paid.status !== "SUCCESS"
      || repeated?.paymentOrderId !== prepay.paymentOrderId
      || repeated.paymentStatus !== "SUCCESS"
      || !paid.channelTransactionNo
      || repeated.channelTransactionNo !== paid.channelTransactionNo
      || repeated.latestAttemptStatus !== "成功"
      || repeated.latestAttemptStatusType !== "success"
      || !paid.latestAttemptCompletedAt
      || repeated.latestAttemptCompletedAt !== paid.latestAttemptCompletedAt
      || !paid.successAt || repeated.successAt !== paid.successAt) {
    throw new Error("本地重复提交未复用匹配的支付成功事实");
  }
  return repeated;
}

export function assertDemoPendingSubmit(prepay, submitted) {
  if (submitted?.paymentOrderId !== prepay.paymentOrderId
      || submitted.prepayOrderNo !== prepay.prepayOrderNo
      || submitted.paymentStatus !== "WAIT_CALLBACK"
      || submitted.paymentMethod !== "支付宝"
      || submitted.channel !== "alipay_h5"
      || typeof submitted.channelTransactionNo !== "string" || !submitted.channelTransactionNo.trim()
      || submitted.latestAttemptStatus !== "处理中"
      || submitted.latestAttemptStatusType !== "info"
      || submitted.latestAttemptCompletedAt != null) {
    throw new Error("本地支付提交缺少待回调受理事实");
  }
  return submitted;
}

export function browserCases(prepay, refund = null) {
  return [["App", 5175], ["H5", 5176], ["PC", 5177]].flatMap(([terminal, port]) => [
    { terminal, port, page: "cashier", label: "收银台", queryLabel: "主动查单", id: prepay.prepayOrderNo,
      apiPath: `/api/payments/cashier/${prepay.prepayOrderNo}`, amount: "¥88.00" },
    { terminal, port, page: "payment-result", label: "支付结果",
      queryLabel: { App: "查询最新状态", H5: "刷新支付结果", PC: "刷新桌面端支付结果" }[terminal],
      id: prepay.paymentOrderId,
      apiPath: `/api/payments/${prepay.paymentOrderId}`, amount: "¥88.00" },
    ...(refund ? [{ terminal, port, page: "refund-progress", label: "退款进度", id: refund.refundOrderId,
      apiPath: `/api/refunds/${refund.refundOrderId}`, amount: "¥18.00", status: refund.status }] : [])
  ]);
}

export function matchesDetailResponse(response, port, apiPath) {
  if (response.status !== 200 || response.method !== "GET") {
    return false;
  }
  const url = new URL(response.url);
  return url.origin === `http://localhost:${port}` && url.pathname === apiPath;
}

const delay = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));

async function waitFor(condition, label, describeFailure) {
  for (let attempt = 0; attempt < 100; attempt++) {
    if (await condition()) return;
    await delay(100);
  }
  throw new Error(`等待 ${label} 超时${describeFailure ? `：${await describeFailure()}` : ""}`);
}

export async function createDemoPrepay() {
  const orderNo = `LOCAL-BROWSER-${Date.now()}-${randomUUID().slice(0, 8)}`;
  const response = await fetch(`${paymentBaseUrl}/api/payments/prepay`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      orderNo, payScene: "HOME_CLEAN", customerName: "local browser QA",
      amount: 88, cashierTitle: "home service payment"
    }),
    redirect: "error",
    signal: AbortSignal.timeout(10000)
  });
  if (!response.ok) throw new Error(`本地预付请求失败：HTTP ${response.status}`);
  return assertDemoPrepay(await response.json(), orderNo);
}

async function verifyDemoBackend() {
  const response = await fetch(`${paymentBaseUrl}/api/demo/identity`, {
    redirect: "error", signal: AbortSignal.timeout(10000)
  });
  if (!response.ok) {
    throw new Error(`本地支付核心未确认 H2/demo 身份：HTTP ${response.status}`);
  }
  assertDemoBackend(await response.json());
}

export async function demoRequest(endpoint, payload) {
  const response = await fetch(`${paymentBaseUrl}${endpoint}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
    redirect: "error",
    signal: AbortSignal.timeout(10000)
  });
  const body = await response.json();
  if (!response.ok || !body?.data) throw new Error(`本地演示请求失败：${endpoint} HTTP ${response.status}`);
  return body.data;
}

async function createDemoRefund(prepay) {
  const submitPayload = demoSubmitPayload(prepay);
  await demoRequest("/api/payments/submit", submitPayload);
  const detailResponse = await fetch(`${paymentBaseUrl}/api/payments/${prepay.paymentOrderId}`,
    { redirect: "error", signal: AbortSignal.timeout(10000) });
  if (!detailResponse.ok) throw new Error("本地支付详情请求失败");
  const detail = (await detailResponse.json()).data;
  if (!detail?.channelTransactionNo) throw new Error("本地支付缺少渠道交易号，不能模拟成功回调");
  const paid = await demoRequest("/api/payments/callback/wx_h5", {
    paymentOrderId: prepay.paymentOrderId,
    channelTransactionNo: detail.channelTransactionNo,
    tradeStatus: "SUCCESS"
  });
  if (paid.status !== "SUCCESS") throw new Error("本地模拟支付未进入成功态");
  assertDemoRepeatedSubmit(prepay, paid, await demoRequest("/api/payments/submit", submitPayload));
  console.log("[PASS] 本地模拟支付成功后重复提交复用相同渠道与终态时间");
  const applied = await demoRequest("/api/refunds/apply", {
    paymentOrderId: prepay.paymentOrderId, idempotencyKey: `REFUND-${prepay.orderNo}`,
    refundAmount: 18, refundMethod: "原路退款", refundReason: "local demo browser QA",
    operator: "local-qa"
  });
  const approved = await demoRequest("/api/refunds/approve", {
    refundOrderId: applied.refundOrderId, remark: "local demo approved", operator: "local-reviewer"
  });
  return assertDemoRefund({ data: approved }, prepay);
}

function demoSubmitPayload(prepay, paymentMethod = "微信支付") {
  const alipay = paymentMethod === "支付宝";
  return {
    prepayOrderNo: prepay.prepayOrderNo, paymentMethod,
    channelCode: alipay ? "alipay_h5" : "wx_h5",
    sourceAppId: "housekeeping-h5-web", merchantNo: alipay ? "MCH_ALI_20001" : "MCH_WX_10001",
    accessToken: "token-housekeeping-h5-web", terminal: "H5",
    clientDeviceId: "local-refund-browser", payerPhone: "13800000000",
    idempotencyKey: prepay.orderNo
  };
}

async function createDemoPending(prepay) {
  return assertDemoPendingSubmit(prepay,
    await demoRequest("/api/payments/submit", demoSubmitPayload(prepay, "支付宝")));
}

async function finalizeDemoRefund(prepay, refund, terminalStatus) {
  const response = await fetch(`${paymentBaseUrl}/api/refunds/${refund.refundOrderId}`,
    { redirect: "error", signal: AbortSignal.timeout(10000) });
  if (!response.ok) throw new Error("本地退款详情请求失败");
  const detail = (await response.json()).data;
  const attempt = detail?.attempts?.[0];
  if (detail?.refundOrderId !== refund.refundOrderId
      || detail.paymentOrderId !== prepay.paymentOrderId
      || detail.status !== "PROCESSING"
      || !detail.channelCode || !detail.channelRefundNo
      || !attempt?.refundAttemptNo || attempt.channelRefundNo !== detail.channelRefundNo) {
    throw new Error("本地退款缺少当前渠道尝试与受理号，不能模拟终态回调");
  }
  const completed = await demoRequest(`/api/refunds/callback/${encodeURIComponent(detail.channelCode)}`, {
    refundOrderId: refund.refundOrderId,
    refundAttemptNo: attempt.refundAttemptNo,
    channelRefundNo: detail.channelRefundNo,
    refundStatus: terminalStatus
  });
  return assertDemoRefund({ data: completed }, prepay, terminalStatus);
}

async function runBrowser(prepay, refund = null, pending = null) {
  if (!existsSync(chromeBinary)) throw new Error(`未找到 Chrome：${chromeBinary}`);
  const profile = await mkdtemp(path.join(os.tmpdir(), "hsp-payment-browser-"));
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
    if (!target) throw new Error("Chrome 未创建页面目标");
    socket = new WebSocket(target.webSocketDebuggerUrl);
    await new Promise((resolve, reject) => {
      socket.addEventListener("open", resolve, { once: true });
      socket.addEventListener("error", reject, { once: true });
    });

    let commandId = 0;
    const pendingCommands = new Map();
    const methods = new Map();
    const responses = [];
    const paymentRequests = [];
    socket.addEventListener("message", ({ data }) => {
      const message = JSON.parse(data);
      if (message.method === "Network.requestWillBeSent") {
        const request = message.params.request;
        methods.set(message.params.requestId, request.method);
        if (/^\/api\/(payments|refunds)\//.test(new URL(request.url).pathname)) {
          paymentRequests.push(request.method);
        }
      }
      if (message.method === "Network.responseReceived") {
        responses.push({
          url: message.params.response.url,
          status: message.params.response.status,
          method: methods.get(message.params.requestId)
        });
      }
      const command = pendingCommands.get(message.id);
      if (!command) return;
      pendingCommands.delete(message.id);
      message.error ? command.reject(new Error(message.error.message)) : command.resolve(message.result);
    });
    const send = (method, params = {}) => new Promise((resolve, reject) => {
      const id = ++commandId;
      pendingCommands.set(id, { resolve, reject });
      socket.send(JSON.stringify({ id, method, params }));
    });
    const evaluate = async (expression) => {
      const result = await send("Runtime.evaluate", { expression, returnByValue: true });
      if (result.exceptionDetails) throw new Error(result.exceptionDetails.text);
      return result.result.value;
    };
    await send("Page.enable");
    await send("Runtime.enable");
    await send("Network.enable");

    for (const page of browserCases(prepay, refund)) {
      responses.length = 0;
      const pagePath = `/${page.page}/${page.id}`;
      await send("Page.navigate", {
        url: `http://localhost:${page.port}${pagePath}?payScene=HOME_CLEAN&prepayOrderNo=${prepay.prepayOrderNo}`
      });
      await waitFor(() => responses.some((item) => matchesDetailResponse(item, page.port, page.apiPath)),
        `${page.terminal} ${page.label} 详情 GET`);
      await waitFor(() => evaluate(`location.pathname === ${JSON.stringify(pagePath)}
        && document.body?.innerText.toUpperCase().includes(${JSON.stringify(`${page.terminal} ${page.label}`.toUpperCase())})
        && document.body?.innerText.includes(${JSON.stringify(prepay.orderNo)})
        && document.body?.innerText.includes(${JSON.stringify(page.amount)})
        && ${page.status ? `document.body?.innerText.includes(${JSON.stringify(page.status)})` : "true"}
        && !document.body?.innerText.includes("重试加载")`), `${page.terminal} ${page.label} 页面渲染`,
        () => evaluate("JSON.stringify({ path: location.pathname, text: document.body?.innerText.slice(0, 1000) })"));
      if (pending) {
        await waitFor(() => evaluate(`Array.from(document.querySelectorAll("button"))
          .some(button => button.textContent?.trim() === ${JSON.stringify(page.queryLabel)} && !button.disabled)`),
        `${page.terminal} ${page.label} 待回调查单入口`,
        () => evaluate(`JSON.stringify({ buttons: Array.from(document.querySelectorAll("button"))
          .map(button => ({ text: button.textContent?.trim(), disabled: button.disabled })),
          text: document.body?.innerText.slice(0, 1200) })`));
        if (page.page === "cashier") {
          await waitFor(() => evaluate(`Array.from(document.querySelectorAll(".channel-card.active"))
            .some(button => button.querySelector("strong")?.textContent?.trim() === "支付宝")
            && document.body?.innerText.includes(${JSON.stringify(pending.channelTransactionNo)})`),
          `${page.terminal} 收银台原支付方式与渠道流水`,
          () => evaluate(`JSON.stringify({ expected: ${JSON.stringify(pending.channelTransactionNo)}, selected: Array.from(document.querySelectorAll(".channel-card.active"))
            .map(button => button.textContent?.trim()), transactionVisible: document.body?.innerText
            .includes(${JSON.stringify(pending.channelTransactionNo)}), text: document.body?.innerText.slice(0, 1800) })`));
        }
      }
      console.log(`[PASS] ${page.terminal} ${page.label}${page.status ? ` ${page.status}` : ""}: 同源 200 GET，订单与金额已渲染`);
    }
    if (paymentRequests.some((method) => method !== "GET")) {
      throw new Error("浏览器页面意外发出了支付写请求");
    }
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
    throw new Error("仅限本地演示栈；确认已启动 demo profile 后显式传入 --demo");
  }
  const successDemo = process.argv.includes("--refund-success-demo");
  const failDemo = process.argv.includes("--refund-fail-demo");
  const pendingDemo = process.argv.includes("--pending-demo");
  if (pendingDemo && (process.argv.includes("--refund-demo") || successDemo || failDemo)) {
    throw new Error("待回调查单与退款演示不能同时选用");
  }
  if ((successDemo || failDemo) && !process.argv.includes("--refund-demo")) {
    throw new Error("退款终态验证需要先显式传入 --refund-demo");
  }
  if (successDemo && failDemo) {
    throw new Error("退款成功与失败终态验证不能同时选用");
  }
  if (!existsSync(chromeBinary)) throw new Error(`未找到 Chrome：${chromeBinary}`);
  await verifyDemoBackend();
  const prepay = await createDemoPrepay();
  const pending = pendingDemo ? await createDemoPending(prepay) : null;
  const refund = process.argv.includes("--refund-demo") ? await createDemoRefund(prepay) : null;
  await runBrowser(prepay, refund, pending);
  if (refund && (successDemo || failDemo)) {
    const completed = await finalizeDemoRefund(prepay, refund, successDemo ? "SUCCESS" : "FAIL");
    await runBrowser(prepay, completed);
    console.log(`[PASS] App/H5/PC 三端同源退款处理中至${successDemo ? "成功" : "失败"}终态浏览器冒烟通过`);
  } else {
    console.log(pendingDemo
      ? "[PASS] App/H5/PC 六页本机待回调查单按钮已启用；浏览器未提交支付写请求"
      : refund
      ? "[PASS] App/H5/PC 九页本地模拟支付及处理中退款只读浏览器冒烟通过"
      : "[PASS] App/H5/PC 六页只读详情浏览器冒烟通过；未提交支付");
  }
}
