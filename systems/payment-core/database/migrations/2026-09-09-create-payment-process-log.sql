-- 将支付处理日志从多表查询投影升级为独立、不可变的事实模型。

CREATE TABLE IF NOT EXISTS t_payment_process_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    log_no VARCHAR(64) NOT NULL COMMENT '处理日志编号',
    payment_order_id VARCHAR(64) NOT NULL COMMENT '关联支付单号',
    order_no VARCHAR(64) DEFAULT NULL COMMENT '关联业务订单号',
    request_no VARCHAR(64) DEFAULT NULL COMMENT '关联支付请求号',
    source_type VARCHAR(32) NOT NULL COMMENT '来源事实类型',
    source_no VARCHAR(64) NOT NULL COMMENT '来源事实编号',
    process_stage VARCHAR(32) NOT NULL COMMENT '处理阶段',
    log_level VARCHAR(16) NOT NULL COMMENT '日志级别',
    log_level_type VARCHAR(16) NOT NULL COMMENT '日志级别样式',
    message TEXT NOT NULL COMMENT '处理事实摘要',
    source VARCHAR(64) NOT NULL COMMENT '来源渠道或系统',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_process_log_no (log_no),
    UNIQUE KEY uk_payment_process_log_source (source_type, source_no),
    KEY idx_payment_process_log_payment (payment_order_id, created_at),
    KEY idx_payment_process_log_order (order_no, created_at),
    KEY idx_payment_process_log_stage_level (process_stage, log_level, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付处理日志事实表';

INSERT IGNORE INTO t_payment_process_log(log_no, payment_order_id, order_no, request_no, source_type, source_no, process_stage, log_level, log_level_type, message, source, created_at)
SELECT pa.attempt_no, pa.payment_order_id, po.order_no, pa.attempt_no, 'PAYMENT_ATTEMPT', pa.attempt_no,
       '支付提交',
       CASE WHEN pa.attempt_status_type = 'danger' THEN 'ERROR' WHEN pa.attempt_status_type = 'warn' THEN 'WARN' ELSE 'INFO' END,
       pa.attempt_status_type,
       CONCAT('支付方式=', pa.payment_method, '；请求状态=', pa.attempt_status, '；请求报文=', pa.request_payload),
       pa.channel_code, pa.created_at
FROM t_payment_attempt pa
         LEFT JOIN t_payment_order po ON po.payment_order_id = pa.payment_order_id;

INSERT IGNORE INTO t_payment_process_log(log_no, payment_order_id, order_no, request_no, source_type, source_no, process_stage, log_level, log_level_type, message, source, created_at)
SELECT pnl.notify_no, pnl.payment_order_id, po.order_no, NULL, 'PAYMENT_NOTIFY', pnl.notify_no,
       '渠道回调',
       CASE WHEN pnl.notify_status_type = 'danger' THEN 'ERROR' WHEN pnl.notify_status_type = 'warn' THEN 'WARN' ELSE 'INFO' END,
       pnl.notify_status_type,
       CONCAT('回调类型=', pnl.notify_type, '；处理状态=', pnl.notify_status, '；结果=', COALESCE(pnl.notify_result, '-')),
       pnl.channel_code, pnl.created_at
FROM t_payment_notify_log pnl
         LEFT JOIN t_payment_order po ON po.payment_order_id = pnl.payment_order_id;

INSERT IGNORE INTO t_payment_process_log(log_no, payment_order_id, order_no, request_no, source_type, source_no, process_stage, log_level, log_level_type, message, source, created_at)
SELECT prr.route_no, prr.payment_order_id, po.order_no, NULL, 'PAYMENT_ROUTE', prr.route_no,
       '支付路由', 'INFO', 'info',
       CONCAT('路由规则=', prr.route_rule, '；路由结果=', prr.route_result),
       prr.channel_code, prr.created_at
FROM t_payment_route_record prr
         LEFT JOIN t_payment_order po ON po.payment_order_id = prr.payment_order_id;

INSERT IGNORE INTO t_payment_process_log(log_no, payment_order_id, order_no, request_no, source_type, source_no, process_stage, log_level, log_level_type, message, source, created_at)
SELECT pe.event_no, pe.payment_order_id, pe.biz_no, NULL, 'PAYMENT_EVENT', pe.event_no,
       '业务事件',
       CASE WHEN pe.event_type = 'PAYMENT_CLOSED' THEN 'WARN' ELSE 'INFO' END,
       CASE WHEN pe.event_type = 'PAYMENT_CLOSED' THEN 'warn' ELSE 'info' END,
       CONCAT('事件类型=', pe.event_type, '；事件载荷=', pe.event_payload),
       'payment-core', pe.created_at
FROM t_payment_event pe;
