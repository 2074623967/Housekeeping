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

启用门禁：

- 已过期证书不允许重新启用
- 证书归属网关未启用时，不允许单独启用证书

## 5. 接入权限

### `GET /api/gateway-access/permissions`
### `POST /api/gateway-access/permissions/toggle`

启用门禁：

- 权限归属应用未启用时，不允许单独启用权限

## 6. 调用方审计

### `GET /api/gateway-access/audit-logs`

支持按请求流水号、应用编码和结果状态筛选，返回签名算法、来源 IP、调用结果和风险提示。

## 7. 灰度发布路由

### `GET /api/gateway-access/release-routes`
### `POST /api/gateway-access/release-routes/toggle`

支持按环境和状态筛选，用于管理 `PROD / GRAY / UAT` 发布路由、流量占比和发布窗口。

启用门禁：

- 灰度路由归属网关未启用时，不允许启用路由
