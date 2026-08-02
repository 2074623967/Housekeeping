# wallet-account database

初始化顺序：

1. 执行 `schema.sql`
2. 演示环境可追加执行 `data.sql`

当前阶段仅提供：

- 钱包主体表
- 钱包账户表
- 钱包流水表

余额口径冻结为：

`total_balance = available_balance + frozen_balance + pending_in_balance - pending_out_balance`
