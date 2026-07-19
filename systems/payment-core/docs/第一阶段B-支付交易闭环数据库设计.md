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
```

关系说明：

1. 一笔订单在一期场景下至少对应一张账单。
2. 一张账单可创建一个预付单。
3. 一个预付单关联一个支付单。
4. 一个支付单可有多次尝试、多条回调、多条事件。

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

## 5. 状态建议

### 5.1 当前版本已使用状态

| 对象 | 当前状态 |
| --- | --- |
| 账单 | `待支付` / `已结清` |
| 预付单 | `待支付` / `支付中` |
| 支付单 | `PREPAY_CREATED` / `WAIT_CALLBACK` / `SUCCESS` / `CLOSED` |
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
