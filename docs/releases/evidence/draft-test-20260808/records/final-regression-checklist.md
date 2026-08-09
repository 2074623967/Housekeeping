# draft-test-20260808 最终人工回归归档

- 基线日期：`2026-08-08`
- 归档状态：`PARTIAL_PASS`

## 1. 基线信息

| 项目 | 记录 |
| --- | --- |
| Git 分支 | `test` |
| Git 提交号 | `b90f8a0` |
| 执行人 | `Codex` |
| 复核人 | `待人工最终复核` |
| 主链路样例单号 | `PAY1785918393972 / PRE1785918393974 / REG-AUTO-APP-20260805-001` |
| 风控样例单号 | `PAY1785811244732 / PRE1785811244734` |
| 关闭单样例单号 | `PAY1785729963046 / DRILL-AMQP-20260803A-001` |
| 补偿样例任务号 | `DLQa254a62bab904f0d9885 / DLQb151ee3a445c42229730 / DLQ5ef0a4f3bbf24129aef2 / DLQe9f4ca3e5625481eb952 / DLQa55e1d33dfa845aeb296` |

## 2. 页面归档核验

| 端/系统 | 页面 | 样例编号 | 状态/动作 | 截图文件 | 结果 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| payment-core/admin-web | 订单中心 | | 发起支付/联查 | `screenshots/payment-core/admin-web/orders.png` | PASS | 页面截图已归档 |
| payment-core/admin-web | 支付单详情 | `PAY1785915556767` | WAIT_CALLBACK | `screenshots/payment-core/admin-web/payment-detail-wait-callback.png` | PASS | 使用 WAIT_CALLBACK 样例 |
| payment-core/admin-web | 支付单详情 | `PAY1785811244732` | RISK_REVIEW | `screenshots/payment-core/admin-web/payment-detail-risk-review.png` | PASS | 使用风控复核样例 |
| payment-core/admin-web | 支付单详情 | `PAY1785918393972` | SUCCESS | `screenshots/payment-core/admin-web/payment-detail-success.png` | PASS | 使用正式来源应用成功样例 |
| payment-core/admin-web | 支付单详情 | `PAY1785729963046` | CLOSED | `screenshots/payment-core/admin-web/payment-detail-closed.png` | PASS | 使用历史已关闭样例 |
| payment-core/app-web | 收银台 | `PRE1785915556768` | WAIT_CALLBACK | `screenshots/payment-core/app-web/cashier-wait-callback.png` | PASS | 收银台 WAIT_CALLBACK 截图已归档 |
| payment-core/app-web | 支付结果页 | `PAY1785918393972` | SUCCESS | `screenshots/payment-core/app-web/result-success.png` | PASS | 成功结果页截图已归档 |
| payment-core/app-web | 支付结果页 | `PAY1785729963046` | CLOSED | `screenshots/payment-core/app-web/result-closed.png` | PASS | 关闭结果页截图已归档 |
| payment-core/app-web | 支付结果页 | `PAY1785811244732` | RISK_REVIEW | `screenshots/payment-core/app-web/result-risk-review.png` | PASS | 风控复核结果页截图已归档 |
| payment-core/h5-web | 收银台/结果页 | `PAY1785918388803 / PRE1785918388806 / PAY1785811244732 / PAY1785729963046` | WAIT_CALLBACK/RISK_REVIEW/SUCCESS/CLOSED | `screenshots/payment-core/h5-web/cashier-wait-callback.png` `screenshots/payment-core/h5-web/result-success.png` `screenshots/payment-core/h5-web/result-risk-review.png` `screenshots/payment-core/h5-web/result-closed.png` | PASS | H5 关键状态截图已归档 |
| payment-core/pc-web | 收银台/结果页 | `PAY1785918388713 / PRE1785918388717 / PAY1785811244732 / PAY1785729963046` | WAIT_CALLBACK/RISK_REVIEW/SUCCESS/CLOSED | `screenshots/payment-core/pc-web/cashier-wait-callback.png` `screenshots/payment-core/pc-web/result-success.png` `screenshots/payment-core/pc-web/result-risk-review.png` `screenshots/payment-core/pc-web/result-closed.png` | PASS | PC 关键状态截图已归档 |
| clearing-system/admin-web | 清分事件页 | `PAY1785918393972 / CLO20003` | publish/replay | `screenshots/clearing-system/admin-web/events.png` | PASS | 清分事件页截图已归档 |
| settlement-system/admin-web | 结算单/出款页 | `SLT20004 / PBT50001` | 清分承接/出款事实 | `screenshots/settlement-system/admin-web/orders.png` `screenshots/settlement-system/admin-web/payouts.png` | PASS | 结算单与出款页截图已归档 |
| accounting-system/admin-web | 账务事件页 | `EVT50005~EVT50010` | PAYMENT_SUCCESS/CLEARING_GENERATED | `screenshots/accounting-system/admin-web/events.png` | PASS | 账务事件页截图已归档 |

## 3. 页面 + 接口 + 后台 + 下游事实串联

| 链路 | 样例编号 | 页面事实 | 接口事实 | 后台事实 | 下游事实 | 结果 |
| --- | --- | --- | --- | --- | --- | --- |
| 正向支付成功 | `PAY1785918393972` | App 成功结果页、后台支付详情、支付事件列表截图均已归档 | `GET /api/payments/PAY1785918393972` 已存在既有运行事实 | `payment-core admin-web /payments/PAY1785918393972` 截图已归档 | `CLO20003 / SLT20004 / EVT50005~EVT50006` 已在既有联调事实中收口 | PASS |
| 风控复核 | `PAY1785811244732` | H5 风控结果页、后台风险建议区截图已归档 | `GET /api/payments/PAY1785811244732` 已存在既有运行事实 | `payment-core admin-web /payments/PAY1785811244732` 截图已归档 | `risk-control-system` 复核单本轮未补图，但风控状态页面已归档 | PARTIAL_PASS |
| 关闭支付单 | `PAY1785729963046` | CLOSED 结果页、后台支付详情截图已归档 | `GET /api/payments/PAY1785729963046` 已存在既有运行事实 | `payment-core admin-web /payments/PAY1785729963046` 截图已归档 | `无下游新增事实，应保持关闭态` | PASS |
| payment.success 补偿 | `DLQa254a62bab904f0d9885 / DLQb151ee3a445c42229730` | 死信任务页截图已归档 | `GET /api/payment-dead-letter-tasks?messageId=MQ-DLQ-20260808-*` 已在补偿记录中验证 | `payment-core task center / dead-letter task` 页面截图已归档 | `EVT50002` 已在补偿闭环记录中验证 | PASS |
| clearing.generated 补偿 | `DLQ5ef0a4f3bbf24129aef2 / DLQe9f4ca3e5625481eb952 / DLQa55e1d33dfa845aeb296` | 死信任务页、清分/结算/账务事件截图已归档 | `GET /api/payment-dead-letter-tasks?messageId=MQ-CLG-DLQ-20260808-*` 已在补偿记录中验证 | `payment-core task center / settlement events / accounting events` 页面截图已归档 | `SVE70002 / EVT50003` 已在补偿闭环记录中验证 | PASS |

## 4. 剩余问题

1. 四端与下游后台页面截图已完成统一归档，当前主要剩余项转为人工最终复核和正式发布候选说明收口。
2. `PENDING_REVIEW / READY_TO_REPLAY` 当前截图来自任务中心当前页，不是专门保留的中间态任务样例，若要做更严格审计仍建议后续补一轮专门样例。
3. 风控复核链路当前已具备页面和后台截图，但若要形成更强的跨系统最终放行包，仍建议补 `risk-control-system` 复核单页面截图。

## 5. 放行建议

| 结论项 | 结果 |
| --- | --- |
| 是否允许推进 `test -> master` | `是` |
| 是否允许创建 `release/*` | `否` |
| 仍需补什么 | 正式发布候选说明从草案升级、`master` 稳定观察、必要时补风控系统复核单截图 |
