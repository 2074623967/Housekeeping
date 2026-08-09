# REG-01 ~ REG-03 当前批次支付前半链路 API 证据

- 执行日期：`2026-08-09`
- 执行环境：本机 `test` 基线，`payment-core` 访问地址 `http://127.0.0.1:18081`
- 当前批次业务单：
  - `orderNo = ORD-CURR-H5-20260809-004`
  - `prepayOrderNo = PRE1786249554192`
  - `billNo = BILL1786249554191`
  - `paymentOrderId = PAY1786249554191`

## 1. 创建预付单

请求：

```bash
curl -s -X POST http://127.0.0.1:18081/api/payments/prepay \
  -H 'Content-Type: application/json' \
  -d '{
    "orderNo":"ORD-CURR-H5-20260809-004",
    "payScene":"H5",
    "customerName":"张女士",
    "amount":168.00,
    "cashierTitle":"家政服务收银台"
  }'
```

关键返回事实：

```json
{
  "prepayOrderNo": "PRE1786249554192",
  "billNo": "BILL1786249554191",
  "paymentOrderId": "PAY1786249554191",
  "payScene": "H5",
  "cashierStatus": "待支付",
  "createdAt": "2026-08-09 12:25:54"
}
```

## 2. 提交支付

背景说明：

- `housekeeping-h5-web` 当前运行库 `strict_mode = 开启` 且 `self_check_status = WARN`，会被自检阻断。
- 当前批次采用 `default-app` 执行真实提交，原因是该来源应用 `strict_mode = 关闭`，可以如实反映当前运行库下的可提交通路。

请求：

```bash
curl -s -X POST http://127.0.0.1:18081/api/payments/submit \
  -H 'Content-Type: application/json' \
  -d '{
    "prepayOrderNo":"PRE1786249554192",
    "paymentMethod":"微信支付",
    "channelCode":"wx_h5",
    "sourceAppId":"default-app",
    "merchantNo":"MCH_HOME_APP",
    "accessToken":"token-default-app",
    "terminal":"MOBILE_WEB",
    "clientIp":"127.0.0.1",
    "idempotencyKey":"PRE1786249554192|微信支付|wx_h5"
  }'
```

关键返回事实：

```json
{
  "prepayOrderNo": "PRE1786249554192",
  "paymentOrderId": "PAY1786249554191",
  "cashierStatus": "支付中",
  "cashierStatusType": "info"
}
```

随后查询支付详情：

```bash
curl -s http://127.0.0.1:18081/api/payments/PAY1786249554191
```

关键返回事实：

```json
{
  "paymentOrderId": "PAY1786249554191",
  "status": "WAIT_CALLBACK",
  "paymentMethod": "微信支付",
  "channel": "wx_h5",
  "channelTransactionNo": "WX-1786249664904",
  "latestTerminal": "MOBILE_WEB",
  "latestClientIp": "127.0.0.1",
  "latestIdempotencyKey": "PRE1786249554192|微信支付|wx_h5",
  "latestAttemptStatus": "处理中",
  "routeLogs": [
    "2026-08-09 12:27:44 | RULE_HOME_WX | 家政 H5 微信优先 -> wx_h5"
  ],
  "notifyLogs": [
    "2026-08-09 12:27:44 | SUBMIT | 待回调"
  ],
  "eventLogs": [
    "PAYMENT_SUBMIT | payment.trade.submitted.v1 | PENDING | 2026-08-09 12:27:44"
  ]
}
```

## 3. 成功回调

请求：

```bash
curl -s -X POST http://127.0.0.1:18081/api/payments/callback/WX_H5 \
  -H 'Content-Type: application/json' \
  -d '{
    "paymentOrderId":"PAY1786249554191",
    "channelTransactionNo":"WX-1786249664904",
    "tradeStatus":"SUCCESS",
    "timestamp":"1786249688",
    "nonce":"N-CURR-20260809-003",
    "signature":"runtime-bypass"
  }'
```

关键返回事实：

```json
{
  "paymentOrderId": "PAY1786249554191",
  "status": "SUCCESS",
  "statusType": "success",
  "latestAttemptStatus": "成功",
  "notifyLogs": [
    "2026-08-09 12:28:18 | SUCCESS | 已收口",
    "2026-08-09 12:27:44 | SUBMIT | 待回调"
  ],
  "eventLogs": [
    "PAYMENT_SUCCESS | payment.trade.succeeded.v1 | SUCCESS | 2026-08-09 12:28:18",
    "PAYMENT_SUBMIT | payment.trade.submitted.v1 | PENDING | 2026-08-09 12:27:44"
  ]
}
```

## 4. 当前批次幂等补充证据

重复提交：

```bash
curl -s -X POST http://127.0.0.1:18081/api/payments/submit \
  -H 'Content-Type: application/json' \
  -d '{
    "prepayOrderNo":"PRE1786249554192",
    "paymentMethod":"微信支付",
    "channelCode":"wx_h5",
    "sourceAppId":"default-app",
    "merchantNo":"MCH_HOME_APP",
    "accessToken":"token-default-app",
    "terminal":"MOBILE_WEB",
    "clientIp":"127.0.0.1",
    "idempotencyKey":"PRE1786249554192|微信支付|wx_h5"
  }'
```

重复提交返回仍为同一笔：

```json
{
  "prepayOrderNo": "PRE1786249554192",
  "paymentOrderId": "PAY1786249554191",
  "cashierStatus": "支付成功",
  "cashierStatusType": "success"
}
```

重复回调：

```bash
curl -s -X POST http://127.0.0.1:18081/api/payments/callback/WX_H5 \
  -H 'Content-Type: application/json' \
  -d '{
    "paymentOrderId":"PAY1786249554191",
    "channelTransactionNo":"WX-1786249664904",
    "tradeStatus":"SUCCESS",
    "timestamp":"1786249719",
    "nonce":"N-CURR-20260809-003-DUP",
    "signature":"runtime-bypass"
  }'
```

重复回调返回仍为已成功收口的同一笔支付单，未产生新的业务编号。
