# REG-07 跨系统链路诊断数据库证据

## 当前数据模型

对账系统已新增事实归口表：

- `t_cross_system_fact_snapshot`

字段覆盖：

1. `business_chain_id`
2. `system_code`
3. `fact_type`
4. `fact_no`
5. `business_no`
6. `fact_status`
7. `amount`
8. `snapshot_version`
9. `occurred_at`

## 当前唯一键

```text
business_chain_id + system_code + fact_type + fact_no
```

该约束用于支撑重复上报幂等更新。
