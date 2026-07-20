CREATE TABLE `t_clearing_batch` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `batch_no` VARCHAR(32) NOT NULL COMMENT '清分批次号',
  `batch_date` DATE NOT NULL COMMENT '清分批次日期',
  `source_type` VARCHAR(32) NOT NULL COMMENT '批次来源，MANUAL/EVENT/RERUN',
  `batch_status` VARCHAR(32) NOT NULL COMMENT '批次状态，处理中/已完成/失败',
  `total_order_count` INT NOT NULL DEFAULT 0 COMMENT '订单总数',
  `success_order_count` INT NOT NULL DEFAULT 0 COMMENT '成功订单数',
  `failed_order_count` INT NOT NULL DEFAULT 0 COMMENT '失败订单数',
  `total_amount` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '订单总金额',
  `version_no` VARCHAR(16) NOT NULL COMMENT '批次版本号',
  `created_by` VARCHAR(64) NOT NULL COMMENT '创建人',
  `created_at` DATETIME NOT NULL COMMENT '创建时间',
  `finished_at` DATETIME DEFAULT NULL COMMENT '完成时间',
  `idempotency_key` VARCHAR(64) DEFAULT NULL COMMENT '幂等键',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_no` (`batch_no`),
  UNIQUE KEY `uk_idempotency_key` (`idempotency_key`),
  KEY `idx_batch_date` (`batch_date`),
  KEY `idx_batch_status` (`batch_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='清分批次主表';

CREATE TABLE `t_clearing_order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `clearing_no` VARCHAR(32) NOT NULL COMMENT '清分单号',
  `batch_no` VARCHAR(32) NOT NULL COMMENT '归属批次号',
  `payment_order_id` VARCHAR(64) NOT NULL COMMENT '支付单号',
  `order_no` VARCHAR(64) NOT NULL COMMENT '业务订单号',
  `order_amount` DECIMAL(18,2) NOT NULL COMMENT '订单支付金额',
  `merchant_amount` DECIMAL(18,2) NOT NULL COMMENT '商家应收金额',
  `worker_amount` DECIMAL(18,2) NOT NULL COMMENT '服务者应收金额',
  `platform_amount` DECIMAL(18,2) NOT NULL COMMENT '平台应收金额',
  `channel_fee_amount` DECIMAL(18,2) NOT NULL COMMENT '渠道手续费金额',
  `subsidy_amount` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '补贴金额',
  `clearing_status` VARCHAR(32) NOT NULL COMMENT '清分状态',
  `rule_no` VARCHAR(32) NOT NULL COMMENT '命中的清分规则号',
  `created_at` DATETIME NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_clearing_no` (`clearing_no`),
  UNIQUE KEY `uk_payment_order_id` (`payment_order_id`),
  KEY `idx_batch_no` (`batch_no`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_clearing_status` (`clearing_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单笔交易清分结果表';

CREATE TABLE `t_clearing_rule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_no` VARCHAR(32) NOT NULL COMMENT '规则编号',
  `rule_name` VARCHAR(128) NOT NULL COMMENT '规则名称',
  `rule_type` VARCHAR(32) NOT NULL COMMENT '规则类型',
  `rule_expression` VARCHAR(512) NOT NULL COMMENT '规则表达式',
  `rule_status` VARCHAR(32) NOT NULL COMMENT '规则状态',
  `version_no` VARCHAR(16) NOT NULL COMMENT '规则版本号',
  `grey_flag` VARCHAR(16) NOT NULL COMMENT '是否灰度',
  `created_at` DATETIME NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_no` (`rule_no`),
  KEY `idx_rule_type` (`rule_type`),
  KEY `idx_rule_status` (`rule_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='清分规则配置表';

CREATE TABLE `t_clearing_fee_rule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `fee_rule_no` VARCHAR(32) NOT NULL COMMENT '费用规则编号',
  `fee_name` VARCHAR(128) NOT NULL COMMENT '费用规则名称',
  `fee_type` VARCHAR(32) NOT NULL COMMENT '费用类型',
  `fee_mode` VARCHAR(32) NOT NULL COMMENT '计费模式，RATE/FIXED',
  `fee_rate` DECIMAL(10,6) DEFAULT NULL COMMENT '费率值',
  `fixed_amount` DECIMAL(18,2) DEFAULT NULL COMMENT '固定费用金额',
  `fee_bearer` VARCHAR(32) NOT NULL COMMENT '费用承担方',
  `status` VARCHAR(32) NOT NULL COMMENT '规则状态',
  `created_at` DATETIME NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fee_rule_no` (`fee_rule_no`),
  KEY `idx_fee_type` (`fee_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='费用规则配置表';

CREATE TABLE `t_clearing_share_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `share_item_no` VARCHAR(32) NOT NULL COMMENT '分账明细编号',
  `clearing_no` VARCHAR(32) NOT NULL COMMENT '清分单号',
  `share_type` VARCHAR(32) NOT NULL COMMENT '分账类型',
  `share_target_no` VARCHAR(64) NOT NULL COMMENT '收款对象编号',
  `share_target_name` VARCHAR(128) NOT NULL COMMENT '收款对象名称',
  `share_amount` DECIMAL(18,2) NOT NULL COMMENT '分账金额',
  `share_status` VARCHAR(32) NOT NULL COMMENT '分账状态',
  `created_at` DATETIME NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_share_item_no` (`share_item_no`),
  KEY `idx_clearing_no` (`clearing_no`),
  KEY `idx_share_type` (`share_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分账明细表';

CREATE TABLE `t_clearing_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `event_no` VARCHAR(32) NOT NULL COMMENT '事件编号',
  `event_type` VARCHAR(32) NOT NULL COMMENT '事件类型',
  `biz_no` VARCHAR(64) NOT NULL COMMENT '业务单号',
  `summary` VARCHAR(256) NOT NULL COMMENT '事件摘要',
  `payload` TEXT NOT NULL COMMENT '事件负载',
  `event_status` VARCHAR(32) NOT NULL COMMENT '事件状态',
  `created_at` DATETIME NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_no` (`event_no`),
  KEY `idx_event_type` (`event_type`),
  KEY `idx_biz_no` (`biz_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='清分事件表';
