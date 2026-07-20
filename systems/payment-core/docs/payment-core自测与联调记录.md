# payment-core 自测与联调记录

## 1. 记录目的

本文档用于沉淀 `payment-core` 冻结版 V1 的真实验证结果，明确：

1. 已验证项
2. 未完全验证项
3. 受环境限制项
4. 当前风险

## 2. 已完成验证

### 2.1 前端构建验证

已完成：

1. `systems/payment-core/frontend/admin-web` 可完成 Vite 构建
2. `systems/payment-core/frontend/app-web` 可完成 Vite 构建
3. `admin-web` 到 `app-web` 的主链路路由口径已对齐：
   - 后台订单中心发起支付后可生成收银台链接
   - 收银台路由已统一为 `/cashier/:prepayOrderNo`
   - 支付结果页路由已统一为 `/payment-result/:paymentOrderId`
4. 用户端页面当前已覆盖：
   - 收银台
   - 支付结果页
5. 支付结果页已按接口文档改为优先调用 `GET /api/payments/{paymentOrderId}` 查询结果详情

结论：

- 当前前端代码不仅满足静态构建通过标准，也已具备后台发起支付到用户端结果页的联调基础

### 2.2 数据库验证

已完成：

1. 2026-07-19 已使用本机 MySQL `root/123456` 成功连接 `127.0.0.1:3306`
2. 库 `housekeeping_payment_core` 已重建成功
3. `schema.sql` 可完整建表
4. `data.sql` 可完整导入样例数据
5. 支付主链路涉及表已校验可正常写入：
   - `t_prepay_order`
   - `t_payment_order`
   - `t_payment_attempt`
   - `t_payment_notify_log`
   - `t_payment_route_record`
   - `t_payment_event`
   - `t_bill`
   - `t_order`

结论：

- 当前 DDL 可在 MySQL 中落地执行

### 2.3 文档与代码对齐验证

已完成：

1. 后端分层结构与规范一致
2. `application.yml` 已切至独立数据库
3. `schema.sql` 已补表注释与字段注释
4. `entity` 已统一使用 `@Data`
5. 前端页面与主链路接口已形成对应关系
6. `app-web` 的页面路由与《一期-支付核心系统前端页面与交互说明》保持一致

### 2.4 后端构建与启动验证

已完成：

1. 2026-07-19 使用 JDK `1.8.0_202` 完成 `mvn -DskipTests package`
2. Spring Boot 服务已成功启动在 `http://127.0.0.1:18080`
3. 基础查询接口已验证：
   - `GET /api/dashboard/summary`
   - `GET /api/orders`
   - `GET /api/payments`
   - `GET /api/payments/{paymentOrderId}`

结论：

- 当前后端已具备真实运行与接口验证基础

### 2.5 支付交易主链路联调验证

已完成一轮真实闭环验证，验证时间：`2026-07-19 16:18:58` 至 `2026-07-19 16:18:59`。

本轮验证订单：

- `orderNo = ORD202607190002`
- `billNo = BILL202607190002`
- `prepayOrderNo = PRE1784449138846`
- `paymentOrderId = PAY1784449138838`

已验证接口：

1. `POST /api/payments/prepay`
2. `GET /api/payments/cashier/{prepayOrderNo}`
3. `POST /api/payments/submit`
4. `GET /api/payments/{paymentOrderId}`
5. `POST /api/payments/callback/WX_H5`
6. `POST /api/payments/query`
7. `POST /api/payments/close`

关键结果：

1. 预付单创建后成功生成独立支付单，不再复用旧支付单
2. 提交支付后支付单状态进入 `WAIT_CALLBACK`
3. 回调成功后支付单状态变为 `SUCCESS`
4. 回调成功后：
   - `t_order.paid_amount = 8800.00`
   - `t_order.order_status = 待履约`
   - `t_bill.paid_amount = 8800.00`
   - `t_bill.bill_status = 已结清`
   - `t_prepay_order.cashier_status = 支付成功`
   - `t_payment_attempt.attempt_status = 成功`
5. 成功支付后再次调用关闭接口，不再错误关闭成功单
6. 支付轨迹完整落库：
   - `PAYMENT_SUBMIT`
   - `PAYMENT_SUCCESS`
   - 回调日志 `SUBMIT / SUCCESS`
   - 路由日志 `默认渠道路由 / 微信支付`

## 3. 本轮修复项

2026-07-19 本轮已修复：

1. 同一订单再次拉起支付时，预付单复用旧支付单导致详情一对多报错
2. 提交支付后支付单状态未进入 `WAIT_CALLBACK`
3. 支付成功后订单、账单、收银台、支付尝试状态未同步收口
4. 成功支付单仍可被关闭，状态机不符合支付域预期
5. `admin-web` 发起支付跳转的用户端端口错误，导致后台无法正确打开收银台
6. `app-web` 支付结果页路由与接口文档不一致，已统一为 `/payment-result/:paymentOrderId`
7. 后台模拟回调渠道编码与后端当前主链路口径不一致，已统一为 `WX_H5`

## 4. 当前风险清单

1. 回调验签、时间窗校验、nonce 防重放和提交幂等键已补入主链路，但仍需要后续接入真实渠道签名口径与持久化防重放存储。
2. 主动查单当前查询的是本地支付详情，不是真实渠道侧查单。
3. 渠道路由、风控决策、超时关闭、补单补偿仍为演示级实现。
4. 缺少自动化单元测试、集成测试与契约测试。
5. 关闭支付接口当前采取“成功单不关闭，直接返回原状态”的保护式处理，后续可补明确错误码语义。

## 5. 当前结论

从工程交付角度看，`payment-core` 当前已经达到：

1. 结构规范可审查
2. 页面与接口可开发
3. 数据库可落地
4. 主链路可运行
5. 文档可交付

从生产投产角度看，当前还需要继续补：

1. 自动化测试体系
2. 真实渠道接入与验签能力
3. 补偿、对账、幂等、防重与告警能力

## 6. 2026-07-19 收款记录与支付可靠性补强验证

### 6.1 构建验证

| 项目 | 命令/方式 | 结果 |
| --- | --- | --- |
| 后端编译 | JDK `1.8.0_202` + IDEA Maven `mvn -DskipTests package` | 通过 |
| 后台前端构建 | `npm run build` | 通过 |
| 自动化测试源码编译 | `mvn -DskipTests package` | 通过 |
| 自动化测试执行 | `mvn test` | 环境阻塞：本机缺少 `surefire-junit-platform`，测试代码尚未执行 |

### 6.2 接口验证

| 场景 | 结果 |
| --- | --- |
| `/api/payment-records?recordType=ALL` | 返回统一收款记录完整原型字段 |
| `/api/payment-records?recordType=WECHAT` | 返回微信支付宝维度记录 |
| `/api/payment-records?recordType=BANK_CARD` | 返回银行卡维度记录 |
| 过期支付单自动关单 | 测试库中的已过期支付单被任务收口为 `CLOSED` |
| 成功支付单重复回调 | 第二次回调直接返回已成功结果，不重复写入日志和事件 |
| `/api/payment-metrics/summary` | 返回支付单总数、成功数、处理中数、关闭数、成功金额和成功率 |

### 6.3 测试说明

本次接口联调使用独立测试库 `housekeeping_payment_core_smoke_v2`，未修改默认业务库。
支付提交、回调测试必须按“预付单 -> 提交支付 -> 回调”的顺序串联，禁止并发触发造成时序假象。

## 7. 2026-07-20 主线构建复核

### 7.1 本轮验证结论

本轮按照“先全量交付 `payment-core`，再推进其他系统”的主线，对当前前后端执行了一次构建复核。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| `admin-web` | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist --emptyOutDir` | 通过 | 后台运营端当前可完成生产构建 |
| `app-web` | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-app-web-dist --emptyOutDir` | 通过 | 用户端收银台与结果页当前可完成生产构建 |
| `h5-web` | 复用 `app-web` Vite 命令构建 | 环境阻塞 | `h5-web` 当前未安装本地 `node_modules`，需执行依赖安装后再复核；代码层面上一轮临时依赖验证曾通过 |
| 后端测试 | `mvn test` | 环境阻塞 | 当前 shell 找不到 `mvn`，需在 IDEA Maven 或配置 Maven PATH 后执行 |

### 7.2 本轮专业判断

1. 当前一期主线应以 `admin-web + app-web + backend` 为核心交付，不再将 `pc-web` 作为当前阻塞项。
2. `h5-web` 可保留为多端扩展基础，但必须完成依赖安装与构建复核后，才能标记为正式交付端。
3. 后续继续开发前，需要把本轮环境阻塞项纳入自测清单，不把“未验证”误写成“已通过”。
