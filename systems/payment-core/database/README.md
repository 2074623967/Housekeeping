# payment-core database

当前已补齐：

- `schema.sql`：支付交易、配置治理、异常治理、任务中心、退款、账单与运营台账建表脚本
- `data.sql`：支付渠道、路由规则、协议、返回码、网关、控制策略、异常值班、支付单与退款演示数据
- `migrations/2026-07-31-add-payment-task-lease.sql`：给原始 `housekeeping_payment_core` 运行库补齐 `t_payment_task_lease` 的最小增量 DDL，用于让最新任务中心租约锁能力安全落库

初始化方式：

1. 先执行 `schema.sql`
2. 再执行 `data.sql`
3. 默认数据库名为 `housekeeping_payment_core`

增量迁移方式：

1. 若原始运行库早于 `2026-07-29` 任务中心租约锁版本，先执行 `migrations/2026-07-31-add-payment-task-lease.sql`
2. 该脚本是幂等 `CREATE TABLE IF NOT EXISTS`，用于补单表，不替代全量 `schema.sql`
3. 执行完成后，应至少验证：
   - `SHOW TABLES LIKE 't_payment_task_lease'`
   - 最新 backend 连接原始运行库后，不再因为 `t_payment_task_lease` 缺失或 `PAYMENT_ISSUE_ESCALATE` 告警内容越界导致调度任务报错

后续继续补强：

- 增量 DDL 版本脚本
- 大表归档与分区策略
- 发布期数据回滚脚本
