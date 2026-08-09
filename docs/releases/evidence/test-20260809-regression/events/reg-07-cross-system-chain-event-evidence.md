# REG-07 跨系统链路诊断事件证据

## 当前能力

`payment-core`、`clearing-system`、`settlement-system`、`accounting-system` 已统一补齐 `businessChainId`，并由对账系统按统一链路维度归口事实快照。

## 当前诊断规则

1. 无事实：`未接收到任何跨系统事实`
2. 有失败或异常事实：`链路存在失败或异常事实，禁止直接进入财务收口`
3. 无失败事实：`已接收跨系统事实，当前未发现失败状态`

## 当前说明

这部分已具备代码、DDL、运行时 schema、MyBatis 和自动化测试证据，是当前回归包中可完整闭环的场景之一。
