ALTER TABLE t_payment_route_rule_config
    ADD COLUMN rollout_percentage INT NOT NULL DEFAULT 100 COMMENT '稳定分桶承接比例，0到100' AFTER fallback_channel_code,
    ADD COLUMN rollout_salt VARCHAR(64) NOT NULL DEFAULT 'stable-v1' COMMENT '稳定分桶盐值，变更会重排桶位' AFTER rollout_percentage;

ALTER TABLE t_payment_route_rule_version
    ADD COLUMN rollout_percentage INT NOT NULL DEFAULT 100 COMMENT '稳定分桶承接比例快照' AFTER fallback_channel_code,
    ADD COLUMN rollout_salt VARCHAR(64) NOT NULL DEFAULT 'stable-v1' COMMENT '稳定分桶盐值快照' AFTER rollout_percentage;

ALTER TABLE t_payment_route_rule_review
    ADD COLUMN rollout_percentage INT NOT NULL DEFAULT 100 COMMENT '候选稳定分桶承接比例' AFTER fallback_channel_code,
    ADD COLUMN rollout_salt VARCHAR(64) NOT NULL DEFAULT 'stable-v1' COMMENT '候选稳定分桶盐值' AFTER rollout_percentage;
