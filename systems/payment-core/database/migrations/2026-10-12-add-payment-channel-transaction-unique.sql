USE housekeeping_payment_core;

ALTER TABLE t_payment_order
    ADD UNIQUE KEY uk_payment_channel_transaction (channel_code, channel_transaction_no);
