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
tools/dev/local-stack.sh happy-path
```

也可以只运行某一类或某个系统：

```bash
tools/dev/local-stack.sh up backends
tools/dev/local-stack.sh up frontends
tools/dev/local-stack.sh up payment-core
```

`up` 适合由当前 Codex 任务窗口持续托管：暂停 Codex 任务不会主动停止服务，回到原任务后可以继续检查；只有在托管终端发送 `Ctrl-C` 才会停止本次脚本拉起的进程。需要脱离当前窗口运行时使用 `start`，之后用 `stop` 显式停止。

运行日志位于 `.runtime/local-stack/logs/`，PID 文件位于 `.runtime/local-stack/pids/`。

### 本地入口

| 系统 | 前端入口 |
| --- | --- |
| 支付后台 | http://127.0.0.1:5174 |
| 支付 App / H5 / PC | http://127.0.0.1:5175 / 5176 / 5177 |
| 钱包账户 | http://127.0.0.1:5178 |
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
