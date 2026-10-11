ALTER TABLE t_risk_review_order
    ADD COLUMN business_key BINARY(32) NULL
        COMMENT '新复核单原业务单号SHA-256精确摘要；历史空值不回填'
        AFTER business_no;

CREATE UNIQUE INDEX uk_risk_review_business_key ON t_risk_review_order (business_key);
