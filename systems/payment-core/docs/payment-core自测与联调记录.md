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

## 8. 2026-09-25 支付主链路适配器集合边界复核

本轮补齐支付提交与主动查单服务的适配器装配边界：`null` 集合按无可用适配器处理，集合内的 `null` 元素被过滤，保留有效适配器继续完成渠道处理。新增提交服务和查单服务回归覆盖空集合、空元素与有效适配器混合场景。

验证结果：

1. `PaymentChannelSubmitServiceImplTest` 与 `PaymentChannelQueryServiceImplTest` 定向测试通过。
2. payment-core 全量后端回归：`1227/1227`，失败 `0`、错误 `0`、跳过 `0`。

本轮只增强支付主链路骨架在脏装配数据下的可用性，不改变真实渠道协议、资金状态、生产基础设施或正式发布治理结论。

## 9. 2026-09-25 主动查单适配器异常失败闭合

主动查单服务现在与支付提交服务保持一致：渠道适配器或结果标准化链路发生未预期运行时异常时，统一收敛为 `PAYMENT_CHANNEL_QUERY_FAILED`，不向调用方泄漏数据库地址、令牌或原始异常堆栈。新增回归覆盖敏感异常信息脱敏、异常因果链清除和 mapper 零交互。

验证结果：

1. `PaymentChannelQueryServiceImplTest`、`PaymentChannelSubmitServiceImplTest` 定向测试通过。
2. payment-core 全量后端回归：`1228/1228`，失败 `0`、错误 `0`、跳过 `0`。

本轮只收口渠道查单失败契约，不改变真实渠道协议、支付状态机、资金事实或正式发布治理结论。

## 10. 2026-09-25 退款查单适配器异常失败闭合

退款主动查单服务现在对渠道适配器中的未预期运行时异常统一返回 `REFUND_CHANNEL_QUERY_FAILED`，并清除原始异常因果链，避免内部数据库连接、令牌和堆栈信息泄漏。新增退款查单服务回归覆盖敏感异常脱敏。

验证结果：

1. `RefundChannelQueryServiceImplTest` 定向测试通过。
2. payment-core 全量后端回归：`1229/1229`，失败 `0`、错误 `0`、跳过 `0`。

本轮只收口退款渠道查单失败契约，不改变退款状态机、真实退款渠道、到账事实或正式发布治理结论。

## 11. 2026-09-25 渠道适配器选择异常失败闭合

支付提交、支付查单和退款查单服务现在对适配器 `supports(...)` 选择阶段的未预期异常统一失败闭合，并与适配器实际执行阶段分离处理，保留稳定错误码和脱敏错误信息。新增三条服务层回归覆盖支付提交、支付查单和退款查单的选择失败场景。

验证结果：

1. 支付提交、支付查单和退款查单定向测试通过。
2. payment-core 全量后端回归：`1232/1232`，失败 `0`、错误 `0`、跳过 `0`。

本轮只增强渠道适配器装配和选择边界的可用性，不改变路由、状态机、真实渠道协议、资金事实或正式发布治理结论。

## 12. 2026-09-25 退款提交与回调链路复核

沿退款申请、审核、事件派发、渠道提交、回调/主动查单和未知结果恢复路径复核当前代码：退款渠道提交由事件派发层统一编排，`UNKNOWN` 结果禁止自动重试并要求查单确认；退款回调统一经过渠道签名、时间窗和 nonce 防重放校验，最终状态通过 compare-and-set 收口。当前没有发现新的未闭合代码增量，本轮以既有实现和回归结果作为复核证据。

该复核不把本地模拟适配器、受控 HTTP 或 H2 结果解释为真实退款渠道受理、退款到账、生产密钥或财务对账事实。

## 8. 2026-08-05 正式来源应用控制策略与路由回归

### 8.1 本轮问题定位

本轮针对正式来源应用提交流程进行复核时，发现 `housekeeping-app-web`、`housekeeping-h5-web`、`housekeeping-pc-web` 在严格模式下会命中 `PAYMENT-1019`，导致正式来源应用无法直接提交支付。

进一步排查确认有两类根因：

1. 支付控制策略自检把“来源应用商户号”误当成“渠道商户号”校验，导致 `MCH_HOME_APP / MCH_HOME_PC` 被误判为未配置。
2. 渠道路由场景判断未对 `MOBILE_WEB / PC_WEB` 和配置中的 `H5 / PC` 做别名归一化，导致 H5 与 PC 终端明明已开通渠道，但路由阶段仍报 `PAYMENT-1003 unsupported payment channel`。

### 8.2 本轮修复

本轮已完成：

1. 在 `PaymentConfigServiceImpl` 中补齐支付方式别名归一化：
   - `银行卡` 与 `银行转账` 统一归一为同一业务支付方式口径
2. 在 `PaymentConfigServiceImpl` 中修正商户号自检语义：
   - 支持区分“来源应用授权商户号”与“渠道商户号”
   - 避免正式来源应用控制策略被误判为 `WARN`
3. 在 `PaymentChannelRoutingServiceImpl` 中补齐终端场景归一化：
   - `MOBILE_WEB -> h5`
   - `PC_WEB -> pc`
   - `APP -> app`
4. 使用 JDK `1.8.0_202` 与 Maven `3.9.16` 完成后端重新编译并重启本地服务。

### 8.3 本轮验证结果

#### 控制策略自检

2026-08-05 16:24 至 16:25，通过 `POST /api/payment-config/control-policies/self-check` 重新执行正式来源应用自检后：

1. `housekeeping-app-web` = `PASS`
2. `housekeeping-h5-web` = `PASS`
3. `housekeeping-pc-web` = `PASS`

#### 正式来源应用真实提交验证

本轮新增正式来源应用样例：

1. App
   - 订单号：`REG-AUTO-APP-20260805-001`
   - 预付单：`PRE1785918393974`
   - 支付单：`PAY1785918393972`
   - 提交结果：成功进入 `支付中`
   - 支付详情结果：`WAIT_CALLBACK`
   - 成功回调收口后状态：`SUCCESS`
2. H5
   - 订单号：`REG-AUTO-H5-20260805-001`
   - 预付单：`PRE1785918388806`
   - 支付单：`PAY1785918388803`
   - 提交结果：成功进入 `支付中`
   - 支付详情结果：`WAIT_CALLBACK`
   - 路由轨迹：`RULE_HOME_WX | 家政 H5 微信优先 -> wx_h5`
   - 成功回调收口后状态：`SUCCESS`
3. PC
   - 订单号：`REG-AUTO-PC-20260805-001`
   - 预付单：`PRE1785918388717`
   - 支付单：`PAY1785918388713`
   - 提交结果：成功进入 `支付中`
   - 支付详情结果：`WAIT_CALLBACK`
   - 路由轨迹：`RULE_HOME_ALI | 家政 H5 支付宝兜底 -> alipay_h5`
   - 成功回调收口后状态：`SUCCESS`

### 8.4 本轮结论

1. `PAYMENT-1019` 已不再阻断正式来源应用支付提交。
2. H5 / PC 的“渠道不支持”问题已从路由别名层修复。
3. 正式来源应用已经恢复到可直接走“预付单 -> 提交支付 -> 待回调 -> 成功回调收口”主链路的状态。
4. 2026-08-05 17:15 至 17:16 已补正式来源应用防误操作验证：
   - 对 App / H5 / PC 三笔成功单重复发送 `SUCCESS` 回调，请求均保持原 `SUCCESS` 状态，未出现重复收口副作用。
   - 对 App / H5 / PC 三笔成功单执行关闭接口，请求均返回原 `SUCCESS` 状态，未被错误关闭。
5. 当前 `payment-core` 仍不建议直接合并 `master`，后续还需要继续补齐：
   - 正式来源应用 `SUCCESS` 页、`CLOSED` 页和异常页的人肉回归截图
   - 更多页面级人工截图证据
   - 异常单、关闭单、重复提交与回调幂等的页面级留痕

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

## 10. 2026-08-04 支付提交接入独立风控中心验证

### 10.1 本轮验证结论

本轮围绕“支付核心虽然已有本地控制策略，但提交支付前尚未真正调用独立风控系统”的问题进行了补强，确认当前已具备 `payment-core -> risk-control-system` 的准入联动基线。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| `payment-core` 全量后端测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml test` | 通过 | `262` 个测试全部通过 |
| `risk-control-system` 后端测试 | 同环境执行 `systems/risk-control-system/backend` 的 `mvn test` | 通过 | `4` 个测试全部通过 |
| 定向主链路验证 | `PaymentServiceImplTest.shouldMarkPaymentAsRiskReviewBeforeChannelSubmit` | 通过 | 支付提交命中风控复核时，不再进入渠道提交 |

### 10.2 本轮新增能力

1. `PaymentSubmitRequestDTO` 补齐 `clientDeviceId`、`payerPhone`，为独立风控评估提供准入上下文。
2. `payment-core` 新增 `PaymentRiskControlService`，支持在提交支付前调用风控中心评估接口。
3. 支付提交链路新增 `PASS / REVIEW / INTERCEPT / REJECT` 决策收口：
   - `PASS`：继续路由和渠道提交
   - `REVIEW`：支付单状态进入 `RISK_REVIEW`，收银台状态进入“待风控复核”
   - `INTERCEPT`：支付单状态进入 `RISK_BLOCKED`
   - `REJECT`：支付单状态进入 `RISK_REJECTED`
4. `risk-control-system` 新增 `/api/risk-control/decisions/evaluate`，支持黑名单、限额规则、基础高危策略和人工复核结果复用。

### 10.3 当前判断

1. 当前支付主链路已不再只是“文档上声明有风控”，而是提交支付前确实会先走独立风控决策。
2. 这仍属于 V1 联调基线，不等于已经具备生产级实时风控，因为规则发布、流式检测、消息告警、评分模型和大并发压测仍未补齐。
3. 后续应继续补 `ops-config-system` 配置下发生效、支付端页面态联动和真实运行环境下的跨系统 smoke。

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

## 99. 2026-08-02 payment-core DLQ intake 真实运行验证

### 99.1 验证背景

本轮目标是把 `payment.trade.dlq -> payment.compensation.dlq-intake -> t_payment_dead_letter_task` 这条死信补偿入账链路从“单元测试通过”推进到“真实 RabbitMQ + MySQL 运行验证通过”。

### 99.2 本轮环境事实

| 项目 | 结果 |
| --- | --- |
| payment-core 新进程 | `127.0.0.1:18080` 启动成功 |
| MySQL | `127.0.0.1:3306 / housekeeping_payment_core` 连接成功 |
| RabbitMQ | `127.0.0.1:5672` 连接成功 |
| 旧 payment-core 进程 | 发现一条监听 `8080` 且连接 `3307` 的旧进程，会抢占 intake 消息 |

### 99.3 本轮排查与修复

1. 初次投递新的演练 DLQ 消息后，`payment.compensation.dlq-intake` 队列被消费，但 `t_payment_dead_letter_task` 仍为 `0` 条。
2. 排查 RabbitMQ 消费者列表发现 intake 队列存在 `2` 个消费者。
3. 继续核对端口与进程后，确认旧 `payment-core` 进程监听 `8080`、连接旧库 `3307`，它与当前新进程同时消费 intake 队列。
4. 停掉旧 `payment-core` 进程后，intake 队列消费者收敛为当前新进程 `1` 个。
5. 重新投递新的演练 DLQ 消息后，补偿任务成功写入 `t_payment_dead_letter_task`。

结论：

1. 之前“消息被消费但未落库”的根因是旧进程抢消息，不是当前代码实现缺陷。
2. 当前新进程对应的死信补偿入账逻辑在真实环境下可用。

### 99.4 新增真实入账证据

本轮新增演练消息：

| message_id | routing_key | target_system | task_status | 结果 |
| --- | --- | --- | --- | --- |
| `DRILL-INTAKE-RETRY-20260802130829` | `payment.success.clearing.dlq.v1` | `clearing-system` | `PENDING_REVIEW` | 已写入补偿任务账本 |

本轮补录历史演练副本：

| message_id | routing_key | target_system | task_status | 说明 |
| --- | --- | --- | --- | --- |
| `DRILL-DLQ-TASK-20260801-001` | `payment.success.clearing.dlq.v1` | `clearing-system` | `PENDING_REVIEW` | 原始 DLQ 队列消息保留，仅补录账本 |
| `DRILL-DLQ-TASK-20260801-001` | `payment.success.accounting.dlq.v1` | `accounting-system` | `PENDING_REVIEW` | 原始 DLQ 队列消息保留，仅补录账本 |
| `DRILL-DLQ-REPLAY-20260801-001` | `payment.success.accounting.dlq.v1` | `accounting-system` | `PENDING_REVIEW` | 原始 DLQ 队列消息保留，仅补录账本 |

截至本轮核对，`t_payment_dead_letter_task` 共存在 `4` 条演练补偿任务记录。

### 99.5 本轮门禁结论

1. `payment-core` 已完成 DLQ intake 到补偿任务账本的真实运行验证。
2. 当前已关闭“没有补偿账本”和“intake 未验证”的门禁缺口。
3. 下一步还需继续完成：
   - 人工复核
   - 定向重放
   - 下游事实核对
   - 人工结案或重放成功结案

### 99.6 2026-08-02 首条真实重放闭环

本轮继续选择账务侧合法 JSON 死信任务 `DLQ7d62fb24b6be4c508db6` 做真实接口验证。

验证动作：

1. `POST /api/payment-dead-letter-tasks/DLQ7d62fb24b6be4c508db6/ready-to-replay`
2. `POST /api/payment-dead-letter-tasks/DLQ7d62fb24b6be4c508db6/replay`

关键结果：

1. 任务状态从 `PENDING_REVIEW` 更新为 `READY_TO_REPLAY`。
2. 随后任务状态更新为 `REPLAYED`。
3. `replay_count = 1`
4. `last_replay_at = 2026-08-02 13:16:30`
5. 对应业务编号 `PAY-REPLAY-DRILL-20260801-001` 在账务系统查询仍保持“已消费”，说明重放没有打破账务消费幂等。

结论：

1. `payment-core` 已具备“人工复核 -> 定向重放 -> 任务状态收口”的真实运行能力。
2. 至少在账务支付成功事件这一条样本上，补偿任务重放不会打坏下游已消费事实。
3. 后续仍需继续核对 clearing / accounting 其余历史演练任务，并补最终结案说明。

### 99.7 2026-08-02 历史演练任务全量收口

本轮继续处理剩余 3 条历史演练任务，处理策略如下：

1. 对合法 JSON 报文的 clearing 死信任务执行“复核 -> 重放”
2. 对非法演练报文执行“保留原始 DLQ 审计副本，不重放，人工结案”

处理结果：

| taskNo | messageId | routingKey | 最终状态 | 处理说明 |
| --- | --- | --- | --- | --- |
| `DLQ51febd7de1a446e4ac4b` | `DRILL-INTAKE-RETRY-20260802130829` | `payment.success.clearing.dlq.v1` | `REPLAYED` | 已完成 clearing 演练死信复核与重放，`replay_count = 1` |
| `DLQ7d62fb24b6be4c508db6` | `DRILL-DLQ-REPLAY-20260801-001` | `payment.success.accounting.dlq.v1` | `REPLAYED` | 已完成 accounting 演练死信复核与重放，`replay_count = 1` |
| `DLQ7afb962a1c0642d483ae` | `DRILL-DLQ-TASK-20260801-001` | `payment.success.accounting.dlq.v1` | `MANUAL_RESOLVED` | 非法演练报文，仅保留原始 DLQ 审计副本，人工结案 |
| `DLQd60e97458069401b8a5b` | `DRILL-DLQ-TASK-20260801-001` | `payment.success.clearing.dlq.v1` | `MANUAL_RESOLVED` | 非法演练报文，仅保留原始 DLQ 审计副本，人工结案 |

补充核对：

1. `PAY-REPLAY-DRILL-20260801-001` 在账务系统查询仍为“已消费”，说明账务重放未破坏幂等。
2. `PAY-INTAKE-DRILL-20260802-002` 在清分系统查询已生成 `PAYMENT_SUCCESS` 消费事件，说明 clearing 重放已生效。
3. `payment-core` 补偿任务列表接口与数据库状态一致，4 条演练任务均已收口。

结论：

1. 当前已经完成一轮“演练死信台账建立 -> 可重放任务真实重放 -> 非法报文人工结案”的全量样本收口。
2. 这意味着 `payment-core` 在补偿任务管理维度已经不再停留在概念设计或单元测试阶段，而是具备真实运行证据。

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

## 87. 2026-07-29 支付处理日志导出与全量回归刷新验证

### 87.1 本轮验证范围

本轮围绕“支付处理日志页面已经有导出按钮，但后端并没有真实 `payment-logs/export` 支撑，且全量回归基线仍停留在旧测试数量”这两个问题进行补齐，目标是同时收口日志导出闭环与最新后端回归基线。

### 87.2 本轮新增内容

1. `PaymentLogService` 新增导出接口。
2. `PaymentLogServiceImpl` 新增 CSV 导出逻辑，和支付请求、支付事件导出保持一致。
3. `PaymentLogController` 新增 `/api/payment-logs/export`。
4. `PaymentLogMapper` / `PaymentLogMapper.xml` 新增 `findAllForExport`。
5. `PaymentLogServiceImplTest` 新增导出场景，覆盖字段标准化和消息内容转义。

### 87.3 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端全量测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml test` | 通过 | `174` 个用例全部通过，`0` failures，`0` errors，`0` skipped |

### 87.4 当前判断

1. 支付处理日志已经从“可查询”升级为“可查询 + 可导出”，前端导出按钮与后端导出接口正式闭环。
2. 当前 `payment-core` 后端自动化门禁已刷新到 `174` 个测试通过，测试基线继续前移。
3. 但该结果仍不等同于支付系统整包已可发布，前端跨端统一回归、整包多系统统一回归、MQ 级可靠投递和回滚演练仍需继续补齐。

## 88. 2026-07-29 支付导出控制器层门禁补齐验证

### 88.1 本轮验证范围

本轮围绕“支付事件导出和支付处理日志导出虽然已经有服务层单测，但控制器层缺少显式门禁，无法证明 `/export` 接口头信息、编码和参数组装没有被后续改坏”的问题进行补强。

### 88.2 本轮新增内容

1. 新增 `PaymentLogControllerTest`，覆盖 `payment-logs/export` 的响应头、字节内容和列表调用入口。
2. 新增 `PaymentEventControllerTest`，覆盖 `payment-events/export` 的响应头、字节内容和列表调用入口。
3. 接口文档补充 `payment-events/export` 的显式说明，避免文档只写查询和重发、遗漏导出能力。

### 88.3 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentLogServiceImplTest,PaymentLogControllerTest,PaymentEventControllerTest,PaymentEventServiceImplTest test` | 通过 | `10` 个用例全部通过，覆盖日志/事件导出服务层与控制器层闭环 |

### 88.4 当前判断

1. `payment-core` 的导出能力已经从“服务层可用”提升为“控制器层也有显式门禁”，更接近冻结版交付包要求。
2. 这一步提升的是接口交付可信度，不等于整包已达到 `master / release` 门槛。

## 89. 2026-07-29 服务者结算单导出闭环验证

### 89.1 本轮验证范围

本轮围绕“服务者结算单页面已经有导出按钮，但后端没有 `/api/settlements/workers/export`，导致查询页与接口文档口径不一致”的问题进行补齐。

### 89.2 本轮新增内容

1. `SettlementService` 新增导出接口。
2. `SettlementServiceImpl` 新增 CSV 导出逻辑，输出服务者结算单快照。
3. `SettlementMapper` / `SettlementMapper.xml` 新增 `findWorkerSettlementsForExport`。
4. `SettlementController` 新增 `/api/settlements/workers/export`。
5. 新增 `SettlementServiceImplTest` 导出场景和 `SettlementControllerTest` 控制器导出场景。

### 89.3 当前判断

1. 服务者结算单已从“只可查询”升级为“可查询 + 可导出”，和前端导出按钮正式闭环。
2. 这一步提升的是 `payment-core` 轻量结算查询入口的完整性，不等于完整 `settlement-system` 已交付。

## 90. 2026-07-29 支付单列表与导出闭环验证

### 90.1 本轮验证范围

本轮围绕“后台支付单页面已有列表和导出入口，但后端还缺少 `/api/payments/export`”的问题进行补齐。

### 90.2 本轮新增内容

1. `PaymentService` 新增导出接口。
2. `PaymentServiceImpl` 新增支付单 CSV 导出逻辑。
3. `PaymentController` 新增 `/api/payments/export`。
4. `PaymentServiceImplTest` 新增导出场景。
5. 新增 `PaymentControllerTest`，覆盖导出响应头、字节内容和列表调用入口。

### 90.3 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f /Users/abc123/workspace/home-service-payment-system/systems/payment-core/backend/pom.xml -Dtest=PaymentServiceImplTest,PaymentControllerTest test` | 通过 | `26` 个用例全部通过，`0` failures，`0` errors，`0` skipped |

### 90.4 当前判断

1. 支付单管理现在已从“可查询”升级为“可查询 + 可导出”，和前端按钮形成闭环。
2. 这一步继续抬高了 `payment-core` 的冻结版交付完整度，但仍不等于整包已可直接发布。

## 91. 2026-07-31 账单中心导出闭环验证

### 91.1 本轮新增内容

1. `BillService` / `BillServiceImpl` 新增账单 CSV 导出能力，复用列表筛选和排序规则，但不受分页限制。
2. `BillMapper` / `BillMapper.xml` 新增 `findAllForExport`，输出账单号、订单号、客户、应收/已付/待付金额、状态和时间字段。
3. `BillController` 新增 `GET /api/bills/export`，以 UTF-8 BOM 返回 `bills.csv`。
4. 新增 `BillControllerTest`，并扩展 `BillServiceImplTest`，覆盖下载响应、条件标准化和 CSV 双引号转义。
5. 接口文档同步了账单导出路径、筛选口径和编码约定；后台账单页原有导出按钮已直接复用该路径，无需改动前端交互。

### 91.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f /Users/abc123/workspace/home-service-payment-system/systems/payment-core/backend/pom.xml -Dtest=BillServiceImplTest,BillControllerTest test` | 通过 | `4` 个用例全部通过，`0` failures，`0` errors，`0` skipped |
| 后端全量测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f /Users/abc123/workspace/home-service-payment-system/systems/payment-core/backend/pom.xml test` | 通过 | `187` 个用例全部通过，`0` failures，`0` errors，`0` skipped |
| 后台前端生产构建 | `npm run build`（`systems/payment-core/frontend/admin-web`） | 通过 | Vite 生产构建完成 |

### 91.3 当前判断

1. 账单中心已从“页面存在导出按钮、后端无落点”收口为前后端可用的查询加导出闭环。
2. 后端全量回归已刷新到 `187` 个测试通过；支付核心全量跨端验证和跨系统接口联调仍未完成。
3. `payment-core` 仍未达到 `test -> master` 或 `master / release` 冻结门槛，当前不创建 release 文档或版本。

## 92. 2026-07-31 收银台会话导出闭环验证

### 92.1 本轮新增内容

1. `CashierSessionService` / `CashierSessionServiceImpl` 新增会话 CSV 导出能力，复用列表筛选与排序规则，但不受分页限制。
2. `CashierSessionMapper` / `CashierSessionMapper.xml` 新增 `findAllForExport`，输出会话、预付单、支付单、订单、账单、客户、金额、终端和失效状态等运营排查字段。
3. `CashierSessionController` 新增 `GET /api/cashier-sessions/export`，以 UTF-8 BOM 返回 `cashier-sessions.csv`。
4. 新增 `CashierSessionControllerTest`，并扩展 `CashierSessionServiceImplTest`，覆盖下载响应、条件标准化和 CSV 双引号转义。
5. 后台会话页原有导出按钮已调用该路径，接口文档同步了导出筛选口径与编码约定。

### 92.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 两模块定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f /Users/abc123/workspace/home-service-payment-system/systems/payment-core/backend/pom.xml -Dtest=BillServiceImplTest,BillControllerTest,CashierSessionServiceImplTest,CashierSessionControllerTest test` | 通过 | `8` 个用例全部通过，`0` failures，`0` errors，`0` skipped |
| 后端全量测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f /Users/abc123/workspace/home-service-payment-system/systems/payment-core/backend/pom.xml test` | 通过 | `190` 个用例全部通过，`0` failures，`0` errors，`0` skipped |

### 92.3 当前判断

1. 收银台会话已从“前端可发起下载、后端没有出口”收口为前后端可用的查询加导出闭环。
2. 后端全量回归已刷新到 `190` 个测试通过；支付核心全量跨端验证和跨系统接口联调仍是后续门槛。
3. `payment-core` 仍未达到 `test -> master` 或 `master / release` 冻结门槛，当前不创建 release 文档或版本。

## 93. 2026-07-31 支付流水导出闭环验证

### 93.1 本轮新增内容

1. `PaymentFlowService` / `PaymentFlowServiceImpl` 新增支付流水 CSV 导出能力，沿用现有列表筛选与排序口径，并直接复用统一流水排障台的 union SQL。
2. `PaymentFlowController` 新增 `GET /api/payment-flows/export`，以 UTF-8 BOM 返回 `payment-flows.csv`。
3. 扩展 `PaymentFlowServiceImplTest`，覆盖条件标准化、导出字段完整性和 CSV 双引号转义。
4. 新增 `PaymentFlowControllerTest`，覆盖导出下载响应和列表入口调用。
5. 接口文档已同步支付流水导出路径、union SQL 口径、导出字段和 CSV 编码约定；后台支付流水页原有导出按钮可直接闭环复用。

### 93.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付流水定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dtest=PaymentFlowServiceImplTest,PaymentFlowControllerTest test` | 通过 | `4` 个用例全部通过，覆盖导出服务层和控制器层闭环 |
| 后端全量测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn test` | 通过 | `196` 个用例全部通过，覆盖支付核心既有链路与新增导出控制器/服务层门禁 |

### 93.3 当前判断

1. 支付流水页已从“前端已有导出入口、后端缺下载能力”收口为前后端可用的查询加导出闭环。
2. 后端全量测试已刷新到 `196` 个用例通过，新增导出闭环未破坏既有支付核心链路。
3. 在支付核心跨端验证、跨系统接口联调和更完整业务闭环完成前，`payment-core` 仍未达到 `test -> master` 或 `master / release` 冻结门槛，本轮先不创建 release 文档或版本。

## 94. 2026-07-31 退款单导出闭环验证

### 94.1 本轮新增内容

1. `RefundService` / `RefundServiceImpl` 新增退款单 CSV 导出能力，复用现有列表筛选口径与 `normalizeQuery(...)`。
2. `RefundMapper` / `RefundMapper.xml` 新增 `findAllForExport`，继续保持 `ORDER BY applied_at DESC`。
3. `RefundController` 新增 `GET /api/refunds/export`，以 UTF-8 BOM 返回 `refunds.csv`。
4. 扩展 `RefundServiceImplTest`，覆盖筛选条件归一化、状态类型导出和 CSV 双引号转义。
5. 新增 `RefundControllerTest`，覆盖导出下载响应和列表入口调用。
6. 接口文档已同步退款导出路径、筛选口径、排序约束和 CSV 编码约定；后台退款页原有导出按钮可直接闭环复用。

### 94.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 退款导出定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dtest=RefundServiceImplTest,RefundControllerTest test` | 通过 | `10` 个用例全部通过，覆盖退款列表、导出、申请、审核、回调、重试与详情链路 |
| 后端全量测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn test` | 通过 | `196` 个用例全部通过，覆盖支付核心既有链路与新增退款导出门禁 |

### 94.3 当前判断

1. 退款单页已从“前端已有导出入口、后端缺下载能力”收口为前后端可用的查询加导出闭环。
2. 截至 2026-07-31，`payment-core` 四个后台导出模块的真实代码闭环已全部落地，且后端全量测试已刷新到 `196` 个用例通过。
3. 在导出门禁证据、跨端联调和更完整业务闭环完成前，`payment-core` 仍未达到 `test -> master` 或 `master / release` 冻结门槛，本轮先不创建 release 文档或版本。

## 95. 2026-07-31 admin 导出下载链路验证

本轮继续补齐 `payment-core` admin 端导出门禁证据，目标不是再补后端代码，而是确认真实页面点击后，四个导出入口都能生成正确的导出 URL，并收到带 `attachment` 响应头的 CSV 下载响应。

### 95.1 本轮动作

1. 先将原始 `housekeeping_payment_core` 库安全克隆到临时副本，确认旧运行库副本可启动最新 backend，但任务中心最初会因缺少 `t_payment_task_lease` 报表缺失错误。
2. 使用 `systems/payment-core/database/migrations/2026-07-31-add-payment-task-lease.sql` 给原始运行库补齐 `t_payment_task_lease`，并验证增量 DDL 已落库。
3. 再使用 `backend/src/main/resources/schema.sql + data.sql` 重建最新临时库 `housekeeping_payment_core_latest_verify_20260731`，保证最新 backend 所需表结构完整。
4. 启动本地 `admin-web`（`127.0.0.1:5174`），并通过本地代理 `127.0.0.1:8080 -> 真实 payment-core backend(127.0.0.1:18080)` 保留页面真实路由、真实筛选默认值和真实导出按钮逻辑。
5. 通过系统 Chrome 的 CDP 打开真实页面，点击 `账单中心 / 收银台会话 / 退款单 / 支付流水` 四个页面的导出按钮。
6. 抓取页面点击后实际执行的 `window.open(...)` 导出 URL，并继续校验真实 backend 返回的 `Content-Disposition` 与 CSV 表头。
7. 追加修复 `PaymentTaskCenterServiceImpl` 的升级告警递归膨胀问题：为升级 outbox 回写 `source_alert_no`，排除历史递归升级候选，并在写库前截断 `alert_content` 到 `512` 字符；随后以原始运行库重新启动最新 backend 做真实调度验证。

### 95.2 验证结果

| 页面 | 按钮 | 点击生成的导出 URL | 响应头 | CSV 首行 |
| --- | --- | --- | --- | --- |
| `/bills` | `导出账单` | `/api/bills/export?billNo=&orderNo=&customerName=&billStatus=%E5%85%A8%E9%83%A8&sortField=createdAt&sortOrder=desc` | `attachment; filename=bills.csv` | `账单号,订单号,客户,账单应收,已支付,待支付,账单状态` |
| `/cashier-sessions` | `导出会话` | `/api/cashier-sessions/export?sessionNo=&paymentOrderId=&orderNo=&customerName=&terminal=%E5%85%A8%E9%83%A8&sessionStatus=%E5%85%A8%E9%83%A8&sortField=createdAt&sortOrder=desc` | `attachment; filename=cashier-sessions.csv` | `会话号,预付单号,支付单号,订单号,客户,终端,会话金额,会话状态` |
| `/refunds` | `导出退款单` | `/api/refunds/export?refundOrderId=&paymentOrderId=&refundStatus=%E5%85%A8%E9%83%A8&refundMethod=%E5%85%A8%E9%83%A8` | `attachment; filename=refunds.csv` | `退款单号,支付单号,退款金额,退款方式,退款状态` |
| `/payment-flows` | `导出流水` | `/api/payment-flows/export?paymentOrderId=&orderNo=&flowType=%E5%85%A8%E9%83%A8&channelCode=&terminal=%E5%85%A8%E9%83%A8&businessStatus=%E5%85%A8%E9%83%A8&keyword=&sortField=createdAt&sortOrder=desc` | `attachment; filename=payment-flows.csv` | `流水号,支付单号,订单号,流水类型,渠道,终端,业务状态` |

### 95.3 结论

1. 四个 admin 页面已完成真实页面渲染、真实按钮点击、真实导出 URL 拼接与真实 backend CSV 下载响应链路验证。
2. 这份证据说明 admin 前端导出入口、代理链路和真实 backend 下载约定本身已不再是阻断项，`payment-core` 四块导出在联调下载层面已经闭环。
3. 原始 `housekeeping_payment_core` 运行库补齐 `t_payment_task_lease` 后，最新 backend 已能在 `2026-07-31 19:36:42` 成功写出 `PAYMENT_ISSUE_ESCALATE` 任务日志；此前暴露的 `alert_content` 越界问题已通过“候选过滤 + `source_alert_no` 落库 + 512 字符截断”修复，不再阻塞真实运行库启动。
4. 随后补充并执行 `systems/payment-core/database/migrations/2026-07-31-ack-legacy-recursive-issue-alerts.sql`，将原始运行库中 `7` 条历史递归升级脏数据从 `待确认` 收口为 `已确认`，并保留 `ack_operator=data-fix-20260731`、`triggered_by=payment-core-alert-cleanup-2026-07-31` 审计痕迹。
5. 至此，原始运行库上与 `PAYMENT_ISSUE_ESCALATE` 相关的已知结构/历史数据阻断已全部收口；当前剩余阻断已经不再是“导出下载证据不足”或“原始运行库缺关键表”，而是更高层的最终 gate 审计闭环，包括 MQ 级可靠投递、失败重试/死信补偿、正式发布清单与发布后验证清单。

## 96. 2026-07-31 支付事件重投递与死信门禁补强

### 96.1 本轮新增内容

1. 更新 `PaymentEventServiceImplTest`，不再只验证旧的 `markRepublished` 兼容口径，而是显式校验后台“重新投递”动作会调用 `PaymentEventDispatchService.republish(...)` 的真实下游重投递语义。
2. 为 `PaymentEventServiceImplTest` 增加“下游重投递返回 `false` 时直接拒绝，并返回 `支付事件不存在`”场景，避免把无效事件误当成重投递成功。
3. 扩展 `PaymentEventDispatchServiceImplTest`，补齐三类可靠投递边界：
   - 非 `PAYMENT_SUCCESS` 事件禁止进入支付成功下游重投递；
   - 支付详情缺失时直接标记 `FAILED`，且不触发任何下游请求；
   - 清分下游非 2xx 时立即停止，不再继续向账务侧投递。
4. 结合既有用例，当前支付事件出站测试已同时覆盖“成功投递 / 失败重试 / 重试耗尽进 `DEAD_LETTER` / 非法事件拒绝 / 缺详情失败 / 下游短路”六类核心边界。

### 96.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付事件定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentEventServiceImplTest,PaymentEventDispatchServiceImplTest test` | 通过 | `13` 个用例全部通过，其中 `PaymentEventServiceImplTest 5/5`、`PaymentEventDispatchServiceImplTest 8/8` |

### 96.3 当前判断

1. `payment-core` 已经把支付事件出站的“人工重投递入口语义、失败重试、重试耗尽进死信、无效事件拒绝、下游短路保护”补成自动化门禁，而不再只是界面能力或读代码推断。
2. 这一步收缩了“MQ 级可靠投递/重试/死信补偿”中的应用层证据缺口，但仍不等同于真实 MQ 中间件生产级验证，因为当前仍主要基于本地 HTTP 下游与单测门禁，而非真实消息代理、死信队列、消费组和补偿编排演练。
3. 因此本轮依旧不推进 `test -> master`，也不基于 `master` 产出 `release/*` 文档或版本。

## 97. 2026-07-31 四块后台导出回归复核

本轮不重复实现导出功能，复核 `bills`、`cashier-sessions`、`refunds`、`payment-flows` 四块已交付导出的服务层和控制器层自动化门禁。

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 四块导出定向回归 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=BillServiceImplTest,BillControllerTest,CashierSessionServiceImplTest,CashierSessionControllerTest,RefundServiceImplTest,RefundControllerTest,PaymentFlowServiceImplTest,PaymentFlowControllerTest test` | 通过 | `22` 个用例通过，`0` failures，`0` errors，`0` skipped |

### 97.1 当前判断

1. 四个导出域的查询条件下推、CSV 字段与转义、UTF-8 下载响应、`attachment` 文件名均有服务层和控制器层回归保护。
2. 结合第 95 节真实页面下载证据，四块导出已不再是 `test -> master` 阻断项。
3. 当前仍不推进合并或创建正式 release，原因是缺少真实消息代理上的可靠投递、重试、死信与跨系统补偿演练证据。

## 98. 2026-08-02 支付请求、日终、任务中心控制器门面补证

本轮针对 `payment-core` 已经产品化落地、但此前缺少独立控制器测试覆盖的三块后台模块补了接口门面回归：

1. 支付请求管理 `PaymentRequestController`
2. 支付日终处理 `PaymentDayEndController`
3. 支付任务中心 `PaymentTaskCenterController`

### 98.1 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 三块控制器定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -Dtest=PaymentRequestControllerTest,PaymentDayEndControllerTest,PaymentTaskCenterControllerTest test` | 通过 | `7` 个用例通过，`0` failures，`0` errors，`0` skipped |

### 98.2 本轮补强点

1. `PaymentRequestControllerTest` 覆盖支付请求列表查询参数绑定和 CSV 导出响应头。
2. `PaymentDayEndControllerTest` 覆盖支付日终总览和手工跑批请求透传。
3. `PaymentTaskCenterControllerTest` 覆盖任务中心总览、任务日志查询，以及超时关单、事件重发、退款重试、SLA 升级、告警派发、回执回查、控制策略巡检七类人工任务入口。

### 98.3 当前判断

1. `支付请求管理 / 日终处理 / 任务中心` 三块高频运营后台入口已补齐控制器层回归保护。
2. 这一步进一步缩小了 `payment-core` 从“页面和服务已落地”到“接口门面可回归”的证据缺口。
3. 当前仍不推进 `test -> master`，因为整包仍缺真实消息代理可靠投递、跨系统补偿演练和正式发布冻结包。

## 99. 2026-08-02 payment-core test 分支最新快照

### 99.1 本轮复核结果

本轮针对 `test` 分支上的 `payment-core` 进行了最新一轮全量后端回归和后台前端构建复核，目的是确认新增控制器门面测试后，整包主线没有被破坏。

### 99.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端全量测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -q test` | 通过 | `220` 个用例通过，`0` failures，`0` errors，`0` skipped |
| `admin-web` 前端构建 | `cd systems/payment-core/frontend/admin-web && npm run build` | 通过 | 最新生产构建通过，输出 `dist/assets/index-BBDWZWVh.js` |
| `app-web` 前端构建 | `cd systems/payment-core/frontend/app-web && npm run build` | 通过 | 最新生产构建通过，输出 `dist/assets/index-DsCUPAzE.js` |
| `h5-web` 前端构建 | `cd systems/payment-core/frontend/h5-web && npm run build` | 通过 | 最新生产构建通过，输出 `dist/assets/index-Du8sikH_.js` |
| `pc-web` 前端构建 | `cd systems/payment-core/frontend/pc-web && npm run build` | 通过 | 最新生产构建通过，输出 `dist/assets/index-BDOG789Z.js` |

### 99.3 当前意义

1. `payment-core` 最新 test 分支状态已经不只是“局部模块可用”，而是具备了一版较完整的回归证据快照。
2. 新增控制器测试后，`payment-core` 的后端全量测试总数已提升到 `220`，说明回归保护范围继续扩大。
3. `admin-web / app-web / h5-web / pc-web` 四端当前都能在最新 `test` 分支上独立完成生产构建，说明多端前端矩阵没有被近期补偿和门面测试改动破坏。
4. 当前仍不进入 `master` 或 `release/*`，因为阻断点已经进一步收敛到生产级可靠投递、跨系统补偿、整包正式发布清单和发布后验证项。

## 100. 2026-08-03 支付事件出站总览补强验证

### 100.1 本轮补强点

1. 新增 `GET /api/payment-events/overview`，在列表筛选条件不变的前提下返回事件总数、成功/发布中/失败/死信分布、涉及下游系统数、到期待重试事件数、支付成功事件数和最近发布时间。
2. `PaymentEventsView` 已接入总览指标、风险卡片和联查入口，避免页面统计仅依赖当前页数据。
3. `PaymentEventMapper.xml` 已统一列表、总览、导出三类查询口径，降低运营页统计与导出不一致的风险。

### 100.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付事件定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -q -Dtest=PaymentEventServiceImplTest,PaymentEventControllerTest test` | 通过 | 支付事件服务层和控制器层总览能力均通过 |

## 101. 2026-08-07 订单快照与异常告警快照导出复核

### 101.1 本轮补强点

1. 新增订单快照导出能力，支持从订单中心导出当前查询条件下的订单留痕。
2. 新增异常告警快照导出能力，支持从异常告警中心导出当前查询条件下的告警留痕。
3. 两个导出能力均沿用现有查询口径，不改变列表筛选语义。

### 101.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端全量测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -q -f systems/payment-core/backend/pom.xml test` | 通过 | `220` 个用例通过，`0` failures，`0` errors，`0` skipped |

### 101.3 当前判断

1. `payment-core` 最新一轮仍保持全量测试通过。
2. 本轮新增的是交付留痕能力，不是足以推动 `test -> master` 的冻结级能力。
3. 当前仍不创建新的 `release/*`，也不产出正式发布文档。
| `admin-web` 前端构建 | `cd systems/payment-core/frontend/admin-web && npm run build` | 通过 | 最新生产构建通过，页面总览与联查入口已接入 |
| 提交前格式检查 | `git diff --check` | 通过 | 本轮改动无空白符和补丁格式问题 |

### 100.3 当前判断

1. `payment-core` 的支付事件出站页已从“基础列表 + 手动重发”升级为“总览 + 风险面判断 + 列表下钻”的正式化运营台。
2. 这一步继续缩小了 `payment-core` 在支付交易闭环中后台上的产品化差距，但仍不代表真实消息总线、订阅确认和跨系统补偿编排已经生产化。
3. 因此本轮仍只提交到 `test`，不推进 `master` 合并或 `release/*` 冻结。

## 101. 2026-08-03 服务者结算总览补强验证

### 101.1 本轮补强点

1. 新增 `GET /api/settlements/workers/overview`，在列表筛选条件不变的前提下返回结算单总数、待审核/待出款/出款中/出款成功分布、实结金额合计、扣减金额合计、保证金影响合计和净额为负结算单数。
2. `WorkerSettlementsView` 已接入总览指标和风险卡片，避免后台卡片继续基于当前页临时统计。
3. `SettlementMapper.xml` 已统一列表、总览、导出三类查询口径，降低页面与导出口径不一致的风险。

### 101.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 服务者结算定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -q -Dtest=SettlementServiceImplTest,SettlementControllerTest test` | 通过 | 服务者结算服务层和控制器层总览能力均通过 |
| `admin-web` 前端构建 | `cd systems/payment-core/frontend/admin-web && npm run build` | 通过 | 最新生产构建通过，页面总览与风险卡片已接入 |
| 提交前格式检查 | `git diff --check` | 通过 | 本轮改动无空白符和补丁格式问题 |

### 101.3 当前判断

1. `payment-core` 的服务者结算页已从“基础查询 + 导出”升级为“总览 + 风险面判断 + 列表下钻”的正式化支付侧核对台。
2. 这一步继续缩小了支付核心域对结算侧可视化与风险识别的产品化差距，但仍不代表完整审批、出款、核销和失败重打作业已经在 `payment-core` 生产化。
3. 因此本轮仍只提交到 `test`，不推进 `master` 合并或 `release/*` 冻结。

## 102. 2026-08-03 收银台会话总览补强验证

### 102.1 本轮补强点

1. 新增 `GET /api/cashier-sessions/overview`，在列表筛选条件不变的前提下返回会话总数、已失效/成功/支付中/待支付会话分布、涉及终端数、15 分钟内即将失效会话数和会话金额合计。
2. `CashierSessionsView` 已接入总览指标、风险卡片和联查入口，避免后台卡片继续基于当前页临时统计。
3. `CashierSessionMapper.xml` 已统一列表、总览、导出三类查询口径，降低页面统计与导出口径不一致的风险。

### 102.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 收银台会话定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -q -Dtest=CashierSessionServiceImplTest,CashierSessionControllerTest test` | 通过 | 收银台会话服务层和控制器层总览能力均通过 |
| `admin-web` 前端构建 | `cd systems/payment-core/frontend/admin-web && npm run build` | 通过 | 最新生产构建通过，页面总览与联查入口已接入 |
| 提交前格式检查 | `git diff --check` | 通过 | 本轮改动无空白符和补丁格式问题 |

### 102.3 当前判断

1. `payment-core` 的收银台会话页已从“基础查询 + 导出”升级为“总览 + 风险面判断 + 列表下钻”的正式化运营台。
2. 这一步继续缩小了支付核心域在终端会话排障和用户跳失识别上的产品化差距，但仍不代表多端收银台的真实终端追踪、埋点分析和自动补偿已生产化。
3. 因此本轮仍只提交到 `test`，不推进 `master` 合并或 `release/*` 冻结。

## 103. 2026-08-03 账单中心总览补强验证

### 103.1 本轮补强点

1. 新增 `GET /api/bills/overview`，在列表筛选条件不变的前提下返回账单总数、已支付/待支付/部分支付/逾期账单数，以及应收金额、已付金额、待付金额合计。
2. `BillsView` 已接入总览指标和风险卡片，避免后台卡片继续基于当前页临时统计。
3. `BillMapper.xml` 已统一列表、总览、导出三类查询口径，降低页面统计与导出口径不一致的风险。

### 103.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 账单中心定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -q -Dtest=BillServiceImplTest,BillControllerTest test` | 通过 | 账单中心服务层和控制器层总览能力均通过 |
| `admin-web` 前端构建 | `cd systems/payment-core/frontend/admin-web && npm run build` | 通过 | 最新生产构建通过，页面总览与风险卡片已接入 |
| 提交前格式检查 | `git diff --check` | 通过 | 本轮改动无空白符和补丁格式问题 |

### 103.3 当前判断

1. `payment-core` 的账单中心已从“基础查询 + 导出”升级为“总览 + 风险面判断 + 列表下钻”的正式化运营台。
2. 这一步继续缩小了支付核心域在账单收口、逾期识别和待收风险可视化上的产品化差距，但仍不代表财务会计口径、会计分录和正式对账链路已经生产化。
3. 因此本轮仍只提交到 `test`，不推进 `master` 合并或 `release/*` 冻结。

## 104. 2026-08-03 支付路由执行结果总览补强验证

### 104.1 本轮补强点

1. 新增 `GET /api/payment-routes/overview`，在列表筛选条件不变的前提下返回路由记录总数、成功命中数、需关注命中数、命中渠道数、线下路由数、微信路由数、支付宝路由数和最近路由时间。
2. `PaymentRoutesView` 已接入总览指标与风险卡片，避免后台卡片继续基于当前页临时统计。
3. `PaymentRouteExecutionMapper.xml` 已统一列表与总览两类查询口径，降低页面统计与列表筛选不一致的风险。

### 104.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付路由执行结果定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -q -Dtest=PaymentRouteExecutionServiceImplTest,PaymentRouteExecutionControllerTest test` | 通过 | 支付路由执行结果服务层和控制器层总览能力均通过 |
| `admin-web` 前端构建 | `cd systems/payment-core/frontend/admin-web && npm run build` | 通过 | 最新生产构建通过，页面总览卡片、风险总览和联查入口可稳定打包 |
| 提交前格式检查 | `git diff --check` | 通过 | 本轮改动无空白符和补丁格式问题 |

### 104.3 当前判断

1. `payment-core` 的支付路由执行结果页正从“基础查询 + 展开报文”升级为“总览 + 风险面判断 + 列表下钻”的正式化运营台。
2. 这一步继续缩小了支付核心域在路由误命中、渠道落点偏差和线下兜底识别上的产品化差距，但仍不代表实时渠道健康评分、自动熔断和策略回放已经生产化。
3. 因此本轮仍只面向 `test` 分支收口，不推进 `master` 合并或 `release/*` 冻结。

## 105. 2026-08-03 支付配置中心治理总览与分区导出补强验证

### 105.1 本轮补强点

1. `PaymentConfigView` 已新增治理总览卡片与风险总览区，统一展示启用渠道、启用路由、启用协议、控制策略 WARN/FAIL、返回码归档和通知供应商覆盖情况。
2. 配置中心各治理分区已补齐独立导出入口，支持渠道、路由、协议、返回码、网关、控制策略、告警供应商和值班路由按分区输出 CSV 快照。
3. `PaymentConfigControllerTest` 已补齐协议新增/编辑、值班路由新增/编辑、各类启停接口和控制策略自检的控制器层门禁用例，避免配置中心能力退化时只剩服务层保护。

### 105.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付配置中心定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -q -Dtest=PaymentConfigControllerTest test` | 通过 | 支付配置中心控制器层新增门禁用例全部通过，覆盖协议新增/编辑、值班路由新增/编辑、启停接口和控制策略自检 |
| `admin-web` 前端构建 | `cd systems/payment-core/frontend/admin-web && npm run build` | 通过 | 最新生产构建通过，治理总览卡片与分区导出入口可稳定打包 |
| 提交前格式检查 | `git diff --check` | 通过 | 本轮改动无空白符和补丁格式问题 |

### 105.3 当前判断

1. `payment-core` 的支付配置中心正从“治理台账可查看”升级为“治理风险可观察、交付快照可分区导出、控制器门禁更完整”的正式化配置治理台。
2. 这一步继续缩小了支付核心域在配置审计、发布留痕和跨角色协同评审上的产品化差距，但仍不代表真实密钥托管、证书上传流转和外部通知平台已生产化。
3. 因此本轮仍只面向 `test` 分支收口，不推进 `master` 合并或 `release/*` 冻结。

## 106. 2026-08-03 支付监控分析风险总览与控制器门禁补强验证

### 106.1 本轮补强点

1. `PaymentMonitorView` 已新增风险总览区，统一展示 `P1` 告警数、存在待处理的渠道数和高风险渠道数，避免首屏只能看摘要卡片和明细表。
2. 后台支付监控分析页继续保持“摘要卡片 + 风险总览 + 趋势 + 告警 + 渠道下钻”的一致结构，更贴近正式运营台交付标准。
3. 新增 `PaymentMonitorControllerTest`，补齐支付监控分析控制器层门禁，避免监控总览接口只依赖服务层测试保护。

### 106.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付监控分析定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -q -Dtest=PaymentMonitorServiceImplTest,PaymentMonitorControllerTest test` | 通过 | 支付监控分析服务层与控制器层门禁均通过，新增覆盖监控总览接口控制器转发 |
| `admin-web` 前端构建 | `cd systems/payment-core/frontend/admin-web && npm run build` | 通过 | 最新生产构建通过，风险总览区接入后页面可稳定打包 |
| 提交前格式检查 | `git diff --check` | 通过 | 本轮改动无空白符和补丁格式问题 |

### 106.3 当前判断

1. `payment-core` 的支付监控分析页正从“可查看摘要和明细”升级为“风险面先判断、再下钻处理”的正式化监控台。
2. 这一步继续缩小了支付核心域在趋势观察、渠道风险识别和异常优先级分层上的产品化差距，但仍不代表真实时序监控、外部告警平台和自动扩缩容联动已经生产化。
3. 因此本轮仍只面向 `test` 分支收口，不推进 `master` 合并或 `release/*` 冻结。

## 107. 2026-08-03 支付交易异常中心风险总览与控制器门禁补强验证

### 107.1 本轮补强点

1. `PaymentIssuesView` 已新增异常风险总览区，统一展示 `SLA` 超时异常数、`P1` 高优先级异常数以及责任组数量 / 异常总量，避免运营首屏只能看责任组卡片和明细表。
2. 后台支付交易异常中心继续保持“筛选 + 风险总览 + 责任组统计 + 异常列表 + 批量处理”的一致结构，更贴近正式异常治理台交付标准。
3. 新增 `PaymentIssueControllerTest`，补齐异常列表、责任组统计、告警明细、人工确认和批量处理入口的控制器层门禁。

### 107.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付交易异常中心定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -q -Dtest=PaymentIssueServiceImplTest,PaymentIssueControllerTest test` | 通过 | 支付交易异常中心服务层与控制器层门禁均通过，新增覆盖异常列表、责任组统计、告警明细、人工确认和批量处理入口 |
| `admin-web` 前端构建 | `cd systems/payment-core/frontend/admin-web && npm run build` | 通过 | 最新生产构建通过，异常风险总览区接入后页面可稳定打包 |
| 提交前格式检查 | `git diff --check` | 通过 | 本轮改动无空白符和补丁格式问题 |

### 107.3 当前判断

1. `payment-core` 的支付交易异常中心正从“支持处理动作的异常列表”升级为“风险面先判断、责任归口更清晰、处理入口更完整”的正式化异常治理台。
2. 这一步继续缩小了支付核心域在异常优先级判断、责任归口识别和人工处理门禁上的产品化差距，但仍不代表真实外部通知平台、法定节假日值班中心和更复杂升级编排已经生产化。
3. 因此本轮仍只面向 `test` 分支收口，不推进 `master` 合并或 `release/*` 冻结。

## 108. 2026-08-03 统一支付记录控制器门禁补强验证

### 108.1 本轮补强点

1. 新增 `PaymentRecordControllerTest`，覆盖统一、微信支付宝和银行卡记录共用的列表参数绑定入口。
2. 新增收款记录详情入口控制器测试，覆盖 `GET /api/payment-records/{paymentOrderId}` 的支付记录详情转发。
3. 本轮不新增统计口径，继续保持记录列表的分页统计与详情页的轨迹聚合边界，避免前端当前页数据冒充全量指标。

### 108.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 统一支付记录定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -q -Dtest=PaymentRecordServiceImplTest,PaymentRecordControllerTest test` | 通过 | 支付记录服务层与控制器层门禁均通过，覆盖列表参数绑定和详情转发 |
| `admin-web` 前端构建 | `cd systems/payment-core/frontend/admin-web && npm run build` | 通过 | 最新生产构建通过，记录列表和详情相关页面可稳定打包 |
| 提交前格式检查 | `git diff --check` | 通过 | 本轮改动无空白符和补丁格式问题 |

### 108.3 当前判断

1. `payment-core` 的统一支付记录入口已从“服务层有测试保护”升级为“列表和详情控制器入口均有门禁保护”。
2. 这一步继续增强了收款运营、支付单详情和支付记录详情之间的接口稳定性，但不代表收款记录已经具备完整财务对账、会计分录和退款核销能力。
3. 因此本轮仍只面向 `test` 分支收口，不推进 `master` 合并或 `release/*` 冻结。

## 109. 2026-08-03 工作台与支付健康指标控制器门禁补强验证

### 109.1 本轮补强点

1. 新增 `DashboardControllerTest`，覆盖工作台汇总接口的服务调用和响应数据透传。
2. 新增 `PaymentMetricsControllerTest`，覆盖支付健康指标汇总接口的服务调用和响应数据透传。
3. 前端工作台继续保持“工作台卡片 + 支付健康度 + 待办事项 + 快捷入口”的现有结构，本轮不改变展示口径，仅补后端控制器门禁证据。

### 109.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 工作台与支付指标定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -q -Dtest=DashboardControllerTest,PaymentMetricsControllerTest test` | 通过 | 两个总览控制器的服务转发和响应透传均通过门禁验证 |
| `admin-web` 前端构建 | `cd systems/payment-core/frontend/admin-web && npm run build` | 通过 | 工作台双接口并行加载相关页面可稳定打包 |
| 提交前格式检查 | `git diff --check` | 通过 | 本轮新增测试和文档无空白符问题 |

### 109.3 当前判断

1. 工作台和支付健康指标入口已具备独立控制器层门禁测试。
2. 本轮不将工作台当前页展示数据扩展解释为全量财务口径，避免运营指标误读。
3. 在命令全部通过并提交到 `test` 前，不推进 `master` 合并或 `release/*` 冻结。

## 110. 2026-08-03 订单中心控制器门禁补强验证

### 110.1 本轮补强点

1. 新增 `OrderControllerTest`，覆盖订单中心列表接口的参数绑定和服务转发。
2. 订单中心继续保持“查询订单 -> 判断是否存在最新预付单 -> 发起支付”的后台运营入口定位，本轮不改变字段和筛选口径。
3. 本轮完成后，`payment-core` 当前控制器层已无独立测试缺口。

### 110.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 订单中心定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -q -Dtest=OrderControllerTest,OrderServiceImplTest test` | 通过 | 订单中心控制器入口与服务层分页查询规范化行为均通过 |
| `admin-web` 前端构建 | `cd systems/payment-core/frontend/admin-web && npm run build` | 通过 | 订单中心和支付发起入口页面可稳定打包 |
| 提交前格式检查 | `git diff --check` | 通过 | 本轮新增测试和文档无空白符问题 |

### 110.3 当前判断

1. 订单中心作为“订单发起支付 -> 创建预付单”的后台入口，现已具备独立控制器层门禁测试。
2. `payment-core` 当前控制器层覆盖已收口，但发布门槛仍取决于真实渠道、跨系统联调、冻结版回滚包和整包发布证据。
3. 在命令全部通过并提交到 `test` 前，不推进 `master` 合并或 `release/*` 冻结。

## 111. 2026-08-07 订单中心导出补齐验证

### 111.1 本轮新增能力

1. 订单中心已补齐 `GET /api/orders/export`，支持按当前筛选条件导出订单快照。
2. 导出链路与列表口径保持一致，避免页面、列表和导出三套口径不一致。
3. 前端 `OrdersView` 的“导出订单”按钮已绑定到真实订单导出接口。

### 111.2 验证待办

1. 后端补跑 `OrderControllerTest` 与 `OrderServiceImplTest`。
2. 前端补跑 `admin-web` 构建。

## 111. 2026-08-04 运营配置快照联动复核

### 111.1 本轮新增能力

1. `payment-core` 在提交支付前，已按 `businessCode + payType + terminalType` 调用 `ops-config-system` 的有效配置快照接口。
2. 当前端未显式传入 `paymentMethod` 时，支付核心会使用运营配置返回的 `defaultPayMethod` 自动补齐。
3. 当前端未显式传入 `channelCode` 时，支付核心会使用运营配置返回的 `primaryChannelProfileCode` 自动补齐路由输入。
4. 当前联动顺序已经明确为：运营配置补齐默认值 -> 支付路由决策 -> 风控准入判断 -> 渠道提交。

### 111.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 提交支付定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -Dtest=PaymentServiceImplTest test` | 通过 | `26` 个测试通过，覆盖默认支付方式和默认渠道自动补齐 |
| `payment-core` 全量后端回归 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository test` | 通过 | `263` 个测试通过，`0` failures，`0` errors，`0` skipped |
| 提交前格式检查 | `git diff --check` | 通过 | 本轮改动无空白符和补丁格式问题 |

### 111.3 当前判断

1. `payment-core` 已具备从上游运营配置域自动补齐默认支付方式和默认渠道的能力，不再完全依赖前端显式下发。
2. 当前有效配置快照仍是 V1 读路径，尚未包含灰度发布、版本回滚和审批流，但已经满足支付主链路的配置收口需求。
3. 本轮仍只面向 `test` 分支持续收口，不推进 `master` 合并或 `release/*` 冻结。

## 112. 2026-08-04 运营配置 + 风控 + 支付核心跨进程 smoke

### 112.1 本轮验证目标

验证以下真实运行态链路：

1. `payment-core` 提交支付时，不传 `paymentMethod` 与 `channelCode`
2. `payment-core` 从 `ops-config-system` 自动补齐默认支付方式和默认渠道
3. `payment-core` 再调用已启动的 `risk-control-system`
4. `risk-control-system` 从 `ops-config-system` 读取 `CONTROL_BIG_AMOUNT_REVIEW`
5. 大额交易命中后，支付单与收银台统一进入 `待风控复核`

### 112.2 运行态事实

1. 预付单创建：
   - `orderNo = SMOKE-OPS-RISK-20260804-001`
   - `prepayOrderNo = PRE1785811244734`
   - `paymentOrderId = PAY1785811244732`
2. 运营配置快照运行态返回：
   - `defaultPayMethod = 微信支付`
   - `primaryChannelProfileCode = CHANNEL_WX_H5`
3. 风控运行态命中：
   - `reviewNo = REVIEW-1785811212924`
   - `riskTag = 大额支付人工复核`
4. 支付核心提交结果：
   - 收银台状态 `待风控复核`
   - 支付详情状态 `RISK_REVIEW`
   - 支付方式已补齐为 `微信支付`
   - 支付渠道已补齐为 `wx_h5`

### 112.3 本轮结论

1. `payment-core -> ops-config-system -> risk-control-system` 的配置生效链路已经拿到真实 HTTP 运行证据，不再只是单测级别的集成。
2. 当前这条链路仍停在“准入复核”阶段，尚未继续覆盖人工复核通过后的再次提交、支付成功回调和下游清结算闭环。
3. 因此本轮显著增强了 `test` 分支门禁，但仍不触发 `master` 合并或 `release/*` 冻结。

## 113. 2026-08-05 人工复核通过后再次提交真实 smoke

### 113.1 本轮验证目标

验证以下真实运行态链路是否已经闭环：

1. `payment-core` 在未传 `paymentMethod` 与 `channelCode` 时继续从 `ops-config-system` 自动补齐默认值
2. 大额支付命中 `risk-control-system` 人工复核后，收银台与支付单统一进入 `待风控复核 / RISK_REVIEW`
3. 风控人工复核通过后，再次提交同一预付单时，支付链路能够从 `待风控复核` 重新回到支付主链路
4. 再次提交成功后，支付单真实进入 `WAIT_CALLBACK`

### 113.2 运行态事实

1. 本轮真实预付单：
   - `orderNo = SMOKE-OPS-RISK-20260805-001`
   - `prepayOrderNo = PRE1785901653980`
   - `paymentOrderId = PAY1785901653979`
   - `billNo = BILL1785901653975`
2. 运营配置运行态事实：
   - `CONTROL_BIG_AMOUNT_REVIEW` 已启用
   - 有效快照返回 `defaultPayMethod = 微信支付`
   - 有效快照返回 `primaryChannelProfileCode = CHANNEL_WX_H5`
3. 首次提交支付时：
   - `payment-core` 未显式传 `paymentMethod` 与 `channelCode`
   - 支付方式自动补齐为 `微信支付`
   - 支付渠道自动补齐为 `wx_h5`
   - 收银台状态从 `待支付` 变为 `待风控复核`
   - 支付详情状态从 `PREPAY_CREATED` 变为 `RISK_REVIEW`
4. 风控人工复核运行态事实：
   - 复核单号 `REVIEW-1785901681130`
   - 风险标签 `大额支付人工复核`
   - 风控审核动作 `APPROVE`
5. 复核通过后再次提交支付：
   - 收银台状态推进为 `支付中`
   - 支付详情状态推进为 `WAIT_CALLBACK`
   - 渠道流水号生成 `WX-1785901790893`
   - 最近一次尝试状态为 `处理中`
   - 幂等键为 `PRE1785901653980|微信支付|wx_h5`
   - 最近一次请求报文已落库，包含 `resolvedChannelCode=wx_h5`
   - 最近一次响应报文已落库，返回 `USERPAYING`
   - 事件轨迹新增 `PAYMENT_SUBMIT | payment.trade.submitted.v1 | PENDING`

### 113.3 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| `PaymentServiceImplTest` 定向回归 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=$JAVA_HOME/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml -Dtest=PaymentServiceImplTest test` | 通过 | `27` 个测试全部通过，新增覆盖 `RISK_REVIEW -> PASS -> submit` |
| `payment-core` 全量回归 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=$JAVA_HOME/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f systems/payment-core/backend/pom.xml test` | 通过 | `264` 个测试全部通过 |
| `ops-config-system` 运行态开关 | `POST /api/ops-config/system-controls/toggle` | 通过 | `CONTROL_BIG_AMOUNT_REVIEW` 已真实启用 |
| `risk-control-system` 运行态限额隔离 | `POST /api/risk-control/limit-rules/toggle` | 通过 | `LIMIT_USER_DAY` 已临时关闭，隔离无关限额影响 |
| 跨进程支付重提 smoke | `ops-config-system + risk-control-system + payment-core` 本机同时启动后，按“预付单 -> 首次提交 -> 人工复核通过 -> 再次提交 -> 主动查单”顺序执行 | 通过 | 已拿到真实 `WAIT_CALLBACK` 证据 |

### 113.4 本轮结论

1. `payment-core` 当前已不只停留在“命中人工复核”，而是已经拿到 `RISK_REVIEW -> APPROVED -> WAIT_CALLBACK` 的真实运行证据。
2. `PaymentMapper.xml` 对 `待风控复核` 的状态机放行修复已被真实运行态验证有效，不再只是单测级别的修复。
3. 当前仍未覆盖真实回调成功、清分、结算、账务同批次闭环，因此本轮依然不足以触发 `test -> master` 或 `release/*`。

## 114. 2026-08-05 支付成功回调后下游联动真实 smoke

### 114.1 本轮验证目标

验证以下真实运行态链路：

1. `payment-core` 在 `WAIT_CALLBACK` 状态下接收 `WX_H5` 成功回调
2. 支付单、预付单、账单和订单在 `payment-core` 内完成成功收口
3. `payment-core` 成功事件真实下发至 `clearing-system` 与 `accounting-system`
4. `clearing-system` 在消费支付成功事件后，真实生成清分结果
5. `clearing-system` 再向 `settlement-system` 与 `accounting-system` 下发 `CLEARING_GENERATED`
6. 结算单与账务事件在下游系统真实落库

### 114.2 运行态事实

1. 使用上轮真实支付单继续验证：
   - `paymentOrderId = PAY1785901653979`
   - `prepayOrderNo = PRE1785901653980`
   - `channelTransactionNo = WX-1785901790893`
2. 回调前基线：
   - `payment-core` 状态为 `WAIT_CALLBACK`
   - `clearing-system` 按 `paymentOrderId=PAY1785901653979` 查询结果为 `0`
   - `accounting-system` 平台手续费账户 `ACT10003` 余额为 `¥12.00`
   - `settlement-system` 尚无本次支付对应结算事实
3. 真实回调请求：
   - 接口 `POST /api/payments/callback/WX_H5`
   - `tradeStatus = SUCCESS`
   - 回调后支付详情状态变为 `SUCCESS`
   - 最近一次支付尝试状态变为 `成功`
   - 事件轨迹新增 `PAYMENT_SUCCESS | payment.trade.succeeded.v1 | SUCCESS`
4. 下游真实落地事实：
   - `clearing-system` 生成清分单 `CLO20002`
   - 清分批次 `CLB10002`
   - 清分金额拆分：
     - 服务者 `¥5647.16`
     - 商家 `¥688.80`
     - 平台 `¥551.04`
     - 渠道费 `¥1.00`
   - `accounting-system` 对 `ACT10003` 新增 `PAYMENT_SUCCESS_EVENT`
   - `ACT10003` 可用余额由 `¥12.00` 变为 `¥6900.00`
   - `accounting-system` 新增 `PAYMENT_SUCCESS` 事件 `EVT50003`
   - `accounting-system` 新增 `CLEARING_GENERATED` 事件 `EVT50002`
   - `settlement-system` 新增 `CLEARING_GENERATED` 事件 `SVE70002`
   - `settlement-system` 新增结算单 `SLT20003`
   - 结算批次 `SET10002`
   - 结算单关联清分单 `CLO20002`
   - 结算单目标 `WRK1001 / 李阿姨`
   - 结算净额 `¥5647.16`

### 114.3 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 回调前支付基线 | `GET /api/payments/PAY1785901653979` | 通过 | 回调前状态为 `WAIT_CALLBACK` |
| 回调前清分基线 | `GET /api/clearing/orders?paymentOrderId=PAY1785901653979` | 通过 | 返回 `0` 条清分结果 |
| 回调前账务基线 | `GET /api/accounting/accounts/ACT10003` | 通过 | 平台手续费账户余额为 `¥12.00` |
| 成功回调 | `POST /api/payments/callback/WX_H5` | 通过 | 支付单真实进入 `SUCCESS` |
| 清分事实核验 | `GET /api/clearing/orders?paymentOrderId=PAY1785901653979` | 通过 | 生成 `CLO20002` |
| 账务事实核验 | `GET /api/accounting/accounts/ACT10003`、`GET /api/accounting/events?...` | 通过 | 余额与事件均已真实变化 |
| 结算事实核验 | `GET /api/settlements/events?...`、`GET /api/settlements/orders?...` | 通过 | 结算事件和结算单均已生成 |

### 114.4 本轮结论

1. `payment-core` 当前已经拿到“支付成功回调 -> 清分 -> 账务 -> 结算”的真实跨系统 HTTP 链路证据，不再只停留在 `WAIT_CALLBACK`。
2. 本轮证明的不是单系统成功，而是同一支付单 `PAY1785901653979` 在四个系统中的支付、清分、账务和结算事实一致推进。
3. 当前仍未形成可发布证据的部分主要剩余：
   - RabbitMQ 正式拓扑下同批次链路的 retry / DLQ / replay 复核
   - 前端四端对 `SUCCESS` 结果页与后台多台账页面的整包回归
   - 数据库迁移回滚包与正式 release 冻结清单

## 115. 2026-08-05 RabbitMQ 隔离拓扑支付成功演练

### 115.1 本轮验证目标

验证以下真实 broker 链路：

1. `payment-core` 在 `payment.event-dispatch.mode=amqp` 下把支付成功事件发布到 `payment.trade`
2. `clearing-system` 与 `accounting-system` 从隔离 `payment.success.20260805a.v1` 路由真实消费
3. `clearing-system` 再把清分结果发布到 `clearing.trade`
4. `settlement-system` 与 `accounting-system` 从隔离 `clearing.generated.20260805a.v1` 路由真实消费
5. 队列、消费者和下游事实都能对齐，不出现未解释积压

### 115.2 运行态事实

1. 本轮隔离拓扑：
   - suffix `20260805a`
   - RabbitMQ 管理端 `http://127.0.0.1:15672`
   - 管理用户 `hsp`
2. 本轮业务单：
   - `orderNo = MQ-DRILL-20260805-001`
   - `prepayOrderNo = PRE1785907738213`
   - `paymentOrderId = PAY1785907738212`
   - `billNo = BILL1785907738209`
3. 提交支付后基线：
   - 支付单状态 `WAIT_CALLBACK`
   - 收银台状态 `支付中`
   - 隔离主队列、retry、DLQ 均为 `0` 积压
   - 4 个消费者均在线
4. 首次回调时：
   - 支付单已真实进入 `SUCCESS`
   - `PAYMENT_SUCCESS` 事件 `EVT1785907822039` 首次发布失败
   - 根因是 `payment-core` 本轮首次 AMQP 启动未注入 `HSP_RABBITMQ_PASSWORD`
5. 修正 `payment-core` RabbitMQ 凭据并重启后：
   - 手动重发 `EVT1785907822039`
   - 事件发布状态变为 `SUCCESS`
6. 下游真实事实：
   - `clearing-system` 生成清分单 `CLO20002`
   - `accounting-system` 生成 `PAYMENT_SUCCESS` 事件 `EVT50002`
   - `settlement-system` 生成 `CLEARING_GENERATED` 事件 `SVE70002`
   - 队列快照显示隔离主队列、retry、DLQ 均无积压

### 115.3 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| RabbitMQ 容器启动 | `docker compose -f infra/rabbitmq/docker-compose.yml up -d` | 通过 | 本地 broker 已启动 |
| 隔离拓扑声明 | `bash infra/rabbitmq/declare_isolated_topology.sh 20260805a` | 通过 | 已生成四条隔离消费链路 |
| 队列基线 | `bash infra/rabbitmq/queue_snapshot.sh 20260805a` | 通过 | 4 个消费者在线，队列无积压 |
| 支付成功回调 | `POST /api/payments/callback/WX_H5` | 通过 | 支付单真实进入 `SUCCESS` |
| 首次 AMQP 出站 | `GET /api/payment-events?paymentOrderId=PAY1785907738212` | 失败后已恢复 | 首次因 RabbitMQ 密码缺失失败 |
| 手动重发 | `POST /api/payment-events/republish` | 通过 | `EVT1785907822039` 状态改为 `SUCCESS` |
| 清分事实核验 | `GET /api/clearing/orders?paymentOrderId=PAY1785907738212` | 通过 | 生成 `CLO20002` |
| 账务事实核验 | `GET /api/accounting/events?eventType=PAYMENT_SUCCESS&bizNo=PAY1785907738212` | 通过 | 生成 `EVT50002` |
| 结算事实核验 | `GET /api/settlements/events?eventType=CLEARING_GENERATED&bizNo=CLO20002` | 通过 | 生成 `SVE70002` |

### 115.4 本轮结论

1. `payment-core -> RabbitMQ -> clearing/accounting -> clearing -> RabbitMQ -> settlement/accounting` 的 broker 级真实链路已经重新拿到运行证据，不再只是 HTTP 直连或单测层面证明。
2. 本轮还额外证明了支付成功出站失败后的手动重发路径可以恢复下游事实，不会把失败事件永久卡死在 `FAILED`。
3. 当前仍需继续补齐：
   - 同批次 retry / DLQ / replay 的完整 MQ 补偿闭环
   - 前端四端围绕该真实 MQ 支付单的整包人工回归
   - 发布说明、回滚说明和版本冻结清单

## 116. 2026-08-05 同后缀 DLQ 人工结案与 Replay 真实验证

### 116.1 本轮验证目标

验证以下真实补偿链路：

1. `payment.trade.dlq` 中的消息可以被 `payment-core` intake 为 `t_payment_dead_letter_task`
2. 无法安全解析的坏消息可以被运营人工结案，不误触发 Replay
3. 可安全重放的有效消息可以经过 `READY_TO_REPLAY -> REPLAYED` 状态流转
4. Replay 后消息可以真实进入下游 `accounting-system`，并产生可核验账务事实

### 116.2 运行态事实

1. 本轮沿用 `20260805a` RabbitMQ 隔离拓扑，与上一轮支付成功事件演练保持同一后缀。
2. 通过 RabbitMQ management API 注入两条 DLQ 演练消息：
   - 人工结案消息：
     - `messageId = MQ-DLQ-20260805A-MANUAL-001`
     - `routingKey = payment.success.clearing.dlq.20260805a.v1`
     - `payload = {invalid-manual-json`
   - 定向 Replay 消息：
     - `messageId = MQ-DLQ-20260805A-REPLAY-001`
     - `routingKey = payment.success.accounting.dlq.20260805a.v1`
     - `payload` 业务字段：
       - `accountNo = ACT10003`
       - `paymentOrderId = PAY-DLQ-20260805A-001`
       - `orderNo = ORD-DLQ-20260805A-001`
       - `customerName = 补偿演练客户`
       - `amount = 9.99`
3. Intake 成功后生成两条死信补偿任务：
   - `taskNo = DLQb08e16e42e524d739cce`
   - `taskNo = DLQb8efefb346924a67ac61`
4. 非法 JSON 消息经运营确认后人工结案：
   - 接口：`POST /api/payment-dead-letter-tasks/DLQb08e16e42e524d739cce/resolve-manually`
   - 操作人：`支付运营A`
   - 备注：`2026-08-05 非法JSON报文，无法安全重放，按隔离演练人工结案`
   - 最终状态：`MANUAL_RESOLVED`
5. 有效补偿消息经运营复核后放行重放：
   - Ready 接口：`POST /api/payment-dead-letter-tasks/DLQb8efefb346924a67ac61/ready-to-replay`
   - Ready 备注：`2026-08-05 已核对账户ACT10003存在且报文字段完整，允许定向重放`
   - Replay 接口：`POST /api/payment-dead-letter-tasks/DLQb8efefb346924a67ac61/replay`
   - Replay 备注：`2026-08-05 定向重放至accounting专属replay路由`
   - 最终状态：`REPLAYED`
   - `replayCount = 1`
6. Replay 后下游真实账务事实：
   - `accounting-system` 生成事件 `EVT50004`
   - `eventType = PAYMENT_SUCCESS`
   - `bizNo = PAY-DLQ-20260805A-001`
   - 新增账务流水 `LDG20007`
   - `accountNo = ACT10003`
   - `amount = 9.99`
   - `beforeBalance = 200.00`
   - `afterBalance = 209.99`

### 116.3 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| DLQ 注入 | RabbitMQ management publish API | 通过 | 两条演练消息均成功进入对应 DLQ 路由 |
| 死信任务查询 | `GET /api/payment-dead-letter-tasks` | 通过 | 查到 `DLQb08e16e42e524d739cce`、`DLQb8efefb346924a67ac61` |
| 人工结案 | `POST /api/payment-dead-letter-tasks/DLQb08e16e42e524d739cce/resolve-manually` | 通过 | 非法 JSON 任务进入 `MANUAL_RESOLVED` |
| 允许重放 | `POST /api/payment-dead-letter-tasks/DLQb8efefb346924a67ac61/ready-to-replay` | 通过 | 有效消息进入可重放状态 |
| 执行重放 | `POST /api/payment-dead-letter-tasks/DLQb8efefb346924a67ac61/replay` | 通过 | 任务状态变为 `REPLAYED`，`replayCount = 1` |
| 下游账务核验 | `GET /api/accounting/events?eventType=PAYMENT_SUCCESS&bizNo=PAY-DLQ-20260805A-001`、`GET /api/accounting/ledgers?accountNo=ACT10003` | 通过 | 事件 `EVT50004` 与流水 `LDG20007` 已真实生成 |

### 116.4 本轮结论

1. `payment-core` 当前已经拿到同一 RabbitMQ 后缀 `20260805a` 下的 `DLQ 入账 -> 运营人工处置 -> 定向 Replay -> 下游账务恢复` 真实运行证据。
2. 这意味着 DLQ 补偿链路已经不再只是“有页面、有接口”，而是具备真实任务账本、人工审核痕迹、Replay 结果和下游账务事实。
3. 当前剩余未补齐的 MQ 门禁已经进一步收敛到：
   - 自动 retry 后进入 DLQ 的同批次新证据
   - 围绕这批真实案例的前端四端与后台多台账统一人工回归
   - 基于当前 HEAD 重算的发布说明、回滚说明和发布后验证清单

## 117. 2026-08-05 四端共享支付页状态补强验证

### 117.1 本轮修复目标

本轮围绕用户端共享支付页存在“关键支付状态被过度简化”的问题补强，重点修复：

1. 支付结果页不再只区分 `success / closed / pending`
2. `RISK_REVIEW / RISK_BLOCKED / RISK_REJECTED / WAIT_CALLBACK / PREPAY_CREATED` 需要按真实业务状态展示
3. 收银台在 `待风控复核 / 支付成功 / 会话过期` 等状态下，不应继续允许用户重复提交
4. `app-web / h5-web / pc-web` 三端需一起验证构建通过

### 117.2 本轮修复内容

1. 更新 `frontend/app-web/src/constants/payment.js`，补齐结果页状态映射：
   - `WAIT_CALLBACK / PAYING`
   - `RISK_REVIEW`
   - `RISK_BLOCKED`
   - `RISK_REJECTED`
   - `PREPAY_CREATED`
2. 更新 `frontend/app-web/src/views/ResultView.vue`：
   - 按不同支付状态展示更细化的“建议下一步 / 补救动作”
   - “模拟成功回调”仅在 `WAIT_CALLBACK / PAYING` 状态可执行
   - “关闭支付单”在 `SUCCESS / CLOSED` 状态下禁用
3. 更新 `frontend/app-web/src/views/CashierView.vue`：
   - 新增收银台提交前状态守卫
   - `待风控复核 / 支付成功 / 会话过期` 状态下禁止继续提交
   - 页面直接给出禁止提交原因，便于用户、运营和测试联查
4. 由于 `h5-web`、`pc-web` 复用 `app-web` 共享视图，上述补强已同步覆盖三端

### 117.3 构建验证

| 端 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| `app-web` | `npm run build` | 通过 | 共享支付页状态补强后产物正常生成 |
| `h5-web` | `npm run build` | 通过 | 复用共享视图的 H5 支付页未被破坏 |
| `pc-web` | `npm run build` | 通过 | PC 端收银台与结果页构建通过 |

### 117.4 本轮结论

1. 用户端支付页当前已经不再把关键状态统一折叠成“处理中”，而是能对 `WAIT_CALLBACK / 风控复核 / 风控拦截 / 风控拒绝 / 待发起支付` 做更专业的页面表达。
2. 收银台动作权限已经更接近真实支付状态机，避免了“待风控复核时还能继续提交”的错误交互。
3. 这轮补强提升的是四端页面的专业性与可联调性，但仍不等于已经完成最新一轮人工回归；后续还需围绕真实支付单补齐页面级人工验收记录。

## 118. 2026-08-05 fast suffix 自动 retry -> DLQ 真实验证

### 118.1 本轮验证目标

验证以下真实链路：

1. `clearing-system` 与 `accounting-system` 的 AMQP consumer 在消费异常时，会自动投递到各自 retry 路由
2. retry 队列 TTL 到期后，消息会自动回到主消费队列再次消费
3. 达到最大重试次数后，消息会自动进入各自 DLQ，而不是停留在 retry 或主队列
4. `payment-core` 能真实接住 DLQ intake 并创建补偿任务
5. 非法报文可由运营按“保留审计副本、不安全重放”方式人工结案

### 118.2 运行态事实

1. 本轮新建 fast suffix：
   - `suffix = 20260805fast`
   - `RETRY_TTL_MS = 2000`
2. 演练前队列快照显示：
   - fast suffix 主队列、retry、DLQ 均为 `0`
   - 尚无 fast suffix 消费者连接
3. 本轮以独立端口启动 fast consumer：
   - `clearing-system`：`18220`
   - `accounting-system`：`18210`
   - 均显式注入 RabbitMQ 凭据：
     - `HSP_RABBITMQ_USERNAME = hsp`
     - `HSP_RABBITMQ_PASSWORD = hsp-local-drill-20260801`
4. 通过 RabbitMQ management API 向 `payment.trade` 发布坏报文：
   - `messageId = MQ-FAST-RETRY-DLQ-20260805-001`
   - `correlationId = MQ-FAST-RETRY-DLQ-20260805-001`
   - `routingKey = payment.success.20260805fast.v1`
   - `payload = {invalid-fast-json`
5. 当前 HEAD 下游消费者未人工注入 DLQ，而是自动完成失败重试：
   - `clearing-system` 自动进入 `payment.success.clearing.retry.20260805fast.v1`
   - `accounting-system` 自动进入 `payment.success.accounting.retry.20260805fast.v1`
6. `payment-core` 的 `payment.compensation.dlq-intake` 活跃消费者已真实接住两条 fast suffix 死信，并生成补偿任务：
   - `DLQaba240b93ae346b7b7b9`
   - `DLQ338493c729064115a467`
7. 两条任务头信息都保留了自动 retry 的真实轨迹：
   - `x-retry-count = 3`
   - `x-first-death-exchange = payment.trade.retry`
   - `x-last-death-reason = expired`
   - `x-first-death-queue / x-last-death-queue` 分别为：
     - `clearing.payment-success.20260805fast.retry`
     - `accounting.payment-success.20260805fast.retry`
8. 两条任务均已人工结案：
   - clearing：`DLQaba240b93ae346b7b7b9`
   - accounting：`DLQ338493c729064115a467`
   - 操作人：`支付运营A`
   - 备注：`2026-08-05 fast suffix 自动重试3次后进入DLQ，报文为非法JSON，保留审计副本并人工结案`
9. 收尾快照显示：
   - `clearing.payment-success.20260805fast = 0`
   - `accounting.payment-success.20260805fast = 0`
   - 两个 retry 队列均为 `0`
   - `clearing.payment-success.20260805fast.dlq = 1`
   - `accounting.payment-success.20260805fast.dlq = 1`
10. 演练结束后已停止临时 fast consumer，避免对常驻 `20260805a` 运行实例造成干扰。

### 118.3 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 快速拓扑声明 | `RETRY_TTL_MS=2000 bash infra/rabbitmq/declare_isolated_topology.sh 20260805fast` | 通过 | 生成 fast suffix 的主队列、retry、DLQ 绑定 |
| 队列基线快照 | `bash infra/rabbitmq/queue_snapshot.sh 20260805fast` | 通过 | 初始主队列、retry、DLQ 均为 `0` |
| 启动 fast clearing consumer | `SERVER_PORT=18220 ... spring-boot:run` | 通过 | 监听 `clearing.payment-success.20260805fast` |
| 启动 fast accounting consumer | `SERVER_PORT=18210 ... spring-boot:run` | 通过 | 监听 `accounting.payment-success.20260805fast` |
| 坏报文注入 | RabbitMQ management publish API | 通过 | `MQ-FAST-RETRY-DLQ-20260805-001` 成功进入 fast suffix 主路由 |
| DLQ 任务核验 | `GET /api/payment-dead-letter-tasks?pageNo=1&pageSize=20` | 通过 | 生成 clearing/accounting 两条 fast suffix 补偿任务 |
| 人工结案 clearing | `POST /api/payment-dead-letter-tasks/DLQaba240b93ae346b7b7b9/resolve-manually` | 通过 | 非法 JSON 任务结案成功 |
| 人工结案 accounting | `POST /api/payment-dead-letter-tasks/DLQ338493c729064115a467/resolve-manually` | 通过 | 非法 JSON 任务结案成功 |
| 收尾快照 | `bash infra/rabbitmq/queue_snapshot.sh 20260805fast` | 通过 | 主队列和 retry 清零，DLQ 保留审计副本 |

### 118.4 本轮结论

1. 当前 HEAD 已真实证明 `消费失败 -> retry exchange -> retry TTL -> 主队列重试 -> 达到上限后进入 DLQ -> intake 入账补偿任务` 这一整条自动补偿前半链路。
2. 这意味着 `payment-core` 的 MQ 门禁不再只依赖历史 `20260803fast` 旧证据，而是在 `20260805fast` 下再次完成当前代码基线验证。
3. 当前剩余门禁继续收敛到两类：
   - 围绕真实支付单的四端人工回归证据仍需补齐
   - 正式发布说明、回滚文档、发布后验证清单仍需按当前 HEAD 重算

## 119. 2026-08-05 四端共享层收敛与构建复核

### 119.1 本轮目标

1. 收敛 `h5-web` 与 `pc-web` 对 `app-web` 页面文件的直接跨工程引用。
2. 在不改动支付主链路接口契约的前提下，沉淀终端共享层，降低三端后续页面演进耦合。
3. 补齐 `.runtime` 忽略规则和关键控制器方法说明，继续贴近冻结交付包标准。

### 119.2 本轮改动

1. 新增 `systems/payment-core/frontend/shared/src/views`：
   - `BusinessEntryView.vue`
   - `CashierView.vue`
   - `ResultView.vue`
2. 新增 `systems/payment-core/frontend/shared/src/styles/terminal.css`，供多端统一复用终端样式入口。
3. `h5-web` 与 `pc-web` 的 `main.js / CashierView.vue / ResultView.vue` 已切换为优先引用 `shared` 层，而不是直接引用 `app-web` 目录。
4. `h5-web` 与 `pc-web` 的 `vite.config.js` 已补齐外部共享目录下 `vue / vue-router` 的 alias 解析，以及 `server.fs.allow`。
5. 根目录 `.gitignore` 已补齐 `.runtime/` 忽略规则。
6. 后端补充：
   - `DashboardController#summary`
   - `SettlementController#workerOverview`
   - `SettlementController#workerList`
   - `SettlementController#exportWorkerList`
   的中文方法说明。

### 119.3 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| `h5-web` 构建 | `npm run build` | 通过 | 共享层切换后，产物正常生成 |
| `pc-web` 构建 | `npm run build` | 通过 | 共享层切换后，产物正常生成 |
| 后端编译 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home mvn -DskipTests compile` | 通过 | 显式切换到 JDK 后编译成功 |

### 119.4 结论

1. 本轮证明 `payment-core` 三端终端前台可以继续朝“独立工程 + 共享层”模式演进，而不是继续保留脆弱的跨工程直接引用。
2. 当前四端运行态联调环境已恢复，且 `h5-web / pc-web / backend` 的最新构建验证已经通过。
3. 这轮补强提升的是工程可维护性与后续自动化回归基础，但仍不能替代真实支付单页面级人工/自动化回归证据。

## 120. 2026-08-05 四端页面级真实回归补证

### 120.1 本轮目标

1. 用真实支付单补齐 `app-web / h5-web / pc-web / admin-web` 四端页面级状态证据。
2. 不只验证接口返回，还要验证页面标题、关键按钮可用性和状态文案。
3. 明确当前 HEAD 距离 `test -> master` 还差什么，而不是只给“构建通过”结论。

### 120.2 本轮样例

#### 既有样例

1. `SUCCESS`
   - 支付单：`PAY1785901653979`
   - 预付单：`PRE1785901653980`
2. `RISK_REVIEW`
   - 支付单：`PAY1785811244732`
   - 预付单：`PRE1785811244734`

#### 本轮新增样例

1. `WAIT_CALLBACK` App
   - 支付单：`PAY1785915556566`
   - 预付单：`PRE1785915556567`
   - 订单号：`REG-DEFAULT-APP-WAIT-CALLBACK-20260805-001`
2. `WAIT_CALLBACK` PC
   - 支付单：`PAY1785915556767`
   - 预付单：`PRE1785915556768`
   - 订单号：`REG-DEFAULT-PC-WAIT-CALLBACK-20260805-001`
3. `PREPAY_CREATED`
   - 支付单：`PAY1785915100360`
   - 预付单：`PRE1785915100361`
   - 订单号：`REG-WAIT-CALLBACK-20260805-001`

### 120.3 页面级回归结果

| 端 | 页面 | 样例 | 结果 | 页面事实 |
| --- | --- | --- | --- | --- |
| `app-web` | `payment-result/PAY1785901653979` | `SUCCESS` | 通过 | 页面标题 `支付成功`；`模拟成功回调` 与 `关闭支付单` 均禁用 |
| `h5-web` | `payment-result/PAY1785811244732` | `RISK_REVIEW` | 通过 | 页面标题 `待风控复核`；`模拟成功回调` 禁用；`结束本次支付` 可用 |
| `pc-web` | `payment-result/PAY1785915556767` | `WAIT_CALLBACK` | 通过 | 页面标题 `支付处理中`；`模拟成功回调` 与 `关闭当前支付单` 均可用 |
| `admin-web` | `payments/PAY1785811244732` | `RISK_REVIEW` | 通过 | 详情页展示 `RISK_REVIEW`；`主动查单` 可用；`模拟回调` 禁用；`关闭支付单` 可用 |

### 120.4 配置治理事实

1. 直接使用正式来源应用创建 `WAIT_CALLBACK` 样例时，实际命中了 `PAYMENT-1019`：
   - `housekeeping-app-web`
   - `housekeeping-pc-web`
2. 根因是当前控制策略为：
   - `strictMode = 开启`
   - `selfCheckStatus = WARN`
   - `selfCheckMessage` 包含：
     - `存在未被启用渠道覆盖的支付方式`
     - `存在未配置到启用渠道的授权商户号`
3. 因此本轮 `WAIT_CALLBACK` 样例使用 `sourceAppId=default-app` 创建，目的是验证页面和主链路状态映射，而不是回避真实问题。

### 120.5 本轮结论

1. 页面维度已经补到了当前 HEAD 的真实证据，不再只停留在“接口能调通、前端能构建”。
2. 当前仍不能推进 `test -> master`，阻断点已经进一步收敛为：
   - 正式来源应用的支付控制策略自检仍为 `WARN`
   - 围绕正式来源应用的最新四端回归证据尚未全量补齐
   - 正式发布材料仍需按当前 HEAD 重算

## 121. 2026-08-06 支付提交运营配置快照强门禁复核

### 121.1 本轮补强点

1. `PaymentServiceImpl#applyOpsConfigDefaults` 已从“能补则补”的宽松兜底，升级为“缺关键默认值即阻断提交”的强门禁。
2. 当提交请求未显式传入 `paymentMethod` 时，支付核心必须命中 `ops-config-system` 的有效快照。
3. 若有效快照不存在，或快照中未配置 `defaultPayMethod`，支付核心会直接抛出：
   - `PAYMENT-1025`
   - `当前支付场景缺少有效运营配置，无法补齐支付方式或渠道`
   - `当前支付场景未配置默认支付方式，无法提交支付`
4. 当请求已经显式传入 `paymentMethod` 时，本轮不会因为缺少默认渠道而额外阻断，而是继续由现有路由决策链路判断渠道可用性。

### 121.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| `PaymentServiceImplTest` 定向回归 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:/Users/abc123/apache-maven-3.9.16/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -Dtest=PaymentServiceImplTest test` | 通过 | `29` 个测试全部通过，新增覆盖“有效快照缺失”和“默认支付方式缺失”两类阻断场景 |
| 提交前格式检查 | `git diff --check` | 待执行 | 本节回填完成后统一执行 |

### 121.3 本轮判断

1. `payment-core` 现在不会再把“运营配置没配全”的问题悄悄带进支付主链路，而是会在提交入口前显式失败。
2. 这条门禁对产品、运营配置和测试都更友好，因为缺的是“配置域事实”还是“支付域路由事实”，现在已经能通过错误码与报错文案直接区分。
3. 本轮补强的是支付提交准入质量，不等价于正式发布门禁收口；`test -> master` 仍取决于页面级回归、发布材料重算和补偿证据完整性。

## 122. 2026-08-07 异常告警明细导出闭环验证

### 122.1 本轮补强点

1. `PaymentIssueService / PaymentIssueController` 新增异常告警明细 CSV 导出能力，接口为 `GET /api/payment-issues/alerts/export`。
2. `PaymentIssueServiceImpl` 复用现有告警明细筛选口径，导出告警基础字段、供应商信息、投递状态、确认人、确认时间、内容快照和原始回执快照。
3. `admin-web` 的“异常告警明细台”页补齐顶部“导出 CSV”入口，支持按当前筛选条件直接导出排障快照。
4. `PaymentIssueServiceImplTest / PaymentIssueControllerTest` 新增导出门禁，避免页面有按钮但后端无真实接口的再次回归。

### 122.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付异常中心定向测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -q -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -Dtest=PaymentIssueServiceImplTest,PaymentIssueControllerTest test` | 通过 | 合计 `16` 个用例全部通过，新增覆盖异常告警明细导出服务与控制器映射 |
| 管理端构建 | `cd systems/payment-core/frontend/admin-web && npm run build` | 通过 | `admin-web` 新增“导出 CSV”入口后仍可稳定构建 |

### 122.3 当前判断

1. 异常告警明细台现在从“可在线看”提升为“可筛选、可确认、可导出”，更接近公司内真实交接班和审计台账的使用方式。
2. 这一步补的是运营排障闭环，不代表异常告警链路本身已经达到正式发布门槛；真实供应商联调、主链路页面矩阵和发布证据仍需继续补强。

## 123. 2026-08-11 release 门禁与回归包复核

### 123.1 本轮复核目标

1. 重新判断当前 HEAD 是否已达到 `test -> master` 门槛。
2. 重新判断当前是否需要基于 `master` 产出正式 `release/*` 文档和版本。
3. 将判断依据从“单次构建/单次测试”升级为“回归包审计 + release 治理材料审计”的组合证据。

### 123.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| release 就绪审计 | `node tools/release/audit_release_readiness.mjs --root-dir /Users/abc123/workspace/home-service-payment-system ...` | 未通过 | 材料完整性 `通过`，正式放行条件 `未通过`，当前仍不允许创建 `release/*` |
| `test` 回归包审计 | `node tools/release/audit_regression_pack.mjs --root /Users/abc123/workspace/home-service-payment-system --pack /Users/abc123/workspace/home-service-payment-system/docs/releases/evidence/test-20260809-regression --output /Users/abc123/workspace/home-service-payment-system/docs/releases/audit/test-20260809-regression-audit.json` | 通过 | `10` 个场景已全部完成，`0` incomplete、`0` missing sample、`0` missing evidence、`0` TODO，但回归汇总仍未显式给出“允许推进 `test -> master` = 是” |
| 提交前格式检查 | `git diff --check` | 通过 | 当前文档与脚本改动无空白符和补丁格式问题 |

### 123.3 当前判断

1. 统一回归证据包已经完成收口，这意味着当前不再被“场景未跑完 / 证据未补齐 / TODO 未清零”阻断。
2. 但 `payment-core` 仍未达到 `test -> master` 或 `master -> release/*` 门槛，真正未收口的是正式发布治理事实：
   - 正式版发布说明仍未生成
   - 正式签字审批结果未形成
   - 回滚演练仍缺实际记录
   - 发布后现场核验仍缺实际记录
   - `master` 稳定观察结束结论仍未形成
3. 因此本轮不产出新的正式 release 文档，不创建 release 版本，后续优先级转为补齐治理记录与最终观察结论，而不是继续重复补回归截图或导出入口。

## 124. 2026-08-15 异常告警外发签名时间窗联动补强

### 124.1 本轮补强点

1. `AbstractLocalPaymentIssueAlertNotifier` 的外部 Webhook 请求签名已从“仅签请求体”升级为“`timestamp + nonce + payload` 统一编排”。
2. 当配置了 `timestampHeaderName / nonceHeaderName / signatureHeaderName` 时，公共通知器会先生成时间戳和随机 nonce，再基于三者构造规范签名串，减少供应商侧时间窗校验与防重放校验无法联动的问题。
3. `LocalSmsPaymentIssueAlertNotifierTest`、`LocalEmailPaymentIssueAlertNotifierTest`、`LocalImPaymentIssueAlertNotifierTest` 已同步改为校验新签名口径，避免三类通知器行为再次分叉。

### 124.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 告警通知器定向回归 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:/Users/abc123/apache-maven-3.9.16/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -q -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f /Users/abc123/workspace/home-service-payment-system/systems/payment-core/backend/pom.xml -Dtest=LocalSmsPaymentIssueAlertNotifierTest,LocalEmailPaymentIssueAlertNotifierTest,LocalImPaymentIssueAlertNotifierTest test` | 通过 | `17` 个用例通过，覆盖 SMS/EMAIL/IM 三类通知器的本地兜底、Webhook 成功、业务失败、HTTP 失败、传输失败与签名门禁 |

### 124.3 本轮判断

1. 这一步收敛的是 `payment-core` 异常告警外发到真实供应商时的服务端时间窗联动与基础防重放编排，不改变支付主链路接口契约。
2. 当前 `payment-core` 仍不应推进 `test -> master`，因为阻断项已经收敛到 release 治理事实，而不是回归包或局部后端门禁。
3. 当前仍不需要基于 `master` 产出新的正式 `release/*` 文档和版本。

## 125. 2026-08-20 告警签名回归与 release 审计复核

### 125.1 本轮复核目标

1. 确认异常告警通知器的最新签名编排改动仍可稳定通过既有单元测试门禁。
2. 重新确认当前仓库状态下是否已经达到 `test -> master` 或 `master -> release/*` 门槛。
3. 把判断依据固定到“定向 Maven 回归 + release 就绪审计”两类最新证据。

### 125.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 告警通知器定向回归 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -q -f /Users/abc123/workspace/home-service-payment-system/systems/payment-core/backend/pom.xml -Dtest=LocalEmailPaymentIssueAlertNotifierTest,LocalImPaymentIssueAlertNotifierTest,LocalSmsPaymentIssueAlertNotifierTest test` | 通过 | `17` 个用例全部通过，确认 SMS / EMAIL / IM 三类通知器的 `timestamp + nonce + payload` 签名口径与测试断言一致 |
| release 就绪审计 | `node /Users/abc123/workspace/home-service-payment-system/tools/release/audit_release_readiness.mjs --root-dir /Users/abc123/workspace/home-service-payment-system ... --output /Users/abc123/workspace/home-service-payment-system/docs/releases/audit/release-readiness-audit.json` | 未通过 | 审计输出显示“材料完整性：通过，正式放行条件：未通过，是否允许创建 `release/*`：否” |
| 提交前格式检查 | `git diff --check` | 通过 | 当前代码、脚本和文档改动无空白符或补丁格式问题 |

### 125.3 本轮判断

1. `payment-core` 的异常告警外发签名改动已经完成本地单测闭环，本轮不需要继续回退到旧签名口径。
2. 截至 `2026-08-20`，当前仍未达到 `test -> master` 门槛，阻断点依旧是 release 治理事实未闭环，而不是 `payment-core` 告警通知器局部质量。
3. 截至 `2026-08-20`，当前也仍不需要基于 `master` 产出新的正式 `release/*` 文档和版本。

## 126. 2026-08-22 回归审计口径澄清与门禁复核

### 126.1 本轮补强点

1. 调整 `tools/release/audit_regression_pack.mjs` 的输出语义，把“回归证据是否已清零 blocker”与“是否允许推进 `test -> master` / 创建 `release/*`”拆成独立字段。
2. 当回归包已经 `0 blocker`，但回归汇总仍未明确给出放行结论时，审计结果现在会额外输出 `notes`，避免再次把“证据完整”误读成“可以直接放行”。
3. 这次补强不改变任何支付主链路代码，只提升 `payment-core` 与整包交付门禁的可解释性和自动推进准确性。

### 126.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| `test` 回归包审计复核 | `node /Users/abc123/workspace/home-service-payment-system/tools/release/audit_regression_pack.mjs --root /Users/abc123/workspace/home-service-payment-system --pack /Users/abc123/workspace/home-service-payment-system/docs/releases/evidence/test-20260809-regression` | 通过 | 仍可读出 `10` 个场景、`0` incomplete、`0` missing sample、`0` missing evidence、`0` TODO；并新增区分“证据已清零”和“放行结论未满足”的输出语义 |
| release 就绪审计口径复核 | 读取 `docs/releases/audit/release-readiness-audit.md` 与 `tools/release/audit_release_readiness.mjs` | 未通过 | 材料完整性 `通过`，正式放行条件 `未通过`，当前仍不允许创建 `release/*` |
| 提交前格式检查 | `git diff --check` | 待执行 | 本轮未实际执行；需在后续提交前统一补跑 |

### 126.3 本轮判断

1. 截至 `2026-08-22`，`payment-core` 当前没有新的代码级放行阻断证据，但也没有新的事实能推翻已有 release 审计结论。
2. 当前对 `test -> master` 的更准确判断是：回归证据包已收口，但放行结论仍未闭环，原因仍是正式发布治理事实未形成最终记录。
3. 当前对 `master -> release/*` 的判断保持不变：仍不需要基于 `master` 产出新的正式 `release/*` 文档和版本。

## 127. 2026-08-25 统一门禁出口落地与告警签名回归复核

### 127.1 本轮补强点

1. 新增 `tools/release/audit_payment_core_gate.mjs`，把 `test` 回归包审计与 `release` 就绪审计收口成一个统一门禁出口，避免后续每轮都靠人工交叉读取两份审计结果。
2. `tools/release/audit_release_readiness.mjs` 新增 `--stdout-json` 模式，使其既能保留原有落盘审计行为，也能被统一门禁脚本作为子审计稳定复用。
3. 继续复核异常告警通知器的 `timestamp + nonce + payload` 签名口径，确认 SMS / EMAIL / IM 三类通知器测试仍全部通过。

### 127.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 统一门禁审计 | `node /Users/abc123/workspace/home-service-payment-system/tools/release/audit_payment_core_gate.mjs --root-dir /Users/abc123/workspace/home-service-payment-system --pack /Users/abc123/workspace/home-service-payment-system/docs/releases/evidence/test-20260809-regression` | 未通过 | 已生成 `docs/releases/audit/payment-core-gate-audit.{json,md}`；结论为“`test -> master = 否`、`release/* = 否`”，阻断项收敛为 `2` 个 |
| 告警通知器定向回归 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -Dtest=LocalEmailPaymentIssueAlertNotifierTest,LocalImPaymentIssueAlertNotifierTest,LocalSmsPaymentIssueAlertNotifierTest test` | 通过 | `17` 个用例全部通过，确认统一签名口径未破坏 SMS / EMAIL / IM 三类通知器的既有门禁 |
| 提交前格式检查 | `git diff --check` | 通过 | 当前新增脚本与文档回填无空白符或补丁格式问题 |

### 127.3 本轮判断

1. 截至 `2026-08-25`，`payment-core` 仍不满足 `test -> master` 门槛；统一门禁报告给出的两个阻断项是：
   - 回归汇总尚未显式记录 `test -> master` 结论
   - release 正式放行条件仍未闭环
2. 截至 `2026-08-25`，`payment-core` 也仍不需要基于 `master` 产出新的正式 `release/*` 文档和版本，因为正式版说明、签字审批、回滚演练、现场核验与稳定观察结束结论都还未形成真实记录。
3. 本轮最重要的推进不是“又补了一次局部代码”，而是把后续自动推进的门禁出口标准化了；后续应优先补回归汇总中的显式放行结论留痕，以及 release 治理事实本身。

## 128. 2026-08-26 回归放行留痕补齐与统一门禁复判

### 128.1 本轮补强点

1. 调整 `tools/release/audit_payment_core_gate.mjs` 的阻断口径，把“是否已显式记录 `test -> master` 结论”与“当前是否允许推进”拆开，避免在 release 治理尚未闭环时把显式记录 `否` 误判成文档缺失。
2. 回填 `docs/releases/evidence/test-20260809-regression/records/regression-summary.md` 的放行结论，明确记录当前 `test -> master = 否`、`release/* = 否`，并把剩余 release 治理缺口写实留痕。
3. 保持 `payment-core` 当前实现面不再新增功能改动，本轮重点是把自动化推进的判断链收口成“证据包已完成、release 治理未完成”的单一结论。

### 128.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| `test` 回归包审计 | `node /Users/abc123/workspace/home-service-payment-system/tools/release/audit_regression_pack.mjs --root /Users/abc123/workspace/home-service-payment-system --pack /Users/abc123/workspace/home-service-payment-system/docs/releases/evidence/test-20260809-regression` | 通过 | 预期应显示证据包 `blocker = 0`，且回归汇总已显式记录 `test -> master` / `release/*` 结论 |
| release 就绪审计 | `node /Users/abc123/workspace/home-service-payment-system/tools/release/audit_release_readiness.mjs --root-dir /Users/abc123/workspace/home-service-payment-system --candidate-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/v1.1.0-发布说明-候选版.md --stabilization-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/2026-08-09-master稳定观察起始记录.md --checklist-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/2026-08-09-master稳定观察检查清单.md --approval-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/2026-08-09-v1.1.0发布审批清单.md --rollback-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/2026-08-09-v1.1.0回滚演练记录.md --onsite-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/2026-08-09-v1.1.0发布后现场核验记录模板.md --stabilization-closing-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/2026-08-09-master稳定观察结束结论模板.md --decision-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/2026-08-09-v1.1.0切版决策记录.md --observation-checkpoint-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/2026-08-09-master稳定观察中间结论.md --live-verification-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/2026-08-09-master主链路即时核验记录.md --deferral-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/2026-08-09-v1.1.0暂缓切版说明.md --gate-day-end-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/2026-08-09-release门禁日结.md --reevaluation-checklist-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/2026-08-10-v1.1.0重评清单.md --release-note-checklist-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/v1.1.0-正式版发布说明检查表.md --prep-status-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/2026-08-09-v1.1.0发布前材料现状记录.md --stdout-json` | 未通过 | 预期仍显示 release 材料完整性 `通过`、正式放行条件 `未通过`，不允许创建 `release/*` |
| 统一门禁复判 | `node /Users/abc123/workspace/home-service-payment-system/tools/release/audit_payment_core_gate.mjs --root-dir /Users/abc123/workspace/home-service-payment-system --pack /Users/abc123/workspace/home-service-payment-system/docs/releases/evidence/test-20260809-regression` | 未通过 | 预期阻断项应从“文档未写结论 + release 未闭环”收敛为“release 正式放行条件未闭环” |

### 128.3 本轮判断

1. 截至 `2026-08-26`，`payment-core` 仍未达到 `test -> master` 门槛，但阻断点已经进一步收敛为单一 release 治理事实未闭环，而不是回归证据包或局部代码质量问题。
2. 截至 `2026-08-26`，当前仍不需要基于 `master` 产出新的正式 `release/*` 文档和版本；原因仍是正式版发布说明、签字审批、回滚演练实际记录、发布后现场核验实际记录和 `master` 稳定观察结束结论均未形成真实记录。
3. 因此下一轮应优先推动真实 release 治理事实补录，而不是继续在 `payment-core` 内做局部代码补丁。

## 129. 2026-08-29 统一门禁口径纠偏与只读复判补强

### 129.1 本轮补强点

1. 修正 `tools/release/audit_payment_core_gate.mjs` 的统一门禁判定，明确把 `test -> master` 与 `master -> release/*` 拆为两段：前者只依赖回归证据和冻结门禁结论，后者再叠加 release 治理事实。
2. 为 `tools/release/audit_payment_core_gate.mjs` 增加 `--stdout-json`，使其在只读巡检环境下也能复用，与现有 `audit_release_readiness.mjs` 的输出方式保持一致。
3. 回填 `docs/releases/evidence/test-20260809-regression/records/regression-summary.md`，把与 `docs/集成交付门禁审计.md` 第 `10.25` 节一致的真实结论写实为：`test -> master = 是`、`release/* = 否`。
4. 同步更新 `docs/releases/audit/payment-core-gate-audit.{json,md}`，消除仓库内“已完成 master 合并”与“仍不允许 test -> master”并存的冲突口径。

### 129.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| `test` 回归包审计 | `node /Users/abc123/workspace/home-service-payment-system/tools/release/audit_regression_pack.mjs --root /Users/abc123/workspace/home-service-payment-system --pack /Users/abc123/workspace/home-service-payment-system/docs/releases/evidence/test-20260809-regression` | 通过 | `10` 个场景全部完成，`test -> master` 已显式记录为 `是`，`release/*` 仍为 `否` |
| release 就绪审计 | `node /Users/abc123/workspace/home-service-payment-system/tools/release/audit_release_readiness.mjs --root-dir /Users/abc123/workspace/home-service-payment-system --candidate-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/v1.1.0-发布说明-候选版.md --stabilization-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/2026-08-09-master稳定观察起始记录.md --checklist-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/2026-08-09-master稳定观察检查清单.md --approval-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/2026-08-09-v1.1.0发布审批清单.md --rollback-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/2026-08-09-v1.1.0回滚演练记录.md --onsite-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/2026-08-09-v1.1.0发布后现场核验记录模板.md --stabilization-closing-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/2026-08-09-master稳定观察结束结论模板.md --decision-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/2026-08-09-v1.1.0切版决策记录.md --observation-checkpoint-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/2026-08-09-master稳定观察中间结论.md --live-verification-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/2026-08-09-master主链路即时核验记录.md --deferral-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/2026-08-09-v1.1.0暂缓切版说明.md --gate-day-end-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/2026-08-09-release门禁日结.md --reevaluation-checklist-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/2026-08-10-v1.1.0重评清单.md --release-note-checklist-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/v1.1.0-正式版发布说明检查表.md --prep-status-doc /Users/abc123/workspace/home-service-payment-system/docs/releases/2026-08-09-v1.1.0发布前材料现状记录.md --stdout-json` | 未通过 | 材料骨架仍齐，但正式放行缺口仍为 `5` 项：候选版未转正式版、签字审批、回滚演练、现场核验、稳定观察结束结论 |
| 统一门禁复判 | `node /Users/abc123/workspace/home-service-payment-system/tools/release/audit_payment_core_gate.mjs --root-dir /Users/abc123/workspace/home-service-payment-system --pack /Users/abc123/workspace/home-service-payment-system/docs/releases/evidence/test-20260809-regression --stdout-json` | 未通过 | 统一门禁已收敛为 `test -> master = 是`、`release/* = 否`，唯一阻断项为 `release 正式放行条件仍未闭环` |
| 提交前格式检查 | `git -C /Users/abc123/workspace/home-service-payment-system diff --check` | 通过 | 当前脚本与文档补丁无空白符问题 |

### 129.3 本轮判断

1. 截至 `2026-08-29`，`payment-core` 对应冻结基线的 `test -> master` 门槛应判定为“已达标且已在 `2026-08-02 05:42 +08:00` 完成”，不应再被 release 治理缺口反向否决。
2. 截至 `2026-08-29`，`master -> release/*` 仍明确不放行；当前缺的仍是五项 release 治理真实记录，而不是 `payment-core` 回归证据、局部代码质量或统一门禁脚本本身。
3. 因此当前仍不需要基于 `master` 产出新的正式 `release/*` 文档和版本；下一轮若继续推进，优先级应放在把候选版升级条件、正式审批、回滚演练、现场核验和稳定观察结束结论补成真实记录。

## 130. 2026-09-03 门禁复判与告警通知器定向回归

### 130.1 本轮复核

1. 重新执行统一门禁审计，结论为：`test -> master = 是`，`release/* = 否`。`test -> master` 已在冻结基线完成，不应被尚未完成的正式发版治理事项反向否决。
2. 重新执行 SMS、EMAIL、IM 三类本地告警通知器的定向 Maven 回归；签名口径保持 `timestamp + nonce + payload`，回归通过。
3. 本轮仅刷新审计事实和验证记录；未将候选发布材料伪造为已执行的演练、核验或签字证据。

### 130.2 验证命令与结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 统一门禁审计 | `node /Users/abc123/workspace/home-service-payment-system/tools/release/audit_payment_core_gate.mjs --root-dir /Users/abc123/workspace/home-service-payment-system --pack /Users/abc123/workspace/home-service-payment-system/docs/releases/evidence/test-20260809-regression` | 未通过 | 已刷新审计产物；`test -> master = 是`，`release/* = 否`，唯一顶层阻断为“release 正式放行条件仍未闭环” |
| 告警通知器定向回归 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:/Users/abc123/apache-maven-3.9.16/bin:$PATH /Users/abc123/apache-maven-3.9.16/bin/mvn -q -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository -f /Users/abc123/workspace/home-service-payment-system/systems/payment-core/backend/pom.xml -Dtest=LocalEmailPaymentIssueAlertNotifierTest,LocalImPaymentIssueAlertNotifierTest,LocalSmsPaymentIssueAlertNotifierTest test` | 通过 | `17` 个用例通过，覆盖 SMS / EMAIL / IM 的本地兜底、Webhook 成功与失败路径和签名编排 |
| 提交前格式检查 | `git -C /Users/abc123/workspace/home-service-payment-system diff --check` | 通过 | 当前工作区补丁未发现空白符错误 |

### 130.3 当前判断

1. `payment-core` 已达到并完成冻结基线的 `test -> master` 门槛。
2. 不应创建 `release/*` 或正式版本；仍缺候选版转正式版、正式签字审批、回滚演练实录、发布后现场核验实录和 `master` 稳定观察结束结论五项真实治理事实。
3. 下次自动推进应优先检查上述事实是否由责任角色补齐；在无新增真实证据时，保持 release 不放行。

## 131. 2026-09-07 当前增量全量验证与门禁复判

### 131.1 本轮验证

1. 对异常告警通知器 `timestamp + nonce + payload` 签名增量执行定向回归，SMS、EMAIL、IM 三类通知器共 `17` 个用例通过。
2. 对 payment-core 后端执行全量 Maven 测试，共 `270` 个用例通过，`0` failures、`0` errors、`0` skipped。
3. 对 `admin-web / app-web / h5-web / pc-web` 执行生产构建，四端全部通过。
4. 重新生成回归、release 就绪和 payment-core 统一门禁审计产物。

### 131.2 门禁结论

1. 回归证据包 `10` 个场景全部收口，冻结基线 `test -> master = 是`。
2. 当前仍不允许创建 `release/*`，唯一顶层阻断为“release 正式放行条件仍未闭环”。
3. 展开后仍缺正式版发布说明、正式签字审批、回滚演练实录、发布后现场核验实录和 `master` 稳定观察结束结论五项真实治理事实。
4. 当前未提交增量已通过代码级验证，但仍需独立提交；后续合并时应基于提交后的新基线重新复判，不能直接套用历史冻结结论。

## 132. 2026-09-08 主动查单生产化闭环验证

### 132.1 本轮补强

1. 新增可配置的 `GatewayAccessPaymentChannelQueryAdapter`，支持按启停开关、查询地址、渠道白名单和超时参数调用统一渠道网关；未启用时保持原有模拟查单行为。
2. 将渠道查单服务改为显式返回 `tradeStatus / channelTransactionNo / querySource`，由支付业务层统一决定是否改变内部状态。
3. 渠道明确返回 `SUCCESS` 时，通过条件更新原子抢占成功收口权，并同步完成支付尝试、预付单、订单/账单、`PAYMENT_SUCCESS` 事件、下游派发和并发令牌释放。
4. 对非成功结果仅记录查单事实；已成功、已关闭以及未抢到收口权的重复查单不会重复记账或重复发事件。
5. 本轮没有真实微信、支付宝或银行渠道凭证，因此只确认 HTTP 契约、适配器选择与内部状态收口自动化测试通过，不把它记录为真实渠道现场联调完成。

### 132.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 主动查单定向测试 | `mvn -Dtest=PaymentChannelQueryServiceImplTest,GatewayAccessPaymentChannelQueryAdapterTest,PaymentServiceImplTest test` | 通过 | `39` 个用例全部通过，覆盖适配器启停、响应校验、成功收口、非成功留痕、终态保护和并发幂等 |
| 后端全量测试 | `mvn test` | 通过 | `277` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功 |
| `app-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功 |
| `h5-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功 |
| `pc-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功 |
| 统一门禁审计 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ...` | 部分通过 | 审计结果为 `test -> master = 是`、`release/* = 否`；release 仍有 `4` 项真实治理缺口 |

### 132.3 当前判断

1. `payment-core` 已具备在回调丢失时通过统一网关主动查单并幂等收口成功状态的代码能力，原有本地模拟环境保持兼容。
2. 真实渠道专有协议、凭证、证书和运行态联调仍是外部接入条件，不能仅凭本轮单元测试判定真实渠道已经投产。
3. 本轮代码提交为 `0732e49`。统一门禁脚本引用的历史回归包仍判定 `test -> master = 是`；对本次新增量另以“外部网关默认关闭、原模拟路径兼容、277 项后端测试通过、四端生产构建通过”作为补充合并依据。
4. `master -> release/*` 仍不放行；当前仍缺正式版发布说明、正式签字审批、发布后现场核验实录和 `master` 稳定观察结束结论，未伪造或补写这些外部治理事实。

## 133. 2026-09-08 待回调自动查单补偿验证

### 133.1 本轮补强

1. 新增待回调支付单候选查询，按首次等待阈值、最近查单重试间隔、预付单有效期和批次上限筛选。
2. 新增 `PAYMENT_PENDING_QUERY` 自动补偿任务和任务中心人工补跑入口；自动任务复用多实例租约，单笔查询失败不会阻断同批其他支付单。
3. 自动调度仅在真实网关查单与补偿任务两个开关同时开启时执行，默认保持关闭。
4. 任务中心新增主动查单操作卡、任务分级口径与执行日志留痕；查询成功、仍处理中和查询失败分别统计。

### 133.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 自动查单补偿定向测试 | `mvn -Dtest=PaymentCompensationSchedulerTest,PaymentTaskCenterServiceImplTest,PaymentTaskCenterControllerTest test` | 通过 | `63` 个用例全部通过，覆盖双开关启停、人工补跑、自动租约、混合结果统计、单笔失败隔离和失败单号摘要 |
| 后端全量测试 | `mvn test` | 通过 | `282` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功，任务中心新增主动查单控件已完成浏览器静态页面检查 |
| `app-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功 |
| `h5-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功 |
| `pc-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功 |

### 133.3 当前判断

1. `payment-core` 已形成“回调等待超时 -> 自动或人工查单 -> 成功幂等收口 -> 任务日志留痕”的基础补偿闭环。
2. 当前自动补偿默认关闭，启用前仍需完成真实 `gateway-access` 地址、渠道凭证、证书、限流参数和运行态联调；本轮验证不代表微信、支付宝或银行渠道现场联调完成。
3. 固定间隔扫描适合作为当前基线，后续仍需补指数退避、渠道级限流、积压指标和告警。
4. 本轮代码级门禁通过，但 `master -> release/*` 仍受正式版发布说明、正式签字审批、发布后现场核验实录和 `master` 稳定观察结束结论约束。

## 134. 2026-09-08 自动查单指数退避与人工立即补跑验证

### 134.1 本轮补强

1. 将待回调查单候选拆为人工与自动两种口径：人工补跑保留首次等待阈值和批次上限，但绕过最近查单退避窗口；自动调度只选择已达到下一重试时间的支付单。
2. 自动候选将渠道业务结果和网关查询失败都计入历史次数，按 `基础间隔 × 退避系数^(历史查单次数-1)` 递增查询间隔，并以最大重试间隔封顶，避免渠道异常期间持续高频访问。
3. 新增 `retry-backoff-factor` 与 `max-retry-interval-seconds` 配置，默认采用 `60 秒 / 2 倍 / 900 秒封顶`，非法偏小值在服务构造时归一化。
4. 新增 Mapper 集成测试，以 H2 MySQL 兼容模式直接执行 MyBatis XML，覆盖首次查单、基础间隔未到期、二次退避未到期/已到期、最大间隔未到期/已到期和人工绕过退避。

### 134.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 主动查单与任务中心定向测试 | `mvn -Dtest=PaymentTaskCenterMapperIntegrationTest,PaymentTaskCenterServiceImplTest,PaymentCompensationSchedulerTest,PaymentTaskCenterControllerTest,PaymentServiceImplTest test` | 通过 | `66` 个用例通过，`0` failures、`0` errors、`0` skipped；覆盖失败事实留痕，且 Mapper 集成测试自动候选返回 3 笔、人工候选返回 6 笔 |
| 后端全量测试 | `mvn test` | 通过 | `284` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功，人工立即补跑说明已同步到任务卡 |
| Mapper XML 校验 | `xmllint --noout PaymentTaskCenterMapper.xml` | 通过 | XML 结构合法 |

### 134.3 当前判断

1. 自动查单已从固定单笔重试间隔升级为按历史查询次数递增、最大间隔封顶的补偿策略，人工处置不再被自动退避误拦截。
2. H2 MySQL 兼容模式证明当前 MyBatis SQL 和退避函数组合可执行，但不替代生产 MySQL 压测、真实渠道限流验证或运行态联调。
3. 下一步生产化重点收敛为渠道级并发/频率配额、待回调积压指标与告警，以及真实 `gateway-access` 渠道凭证和协议联调。
4. 本轮没有新增任何正式审批、发布后现场核验或稳定观察结束事实，`master -> release/*` 仍不放行。

## 135. 2026-09-08 自动查单渠道配额与积压指标验证

### 135.1 本轮补强

1. 自动与人工查单候选均新增单轮每渠道批次配额，使用渠道内窗口排序后再应用全局批次上限，避免单一大渠道占满整轮任务并造成其他渠道饥饿。
2. 任务中心总览新增 `pendingQueryBacklogCount` 与 `duePendingQueryCount`，分别展示已达到首次补查阈值的积压量和当前达到自动退避时间的可执行量。
3. 任务中心新增“主动查单积压”重点告警：超过全局单批上限判定 `P1`，存在积压判定 `P2`，无积压判定 `P3`。
4. `admin-web` 新增“达到补查阈值”“当前到期可查”指标卡及运维关注行，人工立即补跑语义保持不变。

### 135.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| Mapper 与任务中心定向测试 | `mvn -Dtest=PaymentTaskCenterMapperIntegrationTest,PaymentTaskCenterServiceImplTest test` | 通过 | `22` 个用例通过；验证默认配额自动候选 3 笔、每渠道 1 笔时自动候选 2 笔、每渠道 2 笔时人工候选 4 笔，以及积压 6 笔/到期 3 笔统计 |
| 后端全量测试 | `mvn test` | 通过 | `285` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功 |
| 浏览器静态检查 | `http://127.0.0.1:4175/payment-task-center` | 通过 | 两张新增指标卡和运维表行正常渲染；后端 `18081` 未启动，因此仅验证静态布局，不记录为接口联调完成 |
| Mapper XML 与补丁检查 | `xmllint --noout ... && git diff --check` | 通过 | Mapper XML 结构合法，补丁无空白符问题 |

### 135.3 当前判断

1. 单轮渠道配额解决了批次内公平性和突发放量问题，但不等同于跨实例、时间窗口级分布式限流，后者仍需结合共享存储或网关能力实现。
2. 当前积压与到期指标可支持运营判断“总待回调中有多少已需要补查”，但尚未形成时间序列趋势、阈值配置和外部告警。
3. 本轮没有真实渠道凭证、生产 MySQL 压测或真实网关运行态联调，相关事项继续保留为外部接入缺口。
4. 本轮没有新增正式审批、发布后现场核验或稳定观察结束事实，`master -> release/*` 仍不放行。

## 136. 2026-09-08 渠道查单跨实例固定窗口限流验证

### 136.1 本轮补强

1. 新增 `t_payment_channel_query_rate_bucket` 渠道共享桶、MyBatis 原子占用 SQL 和既有运行库增量迁移，所有实例按归一化渠道编码共用同一固定时间窗计数。
2. 限流服务仅在真实 `gateway-access` 与限流开关同时启用时生效，默认关闭，避免本地模拟查单被误限流。
3. 配额占用使用独立 `REQUIRES_NEW` 事务先提交，再发起外部 HTTP 查单，避免把数据库桶行锁持有到网络调用结束。
4. 直接查单、任务中心人工补跑和自动补偿统一经过 `PaymentService.query`；配额耗尽返回 `PAYMENT-1027`，不触达渠道，也不计入自动查单退避失败历史。

### 136.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 限流定向测试 | `mvn -Dtest=PaymentChannelQueryRateLimitServiceImplTest,PaymentChannelQueryRateLimitMapperIntegrationTest,PaymentServiceImplTest test` | 通过 | `40` 个用例通过，覆盖双开关、渠道归一化、参数下限、配额耗尽前阻断渠道，以及同窗 `1 / 1 / 0` 和过窗重置 |
| 后端全量测试 | `mvn test` | 通过 | `291` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| Mapper XML 与补丁检查 | `xmllint --noout PaymentChannelQueryRateLimitMapper.xml && git diff --check` | 通过 | XML 结构合法；使用 MySQL/H2 均支持的 `TIMESTAMPADD` 保持数据库时钟口径 |

### 136.3 当前判断

1. `payment-core` 已具备共享数据库上的跨实例、按渠道固定时间窗口限流基础能力，且所有现有查单入口口径一致。
2. 当前实现是固定窗口，不等同于滑动窗口、令牌桶或网关侧限流；生产 MySQL 并发压测、容量参数校准和趋势告警仍需继续补齐。
3. 本轮没有真实微信、支付宝或银行凭证与运行态环境，因此不记录为真实渠道联调完成。
4. 本轮没有新增正式审批、发布后现场核验或稳定观察结束事实，`master -> release/*` 仍不放行。

## 137. 2026-09-08 主动查单失败趋势与阈值告警验证

### 137.1 本轮补强

1. 任务中心总览新增最近窗口查单总次数、失败次数、失败率和逐小时趋势，数据直接来自 `t_payment_notify_log` 中的 `已查单 / 查单失败` 事实。
2. 新增可配置回看小时数、失败笔数阈值、失败率阈值和失败率最小样本数；失败笔数达到阈值，或样本充分且失败率达到阈值时生成 P1，存在少量失败生成 P2，无失败生成 P3。
3. 两份全量 schema 为查询事实日志新增 `notify_status, created_at` 索引，并提供 `2026-09-08-add-payment-query-monitor-index.sql` 供既有运行库增量执行。
4. `admin-web` 任务中心新增窗口查单量、失败量指标卡、小时趋势表和阈值口径展示，原有加载、失败和空趋势状态保持可用。

### 137.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| Mapper、服务与控制器定向测试 | `mvn -Dtest=PaymentTaskCenterMapperIntegrationTest,PaymentTaskCenterServiceImplTest,PaymentTaskCenterControllerTest test` | 通过 | `28` 个用例通过；窗口内 `15` 次查询、`1` 次失败，窗口外事实被排除；服务层 `12` 次查询、`4` 次失败按 `33.33%` 触发 P1 |
| 后端全量测试 | `mvn test` | 通过 | `293` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 浏览器静态检查 | `http://127.0.0.1:4175/payment-task-center` | 通过 | 指标卡、小时趋势空态、阈值口径和运维关注行均正常进入页面结构；后端 `18081` 未启动，API 显示 `500 [PAYMENT-CLIENT]`，不记录为接口联调完成 |
| Mapper XML 与补丁检查 | `xmllint --noout PaymentTaskCenterMapper.xml && git diff --check` | 通过 | 查询按数据库小时桶聚合，MySQL/H2 兼容模式可执行 |

### 137.3 当前判断

1. 主动查单已从“当前积压快照”扩展为“历史查询事实趋势 + 可配置阈值告警”，运营可以区分偶发失败和持续异常。
2. `PAYMENT-1027` 限流拒绝仍发生在查询事实写入前，本轮没有把它伪装成渠道失败；独立审计与指标能力见第 138 节。
3. 当前 P1/P2/P3 仍是任务中心站内告警，不代表企业微信、短信或邮件已经真实送达；生产 MySQL 压测和真实渠道运行态联调仍未完成。
4. 本轮没有新增正式审批、发布后现场核验或稳定观察结束事实，`master -> release/*` 仍不放行。

## 138. 2026-09-08 主动查单限流拒绝独立审计与告警验证

### 138.1 本轮补强

1. 新增 `t_payment_channel_query_rate_limit_log`，按拒绝流水号记录渠道、支付单号、窗口秒数、渠道配额和数据库拒绝时间，并提供既有运行库增量迁移。
2. `PaymentChannelQueryRateLimitService` 在共享配额耗尽时先写拒绝事实，再返回 `PAYMENT-1027`；独立事务对业务异常不回滚，避免拒绝结果返回后审计记录丢失。
3. 任务中心总览新增监控窗口内限流拒绝次数与独立 P1 阈值，生成 `PAYMENT_QUERY_RATE_LIMIT_REJECTION` 告警，不把拒绝次数并入渠道查询失败率。
4. `admin-web` 新增窗口内限流拒绝指标卡、阈值说明和运维关注行，任务中心口径升级为 V1.16。

### 138.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 限流、支付查询与任务中心定向测试 | `mvn -Dtest=PaymentChannelQueryRateLimitMapperIntegrationTest,PaymentTaskCenterMapperIntegrationTest,PaymentChannelQueryRateLimitServiceImplTest,PaymentServiceImplTest,PaymentTaskCenterServiceImplTest,PaymentTaskCenterControllerTest test` | 通过 | `69` 个用例通过；覆盖拒绝事实写入、支付单号透传、外部渠道调用前拦截、24 小时窗口统计和独立 P1 阈值 |
| 后端全量测试 | `mvn test` | 通过 | `294` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 浏览器静态检查 | `http://127.0.0.1:4176/payment-task-center` | 通过 | 限流拒绝卡片、独立阈值说明、V1.16 标识和运维关注行正常渲染；后端 `18081` 未启动，API 显示 `500 [PAYMENT-CLIENT]`，不记录为接口联调完成 |
| Mapper XML 与补丁检查 | `xmllint --noout PaymentChannelQueryRateLimitMapper.xml PaymentTaskCenterMapper.xml && git diff --check` | 通过 | 两份 Mapper XML 结构合法，H2 MySQL 兼容模式可执行 |

### 138.3 当前判断

1. `PAYMENT-1027` 已具备独立审计事实、窗口指标和站内阈值告警，限流容量问题与真实渠道质量问题可以分开观察。
2. H2 MySQL 兼容模式验证不替代生产 MySQL 并发压测；默认 `60` 秒、单渠道 `100` 次的参数仍需结合真实渠道配额与实例规模校准。
3. 当前告警仍是任务中心站内告警，不代表企业微信、短信或邮件已经真实送达；真实 `gateway-access` 运行态联调仍未完成。
4. 本轮没有新增正式审批、发布后现场核验或稳定观察结束事实，`master -> release/*` 仍不放行。

## 139. 2026-09-08 主动查单监控告警 outbox 验证

### 139.1 本轮补强

1. 新增 `PAYMENT_QUERY_MONITOR_ALERT`，按现有失败笔数、最小样本失败率和限流拒绝次数阈值生成两类 P1 `IN_APP_OUTBOX` 告警。
2. 人工入口 `POST /api/payment-task-center/generate-query-monitor-alerts` 与自动调度共用任务租约；自动任务默认每 `300000ms` 扫描一次。
3. 新增同类未确认告警冷却去重，默认 `60` 分钟，避免分钟级调度持续生成相同告警；任务日志记录阈值命中、实际生成和冷却抑制数量。
4. 新告警复用现有 IM、SMS、EMAIL 派发、失败重试和供应商回执链路；本轮未配置或调用真实企业微信、短信、邮件供应商。
5. `admin-web` 新增“主动查单监控告警”操作卡与 V1.17 任务矩阵，并修复宽表格撑开主页面导致的桌面横向溢出。

### 139.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 任务中心定向测试 | `mvn -Dtest=PaymentTaskCenterServiceImplTest,PaymentTaskCenterControllerTest,PaymentCompensationSchedulerTest,PaymentTaskCenterMapperIntegrationTest test` | 通过 | `39` 个用例通过，覆盖阈值命中、P1 outbox 字段、60 分钟冷却抑制、人工租约、控制器入口、自动调度和 H2 MySQL 兼容 SQL |
| 后端全量测试 | `mvn test` | 通过 | `297` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-query-monitor-alert-v1-full --emptyOutDir` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 浏览器静态检查 | `http://127.0.0.1:4176/payment-task-center` | 通过 | 新任务卡、按钮和 V1.17 矩阵正常渲染；桌面主页面横向溢出已收敛；后端 `18081` 未启动，API 显示 `500 [PAYMENT-CLIENT]`，不记录为接口联调完成 |
| Mapper XML 与补丁检查 | `xmllint --noout PaymentTaskCenterMapper.xml && git diff --check` | 通过 | XML 结构合法，冷却查询已在 H2 MySQL 兼容模式执行 |

### 139.3 当前判断

1. 主动查单失败趋势与限流容量告警已从页面提示升级为可审计 outbox，并可进入现有多通道派发和回执链路。
2. 当前完成的是 outbox 生成、冷却去重和可配置外发代码链路，不代表真实企业微信、短信或邮件已经送达。
3. 生产 MySQL 并发压测、真实 `gateway-access` 运行态联调、通知供应商配置与实际送达回执仍未完成。
4. 本轮没有新增正式审批、发布后现场核验或稳定观察结束事实，`master -> release/*` 仍不放行。

## 140. 2026-09-08 异常告警 outbox 自动派发调度验证

### 140.1 本轮补强

1. 将既有但未被调度器调用的 `runAutoDispatchIssueAlerts()` 接入 `PaymentCompensationScheduler`，补齐 outbox 自动生成后的定时派发入口。
2. 新增 `HSP_PAYMENT_ISSUE_ALERT_AUTO_DISPATCH_ENABLED`，默认 `false`；只有确认通知供应商配置和接收范围后才显式开启。
3. 新增 `HSP_PAYMENT_ISSUE_ALERT_AUTO_DISPATCH_DELAY_MS`，默认 `60000ms`；自动入口继续复用 `PAYMENT_ISSUE_ALERT_DISPATCH` 租约、重试、供应商失败切换和回执状态机。
4. 任务中心页面口径升级为 V1.18，明确自动派发可配置且默认关闭。

### 140.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 自动派发定向测试 | `mvn -Dtest=PaymentCompensationSchedulerTest,PaymentTaskCenterServiceImplTest,PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `58` 个用例通过，覆盖开关启用时委派、关闭时不派发，以及既有租约、重试、供应商切换和回执链路 |
| 后端全量测试 | `mvn test` | 通过 | `299` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-alert-auto-dispatch-v1 --emptyOutDir` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 浏览器静态检查 | `http://127.0.0.1:4176/payment-task-center` | 通过 | V1.18 标识和自动派发开关边界正常渲染；后端 `18081` 未启动，API 仍显示 `500 [PAYMENT-CLIENT]`，不记录为接口联调完成 |
| 补丁检查 | `git diff --check` | 通过 | 代码和文档无空白符错误 |

### 140.3 当前判断

1. outbox 已具备“阈值自动生成 -> 可配置自动派发 -> 重试/主备切换 -> 供应商回执回写”的完整代码编排，不再必须依赖人工点击触发派发。
2. 自动派发默认关闭，本轮没有启用真实 webhook，也没有产生企业微信、短信或邮件实际送达证据。
3. 真实通知供应商配置、接收人核验、运行态派发和实际送达回执仍属于外部联调缺口。
4. 本轮没有新增正式审批、发布后现场核验或稳定观察结束事实，`master -> release/*` 仍不放行。

## 141. 2026-09-08 异常告警派发与回执积压可观测性验证

### 141.1 本轮补强

1. 任务中心总览新增来源 outbox 待派发数、其中派发异常数，以及供应商已受理待回查数，补齐自动派发开启后的积压观测入口。
2. 待派发统计仅包含待确认的 `IN_APP_OUTBOX` 来源记录，状态限定为已生成、部分失败或派发失败；待回查仅包含具备来源告警号、供应商状态为 `ACCEPTED` 且待确认的外发记录。
3. 新增 `PAYMENT_ISSUE_ALERT_DISPATCH_BACKLOG` 与 `PAYMENT_ISSUE_ALERT_RECEIPT_BACKLOG`：派发异常或待派发达到 `10` 条时升级 P1，少量积压为 P2，无积压为 P3。
4. `admin-web` 增加“异常告警待派发”“异常告警待回查”指标卡并升级为 V1.19；运维关注宽表改为局部滚动，移除页面级 `1280px` 硬下限，当前应用窗口不再出现横向溢出。

### 141.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| Mapper、服务与派发定向测试 | `mvn -Dtest=PaymentTaskCenterMapperIntegrationTest,PaymentTaskCenterServiceImplTest,PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `53` 个用例通过，覆盖已生成、部分失败、已确认、ACCEPTED、DELIVERED 的统计边界，以及派发 P1、回查 P2 告警 |
| 后端全量测试 | `mvn test` | 通过 | `301` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-alert-observability-v1 --emptyOutDir` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 浏览器静态检查 | `http://127.0.0.1:4176/payment-task-center` | 通过 | V1.19、两张积压指标卡和局部宽表滚动正常渲染，当前应用窗口无页面级横向溢出；后端 `18081` 未启动，API 显示 `500 [PAYMENT-CLIENT]`，不记录为接口联调完成 |
| Mapper XML 与补丁检查 | `xmllint --noout PaymentTaskCenterMapper.xml && git diff --check` | 通过 | XML 结构合法，文档和代码无空白符错误 |

### 141.3 当前判断

1. 任务中心已能区分“来源 outbox 等待派发”“派发已出现异常”和“供应商已受理等待回执”，自动派发开启后不再只能依赖明细台人工发现积压。
2. 当前统计来自数据库派发状态，不等同于真实企业微信、短信或邮件已经送达；本轮没有新增真实通知供应商回执证据。
3. 生产 MySQL 容量压测、真实通知供应商配置、接收人核验和运行态派发仍属于后续外部联调缺口。
4. 本轮没有新增正式审批、发布后现场核验或稳定观察结束事实，`master -> release/*` 仍不放行。

## 142. 2026-09-08 异常告警积压老化监控验证

### 142.1 本轮补强

1. 任务中心总览新增待派发与待回查积压的最老等待分钟数，并同步返回两类数量阈值和等待时长阈值，保证管理端展示与后端判定使用同一配置事实。
2. 派发积压在存在派发异常、数量达到阈值或单条最老等待达到阈值时升级 P1；回执积压在数量或单条最老等待达到阈值时升级 P1，避免低数量告警长期滞留却始终只判 P2。
3. 默认阈值为待派发 `10` 条/`15` 分钟、待回查 `10` 条/`30` 分钟，分别由四项 `HSP_PAYMENT_ISSUE_ALERT_*` 环境变量配置，服务内统一限制为至少 `1`。
4. Mapper 老化统计沿用 V1.19 的可操作积压过滤口径；已确认来源 outbox、已送达或已确认外发记录即使创建时间更早，也不参与最老等待计算。
5. `admin-web` 升级为 V1.20，指标卡和严重等级矩阵展示动态数量/等待阈值；补充 `900px` 与 `520px` 响应式布局，窄屏下导航改为顶部横向滚动，主内容不再受侧栏挤压。

### 142.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| Mapper 与服务定向测试 | `mvn -Dtest=PaymentTaskCenterMapperIntegrationTest,PaymentTaskCenterServiceImplTest test` | 通过 | `30` 个用例通过；待派发最老 `45` 分钟、待回查最老 `65` 分钟，已确认和已送达的更老记录被排除；单条积压达到 `15/30` 分钟即升级 P1 |
| 后端全量测试 | `mvn test` | 通过 | `302` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-alert-aging-v1 --emptyOutDir` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 浏览器静态检查 | `http://127.0.0.1:4176/payment-task-center` | 通过 | V1.20、动态阈值文案和窄屏单列布局正常渲染，无页面级横向溢出；顶部导航自身横向滚动符合设计；后端 `18081` 未启动，API 显示 `500 [PAYMENT-CLIENT]`，不记录为接口联调完成 |
| Mapper XML 与补丁检查 | `xmllint --noout PaymentTaskCenterMapper.xml && git diff --check` | 通过 | XML 结构合法，代码与文档无空白符错误 |

### 142.3 当前判断

1. 异常告警积压已从单一数量阈值升级为数量与老化时长双阈值，能够及时暴露低数量但长时间未派发或未回执的记录。
2. 当前最老等待时长来自数据库记录，并不等同于真实通知送达耗时；本轮没有配置或调用真实企业微信、短信、邮件供应商。
3. 生产 MySQL 容量压测、真实通知供应商配置、接收人核验、运行态派发和实际送达回执仍属于后续外部联调缺口。
4. 本轮没有新增正式审批、发布后现场核验或稳定观察结束事实，`master -> release/*` 仍不放行。

## 143. 2026-09-08 渠道短窗健康路由熔断验证

### 143.1 本轮补强

1. 新增按渠道聚合的主动查单健康快照，仅统计配置窗口内的 `已查单 / 查单失败` 事实；窗口外和其他通知状态不参与路由健康判断。
2. 健康快照在服务层按既有渠道规范化规则再次合并，兼容 `wx_h5 / wx_jsapi / WX_H5` 等历史编码，避免同一渠道事实被拆散后低估风险。
3. 路由健康开关默认关闭；开启后，失败笔数达到阈值，或样本达到最低要求且失败率达到阈值时熔断该渠道。默认复用 `24` 小时、失败 `5` 笔、样本 `10` 笔且失败率 `30%` 的主动查单监控阈值。
4. 规则目标渠道熔断时切换到同时满足场景、健康和日限额约束的 fallback，并以 `_HEALTH_FALLBACK` 和“短窗健康熔断”说明留痕；显式指定不健康渠道返回 `PAYMENT-1010`；支付方式默认路由跳过不健康候选。
5. 路由总览接口返回实际熔断开关和四项阈值，`admin-web` 路由执行页升级为健康治理 V1.1 并直接展示后端口径。

### 143.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 路由健康定向测试 | `mvn -Dtest=PaymentChannelRoutingServiceImplTest,PaymentChannelQueryHealthMapperIntegrationTest,PaymentRouteExecutionServiceImplTest test` | 通过 | `11` 个用例通过，覆盖失败率熔断、低样本失败笔数熔断、规则 fallback、显式渠道拒绝、默认候选跳过、历史编码归一化和总览配置回传 |
| 后端全量测试 | `mvn test` | 通过 | `306` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-route-health-v1 --emptyOutDir` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 浏览器静态检查 | `http://127.0.0.1:4176/payment-routes` | 通过 | “路由健康治理 V1.1”和默认关闭状态正常渲染；后端 `18081` 未启动，API 显示 `500 [PAYMENT-CLIENT]`，不记录为接口联调完成 |
| Mapper XML 与补丁检查 | `xmllint --noout PaymentMapper.xml && git diff --check` | 通过 | 短窗聚合 SQL 在 H2 MySQL 兼容模式执行，XML 结构与代码文档空白符检查通过 |

### 143.3 当前判断

1. 支付提交路由已具备基于真实内部查单事实的轻量短窗健康熔断，不再只依赖静态启停、场景和日限额。
2. 开关默认关闭；启用前必须确认每条关键规则的 fallback 覆盖，并按真实渠道容量、交易峰谷和故障样本校准阈值。
3. 当前能力不等同于真实网关探活或综合健康评分，本轮没有真实渠道凭证、生产流量或运行态熔断证据。
4. 本轮没有新增正式审批、发布后现场核验或稳定观察结束事实，`master -> release/*` 仍不放行。

## 144. 2026-09-08 支付方式默认渠道稳定加权路由验证

### 144.1 本轮补强

1. `t_payment_channel_config` 新增 `route_weight`，默认值为 `100`；配置总览、管理端渠道表格和治理快照导出统一展示该字段，并提供存量环境迁移脚本。
2. 支付提交使用 `paymentOrderId` 作为稳定路由键。未命中规则且未显式指定渠道时，先按支付方式、场景、短窗健康和单日限额过滤，再以 JDK `CRC32` 映射权重区间。
3. 相同支付单与相同候选配置会稳定命中同一渠道；`routeWeight=0` 仅将渠道从默认候选池摘流，不覆盖规则目标或用户显式指定渠道。
4. 加权命中以 `PAYMENT_METHOD_WEIGHTED` 和“命中权重/候选总权重”写入路由记录；未携带稳定路由键的兼容调用仍保持原优先级选择。
5. `admin-web` 路由执行页升级为“路由健康与权重治理 V1.2”，配置中心补充路由权重列。

### 144.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 加权路由定向测试 | `mvn -Dtest=PaymentChannelRoutingServiceImplTest test` | 通过 | `10` 个用例通过，覆盖稳定路由键、不同权重区间、零权重摘流，并回归规则、显式渠道、日限额和健康熔断 |
| 后端全量测试 | `mvn test` | 通过 | `308` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-route-weight-v1 --emptyOutDir` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 浏览器静态检查 | `http://127.0.0.1:4176/payment-routes` | 通过 | “路由健康与权重治理 V1.2”和权重留痕提示正常渲染；后端 `18081` 未启动，API 显示 `500 [PAYMENT-CLIENT]`，不记录为接口联调完成 |
| Mapper XML 与补丁检查 | `xmllint --noout PaymentConfigMapper.xml PaymentMapper.xml && git diff --check` | 通过 | XML 结构合法，代码与文档无空白符错误 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | `test -> master = 是`、`release/* = 否`；退出码 `2` 为预期 release 阻断，四项真实治理事实仍未形成 |

### 144.3 当前判断

1. 默认渠道选择已从单纯优先级首选升级为支付单级稳定加权分流，并保留规则路由、显式渠道和既有可用性约束的优先语义。
2. 当前权重为静态配置，尚未根据实时容量、成功率、成本或渠道配额自动调节；调整权重后，仅保证新决策按新配置执行，不保证跨配置版本命中不变。
3. 本轮没有启动后端、接入真实渠道流量或形成运行态分布统计，因此不把静态页面与单元测试记为真实渠道分流验证。
4. 本轮没有新增正式审批、发布后现场核验或稳定观察结束事实，`master -> release/*` 仍不放行。

## 145. 2026-09-08 复杂路由规则表达式验证

### 145.1 本轮补强

1. 新增独立 `PaymentRouteExpressionEvaluator`，使用递归下降解析替换原有字符串分段，支持嵌套括号及 `NOT > AND > OR` 布尔优先级。
2. 字符串字段支持 `= / != / IN`，金额字段支持 `= / != / > / >= / < / <= / IN`；新增 `payScene` 条件，并继续支持支付方式、客户类型、请求渠道和终端字段。
3. 关键字大小写不敏感，带空格的值可使用单引号或双引号；渠道、终端和客户类型继续遵循既有归一化口径。
4. 括号缺失、未知字段、非法金额和不支持的比较组合统一按规则“不命中”处理，路由服务继续评估后续规则，避免单条坏配置阻断支付提交。
5. 配置中心增加表达式能力与失败语义提示，路由执行页升级为“路由健康、权重与表达式治理 V1.3”。

### 145.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 表达式与路由定向测试 | `mvn -Dtest=PaymentRouteExpressionEvaluatorTest,PaymentChannelRoutingServiceImplTest test` | 通过 | `16` 个用例通过，覆盖括号、布尔优先级、`NOT / IN / !=`、全部金额比较、带空格引号值、上下文字段归一化、坏规则跳过和既有路由能力 |
| 后端全量测试 | `mvn test` | 通过 | `314` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-route-expression-v1 --emptyOutDir` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 浏览器静态检查 | `http://127.0.0.1:4176/payment-routes`、`/payment-config` | 通过 | V1.3 标识、路由权重列和表达式语法提示正常渲染；后端 `18081` 未启动，API 显示 `500 [PAYMENT-CLIENT]`，不记录为接口联调完成 |
| 补丁与 XML 检查 | `git diff --check && xmllint --noout PaymentConfigMapper.xml PaymentMapper.xml` | 通过 | 代码文档无空白符错误，既有 Mapper XML 结构合法 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | `test -> master = 是`、`release/* = 否`；退出码 `2` 为预期 release 阻断，四项真实治理事实仍未形成 |

### 145.3 当前判断

1. 路由表达式已具备组合条件的明确优先级和安全失败边界，不再因平面字符串切分而限制复杂业务规则，也不会因单条非法配置直接阻断支付提交。
2. 当前规则仍由初始化数据和启停接口治理，尚未提供后台新增、编辑、保存前语法校验、命中模拟和规则版本回滚。
3. 本轮没有启动后端或接入真实渠道，不产生运行态规则命中与真实网关探活证据。
4. 本轮没有新增正式审批、发布后现场核验或稳定观察结束事实，`master -> release/*` 仍不放行。

## 146. 2026-09-08 路由规则控制面 V1.4 验证

### 146.1 本轮补强

1. 支付配置中心新增路由规则创建和编辑接口，规则编码编辑时保持不变，并在事务内返回最新配置总览。
2. 保存前校验规则必填字段、表达式语法与字段、目标/兜底渠道存在性、目标兜底冲突和非负优先级；非法配置在写库前拒绝。
3. 新增表达式校验与样例命中模拟接口，复用运行态求值器及支付方式、请求渠道、场景、终端、金额、客户类型归一化口径，不产生规则写入或路由记录。
4. 管理端新增规则表单、渠道选择、编辑回填、保存、重置和样例模拟；修复优先级 `0` 被默认值覆盖及保存成功提示被立即清空的问题。
5. 接口、设计、需求、测试方案、差距清单和页面交互说明同步升级到路由规则控制面 V1.4，并新增 `PAY-TC-027`。

### 146.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 路由控制面定向测试 | `mvn -Dtest=PaymentRouteExpressionEvaluatorTest,PaymentConfigServiceImplTest,PaymentConfigControllerTest test` | 通过 | `42` 个用例通过，覆盖创建、编辑、重复编码、非法表达式、渠道冲突、负优先级、样例命中模拟和控制器入口 |
| 后端全量测试 | `mvn test` | 通过 | `320` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-route-rule-control-v1 --emptyOutDir` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 浏览器静态检查 | `http://127.0.0.1:4176/payment-config` | 通过 | 窄视口下新增/编辑表单字段、按钮和规则表头正常渲染，无明显页面级横向溢出；后端 `18081` 未启动，API 显示 `500 [PAYMENT-CLIENT]`，不记录为接口联调完成 |
| 补丁与 XML 检查 | `git diff --check && xmllint --noout PaymentConfigMapper.xml` | 通过 | 代码与文档无空白符错误，Mapper XML 结构合法 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | `test -> master = 是`、`release/* = 否`；退出码 `2` 为预期 release 阻断，四项真实治理事实仍未形成 |

### 146.3 当前判断

1. 路由规则已从初始化数据加启停治理升级为可新增、可编辑、可校验和可模拟的轻量控制面，非法规则不会通过本入口进入运行态。
2. 样例模拟只验证单条表达式与输入上下文，不执行完整优先级竞争、渠道健康、日限额或稳定加权决策，也不等同于真实支付链路联调。
3. 规则版本、变更审计、冲突检测、审批发布和回滚仍未落地；真实网关探活与运行态命中证据仍属于外部联调缺口。
4. 本轮没有新增正式审批、发布后现场核验或稳定观察结束事实，`master -> release/*` 仍不放行。

## 147. 2026-09-08 路由规则版本留痕与回滚 V1.5 验证

### 147.1 本轮补强

1. 新增 `t_payment_route_rule_version` 不可变快照表，按规则独立维护递增版本号，记录完整规则配置、变更类型、回滚来源版本、变更原因和创建时间。
2. 新增、编辑、启停和回滚均在原事务内追加 `CREATE / UPDATE / STATUS_CHANGE / ROLLBACK` 快照；存量无版本规则首次变更前自动补 `BASELINE`。
3. 更新、启停和回滚先锁定当前规则行，再计算下一版本号，降低同一规则并发修改导致版本覆盖的风险。
4. 新增历史版本查询与指定版本回滚接口；回滚重新校验表达式、渠道和优先级，恢复配置后生成新版本，不修改或删除历史快照。
5. 管理端增加变更原因、版本历史表和二次确认回滚，配置中心标识升级为 V1.5；异步加载使用请求序号隔离陈旧响应，关闭版本区时不会以空规则编码重新加载；版本记录不包含虚构操作人或审批事实。
6. 新增幂等迁移脚本，为既有运行库建版本表并补存量规则 V1 基线；同步数据库、接口、设计、测试、需求、差距和页面交互文档，新增 `PAY-TC-028`。

### 147.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 路由版本定向测试 | `mvn -Dtest=PaymentConfigRouteRuleVersionMapperIntegrationTest,PaymentConfigServiceImplTest,PaymentConfigControllerTest test` | 通过 | `45` 个用例通过，覆盖创建/编辑版本、存量基线、状态变更、历史查询、回滚来源、非法历史快照拒绝、超长原因拒绝和控制器入口 |
| Mapper SQL 集成测试 | `PaymentConfigRouteRuleVersionMapperIntegrationTest` | 通过 | H2 MySQL 兼容模式实际执行 `FOR UPDATE`、最大版本查询、快照插入、倒序历史查询和主配置恢复更新 |
| 后端全量测试 | `mvn test` | 通过 | `327` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-route-version-v1 --emptyOutDir` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 浏览器静态检查 | `http://127.0.0.1:4176/payment-config` | 通过 | V1.5 标识、变更原因和既有规则表单正常渲染；后端 `18081` 未启动，版本列表及回滚 API 未做运行态联调，不记录为接口联调完成 |
| 补丁与 XML 检查 | `git diff --check && xmllint --noout PaymentConfigMapper.xml` | 通过 | 代码、迁移与文档无空白符错误，Mapper XML 结构合法 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | `test -> master = 是`、`release/* = 否`；退出码 `2` 为预期 release 阻断，四项真实治理事实仍未形成 |

### 147.3 当前判断

1. 路由规则已具备可追溯、可恢复且不覆盖历史的轻量版本治理，配置误改后不再只能依赖人工 SQL 反向修复。
2. 当前并发控制范围为单规则行；跨规则优先级冲突检测、规则集合一致性校验、双人复核与灰度发布仍未落地。
3. 版本快照和本地回滚测试不等于生产审批、真实渠道验证或发布演练；后端未启动，因此本轮没有形成运行态 API 与真实流量证据。
4. 本轮没有新增正式审批、发布后现场核验或稳定观察结束事实，`master -> release/*` 仍不放行。

## 148. 2026-09-08 路由规则冲突检测 V1.6 验证

### 148.1 本轮补强

1. 新增 `GET /api/payment-config/route-rules/conflicts`，只扫描当前启用规则并返回无效表达式、确定路由分歧、重复、遮蔽和同优先级待复核项，不修改规则或运行态路由。
2. 精确同表达式检测复用求值器规范化能力；匹配场景按 `/` 分隔的 OR 集合求交与判断包含关系，并统一 `mobile/mobile_web/H5`、`web/pc_web/PC`、`app_native/APP` 场景别名。
3. 同表达式不同优先级按场景包含关系区分 `SHADOWED_RULE` 与 `PARTIAL_SHADOW`，避免把仍可在其他场景生效的规则误报为永不生效。
4. 同场景同优先级但表达式不同只返回 `PRIORITY_COLLISION / REVIEW`，不伪装成表达式已被证明冲突；当前静态检测不覆盖任意不同布尔表达式的完整 SAT 或可满足性证明。
5. 管理端增加“检测冲突”按钮及加载、空、失败和结构化结果状态，配置中心标识升级为 V1.6；同步接口、设计、测试、需求、差距和页面交互文档，新增 `PAY-TC-029`。

### 148.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 路由冲突定向测试 | `mvn -Dtest=PaymentRouteExpressionEvaluatorTest,PaymentConfigServiceImplTest,PaymentConfigControllerTest test` | 通过 | `52` 个用例通过，覆盖表达式规范化、七类检测结果、场景别名与包含关系、停用规则排除和控制器入口 |
| 后端全量测试 | `mvn test` | 通过 | `331` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-route-conflict-v1 --emptyOutDir` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 浏览器静态检查 | `http://127.0.0.1:4178/payment-config` | 通过 | V1.6 标识、“检测冲突”入口和 API 失败提示正常渲染；`462px` 窄视口页面宽度未溢出且控件无交叠；后端 `18081` 未启动，冲突接口未做运行态联调 |
| 补丁与 XML 检查 | `git diff --check && xmllint --noout PaymentConfigMapper.xml` | 通过 | 代码和文档无空白符错误，Mapper XML 结构合法 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | `test -> master = 是`、`release/* = 否`；退出码 `2` 为预期 release 阻断，候选说明正式化、正式签字、现场核验和稳定观察结束结论仍未形成 |

### 148.3 当前判断

1. 路由规则已具备不改变运行态的结构化冲突排查入口，能够识别确定性配置风险，并把无法静态证明的同优先级表达式关系明确留给人工复核。
2. 当前检测只覆盖启用规则、精确同表达式和场景集合关系，不等同于规则集合的完整语义证明，也不会自动阻断保存或发布。
3. 后端未启动，本轮没有形成运行态 API、真实支付流量或真实网关探活证据；静态页面验证不计为接口联调完成。
4. 本轮没有新增双人复核、正式审批、发布后现场核验或稳定观察结束事实，`master -> release/*` 仍不放行。

## 149. 2026-09-08 路由规则 maker-checker V1.7 验证

### 149.1 本轮补强

1. 新增 `t_payment_route_rule_review` 候选申请表、DTO、Mapper 和幂等迁移脚本，保存规则完整候选快照、基线版本、申请/复核声明、意见和状态。
2. 新增复核申请查询、提交、通过和驳回接口；提交与驳回不改变运行态，通过后才写规则并追加 `REVIEW_RELEASE` 版本。
3. 修改申请通过前复验基线版本、表达式、渠道和涉及候选的阻断级冲突；同一规则已有待复核申请、同人复核、陈旧基线、重复处理均被拒绝。待复核唯一占位键封住新规则并发双申请，数据库冲突会转换为明确业务错误。
4. 关闭旧的路由规则新增、编辑 HTTP 直写入口，避免绕过 maker-checker；启停和版本回滚仍保留原直接路径并明确列为后续治理缺口。
5. 管理端升级为 V1.7，新增申请人声明、必填原因、复核列表、复核人声明、意见、通过发布和驳回交互，并明确人员均为 `SELF_DECLARED`、不等同可信身份或正式签字。
6. 同步接口、设计、数据库、测试、需求、差距和页面交互文档，新增 `PAY-TC-030`；真实百分比灰度和稳定路由桶仍未实现。

### 149.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 路由复核定向测试 | `mvn -Dtest=PaymentConfigRouteRuleVersionMapperIntegrationTest,PaymentConfigServiceImplTest,PaymentConfigControllerTest test` | 通过 | `58` 个用例通过，覆盖提交不改运行态、异人通过、驳回、同人拒绝、陈旧基线、重复待复核、并发唯一冲突转换、阻断冲突、新规则发布、重复处理和 Mapper 状态更新 |
| 后端全量测试 | `mvn test` | 通过 | `341` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 浏览器静态检查 | `http://127.0.0.1:4180/payment-config` | 部分通过 | 窄视口成功渲染 V1.7 标识、候选表单、复核区、边界提示和 API 失败态；随后系统锁屏，未继续滚动截图。后端 `18081` 未启动，复核 API 未做运行态联调 |
| 补丁与 XML 检查 | `git diff --check && xmllint --noout PaymentConfigMapper.xml` | 通过 | 代码、迁移和文档无空白符错误，Mapper XML 结构合法 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | `test -> master = 是`、`release/* = 否`；候选说明正式化、正式签字、现场核验和稳定观察结束结论仍未形成 |

### 149.3 当前判断

1. 新增与修改已具备不能通过旧 HTTP 入口绕过的轻量 maker-checker，候选申请与运行态配置分离，并在发布时执行基线与阻断风险复验。
2. 当前申请人和复核人都是自声明字符串，异人校验仅能降低误操作，不能证明真实身份、权限或不可抵赖签字；本轮不形成正式审批事实。
3. 启停和回滚尚未纳入复核，真实百分比灰度、稳定分桶、真实网关探活和运行态命中证据仍缺失。
4. 本轮没有新增正式签字、发布后现场核验或稳定观察结束事实，`master -> release/*` 仍不放行。

## 150. 2026-09-09 路由规则稳定百分比灰度 V1.8 验证

### 150.1 本轮补强

1. 路由规则新增 `rolloutPercentage` 与 `rolloutSalt`，以 `CRC32(ruleCode:rolloutSalt:paymentOrderId) % 100` 形成支付单级稳定桶；存量规则按 `100% / stable-v1` 保持原行为。
2. 场景与表达式命中后，仅在 `bucket < rolloutPercentage` 时进入当前规则；桶外继续后续规则，不使用当前规则 fallback。只有入桶后目标渠道不可用，才执行原故障兜底语义。
3. 部分灰度缺支付单号时跳过，`0%` 摘流、`100%` 全量；盐值变化允许主动重排桶位，并明确作为高风险候选变更留痕。
4. 灰度字段贯穿运行配置、复核候选、版本快照、回滚、Mapper、初始化数据、增量迁移、治理导出和管理端 V1.8；比例范围和空盐值在写入前拒绝，并补齐配置表单的响应式网格，避免窄视口字段拥挤。
5. 冲突检测排除 `0%` 规则；部分灰度高优先级规则不再把相同场景的低优先级规则误判为完全遮蔽。不同盐值桶集合仍不做静态互斥证明。
6. 同步接口、设计、数据库、测试、需求、差距和页面交互文档，新增 `PAY-TC-031`。代码内稳定分桶不等于生产灰度审批、真实渠道验证或稳定观察完成。

### 150.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 灰度路由与配置定向测试 | `mvn -Dtest=PaymentChannelRoutingServiceImplTest,PaymentConfigServiceImplTest,PaymentConfigRouteRuleVersionMapperIntegrationTest test` | 通过 | `69` 个用例通过，覆盖稳定桶内外、桶外后续规则、可用 fallback 不误用、缺稳定键、0/100 边界、盐值重排、非法配置、复核/版本/回滚字段和部分遮蔽语义 |
| 后端全量测试 | `mvn test` | 通过 | `349` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build -- --configLoader runner --outDir /private/tmp/hsp-admin-web-dist-route-rule-rollout-v18 --emptyOutDir` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 管理端本地服务 | `http://127.0.0.1:4182/payment-config` | 部分通过 | Vite 开发服务持续运行，宿主环境 HTTP 返回 `200`；约 `448px` 窄视口已检查 V1.8 灰度控件、说明和响应式单列表单，无重叠或截断。当前环境无可用 Chrome，桌面宽视口未执行；后端未启动，页面如实呈现配置接口 `500` 失败态 |
| 运行态 API | `http://127.0.0.1:18081/api/payment-config` | 未执行 | 后端未启动，不记录配置、复核或真实支付路由 API 联调通过 |
| 补丁与 XML 检查 | `git diff --check && xmllint --noout PaymentConfigMapper.xml` | 通过 | 代码、迁移和文档无空白符错误，Mapper XML 结构合法 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | `test -> master = 是`、`release/* = 否`；候选说明正式化、正式签字、现场核验和稳定观察结束结论仍未形成 |

### 150.3 当前判断

1. 路由规则已具备真实执行的支付单级稳定百分比桶，且配置变化受既有 maker-checker、版本和回滚链路约束。
2. 当前能力只决定代码内规则是否承接支付单，不包含生产流量编排、渠道探活、发布指标阈值、自动回滚或观察期闭环。
3. 浏览器已完成窄视口静态视觉检查，但桌面宽视口未执行且后端未启动；本轮不形成运行态 API、真实支付流量或真实渠道联调证据。
4. 本轮没有新增可信身份、正式签字、发布后现场核验或稳定观察结束事实，`master -> release/*` 仍不放行。

## 151. 2026-09-09 路由规则启停与回滚复核 V1.9 验证

### 151.1 本轮补强

1. 路由规则 `CREATE / UPDATE / STATUS_CHANGE / ROLLBACK` 四类操作统一进入 maker-checker；启停与回滚提交后仅形成候选申请，不直接改写运行态配置。
2. 复核通过时锁定申请和当前规则，复验候选操作类型、异人自声明、基线版本、表达式、渠道与阻断级冲突，通过后才更新运行态并追加版本；驳回不改变运行态。
3. 停用作为止损操作，允许在候选引用渠道已失效时提交和批准；启用、修改与回滚仍执行完整表达式和渠道校验，避免重新放量到无效配置。
4. 回滚候选新增 `sourceVersionNo/source_version_no`，明确保存目标历史版本；批准后分别记录 `REVIEW_STATUS_CHANGE` 或 `REVIEW_ROLLBACK`，保留复核申请、目标版本与运行版本之间的追溯关系。
5. 删除旧启停直接更新 Mapper，关闭启停和回滚的 HTTP 直写路径；管理端升级为 V1.9，增加操作申请人声明与原因，并把动作入口调整为“申请启用 / 申请停用 / 申请回滚到此版本”。
6. 同步接口、设计、数据库、测试、需求、差距和页面交互文档，新增 `PAY-TC-032`。自声明 maker-checker 不等于可信身份、正式审批或生产发布授权。

### 151.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 路由复核定向测试 | `mvn -Dtest=PaymentConfigServiceImplTest,PaymentConfigControllerTest,PaymentConfigRouteRuleVersionMapperIntegrationTest test` | 通过 | `64` 个用例通过，覆盖启停和回滚提交不改运行态、异人批准、陈旧基线、重复待复核、停用止损、启用渠道复验、回滚目标版本留痕和控制器入口 |
| 后端全量测试 | `mvn test` | 通过 | `353` 个用例全部通过，`0` failures、`0` errors、`0` skipped；首次全量执行暴露并修正一处新增前置校验所需的测试夹具缺失，修正后定向与全量均通过 |
| `admin-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 管理端本地服务 | `http://127.0.0.1:4182/payment-config` | 未复验 | Vite 开发服务保持运行；本轮浏览器核验时 Mac 处于锁屏状态，未将未完成的 V1.9 视觉检查记为通过 |
| 运行态 API | `http://127.0.0.1:18081/api/payment-config` | 未执行 | 后端未启动，不记录配置、复核或真实支付路由 API 联调通过 |
| 补丁与 XML 检查 | `git diff --check && xmllint --noout PaymentConfigMapper.xml` | 通过 | 代码、迁移和文档无空白符错误，Mapper XML 结构合法 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | `test -> master = 是`、`release/* = 否`；候选说明正式化、正式签字、现场核验和稳定观察结束结论仍未形成 |

### 151.3 当前判断

1. 路由规则新增、修改、启停和回滚已统一受候选复核约束，管理端不存在这四类操作的已知直接发布入口。
2. 停用保留失效渠道场景下的止损能力；其他可能扩大或改变流量承接的操作仍在批准前复验候选有效性和基线一致性。
3. 当前申请人和复核人仍是自声明字符串，异人约束不能证明真实身份、权限或不可抵赖签字；后端未启动且锁屏阻断本轮页面复验，不形成运行态 API、真实流量或完整视觉验收证据。
4. 本轮没有新增候选说明正式化、正式签字、发布后现场核验或稳定观察结束事实，`master -> release/*` 仍不放行。

## 152. 2026-09-09 支付协议不可变版本 V1.10 验证

### 152.1 本轮补强

1. 新增 `t_payment_protocol_version`，按协议编码保存包含模板、签约要素、电子签章服务商、适用范围、正文、状态和优先级的完整不可变快照；初始化数据与幂等迁移为存量协议补 V1 `BASELINE`。
2. 协议新增、编辑、启停和回滚分别追加 `CREATE / UPDATE / STATUS_CHANGE / ROLLBACK` 版本，全部要求变更原因；运行态仍保存在 `t_payment_protocol_config`，历史版本只增不改。
3. 编辑、启停和回滚先锁定当前协议，再生成顺序版本；存量无版本协议首次变更前自动保留基线。
4. 回滚恢复目标历史完整快照并以 `sourceVersionNo` 留痕；请求携带页面所见 `baseVersionNo`，服务端发现最新版本漂移时拒绝陈旧回滚，避免覆盖并发新变更。
5. 管理端升级为 V1.10，新增协议变更原因、启停/回滚原因、版本历史和回滚入口；回滚成功后刷新当前配置与已打开历史，最新版本禁用无意义回滚。
6. 同步接口、设计、数据库、测试、需求、差距和页面交互文档，新增 `PAY-TC-033`。版本留痕解决可追溯与恢复，不代表可信审批、法律签署或真实电子签章回执。

### 152.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付协议版本定向测试 | `mvn -Dtest=PaymentConfigServiceImplTest,PaymentProtocolConfigServiceImplTest,PaymentConfigControllerTest,PaymentConfigProtocolVersionMapperIntegrationTest test` | 通过 | `71` 个用例通过，覆盖新增、编辑、启停、存量基线、历史查询、完整回滚、来源版本、陈旧基线、空原因、不存在版本、控制器入口和 MyBatis SQL |
| 后端全量测试 | `mvn test` | 通过 | `361` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 管理端视觉检查 | `http://127.0.0.1:4182/payment-config` | 未执行 | Vite 进程仍监听本地端口，但 Mac 持续锁屏，无法完成 V1.10 的窄视口与桌面视口检查，不将既有页面结论复用为本轮通过 |
| 运行态 API | `http://127.0.0.1:18081/api/payment-config` | 未执行 | 后端未启动，不记录协议版本历史或回滚接口的运行态联调通过 |
| 补丁与 XML 检查 | `git diff --check && xmllint --noout PaymentConfigMapper.xml` | 通过 | 代码、迁移和文档无空白符错误，Mapper XML 结构合法 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | `test -> master = 是`、`release/* = 否`；退出码 `2` 为预期 release 阻断，4 项正式治理事实仍未形成 |

### 152.3 当前判断

1. 支付协议已经从可编辑台账推进为可追溯、可恢复的轻量版本治理，模板版本字符串之外的完整条款事实也能随版本还原。
2. 当前回滚通过行锁和基线版本校验降低并发误覆盖风险，但新增、编辑、启停与回滚仍直接改变运行态，尚未接入 maker-checker 或可信权限。
3. 电子签章服务商仍只是配置字段，没有签署主体、签章请求、回执、证据文件、生效时间或法务审批事实；本轮不形成协议正式签署结论。
4. 后端未启动且锁屏阻断页面视觉检查，本轮不形成运行态 API 或完整视觉验收证据；`master -> release/*` 仍不放行。

## 153. 2026-09-09 支付协议 maker-checker V1.11 验证

### 153.1 本轮补强

1. 新增 `t_payment_protocol_review`，保存协议完整候选、操作类型、基线版本、回滚来源版本、申请人与复核记录；`pending_protocol_code` 唯一占位保证同一协议最多一条待复核申请，申请完成后释放占位。
2. 新增、编辑、启停和回滚统一提交 `CREATE / UPDATE / STATUS_CHANGE / ROLLBACK` 候选；删除协议直接新增、编辑 HTTP 入口，提交和驳回均不修改 `t_payment_protocol_config`。
3. 新建候选基线为空；存量候选提交时锁定协议并保存最新 `baseVersionNo`；回滚候选保存 `sourceVersionNo`。批准时重新锁定申请与协议并复验基线，陈旧申请不覆盖新版本。
4. 申请人与复核人必须不同；批准后才发布运行态并追加 `REVIEW_RELEASE / REVIEW_STATUS_CHANGE / REVIEW_ROLLBACK` 版本。两者身份可信度固定为 `SELF_DECLARED`，不形成可信审批、正式签字或法律签署事实。
5. 管理端升级为 V1.11，补齐协议申请人、独立复核人和意见、复核申请列表及批准/驳回操作；提交后保持运行态不变，批准后刷新运行态及已打开版本历史。
6. 同步数据库迁移说明、接口、设计、测试、需求、差距和页面交互文档，新增 `PAY-TC-034`。

### 153.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付协议复核定向测试 | `mvn -Dtest=PaymentConfigServiceImplTest,PaymentProtocolConfigServiceImplTest,PaymentConfigControllerTest,PaymentConfigProtocolVersionMapperIntegrationTest test` | 通过 | `74` 个用例通过，覆盖提交不改运行态、新协议批准发布、启停/回滚候选、异人批准、同人拒绝、驳回、陈旧基线、存量基线、重复待办，以及 Mapper 行锁、完成和占位释放 |
| 后端全量测试 | `mvn test` | 通过 | `364` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 管理端视觉检查 | `http://127.0.0.1:4182/payment-config` | 未执行 | Mac 持续锁屏，无法完成 V1.11 的窄视口与桌面视口检查，不复用旧版本视觉结论 |
| 运行态 API | `http://127.0.0.1:18081/api/payment-config` | 未执行 | 后端未启动，不记录协议复核接口的运行态联调通过 |
| 补丁与 XML 检查 | `git diff --check && xmllint --noout PaymentConfigMapper.xml` | 通过 | 代码、迁移和文档无空白符错误，Mapper XML 结构合法 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | `test -> master = 是`、`release/* = 否`；退出码 `2` 为预期 release 阻断，候选说明正式化、正式签字、现场核验和稳定观察结束四项事实仍未形成 |

### 153.3 当前判断

1. 支付协议四类运行态变更已统一受候选复核约束，管理端和控制器不存在已知的协议新增、编辑、启停或回滚直接发布入口。
2. 提交与驳回不改运行态；异人批准且基线有效时才发布并追加不可变版本。同协议唯一待办和完成后释放占位已由 Mapper 集成测试覆盖。
3. 当前申请人与复核人仍是自声明字符串，异人约束不能证明真实身份、权限、法务审批或不可抵赖签字；电子签章服务商仍只是配置字段。
4. 后端未启动且锁屏阻断页面视觉检查，本轮不形成运行态 API、法律签署或完整视觉验收证据；`master -> release/*` 仍不放行。

## 154. 2026-09-09 渠道返回码业务链路标准化 V1.2 验证

### 154.1 本轮补强

1. 四类支付提交适配器和四类主动查单适配器保留渠道原始返回码；提交与查单服务统一按 `channelCode + channelReturnCode` 查询 `ENABLED + ACTIVE` 映射，补充平台标准码、文案、处理建议、重试、人工介入、映射版本和命中状态。
2. 未知、停用、归档或配置读取异常统一保留原始码并降级为 `PAYMENT-CHANNEL-UNMAPPED`，不改变支付交易状态，也不自动重试；明确 `SUCCESS` 但未配置成功码时使用平台成功语义且不误报人工介入。
3. 提交标准化结果进入支付尝试响应报文，主动查单结果进入通知日志；两条链路都刷新 `t_payment_order` 最近返回码快照，后续支付单详情可稳定读取。
4. 新增非幂等迁移 `2026-09-09-add-payment-return-code-snapshot.sql`，历史支付单字段保持可空；新增 Mapper 集成测试证明快照写入、提交事务和新会话详情读回。
5. 管理端支付详情、App 收银台和结果页展示返回码标准化字段；管理端窄视口工具栏支持两列换行，修复按钮文字逐字竖排问题。
6. 同步接口、设计、测试、需求、差距、数据库和前端交互文档，新增 `PAY-TC-035`。真实渠道码表、批量导入、差异预检和自动归档仍未实现。

### 154.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 返回码标准化定向测试 | `mvn -Dtest=PaymentChannelReturnCodeStandardizerTest,PaymentChannelSubmitServiceImplTest,PaymentChannelQueryServiceImplTest,PaymentConfigReturnCodeMapperIntegrationTest,PaymentReturnCodeSnapshotMapperIntegrationTest,PaymentServiceImplTest test` | 通过 | `47` 个用例全部通过，覆盖启用/停用/归档/未知映射、读取异常降级、成功码兜底、提交/查单接入、快照写入和跨会话详情读回 |
| 后端全量测试 | `mvn test` | 通过 | `370` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| `app-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功，`32` 个模块完成转换 |
| 前端视觉检查 | `http://127.0.0.1:4183`、`http://127.0.0.1:4184` | 通过 | 已检查管理端窄视口按钮两列换行与文字完整，以及 App 结果页错误态布局；发现的按钮逐字竖排问题修正后复验通过，无明显交叠或截断 |
| 运行态 API | `http://127.0.0.1:18081` | 未执行 | 后端未启动，不记录返回码快照、支付提交、主动查单或真实渠道数据联调通过 |
| 补丁与 XML 检查 | `git diff --check && xmllint --noout PaymentMapper.xml PaymentConfigMapper.xml` | 通过 | 代码、迁移与文档无空白符错误，两份 Mapper XML 结构合法 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | `test -> master = 是`、`release/* = 否`；退出码 `2` 为预期 release 阻断，候选说明正式化、正式签字、现场核验和稳定观察结束四项事实仍未形成 |

### 154.3 当前判断

1. 渠道返回码映射已从配置台账进入支付提交与主动查单生产代码路径，标准化结果具备响应、日志和支付单最近快照三层追溯能力。
2. 标准化层不参与支付状态机判定，未知码不推断成功且配置读取异常不反向失败已发生的渠道调用，避免因运营配置问题诱发重复下单。
3. 当前验证覆盖单元、Mapper SQL、全量回归、生产构建和有限前端视觉检查；后端 `18081` 未启动，因此没有形成运行态 API、真实渠道返回码或端到端数据展示证据。
4. 本轮没有新增候选说明正式化、正式签字、发布后现场核验或稳定观察结束事实，`master -> release/*` 仍不放行。

## 155. 2026-09-09 渠道返回码 CSV 批量导入与差异预检 V1.3 验证

### 155.1 本轮补强

1. 新增返回码 CSV 差异预检与确认应用两阶段接口，固定十列表头，限制单次 `500` 行和 `512 KB`，逐行返回 `CREATE / UPDATE / UNCHANGED / INVALID` 结果。
2. 预检统一校验渠道存在性、字段长度与枚举、大小写不敏感重复键、归档记录保护、优先级和映射版本；更新业务字段但未同步改变 `mappingVersion` 时整批拒绝。
3. 应用阶段在事务内锁定全部现有映射并重算 SHA-256 基线指纹，基线漂移或任一无效行时不写入；文件缺失项不会自动归档。
4. 映射写入与导入审计批次在同一事务提交；Mapper 冲突更新分支增加归档状态防御，即使异常进入重复键分支也不会改写归档记录的业务字段或更新时间。
5. 管理端增加模板下载、CSV 文件选择、操作人自声明、导入原因、差异预检、逐行结果、确认应用和审计批次反馈；重新选择文件会立即清空旧预检结果。
6. 同步数据库、接口、设计、测试、需求、差距和页面交互文档，新增 `PAY-TC-036`。操作人字段仅为自声明审计信息，不代表可信身份、正式审批或不可抵赖签字。

### 155.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 返回码导入定向测试 | `mvn -Dtest=PaymentConfigServiceImplTest,PaymentReturnCodeImportServiceTest,PaymentConfigControllerTest,PaymentConfigReturnCodeMapperIntegrationTest test` | 通过 | `81` 个用例全部通过，覆盖两阶段入口、CSV 解析、差异分类、渠道与版本校验、大小写重复键、归档阻断、基线漂移、原子应用、审计批次和归档冲突 SQL 防御 |
| 后端全量测试 | `mvn test` | 通过 | `379` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 管理端视觉检查 | `payment-config` 页面 | 未执行 | Mac 处于锁屏状态，本轮没有完成桌面或窄视口浏览器复验，不复用旧版本视觉结论 |
| 运行态 API | `http://127.0.0.1:18081/api/payment-config/return-codes/import/*` | 未执行 | 后端 `18081` 未启动，不记录预检、应用或真实渠道码表运行态联调通过 |
| 补丁与 XML 检查 | `git diff --check && xmllint --noout PaymentConfigMapper.xml` | 通过 | 代码、迁移与文档无空白符错误，Mapper XML 结构合法 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | `test -> master = 是`、`release/* = 否`；退出码 `2` 为预期 release 阻断，候选说明正式化、正式签字、现场核验和稳定观察结束四项事实仍未形成 |

### 155.3 当前判断

1. 返回码码表已具备先预检、后应用、整批校验、并发基线保护和批次审计能力；归档项和文件缺失项不会被批量导入修改。
2. 当前锁定范围是单库返回码映射表，能够约束本服务内并发写入；不代表已完成跨系统配置发布、生产数据库迁移或真实渠道码表核验。
3. 后端未启动且锁屏阻断浏览器视觉检查，本轮不形成运行态 API、真实渠道联调或完整页面验收证据。
4. 本轮没有新增候选说明正式化、正式签字、发布后现场核验或稳定观察结束事实，`master -> release/*` 仍不放行。

## 156. 2026-09-09 渠道返回码 CSV 导入 maker-checker V1.4 验证

### 156.1 本轮补强

1. 删除公开的 `/api/payment-config/return-codes/import/apply` 直接应用入口，新增导入复核申请查询、提交、批准和驳回接口；预检仍保持只读，提交与驳回均不修改运行态。
2. 新增 `t_payment_return_code_import_review` 和幂等迁移，保存规范化候选 CSV、SHA-256、提交时基线、差异数量、申请人与复核记录；可空唯一 `pending_import_key` 保证同时最多一条待复核返回码导入，完成后释放占位。
3. 提交阶段先锁唯一待办占位、再锁返回码映射并复验基线和全部行；两个并发提交都观察到空待办时，由数据库唯一键及业务异常转换兜住第二个请求。无实际变化、无效行或陈旧基线均不生成复核申请。
4. 批准阶段先锁复核申请、再锁返回码映射，重新验证候选 SHA-256、基线、渠道与全部 CSV 行；同人复核、重复处理、摘要不一致、陈旧基线和失效候选均拒绝。
5. 映射写入、导入审计批次和复核完成处于同一事务；驳回不写映射和审计批次。管理端升级为 V1.12，增加申请列表、申请人/复核人声明、意见、批准与驳回，并在批准后才刷新运行态。
6. 同步数据库、接口、设计、测试、需求、差距和页面交互文档，新增 `PAY-TC-037`。当前人员身份固定为 `SELF_DECLARED`，不形成可信身份、正式审批或不可抵赖签字；V1.4 仅治理 CSV 批量导入，单条返回码启停仍直接修改运行态。

### 156.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 返回码导入复核定向测试 | `mvn -Dtest=PaymentReturnCodeImportServiceTest,PaymentConfigControllerTest,PaymentConfigReturnCodeMapperIntegrationTest,PaymentConfigServiceImplTest test` | 通过 | `89` 个用例全部通过：导入服务 `13`、Controller `9`、Mapper 集成 `4`、配置服务 `63`；覆盖提交不改运行态、无变化拒绝、唯一待办、异人批准、同人拒绝、驳回、重复处理拒绝、候选摘要复验、批准前基线漂移、原子应用与占位释放后重新提交 |
| 后端全量测试 | `mvn test` | 通过 | Surefire XML 实际汇总 `387` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 管理端静态可达性 | `http://127.0.0.1:4186/payment-config` | 通过 | 本轮重新请求返回 `200`；只证明 Vite 页面入口可访问，不等于交互或视觉验收 |
| 管理端视觉检查 | `payment-config` 页面 | 未执行 | Mac 处于锁屏状态，未执行 V1.12 桌面与窄视口浏览器复验，不复用旧版本视觉结论 |
| 运行态 API | `http://127.0.0.1:18081/api/payment-config/return-codes/import/*` | 未执行 | 后端 `18081` 未监听，不记录预检、复核、批准、驳回或真实渠道码表运行态联调通过 |
| 补丁与 XML 检查 | `git diff --check && xmllint --noout PaymentConfigMapper.xml` | 通过 | 代码、迁移和文档无空白符错误，Mapper XML 结构合法 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | `test -> master = 是`、`release/* = 否`；退出码 `2` 为预期 release 阻断，候选说明正式化、正式签字、现场核验和稳定观察结束四项事实仍未形成 |

### 156.3 当前判断

1. CSV 批量导入已经关闭直接应用入口，候选提交与运行态配置分离；只有异人声明批准且候选摘要、基线、渠道和全部行仍有效时才原子应用。
2. 唯一待办、固定锁顺序和数据库唯一键共同约束本服务内并发提交与处理；该结论来自服务和 Mapper 测试，不代表已执行生产数据库迁移或跨系统配置发布。
3. 单条 `/return-codes/toggle` 仍直接修改运行态，因此不能宣称全部返回码变更已具备 maker-checker。申请人与复核人也仍是自声明字符串，不能证明真实身份、权限或正式审批。
4. 后端未启动且锁屏阻断浏览器视觉检查，本轮不形成运行态 API、真实渠道联调或完整页面验收证据；`master -> release/*` 仍不放行。

## 157. 2026-09-09 渠道返回码单条启停 maker-checker V1.5 验证

### 157.1 本轮补强

1. 保留 `/api/payment-config/return-codes/toggle` URL，但将语义从直接启停改为提交状态候选；新增单条状态复核列表、批准和驳回接口，提交与驳回不修改运行态。
2. 新增 `t_payment_return_code_review`、两份全量 schema 和幂等迁移；以规范化映射键 SHA-256 作为可空唯一待办占位，同一映射同时最多一条待复核申请，完成后释放占位。
3. 渠道规范化为小写、返回码规范化为大写；不存在、已归档、状态未变化、已有待办或唯一键竞态均拒绝。候选保存完整映射基线指纹，使 CSV 导入或其他字段变化可在批准前阻断陈旧候选。
4. 批准先锁复核申请、再锁映射并复验候选状态与完整基线；仅异人声明通过才写状态，映射更新与复核完成处于同一事务。驳回、同人复核和重复处理均不修改运行态。
5. 管理端升级为 V1.13，单条按钮改为“申请启用/申请停用”，新增申请人、原因、复核人、意见和独立复核面板；批准后刷新运行态，提交与驳回只刷新复核列表。
6. 同步数据库、接口、设计、测试、需求、差距和页面交互文档，新增 `PAY-TC-038`。人员身份固定为 `SELF_DECLARED`，只能证明输入了不同声明，不能证明可信身份、授权、正式审批或不可抵赖签字。

### 157.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 返回码状态复核扩大定向测试 | `mvn -Dtest=PaymentReturnCodeMappingConfigServiceImplTest,PaymentReturnCodeImportServiceTest,PaymentConfigControllerTest,PaymentConfigReturnCodeMapperIntegrationTest,PaymentConfigServiceImplTest test` | 通过 | Surefire XML 实际汇总 `98` 个用例全部通过，覆盖提交不改运行态、唯一待办、归档阻断、同人拒绝、异人批准、驳回、重复处理、完整基线漂移、Mapper 占位释放及 CSV 导入回归 |
| 后端全量测试 | `mvn test` | 通过 | Surefire XML 实际汇总 `395` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 管理端静态可达性 | `http://127.0.0.1:4187/payment-config` | 通过 | 本轮重新启动 Vite 后请求返回 `200`；只证明页面入口可访问，不等于交互或视觉验收 |
| 管理端视觉检查 | `payment-config` 页面 | 未执行 | Mac 处于锁屏状态，未执行 V1.13 桌面与窄视口浏览器复验，不复用旧版本视觉结论 |
| 运行态 API | `http://127.0.0.1:18081/api/payment-config/return-codes/*` | 未执行 | 后端 `18081` 未监听，不记录候选提交、批准、驳回或真实渠道码表运行态联调通过 |
| 补丁与 XML 检查 | `git diff --check && xmllint --noout PaymentConfigMapper.xml` | 通过 | 代码、迁移和文档无空白符错误，Mapper XML 结构合法 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | `test -> master = 是`、`release/* = 否`；退出码 `2` 为预期 release 阻断，候选说明正式化、正式签字、现场核验和稳定观察结束四项事实仍未形成 |

### 157.3 当前判断

1. 返回码 CSV 导入继续由 V1.4 复核链路治理，单条启停由 V1.5 独立复核链路治理；管理端、控制器和服务层不存在已知的返回码单条直接启停路径。
2. 单条候选以完整映射指纹保护批准基线，CSV 导入或其他字段变更会使旧候选失效；该结论来自单元和 Mapper 集成测试，不代表已执行生产数据库迁移或并发压测。
3. 当前申请人和复核人仍是自声明字符串，异人约束不能证明真实身份、权限、正式审批或不可抵赖签字。
4. 后端未启动且锁屏阻断浏览器视觉检查，本轮不形成运行态 API、真实渠道联调或完整页面验收证据；`master -> release/*` 仍不放行。

## 158. 2026-09-09 支付渠道启停 maker-checker V1.2 验证

### 158.1 本轮补强

1. 保留 `/api/payment-config/channels/toggle` URL，但将语义从直接启停改为提交渠道状态候选；新增渠道状态复核列表、批准和驳回接口，提交与驳回均不修改运行态。
2. 新增 `t_payment_channel_review`、两份全量 schema 和幂等迁移；以规范化小写渠道编码作为可空唯一待办占位，同一渠道同时最多一条待复核申请，完成后释放占位。
3. 渠道不存在、状态未变化、已有待办或唯一键竞态均拒绝；候选保存覆盖支付方式、商户与应用标识、密钥和公钥、签名与回调、证书、时效、风控、场景、限额、优先级及路由权重的完整基线指纹。
4. 批准先锁复核申请、再锁渠道并复验候选状态与完整基线；仅异人声明通过才写渠道状态，状态更新与复核完成处于同一事务。展示 DTO 不暴露渠道密钥原文，驳回、同人复核和重复处理均不修改运行态。
5. 管理端升级为 V1.14，渠道按钮改为“申请启用/申请停用”，新增申请人、原因、复核人、意见和独立复核面板；批准后刷新运行态，提交与驳回只刷新复核列表。
6. 同步数据库、接口、设计、测试、需求、差距和页面交互文档，新增 `PAY-TC-039`。人员身份固定为 `SELF_DECLARED`，只能证明输入了不同声明，不能证明可信身份、授权、正式审批或不可抵赖签字。

### 158.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付渠道状态复核定向测试 | `mvn -Dtest=PaymentChannelStatusReviewServiceImplTest,PaymentConfigChannelReviewMapperIntegrationTest,PaymentConfigControllerTest,PaymentConfigServiceImplTest test` | 通过 | Surefire XML 实际汇总 `82` 个用例全部通过，覆盖提交不改运行态、唯一待办、相同状态拒绝、唯一键竞态转换、异人批准、同人拒绝、驳回、重复处理、完整基线漂移、Mapper 占位释放及释放后重新提交 |
| 后端全量测试 | `mvn test` | 通过 | Surefire XML 实际汇总 `404` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 管理端静态可达性 | `http://127.0.0.1:4187/payment-config` | 通过 | 本轮重新请求返回 `200`；只证明 Vite 页面入口静态可达，不等于交互或视觉验收 |
| 管理端视觉检查 | `payment-config` 页面 | 未执行 | 本轮未执行 V1.14 桌面与窄视口浏览器复验，不复用旧版本视觉结论 |
| 运行态 API | `http://127.0.0.1:18081/api/payment-config/channels/*` | 未执行 | 后端 `18081` 未监听，不记录候选提交、批准、驳回或真实渠道运行态联调通过 |
| 补丁与 XML 检查 | `git diff --check && xmllint --noout PaymentConfigMapper.xml` | 通过 | 代码、迁移和文档无空白符错误，Mapper XML 结构合法 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | `test -> master = 是`、`release/* = 否`；候选说明正式化、正式签字、发布后现场核验和稳定观察结束结论四项真实事实仍未形成 |

### 158.3 当前判断

1. 支付渠道启停已由候选复核链路治理；管理端、控制器和服务层不存在已知的渠道状态直接启停路径。
2. 渠道候选以完整配置指纹保护批准基线，其他字段变化会使旧候选失效；固定锁顺序、唯一待办和数据库唯一键共同约束本服务内并发提交与处理。该结论来自单元和 Mapper 集成测试，不代表已执行生产数据库迁移或并发压测。
3. 当前申请人和复核人仍是 `SELF_DECLARED` 自声明字符串，异人约束不能证明真实身份、权限、正式审批或不可抵赖签字。
4. 后端 `18081` 未监听且本轮未执行浏览器视觉检查，因此不形成运行态 API、真实渠道联调或完整页面验收证据；`test -> master = 是`、`release/* = 否`，正式放行仍缺候选说明正式化、正式签字、发布后现场核验和稳定观察结束结论。

## 159. 2026-09-09 支付渠道新增/编辑 maker-checker V1.3 验证

### 159.1 本轮补强

1. 复用 `t_payment_channel_review` 扩展 `CREATE / UPDATE / STATUS_CHANGE` 三类操作，共享按渠道编码唯一的待复核占位；新增和编辑提交均只保存完整候选，不直接改变渠道运行态。
2. 新增渠道必须提交验签密钥；编辑时空密钥或空公钥由服务端继承提交时运行配置。签名算法仅支持 `HMAC-SHA256 / RSA2`，RSA2 必须配置公钥，回调通知地址必须是带主机的有效 HTTPS 地址。
3. 候选统一校验必填文本、正数验签窗口与退款时效、非负限额和优先级，以及 `0-100` 路由权重；批准时从复核快照重建并重新校验完整候选，不信任提交阶段校验结果。
4. 批准先锁复核申请，再锁渠道运行配置。CREATE 复验渠道编码仍不存在，UPDATE 和 STATUS_CHANGE 复验完整运行态基线；运行配置写入与复核完成位于同一事务。
5. 查询 DTO 和列表 SQL 只返回密钥掩码及公钥配置状态，不返回密钥或公钥原文。管理端升级为 V1.15，增加完整渠道新增/编辑表单、非敏感字段回填、编辑空敏感字段保留语义和三类复核操作展示。
6. 新增一次性、非幂等迁移 `2026-09-09-expand-payment-channel-review.sql`：先以当前渠道配置回填历史 STATUS_CHANGE 申请，再恢复候选必填列约束；生产执行前仍需备份、变更审批和迁移窗口确认。

### 159.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付渠道配置复核扩大定向测试 | `mvn -Dtest=PaymentChannelStatusReviewServiceImplTest,PaymentConfigChannelReviewMapperIntegrationTest,PaymentConfigControllerTest,PaymentConfigServiceImplTest test` | 通过 | `88` 个用例全部通过，覆盖 CREATE/UPDATE/STATUS_CHANGE 提交与批准、敏感字段继承、完整基线、编码复验、候选安全校验、Mapper 新增更新及展示脱敏 |
| 后端全量测试 | `mvn test` | 通过 | `410` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 管理端桌面静态视觉检查 | `http://127.0.0.1:4188/payment-config` | 通过 | 已检查 V1.15 三列表单、RSA2 公钥文本域和数值输入，未见明显交叠或截断；后端不可用错误态正常显示 |
| 管理端窄视口视觉检查 | Playwright `390 x 844`，`payment-config` 页面 | 通过 | 页面 `scrollWidth=390`，渠道表单宽 `330px`，控件无横向越界；字段单列排列，未见标题、按钮、文本或输入框交叠与截断 |
| 运行态 API | `http://127.0.0.1:18081/api/payment-config/channels/*` | 未执行 | 后端 `18081` 未监听，不记录新增、编辑、批准、驳回或真实渠道运行态联调通过 |
| 补丁与 XML 检查 | `git diff --check && xmllint --noout PaymentConfigMapper.xml` | 通过 | 代码、迁移和文档无空白符错误，Mapper XML 结构合法 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | `test -> master = 是`、`release/* = 否`；候选说明正式化、正式签字、发布后现场核验和稳定观察结束结论四项真实事实仍未形成 |

### 159.3 当前判断

1. 支付渠道新增、完整编辑和启停均已进入统一候选复核链路；管理端、Controller 和配置服务不存在本轮已知的渠道配置直接写入入口。
2. 当前并发约束由固定锁顺序、渠道唯一待办、渠道编码唯一键和批准前基线/存在性复验共同提供；结论来自单元与 Mapper 集成测试，不代表已执行生产数据库迁移或并发压测。
3. `SELF_DECLARED` 只表示申请人和复核人输入了不同声明，不能证明可信身份、权限、正式审批或不可抵赖签字。
4. 增量迁移为一次性脚本，不可重复执行；桌面与窄视口静态视觉检查已完成，但后端 `18081` 未监听，因此不形成运行态 API、真实渠道联调或完整端到端页面验收证据。
5. `test -> master = 是`、`release/* = 否`；正式放行仍缺候选说明正式化、正式签字、发布后现场核验和稳定观察结束结论。

## 160. 2026-09-09 支付网关启停 maker-checker V1.2 验证

### 160.1 本轮补强

1. 保留 `/api/payment-config/gateways/toggle` URL，但将语义从直接启停改为提交状态候选；新增网关状态复核列表、批准和驳回接口，提交与驳回均不修改运行态。
2. 新增 `t_payment_gateway_review`、两份全量 schema 和幂等迁移；以规范化大写网关编码作为可空唯一待办占位，同一网关同时最多一条待复核申请，处理完成后释放占位。
3. 提交先锁待办占位、再锁网关运行配置；网关不存在、目标状态相同、已有待办或数据库唯一键竞态均拒绝。候选基线指纹覆盖网关编码、名称、接入模式、渠道与环境范围、基础地址、报文与签名算法、证书、发布与灰度配置、回调白名单、适配器编排、超时、重试、状态和优先级。
4. 批准先锁复核申请、再锁网关运行配置并复验完整基线；只有异人声明且基线有效时才更新状态，状态更新与复核完成处于同一事务。驳回、同人复核、重复处理和陈旧基线均不修改运行态。
5. 管理端升级为 V1.16，网关按钮改为“申请启用/申请停用”，新增申请人、原因、复核人、意见和独立复核面板；批准后才刷新运行态，提交与驳回只刷新复核列表。
6. 同步数据库、接口、设计、测试、需求、差距和页面交互文档，新增 `PAY-TC-041`。本能力不执行证书轮换、网络探测、灰度动作或真实网关适配，人员身份固定为 `SELF_DECLARED`。

### 160.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付网关状态复核扩大定向测试 | `mvn -Dtest=PaymentGatewayConfigServiceImplTest,PaymentConfigGatewayReviewMapperIntegrationTest,PaymentConfigControllerTest,PaymentConfigServiceImplTest test` | 通过 | `82` 个用例全部通过，覆盖提交不改运行态、编码规范化、相同状态、唯一待办、唯一键竞态转换、异人批准、同人拒绝、驳回、重复处理、完整基线漂移、Mapper 状态应用与占位释放 |
| 后端全量测试 | `mvn test` | 通过 | `417` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 管理端桌面静态视觉检查 | `http://127.0.0.1:4190/payment-config`，`1280 x 720` | 通过 | V1.16、网关复核表单、刷新按钮、空态和网关表格正常渲染，未见明显交叠或截断；后端不可用错误态正常显示 |
| 管理端窄视口视觉检查 | Playwright `390 x 844`，`payment-config` 页面 | 通过 | 页面 `scrollWidth=390`，网关复核输入框宽 `294px` 并单列排列，控件无交叠或页面级横向溢出；V1.16、网关复核区和错误态均实际渲染 |
| 运行态 API | `http://127.0.0.1:18081/api/payment-config/gateways/*` | 未执行 | 后端 `18081` 未监听，不记录候选提交、批准、驳回、真实探活、真实灰度或真实网关运行态联调通过 |
| 补丁与 XML 检查 | `git diff --check && xmllint --noout PaymentConfigMapper.xml` | 通过 | 代码、迁移和文档无空白符错误，Mapper XML 结构合法 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 脚本退出码 `2` 来自 release 治理阻断；`test -> master = 是`、`release/* = 否`，正式放行仍缺候选说明正式化、正式签字、发布后现场核验和稳定观察结束结论 |

### 160.3 当前判断

1. 支付网关启停已由候选复核链路治理；管理端、Controller 和配置服务不存在本轮已知的网关状态直接写入入口。
2. 固定锁顺序、按网关唯一待办、数据库唯一键和批准前完整基线复验共同约束本服务内并发提交与处理。该结论来自单元与 Mapper 集成测试，不代表已执行生产数据库迁移、生产 MySQL 并发压测或真实网关联调。
3. `SELF_DECLARED` 只表示申请人和复核人输入了不同声明，不能证明可信身份、权限、正式审批、不可抵赖签字或生产放行。
4. 桌面与 `390 x 844` 窄视口静态视觉检查已完成，但后端 `18081` 未监听，因此不形成运行态 API、证书轮换、真实探活、真实灰度或完整端到端页面验收证据。
5. `test -> master = 是`、`release/* = 否`；正式放行仍缺候选说明正式化、正式签字、发布后现场核验和稳定观察结束结论。

## 161. 2026-09-09 支付网关新增/编辑 maker-checker V1.3 验证

### 161.1 本轮补强

1. 复用 `t_payment_gateway_review` 扩展 `CREATE / UPDATE / STATUS_CHANGE` 三类操作，共享按大写网关编码唯一的待复核占位；新增和编辑只保存完整候选，不直接改变网关运行态。
2. 完整候选覆盖名称、接入模式、渠道与环境范围、HTTPS 基础地址、报文与签名算法、证书台账、发布与灰度说明、回调白名单、适配器编排、超时、重试、状态和优先级。基础地址必须是带主机的 HTTPS 地址，超时必须大于 `0`，优先级不得小于 `0`，证书状态仅支持 `VALID / EXPIRING_SOON / EXPIRED / ARCHIVED`，样式由服务端映射。
3. 批准先锁复核申请，再锁网关运行配置并从持久化快照重建、重新校验候选。CREATE 复验编码仍不存在并将唯一键竞态转换为基线失效提示，UPDATE/STATUS_CHANGE 复验完整运行配置基线；完整写入与复核完成处于同一事务。
4. 新增一次性、非幂等迁移 `2026-09-09-expand-payment-gateway-review.sql`：先增加可空候选列并从当前网关配置回填历史 STATUS_CHANGE 申请，再恢复候选列 `NOT NULL` 约束；生产执行前仍需备份、变更审批和迁移窗口确认。
5. 管理端升级为 V1.17，增加完整网关新增/编辑表单、原始超时和优先级回填，以及三类复核操作展示；提交和驳回只更新复核列表，批准成功后才刷新运行态。
6. 证书别名、证书状态和灰度策略只形成治理台账。本轮不执行证书上传或轮换、真实探活、生产灰度动作、真实网关适配，也不把 `SELF_DECLARED` 解释为可信身份或正式审批。

### 161.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付网关配置复核扩大定向测试 | `mvn -Dtest=PaymentGatewayConfigServiceImplTest,PaymentConfigGatewayReviewMapperIntegrationTest,PaymentConfigControllerTest,PaymentConfigServiceImplTest test` | 通过 | `87` 个用例全部通过，覆盖 CREATE/UPDATE/STATUS_CHANGE 提交与批准、完整候选复验、非法地址/证书状态/数值拒绝、完整基线漂移、CREATE 编码占用与唯一键竞态转换，以及 Mapper 完整新增更新和待办释放 |
| 后端全量测试 | `mvn test` | 通过 | `422` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 管理端桌面静态视觉检查 | `http://127.0.0.1:4191/payment-config` | 通过 | 已实际渲染 V1.17 完整页面和网关新增/编辑、复核区域；未见明显交叠或截断，后端不可用错误态正常显示 |
| 管理端窄视口视觉检查 | Playwright `390 x 844`，`payment-config` 页面 | 通过 | 页面 `scrollWidth=390`，网关区可见输入控件宽 `294-330px`；表单与复核区单列排列，未发现按钮、标题或输入控件越界和页面级横向溢出 |
| 运行态 API | `http://127.0.0.1:18081/api/payment-config/gateways/*` | 未执行 | 后端 `18081` 未监听，不记录新增、编辑、提交、批准、驳回、真实探活、真实灰度或真实网关运行态联调通过 |
| 补丁与 XML 检查 | `git diff --check && xmllint --noout PaymentConfigMapper.xml` | 通过 | 代码、迁移和文档无空白符错误，Mapper XML 结构合法 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 脚本退出码 `2` 来自 release 治理阻断；`test -> master = 是`、`release/* = 否`，正式放行仍缺候选说明正式化、正式签字、发布后现场核验和稳定观察结束结论 |

### 161.3 当前判断

1. 支付网关新增、完整编辑和启停已进入统一候选复核链路；管理端、Controller 和配置服务不存在本轮已知的网关配置直接运行态写入入口。
2. 当前并发约束由固定锁顺序、网关唯一待办、网关编码唯一键，以及批准前存在性和完整基线复验共同提供。该结论来自单元与 Mapper 集成测试，不代表已执行生产数据库迁移、生产 MySQL 并发压测或真实网关联调。
3. 增量迁移为一次性脚本，不可重复执行；历史 STATUS_CHANGE 申请依赖对应网关运行配置完成候选回填，生产执行前必须先核验回填结果再恢复非空约束。
4. `SELF_DECLARED` 只表示申请人和复核人输入了不同声明，不能证明可信身份、权限、正式审批、不可抵赖签字或生产放行。
5. 桌面与 `390 x 844` 窄视口静态视觉检查已完成，但后端 `18081` 未监听，因此不形成运行态 API、证书上传或轮换、真实探活、生产灰度或完整端到端页面验收证据。
6. `test -> master = 是`、`release/* = 否`；正式放行仍缺候选说明正式化、正式签字、发布后现场核验和稳定观察结束结论。

## 162. 2026-09-09 支付控制策略完整候选 maker-checker V1.6 验证

### 162.1 本轮补强

1. 保留 `POST /api/payment-config/control-policies/toggle` URL 并改为提交 `STATUS_CHANGE` 候选，新增 `GET/POST /api/payment-config/control-policies/reviews` 及批准、驳回入口；CREATE/UPDATE/STATUS_CHANGE 共用按小写来源应用标识唯一的待复核占位，提交和驳回均不修改运行态。
2. 完整候选覆盖来源应用名称、支付方式/渠道/商户授权清单、来源应用与接口两级限流、令牌鉴权、访问令牌、严格模式和启停状态。两级限流必须大于 `0`；新增且开启令牌鉴权时令牌必填，编辑时令牌留空只在提交阶段继承当前值。
3. 新增 `t_payment_control_policy_review`、两份全量 schema 和幂等建表迁移。基线指纹覆盖全部受治理配置并包含令牌原文，不包含自动或人工变化的自检结果；待办唯一键竞态、CREATE 编码占用和运行配置基线漂移均在服务层转换为明确拒绝。
4. 批准先锁复核申请，再锁运行配置并从复核快照重建候选；运行配置写入与复核完成处于同一事务。任一候选批准后自检重置为 `WARN/warn`，严格模式不得沿用旧 `PASS`；自检回写仍作为系统运行状态，不进入人工配置复核。
5. 管理总览和复核列表只返回 `accessTokenConfigured`，不查询或序列化令牌原文；运行时读取和批准应用路径仍可读取令牌。令牌当前仍存于既有运行配置表和复核快照，不解释为专用密钥托管或真实轮换。
6. 管理端升级为 V1.18，新增控制策略完整新增/编辑表单、三类复核列表和启停申请字段；提交与驳回只刷新复核列表，批准后才刷新运行态。人员身份固定为 `SELF_DECLARED`，不代表可信身份、权限、正式审批或不可抵赖签字。

### 162.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付控制策略扩大定向测试 | `mvn -Dtest=PaymentControlPolicyConfigServiceImplTest,PaymentConfigControlPolicyReviewMapperIntegrationTest,PaymentConfigControllerTest,PaymentConfigServiceImplTest test` | 通过 | `85` 个用例全部通过，覆盖 CREATE/UPDATE/STATUS_CHANGE、令牌继承与脱敏、正数限流、唯一待办及竞态、相同状态、异人批准、同人拒绝、驳回、重复处理、完整基线漂移、自检结果不参与基线和批准后自检失效 |
| 后端全量测试 | `mvn test` | 通过 | `433` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 管理端桌面静态视觉检查 | `http://127.0.0.1:4191/payment-config`，`1440 x 1000` | 通过 | 已实际渲染 V1.18 控制策略完整表单、复核区、令牌脱敏提示和后端不可用错误态，未见标题、按钮、输入框交叠或截断 |
| 管理端窄视口视觉检查 | Playwright `390 x 844`，`payment-config` 页面 | 通过 | 页面 `body/document scrollWidth=390`；控制策略标题、双按钮、表单字段及底部操作按钮单列排列，未发现页面级横向溢出或控件遮挡 |
| 运行态 API | `http://127.0.0.1:18081/api/payment-config/control-policies/*` | 未执行 | 后端 `18081` 未监听，不记录新增、编辑、提交、批准、驳回、自检或真实支付主链路运行态联调通过 |
| 补丁与 XML 检查 | `git diff --check && xmllint --noout PaymentConfigMapper.xml` | 通过 | 代码、迁移和文档无空白符错误，Mapper XML 结构合法；旧前端直改调用和控制策略直接写运行态口径已清零 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 脚本退出码 `2` 来自 release 治理阻断；`test -> master = 是`、`release/* = 否`，正式放行仍缺候选说明正式化、正式签字、发布后现场核验和稳定观察结束结论 |

### 162.3 当前判断

1. 支付控制策略新增、完整编辑和启停已进入统一候选复核链路；管理端、Controller 和配置服务不存在本轮已知的控制策略配置直接运行态写入入口。
2. 固定锁顺序、按来源应用唯一待办、数据库唯一键，以及批准前存在性和完整基线复验共同约束本服务内并发提交与处理。该结论来自单元与 Mapper 集成测试，不代表已执行生产数据库迁移、生产 MySQL 并发压测或真实支付链路联调。
3. 管理接口已避免返回令牌原文，但令牌仍保存在既有运行配置和复核快照中；当前能力不构成专用密钥托管、真实轮换、可信身份审批、策略版本回滚或多实例分布式限流。
4. 桌面与 `390 x 844` 窄视口静态视觉检查已完成，但后端 `18081` 未监听，因此不形成运行态 API、自检、候选批准或完整端到端页面验收证据。
5. `test -> master = 是`、`release/* = 否`；正式放行仍缺候选说明正式化、正式签字、发布后现场核验和稳定观察结束结论。

## 163. 2026-09-09 异常告警值班路由完整候选 maker-checker V1.1 验证

### 163.1 本轮补强

1. 保留值班路由新增、编辑和 `POST /api/payment-config/issue-duty-rosters/toggle` URL，但统一改为提交 `CREATE / UPDATE / STATUS_CHANGE` 候选；新增复核列表、批准和驳回接口，提交与驳回不修改运行态。
2. 新增 `t_payment_issue_duty_roster_review`、两份全量 schema 和幂等建表迁移。三类操作共用按大写路由编码唯一的可空待办占位，完成后释放；唯一键竞态转换为明确的重复待办错误。
3. 完整候选和基线指纹覆盖异常类型、严重等级、责任组、接收人、通知通道、升级等级/接收人/策略/超时、班次标签、起止小时、星期范围、日期策略和启停状态。保留跨日班次，星期按首次出现顺序去重，升级超时和日期策略继续由服务端校验。
4. 批准先锁复核申请，再锁运行配置并从复核快照重建候选；CREATE 复验编码未占用，UPDATE/STATUS_CHANGE 复验完整基线。完整新增或更新与复核完成同事务，旧的非加锁查询和直接状态更新 Mapper 路径已删除。
5. 管理端升级为 V1.19，新增表单申请人/原因、启停申请字段、复核人/意见和独立复核面板；提交与驳回只更新复核列表，批准后才刷新运行态。
6. `SELF_DECLARED` 只表达申请人与复核人声明不同，不代表可信认证、权限、正式审批、不可抵赖签字、真实排班中心、法定节假日日历或外部通知送达。

### 163.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 值班路由复核扩大定向测试 | `mvn -Dtest=PaymentConfigIssueDutyRosterReviewMapperIntegrationTest,PaymentIssueDutyRosterConfigServiceImplTest,PaymentConfigControllerTest,PaymentConfigServiceImplTest test` | 通过 | `84` 个用例全部通过，覆盖三类候选、运行态隔离、编码大写、跨日班次、星期规范化、唯一待办及竞态、相同状态、异人批准、同人拒绝、驳回、重复处理、CREATE 编码占用、完整基线漂移和 Mapper 待办释放 |
| 后端全量测试 | `mvn test` | 通过 | `442` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 管理端桌面静态视觉检查 | `http://127.0.0.1:4191/payment-config`，`1440 x 1000` | 通过 | 已实际渲染 V1.19 值班路由完整候选表单、复核区、错误态和运行态表头；页面 `scrollWidth=1440`，未见标题、按钮或输入框交叠与截断 |
| 管理端窄视口视觉检查 | Playwright `390 x 844`，`payment-config` 页面 | 通过 | 页面 `body/document scrollWidth=390`；值班路由表单和复核输入单列排列，标题、刷新按钮和输入控件无交叠或页面级横向溢出 |
| 运行态 API | `http://127.0.0.1:18081/api/payment-config/issue-duty-rosters/*` | 未执行 | 后端 `18081` 未监听，不记录新增、编辑、启停、批准、驳回、真实排班或真实通知送达联调通过 |
| 补丁与 XML 检查 | `git diff --check && xmllint --noout PaymentConfigMapper.xml` | 通过 | 代码、迁移和文档无空白符错误，Mapper XML 结构合法；值班路由旧直写 Mapper 路径已清零 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 脚本退出码 `2` 来自 release 治理阻断；`test -> master = 是`、`release/* = 否`，正式放行仍缺候选说明正式化、正式签字、发布后现场核验和稳定观察结束结论 |

### 163.3 当前判断

1. 异常告警值班路由新增、完整编辑和启停已进入统一候选复核链路；管理端、Controller、Service 和 Mapper 不存在本轮已知的值班路由直接运行态写入入口。
2. 固定锁顺序、按路由唯一待办、数据库唯一键，以及批准前存在性和完整基线复验共同约束本服务内并发提交与处理。该结论来自单元与 H2 MySQL 兼容 Mapper 集成测试，不代表已执行生产数据库迁移或生产 MySQL 并发压测。
3. 当前仍使用轻量自声明身份，且值班日历仅支持星期和日期策略，不具备法定节假日数据、真实轮班交接、可信审批、升级动作编排或通知网关送达证据。
4. 桌面与 `390 x 844` 静态视觉检查已完成，但后端 `18081` 未监听，因此不形成运行态 API、候选批准或完整端到端页面验收证据。
5. `test -> master = 是`、`release/* = 否`；正式放行仍缺候选说明正式化、正式签字、发布后现场核验和稳定观察结束结论。

## 164. 2026-09-09 支付控制策略不可变版本与受控回滚 V1.7 验证

### 164.1 本轮补强

1. 新增 `t_payment_control_policy_version` 不可变版本表，保存完整运行配置快照、递增版本号、变更类型、回滚来源和原因；全量初始化数据与增量迁移均为存量策略生成 V1 `BASELINE`。
2. 新增控制策略版本历史查询和回滚候选接口。管理历史只返回 `accessTokenConfigured`，不返回令牌原文；回滚复核快照内部保留恢复运行配置所需令牌。
3. CREATE/UPDATE/STATUS_CHANGE 批准后分别追加 `REVIEW_RELEASE / REVIEW_STATUS_CHANGE` 版本；ROLLBACK 与三类现有操作共享小写来源应用唯一待办，提交和驳回不修改运行态。
4. 回滚批准先锁复核申请，再锁运行配置，同时复验提交时完整指纹和最新版本号。恢复配置、重置自检为 `WARN`、追加 `REVIEW_ROLLBACK` 新版本与完成申请处于同一事务。
5. 管理端升级为 V1.20，新增版本历史和回滚入口；复核列表展示基线版本与回滚来源版本，历史面板不展示令牌原文。
6. `SELF_DECLARED` 仍只表示人员声明不同，不代表可信身份、权限、正式审批或不可抵赖签字；版本表中的令牌存储也不等于专用密钥托管或真实轮换。

### 164.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付控制策略扩大定向测试 | `mvn -Dtest=PaymentControlPolicyConfigServiceImplTest,PaymentConfigControlPolicyReviewMapperIntegrationTest,PaymentConfigControllerTest,PaymentConfigServiceImplTest test` | 通过 | `88` 个用例全部通过，覆盖四类候选、版本查询、令牌脱敏、回滚来源、指纹与版本基线复验、批准后新版本和自检失效 |
| 后端全量测试 | `mvn test` | 通过 | `444` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 管理端桌面无头渲染检查 | `http://127.0.0.1:4192/payment-config`，Playwright `1280px`，本地模拟响应 | 通过 | V1.20、控制策略版本历史和回滚按钮实际渲染；`clientWidth/scrollWidth=1280/1280`，页面文本未出现测试令牌原文。模拟响应只用于 UI，不算真实 API 联调 |
| 管理端窄视口无头渲染检查 | Playwright `390 x 844`，本地模拟响应 | 通过 | `document/body scrollWidth=390`，版本面板和回滚入口可见，无页面级横向溢出 |
| 运行态 API 与生产迁移 | `http://127.0.0.1:18081/api/payment-config/control-policies/*` | 未执行 | 后端 `18081` 未监听；不记录版本查询、回滚提交/批准、生产 MySQL 迁移、令牌托管或真实支付链路联调通过 |
| 补丁与 XML 检查 | `git diff --check && xmllint --noout PaymentConfigMapper.xml` | 通过 | 代码与 Mapper XML 无空白符或结构错误 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 脚本退出码 `2` 来自 release 治理阻断；`test -> master = 是`、`release/* = 否`，正式放行仍缺候选说明正式化、正式签字、发布后现场核验和稳定观察结束结论 |

### 164.3 当前判断

1. 支付控制策略新增、编辑、启停和历史回滚已进入统一候选复核与不可变版本链路；历史回滚不覆盖旧版本，而是批准后追加新版本。
2. 来源应用唯一待办、运行配置行锁、完整指纹和最新版本号共同约束并发与陈旧候选；结论来自单元和 H2 MySQL 兼容 Mapper 测试，不代表生产 MySQL 并发压测。
3. 管理总览、复核列表和版本历史均不返回令牌原文，但运行配置、复核快照和不可变版本仍保存令牌；可信权限、专用密钥托管与轮换和多实例分布式限流仍未完成。
4. 页面已完成模拟响应下的桌面与手机无头渲染检查；系统锁屏阻断了人工目视检查，且后端未启动，因此不形成真实端到端页面验收证据。
5. `test -> master = 是`、`release/* = 否`；正式放行仍缺候选说明正式化、正式签字、发布后现场核验和稳定观察结束结论。

## 165. 2026-09-09 支付渠道不可变版本与受控回滚 V1.4 验证

### 165.1 本轮补强

1. 新增 `t_payment_channel_version` 不可变版本表，保存渠道完整运行配置快照、递增版本号、变更类型、回滚来源和原因；全量初始化数据与幂等增量迁移均为存量渠道生成 V1 `BASELINE`。
2. 新增渠道版本历史查询和回滚候选接口。管理历史只返回回调密钥、公钥是否配置，不返回原文；内部单版本查询和回滚复核快照保留恢复完整运行配置所需敏感字段。
3. CREATE/UPDATE/STATUS_CHANGE 批准后分别追加 `REVIEW_RELEASE / REVIEW_STATUS_CHANGE` 版本；ROLLBACK 与三类现有操作共享小写渠道编码唯一待办，提交和驳回不修改运行态。
4. 回滚批准先锁复核申请，再锁渠道运行配置，同时复验提交时完整指纹和最新版本号。配置恢复、追加 `REVIEW_ROLLBACK` 新版本与完成申请处于同一事务。
5. 管理端升级为 V1.21，新增渠道版本历史和回滚入口；复核列表展示基线版本与回滚来源版本，版本面板不展示回调密钥、公钥原文。
6. `SELF_DECLARED` 仍只表示人员声明不同，不代表可信身份、权限、正式审批或不可抵赖签字；版本表保存敏感原文也不等于专用密钥托管或真实轮换。

### 165.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付渠道版本定向测试 | `mvn -Dtest=PaymentChannelStatusReviewServiceImplTest,PaymentConfigChannelReviewMapperIntegrationTest,PaymentConfigControllerTest test` | 通过 | Surefire XML 实际汇总 `31` 个用例全部通过，覆盖四类候选、版本查询脱敏、回滚来源、完整指纹与最新版本双基线、版本漂移拒绝和批准后追加新版本 |
| 后端全量测试 | `mvn test` | 通过 | Surefire XML 实际汇总 `446` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 管理端桌面无头渲染检查 | `http://127.0.0.1:4195/payment-config`，Playwright `1280 x 900`，本地模拟响应 | 通过 | V1.21、两条渠道版本和回滚按钮实际渲染；`clientWidth/scrollWidth=1280/1280`，版本表在面板内独立横向滚动，最新版本回滚禁用，页面未出现测试敏感原文 |
| 管理端窄视口无头渲染检查 | Playwright `390 x 844`，本地模拟响应 | 通过 | `document/body scrollWidth=390`，版本面板宽 `330px`，表格在面板内独立滚动，标题、说明和关闭按钮无交叠；模拟响应只用于 UI，不算真实 API 联调 |
| 运行态 API 与生产迁移 | `http://127.0.0.1:18081/api/payment-config/channels/*` | 未执行 | 后端 `18081` 未监听；不记录版本查询、回滚提交/批准、生产 MySQL 迁移、密钥托管或真实渠道联调通过 |
| 补丁与 XML 检查 | `git diff --check && xmllint --noout PaymentConfigMapper.xml` | 通过 | 代码、迁移和文档无空白符错误，Mapper XML 结构合法 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 脚本退出码 `2` 来自 release 治理阻断；`test -> master = 是`、`release/* = 否`，正式放行仍缺候选说明正式化、正式签字、发布后现场核验和稳定观察结束结论 |

### 165.3 当前判断

1. 支付渠道新增、编辑、启停和历史回滚已进入统一候选复核与不可变版本链路；历史回滚不覆盖旧版本，而是批准后追加新版本。
2. 渠道唯一待办、运行配置行锁、完整指纹和最新版本号共同约束并发与陈旧候选；结论来自单元和 H2 MySQL 兼容 Mapper 测试，不代表生产 MySQL 迁移或并发压测已执行。
3. 管理总览、复核列表和版本历史均不返回回调密钥、公钥原文，但运行配置、复核快照和不可变版本仍保存敏感字段；可信权限、专用密钥托管与轮换和真实渠道运维仍未完成。
4. 页面已完成模拟响应下的桌面和手机无头渲染检查，但后端 `18081` 未监听，因此不形成真实端到端页面验收、回滚批准或渠道联调证据。
5. `test -> master = 是`、`release/* = 否`；正式放行仍缺候选说明正式化、正式签字、发布后现场核验和稳定观察结束结论。

## 166. 2026-09-09 支付网关不可变版本与受控回滚 V1.4 验证

### 166.1 本轮补强

1. 新增 `t_payment_gateway_version` 不可变版本表，保存完整网关运行配置快照、递增版本号、变更类型、回滚来源和原因；全量初始化数据与幂等增量迁移均为存量网关生成 V1 `BASELINE`。
2. 新增网关版本历史查询和回滚候选接口。ROLLBACK 从指定历史版本复制完整候选，与 CREATE/UPDATE/STATUS_CHANGE 共享大写网关编码唯一待办；提交和驳回不修改运行态。
3. 四类候选对存量网关保存 `baseVersionNo` 和完整配置指纹，回滚额外保存 `sourceVersionNo`。批准时同时复验指纹和最新版本号，任一漂移均拒绝旧候选。
4. 运行配置写入、追加 `REVIEW_RELEASE / REVIEW_STATUS_CHANGE / REVIEW_ROLLBACK` 新版本与完成复核处于同一事务；历史版本只增不改。
5. 管理端升级为 V1.22，新增网关版本历史和回滚入口、请求竞态保护、最新版本回滚禁用，并在批准后刷新已打开的历史面板。
6. 版本快照只保存现有网关业务台账字段，不包含证书文件或私钥；本能力不等于证书托管/轮换、真实探活、真实灰度动作、适配器调用、可信审批或生产迁移完成。

### 166.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付网关版本定向测试 | `mvn -Dtest=PaymentGatewayConfigServiceImplTest,PaymentConfigGatewayReviewMapperIntegrationTest,PaymentConfigControllerTest test` | 通过 | Surefire XML 汇总 `30` 个用例通过，覆盖版本读写、复核版本号、回滚提交/批准、版本来源、运行态隔离及仅版本号漂移时拒绝旧候选 |
| 后端全量测试 | `mvn test` | 通过 | Surefire XML 汇总 `449` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 管理端桌面无头渲染检查 | `http://127.0.0.1:4196/payment-config`，Playwright `1280 x 900`，本地模拟响应 | 通过 | V1.22、两条网关版本和回滚按钮实际渲染；页面 `clientWidth/scrollWidth=1280/1280`，表格在面板内独立横向滚动，最新版本回滚禁用 |
| 管理端手机无头渲染检查 | Playwright `390 x 844`，本地模拟响应 | 通过 | 页面 `clientWidth/scrollWidth=390/390`，版本面板宽 `330px`、表格容器宽 `294px`，未出现页面级横向溢出或标题/按钮交叠 |
| 运行态 API 与生产迁移 | `http://127.0.0.1:18081/api/payment-config/gateways/*` | 未执行 | 后端 `18081` 未监听；不记录版本查询、回滚提交/批准、生产 MySQL 迁移、证书轮换、真实探活、真实灰度或真实网关联调通过 |
| 补丁与 XML 检查 | `git diff --check && xmllint --noout PaymentConfigMapper.xml` | 通过 | 代码、迁移和 Mapper XML 无空白符或结构错误 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 脚本退出码 `2` 来自 release 治理阻断；`test -> master = 是`、`release/* = 否`，`releaseBlockerCount = 4` |

### 166.3 当前判断

1. 支付网关新增、编辑、启停和历史回滚已进入统一候选复核与不可变版本链路；历史回滚不会覆盖旧版本，而是在批准后追加新版本。
2. 网关唯一待办、运行配置行锁、完整指纹和最新版本号共同约束并发与陈旧候选；结论来自单元和 H2 MySQL 兼容 Mapper 测试，不代表生产 MySQL 迁移或并发压测。
3. 页面已完成模拟响应下的桌面与手机无头渲染检查，但后端 `18081` 未监听，因此不形成真实端到端页面验收、回滚批准、生产迁移或真实网关联调证据。
4. `SELF_DECLARED` 只表示人员声明不同，不能证明可信身份、权限、正式审批、不可抵赖签字或生产放行。
5. `test -> master = 是`、`release/* = 否`；正式放行仍缺候选说明正式化、正式签字、发布后现场核验和稳定观察结束结论。

## 167. 2026-09-09 异常告警通知供应商完整候选复核 V1.2 验证

### 167.1 本轮补强

1. 新增 `t_payment_alert_provider_review` 和幂等迁移，保存 CREATE/UPDATE/STATUS_CHANGE 完整候选、运行配置 SHA-256 基线、可空唯一待办占位及申请/复核记录。
2. 新增供应商创建、编辑、启停候选、复核列表、批准和驳回接口。提交与驳回不修改运行态；批准先锁申请，再锁运行配置，并在同一事务内写入完整配置、完成复核和释放占位。
3. 供应商编码统一大写；通道限制为 `IN_APP / IM / SMS / EMAIL`；路由优先级不得为负，文本按 schema 长度校验。CREATE 批准复验编码未占用，UPDATE/STATUS_CHANGE 批准复验完整基线。
4. 已删除已知的 `updateAlertProviderStatus` 直接运行态写入路径。申请人与复核人仅做大小写不敏感的自声明异人校验，身份可信度为 `SELF_DECLARED`。
5. 管理端升级到 V1.23，新增供应商完整候选表单、启停申请身份与原因、复核工作台和申请启停按钮；批准后才刷新运行态。
6. 本能力不等于真实通知供应商配置、可信审批、生产迁移、接收人核验、运行态派发或实际送达回执完成。

### 167.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 供应商复核受影响定向测试 | `mvn -Dtest=PaymentAlertProviderConfigServiceImplTest,PaymentConfigAlertProviderReviewMapperIntegrationTest,PaymentConfigControllerTest,PaymentConfigServiceImplTest test` | 通过 | `83` 个用例通过，覆盖三类候选、运行态隔离、编码规范化、唯一待办竞态、完整基线漂移、同人拒绝、原子批准、驳回及六类 Controller 入口 |
| 后端全量测试 | `mvn test` | 通过 | Surefire XML 汇总 `456` 个用例，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 管理端桌面模拟响应检查 | `http://127.0.0.1:4198/payment-config`，Playwright `1280 x 900` | 通过 | V1.23、完整候选表单、待复核“通过并应用”和运行态“申请停用”实际渲染；`document/body scrollWidth=1280/1280` |
| 管理端手机模拟响应检查 | Playwright `390 x 844` | 通过 | `document/body scrollWidth=390/390`，表单、复核操作和运行态表格在各自响应式容器内展示，无页面级横向溢出或控件交叠 |
| 运行态 API 与生产迁移 | `http://127.0.0.1:18081/api/payment-config` | 未执行 | 返回 `000`，后端未监听；不记录真实候选提交/批准、生产 MySQL 迁移、供应商参数或实际送达通过 |
| 补丁与 XML 检查 | `git diff --check && xmllint --noout PaymentConfigMapper.xml` | 通过 | 代码、迁移、文档和 Mapper XML 无空白符或结构错误 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 脚本退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`releaseBlockerCount=4` |

### 167.3 当前判断

1. 告警通知供应商新增、编辑和启停已进入统一完整候选复核链路，批准前不会改变自动派发读取的运行配置。
2. 唯一待办、运行配置行锁、完整指纹、创建编码复验和数据库唯一键共同约束本服务内并发与陈旧候选；结论来自单元和 H2 MySQL 兼容 Mapper 测试，不代表生产 MySQL 并发压测。
3. `SELF_DECLARED` 只表示人员声明不同，不能证明可信身份、权限、正式审批或不可抵赖签字；真实企业消息、短信和邮件供应商仍需外部联调。
4. 页面已完成模拟响应下的桌面与手机视觉检查，但后端 `18081` 未监听，因此不形成真实端到端页面验收或通知送达证据。
5. `test -> master = 是`、`release/* = 否`；正式放行仍缺候选说明正式化、正式签字、发布后现场核验和稳定观察结束结论。

## 168. 2026-09-09 支付请求与处理日志独立事实模型 V1.0 验证

### 168.1 本轮补强

1. 复用既有 `t_payment_attempt` 作为独立支付请求事实，不新增同义请求表；新增 `t_payment_process_log` 作为只追加的支付处理日志事实表，以来源类型和来源编号约束重复写入。
2. 全量初始化与幂等迁移均从支付请求、通知日志、路由记录和支付事件回填历史处理事实；日志查询、导出、筛选和总览不再在运行时执行四表 `UNION ALL`。
3. 支付提交、路由、通知/回调、主动查单成功与失败、风控事件、支付成功、人工关单和超时关单均在原有 Spring 事务内追加处理日志；后续支付请求状态更新不会改写已形成的日志快照。
4. 处理日志正文使用 `TEXT`，避免上游请求、响应或事件报文达到字段上限后，附加处理摘要导致支付主事务因日志正文超长而失败。
5. 本能力只形成支付核心库内的独立审计事实，不代表生产 MySQL 迁移、真实渠道请求/回调、外部日志平台归集或生产留存策略已经完成。

### 168.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付处理日志定向测试 | `mvn -Dtest=PaymentServiceImplTest,PaymentExpiryTaskServiceImplTest,PaymentProcessLogMapperIntegrationTest,PaymentLogServiceImplTest,PaymentLogControllerTest test` | 通过 | `43` 个用例全部通过，覆盖主流程四类事实写入、超时关单、重复来源幂等，以及独立表筛选、分页、计数和总览查询 |
| 后端全量测试 | `mvn test` | 通过 | `457` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| 全量 SQL 初始化 | H2 MySQL 兼容模式加载完整 `schema.sql + data.sql` | 通过 | 表结构和四类历史数据回填已完整执行；该结果只证明本地兼容初始化，不等于生产 MySQL 迁移通过 |
| 运行态日志 API | 本地 H2 启动后请求 `/api/payment-logs` | 未完成 | H2 缺少 MySQL `DATE_FORMAT`，接口返回 `PAYMENT-500`；未为通过本地验证而向生产代码加入 H2 专用兼容逻辑，不记录 API 联调通过 |
| 补丁与 XML 检查 | `git diff --check && xmllint --noout PaymentMapper.xml PaymentLogMapper.xml` | 通过 | 代码、SQL、迁移、文档无空白符错误，两个 Mapper XML 结构合法 |
| 生产迁移与真实链路 | 生产 MySQL、真实渠道及外部日志平台 | 未执行 | 不记录生产迁移、真实渠道提交/回调、现场核验或外部日志归集通过 |

### 168.3 当前判断

1. 支付请求事实和支付处理日志已从运行时多表投影升级为职责明确的独立对象模型，存量回填和未来增量写入均有明确路径。
2. 来源唯一键、`INSERT IGNORE` 和同事务追加共同约束重复事实与主流程一致性；结论来自单元和 H2 MySQL 兼容 Mapper 集成测试，不代表生产 MySQL 并发或迁移验证。
3. 本地 H2 已完成全量 schema/data 初始化，但日志 API 仍受 H2/MySQL 函数差异阻断，因此不形成运行态 API 验收证据。
4. `test -> master = 是`、`release/* = 否`；正式放行仍缺候选说明正式化、正式签字、发布后现场核验和稳定观察结束结论。

## 169. 2026-09-09 支付处理日志数据最小化 V1.1 验证

### 169.1 本轮补强

1. 支付提交处理日志只保留支付方式和请求状态，不再复制包含客户端 IP、设备、手机号和幂等键的请求报文；原始请求仍保留在 `t_payment_attempt` 来源事实中。
2. 通知处理日志只保留通知类型和处理状态，不再复制渠道响应；事件处理日志只保留事件类型，不再复制事件载荷。原始通知结果和事件载荷仍分别保留在来源事实表中。
3. 全量初始化和增量迁移采用相同的最小摘要规则，避免存量回填重新扩散原始载荷。
4. 运行时日志幂等写入由 `INSERT IGNORE` 改为 `ON DUPLICATE KEY UPDATE id = id`：唯一键重复保持无操作，缺失必填字段等非重复数据错误继续抛出并触发原事务回滚。
5. 本能力是通用日志数据最小化，不等于支付请求详情字段级授权、统一身份权限、生产归档分区或外部日志平台已经完成。

### 169.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 日志最小化扩大定向测试 | `mvn -Dtest=PaymentProcessLogMapperIntegrationTest,PaymentServiceImplTest,PaymentExpiryTaskServiceImplTest,PaymentLogServiceImplTest,PaymentLogControllerTest test` | 通过 | `44` 个用例全部通过；提交日志断言不含 IP、幂等键和请求报文，通知日志断言不含渠道响应，重复来源写入返回 `0`，非重复数据完整性错误抛出并回滚 |
| 后端全量测试 | `mvn test` | 通过 | `458` 个用例全部通过，`0` failures、`0` errors、`0` skipped |
| 补丁与 XML 检查 | `git diff --check && xmllint --noout PaymentMapper.xml PaymentLogMapper.xml` | 通过 | Java、SQL、迁移和文档无空白符错误，两个 Mapper XML 结构合法 |
| 生产迁移与真实链路 | 生产 MySQL、真实渠道与统一身份系统 | 未执行 | 不记录生产回填、真实请求/回调、字段级授权或外部日志检索通过 |

### 169.3 当前判断

1. 通用支付处理日志已不再成为原始请求、渠道响应和事件载荷的第二份暴露面，运营检索所需阶段、状态、级别和来源摘要保持可用。
2. 原始事实没有被删除或伪脱敏，研发排障仍可通过支付单号和来源编号联查；其生产访问必须继续由后续统一身份与字段级授权约束。
3. 精准唯一键幂等不会再静默忽略非重复数据错误，独立日志与支付主事务的一致性语义更符合设计。
4. `test -> master = 是`、`release/* = 否`；正式放行仍缺候选说明正式化、正式签字、发布后现场核验和稳定观察结束结论。

## 170. 2026-09-09 支付排障出参敏感数据最小化 V1.0 验证

### 170.1 本轮补强

1. 新增统一支付敏感数据脱敏组件，在三类列表及支付请求、支付流水 CSV 出参边界复用；数据库和 Mapper 原始事实保持不变。
2. JSON 脱敏从字段正则替换升级为 Jackson 递归遍历，覆盖嵌套对象、数组、大小写变体、付款手机号、设备标识、通用电话、银行卡号、客户名称、客户端 IP、幂等键及内嵌原始载荷字段。
3. 非字符串敏感值固定掩码，非法 JSON 整体返回 `[REDACTED]`；支付流水摘要只保留最小方式/状态、类型/状态或事件/发布状态，不再重复输出原始载荷。
4. 本能力只形成基础出参最小化，不等于统一身份、角色权限、字段级授权、导出审批、水印、生产审计或渗透测试完成。

### 170.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 出参脱敏定向测试 | `mvn -Dtest=PaymentSensitiveDataMaskerTest,PaymentRequestServiceImplTest,PaymentFlowServiceImplTest,PaymentRouteExecutionServiceImplTest test` | 通过 | `15` 个用例通过，覆盖递归脱敏、非法/标量 JSON 失败闭合、三条服务链路及 Mapper DTO 不被修改 |
| 后端全量测试 | `mvn test` | 通过 | Surefire XML 汇总 `465` 个用例，`0` failures、`0` errors、`0` skipped |
| 生产身份权限与访问审计 | 统一认证、字段级授权、导出审批、水印与审计平台 | 未执行 | 不记录可信授权、生产隐私验收、渗透测试或现场核验通过 |
| 补丁与 XML 检查 | `git diff --check && xmllint --noout PaymentFlowMapper.xml` | 通过 | Java、Mapper XML、测试和文档无空白符或 XML 结构错误 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`releaseBlockerCount=4` |

### 170.3 当前判断

1. 三个支付排障入口已使用同一套出参脱敏规则，避免支付请求已脱敏而流水、路由仍直出原始上下文的不一致。
2. 结构化递归和失败闭合覆盖了原正则无法可靠处理的嵌套、数组、转义、大小写和非字符串场景；原始事实仍只应通过后续受控权限链路访问。
3. `test -> master = 是`、`release/* = 否`；本功能测试通过不能替代候选说明正式化、正式签字、发布后现场核验或稳定观察结束结论。

## 171. 2026-09-09 支付路由执行结果导出 V1.4 验证

### 171.1 本轮补强

1. 新增 `GET /api/payment-routes/export`，复用支付路由列表九项筛选和排序参数，直接执行无分页查询并返回 UTF-8 BOM CSV。
2. 导出列覆盖关联交易、客户与金额、规则与结果、运营配置快照版本、终端、客户端 IP、幂等键、请求/响应报文和创建时间。
3. CSV 生成前复用统一支付敏感数据脱敏器，输出脱敏副本，不修改 Mapper 原始 DTO 或数据库事实；双引号按 CSV 规则转义。
4. 管理端升级为 V1.4，页头新增“导出路由”，按当前筛选与排序条件发起下载，不携带页码和每页条数。

### 171.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 路由导出定向测试 | `mvn -Dtest=PaymentRouteExecutionServiceImplTest,PaymentRouteExecutionControllerTest test` | 通过 | `7` 个用例通过，新增覆盖无分页导出、参数规范化、脱敏、CSV 转义、文件名和媒体类型 |
| 后端全量测试 | `mvn test` | 通过 | Surefire XML 汇总 `467` 个用例，`0` failures、`0` errors、`0` skipped |
| `admin-web` 构建 | `npm run build` | 通过 | Vite 生产构建成功，`68` 个模块完成转换 |
| 管理端桌面模拟响应检查 | `http://127.0.0.1:4201/payment-routes`，Playwright `1280 x 900` | 通过 | 页面 `clientWidth/scrollWidth=1280/1280`；“导出路由”和“查看路由配置”按钮无交叠，导出请求包含当前九项筛选与排序参数，并使用 `_blank`、`noopener,noreferrer` |
| 管理端手机模拟响应检查 | Playwright `390 x 844` | 通过 | 页面 `clientWidth/scrollWidth=390/390`；详情区降为单列，排障信息纵向排列，无页面级横向溢出或页头按钮交叠 |
| 实际浏览器下载与生产大数据量验证 | 真实后端、生产权限和生产规模数据 | 未执行 | 不记录浏览器下载联调、导出权限、审批、水印、下载审计或生产性能通过 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`releaseBlockerCount=4` |

### 171.3 当前判断

1. 支付路由执行台已与支付请求、支付流水等排障页面具备一致的当前筛选条件导出能力，列表与离线证据口径保持一致。
2. 本地测试证明控制器、服务和脱敏行为；模拟响应下的桌面与手机检查只证明页面渲染、响应式布局和导出请求拼装，不证明真实后端下载联调、生产 MySQL 全量导出性能或权限治理完成。
3. `test -> master` 与 `release/*` 结论以本轮统一门禁复判为准；不得以本功能通过替代正式放行治理事实。

## 172. 2026-09-09 支付主对象出参敏感数据最小化 V1.1 验证

### 172.1 本轮补强

1. 支付单列表和 CSV 在服务层复制 Mapper DTO 后脱敏客户名称，不改变数据库原值或筛选口径。
2. 支付详情在补齐路由、回调和事件轨迹后复制 DTO，统一脱敏客户名称、最近客户端 IP、幂等键及请求/响应 JSON。
3. `detail / callback / query / close` 四类返回支付详情的入口共享同一出参转换，避免动作响应绕过只读详情的脱敏边界。
4. 回调验签、主动查单、状态收口、账单结清和关单仍使用 Mapper 原始事实，脱敏副本只用于最终对外返回。

### 172.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付主对象脱敏定向测试 | `mvn -Dtest=PaymentSensitiveDataMaskerTest,PaymentServiceImplTest,PaymentControllerTest test` | 通过 | `43` 个用例通过；覆盖支付单列表、CSV、详情字段脱敏、Mapper DTO 不变及既有主链路状态收口 |
| 后端全量测试 | `mvn test` | 通过 | Surefire XML 汇总 `468` 个用例，`0` failures、`0` errors、`0` skipped |
| 生产身份权限与访问审计 | 统一认证、角色/字段级授权、导出审批、水印和审计平台 | 未执行 | 不记录可信授权、敏感原文受控查看、生产隐私验收、渗透测试或现场核验通过 |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和文档无空白符错误；本轮未修改 Mapper XML 或数据库结构 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`releaseBlockerCount=4` |

### 172.3 当前判断

1. 支付主对象不再通过支付单列表、CSV、只读详情或动作响应直接暴露本轮覆盖的个人与请求上下文字段。
2. 原始事实仍保留在数据库并供事务内业务处理使用；当前默认脱敏不能替代后续可信身份和按角色字段分级授权。
3. `test -> master` 与 `release/*` 结论仍以本轮统一门禁复判为准，不以本功能通过替代正式放行治理事实。

## 173. 2026-09-09 支付运营查询出参敏感数据最小化 V1.0 验证

### 173.1 本轮补强

1. 订单、账单、收银台会话、退款、服务者结算和支付异常列表在 Service 出参边界复制 Mapper DTO，并脱敏客户或服务者姓名。
2. 账单、收银台会话、订单、退款和服务者结算 CSV 使用同一转换结果；订单同时脱敏客户与服务者姓名。
3. 退款详情在独立副本上装配操作日志并脱敏客户姓名，不再改写 Mapper 详情 DTO。
4. 原始筛选、退款渠道下发、内部路由判断和下游支付事件仍使用原始业务事实；本轮未改变数据库结构、交易状态机或用户端收银台返回。

### 173.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 运营查询脱敏定向测试 | `mvn -Dtest=BillServiceImplTest,CashierSessionServiceImplTest,OrderServiceImplTest,RefundServiceImplTest,SettlementServiceImplTest,PaymentIssueServiceImplTest test` | 通过 | `35` 个用例通过；覆盖六类列表、五类 CSV、退款详情和 Mapper DTO 原值保持 |
| 后端全量测试 | `mvn test` | 通过 | Surefire XML 汇总 `474` 个用例，`0` failures、`0` errors、`0` skipped |
| 生产身份权限与访问审计 | 统一认证、角色/字段级授权、导出审批、水印和审计平台 | 未执行 | 不记录敏感原文受控查看、生产隐私验收、渗透测试或现场核验通过 |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和文档无空白符错误；本轮未修改 Mapper XML 或数据库结构 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`releaseBlockerCount=4` |

### 173.3 当前判断

1. 后台运营查询不再从本轮覆盖的列表、退款详情和 CSV 直接返回客户或服务者姓名原文。
2. 默认脱敏没有删除或修改数据库事实，也不影响交易链路；后续敏感原文查看必须建立在可信身份、字段级授权和访问审计之上。
3. `test -> master` 与 `release/*` 仍需以统一门禁复判为准，本功能通过不能替代正式候选说明、签字、发布后现场核验或稳定观察结论。

## 174. 2026-09-09 统一支付记录与退款动作出参最小化 V1.0 验证

### 174.1 本轮补强

1. 统一支付记录列表复制 Mapper DTO 后隐藏 `userId / userPaymentChannelId` 中由客户姓名派生的后缀，查询仍使用原始筛选值。
2. 支付记录详情在独立副本上脱敏用户引用、客户端 IP、幂等键和请求/响应 JSON，再装配路由、回调和事件轨迹；Mapper 详情 DTO 不被修改。
3. 退款申请、批准、成功、失败和重试五类动作响应统一返回客户姓名脱敏副本，渠道提交和状态 CAS 更新仍使用原始事实。
4. 银行卡号当前由 Mapper 固定输出 `54**54`，本轮不伪造真实卡号接入或生产卡数据验证。

### 174.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 记录与退款动作定向测试 | `mvn -Dtest=PaymentSensitiveDataMaskerTest,PaymentRecordServiceListTest,PaymentRecordServiceImplTest,RefundServiceImplTest test` | 通过 | `20` 个用例通过；覆盖用户引用、详情请求上下文、三类轨迹、退款动作响应和 Mapper DTO 原值保持 |
| 后端全量测试 | `mvn test` | 通过 | Surefire XML 汇总 `477` 个用例，`0` failures、`0` errors、`0` skipped |
| 生产身份权限与卡数据 | 统一认证、字段级授权、真实卡数据、访问审计与渗透测试 | 未执行 | 不记录真实卡号、生产隐私验收、授权查看或现场核验通过 |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和文档无空白符错误；本轮未修改 Mapper XML 或数据库结构 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`releaseBlockerCount=4` |

### 174.3 当前判断

1. 统一支付记录详情和退款动作响应不再成为本轮覆盖敏感字段的旁路，列表与详情默认脱敏口径趋于一致。
2. 数据库与 Mapper 原始事实继续供内部交易和受控排障使用；默认脱敏不能替代后续可信身份与字段级授权。
3. `test -> master` 与 `release/*` 仍需以统一门禁复判为准，不以本轮测试通过替代正式放行治理事实。

## 175. 2026-09-09 异常告警供应商回执出参最小化 V1.0 验证

### 175.1 本轮补强

1. 异常告警列表在 Service 对外边界复制 Mapper DTO，并对 `providerReceiptSnapshot` 中的嵌套姓名、手机号和银行卡号等字段执行统一脱敏。
2. CSV 复用同一出参转换；任意无法可靠解析的供应商 HTTP 响应或传输快照统一返回 `[REDACTED]`，避免原始响应扩散到下载文件。
3. 首次确认和已确认幂等返回路径均返回脱敏副本；回执状态判断、确认写入和来源告警同步仍使用原始 DTO。
4. `receiver` 当前为值班团队标签，`alertContent / renderedContentSnapshot` 为内部运营告警正文，本轮保留排障可用性，不做无证据遮蔽。

### 175.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 异常告警回执脱敏定向测试 | `mvn -Dtest=PaymentIssueServiceImplTest test` | 通过 | `12` 个用例通过；覆盖列表嵌套 JSON、CSV 非 JSON、首次确认、已确认幂等返回和 Mapper DTO 原值保持 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 文本报告汇总 `478` 个用例，`0` failures、`0` errors、`0` skipped |
| 生产供应商与权限验收 | 真实企业 IM、短信、邮件回执，统一认证、字段级授权与访问审计 | 未执行 | 不记录真实供应商联调、生产敏感数据、授权查看、渗透测试或现场核验通过 |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和文档无空白符错误；本轮未修改 Mapper XML、数据库结构或告警状态机 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`releaseBlockerCount=4` |

### 175.3 当前判断

1. 异常告警列表、CSV 和确认动作不再直接透传任意供应商原始响应；结构化回执保留非敏感诊断信息，非结构化回执失败闭合。
2. 数据库与 Mapper 原始回执继续供内部确认和受控排障使用；默认最小化不能替代可信身份、字段级授权和访问审计。
3. `test -> master` 与 `release/*` 仍需以统一门禁复判为准，不以本轮测试通过替代正式候选说明、签字、发布后现场核验或稳定观察结论。

## 176. 2026-09-09 异常告警供应商回执真实格式兼容 V1.1 验证

### 176.1 本轮补强

1. 复核通知器实际持久化格式后，确认外部响应使用 `HTTP_RESPONSE:{...}`，而非无前缀 JSON；第 175 节的通用 `maskPayload` 会将该真实格式整体判为非 JSON。
2. `PaymentSensitiveDataMasker` 新增供应商回执专用转换，识别受控大写来源前缀后对 JSON 正文递归脱敏，并保留来源标识及非敏感诊断字段。
3. 原始无前缀对象或数组 JSON 继续兼容；带来源前缀的非 JSON、JSON 标量和任意异常文本继续失败闭合为 `[REDACTED]`。
4. 异常告警列表、CSV 和两类确认返回路径仍共享同一副本转换，不改写 Mapper DTO、数据库回执或通知器输入输出事实。

### 176.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 真实格式兼容定向测试 | `mvn -Dtest=PaymentSensitiveDataMaskerTest,PaymentIssueServiceImplTest test` | 通过 | `20` 个用例通过；覆盖 `HTTP_RESPONSE:` 嵌套 JSON、无前缀 JSON、带前缀传输异常、列表及既有 CSV/确认路径 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 文本报告汇总 `480` 个用例，`0` failures、`0` errors、`0` skipped |
| 真实供应商响应核验 | 企业 IM、短信、邮件生产响应样本与权限审计 | 未执行 | 本轮只依据仓库通知器生成格式和本地测试，不记录真实供应商联调、生产数据或隐私验收通过 |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和文档无空白符错误；本轮未修改 Mapper XML、数据库结构或通知器持久化格式 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`releaseBlockerCount=4` |

### 176.3 当前判断

1. 供应商回执出参最小化已与通知器真实 `HTTP_RESPONSE:{...}` 存储格式对齐，结构化诊断信息不再因来源前缀被无差别丢弃。
2. 非结构化传输异常仍不会通过告警列表、CSV 或确认响应扩散；默认遮蔽不能替代后续真实供应商样本核验和生产字段级授权。
3. `test -> master` 与 `release/*` 仍需以统一门禁复判为准，不以本轮测试通过替代正式放行治理事实。

## 177. 2026-09-09 异常告警传输异常摘要最小化 V1.0 验证

### 177.1 本轮补强

1. IM、SMS、EMAIL 三类 Webhook 通知器发生 `RestClientException` 时，原始异常只写入内部 `providerReceiptSnapshot`，可见的 `providerDeliveryMessage` 固定输出 `transportError=[REDACTED]`。
2. 派发服务在持久化任意通知器结果前再次截断 `transportError=` 后的内容，防止自定义通知器绕过内置实现的最小化规则。
3. 通知器直接抛出运行时异常时，内部快照保留原始故障证据，运营摘要固定为“通知器执行异常”；主备供应商切换逻辑保持不变。
4. 供应商业务状态、HTTP 状态、失败码、超时配置和回执号继续可见，不把故障摘要最小化扩大成无差别隐藏。

### 177.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 告警传输异常定向测试 | `mvn -Dtest=LocalImPaymentIssueAlertNotifierTest,LocalSmsPaymentIssueAlertNotifierTest,LocalEmailPaymentIssueAlertNotifierTest,PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `41` 个用例通过；覆盖三类内置通知器、自定义失败结果、运行时异常和主备切换 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 文本报告汇总 `480` 个用例，`0` failures、`0` errors、`0` skipped |
| 生产故障证据治理 | 原文访问授权、下载审计、保留期限、加密与真实供应商异常样本 | 未执行 | 不记录生产权限、隐私验收、渗透测试或真实异常联调通过 |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和文档无空白符错误；本轮未修改 Mapper XML、数据库结构或告警状态机 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`releaseBlockerCount=4` |

### 177.3 当前判断

1. 底层传输异常不再通过告警明细、CSV 或异常中心聚合摘要扩散，运营仍可依据通道、HTTP 状态、超时和失败分类排障。
2. 内部快照保留原始故障证据并不等于已具备可信授权；生产前仍需补字段级访问控制、审计、加密和保留期限治理。
3. `test -> master` 与 `release/*` 仍需以统一门禁复判为准，不以本轮测试通过替代正式放行治理事实。

## 178. 2026-09-09 支付事件与死信任务出参最小化 V1.0 验证

### 178.1 本轮补强

1. 支付事件列表、CSV 和重发后的刷新列表在 Service 对外边界复制 DTO，并对 `eventPayload` 递归脱敏；Mapper DTO 与数据库原始事件保持不变。
2. 死信任务列表、待重放、人工结案和重放动作响应复制实体，并对 `payloadSnapshot / headerSnapshot` 执行统一脱敏；重放消息仍从持久化原始任务构造，不把展示副本发送给消费者。
3. 脱敏字段扩展到授权头、访问令牌、API/访问密钥、密钥、签名、私钥及 Cookie，并统一识别大小写、连字符和下划线命名；畸形载荷或消息头 JSON 继续失败闭合为 `[REDACTED]`。
4. 消息代理重放异常不再写入可见 `resolutionNote`，统一记录 `重放发送失败：[REDACTED]`；原始异常没有通过本轮对外接口扩散。

### 178.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 事件与死信脱敏定向测试 | `mvn -Dtest=PaymentSensitiveDataMaskerTest,PaymentEventServiceImplTest,PaymentDeadLetterTaskServiceImplTest test` | 通过 | `24` 个用例通过；覆盖凭据字段、事件列表/CSV、死信列表/动作响应、Mapper 原值保持、原始载荷重放和异常摘要遮蔽 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 文本报告汇总 `483` 个用例，`0` failures、`0` errors、`0` skipped |
| 生产身份权限与隐私治理 | 统一认证、字段级授权、原文访问审计、加密、保留期限与真实消息代理故障样本 | 未执行 | 不记录生产 IAM、审计、隐私验收、渗透测试或现场核验通过 |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和文档无空白符错误；本轮未修改 Mapper XML、数据库结构、事件状态机或重放路由 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`releaseBlockerCount=4` |

### 178.3 当前判断

1. 支付事件与死信处置的页面、下载和动作响应不再直接透传载荷或消息头中的凭据与个人信息，畸形结构按失败闭合处理。
2. 重放路径仍使用数据库原始载荷，保证展示最小化不改变业务消息；原始事实保留不等于已具备生产敏感原文访问授权。
3. `test -> master` 与 `release/*` 仍需以统一门禁复判为准，不以本轮自动化通过替代正式审批、生产迁移、真实消息代理联调或稳定观察结论。

## 179. 2026-09-09 未知系统异常出参与日志最小化 V1.0 验证

### 179.1 本轮补强

1. `GlobalExceptionHandler` 对未预期 `Exception` 固定返回 `PAYMENT-500 / system error`，不再把异常消息中的数据库地址、端点、SQL 或凭据片段返回前端。
2. 响应继续生成 `requestId`；服务端错误日志只记录同一请求号和异常类型，不记录异常消息与堆栈，避免把敏感内容从 HTTP 响应搬到普通日志。
3. `BusinessException` 的领域错误码和业务提示保持不变，`IllegalArgumentException` 的明确参数校验提示保持不变，不把系统异常最小化扩大为所有错误无差别隐藏。
4. 测试使用包含模拟密码和内部 JDBC 地址的异常消息，校验响应与日志均不包含原文，并校验日志请求号与响应一致。

### 179.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 全局异常边界定向测试 | `mvn -Dtest=GlobalExceptionHandlerTest test` | 通过 | `3` 个用例通过；覆盖业务错误、参数错误、未知异常固定出参、请求号关联和日志原文阻断 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 文本报告汇总 `483` 个用例，`0` failures、`0` errors、`0` skipped |
| 生产观测与权限验收 | 集中日志平台、受控错误详情、日志 IAM、保留期限、告警与真实故障样本 | 未执行 | 不记录生产日志治理、故障演练、渗透测试或现场核验通过 |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和文档无空白符错误；本轮未修改接口结构、错误码枚举、数据库结构或业务状态机 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`releaseBlockerCount=4` |

### 179.3 当前判断

1. 未知系统异常不再通过统一 HTTP 错误响应或普通服务日志扩散底层异常消息，前端仍可用 `PAYMENT-500 + requestId` 反馈问题。
2. 只记录异常类型会降低本地根因细节；在没有受控错误详情仓和可信日志权限前，这是数据最小化优先的明确取舍，不宣称生产可观测性已经完整。
3. `test -> master` 与 `release/*` 仍需以统一门禁复判为准，不以本轮本地测试替代正式审批、真实故障验证或稳定观察结论。

## 180. 2026-09-09 请求追踪号并发安全 V1.0 验证

### 180.1 本轮补强

1. `RequestIdHolder` 不再共享线程不安全的 `SimpleDateFormat`，改用不可变 `DateTimeFormatter` 生成 17 位毫秒时间前缀。
2. 原三位随机数字后缀只有 900 种取值，改为 8 位 UUID 十六进制后缀；请求号保持 `REQ` 前缀和时间可读性，同时降低同毫秒并发与多实例碰撞风险。
3. 新增并行生成 10,000 个请求号的自动化测试，统一校验 `REQ + 17 位数字 + 8 位十六进制` 格式和集合唯一性。
4. `ApiResponse.success / failure` 继续通过同一生成器创建请求号，上一批未知异常响应与日志关联逻辑无需增加平行实现。

### 180.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 请求号与异常边界定向测试 | `mvn -Dtest=RequestIdHolderTest,GlobalExceptionHandlerTest test` | 通过 | `4` 个用例通过；并发生成 10,000 个请求号全部唯一且格式合法，异常日志与响应请求号一致 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 文本报告汇总 `484` 个用例，`0` failures、`0` errors、`0` skipped |
| 生产追踪验证 | 多实例峰值压测、网关透传、跨服务 trace/span、集中日志检索与碰撞监测 | 未执行 | 不记录生产容量、全链路追踪或全局唯一性验收通过 |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和文档无空白符错误；本轮未修改响应字段、错误码、数据库结构或业务状态机 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`releaseBlockerCount=4` |

### 180.3 当前判断

1. 请求号生成器已具备进程内并发安全，碰撞空间较三位随机数显著扩大，能够更可靠地支撑错误响应与日志关联。
2. 8 位 UUID 后缀是工程上的低碰撞实现，不是数学上的全局唯一证明；跨系统追踪仍需网关、消息和下游服务共同透传上下文。
3. `test -> master` 与 `release/*` 仍需以统一门禁复判为准，不以本轮本地并发测试替代生产压测、正式审批或稳定观察结论。

## 181. 2026-09-09 MyBatis SQL 与绑定参数日志默认关闭 V1.0 验证

### 181.1 本轮补强

1. `application.yml` 显式配置 MyBatis `NoLoggingImpl`，不再把 SQL 语句、查询结果和绑定参数交给普通日志实现；普通日志根级别升到 DEBUG 也不会恢复 MyBatis 参数输出。
2. 仓库内 13 个手工创建 MyBatis `Configuration` 的 Mapper 集成测试显式设置同一日志实现，避免绕过 Spring Boot 自动配置形成测试日志旁路。
3. 新增配置契约测试，直接校验 `mybatis.configuration.log-impl` 的安全默认值，防止后续配置整理时静默回退。
4. 关闭 MyBatis 日志会牺牲 SQL 文本和参数级诊断便利性；生产排障应使用受控指标、链路追踪、慢查询设施或单独治理的工具。

### 181.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| MyBatis 日志定向测试 | `mvn -Dtest=PaymentMyBatisLoggingConfigurationTest,PaymentConfigChannelReviewMapperIntegrationTest,PaymentConfigControlPolicyReviewMapperIntegrationTest,PaymentConfigGatewayReviewMapperIntegrationTest test` | 通过 | `4` 个用例通过；配置契约与三类含敏感参数的 Mapper 路径均未输出 SQL 或绑定参数 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 文本报告汇总 `485` 个用例，`0` failures、`0` errors、`0` skipped；完整日志无 `Preparing:`、`Parameters:`、模拟密钥、公钥或访问令牌绑定值 |
| 生产日志与观测治理 | 日志 IAM、指标、trace、慢查询授权、保留期限、真实负载与隐私验收 | 未执行 | 不记录生产日志权限、可观测性、隐私审计或现场核验通过 |
| 补丁检查 | `git diff --check` | 通过 | 配置、Java 测试和文档无空白符错误；本轮未修改 Mapper XML、数据库结构、接口字段或业务状态机 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 待复判 | 预期仍由 release 治理事实决定，不以本轮日志安全回归替代正式放行条件 |

### 181.3 当前判断

1. 支付配置、凭据和交易参数不再因 MyBatis DEBUG 日志进入普通测试或应用日志，且应用与手工 Mapper 测试配置保持一致。
2. 失去 SQL 文本和参数级日志是数据最小化优先的明确取舍；本轮没有建立生产指标、链路追踪、慢查询授权或受控原文诊断能力。
3. `test -> master` 与 `release/*` 仍需以统一门禁复判为准，不以本轮自动化通过替代正式审批、生产观测或隐私验收结论。

## 182. 2026-09-09 主动查单失败通知快照最小化 V1.0 验证

### 182.1 本轮补强

1. `PaymentServiceImpl.recordChannelQueryFailure` 不再把任意 `BusinessException.message` 复制到通知日志 `responsePayload`，只保留错误码和固定 `channel query failed` 文案。
2. 测试异常消息加入模拟内部 JDBC 地址和令牌，验证持久化参数严格等于固定快照，避免后续渠道适配器或异常包装把底层详情带入支付轨迹。
3. 原异常仍向调用方抛出，`QUERY_FAILED` 状态、`danger` 类型、渠道和查单来源保持不变；失败统计、自动补偿退避和事务边界不受影响。

### 182.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付服务定向测试 | `mvn -Dtest=PaymentServiceImplTest test` | 通过 | `36` 个用例通过；含模拟数据库地址和令牌的查单异常只形成固定通知响应快照 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 文本报告汇总 `485` 个用例，`0` failures、`0` errors、`0` skipped；完整日志未出现模拟地址或 `query-secret` |
| 生产数据与渠道验收 | 历史快照清理、字段级权限、访问审计、保留期限与真实渠道故障样本 | 未执行 | 不记录历史数据已迁移、生产安全治理、真实渠道联调或现场核验通过 |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和文档无空白符错误；本轮未修改 Mapper、数据库结构、接口字段或业务状态机 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 待复判 | 仍以 release 治理事实决定，不以本轮快照最小化替代正式放行条件 |

### 182.3 当前判断

1. 新产生的主动查单失败通知快照不再保存任意异常消息，运营仍可依赖错误码、状态、渠道和支付单标识完成分类与补偿判断。
2. 固定快照会减少支付轨迹中的底层错误细节，这是在缺少可信原文权限和审计前的数据最小化取舍；原调用方业务提示仍保留。
3. `test -> master` 与 `release/*` 继续以统一门禁复判为准，不以本轮本地测试替代生产历史清理、真实渠道验证或正式审批。

## 183. 2026-09-09 Mapper SQL 原始字符串替换禁用 V1.0 验证

### 183.1 本轮补强

1. 新增 `PaymentMapperSqlBindingSafetyTest`，通过 `classpath*:mapper/*.xml` 枚举运行时全部 Mapper XML，并逐份拒绝 `${` 原始字符串替换标记。
2. 当前 22 份 Mapper XML 均只使用 `#{...}` 参数绑定或 `<choose>` 固定 SQL 分支；排序字段和方向不直接拼接调用方输入。
3. 测试不锁定文件名或文件数量，后续新增 Mapper XML 会由 classpath 通配符自动纳入，避免安全清单随模块增长失效。

### 183.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| Mapper SQL 绑定契约测试 | `mvn -Dtest=PaymentMapperSqlBindingSafetyTest test` | 通过 | `1` 个用例通过；运行时发现的全部 Mapper XML 均无 `${` 原始替换标记 |
| Mapper 静态复核 | `rg -n '\$\{' backend/src/main/resources/mapper` | 通过 | 当前 22 份 XML 无匹配；动态排序与方向使用 `<choose>` 固定分支 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 文本报告汇总 `486` 个用例，`0` failures、`0` errors、`0` skipped |
| 生产数据库安全 | 数据库账号最小权限、业务授权、存储过程审计、真实攻击与 SQL 审计 | 未执行 | 不记录生产数据库权限、渗透测试或现场安全验收通过 |
| 补丁检查 | `git diff --check` | 通过 | Java 测试与文档无空白符错误；本轮未修改生产 SQL、数据库结构、接口字段或业务状态机 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 待复判 | 仍由 release 治理事实决定，不以 Mapper 契约测试替代正式放行条件 |

### 183.3 当前判断

1. MyBatis XML 原始字符串替换已从人工约定升级为自动化门禁，后续新增 Mapper 会默认接受同一检查。
2. 禁止 `${...}` 能关闭一类直接 SQL 拼接入口，但不能证明所有业务授权、数据库权限、存储过程和生产运行环境均安全。
3. `test -> master` 与 `release/*` 继续以统一门禁复判为准，不以本轮本地静态契约与回归测试替代正式审批或生产安全验收。

## 184. 2026-09-09 三端收银台 URL 令牌最小化 V1.0 验证

### 184.1 本轮补强

1. APP 与 H5/PC 共用业务入口在创建预付单前，按来源应用将演示接入令牌写入 `sessionStorage`，后续收银台路由不再携带 `accessToken` 或冗余 `sourceAppId`。
2. 收银台从当前终端会话键读取令牌并仅放入支付提交 JSON；收银台到结果页、结果页返回收银台均不再透传令牌。
3. 收银台与结果页兼容旧版含令牌链接：先迁移到对应会话键，再通过 `router.replace` 清除当前 URL；会话存储不可用时业务入口失败闭合。
4. 新增会话存储纯函数测试，覆盖来源隔离、存储异常和 query 非破坏性清理；三端继续复用既有页面与接口层，不新增平行支付流程。

### 184.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 会话令牌单元测试 | `npm test`（`app-web`） | 通过 | Node Test Runner `3` 个用例通过；覆盖按来源存取、存储异常失败闭合和旧 query 清理 |
| APP 生产构建 | `npm run build`（`app-web`） | 通过 | Vite `33` 个模块转换完成 |
| H5 生产构建 | `npm run build`（`h5-web`） | 通过 | Vite `36` 个模块转换完成，共享业务入口与收银台解析正常 |
| PC 生产构建 | `npm run build`（`pc-web`） | 通过 | Vite `37` 个模块转换完成，共享业务入口与桌面收银台解析正常 |
| 本地浏览器路由验证 | `http://127.0.0.1:5185` | 通过 | APP 业务入口正常渲染；旧收银台和结果页链接中的模拟令牌均被 `router.replace` 清除，非敏感业务参数保留；后端代理返回 `500`，未记录支付接口联调通过 |
| 生产身份与浏览器安全 | 可信登录、服务端会话、短期凭据、CSP、XSS、真实终端联调 | 未执行 | 不记录演示令牌已成为可信凭据、生产认证或浏览器安全验收通过 |
| 补丁检查 | `git diff --check` | 通过 | Vue、JavaScript、测试、package 脚本和文档无空白符错误；本轮未修改后端接口或数据库 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 待复判 | 仍由 release 治理事实决定，不以本轮 URL 最小化替代正式放行条件 |

### 184.3 当前判断

1. 三端新支付流程和页面往返不再把接入令牌写入地址栏，旧链接也会在进入页面后清理，降低历史记录、链接复制和 Referer 扩散风险。
2. `sessionStorage` 不是安全凭据仓，前端演示令牌仍存在于构建产物并受同源脚本影响；生产可信身份与短期凭据仍是明确缺口。
3. `test -> master` 与 `release/*` 继续以统一门禁复判为准，不以本轮本地路由验证替代后端支付联调、生产认证、安全测试或正式审批。

## 185. 2026-09-09 运营查询分页边界 V1.0 验证

### 185.1 本轮补强

1. 新增 `PageQueryBounds`，统一将运营查询页码限制为 `1..1000`、每页限制为 `1..100`，最大 offset 为 `99,900`。
2. 支付单、订单、账单、收银台会话、支付请求、支付流水、支付记录、路由执行、事件、处理日志、异常、告警、退款、服务者结算、任务日志和死信任务共 16 个分页 DTO 均复用该规则。
3. DTO 显式归一化 `getPageNo`，使现有 Service 写回、分页响应和 Mapper offset 口径一致；死信任务 `getLimit` 同时补齐 100 条上限。
4. 新增 classpath 自动发现测试，以当前 16 个对象为最低覆盖基线，对极大和极小整数统一验证，不依赖固定类名清单。

### 185.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 分页边界定向测试 | `mvn -Dtest=PageQueryBoundsTest test` | 通过 | `2` 个用例通过；覆盖全部分页 DTO 的极值页码、offset 和 Mapper limit 边界 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 文本报告汇总 `488` 个用例，`0` failures、`0` errors、`0` skipped |
| 生产查询性能 | 索引分析、游标分页、查询超时、慢查询、归档与真实数据量压测 | 未执行 | 不记录生产查询性能、数据库容量或稳定性验收通过 |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和文档无空白符错误；本轮未修改 Mapper SQL、数据库结构、接口字段或业务状态机 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 待复判 | 仍由 release 治理事实决定，不以本轮分页边界回归替代正式放行条件 |

### 185.3 当前判断

1. 极端分页输入不再产生负 offset 或无限深分页，16 类运营列表在 DTO、Service 响应和 Mapper 参数上形成统一边界。
2. 页码 1000、每页 100 是当前 offset 分页的保护上限，不构成生产大数据量性能承诺；深层历史查询仍需索引、归档或游标分页。
3. `test -> master` 与 `release/*` 继续以统一门禁复判为准，不以本轮自动化通过替代生产容量验证、稳定观察或正式审批。

## 186. 2026-09-09 告警派发失败快照最小化 V1.0 验证

### 186.1 本轮补强

1. 三类 Webhook 通知器不再把底层传输异常消息写入 `providerReceiptSnapshot`，固定保存 `HTTP_TRANSPORT_ERROR:[REDACTED]`。
2. 派发服务捕获通知器运行时异常后只保存失败分类码与遮蔽标记，不再持久化异常消息；可见派发消息继续使用固定运营摘要。
3. 正常 HTTP 响应回执、派发状态、主备供应商切换、回执确认及任务状态机均保持原口径。

### 186.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 告警派发定向测试 | `mvn -Dtest=LocalImPaymentIssueAlertNotifierTest,LocalSmsPaymentIssueAlertNotifierTest,LocalEmailPaymentIssueAlertNotifierTest,PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | 覆盖三类传输异常及通知器运行时异常，断言模拟异常原文不进入消息或持久化失败快照 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 文本报告汇总 `488` 个用例，`0` failures、`0` errors、`0` skipped |
| 历史数据与生产治理 | 历史记录清理、加密存储、可信权限、访问审计、保留期限、真实供应商故障样本 | 未执行 | 不记录历史异常原文已清理或生产隐私验收通过 |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和文档无空白符错误；未修改表结构、接口字段或业务状态机 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`releaseBlockerCount=4` |

### 186.3 当前判断

1. 新增告警派发失败事实不再长期保存底层异常原文，排障仍可使用失败分类、通道、超时和状态信息。
2. 正常供应商响应仍按既有回执快照规则留存并在对外查询时脱敏，本轮不改变真实业务回执的审计口径。
3. 本地测试不能替代历史数据处置、生产数据权限、独立受控诊断设施或真实供应商故障验收。

## 187. 2026-09-09 告警供应商正常回执落库脱敏 V1.0 验证

### 187.1 本轮补强

1. `AbstractLocalPaymentIssueAlertNotifier` 在完成业务字段解析后，复用 `PaymentSensitiveDataMasker` 对正常 `HTTP_RESPONSE` 落库副本递归脱敏。
2. 回执号、成功码、投递状态和失败码仍从原始响应解析；仅持久化快照改为脱敏副本，三类通知器无需形成平行实现。
3. 嵌套手机号、姓名和访问令牌已纳入回归断言，畸形 JSON 与标量响应沿用失败闭合规则。

### 187.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 回执脱敏定向测试 | `mvn -Dtest=PaymentSensitiveDataMaskerTest,LocalImPaymentIssueAlertNotifierTest,LocalSmsPaymentIssueAlertNotifierTest,LocalEmailPaymentIssueAlertNotifierTest,PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | 覆盖共享脱敏规则、三类通知器和派发服务；回执号与状态保持可用，模拟手机号、姓名和令牌不进入快照 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 文本报告汇总 `488` 个用例，`0` failures、`0` errors、`0` skipped |
| 历史与生产治理 | 历史快照清理、独立原始凭证库、可信权限、访问审计、保留期限、真实供应商回执 | 未执行 | 不记录历史敏感数据已清理或生产隐私验收通过 |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和文档无空白符错误；未修改表结构、接口字段或业务状态机 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`releaseBlockerCount=4` |

### 187.3 当前判断

1. 新增正常供应商回执不再把已知个人信息和凭据字段原文写入普通告警日志，同时保留业务解析所需的结构化结果。
2. 脱敏后的快照用于普通运营审计；若生产确需原始供应商凭证，应进入具备加密、可信权限、访问审计和保留期限的独立设施。
3. 本地模拟响应不能替代历史数据处置、真实供应商字段盘点或生产隐私验收。

## 188. 2026-09-09 告警供应商结果字段容量保护 V1.0 验证

### 188.1 本轮补强

1. `PaymentIssueAlertDeliveryServiceImpl` 在统一结果归一化边界限制五个供应商可控字段长度，与 `t_payment_issue_alert_log` 当前列容量一致。
2. 规则同时覆盖内置和自定义通知器；空值默认、传输异常遮蔽、主备切换及回执确认逻辑保持不变。
3. 新增超长自定义通知器结果用例，验证主供应商记录成功收敛并继续备用供应商。

### 188.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 派发服务定向测试 | `mvn -Dtest=PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | 超长回执号、状态、说明、渲染内容和快照分别收敛到 `128/64/255/1024/2048` 字符，备用供应商继续成功 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 文本报告汇总 `489` 个用例，`0` failures、`0` errors、`0` skipped |
| 生产数据库与供应商容量 | MySQL 严格模式、真实报文字段盘点、字节编码容量、峰值派发压测 | 未执行 | 不记录生产数据库兼容性、供应商 SLA 或容量验收通过 |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和文档无空白符错误；未修改表结构、接口或状态机 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`releaseBlockerCount=4` |

### 188.3 当前判断

1. 第三方通知器不能再用超长结果字段让普通告警日志插入因列容量失败，主备派发的故障隔离能力得到补强。
2. 当前上限来自仓库内两份一致的 schema，未来数据库迁移调整列容量时必须同步修改归一化常量与测试。
3. 字符截断保护不能替代生产数据库模式核验、供应商契约测试或真实负载验证。

## 189. 2026-09-09 告警供应商诊断码最小化 V1.0 验证

### 189.1 本轮补强

1. 共享通知器基类新增诊断码白名单，`providerStatus`、`failureCode` 和成功码期望/实际值只允许 `A-Z/a-z/0-9/._:/-` 且最长 64 位。
2. 不符合约束的值在 `providerDeliveryMessage` 中固定为 `[REDACTED]`，避免自由文本、换行或凭据片段进入普通日志与运营页面。
3. 原始值继续参与配置状态匹配，标准状态码与失败码展示保持不变。

### 189.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 三类通知器定向测试 | `mvn -Dtest=LocalImPaymentIssueAlertNotifierTest,LocalSmsPaymentIssueAlertNotifierTest,LocalEmailPaymentIssueAlertNotifierTest test` | 通过 | 标准码正常展示；带换行的状态与带空白的失败值均显示 `[REDACTED]`，原文不进入投递说明 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 文本报告汇总 `490` 个用例，`0` failures、`0` errors、`0` skipped |
| 真实供应商契约 | 真实码表、异常样本、独立取证设施与生产安全测试 | 未执行 | 不记录真实供应商诊断字段或生产日志安全验收通过 |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和文档无空白符错误；未修改接口字段、数据库或状态机 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`releaseBlockerCount=4` |

### 189.3 当前判断

1. 供应商可控自由文本不能再通过配置指针进入普通投递说明，标准可运营诊断码仍保留。
2. 业务成功与状态映射继续基于原始响应比较，展示最小化不改变派发结论。
3. 白名单不能替代真实供应商码表盘点、受控原文取证、生产访问审计或渗透测试。

## 190. 2026-09-09 超时支付逐笔关单隔离 V1.0 验证

### 190.1 本轮补强

1. `PaymentExpiryTaskServiceImpl` 对每笔超时支付单使用独立 `REQUIRES_NEW` 事务，单笔异常回滚后继续处理后续支付单。
2. 批次结果新增处理、成功、失败数量及最多 3 个失败支付单号，任务中心据此写入准确的告警级任务日志和复跑建议。
3. 超时关闭事件号使用毫秒时间戳和 8 位 UUID 十六进制后缀，避免继续依赖支付单号尾段区分同毫秒事件。

### 190.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 超时关单定向测试 | `mvn -Dtest=PaymentExpiryTaskServiceImplTest,PaymentTaskCenterServiceImplTest,PaymentExpirySchedulerTest test` | 通过 | 共 `29` 个用例；模拟 3 笔中 1 笔写入失败，验证 2 次提交、1 次回滚、后续支付继续关闭、事件号不同及任务日志失败摘要 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 文本报告汇总 `491` 个用例，`0` failures、`0` errors、`0` skipped |
| 生产与真实渠道 | 生产数据库、真实渠道关单、分布式多实例调度、峰值批量关单 | 未执行 | 不记录生产事务隔离、真实渠道语义、分布式稳定性或容量验收通过 |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和文档无空白符错误；未修改数据库表结构或外部接口路径 |

### 190.3 当前判断

1. 本地单测已证明应用层能隔离单笔运行时失败，并把失败事实带到任务中心；失败支付单保留原状态，后续调度可自然重试。
2. 当前实现仍是内部本地状态关单，不代表已经调用真实支付渠道完成撤销或关闭。
3. mock 事务结果不能替代生产数据库、并发锁竞争、多实例调度和大批量稳定性验证。

## 191. 2026-09-09 失败退款真实重提与逐笔隔离 V1.0 验证

### 191.1 本轮补强

1. 新增失败退款批次服务，通过独立 `REQUIRES_NEW` 事务逐笔调用退款领域服务；单笔异常回滚后继续执行后续退款。
2. 任务重试现在实际执行 `FAIL -> PROCESSING`、任务操作日志、渠道提交和渠道提交日志，不再由任务中心直接更新 Mapper。
3. 批次结果向任务中心提供处理、成功、失败数量和最多 3 个失败退款单号；退款单号与操作日志号增加随机后缀，降低同毫秒唯一键冲突。

### 191.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 退款重试定向测试 | `mvn -Dtest=RefundServiceImplTest,RefundRetryTaskServiceImplTest,PaymentTaskCenterServiceImplTest,PaymentCompensationSchedulerTest test` | 通过 | 共 `48` 个用例；覆盖渠道重新提交、任务触发人、两条日志号唯一、2 次提交、1 次回滚、失败后继续和任务日志分级 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 文本报告汇总 `493` 个用例，`0` failures、`0` errors、`0` skipped |
| 真实渠道与生产事务 | 渠道幂等、超时查单、防重复退款、生产数据库、多实例调度 | 未执行 | 不记录真实资金退回、生产事务隔离、外部副作用回滚或稳定性验收通过 |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和文档无空白符错误；未修改数据库表结构或外部接口路径 |

### 191.3 当前判断

1. 本地测试已证明任务重试不再只是状态重置，并能隔离单笔运行时失败、保留准确任务事实。
2. 本地数据库事务只能回滚本系统状态，不能撤销外部渠道已受理的请求；真实接入必须以退款单号作为稳定幂等依据并通过查单收口不确定结果。
3. 模拟渠道与 mock 事务不能替代真实渠道协议、生产数据库、并发竞争、重复调度和峰值批量验证。

## 192. 2026-09-09 失败事件逐条重发隔离 V1.0 验证

### 192.1 本轮补强

1. 新增失败事件重发批次服务，人工模式查询全部失败事件，自动模式只查询已到重试时间的失败事件。
2. 每条事件在独立 `REQUIRES_NEW` 事务中复用既有投递服务；投递返回 `false` 时提交失败或死信状态，意外运行时异常仅回滚当前事件并继续批次。
3. 批次结果向任务中心提供处理、成功、失败数量及最多 3 个失败事件号，任务日志据此保留准确的运行事实和处置建议。

### 192.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 事件重发定向测试 | `mvn -Dtest=PaymentEventRetryTaskServiceImplTest,PaymentEventDispatchServiceImplTest,PaymentTaskCenterServiceImplTest,PaymentCompensationSchedulerTest test` | 通过 | 覆盖人工与自动候选口径、成功提交、普通失败状态提交、意外异常回滚、失败后继续及任务日志计数和失败事件摘要 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 文本报告汇总 `495` 个用例，`0` failures、`0` errors、`0` skipped |
| 外部投递与生产环境 | 生产 MQ/HTTP、订阅方幂等、生产数据库、多实例调度、峰值重发 | 未执行 | 不记录真实下游送达、外部副作用回滚、生产事务隔离或稳定性验收通过 |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和文档无空白符错误；未修改数据库表结构或外部接口路径 |

### 192.3 当前判断

1. 本地测试已证明单条意外异常不会中断后续事件，正常投递失败产生的 `FAILED / DEAD_LETTER` 状态能够随该条事务提交。
2. 本地数据库事务不能撤销已经发送到 HTTP 或 MQ 下游的事件；生产消费者必须使用稳定 `eventNo / messageId` 实现幂等，超时或未知结果需依靠查询、对账或受控重放收口。
3. mock 事务和本地适配器测试不能替代生产消息代理、真实 HTTP 下游、消费者防重、数据库锁竞争、多实例租约和容量验证。

## 193. 2026-09-10 告警派发与回执逐条事务隔离 V1.0 验证

### 193.1 本轮补强

1. 异常告警派发按告警建立独立 `REQUIRES_NEW` 事务，把多通道派发日志与源告警状态更新纳入同一条本地原子边界。
2. 回执回查按供应商告警日志建立独立 `REQUIRES_NEW` 事务，把回执状态更新与源告警确认纳入同一条本地原子边界。
3. 单条意外运行时异常回滚当前告警并继续批次，任务结果记录处理、成功、部分失败、全部失败数量及最多 3 个失败告警号。

### 193.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 告警任务定向测试 | `mvn -Dtest=PaymentIssueAlertDeliveryServiceImplTest,PaymentTaskCenterServiceImplTest,PaymentIssueAlertSchedulerTest test` | 通过 | 注入源状态写入异常和源告警确认异常，分别验证当前条回滚、后一条提交、批次继续和失败告警号摘要 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 文本报告汇总 `497` 个用例，`0` failures、`0` errors、`0` skipped |
| 真实供应商与生产事务 | IM/SMS/EMAIL 供应商幂等、真实回执、生产数据库、多实例租约、峰值派发 | 未执行 | 不记录外部通知可回滚、真实送达、防重复通知、生产事务隔离或稳定性验收通过 |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和文档无空白符错误；未修改数据库表结构或外部接口路径 |

### 193.3 当前判断

1. 本地测试已证明单条派发或回执落库异常不会中断后续告警，回执状态与源告警确认不会出现单条内部分提交。
2. 供应商受理属于外部副作用，本地事务回滚后仍可能重复发送；通知器持续传递稳定 `alertNo`，真实供应商或接入网关必须基于该标识防重，现有冷却和重放护栏不等同于 exactly-once。
3. mock 事务与本地通知器不能替代生产数据库、真实供应商幂等契约、回执乱序、锁竞争、多实例重复调度和容量验证。

## 194. 2026-09-10 支付主链路业务编号碰撞保护 V1.0 验证

### 194.1 本轮补强

1. `PaymentServiceImpl` 统一通过业务编号生成方法创建账单、支付单、预付单、路由、尝试、事件和通知/查单日志编号。
2. 编号保留既有业务前缀和毫秒时间，并追加 8 位 UUID 十六进制后缀；事件/通知编号与处理日志来源编号的关联关系保持不变。
3. 告警派发、回执及租约跳过三类任务日志号改为 16 位 UUID 十六进制后缀，不再依赖单一毫秒时间。

### 194.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 主链路编号定向测试 | `mvn -Dtest=PaymentServiceImplTest,PaymentIssueAlertDeliveryServiceImplTest,PaymentExpiryTaskServiceImplTest,RefundServiceImplTest test` | 通过 | 覆盖预付、提交、风控、回调、主动查单、关单及任务链路；新增 1000 次生成器格式与样本唯一性断言 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 文本报告汇总 `499` 个用例，`0` failures、`0` errors、`0` skipped |
| 生产并发与数据库 | 多实例并发、时钟回拨、唯一键冲突监控、峰值生成 | 未执行 | 不记录全局绝对唯一、生产数据库冲突为零或容量验收通过 |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和文档无空白符错误；未修改数据库表结构或外部接口路径 |

### 194.3 当前判断

1. 随机后缀显著降低同 JVM 和多实例同毫秒生成内部业务编号时的碰撞风险，并保持现有字段长度范围和前缀可读性。
2. UUID 随机后缀不是数据库唯一约束的替代品，现有事件、通知和处理日志唯一键仍是最终冲突防线；生产需监控唯一键异常并验证高并发容量。
3. 本次不修改真实渠道交易号生成规则，也不宣称应用层样本测试证明跨地域、长期运行或异常随机源条件下绝对无碰撞。

## 195. 2026-09-10 异常 SLA 告警逐条生成隔离 V1.0 验证

### 195.1 本轮补强

1. 任务中心生产构造器接入事务管理器，SLA 初次告警和升级告警的每条 outbox 写入均使用独立 `REQUIRES_NEW` 事务。
2. 单条写入返回未命中或抛出运行时异常时计入失败并继续批次，不再回滚此前已生成告警或阻断后续候选。
3. 任务日志保留处理、生成、失败数量及最多 3 个失败异常引用；升级失败优先记录来源告警号。

### 195.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| SLA 巡检定向测试 | `mvn -Dtest=PaymentTaskCenterServiceImplTest,PaymentCompensationSchedulerTest,PaymentTaskCenterControllerTest test` | 通过 | 构造 3 条候选并让中间一条写入异常，验证 2 次提交、1 次回滚、第三条继续生成、失败计数和异常号摘要 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 文本报告汇总 `500` 个用例，`0` failures、`0` errors、`0` skipped |
| 生产与并发去重 | 生产数据库、人工并发触发、多实例租约竞争、业务唯一键、峰值巡检 | 未执行 | 不记录生产事务隔离、人工并发完全防重或稳定性验收通过 |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和文档无空白符错误；未修改数据库表结构或外部接口路径 |

### 195.3 当前判断

1. 本地测试已证明单条 outbox 写入异常不会中断 SLA 巡检批次，任务日志能准确暴露部分生成失败。
2. 自动调度仍使用共享数据库租约避免同一时刻重复执行；人工入口没有同等租约，候选查询和插入之间也没有业务唯一键，生产并发防重仍未闭环。
3. 本轮只生成站内 outbox，不代表外部 IM/SMS/EMAIL 已派发；外部送达仍由后续派发与回执链路确认。

## 196. 2026-09-10 异常 SLA 告警数据库级幂等 V1.0 验证

### 196.1 本轮补强

1. 告警表新增可空唯一 `generation_key`，初次活动告警按异常号防重，升级告警按来源告警号防重，外部派发日志保持空值不受唯一索引影响。
2. 数据库唯一键冲突被识别为并发重复跳过，进入 `warningCount`，不计生成成功或处理失败；其他写入异常仍按单条事务回滚并进入 `failCount`。
3. 初次告警确认时释放活动键，允许仍未收口异常在下一轮重新告警；升级告警键永久保留，维持每个来源最多一次升级的既有规则。

### 196.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 幂等定向测试 | `mvn -Dtest=PaymentTaskCenterServiceImplTest,PaymentTaskCenterMapperIntegrationTest,PaymentIssueServiceImplTest test` | 通过 | `46` 个用例覆盖初次/升级键、唯一冲突不覆盖原记录、重复跳过计数、确认释放和重新生成 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 文本报告汇总 `503` 个用例，`0` failures、`0` errors、`0` skipped |
| 生产数据库迁移与并发 | 真实 MySQL 存量回填、锁竞争、人工并发触发、冲突监控、峰值巡检 | 未执行 | 不记录生产迁移完成、历史重复已清理、锁等待可接受或 exactly-once 验收通过 |
| 补丁检查 | `git diff --check` | 待提交前执行 | 覆盖 Java、Mapper、schema、migration、测试和文档空白符检查 |

### 196.3 当前判断

1. H2 MySQL 模式已证明唯一键能拒绝第二条同业务键告警且不覆盖原记录，确认后可以释放初次活动键并重新插入。
2. 唯一键关闭了人工与自动任务在 `NOT EXISTS` 查询后并发插入的重复窗口，但只约束本地 outbox 事实，不约束供应商侧重复通知。
3. 增量脚本会为每组历史待确认初次告警或新版升级告警仅保留最早记录占用幂等键，其余历史重复记录不删除；上线前仍需在真实备份数据上审查与演练。

## 197. 2026-09-10 模拟渠道交易号碰撞保护 V1.0 验证

### 197.1 本轮补强

1. `WechatH5PaymentChannelSubmitAdapter`、`AlipayH5PaymentChannelSubmitAdapter`、`OfflineBankPaymentChannelSubmitAdapter` 和 `LocalPaymentChannelSubmitAdapter` 统一复用模拟渠道交易号生成方法。
2. 编号保留 `WX- / ALI- / BANK- / CHN-` 前缀与 13 位毫秒时间，并追加完整 32 位 UUID 十六进制段，不再依赖单一系统时钟区分同毫秒请求。
3. 渠道响应 JSON 继续引用结构化结果中的同一交易号，不改变返回码标准化、支付尝试状态、支付单回写或查单入参。

### 197.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 渠道提交定向测试 | `mvn -Dtest=PaymentChannelSubmitServiceImplTest,PaymentServiceImplTest test` | 通过 | `41` 个用例通过；每个模拟适配器连续生成 `1000` 个编号，校验格式、样本唯一性及响应报文一致性 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 文本报告汇总 `504` 个用例，`0` failures、`0` errors、`0` skipped |
| 真实渠道与生产并发 | 微信、支付宝、银行真实下单、超时未知结果、供应商幂等、多实例峰值 | 未执行 | 不记录真实渠道交易号规则、重复扣款防护、生产全局唯一或稳定性验收通过 |
| 补丁检查 | `git diff --check` | 待提交前执行 | 覆盖 Java、测试和文档空白符检查 |

### 197.3 当前判断

1. 完整 UUID 随机段关闭了本地模拟适配器纯毫秒编号的明显碰撞窗口，并保持在 `channel_transaction_no VARCHAR(128)` 容量内。
2. 样本唯一性测试只能证明本轮本地生成结果，不能证明长期绝对无碰撞；渠道交易号仍必须作为不透明字符串保存和传递。
3. 模拟编号不是支付提交幂等键。真实渠道适配器必须透传本系统稳定幂等标识，并以供应商实际响应与主动查单结果处理“请求超时但渠道可能已受理”的不确定状态。

## 198. 2026-09-10 支付提交幂等键边界保护 V1.0 验证

### 198.1 本轮补强

1. 支付路由解析后立即构造最终幂等键，并在控制策略校验、风控调用、支付尝试查询、并发令牌占用和渠道提交之前执行边界校验。
2. 最终幂等键必须非空、长度不超过 `128` 字符且不含 ISO 控制字符；非法请求统一返回 `PAYMENT-1028`，字段上限与支付尝试及并发令牌表保持一致。
3. 合法幂等键原值继续参与数据库判重与并发占位；支付请求和风控决策 JSON 快照的全部字符串字段统一转义反斜杠、双引号、退格、换页、回车、换行和制表符。

### 198.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付提交定向测试 | `mvn -Dtest=PaymentServiceImplTest test` | 通过 | `39` 个用例通过；覆盖 `129` 字符拒绝、换行控制字符拒绝、非法请求不进入持久化或渠道调用，以及合法含引号幂等键的原值入库与 JSON 转义 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 文本报告汇总 `506` 个用例，`0` failures、`0` errors、`0` skipped |
| 真实渠道与生产并发 | 渠道幂等字段映射、超时未知结果恢复、生产数据库字符集与索引、多实例并发 | 未执行 | 不记录真实渠道防重复扣款、生产索引边界、未知结果收口或稳定性验收通过 |
| 补丁检查 | `git diff --check` | 通过 | 当前代码、测试和文档无空白符错误 |

### 198.3 当前判断

1. 非法最终幂等键在任何本地或外部副作用前失败闭合，避免数据库截断、快照结构破坏和不同环节使用不同键值。
2. JSON 转义只保证本地快照语法边界，不等同于敏感数据脱敏；查询和展示仍由既有服务端脱敏链路处理。
3. 本轮识别到提交方法仍在本地事务内调用渠道的风险；该风险随后由第 199 节的三阶段事务拆分继续收口，真实渠道和生产环境边界仍以第 199 节为准。

## 199. 2026-09-10 支付提交外部副作用隔离 V1.0 验证

### 199.1 本轮补强

1. 移除支付提交方法覆盖渠道调用的外层事务，复用 `TransactionTemplate` 建立独立 `REQUIRES_NEW` 本地预提交与结果事务，渠道适配器调用位于两次本地提交之间。
2. 预提交事务先持久化并发令牌、支付中收银台状态、`WAIT_CALLBACK` 支付状态、路由事实和幂等支付尝试；支付尝试初始状态为“渠道提交中”，保证进程在渠道调用前后中断时仍有可查询事实。
3. 渠道返回后，结果事务按 `attemptNo` 精确回写响应和尝试状态，再写支付提交事件及通知事实；渠道异常或结果事务回滚时，第三个恢复事务保留渠道响应（若已取得）并标记“结果未知，待查单”，不释放令牌、不恢复待支付。
4. App 收银台及 H5/PC 共享包装页在支付中、待回调、结果待确认、渠道提交中或结果未知时锁定渠道切换、幂等键重置、再次提交和本地关闭，只保留刷新与主动查单。

### 199.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付提交定向测试 | `mvn -Dtest=PaymentServiceImplTest test` | 通过 | `40` 个用例通过；新增用例验证预提交先于渠道调用完成，渠道已返回而结果事务失败时执行“提交、渠道调用、结果回滚、恢复提交”的顺序 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 文本报告汇总 `507` 个用例，`0` failures、`0` errors、`0` skipped |
| 收银台状态单测与构建 | `npm test`（app-web）；`npm run build`（app-web / h5-web / pc-web） | 通过 | `6` 个前端常量用例通过，三个终端生产构建通过；覆盖待确认状态锁定与正常待支付放行 |
| 真实渠道与生产故障 | 真实渠道超时/受理、进程终止、生产数据库传播、多实例竞争、主动查单与对账恢复 | 未执行 | 不记录真实扣款防重、崩溃恢复、生产事务隔离或最终一致性验收通过 |
| 补丁检查 | `git diff --check` | 通过 | 当前 Java、Mapper XML、测试和文档无空白符错误 |

### 199.3 当前判断

1. 本地数据库提交事实现在先于外部渠道调用，后续相同支付单或幂等键会看到 `WAIT_CALLBACK` 与已落库尝试，不再因结果事务失败回到可直接重复下单状态。
2. “结果未知”不是支付失败，也不是支付成功；主动查单、可信回调和后续对账仍是最终状态收口依据，活跃令牌只在成功回调、成功查单或受控关单时释放。
3. 该实现缩小了外部副作用与本地事实脱节窗口，但无法消除预提交后、渠道调用前进程退出的窗口，也不能证明真实渠道支持平台幂等键；生产接入仍需渠道请求号、超时查单、监控告警与故障演练。

## 200. 2026-09-10 主动查单等待时钟锚定最新尝试 V1.0 验证

### 200.1 本轮补强

1. `PaymentTaskCenterMapper` 的人工批量补查、自动补偿候选、待查积压和到期数量四个查询统一关联每笔支付单最新支付尝试。
2. 首次 `minAgeSeconds` 等待窗口和渠道内候选排序改用最新 `t_payment_attempt.created_at / id`，既有查单历史指数退避继续以最新查询日志时间计算。
3. `WAIT_CALLBACK` 但没有支付尝试的异常记录不进入自动或人工批量查单，避免在缺少渠道提交事实时发起无依据查询。

### 200.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| Mapper 定向集成测试 | `mvn -Dtest=PaymentTaskCenterMapperIntegrationTest test` | 通过 | 覆盖旧支付单加新鲜最新尝试、无尝试异常数据、人工候选、自动指数退避、渠道批次限额及两个积压计数 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 文本报告汇总 `507` 个用例，`0` failures、`0` errors、`0` skipped |
| 生产 SQL 与调度 | 真实 MySQL 执行计划、索引命中、存量异常数据、多实例调度、峰值积压 | 未执行 | 不记录生产查询性能、锁影响、调度无竞争或容量验收通过 |
| 补丁检查 | `git diff --check` | 通过 | 当前 Java、Mapper XML、集成测试和文档无空白符错误 |

### 200.3 当前判断

1. 三阶段提交写入 `WAIT_CALLBACK` 后，补偿等待时间现在从实际最新提交尝试开始，不再继承可能早于渠道调用数分钟的支付单创建时间。
2. 新鲜“渠道提交中”尝试需经过统一最小等待窗口后才可进入批量查单，降低自动调度与正在执行的渠道请求并发查询风险；窗口到期后仍可处理超时或进程中断形成的未知结果。
3. 无支付尝试数据被排除只是失败闭合，不代表异常已修复；生产应通过数据质量告警定位此类 `WAIT_CALLBACK` 记录，并在确认渠道事实后受控处理。

## 201. 2026-09-10 过期支付不确定结果保护 V1.0 验证

### 201.1 本轮补强

1. `PaymentExpiryTaskServiceImpl` 只关闭无渠道流水、无支付尝试且仍为 `PREPAY_CREATED` 的过期支付单，状态更新再次校验全部条件；并发提交已经改变状态时跳过后续尝试、预付单、事件和日志写入。
2. 批量主动查单取消“预付单必须未过期”的限制，已提交 `WAIT_CALLBACK` 在收银台过期后继续进入人工及自动查单，首次等待、指数退避和渠道批次限制保持不变。
3. 渠道成功状态以条件更新抢占收口权。普通关闭不可重开；仅存在 `PAYMENT_EXPIRED_CLOSED` 历史事件的关闭单可由可信成功回调或查单恢复并完成资金相关本地收口。

### 201.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 定向服务与 Mapper 测试 | `mvn -Dtest=PaymentExpiryTaskServiceImplTest,PaymentServiceImplTest,PaymentTaskCenterMapperIntegrationTest,PaymentMapperStateTransitionIntegrationTest test` | 通过 | 相关四个测试类共 `53` 个用例通过；覆盖并发失去关闭资格、并发成功回调、已过期提交单继续查单、普通关闭不可恢复及历史过期误关成功恢复 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 文本报告汇总 `512` 个用例，`0` failures、`0` errors、`0` skipped |
| 真实渠道与生产数据 | 真实渠道关单、迟到回调、查单、历史误关盘点、生产 MySQL 并发 | 未执行 | 不记录真实渠道关闭成功、生产误关已修复、资金账务完全一致或稳定性验收通过 |
| 补丁检查 | `git diff --check` | 通过 | 当前 Java、Mapper XML、测试和文档无空白符错误 |

### 201.3 当前判断

1. 收银台有效期只约束用户发起支付的页面会话，不能作为渠道未受理或支付失败的权威证据；产生支付尝试后必须由可信回调、主动查单、渠道关单结果或对账事实收口。
2. 本轮消除了仅凭本地过期把不确定支付置为不可恢复 `CLOSED` 的路径，并为存量过期误关提供了受限恢复入口，但未新增真实渠道关单能力。
3. 长期非成功的过期 `WAIT_CALLBACK` 会继续按退避查单并进入异常监控；生产仍需接入渠道关闭/撤销协议、明确 `NOT_FOUND / CLOSED / SUCCESS` 码表并执行存量数据核对。

## 202. 2026-09-10 人工关闭渠道提交保护 V1.0 验证

### 202.1 本轮补强

1. `POST /api/payments/close` 改为通过条件更新关闭尚未提交渠道的支付单，允许状态仅为 `PREPAY_CREATED / RISK_REVIEW / RISK_BLOCKED / RISK_REJECTED`，并要求渠道流水为空且不存在支付尝试。
2. 条件更新失败且支付尚未终态时返回 `PAYMENT-1029`，不更新预付单、不写关闭事件且不释放提交令牌；历史已提交误关单的幂等关闭也保留令牌。
3. 收银台响应新增 `paymentOrderId`；App/H5/PC 和后台列表、详情按服务端同一前置口径控制关闭入口，已提交支付只保留主动查单引导。

### 202.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 定向服务与 Mapper 测试 | `mvn -Dtest=PaymentServiceImplTest,PaymentMapperStateTransitionIntegrationTest test` | 通过 | `48` 个用例通过；覆盖收银台支付单号、未提交原子关闭、已提交拒绝、历史误关令牌保留和成功恢复 SQL |
| 后端全量测试 | `mvn test` | 通过 | Surefire 报告汇总 `516` 个用例，`0` failures、`0` errors、`0` skipped |
| 前端测试与构建 | `npm test`（app-web）；`npm run build`（admin/app/h5/pc） | 通过 | App 常量测试 `7` 个通过，四端生产构建通过 |
| 真实渠道与生产数据 | 渠道关闭/撤销、存量误关盘点、生产 MySQL 并发、真实迟到回调与对账 | 未执行 | 不记录真实渠道关闭、生产误关修复、资金一致或稳定性验收通过 |
| 补丁检查 | `git diff --check` | 通过 | 当前代码、测试和文档无空白符错误 |

### 202.3 当前判断

1. 页面禁用只是交互保护，服务端条件更新才是提交与关闭竞态的最终边界；读取到可关闭状态后发生并发提交时，关闭失败闭合为先查单。
2. 本地关闭只表达平台确认从未产生渠道提交事实，不能替代渠道关闭或撤销；一旦存在支付尝试，即使页面过期或支付单历史上已被误关，也必须保留恢复和查单依据。
3. 历史兼容依赖本地事件与尝试事实，生产上线前仍需盘点脏数据、核对渠道账单并设计真实关单返回码和竞态测试。

## 203. 2026-09-10 支付回调渠道绑定保护 V1.0 验证

### 203.1 本轮补强

1. 支付回调在渠道级签名校验后读取支付详情，并要求 URL 渠道与支付单实际路由渠道忽略大小写后一致；支付单未绑定渠道也按不匹配失败闭合。
2. 不匹配统一返回 `PAYMENT-1030`，且不抢占成功状态、不写回调日志、不更新支付尝试和预付单、不记账、不发布事件、不释放提交令牌。
3. 后台支付详情和列表使用服务端支付单渠道发起模拟回调，移除 `WX_H5` 硬编码；无渠道或非 `WAIT_CALLBACK / PAYING` 状态不开放模拟入口。

### 203.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付服务定向测试 | `mvn -Dtest=PaymentServiceImplTest test` | 通过 | `46` 个用例通过；新增跨渠道成功回调拒绝及无状态副作用断言 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 报告汇总 `517` 个用例，`0` failures、`0` errors、`0` skipped |
| 后台构建 | `npm run build`（admin-web） | 通过 | 支付列表与详情按实际渠道模拟回调，生产构建通过 |
| 真实渠道安全 | 域名/IP 白名单、商户号绑定、真实报文验签、证书轮换、重放和乱序 | 未执行 | 不记录真实渠道回调安全或生产验收通过 |
| 补丁检查 | `git diff --check` | 通过 | 当前代码、测试和文档无空白符错误 |

### 203.3 当前判断

1. 签名有效只证明请求持有 URL 所指渠道的凭证，不能证明该渠道拥有目标支付单；支付单渠道绑定是独立且必要的授权边界。
2. 渠道不匹配在任何资金和状态副作用前失败，避免一个渠道的合法凭证跨渠道收口支付；nonce 防重事实仍由既有验签服务管理。
3. 本地模拟回调只用于联调，生产仍需由网关路由到明确渠道入口，并补商户应用绑定、网络来源控制、真实报文适配和密钥生命周期治理。

## 204. 2026-09-10 支付回调渠道交易号绑定保护 V1.0 验证

### 204.1 本轮补强

1. 回调通过渠道绑定后继续校验渠道交易号：支付单已有交易号时要求与回调值精确一致，不做大小写转换或别名归一。
2. 不一致返回 `PAYMENT-1031`，且不更新支付单、尝试、预付单、订单或账单，不写通知/事件、不发布成功事件、不释放提交令牌。
3. 支付单交易号为空时仍允许可信同渠道回调首次绑定，支持渠道已受理但本地提交结果未知的恢复；四端模拟回调优先复用已有交易号。

### 204.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付服务定向测试 | `mvn -Dtest=PaymentServiceImplTest test` | 通过 | `47` 个用例通过；新增同渠道不同交易号拒绝和无副作用断言 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 报告汇总 `518` 个用例，`0` failures、`0` errors、`0` skipped |
| 四端构建 | `npm run build`（admin/app/h5/pc） | 通过 | 后台和三端结果页优先复用已有渠道交易号，生产构建通过 |
| 真实渠道与生产数据 | 商户订单号映射、真实回调、历史串单扫描、生产数据库并发和渠道对账 | 未执行 | 不记录真实渠道交易归属或资金一致性验收通过 |
| 补丁检查 | `git diff --check` | 通过 | 当前代码、测试和文档无空白符错误 |

### 204.3 当前判断

1. 渠道编码一致只证明来自同一渠道适配域，渠道交易号一致才进一步证明回调指向该支付单已经绑定的渠道交易。
2. 交易号按不透明字符串精确比较，避免大小写转换破坏供应商标识；首次绑定仅用于本地尚无交易号的未知结果恢复，不允许覆盖已有事实。
3. 生产仍需将平台支付单号、渠道商户订单号和渠道交易号联合对账，并对历史重复或串联交易号执行数据质量扫描。

## 205. 2026-09-10 支付回调验签默认失败闭合 V1.0 验证

### 205.1 本轮补强

1. `application.yml` 的 `payment.callback.require-signature` 默认值改为 `true`，`PaymentCallbackSignatureServiceImpl` 的注入兜底同步为 `true`，避免配置缺失时放行未签名回调。
2. `application-demo.yml` 显式设置 `payment.callback.require-signature=false`，保留本地四端模拟回调；关闭验签被限定为 demo 联调用途，不作为测试、预发或生产配置方案。
3. 新增配置契约测试，分别读取基础配置与 demo 配置，锁定默认开启和 demo 显式关闭的环境差异。

### 205.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 回调验签定向测试 | `mvn -Dtest=PaymentCallbackSignatureConfigurationTest,PaymentCallbackSignatureServiceImplTest test` | 通过 | `7` 个用例通过；覆盖基础配置默认开启、demo 显式关闭以及 HMAC、RSA2、时间窗和 nonce 防重 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 报告汇总 `520` 个用例，`0` failures、`0` errors、`0` skipped |
| 真实渠道安全 | 渠道独立密钥/公钥、证书轮换、真实通知报文、网络来源与生产重放 | 未执行 | 不记录真实渠道回调安全或生产验收通过 |
| 补丁检查 | `git diff --check` | 通过 | 当前配置、Java、测试和文档无空白符错误 |

### 205.3 当前判断

1. 默认失败闭合消除了部署遗漏环境变量时直接绕过回调验签的风险；验签所需字段或渠道安全配置缺失时，服务继续以明确业务错误拒绝请求。
2. demo 配置只服务本地模拟交互。非 demo 环境即使技术上可覆盖环境变量，也不得将关闭验签作为联调或故障绕过手段，发布检查应阻断该配置。
3. 本轮没有引入或验证真实渠道材料；生产仍需完成渠道商户/应用绑定、密钥托管与轮换、网络白名单、真实报文适配和重放乱序测试。

## 206. 2026-09-10 支付回调渠道凭证隔离 V1.0 验证

### 206.1 本轮补强

1. 移除 `application.yml` 中的全局 `payment.callback.secret`，并删除 `PaymentCallbackSignatureServiceImpl` 的共享密钥字段、构造参数和缺省回退逻辑。
2. HMAC-SHA256 回调只能使用 `t_payment_channel_config` 对应渠道的 `callback_secret`；缺失时返回 `PAYMENT-1006`。RSA2 仍只使用对应渠道公钥。
3. 删除未使用的 `findCallbackSecretByChannelCode` Mapper 方法和 SQL；配置契约新增“全局回调密钥属性不存在”断言。

### 206.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 回调凭证定向测试 | `mvn -Dtest=PaymentCallbackSignatureServiceImplTest,PaymentCallbackSignatureConfigurationTest,PaymentMapperSqlBindingSafetyTest test` | 通过 | `8` 个用例通过；覆盖缺渠道密钥拒绝、配置无全局 secret 和 Mapper 绑定安全 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 报告汇总 `520` 个用例，`0` failures、`0` errors、`0` skipped |
| 真实凭证治理 | 专用密钥托管、密钥/证书轮换、版本绑定与生产审计 | 未执行 | 不记录真实渠道凭证治理或生产验收通过 |
| 补丁检查 | `git diff --check` | 通过 | 当前 Java、配置、Mapper、测试和文档无空白符错误 |

### 206.3 当前判断

1. URL 渠道、支付单渠道与验签凭证现在使用同一渠道配置边界，某渠道缺少凭证时不能借用全局共享密钥继续处理。
2. 数据库字段非空和配置服务校验承担配置入口保护，验签服务仍对存量空值失败闭合；两层校验不能替代密钥托管与运行态配置审计。
3. 本轮未变更 demo 的未签名模拟能力；demo 关闭验签时不会读取渠道凭证，非 demo 环境不得沿用该行为。

## 207. 2026-09-10 支付回调算法运行时白名单 V1.0 验证

### 207.1 本轮补强

1. `PaymentCallbackSignatureServiceImpl` 对渠道签名算法去空白并转为大写后，仅接受精确 `HMAC-SHA256` 或 `RSA2`；删除空值默认 HMAC 和模糊包含 RSA 的分支。
2. 新增 `PAYMENT-1032` 表达渠道回调算法缺失或不支持，与签名不匹配 `PAYMENT-1005`、凭证缺失 `PAYMENT-1006` 分开。
3. 新增空算法与 `RSA-SHA1` 两个运行时拒绝用例，并断言失败前不清理/写入 nonce。

### 207.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 回调算法定向测试 | `mvn -Dtest=PaymentCallbackSignatureServiceImplTest test` | 通过 | `7` 个用例通过；覆盖 HMAC-SHA256、RSA2、空算法、未知算法、缺密钥、时间窗和 nonce 重放 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 报告汇总 `522` 个用例，`0` failures、`0` errors、`0` skipped |
| 真实渠道密码规范 | 算法参数、密钥长度、证书链、密码套件和轮换兼容 | 未执行 | 不记录真实渠道密码能力或生产验收通过 |
| 补丁检查 | `git diff --check` | 通过 | 当前 Java、测试和文档无空白符错误 |

### 207.3 当前判断

1. 配置服务校验保护正常管理入口，运行时白名单保护历史数据、手工 SQL 或迁移异常，两者不能互相替代。
2. 未知算法不应猜测为 HMAC 或 RSA；失败后保留原支付状态，运营应修复渠道配置并使用可信渠道通知或查单收口。
3. 当前只支持本地 HMAC-SHA256 与 RSA2 骨架；接入真实渠道前仍需核对供应商规定的报文规范、编码、密钥规格、证书链和轮换窗口。

## 208. 2026-09-10 支付回调时间戳溢出保护 V1.0 验证

### 208.1 本轮补强

1. 将回调时间窗差值计算从 `Math.abs(nowSeconds - timestampSeconds)` 改为 `Math.subtractExact`，避免减法或绝对值在 `long` 边界溢出。
2. 差值溢出、时间窗非正或差值超出正负窗口均返回 `PAYMENT-1008`，校验继续位于签名计算和 nonce 持久化之前。
3. 新增 `Long.MIN_VALUE` 秒级时间戳且 HMAC 签名合法的攻击向量测试，断言拒绝并且不清理或写入 nonce。

### 208.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 回调时间窗定向测试 | `mvn -Dtest=PaymentCallbackSignatureServiceImplTest test` | 通过 | `8` 个用例通过；新增极小时间戳溢出拒绝和 nonce 无副作用断言 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 报告汇总 `523` 个用例，`0` failures、`0` errors、`0` skipped |
| 真实渠道时间基线 | NTP、渠道时间戳单位、时区、延迟分布和生产窗口 | 未执行 | 不记录真实渠道时间窗或生产验收通过 |
| 补丁检查 | `git diff --check` | 通过 | 当前 Java、测试和文档无空白符错误 |

### 208.3 当前判断

1. 攻击者即使按极端时间戳生成正确签名，也不能利用 Java `long` 溢出绕过新鲜度校验；请求在 nonce 与支付事务前失败。
2. 时间窗非正属于运行配置异常；本轮当时沿用时间戳错误码失败闭合，后续已在第 210 节收口为独立 `PAYMENT-1034` 并移除全局时间窗回退。
3. 本轮只验证本地秒级时间戳数学边界，真实渠道仍需核对毫秒/秒单位、时钟漂移、网络延迟和重放窗口。

## 209. 2026-09-10 RSA2 回调异常分类 V1.0 验证

### 209.1 本轮补强

1. RSA2 签名 Base64 解码失败或 `Signature.verify` 因畸形字节抛出异常时，转换为 `PAYMENT-1005`，不再由全局异常处理器返回 `PAYMENT-500`。
2. RSA2 公钥 Base64 或 X.509 解析失败时返回新增 `PAYMENT-1033`；公钥字段为空仍沿用 `PAYMENT-1006`。
3. 新增畸形签名和损坏公钥用例，并断言两类失败均不清理或写入 nonce。

### 209.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| RSA2 异常定向测试 | `mvn -Dtest=PaymentCallbackSignatureServiceImplTest test` | 通过 | `10` 个用例通过；新增畸形 Base64 签名与损坏 X.509 公钥业务错误断言 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 报告汇总 `525` 个用例，`0` failures、`0` errors、`0` skipped |
| 真实渠道 RSA 验证 | 真实公钥、证书链、签名向量、轮换、吊销和供应商报文 | 未执行 | 不记录真实渠道 RSA2 或生产验收通过 |
| 补丁检查 | `git diff --check` | 通过 | 当前 Java、测试和文档无空白符错误 |

### 209.3 当前判断

1. 外部签名畸形属于通知认证失败，渠道公钥损坏属于内部安全配置异常，两者现在有不同错误码和处置入口。
2. 错误分类不回显公钥或签名字节，避免排障信息扩大敏感材料暴露；详细原因仅保留在异常链路中。
3. 当前仍是 X.509 SubjectPublicKeyInfo 公钥骨架，真实渠道接入需确认 PEM/DER 格式、证书链、算法参数和轮换窗口。

## 210. 2026-09-10 回调时间窗与防重 TTL 配置失败闭合 V1.0 验证

### 210.1 本轮补强

1. 删除 `payment.callback.allowed-skew-seconds` 及其运行时回退，渠道安全配置的 `notifySignWindowSec` 为空或非正时直接返回新增 `PAYMENT-1034`。
2. 构造 `PaymentCallbackSignatureServiceImpl` 时校验 `payment.callback.nonce-ttl-seconds` 必须为正数，显式零值或负值阻止服务启动，避免创建立即过期的 nonce。
3. 增加渠道时间窗空值/零值、nonce TTL 零值/负值和合法 Base64 错误长度 RSA 签名字节用例，并断言拒绝路径不清理或写入 nonce。

### 210.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 回调安全定向测试 | `mvn -Dtest=PaymentCallbackSignatureServiceImplTest test` | 通过 | `14` 个用例通过；覆盖时间窗、nonce TTL 和 RSA 错误长度失败闭合 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 报告汇总 `529` 个用例，`0` failures、`0` errors、`0` skipped |
| 配置契约 | `PaymentCallbackSignatureConfigurationTest` | 通过 | 断言全局时间窗回退不存在；TTL 默认值后续在第 211 节按包含式边界修正为 `601` 秒 |
| 生产配置与重放验证 | 配置中心、真实 MySQL TTL、多实例并发重放与 NTP 基线 | 未执行 | 不记录生产配置或真实防重放验收通过 |
| 补丁检查 | `git diff --check` | 通过 | 当前 Java、YAML、测试和文档无空白符错误 |

### 210.3 当前判断

1. 渠道验签窗口是渠道安全策略，不能由全局值静默代偿；非法配置应修复并走既有渠道复核流程，不能通过扩大窗口恢复回调处理。
2. nonce TTL 是应用级防重放底线，非正值属于不可运行配置；默认值仍需在真实渠道重试周期、最大时间窗和生产容量基础上校准，第 211 节进一步补齐与时间窗的严格关系。
3. 本轮只验证本地配置和服务边界，不代表生产配置中心校验、真实数据库时间语义、多实例竞争或渠道重放攻击已经验收。

## 211. 2026-09-10 回调 nonce 全签名有效期覆盖 V1.0 验证

### 211.1 本轮补强

1. 明确对称时间窗的完整有效期：未来 `W` 秒边界的通知从首次接收到最终失效最多跨 `2W`，且时间窗端点按包含式接受。
2. 运行时要求 `nonceTtlSeconds > 2 * notifySignWindowSec`；等于或短于完整有效期时返回 `PAYMENT-1034`，避免过期清理与时间窗端点相遇后接受重复通知。
3. 默认 nonce TTL 从 `600` 调整为 `601` 秒，与默认 `300` 秒时间窗形成严格大于关系；渠道扩大时间窗时必须同步提高应用级 TTL。

### 211.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 回调安全定向测试 | `mvn -Dtest=PaymentCallbackSignatureServiceImplTest,PaymentCallbackSignatureConfigurationTest test` | 通过 | `17` 个用例通过；覆盖 `W=300 / TTL=600` 等号边界拒绝与 `601` 默认值 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 报告汇总 `530` 个用例，`0` failures、`0` errors、`0` skipped |
| 真实边界与并发重放 | MySQL 秒级到期、多实例竞争、未来边界通知和配置热变更 | 未执行 | 不记录生产防重放或真实渠道验收通过 |
| 补丁检查 | `git diff --check` | 通过 | 当前 Java、YAML、测试和文档无空白符错误 |

### 211.3 当前判断

1. 正数 TTL 只证明记录不会立即过期，不能证明它覆盖整个签名有效期；对称时间窗必须按两倍跨度并额外越过包含式端点计算。
2. 单一应用级 TTL 必须覆盖所有启用渠道中的最大验签窗口；渠道窗口变更未同步调整 TTL 时，回调失败闭合并要求先修复配置。
3. 本轮是本地数学与服务边界验证，真实数据库时间精度、跨节点时钟、清理竞争和渠道未来时间戳分布仍需生产前验证。

## 212. 2026-09-10 支付回调不可信字段边界 V1.0 验证

### 212.1 本轮补强

1. `PaymentServiceImpl.callback` 在验签前校验请求非空，以及 `channel/paymentOrderId/tradeStatus/channelTransactionNo` 的数据库列宽和控制字符；渠道规范化为小写后贯穿验签、绑定、日志与事件。
2. `PaymentCallbackSignatureServiceImpl` 在渠道配置查询前限制 `timestamp` 为 `20`、`nonce` 为 `128`、`signature` 为 `8192` 字符，并拒绝 ISO 控制字符和空请求。
3. 回调通知 JSON 的 `tradeStatus` 与事件 JSON 的渠道字段改用既有 `escapeJson`，避免可信签名中的引号或反斜线破坏审计快照结构。

### 212.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 回调字段定向测试 | `mvn -Dtest=PaymentCallbackSignatureServiceImplTest,PaymentServiceImplTest test` | 通过 | `64` 个用例通过；覆盖业务列宽、控制字符、安全字段体量、空请求和 JSON 转义 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 报告汇总 `532` 个用例，`0` failures、`0` errors、`0` skipped |
| 真实渠道字段验收 | 渠道报文上限、字符集、Base64 方言、网关体量限制与恶意流量 | 未执行 | 不记录真实渠道协议或生产抗压通过 |
| 补丁检查 | `git diff --check` | 通过 | 当前 Java、测试和文档无空白符错误 |

### 212.3 当前判断

1. 回调字段即使最终无法命中支付单，也不能先触发数据库列溢出、密码库大输入或审计 JSON 结构破坏；失败应稳定落在业务校验域。
2. `8192` 是本地签名文本防御上限，不代表所有供应商都允许该长度；真实适配应进一步按渠道密钥规格收紧，并在入口网关限制整体请求体。
3. 本轮没有引入真实网关 WAF、请求速率限制或渠道协议映射，生产前仍需验证 UTF-8/utf8mb4 字符口径、代理路径解码和大报文资源消耗。

## 213. 2026-09-10 回调 nonce 持久化 SQL 契约 V1.0 验证

### 213.1 本轮补强

1. `PaymentCallbackSecurityMapper.insertCallbackNonce` 将到期表达式从 `DATE_ADD(NOW(), INTERVAL expireSeconds SECOND)` 调整为 `TIMESTAMPADD(SECOND, expireSeconds, NOW())`，保持从数据库当前时间增加 TTL 秒数的等价语义。
2. 新增 H2 MySQL 模式 Mapper 集成测试，直接加载正式 Mapper XML 和接口，验证渠道配置查询忽略大小写、nonce 渠道编码小写落库以及创建与到期时间相差 `601` 秒。
3. 同一集成测试验证 `(channel_code, nonce)` 唯一约束拒绝重复写入，并验证将记录置为过期后可由 `deleteExpiredNonce` 清理。

### 213.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 回调安全 Mapper 定向测试 | `mvn -Dtest=PaymentCallbackSecurityMapperIntegrationTest test` | 通过 | `1` 个集成用例通过；覆盖查询、规范化落库、`601` 秒 TTL、唯一键和清理 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 报告汇总 `533` 个用例，`0` failures、`0` errors、`0` skipped |
| 真实 MySQL 验收 | 执行计划、索引与锁、数据库时钟精度、多实例并发和清理容量 | 未执行 | 不记录真实 MySQL 或生产并发验收通过 |
| 补丁检查 | `git diff --check` | 通过 | 当前 XML、测试和文档无空白符错误 |

### 213.3 当前判断

1. `TIMESTAMPADD` 与原 `DATE_ADD` 在当前用例中都表达“以数据库当前时间增加 TTL 秒数”，本轮调整是 SQL 方言兼容收口，不改变 nonce 生命周期安全不变量。
2. H2 MySQL 模式可以验证 Mapper 绑定、列值、唯一键与清理条件，但不能证明真实 MySQL 的执行计划、锁等待、隔离级别或秒级端点行为。
3. 生产放行前仍需使用目标 MySQL 版本执行迁移后回归，并补多实例同 nonce 竞争、数据库时钟基线、清理与插入竞争及容量验证。

## 214. 2026-09-10 回调 nonce 清理隔离与限量调度 V1.0 验证

### 214.1 本轮补强

1. 回调验签通过后只删除当前 `(channel_code, nonce)` 且已经到期的记录，不再在支付回调事务中扫描并删除所有历史过期 nonce；随后仍依赖复合唯一键裁决并发重放。
2. 新增 `PaymentCallbackNonceCleanupScheduler`，默认每 `300000` 毫秒调用签名服务清理最多 `1000` 条历史过期记录；批量 SQL 按 `expires_at, id` 稳定选取主键后删除。
3. Mapper 集成测试直接验证目标清理不影响其他过期记录、当前 nonce 到期后可重新写入、单轮批次数量受限且未过期记录保留；配置非正批次在组件装配前失败闭合。

### 214.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| nonce 清理定向测试 | `mvn -Dtest=PaymentCallbackSecurityMapperIntegrationTest,PaymentCallbackSignatureServiceImplTest,PaymentCallbackNonceCleanupSchedulerTest,PaymentCallbackSignatureConfigurationTest test` | 通过 | `22` 个用例通过；覆盖目标清理、限量批次、服务委派、调度与配置默认值 |
| 强化 Mapper 回归 | `mvn -Dtest=PaymentCallbackSecurityMapperIntegrationTest test` | 通过 | 验证过期后重用、两个过期记录按 `batchSize=1` 分两轮删除、活跃记录保留 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 报告汇总 `536` 个用例，`0` failures、`0` errors、`0` skipped |
| 真实 MySQL 与多实例清理 | 执行计划、锁等待、调度租约、积压告警和容量 | 未执行 | 不记录生产清理治理通过 |

### 214.3 当前判断

1. 请求路径只触碰当前复合唯一键范围，历史数据维护与支付回调事务解耦；这降低锁放大风险，但不等于生产 MySQL 锁行为已验证。
2. 有限批次使单轮事务上界可配置，稳定排序避免无序选择；当到期产生速率持续高于清理速率时仍可能积压，需要监控与容量校准。
3. 本节初版调度尚未接入跨实例租约，后续已在第 215 节复用既有任务租约并修正 owner 隔离；生产仍需增加清理数量/耗时/积压指标，并按目标 MySQL 版本验证执行计划。

## 215. 2026-09-10 自动任务租约 owner 隔离 V1.0 验证

### 215.1 本轮补强

1. `PaymentTaskCenterServiceImpl` 与 `PaymentIssueAlertDeliveryServiceImpl` 将业务 `triggeredBy` 与数据库 `lock_owner` 分离；每个服务实例生成独立 UUID 后缀，日志仍记录稳定触发者，租约使用 `triggeredBy:instanceSuffix`。
2. `acquireTaskLease` 删除 `lock_owner = currentOwner` 重入条件，并将到期计算改为 `TIMESTAMPADD`；同实例定时任务由 Spring `fixedDelay` 串行执行，不需要数据库重入。
3. nonce 清理调度接入相同租约表，未抢到租约时跳过，成功或异常均只用本次实例 owner 释放；旧 owner 在租约过期并被接管后无法释放新 owner 的锁。

### 215.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 租约定向测试 | `mvn -Dtest=PaymentTaskCenterServiceImplTest,PaymentIssueAlertDeliveryServiceImplTest,PaymentTaskCenterMapperIntegrationTest,PaymentCallbackNonceCleanupSchedulerTest test` | 通过 | `66` 个用例通过；覆盖 owner 前缀、获取/释放一致、跳过与 Mapper 租约裁决 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 报告汇总 `539` 个用例，`0` failures、`0` errors、`0` skipped |
| H2 MySQL 租约集成 | 正式 Mapper XML + `t_payment_task_lease` fixture | 通过 | 同 owner 和异 owner在有效期内均不可重入，过期后新 owner 可接管，旧 owner 释放返回 `0` |
| 真实多实例验证 | 进程中止、租约过期接管、长任务超时、锁等待与主从切换 | 未执行 | 不记录生产租约验收通过 |

### 215.3 当前判断

1. 固定 owner 会把不同实例错误识别为同一持有者，允许重入时无法形成互斥；实例唯一后缀与禁止重入共同恢复数据库租约的基本排他语义。
2. owner 同时是释放时的轻量 fencing token：旧任务若执行超过租约并被接管，旧 owner 的 `release` 条件不能清除新 owner；但旧业务动作本身仍可能继续，需要长任务续租或下游幂等共同约束。
3. 当前证据覆盖本地 SQL 与服务调用顺序，不证明生产数据库时间、网络分区、进程暂停、锁超时和多实例压力下的行为；正式部署前仍需故障注入与容量验证。

## 216. 2026-09-10 自动任务长执行续租 V1.0 验证

### 216.1 本轮补强

1. 新增统一租约服务，将初始化、获取、续期和释放放入 `PROPAGATION_REQUIRES_NEW` 事务，使租约事实不再依赖调用方长业务事务提交，并允许后台线程在任务执行期间读取和延长租约。
2. 租约获取成功后按 `leaseSeconds / 3` 周期续期；当前 `120` 秒租约每 `40` 秒尝试延长。续期必须同时满足任务码、完整 owner 和 `lock_expires_at > NOW()`，过期旧 owner 不能自行复活。
3. 租约句柄关闭会取消后台续期并仅释放一次；任务中心、告警派发/回执和 nonce 清理均复用统一句柄，原有跳过、异常释放和业务触发者审计语义不变。

### 216.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 租约定向测试 | `mvn -Dtest=PaymentTaskLeaseServiceTest,PaymentTaskCenterMapperIntegrationTest,PaymentTaskCenterServiceImplTest,PaymentIssueAlertDeliveryServiceImplTest,PaymentCallbackNonceCleanupSchedulerTest test` | 通过 | `70` 个用例通过；覆盖独立事务调用、40 秒续期调度、幂等关闭、owner/有效期续期条件和原调用链回归 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 报告汇总 `542` 个用例，`0` failures、`0` errors、`0` skipped |
| H2 MySQL 租约续期 | 正式 Mapper XML + `t_payment_task_lease` fixture | 通过 | 当前有效 owner 可续期；异 owner 和已过期 owner 续期均返回 `0` |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | `canPromoteTestToMaster=true`、`canCreateRelease=false`；release 仍有候选说明、正式签字、现场核验和稳定观察结束四项真实阻断 |
| 真实多实例长任务 | 双实例运行超过两分钟、网络分区、JVM 暂停、数据库阻塞和主从切换 | 未执行 | 不记录生产续租、故障切换或 fencing 验收通过 |

### 216.3 当前判断

1. 单独增加续期 SQL 无法解决外层长事务可见性问题；租约生命周期必须使用独立短事务提交，业务批处理继续保留自身事务和逐条幂等边界。
2. 周期续期显著降低正常长任务因固定租期被重复接管的风险，但数据库不可用或线程长时间暂停仍可能失去租约；业务状态更新和外部调用仍需幂等，不能把租约等同于强 fencing token。
3. 当前只有本地 H2 与单元测试证据，生产前仍需在目标 MySQL 上验证锁等待上限、续期延迟指标、租约丢失告警和真实双实例故障注入。

## 217. 2026-09-10 支付提交共享数据库限流 V1.0 验证

### 217.1 本轮补强

1. 删除按最近一分钟支付尝试数量先查后判的非原子限流，新增 `t_payment_submit_rate_limit_bucket`，通过 `(scope_type, scope_key)` 唯一键和带阈值条件的原子 `UPDATE` 约束并发占用。
2. `INTERFACE` 范围组合来源应用、终端和客户端 IP，`SOURCE_METHOD` 范围组合来源应用和支付方式；组合值规范化后仅持久化 SHA-256 摘要，不保存客户端 IP 原文。
3. 权限、访问令牌和严格自检先于配额占用；已有相同幂等键支付尝试时跳过占用。接口额度先于来源支付方式额度，并以 `REQUIRES_NEW + noRollbackFor BusinessException` 保留已经消费的额度。

### 217.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 提交限流定向测试 | `mvn -Dtest=PaymentSubmitRateLimitMapperIntegrationTest,PaymentSubmitRateLimitServiceImplTest,PaymentServiceImplTest test` | 通过 | `54` 个用例通过；覆盖双范围、摘要维度、校验顺序、幂等尝试和拒绝错误码 |
| H2 MySQL 并发集成 | 正式 Mapper XML + 两个独立数据库连接 | 通过 | 阈值为 `1` 时两个并发占用只有一个成功，并验证过期固定窗口重置 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `548` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | Java、XML、SQL 和文档无空白符错误 |
| 真实 MySQL 多实例验证 | 锁等待、死锁、热点桶、数据库时间、吞吐和容量 | 未执行 | 不记录生产分布式限流验收通过 |

### 217.3 当前判断

1. 唯一桶加条件更新消除了先查后写窗口，能够在共享主库前提下对多实例形成统一配额裁决；数据库不可用时当前提交失败，不静默退回进程内或非原子限流。
2. 固定窗口在边界附近可能形成突发流量，热点来源会竞争同一行；生产需按目标 MySQL 验证锁等待、连接池占用和阈值容量，必要时再评估网关令牌桶或专用限流设施。
3. 本轮不新增真实渠道、网关 WAF、可信客户端 IP 解析或生产告警结论；代理链中的客户端 IP 必须由可信入口规范化后再参与接口范围。

## 218. 2026-09-10 支付提交客户端 IP 可信边界 V1.0 验证

### 218.1 本轮补强

1. 支付提交 Controller 始终以服务端连接上下文覆盖请求体 `clientIp`，请求体字段只保留兼容性，不能直接成为限流、风控、渠道请求和支付尝试审计的 IP 来源。
2. 新增 `payment.submit.trusted-proxy-addresses` 配置，默认空列表；直接对端不可信时完全忽略 `X-Forwarded-For`，直接对端可信时从右向左跳过已配置代理并选择首个非可信 IP。
3. IPv4 和 IPv6 地址先做数值格式校验与规范化；转发头超过 `2048` 字符、超过 `32` 跳、包含空值/主机名/畸形 IP 或全部为可信代理时回退直接对端，直接对端本身无效时使用 `UNKNOWN`。

### 218.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| Controller 与配置定向测试 | `mvn -Dtest=PaymentControllerTest,PaymentMyBatisLoggingConfigurationTest test` | 通过 | `8` 个用例通过；覆盖配置加载、请求体覆盖、非可信对端、可信代理链、畸形链路、IPv6 和无效直接对端 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `553` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | Java、YAML、测试和文档无空白符错误 |
| 真实代理链验收 | 网关覆写、负载均衡多跳、IPv6、地址漂移和伪造头流量 | 未执行 | 不记录生产客户端 IP 可信性验收通过 |

### 218.3 当前判断

1. 可信边界必须从不可伪造的直接连接开始；只有确认直接对端属于受控代理，转发链才具备解释价值。右向左剥离可避免把调用方前置的伪造地址误当真实客户端。
2. 默认忽略转发头会在未配置代理时把同一网关后的用户聚合到一个接口桶，这是安全侧失败闭合，但部署前必须准确配置实际代理地址，避免误限流。
3. 当前精确 IP 列表适合固定入口，本轮不实现 CIDR、云负载均衡地址自动发现或 PROXY protocol；这些能力需结合实际网络拓扑和基础设施控制面设计。

## 219. 2026-09-10 支付提交不可信字段边界 V1.0 验证

### 219.1 本轮补强

1. `PaymentServiceImpl.submit` 在首次 Mapper 查询前统一处理请求为空、预付单号、支付方式、渠道、来源应用、商户号、访问令牌、终端、客户端 IP、设备号、付款手机号和显式幂等键，避免非法字段先进入查询、路由、风控、限流或数据库列。
2. 客户端字段统一 trim，按业务列宽或受控快照容量设置 `64/32/64/64/128/128/64/128/32` 上限并拒绝 ISO 控制字符；显式幂等键保持 `128` 字符和 `PAYMENT-1028` 专用分类。
3. `APP_WEB/H5_WEB/PC_WEB/MOBILE_WEB` 分别规范化为稳定的 `APP/H5/PC` 限流及审计维度；任意其他终端返回 `PAYMENT-1035`。运营配置补齐的支付方式和路由决策返回的渠道编码均在消费前复验。

### 219.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 服务定向测试 | `mvn -Dtest=PaymentServiceImplTest test` | 通过 | `52` 个用例通过；覆盖查库前拒绝、未知终端、风险字段体量、幂等键提前拒绝、终端别名和运营默认值复验 |
| 服务与控制器联合测试 | `mvn -Dtest=PaymentServiceImplTest,PaymentControllerTest test` | 通过 | `59` 个用例通过，`0` failures、`0` errors、`0` skipped |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `557` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和文档无空白符错误 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`，release 明细仍有 `4` 项真实阻断 |
| 生产边界验证 | 网关请求体、Unicode、真实渠道字段、MySQL 字符集与恶意流量 | 未执行 | 不记录真实渠道或生产抗压验收通过 |

### 219.3 当前判断

1. 限流维度必须来自有限且稳定的终端集合，否则调用方可通过轮换字符串绕过单维度配额；别名兼容应只发生在可信的有限映射中。
2. 客户端字段和运营默认值都属于边界输入，必须在各自首次消费前校验；内部路由结果也要受目标数据库列宽约束，避免配置脏数据把失败推迟到持久化阶段。
3. 本轮是应用服务和本地自动化边界，不覆盖 HTTP 整体报文大小、组合 Unicode 视觉混淆或渠道专属正则；生产前仍需由网关、渠道适配和数据库环境继续收紧。

## 220. 2026-09-10 渠道查单结果字段边界 V1.0 验证

### 220.1 本轮补强

1. 新增 `PaymentChannelQueryResultValidator`，在适配器结果进入返回码映射前校验并规范化；空对象、缺少交易状态或查单来源、超长字段和 ISO 控制字符统一返回 `PAYMENT-1026`。
2. 返回码映射完成后再次校验配置产出的标准码、标准文案、处理建议、可重试/人工介入标志和映射版本，防止历史脏配置把失败推迟到 `t_payment_order` 或通知日志写入。
3. `PaymentServiceImpl.query` 对注入的任意查单服务结果再次复验；非法结果复用既有最小化失败日志和补偿退避事实，不写返回码快照，不触发成功条件更新、账单结清、事件发布或令牌释放。

### 220.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 查单边界定向测试 | `mvn -Dtest=PaymentChannelQueryServiceImplTest,GatewayAccessPaymentChannelQueryAdapterTest,PaymentServiceImplTest test` | 通过 | `62` 个用例通过；覆盖空结果、映射前原始返回码、映射后标准文案和支付服务防御性复验 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `561` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和文档无空白符错误 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`，release 明细仍有 `4` 项真实阻断 |
| 真实网关与渠道验证 | 响应体限制、状态枚举、字符集、恶意大报文和成功结算 | 未执行 | 不记录真实渠道查单或生产安全验收通过 |

### 220.3 当前判断

1. 原始返回码必须在映射查询前受限，否则即使最终数据库快照有列宽，配置查询本身仍会先承受不可信大输入；映射产物也必须二次校验，配置数据不能被视为天然可信。
2. 查单结果非法属于渠道结果不完整，统一使用 `PAYMENT-1026` 并记录受控失败事实，才能继续复用现有补偿退避与监控口径，同时避免把攻击载荷写入普通审计表。
3. 应用字段边界不能替代 HTTP 客户端响应体上限。真实接入还需在网关和 `RestTemplate` 基础设施层限制整体响应体、连接与读取超时，并按渠道协议收紧状态和字符规则。

## 221. 2026-09-10 支付入口标识与预付金额边界 V1.0 验证

### 221.1 本轮补强

1. `detail/cashier/query/close` 对支付单号或预付单号统一执行必填、trim、`64` 字符和 ISO 控制字符校验，非法值在任何 Mapper 或共享查单限流调用前以 `PAYMENT-1036` 拒绝。
2. `prepay` 在按订单号查库前校验请求对象、订单号、支付场景、客户名称、收银台标题和金额；字符串上限对齐表结构，金额只接受不超过 `16` 位整数和 `2` 位小数的正数。
3. 标准订单应收减已付小于或等于零时以 `PAYMENT-1037` 拒绝；订单金额只缺一项时继续以 `PAYMENT-1011` 失败闭合，不把不完整订单误判为虚拟支付来源。

### 221.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付服务定向测试 | `mvn -Dtest=PaymentServiceImplTest test` | 通过 | `57` 个用例通过；覆盖入口标识、预付字段、金额精度和超额支付拒绝 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `565` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | Java、测试与文档无空白符错误 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`，release 明细仍有 `4` 项真实阻断 |
| 生产边界验证 | 真实订单并发结算、MySQL 严格模式、网关体量与 Unicode | 未执行 | 不记录生产支付入口验收通过 |

### 221.3 当前判断

1. 业务标识必须在首次查库前收口，避免将数据库异常、全表扫描风险或不稳定审计维度当成输入校验；同一规范化值贯穿后续查询和状态处理。
2. 创建支付单前必须保证剩余应付严格大于零，已结清和超额支付属于明确业务终态，不应通过数据库默认值容纳零或负金额。
3. 本轮没有增加订单级锁或原子剩余金额裁决；生产前仍需验证创建预付单与支付成功并发、目标 MySQL 隔离行为及跨服务订单事实一致性。

## 222. 2026-09-10 预付单创建订单级并发协调 V1.0 验证

### 222.1 本轮补强

1. 新增 `t_payment_prepay_creation_lock` 及幂等迁移脚本，以 `order_no` 唯一键为标准订单和虚拟支付建立同一套数据库协调边界。
2. `PaymentServiceImpl.prepay` 在字段校验后先初始化协调行并于当前事务内 `FOR UPDATE`，成功持锁后才查询有效预付单、读取金额和创建账单/支付单/预付单；锁行无法读取时以 `PAYMENT-500` 失败闭合。
3. 两套全量 schema、Mapper 接口/XML、数据库 README 和迁移验收 SQL 已同步，避免代码先于运行库表结构发布。

### 222.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 服务与并发 Mapper 定向测试 | `mvn -Dtest=PaymentServiceImplTest,PaymentPrepayCreationLockMapperIntegrationTest test` | 通过 | `59` 个用例通过；覆盖加锁后查重顺序、锁行失败闭合和两个独立事务同订单串行化 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `567` 个用例，`0` failures、`0` errors、`0` skipped |
| schema 与迁移检查 | 双全量 schema + `2026-09-10-add-payment-prepay-creation-lock.sql` | 通过 | 表名、`order_no VARCHAR(64)` 和唯一索引 `uk_prepay_creation_order` 一致 |
| 补丁检查 | `git diff --check` | 通过 | Java、XML、SQL、测试和文档无空白符错误 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`，release 明细仍有 `4` 项真实阻断 |
| 真实 MySQL 多实例验证 | 锁等待、死锁、连接池、吞吐、协调表容量和跨库订单并发 | 未执行 | 不记录生产并发幂等验收通过 |

### 222.3 当前判断

1. 唯一协调行将同订单并发首次创建和存量查重统一到一个可持久化锁点，避免仅锁 `t_order` 时虚拟支付无行可锁，也避免在预付单表上依赖尚不存在的记录。
2. 协调行与业务写入共用事务，后到请求只能在前一事务提交或回滚后继续；持锁后重新查重是幂等成立的关键，不能把锁移动到查重之后。
3. 当前证据为本地 H2 MySQL 兼容模式与单元测试。生产 MySQL 的锁等待策略、死锁恢复、协调表长期增长和跨库订单金额变化仍需真实环境验证。

## 223. 2026-09-10 预付单完整服务事务并发 V1.0 验证

### 223.1 本轮补强

1. 新增 `PaymentPrepayCreationConcurrencyIntegrationTest`，直接装载正式 `PaymentMapper.xml` 并调用完整 `PaymentServiceImpl.prepay`，不再只验证 Mapper 行锁。
2. 首事务预先持有同订单协调锁，第二个完整服务请求在首事务提交前必须等待；首事务完成真实账单、支付单和预付单写入后，第二请求继续并命中持锁后的查重结果。
3. `insertBill` 和 `insertPrepayOrder` 的到期时间分别等价改用 `TIMESTAMPADD(DAY, 1, NOW())` 与 `TIMESTAMPADD(HOUR, 1, NOW())`，兼容 MySQL 8 与 H2 MySQL 模式。

### 223.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 完整服务并发集成测试 | `mvn -Dtest=PaymentPrepayCreationConcurrencyIntegrationTest test` | 通过 | 双事务覆盖等待、提交后查重与结果复用；两次调用返回相同 `prepayOrderNo/paymentOrderId` |
| 最终数据库事实 | H2 MySQL 模式双连接查询 | 通过 | 协调表、账单、支付单、预付单各 `1` 条 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `568` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | XML、测试和文档无空白符错误 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`，release 明细仍有 `4` 项真实阻断 |
| 真实 MySQL 多实例验证 | 锁等待、死锁恢复、连接池占用与容量 | 未执行 | 不记录生产并发幂等或容量验收通过 |

### 223.3 当前判断

1. 本轮把证据从“Mapper 能形成行锁”提升为“完整服务事务会等待并在提交后复用同一支付事实”，直接验证服务事务边界与正式 SQL 的组合行为。
2. `TIMESTAMPADD` 不改变一天账单到期和一小时预付单到期语义，仅消除测试数据库对 MySQL 专用 `DATE_ADD ... INTERVAL` 语法的不兼容。
3. H2 MySQL 兼容模式不能证明真实 MySQL 的锁等待时间、死锁选择、隔离级别差异或多实例吞吐；这些仍是 release 前的真实环境验证项。

## 224. 2026-09-10 预付单幂等参数冲突保护 V1.0 验证

### 224.1 本轮补强

1. `PaymentServiceImpl.prepay` 命中同订单有效预付单后，核对支付场景、显式客户名称、显式金额和请求生效后的收银台标题，避免异参请求被静默当成幂等成功。
2. 参数冲突统一返回新增错误码 `PAYMENT-1038`，且不读取订单金额、不创建账单、支付单或预付单；同参请求仍复用原结果。
3. 金额复用校验通过既有展示金额解析后按 `BigDecimal.compareTo` 比较，兼容币种符号、千分位和尾随零的等价表示。

### 224.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 服务与并发定向测试 | `mvn -Dtest=PaymentServiceImplTest,PaymentPrepayCreationConcurrencyIntegrationTest test` | 通过 | `61` 个用例；覆盖等价金额正常复用、金额冲突拒绝及等待提交后支付场景冲突 |
| 并发最终事实 | H2 MySQL 模式双连接查询 | 通过 | 冲突请求未生成第二组记录，协调表、账单、支付单和预付单各 `1` 条 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `570` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和文档无空白符错误 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`，release 明细仍有 `4` 项真实阻断 |
| 真实环境验证 | MySQL 多实例、调用方参数版本和跨系统订单事实 | 未执行 | 不记录生产幂等冲突治理验收通过 |

### 224.3 当前判断

1. 幂等复用不仅要求业务键相同，还要求会改变支付语义的请求参数一致；否则返回旧单会让调用方误以为新金额或新场景已经生效。
2. 校验位于订单级锁和有效预付单查询之后，因而并发后到请求只能基于首事务已提交的事实判断一致或冲突，不会在锁外误判。
3. 当前仍以调用方传入的业务字段为比较输入，没有跨系统业务参数版本号或请求摘要；真实接入应结合上游订单版本和调用契约继续治理。

## 225. 2026-09-10 标准订单预付参数口径一致性 V1.0 验证

### 225.1 本轮补强

1. 标准订单读取当前应收、已付和客户事实后，对请求显式携带的金额与客户名称执行一致性校验；金额与当前剩余应付比较，冲突返回 `PAYMENT-1038`。
2. 标准订单显式 `cashierTitle` 不再被忽略，而是与虚拟支付一样写入预付单；未传时统一解析为默认标题，使首次创建与重复复用口径一致。
3. 冲突校验位于账单查询和任何支付事实写入之前，不一致请求不会创建或覆盖账单、支付单、预付单。

### 225.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 服务定向测试 | `mvn -Dtest=PaymentServiceImplTest test` | 通过 | 覆盖等价金额与客户正常创建、自定义标题落库参数、金额冲突和客户冲突 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `572` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和文档无空白符错误 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`，release 明细仍有 `4` 项真实阻断 |
| 上游与生产验证 | 订单版本、支付成功竞态、MySQL 隔离和真实调用方 | 未执行 | 不记录跨系统或生产资金一致性验收通过 |

### 225.3 当前判断

1. 同一字段不能在首次创建时被忽略、在重试时又参与幂等比较；首次创建与复用必须先形成相同的有效请求口径。
2. 标准订单客户与剩余应付属于订单事实，调用方只能省略或传入一致值；标题属于展示意图，可显式覆盖并随预付单持久化。
3. 本轮仍只在 payment-core 本地事务内校验读取快照，没有携带上游订单版本；跨服务订单变化仍需版本号或条件更新补强。

## 226. 2026-09-10 有效预付单订单事实复核 V1.0 验证

### 226.1 本轮补强

1. `prepay` 获取订单协调锁后先读取当前订单应收与已付，再判断是否复用有效预付单；标准订单同时读取当前客户并复核旧预付单事实。
2. 旧预付单金额与当前剩余应付或客户不一致时返回 `PAYMENT-1038`；当前订单已结清或超额时返回 `PAYMENT-1037`，均不返回旧收银台或创建新支付事实。
3. 虚拟支付在查询有效预付单前仍校验客户与金额必填，避免缺少首次请求身份字段的重试仅凭订单号取回旧支付事实。

### 226.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 服务与并发定向测试 | `mvn -Dtest=PaymentServiceImplTest,PaymentPrepayCreationConcurrencyIntegrationTest test` | 通过 | 覆盖正常复用顺序、订单剩余应付漂移拒绝、虚拟支付身份缺失及并发复用/冲突 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `574` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和文档无空白符错误 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`，release 明细仍有 `4` 项真实阻断 |
| 生产并发验证 | 支付成功竞态、上游订单版本、MySQL 隔离与锁等待 | 未执行 | 不记录跨事务或生产资金一致性验收通过 |

### 226.3 当前判断

1. 有效期和支付状态只能证明预付单尚可操作，不能证明其金额与客户仍等于订单当前事实；复用前必须再次核对。
2. 虚拟支付没有独立订单事实来源，客户和金额就是请求身份的一部分，每次重试都必须完整提供才能安全判定幂等。
3. 预付协调锁不被订单更新或支付成功事务共同持有，本轮只能保证当前事务读取后的应用层复核；跨事务强一致仍需共享版本或条件更新。

## 227. 2026-09-10 支付成功与预付创建共享协调 V1.0 验证

### 227.1 本轮补强

1. 渠道回调和主动查单确认成功后，在任何支付成功状态抢占及订单、账单更新前，先获取与预付创建相同的订单协调行锁。
2. 成功收口持锁后重新读取支付详情和终态；关联订单变化、支付单消失或协调行读取失败均以业务异常失败闭合，不继续更新资金事实。
3. 主动查单成功路径在写返回码快照前完成加锁，避免先锁支付单再等待订单协调行；已成功终态按最新事实幂等返回。

### 227.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 服务与并发定向测试 | `mvn -Dtest=PaymentServiceImplTest,PaymentPrepayCreationConcurrencyIntegrationTest test` | 通过 | `67` 个用例；覆盖回调/查单锁顺序、锁失败无资金副作用和并发终态复读 |
| 双事务资金事实测试 | H2 MySQL 模式双连接 | 通过 | 预付请求在成功收口锁提交前等待，提交后读取 `paid_amount=168.00` 并返回 `PAYMENT-1037`，未创建支付单或预付单 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `576` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和文档无空白符错误 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | `canPromoteTestToMaster=true`、`canCreateRelease=false`；release 仍缺候选说明正式化、正式签字、发布后现场核验和稳定观察结束结论 |
| 真实环境验证 | MySQL 多实例、死锁/锁超时、上游订单并发与生产资金 | 未执行 | 不记录真实渠道或生产资金一致性验收通过 |

### 227.3 当前判断

1. payment-core 本地支付成功和预付创建已共享同一订单维度串行边界，后到事务会基于先到事务提交后的支付与订单事实继续处理。
2. 固定先获取订单协调锁、再操作支付单和订单账单，降低两条资金路径反向持锁形成死锁的风险；状态条件更新继续承担回调与查单之间的成功收口幂等。
3. 该结论只覆盖共享 payment-core 数据库及 H2 双连接测试，不覆盖外部订单库或真实 MySQL；跨服务仍需订单版本、计价快照或条件更新。

## 228. 2026-09-10 订单成功收款超额检测 V1.0 验证

### 228.1 本轮补强

1. `PaymentIssueMapper` 按标准订单聚合全部 `SUCCESS` 支付金额，合计超过订单应收时生成一个稳定 P1 异常，并以最新成功支付单提供详情入口；成功资金事实不改写为失败。
2. 异常统一路由至“账务清结算联动组”，沿用现有异常查询、责任组统计、SLA 和告警链路；管理端补充异常范围说明与类型筛选。
3. `PaymentDayEndMapper` 按支付单主键累计同订单成功金额，只把累计后越过应收阈值的支付单计入成功差异；合法 `60 + 40 = 100` 不报错，`80 + 30 > 100` 计 `1` 笔、差异金额 `30.00`。

### 228.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 超额成功 Mapper 集成测试 | `mvn -Dtest=PaymentOverpaidSuccessDetectionMapperIntegrationTest test` | 通过 | `2` 个用例；覆盖合法拆单不误报、稳定异常号、详情入口、责任路由及日终差异 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `578` 个用例，`0` failures、`0` errors、`0` skipped |
| 管理端构建 | `npm run build` | 通过 | Vite 完成 `68` 个模块转换并生成生产构建产物 |
| 补丁检查 | `git diff --check` | 通过 | XML、Java、Vue 和文档无空白符错误 |
| 真实资金处置验证 | 渠道流水、真实 MySQL、退款/调账审批与现场核验 | 未执行 | 不记录生产超额收款处置或资金一致性验收通过 |

### 228.3 当前判断

1. 多笔成功支付不天然等于异常，只有毛成功收款合计超过订单应收才触发；这样既保留渠道成功事实，也允许正常拆单付款。
2. 日终差异金额是“越过阈值的成功支付单金额”，用于阻断和定位，并非数学上的净超额金额；实际超额仍由异常摘要中的成功合计减订单应收表达。
3. 后续退款不删除原超额异常，因为退款属于处置结果而非对历史成功收款的改写；真实渠道流水核对、退款审批、会计处理和生产 MySQL 性能仍需正式环境验收。

## 229. 2026-09-10 统一支付记录成功退款聚合 V1.0 验证

### 229.1 本轮补强

1. `PaymentRecordMapper` 将列表和详情中的固定退款占位值替换为按 `paymentOrderId` 聚合的成功退款次数和金额，只统计 `t_refund_order.status=SUCCESS`。
2. 聚合子查询一次扫描后左连接支付单，双 schema 和增量迁移同步增加 `(payment_order_id, status)` 联合索引；记录到期时间等价改用 `TIMESTAMPADD`，使正式 Mapper 可直接执行集成验证。
3. 管理端退款合计先移除 `¥` 和千分位再转为数值，避免 `Number('¥50.00')` 产生 `NaN`；指标标题改为“本页已退金额”，明确当前统计范围。

### 229.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付记录退款聚合集成测试 | `mvn -Dtest=PaymentRecordRefundAggregationMapperIntegrationTest test` | 通过 | `2` 个用例；列表和详情均得到成功退款 `2` 次、`¥50.00`，排除处理中/失败退款并覆盖无退款零值 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `580` 个用例，`0` failures、`0` errors、`0` skipped |
| 管理端构建 | `npm run build` | 通过 | Vite 完成 `68` 个模块转换并生成生产构建产物 |
| schema 与迁移检查 | 双全量 schema + `2026-09-10-add-refund-payment-status-index.sql` | 通过 | 联合索引名称和字段顺序一致 |
| 补丁检查 | `git diff --check` | 通过 | XML、SQL、Java、Vue 和文档无空白符错误 |
| 真实环境验证 | MySQL 执行计划、渠道退款到账、财务核销与生产数据量 | 未执行 | 不记录真实渠道或生产退款资金验收通过 |

### 229.3 当前判断

1. 退款申请或处理中只表示资金处置尚在途，不能进入“已退”口径；成功状态才是当前系统可用于收款记录展示的退款完成事实。
2. 列表和详情复用同一聚合 SQL，避免两个运营入口对同一支付单展示不同金额；左连接保证没有退款的支付记录不会被过滤。
3. 当前列表卡片是当前分页聚合，不是全量筛选汇总；真实全量退款指标后续应由服务端总览查询承担，不能由浏览器对分页结果外推。

## 230. 2026-09-10 退款申请与失败重试额度串行化 V1.0 验证

### 230.1 本轮补强

1. 退款申请在同一事务内先锁定原支付单，再汇总 `REVIEWING/PROCESSING/SUCCESS` 退款并插入，消除“先查额度、后写申请”的并发超退窗口。
2. 人工重试和任务重试先锁定目标 `FAIL` 退款单，再锁定原支付单并重新计算其他活动退款；只有“活动退款 + 本次重试金额”不超过原实付金额时，才允许进入 `PROCESSING` 并调用渠道。
3. 退款单或原支付金额快照缺少关键字段、金额非法时失败闭合；替代退款已占用额度时，旧失败退款保持原状态，不产生渠道副作用。

### 230.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 服务与双事务定向测试 | `mvn -Dtest=RefundServiceImplTest,RefundApplicationConcurrencyIntegrationTest test` | 通过 | `15` 个用例；覆盖额度不足、异常快照、人工/任务重试及两类锁等待场景 |
| 并发退款申请 | H2 MySQL 模式双连接 | 通过 | 两笔 `60.00` 申请竞争 `100.00` 支付额度，后到事务等待并在重算后拒绝，最终仅一笔活动退款 `60.00` |
| 失败退款并发重试 | H2 MySQL 模式双连接 | 通过 | 替代退款提交后，旧 `60.00` 失败退款重试被拒绝；保留 `FAIL + REVIEWING` 两条历史，活动金额为 `60.00` |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `584` 个用例，`0` failures、`0` errors、`0` skipped |
| 真实环境验证 | MySQL 多实例、真实渠道退款、生产资金与吞吐 | 未执行 | 不记录锁恢复、渠道到账或生产退款验收通过 |

### 230.3 当前判断

1. `FAIL` 不属于已占用退款额度的活动状态，但从 `FAIL` 重试等同于重新申请占用额度，必须在支付单锁内重算，不能直接恢复渠道处理。
2. 退款申请与失败重试均通过原支付单行形成同支付维度串行边界；失败重试额外先锁自身退款单，确保状态与金额快照稳定。
3. 当前证据来自单进程 H2 MySQL 兼容模式，不代表真实 MySQL 的锁等待、死锁选择、超时恢复、多实例吞吐或真实渠道退款已经验收。

## 231. 2026-09-10 退款渠道下发 outbox V1.0 验证

### 231.1 本轮补强

1. 退款审核和失败重试事务只负责将退款改为 `PROCESSING`、记录操作日志并 upsert 稳定 `REFUND_SUBMIT` 事件；渠道调用注册在事务 `afterCommit`，通过 `REQUIRES_NEW` 独立事务执行。
2. 事件号由退款单号稳定派生，重复审核或重试复用同一事件；本地模拟渠道以 `refundOrderId` 返回稳定渠道退款号，派发事务先锁事件行并对 `SUCCESS` 幂等短路。
3. 渠道抛错、返回空受理号或事件与退款快照不一致时记录未知结果，退款保持 `PROCESSING`，事件按既有重试上限进入 `FAILED/DEAD_LETTER`；到期 `PENDING/FAILED` 由自动任务补偿。

### 231.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 干净编译 | `mvn clean test-compile` | 通过 | Maven clean 后重新编译主代码与测试代码，排除陈旧 class 干扰 |
| 退款事件派发定向测试 | `mvn -Dtest=PaymentEventDispatchServiceImplTest test` | 通过 | `15` 个用例；覆盖 `REQUIRES_NEW`、成功事实、未知结果、快照不一致和成功事件幂等 |
| Mapper 与本地渠道测试 | 正式 Mapper H2 MySQL 模式 + 本地渠道单测 | 通过 | 覆盖稳定事件 upsert、到期 `PENDING` 扫描、排除无派发实现事件及稳定渠道退款号 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `591` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | Java、XML、测试和文档无空白符错误 |
| 真实环境验证 | MySQL 多实例、真实渠道幂等、退款到账、生产恢复与现场核验 | 未执行 | 不记录真实退款渠道或生产资金验收通过 |

### 231.3 当前判断

1. outbox 消除了“本地业务事务尚未提交就调用渠道”的窗口，但数据库提交与外部渠道受理仍不是原子操作；恢复安全依赖渠道真实支持以 `refundOrderId` 幂等查询或重放。
2. 未获得确定受理结果时保留 `PROCESSING` 和额度占用，避免未知资金事实被误判为失败后再次释放额度；超过自动重试上限后必须进入人工核查，不能自动宣称退款失败。
3. `FOR UPDATE` 只证明当前单库事务内的串行路径；真实 MySQL 多实例、网络超时后渠道侧状态、生产告警和人工恢复流程仍需独立联调与验收。

## 232. 2026-09-10 退款渠道受理事实持久化 V1.0 验证

### 232.1 本轮补强

1. 退款单新增 `channel_code/channel_refund_no/channel_submitted_at`，保存实际受理渠道、渠道退款号和首次获得受理号时间；同渠道退款号以组合唯一键防止绑定到多笔退款。
2. 渠道返回后仅允许 `PROCESSING` 且尚无渠道退款号的记录首次绑定；事件补偿读取到既有受理事实时，校验其渠道与原支付渠道一致后只收口事件，不再调用渠道。
3. 渠道退款号写库前校验非空、最长 `128` 字符且不含 ISO 控制字符；退款详情接口和管理端详情页同步展示三项渠道受理事实。

### 232.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 服务与 Mapper 定向测试 | `mvn -Dtest=PaymentEventDispatchServiceImplTest,RefundSubmitOutboxMapperIntegrationTest,RefundServiceImplTest test` | 通过 | `31` 个用例；随后新增超长渠道退款号用例并纳入全量回归 |
| 首次绑定 SQL | 正式 `RefundMapper.xml` + H2 MySQL 模式 | 通过 | 首次绑定更新 `1` 行，第二次以不同渠道退款号覆盖更新 `0` 行，原受理号和时间保持不变 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `594` 个用例，`0` failures、`0` errors、`0` skipped |
| 管理端构建 | `npm run build` | 通过 | Vite 完成 `68` 个模块转换并生成生产构建产物；保留既有单包体积提示 |
| schema 与迁移检查 | 双全量 schema + `2026-09-10-add-refund-channel-trace.sql` | 通过 | 三个可空字段与渠道退款号组合唯一键一致 |
| 真实环境验证 | MySQL 迁移、真实渠道回调/查单、到账与财务核验 | 未执行 | 不记录真实退款成功或生产迁移验收通过 |

### 232.3 当前判断

1. 渠道受理号是退款处理中阶段的重要不可覆盖事实，可缩小“渠道已受理但事件未收口”时的重复调用窗口，但它仍不是退款到账凭证。
2. 数据库事务回滚与外部渠道受理之间仍存在不可消除的原子性缺口；在本地尚未保存受理号时，补偿安全继续依赖渠道以 `refundOrderId` 幂等受理或查询。
3. 下一步应以独立退款回调/查单契约核验渠道、退款单号和渠道退款号，并把可信终态及回调日志持久化；不能继续把后台模拟成功/失败动作当作生产闭环。

## 233. 2026-09-10 退款可信回调 V1.0 验证

### 233.1 本轮补强

1. 新增退款专用回调 DTO、Controller 与服务编排，共享支付回调的 HMAC-SHA256/RSA2、时间窗和 nonce 防重底座，但使用独立退款签名原文和 `REFUND` 业务类型。
2. 回调先规范化并校验渠道、退款单号、渠道退款号和有限状态，再验签、锁定退款单、核对渠道关联键，并通过 CAS 收口终态；重复同终态幂等，相反终态返回冲突。
3. 删除 `/api/refunds/success`、`/api/refunds/fail` 及管理端对应按钮，人工界面不再直接制造渠道终态。

### 233.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 定向测试 | `mvn -Dtest=PaymentCallbackSignatureServiceImplTest,PaymentCallbackSecurityMapperIntegrationTest,RefundServiceImplTest,RefundControllerTest,RefundSubmitOutboxMapperIntegrationTest,RefundApplicationConcurrencyIntegrationTest test` | 通过 | `42` 个用例，覆盖支付兼容、退款验签业务类型、字段绑定、终态幂等/冲突及 Mapper CAS |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `599` 个用例，`0` failures、`0` errors、`0` skipped |
| 管理端构建 | `npm run build` | 通过 | Vite 完成 `68` 个模块转换并生成生产构建产物 |
| schema 与迁移 | 双全量 schema + `2026-09-10-generalize-callback-nonce.sql` | 通过 | 存量 nonce 迁移为 `PAYMENT`，新增业务索引且保留跨业务渠道 nonce 唯一约束 |
| 真实环境验证 | 真实渠道签名报文、MySQL 迁移、主动查单、到账与财务核验 | 未执行 | 不记录真实退款成功、生产迁移或现场资金验收通过 |

### 233.3 当前判断

1. 渠道受理号与可信终态现在通过渠道、退款单号和渠道退款号三方绑定，不能由后台人工按钮替代。
2. 相同终态允许使用新 nonce 重复通知并幂等返回；同一 nonce 的重放仍在验签层拒绝，相反终态不能覆盖既有终态。
3. 当前密码学与状态机证据来自本地配置和测试，不代表微信、支付宝或银行真实退款通知格式已经联调；主动查单与渠道对账仍是下一优先缺口。

## 234. 2026-09-10 退款人工主动查单 V1.0 验证

### 234.1 本轮补强

1. 新增退款查单 DTO、服务、适配器和 `POST /api/refunds/query`；仅对已绑定渠道受理号的 `PROCESSING` 退款调用渠道，既有 `SUCCESS/FAIL` 直接幂等返回。
2. 外部查询前只读取非锁定快照，查询完成后再锁定退款行并复核渠道、渠道退款号和最新状态；明确终态通过 `PROCESSING` 条件更新收口，回调已先收口时不回退。
3. `gateway-access` HTTP 适配器默认关闭；本地兜底只回显现有状态。查询结果统一限制为 `PROCESSING/SUCCESS/FAIL`，并校验必填、长度和 ISO 控制字符。
4. 管理端退款列表和详情仅在 `PROCESSING` 状态开放“主动查单”，执行后刷新服务端最新事实，不提供人工选择成功或失败。

### 234.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 定向测试 | `mvn -Dtest=RefundServiceImplTest,RefundControllerTest,RefundChannelQueryServiceImplTest,GatewayAccessRefundChannelQueryAdapterTest,RefundApplicationConcurrencyIntegrationTest test` | 通过 | `34` 个用例，覆盖适配器选择、结果校验、处理中留痕、成功/失败收口、终态短路及回调并发优先 |
| Mapper 集成 | 正式 `RefundMapper.xml` + H2 MySQL 模式 | 通过 | 非锁定快照与加锁收口源均读回渠道、渠道退款号和状态；CAS 仍只允许 `PROCESSING` 单向收口 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `613` 个用例，`0` failures、`0` errors、`0` skipped |
| 管理端构建 | `npm run build` | 通过 | Vite 完成 `68` 个模块转换并生成生产构建产物 |
| 补丁检查 | `git diff --check` | 通过 | Java、XML、Vue、测试和文档无空白符错误 |
| 真实环境验证 | 真实 gateway-access 退款查询、MySQL、渠道流水、到账和财务核验 | 未执行 | 不记录真实退款查单、生产迁移、到账或现场资金验收通过 |

### 234.3 当前判断

1. 人工主动查单补齐了回调丢失时的本地状态收口入口，但只有真实网关返回且关联键一致的明确终态才具备生产意义。
2. 本地兜底的 `PROCESSING` 只证明当前数据库仍在处理中，不是渠道探活或外部查询成功；不得据此释放额度或宣称退款失败。
3. 自动退款查单补偿在第 235 节接入任务中心；真实网关、共享固定窗口限流和真实环境验证仍需独立交付，不得由本地兜底推断。

## 235. 2026-09-10 退款自动主动查单补偿 V1.0 验证

### 235.1 本轮补强

1. 支付任务中心新增 `REFUND_PENDING_QUERY`、人工执行接口和退款查单积压指标；批次逐笔调用既有退款查单服务，单笔异常不会中断其他候选。
2. 人工候选遵守首次等待、总批次和单渠道批次；自动候选额外根据 `CHANNEL_QUERY/CHANNEL_QUERY_FAILED` 次数与最近时间执行指数退避，首次等待统一以渠道受理时间为准。
3. 自动任务复用共享数据库租约；调度必须同时开启退款补偿与真实退款网关开关，默认关闭，本地兜底不会被自动调度使用。
4. 管理端任务中心展示退款达到补查阈值数、当前到期数、严重度规则与人工执行入口，明确自动执行仍受服务端开关控制。

### 235.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 定向测试 | 任务服务、任务中心、调度、Controller 与 Mapper 定向测试 | 通过 | `77` 个用例，覆盖逐笔失败隔离、人工/自动候选、指数退避、单渠道与总批次、首次等待、终态排除、共享租约、任务日志和双开关调度 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `619` 个用例，`0` failures、`0` errors、`0` skipped |
| 管理端构建 | `npm run build` | 通过 | Vite 完成 `68` 个模块转换并生成生产构建产物；保留既有单包超过 500 kB 提示 |
| 补丁检查 | `git diff --check` | 通过 | Java、XML、YAML、Vue、测试和文档无空白符错误 |
| 真实环境验证 | 真实 gateway-access 退款查询、共享固定窗口限流、MySQL 多实例、渠道流水、到账和财务核验 | 未执行 | 不记录真实退款查单、生产运行、到账或现场资金验收通过 |

### 235.3 当前判断

1. 退款主动查单从单笔人工入口扩展为可审计的批次与自动补偿代码闭环，但自动调度默认关闭，只有真实网关开关同时开启才允许运行。
2. 当前退避、总批次和单渠道批次用于控制单轮压力；退款维度的跨实例渠道配额在第 236 节补齐，真实渠道容量仍需校准。
3. `gateway-access` 尚无真实退款查询接口，本地测试与兜底回显不能证明真实渠道可用、退款到账、生产稳定或财务验收完成。

## 236. 2026-09-10 退款主动查单共享固定窗口限流 V1.0 验证

### 236.1 本轮补强

1. 新增 `t_refund_channel_query_rate_bucket`，以渠道唯一键和带阈值条件的原子 `UPDATE` 约束所有实例在同一固定窗口内的退款查单占额。
2. 新增 `t_refund_channel_query_rate_limit_log`；配额拒绝在独立事务保存退款单号、渠道与配置快照后返回 `PAYMENT-1047`，拒绝事实不进入退款查单操作日志。
3. 限流配置要求自身开关和真实退款网关开关同时开启；默认关闭，支付与退款分别使用独立桶，避免在没有真实渠道合同依据时共用容量。
4. 任务中心把支付与退款查单拒绝合并进窗口统计、P1 阈值和监控 outbox，管理端标签同步为“支付/退款限流拒绝”。

### 236.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 定向测试 | 限流服务、正式 Mapper、退款服务与任务中心测试 | 通过 | `66` 个用例，覆盖双开关、渠道规范化、参数下限、拒绝审计、外部调用前拦截和合并监控 |
| 并发与过窗重置 | H2 MySQL 模式双连接 + 数据库时钟 | 通过 | 同窗额度为 `1` 时两条并发事务仅一条占额成功；窗口过期后计数从 `1` 重新开始 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `626` 个用例，`0` failures、`0` errors、`0` skipped |
| 管理端构建 | `npm run build` | 通过 | Vite 完成 `68` 个模块转换；保留既有 `500.93 kB` 单包提示 |
| XML 与补丁检查 | `xmllint --noout ... && git diff --check` | 通过 | 新增 Mapper 与任务中心 Mapper XML 结构合法，无空白符错误 |
| 真实环境验证 | 真实 MySQL 多实例、真实 gateway-access、渠道配额和生产容量 | 未执行 | 不记录真实渠道联通、退款到账、生产稳定或财务验收通过 |

### 236.3 当前判断

1. 退款直接查单、人工批次和自动补偿现在共享数据库渠道配额，跨实例先查后判穿透缺口已在代码与本地数据库兼容测试层闭合。
2. 限流拒绝与渠道查询失败严格分流，因此不会因容量保护虚增渠道失败率，也不会延长单笔退款自动退避历史。
3. 默认值只是开发基线；真实窗口、渠道配额、支付与退款是否应共享上游总额度，必须以实际渠道合同、网关压测和生产监控重新校准。

## 237. 2026-09-10 支付与退款主动查单统一监控 V1.0 验证

### 237.1 本轮补强

1. 任务中心查单总量、失败总量和小时趋势统一合并支付通知日志与退款操作日志，不新增平行监控模块。
2. 退款 `CHANNEL_QUERY/CHANNEL_QUERY_SUCCESS/CHANNEL_QUERY_FAIL/CHANNEL_QUERY_FAILED` 均作为查询事实；仅 `CHANNEL_QUERY_FAILED` 表示查询调用失败，可信退款失败终态 `CHANNEL_QUERY_FAIL` 不进入失败率。
3. 支付与退款限流拒绝继续使用独立日志合并统计，不进入查询失败率；总览、重点告警、阈值 outbox 和前端标签同步使用支付/退款统一口径。

### 237.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 定向测试 | `mvn -Dtest=PaymentTaskCenterMapperIntegrationTest,PaymentTaskCenterServiceImplTest test` | 通过 | `38` 个用例；窗口内支付查询 `15` 次、退款查询事实 `19` 次，统一总量 `34`，查询调用失败合计 `2` |
| 口径排除 | Mapper 集成测试夹具 | 通过 | `RETRY`、25 小时前退款查询和限流拒绝不进入查询总量/失败率；`CHANNEL_QUERY_FAIL` 只进入总量 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `626` 个用例，`0` failures、`0` errors、`0` skipped |
| 管理端构建 | `npm run build` | 通过 | Vite 完成 `68` 个模块转换；保留既有单包超过 500 kB 提示 |
| XML、旧符号与补丁检查 | `xmllint --noout ...`、`rg`、`git diff --check` | 通过 | Mapper XML 合法，旧支付专用方法名无残留，补丁无空白符错误 |
| 真实环境验证 | 真实 gateway-access、生产数据、渠道到账和现场稳定观察 | 未执行 | 不记录真实渠道、生产运行、退款到账或现场验收通过 |

### 237.3 当前判断

1. 任务中心现可用同一时间窗观察支付与退款主动查单压力和查询调用失败，但不会把可信退款失败终态或本地限流保护误报为渠道查询失败。
2. 支付与退款暂时共用监控窗口及告警阈值，适合形成统一运营视图，但真实流量差异可能需要后续按业务类型拆分阈值。
3. 本轮只补齐本地统计、趋势、告警和页面口径，不证明真实退款网关已接入、渠道可用性达标、退款到账或生产稳定性成立。

## 238. 2026-09-10 退款查单失败历史完整性 V1.0 验证

### 238.1 本轮补强

1. 退款查单把适配器调用、结果规范化和渠道退款号一致性校验统一放入失败记录边界，避免不可信结果绕过 `CHANNEL_QUERY_FAILED`。
2. 未预期 `RuntimeException` 转换为 `PAYMENT-1046`，对外消息固定且操作日志只保存“未获得可信结果”，不写远端异常明细；内部 cause 已在第 302 节进一步移除。
3. 限流拒绝仍在渠道查询边界前返回 `PAYMENT-1047`，不生成失败历史；可信退款失败终态仍写 `CHANNEL_QUERY_FAIL`，不与查询调用失败混淆。

### 238.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 定向测试 | `mvn -Dtest=RefundServiceImplTest,RefundQueryTaskServiceImplTest,PaymentTaskCenterMapperIntegrationTest test` | 通过 | `34` 个用例，覆盖退款号不一致、业务异常、运行时异常、限流排除、候选退避和监控统计 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `627` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和文档无空白符错误 |
| 真实环境验证 | 真实退款网关异常、生产事务、渠道流水与到账 | 未执行 | 不记录真实渠道异常收口、生产运行、退款到账或现场验收通过 |

### 238.3 当前判断

1. 自动退款查单现在可基于完整的本地失败事实执行指数退避，不会因适配器抛出普通运行时异常或返回错单结果而在下一轮过早重试。
2. 失败日志刻意不保存远端异常原文；第 302 节已进一步移除内部 cause，API 错误码和运营日志最小化原则不变。
3. `@Transactional(noRollbackFor = BusinessException.class)` 是失败日志随业务异常提交的现有边界；真实数据库故障、事务代理配置和生产网关异常分布仍需环境级验证。

## 239. 2026-09-10 支付查单可信结果与失败历史 V1.0 验证

### 239.1 本轮补强

1. 支付查单适配器抛出普通运行时异常时，服务统一返回 `PAYMENT-1026` 并写最小化失败通知，避免自动退避和统一监控漏记；内部 cause 已在第 302 节进一步移除。
2. 未成功支付单收到缺少渠道流水号的 `SUCCESS` 时，在返回码快照、成功查询日志和结算更新之前失败闭合，不能把无法关联渠道资金事实的响应作为成功。
3. 缺流水号查询仍保留订单协调锁后的最新状态复核；若回调已先收口并写入可信流水号，则复用最新成功支付事实且不重复结算。

### 239.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 定向测试 | `mvn -Dtest=PaymentServiceImplTest,PaymentChannelQueryServiceImplTest,PaymentTaskCenterMapperIntegrationTest test` | 通过 | `81` 个用例，覆盖运行时异常、非法结果、缺流水号成功、回调抢先收口、失败日志、退避与监控统计 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `630` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和文档无空白符错误 |
| 真实环境验证 | 真实支付网关、渠道流水、MySQL 锁竞争和资金结算 | 未执行 | 不记录真实渠道查询、生产并发、资金入账或现场验收通过 |

### 239.3 当前判断

1. 支付与退款查单现在都能把未预期适配器异常收口为各自业务错误并形成失败历史，统一监控与自动退避不再依赖所有适配器自行包装异常。
2. 支付成功查询的可信边界不仅是状态值，还必须能关联渠道交易流水；回调已经形成的最新可信事实优先于查询响应缺失字段，避免并发下重复收口。
3. 本轮证据来自本地单元和全量测试，不证明真实渠道字段质量、数据库隔离、跨实例竞争或资金一致性已达到生产要求。

## 240. 2026-09-10 前端模拟支付回调入口移除 V1.0 验证

### 240.1 本轮补强

1. App/H5/PC 共享结果页移除模拟成功回调按钮、加载状态、模拟交易号生成和 `paymentApi.callback` 封装；处理中提示改为等待真实渠道回调或主动查单。
2. 管理端支付列表和详情移除模拟回调按钮、状态判断、成功报文构造与 API 封装；主动查单、受限关单、详情刷新和支付/回调/事件轨迹查看保持可用。
3. 后端真实签名回调 Controller、Service 和 `POST /api/payments/callback/{channel}` 未修改，渠道仍可通过正式通知收口支付结果。

### 240.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| App 常量测试 | `npm test`（app-web） | 通过 | `8` 个用例，新增处理中提示必须引导主动查单且不得包含模拟回调的契约 |
| App 构建 | `npm run build`（app-web） | 通过 | Vite 完成 `33` 个模块转换 |
| H5 构建 | `npm run build`（h5-web） | 通过 | Vite 完成 `36` 个模块转换并消费共享结果页 |
| PC 构建 | `npm run build`（pc-web） | 通过 | Vite 完成 `37` 个模块转换并消费共享结果页 |
| 管理端构建 | `npm run build`（admin-web） | 通过 | Vite 完成 `68` 个模块转换 |
| 补丁检查 | `git diff --check` | 通过 | Vue、JavaScript、测试和文档无空白符错误 |
| 真实环境验证 | 真实渠道签名通知、生产部署、资金入账和现场核验 | 未执行 | 不记录真实渠道回调、生产环境、资金或现场验收通过 |

### 240.3 当前判断

1. 用户和运营页面已不能通过公开按钮构造成功通知，支付成功必须来自后端接受的可信渠道回调或主动查单事实。
2. 保留真实回调接口是渠道异步通知所必需的，不应因删除 UI 模拟入口而删除 Controller 或弱化验签；本地 demo profile 的关闭验签能力也不得用于测试、预发或生产。
3. 四端构建只证明前端依赖和模板编译通过，未执行真实渠道报文、浏览器人工回归、生产配置或资金一致性验收。

## 241. 2026-09-10 本地模拟支付适配器环境隔离 V1.0 验证

### 241.1 本轮补强

1. 4 个本地支付提交适配器和 4 个本地支付查单适配器增加 `demo` profile 限制，默认环境不再生成模拟渠道流水或以数据库当前状态冒充渠道查询结果。
2. `PaymentChannelSubmitService` 增加适配器可用性预检；支付主服务在任何渠道提交事实落库前执行预检，无适配器时返回 `PAYMENT-1012` 并保持原预付状态。
3. 真实 `GatewayAccessPaymentChannelQueryAdapter` 保持独立配置开关；非 demo 未启用或未匹配真实适配器时，主动查单继续以 `PAYMENT-1004` 失败闭合。

### 241.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 环境装配契约 | `PaymentLocalAdapterProfileConfigurationTest` | 通过 | 默认 profile 的本地提交/查单 Bean 均为 `0`，demo profile 分别注册 `4` 个 |
| 渠道服务与支付主链路定向测试 | `mvn -Dtest=PaymentLocalAdapterProfileConfigurationTest,PaymentChannelSubmitServiceImplTest,PaymentChannelQueryServiceImplTest,PaymentServiceImplTest test` | 通过 | `81` 个用例，覆盖无适配器错误码、demo 装配和预检失败无提交事实副作用 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `634` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | Java、YAML、测试和文档无空白符错误 |
| 真实环境验证 | 真实渠道下单/查单、gateway-access、凭证、生产网络和资金事实 | 未执行 | 不记录真实渠道受理、查询、生产部署或资金验收通过 |

### 241.3 当前判断

1. 基础环境现在宁可明确暴露“真实适配器缺失”，也不会生成看似可信的模拟渠道单号或查询来源；这是失败闭合，不是支付渠道已经可用。
2. demo 仍可支持仓库本地演示和自动化适配器单测，但启用 demo 会同时引入样例数据初始化与未签名回调配置，必须与测试、预发和生产部署隔离。
3. 下一步仍需由 gateway-access 或渠道专属实现提供真实提交和查询适配器，并补商户应用绑定、证书/密钥托管、网络来源控制、超时语义、真实报文和资金一致性验证。

## 242. 2026-09-10 gateway-access 支付查单代理边界 V1.0 验证

### 242.1 本轮补强

1. `gateway-access` 新增 `POST /api/gateway/payment-orders/query`，使用独立交易 Controller 与稳定请求/响应 DTO，不把原有治理台接口或 H2 样例台账直接作为渠道查询结果。
2. 调用前校验共享访问凭证、接入应用、权限归属、网关启用状态、有效证书和当前环境发布路由；渠道上游 URL 必须由部署环境显式配置，访问密钥和三个渠道 URL 默认均为空。
3. 只有上游统一响应码为 `0` 且 `data.tradeStatus` 明确时才返回渠道事实；空响应、业务失败、超时和运行时异常均返回稳定错误，不向调用方暴露上游原始异常。
4. 每次代理请求尽力写入最小 `PAYMENT_QUERY` 审计，不保存访问密钥或上游异常原文；`payment-core` 默认地址同步从错误的 reconciliation-system `18150` 修正为 gateway-access `8090`，并发送应用与访问凭证请求头。

### 242.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| gateway-access 定向测试 | `mvn -Dtest=GatewayTransactionControllerTest,PaymentOrderQueryServiceImplTest,GatewayAccessControllerTest,GatewayAccessServiceImplTest test` | 通过 | `30` 个用例，覆盖 Controller 委派、默认失败闭合、发布路由拒绝、明确上游状态与审计 |
| gateway-access 全量测试 | `mvn test` | 通过 | `30` 个用例，`0` failures、`0` errors、`0` skipped |
| payment-core 定向测试 | `mvn -Dtest=GatewayAccessPaymentChannelQueryAdapterTest test` | 通过 | `4` 个用例，覆盖配置启停、明确状态、空状态和网关业务失败 |
| payment-core 全量测试 | `mvn test` | 通过 | `635` 个用例，`0` failures、`0` errors、`0` skipped |
| 运行态冒烟 | 启动 gateway-access 后调用治理摘要与支付查单接口 | 通过 | Spring、H2、MyBatis XML 和配置绑定启动成功；默认未配密钥返回 `GATEWAY_ACCESS_NOT_CONFIGURED` |
| 补丁检查 | `git diff --check` | 通过 | Java、XML、YAML、测试和文档无空白符错误 |
| 真实环境验证 | 真实渠道协议、密钥托管、网络、渠道流水、资金与财务核验 | 未执行 | 不记录真实微信/支付宝/银行查单、生产部署、到账或现场验收通过 |

### 242.3 当前判断

1. 支付核心与 gateway-access 之间原有“配置指向不存在接口”的断点已闭合为可运行、可失败闭合的 HTTP 代理契约；默认配置不会访问样例 `gateway.local` 地址或制造渠道事实。
2. 当前上游 URL 面向真实渠道集成服务的稳定契约，尚未实现微信、支付宝和银行各自的签名、验签、报文映射及密钥托管，不能称为真实渠道已接入。
3. 来源 IP 白名单执行、发布窗口/灰度流量算法、生产密钥轮换、真实网络故障分布、资金到账与对账仍需后续独立交付和环境验收。

## 243. 2026-09-10 gateway-access 支付提交代理边界 V1.0 验证

### 243.1 本轮补强

1. `gateway-access` 新增 `POST /api/gateway/payment-orders/submit`，与支付查单复用调用凭证、接入应用、权限归属、网关状态、有效证书和发布路由门禁；三个渠道提交 URL 默认留空并失败闭合。
2. `payment-core` 新增默认关闭的 gateway-access 提交适配器。开关、URL、渠道列表、应用编码或访问密钥不完整时不声明支持，保持 `PAYMENT-1012`；调用失败或响应不确定时返回 `PAYMENT-1048`，既有主链路按“结果未知，待查单”收口，禁止自动重提。
3. 提交响应必须同时具备渠道交易号、尝试状态、状态类型和渠道返回码。gateway-access 审计与 payment-core 响应留痕均保持最小化，不保存访问密钥、上游原始报文或异常原文。
4. 运行态发现默认 H2 不支持 Mapper 中的 MySQL `DATE_FORMAT`，已改为读取原始日期列并新增默认 Spring/H2/MyBatis 集成测试，覆盖治理读取和交易审计写入。

### 243.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| gateway-access 定向测试 | `mvn -Dtest=GatewayAccessMapperIntegrationTest,PaymentOrderSubmitServiceImplTest,PaymentOrderQueryServiceImplTest test` | 通过 | `9` 个用例，覆盖默认 H2 Mapper、共享门禁、提交成功和失败闭合 |
| gateway-access 全量测试 | `mvn test` | 通过 | `36` 个用例，`0` failures、`0` errors、`0` skipped |
| payment-core 定向测试 | `GatewayAccessPaymentChannelSubmitAdapterTest` 与相关提交链路测试 | 通过 | 覆盖配置完整性、最小成功留痕和网关业务失败 |
| payment-core 全量测试 | `mvn test` | 通过 | `638` 个用例，`0` failures、`0` errors、`0` skipped |
| 本地受控上游冒烟 | gateway-access `8090` 转发到固定响应 stub `19090` | 通过 | 正确凭证返回 `STUB-WX-1001 / USERPAYING`；错误凭证返回 `GATEWAY_ACCESS_DENIED` |
| 补丁检查 | `git diff --check` | 通过 | Java、XML、YAML、测试和文档无空白符错误 |
| 真实环境验证 | 微信/支付宝/银行协议、生产凭证、网络、资金和对账 | 未执行 | 不记录真实渠道受理、生产部署、到账、对账或财务验收通过 |

### 243.3 当前判断

1. 支付核心到 gateway-access 的支付提交调用边界已可运行，并与主动查单共享失败闭合的治理门禁；本地受控 stub 只验证 HTTP 转发和响应校验，不是任何真实渠道联调。
2. 当前尚未实现微信、支付宝和银行各自的请求映射、签名验签、商户/应用绑定、生产密钥托管与轮换、来源 IP 白名单执行和真实网络故障语义。
3. 发布路由目前只做启用及正流量门禁，不执行实际百分比灰度算法；渠道资金到账、回调、主动查单、对账、结算和财务核验仍需真实环境独立验证。

## 244. 2026-09-10 gateway-access 退款查单代理边界 V1.0 验证

### 244.1 本轮补强

1. `gateway-access` 新增 `POST /api/gateway/refunds/query`，复用支付交易代理的调用凭证、应用、权限、网关、有效证书和发布路由门禁；三个渠道退款查单 URL 默认留空并失败闭合。
2. 代理请求和结果分别稳定为 `refundOrderId / channelCode / channelRefundNo` 与 `refundStatus / channelRefundNo / querySource`。状态仅接受 `PROCESSING / SUCCESS / FAIL`，响应渠道退款号必须与请求一致，防止串退款单收口。
3. `payment-core` 退款查单适配器补齐应用编码和访问凭证请求头，凭证为空时不声明支持；默认地址由错误的 reconciliation-system `18150` 修正为 gateway-access `8090`，网关业务码非 `0` 时按 `PAYMENT-1046` 失败闭合。
4. 两端只记录最小审计和既有退款操作事实，不保存访问密钥、上游原始报文或异常原文。

### 244.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| gateway-access 定向测试 | `mvn -Dtest=GatewayRefundControllerTest,RefundOrderQueryServiceImplTest,GatewayAccessMapperIntegrationTest test` | 通过 | `6` 个用例；后续边界复跑 `5` 个用例，覆盖控制字符、关联退款号、门禁和 Controller 委派 |
| payment-core 定向测试 | `mvn -Dtest=GatewayAccessRefundChannelQueryAdapterTest,RefundChannelQueryServiceImplTest,RefundServiceImplTest test` | 通过 | `30` 个用例，覆盖凭证配置、业务码、结果规范化和退款状态收口 |
| gateway-access 全量测试 | `mvn test` | 通过 | `41` 个用例，`0` failures、`0` errors、`0` skipped |
| payment-core 全量测试 | `mvn test` | 通过 | `638` 个用例，`0` failures、`0` errors、`0` skipped |
| 本地受控上游冒烟 | gateway-access `8090` 转发到固定响应 stub `19091` | 通过 | 正确凭证返回匹配的 `PROCESSING` 退款事实；错误凭证返回 `GATEWAY_ACCESS_DENIED` |
| 补丁检查 | `git diff --check` | 通过 | Java、YAML、测试和文档无空白符错误 |
| 真实环境验证 | 微信/支付宝/银行退款协议、生产凭证、网络、到账和对账 | 未执行 | 不记录真实退款查单、生产部署、资金到账、对账或财务验收通过 |

### 244.3 当前判断

1. payment-core 原有退款主动查单、自动补偿、退避、限流和状态收口现在有了真实存在且失败闭合的 gateway-access HTTP 出口，不再依赖错误的服务地址。
2. 本地受控 stub 只验证代理链路和稳定契约，不是微信、支付宝或银行真实退款查单；真实请求映射、签名验签、商户应用绑定、密钥轮换和网络来源控制仍未完成。
3. 退款 `SUCCESS` 仍只表示可信上游状态返回后的本地收口；真实资金到账、支付机构账单、对账差异、会计分录和财务审批必须另行验证。

## 245. 2026-09-10 gateway-access 退款提交代理边界 V1.0 验证

### 245.1 本轮补强

1. `gateway-access` 新增 `POST /api/gateway/refunds/submit`，复用应用、访问凭证、权限、网关、有效证书和发布路由门禁；微信、支付宝和银行退款提交 URL 默认均为空并失败闭合。
2. `payment-core` 新增非 demo 的 gateway-access 退款提交实现，开关默认关闭、访问凭证默认留空；原本无条件注册的本地退款模拟实现改为只在 `demo` profile 注册。
3. 两端只接受包含渠道退款号和有限受理状态的明确响应；网关拒绝、超时或响应非法返回 `PAYMENT-1049`，由既有 outbox 保持退款处理中并进入未知结果核查，不能据此认定渠道未受理或直接重复退款。
4. gateway-access 审计与 payment-core 响应留痕均保持最小化，不保存访问密钥、完整请求、上游原始响应或异常原文。

### 245.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| gateway-access 定向测试 | `mvn -Dtest=GatewayRefundControllerTest,RefundOrderSubmitServiceImplTest test` | 通过 | 覆盖 Controller 委派、默认失败闭合、金额校验、明确受理和非法响应 |
| payment-core 定向测试 | `mvn -Dtest=GatewayAccessRefundChannelSubmitServiceImplTest,LocalRefundChannelSubmitServiceImplTest,PaymentEventDispatchServiceImplTest test` | 通过 | 覆盖配置完整性、最小留痕、业务失败和既有未知结果 outbox 语义 |
| gateway-access 全量测试 | `mvn test` | 通过 | `46` 个用例，`0` failures、`0` errors、`0` skipped |
| payment-core 全量测试 | `mvn test` | 通过 | `641` 个用例，`0` failures、`0` errors、`0` skipped |
| 本地固定响应冒烟 | gateway-access `18090` 转发到 stub `19091` | 通过 | 正确凭证返回 `WX-REF-SMOKE-001 / PROCESSING`；错误凭证返回 `GATEWAY_ACCESS_DENIED`；stub 校验退款单、支付单、订单和渠道字段 |
| 补丁检查 | `git diff --check` | 通过 | Java、YAML、测试和文档无空白符错误 |
| 真实环境验证 | 微信/支付宝/银行退款协议、生产凭证、网络、幂等、到账和对账 | 未执行 | 不记录真实退款受理、生产部署、资金到账、对账或财务验收通过 |

### 245.3 当前判断

1. 默认环境不再把本地模拟退款号当作渠道受理事实；真实代理未启用或配置不完整时会暴露可定位错误并保留退款处理中状态。
2. 本地固定响应仅证明 payment-core 到 gateway-access 再到稳定上游契约的转发与拒绝边界，不是任何支付机构真实退款联调。
3. 下一步仍需实现具体渠道请求映射、签名验签、商户应用绑定、生产密钥托管与轮换，并以真实渠道幂等、主动查单、账单和财务核验闭合资金风险。

## 246. 2026-09-10 退款提交未知结果停止重试 V1.0 验证

### 246.1 本轮补强

1. 将退款提交失败拆分为确定的前置拒绝和可能已到达上游的未知结果。前者保留 `FAILED` 退避，后者写入独立 `UNKNOWN/warn` 状态并清空下次重试时间。
2. 自动到期扫描和人工批量失败扫描均不选择 `UNKNOWN`；管理端新增未知数量、状态筛选和“待核查”禁用动作，提示按退款单号查单，避免操作员直接重复退款。
3. 单笔重发在没有可信受理号时返回新增 `PAYMENT-1050`，服务层保持该业务错误码；若核查后已经绑定合法渠道退款号，则只将事件收敛为成功，不再调用渠道。
4. 未知结果操作日志只记录固定风险与处置说明，不持久化异常原文、访问凭证或完整渠道报文；退款仍保持 `PROCESSING`，不把事件状态冒充资金终态。

### 246.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 定向测试 | `mvn -Dtest=GatewayAccessRefundChannelSubmitServiceImplTest,PaymentEventDispatchServiceImplTest,PaymentEventServiceImplTest,RefundSubmitOutboxMapperIntegrationTest,PaymentEventRetryTaskServiceImplTest test` | 通过 | `37` 个用例，覆盖前置拒绝、未知结果、候选排除、错误码传播和受理事实安全收敛 |
| payment-core 全量测试 | `mvn test` | 通过 | Surefire 汇总 `647` 个用例，`0` failures、`0` errors、`0` skipped |
| 管理端生产构建 | `npm run build` | 通过 | Vite 转换 `68` 个模块，未知指标、筛选、按钮状态与排障提示编译通过 |
| 补丁检查 | `git diff --check` | 通过 | Java、XML、Vue、测试和文档无空白符错误 |
| 真实环境验证 | 真实渠道查询、生产凭证、网络、幂等、退款到账、对账与财务核验 | 未执行 | 不记录真实退款受理、生产部署、到账、对账、审批或现场验收通过 |

### 246.3 当前判断

1. 未获得确定受理结果的退款提交不再因 outbox 自动补偿或管理端批量操作被直接重发，当前最直接的重复退款风险已在候选、服务和页面三层收口。
2. `UNKNOWN` 不是退款终态；可信查单或人工核查仍必须形成渠道受理事实，最终成功或失败继续由退款回调/查单状态机处理。
3. 本地自动化与构建不能证明渠道按退款单号查询能力、生产幂等实现、人工审批流程、资金到账、对账或财务验收，相关事项继续作为正式放行阻断。

## 247. 2026-09-10 退款提交未知结果异常告警 V1.0 验证

### 247.1 本轮补强

1. 将 `REFUND_SUBMIT/UNKNOWN` 映射为独立“退款提交结果待核查”P1 异常，使用稳定异常编号，展示退款单、事件号、禁止重发说明和未知事件筛选入口。
2. 任务中心新增未知退款提交数量和专属 P1 重点告警；超过 30 分钟后进入既有 SLA 统计与告警候选，不与允许重发的普通下游事件失败混合。
3. 异常责任归属账务清结算联动组；两份初始化数据和幂等迁移补齐全时段 P1 值班路由，候选 SQL 在配置缺失时也保持账清默认回退。
4. 管理端任务中心新增数量卡片和风险说明，异常中心新增类型筛选；前端不推导处理结果，也不开放未知退款重发入口。

### 247.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 定向测试 | `mvn -Dtest=PaymentOverpaidSuccessDetectionMapperIntegrationTest,PaymentTaskCenterMapperIntegrationTest,PaymentTaskCenterServiceImplTest test` | 通过 | `43` 个用例；后续候选 SQL 单测复跑 `9` 个用例通过，覆盖 P1、SLA、责任汇总和值班接收人 |
| payment-core 全量测试 | `mvn test` | 通过 | Surefire XML 汇总 `650` 个用例，`0` failures、`0` errors、`0` skipped |
| 管理端生产构建 | `npm run build` | 通过 | Vite 转换 `68` 个模块；存在既有单包超过 500 kB 的警告，不影响构建成功 |
| SQL 与补丁检查 | XML 解析、迁移/数据 SQL 复核、`git diff --check` | 通过 | 新建与存量数据库分别由一致的初始化数据和按 `roster_code` 防覆盖的幂等迁移承接 |
| 真实环境验证 | 真实告警供应商、排班响应、渠道核查、退款到账和财务处置 | 未执行 | 不记录真实通知送达、人工核查、生产部署、到账或财务验收通过 |

### 247.3 当前判断

1. 未知退款提交现在既不会被直接重发，也不会只停留在事件页等待偶然发现；异常中心、任务中心和 SLA 告警形成了本地运营发现闭环。
2. P1 与账清责任路由只表示系统建议的处置优先级和默认接收组，不证明真实人员已收到、响应或完成核查。
3. 真实渠道按退款单号查询、通知网关送达、可信身份权限、到账、对账和财务核验仍是外部交付边界。

## 248. 2026-09-10 退款提交未知受理事实恢复 V1.0 验证

### 248.1 本轮补强

1. 修正未知退款的主动查单前置条件：`PROCESSING + 已绑定渠道 + REFUND_SUBMIT/UNKNOWN` 可在缺少渠道退款号时按平台退款单号查询，普通待下发退款仍在外部调用前失败闭合。
2. gateway-access 将渠道退款号调整为条件必填；已有受理事实时严格关联，未知场景允许省略请求字段但要求可信响应补回非空渠道退款号。
3. 主动查单或已验签回调返回可信受理事实后，服务锁定退款行并条件绑定渠道退款号、收敛未知事件、写最小恢复日志，再沿用既有状态机处理结果。
4. 该阶段尚未把未知退款自动加入批量查单候选，也不假设真实渠道一定支持平台退款单号查询；后续由第 249 节补齐默认关闭的批量补查边界。

### 248.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| payment-core 定向测试 | `mvn -Dtest=RefundServiceImplTest,RefundSubmitOutboxMapperIntegrationTest,GatewayAccessRefundChannelQueryAdapterTest test` | 通过 | `37` 个用例，`0` failures、`0` errors、`0` skipped；覆盖恢复后状态更新失败必须触发事务回滚的边界 |
| gateway-access 定向测试 | `mvn -Dtest=RefundOrderQueryServiceImplTest test` | 通过 | 最终类内 `6` 个用例，覆盖缺省渠道退款号、已有号匹配/冲突、控制字符和治理门禁 |
| payment-core 全量测试 | `mvn test` | 通过 | Surefire XML 汇总 `656` 个用例，`0` failures、`0` errors、`0` skipped |
| gateway-access 全量测试 | `mvn test` | 通过 | Surefire 汇总 `48` 个用例，`0` failures、`0` errors、`0` skipped |
| XML 与补丁检查 | `xmllint --noout ... && git diff --check` | 通过 | Mapper XML 结构合法，Java、XML、测试和文档无空白符错误 |
| 真实环境验证 | 真实渠道按平台退款单号查询、生产凭证、网络、回调、到账和对账 | 未执行 | 不记录真实渠道支持、生产部署、资金到账、对账或财务验收通过 |

### 248.3 当前判断

1. 之前“提示按退款单号查单但代码强制要求渠道退款号”的内部矛盾已经消除，可信查询或回调可以恢复未知受理事实且不覆盖已有渠道号。
2. 状态恢复的条件更新、事件收敛和固定审计已形成本地事务边界；这不等同于真实渠道已经支持该查询键。
3. 该阶段的自动批量核查尚未完成，后续由第 249 节补齐；真实协议映射、签名、生产并发、资金到账和财务处置仍需后续验证。

## 249. 2026-09-10 退款提交未知结果批量补查 V1.0 验证

### 249.1 本轮补强

1. 复用既有人工批量和自动退款查单任务，将已绑定渠道且最新提交事件为 `UNKNOWN` 的处理中退款纳入候选；无未知事件、事件非 UNKNOWN、新鲜未知和无渠道退款继续排除。
2. 未知事件的最近发布时间作为首次等待基线，失败后复用 `CHANNEL_QUERY_FAILED` 指数退避；可信结果恢复渠道退款号后继续走已有受理退款路径。
3. 未知退款与普通退款共用单渠道批次、总批次、任务租约和数据库共享固定窗口限流，不因缺少渠道退款号绕开节流边界。
4. 自动调度仍要求补偿与 gateway-access 查询双开关同时开启，默认关闭；本地实现不推导真实渠道支持平台退款单号查询。

### 249.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| Mapper 集成测试 | `mvn -Dtest=PaymentTaskCenterMapperIntegrationTest test` | 通过 | `9` 个用例，覆盖未知退款首次等待、退避、候选、计数、渠道配额与 P1 SLA 时间边界 |
| 联合定向测试 | `mvn -Dtest=PaymentTaskCenterMapperIntegrationTest,RefundQueryTaskServiceImplTest,PaymentTaskCenterServiceImplTest,PaymentCompensationSchedulerTest,RefundServiceImplTest test` | 通过 | `81` 个用例，`0` failures、`0` errors、`0` skipped |
| payment-core 全量测试 | `mvn test` | 通过 | Surefire XML 汇总 `656` 个用例，`0` failures、`0` errors、`0` skipped |
| XML 与补丁检查 | `xmllint --noout ... && git diff --check` | 通过 | Mapper XML 结构合法，代码、测试和文档无空白符错误 |
| 真实环境验证 | 真实渠道按平台退款单号查询、生产 MySQL、渠道配额、到账与对账 | 未执行 | 不记录真实渠道支持、生产稳定性、退款到账、对账或财务验收通过 |

### 249.3 当前判断

1. 未知退款从单笔人工核查扩展到受控批量补查，且不重新开放高风险退款提交重发路径。
2. 首次等待、退避、渠道批次和固定窗口限流复用已有事实，避免维护第二套调度状态和节流口径。
3. 真实渠道能力、生产 SQL 执行计划、配额校准、网络故障、到账和财务处置仍是外部验证边界。

## 250. 2026-09-10 本地退款查单适配器环境隔离 V1.0 验证

### 250.1 本轮补强

1. 为 `LocalRefundChannelQueryAdapter` 增加 `demo` profile，与支付本地提交/查单和退款本地提交的环境边界保持一致。
2. 非 demo 环境不再把数据库退款状态与渠道退款号回显成 `LOCAL_SIMULATION_FALLBACK`；没有配置真实 gateway-access 查询能力时明确返回 `PAYMENT-1045`。
3. 能力缺失只进入既有最小查询失败日志、任务失败样例和自动退避，不修改退款状态、渠道退款号或 `REFUND_SUBMIT/UNKNOWN` 事件。
4. demo profile 仍保留本地回显，供显式演示环境使用，不作为真实渠道或资金事实。

### 250.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 联合定向测试 | `mvn -Dtest=PaymentLocalAdapterProfileConfigurationTest,RefundChannelQueryServiceImplTest,RefundServiceImplTest,GatewayAccessRefundChannelQueryAdapterTest,PaymentCompensationSchedulerTest test` | 通过 | `48` 个用例，`0` failures、`0` errors、`0` skipped；覆盖 profile 装配、缺少适配器、可信查询、恢复和调度双开关 |
| payment-core 全量测试 | `mvn test` | 通过 | Surefire XML 汇总 `656` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和文档无空白符错误 |
| 真实环境验证 | gateway-access 真实渠道配置、凭证、签名、网络、到账和对账 | 未执行 | 不记录真实渠道查询、生产部署、退款到账、对账或财务验收通过 |

### 250.3 当前判断

1. payment-core 的四类本地支付/退款模拟实现已采用一致的显式 demo 环境边界，非 demo 不再静默使用退款库状态自证外部查询结果。
2. 缺少真实查询能力现在以业务错误和失败历史暴露，自动补偿不会因本地兜底产生伪成功或错误恢复受理事实。
3. 真实渠道接入、生产配置发布、网络故障验证、资金到账和财务核验仍需外部环境完成。

## 251. 2026-09-10 告警通知器本地回执隔离 V1.0 验证

### 251.1 本轮补强

1. IM、SMS、EMAIL 通知器在 Webhook URL 为空时默认返回 `FAILED` 和 `CONFIGURATION_ERROR:WEBHOOK_URL_MISSING`，不生成供应商回执号，不再把配置缺失写成外部受理。
2. 本地 `LOCAL:*:ACCEPTED` 回执改为同时要求 `demo` profile 与 `payment.issue-alert.local-receipt-enabled=true`；默认配置关闭，demo 配置显式开启，非 demo 属性误配仍被代码门禁拒绝。
3. 已配置 Webhook 的真实 HTTP、认证签名、超时、响应状态归一化、回执提取和脱敏路径保持不变；派发服务继续只将 `ACCEPTED/DELIVERED` 视为成功。

### 251.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 联合定向测试 | `mvn -Dtest=LocalImPaymentIssueAlertNotifierTest,LocalSmsPaymentIssueAlertNotifierTest,LocalEmailPaymentIssueAlertNotifierTest,PaymentIssueAlertNotifierConfigurationTest,PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `53` 个用例；覆盖三通道默认失败、demo 回执、默认/demo Spring 装配、真实 Webhook 与派发失败/主备路径 |
| payment-core 全量测试 | `mvn test` | 通过 | Surefire XML 汇总 `663` 个用例，`0` failures、`0` errors、`0` skipped |
| 配置与补丁检查 | YAML 配置契约测试、`git diff --check` | 通过 | 默认本地回执关闭，demo 配置开启，代码和文档无空白符错误 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | `canPromoteTestToMaster=true`、`canCreateRelease=false`；release 仍有候选说明、正式签字、现场核验和稳定观察结束四项真实阻断 |
| 真实环境验证 | 真实 IM/短信/邮件供应商、生产凭证、网络、最终送达与值班响应 | 未执行 | 不记录真实供应商受理、通知送达、人工响应或生产稳定性通过 |

### 251.3 当前判断

1. 默认及非 demo 环境不再用本地回执掩盖通知网关缺失，人工与自动派发共享同一失败闭合结果。
2. demo 能力仍可用于显式演示，但需要 profile 与配置双重开启，且其回执不得作为真实供应商证据。
3. 真实通知供应商接入、凭证托管与轮换、生产网络、送达回执查询、升级响应和稳定性验收仍需外部环境完成。

## 252. 2026-09-10 告警供应商受理证据收紧 V1.0 验证

### 252.1 本轮补强

1. 将 HTTP `2xx`、业务受理与最终送达分层：只有匹配成功码或已识别供应商状态可形成受理事实，无证据和未知状态统一 `FAILED`。
2. 成功码 Pointer 与期望值要求成对配置；半配置返回稳定失败。HTTP 非 `2xx`、成功码不匹配和供应商明确失败继续优先覆盖结果。
3. `providerReceiptNo` 只接受供应商响应真实字段，不再从内部告警号合成；字段缺失与传输异常保持为空，空响应快照固定为 `HTTP_RESPONSE:EMPTY`。

### 252.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 联合定向测试 | `mvn -Dtest=LocalImPaymentIssueAlertNotifierTest,LocalSmsPaymentIssueAlertNotifierTest,LocalEmailPaymentIssueAlertNotifierTest,PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `54` 个用例；覆盖无证据、未知状态、半配置、真实/缺失回执、空响应、传输失败及既有成功路径 |
| payment-core 全量测试 | `mvn test` | 通过 | Surefire XML 汇总 `668` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | Java、测试和交付文档无空白符错误 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | `canPromoteTestToMaster=true`、`canCreateRelease=false`；release 仍有候选说明、正式签字、现场核验和稳定观察结束四项真实阻断 |
| 真实环境验证 | 真实供应商协议、生产凭证、网络、回执查询、最终送达和人工响应 | 未执行 | 不记录真实供应商受理、送达、值班响应或生产稳定性通过 |

### 252.3 当前判断

1. 告警出站结果不再用传输层成功推导业务受理，也不再用内部编号伪装供应商回执，失败与缺失事实均保持可观测。
2. 供应商明确成功但不提供回执号时允许状态成立而回执为空；是否强制回执仍需结合具体供应商协议另行配置和验证。
3. 真实供应商字段映射、凭证托管、网络、异步回执查询、最终送达和生产运维响应仍是外部交付边界。

## 253. 2026-09-10 告警最终送达真实回执查询 V1.0 验证

### 253.1 本轮补强

1. 删除回查服务根据本地 `ACCEPTED` 记录直接构造 `DELIVERED` 的逻辑，新增 `PaymentIssueAlertReceiptQueryAdapter` 扩展点；只有支持当前通道的适配器明确返回 `DELIVERED` 才更新投递与来源告警。
2. 无适配器、空结果、`ACCEPTED/FAILED` 等非送达结果、回执号冲突和查询异常均失败闭合，不修改数据库；明确送达时保存查询快照、真实供应商回执号、送达说明和确认审计字段。
3. 自动回查增加 `payment.issue-alert.receipt-reconcile.enabled` 独立开关并默认关闭，避免尚未配置真实查询能力时由定时任务推进外部状态。

### 253.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 联合定向测试 | `mvn -Dtest=PaymentIssueAlertDeliveryServiceImplTest,PaymentCompensationSchedulerTest,PaymentIssueAlertNotifierConfigurationTest,PaymentTaskCenterMapperIntegrationTest test` | 通过 | `58` 个用例，`0` failures、`0` errors、`0` skipped；覆盖适配器选择、非送达拒绝、回执冲突、默认关闭、事务隔离和真实 Mapper 回写 |
| payment-core 全量测试 | `mvn test` | 通过 | `674` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | Java、Mapper、配置、测试和交付文档无空白符错误 |
| 真实环境验证 | IM、SMS、EMAIL 供应商回执查询、生产凭证、网络、最终送达与人员响应 | 未执行 | 当前没有真实查询适配器，不记录供应商最终送达、人员响应或生产稳定性通过 |

### 253.3 当前判断

1. `ACCEPTED` 不再由本地数据库回查自动升级为 `DELIVERED`，送达确认必须来自外部查询适配器的明确结果。
2. 当前实现完成了失败闭合、关联键保护、事务回写和默认关闭边界，但只提供适配器接口，没有任何真实供应商实现。
3. 后续仍需按具体供应商实现协议、认证签名、查询限流与重试，并在受控环境验证真实回执、最终送达、值班响应和稳定性。

## 254. 2026-09-10 告警回执查询通知集成网关客户端 V1.0 验证

### 254.1 本轮补强

1. 新增 `HttpPaymentIssueAlertReceiptQueryAdapter`，将 payment-core 的通用回执查询扩展点接到环境显式配置的“通知集成网关”HTTP 客户端；开关、URL、应用编码、访问密钥和通道允许列表必须同时有效。
2. 请求携带稳定关联字段和专用应用认证头；响应只接受业务码 `0`、非空数据与 `DELIVERED / ACCEPTED / FAILED` 有限状态，非法字段、空/失败响应和传输异常均失败闭合。
3. 供应商回执快照经统一敏感数据脱敏器处理，并清空响应中的 `renderedContentSnapshot`，保证回查结果不能覆盖原告警正文；服务层继续校验供应商回执号一致性。
4. 自动回查改为 `receipt-reconcile` 与 `receipt-query.gateway-access` 双开关控制且默认关闭，避免只有调度许可但没有真实网关能力时自动扫描。

### 254.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 联合定向测试 | `mvn -Dtest=HttpPaymentIssueAlertReceiptQueryAdapterTest,PaymentCompensationSchedulerTest,PaymentIssueAlertNotifierConfigurationTest,PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `54` 个用例，`0` failures、`0` errors、`0` skipped；覆盖配置门禁、请求响应校验、脱敏、正文保护、双开关与服务回查边界 |
| payment-core 全量测试 | `mvn test` | 通过 | `680` 个用例，`0` failures、`0` errors、`0` skipped |
| 真实环境验证 | 通知集成网关服务端、IM/SMS/EMAIL 供应商协议、生产凭证、网络、最终送达和人员响应 | 未执行 | 当前仓库没有网关服务端或真实供应商实现，不记录真实送达、人员响应或生产稳定性通过 |

### 254.3 当前判断

1. payment-core 已从“只有查询扩展点”推进到具备稳定、默认关闭、失败闭合的通知集成网关客户端，且不会用未知响应或本地数据库事实推导最终送达。
2. 客户端配置、数据校验和脱敏边界已由本地自动化覆盖；网关服务端、具体供应商协议、认证签名、查询限流与生产部署仍不在当前仓库内。
3. 本轮测试证据只能证明客户端契约和内部状态保护，不证明任何真实供应商最终送达、值班人员响应或生产稳定性。

## 255. 2026-09-10 告警供应商回执查询共享限流 V1.0 验证

### 255.1 本轮补强

1. 新增 `PaymentIssueAlertReceiptQueryRateLimitService` 及数据库 Mapper，在已选择支持通道的查询适配器后、发起通知集成网关 HTTP 请求前占用共享额度。
2. 限流按规范化告警通道使用数据库唯一桶和固定窗口原子更新；额度耗尽写独立最小拒绝日志并返回 `PAYMENT-1051`，不更新投递日志或来源告警。
3. 限流开关与网关查询开关形成双门禁并默认关闭；窗口和单通道额度默认分别为 `60` 秒和 `100` 次，非正配置收敛为 `1`。
4. 新增幂等迁移并同步 backend/database 两份运行 schema。拒绝事务采用 `REQUIRES_NEW + noRollbackFor BusinessException`，保证外层逐条失败隔离时仍保留容量事实。

### 255.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 联合定向测试 | `mvn -Dtest=PaymentIssueAlertReceiptQueryRateLimitServiceImplTest,PaymentIssueAlertReceiptQueryRateLimitMapperIntegrationTest,PaymentIssueAlertDeliveryServiceImplTest,PaymentIssueAlertNotifierConfigurationTest test` | 通过 | `41` 个用例，`0` failures、`0` errors、`0` skipped；覆盖双开关、配置下限、真实 SQL 占额/拒绝/重置、调用前限流与状态不更新 |
| payment-core 全量测试 | `mvn test` | 通过 | `686` 个用例，`0` failures、`0` errors、`0` skipped |
| XML 与补丁检查 | `xmllint --noout ... && git diff --check` | 通过 | 新增 Mapper XML 合法，代码、SQL、测试和文档无空白符错误 |
| 真实环境验证 | 通知集成网关容量、供应商配额、生产 MySQL 并发、最终送达和人员响应 | 未执行 | 不记录生产参数校准、真实送达、人员响应或稳定性通过 |

### 255.3 当前判断

1. 告警回执查询已具备跨实例共享容量保护，人工和自动入口不能绕过，额度耗尽不会向外部网关发请求或制造送达事实。
2. 固定窗口和拒绝日志的本地代码、SQL及事务语义已有自动化证据，但生产 MySQL 锁等待、窗口边界突发和真实网关容量仍需压测校准。
3. 当前仓库仍没有通知集成网关服务端或 IM/SMS/EMAIL 真实供应商实现，本轮不改变真实送达、人员响应与生产稳定性的未完成结论。

## 256. 2026-09-10 告警回执回查持久化退避与并发占用 V1.0 验证

### 256.1 本轮补强

1. 在告警投递日志增加回查次数、最近查询时间、下一次查询时间和有限结果，自动候选按首次等待、退避到期、最大次数和批次上限筛选；人工候选可越过退避与自动次数上限。
2. 回查调用前按告警号与读取时次数原子 claim，失败完成和明确送达再按本次递增次数做 CAS fencing，避免过期实例覆盖后继实例；claim 失败不调用查询适配器或共享限流。
3. 适配器缺失、限流拒绝、查询异常、空结果、回执冲突及 `ACCEPTED/FAILED` 只保存有限分类，按基础 `60` 秒、系数 `2`、上限 `900` 秒退避，默认自动最多 `10` 次。
4. 新增幂等运行库迁移并同步两份 schema，七项配置均由环境变量覆盖且非法非正值收敛为 `1`。

### 256.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 联合定向测试 | `mvn -Dtest=PaymentTaskCenterMapperIntegrationTest,PaymentIssueAlertDeliveryServiceImplTest,PaymentIssueAlertNotifierConfigurationTest test` | 通过 | `49` 个用例，覆盖候选、退避、最大次数、人工越过、CAS fencing、有限结果、claim 拒绝不外呼和配置默认值 |
| payment-core 全量测试 | `mvn test` | 通过 | `689` 个用例，`0` failures、`0` errors、`0` skipped |
| XML 与补丁检查 | `xmllint --noout ... && git diff --check` | 通过 | Mapper XML 合法，代码、迁移、测试和文档无空白符错误 |
| 真实环境验证 | MySQL 幂等迁移复跑、在线 DDL、锁等待/事务超时、通知网关及真实供应商回执 | 未执行 | 不记录生产迁移、最终送达、人员响应或稳定性通过 |

### 256.3 当前判断

1. 自动回执查询已具备持久化节流、最大尝试次数和跨实例状态 fencing，避免每轮全量重复查询及旧执行者覆盖新状态。
2. 本地 H2/MySQL 兼容测试证明 SQL 与编排基线可执行，但真实 MySQL 事务等待、外部超时和生产参数仍需受控验证。
3. 当前仓库仍无通知集成网关服务端和真实 IM/SMS/EMAIL 供应商实现，release 的候选说明、正式签字、现场核验和稳定观察阻断均未解除。

## 257. 2026-09-10 告警回执回查队列运营可见性 V1.0 验证

### 257.1 本轮补强

1. 支付任务中心 overview 新增可执行、退避中、查询中和自动次数耗尽四项告警回执回查指标，复用回查任务的首次等待、最大次数和 claim 超时配置。
2. Mapper SQL 限定外部通道、已派发、`ACCEPTED` 和待确认；有效 `QUERYING` 独立计数，过期 claim 回到可执行，正在执行的人工 claim 不重复归入次数耗尽。
3. admin-web 在既有待回查卡片内增加两行分项展示，不新增嵌套卡片或浏览器端状态推算。首次等待中的记录仍保留在待回查总数，四项之和不要求等于总数。

### 257.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 联合定向测试 | `mvn -Dtest=PaymentTaskCenterMapperIntegrationTest,PaymentTaskCenterServiceImplTest,PaymentTaskCenterControllerTest test` | 通过 | `47` 个用例，覆盖四类 SQL、过期 claim、服务参数传递、DTO 回写和接口字段返回 |
| payment-core 全量测试 | `mvn test` | 通过 | `690` 个用例，`0` failures、`0` errors、`0` skipped |
| admin-web 生产构建 | `npm run build` | 通过 | Vite 转换 `68` 个模块并产出静态资源；仅保留既有大于 `500 kB` 的 chunk 警告 |
| XML 与补丁检查 | `xmllint --noout ... && git diff --check` | 通过 | Mapper XML 合法，代码、测试和文档无空白符错误 |
| 真实环境验证 | 生产 MySQL 查询成本、通知集成网关、IM/SMS/EMAIL 最终送达和人员响应 | 未执行 | 不记录真实送达、人员响应、生产稳定性或正式放行通过 |

### 257.3 当前判断

1. 回查调度状态已经从单一总数推进到可执行、等待、占用和人工介入视角，运营可以更准确地区分当前动作。
2. 本轮没有数据库结构变更；本地 H2/MySQL 兼容查询和前端构建只证明代码口径可执行，生产查询性能与参数仍需真实环境验证。
3. release 的候选发布说明、正式签字、发布后现场核验和稳定观察四项阻断未解除，`release/v1.0.0` 不应移动。

## 258. 2026-09-10 告警回执回查明细钻取 V1.0 验证

### 258.1 本轮补强

1. 异常告警明细列表与 CSV 新增五类回查状态筛选及次数、最近时间、下次时间、有限结果证据；服务端校验有限集合并注入与补偿任务同源的三项配置。
2. 任务中心在自动次数耗尽时把既有待回查告警提升为 P1，跳转直接携带耗尽筛选；admin-web 从 URL 初始化筛选，展示明细证据与处理建议，人工确认后重新加载当前条件。
3. 审查修正首次等待与退避重叠边界：仍在首次等待期的记录即使 next time 在未来也只归为 `INITIAL_WAIT`，任务中心退避计数与明细筛选保持一致。

### 258.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 联合定向测试 | `mvn -Dtest=PaymentTaskCenterMapperIntegrationTest,PaymentIssueServiceImplTest,PaymentIssueControllerTest,PaymentTaskCenterServiceImplTest test` | 通过 | `63` 个用例，`0` failures、`0` errors、`0` skipped；覆盖五类筛选、边界互斥、参数校验、CSV 和 P1 跳转 |
| payment-core 全量测试 | `mvn test` | 通过 | `692` 个用例，`0` failures、`0` errors、`0` skipped |
| admin-web 生产构建 | `npm run build` | 通过 | Vite 转换 `68` 个模块；仅有既有大于 `500 kB` 的 chunk 警告 |
| XML 与补丁检查 | `xmllint --noout ... && git diff --check` | 通过 | `PaymentIssueMapper.xml` 与 `PaymentTaskCenterMapper.xml` 合法，无空白符错误 |
| 浏览器/API 联调 | `/payment-issue-alerts?receiptQueryState=EXHAUSTED` | 部分验证 | 筛选控件曾按 URL 自动选中；当时未连接可用 `18081` 后端，未记录业务 API 联调成功 |
| 真实环境验证 | 通知集成网关、供应商协议、生产 MySQL 与人员响应 | 未执行 | 不记录真实查询、送达、响应或稳定性通过 |

### 258.3 当前判断

1. 运营已可从任务中心的耗尽告警直接定位到回查明细，并查看与导出足以支持人工核查的内部调度证据；筛选与展示状态不再在首次等待/退避边界漂移。
2. 本轮本地测试和构建证据完整，但没有可用后端支撑浏览器业务 API 联调，也没有真实通知网关或供应商环境，因此不扩大外部事实结论。
3. release 的候选发布说明、正式签字、发布后现场核验和稳定观察四项阻断保持不变，`release/v1.0.0` 继续冻结。

## 259. 2026-09-10 单条告警回执人工回查 V1.0 验证

### 259.1 本轮补强

1. 新增 `POST /api/payment-task-center/reconcile-issue-alert-receipts/{alertNo}`，从告警明细对指定待确认、已派发、`ACCEPTED` 外部通道记录执行人工回查，并复用任务日志与 overview 刷新。
2. 单条入口使用数据库返回的历史回查次数执行 CAS claim；人工模式仅绕过自动退避与次数上限，不绕过共享限流、真实适配器、有效占用和回执号一致性检查。
3. 修复人工批量候选未返回 `receipt_query_count` 的问题，并在人工/自动候选、claim、失败完成和明确送达回写增加待确认围栏，防止已确认记录被重复查询或覆盖。
4. admin-web 明细行新增人工回查操作，与人工确认互斥；执行后重新加载当前筛选，并按服务端结果展示成功或有限失败摘要。

### 259.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 联合定向测试 | `mvn -Dtest=PaymentIssueAlertDeliveryServiceImplTest,PaymentTaskCenterMapperIntegrationTest,PaymentTaskCenterServiceImplTest,PaymentTaskCenterControllerTest test` | 通过 | `85` 个用例，`0` failures、`0` errors、`0` skipped；覆盖单条入口、历史次数、共享限流复用与确认并发围栏 |
| payment-core 全量测试 | `mvn test` | 通过 | `695` 个用例，`0` failures、`0` errors、`0` skipped |
| admin-web 生产构建 | `npm run build` | 通过 | Vite 转换 `68` 个模块；仅有既有大于 `500 kB` 的 chunk 警告 |
| XML 与补丁检查 | `xmllint --noout ... && git diff --check` | 通过 | `PaymentTaskCenterMapper.xml` 合法，代码、测试和文档无空白符错误 |
| 真实环境验证 | 通知集成网关、供应商协议、生产 MySQL 与人员响应 | 未执行 | 不记录真实查询、送达、响应或稳定性通过 |

### 259.3 当前判断

1. 运营已可在定位具体告警后执行受控单条回查，自动次数耗尽不再只能启动整批任务或直接人工确认；并发确认的数据库保护也已补齐。
2. 本地自动化证明内部接口、状态围栏和页面构建成立，但没有可用真实通知网关或供应商环境，不能据此认定查询或送达成功。
3. release 的候选发布说明、正式签字、发布后现场核验和稳定观察四项阻断保持不变，`release/v1.0.0` 继续冻结。

## 260. 2026-09-10 单条告警回执回查操作者审计 V1.0 验证

### 260.1 本轮补强

1. 单条人工回查接口新增只含 `operator` 的请求 DTO；服务端去空格并校验必填及 `64` 字符上限，非法输入不查询候选、不 claim、不限流也不外呼。
2. 规范化操作人进入共享回查编排，明确送达时同时写入外部告警确认人、来源 outbox 确认人和任务运行日志发起人，修复固定系统常量无法区分实际发起声明的问题。
3. admin-web 复用异常告警页现有人员输入，标签统一为“默认操作人”；单条回查提交该值，空值在请求前提示。

### 260.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 联合定向测试 | `mvn -Dtest=PaymentIssueAlertDeliveryServiceImplTest,PaymentTaskCenterServiceImplTest,PaymentTaskCenterControllerTest test` | 通过 | `76` 个用例，`0` failures、`0` errors、`0` skipped；覆盖 DTO 透传、规范化、日志/确认写入和非法值前置拒绝 |
| payment-core 全量测试 | `mvn test` | 通过 | `697` 个用例，`0` failures、`0` errors、`0` skipped |
| admin-web 生产构建 | `npm run build` | 通过 | Vite 转换 `68` 个模块；仅有既有大于 `500 kB` 的 chunk 警告 |
| 补丁检查 | `git diff --check` | 通过 | 代码、测试和文档无空白符错误 |
| 可信身份与外部事实 | 登录主体、权限、真实通知网关、供应商送达和人员响应 | 未执行 | 不记录可信授权、真实查询、送达、响应或稳定性通过 |

### 260.3 当前判断

1. 单条回查已从固定系统操作者提升为可追溯的实际发起声明，并在成功闭环时与确认审计保持同源。
2. 当前身份仍由请求方自声明，只能作为内部 V1 留痕；可信认证、权限和不可抵赖审计仍是后续外部治理能力，不能伪造为已完成。
3. release 的候选发布说明、正式签字、发布后现场核验和稳定观察四项阻断保持不变，`release/v1.0.0` 继续冻结。

## 261. 2026-09-10 任务中心人工任务操作者统一审计 V1.0 验证

### 261.1 本轮补强

1. 任务中心 `11` 个手工 `POST` 入口统一接收 `{operator}`，由服务层去空格并执行必填和 `64` 字符上限校验；非法值在候选读取、claim、限流和外呼前拒绝。
2. 规范化操作人统一写入任务运行日志，并继续传递到告警派发、批量与单条回执回查的业务审计；人工服务入口不再保留回退固定身份的无参重载，自动调度方法和系统身份未改变。
3. admin-web 任务中心新增共享的人工任务操作人输入，所有人工按钮提交同一 payload，空输入不发请求；异常告警页输入同步限制为最长 `64` 个字符。

### 261.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 联合定向测试 | `mvn -Dtest=PaymentTaskCenterControllerTest,PaymentTaskCenterServiceImplTest,PaymentIssueAlertDeliveryServiceImplTest,PaymentCompensationSchedulerTest test` | 通过 | `91` 个用例，`0` failures、`0` errors、`0` skipped；覆盖 `11` 个入口透传、前置校验、任务/业务审计和自动调度身份 |
| payment-core 全量测试 | `mvn test` | 通过 | `698` 个用例，`0` failures、`0` errors、`0` skipped |
| admin-web 生产构建 | `npm run build` | 通过 | Vite 转换 `68` 个模块；仅有既有大于 `500 kB` 的 chunk 警告 |
| 补丁检查 | `git diff --check` | 通过 | 代码、测试和文档无空白符错误 |
| 可信身份与外部事实 | 登录主体、角色权限、真实渠道、通知供应商、生产环境和稳定观察 | 未执行 | 不记录可信授权、真实送达、生产验证或正式放行通过 |

### 261.3 当前判断

1. 所有任务中心人工执行已使用一致的操作人契约和审计链路，避免部分入口继续以固定身份覆盖实际发起声明；自动任务语义未被人工入口改造改变。
2. 当前操作人仍由请求方自声明，只能作为内部 V1 留痕，不能替代统一认证、授权、人员主数据或不可抵赖审计。
3. release 的候选发布说明、正式签字、发布后现场核验和稳定观察四项阻断未解除，`release/v1.0.0` 继续冻结。

## 262. 2026-09-10 异常人工操作人显式输入 V1.0 验证

### 262.1 本轮补强

1. 支付异常批量动作的 `operator` 改为独立必填字段，不再缺失时回退为 `assignee`；合法值规范化后分别写入操作人与处理人字段，标记已处理时继续以同一操作人确认告警。
2. 批量动作和告警人工确认统一执行去空格、必填及 `64` 字符上限校验，非法值在任何 Mapper 访问前拒绝；已确认告警的人工请求也要求合法操作人。
3. admin-web 任务中心、异常告警明细和异常中心三处操作人默认留空，异常批量动作增加空值请求拦截，三处输入统一最长 `64` 个字符。

### 262.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 异常中心定向测试 | `mvn -Dtest=PaymentIssueServiceImplTest,PaymentIssueControllerTest test` | 通过 | `22` 个用例，`0` failures、`0` errors、`0` skipped；覆盖规范化、职责分离、空白/超长前置拒绝和控制器透传 |
| payment-core 全量测试 | `mvn test` | 通过 | `701` 个用例，`0` failures、`0` errors、`0` skipped |
| admin-web 生产构建 | `npm run build` | 通过 | Vite 转换 `68` 个模块；仅有既有大于 `500 kB` 的 chunk 警告 |
| 补丁与固定默认值检查 | `git diff --check`、管理端源码检索 | 通过 | 三个人工操作页面不再预填 `payment-core-admin` 或“支付运营”作为操作人 |
| 可信身份与外部事实 | 登录主体、角色权限、真实渠道、供应商送达、生产环境和稳定观察 | 未执行 | 不记录可信授权、真实送达、生产验证或正式放行通过 |

### 262.3 当前判断

1. 异常人工动作不再依赖固定默认身份或把处理人冒充发起人，自声明审计与责任分派的语义已经分开。
2. 当前仍未接入可信认证与授权，只能证明本地输入、校验和审计链路一致；退款申请与审核等人工动作的固定身份边界需要继续审查。
3. release 的候选发布说明、正式签字、发布后现场核验和稳定观察四项阻断未解除，`release/v1.0.0` 继续冻结。

## 263. 2026-09-10 退款人工操作人审计 V1.0 验证

### 263.1 本轮补强

1. 退款申请、审核、失败重试和主动查单请求新增显式操作人；服务端统一去空格、校验必填和 `64` 字符上限，并在任何退款 Mapper 访问前拒绝非法声明。
2. 规范化身份进入申请、审核、重试、查单成功/处理中/失败操作日志；任务中心人工和自动退款查单逐笔传递各自 `triggeredBy`，自动失败退款重试及渠道回调身份保持不变。
3. admin-web 退款列表和详情增加默认留空的操作人输入，四类人工动作在空值时不发请求，API 客户端显式提交 `operator`。

### 263.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 退款链路定向测试 | `mvn -Dtest=RefundServiceImplTest,RefundQueryTaskServiceImplTest,PaymentTaskCenterServiceImplTest,RefundApplicationConcurrencyIntegrationTest,RefundControllerTest test` | 通过 | `72` 个用例，`0` failures、`0` errors、`0` skipped；覆盖规范化日志、非法值前置拒绝、人工/自动任务身份和并发退款 |
| payment-core 全量测试 | `mvn test` | 通过 | `702` 个用例，`0` failures、`0` errors、`0` skipped |
| admin-web 生产构建 | `npm run build` | 通过 | Vite 转换 `68` 个模块；仅有既有大于 `500 kB` 的 chunk 警告 |
| 补丁检查 | `git diff --check` | 通过 | 代码与测试提交后，文档增量无空白符错误 |
| 可信身份与外部事实 | 登录主体、角色权限、真实退款渠道、到账核验、生产环境和稳定观察 | 未执行 | 不记录可信授权、真实渠道处理、到账、生产验证或正式放行通过 |

### 263.3 当前判断

1. 退款四个人工入口已与任务中心、异常中心形成一致的显式自声明身份边界，人工查单与自动调度来源不再被固定常量混淆。
2. 本地自动化只证明参数、调用链、操作日志和页面构建一致；可信认证授权、真实渠道协议、退款到账与生产验证仍未完成。
3. release 的候选发布说明、正式签字、发布后现场核验和稳定观察四项阻断未解除，`release/v1.0.0` 继续冻结。

## 264. 2026-09-10 支付日终人工触发人审计 V1.0 验证

### 264.1 本轮补强

1. `POST /api/payment-day-end/run` 请求体和触发人改为显式必填，触发人去空格并限制最长 `64` 个字符；非法值在业务日期解析和全部日终 Mapper 访问前拒绝。
2. 人工运行方式仅接受 `MANUAL / RERUN`，空值默认 `MANUAL`，不再允许人工请求声明 `AUTO` 或缺失触发人时回退 `system-auto`。当前仓库未发现自动日终调度器。
3. admin-web 清空触发人默认值、移除 `AUTO` 选项并增加空值请求拦截，默认业务日期由固定演示值改为浏览器本地前一日。

### 264.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付日终定向测试 | `mvn -Dtest=PaymentDayEndServiceImplTest,PaymentDayEndControllerTest test` | 通过 | `8` 个用例，`0` failures、`0` errors、`0` skipped；覆盖规范化、空请求、空白/超长触发人、人工 `AUTO` 和零 Mapper 访问 |
| payment-core 全量测试 | `mvn test` | 通过 | `703` 个用例，`0` failures、`0` errors、`0` skipped |
| admin-web 生产构建 | `npm run build` | 通过 | Vite 转换 `68` 个模块；仅有既有大于 `500 kB` 的 chunk 警告 |
| 补丁检查 | `git diff --check` | 通过 | 代码、测试与文档增量无空白符错误 |
| 可信身份与外部事实 | 登录主体、日终权限、生产日终、真实渠道核对、正式对账和稳定观察 | 未执行 | 不记录可信授权、生产处理、账务一致、正式验收或放行通过 |

### 264.3 当前判断

1. 支付日终人工入口已消除默认系统身份和人工 `AUTO` 的来源矛盾，非法来源声明不会触发任何日终事实查询或批次写入。
2. 当前只形成自声明操作人的本地审计边界；自动日终仍未实现，可信身份、权限、生产日终与正式对账均需后续真实能力和证据。
3. release 的候选发布说明、正式签字、发布后现场核验和稳定观察四项阻断未解除，`release/v1.0.0` 继续冻结。

## 265. 2026-09-10 支付事件重发操作人审计 V1.0 验证

### 265.1 本轮补强

1. 单笔事件重发请求增加必填操作人，任务中心人工批量和自动事件重试分别逐事件传递规范化人工来源与 `payment-event-scheduler`；非法值在候选读取或投递前失败。
2. 事件表新增最近成功重发操作人与时间，Mapper、两份 schema 和幂等迁移保持一致；列表、CSV、管理端表格及详情快照展示同一审计事实。
3. 重发入口使用事件行锁串行化同事件并发投递；只有派发成功且审计更新命中一行才提交，失败、未知结果和审计写入异常不伪造成功重发记录。

### 265.2 验证结果

| 项目 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 事件重发联合定向测试 | `mvn -Dtest=PaymentEventDispatchServiceImplTest,PaymentEventRepublishAuditMapperIntegrationTest,PaymentEventServiceImplTest,PaymentEventControllerTest,PaymentEventRetryTaskServiceImplTest,PaymentTaskCenterServiceImplTest test` | 通过 | `71` 个用例，`0` failures、`0` errors、`0` skipped；覆盖操作人前置校验、人工/自动来源、SQL 持久化、异常隔离与行锁重发 |
| payment-core 全量测试 | `mvn test` | 通过 | `707` 个用例，`0` failures、`0` errors、`0` skipped |
| admin-web 生产构建 | `npm run build` | 通过 | Vite 转换 `68` 个模块；仅有既有大于 `500 kB` 的 chunk 警告 |
| 结构与补丁检查 | `xmllint --noout PaymentEventMapper.xml`、双 schema 片段比对、`git diff --check` | 通过 | Mapper XML 合法，运行 schema 字段一致，增量脚本使用 `information_schema` 幂等补列 |
| 外部与生产事实 | 可信身份、生产 MySQL 迁移、真实下游消费、退款渠道受理和稳定观察 | 未执行 | 不记录可信授权、生产迁移、最终消费、渠道处理或正式放行通过 |

### 265.3 当前判断

1. 支付事件重发已从“只有次数与状态”补齐到“人工/自动来源逐事件可见”，并为同事件重发增加数据库并发串行化。
2. 最近重发审计只证明 payment-core 成功执行既有派发链路并更新内部状态，不能替代下游幂等消费、账务入账、退款受理或生产稳定性证据。
3. release 的候选发布说明、正式签字、发布后现场核验和稳定观察四项阻断未解除，`release/v1.0.0` 继续冻结。

## 266. 2026-09-10 支付查单与关单发起来源审计 V1.0 验证

### 266.1 本轮补强

1. 保留消费者自助 `POST /api/payments/query|close`，服务端固定使用 `customer-self-service` 并忽略请求体中的操作人；新增管理端 `POST /api/payments/admin/query|close`，显式操作人去空格、必填且最长 `64` 个字符，非法值先于 Mapper、共享限流和渠道查询失败。
2. 查单通知请求快照增加 `initiatedBy`，处理日志使用独立“主动查单”阶段并把 `source` 记录为实际发起来源；渠道异常、非法可信结果和限流拒绝也保留同一来源。查单成功收口及实际关单事件 payload 同步增加 `initiatedBy`。
3. 任务中心人工与自动待回调查单分别逐笔下传规范化 `triggeredBy` 和 `payment-query-scheduler`。admin-web 三个支付操作页面增加默认留空、最长 `64` 字符的操作人输入并切换后台接口，app-web 自助调用保持不变。

### 266.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 联合定向测试 | `mvn -Dtest=PaymentControllerTest,PaymentServiceImplTest,PaymentTaskCenterServiceImplTest test` | 通过 | `112` 个用例，`0` failures、`0` errors、`0` skipped；覆盖公共入口隔离、后台校验、人工/自动来源及成功/失败审计 |
| payment-core 全量测试 | `mvn test` | 通过 | `709` 个用例，`0` failures、`0` errors、`0` skipped |
| admin-web 生产构建 | `npm run build` | 通过 | `68` 个模块完成转换；仅有既有的大 chunk 警告 |
| admin-web 自动测试 | `npm test -- --run` | 不适用 | 当前 `package.json` 未定义 `test` 脚本，命令明确报 `Missing script: "test"` |
| 补丁与入口检查 | `git diff --check`、前后端源码检索 | 通过 | 管理端只调用后台入口，app-web 仍调用公共自助入口；无需数据库迁移 |

### 266.3 当前判断

1. 当前代码已区分消费者自助类别、后台自声明操作人和内部调度身份，并在本地日志及事件中形成可查询来源；主动查单调用成功不等同于渠道支付成功，只有可信查询结果满足既有状态机才收口支付状态。
2. `customer-self-service` 只是应用内来源类别，后台 `operator` 也是请求方自声明；新增 `/admin/*` 路径不构成认证或授权边界。真实渠道、统一身份、角色权限、生产环境、现场核验与稳定观察均未由本轮证明。

## 267. 2026-09-10 退款渠道下发发起来源贯穿 V1.0 验证

### 267.1 本轮补强

1. 退款审核、人工失败重试和任务重试将规范化操作人或 `triggeredBy` 传入提交后派发；事件人工重发与自动重发分别传入本次操作人和 `payment-event-scheduler`。
2. 退款渠道提交成功、调用前失败和结果未知日志分别以 `CHANNEL_SUBMIT / CHANNEL_SUBMIT_FAILED / CHANNEL_SUBMIT_UNKNOWN` 保留实际派发来源，不再写死 `payment-core-admin`。
3. 派发服务统一拒绝空白、超过 `64` 字符或包含控制字符的来源，并保持受理号绑定、事件行锁、重试次数、死信和未知结果人工核查边界不变。

### 267.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 联合定向测试 | `mvn -Dtest=RefundServiceImplTest,PaymentEventDispatchServiceImplTest,PaymentEventServiceImplTest,PaymentEventRetryTaskServiceImplTest,PaymentTaskCenterServiceImplTest test` | 通过 | `97` 个用例，`0` failures、`0` errors、`0` skipped；覆盖审核、人工/任务重试、人工/自动事件重发、三类渠道日志、非法来源零副作用及单条异常隔离 |
| payment-core 全量测试 | `mvn test` | 通过 | `711` 个用例，`0` failures、`0` errors、`0` skipped |
| 固定身份与补丁检查 | 运行代码检索、`git diff --check` | 通过 | Java 运行代码不再以 `payment-core-admin` 写退款渠道下发日志，补丁无空白符错误 |
| admin-web | 变更范围检查 | 未重复执行 | 本轮没有前端代码变化，沿用既有操作人输入与事件重发交互契约 |
| 外部与生产事实 | 真实退款渠道、到账核验、生产环境和稳定观察 | 未执行 | 不记录真实渠道受理、到账、生产验证或正式放行通过 |

### 267.3 当前判断

1. 退款人工动作、任务重试和事件重发的发起来源已能落到实际渠道下发日志，失败与结果未知路径也不再丢失来源。
2. 当前证据只覆盖本地参数、事务和模拟适配器测试，不证明操作人可信、真实渠道已受理或退款已到账；release 四项治理阻断未解除，`release/v1.0.0` 继续冻结。

## 268. 2026-09-10 死信补偿运营闭环 V1.0 验证

### 268.1 本轮补强

1. admin-web 新增死信补偿任务列表、筛选、脱敏快照与处置区，接入允许重放、人工结案和定向重放三类既有接口，并从支付事件页提供直接联查入口。
2. 页面按 `PENDING_REVIEW / REPLAY_FAILED -> READY_TO_REPLAY -> REPLAYING -> REPLAYED` 既有状态机限制按钮；终态与执行中不提供重复动作，所有人工动作要求处置人与处置说明。
3. 服务端按数据库字段容量校验任务号 `64`、处置人 `64` 和处置说明 `512` 字符，并拒绝控制字符；进入重放时同步落库本次处置人、说明与处置时间。

### 268.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 死信任务定向测试 | `mvn -Dtest=PaymentDeadLetterTaskServiceImplTest,PaymentDeadLetterTaskControllerTest test` | 通过 | `13` 个用例，`0` failures、`0` errors、`0` skipped；覆盖规范化、容量/控制字符前置拒绝、状态门禁、原消费者定向路由、发布确认及失败脱敏 |
| payment-core 全量测试 | `mvn test` | 通过 | `713` 个用例，`0` failures、`0` errors、`0` skipped |
| admin-web 生产构建 | `npm run build` | 通过 | Vite 转换 `70` 个模块；仅有既有大于 `500 kB` 的 chunk 警告 |
| 页面运行态 | 浏览器 `1440x900`、`390x844` | 通过 | 导航、筛选、提示和空状态正常，无页面级横向溢出；五个输入控件在窄屏均位于容器内 |
| 业务 API 处置 | 本地 Vite 代理 | 未通过 | 当前代理后端对列表返回 `500`，页面正确显示可诊断错误；未伪造列表、操作成功或 MQ 重放 |
| 补丁与 Mapper | `git diff --check`、`xmllint --noout PaymentDeadLetterTaskMapper.xml` | 通过 | 新页面及已跟踪文件无空白符错误，Mapper XML 合法 |

### 268.3 当前判断

1. 死信补偿已有后端能力现在具备可扫描、可复核、可结案、可定向重放的管理端入口，页面动作与服务端 CAS 状态门禁一致。
2. 当前只能证明本地代码、测试、构建和错误态布局；真实 RabbitMQ 发布、目标消费者幂等处理、账务/清分/结算收口、可信操作人和生产稳定性仍未验证，release 四项治理阻断未解除。

## 269. 2026-09-10 配置复核人员声明控制字符防护 V1.0 验证

### 269.1 本轮补强

1. 配置中心共享人员声明校验在规范化前扫描原始值，统一拒绝 ISO 控制字符，并保留既有首尾空格去除、必填和 `64` 字符上限。
2. 边界测试分别使用可被 `trim()` 去除的换行和不会被 `trim()` 去除的 `U+0085`，证明申请人非法值不写入复核表，复核人不可借不可见字符绕过自声明异人检查或触发配置发布。
3. `requireReviewText` 不复用人员声明的严格规则，保留申请原因与复核意见的正常多行文本能力；本轮未改变前端表单或数据库结构。

### 269.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 配置联合定向测试 | `mvn -Dtest=PaymentConfigServiceImplTest,PaymentGatewayConfigServiceImplTest test` | 通过 | `78` 个用例，`0` failures、`0` errors、`0` skipped；覆盖申请前置拒绝、审批零写入和不可见字符异人绕过 |
| payment-core 全量测试 | `mvn test` | 通过 | `714` 个用例，`0` failures、`0` errors、`0` skipped |
| 前端与数据库 | 变更范围检查 | 未重复执行 | 本轮没有前端、Mapper XML、schema 或迁移变化 |
| 补丁检查 | `git diff --check` | 通过 | Java 与文档变更无空白符错误 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`，release 明细仍有 `4` 项真实阻断 |

### 269.3 当前判断

1. 八类配置 maker-checker 流程现在共享同一人员声明字符边界，申请与审批不再接受控制字符；正常说明文本的多行录入语义保持不变。
2. 人员值仍来自请求方自声明，不构成认证、授权或不可抵赖审计。本地单测不证明真实人员审批、生产配置发布、现场核验或稳定性通过，`release/v1.0.0` 继续冻结。

## 270. 2026-09-10 人工操作人控制字符统一防护 V1.0 验证

### 270.1 本轮补强

1. 九个服务的人工操作人/触发人规范化统一在 `trim()` 前检查原始值，拒绝全部 ISO 控制字符，并保持既有必填、普通首尾空格去除和 `64` 字符容量规则。
2. 覆盖任务中心、告警派发与回查、日终、退款申请/审核/重试/查单、支付事件单笔与批量重发、退款事件派发、后台支付查单/关单、异常批量动作和告警确认。
3. 测试同时使用末尾换行和 `U+0085`，确认非法值不会读取业务候选、获取任务租约、写审计日志、占用共享限流、派发消息或调用渠道/通知供应商。

### 270.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 九类联合定向测试 | `mvn -Dtest=PaymentTaskCenterServiceImplTest,PaymentIssueAlertDeliveryServiceImplTest,PaymentDayEndServiceImplTest,RefundServiceImplTest,PaymentEventServiceImplTest,PaymentIssueServiceImplTest,PaymentEventRetryTaskServiceImplTest,PaymentEventDispatchServiceImplTest,PaymentServiceImplTest test` | 通过 | `224` 个用例，`0` failures、`0` errors、`0` skipped；覆盖原始值控制字符和零副作用边界 |
| payment-core 全量测试 | `mvn test` | 通过 | `714` 个用例，`0` failures、`0` errors、`0` skipped |
| 前端与数据库 | 变更范围检查 | 未重复执行 | 本轮没有前端、Mapper XML、schema 或迁移变化 |
| 补丁检查 | `git diff --check` | 通过 | Java 与文档变更无空白符错误 |
| 统一门禁复判 | `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` | 部分通过 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`，release 明细仍有 `4` 项真实阻断 |

### 270.3 当前判断

1. 主要人工操作入口与其内部事件/任务边界不再接受控制字符，避免不可见来源污染租约键、运行日志和业务操作审计。
2. 操作人仍是请求方自声明或服务端调度类别，不构成可信认证、授权或正式审批。本轮自动化不证明真实渠道、供应商送达、生产执行或稳定性通过，`release/v1.0.0` 继续冻结。

## 271. 2026-09-10 异常批量动作容量与原子校验 V1.0 验证

### 271.1 本轮补强

1. `POST /api/payment-issues/actions` 单次最多接收 `100` 个异常编号；异常编号、处理人、处理备注分别按 `128 / 64 / 255` 字符容量校验，规范化后重复编号直接拒绝。
2. 动作类型、处理人、操作人、备注和全部异常编号在任何 Mapper 访问前一次性完成非空、长度与 ISO 控制字符校验；空白备注仍使用“批量处理”，合法字段统一去除首尾空格。
3. 告警人工确认补齐告警编号 `64` 字符及控制字符边界；管理端补齐处理人和备注长度限制，确认告警 API 对路径编号执行 URL 组件编码。

### 271.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 异常服务定向测试 | `mvn -Dtest=PaymentIssueServiceImplTest test` | 通过 | `20` 个用例，`0` failures、`0` errors、`0` skipped；覆盖超大批次、超长字段、控制字符、规范化重复编号、默认备注和非法告警编号零 Mapper 访问 |
| payment-core 全量测试 | `mvn test` | 通过 | `718` 个用例，`0` failures、`0` errors、`0` skipped |
| admin-web 生产构建 | `npm run build` | 通过 | Vite 转换 `70` 个模块；仅有既有大于 `500 kB` 的 chunk 警告 |
| 补丁检查 | `git diff --check` | 通过 | 后端、前端和文档无空白符错误 |
| 外部与生产事实 | 真实告警、人员身份、生产数据与稳定观察 | 未执行 | 不记录供应商送达、正式授权、生产容量或 release 放行通过 |

### 271.3 当前判断

1. 异常批量写入口已从逐条边走边校验收敛为整批预校验，非法批次不会先读取部分异常，也不会依赖数据库字段截断；正常动作状态机和事务范围保持不变。
2. 本地测试与构建不证明真实操作人、真实告警送达或生产稳定性，release 四项治理阻断未解除，`release/v1.0.0` 继续冻结。

## 272. 2026-09-10 CSV 公式注入统一防护 V1.0 验证

### 272.1 本轮补强

1. 新增共享 `CsvExportSanitizer`，对以 `= / + / - / @`、制表、回车或换行开头的导出值增加文本标记，避免电子表格把业务数据直接解释为公式。
2. 订单、账单、支付、退款、收银台会话、支付请求、资金流、路由执行、事件、处理日志、日终、任务运行、配置快照、异常告警和结算共 `15` 个服务接入同一规则。
3. 各服务保留原有空值、双引号转义和是否总是加引号的格式策略；已有脱敏复制仍先于导出防护执行，不修改 Mapper、DTO、Controller、前端或数据库结构。

### 272.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| `15` 类导出联合定向测试 | `mvn -Dtest=CsvExportSanitizerTest,...,SettlementServiceImplTest test` | 通过 | `259` 个用例，`0` failures、`0` errors、`0` skipped；覆盖全部公式前缀及订单、异常告警真实导出集成 |
| payment-core 全量测试 | `mvn test` | 通过 | `720` 个用例，`0` failures、`0` errors、`0` skipped |
| 导出入口检索 | 检索全部 `exportCsv / export*Csv` 服务 | 通过 | `15` 个导出服务的单元格格式方法均调用共享防护 |
| 前端与数据库 | 变更范围检查 | 未重复执行 | 本轮没有前端、Mapper XML、schema 或迁移变化 |
| 补丁检查 | `git diff --check` | 通过 | Java 与文档无空白符错误 |

### 272.3 当前判断

1. 当前服务端 CSV 导出不再直接输出公式起始文本，且没有统一改写历史 CSV 格式契约；普通值和既有脱敏行为保持不变。
2. 自动化未覆盖所有第三方电子表格实现、人工二次加工或生产下载链路，不能据此形成生产安全验收或 release 放行结论，`release/v1.0.0` 继续冻结。

## 273. 2026-09-10 异常告警导出容量边界 V1.0 验证

### 273.1 本轮补强

1. 修复异常告警 CSV 沿用列表默认分页、前端未传分页参数时实际只导出前 `20` 条的问题；导出强制从第一页读取，不采信调用方页码和每页条数。
2. 导出查询固定 `pageSize=1001`，现有 Mapper SQL 通过 `LIMIT / OFFSET` 在数据库层限制读取量；最多 `1000` 条正常生成 CSV，探测到第 `1001` 条时明确拒绝并要求缩小筛选范围，不返回静默截断文件。
3. 普通告警列表继续保持原 `1..100` 分页规则；筛选、排序、脱敏、公式防护、BOM、表头和列顺序不变。本轮没有前端、Mapper XML、schema 或迁移变化。

### 273.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 异常服务定向测试 | `mvn -Dtest=PaymentIssueServiceImplTest test` | 通过 | `22` 个用例；新增覆盖调用方 `pageNo=3/pageSize=2` 被改为 `1/1001`、`1000` 条允许和 `1001` 条拒绝 |
| payment-core 全量测试 | `mvn test` | 通过 | `722` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | Java 改动无空白符错误 |
| 外部与生产事实 | 真实下载、生产数据规模与稳定观察 | 未执行 | 不记录真实浏览器下载、生产容量、导出权限或稳定性通过 |

### 273.3 当前判断

1. 异常告警导出不再因默认列表分页漏掉第 `21` 条之后的数据，也不会为追求全量导出执行无界查询；超限由服务端显式失败闭合。
2. 自动化只证明本地服务与 SQL 参数边界，不证明生产数据规模、真实下载链路或稳定性，`release/v1.0.0` 继续冻结。

## 274. 2026-09-11 运营 CSV 导出统一容量边界 V1.0 验证

### 274.1 本轮补强

1. 新增共享 `CsvExportRowLimiter`，统一允许最多 `1000` 条并以第 `1001` 条探测超限；空 Mapper 结果按空集合处理，合法结果不复制列表。
2. 订单、账单、支付、退款、收银台会话、支付请求、资金流、路由执行、事件、处理日志、日终、任务运行、异常告警和结算共 `14` 类同步运营导出在脱敏和 CSV 编码前调用共享校验。
3. 原有 `13` 个独立无分页导出 SQL 增加固定 `LIMIT 1001`；异常告警沿用 `pageNo=1 / pageSize=1001`。普通列表分页、筛选、排序、脱敏、公式防护和 CSV 格式保持不变。

### 274.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| `14` 类联合定向测试 | `mvn -Dtest=CsvExportRowLimiterTest,...,PaymentIssueServiceImplTest test` | 通过 | `199` 个用例，`0` failures、`0` errors、`0` skipped；覆盖共享空值、`1000/1001` 边界及各导出既有行为 |
| payment-core 全量测试 | `mvn test` | 通过 | `725` 个用例，`0` failures、`0` errors、`0` skipped；包含现有 MyBatis 集成测试 |
| Mapper 边界检索 | 检查 `13` 个独立导出 `<select>` | 通过 | 每个目标 SQL 均在排序后包含 `LIMIT 1001`，列表查询未改动 |
| 补丁检查 | `git diff --check` | 通过 | Java、XML 与测试无空白符错误 |
| 前端与数据库结构 | 变更范围检查 | 未重复执行 | 本轮无前端、schema 或迁移变化 |

### 274.3 当前判断

1. `14` 类同步运营导出不再执行无界数据库读取，也不会把超限结果伪装成完整文件；用户必须缩小筛选后重试。
2. 配置治理快照为多分区聚合导出，未纳入本轮上限。自动化不证明真实浏览器下载、生产数据规模、执行计划、连接池或稳定性，`release/v1.0.0` 继续冻结。

## 275. 2026-09-11 配置治理快照分区查询与容量边界 V1.0 验证

### 275.1 本轮补强

1. `exportGovernanceSnapshotCsv` 不再构建完整配置总览，而是按 `section` 延迟读取渠道、路由、协议、返回码、网关、控制策略、告警供应商和值班路由八个实际导出分区。
2. 单分区导出只访问一个 Mapper 查询；`ALL` 访问八个导出查询，不再读取只供页面下拉使用、不会进入 CSV 的协议类型选项。
3. 每个分区独立执行 `1000/1001` 共享边界，任一分区超过 `1000` 条时整体拒绝。`ALL` 在八个分区均合法时理论最多生成 `8000` 条，不静默截断。

### 275.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 配置导出定向测试 | `mvn -Dtest=CsvExportRowLimiterTest,PaymentConfigServiceImplTest,PaymentConfigControllerTest test` | 通过 | `83` 个用例；覆盖单分区仅一次查询、`ALL` 八分区且不查协议类型选项、`1001` 条拒绝和控制器契约 |
| payment-core 全量测试 | `mvn test` | 通过 | `727` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | Java 与测试无空白符错误 |
| 前端与持久化 | 变更范围检查 | 未重复执行 | 本轮无前端、Mapper XML、schema 或迁移变化 |
| 生产容量事实 | 真实数据规模、执行计划、下载与稳定观察 | 未执行 | 不记录数据库读取已完全有界、生产性能或真实下载通过 |

### 275.3 当前判断

1. 配置治理快照已消除单分区导出读取全部总览数据的额外成本，并在 Service 层避免超限结果被当作完整治理证据下载。
2. Mapper 查询本身仍无 `LIMIT 1001`，数据库层有界读取尚未闭环；本地测试不证明生产数据规模、权限审批或稳定性，`release/v1.0.0` 继续冻结。

## 276. 2026-09-11 配置治理快照专用 SQL 容量边界 V1.0 验证

### 276.1 本轮补强

1. 八个配置分区分别新增 `find...ForExport` Mapper 方法，治理快照导出只调用这些专用查询。
2. 专用查询与普通查询复用同一 SQL 主体和稳定排序，仅专用查询追加固定 `LIMIT 1001`；普通总览及业务读取语义不变。
3. Service 保留逐分区 `1000/1001` 校验，第 `1001` 行只用于超限探测，任一分区超限时整体拒绝。

### 276.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 配置导出定向测试 | `mvn -Dtest=PaymentConfigServiceImplTest,PaymentConfigExportQueryBoundaryTest test` | 通过 | `67` 个用例；覆盖专用 Mapper 接线、分区超限和 SQL 边界 |
| payment-core 全量测试 | `mvn test` | 通过 | `729` 个用例，`0` failures、`0` errors、`0` skipped |
| MyBatis 最终 SQL | `PaymentConfigExportQueryBoundaryTest` | 通过 | 八个专用查询均以 `LIMIT 1001` 结尾，八个普通查询均不含该限制 |
| 补丁检查 | `git diff --check` | 通过 | Java、XML、测试与文档无空白符错误 |
| 前端与数据库结构 | 变更范围检查 | 未重复执行 | 本轮无前端、schema 或迁移变化 |

### 276.3 当前判断

1. 配置治理快照已同时形成数据库读取上限与 Service 完整性拒绝边界，普通配置总览和业务查询不受导出上限影响。
2. 本地测试不证明生产数据规模、执行计划、真实下载、导出权限或稳定性，`release/v1.0.0` 继续冻结。

## 277. 2026-09-11 外部渠道结果原始控制字符防护 V1.0 验证

### 277.1 本轮补强

1. `PaymentChannelQueryResultValidator` 在空值和空白归一化前扫描原始字段；适配器结果在码表查询前校验，标准化结果和 PaymentService 写入前再次校验。
2. gateway-access 支付提交与退款提交适配器对渠道流水号、状态、状态类型和返回码执行同一原始值边界；正常首尾空格仍允许归一化。
3. 退款事件派发在绑定首次渠道退款号前再次检查原始值。非法受理号转为未知结果，退款保持 `PROCESSING`，不写渠道关联键。

### 277.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 五类联合定向测试 | `mvn -Dtest=PaymentChannelQueryServiceImplTest,PaymentServiceImplTest,GatewayAccessPaymentChannelSubmitAdapterTest,GatewayAccessRefundChannelSubmitServiceImplTest,PaymentEventDispatchServiceImplTest test` | 通过 | `108` 个用例；新增 5 个末尾换行边界及零错误写入断言 |
| payment-core 全量测试 | `mvn test` | 通过 | `734` 个用例，`0` failures、`0` errors、`0` skipped |
| 同类结果校验检索 | 检索外部响应 `validate/normalize/containsIsoControl` | 通过 | 支付查单、支付提交、退款提交与告警回执均在原始值上检查；退款落库前具备二次防御 |
| 补丁检查 | `git diff --check` | 通过 | Java、测试与文档无空白符错误 |
| 前端与数据库结构 | 变更范围检查 | 未重复执行 | 本轮无前端、Mapper、schema 或迁移变化 |

### 277.3 当前判断

1. 外部渠道结果不能再通过尾部控制字符绕过 payment-core 的字段边界，支付和退款既有成功、待处理与未知结果状态机保持不变。
2. 本地测试不证明真实渠道报文、生产网络、渠道受理、资金到账或稳定性，`release/v1.0.0` 继续冻结。

## 278. 2026-09-11 gateway-access 查单原始结果校验 V1.1 验证

### 278.1 本轮补强

1. `GatewayAccessPaymentChannelQueryAdapter` 移除外部字段的前置 `trim`，补默认来源后立即复用 `PaymentChannelQueryResultValidator`，Service 保留返回码映射前后的双重校验。
2. `GatewayAccessRefundChannelQueryAdapter` 不再用 `hasText` 覆盖含控制字符的来源，仅在来源为 `null` 或空串时补 `GATEWAY_ACCESS_HTTP`，随后交由退款结果校验器处理。
3. 合法结果和缺省来源继续按原契约返回；交易状态或来源含尾部换行时统一失败闭合，不进入后续业务写入。

### 278.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 四类联合定向测试 | `mvn -Dtest=GatewayAccessPaymentChannelQueryAdapterTest,GatewayAccessRefundChannelQueryAdapterTest,PaymentChannelQueryServiceImplTest,RefundChannelQueryServiceImplTest test` | 通过 | `22` 个用例；覆盖支付状态、支付来源和退款来源尾部换行 |
| payment-core 全量测试 | `mvn test` | 通过 | `737` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | Java、测试与文档无空白符错误 |
| 前端与数据库结构 | 变更范围检查 | 未重复执行 | 本轮无前端、Mapper、schema 或迁移变化 |

### 278.3 当前判断

1. gateway-access 支付与退款查单不再通过适配器默认值或预清洗掩盖原始控制字符，共享 Service 校验继续作为后续防御。
2. 本地测试不证明真实 gateway-access 服务、渠道响应、生产网络、资金到账或稳定性，`release/v1.0.0` 继续冻结。

## 279. 2026-09-11 告警通知结果失败闭合 V1.0 验证

### 279.1 本轮补强

1. `PaymentIssueAlertDeliveryServiceImpl` 要求通知器返回非空结果和明确 `providerDeliveryStatus`，删除空结果或空状态隐式补 `ACCEPTED` 的路径。
2. 归一化入口在默认文案、默认快照和字段截断前检查状态、说明、回执快照、回执号及渲染内容的原始控制字符。
3. 非法结果沿既有异常分支保存固定脱敏 `SEND_EXCEPTION`，不保存污染字段、不计成功，并继续尝试同通道下一个候选供应商。

### 279.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 告警派发定向测试 | `mvn -Dtest=PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `39` 个用例；同一轮覆盖 `null` 结果、控制字符状态失败和备用供应商成功 |
| payment-core 全量测试 | `mvn test` | 通过 | `738` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | Java、测试与文档无空白符错误 |
| 前端与数据库结构 | 变更范围检查 | 未重复执行 | 本轮无前端、Mapper、schema 或迁移变化 |

### 279.3 当前判断

1. 通知器必须提供明确结果才能形成受理或送达事实，缺失和非法结果不会再被默认值掩盖；候选供应商降级语义保持不变。
2. 本地自动化不证明真实供应商受理、最终送达、人员响应、生产网络或稳定性，`release/v1.0.0` 继续冻结。

## 280. 2026-09-11 告警供应商状态有限集合 V1.0 验证

### 280.1 本轮补强

1. `PaymentIssueAlertDeliveryServiceImpl` 在控制字符检查后将通知器状态执行 `trim + upper-case`，只接受 `ACCEPTED / DELIVERED / FAILED` 三种内部标准状态。
2. 未知状态沿结果契约失败分支保存固定脱敏 `SEND_EXCEPTION`，不把供应商未识别文本写入告警日志，并继续同通道候选供应商 fallback。
3. 带首尾空格的小写合法状态统一落库为大写值，与任务中心及回执补查 Mapper 对 `ACCEPTED` 的精确条件一致。

### 280.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 告警派发定向测试 | `mvn -Dtest=PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `39` 个用例；覆盖未知状态拒绝、合法 `failed / accepted` 归一化与候选降级 |
| payment-core 全量测试 | `mvn test` | 通过 | `738` 个用例，`0` failures、`0` errors、`0` skipped |
| Mapper 条件复核 | 检索 `provider_delivery_status = 'ACCEPTED'` | 通过 | 归一化落库值与回执候选、积压和监控 SQL 精确匹配 |
| 补丁检查 | `git diff --check` | 通过 | Java、测试与文档无空白符错误 |
| 前端与数据库结构 | 变更范围检查 | 未重复执行 | 本轮无前端、Mapper、schema 或迁移变化 |

### 280.3 当前判断

1. 派发成功判断、持久化状态和回执补查选择已使用同一有限集合口径，未知供应商状态不会污染运营查询。
2. 本地测试不证明真实供应商码表、受理、最终送达、人员响应或生产稳定性，`release/v1.0.0` 继续冻结。

## 281. 2026-09-11 告警回执查询结果二次校验 V1.0 验证

### 281.1 本轮补强

1. `PaymentIssueAlertDeliveryServiceImpl` 对所有 `PaymentIssueAlertReceiptQueryAdapter` 返回值执行统一二次校验，防止新增或替换适配器绕过 HTTP 实现已有的字段防护。
2. 状态统一为 `ACCEPTED / DELIVERED / FAILED`；未知状态、控制字符或超容量字段沿查询异常路径写固定 `QUERY_ERROR`，不把原始污染值写入 `receipt_query_result`。
3. 成功回执快照在 Service 持久化前再次脱敏并丢弃无关渲染内容；原记录已有供应商回执号时，查询结果缺号或异号均按 `RECEIPT_CONFLICT` 处理。

### 281.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 告警派发与回执定向测试 | `mvn -Dtest=PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `41` 个用例；覆盖未知状态、控制字符、超容量、状态归一化、快照脱敏和缺失关联回执号 |
| payment-core 全量测试 | `mvn test` | 通过 | `740` 个用例，`0` failures、`0` errors、`0` skipped |
| 持久化条件复核 | 检查 Service 与 Mapper | 通过 | 非可信结果只更新有限 `receipt_query_result`，不命中 `updateIssueAlertProviderReceipt` 或来源告警确认 |
| 前端与数据库结构 | 变更范围检查 | 未重复执行 | 本轮无前端、Mapper、schema 或迁移变化 |

### 281.3 当前判断

1. 回执查询结果已形成“具体适配器首层校验 + Service 统一二次校验 + Mapper 条件更新”的防御链，送达确认需要明确标准状态和现有回执号关联证据。
2. 本地 mock 与自动化不证明真实供应商回执查询、最终送达、人员响应、生产网络或稳定性，`release/v1.0.0` 继续冻结。

## 282. 2026-09-11 告警回执适配器选择异常闭合 V1.0 验证

### 282.1 本轮补强

1. `PaymentIssueAlertDeliveryServiceImpl` 在回执任务领取后捕获适配器选择过程的运行时异常，并通过既有 `completeIssueAlertReceiptQueryAttempt` 写入固定 `QUERY_ERROR`。
2. 选择异常不进入共享限流和供应商查询，避免无效消耗配额；失败尝试继续使用现有指数退避、最大退避和最大次数规则。
3. 未找到支持适配器仍形成 `ADAPTER_MISSING`，没有把“无实现”与“实现选择异常”合并为同一运营分类。

### 282.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 告警派发与回执定向测试 | `mvn -Dtest=PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `42` 个用例；第三次尝试选择异常写 `QUERY_ERROR` 并设置 `240` 秒退避 |
| payment-core 全量测试 | `mvn test` | 通过 | `741` 个用例，`0` failures、`0` errors、`0` skipped |
| 外部调用隔离 | Mockito 调用验证 | 通过 | 适配器选择异常后未调用限流器、`query`、送达更新或来源告警确认 |
| 前端与数据库结构 | 变更范围检查 | 未重复执行 | 本轮无前端、Mapper、schema 或迁移变化 |

### 282.3 当前判断

1. 适配器选择失败已从立即重试风险转为可观测、可退避的有限失败事实，正常查询与无适配器分类不受影响。
2. 本地 mock 不证明真实 Spring 适配器装配、供应商查询、生产调度频率或稳定性，`release/v1.0.0` 继续冻结。

## 283. 2026-09-11 支付查单交易状态有限集合 V1.0 验证

### 283.1 本轮补强

1. `PaymentChannelQueryResultValidator` 将合法 `tradeStatus` 统一去除首尾空格并转为大写，只允许当前 payment-core 使用的八种平台支付状态。
2. 未知状态在 `PaymentChannelReturnCodeStandardizer` 前失败闭合，避免渠道不受控文本进入返回码查询、快照或支付状态持久化。
3. 修正测试中把微信渠道返回码 `USERPAYING` 同时当作平台交易状态的语义混用，明确使用 `WAIT_CALLBACK + USERPAYING` 表达平台状态与渠道原始码。

### 283.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付查单联合定向测试 | `mvn -Dtest=PaymentChannelQueryServiceImplTest,GatewayAccessPaymentChannelQueryAdapterTest,PaymentServiceImplTest test` | 通过 | `84` 个用例；覆盖合法状态规范化、未知状态拒绝和渠道返回码分层 |
| payment-core 全量测试 | `mvn test` | 通过 | `743` 个用例，`0` failures、`0` errors、`0` skipped |
| 标准化前失败隔离 | Mockito 调用验证 | 通过 | 未知状态不调用返回码标准化器，不形成返回码快照或支付成功事实 |
| 补丁检查 | `git diff --check` | 通过 | Java、测试与文档无空白符错误 |
| 前端与数据库结构 | 变更范围检查 | 未重复执行 | 本轮无前端、Mapper、schema 或迁移变化 |

### 283.3 当前判断

1. 平台交易状态与渠道返回码已在查单入口明确分层，合法状态规范化与未知状态失败闭合使用同一共享边界。
2. 本地 mock 与自动化不证明真实 gateway-access、渠道状态码表、查询结果、资金到账、生产网络或稳定性，`release/v1.0.0` 继续冻结。

## 284. 2026-09-11 支付提交结果共享校验 V1.0 验证

### 284.1 本轮补强

1. `PaymentChannelSubmitServiceImpl` 对任意 `PaymentChannelSubmitAdapter` 返回值执行统一首层校验，再对返回码标准化结果执行二次校验。
2. 必填字段、原始控制字符、数据库字段容量和 `info / warn / danger / success` 状态类型有限集合在共享边界收口，具体适配器不能把未识别文本直接交给支付主流程。
3. 非法结果发生在渠道调用之后时保持结果未知语义，只更新既有支付尝试与预付单待确认状态，不保存不可信渠道流水、响应快照或成功事实。

### 284.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付提交联合定向测试 | `mvn -Dtest=PaymentChannelSubmitServiceImplTest,GatewayAccessPaymentChannelSubmitAdapterTest,PaymentChannelReturnCodeStandardizerTest,PaymentServiceImplTest test` | 通过 | `86` 个用例；覆盖空结果、原始控制字符、未知状态类型、标准化后二次校验和主流程未知结果保护 |
| payment-core 全量测试 | `mvn test` | 通过 | `748` 个用例，`0` failures、`0` errors、`0` skipped |
| 标准化前隔离 | Mockito 调用验证 | 通过 | 原始结果非法时返回码 Mapper 零访问 |
| 资金事实隔离 | Mockito 调用验证 | 通过 | 不更新渠道流水、返回码快照，不创建提交事件或通知记录 |
| 补丁检查 | `git diff --check` | 通过 | Java、测试与文档无空白符错误 |
| 前端与数据库结构 | 变更范围检查 | 未重复执行 | 本轮无前端、Mapper、schema 或迁移变化 |

### 284.3 当前判断

1. 支付提交已形成“具体适配器校验 + 共享 Service 首层校验 + 返回码标准化后二次校验 + 主流程结果未知保护”的防御链。
2. 本地 mock 与自动化不证明真实 gateway-access、渠道受理、资金到账、生产网络或稳定性，`release/v1.0.0` 继续冻结。

## 285. 2026-09-11 风控准入响应可信边界 V1.0 验证

### 285.1 本轮补强

1. `PaymentRiskControlServiceImpl` 同时校验统一响应业务码和数据体，不再把“HTTP 返回且 data 非空”等同于风控业务成功。
2. `decision` 只接受 `PASS / REVIEW / INTERCEPT / REJECT`，`decisionType` 只接受 `info / warn / danger / success`；合法值统一大小写，所有字符串在清洗前检查控制字符并执行容量约束。
3. 空结果、业务失败码、未知决策、非法样式、污染或超容量字段统一转为固定 `PAYMENT_RISK_SERVICE_UNAVAILABLE`，不透出 HTTP 运行时异常原文。

### 285.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 风控边界与支付提交定向测试 | `mvn -Dtest=PaymentRiskControlServiceImplTest,PaymentServiceImplTest test` | 通过 | `76` 个用例；覆盖禁用放行、合法规范化、失败码夹带 PASS、未知枚举、控制字符、超容量和 HTTP 异常 |
| payment-core 全量测试 | `mvn test` | 通过 | `755` 个用例，`0` failures、`0` errors、`0` skipped |
| 支付副作用隔离 | Mockito 调用验证 | 通过 | 风控结果失败时不创建支付尝试、风险事件，不检查或调用渠道适配器 |
| 补丁检查 | `git diff --check` | 通过 | Java 与测试无空白符错误 |
| 前端与数据库结构 | 变更范围检查 | 未重复执行 | 本轮无前端、Mapper、schema 或迁移变化 |

### 285.3 当前判断

1. 风控响应已形成“业务成功码 + 决策有限集合 + 字段可信边界 + 支付写入前隔离”的准入链，不可信响应不能推动支付状态机或渠道调用。
2. 本地 mock 与自动化不证明真实风控中心连通、规则有效性、人工复核质量、生产网络或稳定性，`release/v1.0.0` 继续冻结。

## 286. 2026-09-11 运营配置快照可信边界 V1.0 验证

### 286.1 本轮补强

1. `PaymentOpsConfigServiceImpl` 只消费统一响应 `code=0` 的完整快照，业务失败码即使携带数据也不再用于补齐支付方式或渠道。
2. 快照版本必须非负，业务线、支付类型、终端必须与请求一致；主字段及系统控制项统一执行原始控制字符、空白和 ops-config schema 容量校验。
3. 控制项集合限制最多 `1000` 条，拒绝空项、重复编码、非 `ENABLED` 状态和未知样式；URL 构建与 HTTP 运行时异常继续按现有契约降级为 `null`。

### 286.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 运营配置与支付提交定向测试 | `mvn -Dtest=PaymentOpsConfigServiceImplTest,PaymentServiceImplTest test` | 通过 | `78` 个用例；覆盖禁用、合法规范化、失败码夹带数据、三维不匹配、负版本、半成品、污染、超容量、重复控制项和 HTTP 异常 |
| payment-core 全量测试 | `mvn test` | 通过 | `763` 个用例，`0` failures、`0` errors、`0` skipped |
| 降级语义复核 | Service 与 PaymentService 测试 | 通过 | 非法快照返回 `null`；缺少必须默认值时在路由、风控、支付尝试和渠道调用前返回 `PAYMENT-1025` |
| 补丁检查 | `git diff --check` | 通过 | Java 与测试无空白符错误 |
| 前端与数据库结构 | 变更范围检查 | 未重复执行 | 本轮无前端、Mapper、schema 或迁移变化 |

### 286.3 当前判断

1. 运营配置消费已具备“业务成功码 + 请求维度绑定 + 完整快照 + 控制项有限集合”的可信边界，不可信配置不能改写支付路由上下文。
2. 本地 mock 与自动化不证明真实 ops-config 发布、刷新事件传播、缓存一致性、生产网络或稳定性，`release/v1.0.0` 继续冻结。

## 287. 2026-09-11 支付成功事件下游送达判定 V1.0 验证

### 287.1 本轮补强

1. `PaymentEventDispatchServiceImpl` 在 HTTP 派发前统一校验客户端、清分 URL、账务 URL 和账务账号，空白、控制字符或超容量配置均在任何下游调用前失败。
2. 清分、账务响应改为结构化解析，仅当 HTTP 2xx、响应体与 `data` 非空且业务码严格为字符串 `0` 时确认单次调用成功；HTTP 200 业务失败或空响应体不得继续调用账务系统。
3. AMQP 模式新增账务账号必填校验，并校验 exchange、routing key 与账号的控制字符和容量；非法配置不得发布消息或标记事件成功。

### 287.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 成功事件派发定向测试 | `mvn -Dtest=PaymentEventDispatchServiceImplTest test` | 通过 | `27` 个用例；覆盖 HTTP 双下游成功、配置缺失、非 2xx、HTTP 200 业务失败、空响应体、AMQP 配置缺失、重试与死信 |
| payment-core 全量测试 | `mvn test` | 通过 | `767` 个用例，`0` failures、`0` errors、`0` skipped |
| 顺序调用与失败闭合复核 | Service 单元测试与代码审查 | 通过 | 清分未明确成功时不调用账务；任一下游失败均不得标记发布成功 |
| 补丁检查 | `git diff --check` | 通过 | Java 与测试无空白符错误 |
| 前端与数据库结构 | 变更范围检查 | 未重复执行 | 本轮无前端、Mapper、schema 或迁移变化 |

### 287.3 当前判断

1. 本地派发已形成“配置完整 + HTTP 成功 + 业务成功 + 有效数据”的确认边界，但顺序 HTTP 调用不具备 exactly-once：清分成功、账务失败后的重试可能重放清分，仍依赖下游以支付单号等稳定业务标识幂等。
2. 本地 mock 与自动化不证明真实 HTTP/MQ 送达、清分完成、账务入账、资金到账或生产稳定性，`release/v1.0.0` 继续冻结。

## 288. 2026-09-11 超时关单并发跳过统计 V1.0 验证

### 288.1 本轮补强

1. `PaymentExpiryTaskServiceImpl` 将条件更新返回 `0` 的候选从成功数中剥离，记录为 `skippedCount`；只有实际更新支付单并完成关联状态、事件和处理日志写入时才累计成功数。
2. `PaymentTaskCenterServiceImpl` 将跳过数映射为现有 `warningCount`，任务日志与接口摘要显示“并发跳过 N 笔”，不再对运营人员宣称这类候选已成功关闭。
3. 逐笔 `REQUIRES_NEW` 事务、失败支付单摘要上限、资格复验 SQL 和后续候选继续执行语义保持不变。

### 288.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 超时关单与任务中心定向测试 | `mvn -Dtest=PaymentExpiryTaskServiceImplTest,PaymentTaskCenterServiceImplTest,PaymentExpirySchedulerTest test` | 通过 | `39` 个用例；覆盖实际关闭、事务失败后继续、资格竞争跳过、手工与自动任务中心编排 |
| payment-core 全量测试 | `mvn test` | 通过 | `767` 个用例，`0` failures、`0` errors、`0` skipped |
| 竞争副作用复核 | Service 单元测试与既有 Mapper 状态迁移测试 | 通过 | 条件更新为 `0` 时不更新尝试和预付单，不写关闭事件；成功数为 `0`、跳过数为 `1` |
| 补丁检查 | `git diff --check` | 通过 | Java 与测试无空白符错误 |
| Mapper、数据库与前端 | 变更范围检查 | 未重复执行 | 本轮未修改 SQL、schema、迁移或前端代码 |

### 288.3 当前判断

1. 超时关单任务的计数已与持久化事实对齐：扫描候选、实际关闭、资格变化跳过和事务失败不再混为一类，任务中心 warning 也能暴露竞争跳过。
2. 本地 mock 与 H2/MyBatis 既有测试不证明生产 MySQL 锁竞争、多实例调度、真实渠道关单或资金状态，`release/v1.0.0` 继续冻结。

## 289. 2026-09-11 超时关单有界批次 V1.0 验证

### 289.1 本轮补强

1. `PaymentMapper.findExpiredPayments` 接收参数化 `batchSize` 并在按最早过期时间排序后执行 `LIMIT`，不再一次性加载全部历史积压。
2. `PaymentExpiryTaskServiceImpl` 注入 `payment.expiry-close.batch-size`，默认 `100`，运行时夹取到 `1..1000`；`application.yml` 增加对应环境变量入口。
3. 超出本轮上限的候选留待后续轮次处理，现有资格复验、逐笔事务、竞争跳过统计和任务中心结果语义保持不变。

### 289.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 超时关单有界批次定向测试 | `mvn -Dtest=PaymentExpiryTaskServiceImplTest,PaymentTaskCenterServiceImplTest,PaymentExpirySchedulerTest,PaymentMapperStateTransitionIntegrationTest,PaymentExpiryConfigurationTest test` | 通过 | `44` 个用例；覆盖默认值、上下界夹取、参数化 LIMIT、最早过期优先及既有关单编排 |
| payment-core 全量测试 | `mvn test` | 通过 | `769` 个用例，`0` failures、`0` errors、`0` skipped |
| SQL 参数绑定检查 | `PaymentMapperSqlBindingSafetyTest` 与差异审查 | 通过 | 所有 Mapper XML 禁止 `${...}`，批次上限使用 `#{batchSize}` |
| 补丁检查 | `git diff --check` | 通过 | Java、YAML、Mapper XML 与测试无空白符错误 |
| 数据库与前端结构 | 变更范围检查 | 未重复执行 | 本轮无表、索引、schema、迁移或前端代码变化 |

### 289.3 当前判断

1. 超时关单单轮工作量和内存输入已经有界，默认按最早过期的 `100` 笔逐轮收敛；配置错误不能绕过 `1000` 笔硬上限。
2. 本地测试不证明生产 MySQL 执行计划、索引效率、租约周期内完成、持续失败候选不会积压或多实例吞吐，`release/v1.0.0` 继续冻结。

## 290. 2026-09-11 超时关单失败退避与积压可见性 V1.0 验证

### 290.1 本轮补强

1. 新增 `t_payment_expiry_close_retry`，按支付单唯一登记连续失败次数、当前退避秒数、下次允许重试时间和最长 `512` 字符的最近失败摘要；默认参数为 `60 / 2 / 3600`。
2. 关单事务失败后以独立 `REQUIRES_NEW` 事务登记退避；登记失败只输出 WARN 并继续后续候选。成功关闭或条件更新为 `0` 时清除旧标记，候选查询跳过尚未到期的数据。
3. 新建支付单将未产生的渠道流水保存为 `NULL`，资格查询和条件更新使用 `NULLIF(TRIM(channel_transaction_no), '') IS NULL`，同时兼容历史空字符串。
4. 任务中心总积压使用支付单去重统计并继续包含退避数据，新增当前可执行与退避中数量；管理端卡片和焦点告警同步展示两个口径。

### 290.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 超时关单联合定向测试 | `mvn -Dtest=PaymentExpiryTaskServiceImplTest,PaymentTaskCenterServiceImplTest,PaymentExpirySchedulerTest,PaymentMapperStateTransitionIntegrationTest,PaymentTaskCenterMapperIntegrationTest,PaymentExpiryConfigurationTest test` | 通过 | `61` 个用例；覆盖策略夹取、独立失败登记、登记失败降级、消息截断、候选跳过、成功清理、历史空字符串与运营计数 |
| payment-core 全量测试 | `mvn test` | 通过 | `775` 个用例，`0` failures、`0` errors、`0` skipped |
| admin-web 生产构建 | `npm run build` | 通过 | 仅保留项目既有的 Vite chunk 超过 `500 kB` 提示 |
| 响应式页面检查 | Playwright，`1440x1000`、`390x844` | 通过 | 页面与新增元信息均无横向溢出 |
| 补丁检查 | `git diff --check` | 通过 | Java、YAML、Mapper XML、schema、Vue 与测试无空白符错误 |

### 290.3 当前判断

1. 超时关单已形成“有界批次 + 逐笔事务 + 失败退避 + 到期重入 + 运营拆分计数”的本地闭环，持续失败候选不会在退避窗口内反复占用最早批次。
2. 本地 H2、Mockito、构建和浏览器检查不证明生产 MySQL `ON DUPLICATE KEY UPDATE` 执行计划、锁竞争、多实例吞吐、积压清零 SLA、真实渠道关单或生产稳定性，`release/v1.0.0` 继续冻结。

## 291. 2026-09-11 超时关单陈旧重试标记有界清理 V1.0 验证

### 291.1 本轮补强

1. 每次执行超时关单前先清理已经失去关单资格的 `t_payment_expiry_close_retry` 行，覆盖支付单删除、状态变化、渠道流水形成、预付单不再过期和支付尝试形成五类资格消失原因。
2. 清理按重试表主键稳定排序并复用关单 `batchSize`，单轮最多删除 `1..1000` 行；使用嵌套派生表执行单条原子删除，避免目标表自引用限制和无界清理事务。
3. 清理异常只记录 WARN 并继续候选查询；清理结果不进入 `processedCount / successCount / skippedCount / failCount`，避免技术维护数量污染业务关单结果。

### 291.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 陈旧标记清理定向测试 | `mvn -Dtest=PaymentExpiryTaskServiceImplTest,PaymentMapperStateTransitionIntegrationTest,PaymentMapperSqlBindingSafetyTest test` | 通过 | `15` 个用例；覆盖状态变化、支付尝试形成、有界分批删除、合格标记保留、清理失败继续和参数绑定 |
| payment-core 全量测试 | `mvn test` | 通过 | `778` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | Java、Mapper XML、测试与文档无空白符错误 |
| 前端与数据库结构 | 变更范围检查 | 未重复执行 | 本轮无页面、表、字段或索引变化 |

### 291.3 当前判断

1. 超时关单重试表现已具备资格恢复期间保留、成功或竞争时即时删除、资格在退避期消失后按轮次有界清理的完整生命周期。
2. 本地 H2/MyBatis 只能证明 SQL 语义和容量边界，不证明生产 MySQL 执行计划、全表扫描成本、锁影响、多实例清理吞吐或生产稳定性，`release/v1.0.0` 继续冻结。

## 292. 2026-09-11 超时关单失败详情脱敏 V1.0 验证

### 292.1 本轮补强

1. `PaymentExpiryTaskServiceImpl` 将关单失败摘要固定为 `EXPIRY_CLOSE_FAILED:<异常简单类名>`，不再把任意 `RuntimeException.getMessage()` 写入 `t_payment_expiry_close_retry.last_error_message`。
2. 关单事务和失败标记事务在异常离开 `TransactionTemplate` 回调前转换为内部脱敏异常，不保留原异常 cause 或可写堆栈；清理失败和标记持久化失败 WARN 仅记录异常类型。
3. 原关单事务回滚、失败计数、独立重试标记事务、退避参数和后续候选继续处理语义保持不变。

### 292.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 超时关单脱敏定向测试 | `mvn -Dtest=PaymentExpiryTaskServiceImplTest test` | 通过 | `8` 个用例；注入模拟 JDBC 地址、密码、令牌与换行，验证持久化参数、业务 WARN 和事务 DEBUG throwable 均不含原始详情 |
| payment-core 全量测试 | `mvn test` | 通过 | `779` 个用例，`0` failures、`0` errors、`0` skipped |
| 事务语义回归 | Mockito 事务与 Mapper 断言 | 通过 | 单笔失败回滚、失败标记独立写入、失败计数及后续候选继续处理保持不变 |
| 补丁检查 | `git diff --check` | 通过 | Java、测试与文档无空白符错误 |
| 前端、Mapper 与数据库结构 | 变更范围检查 | 未重复执行 | 本轮无页面、SQL、表、字段、索引或迁移变化 |

### 292.3 当前判断

1. 新产生的超时关单失败记录和本服务相关事务日志已不再传播任意异常原文，仅保留定位所需的稳定失败类别与异常类型。
2. 本轮不回填或清理历史失败摘要，也不证明生产日志级别、采集链路、受控诊断设施、历史数据或生产稳定性已满足要求，`release/v1.0.0` 继续冻结。

## 293. 2026-09-11 自动任务租约异常详情脱敏 V1.0 验证

### 293.1 本轮补强

1. `PaymentTaskLeaseService` 的初始化、获取、续期和释放统一通过安全操作边界执行；运行时异常在离开 `TransactionTemplate` 回调前转换为固定分类异常。
2. 固定异常消息为 `PAYMENT_TASK_LEASE_OPERATION_FAILED:<异常简单类名>`，不保留原始 cause 或调用栈；后台续期 WARN 仅保留任务码、owner 与原异常类型。
3. 无 `TransactionTemplate` 的单元测试兼容构造路径也执行相同脱敏，避免测试或非 Spring 直接调用形成不同异常契约。

### 293.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 租约异常脱敏定向测试 | `mvn -Dtest=PaymentTaskLeaseServiceTest test` | 通过 | `5` 个用例；覆盖独立事务、周期续期、owner 丢失、事务日志与无事务模板兼容路径 |
| payment-core 全量测试 | `mvn test` | 通过 | `781` 个用例，`0` failures、`0` errors、`0` skipped |
| 敏感异常注入 | Logback 事件与 throwable 断言 | 通过 | 模拟 JDBC 地址、密码、SQL 和换行未进入服务 WARN 或 Spring 事务 DEBUG throwable，分类异常无 cause、无堆栈 |
| 租约事务回归 | Mockito 事务管理器与 Mapper 断言 | 通过 | 获取提交、续期失败回滚、owner 条件和释放调用语义保持不变 |
| 补丁检查 | `git diff --check` | 通过 | Java、测试与文档无空白符错误 |

### 293.3 当前判断

1. payment-core 共用租约组件不再通过普通 WARN 或 Spring 事务 DEBUG 路径传播任意底层数据库异常详情，且未改变现有租约控制流。
2. 当前租约仍不是强 fencing token；本地测试不证明生产日志配置、数据库网络分区、JVM 长暂停、多实例失约后的业务中止或生产稳定性，`release/v1.0.0` 继续冻结。

## 294. 2026-09-11 任务中心告警写入异常脱敏 V1.0 验证

### 294.1 本轮补强

1. `PaymentTaskCenterServiceImpl` 的逐候选告警写入事务在回调内部捕获运行时异常，并转换为固定 `PAYMENT_ISSUE_ALERT_WRITE_FAILED:<异常简单类名>` 分类。
2. 内部异常不保留原始 cause 或调用栈，避免 Mapper 与驱动异常详情进入 Spring 事务 DEBUG 日志；失败候选摘要仍只记录最多三笔业务异常编号。
3. 内部异常单独保存重复键布尔分类，`DuplicateKeyException` 继续作为幂等竞争跳过，而非普通写入失败。

### 294.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 任务中心定向测试 | `mvn -Dtest=PaymentTaskCenterServiceImplTest test` | 通过 | `36` 个用例；覆盖普通失败回滚继续、最多三笔失败摘要、重复键跳过和既有任务编排 |
| payment-core 全量测试 | `mvn test` | 通过 | `781` 个用例，`0` failures、`0` errors、`0` skipped |
| 敏感异常注入 | Logback 事务事件断言 | 通过 | 模拟 JDBC 地址、密码、SQL 和换行未进入事务日志；throwable 仅保留固定分类且无 cause、无堆栈 |
| 幂等竞争回归 | `DuplicateKeyException` 单元测试 | 通过 | 并发重复仍计 `warningCount=1`、`failCount=0`，摘要显示并发重复跳过 |
| 补丁检查 | `git diff --check` | 通过 | Java、测试与文档无空白符错误 |

### 294.3 当前判断

1. 任务中心逐笔生成告警的数据库异常已不再通过事务 DEBUG 路径传播原始详情，同时保留候选级失败隔离和重复键幂等语义。
2. 本地 Mockito 不证明生产 MySQL 唯一键竞争、日志采集配置、多实例告警生成、人员响应或生产稳定性，`release/v1.0.0` 继续冻结。

## 295. 2026-09-11 支付提交事务异常脱敏 V1.0 验证

### 295.1 本轮补强

1. `PaymentServiceImpl` 的提交事务执行器新增统一安全回调：受控 `BusinessException` 直接重抛，未知运行时异常转换为固定 `PAYMENT_SUBMIT_TRANSACTION_FAILED:<异常简单类名>`。
2. 固定异常不保留原始 cause、suppressed 或调用栈，覆盖风控状态落库、提交准备、提交完成和结果未知恢复四类事务调用。
3. 渠道调用本身仍位于事务外；渠道已返回结果后若完成事务失败，现有 catch 继续启动独立恢复事务，不因脱敏包装改变结果未知语义。

### 295.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付提交定向测试 | `mvn -Dtest=PaymentServiceImplTest test` | 通过 | `70` 个用例；覆盖参数、幂等、路由、风控、并发令牌、渠道提交、结果未知恢复和支付收口 |
| payment-core 全量测试 | `mvn test` | 通过 | `781` 个用例，`0` failures、`0` errors、`0` skipped |
| 敏感异常注入 | Logback 事务事件断言 | 通过 | 模拟 JDBC 地址、密码、SQL 与换行未进入事务日志；分类异常无 cause、suppressed 和堆栈 |
| 结果未知事务顺序 | Mockito `InOrder` | 通过 | 准备事务提交、渠道调用、完成事务回滚、恢复事务提交顺序不变 |
| 恢复事实断言 | Mapper 交互 | 通过 | 尝试标记“结果未知，待查单”，预付单标记“结果待确认”，有渠道流水时支付单进入 `WAIT_CALLBACK` |

### 295.3 当前判断

1. 支付提交内部事务不再向 Spring DEBUG 日志传播任意数据库异常详情，并保留受控业务错误码与渠道调用后的结果未知恢复原则。
2. 本地 Mockito 不证明真实渠道是否受理、恢复事务在数据库故障下必然成功、主动查单最终收口、资金到账或生产稳定性，`release/v1.0.0` 继续冻结。

## 296. 2026-09-11 失败退款重试事务异常脱敏 V1.0 验证

### 296.1 本轮补强

1. `RefundRetryTaskServiceImpl` 的逐笔 `REQUIRES_NEW` 事务新增安全执行边界，内部运行时异常统一转换为固定 `REFUND_RETRY_TRANSACTION_FAILED:<异常简单类名>`。
2. 固定异常不保留原始 cause、suppressed 或调用栈；批处理外层仍只统计该候选失败，不向任务中心暴露内部异常详情。
3. 原逐笔隔离语义不变：失败候选回滚，后续退款候选继续执行并可独立提交，失败退款单号最多返回三个。

### 296.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 退款重试定向测试 | `mvn -Dtest=RefundRetryTaskServiceImplTest test` | 通过 | `1` 个用例；覆盖单笔回滚、后续候选继续和失败汇总 |
| payment-core 全量测试 | `mvn test` | 通过 | `781` 个用例，`0` failures、`0` errors、`0` skipped |
| 敏感异常注入 | Logback 事务事件断言 | 通过 | 模拟 JDBC 地址、密码、SQL 与换行未进入事务日志；分类异常无 cause 和堆栈 |
| 批次隔离 | Mockito 事务交互 | 通过 | 两笔成功提交、一笔失败回滚，失败后的最后一笔仍执行 |

### 296.3 当前判断

1. 失败退款重试的逐笔事务不再向 Spring DEBUG 日志传播内部运行时异常详情，并保持批处理计数与候选隔离语义。
2. 本地 Mockito 不证明真实渠道下发、退款到账、生产事务隔离、多实例调度或生产稳定性，`release/v1.0.0` 继续冻结。

## 297. 2026-09-11 告警逐笔事务异常脱敏 V1.0 验证

### 297.1 本轮补强

1. `PaymentIssueAlertDeliveryServiceImpl` 的告警派发与供应商回执对账共用安全事务回调，内部运行时异常统一转换为 `PAYMENT_ISSUE_ALERT_ITEM_TRANSACTION_FAILED:<异常简单类名>`。
2. 固定异常不保留原始 cause、suppressed 或调用栈；派发和回执外层仍只汇总当前告警失败，不向任务日志传播内部数据库异常详情。
3. 已在方法内部处理的供应商返回失败、适配器缺失、回执限流和查询失败仍沿用既有分类，不进入异常包装路径。

### 297.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 告警投递服务定向测试 | `mvn -Dtest=PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `42` 个用例；覆盖派发、补发、回执查询、限流、领取和任务租约 |
| payment-core 全量测试 | `mvn test` | 通过 | `781` 个用例，`0` failures、`0` errors、`0` skipped |
| 敏感异常注入 | Logback 事务事件断言 | 通过 | 模拟 JDBC 地址、密码、SQL 与换行未进入事务日志；分类异常无 cause 和堆栈 |
| 逐条隔离 | Mockito 事务交互 | 通过 | 当前告警失败回滚，下一告警继续执行并提交 |

### 297.3 当前判断

1. 告警派发和回执对账的逐笔事务不再向 Spring DEBUG 日志传播内部运行时异常详情，并保持原有结果分类与候选隔离语义。
2. 本地 Mockito 不证明真实通知送达、供应商回执可信、人员确认、多实例调度或生产稳定性，`release/v1.0.0` 继续冻结。

## 298. 2026-09-11 支付事件重投递事务异常脱敏 V1.0 验证

### 298.1 本轮补强

1. `PaymentEventRetryTaskServiceImpl` 的逐笔重投递事务新增安全回调，内部运行时异常统一转换为 `PAYMENT_EVENT_RETRY_TRANSACTION_FAILED:<异常简单类名>`。
2. 固定异常不保留原始 cause、suppressed 或调用栈；批处理外层仍只累计事件失败并记录最多三个失败事件号。
3. `republish` 返回 `false` 的受控失败继续正常提交，异常事件才回滚；两类失败均不阻断后续事件候选。

### 298.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 事件重投递定向测试 | `mvn -Dtest=PaymentEventRetryTaskServiceImplTest test` | 通过 | `3` 个用例；覆盖手工/自动候选、操作人校验、返回失败和异常隔离 |
| payment-core 全量测试 | `mvn test` | 通过 | `781` 个用例，`0` failures、`0` errors、`0` skipped |
| 敏感异常注入 | Logback 事务事件断言 | 通过 | 模拟 JDBC 地址、密码、SQL 与换行未进入事务日志；分类异常无 cause 和堆栈 |
| 批次事务语义 | Mockito 事务交互 | 通过 | 一笔成功、一笔异常回滚、一笔受控失败提交，后续事件仍执行 |

### 298.3 当前判断

1. 支付事件重投递事务不再向 Spring DEBUG 日志传播内部运行时异常详情，并保持成功、受控失败、异常失败和候选隔离语义。
2. 本地 Mockito 不证明外部 MQ/HTTP 未收到回滚事件，也不证明下游幂等、真实消费、跨系统一致性或生产稳定性，`release/v1.0.0` 继续冻结。

## 299. 2026-09-11 退款即时派发失败日志脱敏 V1.0 验证

### 299.1 本轮补强

1. `RefundServiceImpl` 的退款渠道事件即时派发失败 WARN 不再附带原始异常对象，仅记录稳定 outbox 事件号和异常类全名。
2. 退款单号不再直接进入该日志，避免未经过控制字符格式校验的业务标识造成日志注入；运维可通过稳定事件号反查退款单。
3. 事务同步语义不变：退款状态、操作日志和 outbox 先提交，`afterCommit` 派发失败被吸收，事件保留给后续重投递补偿。

### 299.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 退款服务定向测试 | `mvn -Dtest=RefundServiceImplTest test` | 通过 | `30` 个用例；覆盖申请、审核、重试、回调、查单、额度和即时派发 |
| payment-core 全量测试 | `mvn test` | 通过 | `782` 个用例，`0` failures、`0` errors、`0` skipped |
| 敏感异常注入 | Logback WARN 事件断言 | 通过 | 模拟 JDBC 地址、密码、SQL、换行、退款单号和 throwable 均未进入日志 |
| 提交后补偿边界 | 真实事务同步回调 + Mockito | 通过 | outbox 已写入，`afterCommit` 派发异常未向退款业务链路传播 |

### 299.3 当前判断

1. 退款即时派发失败日志已保留必要事件追踪能力并移除原始异常及未校验业务标识，已提交 outbox 仍可进入补偿链路。
2. 本地 Mockito 不证明真实渠道受理、事件补偿必然成功、退款到账、生产日志采集或生产稳定性，`release/v1.0.0` 继续冻结。

## 300. 2026-09-11 告警网关异常详情脱敏 V1.0 验证

### 300.1 本轮补强

1. `AbstractLocalPaymentIssueAlertNotifier` 将签名失败统一转换为 `PAYMENT_ISSUE_ALERT_SIGNATURE_FAILED:<异常简单类名>`，不再拼接签名算法配置或保留底层 cause。
2. 非法响应 JSON 统一转换为 `PAYMENT_ISSUE_ALERT_RESPONSE_JSON_INVALID:<异常简单类名>`，不再拼接 Jackson 异常 message 或保留可能含响应正文的 cause。
3. `PaymentIssueAlertDeliveryServiceImpl` 既有安全持久化与供应商回退逻辑不变：异常供应商写 `SEND_EXCEPTION` 脱敏快照，随后继续候选供应商。

### 300.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 联合定向测试 | `mvn -Dtest=LocalImPaymentIssueAlertNotifierTest,PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `55` 个用例；覆盖签名、响应解析、持久化脱敏和候选供应商回退 |
| payment-core 全量测试 | `mvn test` | 通过 | `784` 个用例，`0` failures、`0` errors、`0` skipped |
| 签名异常注入 | JUnit + Mockito | 通过 | 模拟 URL、token 与换行不进入固定摘要，异常 cause 为空，网关未被调用 |
| 非法 JSON 注入 | JUnit + Mockito | 通过 | 模拟响应密文、URL 与换行不进入固定摘要，异常 cause 为空 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 300.3 当前判断

1. 告警网关签名和响应解析的异常对象本身已完成最小化，持久化层继续只保留既有安全失败事实并保持供应商切换语义。
2. 本地 Mockito 不证明真实通知网关可用、签名协议正确、供应商响应可信、密钥托管或生产稳定性，`release/v1.0.0` 继续冻结。

## 301. 2026-09-11 支付回调验签底层异常脱敏 V1.0 验证

### 301.1 本轮补强

1. 时间戳格式错误和极值溢出继续返回 `PAYMENT_CALLBACK_TIMESTAMP_INVALID`，但业务异常不再挂载包含输入详情的 `NumberFormatException` 或底层算术异常。
2. RSA 签名非 Base64、签名字节长度错误和公钥材料损坏继续分别返回既有签名/凭证业务错误码与安全文案，不再保留底层解析或 JCA cause。
3. 非预期验签异常统一为 `PAYMENT_CALLBACK_VERIFICATION_FAILED:<异常简单类名>`，关闭 cause、suppressed 和可写堆栈；支付与退款回调入口、nonce 防重放和状态机顺序不变。

### 301.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 联合定向测试 | `mvn -Dtest=PaymentCallbackSignatureServiceImplTest,PaymentServiceImplTest,RefundServiceImplTest,GlobalExceptionHandlerTest test` | 通过 | `122` 个用例；覆盖验签、支付/退款回调事务入口和统一错误响应 |
| payment-core 全量测试 | `mvn test` | 通过 | `785` 个用例，`0` failures、`0` errors、`0` skipped |
| 畸形时间戳注入 | JUnit + 真实 HMAC 计算 | 通过 | 输入含模拟敏感片段，返回固定业务码与安全文案，cause 为空且 nonce 未写入 |
| RSA 错误分类回归 | JUnit + 真实 RSA 密钥对 | 通过 | 非 Base64、错误长度、公钥损坏保持原错误码，底层 cause 为空 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 301.3 当前判断

1. 支付与退款回调验签的已知失败继续保持可解释业务错误，底层异常详情不再随异常链传播；非预期验证失败也已具备固定最小分类。
2. 本地测试不证明真实渠道签名向量、证书链、公钥轮换、密钥托管、网关网络来源或生产稳定性，`release/v1.0.0` 继续冻结。

## 302. 2026-09-11 支付与退款主动查单异常链脱敏 V1.0 验证

### 302.1 本轮补强

1. `PaymentServiceImpl` 与 `RefundServiceImpl` 捕获渠道查单未知运行时异常后，继续先写既有固定失败事实，再转换为无底层 cause 的业务异常。
2. `BusinessException.sanitized` 保留调用方指定的业务错误码和安全文案，同时关闭 cause、suppressed 与可写堆栈，避免外层事务 DEBUG 日志或异常处理链展开渠道客户端、驱动或数据库原始详情。
3. 已知 `BusinessException`、共享限流、失败退避统计、查询结果校验、支付/退款状态机和后续补偿入口不变。

### 302.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付与退款服务定向测试 | `mvn -Dtest=PaymentServiceImplTest,RefundServiceImplTest test` | 通过 | `100` 个用例，`0` failures、`0` errors、`0` skipped |
| payment-core 全量测试 | `mvn test` | 通过 | `785` 个用例，`0` failures、`0` errors、`0` skipped |
| 敏感异常注入 | JUnit + Mockito | 通过 | 模拟 JDBC 地址、密码、SQL、令牌与换行不进入固定文案、cause、suppressed、堆栈或失败持久化参数 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 302.3 当前判断

1. 支付与退款主动查单未知异常已不再沿业务异常链传播底层详情，同时保留失败事实、原业务错误码、固定外部文案和自动补偿所需统计。
2. 早期记录中“内部 cause 供受控排障”的描述已被本轮实现替代；当前普通异常链不再承担原始技术诊断载体，若生产确需诊断必须另建受控设施。
3. 本地 Mockito 不证明真实渠道查单、生产事务日志配置、补偿最终成功、资金到账、对账收口或生产稳定性，`release/v1.0.0` 继续冻结。

## 303. 2026-09-11 退款渠道提交上游异常脱敏 V1.0 验证

### 303.1 本轮补强

1. `GatewayAccessRefundChannelSubmitServiceImpl` 捕获未知上游运行时异常后，改用 `RefundChannelSubmitException.sanitized` 返回固定 `PAYMENT-1049` 和安全文案，不再保留原始 cause。
2. 脱敏异常关闭 suppressed 与可写堆栈，但继续携带 `resultUnknown=true`；事件派发仍进入结果未知分支，不把超时、连接失败或非法受理结果解释为渠道明确拒绝。
3. 明确发生在外部调用前的配置门禁和已有 `RefundChannelSubmitException` 路径保持原错误分类与重试语义。

### 303.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 退款提交与事件派发定向测试 | `mvn -Dtest=GatewayAccessRefundChannelSubmitServiceImplTest,PaymentEventDispatchServiceImplTest test` | 通过 | `32` 个用例，`0` failures、`0` errors、`0` skipped |
| payment-core 全量测试 | `mvn test` | 通过 | `785` 个用例，`0` failures、`0` errors、`0` skipped |
| 敏感异常注入 | JUnit + Mockito | 通过 | 模拟 JDBC 地址、密码、请求令牌、响应正文与换行不进入 message、cause、suppressed 或堆栈 |
| 结果未知语义 | 事件派发回归 | 通过 | 事件保持 `UNKNOWN`，不记普通失败或成功，退款状态和额度不被错误收口 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 303.3 当前判断

1. 退款渠道提交适配器已同时保留资金安全所需的“结果未知”分类和异常最小化边界，避免为了脱敏而放宽重复退款保护。
2. 本地 Mockito 不证明真实渠道请求是否送达、渠道是否受理、退款到账、主动查单或对账最终收口、生产日志配置或生产稳定性，`release/v1.0.0` 继续冻结。

## 304. 2026-09-11 支付配置治理异常 cause 脱敏 V1.0 验证

### 304.1 本轮补强

1. `PaymentConfigServiceImpl` 中 `17` 处唯一键并发冲突继续转换为原固定 `IllegalArgumentException`，但不再保留可能包含数据库连接与 SQL 的 `DuplicateKeyException` cause。
2. 回调通知地址、网关基础地址和返回码 CSV 解析失败继续返回原安全提示，并移除可能包含输入材料或解析上下文的底层 cause。
3. 渠道、网关、路由、控制策略、协议、返回码、告警供应商和值班路由的唯一待办、基线指纹、版本校验、事务回滚和自声明异人复核语义不变。

### 304.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 配置服务定向测试 | `mvn -Dtest=PaymentConfigServiceImplTest test` | 通过 | `65` 个用例，覆盖配置治理、版本、回滚、导入、自检和并发冲突 |
| payment-core 全量测试 | `mvn test` | 通过 | `785` 个用例，`0` failures、`0` errors、`0` skipped |
| 敏感异常注入 | JUnit + Mockito | 通过 | 模拟 JDBC 地址、密码、SQL、令牌和换行不进入固定提示，业务校验异常 cause 为空 |
| 静态异常复核 | `rg` | 通过 | 配置服务业务冲突、URL 与 CSV 固定校验分支不再挂载底层 cause |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 304.3 当前判断

1. 支付配置治理的用户可见提示与事务失败语义保持稳定，同时普通异常链不再承载数据库或解析器原始详情。
2. 请求中的 maker/reviewer 仍是自声明字段，本地测试不构成可信身份、角色授权、正式审批、签字、生产配置发布或稳定性证据，`release/v1.0.0` 继续冻结。

## 305. 2026-09-11 支付渠道提交意外异常脱敏 V1.0 验证

### 305.1 本轮补强

1. `PaymentChannelSubmitServiceImpl` 将适配器、结果校验或返回码标准化抛出的非业务运行时异常统一转换为固定 `PAYMENT-1048`。
2. 固定异常不保留 cause、栈或 suppressed，避免渠道响应、数据库连接、SQL、凭据和其他第三方诊断详情沿支付提交调用链扩散。
3. 既有明确业务异常与支付主流程的“结果未知，待查单”语义保持不变，不因脱敏开放直接重复支付。

### 305.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 渠道提交与支付服务定向测试 | `mvn -Dtest=PaymentChannelSubmitServiceImplTest,PaymentServiceImplTest test` | 通过 | `80` 个用例，覆盖渠道提交边界、未知结果恢复与事务异常最小化 |
| payment-core 全量测试 | `mvn test` | 通过 | `786` 个用例，`0` failures、`0` errors、`0` skipped |
| 敏感异常注入 | JUnit + Mockito | 通过 | 模拟 JDBC 地址、密码、SQL、令牌和换行不进入错误消息、cause、栈或 suppressed |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 305.3 当前判断

1. 支付渠道提交的意外失败已在领域服务边界最小化，同时保留结果未知场景必须查单确认的资金安全约束。
2. 本地 Mockito 不证明请求真实送达、渠道受理、主动查单、对账收口、资金到账或生产稳定性，`release/v1.0.0` 继续冻结。

## 306. 2026-09-11 死信接入毒消息重入队治理 V1.0 验证

### 306.1 本轮补强

1. `PaymentDeadLetterTaskService` 新增不受支持路由的专用永久异常，固定文案不携带 routing key 原文。
2. `PaymentDeadLetterTaskAmqpListener` 对专用异常执行 `basicNack(requeue=false)`；其他运行时失败继续 `basicNack(requeue=true)`，成功持久化后才 ACK。
3. 专用分类避免永久毒消息循环，同时不把 Mapper 或基础设施瞬时异常误判为不可重试错误。

### 306.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 死信服务与监听器定向测试 | `mvn -Dtest=PaymentDeadLetterTaskServiceImplTest,PaymentDeadLetterTaskAmqpListenerTest test` | 通过 | `14` 个用例，覆盖合法入账、未知路由、ACK/NACK、人工复核与重放 |
| payment-core 全量测试 | `mvn test` | 通过 | `787` 个用例，`0` failures、`0` errors、`0` skipped |
| 路由原文注入 | JUnit + Mockito | 通过 | 模拟令牌和换行的 routing key 不进入固定异常文案，也不触发 Mapper 或 broker 调用 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 306.3 当前判断

1. 死信接入已区分永久路由错误和可恢复运行时失败，避免当前接入队列对确定性错误无限重试。
2. 本地 Mockito 不证明 `requeue=false` 后消息进入隔离队列、生产 RabbitMQ 拓扑正确、消息绝不丢失或生产稳定性，`release/v1.0.0` 继续冻结。

## 307. 2026-09-11 死信消息头持久化脱敏 V1.0 验证

### 307.1 本轮补强

1. `PaymentDeadLetterTaskServiceImpl` 在调用 Mapper 前对序列化后的消息头快照执行统一敏感数据脱敏，避免新任务把授权头等凭据原文写入数据库。
2. 非敏感 `x-retry-count` 等诊断头继续保留；既有历史行仍由查询和处置响应的出参脱敏兜底，本轮不声称已清理历史数据。
3. 定向重放继续只读取原始消息体，并重建 `compensationTaskNo`、`compensationReplay` 等受控消息头，不依赖数据库中的原始入站消息头。

### 307.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 敏感数据与死信服务定向测试 | `mvn -Dtest=PaymentSensitiveDataMaskerTest,PaymentDeadLetterTaskServiceImplTest test` | 通过 | `20` 个用例；验证 `Authorization` 在 Mapper 入参中为 `[REDACTED]`、令牌原文不可恢复，且 `x-retry-count` 保留 |
| payment-core 全量测试 | `mvn test` | 通过 | `787` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 307.3 当前判断

1. 新入账死信任务的凭据类消息头已在持久化边界最小化，同时保持诊断信息和既有定向重放契约。
2. 本轮无前端、Mapper、schema 或迁移变化；本地 Mockito 不证明真实 RabbitMQ 消息、生产凭据保护、历史数据库已清理或生产稳定性，`release/v1.0.0` 继续冻结。

## 308. 2026-09-11 外部风控决策说明最小化 V1.0 验证

### 308.1 本轮补强

1. `PaymentRiskControlServiceImpl` 对外部 `message` 保持原始控制字符与容量校验，通过后不再保留第三方原文。
2. 规范化决策统一生成 payment-core 自有说明，覆盖准入通过、人工复核、拦截和拒绝四类语义，风险事件只持久化该固定说明。
3. 既有业务码、决策与样式白名单，以及命中编码、风险标签、复核号和事件号边界保持不变。

### 308.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 风控适配器与支付主流程定向测试 | `mvn -Dtest=PaymentRiskControlServiceImplTest,PaymentServiceImplTest test` | 通过 | `77` 个用例；验证模拟 JDBC 地址和凭据被固定平台说明替换，并验证人工复核风险事件写入平台文案 |
| payment-core 全量测试 | `mvn test` | 通过 | `788` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 308.3 当前判断

1. 外部风控任意说明已从支付核心的返回和持久化事实中移除，同时保留决策分类、人工复核关联和失败闭合能力。
2. 本轮无前端、Mapper、schema 或迁移变化；本地 mock 不证明真实风控策略、人工复核质量、外部服务可用性或生产稳定性，`release/v1.0.0` 继续冻结。

## 309. 2026-09-11 风控人工复核关联完整性 V1.0 验证

### 309.1 本轮补强

1. `PaymentRiskControlServiceImpl` 对规范化后的 `REVIEW` 决策增加 `reviewNo` 条件必填校验，复用既有控制字符与 `64` 字符容量边界。
2. 缺少复核单号的外部响应固定按风控结果非法处理，不进入支付状态更新、风险事件写入或渠道调用。
3. 普通 `PASS` 无需伪造复核号，已通过人工复核的 `PASS` 可保留原号；复用待审单不强制生成新的风险事件号。

### 309.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 风控适配器与支付主流程定向测试 | `mvn -Dtest=PaymentRiskControlServiceImplTest,PaymentServiceImplTest test` | 通过 | `78` 个用例；覆盖缺失 `reviewNo` 的 `REVIEW` 固定拒绝，以及正常复核、批准后重提和支付主流程 |
| payment-core 全量测试 | `mvn test` | 通过 | `789` 个用例，`0` failures、`0` errors、`0` skipped |
| 跨系统静态契约核对 | risk-control-system Service 与接口文档 | 通过 | 新建和复用待审单均返回复核号；复用待审单允许不生成新事件号 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 309.3 当前判断

1. payment-core 不再接受无法关联人工审核单的 `REVIEW` 决策，待风控复核状态具备最小跨系统追踪键。
2. 本轮无前端、Mapper、schema 或迁移变化；静态核对和本地 mock 不证明真实审核动作、跨系统数据一致、生产网络或稳定性，`release/v1.0.0` 继续冻结。

## 310. 2026-09-11 风控拦截事件关联完整性 V1.0 验证

### 310.1 本轮补强

1. `PaymentRiskControlServiceImpl` 对规范化后的 `INTERCEPT` 决策增加 `eventNo` 条件必填校验，复用既有控制字符与 `64` 字符容量边界。
2. 缺少风险事件号的外部响应固定按风控结果非法处理；合法事件号去除首尾空格后交付支付主流程。
3. `REVIEW` 继续以 `reviewNo` 为必填关联键；当前存在无追踪号的策略直接拒绝路径，故本轮未收紧 `REJECT`，避免虚构跨系统契约。

### 310.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 风控适配器与支付主流程定向测试 | `mvn -Dtest=PaymentRiskControlServiceImplTest,PaymentServiceImplTest test` | 通过 | `80` 个用例；覆盖 `INTERCEPT` 缺失事件号固定拒绝、合法事件号规范化及支付主流程回归 |
| payment-core 全量测试 | `mvn test` | 通过 | `791` 个用例，`0` failures、`0` errors、`0` skipped |
| 跨系统静态契约核对 | risk-control-system Service 与接口、设计文档 | 通过 | 当前直接拦截路径生成事件号；直接拒绝路径不具备统一追踪键 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 310.3 当前判断

1. payment-core 不再接受无法关联风险事件的 `INTERCEPT` 决策，外部结果在进入支付主流程前失败闭合。
2. 本轮无前端、Mapper、schema 或迁移变化；静态核对和本地 mock 不证明风险事件真实存在、真实拦截、跨系统生产一致或稳定性，`release/v1.0.0` 继续冻结。

## 311. 2026-09-11 风控决策与展示样式一致性 V1.0 验证

### 311.1 本轮补强

1. `PaymentRiskControlServiceImpl` 不再只分别接受决策和样式有限集合，而是校验规范化后的固定组合。
2. `PASS` 仅接受 `success`，`REVIEW` 仅接受 `warn`，`INTERCEPT / REJECT` 仅接受 `danger`；矛盾组合统一按风控结果非法处理。
3. 固定组合校验发生在风险字段交付支付主流程前，避免风险事件持久化与支付状态语义相反的展示样式。

### 311.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 风控适配器与支付主流程定向测试 | `mvn -Dtest=PaymentRiskControlServiceImplTest,PaymentServiceImplTest test` | 通过 | `82` 个用例；覆盖四类矛盾组合固定拒绝、合法 `REJECT + danger` 及支付主流程回归 |
| payment-core 全量测试 | `mvn test` | 通过 | `793` 个用例，`0` failures、`0` errors、`0` skipped |
| 跨系统静态契约核对 | risk-control-system Service | 通过 | 当前 `PASS / REVIEW / INTERCEPT / REJECT` 返回路径分别使用 `success / warn / danger / danger` |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 311.3 当前判断

1. payment-core 不再接受业务决策与展示严重度矛盾的风控响应，风险事件的两类字段保持同一语义。
2. 本轮无前端、Mapper、schema 或迁移变化；静态核对和本地 mock 不证明真实策略、实际处置、跨系统生产一致或稳定性，`release/v1.0.0` 继续冻结。

## 312. 2026-09-11 风控拒绝来源追踪完整性 V1.0 验证

### 312.1 本轮补强

1. payment-core 对 `REJECT` 增加来源追踪条件：人工拒绝使用 `reviewNo`，策略直接拒绝使用 `eventNo`，至少一个必须存在并满足既有字符与容量边界。
2. risk-control-system 的策略直接拒绝生成或复用“已拒绝”风险事件，返回事件号和命中编码；人工复核拒绝只返回原复核单号。
3. 首轮 risk-control 测试发现直接拒绝结果未携带 `hitCode`，实现修正后重新执行定向及全量测试通过。

### 312.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| payment-core 定向测试 | `mvn -Dtest=PaymentRiskControlServiceImplTest,PaymentServiceImplTest test` | 通过 | `84` 个用例；覆盖无追踪号拒绝失败、人工拒绝复核号和直接拒绝事件号 |
| payment-core 全量测试 | `mvn test` | 通过 | `795` 个用例，`0` failures、`0` errors、`0` skipped |
| risk-control-system 定向与全量测试 | `mvn -Dtest=RiskControlServiceImplTest,RiskControlIntegrationTest test`、`mvn test` | 通过 | `9` 个用例；覆盖直接拒绝事件写入及人工拒绝不创建事件 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 312.3 当前判断

1. 两类 `REJECT` 来源均具备与真实业务来源相符的最小追踪键，payment-core 不再接受无法定位来源的拒绝结果。
2. 本轮无前端、Mapper、schema 或迁移变化；本地 H2、Mockito 和静态核对不证明真实策略命中、人工处置、生产数据一致或稳定性，`release/v1.0.0` 继续冻结。

## 313. 2026-09-11 风控事件型终态引用完整性 V1.0 验证

### 313.1 本轮补强

1. payment-core 对 `INTERCEPT` 增加 `hitCode` 条件必填校验，使事件型拦截必须同时具备 `eventNo + hitCode`。
2. `REJECT` 有 `reviewNo` 时继续按人工拒绝处理；没有 `reviewNo` 时按策略直接拒绝处理，并要求同时具备 `eventNo + hitCode`。
3. risk-control-system 将黑名单和策略直接拒绝说明与 `REJECT` 决策对齐，不再用“直接拦截”描述拒绝结果。

### 313.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| payment-core 定向测试 | `mvn -Dtest=PaymentRiskControlServiceImplTest,PaymentServiceImplTest test` | 通过 | `86` 个用例；新增覆盖拦截缺少命中编码、直接拒绝缺少命中编码的失败闭合 |
| payment-core 全量测试 | `mvn test` | 通过 | `797` 个用例，`0` failures、`0` errors、`0` skipped |
| risk-control-system 定向测试 | `mvn -Dtest=RiskControlServiceImplTest,RiskControlIntegrationTest test` | 通过 | `10` 个用例；覆盖黑名单与策略直接拒绝的引用及说明语义 |
| risk-control-system 全量测试 | `mvn test` | 通过 | `10` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 313.3 当前判断

1. 事件型拦截和策略直接拒绝均具备事件号与命中编码，人工拒绝继续保留来源相符的复核单号，跨系统引用矩阵已完整闭合。
2. 本轮无前端、Mapper、schema 或迁移变化；本地 H2、Mockito 和静态核对不证明真实风险事件、真实策略命中、人工审核、跨系统生产一致或稳定性，`release/v1.0.0` 继续冻结。

## 314. 2026-09-11 风控启用态端点配置失败闭合 V1.0 验证

### 314.1 本轮补强

1. `PaymentRiskControlServiceImpl` 将显式关闭与启用态配置错误拆分：`enabled=false` 继续本地放行，`enabled=true` 且评估地址为空时固定失败。
2. 配置错误返回 `PAYMENT_RISK_SERVICE_UNAVAILABLE` 与安全提示，不发起 HTTP 请求，也不进入支付主流程的尝试、事件、状态或渠道副作用。
3. 合法地址、远端异常、业务码、决策样式和追踪字段校验保持原契约。

### 314.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 风控适配器与支付主流程定向测试 | `mvn -Dtest=PaymentRiskControlServiceImplTest,PaymentServiceImplTest test` | 通过 | `87` 个用例；覆盖显式关闭放行、启用态空地址失败和零 HTTP 调用 |
| payment-core 全量测试 | `mvn test` | 通过 | `798` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 314.3 当前判断

1. 风控端点部署参数缺失不再静默降级为准入通过，显式关闭与错误配置具备不同且可审计的业务结果。
2. 本轮无前端、Mapper、schema 或迁移变化；本地 Mockito 不证明生产配置正确、真实风控连通、策略有效性或稳定性，`release/v1.0.0` 继续冻结。

## 315. 2026-09-11 外部 HTTP 超时统一有界 V1.0 验证

### 315.1 本轮补强

1. 共用 `RestTemplate` 工厂将所有传入超时统一夹在 `1..60000ms`，同时用于连接超时和读取超时。
2. 支付提交/查单、退款提交/查单、支付成功下游投递、风控、运营配置、告警回执及三类通知共十一类 HTTP 出站路径自动复用该边界，不新增并行客户端配置。
3. IM、短信和邮件通知失败摘要保存归一化值；`timeout-ms=0` 的行为测试确认摘要显示 `timeout=1ms`。

### 315.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 相关 HTTP 适配器联合定向测试 | `mvn -Dtest=AbstractLocalPaymentIssueAlertNotifierTest,PaymentRiskControlServiceImplTest,PaymentOpsConfigServiceImplTest,GatewayAccessPaymentChannelSubmitAdapterTest,GatewayAccessPaymentChannelQueryAdapterTest,GatewayAccessRefundChannelSubmitServiceImplTest,GatewayAccessRefundChannelQueryAdapterTest,HttpPaymentIssueAlertReceiptQueryAdapterTest,LocalImPaymentIssueAlertNotifierTest,LocalSmsPaymentIssueAlertNotifierTest,LocalEmailPaymentIssueAlertNotifierTest test` | 通过 | `79` 个用例；覆盖负数、零、正常值、超大值及通知摘要一致性 |
| payment-core 全量测试 | `mvn test` | 通过 | `799` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 315.3 当前判断

1. payment-core 自有 HTTP 出站客户端不再接受无限或超过一分钟的连接/读取等待配置，默认配置行为保持不变。
2. 本轮无前端、Mapper、schema 或迁移变化；本地测试不证明真实网络超时精度、上游取消、线程池容量、生产参数合理或稳定性，`release/v1.0.0` 继续冻结。

## 316. 2026-09-11 支付成功下游投递超时可配置 V1.0 验证

### 316.1 本轮补强

1. `PaymentEventDispatchServiceImpl` 自动装配入口新增 `payment.event-dispatch.http-timeout-ms`，替代支付成功 HTTP 下游投递的硬编码 `3000ms`。
2. `application.yml` 通过 `HSP_PAYMENT_EVENT_DISPATCH_HTTP_TIMEOUT_MS` 提供环境覆盖，默认保持 `3000ms`；实际客户端继续使用共享 `1..60000ms` 连接与读取超时边界。
3. 新增配置契约测试，验证仓库默认配置可解析为 `3000`，并回归事件投递实现和共享 HTTP 客户端。

### 316.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 事件投递与超时边界定向测试 | `mvn -Dtest=PaymentEventDispatchServiceImplTest,PaymentEventDispatchConfigurationTest,AbstractLocalPaymentIssueAlertNotifierTest test` | 通过 | `29` 个用例，`0` failures、`0` errors、`0` skipped |
| payment-core 全量测试 | `mvn test` | 通过 | `800` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 316.3 当前判断

1. 支付成功事件 HTTP 下游投递具备环境级超时配置，同时保持统一有界行为和原 `3000ms` 默认兼容性。
2. 本轮无前端、Mapper、schema 或迁移变化；本地测试不证明真实清分或会计系统连通、事件送达、外部受理、生产参数合理或稳定性，`release/v1.0.0` 继续冻结。

## 317. 2026-09-11 AMQP 发布确认等待有界 V1.0 验证

### 317.1 本轮补强

1. 支付成功 AMQP 发布与死信定向重放统一限制 publisher confirm 等待时长，正的超大配置不再原样传入 RabbitMQ 客户端。
2. `payment.amqp.publisher-confirm-timeout-ms` 和 `HSP_PAYMENT_AMQP_PUBLISHER_CONFIRM_TIMEOUT_MS` 保持原配置入口；默认及非正值回退均为 `5000ms`，正值上限为 `60000ms`。
3. 回归直接验证两条路径传给 `waitForConfirmsOrDie` 的实际值，不改变交换机、路由键、消息载荷、失败状态或人工复核门禁。

### 317.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| AMQP 发布与死信重放定向测试 | `mvn -Dtest=PaymentEventDispatchServiceImplTest,PaymentDeadLetterTaskServiceImplTest test` | 通过 | `42` 个用例，`0` failures、`0` errors、`0` skipped |
| payment-core 全量测试 | `mvn test` | 通过 | `804` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 317.3 当前判断

1. payment-core 自有 AMQP publisher confirm 等待不再接受超过一分钟的运行参数，同时保持原默认和非正值兼容回退语义。
2. 本轮无前端、配置键、Mapper、schema 或迁移变化；本地 Mockito 不证明真实 broker 确认、消息送达、消费结果、死信重放成功、生产参数合理或稳定性，`release/v1.0.0` 继续冻结。

## 318. 2026-09-11 支付成功事件派发模式失败闭合 V1.0 验证

### 318.1 本轮补强

1. `PaymentEventDispatchServiceImpl` 在选择传输实现前校验 `payment.event-dispatch.mode`，仅允许 `http / amqp`，不再把未知值当成 HTTP。
2. 合法模式继续忽略普通大小写和首尾空格；空值、拼写错误或其他模式进入既有事件失败/死信收口，不调用任一外部传输客户端。
3. 回归实例同时持有 `RestTemplate` 与 `RabbitTemplate`，验证非法 `amqpp` 配置下两种客户端调用次数均为零。

### 318.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付事件派发定向测试 | `mvn -Dtest=PaymentEventDispatchServiceImplTest test` | 通过 | `30` 个用例，覆盖非法模式零 HTTP/MQ 调用与既有两种合法模式 |
| payment-core 全量测试 | `mvn test` | 通过 | `805` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 318.3 当前判断

1. 事件派发模式部署参数错误不再触发跨协议静默降级，支付成功事件仍由既有重试和死信状态机收口。
2. 本轮无前端、配置键、Mapper、schema 或迁移变化；本地 Mockito 不证明真实 HTTP 或 RabbitMQ 连通、事件送达、下游受理、生产配置正确或稳定性，`release/v1.0.0` 继续冻结。

## 319. 2026-09-11 支付成功事件状态收口完整性 V1.0 验证

### 319.1 本轮补强

1. HTTP 和 AMQP 两个支付成功分支在外部投递完成后共用同一个成功状态收口点，`markPublishSuccess` 必须返回 `1` 才判定派发成功。
2. 更新 `0` 行时抛出固定内部异常并由现有失败处理捕获，按当前重试次数标记 `FAILED` 或 `DEAD_LETTER`，任务中心不会记录重发成功审计。
3. 已有 HTTP、AMQP 成功用例显式模拟更新 `1` 行；新增 HTTP 双下游成功、状态更新 `0` 行的回归验证最终返回失败并调用失败状态更新。

### 319.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付事件派发定向测试 | `mvn -Dtest=PaymentEventDispatchServiceImplTest test` | 通过 | `31` 个用例；覆盖两种传输成功收口与状态更新冲突失败 |
| payment-core 全量测试 | `mvn test` | 通过 | `806` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 319.3 当前判断

1. 支付成功事件不再仅凭外部客户端返回成功就向调用方报告成功，本地事件状态必须同步收口；更新冲突保留可运营处理的失败状态。
2. 外部投递可能已经发生，`FAILED / DEAD_LETTER` 不证明下游未受理；本地 Mockito 不证明真实幂等消费、生产并发、跨系统一致或稳定性，`release/v1.0.0` 继续冻结。

## 320. 2026-09-11 支付成功事件金额校验与失败收口 V1.0 验证

### 320.1 本轮补强

1. `PaymentEventDispatchServiceImpl` 在任何 HTTP 或 AMQP 调用前校验支付明细金额，拒绝空值、空白、非法数字、非正数、超过两位小数及超过系统金额上限的值。
2. 合法人民币展示值继续支持 `¥` 与千分位；金额边界与支付预付金额保持一致，不再把缺失值静默转换为 `BigDecimal.ZERO`。
3. 金额解析和服务者查询进入既有异常捕获范围，失败时按当前重试次数调用 `markPublishFailed` 或 `markPublishDeadLetter`。

### 320.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付事件派发定向测试 | `mvn -Dtest=PaymentEventDispatchServiceImplTest test` | 通过 | `34` 个用例；新增空金额、零金额和非法金额重发状态回归 |
| payment-core 全量测试 | `mvn test` | 通过 | `809` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 320.3 当前判断

1. payment-core 不再向支付成功下游发送零值或不满足系统金额边界的载荷，确定性坏数据能够进入事件失败与死信收口。
2. 本轮无前端、接口字段、Mapper SQL、schema 或迁移变化；本地 Mockito 不证明真实支付金额、下游受理、跨系统数据一致或生产稳定性，`release/v1.0.0` 继续冻结。

## 321. 2026-09-11 缺失支付明细事件死信收口 V1.0 验证

### 321.1 本轮补强

1. `dispatchPaymentSuccess` 查询不到支付明细时改为调用统一 `markFailedOrDeadLetter`，不再绕过最大重试次数判断。
2. 当前重试次数加一未达 `3` 次时继续写 `FAILED` 与下次重试时间；达到上限时写 `DEAD_LETTER` 并清空自动重试时间。
3. 自动重试候选 SQL 保持不变，仍只挑选到期 `FAILED / PENDING` 事件；由派发状态机确保不可恢复的缺失明细事件最终退出自动候选集。

### 321.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付事件派发定向测试 | `mvn -Dtest=PaymentEventDispatchServiceImplTest test` | 通过 | `35` 个用例；覆盖首次缺明细失败与达到上限转死信 |
| payment-core 全量测试 | `mvn test` | 通过 | `810` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 321.3 当前判断

1. 缺失支付明细的确定性失败不再无限停留于自动重试候选，达到既有上限后可由死信与人工处置流程接管。
2. 本轮无前端、接口字段、Mapper SQL、schema 或迁移变化；本地 Mockito 不证明真实数据缺失、生产死信落库、人工处置或稳定性，`release/v1.0.0` 继续冻结。

## 322. 2026-09-11 事件人工重发状态门禁 V1.0 验证

### 322.1 本轮补强

1. `PaymentEventDispatchServiceImpl.republish` 对支付成功和退款提交事件增加服务端最终状态门禁，普通重发只接受 `PENDING / FAILED / DEAD_LETTER`。
2. 已 `SUCCESS`、空状态或未知状态的事件直接返回失败，不调用 HTTP、AMQP 或退款渠道，也不更新发布状态，避免上层写入与真实执行不符的成功重发审计。
3. 退款 `UNKNOWN` 继续进入既有 `PAYMENT-1050` 专用查单和人工核查分支，本轮不改变未知渠道结果的保护语义。

### 322.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 事件重发联合定向测试 | `mvn -Dtest=PaymentEventDispatchServiceImplTest,PaymentEventServiceImplTest test` | 通过 | `45` 个用例；覆盖服务端拒绝成功支付与成功退款再次调用下游 |
| payment-core 全量测试 | `mvn test` | 通过 | `812` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 322.3 当前判断

1. 前端禁用状态不再是唯一保护，服务端能够拒绝直接接口调用或并发旧页面对已成功事件的重复派发，并阻止虚假成功重发审计。
2. 本轮无前端、接口字段、Mapper SQL、schema 或迁移变化；本地 Mockito 不证明生产事件状态、真实渠道或消息系统未被历史重复调用、资金结果或稳定性，`release/v1.0.0` 继续冻结。

## 323. 2026-09-11 支付成功事件资金终态一致性 V1.0 验证

### 323.1 本轮补强

1. `PaymentEventDispatchServiceImpl` 在构造任何支付成功下游载荷前，校验查询到的支付单号与事件指定支付单号完全一致。
2. 同一边界要求支付单当前状态精确为 `SUCCESS`；`WAIT_CALLBACK / CLOSED` 等非成功状态、空状态和错单查询结果不再进入清分、会计或 AMQP。
3. 校验失败复用既有事件重试与死信收口，不修改支付单本身，也不猜测或自动修复资金事实。

### 323.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 事件派发联合定向测试 | `mvn -Dtest=PaymentEventDispatchServiceImplTest,PaymentEventServiceImplTest test` | 通过 | `47` 个用例；覆盖非成功支付状态与支付单号错配失败闭合 |
| payment-core 全量测试 | `mvn test` | 通过 | `814` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 323.3 当前判断

1. payment-core 不再仅凭事件类型和可解析金额向下游声明支付成功，支付单终态和根业务标识必须同时一致。
2. 本轮无前端、接口字段、Mapper SQL、schema 或迁移变化；本地 Mockito 不证明生产支付状态、真实清分记账、消息消费、资金到账或稳定性，`release/v1.0.0` 继续冻结。

## 324. 2026-09-11 支付成功事件渠道追踪事实门禁 V1.0 验证

### 324.1 本轮补强

1. `PaymentEventDispatchServiceImpl` 将支付渠道和渠道流水号纳入支付成功事实校验，两个字段必须非空、在现有 `64 / 128` 字符容量内且不含 ISO 控制字符。
2. 缺少任一追踪字段时，在服务者查询、HTTP、AMQP 和事件成功状态更新前失败闭合，继续使用事件失败与死信状态机。
3. 本轮不把渠道追踪字段新增到下游载荷，不修改支付主单或自动补造渠道事实；跨系统字段扩展需按独立接口版本治理。

### 324.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 事件派发联合定向测试 | `mvn -Dtest=PaymentEventDispatchServiceImplTest,PaymentEventServiceImplTest test` | 通过 | `49` 个用例；覆盖渠道编码与渠道流水号缺失时零下游调用 |
| payment-core 全量测试 | `mvn test` | 通过 | `816` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 324.3 当前判断

1. payment-core 不再把缺少渠道追踪根据的 `SUCCESS` 支付传播为跨系统成功事件，但这只是本地派发前数据门禁。
2. 本轮无前端、接口字段、Mapper SQL、schema 或迁移变化；本地 Mockito 不证明渠道真实受理、扣款或到账、下游消费、对账完成或稳定性，`release/v1.0.0` 继续冻结。

## 325. 2026-09-11 支付事件终态防回退 V1.0 验证

### 325.1 本轮补强

1. `PaymentEventMapper.xml` 为成功、失败、死信和未知状态更新增加允许来源状态，`SUCCESS` 不再被迟到失败结果覆盖。
2. 成功更新继续允许退款 `UNKNOWN` 经核查后收口为 `SUCCESS`；失败仅接受 `PENDING / FAILED`，死信与未知只接受 `PENDING / FAILED / DEAD_LETTER`。
3. `updateLastRepublishAudit` 增加 `publish_status = SUCCESS` 条件，失败事件不能单独写入成功重发操作人和时间。

### 325.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 状态转换联合定向测试 | `mvn -Dtest=PaymentEventRepublishAuditMapperIntegrationTest,PaymentEventDispatchServiceImplTest,PaymentEventServiceImplTest test` | 通过 | `52` 个用例；H2 执行成功、死信、未知和审计门禁，绑定 SQL 校验失败更新来源状态 |
| payment-core 全量测试 | `mvn test` | 通过 | `818` 个用例，`0` failures、`0` errors、`0` skipped |
| Mapper XML 检查 | `xmllint --noout PaymentEventMapper.xml` | 通过 | XML 结构有效 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 325.3 当前判断

1. 服务层锁和状态校验之外，数据库更新语句本身也开始保护成功终态与成功审计一致性，降低迟到或旁路更新造成状态回退的风险。
2. 本轮无前端、接口字段、schema 或迁移变化；H2 与绑定 SQL 验证不等于生产 MySQL 并发压测、真实下游处理或资金结果确认，`release/v1.0.0` 继续冻结。

## 326. 2026-09-11 支付事件重发有界批次 V1.0 验证

### 326.1 本轮补强

1. `PaymentEventRetryTaskServiceImpl` 新增 `payment.event-retry.batch-size`，默认 `100`，非法小值按 `1`、超大值按 `1000` 执行。
2. 人工和自动候选 Mapper 查询均使用参数绑定的 `LIMIT`；人工仍选择 `FAILED`，自动仍选择到期的 `FAILED / PENDING` 支付成功与退款提交事件。
3. 候选排序、逐事件独立事务、成功重发审计、最多三个失败事件号和任务中心本轮统计不变；超过批次的候选由后续轮次继续处理。

### 326.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 事件重发联合定向测试 | `mvn -Dtest=PaymentEventRetryTaskServiceImplTest,PaymentEventRepublishAuditMapperIntegrationTest,RefundSubmitOutboxMapperIntegrationTest,PaymentEventRetryConfigurationTest,PaymentTaskCenterServiceImplTest,PaymentCompensationSchedulerTest test` | 通过 | `64` 个用例；覆盖实际 SQL 截断、默认值、上下界、自动/人工候选与任务中心联动 |
| payment-core 全量测试 | `mvn test` | 通过 | `821` 个用例，`0` failures、`0` errors、`0` skipped |
| Mapper XML 检查 | `xmllint --noout PaymentEventMapper.xml` | 通过 | XML 结构有效 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 326.3 当前判断

1. 事件积压不再被一次性加载和长时间占用单轮任务窗口，任务日志继续表达本轮实际处理结果，而不是承诺全量积压已经清空。
2. 本轮无前端、接口字段、schema 或迁移变化；本地 H2 与 Mockito 不证明生产 MySQL 执行计划、真实积压吞吐、下游受理或稳定性，`release/v1.0.0` 继续冻结。

## 327. 2026-09-11 失败退款重试有界批次 V1.0 验证

### 327.1 本轮补强

1. `RefundRetryTaskServiceImpl` 新增 `payment.refund-retry.batch-size`，默认 `100`，非正配置按 `1`、超过上限按 `1000` 执行。
2. `RefundMapper.findFailedRefundOrderIds` 接收批次参数；当前基线只选择退款单 `FAIL + terminal_at 非空 + success_at 为空`，且最新退款尝试同为 `FAIL` 并具备完成时间的候选，按 `terminal_at ASC, id ASC` 处理最早形成的失败风险，超过批次的退款保持原状态等待后续轮次。
3. 单笔事务隔离、重试额度复验、渠道提交 outbox、异常详情脱敏和任务中心本轮统计保持不变。

### 327.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 退款重试联合定向测试 | `mvn -Dtest=RefundRetryTaskServiceImplTest,RefundRetryConfigurationTest,RefundSubmitOutboxMapperIntegrationTest,RefundServiceImplTest,PaymentTaskCenterServiceImplTest,PaymentCompensationSchedulerTest test` | 通过 | `89` 个用例；覆盖实际 SQL 截断、默认值、上下界、逐笔隔离、任务中心和自动调度 |
| payment-core 全量测试 | `mvn test` | 通过 | `824` 个用例，`0` failures、`0` errors、`0` skipped |
| Mapper XML 检查 | `xmllint --noout RefundMapper.xml` | 通过 | XML 结构有效 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 327.3 当前判断

1. 失败退款积压不再一次性加载并占用完整任务租约，任务结果继续表达单轮实际处理结果，剩余失败退款由后续人工或自动轮次承接。
2. 本轮无前端、接口字段、schema 或迁移变化；本地 H2 与 Mockito 不证明生产 MySQL 执行计划、真实渠道受理、退款到账、积压吞吐或稳定性，`release/v1.0.0` 继续冻结。

## 328. 2026-09-11 异常告警派发有界批次 V1.0 验证

### 328.1 本轮补强

1. `PaymentIssueAlertDeliveryServiceImpl` 新增 `payment.issue-alert.dispatch.batch-size`，默认 `100`，非正配置按 `1`、超过上限按 `1000` 执行，人工与自动派发共用该容量。
2. `PaymentTaskCenterMapper.findPendingOutboxAlerts` 改为接收批次参数，按 `created_at ASC, id ASC` 选择最早的待确认 outbox 告警，超过批次的告警保持原状态等待后续轮次。
3. 自动任务租约、逐告警事务隔离、供应商路由与降级、成功通道防重、投递回执和任务中心本轮统计保持不变。

### 328.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 告警派发联合定向测试 | `mvn -Dtest=PaymentIssueAlertDeliveryServiceImplTest,PaymentIssueAlertNotifierConfigurationTest,PaymentTaskCenterMapperIntegrationTest,PaymentCompensationSchedulerTest test` | 通过 | `74` 个用例；覆盖实际 SQL 截断与最老优先、默认值、上下界、租约跳过和既有派发语义 |
| payment-core 全量测试 | `mvn test` | 通过 | `826` 个用例，`0` failures、`0` errors、`0` skipped |
| Mapper XML 检查 | `xmllint --noout PaymentTaskCenterMapper.xml` | 通过 | XML 结构有效 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 328.3 当前判断

1. 告警积压不再被一次性加载并持续占用单轮任务窗口，任务结果继续表达本轮实际处理结果，剩余 outbox 告警由后续人工或自动轮次承接。
2. 本轮无前端、接口字段、schema 或迁移变化；本地 H2 与 Mockito 不证明生产 MySQL 执行计划、真实供应商受理或送达、积压吞吐、资费结果或稳定性，`release/v1.0.0` 继续冻结。

## 329. 2026-09-11 异常 SLA 候选有界批次 V1.0 验证

### 329.1 本轮补强

1. `PaymentTaskCenterServiceImpl` 新增 `payment.issue-sla.batch-size`，默认 `100`，非正配置按 `1`、超过上限按 `1000` 执行。
2. `findOverdueIssueAlertCandidates` 与 `findUnacknowledgedIssueAlertEscalationCandidates` 分别按该容量查询，默认每类最多 `100` 条、单轮合计最多写入 `200` 条告警；两类容量互不占用，避免一种积压长期饿死另一种候选。
3. 首次告警按 `issue_sla.created_at ASC, issue_no ASC`、未确认升级按 `source_alert.created_at ASC, source_alert.id ASC` 选择最老候选；超过批次的数据保持原状态，由后续轮次继续处理。
4. 自动任务 `120` 秒租约、逐候选独立事务、生成键幂等、重复键 warning、失败隔离、内容截断及 `processedCount` 的既有“逾期异常存量 + 本轮未确认升级候选数”口径保持不变。

### 329.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 异常 SLA 联合定向测试 | `mvn -Dtest=PaymentTaskCenterServiceImplTest,PaymentTaskCenterMapperIntegrationTest,PaymentIssueAlertNotifierConfigurationTest,PaymentCompensationSchedulerTest test` | 通过 | `71` 个用例；覆盖两类实际 SQL 截断与最老优先、默认值、上下界、租约未取得时零候选查询及既有升级语义 |
| payment-core 全量测试 | `mvn test` | 通过 | `830` 个用例，`0` failures、`0` errors、`0` skipped |
| Mapper XML 检查 | `xmllint --noout PaymentTaskCenterMapper.xml` | 通过 | XML 结构有效 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 329.3 当前判断

1. 两类异常 SLA 候选均不再被一次性加载，超出容量的数据保留原状态并由后续人工或自动轮次承接；单轮最大写入量为 `2 x batchSize`，并非全量清空承诺。
2. 本轮无前端、接口字段、schema 或迁移变化；本地 H2 与 Mockito 不证明生产 MySQL 执行计划、真实通知送达、人员响应、积压吞吐、多实例调度或稳定性，`release/v1.0.0` 继续冻结。

## 330. 2026-09-11 异常告警回执回查批次上限 V1.0 验证

### 330.1 本轮补强

1. `PaymentIssueAlertDeliveryServiceImpl` 将 `payment.issue-alert.receipt-query.compensation.batch-size` 从仅保证正数改为统一限制在 `1..1000`，默认值保持 `100`。
2. 人工批量回查与自动回执补偿继续共用该容量；既有两段 Mapper SQL 已具备参数化 `LIMIT` 和稳定排序，本轮不改 SQL。单条指定告警回查继续按告警号查询，不受批量容量影响。
3. 候选之外的数据保持原状态等待后续轮次；逐条 `REQUIRES_NEW`、条件抢占、共享通道限流、退避重试、回执关联校验、来源告警确认和失败隔离保持不变。

### 330.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 告警回执联合定向测试 | `mvn -Dtest=PaymentIssueAlertDeliveryServiceImplTest,PaymentIssueAlertNotifierConfigurationTest,PaymentTaskCenterMapperIntegrationTest,PaymentCompensationSchedulerTest test` | 通过 | `77` 个用例；覆盖人工默认值与上下界、自动上限、任务租约、配置解析、既有 SQL 截断和回执补偿语义 |
| payment-core 全量测试 | `mvn test` | 通过 | `831` 个用例，`0` failures、`0` errors、`0` skipped |
| Mapper XML 检查 | `xmllint --noout PaymentTaskCenterMapper.xml` | 通过 | XML 结构有效，本轮未修改 Mapper SQL |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 330.3 当前判断

1. 错误部署值不能再把人工或自动批量回查扩大到超过 `1000` 条，单轮结果仍只表达本轮实际候选，剩余待确认回执由后续轮次承接。
2. 本轮无前端、接口字段、Mapper SQL、schema 或迁移变化；本地测试不证明生产 MySQL 查询计划、真实供应商受理或送达、回执乱序、积压吞吐、多实例调度或稳定性，`release/v1.0.0` 继续冻结。

## 331. 2026-09-11 支付与退款主动查单容量上限 V1.0 验证

### 331.1 本轮补强

1. `PaymentTaskCenterServiceImpl` 将支付查单的单渠道容量与全局容量统一限制在 `1..1000`，默认值保持 `20 / 100`。
2. `RefundQueryTaskServiceImpl` 对退款查单应用同一边界，默认值同样保持 `20 / 100`；人工与自动入口共用归一化后的容量。
3. 四段既有 Mapper SQL 已先通过 `ROW_NUMBER() OVER (PARTITION BY channel_code ...)` 限制单渠道候选，再通过稳定排序和 `LIMIT` 应用全局容量，本轮不改 SQL、状态条件或退避规则。

### 331.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 主动查单联合定向测试 | `mvn -Dtest=PaymentTaskCenterServiceImplTest,RefundQueryTaskServiceImplTest,PaymentTaskCenterMapperIntegrationTest,PaymentIssueAlertNotifierConfigurationTest,PaymentCompensationSchedulerTest test` | 通过 | `75` 个用例；覆盖支付与退款容量 `0 -> 1`、`1001 -> 1000`、实际 SQL 渠道公平性、人工/自动入口和任务租约 |
| payment-core 全量测试 | `mvn test` | 通过 | `833` 个用例，`0` failures、`0` errors、`0` skipped |
| Mapper XML 检查 | `xmllint --noout PaymentTaskCenterMapper.xml` | 通过 | XML 结构有效，本轮未修改 Mapper SQL |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 331.3 当前判断

1. 错误部署值不能再把支付或退款主动查单的单渠道与全局候选容量扩大到超过 `1000`；超过本轮容量的数据保持原状态并由后续轮次继续处理。
2. 本轮无前端、接口字段、Mapper SQL、schema 或迁移变化；本地 H2 与 Mockito 不证明生产 MySQL 查询计划、真实渠道查单、资金终态、渠道容量校准、积压吞吐、多实例调度或稳定性，`release/v1.0.0` 继续冻结。

## 332. 2026-09-11 支付回调 nonce 清理批次上限 V1.0 验证

### 332.1 本轮补强

1. `PaymentCallbackNonceCleanupScheduler` 将 `HSP_PAYMENT_CALLBACK_NONCE_CLEANUP_BATCH_SIZE` 的默认值保持为 `1000`，非正配置继续拒绝启动，超过 `1000` 的正值统一封顶为 `1000`。
2. `PaymentCallbackSignatureServiceImpl.cleanupExpiredNonces(int)` 在公开服务入口增加同一上限，形成调度器与服务双层保护，直接调用不能放大 Mapper 删除批次。
3. 既有 `120` 秒任务租约、周期续租、最早到期优先、双层派生表删除、唯一键防重和过期复用语义保持不变；超过单轮容量的数据保持原状态并由后续调度继续处理。

### 332.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| nonce 清理联合定向测试 | `mvn -Dtest=PaymentCallbackSecurityMapperIntegrationTest,PaymentCallbackSignatureServiceImplTest,PaymentCallbackNonceCleanupSchedulerTest,PaymentCallbackSignatureConfigurationTest,PaymentTaskLeaseServiceTest test` | 通过 | `32` 个用例；覆盖调度与服务 `1001 -> 1000`、非正值拒绝、Mapper 清理和任务租约 |
| payment-core 全量测试 | `mvn test` | 通过 | `834` 个用例，`0` failures、`0` errors、`0` skipped |
| Mapper XML 检查 | `xmllint --noout PaymentCallbackSecurityMapper.xml` | 通过 | XML 结构有效，本轮未修改 Mapper SQL |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 332.3 当前判断

1. 错误部署值与直接服务调用均不能把单轮 nonce 清理扩大到超过 `1000` 条，剩余历史数据由后续租约调度承接。
2. 本轮无前端、接口字段、Mapper SQL、schema 或迁移变化；本地 H2 与 Mockito 不证明生产 MySQL 执行计划、锁等待、数据库时钟、真实多实例、积压吞吐或稳定性。
3. 发布说明仍为候选版，正式签字审批、发布后现场核验和 master 稳定观察结论均未形成，`release/v1.0.0` 继续冻结。

## 333. 2026-09-11 渠道回调时间窗发布前一致性 V1.0 验证

### 333.1 本轮补强

1. `PaymentConfigServiceImpl` 读取 `payment.callback.nonce-ttl-seconds`，启用态渠道候选除正数校验外，新增 `nonce TTL > 2 * notifySignWindowSec` 约束，与回调验签运行时规则保持一致。
2. 新增/编辑候选在提交时校验，复核批准前通过既有候选重建再次校验；因此候选等待期间部署 TTL 变化时，不会沿用提交时的旧假设直接发布。
3. 停用态候选保留历史参数并可继续走复核，以免不兼容配置阻塞安全下线；任何后续启用或启用态回滚仍需通过当前 TTL 门禁，运行时 `PAYMENT-1034` 兜底保持不变。

### 333.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 渠道配置与回调联合定向测试 | `mvn -Dtest=PaymentChannelStatusReviewServiceImplTest,PaymentCallbackSignatureServiceImplTest,PaymentCallbackSignatureConfigurationTest test` | 通过 | `39` 个用例；覆盖提交拒绝、批准前按当前 TTL 复验、停用态历史参数保留与运行时兜底 |
| payment-core 全量测试 | `mvn test` | 通过 | `836` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 333.3 当前判断

1. 默认 `601` 秒 TTL 下，启用态渠道时间窗 `300` 秒可发布，`301` 秒会在候选提交或批准阶段拒绝；自定义 TTL 时按同一严格不等式计算，不硬编码页面上限。
2. 本轮无前端代码、接口字段、Mapper SQL、schema 或迁移变化；管理端沿用既有服务端错误展示，本地测试不证明生产配置中心一致、真实审批完成、并发发布、渠道回调或多实例防重。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 334. 2026-09-11 渠道启用与回滚待办可执行性 V1.0 验证

### 334.1 本轮补强

1. `submitChannelStatusReview` 在启用停用渠道前校验当前 `notifySignWindowSec` 受当前 nonce TTL 完整覆盖，不兼容时不创建启用待办。
2. `submitChannelRollbackReview` 对目标历史版本执行同一状态感知校验：启用态目标必须兼容，停用态目标允许保留历史参数以支持安全下线。
3. 校验逻辑收敛为配置服务内部共享方法，新增/编辑候选提交和批准前重建继续复用；批准阶段仍按届时 TTL 复验，避免提交通过后配置变化造成失效发布。

### 334.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 渠道配置与回调联合定向测试 | `mvn -Dtest=PaymentChannelStatusReviewServiceImplTest,PaymentCallbackSignatureServiceImplTest,PaymentCallbackSignatureConfigurationTest test` | 通过 | `41` 个用例；覆盖启用提交拒绝、启用态回滚拒绝、停用态回滚允许及批准前复验 |
| payment-core 全量测试 | `mvn test` | 通过 | `838` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 334.3 当前判断

1. 启用渠道的四类来源均受控：新增、编辑、停用后重启用和历史回滚在提交或批准阶段不能绕过当前 TTL 与时间窗契约；停用路径不被安全门禁反向阻塞。
2. 本轮无前端代码、配置键、接口字段、Mapper SQL、schema 或迁移变化；本地测试不证明真实审批、配置发布、渠道回调、多实例防重或生产稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 335. 2026-09-11 渠道单日限额数据库容量一致性 V1.0 验证

### 335.1 本轮补强

1. `PaymentConfigServiceImpl` 将渠道 `dailyLimit` 与候选、运行态、版本表共同使用的 `DECIMAL(18,2)` 容量对齐，最大值为 `9999999999999999.99`。
2. `0` 继续表示既有零额度语义；有效小数位超过 `2` 或超过最大值时在服务端拒绝，不静默舍入或截断，尾随零不改变数值是否合法。
3. 候选提交与批准前重建复用相同校验，因此提交后的历史或恶意候选也不能写入运行态或完成复核。

### 335.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 渠道限额定向测试 | `mvn -Dtest=PaymentChannelStatusReviewServiceImplTest,PaymentConfigChannelReviewMapperIntegrationTest test` | 通过 | `23` 个用例；覆盖 `0`、最大值、三位小数、超上限及批准前复验 |
| payment-core 全量测试 | `mvn test` | 通过 | `840` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 335.3 当前判断

1. 渠道限额不再把列容量错误推迟到数据库处理，提交与批准两层均执行确定性拒绝；本轮不改变既有零额度业务语义。
2. 本轮无前端代码、接口字段、Mapper SQL、schema 或迁移变化；本地 H2 与 Mockito 不证明真实审批、生产 MySQL 严格模式、配置发布、渠道资金容量或稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 336. 2026-09-11 退款申请金额数据库容量一致性 V1.0 验证

### 336.1 本轮补强

1. `RefundServiceImpl` 将退款申请 `refundAmount` 与 `t_refund_order.refund_amount DECIMAL(18,2)` 对齐，继续要求金额大于 `0`，最大值为 `9999999999999999.99`。
2. 有效小数位超过 `2` 或超过最大值时在任何 Mapper 访问前拒绝，不静默舍入或截断；`9999999999999999.990` 经规范化后仍按最大合法值处理。
3. 支付单锁、支付成功校验、累计退款额度预占、申请操作日志和后续 maker-checker 状态流转均沿用既有路径。

### 336.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 退款申请联合定向测试 | `mvn -Dtest=RefundServiceImplTest,RefundApplicationConcurrencyIntegrationTest test` | 通过 | `34` 个用例；覆盖三位有效小数、超上限、最大合法值、尾随零及并发额度预占 |
| payment-core 全量测试 | `mvn test` | 通过 | `842` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 336.3 当前判断

1. 新退款申请不会再把列精度与容量错误推迟给数据库处理，且未改变累计退款不得超过实付金额的资金约束。
2. 本轮无前端代码、接口字段、Mapper SQL、schema 或迁移变化；本地 H2 与 Mockito 不证明真实审批、生产 MySQL 严格模式、真实渠道受理、退款到账、资金核对或稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 337. 2026-09-11 退款申请文本字段持久化边界 V1.0 验证

### 337.1 本轮补强

1. `RefundServiceImpl` 在退款申请访问 Mapper 前规范化 `paymentOrderId / refundMethod / refundReason`，分别限制为 `64 / 32 / 255` 个字符，与 `t_refund_order` 列宽一致。
2. 原支付单号必填；退款方式缺省时继续回填“原路退款”；退款原因允许缺省但统一写为空串，避免 MyBatis 显式绑定 `NULL` 触发 `NOT NULL` 列异常。
3. 三类原始输入在裁剪前检查控制字符，末尾换行也会拒绝；正常首尾空白裁剪后再进入支付单锁定、额度预占和持久化流程。

### 337.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 退款申请联合定向测试 | `mvn -Dtest=RefundServiceImplTest,RefundApplicationConcurrencyIntegrationTest test` | 通过 | `36` 个用例；覆盖空原因、字段裁剪、三类超长值、三类控制字符和并发额度预占 |
| payment-core 全量测试 | `mvn test` | 通过 | `844` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 337.3 当前判断

1. 退款申请不再把三类文本列宽或空原因的持久化失败推迟给数据库，非法请求也不会提前访问 Mapper。
2. 本轮不限制既有退款方式集合，无前端代码、接口字段、Mapper SQL、schema 或迁移变化；本地测试不证明线下打款或退转付已具备真实下游能力、真实审批、渠道受理、退款到账或稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 338. 2026-09-11 退款动作字段持久化边界 V1.0 验证

### 338.1 本轮补强

1. `RefundServiceImpl` 将退款详情、审批、失败重试、人工主动查单和任务重试使用的退款单号统一限制为 `64` 个字符，并在裁剪前拒绝控制字符。
2. 审批与重试的可选操作备注在状态 CAS 前完成规范化，裁剪后最长 `255` 字符；空备注继续使用既有动作默认文案。
3. 合法退款单号与备注会回写规范化值，非法退款单号不访问 Mapper，非法备注不执行状态更新；状态机、默认备注与事件派发均未改变。

### 338.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 退款动作联合定向测试 | `mvn -Dtest=RefundServiceImplTest,RefundApplicationConcurrencyIntegrationTest test` | 通过 | `38` 个用例；覆盖退款单号超长与控制字符、备注裁剪、备注超长与控制字符、更新前拒绝及并发额度预占 |
| payment-core 全量测试 | `mvn test` | 通过 | `846` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 338.3 当前判断

1. 退款动作入口不会再把退款单号或操作备注的列宽、控制字符问题推迟给数据库，非法备注也不会先改变退款状态。
2. 本轮无前端代码、接口字段、Mapper SQL、schema 或迁移变化；本地测试不证明真实身份、审批、渠道受理、退款到账、资金核对或生产稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 339. 2026-09-11 退款查询字段边界 V1.0 验证

### 339.1 本轮补强

1. `RefundServiceImpl` 将退款列表与 CSV 导出的 `refundOrderId / paymentOrderId / refundStatus / refundMethod` 查询条件统一限制为 `64 / 64 / 32 / 32` 个字符。
2. 四类原始查询值在裁剪前拒绝控制字符；合法空白继续裁剪，空单号筛选转为 `NULL`，空状态与方式继续使用“全部”。
3. 列表和导出复用同一规范化方法，非法筛选在任何 Mapper 访问前拒绝；分页与导出行数保护保持不变。

### 339.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 退款查询联合定向测试 | `mvn -Dtest=RefundServiceImplTest,RefundApplicationConcurrencyIntegrationTest test` | 通过 | `39` 个用例；覆盖四类查询字段超长与控制字符、合法裁剪、列表和导出共享门禁 |
| payment-core 全量测试 | `mvn test` | 通过 | `847` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 339.3 当前判断

1. 退款列表和导出不会再把超出目标列宽或含控制字符的查询条件下推数据库，空筛选和分页语义未改变。
2. 本轮无前端代码、接口字段、Mapper SQL、schema 或迁移变化；本地测试不证明生产 MySQL 查询计划、真实数据完整性、渠道受理、退款到账或生产稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 340. 2026-09-11 退款回调控制字符裁剪绕过修复 V1.0 验证

### 340.1 本轮补强

1. `RefundServiceImpl` 对回调 `channel / refundOrderId / channelRefundNo / refundStatus` 先检查原始控制字符，再裁剪并校验 `64 / 64 / 128 / 32` 字符上限。
2. 末尾换行不再被裁剪后当作合法输入；合法首尾空白、渠道小写化和状态大写化保持不变，规范化完成后才进入退款回调验签。
3. 四类非法输入均复用 `REFUND_CALLBACK_REQUEST_INVALID`，并在验签服务与 Mapper 访问前失败，不改变退款状态或审计记录。

### 340.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 退款回调联合定向测试 | `mvn -Dtest=RefundServiceImplTest,RefundApplicationConcurrencyIntegrationTest test` | 通过 | `40` 个用例；覆盖四类末尾控制字符、验签前拒绝、合法空白规范化及退款主链路 |
| payment-core 全量测试 | `mvn test` | 通过 | `848` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 340.3 当前判断

1. 退款回调不再存在“先裁剪、后检查”导致的末尾控制字符绕过，非法请求不能进入验签或状态处理链路。
2. 本轮无前端代码、接口字段、签名算法、Mapper SQL、schema 或迁移变化；本地 Mockito 不证明真实渠道签名、回调送达、退款到账或生产稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 341. 2026-09-11 支付回调渠道控制字符裁剪绕过修复 V1.0 验证

### 341.1 本轮补强

1. `PaymentServiceImpl.callback` 对路径 `channel` 先检查原始控制字符，再裁剪合法空白、转为小写并校验规范化后的 `64` 字符上限。
2. 末尾换行不再被裁剪后作为合法渠道进入验签；支付单号、交易状态和渠道交易号继续使用既有原值控制字符与长度门禁。
3. 非法路径渠道在验签服务和 Mapper 访问前抛出既有 `IllegalArgumentException`，不改变支付状态或生成事件事实。

### 341.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付服务定向测试 | `mvn -Dtest=PaymentServiceImplTest test` | 通过 | `70` 个用例；新增路径渠道末尾换行拒绝，并回归请求体边界与支付主链路 |
| payment-core 全量测试 | `mvn test` | 通过 | `848` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 341.3 当前判断

1. 支付回调路径渠道不再存在“先裁剪、后检查”导致的末尾控制字符绕过，请求体三个关键字段的既有门禁保持不变。
2. 本轮无前端代码、接口字段、错误码、签名算法、Mapper SQL、schema 或迁移变化；本地 Mockito 不证明真实渠道签名、回调送达、支付入账或生产稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 342. 2026-09-11 支付请求控制字符裁剪绕过修复 V1.0 验证

### 342.1 本轮补强

1. `PaymentServiceImpl` 的共享请求字段门禁改为先检查原始控制字符，再执行既有空值判断、裁剪和长度校验。
2. 详情支付单号、收银台预付单号、主动查单与关闭支付单号，以及预付创建的订单号、支付场景、客户名称和收银台标题均覆盖末尾换行场景。
3. 非法输入统一使用 `PAYMENT_REQUEST_INVALID`，并在 Mapper、渠道查询和状态更新前失败；合法首尾空白及可选空值语义不变。

### 342.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付服务定向测试 | `mvn -Dtest=PaymentServiceImplTest test` | 通过 | `71` 个用例；覆盖八类末尾控制字符及既有支付主链路 |
| payment-core 全量测试 | `mvn test` | 通过 | `849` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 342.3 当前判断

1. 共享支付请求门禁不再存在“先裁剪、后检查”导致的末尾控制字符绕过，非法输入不能触达数据访问或渠道查询链路。
2. 本轮无前端代码、接口字段、错误码、Mapper SQL、schema 或迁移变化；本地测试不证明真实身份、渠道受理、支付入账或生产稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 343. 2026-09-11 支付查询字段边界 V1.0 验证

### 343.1 本轮补强

1. `PaymentServiceImpl` 将支付列表与 CSV 导出的 `paymentOrderId / orderNo / paymentMethod / status` 查询条件统一限制为 `64 / 64 / 32 / 32` 个字符。
2. 四类原始查询值在裁剪前拒绝控制字符；合法空白继续裁剪，空单号筛选转为 `NULL`，空支付方式和状态继续使用“全部”。
3. 列表和导出复用同一规范化方法，非法筛选统一返回 `PAYMENT_REQUEST_INVALID` 并在任何 Mapper 访问前拒绝；分页与导出行数保护保持不变。

### 343.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付服务定向测试 | `mvn -Dtest=PaymentServiceImplTest test` | 通过 | `72` 个用例；覆盖四类查询字段超长与控制字符、合法裁剪、列表和导出共享门禁 |
| payment-core 全量测试 | `mvn test` | 通过 | `850` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 343.3 当前判断

1. 支付列表和导出不会再把超出目标列宽或含控制字符的查询条件下推数据库，空筛选和分页语义未改变。
2. 本轮无前端代码、接口字段、错误码、Mapper SQL、schema 或迁移变化；本地测试不证明生产 MySQL 查询计划、真实数据完整性、渠道受理、支付入账或生产稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 344. 2026-09-11 支付提交控制字符裁剪绕过修复 V1.0 验证

### 344.1 本轮补强

1. `PaymentServiceImpl` 对十类一般提交字段先检查原始控制字符，再执行既有裁剪、长度和终端枚举校验，末尾换行不再被清理后绕过。
2. 显式幂等键增加同样的原值检查，并保留独立的 `PAYMENT_SUBMIT_IDEMPOTENCY_KEY_INVALID`；一般字段继续使用 `PAYMENT_SUBMIT_REQUEST_INVALID`。
3. 非法输入在首次预付单查询、路由、风控、共享限流和渠道提交前失败；合法首尾空白、终端别名、缺省值和自动组合幂等键保持不变。

### 344.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付服务定向测试 | `mvn -Dtest=PaymentServiceImplTest test` | 通过 | `73` 个用例；覆盖十类一般字段及显式幂等键末尾控制字符，并回归合法提交链路 |
| payment-core 全量测试 | `mvn test` | 通过 | `851` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 344.3 当前判断

1. 支付提交入口已兑现“任意位置控制字符均拒绝”的既有交付契约，尾部或纯控制字符不能再被裁剪、判空逻辑掩盖。
2. 本轮无前端代码、接口字段、错误码、Mapper SQL、schema 或迁移变化；本地测试不证明可信身份、真实网关鉴权、渠道受理、支付入账或生产稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 345. 2026-09-11 死信人工动作控制字符裁剪绕过修复 V1.0 验证

### 345.1 本轮补强

1. `PaymentDeadLetterTaskServiceImpl` 对任务号、处置人和处置说明先检查原始控制字符，再执行既有裁剪与 `64 / 64 / 512` 字符容量校验。
2. 允许重放、人工结案和执行重放均复用该门禁；末尾换行不再进入状态更新参数、审计说明或消息代理调用。
3. 合法首尾空白、死信状态机、原消费者路由、发布确认、失败脱敏和敏感快照遮蔽语义不变。

### 345.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 死信服务与控制器联合定向测试 | `mvn -Dtest=PaymentDeadLetterTaskServiceImplTest,PaymentDeadLetterTaskControllerTest test` | 通过 | `16` 个用例；覆盖三类末尾控制字符、Mapper/RabbitMQ 零交互及既有重放主链路 |
| payment-core 全量测试 | `mvn test` | 通过 | `852` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 345.3 当前判断

1. 死信人工动作不再存在“先裁剪、后检查”导致的末尾控制字符绕过，非法字段不能改变任务状态或触发消息重放。
2. 本轮无前端代码、接口字段、错误码、Mapper SQL、schema 或迁移变化；本地测试不证明可信操作身份、真实审批、RabbitMQ 生产送达、下游消费结果或生产稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 346. 2026-09-11 死信列表查询字段边界 V1.0 验证

### 346.1 本轮补强

1. `PaymentDeadLetterTaskServiceImpl` 将任务状态、目标系统和消息 ID 查询条件统一限制为 `32 / 64 / 128` 个字符。
2. 三类原始值在裁剪前拒绝控制字符；合法空白继续裁剪，空条件继续转为 `NULL`，分页边界不变。
3. 非法筛选在任何死信 Mapper 访问前拒绝；合法列表仍返回脱敏副本，不修改 Mapper 原始实体。

### 346.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 死信服务与控制器联合定向测试 | `mvn -Dtest=PaymentDeadLetterTaskServiceImplTest,PaymentDeadLetterTaskControllerTest test` | 通过 | `17` 个用例；覆盖三类筛选字段超长与控制字符、Mapper 零交互及既有列表/动作流程 |
| payment-core 全量测试 | `mvn test` | 通过 | `853` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 346.3 当前判断

1. 死信列表不会再把超出目标列宽或含控制字符的查询条件下推数据库，空筛选、分页和脱敏语义未改变。
2. 本轮无前端代码、接口字段、错误码、Mapper SQL、schema 或迁移变化；本地测试不证明生产 MySQL 查询计划、真实消息积压、RabbitMQ 投递或生产稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 347. 2026-09-11 支付事件查询与人工重发字段边界 V1.0 验证

### 347.1 本轮补强

1. `PaymentEventServiceImpl` 将支付单号、事件类型、发布状态、下游系统和事件主题查询条件统一限制为 `64 / 64 / 32 / 256 / 128` 个字符。
2. 五类筛选与重发事件号均在裁剪前拒绝控制字符；重发事件号继续必填且最长 `64` 个字符。
3. 非法筛选不访问 Mapper，非法重发事件号不调用派发服务或审计 Mapper；合法空白裁剪、默认筛选、分页、排序、导出与脱敏保持不变。

### 347.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付事件服务与控制器联合定向测试 | `mvn -Dtest=PaymentEventServiceImplTest,PaymentEventControllerTest test` | 通过 | `15` 个用例；覆盖五类筛选超长与控制字符、重发事件号超长与控制字符、下游零交互及既有列表/总览/导出/重发流程 |
| payment-core 全量测试 | `mvn test` | 通过 | `856` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 347.3 当前判断

1. 支付事件查询不再把超出目标列宽或含控制字符的条件下推数据库，人工重发不再接受裁剪后看似合法的控制字符输入。
2. 本轮无前端代码、接口字段、错误码、Mapper SQL、schema 或迁移变化；本地测试不证明生产 MySQL 查询计划、真实事件积压、下游投递或生产稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 348. 2026-09-12 支付异常与告警查询字段边界 V1.0 验证

### 348.1 本轮补强

1. `PaymentIssueServiceImpl` 将异常列表/责任汇总的 `paymentOrderId / orderNo / issueType / severity / channelCode / paymentMethod` 分别限制为 `64 / 64 / 64 / 32 / 64 / 32` 个字符。
2. 告警明细列表/CSV 导出的 `alertNo / issueNo / paymentOrderId / alertChannel / alertStatus / ackStatus / providerDeliveryStatus / receiptQueryState` 分别限制为 `64 / 128 / 64 / 32 / 32 / 32 / 64 / 32` 个字符。
3. 所有原始值在裁剪前拒绝控制字符；回执回查状态在原值门禁后再执行大写化和枚举校验，非法条件不访问 Mapper。

### 348.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付异常服务与控制器联合定向测试 | `mvn -Dtest=PaymentIssueServiceImplTest,PaymentIssueControllerTest test` | 通过 | `32` 个用例；新增 `4` 个用例覆盖 `28` 类超长或控制字符输入、Mapper 零交互及既有列表/汇总/告警/导出流程 |
| payment-core 全量测试 | `mvn test` | 通过 | `860` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 348.3 当前判断

1. 异常中心的四类查询出口不再将超出目标列宽或含控制字符的条件下推数据库，末尾换行不能再被裁剪后变成合法回执回查状态。
2. 本轮无前端代码、接口字段、错误码、Mapper SQL、schema 或迁移变化；本地测试不证明生产 MySQL 查询计划、真实异常积压、供应商投递/回执或生产稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 349. 2026-09-12 服务者结算查询字段边界 V1.0 验证

### 349.1 本轮补强

1. `SettlementServiceImpl` 将 `settlementOrderId / workerKeyword / settlementStatus / payoutStatus` 分别限制为 `64 / 128 / 32 / 32` 个字符。
2. 四类原始值在裁剪前拒绝控制字符，超长或含控制字符的查询不访问结算 Mapper。
3. 合法首尾空白、默认筛选、分页 `1..100`、金额汇总、服务者姓名脱敏与 CSV `1000` 行上限保持不变。

### 349.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 服务者结算服务与控制器联合定向测试 | `mvn -Dtest=SettlementServiceImplTest,SettlementControllerTest test` | 通过 | `9` 个用例；覆盖四类筛选超长与控制字符、Mapper 零交互及既有列表/总览/导出流程 |
| payment-core 全量测试 | `mvn test` | 通过 | `862` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 349.3 当前判断

1. 服务者结算的三类查询出口不再把超出结算表列宽或含控制字符的条件下推数据库。
2. 本轮无前端代码、接口字段、错误码、Mapper SQL、schema 或迁移变化；本地测试不证明生产结算金额、真实出款、服务者收款或生产稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 350. 2026-09-12 账单查询字段边界 V1.0 验证

### 350.1 本轮补强

1. `BillServiceImpl` 将 `billNo / orderNo / customerName / billStatus` 分别限制为 `64 / 64 / 128 / 32` 个字符。
2. 四类原始值在裁剪前拒绝控制字符，超长或含控制字符的查询不访问账单 Mapper。
3. 合法首尾空白、默认筛选、排序、分页 `1..100`、金额汇总、客户姓名脱敏与 CSV `1000` 行上限保持不变。

### 350.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 账单服务与控制器联合定向测试 | `mvn -Dtest=BillServiceImplTest,BillControllerTest test` | 通过 | `9` 个用例；覆盖四类筛选超长与控制字符、Mapper 零交互及既有列表/总览/导出流程 |
| payment-core 全量测试 | `mvn test` | 通过 | `864` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 350.3 当前判断

1. 账单中心的三类查询出口不再把超出账单表列宽或含控制字符的条件下推数据库。
2. 本轮无前端代码、端点、DTO、错误码、Mapper SQL、schema 或迁移变化；本地测试不证明生产账单金额、真实收款、账务一致性或生产稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 351. 2026-09-12 支付流水查询字段边界 V1.0 验证

### 351.1 本轮补强

1. `PaymentFlowServiceImpl` 将 `paymentOrderId / orderNo / flowType / channelCode / terminal` 分别限制为 `64 / 64 / 32 / 64 / 32` 个字符。
2. 联合投影的 `businessStatus` 按最大来源列宽 `128` 收敛，检索摘要和请求/响应报文的 `keyword` 按最大报文列宽 `2048` 收敛。
3. 七类原始值在裁剪前拒绝控制字符，非法查询不访问流水 Mapper；合法空白、默认筛选、排序、分页、敏感字段脱敏和 CSV 上限保持不变。

### 351.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付流水服务与控制器联合定向测试 | `mvn -Dtest=PaymentFlowServiceImplTest,PaymentFlowControllerTest test` | 通过 | `7` 个用例；覆盖七类筛选超长与控制字符、Mapper 零交互及既有列表/导出流程 |
| payment-core 全量测试 | `mvn test` | 通过 | `866` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 351.3 当前判断

1. 支付流水的列表和导出不再把超出联合投影容量或含控制字符的条件下推数据库。
2. 本轮无前端代码、端点、DTO、错误码、Mapper SQL、schema 或迁移变化；本地测试不证明生产查询计划、渠道报文真实性、支付入账、账务一致性或生产稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 352. 2026-09-12 支付路由执行查询字段边界 V1.0 验证

### 352.1 本轮补强

1. `PaymentRouteExecutionServiceImpl` 将 `paymentOrderId / orderNo / routeRule / channelCode / paymentMethod / terminal / routeResult` 分别限制为 `64 / 64 / 128 / 64 / 32 / 32 / 128` 个字符。
2. 七类原始值在裁剪前拒绝控制字符，超长或含控制字符的查询不访问路由执行 Mapper。
3. 合法空白、默认筛选、排序、分页、最新支付尝试关联、路由健康概览、敏感字段脱敏和 CSV 上限保持不变。

### 352.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 路由执行服务与控制器联合定向测试 | `mvn -Dtest=PaymentRouteExecutionServiceImplTest,PaymentRouteExecutionControllerTest test` | 通过 | `9` 个用例；覆盖七类筛选超长与控制字符、Mapper 零交互及既有列表/总览/导出流程 |
| payment-core 全量测试 | `mvn test` | 通过 | `868` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 352.3 当前判断

1. 路由执行的三类查询出口不再把超出目标列宽或含控制字符的条件下推数据库。
2. 本轮无前端代码、端点、DTO、错误码、Mapper SQL、schema 或迁移变化；本地测试不证明真实路由决策、渠道健康、支付受理、资金结果或生产稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 353. 2026-09-12 支付处理日志查询字段边界 V1.0 验证

### 353.1 本轮补强

1. `PaymentLogServiceImpl` 将 `paymentOrderId / orderNo / processStage / logLevel / source` 分别限制为 `64 / 64 / 32 / 16 / 64` 个字符。
2. `keyword` 检索 `utf8mb4 TEXT` 消息列，按最坏四字节字符容量 `16383` 收敛；六类原始值在裁剪前拒绝控制字符。
3. 非法查询不访问日志 Mapper；合法空白、默认筛选、排序、分页、概览统计和 CSV 上限保持不变。

### 353.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付日志服务与控制器联合定向测试 | `mvn -Dtest=PaymentLogServiceImplTest,PaymentLogControllerTest test` | 通过 | `8` 个用例；覆盖六类筛选超长与控制字符、Mapper 零交互及既有列表/总览/导出流程 |
| payment-core 全量测试 | `mvn test` | 通过 | `870` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 353.3 当前判断

1. 支付处理日志的三类查询出口不再把超出目标容量或含控制字符的条件下推数据库。
2. 本轮无前端代码、端点、DTO、错误码、Mapper SQL、schema 或迁移变化；本地测试不证明生产日志完整性、外部检索能力、支付事实、账务一致性或生产稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 354. 2026-09-12 收银台会话查询字段边界 V1.0 验证

### 354.1 本轮补强

1. `CashierSessionServiceImpl` 将 `sessionNo / paymentOrderId / orderNo / customerName / terminal / sessionStatus` 分别限制为 `64 / 64 / 64 / 128 / 32 / 32` 个字符。
2. 六类原始值在裁剪前拒绝控制字符，超长或含控制字符的查询不访问收银台 Mapper。
3. 合法空白、默认筛选、排序、分页、失效状态派生、临近失效概览、客户姓名脱敏和 CSV 上限保持不变。

### 354.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 收银台服务与控制器联合定向测试 | `mvn -Dtest=CashierSessionServiceImplTest,CashierSessionControllerTest test` | 通过 | `9` 个用例；覆盖六类筛选超长与控制字符、Mapper 零交互及既有列表/总览/导出流程 |
| payment-core 全量测试 | `mvn test` | 通过 | `872` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 354.3 当前判断

1. 收银台会话的三类查询出口不再把超出预付单列宽或含控制字符的条件下推数据库。
2. 本轮无前端代码、端点、DTO、错误码、Mapper SQL、schema 或迁移变化；本地测试不证明真实会话有效性、渠道受理、支付入账、账务一致性或生产稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 355. 2026-09-12 订单查询字段边界 V1.0 验证

### 355.1 本轮补强

1. `OrderServiceImpl` 将 `orderNo / serviceType / orderStatus` 分别限制为 `64 / 64 / 32` 个字符。
2. 三类原始值在裁剪前拒绝控制字符，超长或含控制字符的查询不访问订单 Mapper。
3. 合法空白、空条件、默认筛选、分页、客户与服务者姓名脱敏和 CSV 上限保持不变。

### 355.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 订单服务与控制器联合定向测试 | `mvn -Dtest=OrderServiceImplTest,OrderControllerTest test` | 通过 | `7` 个用例；覆盖三类筛选超长与控制字符、Mapper 零交互及既有列表/导出流程 |
| payment-core 全量测试 | `mvn test` | 通过 | `874` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 355.3 当前判断

1. 订单中心的列表与导出两类查询出口不再把超出订单列宽或含控制字符的条件下推数据库。
2. 本轮无前端代码、端点、DTO、错误码、Mapper SQL、schema 或迁移变化；本地测试不证明真实订单履约、渠道受理、支付入账、账务一致性或生产稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 356. 2026-09-12 支付请求查询字段边界 V1.0 验证

### 356.1 本轮补强

1. `PaymentRequestServiceImpl` 将 `requestNo / paymentOrderId / orderNo / channelCode / terminal / clientIp / requestStatus` 分别限制为 `64 / 64 / 64 / 64 / 32 / 64 / 32` 个字符。
2. 七类原始值在裁剪前拒绝控制字符，超长或含控制字符的查询不访问支付请求 Mapper；请求状态中文别名继续在校验后映射。
3. 合法空白、默认筛选、排序、分页、请求总览、客户端 IP/幂等键/请求响应报文脱敏和 CSV 上限保持不变。

### 356.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付请求服务与控制器联合定向测试 | `mvn -Dtest=PaymentRequestServiceImplTest,PaymentRequestControllerTest test` | 通过 | `9` 个用例；覆盖七类筛选超长与控制字符、Mapper 零交互及既有列表/总览/导出流程 |
| payment-core 全量测试 | `mvn test` | 通过 | `876` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 356.3 当前判断

1. 支付请求的三类查询出口不再把超出查询列宽或含控制字符的条件下推数据库。
2. 本轮无前端代码、端点、DTO、错误码、Mapper SQL、schema 或迁移变化；本地测试不证明真实渠道请求、渠道受理、支付入账、账务一致性或生产稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 357. 2026-09-12 支付记录查询字段边界 V1.0 验证

### 357.1 本轮补强

1. `PaymentRecordServiceImpl` 将列表 `recordType / userId / businessOrderNo / paymentType / paymentStatus / paymentChannel` 分别限制为 `16 / 128 / 64 / 32 / 32 / 64` 个字符。
2. 详情 `paymentOrderId` 要求非空且最长 `64` 个字符；七类原始值在裁剪前拒绝控制字符，非法查询不访问支付记录或支付轨迹 Mapper。
3. 详情主记录、路由、通知和事件轨迹统一使用裁剪后的支付单号；列表兼容、分页、用户引用脱敏、详情报文脱敏和成功退款聚合保持不变。

### 357.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付记录联合定向测试 | `mvn -Dtest=PaymentRecordServiceListTest,PaymentRecordServiceImplTest,PaymentRecordControllerTest,PaymentRecordRefundAggregationMapperIntegrationTest test` | 通过 | `11` 个用例；覆盖六类列表筛选、详情标识、Mapper 零交互、退款聚合与详情轨迹装配 |
| payment-core 全量测试 | `mvn test` | 通过 | `879` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 357.3 当前判断

1. 支付记录列表与详情不再把超出边界或含控制字符的查询条件下推数据库，详情轨迹查询键保持一致。
2. 本轮无前端代码、端点、DTO、Mapper SQL、schema 或迁移变化；本地测试不证明真实支付、退款到账、渠道受理、账务一致性或生产稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 358. 2026-09-12 任务运行日志查询字段边界 V1.0 验证

### 358.1 本轮补强

1. `PaymentTaskCenterServiceImpl` 将 `taskCode / runMode / taskStatus / severityLevel` 分别限制为 `64 / 32 / 32 / 32` 个字符。
2. 四类原始值在裁剪前拒绝控制字符，超长或含控制字符的列表与 CSV 查询不访问任务中心 Mapper。
3. 合法空白、空条件、“全部”筛选、分页、严重等级与升级信息派生、任务执行链路和 CSV 上限保持不变。

### 358.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 任务中心服务与控制器联合定向测试 | `mvn -Dtest=PaymentTaskCenterServiceImplTest,PaymentTaskCenterControllerTest test` | 通过 | `45` 个用例；覆盖四类筛选超长与控制字符、Mapper 零交互及既有列表/导出/任务执行流程 |
| payment-core 全量测试 | `mvn test` | 通过 | `881` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 358.3 当前判断

1. 任务运行日志列表与导出不再把超出目标列宽或含控制字符的筛选下推数据库。
2. 本轮无前端代码、端点、DTO、错误码、Mapper SQL、schema 或迁移变化；本地测试不证明生产调度、真实任务执行、补偿结果、告警送达或生产稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 359. 2026-09-12 支付事件派发标识边界 V1.0 验证

### 359.1 本轮补强

1. `PaymentEventDispatchServiceImpl` 在 `publishPaymentSuccess / publishRefundSubmit / republish` 三个公开入口统一校验并裁剪事件号。
2. 支付成功入口同步校验支付单号，退款提交入口同步校验退款单号；事件号、支付单号和退款单号均要求非空且最长 `64` 个字符，原始值在裁剪前拒绝 ISO 控制字符。
3. 非法标识在事件 Mapper、支付/退款 Mapper、渠道适配器及下游调用前拒绝；合法裁剪值继续用于事件锁定、状态更新、退款提交和下游投递。

### 359.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付事件派发服务定向测试 | `mvn -Dtest=PaymentEventDispatchServiceImplTest test` | 通过 | `42` 个用例；覆盖事件号裁剪、空白/超长/控制字符，以及支付单号和退款单号边界与 Mapper 零交互 |
| payment-core 全量测试 | `mvn test` | 通过 | `882` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 359.3 当前判断

1. 共享事件派发边界不再依赖上层调用方预先清洗标识，三类公开入口使用与数据库列宽一致的规范化键。
2. 本轮无前端代码、端点、DTO、错误码、Mapper SQL、schema 或迁移变化；本地测试不证明真实渠道受理、下游投递、资金结果、账务一致性或生产稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 360. 2026-09-12 运营配置快照请求维度边界 V1.0 验证

### 360.1 本轮补强

1. `PaymentOpsConfigServiceImpl` 在构造有效快照 URI 前统一规范化 `businessCode / payType / terminalType`，三类请求维度均要求非空且最长 `64` 个字符。
2. 三类原始值在裁剪前拒绝 ISO 控制字符；非法请求继续按既有客户端契约返回 `null`，且不发起 HTTP，由支付主链路按缺省字段场景决定是否抛出运营配置快照错误。
3. 合法首尾空白、响应业务成功码、三维绑定、非负版本、主备渠道、路由策略和系统控制项完整性门禁保持不变。

### 360.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 运营配置客户端定向测试 | `mvn -Dtest=PaymentOpsConfigServiceImplTest test` | 通过 | `9` 个用例；覆盖三类请求维度的控制字符、超长、HTTP 零交互，以及合法快照和异常响应 |
| payment-core 全量测试 | `mvn test` | 通过 | `883` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 360.3 当前判断

1. payment-core 不再把污染或超出远端 schema 容量的配置快照维度发往 `ops-config-system`，共享客户端保持失败关闭。
2. 本轮无前端代码、端点、DTO、错误码、Mapper SQL、schema 或迁移变化；本地 mock 不证明真实 ops-config 发布、跨进程网络、配置生效、资金结果或生产稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 361. 2026-09-12 风控评估请求边界 V1.0 验证

### 361.1 本轮补强

1. `PaymentRiskControlServiceImpl` 在 HTTP 调用前校验请求非空，并将业务号、来源系统、风控场景、支付场景、支付方式、渠道、商户、终端和客户端 IP 设为必填；设备号和付款手机号保持可选。
2. 十一类字符串分别限制为 `64 / 64 / 64 / 32 / 32 / 64 / 128 / 32 / 64 / 128 / 32` 个字符，原始值在裁剪前拒绝 ISO 控制字符；金额要求大于 `0`、小数位不超过 `2` 且不超过 `9999999999999999.99`。
3. 合法请求使用新建的规范化副本出站，调用方原 DTO 不被修改；非法请求沿用 `PAYMENT_RISK_SERVICE_UNAVAILABLE` 并保证 `RestTemplate` 零交互。禁用态本地放行和启用态缺失地址的既有优先级保持不变。

### 361.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 风控客户端定向测试 | `mvn -Dtest=PaymentRiskControlServiceImplTest test` | 通过 | `19` 个用例；覆盖请求空值、必填空白、控制字符、超长、金额空值/零值/精度/上限、HTTP 零交互及规范化副本 |
| payment-core 全量测试 | `mvn test` | 通过 | `885` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 361.3 当前判断

1. 共享风控客户端不再依赖支付主流程预先清洗请求，污染或超出跨系统容量的评估上下文不会发往 `risk-control-system`。
2. 本轮无前端代码、端点、DTO、Mapper SQL、schema 或迁移变化；本地 mock 不证明真实风控策略、跨进程网络、人工复核、渠道受理、资金结果或生产稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 362. 2026-09-12 gateway 支付提交请求边界 V1.0 验证

### 362.1 本轮补强

1. `GatewayAccessPaymentChannelSubmitAdapter` 对齐 gateway-access 支付提交契约，在 HTTP 前校验十个必填字符串、两个可选字符串和金额。
2. 支付单号、预付单号、订单号、支付场景、客户名称、支付方式、来源应用、请求渠道、真实渠道、终端、客户端 IP 和幂等键分别限制为 `64 / 64 / 64 / 32 / 128 / 32 / 64 / 64 / 64 / 32 / 64 / 128` 个字符，原始值在裁剪前拒绝 ISO 控制字符。
3. 金额必须大于 `0`、有效小数位不超过 `2` 且不超过 `9999999999999999.99`；合法请求使用新 DTO 副本出站，调用方对象不被修改，响应摘要与出站真实渠道一致。

### 362.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| gateway 支付提交适配器定向测试 | `mvn -Dtest=GatewayAccessPaymentChannelSubmitAdapterTest test` | 通过 | `6` 个用例；覆盖空请求、必填空白、可选控制字符、超长、金额空值/零值/精度/上限、HTTP 零交互及规范化副本 |
| payment-core 全量测试 | `mvn test` | 通过 | `887` 个用例，`0` failures、`0` errors、`0` skipped |
| 干净 Java 8 测试编译 | `mvn -Dmaven.compiler.forceJavacCompilerUse=true -Dmaven.compiler.showWarnings=true -Dmaven.compiler.compilerArgs=-Xlint:unchecked clean test-compile` | 通过 | 新增代码无 unchecked 警告；仍有两条仓库既有 Lombok 警告 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 362.3 当前判断

1. payment-core 的 gateway 支付提交 HTTP 适配器不再依赖调用方预先清洗完整请求，污染或超出远端契约容量的字段不会发送到网关。
2. 支付主流程异常仍按结果未知处理并要求主动查单，不把适配器本地校验结果扩张为安全重试依据；本地测试不证明真实渠道、资金结果或生产稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 363. 2026-09-12 gateway 支付主动查单请求边界 V1.0 验证

### 363.1 本轮补强

1. `GatewayAccessPaymentChannelQueryAdapter` 在 HTTP 调用前校验详情非空，并要求 `paymentOrderId / orderNo / channelCode` 非空且最长均为 `64` 个字符；可选 `channelTransactionNo` 最长为 `128` 个字符。
2. 四类原始字符串在裁剪前拒绝 ISO 控制字符；空详情、必填空白、污染或超长值固定返回 `PAYMENT_CHANNEL_QUERY_FAILED`，并保证 `RestTemplate` 零交互。
3. 合法请求使用新建的 `GatewayQueryRequest` 规范化副本出站，调用方原 `PaymentDetailDTO` 不被修改；既有网关响应可信边界与查单结果规范化语义保持不变。

### 363.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| gateway 支付查单适配器定向测试 | `mvn -Dtest=GatewayAccessPaymentChannelQueryAdapterTest test` | 通过 | `8` 个用例；覆盖空详情、必填空白、可选控制字符、超长、HTTP 零交互、规范化副本及网关响应边界 |
| payment-core 全量测试 | `mvn test` | 通过 | `889` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 363.3 当前判断

1. payment-core 的 gateway 主动查单适配器不再依赖调用方预先清洗详情，污染或超出远端契约容量的字段不会发送到网关。
2. 上层 `PaymentServiceImpl.queryChannel` 仍按既有逻辑记录查单失败审计；本地 mock 不证明真实渠道查单、资金结果、账务一致性或生产稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 364. 2026-09-12 gateway 退款主动查单请求边界 V1.0 验证

### 364.1 本轮补强

1. `GatewayAccessRefundChannelQueryAdapter` 在 HTTP 调用前校验退款快照非空，并要求 `refundOrderId / channelCode` 非空且最长均为 `64` 个字符；可选 `channelRefundNo` 最长为 `128` 个字符。
2. 三类原始字符串在裁剪前拒绝 ISO 控制字符；空退款快照、必填空白、污染或超长值固定返回 `REFUND_CHANNEL_QUERY_FAILED`，并保证 `RestTemplate` 零交互。
3. 合法请求使用新建的 `GatewayRefundQueryRequest` 规范化副本出站，调用方原 `RefundCallbackSourceDTO` 不被修改；提交结果未知时允许渠道退款号为空、首次绑定可信受理事实及既有响应可信边界保持不变。

### 364.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| gateway 退款查单适配器定向测试 | `mvn -Dtest=GatewayAccessRefundChannelQueryAdapterTest test` | 通过 | `7` 个用例；覆盖空退款快照、必填空白、可选控制字符、超长、HTTP 零交互、规范化副本、未知受理及网关响应边界 |
| payment-core 全量测试 | `mvn test` | 通过 | `891` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 364.3 当前判断

1. payment-core 的 gateway 退款主动查单适配器不再依赖调用方预先清洗退款快照，污染或超出远端契约容量的字段不会发送到网关。
2. 上层 `RefundServiceImpl.queryChannel` 仍按既有逻辑记录退款查单失败审计；本地 mock 不证明真实渠道退款查单、退款到账、账务一致性或生产稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 365. 2026-09-12 gateway 退款提交请求边界 V1.0 验证

### 365.1 本轮补强

1. `GatewayAccessRefundChannelSubmitServiceImpl` 在 HTTP 调用前校验请求非空，并要求 `refundOrderId / paymentOrderId / orderNo / refundMethod / channelCode` 必填；`customerName / refundReason` 保持可选。
2. 七类字符串分别限制为 `64 / 64 / 64 / 128 / 32 / 255 / 64` 个字符，原始值在裁剪前拒绝 ISO 控制字符；金额要求大于 `0`、有效小数位不超过 `2` 且不超过 `9999999999999999.99`。
3. 合法请求复制到只包含 gateway 合同字段的 `GatewayRefundSubmitRequest` 后规范化出站，调用方原 DTO 不被修改，内部 `refundStatus / submittedChannelCode / channelRefundNo` 不进入请求体。

### 365.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| gateway 退款提交服务定向测试 | `mvn -Dtest=GatewayAccessRefundChannelSubmitServiceImplTest test` | 通过 | `7` 个用例；覆盖空请求、必填空白、可选控制字符、超长、金额空值/零值/精度/上限、HTTP 零交互、合同 DTO 与规范化副本 |
| payment-core 全量测试 | `mvn test` | 通过 | `893` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 365.3 当前判断

1. payment-core 的 gateway 退款提交服务不再依赖事件派发层预先清洗请求，也不再向跨系统边界透传内部退款状态和已绑定渠道事实。
2. 参数非法与配置门禁仍按渠道调用前确定失败处理；网络、空响应和非确定上游拒绝仍按结果未知处理并要求先查单，不能直接重复退款。
3. 本地 mock 不证明真实渠道受理、退款到账、账务一致性或生产稳定性；发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 366. 2026-09-12 支付成功事件参与方字段边界 V1.0 验证

### 366.1 本轮补强

1. `PaymentEventDispatchServiceImpl` 在支付成功事件下游派发前要求订单号与客户名称非空，订单号、客户名称和服务者名称最长分别为 `64 / 128 / 128` 个字符；服务者名称为空时保留“待分配服务者”兜底。
2. 三类原始字段在裁剪前拒绝 ISO 控制字符，污染或超长值在清分 HTTP、账务 HTTP 和 AMQP 调用前进入既有事件失败流程。
3. 合法值通过局部变量规范化，不修改原 `PaymentDetailDTO`；HTTP 清分、HTTP 账务与 AMQP 使用相同的订单号、客户名称和服务者名称。

### 366.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 支付事件派发服务定向测试 | `mvn -Dtest=PaymentEventDispatchServiceImplTest test` | 通过 | `45` 个用例；新增覆盖参与方字段下游调用前拒绝、HTTP 规范化一致性、服务者名称非法拒绝与原 DTO 不变 |
| payment-core 全量测试 | `mvn test` | 通过 | `896` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 366.3 当前判断

1. 支付成功事件不再依赖支付详情与订单查询结果预先清洗参与方字段，HTTP 与 AMQP 下游不会收到污染、超长或彼此不一致的字段。
2. 既有事件失败、重试退避、死信、HTTP 清分成功后账务失败可能重复调用清分及 AMQP publisher confirm 语义保持不变。
3. 本地 mock 不证明真实清分、真实入账、RabbitMQ 消息送达、资金结果、账务一致性或生产稳定性；发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 367. 2026-09-12 死信补偿消息元数据边界 V1.0 验证

### 367.1 本轮补强

1. 死信接入要求路由键非空、符合受控路由模式且最长 `128` 个字符；提供的消息 ID 与可选关联 ID 最长均为 `128` 个字符，原始值在裁剪前拒绝 ISO 控制字符，合法值以裁剪副本入账。
2. 新增固定脱敏的非法死信消息异常；监听器将非法路由和非法消息元数据判为永久毒消息并执行不重入队 NACK，数据库或其他运行时失败仍保持重入队。
3. 人工重放在 RabbitMQ 调用前校验持久化任务号、消息 ID、关联 ID、exchange、routing key 与载荷；非法 envelope 进入既有受控失败留痕，不发送消息。

### 367.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 死信服务与监听器定向测试 | `mvn -Dtest=PaymentDeadLetterTaskServiceImplTest,PaymentDeadLetterTaskAmqpListenerTest test` | 通过 | `21` 个用例；覆盖合法裁剪、污染/超长元数据、永久毒消息不重入队、暂时失败重入队及非法持久化 envelope 的 broker 零交互 |
| payment-core 全量测试 | `mvn test` | 通过 | `899` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 367.3 当前判断

1. 死信接入不再把确定性元数据错误误判为可恢复数据库故障，避免同一毒消息持续占用接入队列；合法消息仍只在任务账本持久化成功后 ACK。
2. 人工重放不再直接信任数据库中的 AMQP envelope，异常任务不会触发 broker；原始载荷、复核 CAS、失败脱敏和发布确认语义保持不变。
3. 本地 Mockito 不证明真实 RabbitMQ、消费者处理、补偿结果或生产稳定性；发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 368. 2026-09-12 支付监控风险总览口径 V1.0 验证

### 368.1 本轮补强

1. `PaymentMonitorView` 将后端按日期倒序返回的趋势首项作为最新快照，修复最旧记录被误标为最新记录的问题。
2. 高风险渠道统计与渠道处理建议统一识别 `P1 / 高 / HIGH`，与 Mapper 实际输出的 `P1 / P2 / 正常` 口径兼容，避免 `P1` 渠道漏计。
3. 五类空状态与风险总览 scoped 样式补齐，页面在无数据时不再出现空白表格或误导性的“选择渠道”提示，有数据时数值与说明分层展示。

### 368.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| admin-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `70` 个模块转换；仅保留仓库既有单包超过 `500 kB` 提示 |
| 有数据浏览器验证 | 本地 mock + `/payment-monitor` | 通过 | 倒序趋势显示最新日期 `2026-09-12`；一个 `P1` 渠道统计为一个高风险渠道并生成高风险处理建议 |
| 空数据浏览器验证 | 本地 mock + 页面刷新 | 通过 | 趋势、告警、告警快照、渠道列表和渠道快照五类空状态均可见 |
| 视觉检查 | 桌面浏览器截图 | 通过 | 三个风险块独立布局，数值、标题与说明无粘连或遮挡 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 368.3 当前判断

1. 支付监控页的最新趋势与高风险渠道口径已和当前后端契约一致，运营不再因前端索引或枚举错位得到错误摘要。
2. 本轮不改变监控查询、风险阈值、告警生成或下钻路由；浏览器 mock 仅验证渲染逻辑，不代表真实渠道或生产监控事实。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 369. 2026-09-12 运营风险总览共享样式 V1.0 验证

### 369.1 本轮补强

1. 审计九个使用 `overview-grid / overview-card / overview-title` 的页面，确认支付交易异常、支付路由执行结果和支付配置中心缺少自身可生效的 scoped 定义。
2. 在 admin-web 全局样式中补齐仓库既有的风险总览布局、文字层级与三类状态色，缺失页面直接复用；已有 scoped 页面凭更高选择器优先级保持原样。
3. 共享响应式规则在 `1200px` 以下将三列风险块收敛为单列，不新增组件、依赖或业务状态。

### 369.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 页面矩阵静态审计 | `rg` 检查九个风险总览使用方 | 通过 | 确认三个缺失页面与六个已有 scoped 页面，变更范围与消费者一致 |
| admin-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `70` 个模块转换；仅保留仓库既有单包超过 `500 kB` 提示 |
| 路由页计算样式 | 浏览器读取 `getComputedStyle` | 通过 | 总览为三列 grid，风险卡为 grid，间距 `8px`，danger 背景生效 |
| 配置中心计算样式 | 浏览器读取 `getComputedStyle` | 通过 | 三张治理风险卡均命中共享布局与状态色 |
| 视觉检查 | 支付路由执行结果桌面截图 | 通过 | 三张风险卡的标题、数值和说明分层，无粘连或遮挡 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 369.3 当前判断

1. 三个此前缺失样式的运营页面已恢复一致的风险总览信息层级，不再依赖其他组件不可穿透的 scoped 样式。
2. 本轮只改变 CSS 展示，不改变风险计算、异常状态、配置治理或路由事实；本地浏览器不证明真实运营数据与生产稳定性。
3. 发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 370. 2026-09-12 运营快照失败状态 V1.0 验证

### 370.1 本轮补强

1. `PaymentMonitorView / PaymentRoutesView / PaymentIssuesView` 只在完整请求成功后记录已加载状态；首次请求失败时隐藏默认指标与伪空结果，明确提示当前没有可展示的运营快照。
2. 成功加载后的刷新失败不覆盖上次成功数据，错误横幅明确标记当前展示旧快照；成功返回空数组仍作为合法业务空结果展示。
3. 支付交易异常中心在请求错误期间禁用批量处理按钮和行复选框，避免基于旧快照提交处理动作。

### 370.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 首次失败浏览器验证 | 默认后端不可达 + 三个运营页面 | 通过 | 监控和路由默认指标/风险卡未渲染；异常中心责任组统计、列表伪空态未渲染，批量操作禁用 |
| 成功后刷新失败验证 | 本机 mock 先返回唯一数据，再切换 `503` 并触发刷新 | 通过 | 三页均保留唯一旧数据并显示“当前展示上次成功快照”；异常中心批量按钮与行复选框均禁用 |
| 成功空结果验证 | 本机 mock 返回合法空数组 | 通过 | 三页显示原有业务空态，不显示“当前没有可展示的快照”失败空态 |
| admin-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `70` 个模块转换；仅保留仓库既有单包超过 `500 kB` 提示 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 370.3 当前判断

1. 三个运营页面已区分“从未成功加载”“当前成功空结果”和“刷新失败但存在旧快照”，不会再用默认零值或普通空态遮蔽接口失败。
2. 异常中心旧快照只允许查看，不允许选择或批量处理；监控和路由页面保持只读，无新增写入口。
3. 本机 mock 只验证前端状态机与交互，不证明真实运营数据、真实渠道、处置结果或生产稳定性；发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 371. 2026-09-12 人工任务与日终快照保护 V1.0 验证

### 371.1 本轮补强

1. `PaymentTaskCenterView / PaymentDayEndView` 只在查询成功后展示总览；首次失败隐藏初始化零值与人工执行区，已有数据刷新失败保留并标记上次成功快照。
2. 任务中心首次加载原子提交总览和任务日志；后续日志刷新使用独立加载与错误状态，保留旧日志并锁定旧分页入口。
3. 人工任务成功响应先更新总览和执行完成提示，再刷新日志；日志刷新失败不进入任务 POST 的失败分支。所有人工任务按钮在任一任务执行期间统一禁用，日终跑批在总览错误期间禁用。

### 371.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 首次失败浏览器验证 | 本机 mock `503` + `/payment-task-center`、`/payment-day-end` | 通过 | 两页均显示无可用快照，默认指标与人工任务/跑批入口未渲染 |
| 旧快照浏览器验证 | 先返回唯一数据，再切换 `503` 并刷新 | 通过 | 任务日志号与日终批次号均保留，错误横幅标记旧快照，任务与跑批按钮禁用 |
| 人工任务并发锁定 | 人工任务 POST 延迟 `600ms` | 通过 | 执行中的按钮及另一人工任务按钮同时为 disabled，十类按钮共用相同门禁表达式 |
| 写成功与日志读失败分离 | 任务 POST 成功，随后日志 GET 返回 `503` | 通过 | 页面显示“执行完成”、更新总览并保留旧日志；另行提示日志刷新失败，不出现任务执行失败 |
| 成功空结果验证 | 本机 mock 返回零值总览与空日志/批次数组 | 通过 | 正常零值和日志空态可见，不显示无可用快照失败态 |
| admin-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `70` 个模块转换；仅保留仓库既有单包超过 `500 kB` 提示 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 371.3 当前判断

1. 任务中心已将人工任务写结果与任务日志读刷新解耦，避免运维人员因日志接口故障误判已执行任务并重复触发。
2. 任务中心和日终页不再用默认财务/运营数值遮蔽首次查询失败，也不会在总览旧快照状态继续开放人工写入口。
3. 本机 mock 不证明真实补偿任务、渠道查询、事件投递、日终资金事实或生产稳定性；发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 372. 2026-09-12 告警与死信人工动作快照保护 V1.0 验证

### 372.1 本轮补强

1. `PaymentIssueAlertsView / PaymentDeadLetterTasksView` 仅在列表成功后展示指标与业务空态；首次失败显示无可用快照，刷新失败保留上次成功列表并锁定动作与分页。
2. 两页将查询错误和人工动作错误拆分；缺少操作人或处置说明、POST 失败与回查有限失败不再污染快照新鲜度提示。
3. 告警确认/回查及死信复核/结案/重放成功后独立刷新列表；列表刷新失败不覆盖 POST 结果，任一动作进行时全页同类动作统一禁用。

### 372.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 首次失败浏览器验证 | 本机 mock `503` + `/payment-issue-alerts`、`/payment-dead-letter-tasks` | 通过 | 两页均显示无可用快照，零指标和行操作未渲染 |
| 旧快照浏览器验证 | 先返回唯一告警/死信任务，再切换 `503` 并刷新 | 通过 | 唯一记录保留，确认、回查、复核、结案动作均禁用，错误横幅标记旧快照 |
| 动作并发锁定 | 告警确认与死信复核 POST 分别延迟 `600ms` | 通过 | 当前动作及同页另一动作同时 disabled，跨行使用相同全页门禁 |
| 写成功与列表读失败分离 | POST 成功，随后对应列表 GET 返回 `503` | 通过 | 告警确认与死信允许重放均保留成功文案，另行提示列表刷新失败，不出现动作失败文案 |
| 成功空结果验证 | 本机 mock 返回合法空列表 | 通过 | 两页显示普通业务空态和零指标，不显示无可用快照失败态 |
| admin-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `70` 个模块转换；仅保留仓库既有单包超过 `500 kB` 提示 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 372.3 当前判断

1. 告警与死信人工动作不会再因动作后的列表故障被误报失败，降低运营重复确认、重复回查、重复复核或重复重放风险。
2. 旧快照只供排障查看；新的列表查询成功前，行级动作与翻页保持失败关闭。查询错误与人工输入/动作结果各自保留明确语义。
3. 本机 mock 不证明真实通知送达、RabbitMQ 发布确认、下游消费、入账或生产稳定性；发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 373. 2026-09-12 支付事件出站快照与重发保护 V1.0 验证

### 373.1 本轮补强

1. `PaymentEventsView` 将事件列表与风险总览作为同一首次加载快照，只有两项查询同时成功才提交页面数据；首次失败隐藏初始化零值和普通空态，成功空列表保持合法业务语义。
2. 页面拆分完整查询错误、总览刷新错误、人工输入/重发错误和重发成功提示；已有快照刷新失败时保留旧列表、旧总览和事件侧栏并锁定写入口与分页。
3. 重发 POST 成功后立即使用分页响应更新列表与当前事件侧栏，再独立刷新总览；总览 GET 失败不覆盖重发成功结论。任一重发等待期间，同页全部重发以及查询、重置、刷新入口统一禁用。

### 373.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 首次失败浏览器验证 | 本机 mock `503` + `/payment-events` | 通过 | 默认零指标、风险总览、表格和重发入口未渲染，显示无可用快照 |
| 旧快照浏览器验证 | 先返回两条事件与总览，再切换 `503` 并刷新 | 通过 | 两条记录与风险总览保留，错误横幅标记旧快照，两行重发均禁用 |
| 成功空结果验证 | 本机 mock 返回合法空列表与零值总览 | 通过 | 显示零指标、风险总览和普通业务空态，不显示失败空态 |
| 人工输入错误验证 | 操作人为空时触发重发 | 通过 | 只显示“请先输入重发操作人”，不污染查询快照错误 |
| 重发并发锁定 | 两条失败事件，首条 POST 延迟 `600ms` | 通过 | 等待期间两行重发按钮同时 disabled，刷新入口同步锁定 |
| 写成功与总览读失败分离 | 重发 POST 成功，随后总览 GET 返回 `503` | 通过 | 列表和侧栏更新为 `SUCCESS`，保留重发成功文案并单独标记总览旧快照，另一行重发继续禁用 |
| admin-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `70` 个模块转换；仅保留仓库既有单包超过 `500 kB` 提示 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 373.3 当前判断

1. 支付事件出站页已区分首次失败、成功空结果、完整旧快照和重发后总览局部失效，避免默认零值或错误横幅混淆请求事实。
2. 重发结果以 POST 响应为准，后续总览读失败不再诱导重复重发；`UNKNOWN` 事件继续禁止直接重发，旧快照只供排障查看。
3. 本机 mock 仅证明前端状态机，不证明真实 RabbitMQ 发布确认、下游消费、入账、业务完成或生产稳定性；发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 374. 2026-09-12 支付单人工动作快照保护 V1.0 验证

### 374.1 本轮补强

1. `PaymentsView` 仅在列表成功后展示指标、业务空态与人工动作；首次失败显示无可用快照，已有快照刷新失败保留列表和支付单侧栏并锁定写入口与分页。
2. 页面拆分完整查询错误、人工动作后列表刷新错误、操作人/POST 错误和动作结果；顶部增加明确刷新入口，任一人工动作期间锁定同页全部查单、关闭和筛选加载入口。
3. 主动查单与人工关闭 POST 成功后，先用服务端返回的 `PaymentDetailDTO` 更新目标列表行和侧栏，再按动作发起前的筛选快照独立刷新列表；列表 GET 失败不覆盖动作结果。

### 374.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 首次失败浏览器验证 | 本机 mock `503` + `/payments` | 通过 | 默认零指标、普通空态、表格和人工动作未渲染，显示无可用快照 |
| 旧快照浏览器验证 | 先返回两条支付单，再切换 `503` 并刷新 | 通过 | 两条记录和侧栏保留，错误横幅标记旧快照，查单与关闭均禁用 |
| 成功空结果验证 | 本机 mock 返回合法空列表 | 通过 | 显示零指标与普通业务空态，不显示失败空态 |
| 人工输入错误验证 | 操作人为空时触发主动查单 | 通过 | 只显示“请先输入操作人”，不污染列表新鲜度错误 |
| 动作并发锁定 | 两条支付单，主动查单 POST 延迟 `600ms` | 通过 | 两行查单/关闭以及筛选输入、查询、重置和刷新在等待期间统一禁用 |
| 查单成功与列表读失败分离 | 主动查单 POST 返回 `SUCCESS`，随后列表 GET 返回 `503` | 通过 | 目标行和侧栏更新渠道、渠道流水与状态，保留查单结果并单独标记其他列表旧快照 |
| 关闭成功与列表读失败分离 | 人工关闭 POST 返回 `CLOSED`，随后列表 GET 返回 `503` | 通过 | 目标行和侧栏更新为已关闭，保留关闭结果并锁定其余旧列表动作 |
| admin-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `70` 个模块转换；仅保留仓库既有单包超过 `500 kB` 提示 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 374.3 当前判断

1. 支付单管理页已区分首次失败、成功空结果、完整旧快照和人工动作后列表局部失效，不再以默认零值或混合横幅遮蔽请求事实。
2. 主动查单与人工关闭结果以各自 POST 响应为准；后续列表故障不会诱导重复动作，旧快照只供查看，新的列表查询成功后才恢复写入口。
3. 本机 mock 仅证明前端状态机，不证明真实 gateway-access、渠道查询、支付成功资金事实、生产数据库或稳定性；发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 375. 2026-09-12 退款单人工动作快照保护 V1.0 验证

### 375.1 本轮补强

1. `RefundsView` 仅在列表成功后展示指标、业务空态和人工动作；首次失败显示无可用快照，成功空列表仍展示零指标与发起退款入口，申请资格最终由服务端复验。
2. 页面拆分完整查询错误、人工动作后列表刷新错误、表单/POST 错误和动作结果；已有快照刷新失败保留列表与退款单侧栏，并锁定申请、审核、查单、重试和分页。
3. 审核、查单与重试 POST 成功后先用完整 `RefundDetailDTO` 更新目标行和侧栏，再独立刷新列表；发起退款成功保留服务端退款单号，但不按未知筛选条件插入列表。四类 POST 均不因后续列表 GET 失败而被改写为失败。

### 375.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 首次失败浏览器验证 | 本机 mock `503` + `/refunds` | 通过 | 默认零指标、待处理徽标、普通空态、申请区、表格和人工动作未渲染，显示无可用快照 |
| 成功空结果验证 | 本机 mock 返回合法空列表 | 通过 | 显示零指标、普通业务空态与发起退款入口，不显示失败空态 |
| 人工输入错误验证 | 操作人为空时触发退款动作 | 通过 | 只显示表单校验错误，不污染列表新鲜度错误 |
| 动作并发锁定 | 三条退款分别为 `REVIEWING / PROCESSING / FAIL`，审核 POST 延迟 `600ms` | 通过 | 申请、审核、查单、重试及筛选加载入口在等待期间统一 disabled，既有状态资格门禁不变 |
| 审核成功与列表读失败分离 | 审核 POST 返回 `PROCESSING`，随后列表 GET 返回 `503` | 通过 | 目标行与侧栏更新，成功结论保留，其他列表标记旧快照且全部写入口继续禁用 |
| 申请成功与列表读失败分离 | 申请 POST 返回 `REFUND-SNAPSHOT-NEW`，随后列表 GET 返回 `503` | 通过 | 新退款单号成功提示保留，不误报申请失败，也不在未知筛选条件下插入记录 |
| admin-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `70` 个模块转换；仅保留仓库既有单包超过 `500 kB` 提示 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 375.3 当前判断

1. 退款单管理页已区分首次失败、成功空结果、完整旧快照和人工动作后列表局部失效，不再用默认零值或混合错误横幅遮蔽请求事实。
2. 发起退款、审核、主动查单和重试结果以各自 POST 响应为准；后续列表故障不会诱导重复动作，旧快照只供查看，新的列表查询成功后才恢复写入口。
3. 退款受理、查单或重试响应不等于退款到账、账务一致或业务收口。本机 mock 仅证明前端状态机，不证明真实 gateway-access、渠道退款、生产数据库或稳定性；发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 376. 2026-09-12 支付与退款详情动作快照保护 V1.0 验证

### 376.1 本轮补强

1. `PaymentDetailView / RefundDetailView / PaymentRecordDetailView` 只有在详情 GET 成功后才开放人工动作；首次失败显示无可用快照，刷新失败保留旧详情并锁定写入口。
2. 三页拆分加载错误、动作后刷新错误、表单/POST 错误和动作结果；支付与退款领域详情使用 POST 返回的完整 DTO 立即更新，后续 GET 失败不覆盖成功结论。
3. 支付记录详情不使用结构不同的 `PaymentDetailDTO` 覆盖 `PaymentRecordDetailDTO`；查单成功后只展示权威状态结论，记录详情 GET 失败时保留原记录快照并保持只读。

### 376.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 首次失败浏览器验证 | 本机 mock `404/503` + 支付详情 | 通过 | 显示无可用详情快照，主动查单与关闭支付单均 disabled |
| 支付详情动作互斥 | 主动查单 POST 延迟 `600ms` | 通过 | 查单、关闭、刷新和操作人输入在等待期间统一 disabled |
| 支付详情写成功/读失败 | 查单 POST 返回 `SUCCESS`，随后详情 GET 返回 `503` | 通过 | 当前详情状态更新为 `SUCCESS`，成功结论保留，详情刷新错误独立提示且写入口锁定 |
| 退款详情动作互斥 | 审核 POST 延迟 `600ms` | 通过 | 审核、查单、重试、刷新和操作人输入在等待期间统一 disabled |
| 退款详情写成功/读失败 | 审核 POST 返回 `PROCESSING`，随后详情 GET 返回 `503` | 通过 | 当前详情更新为 `PROCESSING`，成功结论保留，详情刷新错误独立提示且写入口锁定 |
| 支付记录详情写成功/读失败 | 查单 POST 返回 `SUCCESS`，随后记录详情 GET 返回 `503` | 通过 | 查单状态结论保留，原 `PREPAY_CREATED` 记录快照不被异构 DTO 覆盖，页面保持只读 |
| admin-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `70` 个模块转换；仅保留仓库既有单包超过 `500 kB` 提示 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 376.3 当前判断

1. 三个详情页已区分首次失败、普通旧快照和人工动作后的局部失效，避免详情读故障被误写为人工动作失败。
2. 领域详情与支付记录详情遵循各自 DTO 合同；只有响应结构匹配时才更新当前详情，支付记录查单结果不会臆造记录字段。
3. 本机 mock 仅证明前端状态机，不证明真实 gateway-access、渠道查单、退款处理、资金结果、账务一致或生产稳定性；发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 377. 2026-09-12 订单发起支付快照保护 V1.0 验证

### 377.1 本轮补强

1. `OrdersView` 仅在列表 GET 成功后建立可操作订单快照；首次失败显示无可用快照，成功空列表保留普通业务空态，旧快照刷新失败时禁止发起支付和翻页。
2. 页面拆分列表加载错误、发起支付后列表刷新错误、POST 错误和成功结果；预付单 POST 成功后先保留预付单号、支付单号与收银台链接，再独立刷新订单列表。
3. `PrepayOrderDTO` 与 `OrderListItemDTO` 保持各自合同，前端不使用预付单响应臆造聚合订单行；任一发起支付等待期间，同页全部发起按钮和筛选加载入口统一禁用。

### 377.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 首次失败浏览器验证 | 本机 mock `503` + `/orders` | 通过 | 显示无可用订单快照，不显示业务空态、表格或发起支付入口 |
| 成功空结果验证 | 本机 mock 返回合法空列表 | 通过 | 显示普通订单业务空态，不显示无可用快照提示 |
| 发起支付并发锁定 | 两条待支付订单，首条 POST 延迟 `600ms` | 通过 | 两行发起按钮以及查询、重置、刷新和筛选输入在等待期间统一 disabled |
| 写成功与列表读失败分离 | 预付单 POST 成功，随后订单列表 GET 返回 `503` | 通过 | `PRE-LAUNCH-SNAPSHOT-NEW / PAY-LAUNCH-SNAPSHOT-NEW` 与收银台入口保留，不出现发起失败文案，旧两行保持只读 |
| admin-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `70` 个模块转换；仅保留仓库既有单包超过 `500 kB` 提示 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 377.3 当前判断

1. 订单中心已区分首次失败、成功空结果、普通旧快照与发起支付后的列表局部失效，不再把列表读故障误报为预付单创建失败。
2. 预付单响应只承载其自身可证明的创建和收银台事实；订单聚合字段等待列表 GET 确认，旧快照期间不允许重复发起。
3. 本机 mock 仅证明前端状态机，不证明生产数据库幂等、真实收银台、渠道受理、支付成功、账单结清或生产稳定性；发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 378. 2026-09-12 异常批量处理快照保护 V1.0 验证

### 378.1 本轮补强

1. `PaymentIssuesView` 将完整查询、批量动作后的完整刷新、表单/POST 错误和动作结果分别建模；旧快照与动作刷新失败状态继续禁止选择和批量写入。
2. 批量 POST 返回的默认分页不再覆盖当前筛选页；POST 成功后按动作前筛选和页码并行请求异常列表与责任组统计，只有两项都成功才更新页面。
3. 任一批量动作执行期间，动作表单、行复选框、筛选、查询、重置、刷新与分页全页互斥；动作后 GET 失败保留成功结论并清空已提交选择，旧快照不推导新处理状态。

### 378.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 动作并发锁定 | 选中唯一异常并将批量 POST 延迟 `600ms` | 通过 | 批量按钮、复选框、动作表单、筛选输入、查询、重置和刷新在等待期间统一 disabled |
| 写成功与完整快照读失败分离 | POST 成功，随后列表与责任组统计 GET 返回 `503` | 通过 | 显示“已记录 1 条异常”，刷新错误独立提示，不出现批量处理失败文案 |
| 默认分页隔离 | POST 响应返回处理后的默认分页，后续 GET 失败 | 通过 | 当前筛选旧行仍显示原“待分派”，不被无筛选语义的 POST 分页覆盖，复选框已清空且页面只读 |
| 快照恢复 | mock 恢复正常后点击刷新 | 通过 | 动作结果和刷新错误清理，列表与责任组统计重新提交，复选框及批量入口恢复可用 |
| admin-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `70` 个模块转换；仅保留仓库既有单包超过 `500 kB` 提示 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 378.3 当前判断

1. 异常批量处理结果与当前筛选读模型不再混用，服务端默认分页不会静默替换运营正在处理的筛选范围。
2. POST 成功后的完整读刷新失败不会诱导重复提交；旧列表只供排障查看，下一次列表与责任组统计同时成功后才恢复写入口。
3. 本机 mock 仅证明前端状态机，不证明生产数据库事务、异常根因消除、支付资金收口、告警送达、SLA 闭环或生产稳定性；发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 379. 2026-09-12 配置总览快照保护 V1.0 验证

### 379.1 本轮补强

1. `PaymentConfigView` 新增总览成功快照标记和独立加载错误；初始化数组与治理零值不再被当成服务端成功配置展示。
2. 首次失败隐藏治理指标及所有配置内容；已有快照刷新失败保留旧内容，通过统一 `fieldset` 门禁禁止其内部输入、按钮和选择器。
3. 顶部总览刷新位于旧快照门禁外，加载期间防重复触发，成功后清除加载错误并恢复配置操作。

### 379.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 首次加载失败 | 新页面进入时总览 GET 返回 `503` | 通过 | 仅显示加载失败与无可用快照，不显示零治理指标、配置表单或写按钮 |
| 旧快照只读 | 先成功加载，再将总览 GET 切换为 `503` 并点击刷新 | 通过 | 旧配置与指标保留，错误明确提示上次成功快照；内容区按钮、输入和选择器统一 disabled |
| 恢复入口 | 旧快照状态检查顶部操作 | 通过 | “刷新配置”保持可用，配置内容内部各子域刷新与写入口保持禁用 |
| 成功恢复 | mock 恢复正常后点击“刷新配置” | 通过 | 错误清除，配置表单和子域操作重新开放 |
| admin-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `71` 个模块转换；仅保留仓库既有单包超过 `500 kB` 提示 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 379.3 当前判断

1. 配置中心不再把客户端初始化值包装为服务端配置事实，首次读失败不会暴露基于未知基线的写入口。
2. 旧快照仍可用于排障观察，但不能提交配置候选或处理审核；只有下一次总览成功后才恢复操作。
3. 本机 mock 仅证明前端状态机，不证明生产配置、可信身份、正式审批、渠道有效性或生产稳定性；发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 380. 2026-09-12 渠道审核结果与读刷新解耦 V1.0 验证

### 380.1 本轮补强

1. `handleChannelReview` 先处理批准或驳回 POST，并立即提交审核列表、清理审核意见及展示动作结果；只有批准成功才进入后续读刷新。
2. 批准后的配置总览和已打开渠道版本历史改为并行刷新，各加载函数维持独立错误状态；渠道版本错误不再复用全局动作提示。
3. 活动配置锁由外层单一 `finally` 释放，覆盖批准、驳回、POST 失败以及批准后读刷新结束路径。

### 380.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| POST 与读刷新分支审查 | 检查 `handleChannelReview` 控制流 | 通过 | POST catch 仅处理审核请求；批准成功后另行构造并等待总览与版本刷新任务 |
| 版本错误域隔离 | 检查 `loadChannelVersions` 与版本面板 | 通过 | 加载错误写入 `channelVersionError`，不再清空或覆盖 `actionMessage`，关闭面板时清理 |
| 活动锁释放 | 检查批准、驳回、POST 失败与刷新路径 | 通过 | 所有已发起审核路径最终经过同一外层 `finally` 清理 `activeConfigCode` |
| admin-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `71` 个模块转换；仅保留仓库既有单包超过 `500 kB` 提示 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |
| 浏览器组合场景 | mock 已加载待审核渠道与版本，准备 POST 成功后两项 GET `503` | 待补 | 原生确认框出现时本机进入锁屏，自动化无法确认；不将该场景登记为通过 |

### 380.3 当前判断

1. 代码控制流不再把批准后的配置或版本读取故障包装为渠道审核 POST 失败，渠道版本错误也不再覆盖审核结果。
2. 浏览器组合场景仍需在本机解锁后补验；在此之前仅能确认生产构建、补丁检查和静态控制流，不扩大为完整交互验证结论。
3. 本机 mock 不证明可信身份、正式审批、真实渠道生效或生产稳定性；发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 381. 2026-09-12 网关审核结果与读刷新解耦 V1.0 验证

### 381.1 本轮补强

1. `handleGatewayReview` 先处理批准或驳回 POST，并立即提交审核列表、清理审核意见及展示动作结果；只有批准成功才进入后续读刷新。
2. 批准后的配置总览和已打开网关版本历史改为并行刷新；网关版本错误使用独立状态，不再复用全局动作提示。
3. 活动配置锁由外层单一 `finally` 释放，覆盖批准、驳回、POST 失败以及批准后读刷新结束路径。

### 381.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| POST 与读刷新分支审查 | 检查 `handleGatewayReview` 控制流 | 通过 | POST catch 仅处理网关审核请求；批准成功后另行构造并等待总览与版本刷新任务 |
| 版本错误域隔离 | 检查 `loadGatewayVersions` 与版本面板 | 通过 | 加载错误写入 `gatewayVersionError`，不再清空或覆盖 `actionMessage`，关闭面板时清理 |
| 驳回路径 | 检查 `approved=false` 分支 | 通过 | POST 成功结论提交后直接返回，不发起总览或版本读取 |
| 活动锁释放 | 检查批准、驳回、POST 失败与刷新路径 | 通过 | 所有已发起审核路径最终经过同一外层 `finally` 清理 `activeConfigCode` |
| admin-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `71` 个模块转换；仅保留仓库既有单包超过 `500 kB` 提示 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |
| 浏览器组合场景 | 批准 POST 成功后总览与网关版本 GET 失败 | 待补 | 本机仍处于锁屏，无法操作原生确认框；不将该场景登记为通过 |

### 381.3 当前判断

1. 代码控制流不再把批准后的配置或版本读取故障包装为网关审核 POST 失败，网关版本错误也不再覆盖审核结果。
2. 浏览器组合场景仍需在本机解锁后补验；当前只确认生产构建、补丁检查和静态控制流，不扩大为完整交互验证结论。
3. 本机验证不证明可信身份、正式审批、证书有效、真实网关可用或生产稳定性；发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未闭环，`release/v1.0.0` 继续冻结。

## 382. 2026-09-12 控制策略审核结果与读刷新解耦 V1.0 验证

### 382.1 本轮补强

1. `handleControlPolicyReview` 独立提交批准或驳回 POST 结果；批准后才并行刷新总览和当前策略版本，驳回不发起运行配置读取。
2. `loadControlPolicyVersions` 使用 `controlPolicyVersionError`，不再清空或覆盖全局动作结论；关闭版本区同步清理错误。
3. 外层单一 `finally` 统一释放活动配置锁，覆盖 POST 失败、驳回成功、批准成功及批准后读刷新结束。

### 382.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| POST 与读刷新分支审查 | 检查 `handleControlPolicyReview` | 通过 | POST 异常边界与批准后的总览/版本读取分离，驳回直接结束 |
| 动作结论保护 | 检查批准成功赋值与版本加载 | 通过 | “请重新执行自检”结论先提交，版本加载只写独立错误状态 |
| 活动锁释放 | 检查全部返回和异常路径 | 通过 | 已发起审核路径均由外层 `finally` 清理 `activeConfigCode` |
| admin-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `71` 个模块转换；仅保留仓库既有单包超过 `500 kB` 提示 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |
| 浏览器组合场景 | 批准 POST 成功后总览与策略版本 GET 失败 | 待补 | 本机仍处于锁屏，无法操作原生确认框；不登记为通过 |

### 382.3 当前判断

1. 代码控制流不再把批准后的读取故障包装为控制策略审核失败，策略版本错误不会覆盖重新自检提示。
2. 浏览器组合场景需在本机解锁后补验；当前证据只覆盖生产构建、补丁检查与静态控制流。
3. 本机验证不证明可信身份、正式审批、自检通过、生产限流有效或生产稳定性；release 四项真实阻断不变，`release/v1.0.0` 继续冻结。

## 383. 2026-09-12 路由规则审核结果与读刷新解耦 V1.0 验证

### 383.1 本轮补强

1. `handleRouteRuleReview` 先独立提交批准或驳回 POST 结果；批准后才并行刷新总览和当前规则版本，驳回不发起运行态读取。
2. `loadRouteRuleVersions` 使用 `routeRuleVersionError`，不再清空或覆盖全局动作结论；关闭版本区同步清理错误。
3. 外层单一 `finally` 统一释放活动配置锁；既有冲突检测仍为独立操作，不从审核或刷新结果推导。

### 383.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| POST 与读刷新分支审查 | 检查 `handleRouteRuleReview` | 通过 | POST 异常边界与批准后的总览/版本读取分离，驳回直接结束 |
| 动作结论保护 | 检查批准成功与版本加载 | 通过 | “候选规则已写入运行态”先提交，版本加载仅写独立错误状态 |
| 活动锁释放 | 检查全部返回和异常路径 | 通过 | 已发起审核路径均由外层 `finally` 清理 `activeConfigCode` |
| admin-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `71` 个模块转换；仅保留仓库既有单包超过 `500 kB` 提示 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |
| 浏览器组合场景 | 批准 POST 成功后总览与规则版本 GET 失败 | 待补 | 本机仍处于锁屏，无法操作原生确认框；不登记为通过 |

### 383.3 当前判断

1. 代码控制流不再把批准后的读取故障包装为路由审核失败，规则版本错误不会覆盖审核写结果。
2. 浏览器组合场景需在本机解锁后补验；当前证据只覆盖生产构建、补丁检查与静态控制流。
3. 本机验证不证明冲突消除、真实流量命中、灰度有效或生产稳定性；release 四项真实阻断不变，`release/v1.0.0` 继续冻结。

## 384. 2026-09-12 支付协议审核结果与读刷新解耦 V1.0 验证

### 384.1 本轮补强

1. `handleProtocolReview` 独立提交批准或驳回 POST 结果；批准后才并行刷新总览和当前协议版本，驳回不发起运行态读取。
2. `loadProtocolVersions` 使用 `protocolVersionError`，不再清空或覆盖全局动作结论；关闭版本区同步清理错误。
3. 外层单一 `finally` 统一释放活动配置锁，覆盖 POST 失败、驳回成功、批准成功及批准后读刷新结束。

### 384.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| POST 与读刷新分支审查 | 检查 `handleProtocolReview` | 通过 | POST 异常边界与批准后的总览/版本读取分离，驳回直接结束 |
| 动作结论保护 | 检查批准成功与版本加载 | 通过 | “候选协议已写入运行态”先提交，版本加载仅写独立错误状态 |
| 活动锁释放 | 检查全部返回和异常路径 | 通过 | 已发起审核路径均由外层 `finally` 清理 `activeConfigCode` |
| admin-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `71` 个模块转换；仅保留仓库既有单包超过 `500 kB` 提示 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |
| 浏览器组合场景 | 批准 POST 成功后总览与协议版本 GET 失败 | 待补 | 本机仍处于锁屏，无法操作原生确认框；不登记为通过 |

### 384.3 当前判断

1. 代码控制流不再把批准后的读取故障包装为协议审核失败，协议版本错误不会覆盖审核写结果。
2. 浏览器组合场景需在本机解锁后补验；当前证据只覆盖生产构建、补丁检查与静态控制流。
3. 本机验证不证明可信身份、法务审批、签章回执、签署主体有效或生产稳定性；release 四项真实阻断不变，`release/v1.0.0` 继续冻结。

## 385. 2026-09-12 返回码审核结果与总览刷新解耦 V1.0 验证

### 385.1 本轮补强

1. `handleReturnCodeReview` 与 `handleReturnCodeImportReview` 均先独立处理审核 POST，提交返回列表、清理复核意见并展示动作结果。
2. 只有批准成功才在 POST 结果之后刷新总览；驳回直接结束，POST 失败只进入对应写请求错误分支。
3. 两条链路均由外层单一 `finally` 释放 `activeConfigCode`，覆盖批准、驳回、POST 失败与总览刷新结束。

### 385.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 单条状态审核控制流 | 检查 `handleReturnCodeReview` | 通过 | 审核 POST 与后续总览 GET 使用不同异常边界，驳回不刷新 |
| CSV 导入审核控制流 | 检查 `handleReturnCodeImportReview` | 通过 | 批量审核结果先提交，批准后才读取总览，驳回不刷新 |
| 活动锁释放 | 检查两类审核全部返回路径 | 通过 | 已发起审核路径均由外层 `finally` 清理活动锁 |
| admin-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `71` 个模块转换；仅保留仓库既有单包超过 `500 kB` 提示 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |
| 浏览器组合场景 | 两类批准 POST 成功后总览 GET 失败 | 待补 | 本机仍处于锁屏，无法操作原生确认框；不登记为通过 |

### 385.3 当前判断

1. 两类返回码审核均不再等待总览刷新后才确认写结果，总览读故障不会被包装为返回码审核失败。
2. 浏览器组合场景需在本机解锁后补验；当前证据只覆盖生产构建、补丁检查与静态控制流。
3. 本机验证不证明真实渠道码表正确、自动归档完成、生产映射有效或生产稳定性；release 四项真实阻断不变，`release/v1.0.0` 继续冻结。

## 386. 2026-09-12 通知治理审核结果与总览刷新解耦 V1.0 验证

### 386.1 本轮补强

1. `handleAlertProviderReview` 与 `handleIssueDutyRosterReview` 均先独立处理审核 POST，提交对应审核列表、清理复核意见并展示动作结果。
2. 只有批准成功才在 POST 结果之后刷新总览；驳回直接结束，POST 失败只进入所属写请求错误分支。
3. 两条链路均由外层单一 `finally` 释放 `activeConfigCode`，覆盖批准、驳回、POST 失败与总览刷新结束。

### 386.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 供应商审核控制流 | 检查 `handleAlertProviderReview` | 通过 | 审核 POST 与后续总览 GET 使用不同异常边界，驳回不刷新 |
| 值班路由审核控制流 | 检查 `handleIssueDutyRosterReview` | 通过 | 审核结果先提交，批准后才读取总览，驳回不刷新 |
| 活动锁释放 | 检查两类审核全部返回路径 | 通过 | 已发起审核路径均由外层 `finally` 清理活动锁 |
| admin-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `71` 个模块转换；仅保留仓库既有单包超过 `500 kB` 提示 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |
| 浏览器组合场景 | 两类批准 POST 成功后总览 GET 失败 | 待补 | 本机仍处于锁屏，无法操作原生确认框；不登记为通过 |

### 386.3 当前判断

1. 两类通知治理审核均不再等待总览刷新后才确认写结果，总览读故障不会被包装为审核失败。
2. 浏览器组合场景需在本机解锁后补验；当前证据只覆盖生产构建、补丁检查与静态控制流。
3. 本机验证不证明外部通知送达、真实排班接收、升级动作执行或生产稳定性；release 四项真实阻断不变，`release/v1.0.0` 继续冻结。

## 387. 2026-09-12 配置动作提示语义分级 V1.0 验证

### 387.1 本轮补强

1. 新增集中式 `actionMessagePresentation`，将配置动作提示映射为错误、信息或成功展示级别。
2. 失败、未通过与不合法结果使用 `error-banner` 和 `alert`；编辑、文件读取及待处理冲突使用 `notice-banner` 和 `status`；已完成动作使用 `success-banner` 和 `status`。
3. 页面原有业务函数、接口调用和动作消息内容保持不变，避免提示样式修复扩大到配置写入行为。

### 387.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 失败提示映射 | 静态检查 `actionMessagePresentation` | 通过 | “失败”“未通过”“不合法”均进入错误提示与 `alert` |
| 信息与成功提示映射 | 静态检查集中映射及模板绑定 | 通过 | 编辑、读取、待处理与完成结果不再共用单一成功语义 |
| 业务行为边界 | 检查代码差异 | 通过 | 未修改 API 调用、审核状态、活动锁或运行态写入逻辑 |
| admin-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `71` 个模块转换；仅保留仓库既有单包超过 `500 kB` 提示 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |
| 浏览器视觉与读屏组合 | 错误、信息、成功三类提示 | 待补 | 本机仍处于锁屏，未执行真实浏览器与辅助技术验证 |

### 387.3 当前判断

1. 配置动作失败和校验拒绝不再以绿色成功样式误导运营人员，并通过角色语义区分错误与状态更新。
2. 浏览器视觉、键盘流程和读屏播报仍需在本机解锁后补验；当前证据只覆盖生产构建、补丁检查与静态映射。
3. 本机验证不证明真实配置有效、外部链路可用或生产稳定性；release 四项真实阻断不变，`release/v1.0.0` 继续冻结。

## 388. 2026-09-12 配置版本历史旧快照保护 V1.0 验证

### 388.1 本轮补强

1. `loadChannelVersions`、`loadGatewayVersions`、`loadControlPolicyVersions`、`loadRouteRuleVersions` 与 `loadProtocolVersions` 仅在目标编码变化时清空版本数组。
2. 同一配置刷新保留上次成功数组；当前请求失败后设置独立错误状态，模板同时展示刷新失败说明与旧版本表格。
3. 首次加载失败且无快照时不再进入普通空态；五类关闭函数继续递增请求序号并完整清理面板状态。

### 388.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 同配置刷新保护 | 静态检查五类版本加载函数 | 通过 | 目标编码未变化时不清空上次成功数组 |
| 跨配置隔离 | 静态检查编码切换与请求序号 | 通过 | 新目标先清空，旧请求响应不能提交到新面板 |
| 首次失败与空态 | 静态检查五类模板条件 | 通过 | 无快照且加载失败时只显示错误，不误报“暂无记录” |
| 关闭清理 | 静态检查五类关闭函数 | 通过 | 请求失效、编码、数组、加载态和错误态均清理 |
| admin-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `71` 个模块转换；仅保留仓库既有单包超过 `500 kB` 提示 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |
| 浏览器组合场景 | 首次失败、同配置刷新失败、快速切换 | 待补 | 本机仍处于锁屏，未执行原生浏览器交互验证 |

### 388.3 当前判断

1. 五类配置版本历史已区分“确定为空”“首次加载未知”和“刷新失败但有旧快照”三种读模型状态。
2. 浏览器快速切换与失败恢复仍需在本机解锁后补验；当前证据只覆盖生产构建、补丁检查和静态控制流。
3. 本机验证不证明真实版本完整、回滚有效或生产稳定性；release 四项真实阻断不变，`release/v1.0.0` 继续冻结。

## 389. 2026-09-12 配置审核列表读取状态分离 V1.0 验证

### 389.1 本轮补强

1. 九类审核申请模板均用对应错误状态约束普通空态，并仅在列表确有数据时渲染表格。
2. 有旧列表时错误提示明确为刷新失败并展示上次成功快照；首次加载失败时只显示加载错误，不再误报暂无申请。
3. 控制策略审核错误提示改用仓库现有 `error-banner`，移除未定义的 `error-box` 与内联间距补偿。

### 389.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 九类首次失败状态 | 静态检查模板空态条件 | 通过 | 错误存在且列表为空时不显示“暂无申请”或空表格 |
| 九类刷新失败状态 | 静态检查错误文案与表格条件 | 通过 | 旧列表保留并明确标识为上次成功快照 |
| 控制策略错误样式 | 搜索 `error-box` | 通过 | 未定义类已清除，统一使用 `error-banner` |
| 业务行为边界 | 检查代码差异 | 通过 | 未修改审核 GET、POST、列表提交或活动锁逻辑 |
| admin-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `71` 个模块转换；仅保留仓库既有单包超过 `500 kB` 提示 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |
| 浏览器组合场景 | 首次失败、刷新失败与恢复 | 待补 | 本机仍处于锁屏，未执行原生浏览器交互验证 |

### 389.3 当前判断

1. 九类审核列表已不再把读取失败误呈现为确定无待办，并能在刷新失败后保留可辨识旧快照。
2. 浏览器失败恢复仍需在本机解锁后补验；当前证据只覆盖生产构建、补丁检查与静态模板状态。
3. 本机验证不证明待办完整、审批可信或生产稳定性；release 四项真实阻断不变，`release/v1.0.0` 继续冻结。

## 390. 2026-09-12 指定告警回执回查编号边界 V1.0 验证

### 390.1 本轮补强

1. `PaymentIssueAlertDeliveryServiceImpl.reconcileDeliveryReceipt` 使用独立 `normalizeAlertNo` 规范化路径参数，不再只做非空与 `trim`。
2. 告警编号要求非空、原始值不含 ISO 控制字符，裁剪后长度不超过 `64`；校验先于指定告警 Mapper 查询。
3. 新增空白、65 字符和换行污染三类失败前置用例，并保留合法首尾空格裁剪的既有成功用例。

### 390.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 合法编号裁剪 | `shouldReconcileSpecifiedReceiptWithPersistedAttemptCount` | 通过 | ` PIA-IM-SINGLE ` 裁剪后查询并完成回查 |
| 非法编号失败前置 | `shouldRejectInvalidSpecifiedReceiptAlertNumberBeforeQueryingCandidate` | 通过 | 空白、65 字符、换行污染均不调用 Mapper |
| 服务定向测试 | `mvn -Dtest=PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `45` 个用例，`0` failures、`0` errors、`0` skipped |
| 后端全量测试 | `mvn test` | 通过 | `900` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 390.3 当前判断

1. 指定回执回查的告警编号已与数据库关键查询列容量一致，污染与超长输入不会进入 Mapper。
2. 本轮未改变批量回查、供应商适配器、回查限流、占用 CAS 或结果状态机。
3. 本地测试不证明真实供应商回执、通知送达、人员响应或生产稳定性；release 四项真实阻断不变，`release/v1.0.0` 继续冻结。

## 391. 2026-09-12 管理端动态 API 路径段编码 V1.0 验证

### 391.1 本轮补强

1. `paymentRecordApi.getDetail`、`paymentApi.getDetail` 与 `refundApi.getDetail` 对详情业务键执行 `encodeURIComponent`。
2. `paymentConfigApi.updateIssueDutyRoster` 对 `rosterCode` 执行相同编码，既有 PUT 方法和 JSON 请求体保持不变。
3. 查询参数继续由 `URLSearchParams` 管理，已编码的告警、死信、审核和版本路径保持不变。

### 391.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 三类详情路径 | Node mock `fetch` 调用客户端方法 | 通过 | `/ ? # %` 分别编码为 `%2F / %3F / %23 / %25` |
| 值班路由更新路径 | Node mock `fetch` 调用更新方法 | 通过 | 路径段编码，方法仍为 PUT，请求体仍为原 JSON |
| 动态路径全量扫描 | 搜索 `client.js` 模板字符串 | 通过 | 业务路径遗漏已补齐，查询串继续使用 `URLSearchParams` |
| admin-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `71` 个模块转换；仅保留仓库既有单包超过 `500 kB` 提示 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 391.3 当前判断

1. 四类动态路径不会再因保留字符改变 URL 结构，客户端与已有路径编码约定一致。
2. 路径编码只保证传输边界，服务端仍必须独立校验业务键并决定存在性、状态和操作资格。
3. mock 与构建不证明真实记录存在、退款到账、配置生效或生产稳定性；release 四项真实阻断不变，`release/v1.0.0` 继续冻结。

## 392. 2026-09-12 用户端收银台动态 API 路径段编码 V1.0 验证

### 392.1 本轮补强

1. `paymentApi.getCashier` 对 `prepayOrderNo` 执行 `encodeURIComponent`，避免预付单号改变收银台 API 请求结构。
2. `paymentApi.getDetail` 对 `paymentOrderId` 执行相同编码；app、H5 和 PC 三端复用 app-web 客户端，无需复制实现。
3. 新增 `src/api/client.test.js`，并将 API 测试纳入 app-web 既有 `npm test` 命令。

### 392.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 收银台与支付详情路径 | Node mock `fetch` 自动测试 | 通过 | `/ ? # %` 分别编码为 `%2F / %3F / %23 / %25` |
| app-web 测试 | `npm test` | 通过 | `9` 个测试，`0` fail、`0` skipped |
| app-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `33` 个模块转换 |
| h5-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `36` 个模块转换 |
| pc-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `37` 个模块转换 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 392.3 当前判断

1. 收银台与支付结果页的动态业务键不会再因保留字符改变 API URL 结构，三端共享同一客户端行为。
2. 编码只保证客户端传输边界；服务端继续独立判定预付单或支付单的字符、容量、存在性和业务状态。
3. 自动测试与构建不证明预付单存在、支付成功、资金到账或生产稳定性；release 四项真实阻断不变，`release/v1.0.0` 继续冻结。

## 393. 2026-09-12 用户端支付页面结构化路由 V1.0 验证

### 393.1 本轮补强

1. app、H5、PC 三端为收银台与支付结果页配置相同的命名路由 `cashier`、`payment-result`。
2. 两份业务入口、共享收银台支付提交/主动查单跳转及结果页返回收银台统一使用共享 `buildCashierRoute`、`buildPaymentResultRoute`。
3. 路由构造器使用 Vue Router `name / params / query` 对象，取消业务键手工拼接页面路径，并新增内存路由解析测试。

### 393.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 路径参数解析 | Vue Router memory history 自动测试 | 通过 | `/ ? # %` 编码为 `%2F / %3F / %23 / %25` |
| 页面查询参数 | Vue Router route location 对象解析 | 通过 | `bizType`、`prepayOrderNo` 与路径参数分离 |
| app-web 测试 | `npm test` | 通过 | `10` 个测试，`0` fail、`0` skipped |
| app-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `34` 个模块转换 |
| h5-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `37` 个模块转换 |
| pc-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `38` 个模块转换 |
| 用户端动态页面路径扫描 | `rg` 扫描 `cashier/${...}`、`payment-result/${...}` | 通过 | 页面代码无手工业务键路径拼接残留 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 393.3 当前判断

1. 三端支付页面导航由 Vue Router 统一处理动态业务键，页面路径与查询参数不再由业务组件手工拼装。
2. 本轮只收敛客户端导航边界，不改变支付动作、状态解析、API 契约或服务端校验。
3. 自动测试与构建不证明真实页面全链路、渠道扣款、资金到账或生产稳定性；release 四项真实阻断不变，`release/v1.0.0` 继续冻结。

## 394. 2026-09-12 管理端详情页面结构化路由 V1.0 验证

### 394.1 本轮补强

1. 管理端为支付单、支付记录和退款详情配置 `payment-detail`、`payment-record-detail`、`refund-detail` 命名路由。
2. 新增三个共享详情路由构造器，集中输出 `name / params / query` 对象；支付记录构造器保留 `recordType` 查询维度。
3. 列表入口、跨域追踪入口和详情互跳共 15 个页面改用结构化路由，取消三类详情路径的手工业务键拼接。

### 394.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 三类详情路径参数 | Vue Router memory history 自动测试 | 通过 | `/ ? # %` 编码为 `%2F / %3F / %23 / %25` |
| 支付记录维度查询 | `paymentRecordDetailRoute` 解析 | 通过 | `recordType=BANK_CARD` 与路径参数独立 |
| admin-web 自动测试 | `npm test` | 通过 | `1` 个测试，`0` fail、`0` skipped |
| admin-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `72` 个模块转换；仅保留既有单包超过 `500 kB` 提示 |
| 详情页面路径残留扫描 | `rg` 扫描三类路径模板字符串 | 通过 | 页面代码无手工详情业务键路径残留，API 客户端仍正确编码 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 394.3 当前判断

1. 管理端三类详情导航由 Vue Router 统一编码路径参数，入口之间复用同一构造规则。
2. 本轮只收敛详情页面导航，列表查询筛选字符串将在后续独立审查，不改变任何写动作或服务端契约。
3. 自动测试与构建不证明记录存在、支付或退款到账、账务一致或生产稳定性；release 四项真实阻断不变，`release/v1.0.0` 继续冻结。

## 395. 2026-09-12 管理端列表筛选结构化路由 V1.0 验证

### 395.1 本轮补强

1. 管理端为 11 个可联查列表配置命名路由，并新增对应 `name / query` 列表路由构造器。
2. 支付详情、支付记录详情、退款详情、账单、订单、监控、异常、告警、事件、请求、日志、路由、流水和收银台会话共 14 个页面改用结构化筛选导航。
3. 27 处原始 `?`、`&` 模板字符串被查询对象替代，既有查询字段名和值保持不变。

### 395.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 特殊字符查询值 | Vue Router memory history 自动测试 | 通过 | `&/#/%` 被保护，解析后的业务值保持原样 |
| 多字段账单筛选 | `billsRoute` 解析 | 通过 | `billNo` 与 `orderNo` 保持两个独立字段 |
| admin-web 自动测试 | `npm test` | 通过 | 详情、列表共 `2` 个测试，`0` fail、`0` skipped |
| admin-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `73` 个模块转换；仅保留既有单包超过 `500 kB` 提示 |
| 动态查询串残留扫描 | `rg` 扫描页面 `router.push` 与 `RouterLink` | 通过 | 页面代码无动态手工查询串残留 |
| 补丁检查 | `git diff --check` | 通过 | 无空白错误 |

### 395.3 当前判断

1. 管理端跨域联查统一通过 Vue Router 查询对象传递筛选字段，业务值不能再通过保留字符改变字段结构。
2. 本轮只改变页面导航构造，不改变目标页面读取查询、列表 API、筛选规则或任何写动作。
3. 自动测试与构建不证明筛选结果存在、支付或退款到账、账务一致或生产稳定性；release 四项真实阻断不变，`release/v1.0.0` 继续冻结。

## 396. 2026-09-12 管理端外部收银台链接治理 V1.0 验证

### 396.1 本轮补强

1. 新增 `buildAppWebCashierUrl`，集中构造管理端打开 app-web 收银台的绝对 URL。
2. 订单发起结果、订单列表、支付详情和收银台会话统一复用构造器；会话页不再使用指向 admin-web 当前源的相对路径。
3. app-web 基地址支持 `VITE_APP_WEB_BASE_URL`，本地回退 `http://127.0.0.1:5175`；协议限制为 HTTP(S)，预付单号按单一路径段编码。

### 396.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 特殊字符预付单号 | `buildAppWebCashierUrl` 自动测试 | 通过 | `/ ? # %` 编码为 `%2F / %3F / %23 / %25` |
| 可配置基础路径 | HTTPS 基地址含路径、查询和片段 | 通过 | 保留基础路径，清除遗留查询和片段 |
| 配置规范化 | 基地址首尾空格及多个尾斜杠 | 通过 | 输出单一规范收银台路径 |
| 非法输入 | 空白预付单号、`javascript:` 协议 | 通过 | 均在生成链接前拒绝 |
| admin-web 自动测试 | `npm test` | 通过 | 路由与外链共 `4` 个测试，`0` fail、`0` skipped |
| admin-web 生产构建 | `npm run build` | 通过 | Vite `6.4.3` 完成 `74` 个模块转换；仅保留既有单包超过 `500 kB` 提示 |
| 残留扫描与补丁检查 | `rg`、`git diff --check` | 通过 | 消费页面无硬编码基地址或原始路径拼接，无空白错误 |

### 396.3 当前判断

1. 管理端四类入口对 app-web 收银台使用同一可配置且受协议约束的绝对链接，预付单号不会改变 URL 结构。
2. 本轮只治理外部导航构造，不探测目标服务，也不改变收银台 API、支付动作或状态机。
3. 自动测试与构建不证明目标环境可达、预付单存在、支付成功、资金到账或生产稳定性；release 四项真实阻断不变，`release/v1.0.0` 继续冻结。

## 397. 2026-09-12 支付配置动态路径业务键边界 V1.0 验证

### 397.1 本轮补强

1. `PaymentConfigServiceImpl` 新增 `requireBusinessKey`，统一执行控制字符拒绝、首尾空格裁剪、必填和 `64` 字符上限。
2. 渠道、路由规则、支付协议、支付网关和控制策略的版本查询与回滚入口复用该规则；值班路由、通知供应商与路由规则更新路径同步收口。
3. 渠道、路由规则、支付协议、值班路由、返回码状态、返回码导入、支付网关、控制策略和通知供应商九类复核申请编号均在查询待办前校验。

### 397.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 动态路径业务键测试 | `mvn -Dtest=PaymentConfigBusinessKeyValidationTest,PaymentConfigServiceImplTest test` | 通过 | 新增 `2` 个、既有 `65` 个，共 `67` 个测试通过，非法输入不触达 Mapper |
| 后端全量测试 | `mvn test` | 通过 | Surefire XML 汇总 `902` 个用例，`0` failures、`0` errors、`0` skipped |
| 动态路径残留检查 | 服务实现与 Controller 路由交叉扫描 | 通过 | 五类版本/回滚、三类更新服务入口和九类复核待办查询均使用统一边界 |
| 补丁检查 | `git diff --check` | 通过 | Java、测试与文档无空白错误 |

### 397.3 当前判断

1. 支付配置动态路径业务键不再以无界字符串进入 Mapper，控制字符、空白和超长值在服务边界被确定拒绝。
2. 本轮不改变 Controller 路由、请求正文校验、审核事务、配置状态、Mapper SQL 或数据库结构。
3. 自动测试不证明配置存在、maker-checker 身份可信、真实渠道或通知配置有效，也不改变 release 四项真实阻断。

## 398. 2026-09-12 协议标记 locale 无关规范化 V1.0 验证

### 398.1 本轮补强

1. 配置导出分区由 `Locale.ROOT` 统一转为大写，避免 `issue_duty_rosters` 在土耳其语环境中产生带点大写字母并误判为非法分区。
2. 告警通知通道由 `Locale.ROOT` 统一转为大写，确保小写 `in_app` 在不同 JVM locale 下始终识别为站内通道且不被误派发。
3. 支付提交的 User-Agent 使用 `Locale.ROOT` 转为小写，确保 `iPhone / Mobile` 终端判断不受服务器区域设置影响。

### 398.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 土耳其语 locale 回归 | 三组新增单元测试 | 通过 | 覆盖值班路由导出、站内通道识别和 iPhone 移动端识别，并在 `finally` 恢复默认 locale |
| 受影响定向测试 | `mvn -Dtest=PaymentConfigServiceImplTest,PaymentIssueAlertDeliveryServiceImplTest,PaymentControllerTest test` | 通过 | `122` 个测试，`0` failures、`0` errors、`0` skipped |
| 后端全量测试 | `mvn test` | 通过 | Surefire XML 汇总 `905` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | Java、测试与文档无空白错误 |

### 398.3 当前判断

1. 三类协议标记规范化结果由协议本身决定，不再被部署主机默认 locale 改写。
2. 本轮不改变导出字段、告警派发状态机、支付提交请求结构、Mapper SQL 或数据库结构。
3. 自动测试不证明生产导出数据、真实通知送达、客户端终端声明可信或生产稳定性，也不改变 release 四项真实阻断。

## 399. 2026-09-12 告警派发业务时区统一 V1.0 验证

### 399.1 本轮补强

1. 告警补发冷却、新鲜度、防重放、供应商熔断和供应商限流的当前时间统一取 `Asia/Shanghai`，与数据库告警时间和业务配置口径保持一致。
2. JVM 默认时区不再参与上述窗口的起止判断，避免非上海部署节点把有效告警判为过期，或把过期告警继续外发。
3. 本轮只收口告警派发服务的时间基准，不改变窗口配置值、派发状态机、供应商选择、失败记录或租约语义。

### 399.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 非业务默认时区回归 | `PaymentIssueAlertDeliveryServiceImplTest#shouldEvaluateFreshnessWindowInBusinessTimezone` | 通过 | JVM 默认时区临时切换为 `Pacific/Honolulu`，上海时间下超过 `10` 分钟窗口的告警被正确拦截，并在 `finally` 恢复默认时区 |
| 告警派发定向测试 | `mvn -Dtest=PaymentIssueAlertDeliveryServiceImplTest test` | 通过 | `47` 个测试，`0` failures、`0` errors、`0` skipped |
| 后端全量测试 | `mvn test` | 通过 | Surefire XML 汇总 `906` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | Java、测试与文档无空白错误 |

### 399.3 当前判断

1. 五类告警窗口现按明确业务时区计算，部署节点默认时区不再改变派发判断结果。
2. 本轮不改变 Controller、DTO、Mapper SQL、数据库结构、告警路由配置或外部通知协议。
3. 自动测试不证明数据库或生产主机时钟正确、真实通知送达、供应商容量充足或生产稳定性，也不改变 release 四项真实阻断。

## 400. 2026-09-12 日终缺省业务日期时区统一 V1.0 验证

### 400.1 本轮补强

1. 手工日终请求未传 `bizDate` 时，服务固定使用 `Asia/Shanghai` 当前日期的前一日作为缺省业务日期。
2. 日终统计查询和批次 `bizDate` 使用同一解析结果，避免非上海部署节点在跨日时统计错误日期的数据。
3. 显式 `bizDate` 的裁剪、`yyyy-MM-dd` 解析、运行方式和触发人校验均保持原语义。

### 400.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 动态异时区回归 | `PaymentDayEndServiceImplTest#shouldResolveDefaultBizDateInBusinessTimezone` | 通过 | 动态选择与上海当日不同的 JVM 默认时区，验证批次业务日期仍为上海当日前一日，并在 `finally` 恢复默认时区 |
| 日终服务定向测试 | `mvn -Dtest=PaymentDayEndServiceImplTest test` | 通过 | `6` 个测试，`0` failures、`0` errors、`0` skipped |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `907` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | Java、测试与文档无空白错误 |

### 400.3 当前判断

1. 日终缺省业务日期不再受部署节点默认时区影响，统计查询与批次记录保持同一上海日期口径。
2. 本轮不改变 Controller、请求 DTO、显式日期规则、Mapper SQL、数据库结构或对账准入逻辑。
3. 自动测试不证明历史时间数据、数据库或生产主机时钟正确，也不证明真实对账和生产稳定性，不改变 release 四项真实阻断。

## 401. 2026-09-12 支付成功事件批次日期时区统一 V1.0 验证

### 401.1 本轮补强

1. HTTP 清分支付成功载荷和 AMQP 支付成功载荷的 `batchDate` 统一由 `currentBusinessDate()` 生成，固定使用 `Asia/Shanghai` 当前日期。
2. 两个下游路径不再分别读取 JVM 默认日期，避免同一事件在非上海部署节点跨日时归入错误清分批次。
3. 账务 HTTP 载荷既有契约不包含 `batchDate`，本轮不新增字段，也不改变事件重试、死信或发布确认语义。

### 401.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 动态异时区回归 | `PaymentEventDispatchServiceImplTest#shouldBuildClearingBatchDateInBusinessTimezone` | 通过 | 动态选择与上海当日不同的 JVM 默认时区，验证 HTTP 清分载荷仍携带上海当日，并在 `finally` 恢复默认时区 |
| 事件派发定向测试 | `mvn -Dtest=PaymentEventDispatchServiceImplTest test` | 通过 | `46` 个测试，`0` failures、`0` errors、`0` skipped |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `908` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | Java、测试与文档无空白错误 |

### 401.3 当前判断

1. 支付成功事件的清分批次日期不再被部署节点默认时区改写，HTTP 与 AMQP 路径保持同一上海日期口径。
2. 本轮不改变事件类型、端点、消息目的地、载荷其他字段、Mapper SQL、数据库结构、重试或死信状态机。
3. 自动测试不证明真实 MQ 发布、下游受理、清分入账、资金一致或生产稳定性，也不改变 release 四项真实阻断。

## 402. 2026-09-12 退款业务编号时区统一 V1.0 验证

### 402.1 本轮补强

1. 退款单号 `REF` 与操作日志号 `ROL` 的 `yyyyMMddHHmmssSSS` 时间前缀固定使用 `Asia/Shanghai`。
2. 编号仍保留原业务前缀和随机 UUID 前 8 位后缀，长度、字符集及唯一性策略不变。
3. 同一静态生成器服务退款申请和操作日志，避免两个编号路径出现不同时间口径。

### 402.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 动态异时区回归 | `RefundServiceImplTest#shouldBuildRefundBusinessNoInBusinessTimezone` | 通过 | 动态选择与上海当日不同的 JVM 默认时区，验证 `ROL` 编号仍以上海业务日期开头，并在 `finally` 恢复默认时区 |
| 退款服务定向测试 | `mvn -Dtest=RefundServiceImplTest test` | 通过 | `39` 个测试，`0` failures、`0` errors、`0` skipped |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `909` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | Java、测试与文档无空白错误 |

### 402.3 当前判断

1. 退款单号和操作日志号的时间前缀不再被部署节点默认时区改写，业务可读日期保持上海口径。
2. 本轮不改变退款申请、审核、渠道提交、回调、查单、事件、Mapper SQL、数据库结构或编号唯一性策略。
3. 自动测试不证明真实退款受理、资金退回、到账核验或生产稳定性，也不改变 release 四项真实阻断。

## 403. 2026-09-12 API 请求追踪号时区统一 V1.0 验证

### 403.1 本轮补强

1. `RequestIdHolder` 的时间格式器固定使用 `Asia/Shanghai`，不再通过 `ZoneId.systemDefault()` 在类加载时缓存部署节点时区。
2. 请求号继续采用 `REQ + yyyyMMddHHmmssSSS + 8位UUID`，格式、长度、字符集与并发唯一性策略不变。
3. `ApiResponse.success/failure` 继续使用同一生成器，不改变任何业务响应字段或错误处理路径。

### 403.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 隔离类加载异时区回归 | `RequestIdHolderTest#shouldGenerateRequestIdInBusinessTimezone` | 通过 | 在动态选择的异时区下用独立类加载器重新初始化生成器，验证请求号仍以上海业务日期开头，并在 `finally` 恢复默认时区 |
| 并发唯一性回归 | `RequestIdHolderTest#shouldGenerateUniqueRequestIdsConcurrently` | 通过 | 并行生成 `10,000` 个请求号，集合无重复且全部符合既有格式 |
| 请求号定向测试 | `mvn -Dtest=RequestIdHolderTest test` | 通过 | `2` 个测试，`0` failures、`0` errors、`0` skipped |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `910` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | Java、测试与文档无空白错误 |

### 403.3 当前判断

1. API 请求追踪号时间前缀不再受部署节点默认时区或类加载时点影响，统一保持上海业务时间口径。
2. 本轮不改变端点、响应 DTO、业务状态、Mapper SQL、数据库结构、日志关联或异常映射。
3. 自动测试不证明请求真实来源、业务完成、审计审批或生产稳定性，也不改变 release 四项真实阻断。

## 404. 2026-09-12 三端预付金额精度保护 V1.0 验证

### 404.1 本轮补强

1. APP 自有入口和 H5/PC 共用入口统一通过 `normalizePaymentAmountInput` 处理金额，不再调用 `Number(form.value.amount)`。
2. 金额工具按字符串解析十进制整数和最多两位小数，裁剪空白、移除无意义前导零、将 `.50` 规范为 `0.50`，并拒绝非正数、指数形式和超过 16 位整数的输入。
3. `paymentApi.prepay` 继续使用原请求字段，金额以 JSON 字符串发出；Spring/Jackson 将该字符串精确绑定到现有 `PrepayRequestDTO.amount BigDecimal`，后端金额容量校验保持不变。

### 404.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 前端金额工具 | `npm test`（app-web） | 通过 | APP 测试共 `12` 个；新增覆盖 `9999999999999999.99` 精确保留及非法金额拒绝 |
| 管理端前端测试 | `npm test`（admin-web） | 通过 | `4` 个路由与外链安全测试全部通过 |
| 四端生产构建 | 分别执行 APP/H5/PC/admin-web `npm run build` | 通过 | 四端均成功产出；管理端仍报告既存的单包超过 `500 kB` 告警，不作为性能验收通过 |
| 后端金额绑定与支付服务定向测试 | `mvn -Dtest=PrepayRequestDTOJsonTest,PaymentServiceImplTest test` | 通过 | `74` 个测试，最大合法金额字符串精确绑定为 `BigDecimal` |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `911` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 前端、Java 测试与文档无空白错误 |

### 404.3 当前判断

1. 三端预付入口不再因 JavaScript 二进制浮点表示改写用户输入金额，前端与后端的 16 位整数、2 位小数容量口径一致。
2. 本轮不改变预付端点、请求字段、后端金额状态机、Mapper SQL、数据库结构、渠道提交或风控逻辑。
3. 自动测试和构建不证明真实渠道扣款、下游入账、资金一致、浏览器兼容性全量覆盖或生产稳定性，也不改变 release 四项真实阻断。

## 405. 2026-09-12 管理端退款申请金额精度保护 V1.0 验证

### 405.1 本轮补强

1. 管理端退款申请统一通过本地 `normalizePaymentAmountInput` 处理金额，不再调用 `Number(applyForm.value.refundAmount)`。
2. 金额工具按字符串解析十进制整数和最多两位小数，裁剪空白、移除无意义前导零、将 `.50` 规范为 `0.50`，并拒绝非正数、指数形式和超过 16 位整数的输入。
3. `refundApi.apply` 继续使用原请求字段，金额以 JSON 字符串发出；Spring/Jackson 将该字符串精确绑定到现有 `RefundApplyRequestDTO.refundAmount BigDecimal`，后端金额与累计退款额度校验保持不变。

### 405.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 管理端金额工具与既有路由测试 | `npm test`（admin-web） | 通过 | 共 `6` 个测试；新增覆盖 `9999999999999999.99` 精确保留及非法金额拒绝 |
| APP 既有前端测试 | `npm test`（app-web） | 通过 | `12` 个测试全部通过 |
| 四端生产构建 | 分别执行 APP/H5/PC/admin-web `npm run build` | 通过 | 四端均成功产出；管理端仍报告既存的 `537.41 kB` 单包告警，不作为性能验收通过 |
| 后端退款定向测试 | `mvn -Dtest=RefundApplyRequestDTOJsonTest,RefundServiceImplTest test` | 通过 | `40` 个测试，最大合法退款金额字符串精确绑定为 `BigDecimal` |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `912` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 前端、Java 测试与文档无空白错误 |

### 405.3 当前判断

1. 管理端退款申请不再因 JavaScript 二进制浮点表示改写用户输入金额，前端与后端的 16 位整数、2 位小数容量口径一致。
2. 本轮不处理列表金额合计的展示精度或支付配置金额，不改变退款端点、请求字段、状态机、Mapper SQL、数据库结构、渠道提交或额度并发控制。
3. 自动测试和构建不证明真实退款渠道受理、资金退回、到账核验、浏览器兼容性全量覆盖或生产稳定性，也不改变 release 四项真实阻断。

## 406. 2026-09-12 支付渠道单日限额精度保护 V1.0 验证

### 406.1 本轮补强

1. 管理端渠道表单的 `dailyLimit` 不再使用 `v-model.number`，候选构造也不再调用 `Number(channelForm.value.dailyLimit)`；允许零值的金额工具以十进制字符串完成校验和提交。
2. `PaymentChannelConfigDTO.dailyLimitValue` 的 Java 类型继续为 `BigDecimal`，通过 Jackson 字段级序列化将 JSON 表示收敛为精确字符串，避免编辑最大合法限额时在响应解析阶段丢失精度。
3. Spring/Jackson 将候选中的限额字符串精确绑定回 `PaymentChannelUpsertRequestDTO.dailyLimit BigDecimal`；后端提交阶段容量校验、批准前复验、候选快照和版本基线保持不变。

### 406.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 管理端金额工具与既有路由测试 | `npm test`（admin-web） | 通过 | 共 `7` 个测试；覆盖零额度、最大合法值、负数、三位小数和超上限拒绝 |
| APP 既有前端测试 | `npm test`（app-web） | 通过 | `12` 个测试全部通过 |
| 四端生产构建 | 分别执行 APP/H5/PC/admin-web `npm run build` | 通过 | 四端均成功产出；管理端仍报告既存的 `537.58 kB` 单包告警，不作为性能验收通过 |
| 后端渠道金额定向测试 | `mvn -Dtest=PaymentChannelAmountJsonTest,PaymentChannelStatusReviewServiceImplTest test` | 通过 | `24` 个测试；覆盖最大限额响应字符串与候选请求精确 `BigDecimal` 绑定 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `914` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 前端、Java、测试与文档无空白错误 |

### 406.3 当前判断

1. 渠道单日限额从后端响应、管理端编辑到候选请求均保持十进制定点语义，零额度和最大合法值不会被 JavaScript 二进制浮点改写。
2. 仓库扫描确认 `dailyLimitValue` 仅由管理端渠道编辑表单消费并已同步适配；其他自行绑定该响应字段为 JSON 数字的外部消费者需改为接收字符串。
3. 本轮不处理路由试算金额或汇总展示，不改变数据库类型、Mapper SQL、渠道复核事务或运行态应用；自动测试不证明审批身份可信、真实渠道容量或生产稳定性，也不改变 release 四项真实阻断。

## 407. 2026-09-12 支付路由试算样例金额精度保护 V1.0 验证

### 407.1 本轮补强

1. 管理端路由试算表单的 `sampleAmount` 不再使用 `v-model.number`，请求构造也不再调用 `Number(...)`；允许零值的金额工具以十进制字符串完成校验和提交。
2. `PaymentConfigServiceImpl.validateRouteRule` 对样例金额增加服务端容量复验：`null` 保留既有按零求值语义，零值合法，负数、超过两位有效小数或超过 `9999999999999999.99` 时拒绝。
3. Spring/Jackson 将最大合法样例金额字符串精确绑定为 `PaymentRouteRuleValidationRequestDTO.amount BigDecimal`；试算只读取请求上下文，不写路由规则、支付单或资金事实。

### 407.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 管理端金额工具与既有路由测试 | `npm test`（admin-web） | 通过 | 共 `7` 个测试；覆盖零值、最大合法值、负数、三位小数和超上限拒绝 |
| APP 既有前端测试 | `npm test`（app-web） | 通过 | `12` 个测试全部通过 |
| 四端生产构建 | 分别执行 APP/H5/PC/admin-web `npm run build` | 通过 | 四端均成功产出；管理端仍报告既存的 `537.69 kB` 单包告警，不作为性能验收通过 |
| 后端路由试算定向测试 | `mvn -Dtest=PaymentRouteRuleValidationAmountJsonTest,PaymentConfigServiceImplTest test` | 通过 | 合计 `68` 个测试；覆盖 `null`、零值、最大合法尾随零及三类非法金额，并验证最大字符串精确绑定 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `916` 个用例，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` | 通过 | 前端、Java、测试与文档无空白错误 |

### 407.3 当前判断

1. 路由试算样例金额从管理端输入到服务端 `BigDecimal` 保持十进制定点语义，并与真实支付金额的 16 位整数、2 位小数容量边界对齐。
2. `null` 按零求值的历史兼容语义、路由表达式计算和规则治理流程保持不变；本轮不改变路由规则持久化、maker-checker、运行态、Mapper SQL 或数据库结构。
3. 本地试算、自动测试与构建不证明真实支付路由、渠道受理、资金结果、浏览器兼容性全量覆盖或生产稳定性，也不改变 release 四项真实阻断。

## 408. 2026-09-12 支付渠道版本限额展示精度保护 V1.0 验证

### 408.1 本轮补强

1. `PaymentChannelVersionDTO.dailyLimit` 的 Java 类型和数据库来源继续为 `BigDecimal`，通过 Jackson 字段级序列化将版本历史接口中的 JSON 表示收敛为精确十进制字符串。
2. 管理端渠道版本历史只通过模板插值展示 `version.dailyLimit`，可直接消费字符串；仓库扫描确认回滚请求仅提交 `versionNo`，服务端从不可变版本表读取完整快照。
3. 本轮不引入全局金额序列化策略，不改变渠道版本生成、复核、回滚、运行态应用或持久化路径。

### 408.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端渠道金额定向测试 | `mvn -Dtest=PaymentChannelAmountJsonTest,PaymentConfigServiceImplTest test` | 通过 | 合计 `70` 个测试；新增断言版本最大限额 JSON 节点为字符串且文本完全相等 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `917` 个用例，`0` failures、`0` errors、`0` skipped |
| 消费者与回滚路径扫描 | `rg` 检查 `PaymentChannelVersionDTO`、`channelVersions` 与 `versionNo` | 通过 | 管理端只展示限额；回滚只提交版本号并由服务端读取版本实体 |
| 补丁检查 | `git diff --check` | 通过 | Java DTO、测试与文档无空白错误 |

### 408.3 当前判断

1. 渠道运行配置编辑值和不可变版本历史限额均以十进制字符串跨越 JavaScript 边界，最大合法值不会在管理端展示前被二进制浮点改写。
2. 本轮仅改变版本历史响应字段的 JSON 表示，不改变 Java 类型、数据库类型、版本快照、maker-checker、回滚事务或运行态应用。
3. 自动测试与代码扫描不证明历史配置真实有效、回滚审批可信、真实渠道容量或生产稳定性，也不改变 release 四项真实阻断。

## 409. 2026-09-12 管理端当前页金额合计精度保护 V1.0 验证

### 409.1 本轮补强

1. 支付列表与退款列表不再对 `¥` 前缀金额字符串直接调用 `Number(...)`，修复非空页面合计显示 `NaN` 的问题。
2. 统一支付记录移除清洗后转 JavaScript `Number` 的 `parseMoney`，三个页面统一使用 `sumPaymentAmounts` 对“分”的数字字符串逐位相加并固定输出两位小数。
3. 工具接受接口既有 `¥` 前缀、千分位和纯数字金额，空值或无效展示值按零忽略；不引入第三方十进制库，也不改变单行金额显示。

### 409.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 管理端金额与路由测试 | `npm test`（admin-web） | 通过 | 共 `8` 个测试；新增精确合计覆盖 `0.10 + 0.20`、千分位、最大金额相加和无效值容错 |
| 管理端生产构建 | `npm run build`（admin-web） | 通过 | 成功产出；仍报告既存的 `538.03 kB` 单包告警，不作为性能验收通过 |
| 旧转换路径扫描 | `rg` 检查三个页面的 `Number(...)`、`parseMoney` 与 `toFixed(2)` | 通过 | 已无旧金额合计路径，非金额整型转换不在本轮范围 |
| 补丁检查 | `git diff --check` | 通过 | 工具、测试、页面与文档无空白错误 |

### 409.3 当前判断

1. 三个管理端列表的当前页金额合计不再受货币符号解析失败或 JavaScript 二进制浮点误差影响，大额相加可超过单笔 16 位整数容量而保持精确文本。
2. 本轮未改变服务端分页、单行展示金额格式、接口字段、Mapper SQL、数据库或任何支付/退款状态机；其他整型表单中的 `Number(...)` 保持原状。
3. 当前页展示合计不等于全量业务汇总，也不作为账务、对账、结算、渠道资金或生产稳定性证明，不改变 release 四项真实阻断。

## 410. 2026-09-12 管理端金额合计分页范围显式化 V1.0 验证

### 410.1 本轮补强

1. 支付列表卡片标题由“实付金额合计”改为“本页实付金额”，退款列表由“退款金额合计”改为“本页退款金额”。
2. 统一支付记录保留“本页已退金额”，三个页面均在用户可见标题中明确当前分页范围，与实际对 `items` 的求和实现一致。

### 410.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 管理端测试 | `npm test`（admin-web） | 通过 | `8` 个测试全部通过 |
| 管理端生产构建 | `npm run build`（admin-web） | 通过 | 成功产出；仍报告既存的 `538.03 kB` 单包告警 |
| 标题口径扫描 | `rg` 检查三个列表页面 | 通过 | 分别为“本页实付金额”“本页退款金额”“本页已退金额”，旧无范围标题已移除 |
| 补丁检查 | `git diff --check` | 通过 | 页面与文档无空白错误 |

### 410.3 当前判断

1. 用户可见标题与当前分页求和范围一致，降低将当前页金额误判为全量汇总的风险。
2. 本轮没有改变计算逻辑、服务端查询、分页、API、数据库或状态机。
3. 页面标题和当前页合计不作为全量报表、账务、对账、结算或生产资金证明，也不改变 release 四项真实阻断。

## 411. 2026-09-12 管理端状态指标分页范围显式化 V1.0 验证

### 411.1 本轮补强

1. 支付列表的“支付成功、待回调”，退款列表的“待审核、退款失败”和待处理徽标，以及统一支付记录的“支付成功、已关闭”统一增加“本页”前缀。
2. 上述指标继续由当前 `items/rows` 计算；“支付单总数、退款单总数、记录总数”继续使用分页响应 `total`，不改变全量记录数口径。

### 411.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 管理端测试 | `npm test`（admin-web） | 通过 | `8` 个测试全部通过 |
| 管理端生产构建 | `npm run build`（admin-web） | 通过 | 成功产出；仍报告既存的 `538.07 kB` 单包告警 |
| 状态标题扫描 | `rg` 检查三个列表页面 | 通过 | 六个状态卡片和一个徽标均显式显示“本页” |
| 补丁检查 | `git diff --check` | 通过 | 页面与文档无空白错误 |

### 411.3 当前判断

1. 页面已清晰区分服务端全量记录数与客户端当前页状态计数，避免运营人员横向比较不同范围的指标。
2. 本轮不改变计数逻辑、分页、接口、数据库或支付退款状态机。
3. 本页状态指标不作为全量运营统计、SLA、资金结果或生产稳定性证明，也不改变 release 四项真实阻断。

## 412. 2026-09-12 退款申请客户端幂等保护 V1.0 验证

### 412.1 本轮补强

1. 退款申请 DTO 新增必填 `idempotencyKey`；服务在锁定原支付单后查询 `paymentOrderId + idempotencyKey`，同键同规范化金额、方式、原因和操作人时返回原退款单，不重复汇总额度、插入退款单或申请日志。
2. 同键异参返回 `PAYMENT-1053`；空白、超过 `128` 字符或含 ISO 控制字符返回 `PAYMENT-1052`，并在任何退款 Mapper 访问前失败。
3. 管理端按规范化请求内容保留失败重试键，字段或操作人变化后生成新键，成功后清空；数据库新增复合唯一索引，双份初始化基线及历史占位回填迁移保持一致。

### 412.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 退款幂等定向测试 | `mvn -Dtest=RefundServiceImplTest,RefundApplicationConcurrencyIntegrationTest,RefundApplyRequestDTOJsonTest test` | 通过 | `46` 个用例；覆盖同键重放、四类异参冲突、非法键零 Mapper 访问、JSON 绑定、异键额度竞争及同键并发单行落库 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `921` 个用例，`0` failures、`0` errors、`0` skipped |
| 管理端测试与构建 | `npm test`、`npm run build`（admin-web） | 通过 | `8` 个测试通过并成功产出；仍报告既存的 `538.56 kB` 单包告警 |
| 结构与补丁检查 | 双 schema/data 片段比对、`xmllint --noout RefundMapper.xml`、`git diff --check` | 通过 | 两套初始化基线一致，Mapper XML 合法，无空白错误 |
| 真实环境验证 | MySQL 在线 DDL、多实例锁等待、调用方重试、真实渠道退款与资金核验 | 未执行 | 不记录生产迁移、渠道幂等、到账、对账或稳定性通过 |

### 412.3 当前判断

1. 客户端网络重试不再仅依赖渠道下发层的稳定 `refundOrderId`；申请层与渠道层分别使用 `idempotencyKey` 和 `refundOrderId`，职责清晰。
2. 历史 `LEGACY:<refundOrderId>` 仅用于满足非空和唯一约束，不表示历史调用方曾提供幂等键，也不改变历史退款事实。
3. 本地自动测试不能替代 MySQL 在线迁移、多实例事务、真实渠道受理、退款到账与财务核验，release 四项真实阻断不变。

## 413. 2026-09-12 管理端退款幂等重试意图 V1.1 验证

### 413.1 本轮补强

1. 新增 `resolveRefundApplicationAttempt` 纯函数，以规范化退款申请载荷的稳定 JSON 指纹决定复用原幂等键或生成新键，页面继续只持有当前申请状态。
2. 测试直接覆盖首次请求、未变化失败重试，以及支付单号、退款金额、退款方式、退款原因和操作人五类内容变化。
3. 申请响应提示使用服务端返回的 `refundOrderId + status`，避免幂等重放返回已流转原单时仍固定宣称“等待审核”。

### 413.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 管理端测试 | `npm test`（admin-web） | 通过 | `11` 个测试；新增 `3` 个退款申请意图用例，覆盖首次生成、同内容复用及五类内容变化换键 |
| 管理端生产构建 | `npm run build`（admin-web） | 通过 | 成功产出；仍报告既存的 `538.77 kB` 单包告警 |
| 补丁检查 | `git diff --check` | 通过 | 页面、纯函数、测试与文档无空白错误 |
| 后端全量测试 | 未重复执行 | 未执行 | 本轮无 Java、Mapper、schema、迁移或后端资源变化；沿用上一轮 `921` 项通过证据，不扩大证明范围 |
| 真实环境验证 | 浏览器刷新、网络超时/响应丢失、真实 API 与渠道资金链路 | 未执行 | 不记录生产重试、渠道受理、退款到账或稳定性通过 |

### 413.3 当前判断

1. 同页面会话内的退款失败重试意图已有可执行测试，申请内容变化不会错误复用旧键，幂等重放提示也与服务端当前事实一致。
2. 当前键仍为页面内存状态，刷新恢复不在本轮范围；若后续持久化，必须设计过期和“明确新申请”边界，避免永久复用旧请求身份。
3. 前端测试与构建不证明真实网络、服务端数据库、渠道退款或资金到账，release 四项真实阻断不变。

## 414. 2026-09-12 支付回调终态并发保护 V1.0 验证

### 414.1 本轮补强

1. 非成功支付回调不再通过无条件 `updatePaymentStatus` 写入 `WAIT_CALLBACK`，改由 Mapper 条件更新并排除 `SUCCESS / CLOSED`。
2. 若初次查询后已有并发路径先完成成功或关闭收口，条件更新返回 `0`，服务读取并返回最新详情，不再更新支付尝试、预付单或写 `PAYMENT_PENDING` 事件。
3. 保留既有回调签名、渠道与渠道流水匹配、非成功状态文本及通知处理语义，不新增 API 字段、错误码、schema、索引或迁移。

### 414.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 回调终态定向测试 | `mvn -Dtest=PaymentServiceImplTest,PaymentMapperStateTransitionIntegrationTest test` | 通过 | `82` 个用例；覆盖 Service 读后并发成功、Mapper 待确认正常写入及 `SUCCESS / CLOSED` 终态拒绝覆盖 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `923` 个用例，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `xmllint --noout PaymentMapper.xml`、`git diff --check` | 通过 | 正式 Mapper XML 合法，代码、测试和文档无空白错误 |
| 真实环境验证 | MySQL 多连接竞态、多实例回调/查单乱序、真实渠道通知与资金核验 | 未执行 | 不记录生产终态保护、渠道交易或资金一致性通过 |

### 414.3 当前判断

1. `SUCCESS / CLOSED` 的终态保护已由数据库条件更新承担，不再依赖 Service 初次读取结果，消除了已确认终态被晚到非成功回调倒退的窗口。
2. 本轮没有收窄 `tradeStatus` 文本集合；真实渠道状态映射仍需结合正式协议适配单独治理，不能由本修复推断已完成。
3. 本地自动测试不替代生产 MySQL 隔离级别、多实例时序、真实渠道乱序通知、账务和对账核验，release 四项真实阻断不变。

## 415. 2026-09-12 支付渠道提交事实与前端查单资格 V1.0 验证

### 415.1 本轮补强

1. 回调与主动查单在外部调用和资金副作用前统一校验：终态 `SUCCESS / CLOSED` 可刷新，其他状态仅 `WAIT_CALLBACK` 且最近支付尝试非空时允许；不满足时返回 `PAYMENT-1054`。
2. 正式 Mapper 的普通成功、待确认状态更新均要求支付尝试存在；支付列表与收银台响应暴露最小只读提交事实，预付单创建/复用路径保持原查询依赖。
3. 管理端支付列表、支付详情、支付记录详情，以及用户端收银台、结果页同时加入按钮资格和动作函数兜底；页面不再以“支付单号存在”或“主动查单始终可用”替代渠道提交事实。

### 415.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `mvn -Dtest=PaymentServiceImplTest,PaymentMapperSqlBindingSafetyTest,PaymentMapperStateTransitionIntegrationTest,PaymentPrepayCreationConcurrencyIntegrationTest test` | 通过 | `90` 项；覆盖零副作用资格拒绝、正式 SQL 最新尝试投影及预付并发路径回归 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `927` 项，`0` failures、`0` errors、`0` skipped |
| 管理端测试与构建 | `npm test`、`npm run build`（admin-web） | 通过 | `12` 项测试通过并成功产出；仍报告既存的 `539.75 kB` 单包告警 |
| 用户端测试与构建 | `npm test`、`npm run build`（app-web） | 通过 | `13` 项测试通过，APP/H5/PC 共用构建成功产出 |
| Mapper 与补丁检查 | `xmllint --noout PaymentMapper.xml`、`git diff --check` | 通过 | Mapper XML 合法，无空白错误 |
| 真实环境验证 | MySQL 多连接、多实例回调/查单、真实渠道适配与资金核验 | 未执行 | 不记录渠道受理、查询成功、资金一致或生产稳定性通过 |

### 415.3 当前判断

1. 客户端五个入口与服务端权威门禁已使用同一资格口径，无渠道提交事实时既不开放操作，也不能通过事件函数绕过。
2. `PaymentListItemDTO.latestAttemptStatus` 与 `CashierPageDTO.paymentStatus / latestAttemptStatus` 只用于展示和动作资格，不是新的渠道受理凭证；权威事实仍由服务端数据库与状态迁移判断。
3. 本地自动化不替代真实渠道协议、MySQL 多实例竞态、账务、对账和生产核验，release 四项真实阻断不变。

## 416. 2026-09-12 收银台首次加载提交事实复用 V1.1 验证

### 416.1 本轮补强

1. `submissionPending` 在没有最新查单详情时读取收银台响应的 `latestAttemptStatus`，防止异常待支付展示状态下重新生成幂等键或再次提交。
2. `canClose` 使用同一回退事实，已存在渠道尝试时不开放本地关闭；查单详情存在后继续以详情中的尝试与渠道流水为准。

### 416.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 用户端测试 | `npm test`（app-web） | 通过 | `13` 项；既有纯函数用例覆盖尝试处理中锁提交和存在尝试禁止关闭 |
| 用户端生产构建 | `npm run build`（app-web） | 通过 | APP/H5/PC 共用构建成功产出 |
| 补丁检查 | `git diff --check` | 通过 | 页面与文档无空白错误 |
| 后端回归 | 未重复执行 | 未执行 | 本轮无 Java、Mapper、资源、schema 或迁移变化；不扩大上一轮 `927` 项证据范围 |

### 416.3 当前判断

1. 收银台获取的新只读提交事实已完整参与查单、重复提交和本地关闭三类资格，不再只服务于主动查单按钮。
2. 浏览器派生状态仅用于提前反馈，不能替代服务端并发令牌、支付尝试、关单条件和渠道查询资格校验。
3. 本地测试与构建不证明真实浏览器网络时序、渠道受理、资金结果或生产稳定性，release 四项真实阻断不变。

## 417. 2026-09-12 失败退款重试渠道轨迹重置 V1.0 验证

### 417.1 本轮补强

1. `FAIL -> PROCESSING` 改用专用条件更新，同时清空上一轮当前 `channelCode / channelRefundNo / channelSubmittedAt / successAt`，避免重新激活的退款提交事件误把旧受理号当成本轮已提交事实。
2. 人工重试与任务中心重试共用该路径；状态迁移、当前轨迹重置、重试日志和 outbox 重新激活仍处于同一事务，提交后才沿既有机制即时派发。
3. 退款终态回调与主动查单并发审计未发现额外缺口：行锁重读和 `PROCESSING -> SUCCESS/FAIL` 条件更新已保证单一终态胜者，重复同终态不再写副作用。

### 417.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `mvn -Dtest=RefundServiceImplTest,PaymentEventDispatchServiceImplTest,RefundSubmitOutboxMapperIntegrationTest test` | 通过 | `95` 项；覆盖人工/任务重试选用专用更新、空轨迹进入渠道提交，以及正式 SQL 清轨迹和重复执行零命中 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `928` 项，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `xmllint --noout RefundMapper.xml`、`git diff --check` | 通过 | Mapper XML 合法，无空白错误 |
| 真实环境验证 | MySQL 多连接、真实渠道重复退款协议、退款到账与对账 | 未执行 | 不记录渠道重试受理、资金退回或生产稳定性通过 |

### 417.3 当前判断

1. 失败退款重试不再被上一轮渠道退款号短路，代码语义与“重新下发退款渠道”的产品动作一致。
2. `t_refund_order` 仍只保存当前一次受理轨迹，既有操作日志不删除但尚无独立多尝试结构；完整退款尝试历史属于后续数据模型增强。
3. 本地自动化不替代真实渠道幂等规则、生产 MySQL 事务竞态、资金到账和财务对账核验，release 四项真实阻断不变。

## 418. 2026-09-12 退款提交未知渠道事实保留 V1.0 验证

### 418.1 本轮补强

1. 退款渠道已经发起调用但返回结果未知时，将实际请求的 `channelCode` 写入仍为 `PROCESSING` 且无渠道退款号的退款单，补齐主动查单和自动补偿所需的渠道定位事实。
2. 该路径不写 `channelRefundNo / channelSubmittedAt / successAt`，不迁移退款终态；`REFUND_SUBMIT` 事件仍进入 `UNKNOWN` 并停止直接重发，等待可信查单或验签回调收口。
3. 明确在调用上游前被配置或治理门禁拒绝时继续进入可重试失败路径，不记录未知渠道；既有非法受理轨迹分支受 SQL 条件保护，不覆盖当前渠道事实。

### 418.2 验证结果

| 验证项 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端定向测试 | `mvn -Dtest=PaymentEventDispatchServiceImplTest,RefundSubmitOutboxMapperIntegrationTest,RefundServiceImplTest,PaymentTaskCenterMapperIntegrationTest test` | 通过 | `110` 项；覆盖未知结果写实际渠道、调用前拒绝不写渠道、正式 SQL 从空渠道进入未知受理恢复 |
| 后端全量测试 | `mvn test` | 通过 | Surefire 汇总 `928` 项，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `xmllint --noout RefundMapper.xml`、`git diff --check` | 通过 | Mapper XML 合法，无空白错误 |
| 真实环境验证 | MySQL 多连接、真实渠道未知结果查单、回调、到账与对账 | 未执行 | 不记录渠道受理、资金退回或生产稳定性通过 |

### 418.3 当前判断

1. 生产代码产生的退款提交未知态已具备渠道定位事实，可进入按平台退款单号主动查单及任务候选，不再因 `channelCode` 为空永久滞留。
2. `channelCode` 只证明平台实际调用了哪个渠道，不证明渠道收到、受理或完成退款；渠道退款号、受理时间和终态仍须可信外部结果形成。
3. 本地自动化不替代真实网关超时语义、渠道按商户退款单号查单能力、生产 MySQL 竞态、退款到账和财务对账核验，release 四项真实阻断不变。

## 419. 2026-09-12 退款渠道多尝试事实 V1.0 验证

### 419.1 本轮补强

1. 新增 `t_refund_attempt` 与迁移回填，使用唯一 `refundAttemptNo` 贯穿 outbox、渠道提交、主动查单和 gateway-access 上游请求；`refundOrderId` 继续承担平台退款单归属。
2. 调用前失败将当前尝试标记为 `FAILED` 并复用原尝试号重发；渠道调用结果未知将尝试和事件标记为 `UNKNOWN` 并停止直接重发；只有退款终态失败后的业务重试才创建新尝试号。
3. 渠道已受理事实先绑定尝试再投影退款单；终态只允许从 `ACCEPTED` 收口。可信查单或验签回调会先恢复 UNKNOWN 受理事实，再确认 outbox 和退款终态。
4. 回调通过 `channelCode + channelRefundNo` 查询历史尝试归属，旧尝试回调不得收口当前尝试；自动查单退避只统计当前尝试创建后的查询日志。
5. 管理端退款详情新增渠道退款尝试表及空态，gateway-access submit/query 契约新增必填 `refundAttemptNo` 并原样转发上游。

### 419.2 验证结果

| 项目 | 结果 |
| --- | --- |
| payment-core 后端全量测试 | `931` 项通过，`0` failures，`0` errors，`0` skipped |
| gateway-access 后端全量测试 | `50` 项通过，`0` failures，`0` errors，`0` skipped |
| payment-core 管理端测试 | `13` 项通过，`0` fail |
| payment-core 管理端生产构建 | `npm run build` 通过；保留既有单包大于 `500 kB` 警告 |
| Mapper 与脚本检查 | 两份 Mapper `xmllint`、退款 schema/data 双份一致性及 `git diff --check` 通过 |
| 代码提交 | `dc330fed feat(payment-core): track refund channel attempts` |

覆盖重点包括：同一轮尝试号在尝试表/outbox/派发间一致、调用前失败复用身份、UNKNOWN 禁止重发、可信查单恢复、旧尝试渠道退款号隔离、终态原子收口、新尝试不继承旧查单退避，以及前后端跨系统字段透传。

### 419.3 当前判断

1. 本地实现已具备结构化退款多尝试事实，不再依赖退款单当前投影解释历史渠道请求。
2. `refundAttemptNo` 只是每轮商户请求身份，不表示渠道已收到、受理或完成退款；`UNKNOWN` 仍必须先核查，不能直接重发。
3. 本地 H2/MySQL 模式测试不替代生产 MySQL 迁移演练、真实渠道幂等与查单协议、退款到账、渠道账单及财务对账核验，release 四项真实阻断不变。

## 420. 2026-09-12 退款查单轮次确定关联 V1.0 验证

### 420.1 本轮补强

1. 退款操作日志新增可空 `refundAttemptNo` 持久化列；渠道提交、提交失败/未知、主动查单、查单失败/终态、UNKNOWN 受理恢复和回调终态均记录已有尝试上下文。
2. 自动查单退避从“退款单号 + 日志时间不早于尝试创建时间”改为按 `refundAttemptNo` 精确聚合，消除秒级时间相等导致的旧轮次次数继承。
3. 主动查单发起时保存预期尝试号，渠道返回后在退款单行锁内与当前最新尝试复核；期间已切换尝试时拒绝旧结果，不产生受理、终态或日志副作用。
4. MySQL 增量迁移只为唯一尝试退款回填历史渠道日志；无法可靠区分轮次的多尝试旧日志不做推断。

### 420.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 定向测试 | `RefundServiceImplTest,PaymentEventDispatchServiceImplTest,PaymentTaskCenterMapperIntegrationTest` 共 `106` 项通过，`0` failures、`0` errors、`0` skipped |
| payment-core 后端全量测试 | `932` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `RefundMapper.xml`、`PaymentTaskCenterMapper.xml` 的 `xmllint` 及 `git diff --check` 通过 |
| schema 检查 | 双份 `t_refund_operation_log` 新增列和索引定义一致 |
| 代码提交 | `7437cf1d fix(payment-core): isolate refund query attempts` |

新增用例显式构造旧查询日志与新尝试 `created_at` 同秒，验证旧日志不会进入新尝试退避；另模拟外部查单期间当前尝试从 `RFA-001` 切换到 `RFA-002`，验证旧结果在任何写操作前被拒绝。

### 420.3 当前判断

1. 退款查询事实和自动退避已使用确定的尝试身份，不再依赖秒级时间近似关联。
2. 单机 H2/Mockito 证明当前 Mapper 与服务分支，不证明生产 MySQL 在线 DDL、历史回填行数、多实例锁时序或真实渠道超时返回；上线前仍需备份、抽样和回滚演练。
3. 本轮不证明真实渠道受理、退款到账、渠道账单或财务对账一致性，release 四项真实阻断不变。

## 421. 2026-09-12 退款首次回调轮次隔离 V1.0 验证

### 421.1 本轮补强

1. 退款回调请求新增必填 `refundAttemptNo`，最大长度 `64`，并在裁剪前拒绝控制字符；非法尝试号在验签和 Mapper 访问前失败。
2. 退款回调签名原文加入尝试号，锁内渠道结果处理要求回调尝试号等于当前最新尝试号；支付回调签名原文保持不变。
3. 旧 UNKNOWN 尝试即使没有历史渠道退款号，其首次晚到回调也会在历史归属查询、受理绑定、outbox 确认、退款终态和日志写入前被拒绝。

### 421.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 定向测试 | `RefundControllerTest,RefundServiceImplTest,PaymentCallbackSignatureServiceImplTest` 共 `69` 项通过，`0` failures、`0` errors、`0` skipped |
| payment-core 后端全量测试 | `934` 项通过，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` 通过；本轮无 Mapper、schema、迁移或前端变化 |
| 代码提交 | `c853baac fix(payment-core): bind refund callbacks to attempts` |

### 421.3 当前判断

1. 退款查单与回调现在都在锁内使用明确尝试身份，UNKNOWN 首次回调不再依赖尚未形成的渠道退款号判断轮次。
2. 这是退款回调协议的非兼容升级，实际接入前必须同步渠道适配器和签名向量；本地 Mockito/HMAC 测试不证明真实渠道已支持。
3. 本轮不证明生产多实例时序、真实退款回调送达、退款到账、渠道账单或财务对账一致性，release 四项真实阻断不变。

## 422. 2026-09-12 退款旧下发事件隔离 V1.0 验证

### 422.1 本轮补强

1. `findChannelSubmitRequestByAttemptNo` 增加当前最新尝试条件，历史退款尝试即使存在可重发事件，也不能生成渠道下发快照。
2. 当前尝试选择统一使用 `created_at DESC, id DESC`，避免同秒多尝试排序不确定；最新尝试的正常下发字段保持不变。
3. 旧事件读取空快照后沿既有失败/死信路径处理，在退款渠道提交、受理绑定和渠道操作日志之前失败闭合。

### 422.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 定向测试 | `RefundSubmitOutboxMapperIntegrationTest,PaymentEventDispatchServiceImplTest` 共 `56` 项通过，`0` failures、`0` errors、`0` skipped |
| payment-core 后端全量测试 | `936` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `RefundMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 数据结构 | 无 schema、data 或迁移变化；正式 SQL 使用既有退款尝试索引 |
| 代码提交 | `94be9578 fix(payment-core): block stale refund submit events` |

### 422.3 当前判断

1. 旧退款事件、旧查单结果和旧回调均已具备明确的当前尝试隔离，异常历史 outbox 不会从静态快照路径调用渠道。
2. 本地 H2/Mockito 只证明当前 SQL 与服务分支，不证明生产 MySQL 事务隔离、事件锁竞争或真实渠道调用时序。
3. 本轮不证明真实渠道受理、退款到账、渠道账单或财务对账一致性，release 四项真实阻断不变。

## 423. 2026-09-12 支付成功事件提交后派发 V1.0 验证

### 423.1 本轮补强

1. 支付成功回调和主动查单不再在本地业务事务提交前调用清分、账务或 AMQP；两条路径统一注册 `afterCommit`，回滚时不会产生外部派发。
2. 提交后的即时派发使用独立 `REQUIRES_NEW` 事务并锁定事件行；仅事件类型、支付单归属和可派发状态一致时读取支付详情并调用下游。
3. 已成功事件重复触发直接幂等结束，身份不一致事件在外部调用前失败闭合；即时派发异常不抹除已提交 outbox，由既有自动补偿或人工重发继续处理。

### 423.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 定向测试 | `PaymentServiceImplTest,PaymentEventDispatchServiceImplTest` 共 `126` 项通过，`0` failures、`0` errors、`0` skipped |
| payment-core 后端全量测试 | `939` 项通过，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` 通过；本轮无 Mapper、schema、迁移或前端变化 |
| 代码提交 | `8d04837f fix(payment-core): dispatch success events after commit` |

### 423.3 当前判断

1. 支付成功 outbox 现在先于外部副作用形成可补偿事实，即时派发、自动补偿与人工重发也共享单事件数据库互斥。
2. HTTP 模式仍是清分后账务的顺序调用；清分成功、账务失败时可能再次调用清分，必须依赖下游幂等并在后续引入分阶段投递事实。AMQP publisher confirm 也不证明消费完成。
3. 本地 Mockito/反射测试不证明 Spring 生产代理、MySQL 多实例锁等待、进程崩溃窗口、真实下游幂等或账务入账；release 四项真实阻断不变。

## 424. 2026-09-12 支付成功 HTTP 分阶段投递 V1.0 验证

### 424.1 本轮补强

1. `t_payment_event.payment_success_http_stage` 建模 `PENDING / CLEARING_ACCEPTED / COMPLETED`，正式查询与事件行锁快照均返回该字段；双份初始化 schema/data 和增量迁移保持一致。
2. HTTP 模式在 `PENDING` 调用清分并条件推进阶段，再调用账务；账务失败沿既有失败或死信状态机收口，下一次从 `CLEARING_ACCEPTED` 继续，不重复调用清分。
3. `COMPLETED` 事件重试只补写发布成功；非法阶段失败闭合。AMQP 模式完全忽略 HTTP 阶段，保持既有 publisher confirm 与发布状态语义。

### 424.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 定向测试 | `PaymentEventDispatchServiceImplTest,PaymentEventRepublishAuditMapperIntegrationTest` 共 `57` 项通过，`0` failures、`0` errors、`0` skipped |
| payment-core 后端全量测试 | `942` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentEventMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 数据结构 | 双份 schema/data 已同步；新增 `2026-09-12-add-payment-success-http-stage.sql`，存量行默认 `PENDING` |
| 统一门禁复判 | 脚本退出码 `2` 仅来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`releaseBlockerCount=4` |
| 代码提交 | `e7f69d34 fix(payment-core): resume staged success delivery` |

全量测试首次执行发现 `RefundSubmitOutboxMapperIntegrationTest` 的 H2 夹具缺少新列，补齐与正式表一致的默认列后再次执行全量通过；未把首次失败记录成业务能力通过。

### 424.3 当前判断

1. 常规账务 HTTP 失败返回后，清分已接受阶段会与事件失败状态共同提交，自动补偿和人工重发只继续账务；阶段条件更新和事件行锁阻止同一事件并发越级推进。
2. 阶段事实只描述 payment-core 已收到下游成功响应，不证明清分完成或账务入账；AMQP publisher confirm 也仍不证明消费者完成处理。
3. 清分接受响应之后、当前事件事务提交之前若进程硬退出或数据库回滚，阶段写入可能丢失并导致清分重放，因此真实下游仍必须按稳定 `eventNo / paymentOrderId` 幂等。本地 H2/Mockito 不替代生产 MySQL、多实例、真实 HTTP、资金或稳定性验证，release 四项真实阻断不变。

## 425. 2026-09-12 支付成功 HTTP 阶段运营可见性 V1.0 验证

### 425.1 本轮补强

1. 支付事件 CSV 在发布状态后增加 `HTTP投递阶段`，按 `paymentSuccessHttpStage` 原值导出；列表、详情和排障建议复用同一前端纯函数解释阶段。
2. `PENDING / CLEARING_ACCEPTED / COMPLETED` 分别展示“待清分”“清分已受理”“双下游已受理”，并给出分阶段重发建议；非 `PAYMENT_SUCCESS` 显示“不适用”，非法或缺失阶段显示“阶段异常”。
3. `PAYMENT_SUCCESS + SUCCESS + PENDING` 显示“未记录 HTTP 阶段”，避免将历史默认值或 AMQP 模式误报为待清分；支付事件页在小屏切换单列布局，表格保留独立横向滚动。

### 425.2 验证结果

| 项目 | 结果 |
| --- | --- |
| payment-core 管理端测试 | `15` 项通过，`0` fail，覆盖三种 HTTP 阶段、历史/AMQP 保守展示、异常值、非支付成功事件和排障建议 |
| PaymentEventServiceImplTest | `11` 项通过，`0` failures、`0` errors、`0` skipped，覆盖 CSV 新列与字段值 |
| payment-core 后端全量测试 | `942` 项通过，`0` failures、`0` errors、`0` skipped |
| 管理端生产构建 | 通过；保留既有 `542.21 kB` 单包大于 `500 kB` 告警 |
| 桌面浏览器 | `1440x960` 下页面无宽度溢出，三种阶段均实际渲染 |
| 手机浏览器 | `390x844` 下 `clientWidth/scrollWidth=390/390`，表格容器 `330px` 且内部横向滚动，阶段徽标无文字溢出 |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1` 来自 release 正式条件未闭环，其内部 `releaseBlockerCount=4` |
| 代码提交 | `e574af37 feat(payment-core): expose success delivery stages` |

### 425.3 当前判断

1. 运营人员现在可在页面、详情、建议和 CSV 中看到一致的阶段事实，并按阶段缩小重试范围；未知或不适用数据失败闭合，不诱导盲目重发。
2. `SUCCESS + PENDING` 不能区分迁移前 HTTP 成功事件与 AMQP 发布事件，因此保守文案是展示策略，不回写数据库，也不推导真实投递模式或下游完成状态。
3. 本机 mock、构建和浏览器检查不证明真实下游接受、真实清分、账务入账、生产数据正确或稳定性；发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论仍未形成，`release/v1.0.0` 继续冻结。

## 426. 2026-09-12 支付成功 HTTP 非法阶段失败闭合 V1.0 验证

### 426.1 本轮补强

1. HTTP 派发移除空白阶段到 `PENDING` 的隐式回退，只接受 `PENDING / CLEARING_ACCEPTED / COMPLETED` 三个持久化状态。
2. 空白、缺失和未知阶段在清分、账务及阶段推进前失败，事件继续按既有重试次数进入 `FAILED / DEAD_LETTER`；AMQP 分支仍不读取 HTTP 阶段。
3. 两个手工构造的旧 HTTP 测试事件补齐数据库真实默认值 `PENDING`，避免测试夹具依赖 DTO 空值表达合法持久化事实。

### 426.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 首次定向测试 | 未通过：`53` 项中 `1` failure、`1` error，原因是两个旧 HTTP 夹具未设置数据库默认阶段；未记录为业务能力通过 |
| 修正后定向测试 | `PaymentEventDispatchServiceImplTest` `53` 项通过，`0` failures、`0` errors、`0` skipped |
| payment-core 后端全量测试 | `943` 项通过，`0` failures、`0` errors、`0` skipped |
| 外部副作用断言 | 空白与未知阶段均为清分/账务零调用、阶段零推进、事件成功零写入，并进入既有失败收口 |
| AMQP 回归 | 既有测试继续通过，HTTP 阶段 Mapper 方法零调用 |
| 结构与补丁 | 无 schema、Mapper 或迁移变化；`git diff --check` 通过 |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |
| 代码提交 | `4dfc68d4 fix(payment-core): reject invalid success delivery stages` |

### 426.3 当前判断

1. HTTP 派发的权威裁决现与运营页面一致：无法识别阶段时不猜测、不调用外部系统，先保留失败事实供数据修复和人工核查。
2. 数据库默认值保证正常新事件仍从 `PENDING` 开始；AMQP 的发布语义继续只由 `publishStatus` 与 publisher confirm 表达，不因无关 HTTP 阶段异常被阻断。
3. 本地 Mockito 不证明生产存量数据质量、真实下游幂等、清分或账务结果、MySQL 多实例行为和稳定性；release 四项真实治理阻断不变。

## 427. 2026-09-12 支付成功 HTTP 完成阶段直接恢复 V1.0 验证

### 427.1 本轮补强

1. 派发入口先校验投递模式；HTTP 模式随后先解析并校验阶段，`COMPLETED` 跳过载荷重建和 HTTP 配置校验，直接补齐事件 `SUCCESS`。
2. `COMPLETED` 不再读取支付详情、服务者，不依赖清分 URL、账务 URL 或账务账号；合法 `PENDING / CLEARING_ACCEPTED` 及 AMQP 的载荷校验与外部调用路径保持不变。
3. 非法模式和非法 HTTP 阶段同步提前到支付详情读取前失败，避免无意义的业务查询，也维持外部副作用零调用。

### 427.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 首次定向测试 | 未通过：`53` 项中 `1` error，原因是非法派发模式提前失败后旧用例的支付详情、服务者 stub 变为多余；业务断言无 failure，未记录为能力通过 |
| 修正后定向测试 | `PaymentEventDispatchServiceImplTest` `53` 项通过，`0` failures、`0` errors、`0` skipped |
| payment-core 后端全量测试 | `943` 项通过，`0` failures、`0` errors、`0` skipped |
| 完成阶段恢复 | 支付详情与服务者零读取、HTTP 配置为空、两个 HTTP 下游零调用、阶段零推进，仅 `markPublishSuccess` 一次 |
| 失败闭合 | 非法模式与空白/未知 HTTP 阶段在支付详情读取前失败，保持下游零调用和既有失败状态收口 |
| 结构与补丁 | 无 API、前端、schema、Mapper 或迁移变化；`git diff --check` 通过 |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |
| 代码提交 | `54724db3 fix(payment-core): recover completed success events directly` |

### 427.3 当前判断

1. `COMPLETED` 现在真正表示两个 HTTP 下游均已接受、只剩本地发布终态待修复；恢复不再被已经无关的载荷来源或下游配置阻断。
2. 事件行锁、可重发状态和 `markPublishSuccess` 受影响行数仍承担本地并发与终态裁决；写入失败不会被包装成成功，而会沿原失败/死信路径保留。
3. 本地 Mockito 不证明生产事件更新一定成功、真实下游接受、清分完成、账务入账、MySQL 多实例时序或稳定性；release 四项真实治理阻断不变。

## 428. 2026-09-12 支付事件到期重试指标口径对齐 V1.0 验证

### 428.1 本轮补强

1. `findOverview.dueRetryEventCount` 与 `findDueFailedEventNos` 复用 `automaticRetryCandidateCondition` SQL 片段，避免总览和自动任务因重复条件再次漂移。
2. 统一口径为：`FAILED` 或 `PAYMENT_SUCCESS / REFUND_SUBMIT` 的 `PENDING`，且 `next_retry_at` 为空或不晚于当前时间；查询筛选仍通过既有 `paymentEventWhere` 约束总览范围。
3. 管理端说明改为“与自动任务候选口径一致”，DTO 注释同步，不改变字段名、响应结构、状态机或重试任务实现。

### 428.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 正式 Mapper 定向测试 | `PaymentEventRepublishAuditMapperIntegrationTest` `6` 项通过，`0` failures、`0` errors、`0` skipped |
| 候选一致性 | 同一夹具下总览计数为 `3`，候选列表恰为无未来时间的既有 `FAILED`、`PENDING PAYMENT_SUCCESS`、`PENDING REFUND_SUBMIT` |
| 排除规则 | `PENDING PAYMENT_PENDING` 和未来到期 `FAILED` 不进入总览计数及自动候选列表 |
| payment-core 后端全量测试 | `944` 项通过，`0` failures、`0` errors、`0` skipped |
| payment-core 管理端测试 | `15` 项通过，`0` fail |
| 管理端生产构建 | 通过；保留既有 `542.22 kB` 单包大于 `500 kB` 告警 |
| Mapper 与补丁检查 | `PaymentEventMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 无 API 字段名、schema、索引或迁移变化 |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |
| 代码提交 | `76fdb49a fix(payment-core): align due event metrics with retries` |

### 428.3 当前判断

1. 运营总览的“到期待重试”现在可作为自动任务当前候选规模的同口径观察值，不再系统性漏掉 `nextRetryAt` 为空的失败事件或首次待派发事件。
2. 总览仍受页面筛选条件影响，而自动任务读取全局有界批次；筛选后的页面数值不能解释为任务本次一定处理的数量，执行时还会受并发状态变化和行锁裁决影响。
3. 本地 H2、测试和构建不证明生产候选规模、任务已执行、真实下游完成或稳定性；release 四项真实治理阻断不变。

## 429. 2026-09-12 支付事件列表与导出稳定排序 V1.0 验证

### 429.1 本轮补强

1. `findAll` 与 `findAllForExport` 统一引用 `paymentEventOrder`，避免列表和 CSV 复制排序逻辑。
2. `createdAt / retryCount / nextRetryAt` 均以对应数据库字段为首排序键，以自增 `id` 为同方向次排序键；默认和非法排序入参仍由 Service 归一为 `createdAt / desc`。
3. 该变更只确定同值记录次序，不改变筛选条件、分页参数、导出上限、展示字段或业务状态。

### 429.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 正式 Mapper 定向测试 | `PaymentEventRepublishAuditMapperIntegrationTest` `7` 项通过，`0` failures、`0` errors、`0` skipped |
| 创建时间跨页 | 三条同秒事件按 `createdAt desc, id desc` 跨三页依次返回 `EVT-AUDIT-003 / 002 / 001`，无重复或遗漏 |
| 重试次数导出 | 三条同值事件按 `retryCount asc, id asc` 返回 `001 / 002 / 003` |
| 下次重试时间导出 | 三条 `nextRetryAt=null` 事件按 `nextRetryAt desc, id desc` 返回 `003 / 002 / 001` |
| payment-core 后端全量测试 | `945` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentEventMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 无 API、前端、schema、索引或迁移变化 |
| 代码提交 | `c76e49f0 fix(payment-core): stabilize payment event ordering` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |

### 429.3 当前判断

1. 在数据集合不变的前提下，列表翻页与 CSV 对同值记录具有确定次序，消除了仅因秒级时间或相同重试字段导致的随机分页边界。
2. `id` 次排序不能提供跨多个独立查询的数据库快照；翻页期间若有新事件写入或旧事件状态改变，偏移分页仍可能随数据集变化，强快照需求应另行采用游标或一致性读设计。
3. 本地 H2 不证明生产 MySQL 执行计划、大数据量导出、并发分页快照或稳定性；release 四项真实治理阻断不变。

## 430. 2026-09-12 退款列表与导出稳定排序 V1.0 验证

### 430.1 本轮补强

1. `RefundMapper.findAll` 与 `findAllForExport` 统一引用 `refundOrderListOrder`，避免页面和 CSV 重复维护排序规则。
2. 退款申请时间仍为唯一业务排序字段，并追加自增 `id DESC` 作为唯一且不可变的次排序键；新申请优先的既有阅读方向不变。
3. 该变更只确定同秒退款次序，不改变筛选、页码、导出 `1001` 行保护、响应字段、退款状态机或资金处理。

### 430.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 正式 Mapper 定向测试 | `RefundListMapperIntegrationTest` `1` 项通过，`0` failures、`0` errors、`0` skipped |
| 退款服务定向回归 | `RefundListMapperIntegrationTest,RefundServiceImplTest` `47` 项通过，`0` failures、`0` errors、`0` skipped |
| 同秒跨页与导出 | 三条同秒退款按 `applied_at desc, id desc` 在三页和导出中均依次返回 `REF-003 / REF-002 / REF-001` |
| payment-core 后端全量测试 | `946` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `RefundMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 首次无效结果 | 新 H2 夹具漏注册既有 `FORMAT` 兼容函数，查询在断言前报错；补齐别名后定向与全量重跑通过，首次结果未计入通过数 |
| 结构变化 | 无 API、前端、schema、索引或迁移变化 |
| 代码提交 | `cf86433f fix(payment-core): stabilize refund list ordering` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |

### 430.3 当前判断

1. 在数据集合不变的前提下，退款列表翻页与 CSV 对同秒记录具有确定次序，消除了仅因秒级申请时间导致的随机分页边界。
2. `id` 次排序不提供跨多个请求的数据库快照；翻页期间若发生退款申请或数据更新，偏移分页仍可能随数据集变化，不能解释为一致性读。
3. 本地 H2 不证明生产 MySQL 执行计划、大数据量导出、并发分页快照、退款到账或资金一致性；release 四项真实治理阻断不变。

## 431. 2026-09-12 支付列表与导出稳定排序 V1.0 验证

### 431.1 本轮补强

1. `PaymentMapper.findAll` 与 `findAllForExport` 统一引用 `paymentOrderListOrder`，避免页面和 CSV 重复维护排序规则。
2. 支付创建时间仍为唯一业务排序字段，并追加 `p.id DESC` 作为唯一且不可变的次排序键；最新支付单优先的既有方向不变。
3. 该变更不改变最近支付尝试关联、筛选、页码、导出 `1001` 行保护、响应字段、支付状态机或资金处理。

### 431.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 正式 Mapper 定向测试 | `PaymentMapperStateTransitionIntegrationTest` `11` 项通过，`0` failures、`0` errors、`0` skipped |
| 同秒跨页与导出 | 三条同秒支付单按 `p.created_at desc, p.id desc` 在三页和导出中均依次返回 `PAY-STABLE-003 / 002 / 001` |
| 最近尝试关联回归 | 原有支付列表最近尝试状态与提交事实用例继续通过 |
| payment-core 后端全量测试 | `947` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 无 API、前端、schema、索引或迁移变化 |
| 代码提交 | `b035a691 fix(payment-core): stabilize payment list ordering` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |

### 431.3 当前判断

1. 在数据集合不变的前提下，支付列表翻页与 CSV 对同秒记录具有确定次序，消除了仅因秒级创建时间导致的随机分页边界。
2. `id` 次排序不提供跨多个请求的数据库快照；翻页期间若发生支付单插入或更新，偏移分页仍可能随数据集变化，不能解释为一致性读。
3. 本地 H2 不证明生产 MySQL 执行计划、大数据量导出、并发分页快照、渠道受理、资金入账或生产稳定性；release 四项真实治理阻断不变。

## 432. 2026-09-12 账单列表与导出稳定排序 V1.0 验证

### 432.1 本轮补强

1. `BillMapper.findAll` 与 `findAllForExport` 统一引用 `billOrder`，避免页面和 CSV 重复维护动态排序。
2. `createdAt / dueAt / billAmount / unpaidAmount` 均以对应数据库表达式为首排序键，以自增 `id` 为同方向次排序键。
3. 该变更不改变筛选、排序请求参数、分页、导出 `1001` 行保护、总览金额、响应字段或账单状态。

### 432.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 正式 Mapper 与服务定向测试 | `BillListMapperIntegrationTest,BillServiceImplTest` `7` 项通过，`0` failures、`0` errors、`0` skipped |
| 创建时间跨页 | 三条同秒账单按 `createdAt desc, id desc` 跨三页依次返回 `BILL-STABLE-003 / 002 / 001` |
| 到期时间与未付金额导出 | 三条同值账单按升序均返回 `001 / 002 / 003` |
| 应收金额导出 | 三条同值账单按降序返回 `003 / 002 / 001` |
| payment-core 后端全量测试 | `948` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `BillMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 无 API、前端、schema、索引或迁移变化 |
| 代码提交 | `91691d59 fix(payment-core): stabilize bill list ordering` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |

### 432.3 当前判断

1. 在数据集合不变的前提下，账单分页和 CSV 对所有受支持排序字段的同值记录均具有确定次序。
2. `id` 次排序不提供跨多个请求的数据库快照；翻页期间若发生账单插入或更新，偏移分页仍可能随数据集变化。
3. 本地 H2 不证明生产 MySQL 执行计划、大数据量导出、并发分页快照、账务入账或资金一致性；release 四项真实治理阻断不变。

## 433. 2026-09-12 收银台会话列表与导出稳定排序 V1.0 验证

### 433.1 本轮补强

1. `CashierSessionMapper.findAll` 与 `findAllForExport` 统一引用 `cashierSessionOrder`，避免页面和 CSV 重复维护动态排序。
2. `createdAt / expiresAt / amount` 均以对应数据库字段为首排序键，以自增 `pr.id` 为同方向次排序键。
3. 该变更不改变会话状态表达式、筛选、排序参数、分页、导出 `1001` 行保护、总览金额或响应字段。

### 433.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 正式 Mapper 与服务定向测试 | `CashierSessionListMapperIntegrationTest,CashierSessionServiceImplTest` `7` 项通过，`0` failures、`0` errors、`0` skipped |
| 创建时间跨页 | 三条同秒会话按 `createdAt desc, pr.id desc` 跨三页依次返回 `PRE-STABLE-003 / 002 / 001` |
| 失效时间导出 | 三条同值会话按升序返回 `001 / 002 / 003` |
| 金额导出 | 三条同值会话按降序返回 `003 / 002 / 001` |
| payment-core 后端全量测试 | `949` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `CashierSessionMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 无 API、前端、schema、索引或迁移变化 |
| 代码提交 | `d74617b2 fix(payment-core): stabilize cashier session ordering` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |

### 433.3 当前判断

1. 在数据集合不变的前提下，收银台会话分页和 CSV 对三类受支持排序字段的同值记录均具有确定次序。
2. `id` 次排序不提供跨多个请求的数据库快照；翻页期间若发生会话插入或更新，偏移分页仍可能随数据集变化。
3. 本地 H2 不证明生产 MySQL 执行计划、大数据量导出、并发分页快照、真实支付提交或渠道受理；release 四项真实治理阻断不变。

## 434. 2026-09-12 支付请求列表与导出稳定排序 V1.0 验证

### 434.1 本轮补强

1. `PaymentRequestMapper.findAll` 与 `findAllForExport` 统一引用 `paymentRequestOrder`，避免页面和 CSV 重复维护动态排序。
2. `createdAt / channelCode / terminal` 均以对应数据库字段为首排序键，以自增 `pa.id` 为同方向次排序键。
3. 该变更不改变支付单关联、最近路由记录选择、筛选、排序参数、分页、导出 `1001` 行保护、响应字段或支付处理状态。

### 434.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 正式 Mapper 与服务定向测试 | `PaymentRequestListMapperIntegrationTest,PaymentRequestServiceImplTest` `7` 项通过，`0` failures、`0` errors、`0` skipped |
| 创建时间跨页 | 三条同秒支付尝试按 `createdAt desc, pa.id desc` 跨三页依次返回 `ATTEMPT-STABLE-003 / 002 / 001` |
| 渠道导出 | 三条同渠道支付尝试按升序返回 `001 / 002 / 003` |
| 终端导出 | 三条同终端支付尝试按降序返回 `003 / 002 / 001` |
| 查询结构回归 | 正式 SQL 继续执行支付单关联和按 `created_at DESC, id DESC` 选择最近路由记录的子查询 |
| payment-core 后端全量测试 | `950` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentRequestMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 无 API、前端、schema、索引或迁移变化 |
| 代码提交 | `c0ae5e8b fix(payment-core): stabilize payment request ordering` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |

### 434.3 当前判断

1. 在数据集合不变的前提下，支付请求分页和 CSV 对三类受支持排序字段的同值记录均具有确定次序。
2. `pa.id` 次排序不提供跨多个请求的数据库快照；翻页期间若发生支付尝试插入或更新，偏移分页仍可能随数据集变化。
3. 本地 H2 不证明生产 MySQL 执行计划、大数据量导出、并发分页快照、真实渠道受理或资金结果；release 四项真实治理阻断不变。

## 435. 2026-09-12 支付流水列表与导出稳定排序 V1.0 验证

### 435.1 本轮补强

1. 支付尝试、渠道回调、路由记录和业务事件联合分支分别投影固定 `flow_source_rank=1..4` 与各自表的 `flow_source_id`，避免不同来源相同自增 ID 发生次排序碰撞。
2. `createdAt / retryCount / flowType` 均以原业务字段为首排序键，再以来源序号和来源主键作为同方向复合次排序键；`findAll` 与 `findAllForExport` 统一引用 `paymentFlowOrder`。
3. 内部排序列不进入响应 DTO、CSV 或 `count` 联合列；筛选、页码、导出 `1001` 行保护、敏感数据脱敏和流水事实含义不变。

### 435.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 正式 Mapper 与服务定向测试 | `PaymentFlowListMapperIntegrationTest,PaymentFlowServiceImplTest` `6` 项通过，`0` failures、`0` errors、`0` skipped |
| 跨来源同秒排序 | 支付尝试、渠道回调、路由记录和业务事件各自 `id=1` 时，创建时间降序按来源序号返回 `EVENT / ROUTE / NOTIFY / ATTEMPT` |
| 创建时间跨页 | 三条同秒支付尝试跨三页依次返回 `FLOW-ATTEMPT-STABLE-003 / 002 / 001` |
| 流水类型导出 | 三条同类型支付尝试按升序返回 `001 / 002 / 003` |
| 重试次数导出 | 三条同重试次数业务事件按降序返回 `003 / 002 / 001` |
| 计数回归 | 正式 `count` 联合查询返回筛选后的 `3` 条，未携带内部排序列 |
| payment-core 后端全量测试 | `951` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentFlowMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 无 API、DTO、前端、schema、索引或迁移变化 |
| 代码提交 | `af431170 fix(payment-core): stabilize payment flow ordering` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |

### 435.3 当前判断

1. 在数据集合不变的前提下，支付流水分页和 CSV 对三类受支持排序字段的同值记录均具有跨来源确定次序。
2. 来源序号是内部固定排序语义，不代表四类流水的业务优先级；复合次排序也不提供多个请求之间的数据库快照，偏移分页仍会随并发写入变化。
3. 本地 H2 不证明生产 MySQL `UNION ALL` 排序执行计划、大数据量导出、并发分页快照、真实渠道处理或资金结果；release 四项真实治理阻断不变。

## 436. 2026-09-12 订单列表与导出稳定排序 V1.0 验证

### 436.1 本轮补强

1. `OrderMapper.findAll` 与 `findForExport` 统一引用 `orderListOrder`，避免页面和 CSV 独立维护排序规则。
2. 订单创建时间仍为唯一业务排序字段，并追加唯一、不可变的 `o.id DESC`；最新订单优先的既有阅读方向不变。
3. 该变更不改变筛选、分页、导出 `1001` 行保护、响应 DTO、名称脱敏，以及最近账单、支付单和预付单的选择规则。

### 436.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 正式 Mapper 与服务定向测试 | `OrderListMapperIntegrationTest,OrderServiceImplTest` `6` 项通过，`0` failures、`0` errors、`0` skipped |
| 同秒跨页与导出 | 三条同秒订单在三页和 CSV 中均依次返回 `ORDER-STABLE-003 / 002 / 001` |
| 最近账单关联 | 两条同秒账单按 `created_at DESC, id DESC` 返回 `BILL-STABLE-NEW` |
| 最近支付单关联 | 两条同秒支付单返回 `PAY-STABLE-NEW` 及其“支付成功”状态 |
| 最近预付单关联 | 两条同秒预付单返回 `PRE-STABLE-NEW` 及其“支付完成”状态 |
| 计数回归 | 正式 `count` 查询返回筛选后的 `3` 条 |
| payment-core 后端全量测试 | `952` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `OrderMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 无 API、DTO、前端、schema、索引或迁移变化 |
| 代码提交 | `9ee2d426 fix(payment-core): stabilize order list ordering` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |

### 436.3 当前判断

1. 在数据集合不变的前提下，订单列表翻页与 CSV 对同秒记录具有确定次序，最近关联事实也有明确的同秒裁决规则。
2. `o.id` 次排序不提供多个请求之间的数据库快照；翻页期间若发生订单插入或更新，偏移分页仍可能随数据集变化。
3. 本地 H2 不证明生产 MySQL 执行计划、大数据导出、并发分页快照、真实履约、渠道受理或资金结果；release 四项真实治理阻断不变。

## 437. 2026-09-12 支付处理日志列表与导出稳定排序 V1.0 验证

### 437.1 本轮补强

1. `PaymentLogMapper.findAll` 与 `findAllForExport` 继续共用 `paymentLogOrder`，并为 `createdAt / logLevel / processStage` 三类排序统一追加自增 `id`。
2. 次排序方向与主排序方向一致，保证相同创建时间、级别或阶段的日志形成确定全序；原排序字段白名单和默认创建时间降序不变。
3. 该变更不改变日志写入幂等键、筛选、计数、总览、分页、导出 `1001` 行保护、响应字段或日志内容。

### 437.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 正式 Mapper 与服务定向测试 | `PaymentProcessLogMapperIntegrationTest,PaymentLogServiceImplTest` `8` 项通过，`0` failures、`0` errors、`0` skipped |
| 创建时间跨页 | 三条同秒日志跨三页依次返回 `LOG-STABLE-003 / 002 / 001` |
| 日志级别导出 | 三条同级别日志按升序返回 `001 / 002 / 003` |
| 处理阶段导出 | 三条同阶段日志按降序返回 `003 / 002 / 001` |
| 写入与失败路径回归 | `(source_type, source_no)` 幂等写入及非重复数据错误失败用例继续通过 |
| 计数回归 | 正式 `count` 查询返回筛选后的 `3` 条 |
| payment-core 后端全量测试 | `953` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentLogMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 无 API、DTO、前端、schema、索引或迁移变化 |
| 代码提交 | `2e2ba662 fix(payment-core): stabilize payment log ordering` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |

### 437.3 当前判断

1. 在数据集合不变的前提下，日志分页和 CSV 对三类受支持排序字段的同值记录均具有确定次序，有利于重复排障和证据比对。
2. `id` 次排序不提供多个请求之间的数据库快照；翻页期间若新增日志，偏移分页仍可能随数据集变化，不能解释为审计快照。
3. 本地 H2 不证明生产日志完整性、MySQL 执行计划、大数据导出、外部告警送达、真实渠道处理或资金结果；release 四项真实治理阻断不变。

## 438. 2026-09-12 服务者结算列表与导出稳定排序 V1.0 验证

### 438.1 本轮补强

1. `SettlementMapper.findWorkerSettlements` 与 `findWorkerSettlementsForExport` 统一引用 `workerSettlementOrder`，避免页面和 CSV 独立维护排序规则。
2. 既有账期结束日、创建时间降序后追加唯一且不可变的自增 `id DESC`，保持最近账期和最新创建记录优先的阅读方向。
3. 该变更不改变筛选、计数、分页、CSV `1001` 行保护、响应字段、服务者姓名脱敏、API、schema、索引或迁移。

### 438.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 正式 Mapper 与服务定向测试 | `WorkerSettlementListMapperIntegrationTest,SettlementServiceImplTest` `7` 项通过，`0` failures、`0` errors、`0` skipped |
| 同账期同秒跨页 | 三条结算单跨三页依次返回 `SETTLE-STABLE-003 / 002 / 001` |
| CSV 稳定顺序 | 相同筛选条件下依次返回 `SETTLE-STABLE-003 / 002 / 001` |
| 字段映射 | 账期为 `2026-09-01 ~ 2026-09-07`，应结、扣减、实结和保证金影响金额格式正确 |
| 计数回归 | 正式 `count` 查询返回筛选后的 `3` 条 |
| payment-core 后端全量测试 | `954` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `SettlementMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 无 API、DTO、前端、schema、索引或迁移变化 |
| 代码提交 | `6ac719c4 fix(payment-core): stabilize worker settlement ordering` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |

首次定向运行因复用的 H2 `DATE_FORMAT` 测试桩固定输出到秒而断言失败；改用日期型兼容函数后，测试类可见性又不满足 H2 反射调用要求。修正测试基础设施后连续定向与全量运行均通过，前两次失败不计为能力通过。

### 438.3 当前判断

1. 在数据集合不变的前提下，服务者结算分页与 CSV 对相同账期结束日和创建时间记录具有确定次序。
2. `id` 次排序不提供多个请求之间的数据库快照；翻页期间若新增或修改结算单，偏移分页仍可能随数据集变化。
3. 本地 H2 不证明生产 MySQL 执行计划、大数据导出、真实出款、到账、结算或对账一致性；release 四项真实治理阻断不变。

## 439. 2026-09-12 支付记录列表与最近尝试确定性读取 V1.0 验证

### 439.1 本轮补强

1. `PaymentRecordMapper.findAll` 的 `createdAt / paymentAmount / paidAt` 三类排序集中到 `paymentRecordOrder`，每类业务字段后追加同方向自增 `p.id`。
2. `PaymentRecordMapper.findDetail` 不再用 `MAX(created_at)` 关联可能重复的尝试行，改为按 `created_at DESC, id DESC LIMIT 1` 唯一选择最近支付尝试。
3. 该变更不新增 CSV 能力，也不改变筛选、计数、分页、响应字段、用户引用脱敏、API、schema、索引或迁移。

### 439.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 支付记录相关定向测试 | `PaymentRecordListMapperIntegrationTest,PaymentRecordRefundAggregationMapperIntegrationTest,PaymentRecordServiceListTest,PaymentRecordServiceImplTest` `11` 项通过，`0` failures、`0` errors、`0` skipped |
| 创建时间跨页 | 三条同秒记录跨三页依次返回 `PAY-RECORD-STABLE-003 / 002 / 001` |
| 支付金额排序 | 三条同金额记录升序返回 `001 / 002 / 003` |
| 支付完成时间排序 | 三条同秒成功记录降序返回 `003 / 002 / 001` |
| 最近尝试选择 | 两条尝试创建时间相同时返回后插入的 `NEW_TERMINAL / NEW_KEY / SUCCESS / new-response` |
| 聚合与服务回归 | 成功退款聚合、列表查询规范化、用户引用脱敏和详情不存在/字段脱敏用例继续通过 |
| 计数回归 | 正式 `count` 查询返回筛选后的 `3` 条 |
| payment-core 后端全量测试 | `956` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentRecordMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 无 API、DTO、前端、schema、索引或迁移变化 |
| 代码提交 | `a6c6617f fix(payment-core): stabilize payment record reads` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |

### 439.3 当前判断

1. 在数据集合不变的前提下，支付记录三类排序的同值行具有确定次序；详情在同秒尝试下也具有明确的最新事实选择规则。
2. `p.id` 次排序不提供多个请求之间的数据库快照；翻页期间若新增或修改支付记录，偏移分页仍可能随数据集变化。
3. 本地 H2 不证明生产 MySQL 执行计划、真实渠道受理、支付成功、退款到账或资金对账一致性；release 四项真实治理阻断不变。

## 440. 2026-09-12 支付详情三类轨迹稳定排序 V1.0 验证

### 440.1 本轮补强

1. `PaymentMapper.findNotifyLogs`、`findRouteLogs` 和 `findEventItems` 从单独按 `created_at DESC` 调整为按 `created_at DESC, id DESC` 返回。
2. 支付详情与收款记录详情继续复用这三条 Mapper 查询，服务层不复制排序或重排结果。
3. 该变更不改变轨迹展示文本、查询范围、详情响应字段、API、前端、schema、索引或迁移。

### 440.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 正式 Mapper 与详情服务定向测试 | `PaymentDetailTraceOrderingMapperIntegrationTest,PaymentRecordServiceImplTest,PaymentServiceImplTest` `80` 项通过，`0` failures、`0` errors、`0` skipped |
| 通知轨迹 | 三条同秒通知按本表 `id DESC` 返回 `通知-003 / 002 / 001` |
| 路由轨迹 | 三条同秒路由按本表 `id DESC` 返回 `规则-003 / 002 / 001` |
| 事件轨迹 | 三条同秒事件按本表 `id DESC` 返回 `EVENT-003 / 002 / 001` |
| 详情装配回归 | 支付主详情与收款记录详情继续获取路由、通知和事件轨迹 |
| payment-core 后端全量测试 | `957` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 无 API、DTO、前端、schema、索引或迁移变化 |
| 代码提交 | `7098ded9 fix(payment-core): stabilize payment detail trace ordering` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |

### 440.3 当前判断

1. 对同一支付单和静态数据集，三类详情轨迹均按“创建时间、主键”从新到旧稳定展示，便于重复排障与证据比对。
2. 自增 `id` 只裁决各事实表内部同秒记录，不表达路由、通知和事件三种事实之间的全局先后或因果关系。
3. 本地 H2 不证明生产 MySQL 执行计划、真实渠道通知时序、下游事件消费、支付成功、账务入账或资金对账一致性；release 四项真实治理阻断不变。

## 441. 2026-09-12 超时关单候选稳定排序 V1.0 验证

### 441.1 本轮补强

1. `PaymentMapper.findExpiredPayments` 从只按最早预付失效时间排序，调整为 `MIN(pr.expires_at) ASC, p.payment_order_id ASC`。
2. `payment_order_id` 在每个分组中唯一且稳定，用于裁决相同最早失效时间；最早失效优先的既有方向保持不变。
3. 该变更只影响固定批次候选的读取顺序，不改变超时资格、渠道提交事实排除、失败退避、任务租约、逐笔条件更新、批次大小、状态机、API、schema、索引或迁移。

### 441.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 正式 Mapper 与任务服务定向测试 | `PaymentMapperStateTransitionIntegrationTest,PaymentExpiryTaskServiceImplTest` `20` 项通过，`0` failures、`0` errors、`0` skipped |
| 同失效时间有界批次 | 三条支付单按 `003 / 001 / 002` 插入，`batchSize=2` 固定返回 `001 / 002` |
| 同失效时间完整批次 | `batchSize=100` 固定返回 `001 / 002 / 003` |
| 既有路径回归 | 最早失效优先、渠道提交事实排除、失败退避、陈旧重试清理和状态迁移用例继续通过 |
| payment-core 后端全量测试 | `958` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 无 API、DTO、前端、schema、索引或迁移变化 |
| 代码提交 | `229d10aa fix(payment-core): stabilize expiry close candidate ordering` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |

### 441.3 当前判断

1. 在资格集合和数据库内容不变时，相同最早失效时间的候选具有确定顺序，固定批次边界可重复解释。
2. 该顺序不提供多实例全局串行；并发任务仍由已有租约限制调度，并由逐笔条件更新裁决最终状态迁移。
3. 本地 H2 不证明生产 MySQL 分组排序执行计划、并发调度、真实渠道关单、生产稳定性、资金或对账结果；release 四项真实治理阻断不变。

## 442. 2026-09-12 支付监控渠道指标稳定排序 V1.0 验证

### 442.1 本轮补强

1. `PaymentMonitorMapper.findChannelMetrics` 在既有 `totalCount DESC, successCount DESC` 后追加 `channel_code ASC, payment_method ASC`。
2. 两个新增字段正好覆盖查询的完整分组键，使统计值相同的分组仍具有确定顺序，同时保留业务量和成功量优先的既有展示语义。
3. 该变更不改变统计范围、成功率、成功金额、待处理数、风险等级、服务装配、接口字段、前端、schema、索引或迁移。

### 442.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 正式 Mapper 与监控服务定向测试 | `PaymentMonitorMapperIntegrationTest,PaymentMonitorServiceImplTest` `2` 项通过，`0` failures、`0` errors、`0` skipped |
| 同统计值顺序 | 三组总量均为 `2`、成功量均为 `1`，稳定返回 `channel-a/method-a`、`channel-a/method-b`、`channel-b/method-a` |
| 聚合字段回归 | 每组 `totalCount=2`、`successCount=1`，证明测试确实进入分组键次排序 |
| 服务装配回归 | 监控服务继续直接装配摘要、趋势、渠道指标和告警，不在服务层重排渠道指标 |
| payment-core 后端全量测试 | `959` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentMonitorMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 无 API、DTO、前端、schema、索引或迁移变化 |
| 代码提交 | `88c4f248 fix(payment-core): stabilize channel metric ordering` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |

### 442.3 当前判断

1. 对同一统计快照，总量和成功量相同的渠道分组具有按完整分组键裁决的确定阅读顺序。
2. 该排序不提供多个请求之间的数据库快照；支付单新增或状态变化会合理改变聚合值和榜单顺序。
3. 本地 H2 不证明生产 MySQL 聚合排序计划、渠道数据完整、真实成功率、渠道稳定性、支付成功、账务入账或资金对账一致性；release 四项真实治理阻断不变。

## 443. 2026-09-12 支付监控告警显式优先级 V1.0 验证

### 443.1 本轮补强

1. `PaymentMonitorMapper.findAlerts` 的三段 `UNION ALL` 分别增加内部 `alertSortNo=1/2/3`，并在联合结果末尾显式 `ORDER BY alertSortNo ASC`。
2. 排序保持待回调 P1、退款失败 P1、停用渠道 P2 的既有业务编排，避免依赖数据库未承诺的联合分支返回顺序。
3. `alertSortNo` 只服务 Mapper 内部排序，`PaymentAlertItemDTO` 与 `GET /api/payment-monitor/overview` 响应字段保持不变。

### 443.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 正式 Mapper 与监控服务定向测试 | `PaymentMonitorMapperIntegrationTest,PaymentMonitorServiceImplTest` `3` 项通过，`0` failures、`0` errors、`0` skipped |
| 三类告警顺序 | 同时命中时固定返回 `WAIT_CALLBACK_TIMEOUT / REFUND_FAIL / CHANNEL_DISABLED` |
| 等级映射 | 前两项均为 `P1`，第三项为 `P2` |
| 前端消费核对 | 管理端按 `overview.alerts` 原序渲染，并以首项初始化告警快照，未在前端二次排序 |
| DTO 与服务回归 | 内部排序号未进入 DTO；监控服务继续装配摘要、趋势、渠道指标和告警 |
| payment-core 后端全量测试 | `960` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentMonitorMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 无 API、DTO、前端、schema、索引或迁移变化 |
| 代码提交 | `b8afdb62 fix(payment-core): stabilize monitor alert ordering` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |

### 443.3 当前判断

1. 三类告警同时存在时，运营页面首项和列表顺序具有明确、可回归的处理优先级，不再依赖 `UNION ALL` 的偶然返回顺序。
2. 固定类型顺序不按影响笔数动态重排；影响笔数仍作为独立字段展示，若未来需要动态优先级，应另行定义等级、数量和时间的组合规则。
3. 本地 H2 不证明生产告警数据完整、处置完成、渠道恢复、退款到账、支付成功或资金对账一致性；release 四项真实治理阻断不变。

## 444. 2026-09-12 支付监控已关闭指标 V1.0 验证

### 444.1 本轮补强

1. `PaymentMonitorMapper.findSummary` 新增 `SUM(CASE WHEN status = 'CLOSED' THEN 1 ELSE 0 END)`，独立返回已关闭支付数量。
2. `PaymentMonitorSummaryDTO` 和监控总览 JSON 摘要新增 `closedPaymentCount`，管理端监控页新增“已关闭支付”卡片。
3. 监控摘要使用专用桌面五列布局，既有平板两列和手机单列规则保持有效；该变更不影响共享卡片网格的其他页面。

### 444.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 监控后端定向测试 | `PaymentMonitorMapperIntegrationTest,PaymentMonitorServiceImplTest,PaymentMonitorControllerTest` `5` 项通过，`0` failures、`0` errors、`0` skipped |
| 摘要状态计数 | 夹具固定返回 `totalCount=6`、`successCount=3`、`pendingPaymentCount=1`、`closedPaymentCount=1` |
| 状态口径 | `CLOSED` 只进入已关闭计数，不进入 `SUCCESS` 或 `PREPAY_CREATED / WAIT_CALLBACK` 计数 |
| 管理端单测 | `15` 项通过，`0` fail、`0` skipped |
| 管理端生产构建 | 通过；产物最大 JS `542.37 kB`，保留既存超过 `500 kB` 告警 |
| payment-core 后端全量测试 | `961` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentMonitorMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 新增摘要响应字段和监控卡片；无状态机、schema、索引或迁移变化 |
| 代码提交 | `a8a651fe feat(payment-core): expose closed payment monitor metric` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |

### 444.3 当前判断

1. 独立支付监控页现已同时展示待收口与已关闭数量，页面矩阵、Mapper、DTO 和前端摘要完成对齐。
2. `closedPaymentCount` 是查询时点 payment-core 中 `CLOSED` 支付单数量，不区分超时关闭和人工关闭，也不表达渠道侧终态。
3. 本地测试不证明生产数据完整、真实渠道关单、支付失败、资金退回、账务入账或对账一致性；release 四项真实治理阻断不变。

## 445. 2026-09-12 支付监控空集合零值契约 V1.0 验证

### 445.1 本轮补强

1. `PaymentMonitorMapper.findSummary` 的成功、待处理和已关闭数量聚合统一增加 `COALESCE(..., 0)`，消除支付表为空时 `SUM` 返回 `NULL` 的数据库语义泄漏。
2. 空支付集合下，摘要的 `totalCount / successCount / pendingPaymentCount / closedPaymentCount` 均固定为整数 `0`，成功率和成功金额继续分别返回 `0.00%`、`¥0.00`。
3. 告警继续从支付、退款和渠道配置三类事实独立计算；清空支付表不会隐式清除失败退款或停用渠道告警。

### 445.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 监控后端定向测试 | `PaymentMonitorMapperIntegrationTest,PaymentMonitorServiceImplTest,PaymentMonitorControllerTest` `6` 项通过，`0` failures、`0` errors、`0` skipped |
| 空支付集合数量 | `totalCount=0`、`successCount=0`、`pendingPaymentCount=0`、`closedPaymentCount=0`，均为非空数值 |
| 空支付集合展示值 | `successRate=0.00%`、`successAmount=¥0.00` |
| 跨表告警独立性 | 保留一笔失败退款与一个停用渠道时，清空支付表后 `alertCount=2` |
| payment-core 后端全量测试 | `962` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentMonitorMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 无 API 路径、DTO、前端、状态机、schema、索引或迁移变化 |
| 代码提交 | `916bdd66 fix(payment-core): normalize empty monitor summary` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |

### 445.3 当前判断

1. 支付监控摘要的整数数量字段现在具备完整的空集合零值契约，调用方不再接收到由 SQL 空集合聚合产生的 `null`。
2. 本轮只归一化空集合表示，不改变有数据时的状态分类、成功率、成功金额或告警口径；前端既有缺省防御继续作为兼容保护。
3. 本地 H2 空表测试不证明生产数据完整、真实渠道状态、支付成功、退款到账、账务入账、资金一致或生产稳定性；release 四项真实治理阻断不变。

## 446. 2026-09-12 支付监控告警计数对齐 V1.0 验证

### 446.1 本轮补强

1. `PaymentMonitorMapper.findSummary` 增加内部 `wait_callback_count`，只在存在 `WAIT_CALLBACK` 支付单时为摘要贡献一类支付告警。
2. `pendingPaymentCount` 继续统计 `PREPAY_CREATED / WAIT_CALLBACK`，从而保留待处理运营指标；正常等待中的 `PREPAY_CREATED` 不再被误报为回调待收口告警。
3. `alertCount` 与 `findAlerts` 使用相同三类生成条件，表示命中的告警类型数而非受影响记录总数。

### 446.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 监控后端定向测试 | `PaymentMonitorMapperIntegrationTest,PaymentMonitorServiceImplTest,PaymentMonitorControllerTest` `7` 项通过，`0` failures、`0` errors、`0` skipped |
| 三类告警对齐 | 同时存在待回调支付、失败退款和停用渠道时，`alertCount=3` 且 `alerts.length=3` |
| 预付等待边界 | 仅有一笔 `PREPAY_CREATED` 时，`pendingPaymentCount=1`、`alertCount=0`、`alerts=[]` |
| payment-core 后端全量测试 | `963` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentMonitorMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 只新增 Mapper 内部聚合；无 API、DTO、前端、状态机、schema、索引或迁移变化 |
| 代码提交 | `1b3e4859 fix(payment-core): align monitor alert count` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |

### 446.3 当前判断

1. 摘要告警数现在与告警数组使用同一组生成条件，调用方可依赖 `summary.alertCount = alerts.length`，当前三类告警下取值为 `0..3`。
2. `affectedCount` 继续表达单类告警影响的记录数；不能把多个 `affectedCount` 求和后与 `alertCount` 比较。
3. 本地 H2 不证明生产告警事实完整、处置完成、渠道恢复、支付成功、退款到账、账务入账、资金一致或生产稳定性；release 四项真实治理阻断不变。

## 447. 2026-09-12 支付监控最近七日时间窗 V1.0 验证

### 447.1 本轮补强

1. `PaymentMonitorMapper.findRecentTrends` 从“全历史最近七个有数据日期”收敛为数据库会话当天及此前六个自然日。
2. 时间条件采用左闭右开区间 `[CURDATE()-6天, CURDATE()+1天)`，排除窗口前历史记录与明日及更晚的异常时间戳。
3. 保留按日聚合、日期降序和最多七行的现有响应形态；无支付事实的日期不生成零值趋势点。

### 447.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 监控后端定向测试 | `PaymentMonitorMapperIntegrationTest,PaymentMonitorServiceImplTest,PaymentMonitorControllerTest` `8` 项通过，`0` failures、`0` errors、`0` skipped |
| 窗口下界 | 前第六天一笔支付进入趋势，前第七天一笔支付被排除 |
| 窗口上界 | 明日时间戳支付被排除，当天六笔既有支付保持聚合为一行 |
| 顺序与行数 | 返回当天、前第六天两行，继续按 `statDate DESC` 排列且不补空白日期 |
| payment-core 后端全量测试 | `964` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentMonitorMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 无 API、DTO、前端、状态机、schema、索引或迁移变化 |
| 代码提交 | `92a373fb fix(payment-core): bound recent monitor trends` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |

### 447.3 当前判断

1. “最近 7 天”现在表达连续七个自然日，而不是七个离散有数据日期；页面不会再把窗口外历史数据当作近期趋势。
2. 窗口以数据库会话 `CURDATE()` 为准，生产环境仍需确保数据库会话时区与业务时区一致；空白日期当前不补零。
3. 本地 H2 不证明生产 MySQL 时区、索引和执行计划、数据完整、真实支付成功、账务入账、资金一致或生产稳定性；release 四项真实治理阻断不变。

## 448. 2026-09-12 支付监控渠道提交事实 V1.0 验证

### 448.1 本轮补强

1. `PaymentMonitorMapper.findChannelMetrics` 使用关联支付单号的 `t_payment_attempt` 存在性作为渠道提交事实，没有任何支付尝试的支付单不进入渠道表现指标。
2. 使用 `EXISTS` 而非直接联表，确保同一支付单的多次尝试只在渠道总量和成功率分母中计一次。
3. 渠道 `pendingCount`、P1 风险和对应提示统一收敛到 `WAIT_CALLBACK`；全局摘要仍保留 `PREPAY_CREATED / WAIT_CALLBACK` 的宽口径待处理数。

### 448.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 监控后端定向测试 | `PaymentMonitorMapperIntegrationTest,PaymentMonitorServiceImplTest,PaymentMonitorControllerTest` `8` 项通过，`0` failures、`0` errors、`0` skipped |
| 已提交渠道指标 | 三组支付单均有支付尝试，继续返回每组 `totalCount=2`、`successCount=1` 及稳定分组键顺序 |
| 未提交预付边界 | 仅有无尝试 `PREPAY_CREATED` 时，`summary.pendingPaymentCount=1`，`channelMetrics=[]` |
| 页面下钻对齐 | 渠道 `pendingCount>0` 只可能来自 `WAIT_CALLBACK`，与管理端下钻 `status=WAIT_CALLBACK` 一致 |
| payment-core 后端全量测试 | `964` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentMonitorMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 无 API 字段、DTO、前端、状态机、schema、索引或迁移变化 |
| 代码提交 | `12e74564 fix(payment-core): require submitted channel metrics` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |

### 448.3 当前判断

1. 渠道总量、成功率、成功金额和风险不再被尚未形成支付尝试的本地订单污染；多次尝试也不会放大支付单数量。
2. 支付尝试只证明 payment-core 已记录提交动作，不证明渠道已受理；生产渠道表现仍需结合渠道流水、查询、回调和对账事实核验。
3. 本轮没有前端代码变化，因此未重复执行管理端测试与构建；本地 H2 不证明生产 MySQL 执行计划、真实渠道成功率、账务入账、资金一致或生产稳定性，release 四项真实治理阻断不变。

## 449. 2026-09-12 支付监控渠道下钻闭环 V1.0 验证

### 449.1 本轮补强

1. 支付单列表与 CSV 导出新增可选 `channelCode`，服务层统一规范化并拒绝超过 64 字符或包含控制字符的值。
2. `PaymentMapper.findAll / count / findAllForExport` 共享渠道编码精确筛选，避免页面条目、总数和导出文件出现不同口径。
3. 管理端支付单页从结构化路由查询初始化筛选；监控待回调渠道下钻同时携带渠道编码、支付方式和 `WAIT_CALLBACK`，P1 文案不再错误包含 P2 停用渠道。

### 449.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 后端定向测试 | `PaymentControllerTest,PaymentServiceImplTest,PaymentMapperStateTransitionIntegrationTest` `99` 项通过，`0` failures、`0` errors、`0` skipped |
| 渠道筛选一致性 | 正式 Mapper 验证列表、总数和导出只返回 `channelCode=wx_h5` 的一笔支付单 |
| 参数边界 | 控制器透传渠道编码；服务层验证空白裁剪、64 字符上限和控制字符拒绝 |
| 管理端单测 | `15` 项通过，含支付列表结构化路由查询断言 |
| 管理端生产构建 | 通过；最大 JS 包约 `542.88 kB`，保留既有超过 `500 kB` 告警 |
| payment-core 后端全量测试 | `965` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 新增列表/导出查询参数与管理端筛选；无状态机、schema、索引或迁移变化 |
| 代码提交 | `7bba23d8 feat(payment-core): support channel payment drilldown` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |

### 449.3 当前判断

1. 待回调渠道指标现在可以下钻到同一渠道、同一支付方式和同一状态的支付单集合，页面列表与 CSV 使用同一筛选口径。
2. P1 仅表示待回调与退款失败，停用渠道继续为 P2；本轮没有改变告警生成条件、等级或状态机。
3. 本机测试不证明真实渠道、资金、生产数据或稳定性；发布说明正式化、正式签字审批、发布后现场核验和 `master` 稳定观察结束结论仍未形成。

## 450. 2026-09-12 退款告警下钻闭环 V1.0 验证

### 450.1 本轮补强

1. 退款管理页从路由查询初始化 `refundOrderId / paymentOrderId / refundStatus / refundMethod`，并在同一页面查询参数变化时回到第一页重新加载。
2. 新增查询虚拟值 `PENDING`，只在列表查询边界映射为 `REVIEWING / PROCESSING`；日终退款待收口告警改用该值，监控退款失败告警继续使用 `FAIL`。
3. 状态下拉增加“待收口（待审核 / 处理中）”，列表和 CSV 继续共享当前页面筛选；`PENDING` 不进入退款状态迁移或数据库存储。

### 450.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 后端定向测试 | `RefundListMapperIntegrationTest,RefundServiceImplTest,PaymentDayEndServiceImplTest` `54` 项通过，`0` failures、`0` errors、`0` skipped |
| 待收口组合筛选 | `REVIEWING / PROCESSING` 进入结果，`SUCCESS / FAIL` 被排除；列表、计数、导出均为 `2` 笔 |
| 日终动作路由 | 退款待收口动作固定为 `/refunds?refundStatus=PENDING`，建议文案同步为待审核与处理中 |
| 管理端单测 | `17` 项通过，含 `FAIL / PENDING` 路由水合与非标量查询防御 |
| 管理端生产构建 | 通过；最大 JS 包约 `543.32 kB`，保留既有超过 `500 kB` 告警 |
| payment-core 后端全量测试 | `966` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `RefundMapper.xml / PaymentDayEndMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 扩展退款列表查询语义与前端筛选；无状态机、schema、索引或迁移变化 |
| 代码提交 | `12e86bf3 fix(payment-core): align refund alert drilldown` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |

### 450.3 当前判断

1. 监控退款失败告警和日终退款待收口告警现在都能落到与告警口径一致的退款集合，且页面导出不会丢失当前筛选。
2. `PENDING` 仅是管理查询的组合条件，不是新的退款生命周期状态，也不得出现在回调、提交、事件或数据库状态字段中。
3. 本机 H2 与管理端测试不证明真实退款提交、渠道受理、到账、资金一致或生产稳定性；release 四项真实治理阻断不变。

## 451. 2026-09-12 停用渠道告警下钻闭环 V1.0 验证

### 451.1 本轮补强

1. 支付监控 `CHANNEL_DISABLED` 告警动作路由改为 `/payment-config?channelStatus=DISABLED`，使统计口径与目标视图一致。
2. 配置中心新增渠道状态路由解析和客户端筛选，只影响支付渠道配置表；路由值变化时同步筛选并定位渠道区域。
3. 渠道表增加筛选空状态，渠道导出按钮明确为“导出全部渠道”，避免将未筛选治理快照误解为当前视图导出。

### 451.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 监控后端定向测试 | `PaymentMonitorMapperIntegrationTest,PaymentMonitorServiceImplTest,PaymentMonitorControllerTest` `8` 项通过，`0` failures、`0` errors、`0` skipped |
| 告警动作路由 | 正式 Mapper 断言 `CHANNEL_DISABLED` 返回 `/payment-config?channelStatus=DISABLED` |
| 管理端筛选 | `DISABLED` 只返回停用渠道；未知状态和非标量路由值回退全量，非数组数据回退空集合 |
| 管理端单测 | `19` 项通过，`0` failures |
| 管理端生产构建 | 通过；最大 JS 包约 `544.47 kB`，保留既有超过 `500 kB` 告警 |
| payment-core 后端全量测试 | `966` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentMonitorMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 浏览器烟测 | `http://127.0.0.1:4204/payment-config?channelStatus=DISABLED` 可加载且无运行时崩溃；配置接口返回现有模拟 `503`，未形成渠道表数据展示证据 |
| 结构变化 | 仅调整告警动作路由与渠道表前端筛选；无 API 响应、状态机、schema、索引或迁移变化 |
| 代码提交 | `b9699096 fix(payment-core): target disabled channel alerts` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |

### 451.3 当前判断

1. 停用渠道告警现在能定位到配置中心的停用渠道集合，不再让运营人员在完整治理页面中人工扫描。
2. 筛选不改变治理总览或配置数据本身，渠道导出仍为全量治理快照；本轮没有自动启停渠道或修改路由规则。
3. 模拟 `503` 下的浏览器烟测不能证明渠道表现场展示、真实渠道停用或生产可用性；release 四项真实治理阻断不变。

## 452. 2026-09-12 异常中心告警下钻一致性 V1.0 验证

### 452.1 本轮补强

1. 异常中心停用渠道异常的 `recommendedRoute` 改为 `/payment-config?channelStatus=DISABLED`，与支付监控的同类告警入口保持一致。
2. 异常列表首次进入时从路由查询水合六个筛选字段；同页查询参数变化后重新水合、回到第一页、清空勾选项并加载目标集合。
3. 路由转换集中为纯函数，数组、空值和空字符串等非标量或无效值回退各字段默认值，避免畸形 URL 污染接口请求。

### 452.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 异常中心后端定向测试 | `33` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 路由契约 | 使用正式 `PaymentIssueMapper.xml` 展开 `findAll / findByIssueNo`，两条 SQL 均包含 `/payment-config?channelStatus=DISABLED` 且不再包含泛化路由 |
| 管理端路由水合 | 覆盖六个标量筛选字段及数组、空值、空字符串回退；同页路由变化触发重新加载 |
| 管理端单测 | `21` 项通过，`0` failures |
| 管理端生产构建 | 通过；最大 JS 包约 `544.72 kB`，保留既有超过 `500 kB` 告警 |
| payment-core 后端全量测试 | `967` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentIssueMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 仅调整异常推荐路由和管理端筛选水合；无 API 响应、状态机、schema、索引或迁移变化 |
| 代码提交 | `6f0cca2a fix(payment-core): align issue alert drilldown` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |

### 452.3 当前判断

1. 异常中心的停用渠道处置入口与监控告警现已统一，列表和详情共享同一异常联合定义，避免两种读取路径产生路由漂移。
2. 待回调异常继续直接由 `t_payment_order.status='WAIT_CALLBACK'` 聚合，不依赖独立异常记录；本轮没有新增异常持久化或改变处置状态机。
3. 本机测试不证明真实渠道状态、异常已处置、支付或退款资金结果和生产稳定性；发布说明正式化、正式签字审批、发布后现场核验、`master` 稳定观察结束结论仍未形成。

## 453. 2026-09-12 异常告警明细路由重水合 V1.0 验证

### 453.1 本轮补强

1. 异常告警明细台把八个路由查询字段集中转换为列表筛选，首次进入和同页查询变化使用同一逻辑。
2. 同页路由变化后回到第一页，清空旧选中告警、操作错误和成功提示，再按新条件请求列表。
3. 数组、空值和空字符串不再进入筛选请求，分别回退空文本条件或“全部”。

### 453.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 管理端路由水合单测 | 覆盖八个筛选字段的标量输入，以及数组、空值、空字符串回退 |
| 管理端全量单测 | `23` 项通过，`0` failures |
| 管理端生产构建 | 通过；最大 JS 包约 `545.05 kB`，保留既有超过 `500 kB` 告警 |
| 浏览器同页切换 | 同一页面依次从 `receiptQueryState=EXHAUSTED` 切换到 `providerDeliveryStatus=ACCEPTED&ackStatus=待确认`，再清除查询；三个筛选视图均正确重水合且无运行时崩溃 |
| 模拟数据边界 | 现有模拟接口不按查询裁剪返回集合，浏览器结果只证明筛选控件和刷新行为，不证明真实查询结果 |
| 补丁检查 | `git diff --check` 通过 |
| 后端验证 | 本轮无后端代码变化，未重复运行 payment-core 后端全量测试 |
| 结构变化 | 仅调整管理端路由筛选状态；无 API、状态机、schema、索引或迁移变化 |
| 代码提交 | `bec50adc fix(payment-core): refresh issue alert filters` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |

### 453.3 当前判断

1. 任务中心的“自动次数耗尽”和“已受理待确认”入口现在可在同一个告警页面内可靠切换，菜单清除查询也不会遗留旧筛选。
2. 页面只消费既有查询参数和 API，不改变告警派发、回执回查、人工确认或导出规则。
3. 本机模拟数据不证明真实供应商送达、回执状态、人工确认、告警处置或生产稳定性；release 四项真实治理阻断不变。

## 454. 2026-09-12 异常告警待派发精准下钻 V1.0 验证

### 454.1 本轮补强

1. 告警列表查询新增只读 `PENDING_DISPATCH`，展开为已生成、部分失败和派发失败三种真实状态。
2. 列表与计数共享 `alertStatusFilter`，CSV 继续复用列表 Mapper；该虚拟值不写入告警日志或派发流程。
3. 任务中心待派发告警携带 `IN_APP_OUTBOX + PENDING_DISPATCH + 待确认` 三项条件进入明细台，管理端新增可识别的组合状态选项。

### 454.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 后端定向测试 | `PaymentTaskCenterMapperIntegrationTest,PaymentTaskCenterServiceImplTest,PaymentIssueServiceImplTest,PaymentIssueControllerTest` `89` 项通过，`0` failures、`0` errors、`0` skipped |
| 待派发组合筛选 | 正式 `PaymentIssueMapper.xml` 对现有夹具返回 `2` 条，状态依次为部分失败、已生成；列表与计数一致，并排除已确认、外发和非待派发状态 |
| 任务中心动作路由 | `PAYMENT_ISSUE_ALERT_DISPATCH_BACKLOG` 固定携带 `alertChannel=IN_APP_OUTBOX&alertStatus=PENDING_DISPATCH&ackStatus=待确认` |
| 管理端单测 | `23` 项通过，`0` failures；路由水合覆盖 `PENDING_DISPATCH` |
| 管理端生产构建 | 通过；最大 JS 包约 `545.15 kB`，保留既有超过 `500 kB` 告警 |
| 浏览器烟测 | 目标 URL 正确选中站内来源、待派发组合状态和待确认三个筛选控件，页面无运行时崩溃 |
| 模拟数据边界 | 页面接口仍返回固定模拟快照，不证明筛选后的真实数据集合 |
| payment-core 后端全量测试 | `967` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentIssueMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 扩展查询语义和管理端选项；无持久化状态机、schema、索引或迁移变化 |
| 代码提交 | `6bf27d75 fix(payment-core): target pending alert dispatches` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |

### 454.3 当前判断

1. 待派发指标现在可定位到与统计条件等价的来源 outbox 集合，不再用无筛选全量日志替代处置对象。
2. `PENDING_DISPATCH` 只是读取边界的组合条件，三种真实告警状态及派发状态迁移保持不变。
3. 本地测试不证明真实告警积压、外部派发、供应商送达、人员确认或生产稳定性；release 四项真实治理阻断不变。

## 455. 2026-09-12 异常告警待回查口径闭环 V1.0 验证

### 455.1 本轮补强

1. 待回查总量、最老等待和四类执行状态统一使用“有来源关联、外部通道、已派发、`ACCEPTED`、待确认”的基础谓词。
2. 异常告警列表的状态投影与筛选补充来源关联约束，并新增只读汇总筛选 `PENDING_RECEIPT`。
3. 无自动次数耗尽记录时，任务中心直接进入全部待回查集合；有耗尽记录时仍优先进入 `EXHAUSTED`。

### 455.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 后端定向测试 | `PaymentTaskCenterMapperIntegrationTest,PaymentTaskCenterServiceImplTest,PaymentIssueServiceImplTest,PaymentIssueControllerTest` `89` 项通过，`0` failures、`0` errors、`0` skipped |
| 畸形记录排除 | `source_alert_no` 非空但通道为 `IN_APP_OUTBOX`、状态为派发成功的夹具不再进入待回查总量、最老等待或明细组合筛选 |
| 指标收敛 | 待回查总量从错误的 `8` 修正为 `7`，最老等待从错误的 `65` 分钟修正为真实外发集合的 `10` 分钟 |
| 明细组合筛选 | `PENDING_RECEIPT` 的正式 Mapper 列表与计数均为 `7`，五类状态合计同为 `7` |
| 任务中心动作路由 | 无耗尽记录时为 `/payment-issue-alerts?receiptQueryState=PENDING_RECEIPT`；存在耗尽记录时仍为 `EXHAUSTED` |
| 管理端单测 | `23` 项通过，`0` failures；路由水合覆盖 `PENDING_RECEIPT` |
| 管理端生产构建 | 通过；最大 JS 包约 `545.22 kB`，保留既有超过 `500 kB` 告警 |
| 浏览器烟测 | `PENDING_RECEIPT` 正确显示为“待回查（全部）”，页面无运行时崩溃 |
| 模拟数据边界 | 页面接口返回固定模拟快照，只证明路由控件行为，不证明真实待回查集合 |
| payment-core 后端全量测试 | `967` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentTaskCenterMapper.xml / PaymentIssueMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 修正统计和查询谓词；无持久化状态机、schema、索引或迁移变化 |
| 代码提交 | `17f87d7f fix(payment-core): align pending alert receipts` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |

### 455.3 当前判断

1. 待回查总量、年龄、分项状态与明细下钻现在基于同一可审计外发日志集合，不再受畸形站内记录污染。
2. `PENDING_RECEIPT` 只提供全体待回查集合入口，行级返回仍使用首次等待、可执行、退避中、查询中和自动次数耗尽五种状态。
3. 本地 H2 和模拟页面不证明真实供应商受理、最终送达、人员响应、告警处置或生产稳定性；release 四项真实治理阻断不变。

## 456. 2026-09-12 异常告警回执来源防护 V1.0 验证

### 456.1 本轮补强

1. 自动候选、按号人工读取、到期候选、claim、完成回查尝试和最终供应商回执更新统一要求来源告警号非空。
2. 三条状态更新 SQL 同时复验外部通道和已派发状态，并继续守住供应商 `ACCEPTED`、待确认、次数与 claim 时间等既有并发条件。
3. 合法回执测试对象补齐来源关系；新增字段看似合法但缺少来源关系的孤立外发对象，保护自动与人工两条执行入口。

### 456.2 验证结果

| 项目 | 结果 |
| --- | --- |
| Mapper 定向测试 | `PaymentTaskCenterMapperIntegrationTest` `16` 项通过，`0` failures、`0` errors、`0` skipped |
| 自动候选防护 | 孤立 `EMAIL / 已派发 / ACCEPTED / 待确认` 记录不进入到期回查候选 |
| 人工读取防护 | 按孤立告警号读取返回 `null` |
| claim 防护 | 对孤立告警执行 claim 返回 `0`，不得形成查询中状态 |
| 完成尝试防护 | 测试直接将孤立记录置为 `receipt_query_count=1 / QUERYING` 后，完成尝试仍返回 `0` |
| 最终回执防护 | 对同一孤立 `QUERYING` 记录写最终 `DELIVERED / 已确认` 回执仍返回 `0` |
| 合法路径回归 | 有来源关联的记录继续覆盖自动 claim、退避、次数耗尽、人工回查与最终回执写入 |
| payment-core 后端全量测试 | `967` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentTaskCenterMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 只收紧 Mapper 执行谓词和测试夹具；无 API、前端、状态机、schema、索引或迁移变化 |
| 代码与测试提交 | `b6e96d21 fix(payment-core): reject orphan alert receipts`；`ff7f5968 test(payment-core): cover orphan receipt write guards` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`，统一 `blockerCount=1`，内部 `releaseBlockerCount=4` |

### 456.3 当前判断

1. 待回查监控、明细、自动调度、人工读取和条件更新现在共享同一来源关联不变量；即使绕过正常 claim 前置流程构造查询中状态，孤立外发日志也不能完成尝试或写入最终回执。
2. 来源号只证明本地外发日志与来源告警建立关联，不证明供应商已真实受理或最终送达；生产数据仍需独立清洗与核验。
3. 本地 H2 不证明真实供应商回执、人员响应、告警处置或生产稳定性；release 四项真实治理阻断不变。

## 457. 2026-09-12 告警派发与回执状态接续 V1.0 验证

### 457.1 本轮补强

1. 修正外发日志生成状态：供应商 `ACCEPTED` 写为待确认，失败结果保持无需回执，使正式派发输出能进入既有回执候选。
2. 首次派发直接取得 `DELIVERED` 时，在来源派发状态更新后确认同异常顶层 outbox；来源确认未命中则本条失败闭合。
3. 异步回查确认来源时补齐 `issueNo`，Mapper 同时校验来源号、异常号、顶层 outbox、站内通道和待确认状态。

### 457.2 验证结果

| 项目 | 结果 |
| --- | --- |
| `ACCEPTED` 生成状态 | 三通道外发日志均为 `已派发 / 待确认 / warn` |
| 失败生成状态 | 主供应商 `FAILED` 日志保持 `派发失败 / 无需回执 / info`，备用 `ACCEPTED` 正确进入待确认 |
| 首次 `DELIVERED` | 先更新来源派发状态，再以同一 `alertNo + issueNo` 确认来源，操作人使用规范化后的实际发起声明 |
| 直接确认失败闭合 | 来源确认影响 `0` 行时批次记失败并保留来源告警号，不计成功 |
| 来源关联防护 | 同异常顶层 outbox 更新 `1` 行；错异常和带父来源号的嵌套 outbox 均更新 `0` 行 |
| 服务与 Mapper 联合定向 | `PaymentIssueAlertDeliveryServiceImplTest,PaymentTaskCenterMapperIntegrationTest` `66` 项通过 |
| payment-core 后端全量测试 | `970` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentTaskCenterMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 调整服务状态映射、派发结果和来源确认谓词；无 API、前端、schema、索引或迁移变化 |
| 代码提交 | `7fc7d7a1 fix(payment-core): queue accepted alert receipts`；`712f589e fix(payment-core): confirm direct alert deliveries`；`3ee427ce fix(payment-core): fail closed direct alert confirmation` |

### 457.3 当前判断

1. 真实派发生成的 `ACCEPTED` 日志现在与回查候选契约接通，不再依赖测试手工制造待确认状态。
2. 首次明确 `DELIVERED` 不再悬空来源确认，且错异常、嵌套来源或并发不命中不能被报告为成功。
3. 本地 mock 与 H2 只证明内部状态映射、调用顺序和 SQL 条件，不证明供应商真实受理、最终送达、人员响应或生产稳定性；release 四项真实治理阻断不变。

## 458. 2026-09-12 告警来源确认幂等裁决 V1.0 验证

### 458.1 本轮补强

1. 来源确认条件更新返回 `0` 时追加只读状态复验，不再无条件继续提交外发 `DELIVERED` 结果。
2. 只接受同一异常的顶层站内来源已经确认作为幂等成功；缺失、错异常、嵌套来源或仍待确认均失败闭合。
3. 首次直接送达和异步回执送达复用同一裁决，避免两条路径形成不同的一致性语义。

### 458.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 条件更新成功 | 来源确认影响行数大于 `0` 时直接成功，不执行额外状态查询 |
| 并发幂等成功 | 条件更新为 `0` 且同异常顶层来源已确认时，本条仍计成功 |
| 关联失败闭合 | 条件更新为 `0` 且已确认复验为 false 时，本条计失败并保留失败来源号 |
| Mapper 正确来源 | 确认前查询 false，条件更新后查询 true |
| Mapper 错关联 | 同告警错 `issueNo`、带父来源号的嵌套 outbox 均查询 false |
| 服务与 Mapper 联合定向 | `PaymentIssueAlertDeliveryServiceImplTest,PaymentTaskCenterMapperIntegrationTest` `67` 项通过 |
| payment-core 后端全量测试 | `971` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentTaskCenterMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 新增内部 Mapper 布尔查询并统一来源裁决；无 HTTP API、前端、schema、索引或迁移变化 |
| 代码提交 | `70c6cfb5 fix(payment-core): verify alert source acknowledgement` |

### 458.3 当前判断

1. 外发终态与来源确认不再因忽略 `0` 行结果而静默分叉，真正的已确认并发仍可幂等收口。
2. 幂等依据来自数据库中同异常顶层来源的明确已确认状态，不从调用次数、异常文本或外发日志自行推导。
3. 本地 mock/H2 不证明生产事务隔离、真实并发时序、供应商最终送达或人员响应；release 四项真实治理阻断不变。

## 459. 2026-09-12 告警回查候选来源完整性 V1.0 验证

### 459.1 本轮补强

1. 三条回查候选读取从“来源号非空”收紧为“同异常、顶层、待确认的站内来源真实存在”。
2. 服务在 claim 前复验来源资格，过期或绕过候选读取的错误对象不得消耗查询次数、供应商调用或共享限流额度。
3. 已确认来源、孤立记录、错异常来源和嵌套 outbox 来源均被排除，合法待确认来源保持原有 claim、退避、耗尽与人工强制回查行为。

### 459.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 自动候选 | 合法记录进入；孤立、错异常和嵌套来源记录不进入 |
| 人工候选 | 批量与按号读取均只返回同异常顶层待确认来源记录 |
| 来源资格查询 | 合法来源为 true；错异常和嵌套来源为 false |
| 服务前置拒绝 | 错来源不执行 claim，不调用回执适配器，不获取限流额度 |
| 合法链路回归 | claim、防重复 claim、失败退避、次数耗尽、人工强制和最终回执更新继续通过 |
| 服务与 Mapper 联合定向 | `PaymentIssueAlertDeliveryServiceImplTest,PaymentTaskCenterMapperIntegrationTest` `68` 项通过 |
| payment-core 后端全量测试 | `972` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentTaskCenterMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 新增内部来源资格查询与候选关联约束；无 HTTP API、前端、schema、索引或迁移变化 |
| 代码提交 | `b7b4609c fix(payment-core): validate alert receipt sources` |

### 459.3 当前判断

1. `source_alert_no` 已从非空标记提升为可验证的来源身份链，畸形记录不会先调用真实供应商再在最终确认阶段失败。
2. 来源已确认后不再回查同源 `ACCEPTED` 外发日志，避免无业务收益的尝试次数和限流额度消耗。
3. 本地 mock/H2 不证明生产 MySQL 执行计划、跨事务竞态、真实供应商受理或最终送达；release 四项真实治理阻断不变。

## 460. 2026-09-12 告警回执原子 claim 来源裁决 V1.0 验证

### 460.1 本轮补强

1. claim 参数从告警号和次数扩展为同时携带来源告警号与异常号，数据库更新边界复验候选读取到的完整来源元组。
2. claim 单语句同时判断外发资格与同异常顶层待确认来源资格，消除服务前置查询之后、次数递增之前的可见竞态窗口。
3. 同表来源查询使用不可合并的 `DISTINCT` 派生集合，规避 MySQL 目标表更新限制，并保持现有 H2 MySQL 兼容测试可执行。

### 460.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 参数关联 | claim 同时匹配 `alertNo / sourceAlertNo / issueNo` |
| 错来源元组 | 对合法外发行传入另一来源号时影响行数为 `0` |
| 已确认来源 | 外发行仍为 `ACCEPTED / 待确认`、来源已确认时 claim 返回 `0` |
| 合法来源 | 自动 claim、防重复、退避、耗尽与人工强制回查保持通过 |
| claim 失败副作用 | 不选择或调用回执适配器，不获取共享限流额度，不完成查询尝试 |
| SQL 兼容策略 | `DISTINCT` 物化派生来源集合；H2 MySQL 模式正式 Mapper 测试通过 |
| 服务与 Mapper 联合定向 | `PaymentIssueAlertDeliveryServiceImplTest,PaymentTaskCenterMapperIntegrationTest` `68` 项通过 |
| payment-core 后端全量测试 | `972` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentTaskCenterMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 只扩展内部 Mapper 参数与 claim SQL；无 HTTP API、前端、schema、索引或迁移变化 |
| 代码提交 | `5efd2028 fix(payment-core): atomically claim alert receipts` |

### 460.3 当前判断

1. 回查资格与尝试次数递增现在由同一数据库更新语句裁决，不再依赖服务层两次语句之间状态保持不变。
2. claim 成功表示该语句的数据库快照中来源资格成立；随后来源被其他合法动作确认时，最终回执仍按既有已确认幂等裁决收口。
3. 本地 H2 不证明生产 MySQL 派生表物化、执行计划、锁等待和并发吞吐；真实供应商与生产结论仍需独立证据，release 四项真实治理阻断不变。

## 461. 2026-09-12 告警最终回执来源围栏 V1.0 验证

### 461.1 本轮补强

1. 最终回执更新参数补齐来源告警号和异常号，在数据库写入边界复验候选读取到的完整外发身份元组。
2. 最终 `DELIVERED` 更新只接受同异常顶层 `IN_APP_OUTBOX` 来源真实存在的外发行；缺失、错异常、错误来源号和嵌套来源均失败闭合。
3. 来源在供应商查询期间被另一合法动作确认时仍允许保存真实送达证据，随后沿用既有已确认复验幂等完成来源收口，不改写原确认审计字段。

### 461.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 参数关联 | 最终回执实体与更新同时携带并匹配 `alertNo / sourceAlertNo / issueNo` |
| 错误来源号 | 对合法查询中外发行传入另一来源号时影响行数为 `0` |
| 嵌套来源 | 来源自身带 `sourceAlertNo` 时最终更新影响行数为 `0` |
| 已确认来源 | 同异常顶层来源已确认后，仍允许保存供应商真实 `DELIVERED` 回执 |
| 合法链路回归 | 最终更新成功后继续通过来源已确认幂等复验，不覆盖确认人和确认时间 |
| SQL 兼容策略 | `DISTINCT` 物化派生来源集合；H2 MySQL 模式正式 Mapper 测试通过 |
| 服务与 Mapper 联合定向 | `PaymentIssueAlertDeliveryServiceImplTest,PaymentTaskCenterMapperIntegrationTest` `68` 项通过 |
| payment-core 后端全量测试 | `972` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentTaskCenterMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 只扩展内部最终回执实体与更新 SQL；无 HTTP API、前端、schema、索引或迁移变化 |
| 代码提交 | `67b300a7 fix(payment-core): fence final alert receipts` |

### 461.3 当前判断

1. 最终回执写入不再只信任外发告警号，而是以候选快照中的来源身份元组和数据库中的真实顶层来源共同裁决。
2. “来源已确认”与“来源无效”被明确区分：前者保留后来到达的真实供应商终态，后者拒绝写入；既避免丢失审计证据，也不让畸形关联冒充送达。
3. 本地 mock/H2 不证明生产 MySQL 派生表物化、执行计划、锁竞争、真实供应商回执或生产稳定性；release 四项真实治理阻断不变。

## 462. 2026-09-12 告警回执尝试完成来源围栏 V1.0 验证

### 462.1 本轮补强

1. 完成回查尝试参数补齐来源告警号和异常号，在数据库更新边界复验 claim 候选的完整身份元组。
2. 未送达、失败、适配器缺失、查询异常和共享限流结果只允许写入关联同异常顶层 `IN_APP_OUTBOX` 来源的外发行。
3. 合法 claim 后来源先被确认时，仍保存本次真实查询结果和退避事实；错误、缺失或嵌套来源失败闭合。

### 462.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 参数关联 | 完成尝试同时匹配 `alertNo / sourceAlertNo / issueNo` |
| 孤立与错异常 | 缺失来源、来源属于另一异常时影响行数均为 `0` |
| 错误来源号 | 对合法查询中外发行传入另一来源号时影响行数为 `0` |
| 嵌套来源 | 来源自身带父来源号时影响行数为 `0` |
| 已确认来源 | 同异常顶层来源已确认后仍允许保存本次查询结果，影响行数为 `1` |
| 失败路径回归 | 适配器缺失、选择异常、查询异常、回执冲突、限流及失败结果继续完成尝试 |
| 退避与耗尽 | 指数退避、错误次数拒绝、自动次数耗尽和人工强制行为保持通过 |
| SQL 兼容策略 | `DISTINCT` 物化派生来源集合；H2 MySQL 模式正式 Mapper 测试通过 |
| 服务与 Mapper 联合定向 | `PaymentIssueAlertDeliveryServiceImplTest,PaymentTaskCenterMapperIntegrationTest` `68` 项通过 |
| payment-core 后端全量测试 | `972` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentTaskCenterMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 只扩展内部完成尝试 Mapper 参数与 SQL；无 HTTP API、前端、schema、索引或迁移变化 |
| 代码提交 | `06d8c962 fix(payment-core): fence receipt attempt completion` |

### 462.3 当前判断

1. claim、非终态尝试完成和最终送达更新现在都绑定候选读取到的来源身份元组，三段写入不再采用强弱不一的来源资格。
2. 来源先确认不抹去已经发生的供应商查询事实，但外发行自身的乐观状态条件继续阻止重复完成或失效对象重新进入退避链。
3. 本地 mock/H2 不证明生产 MySQL 派生表物化、执行计划、锁竞争、真实供应商查询或生产稳定性；release 四项真实治理阻断不变。

## 463. 2026-09-12 告警派发来源行锁裁决 V1.0 验证

### 463.1 本轮补强

1. 每条告警派发在独立事务内、任何通知供应商调用前，锁定并复验候选对应的顶层待确认 outbox。
2. 待派发候选与来源状态更新共享 `alertNo + issueNo + IN_APP_OUTBOX + 顶层 + 待确认 + 可派发状态` 资格。
3. 锁后资格丢失按并发跳过处理；取得执行权后来源状态更新未命中则本条失败，不继续来源确认。

### 463.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 候选完整性 | 带父来源号的嵌套 outbox 不进入待派发集合 |
| 锁定身份 | 正确 `alertNo + issueNo` 的顶层待确认来源可锁定；错误异常号和嵌套来源不可锁定 |
| 并发过期候选 | 锁后资格为 false 时不调用通知器、不插入外发日志、不更新来源状态，处理数保持 `0` |
| 状态推进 | 来源更新同时复验身份、顶层、待确认和原可派发状态；成功后不再满足锁定资格 |
| 更新失败闭合 | 来源状态更新影响 `0` 行时本条计失败，明确送达场景也不继续确认来源 |
| 逐条隔离 | 继续使用既有 `REQUIRES_NEW`，单条异常不阻断后续候选 |
| 服务与 Mapper 联合定向 | `PaymentIssueAlertDeliveryServiceImplTest,PaymentTaskCenterMapperIntegrationTest` `70` 项通过 |
| payment-core 后端全量测试 | `974` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentTaskCenterMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 新增内部行锁查询并收紧候选与状态更新；无 HTTP API、前端、schema、索引或迁移变化 |
| 代码提交 | `3ead0ac5 fix(payment-core): lock alert dispatch sources` |

### 463.3 当前判断

1. 自动与人工入口现在以来源行锁串行裁决同一 outbox 的当前派发执行权，不再只依赖任务级自动租约或锁外候选快照。
2. 行锁覆盖外部通知调用可防止正常并发重复派发，但会增加锁持有时间；进程在供应商已接收、事务尚未提交时崩溃，数据库回滚仍无法撤销外部副作用，因此不能宣称 exactly-once。
3. 生产仍需验证 MySQL 隔离级别、锁等待与超时、人工和自动并发吞吐，以及供应商基于稳定 `alertNo` 的幂等行为和真实回执；release 四项真实治理阻断不变。

## 464. 2026-09-12 告警派发历史归属校验 V1.0 验证

### 464.1 本轮补强

1. 成功去重、失败次数和最近派发日志统一验证外发日志与来源 outbox 的异常号一致。
2. 来源必须真实存在、通道为 `IN_APP_OUTBOX` 且自身无父来源号；孤立、错异常和嵌套来源历史全部排除。
3. 来源是否已确认不影响既有外发事实的归属和审计可见性。

### 464.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 合法成功历史 | 同异常顶层来源的 IM 已派发记录继续返回成功 |
| 错异常成功 | 同来源号但异常号错误的 SMS 已派发记录不构成成功 |
| 失败次数 | 合法失败与更新的错异常失败并存时只统计合法记录，结果为 `1` |
| 最近派发 | 更新的错异常日志不抢占最近记录，返回合法失败日志 |
| 孤立与嵌套来源 | 成功判断为 false，最近日志返回空 |
| 已确认来源 | 顶层来源已确认后，合法历史仍可参与审计判断 |
| 服务与 Mapper 联合定向 | `PaymentIssueAlertDeliveryServiceImplTest,PaymentTaskCenterMapperIntegrationTest` `71` 项通过 |
| payment-core 后端全量测试 | `975` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentTaskCenterMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 只收紧三条内部历史查询；无 HTTP API、Java 接口、前端、schema、索引或迁移变化 |
| 代码提交 | `2e99fc1a fix(payment-core): validate alert delivery history` |

### 464.3 当前判断

1. 派发决策消费的历史事实现在具备可验证来源身份，畸形记录不再影响成功跳过、失败补发上限或最近日志保护。
2. 该修复不清洗或删除存量畸形记录，只阻止其进入执行判断；历史治理仍需可审计识别与人工批准。
3. 本地 H2 不证明生产 MySQL 相关子查询执行计划、存量数据质量、真实供应商派发或生产稳定性；release 四项真实治理阻断不变。

## 465. 2026-09-12 告警派发成功证据收紧 V1.0 验证

### 465.1 本轮补强

1. 通道成功去重在合法来源归属与本地已派发之外，新增供应商 `ACCEPTED / DELIVERED` 证据条件。
2. 服务端防重放去掉“只要已派发即成功”分支，与通知结果规范化的成功集合共用同一口径。
3. 供应商容量统计保持现状，不将容量与成功去重两种不同决策语义混入同一修改。

### 465.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 缺失供应商证据 | 同异常顶层来源下“已派发 + NULL”不构成通道成功 |
| 明确失败证据 | 同异常顶层来源下“已派发 + FAILED”不构成成功 |
| 成功证据 | `ACCEPTED` 与 `DELIVERED` 均继续构成通道成功 |
| 防重放 | 最新“已派发 + FAILED”不阻断通知器，补发后正常保存 `ACCEPTED` |
| 联合定向测试 | `PaymentIssueAlertDeliveryServiceImplTest,PaymentTaskCenterMapperIntegrationTest` `72` 项通过 |
| payment-core 后端全量测试 | `976` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentTaskCenterMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 只收紧内部 SQL 与服务判断；无 HTTP API、前端、schema、索引或迁移变化 |
| 代码提交 | `619490df fix(payment-core): require alert delivery evidence` |

### 465.3 当前判断

1. 本地执行状态不再单独充当供应商成功证据，去重和防重放与正式通知结果的成功集合对齐。
2. 存量“已派发但缺少供应商成功状态”记录会被允许补发；这避免永久漏发，但不能排除证据丢失时的重复通知。
3. 本地 mock/H2 不证明生产历史已清洗、供应商幂等、真实送达或生产稳定性；上线前需单独审计存量数据并验证幂等键，release 四项真实治理阻断不变。

## 466. 2026-09-12 告警本地护栏结果隔离 V1.0 验证

### 466.1 本轮补强

1. 通道重试失败数和供应商熔断失败数只统计 `FAILED / SEND_EXCEPTION`，不再统计未发生通知调用的本地护栏结果。
2. 最近派发日志只从 `ACCEPTED / DELIVERED / FAILED / SEND_EXCEPTION` 中选择，防止护栏日志刷新冷却或防重放的时间基准。
3. Mapper 测试加入 `STR_TO_DATE` 的 H2 兼容别名，使两条供应商时间窗口统计 SQL 可被真正执行。

### 466.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 真实失败 | `FAILED` 与 `SEND_EXCEPTION` 均进入通道重试与供应商熔断计数 |
| 本地护栏 | 更新的 `RATE_LIMITED / RETRY_COOLDOWN_ACTIVE / PROVIDER_CIRCUIT_OPEN` 均不进入失败计数 |
| 最近派发 | 上述护栏日志不抢占最近记录，结果仍为较早的 `SEND_EXCEPTION` |
| 首次定向测试 | 失败：H2 未注册 MySQL `STR_TO_DATE`；未记为业务通过 |
| 修正后联合定向 | `PaymentIssueAlertDeliveryServiceImplTest,PaymentTaskCenterMapperIntegrationTest` `73` 项通过 |
| payment-core 后端全量测试 | `977` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | `PaymentTaskCenterMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 结构变化 | 只收紧内部历史 SQL 和扩展测试夹具；无 HTTP API、前端、schema、索引或迁移变化 |
| 代码提交 | `2fd4e7e8 fix(payment-core): isolate alert guard outcomes` |

### 466.3 当前判断

1. 本地限流、冷却、防重放和熔断拒绝仍保留完整审计日志，但不再被误解为供应商已失败的外部事实。
2. 冷却和熔断时间窗口现在能随真实失败老化而自然恢复，不被周期调度产生的护栏日志自我续期。
3. 本地 H2 不证明生产 MySQL 执行计划、存量结果码完整、真实供应商熔断恢复或并发限流；供应商限流的原子配额预留仍是后续缺口，release 四项真实治理阻断不变。

## 467. 2026-09-12 告警供应商派发原子限流 V1.0 验证

### 467.1 本轮补强

1. 新增按 `providerCode + channelCode` 唯一约束的数据库共享固定窗口桶，替换告警成功历史计数后的非原子判断。
2. 派发前通过独立 `REQUIRES_NEW` 事务初始化并原子占额，事务完成后才调用通知器；真实调用失败不返还额度，本地限流拒绝继续复用既有告警日志审计。
3. 超大分钟窗口转换秒数发生整数溢出时闭合拒绝，不调用共享桶或通知器。

### 467.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 同桶阈值 | 同一供应商与通道前两次占额成功，第三次返回耗尽 |
| 维度隔离 | 不同供应商或不同通道分别持有独立桶 |
| 窗口重置 | 固定窗口过期后下一次占额将计数重置为 `1` |
| 参数边界 | 供应商与通道统一裁剪并转大写，非正窗口与阈值收敛到 `1`，缺失维度闭合失败 |
| 外部调用顺序 | 先取得共享额度，再调用通知器；通知器抛异常后无释放动作 |
| 溢出保护 | `40000000` 分钟策略不调用限流 Mapper 或通知器，记录 `RATE_LIMITED` |
| 联合定向测试 | 新 Mapper、限流服务和派发服务共 `60` 项通过 |
| payment-core 后端全量测试 | `983` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | 全部 Mapper XML 的 `xmllint` 与 `git diff --check` 通过 |
| 数据结构 | 新增共享桶表、全量 schema 定义和 `2026-09-12-add-issue-alert-delivery-rate-limit.sql` |
| 代码提交 | `f26b1876 fix(payment-core): atomically reserve alert delivery quota` |

### 467.3 当前判断

1. 正常数据库并发下，供应商与通道维度的限流判断和容量占用已共享同一个原子更新点，不再依赖最终成功日志倒推容量。
2. 占额在外部调用前独立提交，因此真实失败同样消耗供应商请求容量；代价是占额后、调用前进程崩溃会暂时损失一个额度，固定窗口到期后恢复。
3. 本地 H2/mock 不证明生产 MySQL 锁竞争、多实例吞吐、供应商真实限额、幂等或送达稳定性；必须在目标环境独立压测与校准，release 四项真实治理阻断不变。

## 468. 2026-09-12 告警供应商连续失败熔断恢复 V1.0 验证

### 468.1 本轮补强

1. 熔断判断由窗口失败总数改为窗口内最近三条合法真实供应商结果，落实“连续失败三次”语义。
2. 最近结果查询增加同异常顶层来源归属校验，并要求本地已派发匹配 `ACCEPTED / DELIVERED`、本地派发失败匹配 `FAILED / SEND_EXCEPTION`。
3. 告警日志新增供应商、通道、创建时间组合索引与独立迁移，支撑最近结果读取。

### 468.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 连续失败 | 最近三条为 `FAILED / SEND_EXCEPTION / FAILED` 时主供应商熔断并切换后备 |
| 成功恢复 | 较早有三次失败，但最近三条包含 `ACCEPTED` 时继续调用主供应商 |
| 本地护栏 | `RATE_LIMITED / RETRY_COOLDOWN_ACTIVE / PROVIDER_CIRCUIT_OPEN` 不进入最近真实结果 |
| 来源完整性 | 孤立来源和错异常来源不进入供应商序列 |
| 状态一致性 | “本地已派发 + FAILED”等矛盾组合不构成真实成功或失败证据 |
| 联合定向测试 | `PaymentIssueAlertDeliveryServiceImplTest,PaymentTaskCenterMapperIntegrationTest` 共 `77` 项通过 |
| payment-core 后端全量测试 | `985` 项通过，`0` failures、`0` errors、`0` skipped |
| Mapper 与补丁检查 | 全部 Mapper XML 的 `xmllint` 与 `git diff --check` 通过 |
| 数据结构 | 新增 `idx_issue_alert_provider_delivery` 及 `2026-09-12-add-issue-alert-provider-delivery-index.sql` |
| 代码提交 | `0f5c6296 fix(payment-core): reset alert circuit on recovery` |

### 468.3 当前判断

1. 成功供应商事实现在能够立即打断历史失败序列，避免供应商恢复后仍因窗口累计失败被错误摘流。
2. 熔断只消费来源可追溯、状态自洽的真实通知结果，不再受本地拒绝或畸形历史污染。
3. H2/mock 不证明生产 MySQL 执行计划、索引选择、多实例同时失败时的阈值精度、真实恢复或送达稳定性；release 四项真实治理阻断不变。

## 469. 2026-09-12 告警供应商半开探针协调 V1.0 验证

### 469.1 本轮补强

1. 窗口内最近三条真实结果仍全部失败时继续记录 `PROVIDER_CIRCUIT_OPEN`；窗口到期但全量最近三条仍连续失败时进入半开阶段。
2. 半开阶段按 `providerCode + channelCode` 的 SHA-256 范围键复用数据库任务租约，只允许一个实例调用目标供应商；未持锁实例记录 `PROVIDER_CIRCUIT_HALF_OPEN_BUSY` 并尝试后备供应商。
3. 探针租约获取使用既有独立事务，逻辑租约覆盖通知器调用与外发日志事务，数据库行锁不跨越网络；该版事务完成后立即释放，失败后的持续冷却缺口由第 470 轮修正。

### 469.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 半开识别 | 10 分钟窗口查询为空、全量最近三条均失败时进入半开探针 |
| 唯一探针 | 租约获取成功才调用主供应商；范围键以 `AP:` 开头且总长 64，适配既有字段 |
| 事务可见性 | 单测验证派发事务完成前不释放探针租约；该版完成回调后立即释放，后续由第 470 轮调整 |
| 并发拒绝 | 租约获取失败记录 `PROVIDER_CIRCUIT_HALF_OPEN_BUSY`，主供应商不调用并切换后备 |
| 真实结果隔离 | 半开忙等为本地护栏，不进入 `FAILED / SEND_EXCEPTION` 真实失败序列 |
| 联合定向测试 | `PaymentIssueAlertDeliveryServiceImplTest,PaymentTaskLeaseServiceTest,PaymentTaskCenterMapperIntegrationTest` 共 `84` 项通过 |
| payment-core 后端全量测试 | `987` 项通过，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` 通过 |
| 数据结构 | 复用 `t_payment_task_lease`，无 schema 或迁移变化 |

### 469.3 当前判断

1. 恢复窗口已从“多个实例均可试探”收敛为供应商与通道维度的数据库共享唯一探针，同时保留后备供应商兜底。
2. 探针失败通过新真实失败日志重新打开窗口，成功通过真实成功日志打断序列，不维护第二套易漂移的熔断事实表。
3. 本地测试不证明生产 MySQL 租约竞争、事务完成时序、进程崩溃恢复、真实供应商幂等、恢复或送达稳定性；release 四项真实治理阻断不变。

## 470. 2026-09-12 告警供应商失败探针冷却 V1.0 验证

### 470.1 本轮补强

1. 修正半开失败后立即释放租约导致连续探测的问题；探针租期由 30 秒调整为 `600 + 30` 秒，覆盖熔断恢复窗口并保留固定余量。
2. 租约新增 `retainUntilExpiry()`，只停止续期、不清空数据库 owner 与到期时间；普通 `close()` 的立即释放契约保持不变。
3. 探针事务完成时统一保留租约自然过期。成功结果通过告警日志关闭熔断并绕过旧租约；失败结果继续保持连续失败事实，由未过期租约阻止下一探针。

### 470.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 冷却租期 | 半开租约以 `630` 秒获取，不再使用 30 秒短租约 |
| 停止续期 | `retainUntilExpiry()` 取消后台续期任务 |
| 保留占用 | 保留后不调用 `releaseTaskLease`，数据库租约等待自然过期 |
| 幂等结束 | 重复保留、以及保留后再 `close()` 均不重复取消或误释放 |
| 普通租约回归 | 既有 `close()` 仍只释放一次，自动任务语义不变 |
| 联合定向测试 | `PaymentIssueAlertDeliveryServiceImplTest,PaymentTaskLeaseServiceTest,PaymentTaskCenterMapperIntegrationTest` 共 `85` 项通过 |
| payment-core 后端全量测试 | `988` 项通过，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` 通过 |
| 数据结构 | 继续复用 `t_payment_task_lease`，无 schema 或迁移变化 |

### 470.3 当前判断

1. 失败探针不再因事务完成释放而触发连续探测，数据库共享租约承担跨实例恢复冷却；成功探针仍由真实成功日志恢复正常调用。
2. 固定 30 秒余量覆盖当前通知器秒级超时与事务提交，但不是无限时长保证；异常长调用依靠租约后台续期，完成后剩余期限仍需目标环境验证。
3. 本地 mock/H2 不证明生产 MySQL 时钟、续期竞争、实例崩溃、真实供应商幂等、恢复或送达稳定性；release 四项真实治理阻断不变。

## 471. 2026-09-12 日终成功差异状态一致性 V1.0 验证

### 471.1 本轮补强

1. 将支付成功差异纳入日终批次警告状态判定，使批次状态与既有对账准入阻断口径一致。
2. 新增幂等历史迁移，仅纠正“成功差异大于零但批次为完成”的派生状态与样式，不改写任何金额、数量或交易事实。
3. 新增仅存在支付成功差异的服务用例，避免其他异常同时存在时掩盖该条件遗漏。

### 471.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 单一差异条件 | `paymentSuccessGapCount=1` 且渠道异常、内部异常、待退款均为 `0` 时写入 `WARNING/warn` |
| 完成态口径 | 四类未收口事实全部为 `0` 时才保留 `COMPLETED/success` |
| 历史纠偏 | 新增 `2026-09-12-fix-day-end-success-gap-status.sql`，条件更新可幂等重跑 |
| 日终定向测试 | `PaymentDayEndServiceImplTest` 共 `7` 项通过 |
| payment-core 后端全量测试 | `989` 项通过，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` 通过 |
| 前端与结构 | 无前端、表结构或索引变化 |

### 471.3 当前判断

1. 新生成批次不会再出现“对账被禁止但批次标为完成”的内部口径冲突，总览完成批次数也不再继续累积此类错误状态。
2. 迁移文件已经提交但未在目标 MySQL 执行；上线前必须先统计命中行、完成备份并在执行后复核状态分布与总览汇总。
3. 本地 mock 测试只证明服务判定，不证明真实账务事实已经收口、历史数据已迁移或财务对账已经通过；release 四项真实治理阻断不变。

## 472. 2026-09-12 日终成功差异备注一致性 V1.0 验证

### 472.1 本轮补强

1. 修正日终系统备注遗漏支付成功差异条件的问题，消除警告状态与“可进入次日对账”文字结论并存的矛盾。
2. 对仅有支付成功差异的回归用例补充备注正反断言，直接覆盖错误文案而非只验证批次状态。
3. 新增严格条件的历史备注迁移，只改写旧代码生成的固定全清文案，不覆盖人工或其他系统备注。

### 472.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 单一差异备注 | 成功差异为 `1`、其余异常为 `0` 时包含“成功差异1笔” |
| 错误结论排除 | 同一场景备注不包含“可进入次日对账” |
| 全清条件 | 四类未收口事实全部为 `0` 时才生成原全清备注 |
| 历史纠偏范围 | 仅匹配成功差异大于零且等于旧版固定全清文案的记录，可幂等重跑 |
| 日终定向测试 | `PaymentDayEndServiceImplTest` 共 `7` 项通过 |
| payment-core 后端全量测试 | `989` 项通过，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` 通过 |
| 前端与结构 | 无前端、表结构或索引变化 |

### 472.3 当前判断

1. 新批次的持久化状态、批次备注和总览对账准入现在使用同一组未收口事实，不再向财务展示相互矛盾的结论。
2. 历史备注迁移尚未在目标 MySQL 执行；上线前必须先统计精确命中集合，并在执行后抽查中文文案、金额格式和导出结果。
3. 本地 mock 测试不证明历史生产数据已修复、真实支付差异已处理或财务已批准进入对账；release 四项真实治理阻断不变。

## 473. 2026-09-12 日终成功差异告警可见性 V1.0 验证

### 473.1 本轮补强

1. 日终总览告警新增 `PAYMENT_SUCCESS_GAP`，直接承接已经阻断对账的成功差异事实。
2. 该告警任一笔即标记 P1，并跳转完整支付异常中心；不把聚合差异错误收窄为单一超额收款类型。
3. 管理端顶部未收口摘要和批次提示补充成功差异，使卡片、摘要和对账准入结论保持一致。

### 473.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 告警集合 | 总览固定返回成功差异、渠道异常、内部事件异常和待收口退款四张卡 |
| 成功差异等级 | `latestPaymentSuccessGapCount=3` 时影响数为 `3`、等级为 `P1` |
| 处理入口 | 成功差异跳转 `/payment-issues`，覆盖回调、事件和超额收款聚合来源 |
| 日终定向测试 | `PaymentDayEndServiceImplTest` 共 `7` 项通过 |
| payment-core 后端全量测试 | `989` 项通过，`0` failures、`0` errors、`0` skipped |
| 管理端单测 | `23` 项通过，`0` fail |
| 管理端生产构建 | Vite 构建通过；主包约 `545.32 kB`，保留既有体积提示 |
| 补丁检查 | `git diff --check` 通过 |

### 473.3 当前判断

1. 成功差异不再只藏在事实卡和准入文案中，财务与运营可从首位 P1 告警直接进入既有异常处理台。
2. `/payment-issues` 是聚合入口，不保证每类成功差异都已有完全同构的独立处置单；回调、事件和超额收款仍按异常中心现有明细分别核查。
3. 本地测试与构建不证明真实渠道回调、下游消费、订单应收、资金差异或人工处置已经完成；release 四项真实治理阻断不变。

## 474. 2026-09-12 日终默认业务日时区一致性 V1.0 验证

### 474.1 本轮补强

1. 管理端默认业务日从浏览器本地“昨天”改为上海业务时区前一日，与后端缺省口径一致。
2. 日期计算提取为无页面依赖的前端工具，并允许测试注入当前时钟；页面只消费格式化结果。
3. 使用 UTC 日历对象执行减日，避免运行环境本地时区再次介入上海日历边界。

### 474.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 跨时区边界 | `2026-09-11T16:30:00Z` 对应上海 9 月 12 日 00:30，默认业务日为 `2026-09-11` |
| 跨年边界 | `2025-12-31T16:30:00Z` 对应上海 2026 年 1 月 1 日，默认业务日为 `2025-12-31` |
| 非法时钟 | 无效 `Date` 抛出明确 `TypeError`，不生成畸形日期 |
| 异地进程回归 | `TZ=America/Los_Angeles npm test` 共 `26` 项通过，`0` fail |
| 管理端生产构建 | Vite 构建通过；主包约 `545.69 kB`，保留既有体积提示 |
| 补丁检查 | `git diff --check` 通过 |
| 后端与数据结构 | 无后端、API、schema、索引或迁移变化，未重复运行后端测试 |

### 474.3 当前判断

1. 无论运营浏览器位于哪个时区，页面初始日终账期都与后端上海业务时区的前一日口径一致。
2. 当前日期仍来自客户端系统时钟；若终端时钟本身漂移，默认值可能错误，操作员需在提交前核对，服务端仍负责格式校验但不会替代用户显式选择。
3. 本地单测与构建不证明生产终端时钟、真实关账日、账期数据完整或财务确认；release 四项真实治理阻断不变。

## 475. 2026-09-12 日终开放账期拒绝 V1.0 验证

### 475.1 本轮补强

1. 后端权威拒绝上海当天和未来业务日，阻止未闭合账期生成空批次并抢占最新总览。
2. 日期解析切换为严格日历语义，`yyyy-MM-dd` 外观不变，但不存在的日期不再被自动归整。
3. 管理端日期控件限制最大已关闭业务日，并在请求前复验；服务端校验不依赖前端约束。

### 475.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 上海当天 | 服务返回“业务日期必须早于上海当前日期”，Mapper 零交互 |
| 未来日期 | 服务返回同一稳定错误，Mapper 零交互 |
| 非法日历日 | `2026-02-30` 由 STRICT 解析拒绝，Mapper 零交互 |
| 已关闭账期 | 既有历史日期和缺省上海前一日继续正常生成批次 |
| 日终定向测试 | `PaymentDayEndServiceImplTest` 共 `8` 项通过 |
| payment-core 后端全量测试 | `990` 项通过，`0` failures、`0` errors、`0` skipped |
| 管理端单测 | `TZ=America/Los_Angeles npm test` 共 `27` 项通过，`0` fail |
| 管理端生产构建 | Vite 构建通过；主包约 `546.03 kB`，保留既有体积提示 |
| 补丁检查 | `git diff --check` 通过 |

### 475.3 当前判断

1. 手工 `MANUAL / RERUN` 只能针对上海已经结束的日历日，不再允许用当前或未来空事实形成误导性完成批次。
2. 该边界不等于账期冻结或数据完整性证明；历史日期仍可能在上游数据迟到时变化，补跑前后需由财务运营核对事实快照。
3. 本地时钟与单测不证明生产 NTP、真实关账、资金一致或财务审批；release 四项真实治理阻断不变。

## 476. 2026-09-12 日终重复执行与并发裁决 V1.0 验证

### 476.1 本轮补强

1. 同一业务日通过数据库任务租约串行执行，租约忙请求在任何日终 Mapper 读取和批次写入前拒绝。
2. 无历史批次只允许 `MANUAL`；已有任意运行方式的历史批次后只允许 `RERUN`，多次合法重检继续追加快照而不覆盖历史。
3. 模式裁决、事实采集和批次写入使用同一显式事务，事务提交后才释放业务日租约。

### 476.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 首次模式 | 历史批次数为 `0` 时 `RERUN` 返回“首次执行请使用MANUAL”，不读取日终事实、不插入批次 |
| 重检模式 | 历史批次数大于 `0` 时 `MANUAL` 返回“请使用RERUN重检”，不读取日终事实、不插入批次 |
| 并发执行权 | 租约键为 `PAYMENT_DAY_END:{bizDate}`；获取失败返回“正在执行”，Mapper 零交互 |
| 提交顺序 | 单测验证 `insertBatch -> transaction commit -> releaseTaskLease` |
| 合法重检 | 既有 `RERUN` 用例在存在历史批次时继续生成新快照 |
| 日终定向测试 | `PaymentDayEndServiceImplTest` 共 `11` 项通过 |
| payment-core 后端全量测试 | `993` 项通过，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` 通过 |
| 代码提交 | `58b48cbc fix(payment-core): serialize day-end reruns` |
| 前端与数据结构 | 无前端、HTTP 字段、schema、索引或迁移变化 |

### 476.3 当前判断

1. 同一业务日的初次运行与重检语义现在由数据库共享执行权和持久化历史共同裁决，不再依赖操作员自觉选择或进程内互斥。
2. `RERUN` 的产品含义是形成新的事实快照，因此相同参数的串行重复提交仍会生成多个重检批次；本轮不宣称请求级 exactly-once 或幂等键能力。
3. 本地 mock 只证明调用顺序与失败边界，不证明目标 MySQL 的多实例锁竞争、续期、崩溃恢复、历史数据完整或真实财务重检结论；release 四项真实治理阻断不变。

## 477. 2026-09-12 自动日终调度 V1.0 验证

### 477.1 本轮补强

1. 新增默认关闭的自动日终调度器，默认 cron 为上海时区每日 `00:30`，业务日固定取上海前一已结束日。
2. 自动执行使用内部服务入口、`AUTO` 运行方式和固定系统身份，不开放给浏览器人工接口。
3. 自动执行复用业务日数据库租约；已有批次或租约忙时幂等跳过，写入异常则回滚、释放租约并继续抛出。

### 477.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 调度开关 | 默认配置为 `false`；启用时调用内部自动入口，关闭时不调用服务 |
| 调度时间 | 注解 cron 为 `${payment.day-end.auto.cron:0 30 0 * * *}`，时区固定 `Asia/Shanghai` |
| 自动身份 | 新批次写入 `runMode=AUTO`、`triggeredBy=payment-day-end-scheduler` |
| 业务日期 | 自动入口取 `Asia/Shanghai` 当前日期的前一日 |
| 幂等跳过 | 已有任意批次返回 `false` 且不读取后续事实；租约忙返回 `false` 且 Mapper 零交互 |
| 失败顺序 | 自动写入异常验证 `insertBatch -> transaction rollback -> releaseTaskLease`，异常未被吞掉 |
| 人工入口 | 既有回归继续拒绝 HTTP 请求中的 `runMode=AUTO` |
| 日终定向测试 | 调度、配置与服务共 `20` 项通过 |
| payment-core 后端全量测试 | `1002` 项通过，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` 通过 |
| 代码提交 | `03e089c9 feat(payment-core): schedule automatic day-end` |
| 前端与数据结构 | 无前端、HTTP 字段、schema、索引或迁移变化 |

### 477.3 当前判断

1. 仓库当前已具备自动日终代码入口，旧章节中的“自动日终未实现”是当时历史状态，不再代表当前实现；默认关闭仍意味着目标环境不会自动跑批。
2. 已有批次和租约竞争属于可预期幂等跳过，写入异常属于失败并向调度框架暴露，二者不能混为同一成功结果。
3. 本地单测不证明生产定时器已启用、主机时钟正确、目标 MySQL 多实例竞争安全、账期数据已到齐或财务关账完成；这些仍是部署与 release 前置证据。

## 478. 2026-09-12 自动日终统一任务留痕 V1.0 验证

### 478.1 本轮补强

1. 自动日终执行结果由布尔值升级为结构化内部 DTO，区分已执行、已有批次跳过和业务日租约忙，并携带业务日与新批次状态。
2. 日终调度器通过任务中心执行，统一写入 `PAYMENT_DAY_END` 任务日志；正常、批次告警、幂等跳过、租约竞争和技术失败分别形成可筛选的等级与建议动作。
3. 技术失败日志使用稳定错误码并重新抛出原异常；非法内部结果同样失败闭合，防止运营日志把协议错误解释为正常跳过。

### 478.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 结构化结果 | 已执行返回业务日和 `COMPLETED / WARNING`；未执行只返回 `EXISTING_BATCH / LEASE_BUSY`，非法组合拒绝 |
| 调度入口 | 启用时调用 `PaymentTaskCenterService.runAutoPaymentDayEnd()`；默认关闭时不调用 |
| 告警批次 | 写 `PAYMENT_DAY_END / AUTO / WARNING / P2`，推荐路由 `/payment-day-end` |
| 正常批次 | 写 `SUCCESS / P3`，处理数和成功数均为 `1` |
| 已有批次 | 写 `SUCCESS / P3`，处理数为 `0`，摘要明确幂等跳过 |
| 租约竞争 | 写 `WARNING / P2`，告警数为 `1`，摘要明确其他实例持有业务日租约 |
| 技术失败 | 写 `WARNING / P1`、`failCount=1` 和 `PAYMENT_DAY_END_AUTO_RUN_FAILED`，原异常继续抛出且敏感异常消息不落任务摘要 |
| 非法结果 | 未知跳过原因失败闭合并写稳定失败码 |
| 定向测试 | 日终服务、调度器和任务中心共 `65` 项通过 |
| payment-core 后端全量测试 | `1007` 项通过，`0` failures、`0` errors、`0` skipped |
| 补丁检查 | `git diff --check` 通过 |
| 代码提交 | `f71b0d77 feat(payment-core): record automatic day-end runs` |
| 前端与数据结构 | 任务中心现有自由任务编码筛选、通用列表和 CSV 可直接消费；无前端、HTTP、schema、索引或迁移变化 |

首次定向运行有 `1` 条新增测试失败，原因是断言匹配“已有日终批次”，实现稳定摘要为“已存在日终批次”；修正测试文案后定向与全量均通过，未修改业务逻辑。

### 478.3 当前判断

1. 自动日终现在具备任务中心可查询和可导出的统一留痕，但默认关闭时不会产生“未启用”运行日志；部署配置仍需由环境管理和发布记录证明。
2. 日终批次事务提交并释放业务日租约后才写任务日志。若日志写入失败，批次可能已经成功存在；值班恢复必须先查询批次事实，不能仅凭任务日志缺失决定补跑。
3. 本地 mock 不证明生产任务日志一定持久化、监控已消费 P1/P2、MySQL 多实例时序、账期数据完整或正式关账；release 四项真实治理阻断不变。

## 479. 2026-09-12 任务日志业务日关联 V1.0 验证

### 479.1 本轮补强

1. 任务运行日志实体、列表 DTO 和查询 DTO 新增可空 `bizDate`，自动日终所有结果路径均写入目标业务日，其他任务保持空值。
2. 列表、分页、CSV、HTTP 查询参数和管理端筛选同步支持业务日；日期输入在 Mapper 访问前按严格 `yyyy-MM-dd` 规则校验。
3. `t_payment_task_run_log` 新增 `biz_date DATE NULL` 和 `(task_code, biz_date)` 索引，并提供独立 MySQL 迁移脚本。

### 479.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 自动日终日志 | 完成、已有批次、租约忙均使用内部结果的 `bizDate`；技术失败关联上海前一业务日 |
| 查询契约 | 列表与导出接收可选 `bizDate`；前后空格规范化，非法日期在 Mapper 前拒绝 |
| Mapper 集成 | H2 MySQL 兼容模式完成日志写入、按任务编码与业务日计数/查询、日期返回映射 |
| 管理端 | 日期筛选同时作用于列表和 CSV，列表展示业务日，空值显示 `-` |
| 定向测试 | 控制器 `4` 项、Mapper 集成 `21` 项、任务中心 `47` 项，共 `72` 项通过 |
| payment-core 后端全量测试 | `1009` 项通过，`0` failures、`0` errors、`0` skipped |
| 管理端验证 | `27` 项单测通过；Vite 生产构建通过，保留既有单包大于 500 kB 提示 |
| XML 与补丁检查 | `PaymentTaskCenterMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 代码提交 | `2f6c176f feat(payment-core): link task runs to business dates` |

### 479.3 当前判断

1. 新字段为后续批次-日志勾稽和漏跑检测提供了结构化账期键，但本轮没有宣称相关巡检已经实现。
2. 历史日志的 `biz_date` 保持空值，不从摘要猜测回填；如需历史补录，必须基于可核验的批次或原始运行证据另行制定迁移。
3. 本地 schema、H2 集成测试和构建通过不证明目标 MySQL 已执行迁移、索引在线创建无锁等待或生产日志已与日终批次一致。

## 480. 2026-09-12 自动日终一致性巡检 V1.0 验证

### 480.1 本轮补强

1. 新增默认关闭的 `PAYMENT_DAY_END_PATROL` 调度，按上海时区在 `02:30-06:30` 每小时检查前一业务日，租约忙时留给后续窗口重试。
2. 巡检核对日终全部批次、自动批次与成功创建批次的自动任务日志数量，不自动补跑、不更新批次，也不推断资金或渠道对账结论。
3. 最终业务结论按业务日固定日志号去重；技术失败以独立日志和稳定码留痕后继续抛出，避免失败记录占用最终结论键。

### 480.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 无批次 | `PAYMENT_DAY_END_PATROL / WARNING / P1 / failCount=1`，摘要明确不自动补跑 |
| 自动记录不一致 | 自动批次数与成功创建日志数不同，记 `WARNING / P2 / warningCount=1` |
| 已覆盖 | 人工批次覆盖，或自动批次与成功创建日志数相等，记 `SUCCESS / P3` |
| 并发与去重 | 复用业务日租约；租约忙不落最终日志；最终日志号为 `PTL-DAY-END-PATROL-{yyyyMMdd}`，重复键视为其他实例已记录 |
| 失败闭合 | 非法内部结果或技术异常写 `PAYMENT_DAY_END_PATROL_FAILED`，不复制敏感异常消息，原异常继续抛出 |
| 调度配置 | 默认关闭；默认 cron `0 30 2-6 * * *`；时区固定 `Asia/Shanghai` |
| Mapper 集成 | H2 MySQL 兼容模式实际验证按业务日统计全部/自动批次及成功创建任务日志 |
| 定向测试 | 首轮联合 `102` 项，新增 Mapper SQL 独立 `1` 项，均通过 |
| payment-core 后端全量测试 | `1024` 项通过，`0` failures、`0` errors、`0` skipped |
| XML 与补丁检查 | 两份新增查询 Mapper 的 `xmllint` 与 `git diff --check` 通过 |
| 统一门禁复判 | 脚本退出码 `2` 仅来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、统一 `blockerCount=1`、内部 `releaseBlockerCount=4` |
| 代码提交 | `bac0c42f feat(payment-core): patrol day-end consistency` |
| 前端与数据结构 | 复用任务中心通用日志展示、筛选和路由；无前端、HTTP、schema、索引或迁移变化 |

### 480.3 当前判断

1. 本轮补齐的是批次与自动任务日志的数量巡检，不是资金核对、渠道账单对账、数据水位闸门或财务关账证明。
2. 技术失败按尝试留痕，最终业务结论按账期唯一；这样后续小时可恢复，但生产仍需验证多实例同时计算、重复键处理和监控对 P1/P2 的消费。
3. 默认关闭意味着当前代码不会自行产生巡检事实。目标环境启用、真实运行结果、告警送达与处置闭环必须由部署和运营证据另行确认。

## 481. 2026-09-12 自动日终批次结构化关联 V1.0 验证

### 481.1 本轮补强

1. 自动日终已执行结果携带新生成的 `batchNo`，任务中心将其写入任务日志可空字段 `businessRefNo`；跳过、租约忙、技术失败及非日终任务不填写。
2. 任务日志列表 DTO、Mapper 查询、CSV 和管理端列表新增业务关联号；两份 schema 与独立 MySQL 迁移同步新增字段和复合索引。
3. 日终巡检不再用数量相等代替一一对应，改为检测批次缺日志、日志缺批次和重复日志关联三类结构化异常。

### 481.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 自动日终写入 | 实际创建批次时日志 `businessRefNo=batchNo`；跳过和技术失败为空 |
| 内部结果校验 | 已执行结果缺失、空白或超过 `64` 字符的批次号均失败闭合；未执行结果携带批次号同样拒绝 |
| Mapper 一一关联 | H2 MySQL 兼容模式验证完全匹配时三项缺口为 `0`，悬空批次与悬空日志分别计数，重复关联计数为 `1` |
| 巡检等级 | 任一关联缺口记 `WARNING/P2`；无缺口时摘要明确自动批次与成功日志一一关联 |
| 页面与导出 | 管理端任务日志展示“业务关联号”，空值为 `-`；CSV 增加同名列和值 |
| 定向测试 | 日终服务、任务中心及两组 Mapper 集成共 `101` 项通过 |
| payment-core 后端全量测试 | `1029` 项通过，`0` failures、`0` errors、`0` skipped |
| 管理端验证 | `27` 项单测通过；Vite 生产构建通过，保留既有单包大于 `500 kB` 提示 |
| XML 与补丁检查 | `PaymentTaskCenterMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 统一门禁复判 | 脚本退出码 `2` 仅来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、统一 `blockerCount=1`、内部 `releaseBlockerCount=4` |
| 实现提交 | `eb78fbc6 feat(payment-core): link day-end runs to batches` |

定向测试调试期间依次修正了测试方法的 JDBC 受检异常声明、H2 持久内存库重复建表，以及 JDBC 直接更新后的 MyBatis 一级缓存；这些均为测试装置问题。修正后新增重复关联与批次号边界覆盖，最终定向和全量回归均通过。

### 481.3 当前判断

1. `business_ref_no` 使新产生的自动日终批次与任务日志可以结构化勾稽，但历史日志保持空值，不从自由摘要或同日批次猜测关联。
2. 巡检仍默认关闭，且结论只覆盖本地批次表与任务日志表；不能解释为渠道账单、账务分录、资金或正式财务关账一致。
3. 目标 MySQL 的在线 DDL、存量空值分布、多实例写入时序、P2 告警送达与处置仍需真实环境验证；本地结果不改变 release 治理阻断。

## 482. 2026-09-12 自动日终关联运行方式隔离 V1.0 验证

### 482.1 本轮补强

1. 批次缺日志、日志缺批次和重复关联三类 Mapper 查询均把候选任务日志限定为 `PAYMENT_DAY_END / AUTO`。
2. 重复关联的较早日志同样限定 `AUTO`，人工记录不能作为已存在的自动成功日志参与计数。
3. 不改变成功创建条件、批次类型、业务日和批次号匹配规则，也不新增表结构、接口或页面字段。

### 482.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 人工日志隔离 | 匹配同一自动批次号的 `MANUAL` 成功日志不能消除自动批次缺日志计数 |
| 自动日志集合 | `MANUAL` 日志不进入无批次关联的自动成功日志计数，也不进入重复关联计数 |
| 原有关联语义 | 日志恢复 `AUTO` 后，完全匹配、双向悬空和重复关联测试继续通过 |
| 定向测试 | `PaymentTaskCenterMapperIntegrationTest`、巡检 Mapper 与任务中心服务共 `82` 项通过 |
| payment-core 后端全量测试 | `1029` 项通过，`0` failures、`0` errors、`0` skipped |
| XML 与补丁检查 | `PaymentTaskCenterMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 统一门禁复判 | 脚本退出码 `2` 仅来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、统一 `blockerCount=1`、内部 `releaseBlockerCount=4` |
| 代码提交 | `4e3b9514 fix(payment-core): scope day-end links to automatic runs` |
| 前端与数据结构 | 无前端、HTTP、schema、索引或迁移变化，未重复运行管理端测试与构建 |

### 482.3 当前判断

1. 当前 SQL 与接口契约的 `PAYMENT_DAY_END / AUTO` 集合已经一致，人工日志不能替代自动运行证据。
2. 数据库没有对历史 `business_ref_no` 的来源真实性作强制约束，目标环境仍需审计存量关联和写入权限；本地测试不证明生产数据未受污染。
3. 本轮不改变数据水位、渠道账单、账务或资金边界，也不构成自动巡检已启用、告警闭环或 release 放行证明。

## 483. 2026-09-12 自动日终关联证据完整性 V1.0 验证

### 483.1 本轮补强

1. 自动日终有效关联日志必须由固定系统身份触发、恰好处理一个批次且没有失败。
2. 正常批次只接受 `SUCCESS / successCount=1 / warningCount=0`，告警批次只接受 `WARNING / successCount=0 / warningCount=1`；其他状态计数组合不构成成功创建证据。
3. 批次缺日志、日志缺批次和重复关联三类 SQL 对当前与较早日志使用同一完整谓词。

### 483.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 正常完成证据 | `payment-day-end-scheduler / processed=1 / SUCCESS / success=1 / warning=0 / fail=0` 可完成关联 |
| 告警完成证据 | `payment-day-end-scheduler / processed=1 / WARNING / success=0 / warning=1 / fail=0` 同样可完成关联 |
| 畸形日志隔离 | 触发人改为普通操作员且处理数改为 `2` 后，自动批次重新计为缺少合法成功日志，畸形日志不进入另外两类统计 |
| 原有检测 | 自动运行方式隔离、双向悬空和重复关联场景继续通过 |
| 定向测试 | Mapper、巡检与任务中心联合 `82` 项通过 |
| payment-core 后端全量测试 | `1029` 项通过，`0` failures、`0` errors、`0` skipped |
| XML 与补丁检查 | `PaymentTaskCenterMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 统一门禁复判 | 脚本退出码 `2` 仅来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、统一 `blockerCount=1`、内部 `releaseBlockerCount=4` |
| 代码提交 | `d3e2ee0f fix(payment-core): validate day-end link evidence` |
| 前端与数据结构 | 无前端、HTTP、schema、索引或迁移变化，未重复运行管理端测试与构建 |

### 483.3 当前判断

1. 新谓词与任务中心当前自动日终写入形态一致，避免宽松计数把畸形日志当作批次创建证据。
2. 固定 `triggered_by` 是本地系统身份声明而非可信认证；生产仍需最小写库权限、日志审计和数据抽样，不能仅凭字符串证明真实调度来源。
3. 本轮仍未补齐上游数据水位、渠道账单、账务或资金核验，也不构成生产巡检启用、告警处置或 release 放行证明。

## 484. 2026-09-12 支付日终待收口支付事实 V1.0 验证

### 484.1 本轮补强

1. 日终批次新增 `pendingPaymentCount / pendingPaymentAmount`，明确统计 `PREPAY_CREATED / WAIT_CALLBACK / RISK_REVIEW` 三种本地非终态。
2. 批次状态、系统摘要、总览告警和对账准入统一纳入待收口支付；新增 `PENDING_PAYMENT` 告警与支付单精确下钻。
3. 支付列表、计数和导出支持查询态 `DAY_END_PENDING`；管理端增加对应筛选选项与批次事实展示。
4. 两份 schema 增加非空零默认字段；独立迁移为存量批次保留可空字段，历史空值按事实不完整处理，要求 `RERUN`。

### 484.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 状态集合 | H2 MySQL 兼容测试验证只统计 `PREPAY_CREATED / WAIT_CALLBACK / RISK_REVIEW`，排除 `RISK_BLOCKED / RISK_REJECTED` |
| 列表下钻 | `DAY_END_PENDING` 在列表、计数和 CSV 导出返回同一三状态集合 |
| 批次与准入 | 仅有待收口支付时批次为 `WARNING/warn`，总览禁止进入对账并返回 P1 告警 |
| 历史边界 | 新字段为 `NULL` 的历史批次显示 `UNKNOWN`，总览为“日终事实不完整” |
| 定向后端测试 | `PaymentDayEndServiceImplTest` 与两组 Mapper 集成共 `39` 项通过 |
| payment-core 后端全量测试 | `1033` 项通过，`0` failures、`0` errors、`0` skipped |
| 管理端验证 | `28` 项单测通过；Vite 生产构建通过，保留既有单包大于 `500 kB` 提示 |
| XML 与补丁检查 | `PaymentDayEndMapper.xml`、`PaymentMapper.xml` 与 `git diff --check` 通过 |
| 统一门禁复判 | `canPromoteTestToMaster=true`、`canCreateRelease=false`、统一 `blockerCount=1`、内部 `releaseBlockerCount=4` |
| 实现提交 | `5676b4ef feat(payment-core): snapshot pending day-end payments` |

### 484.3 当前判断

1. 本轮弥补的是 payment-core 可直接证明的本地状态缺口，不是完整跨系统数据水位协议。
2. `RISK_REVIEW` 等待外部人工复核，属于未完成；`RISK_BLOCKED / RISK_REJECTED` 已有终止结论，不应误报为资金待确认。
3. 本地测试不证明目标 MySQL 迁移已完成、渠道回调全部到齐、资金或渠道账单一致；统一门禁仍需按现有治理证据重新复判。

## 485. 2026-09-12 支付成功时间与日终账期归属 V1.0 验证

### 485.1 本轮补强

1. 支付单新增 `successAt`，渠道回调与主动查单成功收口通过同一原子 SQL 首次写入，支付流水 `paidAt` 和排序改用真实字段。
2. 日终所有成功类事实改按 `DATE(success_at)` 归属；支付总量、待收口支付和成功时间缺失识别仍按支付单创建日，避免缺失记录被静默丢弃。
3. 日终批次新增成功时间缺失数，纳入批次状态、摘要、告警、CSV、总览及对账准入；支付列表新增 `SUCCESS_TIME_MISSING` 精确下钻。
4. 两份 schema、固定样例和独立迁移同步更新；迁移仅使用明确成功通知或支付成功事件证据，无法回填的历史成功单保持空值，历史批次完整性字段保持 `NULL`。

### 485.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 原子成功迁移 | H2 MySQL 兼容测试验证合格渠道结果写入非空 `success_at`，无提交事实的支付单仍不能成功收口 |
| 跨日账期 | 构造前一日创建、后一日成功的支付单，成功单量与金额只计入成功日 |
| 缺失识别 | 创建日存在 `SUCCESS + success_at IS NULL` 时，日终完整性计数为 `1`，列表、计数和 CSV 使用同一虚拟筛选集合 |
| 流水语义 | 支付流水列表与退款聚合查询返回 `success_at` 作为 `paidAt`，并按该字段排序 |
| 批次与准入 | 成功时间缺失使批次为 `WARNING/warn`，生成 `PAYMENT_SUCCESS_TIME_MISSING` 告警并禁止进入对账；历史空字段显示 `UNKNOWN` |
| 定向后端测试 | 五组服务与 Mapper 测试共 `46` 项通过 |
| payment-core 后端全量测试 | `1036` 项通过，`0` failures、`0` errors、`0` skipped |
| 管理端验证 | `29` 项单测通过；Vite 生产构建通过，保留既有单包大于 `500 kB` 提示 |
| XML 与补丁检查 | `PaymentDayEndMapper.xml`、`PaymentMapper.xml`、`PaymentRecordMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 统一门禁复判 | 脚本退出码 `2` 仅来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、统一 `blockerCount=1`、内部 `releaseBlockerCount=4` |
| 实现提交 | `db4c7123 feat(payment-core): record payment success time` |

### 485.3 当前判断

1. `success_at` 是 payment-core 首次可信落成功状态的本地时间，不等于渠道实际扣款、清算或入账时间；外部账单仍需后续对账确认。
2. 历史回填遵循“有明确证据才填”，无法证明的记录通过告警阻断而非用 `created_at` 伪造；生产迁移前仍需预查证据冲突、评估锁等待并准备备份与回滚。
3. 本轮验证基于本地 H2、固定样例和管理端构建，不证明目标 MySQL 已迁移、生产多实例时序、渠道资金或正式关账一致，也不解除 release 治理阻断。

## 486. 2026-09-12 支付监控成功事实一致性 V1.0 验证

### 486.1 本轮补强

1. 最近七日趋势将创建量与成功发生量拆分到 `created_at / success_at` 两条时间轴，再按日期并集聚合，跨日成功不再归入创建日。
2. 监控摘要和告警列表新增全局成功时间缺失类型，等级为 P1，动作路由复用支付列表 `SUCCESS_TIME_MISSING`。
3. 服务组装总览时以实际告警数组长度设置 `summary.alertCount`，避免独立查询间事实变化造成响应内计数不一致。
4. 管理端同步更新趋势列名、P1 摘要与缺失事实处理建议，不改变既有路由和 API 结构。

### 486.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 双时间轴趋势 | H2 MySQL 兼容测试验证窗口外创建、窗口内成功只增加成功日；跨日创建与成功分别进入对应日期 |
| 七日窗口 | 未来事实和两条时间轴均在窗口外的事实不进入趋势；日期并集最多返回七个自然日 |
| 全局缺失告警 | `SUCCESS + success_at IS NULL` 生成第二顺位 `PAYMENT_SUCCESS_TIME_MISSING / P1`，影响数与精确下钻正确 |
| 响应计数一致 | 服务测试将 Mapper 摘要置为错误值 `99`，最终响应按两条实际告警修正为 `2` |
| 定向后端测试 | `PaymentMonitorMapperIntegrationTest` 与 `PaymentMonitorServiceImplTest` 共 `8` 项通过 |
| payment-core 后端全量测试 | `1037` 项通过，`0` failures、`0` errors、`0` skipped |
| 管理端验证 | `29` 项单测通过；Vite 生产构建通过，保留既有单包大于 `500 kB` 提示 |
| XML 与补丁检查 | `PaymentMonitorMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 数据结构 | 两份 schema 和迁移 `2026-09-12-add-payment-created-time-index.sql` 新增 `idx_payment_created_at(created_at)`；尚未在目标 MySQL 执行或验证执行计划 |
| 实现提交 | `16866db3 fix(payment-core): align monitor success facts` |

### 486.3 当前判断

1. 趋势现在表达两种发生事实，单日成功量可以大于当日创建量或在创建量为零时非零；这不是成功率分母，不能据此计算当日转化率。
2. 全局告警弥补了历史缺失不一定出现在最近日终批次的问题，但不会自动修复数据；仍需核对明确成功通知或事件后受控处理。
3. 本轮新增创建时间索引迁移，但本地测试不证明目标 MySQL 已执行迁移或采用预期执行计划，也不证明会话时区、生产告警处置或渠道资金一致，不解除 release 治理阻断。

## 487. 2026-09-12 退款成功时间完整性 V1.0 验证

### 487.1 本轮补强

1. 日终批次新增退款成功时间缺失快照，按申请日识别 `SUCCESS + success_at IS NULL`，避免无法归属成功账期的退款静默漏出。
2. 总览、批次准入、告警、CSV 和页面统一纳入该事实；历史批次字段保持 `NULL` 并要求 `RERUN`，不把未知值视为零。
3. 退款列表、计数和导出新增精确虚拟筛选，支付监控同步新增全局 P1 告警并保持响应内告警计数一致。
4. 两份 schema 和独立迁移新增退款状态与申请/成功时间索引，不回填或推断历史退款成功时间。

### 487.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 跨日退款事实 | 申请日与成功日不同时，成功单量和金额只进入 `success_at` 所在日 |
| 缺失识别 | 缺少成功时间的成功退款按申请日进入完整性计数，不进入其他业务日 |
| 列表下钻 | 列表、计数和 CSV 均只返回 `SUCCESS + success_at IS NULL` |
| 日终保护 | 缺失数写入新批次并形成 `WARNING`、P1 告警和禁止对账结论；历史空字段形成事实不完整 |
| 监控保护 | 新增 `REFUND_SUCCESS_TIME_MISSING / P1`，下钻 `/refunds?refundStatus=SUCCESS_TIME_MISSING`，摘要计数与告警数组一致 |
| 定向后端测试 | 五组服务与 Mapper 测试共 `36` 项通过；首次运行仅旧提示文案断言未同步，修正后全量重跑通过 |
| payment-core 后端全量测试 | `1041` 项通过，`0` failures、`0` errors、`0` skipped |
| 管理端验证 | `29` 项单测通过；Vite 生产构建通过，保留既有 `549.52 kB` 单包提示 |
| XML 与补丁检查 | `PaymentDayEndMapper.xml`、`PaymentMonitorMapper.xml`、`RefundMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 数据结构 | 新增两个退款时间索引与 `missing_refund_success_time_count` 迁移；尚未在目标 MySQL 执行 |
| 实现提交 | `a65f0464 fix(payment-core): guard refund success time` |

### 487.3 当前判断

1. 退款 `success_at` 是 payment-core 从可信退款回调或主动查单收口成功时记录的本地时间，不等于客户实际到账或账务冲销时间。
2. 缺失记录需要核对可信回调、查单结果和操作日志后受控处理；不得直接用 `applied_at` 回填。
3. 本地 H2 与固定样例不证明生产迁移、MySQL 执行计划、渠道到账、资金一致或正式关账，也不解除 release 治理阻断。

## 488. 2026-09-12 退款终态事实一致性 V1.0 验证

### 488.1 本轮补强

1. 退款回调锁定快照与主动查单快照同时读取退款单成功时间、最新退款尝试状态和尝试完成时间。
2. 重复终态回调及终态退款主动查单增加失败闭合校验，退款单终态与最新尝试终态不一致、完成时间缺失或成功时间不完整时不再按幂等成功返回。
3. 失败退款若残留成功时间同样判为不一致；服务不在重复结果入口自动修复历史事实。
4. 退款成功更新使用 `COALESCE` 保留首次 `success_at`，正式 Mapper 用例验证退款单与尝试成功事实均可读取且重复状态迁移不生效。

### 488.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 正常终态幂等 | 退款单与最新尝试均为相同终态、尝试完成时间存在且成功退款具有成功时间时，重复结果正常返回 |
| 不一致失败闭合 | 最新尝试仍为 `ACCEPTED`、尝试完成时间缺失或退款成功时间缺失时抛出稳定内部一致性错误，不查询渠道、不返回伪正常结果 |
| 原子收口事实 | 正式 `RefundMapper.xml` 将退款单和最新尝试收口为 `SUCCESS` 后，可读取非空 `successAt / attemptCompletedAt`；反向重复更新返回零行 |
| 定向后端测试 | `RefundServiceImplTest` 与 `RefundSubmitOutboxMapperIntegrationTest` 共 `59` 项通过；首次运行暴露 H2 时间函数兼容与处理中同态边界，修正后全量重跑通过 |
| payment-core 后端全量测试 | `1045` 项通过，`0` failures、`0` errors、`0` skipped |
| 前端与数据结构 | 无 HTTP、页面、schema、索引或迁移变化，因此未重复运行管理端测试与构建 |
| XML 与补丁检查 | `RefundMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |

### 488.3 当前判断

1. 该保护防止可信结果再次触达时静默接受结构化事实断裂，但不是全库巡检；从未再次回调或查单的历史异常仍需后续独立识别。
2. `success_at` 与尝试 `completed_at` 均表示 payment-core 本地收口事实，不证明客户到账、渠道账单、账务冲销或资金一致。
3. 本地 H2 和模拟服务测试不证明目标 MySQL 多实例并发、生产历史数据质量或正式关账，也不解除 release 治理阻断。

## 489. 2026-09-12 退款终态事实主动巡检 V1.0 验证

### 489.1 本轮补强

1. 日终新增退款终态与最新尝试一致性快照，覆盖无尝试、状态错配、完成时间缺失和失败退款残留成功时间。
2. 新事实纳入批次状态、摘要、总览、批次准入、CSV 和 P1 告警；历史批次保持未采集并要求重跑。
3. 退款列表、计数与导出使用同一虚拟筛选集合，支付监控新增同口径全局告警并保持摘要计数与告警数组一致。
4. 管理端新增日终事实展示、最近批次列、退款筛选项和监控处置建议，不新增修复或强制改状态入口。

### 489.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 日终集合 | 申请日内正常终态不计数；状态错配、无最新尝试、完成时间缺失或失败残留成功时间进入不一致数 |
| 告警去重 | `SUCCESS + success_at IS NULL` 且最新尝试完整时只进入退款成功时间缺失，不重复进入终态事实不一致 |
| 列表下钻 | `TERMINAL_FACT_MISMATCH` 的列表、计数与 CSV 返回完全相同的异常退款单集合 |
| 日终准入 | 不一致数写入新批次并形成 `WARNING`、P1 告警和禁止对账；历史空字段形成事实不完整 |
| 监控保护 | 新增 `REFUND_TERMINAL_FACT_MISMATCH / P1`，精确下钻退款列表，摘要告警类型数与实际数组一致 |
| 定向后端测试 | 日终 Mapper、退款列表 Mapper、监控 Mapper、日终服务和监控服务共 `39` 项通过；首次联测暴露既有测试未声明新快照字段，修正后全量重跑通过 |
| payment-core 后端全量测试 | `1048` 项通过，`0` failures、`0` errors、`0` skipped |
| 管理端验证 | `29` 项单测通过；Vite 生产构建通过，保留既有 `550.55 kB` 单包提示 |
| XML 与补丁检查 | `PaymentDayEndMapper.xml`、`PaymentMonitorMapper.xml`、`RefundMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 数据结构 | 两份 schema 与新增迁移同步；尚未在目标 MySQL 执行或验证执行计划 |

### 489.3 当前判断

1. 主动扫描补齐了“必须再次触达才发现”的观察缺口，但告警仅定位异常，不自动修改历史退款单或尝试。
2. 日终按退款申请日保存完整性快照，不能将其解释为退款成功账期；退款成功金额仍只按 `success_at` 归属。
3. 本地 H2、固定样例与页面构建不证明目标 MySQL 迁移、生产历史数据、渠道到账、账务冲销或资金一致，也不解除 release 治理阻断。

## 490. 2026-09-12 支付成功终态事实一致性 V1.0 验证

### 490.1 本轮补强

1. 支付尝试新增 `completed_at`，支付成功回调与主动查单只终结最新一次尝试，并要求更新行数精确为一。
2. 支付详情稳定读取支付单成功时间、最新尝试状态和完成时间，供重复成功结果进入幂等分支前校验。
3. 支付单成功时间缺失、最新尝试缺失或非“成功”、尝试完成时间缺失时显式失败，不把断裂事实包装为正常成功。
4. 历史支付尝试完成时间保持空值，不从通知、事件或支付单时间推断回填；后续需要独立巡检和受控处置。

### 490.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 原子成功收口 | 支付单成功迁移后，最新尝试状态更新为“成功”且 `completed_at` 非空；无法精确终结一条尝试时显式失败，不继续订单、账单和成功事件处理 |
| 历史尝试保护 | 同一支付单存在两次尝试时只更新 `created_at DESC, id DESC` 的最新记录，较早尝试状态与完成时间保持不变 |
| 重复结果保护 | 完整成功事实允许回调幂等返回；尝试状态错配或完成时间缺失时抛出 `payment success facts are inconsistent` |
| 终态主动查单 | 本地已成功且事实完整时直接返回，不再重复调用渠道；事实不完整时在限流、渠道请求和状态副作用前失败闭合 |
| 联合定向测试 | `PaymentServiceImplTest`、`PaymentMapperStateTransitionIntegrationTest`、`PaymentReturnCodeSnapshotMapperIntegrationTest` 共 `98` 项通过 |
| payment-core 后端全量测试 | `1054` 项通过，`0` failures、`0` errors、`0` skipped |
| 首次全量结果 | 首次为 `1050/1051`，唯一失败是旧自建测试表未声明新增完成时间；同步测试契约后，第二次发现同一测试表也未声明既有 `success_at`，一并修正后联合定向与全量重跑通过；失败结果未作为能力通过 |
| 前端与接口 | DTO 响应新增两个事实时间字段，但无管理端文件变化，因此未重复运行管理端测试和构建 |
| 数据结构 | 两份 schema 和独立迁移新增可空 `t_payment_attempt.completed_at`；尚未在目标 MySQL 执行，也未回填历史值 |
| XML 与补丁检查 | `PaymentMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |

### 490.3 当前判断

1. `success_at` 和 `completed_at` 均表示 payment-core 本地成功收口事实，不等于渠道实际扣款、清分或账务入账时间。
2. 当前仅保护成功支付；未提交人工关闭和过期关闭允许不存在支付尝试，不能未经状态模型设计把 `CLOSED` 强行纳入同一一致性谓词。
3. 本地 H2 与模拟服务测试不证明目标 MySQL 多实例原子性、生产历史数据质量、渠道资金一致或正式关账，也不解除 release 四项治理阻断。

## 491. 2026-09-12 支付成功事实主动巡检 V1.0 验证

### 491.1 本轮补强

1. 日终新增支付成功与最新支付尝试一致性快照，覆盖无尝试、最新尝试状态错配和尝试完成时间缺失。
2. 新事实纳入批次状态、摘要、总览、批次准入、CSV 和 P1 告警；历史批次保持未采集并要求重跑。
3. 支付列表、计数与导出使用同一虚拟筛选集合，支付监控新增同口径全局告警并保持摘要计数与告警数组一致。
4. 管理端新增日终事实展示、最近批次列、支付筛选项和监控处置建议，不新增自动修复或强制改状态入口。

### 491.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 日终集合 | 创建日内正常成功与完整最新尝试不计数；无尝试、最新状态错配和完成时间缺失进入不一致数；`CLOSED` 不进入 |
| 独立风险集合 | `SUCCESS + success_at IS NULL` 且最新成功尝试完整时只进入成功时间缺失；若尝试也断裂则允许同时命中两个风险集合 |
| 列表下钻 | `SUCCESS_FACT_MISMATCH` 的列表、计数与 CSV 返回完全相同的三类异常支付单集合 |
| 日终准入 | 不一致数写入新批次并形成 `WARNING`、P1 告警和禁止对账；历史空字段形成事实不完整 |
| 监控保护 | 新增 `PAYMENT_SUCCESS_FACT_MISMATCH / P1`，精确下钻支付列表，摘要告警类型数与实际数组一致 |
| 定向后端测试 | 日终 Mapper、支付列表 Mapper、监控 Mapper 和日终服务共 `52` 项通过 |
| payment-core 后端全量测试 | `1058` 项通过，`0` failures、`0` errors、`0` skipped |
| 管理端验证 | `30` 项单测通过；Vite 生产构建通过，保留既有 `551.58 kB` 单包提示 |
| XML 与补丁检查 | `PaymentMapper.xml`、`PaymentDayEndMapper.xml`、`PaymentMonitorMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 数据结构 | 两份 schema 与新增迁移同步；尚未在目标 MySQL 执行或验证执行计划 |

### 491.3 当前判断

1. 主动扫描补齐了“必须再次触达才发现”的观察缺口，但告警只定位异常，不自动修改历史支付单或支付尝试。
2. 日终按支付成功日保存成功事实一致性快照，与支付成功金额共同按 `success_at` 归属；成功时间缺失因无法使用成功日，仍按创建日进入独立完整性集合。
3. 本地 H2、固定样例与页面构建不证明目标 MySQL 迁移、生产历史数据、渠道扣款、账务入账或资金一致，也不解除 release 四项治理阻断。

## 492. 2026-09-12 支付日终账期查询可索引化 V1.0 验证

### 492.1 本轮补强

1. 将日终 Mapper 中支付创建、支付成功、退款申请和退款成功的 `21` 个 `DATE(column)=bizDate` 条件统一改为左闭右开的日期时间范围。
2. 比较列不再包裹函数；日期常量显式转换为 `DATETIME`，次日上界通过 `TIMESTAMPADD` 计算，保持数据库会话时区下的原账期语义。
3. 新增资源契约测试在本轮锁定全部 `21` 组上下界，并将支付创建、退款申请和退款成功样例放在业务日及次日零点验证边界归属；后续退款终态历史兜底新增 `1` 组申请日范围，失败退款单量和金额新增 `2` 组终态日范围，当前契约为 `24` 组。

### 492.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 支付创建日 | 业务日 `00:00:00` 纳入当日，次日 `00:00:00` 排除；支付总量、待收口、成功时间缺失和成功事实不一致沿用同一边界 |
| 支付成功日 | 成功单量/金额、渠道成功、内部成功、成功差异和两类异常均使用相同半开区间 |
| 退款日期 | 退款成功按成功时间半开区间归属；成功时间缺失和待收口按申请时间归属；终态不一致按终态时间归属，历史终态时间缺失按申请日兜底 |
| SQL 形状 | 当前资源契约确认 `24` 个下界与 `24` 个上界存在，且不存在 `DATE(indexed_column)=bizDate`；其中本轮原始改造 `21` 组，退款终态历史兜底新增 `1` 组，失败退款单量和金额新增 `2` 组 |
| 日终联合定向测试 | `44` 项通过，包含 Mapper、服务、控制器、调度和配置用例 |
| payment-core 后端全量测试 | `1059` 项通过，`0` failures、`0` errors、`0` skipped |
| XML 与补丁检查 | `PaymentDayEndMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 首次兼容失败 | 首次使用 `DATE_ADD` 时 H2 不识别函数；改为裸 `TIMESTAMPADD` 后 H2 无法推断参数类型；最终显式转换绑定常量后定向与全量重跑通过，两次失败均未作为能力通过 |
| 前端与数据结构 | 无页面、DTO、schema、索引或迁移变化，因此未重复运行管理端测试和生产构建 |

### 492.3 当前判断

1. 本轮消除索引列函数包装并保留账期边界，但没有目标 MySQL `EXPLAIN ANALYZE`，不能宣称优化器实际选中了预期索引或生产耗时已经改善。
2. 半开区间仍遵循数据库会话时区；目标环境必须校验时区配置、统计信息、数据倾斜与真实执行计划。
3. 查询性能和本地账期归属通过不证明渠道账单、账务分录、资金一致或正式关账，也不解除 release 四项治理阻断。

## 493. 2026-09-12 支付成功事实巡检账期归属 V1.1 验证

### 493.1 本轮补强

1. 将日终支付成功事实不一致从支付创建日改为支付成功日归属，避免跨日成功后的尝试断裂逃出日终快照。
2. 保持成功时间缺失集合按创建日采集；没有 `success_at` 的记录不猜测成功账期，也不并入本次口径。
3. 资源契约明确锁定该查询使用 `payment_order.success_at` 半开区间，防止后续仅满足范围数量却回退到错误时间轴。

### 493.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 跨日成功异常 | 前日创建、当日 `00:00:00` 成功且缺少最新尝试的支付计入成功日，不计入创建日 |
| 相邻账期边界 | 次日 `00:00:00` 成功的完整支付不进入前一业务日 |
| 独立缺失集合 | `SUCCESS + success_at IS NULL` 继续按创建日进入成功时间缺失，不由成功事实巡检猜测日期 |
| 日终相关测试 | `39` 项通过，`0` failures、`0` errors、`0` skipped |
| payment-core 后端全量 | `1059` 项通过，`0` failures、`0` errors、`0` skipped |
| XML 与补丁检查 | `PaymentDayEndMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 首次编译失败 | 资源契约最初误用 Java 文本块，项目 Java 8 源级别拒绝编译；改为字符串拼接后定向与全量重跑通过，失败未作为通过证据 |
| 数据与接口 | 无 schema、索引、迁移、DTO、HTTP 或前端变化 |

### 493.3 当前判断

1. 新口径关闭了跨日成功异常逃出日终快照的窗口，并与其他支付成功事实使用同一时间轴。
2. 本地 H2 不证明目标 MySQL 执行计划、生产历史分布、渠道扣款、账务入账或资金一致，release 四项治理阻断保持不变。

## 494. 2026-09-12 退款终态时间与账期归属 V1.0 验证

### 494.1 本轮补强

1. 新增退款单本地终态时间，成功和失败收口均在原状态条件更新中首次写入，失败重试清空后由下一次可信终态重新写入。
2. 终态回调和主动查单幂等校验新增终态时间完整性要求；全局监控、退款列表与日终巡检将缺失值视为结构化事实不一致。
3. 日终优先按终态发生日采集不一致；历史缺失终态时间仍按申请日兜底，便于受控重跑暴露但不猜测实际终态日。
4. 退款详情新增 `terminalAt`，管理端展示“终态时间”并对终态缺失给出核查建议。

### 494.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 状态迁移 | `PROCESSING -> FAIL` 写入终态时间；`FAIL -> PROCESSING` 清空；再次 `PROCESSING -> SUCCESS` 同时写入成功与终态时间 |
| 终态幂等 | 成功/失败退款缺少终态时间时显式失败，且不调用渠道查询 |
| 跨日巡检 | 前日申请、当日零点进入失败终态且尝试缺失的退款计入终态日，不回落到申请日 |
| 集合一致性 | 日终、全局监控和退款虚拟筛选均把 `terminal_at IS NULL` 纳入终态事实不一致；仅缺成功时间且终态/尝试完整时不重复命中 |
| 历史迁移 | 只接受 `success_at` 或最新匹配尝试 `completed_at`，静态契约禁止以 `NOW()` 或 `applied_at` 伪造终态 |
| 核心联合定向 | `69` 项通过，`0` failures、`0` errors、`0` skipped |
| payment-core 后端全量 | `1062` 项通过，`0` failures、`0` errors、`0` skipped |
| 管理端 | `30` 项单测通过，Vite 生产构建通过，保留既有约 `551.92 kB` 单包提示 |
| XML 与补丁检查 | `RefundMapper.xml`、`PaymentMonitorMapper.xml`、`PaymentDayEndMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |
| 首次定向失败 | “仅缺成功时间”夹具未设置新增终态时间，导致同时命中终态不一致，告警数多 `1`；补齐可信终态时间后联合定向重跑通过 |
| 首次全量失败 | 退款 outbox 与并发申请的两组 H2 夹具缺少 `terminal_at`，共 `7` 个 SQL 错误；只补测试表结构后受影响用例与全量重跑通过 |

### 494.3 当前判断

1. 新写入退款具备统一成功/失败终态时间，关闭正常应用路径下跨日终态不一致逃出日终的窗口。
2. 历史无可信时间记录仍保持 `NULL`，只有重跑申请日时进入日终兜底；全量历史跨日归账需目标环境审计后受控处置。
3. 本地验证不证明目标 MySQL 在线 DDL、真实渠道到账、账务冲销、资金一致或正式关账，release 四项治理阻断保持不变。

## 495. 2026-09-12 日终退款失败事实快照 V1.0 验证

### 495.1 本轮补强

1. 新增失败退款单量和金额日终快照，统一按 `terminal_at` 所在业务日归属，不再让结构完整的失败退款逃出不可变批次事实。
2. 失败退款非零时批次进入 `WARNING`，总览与批次对账准入均为阻断；日终新增 `REFUND_FAIL / P1` 告警并下钻既有 `FAIL` 退款列表。
3. 历史批次两字段保持 `NULL`，总览、最近批次与 CSV 显示未采集并要求 `RERUN`，不以当前状态倒推历史快照。
4. 管理端新增退款失败单量、金额卡片和批次列，顶部风险摘要同步展示失败数。

### 495.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 账期边界 | 业务日终态失败纳入当日，次日 `00:00:00` 排除并进入次日；单量与金额口径一致 |
| 批次准入 | 仅有 `2` 笔、`¥88.00` 退款失败时批次为 `WARNING`，总览禁止进入对账 |
| 历史兼容 | schema 新批次字段非空默认零；增量迁移字段可空且无历史 UPDATE，旧批次必须重跑 |
| 日终定向 | `32` 项通过，`0` failures、`0` errors、`0` skipped |
| payment-core 后端全量 | `1064` 项通过，`0` failures、`0` errors、`0` skipped |
| 管理端 | `30` 项单测通过，Vite 生产构建通过，保留既有约 `552.64 kB` 单包提示 |
| XML 与补丁检查 | `PaymentDayEndMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |

### 495.3 当前判断

1. 失败退款与全局监控的 P1 口径已进入日终不可变快照，对账准入不再把完整失败退款当作已收口。
2. 缺失 `terminal_at` 的历史失败继续由终态事实不一致集合阻断，不使用申请时间猜测失败发生日，也不与可归属失败数重复计量。
3. 本地验证不证明目标 MySQL 在线 DDL、真实渠道失败原因、补退结果、账务冲销、资金一致或正式关账，release 四项治理阻断保持不变。

## 496. 2026-09-12 失败退款批量重试顺序 V1.0 验证

### 496.1 本轮补强

1. 任务中心人工批量执行和定时调度共用的失败退款候选由申请时间排序改为终态时间排序，优先处理失败持续时间更长的退款。
2. 候选必须满足退款单 `FAIL + terminal_at 非空 + success_at 为空`，且最新退款尝试同为 `FAIL` 并具备完成时间；任一终态事实异常记录保持失败状态并继续由 P1 巡检暴露，不通过批量重试清空异常现场。
3. 相同终态时间继续以自增主键稳定排序，批次大小仍限定为 `1..1000`；退款列表单笔人工重试不复用该候选查询，行为保持不变。

### 496.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 终态时间优先 | 申请更晚但失败更早的退款排在申请更早但失败更晚的退款之前 |
| 稳定顺序 | 相同 `terminal_at` 的退款按 `id ASC` 返回 |
| 历史异常隔离 | 终态时间缺失、最新尝试缺失或错配、完成时间缺失和失败残留成功时间均不进入批量候选 |
| 批次上限 | 限制为 `1` 时只返回最早的完整候选；服务配置上下界测试保持通过 |
| 定向测试 | `RefundSubmitOutboxMapperIntegrationTest` 与 `RefundRetryTaskServiceImplTest` 共 `11` 项通过 |
| payment-core 后端全量 | `1064` 项通过，`0` failures、`0` errors、`0` skipped |
| XML 与补丁检查 | `RefundMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |

### 496.3 当前判断

1. 有界批次现在以可信失败发生时间而不是申请时间决定优先级，并保留同时间稳定顺序。
2. 终态事实缺失记录不会被无人值守批量动作改写，但仍需运营根据 P1 告警核对可信渠道和尝试事实后受控处置。
3. 本轮无 HTTP、前端、schema 或迁移变化；本地验证不证明目标 MySQL 索引选择、多实例调度、真实渠道补退、账务冲销或资金一致，release 四项治理阻断不变。

## 497. 2026-09-12 失败退款重试事实门禁与任务中心分层 V1.0 验证

### 497.1 本轮补强

1. 批量重试候选与 `TERMINAL_FACT_MISMATCH` 使用互补事实口径，完整要求退款单失败终态、终态时间、成功时间互斥及最新失败尝试完成事实一致。
2. 任务中心总览新增 `retryableFailedRefundCount / blockedFailedRefundCount`，保留 `failedRefundCount` 作为总风险量；三者满足总数等于可重试加阻断。
3. 重点告警新增 `REFUND_RETRY / P1`，展示两类数量；存在阻断记录时优先下钻终态事实不一致列表。管理端同步拆分卡片提示、风险表和按钮状态。

### 497.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 候选事实门禁 | 正常失败终态进入候选；终态时间缺失、无最新尝试、最新状态错配、完成时间缺失和残留成功时间全部排除 |
| 总览分层 | 固定样例返回失败总数 `6`、可批量重试 `1`、事实异常阻断 `5`，集合无遗漏且不重叠 |
| 重点告警 | 失败总数非零为 `REFUND_RETRY / P1`；事实异常非零时路由为 `/refunds?refundStatus=TERMINAL_FACT_MISMATCH` |
| 交互保护 | 可重试数为零时任务按钮显示暂无候选并禁用；总失败数和事实异常数仍可见 |
| 后端定向 | 四组 Mapper/服务测试共 `94` 项通过；首次因集成夹具缺少既有总览表/列失败，补齐后重跑通过 |
| payment-core 后端全量 | `1066` 项通过，`0` failures、`0` errors、`0` skipped |
| 管理端 | `30` 项单测通过，Vite 生产构建通过，保留既有约 `553.49 kB` 单包提示 |
| XML 与补丁检查 | `RefundMapper.xml`、`PaymentTaskCenterMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |

### 497.3 当前判断

1. 无人值守批量重试不再改写任何已知终态事实异常，任务中心也不再把全部失败退款误示为可执行候选。
2. 本轮不提供自动修复或强制改状态入口；事实异常仍需核对可信渠道、退款单、最新尝试和操作日志后受控处置。
3. 本轮无 schema 或迁移变化。本地结果不证明目标 MySQL 执行计划、多实例调度、真实渠道补退、账务冲销或资金一致，release 四项治理阻断保持不变。

## 498. 2026-09-12 主动查单候选按钮与操作口径对齐 V1.0 验证

### 498.1 本轮补强

1. 支付人工查单说明去除“尚未过期”限制，明确收银台已过期但已提交渠道的 `WAIT_CALLBACK` 仍可查；按钮使用 `pendingQueryBacklogCount` 显示当前人工候选量。
2. 退款人工查单说明同时覆盖已有渠道退款号，以及提交结果为 `UNKNOWN`、无渠道退款号但具备渠道定位事实的处理中退款；按钮使用 `pendingRefundQueryBacklogCount`。
3. 两类候选为零时分别显示“暂无待查支付”“暂无待查退款”并禁用；人工入口不使用只表示自动退避到期集合的 `duePendingQueryCount / duePendingRefundQueryCount`。

### 498.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 管理端单测 | `30` 项通过，`0` fail |
| Vite 生产构建 | 通过，转换 `84` 个模块；主包约 `553.75 kB`，保留既有大包提示 |
| 非零候选浏览器样例 | 支付 `7`、退款 `3` 时显示 `执行主动查单（7）/执行退款查单（3）`；窄屏与 `1440x900` 均无文本溢出或重叠 |
| 零候选浏览器样例 | 两个暂无候选按钮均可见且 `isEnabled=false`，控制台无 warning/error |
| 补丁检查 | `git diff --check` 通过 |

### 498.3 当前判断

1. 人工任务按钮现在表达服务端现有人工候选集合，不再把收银台有效期或渠道退款号误写成更窄的资格条件，也不在零候选时生成无意义人工执行。
2. 本轮仅修改管理端文案和按钮门禁，不改变服务端候选 SQL、退避、渠道配额、任务状态机、接口字段或持久化结构。
3. 本机固定 mock 只用于交互与布局检查，不证明真实渠道查单、回调收口、退款到账、账务结果或生产稳定性；release 四项治理阻断保持不变。

## 499. 2026-09-13 失败事件重发资格与死信分层 V1.0 验证

### 499.1 本轮补强

1. 任务中心总览将失败事件拆成全部风险、可批量重发和死信三项；全部风险继续覆盖 `FAILED + DEAD_LETTER`，执行候选只覆盖 `FAILED`。
2. 三项固定满足 `failedEventCount = retryableFailedEventCount + deadLetterEventCount`。批量重发服务不接受页面数量，不处理 `DEAD_LETTER`，仍在执行时重新查询有界 `FAILED` 候选。
3. 重点告警和管理端同步展示分层数量。死信存在时引导到死信事件列表并保持 P1；可重发数为零时任务按钮禁用，避免用空批次掩盖死信风险。

### 499.2 验证结果

| 项目 | 结果 |
| --- | --- |
| Mapper 固定样例 | `FAILED=2`、`DEAD_LETTER=3` 时总数 `5`、可批量重发 `2`、死信 `3`，互补分层成立 |
| 重点告警 | 死信存在时返回 `PAYMENT_EVENT / P1`，消息包含三项数量并下钻 `/payment-events?publishStatus=DEAD_LETTER` |
| 后端定向 | `PaymentTaskCenterMapperIntegrationTest,PaymentTaskCenterServiceImplTest` 等联合定向 `85` 项通过 |
| payment-core 后端全量 | `1068` 项通过，`0` failures、`0` errors、`0` skipped |
| 管理端 | `30` 项单测通过；Vite 生产构建通过，转换 `84` 个模块，保留既有约 `554.56 kB` 单包提示 |
| 浏览器混合样例 | 总数 `5`、可重发 `2`、死信 `3`，按钮显示“执行事件重发（2）”且可用 |
| 浏览器仅死信样例 | 总数 `3`、可重发 `0`、死信 `3`，按钮显示“暂无可重发事件”且禁用 |
| 视觉与控制台 | 窄屏 `462x674` 无全局横向溢出或文字裁切，`1440x900` 首屏无重叠；页面控制台无 warning/error |
| XML 与补丁检查 | `PaymentTaskCenterMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |

### 499.3 当前判断

1. 任务中心不再将死信风险量误作批量重发候选，风险可见性与执行资格已经分离。
2. 死信没有被自动恢复、改状态或重新投递；仍需基于事件事实和审批要求从事件页或死信补偿任务受控处置。
3. 固定 mock 只证明页面展示与交互，H2 只证明本地查询和服务编排；两者均不证明真实 MQ、下游消费、账务结果、资金一致、多实例行为或生产稳定性，release 四项治理阻断保持不变。

## 500. 2026-09-13 超时关单候选按钮与退避分层 V1.0 验证

### 500.1 本轮补强

1. 支付超时关单按钮改用 `dueExpiredPaymentCount` 表达当前实际候选量；非零展示数量，为零时禁用，避免仅有失败退避记录时执行空批次。
2. 任务说明明确只处理当前已到重试时间的记录。人工入口与自动调度共用 `PaymentExpiryTaskService.closeExpiredPayments()`，都遵守正式候选 SQL 的 `next_retry_at` 条件。
3. 运维关注表将总积压、当前可关闭和失败退避中拆为三行，继续由服务端总览提供权威计数，不由前端自行推导或回传执行数量。

### 500.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 既有 Mapper 契约 | 正式集成测试已覆盖当前可执行 `2`、失败退避 `1`；两类查询与关单候选的业务资格和退避条件一致 |
| 管理端单测 | `30` 项通过，`0` fail |
| Vite 生产构建 | 通过，转换 `84` 个模块；主包约 `555.29 kB`，保留既有大包提示 |
| 非零浏览器样例 | 总积压 `4`、当前可关闭 `3`、退避中 `1`；按钮显示“执行超时关单（3）”且可用 |
| 仅退避浏览器样例 | 总积压 `2`、当前可关闭 `0`、退避中 `2`；按钮显示“暂无可关闭支付”且禁用 |
| 视觉与控制台 | 两组窄屏 `462x674` 均无全局横向溢出或卡片文字裁切，非零样例 `1440x900` 首屏无重叠；页面控制台无 warning/error |
| 补丁检查 | `git diff --check` 通过 |

### 500.3 当前判断

1. 超时关单页面现在同时区分风险存量、当次候选和等待退避的记录，不再通过可点击按钮暗示全部积压都能立即处理。
2. 页面数量是总览读取时快照；点击后仍由服务端重新查询候选，状态并发变化可能使实际处理数不同，不应为追求数字一致而扩大锁范围或越过退避。
3. 固定 mock 和前端构建不证明真实数据库候选、多实例调度、关单结果、渠道状态、账务结果或生产稳定性，release 四项治理阻断保持不变。

## 501. 2026-09-13 告警回执人工候选与有效占用分层 V1.0 验证

### 501.1 本轮补强

1. 总览新增 `manualIssueAlertReceiptCount`，与人工批量回查使用同一资格：外部通道、已派发、供应商 `ACCEPTED`、待确认、关联同一异常下仍待确认的顶层来源，并排除仍处于 claim 超时窗口内的 `QUERYING`。
2. 人工执行继续允许越过首次等待、失败退避和自动次数上限；过期 `QUERYING` claim 可重新竞争。有效 claim 只能由当前占用者继续完成，不能被另一人工批次越过。
3. 页面保留全部待回查与自动四类分项，单独展示人工可执行数；按钮只消费人工候选数，执行端不接收页面数量。指定告警号的单条人工回查入口和请求契约不变。

### 501.2 验证结果

| 项目 | 结果 |
| --- | --- |
| Mapper claim 生命周期 | 有效 `QUERYING` 时人工计数与批量候选均为 `0`；自动次数耗尽且无有效 claim 时两者均为 `1` |
| 服务总览与重点告警 | 待回查 `13`、人工可执行 `11`、自动到期 `7`、退避 `5`、查询中 `2`、自动耗尽 `3`，文案按六项独立输出 |
| 后端定向 | `148` 项通过；首次旧夹具未设置待回查总数导致文案断言失败，补齐后完整重跑，失败轮次未计作通过证据 |
| payment-core 后端全量 | `1068` 项通过，`0` failures、`0` errors、`0` skipped |
| 管理端 | `30` 项单测通过；Vite 生产构建通过，转换 `84` 个模块，保留既有约 `555.82 kB` 单包提示 |
| 浏览器混合样例 | 待回查 `6`、人工可执行 `5`、自动到期 `2`、退避 `1`、查询中 `1`、自动耗尽 `1`；按钮可用并显示“执行回执回查（5）” |
| 浏览器仅有效占用样例 | 待回查 `2`、人工可执行 `0`、查询中 `2`；按钮显示“暂无可回查告警”且 disabled |
| 视觉与控制台 | 窄屏 `462x674` 与桌面视口无全局横向溢出或文字裁切；页面控制台无 warning/error |
| XML 与补丁检查 | `PaymentTaskCenterMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |

### 501.3 当前判断

1. 待回查总数现在只表达风险存量，人工候选数表达当前可竞争的执行集合；自动等待、退避、查询和次数上限不再被误作人工资格。
2. 总览计数、人工批量候选 SQL 和页面按钮共享同一有效 claim 排除规则；总览与执行之间的并发变化仍由执行时 claim CAS 最终裁决。
3. 固定 mock 只证明前端状态与布局，H2 只证明本地查询和服务编排；两者均不证明真实供应商、通知网关、送达、多实例竞争、资金结果或生产稳定性，release 四项治理阻断保持不变。

## 502. 2026-09-13 异常告警派发候选按钮对齐 V1.0 验证

### 502.1 本轮补强

1. 异常告警派发按钮使用 `pendingIssueAlertDispatchCount` 表达当前顶层待确认 outbox 候选量，不再在零候选时开放空批次入口。
2. 非零显示“执行异常告警派发（N）”，零值显示“暂无待派发告警”并禁用；页面不向执行接口回传该数量。

### 502.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 管理端单测 | `30` 项通过，`0` fail |
| Vite 生产构建 | 通过，转换 `84` 个模块；主包约 `555.98 kB`，保留既有大包提示 |
| 非零固定样例 | 待派发 `3` 时卡片显示 `3`，按钮显示“执行异常告警派发（3）”且可用 |
| 零值固定样例 | 待派发 `0` 时按钮显示“暂无待派发告警”且 disabled |
| 视觉与控制台 | `1440x900` 与 `462x674` 无全局横向溢出或按钮裁切；控制台无 warning/error |
| 补丁检查 | `git diff --check` 通过 |

### 502.3 当前判断

1. 页面按钮现在表达服务端既有候选快照；点击后仍由派发服务重新查询有界候选，并通过逐条来源行锁裁决实际处理对象。
2. 本轮不改变通知通道、供应商路由、失败降级、送达确认、任务统计或持久化结构。
3. 固定 mock 和前端构建不证明真实通知网关、供应商受理、最终送达、多实例防重或生产稳定性，release 四项治理阻断保持不变。

## 503. 2026-09-13 值班路由候选确定性去重 V1.0 验证

1. 首次 SLA、未确认升级和 outbox 派发统一从当前生效路由快照选择 `issue_type + severity` 分区内最早 `id`，避免一项业务候选被多条同时命中路由复制。
2. 双路由样例验证首次告警仍为一条；升级与派发在容量 `2` 时各返回两个不同业务对象，并统一使用首选接收人或通知通道。
3. Mapper 定向 `23/23`、payment-core 后端全量 `1068/1068`、XML 与补丁检查通过；无前端变化。
4. 本地 H2 不证明目标 MySQL 执行计划、配置重叠预检、真实排班或通知送达，release 四项治理阻断保持不变。

## 504. 2026-09-13 值班路由重叠批准门禁 V1.0 验证

1. 批准启用候选前按 `issueType + severity` 执行 `FOR UPDATE` 范围读取，并以星期、日期策略、小时三维共同判断重叠；同编码自身与停用配置排除。
2. 单元样例验证完全重叠候选在写运行态前拒绝并返回冲突编码，小时不相交的同范围班次可批准；Mapper 集成样例验证范围锁查询。
3. 定向 `12/12`、payment-core 后端全量 `1072/1072`、XML 与补丁检查通过；无前端变化。
4. 未自动修复历史重叠，也不将 H2 结果包装为生产 MySQL 并发、真实排班、审批或送达证据，release 四项治理阻断不变。

## 505. 2026-09-13 值班路由空范围批准协调锁 V1.0 验证

### 505.1 本轮补强

1. 新增 `t_payment_issue_duty_roster_scope_lock`，以 `(issue_type, severity)` 唯一协调行覆盖原范围查询没有现有记录时的并发空档。
2. 启用候选批准事务按 `INSERT IGNORE` 初始化、`SELECT id ... FOR UPDATE` 锁定、读取现有路由、重叠复验、运行态写入的顺序执行；协调行缺失时立即失败，不继续读取或写配置。
3. 协调唯一键不等于路由业务唯一键，同范围下小时、星期或日期策略不相交的多条班次仍可共存；停用候选不进入范围协调路径。

### 505.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 服务调用顺序 | 验证同范围依次初始化协调行、锁定协调行、加锁读取路由；锁行读取失败时不查询范围、不写运行态 |
| 空范围双事务 | H2 MySQL 模式下首事务创建并锁定新范围，第二事务在首事务提交前持续阻塞，提交后取得同一协调行 |
| 定向测试 | `PaymentIssueDutyRosterConfigServiceImplTest` 与 `PaymentConfigIssueDutyRosterReviewMapperIntegrationTest` 共 `14/14` 通过 |
| payment-core 后端全量 | `1074/1074` 通过，`0` failures、`0` errors、`0` skipped |
| 数据结构 | 双份 schema 与幂等迁移 `2026-09-13-add-payment-issue-duty-roster-scope-lock.sql` 一致；未执行生产迁移 |
| 静态检查 | `PaymentConfigMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |

### 505.3 当前判断

1. 空范围和已有范围现在都先锁定稳定协调行，同一类型与等级的两个启用批准事务不会再并行通过重叠复验。
2. 本轮不改变 HTTP、DTO、前端、历史路由状态或运行时最早 `id` 兜底规则；协调行按首次批准惰性创建，不从历史配置推断审批事实。
3. H2 结果不证明目标 MySQL 隔离级别、锁等待时长、死锁恢复、迁移执行或多实例容量，也不构成可信审批、真实排班和通知送达证据；release 四项治理阻断保持不变。

## 506. 2026-09-13 控制策略巡检候选按钮对齐 V1.0 验证

### 506.1 本轮补强

1. 任务中心总览新增 `enabledControlPolicyCount`，固定统计全部 `status = 'ENABLED'` 的支付控制策略，与 `runAllEnabledControlPolicySelfChecks()` 的执行集合一致。
2. `controlPolicyWarningCount` 继续统计启用且自检状态非 PASS 的风险，不作为批量巡检候选数；已 PASS 的启用策略仍可参与下一轮巡检。
3. 页面使用启用数控制按钮数量和禁用状态，候选为零时不再主动生成空任务日志；点击后服务端仍重新扫描启用策略，不接收页面候选数。

### 506.2 验证结果

| 项目 | 结果 |
| --- | --- |
| Mapper 口径 | 2 条启用策略中 1 条 PASS、1 条 WARN，另有 1 条停用 WARN；总览返回启用候选 `2`、告警 `1` |
| 后端定向 | `PaymentTaskCenterMapperIntegrationTest`、服务与控制器共 `90/90` 通过 |
| payment-core 后端全量 | `1075/1075` 通过，`0` failures、`0` errors、`0` skipped |
| 管理端 | `30/30` 单测通过；Vite 构建转换 `84` 个模块，主包约 `556.25 kB`，保留既有体积提示 |
| 非零固定样例 | 启用策略 `3`、告警 `1`，按钮显示“执行控制策略巡检（3）”且可用 |
| 零值固定样例 | 启用策略 `0` 时按钮显示“暂无启用控制策略”且 disabled，告警卡仍保留独立风险值 |
| 视觉与控制台 | `1440x900` 与 `462x674` 四组样例无横向溢出、文字裁切或遮挡；控制台无 warning/error |
| 静态检查 | `PaymentTaskCenterMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |

### 506.3 当前判断

1. 页面现在区分“本次可巡检的启用策略”和“当前自检异常策略”，不再用风险结果替代执行集合，也不阻止全部 PASS 策略重新校验。
2. 本轮无 schema、迁移或策略状态写入规则变化；总览只是读取时快照，实际处理数仍由点击后的服务端扫描决定。
3. 固定 mock 只证明交互和布局，H2 只证明本地 SQL 口径；两者不证明真实渠道、网关、商户号、令牌托管、生产执行计划或稳定性，release 四项治理阻断保持不变。

## 507. 2026-09-13 SLA 风险与动作候选分层 V1.0 验证

### 507.1 本轮补强

1. `overdueIssueCount` 继续统计全部超过 P1/P2 SLA 的基础异常；新增 `pendingIssueAlertGenerationCount` 与 `pendingIssueAlertEscalationCount`，分别统计当前可生成首次告警和可生成未确认升级告警的候选。
2. `actionableIssueSlaCount` 为两个互斥动作集合之和，只用于管理端人工巡检按钮；点击后服务端仍重新查询候选，不接收页面数量。
3. 重点告警和页面风险表同时展示基础风险、待首次告警和待升级告警，避免将“已有告警但尚未到升级时间”误作当前可执行任务。

### 507.2 验证结果

| 项目 | 结果 |
| --- | --- |
| Mapper 口径 | 初始基础风险 `1`、首次候选 `1`、升级候选 `0`；写入 10 分钟前待确认来源告警后为 `1/0/0`；推进至 31 分钟前后为 `1/0/1`，正式列表与计数一致 |
| 服务总览 | 模拟基础风险 `6`、首次候选 `2`、升级候选 `3`，动作合计返回 `5`，重点告警文案同步展示三项 |
| 后端定向 | Mapper、服务与控制器共 `92/92` 通过 |
| payment-core 后端全量 | `1077/1077` 通过，`0` failures、`0` errors、`0` skipped |
| 管理端 | `30/30` 单测通过；Vite 构建转换 `84` 个模块，主包约 `556.75 kB`，保留既有体积提示 |
| 非零固定样例 | 基础风险 `6`、首次 `2`、升级 `3`、动作合计 `5`，按钮显示“执行 SLA 升级巡检（5）”且可用 |
| 零动作固定样例 | 基础风险 `1`、首次 `0`、升级 `0`、动作合计 `0`，按钮显示“暂无可升级异常”且 disabled |
| 视觉与控制台 | `1440x900` 与 `462x674` 四组样例无横向溢出、文字裁切或遮挡；控制台无 warning/error |
| 静态检查 | `PaymentTaskCenterMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |

### 507.3 当前判断

1. 页面现在不会因仅存在基础风险而创建无处理对象的人工任务日志，同时不会隐藏仍需持续关注的 SLA 风险。
2. 本轮无 schema、迁移、阈值、生成键、批次上限或自动调度变化；总览只是读取时快照，实际处理数仍由执行时查询决定。
3. 固定 mock 只证明交互和布局，H2 只证明本地 SQL 口径；两者不证明目标 MySQL 执行计划、真实通知送达、生产任务执行或稳定性，release 四项治理阻断保持不变。

## 508. 2026-09-13 查单监控告警候选门禁 V1.0 验证

### 508.1 本轮补强

1. 总览新增 `actionableQueryMonitorAlertCount`，分别判断失败趋势和限流拒绝是否达到配置阈值，并排除冷却窗口内已有同类待确认来源告警的类别。
2. 总览和执行路径复用同一失败数、失败率、最小样本数和限流阈值函数，避免读写口径因公式复制而漂移；执行时仍重新读取指标和冷却状态。
3. 页面仅在候选数非零时开放人工按钮，候选为零时不再主动生成无产出任务日志。

### 508.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 失败趋势候选 | 近 24 小时查单 `12` 次、失败 `4` 次，失败率 `33.33%` 命中阈值且无冷却告警，候选为 `1` |
| 限流候选 | 近 24 小时限流拒绝 `10` 次达到阈值且无冷却告警，候选为 `1` |
| 冷却分层 | 两类阈值同时命中，失败趋势已有 60 分钟内待确认告警、限流拒绝无同类告警，动作候选合计为 `1` |
| 后端定向 | Mapper、服务与控制器共 `93/93` 通过 |
| payment-core 后端全量 | `1078/1078` 通过，`0` failures、`0` errors、`0` skipped |
| 管理端 | `30/30` 单测通过；Vite 构建转换 `84` 个模块，主包约 `556.96 kB`，保留既有体积提示 |
| 非零固定样例 | 失败趋势和限流拒绝均可生成，候选 `2`，按钮显示“生成查单监控告警（2）”且可用 |
| 零值固定样例 | 候选 `0` 时按钮显示“暂无可生成监控告警”且 disabled |
| 视觉与控制台 | `1440x900` 与 `462x674` 四组样例无横向溢出、文字裁切或遮挡；控制台无 warning/error |
| 静态检查 | `git diff --check` 通过 |

### 508.3 当前判断

1. 人工按钮现在表达当前真正可写入的监控告警类别数，不再仅因页面成功加载就允许执行。
2. 总览候选只是读取时快照，指标或冷却状态可在点击前变化；实际处理数仍由执行时复验决定，页面数量不进入写入逻辑。
3. 固定 mock 与 Mockito 只证明本地交互和分支口径，不证明真实查询流量、生产告警生成、通知送达或稳定性，release 四项治理阻断保持不变。

## 509. 2026-09-13 路由规则冲突批准并发串行化 V1.0 验证

### 509.1 本轮补强

1. 新增固定协调键 `ENABLED_RULE_SET`。启用且 `rolloutPercentage > 0` 的复核批准先初始化并锁定协调行，再锁当前规则行、当前读全部规则、执行冲突复验并写入运行态和版本。
2. 全局协调锁位于当前规则行锁之前，避免不同规则批准各自持锁后形成反向等待；冲突全集使用 `SELECT ... FOR UPDATE`，不依赖可能已由渠道查询建立的普通一致性读快照。
3. 停用和 `0%` 候选不引入运行时冲突，因此不获取全局协调锁；既有单候选校验、异人复核、基线漂移和冲突表达式规则保持不变。

### 509.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 服务顺序 | 验证初始化协调行、锁协调行、锁当前规则、当前读全部规则、写运行态的固定顺序 |
| 失败闭合 | 协调行读取为空时抛错，不读取冲突全集，不写运行态或版本 |
| 双事务 | 协调行最初不存在时，第二事务在首事务提交前阻塞，之后取得同一协调行 |
| schema contract | 双份 schema、幂等迁移、唯一键、固定锁名及 Mapper 当前读契约一致 |
| 后端定向 | `PaymentConfigServiceImplTest` 与 `PaymentConfigRouteRuleVersionMapperIntegrationTest` 共 `71/71` 通过 |
| payment-core 后端全量 | `1081/1081` 通过，`0` failures、`0` errors、`0` skipped |
| 静态检查 | `PaymentConfigMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |

首次定向运行因失败闭合用例保留两个不会到达的 Mockito 桩而失败；清理时误删相邻成功用例的当前规则桩，第二次运行暴露并修正；第三次定向与随后全量测试全部通过，前两次不计作通过证据。

### 509.3 当前判断

1. 不同规则编码的启用候选不再并行基于旧快照完成冲突批准；普通预检仍只用于提示，最终裁决以批准事务内的协调锁和当前读为准。
2. 本轮没有前端字段、按钮、请求或响应变化，因此不重复管理端构建与视觉检查；迁移只创建技术协调表，不回填或改写业务规则。
3. H2 结果不证明目标 MySQL 实际隔离级别、锁等待时长、死锁恢复、迁移执行或多实例容量，也不构成可信审批或生产配置事实；release 四项治理阻断保持不变。

## 510. 2026-09-13 路由同优先级潜在冲突显式确认 V1.0 验证

### 510.1 本轮补强

1. 批准请求新增 `priorityCollisionAcknowledged`；未明确确认为 `true` 时，涉及 `PRIORITY_COLLISION` 的候选不得写运行态或版本。
2. 明确确认后可继续发布复核级潜在碰撞，但 `BLOCKER` 始终拒绝。确认值只表示复核人接受表达式互斥性风险，不构成自动互斥证明。
3. 服务端仍在全局协调锁内当前读最新规则全集并作最终裁决；页面不自行判断冲突是否存在，成功批准或驳回后清空确认框。

### 510.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 未确认潜在碰撞 | `PRIORITY_COLLISION` 候选批准失败，错误提示要求确认表达式互斥性风险，运行态和版本均不写入 |
| 已确认潜在碰撞 | 请求字段为 `true` 时允许继续批准并写入候选；验证确认值不会改变既有冲突分类 |
| 阻断冲突 | `BLOCKER` 在确认值为 `true` 时仍被拒绝，不写运行态或版本 |
| 后端定向 | `PaymentConfigServiceImplTest` `69/69`、`PaymentConfigControllerTest` `15/15`，合计 `84/84` 通过 |
| payment-core 后端全量 | `1082/1082` 通过，`0` failures、`0` errors、`0` skipped |
| 管理端 | `30/30` 单测通过；Vite 构建转换 `84` 个模块，CSS 约 `30.22 kB`、JS 约 `557.95 kB`，保留既有大包提示 |
| 页面交互 | 固定 mock 捕获 `priorityCollisionAcknowledged: true`；批准成功后确认框恢复未勾选，控制台无 warning/error |
| 视觉检查 | 桌面 `1280x720` 详细检查确认框、长说明、输入区与表格无重叠；`390x844` 真实视口整页截图确认响应式布局生效，局部自动截图受桌面权限策略限制，未将其计作窄屏局部细节证据 |
| 静态检查 | `PaymentConfigMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |

### 510.3 当前判断

1. 普通复核意见不再隐式放行同优先级潜在碰撞，批准人必须通过独立字段显式接受风险；该动作仍不等于可信身份或正式签字审批。
2. 本轮不修改 `PRIORITY_COLLISION / REVIEW` 分类、运行时 `priority ASC, id ASC` 顺序、schema、索引或迁移；最终结果以服务端锁内最新快照为准。
3. 固定 mock、Mockito 和本地 H2 不证明表达式实际互斥、目标 MySQL 锁行为、真实渠道配置或生产稳定性，release 四项治理阻断保持不变。

## 511. 2026-09-13 路由冲突复核审计持久化与全量 schema V1.0 验证

### 511.1 本轮补强

1. 批准事务将服务端最新冲突快照的检测结果与显式确认结果写入复核记录；待处理和直接驳回不执行批准冲突复验，因此两字段保持 `NULL`。
2. `BLOCKER` 优先于未确认的 `PRIORITY_COLLISION` 返回，避免阻断级问题被复核级提示遮蔽；成功批准无潜在碰撞写 `false/false`，有潜在碰撞写 `true/true`。
3. `systems/payment-core/database/schema.sql` 补齐此前遗漏的路由版本表和复核表，并与后端全量 schema、增量迁移、Mapper 和 DTO 建立静态契约。

### 511.2 验证结果

| 项目 | 结果 |
| --- | --- |
| Mapper 持久化 | H2 完成复核后列表返回 `priorityCollisionDetected=true`、`priorityCollisionAcknowledged=true`；唯一待办释放与第二申请插入保持通过 |
| 无碰撞批准 | 服务测试精确验证完成复核时写入 `false/false` |
| 直接驳回 | 服务测试精确验证完成复核时写入 `null/null`，不伪造未发现结论 |
| 阻断优先级 | 同时存在先返回的潜在碰撞和后返回的阻断冲突且未勾选时，最终错误仍为“候选规则存在阻断级冲突” |
| schema contract | 两份全量 schema 均包含审计列；数据库全量 schema 补齐路由版本/复核表；迁移和 Mapper 读写字段一致 |
| 后端定向 | 服务、控制器、Mapper 集成和 schema 契约共 `88/88` 通过 |
| payment-core 后端全量 | `1083/1083` 通过，`0` failures、`0` errors、`0` skipped |
| 管理端 | `30/30` 单测通过；Vite 构建转换 `84` 个模块，CSS 约 `30.22 kB`、JS 约 `558.24 kB`，保留既有大包提示 |
| 页面样例 | 三条固定记录分别展示“冲突复验未执行”“未发现同优先级潜在冲突”“同优先级风险已确认”，`APPROVED` 与 `PENDING_REVIEW` 状态完整 |
| 视觉与控制台 | `1280x720` 与 `462x674` 无文案遮挡；窄屏根节点 `clientWidth = scrollWidth = 447`，表格保持局部横向滚动；控制台无 warning/error |
| 静态检查 | `PaymentConfigMapper.xml` 的 `xmllint` 与 `git diff --check` 通过 |

首次定向运行因新增查询投影误插入到结构相似的渠道复核查询而失败，路由复核 DTO 字段仍为 `null`；移除错误投影并加入路由复核查询后，第二次定向与随后全量测试全部通过，首次失败不计作通过证据。视觉固定样例首次漏填已完成状态，修正为 `APPROVED` 后重新验证状态数量与控制台。

### 511.3 当前判断

1. 路由复核记录现在可区分未执行、无碰撞和已确认碰撞，不再依赖自由文本追溯风险接受；异常组合仍被页面显式暴露，不自动美化。
2. 新字段和补齐的数据库全量 schema 只建立代码与部署基线；增量迁移尚未在目标 MySQL 执行，历史已完成复核保持 `NULL`，不得倒推当时是否存在或确认冲突。
3. 自声明复核记录不等于可信身份、正式签字或不可抵赖审计；固定 mock、H2 和静态契约也不证明生产数据、锁行为、渠道配置或稳定性，release 四项治理阻断保持不变。

## 512. 2026-09-13 路由冲突确认按申请隔离 V1.0 验证

### 512.1 本轮补强

1. 路由冲突确认从复核区共享布尔值改为唯一待办申请号，控件随待办行展示，不再允许对申请 A 的选择无提示地提交给申请 B。
2. 请求载荷由纯函数构造，仅在批准动作且处理申请与已确认申请相同时返回 `priorityCollisionAcknowledged=true`；驳回和其他申请固定为 `false`。
3. 批准弹窗同时展示当前申请号和确认状态，成功处理或刷新发现申请已离开待办后清除选择；服务端锁内复验与拒绝规则保持不变。

### 512.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 管理端全量单测 | `32/32` 通过，新增 2 项覆盖同申请确认、跨申请隔离、空申请号和驳回载荷 |
| Vite 生产构建 | `85` 个模块转换成功；CSS 约 `30.22 kB`、JS 约 `558.25 kB`，仅保留既有大包提示 |
| 申请级交互 | 两条待办初始均未选；勾选 `RRV-AUDIT-001` 后第二条保持未选，随后勾选 `RRV-AUDIT-002` 时第一条自动取消 |
| 窄屏布局 | `462x674` 下根节点 `clientWidth = scrollWidth = 447`；表格局部横向滚动，两个标签均在操作单元格内，按钮位于标签下方且不重叠 |
| 控制台与补丁 | 页面无 warning/error，`git diff --check` 通过 |
| 桌面视觉 | 当前环境未获得新的桌面浏览器截图，不记为通过证据 |

### 512.3 当前判断

1. 管理端现在能证明确认值与点击批准的申请号一致，避免同屏多待办之间误复用；后端仍以批准事务中的最新冲突复验为最终裁决。
2. 本轮无后端、接口字段、schema、索引或迁移变化；前一轮持久化的确认结果语义不变。
3. 固定 mock、前端单测和窄屏检查不证明表达式确实互斥、可信身份、正式审批、生产配置或渠道稳定性，release 四项治理阻断保持不变。

## 513. 2026-09-13 路由冲突对象审计快照 V1.0 验证

### 513.1 本轮补强

1. 路由复核记录新增 `priorityCollisionRuleCodesJson`，批准时从最新冲突复验结果提取候选之外的对照规则编码，以稳定顺序序列化为 JSON 数组并与批准结果同事务写入。
2. 未确认拒绝信息追加实际对照规则编码；JSON 而非分隔字符串用于持久化，避免规则编码自身包含逗号时失去字段边界。
3. 管理端解析快照并展示具体对象；空快照与畸形 JSON 分别明确显示“冲突对象未记录”和“冲突对象快照异常”，不从当前配置补写历史事实。

### 513.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 服务持久化 | 输入碰撞顺序 `RULE_Z_APP / RULE_A_APP`，完成复核参数稳定为 `["RULE_A_APP","RULE_Z_APP"]`；无碰撞和驳回写 `null` |
| 编码边界 | 前端 JSON 用例包含 `RULE,H5`，展示时保留为单个规则编码；未确认错误包含 `RULE_APP_ONLY` |
| Mapper 集成 | H2 完成复核后可回读 `priorityCollisionRuleCodesJson`，原唯一待办释放和后续申请插入保持通过 |
| schema contract | 双份全量 schema、新增迁移及 Mapper 查询/更新均使用 `priority_collision_rule_codes_json` |
| 后端定向 | `PaymentConfigServiceImplTest` 69 项、`PaymentConfigControllerTest` 15 项、Mapper 集成 3 项、schema 契约 1 项，共 `88/88` 通过 |
| payment-core 后端全量 | `1083/1083` 通过，`0` failures、`0` errors、`0` skipped |
| 管理端与构建 | `33/33` 通过；Vite 转换 `85` 个模块，CSS 约 `30.22 kB`、JS 约 `558.78 kB`，仅保留既有大包提示 |
| 窄屏视觉 | 准确 `462x674` 视口下根节点 `scrollWidth = clientWidth = 447`；目标文案完整位于 `302.72px` 宽复核单元格内，中心点无其他元素覆盖 |
| 控制台 | 页面无 JavaScript warning/error；Chrome Log 仅有既有 `/favicon.ico` 404，不属于业务接口失败 |
| 静态检查 | Mapper XML `xmllint`、字段名扫描与 `git diff --check` 通过 |

### 513.3 当前判断

1. 已批准的潜在碰撞记录现在能回答“批准时检测到哪些对照规则”，不再只能依赖布尔值和当前配置推测；快照仍只代表检测器当时输出，不证明表达式实际相交或互斥。
2. 待办、驳回、无碰撞批准及迁移前历史不写快照，避免把未执行或不存在的复验伪装成已检查；畸形数据由页面显式暴露。
3. 增量迁移尚未在目标 MySQL 执行；本地 H2、固定 mock、自声明身份和视觉检查不构成生产迁移、正式审批、不可抵赖审计或渠道稳定性证据，release 四项治理阻断保持不变。

## 514. 2026-09-13 路由冲突确认绑定精确集合 V1.0 验证

### 514.1 本轮补强

1. 路由复核待办返回当前冲突编码预览，管理端确认状态由单一申请号提升为 `requestNo + ruleCodes`；刷新后只要申请离开待办或预览集合变化，旧确认立即失效。
2. 批准请求携带复核人实际看到的完整编码数组；服务端在全局协调锁和最新规则全集当前读之后，以稳定集合精确比较。集合新增、减少至空、替换、缺失或重复均不允许沿用旧确认。
3. 仓库声明 Java 8，本轮测试编译暴露六个既有测试类使用 Java 9/11 API；已机械替换为 Java 8 等价写法，不改变业务断言或生产代码。

### 514.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 待办预览 | 两条顺序相反的运行态碰撞返回稳定排序 `RULE_A_APP / RULE_Z_APP`；已完成记录不补算当前预览 |
| 精确集合批准 | 申请号与预览集合完全一致时允许批准，并继续把服务端最终集合序列化为审计 JSON |
| 集合漂移 | 新增冲突、已确认冲突减少至空均返回“同优先级潜在冲突集合已变化”，且不写运行态；重复编码在读取冲突全集前拒绝 |
| 前端状态 | 单测覆盖申请号、集合内容、批准与驳回载荷；无预览时不生成确认对象，驳回和未确认请求发送空数组及 `false` |
| 后端定向 | `PaymentConfigServiceImplTest` `73/73`、`PaymentConfigControllerTest` `15/15`，合计 `88/88` 通过 |
| payment-core 后端全量 | `1087/1087` 通过，`0` failures、`0` errors、`0` skipped |
| 管理端与构建 | `33/33` 通过；Vite 转换 `85` 个模块，CSS 约 `30.22 kB`、JS 约 `559.51 kB`，仅保留既有大包提示 |
| 视觉与布局 | `1440x1000` 与 `462x674` 根节点均无横向溢出；具名标签完整位于操作单元格，复选框和按钮无矩形重叠，表格保持局部横向滚动 |
| 控制台 | 无 JavaScript warning/error；唯一 HTTP/Console error 为既有 `favicon.ico` 404 |
| 静态检查 | `git diff --check` 通过；实现提交 `43beef19` |
| 统一门禁复判 | 脚本退出码 `2` 仅来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、统一 `blockerCount=1`、内部 `releaseBlockerCount=4` |

### 514.3 当前判断

1. 风险确认现在只适用于复核人看到的精确对象集合，不能再用旧布尔确认吸收随后出现或消失的冲突；列表预览仍只是交互提示，批准事务锁内结果才是最终裁决。
2. 本轮不改端点路径、数据库结构、运行时路由顺序或冲突分类；最终审计仍只保存服务端批准时的冲突 JSON 快照，不保存页面临时状态。
3. 固定 mock、Mockito、本地 H2 与截图不证明表达式实际相交或互斥、目标 MySQL 锁行为、可信身份、正式审批或生产稳定性，release 四项治理阻断保持不变。

## 515. 2026-09-13 路由冲突确认非法输入锁前拒绝 V1.0 验证

### 515.1 本轮补强

1. 将 `priorityCollisionAcknowledged` 和确认编码集合的纯请求归一化，从全局冲突锁及运行态规则基线读取之后前移到候选实体构建之前。
2. 重复编码和含控制字符编码现在在渠道查询、协调锁初始化/获取、运行态规则锁和冲突全集读取前失败；复核申请锁仍作为确认待办状态和异人声明的必要入口保留。
3. 合法集合的锁序、最新集合精确比较、最终 JSON 审计快照和事务原子性保持不变。

### 515.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 重复编码 | 返回“已确认冲突规则编码不能重复”；渠道、协调锁、运行态规则和冲突全集 Mapper 均零访问 |
| 控制字符 | 输入包含换行时返回“已确认冲突规则编码不能包含控制字符”；候选渠道与全部规则锁查询均零访问 |
| 合法路径回归 | 既有精确集合批准、集合新增/减少拒绝、`BLOCKER` 优先级及审计快照测试继续通过 |
| 服务定向 | `PaymentConfigServiceImplTest` `74/74` 通过，`0` failures、`0` errors、`0` skipped |
| payment-core 后端全量 | `1088/1088` 通过，`0` failures、`0` errors、`0` skipped |
| 静态检查 | `git diff --check` 通过；实现提交 `0a31c817` |
| 统一门禁复判 | 脚本退出码 `2` 仅来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、统一 `blockerCount=1`、内部 `releaseBlockerCount=4` |

首次定向测试因校验顺序改变后四个旧 stub 成为不可达，Mockito 严格模式以 `UnnecessaryStubbingException` 失败；删除过期 stub 后重跑 `74/74` 通过，随后全量回归通过。首次失败不计作通过证据。

### 515.3 当前判断

1. 非法确认集合不再持有系统级路由冲突协调锁，降低无效请求放大配置发布阻塞的风险；这不是认证、授权或接口级流量治理替代品。
2. 本轮只调整批准事务内的校验顺序，不改变端点、响应、数据库结构、冲突算法或合法请求的锁序。
3. Mockito 和本地全量测试不证明目标 MySQL 锁等待、死锁、超时或生产稳定性，可信身份与 release 四项真实治理条件仍需外部证据。

## 516. 2026-09-13 候选路由冲突线性扫描 V1.0 验证

### 516.1 本轮补强

1. 候选冲突计算不再构造“全部当前规则 + 候选”的临时全集，也不再调用全量两两扫描后过滤；改为候选与每条有效当前规则逐一比较。
2. 调用方向保持 `currentRule, candidate`，与旧实现把候选追加在末尾后的组合方向一致；Mapper 顺序、同编码排除、启用与灰度过滤以及无效表达式处理保持原语义。
3. `listRouteRuleConflicts` 继续使用全量两两扫描，运营侧查看全部规则间冲突的能力未被收窄。

### 516.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 候选相关性 | 当前规则中加入一组彼此构成 `ROUTE_DIVERGENCE / BLOCKER`、但与候选场景无交集的规则，候选预览仍只返回 `RULE_A_APP / RULE_Z_APP` |
| 语义回归 | 候选无效表达式仍返回 `INVALID_EXPRESSION / BLOCKER`；同编码、停用、`0%` 和无效当前规则继续跳过 |
| 复杂度边界 | 候选预览与批准复验由全量组合扫描后过滤收窄为候选逐条比较；全量冲突查询保持完整组合扫描 |
| 服务定向 | `PaymentConfigServiceImplTest` `74/74` 通过，`0` failures、`0` errors、`0` skipped |
| payment-core 后端全量 | `1088/1088` 通过，`0` failures、`0` errors、`0` skipped |
| 静态检查 | `git diff --check` 通过；实现提交 `818178b1` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`、统一 `blockerCount=1`、内部 `releaseBlockerCount=4` |

### 516.3 当前判断

1. 批准协调锁内只计算候选相关冲突，按代码路径由 `O(N²)` 降为 `O(N)`；该判断不等同于真实数据规模下的延迟、锁等待或吞吐压测结果。
2. 本轮不改变 HTTP 契约、前端、Mapper、schema、迁移、路由运行顺序、冲突分类或审计持久化语义。
3. Mockito 和本地全量测试不证明目标 MySQL 并发行为、生产容量、可信身份、正式审批或渠道稳定性，release 四项真实治理条件仍需外部证据。

## 517. 2026-09-13 路由复核阻断冲突预览 V1.0 验证

### 517.1 本轮补强

1. 待办候选的一次冲突计算同时派生同优先级确认编码和完整阻断冲突列表，不新增第二次规则扫描或数据库查询。
2. 管理端在阻断列表非空时展示 `冲突类型 / 对照规则 / 原因`，禁用批准、保留驳回，并清除与该申请绑定的旧同优先级风险确认。
3. 阻断字段缺失或为空兼容无阻断响应；字段存在但数组或元素结构畸形时不生成虚假详情，并失败闭合禁止批准。

### 517.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 候选阻断预览 | 候选与 `RULE_BLOCKING_APP` 形成 `ROUTE_DIVERGENCE / BLOCKER`，待办返回完整阻断对象 |
| 无关冲突隔离 | `RULE_ENTERPRISE_A / RULE_ENTERPRISE_B` 彼此阻断但与候选场景无交集，不进入该候选阻断预览 |
| 确认与动作门禁 | 有效阻断使批准不可用并使旧确认失效；畸形列表同样禁止批准；驳回不受影响 |
| 后端定向与全量 | `PaymentConfigServiceImplTest` `74/74`、payment-core 后端 `1088/1088` 通过，均为零失败、零错误、零跳过 |
| 管理端与构建 | 管理端 `35/35` 通过；Vite 转换 `85` 个模块，保留既有大包提示 |
| 视觉与控制台 | `1440x1000`、`462x674` 根节点 `clientWidth = scrollWidth`；阻断文案与按钮无覆盖，批准为 disabled，控制台无错误 |
| 静态与门禁 | `git diff --check` 通过；统一门禁退出码 `2`，`canPromoteTestToMaster=true`、`canCreateRelease=false`、`blockerCount=1`、`releaseBlockerCount=4` |

### 517.3 当前判断

1. 复核人现在能在点击批准前看到当前已知阻断原因，不再把“无同优先级确认项”误读为“候选可批准”；最终判断仍是服务端事务内最新规则复验。
2. 本轮不改变冲突算法、批准状态机、审计快照、Mapper 或数据库结构，也不把待办预览持久化为历史事实。
3. 固定响应与本地视觉检查不证明真实规则数据、目标 MySQL 锁行为、可信身份或正式审批，release 四项真实治理条件仍需外部证据。

## 518. 2026-09-13 路由复核警告冲突预览 V1.0 验证

### 518.1 本轮补强

1. 候选冲突结果按 `BLOCKER / WARNING / REVIEW` 分层派生：阻断返回完整对象、警告返回完整对象、同优先级待复核返回稳定规则编码。
2. 管理端展示合法警告详情但保持批准可用；已返回却畸形的警告列表不生成虚假详情，并禁止批准直至刷新获得可信结构。
3. 警告预览不写数据库、不进入批准请求，也不改变服务端锁内阻断、精确集合确认和最终碰撞审计规则。

### 518.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 警告派生 | 候选与 `RULE_DUPLICATE_APP` 形成 `DUPLICATE_RULE / WARNING`，待办返回完整警告对象 |
| 三层共存 | 同一候选同时返回一个阻断、一条维护警告和稳定排序的同优先级确认对象，三类结果互不覆盖 |
| 前端放行语义 | 合法警告完整展示且 `canApprove=true`；有效阻断或畸形警告列表使批准不可用 |
| 后端定向与全量 | `PaymentConfigServiceImplTest` `74/74`、payment-core 后端 `1088/1088` 通过，均为零失败、零错误、零跳过 |
| 管理端与构建 | 管理端 `37/37` 通过；Vite 转换 `85` 个模块，保留既有大包提示 |
| 视觉与控制台 | `1440x1000`、`462x674` 根节点 `clientWidth = scrollWidth`；阻断、警告与按钮无覆盖，控制台无错误 |
| 静态与门禁 | `git diff --check` 通过；统一门禁退出码 `2`，`canPromoteTestToMaster=true`、`canCreateRelease=false`、`blockerCount=1`、`releaseBlockerCount=4` |

### 518.3 当前判断

1. 路由复核待办现在完整表达自动检测器的阻断、人工确认和维护警告三层输出，不再要求复核人用全量列表手工关联候选。
2. `WARNING` 仍是信息性治理提示，批准后可能保留重复或遮蔽关系；若未来要强制接受，应另行设计确认和审计契约，不能追认本轮页面展示为批准证据。
3. 本轮不证明生产规则分布、表达式语义、目标 MySQL 时序、可信身份或正式审批，release 四项真实治理条件仍需外部证据。

## 519. 2026-09-13 支付渠道流水绑定锁后复核 V1.0 验证

### 519.1 本轮补强

1. 支付成功回调在初次读取和订单协调锁内重读后均核对渠道及渠道流水，防止等待锁期间形成的新绑定被晚到回调覆盖。
2. 主动查单在使用渠道返回流水前与最新支付事实比对；冲突以 `PAYMENT-1026` 失败闭合，并沿用最小化 `QUERY_FAILED` 通知与处理日志。
3. 流水冲突发生在返回码快照、成功状态抢占、订单/账单结算和成功事件之前；未改变无流水查询结果复用并发成功回调流水的既有兼容路径。

### 519.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 回调锁后复核 | 初读无流水、锁后出现另一流水时返回 `PAYMENT-1031`，成功状态、结算和事件均未执行 |
| 查单并发冲突 | 查单返回流水与锁后并发成功流水不同时返回 `PAYMENT-1026`，写固定失败事实且不返回矛盾详情 |
| 服务定向 | `PaymentServiceImplTest` `84/84` 通过，`0` failures、`0` errors、`0` skipped |
| payment-core 后端全量 | `1090/1090` 通过，`0` failures、`0` errors |
| 静态检查 | `git diff --check` 通过；实现提交 `c236245a` |
| 统一门禁复判 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`blockerCount=1`、`releaseBlockerCount=4` |

### 519.3 当前判断

1. 支付成功事实现在不会因查单/回调并发窗口返回或写入另一渠道流水；这只证明本地服务编排保护，不证明渠道侧交易归属。
2. 本轮不改端点、DTO、Mapper、schema、迁移或前端；`PAYMENT-1030 / PAYMENT-1031 / PAYMENT-1026` 沿用既有错误边界。
3. 本地 Mockito 与 H2 回归不替代真实渠道、目标 MySQL 并发、到账、对账、正式审批或生产稳定性证据。

## 520. 2026-09-13 支付成功精确账单结算 V1.0 验证

### 520.1 本轮补强

1. 支付详情从 `t_bill.order_no` 宽关联收窄为 `latest prepay.bill_no -> t_bill.bill_no` 精确关联，避免同订单多账单导致详情多行或账单号漂移。
2. 成功回调与主动查单统一按详情中的 `billNo` 结算账单；不再按订单号批量更新同订单账单。
3. 标准订单和绑定账单更新均要求影响一行，绑定缺失或更新未命中时在成功事件创建前失败，由外层事务回滚本次成功收口。

### 520.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 精确详情关联 | 同订单两张账单时 `findDetail` 返回预付单绑定的 `BILL-SNAPSHOT-TARGET`，不产生多行详情 |
| 精确账单更新 | 更新目标账单后另一账单仍为 `0.00 / 待支付`，目标账单为 `88.00 / 已结清` |
| 失败闭合 | 绑定账单更新返回零行时抛出 `payment bill settlement was not applied atomically`，不创建成功事件 |
| 定向测试 | `PaymentServiceImplTest + PaymentReturnCodeSnapshotMapperIntegrationTest` 合计 `87/87` 通过 |
| payment-core 后端全量 | `1092/1092` 通过，`0` failures、`0` errors |
| 静态检查 | `git diff --check` 通过；实现提交 `33a3c7bf` |
| 统一门禁复判 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`blockerCount=1`、`releaseBlockerCount=4` |

### 520.3 当前判断

1. 支付核心现在以预付单绑定账单作为结算目标，消除了按订单号误结清其他账单的代码路径。
2. 本轮不增加数据库唯一约束或迁移，不推断一个订单只能有一张账单；多账单业务含义仍由账单域决定。
3. H2 和 Mockito 不证明生产历史绑定完整、目标 MySQL 影响行数与事务回滚、真实到账、对账或财务核验。

## 521. 2026-09-13 支付尝试历史保留 V1.0 验证

### 521.1 本轮补强

1. 非成功支付回调只更新按 `created_at DESC, id DESC` 排序的最新支付尝试，不再覆盖同一支付单的较早尝试。
2. 最新尝试更新必须恰好影响一行；零行时在预付单更新和 `PAYMENT_PENDING` 事件前失败，使回调事务回滚。
3. 超时关单的准入 SQL 已要求不存在支付尝试，因此删除关单服务中原本无效的尝试更新。

### 521.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 历史尝试隔离 | H2 MySQL 模式下较早尝试保持“待回调”且状态类型为 `NULL`，最新尝试更新为“待回调 / warn” |
| 失败闭合 | Mapper 返回 `0` 时抛出 `payment attempt pending update was not applied atomically`，不更新预付单、不创建事件 |
| 过期关单 | 成功与竞态跳过路径均不调用最新尝试更新，其他关单副作用保持原契约 |
| 定向测试 | `PaymentServiceImplTest + PaymentExpiryTaskServiceImplTest + PaymentMapperStateTransitionIntegrationTest` 合计 `111/111` 通过 |
| payment-core 后端全量 | `1094/1094` 通过，`0` failures、`0` errors、`0` skipped |
| 静态与提交 | `git diff --check` 通过；实现提交 `68d3a326` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`、统一 `blockerCount=1`、内部 `releaseBlockerCount=4` |

### 521.3 当前判断

1. 多次支付提交的较早尝试不再被晚到的非成功回调篡改，当前尝试与历史尝试的审计边界已经分离。
2. 本轮无前端、HTTP、schema、索引或迁移变化，未重复运行管理端测试、构建或视觉检查。
3. H2、Mockito 与影响行数断言不替代目标 MySQL 并发、生产存量尝试、真实渠道、到账、对账或正式发布证据。

## 522. 2026-09-13 渠道提交与快速回调并发收口 V1.0 验证

### 522.1 本轮补强

1. 渠道提交结果只在支付单仍为 `WAIT_CALLBACK`、渠道一致且流水为空或相同时条件绑定，不再无条件重写支付状态。
2. 已完成尝试允许补写渠道响应原文，但通过 `completed_at` 保留“成功 / success”和完成时间；尝试结果更新必须恰好命中一行。
3. 未知结果恢复使用独立条件迁移，成功、关闭和冲突流水均不降级；非成功回调同样禁止覆盖并发绑定的另一流水。

### 522.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 快速成功回调 | 提交结果条件绑定返回零行后重读成功事实，只补尝试响应，不写提交快照、待回调通知或 `PAYMENT_SUBMIT` 事件 |
| 恢复终态保护 | 正常提交事务失败且并发回调已成功时，未知恢复不更新预付单为“结果待确认”，支付单保持成功 |
| 流水竞争 | 两个非成功回调使用不同流水时第二次更新返回零行，数据库保留首个流水，服务返回 `PAYMENT-1031` |
| Mapper 正向语义 | 待回调支付单可绑定同渠道流水且状态不变；空流水恢复保持流水为空并写 `danger` |
| 尝试终态保护 | 已完成尝试补写 `response_payload` 后仍为“成功 / success”，`completed_at` 保持非空 |
| 定向测试 | `PaymentServiceImplTest + PaymentMapperStateTransitionIntegrationTest` 合计 `108/108` 通过 |
| payment-core 后端全量 | `1099/1099` 通过，`0` failures、`0` errors、`0` skipped |
| 静态与提交 | Mapper XML、`git diff --check` 通过；实现提交 `a5697970` |
| 统一门禁复判 | 脚本退出码 `2`；`canPromoteTestToMaster=true`、`canCreateRelease=false`、统一 `blockerCount=1`、内部 `releaseBlockerCount=4` |

### 522.3 当前判断

1. 提交结果、快速回调和异常恢复不再通过无条件更新相互覆盖支付成功与渠道流水事实。
2. 本轮不改变 HTTP、前端或数据库结构，未重复运行管理端测试、构建和视觉检查。
3. 本地 SQL 与模拟竞态不替代目标 MySQL 并发实测、真实渠道订单归属、资金到账、财务对账或正式发布证据。

## 523. 2026-09-13 四端支付入口与渠道策略对齐 V1.0 验证

### 523.1 本轮补强

1. App/H5 收银台仅展示微信和支付宝，PC 收银台仅展示支付宝和银行卡；终端默认选择过滤后的第一个可用支付方式。
2. 前端提交商户号按支付方式统一映射为 `MCH_WX_10001`、`MCH_ALI_20001`、`BANK_OFFLINE_30001`，与启用中的控制策略和渠道配置一致。
3. 收银台与结果页的提交、刷新、主动查单、关单和幂等键重置统一使用动作锁，避免用户连续点击触发互相竞争的请求。
4. demo 数据补充支付宝 `WAIT_BUYER_PAY -> PAYMENT-CHANNEL-2001` 映射，原成功重复码顺延为 `PAYMENT-CHANNEL-2002`；PC demo 自检基线调整为可提交的 `PASS`。

### 523.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 首次真实失败 | App 使用旧商户号提交返回 `PAYMENT-1020 当前来源应用未开通该商户号`，证明页面常量与运行策略不一致 |
| App 修复后提交 | 微信支付进入 `WAIT_CALLBACK`；预付单 `PRE1789298318129bd6e7753`，支付单 `PAY17892983181297ae92034`，渠道 `wx_h5`，商户号 `MCH_WX_10001` |
| PC 修复后提交 | 页面默认选择支付宝并进入 `WAIT_CALLBACK`；预付单 `PRE17892985320488c9b6cb3`，支付单 `PAY1789298532048358741ec`，渠道 `alipay_h5`，商户号 `MCH_ALI_20001` |
| PC 重启后复验 | 预付单 `PRE1789299187042ef9d8a5c`、支付单 `PAY17892991870419dac836b` 进入 `WAIT_CALLBACK`；`WAIT_BUYER_PAY` 命中 `PAYMENT-CHANNEL-2001`，`returnCodeMappingMatched=true`、可重试“是”、人工介入“否” |
| 前端自动化 | App 收银台常量与状态测试 `14/14` 通过；App、H5、PC 生产构建通过 |
| 后端自动化 | demo 集成测试覆盖 PC + 支付宝提交和返回码映射；payment-core 后端全量 `1100/1100` 通过 |
| 静态检查 | `git diff --check` 通过；两份 demo 初始化数据的渠道返回码与终端控制策略保持一致 |
| 统一门禁复判 | 脚本退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、统一 `blockerCount=1`、内部 `releaseBlockerCount=4` |

### 523.3 当前判断

1. 四端支付入口已按各自终端能力提交到启用渠道，首次真实失败的原因和修复后的支付单均已留痕。
2. PC 首次修复后验证使用的是重启前初始化数据，当时未命中 `WAIT_BUYER_PAY` 映射；payment-core 使用新初始化数据重启后已完成复验并命中标准码。
3. 本地 demo 渠道响应、H2 初始化数据与页面动作锁不证明真实渠道扣款、生产商户授权、到账、对账、目标 MySQL 并发或正式发布条件。

## 524. 2026-09-13 支付日终页面操作状态与空态 V1.0 验证

### 524.1 本轮补强

1. 刷新、导出和手工跑批共享动作锁，跑批期间同步锁定日期、运行方式和触发人输入，函数入口保留二次防重。
2. 仅在已加载且存在日终批次时开放 CSV 导出；手工跑批展示处理中状态。
3. 业务校验及接口失败改用错误提示，成功反馈独立展示；风险告警和最近批次补齐明确空态。

### 524.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 管理端自动化 | Node 测试 `37/37` 通过，零失败、零跳过 |
| 管理端构建 | Vite 转换 `85` 个模块并构建通过；保留既有大包提示 |
| 真实页面 | `http://127.0.0.1:5174/payment-day-end` 正常展示核心指标、对账准入、风险告警、手工表单和两条最近批次，导出与刷新可用 |
| 操作边界 | 未点击手工跑批，不伪造新日终批次或财务操作事实 |
| 静态检查 | `git diff --check` 通过 |
| 统一门禁复判 | 脚本退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、统一 `blockerCount=1`、内部 `releaseBlockerCount=4` |

### 524.3 当前判断

1. 财务用户可以区分加载失败、无告警、无批次、跑批失败与跑批成功，不再面对无解释空白区域或错误颜色语义。
2. 前端动作锁只防止当前页面重复操作；服务端既有任务互斥、幂等和事实校验仍是最终业务边界。
3. 本地页面和 demo 日终数据不证明生产日切、渠道账单、内部账务、资金一致、正式关账或 release 放行条件。

## 525. 2026-09-13 订单中心查询、导出与支付发起状态 V1.0 验证

### 525.1 本轮补强

1. 订单加载、刷新、查询、重置、翻页和支付发起共享动作锁，筛选控件在请求期间同步禁用，所有动作入口保留防重判断。
2. CSV 导出仅在列表已加载、结果非空且快照有效时开放；快照过期后需先恢复查询。
3. 发起支付前重新核对当前页订单与待支付资格，新动作开始时清空上一笔预付结果，避免失败后误开旧收银台链接。

### 525.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 管理端自动化 | Node 测试 `37/37` 通过，零失败、零跳过 |
| 管理端构建 | Vite 转换 `85` 个模块并构建通过；保留既有大包提示 |
| 初始加载态 | 真实页面显示“刷新中 / 查询中”，刷新、导出、筛选、查询和重置均禁用，列表区域显示加载中 |
| 加载完成态 | 三条 demo 订单正常展示；导出、筛选和查询恢复可用，待支付订单显示“发起支付”，非待支付订单显示禁用的“无需发起” |
| 操作边界 | 未点击发起支付，不新增预付单或支付单事实 |
| 静态检查 | `git diff --check` 通过 |
| 统一门禁复判 | 脚本退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、统一 `blockerCount=1`、内部 `releaseBlockerCount=4` |

### 525.3 当前判断

1. 订单中心不再允许查询条件、导出参数和支付发起动作在同一在途窗口相互错位，旧支付结果也不会污染下一次失败动作。
2. 前端仅提供交互防重和资格快照校验，服务端仍负责订单状态、剩余应付、有效预付单及并发一致性的最终裁决。
3. demo 订单页面不证明真实订单、履约、渠道扣款、导出权限、资金一致或 release 放行条件。

## 526. 2026-09-13 账单中心成功快照与查询状态 V1.0 验证

### 526.1 本轮补强

1. 列表与汇总共享查询快照，并通过请求序号阻止旧请求覆盖新请求。
2. 保存最近成功查询和页码；翻页失败恢复原页，导出只使用最近成功查询而非未提交表单。
3. 初次加载、空结果、加载中和过期快照禁用导出，筛选与翻页在请求期间锁定；初次成功前不展示默认零值指标。
4. 修复风险卡 `overview-card.warn / overview-card.info` 错误类名为两个有效 class。

### 526.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 管理端自动化 | Node 测试 `37/37` 通过，零失败、零跳过 |
| 管理端构建 | Vite 转换 `85` 个模块并构建通过；保留既有大包提示 |
| 初始加载态 | 真实页面仅显示加载提示；刷新、导出、筛选、查询和重置均处于禁用状态，未展示默认零值汇总 |
| 加载完成态 | 4 笔账单及金额汇总、三张风险卡、账单列表和首条账单快照正常展示，刷新、导出与筛选恢复可用 |
| 操作边界 | 未点击导出或业务动作，不生成下载或资金事实 |
| 静态检查 | `git diff --check` 通过 |
| 统一门禁复判 | 脚本退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、统一 `blockerCount=1`、内部 `releaseBlockerCount=4` |

### 526.3 当前判断

1. 账单页面的筛选、汇总、列表、页码和导出现在基于同一成功快照，不再把未提交条件或失败页码混入当前事实。
2. 前端快照治理不等于账务事实校验；账单应收、已收、待收仍以服务端数据和后续账务/对账系统为最终依据。
3. demo 账单及浏览器页面不证明生产应收、渠道到账、总分平衡、导出权限、正式关账或 release 放行条件。

## 527. 2026-09-13 统一支付记录导出与查询快照 V1.0 验证

### 527.1 本轮补强

1. 新增支付记录 CSV 导出端点，筛选、排序、成功退款聚合和脱敏口径与列表一致，导出不受当前分页限制。
2. 同步导出最多 `1000` 条，Mapper 多取一行探测超限；CSV 使用 UTF-8 BOM、完整单元格转义和公式注入防护。
3. 页面补齐最近成功查询、成功页码、请求序号、刷新、导出、首次加载、空结果和失败快照状态；加载期间统一锁定筛选和翻页动作。

### 527.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 后端导出定向测试 | `PaymentRecordServiceImplTest + PaymentRecordServiceListTest + PaymentRecordControllerTest + PaymentRecordListMapperIntegrationTest` 通过，覆盖脱敏、BOM、转义、公式防护、超限、下载头及无分页导出 |
| 后端全量回归 | `1104/1104` 通过，`0` failures、`0` errors、`0` skipped |
| 管理端自动化 | Node 测试 `37/37` 通过，零失败、零跳过 |
| 管理端构建 | Vite 转换 `85` 个模块并构建通过；保留既有大包提示 |
| 真实接口 | `GET /api/payment-records/export` 返回 HTTP `200`、`Content-Disposition: attachment; filename=payment-records.csv`、UTF-8 BOM 和 `1901` 字节 CSV；3 条 demo 行的用户标识已脱敏 |
| 真实页面 | `http://127.0.0.1:5174/payment-records` 完成态展示 3 条记录、4 项指标、筛选和首条快照；刷新、导出恢复可用，加载态所有查询控件与动作均禁用 |
| 持续运行 | 原窗口新增前台托管会话 `34148`，payment-core 后端 PID `47639`；管理端、App、H5、PC 及代理冒烟均为 `READY` |
| 静态检查 | `git diff --check` 通过 |
| 统一门禁复判 | 脚本退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、统一 `blockerCount=1`、内部 `releaseBlockerCount=4` |

### 527.3 当前判断

1. 统一支付记录已形成从列表查询到完整 CSV 下载的可操作闭环，页面不会把未提交条件、失败页码或过期快照用于导出。
2. 本地同步导出只适合当前测试和运营排障规模；大数据异步任务、生产权限、审批、水印、下载审计与文件留存仍需后续治理。
3. demo 数据、H2/MyBatis 测试和本机页面不证明真实渠道交易、生产资金事实、生产稳定性或正式发布条件。

## 528. 2026-09-13 支付流水成功快照与操作状态 V1.0 验证

### 528.1 本轮补强

1. 页面保存最近成功查询和页码，以请求序号避免旧请求覆盖路由变化后的新结果；翻页失败恢复原页。
2. 导出固定使用最近成功查询快照；首次加载、空结果、加载中和刷新失败时禁用，未提交筛选不进入导出参数。
3. 刷新、筛选、查询、重置和翻页统一动作锁，首次未加载不展示伪零值指标；首次失败、刷新失败和真实空结果使用不同提示。

### 528.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 管理端自动化 | Node 测试 `37/37` 通过，零失败、零跳过 |
| 管理端构建 | Vite 转换 `85` 个模块并构建通过；保留既有大包提示 |
| 初始加载态 | 真实页面显示“刷新中 / 查询中”和支付流水加载提示；导出、筛选、查询及重置均禁用，未展示默认零值指标 |
| 加载完成态 | 真实页面展示 `12` 条流水、成功 `6` 条、待关注 `2` 条、`4` 类流水，列表与首条链路快照正常显示，刷新和导出恢复可用 |
| 静态检查 | `git diff --check` 通过 |
| 统一门禁 | 沿用同批次实测结论：`canPromoteTestToMaster=true`、`canCreateRelease=false`、统一 `blockerCount=1`、内部 `releaseBlockerCount=4` |

### 528.3 当前判断

1. 支付流水查询、展示、翻页和导出不再混用失败页码或未提交筛选，页面能区分未加载、真实空结果和过期快照。
2. 本轮前端治理不改变后端流水事实；跨请求数据库一致性、生产权限、下载审计和大数据异步导出仍需后续验证。
3. demo 流水与本机页面不证明真实渠道回调、事件投递、资金到账、生产稳定性或正式发布条件。

## 529. 2026-09-13 支付请求与处理日志成功快照 V1.0 验证

### 529.1 本轮补强

1. 支付请求汇总和列表改为并发查询并原子落页；处理日志汇总与列表使用同一成功查询对象，两页均以请求序号拒绝过期响应。
2. 保存最近成功查询和页码，翻页失败恢复原页；导出不再读取未提交表单，只在成功非空且未过期的快照上开放。
3. 刷新、筛选、查询、重置和翻页统一动作锁；首次未加载不展示默认零值汇总，首次失败、刷新失败和真实空结果使用独立提示。

### 529.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 管理端自动化 | Node 测试 `37/37` 通过，零失败、零跳过 |
| 管理端构建 | Vite 转换 `85` 个模块并构建通过；保留既有大包提示 |
| 支付请求加载态 | 刷新、导出、筛选、查询和重置均禁用，只显示加载提示，未展示默认零值汇总 |
| 支付请求完成态 | 真实页面展示 `3` 条请求、成功 `2` 条、等待回调 `1` 条、涉及终端和渠道各 `3` 个；列表、汇总、详情及脱敏字段一致，刷新和导出恢复可用 |
| 处理日志完成态 | 真实页面展示 `12` 条日志、WARN `2` 条、INFO `10` 条、`4` 个阶段和 `4` 个来源；风险总览、列表和首条详情一致，刷新和导出可用 |
| 静态检查 | `git diff --check` 通过 |
| 统一门禁复判 | 脚本退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、统一 `blockerCount=1`、内部 `releaseBlockerCount=4` |

### 529.3 当前判断

1. 支付请求和处理日志页面不再出现汇总与列表半更新，也不会把失败页码、过期响应或未提交筛选用于导出。
2. 前端成功快照只保证当前页面的一致展示，不提供数据库事务级快照，也不替代生产检索、权限、水印、下载审计或大数据异步导出。
3. demo 请求、日志和本机页面不证明真实渠道调用、回调送达、资金到账、生产稳定性或正式发布条件。

## 530. 2026-09-13 收银会话与服务者结算成功快照 V1.0 验证

### 530.1 本轮补强

1. 收银会话和服务者结算的汇总、列表使用同一查询对象并原子落页，两页均以请求序号拒绝过期响应。
2. 保存最近成功查询和页码，翻页失败恢复原页；导出只读取成功非空且未过期的查询快照，不读取未提交表单。
3. 首次未加载不展示默认零值汇总，首次失败、刷新失败和真实空结果使用独立提示；刷新、筛选、查询、重置和翻页统一动作锁。
4. 收银会话页安全读取字符串路由参数并监听路由条件变化，数组等畸形查询值不进入后端请求。

### 530.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 管理端自动化 | Node 测试 `37/37` 通过，零失败、零跳过 |
| 管理端构建 | Vite 转换 `85` 个模块并构建通过；保留既有大包提示 |
| 收银会话完成态 | 真实页面展示 `3` 条会话、失效 `2` 条、完成 `1` 条、涉及终端 `2` 个、金额合计 `¥10,668.00`；汇总、列表和首条详情一致 |
| 服务者结算完成态 | 真实页面展示 `3` 条结算，待审核、待出款、出款中和出款成功各 `1` 条，实结金额合计 `¥16,820.00`；汇总、列表和首条快照一致 |
| 页面布局 | `1280 x 720` 桌面视口下标题、动作区、指标、风险总览和筛选区无覆盖，页面可正常滚动 |
| 静态检查 | `git diff --check` 通过 |
| 统一门禁复判 | 脚本退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、统一 `blockerCount=1`、内部 `releaseBlockerCount=4` |

### 530.3 当前判断

1. 收银会话和服务者结算页面不会再混用失败页码、过期响应或未提交筛选驱动翻页与导出，并能区分未加载、空结果和失败刷新。
2. 前端成功快照只保证当前页面的一致展示，不提供数据库事务级快照，也不替代生产权限、水印、下载审计或大数据异步导出。
3. demo 会话、结算和本机页面不证明真实渠道支付、资金结算、服务者出款、生产稳定性或正式发布条件。

## 531. 2026-09-13 管理端工作台成功快照 V1.0 验证

### 531.1 本轮补强

1. 删除首次加载失败时展示的预置零值概览卡，只有工作台概览和支付指标同时成功后才展示业务区域。
2. 以请求序号拒绝过期响应；刷新期间禁用刷新按钮，已有快照时刷新失败保留上一份完整成功数据并明确提示。
3. 首次加载、首次失败、成功数据和成功后的刷新失败具备独立页面语义，快捷入口不依赖业务数据请求。

### 531.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 管理端自动化 | Node 测试 `37/37` 通过，零失败、零跳过 |
| 管理端构建 | Vite 转换 `85` 个模块并构建通过；保留既有大包提示 |
| 工作台完成态 | 本机页面展示 `4` 张概览卡、支付成功率 `66.00%`、成功金额 `¥3,868.00`、待处理支付 `1` 笔、已关闭支付 `0` 笔 |
| 工作台操作 | 重点事项表、健康度与 `4` 个快捷入口正常显示，刷新完成后动作恢复可用 |
| 页面布局 | `1280 x 720` 桌面视口下标题、刷新动作、风险提示、概览卡及双栏面板无覆盖，页面可正常滚动 |
| 静态检查 | `git diff --check` 通过 |
| 统一门禁口径 | 沿用本轮实测：`canPromoteTestToMaster=true`、`canCreateRelease=false`、统一 `blockerCount=1`、内部 `releaseBlockerCount=4` |

### 531.3 当前判断

1. 工作台不会再把预置零值当作接口事实，也不会在两组接口只成功一组时形成半更新。
2. 页面成功快照只保证当前管理端展示一致，不等于跨请求数据库事务快照或生产监控平台数据。
3. 本机 demo 指标不证明真实经营数据、渠道交易、资金到账、生产稳定性或正式发布条件。

## 532. 2026-09-13 App/H5/PC 业务入口共享骨架 V1.0 验证

### 532.1 本轮补强

1. H5/PC 的业务入口改为薄包装，统一复用 app-web 的充值、提现补款、转账补款和余额补款实现，避免三份业务状态机继续漂移。
2. 保留 `terminalVariant / sceneType` 入参，三端仍分别生成来源应用、支付场景、业务单号前缀和收银台路由。
3. 在 `640px` 以下将区块标题和支持标签改为上下排列，避免窄屏标签逐字换行。

### 532.2 验证结果

| 项目 | 结果 |
| --- | --- |
| App 自动化 | Node 测试 `14/14` 通过，零失败、零跳过 |
| App 构建 | Vite 转换 `35` 个模块并构建通过 |
| H5 构建 | Vite 转换 `39` 个模块并构建通过 |
| PC 构建 | Vite 转换 `40` 个模块并构建通过 |
| App 页面 | `/balance-pay` 显示 `housekeeping-app-web / BALANCE_PAY_APP`，四类业务入口、表单和动作完整 |
| H5 页面 | `/transfer` 显示 `housekeeping-h5-web / ACCOUNT_TRANSFER_H5`，`390 x 844` 视口下标签、标题与表单无覆盖 |
| PC 页面 | `/recharge` 显示 `housekeeping-pc-web / WALLET_RECHARGE_PC`，四类业务入口、表单和动作完整 |
| 静态检查 | `git diff --check` 通过 |
| 统一门禁复判 | 脚本退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、统一 `blockerCount=1`、内部 `releaseBlockerCount=4` |

### 532.3 当前判断

1. App/H5/PC 的基础业务入口现在共用同一权威状态机，同时保留终端差异，不会再因复制修改造成入口能力漂移。
2. 四类入口仍创建虚拟业务预付单并进入统一收银台，不代表钱包、提现、转账等下游系统的真实业务状态机或资金闭环已经交付。
3. 小程序端仍未实现；本机页面、demo 数据和生产构建不证明真实渠道交易、生产稳定性或正式发布条件。

## 533. 2026-09-13 支付监控有效快照与页面溢出 V1.0 验证

### 533.1 本轮补强

1. 监控加载以请求号拒绝过期响应，刷新期间锁定刷新动作；刷新失败保留上次成功快照供阅读。
2. 告警、渠道和配置下钻只在成功、非加载、未过期的监控快照上开放。
3. 趋势和告警表格增加局部滚动容器，防止双栏表格撑开整页。

### 533.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 管理端自动化 | Node 测试 `37/37` 通过，零失败、零跳过 |
| 管理端构建 | Vite 转换 `85` 个模块并构建通过；保留既有大包提示 |
| 监控完成态 | 真实页面展示支付总量 `3`、成功 `2`、成功率 `66.00%`、待收口 `1`、已关闭 `0`、P1 告警 `3` |
| 渠道快照 | 展示 `offline_bank / wx_jsapi / alipay_h5` 三组渠道指标，告警与渠道下钻在有效快照上可用 |
| 页面宽度 | 浏览器实测 `documentElement.scrollWidth=1265`、`clientWidth=1265`，整页无横向溢出 |
| 静态检查 | `git diff --check` 通过 |
| 统一门禁复判 | 脚本退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、统一 `blockerCount=1`、内部 `releaseBlockerCount=4` |

### 533.3 当前判断

1. 监控页不会再由过期响应覆盖新数据，也不会基于加载中或刷新失败后的旧快照继续跨页处置。
2. 表格宽度压力限制在各自面板内，不再带动侧栏和整个管理端横向滚动。
3. 本机 demo 指标和页面快照不证明生产监控实时性、真实告警处置、资金一致性或正式发布条件。

## 534. 2026-09-13 死信补偿任务有效快照与动作结果 V1.0 验证

### 534.1 本轮补强

1. 列表查询新增成功查询、成功页码和递增请求号，旧响应不能覆盖新结果，翻页失败恢复最近成功页码。
2. 加载与处置期间锁定筛选、处置输入和所有动作；函数入口复核快照有效性、任务仍在当前列表且当前状态允许目标动作。
3. 写操作成功与后续列表刷新分开表达；刷新失败时保留处置成功事实并冻结旧快照，要求刷新成功后才能继续操作。

### 534.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 管理端自动化 | Node 测试 `37/37` 通过，零失败、零跳过 |
| 管理端构建 | Vite 转换 `85` 个模块并构建通过；保留既有大包提示 |
| 真实页面 | `http://127.0.0.1:5174/payment-dead-letter-tasks` 正常加载并完成一次真实刷新，展示 0 条任务的独立空态、筛选和处置说明，无错误提示 |
| 操作边界 | 当前无任务样本，未执行允许重放、人工结案或定向重放，不制造处置或消息发布事实 |
| 页面宽度 | 浏览器实测 `documentElement.scrollWidth=1265`、`clientWidth=1265`，整页无横向溢出 |
| 静态检查 | `git diff --check` 通过 |
| 统一门禁复判 | 脚本退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、统一 `blockerCount=1`、内部 `releaseBlockerCount=4` |

### 534.3 当前判断

1. 死信补偿页不再由过期响应、失败页码或陈旧任务驱动写操作，并能区分服务端动作失败与动作成功后的列表刷新失败。
2. 前端动作锁只是操作保护，服务端状态机、行锁、发布确认和消费者幂等仍是最终裁决边界。
3. 本机空数据页不证明真实 RabbitMQ 重放、下游消费、业务收口、资金事实、生产权限或正式发布条件。

## 535. 2026-09-13 支付日终过期快照动作 V1.0 验证

### 535.1 本轮补强

1. 日终 CSV 导出只允许基于非加载、未过期且包含批次的成功快照执行。
2. 支付、退款事实和风险告警下钻统一增加有效快照门禁及函数入口复核，刷新失败后旧事实仅供阅读。
3. 手工跑批函数入口增加成功快照校验，与模板禁用状态共同阻止首次未加载或刷新失败后的动作。

### 535.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 管理端自动化 | Node 测试 `37/37` 通过，零失败、零跳过 |
| 管理端构建 | Vite 转换 `85` 个模块并构建通过；保留既有大包提示 |
| 真实页面 | `http://127.0.0.1:5174/payment-day-end` 正常展示最近 `2` 个批次、对账准入、风险事实、告警、表单和批次表 |
| 有效快照动作 | 导出、风险下钻和手工跑批入口正常开放；本轮未点击，不产生下载、跳转或新跑批事实 |
| 页面宽度 | 浏览器实测 `documentElement.scrollWidth=1265`、`clientWidth=1265`，整页无横向溢出 |
| 静态检查 | `git diff --check` 通过 |
| 统一门禁复判 | 脚本退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、统一 `blockerCount=1`、内部 `releaseBlockerCount=4` |

### 535.3 当前判断

1. 日终页刷新失败后的旧快照不再驱动导出、跨页排障或手工跑批，阅读态与操作态已经分开。
2. 前端门禁不替代服务端跑批幂等、任务互斥、数据水位校验、财务权限和审计。
3. 本机 demo 批次不证明渠道账单、账务核对、资金一致、正式关账、生产稳定性或 release 放行条件。

## 536. 2026-09-13 配置治理快照导出门禁 V1.0 验证

### 536.1 本轮补强

1. 配置治理快照导出增加统一资格：总览必须成功加载，且当前不能处于加载、刷新失败或配置变更执行状态。
2. 顶部全量导出按钮绑定资格，导出函数入口再次校验；分区导出继续受既有配置字段集整体禁用约束。

### 536.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 管理端自动化 | Node 测试 `37/37` 通过，零失败、零跳过 |
| 管理端构建 | Vite 转换 `85` 个模块并构建通过；保留既有大包提示 |
| 真实页面 | `http://127.0.0.1:5174/payment-config` 正常展示治理指标、配置内容和 `9` 个导出入口，无错误提示 |
| 有效快照动作 | 顶部“导出治理快照”在成功快照下可用；本轮未触发下载或任何配置变更动作 |
| 页面宽度 | 浏览器实测 `documentElement.scrollWidth=1265`、`clientWidth=1265`，整页无横向溢出 |
| 静态检查 | `git diff --check` 通过 |
| 统一门禁复判 | 脚本退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、统一 `blockerCount=1`、内部 `releaseBlockerCount=4` |

### 536.3 当前判断

1. 配置中心不再允许首次未加载、刷新失败、加载中或配置变更执行中的快照驱动治理导出。
2. 前端导出门禁不替代服务端授权、数据权限、下载审计、水印、文件留存或敏感字段治理。
3. 本机配置和页面不证明生产配置已核准、真实凭据已托管、可信身份已接入、生产稳定性或 release 放行条件。

## 537. 2026-09-13 支付事件成功快照与手动重发门禁 V1.0 验证

### 537.1 本轮补强

1. 汇总与列表共享同一查询对象，保存成功查询和页码，并以递增请求号拒绝过期响应。
2. 导出和翻页只使用最近成功查询；加载或重发期间锁定筛选、操作人和所有查询动作。
3. 手动重发入口复核快照、事件存在性和状态资格；支付单、日志、流水和死信任务联查统一增加有效快照门禁。

### 537.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 管理端自动化 | Node 测试 `37/37` 通过，零失败、零跳过 |
| 管理端构建 | Vite 转换 `85` 个模块并构建通过；保留既有大包提示 |
| 真实页面 | `http://127.0.0.1:5174/payment-events` 正常展示 `3` 条事件、风险总览、列表和首条事件快照，无错误提示 |
| 有效快照动作 | 导出和 `5` 个联查入口可用，`1` 条事件显示可用重发动作；本轮均未点击，不产生下载、跳转或重发事实 |
| 页面宽度 | 浏览器实测 `documentElement.scrollWidth=1265`、`clientWidth=1265`，整页无横向溢出 |
| 静态检查 | `git diff --check` 通过 |
| 统一门禁复判 | 脚本退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、统一 `blockerCount=1`、内部 `releaseBlockerCount=4` |

### 537.3 当前判断

1. 事件页不再让未提交筛选、失败页码、过期响应或陈旧事件驱动导出、手动重发和跨页联查。
2. 前端快照与动作锁不替代服务端事件行锁、重试状态机、消息发布确认、消费者幂等和权限审计。
3. 本机 demo 事件不证明真实消息送达、下游消费、账务入账、资金一致、生产稳定性或 release 放行条件。

## 538. 2026-09-13 支付异常中心可运行骨架恢复 V1.0 验证

### 538.1 本轮补强

1. `PaymentIssueMapper.findAll` 的列表字段、筛选字段及排序字段显式限定为 `payment_issue_union`，修复连接动作、告警和通道统计后 `issue_no` 等列在 H2 中歧义的问题。
2. `PaymentDemoProfileIntegrationTest` 新增 `PaymentIssueService.list` 真实查询断言，把异常中心主列表纳入 demo 应用可运行门禁。
3. `PaymentIssuesView` 以同一查询并发读取列表与责任组统计，只有两者同时成功且请求仍为最新时才落页；翻页、批量写入及三个联查入口只使用有效成功快照。

### 538.2 验证结果

| 项目 | 结果 |
| --- | --- |
| Mapper XML | `xmllint --noout` 通过，`git diff --check` 通过 |
| demo 集成测试 | `PaymentDemoProfileIntegrationTest` 通过，异常列表可在完整 Spring demo 上下文中真实执行 |
| 后端定向测试 | `PaymentIssueServiceImplTest,PaymentIssueControllerTest,PaymentOverpaidProfileIntegrationTest` 全部通过 |
| 后端全量测试 | `1104/1104` 通过，`0` failures、`0` errors、`0` skipped |
| 管理端自动化 | Node 测试 `37/37` 通过，零失败、零跳过 |
| 管理端构建 | Vite 转换 `85` 个模块并构建通过；保留既有大包提示 |
| 真实接口 | `GET /api/payment-issues` 返回 `code=0` 和 `2` 条异常；责任组统计返回支付后端值班组 `2` 条、SLA 超时 `2` 条、P1 `2` 条 |
| 真实页面 | `http://127.0.0.1:5174/payment-issues` 正常展示 2 条异常、风险总览、责任组统计、批量表单和联查入口，无错误提示 |
| 页面宽度 | 无头 Chromium 实测 `documentElement.scrollWidth=1440`、`clientWidth=1440`，整页无横向溢出 |
| 持续运行 | 当前窗口托管会话 `61700` 持续运行；后端 PID `58243`，管理端、App、H5、PC 及代理均为 `READY` |
| 操作边界 | 本轮未勾选异常、未提交批量动作、未确认告警，不制造运营处置事实 |
| 统一门禁复判 | 脚本退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`blockerCount=1`、`releaseBlockerCount=4` |

### 538.3 当前判断

1. 支付异常中心已从“页面可进入但主列表真实查询报 `PAYMENT-500`”恢复为可查询、可筛选、可联查且具备批量处理入口的本地可运行骨架。
2. 前端成功快照和动作锁不替代服务端事务、状态资格复验、可信身份、权限与审计；本轮只验证动作入口可用，没有执行运营处置。
3. 本机 demo 数据、自动化测试和页面渲染不证明真实渠道、生产并发、资金一致性、稳定观察或 release 放行条件。

## 539. 2026-09-13 异常告警明细成功快照与人工动作 V1.0 验证

### 539.1 本轮补强

1. `PaymentIssueAlertsView` 保存最近成功查询和页码，以递增请求号隔离过期响应；列表、选中告警、翻页和详情均由同一成功快照驱动。
2. CSV 导出改为使用成功查询，未提交筛选不再改变下载参数；加载、空结果、过期快照或人工动作期间导出失败关闭。
3. 确认回执和单条人工回查复核当前告警与状态资格，写成功后再独立刷新原查询；刷新失败保留动作成功或有限失败摘要，并通过独立错误状态冻结旧快照。
4. 支付单、异常中心和处理日志入口改为带函数守卫的按钮；筛选、操作人和所有读写动作在加载或人工动作期间统一互斥。

### 539.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 管理端自动化 | Node 测试 `37/37` 通过，零失败、零跳过 |
| 管理端构建 | Vite 转换 `85` 个模块并构建通过；保留既有大包提示 |
| 真实页面 | `http://127.0.0.1:5174/payment-issue-alerts` 展示 `4` 条告警、四张指标卡、告警快照、处理建议和三个联查入口，无错误提示 |
| 导出查询绑定 | 成功加载后将告警编号输入改为 `UNSUBMITTED-ALERT`，捕获的导出 URL 仍为成功查询中的空告警编号，未读取未提交筛选 |
| 响应式布局 | 无头 Chromium 实测桌面 `scrollWidth/clientWidth=1440/1440`，移动端 `390/390`，整页无横向溢出 |
| 写后刷新失败 | 浏览器拦截确认 POST 并返回成功，再模拟列表 `503`；页面同时保留“已确认回执”和独立刷新失败提示，不出现“确认回执失败” |
| 旧快照动作 | 上述模拟失败后 4 个确认按钮和支付单、异常中心、处理日志三个联查按钮全部禁用 |
| 真实写操作边界 | 模拟 POST 由浏览器拦截，未触达真实后端；本轮未执行真实确认、回查或下载 |
| 静态检查 | `git diff --check` 通过 |
| 统一门禁复判 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`blockerCount=1`、`releaseBlockerCount=4` |

### 539.3 当前判断

1. 异常告警明细台不再由未提交筛选、失败页码、过期响应或陈旧告警驱动导出、人工动作和跨页联查，写操作结果与后续读刷新也已分开表达。
2. 页面动作资格不证明通知已送达或回执真实存在；真实人工确认和供应商回查仍需可信操作人、权限、审计及现场业务证据。
3. 本机 demo 告警和网络拦截模拟不证明生产供应商、外呼稳定性、并发安全、正式审批或 release 放行条件。

## 540. 2026-09-14 支付任务中心查询快照与人工任务 V1.0 验证

### 540.1 本轮补强

1. `PaymentTaskCenterView` 为总览和任务日志增加独立请求号；首次两项查询继续原子提交，后续日志查询保存成功条件和页码并拒绝过期响应。
2. 日志翻页与 CSV 导出只使用成功查询，未提交筛选不会驱动下载；日志读取失败后旧列表只供查看，导出和推荐路由下钻失败关闭。
3. 十类人工任务在函数入口复核有效总览、日志快照和对应候选数量；加载或任一任务执行期间，筛选、操作人、刷新、下钻及同页其他人工任务统一锁定。
4. 任务失败与成功分别使用错误/成功状态；POST 成功后先落服务端返回总览，再刷新原日志查询，日志刷新失败不会把已完成任务误报为执行失败。

### 540.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 管理端自动化 | Node 测试 `37/37` 通过，零失败、零跳过 |
| 管理端构建 | Vite 转换 `85` 个模块并构建通过；保留既有大包提示 |
| 真实页面 | `http://127.0.0.1:5174/payment-task-center` 展示完整总览、关注告警、任务规则、十类人工任务、运维关注点和 `59` 条任务日志 |
| 真实候选资格 | 主动查单 `1`、控制策略巡检 `4`、异常告警派发 `2` 可执行；其余七类无候选按钮禁用，本轮均未点击 |
| 导出查询绑定 | 成功加载后将任务编码改为 `UNSUBMITTED-TASK`，捕获的导出 URL 仍使用成功查询中的空任务编码 |
| 响应式布局 | 无头 Chromium 实测桌面 `scrollWidth/clientWidth=1440/1440`、移动端 `390/390`，整页无横向溢出 |
| 写后日志失败 | 浏览器拦截主动查单 POST 并返回成功，再模拟任务日志 `503`；保留“执行完成”和日志刷新失败提示，不出现“执行失败” |
| 旧日志动作 | 上述模拟失败后日志 CSV 导出及当前页 `10` 个推荐路由下钻全部禁用 |
| 真实写操作边界 | 模拟 POST 被浏览器拦截，未触达真实后端；未执行真实查单、巡检、派发、重发、重试、回查或下载 |
| 静态检查 | `git diff --check` 通过 |
| 统一门禁复判 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`blockerCount=1`、`releaseBlockerCount=4` |

### 540.3 当前判断

1. 支付任务中心不再由未提交筛选、失败页码、过期日志或陈旧总览驱动导出、下钻和人工任务，任务写结果与任务日志读刷新也已拆分。
2. 服务端返回的动作总览仍是后续人工任务资格依据；日志刷新失败只冻结日志相关操作，不伪造任务失败或撤销已返回的任务事实。
3. 本机 demo 调度记录和浏览器拦截模拟不证明真实渠道查单、通知派发、跨系统消费、资金一致性、生产并发、正式审批或 release 放行条件。

## 541. 2026-09-14 支付路由执行结果成功快照 V1.0 验证

### 541.1 本轮补强

1. `PaymentRoutesView` 为汇总与列表增加共享查询对象、最近成功查询、成功页码和递增请求号，避免半快照及旧响应覆盖新结果。
2. 翻页与 CSV 导出只使用最近成功查询，翻页失败恢复成功页码；未提交筛选不会改变导出参数。
3. 报文展开、路由快照选择及支付单、路由流水、支付请求联查统一增加有效快照门禁；加载与刷新失败后不会由旧路由事实驱动后续操作。
4. 筛选条件响应结构化路由参数变化；刷新、查询、重置、筛选、翻页和快照操作在加载期间统一锁定。

### 541.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 管理端自动化 | Node 测试 `37/37` 通过，零失败、零跳过 |
| 管理端构建 | Vite 转换 `85` 个模块并构建通过；保留既有大包提示 |
| 真实页面 | `http://127.0.0.1:5174/payment-routes` 展示 `3` 条路由、成功 `2` 条、需关注 `1` 条、命中渠道 `3` 个及首条路由快照，无错误提示 |
| 导出查询绑定 | 成功加载后将支付单号改为 `UNCOMMITTED_ORDER`，捕获的导出 URL 仍使用成功查询中的空支付单号，未读取未提交筛选 |
| 刷新失败 | 浏览器拦截路由汇总与列表并返回 `503`；页面保留上次成功的 3 行路由并明确标记快照过期 |
| 旧快照动作 | 上述失败后 CSV 导出以及支付单、路由流水、支付请求三个联查按钮全部禁用 |
| 响应式布局 | 无头 Chromium 实测桌面 `scrollWidth/clientWidth=1440/1440`、移动端 `390/390`，整页无横向溢出，宽表保留局部滚动 |
| 真实操作边界 | 模拟失败由浏览器拦截，未触达真实后端；本轮未执行真实下载或跨页操作 |
| 静态检查 | `git diff --check` 通过 |
| 统一门禁复判 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`blockerCount=1`、`releaseBlockerCount=4` |

### 541.3 当前判断

1. 支付路由执行结果不再由未提交筛选、失败页码、过期响应或陈旧路由驱动导出、报文查看和跨页联查。
2. 前端原子快照不替代服务端稳定排序、数据库事务一致性、权限、下载审计和敏感报文治理。
3. 本机 demo 路由与浏览器拦截模拟不证明真实渠道选路、配置正确性、生产并发、资金结果、正式审批或 release 放行条件。

## 542. 2026-09-14 支付单查询快照与人工动作 V1.1 验证

### 542.1 本轮补强

1. `PaymentsView` 保存最近成功查询和成功页码，并以递增请求号拒绝过期响应；刷新、翻页和动作后刷新均复用已提交查询，不读取筛选草稿。
2. CSV 导出、支付单快照、详情、主动查单和人工关单统一增加有效快照门禁；查单、关单函数入口再次复核当前行、状态资格和成功查询。
3. 筛选与操作人输入在加载或人工动作期间统一锁定，页面响应结构化路由参数变化；翻页失败恢复最近成功页码。
4. 窄屏下支付单列表与详情改为单列，侧栏补齐最小宽度和长标识断行，避免详情列挤压表格并撑开整页。

### 542.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 管理端自动化 | Node 测试 `37/37` 通过，零失败、零跳过 |
| 管理端构建 | Vite 转换 `85` 个模块并构建通过；保留既有大包提示 |
| 真实页面 | `http://127.0.0.1:5174/payments` 展示 `3` 条支付单、成功 `2` 条、待回调 `1` 条、本页实付金额 `5868.00` 及首条支付单快照，无错误提示 |
| 动作资格 | 当前 3 条 demo 支付单均可主动查单；均已存在渠道提交事实，因此人工关闭按钮禁用，符合既有资格规则 |
| 成功查询刷新 | 将支付单筛选草稿改为 `UNCOMMITTED_PAYMENT` 后点击刷新，草稿保留但页面继续展示最近成功查询的 3 条结果 |
| 查询与空态 | 随后提交该筛选，页面返回合法空结果、四项指标为零且导出禁用；重置后恢复 3 条真实数据 |
| 响应式布局 | 修复前 `390px` 视口整页宽 `406px`；修复后内容宽 `375px`，桌面内容宽 `1265/1280`，宽表仅在局部容器滚动 |
| 真实写操作边界 | 本轮未点击主动查单、人工关单或导出，不产生渠道查询、关闭状态、下载或资金事实 |
| 静态检查 | `git diff --check` 通过 |
| 统一门禁复判 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`blockerCount=1`、`releaseBlockerCount=4` |

### 542.3 当前判断

1. 支付单页不再由未提交筛选、失败页码、过期响应或陈旧支付单驱动导出、详情和人工动作；动作后刷新也保持原成功查询语义。
2. 当前行与状态资格的前端复核是误操作保护，不替代服务端支付状态机、事务锁、幂等、可信操作人、权限和审计。
3. 本机 demo 支付单和页面验证不证明真实渠道查单、资金结果、生产数据库、并发能力、稳定观察或 release 放行条件。

## 543. 2026-09-14 退款单查询快照与人工动作 V1.1 验证

### 543.1 本轮补强

1. `RefundsView` 保存最近成功查询和成功页码，并以递增请求号拒绝过期响应；刷新、翻页及动作后刷新均复用已提交查询，不读取筛选草稿。
2. CSV 导出、退款快照、详情、申请、审核、主动查单和失败重试统一增加有效快照门禁；三类行级动作函数再次复核当前退款单与状态资格。
3. 申请成功后的列表刷新固定回到成功查询第一页，并继续复用既有申请幂等键机制；筛选、申请表单与操作人在加载或动作期间统一锁定。
4. 新增 `refundAction` 纯函数及边界测试；窄屏下退款列表与详情改为单列，侧栏补齐最小宽度和长标识断行。

### 543.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 管理端自动化 | Node 测试 `38/38` 通过，新增退款动作状态资格用例，零失败、零跳过 |
| 管理端构建 | Vite 转换 `86` 个模块并构建通过；保留既有大包提示 |
| 真实页面 | `http://127.0.0.1:5174/refunds` 展示 `3` 条退款、待审核 `0`、失败 `1`、本页金额 `868.00` 及首条退款快照，无错误提示 |
| 动作资格 | `PROCESSING` 记录仅主动查单可用，`SUCCESS` 记录三类动作均禁用，`FAIL` 记录仅重试可用；当前无 `REVIEWING` 记录 |
| 成功查询刷新 | 将退款单筛选草稿改为 `UNCOMMITTED_REFUND` 后点击刷新，草稿保留但页面继续展示最近成功查询的 3 条结果 |
| 查询与空态 | 随后提交该筛选，页面返回合法空结果、指标为零且导出禁用；发起退款仍可用，重置后恢复 3 条真实数据 |
| 响应式布局 | 桌面内容宽 `1265/1280`；`390px` 移动端内容宽 `375px`，表格 `315/1301`，仅表格容器横向滚动 |
| 真实写操作边界 | 本轮未点击退款申请、审核、主动查单、重试或导出，不产生退款受理、渠道查询、到账、下载或资金事实 |
| 静态检查 | `git diff --check` 通过 |
| 统一门禁复判 | 退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`blockerCount=1`、`releaseBlockerCount=4` |

### 543.3 当前判断

1. 退款单页不再由未提交筛选、失败页码、过期响应或陈旧退款单驱动导出、详情与人工动作；写成功后的读刷新也保持原成功查询语义。
2. 前端动作资格与申请幂等键是误操作和重复提交保护，不替代服务端退款状态机、累计可退校验、事务锁、可信身份、权限与审计。
3. 本机 demo 退款和页面验证不证明真实渠道退款、到账结果、账务一致性、生产并发、稳定观察或 release 放行条件。

## 544. 2026-09-14 支付与退款详情实体快照 V1.1 验证

### 544.1 本轮补强

1. `PaymentDetailView / RefundDetailView` 为详情 GET 增加递增请求号和路由实体身份校验；参数变化时清除旧详情与输入，旧响应不能覆盖新单据。
2. 空详情或响应单号与当前路由不一致时不建立成功快照，页面展示明确加载失败与无可用快照，并冻结全部业务动作和关联下钻。
3. 支付查单/关单与退款审核/查单/重试在函数入口复核有效快照、当前实体及状态资格；动作结果只允许回写发起时仍为当前路由的页面。
4. 支付和退款的关联支付、记录、订单、账单、请求、日志及收银台入口统一绑定有效快照；详情刷新期间同步锁定操作人和备注。
5. 新增共享 `canClosePaymentLocally` 并由支付列表与详情复用，要求未支付本地状态、无渠道交易号且无渠道尝试；自动化覆盖允许和拒绝边界。

### 544.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 管理端自动化 | Node 测试 `39/39` 通过，新增支付本地关单资格用例，零失败、零跳过 |
| 管理端构建 | Vite 转换 `86` 个模块并构建通过；保留既有大包提示 |
| 真实支付详情 | `PAY202607190002` 正常展示 `WAIT_CALLBACK`、金额 `¥2,000.00`、三类轨迹各 1 条和最近尝试；主动查单可用、关单禁用，三个顶部联查和四个详情联查可用 |
| 真实退款详情 | `REF202607190001` 正常展示 `PROCESSING`、金额 `¥68.00`、1 条渠道尝试和 2 条操作日志；仅主动查单可用，三个支付联查入口可用 |
| 支付空响应 | `PAY-NOT-FOUND` 原先无错误且页面空白；修复后显示“响应为空或与当前路由不一致”和无可用快照，业务卡片为 0，三个顶部联查、查单及关单全部禁用 |
| 退款不存在 | `REF-NOT-FOUND` 显示服务端“退款单不存在”错误和无可用快照，业务卡片为 0，三个联查入口全部禁用 |
| 响应式布局 | 支付与退款详情桌面内容宽均为 `1265/1280`；`390px` 移动端内容宽均为 `375px`，无整页横向溢出 |
| 真实写操作边界 | 本轮未点击主动查单、关单、审核、重试或收银台入口，不产生渠道请求、状态变化、外链访问或资金事实 |
| 静态检查 | `git diff --check` 通过 |
| 统一门禁复判 | 2026-09-14 脚本退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`blockerCount=1`、`releaseBlockerCount=4`，未闭环项仍为候选发布说明、正式签字审批、发布后现场核验和 `master` 稳定观察结束结论 |

### 544.3 当前判断

1. 两个详情页不再把空响应、串单响应、旧请求或旧动作结果当成当前单据事实；人工动作与关联下钻只能由当前有效实体快照驱动。
2. 支付本地关单的列表与详情口径已经统一；退款列表与详情继续共用同一动作资格函数，前端不会因页面不同开放相互矛盾的操作。
3. 本机 demo 详情和只读失败验证不证明真实渠道查单、退款到账、账务一致性、生产并发、可信身份、稳定观察或 release 放行条件。

## 545. 2026-09-14 支付记录详情实体快照 V1.1 验证

### 545.1 本轮补强

1. `PaymentRecordDetailView` 为详情 GET 增加递增请求号、路由支付单身份校验和参数变化重载，旧详情及旧查单响应不能覆盖新实体。
2. 空详情或串单响应不建立成功快照；主动查单、支付单详情、支付请求和处理日志入口统一绑定当前有效快照。
3. 查单结果只保留支付状态结论，再独立加载结构不同的支付记录详情；操作人输入在详情加载和动作期间锁定。
4. 支付记录来源类型集中归一化，只接受 `ALL / WECHAT / BANK_CARD`，其余查询参数回退统一记录列表。

### 545.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 管理端自动化 | Node 测试 `40/40` 通过，新增来源类型合法值、非法字符串、数组和空值回退用例，零失败、零跳过 |
| 管理端构建 | Vite 转换 `86` 个模块并构建通过；保留既有大包提示 |
| 真实正常接口 | `GET /api/payment-records/PAY202607190002` 返回 HTTP `200`、业务码 `0` 和单号一致的完整支付中快照 |
| 真实失败接口 | `GET /api/payment-records/PAY-NOT-FOUND` 返回 HTTP `200`、业务码 `PAYMENT-1002` 和“支付单不存在”，请求层进入失败分支 |
| 页面视觉复验 | Mac 当前锁屏，内置浏览器无法操作；本批不登记正常页、空态或响应式页面验证通过，待解锁后补验 |
| 真实写操作边界 | 本轮未执行主动查单、下载或联查跳转，不产生渠道请求、状态变化、下载或资金事实 |
| 静态检查 | `git diff --check` 通过 |
| 统一门禁复判 | 脚本退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`blockerCount=1`、`releaseBlockerCount=4` |

### 545.3 当前判断

1. 支付记录详情已与支付详情、退款详情采用一致的实体快照边界，不再由空响应、串单响应、旧请求或旧动作结果建立操作资格。
2. 来源类型归一化保证返回列表语义稳定，但不改变列表数据范围、后端查询契约或支付状态机。
3. 自动化、构建和真实 API 验证不替代待解锁的页面视觉复验，也不证明真实渠道查单、资金、生产并发、可信身份或 release 放行条件。

## 546. 2026-09-14 订单中心成功查询快照 V1.2 验证

### 546.1 本轮补强

1. 订单列表新增最近成功查询、成功页码和递增请求号，刷新、翻页、导出及支付发起后的刷新统一复用成功条件。
2. 导出不再读取未提交筛选表单；失败翻页恢复成功页码，旧请求响应不能覆盖较新的列表结果。
3. 支付单、支付记录、账单和既有收银台入口统一绑定当前有效订单快照，并在函数入口复核目标订单仍位于当前列表。

### 546.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 管理端自动化 | Node 测试 `40/40` 通过，零失败、零跳过 |
| 管理端构建 | Vite 转换 `86` 个模块并构建通过；保留既有大包提示 |
| 真实列表接口 | 全量查询返回 HTTP `200`、业务码 `0`、3 条订单和一致的 `pageNo=1 / pageSize=20` |
| 真实导出接口 | 筛选 `ORD202607190002` 返回 HTTP `200`、`orders.csv` 下载头、UTF-8 CSV 和唯一目标订单 |
| 页面视觉复验 | Mac 当前锁屏，内置浏览器无法操作；本批不登记筛选草稿、失败快照、行级入口或响应式页面验证通过，待解锁后补验 |
| 真实写操作边界 | 本轮未执行支付发起、页面下载、联查跳转或打开收银台，不产生预付单、渠道请求、页面下载或资金事实 |
| 静态检查 | `git diff --check` 通过 |
| 统一门禁复判 | 脚本退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`blockerCount=1`、`releaseBlockerCount=4` |

### 546.3 当前判断

1. 订单中心展示、翻页、导出和行级操作现共享同一成功查询，不再由筛选草稿、失败页码或过期订单驱动后续动作。
2. 支付发起成功后的收银台结果仍作为独立 POST 事实保留，列表刷新失败不会将成功结果改写为失败。
3. 自动化、构建和真实 API 结果不替代待解锁的页面视觉复验，也不证明真实渠道支付、资金、生产并发、权限或 release 放行条件。

## 547. 2026-09-14 小程序支付后端接入基线 V1.0 验证

### 547.1 本轮补强

1. 支付提交新增 `MINI_PROGRAM` 规范终端及三个兼容别名，未知终端仍失败关闭。
2. 支付渠道场景和路由表达式统一支持小程序终端归一化，既有中文“小程序”范围可被规范终端命中。
3. demo 增加独立小程序来源控制策略和微信支付端到端提交样例。

### 547.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 提交终端边界 | `MINI_PROGRAM / WECHAT_MINI_PROGRAM / WX_MINI_PROGRAM / 小程序` 均归一化为 `MINI_PROGRAM`；未知值既有拒绝用例保持通过 |
| 路由场景 | `MINI_PROGRAM` 成功命中 `H5/APP/小程序` 渠道范围并选择 `wx_h5` |
| 路由表达式 | 小程序上下文可匹配 `terminal=小程序 / MINI_PROGRAM / WX_MINI_PROGRAM` |
| demo 提交闭环 | 独立来源策略下创建预付单并提交微信支付，收银台为“支付中”、支付单为 `WAIT_CALLBACK`、最近终端为 `MINI_PROGRAM` |
| 后端定向测试 | `PaymentServiceImplTest,PaymentChannelRoutingServiceImplTest,PaymentRouteExpressionEvaluatorTest,PaymentDemoProfileIntegrationTest` 通过 |
| 后端全量测试 | `1107/1107` 通过，`0` failures、`0` errors、`0` skipped |
| 静态检查 | `git diff --check` 通过 |
| 运行边界 | 当前长驻 payment-core 进程尚未重启到本批代码；真实接口验证留待小程序前端完成后一并执行 |
| 统一门禁复判 | 脚本退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`blockerCount=1`、`releaseBlockerCount=4` |

### 547.3 当前判断

1. 小程序已经具备可复用支付主链路的后端接入地基，但前端工程、微信登录态和 `wx.requestPayment` 尚未交付，端类型不能改判为已实现。
2. 独立来源策略避免小程序冒用 App/H5 接入身份；示例令牌只适用于本地 demo，不能进入真实环境。
3. 本地 H2、模拟渠道和单进程测试不证明真实微信受理、扣款、回调、资金一致、生产并发或 release 放行条件。

## 548. 2026-09-14 原生微信小程序支付骨架 V1.0 验证

### 548.1 本轮补强

1. 新增原生微信小程序三页工程，覆盖四类业务入口、预付单创建、收银台、支付提交、结果加载、主动查单和受限关单。
2. 支付提交资格绑定微信通道、待支付状态、`PREPAY_CREATED`、空渠道尝试和有效会话；每秒倒计时归零后自动关闭提交入口。
3. 收银台不再根据缺少渠道流水的摘要 DTO 猜测关单资格，关单仅由完整支付详情驱动；预付接口不接收的业务说明字段已删除。

### 548.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 小程序纯函数测试 | Node 测试 `5/5` 通过，零失败、零跳过 |
| 提交状态边界 | 待风控、已发生渠道尝试、非 `PREPAY_CREATED`、无微信通道和已过期会话均禁止提交 |
| 关单状态边界 | 仅完整详情中的本地未支付态、无渠道流水且无渠道尝试可关单；`WAIT_CALLBACK` 禁止关单 |
| 工程静态检查 | 全部 JavaScript 通过 `node --check`，全部工程 JSON 可解析，未混入 DOM/Vue API |
| 页面事件检查 | WXML 中入口、收银台和结果页事件均存在对应 Page 方法 |
| 后端全量回归 | `1107/1107` 通过，`0` failures、`0` errors、`0` skipped |
| 本地真实接口 | 创建预付单 `PRE178933949464752f0b1fc` 和支付单 `PAY1789339494646b3cf1bb0`；收银台由“待支付”进入“支付中”，详情为 `WAIT_CALLBACK / wx_h5 / MINI_PROGRAM / USERPAYING` |
| 长驻运行 | payment-core 已在当前窗口重新拉起，后端 PID `77042`，`18081` 与 Admin/App/H5/PC 四个入口及代理均为 `READY` |
| 视觉与真机边界 | 本机只有普通微信客户端、没有微信开发者工具，未执行编译或真机验证，不登记为通过 |
| 真实支付边界 | 本地接口调用产生一笔 demo 预付单、支付单和渠道尝试；后端未返回 `wx.requestPayment` 签名参数，不产生真实微信扣款、回调或资金事实 |
| 统一门禁复判 | 脚本退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`blockerCount=1`、`releaseBlockerCount=4` |

### 548.3 当前判断

1. 小程序已从“未实现”推进到“部分实现”的可运行功能骨架，可用于本地预付、提交和结果联调。
2. 真实微信支付仍需补齐登录态/openid、签名参数、AppID 与商户绑定、合法域名、证书、回调验签及真机验收。
3. Node 自动化和本地 demo 不能替代微信开发者工具编译、真机行为、真实渠道、生产并发或 release 放行证据。

## 549. 2026-09-14 服务端 API 支付终端与运营查询骨架 V1.0 验证

### 549.1 本轮补强

1. 新增零依赖 API CLI，覆盖预付、收银台、提交、详情、主动查单和受限关单；后端把 `API` 纳入规范终端、渠道场景、路由表达式和独立来源控制策略。
2. 管理端路由、收银台、支付请求和支付流水统一使用规范终端选项；中文“小程序”查询值修正为 `MINI_PROGRAM`，API 使用 `API`。
3. 路由记录新增可空 `attempt_no` 和普通索引，新提交显式绑定本次尝试；存量记录保留兼容读取，避免同秒重复尝试串联。
4. 收银台拆分预付业务场景 `payScene` 与实际发起终端 `terminal`，已提交会话读取最新尝试终端，无尝试时回退业务场景。

### 549.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 后端定向测试 | `PaymentRouteExecutionMapperIntegrationTest,CashierSessionListMapperIntegrationTest,PaymentServiceImplTest` 通过，覆盖显式关联、同秒排序、终端筛选、场景拆分和写入顺序 |
| 后端全量测试 | `1112/1112` 通过，`0` failures、`0` errors、`0` skipped |
| 管理端自动化 | Node 测试 `41/41` 通过，规范终端选项用例零失败 |
| 管理端构建 | Vite 转换 `87` 个模块并构建通过；保留既有大包提示 |
| API CLI 自动化 | Node 测试 `6/6` 通过，覆盖命令参数、请求契约、路径编码、地址归一化和业务失败 |
| 本地真实支付 | 创建 `PRE1789343473684316ad947 / PAY17893434736837c091a2d / API-DEMO-VERIFY-20260914-02` 并提交支付宝模拟渠道；结果保持 `WAIT_CALLBACK / alipay_h5 / API / WAIT_BUYER_PAY` |
| API 主动查单 | CLI 查询返回 `querySource=ALIPAY_SIMULATION`、`status=WAIT_CALLBACK`、`successAt=null`，未制造成功事实 |
| 四列表筛选 | 路由、收银台、支付请求、支付流水使用 `terminal=API` 均返回 `total=1`；收银台同时返回 `payScene=SERVICE_ORDER_API / terminal=API` |
| 路由关联 | 新路由 `RTR178934348577598f8c9ca` 正确读取本次 API 尝试 `ATT1789343485775f081e370` 的支付宝、终端和脱敏报文，不再因先前时间条件消失 |
| 长驻运行 | payment-core 在当前窗口持续运行，后端 PID `83333`，`18081` 与 Admin/App/H5/PC 四个入口及代理均为 `READY` |
| 静态检查 | `git diff --check` 通过 |
| 统一门禁复判 | 脚本退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`blockerCount=1`、`releaseBlockerCount=4` |

### 549.3 当前判断

1. API 端已从“未实现”推进到可运行的服务调用骨架，并具备四类管理查询的最小运营可观测性；它仍不是完整开放平台。
2. 显式路由尝试号消除了新数据的同秒串联风险；生产执行增量迁移前仍需评估大表更新锁影响并抽样核对存量回填，不能把兼容推断当成确定历史事实。
3. 本地 demo、模拟渠道和单机测试不证明生产凭证托管、请求签名、IP 白名单、真实渠道受理、资金结果、生产并发或 release 放行条件。

## 550. 2026-09-14 App/H5/PC 用户支付实体快照 V1.0 验证

### 550.1 本轮补强

1. 共用收银台和结果页改为监听动态路由实体，切换预付单或支付单时清空旧详情、倒计时、渠道选择、幂等键、动作反馈和 loading 状态，并重新加载当前实体。
2. GET 增加递增请求号和响应单号校验；提交、查单、关单与支付失败后的收银台刷新绑定发起时的预付单、支付单和实体版本，旧动作结果不能写回新路由。
3. 结果页返回收银台改用已加载详情关联的预付单，不再信任 URL 查询参数；新增共享实体匹配纯函数及失败闭合测试。

### 550.2 验证结果

| 项目 | 结果 |
| --- | --- |
| App 自动化 | Node 测试 `18/18` 通过，新增 4 组实体匹配用例，零失败、零跳过 |
| 三端构建 | App 转换 `36` 个模块、H5 `40` 个、PC `41` 个，三端 Vite 生产构建全部通过 |
| API CLI 自动化 | `6/6` 通过，零失败、零跳过 |
| 后端全量回归 | `1112/1112` 通过，`0` failures、`0` errors、`0` skipped |
| 本地实体 A | `PRE1789344423370a28eb9e5 / PAY178934442337081ce8d71 / APP-ENTITY-A-20260914 / ¥31.00` |
| 本地实体 B | `PRE1789344420037e6b8a426 / PAY1789344420036e8c49265 / APP-ENTITY-B-20260914 / ¥47.00` |
| 三端页面 | App、H5、PC 分别展示目标订单、金额和单号，未发现另一实体标识；PC 内容宽与视口宽均为 `1265` |
| 结果页归属 | URL 携带 `prepayOrderNo=PRE-WRONG` 时，App/H5 仍展示详情关联的真实预付单且不显示伪造值；`PAY-NOT-FOUND` 进入失败闭合 |
| 浏览器控制台 | 仅有 Vite 连接调试日志，无 warning 或 error |
| 写操作边界 | 仅创建两张本地 demo 预付单，未提交渠道、未查单、未关单、未模拟回调，不产生支付成功或资金事实 |
| 长驻运行 | 当前窗口会话 `9392` 持续托管；后端 PID `83333`，`18081` 与 App/H5/PC 开发入口保持运行 |
| 静态检查 | `git diff --check` 通过 |
| 统一门禁复判 | 脚本退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`blockerCount=1`、`releaseBlockerCount=4` |

### 550.3 当前判断

1. App/H5/PC 用户支付端已经具备按当前路由实体失败闭合的加载与动作边界，页面不会把已识别的空响应、串单响应、旧请求或旧动作结果当成当前支付事实。
2. 本地页面与自动化证明的是浏览器内一致性和基础可用骨架，不是数据库事务、真实渠道、资金到账、生产网络乱序或对账证明。
3. 本批没有改变正式发布治理事实；候选说明正式化、正式签字审批、发布后现场核验和 `master` 稳定观察结束结论仍需真实外部证据。

## 551. 2026-09-14 App/H5/PC 支付结果有界自动刷新 V1.0 验证

### 551.1 本轮补强

1. Web 三端共用结果页对 `WAIT_CALLBACK / PAYING / RISK_REVIEW` 增加每 `5` 秒一次、最多 `60` 次的只读详情刷新，终态和无需后台推进的状态不轮询。
2. 自动刷新继续复用实体快照和递增请求号；路由变化、卸载、人工查单或关单取消计时器并使旧 GET 失效，动作结束后按最新状态决定是否恢复。
3. 自动刷新只读取支付详情，不调用主动查单、回调或关单接口；失败与不一致响应静默失败闭合，不覆盖当前详情或人工动作反馈。

### 551.2 验证结果

| 项目 | 结果 |
| --- | --- |
| App 自动化 | Node 测试 `20/20` 通过，新增状态白名单、大小写归一、`5s` 间隔、`60` 次上限及非法次数用例 |
| 三端构建 | App 转换 `36` 个模块、H5 `40` 个、PC `41` 个，Vite 生产构建全部通过 |
| App 运行态 | `PAY17893434736837c091a2d` 在超过两个刷新周期后仍正确展示“支付处理中”和当前支付单，控制台无 warning/error |
| H5/PC 运行态 | 同一 `WAIT_CALLBACK` 支付单在两端跨过至少一个刷新周期，终端文案、状态和实体正确，控制台均为零 warning/error |
| 网络观测边界 | 内置浏览器隔离上下文不暴露资源性能条目，未把具体 GET 次数登记为浏览器实测；间隔和次数由纯函数测试与构建证明 |
| 写操作边界 | 未点击主动查单、关单或回调，未创建新支付尝试，不产生渠道请求、支付成功或资金事实 |
| 长驻运行 | 当前窗口会话 `9392` 持续托管，后端 PID `83333` 与 App/H5/PC 入口继续运行 |
| 静态检查 | `git diff --check` 通过 |
| 统一门禁复判 | 脚本退出码 `2` 来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`blockerCount=1`、`releaseBlockerCount=4` |

### 551.3 当前判断

1. 用户支付结果页已具备等待可信服务端状态推进并自动更新的基础能力，不再要求用户持续手工刷新页面。
2. 自动 GET 与渠道主动查单职责分离，前端轮询不会自行生成渠道事实或支付终态；真实支付收口仍依赖可信回调、查单和服务端状态机。
3. 本地策略测试与三端页面稳定性不证明生产轮询容量、网络乱序、真实渠道、资金到账或对账，release 四项真实治理阻断保持不变。

## 552. 2026-09-14 原生小程序支付结果有界自动刷新 V1.0 验证

### 552.1 本轮补强

1. 小程序结果页对 `WAIT_CALLBACK / PAYING / RISK_REVIEW` 增加每 `5` 秒一次、最多 `60` 次的只读详情刷新，与 Web 三端保持一致。
2. 页面隐藏、卸载、人工刷新、主动查单和关单取消计时器并使旧 GET 失效；中断首次加载或人工动作后，页面重新显示会立即加载当前详情。
3. 自动响应同时匹配支付单和预付单；首次可信详情覆盖页面传入的预付单号，返回收银台不再由可修改参数决定。

### 552.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 小程序自动化 | Node 测试 `11/11` 通过，覆盖刷新白名单与上限、前后台恢复、自动 GET/人工查单竞态和错预付单隔离 |
| JS 语法 | 结果页、支付工具和页面测试通过 `node --check` |
| Web 回归 | App 共享自动化 `20/20` 通过；App/H5/PC 生产构建分别转换 `36/40/41` 个模块 |
| 后端全量 | Maven `1112/1112` 通过，零失败、零错误、零跳过，`BUILD SUCCESS` |
| 静态检查 | `git diff --check` 通过 |
| 写操作边界 | 自动刷新只调用详情 GET；测试未调用真实回调、未创建渠道或资金事实 |
| 环境边界 | 未使用微信开发者工具或真机，不记录 `wx.requestPayment`、真实微信扣款或真机生命周期通过 |
| 统一门禁复判 | 退出码 `2` 仅来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`blockerCount=1`、`releaseBlockerCount=4` |
| 长驻运行 | 当前窗口会话 `21861` 持续托管，后端 PID `90199`；`18081`、Admin/App/H5/PC 入口及代理均为 `READY` |

### 552.3 当前判断

1. App/H5/PC/小程序四类用户终端均已具备等待可信支付状态自动收口的基础能力，核心结果页不再依赖持续手工刷新。
2. 小程序前后台切换与人工动作竞态具备失败闭合保护，但本地 Node 页面模型不替代微信运行时、弱网、杀进程恢复和真机验收。
3. 本轮不改变正式渠道与发布治理事实；真实微信参数、证书、回调、资金一致和 release 四项治理阻断保持不变。

## 553. 2026-09-14 标准订单支付详情与唯一联查 V1.0 验证

### 553.1 本轮补强

1. 新增标准订单详情接口 `GET /api/orders/{orderNo}` 和管理端 `/orders/:orderNo`，从订单列表可进入唯一订单支付详情。
2. 详情聚合标准订单、最近账单、最近支付单和最近预付单；三类最近事实分别按 `created_at DESC, id DESC` 选择，不声明属于同一次支付尝试。
3. 客户与服务者名称由服务端脱敏；不存在的标准订单返回 `PAYMENT-1055 / 订单不存在`，虚拟支付业务号不推导为标准订单。

### 553.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 后端定向测试 | `OrderListMapperIntegrationTest` 2 项、`OrderControllerTest` 3 项、`OrderServiceImplTest` 7 项，共 `12/12` 通过，`BUILD SUCCESS` |
| 后端全量测试 | Maven `1116/1116` 通过，`0` failures、`0` errors、`0` skipped，`BUILD SUCCESS` |
| 管理端自动化 | Node 测试 `41/41` 通过，零失败、零跳过 |
| 管理端构建 | Vite 转换 `88` 个模块并构建通过；保留既有大包提示 |
| 标准订单详情 API | `ORD202607190002` 返回订单金额 `¥8,800.00`、已支付 `¥2,000.00`、待支付 `¥6,800.00`，最近账单 `BILL202607190002`、最近支付单 `PAY202607190002`、最近预付单 `PRE202607190002` |
| 脱敏与不存在边界 | 客户名称返回 `王*`、服务者名称返回 `周*`；`ORDER-MISSING` 返回 `PAYMENT-1055 / 订单不存在` |
| 桌面视觉 | `1440x900` 下四块详情与金额区无重叠、无横向溢出，控制台零 warning/error |
| 手机视觉 | `390x844` 下无横向溢出，六个操作入口按两列排列且单项为 `179x44`，未发现文字或控件重叠 |
| 长驻运行 | 当前窗口会话 `80679` 持续托管，后端 PID `92048`；`18081` 与 Admin/App/H5/PC 入口及代理均为 `READY` |
| 统一门禁复判 | 脚本退出码 `2` 仅来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`blockerCount=1`、`releaseBlockerCount=4` |

### 553.3 当前判断

1. 管理员已可从标准订单唯一入口查看订单应付、已付、待付及三类最近支付关联事实，具备基础联查和问题定位能力。
2. 三类最近事实是独立时间序列的只读快照，不证明账单、支付单和预付单属于同一次支付尝试，也不替代支付尝试或渠道流水详情。
3. 本地 H2、demo 数据、浏览器视觉和自动化不证明真实渠道、资金一致、生产权限或 release 放行；候选说明正式化、正式签字审批、发布后现场核验和 `master` 稳定观察结束结论仍未闭环。

## 554. 2026-09-14 交易账单唯一详情与精确联查 V1.0 验证

### 554.1 本轮补强

1. 新增唯一账单详情接口 `GET /api/bills/{billNo}` 和管理端 `/bills/:billNo`，账单列表与订单详情均可进入确定账单实体。
2. 最近预付单在目标账单内稳定选择；最近支付单只经该账单预付关系关联，避免同一业务订单多账单时串联其他账单支付事实。
3. 标准订单存在时才开放唯一订单详情；虚拟业务账单不推导标准订单。客户名称由服务端脱敏，不新增账单或支付状态写入口。

### 554.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 后端定向测试 | `BillListMapperIntegrationTest` 2 项、`BillControllerTest` 4 项、`BillServiceImplTest` 8 项，共 `14/14` 通过 |
| 后端全量测试 | Maven `1120/1120` 通过，`0` failures、`0` errors、`0` skipped，`BUILD SUCCESS` |
| 管理端自动化 | Node 测试 `41/41` 通过，零失败、零跳过 |
| 管理端构建 | Vite 转换 `89` 个模块并构建通过；保留既有大包提示 |
| 唯一账单详情 API | `BILL202607190002` 返回应收 `¥8,800.00`、已付 `¥2,000.00`、待付 `¥6,800.00`，标准订单 `ORD202607190002`、最近预付单 `PRE202607190002`、最近支付单 `PAY202607190002` |
| 精确关联与脱敏 | 最近支付单明确关联预付单 `PRE202607190002`；客户名称返回 `王*`，其他账单支付事实不会进入当前账单集成样例 |
| 不存在边界 | `BILL-MISSING` 返回 `PAYMENT-1056 / 账单不存在`，不建立空详情快照 |
| 桌面视觉 | `1440x900` 下无横向溢出，六个操作按钮均为 `44px` 高，金额与三块详情无重叠，控制台零 warning/error |
| 手机视觉 | `390x844` 下文档宽与内容宽均为 `375px`，六个按钮两列排列且单项为 `172x44`，页面元素重叠检测为零 |
| 双向联查 | 账单详情进入 `/orders/ORD202607190002`，订单详情再进入 `/bills/BILL202607190002`，两次均为唯一实体且控制台无错误 |
| 长驻运行 | 当前窗口会话 `80557` 持续托管，后端 PID `94479`；`18081` 与 Admin/App/H5/PC 入口及代理均为 `READY` |
| 统一门禁复判 | 脚本退出码 `2` 仅来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`blockerCount=1`、`releaseBlockerCount=4` |

### 554.3 当前判断

1. 订单与账单两类支付侧核心实体现在都有唯一详情入口，账单可沿精确关系下钻最近预付和支付事实，基础运营联查架子进一步闭合。
2. 最近预付单和最近关联支付单仍是独立时间序列快照，不证明同一次尝试；虚拟业务账单只保留支付事实，不伪造标准订单归属。
3. 本地 H2、demo API、浏览器和自动化不证明目标 MySQL 执行计划、真实渠道扣款、账务、资金、清分、结算、对账或生产权限，release 四项真实治理阻断保持不变。

## 555. 2026-09-14 收银台会话唯一详情与陈旧快照保护 V1.0 验证

### 555.1 本轮补强

1. 新增管理端唯一会话接口 `GET /api/cashier-sessions/{sessionNo}` 和页面 `/cashier-sessions/:sessionNo`，从会话列表进入确定预付单会话。
2. 详情复用动态过期状态口径，选择最近支付尝试终端，并区分业务订单号与真实标准订单号；不复用返回原始姓名的用户收银台接口。
3. 列表和详情全部联查改为有效快照函数守卫，首次失败、动态切单和刷新失败不能继续使用陈旧关联导航。

### 555.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 后端定向测试 | `CashierSessionListMapperIntegrationTest` 3 项、`CashierSessionControllerTest` 4 项、`CashierSessionServiceImplTest` 9 项，共 `16/16` 通过 |
| 后端全量测试 | Maven `1125/1125` 通过，`0` failures、`0` errors、`0` skipped，退出码 `0` |
| 管理端自动化 | Node 测试 `41/41` 通过，零失败、零跳过 |
| 管理端构建 | Vite 转换 `90` 个模块并构建通过；保留既有大包提示 |
| 唯一会话详情 API | `PRE202607190002` 返回支付单 `PAY202607190002`、业务及标准订单 `ORD202607190002`、账单 `BILL202607190002`、终端 `APP`、金额 `¥6,800.00` 和动态状态“已失效” |
| 脱敏与不存在边界 | 客户名称返回 `王*`；`PRE-MISSING` 返回 `PAYMENT-1057 / 收银台会话不存在`，不建立详情快照 |
| 页面与窄屏 | 从列表真实点击进入唯一详情；窄屏工具按钮两列展示、文本未溢出，详情字段与接口一致 |
| 联查与失败关闭 | 有效会话可进入唯一账单详情；不存在会话展示错误码和请求号，标准订单、账单、支付单、支付记录、支付流水、用户收银台六个按钮全部禁用 |
| 长驻运行 | 当前窗口会话 `28368` 持续托管，后端 PID `97729`；`18081` 与 Admin/App/H5/PC 入口及代理均为 `READY` |
| 统一门禁复判 | 脚本退出码 `2` 仅来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`blockerCount=1`、`releaseBlockerCount=4` |

### 555.3 当前判断

1. 订单、账单和收银台会话三类支付侧基础实体均已有唯一详情入口，管理员可以从会话沿确定标识联查支付链路，基础可用骨架进一步闭合。
2. 会话状态的“已失效”是只读动态投影，最近终端也是支付尝试快照；两者不替代超时关单、渠道查单、回调、账务或对账事实。
3. 本地 H2、demo API、浏览器和自动化不证明目标 MySQL 执行计划、真实渠道扣款、资金一致、生产权限或 release 放行；四项 release 治理阻断保持不变。

## 556. 2026-09-14 支付请求唯一详情与请求级路由精确关联 V1.0 验证

### 556.1 本轮补强

1. 新增 `GET /api/payment-requests/{requestNo}` 和管理端 `/payment-requests/:requestNo`，从支付请求列表进入唯一支付尝试详情。
2. 列表、导出和详情统一按 `attempt_no` 关联路由，同一支付单的不同尝试不再串用路由；历史空尝试号路由保持未关联。
3. 详情输出订单、账单、支付单、预付单、来源、渠道、状态、配置快照和报文，IP、幂等键与报文由服务层复制后脱敏。
4. 列表和详情的选择、报文展开及联查都绑定当前有效快照；动态路由、首次失败和刷新失败均失败关闭。

### 556.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 后端定向测试 | `PaymentRequestControllerTest` 4 项、`PaymentRequestServiceImplTest` 9 项、`PaymentRequestListMapperIntegrationTest` 2 项，共 `15/15` 通过 |
| 后端全量测试 | Maven `1130/1130` 通过，`0` failures、`0` errors、`0` skipped，退出码 `0` |
| 管理端自动化 | Node 测试 `41/41` 通过，特殊字符请求号保持单一路径段 |
| 管理端构建 | Vite 转换 `92` 个模块并构建通过；保留既有大包提示 |
| 请求级路由 | 集成样例中同支付单双请求分别返回 `route-first / route-second`；空 `attempt_no` 历史路由不进入虚拟请求详情 |
| 真实详情 API | `ATT202607190002` 返回 `PAY202607190002 / PRE202607190002 / ORD202607190002 / BILL202607190002 / RTR202607190002`，终端 `APP`，IP 和幂等键已脱敏 |
| 不存在边界 | `ATT-MISSING` 返回 `PAYMENT-1058 / 支付请求不存在`，页面不建立详情快照且九类联查和用户收银台按钮全部禁用 |
| 页面与窄屏 | 有效详情展示请求、关联、路由和报文，无文字或控件重叠；窄屏操作入口两列排列 |
| 长驻运行 | 当前窗口会话 `34314` 持续托管，后端 PID `2033`；`18081` 与 Admin/App/H5/PC 入口及代理均为 `READY` |
| 静态检查 | `git diff --check` 通过 |
| 统一门禁复判 | 脚本退出码 `2` 仅来自 release 治理阻断；`canPromoteTestToMaster=true`、`canCreateRelease=false`、`blockerCount=1`、`releaseBlockerCount=4` |

### 556.3 当前判断

1. 支付请求现已具备唯一详情、请求级路由归属和完整基础联查，订单、账单、会话与请求四类核心运营实体均可从确定标识进入详情。
2. 历史空尝试号路由不做归属推断，避免为追求页面完整制造错误事实；如需补录应另走可审计迁移与核验流程。
3. 本地 H2、demo API、浏览器和自动化不证明目标 MySQL 执行计划、历史数据回填、真实渠道、资金一致、生产权限或 release 放行；四项真实治理阻断保持不变。

## 557. 2026-09-20 支付流水唯一详情与可信联查 V1.0 验证

### 557.1 本轮补强

1. 新增复合路径 `GET /api/payment-flows/{flowSource}/{flowNo}` 和管理端详情页，四类来源使用固定来源值，解决跨表裸流水号碰撞。
2. 列表、导出和详情统一携带来源；回调、事件不宽挂最近预付单，路由仅按 `attempt_no` 精确挂接支付请求。
3. 详情服务对来源、编号执行白名单、控制字符、长度边界校验，并返回 `PAYMENT-1059`；报文、IP、幂等键以脱敏副本输出。
4. 详情页校验响应复合键；首次失败不建快照，刷新失败保留阅读快照但关闭联查，列表同样关闭旧快照下钻。

### 557.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 后端定向测试 | `12/12` 通过 |
| 管理端自动化 | `41/41` 通过 |
| 管理端构建 | Vite `94` 个模块构建通过 |
| Mapper 集成 | 四来源列表、复合键碰撞、精确路由归属、空 `attempt_no` 不猜测均通过 |
| 静态检查 | `git diff --check` 通过 |
| 运行态验证 | 本轮尝试启动 `payment-core` 长驻栈时，沙箱端口绑定返回 `Operation not permitted`；提权审批服务故障，未取得真实 API 和浏览器页面证据 |
| 证据边界 | 本地 H2、单元测试、前端自动化和构建不替代目标 MySQL、历史回填、真实渠道、资金一致、浏览器实测或 release 放行 |

## 558. 2026-09-21 支付处理日志唯一详情与可信联查 V1.0 验证

### 558.1 本轮补强

1. 新增 `GET /api/payment-logs/{logNo}` 和管理端 `/payment-logs/:logNo`，以日志号唯一定位单条支付处理日志。
2. 后端详情返回支付单、业务订单、标准订单、支付请求、来源类型、来源编号、处理阶段、日志级别、来源系统、消息和创建时间。
3. 服务层对日志号执行去空白、必填、控制字符和 `64` 字符边界校验；不存在返回 `PAYMENT-1060`。
4. 前端详情页校验响应日志号与动态路由一致；首次失败不建快照，刷新失败保留阅读快照但关闭联查；日志 `PAYMENT_EVENT` 安全映射为流水 `BUSINESS_EVENT`。
5. 支付处理日志列表将“查看详情”升级为唯一详情入口，列表和侧栏快照在刷新失败后均禁止继续下钻。

### 558.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 支付日志后端定向测试 | `PaymentLogControllerTest`、`PaymentLogServiceImplTest`、`PaymentProcessLogMapperIntegrationTest` 共 `16/16` 通过 |
| 后端全量测试 | Maven `-q test` 退出码 `0`，Surefire 汇总 `1139/1139` 通过 |
| 管理端自动化 | `npm test` 共 `41/41` 通过 |
| 管理端构建 | `npm run build` 通过，Vite `96` 个模块 |
| Mapper 集成 | `findDetail` 验证日志号唯一查询、标准订单命中、来源类型/编号和不存在返回空 |
| 运行态验证 | 提权启动 `tools/dev/local-stack.sh up payment-core` 成功，`GET /api/payment-logs?pageNo=1&pageSize=1` 返回 `EVT202607190002`，`GET /api/payment-logs/EVT202607190002` 返回日志详情，`/payment-logs/EVT202607190002` 管理端路由返回 `200`，`GET /api/payment-flows/BUSINESS_EVENT/EVT202607190002` 返回同一事件流水详情 |
| 证据边界 | 当前证据不替代目标 MySQL 执行计划、历史数据回填、真实渠道、资金一致、完整浏览器交互走查或 release 放行 |

## 559. 2026-09-21 支付事件唯一详情与出站排障 V1.0 验证

### 559.1 本轮补强

1. 新增 `GET /api/payment-events/{eventNo}` 和管理端 `/payment-events/:eventNo`，以事件号唯一定位单条支付出站事件。
2. 后端详情返回支付单、业务单、标准订单、事件主题、下游系统、发布状态、HTTP 投递阶段、重试、最近重发审计、事件载荷和创建时间。
3. 服务层对事件号执行去空白、必填、控制字符和 `64` 字符边界校验；不存在返回 `PAYMENT-1061`，事件载荷以脱敏副本输出。
4. 前端详情页校验响应事件号与动态路由一致；首次失败不建快照，刷新失败保留阅读快照但关闭联查；事件流水固定使用 `BUSINESS_EVENT + eventNo`。
5. 支付事件列表将“详情”升级为唯一详情入口，列表和侧栏快照在刷新失败或交互锁定后均禁止继续下钻。

### 559.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 支付事件后端定向测试 | `PaymentEventControllerTest`、`PaymentEventServiceImplTest`、`PaymentEventRepublishAuditMapperIntegrationTest` 共 `27/27` 通过 |
| 后端全量测试 | Maven `-q test` 退出码 `0`，Surefire 汇总 `1144/1144` 通过 |
| 管理端自动化 | `npm test` 共 `41/41` 通过 |
| 管理端构建 | `npm run build` 通过，Vite `98` 个模块 |
| Mapper 集成 | `findDetail` 验证事件号唯一查询、标准订单命中、投递/重试字段和不存在返回空 |
| 运行态验证 | 前台托管 `tools/dev/local-stack.sh up payment-core` 成功，`GET /api/payment-events?pageNo=1&pageSize=1` 返回 `EVT202607190002`，`GET /api/payment-events/EVT202607190002` 返回事件详情，`/payment-events/EVT202607190002` 管理端路由返回 `200`，`GET /api/payment-flows/BUSINESS_EVENT/EVT202607190002` 返回同一事件流水详情 |
| 证据边界 | 当前证据不替代下游真实消费、账务入账、清分结算、目标 MySQL 执行计划、历史数据回填、真实渠道、资金一致、完整浏览器交互走查或 release 放行 |

## 560. 2026-09-21 支付路由执行唯一详情与可信联查 V1.0 验证

### 560.1 本轮补强

1. 新增 `GET /api/payment-routes/{routeNo}` 和管理端 `/payment-routes/:routeNo`，以路由记录号唯一定位单条路由执行事实。
2. 后端详情返回路由、支付单、订单、预付单、请求号、支付方式、渠道、配置快照、终端、IP、幂等键和请求/响应报文。
3. 服务层对路由号执行去空白、必填、控制字符和 `64` 字符边界校验；不存在返回 `PAYMENT-1062`，敏感字段以脱敏副本输出。
4. 详情 SQL 只按 `attempt_no` 精确补齐支付请求上下文；历史空尝试号路由不按支付单最近请求猜测归属。
5. 前端详情页校验响应路由号与动态路由一致；首次失败不建快照，刷新失败保留阅读快照但关闭联查；列表和侧栏新增唯一详情入口。

### 560.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 支付路由后端定向测试 | `PaymentRouteExecutionControllerTest`、`PaymentRouteExecutionServiceImplTest`、`PaymentRouteExecutionMapperIntegrationTest` 共 `15/15` 通过 |
| 后端全量测试 | Maven `-q test` 退出码 `0`，Surefire 汇总 `1149/1149` 通过 |
| 管理端自动化 | `npm test` 共 `41/41` 通过 |
| 管理端构建 | `npm run build` 通过，Vite `100` 个模块 |
| Mapper 集成 | 验证详情按路由号唯一查询、精确尝试号关联、历史空 `attempt_no` 不猜测请求归属 |
| 静态检查 | `git diff --check` 与 `xmllint --noout PaymentRouteExecutionMapper.xml` 通过 |
| 运行态验证 | 前台托管 `tools/dev/local-stack.sh up payment-core` 成功，`GET /api/payment-routes?pageNo=1&pageSize=1` 返回 `RTR202607190002`，`GET /api/payment-routes/RTR202607190002` 返回路由详情，`/payment-routes/RTR202607190002` 管理端路由返回 `200`，`GET /api/payment-flows/ROUTE_RECORD/RTR202607190002` 返回同一路由流水详情 |
| 证据边界 | 当前证据不替代目标 MySQL 执行计划、历史数据回填、真实渠道、资金一致、完整浏览器交互走查或 release 放行 |

## 561. 2026-09-21 支付交易异常唯一详情与处置骨架 V1.0 验证

### 561.1 本轮补强

1. 新增 `GET /api/payment-issues/{issueNo}` 和管理端 `/payment-issues/:issueNo`，以异常编号唯一定位单条支付交易异常。
2. 后端详情复用异常列表处置口径，返回责任组、处理状态、SLA、升级建议、告警派发摘要、供应商投递摘要和最近处理动作。
3. 服务层对异常编号执行去空白、必填、控制字符和 `128` 字符边界校验；不存在返回 `PAYMENT-1063`，客户名称以脱敏副本输出。
4. 前端详情页校验响应异常编号与动态路由一致；首次失败不建快照，刷新失败保留阅读快照但关闭联查。
5. 支付交易异常列表新增“查看详情”入口，原“立即排查”继续作为推荐路径入口；陈旧快照不能驱动详情、推荐入口或跨页联查。

### 561.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 支付异常后端定向测试 | `PaymentIssueControllerTest`、`PaymentIssueServiceImplTest`、`PaymentIssueMapperRouteContractTest` 共 `37/37` 通过 |
| 后端全量测试 | Maven `-q test` 退出码 `0`，Surefire 汇总 `1153/1153` 通过 |
| 管理端自动化 | `npm test` 共 `41/41` 通过 |
| 管理端构建 | `npm run build` 通过，Vite `102` 个模块 |
| Mapper 契约 | `findByIssueNo` 与列表保持推荐路由契约，停用渠道异常继续指向 `channelStatus=DISABLED`；最小 H2 集成 fixture 已补齐异常动作/告警表，验证旧异常检测路径不会被详情宽表拖倒 |
| 静态检查 | `git diff --check` 与 `xmllint --noout PaymentIssueMapper.xml` 通过 |
| 运行态验证 | 前台托管 `tools/dev/local-stack.sh up payment-core` 成功，`GET /api/payment-issues?pageNo=1&pageSize=1` 返回 `ISSUE-NOTIFY-NTF202607190002`，`GET /api/payment-issues/ISSUE-NOTIFY-NTF202607190002` 返回异常详情，`/payment-issues/ISSUE-NOTIFY-NTF202607190002` 管理端路由返回 `200` |
| 证据边界 | 当前证据不替代目标 MySQL 执行计划、告警真实送达、渠道事实、资金一致、完整浏览器交互走查或 release 放行 |

## 562. 2026-09-21 支付监控快照导出 V1.1 验证

### 562.1 本轮补强

1. 新增 `GET /api/payment-monitor/export`，导出与当前总览相同来源的摘要、趋势、渠道和告警 CSV 快照。
2. 导出统一经过 CSV 公式注入防护；空列表和空字段保持为空，不补造监控事实。
3. 管理端支付监控页新增“导出快照”入口，仅在最新总览加载成功且快照未过期时可用；摘要新增处理中退款数量，并可下钻到退款列表的 `PROCESSING` / `FAIL` 筛选和配置中心的 `DISABLED` 渠道筛选。

### 562.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 支付监控后端定向测试 | `PaymentMonitorControllerTest`、`PaymentMonitorServiceImplTest` 通过 |
| 后端全量测试 | Maven `-q test` 退出码 `0` |
| 管理端自动化 | `npm test` 共 `43/43` 通过 |
| 管理端构建 | `npm run build` 通过，Vite `108` 个模块 |
| 静态检查 | `git diff --check` 与 `xmllint --noout PaymentMonitorMapper.xml` 通过 |
| 证据边界 | CSV 快照只反映 payment-core 当前查询结果，不替代真实渠道、生产监控、资金一致或 release 放行事实 |

## 563. 2026-09-21 支付请求风险下钻 V1.1 验证

### 563.1 本轮补强

1. 支付请求管理页为处理中、等待回调和失败请求增加结构化状态下钻入口，复用既有请求列表查询与状态归一化契约。
2. 新增支付请求状态路由测试；下钻只改变查询筛选，不执行重试、关闭或其他写操作。

### 563.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 管理端自动化 | `npm test` 共 `44/44` 通过 |
| 管理端构建 | `npm run build` 通过，Vite `108` 个模块 |
| 静态检查 | `git diff --check` 通过 |
| 证据边界 | 下钻只提供本地请求事实筛选，不替代真实渠道结果、资金一致或 release 放行事实 |

## 564. 2026-09-21 收银台失效会话下钻 V1.1 验证

### 564.1 本轮补强

1. 收银台会话风险总览新增“查看已失效会话”入口，进入既有 `sessionStatus=已失效` 列表筛选。
2. 下钻仅改变查询条件，不执行会话关闭、支付重试或其他写操作。

### 564.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 管理端自动化 | `npm test` 共 `45/45` 通过 |
| 管理端构建 | `npm run build` 通过，Vite `108` 个模块 |
| 静态检查 | `git diff --check` 通过 |
| 证据边界 | 会话下钻只反映 payment-core 当前查询结果，不替代真实终端行为、渠道结果或 release 放行事实 |

## 565. 2026-09-21 任务中心空摘要边界修复 V1.23 验证

### 565.1 本轮补强

1. 任务中心总览在数据库摘要无行时返回空的结构化总览，不再因直接调用 setter 触发 NPE。
2. 查单趋势查询为空时按空列表处理，保持任务告警计算和页面读取可用。

### 565.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 任务中心后端定向测试 | `PaymentTaskCenterServiceImplTest` 通过 |
| 证据边界 | 空摘要兜底只证明 payment-core 查询层健壮性，不替代生产数据库、调度租约或真实渠道验证 |

## 566. 2026-09-21 工作台空卡片边界修复 V1.1 验证

### 566.1 本轮补强

1. 工作台卡片查询无数据时返回空数组，前端不再收到 `cards=null`。
2. 新增服务层空卡片边界测试；不改变已有卡片字段和排序口径。

### 566.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 工作台后端定向测试 | `DashboardServiceImplTest`、`DashboardControllerTest` 通过 |
| 证据边界 | 空卡片兜底只证明 payment-core 工作台查询健壮性，不替代生产数据完整性或 release 放行事实 |

## 567. 2026-09-21 支付指标空摘要边界修复 V1.1 验证

### 567.1 本轮补强

1. 支付指标查询底层无返回行时，服务层返回结构化零值摘要，包含 `0` 单量、`¥0.00` 成功金额和 `0.00%` 成功率。
2. 保留数据库已有摘要原样返回，不改变正常数据的统计口径。

### 567.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 支付指标后端定向测试 | `PaymentMetricsServiceImplTest`、`PaymentMetricsControllerTest` 通过 |
| 证据边界 | 空摘要兜底只证明 payment-core 指标查询层健壮性，不替代生产数据库、渠道事实、资金一致或 release 放行事实 |

## 568. 2026-09-21 配置中心空列表边界修复 V1.1 验证

### 568.1 本轮补强

1. 配置中心总览的渠道、路由规则、协议、协议类型、返回码、网关、控制策略、告警供应商和值班配置列表统一将底层 `null` 归一为空数组。
2. 保留已有查询结果和配置字段，不改变配置治理、复核或版本流程。

### 568.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 配置中心后端定向测试 | `PaymentConfigServiceImplTest`、`PaymentConfigControllerTest` 通过 |
| 证据边界 | 空列表兜底只证明 payment-core 配置总览查询健壮性，不替代可信身份权限、真实密钥托管、生产配置或 release 放行事实 |

## 569. 2026-09-21 支付日终空批次列表边界修复 V1.1 验证

### 569.1 本轮补强

1. 日终总览最近批次查询无数据时返回空数组，避免空库首次加载触发批次遍历异常。
2. 保留日终空摘要的“未生成事实”阻断结论，不把空列表兜底误判为已完成对账。

### 569.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 日终后端定向测试 | `PaymentDayEndServiceImplTest`、`PaymentDayEndControllerTest` 通过 |
| 证据边界 | 空批次列表兜底只证明 payment-core 日终查询健壮性，不替代真实跑批、对账、财务核验或 release 放行事实 |

## 570. 2026-09-21 统一分页空列表边界修复 V1.1 验证

### 570.1 本轮补强

1. `PageResultDTO` 在构造时将 mapper 返回的 `null` 列表统一归一为空数组，覆盖所有后台分页列表的共同响应契约。
2. 不改变总条数、页码、页大小和已有列表内容；空结果仍由 `total=0` 表示。

### 570.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 分页契约与关联服务定向测试 | `PageResultDTOTest`、`PaymentEventServiceImplTest`、`RefundServiceImplTest`、`PaymentTaskCenterServiceImplTest` 通过 |
| 证据边界 | 分页空列表兜底只证明 payment-core 响应契约健壮性，不替代生产数据完整性、真实渠道或 release 放行事实 |

## 571. 2026-09-21 任务中心空任务日志列表边界修复 V1.21 验证

### 571.1 本轮补强

1. 任务中心总览和任务执行日志列表在 mapper 返回 `null` 时统一使用空数组，避免空库或无匹配筛选触发日志增强遍历异常。
2. 保留任务总览、分页总数和告警推导口径，不把空列表当作任务成功事实。

### 571.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 任务中心后端定向测试 | `PaymentTaskCenterServiceImplTest` 通过 |
| 证据边界 | 空日志列表兜底只证明 payment-core 任务查询健壮性，不替代调度租约、自动任务执行或真实外部网关事实 |

## 572. 2026-09-21 管理端分页响应空列表边界修复 V1.1 验证

### 572.1 本轮补强

1. 管理端 API 客户端统一将分页响应中的 `items=null` 归一为空数组，避免列表页在兼容旧接口或异常响应时执行 `find` / `some` 触发白屏。
2. 非分页摘要和已有数组保持原样，不改变服务端分页计数和正常列表内容。

### 572.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 管理端自动化 | `npm test` 共 `47/47` 通过 |
| 管理端构建 | `npm run build` 通过，Vite `108` 个模块 |
| 证据边界 | 客户端空列表兜底只证明管理端响应消费健壮性，不替代后端数据契约、生产接口或 release 放行事实 |

## 573. 2026-09-21 异常中心责任组摘要空列表边界修复 V1.8 验证

### 573.1 本轮补强

1. 异常中心责任组摘要查询无数据时返回空数组，避免管理端摘要卡片对 `null` 执行长度和聚合计算。
2. 保留异常分页列表、SLA 和批量处理事实，不把无责任组摘要误判为无异常或已处理。

### 573.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 异常中心后端定向测试 | `PaymentIssueServiceImplTest`、`PaymentIssueControllerTest` 通过 |
| 证据边界 | 空摘要兜底只证明 payment-core 异常中心查询健壮性，不替代真实告警派发、责任组联动或 release 放行事实 |

## 574. 2026-09-21 路由冲突检测空规则边界修复 V1.10 验证

### 574.1 本轮补强

1. 配置中心路由冲突检测在规则查询无行时按空规则集处理，返回空冲突数组，不再因直接遍历 `null` 失败。
2. 保留冲突检测的启用状态、稳定分桶和表达式判断口径；空规则集只表示当前没有可检测规则，不代表生产路由已验证。

### 574.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 配置中心后端定向测试 | `PaymentConfigServiceImplTest`、`PaymentConfigControllerTest` 通过 |
| 证据边界 | 空规则集兜底只证明 payment-core 冲突检测查询健壮性，不替代真实路由配置、灰度审批、探活或 release 放行事实 |

## 575. 2026-09-21 配置复核与版本列表空值边界修复 V1.12 验证

### 575.1 本轮补强

1. 配置中心公开读取接口对渠道、路由规则、协议、返回码、网关、控制策略、告警供应商和值班路由的复核列表与版本列表统一返回空数组。
2. 不改变候选提交、批准、驳回、回滚或版本基线校验逻辑；空列表仅表示当前没有对应记录。

### 575.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 配置中心后端定向测试 | `PaymentConfigServiceImplTest`、`PaymentConfigControllerTest` 通过 |
| 证据边界 | 空列表契约只证明配置页面读取健壮性，不替代可信身份权限、正式审批、真实配置生效或 release 放行事实 |

## 576. 2026-09-21 配置复核操作回响空列表边界修复 V1.13 验证

### 576.1 本轮补强

1. 配置中心提交、批准、驳回、回滚等返回复核列表的操作结果统一经过空数组归一，避免操作成功但数据库当前无复核行时管理端刷新链路收到 `null`。
2. 不改变复核状态转换、基线校验、版本留痕和受控回滚逻辑。

### 576.2 验证结果

| 项目 | 结果 |
| --- | --- |
| 配置中心后端定向测试 | `PaymentConfigServiceImplTest`、`PaymentConfigControllerTest` 通过 |
| 证据边界 | 操作回响空列表兜底只证明配置管理响应健壮性，不替代可信身份、正式审批、真实配置生效或 release 放行事实 |
## 577. 2026-09-22 配置复核与自检空列表边界修复 V1.1 验证

### 本轮修正

1. 路由规则复核预览对复核列表和当前规则列表的 `null` 结果统一归一为空列表，空库下仍可返回合法空结果。
2. 控制策略自检的网关列表、协议类型字典和返回码导入渠道列表对 Mapper 的 `null` 结果统一归一为空列表。
3. 新增配置服务回归测试，覆盖空路由复核列表和空网关自检路径。

### 验证结果

| 项目 | 结果 |
| --- | --- |
| `PaymentConfigServiceImplTest` | 通过，`80` 个用例 |
| 真实渠道/生产配置事实 | 未声明，仍需真实供应商、密钥和审批证据 |
| release 放行 | 不推进，继续受正式审批、现场核验和稳定观察门禁约束 |

## 578. 2026-09-22 渠道路由空列表边界修复 V1.1 验证

### 本轮修正

1. 渠道路由决策对启用渠道和启用规则查询的 `null` 结果统一归一为空列表。
2. 无启用渠道时继续按既有业务契约返回 `PAYMENT_CHANNEL_UNAVAILABLE`，不因空库触发空指针异常。
3. 新增路由服务空渠道回归测试。

### 验证结果

| 项目 | 结果 |
| --- | --- |
| `PaymentChannelRoutingServiceImplTest` | 通过，`21` 个用例 |
| 真实渠道接入 | 未声明，当前仍是配置驱动路由骨架 |
| release 放行 | 不推进，仍受正式发布治理门禁约束 |

## 579. 2026-09-22 路由健康查询空列表边界修复 V1.1 验证

### 本轮修正

1. 路由健康熔断开启时，对渠道查单健康聚合查询返回的 `null` 结果统一按空指标集合处理。
2. 空健康数据不制造失败率或熔断事实，继续按渠道配置和既有路由规则决策。
3. 新增健康路由空查询结果回归测试。

### 验证结果

| 项目 | 结果 |
| --- | --- |
| `PaymentChannelRoutingServiceImplTest` | 通过，`22` 个用例 |
| 真实渠道健康数据 | 未声明，当前仅验证本地配置驱动决策 |
| release 放行 | 不推进，仍受正式发布治理门禁约束 |

## 580. 2026-09-22 用户端多端构建与测试复核

### 验证结果

| 端/模块 | 自动化测试 | 构建 |
| --- | ---: | --- |
| `admin-web` | `47/47` | Vite 通过 |
| `api-client` | `6/6` | 不适用 |
| `app-web` | `21/21` | Vite 通过 |
| `h5-web` | `2/2` | Vite 通过 |
| `mini-program` | `11/11` | 原生工程，无 Vite 构建 |
| `pc-web` | `2/2` | Vite 通过 |

### 证据边界

1. 本轮证明各端现有代码可测试、可构建，收银台、结果页和业务入口的本地契约保持一致。
2. `admin-web` 构建仍有单个大 chunk 警告，但构建成功；不把该警告解释为生产性能验收完成。
3. 本轮不声明真实渠道扣款、微信小程序 `wx.requestPayment`、可信身份、生产网关或 release 放行已经完成。

## 581. 2026-09-22 payment-core 后端全量回归复核

### 验证结果

| 项目 | 结果 |
| --- | --- |
| `systems/payment-core/backend` Maven 全量测试 | `1187` 个用例通过，`0` failures，`0` errors，`0` skipped |
| 工作区补丁检查 | `git status` clean，未产生未提交代码 |
| release 放行 | 不推进，仍受正式发布治理门禁约束 |

### 证据边界

1. 本轮证明 payment-core 当前后端状态机、任务中心、路由、回调、退款、对账和配置相关自动化测试均可通过。
2. 测试使用本地/H2/模拟适配器，不能替代真实微信、支付宝、银行渠道、消息总线、商户认证、生产密钥和资金到账事实。

## 582. 2026-09-22 对账事实失败重试语义回归

### 本轮修正

1. 新增支付事件派发回归用例：清分和账务下游明确成功、对账事实接口返回失败时，支付成功事件不得标记为 `SUCCESS`。
2. 该事件继续进入既有失败/重试路径；重试时清分、账务和对账事实均依赖各自业务幂等，不把对账不可用吞成主事件成功。

### 验证结果

| 项目 | 结果 |
| --- | --- |
| `PaymentEventDispatchServiceImplTest` | 通过 |
| payment-core 后端全量测试 | `1188` 个用例通过，`0` failures，`0` errors，`0` skipped |
| 真实对账服务不可用演练 | 未声明，仅验证 Mockito 失败语义 |
| release 放行 | 不推进，仍受正式发布治理门禁约束 |

### 证据边界

本轮只固定本地事件状态和重试边界，不证明生产对账服务可用、真实消息投递、资金一致性、重复消费治理或正式发布条件已完成。

## 583. 2026-09-22 全量回归与统一门禁复判

### 验证结果

| 项目 | 结果 |
| --- | --- |
| `systems/payment-core/backend` Maven 全量测试 | `1188` 个用例通过，`0` failures，`0` errors，`0` skipped |
| `admin-web` / `api-client` / `app-web` / `h5-web` / `pc-web` 前端测试 | 分别 `47/47`、`6/6`、`21/21`、`2/2`、`2/2` 通过 |
| 四个 Vite 前端构建 | 全部通过；`admin-web` 保留单个大 chunk 警告 |
| 统一门禁审计 | 回归证据包完整，`test -> master` 允许；`release/*` 不允许 |

### 当前门禁结论

1. `test -> master` 的回归证据、场景台账和自动化验证已满足当前审计条件。
2. `master -> release/*` 仍被正式签字审批、发布后现场核验、稳定观察结束结论和候选版正式化四项真实治理事实阻断。
3. 本轮没有修改 release 材料来制造放行结论，也没有把本地/H2/模拟渠道结果解释为真实渠道、资金到账、生产消息或生产稳定性证据。

## 584. 2026-09-22 单会话全栈 happy-path 财务收口复核

### 验证结果

| 项目 | 结果 |
| --- | --- |
| `tools/dev/local-stack.sh verify all` | 通过；全部选定后端和前端服务 READY，HTTP smoke 通过，流程结束自动清理服务 |
| 支付主链路 | `PAYMENT_SUCCESS -> CLEARING_GENERATED -> SETTLEMENT_CREATED -> ACCOUNTING_POSTED` 全部成功 |
| 退款链路 | `REFUND_SUCCESS` 事实成功并保持幂等 |
| 对账链路 | `chainStatus=COMPLETED`、`responsibilityStatus=CLOSED`、`completedStageCount=4`、`expectedStageCount=4`、`financialCloseable=true` |

### 证据边界

1. 本轮证明本地受控栈中四阶段事实能够完整落库、重复退款事实能够幂等收口，且 happy-path 已实际执行财务收口断言。
2. 本地 demo/H2/HTTP 联调不证明真实微信、支付宝、银行渠道、生产 RabbitMQ、生产 MySQL、资金到账、正式财务审批或 release 放行条件已完成。

## 585. 2026-09-23 settlement 巡检增量后的全栈回归复核

### 验证结果

| 项目 | 结果 |
| --- | --- |
| `tools/dev/local-stack.sh verify all` | 通过；全部选定后端和前端服务 READY，HTTP smoke 通过并自动清理 |
| payment-core 后端全量测试 | `1188` 个用例通过，`0` failures，`0` errors，`0` skipped |
| 跨系统支付主链路 | `PAYMENT_SUCCESS -> CLEARING_GENERATED -> SETTLEMENT_CREATED -> ACCOUNTING_POSTED` |
| 退款与对账链路 | `REFUND_SUCCESS`；`chainStatus=COMPLETED`、`completedStageCount=4`、`expectedStageCount=4`、`financialCloseable=true` |
| 统一门禁 | `test -> master` 允许；`master -> release/*` 不允许 |

### 变更范围与证据边界

1. 本轮复核覆盖 settlement 出款巡检骨架提交后的全栈启动、HTTP 冒烟、跨系统 happy-path 和 payment-core 回归，未发现主链路回退。
2. settlement 巡检默认关闭；本轮没有自动执行真实出款、重试或银行通道调用。
3. 本地 H2/demo/HTTP 栈只证明接口形状、状态收口和对账事实在受控环境下可联通，不证明真实渠道、生产 RabbitMQ/MySQL、资金到账、正式审批、现场核验或稳定观察已经完成。

## 586. 2026-09-23 当前 HEAD 全量前后端回归复核

### 验证结果

| 项目 | 结果 |
| --- | --- |
| `systems/payment-core/backend` Maven 全量测试 | `1188` 个用例通过，`0` failures，`0` errors，`0` skipped |
| `admin-web` 前端测试 | `47/47` 通过 |
| `app-web` 前端测试 | `21/21` 通过 |
| `pc-web` 前端测试 | `2/2` 通过 |
| `h5-web` 前端测试 | `2/2` 通过 |
| 四个 Vite 前端构建 | 全部通过；`admin-web` 仍有单个大 chunk 警告 |

### 当前判断

1. 当前 `test` HEAD 的 payment-core 后端与四个 Vite 前端没有发现回归，用户端支付入口、收银台、结果页及管理端运营页面仍可构建和运行。
2. 本轮仍不把本地/H2/模拟适配器结果解释为真实渠道受理、资金到账、生产消息总线、可信认证或正式发布放行事实。

## 587. 2026-09-23 非 Vite 端契约回归复核

| 项目 | 结果 |
| --- | --- |
| `mini-program` 原生纯函数测试 | `11/11` 通过 |
| `api-client` 服务间调用契约测试 | `6/6` 通过 |

本轮补齐小程序和零依赖 API 联调端的当前 HEAD 契约证据；小程序仍需微信开发者工具/真机与真实 `wx.requestPayment` 参数，API client 仍需正式商户认证和真实渠道接入，均不在本地测试通过的声明范围内。

## 588. 2026-09-23 当前 HEAD 全栈财务收口复核

| 项目 | 结果 |
| --- | --- |
| `tools/dev/local-stack.sh verify all` | 提权运行通过；全部选定后端与前端 READY，HTTP 冒烟通过并自动清理 |
| 跨系统支付链路 | `PAYMENT_SUCCESS -> CLEARING_GENERATED -> SETTLEMENT_CREATED -> ACCOUNTING_POSTED` |
| 退款与对账 | `REFUND_SUCCESS`；`chainStatus=COMPLETED`，四阶段完整，`financialCloseable=true` |

本轮验证证明当前 HEAD 在受控本地隔离栈中的正向财务链路没有回退；不证明真实微信/支付宝/银行渠道、生产 RabbitMQ/MySQL、失败派发补偿、资金到账、正式财务审批或 release 放行已经完成。

## 589. 2026-09-23 REG-08 门禁复判

### 复判结果

| 项目 | 结果 |
| --- | --- |
| 统一门禁审计 | `test -> master = 否`，`release/* = 否` |
| test 回归证据 | 未收口；REG-08 页面与补偿检查点仍为 `HISTORICAL` |
| release 治理 | 仍有候选说明正式化、正式签字审批、现场核验和稳定观察结束结论四项阻断 |

本次复判修正了回归台账与证据文件之间的矛盾：历史死信任务截图和旧 replay 记录不再被当作当前 HEAD 的同批次事实。恢复 RabbitMQ 后，必须补新消息、新任务号、当前实例状态和下游查询，再重新运行统一门禁。

### 历史材料口径

仓库中 2026-08-09 及更早的发布草案、冻结记录和合并记录保留当时的 `test -> master = 是` 历史结论，不随本次审计回写。当前门禁判断只以 `docs/releases/audit/payment-core-gate-audit.{json,md}` 和当前回归台账为准，避免把历史基线结论误当作当前 HEAD 放行结论。

## 590. 2026-09-23 当前批次 DLQ 取证工具

### 新增能力

1. 新增 `tools/dev/dlq-compensation-smoke.mjs`，要求显式提供隔离 `HSP_DLQ_SUFFIX`。
2. 工具通过 RabbitMQ Management API 发布新 `payment.success.*` 或 `clearing.generated.*` 死信消息，轮询 payment-core 补偿任务，执行人工复核和定向 replay；可选地同时注入非法报文并核验 `MANUAL_RESOLVED`，最后查询 accounting/clearing/settlement 下游事件事实。
3. 工具不接受历史任务号；RabbitMQ、payment-core 或下游事实任一不可用时直接失败，不产生“已完成”输出。

### 验证结果

| 项目 | 结果 |
| --- | --- |
| Node.js 语法检查 | 通过 |
| `tools/dev/dlq-compensation-smoke.test.mjs` mock contract | `6/6` 通过，覆盖 payment.success replay/人工结案、clearing.generated settlement replay、自定义 payload 业务单号透传、事件类型不匹配拒绝、非法超时参数拒绝和 malformed JSON 明确失败 |
| 无 broker 运行保护 | payment.success 与 clearing.generated 两种模式均明确失败，未回退历史证据 |
| 当前批次运行态 | 待 Docker/RabbitMQ 恢复后执行，暂不登记为通过 |

## 591. 2026-09-23 当前提交后端全量回归复核

### 验证命令

```bash
/Users/abc123/workspace/apache-maven-3.9.12/bin/mvn -q test
```

### 验证结果

| 项目 | 结果 |
| --- | --- |
| `systems/payment-core/backend` Maven 全量测试 | Surefire 汇总 `1188/1188` 通过，`0` failures、`0` errors、`0` skipped，退出码 `0` |
| 死信服务与控制器专项测试 | `PaymentDeadLetterTaskServiceImplTest`、`PaymentDeadLetterTaskControllerTest` 通过 |
| 运行态边界 | 本轮仅验证本地代码与测试，不宣称 RabbitMQ、真实渠道或发布治理条件已完成 |

## 592. 2026-09-23 当前提交前端全量回归复核

### 验证结果

| 项目 | 结果 |
| --- | --- |
| 前端测试 | `admin-web 47/47`、`app-web 21/21`、`pc-web 2/2`、`h5-web 2/2`、`mini-program 11/11`、`api-client 6/6`，合计 `89/89` 通过 |
| Vite 构建 | `admin-web`、`app-web`、`pc-web`、`h5-web` 均构建成功 |
| 构建提示 | `admin-web` 有单个 bundle 超过 500 kB 的 Vite 提示，不影响本次构建成功；代码分包仍列为后续性能优化项 |
| 运行态边界 | 本轮仅验证前端代码与构建产物，不宣称真实渠道、消息代理或发布治理条件已完成 |

## 593. 2026-09-23 admin-web 路由懒加载与首屏包体收口

### 本轮变更

1. `admin-web/src/router/index.js` 保留主布局同步加载，将业务列表和详情页改为 Vue Router 异步组件，按路由生成独立 chunk。
2. 路由路径、名称、参数和页面组件边界不变，不改变现有业务状态机或接口调用。

### 验证结果

| 项目 | 结果 |
| --- | --- |
| admin-web Node 测试 | `47/47` 通过 |
| admin-web Vite 构建 | 通过；入口 JS 从原约 `682 kB` 降为约 `103 kB`，业务页面按路由拆分 |
| 构建产物边界 | 详情、列表、配置和任务页面均生成独立 chunk；未产生未跟踪仓库文件 |
| 运行态边界 | 本轮只改善前端首屏加载，不代表生产 CDN、缓存策略或真实发布验收已完成 |

## 594. 2026-09-23 当前门禁快照

执行 `node tools/release/audit_payment_core_gate.mjs --root-dir . --pack docs/releases/evidence/test-20260809-regression --stdout-json` 的当前结果：

| 门禁 | 当前结论 |
| --- | --- |
| `test -> master` | `false`；REG-08 仍缺当前批次页面截图和补偿/回滚闭环证据 |
| `master -> release/*` | `false`；候选说明正式化、正式签字审批、发布后现场核验、稳定观察结束结论仍未形成 |
| 历史记录边界 | 文档中更早批次的 `test -> master = 是` 仅保留为历史事实，不作为当前 HEAD 放行依据 |

## 595. 2026-09-23 跨系统管理端构建复核

### 验证结果

| 项目 | 结果 |
| --- | --- |
| 管理端 Vite 构建 | accounting、clearing、deposit、gateway-access、ops-config、reconciliation、refund-center、risk-control、settlement、wallet-account、wallet-system 共 `11/11` 通过 |
| 构建命令 | 各管理端目录执行 `npm run build` |
| 构建产物边界 | 本地构建均成功，未产生未跟踪仓库文件；不代表 CDN、生产缓存、浏览器兼容性或发布后现场验收 |

## 596. 2026-09-23 服务调用端异常响应契约收口

### 本轮变更

1. `frontend/api-client/payment-api.mjs` 对上游返回非 JSON、空响应或代理错误页的情况统一抛出 `PAYMENT-API-CLIENT-INVALID-RESPONSE`，并保留 HTTP 状态码。
2. CLI 错误输出优先保留结构化客户端错误码，避免调用方只能看到底层 JSON 解析异常。
3. 该处理只改善服务调用端的失败可诊断性，不把网关不可用解释为支付失败、渠道失败或资金事实。

### 验证结果

| 项目 | 结果 |
| --- | --- |
| `frontend/api-client` Node 测试 | `7/7` 通过 |
| 非 JSON 上游响应回归 | 明确返回客户端错误码和 HTTP 状态码 |
| `git diff --check` | 通过 |
| 提交 | `4ce80628 fix: normalize invalid api client responses` |

## 597. 2026-09-23 用户端与管理端上游响应错误契约统一

### 本轮变更

1. `app-web` 和 `admin-web` 的浏览器端 API client 对非 JSON 或空响应统一返回 `PAYMENT-CLIENT-INVALID-RESPONSE`，不再把代理错误页降级成普通业务失败。
2. 两端均保留可读错误消息，业务页面可以沿用现有错误展示，不把网关不可用误判为支付、退款或资金状态。

### 验证结果

| 项目 | 结果 |
| --- | --- |
| `app-web` Node 测试 | `22/22` 通过 |
| `admin-web` Node 测试 | `48/48` 通过 |
| `git diff --check` | 通过 |
| 提交 | `47766543 fix: normalize frontend upstream response errors` |

## 598. 2026-09-23 三端传输失败错误码统一

### 本轮变更

1. `api-client`、`app-web` 和 `admin-web` 对网络断开统一返回网络错误码，对中止请求统一返回超时错误码。
2. 收银台、管理后台和服务到服务调用端可以据此区分业务拒绝、协议响应异常、网络不可达和请求超时；不改变支付、退款或资金状态。

### 验证结果

| 项目 | 结果 |
| --- | --- |
| `api-client` Node 测试 | `8/8` 通过 |
| `app-web` Node 测试 | `23/23` 通过 |
| `admin-web` Node 测试 | `49/49` 通过 |
| `git diff --check` | 待提交前复核 |

## 600. 2026-09-23 跨系统管理端前端聚合回归

### 验证范围

本轮对 accounting、clearing、deposit、gateway-access、ops-config、payment-core、reconciliation、refund-center、risk-control、settlement、wallet-account、wallet-system 共 `12` 个管理端执行各自 `npm test && npm run build`。

后续可重复执行：

```bash
node tools/dev/frontend-admin-regression.mjs
```

### 验证结果

| 项目 | 结果 |
| --- | --- |
| 管理端 Node 测试 | `85/85` 通过，`0` failures、`0` errors、`0` skipped |
| 管理端 Vite 构建 | `12/12` 通过 |
| 工作区产物 | 未产生需纳入仓库的构建产物变更 |
| 运行态边界 | 只证明本地前端契约和构建可用，不代表真实渠道、资金、生产配置或发布治理完成 |

## 601. 2026-09-23 payment-core 后端全量回归复核

### 验证命令

```bash
/Users/abc123/workspace/apache-maven-3.9.12/bin/mvn -q test
```

### 验证结果

| 项目 | 结果 |
| --- | --- |
| `systems/payment-core/backend` Maven/Surefire | `1188/1188` 通过，`0` failures、`0` errors、`0` skipped |
| 测试日志中的受控异常 | 属于失败闭合、事务回滚和敏感信息清洗场景的主动验证，不计为测试失败 |
| 运行态边界 | 仅证明 demo profile 下本地代码和测试通过，不代表 RabbitMQ、真实渠道、生产 MySQL 或发布治理完成 |

## 599. 2026-09-23 小程序传输失败错误码补齐

### 本轮变更

1. 小程序 `wx.request` 的网络失败与超时分别返回 `PAYMENT-MINI-NETWORK`、`PAYMENT-MINI-TIMEOUT`。
2. 小程序收银台和结果页可以沿用统一错误展示，不把网络故障误当成支付业务拒绝。
3. 该补丁只规范客户端错误边界，不改变支付状态、主动查单或关单条件。

### 验证结果

| 项目 | 结果 |
| --- | --- |
| 小程序 Node 测试 | `13/13` 通过 |
| 网络失败回归 | 通过 |
| 超时失败回归 | 通过 |
| `git diff --check` | 待提交前复核 |

## 602. 2026-09-23 后端模块回归脚本接入

### 本轮变更

新增 `tools/dev/backend-regression.mjs`，统一维护 12 个后端模块的 Maven 回归入口：

```bash
node tools/dev/backend-regression.mjs
node tools/dev/backend-regression.mjs --only payment-core/backend
MAVEN_BIN=/path/to/mvn node tools/dev/backend-regression.mjs
```

脚本会校验模块目录和 `pom.xml`，探测可用 Maven，并在任一模块失败时以非零状态退出；它只负责本地自动化回归，不替代真实 RabbitMQ、渠道、生产数据库或发布治理证据。

### 当前验证结果

| 项目 | 结果 |
| --- | --- |
| `payment-core/backend` 定向执行 | 通过，模块回归命令成功完成 |
| Maven 路径 | `/Users/abc123/workspace/apache-maven-3.9.12/bin/mvn` |
| 当前批次真实消息代理取证 | 未完成，Docker socket 权限阻断 |

## 603. 2026-09-23 全系统后端聚合回归复核

### 验证命令

```bash
MAVEN_BIN=/Users/abc123/workspace/apache-maven-3.9.12/bin/mvn \
  node tools/dev/backend-regression.mjs
```

### 验证结果

| 项目 | 结果 |
| --- | --- |
| 后端模块 | `12/12` 通过 |
| Surefire 用例 | `1465/1465` 通过，`0` failures、`0` errors、`0` skipped |
| 覆盖模块 | payment-core、gateway-access、settlement、accounting、clearing、refund-center、reconciliation、risk-control、ops-config、deposit、wallet-account、wallet-system |
| 运行态边界 | 证明本地各后端模块测试可执行，不替代真实 RabbitMQ、渠道、生产数据库、资金事实或发布治理证据 |

## 604. 2026-09-23 payment-core 用户端与小程序回归复核

### 验证结果

| 端 | 自动化测试 | 构建 |
| --- | ---: | --- |
| `api-client` | `8/8` | 不适用（Node 服务调用端） |
| `app-web` | `23/23` | 既有构建记录保持通过 |
| `h5-web` | `2/2` | Vite 通过，`41` modules |
| `pc-web` | `2/2` | Vite 通过，`42` modules |
| `mini-program` | `13/13` | 原生小程序，无 Vite 构建 |
| 合计 | `48/48` | 本轮涉及构建均通过 |

本轮只证明本地客户端契约、路由、错误边界和小程序页面模型可运行；微信开发者工具/真机、`wx.requestPayment` 签名参数、真实渠道扣款与资金事实仍未被本地测试替代。

## 605. 2026-09-23 支付流水页联查动作运行时修复

### 本轮修复

`PaymentFlowsView.vue` 的支付单、流水详情、支付请求和处理日志联查动作统一使用快照可用门禁。此前动作函数引用了未定义的 `canUseSnapshot`，页面构建虽可通过，但用户点击联查时会触发运行时错误；现已补齐该计算状态，并在加载中、无成功快照或快照刷新失败时关闭联查动作。

### 验证结果

| 项目 | 结果 |
| --- | --- |
| `admin-web` Node 测试 | `49/49` 通过 |
| `admin-web` Vite 构建 | 通过，`109` modules |
| `git diff --check` | 通过 |
| 提交 | `6bc6c0aa fix(payment-core): guard payment flow snapshot actions` |

## 606. 2026-09-23 跨系统管理端修复后回归

支付流水页运行时修复后重新执行：

```bash
node tools/dev/frontend-admin-regression.mjs
```

结果：12 个管理端均通过 `npm test && npm run build`，前端用例合计 `85/85` 通过；本轮未发现跨系统管理端契约或构建回退。

## 607. 2026-09-23 对账管理端跨系统链路诊断入口

### 本轮变更

1. reconciliation 管理端新增“链路诊断”入口，可按 `businessChainId` 查询支付、清分、结算、账务等跨系统事实快照。
2. 新增 API client `getCrossSystemChain`，对链路标识进行单路径编码，并沿用现有网络、非 JSON 和业务错误契约。
3. 页面展示统一链路状态、责任状态、完成阶段、异常事实、财务收口判断、下一动作和事实明细；只读查询不新增资金或状态写入口。

### 验证结果

| 项目 | 结果 |
| --- | --- |
| reconciliation admin Node 测试 | `4/4` 通过 |
| reconciliation admin Vite 构建 | 通过，`11` modules |
| reconciliation backend 回归 | `1/1` 模块通过（`ReconciliationIntegrationTest`） |
| 真实跨系统事实 | 当前未启动长驻栈；本轮不把本地契约测试解释为真实 MQ、生产数据库或财务收口事实 |

## 608. 2026-09-23 链路诊断后的管理端聚合回归

执行 `node tools/dev/frontend-admin-regression.mjs`，12 个管理端均通过 `npm test && npm run build`，前端用例合计 `85/85` 通过；新增 reconciliation 链路诊断入口未造成其他管理端回退。

## 609. 2026-09-23 链路事实时间字段映射修复

复核 reconciliation `CrossSystemFactDTO` 后发现事实发生时间字段为 `occurredAt`，链路诊断表此前错误读取 `factAt`，导致时间列显示为空。已修正为后端契约字段，并完成 reconciliation admin `4/4` 测试与 Vite 构建复核；不涉及 API、数据库或资金状态变化。

## 610. 2026-09-23 链路事实金额与配置版本展示补齐

跨系统链路诊断事实表新增金额和配置快照版本列，金额沿用后端字符串序列化，不做 JavaScript 浮点转换；用于运营核对各阶段金额口径及实际消费的配置版本。reconciliation admin `4/4` 测试和 Vite 构建继续通过，不新增写入口或资金事实。

## 611. 2026-09-23 后端回归工具 Maven 探测可移植性修复

`tools/dev/backend-regression.mjs` 移除机器相关的绝对 Maven 路径，改为按 `MAVEN_BIN`、`MAVEN_HOME/bin/mvn`、PATH 中的 `mvn` 顺序探测；找不到时给出明确配置提示。使用显式 `MAVEN_BIN` 对 reconciliation 后端定向回归验证通过，`1/1` 模块通过。

## 612. 2026-09-23 全套本地骨架持续托管与跨系统验收

按项目级运行基线在当前任务窗口启动 `tools/dev/local-stack.sh up all`，由同一托管会话持续运行。12 个后端和 15 个前端入口全部达到 `READY`，未使用外部进程或静态页面替代。

随后执行：

```bash
tools/dev/local-stack.sh status all
tools/dev/local-stack.sh smoke all
tools/dev/local-stack.sh happy-path
```

结果：状态检查和 HTTP 冒烟通过；跨系统 happy-path 实际生成支付单、支付成功事件、清分单、结算单、退款单，并确认账务事件包含 `PAYMENT_SUCCESS`、`CLEARING_GENERATED`、`REFUND_SUCCESS`，对账事实包含支付成功、清分生成、结算创建、账务入账和退款成功，链路状态为 `COMPLETED`，财务收口判断为 `true`。该结果证明本地 demo/H2/HTTP 功能骨架可运行，不代表真实渠道、生产 MQ、生产数据库或正式发布治理已完成。

## 613. 2026-09-23 本地全栈启动工具链探测可移植性

`tools/dev/local-stack.sh` 不再依赖单一机器上的 Maven/JDK 绝对路径：Maven 按 `HSP_MAVEN_BIN`、PATH、仓库相邻工具链和已知本地候选路径探测；JDK 按 `HSP_JAVA_HOME`、PATH 和本机候选路径探测；Maven 本地仓库按 `HSP_MAVEN_REPO`、相邻仓库和用户本地仓库探测。

验证结果：`bash -n tools/dev/local-stack.sh` 通过；显式配置 `HSP_MAVEN_BIN/HSP_JAVA_HOME` 执行 `doctor backends` 通过；移除两个环境变量后仍能通过本机探测执行 `doctor backends`。本轮不重启已运行服务，也不改变业务接口、数据库或资金状态。

## 614. 2026-09-23 统一门禁复判

执行：

```bash
node tools/release/audit_payment_core_gate.mjs --root-dir . --pack docs/releases/evidence/test-20260809-regression --stdout-json
```

当前结果：`canPromoteTestToMaster=false`、`canCreateRelease=false`。`test -> master` 仍有 `REG-08` 当前批次页面截图和补偿/DLQ 闭环证据缺口；`master -> release/*` 仍缺候选版正式化、正式签字审批、发布后现场核验和稳定观察结束结论。本次复判不改变任何放行状态，也不把本地长驻栈或 happy-path 当作生产发布证据。

## 615. 2026-09-23 后端全量回归工具链复核

首次在未设置 `MAVEN_BIN/MAVEN_HOME` 的环境执行 `node tools/dev/backend-regression.mjs` 时，原脚本因 PATH 没有 Maven 直接失败；已补充仓库相邻 Maven 工具链探测。修复后重新执行同一命令，12/12 后端模块全部通过，未发生 failures、errors 或 skipped。

本轮只改善回归工具的环境发现能力，不改变后端业务、接口、数据库、真实渠道或发布门禁结论。

## 618. 2026-09-23 REG-08 DLQ smoke 失败契约与环境复核

`tools/dev/dlq-compensation-smoke.mjs` 的网络请求现在统一转换为带目标 URL 的稳定 `request failed` 错误；新增不可达 RabbitMQ 管理接口测试后，DLQ smoke mock 回归为 `7/7` 通过。

使用新隔离后缀尝试当前批次真实 smoke：

```bash
HSP_DLQ_SUFFIX=20260923-b \
HSP_DLQ_EVENT_KIND=payment-success \
HSP_DLQ_TARGET_SYSTEM=accounting \
HSP_DLQ_TIMEOUT_MS=1000 \
node tools/dev/dlq-compensation-smoke.mjs
```

结果：在发布消息前因 `http://127.0.0.1:15672` RabbitMQ 管理接口不可达而失败；`docker info` 同时确认 Docker daemon 未运行。未创建当前批次 DLQ 消息、任务号、回放事实或截图，因此 `REG-08` 继续保持 `HISTORICAL`，门禁不改判。

## 616. 2026-09-23 payment-core 用户端回归复核

按各工程实际 package 脚本执行用户端回归：

| 入口 | 测试 | 构建 |
| --- | ---: | --- |
| `api-client` | `8/8` | 无 `build` 脚本，按 CLI 工程只执行测试 |
| `app-web` | `23/23` | Vite 通过 |
| `h5-web` | `2/2` | Vite 通过 |
| `pc-web` | `2/2` | Vite 通过 |
| `mini-program` | `13/13` | 原生小程序工程无 `build` 脚本，按测试契约执行 |

本轮确认用户端测试和可构建 Web 入口没有回退；原生小程序真机、微信开发者工具和真实 `wx.requestPayment` 仍未验证，不把本地测试解释为真实扣款或渠道事实。

## 617. 2026-09-23 payment-core 用户端回归脚本固化

新增 `tools/dev/payment-core-user-regression.mjs`，统一遍历 `api-client`、`app-web`、`h5-web`、`pc-web` 和 `mini-program`，要求每个工程声明 `test` 脚本；只有声明了 `build` 的工程才执行构建，原生小程序和 API client 的无构建脚本会明确记录为 accepted，而不会误报失败。支持 `--only` 定向执行。

执行 `node tools/dev/payment-core-user-regression.mjs` 结果为 `5/5 user apps passed their declared checks`：API client `8/8`、App `23/23`、H5 `2/2`、PC `2/2`、小程序 `13/13`，App/H5/PC 构建通过。

## 619. 2026-09-23 payment-core 后端当前 HEAD 全量回归

在当前 `test` HEAD 执行：

```bash
/Users/abc123/workspace/apache-maven-3.9.12/bin/mvn -q test
```

Surefire 报告汇总 `1188` 个用例，`0` failures、`0` errors、`0` skipped。测试期间出现的异常文本均来自预期失败分支和事务回滚断言，不构成测试失败。

本轮只证明 payment-core 本地后端测试可回归；RabbitMQ、真实渠道、生产数据库、生产通知供应商和正式发布治理仍未被本地测试替代。

## 620. 2026-09-23 跨系统管理端聚合回归复核

执行：

```bash
node tools/dev/frontend-admin-regression.mjs
```

结果：`12/12` 管理端均通过 `npm test && npm run build`，前端用例合计 `89/89` 通过。覆盖 payment-core、clearing、settlement、reconciliation、accounting、refund、deposit、wallet、wallet-account、risk-control、ops-config 和 gateway-access 管理端。

本轮只证明管理端 API 契约与生产构建没有回退；不替代真实浏览器、微信开发者工具、RabbitMQ、渠道、生产数据库或正式发布治理验证。

## 621. 2026-09-23 本地全栈启动工具链探测修复

1. 修正 `tools/dev/local-stack.sh` 在 macOS 上把 `/usr/bin/java` 错误裁剪为 `/usr` 的问题，优先通过 `/usr/libexec/java_home` 解析真实 JDK Home，并保留显式 `HSP_JAVA_HOME` 覆盖能力。
2. Maven 本地仓库探测增加可写性判断，避免优先选中不可写的绝对路径仓库；找不到可写候选时继续落到用户本地仓库或运行时目录。
3. `bash -n tools/dev/local-stack.sh`、默认 `doctor backends` 和显式 `HSP_JAVA_HOME` 的 `doctor backends` 均通过。

在未提升的执行沙箱中，单服务启动仍受 Maven 状态文件写入和本地端口监听限制；该次失败不代表业务服务失败。后续已在允许本地端口监听和 Maven 仓库访问的受控托管环境完成复验，见第 622 节。

## 622. 2026-09-23 受控本地全栈 HTTP 与 happy-path 复核

在允许本地端口和托管进程访问的受控环境中执行：

```bash
tools/dev/local-stack.sh status all
tools/dev/local-stack.sh smoke all
tools/dev/local-stack.sh happy-path
```

### 验证结果

| 项目 | 结果 |
| --- | --- |
| 本地托管状态 | `12` 个后端、`15` 个前端及代理路径均 `READY` |
| HTTP 冒烟 | `[PASS] 所选本地系统 HTTP 冒烟通过` |
| payment-core dashboard | `code=0`，返回工作台摘要 |
| 跨系统 happy-path | `[PASS] cross-system local happy path` |
| 对账链路 | `COMPLETED`，`4/4` 阶段完成，`financialCloseable=true` |

本批次本地业务标识为：支付单 `PAY1790108391985b894c4e9`、清分单 `CLO20005`、结算单 `SLT20006`、退款单 `REF20260923041952515`。账务事实包含 `PAYMENT_SUCCESS/CLEARING_GENERATED/REFUND_SUCCESS`，对账事实包含支付成功、清分生成、结算创建、账务入账和退款成功。

以上是 demo/H2/HTTP 受控本地骨架证据，不代表真实微信、支付宝、银行渠道、RabbitMQ、生产数据库、资金到账或正式发布治理已验收。

## 623. 2026-09-23 本地栈外部托管状态显示与 REG-08 复核

1. `tools/dev/local-stack.sh status` 现在将“HTTP 已 READY、但进程不是当前脚本托管”的服务显示为 `external/ready`，不再因残留 PID 文件显示为误导性的 `stale-pid`。
2. 当前 payment-core 后端和四个前端入口复核均为 `READY / external/ready`，`bash -n tools/dev/local-stack.sh` 和补丁检查通过。
3. 当前批次 REG-08 真实条件复核：`http://127.0.0.1:15672` RabbitMQ 管理接口不可达，Docker daemon 也未运行；因此未执行发布消息、未创建当前批次 DLQ 任务或回放事实，门禁继续保持未放行。

## 624. 2026-09-23 全栈 verify 复用外部托管服务

执行：

```bash
tools/dev/local-stack.sh verify all
```

结果：脚本正确复用现有外部托管的 `12` 个后端、`15` 个前端及代理路径，全部显示 `READY / external/ready`；HTTP smoke 和 cross-system happy-path 均通过。新批次支付单为 `PAY1790108597980f54a867b`，清分单 `CLO20006`，结算单 `SLT20007`，退款单 `REF20260923042318300`，对账链路 `COMPLETED`、`4/4`、`financialCloseable=true`。

该 verify 仍属于受控 demo/H2/HTTP 本地骨架验证；不改变真实渠道、RabbitMQ、生产数据库、资金到账和正式发布治理边界。

## 625. 2026-09-23 清分/结算/对账分页接口运行态复核

在当前受控本地托管栈中分别请求第二页：

| 接口 | 结果 |
| --- | --- |
| `GET /api/clearing/orders?pageNo=2&pageSize=20` | `code=0`，`total=6`，`pageNo=2`，`pageSize=20` |
| `GET /api/settlements/payouts?pageNo=2&pageSize=20` | `code=0`，`total=0`，`pageNo=2`，`pageSize=20` |
| `GET /api/reconciliation/differences?pageNo=2&pageSize=20` | `code=0`，`total=1`，`pageNo=2`，`pageSize=20` |

空页仍保留标准分页元数据；本轮只验证本地 HTTP 查询契约，不代表生产数据量、索引性能或财务正式核验。

## 626. 2026-09-23 本地栈 Maven 仓库前置诊断

`tools/dev/local-stack.sh doctor backends` 新增 Maven 本地仓库路径与可写性检查，当前受控环境输出：

```text
[OK] Maven repository writable -> /Users/abc123/apache-maven-3.9.16/repository
```

该检查用于在启动前发现工具链权限问题，不代表 Maven 依赖下载、生产数据库或真实服务依赖已经验收。

## 627. 2026-09-23 全栈工具链 doctor 复核

执行 `tools/dev/local-stack.sh doctor all` 通过：Maven、真实 JDK、Maven 仓库、Node/npm 和 `15` 个前端工程依赖均满足启动前置条件。

同批次门禁复判仍为 `test -> master = false`、`release/* = false`；REG-08 仍缺当前批次页面与补偿闭环证据，release 仍缺候选说明正式化、正式签字、现场核验和稳定观察结束结论。

## 628. 2026-09-23 accounting 管理端列表骨架分页收口

补齐 accounting 管理端六个核心列表页的可用分页入口：主体、账户、流水、冻结单、调账单和账户事件。查询与重置会回到第一页，翻页按钮在首尾页和加载中禁用；接口返回的总数与页码用于分页摘要，翻页后详情预览同步当前页首条记录。Dashboard 仍保持摘要用途的第一页读取，不将摘要改造成运营明细列表。

验证：

```bash
cd systems/accounting-system/frontend/admin-web
npm test
npm run build
```

结果：API 客户端测试 `4/4` 通过，Vite 生产构建通过，`git diff --check` 通过。流水分页参数契约测试覆盖筛选条件、`pageNo` 与 `pageSize`。本轮代码提交为 `813161b2`、`8fcd1804`。

本轮只证明管理端分页代码和构建契约可用；accounting 后端未在本轮常驻运行，未形成新的 HTTP 运行态样本，也不代表生产数据规模、索引性能或正式发布验收。

## 629. 2026-09-23 管理端全量回归复核

在 accounting 管理端分页收口后重新执行：

```bash
node tools/dev/frontend-admin-regression.mjs
```

结果：`12/12` 管理端均通过各自 `npm test && npm run build`。其中 payment-core 管理端 `49/49` 通过，accounting 管理端 `4/4` 通过；全量回归脚本最终输出 `12/12 admin apps passed test and build`。

该结果证明当前代码基线的管理端测试与生产构建没有回退，不替代真实浏览器交互、现场核验、RabbitMQ/DLQ 当前批次证据或正式发布审批。

## 630. 2026-09-23 payment-core 当前 HEAD 后端全量回归

在当前 `test` HEAD 执行：

```bash
cd systems/payment-core/backend
/Users/abc123/workspace/apache-maven-3.9.12/bin/mvn -q test
```

Surefire 汇总：`1188` tests，`0` failures、`0` errors、`0` skipped。日志中出现的异常文本来自预期失败分支、事务回滚和隔离配置测试断言，未形成失败用例。

本轮只证明 demo/H2 测试配置下的 payment-core 后端当前代码基线可回归；不替代真实渠道、RabbitMQ、生产数据库、资金到账或正式发布治理验证。

## 631. 2026-09-23 clearing 管理端主列表分页收口

补齐 clearing 管理端四个主运营列表页的分页能力：清分批次、清分规则、费用规则和分账明细。页面现在使用服务端 `total/pageNo/pageSize`，查询与重置回到第一页，翻页按钮在首尾页和加载中禁用，移动端采用纵向分页布局；批次详情下钻的关联清分结果仍作为独立明细读取，不与批次主列表页码混用。

验证：

```bash
cd systems/clearing-system/frontend/admin-web
npm test
npm run build
```

结果：`5/5` API 客户端测试通过，Vite 生产构建通过，`git diff --check` 通过。新增契约测试覆盖批次、规则、费用和分账列表的分页参数传递。本轮不形成新的生产或 RabbitMQ 事实证据。

## 632. 2026-09-23 clearing 改动后跨系统管理端回归

再次执行：

```bash
node tools/dev/frontend-admin-regression.mjs
```

结果：`12/12` 管理端通过各自测试与生产构建；clearing 管理端本轮为 `5/5` 测试通过，payment-core 管理端保持 `49/49` 通过。该回归只证明代码和构建基线没有回退，不改变当前 REG-08、RabbitMQ、真实渠道及正式发布治理边界。

## 633. 2026-09-23 settlement 管理端主列表分页收口

补齐 settlement 管理端四个主运营列表页的分页能力：结算批次、结算单、结算事件和审核工作台。主列表使用服务端 `total/pageNo/pageSize`，查询与重置回到第一页，翻页按钮在首尾页和加载中禁用；批次与事件下钻的关联结算单仍保持独立明细读取。

验证：

```bash
cd systems/settlement-system/frontend/admin-web
npm test
npm run build
```

结果：`7/7` API 客户端测试通过，Vite 生产构建通过，`git diff --check` 通过。新增契约测试覆盖批次、结算单和事件列表分页参数；本轮不形成新的生产、渠道或 RabbitMQ 事实证据。

## 634. 2026-09-23 settlement 改动后跨系统管理端回归

再次执行：

```bash
node tools/dev/frontend-admin-regression.mjs
```

结果：`12/12` 管理端通过各自测试与生产构建；settlement 管理端本轮为 `7/7` 测试通过，payment-core 管理端保持 `49/49` 通过。该回归只证明代码和构建基线没有回退，不改变当前发布证据和治理门禁。

## 635. 2026-09-23 主运营列表分页扫描边界

对 clearing、settlement、accounting、payment-core 和 reconciliation 管理端进行固定第一页扫描：clearing、settlement、accounting 的主运营列表已接通服务端分页；payment-core 现有主列表已具备分页与快照门禁。剩余固定第一页调用均属于 Dashboard 摘要或已选中批次/事件的详情下钻；reconciliation 批次列表当前后端接口返回数组且没有分页契约，未仅修改前端制造假分页。

该边界记录用于避免把摘要/详情查询误判为主列表缺陷，也避免在缺少后端契约时伪造分页能力。正式发布门禁不因此改变。

## 636. 2026-09-23 reconciliation 批次列表分页闭环

为 reconciliation 批次主列表补齐真实分页契约：

- 后端新增 `BatchQueryDTO`，Controller 接收 `pageNo/pageSize`，Service 对页码和页大小做边界归一。
- MyBatis 增加批次分页查询与总数查询，返回统一 `PageResultDTO<ReconciliationBatchListItemDTO>`。
- reconciliation 管理端新增批次分页控件，并保持 Dashboard 摘要、批次详情和差异分页的独立行为。

验证：

```bash
cd systems/reconciliation-system/frontend/admin-web
npm test
npm run build

cd ../../backend
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home \
PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH \
/Users/abc123/workspace/apache-maven-3.9.12/bin/mvn -q test
```

结果：前端 `6/6` 通过并构建成功；后端 `14/14` 通过（集成测试 `13`、DAO 边界测试 `1`）。本轮代码验证使用本地 H2/JDK，仍不代表生产数据库、真实渠道或正式发布验收。

## 637. 2026-09-23 reconciliation 改动后跨系统管理端回归

对 reconciliation 批次主列表分页改动执行全量管理端回归：

```bash
node tools/dev/frontend-admin-regression.mjs
```

结果：`12/12` 管理端均通过各自测试与生产构建；payment-core `49/49`、clearing `5/5`、settlement `7/7`、reconciliation `6/6`。该回归只证明当前代码和构建基线未回退，不替代真实浏览器现场核验、RabbitMQ/DLQ 当前批次证据、生产渠道或正式发布审批。

## 638. 2026-09-23 当前 HEAD 后端全量回归复核

在当前 `test` HEAD 使用真实 JDK 8 执行：

```bash
cd systems/payment-core/backend
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home \
PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH \
/Users/abc123/workspace/apache-maven-3.9.12/bin/mvn -q test
```

Surefire 汇总：`1188` tests，`0` failures、`0` errors、`0` skipped。输出中的异常来自预期失败分支、事务回滚和隔离配置测试断言；本轮只证明本地 demo/H2 测试基线可回归，不替代真实渠道、生产数据库、RabbitMQ、资金到账或正式发布治理验证。

## 639. 2026-09-23 小程序支付骨架回归

在 `systems/payment-core/frontend/mini-program` 执行：

```bash
npm test
```

结果：`13/13` 通过，覆盖业务入口、预付单、收银台提交、网络错误、支付结果自动刷新、主动查单和受限关单。当前后端仍未返回微信 `wx.requestPayment` 签名参数，本轮验证只证明小程序本地功能骨架和状态保护可用，不代表真实微信扣款。

## 640. 2026-09-23 refund-center 主列表分页收口

退款中心前端补齐退款单和退款成功事件出站两张主列表的服务端分页控件：显示页码与总数，首尾页和加载中禁用翻页，查询动作回到第一页；详情下钻继续保持独立读取，不与主列表页码混用。

验证：

```bash
cd systems/refund-center/frontend/admin-web
npm test
npm run build

cd ../../..
node tools/dev/frontend-admin-regression.mjs
```

结果：退款中心定向 `4/4` 通过并构建成功，全量管理端 `12/12` 通过。后端已有分页查询与总数契约，本轮只补齐页面操作闭环；不改变真实退款渠道、账务、对账或生产消息事实。

## 641. 2026-09-23 deposit-system 账户与流水分页闭环

为 deposit-system 的账户和资金流水主列表补齐真实分页契约：

- 后端新增统一 `PageResultDTO`，账户、流水查询接收 `pageNo/pageSize`，Mapper 增加分页与总数查询。
- 保留 overview、V2 预览、导出等原有不分页语义，避免把摘要和下载接口误切成第一页。
- 管理端接入分页响应归一化、页码控件和查询回第一页行为。

验证：

```bash
cd systems/deposit-system/backend
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home \
PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH \
/Users/abc123/workspace/apache-maven-3.9.12/bin/mvn -q test

cd ../frontend/admin-web
npm test
npm run build
```

结果：后端 `11/11` 通过，管理端 `3/3` 通过并构建成功。本轮仍只证明本地代码、H2 测试和前端构建可用，不代表生产数据库、真实渠道或正式发布验收。

## 642. 2026-09-23 受控权限下全栈运行态复验

使用本地已有 Maven 仓库和受控端口权限执行：

```bash
HSP_MAVEN_REPO=/Users/abc123/apache-maven-3.9.16/repository \
bash tools/dev/local-stack.sh verify all
```

结果：`12` 个后端、`15` 个前端及代理路径均为 `READY`，HTTP 冒烟通过；跨系统 happy-path 实际生成支付、清分、结算、账务、退款和对账事实，账务事件包含 `PAYMENT_SUCCESS`、`CLEARING_GENERATED`、`REFUND_SUCCESS`，对账事实包含支付成功、清分生成、结算创建、账务入账和退款成功，链路状态为 `COMPLETED`、完成阶段 `4/4`、`financialCloseable=true`。

该结果是当前 demo/H2/HTTP 受控本地运行态证据，不代表真实微信、支付宝、银行渠道、RabbitMQ、生产数据库、资金到账、财务关账或正式发布治理已完成。

## 643. 2026-09-23 本地全栈 Maven 仓库回退修复

修正 `tools/dev/local-stack.sh` 的 Maven 仓库解析：当显式配置、工具链相邻仓库和用户 Maven 仓库均不可用时，自动创建并回退到工作区 `.runtime/maven-repository`，避免仅因 `$HOME/.m2/repository` 缺失或不可写而阻断本地骨架启动。

验证：

```bash
bash -n tools/dev/local-stack.sh
HOME=/private/tmp/hsp-no-maven-home-$$ bash tools/dev/local-stack.sh doctor backends
git diff --check
```

结果：脚本语法、补丁检查和回退场景 `doctor backends` 均通过。该修复只改善本地工具链前置诊断，不改变 Maven 依赖来源、业务接口、数据库或支付资金状态。

## 644. 2026-09-23 后端全量回归 Maven 仓库解析统一

`tools/dev/backend-regression.mjs` 与 `local-stack.sh` 统一 Maven 仓库解析：支持显式 `MAVEN_REPO/HSP_MAVEN_REPO`，按相邻仓库、用户仓库和工作区 `.runtime/maven-repository` 回退，并向每个 Maven 测试进程显式传入 `-Dmaven.repo.local`。

验证：

```bash
HOME=/private/tmp/hsp-no-maven-home-$$ \
MAVEN_REPO=/Users/abc123/apache-maven-3.9.16/repository \
node tools/dev/backend-regression.mjs --only payment-core
```

首次执行暴露了 `--only payment-core` 选择器只支持完整目录的问题，已一并修正为支持系统名；修正后 payment-core 后端全量 `1188/1188` 通过，`0` failures、`0` errors、`0` skipped。本轮工具改动同时通过 `node --check` 与 `git diff --check`，不把仓库解析修复本身解释为新的渠道或生产证据。

补充验证时，全量回归在 `deposit-system` 暴露 JRE 缺少编译器的问题，未将中断前的模块汇总为全量通过；脚本已增加 JDK 解析和显式环境注入，需在修复后重新执行全量回归。

修复后重新执行全量回归时，已通过 payment-core、gateway-access、settlement、accounting，随后 clearing 的一个 Spring Context 用例因 H2 `t_clearing_batch already exists` 报错而中断；单独使用同一脚本和同一 JDK/Maven 仓库重跑 clearing-system 后 `36/36` 通过。该次全量仍不登记为通过，后续需再做一次完整串行回归确认。

## 645. 2026-09-23 后端统一回归最终收口

在 JDK 注入、Maven 仓库解析和系统名选择器修复后，重新执行：

```bash
HOME=/private/tmp/hsp-no-maven-home-$$ \
MAVEN_REPO=/Users/abc123/apache-maven-3.9.16/repository \
node tools/dev/backend-regression.mjs
```

结果：`12/12` 后端模块通过，合计 `1465/1465` 个测试，`0` failures、`0` errors、`0` skipped；本次包含 clearing-system `36/36` 和 deposit-system `11/11`。前一次全量中断和 clearing 定向复验均保留为历史诊断，不覆盖本次最终结果。

该结果证明统一回归脚本、JDK/Maven 工具链和本地 H2 测试基线可运行，不代表真实渠道、RabbitMQ、生产 MySQL、资金到账或正式发布治理完成。

## 646. 2026-09-23 管理端全量回归最终复核

在后端统一回归收口后再次执行：

```bash
node tools/dev/frontend-admin-regression.mjs
```

结果：`12/12` 管理端均通过各自测试与生产构建；payment-core 管理端保持 `49/49`，deposit `3/3`，clearing `5/5`，settlement `7/7`，其余管理端基线未回退。该结果只证明本地前端测试和构建可用，不替代真实浏览器现场核验、渠道、RabbitMQ 或正式发布治理证据。

## 647. 2026-09-23 受控权限下本地全栈运行态复验补充

使用本地已有 Maven 仓库执行：

```bash
HSP_MAVEN_REPO=/Users/abc123/apache-maven-3.9.16/repository \
bash tools/dev/local-stack.sh verify all
```

结果：`12` 个后端、`15` 个前端及代理路径均为 `READY`，HTTP 冒烟和跨系统 happy-path 通过；本批生成支付、清分、结算、账务、退款和对账事实，对账链路为 `COMPLETED`、完成阶段 `4/4`、`financialCloseable=true`。

本记录只证明当前 demo/H2/HTTP 本地骨架可运行，不证明真实微信/支付宝/银行渠道、RabbitMQ、生产 MySQL、资金到账、财务关账、正式审批、现场核验或稳定观察完成；REG-08 当前批次 DLQ 运行态证据仍待 Docker/RabbitMQ 恢复后重新采集。

## 648. 2026-09-23 payment-core 任务中心运行态页面核验

在 `http://127.0.0.1:5174/payment-task-center` 进行当前运行态检查：任务摘要、P1/P2/P3 严重等级口径、人工任务入口、任务执行日志和分页查询均完成加载；页面当前存在的待回调支付、失败退款事实异常和 SLA 超时项均按既有提示展示，未将它们误标为已收口。

在当前窄视口下测得 `innerWidth=462`、`documentElement.scrollWidth=447`、`body.scrollWidth=447`，无横向溢出，页面无 `role=alert` 错误节点。该检查只证明任务中心运营骨架与响应式布局当前可用，不替代真实 RabbitMQ/DLQ、通知供应商或发布治理证据。

## 649. 2026-09-23 支付处理日志空分页边界补强

发现 `PaymentLogServiceImpl.list()` 直接把 Mapper 返回列表交给分页响应，空库或持久化层返回 `null` 时不满足前端统一空数组契约。本轮新增 `safeList` 归一化，并补充 `PaymentLogServiceImplTest` 的 `null` 列表回归用例。

验证结果：定向日志服务测试 `9/9` 通过；payment-core 后端全量回归 `1/1` 模块通过。本轮不改变日志查询、导出或脱敏语义，也不代表生产数据库容量和发布治理已完成。

## 650. 2026-09-23 最新提交全栈复验

在最新 `test` HEAD 上再次执行：

```bash
HSP_MAVEN_REPO=/Users/abc123/apache-maven-3.9.16/repository \
bash tools/dev/local-stack.sh verify all
```

结果：`12` 个后端、`15` 个前端及代理路径均为 `READY`，HTTP 冒烟通过；跨系统 happy-path 生成支付、清分、结算、账务、退款和对账事实，对账链路为 `COMPLETED`、完成阶段 `4/4`、`financialCloseable=true`。本批次复验使用了受控环境中已存在的外部服务进程，脚本结束后未留下本次启动的服务进程。

该结果只证明最新代码在 demo/H2/HTTP 本地骨架下可运行，不代表真实渠道、RabbitMQ/DLQ、生产 MySQL、资金到账、财务关账、正式审批、现场核验或稳定观察完成。

## 651. 2026-09-23 支付单与支付记录详情契约收口

本轮将支付单详情不存在统一收口为 `PAYMENT_ORDER_NOT_FOUND`，并将支付单/支付记录详情中的路由、回调、事件轨迹 `NULL` 集合归一为空数组，避免后台详情页收到 `200 + data:null` 或不可迭代集合。

验证结果：`PaymentServiceImplTest 94/94`、`PaymentRecordServiceImplTest 6/6` 通过；随后重新执行 payment-core 后端全量回归，`1/1` 模块通过，`1194/1194` 测试通过，`0` failures、`0` errors、`0` skipped。该结果只证明本地 Java/H2 服务契约和回归基线，不代表真实渠道、生产数据库或发布治理已完成。

## 652. 2026-09-23 渠道服务空请求失败闭合

发现渠道下单/查单服务在适配器选择层直接读取请求字段；调用方传入 `null` 时会抛出裸 `NullPointerException`，无法保持 payment-core 统一错误码和脱敏异常语义。本轮在 `PaymentChannelSubmitServiceImpl` 与 `PaymentChannelQueryServiceImpl` 增加显式请求校验，分别返回 `PAYMENT_CHANNEL_SUBMIT_FAILED`、`PAYMENT_CHANNEL_QUERY_FAILED`，不调用适配器或返回码标准化器。

验证结果：渠道服务定向测试 `22/22` 通过；随后执行 `HSP_MAVEN_REPO=/Users/abc123/apache-maven-3.9.16/repository JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home node tools/dev/backend-regression.mjs --only payment-core`，`1/1` 模块通过，Surefire 汇总 `1196/1196`，`0` failures、`0` errors、`0` skipped。该结果只证明本地 Java/H2 错误边界和回归基线，不代表真实渠道、RabbitMQ/DLQ、生产数据库或发布治理已完成。

## 653. 2026-09-23 浏览器端请求超时边界补强

admin-web 与 app-web 的统一 API client 增加 `10000ms` 请求超时：请求由 `AbortController` 控制，超时统一映射为 `PAYMENT-CLIENT-TIMEOUT`，网络失败继续映射为 `PAYMENT-CLIENT-NETWORK`，并在 `finally` 中清理计时器；H5/PC 复用 app-web 客户端，因此不重复实现。新增 admin-web 与 app-web 超时错误回归用例。

验证结果：payment-core admin-web `51/51`、app-web `24/24` 测试通过，admin-web/app-web/h5-web/pc-web 生产构建通过，`git diff --check` 通过。本轮只改善用户端请求失败可解释性，不改变支付状态机、渠道协议或资金事实。

## 654. 2026-09-23 小程序原生支付参数兼容层

小程序收银台新增条件式原生支付拉起：提交响应包含完整 `timeStamp`、`nonceStr`、`package`、`signType`、`paySign` 时调用 `wx.requestPayment`，取消、失败和运行环境不支持分别映射稳定错误码；响应不含原生参数时继续进入本地 demo 结果页。后端提交结果 DTO 增加临时 `nativePaymentParams` 契约，由渠道结果边界校验完整性和长度，参数只随本次响应透传，不写入预付单。

验证结果：小程序 Node 测试 `17/17`、payment-core 后端全量回归 `1/1` 模块通过；admin-web `51/51`、app-web `24/24`、H5 `2/2`、PC `2/2`、API client `9/9` 通过，四端生产构建通过。当前本地适配器仍不返回真实微信签名参数，因此本批不声称真实微信扣款或渠道资金闭环完成。

## 655. 2026-09-23 预付单业务说明留痕

修复用户端业务入口填写说明后静默丢失的问题：`PrepayRequestDTO` 新增可选 `remark`，Web 三端、小程序入口和零依赖 API client 均会透传；payment-core 在预付单创建事务内将规范化说明写入现有不可变支付处理日志，使用 `PREPAY_ORDER` 来源和 `预付单创建` 阶段，不新增钱包、账务或订单状态职责。控制字符和长度边界沿用统一请求校验。

验证结果：payment-core 后端全量回归 `1/1` 模块通过；app-web `24/24`、小程序 `17/17`、API client `9/9` 测试通过，`git diff --check` 通过。本轮只补充支付创建事实可追溯性，不代表真实资金入账、钱包状态推进或生产审计权限已完成。

## 656. 2026-09-23 退款主动查单骨架口径与回归复核

本轮复核 `payment-core` 与 `gateway-access` 的退款主动查单链路，确认代码骨架已完整具备：payment-core 提供人工/自动查单、适配器选择、限流、失败历史和状态收口；gateway-access 提供 `POST /api/gateway/refunds/query` 代理，并复用应用、凭证、权限、渠道网关、证书和发布路由治理。仓库默认不配置真实渠道上游和访问凭证，非 demo 环境不会用本地状态伪造渠道结果。

验证结果：两个模块均 `1/1 backend modules passed`，失败数 `0`。本轮只证明本地 Java/H2/受控 HTTP 契约和错误边界可运行，不代表微信、支付宝、银行真实退款协议、签名验签、生产密钥、网络联通、退款到账、渠道对账或财务验收完成。

## 657. 2026-09-23 支付详情跨系统链路 ID 收口

为让支付详情能够直接下钻到已有 reconciliation 跨系统事实查询，本轮在 `PaymentDetailDTO` 增加 `businessChainId`。payment-core 按稳定规则从支付单号派生 `CHAIN-{paymentOrderId}`，管理端详情展示该值及其查询用途；不新增账务、清分、结算或对账写入职责。

验证：

```bash
MAVEN_REPO=/Users/abc123/apache-maven-3.9.16/repository \
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home \
node tools/dev/backend-regression.mjs --only payment-core

cd systems/payment-core/frontend/admin-web
npm test
npm run build
```

结果：`PaymentServiceImplTest` 定向回归通过；payment-core 后端 `1/1` 模块通过，Surefire 汇总 `1198/1198`，`0` failures、`0` errors、`0` skipped；payment-core admin-web `51/51` 测试通过并构建成功。该结果只证明本地 Java/H2 和管理端契约可用，不代表真实渠道、RabbitMQ/DLQ、生产数据库或正式发布治理完成。

## 658. 2026-09-23 支付详情直达对账链路诊断

支付详情页新增“跨系统链路”入口：按 `businessChainId` 打开 reconciliation 管理端，reconciliation 端从 URL 参数自动加载链路事实；手工输入查询仍保留。该入口只改善运营下钻，不新增 payment-core 对账写入职责。

验证：

```bash
cd systems/payment-core/frontend/admin-web
npm test
npm run build

cd ../../../reconciliation-system/frontend/admin-web
npm test
npm run build
```

结果：payment-core admin-web `53/53`、reconciliation admin-web `8/8` 测试通过，两端生产构建成功；`git diff --check` 通过。本地默认跳转地址为 `http://127.0.0.1:5180`，可用 `VITE_RECONCILIATION_ADMIN_BASE_URL` 覆盖。该结果不代表真实权限、生产域名、跨系统事实或发布治理完成。

## 659. 2026-09-23 支付监控指标字典与下钻入口

监控总览新增 `metricDefinitions` 指标字典，统一返回支付成功率、待收口支付、失败退款、停用渠道和 P1 告警的定义、单位、默认运营门槛、告警等级、责任方和处置入口；管理端新增指标口径表，可直接下钻到对应页面。默认门槛只用于当前骨架运营提示，不声称生产 SLO 已审批。

验证：

```bash
MAVEN_REPO=/Users/abc123/apache-maven-3.9.16/repository \
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home \
node tools/dev/backend-regression.mjs --only payment-core

cd systems/payment-core/frontend/admin-web
npm test
npm run build
```

结果：`PaymentMonitorServiceImplTest 3/3`、`PaymentMonitorControllerTest 2/2` 通过；payment-core 后端 `1/1` 模块通过，Surefire 汇总 `1198/1198`，`0` failures、`0` errors、`0` skipped；admin-web `53/53` 测试通过并构建成功。该结果不代表真实生产指标、外部告警送达、SLO 审批或发布治理完成。

## 660. 2026-09-23 本地回归基线报告脚本

新增 `tools/release/run_local_regression_report.mjs`，统一调用后端回归、管理端回归和 payment-core 门禁审计，并生成可重复的本地自动化基线报告。报告 JSON 只保留命令、状态、信号、错误、日志路径、Surefire/前端测试统计和门禁摘要；完整 stdout/stderr 写入同名 `.log` 文件，避免把大段日志重复嵌入报告。

验证：

```bash
MAVEN_REPO=/Users/abc123/apache-maven-3.9.16/repository \
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home \
node tools/release/run_local_regression_report.mjs \
  --only payment-core \
  --skip-frontend \
  --output .runtime/payment-core-current-regression.json
```

结果：payment-core 后端 `1198/1198` 通过，失败 `0`、错误 `0`、跳过 `0`；门禁摘要仍如实保留当前 evidence pack 的不可放行结论。该报告是本地自动化回归基线，不替代真实渠道、生产 MQ/MySQL、页面冻结、审批签字或发布后观察证据。

## 662. 2026-09-23 本地回归报告纳入用户端

扩展 `tools/release/run_local_regression_report.mjs`：当范围为全部模块或 `--only payment-core` 时，默认追加 `tools/dev/payment-core-user-regression.mjs`，将 API client、App、H5、PC、小程序的测试与构建结果写入 `userFrontend` 摘要；`--skip-user-frontend` 可显式跳过。非 payment-core 的 `--only` 范围不会误跑用户端。

验证：

```bash
MAVEN_REPO=/Users/abc123/apache-maven-3.9.16/repository \
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home \
node tools/release/run_local_regression_report.mjs \
  --only payment-core \
  --skip-frontend \
  --skip-gate \
  --output .runtime/payment-core-full-local-regression.json
```

结果：payment-core 后端 `1198/1198` 通过，5 个用户端 `5/5` 通过，用户端测试合计 `54`；App/H5/PC 构建通过，小程序和 API client 按无构建脚本规则记录为 accepted。该结果仍只代表本地自动化基线，不代表真实渠道、生产资金或发布放行。

## 663. 2026-09-23 全量前端回归纳入统一报告

执行：

```bash
node tools/release/run_local_regression_report.mjs \
  --skip-backend \
  --output .runtime/payment-core-full-frontend-regression.json
```

结果：管理端 `12/12` 工程通过测试与生产构建，测试合计 `120` 个；payment-core 用户端 `5/5` 工程通过，测试合计 `54` 个。报告同时保留当前 payment-core 门禁摘要，门禁仍按现有证据包返回不可放行结论；前端自动化通过不替代真实浏览器、微信开发者工具、渠道或发布证据。

## 664. 2026-09-23 DLQ 重放结果状态提示修复

本轮修复 payment-core 管理端死信补偿任务页面的结果提示边界：服务端返回 `REPLAY_FAILED` 时，页面显示重放失败并要求排查消息代理与消费者，列表刷新后仍保留该提示；只有返回 `REPLAYED` 时才显示消息发布确认，同时继续提示运营核对目标系统。该改动不改变后端重放状态机，也不把发布确认解释为下游消费成功。

验证：

```bash
cd systems/payment-core/frontend/admin-web
npm test
npm run build

node tools/dev/frontend-admin-regression.mjs
node tools/dev/payment-core-user-regression.mjs
node tools/release/audit_payment_core_gate.mjs \
  --root-dir /Users/abc123/workspace/home-service-payment-system \
  --pack /Users/abc123/workspace/home-service-payment-system/docs/releases/evidence/test-20260809-regression \
  --stdout-json
```

结果：payment-core admin-web `56/56` 测试通过并构建成功；全量管理端 `12/12`、用户端 `5/5` 均通过既有测试与构建检查。当前门禁仍为不可放行：REG-08 缺当前批次页面冻结截图和补偿/replay/DLQ 闭环证据；release 仍缺正式签字审批、发布后现场核验和 master 稳定观察结束结论。本轮代码回归不替代这些真实治理事实。

## 665. 2026-09-23 payment-core 后端全量回归复核

本轮使用仓库约定的 Maven 3.9.16 与 JDK 8 环境重新执行 payment-core 后端全量测试，覆盖当前提交后的全部 Java 测试类。

```bash
HSP_MAVEN_REPO=/Users/abc123/apache-maven-3.9.16/repository \
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home \
PATH=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/bin:$PATH \
/Users/abc123/apache-maven-3.9.16/bin/mvn \
  -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository \
  -q -f systems/payment-core/backend/pom.xml test
```

结果：Surefire 汇总 `1199/1199`，`0` failures、`0` errors、`0` skipped。测试日志中的异常均来自既有失败分支、事务回滚和敏感信息脱敏测试，进程最终退出码为 `0`。该结果证明当前本地 Java/H2 骨架回归通过，不代表真实渠道、生产 MQ/MySQL、资金到账或 release 治理条件完成。

## 666. 2026-09-23 支付提交空请求入口校验

补强 `POST /api/payments/submit` 的空请求体边界：控制器不再在补充客户端 IP 和终端信息前直接解引用请求对象，而是将 `null` 原样交给 `PaymentService` 的统一提交参数校验，避免 malformed JSON `null` 被转换为控制器 NPE 和非预期 500。正常请求的可信代理 IP 与终端解析路径保持不变。

验证：新增 `PaymentControllerTest.shouldDelegateNullSubmitBodyToServiceValidation`，并重跑 payment-core 后端全量测试，Surefire `1199/1199` 全部通过。

## 667. 2026-09-23 退款详情不存在错误码收口

审查退款运营详情接口时发现，`GET /api/refunds/{refundOrderId}` 在退款单不存在时抛出普通 `IllegalArgumentException`，会被统一异常处理器映射为通用校验错误 `PAYMENT-400`；同一模块已有 `REFUND_ORDER_NOT_FOUND (PAYMENT-1044)` 业务错误码，查询和其他详情入口应保持一致。

本轮将 `RefundServiceImpl.detail` 的不存在分支改为 `BusinessException(REFUND_ORDER_NOT_FOUND, "退款单不存在")`，并新增服务层回归用例，确认不存在详情不会继续查询操作日志或退款尝试。

验证结果：`RefundServiceImplTest` 定向回归通过；随后执行 payment-core 后端全量 Maven 回归，Surefire `1200/1200` 通过，`0` failures、`0` errors、`0` skipped。该修复只统一退款详情错误契约，不改变退款状态机、渠道协议、到账事实或发布门禁。

## 668. 2026-09-23 请求绑定错误统一返回校验码

补强全局请求契约：空请求体、非法 JSON、缺失请求参数、路径参数缺失以及分页参数类型错误现在统一返回 `PAYMENT-400`，不再被通用异常处理器误判为 `PAYMENT-500`。响应结构保持 `code/message/data/requestId` 不变，便于各端稳定区分用户输入错误与系统故障。

新增 `GlobalExceptionHandlerTest` 覆盖空/不可读请求体和参数类型绑定失败场景。随后使用仓库内 Maven 3.9.16、JDK 8 和本地依赖仓库执行 payment-core 全量回归，Surefire 汇总 `1202/1202`，`0` failures、`0` errors、`0` skipped。

## 669. 2026-09-23 当前批次门禁复核

重新执行 `node tools/release/audit_payment_core_gate.mjs --root-dir /Users/abc123/workspace/home-service-payment-system --pack /Users/abc123/workspace/home-service-payment-system/docs/releases/evidence/test-20260809-regression --stdout-json`。代码回归证据本身完整，但当前批次 `REG-08` 仍缺同批次页面冻结截图和补偿/replay/DLQ 闭环证据；因此 `test -> master=false`、`canCreateRelease=false`。release 仍缺正式版发布说明、签字审批、发布后现场核验和 master 稳定观察结束结论。本轮不使用本地测试结果替代这些真实治理证据。

## 661. 2026-09-23 最新 HEAD 全套本地骨架验收

在当前 `test` HEAD 上执行：

```bash
HSP_MAVEN_REPO=/Users/abc123/apache-maven-3.9.16/repository \
bash tools/dev/local-stack.sh verify all
```

结果：12 个后端、15 个前端及全部代理路径均为 `READY`，HTTP 冒烟通过；跨系统 happy-path 生成支付、清分、结算、账务、退款和对账事实，对账链路为 `COMPLETED`，完成阶段 `4/4`，`financialCloseable=true`。本次验证只证明受控 demo/H2/HTTP 本地骨架在最新代码上可运行，不代表真实渠道、RabbitMQ/DLQ、生产 MySQL、资金到账、正式审批、现场核验或 release 放行完成。

## 670. 2026-09-23 工作台接入任务中心实时待办

工作台“今日重点事项”不再使用固定的关单和结算文案，改为读取已有 `GET /api/payment-task-center/overview` 的真实积压数据，按待回调支付、主动查单、SLA 异常、失败事件/死信、失败退款、日终告警和控制策略自检生成可下钻队列。任务中心读取失败时，页面明确提示并降级到支付指标，不把降级结果伪装成实时待办。

验证：新增 `dashboardFocus.test.js` 3 个用例；payment-core admin-web `59/59` 测试通过、生产构建通过；随后执行 `node tools/dev/frontend-admin-regression.mjs`，管理端 `12/12` 工程测试与构建全部通过。该改动只改善运营工作台的实时待办和下钻体验，不替代真实告警通道、值班责任绑定或发布证据。

## 671. 2026-09-23 工作台待办口径与当前门禁复核

修正工作台待办聚合口径：任务中心已返回的 `0` 不再被支付指标旧值覆盖；重叠字段（失败事件/死信、失败退款/待查单、SLA 总数/告警候选）按最大值收敛，避免把同一积压重复计数。重新执行 gate audit 后，`test -> master=false`、`canCreateRelease=false` 仍保持不变，阻断仍是 REG-08 当前批次真实证据和 release 正式治理事实。

## 672. 2026-09-23 API 调用端补齐退款操作骨架

`systems/payment-core/frontend/api-client` 新增五个零依赖命令：`refund-apply`、`refund-detail`、`refund-approve`、`refund-query` 和 `refund-retry`，分别覆盖退款申请、详情读取、审核、主动查单和失败重试。命令沿用已有统一 JSON 响应、10 秒超时、网络错误归一化和路径参数编码规则；默认幂等键只用于本地联调，操作人仍必须由调用方显式传入，不能替代可信身份或真实退款渠道事实。

验证：API client `npm test` 共 `10/10` 通过，新增审核命令契约断言，`git diff --check` 通过。本轮不改变 payment-core 后端退款状态机、真实渠道协议、退款中心职责或发布门禁结论。

## 673. 2026-09-23 API 调用端变更后的门禁复核

本轮重新执行 payment-core gate audit：本地代码与接口测试不影响现有治理判断，`test -> master=false`、`canCreateRelease=false` 仍保持不变。当前阻断仍为 REG-08 缺当前同批次页面冻结截图和补偿/replay/DLQ 闭环证据，以及 release 正式发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论。

## 674. 2026-09-23 本地全栈 Maven 探测修复

修复 `tools/dev/local-stack.sh` 的默认工具链探测：先校验 `command -v mvn` 返回路径确实可执行，并优先选择当前可用的 Maven 3.9.16 与对应本地仓库，避免 PATH 中残留旧 Maven 路径导致启动脚本误选或等待超时。显式传入 `HSP_MAVEN_BIN`、`HSP_MAVEN_REPO` 的行为保持不变。

验证：`bash -n tools/dev/local-stack.sh`、`git diff --check` 通过；受控本机执行 `bash tools/dev/local-stack.sh doctor payment-core` 返回 Maven 3.9.16、JDK 和依赖检查通过。本轮只改善本地运行骨架启动探测，不改变业务状态机、真实渠道或发布门禁。

## 675. 2026-09-23 启动修复后的本地全栈复验

在当前 `test` HEAD 上执行：

```bash
bash tools/dev/local-stack.sh smoke all
node tools/dev/cross-system-happy-path.mjs
```

结果：12 个后端、15 个前端及代理路径全部 `READY`，HTTP 冒烟通过；跨系统 happy-path 生成支付、清分、结算、账务、退款和对账事实，对账链路为 `COMPLETED`、完成阶段 `4/4`、`financialCloseable=true`。该结果只证明受控 demo/H2/HTTP 骨架可运行，不代表真实渠道、RabbitMQ/DLQ、生产 MySQL、资金到账或正式发布治理完成。

## 676. 2026-09-23 本地全栈复验后的门禁复判

重新执行 payment-core gate audit 后，`test -> master=false`、`canCreateRelease=false` 保持不变。当前回归包只有 REG-08 仍缺当前批次页面冻结截图和补偿/replay/DLQ 闭环证据；release 仍缺正式候选发布说明、签字审批、发布后现场核验和 master 稳定观察结束结论。本地全栈通过不替代这些真实治理事实。

## 677. 2026-09-23 API client 退款审核命令后的门禁复判

API client 新增 `refund-approve` 后重新执行 gate audit，门禁结论无变化：`test -> master=false`、`canCreateRelease=false`。代码层退款申请、审核、查单和重试命令不构成真实退款到账、财务审批或发布治理证据；现有 REG-08 与 release 四项真实阻断继续保留。

## 678. 2026-09-23 支付后台系统总览入口

为让本地功能骨架可以从一个后台入口继续使用，payment-core 管理端新增“系统总览”页面，集中列出 12 个管理后台和 App/H5/PC 3 个用户端，并按支付核心、资金域、平台治理分组。入口地址默认对应本地栈端口，同时支持通过 `VITE_*_BASE_URL` 环境变量替换为其他环境地址；每个入口均以新窗口打开，保留当前运营后台上下文。

同时补齐管理端测试脚本对 `src/constants/*.test.js` 的收集，系统目录契约测试不再被测试命令遗漏。

验证：payment-core admin-web `61/61` 测试通过、生产构建通过；全量管理端 `12/12` 工程测试与构建通过；`git diff --check` 通过。该入口只解决本地骨架可达性，不代表统一身份、跨系统权限或生产域名配置已经完成。

## 679. 2026-09-23 后端回归 Maven 探测统一

修复 `tools/dev/backend-regression.mjs` 与 `tools/dev/local-stack.sh` 的工具链偏差：回归脚本现在同时接受 `MAVEN_BIN/HSP_MAVEN_BIN`、`JAVA_HOME/HSP_JAVA_HOME` 和 `MAVEN_REPO/HSP_MAVEN_REPO`，并优先探测 Maven 3.9.16 及其本地仓库，避免 PATH 或旧目录残留导致回归误选 Maven 3.9.12。

验证：`node --check tools/dev/backend-regression.mjs` 通过；使用 Maven 3.9.16、JDK 8 和对应本地仓库执行 `node tools/dev/backend-regression.mjs --only payment-core`，`1/1` 模块通过，Surefire 全量测试 `1202/1202`，失败 `0`、错误 `0`、跳过 `0`。该改动只统一本地验收工具链，不改变业务状态机、真实渠道或发布门禁。

## 680. 2026-09-23 系统总览运行态核验

在原任务窗口持续托管的本地栈中，用受控环境检查 `http://127.0.0.1:5174/system-directory` 和 `http://127.0.0.1:18081/api/dashboard/summary`，页面入口与 payment-core API 均返回 HTTP `200`。该核验确认系统总览不是只有静态构建产物，而是能在当前运行栈中被访问；不代表其他环境域名、统一身份或生产服务可用。

## 681. 2026-09-23 跨系统主链路复核

在同一持续托管栈中执行 `node tools/dev/cross-system-happy-path.mjs`，本地链路重新生成支付、支付成功事件、清分、结算、账务、退款和对账事实。结果为：对账链路 `COMPLETED`，完成阶段 `4/4`，`financialCloseable=true`；账务事件包含 `PAYMENT_SUCCESS`、`CLEARING_GENERATED`、`REFUND_SUCCESS`，对账事实包含 `PAYMENT_SUCCESS`、`CLEARING_GENERATED`、`SETTLEMENT_CREATED`、`ACCOUNTING_POSTED`、`REFUND_SUCCESS`。该结果只证明本地 H2/HTTP 骨架跨系统可联调，不代表真实消息代理、资金到账、生产数据库或财务审批。

## 682. 2026-09-23 当前批次门禁复判

重新执行 payment-core gate audit：`test -> master=false`、`canCreateRelease=false`。当前回归包仍缺 REG-08 同批次页面冻结截图和补偿/replay/DLQ 闭环证据；release 仍缺正式候选发布说明、正式签字审批、发布后现场核验和 master 稳定观察结束结论。本轮本地代码、页面构建和跨系统 happy-path 均不替代这些真实治理事实。

## 683. 2026-09-23 支付成功事件版本化载荷契约

支付成功事件出站的 HTTP 与 AMQP 两种模式统一补齐 `eventNo`、`eventType`、`schemaVersion` 三个追踪字段；AMQP 消息头同步携带 `schemaVersion`，继续以 `eventNo` 作为 `messageId/correlationId`。清分和账务的支付成功请求 DTO 显式声明这些字段，保留旧业务字段和历史消费者兼容行为。

本轮新增定向断言覆盖 AMQP JSON 载荷和消息头。该变更只收口本地事件契约和跨系统排障字段，不代表真实 RabbitMQ 消费、DLQ 闭环、下游资金事实或发布治理完成。

## 684. 2026-09-23 版本化事件契约回归与门禁复判

验证结果：payment-core 定向事件派发测试 `54/54` 通过；payment-core 全量 Maven 回归 `1202/1202` 通过；清分、账务全量测试均通过；`git diff --check` 通过。原托管栈当前未提供可访问 HTTP 端口，本轮跨系统 happy-path 返回 `fetch failed`，未将其写成本批次运行态成功证据。

重新执行统一门禁审计，结果仍为 `test -> master=false`、`canCreateRelease=false`。审计明确 REG-08 仍缺当前同批次页面冻结截图和补偿/replay/DLQ 闭环证据，release 仍缺候选说明正式化、正式签字审批、发布后现场核验和 master 稳定观察结束结论；本轮代码回归不替代这些真实治理事实。

## 685. 2026-09-23 支付数据库基线 schema 对齐

发现 `systems/payment-core/database/schema.sql` 与应用资源 schema 存在实际交付缺口：缺少 `t_payment_expiry_close_retry`，且把新建支付单尚未产生的 `channel_transaction_no` 设为 `NOT NULL`。本轮补齐数据库基线表、可执行增量迁移 `2026-09-23-align-payment-schema-baseline.sql`，并新增 schema 契约测试，确保应用 schema、交付 schema 和迁移保持一致。

验证结果：schema 契约定向测试通过；payment-core 全量 Maven 回归 `1203/1203`，失败 `0`、错误 `0`、跳过 `0`；`git diff --check` 通过。本轮只修复数据库建库/迁移基线，不代表迁移已在目标生产 MySQL 执行，也不改变真实渠道、资金或发布门禁结论。

## 686. 2026-09-23 支付成功事件消费者版本校验

清分和账务 AMQP 支付成功消费者新增事件信封校验：显式携带的 `eventType` 必须为 `PAYMENT_SUCCESS`、`schemaVersion` 必须为 `1`，消息头中的事件类型、schema 版本和 `messageId` 与 JSON 载荷不一致时进入既有 retry/DLQ 路径；历史消息缺少新增字段时继续兼容消费。新增清分 schema 版本错误、账务事件类型错误用例，覆盖错误消息不进入业务服务。

验证结果：清分定向 `6/6`、账务定向 `5/5`；清分全量 `38/38`、账务全量 `45/45`，失败 `0`、错误 `0`。该校验只增强本地消费者契约和异常闭合，不代表真实 RabbitMQ/DLQ、下游资金事实或发布治理完成。

## 687. 2026-09-23 文档验证基线校正

同步修正 `payment-core/README.md` 与前端 README 的交付口径：payment-core 后端全量基线更新为 `1203` 个测试通过，API client 说明补齐退款申请、审核、详情、主动查单和失败重试命令；并明确版本化支付成功事件消费者已完成本地契约校验。该变更只校正文档与当前代码事实，不新增生产渠道、身份权限或发布治理结论。

## 688. 2026-09-23 支付成功事件消息头边界回归

新增清分消息头 `eventType` 不一致、账务消息头 `schemaVersion` 不一致两条回归用例，验证错误信封不进入业务服务，并按既有 retry 路由确认原消息。定向结果为清分 `6/6`、账务 `5/5`，全量结果为清分 `38/38`、账务 `45/45`，失败 `0`、错误 `0`；本轮不改变 payment-core `1203/1203` 全量基线或真实 broker 结论。

## 689. 2026-09-23 清分生成事件版本化信封补齐

清分系统的 `CLEARING_GENERATED` outbox 载荷现在显式带 `eventNo`、`eventType=CLEARING_GENERATED`、`schemaVersion=1`；AMQP 消息头同步带 schema 版本，并继续以事件号作为 `messageId/correlationId`。账务和结算 AMQP 消费者复用支付成功事件的兼容校验策略：显式信封字段或消息头不支持、互相不一致时不进入业务服务，继续进入既有 retry/DLQ 路径；历史缺少新增字段的清分消息保持兼容消费。

验证结果：账务定向 `5/5`、结算定向 `5/5`、清分派发定向 `5/5`；账务后端全量 `47/47`、结算后端全量 `38/38`、清分后端全量 `38/38`，失败 `0`、错误 `0`。本地持续托管栈当前 12 个后端、15 个前端及代理路径均为 `READY`，但这些结果只证明本地代码、H2/demo 和 HTTP 骨架可运行，不代表真实 RabbitMQ/DLQ、生产数据库、资金事实或发布治理已完成。

## 690. 2026-09-23 DLQ 补偿工具合同复核与门禁现状

在受控回环监听权限下执行 `node --test tools/dev/dlq-compensation-smoke.test.mjs`，7/7 通过，覆盖 payment.success 与 clearing.generated 当前批次重放合同、人工结案、业务单号透传、事件类型校验、非法超时和畸形 JSON 失败；该测试使用 mock 管理 API，不声称 RabbitMQ 已发布、死信任务已入账或下游事实已恢复。

当前 `node tools/release/audit_payment_core_gate.mjs --root-dir ... --pack ... --stdout-json` 仍判定 `test -> master=false`、`canCreateRelease=false`：REG-08 缺当前同批次页面冻结和真实 DLQ/replay/下游事实，release 缺候选说明正式化、正式签字、发布后现场核验和 master 稳定观察结束结论。Docker daemon 当前不可连接，因此本轮不填充这些真实治理证据。

## 691. 2026-09-23 提交后 payment-core 全量回归

在提交 `5b84f736` 后，以 Maven 3.9.16、JDK 8 和已有本地依赖仓库执行 `HSP_MAVEN_REPO=/Users/abc123/apache-maven-3.9.16/repository HSP_JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home node tools/dev/backend-regression.mjs --only payment-core`，Surefire 汇总为 `1203/1203`，失败 `0`、错误 `0`、跳过 `0`。该结果证明当前 payment-core 代码基线可回归，不改变 REG-08、真实 RabbitMQ、生产数据库、渠道资金或 release 治理结论。

## 692. 2026-09-23 持续托管栈页面运行态核验

在原任务窗口持续托管的本地栈中，对管理端工作台、死信补偿任务页和 App 余额支付入口进行浏览器可访问性核验：

- `http://127.0.0.1:5174/dashboard`：工作台指标、今日重点事项、快捷下钻入口正常渲染；任务中心聚合失败时有明确降级提示语义。
- `http://127.0.0.1:5174/payment-dead-letter-tasks`：筛选项、空列表态、重放边界提示和“重放不等于下游完成”的风险说明正常渲染。
- `http://127.0.0.1:5175/balance-pay`：业务入口、客户/金额/业务号表单、创建预付单入口、重置入口和后续真实账户域边界说明正常渲染。

本轮只证明当前受控本地栈的页面可达性、主要空态和入口骨架可用，不代表真实身份权限、真实渠道受理、RabbitMQ/DLQ 消费、资金到账或生产视觉验收完成。当前门禁仍以统一审计为准：`test -> master=false`、`canCreateRelease=false`；REG-08 仍缺当前批次真实页面冻结及补偿/replay/DLQ/下游闭环，release 仍缺正式发布说明、正式签字、发布后现场核验和 master 稳定观察结束结论。

## 693. 2026-09-23 全系统后端回归复核

在当前 `test` HEAD 上使用 Maven 3.9.16、JDK 8 和已有本地依赖仓库执行：

```bash
HSP_MAVEN_REPO=/Users/abc123/apache-maven-3.9.16/repository \
HSP_JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home \
node tools/dev/backend-regression.mjs
```

结果：`12/12` 个后端模块通过，Surefire 汇总 `1489/1489`，`0` failures、`0` errors、`0` skipped。当前模块明细为：payment-core `1203`、gateway-access `51`、settlement-system `38`、accounting-system `47`、clearing-system `38`、refund-center `17`、reconciliation-system `14`、risk-control-system `11`、ops-config-system `12`、deposit-system `11`、wallet-account `23`、wallet-system `24`。

该结果只更新本地 Java/H2 自动化基线，证明事件契约收口后各后端模块未出现自动化回归；不替代真实 RabbitMQ/DLQ、生产 MySQL、多实例调度、真实渠道受理、资金到账或发布治理事实。统一门禁仍保持 `test -> master=false`、`canCreateRelease=false`。

## 694. 2026-09-23 前端全量回归与生产构建复核

在同一 `test` HEAD 上执行管理端与 payment-core 用户端回归脚本，结果如下：

- 管理端 `12/12` 个工程均通过 `npm test` 与 `npm run build`；payment-core `admin-web` 为 `61/61`。
- payment-core 用户端 `5/5` 通过：App `24/24`、H5 `2/2`、PC `2/2`、小程序 `17/17`、API client `10/10`。
- App、H5、PC 的 Vite 生产构建均通过；小程序和 API client 按包声明执行测试，无独立 build 脚本。

该结果证明当前页面路由、API 边界、错误归一化、业务入口和前端构建基线未因本轮跨系统事件契约收口而回归；不替代真实浏览器多端人工验收、微信开发者工具、可信身份权限、真实渠道签名或生产域名验证。

## 695. 2026-09-23 App/H5/PC 用户端退款进度骨架

对照用户端 README 与当前路由矩阵，发现 `app-web` 已声明承接“用户退款进度页”，但 App、H5、PC 路由实际只有业务入口、收银台和支付结果页。本轮补齐：

- 共享路由常量和编码安全的 `refund-progress/:refundOrderId` 路由构造器；App、H5、PC 均注册该页面。
- App 权威视图调用只读 `GET /api/refunds/{refundOrderId}`，展示退款状态、金额、原支付/订单、渠道退款单号、操作日志和渠道尝试轨迹。
- 处理中状态最多自动刷新 5 次；手动刷新只读详情，不提供用户端审核、主动查单或失败重试入口，避免越过退款中心和运营职责边界。
- 不存在退款单时沿用统一业务错误响应，页面展示可理解的失败态，不白屏、不把后台动作伪装成用户操作。

验证：App `26/26` 测试、H5 `2/2` 测试、PC `2/2` 测试通过；三端生产构建通过；`git diff --check` 通过；临时 Vite 运行态访问 `/refund-progress/REF-DEMO` 正常渲染并展示 `PAYMENT-1044` 不存在错误态。当前记录只证明本地前端路由、只读接口边界和受控浏览器运行态可用，不代表真实身份权限、退款到账、渠道通知或生产域名验收完成。

## 696. 2026-09-23 微信小程序退款进度骨架

为避免用户端退款查询在小程序缺失，本轮把同一只读能力补到原生小程序：

- 新增 `pages/refund-progress/index`，通过 `GET /api/refunds/{refundOrderId}` 展示退款状态、退款金额、原支付/订单、渠道退款单号、操作日志和渠道尝试轨迹。
- 处理中状态最多自动刷新 5 次；手动刷新只更新当前退款单快照，严格校验返回的 `refundOrderId`，响应串单或不存在时失败闭合。
- 页面只提供状态查询和返回业务入口，不提供审核、主动查单或失败重试，继续保持退款中心与运营后台的职责边界。

验证：小程序测试 `20/20` 通过，覆盖详情加载、串单响应拒绝和点击事件触发的手动刷新；`git diff --check` 通过。当前记录只证明小程序本地页面模型和受控 API 客户端边界可运行，不代表微信开发者工具/真机、真实身份、渠道退款到账或生产合法域名验收完成。

## 697. 2026-09-23 后台退款详情接入用户进度入口

后台退款详情新增“打开用户退款进度”入口，沿现有外部收银台 URL 规则构造 `VITE_APP_WEB_BASE_URL/refund-progress/{refundOrderId}`，对协议、路径和退款单号进行统一校验与编码；入口只打开用户端只读页面，不把审核、主动查单或失败重试动作暴露给用户端。

验证：admin-web 外部 URL 契约测试新增退款单号编码、默认地址和空值拒绝用例；随后 admin-web 测试与生产构建通过，`git diff --check` 通过。该入口只改善本地多端骨架可达性，不代表统一身份、跨域策略、真实退款到账或生产域名配置已验收。

## 698. 2026-09-23 可运行骨架阶段口径校正

本轮按“先把架子搭起来、每个入口能打开并完成基本操作，再补生产化细节”的阶段目标复核 payment-core。代码事实已确认：管理端系统总览可到达 12 个后台、App/H5/PC 用户端，并提供小程序工程与 API client 联调入口；App/H5/PC/小程序已覆盖业务入口、收银台、支付结果和退款进度；API client 覆盖支付与退款的服务调用骨架。同步修正文档中早期“小程序未实现”和“用户端只有收银台/结果页”的旧口径。

验证：admin-web `63/63`、App `26/26`、H5 `2/2`、PC `2/2`、小程序 `20/20` 通过，`git diff --check` 通过。真实微信/支付宝/银行协议、可信身份权限、生产数据库与 MQ、资金到账、通知送达、现场核验和发布审批仍属于后续阶段，不能由本地 demo 或前端测试替代。

## 699. 2026-09-23 本地回归工具复用 Maven 缓存

本轮修复 `tools/dev/local-stack.sh` 和 `tools/dev/backend-regression.mjs` 的本地 Maven 仓库选择：在工作区运行时仓库为空或不可写、但机器已有可读依赖缓存时，优先复用该缓存；本地栈后端/前端启动失败或超时时会清理对应 PID 文件，避免留下 stale 状态。

验证：`bash -n tools/dev/local-stack.sh`、`node --check tools/dev/backend-regression.mjs`、`tools/dev/local-stack.sh doctor backends` 通过；payment-core 后端回归 `1/1` 模块、`1203/1203` 测试通过。当前沙箱仍禁止普通进程监听本地端口，未将受限环境下的 `EPERM` 误记为应用失败；真实端口验证需在允许本地监听的托管环境执行。

## 700. 2026-09-23 全系统后端回归复核

使用修复后的统一 Maven 仓库探测执行 `node tools/dev/backend-regression.mjs`，12 个后端模块串行通过，Surefire 汇总 `1489/1489`，`0` failures、`0` errors、`0` skipped。模块明细为：payment-core `1203`、gateway-access `51`、settlement-system `38`、accounting-system `47`、clearing-system `38`、refund-center `17`、reconciliation-system `14`、risk-control-system `11`、ops-config-system `12`、deposit-system `11`、wallet-account `23`、wallet-system `24`。

该结果证明回归工具链和当前 Java/H2 代码基线可重复执行，不替代真实 RabbitMQ/DLQ、生产 MySQL、多实例调度、真实渠道受理、资金到账或发布治理证据；统一门禁仍保持 `test -> master=false`、`canCreateRelease=false`。

## 701. 2026-09-23 系统总览接入端目录补齐

为让“可运行骨架”覆盖实际联调入口，系统总览目录由 15 项扩展为 17 项：12 个管理后台、3 个浏览器用户端、1 个微信小程序工程入口和 1 个 API client 联调入口。浏览器入口继续以 URL 打开；小程序显示工程路径，API client 显示可直接执行的测试/联调命令，避免把非浏览器工程伪装成可点击网页。

验证：`systemCatalog` 契约测试与 payment-core admin-web 测试共 `63/63` 通过，生产构建通过；API client `10/10` 通过；`git diff --check` 通过。该目录只改善本地骨架发现和联调可达性，不代表微信开发者工具、统一身份权限、真实渠道或生产域名已验收。

## 702. 2026-09-24 退款中心 MySQL 兼容与失败补偿回归

退款中心补齐支付成功事实投影的 MySQL 幂等 upsert、共享 schema 的跨 H2/MySQL 载荷类型，并在管理端详情页提供失败回调到失败重试的可操作路径。定向结果为退款中心后端 `18/18`、管理端 `6/6`，生产构建通过；随后执行全量后端回归，`12/12` 模块通过，Surefire 汇总 `1490/1490`，失败 `0`、错误 `0`、跳过 `0`。

本轮仍只证明本地 H2/HTTP 和前端构建骨架可回归；真实 MySQL 实例、渠道回调验签、RabbitMQ relay、生产资金到账和发布治理证据仍未形成，不能据此推进 `test -> master` 或 `master -> release/*`。

## 703. 2026-09-24 当前 HEAD 全栈可运行骨架复验

在原任务窗口持续托管的本地栈中执行：

```bash
HSP_MAVEN_REPO=/Users/abc123/apache-maven-3.9.16/repository \
bash tools/dev/local-stack.sh verify all
```

结果：12 个后端、15 个前端及代理路径全部 `READY`，HTTP 冒烟通过；跨系统 happy-path 生成支付、清分、结算、账务、退款和对账事实，对账链路为 `COMPLETED`、完成阶段 `4/4`、`financialCloseable=true`。

同批次端侧回归结果：payment-core 后端 `1203/1203`，admin-web `63/63`，App `26/26`，H5 `2/2`，PC `2/2`，小程序 `20/20`，API client `10/10`；全量管理端 `12/12` 测试与生产构建通过。

本轮只证明当前 demo/H2/HTTP 骨架可持续运行和各端契约可回归，不代表真实渠道、RabbitMQ/DLQ、生产 MySQL、资金到账、可信身份、正式审批、现场核验或发布门禁完成；`test -> master` 与 `master -> release/*` 仍以统一审计和真实证据为准。

## 704. 2026-09-24 死信补偿任务定向回归

在原任务窗口持续托管栈保持运行期间，以 Maven 3.9.16、JDK 8 和已有本地依赖仓库执行 payment-core 死信补偿定向回归：服务 `PaymentDeadLetterTaskServiceImplTest`、控制器 `PaymentDeadLetterTaskControllerTest`、AMQP 监听器 `PaymentDeadLetterTaskAmqpListenerTest` 合计 `26/26` 通过，失败 `0`、错误 `0`、跳过 `0`。

本轮只复核死信入账、脱敏、人工复核、人工结案、定向重放、发布确认、失败闭合和路由监听等可运行骨架，不代表真实 RabbitMQ/DLQ 送达、下游消费、资金事实或当前批次 REG-08 页面冻结证据已形成。统一门禁审计仍保持 `test -> master=false`、`canCreateRelease=false`。

## 705. 2026-09-24 当前 HEAD payment-core 全量回归

在提交 `87efd61f` 后，以 Maven 3.9.16、JDK 8 和已有本地依赖仓库执行 `HSP_MAVEN_REPO=/Users/abc123/apache-maven-3.9.16/repository HSP_JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home node tools/dev/backend-regression.mjs --only payment-core`，payment-core `1/1` 模块通过，Surefire 汇总 `1203/1203`，失败 `0`、错误 `0`、跳过 `0`。

该结果确认当前 payment-core 代码与已有事件、任务、配置、查询、边界回归基线未漂移；测试日志中的异常样例均为预期失败闭合路径，不代表真实 RabbitMQ/DLQ、生产 MySQL、真实渠道受理、资金到账或发布治理条件已完成。

## 706. 2026-09-24 当前 HEAD 前端矩阵回归

在同一 `test` HEAD 上执行管理端与 payment-core 用户端回归脚本：全量管理端 `12/12` 个工程均通过 `npm test` 与 `npm run build`；payment-core 用户端 App `26/26`、H5 `2/2`、PC `2/2`、小程序 `20/20`、API client `10/10` 通过，App/H5/PC 生产构建通过。

该结果确认当前管理端目录、用户端业务入口、收银台、支付结果、退款进度、小程序模型和 API client 契约没有前端回归；不替代真实浏览器多端验收、微信开发者工具/真机、统一身份权限、真实渠道签名或生产域名验证。

## 707. 2026-09-24 全系统后端回归复核

在原任务窗口持续托管的本地栈上下文中，使用 Maven 3.9.16、JDK 8 和已有本地依赖仓库执行：

```bash
HSP_MAVEN_REPO=/Users/abc123/apache-maven-3.9.16/repository \
HSP_JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home \
node tools/dev/backend-regression.mjs
```

结果：12/12 个后端模块通过，Surefire 汇总 `1491/1491`，失败 `0`、错误 `0`、跳过 `0`。模块范围覆盖 payment-core、gateway-access、settlement-system、accounting-system、clearing-system、refund-center、reconciliation-system、risk-control-system、ops-config-system、deposit-system、wallet-account 和 wallet-system。

该结果确认当前可运行骨架的全系统 Java/H2 自动化基线未出现回归；不替代真实 RabbitMQ/DLQ、生产 MySQL、多实例调度、真实渠道受理、资金到账、可信身份权限或发布治理证据。统一门禁仍保持 `test -> master=false`、`canCreateRelease=false`。

## 708. 2026-09-24 gateway-access 管理台分页契约修复

复核 payment-core 的真实渠道接入边界时发现，`gateway-access` 后端列表接口返回 `PageResultDTO`，而管理端客户端曾将响应压平成数组，页面却按 `.records` 读取，导致首次加载后列表状态异常。本轮将客户端统一归一为 `{ records, total, pageNo, pageSize }`，并兼容空响应、数组和 `items` 形状。

验证：gateway-access 后端 `51/51`、管理端前端 `6/6` 通过，生产构建和 `git diff --check` 通过；修复已提交为 `a4c20947`。浏览器人工视觉核验因当前 macOS 锁屏未执行，不登记为通过；本轮只收口本地分页契约，不代表真实渠道、证书轮换、网关探活、灰度发布或生产网络已验收。

## 709. 2026-09-24 风控与运营配置管理台分页契约修复

跨系统扫描发现 `risk-control`、`ops-config` 与 gateway-access 存在同型问题：后端列表接口返回分页对象，客户端却压平成数组，而页面按 `.records` 读取。两套客户端现统一保留 `{ records, total, pageNo, pageSize }`，空库仍返回可渲染的空分页。

验证：risk-control 后端 `11/11`、管理端 `6/6` 通过并完成生产构建；ops-config 后端 `12/12`、管理端 `6/6` 通过并完成生产构建；`git diff --check` 通过。该批只收口本地列表契约，不代表真实风控引擎、配置发布、消息总线或可信权限已验收。

## 710. 2026-09-24 全量管理端回归复核

在 `gateway-access`、`risk-control`、`ops-config` 管理台分页契约修复后，重新执行 `node tools/dev/frontend-admin-regression.mjs`。结果：`12/12` 个管理端工程的 `npm test` 与 `npm run build` 均通过，当前批次管理端用例合计 `133/133`，失败 `0`。

该结果确认本轮跨系统客户端归一化没有破坏其他后台页面和构建；不替代真实浏览器多端人工验收、可信身份权限、生产 API 域名或发布治理证据。

## 711. 2026-09-24 wallet-account 管理端空分页响应修复

复核其他资金子系统管理台时发现，`wallet-account` 的账户、流水和导出任务分页入口在 `data: null` 时会把空值传给页面，页面读取 `.records`/`.total` 存在运行时风险。本轮仅对三个分页入口归一空页，详情与批量余额接口保持原始空响应语义。

验证：wallet-account admin-web `6/6` 测试通过，生产构建和 `git diff --check` 通过；本轮不改变钱包账户状态、余额、导出任务或真实资金事实。

## 712. 2026-09-24 管理端全量回归复核（含 wallet-account 修复）

在 wallet-account 空分页响应修复后重新执行 `node tools/dev/frontend-admin-regression.mjs`：`12/12` 个管理端工程的 `npm test` 与 `npm run build` 均通过，当前批次管理端用例合计 `134/134`，失败 `0`。

该结果更新管理端自动化基线，不替代真实浏览器人工验收、生产身份权限、API 域名或发布治理证据。

## 713. 2026-09-24 payment-core 管理端列表空响应契约修复

复核 payment-core 管理端 API client 时发现，详情/概览与分页列表共用请求归一化逻辑；当列表接口返回 `data: null` 时，页面直接读取 `result.items` 会触发运行时错误，而详情接口又必须保留原始 `null` 语义。本轮新增列表专用 `requestPage()`，仅对分页列表提供 `{ items: [], total: 0, pageNo: 1, pageSize: 20 }` 空页兜底，并将订单、支付、退款、流水、事件、任务、会话、结算等列表入口切换到该入口；详情、概览和操作接口继续返回原始 `data`。

验证：payment-core admin-web `65/65` 测试通过，生产构建通过，新增列表 `data: null` 空页和详情 `data: null` 语义回归用例。该改动只保证本地管理端骨架在空库/空响应下可渲染，不代表真实渠道、RabbitMQ/DLQ、生产 MySQL、资金到账、身份权限或发布治理证据已经完成。

## 714. 2026-09-24 payment-core 配置中心集合空响应契约修复

复核配置中心页面的复核申请、版本历史和冲突查询时发现，这些接口直接把 `data` 当数组使用；成功响应若为 `null`，页面在 `.length`、`.some` 或循环渲染处会崩溃。本轮新增配置集合专用归一化：复核申请、版本历史、冲突列表以及返回列表的配置变更动作统一将非数组响应收敛为空数组；配置总览将空响应收敛为空对象；路由表达式校验、返回码导入预检和控制策略自检等对象接口保持原始对象语义。

验证：payment-core admin-web `66/66` 测试通过，生产构建通过，新增配置集合空响应和总览空响应回归用例；`git diff --check` 通过。该改动只保证配置中心在空库/空响应下可继续展示空态，不代表真实身份权限、正式审批、生产配置中心、渠道探活或发布治理证据已经完成。

## 715. 2026-09-24 payment-core 异常责任汇总空响应契约修复

按 payment-core 控制器返回类型逐项核对管理端 API client，发现异常中心的 `responsibility-summary` 是唯一仍直接返回列表的非分页接口；页面会对结果执行 `.length`、`.reduce` 和循环渲染，成功响应 `data: null` 时会触发运行时错误。本轮将该接口切换到集合专用归一化，空响应稳定返回空数组。

验证：新增 `data: null` 责任汇总回归用例；payment-core admin-web `67/67` 测试通过，生产构建通过；全量管理端 `12/12` 工程测试与生产构建通过。该改动只保证异常中心空态可渲染，不改变异常事实、责任组统计或告警处理状态。

## 716. 2026-09-24 payment-core 管理端总览空响应契约修复

继续按“先把骨架搭起来、空库也能用”的目标复核监控分析、任务中心和支付日终三类总览页面：成功响应 `data: null` 时，页面原先可能直接覆盖默认快照，导致嵌套字段或数组读取异常。本轮新增对象总览专用归一化；监控、任务中心、日终页面在载入和操作回写时均保留默认字段结构，并对趋势、告警、渠道、批次等集合回退为空数组；详情接口仍保留原始 `null` 语义。

验证：payment-core admin-web `68/68` 测试通过，生产构建通过；随后执行 `node tools/dev/frontend-admin-regression.mjs`，全量管理端 `12/12` 工程测试与生产构建通过，`git diff --check` 通过。该改动只保证总览空态和空库骨架可继续操作，不代表真实渠道、RabbitMQ/DLQ、生产 MySQL、资金到账、身份权限或发布治理证据已经完成。

## 717. 2026-09-24 小程序原生支付参数跨网关代理契约

复核小程序支付链路时发现，payment-core 已支持临时携带 `nativePaymentParams`，但 gateway-access 的支付提交结果 DTO 未透传该字段，真实上游即使签名完成，小程序仍无法调用 `wx.requestPayment`。本轮补齐跨系统临时响应契约：只有 `MINI_PROGRAM` 终端可以返回完整的 `timeStamp / nonceStr / package / signType / paySign`，gateway-access 与 payment-core adapter 都执行白名单、完整集合、长度和控制字符校验；参数不落库，非小程序或非法集合拒绝。demo 适配器仍不生成假签名。

验证：payment-core 后端 `1204/1204`、gateway-access 后端 `53/53` 通过，失败 `0`、错误 `0`、跳过 `0`；当前全量后端 `12/12` 模块合计 `1494/1494`，`git diff --check` 通过。该改动只补齐真实上游签名参数的安全传递边界，不代表微信登录、AppID/商户绑定、证书、真实扣款、回调验签、资金到账或真机验收已经完成。

## 718. 2026-09-24 REG-08 当前批次死信补偿证据

在隔离后缀 `20260924-b` 下保持 RabbitMQ、payment-core、accounting、clearing、settlement 运行，完成三类当前批次演练：

| 场景 | messageId | taskNo | 最终状态 | 下游事实 |
| --- | --- | --- | --- | --- |
| 非法 PAYMENT_SUCCESS | `DLQ-MANUAL-SMOKE-1790223210663-30716` | `DLQ0677f160ab774a0198c8` | `MANUAL_RESOLVED` | 原始 DLQ 审计副本保留，不向下游重放 |
| PAYMENT_SUCCESS -> accounting | `DLQ-SMOKE-1790223210646-30716` | `DLQf3f3da722cbf43968c4f` | `REPLAYED`, `replayCount=1` | `EVT50004`, `LDG20007`, `ACT10003`, `¥9.99` |
| CLEARING_GENERATED -> settlement | `DLQ-SMOKE-1790223184991-30656` | `DLQf8328932093b4732a293` | `REPLAYED`, `replayCount=1` | `SVE70003`, `SLT20004`, `SET10003`, `¥112.00` |

当前真实死信任务页通过无头浏览器从 `/payment-dead-letter-tasks` 采集，截图同时展示当前 `MANUAL_RESOLVED` 与两条 `REPLAYED` 任务。详细查询响应和证据文件见 `docs/releases/evidence/test-20260809-regression/` 下的 API、数据库、事件和截图目录。

## 719. 2026-09-24 REG-08 证据完整后的统一门禁复判

执行：

```bash
node tools/release/audit_payment_core_gate.mjs \
  --root-dir /Users/abc123/workspace/home-service-payment-system \
  --pack /Users/abc123/workspace/home-service-payment-system/docs/releases/evidence/test-20260809-regression \
  --stdout-json
```

结果：回归 `scenarioCount=10`、`incomplete=0`、`missingSample=0`、`missingEvidence=0`、`historicalPage=0`、`historicalRollback=0`，`regressionEvidenceReady=true`；`canPromoteTestToMaster=false`、`canCreateRelease=false` 继续保持。当前剩余阻断仅为 test 汇总的保守不放行决策，以及 release 候选说明正式化、正式签字审批、发布后现场核验和 master 稳定观察结束结论，不能用本地测试或 broker 演练替代。

本轮代码/工具验证：payment-core 后端 `1204/1204`，admin-web `68/68` 且生产构建通过；DLQ 演练工具测试 `7/7` 通过，`git diff --check` 通过。

## 720. 2026-09-24 本地回归报告门禁退出码修复

执行 `node tools/release/run_local_regression_report.mjs --only payment-core --skip-backend --skip-frontend --skip-user-frontend` 时，发现报告文件虽然记录了门禁子命令 `status=2`，主进程却错误返回 `0`，会让 CI 把不可放行状态误判为报告成功。

本轮修正报告脚本，将门禁子命令非零状态纳入最终退出码，并新增 `tools/release/run_local_regression_report.test.mjs` 固定该契约：当前 REG-08 证据完整但 test/release 仍不可放行时，报告返回 `1`，同时保留 `gate.status=2`、`canPromoteTestToMaster=false` 和 `canCreateRelease=false`。

验证：报告退出码修复测试 `1/1` 通过；实际门禁-only 报告按预期返回 `1`。该修复只校正本地回归报告的失败传播，不改变门禁决策，也不把发布治理阻断解释成代码测试失败。

## 721. 2026-09-24 受控运行态纳入本地回归报告

现有 `run_local_regression_report.mjs` 默认只执行代码回归和门禁审计；本轮新增显式 `--include-runtime` 与 `--runtime-scope`，在已有 `local-stack.sh up` 托管会话上追加 HTTP smoke 和跨系统 `happy-path`，并将 smoke 状态、happy-path 日志及解析后的订单号、支付单号、事件类型、对账链状态和 `financialCloseable` 写入 `runtime` 节点。

该选项不负责启动或停止本地服务；运行态失败或 happy-path 没有事实 JSON 时报告失败退出。运行态证据仍只属于受控 demo/H2/HTTP 骨架，不替代真实渠道、生产 RabbitMQ/MySQL、资金到账、正式审批、页面冻结或发布后观察。

验证命令：

```bash
node tools/release/run_local_regression_report.mjs \
  --only payment-core \
  --skip-backend --skip-frontend --skip-user-frontend \
  --include-runtime --runtime-scope all \
  --skip-gate \
  --output .runtime/payment-core-runtime-regression.json
```

本轮实际组合验证：runtime smoke 与 happy-path 均返回 `0`；报告中的 `runtime.happyPath.facts` 已记录 `PAYMENT_SUCCESS / CLEARING_GENERATED / SETTLEMENT_CREATED / ACCOUNTING_POSTED / REFUND_SUCCESS`，对账链 `COMPLETED`、阶段 `4/4`、`financialCloseable=true`。同一报告的统一门禁返回 `status=2`，主进程返回 `1`，并保留 `canPromoteTestToMaster=false`、`canCreateRelease=false`；这证明运行态证据接入没有绕过正式发布门禁。

## 722. 2026-09-24 管理端畸形分页响应边界补强

复核 `admin-web` 分页请求层时发现，列表接口虽已处理 `data: null` 和 `items: null`，但上游若误返回数组或非对象，页面仍可能收到没有 `.items` 的无效响应形状。本轮新增分页响应专用归一化：数组响应按单页列表保留并补齐分页元数据，非对象或缺少 `items` 的响应收敛为空页；详情、概览和集合接口继续保留各自原始语义。

验证结果：payment-core admin-web `69/69` 测试通过，Vite 生产构建通过；payment-core 后端全量 Maven 回归 `1204/1204`，失败 `0`、错误 `0`、跳过 `0`。本轮只增强本地管理端空态和异常响应的可用性，不改变支付状态机、渠道协议、资金事实或发布门禁结论。

## 723. 2026-09-24 当前门禁复判

执行 `node tools/release/audit_payment_core_gate.mjs --root-dir /Users/abc123/workspace/home-service-payment-system --pack /Users/abc123/workspace/home-service-payment-system/docs/releases/evidence/test-20260809-regression --stdout-json`。

结果：回归证据包 `10/10` 完整，缺失样例 `0`、缺失证据 `0`、历史页面 `0`、历史补偿 `0`；`canPromoteTestToMaster=false`、`canCreateRelease=false` 继续保持。当前阻断来自正式治理汇总不放行、候选发布说明未正式化、正式签字审批未形成、发布后现场核验未形成实际记录和 master 稳定观察结束结论未形成，不是 payment-core 代码回归失败。本复判不修改历史证据，不伪造审批、现场核验或发布事实。
## 724. 2026-09-24 全量管理端分页边界回归

在退款中心和对账中心补齐畸形分页响应归一化后，重新执行 12 个管理端工程的 `npm test && npm run build`。

验证结果：12/12 个管理端工程测试与生产构建全部通过，当前批次前端用例合计 `145/145`，失败 `0`；其中退款中心 `9/9`、对账中心 `9/9`、网关接入 `6/6`。本轮只证明本地管理端骨架在正常、空数据和畸形响应下可继续渲染与操作，不替代真实浏览器、渠道网络、生产数据库、消息总线、资金事实或正式发布治理。

## 725. 2026-09-24 当前 HEAD 全量回归与门禁复核

### 代码回归

| 范围 | 结果 |
| --- | --- |
| payment-core 后端 | `1203/1203` 通过，失败 `0`、错误 `0`、跳过 `0` |
| payment-core 用户端 | App `26/26`、H5 `2/2`、PC `2/2`、小程序 `20/20`、API client `10/10` 通过；App/H5/PC 构建通过 |
| 前端管理端 | 既有 `12/12` 工程回归通过，当前批次 `145/145` 用例通过 |

### 门禁复核

`audit_payment_core_gate.mjs` 当前结果为：回归证据包 `10/10` 完整，缺失样例 `0`、缺失证据 `0`，`canPromoteTestToMaster=false`、`canCreateRelease=false`。阻断来自 test 汇总未放行、候选发布说明未正式化、正式签字审批未形成、发布后现场核验未形成实际记录和 master 稳定观察结束结论未形成；这些不能用本地测试、H2、demo 渠道或 HTTP happy-path 替代。

## 726. 2026-09-24 原任务窗口持续托管栈运行态复核

在原任务窗口以 `tools/dev/local-stack.sh up all` 持续托管本地 demo 栈：12 个后端和 15 个前端全部 `READY`，全量 HTTP smoke 通过。

随后执行跨系统 happy-path，实际生成支付、清分、结算、退款和账务事实；对账事实包含 `PAYMENT_SUCCESS / CLEARING_GENERATED / SETTLEMENT_CREATED / ACCOUNTING_POSTED / REFUND_SUCCESS`，对账链状态为 `COMPLETED`，完成阶段 `4/4`，`financialCloseable=true`。本证据只证明本地 H2/demo/HTTP 骨架可以启动并完成跨系统演示闭环，不代表真实渠道受理、生产 MySQL/RabbitMQ、资金到账、正式财务关账或发布治理条件已完成。

## 727. 2026-09-24 保证金管理端分页边界补强

保证金管理端账户和流水 API 补齐分页响应归一化：`null`、字符串等非对象响应统一为空页，数组响应保留为单页列表，对象响应补齐合法的 `items/total/pageNo/pageSize`。新增 `6/6` API 单元测试，生产构建通过；该修复已纳入后续全量管理端回归，不改变 payment-core 的正式发布门禁结论。

随后重跑全量管理端回归：`12/12` 个工程的测试和生产构建全部通过，当前批次合计 `147/147` 用例通过，失败 `0`。该结果只证明本地管理端骨架在正常、空数据和畸形响应下可继续渲染与操作，不替代真实浏览器、渠道网络、生产数据库、消息总线、资金事实或正式发布治理。

## 728. 2026-09-25 payment-core 共享分页元数据边界补强

复核 payment-core 管理端共享 API 客户端时发现，列表响应虽已处理 `items: null`、数组和非对象，但 `total/pageNo/pageSize` 仍可能以非法字符串、负数或缺失值进入分页组件。本轮统一按列表长度、默认页码 `1` 和默认页大小 `20` 收敛，避免页面出现 `NaN` 页数或错误翻页状态。

验证结果：payment-core admin-web `70/70` 测试通过，生产构建通过；全量管理端 `12/12` 工程测试与生产构建通过，当前批次合计 `148/148` 用例通过。该修复只增强本地运营页面异常响应下的可用性，不改变支付状态机、渠道协议、资金事实或正式发布门禁。

## 729. 2026-09-25 资金运营端共享分页元数据边界补强

清分、账务和结算管理端同步收敛列表响应的非法 `total/pageNo/pageSize`，并保留普通摘要、详情和操作响应的原始语义。新增异常分页回归后，清分 `9/9`、账务 `8/8`、结算 `10/10` 通过；全量管理端仍为 `12/12` 工程测试与生产构建通过，当前批次合计 `151/151` 用例通过。本轮只增强资金运营页面的异常响应可用性，不替代真实出款、生产总账、消息总线或财务审批事实。

## 730. 2026-09-25 其他运营端分页元数据边界补强

gateway-access、ops-config、risk-control 和 wallet-account 管理端同步收敛异常分页数字字段，并保持各模块摘要、详情和空列表的既有契约。新增异常分页回归后，四模块分别为 `7/7`、`7/7`、`7/7`、`8/8`；全量管理端 `12/12` 工程测试与生产构建通过，当前批次合计 `155/155` 用例通过。本轮只增强本地运营骨架的异常响应可用性，不改变真实渠道、风控策略、钱包账务、生产基础设施或发布门禁结论。

## 731. 2026-09-25 payment-core 后端当前 HEAD 全量回归

使用仓库内 Maven 3.9.16、JDK 8 和本地依赖仓库执行 `HSP_MAVEN_REPO=/Users/abc123/apache-maven-3.9.16/repository HSP_JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home node tools/dev/backend-regression.mjs --only payment-core`。结果为 `1/1` 后端模块通过，Surefire 汇总 `1204/1204`，失败 `0`、错误 `0`、跳过 `0`。

测试日志中的回滚和失败异常来自既有故障路径用例的主动注入，命令最终退出码为 `0`；该基线只证明本地 Java/H2 自动化回归，不替代真实渠道、RabbitMQ、生产 MySQL、资金到账或发布治理事实。

## 732. 2026-09-25 当前 HEAD 受控本地栈运行态复核

在受控权限下执行 `HSP_MAVEN_REPO=/Users/abc123/apache-maven-3.9.16/repository HSP_JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home bash tools/dev/local-stack.sh verify all`：12 个后端、15 个前端及代理路径全部 `READY`，HTTP smoke 通过，跨系统 happy-path 通过。

本次链路事实包含支付、清分、结算、账务、退款及对账事实；对账链状态为 `COMPLETED`，完成阶段 `4/4`，`financialCloseable=true`。该证据仅证明本地 H2/demo/HTTP 骨架在当前托管会话可运行，不代表真实渠道受理、生产 RabbitMQ/MySQL、资金到账、正式财务关账、审批签字或发布后观察完成。

## 733. 2026-09-25 test 到 master 当前 Git 门禁复核

当前仓库事实为：`master` 是 `test` 的祖先，当前 `test` 仍包含尚未合入 `master` 的提交；因此当前 HEAD 尚未完成 `test -> master` 实际合入。回归证据包中保留的“历史冻结基线已合入”只描述历史批次，不能替代当前分支关系。

统一门禁仍据此保持 `canPromoteTestToMaster=false`、`canCreateRelease=false`。该结论不代表代码回归失败，也不伪造新的合并、审批或发布事实。

## 734. 2026-09-25 监控分析空元素边界补强

`PaymentMonitorServiceImpl` 现在会在服务边界过滤监控趋势、渠道指标和告警列表中的 `null` 元素，避免脏数据导致概览或 CSV 导出整体失败。新增 `PaymentMonitorServiceImplTest` 空元素回归。

验证结果：监控服务定向测试通过；payment-core 后端全量回归更新为 `1205/1205`，失败 `0`、错误 `0`、跳过 `0`。本轮只增强本地监控页面和导出的异常数据容错，不改变告警事实、指标口径或正式发布门禁。

## 735. 2026-09-25 支付详情日志空元素边界补强

`PaymentServiceImpl` 和 `PaymentRecordServiceImpl` 现在会在详情服务边界过滤路由日志、通知日志和事件日志中的 `null` 元素，避免单条脏数据导致支付详情或收款记录详情整体失败。新增 `PaymentServiceImplTest`、`PaymentRecordServiceImplTest` 空元素回归。

验证结果：两组详情定向测试通过；payment-core 后端全量回归更新为 `1207/1207`，失败 `0`、错误 `0`、跳过 `0`。本轮只增强运营排障详情的异常数据容错，不改变支付事实、脱敏契约或正式发布门禁。

## 736. 2026-09-25 路由与补偿候选空元素边界补强

`PaymentChannelRoutingServiceImpl`、`PaymentEventRetryTaskServiceImpl`、`RefundQueryTaskServiceImpl` 和 `RefundRetryTaskServiceImpl` 现在会在服务边界过滤候选列表中的 `null` 元素，避免脏候选触发支付路由、事件重发或退款补偿批次异常。新增四条服务层回归，覆盖健康路由、事件重发、退款主动查单和失败退款重试。

验证结果：四组定向测试通过；payment-core 后端全量回归更新为 `1211/1211`，失败 `0`、错误 `0`、跳过 `0`。本轮只增强主链路和补偿任务的异常数据容错，不改变路由规则、任务状态机、真实渠道或正式发布门禁。

## 737. 2026-09-25 配置治理总览空元素边界补强

`PaymentConfigServiceImpl` 现在会在配置总览、配置自检和路由冲突检测共用的服务边界过滤渠道、路由、协议、网关、控制策略、告警供应商和值班路由列表中的 `null` 元素，避免单条脏行导致运营配置页面或自检失败。新增 `PaymentConfigServiceImplTest` 总览空元素回归。

验证结果：配置治理定向测试通过；payment-core 后端全量回归更新为 `1212/1212`，失败 `0`、错误 `0`、跳过 `0`。本轮只增强配置控制面的异常数据容错，不改变复核、版本或发布门禁语义。

## 738. 2026-09-25 admin-web 列表与退款详情空元素边界补强

管理端 API 客户端现在过滤分页 `items`、集合响应、退款详情 `attempts` 和 `operationLogs` 中的 `null` 行；退款详情视图的操作日志空态改为安全访问，避免异常响应导致运营列表或详情页渲染失败。新增分页、集合和退款详情空元素回归。

验证结果：payment-core admin-web 测试 `72/72` 通过，生产构建通过。该轮只增强管理端异常响应容错，不改变后端支付/退款事实、分页口径或正式发布门禁。

## 739. 2026-09-25 用户端收银台与详情空元素边界补强

App/H5/PC 共享用户端 API 客户端现在过滤收银台渠道、支付详情轨迹和退款详情尝试/操作日志中的 `null` 行，避免异常响应导致收银台、支付结果和退款进度页面渲染失败。新增用户端客户端空元素回归。

验证结果：payment-core app-web 测试 `27/27` 通过，生产构建通过。该轮只增强用户端异常响应容错，不改变支付状态机、渠道协议或正式发布门禁。

## 740. 2026-09-25 H5/PC 共享用户端回归复核

确认 H5 与 PC 通过 shared 视图复用 app-web API 客户端和支付页面；H5 测试 `2/2`、PC 测试 `2/2`，两端生产构建均通过。该验证只证明共享用户端骨架构建和路由基线正常，不替代真机、真实渠道或正式发布治理证据。

## 741. 2026-09-25 小程序详情响应空元素边界补强

小程序请求层现在过滤收银台渠道、支付详情轨迹和退款详情尝试/操作日志中的 `null` 行，避免异常响应导致原生收银台、结果页或退款进度时间线异常。新增小程序请求层空元素回归。

验证结果：小程序测试 `21/21` 通过。该轮只增强小程序异常响应容错，不改变原生支付参数契约、支付状态机或正式发布门禁；真机、真实签名和真实渠道仍需独立验证。

## 742. 2026-09-25 最新 HEAD 本地全栈验收

在当前 `test` HEAD 上执行 `tools/dev/local-stack.sh verify all`：12 个后端、15 个前端及代理路径全部 `READY`，HTTP smoke 和跨系统 happy-path 通过；本地链路生成支付、清分、结算、账务、退款和对账事实，对账链 `COMPLETED`、完成阶段 `4/4`、`financialCloseable=true`。

该运行态只属于受控 H2/demo/HTTP 本地骨架，不替代真实渠道、RabbitMQ、生产 MySQL、资金到账、真机验收或正式发布治理证据。

## 743. 2026-09-25 异常中心查询空元素边界补强

`PaymentIssueServiceImpl` 现在会在支付异常列表、告警日志列表和责任组汇总边界过滤 `null` 元素，避免单条脏数据影响分页、脱敏输出或运营统计。新增异常中心服务空元素回归；首次全量尝试因测试 DTO setter 错误在编译阶段失败，修正为真实字段后重新验证。

验证结果：异常中心定向测试通过；payment-core 后端全量回归为 `1213/1213`，失败 `0`、错误 `0`、跳过 `0`。本轮只增强异常中心异常数据容错，不改变告警事实、任务处置或正式发布门禁。

## 744. 2026-09-25 任务中心趋势与执行日志空元素边界补强

`PaymentTaskCenterServiceImpl` 现在会在查询趋势、最近任务执行日志和分页任务日志边界过滤 `null` 元素，避免单条脏数据导致失败率、严重等级、升级状态或人工补偿列表派生失败。新增任务中心服务空元素回归。

验证结果：任务中心定向测试通过；payment-core 后端全量回归为 `1214/1214`，失败 `0`、错误 `0`、跳过 `0`。本轮只增强任务处置异常数据容错，不改变任务状态机、补偿语义或正式发布门禁。

## 745. 2026-09-25 日终批次列表空元素边界补强

`PaymentDayEndServiceImpl` 现在会在日终总览最近批次服务边界过滤 `null` 元素，避免单条脏批次导致对账准入结论或财务关账页面失败。新增日终服务空批次回归。

验证结果：日终定向测试通过；payment-core 后端全量回归为 `1215/1215`，失败 `0`、错误 `0`、跳过 `0`。本轮只增强日终/对账准入异常数据容错，不改变批次事实、财务口径或正式发布门禁。

## 746. 2026-09-25 支付事件出站列表空元素边界补强

`PaymentEventServiceImpl` 现在会在支付事件出站列表和 CSV 导出共用的脱敏列表边界过滤 `null` 元素，避免单条脏事件影响分页、排障或导出。新增事件服务空元素回归。

验证结果：支付事件定向测试通过；payment-core 后端全量回归为 `1216/1216`，失败 `0`、错误 `0`、跳过 `0`。本轮只增强事件出站异常数据容错，不改变事件事实、重发审计或正式发布门禁。

## 747. 2026-09-25 支付请求列表空元素边界补强

支付请求分页和 CSV 导出共用的脱敏列表边界现在过滤 mapper 返回的 `null` 行，避免单条脏请求导致运营排查列表或导出失败；新增支付请求服务回归，并修正配置总览回归夹具中 `Arrays.asList(null)` 的 Java varargs 陷阱。

验证结果：支付请求与配置治理定向测试通过；payment-core 后端全量回归为 `1217/1217`，失败 `0`、错误 `0`、跳过 `0`。本轮只增强本地支付请求排障骨架和测试稳定性，不改变支付事实、真实渠道或正式发布治理结论。

## 748. 2026-09-25 运营列表空元素边界补强

支付流水、收银台会话、账单、订单、服务者结算、退款和路由执行列表的脱敏边界现在统一过滤 mapper 返回的 `null` 行，避免单条脏数据影响分页或 CSV 导出；对应 7 组服务测试覆盖了空行与有效行混合响应。

验证结果：7 组运营列表定向测试通过；payment-core 后端全量回归为 `1224/1224`，失败 `0`、错误 `0`、跳过 `0`。本轮只增强本地运营列表骨架容错，不改变支付、退款、结算事实、真实渠道或正式发布治理结论。

## 749. 2026-09-25 主支付与补偿列表空元素边界补强

支付主单、收款记录和死信任务列表的脱敏边界现在统一过滤 mapper 返回的 `null` 行，避免支付主列表、收款排查和补偿重放页面出现空记录；新增收款记录列表回归，并复用主单与死信列表的空行回归。

验证结果：支付主单、收款记录和死信任务定向测试通过；payment-core 后端全量回归为 `1227/1227`，失败 `0`、错误 `0`、跳过 `0`。本轮只增强本地支付与补偿运营骨架容错，不改变支付事实、重放语义、真实消息总线或正式发布治理结论。

## 750. 2026-09-25 支付日志与过期关单候选空元素边界补强

支付处理日志分页和过期关单任务候选现在统一过滤 mapper 返回的 `null` 元素，避免日志列表出现空行或定时任务因单条脏候选中断；新增日志列表混合响应和过期关单候选回归。

验证结果：支付日志与过期关单定向测试通过；payment-core 后端全量回归为 `1229/1229`，失败 `0`、错误 `0`、跳过 `0`。本轮只增强本地排障与定时任务骨架容错，不改变日志事实、关单状态机、真实基础设施或正式发布治理结论。

## 751. 2026-09-25 支付日志 CSV 空元素边界补强

支付处理日志 CSV 导出现在复用列表服务的空元素归一化，过滤 mapper 返回的 `null` 行，避免分页列表可用但导出因空记录失败；新增 CSV 混合响应回归。

验证结果：支付日志定向测试通过；payment-core 后端全量回归为 `1230/1230`，失败 `0`、错误 `0`、跳过 `0`。本轮只增强本地日志导出骨架容错，不改变日志事实、导出字段口径、真实基础设施或正式发布治理结论。

## 752. 2026-09-25 退款详情集合空元素边界补强

退款详情的操作日志和退款尝试集合现在统一过滤 mapper 返回的 `null` 元素，避免退款进度时间线或运营详情页出现空记录；新增退款详情混合响应回归。

验证结果：退款服务定向测试通过；payment-core 后端全量回归为 `1231/1231`，失败 `0`、错误 `0`、跳过 `0`。本轮只增强本地退款详情骨架容错，不改变退款状态机、渠道事实、真实退款到账或正式发布治理结论。

## 753. 2026-09-25 payment-core 全端前端回归复核

当前 payment-core 前端骨架完成全端回归：12 个管理端工程测试与生产构建全部通过；App/H5/PC 共享用户端测试 `27/27` 且生产构建通过；微信小程序请求层测试 `21/21` 通过。该结果证明当前本地管理端、浏览器用户端和小程序 API 骨架可构建、可回归，不替代真实浏览器/真机、统一身份权限、真实渠道或发布治理证据。

## 754. 2026-09-25 当前 HEAD 本地全栈联调复核

在当前 `test` HEAD 执行 `bash tools/dev/local-stack.sh verify all`：12 个后端、15 个前端及代理路径全部 `READY`，HTTP 冒烟和跨系统 happy-path 通过；本地链路生成支付、清分、结算、账务、退款和对账事实，对账链 `COMPLETED`、完成阶段 `4/4`、`financialCloseable=true`。该运行态只属于受控 H2/demo/HTTP 本地骨架，不替代真实渠道、RabbitMQ/DLQ、生产 MySQL、资金到账、真机验收或正式发布治理证据。

## 755. 2026-09-25 异常告警派发候选空元素边界补强

异常告警派发和回执核对任务现在统一过滤 mapper 返回的 `null` 候选，告警供应商候选也跳过空配置，避免单条脏数据中断整批派发、回执核对或升级等级汇总；新增空候选任务回归。

验证结果：异常告警派发服务定向测试通过；payment-core 后端全量回归为 `1232/1232`，失败 `0`、错误 `0`、跳过 `0`。本轮只增强本地异常处置骨架容错，不改变告警状态机、真实通知送达或正式发布治理结论。

## 756. 2026-09-25 DLQ 补偿 smoke 工具契约回归

执行 `node --test tools/dev/dlq-compensation-smoke.test.mjs`（受控权限允许本地 mock 端口）通过 `7/7`，覆盖 `payment.success` 与 `clearing.generated` 的 replay、人工解决、业务号提取、非法事件类型、非法超时、非法 JSON 和 RabbitMQ 管理端不可用分支。

本轮只证明 smoke 工具的参数校验、API 编排和失败闭合契约可回归；没有 RabbitMQ 管理端和隔离拓扑的真实运行态取证，因此不将该结果登记为 `REG-08` 当前批次证据，也不改变 `test -> master` 或 release 门禁结论。

## 757. 2026-09-25 当前 HEAD payment-core 全量回归复核

使用仓库 Maven 3.9.16、JDK 8 和受控本地依赖仓库执行 `HSP_MAVEN_REPO=/Users/abc123/apache-maven-3.9.16/repository HSP_JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home node tools/dev/backend-regression.mjs --only payment-core`，`1/1` 模块通过；当前 Surefire 汇总为 `1221/1221`，`0` failures、`0` errors、`0` skipped。

历史章节中的更高测试数保留为当时批次证据，不代表当前测试集合；本轮只更新当前 HEAD 计数，不改变真实渠道、生产基础设施、资金事实或发布门禁判断。

## 758. 2026-09-25 全支付后端矩阵回归复核

在同一当前 HEAD、JDK 8 和 Maven 3.9.16 受控环境下执行 `node tools/dev/backend-regression.mjs`，`12/12` 后端模块通过：payment-core `1221/1221`、gateway-access `53/53`、settlement `38/38`、accounting `47/47`、clearing `38/38`、refund-center `18/18`、reconciliation `15/15`、risk-control `11/11`、ops-config `12/12`、deposit `11/11`、wallet-account `23/23`、wallet-system `24/24`；全部 `0` failures、`0` errors、`0` skipped。

该矩阵只证明各模块本地 Maven/H2 或测试数据库回归通过，不替代真实 MySQL、RabbitMQ/DLQ、渠道协议、资金到账、审批签字、现场核验或正式发布门禁。

## 759. 2026-09-25 当前 HEAD 前端矩阵回归复核

执行 `node tools/dev/frontend-admin-regression.mjs`，12/12 管理端工程的 `npm test` 与生产构建全部通过；执行 `node tools/dev/payment-core-user-regression.mjs`，5/5 用户端通过：API client `10/10`、App `27/27`、H5 `2/2`、PC `2/2`、小程序 `21/21`，App/H5/PC 生产构建通过。

该结果只证明本地前端测试、构建和 API 客户端骨架可用，不替代真实浏览器组合、微信开发者工具/真机、统一身份权限、真实渠道签名、生产域名或发布治理证据。

## 760. 2026-09-25 对账 DAO 混合空元素边界补强

对账 DAO 的统一列表边界现在同时处理 `null` 列表和列表中的 `null` 行，保留有效记录，避免详情、CSV 导出和跨系统链路因单条脏数据失败。新增 DAO 回归后，reconciliation-system 后端全量测试为 `16/16`，失败 `0`、错误 `0`、跳过 `0`；本轮只增强对账本地骨架容错，不改变真实渠道账单、生产 MQ、自动核销或正式发布治理结论。

## 761. 2026-09-25 配置与风控 DAO 混合空元素边界补强

运营配置和风控 DAO 列表现在统一过滤 Mapper 返回的 `null` 行，同时保留有效记录，避免支付配置快照、管理台列表和风控决策装配被单条脏数据中断。运营配置后端全量 `13/13`、风控后端全量 `12/12`，均为 `0` failures、`0` errors、`0` skipped。

本轮只增强支付依赖子系统的本地查询骨架容错，不替代真实配置发布、风控策略命中、消息总线、生产数据库、资金或正式发布治理证据。

## 762. 2026-09-25 账务、清分、结算与钱包列表边界补强

支付核心直接依赖的账务、清分、结算和钱包骨架现在统一过滤列表中的 `null` 行，同时保留有效记录；本轮回归结果为账务 `48/48`、清分 `39/39`、结算 `39/39`、钱包 `25/25`，均为 `0` failures、`0` errors、`0` skipped。

本轮只增强跨支付子系统的本地查询和任务骨架容错，不替代真实数据库、消息总线、渠道、资金到账、审批或正式发布治理证据。

## 763. 2026-09-25 当前 HEAD 全支付后端矩阵复核

执行 `HSP_MAVEN_REPO=/Users/abc123/apache-maven-3.9.16/repository HSP_JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home node tools/dev/backend-regression.mjs`，`12/12` 模块通过。当前各模块 Surefire 计数为：payment-core `1221`、gateway-access `53`、settlement `39`、accounting `48`、clearing `39`、refund-center `18`、reconciliation `16`、risk-control `12`、ops-config `13`、deposit `11`、wallet-account `23`、wallet-system `25`；全部 `0` failures、`0` errors、`0` skipped。

矩阵只证明当前本地 Maven/H2 或测试数据库链路可回归，不替代真实 MySQL、RabbitMQ/DLQ、渠道协议、资金到账、审批签字、现场核验或正式发布门禁事实。

## 764. 2026-09-25 退款渠道查单请求失败闭合

退款渠道查单服务现在与支付查单保持一致：`null` 请求在选择适配器前返回 `REFUND_CHANNEL_QUERY_FAILED`，适配器集合为 `null` 或包含空元素时按“无可用适配器”处理，不再泄漏 `NullPointerException`。新增两条服务层回归后，payment-core 后端全量回归为 `1223/1223`，失败 `0`、错误 `0`、跳过 `0`。

本轮只收口退款查单服务的输入和装配边界，不改变真实渠道协议、退款到账、生产网关或发布治理结论。

## 765. 2026-09-25 网关支付适配器受控异常脱敏

`GatewayAccessPaymentChannelSubmitAdapter`、`GatewayAccessPaymentChannelQueryAdapter` 及支付提交/查单结果校验器现在统一使用 `BusinessException.sanitized(...)` 生成固定业务失败，避免网关拒绝、响应字段非法、请求校验失败和标准化失败把内部堆栈带入事务日志或异常链；新增提交与查单适配器脱敏断言。

验证结果：网关支付提交/查单定向测试通过；payment-core 后端全量回归为 `1232/1232`，失败 `0`、错误 `0`、跳过 `0`。本轮只收口网关边界异常的安全失败契约，不改变渠道订单绑定、支付状态机、真实网关受理、资金到账或正式发布治理结论。

## 766. 2026-09-25 退款网关查单异常脱敏

`GatewayAccessRefundChannelQueryAdapter` 与 `RefundChannelQueryResultValidator` 现在统一以脱敏 `BusinessException` 闭合网关拒绝、请求非法和退款状态/字段非法响应，保持未知退款主动查单、`PROCESSING/SUCCESS/FAIL` 状态校验及查询来源语义不变；新增退款查单异常脱敏断言。

验证结果：退款网关查单定向测试通过；payment-core 后端全量回归为 `1232/1232`，失败 `0`、错误 `0`、跳过 `0`。本轮只补强退款查单边界安全失败，不改变退款状态机、渠道退款到账、真实网关或正式发布治理结论。

## 767. 2026-09-25 payment-core 用户端与管理端矩阵复核

重新执行前端回归矩阵：管理端 `12/12` 工程的测试与生产构建通过；用户端 `5/5` 通过，其中 API client `10/10`、App `27/27`、H5 `2/2`、PC `2/2`、微信小程序 `21/21`，App/H5/PC 生产构建通过。首次用户端构建因既有 `node_modules/.vite-temp` 权限失败，受控权限下重跑后通过；本节只记录本地前端骨架证据，不替代真实浏览器组合、微信开发者工具/真机、统一身份权限、渠道签名或生产域名验收。

## 768. 2026-09-25 当前 HEAD 本地全栈联调复核

执行 `HSP_MAVEN_REPO=/Users/abc123/apache-maven-3.9.16/repository bash tools/dev/local-stack.sh verify all`：12 个后端、15 个前端及代理路径全部 `READY`，HTTP 冒烟与跨系统 happy-path 通过；本地链路实际生成支付、清分、结算、账务、退款和对账事实，对账链为 `COMPLETED`、完成阶段 `4/4`、`financialCloseable=true`。该结果只属于受控 H2/demo/HTTP 本地骨架，不替代真实渠道、RabbitMQ/DLQ、生产 MySQL、资金到账、真机验收或正式发布治理证据。

## 769. 2026-09-25 README 与当前交付口径校正

README 的旧批次回归数字已更新为当前可验证基线：payment-core 后端 `1232/1232`，管理端 `12/12`，用户端 `5/5`；同时补充当前受控全栈 `12` 个后端、`15` 个前端及代理 `READY`、HTTP 冒烟和跨系统 happy-path 证据。历史章节保持原样，仅在交付差距清单中明确 8.75 的退款查单代理描述属于历史批次，当前实现以 8.76 和现行代码为准。
### 9.70 2026-09-25 返回码指纹与工作台空行边界补强

返回码导入预检、提交和批准后的基线指纹计算现在统一过滤 Mapper 返回的 `null` 映射，避免脏行在排序或字段读取时中断配置治理；工作台卡片列表也过滤集合内 `null` 行，保留有效卡片。新增两条服务层回归，payment-core 后端全量回归为 `1234/1234`，失败 `0`、错误 `0`、跳过 `0`。本轮只补强本地配置治理和工作台骨架的异常数据容错，不改变返回码版本、复核状态、支付事实、真实基础设施或发布治理结论。
### 9.71 2026-09-25 配置治理快照导出空行边界补强

配置治理快照 CSV 的渠道、路由、协议、返回码、网关、控制策略、告警供应商和值班路由八个分区现在统一过滤导出 Mapper 返回的 `null` 行，避免单条脏配置导致整份导出失败；新增治理快照导出回归，payment-core 后端全量回归为 `1235/1235`，失败 `0`、错误 `0`、跳过 `0`。本轮只增强本地配置导出骨架容错，不改变配置版本、候选复核、运行态发布或正式发布治理结论。
### 9.72 2026-09-25 日终与任务中心导出空行边界补强

日终批次 CSV 和任务执行日志 CSV 在派生展示字段计算前统一过滤 Mapper 返回的 `null` 行，避免单条脏记录导致导出失败；既有日终与任务中心导出回归已改为覆盖“空行 + 有效行”混合结果，payment-core 全量回归保持 `1235/1235`，失败 `0`、错误 `0`、跳过 `0`。本轮只增强本地运营导出骨架容错，不改变批次事实、任务状态机、补偿语义或正式发布治理结论。
### 9.73 2026-09-25 异常 SLA 候选空集合边界补强

任务中心异常 SLA 升级现在将逾期告警候选和未确认升级候选统一归一化为安全列表，Mapper 返回 `null` 时按本轮无候选处理；自动升级回归覆盖两个 `null` 候选集合，payment-core 全量回归保持 `1235/1235`，失败 `0`、错误 `0`、跳过 `0`。本轮只增强任务处置骨架容错，不改变告警生成、升级状态机、补偿语义或正式发布治理结论。
