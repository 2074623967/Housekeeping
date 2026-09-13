-- payment-core 支付路由规则冲突复验协调表增量脚本
-- 目标库：MySQL 8.x

USE housekeeping_payment_core;

CREATE TABLE IF NOT EXISTS t_payment_route_rule_conflict_lock (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    lock_name VARCHAR(64) NOT NULL COMMENT '冲突全集协调维度',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_route_rule_conflict_lock (lock_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付路由规则冲突复验协调表';
