# gateway-access 数据库设计

## 1. 表清单

1. `t_gateway_app`
2. `t_gateway_channel`
3. `t_gateway_certificate`
4. `t_gateway_permission`
5. `t_gateway_audit_log`

## 2. 说明

第一版先给出完整表结构草案，后续可迁移为正式持久化实现。
调用方审计表必须保留请求流水号、调用应用、网关、签名算法、来源 IP、结果和风险提示，支持接入问题回溯。
