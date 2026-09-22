#!/usr/bin/env node

import { existsSync, readFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const userApps = [
  "systems/payment-core/frontend/api-client",
  "systems/payment-core/frontend/app-web",
  "systems/payment-core/frontend/h5-web",
  "systems/payment-core/frontend/pc-web",
  "systems/payment-core/frontend/mini-program"
];

const onlyIndex = process.argv.indexOf("--only");
const only = onlyIndex >= 0 ? process.argv[onlyIndex + 1] : "";
if (onlyIndex >= 0 && !only) {
  throw new Error("--only 需要一个用户端目录或目录后缀");
}

const selected = only
  ? userApps.filter((appDir) => appDir === only || appDir.endsWith(`/${only}`))
  : userApps;
if (selected.length === 0) {
  throw new Error(`未找到用户端模块：${only}`);
}

const run = (appDir, npmArgs) => {
  const absoluteDir = path.join(rootDir, appDir);
  const packageFile = path.join(absoluteDir, "package.json");
  if (!existsSync(packageFile)) {
    throw new Error(`${appDir}: package.json 不存在`);
  }
  console.log(`[payment-core-user-regression] ${appDir} :: npm ${npmArgs.join(" ")}`);
  execFileSync("npm", npmArgs, { cwd: absoluteDir, stdio: "inherit" });
};

for (const appDir of selected) {
  const packageJson = JSON.parse(readFileSync(path.join(rootDir, appDir, "package.json"), "utf8"));
  if (!packageJson.scripts?.test) {
    throw new Error(`${appDir}: package.json 缺少 test 脚本`);
  }
  run(appDir, ["test"]);
  if (packageJson.scripts.build) {
    run(appDir, ["run", "build"]);
  } else {
    console.log(`[payment-core-user-regression] ${appDir} :: no build script (accepted for this package)`);
  }
}

console.log(`[payment-core-user-regression] ${selected.length}/${selected.length} user apps passed their declared checks`);
