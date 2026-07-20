INSERT INTO t_dashboard_card (card_key, title, value, badge_type, badge_text, sort_no) VALUES
('pay_success', '今日支付成功金额', '¥128,420', 'success', '较昨日 +8.4%', 1),
('refund_amount', '今日退款金额', '¥7,240', 'warn', '待复核 3 笔', 2),
('worker_pending', '待结算服务者金额', '¥89,360', 'info', '账期待生成', 3),
('recon_gap', '未关闭差异金额', '¥3,120', 'danger', '阻断级 2 笔', 4);

INSERT INTO t_payment_channel_config (channel_code, channel_name, payment_method, merchant_no, scene_scope, status, status_type, daily_limit, priority, updated_at) VALUES
('wx_h5', '微信 H5 支付', '微信支付', 'MCH_WX_10001', 'H5/APP/小程序', 'ENABLED', 'success', 500000.00, 1, '2026-07-20 22:40:00'),
('alipay_h5', '支付宝 H5 支付', '支付宝', 'MCH_ALI_20001', 'H5/APP/PC', 'ENABLED', 'success', 500000.00, 2, '2026-07-20 22:40:00'),
('offline_bank', '线下银行转账', '银行转账', 'BANK_OFFLINE_30001', '企业客户/大额订单', 'ENABLED', 'success', 2000000.00, 3, '2026-07-20 22:40:00');

INSERT INTO t_payment_route_rule_config (rule_code, rule_name, match_scene, match_expression, target_channel_code, fallback_channel_code, status, status_type, priority, updated_at) VALUES
('RULE_HOME_WX', '家政 H5 微信优先', 'HOME_CLEAN/H5', 'paymentMethod=微信支付 AND amount<=5000', 'wx_h5', 'alipay_h5', 'ENABLED', 'success', 1, '2026-07-20 22:40:00'),
('RULE_HOME_ALI', '家政 H5 支付宝兜底', 'HOME_CLEAN/H5', 'paymentMethod=支付宝 AND amount<=5000', 'alipay_h5', 'wx_h5', 'ENABLED', 'success', 2, '2026-07-20 22:40:00'),
('RULE_ENTERPRISE_BANK', '企业大额订单走线下银行', 'ENTERPRISE/PC', 'amount>5000 OR customerType=ENTERPRISE', 'offline_bank', 'alipay_h5', 'ENABLED', 'success', 3, '2026-07-20 22:40:00');

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

INSERT INTO t_payment_event (event_no, event_type, payment_order_id, biz_no, event_payload, created_at) VALUES
('EVT202607190001', 'PAYMENT_SUCCESS', 'PAY202607190001', 'ORD202607190001', '{"amount":268.00}', '2026-07-19 09:21:23'),
('EVT202607190002', 'PAYMENT_PENDING', 'PAY202607190002', 'ORD202607190002', '{"amount":6800.00}', '2026-07-19 10:03:19'),
('EVT202607180074', 'PAYMENT_SUCCESS', 'PAY202607180074', 'ORD202607180118', '{"amount":3600.00}', '2026-07-18 18:41:21');

INSERT INTO t_refund_order (refund_order_id, payment_order_id, order_no, customer_name, refund_amount, refund_method, status, status_type, applied_at, success_at) VALUES
('REF202607190001', 'PAY202607190001', 'ORD202607190001', '张女士', 68.00, '原路退款', 'PROCESSING', 'warn', '2026-07-19 11:05:10', NULL),
('REF202607180019', 'PAY202607180074', 'ORD202607180118', '企业客户-晨星科技', 600.00, '原路退款', 'SUCCESS', 'success', '2026-07-18 19:00:11', '2026-07-18 19:03:26'),
('REF202607170088', 'PAY202607160031', 'ORD202607160071', '赵女士', 200.00, '退转付', 'FAIL', 'danger', '2026-07-17 15:10:32', NULL);

INSERT INTO t_worker_settlement_order (settlement_order_id, worker_name, period_start, period_end, amount_should_settle, deduct_amount, amount_net_settle, deposit_impact_amount, status, status_type, payout_status, payout_status_type, created_at) VALUES
('SETW202607190001', '李阿姨', '2026-07-14', '2026-07-19', 4260.00, 120.00, 4140.00, 0.00, '待审核', 'warn', '待出款', 'info', '2026-07-19 12:10:00'),
('SETW202607190002', '周阿姨', '2026-07-14', '2026-07-19', 9860.00, 300.00, 9560.00, 200.00, '待出款', 'info', '出款中', 'warn', '2026-07-19 12:15:00'),
('SETW202607180017', '陈师傅', '2026-07-07', '2026-07-13', 3120.00, 0.00, 3120.00, 0.00, '已完成', 'success', '出款成功', 'success', '2026-07-18 16:30:00');
