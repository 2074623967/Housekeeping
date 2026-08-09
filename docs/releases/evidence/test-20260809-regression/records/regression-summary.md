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
| REG-01 | 创建预付单 | PASS | PASS | N/A | TODO | TODO | 部分完成 | 历史 smoke 已证明预付单创建成功，缺最新页面与失败回滚证据 |
| REG-02 | 提交支付请求 | PASS | PASS | N/A | TODO | TODO | 部分完成 | 已有历史提交与路由命中证据 |
| REG-03 | 渠道回调成功 | PASS | PASS | PASS | TODO | TODO | 部分完成 | 已有历史回调收口与出站事件证据 |
| REG-04 | 支付成功自动清分 | PASS | PASS | PASS | TODO | TODO | 部分完成 | 已有历史清分承接证据 |
| REG-05 | 清分结果自动结算 | PASS | PASS | PASS | TODO | TODO | 部分完成 | 已有历史结算承接证据 |
| REG-06 | 清分结果自动记账 | PASS | PASS | PASS | TODO | TODO | 部分完成 | 已有历史账务承接证据 |
| REG-07 | 跨系统链路诊断 | PASS | PASS | PASS | OPTIONAL | N/A | 已完成 | 当前 test 基线已具备代码与自动化闭环 |
| REG-08 | 失败派发重试 / DLQ | PASS | PASS | PASS | TODO | PASS | 部分完成 | 历史补偿闭环已存在，缺最新同批次页面与任务快照 |
| REG-09 | 结算审核与出款 | PASS | PASS | PASS | TODO | TODO | 部分完成 | 已有历史审核、出款与回单证据 |
| REG-10 | 日终处理与运营巡检 | PASS | TODO | TODO | TODO | TODO | 部分完成 | 当前仅有自动化测试覆盖证据 |

## 3. 阻断项

1. `REG-01 ~ REG-06 / REG-09` 仍缺最新同批次页面冻结与页面矩阵映射。
2. `REG-08` 仍缺最新同批次 `DLQ -> task -> page snapshot -> downstream fact` 回归证据。
3. `REG-10` 当前仅有自动化测试覆盖，缺真实日终业务批次证据。

## 4. 放行结论

| 结论项 | 结果 |
| --- | --- |
| 是否允许推进 `test -> master` | |
| 是否允许创建 `release/*` | |
| 仍需补什么 | |
