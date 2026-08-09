ALTER TABLE t_clearing_order
    ADD COLUMN business_chain_id VARCHAR(96) NULL COMMENT '跨系统业务链路ID';

UPDATE t_clearing_order
SET business_chain_id = CONCAT('CHAIN-', payment_order_id)
WHERE business_chain_id IS NULL;

ALTER TABLE t_clearing_order
    MODIFY COLUMN business_chain_id VARCHAR(96) NOT NULL COMMENT '跨系统业务链路ID',
    ADD KEY idx_business_chain_id (business_chain_id);

ALTER TABLE t_clearing_event
    ADD COLUMN business_chain_id VARCHAR(96) NULL COMMENT '跨系统业务链路ID';

UPDATE t_clearing_event
SET business_chain_id = CONCAT('CHAIN-', biz_no)
WHERE business_chain_id IS NULL;

ALTER TABLE t_clearing_event
    MODIFY COLUMN business_chain_id VARCHAR(96) NOT NULL COMMENT '跨系统业务链路ID',
    ADD KEY idx_event_business_chain_id (business_chain_id);
