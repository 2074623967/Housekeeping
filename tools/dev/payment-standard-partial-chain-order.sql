INSERT INTO t_order (order_no, customer_name, service_type, worker_name, order_amount, paid_amount, order_status, order_status_type, fulfillment_status, fulfillment_status_type, created_at)
VALUES ('LOCAL-HAPPY-STANDARD-PARTIAL', 'local happy path', '深度保洁', 'local worker', 88.00, 20.00, '待支付', 'warn', '待确认', 'warn', CURRENT_TIMESTAMP);
