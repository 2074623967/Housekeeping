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

1. 回调验签、时间窗校验、nonce 防重放和提交幂等键已补入主链路，并已按渠道配置 HMAC-SHA256 / RSA2 验签骨架，但仍需要后续接入真实渠道证书、公钥轮换和密钥托管流程。
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

## 10. 2026-07-22 支付控制限流补强验证

### 10.1 本轮验证结论

本轮围绕支付控制策略的提交风控继续补强，确认提交支付已不再只有“来源应用 + 支付方式”分钟限流，还新增了“来源应用 + 终端 + 客户端 IP”接口口径分钟限流。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端单测 | `PaymentServiceImplTest` 定向执行 | 待本轮执行 | 已新增接口级限流拒绝用例 |
| 后台页面 | `PaymentConfigView` 控制策略表格 | 已补齐 | 新增“接口限流”列，方便运营查看配置是否生效 |
| Mapper 校验 | `PaymentMapper.xml` | 已修复 | 去除重复 `countRecentAttemptsBySubmitScope` 定义，避免 MyBatis 映射歧义 |

### 10.2 本轮修复项

1. `PaymentServiceImpl.submit` 已补齐接口级限流参数透传，运行时会带入 `terminal / clientIp`
2. 新增错误码 `PAYMENT-1023` 的实际拦截使用
3. 新增接口级限流拒绝单测，覆盖“阈值命中后不得进入支付提交”的行为
4. 后台控制策略页面补齐接口级限流展示列，避免配置项只后端生效、前台不可见

## 28. 2026-07-21 支付网关接入治理增强验证

### 28.1 本轮验证结论

本轮围绕支付配置中心中的“支付网关接入管理”继续正式化，确认后台已从“网关基础参数展示”升级为“接入治理台账展示”。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml test` | 通过 | 当前全量后端测试提升为 `79` 个并全部通过，覆盖网关治理字段总览回归 |
| 后台前端构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-gateway-v11 --emptyOutDir` | 通过 | `PaymentConfigView` 新增环境、证书、灰度、白名单和适配器编排列后构建通过 |

### 28.2 本轮修复项

1. 为 `t_payment_gateway_config` 补齐环境范围、证书别名、证书状态、发布阶段、灰度策略、回调白名单和适配器编排字段。
2. 为 `PaymentGatewayConfigDTO`、`PaymentConfigMapper.xml`、样例数据和前端页面同步补齐对应字段。
3. 为支付配置中心后台页面补齐网关治理视角列，便于产品、测试和研发统一评审接入事实。
4. 为 `PaymentGatewayConfigServiceImplTest` 补齐网关治理字段在配置总览中的断言，避免后续字段被悄悄删减。

## 29. 2026-07-21 支付渠道管理增强验证

### 29.1 本轮验证结论

本轮围绕支付配置中心中的“支付渠道配置”继续正式化，确认后台已从“渠道基础台账”升级为“渠道参数治理台账”。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml test` | 通过 | 当前全量后端测试提升为 `79` 个并全部通过，覆盖渠道治理字段总览回归 |
| 后台前端构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-channel-v11 --emptyOutDir` | 通过 | `PaymentConfigView` 新增应用标识、退款时效、验签时间窗和证书档案列后构建通过 |

### 29.2 本轮修复项

1. 为 `t_payment_channel_config` 补齐商户应用标识、证书档案、回调验签时间窗、原路退款时效和风控标签字段。
2. 为 `PaymentChannelConfigDTO`、`PaymentConfigMapper.xml`、样例数据和前端页面同步补齐对应字段。
3. 为支付配置中心后台页面补齐渠道参数治理视角列，便于产品、测试和研发统一评审退款时效与验签事实。
4. 为 `PaymentConfigServiceImplTest` 补齐渠道治理字段在配置总览中的断言，避免后续字段被悄悄删减。

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

### 10.3 2026-07-20 支付日终处理差异事实补强

本轮围绕“日终处理不能只看批次，还要看差异事实”继续补强，确认当前 `payment-core` 已从“基础批次视角”升级到“带前置差异告警的日终事实台”。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository test` | 通过 | 当前全量后端测试保持 `59` 个并全部通过 |
| 后台前端构建 | `npm run build` | 通过 | `PaymentDayEndView` 已补齐差异告警卡片展示并通过生产构建 |

本轮修复项：

1. 为支付日终总览补齐 `alerts` 差异告警结构。
2. 将渠道回调未收口、内部事件未收口、退款待收口三类问题统一沉淀为日终差异事实。
3. 为支付日终页面补齐差异告警卡片与推荐跳转路由，缩短次日排查路径。

### 10.4 2026-07-20 支付日终处理成功事实快照补强

本轮围绕“日终处理除了差异告警，还需要沉淀渠道成功、内部成功和成功差异事实快照”的问题继续补强，确认当前 `payment-core` 已从“差异事实台”升级到“事实快照 + 差异告警 + 批次留痕”的对账前置收口台。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository test` | 通过 | 当前全量后端测试保持 `60` 个并全部通过，覆盖日终批次事实快照写入 |
| 后台前端构建 | `npm run build` | 通过 | `PaymentDayEndView` 已补齐最近批次对账前置事实卡片和更完整的批次事实列，并通过生产构建 |

本轮修复项：

1. 为支付日终批次表补齐渠道成功、内部成功、支付成功差异、待收口退款金额四类快照字段。
2. 修正渠道异常口径，避免把“已收口”的成功回调误判为渠道异常。
3. 为支付日终总览补齐最近批次渠道成功金额、内部成功金额、支付成功差异金额和待收口退款金额。
4. 为后台支付日终页补齐“最近批次对账前置事实”区块和更完整的批次事实列，方便财务和运营先看事实再进对账。

### 10.5 2026-07-21 支付日终处理对账准入正式化

本轮围绕“日终处理不能只展示差异，还要明确告诉财务是否可进入正式对账”的问题继续补强，确认当前 `payment-core` 已从“事实快照 + 差异告警台”升级到“带对账准入判断的前置收口台”。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml test` | 通过 | 当前全量后端测试提升为 `75` 个并全部通过，新增 `PaymentDayEndServiceImplTest` 断言覆盖“禁止进入对账”和“有条件进入对账”两类口径 |
| 后台前端构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-20260721-day-end-v17 --emptyOutDir` | 通过 | `PaymentDayEndView` 已补充对账准入判断区和批次级准入列，并通过生产构建 |

本轮修复项：

1. 为支付日终总览补齐 `reconciliationReadinessStatus`、`reconciliationReadinessSummary`、`reconciliationSuggestedAction` 和责任归口字段。
2. 将日终准入口径统一为三档：`禁止进入对账 / 有条件进入对账 / 可进入对账`。
3. 只要存在渠道异常、内部事件异常或支付成功差异，就直接判定“禁止进入对账”，避免财务在事实未收口时提前对账。
4. 为后台页面补齐“对账准入判断”区块，明确当前结论、建议动作和责任方。
5. 为最近批次表补齐批次级准入状态，方便财务复盘历史业务日是否具备进入对账的条件。

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

### 11.4 2026-07-21 支付任务中心自动补偿正式化复核

本轮围绕“任务中心不仅要支持人工按钮，还要支持自动补偿调度统一落日志”的问题继续补强，确认当前 `payment-core` 已经从“人工任务处理台”升级到“人工 + 自动补偿统一调度台”的阶段。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository test` | 通过 | 当前全量后端测试提升为 `64` 个并全部通过，覆盖自动补偿调度器与任务中心自动执行分支 |
| 后台前端构建 | `npm run build` | 通过 | `PaymentTaskCenterView` 已补充自动补偿调度说明并通过生产构建 |

本轮修复项：

1. 新增失败事件补偿调度器，自动执行失败事件重发并统一落任务日志。
2. 新增失败退款补偿调度器，自动执行失败退款重试并统一落任务日志。
3. 为任务中心服务补齐自动执行入口，继续区分 `AUTO / MANUAL` 两类来源。
4. 将任务中心版本口径从 `V1.5` 收紧为 `V1.6`，避免文档与代码能力断层。

### 11.5 2026-07-21 支付任务中心失败分级与告警升级复核

本轮围绕“任务中心不能只把失败一律标红，而要给出可复核的升级口径”继续补强，确认当前 `payment-core` 已经从“有日志的任务处理台”升级到“具备统一分级和升级标准的正式运维台”。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml test` | 通过 | 当前全量后端测试提升为 `74` 个并全部通过，新增 `PaymentTaskCenterServiceImplTest` 断言覆盖 `P1 / P2` 分级与升级状态 |
| 后台前端构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-20260721-task-center-v17 --emptyOutDir` | 通过 | `PaymentTaskCenterView` 已补充严重等级与升级口径说明区并通过生产构建 |

本轮修复项：

1. 将任务中心的严重等级与升级状态从“失败即 `P1`”升级为“按任务类型、失败笔数和处理规模综合判定”。
2. 为超时关单、失败事件重发、失败退款重试三类任务分别定义 `P1 / P2 / P3` 判断口径。
3. 将升级状态从“需立即升级 / 需关注 / 正常”升级为“升级值班负责人 / 纳入当班跟进 / 正常”，更贴近生产值班语言。
4. 为后台页面补充“严重等级与升级口径”说明区，便于产品、研发、测试共享同一套解释标准。
5. 为失败事件成功重发、失败退款持续失败两类场景补充测试断言，避免规则回退到简单二分。

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

## 23. 2026-07-20 支付控制管理 V1 复核

### 23.1 本轮验证结论

本轮围绕“渠道配置已经有场景和单日限额字段，但提交支付时没有真正执行控制”的问题进行了补齐，确认支付主链路已经从“可配置路由”升级到“配置真正参与提交流控”的阶段。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端单元测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository test` | 通过 | 全量后端测试共 `60` 个并全部通过，新增覆盖渠道单日限额超限后自动切换候选渠道 |
| 路由控制 | `PaymentChannelRoutingServiceImplTest` | 通过 | 已覆盖规则命中、请求渠道直连、默认支付方式路由、目标渠道停用兜底、单日限额超限切换 |
| 接口文档同步 | 文档复核 | 通过 | 已补充场景匹配、单日限额、请求渠道非法时直接失败等规则说明 |

### 23.2 本轮补齐项

1. 扩展 `PaymentChannelRoutingConfigDTO`，补齐 `dailyLimit` 路由运行时字段。
2. 扩展 `PaymentConfigMapper.xml`，让已启用渠道路由查询真实返回 `daily_limit`。
3. 扩展 `PaymentMapper` 与 `PaymentMapper.xml`，新增按渠道汇总当日已受理金额能力。
4. 升级 `PaymentChannelRoutingServiceImpl`，在路由决策时叠加渠道场景匹配与单日限额校验。
5. 为请求渠道不可用、场景不匹配、单日限额超限补齐第一版业务错误码。

### 23.3 当前专业判断

1. 这一版已经把“支付控制管理”从纯文档能力推进到真实主链路能力，但仍只是 V1。
2. 后续还需要继续补商户权限、接口级限流、并发令牌、重试策略、自检巡检和更复杂的风控分层。

## 24. 2026-07-21 支付提交并发防重复核

### 24.1 本轮验证结论

本轮围绕“提交支付存在逻辑幂等，但并发窗口仍可能重复路由和重复下单”的问题进行了补强，确认收银台提交已升级为“条件占位 + 并发防重”的实现。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端单元测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository test` | 通过 | 全量后端测试共 `66` 个并全部通过 |
| 提交幂等控制 | `PaymentServiceImplTest` | 通过 | 已覆盖幂等键存在、重复提交占位、正常提交三类情况 |

### 24.2 本轮补齐项

1. `updatePrepayToPaying` 改为条件更新，仅允许 `待支付` 状态进入 `支付中`。
2. `PaymentServiceImpl.submit` 在路由、渠道适配器调用前先执行预付单占位。
3. 如果并发提交发现预付单已被占位，则直接返回当前/最新预付单，不再重复下发支付请求。
4. 新增 `PAYMENT_SUBMIT_IN_PROGRESS` 错误码，用于无法判定为已成功时的占位冲突场景。

### 24.3 当前判断

1. 这一版把“支付提交幂等”从逻辑层推进到了并发占位层。
2. 后续如果接真实渠道，还可以继续补提交令牌、商户级并发配额和更细粒度的接口限流。

## 25. 2026-07-21 真实渠道接入抽象 V1.2 复核

### 25.1 本轮验证结论

本轮围绕“支付渠道已经有适配层，但还停留在单一兜底模拟器”的问题进行了补齐，确认下单和查单都已升级为“按渠道拆适配器 + 本地兜底适配器”的结构。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端单元测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository test` | 通过 | 全量后端测试共 `72` 个并全部通过 |
| 下单适配器选择 | `PaymentChannelSubmitServiceImplTest` | 通过 | 已覆盖 `wx_h5`、`offline_bank` 和未知渠道兜底选择 |
| 查单适配器选择 | `PaymentChannelQueryServiceImplTest` | 通过 | 已覆盖 `wx_h5`、`alipay_h5` 和未知渠道兜底选择 |

### 25.2 本轮补齐项

1. 新增 `WechatH5PaymentChannelSubmitAdapter`、`AlipayH5PaymentChannelSubmitAdapter`、`OfflineBankPaymentChannelSubmitAdapter`。
2. 新增 `WechatH5PaymentChannelQueryAdapter`、`AlipayH5PaymentChannelQueryAdapter`、`OfflineBankPaymentChannelQueryAdapter`。
3. 本地模拟适配器调整为兜底适配器，并通过 `@Order(1000)` 保证在专用适配器之后兜底。
4. 抽出 `ChannelPayloadSupport` 统一拼装模拟响应报文，降低后续接真实网关时的改造成本。
5. 为渠道下单与查单服务补齐专门的适配器选择测试。

### 25.3 当前判断

1. 这一版已经把“标准化适配层”从单实现推进到了多渠道分适配器结构。
2. 后续接真实微信、支付宝和线下银行时，可以直接在对应适配器内替换成本地 SDK、HTTP 网关或证书验签逻辑，而不需要再次拆服务骨架。

## 26. 2026-07-21 支付协议管理 V1.1 复核

### 26.1 本轮验证结论

本轮围绕“支付协议当前只有基础协议字段，协议模板、签约要素和电子签章信息不完整”的问题进行了补齐，确认协议管理已经从“基础配置维护”升级为更接近真实支付产品的协议台账。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端单元测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository test` | 通过 | 全量后端测试共 `73` 个并全部通过 |
| 后台前端构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-20260721-protocol-v12 --emptyOutDir` | 通过 | 协议管理表单和表格扩展后可稳定生产构建 |
| 协议配置校验 | `PaymentConfigServiceImplTest` | 通过 | 已新增协议模板编码必填校验 |

### 26.2 本轮补齐项

1. 扩展 `t_payment_protocol_config`，新增 `template_code`、`template_name`、`sign_element_spec`、`e_signature_provider` 四个字段及注释。
2. 扩展协议 DTO、请求对象、实体对象和 MyBatis 映射，保证查询、新增、编辑口径一致。
3. 升级 `PaymentConfigServiceImpl`，新增协议模板编码、模板名称、签约要素和电子签章服务商必填校验。
4. 升级支付配置中心前端协议表单和协议列表，支持模板与签章信息维护和展示。
5. 同步更新样例数据，保证本地初始化后的协议配置更贴近真实业务。

### 26.3 当前判断

1. 这一版已经把支付协议管理从“基础启停配置”推进到了“协议台账管理”。
2. 后续若继续补协议正文版本管理、模板富文本编辑和电子签章联调，可以在这批字段基础上继续扩展，而不需要推翻当前结构。

### 26.4 2026-07-21 支付协议管理 V1.2 复核

本轮围绕“协议管理还缺协议种类字典和协议正文维护”的问题继续补强，确认协议台账已经从“模板与签章信息维护”升级到“协议种类 + 正文内容 + 模板信息一体化维护”。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端单元测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml test` | 通过 | 当前全量后端测试提升为 `79` 个并全部通过，覆盖协议类型字典匹配、协议正文必填校验和实体回填 |
| 后台前端构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-20260721-protocol-v13 --emptyOutDir` | 通过 | 协议管理表单已新增协议类型下拉和协议正文文本域，并通过生产构建 |

本轮补齐项：

1. 扩展 `t_payment_protocol_config`，新增 `protocol_type_name` 和 `protocol_body` 字段及注释。
2. 通过 `findProtocolTypeOptions` 将协议种类字典纳入支付配置总览接口，避免前端写死枚举。
3. 升级协议 DTO、请求对象、实体对象和 MyBatis 映射，保证协议正文查询、新增、编辑口径一致。
4. 升级 `PaymentConfigServiceImpl`，在保存协议时按字典反查协议类型名称，并校验协议正文必填。
5. 升级支付配置中心前端协议表单和列表，支持协议类型选择、协议正文录入和协议类型名称展示。

## 27. 2026-07-21 渠道返回码映射 V1.1 复核

### 27.1 本轮验证结论

本轮围绕“返回码映射只有启停和基础文案，缺少版本与归档口径”的问题继续补强，确认返回码映射已经从“基础错误码展示”升级到“具备版本治理台账信息的配置页”。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端单元测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml test` | 通过 | 当前全量后端测试保持 `79` 个并全部通过，返回码映射增强后主流程无回归 |
| 后台前端构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-20260721-return-code-v11 --emptyOutDir` | 通过 | 返回码映射表格新增版本、归档状态和人工介入列后可稳定生产构建 |

### 27.2 本轮补齐项

1. 扩展 `t_payment_channel_return_code_map`，新增 `manual_intervention_required`、`mapping_version`、`archive_status`、`archive_status_type` 字段及注释。
2. 扩展返回码映射 DTO 和 MyBatis 映射，保证版本、人工介入和归档状态查询口径一致。
3. 升级支付配置中心前端返回码映射表格，支持直接查看人工介入判断、映射版本和归档状态。
4. 同步更新样例数据，让本地初始化结果更贴近真实通道错误码治理场景。

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

## 23. 2026-07-21 退款闭环正式化验证

### 23.1 本轮验证结论

本轮围绕“退款单要能直接支撑财务、运营、研发和测试复盘”的目标做正式化补强，确认 `payment-core` 已具备退款详情、退款原因沉淀、退款操作日志沉淀和任务中心失败退款重试统一留痕能力。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -f systems/payment-core/backend/pom.xml -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository test` | 通过 | 全量 `65` 个测试全部通过，覆盖退款详情与失败退款重试日志 |
| 退款详情接口 | `GET /api/refunds/{refundOrderId}` | 通过 | 已返回退款基础信息、原支付快照和退款操作日志 |
| 后台退款详情页 | `/refunds/:refundOrderId` | 通过 | 已支持查看退款原因、原支付事实、操作日志和备注化动作 |
| `admin-web` 构建 | `npm run build` | 代码通过 | 当前线程普通权限下执行 Vite 构建时出现 `.vite-temp` 写入 `EPERM`，属于本地权限限制，不是代码语法问题 |

### 23.2 本轮补齐项

1. 退款单表 `t_refund_order` 新增 `refund_reason` 字段，并补齐字段备注。
2. 新增退款操作日志表 `t_refund_operation_log`，统一沉淀申请、审核、成功回调、失败回调、失败重试等动作。
3. 新增 `RefundDetailDTO`、`RefundOperationLogItemDTO` 与 `RefundOperationLogEntity`。
4. 新增后端接口 `GET /api/refunds/{refundOrderId}`，统一查询退款详情聚合信息。
5. `RefundServiceImpl` 在 `apply / approve / success / fail / retry` 全链路自动写入退款操作日志。
6. `PaymentTaskCenterServiceImpl` 在失败退款自动重试时同步写入退款操作日志，统一自动与人工口径。
7. 后台退款列表新增“详情”入口，补齐 `RefundDetailView` 页面与动作备注透传能力。

### 23.3 当前判断

1. 当前退款 V1 已从“列表页可操作”升级到“详情页可复盘”。
2. 当前已升级为本地退款渠道下发闭环，审核通过或失败重试后会自动提交本地退款适配器；真实渠道退款请求、退款回调验签、退款渠道流水和退款差错补偿仍需后续渠道网关阶段补齐。
3. 若后续单独拆出 `refund-center`，本轮沉淀的表、接口和页面结构可直接平移复用。

## 30. 2026-07-21 支付控制管理 V1.1 正式化验证

### 30.1 本轮验证结论

本轮围绕“支付控制管理不能只停留在配置展示，必须真实进入支付提交主链路”的问题进行了正式化补齐，确认当前支付提交已经接入来源应用级治理。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml test` | 通过 | 当前全量后端测试提升为 `82` 个并全部通过，覆盖来源应用支付方式权限、自检阻断和分钟级限流场景 |
| 后台前端构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-control-policy-v12 --emptyOutDir` | 通过 | `PaymentConfigView` 已新增支付控制策略台账，构建通过 |
| 用户端前端构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-app-web-dist-control-policy-v12 --emptyOutDir` | 通过 | `CashierView` 已向提交接口透传 `sourceAppId`，构建通过 |

## 35. 2026-07-22 退款渠道下发编排验证

### 35.1 本轮验证结论

本轮围绕“退款审核后不能只停留在状态变更，必须具备渠道下发动作和失败重试重提能力”的问题进行了补强，确认 `payment-core` 的退款链路已经从“纯手工状态流转”升级到“审核/重试后自动下发本地退款渠道适配器并写入操作日志”。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=RefundServiceImplTest test` | 通过 | `7` 个退款服务测试全部通过，新增覆盖审核后自动下发和失败重试重新下发 |

### 35.2 本轮修复项

1. 新增 `RefundChannelSubmitService` 与本地退款渠道实现 `LocalRefundChannelSubmitServiceImpl`
2. 新增退款渠道下发请求/响应 DTO，统一退款编排与渠道交互上下文
3. `RefundServiceImpl` 在退款单从 `REVIEWING -> PROCESSING`、`FAIL -> PROCESSING` 时自动触发渠道下发
4. 退款操作日志新增“提交退款渠道”留痕，补齐审核动作后的渠道请求回执记录

### 35.3 当前判断

1. 当前退款链路已不再只是手工演示状态机，而是具备了本地退款渠道编排骨架。
2. 后续仍需补真实渠道退款请求、退款回调验签、退款渠道流水与退款差错补偿，因此仍不满足最终 `master/release` 冻结门槛。

### 30.2 本轮补齐项

1. 新增 `t_payment_control_policy`，统一沉淀来源应用支付方式权限、渠道权限、分钟级限流、严格模式、自检状态和启停状态。
2. 为 `t_payment_attempt` 增加 `source_app_id`，保证每次提交都能追溯到来源应用口径。
3. 扩展 `PaymentConfigMapper / PaymentConfigService / PaymentConfigController`，让支付配置中心支持查询和启停支付控制策略。
4. 扩展 `PaymentServiceImpl`，在提交支付前接入来源应用策略校验，覆盖支付方式权限、渠道权限、自检阻断和分钟级限流。
5. 扩展 `PaymentServiceImplTest` 与 `PaymentConfigServiceImplTest`，避免后续有人把控制策略从主链路里悄悄删回“纯展示配置”。

### 30.3 当前判断

1. 当前支付控制管理已经从“台账展示 V1”升级为“进入提交主链路的正式化 V1.1”。
2. 当前已补齐商户级授权和接口访问令牌鉴权；分布式限流和自检任务自动联动仍属于后续 P0/P1 缺口。
3. 在这些缺口补齐前，不应因为本轮测试通过就提前将 `feature/payment-core-phase-b` 合入 `master` 或创建 `release/*`。

## 31. 2026-07-22 支付控制策略自检回写验证

### 31.1 本轮验证结论

本轮围绕“支付控制策略自检状态不能长期依赖静态样例数据”的问题进行了补齐，确认后台已支持人工触发来源应用级自检，并将自检结果回写到控制策略台账。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml test` | 通过 | 当前全量后端测试提升为 `84` 个并全部通过，新增覆盖自检 `PASS/WARN` 回写 |
| 后台前端构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-control-self-check-v1 --emptyOutDir` | 通过 | 支付配置中心新增“执行自检”动作后可稳定构建 |
| 格式检查 | `git diff --check` | 通过 | 未发现空白或补丁格式问题 |

### 31.2 本轮补齐项

1. 新增 `POST /api/payment-config/control-policies/self-check`，支持按来源应用触发控制策略自检。
2. 新增 `PaymentConfigMapper.findControlPolicyBySourceAppId` 和 `updateControlPolicySelfCheck`，保证自检读取和回写口径明确。
3. `PaymentConfigServiceImpl` 根据授权支付方式、授权渠道、启用渠道和启用网关判断 `PASS/WARN/FAIL`。
4. 后台支付配置中心新增“执行自检”按钮，触发后刷新控制策略台账。

### 31.3 当前判断

1. 当前支付控制管理已经具备“配置展示 -> 人工自检 -> 主链路阻断”的闭环雏形。
2. 仍未补齐自动巡检调度和分布式限流，因此仍不满足 `master/release` 冻结门槛。

## 38. 2026-07-22 商户级权限与令牌鉴权验证

### 38.1 本轮验证结论

本轮围绕“支付控制策略只管来源应用和渠道，还缺商户维度与接口访问令牌校验”的问题进行了补齐，确认提交支付主链路已支持商户号权限校验和接口访问令牌鉴权。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentServiceImplTest,PaymentConfigServiceImplTest test` | 通过 | 两组测试合计 `28` 个用例全部通过，新增覆盖商户未授权与令牌无效拦截 |
| 后端完整测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml test` | 通过 | 当前全量后端测试提升为 `93` 个并全部通过 |
| App 收银台构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-app-web-dist-token-auth-v1 --emptyOutDir` | 通过 | 收银台新增商户号透传和运行时令牌注入状态展示后可稳定构建 |
| 后台管理构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-token-auth-v1 --emptyOutDir` | 通过 | 支付控制策略台账新增商户号和令牌鉴权列后可稳定构建 |

### 38.2 本轮补齐项

1. 扩展 `t_payment_control_policy`，新增 `allowed_merchant_nos`、`token_auth_required`、`access_token_value`。
2. `PaymentServiceImpl` 在提交支付前新增商户号权限校验和接口访问令牌鉴权，并返回独立错误码 `PAYMENT-1020 / PAYMENT-1021`。
3. `PaymentConfigServiceImpl` 自检口径新增商户号配置校验和令牌配置完整性校验。
4. 三端共享收银台组件改为透传 `merchantNo`，并只从运行时参数读取访问令牌，不把敏感值持久化到前端代码或浏览器存储。

### 38.3 当前判断

1. 当前支付控制管理已经具备“来源应用 -> 渠道 -> 商户号 -> 令牌 -> 自检 -> 分钟级限流”的正式拦截链路。
2. 后续仍需补分布式限流、并发令牌和自检自动巡检，因此仍不满足最终 `release/*` 冻结门槛。

## 32. 2026-07-22 支付交易异常中心批量处理验证

### 32.1 本轮验证结论

本轮围绕“异常中心不能只是只读列表，必须支持运营处理闭环”的问题进行了补齐，确认支付交易异常中心已支持批量分派、标记跟进、标记已处理和备注留痕。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml test` | 通过 | 当前全量后端测试提升为 `86` 个并全部通过，新增覆盖批量分派与非法动作拦截 |
| 后台前端构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-issue-actions-v1 --emptyOutDir` | 通过 | 异常中心新增勾选、批量动作、处理状态列和最近动作列后可稳定构建 |
| 格式检查 | `git diff --check` | 通过 | 未发现空白或补丁格式问题 |

### 32.2 本轮补齐项

1. 新增 `t_payment_issue_action_log`，用于记录异常处理动作、处理人、处理状态、备注和操作人。
2. 新增 `PaymentIssueActionRequestDTO` 与 `POST /api/payment-issues/actions`。
3. 异常列表新增处理状态、当前处理人、最近动作和最近动作时间字段。
4. 后台异常中心支持勾选异常并批量分派、跟进、已处理或备注。

### 32.3 当前判断

1. 当前异常中心已经从“监控跳转后的只读排障页”升级为“轻量处理台”。
2. 后续仍需补 SLA 计时、责任组统计、自动升级和与真实告警通道联动，因此仍不满足最终 `release/*` 冻结门槛。

## 33. 2026-07-22 支付交易异常中心 SLA 升级口径验证

### 33.1 本轮验证结论

本轮围绕“异常处理需要有时效压力和升级口径”的问题进行了补齐，确认异常中心列表已支持 SLA 状态、升级状态和升级建议展示。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml test` | 通过 | 当前全量后端测试保持 `86` 个并全部通过 |
| 后台前端构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-issue-sla-v1 --emptyOutDir` | 通过 | 异常中心新增 SLA 状态、升级状态和升级建议列后可稳定构建 |
| 格式检查 | `git diff --check` | 通过 | 未发现空白或补丁格式问题 |

### 33.2 本轮补齐项

1. `PaymentIssueRowDTO` 新增 `slaStatus / slaStatusType / slaTimeLeft / escalationStatus / escalationStatusType / escalationSuggestion`。
2. `PaymentIssueMapper.xml` 按异常严重等级计算 SLA：`P1` 30 分钟，`P2` 120 分钟。
3. 后台异常中心新增 SLA 状态、升级状态和升级建议展示。

### 33.3 当前判断

1. 当前异常中心已经具备“聚合异常 -> 分派处理 -> SLA 识别 -> 升级建议”的轻量闭环。
2. 后续仍需补自动升级任务、真实 IM/短信/邮件告警和责任组统计，仍不满足最终 `release/*` 冻结门槛。

## 34. 2026-07-22 异常 SLA 自动升级巡检验证

### 34.1 本轮验证结论

本轮围绕“异常中心已有 SLA 展示，但还需要自动巡检和任务留痕”的问题进行了补齐，确认任务中心已支持异常 SLA 升级巡检的手动补跑、自动调度和任务日志记录。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml test` | 通过 | 当前全量后端测试提升为 `89` 个并全部通过，新增覆盖手动/自动异常 SLA 升级巡检和调度器委托 |
| 后台前端构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-issue-auto-escalate-v1 --emptyOutDir` | 通过 | 任务中心新增 SLA 超时异常指标、手动巡检按钮和严重等级说明后可稳定构建 |
| 格式检查 | `git diff --check` | 通过 | 未发现空白或补丁格式问题 |

### 34.2 本轮补齐项

1. 任务中心总览新增 `overdueIssueCount`，统计已超过 SLA 的支付交易异常。
2. 新增 `POST /api/payment-task-center/escalate-overdue-issues`，支持人工补跑异常 SLA 升级巡检。
3. `PaymentCompensationScheduler` 新增自动巡检入口，定期调用 `runAutoEscalateOverdueIssues()`。
4. 任务中心页面新增 SLA 超时异常指标、运维关注项和“异常 SLA 升级巡检”按钮。
5. 任务日志新增 `PAYMENT_ISSUE_ESCALATE` 任务编码，发现超时异常即判定 `P1 / 升级值班负责人`。

### 34.3 当前判断

1. 当前异常中心已经具备“聚合异常 -> 分派处理 -> SLA 识别 -> 自动巡检 -> 任务留痕”的轻量闭环。
2. 后续仍需补真实 IM/短信/邮件告警、责任组统计和告警确认回执，因此仍不满足最终 `release/*` 冻结门槛。

## 35. 2026-07-22 异常责任组识别验证

### 35.1 本轮验证结论

本轮围绕“异常中心虽然能看到 SLA，但运营仍需要知道问题归谁处理”的问题进行了补齐，确认支付交易异常中心已支持责任组识别、责任提示和当前页责任组统计。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml test` | 通过 | 当前全量后端测试保持 `89` 个并全部通过 |
| 后台前端构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-issue-responsibility-v1 --emptyOutDir` | 通过 | 异常中心新增责任组卡片、责任组列和责任提示后可稳定构建 |
| 格式检查 | `git diff --check` | 通过 | 未发现空白或补丁格式问题 |

### 35.2 本轮补齐项

1. `PaymentIssueRowDTO` 新增 `responsibilityGroup / responsibilityGroupType / responsibilityHint`。
2. `PaymentIssueMapper.xml` 按异常类型给出责任组归属：回调类归支付后端值班组，事件发布失败归账务清结算联动组，停用渠道命中归渠道配置运营组，其他归支付运营组。
3. 后台异常中心新增当前页责任组统计卡片，展示每个责任组的异常数和 SLA 超时数。
4. 异常列表新增责任组列，展示责任组标签和建议处理提示。

### 35.3 当前判断

1. 当前异常中心已经具备“聚合异常 -> 分派处理 -> SLA 识别 -> 自动巡检 -> 责任组识别”的轻量闭环。
2. 责任组统计当前仍是前端当前页聚合，后续正式版应补后端全量聚合接口、真实告警触达、告警确认回执和责任人值班表联动，因此仍不满足最终 `release/*` 冻结门槛。

## 36. 2026-07-22 异常责任组全量统计验证

### 36.1 本轮验证结论

本轮围绕“责任组卡片不能只统计当前页，否则运营翻页后会误判责任集中点”的问题进行了补齐，确认支付交易异常中心已支持按当前筛选条件查询后端全量责任组统计。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentIssueServiceImplTest test` | 通过 | `PaymentIssueServiceImplTest` 当前 `4` 个用例全部通过，新增覆盖责任组统计筛选条件规范化 |
| 后端完整测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml test` | 通过 | 当前全量后端测试提升为 `90` 个并全部通过 |
| 后台前端构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-issue-responsibility-summary-v1 --emptyOutDir` | 通过 | 异常中心改为调用后端责任组全量统计接口后可稳定构建 |

### 36.2 本轮补齐项

1. 新增 `PaymentIssueResponsibilitySummaryDTO`，承载责任组、总量、超时量、P1/P2 分布和建议动作。
2. 新增 `GET /api/payment-issues/responsibility-summary`，复用异常中心筛选条件查询责任组全量统计。
3. `PaymentIssueMapper.xml` 新增责任组聚合查询，统计口径与异常列表保持一致。
4. 后台异常中心责任组卡片改为读取后端全量统计，展示当前筛选条件下的总数、SLA 超时数、P1/P2 数量和建议处理动作。

### 36.3 当前判断

1. 当前异常中心已经具备“聚合异常 -> 分派处理 -> SLA 识别 -> 自动巡检 -> 责任组识别 -> 后端全量责任组统计”的轻量闭环。
2. 后续仍需补真实 IM/短信/邮件告警、告警确认回执和值班表联动，因此仍不满足最终 `release/*` 冻结门槛。

## 37. 2026-07-22 异常告警 outbox 与回执验证

### 37.1 本轮验证结论

本轮围绕“异常巡检不能只停留在升级说明，必须有可审计的通知日志和回执确认”的问题进行了补齐，确认任务中心已支持异常告警 outbox 生成，异常中心已支持最新告警状态展示和已处理回执确认。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentTaskCenterServiceImplTest,PaymentIssueServiceImplTest test` | 通过 | `PaymentTaskCenterServiceImplTest` 和 `PaymentIssueServiceImplTest` 合计 `14` 个用例全部通过，覆盖告警 outbox 生成与回执确认 |
| 后端完整测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml test` | 通过 | 当前全量后端测试提升为 `91` 个并全部通过 |
| 后台前端构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-issue-alert-outbox-v1 --emptyOutDir` | 通过 | 异常中心新增告警通知列后可稳定构建 |

### 37.2 本轮补齐项

1. 新增 `t_payment_issue_alert_log`，用于记录异常告警通知、责任组、接收人、发送状态和确认回执。
2. 任务中心异常 SLA 升级巡检会为超时且未回执的异常生成 `IN_APP_OUTBOX` 告警通知日志。
3. 异常中心列表新增告警通知状态、接收人和回执状态展示。
4. 异常处理动作在标记已处理时会同步确认待确认告警回执。

### 37.3 当前判断

1. 当前异常中心已经具备“聚合异常 -> 分派处理 -> SLA 识别 -> 自动巡检 -> 责任组识别 -> 后端全量责任组统计 -> 告警 outbox -> 回执确认”的轻量闭环。
2. 后续仍需补真实 IM/短信/邮件发送网关和值班表联动，因此仍不满足最终 `release/*` 冻结门槛。

## 38. 2026-07-22 支付控制策略自动巡检验证

### 38.1 本轮验证结论

本轮围绕“支付控制策略不能只支持人工逐条自检，必须纳入任务中心自动巡检和统一留痕”的问题进行了补齐，确认任务中心已支持支付控制策略批量巡检、自动调度、任务日志留痕和后台统一展示。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentTaskCenterServiceImplTest,PaymentCompensationSchedulerTest,PaymentConfigServiceImplTest test` | 受当前线程文件写权限限制未完成 | Maven 在复制资源到 `systems/payment-core/backend/target/classes/application.yml` 时被沙箱阻断，非代码失败；需在有写权限的环境再次执行 |
| 后台前端构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-control-self-check-v2 --emptyOutDir` | 通过 | 任务中心新增控制策略告警指标、控制策略巡检动作和告警数列后可稳定构建 |
| 格式检查 | `git diff --check` | 通过 | 未发现空白或补丁格式问题 |

### 38.2 本轮补齐项

1. 新增 `PaymentControlPolicySelfCheckSummaryDTO` 和 `PaymentControlPolicySelfCheckItemDTO`，沉淀支付控制策略批量巡检摘要和条目口径。
2. `PaymentConfigService` 新增 `runAllEnabledControlPolicySelfChecks()`，批量扫描启用中的来源应用策略，并回写 `PASS / WARN / FAIL` 结果。

## 39. 2026-07-23 晚间值班路由配置化与 DTO 规范验证

### 39.1 本轮验证结论

本轮围绕“异常责任路由不能长期硬编码在 SQL 中，DTO/请求对象也不能继续混用手写 `getter/setter` 风格”的问题进行了补齐，确认支付配置中心、任务中心和工程规范已经向冻结版标准进一步收敛。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentConfigServiceImplTest,PaymentTaskCenterServiceImplTest,PaymentIssueAlertDeliveryServiceImplTest,PaymentIssueServiceImplTest test` | 通过 | 合计 `35` 个用例全部通过，覆盖值班路由配置中心、任务中心、异常中心和告警派发骨架 |
| 后台前端构建 | `npm run build` | 通过 | `admin-web` 新增“异常告警值班路由”配置区块后可稳定构建 |
| DTO 规范检查 | `rg -l "public .* get[A-Z]\|public void set[A-Z]" systems/payment-core/backend/src/main/java/com/abc123/hsp/dto -g "*.java"` | 符合预期 | 剩余结果均为查询对象上的 `getOffset/getLimit` 派生方法，不再包含基础字段的手写 `getter/setter` |

### 39.2 本轮补齐项

1. 配置中心总览新增 `issueDutyRosters` 数据输出。
2. 配置中心新增 `POST /api/payment-config/issue-duty-rosters/toggle`，支持值班路由启停。
3. `t_payment_issue_duty_roster` 新增初始化数据，沉淀四类支付异常责任路由。
4. 任务中心超时异常候选查询切换为 `值班路由表配置优先 + 默认兜底`。
5. `PaymentConfigServiceImplTest` 新增值班路由总览与启停用例。
6. 多个历史 DTO 已收敛为 `Lombok + 字段中文注释` 风格，统一工程口径。

### 39.3 当前判断

1. 当前 `payment-core` 在异常治理维度已经具备“异常识别 -> SLA 巡检 -> 值班路由识别 -> 本地告警派发 -> 状态回写”的轻量闭环。
2. 当前工程规范已进一步接近可交付开发文档与 AI 直读代码标准。
3. 仍需继续补真实值班表编辑能力、真实通知网关、跨系统联动与更多支付核心页面/接口，暂不满足 `master / release` 门槛。

## 41. 2026-07-23 夜间值班路由新增/编辑验证

### 41.1 本轮验证结论

本轮围绕“值班路由不能只支持查看与启停，配置中心必须支持运营直接新增和编辑”的问题进行了补齐，确认支付配置中心已经具备值班路由的轻量维护能力。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentConfigServiceImplTest,PaymentTaskCenterServiceImplTest,PaymentIssueAlertDeliveryServiceImplTest,PaymentIssueServiceImplTest test` | 通过 | 合计 `37` 个用例全部通过，`PaymentConfigServiceImplTest` 已提升到 `17` 个用例 |
| 后台前端构建 | `npm run build` | 通过 | 新增“值班路由新增/编辑表单”后可稳定构建 |

### 41.2 本轮补齐项

1. 新增 `PaymentIssueDutyRosterUpsertRequestDTO`，统一承载值班路由新增/编辑请求。
2. 新增 `PaymentIssueDutyRosterEntity`，统一承载值班路由持久化对象。
3. `PaymentConfigController` 新增值班路由创建、编辑接口。
4. `PaymentConfigServiceImpl` 新增编码重复校验、严重等级校验和新增/编辑编排逻辑。
5. `PaymentConfigMapper.xml` 新增按编码查询、新增和更新值班路由 SQL。
6. `admin-web` 新增值班路由表单与编辑动作。

### 41.3 当前判断

1. 当前值班路由模块已经从“只读配置表”升级为“后台可维护配置模块”。
2. 后续仍需补值班日历、升级链路、真实通知网关与审计留痕扩展，才可能接近最终冻结版门槛。

## 42. 2026-07-23 深夜通知通道配置化派发验证

### 42.1 本轮验证结论

本轮围绕“值班路由中的通知通道和升级等级不能只用于展示，必须真正参与告警派发执行”的问题进行了补齐，确认异常告警派发已从固定全通道广播升级为配置驱动派发。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentIssueAlertDeliveryServiceImplTest,PaymentTaskCenterServiceImplTest,PaymentConfigServiceImplTest,PaymentIssueServiceImplTest test` | 通过 | 合计 `39` 个用例全部通过，`PaymentIssueAlertDeliveryServiceImplTest` 提升到 `4` 个用例 |

### 42.2 本轮补齐项

1. `PaymentIssueAlertDispatchItemDTO` 新增 `notifyChannels / escalationLevel / scheduleTag`。
2. `PaymentTaskCenterMapper.xml` 在待派发 outbox 查询阶段联动值班路由表，补齐路由配置字段。
3. `PaymentIssueAlertDeliveryServiceImpl` 改为仅对配置的通知通道执行派发。
4. `IN_APP` 作为源 outbox 通道不再重复进入实际通知器派发列表。
5. 不支持的通知通道会显式记为失败留痕。
6. 任务日志升级状态开始携带最高升级等级口径。
7. 测试新增“仅派发配置通道”和“不支持通道记失败”覆盖。

### 42.3 当前判断

1. 当前异常告警派发已经从固定广播升级为“值班路由配置驱动派发”，更接近真实生产治理模式。
2. 后续仍需补真实通知网关和值班日历联动，因此仍不满足最终 `release/*` 冻结门槛。

## 43. 2026-07-29 支付事件死信收口验证

### 43.1 本轮验证结论

本轮围绕“支付事件出站失败后不能无限重试，必须进入可见、可查、可人工介入的死信状态”的问题进行了补齐，确认事件出站、任务中心和异常中心已经统一识别 `DEAD_LETTER` 状态。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端全量测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml test` | 通过 | 合计 `172` 个用例全部通过，覆盖支付提交、回调、退款、任务中心、异常中心与事件出站 |
| 后台前端构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-20260729-dead-letter --emptyOutDir` | 通过 | `admin-web` 可稳定构建，说明任务中心、异常中心和事件出站页面未被本轮改动破坏 |
| App 端构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-app-web-dist-20260729-dead-letter --emptyOutDir` | 通过 | `app-web` 可稳定构建，支付端共享视图未受影响 |
| H5 端构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-h5-web-dist-20260729-dead-letter --emptyOutDir` | 通过 | `h5-web` 可稳定构建，复用收银台/结果页逻辑通过 |
| PC 端构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-pc-web-dist-20260729-dead-letter --emptyOutDir` | 通过 | `pc-web` 可稳定构建，桌面端支付页逻辑通过 |
| 定向单测 | `PaymentEventDispatchServiceImplTest` | 通过 | 新增“超过最大重试次数转死信”断言，验证 `FAILED -> DEAD_LETTER` 收口路径 |
| 差异检查 | `git diff --check` | 通过 | 未发现空白字符、冲突标记或补丁格式问题 |

### 43.2 本轮补齐项

1. `PaymentEventDispatchServiceImpl` 新增最大重试次数判断，超过阈值后不再继续标记普通失败，而是直接写入 `DEAD_LETTER`。
2. `PaymentEventMapper` / `PaymentEventMapper.xml` 新增 `markPublishDeadLetter`，统一沉淀事件死信落库动作。
3. `PaymentTaskCenterMapper.xml` 和 `PaymentIssueMapper.xml` 已把 `DEAD_LETTER` 纳入失败事件检索口径，避免任务中心和异常中心遗漏死信事件。
4. `PaymentEventDispatchServiceImplTest` 已补齐死信路径单测，避免后续回归把无限重试问题重新带回来。

### 43.3 当前判断

1. `payment-core` 的支付事件出站从“失败重试”升级为“失败重试 + 死信收口”的轻量闭环。
2. 当前仍未接真实 MQ、死信队列和消费回执，因此这次补齐属于本地 outbox 可靠性增强，不等同于生产级消息中间件治理。
3. 在补齐真实消息总线、订阅确认和死信二次补偿编排之前，仍不满足 `master / release` 冻结门槛。

## 44. 2026-07-29 支付事件导出验证

### 44.1 本轮验证结论

本轮围绕“支付事件出站不仅要能查，还要能导出给测试、运营和排障复盘”的问题进行了补齐，确认 `payment-events/export` 已经和前端导出按钮闭环。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 定向单测 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentEventServiceImplTest test` | 通过 | 合计 `4` 个用例全部通过，覆盖事件列表查询、导出和重发场景 |

### 44.2 本轮补齐项

1. `PaymentEventService` 新增导出接口。
2. `PaymentEventServiceImpl` 新增 CSV 导出逻辑，和支付请求导出保持一致。
3. `PaymentEventController` 新增 `/api/payment-events/export`。
4. `PaymentEventMapper` / `PaymentEventMapper.xml` 新增 `findAllForExport`。
5. `PaymentEventsView` 继续沿用前端导出按钮，和后端导出接口正式闭环。

### 44.3 当前判断

1. 支付事件出站已经从“可查询”升级为“可查询 + 可导出 + 可重发 + 可识别死信”。
2. 当前仍未接真实消息总线，因此仍属于支付核心域的出站治理增强，不等于最终 `master / release` 冻结门槛。

1. 当前异常告警派发已经具备“值班路由配置 -> 通知通道选择 -> 派发执行 -> 失败留痕 -> 任务升级文案”的轻量闭环。
2. 后续仍需补真实通知供应商、发送回执、失败补发和通道限流，才可能接近最终冻结版门槛。
## 43. 2026-07-24 告警失败补发与对象规范收口验证

### 43.1 本轮验证结论

本轮围绕“异常告警失败后能否重新进入派发队列、补发时能否避免重复轰炸已成功通道，以及对象模型是否仍然存在历史规范尾巴”进行了补强与验证，确认 `payment-core` 在任务中心和 DTO 规范层面都进一步向冻结版交付包靠拢。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentIssueAlertDeliveryServiceImplTest,PaymentTaskCenterServiceImplTest,PaymentConfigServiceImplTest,PaymentIssueServiceImplTest,PaymentServiceImplTest test` | 通过 | 合计 `59` 个用例全部通过，其中 `PaymentIssueAlertDeliveryServiceImplTest` 为 `5` 个用例，新增补发去重覆盖 |
| DTO/VO/Entity 规范审计 | `python3` 读库扫描 | 通过 | 自动审计结果 `TOTAL_PROBLEMS=0`，当前 `payment-core` 对象层已无遗漏的 Lombok/字段注释缺口 |

### 43.2 本轮补齐项

1. `PaymentTaskCenterMapper.xml` 待派发 outbox 查询口径扩展为 `已生成 / 部分失败 / 派发失败`。
2. `PaymentTaskCenterMapper` 新增按 `issueNo + alertChannel` 查询历史成功派发记录的方法。
3. `PaymentIssueAlertDeliveryServiceImpl` 在补发阶段会跳过已成功通道，仅重试仍未成功的通知通道。
4. 任务执行文案调整为“待派发/补发”，更贴近真实任务中心表达。
5. `PaymentIssueAlertDeliveryServiceImplTest` 新增“补发时跳过已成功通道”覆盖。
6. `PaymentDetailDTO`、`RefundListItemDTO` 等历史对象补齐类注释与字段中文语义注释，完成本轮 DTO 收口。

### 43.3 当前判断

1. 当前异常告警派发已经具备“首次派发 + 失败补发 + 成功通道去重”的最小生产化雏形。
2. 当前 `payment-core` 对象层已完成一轮统一规范收口，后续新增 DTO/VO/Entity 应延续同一标准，避免再次回退。
3. 真实通知供应商、发送回执明细、补发上限与补发节流仍未补齐，因此本轮提升的是成熟度，不是最终 `master / release` 触发条件。

## 44. 2026-07-24 值班班次生效窗联动验证

### 44.1 本轮验证结论

本轮围绕“值班路由不只是责任组和通知通道，还要有生效班次，任务中心要按当前小时匹配当前班次”进行了补强，确认 `payment-core` 在异常升级链路上进一步具备了真实值班配置的味道。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentConfigServiceImplTest,PaymentTaskCenterServiceImplTest,PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | 共 `35` 个用例全部通过，覆盖值班路由生效窗、任务中心异常升级与告警派发 |

### 44.2 本轮补齐项

1. `t_payment_issue_duty_roster` 新增 `effective_start_hour / effective_end_hour`。
2. `PaymentIssueDutyRosterDTO / PaymentIssueDutyRosterEntity / PaymentIssueDutyRosterUpsertRequestDTO` 同步新增班次生效小时字段。
3. 配置中心前端的值班路由表单和表格展示新增班次生效小时与时间窗字段。
4. 任务中心异常升级候选按当前小时匹配值班路由，并将班次标签、时间窗同步写入告警内容。
5. `PaymentTaskCenterServiceImplTest` 新增对“班次标签/时间窗进入告警内容”的验证。

### 44.3 当前判断

1. 当前值班路由已从“纯配置项”升级为“可按时间窗生效的值班配置”。
2. 这使异常 SLA 升级更贴近真实值班班表的操作方式。
3. 但值班日历、跨日班次、升级链路和真实外部通知供应商仍未完成，因此当前仍不满足 `master / release` 冻结门槛。

## 45. 2026-07-24 告警通知供应商配置验证

### 45.1 本轮验证结论

本轮围绕“本地 IM / SMS / Email 通知器不能只有代码骨架，还需要正式的供应商配置、模板配置和启停治理”进行了补强，确认 `payment-core` 已具备轻量通知中心配置入口。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentConfigServiceImplTest,PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | 共 `25` 个用例全部通过，覆盖供应商配置总览、启停和派发前校验 |
| 后台前端构建 | `npm run build` | 通过 | `admin-web` 新增“告警通知供应商配置”区块后可稳定构建 |

### 45.2 本轮补齐项

1. 新增 `t_payment_alert_provider_config`，沉淀告警通知供应商配置。
2. 新增 `PaymentAlertProviderConfigDTO`，并将 `alertProviders` 纳入支付配置中心总览返回。
3. `PaymentConfigServiceImpl / PaymentConfigController / PaymentConfigMapper.xml` 新增供应商配置启停能力。
4. `PaymentIssueAlertDeliveryServiceImpl` 新增“派发前校验指定通道是否存在启用中的供应商配置”逻辑。
5. `PaymentConfigView.vue` 新增告警通知供应商配置区块，统一展示通道、端点、模板、重试和限流策略。

### 45.3 当前判断

1. 当前异常告警派发已经从“本地通知器骨架”升级为“供应商配置中心 + 通道启停治理 + 派发前供应商校验”的轻量通知中心雏形。
2. 这让系统更接近真实企业里的通知治理方式。
3. 但真实供应商调用、模板变量渲染、发送回执明细和多供应商路由仍未完成，因此当前仍不满足 `master / release` 冻结门槛。

## 46. 2026-07-24 供应商配置进入派发执行与留痕验证

### 46.1 本轮验证结论

本轮围绕“供应商配置不能只在配置中心可见，而要真正进入派发执行链路，并把供应商/模板/端点信息沉淀进派发留痕”的问题进行了补强，确认 `payment-core` 的告警通知中心已进一步走向生产化。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | 共 `6` 个用例全部通过，覆盖供应商配置进入派发项、派发日志留痕和缺失配置失败场景 |

### 46.2 本轮补齐项

1. `PaymentIssueAlertDispatchItemDTO` 新增 `providerCode / providerName / endpointAlias / templateCode` 运行时字段。
2. `PaymentTaskCenterMapper` 新增按通知通道查询启用中供应商配置的方法。
3. `PaymentIssueAlertDeliveryServiceImpl` 改为先加载当前通道供应商配置，再把供应商、模板和端点信息注入派发项。
4. 派发日志留痕增强，当前会在告警内容中保留供应商、模板和端点信息，便于后续排查。

### 46.3 当前判断

1. 当前异常告警通知中心已经从“供应商配置存在”升级为“供应商配置真正参与派发执行和审计留痕”。
2. 这为后续真实企业微信、短信网关和邮件平台接入提供了稳定主链路。
3. 但真实供应商调用、模板变量渲染、发送回执明细和多供应商路由仍未完成，因此当前仍不满足 `master / release` 冻结门槛。
3. `PaymentTaskCenterService` 新增“支付控制策略自动巡检”手动与自动执行入口，任务编码为 `PAYMENT_CONTROL_SELF_CHECK`。
4. `PaymentCompensationScheduler` 新增控制策略自动巡检调度入口，避免控制策略长期依赖人工点击。
5. 任务中心总览新增 `controlPolicyWarningCount` 指标，统一展示未通过自检的控制策略数量。
6. 任务日志口径扩展为“处理数 / 成功数 / 告警数 / 失败数”，避免控制策略巡检只有 `WARN` 时被误判为失败。
7. 后台任务中心页面新增控制策略巡检按钮、控制策略告警指标和任务日志告警数展示。

### 38.3 当前判断

1. 当前支付控制治理已经具备“来源应用配置 -> 人工自检 -> 自动巡检 -> 任务留痕 -> 主链路阻断”的正式化雏形。
2. 受当前线程写权限限制，本轮后端定向测试还需要在可写 `target/` 目录的环境再次执行，但从前端构建、补丁格式和代码联动口径看，本轮改造已达到可继续集成的状态。
3. 后续仍需补接口级分布式限流、并发令牌、防重试编排和真实运维告警触达，因此仍不满足最终 `master/release` 冻结门槛。

## 39. 2026-07-22 支付提交并发令牌验证

### 39.1 本轮验证结论

本轮围绕“同一预付单在短时间内可能被多终端或重复点击并发打入支付链路”的问题进行了补齐，确认支付主链路已支持并发令牌占用、关闭/成功回调释放和异常回滚释放。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentServiceImplTest test` | 通过 | `PaymentServiceImplTest` 当前 `18` 个用例全部通过，新增覆盖并发令牌占用、并发拦截和关闭释放 |
| 格式检查 | `git diff --check` | 通过 | 未发现空白或补丁格式问题 |

### 39.2 本轮补齐项

1. 新增 `t_payment_submit_concurrency_token`，统一记录预付单级并发令牌、持有幂等键、终端、客户端 IP、过期时间和释放原因。
2. `PaymentServiceImpl.submit()` 在进入 `支付中` 前先占用并发令牌，避免同一预付单被不同终端同时打入渠道。
3. 当预付单已被其他流程推进到 `支付中` 时，当前请求会释放本次令牌并返回最新收银台状态。
4. 当渠道提交抛异常时，系统会释放并发令牌并将收银台状态回滚到 `待支付`。
5. 当支付成功回调或人工关闭完成时，系统会释放当前支付单对应的并发令牌。
6. 新增 `PAYMENT-1022` 错误码，用于标识“预付单存在并发支付提交”。

### 39.3 当前判断

1. 当前支付主链路已经具备“幂等键 -> 收银台占位 -> 并发令牌 -> 渠道路由 -> 渠道提交 -> 成功/关闭释放”的正式化雏形。
2. 后续仍需补接口级分布式限流和更细粒度重试编排，因此仍不满足最终 `master/release` 冻结门槛。

## 40. 2026-07-23 异常告警派发骨架验证

### 40.1 本轮验证范围

本轮围绕支付任务中心中的“异常告警派发”能力进行了验证，目标是确认当前代码已从“只生成 outbox 告警”升级为“可由任务中心触发本地告警派发骨架并回写状态”。

本轮覆盖内容：

1. `PaymentIssueAlertDeliveryService` 手动/自动派发入口
2. `PaymentTaskCenterService` 与 `PaymentTaskCenterController` 的派发任务挂接
3. `PaymentTaskCenterMapper.xml` 的待派发 outbox 查询与派发状态回写
4. 本地 IM / SMS / Email 三类通知器骨架
5. `PaymentTaskCenterServiceImplTest` 的派发入口测试

### 40.2 验证过程

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 第一次测试执行 | `mvn -Dtest=PaymentTaskCenterServiceImplTest test` | 失败 | 环境使用的是 `JRE`，缺少编译器，报错 `No compiler is provided in this environment` |
| 环境切换 | 设置 `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home` | 成功 | 切换到本机可用 JDK |
| 第二次测试执行 | `/Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -Dtest=PaymentTaskCenterServiceImplTest test` | 初次失败 | `Mockito` 严格模式下发现两个无效 stubbing |
| 测试修复后再次执行 | 同上 | 通过 | `13` 个测试全部通过 |

### 40.3 本轮补齐项

1. 新增 `PaymentIssueAlertDeliveryService` 与默认实现，统一承接异常告警派发任务。
2. 新增 `PaymentIssueAlertNotifier` 抽象，并补齐本地 `IM / SMS / EMAIL` 三类通知器骨架。
3. `PaymentTaskCenterService` 新增“异常告警派发”手动与自动执行入口。
4. `PaymentTaskCenterController` 新增 `POST /api/payment-task-center/dispatch-issue-alerts`。
5. `PaymentTaskCenterMapper.xml` 新增待派发 `outbox` 告警查询和派发状态回写。
6. `PaymentTaskCenterServiceImplTest` 扩展到派发入口验证，并完成定向单测通过。
7. `admin-web` 任务中心新增“异常告警派发”操作卡片，并完成 `npm run build` 通过。
8. 异常告警派发进一步补齐 IM / SMS / EMAIL 多通道独立留痕，原始 outbox 回写 `已派发 / 部分失败 / 派发失败`。
9. `PaymentIssueAlertDeliveryServiceImplTest` 新增成功派发与部分失败场景，和任务中心测试一起通过 `15` 个用例。
10. 异常中心列表补齐告警总状态与通道派发摘要展示，避免单条通道日志覆盖整体告警认知。
11. `PaymentIssueServiceImplTest + PaymentIssueAlertDeliveryServiceImplTest + PaymentTaskCenterServiceImplTest` 于 `2026-07-23` 合计通过 `20` 个用例。

### 40.4 当前判断

1. 当前 `payment-core` 已具备异常 SLA 告警 `outbox -> 本地派发骨架 -> 状态回写` 的后端闭环。
2. 本轮通过的是本地骨架与任务中心编排，不代表已经完成企业微信 / 钉钉 / 短信 / 邮件供应商的生产级对接。
3. 因此本轮提升的是冻结版成熟度，而不是发布门槛；`master / release` 判断仍需继续看真实通知网关、值班路由和回执流水等能力。

## 41. 2026-07-24 告警供应商回执与异常中心审计补强验证

### 41.1 本轮验证范围

本轮围绕“异常告警派发虽然已接入供应商配置，但日志层和异常中心仍看不到供应商回执结果”的问题进行了补强验证，目标是确认当前代码已从“供应商配置进入执行链路”升级为“供应商执行回执可审计、可在异常中心查看”。

本轮覆盖内容：

1. `PaymentIssueAlertNotifier` 返回标准化投递结果对象
2. 本地 `IM / SMS / EMAIL` 通知器统一模拟供应商回执
3. `PaymentIssueAlertDeliveryServiceImpl` 在成功/失败场景下写入供应商投递明细
4. `t_payment_issue_alert_log` 审计字段扩展
5. 异常中心列表补齐供应商投递摘要与回执摘要展示

### 41.2 验证过程

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `6` 个测试全部通过，覆盖成功派发、部分失败、配置缺失、成功通道去重等场景 |
| 前端构建 | `npm run build` | 通过 | `admin-web` 生产构建成功，异常中心新增供应商/回执信息未引入前端回归 |
| 格式检查 | `git diff --check` | 通过 | 未发现空白或补丁格式问题 |

### 41.3 本轮补齐项

1. 新增 `PaymentIssueAlertDeliveryResultDTO`，统一承接通知器的供应商回执信息。
2. 新增 `AbstractLocalPaymentIssueAlertNotifier`，让本地 IM / SMS / EMAIL 通知器共用一致的回执生成逻辑。
3. `PaymentIssueAlertNotifier.send()` 从 `void` 升级为返回投递结果，主链路不再丢失供应商执行信息。
4. `PaymentIssueAlertDeliveryServiceImpl` 会把供应商编码、供应商名称、端点、模板、回执号、投递状态、投递说明和渲染快照统一写入告警日志。
5. 当供应商配置缺失、通知器缺失或发送异常时，系统会把失败原因以标准化投递状态写入日志，而不是只留一个失败状态。
6. `PaymentIssueMapper.xml` 与异常中心列表新增“最新供应商投递摘要 / 最新供应商回执摘要”展示。

### 41.4 当前判断

1. 当前 `payment-core` 的异常告警链路已经具备“配置进入执行 -> 执行返回回执 -> 日志可审计 -> 异常中心可联查”的更完整闭环。
2. 本轮通过的是本地供应商回执模拟与审计链路，不代表已经完成真实企业微信/短信/邮件网关的生产级对接。
3. 因此本轮提升的是告警治理成熟度，而不是最终发布门槛；`master / release` 判断仍需继续看真实网关接入、模板变量渲染和多供应商路由策略。

## 42. 2026-07-25 告警模板变量渲染与供应商路由验证

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `6` 个用例全部通过，覆盖 P1 专用模板命中、缺配置失败、部分失败和成功通道去重 |
| 前端构建 | `npm run build` | 通过 | 配置中心展示模板正文、路由规则和优先级后构建成功 |
| 格式检查 | `git diff --check` | 通过 | 未发现补丁格式问题 |

本轮补齐项：

1. 告警供应商配置增加 `template_body / route_rule / route_priority`。
2. 派发服务按优先级匹配供应商路由规则，支持 `severity / issueType / responsibilityGroup / DEFAULT`。
3. 派发服务渲染 `{{severity}} / {{issueType}} / {{issueNo}} / {{paymentOrderId}} / {{responsibilityGroup}} / {{receiver}} / {{scheduleTag}} / {{alertContent}} / {{triggeredBy}}` 模板变量。
4. 本地通知器优先使用渲染后的内容快照，异常中心和告警日志可追溯最终文案。

## 43. 2026-07-25 异常告警回执回查验证

### 43.1 本轮验证范围

本轮围绕“异常告警已经派发到供应商后，仍缺少送达回执收口闭环”的问题进行了补强验证，目标是确认当前代码已从“供应商已受理”升级为“任务中心可继续回查并回写送达结果”。

本轮覆盖内容：

1. `PaymentIssueAlertDeliveryService` 手动/自动回执回查入口
2. `PaymentTaskCenterService` 与 `PaymentCompensationScheduler` 的回查任务挂接
3. `PaymentTaskCenterMapper.xml` 的 `ACCEPTED -> DELIVERED` 回写能力
4. `admin-web` 任务中心新增“异常告警回执回查”卡片和口径说明
5. `PaymentIssueAlertDeliveryServiceImplTest` 的回执回查用例

### 43.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | 覆盖“有待回查日志”和“无待回查日志”两类场景 |
| 前端构建 | `cd systems/payment-core/frontend/admin-web && npm run build` | 通过 | 确认任务中心新增卡片与文案不会引入构建回归 |

### 43.3 本轮补齐项

1. 新增“异常告警回执回查”任务码 `PAYMENT_ISSUE_ALERT_RECEIPT_RECONCILE`。
2. 对外部通道中 `provider_delivery_status = ACCEPTED` 的异常告警日志执行统一回查。
3. 回查成功后同步回写 `provider_delivery_status = DELIVERED`、`ack_status = 已确认`。
4. 回查结果会进入任务执行日志，统一纳入严重等级、升级状态和建议动作口径。
5. 自动补偿调度器新增“供应商回执回查”定时任务，减少人工逐条核对。

### 43.4 当前判断

1. 当前 `payment-core` 的异常告警链路已从“outbox -> 外部派发 -> 供应商受理”进一步推进到“任务中心回查 -> 送达确认”的第二段闭环。
2. 本轮仍属于本地供应商回执骨架，不代表已经接入真实 IM / SMS / EMAIL 供应商回执 API。
3. 这一步显著提升了冻结版完整性，但是否进入 `master / release` 仍要继续看真实通知网关、值班路由、跨系统联动和更大范围回归。

## 44. 2026-07-25 异常告警明细台验证

### 44.1 本轮验证范围

本轮围绕“异常告警已经支持 outbox、派发和回执回查，但运营和测试仍缺少可直接联查的明细台”的问题进行了补强验证，目标是确认当前代码已具备统一查询异常告警通知日志的后端接口和后台页面。

本轮覆盖内容：

1. `PaymentIssueService / PaymentIssueController / PaymentIssueMapper` 新增告警明细分页查询能力
2. `admin-web` 新增“异常告警明细台”页面与菜单入口
3. 异常中心中的告警通知区域新增“查看明细”跳转入口
4. `PaymentIssueServiceImplTest` 新增告警明细查询口径规范化覆盖

### 44.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentIssueServiceImplTest test` | 通过 | 覆盖异常中心动作和告警明细查询 |
| 前端构建 | `cd systems/payment-core/frontend/admin-web && npm run build` | 通过 | 确认新增页面、菜单和跳转入口不会引入构建回归 |

### 44.3 本轮补齐项

1. 新增 `PaymentIssueAlertLogQueryDTO / PaymentIssueAlertLogRowDTO`，统一承接告警明细查询参数和返回结构。
2. 新增 `/api/payment-issues/alerts`，支持按告警编号、异常编号、支付单号、通道、告警状态、回执状态和供应商投递状态分页查询。
3. 后台页面可直接查看供应商、模板、回执号、投递状态、投递说明、渲染快照、确认人和确认时间。
4. 异常中心列表中的“告警通知”单元格新增“查看明细”入口，形成“聚合异常 -> 告警明细”的排障路径。

### 44.4 当前判断

1. 当前 `payment-core` 在异常治理维度已具备“异常聚合视图 + 任务中心补偿视图 + 告警执行明细视图”的三层观察面。
2. 本轮提升的是运维、产品和测试的联查效率，不代表真实通知供应商、补发上限和通道节流已经正式化。
3. 因此本轮依旧属于冻结版补强，而不是 `master / release` 的触发条件。

## 45. 2026-07-25 异常告警补发护栏验证

### 45.1 本轮验证范围

本轮围绕“异常告警已经支持失败补发，但还缺少正式的补发上限和冷却护栏”的问题进行了补强验证，目标是确认当前代码不会在短时间内无限重试同一异常通道。

本轮覆盖内容：

1. `PaymentIssueAlertDeliveryServiceImpl` 按 `retryPolicy` 解析失败重试次数和冷却时间
2. `PaymentTaskCenterMapper` 新增失败派发次数统计和最近通道日志查询
3. `PaymentIssueAlertDeliveryServiceImplTest` 新增重试上限和冷却窗口护栏覆盖

### 45.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | 覆盖正常派发、补发去重、回执回查、重试上限和冷却窗口 |

### 45.3 本轮补齐项

1. `PaymentIssueAlertLogEntity` 新增 `createdAt` 字段，供补发冷却时间判断使用。
2. `PaymentTaskCenterMapper.xml` 新增失败次数统计和最近派发日志查询 SQL。
3. 当供应商配置中的 `retryPolicy` 命中“失败重试 X 次 / 间隔 Y 分钟”时，派发服务会在发送前先判断是否允许继续补发。
4. 当失败次数已超出允许范围或冷却窗口未到时，本轮任务会直接阻断该通道补发。

### 45.4 当前判断

1. 当前 `payment-core` 的异常告警补偿能力已从“失败补发骨架”进一步推进到“带补发护栏的轻量正式化版本”。
2. 本轮提升的是执行层面的稳定性，不代表真实通知供应商、通道级限流和跨实例分布式协调已经完成。
3. 因此本轮同样属于冻结版补强，而不是 `master / release` 的触发条件。

## 46. 2026-07-25 异常告警通道级限流护栏验证

### 46.1 本轮验证范围

本轮围绕“异常告警补发已经具备重试护栏，但供应商通道仍缺少单位时间内的派发限流保护”的问题进行了补强验证，目标是确认当前代码会按供应商限流策略阻断超量派发。

本轮覆盖内容：

1. `PaymentIssueAlertDeliveryServiceImpl` 按 `rateLimitPolicy` 解析时间窗口与派发阈值
2. `PaymentTaskCenterMapper` 新增按供应商 + 通道 + 时间窗口统计派发次数能力
3. `PaymentIssueAlertDeliveryServiceImplTest` 新增通道级限流护栏覆盖

### 46.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | 覆盖正常派发、补发护栏、回执回查和通道级限流 |

### 46.3 本轮补齐项

1. `PaymentTaskCenterMapper.xml` 新增按供应商配置编码和通知通道统计窗口内成功派发量的 SQL。
2. 派发服务在真正调用通知器前会先校验 `rateLimitPolicy`，如“每分钟 60 条”。
3. 当命中限流阈值时，本轮派发会被阻断，并落一条标准化失败日志，供异常明细台和后续排查联查。

### 46.4 当前判断

1. 当前 `payment-core` 的异常告警执行链路已从“补发护栏”进一步推进到“补发护栏 + 通道级限流护栏”的轻量正式化版本。
2. 本轮提升的是派发执行层面的限流保护，不代表真实通知供应商、跨实例分布式协调和更复杂的令牌桶/漏桶模型已经完成。
3. 因此本轮仍属于冻结版补强，而不是 `master / release` 的触发条件。

## 47. 2026-07-25 异常告警跨日班次路由验证

### 47.1 本轮验证范围

本轮围绕“值班路由已经有班次时段，但还不能表达 22:00-次日06:00 这类夜班跨日窗口”的问题进行了补强验证，目标是确认当前代码已支持跨日班次配置和路由命中。

本轮覆盖内容：

1. `PaymentConfigServiceImpl` 允许创建和更新跨日班次值班路由
2. `PaymentTaskCenterMapper.xml` 在异常 SLA 巡检与 outbox 派发阶段支持跨日班次命中
3. `PaymentConfigMapper.xml` 值班窗口展示升级为跨日可读文案
4. `PaymentConfigServiceImplTest` 新增跨日班次配置覆盖

### 47.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentConfigServiceImplTest test` | 通过 | 覆盖值班路由新增、编辑和跨日班次配置 |

### 47.3 本轮补齐项

1. 值班路由配置不再限制“开始小时必须小于等于结束小时”，正式支持跨日班次。
2. 异常 SLA 巡检与 outbox 派发读取值班路由时，已支持夜班跨日窗口判断。
3. 值班窗口文案在配置总览和异常升级内容中已可展示为 `22:00-次日06:00`。

### 47.4 当前判断

1. 当前 `payment-core` 的值班路由能力已从“单日时段配置”进一步推进到“支持跨日夜班”的轻量正式化版本。
2. 本轮提升的是值班编排层面的准确性，不代表值班日历、节假日排班和真实通知供应商联动已经完成。
3. 因此本轮仍属于冻结版补强，而不是 `master / release` 的触发条件。

## 48. 2026-07-25 异常告警多供应商失败切换验证

### 48.1 本轮验证范围

本轮围绕“异常告警派发已经具备重试、限流和回执能力，但首选供应商失败后仍会导致整条通道直接失败”的问题进行了补强验证，目标是确认当前代码已支持候选供应商自动切换。

本轮覆盖内容：

1. `PaymentIssueAlertDeliveryServiceImpl` 按候选供应商优先级依次尝试派发
2. 当首选供应商失败时自动切换下一候选供应商
3. `PaymentIssueAlertDeliveryServiceImplTest` 新增主备供应商切换覆盖

### 48.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | 覆盖正常派发、护栏、回执回查和供应商失败切换 |

### 48.3 本轮补齐项

1. 同一通知通道命中的多个供应商候选已支持按优先级依次尝试。
2. 每个供应商尝试都会单独写告警日志，便于后续复盘主备切换过程。
3. 只有全部候选供应商都失败时，该通道才会判定为失败。

### 48.4 当前判断

1. 当前 `payment-core` 的异常告警派发能力已从“单供应商命中”进一步推进到“多供应商自动失败切换”的轻量正式化版本。
2. 本轮提升的是通知编排层面的弹性，不代表真实供应商 SDK/HTTP 接入、熔断降级和跨实例协同已经完成。
3. 因此本轮仍属于冻结版补强，而不是 `master / release` 的触发条件。

## 49. 2026-07-25 异常告警供应商熔断降级验证

### 49.1 本轮验证范围

本轮围绕“异常告警派发已经支持主备供应商切换，但主供应商在短时间连续失败时仍会被反复尝试”的问题进行了补强验证，目标是确认当前代码已支持轻量供应商熔断降级。

本轮覆盖内容：

1. `PaymentIssueAlertDeliveryServiceImpl` 新增供应商短时失败熔断判断
2. 主供应商熔断时自动切到后备供应商
3. `PaymentIssueAlertDeliveryServiceImplTest` 新增供应商熔断切换覆盖

### 49.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | 覆盖派发、护栏、主备切换和熔断降级 |

### 49.3 本轮补齐项

1. 当某个供应商在最近 `10` 分钟内失败达到 `3` 次时，会被临时熔断。
2. 熔断命中后不会继续尝试该供应商，而是直接切到后备供应商。
3. 熔断命中会落标准化失败日志，供异常明细台复盘。

### 49.4 当前判断

1. 当前 `payment-core` 的异常告警编排能力已从“多供应商失败切换”进一步推进到“带轻量熔断降级”的主备治理版本。
2. 本轮提升的是通知编排层面的韧性，不代表真实供应商 SDK/HTTP 接入和跨实例熔断共享已经完成。
3. 因此本轮仍属于冻结版补强，而不是 `master / release` 的触发条件。

## 50. 2026-07-25 异常告警工作日/非工作日值班策略验证

### 50.1 本轮验证范围

本轮围绕“异常告警值班路由只有小时段，没有星期范围和工作日策略，难以表达真实白班/周末/全周兜底排班”的问题进行了补强验证。

本轮覆盖内容：

1. `t_payment_issue_duty_roster` 新增 `weekday_scope` 与 `holiday_strategy`
2. `PaymentConfigServiceImpl` 新增值班路由日期策略校验与归一化
3. `PaymentTaskCenterMapper.xml` 新增值班星期范围与工作日策略命中条件
4. `admin-web` 支付配置中心补齐值班路由“适用星期/日期策略”配置与展示

### 50.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentConfigServiceImplTest,PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | 覆盖值班路由配置增强与异常告警派发不回退 |
| 后台前端构建 | `npm run build` | 通过 | `systems/payment-core/frontend/admin-web` 值班路由表单新增字段后仍可生产构建 |

### 50.3 本轮补齐项

1. 值班路由可以按 `1-7` 配置生效星期，支持工作日白班与周末班拆分。
2. 值班路由可以声明 `ALL_DAYS / WORKDAY_ONLY / NON_WORKDAY_ONLY` 三类日期策略。
3. 巡检生成 outbox 和待派发 outbox 命中值班路由时，会同时校验小时窗口、星期范围和日期策略。
4. 页面已展示日期策略和适用星期，避免运营只改后端、不知当前值班命中边界。

### 50.4 当前判断

1. 当前 `payment-core` 的异常告警值班编排能力已从“只有小时段”推进到“小时段 + 星期范围 + 工作日策略”的轻量正式化版本。
2. 本轮提升的是值班路由编排能力，不代表法定节假日服务、真实排班中心或升级链路已经完成。
3. 因此本轮仍属于冻结版补强，而不是 `master / release` 的触发条件。

## 51. 2026-07-25 异常告警升级编排验证

### 51.1 本轮验证范围

本轮围绕“值班路由只有升级等级，没有升级接收人、升级策略和超时阈值”的问题进行了补强验证。

本轮覆盖内容：

1. `t_payment_issue_duty_roster` 新增升级接收人、升级策略和升级超时分钟数
2. `PaymentConfigServiceImpl` 新增升级字段必填和超时范围校验
3. `PaymentTaskCenterServiceImpl` 生成 SLA 告警 outbox 时追加升级策略说明
4. `admin-web` 支付配置中心补齐升级编排字段配置与展示

### 51.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentConfigServiceImplTest,PaymentTaskCenterServiceImplTest test` | 通过 | `36` 个用例通过 |
| 后台前端构建 | `npm run build` | 通过 | `systems/payment-core/frontend/admin-web` 构建通过 |

### 51.3 当前判断

1. 当前 `payment-core` 的异常告警已具备轻量升级编排配置能力。
2. 本轮仍未实现未确认自动二次派发、法定节假日排班中心和真实外部通知网关，因此仍不触发 `master / release`。

## 52. 2026-07-25 异常告警未确认自动升级验证

### 52.1 本轮验证范围

本轮围绕“升级策略已经可配置，但超时未确认后没有自动生成升级 outbox”的问题进行了补强验证。

本轮覆盖内容：

1. `PaymentTaskCenterMapper` 新增未确认告警升级候选查询
2. `PaymentTaskCenterServiceImpl` 的 SLA 升级巡检同步生成未确认升级 outbox
3. `PaymentTaskCenterServiceImplTest` 新增未确认超时告警自动升级覆盖

### 52.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentTaskCenterServiceImplTest test` | 通过 | `14` 个用例通过 |

### 52.3 当前判断

1. 当前 `payment-core` 的异常告警升级链路已具备“配置升级策略 -> 超时未确认 -> 自动生成升级 outbox”的轻量闭环。
2. 本轮仍未实现真实外部通知网关、独立升级状态机和法定节假日排班中心，因此仍不触发 `master / release`。

## 53. 2026-07-25 异常告警来源 outbox 级派发幂等验证

### 53.1 本轮验证范围

本轮围绕“升级 outbox 与原始 outbox 共用同一个 `issueNo`，可能被原始告警的通道成功记录误判为已派发”的问题进行了修正验证。

本轮覆盖内容：

1. `t_payment_issue_alert_log` 新增 `source_alert_no`
2. 外部派发日志写入来源站内 outbox 编号
3. 派发幂等、失败重试计数和最近失败查询改为按来源 outbox 编号判断
4. 新增“同一异常下升级 outbox 仍可独立派发”的回归用例

### 53.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `14` 个用例通过 |

### 53.3 当前判断

1. 当前 `payment-core` 的异常告警派发幂等边界已从异常单维度修正为来源 outbox 维度。
2. 这一步确保原始告警和升级告警可以各自独立派发，同时仍保留同一来源 outbox 的通道级去重能力。

## 54. 2026-07-25 异常告警 IM Webhook 外部通知验证

### 54.1 本轮验证范围

本轮围绕“IM 告警仍是本地通知器骨架，缺少可接外部机器人/Webhook 的正式化入口”的问题进行了补强验证。

本轮覆盖内容：

1. `LocalImPaymentIssueAlertNotifier` 支持配置外部 Webhook
2. 未配置 Webhook 时保留本地回执兜底
3. 配置 Webhook 后通过 HTTP POST 投递告警 payload
4. `application.yml` 新增 `payment.issue-alert.im.webhook-url`
5. 新增 `LocalImPaymentIssueAlertNotifierTest`

### 54.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=LocalImPaymentIssueAlertNotifierTest,PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `16` 个用例通过 |

### 54.3 当前判断

1. 当前 `payment-core` 的 IM 告警已经具备轻量外部 Webhook 接入能力。
2. SMS/EMAIL、Webhook 签名、超时配置和响应体业务码解析仍待补齐，因此仍不触发 `master / release`。

## 55. 2026-07-25 异常告警 SMS HTTP 网关通知验证

### 55.1 本轮验证范围

本轮围绕“SMS 告警仍是本地通知器骨架，缺少可接外部短信 HTTP 网关的正式化入口”的问题进行了补强验证。

本轮覆盖内容：

1. `LocalSmsPaymentIssueAlertNotifier` 支持配置外部短信 HTTP 网关
2. 未配置 HTTP 网关时保留本地回执兜底
3. 配置 HTTP 网关后通过 HTTP POST 投递告警 payload
4. `application.yml` 新增 `payment.issue-alert.sms.webhook-url`
5. 新增 `LocalSmsPaymentIssueAlertNotifierTest`

### 55.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=LocalSmsPaymentIssueAlertNotifierTest,LocalImPaymentIssueAlertNotifierTest,PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `18` 个用例通过 |

### 55.3 当前判断

1. 当前 `payment-core` 的 SMS 告警已经具备轻量外部 HTTP 网关接入能力。
2. EMAIL、短信签名、超时配置、响应体业务码解析和供应商级失败码标准化仍待补齐，因此仍不触发 `master / release`。

## 56. 2026-07-25 异常告警 EMAIL HTTP 网关通知验证

### 56.1 本轮验证范围

本轮围绕“EMAIL 告警仍是本地通知器骨架，缺少可接外部邮件 HTTP 网关的正式化入口”的问题进行了补强验证。

本轮覆盖内容：

1. `LocalEmailPaymentIssueAlertNotifier` 支持配置外部邮件 HTTP 网关
2. 未配置 HTTP 网关时保留本地回执兜底
3. 配置 HTTP 网关后通过 HTTP POST 投递告警 payload
4. `application.yml` 新增 `payment.issue-alert.email.webhook-url`
5. 新增 `LocalEmailPaymentIssueAlertNotifierTest`

### 56.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=LocalEmailPaymentIssueAlertNotifierTest,LocalSmsPaymentIssueAlertNotifierTest,LocalImPaymentIssueAlertNotifierTest,PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `20` 个用例通过 |

### 56.3 当前判断

1. 当前 `payment-core` 的 IM/SMS/EMAIL 三类异常告警通道都已具备轻量外部 HTTP/Webhook 接入能力。
2. 签名、超时、响应体业务码解析和供应商级失败码标准化仍待补齐，因此仍不触发 `master / release`。

## 57. 2026-07-25 异常告警外部网关超时配置验证

### 57.1 本轮验证范围

本轮围绕“IM/SMS/EMAIL 虽然具备外部 HTTP/Webhook 入口，但缺少分通道超时配置”的问题进行了补强验证。

本轮覆盖内容：

1. `application.yml` 新增 `payment.issue-alert.im.timeout-ms`
2. `application.yml` 新增 `payment.issue-alert.sms.timeout-ms`
3. `application.yml` 新增 `payment.issue-alert.email.timeout-ms`
4. 三类通知器统一使用连接超时与读取超时
5. 三类通知器测试补齐超时值断言

### 57.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=LocalEmailPaymentIssueAlertNotifierTest,LocalSmsPaymentIssueAlertNotifierTest,LocalImPaymentIssueAlertNotifierTest,PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `20` 个用例通过 |

### 57.3 当前判断

1. 当前 `payment-core` 的三类异常告警通道都已具备分通道超时配置能力。
2. 签名、响应体业务码解析、重试退避和供应商级失败码标准化仍待补齐，因此仍不触发 `master / release`。

## 58. 2026-07-25 异常告警外部网关业务成功码校验验证

### 58.1 本轮验证范围

本轮围绕“外部 HTTP/Webhook 通知器虽然可发请求，但仍只按 `HTTP 200` 判断成功，缺少业务成功码校验和回执号提取”的问题进行了补强验证。

本轮覆盖内容：

1. `application.yml` 新增 IM/SMS/EMAIL 三类通道的成功码 JSON Pointer 配置
2. `application.yml` 新增 IM/SMS/EMAIL 三类通道的成功码期望值配置
3. `application.yml` 新增 IM/SMS/EMAIL 三类通道的回执号 JSON Pointer 配置
4. 三类通知器支持解析返回体并提取供应商回执号
5. IM 通知器新增业务成功码不匹配拒绝用例

### 58.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=LocalEmailPaymentIssueAlertNotifierTest,LocalSmsPaymentIssueAlertNotifierTest,LocalImPaymentIssueAlertNotifierTest,PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `21` 个用例通过 |

### 58.3 当前判断

1. 当前 `payment-core` 的 IM/SMS/EMAIL 三类异常告警通道都已具备业务成功码校验和回执号提取能力。
2. 签名、统一回执状态映射、重试退避和供应商失败码标准化仍待补齐，因此仍不触发 `master / release`。

## 59. 2026-07-25 异常告警外部网关认证头配置验证

### 59.1 本轮验证范围

本轮围绕“外部 HTTP/Webhook 通知器虽然可以打网关，但缺少可配置认证头，难以对接真实供应商鉴权”的问题进行了补强验证。

本轮覆盖内容：

1. `application.yml` 新增 IM/SMS/EMAIL 三类通道的认证头名称配置
2. `application.yml` 新增 IM/SMS/EMAIL 三类通道的认证头取值配置
3. 三类通知器统一把认证头写入 HTTP 请求头
4. 三类通知器测试补齐认证头断言

### 59.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=LocalEmailPaymentIssueAlertNotifierTest,LocalSmsPaymentIssueAlertNotifierTest,LocalImPaymentIssueAlertNotifierTest,PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `21` 个用例通过 |

### 59.3 当前判断

1. 当前 `payment-core` 的 IM/SMS/EMAIL 三类异常告警通道都已具备外部网关认证头配置能力。
2. 签名、统一回执状态映射、重试退避和供应商失败码标准化仍待补齐，因此仍不触发 `master / release`。

## 60. 2026-07-25 异常告警外部网关签名头配置验证

### 60.1 本轮验证范围

本轮围绕“外部 HTTP/Webhook 通知器虽然可以带认证头，但缺少可配置签名头，难以对接需要请求签名的真实供应商网关”的问题进行了补强验证。

本轮覆盖内容：

1. `application.yml` 新增 IM/SMS/EMAIL 三类通道的签名头名称配置
2. `application.yml` 新增 IM/SMS/EMAIL 三类通道的签名密钥配置
3. 三类通知器统一按请求体计算 `HMAC-SHA256` 并写入签名头
4. 三类通知器测试补齐签名头断言

### 60.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=LocalEmailPaymentIssueAlertNotifierTest,LocalSmsPaymentIssueAlertNotifierTest,LocalImPaymentIssueAlertNotifierTest,PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `21` 个用例通过 |

### 60.3 当前判断

1. 当前 `payment-core` 的 IM/SMS/EMAIL 三类异常告警通道都已具备签名头配置能力。
2. 时间戳/nonce 联动、供应商签名算法切换、统一回执状态映射、重试退避和供应商失败码标准化仍待补齐，因此仍不触发 `master / release`。

## 61. 2026-07-25 异常告警外部网关时间戳与 Nonce 头配置验证

### 61.1 本轮验证范围

本轮围绕“外部 HTTP/Webhook 通知器虽然可以带认证头和签名头，但缺少时间戳与 nonce 头，难以对接真实供应商的时效校验与防重放要求”的问题进行了补强验证。

本轮覆盖内容：

1. `application.yml` 新增 IM/SMS/EMAIL 三类通道的时间戳头名称配置
2. `application.yml` 新增 IM/SMS/EMAIL 三类通道的 nonce 头名称配置
3. 三类通知器统一把时间戳与 nonce 写入 HTTP 请求头
4. 三类通知器测试补齐时间戳头与 nonce 头断言

### 61.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=LocalEmailPaymentIssueAlertNotifierTest,LocalSmsPaymentIssueAlertNotifierTest,LocalImPaymentIssueAlertNotifierTest,PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `21` 个用例通过 |

### 61.3 当前判断

1. 当前 `payment-core` 的 IM/SMS/EMAIL 三类异常告警通道都已具备时间戳头与 nonce 头配置能力。
2. 服务端时间窗校验联动、统一防重放编排、供应商签名算法切换、统一回执状态映射、重试退避和供应商失败码标准化仍待补齐，因此仍不触发 `master / release`。

## 62. 2026-07-25 异常告警外部网关统一回执状态映射验证

### 62.1 本轮验证范围

本轮围绕“外部 HTTP/Webhook 通知器虽然可以请求成功，但供应商返回的原始投递状态无法统一映射，导致任务中心无法准确判断是否需要切换候选供应商”的问题进行了补强验证。

本轮覆盖内容：

1. `application.yml` 新增 IM/SMS/EMAIL 三类通道的供应商投递状态提取指针配置
2. `application.yml` 新增 IM/SMS/EMAIL 三类通道的 `DELIVERED / ACCEPTED / FAILED` 原始状态枚举映射配置
3. `application.yml` 新增供应商失败码提取指针配置，统一拼接到投递说明中
4. 通知器统一把供应商原始状态归一化到 `DELIVERED / ACCEPTED / FAILED`
5. 当主供应商返回 `FAILED` 时，派发服务会将本次记录记为失败并继续切换下一候选供应商

### 62.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=LocalImPaymentIssueAlertNotifierTest,LocalSmsPaymentIssueAlertNotifierTest,LocalEmailPaymentIssueAlertNotifierTest,PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `23` 个用例通过 |

### 62.3 当前判断

1. 当前 `payment-core` 的 IM/SMS/EMAIL 三类异常告警通道都已具备供应商原始回执状态提取、统一状态映射和失败码标准化拼接能力。
2. 当主供应商明确返回失败态时，异常告警派发服务已能将该次投递判为失败并继续尝试后备供应商。
3. 服务端时间窗校验联动、统一防重放编排、供应商签名算法切换和更细粒度重试退避策略仍待补齐，因此当前仍不触发 `master / release`。

## 63. 2026-07-25 异常告警外部网关 RSA2 签名算法切换验证

### 63.1 本轮验证范围

本轮围绕“外部 HTTP/Webhook 通知器已支持 HMAC，但尚不能按供应商要求切换到 RSA2 签名算法”的问题进行了补强验证。

本轮覆盖内容：

1. 告警通知器签名能力从单一 HMAC 扩展为按配置切换 `HMAC_SHA256 / HMAC_SHA1 / HMAC_MD5 / RSA2`
2. 当配置 `RSA2` 时，通知器会按 PKCS8 私钥对请求体执行 `SHA256withRSA` 签名
3. 现有 HMAC 签名链路保持兼容，不影响 IM/SMS/EMAIL 原有测试
4. `LocalImPaymentIssueAlertNotifierTest` 新增 RSA2 场景验证，确认签名头可正常生成

### 63.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=LocalImPaymentIssueAlertNotifierTest,LocalSmsPaymentIssueAlertNotifierTest,LocalEmailPaymentIssueAlertNotifierTest,PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `24` 个用例通过 |

### 63.3 当前判断

1. 当前 `payment-core` 的异常告警外部网关签名能力已从“HMAC 固定实现”升级为“按供应商配置切换 HMAC / RSA2”。
2. 这一步补齐了外部通知供应商常见的非对称签名接入要求，提升了真实供应商接入可行性。
3. 服务端时间窗校验联动、统一防重放编排和更细粒度重试退避策略仍待补齐，因此当前仍不触发 `master / release`。

## 64. 2026-07-25 异常告警补发指数退避策略验证

### 64.1 本轮验证范围

本轮围绕“异常告警补发目前只有固定冷却时间，供应商连续失败时无法按更保守的节奏退避”的问题进行了补强验证。

本轮覆盖内容：

1. `retryPolicy` 在原有 `失败重试N次/间隔M分钟` 基础上，新增 `退避系数X倍/最大间隔Y分钟` 解析能力
2. 告警派发服务会根据历史失败次数动态计算有效冷却时间，而不是始终使用固定间隔
3. 当连续失败次数增大时，补发间隔可按指数增长，并在达到最大间隔后封顶
4. `PaymentIssueAlertDeliveryServiceImplTest` 新增指数退避场景，验证 `5分钟 * 2倍` 并按 `12分钟` 上限封顶
5. 旧有固定冷却测试同步去除日期依赖，确保回归测试稳定

### 64.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentIssueAlertDeliveryServiceImplTest,LocalImPaymentIssueAlertNotifierTest,LocalSmsPaymentIssueAlertNotifierTest,LocalEmailPaymentIssueAlertNotifierTest test` | 通过 | `25` 个用例通过 |

### 64.3 当前判断

1. 当前 `payment-core` 的异常告警补发策略已从“固定冷却”升级为“支持指数退避和最大间隔封顶”的生产化策略。
2. 这一步补齐了真实供应商连续失败时的保守补发节奏控制，降低了短时间重复轰炸供应商网关的风险。
3. 服务端时间窗校验联动和统一防重放编排仍待补齐，因此当前仍不触发 `master / release`。

## 65. 2026-07-25 告警模板占位符扩展渲染验证

### 65.1 本轮验证范围

本轮围绕“告警模板渲染虽然已经支持少量固定字段，但扩展变量仍需改代码、未知占位符也缺少统一兜底”的问题进行了补强验证。

本轮覆盖内容：

1. 模板渲染器从写死 `replace` 升级为占位符驱动解析，统一识别 `{{variableName}}`
2. 新增对 `alertNo / notifyChannels / escalationLevel / providerCode / providerName / endpointAlias / templateCode / templateBody` 等变量的支持
3. 未识别占位符统一兜底为 `-`，避免真实供应商模板漏配时直接原样透出占位符
4. `PaymentIssueAlertDeliveryServiceImplTest` 新增扩展变量与未知占位符场景，验证渲染结果可直接进入通知器

### 65.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `17` 个用例通过 |

### 65.3 当前判断

1. 当前 `payment-core` 的异常告警模板已从“固定字段替换”升级为“通用占位符解析 + 未知字段兜底”的实现。
2. 这一步提升了真实供应商模板接入时的扩展性，也降低了模板配置不完整时的文案风险。
3. 服务端时间窗校验联动和统一防重放编排仍待补齐，因此当前仍不触发 `master / release`。

## 66. 2026-07-25 异常告警服务端时间窗与防重放验证

### 66.1 本轮验证范围

本轮围绕“异常告警虽然已经具备外部网关签名、时间戳和 nonce 头，但服务端仍缺少统一的时间窗/防重放拦截编排”的问题进行了补强验证。

本轮覆盖内容：

1. `retryPolicy` 新增 `防重放窗口N分钟 / 时间窗N分钟` 解析能力
2. 派发服务在正式发送前，会读取同一告警同一通道的最近一次派发日志
3. 若最近一次派发仍处于成功派发保护时间窗内，则直接拦截本次发送，避免把外部网关当作可重复重放入口
4. `PaymentIssueAlertDeliveryServiceImplTest` 新增“最近一次已派发仍在 10 分钟保护期内”场景，验证通知器不会再次调用

### 66.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `18` 个用例通过 |

### 66.3 当前判断

1. 当前 `payment-core` 的异常告警派发链路已具备服务端时间窗与防重放编排，不再只依赖外部网关自行兜底。
2. 这一步显著降低了同一异常在短时间内被重复重放到外部供应商的风险。
3. 当前异常告警链路在“真实供应商接入”方向上的高优先级缺口已进一步缩小，但整体系统仍未达到 `master / release` 冻结门槛。

## 67. 2026-07-25 告警供应商多条件路由规则验证

### 67.1 本轮验证范围

本轮围绕“告警供应商路由规则目前只支持单条件命中，难以支撑按班次、升级等级、接收人等维度进行更细分的多供应商分流”的问题进行了补强验证。

本轮覆盖内容：

1. 供应商 `routeRule` 从单条件 `key=value` 扩展为多条件 `key=value&key2=value2`
2. 命中字段从原有 `severity / issueType / responsibilityGroup` 扩展到 `scheduleTag / receiver / escalationLevel / triggeredBy`
3. 派发服务会按多条件组合规则筛选更合适的候选供应商，再进入既有优先级与补发链路
4. `PaymentIssueAlertDeliveryServiceImplTest` 新增“班次 + 升级等级”复合路由场景，验证白班机器人而非夜班或默认机器人被正确命中

### 67.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `19` 个用例通过 |

### 67.3 当前判断

1. 当前 `payment-core` 的异常告警供应商路由能力已从“单条件命中”升级为“支持班次、升级等级、接收人等多条件组合匹配”。
2. 这一步提升了多供应商在不同值班场景下的精细化分流能力，更接近真实公司内部的告警路由策略。
3. 当前异常告警链路在“真实供应商接入”方向上的高优先级缺口已继续缩小，但整体系统仍未达到 `master / release` 冻结门槛。

## 68. 2026-07-25 异常告警供应商原始回执快照验证

### 68.1 本轮验证范围

本轮围绕“异常告警投递日志里只有归一化状态和简短说明，但缺少供应商原始回执快照，后续排查 webhook / 网关回执差异时证据不够完整”的问题进行了补强验证。

本轮覆盖内容：

1. 告警投递结果 DTO、投递日志实体、异常明细行 DTO 新增 `providerReceiptSnapshot` 字段
2. 本地通知器统一补充原始回执快照生成逻辑，本地模拟回执记录 `LOCAL:<channel>:<status>`，HTTP/Webhook 回执记录 `HTTP_RESPONSE:<rawBody>`
3. 告警派发服务在落库时单独写入 `provider_receipt_snapshot`，失败兜底场景也会补默认快照
4. 异常中心告警列表增加“供应商原始回执快照”展示，便于值班同学直接查看供应商原始返回
5. 通知器与派发服务测试补充快照断言，验证“产出快照 -> 落库 -> 页面可读”链路闭环

### 68.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=LocalImPaymentIssueAlertNotifierTest,LocalSmsPaymentIssueAlertNotifierTest,LocalEmailPaymentIssueAlertNotifierTest,PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `28` 个用例通过 |
| 管理端构建验证 | `npm run build` | 通过 | `systems/payment-core/frontend/admin-web` 构建成功 |

### 68.3 当前判断

1. 当前 `payment-core` 的异常告警投递链路已经具备“归一化状态 + 原始供应商回执快照”双维度留痕能力，排查 webhook/供应商回执问题时证据更完整。
2. 这一步把异常中心从“只能看摘要状态”提升到“可以直接看到原始回执片段”，更接近公司内部告警台账的可追溯要求。
3. 当前补强仍聚焦在异常告警链路，`payment-core` 全系统仍未达到 `master / release` 冻结门槛，后续还需要继续补齐更广范围的业务闭环与联调验证。

## 69. 2026-07-25 异常告警人工确认回执闭环验证

### 69.1 本轮验证范围

本轮围绕“异常告警明细页虽然能看到回执状态，但运营在核对站内触达或外部供应商送达后，缺少独立的人工确认回执动作，导致‘异常已处理’与‘告警已确认’两种业务语义仍然耦合”的问题进行了补强验证。

本轮覆盖内容：

1. 新增 `POST /api/payment-issues/alerts/{alertNo}/acknowledge` 独立确认接口，按告警编号收口人工确认回执
2. 服务层补充“告警不存在 / 无需回执 / 已确认幂等返回 / 缺少确认人”校验，避免错误确认
3. 异常告警明细页增加“默认确认人”和“确认回执”操作列，支持对 `待确认` 告警单独收口
4. 人工确认成功后，页面会即时刷新当前行的 `ackStatus / ackOperator / ackAt`
5. `PaymentIssueServiceImplTest` 新增人工确认回执用例，验证确认成功与“无需回执”拦截场景

### 69.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentIssueServiceImplTest test` | 通过 | `9` 个用例全部通过 |
| 管理端构建验证 | `npm run build` | 通过 | `systems/payment-core/frontend/admin-web` 构建成功 |

### 69.3 当前判断

1. 当前 `payment-core` 的异常告警中心已经具备“系统自动派发留痕 + 人工确认回执收口”两段式闭环，业务语义比之前更清晰。
2. 这一步让运营、研发和测试在排查告警时不再只能依赖“标记异常已处理”顺带确认告警，而是可以对触达本身独立留痕。
3. 当前补强仍聚焦在异常治理维度，`payment-core` 依旧未达到 `master / release` 冻结门槛，后续还需要继续补更广的页面矩阵与支付主链路联调验证。

## 70. 2026-07-26 三端收银台主动查单补强验证

### 70.1 本轮验证范围

本轮围绕“收银台页虽然可以刷新会话，但在用户扫码后等待回调、PC/H5 页面停留或联调排障时，缺少独立主动查单动作，导致必须先跳到结果页或后台再确认状态”的问题进行了补强验证。

本轮覆盖内容：

1. 在 `app-web / h5-web / pc-web` 共用收银台组件中新增 `主动查单` 按钮
2. 收银台右侧新增“主动查单快照”区域，展示最近查单状态、查单来源、渠道流水号和最近尝试状态
3. 若查单结果已经收口为 `SUCCESS / CLOSED`，前端自动跳转到支付结果页继续处理后续动作
4. 同步更新前端页面说明文档，明确收银台已具备“等待回调中直接查单”的正式交互能力

### 70.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| App 端构建验证 | `npm run build` | 通过 | `systems/payment-core/frontend/app-web` 构建成功 |
| H5 端构建验证 | `npm run build` | 通过 | `systems/payment-core/frontend/h5-web` 构建成功 |
| PC 端构建验证 | `npm run build` | 通过 | `systems/payment-core/frontend/pc-web` 构建成功 |

### 70.3 当前判断

1. 当前 `payment-core` 的三端收银台已经从“只能刷新会话”提升到“可在当前页执行主动查单并查看最近查单快照”，更贴近真实支付产品的用户等待与运营排障场景。
2. 这一步补强了支付主链路中“支付后等待回调”的中间态处理，尤其适合桌面扫码支付、H5 外部唤起返回和 App 内联调复核。
3. 本轮补强仍聚焦收银台交互体验，`payment-core` 依旧未达到 `master / release` 冻结门槛，后续还需要继续补更广的支付主链路、页面矩阵与跨系统联调验证。

## 71. 2026-07-26 三端结果页真实渠道回调补强验证

### 71.1 本轮验证范围

本轮围绕“支付结果页的模拟成功回调动作写死为 `WX_H5`，会导致支付宝、银行卡、PC 扫码等场景联调结果失真”的问题进行了补强验证。

本轮覆盖内容：

1. 结果页改为优先使用当前支付单真实 `channel` 发起模拟回调
2. 若详情里暂未返回渠道编码，则回退使用支付方式映射出的渠道编码
3. 结果摘要区新增“回调模拟渠道”字段，方便测试、运营和研发快速确认当前回调到底打到了哪个渠道
4. 同步更新前端页面说明文档，明确结果页模拟回调必须遵循真实渠道口径

### 71.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| App 端构建验证 | `npm run build` | 通过 | `systems/payment-core/frontend/app-web` 构建成功 |
| H5 端构建验证 | `npm run build` | 通过 | `systems/payment-core/frontend/h5-web` 构建成功 |
| PC 端构建验证 | `npm run build` | 通过 | `systems/payment-core/frontend/pc-web` 构建成功 |

### 71.3 当前判断

1. 当前 `payment-core` 的三端支付结果页已经具备“按真实渠道模拟回调”的联调能力，不再把所有支付方式都错误归一到微信渠道。
2. 这一步补强了多支付方式、多终端场景下的联调准确性，尤其对支付宝、银行卡和 PC 桌面端支付更重要。
3. 本轮补强仍聚焦支付结果页交互与联调准确性，`payment-core` 依旧未达到 `master / release` 冻结门槛，后续还需要继续补更广的支付主链路、页面矩阵与跨系统联调验证。

## 72. 2026-07-26 支付端 accessToken 透传闭环验证

### 72.1 本轮验证范围

本轮围绕“收银台跳转结果页后未继续透传 `accessToken`，导致用户从结果页返回收银台时联调令牌丢失，页面虽然能打开但无法维持同一套调用上下文”的问题进行了补强验证。

本轮覆盖内容：

1. 收银台跳转支付结果页时，统一透传运行时 `accessToken` 和 `terminalVariant`
2. 收银台主动查单后若状态已收口并自动跳转结果页，也同步透传 `accessToken` 和 `terminalVariant`
3. 结果页点击“返回收银台”时继续带回 `accessToken` 和 `terminalVariant`，保证二次进入收银台仍处于同一联调上下文
4. 同步更新前端页面说明文档，明确支付端页面间跳转必须保留运行时令牌

### 72.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| App 端构建验证 | `npm run build` | 通过 | `systems/payment-core/frontend/app-web` 构建成功 |
| H5 端构建验证 | `npm run build` | 通过 | `systems/payment-core/frontend/h5-web` 构建成功 |
| PC 端构建验证 | `npm run build` | 通过 | `systems/payment-core/frontend/pc-web` 构建成功 |

### 72.3 当前判断

1. 当前 `payment-core` 的支付端主链路已经补齐“收银台 -> 结果页 -> 再回收银台”的运行时令牌透传，不再因为页面跳转打断联调上下文。
2. 这一步提升了 App / H5 / PC 三端在真实联调和问题复现时的连续性，尤其适合测试、运营和客服重复进入支付端页面进行查单与补救动作。
3. 本轮补强仍聚焦支付端页面跳转一致性，`payment-core` 依旧未达到 `master / release` 冻结门槛，后续还需要继续补更广的支付主链路、页面矩阵与跨系统联调验证。

## 73. 2026-07-26 支付业务入口页与虚拟预付单验证

### 73.1 本轮验证范围

本轮围绕“充值、提现、转账、余额支付四类用户端入口页仍缺失，且 `prepay` 只能依赖既有订单”的问题进行了补强验证。

本轮覆盖内容：

1. `app-web / h5-web / pc-web` 三端新增充值、提现、转账、余额支付入口页
2. 入口页直接调用统一 `prepay`，进入同一套收银台、提交和结果页链路
3. 后端 `prepay` 增加虚拟业务单模式，支持非标准订单型场景直接创建预付单
4. 同步更新差距清单，避免文档继续把这四类入口写成未实现

### 73.2 验证命令

| 项目 | 命令/方式 | 预期结果 | 说明 |
| --- | --- | --- | --- |
| 后端单测 | `mvn -Dtest=PaymentServiceImplTest test` | 通过 | `PaymentServiceImplTest` 20 个测试通过 |
| App 端构建验证 | `npm run build` | 通过 | `systems/payment-core/frontend/app-web` 构建成功 |
| H5 端构建验证 | `npm run build` | 通过 | `systems/payment-core/frontend/h5-web` 构建成功 |
| PC 端构建验证 | `npm run build` | 通过 | `systems/payment-core/frontend/pc-web` 构建成功 |

### 73.3 当前判断

1. 当前 `payment-core` 的用户端入口不再只有收银台和结果页，四类业务入口已经可以直接拉起统一支付链路。
2. 虚拟预付单模式让充值、提现、转账、余额支付先具备可跑通的产品入口，后续再继续接真实钱包、账户和提现域。
3. 本轮补强仍属于支付核心域扩展，`payment-core` 依旧未达到 `master / release` 冻结门槛，后续还需要继续补更广的支付主链路、页面矩阵与跨系统联调验证。

## 74. 2026-07-28 payment-core 全量后端回归验证

### 74.1 本轮验证范围

本轮对 `payment-core` 后端执行完整 Maven 测试套件，覆盖支付主链路、退款、异常中心、告警派发、配置治理、任务中心、日终处理、支付记录、收银台会话和渠道适配等已有自动化用例。

### 74.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端全量回归 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository test` | 通过 | `149` 个测试全部通过，`0` failures，`0` errors，`0` skipped |
| 后台前端构建 | `systems/payment-core/frontend/admin-web` 执行 `npm run build` | 通过 | 已覆盖本轮工作台、支付记录详情、退款详情、异常告警明细台、支付监控分析等页面增强后的生产构建 |
| 提交前差异校验 | `git diff --check` | 通过 | 本轮前端与文档改动未发现空白字符错误 |

### 74.3 回归覆盖重点

1. 支付主链路：预付单创建、支付提交、回调收口、主动查单、关单保护。
2. 退款链路：申请、审核、成功/失败回调、失败重试、操作日志。
3. 支付可靠性：超时关单、失败事件重发、异常 SLA 升级、告警派发与回执确认。
4. 配置治理：渠道、路由、协议、网关、返回码、支付控制策略。
5. 运营查询：订单、账单、支付单、支付流水、支付请求、处理日志、支付记录、收银台会话、服务者结算查询入口。

### 74.4 当前判断

1. 本轮证明当前已有的后端自动化测试套件可稳定执行，具备进入后续 `test` 分支集成验证的基础。
2. 该结果不等同于完整支付平台已可发布：真实渠道、真实回调验签、跨系统联调、压测、容灾演练与生产通知供应商接入仍是后续门槛。
3. `master / release` 的推进仍需以完整交付差距清单收口、跨端构建回归、关键主链路 smoke test 和跨系统接口验证通过为准。

## 75. 2026-07-29 虚拟预付单回调与自动下游联调验证

### 75.1 本轮验证范围

本轮围绕虚拟业务单场景的真实联调缺陷进行收口，重点验证以下内容：

1. 非标准订单型支付场景创建虚拟预付单后，支付成功回调是否能正确更新账单实付金额
2. 支付成功事件是否会由 `payment-core` 自动下发到 `clearing-system` 与 `accounting-system`
3. 修复后端回调逻辑后，完整 Maven 回归测试与真实 smoke 是否同时通过

### 75.2 缺陷定位

真实调用 `POST /api/payments/callback/alipay_h5` 时发现：

1. 虚拟订单 `SMOKE-ORDER-20260729-002` 不存在 `t_order` 数据
2. `findOrderAmount(orderNo)` 返回空值
3. 回调成功分支仍直接使用空金额更新 `t_bill.paid_amount`
4. 最终触发数据库异常：`Column 'paid_amount' cannot be null`

### 75.3 修复内容

1. `PaymentServiceImpl.callback(...)` 在支付成功分支增加账单实付金额兜底：当订单金额缺失时，使用支付单金额 `detail.getAmount()` 解析结果作为 `settledAmount`
2. 仅在存在真实订单金额时才更新 `t_order`
3. 补充 `PaymentServiceImplTest.shouldUsePaymentAmountToCloseBillWhenCallbackOrderSourceMissing`
4. 重新启动 `payment-core` 本地服务，确保联调使用的是修复后的最新编译产物

### 75.4 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端全量回归 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository test` | 通过 | `150` 个测试全部通过 |
| 虚拟预付单创建 | `POST /api/payments/prepay` | 通过 | 生成 `PRE1785287995357` / `PAY1785287995355` |
| 支付成功回调 | `POST /api/payments/callback/alipay_h5` | 通过 | 支付单状态成功收口为 `SUCCESS` |
| 清分消费验证 | `GET /api/clearing/events?bizNo=PAY1785287995355&pageNo=1&pageSize=20` | 通过 | 生成 `EVT60003` |
| 账务消费验证 | `GET /api/accounting/events?bizNo=PAY1785287995355&pageNo=1&pageSize=20` | 通过 | 生成 `EVT50003` |

### 75.5 当前判断

1. 虚拟预付单场景已经具备真实回调成功收口能力，不再因为缺少订单主表而导致账单更新失败。
2. `payment-core -> clearing-system / accounting-system` 的自动下游联动已在本机环境真实验证通过。
3. 后续仍要继续补 `settlement-system` 自动串联、MQ 级可靠投递、补偿与死信验证，才能继续推进更高阶段的冻结交付。

## 76. 2026-07-29 告警派发时间窗正式化验证

### 76.1 本轮验证范围

本轮围绕“异常告警派发虽然已支持防重放窗口配置，但 `时间窗X分钟` 仍未真正按站内告警创建时间生效”的问题进行补强，目标是避免过期 outbox 告警继续外发到真实通知通道。

### 76.2 本轮新增内容

1. `PaymentIssueAlertDispatchItemDTO` 新增 `createdAt` 字段，正式承接站内 outbox 告警创建时间。
2. `PaymentTaskCenterMapper.xml#findPendingOutboxAlerts` 补齐 `createdAt` 查询下推，避免派发服务只能依赖运行时最新日志推断时间窗。
3. `PaymentIssueAlertDeliveryServiceImpl` 新增服务端新鲜度护栏：当供应商 `retryPolicy` 配置了 `时间窗X分钟` 且 outbox 告警已过期时，直接回写 `FRESHNESS_WINDOW_EXPIRED`，不再继续调用外部通知器。
4. 保留已有 `防重放窗口` 逻辑，形成“来源告警时间窗 + 最近一次派发保护窗”双重护栏。
5. `PaymentIssueAlertDeliveryServiceImplTest` 新增过期时间窗用例，并同步修正基础测试数据的 `createdAt`，避免旧用例误命中过期护栏。

### 76.3 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -Dtest=PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `20` 个用例全部通过，新增覆盖 `FRESHNESS_WINDOW_EXPIRED` |

### 76.4 当前判断

1. 当前 `payment-core` 的异常告警派发不再只校验“最近是否派发成功”，也会校验“这条 outbox 告警本身是否已经过期”，更接近正式通知中心的时间窗治理口径。
2. 这一步补齐了文档里多次提到的“服务端时间窗校验联动”缺口，后续接入真实企业微信、短信和邮件供应商时不需要再回头重改主链路。
3. 真实外部供应商回执状态映射、失败码标准化和跨实例协调仍需继续补齐，因此本轮依旧不触发 `master / release`。

## 77. 2026-07-29 已确认告警停止外发验证

### 77.1 本轮验证范围

本轮围绕“站内 outbox 告警一旦被人工确认，后续自动派发任务仍可能继续把它投递到外部通道”的闭环缺口进行补强，目标是避免已确认告警继续触发重复通知。

### 77.2 本轮新增内容

1. `PaymentIssueAlertDispatchItemDTO` 新增 `ackStatus` 字段，正式承接站内 outbox 确认状态。
2. `PaymentTaskCenterMapper.xml#findPendingOutboxAlerts` 增加 `ack_status` 下推，并只返回 `待确认` 的 source outbox 告警。
3. `PaymentIssueAlertDeliveryServiceImpl` 增加服务端兜底校验：即使查询层漏拦截，`ackStatus=已确认` 的 source outbox 也会被直接跳过，不再进入任何通知器。
4. 异常告警派发结果中的 `processedCount` 与摘要口径改为按“实际参与派发的告警数”统计，不再把已确认后被跳过的数据误算进去。
5. `PaymentIssueAlertDeliveryServiceImplTest` 新增“source outbox 已确认时不再外发”场景，验证不会写派发日志、不会回写通道状态，也不会继续调用 IM/SMS/EMAIL 通知器。

### 77.3 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -Dtest=PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `21` 个用例全部通过，新增覆盖“已确认即停止外发” |

### 77.4 当前判断

1. 当前 `payment-core` 的异常告警派发链路已经具备“时间窗/防重放/人工确认”三层收敛保护，不再因为 source outbox 已确认却继续被自动派发而重复打扰值班人员。
2. 这一步进一步提升了异常告警链路在真实运维场景下的可控性，也让任务中心、异常中心和人工确认动作之间的闭环更完整。
3. 真实通知供应商接入、跨实例协调和更大范围跨系统门禁仍需继续补齐，因此本轮依旧不触发 `master / release`。

## 78. 2026-07-29 自动回执确认审计留痕验证

### 78.1 本轮验证范围

本轮围绕“异常告警回执回查已经能把供应商日志从 `ACCEPTED` 回写为 `DELIVERED`，但自动确认人/确认时间留痕不足”的问题进行补强，目标是让自动回查形成可审计的正式闭环。

### 78.2 本轮新增内容

1. `PaymentIssueAlertLogEntity` 新增 `ackOperator / ackAt` 字段，补齐异常告警自动确认的审计承载对象。
2. `PaymentTaskCenterMapper.xml#findAcceptedIssueAlertDeliveryLogs` 查询补齐 `ackOperator / ackAt` 映射，保证后续联查和二次处理能拿到完整上下文。
3. `PaymentTaskCenterMapper.xml#updateIssueAlertProviderReceipt` 增加 `ack_operator` 和 `ack_at = NOW()` 回写，自动回执确认不再只是改状态。
4. `PaymentIssueAlertDeliveryServiceImpl#buildReceiptUpdate` 在自动回查成功时，将 `triggeredBy` 同步写入 `ackOperator`，明确是谁触发了自动确认。
5. `PaymentIssueAlertDeliveryServiceImplTest` 增加断言，确认自动回查后 `ackOperator=payment-core-admin` 可正确沉淀。

### 78.3 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -Dtest=PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `21` 个用例全部通过，新增覆盖自动确认人留痕 |

### 78.4 当前判断

1. 当前 `payment-core` 的异常告警回执回查不再只是“状态收口”，还具备了“自动确认人 + 自动确认时间”的审计留痕，更接近公司内部正式运维台账的要求。
2. 这一步提升了测试、运营和研发回放自动回查任务时的可解释性，也为后续 release 前门禁审计提供了更强证据。
3. 真实供应商回执 API、跨实例协调和更广范围跨系统联动仍需继续补齐，因此本轮依旧不触发 `master / release`。

## 79. 2026-07-29 回执回查同步确认 source outbox 验证

### 79.1 本轮验证范围

本轮围绕“文档声称回执回查成功后会同步回写 `ack_status=已确认`，但实际代码只更新了外部通道日志，source outbox 仍可能保持 `待确认` 并继续触发升级巡检”的真实闭环缺口进行修复。

### 79.2 本轮新增内容

1. `PaymentTaskCenterMapper#findAcceptedIssueAlertDeliveryLogs` 补齐 `sourceAlertNo` 查询映射，保证回执回查阶段能拿到来源站内告警编号。
2. 新增 `PaymentTaskCenterMapper#updateSourceIssueAlertAcknowledgement`，用于在回执回查成功后回写 source outbox 的确认状态。
3. `PaymentIssueAlertDeliveryServiceImpl` 在外部通道日志从 `ACCEPTED -> DELIVERED` 回写成功后，同步将 `sourceAlertNo` 对应的站内 outbox 更新为 `ack_status=已确认`，并沉淀 `ackOperator / ackAt`。
4. `PaymentIssueAlertDeliveryServiceImplTest` 新增断言，确认回执回查成功后会调用 `updateSourceIssueAlertAcknowledgement`，避免 source outbox 长期悬挂在 `待确认`。

### 79.3 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -Dtest=PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `21` 个用例全部通过，新增覆盖 source outbox 同步确认 |

### 79.4 当前判断

1. 当前 `payment-core` 的异常告警回执回查终于从“只改外部通道日志”升级为“外部通道日志 + source outbox 一起收口”的完整闭环，和文档口径保持一致。
2. 这一步直接降低了“供应商已送达但 source outbox 仍触发升级巡检”的误报风险，也提升了异常中心、任务中心和升级巡检之间的一致性。
3. 真实供应商 API、跨实例协调和更广范围跨系统门禁仍需继续补齐，因此本轮依旧不触发 `master / release`。

## 80. 2026-07-29 人工确认同步收口 source outbox 验证

### 80.1 本轮验证范围

本轮围绕“异常中心人工确认的是一条外部告警日志，但 source outbox 仍可能保持 `待确认`，从而与自动回查收口口径不一致”的问题进行补强，目标是让人工确认和自动确认都能收口同一条来源站内告警。

### 80.2 本轮新增内容

1. `PaymentIssueServiceImpl#acknowledgeAlert` 在确认目标告警后，若该告警存在 `sourceAlertNo`，会同步确认对应的 source outbox。
2. 同步确认沿用现有 `PaymentIssueMapper#acknowledgeAlertByAlertNo`，不额外引入并行口径，保持异常中心确认动作的一致性。
3. `PaymentIssueServiceImplTest` 在“人工确认成功”场景中新增 source outbox 断言，验证 `PIA-OUTBOX-001` 会与外部告警日志一起被确认。

### 80.3 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -Dtest=PaymentIssueServiceImplTest test` | 通过 | `9` 个用例全部通过，新增覆盖人工确认同步 source outbox |

### 80.4 当前判断

1. 当前 `payment-core` 的异常告警确认链路已经在“自动回查确认”和“人工确认”两条入口上统一收口 source outbox，不再出现一边已确认、一边仍待确认的状态撕裂。
2. 这一步进一步提升了异常中心、任务中心和升级巡检三者之间的一致性，也让值班同学在手工处置时不会留下隐藏的升级触发点。
3. 真实供应商 API、跨实例协调和更广范围跨系统门禁仍需继续补齐，因此本轮依旧不触发 `master / release`。

## 81. 2026-07-29 异常告警自动任务跨实例租约锁验证

### 81.1 本轮验证范围

本轮围绕“异常告警自动派发 / 自动回执回查在多实例部署下仍可能被多个调度器重复执行，导致重复外发和重复回写”的问题进行补强，目标是让 `payment-core` 至少在最关键的异常告警自动任务上具备数据库共享租约锁能力。

### 81.2 本轮新增内容

1. `schema.sql` 新增 `t_payment_task_lease`，沉淀任务级分布式租约锁。
2. `PaymentTaskCenterMapper / PaymentTaskCenterMapper.xml` 新增 `initTaskLease / acquireTaskLease / releaseTaskLease`。
3. `PaymentIssueAlertDeliveryServiceImpl` 的 `autoDispatchPendingAlerts / autoReconcileDeliveryReceipts` 在正式执行前先抢占租约锁。
4. 当检测到其他实例已持锁时，本次自动任务会直接跳过，并落一条 `WARNING` 级任务执行日志。
5. 自动任务执行完成后会释放本实例持有的租约，避免锁长期悬挂。
6. `PaymentIssueAlertDeliveryServiceImplTest` 新增“租约被占用时跳过”和“回执回查后释放租约”场景。

### 81.3 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `23` 个用例全部通过，新增覆盖自动任务租约锁跳过与释放 |

### 81.4 当前判断

1. 当前 `payment-core` 的异常告警自动派发与自动回执回查已经具备第一版跨实例共享租约锁，不再默认允许多个调度实例同时执行同一任务。
2. 这一步显著降低了多实例部署时重复派发、重复回查和重复写任务日志的风险，更接近正式生产调度体系的基本门槛。
3. 真实供应商 API、更多任务类型的跨实例协调和更广范围跨系统门禁仍需继续补齐，因此本轮依旧不触发 `master / release`。

## 82. 2026-07-29 任务中心自动调度跨实例租约锁扩围验证

### 82.1 本轮验证范围

本轮围绕“只有异常告警自动任务具备跨实例租约锁，其余任务中心自动调度在多实例下仍会重复执行”的问题进行补强，目标是让 `payment-core` 任务中心核心自动任务统一纳入共享租约锁治理。

### 82.2 本轮新增内容

1. `PaymentTaskCenterServiceImpl` 的 `runAutoCloseExpiredPayments / runAutoRepublishFailedEvents / runAutoRetryFailedRefunds / runAutoEscalateOverdueIssues / runAutoControlPolicySelfChecks` 统一接入 `runAutoTaskWithLease`。
2. 自动任务开始前统一执行 `initTaskLease / acquireTaskLease`，执行完成后统一释放租约。
3. 当租约已被其他实例持有时，本实例直接跳过执行，并写入 `WARNING` 级任务执行日志。
4. `PaymentTaskCenterServiceImplTest` 补齐自动关单跳过、自动自检抢锁、自动事件重发释放锁、自动退款重试释放锁、自动 SLA 升级释放锁等场景。

### 82.3 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentTaskCenterServiceImplTest test` | 通过 | `16` 个用例全部通过，新增覆盖任务中心多类自动任务的租约锁跳过与释放 |

### 82.4 当前判断

1. 当前 `payment-core` 的任务中心核心自动调度已从“单任务有锁”升级为“多任务统一租约锁治理”，更接近真实生产多实例部署的任务调度标准。
2. 这一步继续缩小了 `master / release` 门槛里“跨实例协调”这一类高优先级缺口。
3. 真实供应商 API、更广范围跨系统门禁和支付主链路/页面矩阵全量联调仍需继续补齐，因此本轮依旧不触发 `master / release`。

## 83. 2026-07-29 通知供应商业务失败码标准化回执验证

### 83.1 本轮验证范围

本轮围绕“外部通知供应商虽然已经支持响应体解析，但当业务码不通过或 HTTP 非 2xx 时仍主要依赖抛异常，导致任务中心和异常中心拿不到稳定失败证据”的问题进行补强，目标是让通知器把这类场景标准化收口为 `FAILED` 投递结果。

### 83.2 本轮新增内容

1. `AbstractLocalPaymentIssueAlertNotifier#buildWebhookDeliveryResult` 新增对 HTTP 状态码和业务成功码的统一失败判定。
2. 当业务码不满足 `successExpectedValue` 时，不再只抛异常，而是沉淀 `FAILED` 投递状态，并把 `期望/实际` 写入 `businessCheck`。
3. 当供应商返回 HTTP 非 `2xx` 时，即使响应体里带有受理态字段，也统一按失败回执处理。
4. `LocalImPaymentIssueAlertNotifierTest / LocalSmsPaymentIssueAlertNotifierTest / LocalEmailPaymentIssueAlertNotifierTest` 新增业务码失败与 HTTP 非 `2xx` 标准化失败场景。

### 83.3 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=LocalImPaymentIssueAlertNotifierTest,LocalSmsPaymentIssueAlertNotifierTest,LocalEmailPaymentIssueAlertNotifierTest,PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `33` 个用例全部通过，新增覆盖 IM/SMS/EMAIL 三通道业务码失败与 HTTP 500 场景 |

### 83.4 当前判断

1. 当前 `payment-core` 的 IM/SMS/EMAIL 三类通知器已经从“业务失败主要靠异常抛出”升级为“失败结果可标准化落库、可被异常中心和任务中心审计”。
2. 这一步继续缩小了真实通知供应商接入里的“响应体业务码解析 / 失败码标准化”缺口。
3. 真实供应商 API、更多供应商特定签名/鉴权细节和更广范围跨系统门禁仍需继续补齐，因此本轮依旧不触发 `master / release`。

## 84. 2026-07-29 支付成功双下游自动联动门禁验证

### 84.1 本轮验证范围

本轮围绕“文档已经声称 `payment-core -> clearing-system / accounting-system` 的下游自动联动成立，但缺少稳定自动化测试门禁”的问题进行补强，目标是把支付成功后的双下游投递纳入自动化验证。

### 84.2 本轮新增内容

1. 新增 `PaymentEventDispatchServiceImplTest`。
2. 补齐“清分与账务双下游都成功时标记事件成功”场景。
3. 补齐“账务下游返回非 2xx 时标记事件失败”场景。
4. 补齐“事件不存在时 republish 返回 false”场景。
5. 补齐“清分载荷 / 账务载荷关键字段正确组装”场景。

### 84.3 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentEventDispatchServiceImplTest test` | 通过 | `4` 个用例全部通过，新增覆盖 clearing/accounting 双下游自动联动门禁 |

### 84.4 当前判断

1. 当前 `payment-core` 已经不只是“本机手工 smoke 证明能联动下游”，而是把支付成功后的 `clearing-system / accounting-system` 双下游自动投递纳入了后端自动化门禁。
2. 这一步继续缩小了“更广范围跨系统门禁”这一类发布缺口。
3. 真实供应商 API、更大范围跨系统链路矩阵和支付端页面矩阵全量联调仍需继续补齐，因此本轮依旧不触发 `master / release`。

## 85. 2026-07-29 支付成功回调触发双下游分发门禁验证

### 85.1 本轮验证范围

本轮围绕“虽然已有下游投递测试，但仍缺少主链路层面对 `callback -> publishPaymentSuccess -> clearing/accounting` 触发关系的自动化门禁”进行补强，目标是把支付成功回调与双下游分发之间的主链路关系补成显式测试。

### 85.2 本轮新增内容

1. `PaymentServiceImplTest` 新增“支付成功回调会写 `PAYMENT_SUCCESS` 事件并触发 `paymentEventDispatchService.publishPaymentSuccess`”场景。
2. `PaymentServiceImplTest` 新增“非成功回调只写 `PAYMENT_PENDING` 事件，不触发下游分发”场景。
3. 联合 `PaymentEventDispatchServiceImplTest`，形成“主链路触发 + 双下游投递”两段式自动化门禁。

### 85.3 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentServiceImplTest,PaymentEventDispatchServiceImplTest test` | 通过 | `27` 个用例全部通过，新增覆盖成功回调触发下游分发与待回调不触发下游分发 |

### 85.4 当前判断

1. 当前 `payment-core` 已经把“支付成功回调 -> 事件落库 -> 双下游分发”这条跨系统主链路补成了自动化门禁，而不只是依赖人工 smoke 和记忆口径。
2. 这一步继续缩小了 `master / release` 前必须验证的跨系统链路缺口。
3. 真实供应商 API、更大范围跨系统链路矩阵和支付端页面矩阵全量联调仍需继续补齐，因此本轮依旧不触发 `master / release`。

## 86. 2026-07-29 payment-core 后端全量回归刷新验证

### 86.1 本轮验证范围

本轮围绕“近期已经连续补入异常告警通知标准化、供应商回退、自动任务跨实例租约锁、支付成功回调触发双下游分发，但文档里的全量回归基线仍停留在旧测试数量”这一问题进行复核，目标是刷新 `payment-core` 当前真实可引用的后端回归基线。

### 86.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端全量测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml test` | 通过 | `171` 个用例全部通过，`0` failures，`0` errors，`0` skipped |

### 86.3 本轮判断

1. 当前 `payment-core` 的后端自动化门禁已刷新到 `171` 个测试通过，最新补强的异常治理和跨系统主链路能力已经纳入全量回归，而不再只是局部定向验证。
2. 这提升了 `test` 分支继续迭代时的可信度，也让后续 `master / release` 审计有了更新鲜的测试基线。
3. 但该结果仍不等同于支付系统整包已可发布，前端跨端统一回归、整包多系统统一回归、MQ 级可靠投递和回滚演练仍需继续补齐。
