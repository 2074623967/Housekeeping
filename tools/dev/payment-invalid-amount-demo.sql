UPDATE t_payment_attempt
SET completed_at = '2026-07-19 09:21:22'
WHERE attempt_no = 'ATT202607190001';

UPDATE t_payment_order
SET amount = 0.00
WHERE payment_order_id = 'PAY202607190001';

UPDATE t_payment_order
SET amount = -5.00
WHERE payment_order_id = 'PAY202607180074';
