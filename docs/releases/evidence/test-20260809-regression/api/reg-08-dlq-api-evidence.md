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
