# settlement-system backend

## 说明

当前目录用于承载 `settlement-system` 的后端实现。

第一版目标：

1. 结算批次查询与创建
2. 结算单查询、创建、审核与驳回
3. 出款批次创建、查询与重试
4. 清分结果事件消费

当前实现阶段：

1. 已补齐 Spring Boot + MyBatis + H2 可运行基线
2. 已补齐结算批次、结算单、审核日志、出款批次、出款记录、清分事件的持久化实现
3. 已补齐 `schema.sql / data.sql / mapper xml / 集成测试`
4. 后续会继续切换到 MySQL，并补齐出款失败补偿、回单回写、审计追踪和跨系统联动
