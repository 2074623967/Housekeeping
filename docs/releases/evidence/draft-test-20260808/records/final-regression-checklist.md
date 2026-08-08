# draft-test-20260808 最终人工回归归档

- 基线日期：`2026-08-08`
- 归档状态：`TODO`

## 1. 基线信息

| 项目 | 记录 |
| --- | --- |
| Git 分支 | `test` |
| Git 提交号 | |
| 执行人 | |
| 复核人 | |
| 主链路样例单号 | |
| 风控样例单号 | |
| 关闭单样例单号 | |
| 补偿样例任务号 | |

## 2. 页面归档核验

| 端/系统 | 页面 | 样例编号 | 状态/动作 | 截图文件 | 结果 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| payment-core/admin-web | 订单中心 | | 发起支付/联查 | | TODO | |
| payment-core/admin-web | 支付单详情 | | WAIT_CALLBACK | | TODO | |
| payment-core/admin-web | 支付单详情 | | RISK_REVIEW | | TODO | |
| payment-core/admin-web | 支付单详情 | | SUCCESS | | TODO | |
| payment-core/admin-web | 支付单详情 | | CLOSED | | TODO | |
| payment-core/app-web | 收银台 | | WAIT_CALLBACK | | TODO | |
| payment-core/app-web | 支付结果页 | | SUCCESS | | TODO | |
| payment-core/app-web | 支付结果页 | | CLOSED | | TODO | |
| payment-core/app-web | 支付结果页 | | RISK_REVIEW | | TODO | |
| payment-core/h5-web | 收银台/结果页 | | WAIT_CALLBACK/RISK_REVIEW/SUCCESS/CLOSED | | TODO | |
| payment-core/pc-web | 收银台/结果页 | | WAIT_CALLBACK/RISK_REVIEW/SUCCESS/CLOSED | | TODO | |
| clearing-system/admin-web | 清分事件页 | | publish/replay | | TODO | |
| settlement-system/admin-web | 结算单/出款页 | | 清分承接/出款事实 | | TODO | |
| accounting-system/admin-web | 账务事件页 | | PAYMENT_SUCCESS/CLEARING_GENERATED | | TODO | |

## 3. 页面 + 接口 + 后台 + 下游事实串联

| 链路 | 样例编号 | 页面事实 | 接口事实 | 后台事实 | 下游事实 | 结果 |
| --- | --- | --- | --- | --- | --- | --- |
| 正向支付成功 | | | | | | TODO |
| 风控复核 | | | | | | TODO |
| 关闭支付单 | | | | | | TODO |
| payment.success 补偿 | | | | | | TODO |
| clearing.generated 补偿 | | | | | | TODO |

## 4. 剩余问题

1. 
2. 
3. 

## 5. 放行建议

| 结论项 | 结果 |
| --- | --- |
| 是否允许推进 `test -> master` | |
| 是否允许创建 `release/*` | |
| 仍需补什么 | |
