# REG-07 跨系统链路诊断 API 证据

## 当前自动化证据

2026-08-09 已在 `test` 基线执行：

```text
reconciliation-system 6/6
```

对应能力：

1. `POST /api/reconciliation/cross-system/facts`
2. `GET /api/reconciliation/cross-system/chains/{businessChainId}`

## 测试覆盖点

1. 同一链路写入 4 条事实后，返回 `factCount = 4`
2. 重复写入相同事实时，数量不增加
3. 存在失败事实时，返回“链路存在失败或异常事实，禁止直接进入财务收口”

## 样例链路

- `CHAIN-IT-001`
- `CHAIN-IT-002`
- `CHAIN-PAY202607200001`
