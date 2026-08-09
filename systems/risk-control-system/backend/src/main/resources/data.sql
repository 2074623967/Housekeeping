INSERT INTO t_risk_policy (policy_code, policy_name, risk_dimension, hit_action, risk_level, status, status_type, updated_at) VALUES
('POLICY_DEVICE_RISK', '异常设备拦截', '设备', 'INTERCEPT', '高', 'ENABLED', 'success', '2026-08-04 10:10:00'),
('POLICY_REALNAME_WARN', '实名信息不一致预警', '身份', 'REVIEW', '中', 'ENABLED', 'success', '2026-08-04 10:12:00'),
('POLICY_PAYOUT_REGULATE', '监管付款复核', '付款', 'REVIEW', '高', 'DISABLED', 'danger', '2026-08-04 10:15:00');

INSERT INTO t_risk_limit_rule (rule_code, rule_name, target_type, scene_code, limit_value, time_window, status, status_type, updated_at) VALUES
('LIMIT_USER_DAY', '用户单日支付限额', 'USER', 'PAY_CONSUME', '3000', 'DAY', 'ENABLED', 'success', '2026-08-04 10:16:00'),
('LIMIT_MERCHANT_ORDER', '商家单笔收款限额', 'MERCHANT', 'PAY_CONSUME', '50000', 'ORDER', 'ENABLED', 'success', '2026-08-04 10:18:00'),
('LIMIT_CARD_MONTH', '银行卡月累计限额', 'CARD', 'PAY_DEPOSIT', '100000', 'MONTH', 'DISABLED', 'danger', '2026-08-04 10:20:00');

INSERT INTO t_risk_blocklist (block_code, subject_value, subject_type, reason, action_type, status, status_type, updated_at) VALUES
('BLOCK_DEVICE_01', 'DEVICE-99A1', 'DEVICE', '疑似群控设备', 'INTERCEPT', 'ENABLED', 'success', '2026-08-04 10:21:00'),
('BLOCK_PHONE_02', '13800008888', 'PHONE', '投诉欺诈高风险号码', 'REVIEW', 'ENABLED', 'success', '2026-08-04 10:22:00'),
('BLOCK_MERCHANT_03', 'MERCHANT-7788', 'MERCHANT', '疑似二清链路待核查', 'INTERCEPT', 'DISABLED', 'danger', '2026-08-04 10:24:00');

INSERT INTO t_risk_intercept_event (event_no, payment_order_id, hit_policy, risk_level, decision_result, source_system, happened_at) VALUES
('RISK-EVT-1001', 'PAY202608040001', 'POLICY_DEVICE_RISK', '高', '已拦截', 'payment-core', '2026-08-04 09:58:00'),
('RISK-EVT-1002', 'PAY202608040002', 'POLICY_REALNAME_WARN', '中', '转人工复核', 'payment-core', '2026-08-04 10:02:00'),
('RISK-EVT-1003', 'SLT202608040001', 'POLICY_PAYOUT_REGULATE', '高', '待审核', 'settlement-system', '2026-08-04 10:05:00');

INSERT INTO t_risk_review_order (review_no, business_no, risk_tag, review_item, status, status_type, reviewer, updated_at) VALUES
('REVIEW-1001', 'PAY202608040002', '实名信息不一致', '核对姓名与实名结果', 'PENDING', 'warn', NULL, '2026-08-04 10:06:00'),
('REVIEW-1002', 'SLT202608040001', '监管付款', '确认监管账户与付款用途', 'PENDING', 'warn', NULL, '2026-08-04 10:07:00'),
('REVIEW-1003', 'PAY202608030099', '历史申诉单', '核验用户补充材料', 'APPROVED', 'success', '风险审核员B', '2026-08-04 09:50:00');

INSERT INTO t_risk_monitor_rule (monitor_code, monitor_name, monitor_target, alert_threshold, notify_policy, status, status_type, updated_at) VALUES
('MONITOR_TIMEOUT', '高风险订单超时未处理', '人工复核单', '15分钟', 'IM+邮件', 'ENABLED', 'success', '2026-08-04 10:08:00'),
('MONITOR_INTERCEPT_RATE', '拦截率异常波动', '风险事件', '5分钟 > 15%', 'IM+短信', 'ENABLED', 'success', '2026-08-04 10:09:00'),
('MONITOR_PAYOUT_RISK', '监管付款待审积压', '结算出款', '待审 > 20笔', '邮件', 'DISABLED', 'danger', '2026-08-04 10:10:00');

