# payment-core database

当前已补齐：

- `schema.sql`：支付交易、配置治理、异常治理、任务中心、退款、账单与运营台账建表脚本
- `data.sql`：支付渠道、路由规则、协议、返回码、网关、控制策略、异常值班、支付单与退款演示数据

初始化方式：

1. 先执行 `schema.sql`
2. 再执行 `data.sql`
3. 默认数据库名为 `housekeeping_payment_core`

后续继续补强：

- 增量 DDL 版本脚本
- 大表归档与分区策略
- 发布期数据回滚脚本
