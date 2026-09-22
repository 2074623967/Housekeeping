# REG-08 失败派发重试 / DLQ 事件与下游事实证据

## 已验证下游事实

### payment.success.* replay

- 账务事件：`EVT50004`
- 账务流水：`LDG20007`

### clearing.generated.* replay

- 结算事件：`SVE70002`
- 账务事件：`EVT50003`

## 当前说明

历史证据已证明：

1. 可修复消息可经过 `READY_TO_REPLAY -> REPLAYED`
2. 非法消息可经过 `MANUAL_RESOLVED`
3. replay 后下游事实能够恢复

但当前回归包仍缺“与本轮代码基线同批次”的 replay / downstream fact 新证据。

## 2026-09-23 当前 HEAD 复核

当前 payment-core 后端全量测试已覆盖死信 intake、人工复核、定向 replay、publisher confirm 失败和 ACK/NACK 分支；这些是本地自动化证据，不是 broker 运行态证据。

本轮未能取得新的事件运行态样本：本机 Docker daemon 未运行，RabbitMQ broker 不可用，因此没有向 DLQ 发布新消息，也没有把旧 `EVT50004 / SVE70002 / EVT50003` 重新登记为当前事实。REG-08 在当前门禁中继续保持 `HISTORICAL`，直到恢复 broker 后完成新批次 replay 和下游事实查询。
