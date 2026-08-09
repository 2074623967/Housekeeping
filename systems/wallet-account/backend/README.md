# wallet-account backend

启动后提供：

- `GET /api/wallet/accounts`
- `GET /api/wallet/accounts/{walletAccountNo}`
- `GET /api/wallet/accounts/{walletAccountNo}/balance`
- `GET /api/wallet/accounts/balances`
- `GET /api/wallet/flows`
- `POST /api/wallet/accounts`
- `POST /api/wallet/accounts/{walletAccountNo}/status-change`

当前默认使用 H2 内存库演示阶段 1 钱包账户能力。
