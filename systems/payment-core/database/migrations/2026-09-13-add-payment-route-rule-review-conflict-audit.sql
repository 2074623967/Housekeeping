USE housekeeping_payment_core;

ALTER TABLE t_payment_route_rule_review
    ADD COLUMN priority_collision_detected TINYINT(1) NULL
        COMMENT '批准复验是否检测到同优先级潜在冲突，未执行批准复验为空' AFTER review_comment,
    ADD COLUMN priority_collision_acknowledged TINYINT(1) NULL
        COMMENT '检测到潜在冲突时是否完成显式风险确认，未执行批准复验为空' AFTER priority_collision_detected;
