INSERT INTO t_clearing_rule (
    rule_no,
    rule_name,
    rule_type,
    rule_expression,
    rule_status,
    version_no,
    grey_flag,
    created_at
) VALUES (
    'CLR30001',
    '家政订单基础清分规则',
    'ORDER',
    '平台=8%, 渠道=固定1元, 服务者=余下*90%, 商家=剩余',
    '启用',
    'V1',
    '否',
    '2026-07-20 09:00:00'
);

INSERT INTO t_clearing_fee_rule (
    fee_rule_no,
    fee_name,
    fee_type,
    fee_mode,
    fee_rate,
    fixed_amount,
    fee_bearer,
    status,
    created_at
) VALUES
(
    'FEE40001',
    '平台服务费',
    'PLATFORM_FEE',
    'RATE',
    0.080000,
    0.00,
    '用户',
    '启用',
    '2026-07-20 09:05:00'
),
(
    'FEE40002',
    '渠道手续费',
    'CHANNEL_FEE',
    'FIXED',
    0.000000,
    1.00,
    '平台',
    '启用',
    '2026-07-20 09:06:00'
);

INSERT INTO t_clearing_batch (
    batch_no,
    batch_date,
    source_type,
    batch_status,
    total_order_count,
    success_order_count,
    failed_order_count,
    total_amount,
    version_no,
    created_by,
    created_at,
    finished_at,
    idempotency_key
) VALUES (
    'CLB10001',
    '2026-07-20',
    'EVENT',
    '已完成',
    1,
    1,
    0,
    168.00,
    'V1',
    '系统事件',
    '2026-07-20 10:00:00',
    '2026-07-20 10:01:00',
    'EVT-PAY202607200001'
);

INSERT INTO t_clearing_order (
    clearing_no,
    batch_no,
    payment_order_id,
    order_no,
    order_amount,
    merchant_amount,
    worker_amount,
    platform_amount,
    channel_fee_amount,
    subsidy_amount,
    clearing_status,
    rule_no,
    created_at
) VALUES (
    'CLO20001',
    'CLB10001',
    'PAY202607200001',
    'ORD202607200001',
    168.00,
    16.80,
    136.76,
    13.44,
    1.00,
    0.00,
    '清分成功',
    'CLR30001',
    '2026-07-20 10:00:30'
);

INSERT INTO t_clearing_share_item (
    share_item_no,
    clearing_no,
    share_type,
    share_target_no,
    share_target_name,
    share_amount,
    share_status,
    created_at
) VALUES
(
    'SHR50001',
    'CLO20001',
    'WORKER',
    'WRK1001',
    '李阿姨',
    136.76,
    '待结算',
    '2026-07-20 10:00:31'
),
(
    'SHR50002',
    'CLO20001',
    'MERCHANT',
    'MCH1001',
    '上海静安门店',
    16.80,
    '待结算',
    '2026-07-20 10:00:32'
),
(
    'SHR50003',
    'CLO20001',
    'PLATFORM',
    'PLT1001',
    '家政平台',
    13.44,
    '待结算',
    '2026-07-20 10:00:33'
);

INSERT INTO t_clearing_event (
    event_no,
    event_type,
    biz_no,
    summary,
    payload,
    event_status,
    created_at
) VALUES (
    'EVT60001',
    'PAYMENT_SUCCESS',
    'PAY202607200001',
    '张女士 支付成功后触发清分',
    '{"paymentOrderId":"PAY202607200001","orderNo":"ORD202607200001"}',
    '已消费',
    '2026-07-20 10:01:00'
);
