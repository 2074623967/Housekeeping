INSERT INTO t_wallet_owner(wallet_owner_id, owner_type, owner_name, owner_status, biz_line_code, tenant_code, ext_ref_no)
VALUES ('WO-USER-001', 'USER', '王阿姨', 'ENABLED', 'HOUSEKEEPING', 'DEFAULT', 'U10001');

INSERT INTO t_wallet_owner(wallet_owner_id, owner_type, owner_name, owner_status, biz_line_code, tenant_code, ext_ref_no)
VALUES ('WO-WORKER-001', 'WORKER', '李师傅', 'ENABLED', 'HOUSEKEEPING', 'DEFAULT', 'W20001');

INSERT INTO t_wallet_account(wallet_account_no, wallet_owner_id, owner_type, owner_name, account_type, account_scene, currency_code,
                             account_status, allow_credit, risk_level, total_balance, available_balance, frozen_balance,
                             pending_in_balance, pending_out_balance)
VALUES ('WA-USER-001', 'WO-USER-001', 'USER', '王阿姨', 'MAIN', 'USER_STORE', 'CNY',
        'ACTIVE', 0, 'LOW', 268.00, 240.00, 20.00, 8.00, 0.00);

INSERT INTO t_wallet_account(wallet_account_no, wallet_owner_id, owner_type, owner_name, account_type, account_scene, currency_code,
                             account_status, allow_credit, risk_level, total_balance, available_balance, frozen_balance,
                             pending_in_balance, pending_out_balance)
VALUES ('WA-WORKER-001', 'WO-WORKER-001', 'WORKER', '李师傅', 'MAIN', 'WORKER_INCOME', 'CNY',
        'FROZEN', 0, 'MEDIUM', 1260.00, 1000.00, 200.00, 80.00, 20.00);

INSERT INTO t_wallet_flow(flow_no, wallet_account_no, flow_type, source_system, source_biz_no, idempotency_key,
                          change_amount, before_available_balance, after_available_balance, operator_name, operation_reason)
VALUES ('WF-0001', 'WA-USER-001', 'OPEN_ACCOUNT', 'wallet-account', 'OPEN-WA-USER-001', 'OPEN-WA-USER-001',
        0.00, 0.00, 0.00, 'system', '账户开户');

INSERT INTO t_wallet_flow(flow_no, wallet_account_no, flow_type, source_system, source_biz_no, idempotency_key,
                          change_amount, before_available_balance, after_available_balance, operator_name, operation_reason)
VALUES ('WF-0002', 'WA-USER-001', 'PENDING_IN', 'payment-core', 'PAY-RECHARGE-001', 'PENDING-IN-PAY-RECHARGE-001',
        8.00, 232.00, 240.00, 'system', '充值在途入账');

INSERT INTO t_wallet_flow(flow_no, wallet_account_no, flow_type, source_system, source_biz_no, idempotency_key,
                          change_amount, before_available_balance, after_available_balance, operator_name, operation_reason)
VALUES ('WF-0003', 'WA-WORKER-001', 'FREEZE', 'wallet-withdraw', 'WD-LOCK-001', 'FREEZE-WD-LOCK-001',
        200.00, 1200.00, 1000.00, 'finance_bot', '提现预冻结');
