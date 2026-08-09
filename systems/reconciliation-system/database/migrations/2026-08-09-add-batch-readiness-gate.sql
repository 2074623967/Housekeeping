USE housekeeping_reconciliation;

ALTER TABLE t_reconciliation_batch
    ADD COLUMN readiness_status VARCHAR(32) NOT NULL DEFAULT 'NOT_CHECKED'
        COMMENT '对账准入状态' AFTER difference_count,
    ADD COLUMN readiness_summary VARCHAR(500) NULL
        COMMENT '对账准入结论' AFTER readiness_status,
    ADD COLUMN readiness_suggested_action VARCHAR(500) NULL
        COMMENT '对账准入建议动作' AFTER readiness_summary,
    ADD COLUMN readiness_blocking_owner VARCHAR(64) NULL
        COMMENT '对账准入阻断责任方' AFTER readiness_suggested_action,
    ADD COLUMN readiness_chain_count INT NOT NULL DEFAULT 0
        COMMENT '纳入准入判断的业务链路数' AFTER readiness_blocking_owner,
    ADD COLUMN readiness_completed_chain_count INT NOT NULL DEFAULT 0
        COMMENT '已完成关键事实链路数' AFTER readiness_chain_count,
    ADD COLUMN readiness_abnormal_chain_count INT NOT NULL DEFAULT 0
        COMMENT '存在异常事实的链路数' AFTER readiness_completed_chain_count,
    ADD COLUMN readiness_pending_chain_count INT NOT NULL DEFAULT 0
        COMMENT '尚未完成关键事实的链路数' AFTER readiness_abnormal_chain_count,
    ADD COLUMN readiness_checked_at DATETIME NULL
        COMMENT '最近一次准入检查时间' AFTER readiness_pending_chain_count;
