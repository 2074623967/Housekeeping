# 退款中心数据库

生产数据库：`housekeeping_refund`。

- `schema.sql`：建表、索引和字段注释。
- `data.sql`：本地演示数据，不用于生产。
- 退款中心不得直接修改 `payment-core`、账务、清分或结算数据库。
- 支付成功事实通过内部投影接口或消息进入 `t_refund_payment_source`。

