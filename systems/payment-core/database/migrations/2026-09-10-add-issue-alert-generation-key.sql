-- 异常 SLA 告警生成幂等键：并发巡检最多生成一条有效初次告警或来源升级告警。
ALTER TABLE t_payment_issue_alert_log
    ADD COLUMN generation_key VARCHAR(191) DEFAULT NULL
        COMMENT '告警生成幂等键，确认后的初次告警释放'
        AFTER source_alert_no;

-- 历史待确认初次 outbox 可能存在并发重复，仅为每个异常最早一条占用活动幂等键。
UPDATE t_payment_issue_alert_log alert_log
LEFT JOIN t_payment_issue_alert_log earlier
       ON earlier.issue_no = alert_log.issue_no
      AND earlier.alert_channel = 'IN_APP_OUTBOX'
      AND earlier.source_alert_no IS NULL
      AND earlier.alert_content NOT LIKE '升级来源告警 %'
      AND earlier.ack_status = '待确认'
      AND earlier.id < alert_log.id
SET alert_log.generation_key = CONCAT('ISSUE_ACTIVE:', alert_log.issue_no)
WHERE alert_log.alert_channel = 'IN_APP_OUTBOX'
  AND alert_log.source_alert_no IS NULL
  AND alert_log.alert_content NOT LIKE '升级来源告警 %'
  AND alert_log.ack_status = '待确认'
  AND earlier.id IS NULL;

-- 新版升级 outbox 已保存来源告警号；历史文本兜底仍由既有 NOT EXISTS 规则防重。
UPDATE t_payment_issue_alert_log alert_log
LEFT JOIN t_payment_issue_alert_log earlier
       ON earlier.source_alert_no = alert_log.source_alert_no
      AND earlier.alert_channel = 'IN_APP_OUTBOX'
      AND earlier.id < alert_log.id
SET alert_log.generation_key = CONCAT('ISSUE_ESCALATION:', alert_log.source_alert_no)
WHERE alert_log.alert_channel = 'IN_APP_OUTBOX'
  AND alert_log.source_alert_no IS NOT NULL
  AND earlier.id IS NULL;

ALTER TABLE t_payment_issue_alert_log
    ADD UNIQUE KEY uk_issue_alert_generation (generation_key);
