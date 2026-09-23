# payment-core 微信小程序支付端

## 定位

原生微信小程序支付骨架，覆盖充值、提现补款、转账补款、余额补款、预付单、收银台、支付提交、结果刷新、主动查单和受限关单。

## 本地运行

1. 保持 `payment-core` demo 后端运行在 `18081`。
2. 在微信开发者工具中导入本目录，使用测试号运行。
3. 本地调试时关闭开发者工具的合法域名校验；真机联调必须把 `paymentApiBaseUrl` 改为设备可访问且已配置为合法域名的 HTTPS 地址。

默认 API 地址为 `http://127.0.0.1:18081`。也可以在开发者工具控制台执行：

```js
wx.setStorageSync("paymentApiBaseUrl", "https://your-payment-api.example.com")
```

## 本地 demo 身份

- 来源应用：`housekeeping-mini-program`
- 支付方式：微信支付
- 渠道：`wx_h5`
- 商户号：`MCH_WX_10001`
- 终端：`MINI_PROGRAM`

仓库中的访问令牌仅用于本地 demo。真实环境必须由受控配置、用户登录态和安全后端注入，不能把示例令牌发布到生产小程序。

## 当前边界

前端已支持在提交响应包含完整微信 `wx.requestPayment` 参数时拉起原生支付；后端本地渠道适配器当前只返回支付受理与待回调事实，因此本地 demo 会跳过原生拉起并继续进入结果页。真实接入需由 `gateway-access` 返回受签名保护的微信支付参数，并补齐 AppID/商户绑定、openid、合法域名、证书与回调验签。

## 自动化

```bash
npm test
```
