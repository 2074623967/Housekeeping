# 对账系统数据库

生产数据库：`housekeeping_reconciliation`。

核心表：

- `t_reconciliation_batch`：对账批次和匹配汇总。
- `t_reconciliation_channel_record`：渠道账单明细。
- `t_reconciliation_internal_record`：平台内部支付事实。
- `t_reconciliation_difference`：差异和人工处置结果。

对账系统不直接写支付、账务、清分和结算系统的数据库。

