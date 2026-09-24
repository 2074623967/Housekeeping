# REG-08 失败派发重试 / DLQ 事件与下游事实证据

## 2026-09-24 当前 HEAD 事件运行态证据

### PAYMENT_SUCCESS -> accounting

- 入站消息：`DLQ-SMOKE-1790223210646-30716`
- 任务：`DLQf3f3da722cbf43968c4f`
- 下游事件：`EVT50004`
- `eventType=PAYMENT_SUCCESS`
- `bizNo=PAY-DLQ-SMOKE-1790223210646-30716`
- 状态：`已消费`
- 账务流水：`LDG20007`，`accountNo=ACT10003`，金额 `¥9.99`，状态“已记账”

### CLEARING_GENERATED -> settlement

- 入站消息：`DLQ-SMOKE-1790223184991-30656`
- 任务：`DLQf8328932093b4732a293`
- 清分业务号：`CLO-DLQ-1790223184991-30656`
- 下游事件：`SVE70003`
- `eventType=CLEARING_GENERATED`
- 状态：`已消费`
- 结算单：`SLT20004`
- 结算批次：`SET10003`
- 净结算金额：`¥112.00`

### 非法报文

- 入站消息：`DLQ-MANUAL-SMOKE-1790223210663-30716`
- 任务：`DLQ0677f160ab774a0198c8`
- 状态：`MANUAL_RESOLVED`
- 原始 DLQ 审计副本保留，未向下游重放
