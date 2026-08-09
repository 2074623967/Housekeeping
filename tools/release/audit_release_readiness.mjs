#!/usr/bin/env node

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

function ensureArg(flag) {
  const value = getArgValue(flag);
  if (!value) {
    throw new Error(`缺少参数 ${flag}`);
  }
  return value;
}

function readText(filePath) {
  if (!fs.existsSync(filePath)) {
    throw new Error(`文件不存在: ${filePath}`);
  }
  return fs.readFileSync(filePath, "utf8");
}

function toRelative(rootDir, filePath) {
  return path.relative(rootDir, filePath) || path.basename(filePath);
}

function containsAll(content, patterns) {
  return patterns.every((pattern) => content.includes(pattern));
}

function buildAudit(
  rootDir,
  candidateDocPath,
  stabilizationDocPath,
  checklistDocPath,
  approvalDocPath,
  rollbackDocPath,
  onSiteDocPath,
  stabilizationClosingDocPath,
  decisionDocPath,
  observationCheckpointDocPath,
  liveVerificationDocPath,
  deferralDocPath,
  gateDayEndDocPath,
  reevaluationChecklistDocPath,
  releaseNoteChecklistDocPath,
  prepStatusDocPath
) {
  const candidateContent = readText(candidateDocPath);
  const stabilizationContent = readText(stabilizationDocPath);
  const checklistContent = readText(checklistDocPath);
  const approvalContent = readText(approvalDocPath);
  const rollbackContent = readText(rollbackDocPath);
  const onSiteContent = readText(onSiteDocPath);
  const stabilizationClosingContent = readText(stabilizationClosingDocPath);
  const decisionContent = readText(decisionDocPath);
  const observationCheckpointContent = readText(observationCheckpointDocPath);
  const liveVerificationContent = readText(liveVerificationDocPath);
  const deferralContent = readText(deferralDocPath);
  const gateDayEndContent = readText(gateDayEndDocPath);
  const reevaluationChecklistContent = readText(reevaluationChecklistDocPath);
  const releaseNoteChecklistContent = readText(releaseNoteChecklistDocPath);
  const prepStatusContent = readText(prepStatusDocPath);

  const blockers = [];

  const candidateReady = containsAll(candidateContent, [
    "状态：候选版",
    "当前结论：`暂不切 release/*`"
  ]);
  if (!candidateReady) {
    blockers.push("release 候选版发布说明尚未达到当前模板要求");
  }

  const stabilizationReady = containsAll(stabilizationContent, [
    "当前阶段：`冻结候选已从 test 合入 master，进入稳定观察`",
    "当前结论 | `进入观察，暂不切 release/*`"
  ]);
  if (!stabilizationReady) {
    blockers.push("master 稳定观察起始记录不完整");
  }

  const checklistReady = containsAll(checklistContent, [
    "当前阶段：`release/*` 前稳定观察",
    "当前结论：`观察中，暂不切版`"
  ]);
  if (!checklistReady) {
    blockers.push("master 稳定观察检查清单不完整");
  }

  // “预审完成”只表示材料结构齐全，不代表正式审批已经完成；
  // release 是否可创建仍由真实签字、回滚演练和观察结束结论共同决定。
  const approvalReady =
    approvalContent.includes("审批前置条件") &&
    (
      approvalContent.includes("当前状态：`待审批`") ||
      approvalContent.includes("当前状态：`预审完成，待正式签字`")
    );
  if (!approvalReady) {
    blockers.push("发布审批清单不完整");
  }

  const rollbackReady =
    rollbackContent.includes("演练记录") &&
    (
      rollbackContent.includes("当前状态：`待演练 / 待补齐`") ||
      rollbackContent.includes("当前状态：`方案已确认，待实际演练`")
    );
  if (!rollbackReady) {
    blockers.push("回滚演练记录不完整");
  }

  const onSiteReady = containsAll(onSiteContent, [
    "当前状态：`模板`",
    "发布后现场核验记录模板"
  ]);
  if (!onSiteReady) {
    blockers.push("发布后现场核验记录模板不完整");
  }

  const stabilizationClosingReady = containsAll(stabilizationClosingContent, [
    "当前状态：`模板`",
    "是否允许继续推进 `release/v1.1.0`"
  ]);
  if (!stabilizationClosingReady) {
    blockers.push("master 稳定观察结束结论模板不完整");
  }

  const decisionReady = containsAll(decisionContent, [
    "是否允许创建 `release/v1.1.0`",
    "2026-08-09",
    "否"
  ]);
  if (!decisionReady) {
    blockers.push("切版决策记录不完整");
  }

  const observationCheckpointReady = containsAll(observationCheckpointContent, [
    "当前阶段：`观察进行中`",
    "今天不切 release/v1.1.0"
  ]);
  if (!observationCheckpointReady) {
    blockers.push("master 观察中间结论记录不完整");
  }

  const liveVerificationReady = containsAll(liveVerificationContent, [
    "即时核验结果",
    "`payment-core`",
    "`clearing-system`",
    "`settlement-system`",
    "`accounting-system`"
  ]);
  if (!liveVerificationReady) {
    blockers.push("master 主链路即时核验记录不完整");
  }

  const deferralReady = containsAll(deferralContent, [
    "2026-08-09 暂不创建 release/v1.1.0",
    "计划重评日期：`2026-08-10`"
  ]);
  if (!deferralReady) {
    blockers.push("暂缓切版说明不完整");
  }

  const gateDayEndReady = containsAll(gateDayEndContent, [
    "2026-08-09 不创建 release/v1.1.0",
    "已确认通过项",
    "当前仍未满足项"
  ]);
  if (!gateDayEndReady) {
    blockers.push("release 门禁日结不完整");
  }

  const reevaluationReady = containsAll(reevaluationChecklistContent, [
    "计划执行日期：`2026-08-10`",
    "必查项"
  ]);
  if (!reevaluationReady) {
    blockers.push("重评清单不完整");
  }

  const releaseNoteChecklistReady = containsAll(releaseNoteChecklistContent, [
    "当前状态：`待正式版说明生成`",
    "必备内容"
  ]);
  if (!releaseNoteChecklistReady) {
    blockers.push("正式版发布说明检查表不完整");
  }

  const prepStatusReady = containsAll(prepStatusContent, [
    "材料骨架已齐",
    "当前仍缺的实际记录"
  ]);
  if (!prepStatusReady) {
    blockers.push("发布前材料现状记录不完整");
  }

  if (!candidateContent.includes("还缺正式 release 级发布审批材料")) {
    blockers.push("release 候选版没有显式列出发布审批材料缺口");
  }
  if (!candidateContent.includes("还缺正式发布后现场核验记录")) {
    blockers.push("release 候选版没有显式列出发布后现场核验缺口");
  }

  if (candidateContent.includes("暂不切 release/*")) {
    blockers.push("当前候选版发布说明仍明确暂不切 release/*");
  }
  if (checklistContent.includes("待完成项")) {
    blockers.push("release 候选准备清单仍存在待完成项");
  }

  const canCreateRelease = blockers.length === 0 && false;

  return {
    generatedAt: new Date().toISOString(),
    rootDir,
    inputs: {
      candidateDoc: toRelative(rootDir, candidateDocPath),
      stabilizationDoc: toRelative(rootDir, stabilizationDocPath),
      checklistDoc: toRelative(rootDir, checklistDocPath),
      approvalDoc: toRelative(rootDir, approvalDocPath),
      rollbackDoc: toRelative(rootDir, rollbackDocPath),
      onSiteDoc: toRelative(rootDir, onSiteDocPath),
      stabilizationClosingDoc: toRelative(rootDir, stabilizationClosingDocPath),
      decisionDoc: toRelative(rootDir, decisionDocPath),
      observationCheckpointDoc: toRelative(rootDir, observationCheckpointDocPath),
      liveVerificationDoc: toRelative(rootDir, liveVerificationDocPath),
      deferralDoc: toRelative(rootDir, deferralDocPath),
      gateDayEndDoc: toRelative(rootDir, gateDayEndDocPath),
      reevaluationChecklistDoc: toRelative(rootDir, reevaluationChecklistDocPath),
      releaseNoteChecklistDoc: toRelative(rootDir, releaseNoteChecklistDocPath),
      prepStatusDoc: toRelative(rootDir, prepStatusDocPath)
    },
    summary: {
      candidateDocReady: candidateReady,
      stabilizationReady,
      checklistReady,
      approvalReady,
      rollbackReady,
      onSiteReady,
      stabilizationClosingReady,
      decisionReady,
      observationCheckpointReady,
      liveVerificationReady,
      deferralReady,
      gateDayEndReady,
      reevaluationReady,
      releaseNoteChecklistReady,
      prepStatusReady,
      blockerCount: blockers.length,
      canCreateRelease
    },
    blockers
  };
}

function renderMarkdown(audit) {
  const lines = [];
  lines.push("# release 就绪自动审计报告");
  lines.push("");
  lines.push(`- 生成时间：\`${audit.generatedAt}\``);
  lines.push(`- 是否允许创建 \`release/*\`：\`${audit.summary.canCreateRelease ? "是" : "否"}\``);
  lines.push("");
  lines.push("## 汇总");
  lines.push("");
  lines.push("| 项目 | 结果 |");
  lines.push("| --- | --- |");
  lines.push(`| 候选版发布说明 | \`${audit.summary.candidateDocReady ? "通过" : "未通过"}\` |`);
  lines.push(`| master 稳定观察起始记录 | \`${audit.summary.stabilizationReady ? "通过" : "未通过"}\` |`);
  lines.push(`| master 稳定观察检查清单 | \`${audit.summary.checklistReady ? "通过" : "未通过"}\` |`);
  lines.push(`| 发布审批清单 | \`${audit.summary.approvalReady ? "通过" : "未通过"}\` |`);
  lines.push(`| 回滚演练记录模板 | \`${audit.summary.rollbackReady ? "通过" : "未通过"}\` |`);
  lines.push(`| 发布后现场核验模板 | \`${audit.summary.onSiteReady ? "通过" : "未通过"}\` |`);
  lines.push(`| master 观察结束结论模板 | \`${audit.summary.stabilizationClosingReady ? "通过" : "未通过"}\` |`);
  lines.push(`| 切版决策记录 | \`${audit.summary.decisionReady ? "通过" : "未通过"}\` |`);
  lines.push(`| master 观察中间结论 | \`${audit.summary.observationCheckpointReady ? "通过" : "未通过"}\` |`);
  lines.push(`| 主链路即时核验记录 | \`${audit.summary.liveVerificationReady ? "通过" : "未通过"}\` |`);
  lines.push(`| 暂缓切版说明 | \`${audit.summary.deferralReady ? "通过" : "未通过"}\` |`);
  lines.push(`| release 门禁日结 | \`${audit.summary.gateDayEndReady ? "通过" : "未通过"}\` |`);
  lines.push(`| 次日重评清单 | \`${audit.summary.reevaluationReady ? "通过" : "未通过"}\` |`);
  lines.push(`| 正式版说明检查表 | \`${audit.summary.releaseNoteChecklistReady ? "通过" : "未通过"}\` |`);
  lines.push(`| 发布前材料现状记录 | \`${audit.summary.prepStatusReady ? "通过" : "未通过"}\` |`);
  lines.push(`| 阻断项数量 | \`${audit.summary.blockerCount}\` |`);
  lines.push("");
  lines.push("## 阻断项");
  lines.push("");
  if (!audit.blockers.length) {
    lines.push("1. 当前自动审计未发现阻断项。");
  } else {
    audit.blockers.forEach((blocker, index) => {
      lines.push(`${index + 1}. ${blocker}`);
    });
  }
  lines.push("");
  lines.push("## 输入文件");
  lines.push("");
  lines.push(`1. \`${audit.inputs.candidateDoc}\``);
  lines.push(`2. \`${audit.inputs.stabilizationDoc}\``);
  lines.push(`3. \`${audit.inputs.checklistDoc}\``);
  lines.push(`4. \`${audit.inputs.approvalDoc}\``);
  lines.push(`5. \`${audit.inputs.rollbackDoc}\``);
  lines.push(`6. \`${audit.inputs.onSiteDoc}\``);
  lines.push(`7. \`${audit.inputs.stabilizationClosingDoc}\``);
  lines.push(`8. \`${audit.inputs.decisionDoc}\``);
  lines.push(`9. \`${audit.inputs.observationCheckpointDoc}\``);
  lines.push(`10. \`${audit.inputs.liveVerificationDoc}\``);
  lines.push(`11. \`${audit.inputs.deferralDoc}\``);
  lines.push(`12. \`${audit.inputs.gateDayEndDoc}\``);
  lines.push(`13. \`${audit.inputs.reevaluationChecklistDoc}\``);
  lines.push(`14. \`${audit.inputs.releaseNoteChecklistDoc}\``);
  lines.push(`15. \`${audit.inputs.prepStatusDoc}\``);
  lines.push("");
  return `${lines.join("\n")}\n`;
}

function main() {
  const rootDir = ensureArg("--root-dir");
  const candidateDocPath = ensureArg("--candidate-doc");
  const stabilizationDocPath = ensureArg("--stabilization-doc");
  const checklistDocPath = ensureArg("--checklist-doc");
  const approvalDocPath = ensureArg("--approval-doc");
  const rollbackDocPath = ensureArg("--rollback-doc");
  const onSiteDocPath = ensureArg("--onsite-doc");
  const stabilizationClosingDocPath = ensureArg("--stabilization-closing-doc");
  const decisionDocPath = ensureArg("--decision-doc");
  const observationCheckpointDocPath = ensureArg("--observation-checkpoint-doc");
  const liveVerificationDocPath = ensureArg("--live-verification-doc");
  const deferralDocPath = ensureArg("--deferral-doc");
  const gateDayEndDocPath = ensureArg("--gate-day-end-doc");
  const reevaluationChecklistDocPath = ensureArg("--reevaluation-checklist-doc");
  const releaseNoteChecklistDocPath = ensureArg("--release-note-checklist-doc");
  const prepStatusDocPath = ensureArg("--prep-status-doc");
  const outputDir = getArgValue(
    "--output-dir",
    path.join(rootDir, "docs", "releases", "audit")
  );

  fs.mkdirSync(outputDir, { recursive: true });

  const audit = buildAudit(
    rootDir,
    candidateDocPath,
    stabilizationDocPath,
    checklistDocPath,
    approvalDocPath,
    rollbackDocPath,
    onSiteDocPath,
    stabilizationClosingDocPath,
    decisionDocPath,
    observationCheckpointDocPath,
    liveVerificationDocPath,
    deferralDocPath,
    gateDayEndDocPath,
    reevaluationChecklistDocPath,
    releaseNoteChecklistDocPath,
    prepStatusDocPath
  );
  const jsonPath = path.join(outputDir, "release-readiness-audit.json");
  const mdPath = path.join(outputDir, "release-readiness-audit.md");

  fs.writeFileSync(jsonPath, `${JSON.stringify(audit, null, 2)}\n`, "utf8");
  fs.writeFileSync(mdPath, renderMarkdown(audit), "utf8");

  console.log(`已生成 release 就绪审计 JSON: ${jsonPath}`);
  console.log(`已生成 release 就绪审计 Markdown: ${mdPath}`);
  console.log(`是否允许创建 release/*: ${audit.summary.canCreateRelease ? "是" : "否"}`);

  if (audit.blockers.length) {
    process.exitCode = 2;
  }
}

main();
