# REG-01 ~ REG-03 当前批次支付前半链路数据库证据

- 执行日期：`2026-08-09`
- 数据库：`housekeeping_payment_core`
- 查询客户端：`/usr/local/mysql/bin/mysql`
- 核心业务单：
  - `prepayOrderNo = PRE1786249554192`
  - `billNo = BILL1786249554191`
  - `paymentOrderId = PAY1786249554191`

## 1. 预付单与支付单事实

查询结果：

```text
t_prepay_order
PRE1786249554192 | ORD-CURR-H5-20260809-004 | 张女士 | 168.00 | H5 | 支付成功 | success | PAY1786249554191 | 2026-08-09 12:25:54 | 2026-08-09 13:25:54

t_payment_order
PAY1786249554191 | ORD-CURR-H5-20260809-004 | 张女士 | 168.00 | 微信支付 | wx_h5 | WX-1786249664904 | SUCCESS | success | 2026-08-09 12:25:54

t_bill
BILL1786249554191 | ORD-CURR-H5-20260809-004 | 张女士 | 168.00 | 168.00 | 已结清 | success
```

结论：

- 预付单已从 `待支付` 收口到 `支付成功`
- 支付单已成功写入支付方式 `微信支付`、渠道 `wx_h5`、渠道流水号 `WX-1786249664904`
- 账单已被同步结清

## 2. 提交链路事实

查询结果：

```text
t_payment_attempt
ATT1786249664915 | PRE1786249554192 | PAY1786249554191 | wx_h5 | 微信支付 | default-app | MOBILE_WEB | 127.0.0.1 | PRE1786249554192|微信支付|wx_h5 | 成功 | success | 2026-08-09 12:27:44

t_payment_route_record
RTR1786249664914 | PAY1786249554191 | wx_h5 | RULE_HOME_WX | 家政 H5 微信优先 -> wx_h5 | 2026-08-09 12:27:44
```

结论：

- 当前批次确实发生了真实 submit，而不是只靠直接回调伪造成功
- 路由规则实际命中 `RULE_HOME_WX`
- 尝试日志和路由日志均为当前批次新事实

## 3. 回调与事件事实

查询结果：

```text
t_payment_notify_log
NTF1786249664920 | PAY1786249554191 | wx_h5  | SUBMIT  | 待回调 | warn    | 2026-08-09 12:27:44
NTF1786249698677 | PAY1786249554191 | WX_H5  | SUCCESS | 已收口 | success | 2026-08-09 12:28:18

t_payment_event
EVT1786249664917 | PAYMENT_SUBMIT  | PAY1786249554191 | ORD-CURR-H5-20260809-004 | payment.trade.submitted.v1 | PENDING | warn    | 0 | 2026-08-09 12:27:44
EVT1786249698683 | PAYMENT_SUCCESS | PAY1786249554191 | ORD-CURR-H5-20260809-004 | payment.trade.succeeded.v1 | SUCCESS | success | 1 | 2026-08-09 12:28:18
```

结论：

- 提交待回调日志与成功回调日志顺序完整
- `PAYMENT_SUBMIT` 与 `PAYMENT_SUCCESS` 两条核心事件都已落库

## 4. 同批次幂等核验

查询结果：

```text
attempt_count = 1
success_notify_count = 1
payment_success_event_count = 1
```

结论：

- 重复提交没有新增第二条 `t_payment_attempt`
- 重复回调没有新增第二条成功回调日志
- 重复回调没有新增第二条 `PAYMENT_SUCCESS` 事件
- 因此当前批次的补偿/回滚证据可按“幂等收口已生效”判定为通过
