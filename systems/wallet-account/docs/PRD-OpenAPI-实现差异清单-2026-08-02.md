# wallet-account PRD / OpenAPI / 实现差异清单

整理日期：2026-08-02

文档目标：把当前 `wallet-account` 子系统进入 `test` 前最关键的一致性差异拆成可执行项，避免后续反复重新阅读 PRD、OpenAPI 和代码。

对照范围：

1. `/Users/abc123/workspace/支付相关/钱包账户系统PRD版.md`
2. `/Users/abc123/workspace/支付相关/钱包账户系统后端OpenAPI正式稿.yaml`
3. `/Users/abc123/workspace/home-service-payment-system/systems/wallet-account/backend`
4. `/Users/abc123/workspace/home-service-payment-system/systems/wallet-account/frontend/admin-web`

## 1. 总体结论

当前差异主要不是“核心接口不存在”，而是“冻结文档写得比当前实现更宽”。

已实现主干：

1. 开户
2. 账户分页
3. 账户详情
4. 单账户余额
5. 批量余额
6. 流水分页
7. 流水导出受理
8. 状态变更

当前真正阻断 `feature -> test` 的一致性问题集中在三类：

1. 查询条件范围不一致
2. 返回契约表达不一致
3. 前端页面范围比冻结稿更轻

## 2. 差异清单

| 分类 | 冻结文档口径 | 当前实现 | 结论 | 建议动作 |
| --- | --- | --- | --- | --- |
| 账户列表筛选 | PRD 要求支持账户编号、主体类型、外部主体编号、账户类型、账户状态 | 当前仅支持 `keyword / ownerType / accountStatus` | 不一致 | 二选一：补实现，或把阶段 1 冻结范围收窄到当前已交付 |
| 账户列表 OpenAPI | OpenAPI 声明 `walletAccountNo / extRefNo / accountType / bizLineCode / tenantCode / openedStartTime / openedEndTime` | Controller/DTO 未支持这些字段 | 不一致 | 优先更新 OpenAPI 或补实现，不可继续双口径并存 |
| 流水列表筛选 | OpenAPI 声明 `flowNo / walletOwnerId / flowType / bizType / occurredStartTime / occurredEndTime` | 当前仅支持 `walletAccountNo / sourceSystem / sourceBizNo` | 不一致 | 若本阶段不做，需明确写入非目标或后续迭代 |
| 账户详情形态 | PRD 写“详情抽屉” | 当前为页面右侧卡片区 | 部分不一致 | 若不改 UI，则文档口径要改为“详情卡片区 / 侧栏区” |
| 状态变更交互 | PRD 写“状态变更弹窗” | 当前为列表按钮 + 浏览器确认框 | 不一致 | 建议补标准弹窗，至少补操作原因与确认文案 |
| 导出能力 | PRD 写“支持导出” | 当前只做到“导出任务受理” | 部分不一致 | 文档需明确阶段 1 为“异步导出任务受理，不含下载中心” |
| 返回契约 | OpenAPI `CommonSuccessResponse` 与当前 `ApiResponse<T>` 不一致 | 当前统一返回 `code/message/data/requestId` | 不一致 | 统一以当前真实返回结构更新 OpenAPI |
| 本地服务地址 | OpenAPI 本地地址仍写 `127.0.0.1:8080` | 当前服务默认端口 `8095` | 不一致 | 更新 OpenAPI 本地 server 定义 |

## 3. 推荐处理策略

### 3.1 第一优先级

先统一文档口径，不要再让文档和代码互相冲突：

1. 更新 OpenAPI，使之与当前 Controller 请求参数、返回结构一致
2. 更新 PRD，把当前未做的筛选项和页面细节明确标为“后续迭代”
3. 更新实施说明和测试基线，明确导出能力当前仅为“受理任务”

### 3.2 第二优先级

补一轮最小实现增强，让它更接近冻结稿：

1. 账户列表补 `accountType`
2. 开户与查询口径补 `extRefNo`
3. 状态变更改为标准弹窗
4. 账户详情从卡片区升级为更明确的详情抽屉/侧栏

### 3.3 第三优先级

若继续冲刺 `feature -> test`：

1. 先完成文档与实现同口径
2. 再做一次前后端联调回归
3. 形成“已对齐后”的最终提测结论

## 4. 建议的下一轮执行顺序

1. 更新 OpenAPI
2. 更新 PRD 范围描述
3. 补前端标准状态变更弹窗
4. 评估是否补 `accountType / extRefNo` 查询
5. 回填最终提测结论

## 5. 当前判断

截至 2026-08-02，`wallet-account` 不是“功能完全没做”，而是已经到了“需要做范围收口和冻结一致性治理”的阶段。

这意味着后续最值当的工作，不是盲目再铺更多能力，而是先把当前这一轮交付包收成可晋级的版本。
