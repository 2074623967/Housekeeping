#!/usr/bin/env node

import { execFileSync } from "child_process";
import fs from "fs";
import path from "path";

const argv = process.argv.slice(2);

function getArgValue(flag, fallback = "") {
  const index = argv.indexOf(flag);
  if (index === -1 || index === argv.length - 1) {
    return fallback;
  }
  return argv[index + 1];
}

function hasFlag(flag) {
  return argv.includes(flag);
}

function ensureArg(flag) {
  const value = getArgValue(flag);
  if (!value) {
    throw new Error(`缺少参数 ${flag}`);
  }
  return value;
}

function toAbsolute(rootDir, relativePath) {
  return path.resolve(rootDir, relativePath);
}

function runNodeJson(scriptPath, args) {
  try {
    return JSON.parse(execFileSync(process.execPath, [scriptPath].concat(args), {
      encoding: "utf8"
    }));
  } catch (error) {
    if (error && typeof error.stdout === "string" && error.stdout.trim()) {
      return JSON.parse(error.stdout);
    }
    throw error;
  }
}

function buildGateAudit(rootDir, regressionAudit, releaseAudit, packPath) {
  const blockers = [];
  const notes = [];

  if (!regressionAudit.summary.evidenceReady) {
    blockers.push("test 回归证据包仍未收口");
  }
  if (!regressionAudit.summary.promotionDecisionRecorded) {
    blockers.push("回归汇总尚未显式记录 `test -> master` 结论");
  }
  if (regressionAudit.summary.promotionDecisionRecorded && !regressionAudit.summary.canPromoteTestToMaster) {
    blockers.push("回归汇总当前仍未放行 `test -> master`");
  }
  if (!releaseAudit.summary.materialsReady) {
    blockers.push("release 审计材料结构仍存在缺口");
  }
  if (!releaseAudit.summary.releaseConditionsMet) {
    blockers.push("release 正式放行条件仍未闭环");
  }

  const canPromoteTestToMaster =
    regressionAudit.summary.evidenceReady &&
    regressionAudit.summary.promotionDecisionRecorded &&
    regressionAudit.summary.canPromoteTestToMaster;
  const canCreateRelease =
    canPromoteTestToMaster &&
    regressionAudit.summary.releaseDecisionRecorded &&
    regressionAudit.summary.canCreateRelease &&
    releaseAudit.summary.materialsReady &&
    releaseAudit.summary.canCreateRelease;

  if (regressionAudit.summary.evidenceReady) {
    notes.push("回归证据包已清零，`test -> master` 与 `release/*` 需按各自门禁结论分别判定。");
  }
  if (canPromoteTestToMaster) {
    notes.push("回归汇总已明确 `test -> master` 达标，后续只需继续跟踪 `master -> release/*` 的治理事实。");
  } else if (regressionAudit.summary.promotionDecisionRecorded && !regressionAudit.summary.canPromoteTestToMaster) {
    notes.push("回归汇总已显式记录当前不允许推进 `test -> master`，需先补齐 test 基线自身的冻结门禁。");
  }
  if (regressionAudit.summary.releaseDecisionRecorded && !regressionAudit.summary.canCreateRelease) {
    notes.push("回归汇总已显式记录当前不允许创建 `release/*`，需待正式切版治理条件满足后再改判。");
  }
  regressionAudit.notes.forEach((note) => notes.push(`回归审计：${note}`));
  releaseAudit.notes.forEach((note) => notes.push(`release 审计：${note}`));

  return {
    generatedAt: new Date().toISOString(),
    rootDir,
    inputs: {
      regressionPack: path.relative(rootDir, packPath),
      regressionScript: path.relative(rootDir, toAbsolute(rootDir, "tools/release/audit_regression_pack.mjs")),
      releaseScript: path.relative(rootDir, toAbsolute(rootDir, "tools/release/audit_release_readiness.mjs"))
    },
    summary: {
      regressionEvidenceReady: regressionAudit.summary.evidenceReady,
      regressionPromotionDecisionRecorded: regressionAudit.summary.promotionDecisionRecorded,
      regressionReleaseDecisionRecorded: regressionAudit.summary.releaseDecisionRecorded,
      releaseMaterialsReady: releaseAudit.summary.materialsReady,
      releaseConditionsMet: releaseAudit.summary.releaseConditionsMet,
      blockerCount: blockers.length,
      canPromoteTestToMaster,
      canCreateRelease
    },
    blockers,
    notes,
    regressionAudit,
    releaseAudit
  };
}

function renderMarkdown(audit) {
  const lines = [];
  lines.push("# payment-core 统一门禁审计报告");
  lines.push("");
  lines.push(`- 生成时间：\`${audit.generatedAt}\``);
  lines.push(`- 是否允许推进 \`test -> master\`：\`${audit.summary.canPromoteTestToMaster ? "是" : "否"}\``);
  lines.push(`- 是否允许创建 \`release/*\`：\`${audit.summary.canCreateRelease ? "是" : "否"}\``);
  lines.push(`- 阻断项数量：\`${audit.summary.blockerCount}\``);
  lines.push("");
  lines.push("## 汇总");
  lines.push("");
  lines.push("| 项目 | 结果 |");
  lines.push("| --- | --- |");
  lines.push(`| 回归证据包已收口 | \`${audit.summary.regressionEvidenceReady ? "是" : "否"}\` |`);
  lines.push(`| 回归汇总已记录 \`test -> master\` 结论 | \`${audit.summary.regressionPromotionDecisionRecorded ? "是" : "否"}\` |`);
  lines.push(`| 回归汇总已记录 \`release/*\` 结论 | \`${audit.summary.regressionReleaseDecisionRecorded ? "是" : "否"}\` |`);
  lines.push(`| release 材料完整性 | \`${audit.summary.releaseMaterialsReady ? "通过" : "未通过"}\` |`);
  lines.push(`| release 正式放行条件 | \`${audit.summary.releaseConditionsMet ? "通过" : "未通过"}\` |`);
  lines.push("");
  lines.push("## 阻断项");
  lines.push("");
  if (!audit.blockers.length) {
    lines.push("1. 当前统一门禁未发现阻断项。");
  } else {
    audit.blockers.forEach((blocker, index) => {
      lines.push(`${index + 1}. ${blocker}`);
    });
  }
  lines.push("");
  lines.push("## 备注");
  lines.push("");
  if (!audit.notes.length) {
    lines.push("1. 当前无额外备注。");
  } else {
    audit.notes.forEach((note, index) => {
      lines.push(`${index + 1}. ${note}`);
    });
  }
  lines.push("");
  lines.push("## 输入");
  lines.push("");
  lines.push(`1. \`${audit.inputs.regressionPack}\``);
  lines.push(`2. \`${audit.inputs.regressionScript}\``);
  lines.push(`3. \`${audit.inputs.releaseScript}\``);
  lines.push("");
  return `${lines.join("\n")}\n`;
}

function main() {
  const rootDir = path.resolve(ensureArg("--root-dir"));
  const packPath = path.resolve(ensureArg("--pack"));
  const outputDir = getArgValue("--output-dir", path.join(rootDir, "docs", "releases", "audit"));
  const regressionScript = toAbsolute(rootDir, "tools/release/audit_regression_pack.mjs");
  const releaseScript = toAbsolute(rootDir, "tools/release/audit_release_readiness.mjs");
  const stdoutJson = hasFlag("--stdout-json");

  const regressionAudit = runNodeJson(regressionScript, [
    "--root",
    rootDir,
    "--pack",
    packPath
  ]);
  const releaseAudit = runNodeJson(releaseScript, [
    "--root-dir",
    rootDir,
    "--candidate-doc",
    toAbsolute(rootDir, "docs/releases/v1.1.0-发布说明-候选版.md"),
    "--stabilization-doc",
    toAbsolute(rootDir, "docs/releases/2026-08-09-master稳定观察起始记录.md"),
    "--checklist-doc",
    toAbsolute(rootDir, "docs/releases/2026-08-09-master稳定观察检查清单.md"),
    "--approval-doc",
    toAbsolute(rootDir, "docs/releases/2026-08-09-v1.1.0发布审批清单.md"),
    "--rollback-doc",
    toAbsolute(rootDir, "docs/releases/2026-08-09-v1.1.0回滚演练记录.md"),
    "--onsite-doc",
    toAbsolute(rootDir, "docs/releases/2026-08-09-v1.1.0发布后现场核验记录模板.md"),
    "--stabilization-closing-doc",
    toAbsolute(rootDir, "docs/releases/2026-08-09-master稳定观察结束结论模板.md"),
    "--decision-doc",
    toAbsolute(rootDir, "docs/releases/2026-08-09-v1.1.0切版决策记录.md"),
    "--observation-checkpoint-doc",
    toAbsolute(rootDir, "docs/releases/2026-08-09-master稳定观察中间结论.md"),
    "--live-verification-doc",
    toAbsolute(rootDir, "docs/releases/2026-08-09-master主链路即时核验记录.md"),
    "--deferral-doc",
    toAbsolute(rootDir, "docs/releases/2026-08-09-v1.1.0暂缓切版说明.md"),
    "--gate-day-end-doc",
    toAbsolute(rootDir, "docs/releases/2026-08-09-release门禁日结.md"),
    "--reevaluation-checklist-doc",
    toAbsolute(rootDir, "docs/releases/2026-08-10-v1.1.0重评清单.md"),
    "--release-note-checklist-doc",
    toAbsolute(rootDir, "docs/releases/v1.1.0-正式版发布说明检查表.md"),
    "--prep-status-doc",
    toAbsolute(rootDir, "docs/releases/2026-08-09-v1.1.0发布前材料现状记录.md"),
    "--stdout-json"
  ]);

  const audit = buildGateAudit(rootDir, regressionAudit, releaseAudit, packPath);
  if (stdoutJson) {
    console.log(JSON.stringify(audit, null, 2));
  } else {
    fs.mkdirSync(outputDir, { recursive: true });
    const jsonPath = path.join(outputDir, "payment-core-gate-audit.json");
    const mdPath = path.join(outputDir, "payment-core-gate-audit.md");
    fs.writeFileSync(jsonPath, `${JSON.stringify(audit, null, 2)}\n`, "utf8");
    fs.writeFileSync(mdPath, renderMarkdown(audit), "utf8");

    console.log(`已生成 payment-core 门禁审计 JSON: ${jsonPath}`);
    console.log(`已生成 payment-core 门禁审计 Markdown: ${mdPath}`);
    console.log(`是否允许推进 test -> master: ${audit.summary.canPromoteTestToMaster ? "是" : "否"}`);
    console.log(`是否允许创建 release/*: ${audit.summary.canCreateRelease ? "是" : "否"}`);
  }

  if (audit.blockers.length) {
    process.exitCode = 2;
  }
}

main();
