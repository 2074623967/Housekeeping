INSERT INTO t_wallet_account (account_no, owner_name, wallet_type, status, available_amount, frozen_amount, created_at) VALUES
('WALLET-10001', '张女士', 'PERSONAL', 'ACTIVE', 268.00, 20.00, '2026-07-26 09:00:00'),
('WALLET-10002', '王先生', 'PERSONAL', 'ACTIVE', 880.00, 0.00, '2026-07-26 09:01:00'),
('WALLET-20001', '晨星科技', 'ENTERPRISE', 'ACTIVE', 12680.00, 300.00, '2026-07-26 09:02:00');

INSERT INTO t_wallet_ledger (ledger_no, account_no, biz_type, biz_no, amount, direction, created_at) VALUES
('WLD-0001', 'WALLET-10001', 'RECHARGE', 'RCH-20260726001', 100.00, 'IN', '2026-07-26 09:05:00'),
('WLD-0002', 'WALLET-10001', 'BALANCE_PAY', 'PAY-20260726001', 32.00, 'OUT', '2026-07-26 09:08:00'),
('WLD-0003', 'WALLET-20001', 'TRANSFER', 'TRF-20260726001', 500.00, 'IN', '2026-07-26 09:10:00');

INSERT INTO t_wallet_recharge_order (recharge_no, account_no, biz_no, amount, status, created_at) VALUES
('RCH-20260726001', 'WALLET-10001', 'RCH-20260726001', 100.00, 'SUCCESS', '2026-07-26 09:05:00');

INSERT INTO t_wallet_withdraw_order (withdraw_no, account_no, biz_no, amount, status, created_at) VALUES
('WTH-20260726001', 'WALLET-10002', 'WTH-20260726001', 80.00, 'SUCCESS', '2026-07-26 09:12:00');

INSERT INTO t_wallet_transfer_order (transfer_no, source_account_no, target_account_no, biz_no, amount, status, created_at) VALUES
('TRF-20260726001', 'WALLET-20001', 'WALLET-10001', 'TRF-20260726001', 500.00, 'SUCCESS', '2026-07-26 09:10:00');
