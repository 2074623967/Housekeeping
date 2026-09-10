USE housekeeping_payment_core;

SET @add_receipt_query_count_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE t_payment_issue_alert_log ADD COLUMN receipt_query_count INT NOT NULL DEFAULT 0 COMMENT ''供应商回执回查尝试次数'' AFTER provider_delivery_message',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_payment_issue_alert_log'
      AND COLUMN_NAME = 'receipt_query_count'
);
PREPARE add_receipt_query_count_stmt FROM @add_receipt_query_count_sql;
EXECUTE add_receipt_query_count_stmt;
DEALLOCATE PREPARE add_receipt_query_count_stmt;

SET @add_last_receipt_query_at_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE t_payment_issue_alert_log ADD COLUMN last_receipt_query_at DATETIME DEFAULT NULL COMMENT ''最近一次供应商回执回查时间'' AFTER receipt_query_count',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_payment_issue_alert_log'
      AND COLUMN_NAME = 'last_receipt_query_at'
);
PREPARE add_last_receipt_query_at_stmt FROM @add_last_receipt_query_at_sql;
EXECUTE add_last_receipt_query_at_stmt;
DEALLOCATE PREPARE add_last_receipt_query_at_stmt;

SET @add_next_receipt_query_at_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE t_payment_issue_alert_log ADD COLUMN next_receipt_query_at DATETIME DEFAULT NULL COMMENT ''下一次允许自动回查时间'' AFTER last_receipt_query_at',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_payment_issue_alert_log'
      AND COLUMN_NAME = 'next_receipt_query_at'
);
PREPARE add_next_receipt_query_at_stmt FROM @add_next_receipt_query_at_sql;
EXECUTE add_next_receipt_query_at_stmt;
DEALLOCATE PREPARE add_next_receipt_query_at_stmt;

SET @add_receipt_query_result_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE t_payment_issue_alert_log ADD COLUMN receipt_query_result VARCHAR(32) DEFAULT NULL COMMENT ''最近一次供应商回执回查有限结果'' AFTER next_receipt_query_at',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_payment_issue_alert_log'
      AND COLUMN_NAME = 'receipt_query_result'
);
PREPARE add_receipt_query_result_stmt FROM @add_receipt_query_result_sql;
EXECUTE add_receipt_query_result_stmt;
DEALLOCATE PREPARE add_receipt_query_result_stmt;

SET @add_issue_alert_receipt_query_index_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE t_payment_issue_alert_log ADD KEY idx_issue_alert_receipt_query (provider_delivery_status, alert_status, next_receipt_query_at, receipt_query_count, created_at)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_payment_issue_alert_log'
      AND INDEX_NAME = 'idx_issue_alert_receipt_query'
);
PREPARE add_issue_alert_receipt_query_index_stmt FROM @add_issue_alert_receipt_query_index_sql;
EXECUTE add_issue_alert_receipt_query_index_stmt;
DEALLOCATE PREPARE add_issue_alert_receipt_query_index_stmt;
