#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { existsSync } from "node:fs";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { createServer } from "node:http";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../systems/payment-core/frontend/app-web/dist");
const chromeBinary = process.env.HSP_CHROME_BIN
  || "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const paymentIds = {
  query: "PAY-RESULT-QUERY-001", close: "PAY-RESULT-CLOSE-001",
  submit: "PAY-RESULT-SUBMIT-001", submitOther: "PAY-RESULT-SUBMIT-002"
};
const delay = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));

async function waitFor(condition, label) {
  for (let attempt = 0; attempt < 100; attempt += 1) {
    if (await condition()) return;
    await delay(100);
  }
  throw new Error(`Timed out waiting for ${label}`);
}

function paymentDetail(kind, status) {
  const paymentOrderId = paymentIds[kind];
  const submitted = kind !== "close" && status !== "PREPAY_CREATED";
  return {
    paymentOrderId, businessChainId: `CHAIN-${paymentOrderId}`,
    prepayOrderNo: `PRE-${paymentOrderId}`, orderNo: `ORD-${paymentOrderId}`,
    payScene: "HOME_CLEAN", amount: "68.00", paymentMethod: "支付宝",
    customerName: "本机用户", channel: submitted ? "alipay_h5" : null,
    channelTransactionNo: submitted ? "ALI-LOCAL-001" : null,
    status, statusType: status === "SUCCESS" ? "success" : status === "CLOSED" ? "danger"
      : status === "WAIT_CALLBACK" ? "warn" : "info",
    successAt: status === "SUCCESS" ? "2026-10-10 10:00:03" : null,
    createdAt: "2026-10-10 10:00:00",
    latestAttemptStatus: submitted ? status === "SUCCESS" ? "成功" : "处理中" : null,
    latestAttemptStatusType: submitted ? status === "SUCCESS" ? "success" : "info" : null,
    latestAttemptCompletedAt: status === "SUCCESS" ? "2026-10-10 10:00:02" : null,
    routeLogs: [], notifyLogs: [], eventLogs: []
  };
}

function cashierPage(kind, status) {
  const payment = paymentDetail(kind, status);
  return {
    ...payment,
    paymentStatus: status,
    status: status === "SUCCESS" ? "支付成功" : status === "CLOSED" ? "已关闭"
      : status === "WAIT_CALLBACK" ? "待回调" : "待支付",
    statusType: status === "SUCCESS" ? "success" : status === "CLOSED" ? "danger" : "warn",
    paymentMethod: kind !== "close" && status !== "PREPAY_CREATED" ? "支付宝" : null,
    expiresAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000)
      .toISOString().slice(0, 19).replace("T", " "),
    channels: ["支付宝"]
  };
}

async function main() {
  assert.ok(existsSync(path.join(root, "index.html")), "Build app-web before running this smoke");
  assert.ok(existsSync(chromeBinary), `Chrome not found: ${chromeBinary}`);
  let kind = "query";
  let status = "WAIT_CALLBACK";
  let failReadback = false;
  let retrySubmit = false;
  const retrySubmitBodies = new Map();
  const requests = [];
  const server = createServer(async (request, response) => {
    const pathname = new URL(request.url, "http://localhost").pathname;
    if (pathname.startsWith("/api/")) {
      requests.push({ method: request.method, pathname });
      response.setHeader("Content-Type", "application/json; charset=utf-8");
      if (request.method === "GET" && [
        `/api/payments/${paymentIds[kind]}`,
        `/api/payments/cashier/PRE-${paymentIds[kind]}`
      ].includes(pathname)) {
        if (failReadback && status !== (kind === "query" ? "WAIT_CALLBACK" : "PREPAY_CREATED")) {
          failReadback = false;
          response.statusCode = 503;
          response.end(JSON.stringify({ code: "PAYMENT-503", message: "临时不可用" }));
        } else {
          const data = pathname.includes("/cashier/")
            ? cashierPage(kind, status) : paymentDetail(kind, status);
          if (retrySubmit && pathname.includes("/cashier/")) {
            data.channels = ["支付宝", "微信支付"];
          }
          response.end(JSON.stringify({ code: "0", data }));
        }
      } else if (request.method === "POST" && pathname === `/api/payments/${kind === "submitOther" ? "submit" : kind}`) {
        let input = "";
        for await (const chunk of request) input += chunk;
        const body = JSON.parse(input);
        if (kind === "submit" || kind === "submitOther") {
          assert.equal(body.prepayOrderNo, `PRE-${paymentIds[kind]}`);
          assert.equal(body.paymentMethod, "支付宝");
          assert.equal(body.accessToken, "local-test-token");
          assert.ok(typeof body.idempotencyKey === "string" && body.idempotencyKey.length > 0);
          if (retrySubmit) {
            const attempts = retrySubmitBodies.get(body.prepayOrderNo) || [];
            if (attempts.length) {
              assert.deepEqual(body, attempts[0], "刷新后必须用原支付请求键和内容重试");
            }
            attempts.push(body);
            retrySubmitBodies.set(body.prepayOrderNo, attempts);
            assert.ok(attempts.length <= 2);
            if (attempts.length === 2) {
              status = "WAIT_CALLBACK";
              response.end(JSON.stringify({ code: "0", data: {
                ...cashierPage(kind, status), channel: body.channelCode,
                cashierStatus: "支付中", cashierStatusType: "info"
              } }));
            } else {
              response.statusCode = 400;
              response.end(JSON.stringify({ code: "PAYMENT-400", message: "动作响应丢失" }));
            }
            return;
          }
        } else {
          assert.deepEqual(body, { paymentOrderId: paymentIds[kind] });
        }
        status = kind === "query" ? "SUCCESS" : kind === "close" ? "CLOSED" : "WAIT_CALLBACK";
        response.statusCode = 400;
        response.end(JSON.stringify({ code: "PAYMENT-400", message: "动作响应丢失" }));
      } else {
        response.writeHead(404).end();
      }
      return;
    }
    const file = pathname.startsWith("/assets/")
      ? path.resolve(root, `.${pathname}`) : path.join(root, "index.html");
    if (!file.startsWith(`${root}${path.sep}`)) {
      response.writeHead(403).end();
      return;
    }
    try {
      const content = await readFile(file);
      response.setHeader("Content-Type", file.endsWith(".js") ? "text/javascript"
        : file.endsWith(".css") ? "text/css" : "text/html");
      response.end(content);
    } catch {
      response.writeHead(404).end();
    }
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const baseUrl = `http://127.0.0.1:${server.address().port}`;
  const profile = await mkdtemp(path.join(os.tmpdir(), "hsp-result-refresh-"));
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
    }, "Chrome debug port");
    const targets = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
    socket = new WebSocket(targets.find((target) => target.type === "page").webSocketDebuggerUrl);
    await new Promise((resolve, reject) => {
      socket.addEventListener("open", resolve, { once: true });
      socket.addEventListener("error", reject, { once: true });
    });
    let commandId = 0;
    const pending = new Map();
    socket.addEventListener("message", ({ data }) => {
      const message = JSON.parse(data);
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
    const actionButton = (label) => `Array.from(document.querySelectorAll('.terminal-actions button'))
      .find((button) => button.textContent.trim() === '${label}')`;
    await send("Page.enable");
    await send("Runtime.enable");
    for (const scenario of ["query", "close"]) {
      for (const readbackFails of [false, true]) {
        kind = scenario;
        status = kind === "query" ? "WAIT_CALLBACK" : "PREPAY_CREATED";
        failReadback = readbackFails;
        const start = requests.length;
        await send("Page.navigate", { url: `${baseUrl}/payment-result/${paymentIds[kind]}?payScene=HOME_CLEAN` });
        const label = kind === "query" ? "查询最新状态" : "关闭支付单";
        await waitFor(() => evaluate(`Boolean(${actionButton(label)})`), "initial result action");
        assert.equal(await evaluate(`${actionButton(label)}.disabled`), false);
        await evaluate(`${actionButton(label)}.click()`);
        if (readbackFails) {
          await waitFor(() => evaluate("document.body.innerText.includes('支付状态未经确认，请重新加载详情，勿重复付款')"), "unverified result");
          assert.equal(await evaluate("document.querySelector('.terminal-actions') === null"), true);
          const retry = "Array.from(document.querySelectorAll('button')).find((button) => button.textContent.trim() === '重试加载支付结果')";
          await evaluate(`${retry}.click()`);
        }
        const expectedTitle = kind === "query" ? "支付成功" : "支付已关闭";
        await waitFor(() => evaluate(`document.querySelector('.terminal-hero h1')?.textContent.trim() === '${expectedTitle}'`), "updated result");
        assert.equal(await evaluate("document.body.innerText.includes('动作响应丢失 [PAYMENT-400]')"), !readbackFails);
        assert.equal(requests.slice(start).filter((item) => item.method === "POST").length, 1);
        assert.ok(requests.slice(start).filter((item) => item.method === "GET"
          && item.pathname === `/api/payments/${paymentIds[kind]}`).length >= (readbackFails ? 3 : 2));
      }
    }
    for (const scenario of ["query", "close"]) {
      for (const readbackFails of [false, true]) {
        kind = scenario;
        status = kind === "query" ? "WAIT_CALLBACK" : "PREPAY_CREATED";
        failReadback = readbackFails;
        const start = requests.length;
        await send("Page.navigate", { url: `${baseUrl}/cashier/PRE-${paymentIds[kind]}?payScene=HOME_CLEAN` });
        const label = kind === "query" ? "主动查单" : "关闭本次支付";
        await waitFor(() => evaluate(`Boolean(${actionButton(label)})`), "initial cashier action");
        assert.equal(await evaluate(`${actionButton(label)}.disabled`), false);
        await evaluate(`${actionButton(label)}.click()`);
        if (readbackFails) {
          await waitFor(() => evaluate("document.body.innerText.includes('支付状态未经确认，请刷新收银台，勿重复付款')"), "unverified cashier");
          assert.equal(await evaluate(`${actionButton(label)}.disabled`), true);
          assert.equal(await evaluate(`${actionButton('重置幂等键')}.disabled`), true);
          await evaluate(`${actionButton('刷新收银台')}.click()`);
        }
        const expectedTitle = kind === "query" ? "支付成功" : "支付已关闭";
        await waitFor(() => evaluate(`location.pathname === '/payment-result/${paymentIds[kind]}'
          && document.querySelector('.terminal-hero h1')?.textContent.trim() === '${expectedTitle}'`), "cashier recovery result");
        assert.equal(requests.slice(start).filter((item) => item.method === "POST").length, 1);
        assert.ok(requests.slice(start).filter((item) => item.method === "GET"
          && item.pathname === `/api/payments/cashier/PRE-${paymentIds[kind]}`).length >= (readbackFails ? 3 : 2));
      }
    }
    for (const readbackFails of [false, true]) {
      kind = "submit";
      status = "PREPAY_CREATED";
      failReadback = readbackFails;
      const start = requests.length;
      await send("Page.navigate", { url: `${baseUrl}/cashier/PRE-${paymentIds[kind]}?payScene=HOME_CLEAN&accessToken=local-test-token` });
      await waitFor(() => evaluate(`Boolean(${actionButton('确认支付')}) && !${actionButton('确认支付')}.disabled`), "submit action");
      await evaluate(`${actionButton('确认支付')}.click()`);
      if (readbackFails) {
        await waitFor(() => evaluate("document.body.innerText.includes('支付状态未经确认，请刷新收银台，勿重复付款')"), "unverified submit");
        assert.equal(await evaluate(`${actionButton('确认支付')}.disabled`), true);
        assert.equal(await evaluate(`${actionButton('主动查单')}.disabled`), true);
        assert.equal(await evaluate(`${actionButton('重置幂等键')}.disabled`), true);
        await evaluate(`${actionButton('刷新收银台')}.click()`);
      }
      await waitFor(() => evaluate(`document.body.innerText.includes('待回调')
        && !${actionButton('主动查单')}.disabled`), "pending submit recovery");
      assert.equal(await evaluate(`${actionButton('确认支付')}.disabled`), true);
      assert.equal(await evaluate(`${actionButton('重置幂等键')}.disabled`), true);
      assert.equal(requests.slice(start).filter((item) => item.method === "POST").length, 1);
      assert.ok(requests.slice(start).filter((item) => item.method === "GET"
        && item.pathname === `/api/payments/cashier/PRE-${paymentIds[kind]}`).length >= (readbackFails ? 3 : 2));
    }
    kind = "submitOther";
    status = "PREPAY_CREATED";
    failReadback = false;
    const ignoredWrite = await send("Page.addScriptToEvaluateOnNewDocument", {
      source: `(() => {
        const original = Storage.prototype.setItem;
        Object.defineProperty(Storage.prototype, 'setItem', { configurable: true, value(key, value) {
          if (key.startsWith('hsp.paymentSubmitAttempt.v1.')) return;
          return original.call(this, key, value);
        } });
      })();`
    });
    const ignoredWriteStart = requests.length;
    await send("Page.navigate", { url: `${baseUrl}/cashier/PRE-${paymentIds[kind]}?payScene=HOME_CLEAN&accessToken=local-test-token` });
    await waitFor(() => evaluate(`Boolean(${actionButton('确认支付')}) && !${actionButton('确认支付')}.disabled`),
      "cashier with ignored pending-key write");
    const pendingKeysBeforeSubmit = await evaluate("Object.keys(sessionStorage).filter((key) => key.startsWith('hsp.paymentSubmitAttempt.v1.')).length");
    await evaluate(`${actionButton('确认支付')}.click()`);
    await waitFor(() => evaluate("document.body.innerText.includes('未决支付记录无法核实')"),
      "ignored pending-key write blocks payment");
    assert.equal(requests.slice(ignoredWriteStart).filter((item) => item.method === "POST").length, 0);
    assert.equal(await evaluate("Object.keys(sessionStorage).filter((key) => key.startsWith('hsp.paymentSubmitAttempt.v1.')).length"),
      pendingKeysBeforeSubmit);
    await send("Page.removeScriptToEvaluateOnNewDocument", { identifier: ignoredWrite.identifier });
    kind = "submit";
    status = "PREPAY_CREATED";
    failReadback = false;
    retrySubmit = true;
    const retryStart = requests.length;
    const retryUrl = `${baseUrl}/cashier/PRE-${paymentIds[kind]}?payScene=HOME_CLEAN&accessToken=local-test-token`;
    await send("Page.navigate", { url: retryUrl });
    await waitFor(() => evaluate(`Boolean(${actionButton('确认支付')}) && !${actionButton('确认支付')}.disabled`),
      "initial retryable cashier");
    await evaluate(`${actionButton('确认支付')}.click()`);
    await waitFor(() => evaluate(`document.body.innerText.includes('动作响应丢失 [PAYMENT-400]')
      && !${actionButton('确认支付')}.disabled`), "failed submit with unchanged cashier");
    assert.equal(retrySubmitBodies.get(`PRE-${paymentIds[kind]}`).length, 1);
    assert.equal(await evaluate(`${actionButton('重置幂等键')}.disabled`), true);
    assert.equal(await evaluate("Array.from(document.querySelectorAll('.channel-card')).every((button) => button.disabled)"), true);
    const firstSubmitKey = retrySubmitBodies.get(`PRE-${paymentIds[kind]}`)[0].idempotencyKey;
    kind = "submitOther";
    status = "PREPAY_CREATED";
    const otherUrl = `${baseUrl}/cashier/PRE-${paymentIds[kind]}?payScene=HOME_CLEAN&accessToken=local-test-token`;
    await send("Page.navigate", { url: otherUrl });
    await waitFor(() => evaluate(`Boolean(${actionButton('确认支付')}) && !${actionButton('确认支付')}.disabled`),
      "other prepay cashier");
    await evaluate(`${actionButton('确认支付')}.click()`);
    await waitFor(() => evaluate(`document.body.innerText.includes('动作响应丢失 [PAYMENT-400]')
      && !${actionButton('确认支付')}.disabled`), "other prepay unresolved submit");
    assert.equal(retrySubmitBodies.get(`PRE-${paymentIds[kind]}`).length, 1);
    const otherSubmitKey = retrySubmitBodies.get(`PRE-${paymentIds[kind]}`)[0].idempotencyKey;
    assert.notEqual(otherSubmitKey, firstSubmitKey);
    kind = "submit";
    status = "PREPAY_CREATED";
    await send("Page.navigate", { url: retryUrl });
    await waitFor(() => evaluate(`Boolean(${actionButton('确认支付')}) && !${actionButton('确认支付')}.disabled`),
      "refreshed retryable cashier");
    assert.equal(await evaluate(`document.body.innerText.includes('${firstSubmitKey}')`), true);
    assert.equal(await evaluate(`${actionButton('重置幂等键')}.disabled`), true);
    assert.equal(await evaluate("Array.from(document.querySelectorAll('.channel-card')).every((button) => button.disabled)"), true);
    await evaluate(`${actionButton('确认支付')}.click()`);
    try {
      await waitFor(() => evaluate(`location.pathname === '/payment-result/${paymentIds[kind]}'`),
        "replayed submit result");
    } catch (error) {
      throw new Error(`${error.message}; requests=${retrySubmitBodies.size}; page=${await evaluate("document.body.innerText.slice(0, 1200)")}`);
    }
    assert.equal(retrySubmitBodies.get(`PRE-${paymentIds[kind]}`).length, 2);
    kind = "submitOther";
    status = "PREPAY_CREATED";
    await send("Page.navigate", { url: otherUrl });
    await waitFor(() => evaluate(`Boolean(${actionButton('确认支付')}) && !${actionButton('确认支付')}.disabled`),
      "restored other prepay cashier");
    assert.equal(await evaluate(`document.body.innerText.includes('${otherSubmitKey}')`), true);
    assert.equal(await evaluate(`${actionButton('重置幂等键')}.disabled`), true);
    assert.equal(requests.slice(retryStart).filter((item) => item.method === "POST").length, 3);
    const digestFailure = await send("Page.addScriptToEvaluateOnNewDocument", {
      source: "Object.defineProperty(SubtleCrypto.prototype, 'digest', { value: () => Promise.reject(new Error('digest unavailable')), configurable: true });"
    });
    await send("Page.navigate", { url: otherUrl });
    await waitFor(() => evaluate(`document.body.innerText.includes('未决支付记录无法核实')
      && document.querySelector('.terminal-actions') === null`), "digest failure blocks reloaded cashier");
    assert.equal(requests.slice(retryStart).filter((item) => item.method === "POST").length, 3);
    assert.equal(await evaluate(`Object.values(sessionStorage).some((value) => value.includes('${otherSubmitKey}'))`), true);
    await send("Page.removeScriptToEvaluateOnNewDocument", { identifier: digestFailure.identifier });
    await send("Page.navigate", { url: otherUrl });
    await waitFor(() => evaluate(`Boolean(${actionButton('确认支付')}) && !${actionButton('确认支付')}.disabled`),
      "restored cashier after digest recovery");
    assert.equal(await evaluate(`document.body.innerText.includes('${otherSubmitKey}')`), true);
    const storageFailure = await send("Page.addScriptToEvaluateOnNewDocument", {
      source: "Object.defineProperty(Storage.prototype, 'getItem', { value: () => { throw new Error('storage unavailable'); }, configurable: true });"
    });
    await send("Page.navigate", { url: otherUrl });
    await waitFor(() => evaluate(`document.body.innerText.includes('未决支付记录无法核实')
      && document.querySelector('.terminal-actions') === null`), "storage read failure blocks reloaded cashier");
    assert.equal(requests.slice(retryStart).filter((item) => item.method === "POST").length, 3);
    assert.equal(await evaluate(`Object.values(sessionStorage).some((value) => value.includes('${otherSubmitKey}'))`), true);
    await send("Page.removeScriptToEvaluateOnNewDocument", { identifier: storageFailure.identifier });
    await send("Page.navigate", { url: otherUrl });
    await waitFor(() => evaluate(`Boolean(${actionButton('确认支付')}) && !${actionButton('确认支付')}.disabled`),
      "restored cashier after storage recovery");
    assert.equal(await evaluate(`document.body.innerText.includes('${otherSubmitKey}')`), true);
    assert.equal(await evaluate(`(() => {
      const keys = Object.keys(sessionStorage).filter((key) => key.startsWith('hsp.paymentSubmitAttempt.v1.'));
      if (keys.length !== 1) return false;
      sessionStorage.setItem(keys[0], '{broken-json');
      return true;
    })()`), true);
    await evaluate(`${actionButton('确认支付')}.click()`);
    await waitFor(() => evaluate(`document.body.innerText.includes('未决支付记录无法核实')
      && ${actionButton('确认支付')}.disabled`), "corrupt attempt blocks in-page submit");
    assert.equal(requests.slice(retryStart).filter((item) => item.method === "POST").length, 3);
    await send("Page.navigate", { url: otherUrl });
    await waitFor(() => evaluate(`document.body.innerText.includes('未决支付记录无法核实')
      && document.querySelector('.terminal-actions') === null`), "corrupt attempt blocks reloaded cashier");
    assert.equal(requests.slice(retryStart).filter((item) => item.method === "POST").length, 3);
    status = "SUCCESS";
    await send("Page.navigate", { url: otherUrl });
    await waitFor(() => evaluate(`location.pathname === '/payment-result/${paymentIds[kind]}'
      && document.body.innerText.includes('支付成功')`), "final result despite corrupt pending attempt");
    assert.equal(requests.slice(retryStart).filter((item) => item.method === "POST").length, 3);
    assert.equal(await evaluate(`Object.entries(sessionStorage).some(([key, value]) =>
      key.startsWith('hsp.paymentSubmitAttempt.v1.') && value === '{broken-json')`), true);
    console.log("[PASS] User result and cashier actions, including corrupt attempt and final result");
  } finally {
    socket?.close();
    chrome.kill("SIGTERM");
    await Promise.race([new Promise((resolve) => chrome.once("exit", resolve)), delay(3000)]);
    if (chrome.exitCode == null) chrome.kill("SIGKILL");
    await rm(profile, { recursive: true, force: true });
    await new Promise((resolve) => server.close(resolve));
  }
}

await main();
