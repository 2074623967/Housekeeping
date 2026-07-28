INSERT INTO t_gateway_app (app_code, app_name, source_system, owner, ip_whitelist, permission_scope, status, status_type, updated_at) VALUES
('APP_PAY_CORE', '支付核心应用', 'payment-core', '支付平台主管', '10.0.0.0/24', '收款/退款', 'ENABLED', 'success', '2026-07-20 22:40:00'),
('APP_SETTLEMENT', '结算平台应用', 'settlement-system', '结算平台主管', '10.0.1.0/24', '出款/回单', 'ENABLED', 'success', '2026-07-20 22:40:00'),
('APP_RISK', '风控平台应用', 'risk-control-system', '风控平台主管', '10.0.2.0/24', '拦截/告警', 'DISABLED', 'danger', '2026-07-20 22:40:00');

INSERT INTO t_gateway_channel (gateway_code, gateway_name, channel_type, protocol_type, sign_algorithm, endpoint, status, status_type, updated_at) VALUES
('GW_WX', '微信渠道接入', '支付渠道', 'HTTP+JSON', 'HMAC-SHA256', 'https://gateway.local/wechat', 'ENABLED', 'success', '2026-07-20 22:40:00'),
('GW_ALI', '支付宝渠道接入', '支付渠道', 'HTTP+FORM', 'RSA2', 'https://gateway.local/alipay', 'ENABLED', 'success', '2026-07-20 22:40:00'),
('GW_BANK', '银行银企直连', '银行直联', 'SFTP+FILE', 'RSA2048', 'https://gateway.local/bank', 'DISABLED', 'danger', '2026-07-20 22:40:00');

INSERT INTO t_gateway_certificate (certificate_code, gateway_code, certificate_version, expire_at, status, status_type, updated_at) VALUES
('CERT_WX_2026', 'GW_WX', 'v2026.07', '2026-09-30', 'ENABLED', 'success', '2026-07-20 22:40:00'),
('CERT_ALI_2026', 'GW_ALI', 'v2026.07', '2026-08-20', 'ENABLED', 'success', '2026-07-20 22:40:00'),
('CERT_BANK_2026', 'GW_BANK', 'v2026.06', '2026-07-25', 'DISABLED', 'danger', '2026-07-20 22:40:00');

INSERT INTO t_gateway_permission (permission_code, app_code, scope, status, status_type, updated_at) VALUES
('PERM_PAYMENT_CORE', 'APP_PAY_CORE', '支付收款/退款', 'ENABLED', 'success', '2026-07-20 22:40:00'),
('PERM_SETTLEMENT_OUT', 'APP_SETTLEMENT', '资金出款/回单', 'ENABLED', 'success', '2026-07-20 22:40:00'),
('PERM_RISK_SIGNAL', 'APP_RISK', '风险事件/拦截', 'DISABLED', 'danger', '2026-07-20 22:40:00');

INSERT INTO t_gateway_release_route (route_code, gateway_code, environment, release_strategy, traffic_percent, release_window, status, status_type, updated_at) VALUES
('ROUTE_WX_PROD', 'GW_WX', 'PROD', 'BLUE_GREEN', 100, '2026-07-28 00:00 ~ 2026-07-28 23:59', 'ENABLED', 'success', '2026-07-28 09:00:00'),
('ROUTE_ALI_GRAY', 'GW_ALI', 'GRAY', 'CANARY', 20, '2026-07-28 10:00 ~ 2026-07-28 18:00', 'ENABLED', 'success', '2026-07-28 10:15:00'),
('ROUTE_BANK_UAT', 'GW_BANK', 'UAT', 'DIRECT', 100, '2026-07-28 09:00 ~ 2026-07-28 20:00', 'DISABLED', 'danger', '2026-07-28 08:50:00');

INSERT INTO t_gateway_audit_log (request_id, app_code, gateway_code, operation_type, sign_type, client_ip, result_status, result_status_type, risk_hint, happened_at) VALUES
('REQ-202607280001', 'APP_PAY_CORE', 'GW_WX', 'PAY_PREPARE', 'HMAC-SHA256', '10.0.0.12', 'SUCCESS', 'success', '签名验证通过，来源IP命中白名单', '2026-07-28 09:10:00'),
('REQ-202607280002', 'APP_SETTLEMENT', 'GW_BANK', 'PAYOUT_SUBMIT', 'RSA2048', '10.0.1.33', 'FAILED', 'danger', '银行网关已停用，请检查渠道状态', '2026-07-28 10:05:00'),
('REQ-202607280003', 'APP_PAY_CORE', 'GW_ALI', 'REFUND_SUBMIT', 'RSA2', '10.0.0.12', 'SUCCESS', 'success', '签名验证通过，回调地址校验完成', '2026-07-28 11:18:00');
