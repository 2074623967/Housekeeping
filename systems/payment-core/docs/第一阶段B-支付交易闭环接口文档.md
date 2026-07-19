# 第一阶段B-支付交易闭环接口文档

## 1. 文档定位

本文档是 `payment-core` 在 `Sunday, July 19, 2026` 的第一阶段B接口文档草案。

目标：

- 明确支付交易闭环核心接口
- 为后续 `controller / service / serviceimpl / mapper / mapper.xml / entity` 开发提供接口基线

## 2. 接口列表

1. `POST /api/payments/prepay`
2. `GET /api/payments/cashier/{prepayOrderNo}`
3. `POST /api/payments/submit`
4. `POST /api/payments/callback/{channel}`
5. `POST /api/payments/query`
6. `POST /api/payments/close`
7. `GET /api/payments/{paymentOrderId}`

## 3. 创建预付单

接口：

`POST /api/payments/prepay`

说明：

- 根据订单号、账单号创建支付预付单

请求示例：

```json
{
  "orderNo": "ORD202607190003",
  "billNo": "BILL202607190001",
  "amount": 168.00,
  "sceneCode": "HOME_CLEAN",
  "clientType": "H5"
}
```

返回示例：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "prepayOrderNo": "PRE202607190001",
    "paymentOrderId": "PAY202607190003",
    "status": "PREPAY_CREATED",
    "expireTime": "2026-07-19 11:30:00"
  },
  "requestId": "REQ202607190001"
}
```

## 4. 获取收银台参数

接口：

`GET /api/payments/cashier/{prepayOrderNo}`

说明：

- 获取收银台展示数据和可用支付方式

返回示例：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "prepayOrderNo": "PRE202607190001",
    "paymentOrderId": "PAY202607190003",
    "orderNo": "ORD202607190003",
    "amount": "¥168.00",
    "defaultMethod": "wechat",
    "availableMethods": [
      {
        "method": "wechat",
        "methodName": "微信支付"
      },
      {
        "method": "alipay",
        "methodName": "支付宝"
      }
    ]
  },
  "requestId": "REQ202607190002"
}
```

## 5. 提交支付

接口：

`POST /api/payments/submit`

说明：

- 用户在收银台确认支付方式后发起真实支付提交

请求示例：

```json
{
  "prepayOrderNo": "PRE202607190001",
  "paymentMethod": "wechat",
  "clientType": "H5"
}
```

返回示例：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "paymentOrderId": "PAY202607190003",
    "attemptNo": "ATT202607190001",
    "status": "WAIT_CALLBACK",
    "channel": "wx_h5",
    "redirectUrl": "https://cashier.example.com/mock/wechat"
  },
  "requestId": "REQ202607190003"
}
```

## 6. 渠道回调

接口：

`POST /api/payments/callback/{channel}`

说明：

- 接收渠道异步回调并完成幂等收口

路径参数：

- `channel`：渠道编码，例如 `wx_h5`

返回建议：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "paymentOrderId": "PAY202607190003",
    "status": "SUCCESS"
  },
  "requestId": "REQ202607190004"
}
```

## 7. 主动查单

接口：

`POST /api/payments/query`

说明：

- 在回调缺失或状态不确定时主动向渠道查单

请求示例：

```json
{
  "paymentOrderId": "PAY202607190003"
}
```

## 8. 关闭支付单

接口：

`POST /api/payments/close`

说明：

- 关闭超时未支付或人工终止的支付单

请求示例：

```json
{
  "paymentOrderId": "PAY202607190003",
  "reason": "ORDER_TIMEOUT"
}
```

## 9. 查询支付单详情

接口：

`GET /api/payments/{paymentOrderId}`

说明：

- 后台查看支付单详情、尝试记录、回调日志和当前状态

## 10. 错误码建议

| 错误码 | 说明 |
| --- | --- |
| `PAY001` | 订单不存在 |
| `PAY002` | 账单不存在 |
| `PAY003` | 预付单不存在 |
| `PAY004` | 支付单状态非法 |
| `PAY005` | 渠道路由失败 |
| `PAY006` | 渠道提交失败 |
| `PAY007` | 渠道回调验签失败 |
| `PAY008` | 支付单已关闭 |

## 11. 结论

从 `Sunday, July 19, 2026` 开始，第一阶段B的接口已经具备明确边界，后续可以按这份文档继续推进 `controller、service、serviceimpl、mapper、mapper.xml、entity` 和数据库实现。
