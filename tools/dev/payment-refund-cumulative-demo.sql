INSERT INTO t_refund_order (refund_order_id, payment_order_id, idempotency_key, order_no, customer_name, refund_amount, refund_method, refund_reason, channel_code, channel_refund_no, channel_submitted_at, status, status_type, applied_at, success_at, terminal_at)
VALUES ('REF202607180020', 'PAY202607180074', 'LEGACY:REF202607180020', 'ORD202607180118', '企业客户-晨星科技', 3200.00, '原路退款', '演示累计超额诊断', 'offline_bank', 'BANK-REF-202607180020', '2026-07-18 19:05:15', 'SUCCESS', 'success', '2026-07-18 19:04:11', '2026-07-18 19:07:26', '2026-07-18 19:07:26');

INSERT INTO t_refund_attempt (refund_attempt_no, refund_order_id, channel_code, channel_refund_no, attempt_status, attempt_status_type, submitted_at, completed_at, created_at)
VALUES ('RFA202607180020', 'REF202607180020', 'offline_bank', 'BANK-REF-202607180020', 'SUCCESS', 'success', '2026-07-18 19:05:15', '2026-07-18 19:07:26', '2026-07-18 19:05:10');
