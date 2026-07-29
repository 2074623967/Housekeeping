INSERT INTO t_account_subject (subject_id, subject_type, subject_name, owner_name, status, created_at) VALUES
('SUB1001', 'CUSTOMER', '张女士', '张女士', '启用', '2026-07-20 10:00:00'),
('SUB1002', 'WORKER', '李阿姨', '李阿姨', '启用', '2026-07-20 10:01:00'),
('SUB1003', 'PLATFORM', '家政平台主体', '财务中心', '启用', '2026-07-20 10:02:00');

INSERT INTO t_account (account_no, subject_id, subject_name, account_type, account_status, currency, created_at, last_change_at) VALUES
('ACT10001', 'SUB1001', '张女士', '用户可用账户', '正常', 'CNY', '2026-07-20 10:00:00', '2026-07-20 10:08:00'),
('ACT10002', 'SUB1002', '李阿姨', '服务者应收账户', '正常', 'CNY', '2026-07-20 10:01:00', '2026-07-20 10:06:00'),
('ACT10003', 'SUB1003', '家政平台主体', '平台手续费账户', '正常', 'CNY', '2026-07-20 10:02:00', '2026-07-20 10:07:00');

INSERT INTO t_account_balance (account_no, available_amount, frozen_amount, in_transit_amount, updated_at) VALUES
('ACT10001', 100.00, 68.00, 0.00, '2026-07-20 10:08:00'),
('ACT10002', 120.00, 0.00, 0.00, '2026-07-20 10:06:00'),
('ACT10003', 12.00, 0.00, 0.00, '2026-07-20 10:07:00');

INSERT INTO t_account_ledger (ledger_no, account_no, biz_type, biz_no, direction, amount, before_balance, after_balance, ledger_status, operator_name, created_at) VALUES
('LDG20001', 'ACT10001', 'PAYMENT_SUCCESS', 'PAY202607200001', '贷方', 168.00, 0.00, 168.00, '已记账', '系统入账', '2026-07-20 10:03:00'),
('LDG20002', 'ACT10001', 'BALANCE_FREEZE', 'ORD202607200001', '借方', 68.00, 168.00, 100.00, '已记账', '运营冻结', '2026-07-20 10:08:00'),
('LDG20003', 'ACT10002', 'CLEARING_RESULT', 'CLR202607200001', '贷方', 120.00, 0.00, 120.00, '已记账', '清分入账', '2026-07-20 10:06:00'),
('LDG20004', 'ACT10003', 'SERVICE_FEE', 'FEE202607200001', '贷方', 12.00, 0.00, 12.00, '已记账', '平台服务费入账', '2026-07-20 10:07:00');

INSERT INTO t_account_freeze (freeze_no, account_no, biz_no, freeze_type, freeze_reason, freeze_amount, freeze_status, operator_name, created_at, unfrozen_at) VALUES
('FRZ30001', 'ACT10001', 'ORD202607200001', '履约担保', '服务履约担保', 68.00, '冻结中', '运营冻结', '2026-07-20 10:08:00', NULL);

INSERT INTO t_account_adjustment (adjust_no, account_no, adjust_direction, adjust_amount, adjust_reason, adjust_status, created_by, approved_by, created_at, approved_at) VALUES
('ADJ40001', 'ACT10002', '贷方', 8.00, '履约奖励补贴', '待审批', '财务小李', NULL, '2026-07-20 10:09:00', NULL);

INSERT INTO t_account_event (event_no, event_type, biz_no, summary, payload, event_status, created_at) VALUES
('EVT50001', 'PAYMENT_SUCCESS', 'PAY202607200001', '消费支付成功后用户入账', '{"paymentOrderId":"PAY202607200001","amount":168.00}', '已消费', '2026-07-20 10:10:00');
