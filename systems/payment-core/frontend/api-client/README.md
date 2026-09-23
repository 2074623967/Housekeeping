# payment-core API 调用端

面向服务到服务联调的零依赖命令行调用端，覆盖预付、收银台、提交、支付详情、主动查单、受限关单，以及退款申请、退款详情、退款主动查单和失败重试。它直接输出 payment-core 的统一 JSON 响应，不把渠道受理或 `WAIT_CALLBACK` 解释为支付成功。

## 本地使用

创建预付单：

```bash
node payment-api.mjs prepay \
  --order-no API-DEMO-001 \
  --pay-scene SERVICE_ORDER_API \
  --customer-name API联调客户 \
  --amount 168.00 \
  --cashier-title API支付
```

`prepay` 还支持可选参数 `--remark`，会作为业务说明写入 payment-core 支付处理日志，便于运营排查。

提交支付宝支付：

```bash
node payment-api.mjs submit \
  --prepay-order-no PRE123 \
  --access-token token-housekeeping-api \
  --idempotency-key API-DEMO-001-SUBMIT
```

读取支付详情：

```bash
node payment-api.mjs detail --payment-order-id PAY123
```

还支持：

```bash
node payment-api.mjs cashier --prepay-order-no PRE123
node payment-api.mjs query --payment-order-id PAY123
node payment-api.mjs close --payment-order-id PAY123
```

发起退款申请：

```bash
node payment-api.mjs refund-apply \
  --payment-order-id PAY123 \
  --refund-amount 20.00 \
  --operator api-operator
```

查询退款详情或主动查单：

```bash
node payment-api.mjs refund-detail --refund-order-id REF123
node payment-api.mjs refund-query --refund-order-id REF123 --operator api-operator
```

失败退款重试：

```bash
node payment-api.mjs refund-retry --refund-order-id REF123 --operator api-operator --remark "服务调用端重试"
```

默认地址为 `http://127.0.0.1:18081`，可用 `--base-url` 或 `PAYMENT_API_BASE_URL` 覆盖。生产接入必须从密钥托管或运行环境注入访问令牌，不得复用仓库 demo 令牌。

## 自动化

```bash
npm test
```
