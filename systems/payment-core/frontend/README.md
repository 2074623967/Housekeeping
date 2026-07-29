# payment-core frontend

## 目录定位

`payment-core` 前端不再维持单一目录，而是按端拆分。

当前结构：

```text
frontend
├── admin-web
├── app-web
├── h5-web
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

### app-web

用户端 App 支付端。

后续主要承接：

- 收银台
- 支付结果页
- 订单支付页
- 用户退款进度页

### h5-web

用户端 H5 支付端。

当前承接：

- H5 收银台
- H5 支付结果页

### pc-web

用户端 PC 支付端。

当前承接：

- PC 收银台
- PC 支付结果页

### shared

用于放未来多端共享内容，例如：

- 共享接口模型
- 共享工具函数
- 共享常量
