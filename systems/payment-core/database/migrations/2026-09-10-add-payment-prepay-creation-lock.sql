-- payment-core 预付单创建并发协调表增量脚本
-- 目标库：MySQL 8.x

USE housekeeping_payment_core;

CREATE TABLE IF NOT EXISTS t_payment_prepay_creation_lock (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    order_no VARCHAR(64) NOT NULL COMMENT '订单维度协调键',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_prepay_creation_order (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预付单创建并发协调表';
