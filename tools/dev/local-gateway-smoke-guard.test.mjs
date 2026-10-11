import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import test from "node:test";
import { fileURLToPath } from "node:url";
import { assertLocalGatewaySmokeEnvironment } from "./local-gateway-smoke-guard.mjs";

test("allows only local toolchain and query race controls", () => {
  assert.doesNotThrow(() => assertLocalGatewaySmokeEnvironment({
    HSP_MAVEN_BIN: "/opt/maven/bin/mvn",
    HSP_MAVEN_REPO: "/tmp/maven-repo",
    HSP_JAVA_HOME: "/opt/jdk",
    HSP_QUERY_RACE_MODE: "conflicting"
  }));
});

test("rejects inherited service, datasource, JVM and proxy overrides without exposing values", () => {
  for (const name of ["SPRING_DATASOURCE_URL", "HSP_PAYMENT_CHANNEL_QUERY_GATEWAY_URL",
    "HSP_RABBITMQ_HOST", "GATEWAY_TRANSACTION_ACCESS_KEY", "DATABASE_URL",
    "JAVA_TOOL_OPTIONS", "MAVEN_OPTS", "NODE_USE_ENV_PROXY", "HTTPS_PROXY"]) {
    assert.throws(
      () => assertLocalGatewaySmokeEnvironment({ [name]: "sensitive-value" }),
      (error) => error.message.includes(name) && !error.message.includes("sensitive-value")
    );
  }
});

test("both gateway smoke entry points refuse overrides before starting services", () => {
  for (const script of ["payment-core-gateway-query-smoke.mjs", "payment-core-gateway-refund-smoke.mjs"]) {
    const result = spawnSync(process.execPath, [fileURLToPath(new URL(script, import.meta.url))], {
      env: { PATH: process.env.PATH, SPRING_DATASOURCE_URL: "jdbc:mysql://example.invalid/unsafe" },
      encoding: "utf8",
      timeout: 5000
    });
    assert.equal(result.status, 1, `${script}: ${result.stderr}`);
    assert.match(result.stderr, /refuses environment override: SPRING_DATASOURCE_URL/);
    assert.doesNotMatch(result.stderr, /jdbc:mysql:/);
  }
});
