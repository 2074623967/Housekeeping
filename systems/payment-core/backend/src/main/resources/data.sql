INSERT INTO t_dashboard_card (card_key, title, value, badge_type, badge_text, sort_no) VALUES
('pay_success', '今日支付成功金额', '¥128,420', 'success', '较昨日 +8.4%', 1),
('refund_amount', '今日退款金额', '¥7,240', 'warn', '待复核 3 笔', 2),
('worker_pending', '待结算服务者金额', '¥89,360', 'info', '账期待生成', 3),
('recon_gap', '未关闭差异金额', '¥3,120', 'danger', '阻断级 2 笔', 4);

INSERT INTO t_order (order_no, customer_name, service_type, worker_name, order_amount, paid_amount, order_status, order_status_type, fulfillment_status, fulfillment_status_type, created_at) VALUES
('ORD202607190001', '张女士', '深度保洁', '李阿姨', 268.00, 268.00, '待履约', 'info', '已预约', 'info', '2026-07-19 09:20:11'),
('ORD202607190002', '王先生', '月嫂套餐', '周阿姨', 8800.00, 2000.00, '待支付', 'warn', '待确认', 'warn', '2026-07-19 10:02:44'),
('ORD202607180118', '企业客户-晨星科技', '企业保洁', '企业服务组', 3600.00, 3600.00, '已完成', 'success', '已验收', 'success', '2026-07-18 18:40:07');

INSERT INTO t_payment_order (payment_order_id, order_no, customer_name, amount, payment_method, channel_code, channel_transaction_no, status, status_type, created_at) VALUES
('PAY202607190001', 'ORD202607190001', '张女士', 268.00, '微信', 'wx_jsapi', 'WX99887766', 'SUCCESS', 'success', '2026-07-19 09:21:18'),
('PAY202607190002', 'ORD202607190002', '王先生', 2000.00, '支付宝', 'alipay_h5', 'ALI77665544', 'WAIT_CALLBACK', 'warn', '2026-07-19 10:03:01'),
('PAY202607180074', 'ORD202607180118', '企业客户-晨星科技', 3600.00, '银行转账', 'offline_bank', 'BANK332211', 'SUCCESS', 'success', '2026-07-18 18:41:09');

INSERT INTO t_refund_order (refund_order_id, payment_order_id, order_no, customer_name, refund_amount, refund_method, status, status_type, applied_at, success_at) VALUES
('REF202607190001', 'PAY202607190001', 'ORD202607190001', '张女士', 68.00, '原路退款', 'PROCESSING', 'warn', '2026-07-19 11:05:10', NULL),
('REF202607180019', 'PAY202607180074', 'ORD202607180118', '企业客户-晨星科技', 600.00, '原路退款', 'SUCCESS', 'success', '2026-07-18 19:00:11', '2026-07-18 19:03:26'),
('REF202607170088', 'PAY202607160031', 'ORD202607160071', '赵女士', 200.00, '退转付', 'FAIL', 'danger', '2026-07-17 15:10:32', NULL);

INSERT INTO t_worker_settlement_order (settlement_order_id, worker_name, period_start, period_end, amount_should_settle, deduct_amount, amount_net_settle, deposit_impact_amount, status, status_type, payout_status, payout_status_type, created_at) VALUES
('SETW202607190001', '李阿姨', '2026-07-14', '2026-07-19', 4260.00, 120.00, 4140.00, 0.00, '待审核', 'warn', '待出款', 'info', '2026-07-19 12:10:00'),
('SETW202607190002', '周阿姨', '2026-07-14', '2026-07-19', 9860.00, 300.00, 9560.00, 200.00, '待出款', 'info', '出款中', 'warn', '2026-07-19 12:15:00'),
('SETW202607180017', '陈师傅', '2026-07-07', '2026-07-13', 3120.00, 0.00, 3120.00, 0.00, '已完成', 'success', '出款成功', 'success', '2026-07-18 16:30:00');
