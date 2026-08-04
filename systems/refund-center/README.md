# 退款中心

退款中心是家政服务支付平台的逆向交易系统，负责退款申请、审核、渠道提交、异步回调、失败重试、操作审计和退款成功事件出站。

## 当前交付范围

- 独立数据库：`housekeeping_refund`
- 退款申请金额校验和幂等
- 原支付成功事实投影
- 退款审核、渠道提交、成功/失败回调、失败重试
- 退款操作日志和 outbox 事件
- Vue3 管理后台：工作台、退款列表、退款详情、动作操作
- MyBatis `dao / mapper / mapper.xml` 持久化分层

## 系统边界

`payment-core` 只负责正向支付及退款入口兼容；退款中心拥有退款单状态机和逆向资金事实。账务系统通过 `REFUND_SUCCESS` 事件记账，支付核心通过内部事件或消息将支付成功事实投影到本系统。

## 本地启动

```bash
cd backend
/Users/abc123/apache-maven-3.9.16/bin/mvn \
  -Dmaven.repo.local=/Users/abc123/apache-maven-3.9.16/repository spring-boot:run

cd ../frontend/admin-web
npm run dev
```

默认端口：后端 `18140`，前端 `5179`。

切换本地 MySQL 时使用：

```bash
REFUND_CENTER_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/housekeeping_refund?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8&allowPublicKeyRetrieval=true' \
REFUND_CENTER_DATASOURCE_DRIVER='com.mysql.cj.jdbc.Driver' \
REFUND_CENTER_DATASOURCE_USERNAME='root' \
REFUND_CENTER_DATASOURCE_PASSWORD='<本机密码>' \
REFUND_CENTER_SQL_INIT_MODE=never
```
