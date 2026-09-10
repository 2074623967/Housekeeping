-- payment-core 支付事件成功重发审计
-- 目标库：MySQL 8.x

USE housekeeping_payment_core;

SET @add_last_republish_operator_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE t_payment_event ADD COLUMN last_republish_operator VARCHAR(64) NULL COMMENT ''最近一次成功重发操作人'' AFTER next_retry_at',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_payment_event'
      AND COLUMN_NAME = 'last_republish_operator'
);
PREPARE add_last_republish_operator_stmt FROM @add_last_republish_operator_sql;
EXECUTE add_last_republish_operator_stmt;
DEALLOCATE PREPARE add_last_republish_operator_stmt;

SET @add_last_republished_at_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE t_payment_event ADD COLUMN last_republished_at DATETIME NULL COMMENT ''最近一次成功重发时间'' AFTER last_republish_operator',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_payment_event'
      AND COLUMN_NAME = 'last_republished_at'
);
PREPARE add_last_republished_at_stmt FROM @add_last_republished_at_sql;
EXECUTE add_last_republished_at_stmt;
DEALLOCATE PREPARE add_last_republished_at_stmt;
