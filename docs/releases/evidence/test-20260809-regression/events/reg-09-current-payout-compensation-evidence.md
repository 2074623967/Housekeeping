# REG-09 当前批次结算审核与出款补偿证据

- 日期：`2026-08-09`
- 分支：`test`
- 关联结算单：`SLT20004`
- 关联清分单：`CLO-DLQ-20260809-001`
- 关联出款批次：`PBT50001`
- 关联出款记录：`POU60001`

## 1. 当前批次来源

本批次来自 `clearing.generated -> settlement` 的当前基线 replay 成功场景：

- 事件：`SVE70003`
- `bizNo = CLO-DLQ-20260809-001`
- 生成结算单：`SLT20004`

## 2. 审核通过

接口：

`POST /api/settlements/orders/SLT20004/audit`

结果：

- `auditStatus = 已通过`
- `settlementStatus = 待出款`

## 3. 创建出款批次

接口：

`POST /api/settlements/payouts`

请求要点：

```json
{
  "batchNo": "SET10003",
  "payoutChannel": "AUTO_BANK",
  "createdBy": "财务经理A"
}
```

结果：

- 出款批次：`PBT50001`
- 初始状态：`待出款`
- 出款笔数：`1`
- 总金额：`¥80.00`

## 4. 首次出款失败

接口：

`POST /api/settlements/payouts/PBT50001/execute`

请求要点：

```json
{
  "operatorName": "财务经理A",
  "remark": "2026-08-09 当前test基线出款失败演练",
  "executionResult": "FAILED",
  "failureReason": "银行通道返回账户信息待复核"
}
```

结果：

- 批次状态：`待重试`
- `failedCount = 1`

记录查询：

`GET /api/settlements/payouts/PBT50001/records?pageNo=1&pageSize=20`

返回：

| 字段 | 值 |
| --- | --- |
| `payoutNo` | `POU60001` |
| `payoutStatus` | `已失败` |
| `retryCount` | `0` |

## 5. 人工重试成功

接口：

`POST /api/settlements/payouts/PBT50001/retry`

请求要点：

```json
{
  "operatorName": "财务经理A",
  "reason": "2026-08-09 已完成账户信息复核，发起人工补发"
}
```

结果：

- 批次状态：`已完成`
- `successCount = 1`
- `failedCount = 0`

再次查询记录：

| 字段 | 值 |
| --- | --- |
| `payoutNo` | `POU60001` |
| `payoutStatus` | `已发放` |
| `retryCount` | `1` |

## 6. 结算单终态

`GET /api/settlements/orders/SLT20004`

返回：

- `settlementStatus = 已出款`
- `payoutStatus = 已发放`
- `auditStatus = 已通过`

## 7. 结论

当前 `test` 基线已经具备一条真实的 current batch 结算审核与出款补偿闭环：

`清分事件 replay -> 生成结算单 -> 审核通过 -> 创建出款批次 -> 首次失败 -> 人工重试 -> 出款成功`

因此 `REG-09` 的当前批次补偿/回滚证据已经补齐。
