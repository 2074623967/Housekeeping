# payment-core

## 模块定位

`payment-core` 是家政支付系统的一期落地模块，对应 `支付核心系统 MVP`。

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
└── shared
```

## 当前范围

当前已覆盖：

- 工作台
- 订单中心
- 支付单管理
- 退款单管理入口
- 服务者结算入口

这些页面当前都归属于：

- `frontend/admin-web`

## 文档入口

- [开发落地说明](/Users/abc123/workspace/home-service-payment-system/systems/payment-core/docs/开发落地说明.md)
- [一期-支付核心系统接口与数据设计](/Users/abc123/workspace/home-service-payment-system/systems/payment-core/docs/一期-支付核心系统接口与数据设计.md)
- [一期-支付核心系统前端页面与交互说明](/Users/abc123/workspace/home-service-payment-system/systems/payment-core/docs/一期-支付核心系统前端页面与交互说明.md)
- [一期-支付核心系统后端详细设计](/Users/abc123/workspace/home-service-payment-system/systems/payment-core/docs/一期-支付核心系统后端详细设计.md)
- [一期-支付核心系统DDL.sql](/Users/abc123/workspace/home-service-payment-system/systems/payment-core/docs/一期-支付核心系统DDL.sql)
