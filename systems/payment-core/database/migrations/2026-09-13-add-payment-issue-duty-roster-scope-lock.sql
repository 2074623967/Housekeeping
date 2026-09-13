-- payment-core 异常告警值班路由批准范围协调表增量脚本
-- 目标库：MySQL 8.x

USE housekeeping_payment_core;

CREATE TABLE IF NOT EXISTS t_payment_issue_duty_roster_scope_lock (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    issue_type VARCHAR(64) NOT NULL COMMENT '异常类型协调维度',
    severity VARCHAR(32) NOT NULL COMMENT '严重等级协调维度',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_issue_duty_roster_scope_lock (issue_type, severity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异常告警值班路由批准范围协调表';
