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
   - 路由日志 `RULE_HOME_WX / 家政 H5 微信优先 -> wx_h5`

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
| `h5-web` | `npm install --cache /private/tmp/h5-web-npm-cache` 后执行 `npm run build -- --configLoader runner --outDir /private/tmp/hsp-h5-web-dist-20260720 --emptyOutDir` | 通过 | H5 用户端当前已完成依赖安装与生产构建复核 |
| 后端测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository test` | 通过 | 使用用户指定 Maven 与 repository，`36` 个测试全部通过 |

### 7.2 本轮专业判断

1. 当前一期主线应以 `admin-web + app-web + backend` 为核心交付，不再将 `pc-web` 作为当前阻塞项。
2. `h5-web` 已从“多端扩展基础”升级为“已完成构建复核的正式交付端”，后续可以继续增强页面矩阵与真实链路联调。
3. 后续继续开发前，需要把 H5 真实接口联调纳入自测清单，不把“仅构建通过”误写成“全链路已验证”。

### 7.3 本轮修复项

1. 修复 `PaymentController` 中 `jakarta.servlet.http.HttpServletRequest` 与 Spring Boot 2.7 / `javax.servlet-api` 不匹配的问题。
2. 修复 `PaymentServiceImplTest` 两处 Mockito 严格模式下的无用 stubbing。
3. 将订单中心、支付单管理升级为后端分页筛选，减少后台页面全量拉取数据的风险。
4. 为订单中心、支付单管理 Mapper 查询参数补充 `@Param("query")`，保证 `mapper.xml` 中 `query.xxx` 动态 SQL 运行时绑定稳定。
5. 为账单、支付请求、支付流水、支付日志、收银台会话、服务者结算、退款等列表 Mapper 统一补充显式参数绑定。
6. 补齐退款 V1 闭环：发起退款、审核通过、模拟成功/失败回调、失败重试，并增加退款金额和状态流转单测。
7. 补齐支付配置中心 V1：支付渠道配置、路由规则配置、渠道/规则启停接口和后台页面，并增加配置服务单测。
8. 补齐支付监控分析 V1：支付趋势、渠道表现、异常告警接口与后台页面，并增加监控服务单测。
9. 将支付回调安全能力升级为生产化 V1：渠道独立回调密钥、持久化 nonce 防重放、渠道编码统一归一化，并更新配置展示页。

## 9. 2026-07-20 用户支付端精修复核

### 9.1 本轮验证结论

本轮围绕 `app-web / h5-web` 的收银台与支付结果页进行了前端交付增强，确认两端已经从“基础可用”升级到“可联调、可演示、可继续扩展”的正式页面状态。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| `app-web` 收银台 | 通过 | 已补齐会话倒计时、渠道说明、支付单号、幂等键、终端标识和主动关闭支付动作 |
| `app-web` 支付结果页 | 通过 | 已补齐结果摘要、轨迹分区、主动查单、模拟回调、关闭支付、返回收银台动作 |
| `h5-web` 收银台/结果页 | 通过 | 已复用同一套支付逻辑，并补齐 H5 终端展示文案和构建验证 |
| `app-web` 构建 | 通过 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-app-web-dist-20260720 --emptyOutDir` 成功 |
| `h5-web` 构建 | 通过 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-h5-web-dist-20260720 --emptyOutDir` 成功 |

### 9.2 本轮修复项

1. 将用户端样式升级为响应式终端布局，去掉旧版 `min-width: 1200px` 的桌面限制。
2. 为收银台补齐支付方式说明、倒计时、会话状态、支付单号和联调留痕信息。
3. 为收银台补齐主动关闭支付动作，便于测试异常流和重复发起支付场景。
4. 为支付结果页补齐关闭支付、返回收银台、路由/回调/事件分区展示。
5. 为 `h5-web` 完成依赖安装与正式构建复核，修正此前“环境阻塞”的旧结论。

## 8. 2026-07-20 配置化路由闭环复核

### 8.1 本轮验证结论

本轮围绕“支付配置中心的路由规则不能只停留在展示层”进行了代码与测试复核，确认当前主链路已经从硬编码默认路由升级为配置驱动路由执行。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| 支付提交主链路 | 通过 | `submit` 已按支付方式、请求渠道、支付场景、终端、金额、客户类型组装路由上下文 |
| 路由规则命中 | 通过 | 已支持 `matchScene` + `AND/OR` 表达式匹配 |
| 路由兜底 | 通过 | 目标渠道停用时，可自动落到规则兜底渠道 |
| 自动化测试 | 通过 | 新增配置化路由测试后，全量后端测试为 `36` 个并全部通过 |

### 8.2 本轮修复项

1. 将 `PaymentServiceImpl.submit` 从旧版 `paymentMethod + channelCode` 硬编码路由，改为读取配置化路由决策对象。
2. 提交支付时，路由日志不再写“默认渠道路由”，改为真实记录命中的 `routeRule` 和 `routeResult`。
3. 新增金额解析逻辑，将预付单展示金额统一还原为数值，用于路由表达式比较。
4. 新增 `PaymentChannelRoutingServiceImplTest`，覆盖规则命中、请求渠道直连、支付方式默认路由、目标渠道停用后兜底四类场景。
5. 调整 `PaymentServiceImplTest`，校验提交支付后写入的是配置化路由结果，而不是旧的默认路由描述。

## 10. 2026-07-20 异常码与失败态复核

### 10.1 本轮验证结论

本轮围绕支付异常流的可观测性进行了补强，确认当前支付核心域已经从“只有失败文案”升级为“后端返回业务错误码，前端展示错误码与 requestId”的联调口径。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| 主链路业务异常 | 通过 | `prepay / cashier / submit / callback / query / close` 已补齐核心业务异常码 |
| 回调安全异常 | 通过 | 验签失败、密钥缺失、时间戳异常、nonce 重放均已落到独立错误码 |
| 前端错误展示 | 通过 | `admin-web / app-web / h5-web` 请求层已透出 `message + code + requestId` |
| H5 终端入口 | 通过 | `h5-web` 已改为走自身包装视图，不再直接绕过 H5 终端差异层 |
| 自动化测试 | 通过 | 新增异常处理器测试后，全量后端测试为 `36` 个并全部通过 |

### 10.2 本轮修复项

1. 新增 `BusinessException` 和 `ErrorCode`，统一支付核心域错误码常量。
2. 为全局异常处理器补齐 `BusinessException`、参数校验异常和系统异常三层处理逻辑。
3. 为支付主链路和支付路由、查单、回调验签补齐第一版业务错误码。
4. 为 `app-web` 和 `admin-web` 请求层补齐错误码、`requestId` 展示口径。
5. 修复 `h5-web` 入口仍直接引用 `app-web` 组件的问题，确保 H5 终端差异层真正生效。
