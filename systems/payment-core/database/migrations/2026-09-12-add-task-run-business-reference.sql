-- payment-core 任务运行日志业务单据关联
-- 目标库：MySQL 8.x

USE housekeeping_payment_core;

ALTER TABLE t_payment_task_run_log
    ADD COLUMN business_ref_no VARCHAR(64) DEFAULT NULL COMMENT '关联业务单据号' AFTER biz_date,
    ADD INDEX idx_task_code_business_ref (task_code, business_ref_no);
