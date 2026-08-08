# draft-test-20260808 最终人工回归归档

- 基线日期：`2026-08-08`
- 归档状态：`TODO`

## 1. 基线信息

| 项目 | 记录 |
| --- | --- |
| Git 分支 | `test` |
| Git 提交号 | `d40adca` |
| 执行人 | `Codex` |
| 复核人 | `TODO` |
| 主链路样例单号 | `PAY1785918393972 / PRE1785918393974 / REG-AUTO-APP-20260805-001` |
| 风控样例单号 | `PAY1785811244732 / PRE1785811244734` |
| 关闭单样例单号 | `PAY1785729963046 / DRILL-AMQP-20260803A-001` |
| 补偿样例任务号 | `DLQa254a62bab904f0d9885 / DLQb151ee3a445c42229730 / DLQ5ef0a4f3bbf24129aef2 / DLQe9f4ca3e5625481eb952 / DLQa55e1d33dfa845aeb296` |

## 2. 页面归档核验

| 端/系统 | 页面 | 样例编号 | 状态/动作 | 截图文件 | 结果 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| payment-core/admin-web | 订单中心 | | 发起支付/联查 | | TODO | |
| payment-core/admin-web | 支付单详情 | `PAY1785915556767` | WAIT_CALLBACK | | TODO | 使用 WAIT_CALLBACK 样例 |
| payment-core/admin-web | 支付单详情 | `PAY1785811244732` | RISK_REVIEW | | TODO | 使用风控复核样例 |
| payment-core/admin-web | 支付单详情 | `PAY1785918393972` | SUCCESS | | TODO | 使用正式来源应用成功样例 |
| payment-core/admin-web | 支付单详情 | `PAY1785729963046` | CLOSED | | TODO | 使用历史已关闭样例 |
| payment-core/app-web | 收银台 | `PRE1785915556768` | WAIT_CALLBACK | | TODO | |
| payment-core/app-web | 支付结果页 | `PAY1785918393972` | SUCCESS | | TODO | |
| payment-core/app-web | 支付结果页 | `PAY1785729963046` | CLOSED | | TODO | |
| payment-core/app-web | 支付结果页 | `PAY1785811244732` | RISK_REVIEW | | TODO | |
| payment-core/h5-web | 收银台/结果页 | `PAY1785918388803 / PRE1785918388806 / PAY1785811244732 / PAY1785729963046` | WAIT_CALLBACK/RISK_REVIEW/SUCCESS/CLOSED | | TODO | WAIT_CALLBACK 需在成功回调前先采图 |
| payment-core/pc-web | 收银台/结果页 | `PAY1785918388713 / PRE1785918388717 / PAY1785811244732 / PAY1785729963046` | WAIT_CALLBACK/RISK_REVIEW/SUCCESS/CLOSED | | TODO | WAIT_CALLBACK 可优先使用 `PAY1785915556767` |
| clearing-system/admin-web | 清分事件页 | `PAY1785918393972 / CLO20003` | publish/replay | | TODO | |
| settlement-system/admin-web | 结算单/出款页 | `SLT20004 / PBT50001` | 清分承接/出款事实 | | TODO | |
| accounting-system/admin-web | 账务事件页 | `EVT50005~EVT50010` | PAYMENT_SUCCESS/CLEARING_GENERATED | | TODO | |

## 3. 页面 + 接口 + 后台 + 下游事实串联

| 链路 | 样例编号 | 页面事实 | 接口事实 | 后台事实 | 下游事实 | 结果 |
| --- | --- | --- | --- | --- | --- | --- |
| 正向支付成功 | `PAY1785918393972` | App 成功结果页、后台支付详情、支付事件列表 | `GET /api/payments/PAY1785918393972` | `payment-core admin-web /payments/PAY1785918393972` | `CLO20003 / SLT20004 / EVT50005~EVT50006` | TODO |
| 风控复核 | `PAY1785811244732` | H5 风控结果页、后台风险建议区 | `GET /api/payments/PAY1785811244732` | `payment-core admin-web /payments/PAY1785811244732` | `risk-control-system` 复核单如需补图再追加 | TODO |
| 关闭支付单 | `PAY1785729963046` | CLOSED 结果页、后台支付详情 | `GET /api/payments/PAY1785729963046` | `payment-core admin-web /payments/PAY1785729963046` | `无下游新增事实，应保持关闭态` | TODO |
| payment.success 补偿 | `DLQa254a62bab904f0d9885 / DLQb151ee3a445c42229730` | 死信任务页状态 | `GET /api/payment-dead-letter-tasks?messageId=MQ-DLQ-20260808-*` | `payment-core task center / dead-letter task` | `EVT50002` | TODO |
| clearing.generated 补偿 | `DLQ5ef0a4f3bbf24129aef2 / DLQe9f4ca3e5625481eb952 / DLQa55e1d33dfa845aeb296` | 死信任务页状态、下游事件页 | `GET /api/payment-dead-letter-tasks?messageId=MQ-CLG-DLQ-20260808-*` | `payment-core task center / settlement events / accounting events` | `SVE70002 / EVT50003` | TODO |

## 4. 剩余问题

1. 四端页面冻结截图仍未在当前批次统一归档，尚不能形成完整冻结包。
2. `PENDING_REVIEW / READY_TO_REPLAY` 中间态任务需要在现场保留样例后再补图。
3. 正式来源应用 H5/PC 的 `WAIT_CALLBACK` 截图需要在成功回调前抢拍，当前样例更多偏向成功后事实。

## 5. 放行建议

| 结论项 | 结果 |
| --- | --- |
| 是否允许推进 `test -> master` | `否` |
| 是否允许创建 `release/*` | `否` |
| 仍需补什么 | 页面冻结截图、一体化回归归档、正式发布候选说明 |
