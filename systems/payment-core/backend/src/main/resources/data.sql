INSERT INTO t_dashboard_card (card_key, title, value, badge_type, badge_text, sort_no) VALUES
('pay_success', '今日支付成功金额', '¥128,420', 'success', '较昨日 +8.4%', 1),
('refund_amount', '今日退款金额', '¥7,240', 'warn', '待复核 3 笔', 2),
('worker_pending', '待结算服务者金额', '¥89,360', 'info', '账期待生成', 3),
('recon_gap', '未关闭差异金额', '¥3,120', 'danger', '阻断级 2 笔', 4);

INSERT INTO t_payment_channel_config (channel_code, channel_name, payment_method, merchant_no, callback_secret, callback_notify_url, scene_scope, status, status_type, daily_limit, priority, updated_at) VALUES
('wx_h5', '微信 H5 支付', '微信支付', 'MCH_WX_10001', 'wx-h5-secret-2026', 'https://pay.housekeeping.local/callback/wx_h5', 'H5/APP/小程序', 'ENABLED', 'success', 500000.00, 1, '2026-07-20 22:40:00'),
('alipay_h5', '支付宝 H5 支付', '支付宝', 'MCH_ALI_20001', 'alipay-h5-secret-2026', 'https://pay.housekeeping.local/callback/alipay_h5', 'H5/APP/PC', 'ENABLED', 'success', 500000.00, 2, '2026-07-20 22:40:00'),
('offline_bank', '线下银行转账', '银行转账', 'BANK_OFFLINE_30001', 'offline-bank-secret-2026', 'https://pay.housekeeping.local/callback/offline_bank', '企业客户/大额订单', 'ENABLED', 'success', 2000000.00, 3, '2026-07-20 22:40:00');

INSERT INTO t_payment_route_rule_config (rule_code, rule_name, match_scene, match_expression, target_channel_code, fallback_channel_code, status, status_type, priority, updated_at) VALUES
('RULE_HOME_WX', '家政 H5 微信优先', 'HOME_CLEAN/H5', 'paymentMethod=微信支付 AND amount<=5000', 'wx_h5', 'alipay_h5', 'ENABLED', 'success', 1, '2026-07-20 22:40:00'),
('RULE_HOME_ALI', '家政 H5 支付宝兜底', 'HOME_CLEAN/H5', 'paymentMethod=支付宝 AND amount<=5000', 'alipay_h5', 'wx_h5', 'ENABLED', 'success', 2, '2026-07-20 22:40:00'),
('RULE_ENTERPRISE_BANK', '企业大额订单走线下银行', 'ENTERPRISE/PC', 'amount>5000 OR customerType=ENTERPRISE', 'offline_bank', 'alipay_h5', 'ENABLED', 'success', 3, '2026-07-20 22:40:00');

INSERT INTO t_payment_protocol_config (protocol_code, protocol_name, protocol_type, template_version, sign_mode, scene_scope, channel_scope, merchant_ack_required, risk_control_tag, status, status_type, priority, updated_at) VALUES
('PROTO_HOUSEKEEPING_SIGN_V1', '家政服务标准收单协议', 'PAYMENT_SIGN', 'v1.0.0', '线上签约+实名校验', '保洁/月嫂/到家服务', 'wx_h5,alipay_h5', '需要', '实名+重复签约校验', 'ENABLED', 'success', 1, '2026-07-20 22:40:00'),
('PROTO_ENTERPRISE_PREAUTH_V1', '企业客户预授权协议', 'PREAUTH', 'v1.2.0', '线下审核+线上确认', '企业保洁/长期合同', 'offline_bank,alipay_h5', '需要', '企业白名单+大额限额', 'ENABLED', 'success', 2, '2026-07-20 22:40:00'),
('PROTO_MEMBER_DEDUCT_V1', '会员代扣协议', 'WITHHOLD', 'v0.9.3', '短信确认+静默续扣', '包月保洁/会员续费', 'wx_h5', '不需要', '签约时效+扣款频控', 'DISABLED', 'danger', 3, '2026-07-20 22:40:00');

INSERT INTO t_payment_channel_return_code_map (channel_code, channel_return_code, standardized_code, standardized_message, handling_suggestion, retryable, status, status_type, priority, updated_at) VALUES
('wx_h5', 'USERPAYING', 'PAYMENT-CHANNEL-1001', '用户支付中，需继续轮询或等待回调', '展示处理中并触发主动查单', '是', 'ENABLED', 'success', 1, '2026-07-20 22:40:00'),
('wx_h5', 'AUTH_CODE_INVALID', 'PAYMENT-CHANNEL-1002', '付款码无效或已过期', '提示用户刷新付款码后重试', '否', 'ENABLED', 'success', 2, '2026-07-20 22:40:00'),
('alipay_h5', 'ACQ.TRADE_HAS_SUCCESS', 'PAYMENT-CHANNEL-2001', '交易已成功，请避免重复扣款', '直接收口成功并记录重复提交通知', '否', 'ENABLED', 'success', 3, '2026-07-20 22:40:00'),
('offline_bank', 'BANK_TIMEOUT', 'PAYMENT-CHANNEL-3001', '银行通道超时', '转人工复核并支持后续补单', '是', 'DISABLED', 'danger', 4, '2026-07-20 22:40:00');

INSERT INTO t_payment_gateway_config (gateway_code, gateway_name, access_mode, channel_scope, api_base_url, protocol_type, sign_algorithm, timeout_ms, retry_policy, status, status_type, priority, updated_at) VALUES
('GATEWAY_WX_ACQ', '微信收单网关', '直连', 'wx_h5', 'https://gateway.housekeeping.local/wx/acquire', 'HTTP+JSON', 'HMAC-SHA256', 3000, '失败重试2次+超时查单', 'ENABLED', 'success', 1, '2026-07-20 22:40:00'),
('GATEWAY_ALI_ACQ', '支付宝收单网关', '直连', 'alipay_h5', 'https://gateway.housekeeping.local/alipay/acquire', 'HTTP+FORM', 'RSA2', 3500, '失败重试1次+异步回调兜底', 'ENABLED', 'success', 2, '2026-07-20 22:40:00'),
('GATEWAY_BANK_OFFLINE', '线下银行清算网关', '银行服务商', 'offline_bank', 'https://gateway.housekeeping.local/bank/offline', 'SFTP+文件', 'RSA2048', 8000, '人工复核+批次补传', 'DISABLED', 'danger', 3, '2026-07-20 22:40:00');

INSERT INTO t_order (order_no, customer_name, service_type, worker_name, order_amount, paid_amount, order_status, order_status_type, fulfillment_status, fulfillment_status_type, created_at) VALUES
('ORD202607190001', '张女士', '深度保洁', '李阿姨', 268.00, 268.00, '待履约', 'info', '已预约', 'info', '2026-07-19 09:20:11'),
('ORD202607190002', '王先生', '月嫂套餐', '周阿姨', 8800.00, 2000.00, '待支付', 'warn', '待确认', 'warn', '2026-07-19 10:02:44'),
('ORD202607180118', '企业客户-晨星科技', '企业保洁', '企业服务组', 3600.00, 3600.00, '已完成', 'success', '已验收', 'success', '2026-07-18 18:40:07');

INSERT INTO t_payment_order (payment_order_id, order_no, customer_name, amount, payment_method, channel_code, channel_transaction_no, status, status_type, created_at) VALUES
('PAY202607190001', 'ORD202607190001', '张女士', 268.00, '微信', 'wx_jsapi', 'WX99887766', 'SUCCESS', 'success', '2026-07-19 09:21:18'),
('PAY202607190002', 'ORD202607190002', '王先生', 2000.00, '支付宝', 'alipay_h5', 'ALI77665544', 'WAIT_CALLBACK', 'warn', '2026-07-19 10:03:01'),
('PAY202607180074', 'ORD202607180118', '企业客户-晨星科技', 3600.00, '银行转账', 'offline_bank', 'BANK332211', 'SUCCESS', 'success', '2026-07-18 18:41:09');

INSERT INTO t_bill (bill_no, order_no, customer_name, bill_amount, paid_amount, bill_status, bill_status_type, due_at, created_at) VALUES
('BILL202607190001', 'ORD202607190001', '张女士', 268.00, 268.00, '已支付', 'success', '2026-07-20 23:59:59', '2026-07-19 09:20:35'),
('BILL202607190002', 'ORD202607190002', '王先生', 8800.00, 2000.00, '部分支付', 'warn', '2026-07-21 23:59:59', '2026-07-19 10:02:51'),
('BILL202607180118', 'ORD202607180118', '企业客户-晨星科技', 3600.00, 3600.00, '已结清', 'success', '2026-07-19 23:59:59', '2026-07-18 18:40:18');

INSERT INTO t_prepay_order (prepay_order_no, bill_no, order_no, customer_name, amount, pay_scene, cashier_title, cashier_status, cashier_status_type, payment_order_id, created_at, expires_at) VALUES
('PRE202607190001', 'BILL202607190001', 'ORD202607190001', '张女士', 268.00, 'H5', '家政服务收银台', '待支付', 'warn', 'PAY202607190001', '2026-07-19 09:20:58', '2026-07-19 10:20:58'),
('PRE202607190002', 'BILL202607190002', 'ORD202607190002', '王先生', 6800.00, 'H5', '家政服务收银台', '支付中', 'info', 'PAY202607190002', '2026-07-19 10:03:00', '2026-07-19 11:03:00'),
('PRE202607180118', 'BILL202607180118', 'ORD202607180118', '企业客户-晨星科技', 3600.00, 'PC', '企业客户收银台', '已完成', 'success', 'PAY202607180074', '2026-07-18 18:40:52', '2026-07-18 19:40:52');

INSERT INTO t_payment_attempt (attempt_no, prepay_order_no, payment_order_id, channel_code, payment_method, request_payload, response_payload, attempt_status, attempt_status_type, created_at) VALUES
('ATT202607190001', 'PRE202607190001', 'PAY202607190001', 'wx_jsapi', '微信', '{"scene":"H5","amount":268.00}', '{"payUrl":"https://pay.example.com/wx/1"}', '成功', 'success', '2026-07-19 09:21:10'),
('ATT202607190002', 'PRE202607190002', 'PAY202607190002', 'alipay_h5', '支付宝', '{"scene":"H5","amount":6800.00}', '{"payUrl":"https://pay.example.com/ali/2"}', '等待回调', 'warn', '2026-07-19 10:03:11'),
('ATT202607180074', 'PRE202607180118', 'PAY202607180074', 'offline_bank', '银行转账', '{"scene":"PC","amount":3600.00}', '{"bankNo":"BANK332211"}', '成功', 'success', '2026-07-18 18:41:11');

INSERT INTO t_payment_notify_log (notify_no, payment_order_id, channel_code, notify_type, notify_payload, notify_result, notify_status, notify_status_type, created_at) VALUES
('NTF202607190001', 'PAY202607190001', 'wx_jsapi', 'SUCCESS', '{"tradeState":"SUCCESS"}', '{"code":"SUCCESS"}', '已收口', 'success', '2026-07-19 09:21:22'),
('NTF202607190002', 'PAY202607190002', 'alipay_h5', 'WAITING', '{"tradeStatus":"WAIT_BUYER_PAY"}', NULL, '待处理', 'warn', '2026-07-19 10:03:18'),
('NTF202607180074', 'PAY202607180074', 'offline_bank', 'SUCCESS', '{"status":"SUCCESS"}', '{"code":"SUCCESS"}', '已收口', 'success', '2026-07-18 18:41:20');

INSERT INTO t_payment_route_record (route_no, payment_order_id, channel_code, route_rule, route_result, created_at) VALUES
('RTR202607190001', 'PAY202607190001', 'wx_jsapi', 'customer_channel=wechat', '微信JSAPI', '2026-07-19 09:20:59'),
('RTR202607190002', 'PAY202607190002', 'alipay_h5', 'amount>1000 => alipay', '支付宝H5', '2026-07-19 10:03:02'),
('RTR202607180074', 'PAY202607180074', 'offline_bank', 'business=enterprise => offline', '线下转账', '2026-07-18 18:40:54');

INSERT INTO t_payment_event (event_no, event_type, payment_order_id, biz_no, downstream_system, event_topic, publish_status, publish_status_type, retry_count, last_published_at, next_retry_at, event_payload, created_at) VALUES
('EVT202607190001', 'PAYMENT_SUCCESS', 'PAY202607190001', 'ORD202607190001', 'accounting-system,clearing-system,settlement-system', 'payment.trade.succeeded.v1', 'SUCCESS', 'success', 1, '2026-07-19 09:21:25', NULL, '{"amount":268.00}', '2026-07-19 09:21:23'),
('EVT202607190002', 'PAYMENT_PENDING', 'PAY202607190002', 'ORD202607190002', 'payment-core-ops', 'payment.trade.pending.v1', 'PENDING', 'warn', 0, NULL, '2026-07-20 10:08:19', '{"amount":6800.00}', '2026-07-19 10:03:19'),
('EVT202607180074', 'PAYMENT_SUCCESS', 'PAY202607180074', 'ORD202607180118', 'accounting-system,clearing-system,settlement-system', 'payment.trade.succeeded.v1', 'FAILED', 'danger', 2, '2026-07-18 18:41:30', '2026-07-20 09:00:00', '{"amount":3600.00}', '2026-07-18 18:41:21');

INSERT INTO t_refund_order (refund_order_id, payment_order_id, order_no, customer_name, refund_amount, refund_method, status, status_type, applied_at, success_at) VALUES
('REF202607190001', 'PAY202607190001', 'ORD202607190001', '张女士', 68.00, '原路退款', 'PROCESSING', 'warn', '2026-07-19 11:05:10', NULL),
('REF202607180019', 'PAY202607180074', 'ORD202607180118', '企业客户-晨星科技', 600.00, '原路退款', 'SUCCESS', 'success', '2026-07-18 19:00:11', '2026-07-18 19:03:26'),
('REF202607170088', 'PAY202607160031', 'ORD202607160071', '赵女士', 200.00, '退转付', 'FAIL', 'danger', '2026-07-17 15:10:32', NULL);

INSERT INTO t_worker_settlement_order (settlement_order_id, worker_name, period_start, period_end, amount_should_settle, deduct_amount, amount_net_settle, deposit_impact_amount, status, status_type, payout_status, payout_status_type, created_at) VALUES
('SETW202607190001', '李阿姨', '2026-07-14', '2026-07-19', 4260.00, 120.00, 4140.00, 0.00, '待审核', 'warn', '待出款', 'info', '2026-07-19 12:10:00'),
('SETW202607190002', '周阿姨', '2026-07-14', '2026-07-19', 9860.00, 300.00, 9560.00, 200.00, '待出款', 'info', '出款中', 'warn', '2026-07-19 12:15:00'),
('SETW202607180017', '陈师傅', '2026-07-07', '2026-07-13', 3120.00, 0.00, 3120.00, 0.00, '已完成', 'success', '出款成功', 'success', '2026-07-18 16:30:00');

INSERT INTO t_payment_day_end_batch (batch_no, biz_date, run_mode, batch_status, batch_status_type, payment_total_count, payment_success_count, payment_success_amount, refund_success_count, refund_success_amount, channel_abnormal_count, internal_abnormal_count, pending_refund_count, summary_comment, triggered_by, created_at, completed_at) VALUES
('DEB202607190001', '2026-07-19', 'AUTO', 'WARNING', 'warn', 2, 1, 268.00, 0, 0.00, 0, 0, 1, '支付主链路收口正常，仍有退款处理中需在下一账期继续跟进。', 'system', '2026-07-19 23:30:00', '2026-07-19 23:30:10'),
('DEB202607180001', '2026-07-18', 'AUTO', 'WARNING', 'warn', 1, 1, 3600.00, 1, 600.00, 0, 1, 0, '内部事件存在未发布成功记录，需与清分、结算和账务链路一起排查。', 'system', '2026-07-18 23:30:00', '2026-07-18 23:30:12');

INSERT INTO t_payment_task_run_log (task_log_no, task_code, task_name, run_mode, task_status, task_status_type, severity_level, severity_level_type, escalation_status, escalation_status_type, processed_count, success_count, fail_count, summary_comment, suggested_action, recommended_route, triggered_by, started_at, completed_at) VALUES
('PTL202607190001', 'PAYMENT_EXPIRE_CLOSE', '支付超时关单', 'AUTO', 'SUCCESS', 'success', 'P2', 'warn', '需关注', 'warn', 1, 1, 0, '自动关闭 1 笔已过期未收口支付单。', '继续观察超时队列并核对是否仍有未收口订单', '/payment-task-center', 'system-scheduler', '2026-07-19 11:05:00', '2026-07-19 11:05:02'),
('PTL202607190002', 'PAYMENT_EVENT_RETRY', '失败事件重发', 'MANUAL', 'SUCCESS', 'success', 'P2', 'warn', '需关注', 'warn', 1, 1, 0, '已重发 1 条失败事件，当前无剩余失败事件。', '回看事件出站结果并确认下游系统已收口', '/payment-events', 'payment-core-admin', '2026-07-19 15:30:00', '2026-07-19 15:30:03'),
('PTL202607180001', 'REFUND_FAIL_RETRY', '失败退款重试', 'MANUAL', 'WARNING', 'warn', 'P1', 'danger', '需立即升级', 'danger', 2, 1, 1, '已重试 1 笔失败退款，仍有 1 笔待人工排查。', '优先核对退款失败原因并决定是否重新提交', '/refunds', 'payment-core-admin', '2026-07-18 18:10:00', '2026-07-18 18:10:05');
