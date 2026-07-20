CREATE TABLE `t_settlement_batch` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `batch_no` VARCHAR(32) NOT NULL COMMENT '结算批次号',
  `batch_date` DATE NOT NULL COMMENT '结算批次日期',
  `settlement_type` VARCHAR(32) NOT NULL COMMENT '结算类型，MANUAL/EVENT/RERUN',
  `batch_status` VARCHAR(32) NOT NULL COMMENT '批次状态',
  `total_count` INT NOT NULL DEFAULT 0 COMMENT '结算单总数',
  `audited_count` INT NOT NULL DEFAULT 0 COMMENT '已审核数量',
  `payout_count` INT NOT NULL DEFAULT 0 COMMENT '已出款数量',
  `total_amount` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '结算总金额',
  `payout_channel` VARCHAR(32) NOT NULL COMMENT '出款渠道',
  `created_by` VARCHAR(64) NOT NULL COMMENT '创建人',
  `created_at` DATETIME NOT NULL COMMENT '创建时间',
  `finished_at` DATETIME DEFAULT NULL COMMENT '完成时间',
  `idempotency_key` VARCHAR(64) DEFAULT NULL COMMENT '幂等键',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_no` (`batch_no`),
  UNIQUE KEY `uk_idempotency_key` (`idempotency_key`),
  KEY `idx_batch_date` (`batch_date`),
  KEY `idx_batch_status` (`batch_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算批次主表';

CREATE TABLE `t_settlement_order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `settlement_no` VARCHAR(32) NOT NULL COMMENT '结算单号',
  `batch_no` VARCHAR(32) NOT NULL COMMENT '归属批次号',
  `target_type` VARCHAR(32) NOT NULL COMMENT '对象类型',
  `target_no` VARCHAR(64) NOT NULL COMMENT '对象编号',
  `target_name` VARCHAR(128) NOT NULL COMMENT '对象名称',
  `should_settle_amount` DECIMAL(18,2) NOT NULL COMMENT '应结金额',
  `deduct_amount` DECIMAL(18,2) NOT NULL COMMENT '扣减金额',
  `net_settle_amount` DECIMAL(18,2) NOT NULL COMMENT '实结金额',
  `settlement_status` VARCHAR(32) NOT NULL COMMENT '结算状态',
  `payout_status` VARCHAR(32) NOT NULL COMMENT '出款状态',
  `audit_status` VARCHAR(32) NOT NULL COMMENT '审核状态',
  `clearing_no` VARCHAR(32) DEFAULT NULL COMMENT '来源清分单号',
  `created_at` DATETIME NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_no` (`settlement_no`),
  KEY `idx_batch_no` (`batch_no`),
  KEY `idx_target_no` (`target_no`),
  KEY `idx_settlement_status` (`settlement_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算单主表';

CREATE TABLE `t_settlement_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `item_no` VARCHAR(32) NOT NULL COMMENT '明细号',
  `settlement_no` VARCHAR(32) NOT NULL COMMENT '结算单号',
  `item_type` VARCHAR(32) NOT NULL COMMENT '明细类型',
  `item_name` VARCHAR(128) NOT NULL COMMENT '明细名称',
  `item_amount` DECIMAL(18,2) NOT NULL COMMENT '明细金额',
  `created_at` DATETIME NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_item_no` (`item_no`),
  KEY `idx_settlement_no` (`settlement_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算明细项表';

CREATE TABLE `t_settlement_audit_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `audit_no` VARCHAR(32) NOT NULL COMMENT '审核日志号',
  `settlement_no` VARCHAR(32) NOT NULL COMMENT '结算单号',
  `audit_action` VARCHAR(32) NOT NULL COMMENT '审核动作',
  `audit_result` VARCHAR(32) NOT NULL COMMENT '审核结果',
  `operator_name` VARCHAR(64) NOT NULL COMMENT '操作人',
  `remark` VARCHAR(256) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_audit_no` (`audit_no`),
  KEY `idx_settlement_no` (`settlement_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算审核日志表';

CREATE TABLE `t_payout_batch` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `payout_batch_no` VARCHAR(32) NOT NULL COMMENT '出款批次号',
  `batch_no` VARCHAR(32) NOT NULL COMMENT '结算批次号',
  `payout_channel` VARCHAR(32) NOT NULL COMMENT '出款渠道',
  `payout_status` VARCHAR(32) NOT NULL COMMENT '出款状态',
  `payout_count` INT NOT NULL DEFAULT 0 COMMENT '出款笔数',
  `success_count` INT NOT NULL DEFAULT 0 COMMENT '成功笔数',
  `failed_count` INT NOT NULL DEFAULT 0 COMMENT '失败笔数',
  `total_amount` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '出款总金额',
  `created_by` VARCHAR(64) NOT NULL COMMENT '创建人',
  `created_at` DATETIME NOT NULL COMMENT '创建时间',
  `finished_at` DATETIME DEFAULT NULL COMMENT '完成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payout_batch_no` (`payout_batch_no`),
  KEY `idx_batch_no` (`batch_no`),
  KEY `idx_payout_status` (`payout_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出款批次表';

CREATE TABLE `t_payout_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `payout_no` VARCHAR(32) NOT NULL COMMENT '出款记录号',
  `payout_batch_no` VARCHAR(32) NOT NULL COMMENT '出款批次号',
  `settlement_no` VARCHAR(32) NOT NULL COMMENT '结算单号',
  `target_no` VARCHAR(64) NOT NULL COMMENT '收款对象编号',
  `target_name` VARCHAR(128) NOT NULL COMMENT '收款对象名称',
  `payout_amount` DECIMAL(18,2) NOT NULL COMMENT '出款金额',
  `payout_status` VARCHAR(32) NOT NULL COMMENT '出款状态',
  `retry_count` INT NOT NULL DEFAULT 0 COMMENT '重试次数',
  `created_at` DATETIME NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payout_no` (`payout_no`),
  KEY `idx_payout_batch_no` (`payout_batch_no`),
  KEY `idx_settlement_no` (`settlement_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出款记录表';

CREATE TABLE `t_settlement_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `event_no` VARCHAR(32) NOT NULL COMMENT '事件号',
  `event_type` VARCHAR(32) NOT NULL COMMENT '事件类型',
  `biz_no` VARCHAR(64) NOT NULL COMMENT '业务单号',
  `summary` VARCHAR(256) NOT NULL COMMENT '摘要',
  `payload` TEXT NOT NULL COMMENT '事件负载',
  `event_status` VARCHAR(32) NOT NULL COMMENT '事件状态',
  `created_at` DATETIME NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_no` (`event_no`),
  KEY `idx_event_type` (`event_type`),
  KEY `idx_biz_no` (`biz_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算事件表';
