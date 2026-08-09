# test-20260809-regression 回归汇总

- 基线日期：`2026-08-09`
- 归档状态：`TODO`

## 1. 基线信息

| 项目 | 记录 |
| --- | --- |
| Git 分支 | `test` |
| Git 提交号 | |
| 环境类型 | |
| 执行人 | |
| 复核人 | |

## 2. 场景结论

| 场景编号 | 业务场景 | API | 数据库 | 事件 | 页面 | 补偿/回滚 | 结论 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| REG-01 | 创建预付单 | PASS | PASS | N/A | PASS | PENDING_EVIDENCE | 部分完成 | 当前 test 基线预付入口截图已生成，缺失败回滚证据 |
| REG-02 | 提交支付请求 | PASS | PASS | N/A | PASS | PENDING_EVIDENCE | 部分完成 | 当前 test 基线支付流水页截图已生成，缺提交失败回滚证据 |
| REG-03 | 渠道回调成功 | PASS | PASS | PASS | PASS | PENDING_EVIDENCE | 部分完成 | 当前 test 基线支付结果页截图已生成，缺重复回调回滚证据 |
| REG-04 | 支付成功自动清分 | PASS | PASS | PASS | PASS | PASS | 已完成 | 当前 test 基线已完成 payment.success -> clearing 当前批次 replay，并生成清分事件 `EVT60002` |
| REG-05 | 清分结果自动结算 | PASS | PASS | PASS | PASS | PASS | 已完成 | 当前 test 基线已完成 clearing.generated -> settlement 当前批次 replay，并生成结算事件 `SVE70003` |
| REG-06 | 清分结果自动记账 | PASS | PASS | PASS | PASS | PASS | 已完成 | 当前 test 基线已完成 replay 后下游补事实收口，并生成账务事件 `EVT50012` |
| REG-07 | 跨系统链路诊断 | PASS | PASS | PASS | OPTIONAL | N/A | 已完成 | 当前 test 基线已具备代码与自动化闭环 |
| REG-08 | 失败派发重试 / DLQ | PASS | PASS | PASS | PASS | PASS | 已完成代码与页面证据 | 当前 test 基线死信任务页截图已生成，补偿业务批次仍需同批次复核 |
| REG-09 | 结算审核与出款 | PASS | PASS | PASS | PASS | PASS | 已完成 | 当前 test 基线已完成 `SLT20004 -> PBT50001` 的审核、出款失败与人工重试成功 |
| REG-10 | 日终处理与运营巡检 | PASS | PASS | PASS | PASS | PASS | 已完成 | 当前 test 运行态已完成 REC-DEMO-1001 差异识别、人工结案和工作台回写 |

## 3. 阻断项

1. `REG-01 ~ REG-03` 仍缺最新同批次补偿/回滚证据。

## 4. 放行结论

| 结论项 | 结果 |
| --- | --- |
| 是否允许推进 `test -> master` | |
| 是否允许创建 `release/*` | |
| 仍需补什么 | |
