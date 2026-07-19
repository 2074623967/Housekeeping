# 第一阶段B-支付交易闭环数据库设计

## 1. 文档定位

本文档是 `payment-core` 在 `Sunday, July 19, 2026` 的 `第一阶段B：支付交易闭环` 数据库设计稿。

目标：

- 补齐订单支付主链路的核心对象
- 为后续接口开发和代码落地提供数据库基线

## 2. 设计范围

本次新增对象聚焦于支付交易闭环，不覆盖：

- 账务分录
- 清分结果
- 对账差异
- 保证金扣罚

本次新增对象包括：

1. `t_bill`
2. `t_prepay_order`
3. `t_payment_attempt`
4. `t_payment_notify_log`
5. `t_payment_channel_route_record`
6. `t_payment_event`

## 3. 对象关系

```text
order
  -> bill
    -> prepay_order
      -> payment_order
        -> payment_attempt
        -> payment_channel_route_record
        -> payment_notify_log
        -> payment_event
```

说明：

- 一笔订单可对应多条账单
- 一条账单可创建一个预付单
- 一个预付单可收敛到一个支付单
- 一个支付单可有多次尝试
- 一个支付单可有多条回调日志

## 4. 表设计

### 4.1 账单表 `t_bill`

用途：

- 作为订单与支付之间的金额桥梁

核心字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `bill_no` | varchar(64) | 账单号 |
| `order_no` | varchar(64) | 订单号 |
| `bill_type` | varchar(32) | 主账单、补差价账单、尾款账单 |
| `amount` | decimal(18,2) | 应付金额 |
| `paid_amount` | decimal(18,2) | 已付金额 |
| `bill_status` | varchar(32) | 账单状态 |
| `expire_time` | datetime | 过期时间 |
| `created_at` | datetime | 创建时间 |
| `updated_at` | datetime | 更新时间 |

索引建议：

- `uk_bill_no`
- `idx_order_no`
- `idx_bill_status`

### 4.2 预付单表 `t_prepay_order`

用途：

- 作为收银台展示和支付提交前的统一支付入口对象

核心字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `prepay_order_no` | varchar(64) | 预付单号 |
| `bill_no` | varchar(64) | 关联账单号 |
| `order_no` | varchar(64) | 订单号 |
| `payment_order_id` | varchar(64) | 关联支付单号 |
| `amount` | decimal(18,2) | 支付金额 |
| `currency` | varchar(16) | 币种 |
| `status` | varchar(32) | 预付单状态 |
| `default_method` | varchar(32) | 默认支付方式 |
| `expire_time` | datetime | 过期时间 |
| `created_at` | datetime | 创建时间 |
| `updated_at` | datetime | 更新时间 |

索引建议：

- `uk_prepay_order_no`
- `idx_bill_no`
- `idx_payment_order_id`

### 4.3 支付尝试表 `t_payment_attempt`

用途：

- 记录一次支付单的每次真实支付尝试

核心字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `attempt_no` | varchar(64) | 尝试号 |
| `payment_order_id` | varchar(64) | 支付单号 |
| `prepay_order_no` | varchar(64) | 预付单号 |
| `payment_method` | varchar(32) | 支付方式 |
| `channel_code` | varchar(64) | 渠道编码 |
| `submit_status` | varchar(32) | 提交状态 |
| `channel_request_no` | varchar(128) | 渠道请求号 |
| `channel_pay_token` | varchar(256) | 渠道支付令牌 |
| `fail_reason` | varchar(256) | 失败原因 |
| `created_at` | datetime | 创建时间 |
| `updated_at` | datetime | 更新时间 |

索引建议：

- `uk_attempt_no`
- `idx_payment_order_id`
- `idx_channel_code`

### 4.4 支付回调日志表 `t_payment_notify_log`

用途：

- 记录渠道回调原文和收口结果

核心字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `notify_no` | varchar(64) | 回调日志号 |
| `payment_order_id` | varchar(64) | 支付单号 |
| `channel_code` | varchar(64) | 渠道编码 |
| `channel_transaction_no` | varchar(128) | 渠道交易号 |
| `notify_status` | varchar(32) | 回调处理状态 |
| `sign_verify_result` | varchar(32) | 验签结果 |
| `notify_payload` | text | 回调原文 |
| `received_at` | datetime | 接收时间 |
| `processed_at` | datetime | 处理时间 |

索引建议：

- `uk_notify_no`
- `idx_payment_order_id`
- `idx_channel_transaction_no`

### 4.5 渠道路由记录表 `t_payment_channel_route_record`

用途：

- 记录支付单在支付提交前后的路由决策结果

核心字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `route_record_no` | varchar(64) | 路由记录号 |
| `payment_order_id` | varchar(64) | 支付单号 |
| `route_scene` | varchar(32) | 路由场景 |
| `candidate_channels` | varchar(512) | 候选渠道 |
| `selected_channel` | varchar(64) | 选中渠道 |
| `route_result` | varchar(32) | 路由结果 |
| `route_reason` | varchar(256) | 路由原因 |
| `created_at` | datetime | 创建时间 |

索引建议：

- `uk_route_record_no`
- `idx_payment_order_id`

### 4.6 支付事件表 `t_payment_event`

用途：

- 用于记录支付主链路中的状态事件，供后续账务、清结算消费

核心字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `event_no` | varchar(64) | 事件号 |
| `payment_order_id` | varchar(64) | 支付单号 |
| `event_type` | varchar(64) | 事件类型 |
| `event_status` | varchar(32) | 事件状态 |
| `event_body` | text | 事件内容 |
| `published_flag` | tinyint | 是否已发布 |
| `created_at` | datetime | 创建时间 |

索引建议：

- `uk_event_no`
- `idx_payment_order_id`
- `idx_event_type`
- `idx_published_flag`

## 5. 状态字段建议

### 5.1 账单状态

- `INIT`
- `WAIT_PAY`
- `PART_PAY`
- `PAID`
- `CLOSED`

### 5.2 预付单状态

- `INIT`
- `PREPAY_CREATED`
- `PAYING`
- `EXPIRED`
- `CLOSED`

### 5.3 支付尝试状态

- `SUBMITTING`
- `ACCEPTED`
- `FAIL`
- `TIMEOUT`

### 5.4 回调处理状态

- `RECEIVED`
- `SIGN_FAIL`
- `IGNORED`
- `PROCESSED`

## 6. 设计结论

从 `Sunday, July 19, 2026` 开始，`payment-core` 如果要进入真正支付闭环开发，数据库层至少要补齐以上 6 个核心对象。

在这些对象落地前，系统只能被视为“支付后台骨架”，不能被视为完整支付一期。

