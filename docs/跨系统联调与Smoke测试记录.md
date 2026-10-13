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

## 10. 2026-09-22 本地 happy-path 财务收口断言补强

`tools/dev/cross-system-happy-path.mjs` 现在除逐条核验 `PAYMENT_SUCCESS`、`CLEARING_GENERATED`、`SETTLEMENT_CREATED`、`ACCOUNTING_POSTED` 和 `REFUND_SUCCESS` 事实外，还必须满足：

1. `expectedStageCount = 4` 且 `completedStageCount = expectedStageCount`；
2. 链路状态为 `COMPLETED`，责任状态为 `CLOSED`；
3. `financialCloseable = true`。

这组断言只在本地联调服务真实返回完整四阶段事实时通过，不把“事实行存在”直接解释为财务已收口；仍不替代渠道账单、生产账务、资金到账或正式财务审批证据。

## 12. 2026-09-27 当前工作树完整本地栈复核

### 12.1 执行命令

```sh
HSP_LOCAL_START_TIMEOUT_SECONDS=120 \
HSP_MAVEN_REPO=/Users/abc123/apache-maven-3.9.16/repository \
bash tools/dev/local-stack.sh verify all
```

### 12.2 结果

| 范围 | 结果 |
| --- | --- |
| 全部选定后端 | 12 个后端全部 READY，HTTP 冒烟通过 |
| 全部选定前端 | 15 个前端入口及代理全部 READY |
| 支付、清分、结算、账务 | happy-path 通过，生成支付成功、清分、结算和账务事实 |
| 退款链路 | `REFUND_SUCCESS` 事实生成 |
| 对账链路 | `COMPLETED`，完成阶段 `4/4`，`financialCloseable=true` |
| 运行清理 | 验证完成后本轮启动的前端进程由脚本停止 |

本轮使用脚本动态生成隔离业务编号，证明当前工作树在受控本地 H2/demo/HTTP 栈上的正向跨系统闭环可运行；不替代真实渠道、生产 RabbitMQ/MySQL、失败重试/DLQ/replay、资金到账、正式鉴权或发布治理证据。

## 11. 2026-09-23 当前 HEAD 全栈复核

### 11.1 执行命令

```sh
HSP_MAVEN_BIN=/Users/abc123/apache-maven-3.9.16/bin/mvn \
HSP_MAVEN_REPO=/Users/abc123/apache-maven-3.9.16/repository \
tools/dev/local-stack.sh verify all
```

### 11.2 结果

| 范围 | 结果 |
| --- | --- |
| 全部选定后端 | 全部 READY，HTTP 冒烟通过 |
| 全部选定前端 | 全部 READY，代理查询通过 |
| 支付主链路 | `PAYMENT_SUCCESS -> CLEARING_GENERATED -> SETTLEMENT_CREATED -> ACCOUNTING_POSTED` |
| 退款链路 | `REFUND_SUCCESS`，事实保持幂等 |
| 对账链路 | `COMPLETED`，`completedStageCount=4`，`expectedStageCount=4`，`financialCloseable=true` |
| 运行清理 | 验证结束后所有本次启动进程自动停止 |

本轮生成的隔离样例由脚本动态创建，未将固定历史编号当作当前事实。该验证仍只覆盖受控本地 demo/H2/HTTP 栈的正向收口，不替代真实渠道、生产 RabbitMQ/MySQL、失败重试/DLQ/replay、资金到账、正式审批或发布治理证据。

## 12. 2026-09-27 当前工作树 happy-path 复核

### 12.1 执行命令

```sh
tools/dev/local-stack.sh start backends
tools/dev/local-stack.sh happy-path
```

### 12.2 结果

| 范围 | 结果 |
| --- | --- |
| 后端栈 | 12 个后端全部 READY，使用已运行的本地服务进程 |
| 支付主链路 | 支付成功、清分单 `CLO20042`、结算单 `SLT20044` 均生成 |
| 退款链路 | 退款单 `REF20260927192013882` 与退款事件均生成，`REFUND_SUCCESS` 已进入账务事实 |
| 对账链路 | `COMPLETED`，完成阶段 `4/4`，`financialCloseable=true` |
| 账务与对账事实 | 账务包含 `PAYMENT_SUCCESS`、`CLEARING_GENERATED`、`REFUND_SUCCESS`；对账包含支付、清分、结算、账务、退款事实 |

本轮 `tools/dev/local-stack.sh happy-path` 返回 `[PASS] cross-system local happy path`。编号由脚本动态生成，验证的是当前工作树接入本地受控服务后的正向跨系统闭环；不替代真实渠道、生产消息与数据库、失败重试/DLQ/replay、资金到账、正式鉴权或发布治理证据。

## 13. 2026-09-27 跨系统金额断言精度回归

`tools/dev/cross-system-happy-path.mjs` 原先使用 `Number/toFixed(2)` 比较支付、退款、账务、清分和结算金额，超过 JavaScript 安全整数范围时可能把相差一分钱的事实误判为一致。本轮统一改为分币 `BigInt` 比较，并新增大额精确匹配与一分钱差异拒绝测试。

本轮自测：`node --test tools/dev/cross-system-happy-path.test.mjs`（28/28）、`node --check tools/dev/cross-system-happy-path.mjs`、`git diff --check`。该改动提升本地 happy-path 验收断言的可信度，不替代生产金额核对和财务验收。

## 15. 2026-09-27 跨系统 happy-path 非 JSON 错误诊断回归

跨系统验证器此前遇到服务返回 HTML 或其他非 JSON 内容时会直接抛出原始解析异常，错误中缺少失败端点和恢复提示。本轮统一包装响应解析失败，保留原始异常为 `cause`，并覆盖非 JSON 响应测试。

本轮自测：`node --test tools/dev/cross-system-happy-path.test.mjs`（29/29）、`node --check tools/dev/cross-system-happy-path.mjs`、`git diff --check`。该改动只改善联调失败诊断，不改变正向业务断言。

## 16. 2026-09-27 错误诊断修复后跨系统 happy-path 实跑

本轮执行 `tools/dev/local-stack.sh happy-path` 返回 `[PASS] cross-system local happy path`，生成支付单 `PAY1790509644934ea0322db`、清分单 `CLO20044`、结算单 `SLT20046`、退款单 `REF20260927194725463`。账务事实包含 `PAYMENT_SUCCESS`、`CLEARING_GENERATED`、`REFUND_SUCCESS`，对账状态为 `COMPLETED`，完成阶段 `4/4`，`financialCloseable=true`。

## 14. 2026-09-27 修复后跨系统 happy-path 实跑

本轮执行 `tools/dev/local-stack.sh happy-path` 返回 `[PASS] cross-system local happy path`，动态生成并完成以下链路：支付单 `PAY1790509430233fbf5e340`、清分单 `CLO20043`、结算单 `SLT20045`、退款单 `REF20260927194350897`。账务事实包含 `PAYMENT_SUCCESS`、`CLEARING_GENERATED`、`REFUND_SUCCESS`；对账事实包含支付、清分、结算、账务和退款，链状态为 `COMPLETED`，完成阶段 `4/4`，`financialCloseable=true`。

该证据覆盖当前工作树接入本地受控服务后的正向支付、退款和财务收口，不替代真实渠道、生产消息与数据库、失败重试/DLQ/replay、资金到账、正式鉴权或发布治理证据。

## 17. 2026-09-29 当前工作树跨系统 happy-path 最终复核

本轮先重启 payment-core、clearing、settlement、accounting、refund-center 和 reconciliation 的本地服务，清理 H2 内存态污染，再执行 `node tools/dev/cross-system-happy-path.mjs`。验证结果如下：

| 范围 | 结果 |
| --- | --- |
| 支付主链路 | 支付单 `PAY1790633306809a9f9346c`、支付成功事件 `EVT1790633306904364cc881` |
| 清分与结算 | 清分单 `CLO20012`、结算单 `SLT20006`，统一 `CHAIN-PAY1790633306809a9f9346c` |
| 退款链路 | 退款单 `REF20260929060827366`、成功事件 `REVT-04214bdd1a10454ea57bb9e1c0571727`，申请、审批、提交、回调和 outbox 重复派发均通过 |
| 对账链路 | `COMPLETED`，完成阶段 `4/4`，`7` 条成功事实，`financialCloseable=true` |

对账事实包含支付成功、清分生成、结算生成、支付入账、清分入账、退款入账和退款成功；`businessChainId` 全部匹配同一支付单。该结果仅证明本地 H2/demo/HTTP 受控栈的正向闭环，不替代真实渠道、生产 RabbitMQ/MySQL、失败重试/DLQ/replay、资金到账、正式审批或发布治理证据。

## 18. 2026-09-29 跨系统支付成功投影幂等复核

跨系统 happy-path 原先只向退款中心投递一次支付成功投影，未验证重复投影不会阻断后续退款申请。本轮对同一支付成功投影连续调用两次，再继续执行退款申请、回调、账务入账和对账收口，用正向链路覆盖退款中心支付成功投影的跨系统幂等边界。

本轮自测：`node --test tools/dev/cross-system-happy-path.test.mjs`、脚本 `node --check`、本地 happy-path 实跑及 `git diff --check` 均通过。该证据不替代真实消息重复投递、生产数据库约束、渠道到账或正式发布治理验证。

## 19. 2026-09-30 跨系统退款详情回查业务链路归属校验补齐

跨系统 happy-path 在退款成功后回查退款详情时此前未传入期望 `businessChainId`，只能校验退款单号、支付单号和业务订单号，无法发现详情响应的链路漂移。本轮要求详情回查严格匹配 `CHAIN-{paymentOrderId}`，并补充缺失链路与错配链路拒绝测试。

本轮自测：`node --test tools/dev/cross-system-happy-path.test.mjs`（34/34）、`node --check tools/dev/cross-system-happy-path.mjs` 与 `git diff --check` 通过；本轮未重复执行本地服务实跑。该证据不替代真实退款中心 HTTP、消息投影、生产数据库或正式发布治理验证。

## 20. 2026-09-30 跨系统支付成功事件业务链路归属校验补齐

跨系统 happy-path 在支付成功事件投影回查时此前只校验支付单号、订单号和发布完成状态，未校验事件返回的 `businessChainId`。本轮要求真实主流程调用传入 `CHAIN-{paymentOrderId}`，并补充缺失与错配链路拒绝测试。

本轮自测：`node --test tools/dev/cross-system-happy-path.test.mjs`（34/34）、`node --check tools/dev/cross-system-happy-path.mjs` 与 `git diff --check` 通过；本轮未重复执行本地服务实跑。该证据不替代真实支付事件 HTTP、消息投影、生产数据库或正式发布治理验证。

## 21. 2026-09-30 跨系统 happy-path 补齐审核与出款执行闭环

跨系统 happy-path 在清分生成结算单后此前只核验结算单处于“待审核/待出款”，没有继续调用结算审核、出款批次查询、出款执行及结算终态查询，无法证明运营审批到出款收口的正向链路。本轮接入真实结算 HTTP 步骤：审核结算单进入出款队列，按当前结算单号校验出款记录归属和金额，执行成功后校验出款批次完成计数、记录“已发放”及结算单“已出款/已发放”终态；共享出款批次中的其他记录不会干扰当前结算单断言。

本轮自测：`node --test tools/dev/cross-system-happy-path.test.mjs`（`35/35`）、`node --check tools/dev/cross-system-happy-path.mjs` 与 `git diff --check` 通过。本轮未启动本地多服务栈，未新增真实 HTTP happy-path 成功结论；该证据不替代真实银行渠道、生产数据库、消息总线、资金到账或正式审批发布治理验收。

## 27. 2026-09-30 payment-chain 复核被外部旧实例阻断

本轮执行 `HSP_LOCAL_START_TIMEOUT_SECONDS=120 HSP_MAVEN_REPO=/Users/abc123/apache-maven-3.9.16/repository bash tools/dev/local-stack.sh verify payment-chain`。脚本自身启动的 risk-control-system 与 gateway-access 已就绪，所选后端 HTTP 冒烟通过；但固定端口上的 payment-core 被识别为外部进程，happy-path 预付单响应缺少 `businessChainId`，在第一条链路断言处停止。随后脚本清理了本轮启动的进程。该结果不能代表当前工作树构建失败，也不能新增 HTTP happy-path 成功证据；需要先停止占用固定端口的旧实例，再用当前工作树重新启动依赖栈。

本轮定向契约测试与 `node --check tools/dev/cross-system-happy-path.mjs` 未因该运行失败而改写；不替代真实消息总线、生产数据库、银行渠道、资金到账或正式发布治理验收。

## 28. 2026-09-30 payment-chain 重试仍未形成当前工作树成功证据

固定端口短暂无响应后再次执行 `tools/dev/local-stack.sh verify payment-chain`。risk-control-system 与 gateway-access 由本轮脚本启动并通过 HTTP 冒烟；payment-core、账务、清分、结算、退款和对账随后再次被外部进程接管，happy-path 预付单响应仍缺少 `businessChainId`，在第一条业务链路断言处失败，脚本已清理本轮启动进程。本轮不新增 `COMPLETED`、`4/4` 或 `financialCloseable=true` 证据。

## 22. 2026-09-30 跨系统结算审核响应连续性门禁

跨系统 happy-path 此前审核结算单时只校验结算单号和状态，清分归属、业务链路或净结算金额发生漂移仍可能继续进入出款。本轮要求审核响应继续匹配原清分单、`businessChainId` 和净结算金额，并补充三类错配回归。

本轮自测：`node --test tools/dev/cross-system-happy-path.test.mjs`（`35/35`）、`node --check tools/dev/cross-system-happy-path.mjs` 与 `git diff --check` 通过。本轮未启动本地多服务栈，未新增真实 HTTP happy-path 成功结论；该证据不替代真实银行渠道、生产数据库、消息总线、资金到账或正式审批发布治理验收。

## 23. 2026-09-30 跨系统出款后结算收口连续性门禁

跨系统 happy-path 在出款后回查结算详情时此前只校验“已出款/已发放/已通过”三个状态，未验证清分归属、`businessChainId`、净结算金额和终态样式。本轮新增 `assertSettlementClosed`，要求最终详情同时匹配原结算单、清分单、业务链路和净额，并补充归属、金额、链路及样式错配回归。

本轮自测：`node --test tools/dev/cross-system-happy-path.test.mjs`（`35/35`）、`node --check tools/dev/cross-system-happy-path.mjs` 与 `git diff --check` 通过。本轮未启动本地多服务栈，未新增真实 HTTP happy-path 成功结论；该证据不替代真实银行渠道、生产数据库、消息总线、资金到账或正式审批发布治理验收。

## 24. 2026-09-30 当前本地栈启动复核

本轮先执行 `tools/dev/local-stack.sh doctor backends`，curl、仓库 Maven、JDK 8、javac 和 Maven 缓存均可用；随后以提升权限执行 `tools/dev/local-stack.sh start backends`。ops-config 被脚本识别为外部提供，但 risk-control-system 与 gateway-access 分别在 `90` 秒内未就绪，脚本启动随后续服务继续推进时中止，并执行 `stop backends` 清理本轮管理的进程。最终后端状态全部为 `DOWN`，本轮未执行 `node tools/dev/cross-system-happy-path.mjs`，不新增当前 HTTP happy-path 成功结论。

本轮自测：`node --test tools/dev/cross-system-happy-path.test.mjs`（`35/35`）仍通过；启动失败日志保留在 `.runtime/local-stack/logs/`。当前证据不替代真实消息总线、生产数据库、银行渠道、资金到账或正式发布治理验收。

## 29. 2026-09-30 预付单业务链路断言空白归一化

跨系统 happy-path 验证器此前检查预付单 `businessChainId` 时直接使用响应中的原始 `paymentOrderId`，而支付响应链路标识按裁剪后的支付单号派生；带首尾空白的合法响应会被验证器误判为链路错配。本轮将该断言统一为 `CHAIN-{paymentOrderId.trim()}`，并补充带空白支付单号的回归。

本轮自测：`node --test tools/dev/cross-system-happy-path.test.mjs`（`35/35`）、`node --check tools/dev/cross-system-happy-path.mjs` 与 `git diff --check` 通过。本轮只增强验证器契约，不新增真实 HTTP happy-path 成功结论。

## 30. 2026-09-30 跨系统服务地址归一化

跨系统 happy-path 验证器此前直接使用环境变量服务地址；地址带首尾空白或多个末尾斜杠时，会污染请求 URL 并使所有链路请求失败。本轮统一裁剪服务地址并移除全部末尾斜杠，覆盖支付、清分、结算、账务、退款和对账服务。

本轮自测：`node --test tools/dev/cross-system-happy-path.test.mjs`（`36/36`）、`node --check tools/dev/cross-system-happy-path.mjs` 与 `git diff --check` 通过。本轮只增强验证器配置鲁棒性，不新增真实 HTTP happy-path 成功结论。
## 25. 2026-09-30 支付主链路验证范围隔离

`local-stack.sh verify all` 会同时启动 deposit、wallet 等不参与支付 happy-path 的系统；这些系统启动失败时会阻断支付、清分、结算、账务、退款和对账链路验证。本轮新增 `payment-chain` 范围，仅选择支付主链路所需的后端依赖，并补充脚本契约测试，避免无关系统状态掩盖支付链路验证结果。

本轮自测：`node --test tools/dev/local-infrastructure-defaults.test.mjs`、`bash -n tools/dev/local-stack.sh`、`git diff --check`。尚未据此宣称新的 HTTP happy-path 成功，仍需在该范围下实际启动并运行链路。

## 26. 2026-09-30 账务事件与流水时间顺序修复

真实 HTTP happy-path 在退款成功账务核验阶段发现账务流水先于其源事件创建。根因是账务事件消费方法统一先写 ledger、再写 event；本轮改为同事务内先记录事件、再记账，覆盖支付成功、清分生成、退款成功和结算补偿四类消费，并补充退款事件/流水时间顺序回归。

## 27. 2026-09-30 本地栈子进程清理修复

`local-stack.sh` 此前停止服务时只终止记录的 Maven 父进程，`spring-boot:run` 派生的 Spring Boot JVM 可能继续占用固定端口，造成后续 payment-chain 被残留实例接管。本轮增加递归子进程清理，在父进程终止前回收其子进程，并补充脚本契约测试。

本轮自测：`bash -n tools/dev/local-stack.sh`、`node --test tools/dev/local-infrastructure-defaults.test.mjs`（`4/4`）和 `git diff --check` 通过。本轮尚未重新执行真实 HTTP happy-path，不新增 `COMPLETED`、`4/4` 或 `financialCloseable=true` 证据。

## 28. 2026-09-30 本地栈运行目录可配置与受控复核

本地栈新增 `HSP_LOCAL_RUNTIME_DIR`，允许将 PID/日志目录放到工作树外，默认目录保持 `.runtime/local-stack`。提升权限复核时 risk-control 与 gateway-access 由当前脚本启动并通过 HTTP 冒烟，payment-core 及其他依赖仍由外部实例提供；预付响应缺少 `businessChainId`，验证器在首条链路断言处失败，并清理本轮启动的进程。

本轮自测：本地基础设施契约测试 `5/5`、`bash -n tools/dev/local-stack.sh` 和 `git diff --check` 通过。该复核不新增真实 happy-path 成功证据，不能据此写入 `COMPLETED`、`4/4` 或 `financialCloseable=true`。

## 31. 2026-10-02 happy-path 支付场景变量作用域修复

跨系统验证器在成功回调后使用 `expectedPayScene` 校验支付事件、清分、账务、退款及对账事实，但主函数此前未声明该变量。单元断言测试只导入辅助函数，未执行主流程，因此无法发现真实 HTTP 路径上的 `ReferenceError`。本轮在主函数声明唯一的 `HOME_CLEAN` 预期场景，并统一用于预付请求和后续各阶段校验；新增主流程场景绑定回归。

本轮自测：`node --test tools/dev/cross-system-happy-path.test.mjs`（`52/52`）、`node --check tools/dev/cross-system-happy-path.mjs` 和 `git diff --check` 通过。`local-stack.sh status payment-chain` 显示九个依赖服务均为 `DOWN`；本轮尚未完成真实 HTTP happy-path，不新增 `COMPLETED`、`4/4` 或 `financialCloseable=true` 证据。

## 32. 2026-10-02 受控 HTTP 链路递进核验

使用 `local-stack.sh verify payment-chain` 按轮启动九个 demo 后端、执行 HTTP 冒烟和隔离支付单 happy-path，并由脚本清理进程。依次发现并修复支付详情 `payScene` 缺失、支付成功事件 payload 缺少场景、清分消费事件 payload 缺少业务链路；每次复跑均越过上一断点。当前最新断点为清分订单列表未提供服务者目标，验证器此前误从列表读取目标；本轮改从清分详情的 `WORKER` 分账项获取，并绑定清分单、业务链路与金额。

本轮自测：验证器 `52/52` 定向测试、Node 语法检查及 `git diff --check` 通过；payment-core、clearing-system 相关 Java 定向测试通过。最新清分详情验证器修复尚未进行完整 HTTP 复跑，因此不写入跨系统完成结论。所有尝试均为本地 demo，非真实微信、支付宝、银行或生产数据库/MQ 验收。

## 33. 2026-10-02 本地九服务 payment-chain happy-path 通过

清分目标改为读取清分详情的服务者分账项后，执行 `bash tools/dev/local-stack.sh verify payment-chain`，九个 demo 后端全部启动并通过 HTTP 冒烟。隔离链路 `LOCAL-HAPPY-1790905923821-19840` 完成预付、收银台、提交、成功回调与幂等、清分、结算审批及出款、退款、账务三类成功事件和对账事实核验。支付单 `PAY17909059240671492bba6`、清分单 `CLO20002`、结算单 `SLT20003`、退款单 `REF20261002095206915`；对账链 `COMPLETED`，阶段 `4/4`，`financialCloseable=true`。命令退出码 `0`，脚本已停止本轮启动的九个服务。

这是 H2/本地模拟协议和 HTTP 下游的**本地 demo 成功证据**，不等于生产数据库/MQ、真实微信/支付宝/银行协议、密钥托管、资金到账、现场核验或正式审批发布完成。payment-core 全量 Maven 回归仍有独立失败，见其自测记录。

## 34. 2026-10-02 全模块回归与九服务链路复核

在 payment-core 退款候选排序与支付事实测试夹具、账务事件空场景幂等与清分摘要、退款出站摘要断言、对账事实 `PENDING→SUCCESS` 状态升级兼容修正后，连续执行全后端回归 `node tools/dev/backend-regression.mjs`，`12/12` 模块通过；`node tools/dev/frontend-admin-regression.mjs` 的 `12/12` 管理端测试与构建通过；`node tools/dev/payment-core-user-regression.mjs` 的 `5/5` 用户端声明检查通过。payment-core Surefire XML 汇总 `1454` 项、`0` 失败、`0` 错误、`0` 跳过。

随后在允许进程状态查询的本地环境执行 `bash tools/dev/local-stack.sh verify payment-chain`，九服务启动并通过 HTTP 冒烟。隔离链路 `LOCAL-HAPPY-1790908852389-27182` 的支付单 `PAY17909088525756cc3ca8d`、清分单 `CLO20002`、结算单 `SLT20003`、退款单 `REF20261002104055269` 收口，对账 `COMPLETED`、阶段 `4/4`、`financialCloseable=true`；验证命令退出码 `0`，九服务由脚本停止。受限沙箱禁止 `ps/pgrep` 时脚本会误判已启动进程为退出，故该次复核在有进程查询权限的本地环境执行，不将沙箱内误判当作业务失败。

这些结果只证明本地 H2、模拟协议、HTTP demo 与源码级回归。真实渠道及通知供应商、生产数据库/MQ、凭证托管、资金到账、权限审批、发布签字和现场观察仍是独立验收项，不由本轮本地证据放行。

## 35. 2026-10-03 退款额度并发锁与清分下游回执复核

退款中心针对同一支付成功投影行加事务锁，先锁后读幂等键和已占用退款额度；隔离 H2 并发用例证实前一笔 `70.00` 提交后，等待中的另一笔 `60.00` 被超额门禁拒绝，同键重放返回原退款单。退款中心 Maven 全量 `37/37` 通过，目标 MySQL 8 并发证据仍缺。

首次九服务 `verify payment-chain` 在 `accounting CLEARING_GENERATED/CLO20003` 唯一已消费事件断言处失败，脚本自动停止服务；清分出站只看 HTTP 状态，可能把下游 `200 + 非零业务码` 误标成功。新增清分结算/账务/对账回执 `code=0` 门禁及账务业务失败测试；随后独立启动九个本地 demo 后端并运行 happy-path：支付单 `PAY179100401831734a52576`、清分单 `CLO20003`、退款单 `REF20261003130700242`、账务三类事件和对账 `4/4` 完成，服务已清理。后续补上结算/账务地址为空的失败闭合和测试，清分 Maven 全量 `54/54`；该补强未再次运行九服务。首次失败的原始下游响应已随隔离栈关闭而不可追溯，后一次通过不等于证明其根因；正式资金、真实下游、MySQL 8、权限及发布仍未放行。
# 九服务隔离演示身份契约（无监听路由测试通过，跨进程 HTTP 待验）

跨系统写入前，`verify payment-chain/all` 应在受控 `local-payment-chain` profile 下逐一读取支付核心、运营配置、风控、网关、账务、清分、结算、退款中心和对账中心的 `/api/demo/identity`；各服务仅在该演示 profile（支付核心仍使用 `demo`）及 H2 驱动同时满足时暴露独有标识。任何服务缺失、标识错误或重定向都应在首笔演示写入前失败闭合。该标识是本机误连防护，不是认证，也不证明真实渠道或生产资金。

八个下游控制器的 profile/H2 驱动定向测试 `8/8`，跨系统脚本与本地入口离线合同 `71/71` 通过；九服务逐一读回和错配失败均覆盖。尚未启动九服务执行写入型 HTTP happy-path，不能据此声称完整 HTTP 联调通过。

进一步用无监听 MockMvc 对八个下游逐一验证 `GET /api/demo/identity` 的 HTTP 200、`code=0` 与独有 `data`，每类仍同时验证 profile/驱动门禁；支付核心无需引入新测试依赖，以现有独立上下文测试确认 demo/H2 条件、映射注解与响应码/身份值。九类定向 Maven 测试 `9/9`，零失败、零错误、零跳过；此证据不等于各服务实际端口上的跨进程读回或写入型全链路。

## 36. 2026-10-10 支付主动查单双服务本地 HTTP 回环

`node tools/dev/payment-core-gateway-query-smoke.mjs` 在单次受控会话启动 payment-core、gateway-access 及本机查询/下游桩，强制精确 H2/demo 身份并将证书续期限定于当次初始化数据。支付核心运营查单由真实 HTTP 穿过网关凭证与治理，再到本机支付宝查询桩；未知状态和错绑渠道交易号各保留待回调与失败日志，精确成功事实才收口支付/尝试/预付单/账单并产生唯一成功事件。本机模拟清分/账务各收到一次，终态重放不再次查上游。扩展权限下脚本 `1/1` 通过，退出停服；未向外部渠道或真实资金系统写入。此处下游仍是 mock，不能与既有九服务由**回调**驱动的 `COMPLETED`、`4/4` 验收混同；九服务由**主动查单**驱动的对账仍待验证。

## 37. 2026-10-10 九服务主动查单驱动对账完成

`bash tools/dev/local-stack.sh verify payment-query-chain` 在九服务 H2/demo 写前门禁之外额外检查本机 `18190` 端口和 mock PID，启动受控查询桩并为当次网关 H2 fixture 续期样例微信证书。支付提交后，原 happy-path 的查询变体以真实 payment-core→gateway-access HTTP 经本机上游确认 `SUCCESS`，而非伪装成支付回调；网关审计和上游请求均为一条，重复终态查单不再访问上游。支付单 `PAY17915989466695ddb85e8` 随后经过真实清分、结算、退款中心、账务与对账服务，链路 `COMPLETED`、阶段 `4/4`、本地 `financialCloseable=true`。命令退出码 `0`，九服务及上游 STOP；离线契约 `76/76` 通过。上游事实仍是本机 mock，正式渠道资金/证书与可信身份、目标 MySQL 8/历史迁移及生产放行完全独立。

查询变体引入的空环境数组在默认回调模式遇到 Bash `set -u` 时会使支付核心未启动；修复条件展开后，另起一轮 `verify payment-chain` 回归通过，支付单 `PAY17915993734874cd16bac` 的 `paymentCompletion=CALLBACK`、对账 `COMPLETED`、`4/4`，九服务随后全部停服。两次不同演示订单分别证明主动查单与回调路径，不能相互冒名。

最后为查询桩增加 `/health` 精确身份读回及上游订单/渠道核对，重新运行 `verify payment-query-chain`：支付单 `PAY17915995740595a15359b` 的 `paymentCompletion=GATEWAY_QUERY`、对账 `COMPLETED`、阶段 `4/4`、本地 `financialCloseable=true`；命令 `0`，九服务与本机桩均 STOP。

## 38. 2026-10-10 渠道未知状态拒绝后同单重查收口

九服务查询桩对首次精确绑定请求返回未知 `PAID`，网关和支付核心失败闭合：运营查单返回 `PAYMENT-1026`，支付仍 `WAIT_CALLBACK`、账单未结且没有成功出站事件。第二次同单同渠道交易号查询模拟 `SUCCESS`，支付收口后才由真实下游清分、账务、结算、退款中心与对账服务处理；终态重放不产生第三次上游请求。网关查询审计失败、成功各一条。`verify payment-query-chain` 返回 `0`，支付 `PAY17915999007646ed41f00`、对账 `COMPLETED`、`4/4`，九服务与 mock 均 STOP，静态契约 `76/76`。这是本机 H2/demo/mock 的拒绝与恢复链路，不是正式渠道故障、资金或 MySQL 8 竞态验收。

## 39. 2026-10-10 双服务查单与回调抢先竞态

`payment-core-gateway-query-smoke.mjs` 在第三次支付核心→网关→本机上游查单到达后暂停返回，先以真实 payment-core HTTP 回调确认同单成功，再放行查询。查询返回回调赢家，唯一成功事件只向本机清分、账务桩各投递一次；原两次无效事实拒绝与网关审计仍成立。脚本退出码 `0`，两服务清理；相邻 payment-core H2/HTTP 定向测试 `1/1`，并直接核对成功出站为回调载荷而非晚到的 `ACTIVE_QUERY` 载荷。此受控本机时序不是 MySQL 8 多实例并发、真实渠道资金或九服务竞态验收。

## 40. 2026-10-10 成功支付后的同渠道回调重放

payment-core H2/HTTP 定向测试先由主动查单确认成功，再重放查询及同渠道成功回调，唯一事件仍保留 `ACTIVE_QUERY` 来源和原操作人、只派发一次；离线 `1/1`。双服务脚本在回调抢先赢得上一轮查询后额外重放同渠道回调，仍无第二次下游投递或额外上游查单，退出码 `0` 且停服。两者是不同赢家时序，不证明双服务查单赢家的并发，也不证明 MySQL 8 多实例及真实渠道资金。

## 41. 2026-10-10 回调赢家后的冲突查单回环

双服务脚本以 `HSP_QUERY_RACE_MODE=conflicting` 在第三次网关查询到达本机上游后延迟响应；支付核心回调先成功，再收到上游 `SUCCESS/ALI-WRONG`，拒绝该错绑结果并记录第三条查单失败日志。唯一成功事件及本机清分/账务各一次不变，终态重放不额外访问上游。默认匹配变体也通过，两轮各退出 `0`、停服；相邻支付核心真实 HTTP 定向正反竞态 `2/2`。这仍是 H2/demo、本机 mock 和单实例时序，不等于 MySQL 8 多实例、真实渠道资金或九服务竞态验收。

## 42. 2026-10-10 用户支付结果页动作报错后只读核实

结果页查单/关闭 POST 失败后按原支付身份再 GET 详情；本机 Chrome 静态产物和 mock API 的四轮覆盖：POST 返回 HTTP 400 但状态已变成功/关闭、读回确认后展示新状态，或 GET 503 时撤销旧快照及操作并提示未经确认，手动重试再显示新状态。各轮一次 POST、至少两次 GET；浏览器脚本通过，用户端 `111/111`、构建通过。这是前端错误处理回环，不等于真实渠道资金、后端多实例或跨九服务运行证据。

## 43. 2026-10-10 收银台失败动作只读核实与锁定

收银台查单/关闭 POST 报错后按原预付单与业务场景 GET 读回；新增隔离 Chrome 四轮：mock 的支付已成功/关闭时进入结果页，GET 503 时锁定旧快照、禁用查单/关单及幂等键重置，手动刷新恢复后进入结果页。连同原结果页四轮，同脚本八轮各只发送一次 POST、至少两次对应 GET；用户端 `112/112`、Vite 构建、脚本 `[PASS]`。这是本机前端/mock 证据，不等于渠道资金或真实支付核心并发验收。

## 44. 2026-10-10 支付提交响应丢失的用户端恢复

同一隔离浏览器脚本增加两轮：一次虚构预付提交返回 HTTP 400，本机 mock 进入 `WAIT_CALLBACK`；读回成功时用户端锁定重复支付、开放原渠道查单，首次 GET 503 时提示状态未知并禁用动作，手动刷新后才恢复查单。十轮总回归 `[PASS]`，提交两轮均只有一次 POST、至少两次预付 GET；用户端 `113/113`、构建通过。该模拟不证明渠道真实受理、资金、生产令牌或目标 MySQL 8 并发幂等。

## 45. 2026-10-10 当前改动下九服务两条收口路径复验

检查目标端口、环境覆盖及本轮 PID 门禁后，分别运行 `bash tools/dev/local-stack.sh verify payment-chain` 与 `bash tools/dev/local-stack.sh verify payment-query-chain`。受限沙箱无法执行进程状态查询 `ps`，第一轮启动被脚本判为退出且未写入业务；随后对同一命令使用获准的进程查询权限重试。回调变体支付 `PAY179160830271069a648e0`，主动查单变体支付 `PAY1791608484863ab2997ee`，均由本轮九个 H2/demo 后端提供 HTTP 冒烟及写前身份核验，收口支付、清分、结算、退款、三类账务事件及七项对账事实。两轮各自返回 `COMPLETED`、阶段 `4/4`、本地 `financialCloseable=true`，命令退出码均为 `0`；九服务及查询变体的上游 mock 均由脚本停止，目标端口无监听，未触碰现存前端 PID。此为两笔不同虚构订单的本机回调/查询回归，不是目标 MySQL 8 多实例、正式身份、真实渠道资金或生产发布证据。

## 46. 2026-10-10 支付请求摘要落库后九服务复验

支付尝试新增 `request_fingerprint` 且同键内容复核后，分别运行隔离 `verify payment-chain` 和 `verify payment-query-chain`。回调支付 `PAY1791610810378388267ed`、网关主动查单支付 `PAY1791610994207deb1b3b9` 分别经本轮九服务 H2/demo 身份核验、HTTP 冒烟及完整 happy-path；两条独立演示链均达对账 `COMPLETED`、阶段 `4/4`、本地 `financialCloseable=true`，各命令退出码 `0`。脚本逐一停止九个后端及查询模式的本机上游，随后 `status payment-query-chain` 九项均 `DOWN`、相关十个端口无监听。这里仅证实新 schema 下本地回调/查询主路径未回退，不证明同键跨实例竞态、生产历史迁移、可信身份、MySQL 8 或真实渠道资金与正式关账。

## 47. 2026-10-10 精确幂等键生成列后的九服务复验

两份支付尝试基线改为 `VARBINARY(512)` 生成列精确唯一后，在目标端口空闲、无数据库覆盖环境变量下，分别运行隔离 `verify payment-chain` 与 `verify payment-query-chain`。回调支付 `PAY179161493267645a7b8aa`、主动查单支付 `PAY1791615107577737f160c` 各自通过本轮 H2/demo 写前身份核验、九服务 HTTP 冒烟和完整 happy-path；对账均为 `COMPLETED`、阶段 `4/4`、本地 `financialCloseable=true`。两轮命令退出码 `0`，九服务与本机上游查询桩均自动停止；随后 `status payment-query-chain` 九项 `DOWN`，相关十端口无监听。此为新 schema 下两笔独立虚构订单的本机主路径证据，不是目标 MySQL 8 增量迁移、多实例、真实渠道资金、可信身份或正式关账验收。

## 48. 2026-10-10 自动幂等键修复后的九服务复验

支付提交对超长自动键改为固定摘要后，重新分别运行隔离 `verify payment-chain` 与 `verify payment-query-chain`。受限沙箱内 `ps` 不可读取进程状态，首轮脚本误判九服务退出、写前身份门禁未放行；获准的进程状态权限下重试成功。回调支付 `PAY1791616608977f60b7efd`、网关主动查单支付 `PAY179161677666883bc4ad4` 分别经九服务 H2/demo HTTP 冒烟、支付、清分、结算、退款、三类账务事件和对账收口，各自 `COMPLETED/4/4`、本地 `reconciliationFinancialCloseable=true`，命令退出码均 `0`。本轮仅验证常规渠道 happy-path；长组合另由隔离 H2/demo 专用模拟适配器测试覆盖落库及重放。脚本停止九服务及查询桩后复核九项 `DOWN`、十端口无监听。目标 MySQL 8、多实例、正式身份、真实渠道资金与正式关账仍未验收。

## 49. 2026-10-10 支付未决键摘要失败的用户端浏览器回归

`payment-result-action-refresh-browser-smoke.mjs` 在同一标签页保留第二张虚构预付单未决键时，向新文档注入失败的 `SubtleCrypto.digest`。页面刷新后拒绝显示提交动作并提示未决记录不可核实；本机 mock 的支付 POST 数保持不变，会话存储仍保留原键。移除注入并刷新后原键再次恢复，随后畸形记录拒绝提交及服务端可信成功终态只读进入结果页的原场景仍 `[PASS]`。用户端 `121/121`、构建及隔离 Chrome/mock 回归通过。未连接真实支付核心，不代表跨标签页/会话、可信身份、正式渠道资金或 MySQL 8 幂等验收。

## 50. 2026-10-10 支付未决键会话存储读取故障的浏览器回归

延续第 49 节的隔离 Chrome/mock：在已有未决记录后向新文档注入抛错的 `Storage.getItem`，页面刷新后不显示支付提交动作，提示未决记录不可核实，本机 mock 的支付 POST 数保持不变，原存储值仍在。移除故障后再刷新，原支付键恢复；摘要失败、畸形存储及可信终态只读展示场景继续 `[PASS]`。用户端 `122/122`、构建、浏览器脚本均通过；存储写入/删除故障由单元测试覆盖。此为本机用户端门禁，不是跨标签页/会话、真实渠道、可信身份或目标 MySQL 8 验收。

## 51. 2026-10-10 管理端异常批量动作畸形未决键浏览器回归

隔离 Chrome/mock 保持事件、退款、支付、任务中心和死信的原错误读回场景，以及异常批量动作响应丢失后刷新同内容使用原键的回归。新增在异常列表可操作且选中一条时向本标签页写入损坏的 `payment-issue-action-attempt:v1` 值：点击“批量处理选中异常”后显示“批量处理未发送：未决批量动作无法核实”，本机异常动作 POST 数仍为零，原损坏值未被清理。管理端 `302/302`、构建与浏览器脚本 `[PASS]`；存储/摘要能力故障由工具单测覆盖。此为本机管理端门禁，不是可信审批、跨标签页恢复或目标 MySQL 8/正式渠道资金验收。

## 52. 2026-10-10 管理端异常批量动作分区存储回归

`paymentIssueActionAttempt` 由单槽位改为摘要分区，工具单测覆盖两种动作并存、分别恢复/清理、旧单键读取、旧键/新分区冲突失败闭合、损坏分区隔离和同页原键重试。管理端 `305/305`、Vite 构建及隔离 Chrome/mock 原错误读回场景均通过。浏览器新增两笔不同备注的虚构异常批量动作，分别模拟 POST 响应丢失；会话存储保留两个摘要分区。页面分别刷新并重输原动作后，两次 POST 请求体（含幂等键）各与自身首次请求一致、两键不同；畸形旧键仍拒绝 POST，脚本 `[PASS]`。该证据不代替跨标签页/会话、可信身份、正式资金/通知或目标 MySQL 8 验收。

## 53. 2026-10-10 管理端退款申请刷新恢复本机回归

隔离 Chrome/mock 先提交虚构退款申请，第一次 POST 模拟响应丢失且列表读回失败。确认本标签页 `sessionStorage` 只保存摘要与随机键、不含支付单号；刷新并重输原申请后，将分区记录临时改为畸形 JSON，页面提示未决申请无法核实，退款申请 POST 仍为一次。恢复原记录再提交，第二次 POST 的完整请求体与第一次相同，页面显示退款单当前状态，成功后对应分区被清理。管理端工具定向 `5/5`、全量前端 `307/307`、Vite 构建及隔离脚本 `[PASS]`；不连接真实退款渠道，不代表资金到账、跨标签页/会话、目标 MySQL 8 或可信运营身份验收。

## 54. 2026-10-10 用户端未决支付键静默丢写本机回归

在隔离 Chrome/mock 的未使用虚构预付单页面，向新文档注入只忽略 `hsp.paymentSubmitAttempt.v1.` 分区写入的 `Storage.setItem`，其余存储及访问令牌正常。点击“确认支付”后页面提示未决支付记录无法核实，mock 支付 POST 数仍为零，未决分区数量未增加。移除注入后既有两张预付单分别持键、同内容重试、摘要/存储读取故障与畸形记录场景仍 `[PASS]`。用户端工具定向 `8/8`、全量 `125/125` 和 Vite 构建通过；静默删除与数组 UUID 拒绝由工具单测覆盖。该证据不代替真实渠道、多标签页/会话或目标 MySQL 8 验收。

## 55. 2026-10-10 管理端异常批量动作静默丢写本机回归

隔离 Chrome/mock 在两笔不同内容的虚构批量动作模拟响应丢失后，保留两个待核实键；第三笔不同备注的新动作注入 `Storage.setItem` 静默忽略分区写入。页面提示“批量处理未发送：未决批量动作无法核实”，动作 POST 仍为两次，两个原分区未被覆盖；移除注入并刷新后，前两笔继续以各自原请求体和原键重放。原异常、退款、支付、任务及死信错误读回回归保持 `[PASS]`。管理端工具定向 `11/11`、全量 `311/311`、构建通过。仅为本机 mock 门禁，不证明真实资金、可信运营身份、跨标签页/会话或目标 MySQL 8 验收。

## 56. 2026-10-10 管理端退款申请清键失败后的本机回归

隔离 Chrome/mock 中，首笔虚构申请模拟响应丢失并按原键刷新恢复；第二次 POST 返回原退款单后，注入 `Storage.removeItem` 对申请分区静默无效。页面同时显示原退款单 `REVIEWING` 和“本地申请键未能清理”，分区被标记为 `confirmed`。恢复删除能力并刷新，重输相同内容后页面提示未决申请无法核实，退款申请 POST 始终为两次；测试结束清理该隔离资料。管理端工具定向 `7/7`、全量 `313/313`、Vite 构建与原管理端动作浏览器脚本通过。仅为本机 mock 与前端事实反馈，不证明跨会话、真实渠道退款到账、可信身份或目标 MySQL 8 验收。

## 57. 2026-10-11 当前基线九服务支付链路复验

在固定端口和现存后端 PID 的写前隔离检查通过后，执行 `local-stack.sh verify payment-chain`。运营配置、风控、网关、账务、清分、结算、退款、对账和支付核心九个后端依次 `READY`，所选 HTTP 冒烟及跨系统 happy-path 均 `[PASS]`。本机虚构交易以回调收口支付，生成成功事件、清分和结算事实，并由独立 refund-center 承接退款；账务包含 `PAYMENT_SUCCESS`、`CLEARING_GENERATED`、`REFUND_SUCCESS`，对账链路为 `COMPLETED`、阶段 `4/4`、`financialCloseable=true`。脚本退出码 `0`，九个后端均停服，相关端口和 PID 文件复核无遗留。只证明隔离 H2/demo 及本机 HTTP 协议链路，未验证真实渠道资金、目标 MySQL 8、多实例、可信主体或生产发布。

## 58. 2026-10-11 当前基线九服务主动网关查单链路复验

在固定端口、环境覆盖与现存 PID 的写前隔离检查通过后，执行 `local-stack.sh verify payment-query-chain`。本机受控查询上游及九个后端均 `READY`，HTTP 冒烟与跨系统主动查单 happy-path 均 `[PASS]`；虚构支付由 `GATEWAY_QUERY` 收口，后续支付成功、清分、结算、账务与退款事实进入对账，链路 `COMPLETED`、阶段 `4/4`、`financialCloseable=true`。退出码 `0`，九个后端与查询上游均停服，相关端口和 PID 文件复核无遗留。此路径仅使用 H2/demo 与本机假上游，不证明真实渠道查单、资金到账、目标 MySQL 8、多实例、可信身份或发布。

## 59. 2026-10-11 当前基线风控双服务复核周期

在目标端口与现存 PID 隔离后运行 `local-stack.sh verify risk-review`，支付核心和风控后端均 `READY`，HTTP 冒烟与本地人工复核周期均 `[PASS]`。演示订单待审及批准后变更绑定上下文返回 `PAYMENT-1024`，原请求批准后进入 `WAIT_CALLBACK`；另一个硬拒绝订单维持 `RISK_BLOCKED`。退出码 `0`，两服务停服、端口/PID 无遗留。仅为 H2/demo 的本机 HTTP 状态门禁，不证明可信审核人身份、目标 MySQL 8、正式渠道资金或发布。

## 60. 2026-10-11 全量本地栈写前隔离与闭环复验

`verify all` 新增前端 PID 文件及监听端口写前检查，不再仅隔离后端，避免复用已有管理端或用户端。静态及拒绝现存 PID 的定向测试 `15/15`、开发脚本全量 `105/105` 通过。在独立临时运行目录执行 `local-stack.sh verify all`，12 个后端及 15 个前端均 `READY`，所选 HTTP 冒烟和跨系统 happy-path 均 `[PASS]`；支付、清分、结算、账务和退款事实进入对账，链路 `COMPLETED`、阶段 `4/4`、`reconciliationFinancialCloseable=true`。退出码 `0`，本轮 27 个服务均显示 `STOP`，临时 PID 目录无文件。只证明本机 H2/demo 和模拟协议闭环，不代替 MySQL 8、正式渠道真实资金、可信身份及生产发布验收。

## 61. 2026-10-11 全量栈前端端口与启动归属复核

补齐 `verify all` 的前端端口占用写前拒绝用例，并在启动后对 15 个前端检查本轮 PID 是否存在且存活，避免写前检查与启动之间端口被其他进程抢占后复用外部页面。先验证启动归属测试在旧实现上失败，修正后定向 `17/17`、获准回环环境开发脚本全量 `107/107` 通过。独立临时运行目录下 `verify all` 退出码 `0`：12 后端和 15 前端均 `READY`，HTTP 冒烟与跨系统 happy-path 均 `[PASS]`，27 个本轮进程均 `STOP`，临时 PID 无遗留且目标监听端口空闲。只证明本机 H2/demo 模拟链路及进程归属，不代表 MySQL 8、真实资金渠道、可信身份或发布验收。

## 62. 2026-10-11 收银台提交响应未推进的浏览器回归

隔离 Chrome/mock 对一张虚构预付单首次 POST 返回 `code=0`，但收银台状态仍为 `PREPAY_CREATED`，GET 读回仍待支付。页面不跳转成功结果，也不清除当前分区的未决键；重置入口禁用。刷新后原键仍可见，第二次 POST 使用与第一次完全相同的请求体和键，模拟受理后进入待回调结果页。App/H5/PC 共用客户端单测先红后绿，用户端 `125/125`、Vite 构建及浏览器脚本 `[PASS]`。仅为本机 mock 和客户端状态门禁，不证明正式渠道、真实资金、目标 MySQL 8、跨标签页/会话、可信身份或发布。

## 63. 2026-10-11 PAYING 提交响应的四端未决门禁

App/H5/PC 共用客户端与小程序客户端对 `code=0 / PAYING` 的支付提交结果先红后绿：不以服务端处理中状态作为渠道受理完成事实。小程序附带有效原生支付参数的同状态页面用例不调用微信支付、不跳结果页，GET 读回仍处理中时保留确定性键。隔离 Chrome/mock 对 App 收银台首次 POST 返回 `PAYING` 并读回处理中，页面留在收银台且保留原分区未决键，重置禁用；脚本 `[PASS]`，App 共用端 `125/125`、H5/PC 各 `4/4`、三端构建、小程序 `118/118`。仅验证本地 JS 与隔离 App 浏览器，不替代 H5/PC 真浏览器、后端真实受理、目标 MySQL 8、多实例、微信真机、真实资金或生产发布。

## 64. 2026-10-11 PAYING 操作提示与查单门禁一致性

隔离 Chrome/mock 首先复现 App 收银台 GET 读回 `PAYING` 时查单禁用但提示用户先查单。现在首次提交异常读回与重载页面都提示刷新收银台等待推进、勿重复付款；浏览器同时核查原键留存、重置与主动查单禁用、刷新可用、无错误跳转，脚本 `[PASS]`。App `125/125`、H5/PC 各 `4/4`、三端构建通过。此证据仅覆盖本地 mock 与共享页面代码，不证明 H5/PC 真浏览器、渠道实际受理、目标 MySQL 8、多实例恢复、真实资金或发布。

## 65. 2026-10-11 九服务支付请求真实状态投影与回调/查单收口

`cross-system-happy-path` 现沿真实本机支付提交和同键重放读取支付请求列表、总览、详情，核对同支付单恰好一条“处理中 / info”请求；回调或受控网关查单收口后，同一请求号成为“成功 / success”，处理中列表为空。断言单测 `64/64`；`local-stack.sh verify payment-chain` 与 `verify payment-query-chain` 均通过写前隔离门禁、九服务 HTTP 冒烟与 happy-path `[PASS]`，分别以 `CALLBACK` 和 `GATEWAY_QUERY` 收口，对账 `COMPLETED/4/4`、`financialCloseable=true`。退出码均为 `0`，九服务及本机查单上游停止、状态 `DOWN`。仅为 H2/demo、模拟渠道与上游，不证明目标 MySQL 8、多实例、正式资金、可信身份或发布。

## 66. 2026-10-11 当前基线九服务支付链复验

在 `eeb7ca5d` 基线上复核写前环境、PID 与端口门禁后，`node --test tools/dev/cross-system-happy-path.test.mjs` 为 `64/64`。分别执行 `bash tools/dev/local-stack.sh verify payment-chain` 和 `verify payment-query-chain`，两轮退出码均为 `0`，九服务 HTTP 冒烟与 happy-path 均 `[PASS]`。支付单 `PAY17917153983175e936489` 经 `CALLBACK` 收口，`PAY179171557591650eef12d` 经 `GATEWAY_QUERY` 收口；两轮对账均为 `COMPLETED/4/4` 且 `reconciliationFinancialCloseable=true`。脚本停服后九服务均 `DOWN`，查询链本机上游桩也已停止。该结果仅覆盖本机 H2/demo、模拟渠道与查单上游，不代表目标 MySQL 8、多实例、可信身份、真实资金或生产验收。

## 67. 2026-10-11 并发令牌接管修复后的跨系统双路径复验

在 `b17ef42b` 基线上，先确认本地九服务均 `DOWN`，`doctor payment-chain` 的 JDK 8、Maven 缓存和 Node 检查通过。`node --test tools/dev/cross-system-happy-path.test.mjs` 为 `64/64`；分别运行 `bash tools/dev/local-stack.sh verify payment-chain` 与 `verify payment-query-chain`，写前环境/PID/端口隔离门禁及九服务 HTTP 冒烟通过，脚本均以 `0` 退出。`PAY17917213307940c759bdd` 经 `CALLBACK` 收口，`PAY179172156338381a2d5af` 经本机 `GATEWAY_QUERY` 桩收口，事件、清分、结算、账务、退款和对账链路均 `[PASS]`；两轮对账均为 `COMPLETED/4/4`、`reconciliationFinancialCloseable=true`。退出后九服务状态再次确认为 `DOWN`，查询链桩亦已停。该验证只证明隔离 H2/demo 模拟渠道与本机上游在此基线未回退，不证明目标 MySQL 8 多实例锁序、真实渠道资金、可信身份或生产发布。

## 68. 2026-10-12 同键重放风控顺序修复后的支付链双路径复验

在 `a156f625` 基线上分别使用独立临时运行目录执行 `bash tools/dev/local-stack.sh verify payment-chain` 与 `verify payment-query-chain`，两轮均通过写前环境/PID/端口隔离门禁、九服务 HTTP 冒烟和 happy-path，退出码为 `0`。支付单 `PAY179175963019731fec661` 经 `CALLBACK` 收口，`PAY17917598148020c8cf727` 经本机 `GATEWAY_QUERY` 桩收口；支付成功事件、清分、结算、账务、退款及对账事实贯通，两轮均为 `COMPLETED/4/4`、`reconciliationFinancialCloseable=true`。脚本停止九服务及查询桩后，两个临时 PID 目录均为空，目标端口无监听。该结果只证明本机 H2/demo、模拟渠道和本机查单桩在此提交上未回退，不代表目标 MySQL 8 多实例竞态、真实资金渠道、可信身份、审批或生产发布验收。

## 69. 2026-10-13 AMQP 发布修复后本机 HTTP 双路径复验

在 `296d42db` 基线，`doctor payment-chain` 的 JDK 8、Maven 缓存和 Node 检查通过，九服务初始均为 `DOWN`。`node --test tools/dev/cross-system-happy-path.test.mjs` 为 `64/64`；隔离执行 `verify payment-chain` 与 `verify payment-query-chain` 均通过九服务 HTTP 冒烟和 happy-path，退出码为 `0`。支付单 `PAY1791847629110e7339754` 经 `CALLBACK` 收口，`PAY1791847823115b126b3f9` 经本机 `GATEWAY_QUERY` 收口；支付成功、清分、结算、账务、退款及七项对账事实贯通，均为 `COMPLETED/4/4` 且 `reconciliationFinancialCloseable=true`。脚本分别停服，复核九后端 `DOWN`、本机查单桩已停止且 18190 无监听。此证据只覆盖 H2/demo、HTTP 与模拟渠道，不验证 RabbitMQ 正/重试/死信、目标 MySQL 8 多实例、正式身份或真实资金。

## 70. 2026-10-13 回调验签与退款拒收变更后的九服务双路径复核

在 `4fbd0622` 基线上，写前检查确认九后端 `DOWN`，JDK 8、Maven 缓存、Node 就绪；`cross-system-happy-path.test.mjs` `64/64`。独立临时运行目录执行 `verify payment-chain` 与 `verify payment-query-chain`，两轮均以退出码 `0` 完成九服务 HTTP 冒烟与 happy-path `[PASS]`：`PAY179185058753410a6f50f` 经支付回调收口，`PAY1791850782131799f5452` 经本机网关主动查单收口；支付事件、清分、结算、账务、退款和对账均贯通，对账 `COMPLETED/4/4`、`reconciliationFinancialCloseable=true`。脚本停服后九后端均为 `DOWN`，查询桩 18190 无监听，两个临时 PID 目录无文件。仅证明当前 H2/demo、模拟渠道 HTTP 双路径未回退；不覆盖本轮拒收回调的正式渠道 ACK/重试、真实 RabbitMQ、目标 MySQL 8 多实例、可信身份或真实资金。

## 71. 2026-10-13 当前提交 H2/demo 双路径再验证

在 `e5a01e48` 基线上先确认九后端 `DOWN`，`doctor payment-chain` 通过，`cross-system-happy-path.test.mjs` `64/64`。首次沙箱运行九服务立即退出且日志为空，不作为运行态证据；允许本机回环后分别运行 `verify payment-chain` 与 `verify payment-query-chain`，两轮退出码 `0`、九服务 HTTP 冒烟和 happy-path `[PASS]`。支付单 `PAY179185297722731d4be88` 经 `CALLBACK`、`PAY1791853153950503bf507` 经 `GATEWAY_QUERY` 收口；两轮均有支付事件、清分、结算、三类账务事件、退款及七项对账事实，对账 `COMPLETED/4/4`、`financialCloseable=true`。退出后九后端均为 `DOWN`，18190 查询桩无监听。仅证明本机 H2/demo HTTP 模拟链路在当前提交可重复，不能替代真实 RabbitMQ、目标 MySQL 8 多实例、正式渠道 ACK/查单、可信身份及资金验收。
