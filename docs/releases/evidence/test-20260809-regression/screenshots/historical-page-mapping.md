# 历史页面证据映射

## 说明

本文件用于把 `draft-test-20260808` 冻结证据包中的历史截图映射到当前回归场景。

这些截图证明页面形态、状态页和后台页面曾经完成过冻结归档，但它们不是 `2026-08-09` 同批次截图，因此只能标记为 `HISTORICAL`，不能直接作为当前放行依据。

## 映射关系

| 场景 | 历史截图路径 |
| --- | --- |
| REG-01 | `../../draft-test-20260808/screenshots/payment-core/app-web/recharge-entry.png` `../../draft-test-20260808/screenshots/payment-core/app-web/withdraw-entry.png` `../../draft-test-20260808/screenshots/payment-core/app-web/transfer-entry.png` |
| REG-02 | `../../draft-test-20260808/screenshots/payment-core/admin-web/orders.png` `../../draft-test-20260808/screenshots/payment-core/admin-web/payment-flows.png` |
| REG-03 | `../../draft-test-20260808/screenshots/payment-core/admin-web/payment-detail-success.png` `../../draft-test-20260808/screenshots/payment-core/app-web/result-success.png` |
| REG-04 | `../../draft-test-20260808/screenshots/clearing-system/admin-web/events.png` |
| REG-05 | `../../draft-test-20260808/screenshots/settlement-system/admin-web/orders.png` |
| REG-06 | `../../draft-test-20260808/screenshots/accounting-system/admin-web/events.png` |
| REG-08 | `../../draft-test-20260808/screenshots/payment-core/admin-web/dead-letter-pending-review.png` `../../draft-test-20260808/screenshots/payment-core/admin-web/dead-letter-ready-replay.png` `../../draft-test-20260808/screenshots/payment-core/admin-web/dead-letter-manual-resolved.png` `../../draft-test-20260808/screenshots/payment-core/admin-web/dead-letter-replayed.png` |
| REG-09 | `../../draft-test-20260808/screenshots/settlement-system/admin-web/orders.png` `../../draft-test-20260808/screenshots/settlement-system/admin-web/payouts.png` |

## 当前缺口

1. 这些截图缺少 `2026-08-09` 同批次业务编号绑定。
2. 缺少和当前回归包场景台账一一对应的最新截图归档。
3. 因此审计仍会继续阻断 `test -> master`。
