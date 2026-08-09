# REG-01 ~ REG-06 / REG-09 API 证据

## 证据来源

1. `docs/跨系统联调与Smoke测试记录.md`
2. `docs/集成交付门禁审计.md`

## 已核验样例

### 正向主链路

- 业务单：`DRILL-AMQP-20260803A-002`
- 预付单：`PRE1785733720393`
- 支付单：`PAY1785733720390`
- 账单：`BILL1785733720388`

已在文档中记录并验证：

1. `POST /api/payments/prepay`
2. `POST /api/payments/submit`
3. `POST /api/payments/callback/wx_h5`

### 下游承接

已在文档中记录并验证：

1. `GET /api/clearing/orders` 生成 `CLO20002`
2. `GET /api/settlements/orders` 生成 `SLT20003`
3. `GET /api/accounting/events` 验证账务消费
4. `POST /api/settlements/orders/SLT20003/audit`
5. `POST /api/settlements/payouts` 生成 `PBT50001`
6. `POST /api/settlements/payouts/PBT50001/execute`

## 当前使用说明

本文件作为 `REG-01 ~ REG-06 / REG-09` 的 API 历史基线证据。它证明主链路和出款主链路已经真实跑通过，但不替代“最新同批次页面冻结”或“最新补偿闭环”证据。
