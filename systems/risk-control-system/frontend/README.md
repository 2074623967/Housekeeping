# 风控系统前端

Vue3 + Vite 管理后台，默认代理到 `http://127.0.0.1:18180`。

```bash
npm install
npm run build
```

页面动作只维护风控治理台账和人工复核基线，不代表已经接入真实实时风控引擎。

管理端 API client 已区分风控服务的网络失败、超时、非 JSON 响应和业务错误；Node 测试 `3/3` 通过，Vite 生产构建通过。
