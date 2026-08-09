# REG-10 日终处理与运营巡检事件证据

- 采集日期：`2026-08-09`
- 分支：`test`
- 系统：`reconciliation-system`

## 1. 运行态接口事实

`GET /api/reconciliation/overview` 返回摘要：

```json
{
  "batchCount": 1,
  "runningBatchCount": 0,
  "openDifferenceCount": 1,
  "matchedCount": 2
}
```

`GET /api/reconciliation/batches/REC-DEMO-1001` 返回批次详情，包含渠道记录、平台支付事实和 `DIFF-DEMO-1001` 未结金额差异。

## 2. 事件/运营处置口径

本轮没有伪造“差异已解决”事件。当前运营状态明确为：

1. 批次事实生成完成。
2. 金额差异仍为 `OPEN`。
3. 运营动作应进入差异处理页，完成核实、调账或补单后再结案。
4. 在差异未结案前，日终不允许标记为“可直接进入正式对账”。

这条证据用于证明系统能够把日终结果和未结异常暴露给运营，而不是只记录一个成功批次状态。
