#!/usr/bin/env node

import fs from "fs";
import path from "path";
import { execSync } from "child_process";

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

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, "utf8"));
}

function readText(filePath) {
  return fs.readFileSync(filePath, "utf8");
}

function toRelative(rootDir, filePath) {
  return path.relative(rootDir, filePath) || path.basename(filePath);
}

function extractValue(content, label) {
  const escapedLabel = label.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const regex = new RegExp(`\\|\\s*${escapedLabel}\\s*\\|\\s*([^|]+?)\\s*\\|`);
  const match = content.match(regex);
  return match ? match[1].trim() : "";
}

function extractLineValue(content, prefix) {
  const escapedPrefix = prefix.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const regex = new RegExp(`${escapedPrefix}\\s*[:：]\\s*\\\`?([^\\n\\\`]+)\\\`?`);
  const match = content.match(regex);
  return match ? match[1].trim() : "";
}

function detectVersionTag(evidenceDir) {
  return path.basename(evidenceDir);
}

function resolveGitCommit(rootDir) {
  try {
    return execSync("git rev-parse --short HEAD", {
      cwd: rootDir,
      stdio: ["ignore", "pipe", "ignore"]
    }).toString("utf8").trim();
  } catch (error) {
    return "";
  }
}

function buildSummaryContent({
  rootDir,
  evidenceDir,
  releaseDocPath,
  auditReportPath,
  regressionPath
}) {
  const audit = readJson(auditReportPath);
  const regressionContent = readText(regressionPath);
  const releaseDocContent = readText(releaseDocPath);

  const versionTag = detectVersionTag(evidenceDir);
  const gitCommit = resolveGitCommit(rootDir);
  const mainSample = extractValue(regressionContent, "主链路样例单号");
  const riskSample = extractValue(regressionContent, "风控样例单号");
  const closedSample = extractValue(regressionContent, "关闭单样例单号");
  const compensationSample = extractValue(regressionContent, "补偿样例任务号");
  const releaseDate = extractLineValue(releaseDocContent, "- 发布日期");
  const scopeSystems = extractLineValue(releaseDocContent, "- 涉及系统");
  const scopeModules = extractLineValue(releaseDocContent, "- 涉及模块");

  const lines = [];
  lines.push("# 冻结候选汇总");
  lines.push("");
  lines.push(`- 版本标识：\`${versionTag}\``);
  lines.push(`- 生成时间：\`${new Date().toISOString()}\``);
  lines.push(`- Git 提交号：\`${gitCommit || "UNKNOWN"}\``);
  lines.push(`- 发布日期口径：\`${releaseDate || "UNKNOWN"}\``);
  lines.push("");
  lines.push("## 1. 当前结论");
  lines.push("");
  lines.push(`1. 当前是否允许推进 \`test -> master\`：\`${audit.summary.canPromoteTestToMaster ? "是" : "否"}\``);
  lines.push(`2. 当前是否允许创建 \`release/*\`：\`${audit.summary.canCreateRelease ? "是" : "否"}\``);
  lines.push(`3. 当前冻结候选阻断项数量：\`${audit.summary.blockerCount}\``);
  lines.push("");
  lines.push("## 2. 发布范围摘要");
  lines.push("");
  lines.push(`1. 涉及系统：${scopeSystems || "待补充"}`);
  lines.push(`2. 涉及模块：${scopeModules || "待补充"}`);
  lines.push(`3. 证据包目录：\`${toRelative(rootDir, evidenceDir)}\``);
  lines.push("");
  lines.push("## 3. 关键业务样例");
  lines.push("");
  lines.push(`1. 主链路样例：${mainSample || "待补充"}`);
  lines.push(`2. 风控样例：${riskSample || "待补充"}`);
  lines.push(`3. 关闭单样例：${closedSample || "待补充"}`);
  lines.push(`4. 补偿样例：${compensationSample || "待补充"}`);
  lines.push("");
  lines.push("## 4. 证据完成度");
  lines.push("");
  lines.push("| 项目 | 当前结果 |");
  lines.push("| --- | --- |");
  lines.push(`| 截图总数 | \`${audit.summary.screenshotTotal}\` |`);
  lines.push(`| 已归档截图数 | \`${audit.summary.screenshotDone}\` |`);
  lines.push(`| 未归档截图数 | \`${audit.summary.screenshotPending}\` |`);
  lines.push(`| 人工回归 TODO 数 | \`${audit.summary.regressionTodoCount}\` |`);
  lines.push(`| 发布说明是否仍为草案 | \`${audit.summary.releaseDocIsDraft ? "是" : "否"}\` |`);
  lines.push("");
  lines.push("## 5. 当前阻断");
  lines.push("");
  if (!audit.blockers.length) {
    lines.push("1. 当前无阻断项。");
  } else {
    audit.blockers.forEach((blocker, index) => {
      lines.push(`${index + 1}. ${blocker}`);
    });
  }
  lines.push("");
  lines.push("## 6. 最近应优先补的页面");
  lines.push("");
  if (!audit.details.pendingScreenshots.length) {
    lines.push("当前无待补截图页面。");
  } else {
    lines.push("| 系统 | 终端 | 页面 | 样例编号 |");
    lines.push("| --- | --- | --- | --- |");
    audit.details.pendingScreenshots.slice(0, 10).forEach((item) => {
      lines.push(`| ${item.system} | ${item.terminal} | ${item.pageKey} | ${item.sampleBizNoOrTaskNo || ""} |`);
    });
  }
  lines.push("");
  lines.push("## 7. 下一步建议");
  lines.push("");
  lines.push("1. 先补页面级冻结截图，并同步回填 `screenshot-manifest.csv`。");
  lines.push("2. 再补一体化人工回归归档，把页面、接口、后台、下游事实串起来。");
  lines.push("3. 最后把发布说明从草案升级为冻结候选版，再重新运行自动审计。");
  lines.push("");
  lines.push("## 8. 关联材料");
  lines.push("");
  lines.push(`1. \`${toRelative(rootDir, releaseDocPath)}\``);
  lines.push(`2. \`${toRelative(rootDir, auditReportPath)}\``);
  lines.push(`3. \`${toRelative(rootDir, regressionPath)}\``);
  lines.push("");
  return `${lines.join("\n")}\n`;
}

function main() {
  const evidenceDir = ensureArg("--evidence-dir");
  const auditReportPath = getArgValue(
    "--audit-report",
    path.join(evidenceDir, "records", "freeze-audit-report.json")
  );
  const regressionPath = getArgValue(
    "--regression",
    path.join(evidenceDir, "records", "final-regression-checklist.md")
  );
  const releaseDocPath = ensureArg("--release-doc");
  const rootDir = path.resolve(path.join(evidenceDir, "..", "..", "..", ".."));
  const outputPath = getArgValue(
    "--output",
    path.join(evidenceDir, "records", "freeze-candidate-summary.md")
  );

  const content = buildSummaryContent({
    rootDir,
    evidenceDir,
    releaseDocPath,
    auditReportPath,
    regressionPath
  });

  fs.writeFileSync(outputPath, content, "utf8");
  console.log(`已生成冻结候选汇总: ${outputPath}`);
}

main();
