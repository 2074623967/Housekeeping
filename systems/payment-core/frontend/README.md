# payment-core frontend

## 目录定位

`payment-core` 前端不再维持单一目录，而是按端拆分。

当前结构：

```text
frontend
├── admin-web
├── api-client
├── app-web
├── h5-web
├── mini-program
├── pc-web
└── shared
```

## 子目录职责

### admin-web

后台管理台，面向：

- 支付运营
- 财务
- 结算运营
- 客服后台

当前已经落地的页面全部归属于：

- `admin-web`

### api-client

服务到服务 API 联调端，覆盖预付、收银台、提交、详情、主动查单、受限关单，以及退款申请、审核、详情、主动查单和失败重试。

### app-web

用户端 App 支付端。

后续主要承接：

- 收银台
- 支付结果页
- 订单支付页
- 用户退款进度页

与 H5、PC 共用支付端路由契约，入口测试覆盖四类业务场景；当前 App 测试 `26/26` 通过，Vite 生产构建通过。

### h5-web

用户端 H5 支付端。

当前承接：

- H5 收银台
- H5 支付结果页

已补独立路由契约测试，覆盖充值、提现、转账、余额支付、收银台和支付结果入口；当前测试 `2/2` 通过，Vite 生产构建通过。

### pc-web

用户端 PC 支付端。

当前承接：

- PC 收银台
- PC 支付结果页

已补独立路由契约测试，覆盖充值、提现、转账、余额支付、收银台和支付结果入口；当前测试 `2/2` 通过，Vite 生产构建通过。

### mini-program

原生微信小程序支付端，覆盖四类业务入口、收银台和支付结果闭环。

当前小程序契约测试 `20/20` 通过；服务到服务 `api-client` 契约测试 `10/10` 通过。App、H5、PC 三端生产构建均通过。

### shared

用于放未来多端共享内容，例如：

- 共享接口模型
- 共享工具函数
- 共享常量

## 本地浏览器冒烟

从仓库根目录先确认 `18081`、`5174`—`5177` 端口空闲，且没有数据库或 Spring 环境覆盖；在一个终端运行 `bash tools/dev/local-stack.sh up payment-core`，另一个终端运行 `node tools/dev/payment-terminal-browser-smoke.mjs --demo`。可选 `--pending-demo` 验证待回调查单入口，或 `--refund-demo --refund-success-demo` / `--refund-demo --refund-fail-demo` 验证退款进度。结束后在服务终端按 Ctrl-C，并核对 `bash tools/dev/local-stack.sh status payment-core` 均为 `DOWN`。

浏览器脚本会在创建虚构预付单前检查 `/api/demo/identity`。该身份接口只在内置固定的 H2 内存库 URL 与 `demo` profile 同时满足时开放；不要自定义 `HSP_DEMO_DB_URL`，否则即使仍是 H2，身份接口也会返回 404，脚本会安全停止。此验证只针对 localhost 模拟事实，不涉及真实渠道或生产资金。
