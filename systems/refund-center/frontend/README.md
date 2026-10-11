# 退款中心前端

Vue3 + Vite 管理后台，默认代理到 `http://127.0.0.1:18140`。

```bash
npm install
npm run build
```

管理端 API client 已区分退款服务的网络失败、超时、非 JSON 响应和业务错误；JSON 请求及退款详情导出默认使用 `10000ms` 客户端超时并在请求结束后清理计时器，Node 测试和 Vite 生产构建通过。这些结果不代表真实渠道退款或资金到账。
