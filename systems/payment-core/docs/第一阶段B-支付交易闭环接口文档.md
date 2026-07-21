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
| `/api/payment-flows` | `GET` | 查询统一支付流水排障台 |
| `/api/payment-routes` | `GET` | 查询支付路由执行结果台 |
| `/api/payment-requests` | `GET` | 查询支付请求管理台 |
| `/api/payment-logs` | `GET` | 查询支付处理日志台 |
| `/api/payment-records` | `GET` | 按支付维度分页查询收款记录 |
| `/api/payment-records/{paymentOrderId}` | `GET` | 查询单笔收款记录详情 |
| `/api/payment-metrics/summary` | `GET` | 查询支付成功率、成功金额和状态分布 |
| `/api/payment-events` | `GET` | 查询支付事件出站台账 |
| `/api/payment-events/republish` | `POST` | 手动重发支付事件 |
| `/api/payment-issues` | `GET` | 查询支付交易异常中心列表 |
| `/api/refunds` | `GET` | 分页查询退款单 |
| `/api/refunds/{refundOrderId}` | `GET` | 查询退款详情与操作日志 |
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
| `/api/payment-config/gateways/toggle` | `POST` | 启停支付网关接入配置 |
| `/api/payment-monitor/overview` | `GET` | 查询支付趋势、渠道表现和异常告警 |
| `/api/payment-day-end/overview` | `GET` | 查询支付日终处理总览与差异告警 |
| `/api/payment-day-end/run` | `POST` | 手动触发支付日终处理 |
| `/api/payment-task-center/overview` | `GET` | 查询支付任务中心总览 |
| `/api/payment-task-center/task-runs` | `GET` | 查询支付任务执行日志 |
| `/api/payment-task-center/close-expired-payments` | `POST` | 手动执行超时关单 |
| `/api/payment-task-center/republish-failed-events` | `POST` | 手动执行失败事件重发 |
| `/api/payment-task-center/retry-failed-refunds` | `POST` | 手动执行失败退款重试 |

## 3. 支付流水排障台查询

接口：`GET /api/payment-flows`

查询参数：

| 参数 | 说明 |
| --- | --- |
| `paymentOrderId` | 支付单号，模糊匹配 |
| `orderNo` | 订单号，模糊匹配 |
| `flowType` | 流水类型，支持 `支付尝试 / 渠道回调 / 路由记录 / 业务事件 / 全部` |
| `channelCode` | 渠道编码，模糊匹配 |
| `terminal` | 终端，支持 `H5 / PC / APP / 小程序 / 全部` |
| `businessStatus` | 业务状态，支持按尝试状态、回调状态、路由结果和事件类型统一筛选 |
| `keyword` | 关键字，按摘要、请求报文、响应报文模糊匹配 |
| `sortField` | 排序字段，支持 `createdAt / retryCount / flowType` |
| `sortOrder` | 排序方向，支持 `asc / desc` |
| `pageNo` | 页码，从 `1` 开始 |
| `pageSize` | 每页条数，最大 `100` |

返回示例：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "items": [
      {
        "flowNo": "ATT202607190002",
        "paymentOrderId": "PAY202607190002",
        "orderNo": "ORD202607190002",
        "prepayOrderNo": "PRE202607190002",
        "flowType": "支付尝试",
        "flowTypeTag": "info",
        "channelCode": "alipay_h5",
        "terminal": "H5",
        "clientIp": "127.0.0.1",
        "idempotencyKey": "PRE202607190002|支付宝|alipay_h5",
        "businessStatus": "等待回调",
        "businessStatusType": "warn",
        "notifyType": null,
        "routeRule": null,
        "downstreamSystem": null,
        "eventTopic": null,
        "publishStatus": null,
        "retryCount": null,
        "requestPayload": "{\"scene\":\"H5\",\"amount\":6800.00}",
        "responsePayload": "{\"payUrl\":\"https://pay.example.com/ali/2\"}",
        "summary": "方式=支付宝 / 请求={\"scene\":\"H5\",\"amount\":6800.00}",
        "createdAt": "2026-07-19 10:03:11"
      }
    ],
    "total": 1,
    "pageNo": 1,
    "pageSize": 20
  },
  "requestId": "REQ202607200021"
}
```

业务说明：

1. 当前统一聚合支付尝试、渠道回调、路由记录、业务事件四类过程流水。
2. 不同流水类型会按统一字段口径返回深度排障信息，例如终端、IP、幂等键、回调类型、路由规则、下游系统、事件主题、发布状态和重试次数。
3. 前端可直接使用 `requestPayload`、`responsePayload` 展开原始报文区，无需再拼接二次接口。

## 4. 支付路由执行结果台查询

接口：`GET /api/payment-routes`

查询参数：

| 参数 | 说明 |
| --- | --- |
| `paymentOrderId` | 支付单号，模糊匹配 |
| `orderNo` | 订单号，模糊匹配 |
| `routeRule` | 路由规则关键字，模糊匹配 |
| `channelCode` | 渠道编码，模糊匹配 |
| `paymentMethod` | 支付方式，支持 `全部 / 微信 / 支付宝 / 银行转账` |
| `terminal` | 终端，支持 `全部 / H5 / PC / APP / 小程序` |
| `routeResult` | 路由结果，支持 `全部` |
| `sortField` | 排序字段，支持 `createdAt / channelCode / routeResult` |
| `sortOrder` | 排序方向，支持 `asc / desc` |
| `pageNo` | 页码，从 `1` 开始 |
| `pageSize` | 每页条数，最大 `100` |

返回示例：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "items": [
      {
        "routeNo": "RTR202607190002",
        "paymentOrderId": "PAY202607190002",
        "orderNo": "ORD202607190002",
        "prepayOrderNo": "PRE202607190002",
        "customerName": "王先生",
        "amount": "¥2000.00",
        "paymentMethod": "支付宝",
        "channelCode": "alipay_h5",
        "routeRule": "amount>1000 => alipay",
        "routeResult": "支付宝H5",
        "routeResultType": "success",
        "terminal": "H5",
        "clientIp": "127.0.0.1",
        "idempotencyKey": "PRE202607190002|支付宝|alipay_h5",
        "requestPayload": "{\"scene\":\"H5\",\"amount\":6800.00}",
        "responsePayload": "{\"payUrl\":\"https://pay.example.com/ali/2\"}",
        "createdAt": "2026-07-19 10:03:02"
      }
    ],
    "total": 1,
    "pageNo": 1,
    "pageSize": 20
  },
  "requestId": "REQ202607200031"
}
```

业务说明：

1. 当前按支付路由记录聚合支付单、订单、预付单和最近一次支付请求上下文。
2. 前端可直接用本接口查看路由规则、命中渠道、路由结果以及请求/响应报文，无需再跳多页拼装。
3. 当前仍是 V1 版本，后续可继续补权重、熔断、命中解释和渠道健康度信息。

## 5. 支付请求管理查询

接口：`GET /api/payment-requests`

查询参数：

| 参数 | 说明 |
| --- | --- |
| `requestNo` | 请求编号，模糊匹配 |
| `paymentOrderId` | 支付单号，模糊匹配 |
| `orderNo` | 订单号，模糊匹配 |
| `channelCode` | 渠道编码，模糊匹配 |
| `terminal` | 终端，支持 `全部 / H5 / PC / APP / 小程序` |
| `clientIp` | 客户端 IP，模糊匹配 |
| `requestStatus` | 请求状态，支持 `全部 / 请求已发起 / 请求成功 / 请求失败 / 已关闭` |
| `sortField` | 排序字段，支持 `createdAt / channelCode / terminal` |
| `sortOrder` | 排序方向，支持 `asc / desc` |
| `pageNo` | 页码，从 `1` 开始 |
| `pageSize` | 每页条数，最大 `100` |

业务说明：

1. 当前用于查看支付尝试、渠道请求报文、响应报文以及路由结果的统一台账。
2. 前端可通过 `clientIp + terminal + channelCode` 组合快速反查同一批终端流量。
3. 生产环境仍需叠加报文字段脱敏和权限分域控制。

## 6. 支付处理日志查询

接口：`GET /api/payment-logs`

查询参数：

| 参数 | 说明 |
| --- | --- |
| `paymentOrderId` | 支付单号，模糊匹配 |
| `orderNo` | 订单号，模糊匹配 |
| `processStage` | 处理阶段，支持 `全部 / 支付提交 / 支付路由 / 渠道回调 / 业务事件` |
| `logLevel` | 日志级别，支持 `全部 / INFO / WARN / ERROR` |
| `source` | 来源，模糊匹配 |
| `keyword` | 日志关键字，模糊匹配 |
| `sortField` | 排序字段，支持 `createdAt / logLevel / processStage` |
| `sortOrder` | 排序方向，支持 `asc / desc` |
| `pageNo` | 页码，从 `1` 开始 |
| `pageSize` | 每页条数，最大 `100` |

业务说明：

1. 当前统一收敛支付提交、支付路由、渠道回调和业务事件四类处理日志。
2. 排序能力主要用于运营优先查看最新错误、按阶段集中复核问题单。
3. 生产环境后续接日志检索、链路追踪和告警平台时，本接口可继续作为业务态排障入口。

## 7. 支付事件出站查询与重发

查询接口：`GET /api/payment-events`

重发接口：`POST /api/payment-events/republish`

查询参数：

| 参数 | 说明 |
| --- | --- |
| `paymentOrderId` | 支付单号，模糊匹配 |
| `eventType` | 事件类型，支持 `全部` |
| `publishStatus` | 发布状态，支持 `全部` |
| `downstreamSystem` | 下游系统，支持 `全部` |
| `eventTopic` | 事件主题关键字，模糊匹配 |
| `sortField` | 排序字段，支持 `createdAt / retryCount / nextRetryAt` |
| `sortOrder` | 排序方向，支持 `asc / desc` |
| `pageNo` | 页码，从 `1` 开始 |
| `pageSize` | 每页条数，最大 `100` |

重发请求示例：

```json
{
  "eventNo": "EVT202607190002"
}
```

业务说明：

1. 重发接口会沿用当前查询条件返回最新分页结果，前端无需额外再发一次查询。
2. 当前主要支撑支付成功、支付关闭、退款等出站事件的可见化与手动补偿入口。
3. 后续若接入真实消息总线，需要继续补投递批次、订阅确认和死信处理字段。

## 8. 创建预付单

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

## 9. 获取收银台参数

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

## 10. 提交支付

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
3. 路由执行前会校验渠道适用场景，当前按 `payScene / terminal / customerType` 与渠道 `sceneScope` 做匹配，不匹配的渠道不会进入本次提交。
4. 路由执行前会校验渠道单日限额，当前按“当日已受理金额 + 本次请求金额 <= dailyLimit”判断，超限渠道不会进入本次提交。
5. 若前端强制指定的渠道已停用、场景不匹配或超过单日限额，接口会直接返回业务错误，不再静默降级到其他渠道。
6. 若规则目标渠道被停用、场景不匹配或超过单日限额，则自动尝试规则配置的兜底渠道。
7. 当前内置规则示例：
   - `RULE_HOME_WX`：家政 H5 小额微信优先
   - `RULE_HOME_ALI`：家政 H5 小额支付宝优先
   - `RULE_ENTERPRISE_BANK`：企业或大额订单优先走线下银行

幂等说明：

1. 当同一预付单重复提交时，如果支付单已进入 `WAIT_CALLBACK`、`SUCCESS` 或 `CLOSED`，接口直接返回当前预付单信息。
2. 如果幂等键已存在，接口同样直接返回当前预付单信息。
3. 当前提交链路还会先对预付单执行“待支付 -> 支付中”的条件占位更新，只有首次占位成功的请求才允许继续调用渠道适配器。
4. 如果并发请求发现预付单已经被其他线程占位，接口会优先返回最新预付单状态，避免重复路由、重复下单和重复待回调日志。
5. 这样可以避免按钮重复点击、浏览器重试和并发接口重放导致重复路由、重复尝试和重复待回调日志。

## 8. 模拟回调

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

当前版本已经抽象渠道查单适配器，并按 `wx_h5 / alipay_h5 / offline_bank / local-fallback` 做了第一版拆分；
真实微信、支付宝、银行查单适配器由 `gateway-access` 接入后注册，不改变本接口契约。

返回要点：

1. `channelTransactionNo` 会返回本次查单得到的渠道流水号。
2. `querySource` 会返回查单结果来源，例如 `WECHAT_SIMULATION`、`ALIPAY_SIMULATION`、`OFFLINE_BANK_SIMULATION`、`LOCAL_SIMULATION_FALLBACK`。

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
4. 最近一次支付尝试信息：终端、客户端 IP、幂等键、尝试状态、请求报文、响应报文
5. 渠道路由轨迹
6. 回调轨迹
7. 事件轨迹

## 13. 收款记录分页查询

接口：`GET /api/payment-records`

当前补充能力：

1. 已支持 `paymentStatus`、`paymentChannel` 两类运营筛选。
2. 已支持按 `createdAt / paymentAmount / paidAt` 排序。
3. 适用于统一支付记录、微信支付宝支付记录和银行卡支付记录三类列表页共用。

接口：`GET /api/bills`

当前补充能力：

1. 已支持 `customerName` 筛选。
2. 已支持按 `createdAt / dueAt / billAmount / unpaidAmount` 排序。

接口：`GET /api/cashier-sessions`

当前补充能力：

1. 已支持 `paymentOrderId`、`customerName` 筛选。
2. 已支持按 `createdAt / expiresAt / amount` 排序。

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

## 14. 收款记录详情查询

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

### 12.3 查询退款详情

接口：`GET /api/refunds/{refundOrderId}`

返回内容：

1. 退款基础信息：退款单号、原支付单号、原订单号、客户名称、退款金额、退款方式、退款原因、状态、申请时间、成功时间。
2. 原支付快照：原支付金额、原支付方式、原支付状态。
3. `operationLogs`：退款操作日志，覆盖发起申请、审核通过、成功回调、失败回调、失败重试以及任务中心批量重试等动作。

### 12.4 退款动作接口

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
3. 退款原因会落在退款单上，操作备注会进入退款操作日志，便于财务、运营、测试和研发统一复盘。
4. 当前为本地模拟闭环，真实渠道退款请求、退款回调验签、退款渠道流水和差错补偿在后续渠道网关能力中扩展。

## 12. 支付配置中心接口

### 12.1 查询配置总览

接口：`GET /api/payment-config`

返回内容：

1. `channels`：支付渠道配置，包含渠道编码、渠道名称、支付方式、商户号、回调通知地址、验签密钥配置情况、适用场景、状态、单日限额、优先级。
2. `routeRules`：支付路由规则，包含规则编码、规则名称、匹配场景、匹配表达式、目标渠道、兜底渠道、状态、优先级。
3. `protocols`：支付协议配置，包含协议编码、协议名称、协议类型、模板版本、签约模式、适用场景、适用渠道、商户确认要求、风控标签、状态。
4. `returnCodeMappings`：渠道返回码映射，包含渠道编码、渠道返回码、标准错误码、标准错误文案、处理建议、是否可重试、是否需要人工介入、映射版本、归档状态和启停状态。
5. `gateways`：支付网关接入配置，包含网关编码、网关名称、接入模式、适用渠道、环境范围、网关基础地址、报文协议、签名算法、证书别名、证书状态、发布阶段、灰度策略、回调白名单、适配器编排、超时时间、重试策略和状态。

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

启停支付网关：

```http
POST /api/payment-config/gateways/toggle
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
3. 协议配置当前已支持运营查看、启停、协议种类字典、协议模板编码/名称维护、签约要素配置、协议正文编辑和电子签章服务商维护；后续继续补电子签章联调能力。
4. 返回码映射启停时，`configCode` 传渠道编码，`subCode` 传渠道返回码，例如 `WX_H5 + USERPAYING`。
5. 返回码映射当前已支持运营查看、启停、人工介入判断、映射版本和归档状态展示；后续继续补返回码批量导入、渠道差异比对和真正自动归档动作。
6. 支付网关启停时，`configCode` 传网关编码，例如 `GATEWAY_WX_ACQ`；当前已支持网关治理台账、启停、环境范围、证书状态、发布阶段、灰度策略、回调白名单和适配器编排展示，后续继续补证书轮换动作、真实灰度发布编排和渠道探活联调。
7. 当前 V1 已支持运营启停和配置展示，后续需要将真实路由算法从硬编码升级为按配置规则匹配。

## 13. 支付监控分析接口

### 13.1 查询监控总览

接口：`GET /api/payment-monitor/overview`

返回内容：

1. `summary`：支付监控摘要卡片数据，包含支付单总量、成功笔数、成功率、成功金额、待回调支付单量、退款失败笔数、停用渠道数和异常告警数。
2. `trends`：最近 7 天支付趋势，包含日期、总单量、成功单量和成功金额。
3. `channelMetrics`：按渠道与支付方式统计的总单量、成功率、成功金额和待处理笔数，并补充 `riskLevel`、`riskLevelType`、`riskHint` 便于前端直接展示风险标签和说明。
4. `alerts`：待处理异常告警列表，当前包括回调待收口、退款失败、渠道停用三类，并补充 `suggestedAction` 和 `actionRoute` 便于前端一键跳转到排障页。

返回示例：

```json
{
  "summary": {
    "totalCount": 268,
    "successCount": 247,
    "successRate": "92.16%",
    "successAmount": "¥18,932.00",
    "pendingPaymentCount": 12,
    "failedRefundCount": 3,
    "disabledChannelCount": 1,
    "alertCount": 4
  },
  "trends": [
    {
      "statDate": "2026-07-20",
      "totalCount": 38,
      "successCount": 35,
      "successAmount": "¥2,866.00"
    }
  ],
  "channelMetrics": [
    {
      "channelCode": "WX_H5",
      "paymentMethod": "WECHAT_H5",
      "totalCount": 72,
      "successCount": 65,
      "successRate": "90.28%",
      "successAmount": "¥5,880.00",
      "pendingCount": 4,
      "riskLevel": "中风险",
      "riskLevelType": "warn",
      "riskHint": "存在 4 笔待回调支付，建议优先查单"
    }
  ],
  "alerts": [
    {
      "alertType": "WAIT_CALLBACK",
      "alertTitle": "待回调支付积压",
      "alertMessage": "存在支付结果未收口的交易",
      "alertLevel": "高",
      "alertLevelType": "danger",
      "affectedCount": 12,
      "suggestedAction": "进入支付交易异常中心，优先核对待回调支付单、回调日志和查单结果",
      "actionRoute": "/payment-issues?issueType=待回调未收口"
    }
  ]
}
```

### 13.2 查询支付交易异常中心

接口：`GET /api/payment-issues`

请求参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `paymentOrderId` | `string` | 支付单号，模糊查询 |
| `orderNo` | `string` | 订单号，模糊查询 |
| `issueType` | `string` | 异常类型，默认 `全部` |
| `severity` | `string` | 严重等级，默认 `全部` |
| `channelCode` | `string` | 渠道编码，模糊查询 |
| `paymentMethod` | `string` | 支付方式，默认 `全部` |
| `pageNo` | `int` | 页码，默认 `1` |
| `pageSize` | `int` | 每页条数，默认 `20`，最大 `100` |

返回内容：

1. 聚合 `待回调未收口`、`回调处理待跟进`、`下游事件发布失败`、`命中停用渠道` 四类异常。
2. 每条异常返回支付单、订单、客户、支付方式、渠道、异常类型、严重等级、支付状态、异常摘要、根因提示、建议动作和推荐跳转路由。
3. 前端通过 `recommendedRoute` 直接进入支付单详情、支付处理日志、支付事件出站或支付配置页。

返回示例：

```json
{
  "items": [
    {
      "issueNo": "ISSUE-WAIT-PAY202607190002",
      "paymentOrderId": "PAY202607190002",
      "orderNo": "ORD202607190002",
      "customerName": "王先生",
      "paymentMethod": "支付宝",
      "channelCode": "alipay_h5",
      "issueType": "待回调未收口",
      "issueTypeTag": "danger",
      "severity": "P1",
      "severityType": "danger",
      "paymentStatus": "WAIT_CALLBACK",
      "paymentStatusType": "warn",
      "issueSummary": "支付单仍处于待回调状态，最新回调结果=待处理",
      "rootCauseHint": "最近支付尝试=等待回调；建议优先主动查单并核对回调验签与状态收口。",
      "recommendedAction": "查看支付单并核对回调、日志和查单结果",
      "recommendedRoute": "/payments/PAY202607190002",
      "createdAt": "2026-07-19 10:03:01"
    }
  ],
  "total": 2,
  "pageNo": 1,
  "pageSize": 20
}
```

### 13.3 查询支付任务中心总览

接口：`GET /api/payment-task-center/overview`

返回内容：

1. `expiredPaymentCount`：待超时关单数量。
2. `pendingCallbackCount`：待收口支付中数量。
3. `failedEventCount`：失败事件数量。
4. `failedRefundCount`：失败退款数量。
5. `warningDayEndBatchCount`：日终告警批次数。
6. `focusAlerts`：重点任务告警，包含告警标题、告警等级、影响数量和建议跳转路由。
7. `recentTaskRuns`：最近 10 条任务执行日志。

### 13.4 查询支付任务执行日志

接口：`GET /api/payment-task-center/task-runs`

查询参数：

| 参数 | 说明 |
| --- | --- |
| `taskCode` | 任务编码，支持精确匹配 |
| `runMode` | 运行方式，支持 `全部 / AUTO / MANUAL` |
| `taskStatus` | 任务状态，支持 `全部 / SUCCESS / WARNING` |
| `severityLevel` | 严重等级，支持 `全部 / P1 / P2 / P3` |
| `pageNo` | 页码，从 `1` 开始 |
| `pageSize` | 每页条数，最大 `100` |

业务说明：

1. 当前日志已统一落地自动调度和人工触发两类来源，便于区分 `AUTO / MANUAL`。
2. 每条日志返回严重等级、升级状态、建议动作和推荐路由，便于产品、研发、测试共用同一运维口径。
3. 严重等级与升级状态不再按“是否失败”简单二分，而是按任务类型、失败笔数和处理规模综合判定：
   - `PAYMENT_EXPIRE_CLOSE`：失败 `>= 5` 或处理量 `>= 30` 判定 `P1 / 升级值班负责人`；失败 `1-4` 或处理量 `10-29` 判定 `P2 / 纳入当班跟进`
   - `PAYMENT_EVENT_RETRY`：失败 `>= 3` 或处理量 `>= 10` 判定 `P1 / 升级值班负责人`；失败 `1-2` 或存在成功重发则判定 `P2 / 纳入当班跟进`
   - `REFUND_FAIL_RETRY`：失败 `>= 2` 或处理量 `>= 8` 判定 `P1 / 升级值班负责人`；失败 `1` 笔或存在成功重试则判定 `P2 / 纳入当班跟进`
4. 当前超时关单、失败事件重发、失败退款重试都由该页统一留痕，且失败事件和失败退款已接入后台自动补偿调度，后续可继续扩展更多支付运维任务。

### 13.5 查询支付日终处理总览

接口：`GET /api/payment-day-end/overview`

返回内容：

1. `totalBatchCount / completedBatchCount / abnormalBatchCount / latestBizDate`：日终批次汇总。
2. `openChannelAbnormalCount / openInternalAbnormalCount / openPendingRefundCount`：当前未收口差异计数。
3. `latestChannelSuccessCount / latestChannelSuccessAmount / latestInternalSuccessCount / latestInternalSuccessAmount`：最近批次渠道成功与内部事件成功事实。
4. `latestPaymentSuccessGapCount / latestPaymentSuccessGapAmount / latestPendingRefundAmount`：最近批次支付成功差异和待收口退款金额事实。
5. `reconciliationReadinessStatus / reconciliationReadinessType / reconciliationReadinessSummary / reconciliationSuggestedAction / reconciliationBlockingOwner`：当前是否可进入正式对账的统一准入口径、建议动作和责任归口。
6. `alerts`：当前差异告警，至少覆盖渠道回调未收口、内部事件未收口、退款待收口三类。
7. `recentBatches`：最近日终批次列表，供财务和运营回看业务日执行结果，并返回每个批次的 `reconciliationReadinessStatus`。

业务说明：

1. 当前接口定位是“对账前置事实收口”，不是正式财务对账差异闭环系统。
2. 当前日终批次会同时沉淀支付成功事实、渠道成功收口事实、内部事件成功事实、成功差异事实和待收口退款金额。
3. 对账准入口径优先判断“主链路事实是否收口”：只要存在渠道异常、内部事件异常或支付成功差异，就应直接判定“禁止进入对账”。
4. 如果主链路事实已收口但仍有退款待收口，则可判定为“有条件进入对账”，由财务在正式对账时同步关注逆向资金差异。
5. 差异告警的 `actionRoute` 直接指向异常中心、事件出站页或退款页，便于次日快速排查。
6. 后续如果接入渠道对账文件和账务分录核对，可继续在该接口上扩展更细粒度差异事实。

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
