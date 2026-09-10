# 第一阶段B-支付交易闭环数据库设计

## 1. 文档定位

虽然文件名保留“第一阶段B”，但本文档内容已经作为 `payment-core` 一期支付交易闭环 V1 的冻结版数据库设计说明使用。

目标：

1. 解释支付主链路核心对象关系
2. 说明当前 `schema.sql` 的真实落地范围
3. 为后续账务、清分、结算系统提供上游对象基线

## 2. 数据对象范围

本次聚焦支付核心域，不覆盖：

1. 账务分录
2. 清分结果
3. 清算出款
4. 对账差异
5. 保证金扣罚

本次核心对象包括：

1. `t_order`
2. `t_bill`
3. `t_prepay_order`
4. `t_payment_order`
5. `t_payment_attempt`
6. `t_payment_notify_log`
7. `t_payment_route_record`
8. `t_payment_event`
9. `t_payment_issue_action_log`
10. `t_payment_issue_alert_log`
11. `t_payment_channel_query_rate_bucket`
12. `t_payment_channel_query_rate_limit_log`
13. `t_refund_order`
14. `t_refund_channel_query_rate_bucket`
15. `t_refund_channel_query_rate_limit_log`
16. `t_payment_issue_alert_receipt_query_rate_bucket`
17. `t_payment_issue_alert_receipt_query_rate_limit_log`

## 3. 对象关系

```text
t_order
  -> t_bill
    -> t_prepay_order
      -> t_payment_order
        -> t_payment_attempt
        -> t_payment_route_record
        -> t_payment_notify_log
        -> t_payment_event
        -> t_payment_issue_action_log
        -> t_payment_issue_alert_log
        -> t_refund_order
```

关系说明：

1. 一笔订单在一期场景下至少对应一张账单。
2. 一张账单可创建一个预付单。
3. 一个预付单关联一个支付单。
4. 一个支付单可有多次尝试、多条回调、多条事件。
5. 异常处理动作和告警通知日志不改变支付单状态，只沉淀运营排障、告警通知和确认回执证据。
6. 渠道查单限流桶是按渠道共享的运行控制对象，不与单笔支付单建立外键关系；所有实例通过渠道唯一键竞争同一固定时间窗配额。
7. 一个成功支付单可关联多笔退款单；退款单独立保存退款金额、状态和渠道受理事实，不改写原支付成功事实。
8. 告警回执查询限流桶按通知通道共享，不与单条告警建立外键；拒绝日志仅用告警号关联内部回查任务，不保存供应商响应。

## 4. 核心表设计

### 4.1 订单表 `t_order`

作用：沉淀支付域读取的订单基础信息。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `order_no` | 订单号 |
| `customer_name` | 客户名称 |
| `service_type` | 服务品类 |
| `worker_name` | 服务者 |
| `order_amount` | 订单应收金额 |
| `paid_amount` | 订单已支付金额 |
| `order_status` | 订单状态 |
| `fulfillment_status` | 履约状态 |

### 4.2 账单表 `t_bill`

作用：作为订单与支付之间的金额桥梁。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `bill_no` | 账单号 |
| `order_no` | 关联订单号 |
| `customer_name` | 客户名称 |
| `bill_amount` | 账单应收金额 |
| `paid_amount` | 账单已支付金额 |
| `bill_status` | 账单状态 |
| `due_at` | 到期时间 |

### 4.3 预付单表 `t_prepay_order`

作用：作为收银台直接读取的统一支付入口对象。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `prepay_order_no` | 预付单号 |
| `bill_no` | 关联账单号 |
| `order_no` | 关联订单号 |
| `amount` | 预付金额 |
| `pay_scene` | 支付场景 |
| `cashier_title` | 收银台标题 |
| `cashier_status` | 收银台状态 |
| `payment_order_id` | 关联支付单号 |
| `expires_at` | 失效时间 |

### 4.4 支付单表 `t_payment_order`

作用：后台运营与状态追踪主对象。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `payment_order_id` | 支付单号 |
| `order_no` | 订单号 |
| `amount` | 本次支付金额 |
| `payment_method` | 支付方式 |
| `channel_code` | 渠道编码 |
| `channel_transaction_no` | 渠道流水号 |

### 4.5 已落地索引

1. `t_payment_order`
   - `payment_order_id` 唯一索引
   - `order_no` 索引
   - `status, created_at` 组合索引
   - `channel_code, created_at` 组合索引
2. `t_prepay_order`
   - `prepay_order_no` 唯一索引
   - `expires_at, payment_order_id` 组合索引
   - `order_no, created_at` 组合索引
3. `t_payment_attempt`
   - `attempt_no` 唯一索引
   - `payment_order_id, created_at` 组合索引
4. `t_payment_notify_log`
   - `notify_no` 唯一索引
   - `payment_order_id, created_at` 组合索引
   - `notify_status, created_at` 组合索引，支持查单事实窗口统计与失败趋势聚合
5. `t_payment_route_record`
   - `route_no` 唯一索引
   - `payment_order_id, created_at` 组合索引
6. `t_payment_event`
   - `event_no` 唯一索引
   - `payment_order_id, created_at` 组合索引
   - `biz_no, created_at` 组合索引
| `status` | 支付状态 |

### 4.5 支付尝试表 `t_payment_attempt`

作用：沉淀每次支付提交的请求和响应轨迹。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `attempt_no` | 支付尝试号 |
| `prepay_order_no` | 预付单号 |
| `payment_order_id` | 支付单号 |
| `channel_code` | 渠道编码 |
| `payment_method` | 支付方式 |
| `request_payload` | 请求报文 |
| `response_payload` | 响应报文 |
| `attempt_status` | 尝试状态 |
| `ops_config_snapshot_version` | 本次支付尝试消费的运营配置快照版本 |

### 4.6 回调日志表 `t_payment_notify_log`

作用：沉淀异步回调轨迹与收口结果。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `notify_no` | 回调日志号 |
| `payment_order_id` | 支付单号 |
| `channel_code` | 渠道编码 |
| `notify_type` | 回调类型 |
| `notify_payload` | 回调报文 |
| `notify_result` | 处理结果报文 |
| `notify_status` | 回调处理状态 |

### 4.7 路由记录表 `t_payment_route_record`

作用：沉淀支付渠道选择过程。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `route_no` | 路由记录号 |
| `payment_order_id` | 支付单号 |
| `channel_code` | 渠道编码 |
| `route_rule` | 路由规则 |
| `route_result` | 路由结果 |
| `ops_config_snapshot_version` | 本次路由决策消费的运营配置快照版本 |

### 4.8 支付事件表 `t_payment_event`

作用：沉淀支付主链路关键事件，为后续账务和清结算消费做准备。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `event_no` | 事件号 |
| `event_type` | 事件类型 |
| `payment_order_id` | 支付单号 |
| `biz_no` | 业务单号 |
| `event_payload` | 事件内容 |

### 4.9 支付交易异常处理动作日志表 `t_payment_issue_action_log`

作用：沉淀运营在异常中心执行的分派、跟进、已处理和备注动作，避免把人工排障动作混入支付交易状态机。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `action_no` | 异常处理动作编号 |
| `issue_no` | 异常编号 |
| `payment_order_id` | 支付单号 |
| `issue_type` | 异常类型 |
| `action_type` | 处理动作类型 |
| `assignee` | 当前处理人 |
| `handling_status` | 处理状态 |
| `action_remark` | 处理备注 |
| `operator` | 操作人 |

### 4.10 支付交易异常告警通知日志表 `t_payment_issue_alert_log`

作用：沉淀异常 SLA 巡检生成的告警 outbox、责任组、接收人和确认回执。当前作为本地可审计 outbox，后续可由真实 IM、短信、邮件网关消费。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `alert_no` | 告警通知编号 |
| `issue_no` | 异常编号 |
| `payment_order_id` | 支付单号 |
| `severity` | 严重等级 |
| `responsibility_group` | 责任组 |
| `alert_channel` | 通知通道，当前为 `IN_APP_OUTBOX` |
| `receiver` | 告警接收人 |
| `alert_status` | 告警发送状态 |
| `ack_status` | 告警回执状态 |
| `alert_content` | 告警内容 |
| `triggered_by` | 触发来源 |
| `ack_operator` | 回执确认人 |
| `ack_at` | 回执确认时间 |

### 4.11 支付渠道查单共享限流桶 `t_payment_channel_query_rate_bucket`

作用：在真实网关查单前按渠道原子占用共享固定时间窗配额，使直接查单、人工补跑和自动补偿在多实例部署下使用同一限流口径。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `channel_code` | 归一化后的渠道编码，唯一索引保证每个渠道只有一个共享桶 |
| `window_started_at` | 当前固定时间窗开始时间，使用数据库时钟更新 |
| `used_count` | 当前时间窗已占用的查单次数 |
| `updated_at` | 最近一次成功占用或窗口重置时间 |

并发规则：初始化使用幂等插入，配额占用使用带窗口过期和上限条件的单条 `UPDATE`；更新行数为 `0` 表示当前窗口配额已耗尽。既有运行库通过 `database/migrations/2026-09-08-add-payment-channel-query-rate-bucket.sql` 增量建表。

### 4.12 支付渠道查单限流拒绝日志 `t_payment_channel_query_rate_limit_log`

作用：独立沉淀共享渠道配额耗尽事实，支持任务中心窗口统计与渠道排查，不污染 `t_payment_notify_log` 的真实渠道查询失败率。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `rejection_no` | 限流拒绝流水号，唯一索引保证审计事实可定位 |
| `channel_code` | 归一化后的渠道编码 |
| `payment_order_id` | 被拒绝查单的支付单号 |
| `window_seconds` | 拒绝发生时使用的限流窗口秒数 |
| `max_queries` | 拒绝发生时使用的渠道窗口配额 |
| `rejected_at` | 拒绝时间，使用数据库时钟写入 |

事务规则：限流服务在 `REQUIRES_NEW` 事务内记录拒绝事实，并对 `BusinessException` 配置不回滚，确保向调用方返回 `PAYMENT-1027` 时审计记录仍提交。既有运行库通过 `database/migrations/2026-09-08-add-payment-channel-query-rate-limit-log.sql` 增量建表。

### 4.13 支付路由规则版本快照表 `t_payment_route_rule_version`

作用：以只增不改的完整快照记录路由规则新增、编辑、启停与回滚，支持管理端查看变更轨迹并恢复指定历史配置。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `rule_code` | 路由规则编码，与 `version_no` 组成唯一索引 |
| `version_no` | 规则内单调递增版本号 |
| `rule_name / match_scene / match_expression` | 规则名称、场景和表达式快照 |
| `target_channel_code / fallback_channel_code` | 目标与兜底渠道快照 |
| `status / status_type / priority` | 状态、样式与优先级快照 |
| `change_type` | `CREATE / BASELINE / UPDATE / STATUS_CHANGE / ROLLBACK / REVIEW_RELEASE` |
| `source_version_no` | 回滚时指向被恢复的历史版本，其余变更为空 |
| `change_reason` | 变更原因，最长 `255` 个字符 |
| `created_at` | 版本形成时间，使用数据库时钟写入 |

事务规则：更新、启停和回滚先锁定 `t_payment_route_rule_config` 当前规则行，再在同一事务内更新主配置并追加版本快照；回滚不会覆盖历史记录。既有运行库通过 `database/migrations/2026-09-08-add-payment-route-rule-version.sql` 幂等建表并为尚无版本记录的存量规则补 V1 基线。

### 4.14 支付路由规则复核申请表 `t_payment_route_rule_review`

作用：保存新增或修改规则的完整候选快照与轻量 maker-checker 处理状态。申请创建和驳回不修改运行规则；通过后才在同一事务内发布候选并生成 `REVIEW_RELEASE` 版本。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `request_no` | 复核申请编号，唯一索引 |
| `rule_code / base_version_no` | 规则编码与提交时基线版本；新建规则基线为空 |
| `pending_rule_code` | 待复核唯一占位，提交时等于规则编码，完成后置空；唯一索引封住并发双申请 |
| `operation_type` | `CREATE / UPDATE` |
| `rule_name / match_scene / match_expression` | 候选规则名称、场景和表达式 |
| `target_channel_code / fallback_channel_code` | 候选目标与兜底渠道 |
| `status / status_type / priority` | 候选状态、样式和优先级 |
| `requester / request_reason` | 申请人自声明与申请原因 |
| `review_status / review_status_type` | `PENDING_REVIEW / APPROVED / REJECTED` 及展示样式 |
| `reviewer / review_comment` | 复核人自声明与复核意见 |
| `created_at / reviewed_at / updated_at` | 申请、处理与更新时间 |

事务规则：同一规则只允许一个待复核申请；处理时锁定申请行并以 `review_status = 'PENDING_REVIEW'` 条件更新，避免重复处理。修改申请通过前比较最新规则版本与 `base_version_no`，并重新执行候选校验和阻断级冲突检查。人员字段均为自声明，表数据不是可信身份签字或生产审批证据。既有运行库通过 `database/migrations/2026-09-08-add-payment-route-rule-review.sql` 幂等建表。

### 4.15 异常告警生成幂等字段 `t_payment_issue_alert_log.generation_key`

作用：为 SLA 初次告警与来源升级告警提供数据库最终并发防重，不改变告警号、派发日志和历史回执事实。

| 取值 | 规则 |
| --- | --- |
| `ISSUE_ACTIVE:<issueNo>` | 当前未确认初次告警占用；确认时置空，允许未收口异常后续重新告警 |
| `ISSUE_ESCALATION:<sourceAlertNo>` | 来源告警升级永久占用；确认时不释放 |
| `NULL` | 查询趋势告警、外部通道派发日志及不参与 SLA 生成防重的其他记录 |

索引规则：`uk_issue_alert_generation(generation_key)` 为可空唯一索引。既有运行库通过 `database/migrations/2026-09-10-add-issue-alert-generation-key.sql` 一次性加列、为每组存量记录的最早一条回填键后建索引；脚本不删除其余历史重复记录。生产执行前必须在备份库验证重复分布、DDL 锁影响、回填耗时与回滚方案。

### 4.16 退款单表 `t_refund_order`

作用：保存退款申请、额度占用状态、渠道受理关联键和最终退款结果；退款事实与原支付成功事实并列保留。

| 字段 | 说明 |
| --- | --- |
| `refund_order_id` | 平台退款单号，唯一索引，也是渠道请求幂等键 |
| `payment_order_id / order_no` | 原支付单号与业务订单号 |
| `refund_amount / refund_method / refund_reason` | 退款金额、方式与原因 |
| `channel_code` | 实际受理退款的渠道编码，受理前为空 |
| `channel_refund_no` | 渠道侧退款单号，受理后首次绑定且不可覆盖 |
| `channel_submitted_at` | 首次获得有效渠道退款号的数据库时间 |
| `status / status_type` | `REVIEWING/PROCESSING/SUCCESS/FAIL` 及页面样式 |
| `applied_at / success_at` | 申请时间与可信退款成功时间 |

索引规则：`uk_refund_order_id(refund_order_id)` 保证平台退款单唯一；`uk_refund_channel_no(channel_code, channel_refund_no)` 防止同一渠道退款号绑定多笔退款；`idx_refund_payment_status(payment_order_id, status)` 支撑原支付单活动额度和成功退款聚合。既有运行库通过 `database/migrations/2026-09-10-add-refund-channel-trace.sql` 增量加列和唯一键；生产执行前必须核对历史重复、DDL 锁、回滚和渠道退款号实际唯一性。

### 4.17 支付与退款回调 nonce 表 `t_payment_callback_nonce`

作用：保存支付和退款渠道通知的短期随机串占用，用数据库唯一键提供跨实例防重放边界。

| 字段 | 说明 |
| --- | --- |
| `channel_code / nonce` | 规范化渠道编码与回调随机串，组合唯一 |
| `biz_type` | `PAYMENT` 或 `REFUND`，标识随机串对应业务类型 |
| `biz_no` | 支付单号或退款单号，仅用于审计关联，不参与唯一性放宽 |
| `expires_at / created_at` | nonce 过期时间与创建时间，均使用数据库时钟 |

索引规则：`uk_channel_nonce(channel_code, nonce)` 在同一渠道内跨支付和退款共同防重；`idx_callback_nonce_biz(biz_type, biz_no)` 支撑业务排查；`idx_nonce_expires_at(expires_at)` 支撑限量清理。既有运行库通过 `database/migrations/2026-09-10-generalize-callback-nonce.sql` 将 `payment_order_id` 重命名为 `biz_no`，新增 `biz_type` 并把存量行默认标记为 `PAYMENT`。生产执行前必须验证 MySQL 在线 DDL、锁影响、备份与回滚，不得因业务类型不同放宽同渠道 nonce 唯一约束。

### 4.18 退款渠道查单共享限流桶 `t_refund_channel_query_rate_bucket`

作用：在真实退款网关查询前按渠道原子占用独立固定窗口配额，使单笔人工查单、任务中心人工批次和自动补偿在多实例下使用同一退款容量口径。

| 字段 | 说明 |
| --- | --- |
| `channel_code` | 规范化渠道编码，唯一索引保证每个渠道一个退款查询桶 |
| `window_started_at` | 当前固定窗口开始时间，使用数据库时钟 |
| `used_count` | 当前窗口已成功占用的退款查单次数 |
| `updated_at` | 最近占额或窗口重置时间 |

并发规则与支付查单桶一致，但两张表不共享额度；额度为 `1` 的 H2 双连接测试只允许一条事务更新成功。既有运行库通过 `database/migrations/2026-09-10-add-refund-channel-query-rate-limit.sql` 增量建表。

### 4.19 退款渠道查单限流拒绝日志 `t_refund_channel_query_rate_limit_log`

| 字段 | 说明 |
| --- | --- |
| `rejection_no` | `RQRL` 前缀拒绝流水号，唯一索引 |
| `channel_code` | 规范化渠道编码 |
| `refund_order_id` | 被拒绝查询的退款单号 |
| `window_seconds / max_queries` | 拒绝发生时的限流配置快照 |
| `rejected_at` | 数据库拒绝时间 |

事务规则：拒绝日志在 `REQUIRES_NEW` 事务提交并返回 `PAYMENT-1047`，不写退款 `CHANNEL_QUERY_FAILED` 操作日志。任务中心通过支付和退款拒绝日志计数求和形成统一监控；该聚合不表示两类业务共用同一渠道容量桶。

### 4.20 告警供应商回执查询共享限流桶 `t_payment_issue_alert_receipt_query_rate_bucket`

作用：在调用通知集成网关查询供应商最终送达回执前，按告警通道提供跨 payment-core 实例共享的固定窗口容量保护。

| 字段 | 说明 |
| --- | --- |
| `channel_code` | 规范化为大写的告警通道编码，唯一索引保证每个通道一个共享桶 |
| `window_started_at` | 当前固定窗口开始时间，使用数据库时钟 |
| `used_count` | 当前窗口已成功占用的回执查询次数 |
| `updated_at` | 最近占额或窗口重置时间 |

并发规则：初始化使用幂等插入，占额通过带窗口过期和上限条件的单条 `UPDATE` 完成；更新 `0` 行表示额度耗尽。该桶与支付、退款渠道查单桶相互独立，既有运行库通过 `database/migrations/2026-09-10-add-issue-alert-receipt-query-rate-limit.sql` 增量建表。

### 4.21 告警供应商回执查询限流拒绝日志 `t_payment_issue_alert_receipt_query_rate_limit_log`

| 字段 | 说明 |
| --- | --- |
| `rejection_no` | `IARQRL` 前缀拒绝流水号，唯一索引 |
| `channel_code` | 被限流的告警通道编码 |
| `alert_no` | 被拒绝查询的告警投递日志编号 |
| `window_seconds / max_queries` | 拒绝发生时的窗口和单通道额度配置快照 |
| `rejected_at` | 数据库拒绝时间 |

事务规则：拒绝日志在 `REQUIRES_NEW` 事务中提交，并对 `BusinessException` 不回滚，确保外层将本条计为失败时仍保留容量拒绝事实。表中不保存通知网关请求、供应商响应、凭证或异常原文。

### 4.22 告警投递日志回执回查状态字段

`t_payment_issue_alert_log` 在既有供应商受理和送达字段之外增加以下回查调度状态：

| 字段 | 说明 |
| --- | --- |
| `receipt_query_count` | 已成功 claim 的回查次数，默认 `0`，同时作为并发 CAS 版本 |
| `last_receipt_query_at` | 最近一次 claim 的数据库时间，用于判断 `QUERYING` 占用是否过期 |
| `next_receipt_query_at` | 下一次允许自动回查的数据库时间；达到上限或已送达后为空 |
| `receipt_query_result` | `QUERYING / DELIVERED` 或受控失败与非终态分类，不保存异常原文 |

索引 `idx_issue_alert_receipt_query(provider_delivery_status, alert_status, next_receipt_query_at, receipt_query_count, created_at)` 支撑自动候选扫描。claim、失败完成和送达完成均校验次数，旧执行者不能覆盖新 claim；运行库由 `database/migrations/2026-09-10-add-issue-alert-receipt-query-retry.sql` 通过 `information_schema` 幂等补列和补索引。生产执行前仍需验证在线 DDL、锁影响、备份和回滚。

### 4.23 支付事件成功重发审计字段

`t_payment_event` 在既有发布状态、重试次数和时间之外增加：

| 字段 | 说明 |
| --- | --- |
| `last_republish_operator` | 最近一次成功重发的人工自声明操作人或自动调度身份，最长 `64` 个字符；旧记录允许为空 |
| `last_republished_at` | 最近一次成功重发审计写入的数据库时间；失败尝试不更新 |

一致性规则：重发事务先使用 `event_no` 唯一索引锁定事件行，既有派发成功后再更新两项审计字段；审计更新不为一行时整体失败，避免发布状态与来源分离提交。运行库通过 `database/migrations/2026-09-10-add-payment-event-republish-audit.sql` 以 `information_schema` 幂等补列。当前只完成 H2/MySQL 兼容 SQL 测试，生产执行仍需评估在线 DDL、锁等待、备份与回滚。

### 4.24 支付查单与关单来源复用字段

本轮不新增表和字段，复用既有审计载体：

1. `t_payment_notify_log.request_payload`：主动查单保存渠道查询技术来源 `querySource` 与发起来源 `initiatedBy`；成功、非终态、失败和限流拒绝使用同一结构。
2. `t_payment_process_log.source`：主动查单使用独立 `process_stage=主动查单`，`source` 保存 `customer-self-service`、后台规范化操作人或 `payment-query-scheduler`。
3. `t_payment_event.event_payload`：查单收口生成的 `PAYMENT_SUCCESS` 与实际关单生成的 `PAYMENT_CLOSED` 增加 `initiatedBy`，事件处理日志对实际关单使用同一来源。
4. 上述字段容量可容纳最长 `64` 字符来源，无需迁移。历史记录不会回填或推导操作人，避免制造不存在的审计事实。

### 4.25 退款渠道下发来源复用字段

本轮不新增表、字段或迁移，复用 `t_refund_operation_log.operator_name`：

1. `CHANNEL_SUBMIT` 保存本次渠道提交成功派发来源。
2. `CHANNEL_SUBMIT_FAILED` 保存调用渠道前被配置、治理或快照校验拒绝时的派发来源。
3. `CHANNEL_SUBMIT_UNKNOWN` 保存渠道可能已经受理但结果未知时的派发来源。
4. 来源由退款审核、人工/任务重试或事件人工/自动重发入口传入，去空格后最长 `64` 个字符，与现有字段容量一致。历史固定身份记录不回填、不推导，避免制造不存在的操作事实。

### 4.26 死信重放处置事实复用字段

本轮不新增表或迁移，复用 `t_payment_dead_letter_task` 既有字段：

1. `operator VARCHAR(64)` 保存最近一次人工复核、结案或重放操作人。
2. `resolution_note VARCHAR(512)` 保存最近人工处置说明；进入 `REPLAYING` 时同步写入本次重放说明，失败后按既有规则覆盖为不含代理异常原文的脱敏失败说明。
3. `resolved_at` 在复核、结案和进入重放时更新，`last_replay_at / replay_count` 只随条件重放更新；成功与失败状态继续由独立 CAS 更新收口。
4. 页面返回的消息体和消息头是复制后的脱敏快照，数据库仍保存原始载荷供受控重放。生产环境需另行验证数据访问权限、留存周期、密钥字段治理和目标消费者幂等。

### 4.27 配置复核人员声明存储边界

本轮不新增表、字段或迁移，继续复用各配置复核表既有 `requester / reviewer` 字段：

1. 服务端写入前统一去除人员声明首尾空格，并限制最长 `64` 个字符，与现有字段容量保持一致。
2. 原始声明包含 ISO 控制字符时整次申请或复核动作失败，不把不可见字符写入审计字段，也不依赖数据库截断或字符集副作用做校验。
3. 申请原因和复核意见继续按既有说明字段保存并允许正常换行；本轮不清洗、不推导历史人员记录，避免制造不存在的审批事实。
4. 数据库存储的人员声明仍不具备身份真实性或不可抵赖性，后续可信身份接入需增加认证主体标识、来源和授权证据，而不是仅扩展展示名称字段。

### 4.28 人工操作人审计字段字符边界

本轮不新增表、字段或迁移，继续复用任务运行、日终、支付处理、退款操作、事件重发、异常动作和告警投递/确认中的既有人员字段：

1. 服务端只写入去除普通首尾空格后、最长 `64` 个字符且原始值不含 ISO 控制字符的人工来源。
2. 非法来源在任何持久化前失败，不依赖数据库截断、字符集转换或后续展示层清洗；历史记录不回写、不推导，避免制造新的操作事实。
3. 自动任务的服务端调度类别保持原值，与人工自声明分开解释；两者都只是执行来源，不代表认证主体或正式审批人。
4. 后续可信身份治理应增加稳定主体标识、认证来源、角色授权和不可抵赖证据，不能仅依赖现有展示名称字段。

### 4.29 异常人工动作写入容量边界

1. 本轮不修改表结构，服务端按既有 `t_payment_issue_action_log` 容量约束 `issue_no VARCHAR(128)`、`action_type VARCHAR(32)`、`assignee VARCHAR(64)`、`action_remark VARCHAR(255)` 和 `operator VARCHAR(64)`。
2. 告警人工确认的 `alert_no` 按 `t_payment_issue_alert_log.alert_no VARCHAR(64)` 校验；非法或含控制字符的编号在读取告警表前失败，不依赖数据库隐式截断。
3. 单批最多 `100` 条且规范化编号不得重复，降低单事务锁持有、日志写入和告警更新的无界放大风险。该上限是应用写入口契约，不新增数据库约束或迁移。
4. 数据库事务仍保证批量动作任一处理中途异常时整体回滚；本地测试未替代生产 MySQL 容量、锁等待与并发压测。

### 4.30 CSV 安全编码与持久化边界

1. CSV 公式防护只作用于服务层导出副本，不回写订单、支付、退款、事件、告警、配置或结算表，也不新增 schema 与迁移。
2. 文本标记不是业务字段的一部分，数据库查询和 API JSON 响应继续返回原始值或既有脱敏值；只有 CSV 序列化结果包含安全前缀。
3. 双引号转义、换行包裹和公式前缀处理均在导出时完成，避免为展示安全污染历史事实或破坏后续对账关联键。
4. 本轮测试不证明生产文件存储、对象存储转发、人工二次加工或第三方导入链路安全，相关链路需保留独立验证证据。

### 4.31 异常告警导出查询容量边界

1. 异常告警 CSV 继续读取既有告警查询及排序 SQL，不新增表、索引、schema 或迁移，也不修改持久化事实。
2. Service 固定传入第一页和 `1001` 条探测上限，Mapper 现有 `LIMIT #{query.limit} OFFSET #{query.offset}` 使数据库单次最多返回 `1001` 行。
3. `1000` 行以内完整导出，第 `1001` 行只用于识别超限并拒绝请求，不静默截断，也不执行无界查询。
4. 该边界未经过生产数据规模、执行计划、连接池或下载耗时验证，不能作为生产容量与稳定性结论。

### 4.32 同步运营导出 SQL 统一容量边界

1. `13` 个独立运营导出 Mapper SQL 在既有筛选和稳定排序之后增加固定 `LIMIT 1001`，覆盖订单、账单、支付、退款、收银台会话、支付请求、资金流、路由执行、事件、处理日志、日终、任务运行和结算。
2. 异常告警继续复用分页 SQL 并传入 `pageNo=1 / pageSize=1001`；因此 `14` 类同步运营导出均在数据库层最多读取 `1001` 行。
3. 第 `1001` 行只用于 Service 判断超过 `1000` 条并拒绝导出，不持久化、不返回、不静默截断；普通分页查询、表结构、索引、schema 和迁移不变。
4. 配置治理快照的多分区查询未纳入本轮 SQL 上限。固定行数不能替代生产执行计划、并发、连接池、内存和下载耗时验证。

### 4.33 配置治理快照查询边界现状

1. 单分区配置导出已改为只调用目标配置查询，避免读取其他七个导出分区及不会进入 CSV 的协议类型选项；`ALL` 才顺序读取八个导出分区。
2. Service 对每个 Mapper 结果独立执行 `1000` 条上限校验，超限整体拒绝，不修改配置表、schema、索引或迁移。
3. 八个查询仍与配置总览共用现有无分页 SQL，本轮没有增加 `LIMIT 1001`；因此该边界限制输出和后续 CSV 内存放大，但尚不能限制数据库到 Mapper 的单次结果集大小。
4. 若生产配置规模接近上限，应新增不影响总览语义的专用导出查询并下沉 `LIMIT 1001`，同时验证执行计划、连接池和下载耗时；当前本地测试不构成这些结论。

### 4.34 配置治理快照专用 SQL 容量边界

1. 八个配置分区新增专用导出查询，与普通查询复用同一字段和稳定排序 SQL 片段，仅专用查询追加 `LIMIT 1001`。
2. 普通配置总览及运行路由、自检、返回码导入等业务查询保持无该固定限制，避免业务配置集合被静默截断。
3. 第 `1001` 行由 Service 用于判断超过 `1000` 条并整体拒绝；本轮不修改表、字段、索引、schema 或迁移。
4. 固定行数只证明查询结果集边界，不替代生产执行计划、索引选择、并发、连接池、内存和下载耗时验证。

### 4.35 外部渠道结果入库前字符边界

1. 支付查单和提交结果在写通知日志、返回码快照、支付成功事实前检查原始字符串，尾部换行等 ISO 控制字符不得依赖数据库截断或 `trim` 清洗后入库。
2. 退款提交结果在适配器及首次渠道退款号绑定前双重检查；非法受理号不写 `channel_refund_no`，出站事件进入 `UNKNOWN`，退款继续保持 `PROCESSING`。
3. 本轮不修改表、字段、索引、schema 或迁移；现有字段长度仍由服务边界和数据库共同约束。
4. 该本地边界不证明生产渠道返回、网络故障、数据库隔离或资金状态。

### 4.36 gateway-access 查单预清洗边界

1. 支付与退款查单适配器必须在任何 `trim` 或默认来源替换前保留并校验外部原始字符，非法交易状态、渠道流水或来源不得形成可入库事实。
2. 合法缺省来源继续补为 `GATEWAY_ACCESS_HTTP`；显式控制字符来源必须失败，不写查单成功日志、返回码快照、支付或退款状态。
3. 本轮不修改表、字段、索引、schema 或迁移，现有字段容量与 Service 写入前校验保持不变。
4. 该边界不证明生产渠道查询、数据库隔离、资金状态或稳定性。

### 4.37 告警通知结果入库边界

1. 通知器返回空结果、空投递状态或任一结果字段含 ISO 控制字符时，不得写入供应商受理或送达事实。
2. 非法候选只写固定 `provider_delivery_status=SEND_EXCEPTION` 与脱敏说明，原始状态、说明、回执快照、回执号和渲染内容不得进入告警日志字段。
3. 本轮不修改表、字段、索引、schema 或迁移；现有容量截断和候选供应商逐条日志模型保持不变。
4. 该边界不证明真实供应商受理、最终送达、数据库隔离或生产稳定性。

### 4.38 告警供应商状态标准化边界

1. `provider_delivery_status` 的外部通知结果只允许写入标准大写 `ACCEPTED / DELIVERED / FAILED`，内部异常继续使用既有固定分类。
2. 标准化发生在写库前，确保 `provider_delivery_status = 'ACCEPTED'` 的回执候选、积压、老化和监控 SQL 不会遗漏大小写或首尾空格不同但业务已判成功的记录。
3. 未知状态不落原文，当前候选仅写固定 `SEND_EXCEPTION`；本轮不修改表、字段、索引、schema 或迁移。
4. 该边界不证明真实供应商码表、最终送达、数据库隔离或生产稳定性。

### 4.39 告警回执查询结果持久化边界

1. `receipt_query_result` 只保存内部有限分类或标准状态，包括 `QUERYING / EMPTY / ACCEPTED / FAILED / DELIVERED / QUERY_ERROR / RECEIPT_CONFLICT / RATE_LIMITED / ADAPTER_MISSING`，不保存未知供应商状态原文。
2. `provider_delivery_status` 只有在查询结果明确为 `DELIVERED` 且与原 `provider_receipt_no` 关联一致时才由条件更新改写；原记录有回执号而结果缺号或异号时保持 `ACCEPTED`。
3. Service 在写库前按现有列容量校验回执号、说明和快照并脱敏快照；非法结果只完成本次查询尝试，不覆盖既有供应商回执事实或来源告警确认状态。
4. 本轮不修改表、字段、索引、schema 或迁移；本地测试不证明真实供应商回执、数据库隔离或生产稳定性。

### 4.40 告警回执适配器选择异常持久化边界

1. 适配器选择异常通过现有条件更新把 `receipt_query_result` 从 `QUERYING` 改为 `QUERY_ERROR`，同时按尝试次数设置 `next_receipt_query_at`；不新增异常明文字段。
2. 该更新仍要求 `provider_delivery_status='ACCEPTED'`、`ack_status='待确认'`、领取次数匹配且当前结果为 `QUERYING`，并发失配不得覆盖其他执行者结果。
3. 无适配器继续写 `ADAPTER_MISSING`；两类失败均不修改供应商回执、送达状态或来源告警确认事实。
4. 本轮不修改表、字段、索引、schema 或迁移；真实数据库事务、调度竞争和生产稳定性仍需外部验证。

### 4.41 支付查单交易状态持久化边界

1. 支付查单结果写库前只允许标准大写平台状态 `PREPAY_CREATED / RISK_REVIEW / RISK_BLOCKED / RISK_REJECTED / WAIT_CALLBACK / SUCCESS / CLOSED / FAIL`；合法输入的首尾空格和大小写差异在 Service 边界统一收敛。
2. 渠道原始返回码继续通过独立 `channel_return_code` 及返回码快照语义保存，不得写入支付交易状态列；`USERPAYING` 等渠道码不扩充平台状态集合。
3. 未知交易状态在返回码 Mapper 查询前拒绝，不新增返回码快照，不更新支付成功或关闭事实，也不派发下游事件；既有支付记录保持原状态，仅保留固定查单失败审计。
4. 本轮不修改表、字段、索引、schema 或迁移；本地测试不证明真实渠道数据、数据库隔离、资金到账或生产稳定性。

### 4.42 支付提交结果持久化边界

1. 渠道提交结果写库前按 `t_payment_order.channel_transaction_no / last_*` 和 `t_payment_attempt.response_payload / attempt_status / attempt_status_type` 现有列容量统一校验，状态类型只保存标准小写有限集合。
2. 返回码标准化新增的标准码、文案、处理建议、重试与人工介入标识、映射版本以及扩展后的响应快照必须再次通过相同容量和原始字符边界，避免配置脏数据造成数据库截断或污染运营字段。
3. 结果校验失败时只把已创建支付尝试更新为“结果未知，待查单”，预付单更新为“结果待确认”；不写渠道流水、返回码快照、`PAYMENT_SUBMIT` 事件或待回调通知。该语义不等于渠道明确拒绝。
4. 本轮不修改表、字段、索引、schema 或迁移；本地测试不证明真实渠道受理、数据库隔离、资金到账或生产稳定性。

### 4.43 风控准入结果持久化边界

1. 只有成功业务码下的标准决策 `REVIEW / INTERCEPT / REJECT` 才允许生成支付风险状态和 `PAYMENT_RISK_*` 事件；`PASS` 仅允许继续主流程，不单独形成支付或资金事实。
2. 命中编码、风险标签、复核单号、事件号和说明在事件 JSON 生成前完成控制字符、空白和容量校验，避免不可信远端字段进入 `t_payment_event.event_payload`。
3. 风控空结果、业务失败码、未知决策或非法字段在任何支付更新前失败，不创建 `t_payment_attempt`、不写风险事件，也不把未知决策按默认 `RISK_BLOCKED` 持久化。
4. 本轮不修改表、字段、索引、schema 或迁移；本地测试不证明真实风控数据、数据库隔离、策略有效性或生产稳定性。

### 4.44 运营配置快照消费与版本留痕边界

1. 只有业务成功、请求三维匹配且字段完整的运营配置快照才允许补齐提交上下文；串业务线、支付类型或终端的快照不得写入支付路由或尝试版本字段。
2. `snapshotVersion` 必须非负，合法值继续通过现有 `ops_config_snapshot_version` 写入路由记录和支付尝试；非法快照不得形成版本留痕，避免错误版本被解释为已消费配置事实。
3. 默认支付方式、渠道档案编码、路由策略和控制项按 ops-config 来源 schema 容量在适配器边界校验；本轮不复制控制项到 payment-core 数据表，也不新增缓存或配置快照表。
4. 本轮不修改表、字段、索引、schema 或迁移；本地测试不证明真实配置发布、刷新事件传播、数据库隔离或生产稳定性。

### 4.45 支付成功事件派发状态事实边界

1. `t_payment_event.publish_status` 只有在 HTTP 模式下清分与账务均明确返回 `code=0 + data`，或 AMQP 发布调用无异常后才允许写为成功；空配置、HTTP 2xx 业务失败和空响应体均不得形成成功事实。
2. 下游失败继续通过现有重试次数、失败状态和死信状态留痕。本轮不新增“清分已接收、账务未接收”的子步骤表，因此数据库事件状态不能证明每个下游的独立处理结果。
3. 清分成功、账务失败后的事件重试可能重放清分，必须依赖下游按稳定业务标识幂等；若后续要求逐下游可审计重放，应另行设计投递明细或 outbox 子任务，而不是从单一成功状态推导 exactly-once。
4. 本轮不修改表、字段、索引、schema 或迁移；本地测试不证明真实 MQ 确认、供应商送达、清分完成、账务入账、资金到账或生产数据库一致性。

### 4.46 超时关单资格竞争与任务统计边界

1. 超时关单仍以 `t_payment_order` 条件更新作为最终资格闸门：状态为 `PREPAY_CREATED`、渠道流水为空、预付单已过期且不存在支付尝试时才允许改为 `CLOSED`。
2. 候选扫描与条件更新之间发生状态变化时，更新行数为 `0`；该事务不改写尝试或预付单，不插入 `PAYMENT_EXPIRED_CLOSED` 事件和处理日志，因此不能形成关闭成功事实。
3. 任务运行日志沿现有字段保存实际关闭数到 `success_count`、资格竞争跳过数到 `warning_count`、事务异常数到 `fail_count`，无需新增表或字段。
4. 本轮不修改 Mapper SQL、表、索引、schema 或迁移；本地测试不证明生产 MySQL 隔离、锁竞争、多实例调度、真实渠道关单或资金状态。

## 5. 状态建议

### 5.1 当前版本已使用状态

| 对象 | 当前状态 |
| --- | --- |
| 账单 | `待支付` / `已结清` |
| 预付单 | `待支付` / `支付中` |
| 支付单 | `PREPAY_CREATED` / `WAIT_CALLBACK` / `SUCCESS` / `CLOSED` |
| 退款单 | `REVIEWING` / `PROCESSING` / `SUCCESS` / `FAIL` |
| 回调日志 | `待回调` / `已收口` |

### 5.2 后续增强建议

后续可进一步拆分为：

1. 支付提交状态
2. 渠道受理状态
3. 回调处理状态
4. 查单确认状态
5. 关闭原因状态

## 6. 索引与查询建议

当前和后续都应遵循：

1. 主业务单号必须唯一索引
2. 支付详情页常用追踪字段必须具备索引
3. 后续如果引入分页和筛选，优先为 `payment_order_id`、`order_no`、`status`、`created_at` 建组合索引

## 7. 版本结论

当前数据库设计已经不再是“待补的概念稿”，而是与 `schema.sql` 对齐的正式数据库交付说明。

它的价值在于：

1. 明确支付域对象边界
2. 支撑当前前后端联调
3. 为账务、清结算、对账系统提供稳定上游对象
