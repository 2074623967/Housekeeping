# test-20260809-regression 回归证据包

- 基线日期：`2026-08-09`
- 生成时间：`2026-08-09 10:46:08 +0800`
- 生成脚本：`tools/release/init_regression_evidence_pack.sh`

## 目录说明

- `regression-scenarios.csv`：统一场景台账，串联 API、数据库、事件、页面和补偿/回滚证据
- `records/regression-summary.md`：本轮回归结论、阻断项和是否允许推进下一阶段
- `api/`：接口请求/响应样例或 curl 记录
- `database/`：数据库事实截图、SQL 输出或导出文件
- `events/`：事件台账、补偿任务、重试结果
- `screenshots/`：页面截图，按系统归档

## 使用要求

1. 所有证据必须来自当前 `test` 基线或明确记录的隔离环境。
2. 每个场景至少要回填 API、数据库事实、事件台账三类证据；涉及页面的场景还要补截图。
3. 所有场景统一使用 `REG-01 ~ REG-10` 编号，不允许新增临时口径编号。
4. 如果场景属于补偿、回放、回滚演练，必须在 `events/` 或 `database/` 中保留执行前后对比证据。
5. 完成回归后，先运行 `tools/release/audit_regression_pack.mjs`，再决定是否更新发布结论。

## 当前 HEAD 补偿复核

REG-08 已使用隔离后缀 `20260924-b` 完成当前批次运行态复核。RabbitMQ 与 payment-core、clearing、settlement、accounting 消费者均保持运行，证据见 `api/`、`database/`、`events/` 和 `screenshots/payment-core/admin-web/dead-letter-replayed.png`。

```bash
HSP_DLQ_SUFFIX=20260923-a \
HSP_DLQ_EVENT_KIND=payment-success \
HSP_DLQ_TARGET_SYSTEM=accounting \
node tools/dev/dlq-compensation-smoke.mjs

HSP_DLQ_SUFFIX=20260923-a \
HSP_DLQ_EVENT_KIND=clearing-generated \
HSP_DLQ_TARGET_SYSTEM=settlement \
node tools/dev/dlq-compensation-smoke.mjs
```

本轮已把新 `messageId`、`taskNo`、最终任务状态、`replayCount`、路由键和下游事实写入 `api/reg-08-dlq-api-evidence.md`、`database/reg-08-dlq-db-evidence.md`、`events/reg-08-dlq-event-evidence.md`，并采集当前死信任务页的 `MANUAL_RESOLVED` 与 `REPLAYED` 截图。`PENDING_REVIEW` / `READY_TO_REPLAY` 保留为历史页面样例，不在当前批次中虚构。

可选参数 `HSP_DLQ_TIMEOUT_MS`、`HSP_DLQ_POLL_MS` 必须为正数；`HSP_DLQ_PAYLOAD_JSON` 必须是与事件模式匹配的 JSON 对象并包含业务单号。非法参数会在消息发布前失败，不能写入当前批次证据。
