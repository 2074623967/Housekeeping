-- payment-core incremental migration
-- date: 2026-07-31
-- purpose:
--   Backfill the task-center lease table into the original
--   `housekeeping_payment_core` runtime database without rebuilding the whole
--   schema, so the latest backend can run scheduled task coordination safely.

USE housekeeping_payment_core;

CREATE TABLE IF NOT EXISTS t_payment_task_lease (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    task_code VARCHAR(64) NOT NULL COMMENT '任务编码',
    lock_owner VARCHAR(128) DEFAULT NULL COMMENT '当前持锁实例或执行者',
    lock_expires_at DATETIME DEFAULT NULL COMMENT '租约过期时间',
    updated_at DATETIME NOT NULL COMMENT '最近更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_code (task_code),
    KEY idx_lock_expires_at (lock_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付任务分布式租约锁表';
