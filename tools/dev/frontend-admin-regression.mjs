#!/usr/bin/env node

import { existsSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const adminApps = [
  "systems/accounting-system/frontend/admin-web",
  "systems/clearing-system/frontend/admin-web",
  "systems/deposit-system/frontend/admin-web",
  "systems/gateway-access/frontend/admin-web",
  "systems/ops-config-system/frontend/admin-web",
  "systems/payment-core/frontend/admin-web",
  "systems/reconciliation-system/frontend/admin-web",
  "systems/refund-center/frontend/admin-web",
  "systems/risk-control-system/frontend/admin-web",
  "systems/settlement-system/frontend/admin-web",
  "systems/wallet-account/frontend/admin-web",
  "systems/wallet-system/frontend/admin-web"
];

const run = (appDir, npmArgs) => {
  const absoluteDir = path.join(rootDir, appDir);
  if (!existsSync(path.join(absoluteDir, "package.json"))) {
    throw new Error(`${appDir}: package.json 不存在`);
  }
  console.log(`[frontend-admin-regression] ${appDir} :: npm ${npmArgs.join(" ")}`);
  execFileSync("npm", npmArgs, {
    cwd: absoluteDir,
    stdio: "inherit"
  });
};

for (const appDir of adminApps) {
  run(appDir, ["test"]);
  run(appDir, ["run", "build"]);
}

console.log(`[frontend-admin-regression] ${adminApps.length}/${adminApps.length} admin apps passed test and build`);
