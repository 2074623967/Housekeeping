#!/usr/bin/env node

import { accessSync, existsSync, mkdirSync, constants as fsConstants } from "node:fs";
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
  ? backendApps.filter((appDir) =>
      appDir === only || appDir.endsWith(`/${only}`) || appDir.endsWith(`/${only}/backend`))
  : backendApps;
if (selected.length === 0) {
  throw new Error(`未找到后端模块：${only}`);
}

const configuredMaven = process.env.MAVEN_BIN || process.env.HSP_MAVEN_BIN;
const mavenHome = process.env.MAVEN_HOME;
const mavenCandidates = [
  configuredMaven,
  mavenHome ? path.join(mavenHome, "bin", "mvn") : "",
  path.join(rootDir, "..", "apache-maven-3.9.16", "bin", "mvn"),
  "/Users/abc123/apache-maven-3.9.16/bin/mvn",
  path.join(rootDir, "..", "apache-maven-3.9.12", "bin", "mvn"),
  "mvn"
].filter(Boolean);
const maven = mavenCandidates.find((candidate) => {
  if (candidate !== "mvn" && !existsSync(candidate)) {
    return false;
  }
  return spawnSync(candidate, ["-v"], { stdio: "ignore" }).status === 0;
});
if (!maven) {
  throw new Error("未找到 Maven，请将 mvn 放入 PATH，或设置 MAVEN_BIN / MAVEN_HOME");
}

const javaHomeCandidates = [
  process.env.JAVA_HOME,
  process.env.HSP_JAVA_HOME,
  "/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home",
  "/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home"
].filter(Boolean);
const javaHome = javaHomeCandidates.find((candidate) => {
  const javac = path.join(candidate, "bin", "javac");
  return existsSync(javac) && spawnSync(javac, ["-version"], { stdio: "ignore" }).status === 0;
});
if (!javaHome) {
  throw new Error("未找到可编译的 JDK，请设置 JAVA_HOME");
}

const configuredRepo = process.env.MAVEN_REPO || process.env.HSP_MAVEN_REPO;
const repoCandidates = [
  configuredRepo,
  path.join(rootDir, "..", "apache-maven-3.9.16", "repository"),
  "/Users/abc123/apache-maven-3.9.16/repository",
  path.join(rootDir, "..", "apache-maven-3.9.12", "repository"),
  process.env.HOME ? path.join(process.env.HOME, ".m2", "repository") : ""
].filter(Boolean);

const isWritableDirectory = (directory) => {
  try {
    accessSync(directory, fsConstants.W_OK);
    return true;
  } catch {
    return false;
  }
};

let mavenRepo = repoCandidates.find((candidate) =>
  existsSync(candidate) && isWritableDirectory(candidate));
if (!mavenRepo) {
  mavenRepo = path.join(rootDir, ".runtime", "maven-repository");
  mkdirSync(mavenRepo, { recursive: true });
}
if (!isWritableDirectory(mavenRepo)) {
  throw new Error(`Maven 仓库不可写：${mavenRepo}，可设置 MAVEN_REPO 或 HSP_MAVEN_REPO`);
}

const javaEnv = {
  ...process.env,
  JAVA_HOME: javaHome,
  PATH: `${path.join(javaHome, "bin")}${process.env.PATH ? `:${process.env.PATH}` : ""}`
};

for (const appDir of selected) {
  const absoluteDir = path.join(rootDir, appDir);
  if (!existsSync(path.join(absoluteDir, "pom.xml"))) {
    throw new Error(`${appDir}: pom.xml 不存在`);
  }
  console.log(`[backend-regression] ${appDir} :: ${maven} -Dmaven.repo.local=${mavenRepo} -q test`);
  execFileSync(maven, ["-Dmaven.repo.local=" + mavenRepo, "-q", "test"], {
    cwd: absoluteDir,
    env: javaEnv,
    stdio: "inherit"
  });
}

console.log(`[backend-regression] ${selected.length}/${selected.length} backend modules passed`);
