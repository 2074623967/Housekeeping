# gateway-access 数据库设计

## 1. 表清单

1. `t_gateway_app`
2. `t_gateway_channel`
3. `t_gateway_certificate`
4. `t_gateway_permission`
5. `t_gateway_audit_log`
6. `t_gateway_release_route`

## 2. 说明

第一版先给出完整表结构草案，后续可迁移为正式持久化实现。
调用方审计表必须保留请求流水号、调用应用、网关、签名算法、来源 IP、结果和风险提示，支持接入问题回溯。
灰度发布路由表必须保留环境、发布策略、流量占比和发布时间窗口，支持环境隔离与流量切换治理。

MySQL `database/schema.sql` 与 H2 初始化脚本均应包含发布路由及调用审计两表。已有库只使用 `database/migrations/2026-10-12-add-release-route-and-audit.sql` 补齐缺失表，不得执行带 `DROP TABLE` 的建库基线；增量脚本不自动写入任何发布路由或伪造审计记录。生产发布路由、证书和访问密钥仍须单独受控配置与验证。
