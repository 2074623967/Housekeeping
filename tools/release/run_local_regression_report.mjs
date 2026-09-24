#!/usr/bin/env node

import fs from "node:fs";
import { spawnSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const backendApps = [
  "systems/payment-core/backend",
  "systems/gateway-access/backend",
  "systems/settlement-system/backend",
  "systems/accounting-system/backend",
  "systems/clearing-system/backend",
  "systems/refund-center/backend",
  "systems/reconciliation-system/backend",
  "systems/risk-control-system/backend",
  "systems/ops-config-system/backend",
  "systems/deposit-system/backend",
  "systems/wallet-account/backend",
  "systems/wallet-system/backend"
];

function getArgValue(flag, fallback = "") {
  const index = process.argv.indexOf(flag);
  return index >= 0 && index < process.argv.length - 1 ? process.argv[index + 1] : fallback;
}

function hasFlag(flag) {
  return process.argv.includes(flag);
}

function selectBackendApps(only) {
  if (!only) return backendApps;
  const selected = backendApps.filter((appDir) =>
    appDir === only || appDir.endsWith(`/${only}`) || appDir.endsWith(`/${only}/backend`));
  if (!selected.length) throw new Error(`未找到后端模块：${only}`);
  return selected;
}

function runNode(label, script, args, logPath) {
  const command = [process.execPath, script, ...args];
  const result = spawnSync(process.execPath, [script, ...args], {
    cwd: rootDir,
    env: process.env,
    encoding: "utf8"
  });
  const stdout = result.stdout || "";
  const stderr = result.stderr || "";
  fs.writeFileSync(logPath, `${stdout}${stderr}`, "utf8");
  const status = result.error ? 1 : (result.status ?? 1);
  console.log(`[local-regression] ${label}: ${status === 0 ? "passed" : "failed"} (log: ${path.relative(rootDir, logPath)})`);
  return {
    command: command.join(" "),
    status,
    signal: result.signal || "",
    error: result.error ? result.error.message : "",
    stdout,
    stderr,
    logPath: path.relative(rootDir, logPath)
  };
}

function runShell(label, script, args, logPath) {
  const command = ["bash", script, ...args];
  const result = spawnSync("bash", [script, ...args], {
    cwd: rootDir,
    env: process.env,
    encoding: "utf8"
  });
  const stdout = result.stdout || "";
  const stderr = result.stderr || "";
  fs.writeFileSync(logPath, `${stdout}${stderr}`, "utf8");
  const status = result.error ? 1 : (result.status ?? 1);
  console.log(`[local-regression] ${label}: ${status === 0 ? "passed" : "failed"} (log: ${path.relative(rootDir, logPath)})`);
  return {
    command: command.join(" "),
    status,
    signal: result.signal || "",
    error: result.error ? result.error.message : "",
    stdout,
    stderr,
    logPath: path.relative(rootDir, logPath)
  };
}

function parseLastJsonObject(output) {
  for (let index = output.lastIndexOf("{"); index >= 0; index = output.lastIndexOf("{", index - 1)) {
    try {
      const parsed = JSON.parse(output.slice(index));
      if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
        return parsed;
      }
    } catch {
      // Keep searching for the outermost JSON object in mixed command output.
    }
  }
  return null;
}

function summarizeExecution(execution) {
  const { stdout, stderr, ...summary } = execution;
  return summary;
}

function readSurefireStats(appDirs) {
  const stats = { modules: [], totalTests: 0, failures: 0, errors: 0, skipped: 0 };
  appDirs.forEach((appDir) => {
    const reportDir = path.join(rootDir, appDir, "target/surefire-reports");
    const moduleStats = { appDir, tests: 0, failures: 0, errors: 0, skipped: 0 };
    if (fs.existsSync(reportDir)) {
      fs.readdirSync(reportDir).filter((name) => name.startsWith("TEST-") && name.endsWith(".xml")).forEach((name) => {
        const content = fs.readFileSync(path.join(reportDir, name), "utf8");
        moduleStats.tests += Number(content.match(/tests="(\d+)"/)?.[1] || 0);
        moduleStats.failures += Number(content.match(/failures="(\d+)"/)?.[1] || 0);
        moduleStats.errors += Number(content.match(/errors="(\d+)"/)?.[1] || 0);
        moduleStats.skipped += Number(content.match(/skipped="(\d+)"/)?.[1] || 0);
      });
    }
    stats.modules.push(moduleStats);
    stats.totalTests += moduleStats.tests;
    stats.failures += moduleStats.failures;
    stats.errors += moduleStats.errors;
    stats.skipped += moduleStats.skipped;
  });
  return stats;
}

function readFrontendTestCount(output) {
  return [...output.matchAll(/ℹ tests (\d+)/g)].reduce((total, match) => total + Number(match[1]), 0);
}

function shouldRunUserFrontend(only) {
  return !hasFlag("--skip-user-frontend") && (!only || only === "payment-core");
}

function main() {
  const only = getArgValue("--only");
  const runtimeScope = getArgValue("--runtime-scope", "all");
  const includeRuntime = hasFlag("--include-runtime");
  const outputPath = path.resolve(rootDir, getArgValue("--output", ".runtime/current-regression-report.json"));
  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  const logStem = outputPath.replace(/\.json$/i, "");
  const selectedBackends = selectBackendApps(only);
  const report = {
    generatedAt: new Date().toISOString(),
    rootDir,
    scope: {
      only: only || "all",
      backend: !hasFlag("--skip-backend"),
      frontendAdmin: !hasFlag("--skip-frontend"),
      userFrontend: shouldRunUserFrontend(only),
      gate: !hasFlag("--skip-gate")
    },
    classification: includeRuntime ? "local-automation-and-controlled-runtime" : "local-automation-only",
    backend: null,
    frontendAdmin: null,
    userFrontend: null,
    runtime: null,
    gate: null,
    limitations: [
      "本报告只汇总本地自动化测试、构建和门禁脚本输出，不替代真实渠道、生产数据库、RabbitMQ、页面冻结或审批证据。",
      "门禁结论由现有 evidence pack 审计决定，不因本地测试通过而自动放行。"
    ]
  };
  let failed = false;

  if (!hasFlag("--skip-backend")) {
    const execution = runNode("backend", "tools/dev/backend-regression.mjs", only ? ["--only", only] : [], `${logStem}.backend.log`);
    report.backend = { ...summarizeExecution(execution), ...readSurefireStats(selectedBackends) };
    failed ||= execution.status !== 0;
  }

  if (!hasFlag("--skip-frontend")) {
    const execution = runNode("frontend-admin", "tools/dev/frontend-admin-regression.mjs", [], `${logStem}.frontend-admin.log`);
    report.frontendAdmin = {
      ...summarizeExecution(execution),
      testCount: readFrontendTestCount(`${execution.stdout}${execution.stderr}`),
      appCount: execution.status === 0 ? 12 : null
    };
    failed ||= execution.status !== 0;
  }

  if (shouldRunUserFrontend(only)) {
    const execution = runNode("payment-core-user", "tools/dev/payment-core-user-regression.mjs", [], `${logStem}.user.log`);
    report.userFrontend = {
      ...summarizeExecution(execution),
      testCount: readFrontendTestCount(`${execution.stdout}${execution.stderr}`),
      appCount: execution.status === 0 ? 5 : null
    };
    failed ||= execution.status !== 0;
  }

  if (includeRuntime) {
    const smoke = runShell(
      "runtime-smoke",
      "tools/dev/local-stack.sh",
      ["smoke", runtimeScope],
      `${logStem}.runtime-smoke.log`
    );
    const happyPath = runShell(
      "runtime-happy-path",
      "tools/dev/local-stack.sh",
      ["happy-path"],
      `${logStem}.runtime-happy-path.log`
    );
    const happyPathFacts = parseLastJsonObject(`${happyPath.stdout}\n${happyPath.stderr}`);
    report.runtime = {
      scope: runtimeScope,
      smoke: summarizeExecution(smoke),
      happyPath: {
        ...summarizeExecution(happyPath),
        facts: happyPathFacts,
        parseError: happyPath.status === 0 && !happyPathFacts
          ? "happy-path 没有返回可解析的事实 JSON"
          : ""
      },
      limitations: [
        "运行态证据只代表当前受控 demo/H2/HTTP 环境，不替代真实渠道、生产 RabbitMQ/MySQL、资金到账或财务审批。"
      ]
    };
    failed ||= smoke.status !== 0 || happyPath.status !== 0 || !happyPathFacts;
  }

  if (!hasFlag("--skip-gate")) {
    const execution = runNode(
      "payment-core-gate",
      "tools/release/audit_payment_core_gate.mjs",
      ["--root-dir", rootDir, "--pack", path.join(rootDir, "docs/releases/evidence/test-20260809-regression"), "--stdout-json"],
      `${logStem}.gate.log`
    );
    let audit = null;
    try {
      audit = JSON.parse(execution.stdout);
    } catch {
      audit = { parseError: "门禁脚本没有返回可解析 JSON" };
    }
    report.gate = { ...summarizeExecution(execution), summary: audit?.summary || null, blockers: audit?.blockers || [] };
    failed ||= execution.status !== 0 || Boolean(audit?.parseError);
  }

  fs.writeFileSync(outputPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  console.log(`[local-regression] report: ${path.relative(rootDir, outputPath)}`);
  process.exitCode = failed ? 1 : 0;
}

export { parseLastJsonObject };

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main();
}
