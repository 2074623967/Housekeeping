ALTER TABLE t_cross_system_fact_snapshot
    ADD COLUMN pay_scene VARCHAR(64) NULL COMMENT '支付业务场景，历史事实可为空' AFTER business_no;
