ALTER TABLE t_settlement_order
    ADD COLUMN pay_scene VARCHAR(64) DEFAULT NULL COMMENT '支付业务场景，历史数据可为空' AFTER clearing_no;
