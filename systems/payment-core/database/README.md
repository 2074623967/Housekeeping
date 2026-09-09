# payment-core database

当前已补齐：

- `schema.sql`：支付交易、配置治理、异常治理、任务中心、退款、账单与运营台账建表脚本
- `data.sql`：支付渠道、路由规则、协议、返回码、网关、控制策略、异常值班、支付单与退款演示数据

## 应用启动初始化

- 默认启动不会执行 `schema.sql` 或 `data.sql`，避免支付订单、outbox 和对账事实被应用重启覆盖。
- 首次本地演示可显式使用 `SPRING_PROFILES_ACTIVE=demo`，或一次性设置 `HSP_SQL_INIT_MODE=always` 重建样例库。
- 已有演练库、测试库和任何持久环境应保持 `HSP_SQL_INIT_MODE=never`，结构变更通过受控 DDL migration 执行。
- `migrations/2026-07-31-add-payment-task-lease.sql`：给原始 `housekeeping_payment_core` 运行库补齐 `t_payment_task_lease` 的最小增量 DDL，用于让最新任务中心租约锁能力安全落库
- `migrations/2026-08-09-add-ops-config-snapshot-version.sql`：给支付尝试和路由记录补齐运营配置快照版本字段，支持支付事实按配置版本追溯
- `migrations/2026-07-31-ack-legacy-recursive-issue-alerts.sql`：把历史递归升级 outbox 告警收口为 `已确认`，保留审计记录但移出待确认/待升级范围
- `migrations/2026-09-08-add-payment-route-rule-version.sql`：幂等创建路由规则版本快照表，并为尚无版本记录的存量规则补录 V1 基线
- `migrations/2026-09-09-add-payment-route-rule-rollout.sql`：为运行规则、版本和复核候选补齐稳定百分比灰度字段，存量数据默认保持 `100% / stable-v1`
- `migrations/2026-09-09-add-route-rule-review-source-version.sql`：为路由规则回滚候选记录目标历史版本号，须在复核表创建后执行
- `migrations/2026-09-09-add-payment-protocol-version.sql`：幂等创建支付协议不可变版本表，并为尚无版本记录的存量协议补录 V1 基线
- `migrations/2026-09-09-add-payment-protocol-review.sql`：幂等创建支付协议复核申请表，以唯一待办占位约束同一协议最多一条待复核申请
- `migrations/2026-09-09-add-payment-return-code-snapshot.sql`：为支付单补齐最近一次渠道返回码标准化快照字段，使提交或查单后的详情可稳定追溯；脚本非幂等，只执行一次
- `migrations/2026-09-09-add-payment-return-code-import-batch.sql`：幂等创建返回码 CSV 导入审计批次表，记录自声明操作人、原因、源文件摘要、应用前后指纹和差异数量
- `migrations/2026-09-09-add-payment-return-code-import-review.sql`：幂等创建返回码 CSV 导入复核申请表，以可空唯一待办占位限制同时最多一条待复核导入候选
- `migrations/2026-09-09-add-payment-return-code-review.sql`：幂等创建单条返回码状态变更复核表，以规范化映射键摘要和可空唯一待办占位限制同一映射最多一条待复核申请
- `migrations/2026-09-09-add-payment-channel-review.sql`：幂等创建支付渠道状态变更复核表，以可空唯一渠道占位限制同一渠道最多一条待复核申请
- `migrations/2026-09-09-expand-payment-channel-review.sql`：一次性扩展渠道复核表，增加新增/编辑操作类型与完整候选快照，并回填既有状态申请

初始化方式：

1. 先执行 `schema.sql`
2. 再执行 `data.sql`
3. 默认数据库名为 `housekeeping_payment_core`

增量迁移方式：

1. 若原始运行库早于 `2026-07-29` 任务中心租约锁版本，先执行 `migrations/2026-07-31-add-payment-task-lease.sql`
2. 若原始运行库存在历史递归升级告警，再执行 `migrations/2026-07-31-ack-legacy-recursive-issue-alerts.sql`
3. 若运行库尚未建立路由规则版本治理能力，执行 `migrations/2026-09-08-add-payment-route-rule-version.sql`；脚本可重复执行，只为尚无任何版本记录的存量规则补录 V1 `BASELINE`
4. 执行 `migrations/2026-09-09-add-payment-protocol-version.sql`；脚本可重复执行，只为尚无任何版本记录的存量协议补录 V1 `BASELINE`
5. 协议版本表建立后执行 `migrations/2026-09-09-add-payment-protocol-review.sql`；脚本可重复执行，建立协议候选复核与唯一待办占位
6. 在路由规则版本和复核表均已建立后，执行 `migrations/2026-09-09-add-payment-route-rule-rollout.sql`；该 DDL 不是可重复脚本，需由迁移工具记录并确保只执行一次
7. 执行 `migrations/2026-09-09-add-route-rule-review-source-version.sql`，为回滚候选补齐来源版本；该 DDL 不是可重复脚本，需由迁移工具记录并确保只执行一次
8. 执行 `migrations/2026-09-09-add-payment-return-code-snapshot.sql`，为支付单补齐返回码快照；该 DDL 不是可重复脚本，需由迁移工具记录并确保只执行一次，历史支付单字段保持 `NULL`
9. 执行 `migrations/2026-09-09-add-payment-return-code-import-batch.sql`，建立返回码批量导入审计批次表；脚本可重复执行
10. 执行 `migrations/2026-09-09-add-payment-return-code-import-review.sql`，建立返回码导入候选复核表；脚本可重复执行
11. 执行 `migrations/2026-09-09-add-payment-return-code-review.sql`，建立单条返回码状态变更复核表；脚本可重复执行
12. 执行 `migrations/2026-09-09-add-payment-channel-review.sql`，建立支付渠道状态变更复核表；脚本可重复执行
13. 执行一次 `migrations/2026-09-09-expand-payment-channel-review.sql`，扩展渠道完整候选字段；该脚本不可重复执行
14. 增量脚本不替代全量 `schema.sql`，执行完成后应至少验证：
   - `SHOW TABLES LIKE 't_payment_task_lease'`
   - `source_alert_no IS NULL AND alert_content LIKE '升级来源告警 %' AND ack_status = '待确认'` 的历史递归告警数已清零
   - `SHOW TABLES LIKE 't_payment_route_rule_version'`
   - `SELECT COUNT(*) FROM t_payment_route_rule_config config WHERE NOT EXISTS (SELECT 1 FROM t_payment_route_rule_version version WHERE version.rule_code = config.rule_code)` 返回 `0`
   - `SELECT rule_code, version_no, COUNT(*) FROM t_payment_route_rule_version GROUP BY rule_code, version_no HAVING COUNT(*) > 1` 返回空集
   - 三张路由规则表的 `rollout_percentage / rollout_salt` 均非空，存量规则为 `100 / stable-v1`
   - `t_payment_route_rule_review.source_version_no` 已存在，非回滚申请可为空
   - `SHOW TABLES LIKE 't_payment_protocol_version'`
   - `SHOW TABLES LIKE 't_payment_protocol_review'`
   - `SELECT COUNT(*) FROM t_payment_protocol_config config WHERE NOT EXISTS (SELECT 1 FROM t_payment_protocol_version version WHERE version.protocol_code = config.protocol_code)` 返回 `0`
   - `SELECT protocol_code, version_no, COUNT(*) FROM t_payment_protocol_version GROUP BY protocol_code, version_no HAVING COUNT(*) > 1` 返回空集
   - `SELECT protocol_code, COUNT(*) FROM t_payment_protocol_review WHERE review_status = 'PENDING_REVIEW' GROUP BY protocol_code HAVING COUNT(*) > 1` 返回空集
   - `SHOW COLUMNS FROM t_payment_order LIKE 'last_channel_return_code'` 与 `SHOW COLUMNS FROM t_payment_order LIKE 'last_mapping_matched'` 均返回一行
   - 迁移前存量支付单的 `last_channel_return_code / last_standardized_code / last_mapping_matched` 均可为 `NULL`，不得批量伪造历史映射结果
   - `SHOW TABLES LIKE 't_payment_return_code_import_batch'` 返回一行
   - `SELECT batch_no, source_sha256, baseline_fingerprint, result_fingerprint, applied_count FROM t_payment_return_code_import_batch ORDER BY created_at DESC LIMIT 10` 可读取导入审计批次；操作人字段仅为自声明，不得解释为正式审批或可信身份
   - `SHOW TABLES LIKE 't_payment_return_code_import_review'` 返回一行
   - `SELECT pending_import_key, COUNT(*) FROM t_payment_return_code_import_review WHERE pending_import_key IS NOT NULL GROUP BY pending_import_key HAVING COUNT(*) > 1` 返回空集；已完成申请的 `pending_import_key` 应为空
   - `SHOW TABLES LIKE 't_payment_return_code_review'` 返回一行
   - `SELECT pending_mapping_key, COUNT(*) FROM t_payment_return_code_review WHERE pending_mapping_key IS NOT NULL GROUP BY pending_mapping_key HAVING COUNT(*) > 1` 返回空集；已完成申请的 `pending_mapping_key` 应为空
   - `SHOW TABLES LIKE 't_payment_channel_review'` 返回一行
   - `SELECT pending_channel_code, COUNT(*) FROM t_payment_channel_review WHERE pending_channel_code IS NOT NULL GROUP BY pending_channel_code HAVING COUNT(*) > 1` 返回空集；已完成申请的 `pending_channel_code` 应为空
   - 复核表中的申请人和复核人均为自声明，不得解释为可信身份、正式审批或不可抵赖签字
   - 最新 backend 连接原始运行库后，不再因为 `t_payment_task_lease` 缺失或 `PAYMENT_ISSUE_ESCALATE` 告警内容越界导致调度任务报错

后续继续补强：

- 增量 DDL 版本脚本
- 大表归档与分区策略
- 发布期数据回滚脚本
