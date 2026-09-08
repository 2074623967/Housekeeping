ALTER TABLE t_payment_channel_config
    ADD COLUMN route_weight INT NOT NULL DEFAULT 100 COMMENT '默认支付方式路由权重，0 表示不参与加权候选'
    AFTER priority;
