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

## 8. 2026-08-03 隔离 RabbitMQ + 隔离 MySQL 真实主链路复核

### 8.1 验证范围

本轮不再沿用历史 HTTP 直推或共享队列口径，而是在隔离 RabbitMQ 拓扑 `20260803a` 和隔离 MySQL 库上，重新验证：

`payment-core 预付单 -> 提交 -> 渠道回调成功 -> PAYMENT_SUCCESS 出站 -> clearing 消费 -> CLEARING_GENERATED 出站 -> settlement/accounting 消费`

### 8.2 隔离环境

| 项目 | 值 |
| --- | --- |
| RabbitMQ 容器 | `hsp-rabbitmq` |
| RabbitMQ 用户 | `hsp` |
| RabbitMQ 隔离后缀 | `20260803a` |
| MySQL 容器 | `hsp-payment-mysql-drill` |
| 支付核心库 | `housekeeping_payment_core` |
| `payment-core` 端口 | `18081` |
| `clearing-system` 端口 | `18122` |
| `settlement-system` 端口 | `18132` |
| `accounting-system` 端口 | `18112` |

### 8.3 验证步骤与结果

| 步骤 | 接口/动作 | 业务编号 | 结果 |
| --- | --- | --- | --- |
| 历史单复核 | `GET /api/payments/PAY1785729963046` | `DRILL-AMQP-20260803A-001` | 发现该笔在 `2026-08-03 05:06:36` 已被超时任务自动关单，状态为 `CLOSED`，不再继续复用 |
| 新建预付单 | `POST /api/payments/prepay` | `DRILL-AMQP-20260803A-002` | 成功生成 `PRE1785733720393` / `PAY1785733720390` / `BILL1785733720388` |
| 支付提交 | `POST /api/payments/submit` | `PAY1785733720390` | 按 `housekeeping-h5-web + H5 + 微信支付 + wx_h5` 成功命中 `RULE_HOME_WX`，支付单进入 `WAIT_CALLBACK` |
| 支付回调成功 | `POST /api/payments/callback/wx_h5` | `PAY1785733720390` | 支付单收口为 `SUCCESS`，`PAYMENT_SUCCESS` 事件出站状态为 `SUCCESS` |
| 清分单生成 | `GET /api/clearing/orders` | `CLO20002` | 生成清分单 `CLO20002`，关联支付单 `PAY1785733720390` |
| 清分事件复核 | `GET /api/clearing/events` | `EVT60002` / `EVT60003` | `PAYMENT_SUCCESS` 已消费，`CLEARING_GENERATED` 已生成 |
| 结算单生成 | `GET /api/settlements/orders` | `SLT20003` | 生成结算单 `SLT20003`，状态 `待审核/待出款` |
| 账务事件复核 | `GET /api/accounting/events` | `EVT50002` / `EVT50003` | 支付成功与清分生成两类账务事件均已消费 |
| 账务余额复核 | `GET /api/accounting/accounts` + `GET /api/accounting/balances/ACT10003` | `ACT10002` / `ACT10003` | 服务者应收账户增至 `¥273.16`，平台手续费账户余额为 `¥200.00` |
| 队列快照复核 | `infra/rabbitmq/queue_snapshot.sh 20260803a` | `2026-08-03 13:09:42 +0800` | 主队列、retry、DLQ 均为 `0` 消息，4 个隔离主队列消费者在线 |

### 8.4 本轮关键结论

1. `payment-core -> clearing-system -> settlement-system / accounting-system` 的真实异步链路已在隔离 RabbitMQ 和隔离 MySQL 条件下再次验证通过，不再只是旧的 HTTP 直推口径。
2. 本轮也验证了一个真实业务细节：错误的提交参数组合会被路由规则阻断；必须使用与控制策略和场景规则一致的 `sourceAppId / terminal / paymentMethod / channelCode` 才能通过。
3. 历史隔离单 `DRILL-AMQP-20260803A-001` 被超时任务自动关单，说明支付超时关单任务正在真实运行；后续联调必须始终使用新鲜业务单，避免把过期单误判为主链路失败。

### 8.5 当前仍未关闭的门禁

1. 本轮证明了“隔离真实 MQ 主链路可跑通”，但还没有形成完整的失败重试、DLQ 入池、人工结案、定向 replay 一套最新同批次证据。
2. `settlement-system` 当前只验证到“结算单生成”，尚未在同一批次里继续验证“审核通过 -> 生成待出款草稿 -> 执行出款 -> 失败重试”。
3. 因此当前仍不足以单独触发 `test -> master` 或 `release/*`。

### 8.6 同批次结算出款闭环补证

在 `SLT20003` 生成后，继续按结算产品流程补做同批次出款闭环验证，结果如下：

| 步骤 | 接口/动作 | 业务编号 | 结果 |
| --- | --- | --- | --- |
| 结算审核通过 | `POST /api/settlements/orders/SLT20003/audit` | `SLT20003` | 审核状态更新为 `已通过`，结算状态维持为 `待出款` |
| 生成待出款草稿 | `POST /api/settlements/payouts` | `SET10002` | 生成出款批次 `PBT50001`，批次状态 `待出款`，金额 `¥153.16` |
| 执行出款 | `POST /api/settlements/payouts/PBT50001/execute` | `PBT50001` | 批次状态收口为 `已完成` |
| 出款记录核对 | `GET /api/settlements/payouts/PBT50001/records` | `POU60001` | 出款记录状态为 `已发放`，重试次数 `0` |
| 结算单核对 | `GET /api/settlements/orders/SLT20003/full` | `SLT20003` | 结算状态 `已出款`，出款状态 `已发放`，审计日志含“审核通过”“执行出款”两条记录 |

补充结论：

1. `payment-core -> clearing -> settlement` 这一批次已经不只停留在“结算单生成”，而是继续验证到了真实的出款草稿创建和执行出款收口。
2. 当前剩余门禁已进一步缩小到 retry / DLQ / replay / 人工结案等补偿链路，而不是正向出款主链路缺失。

## 9. 2026-08-05 正式来源应用最新跨系统联调复核

### 9.1 验证范围

本轮不再使用历史默认来源应用或旧隔离单，而是直接使用正式来源应用：

1. `housekeeping-app-web`
2. `housekeeping-h5-web`
3. `housekeeping-pc-web`

验证目标是确认在正式来源应用控制策略恢复为 `PASS`、正式提交流程恢复可用后，最新支付样例是否已经自动联动到：

`payment-core -> clearing-system -> settlement-system -> accounting-system`

### 9.2 正式来源应用支付样例

| 终端 | 订单号 | 预付单 | 支付单 | 支付状态 |
| --- | --- | --- | --- | --- |
| App | `REG-AUTO-APP-20260805-001` | `PRE1785918393974` | `PAY1785918393972` | `SUCCESS` |
| H5 | `REG-AUTO-H5-20260805-001` | `PRE1785918388806` | `PAY1785918388803` | `SUCCESS` |
| PC | `REG-AUTO-PC-20260805-001` | `PRE1785918388717` | `PAY1785918388713` | `SUCCESS` |

### 9.3 下游联动结果

| 系统 | 业务编号 | 结果 |
| --- | --- | --- |
| `clearing-system` | `CLO20003 / CLO20004 / CLO20005` | 三笔正式来源应用支付样例全部生成清分单，状态均为“清分成功”，生成时间均为 `2026-08-05 17:11:43` |
| `settlement-system` | `SLT20004 / SLT20005 / SLT20006` | 三笔正式来源应用支付样例全部自动生成结算单，状态均为“待审核 / 待出款”，生成时间均为 `2026-08-05 17:11:43` |
| `accounting-system` | `EVT50005 ~ EVT50010` | 三笔正式来源应用支付样例的 `PAYMENT_SUCCESS` 与 `CLEARING_GENERATED` 事件均已自动消费，状态均为“已消费” |

### 9.4 本轮结论

1. 这说明当前最新 `payment-core` 代码不只是“正式来源应用本身可支付”，而是已经带着最新正式来源应用口径重新打通了下游清分、结算、账务联动。
2. 当前跨系统 smoke 的主要缺口已不再是“最新支付核心样例没有下游证据”，而是：
   - 缺少同批次 retry / DLQ / replay / 人工结案补偿链路证据
   - 缺少正式来源应用页面级截图证据
   - 缺少一版可直接用于 `master / release` 判断的冻结版发布文档
