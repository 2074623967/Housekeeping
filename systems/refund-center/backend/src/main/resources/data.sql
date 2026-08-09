INSERT INTO t_refund_payment_source
    (payment_order_id, order_no, customer_name, paid_amount, channel_code, paid_at)
VALUES
    ('PAY-DEMO-1001', 'ORDER-HK-1001', '家政客户A', 188.00, 'WECHAT', CURRENT_TIMESTAMP);

INSERT INTO t_refund_order
    (refund_order_id, payment_order_id, order_no, customer_name, paid_amount, refund_amount,
     refund_method, refund_reason, status, idempotency_key)
VALUES
    ('REF-DEMO-1001', 'PAY-DEMO-1001', 'ORDER-HK-1001', '家政客户A', 188.00, 68.00,
     'ORIGINAL', '客户取消服务', 'REVIEWING', 'IDEMPOTENCY-DEMO-1001');

INSERT INTO t_refund_operation_log
    (log_no, refund_order_id, action_code, action_name, from_status, to_status, operator_name, operation_remark)
VALUES
    ('RLOG-DEMO-1001', 'REF-DEMO-1001', 'APPLY', '发起退款申请', 'INIT', 'REVIEWING',
     'demo-seed', '演示退款申请');

