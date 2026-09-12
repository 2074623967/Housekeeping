-- payment-core 任务运行日志业务日关联
-- 目标库：MySQL 8.x

USE housekeeping_payment_core;

ALTER TABLE t_payment_task_run_log
    ADD COLUMN biz_date DATE DEFAULT NULL COMMENT '关联业务日' AFTER task_name,
    ADD INDEX idx_task_code_biz_date (task_code, biz_date);
