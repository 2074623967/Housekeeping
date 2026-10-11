# 退款中心数据库

生产数据库：`housekeeping_refund`。

- `schema.sql`：建表、索引和字段注释。
- `data.sql`：本地演示数据，不用于生产。
- `migrations/`：既有生产表的显式增量迁移，按文件名顺序执行并记录执行结果。
- 退款中心不得直接修改 `payment-core`、账务、清分或结算数据库。
- 支付成功事实通过内部投影接口或消息进入 `t_refund_payment_source`。
- 既有 MySQL 库需在启用大小写敏感的退款幂等键前，审核并执行 `migrations/2026-10-12-add-exact-refund-idempotency.sql`：先增加字节精确唯一索引，再删除旧排序规则唯一索引；运行前备份并核对实际索引和历史数据。本仓库的 H2 自测不执行生产迁移。
