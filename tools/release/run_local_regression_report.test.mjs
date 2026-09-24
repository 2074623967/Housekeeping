import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import test from "node:test";
import { parseLastJsonObject } from "./run_local_regression_report.mjs";

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const scriptPath = path.join(rootDir, "tools/release/run_local_regression_report.mjs");

test("propagates a blocked payment gate as a failed report", () => {
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "hsp-regression-report-"));
  const outputPath = path.join(tempDir, "report.json");
  try {
    assert.throws(
      () => execFileSync(process.execPath, [
        scriptPath,
        "--only", "payment-core",
        "--skip-backend",
        "--skip-frontend",
        "--skip-user-frontend",
        "--output", outputPath
      ], { cwd: rootDir, encoding: "utf8", stdio: "pipe" }),
      (error) => error.status === 1
    );

    const report = JSON.parse(fs.readFileSync(outputPath, "utf8"));
    assert.equal(report.gate.status, 2);
    assert.equal(report.gate.summary.regressionEvidenceReady, true);
    assert.equal(report.gate.summary.canPromoteTestToMaster, false);
    assert.equal(report.gate.summary.canCreateRelease, false);
  } finally {
    fs.rmSync(tempDir, { recursive: true, force: true });
  }
});

test("extracts the outermost facts JSON from mixed runtime output", () => {
  const output = [
    "[PASS] cross-system local happy path",
    JSON.stringify({ orderNo: "LOCAL-001", reconciliation: { status: "COMPLETED" } }),
    ""
  ].join("\n");

  assert.deepEqual(parseLastJsonObject(output), {
    orderNo: "LOCAL-001",
    reconciliation: { status: "COMPLETED" }
  });
  assert.equal(parseLastJsonObject("[PASS] no facts"), null);
});
