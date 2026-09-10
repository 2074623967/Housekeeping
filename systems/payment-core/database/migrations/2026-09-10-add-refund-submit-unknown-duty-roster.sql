INSERT INTO t_payment_issue_duty_roster (
    roster_code,
    issue_type,
    severity,
    responsibility_group,
    receiver,
    notify_channels,
    escalation_level,
    escalation_receiver,
    escalation_policy,
    escalation_timeout_minutes,
    schedule_tag,
    effective_start_hour,
    effective_end_hour,
    weekday_scope,
    holiday_strategy,
    status,
    status_type,
    updated_at
)
SELECT 'DUTY_REFUND_SUBMIT_UNKNOWN_P1',
       '退款提交结果待核查',
       'P1',
       '账务清结算联动组',
       '账清结算联动值班',
       'IN_APP,IM,SMS',
       'L1',
       '账清结算负责人',
       '30分钟未核查升级账清结算负责人',
       30,
       '退款核查全时段',
       0,
       23,
       '1,2,3,4,5,6,7',
       'ALL_DAYS',
       'ENABLED',
       'success',
       NOW()
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1
    FROM t_payment_issue_duty_roster
    WHERE roster_code = 'DUTY_REFUND_SUBMIT_UNKNOWN_P1'
);
