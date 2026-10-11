-- Add the payment business scene to existing refund-center projection rows.
SET @pay_scene_column_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_refund_payment_source'
      AND COLUMN_NAME = 'pay_scene'
);
SET @pay_scene_ddl = IF(
    @pay_scene_column_exists = 0,
    'ALTER TABLE t_refund_payment_source ADD COLUMN pay_scene VARCHAR(64) NULL COMMENT ''支付业务场景''',
    'SELECT 1'
);
PREPARE pay_scene_statement FROM @pay_scene_ddl;
EXECUTE pay_scene_statement;
DEALLOCATE PREPARE pay_scene_statement;
