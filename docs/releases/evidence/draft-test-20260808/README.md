# draft-test-20260808 冻结证据包

- 基线日期：`2026-08-08`
- 生成时间：`2026-08-08 09:21:32 +0800`
- 生成脚本：`tools/release/init_freeze_evidence_pack.sh`

## 目录说明

- `screenshots/`：页面级截图归档
- `records/final-regression-checklist.md`：页面 + 接口 + 后台 + 下游事实的一体化回归记录
- `screenshot-manifest.csv`：截图台账，记录页面、状态、样例单号、文件名和归档状态

## 使用要求

1. 截图必须来自当前 `test` 基线。
2. 每张截图必须能映射到页面验收矩阵中的页面与关键状态。
3. 截图文件名建议追加业务编号，例如：
   - `payment-detail-success-PAY1785918393972.png`
   - `result-risk-review-PAY1785811244732.png`
4. 完成截图后，务必同步更新 `screenshot-manifest.csv` 与 `final-regression-checklist.md`。

## 建议命令

1. 初始化截图采集计划：

```bash
/Users/abc123/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node \
  /Users/abc123/workspace/home-service-payment-system/tools/release/capture_freeze_screenshots.mjs \
  --init-plan \
  --manifest /Users/abc123/workspace/home-service-payment-system/docs/releases/evidence/draft-test-20260808/screenshot-manifest.csv \
  --evidence-dir /Users/abc123/workspace/home-service-payment-system/docs/releases/evidence/draft-test-20260808
```

2. 先用 dry-run 检查哪些页面还没补 URL：

```bash
/Users/abc123/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node \
  /Users/abc123/workspace/home-service-payment-system/tools/release/capture_freeze_screenshots.mjs \
  --plan /Users/abc123/workspace/home-service-payment-system/docs/releases/evidence/draft-test-20260808/capture-plan.json \
  --manifest /Users/abc123/workspace/home-service-payment-system/docs/releases/evidence/draft-test-20260808/screenshot-manifest.csv \
  --evidence-dir /Users/abc123/workspace/home-service-payment-system/docs/releases/evidence/draft-test-20260808 \
  --dry-run
```
