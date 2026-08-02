# wallet-account

`wallet-account` 是家政服务支付平台的钱包账户底座子系统，负责钱包主体、账户、余额和流水的统一承载。

当前仓库内已落最小可运行骨架，覆盖：

- `database`：H2/MySQL 兼容的阶段 1 DDL 与种子数据
- `backend`：开户、账户查询、余额查询、流水查询、状态流转最小接口
- `frontend/admin-web`：钱包账户列表、详情、流水与状态操作后台页骨架
- `docs`：实施说明与冻结文档映射

后续迭代优先顺序：

1. 接真实 MySQL 与联调环境
2. 补前端真实接口联通
3. 增加批量余额查询、导出任务、冻结/解冻业务动作
4. 再衔接 `wallet-core` 与 `accounting-core`
