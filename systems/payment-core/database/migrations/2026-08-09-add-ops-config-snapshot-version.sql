USE housekeeping_payment_core;

ALTER TABLE t_payment_attempt
    ADD COLUMN ops_config_snapshot_version BIGINT NULL COMMENT '本次支付尝试消费的运营配置快照版本';

ALTER TABLE t_payment_route_record
    ADD COLUMN ops_config_snapshot_version BIGINT NULL COMMENT '本次路由消费的运营配置快照版本';

CREATE INDEX idx_attempt_ops_config_snapshot_version
    ON t_payment_attempt (ops_config_snapshot_version);

CREATE INDEX idx_route_ops_config_snapshot_version
    ON t_payment_route_record (ops_config_snapshot_version);
