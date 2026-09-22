#!/usr/bin/env node

import { existsSync } from "node:fs";
import { execFileSync, spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";

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

const onlyIndex = process.argv.indexOf("--only");
const only = onlyIndex >= 0 ? process.argv[onlyIndex + 1] : "";
if (onlyIndex >= 0 && !only) {
  throw new Error("--only 需要一个后端目录或目录后缀");
}

const selected = only
  ? backendApps.filter((appDir) => appDir === only || appDir.endsWith(`/${only}`))
  : backendApps;
if (selected.length === 0) {
  throw new Error(`未找到后端模块：${only}`);
}

const configuredMaven = process.env.MAVEN_BIN;
const mavenCandidates = configuredMaven
  ? [configuredMaven]
  : ["mvn", "/Users/abc123/workspace/apache-maven-3.9.12/bin/mvn"];
const maven = mavenCandidates.find((candidate) => {
  if (candidate !== "mvn" && !existsSync(candidate)) {
    return false;
  }
  return spawnSync(candidate, ["-v"], { stdio: "ignore" }).status === 0;
});
if (!maven) {
  throw new Error("未找到 Maven，请设置 MAVEN_BIN");
}

for (const appDir of selected) {
  const absoluteDir = path.join(rootDir, appDir);
  if (!existsSync(path.join(absoluteDir, "pom.xml"))) {
    throw new Error(`${appDir}: pom.xml 不存在`);
  }
  console.log(`[backend-regression] ${appDir} :: ${maven} -q test`);
  execFileSync(maven, ["-q", "test"], {
    cwd: absoluteDir,
    stdio: "inherit"
  });
}

console.log(`[backend-regression] ${selected.length}/${selected.length} backend modules passed`);
