INSERT INTO t_reconciliation_batch
    (batch_no, business_date, channel_code, bill_source, status, channel_count, internal_count, matched_count, difference_count)
VALUES
    ('REC-DEMO-1001', CURRENT_DATE, 'WECHAT', 'CHANNEL_FILE', 'COMPLETED', 3, 3, 2, 1);

INSERT INTO t_reconciliation_channel_record
    (batch_no, channel_trade_no, payment_order_id, amount, trade_status, trade_time)
VALUES
    ('REC-DEMO-1001', 'WX-TRADE-1001', 'PAY-DEMO-1001', 188.00, 'SUCCESS', CURRENT_TIMESTAMP),
    ('REC-DEMO-1001', 'WX-TRADE-1002', 'PAY-DEMO-1002', 68.00, 'SUCCESS', CURRENT_TIMESTAMP),
    ('REC-DEMO-1001', 'WX-TRADE-1003', 'PAY-DEMO-1003', 20.00, 'SUCCESS', CURRENT_TIMESTAMP);

INSERT INTO t_reconciliation_internal_record
    (batch_no, payment_order_id, amount, internal_status, source_system, paid_time)
VALUES
    ('REC-DEMO-1001', 'PAY-DEMO-1001', 188.00, 'SUCCESS', 'payment-core', CURRENT_TIMESTAMP),
    ('REC-DEMO-1001', 'PAY-DEMO-1002', 60.00, 'SUCCESS', 'payment-core', CURRENT_TIMESTAMP),
    ('REC-DEMO-1001', 'PAY-DEMO-1003', 20.00, 'SUCCESS', 'payment-core', CURRENT_TIMESTAMP);

INSERT INTO t_reconciliation_difference
    (difference_no, batch_no, difference_type, payment_order_id, channel_amount, internal_amount, status)
VALUES
    ('DIFF-DEMO-1001', 'REC-DEMO-1001', 'AMOUNT_MISMATCH', 'PAY-DEMO-1002', 68.00, 60.00, 'OPEN');

