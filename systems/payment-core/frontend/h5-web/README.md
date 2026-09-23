# h5-web

H5 支付端，复用 `app-web` 的收银台与结果页能力，独立承载 H5 入口。

同时提供 `/refund-progress/:refundOrderId` 只读退款进度页，沿用退款详情接口展示状态和处理轨迹，不在用户端执行运营动作。
