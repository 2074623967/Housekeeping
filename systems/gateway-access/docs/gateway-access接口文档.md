# gateway-access 接口文档

## 1. 总览

### `GET /api/gateway-access/summary`

返回：

- `metrics`
- `highlights`

## 2. 接入应用

### `GET /api/gateway-access/applications`
### `POST /api/gateway-access/applications/toggle`

## 3. 网关渠道

### `GET /api/gateway-access/gateways`
### `POST /api/gateway-access/gateways/toggle`

## 4. 证书管理

### `GET /api/gateway-access/certificates`
### `POST /api/gateway-access/certificates/toggle`

## 5. 接入权限

### `GET /api/gateway-access/permissions`
### `POST /api/gateway-access/permissions/toggle`

## 6. 调用方审计

### `GET /api/gateway-access/audit-logs`

支持按请求流水号、应用编码和结果状态筛选，返回签名算法、来源 IP、调用结果和风险提示。
