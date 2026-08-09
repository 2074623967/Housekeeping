# release 自动切版运行手册

## 1. 目的

本手册用于在 `master` 稳定观察结束后，基于真实 release readiness 审计自动创建 `release/v1.1.0`，并把正式版发布说明和切版记录一起提交。

脚本默认是 `dry-run`，不会创建分支，不会修改工作树。

## 2. 前置条件

1. 当前工作树位于 `master`。
2. `master` 工作树干净，没有未提交变更。
3. 当前日期不早于 `2026-08-10`。
4. `docs/releases/audit/release-readiness-audit.json` 中：
   - `summary.canCreateRelease = true`
5. `release/v1.1.0` 尚不存在。
6. 正式版发布说明预填版存在。

## 3. Dry-run

```bash
node tools/release/promote_master_to_release.mjs \
  --root /private/tmp/home-service-payment-system-master \
  --effective-date 2026-08-10
```

预期结果：

- 所有条件通过时输出 `READY_FOR_RELEASE`
- 任一条件未通过时输出 `BLOCKED`
- 不创建任何分支，不生成 release 工作树

## 4. 正式执行

只有在 `master` 观察结束结论、正式签字审批和现场核验全部通过后，才允许执行：

```bash
node tools/release/promote_master_to_release.mjs \
  --root /private/tmp/home-service-payment-system-master \
  --effective-date 2026-08-10 \
  --execute
```

脚本会：

1. 再次确认当前在干净 `master`
2. 再次确认日期、release readiness 审计和目标分支状态
3. 创建独立 release 工作树和 `release/v1.1.0`
4. 将正式版发布说明预填版升级为正式版说明
5. 生成正式切版记录
6. 提交 `release: cut v1.1.0 from master`

## 5. 安全边界

1. 脚本不会覆盖已经存在的 release 分支或 release 工作树。
2. 脚本不会自动推送远端，不会直接部署生产。
3. 生产发布仍需基于正式版发布说明执行发布后现场核验。
4. 当前 `2026-08-09` 的审计仍未允许创建 release，因此只能运行 dry-run。
