# 对账系统

对账系统负责把渠道账单、平台支付事实和后续财务处理结果放到同一批次中核对，输出匹配结果和可审计差异。

## V1 范围

- 对账批次创建
- 渠道账单记录导入
- 平台支付事实导入
- 按支付单号和金额自动匹配
- 渠道单边、平台单边、金额不一致差异
- 差异列表和人工结案
- 独立数据库 `housekeeping_reconciliation`
- Vue3 财务后台

## 系统边界

对账系统只读或接收支付、退款、账务和渠道事实，不直接修改其他系统核心表。差异处置结果通过事件或人工调账流程回写对应责任系统。

## 本地启动

```bash
cd backend
/Users/abc123/apache-maven-3.9.16/bin/mvn \
  -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository spring-boot:run

cd ../frontend/admin-web
npm install
npm run dev
```

默认后端端口 `18150`，前端端口 `5180`。

