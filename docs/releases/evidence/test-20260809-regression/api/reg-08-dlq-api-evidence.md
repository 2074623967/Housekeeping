# REG-08 失败派发重试 / DLQ API 证据

## 证据来源

1. `systems/payment-core/docs/payment-core自测与联调记录.md`
2. `docs/releases/2026-08-08-补偿闭环待执行记录.md`

## 已验证接口

### 2026-08-05 同后缀 DLQ 人工结案与 Replay

- `GET /api/payment-dead-letter-tasks`
- `POST /api/payment-dead-letter-tasks/DLQb08e16e42e524d739cce/resolve-manually`
- `POST /api/payment-dead-letter-tasks/DLQb8efefb346924a67ac61/ready-to-replay`
- `POST /api/payment-dead-letter-tasks/DLQb8efefb346924a67ac61/replay`

### 2026-08-08 清分结果补偿闭环

- `GET /api/payment-dead-letter-tasks?messageId=MQ-CLG-DLQ-20260808-*`

## 2026-09-24 当前 HEAD 运行态证据

本轮使用隔离后缀 `20260924-b`，RabbitMQ、payment-core、accounting、clearing、settlement 均为当前运行实例。以下查询均通过当前 payment-core API 完成：

### 可修复 PAYMENT_SUCCESS

- 发布消息：`DLQ-SMOKE-1790223210646-30716`
- 查询：`GET /api/payment-dead-letter-tasks?messageId=DLQ-SMOKE-1790223210646-30716&pageNo=1&pageSize=20`
- 任务：`DLQf3f3da722cbf43968c4f`
- 状态：`REPLAYED`
- `replayCount=1`
- 死信路由：`payment.success.accounting.dlq.20260924-b.v1`
- 重放路由：`payment.success.accounting.replay.20260924-b.v1`
- 处置说明：当前批次已核对业务字段，允许定向重放

### 非法报文人工结案

- 发布消息：`DLQ-MANUAL-SMOKE-1790223210663-30716`
- 查询：`GET /api/payment-dead-letter-tasks?messageId=DLQ-MANUAL-SMOKE-1790223210663-30716&pageNo=1&pageSize=20`
- 任务：`DLQ0677f160ab774a0198c8`
- 状态：`MANUAL_RESOLVED`
- `replayCount=0`
- 处置说明：当前批次报文非法，保留审计副本后人工结案

### 可修复 CLEARING_GENERATED

- 发布消息：`DLQ-SMOKE-1790223184991-30656`
- 查询：`GET /api/payment-dead-letter-tasks?messageId=DLQ-SMOKE-1790223184991-30656&pageNo=1&pageSize=20`
- 任务：`DLQf8328932093b4732a293`
- 状态：`REPLAYED`
- `replayCount=1`
- 死信路由：`clearing.generated.settlement.dlq.20260924-b.v1`
- 重放路由：`clearing.generated.settlement.replay.20260924-b.v1`
- 处置说明：当前批次已核对业务字段，允许定向重放
