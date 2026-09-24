# REG-08 失败派发重试 / DLQ 数据库事实证据

## 已验证任务号

### 2026-08-05 fast suffix 自动 retry -> DLQ

- `DLQaba240b93ae346b7b7b9`
- `DLQ338493c729064115a467`

### 2026-08-08 补偿闭环

- `DLQ5ef0a4f3bbf24129aef2`
- `DLQe9f4ca3e5625481eb952`
- `DLQa55e1d33dfa845aeb296`

## 已验证状态

- `MANUAL_RESOLVED`
- `REPLAYED`

## 2026-09-24 当前 HEAD 运行态事实

payment-core `GET /api/payment-dead-letter-tasks?pageNo=1&pageSize=20` 返回本批次三条任务，均已闭环：

| taskNo | messageId | targetSystem | taskStatus | replayCount | 关键路由 |
| --- | --- | --- | --- | ---: | --- |
| `DLQ0677f160ab774a0198c8` | `DLQ-MANUAL-SMOKE-1790223210663-30716` | `accounting-system` | `MANUAL_RESOLVED` | 0 | `payment.success.accounting.dlq.20260924-b.v1` |
| `DLQf3f3da722cbf43968c4f` | `DLQ-SMOKE-1790223210646-30716` | `accounting-system` | `REPLAYED` | 1 | `payment.success.accounting.replay.20260924-b.v1` |
| `DLQf8328932093b4732a293` | `DLQ-SMOKE-1790223184991-30656` | `settlement-system` | `REPLAYED` | 1 | `clearing.generated.settlement.replay.20260924-b.v1` |

下游事实查询结果：

- accounting `GET /api/accounting/events?eventType=PAYMENT_SUCCESS&bizNo=PAY-DLQ-SMOKE-1790223210646-30716`：事件 `EVT50004`，状态“已消费”。
- accounting `GET /api/accounting/ledgers?accountNo=ACT10003&bizNo=PAY-DLQ-SMOKE-1790223210646-30716`：流水 `LDG20007`，金额 `¥9.99`，状态“已记账”。
- settlement `GET /api/settlements/events?eventType=CLEARING_GENERATED&bizNo=CLO-DLQ-1790223184991-30656`：事件 `SVE70003`，状态“已消费”。
- settlement `GET /api/settlements/orders?clearingNo=CLO-DLQ-1790223184991-30656`：结算单 `SLT20004`，批次 `SET10003`，净结算金额 `¥112.00`，状态“待审核”。
- settlement `GET /api/settlements/batches/SET10003`：批次状态“已完成”，总金额 `¥112.00`。
