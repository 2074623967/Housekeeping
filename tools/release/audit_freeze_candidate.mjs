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

function parseCsvLine(line) {
  const values = [];
  let current = "";
  let insideQuote = false;

  for (let index = 0; index < line.length; index += 1) {
    const char = line[index];
    const nextChar = line[index + 1];

    if (char === "\"") {
      if (insideQuote && nextChar === "\"") {
        current += "\"";
        index += 1;
      } else {
        insideQuote = !insideQuote;
      }
      continue;
    }

    if (char === "," && !insideQuote) {
      values.push(current);
      current = "";
      continue;
    }

    current += char;
  }

  values.push(current);
  return values;
}

function readCsv(filePath) {
  const content = fs.readFileSync(filePath, "utf8").trim();
  const lines = content ? content.split(/\r?\n/) : [];
  if (!lines.length) {
    return { header: [], rows: [] };
  }
  const header = parseCsvLine(lines[0]);
  const rows = lines.slice(1).map((line) => {
    const values = parseCsvLine(line);
    const row = {};
    header.forEach((column, index) => {
      row[column] = values[index] || "";
    });
    return row;
  });
  return { header, rows };
}

function normalizeStatus(value) {
  return String(value || "").trim().toUpperCase();
}

function isCompletedStatus(value) {
  return ["DONE", "ARCHIVED", "PASS", "PASSED", "OK", "CAPTURED"].includes(normalizeStatus(value));
}

function requiresSampleBizNo(row) {
  const requiredState = String(row.required_state_or_action || "").trim().toUpperCase();
  const pageKey = String(row.page_key || "").trim();
  const system = String(row.system || "").trim();

  if ([
    "WAIT_CALLBACK",
    "RISK_REVIEW",
    "SUCCESS",
    "CLOSED",
    "PREPAY_CREATED",
    "MANUAL_RESOLVED",
    "REPLAYED"
  ].includes(requiredState)) {
    return true;
  }

  return [
    "clearing-system/admin-web/events",
    "settlement-system/admin-web/orders",
    "settlement-system/admin-web/payouts",
    "accounting-system/admin-web/events"
  ].includes(`${system}/${row.terminal}/${pageKey}`);
}

function toRelative(rootDir, filePath) {
  return path.relative(rootDir, filePath) || path.basename(filePath);
}

function readText(filePath) {
  return fs.existsSync(filePath) ? fs.readFileSync(filePath, "utf8") : "";
}

function countMarkdownTodos(content) {
  const lines = content.split(/\r?\n/);
  return lines.filter((line) => /\|\s*TODO\s*\|/.test(line)).length;
}

function buildManifestAudit(evidenceDir, manifestPath) {
  const manifest = readCsv(manifestPath);
  const rows = manifest.rows;
  const pendingRows = [];
  const missingFileRows = [];
  const doneWithoutBizNoRows = [];

  rows.forEach((row) => {
    const status = normalizeStatus(row.archive_status);
    const screenshotFile = row.screenshot_file || "";
    const screenshotPath = screenshotFile
      ? path.join(evidenceDir, screenshotFile)
      : "";

    if (!isCompletedStatus(status)) {
      pendingRows.push(row);
      return;
    }

    if (!screenshotFile || !fs.existsSync(screenshotPath)) {
      missingFileRows.push(row);
    }

    if (requiresSampleBizNo(row) && !String(row.sample_biz_no_or_task_no || "").trim()) {
      doneWithoutBizNoRows.push(row);
    }
  });

  return {
    total: rows.length,
    done: rows.filter((row) => isCompletedStatus(row.archive_status)).length,
    pending: pendingRows.length,
    pendingRows,
    missingFileRows,
    doneWithoutBizNoRows
  };
}

function buildRegressionAudit(regressionPath) {
  const content = readText(regressionPath);
  const todoCount = countMarkdownTodos(content);
  const canPromoteTestToMaster = /\|\s*是否允许推进 `test -> master`\s*\|\s*`?是`?\s*\|/.test(content);
  const canCreateRelease = /\|\s*是否允许创建 `release\/\*`\s*\|\s*`?是`?\s*\|/.test(content);

  return {
    todoCount,
    canPromoteTestToMaster,
    canCreateRelease
  };
}

function buildReleaseDocAudit(releaseDocPath) {
  const content = readText(releaseDocPath);
  const isDraft = /状态：草案/.test(content) || /草案，不代表当前允许发布/.test(content);
  const blocksMaster = /仍不允许自动合并 `master`/.test(content) || /暂不允许自动合并/.test(content);
  const blocksRelease = /仍不允许自动创建 `release\/\*`/.test(content) || /暂不允许自动创建/.test(content);

  return {
    isDraft,
    blocksMaster,
    blocksRelease
  };
}

function buildAudit(rootDir, evidenceDir, manifestPath, regressionPath, releaseDocPath) {
  const manifestAudit = buildManifestAudit(evidenceDir, manifestPath);
  const regressionAudit = buildRegressionAudit(regressionPath);
  const releaseDocAudit = buildReleaseDocAudit(releaseDocPath);

  const blockers = [];

  if (manifestAudit.pending > 0) {
    blockers.push(`截图台账仍有 ${manifestAudit.pending} 项未归档完成`);
  }
  if (manifestAudit.missingFileRows.length > 0) {
    blockers.push(`截图台账中有 ${manifestAudit.missingFileRows.length} 项标记完成但截图文件不存在`);
  }
  if (manifestAudit.doneWithoutBizNoRows.length > 0) {
    blockers.push(`截图台账中有 ${manifestAudit.doneWithoutBizNoRows.length} 项已完成但缺业务编号或任务号`);
  }
  if (regressionAudit.todoCount > 0) {
    blockers.push(`人工回归归档中仍有 ${regressionAudit.todoCount} 个 TODO 待回填`);
  }
  if (releaseDocAudit.isDraft) {
    blockers.push("发布说明仍处于草案状态");
  }
  if (releaseDocAudit.blocksMaster) {
    blockers.push("当前发布文档仍明确阻断 test -> master");
  }
  if (releaseDocAudit.blocksRelease) {
    blockers.push("当前发布文档仍明确阻断 master -> release/*");
  }

  const canPromoteTestToMaster = blockers.length === 0
    && regressionAudit.canPromoteTestToMaster
    && !releaseDocAudit.blocksMaster;
  const canCreateRelease = blockers.length === 0
    && regressionAudit.canCreateRelease
    && !releaseDocAudit.blocksRelease
    && !releaseDocAudit.isDraft;

  return {
    generatedAt: new Date().toISOString(),
    rootDir,
    evidenceDir,
    inputs: {
      manifest: toRelative(rootDir, manifestPath),
      regression: toRelative(rootDir, regressionPath),
      releaseDoc: toRelative(rootDir, releaseDocPath)
    },
    summary: {
      screenshotTotal: manifestAudit.total,
      screenshotDone: manifestAudit.done,
      screenshotPending: manifestAudit.pending,
      regressionTodoCount: regressionAudit.todoCount,
      releaseDocIsDraft: releaseDocAudit.isDraft,
      canPromoteTestToMaster,
      canCreateRelease,
      blockerCount: blockers.length
    },
    blockers,
    details: {
      pendingScreenshots: manifestAudit.pendingRows.slice(0, 20).map((row) => ({
        system: row.system,
        terminal: row.terminal,
        pageKey: row.page_key,
        status: row.archive_status,
        sampleBizNoOrTaskNo: row.sample_biz_no_or_task_no
      })),
      missingScreenshotFiles: manifestAudit.missingFileRows.slice(0, 20).map((row) => ({
        system: row.system,
        terminal: row.terminal,
        pageKey: row.page_key,
        screenshotFile: row.screenshot_file
      })),
      doneWithoutBizNo: manifestAudit.doneWithoutBizNoRows.slice(0, 20).map((row) => ({
        system: row.system,
        terminal: row.terminal,
        pageKey: row.page_key,
        screenshotFile: row.screenshot_file
      }))
    }
  };
}

function buildMarkdownReport(audit) {
  const lines = [];
  lines.push("# 冻结候选自动审计报告");
  lines.push("");
  lines.push(`- 生成时间：\`${audit.generatedAt}\``);
  lines.push(`- 证据包：\`${toRelative(audit.rootDir, audit.evidenceDir)}\``);
  lines.push(`- 是否允许推进 \`test -> master\`：\`${audit.summary.canPromoteTestToMaster ? "是" : "否"}\``);
  lines.push(`- 是否允许创建 \`release/*\`：\`${audit.summary.canCreateRelease ? "是" : "否"}\``);
  lines.push("");
  lines.push("## 汇总");
  lines.push("");
  lines.push("| 项目 | 结果 |");
  lines.push("| --- | --- |");
  lines.push(`| 截图总数 | \`${audit.summary.screenshotTotal}\` |`);
  lines.push(`| 已归档截图数 | \`${audit.summary.screenshotDone}\` |`);
  lines.push(`| 未归档截图数 | \`${audit.summary.screenshotPending}\` |`);
  lines.push(`| 人工回归 TODO 数 | \`${audit.summary.regressionTodoCount}\` |`);
  lines.push(`| 发布说明是否仍为草案 | \`${audit.summary.releaseDocIsDraft ? "是" : "否"}\` |`);
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
  lines.push("## 重点待补页面");
  lines.push("");

  if (!audit.details.pendingScreenshots.length) {
    lines.push("当前无待补页面。");
  } else {
    lines.push("| 系统 | 终端 | 页面 | 状态 | 样例编号 |");
    lines.push("| --- | --- | --- | --- | --- |");
    audit.details.pendingScreenshots.forEach((item) => {
      lines.push(
        `| ${item.system} | ${item.terminal} | ${item.pageKey} | ${item.status || "TODO"} | ${item.sampleBizNoOrTaskNo || ""} |`
      );
    });
  }

  lines.push("");
  lines.push("## 输入文件");
  lines.push("");
  lines.push(`1. \`${audit.inputs.manifest}\``);
  lines.push(`2. \`${audit.inputs.regression}\``);
  lines.push(`3. \`${audit.inputs.releaseDoc}\``);
  lines.push("");
  return `${lines.join("\n")}\n`;
}

function main() {
  const evidenceDir = ensureArg("--evidence-dir");
  const manifestPath = getArgValue(
    "--manifest",
    path.join(evidenceDir, "screenshot-manifest.csv")
  );
  const regressionPath = getArgValue(
    "--regression",
    path.join(evidenceDir, "records", "final-regression-checklist.md")
  );
  const releaseDocPath = ensureArg("--release-doc");
  const rootDir = path.resolve(path.join(evidenceDir, "..", "..", "..", ".."));

  if (!fs.existsSync(manifestPath)) {
    throw new Error(`截图台账不存在: ${manifestPath}`);
  }
  if (!fs.existsSync(regressionPath)) {
    throw new Error(`人工回归文件不存在: ${regressionPath}`);
  }
  if (!fs.existsSync(releaseDocPath)) {
    throw new Error(`发布说明不存在: ${releaseDocPath}`);
  }

  const audit = buildAudit(rootDir, evidenceDir, manifestPath, regressionPath, releaseDocPath);
  const outputJsonPath = path.join(evidenceDir, "records", "freeze-audit-report.json");
  const outputMdPath = path.join(evidenceDir, "records", "freeze-audit-report.md");

  fs.writeFileSync(outputJsonPath, `${JSON.stringify(audit, null, 2)}\n`, "utf8");
  fs.writeFileSync(outputMdPath, buildMarkdownReport(audit), "utf8");

  console.log(`已生成自动审计 JSON: ${outputJsonPath}`);
  console.log(`已生成自动审计 Markdown: ${outputMdPath}`);
  console.log(`是否允许 test -> master: ${audit.summary.canPromoteTestToMaster ? "是" : "否"}`);
  console.log(`是否允许 master -> release/*: ${audit.summary.canCreateRelease ? "是" : "否"}`);

  if (audit.blockers.length) {
    process.exitCode = 2;
  }
}

main();
