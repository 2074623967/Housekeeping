# 对账系统数据库

生产数据库：`housekeeping_reconciliation`。

核心表：

- `t_reconciliation_batch`：对账批次、匹配汇总和正式对账准入快照。
- `t_reconciliation_channel_record`：渠道账单明细。
- `t_reconciliation_internal_record`：平台内部支付事实。
- `t_reconciliation_difference`：差异和人工处置结果。

对账系统不直接写支付、账务、清分和结算系统的数据库。

生产数据库升级顺序：

1. 新建环境执行 `schema.sql`。
2. 已有环境执行 `migrations/2026-08-09-add-batch-readiness-gate.sql`。
3. 发布后调用批次准入检查接口，确认 `readiness_status` 已按现有跨系统事实回填。
