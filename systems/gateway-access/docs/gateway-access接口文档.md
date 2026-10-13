# gateway-access 接口文档

## 1. 总览

### `GET /api/gateway-access/summary`

返回：

- `metrics`
- `highlights`

## 2. 接入应用

### `GET /api/gateway-access/applications`
### `POST /api/gateway-access/applications/toggle`

## 3. 网关渠道

### `GET /api/gateway-access/gateways`
### `POST /api/gateway-access/gateways/toggle`

## 4. 证书管理

### `GET /api/gateway-access/certificates`
### `POST /api/gateway-access/certificates/toggle`

启用门禁：

- 已过期证书不允许重新启用
- 证书归属网关未启用时，不允许单独启用证书

## 5. 接入权限

### `GET /api/gateway-access/permissions`
### `POST /api/gateway-access/permissions/toggle`

启用门禁：

- 权限归属应用未启用时，不允许单独启用权限

## 6. 调用方审计

### `GET /api/gateway-access/audit-logs`

支持按请求流水号、应用编码和结果状态筛选，返回签名算法、来源 IP、调用结果和风险提示。

### `GET /api/gateway-access/audit-logs/export`

导出当前筛选条件下的调用方审计快照，便于排障、审计和留档。

## 7. 灰度发布路由

### `GET /api/gateway-access/release-routes`
### `POST /api/gateway-access/release-routes/toggle`

支持按环境和状态筛选，用于管理 `PROD / GRAY / UAT` 发布路由、流量占比和发布窗口。

启用门禁：

- 灰度路由归属网关未启用时，不允许启用路由

## 8. 支付订单提交代理

### `POST /api/gateway/payment-orders/submit`

请求头与主动查单接口一致，必须提供 `X-Gateway-App-Code` 和 `X-Gateway-Access-Key`。

请求体：

```json
{
  "paymentOrderId": "PAY202609100001",
  "prepayOrderNo": "PRE202609100001",
  "orderNo": "ORD202609100001",
  "payScene": "HOME_SERVICE",
  "customerName": "客户姓名",
  "amount": 168.00,
  "paymentMethod": "wechat",
  "sourceAppId": "APP-H5",
  "requestedChannelCode": "wx_h5",
  "resolvedChannelCode": "wx_h5",
  "terminal": "H5",
  "clientIp": "127.0.0.1",
  "idempotencyKey": "stable-submit-key"
}
```

成功响应必须同时包含 `channelTransactionNo / attemptStatus / attemptStatusType / channelReturnCode`。当终端为 `MINI_PROGRAM` 且上游已经完成微信签名时，可额外返回完整的 `nativePaymentParams`：`timeStamp / nonceStr / package / signType / paySign`；网关只透传这五个白名单字段，不落库，缺字段、额外字段或控制字符均拒绝。金额必须为正数，最多 `16` 位整数和 `2` 位小数；文本字段执行必填、长度和 ISO 控制字符校验，显式传入的可选 `customerName / requestedChannelCode` 也先校验原文再将纯空白规范为空值。空响应、业务失败或必要字段缺失均不视为已明确受理。

提交与查单复用相同的应用、凭证、权限、网关、有效证书和当前环境发布路由门禁。上游异常只记录请求号和异常类型，并返回 `GATEWAY_UPSTREAM_UNAVAILABLE`；调用方必须先查单再决定后续动作，禁止因超时或未知结果自动重提。

提交上游由以下环境变量显式配置，默认均为空：

| 环境变量 | 渠道 |
| --- | --- |
| `HSP_GATEWAY_WX_PAYMENT_SUBMIT_URL` | 微信 H5 |
| `HSP_GATEWAY_ALIPAY_PAYMENT_SUBMIT_URL` | 支付宝 H5 |
| `HSP_GATEWAY_BANK_PAYMENT_SUBMIT_URL` | 线下银行 |

每次请求尽力写入 `PAYMENT_SUBMIT` 最小审计，不保存访问密钥、请求报文或上游原始响应。当前接口是面向渠道集成服务的稳定代理边界，不代表微信、支付宝或银行真实协议、签名验签、生产密钥托管、来源 IP 白名单、灰度算法、资金到账或对账已经完成。

## 9. 支付订单主动查单代理

### `POST /api/gateway/payment-orders/query`

请求头：

| 请求头 | 必填 | 说明 |
| --- | --- | --- |
| `X-Gateway-App-Code` | 是 | 调用应用编码，当前部署默认绑定 `APP_PAY_CORE` |
| `X-Gateway-Access-Key` | 是 | 部署环境注入的访问凭证，不写入代码、响应或审计日志 |

请求体：

```json
{
  "paymentOrderId": "PAY202609100001",
  "orderNo": "ORD202609100001",
  "channelCode": "wx_h5",
  "channelTransactionNo": "WX202609100001"
}
```

成功响应中的 `data` 只透传配置上游返回的确定性渠道事实，包含 `tradeStatus`、`channelTransactionNo`、`querySource` 以及可选返回码标准化字段。接口不会从 gateway-access 本地台账推导支付状态，也不会生成模拟成功结果。
代理仅接受 payment-core 支持的交易状态（`PREPAY_CREATED`、`RISK_REVIEW`、`RISK_BLOCKED`、`RISK_REJECTED`、`WAIT_CALLBACK`、`SUCCESS`、`CLOSED`、`FAIL`），并在成功审计前校验响应字段原文长度与控制字符；未知状态或越界字段返回 `GATEWAY_UPSTREAM_INVALID_RESPONSE` 并记失败审计。请求中可选的 `channelTransactionNo` 即使只有空白，也先执行原文长度及 ISO 控制字符校验，再将合法纯空白规范为空值以按支付单号查单。

代理前依次校验：

1. 调用应用编码与访问凭证匹配。
2. 接入应用与权限均处于启用状态，且权限归属当前应用。
3. 渠道编码已映射到网关，网关处于启用状态。
4. 网关存在启用且未过期的证书。
5. 当前环境存在启用且流量占比大于 `0` 的发布路由。
6. 目标渠道已配置真实支付查单上游 URL。

校验或上游调用失败时只返回稳定错误码与最小消息；上游原始异常、访问密钥和敏感报文不进入响应或审计。每次请求尽力写入 `PAYMENT_QUERY` 审计记录，审计失败不把已经取得的渠道交易事实改写成其他状态。

关键配置：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `HSP_GATEWAY_PAYMENT_APP_CODE` | `APP_PAY_CORE` | 允许调用的应用编码 |
| `HSP_GATEWAY_PAYMENT_PERMISSION_CODE` | `PERM_PAYMENT_CORE` | 查单前校验的权限编码 |
| `HSP_GATEWAY_PAYMENT_ACCESS_KEY` | 空 | 访问凭证；空值时失败闭合 |
| `HSP_GATEWAY_PAYMENT_ENVIRONMENT` | `PROD` | 发布路由环境 |
| `HSP_GATEWAY_PAYMENT_QUERY_TIMEOUT_MS` | `2000` | 上游连接与读取超时 |
| `HSP_GATEWAY_WX_PAYMENT_QUERY_URL` | 空 | 微信渠道真实查单集成地址 |
| `HSP_GATEWAY_ALIPAY_PAYMENT_QUERY_URL` | 空 | 支付宝渠道真实查单集成地址 |
| `HSP_GATEWAY_BANK_PAYMENT_QUERY_URL` | 空 | 银行渠道真实查单集成地址 |

默认访问凭证和三个上游 URL 均为空，因此仓库默认配置不能调用外部渠道。当前接口是稳定代理边界，不代表微信、支付宝或银行协议适配、生产密钥托管、来源 IP 白名单执行、真实网络联通、资金到账或财务验收已经完成。

## 10. 退款订单主动查单代理

### `POST /api/gateway/refunds/query`

请求头与支付交易代理一致，必须提供 `X-Gateway-App-Code` 和 `X-Gateway-Access-Key`。

请求体：

```json
{
  "refundOrderId": "REF202609100001",
  "refundAttemptNo": "RFA202609100001",
  "channelCode": "wx_h5",
  "channelRefundNo": "WX-REFUND-202609100001"
}
```

`refundOrderId / refundAttemptNo / channelCode` 必填。`refundAttemptNo` 是该轮退款渠道请求的商户侧身份，必须与提交时使用的值一致。已有渠道受理事实时必须传 `channelRefundNo`；退款提交结果未知且尚未取得渠道退款号时允许省略该字段，由上游按退款尝试号和平台退款单号核查。显式传入的可选字段仍执行长度和 ISO 控制字符校验。

成功响应中的 `data` 必须包含 `refundStatus / channelRefundNo`，`querySource` 可由代理在上游省略或纯空白时补为 `GATEWAY_ACCESS_PROXY`。`refundStatus` 只允许 `PROCESSING / SUCCESS / FAIL`；请求已携带 `channelRefundNo` 时，响应必须与其完全一致；请求未携带时，响应仍必须返回非空渠道退款号，供支付核心首次绑定可信受理事实。上游显式提供的 `querySource` 即使只有空白也先执行原文长度和 ISO 控制字符校验；缺失必填事实、超长、含 ISO 控制字符、状态非法或已有关联键不一致均返回 `GATEWAY_UPSTREAM_INVALID_RESPONSE`，不会修改为其他退款事实。

受控上游响应还必须原样回显 `data.refundOrderId` 与 `data.refundAttemptNo`，分别与本次请求的平台退款单号、单轮商户退款请求号精确相同；网关代理不得自行根据请求补写缺失的身份字段。任何一项缺失、串单或跨轮次，即使 `channelRefundNo` 尚未知且业务码为 `0`，也返回 `GATEWAY_UPSTREAM_INVALID_RESPONSE`，不得把别的退款受理事实绑定至本单。旧版只回状态/渠道号的上游不兼容，正式切换需先完成渠道协议映射、混版治理和受控验证。

退款查单复用支付交易代理的应用、访问凭证、权限、网关、有效证书和当前环境发布路由门禁，并写入 `REFUND_QUERY` 最小审计。访问密钥、请求报文、上游原始响应和异常原文不进入审计日志。

退款查单上游由以下环境变量显式配置，默认均为空：

| 环境变量 | 渠道 |
| --- | --- |
| `HSP_GATEWAY_WX_REFUND_QUERY_URL` | 微信 H5 |
| `HSP_GATEWAY_ALIPAY_REFUND_QUERY_URL` | 支付宝 H5 |
| `HSP_GATEWAY_BANK_REFUND_QUERY_URL` | 线下银行 |

当前接口只闭合“平台退款单号可查、已有渠道退款号严格匹配”的稳定 HTTP 代理契约；是否支持按平台退款单号查询仍取决于具体渠道协议与集成服务实现，不代表微信、支付宝或银行真实退款协议、签名验签、生产凭证、网络白名单、退款到账、对账或财务验收已经完成。

## 11. 退款订单提交代理

### `POST /api/gateway/refunds/submit`

请求头与其他交易代理一致，必须提供 `X-Gateway-App-Code` 和 `X-Gateway-Access-Key`。

请求体：

```json
{
  "refundOrderId": "REF202609100001",
  "refundAttemptNo": "RFA202609100001",
  "paymentOrderId": "PAY202609100001",
  "orderNo": "ORD202609100001",
  "customerName": "客户姓名",
  "refundAmount": 68.00,
  "refundMethod": "ORIGINAL_ROUTE",
  "refundReason": "客户申请退款",
  "channelCode": "wx_h5"
}
```

退款金额必须为正数且最多两位小数；`refundOrderId / refundAttemptNo / paymentOrderId / orderNo / refundMethod / channelCode` 必填，文本执行长度和 ISO 控制字符校验。显式传入的可选 `customerName / refundReason` 也先按原文校验，再将纯空白规范为空值。`refundAttemptNo` 作为每轮渠道侧商户退款请求号原样转发，同一轮调用前失败重试必须复用，新的退款业务重试必须使用新值。代理前复用接入应用、权限、网关、有效证书和发布路由门禁。

成功响应必须同时包含 `refundOrderId / refundAttemptNo / channelRefundNo / status / statusType`；前两项须由受控上游原样回显并与本轮请求精确一致，代理不得从请求自行补写。仅接受 `PROCESSING / warn` 或 `SUCCESS / success` 配对。空响应、上游业务码非 `0`、身份缺失/串单/跨尝试、状态与样式矛盾或其他非法响应均不视为明确受理，返回 `GATEWAY_UPSTREAM_INVALID_RESPONSE`、审计 `UNKNOWN / warn`；超时统一返回 `GATEWAY_UPSTREAM_UNAVAILABLE` 并提示先查单再重试。旧版只回状态和渠道号的上游需先完成协议映射及混版治理。

退款提交上游由以下环境变量显式配置，默认均为空：

| 环境变量 | 渠道 |
| --- | --- |
| `HSP_GATEWAY_WX_REFUND_SUBMIT_URL` | 微信 H5 |
| `HSP_GATEWAY_ALIPAY_REFUND_SUBMIT_URL` | 支付宝 H5 |
| `HSP_GATEWAY_BANK_REFUND_SUBMIT_URL` | 线下银行 |

每次请求尽力写入 `REFUND_SUBMIT` 最小审计，不保存访问密钥、请求报文、上游原始响应或异常原文。生成或收到 `refundAttemptNo` 不等于渠道已经受理；UNKNOWN 结果必须先按同一尝试号查单，不能直接换号或重发。本接口只形成稳定代理契约，不证明具体渠道协议、签名验签、幂等受理、生产密钥托管、退款到账、对账或财务验收完成。

## 12. 交易提交审计结果语义

1. `PAYMENT_SUBMIT / REFUND_SUBMIT` 在应用、凭证、权限、网关、证书、路由、URL 或请求参数门禁阶段被明确拒绝时，审计结果为 `FAILED / danger`，表示代理未进入上游提交调用。
2. 代理已经进入上游 HTTP 提交调用后，若出现空响应、非确定业务响应、响应字段非法、连接异常或超时，审计结果为 `UNKNOWN / warn`；稳定错误码仍按原契约返回，调用方必须先查单再决定后续动作。
3. `UNKNOWN` 只证明本地无法确定上游提交结果，不证明渠道已收到、受理、扣款或退款；明确受理且响应校验通过时才记录 `SUCCESS / success`。
4. `PAYMENT_QUERY / REFUND_QUERY` 不产生新的资金提交，查单调用失败继续记录 `FAILED / danger`，不得套用提交未知语义。
