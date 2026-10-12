import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { createServer } from "node:http";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

const scriptPath = fileURLToPath(new URL("../../infra/rabbitmq/declare_isolated_topology.sh", import.meta.url));

async function runTopology(suffix, options = {}) {
  const requests = [];
  const server = createServer(async (request, response) => {
    const chunks = [];
    for await (const chunk of request) {
      chunks.push(chunk);
    }
    requests.push({ method: request.method, path: request.url, body: JSON.parse(Buffer.concat(chunks).toString()) });
    response.writeHead(200, { "Content-Type": "application/json" });
    response.end("{}");
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  try {
    const result = await new Promise((resolve) => {
      const child = spawn("bash", [scriptPath, suffix], {
        env: {
          ...process.env,
          RABBIT_HOST: "127.0.0.1",
          RABBIT_PORT: String(server.address().port),
          RABBIT_USER: "hsp",
          RABBIT_PASSWORD: "local-test-only",
          RABBIT_VHOST: "/",
          ...options
        },
        stdio: ["ignore", "pipe", "pipe"]
      });
      const stdout = [];
      const stderr = [];
      child.stdout.on("data", (chunk) => stdout.push(chunk));
      child.stderr.on("data", (chunk) => stderr.push(chunk));
      child.on("close", (exitCode) => resolve({
        exitCode,
        stdout: Buffer.concat(stdout).toString(),
        stderr: Buffer.concat(stderr).toString()
      }));
    });
    return { ...result, requests };
  } finally {
    server.close();
  }
}

test("isolated topology prints live producer and consumer settings", async () => {
  const result = await runTopology("drill-a");
  assert.equal(result.exitCode, 0, result.stderr);
  assert.match(result.stdout, /HSP_PAYMENT_EVENT_DISPATCH_MODE=amqp/);
  assert.match(result.stdout, /HSP_PAYMENT_AMQP_PAYMENT_SUCCESS_ROUTING_KEY=payment\.success\.drill-a\.v1/);
  assert.match(result.stdout, /HSP_CLEARING_AMQP_DISPATCH_MODE=amqp/);
  assert.match(result.stdout, /HSP_PAYMENT_AMQP_DEAD_LETTER_TASK_ENABLED=true/);
  assert.doesNotMatch(result.stdout, /HSP_PAYMENT_EVENT_ROUTING_KEY=/);
  assert.equal(result.requests.filter((request) => request.method === "PUT").length, 12);
  assert.equal(result.requests.filter((request) => request.method === "POST").length, 20);
  assert.ok(result.requests.filter((request) => request.method === "PUT")
    .every((request) => request.body.durable === true && request.body.auto_delete === false));
  assert.ok(result.requests.some((request) => request.body.routing_key === "payment.success.drill-a.v1"));
  assert.ok(result.requests.some((request) => request.body.routing_key === "clearing.generated.drill-a.v1"));
});

test("invalid suffix and retry TTL cannot write broker topology", async () => {
  for (const [suffix, options, error] of [
    ["../shared", {}, /suffix must be/],
    ["drill-a", { RETRY_TTL_MS: "0" }, /RETRY_TTL_MS must be/],
    ["drill-a", { RETRY_TTL_MS: "300000,\"injected\":true" }, /RETRY_TTL_MS must be/]
  ]) {
    const result = await runTopology(suffix, options);
    assert.notEqual(result.exitCode, 0);
    assert.match(result.stderr, error);
    assert.equal(result.requests.length, 0);
  }
});
