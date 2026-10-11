UPDATE t_payment_order SET status = 'CLOSED'
WHERE payment_order_id = 'PAY202607190002';
UPDATE t_payment_order SET status = 'RISK_REVIEW'
WHERE payment_order_id = 'PAY202607190001';
UPDATE t_payment_order SET status = 'FAIL'
WHERE payment_order_id = 'PAY202607180074';

UPDATE t_prepay_order SET cashier_status = '支付成功', cashier_status_type = 'success'
WHERE prepay_order_no IN ('PRE202607190002', 'PRE202607190001', 'PRE202607180118');
