ALTER TABLE t_payment_attempt
    ADD COLUMN idempotency_key_bytes VARBINARY(512) GENERATED ALWAYS AS (CAST(idempotency_key AS BINARY(512)))
        COMMENT '幂等键 UTF-8 字节及补零精确唯一值' AFTER idempotency_key;

ALTER TABLE t_payment_attempt
    ADD UNIQUE KEY uk_attempt_idempotency_bytes (idempotency_key_bytes);

ALTER TABLE t_payment_attempt
    ADD KEY idx_attempt_idempotency (idempotency_key);

ALTER TABLE t_payment_attempt
    DROP INDEX uk_attempt_idempotency;
