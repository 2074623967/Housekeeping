# 保证金系统数据库

生产数据库：`housekeeping_deposit`。

执行前请确认目标环境、备份策略和数据库账号权限。脚本只负责初始化表结构，不会替代正式迁移工具。

```bash
mysql -uroot -p < schema.sql
```

当前 V1 表：

- `t_deposit_account`：保证金账户余额、冻结额和状态
- `t_deposit_flow`：保证金动作不可变流水

H2 测试使用 `backend/src/main/resources/schema.sql`；生产 MySQL 使用本目录脚本。
