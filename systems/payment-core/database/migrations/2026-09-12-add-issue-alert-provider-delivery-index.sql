USE housekeeping_payment_core;

ALTER TABLE t_payment_issue_alert_log
    ADD INDEX idx_issue_alert_provider_delivery (provider_code, alert_channel, created_at);
