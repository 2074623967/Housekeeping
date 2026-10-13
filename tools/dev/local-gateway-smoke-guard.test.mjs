import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import test from "node:test";
import { fileURLToPath } from "node:url";
import { assertLocalGatewaySmokeEnvironment } from "./local-gateway-smoke-guard.mjs";

test("allows local toolchain controls", () => {
  assert.doesNotThrow(() => assertLocalGatewaySmokeEnvironment({
    HSP_MAVEN_BIN: "/opt/maven/bin/mvn",
    HSP_MAVEN_REPO: "/tmp/maven-repo",
    HSP_JAVA_HOME: "/opt/jdk"
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

test("allows only the explicit query race variant on the query smoke", () => {
  assert.doesNotThrow(() => assertLocalGatewaySmokeEnvironment(
    { HSP_QUERY_RACE_MODE: "conflicting" }, { allowQueryRaceMode: true }
  ));
  for (const [value, options] of [["conflict", { allowQueryRaceMode: true }],
    ["conflicting", undefined]]) {
    assert.throws(
      () => assertLocalGatewaySmokeEnvironment({ HSP_QUERY_RACE_MODE: value }, options),
      /HSP_QUERY_RACE_MODE/
    );
  }
});

test("gateway smoke entry points refuse overrides before starting services", () => {
  for (const script of ["payment-core-gateway-query-smoke.mjs", "payment-core-gateway-refund-smoke.mjs",
    "payment-core-gateway-submit-smoke.mjs"]) {
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

test("unsupported query race modes fail before startup", () => {
  for (const [script, mode] of [["payment-core-gateway-query-smoke.mjs", "conflict"],
    ["payment-core-gateway-refund-smoke.mjs", "conflicting"],
    ["payment-core-gateway-submit-smoke.mjs", "conflicting"]]) {
    const result = spawnSync(process.execPath, [fileURLToPath(new URL(script, import.meta.url))], {
      env: { PATH: process.env.PATH, HSP_QUERY_RACE_MODE: mode },
      encoding: "utf8",
      timeout: 5000
    });
    assert.equal(result.status, 1, `${script}: ${result.stderr}`);
    assert.match(result.stderr, /refuses environment override: HSP_QUERY_RACE_MODE/);
  }
});
