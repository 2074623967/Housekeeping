INSERT INTO t_ops_agreement_template (template_code, template_name, subject_type, sign_fields, esign_provider, status, status_type, updated_at) VALUES
('AGREE_WORKER_V1', '服务者收款协议', 'WORKER', '姓名/身份证/银行卡', '上上签', 'ENABLED', 'success', '2026-08-04 09:10:00'),
('AGREE_MERCHANT_V1', '商家支付协议', 'MERCHANT', '企业名称/统一社会信用代码/结算账户', '法大大', 'ENABLED', 'success', '2026-08-04 09:12:00'),
('AGREE_DEPOSIT_V1', '保证金补充协议', 'WORKER', '主体编号/保证金金额/违约条款', '上上签', 'DISABLED', 'danger', '2026-08-04 09:15:00');

INSERT INTO t_ops_business_line (business_code, business_name, default_scene, owner, settlement_policy, status, status_type, updated_at) VALUES
('HOME_CLEAN', '家政保洁', '订单收款', '支付产品经理A', 'T+1 自动结算', 'ENABLED', 'success', '2026-08-04 09:16:00'),
('HOME_NANNY', '月嫂保姆', '预付签约', '支付产品经理B', '阶段性确认后结算', 'ENABLED', 'success', '2026-08-04 09:18:00'),
('HOME_DEPOSIT', '服务保障金', '保证金收取', '资金产品经理A', '冻结后专账管理', 'DISABLED', 'danger', '2026-08-04 09:19:00');

INSERT INTO t_ops_payment_type (type_code, type_name, transaction_category, fee_policy, refund_capability, status, status_type, updated_at) VALUES
('PAY_CONSUME', '消费支付', '收款', '按渠道费率收取', '支持原路退款', 'ENABLED', 'success', '2026-08-04 09:20:00'),
('PAY_DEPOSIT', '保证金收取', '资金沉淀', '平台免手续费', '支持人工审核退还', 'ENABLED', 'success', '2026-08-04 09:22:00'),
('PAY_TRANSFER', '转账代付', '付款', '按单笔代付收费', '仅支持撤回前取消', 'DISABLED', 'danger', '2026-08-04 09:23:00');

INSERT INTO t_ops_cashier_template (template_code, template_name, terminal_type, default_pay_method, display_policy, status, status_type, updated_at) VALUES
('CASHIER_APP_ORDER', 'App 订单收银台', 'APP', '微信支付', '微信优先+余额次选', 'ENABLED', 'success', '2026-08-04 09:24:00'),
('CASHIER_H5_CONTRACT', 'H5 签约收银台', 'H5', '支付宝', '签约优先展示协议说明', 'ENABLED', 'success', '2026-08-04 09:26:00'),
('CASHIER_PC_FINANCE', 'PC 财务补款台', 'PC', '银行卡', '展示企业对公选项', 'DISABLED', 'danger', '2026-08-04 09:27:00');

INSERT INTO t_ops_channel_profile (channel_code, channel_name, channel_type, merchant_profile, refund_sla, risk_tag, status, status_type, updated_at) VALUES
('CHANNEL_WX_H5', '微信 H5 渠道档案', '微信', '商户号模板-WX-H5', 'T+0 原路退款', '实名校验', 'ENABLED', 'success', '2026-08-04 09:28:00'),
('CHANNEL_ALI_APP', '支付宝 App 渠道档案', '支付宝', '商户号模板-ALI-APP', 'T+0 原路退款', '签约校验', 'ENABLED', 'success', '2026-08-04 09:30:00'),
('CHANNEL_BANK_CORP', '企业网银渠道档案', '银行卡', '商户号模板-BANK', 'T+1 线下复核', '大额审批', 'DISABLED', 'danger', '2026-08-04 09:31:00');

INSERT INTO t_ops_routing_rule (route_code, business_code, pay_type, primary_channel, backup_channel, match_policy, status, status_type, updated_at) VALUES
('ROUTE_HOME_CLEAN_APP', 'HOME_CLEAN', 'PAY_CONSUME', 'CHANNEL_WX_H5', 'CHANNEL_ALI_APP', 'App 终端优先微信', 'ENABLED', 'success', '2026-08-04 09:32:00'),
('ROUTE_HOME_NANNY_H5', 'HOME_NANNY', 'PAY_CONSUME', 'CHANNEL_ALI_APP', 'CHANNEL_WX_H5', '签约场景优先支付宝', 'ENABLED', 'success', '2026-08-04 09:34:00'),
('ROUTE_HOME_DEPOSIT', 'HOME_DEPOSIT', 'PAY_DEPOSIT', 'CHANNEL_BANK_CORP', 'CHANNEL_ALI_APP', '保证金大额走银行卡', 'DISABLED', 'danger', '2026-08-04 09:35:00');

INSERT INTO t_ops_system_control (control_code, control_name, control_scope, control_value, risk_level, status, status_type, updated_at) VALUES
('CONTROL_TIMEOUT_CLOSE', '超时关闭分钟数', '支付订单', '30', '中', 'ENABLED', 'success', '2026-08-04 09:36:00'),
('CONTROL_CALLBACK_STRICT', '回调严格模式', '回调验签', 'PASS/WARN/FAIL', '高', 'ENABLED', 'success', '2026-08-04 09:38:00'),
('CONTROL_BIG_AMOUNT_REVIEW', '大额支付人工复核', '支付控制', '5000', '高', 'DISABLED', 'danger', '2026-08-04 09:40:00');
