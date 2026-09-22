# payment-core 统一门禁审计报告

- 生成时间：`2026-09-22T17:01:51.585Z`
- 是否允许推进 `test -> master`：`否`
- 是否允许创建 `release/*`：`否`
- 阻断项数量：`3`

## 汇总

| 项目 | 结果 |
| --- | --- |
| 回归证据包已收口 | `否` |
| 回归汇总已记录 `test -> master` 结论 | `是` |
| 回归汇总已记录 `release/*` 结论 | `是` |
| release 材料完整性 | `通过` |
| release 正式放行条件 | `未通过` |

## 阻断项

1. test 回归证据包仍未收口
2. 回归汇总当前仍未放行 `test -> master`
3. release 正式放行条件仍未闭环

## 备注

1. 回归汇总已显式记录当前不允许推进 `test -> master`，需先补齐 test 基线自身的冻结门禁。
2. 回归汇总已显式记录当前不允许创建 `release/*`，需待正式切版治理条件满足后再改判。
3. release 审计：当前材料骨架已齐，但正式放行条件仍未闭环。
4. release 审计：现有观察结论仍停留在 2026-08-09 当日口径，尚未补齐观察结束记录，因此不能提前形成正式切版结论。

## 输入

1. `docs/releases/evidence/test-20260809-regression`
2. `tools/release/audit_regression_pack.mjs`
3. `tools/release/audit_release_readiness.mjs`
