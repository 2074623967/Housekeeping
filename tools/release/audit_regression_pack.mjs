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

function normalize(value) {
  return String(value || "").trim().toUpperCase();
}

function isDone(value) {
  return ["DONE", "PASS", "PASSED", "OK", "N/A", "NOT_APPLICABLE", "OPTIONAL", "HISTORICAL"].includes(normalize(value));
}

function fileExists(baseDir, relativePath) {
  if (!String(relativePath || "").trim()) {
    return false;
  }
  return fs.existsSync(path.join(baseDir, relativePath));
}

function readText(filePath) {
  return fs.existsSync(filePath) ? fs.readFileSync(filePath, "utf8") : "";
}

function countSummaryTodos(content) {
  return content.split(/\r?\n/).filter((line) => /\|\s*TODO\s*\|/.test(line)).length;
}

function auditScenarioRows(packDir, rows) {
  const incompleteRows = [];
  const missingSampleRows = [];
  const missingEvidenceRows = [];
  const historicalPageRows = [];
  const historicalRollbackRows = [];

  rows.forEach((row) => {
    const checkpoints = [
      ["api_status", row.api_status],
      ["db_status", row.db_status],
      ["event_status", row.event_status],
      ["page_status", row.page_status],
      ["rollback_or_compensation_status", row.rollback_or_compensation_status]
    ];

    const incompleteFields = checkpoints
      .filter((item) => !isDone(item[1]))
      .map((item) => item[0]);

    if (incompleteFields.length > 0) {
      incompleteRows.push({
        scenarioId: row.scenario_id,
        scenarioName: row.scenario_name,
        incompleteFields
      });
    }

    if (!String(row.sample_biz_no_or_task_no || "").trim()) {
      missingSampleRows.push({
        scenarioId: row.scenario_id,
        scenarioName: row.scenario_name
      });
    }

    const evidencePairs = [
      ["api", row.api_status, row.api_evidence_file],
      ["database", row.db_status, row.db_evidence_file],
      ["event", row.event_status, row.event_evidence_file],
      ["page", row.page_status, row.page_evidence_file]
    ];

    evidencePairs.forEach(([kind, status, evidenceFile]) => {
      const normalized = normalize(status);
      if (["DONE", "PASS", "PASSED", "OK"].includes(normalized) && !fileExists(packDir, evidenceFile)) {
        missingEvidenceRows.push({
          scenarioId: row.scenario_id,
          scenarioName: row.scenario_name,
          kind,
          evidenceFile
        });
      }
    });

    if (normalize(row.page_status) === "HISTORICAL") {
      historicalPageRows.push({
        scenarioId: row.scenario_id,
        scenarioName: row.scenario_name,
        evidenceFile: row.page_evidence_file
      });
    }

    if (normalize(row.rollback_or_compensation_status) === "HISTORICAL") {
      historicalRollbackRows.push({
        scenarioId: row.scenario_id,
        scenarioName: row.scenario_name
      });
    }
  });

  return {
    incompleteRows,
    missingSampleRows,
    missingEvidenceRows,
    historicalPageRows,
    historicalRollbackRows
  };
}

function auditSummary(summaryPath) {
  const content = readText(summaryPath);
  return {
    todoCount: countSummaryTodos(content),
    canPromoteTestToMaster: /\|\s*是否允许推进 `test -> master`\s*\|\s*`?是`?\s*\|/.test(content),
    canCreateRelease: /\|\s*是否允许创建 `release\/\*`\s*\|\s*`?是`?\s*\|/.test(content)
  };
}

function main() {
  const rootDir = path.resolve(ensureArg("--root"));
  const packDir = path.resolve(ensureArg("--pack"));
  const scenarioPath = path.join(packDir, "regression-scenarios.csv");
  const summaryPath = path.join(packDir, "records", "regression-summary.md");

  if (!fs.existsSync(scenarioPath)) {
    throw new Error(`未找到场景台账: ${scenarioPath}`);
  }

  if (!fs.existsSync(summaryPath)) {
    throw new Error(`未找到回归汇总: ${summaryPath}`);
  }

  const scenarioAudit = auditScenarioRows(packDir, readCsv(scenarioPath).rows);
  const summaryAudit = auditSummary(summaryPath);
  const blockers = [];

  if (scenarioAudit.incompleteRows.length > 0) {
    blockers.push(`仍有 ${scenarioAudit.incompleteRows.length} 个回归场景未完成所有检查点`);
  }
  if (scenarioAudit.missingSampleRows.length > 0) {
    blockers.push(`仍有 ${scenarioAudit.missingSampleRows.length} 个回归场景缺业务编号或任务号`);
  }
  if (scenarioAudit.missingEvidenceRows.length > 0) {
    blockers.push(`仍有 ${scenarioAudit.missingEvidenceRows.length} 个回归证据文件缺失`);
  }
  if (scenarioAudit.historicalPageRows.length > 0) {
    blockers.push(`仍有 ${scenarioAudit.historicalPageRows.length} 个场景仅回填历史页面截图，缺当前同批次冻结截图`);
  }
  if (scenarioAudit.historicalRollbackRows.length > 0) {
    blockers.push(`仍有 ${scenarioAudit.historicalRollbackRows.length} 个场景仅回填历史补偿/回滚证据，缺当前同批次闭环证据`);
  }
  if (summaryAudit.todoCount > 0) {
    blockers.push(`回归汇总中仍有 ${summaryAudit.todoCount} 个 TODO 待回填`);
  }

  const report = {
    generatedAt: new Date().toISOString(),
    rootDir,
    packDir,
    summary: {
      scenarioCount: readCsv(scenarioPath).rows.length,
      incompleteScenarioCount: scenarioAudit.incompleteRows.length,
      missingSampleCount: scenarioAudit.missingSampleRows.length,
      missingEvidenceCount: scenarioAudit.missingEvidenceRows.length,
      historicalPageCount: scenarioAudit.historicalPageRows.length,
      historicalRollbackCount: scenarioAudit.historicalRollbackRows.length,
      summaryTodoCount: summaryAudit.todoCount,
      canPromoteTestToMaster: blockers.length === 0 && summaryAudit.canPromoteTestToMaster,
      canCreateRelease: blockers.length === 0 && summaryAudit.canCreateRelease,
      blockerCount: blockers.length
    },
    blockers,
    details: {
      incompleteScenarios: scenarioAudit.incompleteRows,
      missingSampleScenarios: scenarioAudit.missingSampleRows,
      missingEvidenceFiles: scenarioAudit.missingEvidenceRows,
      historicalPageScenarios: scenarioAudit.historicalPageRows,
      historicalRollbackScenarios: scenarioAudit.historicalRollbackRows
    }
  };

  const outputPath = getArgValue("--output");
  const serialized = `${JSON.stringify(report, null, 2)}\n`;
  if (outputPath) {
    fs.writeFileSync(path.resolve(outputPath), serialized, "utf8");
  } else {
    process.stdout.write(serialized);
  }
}

main();
