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
2. 未预期 `RuntimeException` 转换为带内部 cause 的 `PAYMENT-1046`，对外消息固定且操作日志只保存“未获得可信结果”，不写远端异常明细。
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
2. 失败日志刻意不保存远端异常原文；内部 cause 仅供受控技术排障，不改变 API 错误码和运营日志最小化原则。
3. `@Transactional(noRollbackFor = BusinessException.class)` 是失败日志随业务异常提交的现有边界；真实数据库故障、事务代理配置和生产网关异常分布仍需环境级验证。

## 239. 2026-09-10 支付查单可信结果与失败历史 V1.0 验证

### 239.1 本轮补强

1. 支付查单适配器抛出普通运行时异常时，服务统一返回带内部 cause 的 `PAYMENT-1026`，并写最小化失败通知，避免自动退避和统一监控漏记。
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
