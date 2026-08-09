# REG-10 日终处理与运营巡检事件证据

- 采集日期：`2026-08-09`
- 分支：`test`
- 系统：`reconciliation-system`

## 1. 运行态处置轨迹

### 1.1 结案前

`GET /api/reconciliation/differences?status=OPEN&pageNo=1&pageSize=20`

- 返回 `DIFF-DEMO-1001`
- 状态为 `OPEN`

### 1.2 结案动作

`POST /api/reconciliation/differences/DIFF-DEMO-1001/resolve`

- 处置结论：`人工核实并完成调账`
- 处置备注：`2026-08-09 test 基线日终差异结案演练`

### 1.3 结案后

`GET /api/reconciliation/overview`

```json
{
  "batchCount": 1,
  "runningBatchCount": 0,
  "openDifferenceCount": 0,
  "matchedCount": 2
}
```

`GET /api/reconciliation/batches/REC-DEMO-1001`

- `openDifferenceCount = 0`
- `resolvedDifferenceCount = 1`
- `DIFF-DEMO-1001.status = RESOLVED`
- `resolvedAt = 2026-08-09T11:29:16.766`

## 2. 运营收口口径

这条证据证明当前 test 基线已经具备：

1. 日终差异可识别。
2. 运营可通过标准接口完成结案。
3. 结案后工作台与批次事实同步变化。
4. 日终巡检不是只看“批次完成”，而是看“差异是否已经收口”。
