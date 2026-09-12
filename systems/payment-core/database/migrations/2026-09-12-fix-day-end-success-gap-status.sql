-- Correct historical derived statuses produced before payment success gaps were
-- included in the day-end warning rule. Re-running this update is idempotent.
UPDATE t_payment_day_end_batch
SET batch_status = 'WARNING',
    batch_status_type = 'warn'
WHERE payment_success_gap_count > 0
  AND batch_status = 'COMPLETED';
