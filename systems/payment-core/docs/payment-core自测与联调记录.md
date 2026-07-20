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
3. `systems/payment-core/frontend/pc-web` 可完成 Vite 构建
4. `admin-web` 到 `app-web` 的主链路路由口径已对齐：
   - 后台订单中心发起支付后可生成收银台链接
   - 收银台路由已统一为 `/cashier/:prepayOrderNo`
   - 支付结果页路由已统一为 `/payment-result/:paymentOrderId`
5. 用户端页面当前已覆盖：
   - 收银台
   - 支付结果页
   - PC 收银台
   - PC 支付结果页
6. 支付结果页已按接口文档改为优先调用 `GET /api/payments/{paymentOrderId}` 查询结果详情

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
| `/api/payment-records/{paymentOrderId}` | 返回最近一次支付尝试、请求报文、响应报文、路由/回调/事件轨迹 |
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
| `admin-web` | `npm run build` | 通过 | 后台运营端当前可完成生产构建 |
| `app-web` | `npm run build` | 通过 | 用户端收银台与结果页当前可完成生产构建 |
| `pc-web` | `npm run build` | 通过 | PC 收银台与结果页当前可完成生产构建 |
| `h5-web` | `npm run build` | 通过 | H5 用户端当前可完成生产构建 |
| 后端测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository test` | 通过 | 使用用户指定 Maven 与 `repository`，`49` 个测试全部通过 |

### 7.2 本轮专业判断

1. 当前一期主线应以 `admin-web + app-web + pc-web + h5-web + backend` 作为完整交付基线，而不是只看后台和单一用户端。
2. `payment-core` 当前的主要问题已经不是“能不能构建”，而是“生产级能力是否补齐”，例如真实渠道接入、验签、幂等、防重、补偿和跨系统事件收敛。
3. 后续继续开发前，需要把 `pc-web` 和 `h5-web` 的真实接口联调纳入自测清单，不把“构建通过”误写成“全链路已验证”。

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

## 9. 2026-07-20 支付协议管理增强验证

### 9.1 本轮验证结论

本轮围绕支付配置中心中的“支付协议管理”进行了正式化增强，确认后台已经从“只读+启停”升级到“可新增、可编辑、可启停”的一体化维护形态。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml test` | 通过 | `PaymentConfigServiceImplTest` 已扩展到协议新增、编辑、重复编码校验 |
| 后台前端构建 | `npm run build` | 通过 | `PaymentConfigView` 已新增协议表单、编辑入口、优先级展示与状态维护 |

### 9.2 本轮修复项

1. 新增支付协议新增接口：`POST /api/payment-config/protocols`
2. 新增支付协议编辑接口：`PUT /api/payment-config/protocols/{protocolCode}`
3. 为支付协议配置补齐 `PaymentProtocolUpsertRequestDTO` 与 `PaymentProtocolConfigEntity`
4. 为 `PaymentConfigMapper` 补齐协议单条查询、插入、更新 `mapper.xml`
5. 为后台配置页补齐协议新增、编辑、重置表单和优先级展示
6. 修复“编辑已有协议时优先级被默认值覆盖”的隐性配置风险

## 10. 2026-07-20 支付日终处理 V1 验证

### 10.1 本轮验证结论

本轮围绕“支付日终处理”补齐了后台页面、后端聚合接口、手动触发接口和批次留痕表，确认 `payment-core` 已经从“只有支付监控，没有日终收口”推进到“具备基础日终批次视角”的阶段。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml test` | 通过 | 新增 `PaymentDayEndServiceImplTest`，覆盖总览查询与手动跑批 |
| 后台前端构建 | `npm run build` | 通过 | `PaymentDayEndView`、路由、导航与接口封装全部通过生产构建 |

### 10.2 本轮修复项

1. 新增支付日终批次表 `t_payment_day_end_batch`，沉淀业务日、批次状态、异常数和执行备注
2. 新增支付日终总览接口：`GET /api/payment-day-end/overview`
3. 新增支付日终手动触发接口：`POST /api/payment-day-end/run`
4. 新增后台页面“支付日终处理”，支持查看总览、异常数、最近批次与手动触发
5. 统一按专业口径修正退款成功统计日期，改为 `success_at`
6. 修复聚合空结果时页面可能出现 `null` 的边界问题

## 11. 2026-07-20 支付运营筛选排序增强验证

### 11.1 本轮验证结论

本轮围绕支付路由执行结果、支付请求管理、支付处理日志、支付事件出站四个运营排障页面进行了同口径增强，确认后台已经从“基础查询可用”升级到“支持运营条件缩圈、排序复核和事件重发后结果回看”的阶段。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository test` | 通过 | `57` 个测试全部通过，覆盖查询参数归一化和分页查询服务 |
| 后台前端构建 | `npm run build` | 通过 | `PaymentRequestsView`、`PaymentLogsView`、`PaymentEventsView`、`PaymentRoutesView` 最新筛选栏与排序控件通过生产构建 |

### 11.2 本轮修复项

1. 支付请求管理新增 `clientIp`、`sortField`、`sortOrder` 查询参数，并同步到后台筛选栏和后端动态 SQL。
2. 支付处理日志新增 `sortField`、`sortOrder` 查询参数，支持按创建时间、日志级别、处理阶段统一排序。
3. 支付事件出站新增 `eventTopic`、`sortField`、`sortOrder` 查询参数，手动重发后可沿用当前筛选条件回看结果。
4. 支付路由执行结果新增 `paymentMethod`、`terminal`、`sortField`、`sortOrder` 查询参数，便于从支付方式和终端维度复盘命中结果。
5. 四个页面的前端说明、接口口径和交付状态文档已同步更新，避免产品、前端、后端、测试看到不同版本字段。

## 11. 2026-07-20 支付任务中心 V1 验证

### 11.1 本轮验证结论

本轮围绕“任务监控与失败重试正式化”补齐了支付任务中心，确认 `payment-core` 已经从“只有定时任务和零散重试按钮”升级到“具备统一任务处理台、人工触发入口和执行留痕”的阶段。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml test` | 通过 | 新增 `PaymentTaskCenterServiceImplTest`、`PaymentExpiryTaskServiceImplTest`，当前全量后端测试为 `55` 个并全部通过 |
| 后台前端构建 | `npm run build` | 通过 | `PaymentTaskCenterView`、路由、导航和接口封装全部通过生产构建 |

### 11.2 本轮修复项

1. 新增支付任务执行日志表 `t_payment_task_run_log`，沉淀任务编码、执行结果、处理量和触发人
2. 新增支付任务中心总览接口：`GET /api/payment-task-center/overview`
3. 新增支付任务动作接口：
   - `POST /api/payment-task-center/close-expired-payments`
   - `POST /api/payment-task-center/republish-failed-events`
   - `POST /api/payment-task-center/retry-failed-refunds`
4. 将超时关单逻辑从 `PaymentExpiryScheduler` 中抽到 `PaymentExpiryTaskServiceImpl`，统一给调度器和任务中心复用
5. 新增后台页面“支付任务中心”，统一查看超时关单、失败事件、失败退款、日终告警和最近执行日志
6. 修复差距清单里“日终处理重复且状态冲突”的旧口径问题

### 11.3 2026-07-20 支付任务中心 V1.5 补强复核

本轮围绕任务中心的“正式运维能力”继续补强，确认当前版本已经从“只有最近 10 条日志和三个按钮”升级到“自动/手动来源统一留痕、重点告警卡片、日志筛选分页和任务严重等级展示”的阶段。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository test` | 通过 | 当前全量后端测试提升为 `59` 个并全部通过 |
| 后台前端构建 | `npm run build` | 通过 | `PaymentTaskCenterView` 的重点告警、日志筛选分页与路由跳转能力通过生产构建 |

本轮修复项：

1. 为任务执行日志补齐严重等级、升级状态、建议动作和推荐路由字段。
2. 新增任务日志分页查询接口：`GET /api/payment-task-center/task-runs`。
3. 将自动调度的超时关单纳入统一任务日志，正式区分 `AUTO / MANUAL` 两类来源。
4. 为任务中心总览补齐重点告警卡片，覆盖超时支付、待收口支付和失败事件三类运维关注点。

## 8. 2026-07-20 用户支付端精修复核

### 8.1 本轮验证结论

本轮围绕 `app-web / h5-web / pc-web` 的收银台与支付结果页进行了前端交付增强，确认三端已经从“基础可用”升级到“可联调、可演示、可继续扩展”的正式页面状态。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| `app-web` 收银台 | 通过 | 已补齐会话倒计时、渠道说明、支付单号、幂等键、终端标识和主动关闭支付动作 |
| `app-web` 支付结果页 | 通过 | 已补齐结果摘要、轨迹分区、主动查单、模拟回调、关闭支付、返回收银台动作 |
| `h5-web` 收银台/结果页 | 通过 | 已复用同一套支付逻辑，并补齐 H5 终端展示文案和构建验证 |
| `pc-web` 收银台/结果页 | 通过 | 已新增独立 PC 端入口并复用同一套交易逻辑，补齐桌面端支付文案和构建验证 |
| `app-web` 构建 | 通过 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-app-web-dist-20260720 --emptyOutDir` 成功 |
| `h5-web` 构建 | 通过 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-h5-web-dist-20260720 --emptyOutDir` 成功 |
| `pc-web` 构建 | 通过 | `npm run build` 成功，产出独立 PC 端构建包 |

### 8.2 本轮修复项

1. 将用户端样式升级为响应式终端布局，去掉旧版 `min-width: 1200px` 的桌面限制。
2. 为收银台补齐支付方式说明、倒计时、会话状态、支付单号和联调留痕信息。
3. 为收银台补齐主动关闭支付动作，便于测试异常流和重复发起支付场景。
4. 为支付结果页补齐关闭支付、返回收银台、路由/回调/事件分区展示。
5. 为 `h5-web` 完成依赖安装与正式构建复核，修正此前“环境阻塞”的旧结论。
6. 新增 `pc-web` 独立前端端口与桌面端文案，补齐 PC 收银台/支付结果页基础骨架。

## 9. 2026-07-20 配置化路由闭环复核

### 9.1 本轮验证结论

本轮围绕“支付配置中心的路由规则不能只停留在展示层”进行了代码与测试复核，确认当前主链路已经从硬编码默认路由升级为配置驱动路由执行。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| 支付提交主链路 | 通过 | `submit` 已按支付方式、请求渠道、支付场景、终端、金额、客户类型组装路由上下文 |
| 路由规则命中 | 通过 | 已支持 `matchScene` + `AND/OR` 表达式匹配 |
| 路由兜底 | 通过 | 目标渠道停用时，可自动落到规则兜底渠道 |
| 自动化测试 | 通过 | 当前全量后端测试为 `55` 个并全部通过 |

### 9.2 本轮修复项

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
| 前端错误展示 | 通过 | `admin-web / app-web / h5-web / pc-web` 请求层已透出 `message + code + requestId` |
| H5 终端入口 | 通过 | `h5-web` 已改为走自身包装视图，不再直接绕过 H5 终端差异层 |
| PC 终端入口 | 通过 | `pc-web` 已改为走独立入口与独立路由，不再与 App/H5 混用 |
| 自动化测试 | 通过 | 当前全量后端测试为 `55` 个并全部通过 |

### 10.2 本轮修复项

1. 新增 `BusinessException` 和 `ErrorCode`，统一支付核心域错误码常量。
2. 为全局异常处理器补齐 `BusinessException`、参数校验异常和系统异常三层处理逻辑。
3. 为支付主链路和支付路由、查单、回调验签补齐第一版业务错误码。
4. 为 `app-web` 和 `admin-web` 请求层补齐错误码、`requestId` 展示口径。
5. 修复 `h5-web` 入口仍直接引用 `app-web` 组件的问题，确保 H5 终端差异层真正生效。
6. 新增 `pc-web` 入口和 PC 端展示层，确保桌面端与 App/H5 的终端差异层真正生效。

## 12. 2026-07-20 渠道下单适配器复核

### 12.1 本轮验证结论

本轮围绕“支付提交流程不能继续把渠道返回结果硬编码在 `PaymentServiceImpl` 里”进行了抽象补强，确认支付主链路已经具备第一版可扩展的渠道下单适配层。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| 渠道下单抽象 | 通过 | 已新增 `PaymentChannelSubmitAdapter + PaymentChannelSubmitService`，提交支付不再在主服务里硬编码渠道响应 |
| 提交结果留痕 | 通过 | 支付单已回写 `channelTransactionNo`，支付尝试与通知日志已记录真实适配器返回报文 |
| 本地模拟渠道 | 通过 | 已补齐 `LocalPaymentChannelSubmitAdapter`，在未接真实微信/支付宝前可稳定支撑联调 |
| 自动化测试 | 通过 | 当前全量后端测试为 `55` 个并全部通过 |

### 12.2 本轮修复项

1. 新增 `PaymentChannelSubmitRequestDTO` 和 `PaymentChannelSubmitResultDTO`，统一提交支付时传给渠道适配层的上下文和回参口径。
2. 新增 `PaymentChannelSubmitAdapter`、`PaymentChannelSubmitService` 及其实现，沿用查单适配器模式对齐后续真实渠道接入方式。
3. 将 `PaymentServiceImpl.submit` 改为先组装标准化上下文，再委托渠道下单服务完成提交。
4. 调整 `PaymentMapper.updatePaymentMethodAndChannel`，在支付单上同步回写渠道交易流水号。
5. 调整 `PaymentServiceImplTest`，校验渠道交易流水号、响应报文和支付尝试状态已来自适配器返回值，而不是旧版硬编码。

## 13. 2026-07-20 订单中心联查增强复核

### 13.1 本轮验证结论

本轮围绕“订单中心不能只停留在发起支付入口，必须能直接钻到当前支付链路”进行了补强，确认订单页已经具备面向运营、测试和研发的支付联查入口。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| 订单列表链路字段 | 通过 | 后端已补齐 `billNo`、最新支付单号、最新预付单号、最新支付状态、最新收银台状态 |
| 订单中心联查动作 | 通过 | 前端已支持从订单页直接跳转支付单详情、支付记录详情、账单中心和当前收银台 |
| 账单中心回填查询 | 通过 | 账单页已支持从路由参数自动回填 `billNo / orderNo / billStatus` |
| 自动化测试 | 通过 | 当前全量后端测试为 `53` 个并全部通过 |

### 13.2 本轮修复项

1. 扩展 `OrderMapper` 查询口径，将订单、账单、最新支付单、最新预付单聚合到订单中心分页列表。
2. 扩展 `OrderListItemDTO`，补齐订单侧支付联查需要的链路字段。
3. 为 `OrdersView` 增加“查看支付单 / 查看支付记录 / 查看账单 / 打开收银台”动作，不再只有“发起支付”单一入口。
4. 发起支付成功后自动刷新订单列表，保证最新支付链路字段及时回显。
5. 为 `BillsView` 增加路由参数回填，支撑订单页按账单号和订单号钻取。

## 14. 2026-07-20 支付详情联查与 PC 收银台补强复核

### 14.1 本轮验证结论

本轮围绕“支付单详情页与 PC 收银台仍偏基础版”的问题进行了补强，确认后台支付详情联查链路和桌面端支付呈现已更接近冻结版交付口径。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| 支付单详情联查入口 | 通过 | 后台详情页已支持跳到支付记录详情、订单中心、账单中心和当前收银台 |
| 支付详情轨迹空态 | 通过 | 路由、回调、事件三类轨迹在无数据时已补齐空态提示 |
| PC 收银台扫码承载区 | 通过 | `pc-web` 已提供独立二维码承载区、渠道编码展示和桌面端联查提示，可替换为真实二维码组件 |
| 前端构建验证 | 通过 | `admin-web` 构建通过，支付端共享样式和逻辑可继续复用 |

### 14.2 本轮修复项

1. 为 `PaymentDetailView` 增加支付记录详情、订单中心、账单中心和当前收银台的联查动作。
2. 在支付单详情页补充 `billNo`、`querySource` 和轨迹空态提示，提升排障可读性。
3. 在 `app-web` 的桌面端分支中增加独立二维码承载区和桌面端扫码元信息，支撑后续替换为真实二维码。
4. 为 `pc-web` 收银台补充失败补救动作和联查建议，使桌面端页面不再只剩提示文案。

## 15. 2026-07-20 支付详情尝试信息对齐复核

### 15.1 本轮验证结论

本轮围绕“支付单详情接口、后台支付详情页和支付结果页对最近支付尝试信息展示不一致”的问题进行了补齐，确认三端口径已进一步收敛。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| 支付详情接口字段 | 通过 | `GET /api/payments/{paymentOrderId}` 已补齐最近尝试终端、IP、幂等键、尝试状态、请求报文和响应报文 |
| 后台支付详情页 | 通过 | 已新增最近支付尝试区块，支持直接查看请求/响应报文 |
| 用户支付结果页 | 通过 | 已新增最近尝试状态、终端、IP、幂等键和请求/响应报文展示 |
| 自动化测试 | 通过 | 新增支付详情尝试信息单测后，当前全量后端测试为 `55` 个并全部通过 |

### 15.2 本轮修复项

1. 扩展 `PaymentDetailDTO`，补齐最近一次支付尝试相关字段。
2. 扩展 `PaymentMapper.findDetail`，关联最新支付尝试记录。
3. 为 `PaymentServiceImplTest` 增加支付详情尝试信息聚合测试。
4. 为后台支付详情页和用户支付结果页补齐尝试信息展示，提升联调和排障可读性。

## 16. 2026-07-20 支付流水排障台增强复核

### 16.1 本轮验证结论

本轮围绕“支付流水查询仍停留在摘要列表，不足以支撑统一链路排障”的问题进行了补强，确认后台支付流水页已经具备更接近正式版的统一排障台能力。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| 支付流水接口字段 | 通过 | `GET /api/payment-flows` 已补齐渠道编码、业务状态筛选，以及终端、IP、幂等键、回调类型、路由规则、下游系统、事件主题、发布状态、重试次数、原始报文等深度字段 |
| 后台支付流水页 | 通过 | 已支持按渠道编码、业务状态筛选，并支持展开详情查看原始报文与联查动作 |
| 自动化测试 | 通过 | `PaymentFlowServiceImplTest` 所在后端全量测试共 `55` 个并全部通过 |
| 前端构建验证 | 通过 | `admin-web` 已完成生产构建，支付流水排障台增强页可稳定打包 |

### 16.2 本轮修复项

1. 扩展 `PaymentFlowQueryDTO` 和 `PaymentFlowListItemDTO`，统一支付尝试、回调、路由、事件四类流水的深度排障字段。
2. 扩展 `PaymentFlowController`、`PaymentFlowServiceImpl` 和 `PaymentFlowMapper.xml`，补齐渠道编码和业务状态筛选条件。
3. 为 `PaymentFlowServiceImplTest` 增加查询参数规整断言，确保服务层对渠道编码和业务状态查询口径保持一致。
4. 为 `PaymentFlowsView` 增加展开详情区、原始报文展示和支付单/支付请求/支付处理日志联查动作。

## 17. 2026-07-20 支付请求与支付日志运营筛选增强复核

### 17.1 本轮验证结论

本轮围绕“支付请求页和支付日志页只能按少量条件筛选，运营排障仍不够高效”的问题进行了补强，确认两类页面已经具备更贴近正式版后台的实战筛选能力。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| 支付请求接口字段 | 通过 | `GET /api/payment-requests` 已补齐订单号、渠道编码、终端筛选 |
| 支付处理日志接口字段 | 通过 | `GET /api/payment-logs` 已补齐订单号、日志来源、关键字筛选 |
| 自动化测试 | 通过 | 后端全量测试共 `55` 个并全部通过 |
| 前端构建验证 | 通过 | `admin-web` 已完成生产构建，请求页和日志页增强查询区可稳定打包 |

### 17.2 本轮修复项

1. 扩展 `PaymentRequestQueryDTO`、`PaymentRequestController`、`PaymentRequestServiceImpl` 和 `PaymentRequestMapper.xml`，补齐订单号、渠道编码、终端查询口径。
2. 扩展 `PaymentLogQueryDTO`、`PaymentLogController`、`PaymentLogServiceImpl` 和 `PaymentLogMapper.xml`，补齐订单号、日志来源、关键字检索口径。
3. 为 `PaymentRequestServiceImplTest`、`PaymentLogServiceImplTest` 增加参数规整断言，确保服务层对新增查询字段做统一裁剪。
4. 为 `PaymentRequestsView`、`PaymentLogsView` 和前端 API 封装补齐新增筛选项，提升运营、研发和测试的联动排障效率。

## 18. 2026-07-20 支付路由执行结果台增强复核

### 18.1 本轮验证结论

本轮围绕“支付路由执行结果仍需要从支付流水和支付详情里拼装查看，不利于独立排障”的问题进行了补强，确认后台已具备独立的支付路由执行结果台。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| 支付路由执行结果接口 | 通过 | `GET /api/payment-routes` 已补齐支付单、订单、路由规则、命中渠道、终端、幂等键和请求/响应报文聚合字段 |
| 后台支付路由页 | 通过 | 已新增独立路由页，支持筛选、展开详情和支付单/路由流水/支付请求/配置联查动作 |
| 自动化测试 | 通过 | 新增 `PaymentRouteExecutionServiceImplTest` 后，后端全量测试共 `55` 个并全部通过 |
| 前端构建验证 | 通过 | `admin-web` 已完成生产构建，支付路由执行结果页可稳定打包 |

### 18.2 本轮修复项

1. 新增 `PaymentRouteExecutionQueryDTO`、`PaymentRouteExecutionListItemDTO`、`PaymentRouteExecutionMapper`、`PaymentRouteExecutionService` 与控制器，补齐支付路由执行结果独立查询能力。
2. 新增 `PaymentRouteExecutionMapper.xml`，聚合路由记录、支付单、预付单和最近支付请求上下文。
3. 新增 `PaymentRouteExecutionServiceImplTest`，校验查询条件的统一裁剪与分页口径。
4. 新增后台页面 `PaymentRoutesView`，支持独立路由页、展开详情、查看路由流水、支付请求和路由配置。

## 19. 2026-07-20 用户支付端多支付方式与补救动作增强复核

### 19.1 本轮验证结论

本轮围绕“用户支付端仍偏演示页，缺少支付前检查、多支付方式对比和失败补救指引”的问题进行了补强，确认 `app-web / h5-web / pc-web` 三端支付页已更接近真实产品交付口径。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| `app-web` 收银台增强 | 通过 | 已补齐支付前检查、会话风险提示、支付方式对比说明、失败补救建议和幂等键重置动作 |
| `app-web` 支付结果页增强 | 通过 | 已补齐按支付状态区分的建议下一步与补救动作 |
| `h5-web` 构建验证 | 通过 | 已复用同一套增强后的支付逻辑与展示能力 |
| `pc-web` 构建验证 | 通过 | 已在桌面端保留增强后的支付方式对比和补救建议展示 |
| 前端构建验证 | 通过 | `app-web / h5-web / pc-web` 三端生产构建全部通过 |

### 19.2 本轮修复项

1. 扩展 `CashierView`，补齐支付前检查、会话风险提示、支付方式对比、失败补救建议和幂等键重置动作。
2. 扩展 `ResultView`，补齐按支付状态区分的建议下一步与补救动作。
3. 调整共享样式，支撑新增的支付前检查区、对比区和建议清单展示。
4. 完成 `app-web / h5-web / pc-web` 三端生产构建复核，确保共享交易逻辑增强后仍可稳定交付。

## 20. 2026-07-20 支付监控 drill-down 增强复核

### 20.1 本轮验证结论

本轮围绕“支付监控页只有趋势和简单列表，缺少正式运营需要的摘要卡片、风险提示和排障跳转动作”的问题进行了补齐，确认监控页已经从“观察页”升级为“监控与排障入口页”。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端单元测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository test` | 通过 | 全量后端测试共 `55` 个并全部通过，新增覆盖 `findSummary()` 与监控聚合返回字段 |
| `admin-web` 构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-20260720-monitor-drilldown --emptyOutDir` | 通过 | 支付监控页新增摘要卡片、异常告警操作列、渠道风险说明与 drill-down 跳转后可稳定构建 |
| 监控总览接口 | `GET /api/payment-monitor/overview` | 已完成代码交付 | 当前已补齐 `summary`、`riskLevel/riskHint`、`suggestedAction/actionRoute`，前端已完成消费与跳转 |

### 20.2 本轮补齐项

1. 新增 `PaymentMonitorSummaryDTO`，统一承载监控摘要卡片数据。
2. 扩展 `PaymentMonitorOverviewDTO`，增加 `summary` 聚合返回。
3. 扩展 `PaymentChannelMetricDTO`，增加 `riskLevel`、`riskLevelType`、`riskHint`。
4. 扩展 `PaymentAlertItemDTO`，增加 `suggestedAction`、`actionRoute`。
5. 扩展 `PaymentMonitorMapper.xml`，增加监控摘要 SQL，并为渠道与告警结果补充风险和跳转信息。
6. 升级后台支付监控页，补齐四张摘要卡、异常告警排查动作和渠道明细钻取动作。

## 21. 2026-07-20 支付交易异常中心复核

### 21.1 本轮验证结论

本轮围绕“支付主链路异常分散在支付单、日志、事件、监控等多个页面，运营缺少统一排障中心”的问题进行了补齐，确认后台已具备支付交易异常聚合和联查入口。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端单元测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository test` | 通过 | 新增 `PaymentIssueServiceImplTest` 后，全量后端测试共 `56` 个并全部通过 |
| `admin-web` 构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-20260720-issue-center --emptyOutDir` | 通过 | 新增支付交易异常中心页面、路由和导航后可稳定构建 |
| 异常中心接口 | `GET /api/payment-issues` | 已完成代码交付 | 当前已聚合待回调未收口、回调待处理、下游事件发布失败、命中停用渠道四类异常，并支持推荐路由跳转 |

### 21.2 本轮补齐项

1. 新增 `PaymentIssueQueryDTO`、`PaymentIssueRowDTO`、`PaymentIssueMapper`、`PaymentIssueService`、`PaymentIssueController`。
2. 新增 `PaymentIssueMapper.xml`，聚合支付单、支付尝试、回调日志、事件出站和渠道状态，生成统一异常列表。
3. 新增后台页面 `PaymentIssuesView`，支持异常筛选、统一展示和一键排查。
4. 将支付监控页中“待回调未收口”告警接入支付交易异常中心，形成“监控 -> 异常 -> 详情”的排障链路。

## 22. 2026-07-20 运营筛选与排序增强复核

### 22.1 本轮验证结论

本轮围绕“账单中心、收银台会话、支付流水、统一支付记录虽然已可查询，但排序与运营筛选仍不够正式后台化”的问题进行了补齐，确认这几类高频运营页已经具备更实用的查询能力。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端单元测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository test` | 通过 | 新增 `PaymentRecordServiceListTest` 后，全量后端测试共 `57` 个并全部通过 |
| `admin-web` 构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-20260720-ops-filters --emptyOutDir` | 通过 | 账单中心、收银台会话、支付流水、统一支付记录增强后的筛选和排序控件可稳定构建 |
| 查询页增强 | 页面联调复核 | 通过 | 当前已补齐更多运营筛选字段和排序字段，便于大额单、超时会话、关键字报文和渠道维度排查 |

### 22.2 本轮补齐项

1. 扩展 `BillQueryDTO / BillController / BillMapper.xml`，补齐客户名称筛选和账单金额、待支付金额、到期时间排序。
2. 扩展 `CashierSessionQueryDTO / CashierSessionController / CashierSessionMapper.xml`，补齐支付单号、客户名称筛选和会话金额、失效时间排序。
3. 扩展 `PaymentFlowQueryDTO / PaymentFlowController / PaymentFlowMapper.xml`，补齐终端、关键字筛选以及重试次数、流水类型排序。
4. 扩展 `PaymentRecordQueryDTO / PaymentRecordController / PaymentRecordMapper.xml`，补齐支付状态、支付渠道筛选以及支付金额、支付成功时间排序。
5. 同步增强 `BillsView`、`CashierSessionsView`、`PaymentFlowsView`、`PaymentRecordsView` 和前端 API 参数封装，保证前后端口径一致。

## 11. 2026-07-20 支付记录详情钻取复核

### 11.1 本轮验证结论

本轮围绕后台收款记录页的“列表 -> 详情 -> 支付单 / 请求 / 日志”钻取链路进行了补齐，验证目标是让支付运营、研发和测试在不切 SQL 的前提下完成单笔支付问题定位。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端单元测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository test` | 通过 | 新增 `PaymentRecordServiceImplTest` 后，全量后端测试共 `38` 个并全部通过 |
| `admin-web` 构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-20260720-record-detail --emptyOutDir` | 通过 | 新增 `PaymentRecordDetailView`、路由和 API 调用均可稳定构建 |
| 收款记录详情接口 | `GET /api/payment-records/{paymentOrderId}` | 已完成代码交付 | 当前已完成 service、controller、mapper、前端详情页与文档同步，待下一轮联调时补充真实接口截图或 curl 记录 |

### 11.2 本轮补齐项

1. 新增 `PaymentRecordDetailDTO` 作为支付记录详情聚合对象。
2. 新增 `PaymentRecordService.detail` 与 `GET /api/payment-records/{paymentOrderId}` 接口。
3. 后台新增“支付记录详情”页面，支持返回来源列表、查看支付单详情、查看支付请求、查看处理日志、主动查单。
4. 将收款记录列表“支付记录”操作从直接跳支付单详情改为先进入支付记录详情页。
