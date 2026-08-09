# REG-01 ~ REG-06 / REG-09 数据库事实证据

## 证据来源

1. `docs/跨系统联调与Smoke测试记录.md`
2. `docs/集成交付门禁审计.md`
3. `systems/payment-core/docs/payment-core自测与联调记录.md`

## 已核验事实

### 支付主链路

- `t_payment_order`：`PAY1785733720390` 成功收口
- `t_bill`：`BILL1785733720388` 已关联支付成功

### 清分事实

- `t_clearing_order`：`CLO20002`
- 清分状态：`清分成功`

### 结算事实

- `t_settlement_order`：`SLT20003`
- 结算状态：`待审核/待出款`，后续收口到 `已出款/已发放`

### 出款事实

- `t_payout_batch`：`PBT50001`
- `t_payout_record`：`POU60001`

### 账务事实

- 账务事件已消费
- 服务者应收账户、平台手续费账户在文档中有余额变更记录

## 当前使用说明

本文件证明 `REG-01 ~ REG-06 / REG-09` 在历史 smoke 中已具备数据库事实闭环，但当前仍需补“最新同批次回归包”下的实库或隔离库取证文件。
