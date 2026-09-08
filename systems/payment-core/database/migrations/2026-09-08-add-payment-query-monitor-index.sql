USE housekeeping_payment_core;

CREATE INDEX idx_notify_status_created
    ON t_payment_notify_log (notify_status, created_at);
