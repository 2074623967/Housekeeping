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

3. 自动审计当前冻结候选是否具备放行条件：

```bash
/Users/abc123/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node \
  /Users/abc123/workspace/home-service-payment-system/tools/release/audit_freeze_candidate.mjs \
  --evidence-dir /Users/abc123/workspace/home-service-payment-system/docs/releases/evidence/draft-test-20260808 \
  --release-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/2026-08-05-支付系统发布说明草案.md
```

脚本会输出：

1. `records/freeze-audit-report.json`
2. `records/freeze-audit-report.md`

并自动判断：

1. 当前是否允许推进 `test -> master`
2. 当前是否允许创建 `release/*`

4. 生成冻结候选汇总页，便于版本评审快速查看：

```bash
/Users/abc123/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node \
  /Users/abc123/workspace/home-service-payment-system/tools/release/render_freeze_candidate_summary.mjs \
  --evidence-dir /Users/abc123/workspace/home-service-payment-system/docs/releases/evidence/draft-test-20260808 \
  --release-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/2026-08-05-支付系统发布说明草案.md
```

脚本会输出：

1. `records/freeze-candidate-summary.md`

## 当前已预填样例

当前证据包已预填以下真实样例，后续补截图时可直接复用：

1. `SUCCESS`
   - `PAY1785918393972 / PRE1785918393974`
2. `RISK_REVIEW`
   - `PAY1785811244732 / PRE1785811244734`
3. `WAIT_CALLBACK`
   - `PAY1785915556767 / PRE1785915556768`
   - `PAY1785915556566`
4. `PREPAY_CREATED`
   - `PAY1785915100360 / PRE1785915100361`
5. `CLOSED`
   - `PAY1785729963046`
6. `payment.success` 补偿
   - `DLQa254a62bab904f0d9885`
   - `DLQb151ee3a445c42229730`
7. `clearing.generated` 补偿
   - `DLQ5ef0a4f3bbf24129aef2`
   - `DLQe9f4ca3e5625481eb952`
   - `DLQa55e1d33dfa845aeb296`

仍需现场补的内容：

1. 各端实际访问 `baseUrl`
2. 若需要保留 `READY_TO_REPLAY / PENDING_REVIEW` 页面截图，需要在重放前保留中间态任务
3. 若要获取正式来源应用 H5/PC 的 `WAIT_CALLBACK` 截图，需要在成功回调前先完成采图

## 当前阻断判断

基于 `test` 分支 `d40adca` 基线，当前冻结版仍以以下三项为主阻断：

1. 页面级冻结截图尚未形成同批次归档
2. 页面 + 接口 + 后台 + 下游事实的一体化回归清单尚未全部回填
3. 最终发布候选说明仍需基于本批次证据再做一次收口

因此，当前策略应继续优先补“证据和归档”，而不是继续零散增加单点功能。
