# 跨系统联调与 Smoke 测试记录

## 1. 验证范围

本轮在本机独立 H2 环境启动以下服务：

1. `clearing-system`：`18120`
2. `settlement-system`：`18130`
3. `accounting-system`：`18110`

验证目标是确认支付成功后的跨系统事件契约可被清分、结算、账务三个领域正确消费。

## 2. 验证链路

```text
支付成功事件
  -> 清分系统消费 PAYMENT_SUCCESS
  -> 清分结果事件
  -> 结算系统消费 CLEARING_GENERATED
  -> 账务系统消费 CLEARING_GENERATED
  -> 服务者应收账户入账
```

## 3. 实际结果

| 步骤 | 业务编号 | 结果 |
| --- | --- | --- |
| 清分消费支付成功事件 | `PAY-SMOKE-20260729-001` | 返回 `code=0`，生成 `EVT60002`，状态为“已消费” |
| 结算消费清分结果事件 | `CLR-SMOKE-20260729-001` | 返回 `code=0`，生成 `SVE70002`，状态为“已消费” |
| 账务消费清分结果事件 | `CLR-SMOKE-20260729-001` | 返回 `code=0`，生成 `EVT50002`，状态为“已消费” |
| 账务余额验证 | `ACT10002` | 服务者应收账户可用余额由 `¥120.00` 变为 `¥210.00` |

## 4. 验证结论

1. 当前清分、结算、账务的事件接收接口、DTO 字段、事件落库和账务入账行为在本地环境可连通。
2. 验证使用隔离 smoke 业务编号和内存 H2 数据源，不影响业务数据库。
3. 该验证是“事件契约 + 消费结果” smoke test；当前由测试步骤显式投递下游事件，不等同于生产级 MQ 自动编排。

## 5. 后续门禁

1. 接入真实 MQ 或可靠事件总线后，需补事件发布、幂等、重试、死信和补偿场景的端到端验证。
2. 需补 `payment-core` 支付成功事件到清分系统的自动投递适配，避免依赖人工/脚本转发。
3. 在 `test` 分支需要重新执行本 smoke 流程，并记录环境、版本号和结果。

## 6. 2026-07-29 payment-core 自动下游联调验证

### 6.1 验证范围

本轮直接从 `payment-core` 发起真实支付主链路，重点确认“虚拟订单预付单 -> 支付成功回调 -> 支付成功事件自动下发 -> 清分消费 -> 账务消费”是否可真实跑通。

### 6.2 验证步骤与结果

| 步骤 | 接口/动作 | 业务编号 | 结果 |
| --- | --- | --- | --- |
| 预付单创建 | `POST /api/payments/prepay` | `SMOKE-ORDER-20260729-003` | 成功生成 `PRE1785287995357` / `PAY1785287995355` / `BILL1785287995352` |
| 支付成功回调 | `POST /api/payments/callback/alipay_h5` | `PAY1785287995355` | 回调收口成功，支付单状态变为 `SUCCESS` |
| 清分事件消费 | `GET /api/clearing/events?bizNo=PAY1785287995355` | `PAY1785287995355` | 生成 `EVT60003`，事件类型 `PAYMENT_SUCCESS`，状态“已消费” |
| 账务事件消费 | `GET /api/accounting/events?bizNo=PAY1785287995355` | `PAY1785287995355` | 生成 `EVT50003`，事件类型 `PAYMENT_SUCCESS`，状态“已消费” |

### 6.3 本轮缺陷与修复

1. 真实回调时发现虚拟订单场景没有 `t_order` 数据，`findOrderAmount(orderNo)` 返回空值，导致 `t_bill.paid_amount` 更新为 `null` 并触发数据库约束异常。
2. 已在 `payment-core` 回调成功分支补齐兜底逻辑：当订单金额缺失时，改用支付单金额解析结果作为账单实付金额，同时仅在真实订单存在时更新订单表。
3. 已补充对应单元测试，避免后续回归再次把虚拟业务单场景打坏。

### 6.4 验证结论

1. `payment-core` 到 `clearing-system`、`accounting-system` 的支付成功自动联动已经在本机环境验证通过。
2. 当前仍未覆盖 `settlement-system` 自动串联、MQ 真正异步投递、死信补偿和失败重试闭环，这些仍是进入 `master` / `release` 前的后续门禁。

## 7. 2026-07-29 clearing -> settlement/accounting 自动联调验证

### 7.1 验证范围

本轮在 `payment-core -> clearing-system` 已自动联动的基础上，继续验证 `clearing-system` 是否会把 `CLEARING_GENERATED` 结果自动派发到 `settlement-system` 与 `accounting-system`。

### 7.2 本轮修复

1. 为 `clearing-system` 新增 `ClearingEventDispatchService`，在消费 `PAYMENT_SUCCESS` 后自动向结算、账务继续投递清分结果。
2. 新增 `ClearingEventDispatchServiceImplTest`，覆盖“派发成功”和“清分单不存在”两类场景。
3. 修复两处真实联调缺陷：
   `settlement-system` 默认派发地址应为 `/api/settlements/events/clearing/generated`
   `accounting-system` 默认派发地址应为 `/api/accounting/events/clearing/generated`

### 7.3 验证步骤与结果

| 步骤 | 接口/动作 | 业务编号 | 结果 |
| --- | --- | --- | --- |
| 预付单创建 | `POST /api/payments/prepay` | `SMOKE-ORDER-20260729-006` | 成功生成 `PRE1785289710000` / `PAY1785289709999` |
| 支付成功回调 | `POST /api/payments/callback/alipay_h5` | `PAY1785289709999` | 支付单状态收口为 `SUCCESS` |
| 清分事件消费 | `GET /api/clearing/events` | `PAY1785289709999` | 生成 `EVT60002`，状态“已消费” |
| 清分结果生成 | `GET /api/clearing/orders` | `CLO20002` | 生成清分单 `CLO20002` |
| 结算事件消费 | `GET /api/settlements/events?bizNo=CLO20002` | `CLO20002` | 生成 `SVE70003`，状态“已消费” |
| 账务事件消费 | `GET /api/accounting/events?bizNo=CLO20002` | `CLO20002` | 生成 `EVT50008`，状态“已消费” |

### 7.4 验证结论

1. 当前本机环境已经真实验证 `payment-core -> clearing-system -> settlement-system / accounting-system` 的自动链路。
2. 这意味着跨系统 smoke 不再依赖人工补投 `CLEARING_GENERATED` 事件。
3. 当前剩余门禁主要收敛到 MQ 级可靠投递、失败重试、死信补偿和 `test -> master -> release` 稳定性观察。
