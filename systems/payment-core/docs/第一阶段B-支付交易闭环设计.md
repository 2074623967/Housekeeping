# 第一阶段B-支付交易闭环设计

## 1. 文档定位

本文档用于修正 `payment-core` 在 `Sunday, July 19, 2026` 的第一阶段定义。

当前结论：

- 已完成的是 `第一阶段A：支付后台运营骨架`
- 尚未完成的是 `第一阶段B：支付交易闭环`

本文档聚焦的就是 `第一阶段B`。

## 2. 为什么需要这一阶段

当前 `payment-core` 已具备：

- 工作台
- 订单中心
- 支付单管理
- 退款单入口
- 结算入口
- MySQL 接入

但这还不等于真正完成支付系统一期，因为真正的支付主链路还没有落地。

## 3. 第一阶段B建设目标

一句话目标：

把家政订单从“发起支付”到“支付成功回调收口”的整条主链路补完整。

## 4. 要覆盖的主链路

必须覆盖以下完整链路：

1. 订单创建
2. 账单生成
3. 预付单生成
4. 收银台参数组装
5. 支付方式选择
6. 支付渠道路由
7. 发起支付
8. 支付结果返回
9. 异步回调接收
10. 主动查单补偿
11. 支付状态收敛
12. 更新订单与账单
13. 触发后续账务/清分事件

## 5. 核心对象补充

在当前对象基础上，必须新增以下关键对象：

1. `bill`
2. `prepay_order`
3. `payment_attempt`
4. `payment_notify_log`
5. `payment_channel_route_record`
6. `payment_event`

## 6. 前端需要补的能力

### 6.1 app-web / h5 侧

需要补：

- 收银台页面
- 支付方式列表
- 支付结果页
- 支付失败页

### 6.2 admin-web 侧

需要补：

- 支付单详情页
- 支付回调日志页
- 主动查单操作
- 支付关闭操作

## 7. 后端需要补的接口

至少补齐：

1. `POST /api/payments/prepay`
2. `GET /api/payments/cashier/{prepayOrderNo}`
3. `POST /api/payments/submit`
4. `POST /api/payments/callback/{channel}`
5. `POST /api/payments/query`
6. `POST /api/payments/close`
7. `GET /api/payments/{paymentOrderId}`

## 8. 支付状态机

至少要有：

- `INIT`
- `PREPAY_CREATED`
- `PAYING`
- `WAIT_CALLBACK`
- `SUCCESS`
- `FAIL`
- `CLOSED`
- `PARTIAL_REFUND`
- `REFUNDED`

## 9. 必须补的工程能力

1. 幂等控制
2. 签名验签
3. 回调防重
4. 主动查单补偿
5. 渠道超时处理
6. 支付失败关闭
7. 审计日志

## 10. 结论

从 `Sunday, July 19, 2026` 开始，`payment-core` 的第一阶段不能再被理解为“已经完成支付一期”，而应拆成：

- `第一阶段A：支付后台运营骨架`
- `第一阶段B：支付交易闭环`

后续代码实现需要优先补全 `第一阶段B`。

