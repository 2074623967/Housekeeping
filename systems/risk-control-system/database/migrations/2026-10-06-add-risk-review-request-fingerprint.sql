ALTER TABLE t_risk_review_order
    ADD COLUMN request_fingerprint VARCHAR(64) NULL
        COMMENT 'v2:Base64URL无填充SHA-256；历史空值或旧版本不得批准放行'
        AFTER business_no;
