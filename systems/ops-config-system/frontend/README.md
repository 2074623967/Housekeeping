# 运营配置系统前端

Vue3 + Vite 管理后台，默认代理到 `http://127.0.0.1:18170`。

```bash
npm install
npm run build
```

页面动作只维护运营配置台账，不会直接下发真实渠道证书、密钥或风控限额。
# ops-config-system frontend

管理端 API client 已区分运营配置服务的网络失败、超时、非 JSON 响应和业务错误，并统一归一列表空响应；JSON 请求及有效配置快照导出默认使用 `10000ms` 客户端超时并在请求结束后清理计时器，Node 测试和 Vite 生产构建通过。页面验证不代表真实生产配置发布或审批事实。
