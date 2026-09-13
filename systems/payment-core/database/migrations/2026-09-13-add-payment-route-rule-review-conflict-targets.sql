USE housekeeping_payment_core;

ALTER TABLE t_payment_route_rule_review
    ADD COLUMN priority_collision_rule_codes_json TEXT NULL
        COMMENT '批准复验检测到的对照规则编码 JSON 数组快照' AFTER priority_collision_acknowledged;
