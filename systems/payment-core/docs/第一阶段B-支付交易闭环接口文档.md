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
| `/api/payment-metrics/summary` | `GET` | 查询支付成功率、成功金额和状态分布 |

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
    "channelCode": "WX_H5"
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
3. 记录路由日志
4. 记录支付尝试日志
5. 记录提交事件
6. 预写一条待回调日志

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
  "tradeStatus": "SUCCESS",
  "channelTransactionNo": "WX2026071900001",
  "timestamp": "1752912345",
  "nonce": "abc123",
  "signature": "base64-hmac-sha256"
}
```

生产环境开启 `HSP_PAYMENT_CALLBACK_REQUIRE_SIGNATURE=true` 后，签名原文为：

`channel|paymentOrderId|tradeStatus|channelTransactionNo|timestamp|nonce`

使用渠道独立密钥计算 HMAC-SHA256，并将结果 Base64 编码后放入 `signature`。

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
    "routeLogs": ["2026-07-19 10:16:01 | 默认渠道路由 | 微信支付"],
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

## 10. 错误与边界说明

## 10.1 收款记录分页查询

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

## 10.2 错误与边界说明

当前版本重点是开发与联调基线，尚未完整沉淀生产级错误码体系。建议后续统一补充：

| 错误码 | 建议含义 |
| --- | --- |
| `PAY001` | 订单不存在 |
| `PAY002` | 账单不存在 |
| `PAY003` | 预付单不存在 |
| `PAY004` | 支付单状态非法 |
| `PAY005` | 支付单已关闭 |
| `PAY006` | 回调验签失败 |
| `PAY007` | 重复回调 |

## 11. 结论

这份接口文档当前已经可以直接指导前后端联调、测试编写和后续自动化补强，是 `payment-core` 一期支付交易闭环 V1 的正式接口基线。
