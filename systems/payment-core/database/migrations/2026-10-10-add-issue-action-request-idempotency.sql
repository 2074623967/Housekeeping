CREATE TABLE t_payment_issue_action_request (
    idempotency_key_hash VARCHAR(64) NOT NULL COMMENT '规范化幂等键的 SHA-256 摘要',
    request_fingerprint VARCHAR(64) NOT NULL COMMENT '规范化请求摘要',
    claim_token VARCHAR(36) NOT NULL COMMENT '本次事务占用标记',
    response_json LONGTEXT DEFAULT NULL COMMENT '提交时脱敏的目标响应',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (idempotency_key_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付异常批量动作请求去重表';
