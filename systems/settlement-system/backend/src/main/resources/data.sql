INSERT INTO t_settlement_batch (
    batch_no,
    batch_date,
    settlement_type,
    batch_status,
    total_count,
    audited_count,
    payout_count,
    total_amount,
    payout_channel,
    created_by,
    created_at,
    finished_at,
    idempotency_key
) VALUES (
    'SET10001',
    '2026-07-20',
    'MANUAL',
    '待审核',
    2,
    0,
    0,
    132.00,
    'BANK',
    '结算运营',
    '2026-07-20 09:00:00',
    NULL,
    'SETTLE-20260720-INIT'
);

INSERT INTO t_settlement_order (
    settlement_no,
    batch_no,
    target_type,
    target_no,
    target_name,
    should_settle_amount,
    deduct_amount,
    net_settle_amount,
    settlement_status,
    payout_status,
    audit_status,
    clearing_no,
    created_at
) VALUES
(
    'SLT20001',
    'SET10001',
    'WORKER',
    'WRK1001',
    '李阿姨',
    120.00,
    8.00,
    112.00,
    '待审核',
    '待出款',
    '待审核',
    'CLO20001',
    '2026-07-20 09:05:00'
),
(
    'SLT20002',
    'SET10001',
    'MERCHANT',
    'MCH1001',
    '上海静安门店',
    20.00,
    0.00,
    20.00,
    '待审核',
    '待出款',
    '待审核',
    'CLO20001',
    '2026-07-20 09:06:00'
);

INSERT INTO t_settlement_item (
    item_no,
    settlement_no,
    item_type,
    item_name,
    item_amount,
    created_at
) VALUES
(
    'ITM30001',
    'SLT20001',
    '基础结算',
    '应结金额',
    120.00,
    '2026-07-20 09:05:30'
),
(
    'ITM30002',
    'SLT20001',
    '结算扣减',
    '扣减金额',
    8.00,
    '2026-07-20 09:05:31'
),
(
    'ITM30003',
    'SLT20002',
    '商家结算',
    '应结金额',
    20.00,
    '2026-07-20 09:06:30'
);

INSERT INTO t_settlement_audit_log (
    audit_no,
    settlement_no,
    audit_action,
    audit_result,
    operator_name,
    remark,
    created_at
) VALUES
(
    'AUD40001',
    'SLT20001',
    '创建结算单',
    '待审核',
    '系统',
    '',
    '2026-07-20 09:05:40'
),
(
    'AUD40002',
    'SLT20002',
    '创建结算单',
    '待审核',
    '系统',
    '',
    '2026-07-20 09:06:40'
);

INSERT INTO t_settlement_event (
    event_no,
    event_type,
    biz_no,
    summary,
    payload,
    event_status,
    created_at
) VALUES (
    'SVE70001',
    'CLEARING_GENERATED',
    'CLO20001',
    '清分结果生成后自动生成结算单',
    '{"clearingNo":"CLO20001"}',
    '已消费',
    '2026-07-20 09:10:00'
);
