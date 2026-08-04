INSERT INTO t_deposit_account
    (account_no, owner_id, owner_type, required_amount, balance, frozen_amount, status)
VALUES
    ('DEP-DEMO-1001', 'WORKER-1001', 'WORKER', 500.00, 500.00, 100.00, 'OPEN');

INSERT INTO t_deposit_flow
    (flow_no, account_no, flow_type, amount, before_balance, after_balance,
     before_frozen_amount, after_frozen_amount, reference_no, remark)
VALUES
    ('DFLOW-DEMO-1001', 'DEP-DEMO-1001', 'COLLECT', 500.00, 0.00, 500.00,
     0.00, 0.00, 'PAY-DEMO-1001', '演示保证金收取');
