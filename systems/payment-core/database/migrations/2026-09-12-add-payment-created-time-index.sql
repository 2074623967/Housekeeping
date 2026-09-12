ALTER TABLE t_payment_order
    ADD INDEX idx_payment_created_at (created_at);
