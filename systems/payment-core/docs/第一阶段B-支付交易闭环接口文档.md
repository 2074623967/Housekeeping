# 第一阶段B-支付交易闭环接口文档

## 1. 文档定位

虽然文件名保留“第一阶段B”，但当前文档内容已经作为 `payment-core` 一期支付交易闭环 V1 的冻结版接口文档使用。

适用对象：

- 产品经理
- 前端开发
- 后端开发
- 测试工程师

## 2. 接口清单

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/payments/prepay` | `POST` | 创建账单、支付单、预付单 |
| `/api/payments/cashier/{prepayOrderNo}` | `GET` | 获取收银台数据 |
| `/api/payments/submit` | `POST` | 提交支付 |
| `/api/payments/callback/{channel}` | `POST` | 模拟渠道回调 |
| `/api/payments/query` | `POST` | 主动查单 |
| `/api/payments/close` | `POST` | 关闭支付单 |
| `/api/payments/{paymentOrderId}` | `GET` | 查询支付详情 |
| `/api/payment-records` | `GET` | 按支付维度分页查询收款记录 |
| `/api/payment-records/{paymentOrderId}` | `GET` | 查询单笔收款记录详情 |
| `/api/payment-metrics/summary` | `GET` | 查询支付成功率、成功金额和状态分布 |
| `/api/refunds` | `GET` | 分页查询退款单 |
| `/api/refunds/apply` | `POST` | 发起退款申请 |
| `/api/refunds/approve` | `POST` | 审核通过退款单并提交处理 |
| `/api/refunds/success` | `POST` | 模拟渠道退款成功回调 |
| `/api/refunds/fail` | `POST` | 模拟渠道退款失败回调 |
| `/api/refunds/retry` | `POST` | 失败退款单重新提交处理 |
| `/api/payment-config` | `GET` | 查询支付渠道、路由规则、支付协议与返回码映射配置 |
| `/api/payment-config/channels/toggle` | `POST` | 启停支付渠道 |
| `/api/payment-config/route-rules/toggle` | `POST` | 启停路由规则 |
| `/api/payment-config/protocols/toggle` | `POST` | 启停支付协议 |
| `/api/payment-config/return-codes/toggle` | `POST` | 启停渠道返回码映射 |
| `/api/payment-monitor/overview` | `GET` | 查询支付趋势、渠道表现和异常告警 |

## 3. 创建预付单

接口：`POST /api/payments/prepay`

请求示例：

```json
{
  "orderNo": "ORD202607190003",
  "payScene": "HOME_CLEAN"
}
```

返回示例：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "prepayOrderNo": "PRE1752912345678",
    "billNo": "BILL1752912341234",
    "orderNo": "ORD202607190003",
    "customerName": "张女士",
    "amount": "¥168.00",
    "payScene": "HOME_CLEAN",
    "cashierTitle": "家政服务收银台",
    "cashierStatus": "待支付",
    "cashierStatusType": "warn",
    "paymentOrderId": "PAY1752912340001",
    "createdAt": "2026-07-19 10:15:30",
    "expiresAt": "2026-07-19 11:15:30"
  },
  "requestId": "REQ202607190001"
}
```

业务说明：

1. 若订单尚无账单，则自动创建账单。
2. 若订单当前不存在“未过期且未收口”的预付单，则自动创建新的支付单与预付单。
3. 若同一订单已存在有效预付单，则直接复用原预付单与支付单，保证重复拉起收银台幂等。

## 4. 获取收银台参数

接口：`GET /api/payments/cashier/{prepayOrderNo}`

返回示例：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "prepayOrderNo": "PRE1752912345678",
    "orderNo": "ORD202607190003",
    "billNo": "BILL1752912341234",
    "customerName": "张女士",
    "amount": "¥168.00",
    "payScene": "HOME_CLEAN",
    "title": "家政服务收银台",
    "status": "待支付",
    "statusType": "warn",
    "expiresAt": "2026-07-19 11:15:30",
    "channels": ["微信支付", "支付宝", "银行卡"]
  },
  "requestId": "REQ202607190002"
}
```

## 5. 提交支付

接口：`POST /api/payments/submit`

请求示例：

```json
{
  "prepayOrderNo": "PRE1752912345678",
  "paymentMethod": "微信支付",
  "channelCode": "WX_H5",
  "terminal": "APP_WEB",
  "clientIp": "127.0.0.1",
  "idempotencyKey": "PRE1752912345678|微信支付|WX_H5"
}
```

`channelCode` 会由支付核心统一规范化为标准编码：

| 请求渠道/支付方式 | 标准渠道编码 |
| --- | --- |
| 微信、`WX_H5`、`wx_jsapi` | `wx_h5` |
| 支付宝、`ALIPAY_H5`、`alipay_h5` | `alipay_h5` |
| 银行卡、银行转账、`BANK_CARD` | `offline_bank` |

返回示例：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "prepayOrderNo": "PRE1752912345678",
    "billNo": "BILL1752912341234",
    "orderNo": "ORD202607190003",
    "customerName": "张女士",
    "amount": "¥168.00",
    "payScene": "HOME_CLEAN",
    "cashierTitle": "家政服务收银台",
    "cashierStatus": "支付中",
    "cashierStatusType": "info",
    "paymentOrderId": "PAY1752912340001",
    "createdAt": "2026-07-19 10:15:30",
    "expiresAt": "2026-07-19 11:15:30"
  },
  "requestId": "REQ202607190003"
}
```

落库效果：

1. 更新预付单状态
2. 更新支付单支付方式与渠道
3. 按渠道配置与路由规则执行真实路由决策，并记录路由日志
4. 记录支付尝试日志
5. 记录提交事件
6. 预写一条待回调日志

路由说明：

1. 提交支付时，系统会基于 `paymentMethod`、请求渠道、支付场景、终端、金额和客户类型组装路由上下文。
2. 路由优先级为：
   - 已启用路由规则命中
   - 前端指定且已启用的渠道
   - 按支付方式匹配默认启用渠道
3. 若规则目标渠道被停用，则自动使用规则配置的兜底渠道。
4. 当前内置规则示例：
   - `RULE_HOME_WX`：家政 H5 小额微信优先
   - `RULE_HOME_ALI`：家政 H5 小额支付宝优先
   - `RULE_ENTERPRISE_BANK`：企业或大额订单优先走线下银行

幂等说明：

1. 当同一预付单重复提交时，如果支付单已进入 `WAIT_CALLBACK`、`SUCCESS` 或 `CLOSED`，接口直接返回当前预付单信息。
2. 如果幂等键已存在，接口同样直接返回当前预付单信息。
3. 这样可以避免按钮重复点击、浏览器重试和接口重放导致重复路由、重复尝试和重复待回调日志。

## 6. 模拟回调

接口：`POST /api/payments/callback/{channel}`

路径参数：

| 参数 | 说明 |
| --- | --- |
| `channel` | 渠道编码，例如 `WX_H5` |

请求示例：

```json
{
  "paymentOrderId": "PAY1752912340001",
  "channelTransactionNo": "WX202607190001",
  "tradeStatus": "SUCCESS",
  "timestamp": "1752912930",
  "nonce": "N202607190001",
  "signature": "base64-signature"
}
```

验签说明：

1. `timestamp` 必须在允许时间窗内
2. `nonce` 必须未被重复使用，当前已落到持久化表做跨实例防重放
3. 签名串按 `channel|paymentOrderId|tradeStatus|channelTransactionNo|timestamp|nonce` 组织，`channel` 会先统一规范化为小写编码
{
  "paymentOrderId": "PAY1752912340001",
  "tradeStatus": "SUCCESS",
  "channelTransactionNo": "WX2026071900001",
  "timestamp": "1752912345",
  "nonce": "abc123",
  "signature": "base64-hmac-sha256"
}
```

生产环境开启 `HSP_PAYMENT_CALLBACK_REQUIRE_SIGNATURE=true` 后，签名原文为：

`channel|paymentOrderId|tradeStatus|channelTransactionNo|timestamp|nonce`

使用渠道独立密钥计算 HMAC-SHA256，并将结果 Base64 编码后放入 `signature`。渠道密钥优先从支付渠道配置中读取，找不到时才回退到全局环境变量。

返回示例：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "paymentOrderId": "PAY1752912340001",
    "prepayOrderNo": "PRE1752912345678",
    "billNo": "BILL1752912341234",
    "orderNo": "ORD202607190003",
    "customerName": "张女士",
    "amount": "¥168.00",
    "paymentMethod": "微信支付",
    "channel": "WX_H5",
    "channelTransactionNo": "WX2026071900001",
    "status": "SUCCESS",
    "statusType": "success",
    "createdAt": "2026-07-19 10:15:30",
    "routeLogs": ["2026-07-19 10:16:01 | RULE_HOME_WX | 家政 H5 微信优先 -> wx_h5"],
    "notifyLogs": ["2026-07-19 10:16:33 | SUCCESS | 已收口"],
    "eventLogs": ["PAYMENT_SUCCESS | ORD202607190003 | 2026-07-19 10:16:33"]
  },
  "requestId": "REQ202607190004"
}
```

## 7. 主动查单

接口：`POST /api/payments/query`

请求示例：

```json
{
  "paymentOrderId": "PAY1752912340001"
}
```

说明：

当前版本已经抽象渠道查单适配器，但默认使用 `LOCAL_SIMULATION` 本地模拟适配器；
真实微信、支付宝、银行查单适配器由 `gateway-access` 接入后注册，不改变本接口契约。

返回要点：

1. `channelTransactionNo` 会返回本次查单得到的渠道流水号。
2. `querySource` 会返回查单结果来源，例如 `LOCAL_SIMULATION`、`WECHAT`、`ALIPAY`。

## 8. 关闭支付单

接口：`POST /api/payments/close`

请求示例：

```json
{
  "paymentOrderId": "PAY1752912340001",
  "reason": "ORDER_TIMEOUT"
}
```

说明：

关闭动作会：

1. 更新支付状态为 `CLOSED`
2. 写入 `PAYMENT_CLOSED` 事件
3. 返回最新支付详情

## 9. 查询支付单详情

接口：`GET /api/payments/{paymentOrderId}`

返回内容包含：

1. 支付基本信息
2. 预付单号
3. 账单号
4. 渠道路由轨迹
5. 回调轨迹
6. 事件轨迹

## 10. 收款记录分页查询

接口：`GET /api/payment-records`

查询参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `recordType` | 否 | `ALL`、`WECHAT`、`BANK_CARD`，默认 `ALL` |
| `userId` | 否 | 用户 ID 模糊查询 |
| `businessOrderNo` | 否 | 业务订单号模糊查询 |
| `paymentType` | 否 | 支付类型，例如 `消费支付` |
| `pageNo` | 否 | 页码，从 1 开始，默认 1 |
| `pageSize` | 否 | 每页条数，默认 20，最大 100 |

返回结构：

```json
{
  "items": [],
  "total": 3,
  "pageNo": 1,
  "pageSize": 20
}
```

## 11. 收款记录详情查询

接口：`GET /api/payment-records/{paymentOrderId}`

返回示例：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "paymentOrderId": "PAY1752912340001",
    "businessOrderNo": "ORD202607190003",
    "paymentRequestNo": "PAY1752912340001-REQ",
    "paymentGateway": "微信支付网关",
    "paymentChannel": "wx_h5",
    "paymentMethod": "微信支付",
    "paymentStatus": "支付成功",
    "paymentAmount": "¥168.00",
    "latestTerminal": "APP_WEB",
    "latestClientIp": "127.0.0.1",
    "latestIdempotencyKey": "PRE1752912345678|微信支付|WX_H5",
    "latestAttemptStatus": "SUCCESS",
    "latestAttemptStatusType": "success",
    "latestRequestPayload": "{\"paymentOrderId\":\"PAY1752912340001\",\"channelCode\":\"wx_h5\"}",
    "latestResponsePayload": "{\"tradeStatus\":\"SUCCESS\",\"channelTransactionNo\":\"WX2026071900001\"}",
    "routeLogs": [
      "2026-07-19 10:16:01 | RULE_HOME_WX | 家政 H5 微信优先 -> wx_h5"
    ],
    "notifyLogs": [
      "2026-07-19 10:16:33 | SUCCESS | 已收口"
    ],
    "eventLogs": [
      "PAYMENT_SUCCESS | ORD202607190003 | 2026-07-19 10:16:33"
    ]
  },
  "requestId": "REQ202607190010"
}
```

返回说明：

1. 该接口面向后台“支付记录详情”页面，补齐支付记录列表无法承载的报文和轨迹信息。
2. `latestRequestPayload` 与 `latestResponsePayload` 来自最近一次支付尝试记录。
3. `routeLogs`、`notifyLogs`、`eventLogs` 直接复用支付主链路轨迹表，便于运营与研发统一排障。

## 12. 退款管理接口

### 12.1 分页查询退款单

接口：`GET /api/refunds`

查询参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `refundOrderId` | 否 | 退款单号模糊查询 |
| `paymentOrderId` | 否 | 原支付单号模糊查询 |
| `refundStatus` | 否 | `全部`、`REVIEWING`、`PROCESSING`、`SUCCESS`、`FAIL` |
| `refundMethod` | 否 | `全部`、`原路退款`、`线下打款`、`退转付` |
| `pageNo` | 否 | 页码，从 1 开始 |
| `pageSize` | 否 | 每页条数，最大 100 |

### 12.2 发起退款申请

接口：`POST /api/refunds/apply`

请求示例：

```json
{
  "paymentOrderId": "PAY202607190001",
  "refundAmount": 68.00,
  "refundMethod": "原路退款",
  "refundReason": "客户取消服务"
}
```

业务约束：

1. 原支付单必须存在且状态为 `SUCCESS`。
2. `refundAmount` 必须大于 `0`。
3. 同一支付单下 `REVIEWING`、`PROCESSING`、`SUCCESS` 状态退款金额累计不能超过原支付金额。
4. 创建后状态为 `REVIEWING`，等待运营或财务审核。

### 12.3 退款动作接口

审核通过：

```http
POST /api/refunds/approve
```

模拟成功：

```http
POST /api/refunds/success
```

模拟失败：

```http
POST /api/refunds/fail
```

失败重试：

```http
POST /api/refunds/retry
```

请求示例：

```json
{
  "refundOrderId": "REF20260720223600123",
  "remark": "运营审核通过"
}
```

状态流转：

| 动作 | 原状态 | 目标状态 |
| --- | --- | --- |
| 审核通过 | `REVIEWING` | `PROCESSING` |
| 成功回调 | `PROCESSING` | `SUCCESS` |
| 失败回调 | `PROCESSING` | `FAIL` |
| 失败重试 | `FAIL` | `PROCESSING` |

实现说明：

1. 后端使用状态条件更新，避免重复点击、重复回调导致退款状态回退。
2. `SUCCESS` 会写入 `success_at`，`retry` 会清空成功时间。
3. 当前为本地模拟闭环，真实渠道退款请求、退款回调验签、退款渠道流水和差错补偿在后续渠道网关能力中扩展。

## 12. 支付配置中心接口

### 12.1 查询配置总览

接口：`GET /api/payment-config`

返回内容：

1. `channels`：支付渠道配置，包含渠道编码、渠道名称、支付方式、商户号、回调通知地址、验签密钥配置情况、适用场景、状态、单日限额、优先级。
2. `routeRules`：支付路由规则，包含规则编码、规则名称、匹配场景、匹配表达式、目标渠道、兜底渠道、状态、优先级。
3. `protocols`：支付协议配置，包含协议编码、协议名称、协议类型、模板版本、签约模式、适用场景、适用渠道、商户确认要求、风控标签、状态。
4. `returnCodeMappings`：渠道返回码映射，包含渠道编码、渠道返回码、渠道返回信息、标准状态、标准错误码、是否可重试、是否需要人工介入、适用处理策略、状态。

### 12.2 启停渠道或路由规则

启停渠道：

```http
POST /api/payment-config/channels/toggle
```

启停路由规则：

```http
POST /api/payment-config/route-rules/toggle
```

启停支付协议：

```http
POST /api/payment-config/protocols/toggle
```

启停渠道返回码映射：

```http
POST /api/payment-config/return-codes/toggle
```

请求示例：

```json
{
  "configCode": "wx_h5",
  "enabled": false
}
```

说明：

1. `enabled=true` 时状态更新为 `ENABLED`。
2. `enabled=false` 时状态更新为 `DISABLED`。
3. 协议配置当前已支持运营查看和启停，后续继续补充协议模板编辑、协议种类管理、签约要素配置和电子签章能力。
4. 返回码映射启停时，`configCode` 传渠道编码，`subCode` 传渠道返回码，例如 `WX_H5 + USERPAYING`。
5. 返回码映射当前已支持运营查看与启停，后续继续补返回码批量导入、映射版本治理、渠道差异比对和异常自动归档。
6. 当前 V1 已支持运营启停和配置展示，后续需要将真实路由算法从硬编码升级为按配置规则匹配。

## 13. 支付监控分析接口

### 13.1 查询监控总览

接口：`GET /api/payment-monitor/overview`

返回内容：

1. `trends`：最近 7 天支付趋势，包含日期、总单量、成功单量和成功金额。
2. `channelMetrics`：按渠道与支付方式统计的总单量、成功率、成功金额和待处理笔数。
3. `alerts`：待处理异常告警列表，当前包括回调待收口、退款失败、渠道停用三类。

## 14. 错误与边界说明

当前版本重点仍是开发与联调基线，但支付主链路已经补齐第一版业务错误码体系，前端可直接展示 `code + requestId` 做问题定位。

| 错误码 | 当前含义 |
| --- | --- |
| `PAYMENT-400` | 通用参数校验失败 |
| `PAYMENT-500` | 通用系统异常 |
| `PAYMENT-1001` | 预付单不存在 |
| `PAYMENT-1002` | 支付单不存在 |
| `PAYMENT-1003` | 当前支付方式无法命中可用路由渠道 |
| `PAYMENT-1004` | 未找到可用查单适配器 |
| `PAYMENT-1005` | 回调签名缺失或验签失败 |
| `PAYMENT-1006` | 回调密钥未配置 |
| `PAYMENT-1007` | 回调 `nonce` 重放 |
| `PAYMENT-1008` | 回调时间戳非法或超出时间窗 |
| `PAYMENT-1009` | 回调渠道缺失 |
| `PAYMENT-1010` | 当前没有启用的支付渠道 |
| `PAYMENT-1011` | 支付来源订单金额信息缺失 |

联调说明：

1. 页面侧接到失败响应时，应优先展示 `message`，并同时展示 `code` 与 `requestId`。
2. 用户端和后台当前都已按此口径展示错误信息，便于产品、开发、测试共享同一条问题线索。

## 15. 结论

这份接口文档当前已经可以直接指导前后端联调、测试编写和后续自动化补强，是 `payment-core` 一期支付交易闭环 V1 的正式接口基线。
