-- Correct only the legacy all-clear summary generated for batches that still
-- contain payment success gaps. Re-running this update is idempotent.
UPDATE t_payment_day_end_batch
SET summary_comment = CONCAT(
        '成功差异', payment_success_gap_count, '笔（¥',
        FORMAT(payment_success_gap_amount, 2),
        '），渠道异常', channel_abnormal_count,
        '笔，内部事件异常', internal_abnormal_count,
        '笔，待收口退款', pending_refund_count,
        '笔（¥', FORMAT(pending_refund_amount, 2),
        '），需在对账与差错环节继续跟进。'
    )
WHERE payment_success_gap_count > 0
  AND summary_comment = '支付成功、渠道回调、内部事件和退款状态均已完成日终收口，可进入次日对账。';
