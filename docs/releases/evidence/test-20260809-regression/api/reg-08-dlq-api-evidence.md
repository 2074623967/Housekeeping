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

## 当前说明

历史运行证据已经证明 `DLQ -> task -> manual resolve / replay` 能跑通，但当前回归包仍缺“2026-08-09 同批次”的最新实操证据。

## 2026-09-23 当前 HEAD 复核

本轮检查了当前工作树和补偿接口代码，确认 API 端点与服务端人工处置状态机仍存在；但未执行新的 DLQ 运行态证据。原因是本机 Docker daemon 未运行，无法启动 `infra/rabbitmq/docker-compose.yml` 提供的 RabbitMQ 管理 API 和 broker。

本轮明确未声明：

1. 新消息已发布到 `payment.trade.dlq` 或 `clearing.trade.dlq`；
2. 新任务已进入 `PENDING_REVIEW`；
3. 当前 HEAD 的 `READY_TO_REPLAY -> REPLAYED` 已完成；
4. replay 后下游事实已恢复。

恢复 RabbitMQ 后，必须使用新 `messageId`、新任务号和当前运行实例重复记录上述四项，不能复用本文件的历史编号。
