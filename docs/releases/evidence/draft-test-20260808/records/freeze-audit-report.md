# 冻结候选自动审计报告

- 生成时间：`2026-08-08T01:43:33.033Z`
- 证据包：`docs/releases/evidence/draft-test-20260808`
- 是否允许推进 `test -> master`：`否`
- 是否允许创建 `release/*`：`否`

## 汇总

| 项目 | 结果 |
| --- | --- |
| 截图总数 | `50` |
| 已归档截图数 | `0` |
| 未归档截图数 | `50` |
| 人工回归 TODO 数 | `19` |
| 发布说明是否仍为草案 | `是` |
| 阻断项数量 | `3` |

## 阻断项

1. 截图台账仍有 50 项未归档完成
2. 人工回归归档中仍有 19 个 TODO 待回填
3. 发布说明仍处于草案状态

## 重点待补页面

| 系统 | 终端 | 页面 | 状态 | 样例编号 |
| --- | --- | --- | --- | --- |
| payment-core | admin-web | dashboard | TODO |  |
| payment-core | admin-web | orders | TODO |  |
| payment-core | admin-web | payment-list | TODO |  |
| payment-core | admin-web | payment-detail-wait-callback | TODO | PAY1785915556767 |
| payment-core | admin-web | payment-detail-risk-review | TODO | PAY1785811244732 |
| payment-core | admin-web | payment-detail-success | TODO | PAY1785918393972 |
| payment-core | admin-web | payment-detail-closed | TODO | PAY1785729963046 |
| payment-core | admin-web | payment-flows | TODO |  |
| payment-core | admin-web | payment-events | TODO |  |
| payment-core | admin-web | payment-issues | TODO |  |
| payment-core | admin-web | cashier-sessions | TODO |  |
| payment-core | admin-web | dead-letter-pending-review | TODO |  |
| payment-core | admin-web | dead-letter-ready-replay | TODO |  |
| payment-core | admin-web | dead-letter-manual-resolved | TODO | DLQa254a62bab904f0d9885 |
| payment-core | admin-web | dead-letter-replayed | TODO | DLQb151ee3a445c42229730 |
| payment-core | app-web | recharge-entry | TODO |  |
| payment-core | app-web | withdraw-entry | TODO |  |
| payment-core | app-web | transfer-entry | TODO |  |
| payment-core | app-web | balance-pay-entry | TODO |  |
| payment-core | app-web | cashier-prepay-created | TODO | PRE1785915100361 |

## 输入文件

1. `docs/releases/evidence/draft-test-20260808/screenshot-manifest.csv`
2. `docs/releases/evidence/draft-test-20260808/records/final-regression-checklist.md`
3. `docs/releases/2026-08-05-支付系统发布说明草案.md`

