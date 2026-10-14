# 家政支付系统

## 项目说明

这是一个独立的新项目目录，用于落地“家政服务公司完整支付系统”。

仓库采用 `项目级总控 + 按系统拆分实现` 的组织方式，方便后续扩展和维护。

当前技术选型：

- 前端：Vue 3 + Vite + Vue Router
- 后端：Java 8 + Spring Boot 2.7
- 文档：Markdown

## 目录结构

- `docs`：项目级总控文档
- `systems`：按系统拆分的实现目录

核心文档：

- `docs/整体架构与实现目标.md`
- `docs/开发排期与提交流程.md`
- `docs/总体架构与系统实施蓝图.md`
- `docs/系统级设计模板.md`
- `docs/第二阶段系统落地清单.md`
- `docs/模块开发交付标准.md`
- `docs/系统分期建设方案.md`
- `docs/系统目录总览.md`
- `docs/四类资料对照与系统二次加工规范.md`

`payment-core` 第一阶段B 关键文档：

- `systems/payment-core/docs/第一阶段B-支付交易闭环设计.md`
- `systems/payment-core/docs/第一阶段B-支付交易闭环需求清单.md`
- `systems/payment-core/docs/第一阶段B-支付交易闭环数据库设计.md`
- `systems/payment-core/docs/第一阶段B-支付交易闭环接口文档.md`

当前已落地系统：

- `systems/payment-core`
- `systems/wallet-account`
- `systems/accounting-system`
- `systems/clearing-system`
- `systems/settlement-system`
- `systems/refund-center`
- `systems/reconciliation-system`
- `systems/deposit-system`
- `systems/ops-config-system`
- `systems/risk-control-system`
- `systems/wallet-system`
- `systems/gateway-access`

`payment-core` 内部包含：

- `docs`
- `frontend`
- `backend`

`payment-core/frontend` 当前已拆分为：

- `admin-web`
- `app-web`
- `h5-web`
- `pc-web`
- `shared`

## 系统建设顺序

本项目不是按“页面列表”开发，而是按支付资金平台的系统边界逐步建设。

当前约定的建设顺序如下：

1. `一期：支付核心域`
2. `二期：账户账务与清结算域`
3. `三期：退款、对账、保证金与运营配置域`
4. `四期：钱包、营销、线下汇入、二清监管与业财税票协同`

详细说明请看：

- `docs/系统分期建设方案.md`

## 当前开发范围

当前第一优先级交付仍归属于 `systems/payment-core`，也就是 `一期：支付核心域`，覆盖：

- 支付运营工作台
- 订单中心
- 账单中心
- 支付单管理
- 支付流水查询
- 收银台会话管理
- 支付请求管理
- 支付处理日志
- 支付交易异常中心
- 支付异常告警中心
- 支付任务中心
- 支付监控分析
- 支付日终处理
- 支付配置中心
- 支付事件出站台账
- 退款单管理
- 服务者结算联查入口
- 用户端 / H5 / PC 收银台与支付结果页
- 后端支付交易闭环 API 与自动补偿骨架

说明：

- `payment-core` 当前已经不是“只做几个查询页”的 MVP，而是支付核心域阶段性交付包。
- `退款单管理` 当前由 `payment-core` 过渡承载，长期归属仍建议拆到独立 `refund-center`。
- `服务者结算单` 当前只保留联查入口，完整结算审批、出款和核销继续归属 `settlement-system`。
- 账户账务、清分清算、对账、保证金、钱包营销等能力，会在对应系统继续展开，而不是继续堆在 `payment-core`。

## 运行说明

仓库根目录提供统一运行入口：

```bash
# 检查 Java、Maven、Node 和前端依赖
tools/dev/local-stack.sh doctor all

# 在当前终端前台托管全部系统；保持运行，Ctrl-C 后统一清理
tools/dev/local-stack.sh up all

# 查看状态或重新执行前端、后端及代理链路冒烟
tools/dev/local-stack.sh status all
tools/dev/local-stack.sh smoke all

# 在同一受控会话完成全栈启动、HTTP 冒烟、跨系统 happy-path 并自动停止
tools/dev/local-stack.sh verify all

# 在所有相关端口空闲、无现存 PID 时仅验证隔离支付链路并自动停止
tools/dev/local-stack.sh verify payment-chain

# 相同九服务链路改由本机网关主动查单收口支付，受控上游和 H2 证书 fixture 随会话清理
tools/dev/local-stack.sh verify payment-query-chain

# 在隔离 H2 中预置一笔未付标准订单，验证支付至九服务对账并自动停服
tools/dev/local-stack.sh verify payment-standard-chain

# 同一标准订单夹具改由受控本机网关主动查单完成支付
tools/dev/local-stack.sh verify payment-standard-query-chain

# 预置应收 88、已付 20 的标准订单，支付本次尾款 68 并核对九服务
tools/dev/local-stack.sh verify payment-standard-partial-chain

# 同一补尾款夹具改由受控本机网关主动查单完成支付
tools/dev/local-stack.sh verify payment-standard-partial-query-chain

# 仅启动 H2/demo 支付核心与风控后端，验证人工复核并自动停止
tools/dev/local-stack.sh verify risk-review
```

`up` 会在当前终端持续托管服务，适合在同一个 Codex 任务窗口中暂停后继续；暂停任务不会主动停止已启动进程，回到窗口后可继续执行 `status`、`smoke` 或业务验收。macOS 如果系统默认 Java 只有 JRE，可显式指定完整 JDK 和已有 Maven 缓存：

```bash
HSP_JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home \
HSP_MAVEN_REPO=/Users/abc123/apache-maven-3.9.16/repository \
bash tools/dev/local-stack.sh up all
```

支付核心主动查单穿过真实 gateway-access 的本地双服务回归可执行 `node tools/dev/payment-core-gateway-query-smoke.mjs`（按需指定 `HSP_MAVEN_BIN`、`HSP_JAVA_HOME`）。脚本仅启动回环 HTTP 与精确 H2/demo 服务，网关证书调整只写当次 H2 fixture；清分/账务仍是本机受控 mock，不代表真实渠道或九服务验收。

网关查单与退款双服务 smoke 均在启动前拒绝继承的 Spring、服务连接、数据库、JVM 和代理环境覆盖；仅接收本地 Maven/Java 路径与 Maven 仓库。只有查单脚本接受 `HSP_QUERY_RACE_MODE=conflicting`，拼写错误或退款脚本携带此变量都会在启动前失败。运行前清理其他环境变量，不要将脚本指向共享或真实渠道。

也可以只运行某一类或某个系统：

```bash
tools/dev/local-stack.sh up backends
tools/dev/local-stack.sh up frontends
tools/dev/local-stack.sh up payment-core
```

`up` 适合由当前 Codex 任务窗口持续托管：暂停 Codex 任务不会主动停止服务，回到原任务后可以继续检查；只有在托管终端发送 `Ctrl-C` 才会停止本次脚本拉起的进程。需要脱离当前窗口运行时使用 `start`，之后用 `stop` 显式停止。

`verify all` 适合一次性验收本地骨架。它把启动、冒烟、跨系统 happy-path 和停止放在同一个受控会话中，避免运行器回收后台进程后留下 stale PID。

`verify risk-review` 只启动支付核心和风控中心后端，不启动前端；它在隔离 H2/demo 中创建虚拟支付单，验证待审、批准后变更方式/渠道/商户均返回 `PAYMENT-1024` 且无支付尝试，原请求批准后继续到 `WAIT_CALLBACK`。命令拒绝数据库、端口、关键网关等环境覆盖，拒绝现存两服务 PID 文件和相关端口已有服务；运行前先确认本机端口未被代理到共享环境。脚本在首笔写入前校验本机 HTTP 地址、payment-core H2/demo 身份和仅在 `local-risk-review` profile + H2 驱动下可用的风控身份端点；异常退出也清理本次启动的服务。`node --test tools/dev/cross-system-risk-review.test.mjs` 可单独运行断言测试；身份标记不是认证，不代表生产审核身份、目标 MySQL 8 迁移或真实资金验收。

跨系统 happy-path 只能经隔离的 `verify payment-chain` / `verify payment-query-chain` / `verify payment-standard-chain` / `verify payment-standard-query-chain` / `verify payment-standard-partial-chain` / `verify payment-standard-partial-query-chain` / `verify all` 运行；`local-stack.sh happy-path` 是虚拟订单回调链的别名，不能在 `up all` 或 `start backends` 保持运行时复用已有服务，须先停服。入口要求本机有 `lsof` 与 `nc`，在启动前拒绝数据库和下游覆盖、现存后端 PID 文件与目标端口监听，启动后要求每个目标后端由本轮进程提供；首笔写请求前还要求九个目标为固定本机端口，并逐一读取各自的 H2 演示身份，任何缺失、错配或重定向均失败闭合。四个 `payment-standard-*` 模式仅在本轮 H2/demo payment-core 初始化时加载测试 SQL，预置标准订单而不暴露业务创建接口。支付核心身份仅在 `demo` + H2、其他八服务仅在受控 `local-payment-chain` profile + H2 驱动下可用；本地标记不是跨系统认证，也不代替外部部署和实际资金核验。

确认本地 `payment-core` 使用 `local-stack.sh` 的 demo profile 且系统 Chrome 可用时，可执行 `node tools/dev/payment-terminal-browser-smoke.mjs --demo`：它仅在 `localhost:18081` 的后端返回 demo profile + H2 专属身份标记后，才创建一笔虚构预付单，随后在 App/H5/PC 的收银台和结果页检查各自端口的只读详情响应与订单金额渲染，不提交支付。缺少显式 `--demo` 或身份标记时脚本不写入预付单；标记仅防止误连非演示后端，不是认证凭据。脚本单测为 `node --test tools/dev/payment-terminal-browser-smoke.test.mjs`；如 Chrome 不在默认 macOS 路径，可设置 `HSP_CHROME_BIN`。在会回收后台进程的运行器内，应将 `local-stack.sh start payment-core`、浏览器脚本和 `local-stack.sh stop payment-core` 放在同一受控会话，并确保异常退出也执行停服。

如需核验 **payment-core 同源退款** 的处理中用户页，显式执行 `node tools/dev/payment-terminal-browser-smoke.mjs --demo --refund-demo`。此模式会在本地 H2/demo 环境模拟支付提交、成功回调、退款申请与审核，随后只读检查三端共九页；它不覆盖独立 `refund-center` 的退款来源，也不证明真实渠道受理、资金到账或用户归属授权。不要对非演示服务使用此模式。

如需继续核验同一笔 demo 退款从处理中进入成功终态，可追加 `--refund-success-demo`；脚本读取本轮退款尝试号与渠道退款号后模拟成功回调，分别检查处理态和成功态的三端只读页面。此选项必须与 `--demo --refund-demo` 一起使用，不代表真实到账或独立退款中心闭环。

如需核验退款失败终态，改为追加 `--refund-fail-demo`（同样必须与 `--demo --refund-demo` 一起使用，不能与 `--refund-success-demo` 同时使用）；脚本基于本轮渠道尝试和受理号模拟失败回调，并分别检查处理态和失败态的 App/H5/PC 页面。只覆盖 payment-core 同源虚构退款，不证明独立退款中心查询、可信用户归属或真实渠道失败处理。

两种退款终态模式还会重新读取本轮退款的真实 HTTP 详情，分别调用 App/H5/PC 共用、小程序、管理端和 API 客户端的校验器，拒绝缺少最新尝试或终态与渠道归属矛盾的响应；小程序和管理端在此仅验证客户端响应契约，不运行其页面。

运行日志位于 `.runtime/local-stack/logs/`，PID 文件位于 `.runtime/local-stack/pids/`。

### 本地入口

| 系统 | 前端入口 |
| --- | --- |
| 支付后台 | http://localhost:5174 |
| 支付 App / H5 / PC | http://localhost:5175 / 5176 / 5177 |
| 钱包账户 | http://localhost:5178 |
| 退款中心 | http://127.0.0.1:5179 |
| 对账系统 | http://127.0.0.1:5180 |
| 保证金 | http://127.0.0.1:5181 |
| 运营配置 | http://127.0.0.1:5182 |
| 风控 | http://127.0.0.1:5183 |
| 网关接入 | http://127.0.0.1:5184 |
| 钱包与营销资金 | http://127.0.0.1:5185 |
| 清分 | http://127.0.0.1:5273 |
| 结算 | http://127.0.0.1:5274 |
| 账务 | http://127.0.0.1:5275 |

系统当前可操作能力、验证口径和待补项见：

- `docs/本地可运行骨架与功能矩阵.md`

`payment-core` 的单模块详细启动方式见：

- `systems/payment-core/docs/开发落地说明.md`
