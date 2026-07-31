-- payment-core incremental data cleanup
-- date: 2026-07-31
-- purpose:
--   Acknowledge historical recursive IN_APP_OUTBOX escalation alerts that were
--   generated before source_alert_no backfill and candidate filtering were
--   introduced. Keep the rows for audit, but remove them from pending
--   escalation / delivery scope so old data no longer disturbs runtime gates.

USE housekeeping_payment_core;

UPDATE t_payment_issue_alert_log
SET ack_status = '已确认',
    ack_status_type = 'success',
    ack_operator = 'data-fix-20260731',
    ack_at = NOW(),
    triggered_by = 'payment-core-alert-cleanup-2026-07-31'
WHERE alert_channel = 'IN_APP_OUTBOX'
  AND ack_status = '待确认'
  AND source_alert_no IS NULL
  AND alert_content LIKE '升级来源告警 %';
