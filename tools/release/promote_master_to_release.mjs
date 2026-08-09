#!/usr/bin/env node

import fs from "fs";
import path from "path";
import { execFileSync } from "child_process";

const argv = process.argv.slice(2);

function getArg(flag, fallback = "") {
  const index = argv.indexOf(flag);
  return index >= 0 && index + 1 < argv.length ? argv[index + 1] : fallback;
}

function hasFlag(flag) {
  return argv.includes(flag);
}

function fail(message) {
  console.error(`[release-guard] BLOCKED: ${message}`);
  process.exitCode = 2;
}

function git(rootDir, args) {
  return execFileSync("git", ["-C", rootDir, ...args], {
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"]
  }).trim();
}

function parseDate(value) {
  const parsed = new Date(`${value}T00:00:00+08:00`);
  if (Number.isNaN(parsed.getTime())) {
    throw new Error(`无效日期: ${value}`);
  }
  return parsed;
}

function replaceOnce(content, source, target) {
  if (!content.includes(source)) {
    throw new Error(`发布说明缺少预期文本: ${source}`);
  }
  return content.replace(source, target);
}

function main() {
  const rootDir = path.resolve(getArg("--root", process.cwd()));
  const releaseBranch = getArg("--release-branch", "release/v1.1.0");
  const targetDate = getArg("--target-date", "2026-08-10");
  const effectiveDate = getArg(
    "--effective-date",
    new Intl.DateTimeFormat("en-CA", {
      timeZone: "Asia/Shanghai",
      year: "numeric",
      month: "2-digit",
      day: "2-digit"
    }).format(new Date())
  );
  const auditPath = path.resolve(
    getArg(
      "--audit",
      path.join(rootDir, "docs", "releases", "audit", "release-readiness-audit.json")
    )
  );
  const prefilledNotePath = path.resolve(
    getArg(
      "--prefilled-note",
      path.join(rootDir, "docs", "releases", "v1.1.0-正式版发布说明-预填版.md")
    )
  );
  const releaseWorktree = path.resolve(
    getArg("--release-worktree", path.join("/tmp", "home-service-payment-system-release-v1.1.0"))
  );
  const execute = hasFlag("--execute");

  try {
    if (!fs.existsSync(auditPath)) {
      throw new Error(`未找到 release readiness 审计文件: ${auditPath}`);
    }
    if (!fs.existsSync(prefilledNotePath)) {
      throw new Error(`未找到正式版发布说明预填版: ${prefilledNotePath}`);
    }

    const branch = git(rootDir, ["branch", "--show-current"]);
    if (branch !== "master") {
      throw new Error(`当前分支不是 master，而是 ${branch || "detached HEAD"}`);
    }

    const status = git(rootDir, ["status", "--porcelain"]);
    if (status) {
      throw new Error("master 工作树不干净，禁止自动切 release");
    }

    const target = parseDate(targetDate);
    const effective = parseDate(effectiveDate);
    if (effective < target) {
      throw new Error(`尚未到目标切版日期，目标 ${targetDate}，当前生效日期 ${effectiveDate}`);
    }

    const branchList = git(rootDir, ["branch", "--list", releaseBranch]);
    if (branchList) {
      throw new Error(`目标分支已存在，拒绝覆盖: ${releaseBranch}`);
    }

    const audit = JSON.parse(fs.readFileSync(auditPath, "utf8"));
    if (!audit.summary || audit.summary.canCreateRelease !== true) {
      throw new Error("release readiness 审计未通过，禁止创建 release 分支");
    }

    const masterSha = git(rootDir, ["rev-parse", "master"]);
    const plan = {
      releaseBranch,
      effectiveDate,
      targetDate,
      masterSha,
      auditPath: path.relative(rootDir, auditPath),
      prefilledNotePath: path.relative(rootDir, prefilledNotePath),
      releaseWorktree,
      action: execute ? "execute" : "dry-run"
    };

    if (!execute) {
      console.log(JSON.stringify({ status: "READY_FOR_RELEASE", plan }, null, 2));
      return;
    }

    if (fs.existsSync(releaseWorktree)) {
      throw new Error(`release 工作树已存在，拒绝覆盖: ${releaseWorktree}`);
    }

    git(rootDir, ["worktree", "add", releaseWorktree, "-b", releaseBranch, "master"]);

    const finalNotePath = path.join(
      releaseWorktree,
      "docs",
      "releases",
      "v1.1.0-发布说明.md"
    );
    let releaseNote = fs.readFileSync(prefilledNotePath, "utf8");
    releaseNote = replaceOnce(releaseNote, "> 状态：`预填版`", "> 状态：`正式版`");
    releaseNote = replaceOnce(
      releaseNote,
      "> 当前结论：`仅完成预填，不代表今天允许切版`",
      "> 当前结论：`已满足切版条件，作为正式发布说明生效`"
    );
    releaseNote = replaceOnce(releaseNote, "计划发布日期：`2026-08-10`", `发布日期：\`${effectiveDate}\``);
    fs.writeFileSync(finalNotePath, releaseNote, "utf8");

    const cutoverRecordPath = path.join(
      releaseWorktree,
      "docs",
      "releases",
      `${effectiveDate}-v1.1.0正式切版记录.md`
    );
    const packageTemplateDir = path.join(
      rootDir,
      "docs",
      "releases",
      "templates",
      "v1.1.0"
    );
    const packageDir = path.join(releaseWorktree, "docs", "releases", "v1.1.0");
    if (!fs.existsSync(packageTemplateDir)) {
      throw new Error(`未找到版本变更包模板目录: ${packageTemplateDir}`);
    }
    fs.mkdirSync(packageDir, { recursive: true });
    for (const fileName of fs.readdirSync(packageTemplateDir)) {
      const sourcePath = path.join(packageTemplateDir, fileName);
      const targetPath = path.join(packageDir, fileName);
      const packageContent = fs
        .readFileSync(sourcePath, "utf8")
        .replaceAll("release 分支自动生成后填写", `正式版，分支 \`${releaseBranch}\``)
        .replaceAll("来源 SHA：`待填写`", `来源 SHA：\`${masterSha}\``)
        .replaceAll("发布日期：`待填写`", `发布日期：\`${effectiveDate}\``)
        .replaceAll("发布窗口：`待填写`", `发布窗口：\`${effectiveDate}\``);
      fs.writeFileSync(targetPath, packageContent, "utf8");
    }
    fs.writeFileSync(
      cutoverRecordPath,
      [
        `# ${effectiveDate} v1.1.0 正式切版记录`,
        "",
        `- 发布分支：\`${releaseBranch}\``,
        `- 来源分支：\`master\``,
        `- 来源 SHA：\`${masterSha}\``,
        `- release readiness 审计：通过`,
        `- 执行方式：\`promote_master_to_release.mjs --execute\``,
        "",
        "## 结论",
        "",
        "本记录证明本次 release 分支基于已通过门禁的 master 创建，正式发布说明已从预填版升级。",
        ""
      ].join("\n"),
      "utf8"
    );

    git(releaseWorktree, [
      "add",
      "docs/releases/v1.1.0-发布说明.md",
      `docs/releases/${effectiveDate}-v1.1.0正式切版记录.md`,
      "docs/releases/v1.1.0"
    ]);
    git(releaseWorktree, ["commit", "-m", "release: cut v1.1.0 from master"]);

    console.log(
      JSON.stringify(
        {
          status: "RELEASE_CREATED",
          releaseBranch,
          releaseWorktree,
          masterSha,
          releaseSha: git(releaseWorktree, ["rev-parse", "HEAD"]),
          releaseNote: path.relative(rootDir, finalNotePath),
          cutoverRecord: path.relative(rootDir, cutoverRecordPath)
        },
        null,
        2
      )
    );
  } catch (error) {
    fail(error instanceof Error ? error.message : String(error));
  }
}

main();
