# payment-core

## 模块定位

`payment-core` 是家政支付系统的一期落地模块，对应 `支付核心系统` 的阶段性交付包。

它负责承接最基础的支付运营后台能力，为后续账户账务、清结算、退款中心、对账系统提供主链路入口。

## 当前目录结构

```text
payment-core
├── docs
├── frontend
└── backend
```

其中 `frontend` 已拆分为：

```text
frontend
├── admin-web
├── app-web
├── h5-web
├── pc-web
└── shared
```

## 当前范围

当前已覆盖：

- 工作台
- 订单中心
- 账单中心
- 支付单管理
- 支付单详情
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
- 退款单管理与退款详情复盘
- 服务者结算联查入口
- `app-web` 用户端收银台与支付结果页
- `h5-web` H5 收银台与支付结果页
- `pc-web` PC 收银台与支付结果页

其中后台页面归属于：

- `frontend/admin-web`

用户端页面归属于：

- `frontend/app-web`
- `frontend/h5-web`
- `frontend/pc-web`

## 当前交付状态

截至 `2026-08-02`，`payment-core` 已完成一期支付核心域的阶段性交付：

1. 后端支付主链路已可真实启动、自测并落库
2. `admin-web` 已覆盖运营后台核心页面
3. `app-web / h5-web / pc-web` 已覆盖多端收银台和支付结果页
4. 后台订单中心可生成收银台链接并串联到多端支付流程
5. 后台已补齐退款列表、退款详情、退款原因和退款操作日志的最小闭环复盘能力
6. 已具备支付事件出站、异常中心、任务中心、日终处理和配置治理等运营骨架
7. 当前版本适合作为一期支付核心域的开发、联调和测试基线

注意：

- 当前版本并不等于“完整支付平台全量完成”
- 若要从完整支付系统视角评估当前 `payment-core` 的页面、端类型、模块边界和能力缺口，请优先阅读：
  - [payment-core全量交付差距清单](/Users/abc123/workspace/home-service-payment-system/systems/payment-core/docs/payment-core全量交付差距清单.md)
  - [payment-core页面矩阵与系统归属清单](/Users/abc123/workspace/home-service-payment-system/systems/payment-core/docs/payment-core页面矩阵与系统归属清单.md)

## 页面边界冻结说明

`payment-core` 后续只继续承接以下两类内容：

1. 正向支付交易主链路
2. 支付核心运营查询与排障能力

以下能力默认不再继续堆到 `payment-core`，而是拆到独立系统：

- 账户账务
- 清分清算
- 结算出款
- 长期独立退款中心
- 财务对账
- 保证金
- 钱包与营销资金
- 配置中心
- 风控中台

若要查看完整页面归属与冻结边界，请优先阅读：

- [payment-core页面矩阵与系统归属清单](/Users/abc123/workspace/home-service-payment-system/systems/payment-core/docs/payment-core页面矩阵与系统归属清单.md)

## 文档入口

- [开发落地说明](/Users/abc123/workspace/home-service-payment-system/systems/payment-core/docs/开发落地说明.md)
- [payment-core全量交付差距清单](/Users/abc123/workspace/home-service-payment-system/systems/payment-core/docs/payment-core全量交付差距清单.md)
- [payment-core页面矩阵与系统归属清单](/Users/abc123/workspace/home-service-payment-system/systems/payment-core/docs/payment-core页面矩阵与系统归属清单.md)
- [一期-支付核心系统接口与数据设计](/Users/abc123/workspace/home-service-payment-system/systems/payment-core/docs/一期-支付核心系统接口与数据设计.md)
- [一期-支付核心系统前端页面与交互说明](/Users/abc123/workspace/home-service-payment-system/systems/payment-core/docs/一期-支付核心系统前端页面与交互说明.md)
- [一期-支付核心系统后端详细设计](/Users/abc123/workspace/home-service-payment-system/systems/payment-core/docs/一期-支付核心系统后端详细设计.md)
- [一期-支付核心系统DDL.sql](/Users/abc123/workspace/home-service-payment-system/systems/payment-core/docs/一期-支付核心系统DDL.sql)
