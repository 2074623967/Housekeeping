# test-20260809-regression 回归证据包

- 基线日期：`2026-08-09`
- 生成时间：`2026-08-09 10:46:08 +0800`
- 生成脚本：`tools/release/init_regression_evidence_pack.sh`

## 目录说明

- `regression-scenarios.csv`：统一场景台账，串联 API、数据库、事件、页面和补偿/回滚证据
- `records/regression-summary.md`：本轮回归结论、阻断项和是否允许推进下一阶段
- `api/`：接口请求/响应样例或 curl 记录
- `database/`：数据库事实截图、SQL 输出或导出文件
- `events/`：事件台账、补偿任务、重试结果
- `screenshots/`：页面截图，按系统归档

## 使用要求

1. 所有证据必须来自当前 `test` 基线或明确记录的隔离环境。
2. 每个场景至少要回填 API、数据库事实、事件台账三类证据；涉及页面的场景还要补截图。
3. 所有场景统一使用 `REG-01 ~ REG-10` 编号，不允许新增临时口径编号。
4. 如果场景属于补偿、回放、回滚演练，必须在 `events/` 或 `database/` 中保留执行前后对比证据。
5. 完成回归后，先运行 `tools/release/audit_regression_pack.mjs`，再决定是否更新发布结论。
