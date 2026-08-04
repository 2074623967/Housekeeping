# 风控系统数据库

生产数据库：`housekeeping_risk_control`。

```bash
mysql -uroot -p < schema.sql
```

当前 V1 表：

- `t_risk_policy`
- `t_risk_limit_rule`
- `t_risk_blocklist`
- `t_risk_intercept_event`
- `t_risk_review_order`
- `t_risk_monitor_rule`

测试环境使用 `backend/src/main/resources/schema.sql`，生产初始化使用本目录脚本。

