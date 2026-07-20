# settlement-system backend

## 说明

当前目录用于承载 `settlement-system` 的后端实现。

第一版目标：

1. 结算批次查询与创建
2. 结算单查询、创建、审核与驳回
3. 出款批次创建、查询与重试
4. 清分结果事件消费

当前实现阶段：

1. 已补齐 Spring Boot 可运行骨架
2. 已补齐结算系统第一版内存态联调能力
3. 后续会继续替换为 MyBatis + MySQL 正式实现
