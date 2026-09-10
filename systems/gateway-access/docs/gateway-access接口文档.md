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

## 8. 支付订单主动查单代理

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
