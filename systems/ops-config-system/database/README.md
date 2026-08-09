# 运营配置系统数据库

生产数据库：`housekeeping_ops_config`。

```bash
mysql -uroot -p < schema.sql
```

当前 V1 表：

- `t_ops_agreement_template`
- `t_ops_business_line`
- `t_ops_payment_type`
- `t_ops_cashier_template`
- `t_ops_channel_profile`
- `t_ops_routing_rule`
- `t_ops_system_control`

测试环境使用 `backend/src/main/resources/schema.sql`，生产初始化使用本目录脚本。
