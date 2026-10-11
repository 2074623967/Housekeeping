# payment-core API 调用端

面向服务到服务联调的零依赖命令行调用端，覆盖预付、收银台、提交、支付详情、主动查单、受限关单，以及退款申请、审核、详情、主动查单和失败重试。它直接输出 payment-core 的统一 JSON 响应，不把渠道受理或 `WAIT_CALLBACK` 解释为支付成功。

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
  --idempotency-key API-REFUND-INTENT-001 \
  --operator api-operator
```

查询退款详情或主动查单：

```bash
node payment-api.mjs refund-detail --refund-order-id REF123
node payment-api.mjs refund-query --refund-order-id REF123 --operator api-operator
```

审核退款申请：

```bash
node payment-api.mjs refund-approve --refund-order-id REF123 --operator checker-operator --remark "审核通过"
```

失败退款重试：

```bash
node payment-api.mjs refund-retry --refund-order-id REF123 --operator api-operator --remark "服务调用端重试"
```

默认地址为 `http://localhost:18081`，可用 `--base-url` 或 `PAYMENT_API_BASE_URL` 覆盖。生产接入必须从密钥托管或运行环境注入访问令牌，不得复用仓库 demo 令牌。

`submit` 省略 `--idempotency-key` 时，按预付单使用固定 `API-${prepayOrderNo}`；重跑相同命令不会因当前时间变化而改键。提交内容、令牌或渠道变化时，后端会对同键异内容拒绝，不应盲目指定新键绕过。`refund-apply` 必须由调用方提供并保存一笔业务申请的 `--idempotency-key`；结果未知时原键原内容重试，确需第二笔同支付单退款时须先核对原退款事实，再使用新的业务意图键。该键不是身份凭证。

仅在完全省略支付提交键时使用固定默认键；显式传 `--idempotency-key=''` 或全空白值会在本地报参数错误，不静默切换到默认键。调用方应恢复原业务意图键，不能把配置丢失当作省略参数。

CLI 仅接受当前命令声明的参数和通用 `--base-url`。例如把 `--idempotency-key` 拼为 `--idempotency-ke` 会在发请求前报错，不再被忽略后落入默认键；其他命令的参数也不能混用。

退款申请成功响应还需包含与本次请求一致的 `refundMethod`（省略参数时为“原路退款”）；缺失或不一致时 CLI 报响应错误，不将该响应作为可信申请结果。应按原幂等键核对退款事实，不要直接换键重发。

退款申请、审核、查单与重试接口返回退款摘要，不含原支付金额 `paidAmount`；只有退款详情返回该字段。CLI 对摘要仍核验退款金额与身份，若响应意外带有 `paidAmount` 则校验其金额及与退款额的关系；退款详情必须提供有效 `paidAmount`。不要用摘要响应代替详情或原支付事实核验。

所有退款命令的终态 `SUCCESS`/`FAIL` 响应均须有合法 `terminalAt`，非终态不能提前出现终态时间；摘要与详情同样受此门禁约束。缺失或矛盾时应保留原申请幂等键并核对服务端事实。

退款详情中的渠道尝试与退款单终态不同：`PENDING`、渠道前失败的 `FAILED` 尝试尚无 `submittedAt`，后端用 `-` 表示；`UNKNOWN`、`ACCEPTED` 和终态尝试必须有合法提交时间。CLI 按尝试状态分别核验，不把合法的未派发记录误判为响应损坏。

## 自动化

```bash
npm test
```
