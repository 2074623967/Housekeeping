ALTER TABLE t_account_ledger
    ADD COLUMN pay_scene VARCHAR(64) DEFAULT NULL COMMENT '支付业务场景，历史数据可为空' AFTER biz_no;
