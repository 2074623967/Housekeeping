# REG-03 当前批次支付前半链路事件证据

- 执行日期：`2026-08-09`
- 支付单：`PAY1786249554191`
- 预付单：`PRE1786249554192`
- 业务单：`ORD-CURR-H5-20260809-004`

## 1. 事件时间线

```text
2026-08-09 12:27:44 | EVT1786249664917 | PAYMENT_SUBMIT  | payment.trade.submitted.v1 | PENDING | retryCount=0
2026-08-09 12:28:18 | EVT1786249698683 | PAYMENT_SUCCESS | payment.trade.succeeded.v1 | SUCCESS | retryCount=1
```

## 2. 关键事实

1. `submit` 成功后，支付核心先写入 `PAYMENT_SUBMIT` 事件，说明当前批次已真实进入待回调状态。
2. `callback/WX_H5` 成功后，支付核心写入 `PAYMENT_SUCCESS` 事件，事件主题为 `payment.trade.succeeded.v1`。
3. 重复回调后，通过数据库复核 `PAYMENT_SUCCESS` 事件总数仍为 `1`，说明当前批次未被重复收口。

## 3. 结论

`REG-03` 所需的“回调成功 + 当前批次幂等收口”事件证据已经齐全，可按 PASS 归档。
