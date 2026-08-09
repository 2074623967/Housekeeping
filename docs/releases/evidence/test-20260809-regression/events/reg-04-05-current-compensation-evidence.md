# REG-04 / REG-05 当前批次补偿闭环证据

- 日期：`2026-08-09`
- 分支：`test`
- RabbitMQ suffix：`20260803fast`
- 目标：补齐当前代码基线下 `payment.success -> clearing` 与 `clearing.generated -> settlement` 的最新同批次补偿闭环证据

## 1. payment.success -> clearing

### 1.1 新消息与任务

| 项目 | 记录 |
| --- | --- |
| `messageId` | `MQ-DLQ-20260809-CLEARING-REPLAY-001` |
| `routingKey` | `payment.success.clearing.dlq.20260803fast.v1` |
| 任务号 | `DLQ6439d0139c934fbb8f8c` |
| 初始状态 | `PENDING_REVIEW` |

### 1.2 人工审核与 replay

1. `POST /api/payment-dead-letter-tasks/DLQ6439d0139c934fbb8f8c/ready-to-replay`
2. `POST /api/payment-dead-letter-tasks/DLQ6439d0139c934fbb8f8c/replay`

结果：

- 任务状态：`REPLAYED`
- `replayCount = 1`
- `lastReplayAt = 2026-08-09 11:59:42`

### 1.3 下游清分事实

`GET /api/clearing/events?eventType=PAYMENT_SUCCESS&bizNo=PAY-DLQ-CLR-20260809-001&pageNo=1&pageSize=20`

返回：

| 字段 | 值 |
| --- | --- |
| `eventNo` | `EVT60002` |
| `eventType` | `PAYMENT_SUCCESS` |
| `bizNo` | `PAY-DLQ-CLR-20260809-001` |
| `eventStatus` | `已消费` |

## 2. clearing.generated -> settlement

### 2.1 新消息与任务

| 项目 | 记录 |
| --- | --- |
| `messageId` | `MQ-CLG-DLQ-20260809-SETTLEMENT-001` |
| `routingKey` | `clearing.generated.settlement.dlq.20260803fast.v1` |
| 任务号 | `DLQb98ff83d9601499395c7` |
| 初始状态 | `PENDING_REVIEW` |

### 2.2 人工审核与 replay

1. `POST /api/payment-dead-letter-tasks/DLQb98ff83d9601499395c7/ready-to-replay`
2. `POST /api/payment-dead-letter-tasks/DLQb98ff83d9601499395c7/replay`

结果：

- 任务状态：`REPLAYED`
- `replayCount = 1`
- `lastReplayAt = 2026-08-09 11:57:16`

### 2.3 下游结算事实

`GET /api/settlements/events?eventType=CLEARING_GENERATED&bizNo=CLO-DLQ-20260809-001&pageNo=1&pageSize=20`

返回：

| 字段 | 值 |
| --- | --- |
| `eventNo` | `SVE70003` |
| `eventType` | `CLEARING_GENERATED` |
| `bizNo` | `CLO-DLQ-20260809-001` |
| `eventStatus` | `已消费` |

## 3. 当前仍未收口的点

1. `payment.success -> accounting` 当前批次 replay 已执行，但暂未观察到新的账务事件落表。
2. `clearing.generated -> accounting` 当前批次 replay 已执行，但暂未观察到新的账务事件落表。
3. 这意味着当前可以确认 `REG-04`、`REG-05` 的最新同批次补偿闭环证据，但 `REG-06`、`REG-09` 仍不能直接判定为通过。
