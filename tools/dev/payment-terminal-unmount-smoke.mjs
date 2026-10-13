#!/usr/bin/env node

import { spawn } from "node:child_process";
import { existsSync } from "node:fs";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { createServer } from "node:http";
import { createServer as createTcpServer } from "node:net";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const appDirectory = path.join(root, "systems/payment-core/frontend/app-web");
const chromeBinary = process.env.HSP_CHROME_BIN
  || "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const refundPath = "/api/refunds/REF-UNMOUNT-SMOKE";
const refundDetail = {
  refundOrderId: "REF-UNMOUNT-SMOKE", paymentOrderId: "PAY-UNMOUNT-SMOKE",
  businessChainId: "CHAIN-PAY-UNMOUNT-SMOKE", orderNo: "ORDER-UNMOUNT-SMOKE",
  paidAmount: "68.00", refundAmount: "18.00", refundMethod: "原路退款",
  refundReason: "local browser lifecycle smoke", status: "PROCESSING", statusType: "warn",
  payScene: "BALANCE_PAY_APP", appliedAt: "2026-09-27 12:00:00",
  attempts: [], operationLogs: []
};

const delay = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));

async function waitFor(predicate, label, timeoutMs = 12000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await predicate()) return;
    await delay(100);
  }
  throw new Error(`Timed out waiting for ${label}`);
}

async function freePort() {
  const server = createTcpServer();
  await new Promise((resolve, reject) => server.once("error", reject).listen(0, "127.0.0.1", resolve));
  const port = server.address().port;
  await new Promise((resolve) => server.close(resolve));
  return port;
}

async function stopChild(child) {
  if (!child || child.exitCode !== null || child.signalCode !== null) return;
  child.kill("SIGTERM");
  await Promise.race([
    new Promise((resolve) => child.once("exit", resolve)),
    delay(5000)
  ]);
  if (child.exitCode === null && child.signalCode === null) child.kill("SIGKILL");
}

async function main() {
  if (!existsSync(chromeBinary)) throw new Error(`Chrome not found: ${chromeBinary}`);
  const profile = await mkdtemp(path.join(os.tmpdir(), "hsp-terminal-unmount-"));
  let calls = 0;
  let releaseDelayedResponse;
  let vite;
  let chrome;
  let socket;
  const api = createServer((incoming, outgoing) => {
    if (incoming.method !== "GET" || incoming.url !== refundPath) {
      outgoing.writeHead(404).end();
      return;
    }
    calls += 1;
    const respond = () => {
      outgoing.writeHead(200, { "Content-Type": "application/json", "Cache-Control": "no-store" });
      outgoing.end(JSON.stringify({ code: "0", data: refundDetail }));
    };
    if (calls === 2) releaseDelayedResponse = respond;
    else respond();
  });
  try {
    await new Promise((resolve, reject) => api.once("error", reject).listen(0, "127.0.0.1", resolve));
    const apiPort = api.address().port;
    const vitePort = await freePort();
    vite = spawn(path.join(appDirectory, "node_modules/.bin/vite"),
      ["--host", "127.0.0.1", "--port", String(vitePort), "--strictPort"], {
        cwd: appDirectory,
        env: { ...process.env, HSP_PAYMENT_CORE_API_TARGET: `http://127.0.0.1:${apiPort}` },
        stdio: "ignore"
      });
    await waitFor(async () => {
      if (vite.exitCode !== null) throw new Error(`Vite exited: ${vite.exitCode}`);
      try { return (await fetch(`http://127.0.0.1:${vitePort}/`)).ok; } catch { return false; }
    }, "local Vite app");

    chrome = spawn(chromeBinary, [
      "--headless", "--disable-gpu", "--disable-background-networking",
      "--disable-component-update", "--no-first-run", "--no-default-browser-check",
      "--no-proxy-server", "--remote-allow-origins=*", "--remote-debugging-port=0",
      `--user-data-dir=${profile}`, "about:blank"
    ], { stdio: "ignore" });
    let debugPort;
    await waitFor(async () => {
      if (chrome.exitCode !== null) throw new Error(`Chrome exited: ${chrome.exitCode}`);
      try {
        debugPort = (await readFile(path.join(profile, "DevToolsActivePort"), "utf8")).split("\n")[0];
        return Boolean(debugPort);
      } catch { return false; }
    }, "Chrome debugging port");
    const targets = await (await fetch(`http://127.0.0.1:${debugPort}/json/list`)).json();
    const page = targets.find((target) => target.type === "page");
    if (!page) throw new Error("Chrome page target missing");
    socket = new WebSocket(page.webSocketDebuggerUrl);
    await new Promise((resolve, reject) => {
      socket.addEventListener("open", resolve, { once: true });
      socket.addEventListener("error", reject, { once: true });
    });
    let commandId = 0;
    const pendingCommands = new Map();
    let detailResponses = 0;
    socket.addEventListener("message", ({ data }) => {
      const message = JSON.parse(data);
      if (message.method === "Network.responseReceived"
          && new URL(message.params.response.url).pathname === refundPath) detailResponses += 1;
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
    await send("Page.navigate", { url: `http://127.0.0.1:${vitePort}/refund-progress/REF-UNMOUNT-SMOKE?payScene=BALANCE_PAY_APP` });
    await waitFor(() => evaluate(`document.body?.innerText.includes("退款处理中")
      && Array.from(document.querySelectorAll("button"))
        .some(button => button.textContent?.trim() === "返回支付入口" && !button.disabled)`),
    "rendered refund progress");
    await waitFor(() => calls === 2 && typeof releaseDelayedResponse === "function",
      "delayed automatic refund refresh");
    const clicked = await evaluate(`(() => {
      const button = Array.from(document.querySelectorAll("button"))
        .find(item => item.textContent?.trim() === "返回支付入口" && !item.disabled);
      button?.click();
      return Boolean(button);
    })()`);
    if (!clicked) throw new Error("Refund progress return action unavailable");
    await waitFor(() => evaluate("location.pathname === '/balance-pay'"), "SPA navigation away from refund progress");
    releaseDelayedResponse();
    releaseDelayedResponse = null;
    await waitFor(() => detailResponses === 2, "delayed response delivered to Chrome");
    await delay(8500);
    if (calls !== 2) throw new Error(`Refund polling resumed after unmount: ${calls} requests`);
    console.log("[PASS] App refund progress: delayed response after SPA unmount did not restart polling");
  } finally {
    releaseDelayedResponse?.();
    socket?.close();
    await stopChild(chrome);
    await stopChild(vite);
    if (api.listening) await new Promise((resolve) => api.close(resolve));
    await rm(profile, { recursive: true, force: true });
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
