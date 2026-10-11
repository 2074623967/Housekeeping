ALTER TABLE t_risk_intercept_event
    ADD COLUMN event_key BINARY(32) NULL
        COMMENT '新事件支付单/策略/处置/来源系统SHA-256精确摘要；历史空值不回填'
        AFTER event_no;

CREATE UNIQUE INDEX uk_risk_event_key ON t_risk_intercept_event (event_key);
