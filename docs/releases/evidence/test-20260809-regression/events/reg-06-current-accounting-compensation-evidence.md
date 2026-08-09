# REG-06 当前批次账务补偿证据

- 日期：`2026-08-09`
- 分支：`test`
- RabbitMQ suffix：`20260803fast`
- 目标：补齐 `clearing.generated -> accounting` 当前代码基线的最新同批次补偿闭环证据

## 1. 当前批次死信任务

| 项目 | 记录 |
| --- | --- |
| `messageId` | `MQ-CLG-DLQ-20260809-ACCOUNTING-001` |
| `routingKey` | `clearing.generated.accounting.dlq.20260803fast.v1` |
| 任务号 | `DLQ7a0a8ed4e376435e914a` |
| 初始状态 | `PENDING_REVIEW` |

## 2. replay 处置

1. `POST /api/payment-dead-letter-tasks/DLQ7a0a8ed4e376435e914a/ready-to-replay`
2. `POST /api/payment-dead-letter-tasks/DLQ7a0a8ed4e376435e914a/replay`

结果：

- 任务状态：`REPLAYED`
- `replayCount = 1`
- `lastReplayAt = 2026-08-09 11:57:16`

## 3. replay 后现象

`GET /api/accounting/events?eventType=CLEARING_GENERATED&bizNo=CLO-DLQ-20260809-002&pageNo=1&pageSize=20`

在 replay 后未立即查到新账务事件，说明当前基线的 `MQ replay -> accounting` 自动收口仍存在差异。

## 4. 下游补事实收口

根据补偿手册对 `clearing.generated.*` 的要求，当前链路允许“必要时 replay 或下游补事实”。

因此对同一条 current batch payload 执行账务补事实：

`POST /api/accounting/events/clearing/generated`

请求体：

```json
{
  "accountNo": "ACT10003",
  "clearingOrderNo": "CLO-DLQ-20260809-002",
  "bizNo": "PAY-DLQ-CLG-20260809-002",
  "amount": 66.60,
  "summary": "2026-08-09 当前test基线 replay 后人工补事实收口"
}
```

## 5. 账务事实

`GET /api/accounting/events?eventType=CLEARING_GENERATED&bizNo=CLO-DLQ-20260809-002&pageNo=1&pageSize=20`

返回：

| 字段 | 值 |
| --- | --- |
| `eventNo` | `EVT50012` |
| `eventType` | `CLEARING_GENERATED` |
| `bizNo` | `CLO-DLQ-20260809-002` |
| `eventStatus` | `已消费` |
| `summary` | `2026-08-09 当前test基线 replay 后人工补事实收口` |

## 6. 结论

当前 `test` 基线下，`clearing.generated -> accounting` 的最新同批次补偿闭环已经达到：

`DLQ 入账 -> 任务审核 -> replay -> 发现下游未自动收口 -> 使用标准账务事件接口补事实 -> 账务事件落表`

因此 `REG-06` 的当前批次补偿证据已补齐，同时也保留了一个真实迭代问题：`accounting` 方向的 MQ replay 仍需继续排查自动收口差异。
