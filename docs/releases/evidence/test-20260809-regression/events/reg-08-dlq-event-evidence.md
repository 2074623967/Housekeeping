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
