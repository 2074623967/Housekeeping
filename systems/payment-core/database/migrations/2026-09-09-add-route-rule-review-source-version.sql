ALTER TABLE t_payment_route_rule_review
    ADD COLUMN source_version_no INT NULL COMMENT '回滚目标历史版本号，非回滚申请为空' AFTER base_version_no;
