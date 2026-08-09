# REG-10 日终处理与运营巡检 API 证据

## 1. 自动化覆盖

`payment-core` 后端全量测试 `270/270` 通过，其中包含：

- `PaymentDayEndControllerTest`
- `PaymentTaskCenterControllerTest`
- `PaymentMetricsControllerTest`

`reconciliation-system` 后端全量测试 `6/6` 通过。

## 2. August 9, 2026 真实运行态接口

### 2.1 结案前差异查询

`GET /api/reconciliation/differences?status=OPEN&pageNo=1&pageSize=20`

结果要点：

- 批次：`REC-DEMO-1001`
- 差异：`DIFF-DEMO-1001`
- 类型：`AMOUNT_MISMATCH`
- 状态：`OPEN`

### 2.2 人工结案

`POST /api/reconciliation/differences/DIFF-DEMO-1001/resolve`

请求体：

```json
{
  "differenceNo": "DIFF-DEMO-1001",
  "resolution": "人工核实并完成调账",
  "remark": "2026-08-09 test 基线日终差异结案演练"
}
```

返回结果：`success`

### 2.3 结案后工作台摘要

`GET /api/reconciliation/overview`

返回要点：

```json
{
  "batchCount": 1,
  "runningBatchCount": 0,
  "openDifferenceCount": 0,
  "matchedCount": 2
}
```

### 2.4 结案后批次详情

`GET /api/reconciliation/batches/REC-DEMO-1001`

返回要点：

- `openDifferenceCount = 0`
- `resolvedDifferenceCount = 1`
- `DIFF-DEMO-1001.status = RESOLVED`
- `resolution = 人工核实并完成调账`

## 3. 当前说明

截至 August 9, 2026，REG-10 已具备自动化测试和真实运行态接口证据，能够证明：

1. 日终/对账批次已生成。
2. 差异可以进入人工收口。
3. 收口后工作台摘要和批次详情会同步回写。
