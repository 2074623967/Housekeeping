#!/usr/bin/env node

import fs from "fs";
import path from "path";
import { createRequire } from "module";

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

function escapeCsvCell(value) {
  const normalized = value == null ? "" : String(value);
  if (normalized.includes(",") || normalized.includes("\"") || normalized.includes("\n")) {
    return `"${normalized.replace(/"/g, "\"\"")}"`;
  }
  return normalized;
}

function readManifest(manifestPath) {
  const content = fs.readFileSync(manifestPath, "utf8").trim();
  const lines = content.split(/\r?\n/);
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

function writeManifest(manifestPath, header, rows) {
  const lines = [header.join(",")];
  rows.forEach((row) => {
    lines.push(header.map((column) => escapeCsvCell(row[column] || "")).join(","));
  });
  fs.writeFileSync(manifestPath, `${lines.join("\n")}\n`, "utf8");
}

function buildDefaultOutput(row) {
  return path.posix.join(
    "screenshots",
    row.system || "unknown-system",
    row.terminal || "unknown-terminal",
    `${row.page_key || "unknown-page"}.png`
  );
}

function buildBaseUrlKey(row) {
  return `${row.system}/${row.terminal}`;
}

function buildDefaultBaseUrls() {
  return {
    "payment-core/admin-web": "http://127.0.0.1:5174",
    "payment-core/app-web": "http://127.0.0.1:5175",
    "payment-core/h5-web": "http://127.0.0.1:5176",
    "payment-core/pc-web": "http://127.0.0.1:5177",
    "clearing-system/admin-web": "http://127.0.0.1:5273",
    "settlement-system/admin-web": "http://127.0.0.1:5274",
    "accounting-system/admin-web": "http://127.0.0.1:5275"
  };
}

function buildPathTemplate(row) {
  const key = `${row.system}/${row.terminal}/${row.page_key}`;
  const mapping = {
    "payment-core/admin-web/dashboard": "/dashboard",
    "payment-core/admin-web/orders": "/orders",
    "payment-core/admin-web/payment-list": "/payments",
    "payment-core/admin-web/payment-detail-wait-callback": "/payments/{{paymentOrderIdWaitCallback}}",
    "payment-core/admin-web/payment-detail-risk-review": "/payments/{{paymentOrderIdRiskReview}}",
    "payment-core/admin-web/payment-detail-success": "/payments/{{paymentOrderIdSuccess}}",
    "payment-core/admin-web/payment-detail-closed": "/payments/{{paymentOrderIdClosed}}",
    "payment-core/admin-web/payment-flows": "/payment-flows",
    "payment-core/admin-web/payment-events": "/payment-events",
    "payment-core/admin-web/payment-issues": "/payment-issues",
    "payment-core/admin-web/cashier-sessions": "/cashier-sessions",
    "payment-core/admin-web/dead-letter-pending-review": "/payment-task-center",
    "payment-core/admin-web/dead-letter-ready-replay": "/payment-task-center",
    "payment-core/admin-web/dead-letter-manual-resolved": "/payment-task-center",
    "payment-core/admin-web/dead-letter-replayed": "/payment-task-center",
    "payment-core/app-web/recharge-entry": "/recharge",
    "payment-core/app-web/withdraw-entry": "/withdraw",
    "payment-core/app-web/transfer-entry": "/transfer",
    "payment-core/app-web/balance-pay-entry": "/balance-pay",
    "payment-core/app-web/cashier-prepay-created": "/cashier/{{prepayOrderNoPrepayCreated}}",
    "payment-core/app-web/cashier-wait-callback": "/cashier/{{prepayOrderNoWaitCallback}}",
    "payment-core/app-web/cashier-risk-review": "/cashier/{{prepayOrderNoRiskReview}}",
    "payment-core/app-web/result-success": "/payment-result/{{paymentOrderIdSuccess}}",
    "payment-core/app-web/result-closed": "/payment-result/{{paymentOrderIdClosed}}",
    "payment-core/app-web/result-risk-review": "/payment-result/{{paymentOrderIdRiskReview}}",
    "payment-core/app-web/result-wait-callback": "/payment-result/{{paymentOrderIdWaitCallback}}",
    "payment-core/h5-web/recharge-entry": "/recharge",
    "payment-core/h5-web/withdraw-entry": "/withdraw",
    "payment-core/h5-web/transfer-entry": "/transfer",
    "payment-core/h5-web/balance-pay-entry": "/balance-pay",
    "payment-core/h5-web/cashier-wait-callback": "/cashier/{{prepayOrderNoWaitCallback}}",
    "payment-core/h5-web/cashier-risk-review": "/cashier/{{prepayOrderNoRiskReview}}",
    "payment-core/h5-web/result-success": "/payment-result/{{paymentOrderIdSuccess}}",
    "payment-core/h5-web/result-closed": "/payment-result/{{paymentOrderIdClosed}}",
    "payment-core/h5-web/result-risk-review": "/payment-result/{{paymentOrderIdRiskReview}}",
    "payment-core/h5-web/result-wait-callback": "/payment-result/{{paymentOrderIdWaitCallback}}",
    "payment-core/pc-web/recharge-entry": "/recharge",
    "payment-core/pc-web/withdraw-entry": "/withdraw",
    "payment-core/pc-web/transfer-entry": "/transfer",
    "payment-core/pc-web/balance-pay-entry": "/balance-pay",
    "payment-core/pc-web/cashier-wait-callback": "/cashier/{{prepayOrderNoWaitCallback}}",
    "payment-core/pc-web/cashier-risk-review": "/cashier/{{prepayOrderNoRiskReview}}",
    "payment-core/pc-web/result-success": "/payment-result/{{paymentOrderIdSuccess}}",
    "payment-core/pc-web/result-closed": "/payment-result/{{paymentOrderIdClosed}}",
    "payment-core/pc-web/result-risk-review": "/payment-result/{{paymentOrderIdRiskReview}}",
    "payment-core/pc-web/result-wait-callback": "/payment-result/{{paymentOrderIdWaitCallback}}",
    "clearing-system/admin-web/events": "/events",
    "settlement-system/admin-web/orders": "/orders",
    "settlement-system/admin-web/payouts": "/payouts",
    "accounting-system/admin-web/events": "/events"
  };
  return mapping[key] || "";
}

function buildSampleValues() {
  return {
    paymentOrderIdWaitCallback: "",
    paymentOrderIdRiskReview: "",
    paymentOrderIdSuccess: "",
    paymentOrderIdClosed: "",
    prepayOrderNoPrepayCreated: "",
    prepayOrderNoWaitCallback: "",
    prepayOrderNoRiskReview: ""
  };
}

function buildDefaultPlan(manifest) {
  const baseUrls = buildDefaultBaseUrls();
  manifest.rows.forEach((row) => {
    baseUrls[buildBaseUrlKey(row)] = baseUrls[buildBaseUrlKey(row)] || "";
  });
  return {
    generatedAt: new Date().toISOString(),
    note: "优先补齐 baseUrls 和 sampleValues；若某页需要特殊跳转，也可直接覆盖单条 url。",
    baseUrls,
    sampleValues: buildSampleValues(),
    targets: manifest.rows.map((row) => ({
      system: row.system,
      terminal: row.terminal,
      pageKey: row.page_key,
      pageName: row.page_name,
      requiredStateOrAction: row.required_state_or_action,
      sampleBizNoOrTaskNo: row.sample_biz_no_or_task_no,
      baseUrlKey: buildBaseUrlKey(row),
      pathTemplate: buildPathTemplate(row),
      output: row.screenshot_file || buildDefaultOutput(row),
      url: "",
      waitForSelector: "",
      fullPage: true,
      viewport: row.terminal === "app-web"
        ? { width: 430, height: 932 }
        : row.terminal === "h5-web"
          ? { width: 390, height: 844 }
          : row.terminal === "pc-web"
            ? { width: 1440, height: 1280 }
            : { width: 1600, height: 1200 },
      archiveStatus: row.archive_status || "TODO",
      notes: row.notes || ""
    }))
  };
}

function initPlan(manifestPath, evidenceDir) {
  const manifest = readManifest(manifestPath);
  const plan = buildDefaultPlan(manifest);
  const planPath = path.join(evidenceDir, "capture-plan.json");
  fs.writeFileSync(planPath, `${JSON.stringify(plan, null, 2)}\n`, "utf8");
  console.log(`已生成截图采集计划: ${planPath}`);
}

function loadPlaywrightModule() {
  const roots = [
    process.env.PLAYWRIGHT_NODE_MODULES,
    "/Users/abc123/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules",
    process.env.NODE_PATH
  ].filter(Boolean);

  for (const root of roots) {
    try {
      const resolver = createRequire(path.join(root, "__resolver__.cjs"));
      return resolver("playwright");
    } catch (error) {
      // continue
    }
  }

  try {
    return createRequire(import.meta.url)("playwright");
  } catch (error) {
    throw new Error(
      "未找到 playwright。可通过设置 PLAYWRIGHT_NODE_MODULES 指向可用 node_modules，或使用桌面线程自带依赖路径。"
    );
  }
}

function applyTemplate(template, values) {
  return template.replace(/\{\{([^}]+)\}\}/g, (_, key) => values[key] || "");
}

function resolveTargetUrl(plan, target) {
  if (target.url) {
    return target.url;
  }
  if (!target.baseUrlKey || !target.pathTemplate) {
    return "";
  }
  const baseUrl = (plan.baseUrls && plan.baseUrls[target.baseUrlKey]) || "";
  if (!baseUrl) {
    return "";
  }
  const relativePath = applyTemplate(target.pathTemplate, plan.sampleValues || {});
  if (!relativePath || relativePath.includes("{{")) {
    return "";
  }
  return `${baseUrl.replace(/\/$/, "")}${relativePath}`;
}

async function capture(planPath, manifestPath, evidenceDir, dryRun) {
  const plan = JSON.parse(fs.readFileSync(planPath, "utf8"));
  const manifest = readManifest(manifestPath);
  const targets = Array.isArray(plan.targets) ? plan.targets : [];
  const resolvedTargets = targets.map((target) => ({
    ...target,
    resolvedUrl: resolveTargetUrl(plan, target)
  }));
  const executableTargets = resolvedTargets.filter((target) => target.resolvedUrl && target.output);
  const skippedTargets = resolvedTargets.filter((target) => !target.resolvedUrl || !target.output);

  console.log(`计划总数: ${targets.length}`);
  console.log(`可执行截图数: ${executableTargets.length}`);
  console.log(`缺少 URL 或输出路径的目标数: ${skippedTargets.length}`);

  if (dryRun) {
    skippedTargets.slice(0, 20).forEach((target) => {
      console.log(`SKIP ${target.system}/${target.terminal}/${target.pageKey}: 缺少可解析的 url 或 output`);
    });
    return;
  }

  if (!executableTargets.length) {
    throw new Error("没有可执行的截图目标，请先在 capture-plan.json 中补齐 baseUrls/sampleValues 或单条 url。");
  }

  const playwright = loadPlaywrightModule();
  const browser = await playwright.chromium.launch({
    headless: getArgValue("--headless", "true") !== "false"
  });

  const report = {
    generatedAt: new Date().toISOString(),
    planPath,
    results: []
  };

  try {
    for (const target of executableTargets) {
      const context = await browser.newContext({
        viewport: target.viewport || { width: 1440, height: 1280 },
        deviceScaleFactor: 1
      });
      const page = await context.newPage();
      const timeoutMs = Number(target.timeoutMs || 30000);

      try {
        await page.goto(target.resolvedUrl, {
          waitUntil: target.waitUntil || "networkidle",
          timeout: timeoutMs
        });

        if (target.waitForSelector) {
          await page.waitForSelector(target.waitForSelector, { timeout: timeoutMs });
        }

        const absoluteOutput = path.join(evidenceDir, target.output);
        fs.mkdirSync(path.dirname(absoluteOutput), { recursive: true });
        await page.screenshot({
          path: absoluteOutput,
          fullPage: target.fullPage !== false
        });

        const manifestRow = manifest.rows.find(
          (row) => row.system === target.system
            && row.terminal === target.terminal
            && row.page_key === target.pageKey
        );
        if (manifestRow) {
          manifestRow.screenshot_file = target.output;
          manifestRow.archive_status = "CAPTURED";
          manifestRow.notes = target.notes || manifestRow.notes || "";
        }

        report.results.push({
          pageKey: target.pageKey,
          status: "CAPTURED",
          output: target.output,
          url: target.resolvedUrl
        });
        console.log(`CAPTURED ${target.pageKey} -> ${target.output}`);
      } catch (error) {
        report.results.push({
          pageKey: target.pageKey,
          status: "FAILED",
          output: target.output,
          url: target.resolvedUrl,
          error: error.message
        });
        console.error(`FAILED ${target.pageKey}: ${error.message}`);
      } finally {
        await context.close();
      }
    }
  } finally {
    await browser.close();
  }

  writeManifest(manifestPath, manifest.header, manifest.rows);
  fs.writeFileSync(
    path.join(evidenceDir, "capture-report.json"),
    `${JSON.stringify(report, null, 2)}\n`,
    "utf8"
  );
}

async function main() {
  if (hasFlag("--init-plan")) {
    const manifestPath = ensureArg("--manifest");
    const evidenceDir = ensureArg("--evidence-dir");
    initPlan(manifestPath, evidenceDir);
    return;
  }

  const planPath = ensureArg("--plan");
  const manifestPath = ensureArg("--manifest");
  const evidenceDir = ensureArg("--evidence-dir");
  await capture(planPath, manifestPath, evidenceDir, hasFlag("--dry-run"));
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
