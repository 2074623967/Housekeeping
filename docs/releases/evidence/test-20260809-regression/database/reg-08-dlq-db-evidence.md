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

## 当前说明

历史证据已证明补偿任务账本、人工结案和重放状态流转存在真实记录，但当前回归包仍需补“最新同批次任务号 + 数据库取证”。
