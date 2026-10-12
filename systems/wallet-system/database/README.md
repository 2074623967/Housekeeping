# wallet-system database

当前已补齐：

- `schema.sql`：钱包账户、流水、充值、提现、转账、余额支付、红包、风控事件建表脚本
- `data.sql`：个人/企业钱包、充值、提现、转账、红包与风控演示数据

初始化方式：

1. 先执行 `schema.sql`
2. 再执行 `data.sql`
3. 默认数据库名为 `housekeeping_wallet`

说明：

- 所有表字段必须带备注
- 钱包资金域和支付核心域分库
- 后续继续补余额快照、冻结明细、营销活动资金池与对账辅助表
- 充值单 `biz_no` 必须全局唯一；如从旧库升级，先审计、清理重复业务号，再增加 `uk_wallet_recharge_biz_no` 唯一索引，不要重跑会删表的 `schema.sql`
- 充值接口只创建 `PENDING` 单，不代表支付成功或钱包入账；`data.sql` 的 `SUCCESS` 是演示历史数据
