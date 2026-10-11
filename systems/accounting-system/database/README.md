# accounting-system database

当前已补齐：

- `schema.sql`：账户主体、账户、余额、流水、冻结单、调账单、事件表建表脚本
- `migrations/2026-10-02-add-ledger-payment-scene.sql`：为既有账务流水表增量补充可空支付业务场景字段，历史流水保持为空
- `migrations/2026-10-03-add-event-business-uniqueness.sql`：为事件类型与业务单号增加唯一约束；升级前先检查 `SELECT event_type, biz_no, COUNT(*) FROM t_account_event GROUP BY event_type, biz_no HAVING COUNT(*) > 1`，历史重复需先按事件、流水和资金事实人工核实，不能自动合并或删除

后续继续补强：

- 初始化演示数据
- 索引优化脚本
- 归档与分区策略脚本
