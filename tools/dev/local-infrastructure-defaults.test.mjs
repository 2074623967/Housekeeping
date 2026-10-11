import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { spawnSync } from "node:child_process";
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";

const root = new URL("../..", import.meta.url);

const expectedDefaults = [
  ["systems/payment-core/backend/src/main/resources/application.yml", "HSP_DB_URL:jdbc:mysql://localhost:3306/"],
  ["systems/payment-core/backend/src/main/resources/application.yml", "HSP_RABBITMQ_HOST:localhost"],
  ["systems/payment-core/backend/src/main/resources/application.yml", "HSP_PAYMENT_RISK_CONTROL_EVALUATE_URL:http://localhost:18180/api/risk-control/decisions/evaluate"],
  ["systems/risk-control-system/backend/src/main/resources/application.yml", "RISK_CONTROL_PORT:18180"],
  ["systems/clearing-system/backend/src/main/resources/application.yml", "HSP_RABBITMQ_HOST:localhost"],
  ["systems/settlement-system/backend/src/main/resources/application.yml", "HSP_RABBITMQ_HOST:localhost"],
  ["systems/accounting-system/backend/src/main/resources/application.yml", "HSP_RABBITMQ_HOST:localhost"]
];

test("uses localhost for local database and RabbitMQ defaults", async () => {
  const contents = new Map();
  for (const [relativePath] of expectedDefaults) {
    if (!contents.has(relativePath)) {
      contents.set(relativePath, await readFile(new URL(relativePath, root), "utf8"));
    }
  }
  for (const [relativePath, expected] of expectedDefaults) {
    assert.match(contents.get(relativePath), new RegExp(expected.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  }
});

test("keeps the payment-chain verification scope focused on required backends", async () => {
  const localStack = await readFile(new URL("tools/dev/local-stack.sh", root), "utf8");
  assert.match(localStack, /payment-chain  启动支付 happy-path 所需的后端依赖/);
  assert.match(localStack, /if \[\[ "\$scope" == "payment-chain" \|\| "\$scope" == "payment-query-chain" \]\]/);
  assert.match(localStack, /ops-config-system\|risk-control-system\|gateway-access\|accounting-system\|clearing-system\|settlement-system\|refund-center\|reconciliation-system\|payment-core/);
  assert.match(localStack, /verify 只支持 all、payment-chain、payment-query-chain 或 risk-review/);
});

test("refuses query-chain datasource overrides before starting its local upstream", () => {
  const result = spawnSync("bash", [new URL("tools/dev/local-stack.sh", root).pathname,
    "verify", "payment-query-chain"], {
    encoding: "utf8",
    env: { PATH: process.env.PATH, HOME: process.env.HOME, HSP_DB_URL: "jdbc:mysql://unexpected" }
  });
  assert.equal(result.status, 2);
  assert.match(result.stderr, /payment-chain 拒绝环境覆盖: HSP_DB_URL/);
  assert.doesNotMatch(result.stdout, /\[START\]/);
});

test("query-chain owns its loopback upstream and uses only local H2 fixtures", async () => {
  const localStack = await readFile(new URL("tools/dev/local-stack.sh", root), "utf8");
  assert.match(localStack, /payment-query-upstream\.pid/);
  assert.match(localStack, /lsof -nP -iTCP:18190 -sTCP:LISTEN/);
  assert.match(localStack, /HSP_GATEWAY_WX_PAYMENT_QUERY_URL="http:\/\/127\.0\.0\.1:18190\/query"/);
  assert.match(localStack, /SPRING_SQL_INIT_DATA_LOCATIONS="classpath:data\.sql,file:/);
  assert.match(localStack, /HSP_LOCAL_PAYMENT_QUERY_CHAIN_SCOPED=true/);
  const fixture = await readFile(new URL("tools/dev/payment-query-chain-gateway.sql", root), "utf8");
  assert.match(fixture, /WHERE certificate_code = 'CERT_WX_2026'/);
});

test("refuses query-chain verification when an upstream PID already exists", () => {
  const runtimeDir = mkdtempSync(join(tmpdir(), "hsp-payment-query-chain-"));
  try {
    mkdirSync(join(runtimeDir, "pids"));
    writeFileSync(join(runtimeDir, "pids", "payment-query-upstream.pid"), "999999\n");
    const result = spawnSync("bash", [new URL("tools/dev/local-stack.sh", root).pathname,
      "verify", "payment-query-chain"], {
      encoding: "utf8",
      env: { PATH: process.env.PATH, HOME: process.env.HOME, HSP_LOCAL_RUNTIME_DIR: runtimeDir }
    });
    assert.equal(result.status, 2);
    assert.match(result.stderr, /上游 mock 端口\/PID 已被占用/);
    assert.doesNotMatch(result.stdout, /\[START\]/);
  } finally {
    rmSync(runtimeDir, { recursive: true, force: true });
  }
});

test("isolates risk-review verification to local H2 services", async () => {
  const localStack = await readFile(new URL("tools/dev/local-stack.sh", root), "utf8");
  assert.match(localStack, /risk-review    仅启动支付核心与风控中心/);
  assert.match(localStack, /"\$scope" == "risk-review"/);
  assert.match(localStack, /"\$component" == "backends".*"risk-control-system".*"payment-core"/);
  assert.match(localStack, /HSP_DEMO_DB_URL.*HSP_DB_USERNAME/);
  assert.match(localStack, /RISK_CONTROL_DATASOURCE_URL.*RISK_CONTROL_DATASOURCE_DRIVER/);
  assert.match(localStack, /HSP_PAYMENT_RISK_CONTROL_EVALUATE_URL/);
  assert.match(localStack, /backend\.\$variable_name\.pid/);
  assert.match(localStack, /export HSP_PAYMENT_RISK_CONTROL_ENABLED=true/);
  assert.match(localStack, /backend_env\+=\(SPRING_PROFILES_ACTIVE=local-risk-review\)/);
  assert.match(localStack, /HSP_LOCAL_RISK_REVIEW_SCOPED=true node/);
  assert.match(localStack, /cross-system-risk-review\.mjs/);
  const riskReview = await readFile(new URL("tools/dev/cross-system-risk-review.mjs", root), "utf8");
  assert.ok(riskReview.indexOf("risk-control-h2-local-review") < riskReview.indexOf("const orderNo ="));
});

test("refuses payment-chain verification with a datasource override before starting services", () => {
  const result = spawnSync("bash", [new URL("tools/dev/local-stack.sh", root).pathname, "verify", "payment-chain"], {
    encoding: "utf8",
    env: { PATH: process.env.PATH, HOME: process.env.HOME, HSP_DB_URL: "jdbc:h2:mem:unexpected" }
  });
  assert.equal(result.status, 2);
  assert.match(result.stderr, /payment-chain 拒绝环境覆盖: HSP_DB_URL/);
  assert.doesNotMatch(result.stdout, /\[START\]/);
});

test("refuses payment-chain verification with an existing backend PID file", () => {
  const runtimeDir = mkdtempSync(join(tmpdir(), "hsp-payment-chain-"));
  try {
    mkdirSync(join(runtimeDir, "pids"));
    writeFileSync(join(runtimeDir, "pids", "backend.ops-config-system.pid"), "999999\n");
    const result = spawnSync("bash", [new URL("tools/dev/local-stack.sh", root).pathname, "verify", "payment-chain"], {
      encoding: "utf8",
      env: { PATH: process.env.PATH, HOME: process.env.HOME, HSP_LOCAL_RUNTIME_DIR: runtimeDir }
    });
    assert.equal(result.status, 2);
    assert.match(result.stderr, /payment-chain 发现现存 PID 文件: backend\.ops-config-system\.pid/);
    assert.doesNotMatch(result.stdout, /\[START\]/);
  } finally {
    rmSync(runtimeDir, { recursive: true, force: true });
  }
});

test("requires payment-chain port and PID isolation before starting or writing", async () => {
  const localStack = await readFile(new URL("tools/dev/local-stack.sh", root), "utf8");
  const verify = localStack.slice(localStack.indexOf("run_verify() {"), localStack.indexOf("main() {"));
  assert.match(verify, /guard_local_payment_chain_verify "\$scope"/);
  assert.ok(verify.indexOf('guard_local_payment_chain_verify "$scope"') < verify.indexOf("run_start \"$scope\""));
  assert.ok(verify.indexOf('guard_started_local_payment_chain "$scope"') > verify.indexOf("run_start \"$scope\""));
  assert.ok(verify.indexOf('guard_started_local_payment_chain "$scope"') < verify.indexOf("HSP_LOCAL_PAYMENT_CHAIN_SCOPED=true node"));
  assert.match(localStack, /backend\.\$name\.pid/);
  assert.match(localStack, /lsof -nP -iTCP:"\$port" -sTCP:LISTEN/);
  assert.match(verify, /HSP_LOCAL_PAYMENT_CHAIN_SCOPED=true node/);
  assert.match(localStack, /happy-path\)\s+run_verify payment-chain/);
  assert.match(localStack, /payment_profile="demo,local-payment-chain"/);
  assert.match(localStack, /backend_env\+=\(SPRING_PROFILES_ACTIVE=local-payment-chain\)/);
  assert.match(verify, /run_start "\$scope" "\$verification_mode"/);
});

test("verify all checks frontend PID and ports before starting backends", async () => {
  const localStack = await readFile(new URL("tools/dev/local-stack.sh", root), "utf8");
  const guard = localStack.slice(localStack.indexOf("guard_local_payment_chain_verify() {"),
    localStack.indexOf("guard_started_local_payment_chain() {"));
  assert.match(guard, /if \[\[ "\$scope" == "all" \]\]/);
  assert.match(guard, /frontend\.\$name\.pid/);
  assert.match(guard, /done < <\(frontend_manifest\)/);
});

test("verify all refuses a preexisting frontend PID before any backend startup", () => {
  const runtimeDir = mkdtempSync(join(tmpdir(), "hsp-verify-all-"));
  try {
    mkdirSync(join(runtimeDir, "pids"));
    writeFileSync(join(runtimeDir, "pids", "frontend.payment-admin.pid"), "999999\n");
    const result = spawnSync("bash", [new URL("tools/dev/local-stack.sh", root).pathname, "verify", "all"], {
      encoding: "utf8",
      env: { PATH: process.env.PATH, HOME: process.env.HOME, HSP_LOCAL_RUNTIME_DIR: runtimeDir }
    });
    assert.equal(result.status, 2);
    assert.match(result.stderr, /verify all 发现现存前端 PID 文件: frontend\.payment-admin\.pid/);
    assert.doesNotMatch(result.stdout, /\[START\]/);
  } finally {
    rmSync(runtimeDir, { recursive: true, force: true });
  }
});

test("verify all refuses an occupied frontend port before any backend startup", () => {
  const runtimeDir = mkdtempSync(join(tmpdir(), "hsp-verify-all-port-"));
  try {
    const binDir = join(runtimeDir, "bin");
    mkdirSync(binDir);
    writeFileSync(join(binDir, "lsof"), '#!/bin/sh\n[ "$2" = "-iTCP:5174" ]\n', { mode: 0o755 });
    writeFileSync(join(binDir, "nc"), "#!/bin/sh\nexit 1\n", { mode: 0o755 });
    const result = spawnSync("bash", [new URL("tools/dev/local-stack.sh", root).pathname, "verify", "all"], {
      encoding: "utf8",
      env: { PATH: `${binDir}:${process.env.PATH}`, HOME: process.env.HOME, HSP_LOCAL_RUNTIME_DIR: runtimeDir }
    });
    assert.equal(result.status, 2);
    assert.match(result.stderr, /verify all 前端端口 5174 已有服务，拒绝复用/);
    assert.doesNotMatch(result.stdout, /\[START\]/);
  } finally {
    rmSync(runtimeDir, { recursive: true, force: true });
  }
});

test("verify all requires owned frontend PIDs after startup", async () => {
  const localStack = await readFile(new URL("tools/dev/local-stack.sh", root), "utf8");
  const guard = localStack.slice(localStack.indexOf("guard_started_local_payment_chain() {"),
    localStack.indexOf("run_verify() {"));
  assert.match(guard, /if \[\[ "\$scope" == "all" \]\]; then/);
  assert.match(guard, /frontend\.\$name\.pid/);
  assert.match(guard, /done < <\(frontend_manifest\)/);
});

test("prefers a real macOS JDK before deriving JAVA_HOME from PATH java", async () => {
  const localStack = await readFile(new URL("tools/dev/local-stack.sh", root), "utf8");
  const candidateIndex = localStack.indexOf('for candidate in \\\n    "/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home"');
  const pathFallbackIndex = localStack.indexOf("if command -v java >/dev/null 2>&1");
  assert.ok(candidateIndex >= 0);
  assert.ok(pathFallbackIndex > candidateIndex);
});

test("stops Spring Boot child JVMs before the tracked Maven process", async () => {
  const localStack = await readFile(new URL("tools/dev/local-stack.sh", root), "utf8");
  const cleanupIndex = localStack.indexOf("stop_child_processes \"$pid\"");
  const parentKillIndex = localStack.indexOf('kill "$pid" >/dev/null 2>&1 || true', cleanupIndex);
  assert.match(localStack, /stop_child_processes\(\) \{/);
  assert.match(localStack, /pgrep -P \"\$parent_pid\"/);
  assert.ok(cleanupIndex >= 0);
  assert.ok(parentKillIndex > cleanupIndex);
});

test("does not treat zombie PIDs as live local-stack processes", async () => {
  const localStack = await readFile(new URL("tools/dev/local-stack.sh", root), "utf8");
  assert.match(localStack, /kill -0 "\$pid"[^\n]*\|\| return 1/);
  assert.match(localStack, /ps -o stat= -p "\$pid"/);
  assert.match(localStack, /"\$process_state" != Z\*/);
});

test("allows PID and log storage outside the repository", async () => {
  const localStack = await readFile(new URL("tools/dev/local-stack.sh", root), "utf8");
  assert.match(localStack, /RUNTIME_DIR=\"\$\{HSP_LOCAL_RUNTIME_DIR:-\$ROOT_DIR\/\.runtime\/local-stack\}\"/);
});
