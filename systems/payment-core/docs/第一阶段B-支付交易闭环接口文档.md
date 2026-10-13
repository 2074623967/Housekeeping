# 第一阶段B-支付交易闭环接口文档

## 1. 文档定位

虽然文件名保留“第一阶段B”，但当前文档内容已经作为 `payment-core` 一期支付交易闭环 V1 的冻结版接口文档使用。

适用对象：

- 产品经理
- 前端开发
- 后端开发
- 测试工程师

## 2. 接口清单

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/payments/prepay` | `POST` | 创建账单、支付单、预付单 |
| `/api/payments/cashier/{prepayOrderNo}` | `GET` | 获取收银台数据 |
| `/api/payments/submit` | `POST` | 提交支付 |
| `/api/payments/callback/{channel}` | `POST` | 接收真实渠道签名回调，产品页面不调用 |
| `/api/payments/query` | `POST` | 主动查单 |
| `/api/payments/close` | `POST` | 关闭支付单 |
| `/api/payments` | `GET` | 查询支付单列表 |
| `/api/payments/export` | `GET` | 导出支付单列表 CSV |
| `/api/payments/{paymentOrderId}` | `GET` | 查询支付详情 |
| `/api/bills` | `GET` | 查询交易账单中心 |
| `/api/bills/export` | `GET` | 导出当前筛选条件下的交易账单 CSV |
| `/api/cashier-sessions` | `GET` | 查询收银台会话管理列表 |
| `/api/cashier-sessions/overview` | `GET` | 查询收银台会话总览指标 |
| `/api/cashier-sessions/export` | `GET` | 导出当前筛选条件下的收银台会话 CSV |
| `/api/payment-flows` | `GET` | 查询统一支付流水排障台 |
| `/api/payment-flows/export` | `GET` | 导出统一支付流水排障台 |
| `/api/payment-routes` | `GET` | 查询支付路由执行结果台 |
| `/api/payment-routes/overview` | `GET` | 查询支付路由执行结果总览指标 |
| `/api/payment-routes/export` | `GET` | 导出当前筛选条件下的支付路由执行结果 CSV |
| `/api/payment-routes/{routeNo}` | `GET` | 查询单条支付路由执行详情 |
| `/api/payment-requests` | `GET` | 查询支付请求管理台 |
| `/api/payment-requests/overview` | `GET` | 查询支付请求总览指标 |
| `/api/payment-logs` | `GET` | 查询支付处理日志台 |
| `/api/payment-logs/overview` | `GET` | 查询支付处理日志总览指标 |
| `/api/payment-logs/export` | `GET` | 导出支付处理日志 CSV |
| `/api/payment-records` | `GET` | 按支付维度分页查询收款记录 |
| `/api/payment-records/export` | `GET` | 导出当前筛选条件下的收款记录 CSV |
| `/api/payment-records/{paymentOrderId}` | `GET` | 查询单笔收款记录详情 |
| `/api/payment-metrics/summary` | `GET` | 查询支付成功率、成功金额和状态分布 |
| `/api/payment-events` | `GET` | 查询支付事件出站台账，支持 `FAILED_OR_DEAD_LETTER` 联合口径 |
| `/api/payment-events/overview` | `GET` | 查询支付事件出站总览指标 |
| `/api/payment-events/republish` | `POST` | 手动重发支付事件 |
| `/api/payment-issues` | `GET` | 查询支付交易异常中心列表 |
| `/api/payment-issues/{issueNo}` | `GET` | 查询单条支付交易异常详情 |
| `/api/payment-issues/responsibility-summary` | `GET` | 查询支付交易异常责任组统计 |
| `/api/payment-issues/alerts` | `GET` | 查询支付异常告警通知明细 |
| `/api/payment-issues/alerts/export` | `GET` | 导出支付异常告警通知明细 CSV |
| `/api/refunds` | `GET` | 分页查询退款单 |
| `/api/refunds/export` | `GET` | 导出退款单 CSV |
| `/api/refunds/{refundOrderId}` | `GET` | 查询退款详情与操作日志 |
| `/api/refunds/apply` | `POST` | 发起退款申请 |
| `/api/refunds/approve` | `POST` | 审核通过退款单并提交处理 |
| `/api/refunds/callback/{channel}` | `POST` | 验签并收口渠道退款结果 |
| `/api/refunds/retry` | `POST` | 失败退款单重新提交处理 |
| `/api/settlements/workers` | `GET` | 查询服务者结算单 |
| `/api/settlements/workers/overview` | `GET` | 查询服务者结算总览指标 |
| `/api/settlements/workers/export` | `GET` | 导出服务者结算单 CSV |
| `/api/payment-config` | `GET` | 查询支付渠道、路由规则、支付协议与返回码映射配置 |
| `/api/payment-config/export` | `GET` | 导出支付配置治理快照 CSV |
| `/api/payment-config/channels/toggle` | `POST` | 提交支付渠道启停候选，运行态不变 |
| `/api/payment-config/channels/reviews` | `GET` | 查询支付渠道状态变更复核申请 |
| `/api/payment-config/channels/reviews` | `POST` | 提交支付渠道新增或编辑完整候选，运行态不变 |
| `/api/payment-config/channels/reviews/{requestNo}/approve` | `POST` | 异人复核通过并应用渠道状态候选 |
| `/api/payment-config/channels/reviews/{requestNo}/reject` | `POST` | 异人驳回渠道状态候选，运行态不变 |
| `/api/payment-config/channels/{channelCode}/versions` | `GET` | 查询支付渠道不可变版本历史，不返回密钥或公钥原文 |
| `/api/payment-config/channels/{channelCode}/rollback` | `POST` | 提交指定历史版本的回滚候选，运行态不变 |
| `/api/payment-config/route-rules/validate` | `POST` | 校验路由表达式并模拟样例命中 |
| `/api/payment-config/route-rules/conflicts` | `GET` | 检测当前启用路由规则的冲突、重复、遮蔽和待复核项 |
| `/api/payment-config/route-rules/reviews` | `GET` | 查询路由规则复核申请 |
| `/api/payment-config/route-rules/reviews` | `POST` | 提交新增或修改候选快照，运行态不变 |
| `/api/payment-config/route-rules/reviews/{requestNo}/approve` | `POST` | 异人复核通过并发布候选快照 |
| `/api/payment-config/route-rules/reviews/{requestNo}/reject` | `POST` | 异人驳回候选快照，运行态不变 |
| `/api/payment-config/route-rules/{ruleCode}/versions` | `GET` | 查询路由规则历史版本 |
| `/api/payment-config/route-rules/{ruleCode}/rollback` | `POST` | 提交指定历史版本的回滚候选，运行态不变 |
| `/api/payment-config/route-rules/toggle` | `POST` | 提交路由规则启停候选，运行态不变 |
| `/api/payment-config/protocols/toggle` | `POST` | 提交支付协议启停候选，运行态不变 |
| `/api/payment-config/protocols/reviews` | `GET` | 查询支付协议复核申请 |
| `/api/payment-config/protocols/reviews` | `POST` | 提交新增或编辑候选快照，运行态不变 |
| `/api/payment-config/protocols/reviews/{requestNo}/approve` | `POST` | 异人复核通过并发布候选快照 |
| `/api/payment-config/protocols/reviews/{requestNo}/reject` | `POST` | 异人驳回候选快照，运行态不变 |
| `/api/payment-config/protocols/{protocolCode}/versions` | `GET` | 查询支付协议历史版本 |
| `/api/payment-config/protocols/{protocolCode}/rollback` | `POST` | 提交指定历史版本的回滚候选，运行态不变 |
| `/api/payment-config/return-codes/toggle` | `POST` | 提交渠道返回码单条启停候选，运行态不变 |
| `/api/payment-config/return-codes/reviews` | `GET` | 查询渠道返回码单条状态变更复核申请 |
| `/api/payment-config/return-codes/reviews/{requestNo}/approve` | `POST` | 异人复核通过并应用单条状态候选 |
| `/api/payment-config/return-codes/reviews/{requestNo}/reject` | `POST` | 异人驳回单条状态候选，运行态不变 |
| `/api/payment-config/return-codes/import/preview` | `POST` | 解析并预检返回码 CSV，返回逐行差异，不修改运行态 |
| `/api/payment-config/return-codes/import/reviews` | `GET` | 查询返回码 CSV 导入复核申请 |
| `/api/payment-config/return-codes/import/reviews` | `POST` | 锁定并复验预检基线，提交导入候选，运行态不变 |
| `/api/payment-config/return-codes/import/reviews/{requestNo}/approve` | `POST` | 异人复核通过，复验候选后原子应用并记录审计批次 |
| `/api/payment-config/return-codes/import/reviews/{requestNo}/reject` | `POST` | 异人驳回导入候选，运行态不变 |
| `/api/payment-config/gateways/toggle` | `POST` | 提交支付网关启停候选，运行态不变 |
| `/api/payment-config/gateways/reviews` | `GET` | 查询支付网关配置变更复核申请 |
| `/api/payment-config/gateways/reviews` | `POST` | 提交支付网关新增或编辑完整候选，运行态不变 |
| `/api/payment-config/gateways/reviews/{requestNo}/approve` | `POST` | 异人复核通过并应用支付网关完整候选 |
| `/api/payment-config/gateways/reviews/{requestNo}/reject` | `POST` | 异人驳回支付网关完整候选，运行态不变 |
| `/api/payment-config/gateways/{gatewayCode}/versions` | `GET` | 查询支付网关不可变版本历史 |
| `/api/payment-config/gateways/{gatewayCode}/rollback` | `POST` | 提交指定历史版本的完整回滚候选，运行态不变 |
| `/api/payment-config/control-policies/toggle` | `POST` | 提交支付控制策略启停候选，运行态不变 |
| `/api/payment-config/control-policies/reviews` | `GET` | 查询支付控制策略配置变更复核申请 |
| `/api/payment-config/control-policies/reviews` | `POST` | 提交支付控制策略新增或编辑完整候选 |
| `/api/payment-config/control-policies/reviews/{requestNo}/approve` | `POST` | 异人复核通过并应用完整候选 |
| `/api/payment-config/control-policies/reviews/{requestNo}/reject` | `POST` | 异人驳回候选，运行态不变 |
| `/api/payment-config/control-policies/{sourceAppId}/versions` | `GET` | 查询支付控制策略不可变版本历史，不返回令牌原文 |
| `/api/payment-config/control-policies/{sourceAppId}/rollback` | `POST` | 提交指定历史版本的回滚候选，运行态不变 |
| `/api/payment-config/issue-duty-rosters` | `POST` | 提交异常告警值班路由新增候选，运行态不变 |
| `/api/payment-config/issue-duty-rosters/{rosterCode}` | `PUT` | 提交异常告警值班路由编辑候选，运行态不变 |
| `/api/payment-config/issue-duty-rosters/toggle` | `POST` | 提交异常告警值班路由启停候选，运行态不变 |
| `/api/payment-config/issue-duty-rosters/reviews` | `GET` | 查询异常告警值班路由复核申请 |
| `/api/payment-config/issue-duty-rosters/reviews/{requestNo}/approve` | `POST` | 异人复核通过并应用完整候选 |
| `/api/payment-config/issue-duty-rosters/reviews/{requestNo}/reject` | `POST` | 异人驳回完整候选，运行态不变 |
| `/api/payment-monitor/overview` | `GET` | 查询支付趋势、渠道表现和异常告警 |
| `/api/payment-day-end/overview` | `GET` | 查询支付日终处理总览与差异告警 |
| `/api/payment-day-end/run` | `POST` | 手动触发支付日终处理 |
| `/api/payment-day-end/export` | `GET` | 导出支付日终批次快照 CSV |
| `/api/payment-task-center/overview` | `GET` | 查询支付任务中心总览 |
| `/api/payment-task-center/task-runs` | `GET` | 查询支付任务执行日志 |
| `/api/payment-task-center/task-runs/export` | `GET` | 导出支付任务执行日志 CSV |
| `/api/payment-task-center/close-expired-payments` | `POST` | 手动执行超时关单 |
| `/api/payment-task-center/query-pending-payments` | `POST` | 手动执行待回调支付主动查单补偿 |
| `/api/payment-task-center/republish-failed-events` | `POST` | 手动执行失败事件重发 |
| `/api/payment-task-center/retry-failed-refunds` | `POST` | 手动执行失败退款重试 |
| `/api/payment-task-center/escalate-overdue-issues` | `POST` | 手动执行异常 SLA 升级巡检 |

## 3. 支付流水排障台查询

接口：`GET /api/payment-flows`

查询参数：

| 参数 | 说明 |
| --- | --- |
| `paymentOrderId` | 支付单号，模糊匹配 |
| `orderNo` | 订单号，模糊匹配 |
| `flowType` | 流水类型，支持 `支付尝试 / 渠道回调 / 路由记录 / 业务事件 / 全部` |
| `channelCode` | 渠道编码，模糊匹配 |
| `terminal` | 终端，支持 `H5 / PC / APP / 小程序 / 全部` |
| `businessStatus` | 业务状态，支持按尝试状态、回调状态、路由结果和事件类型统一筛选 |
| `keyword` | 关键字，按摘要、请求报文、响应报文模糊匹配 |
| `sortField` | 排序字段，支持 `createdAt / retryCount / flowType` |
| `sortOrder` | 排序方向，支持 `asc / desc` |
| `pageNo` | 页码，从 `1` 开始 |
| `pageSize` | 每页条数，最大 `100` |

返回示例：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "items": [
      {
        "flowNo": "ATT202607190002",
        "paymentOrderId": "PAY202607190002",
        "successFactIssue": null,
        "orderNo": "ORD202607190002",
        "prepayOrderNo": "PRE202607190002",
        "flowType": "支付尝试",
        "flowTypeTag": "info",
        "channelCode": "alipay_h5",
        "terminal": "H5",
        "clientIp": "127.0.*.*",
        "idempotencyKey": "PRE202****y_h5",
        "businessStatus": "等待回调",
        "businessStatusType": "warn",
        "notifyType": null,
        "routeRule": null,
        "downstreamSystem": null,
        "eventTopic": null,
        "publishStatus": null,
        "retryCount": null,
        "requestPayload": "{\"scene\":\"H5\",\"amount\":6800.00}",
        "responsePayload": "{\"payUrl\":\"https://pay.example.com/ali/2\"}",
        "summary": "方式=支付宝 / 状态=等待回调",
        "createdAt": "2026-07-19 10:03:11"
      }
    ],
    "total": 1,
    "pageNo": 1,
    "pageSize": 20
  },
  "requestId": "REQ202607200021"
}
```

业务说明：

1. 当前统一聚合支付尝试、渠道回调、路由记录、业务事件四类过程流水。
2. 不同流水类型会按统一字段口径返回排障信息；客户端 IP、幂等键和请求/响应 JSON 中的手机号、设备标识、卡号、客户名称等字段在出参边界统一脱敏。
3. 前端可直接使用 `requestPayload`、`responsePayload` 展开脱敏报文；非法 JSON 整体返回 `[REDACTED]`，流水摘要不再重复拼接原始载荷。
4. 列表和按 `flowSource + flowNo` 的唯一详情从所属支付单投影 `successFactIssue`，正常或无支付单匹配为 `null`，成功时间缺失为 `SUCCESS_TIME_MISSING`，成功事实不一致为 `SUCCESS_FACT_MISMATCH`；复用支付单原精度判断，不能仅凭流水事件或摘要认定资金成功。异常流水本身可读，但管理端不提供被成功事实门禁拒绝的支付单详情入口，仍可查支付请求和处理日志。

导出接口：`GET /api/payment-flows/export`

1. 导出接口复用列表全部筛选参数和排序规则，不接收分页参数。
2. 导出文件名固定为 `payment-flows.csv`，以 UTF-8 BOM 返回，兼容 Excel 直接打开。
3. 导出查询严格复用当前统一流水排障台的 union SQL 口径，不再另起分页循环导出路径。
4. 导出列包含流水编号、支付单号、订单号、预付单号、流水类型、类型标签、渠道编码、终端、客户端 IP、幂等键、业务状态、状态类型、通知类型、路由规则、下游系统、事件主题、发布状态、重试次数、请求报文、响应报文、摘要、创建时间。
5. 请求报文、响应报文、摘要等文本列先执行与列表一致的脱敏，再按 CSV 双引号规则转义。
6. 原有列末尾追加“支付单成功事实异常类型”，与列表、唯一详情一致；正常或无匹配支付单时为空列。

## 4. 支付路由执行结果台查询

接口：`GET /api/payment-routes`

总览接口：`GET /api/payment-routes/overview`

查询参数：

| 参数 | 说明 |
| --- | --- |
| `paymentOrderId` | 支付单号，模糊匹配 |
| `orderNo` | 订单号，模糊匹配 |
| `routeRule` | 路由规则关键字，模糊匹配 |
| `channelCode` | 渠道编码，模糊匹配 |
| `paymentMethod` | 支付方式，支持 `全部 / 微信 / 支付宝 / 银行转账` |
| `terminal` | 终端，支持 `全部 / H5 / PC / APP / 小程序` |
| `routeResult` | 路由结果，支持 `全部` |
| `sortField` | 排序字段，支持 `createdAt / channelCode / routeResult` |
| `sortOrder` | 排序方向，支持 `asc / desc` |
| `pageNo` | 页码，从 `1` 开始 |
| `pageSize` | 每页条数，最大 `100` |

返回示例：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "items": [
      {
        "routeNo": "RTR202607190002",
        "paymentOrderId": "PAY202607190002",
        "successFactIssue": null,
        "orderNo": "ORD202607190002",
        "prepayOrderNo": "PRE202607190002",
        "customerName": "王*",
        "amount": "¥2000.00",
        "paymentMethod": "支付宝",
        "channelCode": "alipay_h5",
        "routeRule": "amount>1000 => alipay",
        "routeResult": "支付宝H5",
        "routeResultType": "success",
        "terminal": "H5",
        "clientIp": "127.0.*.*",
        "idempotencyKey": "PRE202****y_h5",
        "requestPayload": "{\"scene\":\"H5\",\"amount\":6800.00}",
        "responsePayload": "{\"payUrl\":\"https://pay.example.com/ali/2\"}",
        "createdAt": "2026-07-19 10:03:02"
      }
    ],
    "total": 1,
    "pageNo": 1,
    "pageSize": 20
  },
  "requestId": "REQ202607200031"
}
```

业务说明：

1. 当前按支付路由记录聚合支付单、订单、预付单和最近一次支付请求上下文。
2. 前端可直接用本接口查看路由规则、命中渠道、路由结果以及请求/响应报文，无需再跳多页拼装。
3. `GET /api/payment-routes/overview` 复用同一组筛选条件返回路由记录总数、成功命中数、需关注命中数、命中渠道数、线下路由数、微信路由数、支付宝路由数和最近路由时间，便于运营先看风险面再下钻。
4. 总览同时返回 `routeHealthEnabled`、`routeHealthLookbackHours`、`routeHealthFailureCountThreshold`、`routeHealthFailureRateThresholdPercent`、`routeHealthMinimumSampleCount`，管理端据此展示当前实际生效的路由健康熔断口径。
5. 开启路由健康熔断后，服务按渠道聚合窗口内 `已查单 / 查单失败` 事实；失败笔数达到阈值，或样本数达到最低要求且失败率达到阈值时，将该渠道判定为短窗不健康。
6. 规则目标渠道不健康时，命中可用 fallback 并以 `_HEALTH_FALLBACK` 规则后缀和“短窗健康熔断”结果说明留痕；显式指定不健康渠道时返回 `PAYMENT-1010`，不会静默改用其他支付方式；支付方式默认路由会跳过不健康候选。
7. 配置 `HSP_PAYMENT_CHANNEL_ROUTE_HEALTH_ENABLED` 默认值为 `false`；统计窗口及失败阈值复用 `HSP_PAYMENT_CHANNEL_QUERY_MONITOR_*` 配置。启用前需确认 fallback 覆盖并按真实渠道容量校准阈值。
8. 当前 V1.3 健康信号来自可审计的主动查单事实，不等同于网关主动探活；支付方式默认路由已补稳定加权，规则表达式已补括号和复合布尔求值，后续仍需补规则编辑校验和真实渠道探活联动。
9. 路由列表中的客户名称、客户端 IP、幂等键及请求/响应 JSON 与支付请求、支付流水使用同一套出参脱敏规则；原始事实仍保留在数据库，不代表字段级授权已经完成。
10. 列表、唯一路由详情与 CSV 从所属支付单投影 `successFactIssue`：正常或无支付单匹配为 `null`，成功时间缺失为 `SUCCESS_TIME_MISSING`，成功事实不一致为 `SUCCESS_FACT_MISMATCH`。判断复用支付单数据库原精度条件，不凭路由结果认定支付成功；异常路由仍可排障，但管理端禁用已被成功事实门禁拒绝的支付详情入口。

导出接口：`GET /api/payment-routes/export`

1. 导出接口复用列表的 `paymentOrderId / orderNo / routeRule / channelCode / paymentMethod / terminal / routeResult / sortField / sortOrder`，不接收分页参数。
2. 导出文件名固定为 `payment-routes.csv`，使用 UTF-8 BOM；查询直接返回全部匹配记录，不通过分页循环拼接。
3. 导出列覆盖路由记录号、支付单号、订单号、预付单号、客户名称、支付金额、支付方式、命中渠道、路由规则、路由结果、配置快照版本、终端、客户端 IP、幂等键、请求报文、响应报文和创建时间；末列追加“支付单成功事实异常类型”，正常行留空、异常行保留原始码。
4. 客户名称、客户端 IP、幂等键及 JSON 报文使用与列表相同的脱敏规则，文本按 CSV 双引号规则转义；生产仍需补导出权限、审批、水印和下载审计。

详情接口：`GET /api/payment-routes/{routeNo}`

1. `routeNo` 按单一路径段编码，服务端去除首尾空白后校验必填、最长 `64` 个字符且不得包含控制字符；非法请求沿用 `PAYMENT-1036`，不存在返回 `PAYMENT-1062 / 支付路由记录不存在`。
2. 响应包含 `routeNo / requestNo / paymentOrderId / successFactIssue / orderNo / prepayOrderNo / customerName / amount / paymentMethod / channelCode / routeRule / routeResult / routeResultType / opsConfigSnapshotVersion / terminal / clientIp / idempotencyKey / requestPayload / responsePayload / createdAt`；异常码与列表、CSV 同源。
3. 详情以 `t_payment_route_record.route_no` 唯一定位，并只按 `route.attempt_no = attempt.attempt_no` 补齐支付请求上下文；历史 `attempt_no IS NULL` 路由不按同支付单最近请求猜测归属。
4. 客户名称、客户端 IP、幂等键和请求/响应报文由服务层输出脱敏副本，不改写 Mapper 原始 DTO 或数据库事实。
5. 接口只读，不修改路由规则、不重试支付、不更新支付状态，也不代表真实渠道受理、账务、清分、结算、对账或生产放行事实。

## 5. 支付请求管理查询

接口：`GET /api/payment-requests`

查询参数：

| 参数 | 说明 |
| --- | --- |
| `requestNo` | 请求编号，模糊匹配 |
| `paymentOrderId` | 支付单号，模糊匹配 |
| `orderNo` | 订单号，模糊匹配 |
| `channelCode` | 渠道编码，模糊匹配 |
| `terminal` | 终端，支持 `全部 / H5 / PC / APP / 小程序` |
| `clientIp` | 客户端 IP，模糊匹配 |
| `requestStatus` | 请求状态，支持 `全部 / 请求已发起 / 请求成功 / 请求失败 / 已关闭` |
| `sortField` | 排序字段，支持 `createdAt / channelCode / terminal` |
| `sortOrder` | 排序方向，支持 `asc / desc` |
| `pageNo` | 页码，从 `1` 开始 |
| `pageSize` | 每页条数，最大 `100` |

业务说明：

1. 当前用于查看支付尝试、渠道请求报文、响应报文以及路由结果的统一台账。
2. 前端可通过 `clientIp + terminal + channelCode` 组合快速反查同一批终端流量。
3. 新增 `GET /api/payment-requests/export` 导出能力，沿用同一组筛选参数输出 CSV 快照，便于测试留档、问题复盘和跨团队对数。
4. 当前接口已兼容 `请求已发起/请求成功/请求失败` 与底层 `处理中/成功/失败` 状态别名映射，避免页面口径与数据库状态不一致。
5. 当前支付请求、支付流水和路由执行统一递归脱敏 `clientIp / clientDeviceId / idempotencyKey / payerPhone / mobile / phone / tel / bankCardNo / cardNo / customerName / name` 等字段；支持嵌套对象、数组和大小写变体，非法 JSON 返回 `[REDACTED]`。
6. 新增 `GET /api/payment-requests/overview`，复用同一组筛选条件返回总量、成功/失败/处理中/等待回调分布、涉及渠道数、涉及终端数、重复支付单请求数、缺响应报文数和最近请求时间，方便运营先看全局再下钻列表。
7. 列表和导出只返回脱敏副本，数据库原始支付请求事实不被覆盖；生产仍需补统一身份、角色与字段级授权、导出审计和留存策略。
8. 列表、唯一请求详情和 CSV 从所属支付单投影 `successFactIssue`，正常或无支付单匹配为 `null`，成功时间缺失为 `SUCCESS_TIME_MISSING`，成功事实不一致为 `SUCCESS_FACT_MISMATCH`；复用支付单原精度条件而非请求状态或展示时间。CSV 在原列末尾追加“支付单成功事实异常类型”；异常请求仍可读，但管理端不提供会被成功事实门禁拒绝的支付单详情入口，普通请求保留原导航。

## 6. 支付处理日志查询

接口：`GET /api/payment-logs`

总览接口：`GET /api/payment-logs/overview`

导出接口：`GET /api/payment-logs/export`

查询参数：

| 参数 | 说明 |
| --- | --- |
| `paymentOrderId` | 支付单号，模糊匹配 |
| `orderNo` | 订单号，模糊匹配 |
| `processStage` | 处理阶段，支持 `全部 / 支付提交 / 支付路由 / 渠道回调 / 业务事件` |
| `logLevel` | 日志级别，支持 `全部 / INFO / WARN / ERROR` |
| `source` | 日志来源，模糊匹配 |
| `keyword` | 关键字，按日志消息模糊匹配 |
| `sortField` | 排序字段，支持 `createdAt / logLevel / processStage` |
| `sortOrder` | 排序方向，支持 `asc / desc` |
| `pageNo` | 页码，从 `1` 开始，仅列表接口使用 |
| `pageSize` | 每页条数，最大 `100`，仅列表接口使用 |

业务说明：

1. 当前统一展示支付提交、支付路由、渠道回调、业务事件四类处理事实；列表、导出和总览统一读取 `t_payment_process_log`，不再在查询时临时执行四表 `UNION ALL`。
2. 新增 `GET /api/payment-logs/overview`，在列表筛选条件不变的前提下返回日志总数、错误/告警/信息分布、涉及阶段数、涉及来源数、渠道回调错误数、业务事件告警数、回调关键字命中数和最近日志时间。
3. 前端可先使用总览接口定位风险面，再通过列表接口按支付单号、订单号、日志来源和关键字继续下钻。
4. 导出接口复用同一套筛选条件与排序规则，输出 UTF-8 BOM CSV，便于测试留档、运营复盘和研发排障。
5. `t_payment_attempt` 继续作为独立支付请求事实；增量迁移会把既有尝试、回调、路由和事件幂等回填到处理日志表，主链路后续在同一事务内追加日志。接口字段保持兼容。
6. `logLevel` 是 `INFO / WARN / ERROR` 严重级别，`logLevelType` 是原动作结果的展示样式；成功提交或回调可以合法返回 `INFO + success`，普通信息为 `INFO + info`，告警为 `WARN + warn`，错误为 `ERROR + danger`。列表和详情前端对这些组合均校验，不把成功结果样式误判为无效日志，也不接受未知或错误配对。
7. 列表、唯一详情与 CSV 从所属支付单投影 `successFactIssue`，正常或无支付单匹配时为 `null`，成功时间缺失为 `SUCCESS_TIME_MISSING`，成功事实不一致为 `SUCCESS_FACT_MISMATCH`；复用支付单原精度判断而非日志显示时间。CSV 在原列后追加“支付单成功事实异常类型”。异常日志仍可读，但管理端不把支付详情当作可用联查入口；其他日志保留原入口。
8. `message` 仅返回可检索的最小必要摘要：支付提交保留支付方式和请求状态，通知保留回调类型和处理状态，事件保留事件类型；不复制客户端 IP、设备、手机号、幂等键、请求/响应报文或事件载荷。需要原始事实时按日志编号和支付单号进入对应支付请求、通知或事件查询链路。
9. 当前数据最小化不代表角色权限已经落地；支付请求详情和其他原始事实接口仍需在生产接入统一身份与字段级授权。

## 7. 支付日终处理与任务中心导出补充

1. 新增 `GET /api/payment-day-end/export`，直接导出全部支付日终批次快照，字段覆盖业务日期、运行方式、主链路成功事实、差异事实、退款收口事实、对账准入结论和触发人，供财务对账、测试留档和问题复盘使用。
2. 新增 `GET /api/payment-task-center/task-runs/export`，复用任务日志列表当前筛选条件导出 CSV，字段覆盖任务编码、运行方式、任务状态、严重等级、升级状态、处理结果、建议动作和推荐路由。
3. 两个导出接口均以 UTF-8 BOM 返回，文件可直接被 Excel 打开；导出文本列统一按 CSV 双引号规则转义，避免摘要、建议动作和备注中的逗号、引号破坏结构。

## 8. 支付配置治理快照导出

接口：`GET /api/payment-config/export`

查询参数：

| 参数 | 说明 |
| --- | --- |
| `section` | 导出分区，支持 `ALL / CHANNELS / ROUTE_RULES / PROTOCOLS / RETURN_CODES / GATEWAYS / CONTROL_POLICIES / ALERT_PROVIDERS / ISSUE_DUTY_ROSTERS`，默认 `ALL` |

业务说明：

1. 该接口用于导出配置治理快照，而不是导出单一页面截图，供运营、财务、测试、发布评审和事故复盘直接留档。
2. 导出列统一覆盖 `配置域 / 主键编码 / 次级编码 / 配置名称 / 状态 / 治理摘要 / 适用范围 / 风控或限制 / 运维建议 / 更新时间`。
3. `ALL` 模式会把渠道、路由、协议、返回码、网关、控制策略、告警供应商和值班路由合并到一张 CSV 中，并通过 `配置域` 字段区分。
4. 当前版本优先解决“治理快照留痕”问题，后续如要支持更复杂的筛选、异步归档和下载中心，可在此能力之上继续扩展。

查询参数：

| 参数 | 说明 |
| --- | --- |
| `paymentOrderId` | 支付单号，模糊匹配 |
| `orderNo` | 订单号，模糊匹配 |
| `processStage` | 处理阶段，支持 `全部 / 支付提交 / 支付路由 / 渠道回调 / 业务事件` |
| `logLevel` | 日志级别，支持 `全部 / INFO / WARN / ERROR` |
| `source` | 来源，模糊匹配 |
| `keyword` | 日志关键字，模糊匹配 |
| `sortField` | 排序字段，支持 `createdAt / logLevel / processStage` |
| `sortOrder` | 排序方向，支持 `asc / desc` |
| `pageNo` | 页码，从 `1` 开始 |
| `pageSize` | 每页条数，最大 `100` |

业务说明：

1. 当前统一收敛支付提交、支付路由、渠道回调和业务事件四类处理日志。
2. 排序能力主要用于运营优先查看最新错误、按阶段集中复核问题单。
3. 当前已支持沿用同一组筛选参数导出 CSV 快照，便于测试留档、问题复盘和跨团队对数。
4. 生产环境后续接日志检索、链路追踪和告警平台时，本接口可继续作为业务态排障入口。

## 7. 支付事件出站查询与重发

查询接口：`GET /api/payment-events`

总览接口：`GET /api/payment-events/overview`

重发接口：`POST /api/payment-events/republish`

导出接口：`GET /api/payment-events/export`

查询参数：

| 参数 | 说明 |
| --- | --- |
| `paymentOrderId` | 支付单号，模糊匹配 |
| `eventType` | 事件类型，支持 `全部` |
| `publishStatus` | 发布状态，支持 `全部` |
| `downstreamSystem` | 下游系统，支持 `全部` |
| `eventTopic` | 事件主题关键字，模糊匹配 |
| `sortField` | 排序字段，支持 `createdAt / retryCount / nextRetryAt` |
 | `sortOrder` | 排序方向，支持 `asc / desc` |
 | `pageNo` | 页码，从 `1` 开始 |
| `pageSize` | 每页条数，最大 `100` |

重发请求示例：

```json
{
  "eventNo": "EVT202607190002"
}
```

业务说明：

1. 新增 `GET /api/payment-events/overview`，复用同一组筛选条件返回事件总数、发布成功/发布中/失败/死信分布、涉及下游系统数、到期待重试事件数、支付成功事件数和最近发布时间，便于运营和研发先看风险面再下钻列表。
2. 列表、总览、导出三类接口当前共用统一筛选口径，避免前端页面统计、导出快照和后台查询出现不一致。
3. `FAILED_OR_DEAD_LETTER` 作为联合筛选口径保留，用于快速圈定需要补偿和人工干预的出站事件。
4. 列表、重发后的列表响应、唯一事件详情及 CSV 尾列返回所属支付单的 `successFactIssue`：正常或无匹配支付单为 `null`，成功时间缺失为 `SUCCESS_TIME_MISSING`，成功事实不一致为 `SUCCESS_FACT_MISMATCH`。诊断复用支付单数据库原精度判断，不把事件类型或发布状态当作资金成功事实。异常事件仍可查看并联查处理日志与流水，但管理端不跳转已被支付事实门禁拒绝的支付详情；本字段不修改事件投递、重试或 HTTP 已完成阶段的既有语义。
1. 重发接口会沿用当前查询条件返回最新分页结果，前端无需额外再发一次查询。
2. 当前主要支撑支付成功、支付关闭、退款等出站事件的可见化与手动补偿入口。
3. 后续若接入真实消息总线，需要继续补投递批次、订阅确认和死信处理字段。

支付成功事件统一载荷契约（HTTP 与 AMQP 共用语义）：

| 字段 | 说明 |
| --- | --- |
| `eventNo` | payment-core 出站事件号，用于跨系统排障和投递幂等追踪 |
| `eventType` | 事件类型，当前为 `PAYMENT_SUCCESS` |
| `schemaVersion` | 载荷契约版本，当前为 `1` |
| `businessChainId` | 跨系统业务链路 ID，当前按 `CHAIN-{paymentOrderId}` 稳定生成 |

HTTP 请求体和 AMQP JSON 消息均保留上述字段及原有业务字段；AMQP 消息头同时携带 `eventType`、`schemaVersion`、`paymentOrderId`，并继续使用 `eventNo` 作为 `messageId/correlationId`。新增字段对历史消费者向后兼容，不能替代真实 broker 订阅确认、生产数据或下游业务事实。

## 8. 支付单管理查询与导出

查询接口：`GET /api/payments`

导出接口：`GET /api/payments/export`

查询参数：

| 参数 | 说明 |
| --- | --- |
| `paymentOrderId` | 支付单号，模糊匹配 |
| `orderNo` | 订单号，模糊匹配 |
| `paymentMethod` | 支付方式，支持 `全部 / 微信支付 / 支付宝 / 银行卡` |
| `status` | 支付状态，支持 `全部 / PREPAY_CREATED / WAIT_CALLBACK / SUCCESS / CLOSED` |
| `pageNo` | 页码，从 `1` 开始 |
| `pageSize` | 每页条数，最大 `100` |

业务说明：

1. 当前后台支付单列表和导出 CSV 使用同一组筛选口径，避免页面看到的数据和导出快照不一致。
2. 导出文件包含支付单号、订单号、客户名称、支付金额、支付方式、支付渠道、渠道交易号、支付状态和创建时间。
3. 明细钻取仍沿用 `GET /api/payments/{paymentOrderId}`，导出接口只负责当前筛选结果快照。
4. 列表和 CSV 中的客户名称均在 Service 出参边界基于 Mapper DTO 副本脱敏，原始筛选值和数据库事实不变。
5. 列表的 `successFactIssue` 对成功时间缺失和成功事实不一致分别返回 `SUCCESS_TIME_MISSING`、`SUCCESS_FACT_MISMATCH`，正常行返回 `null`。CSV 在原有列末尾追加“成功事实异常类型”，使用同一判断和原始异常码；正常行该列为空，不得将异常 `SUCCESS` 行仅凭状态视为可信成功事实。

## 9. 创建预付单

接口：`POST /api/payments/prepay`

请求示例：

```json
{
  "orderNo": "ORD202607190003",
  "payScene": "HOME_CLEAN",
  "remark": "上门服务尾款"
}
```

返回示例：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "prepayOrderNo": "PRE1752912345678",
    "billNo": "BILL1752912341234",
    "orderNo": "ORD202607190003",
    "customerName": "张女士",
    "amount": "¥168.00",
    "payScene": "HOME_CLEAN",
    "cashierTitle": "家政服务收银台",
    "cashierStatus": "待支付",
    "cashierStatusType": "warn",
    "paymentOrderId": "PAY1752912340001",
    "createdAt": "2026-07-19 10:15:30",
    "expiresAt": "2026-07-19 11:15:30"
  },
  "requestId": "REQ202607190001"
}
```

业务说明：

1. 若订单尚无账单，则自动创建账单。
2. 若订单当前不存在“未过期且未收口”的预付单，则自动创建新的支付单与预付单。
3. 若同一订单已存在有效预付单，则直接复用原预付单与支付单，保证重复拉起收银台幂等。

## 9. 获取收银台参数

接口：`GET /api/payments/cashier/{prepayOrderNo}`

返回示例：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "prepayOrderNo": "PRE1752912345678",
    "orderNo": "ORD202607190003",
    "billNo": "BILL1752912341234",
    "customerName": "张女士",
    "amount": "¥168.00",
    "payScene": "HOME_CLEAN",
    "title": "家政服务收银台",
    "status": "待支付",
    "statusType": "warn",
    "expiresAt": "2026-07-19 11:15:30",
    "channels": ["微信支付", "支付宝", "银行卡"]
  },
  "requestId": "REQ202607190002"
}
```

## 10. 提交支付

接口：`POST /api/payments/submit`

请求示例：

```json
{
  "prepayOrderNo": "PRE1752912345678",
  "paymentMethod": "微信支付",
  "channelCode": "WX_H5",
  "sourceAppId": "housekeeping-app-web",
  "merchantNo": "MCH_WX_10001",
  "accessToken": "runtime-injected-token",
  "terminal": "APP_WEB",
  "idempotencyKey": "PRE1752912345678|微信支付|WX_H5"
}
```

`merchantNo` 使用实际渠道商户号，并与 `paymentMethod / channelCode` 保持一致：微信支付使用 `MCH_WX_10001`，支付宝使用 `MCH_ALI_20001`，银行卡使用 `BANK_OFFLINE_30001`。来源应用权限由 `sourceAppId` 对应的控制策略独立校验，不再把来源应用标识误作渠道商户号。

`clientIp` 请求字段仅为兼容保留，HTTP 入口不会信任该值，而是始终按 14.46 节的连接与可信代理规则覆盖。

`terminal` 的规范值包含 `APP / H5 / PC / MINI_PROGRAM / API / UNKNOWN`。微信小程序应发送 `MINI_PROGRAM`；服务端兼容 `WECHAT_MINI_PROGRAM / WX_MINI_PROGRAM / 小程序` 并统一保存为 `MINI_PROGRAM`。服务到服务调用必须显式发送 `API`。本地 demo 来源应用 `housekeeping-mini-program` 仅允许微信支付、`wx_h5` 与 `MCH_WX_10001`，真实环境必须由受控配置和密钥托管替换示例令牌。

小程序本地 demo 提交示例：

```json
{
  "prepayOrderNo": "PRE1752912345678",
  "paymentMethod": "微信支付",
  "channelCode": "WX_H5",
  "sourceAppId": "housekeeping-mini-program",
  "merchantNo": "MCH_WX_10001",
  "accessToken": "token-housekeeping-mini-program",
  "terminal": "MINI_PROGRAM",
  "clientDeviceId": "wechat-mini-program",
  "idempotencyKey": "MINI-PRE1752912345678-1789351200000-abc"
}
```

示例令牌只存在于 demo profile。当前提交响应只表达本地渠道适配器已受理并进入待回调，不包含微信 `wx.requestPayment` 所需的 `timeStamp / nonceStr / package / signType / paySign`。真实接入必须由可信后端按当前用户 openid 和绑定商户生成短时签名参数，前端不得自行拼装或缓存长期凭证。

`channelCode` 会由支付核心统一规范化为标准编码：

| 请求渠道/支付方式 | 标准渠道编码 |
| --- | --- |
| 微信、`WX_H5`、`wx_jsapi` | `wx_h5` |
| 支付宝、`ALIPAY_H5`、`alipay_h5` | `alipay_h5` |
| 银行卡、银行转账、`BANK_CARD` | `offline_bank` |

标准订单的未完成支付提交会在路由前核对当前订单应收、已付、客户与预付金额，并在取得同订单预付创建锁后重新读取核对。订单已结清或超额支付返回 `PAYMENT_ORDER_ALREADY_SETTLED`；订单来源金额或客户信息不完整返回 `PAYMENT_ORDER_SOURCE_MISSING`；金额无效、客户变化或当前剩余应付与原预付金额不一致返回 `PAYMENT_PREPAY_REQUEST_CONFLICT`。这些拒绝路径不应创建新的支付尝试或下发渠道请求；应重新核实订单并发起匹配当前欠款的预付。仅创建时标记为 `VIRTUAL` 且当前无标准订单行的虚拟单保留既有提交语义；标准来源行消失、虚拟业务号后来变成标准订单，或存量 `UNKNOWN` 标记无法确认来源时，未完成提交返回 `PAYMENT_ORDER_SOURCE_MISSING`。该本地订单锁只协调遵守同一锁约定的写入，不保证其他系统对订单并发修改时的全局原子性。

返回示例：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "prepayOrderNo": "PRE1752912345678",
    "billNo": "BILL1752912341234",
    "orderNo": "ORD202607190003",
    "customerName": "张女士",
    "amount": "¥168.00",
    "payScene": "HOME_CLEAN",
    "cashierTitle": "家政服务收银台",
    "cashierStatus": "支付中",
    "cashierStatusType": "info",
    "paymentOrderId": "PAY1752912340001",
    "createdAt": "2026-07-19 10:15:30",
    "expiresAt": "2026-07-19 11:15:30"
  },
  "requestId": "REQ202607190003"
}
```

落库效果：

1. 更新预付单状态
2. 更新支付单支付方式与渠道
3. 按渠道配置与路由规则执行真实路由决策，并记录路由日志
4. 记录支付尝试日志
5. 记录提交事件
6. 预写一条待回调日志
7. 提交前执行来源应用、渠道、商户号、访问令牌、自检状态和分钟级限流校验

路由说明：

1. 提交支付时，系统会基于 `paymentMethod`、请求渠道、支付场景、终端、金额和客户类型组装路由上下文。
2. 路由优先级为：
   - 已启用路由规则命中
   - 前端指定且已启用的渠道
   - 按支付方式匹配默认启用渠道
3. 路由执行前会校验渠道适用场景，当前按 `payScene / terminal / customerType` 与渠道 `sceneScope` 做匹配，不匹配的渠道不会进入本次提交。
4. 路由执行前会校验渠道单日限额，当前按“当日正金额已受理金额 + 本次请求金额 <= dailyLimit”判断，超限渠道不会进入本次提交；历史非正金额不得冲抵渠道容量，详见 14.391。
5. 提交前还会校验 `merchantNo` 是否在来源应用授权范围内；若该来源应用开启了令牌鉴权，则 `accessToken` 必须与控制策略中的运行时口令一致。
5. 若前端强制指定的渠道已停用、场景不匹配或超过单日限额，接口会直接返回业务错误，不再静默降级到其他渠道。
6. 若规则目标渠道被停用、场景不匹配或超过单日限额，则自动尝试规则配置的兜底渠道。
7. 未命中规则且未显式指定渠道时，系统先按支付方式、场景、健康状态、单日限额过滤候选，再使用支付单号作为稳定路由键，按渠道 `routeWeight` 做确定性加权选择；重复提交不会因随机数漂移到其他渠道。
8. `routeWeight=0` 表示渠道不参与支付方式默认候选池；该字段不覆盖规则目标或用户显式指定渠道。未携带稳定路由键的兼容调用仍按原优先级选择首个可用渠道。
9. 加权命中以 `PAYMENT_METHOD_WEIGHTED` 和“当前渠道权重/候选总权重”写入路由记录，便于运营核对配置与实际命中。
10. 规则表达式支持括号、`AND / OR / NOT`，且 `NOT > AND > OR`；字段支持 `paymentMethod / customerType / requestedChannel / channelCode / terminal / payScene / amount`。
11. 字符串字段支持 `= / != / IN (...)`，金额字段支持 `= / != / > / >= / < / <= / IN (...)`；含空格的值可使用单引号或双引号包裹。
12. 表达式为空表示无条件匹配；语法错误、未知字段、字符串使用大小比较或金额格式非法时，该规则按“不命中”处理并继续评估后续规则，不向支付提交链路抛出解析异常。
13. 当前内置规则示例：
   - `RULE_HOME_WX`：家政 H5 小额微信优先
   - `RULE_HOME_ALI`：家政 H5 小额支付宝优先
   - `RULE_ENTERPRISE_BANK`：企业或大额订单优先走线下银行

幂等说明：

1. 当同一预付单重复提交时，如果支付单已进入 `WAIT_CALLBACK`、`SUCCESS` 或 `CLOSED`，接口直接返回当前预付单信息。
2. 如果幂等键已存在，接口同样直接返回当前预付单信息。
3. 当前提交链路还会先对预付单执行“待支付 -> 支付中”的条件占位更新，只有首次占位成功的请求才允许继续调用渠道适配器。
4. 如果并发请求发现预付单已经被其他线程占位，接口会优先返回最新预付单状态，避免重复路由、重复下单和重复待回调日志。
5. 这样可以避免按钮重复点击、浏览器重试和并发接口重放导致重复路由、重复尝试和重复待回调日志。

## 8. 支付渠道回调

接口：`POST /api/payments/callback/{channel}`

路径参数：

| 参数 | 说明 |
| --- | --- |
| `channel` | 渠道编码，例如 `WX_H5` |

请求示例：

```json
{
  "paymentOrderId": "PAY1752912340001",
  "channelTransactionNo": "WX202607190001",
  "tradeStatus": "SUCCESS",
  "timestamp": "1752912930",
  "nonce": "N202607190001",
  "signature": "base64-signature"
}
```

验签说明：

1. `timestamp` 必须在允许时间窗内
2. `nonce` 必须未被重复使用，当前已落到持久化表做跨实例防重放
3. 签名串按 `channel|paymentOrderId|tradeStatus|channelTransactionNo|timestamp|nonce` 组织，`channel` 会先统一规范化为小写编码
{
  "paymentOrderId": "PAY1752912340001",
  "tradeStatus": "SUCCESS",
  "channelTransactionNo": "WX2026071900001",
  "timestamp": "1752912345",
  "nonce": "abc123",
  "signature": "base64-hmac-sha256"
}
```

回调验签默认开启；除显式 `demo` profile 的本地模拟环境外，签名原文为：

`channel|paymentOrderId|tradeStatus|channelTransactionNo|timestamp|nonce`

使用渠道独立密钥计算 HMAC-SHA256，并将结果 Base64 编码后放入 `signature`。渠道密钥只从支付渠道配置中读取；渠道安全配置或对应密钥/公钥缺失时返回 `PAYMENT-1006`，不存在全局共享密钥回退。

返回示例：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "paymentOrderId": "PAY1752912340001",
    "prepayOrderNo": "PRE1752912345678",
    "billNo": "BILL1752912341234",
    "orderNo": "ORD202607190003",
    "customerName": "张女士",
    "amount": "¥168.00",
    "paymentMethod": "微信支付",
    "channel": "WX_H5",
    "channelTransactionNo": "WX2026071900001",
    "status": "SUCCESS",
    "statusType": "success",
    "createdAt": "2026-07-19 10:15:30",
    "routeLogs": ["2026-07-19 10:16:01 | RULE_HOME_WX | 家政 H5 微信优先 -> wx_h5"],
    "notifyLogs": ["2026-07-19 10:16:33 | SUCCESS | 已收口"],
    "eventLogs": ["PAYMENT_SUCCESS | ORD202607190003 | 2026-07-19 10:16:33"]
  },
  "requestId": "REQ202607190004"
}
```

## 7. 主动查单

接口：`POST /api/payments/query`

请求示例：

```json
{
  "paymentOrderId": "PAY1752912340001"
}
```

说明：

当前版本已经抽象渠道查单适配器，并按 `wx_h5 / alipay_h5 / offline_bank / local-fallback` 做了第一版拆分；
真实微信、支付宝、银行查单适配器由 `gateway-access` 接入后注册，不改变本接口契约。

返回要点：

1. `channelTransactionNo` 会返回本次查单得到的渠道流水号。
2. `querySource` 会返回查单结果来源，例如 `WECHAT_SIMULATION`、`ALIPAY_SIMULATION`、`OFFLINE_BANK_SIMULATION`、`LOCAL_SIMULATION_FALLBACK`。
3. 渠道返回原始码后，支付提交或主动查单会把最近一次标准化结果持久化到支付单；本次查单响应以及后续 `GET /api/payments/{paymentOrderId}` 详情均稳定返回 `channelReturnCode / standardizedCode / standardizedMessage / handlingSuggestion / retryable / manualInterventionRequired / returnCodeMappingVersion / returnCodeMappingMatched`。
4. 只有 `ENABLED + ACTIVE` 映射参与匹配。未知、停用或已归档码保留原始事实，标准码降级为 `PAYMENT-CHANNEL-UNMAPPED`，重试口径为“未知”并提示人工判断；内部已确认 `SUCCESS` 的查单使用平台成功语义，不误报人工介入。

## 8. 关闭支付单

接口：`POST /api/payments/close`

请求示例：

```json
{
  "paymentOrderId": "PAY1752912340001",
  "reason": "ORDER_TIMEOUT"
}
```

说明：

关闭动作会：

1. 更新支付状态为 `CLOSED`
2. 写入 `PAYMENT_CLOSED` 事件
3. 返回最新支付详情

## 9. 查询支付单详情

接口：`GET /api/payments/{paymentOrderId}`

返回内容包含：

1. 支付基本信息
2. 预付单号
3. 账单号
4. 跨系统业务链路 ID：按支付单稳定派生为 `CHAIN-{paymentOrderId}`，用于对账系统查询支付、清分、结算和账务事实快照
5. 最近一次支付尝试信息：终端、客户端 IP、幂等键、尝试状态、请求报文、响应报文
6. 渠道路由轨迹
7. 回调轨迹
8. 事件轨迹

出参最小化规则：

1. `customerName / latestClientIp / latestIdempotencyKey / latestRequestPayload / latestResponsePayload` 统一返回脱敏副本。
2. `GET detail`、渠道回调、主动查单和关单四类返回支付详情的接口口径一致，不因动作入口不同再次暴露原始上下文。
3. 请求/响应报文按 JSON 结构递归脱敏，非法或标量 JSON 返回 `[REDACTED]`；Mapper DTO 和数据库事实不被修改。
4. 当前能力不提供敏感原文查看入口，也不代表可信身份、角色或字段级授权和生产访问审计已经完成。

## 13. 收款记录分页查询

接口：`GET /api/payment-records`

导出接口：`GET /api/payment-records/export`

当前补充能力：

1. 已支持 `paymentStatus`、`paymentChannel` 两类运营筛选。
2. 已支持按 `createdAt / paymentAmount / paidAt` 排序。
3. 适用于统一支付记录、微信支付宝支付记录和银行卡支付记录三类列表页共用。
4. 导出接口复用 `recordType / customerName / businessOrderNo / paymentType / paymentStatus / paymentChannel / sortField / sortOrder`，不接收分页参数，并与列表使用同一筛选、排序和成功退款聚合口径；旧 `userId` 可单独作为 `customerName` 的兼容别名。
5. 导出最多允许 `1000` 条完整记录，Mapper 固定读取 `1001` 条用于超限探测；超限时整体失败并提示缩小筛选范围，不静默截断。
6. 文件名固定为 `payment-records.csv`，采用 UTF-8 BOM；列值沿用列表脱敏规则并统一执行 CSV 转义和表格公式注入防护。
7. 当前导出用于本地运营排障和测试留档，不代表生产导出权限、审批、水印、下载审计和保留策略已完成。
8. `customerName` 按客户姓名模糊查询，历史参数 `userId` 仅是兼容别名，两者不可同时提供，均不代表真实用户 ID；支付记录的用户、用户支付渠道、商品、业务线和网关等无逐单可信来源字段返回 `null`。`returnParameterType` 仅在实际渠道交易号存在时为“渠道交易号”，`parameterValue` 返回该交易号；未留存的具体预付标识、支付宝订单号或网关身份不能从渠道代码推断。
9. 列表、唯一详情和 CSV 尾列增加所属支付单的 `successFactIssue`：正常为 `null`，成功时间缺失为 `SUCCESS_TIME_MISSING`，成功事实不一致为 `SUCCESS_FACT_MISMATCH`。此为支付单原精度诊断，不凭中文“支付成功”、显示到秒的时间或事件主题推断资金成功；异常记录保留记录详情与安全联查，成功计数不将异常行视为可信成功。

接口：`GET /api/bills`

总览接口：`GET /api/bills/overview`

当前补充能力：

1. 已支持 `customerName` 筛选。
2. 已支持按 `createdAt / dueAt / billAmount / unpaidAmount` 排序。
3. `GET /api/bills/overview` 复用同一组筛选条件返回账单总数、已支付/待支付/部分支付/逾期账单数，以及应收金额、已付金额、待付金额合计。
4. `GET /api/bills/export` 复用账单号、订单号、客户名称、账单状态和排序条件导出全量匹配数据，不受列表分页影响。
5. 导出文件名为 `bills.csv`，采用 UTF-8 BOM 编码，并对 CSV 字段中的双引号进行转义。

接口：`GET /api/cashier-sessions`

总览接口：`GET /api/cashier-sessions/overview`

当前补充能力：

1. 已支持 `paymentOrderId`、`customerName` 筛选。
2. 已支持按 `createdAt / expiresAt / amount` 排序。
3. `GET /api/cashier-sessions/overview` 复用同一组筛选条件返回会话总数、已失效会话数、成功/已完成会话数、支付中会话数、待支付会话数、涉及终端数、15 分钟内即将失效会话数和会话金额合计。
4. `GET /api/cashier-sessions/export` 复用会话号、支付单号、订单号、客户、终端、会话状态和排序条件导出全量匹配数据，不受列表分页影响。
5. 导出文件名为 `cashier-sessions.csv`，采用 UTF-8 BOM 编码，并对 CSV 字段中的双引号进行转义。

接口：`GET /api/refunds`

当前补充能力：

1. 已支持 `refundOrderId`、`paymentOrderId`、`refundStatus`、`refundMethod` 筛选。
2. `GET /api/refunds/export` 复用退款单号、支付单号、退款状态、退款方式导出全量匹配数据，不受列表分页影响。
3. 导出结果继续保持 `applied_at DESC` 顺序，不引入新的导出排序参数。
4. 导出文件名为 `refunds.csv`，采用 UTF-8 BOM 编码，并对 CSV 字段中的双引号进行转义。

查询参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `recordType` | 否 | `ALL`、`WECHAT`、`BANK_CARD`，默认 `ALL` |
| `customerName` | 否 | 客户姓名模糊查询；与 `userId` 不可同时提供 |
| `userId` | 否 | 兼容旧客户端的客户姓名筛选别名；不支持真实用户 ID 查询 |
| `businessOrderNo` | 否 | 业务订单号模糊查询 |
| `paymentType` | 否 | 支付类型，例如 `消费支付` |
| `pageNo` | 否 | 页码，从 1 开始，默认 1 |
| `pageSize` | 否 | 每页条数，默认 20，最大 100 |

返回结构：

```json
{
  "items": [],
  "total": 3,
  "pageNo": 1,
  "pageSize": 20
}
```

## 14. 收款记录详情查询

接口：`GET /api/payment-records/{paymentOrderId}`

返回示例：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "paymentOrderId": "PAY1752912340001",
    "businessOrderNo": "ORD202607190003",
    "paymentRequestNo": "PAY1752912340001-REQ",
    "paymentGateway": null,
    "paymentChannel": "wx_h5",
    "paymentMethod": "微信支付",
    "paymentStatus": "支付成功",
    "paymentAmount": "¥168.00",
    "latestTerminal": "APP_WEB",
    "latestClientIp": "127.0.0.1",
    "latestIdempotencyKey": "PRE1752912345678|微信支付|WX_H5",
    "latestAttemptStatus": "SUCCESS",
    "latestAttemptStatusType": "success",
    "latestRequestPayload": "{\"paymentOrderId\":\"PAY1752912340001\",\"channelCode\":\"wx_h5\"}",
    "latestResponsePayload": "{\"tradeStatus\":\"SUCCESS\",\"channelTransactionNo\":\"WX2026071900001\"}",
    "routeLogs": [
      "2026-07-19 10:16:01 | RULE_HOME_WX | 家政 H5 微信优先 -> wx_h5"
    ],
    "notifyLogs": [
      "2026-07-19 10:16:33 | SUCCESS | 已收口"
    ],
    "eventLogs": [
      "PAYMENT_SUCCESS | ORD202607190003 | 2026-07-19 10:16:33"
    ]
  },
  "requestId": "REQ202607190010"
}
```

返回说明：

1. 该接口面向后台“支付记录详情”页面，补齐支付记录列表无法承载的报文和轨迹信息。
2. `latestRequestPayload` 与 `latestResponsePayload` 来自最近一次支付尝试记录。
3. `routeLogs`、`notifyLogs`、`eventLogs` 直接复用支付主链路轨迹表，便于运营与研发统一排障。
4. 唯一详情从所属支付单投影 `successFactIssue`，与列表、CSV 使用同一数据库原精度诊断；异常记录仍可只读排障，但管理端禁用被事实门禁拒绝的支付详情及主动查单入口。

## 12. 退款管理接口

### 12.1 分页查询退款单

接口：`GET /api/refunds`

查询参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `refundOrderId` | 否 | 退款单号模糊查询 |
| `paymentOrderId` | 否 | 原支付单号模糊查询 |
| `refundStatus` | 否 | `全部`、`REVIEWING`、`PROCESSING`、`SUCCESS`、`FAIL` |
| `refundMethod` | 否 | `全部`、`原路退款`、`线下打款`、`退转付` |
| `pageNo` | 否 | 页码，从 1 开始 |
| `pageSize` | 否 | 每页条数，最大 100 |

### 12.2 发起退款申请

接口：`POST /api/refunds/apply`

请求示例：

```json
{
  "paymentOrderId": "PAY202607190001",
  "idempotencyKey": "7b1db871-17c6-4ec0-a98d-d6a8ae5d9a47",
  "refundAmount": "68.00",
  "refundMethod": "原路退款",
  "refundReason": "客户取消服务",
  "operator": "finance-operator"
}
```

业务约束：

1. 原支付单必须存在且状态为 `SUCCESS`。
2. `refundAmount` 必须大于 `0`。
3. 同一支付单下 `REVIEWING`、`PROCESSING`、`SUCCESS` 状态退款金额累计不能超过原支付金额。
4. `idempotencyKey` 与 `operator` 必填；同支付单、同键、同规范化申请内容重放时返回原退款单，同键异参返回 `PAYMENT-1053`。
5. 创建后状态为 `REVIEWING`，等待运营或财务审核。

### 12.3 查询退款详情

接口：`GET /api/refunds/{refundOrderId}`

返回内容：

1. 退款基础信息：退款单号、原支付单号、原订单号、客户名称、退款金额、退款方式、退款原因、状态、申请时间、成功时间。
2. 原支付快照：原支付金额、原支付方式、原支付状态。
3. `operationLogs`：退款操作日志，覆盖发起申请、审核通过、成功回调、失败回调、失败重试以及任务中心批量重试等动作。

### 12.4 退款动作与渠道回调接口

审核通过：

```http
POST /api/refunds/approve
```

可信渠道回调：

```http
POST /api/refunds/callback/{channel}
```

失败重试：

```http
POST /api/refunds/retry
```

请求示例：

```json
{
  "refundOrderId": "REF20260720223600123",
  "remark": "运营审核通过"
}
```

渠道回调请求示例：

```json
{
  "refundOrderId": "REF20260720223600123",
  "refundAttemptNo": "RFA20260720223600123",
  "channelRefundNo": "WXREF202607200001",
  "refundStatus": "SUCCESS",
  "timestamp": "1753022160",
  "nonce": "refund-notify-001",
  "signature": "base64-signature"
}
```

状态流转：

| 动作 | 原状态 | 目标状态 |
| --- | --- | --- |
| 审核通过 | `REVIEWING` | `PROCESSING` |
| 渠道成功回调 | `PROCESSING` | `SUCCESS` |
| 渠道失败回调 | `PROCESSING` | `FAIL` |
| 失败重试 | `FAIL` | `PROCESSING` |

实现说明：

1. 回调签名原文为 `channel|refundOrderId|refundAttemptNo|refundStatus|channelRefundNo|timestamp|nonce`，渠道规范化为小写；签名算法、时间窗和 nonce 防重规则与支付回调共享。
2. 后端锁定退款单并核对当前退款尝试、实际受理渠道与渠道退款号，只有 `SUCCESS/FAIL` 可从 `PROCESSING` 收口；相同终态幂等返回，相反终态返回冲突。
3. `SUCCESS` 会写入 `success_at`，`retry` 会清空成功时间；可信回调操作人固定为 `channel-callback`。
4. 后台不再提供模拟成功/失败接口。当前通用回调契约仍需由真实渠道适配器映射，主动查单、渠道流水和到账核验尚未完成。

## 11.4 服务者结算单查询与导出

查询接口：`GET /api/settlements/workers`

总览接口：`GET /api/settlements/workers/overview`

导出接口：`GET /api/settlements/workers/export`

查询参数：

| 参数 | 说明 |
| --- | --- |
| `settlementOrderId` | 结算单号，模糊匹配 |
| `workerKeyword` | 服务者名称关键字，模糊匹配 |
| `settlementStatus` | 结算状态，支持 `全部 / 待审核 / 待出款 / 出款成功` |
| `payoutStatus` | 出款状态，支持 `全部 / 待出款 / 出款中 / 出款成功` |
| `pageNo` | 页码，从 `1` 开始，仅列表接口使用 |
| `pageSize` | 每页条数，最大 `100`，仅列表接口使用 |

业务说明：

1. 新增 `GET /api/settlements/workers/overview`，在列表筛选条件不变的前提下返回结算单总数、待审核/待出款/出款中/出款成功分布、实结金额合计、扣减金额合计、保证金影响合计和净额为负结算单数。
2. 列表、总览、导出三类接口当前共用统一筛选口径，避免页面总览、导出快照和后台查询口径不一致。
3. 当前模块在 `payment-core` 中仍定位为支付侧快速核对入口，完整审批、出款、核销、失败重打等作业后续由 `settlement-system` 承接。

## 12. 支付配置中心接口

### 12.1 查询配置总览

接口：`GET /api/payment-config`

返回内容：

1. `channels`：支付渠道配置，包含渠道编码、渠道名称、支付方式、商户号、商户应用标识、回调通知地址、验签密钥配置情况、证书档案、回调验签时间窗、原路退款时效、风控标签、适用场景、状态、单日限额、优先级和默认路由权重 `routeWeight`。
2. `routeRules`：支付路由规则，包含规则编码、规则名称、匹配场景、匹配表达式、目标渠道、兜底渠道、稳定灰度比例 `rolloutPercentage`、分桶盐值 `rolloutSalt`、状态、优先级。
3. `protocols`：支付协议配置，包含协议编码、协议名称、协议类型、模板版本、签约模式、适用场景、适用渠道、商户确认要求、风控标签、状态。
4. `returnCodeMappings`：渠道返回码映射，包含渠道编码、渠道返回码、标准错误码、标准错误文案、处理建议、是否可重试、是否需要人工介入、映射版本、归档状态和启停状态。
5. `gateways`：支付网关接入配置，包含网关编码、网关名称、接入模式、适用渠道、环境范围、网关基础地址、报文协议、签名算法、证书别名、证书状态、发布阶段、灰度策略、回调白名单、适配器编排、超时时间、重试策略和状态。
6. `controlPolicies`：支付控制策略配置，包含来源应用标识、来源应用名称、允许支付方式、允许渠道、允许商户号、两级分钟限流阈值、令牌鉴权及令牌是否配置、严格模式、自检状态、自检提示和启停状态；不返回令牌原文。

### 12.2 配置启停与路由规则启停复核

启停渠道：

```http
POST /api/payment-config/channels/toggle
GET  /api/payment-config/channels/reviews
POST /api/payment-config/channels/reviews
POST /api/payment-config/channels/reviews/{requestNo}/approve
POST /api/payment-config/channels/reviews/{requestNo}/reject
GET  /api/payment-config/channels/{channelCode}/versions
POST /api/payment-config/channels/{channelCode}/rollback
```

提交请求示例：

```json
{
  "channelCode": "wx_h5",
  "enabled": false,
  "requester": "maker-a",
  "changeReason": "渠道维护期间暂停新支付承接"
}
```

新增或编辑完整候选调用 `POST /channels/reviews`，请求外层为申请人，内层为候选：

```json
{
  "requester": "maker-a",
  "candidate": {
    "channelCode": "wx_h5",
    "channelName": "微信 H5 支付",
    "paymentMethod": "微信支付",
    "merchantNo": "MCH_WX_10001",
    "merchantAppId": "wx-app-h5-001",
    "callbackSecret": "",
    "callbackSignAlgorithm": "HMAC-SHA256",
    "callbackPublicKey": "",
    "callbackNotifyUrl": "https://pay.example.com/callback/wx_h5",
    "certificateProfile": "wx-cert-profile-v2026.09",
    "notifySignWindowSec": 300,
    "refundWindowDays": 180,
    "riskControlTag": "实名校验+重复支付监控",
    "sceneScope": "H5/APP/小程序",
    "enabled": true,
    "dailyLimit": 500000.00,
    "priority": 1,
    "routeWeight": 100,
    "changeReason": "更新证书档案"
  }
}
```

渠道编码按小写规范化。同一渠道的 `CREATE / UPDATE / STATUS_CHANGE` 共用一条 `PENDING_REVIEW` 占位。既有渠道的 `paymentMethod` 按原文不可变，编辑候选、历史回滚或待审批准若尝试修改会拒绝；包括只改变别名文字的情况，因为路由表达式和历史支付快照可能依赖原文。需要切换支付方式时须新建渠道编码、人工迁移关联路由规则，并按治理流程停用旧渠道，不自动改写历史支付与版本。新增时密钥必填；编辑时空密钥或公钥只表示由服务端继承当前值。签名算法仅支持 `HMAC-SHA256 / RSA2`，RSA2 必须有公钥，回调地址必须使用 HTTPS，验签窗口和退款时效为正数，限额和优先级非负，路由权重为 `0-100`。

启停提交保存完整运行态基线指纹；编辑提交同时保存完整候选和基线，新增基线为空。三类提交与驳回都不修改运行态，列表仅返回密钥/公钥是否配置，不返回原文。

批准或驳回请求使用 `reviewer / reviewComment`。批准先锁复核申请、再锁渠道并重验完整候选；CREATE 要求编码仍不存在，UPDATE/STATUS_CHANGE 要求完整基线仍一致。同人复核、重复处理、编码占用或配置漂移时拒绝。只有异人声明批准才新增或更新运行态，渠道写入与复核完成同事务提交；驳回不改运行态，完成后释放渠道待办占位。列表返回申请号、操作类型、非敏感候选、候选状态、基线摘要、申请与复核记录、时间及固定的 `identityTrustLevel=SELF_DECLARED`。

路由规则启停申请：

```http
POST /api/payment-config/route-rules/toggle
```

请求示例：

```json
{
  "ruleCode": "RULE_HOME_WX",
  "enabled": false,
  "requester": "maker-a",
  "changeReason": "渠道维护期间停用微信优先规则"
}
```

该接口只创建 `STATUS_CHANGE` 待复核候选，不直接更新运行态。规则不存在、状态未变化、申请人或原因为空，以及同规则已有待复核申请时均拒绝。

启停支付协议：

```http
POST /api/payment-config/protocols/toggle
```

请求示例：

```json
{
  "protocolCode": "PROTO_HOUSEKEEPING_SIGN_V1",
  "enabled": false,
  "requester": "maker-a",
  "changeReason": "停止旧模板继续签约"
}
```

该接口只创建 `STATUS_CHANGE` 待复核候选，不直接更新运行态。协议不存在、状态未变化、申请人或原因为空，以及同协议已有待复核申请时均拒绝。

启停渠道返回码映射：

```http
POST /api/payment-config/return-codes/toggle
GET  /api/payment-config/return-codes/reviews
POST /api/payment-config/return-codes/reviews/{requestNo}/approve
POST /api/payment-config/return-codes/reviews/{requestNo}/reject
```

提交请求示例：

```json
{
  "channelCode": "wx_h5",
  "channelReturnCode": "USERPAYING",
  "enabled": false,
  "requester": "maker-a",
  "changeReason": "暂停该返回码自动承接"
}
```

渠道编码按小写、渠道返回码按大写规范化。同一映射同时最多一条 `PENDING_REVIEW`；映射不存在、已归档、目标状态未变化或已有待办时拒绝。提交保存完整映射基线指纹，不修改运行态。

批准或驳回请求使用 `reviewer / reviewComment`。批准先锁复核申请，再锁运行映射并复验候选状态与完整基线；CSV 导入或其他映射字段变化会使旧候选失效。只有异人声明批准才更新状态，状态写入与复核完成同事务提交；驳回不改运行态，完成后释放该映射待办占位。列表返回申请、候选状态、基线、复核记录、时间和固定的 `identityTrustLevel=SELF_DECLARED`。

返回码 CSV 差异预检与复核：

```http
POST /api/payment-config/return-codes/import/preview
GET  /api/payment-config/return-codes/import/reviews
POST /api/payment-config/return-codes/import/reviews
POST /api/payment-config/return-codes/import/reviews/{requestNo}/approve
POST /api/payment-config/return-codes/import/reviews/{requestNo}/reject
```

固定 CSV 表头：

```csv
channelCode,channelReturnCode,standardizedCode,standardizedMessage,handlingSuggestion,retryable,manualInterventionRequired,mappingVersion,status,priority
wx_h5,USERPAYING,PAYMENT-CHANNEL-1001,用户支付中,等待回调或主动查单,是,否,v2026.09.1,ENABLED,1
```

预检请求只需传 `csvContent`；响应返回 `baselineFingerprint`、整批 `valid`、新增/更新/不变/无效数量和逐行 `CREATE / UPDATE / UNCHANGED / INVALID` 结果。提交复核请求需原样传回 `csvContent` 和 `baselineFingerprint`，并补充：

```json
{
  "csvContent": "channelCode,...",
  "baselineFingerprint": "64位SHA-256",
  "requester": "maker-a",
  "changeReason": "同步渠道最新码表"
}
```

提交阶段先锁唯一待办占位、再锁运行态映射并重新计算基线；基线漂移、无实际变化或任一无效行时拒绝。合法候选保存规范化 CSV、`sourceSha256`、`baselineFingerprint`、差异数量、申请人和原因，同时最多保留一条 `PENDING_REVIEW` 申请，提交不修改运行态。

复核处理请求：

```json
{
  "reviewer": "checker-b",
  "reviewComment": "已核对渠道码表版本和差异"
}
```

批准阶段先锁复核申请、再锁运行态映射，重新验证候选 SHA-256、基线、渠道和所有行；同人复核、重复处理、摘要不一致、基线漂移或候选失效时拒绝。批准时映射写入、导入审计批次和复核完成同事务提交；驳回只完成复核，二者都会释放唯一待办占位。列表返回 `requestNo / sourceSha256 / baselineFingerprint / totalCount / createCount / updateCount / unchangedCount / requester / requestReason / reviewStatus / reviewer / reviewComment / appliedBatchNo / createdAt / reviewedAt / identityTrustLevel`。

单次最多 `500` 行、`512 KB`；未出现在文件中的映射保持不变，不自动归档。CSV 导入由 V1.4 复核链路治理，单条启停由 V1.5 复核链路治理。两者的 `requester / reviewer` 均是自声明，`identityTrustLevel` 固定为 `SELF_DECLARED`，不代表可信身份、正式审批或不可抵赖签字。

支付网关完整配置复核：

```http
POST /api/payment-config/gateways/toggle
GET  /api/payment-config/gateways/reviews
POST /api/payment-config/gateways/reviews
POST /api/payment-config/gateways/reviews/{requestNo}/approve
POST /api/payment-config/gateways/reviews/{requestNo}/reject
GET  /api/payment-config/gateways/{gatewayCode}/versions
POST /api/payment-config/gateways/{gatewayCode}/rollback
```

版本历史按 `versionNo` 倒序返回完整网关业务配置、`changeType`、`sourceVersionNo`、变更原因和创建时间。回滚请求为 `versionNo / requester / changeReason`；只生成 `ROLLBACK` 候选，批准后才恢复配置并追加 `REVIEW_ROLLBACK` 新版本。非 CREATE 候选批准时同时复验 `baseFingerprint` 与 `baseVersionNo`。

提交请求示例：

```json
{
  "gatewayCode": "GATEWAY_WX_ACQ",
  "enabled": false,
  "requester": "maker-a",
  "changeReason": "维护窗口内暂停微信收单网关"
}
```

提交阶段先锁同网关唯一待办占位、再锁网关运行配置；网关不存在、目标状态未变化或已有待办时拒绝。合法候选保存完整网关配置基线指纹、目标状态、申请人和原因，提交不修改运行态。

新增或编辑完整候选请求示例：

```json
{
  "requester": "maker-a",
  "candidate": {
    "gatewayCode": "GATEWAY_WX_ACQ",
    "gatewayName": "微信收单网关",
    "accessMode": "直连",
    "channelScope": "wx_h5",
    "environmentScope": "生产+预发+沙箱",
    "apiBaseUrl": "https://gateway.housekeeping.local/wx/acquire",
    "protocolType": "HTTP+JSON",
    "signAlgorithm": "HMAC-SHA256",
    "certificateAlias": "wx-mch-cert-v2026.09",
    "certificateStatus": "VALID",
    "releaseStage": "灰度 30%",
    "grayStrategy": "按商户分组灰度至核心城市订单",
    "callbackWhitelist": "101.1.0.0/16,101.2.0.0/16",
    "adapterRegistry": "submit=wechat-h5,query=wechat-query,refund=wechat-refund",
    "timeoutMs": 3000,
    "retryPolicy": "失败重试2次+超时查单",
    "enabled": true,
    "priority": 1,
    "changeReason": "更新网关证书档案与接入参数"
  }
}
```

完整候选的网关编码统一转为大写，基础地址必须是带主机的有效 HTTPS 地址，超时时间必须大于 `0`，优先级不得小于 `0`；证书状态仅支持 `VALID / EXPIRING_SOON / EXPIRED / ARCHIVED`，展示样式由服务端映射。CREATE 的基线为空，UPDATE 保存提交时完整运行配置指纹，二者与 STATUS_CHANGE 共用按网关唯一待办。

复核处理请求：

```json
{
  "reviewer": "checker-b",
  "reviewComment": "已核对维护窗口和影响范围"
}
```

批准阶段先锁复核申请、再锁网关运行配置并重新校验完整候选。CREATE 要求编码仍未占用，UPDATE/STATUS_CHANGE 要求网关存在且完整基线未漂移；同人声明、重复处理、候选非法或基线失效时拒绝。新增或完整更新与复核完成处于同一事务；驳回只完成复核。列表返回 `operationType`、全部非敏感候选字段、`baseFingerprint / candidateStatus / requester / requestReason / reviewStatus / reviewer / reviewComment / createdAt / reviewedAt / identityTrustLevel`。

支付控制策略完整候选复核：

```http
POST /api/payment-config/control-policies/toggle
GET  /api/payment-config/control-policies/reviews
POST /api/payment-config/control-policies/reviews
POST /api/payment-config/control-policies/reviews/{requestNo}/approve
POST /api/payment-config/control-policies/reviews/{requestNo}/reject
GET  /api/payment-config/control-policies/{sourceAppId}/versions
POST /api/payment-config/control-policies/{sourceAppId}/rollback
```

执行支付控制策略自检：

```http
POST /api/payment-config/control-policies/self-check
```

控制策略启停候选请求示例：

```json
{
  "sourceAppId": "housekeeping-app-web",
  "enabled": false,
  "requester": "maker-a",
  "changeReason": "暂停来源应用支付准入"
}
```

新增或编辑完整候选通过 `requester + candidate` 提交；candidate 包含来源应用标识/名称、支付方式/渠道/商户授权清单、两级正数限流、令牌鉴权开关、可选访问令牌、严格模式、启停状态和变更原因。新增且开启令牌鉴权时令牌必填；编辑留空继承当前令牌。管理总览和复核列表只返回 `accessTokenConfigured`，不返回 `accessTokenValue`。批准后自检状态重置为 `WARN`，严格模式必须重新自检通过后才能继续提交支付。

版本历史返回完整非敏感策略快照、`versionNo / changeType / sourceVersionNo / changeReason / createdAt` 和 `accessTokenConfigured`，不返回令牌原文。回滚请求包含 `versionNo / requester / changeReason`，生成 `ROLLBACK` 候选并记录提交时完整指纹和版本号；批准同时复验两类基线，恢复完整策略、重置自检为 `WARN` 并追加 `REVIEW_ROLLBACK` 新版本。

异常告警值班路由完整候选复核：

```http
POST /api/payment-config/issue-duty-rosters
PUT  /api/payment-config/issue-duty-rosters/{rosterCode}
POST /api/payment-config/issue-duty-rosters/toggle
GET  /api/payment-config/issue-duty-rosters/reviews
POST /api/payment-config/issue-duty-rosters/reviews/{requestNo}/approve
POST /api/payment-config/issue-duty-rosters/reviews/{requestNo}/reject
```

新增和编辑请求为完整值班路由候选，并附带 `requester / changeReason`；启停请求包含 `rosterCode / enabled / requester / changeReason`。编码统一大写，三类操作共享同一路由唯一待办。候选覆盖异常类型、严重等级、责任组、接收人、通知通道、升级配置、班次小时、星期范围、日期策略和状态。

批准或驳回请求包含 `reviewer / reviewComment`。批准先锁申请，再锁运行配置并复验存在性和完整基线；CREATE 编码占用、UPDATE/STATUS_CHANGE 基线漂移、同人声明和重复处理均拒绝。只有批准才在同一事务新增或完整更新运行态；驳回仅完成申请。`identityTrustLevel=SELF_DECLARED` 不代表可信身份、正式审批、真实排班或通知送达。

说明：

1. 启停候选请求的 `enabled=true` 表示目标状态 `ENABLED`。
2. 启停候选请求的 `enabled=false` 表示目标状态 `DISABLED`。
3. `GET /api/payment-config/export` 支持 `section` 参数，当前支持 `ALL / CHANNELS / ROUTE_RULES / PROTOCOLS / RETURN_CODES / GATEWAYS / CONTROL_POLICIES / ALERT_PROVIDERS / ISSUE_DUTY_ROSTERS`，用于页面按分区导出治理快照。
4. 协议配置当前已支持运营查看、协议种类字典、协议模板编码/名称维护、签约要素配置、协议正文编辑、电子签章服务商维护、不可变版本和自声明 maker-checker；新增、编辑、启停与回滚都先提交完整候选，批准前运行态不变。该能力不等于可信审批、正式法律签署或电子签章联调。
5. 返回码映射启停候选使用 `channelCode / channelReturnCode / enabled / requester / changeReason`，不再使用通用 `configCode / subCode` 直接写入请求。
6. 返回码映射当前已支持运营查看、人工介入判断、映射版本和归档状态展示，并已进入渠道提交响应留痕与主动查单详情；CSV 批量导入具备 V1.4 复核与审计，单条启停具备 V1.5 独立唯一待办、完整基线指纹和自声明异人复核。后续继续补可信身份权限、真实渠道码表校验和受控自动归档。
7. 支付网关新增、编辑、启停和历史回滚已统一纳入 V1.4 完整候选复核、不可变版本和指纹/版本双基线，关闭已知直接运行态写入入口；`requester / reviewer` 仍是自声明，`identityTrustLevel` 固定为 `SELF_DECLARED`，不代表可信身份、正式审批或不可抵赖签字。候选中的证书别名和状态只是治理台账字段，证书上传/轮换动作、真实灰度发布编排、渠道探活和真实网关适配仍未落地。
8. 支付渠道当前已支持商户应用标识、证书档案、验签时间窗、退款时效和风控标签展示；新增、编辑、启停和历史回滚已统一纳入 V1.4 完整候选复核、完整指纹与版本双基线和不可变版本。管理总览、复核列表和版本历史均不返回回调密钥或公钥原文；后续继续补可信身份权限、专用密钥轮换、证书上传、多商户参数模板和真实探活治理。
9. 支付控制策略新增、编辑、启停和历史回滚已统一纳入 V1.7 完整候选复核与不可变版本；来源应用编码统一小写，同应用唯一待办，批准前重验完整指纹和版本基线。自检仍使用 `configCode` 传来源应用标识并回写 `PASS/WARN/FAIL`；自动/人工自检属于系统运行状态，不进入配置复核。管理总览、复核列表和版本历史只展示令牌是否配置，人员仍为 `SELF_DECLARED`。
10. 路由规则新增、编辑、启停和回滚均提交候选快照进入自声明 maker-checker；旧的直接运行态写入路径已关闭，复核通过前运行态不变。规则编码编辑时不可变，目标与兜底渠道必须存在且不能相同，非空兜底渠道的支付方式须与目标一致。
11. 异常告警值班路由新增、编辑和启停均进入 V1.1 完整候选复核；允许跨日班次，星期范围规范化，升级超时为 `5-1440` 分钟，日期策略只支持 `ALL_DAYS / WORKDAY_ONLY / NON_WORKDAY_ONLY`。

### 12.3 路由规则维护与校验

新增和编辑统一提交接口：`POST /api/payment-config/route-rules/reviews`

请求示例：

```json
{
  "requester": "maker-a",
  "candidate": {
    "ruleCode": "RULE_HOME_APP",
    "ruleName": "家政 App 小额路由",
    "matchScene": "HOME_CLEAN/APP",
    "matchExpression": "terminal IN (H5, APP) AND amount<=500",
    "targetChannelCode": "wx_h5",
    "fallbackChannelCode": "",
    "rolloutPercentage": 30,
    "rolloutSalt": "home-app-v1",
    "priority": 10,
    "enabled": true,
    "changeReason": "增加 App 小额支付专用规则"
  }
}
```

保存约束：

1. 新增时 `ruleCode` 必填且不能与现有规则重复；编辑时按候选快照中的 `ruleCode` 绑定当前规则并保持编码不变。
2. 名称、匹配场景、表达式、目标渠道和 `rolloutSalt` 必填，优先级必须大于等于 `0`；盐值最长 `64` 个字符。
3. 目标渠道和非空兜底渠道必须存在于支付渠道配置中，且二者不能相同；非空兜底须与目标支付方式一致（`银行卡`、`银行转账` 视为同一方式）。支付方式缺失或不一致时在写入候选前拒绝，复核批准、历史版本回滚及启用复核也重新校验；禁用旧规则不受跨方式存量限制。表单仅展示同方式可选渠道，旧跨方式值保留只读提示，不会自动改写。管理端规则列表及待复核候选在渠道总览快照可用时提示跨方式、同渠道或渠道缺失项；提示仅用于人工盘点，不替代后端复验，也不推断生产路由流量。
4. 表达式必须通过 V1.3 语法与字段校验；非法规则在写库前拒绝，不进入支付提交链路。
5. `requester` 和 `changeReason` 必填；同一规则已有 `PENDING_REVIEW` 时拒绝重复提交。
6. 提交只写入复核申请，不更新 `t_payment_route_rule_config`，也不生成运行版本。
7. `rolloutPercentage` 取 `0..100` 的整数；未传时按 `100` 兼容。`0` 表示规则保留但不承接流量，`100` 表示全量。

校验与模拟接口：`POST /api/payment-config/route-rules/validate`

请求示例：

```json
{
  "matchExpression": "terminal IN (H5, APP) AND amount<=500",
  "paymentMethod": "微信支付",
  "requestedChannelCode": "wx_h5",
  "payScene": "HOME_CLEAN",
  "terminal": "APP",
  "amount": 168.00,
  "customerName": "个人客户"
}
```

返回示例：

```json
{
  "valid": true,
  "matched": true,
  "message": "表达式合法，样例上下文命中"
}
```

说明：该接口只执行语法、字段和样例上下文求值，不写入规则，也不代表真实支付流量已经命中该规则。

### 12.4 路由规则版本与回滚

版本列表接口：`GET /api/payment-config/route-rules/{ruleCode}/versions`

版本按 `versionNo` 倒序返回，包含表达式、渠道、`rolloutPercentage`、`rolloutSalt` 等完整规则快照，以及 `changeType`、`sourceVersionNo`、`changeReason` 和 `createdAt`。变更类型支持：

1. `CREATE`：通过配置接口新建规则。
2. `BASELINE`：存量规则首次变更前自动补录的基线。
3. `UPDATE`：编辑规则字段。
4. `STATUS_CHANGE`：历史直接启用或停用规则形成的版本。
5. `ROLLBACK`：历史直接恢复某一版本形成的新版本。
6. `REVIEW_RELEASE`：候选快照通过轻量复核后形成的新版本；只表示代码内版本来源，不等同生产正式放行。
7. `REVIEW_STATUS_CHANGE`：启停候选通过复核后形成的新版本。
8. `REVIEW_ROLLBACK`：回滚候选通过复核后形成的新版本，`sourceVersionNo` 指向目标历史版本。

回滚接口：`POST /api/payment-config/route-rules/{ruleCode}/rollback`

请求示例：

```json
{
  "versionNo": 2,
  "changeReason": "恢复大促前稳定配置",
  "requester": "maker-a"
}
```

回滚约束：

1. 规则和目标版本必须存在，版本号必须大于 `0`。
2. 提交回滚时重新校验历史快照中的表达式、目标渠道、兜底渠道、灰度比例、分桶盐值和优先级，避免失效依赖形成候选。
3. 申请保存当前 `baseVersionNo` 和目标 `sourceVersionNo`；复核通过前锁定规则并重验基线，期间出现新版本时拒绝旧申请。
4. 复核通过后才恢复配置并追加 `REVIEW_ROLLBACK` 快照；驳回或未处理不改变运行态，也不删除或改写历史版本。
5. `requester` 和 `changeReason` 必填，分别最长 `64` 和 `255` 个字符；自声明身份不应被解释为正式审批或生产发布证据。

### 12.5 路由规则冲突检测

接口：`GET /api/payment-config/route-rules/conflicts`

返回示例：

```json
[
  {
    "conflictType": "ROUTE_DIVERGENCE",
    "severity": "BLOCKER",
    "severityType": "danger",
    "ruleCode": "RULE_HOME_H5_A",
    "ruleName": "家政 H5 路由 A",
    "conflictingRuleCode": "RULE_HOME_H5_B",
    "conflictingRuleName": "家政 H5 路由 B",
    "priority": 10,
    "conflictingPriority": 10,
    "overlappingScene": "h5",
    "message": "同场景、同优先级和同表达式指向不同渠道，运行结果依赖数据库记录顺序"
  }
]
```

检测口径：

1. 独立检测接口只扫描当前状态为 `ENABLED` 且 `rolloutPercentage > 0` 的规则，不改变规则状态或写入版本快照；复核发布会把候选加入当前规则集合并复用阻断级检查。
2. 表达式先做空白与关键字大小写规范化，仅对规范化后完全相同的表达式判断重复、路由分歧和优先级遮蔽。
3. 匹配场景以 `/` 分隔并按 OR 关系求交集与包含关系；`mobile / mobile_web / H5` 归一为 `h5`，`web / pc_web / PC` 归一为 `pc`，`app_native / APP` 归一为 `app`。空场景按全部场景处理。
4. `INVALID_EXPRESSION / ROUTE_DIVERGENCE` 为 `BLOCKER`；`DUPLICATE_RULE / OVERLAPPING_DUPLICATE / SHADOWED_RULE / PARTIAL_SHADOW` 为 `WARNING`。
5. 场景有交集且优先级相同、但表达式不同的规则返回 `PRIORITY_COLLISION / REVIEW`，只表示需要人工确认表达式是否互斥，不等同于已证明冲突。
6. 当前实现是结构化静态检测，不覆盖任意不同布尔表达式之间的完整语义重叠或可满足性证明；检测结果也不代表双人复核、灰度审批或真实渠道验证已经完成。

### 12.6 路由规则轻量复核

查询接口：`GET /api/payment-config/route-rules/reviews`

通过接口：`POST /api/payment-config/route-rules/reviews/{requestNo}/approve`

驳回接口：`POST /api/payment-config/route-rules/reviews/{requestNo}/reject`

处理请求示例：

```json
{
  "reviewer": "reviewer-b",
  "reviewComment": "配置与风险检查通过"
}
```

处理约束：

1. `requester` 与 `reviewer` 都是 `SELF_DECLARED` 自声明文本，系统仅校验两者不相同；当前未接入可信登录身份、权限或电子签名，不得解释为正式签字审批。
2. 复核只处理 `PENDING_REVIEW` 申请。通过前重新校验当前基线版本、候选表达式、渠道有效性，以及候选加入当前规则集合后是否存在涉及候选的 `BLOCKER`。
3. `CREATE / UPDATE / STATUS_CHANGE / ROLLBACK` 均保存完整候选快照；回滚申请额外保存 `sourceVersionNo`。同一规则同时只允许一条待复核申请。
4. 通过后才新增或更新运行态规则，并按操作追加 `REVIEW_RELEASE / REVIEW_STATUS_CHANGE / REVIEW_ROLLBACK` 版本；驳回只更新申请状态，不改变运行态。
5. 该能力仍是基于自声明人员文本的轻量 maker-checker。代码内稳定百分比桶已实现，但不代表生产灰度审批、真实渠道验证或发布后稳定观察已经完成。

### 12.7 路由规则稳定百分比灰度

1. 规则先匹配场景和表达式，再以 `CRC32(ruleCode + ":" + rolloutSalt + ":" + paymentOrderId) % 100` 计算稳定桶；仅当 `bucket < rolloutPercentage` 时进入当前规则。
2. 未进入当前灰度桶时继续评估后续规则，不使用当前规则 fallback；只有进入桶后目标渠道不可用，才尝试当前规则的兜底渠道。
3. 部分灰度规则缺少 `paymentOrderId` 稳定键时按不命中跳过，避免随机漂移；100% 规则保持既有行为。
4. 相同规则编码、盐值和支付单号的桶位稳定。调整比例只改变阈值，修改盐值会重排后续支付单桶位，必须作为完整候选快照复核并进入版本历史。
5. 本能力是代码内路由执行语义，不自动完成生产发布审批、流量观测、渠道联调或回滚决策。

### 12.8 支付协议版本与 maker-checker 复核

新增和编辑候选：`POST /api/payment-config/protocols/reviews`

```json
{
  "requester": "maker-a",
  "candidate": {
    "protocolCode": "PROTO_HOUSEKEEPING_SIGN_V2",
    "protocolName": "家政服务签约协议",
    "protocolType": "SERVICE_SIGN",
    "protocolTypeName": "服务签约协议",
    "templateCode": "TPL_HOME_SIGN",
    "templateName": "家政签约模板",
    "templateVersion": "2.0",
    "signMode": "ONLINE",
    "signElementSpec": "姓名、手机号、订单号",
    "eSignatureProvider": "DEMO_ESIGN",
    "sceneScope": "HOME_CLEAN",
    "channelScope": "ALL",
    "merchantAckRequired": "YES",
    "riskControlTag": "STANDARD",
    "protocolBody": "协议正文候选",
    "priority": 10,
    "enabled": true,
    "changeReason": "更新签约条款"
  }
}
```

查询申请：`GET /api/payment-config/protocols/reviews`

通过申请：`POST /api/payment-config/protocols/reviews/{requestNo}/approve`

驳回申请：`POST /api/payment-config/protocols/reviews/{requestNo}/reject`

```json
{
  "reviewer": "reviewer-b",
  "reviewComment": "配置复核通过"
}
```

查询历史：`GET /api/payment-config/protocols/{protocolCode}/versions`

启停候选：`POST /api/payment-config/protocols/toggle`

回滚候选：`POST /api/payment-config/protocols/{protocolCode}/rollback`

```json
{
  "versionNo": 1,
  "requester": "maker-a",
  "changeReason": "恢复已完成条款核对的协议版本"
}
```

处理约束：

1. `CREATE / UPDATE / STATUS_CHANGE / ROLLBACK` 都将完整候选写入 `t_payment_protocol_review`；提交与驳回不修改运行态，也不生成运行版本。同一协议同时最多一条 `PENDING_REVIEW` 申请。
2. 新建候选的 `baseVersionNo` 为空；存量协议提交时锁定当前协议、按需补录 `BASELINE`，并保存最新版本号。回滚候选额外保存 `sourceVersionNo`，目标历史版本必须存在。
3. 复核人必须与申请人不同。批准时重新锁定申请和协议并复验最新版本；存量基线漂移或新建编码已被占用时拒绝发布。申请完成后释放唯一待办占位。
4. 批准后才新增或更新 `t_payment_protocol_config`，并按操作追加 `REVIEW_RELEASE / REVIEW_STATUS_CHANGE / REVIEW_ROLLBACK` 不可变版本；历史版本只增不改。
5. `requester` 与 `reviewer` 的身份可信度固定为 `SELF_DECLARED`。该异人约束不能证明可信身份、法务批准、正式签字、协议生效或真实电子签章回执。

## 13. 支付监控分析接口

### 13.1 查询监控总览

接口：`GET /api/payment-monitor/overview`

返回内容：

1. `summary`：支付监控摘要卡片数据，包含支付单总量、成功笔数、成功率、成功金额、待回调支付单量、退款失败笔数、停用渠道数和异常告警数。
2. `trends`：最近 7 天支付趋势，包含日期、总单量、成功单量和成功金额。
3. `channelMetrics`：按渠道与支付方式统计的总单量、成功率、成功金额和待处理笔数，并补充 `riskLevel`、`riskLevelType`、`riskHint` 便于前端直接展示风险标签和说明。
4. `alerts`：待处理异常告警列表，当前包括回调待收口、退款失败、渠道停用三类，并补充 `suggestedAction` 和 `actionRoute` 便于前端一键跳转到排障页。
5. `metricDefinitions`：当前监控页使用的指标字典，包含 `metricCode`、`metricName`、`definition`、`unit`、`defaultThreshold`、`alertLevel`、`owner` 和 `actionRoute`；`defaultThreshold` 是本地默认运营门槛，不等同于生产 SLO 或正式审批结果。

返回示例：

```json
{
  "summary": {
    "totalCount": 268,
    "successCount": 247,
    "successRate": "92.16%",
    "successAmount": "¥18,932.00",
    "pendingPaymentCount": 12,
    "closedPaymentCount": 5,
    "failedRefundCount": 3,
    "disabledChannelCount": 1,
    "alertCount": 3
  },
  "trends": [
    {
      "statDate": "2026-07-20",
      "totalCount": 38,
      "successCount": 35,
      "successAmount": "¥2,866.00"
    }
  ],
  "channelMetrics": [
    {
      "channelCode": "WX_H5",
      "paymentMethod": "WECHAT_H5",
      "totalCount": 72,
      "successCount": 65,
      "successRate": "90.28%",
      "successAmount": "¥5,880.00",
      "pendingCount": 4,
      "riskLevel": "中风险",
      "riskLevelType": "warn",
      "riskHint": "存在 4 笔待回调支付，建议优先查单"
    }
  ],
  "alerts": [
    {
      "alertType": "WAIT_CALLBACK",
      "alertTitle": "待回调支付积压",
      "alertMessage": "存在支付结果未收口的交易",
      "alertLevel": "高",
      "alertLevelType": "danger",
      "affectedCount": 12,
      "suggestedAction": "进入支付交易异常中心，优先核对待回调支付单、回调日志和查单结果",
      "actionRoute": "/payment-issues?issueType=待回调未收口"
    }
  ],
  "metricDefinitions": [
    {
      "metricCode": "payment_success_rate",
      "metricName": "支付成功率",
      "definition": "成功支付单量 / 支付单总量",
      "unit": "%",
      "defaultThreshold": ">= 95%",
      "alertLevel": "P1",
      "owner": "支付运营",
      "actionRoute": "/payments"
    }
  ]
}
```

### 13.2 查询支付交易异常中心

接口：`GET /api/payment-issues`

请求参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `paymentOrderId` | `string` | 支付单号，模糊查询 |
| `orderNo` | `string` | 订单号，模糊查询 |
| `issueType` | `string` | 异常类型，默认 `全部` |
| `severity` | `string` | 严重等级，默认 `全部` |
| `channelCode` | `string` | 渠道编码，模糊查询 |
| `paymentMethod` | `string` | 支付方式，默认 `全部` |
| `pageNo` | `int` | 页码，默认 `1` |
| `pageSize` | `int` | 每页条数，默认 `20`，最大 `100` |

返回内容：

1. 聚合 `待回调未收口`、`回调处理待跟进`、`下游事件发布失败`、`命中停用渠道` 四类异常。
2. 每条异常返回支付单、订单、客户、支付方式、渠道、异常类型、严重等级、支付状态、异常摘要、根因提示、建议动作、推荐跳转路由、处理状态、当前处理人、责任组、责任组处理提示、SLA 状态、升级状态、升级建议和最近处理动作。
3. 前端通过 `recommendedRoute` 直接进入支付单详情、支付处理日志、支付事件出站或支付配置页。

返回示例：

```json
{
  "items": [
    {
      "issueNo": "ISSUE-WAIT-PAY202607190002",
      "paymentOrderId": "PAY202607190002",
      "orderNo": "ORD202607190002",
      "customerName": "王先生",
      "paymentMethod": "支付宝",
      "channelCode": "alipay_h5",
      "issueType": "待回调未收口",
      "issueTypeTag": "danger",
      "severity": "P1",
      "severityType": "danger",
      "paymentStatus": "WAIT_CALLBACK",
      "paymentStatusType": "warn",
      "issueSummary": "支付单仍处于待回调状态，最新回调结果=待处理",
      "rootCauseHint": "最近支付尝试=等待回调；建议优先主动查单并核对回调验签与状态收口。",
      "recommendedAction": "查看支付单并核对回调、日志和查单结果",
      "recommendedRoute": "/payments/PAY202607190002",
      "handlingStatus": "待分派",
      "handlingStatusType": "warn",
      "assignee": "未分派",
      "responsibilityGroup": "支付后端值班组",
      "responsibilityGroupType": "danger",
      "responsibilityHint": "优先核对查单、回调验签、幂等和状态机收口",
      "alertStatus": "已生成",
      "alertStatusType": "warn",
      "alertAckStatus": "待确认",
      "alertAckStatusType": "warn",
      "alertReceiver": "支付后端值班",
      "slaStatus": "已超时",
      "slaStatusType": "danger",
      "slaTimeLeft": "P1 SLA 30分钟，已耗时45分钟",
      "escalationStatus": "升级值班负责人",
      "escalationStatusType": "danger",
      "escalationSuggestion": "立即同步支付后端、渠道值班和运营负责人",
      "latestActionSummary": "暂无处理动作",
      "latestActionAt": null,
      "createdAt": "2026-07-19 10:03:01"
    }
  ],
  "total": 2,
  "pageNo": 1,
  "pageSize": 20
}
```

### 13.2.1 查询单条支付交易异常详情

接口：`GET /api/payment-issues/{issueNo}`

1. `issueNo` 按单一路径段编码，服务端去除首尾空白后校验必填、最长 `128` 个字符且不得包含控制字符；非法请求沿用 `PAYMENT-1036`，不存在返回 `PAYMENT-1063 / 支付交易异常不存在`。
2. 响应字段与异常列表行保持同口径，包含异常编号、支付单、订单、客户、支付方式、渠道、异常类型、严重等级、支付状态、异常摘要、根因提示、建议动作、推荐跳转路由、处理状态、当前处理人、责任组、告警派发摘要、SLA、升级建议、最近处理动作和异常时间。
3. 详情 SQL 复用异常列表 `issueUnion` 口径，并补齐最新处理动作、站内告警、外部供应商告警和通道派发摘要；列表与详情不得产生不同责任组或推荐入口。
4. 客户名称由服务层输出脱敏副本，不改写 Mapper 原始 DTO 或数据库事实。
5. 接口只读，不执行批量处理、不确认告警回执、不重发事件、不修改支付状态，也不代表真实告警送达、渠道事实、资金一致或生产放行。

### 13.3 查询支付交易异常责任组统计

接口：`GET /api/payment-issues/responsibility-summary`

请求参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `paymentOrderId` | `string` | 支付单号，模糊查询 |
| `orderNo` | `string` | 订单号，模糊查询 |
| `issueType` | `string` | 异常类型，默认 `全部` |
| `severity` | `string` | 严重等级，默认 `全部` |
| `channelCode` | `string` | 渠道编码，模糊查询 |
| `paymentMethod` | `string` | 支付方式，默认 `全部` |

返回内容：

1. 统计口径与 `GET /api/payment-issues` 使用同一组异常来源和筛选条件。
2. 返回每个责任组的异常总数、SLA 超时数、P1 数、P2 数和建议处理动作。
3. 该接口用于替代前端当前页聚合，运营翻页后责任组卡片仍保持当前筛选条件下的全量口径。

返回示例：

```json
[
  {
    "groupName": "支付后端值班组",
    "groupType": "danger",
    "totalCount": 8,
    "overdueCount": 3,
    "p1Count": 8,
    "p2Count": 0,
    "suggestedAction": "优先核对查单、回调验签、幂等和状态机收口"
  }
]
```

### 13.4 查询支付异常告警通知明细

接口：`GET /api/payment-issues/alerts`

请求参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `alertNo` | `string` | 告警编号，模糊查询 |
| `issueNo` | `string` | 异常编号，模糊查询 |
| `paymentOrderId` | `string` | 支付单号，模糊查询 |
| `alertChannel` | `string` | 告警通道，默认 `全部` |
| `alertStatus` | `string` | 告警状态，默认 `全部` |
| `ackStatus` | `string` | 回执状态，默认 `全部` |
| `providerDeliveryStatus` | `string` | 供应商投递状态，默认 `全部` |
| `pageNo` | `int` | 页码，默认 `1` |
| `pageSize` | `int` | 每页条数，默认 `20`，最大 `100` |

返回内容：

1. 返回告警编号、异常编号、支付单号、异常类型、严重等级、责任组、通道、接收人、告警状态、回执状态和创建时间。
2. 同时返回供应商编码、供应商名称、端点别名、模板编码、投递状态、投递说明、供应商回执号和原始回执快照。
3. 用于支撑运营、研发和测试统一导出异常通知快照，做离线排障、交班和审计留痕。

返回示例：

```json
{
  "items": [
    {
      "alertNo": "PIA-202608070001",
      "issueNo": "ISSUE-WAIT-PAY202608070001",
      "paymentOrderId": "PAY202608070001",
      "issueType": "待回调未收口",
      "severity": "P1",
      "responsibilityGroup": "支付后端值班组",
      "alertChannel": "IM",
      "receiver": "支付运营",
      "alertStatus": "已派发",
      "ackStatus": "待确认",
      "providerCode": "im_provider",
      "providerName": "企业IM",
      "endpointAlias": "主Webhook",
      "templateCode": "PAYMENT_ISSUE_NOTICE",
      "providerDeliveryStatus": "ACCEPTED",
      "providerDeliveryMessage": "accepted",
      "providerReceiptNo": "receipt-001",
      "providerReceiptSnapshot": "{\"status\":\"accepted\"}",
      "triggeredBy": "任务中心",
      "ackOperator": null,
      "ackAt": null,
      "createdAt": "2026-08-07 09:30:00"
    }
  ],
  "total": 1,
  "pageNo": 1,
  "pageSize": 20
}
```

### 13.5 导出支付异常告警通知明细

接口：`GET /api/payment-issues/alerts/export`

请求参数：

1. 与 `GET /api/payment-issues/alerts` 完全一致，但不需要分页参数。
2. 导出结果使用 UTF-8 CSV，文件名固定为 `payment-issue-alert-logs.csv`。
3. 导出字段覆盖告警基础信息、供应商信息、投递状态、确认人、确认时间、内容快照和回执快照。

### 13.6 批量处理支付交易异常

接口：`POST /api/payment-issues/actions`

请求示例：

```json
{
  "issueNos": ["ISSUE-WAIT-PAY202607190002"],
  "idempotencyKey": "issue-action-unique-attempt-001",
  "actionType": "分派处理人",
  "assignee": "后端值班",
  "operator": "支付运营",
  "remark": "请先主动查单并核对回调验签结果"
}
```

字段说明：

| 字段 | 是否必填 | 说明 |
| --- | --- | --- |
| `issueNos` | 是 | 待处理异常编号列表 |
| `idempotencyKey` | 新客户端应传 | 同一次批量动作重试必须复用，最长 128 字符；旧客户端省略时仍允许，但没有请求去重保障 |
| `actionType` | 是 | 支持 `分派处理人 / 标记跟进中 / 标记已处理 / 补充备注` |
| `assignee` | 是 | 当前处理人 |
| `operator` | 是 | 操作人；去除首尾空白后最长 `64` 个字符，不再回退为处理人 |
| `remark` | 否 | 处理备注，不传时默认 `批量处理` |

处理说明：

1. 后端会按 `issueNo` 回查当前聚合异常，异常不存在时拒绝处理，避免对已消失的异常写入误导性动作。
2. 处理动作会写入 `t_payment_issue_action_log`，不直接修改支付单状态，避免把运营排障动作和交易状态机混在一起。
3. 当 `actionType=标记已处理` 时，后端会同步确认该异常下仍处于 `待确认` 的告警回执。
4. 接口返回 `PageResultDTO<PaymentIssueRowDTO>`，其中 `items` 按请求顺序精确包含本次处理的全部异常（最多 `100` 条），`total/pageSize` 等于本次处理数、`pageNo=1`；不代表调用页面的筛选结果。前端仍应按原筛选条件另行刷新列表与责任组统计，不得用动作响应覆盖当前列表。

### 13.7 查询支付任务中心总览

接口：`GET /api/payment-task-center/overview`

返回内容：

1. `expiredPaymentCount`：待超时关单数量。
2. `pendingCallbackCount`：待收口支付中数量。
3. `failedEventCount`：失败事件数量。
4. `failedRefundCount`：失败退款数量。
5. `warningDayEndBatchCount`：日终告警批次数。
6. `focusAlerts`：重点任务告警，包含告警标题、告警等级、影响数量和建议跳转路由。
7. `overdueIssueCount`：超过 SLA 的支付交易异常数。
8. `recentTaskRuns`：最近 10 条任务执行日志。
9. `pendingQueryBacklogCount`：存在支付尝试、已达到首次主动补查等待阈值且仍为 `WAIT_CALLBACK` 的支付单数；预付单过期不排除已提交交易。
10. `duePendingQueryCount`：当前已达到指数退避时间、可由自动任务查询的支付单数。
11. `queryMonitorWindowHours`：查单监控回看窗口小时数。
12. `recentQueryCount`、`recentQueryFailureCount`、`recentQueryFailureRate`：窗口内查单总数、失败数和失败率。
13. `queryFailureCountThreshold`、`queryFailureRateThresholdPercent`、`queryFailureRateMinimumSampleCount`：查单失败 P1 告警阈值口径。
14. `queryTrends`：按小时聚合的查单总数、失败数和失败率。
15. `recentQueryRateLimitRejectionCount`：窗口内共享渠道配额耗尽导致的查单拒绝次数，不计入渠道失败率。
16. `queryRateLimitRejectionCountThreshold`：限流拒绝次数 P1 告警阈值。
17. `pendingIssueAlertDispatchCount`：待确认且状态为已生成、部分失败或派发失败的来源 `IN_APP_OUTBOX` 告警数。
18. `failedIssueAlertDispatchCount`：上述待派发告警中仍处于部分失败或派发失败的数量。
19. `oldestPendingIssueAlertDispatchMinutes`：最老一条待派发来源 outbox 的等待分钟数，无积压时为 `0`。
20. `issueAlertDispatchCountThreshold`、`issueAlertDispatchTimeoutMinutes`：待派发积压升级 P1 的数量与等待时长阈值。
21. `pendingIssueAlertReceiptCount`：供应商已受理、待确认且等待送达回执回查的外发告警数。
22. `oldestPendingIssueAlertReceiptMinutes`：最老一条待回查外发告警的等待分钟数，无积压时为 `0`。
23. `issueAlertReceiptCountThreshold`、`issueAlertReceiptTimeoutMinutes`：待回查积压升级 P1 的数量与等待时长阈值。

异常告警派发可观测性说明：待派发数量或最老等待时长达到配置阈值，或其中存在派发异常时升级为 P1；存在少量待派发但未命中上述条件时为 P2。待回查数量或最老等待时长达到配置阈值时升级为 P1，存在少量待回查但未命中阈值时为 P2。默认阈值分别为待派发 `10` 条/`15` 分钟、待回查 `10` 条/`30` 分钟。以上均为任务中心积压口径，不代表真实通知已经送达。

### 13.8 查询支付任务执行日志

接口：`GET /api/payment-task-center/task-runs`

查询参数：

| 参数 | 说明 |
| --- | --- |
| `taskCode` | 任务编码，支持精确匹配 |
| `runMode` | 运行方式，支持 `全部 / AUTO / MANUAL` |
| `taskStatus` | 任务状态，支持 `全部 / SUCCESS / WARNING` |
| `severityLevel` | 严重等级，支持 `全部 / P1 / P2 / P3` |
| `pageNo` | 页码，从 `1` 开始 |
| `pageSize` | 每页条数，最大 `100` |

业务说明：

1. 当前日志已统一落地自动调度和人工触发两类来源，便于区分 `AUTO / MANUAL`。
2. 每条日志返回严重等级、升级状态、建议动作和推荐路由，便于产品、研发、测试共用同一运维口径。
3. 严重等级与升级状态不再按“是否失败”简单二分，而是按任务类型、失败笔数和处理规模综合判定：
   - `PAYMENT_EXPIRE_CLOSE`：失败 `>= 5` 或处理量 `>= 30` 判定 `P1 / 升级值班负责人`；失败 `1-4` 或处理量 `10-29` 判定 `P2 / 纳入当班跟进`
   - `PAYMENT_PENDING_QUERY`：查询失败 `>= 3` 判定 `P1 / 升级值班负责人`；存在查询失败或渠道仍处理中判定 `P2 / 纳入当班跟进`
   - `PAYMENT_QUERY_MONITOR_ALERT`：失败笔数、失败率或限流拒绝达到配置阈值时判定 `P1 / 升级值班负责人`；任务生成告警 outbox，不修改支付交易状态
   - `PAYMENT_EVENT_RETRY`：失败 `>= 3` 或处理量 `>= 10` 判定 `P1 / 升级值班负责人`；失败 `1-2` 或存在成功重发则判定 `P2 / 纳入当班跟进`
   - `REFUND_FAIL_RETRY`：失败 `>= 2` 或处理量 `>= 8` 判定 `P1 / 升级值班负责人`；失败 `1` 笔或存在成功重试则判定 `P2 / 纳入当班跟进`
4. 当前待回调主动查单、超时关单、失败事件重发、失败退款重试、异常 SLA 升级巡检都由该页统一留痕，并已接入后台自动补偿调度；待回调查单只有在真实网关和补偿开关同时启用时才自动运行。

### 13.7.1 手动执行待回调支付主动查单

接口：`POST /api/payment-task-center/query-pending-payments`

处理规则：

1. 人工补跑选择存在支付尝试、达到最小等待时长且仍为 `WAIT_CALLBACK` 的支付单，收银台过期后仍可补查；人工模式不受自动重试退避窗口限制，便于运营立即复核。
2. 单次处理数量受批次上限约束，按最新支付尝试时间从早到晚执行。
3. 每笔复用 `POST /api/payments/query` 的幂等收口逻辑；渠道成功会收口支付主链路，渠道处理中计入告警，网关异常计入失败。
4. 单笔失败与其他支付单隔离，任务摘要最多记录三个失败支付单号；自动任务使用 `PAYMENT_PENDING_QUERY` 租约避免多实例重复执行。
5. 自动调度要求 `HSP_PAYMENT_CHANNEL_QUERY_GATEWAY_ENABLED=true` 且 `HSP_PAYMENT_CHANNEL_QUERY_COMPENSATION_ENABLED=true`，任一关闭均不执行自动查单。
6. 自动任务将渠道业务结果和网关查询失败都计入历史次数，并按 `基础间隔 × 退避系数^(历史次数-1)` 计算单笔支付单的下一次查询间隔，达到最大间隔后封顶。
7. 人工与自动候选均先按渠道内创建时间排序并限制每渠道单批配额，再应用全局批次上限，避免单一大渠道挤占整轮查询资源。
8. 任务中心总览按配置窗口聚合 `已查单 / 查单失败` 查询事实，返回窗口查单总数、失败数、失败率和逐小时趋势。
9. “主动查单失败趋势”在失败笔数达到阈值，或样本量达到最低要求且失败率达到阈值时判定 `P1`；存在失败但未达阈值判定 `P2`，无失败判定 `P3`。
10. `PAYMENT-1027` 限流拒绝发生在渠道调用和查询事实写入之前，不混入渠道失败率；每次拒绝单独写入 `t_payment_channel_query_rate_limit_log`，任务中心按监控窗口展示拒绝次数并按独立阈值生成告警。

补偿配置：

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `HSP_PAYMENT_CHANNEL_QUERY_COMPENSATION_ENABLED` | `false` | 自动查单补偿开关 |
| `HSP_PAYMENT_CHANNEL_QUERY_COMPENSATION_DELAY_MS` | `60000` | 自动任务固定间隔 |
| `HSP_PAYMENT_CHANNEL_QUERY_COMPENSATION_MIN_AGE_SECONDS` | `30` | 支付进入待回调后的首次补查等待时间 |
| `HSP_PAYMENT_CHANNEL_QUERY_COMPENSATION_RETRY_INTERVAL_SECONDS` | `60` | 自动查单的基础重试间隔 |
| `HSP_PAYMENT_CHANNEL_QUERY_COMPENSATION_RETRY_BACKOFF_FACTOR` | `2` | 自动查单的指数退避系数，最小为 `1` |
| `HSP_PAYMENT_CHANNEL_QUERY_COMPENSATION_MAX_RETRY_INTERVAL_SECONDS` | `900` | 单笔支付单自动查单的最大退避间隔，不能小于基础间隔 |
| `HSP_PAYMENT_CHANNEL_QUERY_COMPENSATION_PER_CHANNEL_BATCH_SIZE` | `20` | 单轮任务对单一渠道最多查询的支付单数量 |
| `HSP_PAYMENT_CHANNEL_QUERY_COMPENSATION_BATCH_SIZE` | `100` | 单批最大支付单数量 |

监控配置：

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `HSP_PAYMENT_CHANNEL_QUERY_MONITOR_LOOKBACK_HOURS` | `24` | 查单趋势回看小时数，服务内限制为 `1-168` |
| `HSP_PAYMENT_CHANNEL_QUERY_MONITOR_FAILURE_COUNT_THRESHOLD` | `5` | 触发 P1 的窗口失败笔数阈值，最小为 `1` |
| `HSP_PAYMENT_CHANNEL_QUERY_MONITOR_FAILURE_RATE_THRESHOLD_PERCENT` | `30` | 触发 P1 的窗口失败率阈值，限制为 `1%-100%` |
| `HSP_PAYMENT_CHANNEL_QUERY_MONITOR_FAILURE_RATE_MINIMUM_SAMPLE_COUNT` | `10` | 应用失败率阈值前要求的最小查单样本数，最小为 `1` |
| `HSP_PAYMENT_CHANNEL_QUERY_MONITOR_RATE_LIMIT_REJECTION_COUNT_THRESHOLD` | `10` | 触发 P1 的窗口限流拒绝次数阈值，最小为 `1` |
| `HSP_PAYMENT_CHANNEL_QUERY_MONITOR_ALERT_COOLDOWN_MINUTES` | `60` | 同类未确认 outbox 告警的冷却去重窗口，最小为 `1` 分钟 |
| `HSP_PAYMENT_CHANNEL_QUERY_MONITOR_ALERT_DELAY_MS` | `300000` | 自动生成查单监控告警的固定扫描间隔 |
| `HSP_PAYMENT_ISSUE_ALERT_AUTO_DISPATCH_ENABLED` | `false` | outbox 告警自动派发开关；确认接收范围和通知供应商配置后显式启用 |
| `HSP_PAYMENT_ISSUE_ALERT_AUTO_DISPATCH_DELAY_MS` | `60000` | 自动派发待处理 outbox 的固定扫描间隔 |
| `HSP_PAYMENT_ISSUE_ALERT_DISPATCH_COUNT_THRESHOLD` | `10` | 待派发来源 outbox 升级 P1 的数量阈值，最小为 `1` |
| `HSP_PAYMENT_ISSUE_ALERT_DISPATCH_TIMEOUT_MINUTES` | `15` | 单条待派发来源 outbox 升级 P1 的等待分钟阈值，最小为 `1` |
| `HSP_PAYMENT_ISSUE_ALERT_RECEIPT_COUNT_THRESHOLD` | `10` | 待回查外发告警升级 P1 的数量阈值，最小为 `1` |
| `HSP_PAYMENT_ISSUE_ALERT_RECEIPT_TIMEOUT_MINUTES` | `30` | 单条待回查外发告警升级 P1 的等待分钟阈值，最小为 `1` |

### 13.7.2 生成主动查单监控告警

接口：`POST /api/payment-task-center/generate-query-monitor-alerts`

处理规则：

1. 任务编码为 `PAYMENT_QUERY_MONITOR_ALERT`，人工入口与自动调度共用该任务租约，避免两类入口并行生成同类告警。
2. 查单失败笔数达到阈值，或样本数达到最低要求且失败率达到阈值时，生成 `主动查单失败趋势` P1 告警。
3. 限流拒绝次数达到独立阈值时，生成 `主动查单限流拒绝` P1 告警；拒绝事实仍不计入渠道查询失败率。
4. 告警写入 `t_payment_issue_alert_log`，来源通道为 `IN_APP_OUTBOX`，责任组为 `支付渠道运营组`，后续复用既有 IM、SMS、EMAIL 派发、重试和供应商回执链路。
5. 同类未确认 outbox 在冷却窗口内已经存在时，本轮仅记录阈值命中和抑制数量，不重复生成；冷却时长可配置。
6. 当前只确认 outbox 和可配置外发链路具备代码与自动化测试能力，不代表真实企业微信、短信或邮件已经送达。

自动派发说明：

1. 开启 `HSP_PAYMENT_ISSUE_ALERT_AUTO_DISPATCH_ENABLED` 后，`PaymentCompensationScheduler` 会按配置间隔调用 `runAutoDispatchIssueAlerts()`。
2. 自动派发继续使用 `PAYMENT_ISSUE_ALERT_DISPATCH` 任务租约，并复用人工入口相同的值班路由、供应商选择、限流、重试、失败切换、回执和来源 outbox 状态回写逻辑。
3. 开关默认关闭；本地通知器回执和自动化测试不能替代真实通知供应商的送达证据。

### 13.7 手动执行异常 SLA 升级巡检

接口：`POST /api/payment-task-center/escalate-overdue-issues`

处理说明：

1. 任务会统计当前已超过 SLA 的支付交易异常：`P1` 30 分钟、`P2` 120 分钟。
2. 任务不直接修改支付单交易状态，而是写入任务日志，推动运营和值班负责人在异常中心完成处理动作。
3. 若存在超时异常，本次任务严重等级会判定为 `P1`，升级状态为 `升级值班负责人`。
4. 任务会为超过 SLA 且没有待确认告警的异常生成 `IN_APP_OUTBOX` 告警通知日志，后续可由真实 IM/短信/邮件通道消费。

### 13.8 查询支付日终处理总览

接口：`GET /api/payment-day-end/overview`

返回内容：

1. `totalBatchCount / completedBatchCount / abnormalBatchCount / latestBizDate`：日终批次汇总。
2. `openChannelAbnormalCount / openInternalAbnormalCount / openPendingRefundCount`：当前未收口差异计数。
3. `latestChannelSuccessCount / latestChannelSuccessAmount / latestInternalSuccessCount / latestInternalSuccessAmount`：最近批次渠道成功与内部事件成功事实。
4. `latestPaymentSuccessGapCount / latestPaymentSuccessGapAmount / latestPendingRefundAmount`：最近批次支付成功差异和待收口退款金额事实。
5. `reconciliationReadinessStatus / reconciliationReadinessType / reconciliationReadinessSummary / reconciliationSuggestedAction / reconciliationBlockingOwner`：当前是否可进入正式对账的统一准入口径、建议动作和责任归口。
6. `alerts`：当前差异告警，至少覆盖渠道回调未收口、内部事件未收口、退款待收口三类。
7. `recentBatches`：最近日终批次列表，供财务和运营回看业务日执行结果，并返回每个批次的 `reconciliationReadinessStatus`。

业务说明：

1. 当前接口定位是“对账前置事实收口”，不是正式财务对账差异闭环系统。
2. 当前日终批次会同时沉淀支付成功事实、渠道成功收口事实、内部事件成功事实、成功差异事实和待收口退款金额。
3. 对账准入口径优先判断“主链路事实是否收口”：只要存在渠道异常、内部事件异常或支付成功差异，就应直接判定“禁止进入对账”。
4. 如果主链路事实已收口但仍有退款待收口，则可判定为“有条件进入对账”，由财务在正式对账时同步关注逆向资金差异。
5. 差异告警的 `actionRoute` 直接指向异常中心、事件出站页或退款页，便于次日快速排查。
6. 后续如果接入渠道对账文件和账务分录核对，可继续在该接口上扩展更细粒度差异事实。

### 13.9 主动查单与成功收口

接口：`POST /api/payments/query`

请求示例：

```json
{
  "paymentOrderId": "PAY202609080001"
}
```

处理规则：

1. `payment.channel-query.gateway-access.enabled=false` 时，非 demo 环境不使用微信、支付宝、线下银行或本地兜底模拟适配器；未找到其他真实适配器时返回 `PAYMENT-1004`。只有显式 demo profile 保留本地模拟查单。
2. 启用且配置完整的 `gateway-access` 后，支付核心携带 `X-Gateway-App-Code / X-Gateway-Access-Key` 请求头，向配置的 `query-url` 发送 `paymentOrderId / orderNo / channelCode / channelTransactionNo`，并读取统一响应中的 `data.tradeStatus / data.channelTransactionNo / data.querySource / data.channelReturnCode`。网关业务响应码非 `0` 时统一按查单失败处理。
3. 每次有效渠道查询都会写入查询事实日志；`WAIT_CALLBACK / USERPAYING / FAIL / UNKNOWN` 等非成功状态只刷新返回快照，不修改支付、订单或账单状态。
4. 渠道明确返回 `SUCCESS` 且存在渠道流水号时，通过条件更新原子抢占成功收口权；抢占成功后同步更新支付尝试、预付单、订单、账单，生成 `PAYMENT_SUCCESS` 事件，派发下游并释放提交并发令牌。
5. 已为 `SUCCESS / CLOSED` 的支付单不会被主动查单重新打开；并发或重复查单未抢到收口权时不会重复记账、生成事件或派发下游。
6. 外部网关调用失败、返回空数据或成功结果缺少渠道流水号时返回 `PAYMENT-1026`，不会以本地状态伪装成真实渠道结果。
7. `gateway-access` 与共享限流开关同时启用时，所有直接、人工补跑和自动补偿查单在调用网关前统一占用渠道时间窗配额；配额耗尽返回 `PAYMENT-1027`，不调用外部渠道，也不计入单笔自动退避失败历史。
8. 渠道提交与主动查单结果按 `channelCode + channelReturnCode` 查询启用且未归档映射，标准码、文案、处理建议、重试和人工介入口径进入支付尝试响应报文或查单通知日志，并刷新 `t_payment_order` 最近返回码快照，供后续详情稳定读取；映射读取失败采用未匹配降级，避免渠道已受理后因配置查询异常诱发重复下单。
9. 返回码标准化只提供统一运营语义和审计事实，不改变 `tradeStatus`，也不根据 `retryable` 自动重试。未知码不得推断成成功；只有渠道交易状态明确为 `SUCCESS` 时才按既有幂等流程收口。
10. `gateway-access` 当前已提供 `/api/gateway/payment-orders/query` 代理边界，并校验调用凭证、应用、权限、网关、有效证书和当前环境发布路由；访问密钥或渠道上游 URL 未配置时失败闭合，本地治理台账不作为支付状态来源。

配置项：

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `HSP_PAYMENT_CHANNEL_QUERY_GATEWAY_ENABLED` | `false` | 是否启用 gateway-access HTTP 查单 |
| `HSP_PAYMENT_CHANNEL_QUERY_GATEWAY_URL` | `http://127.0.0.1:8090/api/gateway/payment-orders/query` | gateway-access 统一查单地址 |
| `HSP_PAYMENT_CHANNEL_QUERY_SUPPORTED_CHANNELS` | `wx_h5,alipay_h5,offline_bank` | 允许交给外部网关查询的渠道编码 |
| `HSP_PAYMENT_CHANNEL_QUERY_GATEWAY_APP_CODE` | `APP_PAY_CORE` | 发送到 gateway-access 的调用应用编码 |
| `HSP_PAYMENT_CHANNEL_QUERY_GATEWAY_ACCESS_KEY` | 空 | 与 gateway-access 一致的访问凭证；空值时适配器不生效 |
| `HSP_PAYMENT_CHANNEL_QUERY_TIMEOUT_MS` | `2000` | 连接与读取超时毫秒数 |
| `HSP_PAYMENT_CHANNEL_QUERY_RATE_LIMIT_ENABLED` | `false` | 是否启用数据库共享渠道查单限流；仅在真实网关同时启用时生效 |
| `HSP_PAYMENT_CHANNEL_QUERY_RATE_LIMIT_WINDOW_SECONDS` | `60` | 渠道共享固定时间窗秒数，最小归一化为 `1` |
| `HSP_PAYMENT_CHANNEL_QUERY_RATE_LIMIT_MAX_QUERIES_PER_CHANNEL` | `100` | 单渠道每个时间窗允许占用的最大查单次数，最小归一化为 `1` |

### 13.10 异常告警通知供应商候选复核

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/payment-config/alert-providers` | 提交新增完整候选 |
| `PUT` | `/api/payment-config/alert-providers/{providerCode}` | 提交编辑完整候选 |
| `POST` | `/api/payment-config/alert-providers/toggle` | 提交启停候选 |
| `GET` | `/api/payment-config/alert-providers/reviews` | 查询复核申请 |
| `POST` | `/api/payment-config/alert-providers/reviews/{requestNo}/approve` | 异人声明批准并原子应用 |
| `POST` | `/api/payment-config/alert-providers/reviews/{requestNo}/reject` | 驳回候选，不改变运行态 |

新增或编辑请求包含 `providerCode / providerName / channelCode / endpointAlias / templateCode / templateBody / routeRule / routePriority / retryPolicy / rateLimitPolicy / enabled / requester / changeReason`。启停请求包含 `providerCode / enabled / requester / changeReason`；处理请求包含 `reviewer / reviewComment`。

供应商编码规范化为大写，通道仅支持 `IN_APP / IM / SMS / EMAIL`，同一编码同时最多一条待办。UPDATE/STATUS_CHANGE 批准时复验完整基线，CREATE 批准时复验编码未占用。复核身份可信度固定为 `SELF_DECLARED`，不代表正式审批或真实供应商联调。

## 14. 错误与边界说明

### 14.1 运营查询姓名出参规则

1. 订单、账单、收银台会话、退款、服务者结算和支付异常列表默认返回脱敏后的客户或服务者姓名；订单同时脱敏两类姓名。
2. 账单、收银台会话、订单、退款和服务者结算 CSV 与列表使用相同姓名脱敏规则；退款详情的 `customerName` 同样脱敏。
3. 筛选参数仍按原始输入查询，Mapper DTO、数据库事实、退款渠道请求和支付事件载荷不因展示脱敏而改变。
4. 当前接口不提供敏感原文查看能力；后续如需查看，必须先接入可信身份、按角色字段授权和访问审计，不能由前端还原。

### 14.2 统一支付记录与退款动作出参规则

1. `GET /api/payment-records` 的 `userId / userPaymentChannelId` 在无可信逐单来源时返回 `null`；列表和导出优先使用 `customerName` 按原始客户姓名输入匹配，历史参数 `userId` 可单独兼容，二者不可同时提供，不得将其解释为真实用户 ID 查询。
2. `GET /api/payment-records/{paymentOrderId}` 额外脱敏最近客户端 IP、幂等键和请求/响应 JSON，路由、回调和事件轨迹保持可用。
3. `/api/refunds/apply / approve / success / fail / retry` 的成功响应均返回脱敏后的 `customerName`，不改变退款状态机和渠道提交入参。
4. Mapper 原始 DTO 和数据库事实保持不变；当前接口不提供敏感原文恢复、可信授权或生产审计能力。

### 14.3 异常告警供应商回执出参规则

1. `GET /api/payment-issues/alerts` 和 `GET /api/payment-issues/alerts/export` 对 `providerReceiptSnapshot` 使用同一出参转换：对象或数组 JSON 递归脱敏后返回，非 JSON 或 JSON 标量返回 `[REDACTED]`。
2. `POST /api/payment-issues/alerts/{alertNo}/acknowledge` 在首次确认和已确认幂等返回路径执行同一转换，避免动作响应绕过列表口径。
3. `receiver` 当前承载值班团队标签，`alertContent / renderedContentSnapshot` 承载内部运营正文，本轮不做无证据遮蔽；供应商原始回执不会通过上述接口直接透传。
4. 确认状态判断和来源告警同步使用 Mapper 原始 DTO，数据库事实不被覆盖；当前规则不代表真实供应商联调、生产授权或隐私验收完成。

### 14.4 异常告警供应商回执来源前缀规则

1. 服务端识别形如 `HTTP_RESPONSE:{...}` 的受控大写来源前缀，返回时保留来源前缀并对冒号后的对象或数组 JSON 递归脱敏。
2. 无前缀对象或数组 JSON 沿用通用载荷脱敏；带来源前缀但正文不是结构化 JSON 时返回 `[REDACTED]`，不得透传传输异常原文。
3. 来源识别只用于出参格式转换，不改变数据库保存值、供应商投递状态、回执号或确认状态机。

### 14.5 异常告警传输异常摘要规则

1. 三类 Webhook 通知器发生连接、超时等传输异常时，`providerDeliveryMessage` 固定包含通道、超时配置和 `transportError=[REDACTED]`，不返回 `RestClientException` 原文。
2. 派发服务会再次截断第三方通知器返回消息中 `transportError=` 后的任意内容；通知器直接抛出异常时返回固定“通知器执行异常”摘要。
3. 原始异常文本仅进入内部 `providerReceiptSnapshot`，对外查询仍按第 14.3、14.4 节失败闭合；本规则不提供生产原文查看授权。

### 14.6 支付事件与死信任务出参最小化规则

1. `GET /api/payment-events`、`GET /api/payment-events/export` 以及事件重发后的刷新结果返回 `eventPayload` 脱敏副本，不修改 Mapper DTO 或数据库事件载荷。
2. 死信任务列表、待重放、人工结案和重放动作响应返回 `payloadSnapshot / headerSnapshot` 脱敏副本；实际重放消息继续使用数据库原始载荷，避免展示转换改变消费者输入。
3. `Authorization`、访问令牌、API/访问密钥、密钥、签名、私钥及 Cookie 等凭据字段统一返回 `[REDACTED]`，字段名大小写、连字符和下划线差异不影响识别。
4. 无法解析为对象或数组的载荷、消息头快照失败闭合为 `[REDACTED]`；重放发送失败的可见处置说明固定为 `重放发送失败：[REDACTED]`，不返回消息代理异常原文。
5. 本规则只定义默认对外最小化，不提供生产敏感原文查看、可信身份、字段级授权或访问审计能力。

### 14.7 未知系统异常响应规则

1. 未被业务异常或参数异常处理器接管的任意 `Exception` 统一返回 `code=PAYMENT-500`、`message=system error` 和非空 `requestId`，不得返回底层异常消息或堆栈。
2. 服务端错误日志使用同一 `requestId` 关联，仅记录异常类型，不记录异常消息与堆栈，防止数据库地址、SQL、内部端点或凭据片段进入普通日志。
3. `BusinessException` 仍返回明确领域错误码与业务文案，`IllegalArgumentException` 仍返回 `PAYMENT-400` 和参数校验提示；调用方应使用 `code + requestId` 区分和反馈未知系统错误。
4. 本规则不代表受控错误详情存储、集中日志权限、保留期限、告警或生产故障演练已经完成。

### 14.8 请求追踪号生成规则

1. `requestId` 为不透明追踪字符串，当前格式为 `REQ + yyyyMMddHHmmssSSS + 8 位小写十六进制 UUID 后缀`；调用方不得依赖长度或解析时间参与业务判断。
2. 时间部分使用线程安全的 JDK `DateTimeFormatter`，随机部分扩大同毫秒取值空间；成功与失败响应均通过 `ApiResponse` 的同一生成入口创建。
3. 前端应原样展示或反馈请求号，不得自行重建；当前规则不包含上游请求号接收、跨服务透传、消息 trace/span 或集中追踪平台能力。
4. 10,000 次本地并发唯一性测试只能证明当前样本，不构成生产多实例峰值容量或全局唯一性的形式化验收。

### 14.9 MyBatis SQL 与绑定参数日志规则

1. `application.yml` 显式配置 `mybatis.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl`，默认不输出 MyBatis SQL 语句、结果集和绑定参数。
2. 该配置不依赖普通日志根级别，因此将 root logger 调到 DEBUG 不会重新打开 MyBatis 语句与参数输出；支付渠道密钥、公钥、控制策略令牌和交易个人信息不得通过此日志路径扩散。
3. 手工创建 MyBatis `Configuration` 的 Mapper 集成测试必须显式设置同一 `NoLoggingImpl`，避免绕过 Spring Boot 配置形成测试日志旁路。
4. 关闭此日志会减少 SQL 文本与参数级诊断信息；生产应使用受控指标、链路追踪、慢查询设施或单独治理的诊断工具，本规则不证明生产日志 IAM、隐私或可观测性验收完成。

### 14.10 主动查单失败通知快照规则

1. `POST /api/payments/query` 调用渠道查单失败时，通知日志 `responsePayload` 只持久化受控 `BusinessException.code` 和固定 `message=channel query failed`，不得复制异常消息原文。
2. 原 `BusinessException` 继续向接口异常边界抛出，调用方错误码和可操作业务提示不变；`QUERY_FAILED` 状态、查单失败统计、自动补偿退避和处理日志继续按原口径工作。
3. 该规则阻断新失败快照扩散，不清理既有历史记录，也不代表生产字段级授权、访问审计、保留期限或真实渠道故障联调已经完成。

### 14.11 Mapper SQL 参数绑定规则

1. `payment-core` 的 MyBatis Mapper XML 禁止使用 `${...}` 原始字符串替换，筛选值、分页参数和写入字段必须使用 `#{...}` 参数绑定。
2. 排序字段、排序方向及其他不能作为 JDBC 参数的位置必须通过 `<choose>` 等固定 SQL 分支生成，不得直接使用请求参数拼接列名或关键字。
3. classpath 契约测试自动枚举全部 `mapper/*.xml` 并拒绝原始替换标记，新增 Mapper 文件自动纳入；当前 22 份 Mapper XML 均满足该规则。
4. 本规则只覆盖 MyBatis XML 原始替换入口，不证明数据库账号最小权限、业务授权、存储过程、生产 SQL 审计或渗透测试已经完成。

### 14.12 三端收银台接入令牌传递规则

1. `POST /api/payments/submit` 的 `accessToken` 继续位于 JSON 请求体，不得通过收银台或支付结果页 URL query 传递；`sourceAppId` 由三端固定终端配置确定。
2. APP、H5、PC 业务入口按来源应用把当前演示令牌写入标签页级 `sessionStorage`，收银台提交时读取；会话存储不可用必须失败闭合。
3. 旧版含 `accessToken` 的收银台或结果页链接只允许一次性迁移，页面随后使用 `router.replace` 删除 URL 参数，后续页面往返不再恢复该参数。
4. 该规则不把前端演示令牌升级为可信凭据；构建产物可见性、同源脚本访问和 XSS 风险仍存在，生产接入需改用可信身份、服务端会话或受控短期凭据。

### 14.13 运营查询分页参数规则

1. 具备分页的支付单、订单、账单、收银台会话、支付请求、支付流水、支付记录、路由执行、事件、处理日志、异常、告警、退款、服务者结算、任务日志和死信任务查询统一限制 `pageNo=1..1000`。
2. Mapper 使用的每页数量统一限制为 `1..100`，最大 offset 为 `(1000 - 1) * 100 = 99,900`；负数、零值和极大整数在进入 Mapper 前按边界收敛。
3. Service 返回的分页元数据使用归一化后的页码和每页数量，调用方不得依赖越界入参原样回显。
4. 本规则不改变非分页 CSV 导出，也不代表索引、游标分页、查询超时、生产大数据量性能或数据库容量已经验收。

### 14.14 告警派发失败回执规则

1. Webhook 连接、读取超时等传输异常的 `providerReceiptSnapshot` 固定为 `HTTP_TRANSPORT_ERROR:[REDACTED]`。
2. 通知器抛出的运行时异常固定为 `LOCAL_FAILURE:<providerDeliveryStatus>:[REDACTED]`，不得回传或持久化底层异常消息。
3. `providerDeliveryMessage` 继续返回固定运营摘要；正常供应商 HTTP 响应回执按既有规则保存，并在查询边界执行脱敏。
4. 本规则只约束新增失败事实，不代表历史快照清理、生产权限、审计或真实供应商验收完成。

### 14.15 告警供应商正常回执持久化规则

1. Webhook 原始响应只用于当前调用内解析成功码、回执号、投递状态和失败码，不直接写入普通告警日志。
2. `providerReceiptSnapshot` 保存 `HTTP_RESPONSE:` 加递归脱敏后的对象或数组 JSON；手机号、姓名、银行卡号和凭据字段按统一规则遮蔽。
3. 非 JSON 或 JSON 标量响应固定保存为 `[REDACTED]`；调用方应使用独立状态字段，不得从快照推导派发结论。
4. 如生产审计必须保留原始供应商凭证，应使用独立加密、授权、审计和保留期限设施，本接口不提供原文读取能力。

### 14.16 告警供应商结果字段容量规则

1. 通知器结果进入告警日志前统一限制：`providerReceiptNo<=128`、`providerDeliveryStatus<=64`、`providerDeliveryMessage<=255`、`renderedContentSnapshot<=1024`、`providerReceiptSnapshot<=2048` 字符。
2. 超长内容按字段上限截断；调用方必须使用独立状态字段，不得依赖说明或快照尾部表达业务结论。
3. 该边界覆盖内置和自定义通知器，防止单个供应商异常结果因列容量错误阻断备用通道派发。
4. 字符上限不代表真实供应商契约、数据库字节容量或生产峰值负载已经验收。

### 14.17 告警供应商诊断码展示规则

1. `providerDeliveryMessage` 中的供应商状态、失败码和成功码差异值仅保留最多 64 位的 ASCII 标识符，允许字符为字母、数字、点、下划线、冒号、斜杠和连字符。
2. 含空白、换行、自由文本或超长内容的值统一返回 `[REDACTED]`，不得原样拼接到说明字段。
3. 该处理只影响展示和普通日志，供应商状态匹配仍使用原始值，标准化投递状态保持原协议口径。
4. 当前规则不替代真实供应商码表契约、独立原始凭证存储或生产安全验收。

### 14.18 超时关单任务结果规则

1. `POST /api/payment-task-center/actions/close-expired-payments` 的外部响应结构保持 `PaymentTaskActionResultDTO`，其中 `processedCount` 为本轮扫描数量，`successCount` 为成功提交数量，`failCount` 为独立事务回滚数量。
2. 单笔失败不终止批次；响应与任务日志的 `summaryComment` 最多列出 3 个失败支付单号，完整失败规模以 `failCount` 为准。
3. 失败支付单保持未收口状态，后续自动或人工任务可再次选中；调用方不得把批次返回成功等同于每笔关单均成功。
4. 该接口仍表达支付核心本地状态处理结果，不代表真实支付渠道撤销、关闭或资金侧收口已经完成。

### 14.19 失败退款重试任务结果规则

1. `POST /api/payment-task-center/retry-failed-refunds` 的响应继续使用 `PaymentTaskActionResultDTO`；服务端按单轮容量选择失败退款，`processedCount` 为本轮候选数，`successCount` 为完成本地状态流转并提交渠道的数量，`failCount` 为单笔事务失败数量。
2. 单笔失败不终止批次，`summaryComment` 最多列出 3 个失败退款单号；调用方应以 `failCount` 判断完整失败规模。
3. 成功计数只表示渠道提交动作完成，不表示退款资金已到账；最终结果仍由退款回调或后续真实渠道查单确认。
4. 真实渠道接入必须使用稳定退款单号实现幂等，并处理请求超时但渠道可能已受理的不确定状态；本地事务无法回滚外部渠道副作用。

### 14.20 失败事件重发任务结果规则

1. `POST /api/payment-task-center/actions/republish-failed-events` 的响应继续使用 `PaymentTaskActionResultDTO`；人工模式按单轮容量处理 `FAILED` 事件，自动模式按同一容量处理已到重试时间的 `FAILED / PENDING` 支付成功与退款提交事件。
2. `processedCount` 为本轮候选数，`successCount` 为投递服务返回成功的数量，`failCount` 包含投递返回 `false` 和单条事务抛出运行时异常的数量。
3. 每条事件使用独立 `REQUIRES_NEW` 事务；普通投递失败写入的 `FAILED / DEAD_LETTER` 状态正常提交，意外异常回滚当前事件且不阻断后续事件。`summaryComment` 最多列出 3 个失败事件号。
4. 本地事务无法撤销已经交付给 HTTP 或 MQ 下游的外部事件；真实下游必须以稳定 `eventNo / messageId` 实现幂等，接口成功也不等同于所有订阅方已完成业务处理。

### 14.21 告警派发与回执回查任务结果规则

1. 告警派发与回执回查接口继续返回 `PaymentTaskActionResultDTO`；每条待派发告警或已受理供应商日志使用独立 `REQUIRES_NEW` 事务，单条异常计入失败并继续批次。
2. 派发事务包含当前告警的通道投递日志和源告警状态更新；受控供应商失败正常提交失败日志，意外数据库异常回滚当前告警。摘要最多列出 3 个未完全送达告警号。
3. 回执事务包含供应商回执更新和源告警确认，任一步异常均回滚当前条；摘要最多列出 3 个回写失败的供应商告警号。
4. 外部供应商受理不能由本地事务撤销，通知请求使用稳定 `alertNo` 作为幂等关联标识；真实供应商或通知网关必须实现防重，接口成功不等同于终端已送达或用户已确认。

### 14.22 支付主链路业务编号规则

1. `billNo / paymentOrderId / prepayOrderNo / routeNo / attemptNo / eventNo / notifyNo` 保留既有业务前缀，并使用“前缀 + 13 位毫秒时间 + 8 位 UUID 十六进制后缀”生成。
2. 接口调用方必须把编号视为不透明字符串，禁止依赖固定总长度、从编号解析创建时间或在传递时裁剪随机后缀。
3. 事件与通知编号继续作为对应处理日志的 `logNo / sourceNo`，不改变详情轨迹关联；告警任务日志使用独立 UUID 后缀编号。
4. 随机后缀降低应用层并发碰撞概率，但不替代数据库唯一约束、幂等键、渠道交易号或生产冲突监控。

### 14.23 异常 SLA 巡检任务结果规则

1. `POST /api/payment-task-center/actions/escalate-overdue-issues` 继续返回 `PaymentTaskActionResultDTO`，`processedCount` 保持“逾期异常存量 + 未确认升级候选”的既有口径。
2. 初次逾期告警和未确认升级告警逐条在独立 `REQUIRES_NEW` 事务中写入 outbox；单条写入失败计入 `failCount` 并继续后续候选，成功写入计入 `successCount`。
3. `summaryComment` 最多列出 3 个失败引用，初次告警使用异常号，升级告警优先使用来源告警号；调用方以 `failCount` 判断完整失败规模。
4. 自动任务租约不覆盖人工入口并发，当前 `NOT EXISTS` 候选过滤也不等同于数据库级唯一约束；接口暂不承诺人工并发触发 exactly-once。

### 14.24 异常 SLA 巡检并发幂等规则

1. 初次告警以 `ISSUE_ACTIVE:<issueNo>` 作为数据库生成键，同一异常同时最多一条活动告警；告警确认后释放该键，仍逾期时允许后续巡检重新生成。
2. 升级告警以 `ISSUE_ESCALATION:<sourceAlertNo>` 作为数据库生成键，确认后也不释放，保证每条来源告警最多生成一次升级 outbox。
3. 唯一键竞争失败作为幂等重复跳过：不增加 `successCount` 或 `failCount`，增加 `warningCount`，并在 `summaryComment` 展示跳过数量；其他数据库异常继续计入 `failCount`。
4. 幂等范围仅为本地告警生成，不代表供应商通知 exactly-once；调用方仍应使用稳定 `alertNo` 关联派发与回执事实。

### 14.25 模拟渠道交易号规则

1. 仅 demo profile 注册的本地微信 H5、支付宝 H5、线下银行与兜底适配器返回的 `channelTransactionNo` 使用“渠道前缀 + 13 位毫秒时间 + 32 位 UUID 十六进制段”，最大长度小于当前 `128` 字符字段上限。
2. `PaymentChannelSubmitResultDTO.channelTransactionNo` 与 `responsePayload.channelTransactionNo` 必须保持一致，支付核心将该值回写支付单并在后续渠道查单时透传。
3. 调用方必须把渠道交易号视为不透明字符串，不得校验固定长度、解析时间或自行重建；真实渠道接入后直接采用供应商返回值，不沿用本地模拟生成规则。
4. 该编号不是提交幂等键，也不证明支付成功。真实渠道超时或未知结果必须先按稳定支付标识查单，禁止仅因本地未落库直接重复下单。

### 14.26 支付提交幂等键规则

1. `POST /api/payments/submit` 在路由解析后确定最终 `idempotencyKey`；显式键优先，未提供时沿用“预付单号 + 支付方式 + 实际渠道”的既有派生规则。
2. 最终键必须包含非空白字符、长度不超过 `128` 字符且不含 ISO 控制字符。校验失败返回 `PAYMENT-1028`，并在控制策略、风控、尝试查询、并发令牌和渠道提交前终止请求。
3. `128` 字符限制与 `t_payment_attempt.idempotency_key` 和 `t_payment_submit_concurrency_token.holder_idempotency_key` 对齐；调用方不得依赖数据库截断，也不得对失败键自动裁剪后重试。
4. 合法原始键用于持久化判重和并发占位，JSON 请求快照使用转义后的表示；两者表达同一键值，但调用方不得把快照文本当作新的幂等键。

### 14.27 支付提交事务与未知结果规则

1. `POST /api/payments/submit` 在渠道调用前提交本地预处理事务：支付单进入 `WAIT_CALLBACK`，收银台进入支付中，路由记录、并发令牌和幂等支付尝试均已持久化；渠道调用不持有该本地事务。
2. 渠道正常返回后，独立结果事务按支付尝试号回写渠道响应和尝试状态，并追加 `PAYMENT_SUBMIT` 事件及 `SUBMIT` 通知事实；历史支付尝试不被批量覆盖。
3. 渠道调用抛出异常，或渠道已经返回但本地结果事务失败时，服务保持支付单 `WAIT_CALLBACK`，将尝试标记为“结果未知，待查单”、收银台标记为“结果待确认”，并保留活跃并发令牌。
4. 调用方收到提交异常后必须查询原支付单，不得自动创建新幂等键重提。只有可信回调、主动查单或受控关单能结束未知状态；本接口不承诺跨数据库与渠道的原子提交。

### 14.28 批量主动查单候选时间规则

1. `POST /api/payment-task-center/actions/query-pending-payments` 的人工与自动模式均要求支付单为 `WAIT_CALLBACK` 且存在支付尝试；预付单是否过期不再作为排除条件。
2. 首次等待阈值 `minAgeSeconds` 以该支付单最新 `t_payment_attempt.created_at` 为起点；同一渠道的批次排名也按最新尝试时间和尝试主键排序，不使用支付单创建时间。
3. 自动模式在首次等待之外继续应用既有查询历史指数退避；人工批量模式绕过历史退避但不绕过首次等待、渠道批次上限或总批次上限。
4. 缺少支付尝试的 `WAIT_CALLBACK` 异常数据不进入候选和积压计数，调用方应走数据异常排查而不是直接调用渠道查单。

### 14.29 过期支付关闭与成功恢复规则

1. `POST /api/payment-task-center/actions/close-expired-payments` 仅关闭仍为 `PREPAY_CREATED`、渠道交易号为空、不存在支付尝试、无关联 `ACTIVE` 提交令牌且预付单已过期的支付单；候选筛选与更新时均校验令牌和原有条件，并发提交已开始时不写关闭事件。
2. 存在支付尝试的 `WAIT_CALLBACK` 即使预付单已过期，仍计入达到等待阈值的人工和自动查单候选；自动模式继续使用查询历史指数退避、最大间隔、渠道批次配额和限流。
3. 可信成功回调或主动查单通过同一条件更新抢占成功收口权。普通 `PAYMENT_CLOSED` 不可重开；历史上带 `PAYMENT_EXPIRED_CLOSED` 事件的关闭单允许恢复为 `SUCCESS`，且只有抢占成功者记账和发布成功事件。
4. 当前接口没有实现真实渠道关闭或撤销调用，非成功渠道结果不会被本地过期任务推断为渠道已关闭；生产接入需补渠道终态码表、关闭请求幂等、关闭与成功竞态及对账规则。

### 14.30 人工关闭支付单规则

1. `POST /api/payments/close` 仅接受尚未提交渠道的支付单：状态属于 `PREPAY_CREATED / RISK_REVIEW / RISK_BLOCKED / RISK_REJECTED`，渠道交易号为空、不存在任何支付尝试且同一精确支付单号未关联 `ACTIVE` 提交令牌。
2. 服务端使用条件更新原子抢占关闭权。条件更新失败后若最新状态为 `SUCCESS / CLOSED` 则幂等返回最新详情；非终态且关联 `ACTIVE` 提交令牌时返回 `PAYMENT-1016`，提示刷新并核对原支付事实；其他不满足关单条件的非终态返回 `PAYMENT-1029`，在具备渠道受理事实时先主动查单。
3. 以上失败路径均不更新预付单、不追加关闭事件且不释放提交令牌；已提交后历史误关的 `CLOSED` 单据重复关闭时同样保留令牌和可信成功恢复入口。
4. `GET /api/cashier/{prepayOrderNo}` 新增 `paymentOrderId`，供收银台直接查询与关闭对应支付单。该接口仍只是本地关单，不调用真实渠道关闭或撤销。

### 14.31 支付回调渠道绑定规则

1. `POST /api/payments/callback/{channel}` 先按 `{channel}` 完成既有签名、时间窗和 nonce 防重校验，再校验 `{channel}` 与支付单持久化 `channel` 忽略大小写后一致。
2. 支付单未绑定渠道或渠道不一致时返回 `PAYMENT-1030`；该请求不得更新支付单、支付尝试、预付单、订单、账单、通知日志、事件或提交令牌。
3. 调用方必须使用实际路由渠道的独立回调入口与凭证，不得用一个渠道的签名收口另一渠道支付。产品页面不发起回调；本地受控测试工具也不得硬编码默认渠道。
4. 当前绑定到平台渠道编码，尚未进一步绑定渠道商户号、应用标识、证书版本或网络来源；这些仍是生产真实渠道接入项。

### 14.32 支付回调渠道交易号绑定规则

1. `POST /api/payments/callback/{channel}` 在渠道绑定通过后校验 `channelTransactionNo`。支付单已有渠道交易号时，回调值必须逐字符精确相等，不做裁剪、大小写转换或格式解析。
2. 已有值不一致返回 `PAYMENT-1031`，不覆盖原交易号，也不更新支付状态、尝试、预付单、订单、账单、通知日志、事件或提交令牌。
3. 支付单渠道交易号为空时，允许通过全部安全校验的同渠道回调首次写入交易号，以收口渠道受理但本地结果未知的场景。
4. 调用方必须把渠道交易号作为不透明标识原样保存和回传；生产还需联合校验渠道商户订单号、商户/应用标识和对账文件。

### 14.33 支付回调验签环境默认规则

1. 基础配置 `payment.callback.require-signature` 默认值为 `true`，服务构造器兜底同样为 `true`；部署未提供 `HSP_PAYMENT_CALLBACK_REQUIRE_SIGNATURE` 时仍执行签名、时间窗和 nonce 防重校验。
2. 仅 `application-demo.yml` 显式配置为 `false`，用于本地受控工具联调；产品页面不暴露模拟回调。测试、预发与生产不得设置 `HSP_PAYMENT_CALLBACK_REQUIRE_SIGNATURE=false`，也不得在验签失败后自动降级重试。
3. 默认开启不等于安全材料已经就绪。非 demo 环境必须按渠道配置 HMAC-SHA256 密钥或 RSA2 公钥；材料缺失返回 `PAYMENT-1006`，字段缺失或验签失败返回 `PAYMENT-1005`。
4. 当前契约测试只锁定配置默认值，不替代真实渠道签名规范、商户应用绑定、证书轮换、密钥托管、网络来源限制及生产报文验收。

### 14.34 支付回调渠道凭证隔离规则

1. HMAC-SHA256 验签只读取 URL 渠道对应 `t_payment_channel_config.callback_secret`，RSA2 只读取同一渠道的 `callback_public_key`；服务不接受全局回调密钥。
2. 渠道安全配置不存在、HMAC 密钥为空或 RSA2 公钥为空均返回 `PAYMENT-1006`，且不进入 nonce 写入、支付单读取、渠道/交易号绑定或支付状态处理。
3. `payment.callback.secret` 与 `HSP_PAYMENT_CALLBACK_SECRET` 已从有效配置契约移除。调用方不得以共享凭证兜底多个渠道，也不得在单渠道配置失败时切换其他渠道密钥重试。
4. 当前渠道配置仍将密钥材料存于业务数据库；生产需要继续迁移到专用密钥托管并绑定密钥版本、商户应用和证书轮换事实。

### 14.35 支付回调算法运行时白名单

1. 回调验签读取渠道配置后，将 `callbackSignAlgorithm` 去除首尾空白并按大写规范化，只接受精确 `HMAC-SHA256` 或 `RSA2`。
2. 算法为空或其他值返回 `PAYMENT-1032`；不再为空值默认 HMAC，也不接受 `RSA`、`RSA-SHA1` 等模糊名称。
3. 算法校验失败发生在签名计算、nonce 清理/写入和支付单状态处理之前，不产生支付、资金、事件或防重事实副作用。
4. 接入新算法必须先扩展明确的配置校验、运行时实现、错误处理和真实渠道测试，不能通过字符串包含关系或默认分支启用。

### 14.36 支付回调时间戳数值边界

1. `timestamp` 按有符号 64 位秒级 Unix 时间戳解析，非数字返回 `PAYMENT-1008`；解析成功后使用无静默溢出的差值计算校验渠道时间窗。
2. 当前时间与回调时间相减发生 `long` 溢出或差值超出正负窗口时返回 `PAYMENT-1008`；渠道有效窗口为空或非正属于安全配置异常，返回 `PAYMENT-1034`。
3. 时间窗失败发生在签名计算、nonce 清理/写入和支付单状态处理之前，即使攻击者为极端时间戳生成正确签名也不会产生业务副作用。
4. 渠道接入必须明确时间戳单位和签名原文；毫秒值不得直接作为秒值传入，服务端也不做启发式单位转换。

### 14.37 RSA2 回调异常分类

1. `signature` 不是合法 Base64，或解码后的字节无法作为 RSA2 签名验证时，返回 `PAYMENT-1005`；响应不回显原始签名字节。
2. 渠道公钥为空返回 `PAYMENT-1006`；公钥 Base64、X.509 SubjectPublicKeyInfo 或 RSA 解析失败返回 `PAYMENT-1033`。
3. 上述异常发生在 nonce 清理/写入和支付单状态处理之前，不产生支付、资金、事件或防重事实副作用。
4. 当前只接受可解析为 X.509 RSA 公钥的文本；真实渠道如使用证书、不同 PEM 类型或密钥版本标识，需在适配层明确转换与轮换契约。

### 14.38 支付回调安全窗口配置规则

1. 回调时间窗只读取 URL 渠道安全配置中的 `notifySignWindowSec`；基础配置不再提供 `payment.callback.allowed-skew-seconds` 或环境变量回退。
2. `notifySignWindowSec` 为空、为 `0` 或负数时返回 `PAYMENT-1034`，失败发生在时间窗计算、签名验证、nonce 清理/写入和支付状态处理之前。
3. `payment.callback.nonce-ttl-seconds` 必须为正数；显式配置非正值会阻止应用启动，而不是写入立即过期的防重记录。默认值后续按 14.39 节修正为 `601` 秒。
4. 生产环境应通过配置发布门禁、真实数据库时间语义和多实例重放测试验证 nonce 生命周期；对称时间窗的严格计算见 14.39 节。

### 14.39 支付回调 nonce 生命周期覆盖规则

1. 若渠道时间窗为 `W` 秒，时间戳在当前时间前后 `W` 秒内均有效；未来边界通知从首次接收到最终失效最多持续 `2W` 秒。
2. nonce 到期记录在 `expires_at <= NOW()` 时可清理，故 `payment.callback.nonce-ttl-seconds` 必须严格大于 `2 * notifySignWindowSec`；等于 `2W` 仍返回 `PAYMENT-1034`。
3. 默认配置为时间窗 `300` 秒、nonce TTL `601` 秒。任一启用渠道将窗口调大后，部署配置必须同步提高 TTL，否则通知在签名与 nonce 副作用前失败闭合。
4. 该约束只保护本地防重记录生命周期；真实渠道还需验证时间戳精度、数据库时钟、多实例唯一键竞争、清理调度和渠道重试跨度。

### 14.40 支付回调字段与审计快照边界

1. `channel/paymentOrderId/tradeStatus/channelTransactionNo` 必填，最大长度分别为 `64/64/32/128` 字符，任一字段含 ISO 控制字符或超限均返回 `PAYMENT-400`，且发生在验签和数据库访问前。
2. `timestamp` 最大 `20` 字符，超限或含控制字符返回 `PAYMENT-1008`；`nonce/signature` 最大 `128/8192` 字符，超限或含控制字符返回 `PAYMENT-1005`。
3. 渠道路径去首尾空白并按小写规范化；空白或超过 `64` 字符时返回 `PAYMENT-400` 或 `PAYMENT-1009`。规范化值用于验签原文、支付单渠道绑定、通知日志和事件快照。
4. 通知日志中的 `tradeStatus` 与事件中的渠道值按 JSON 字符串完整转义，不改变用于验签和状态判断的原始交易状态；整体 HTTP 请求体上限和渠道专属字段规则仍由接入网关补齐。

### 14.41 支付回调 nonce 持久化 SQL 契约

1. nonce 写入使用 `TIMESTAMPADD(SECOND, #{expireSeconds}, NOW())` 计算 `expires_at`，与原 `DATE_ADD(NOW(), INTERVAL #{expireSeconds} SECOND)` 保持“数据库当前时间增加 TTL 秒数”的等价语义。
2. `channel_code` 写入前继续按小写规范化，`(channel_code, nonce)` 唯一约束继续承担跨请求防重；`expires_at <= NOW()` 的记录仍由既有清理 SQL 删除，接口字段和错误码不变。
3. H2 MySQL 模式集成测试已覆盖大小写查询、小写落库、`601` 秒 TTL、重复唯一键和到期清理；该结果不代表真实 MySQL 执行计划、锁行为、时钟精度或多实例竞争已经验收。

### 14.42 支付回调 nonce 清理隔离与调度配置

1. `POST /api/payments/callback/{channel}` 在签名通过后只删除当前规范化 `channel + nonce` 的已过期记录，再尝试写入新 nonce；不会在回调事务中全量删除其他历史记录，重复活跃 nonce 仍返回 `PAYMENT-1007`。
2. 历史过期记录由后台调度清理，默认配置为 `payment.callback.nonce-cleanup.fixed-delay-ms=300000`、`batch-size=1000`，对应环境变量为 `HSP_PAYMENT_CALLBACK_NONCE_CLEANUP_DELAY_MS` 和 `HSP_PAYMENT_CALLBACK_NONCE_CLEANUP_BATCH_SIZE`。
3. 批量清理按 `expires_at, id` 选择有限主键集合后删除，单轮不会触碰未到期记录；批次非正会阻止清理组件装配。该内部任务不新增对外接口或页面操作。
4. 本节初版尚未接入多实例清理租约，后续已在 14.43 节补齐 owner 隔离；当前仍没有积压监控或生产 MySQL 执行计划证据，部署前需按回调峰值和 TTL 校准周期、批次与数据库容量。

### 14.43 自动任务租约 owner 与释放契约

1. 自动任务数据库租约的 `lock_owner` 使用 `triggeredBy:instanceSuffix`，其中 UUID 实例后缀仅用于内部互斥；任务运行日志和页面仍使用原 `triggeredBy`，不新增对外字段。
2. 有效租约不允许同 owner 或其他 owner 重入；租约到期后新 owner 可原子接管。释放语句同时匹配 `task_code + lock_owner`，旧 owner 不能释放已经由新实例持有的租约。
3. nonce 清理使用任务码 `PAYMENT_CALLBACK_NONCE_CLEANUP` 和 `120` 秒租约；未抢到时不执行清理，执行成功或异常均尝试释放本实例租约。
4. 本节初版尚无续租，后续已在 14.44 节补齐独立事务和周期续期；生产仍需验证真实 MySQL 时钟、锁行为、租约丢失监控和强 fencing 边界。

### 14.44 自动任务租约续期内部契约

1. 自动任务租约不新增对外 HTTP 接口；任务中心、告警派发/回执和 nonce 清理在内部统一调用 `PaymentTaskLeaseService`，对页面、任务日志和既有接口响应无字段变更。
2. 初始化与获取在同一个独立 `REQUIRES_NEW` 短事务中完成，续期和释放各自使用独立短事务。获取成功后按租期三分之一调度续期，当前 `120` 秒租期对应 `40` 秒间隔。
3. 续期 SQL 仅更新 `task_code + lock_owner` 匹配且尚未过期的记录；异 owner、已过期 owner 或已释放租约返回 `0`，不得恢复旧执行者资格。句柄关闭会取消续期并按同一 owner 幂等释放。
4. 该契约降低正常长任务重复执行概率，但不是强 fencing token；数据库不可用、长时间 JVM 暂停或续期线程阻塞时仍可能失约，生产需要租约丢失监控、业务幂等和真实多实例故障测试。

### 14.45 支付提交共享限流内部契约

1. `POST /api/payments/submit` 在控制策略权限、访问令牌和严格自检通过后，对尚无相同幂等键支付尝试的请求占用共享分钟配额；已有尝试直接沿用幂等恢复路径，不重复计数。
2. 接口配额范围为 `sourceAppId + terminal + clientIp`，耗尽返回 `PAYMENT-1023`；来源支付方式配额范围为 `sourceAppId + paymentMethod`，耗尽返回 `PAYMENT-1018`。两个组合范围只以 SHA-256 摘要写入数据库。
3. 服务先占接口额度，再占来源支付方式额度；占用通过独立短事务立即提交。第二个范围拒绝不会退还第一个范围额度，避免失败重试绕过入口流量约束。
4. 固定窗口长度为 `60` 秒，阈值继续来自生效支付控制策略的 `interfaceMinuteSubmitLimit / minuteSubmitLimit`；非正值表示对应范围不启用。该内部契约不新增请求或响应字段。
5. 当前只完成本地 H2 MySQL 模式并发验证，真实 MySQL 的行锁、热点范围、代理客户端 IP 可信边界、网关限流与生产容量仍需部署前验收。

### 14.46 支付提交客户端 IP 可信代理契约

1. `POST /api/payments/submit` 始终覆盖 JSON 中的 `clientIp`。默认使用 `HttpServletRequest.remoteAddr`，直接调用方不能通过请求体或 `X-Forwarded-For` 自选限流维度。
2. `payment.submit.trusted-proxy-addresses` 对应环境变量 `HSP_PAYMENT_SUBMIT_TRUSTED_PROXY_ADDRESSES`，默认空；多个固定代理 IP 以逗号分隔。只有直接对端规范化后命中该列表时才解析 `X-Forwarded-For`。
3. 可信代理链从右向左处理：跳过列表中的代理地址，首个非可信数值 IP 作为客户端地址。链中存在空项、主机名或畸形地址，头长度超过 `2048` 字符、跳数超过 `32`，或整条链都是可信代理时，统一回退直接对端。
4. 当前配置只接受明确 IPv4/IPv6 地址，不支持 CIDR 或主机名。生产启用前必须核对负载均衡和反向代理是否清理/追加转发头、实际多跳顺序及地址漂移策略，不能照搬示例列表。

### 14.47 支付提交字段与终端规范化契约

1. `POST /api/payments/submit` 在查找预付单前校验请求。`prepayOrderNo` 必填且最长 `64` 字符；`paymentMethod/channelCode/sourceAppId/merchantNo/accessToken/clientIp/clientDeviceId/payerPhone` 非空时最长分别为 `32/64/64/128/128/64/128/32` 字符，所有字段去除首尾空白并拒绝 ISO 控制字符。
2. 支付方式允许先为空并由有效运营配置补齐，但补齐结果仍必须满足必填和 `32` 字符上限；请求渠道允许为空交给路由选择，路由最终渠道必填且最长 `64` 字符。
3. `terminal` 缺失时为 `UNKNOWN`；规范值仅有 `APP/H5/PC/MINI_PROGRAM/API/UNKNOWN`。兼容别名 `APP_WEB -> APP`、`H5_WEB/MOBILE_WEB -> H5`、`PC_WEB -> PC`、`WECHAT_MINI_PROGRAM/WX_MINI_PROGRAM/小程序 -> MINI_PROGRAM`，其他值返回 `PAYMENT-1035`，不得形成新的限流、风控或审计维度。
4. 显式 `idempotencyKey` 继续按 14.38 的 `128` 字符与控制字符规则返回 `PAYMENT-1028`；其他提交字段非法统一返回 `PAYMENT-1035`。以上限制不替代部署网关的整体 JSON 请求体上限和渠道专属字段规则。

### 14.48 渠道查单结果字段契约

1. 查单适配器必须返回非空对象，`tradeStatus` 和 `querySource` 必填且最长分别为 `32/64` 字符；`channelTransactionNo/channelReturnCode` 非空时最长分别为 `128/64` 字符。所有字符串 trim 后不得含 ISO 控制字符。
2. 标准化字段 `standardizedCode/standardizedMessage/handlingSuggestion/retryable/manualInterventionRequired/mappingVersion` 非空时最长分别为 `64/256/256/16/16/32` 字符；原始结果在映射前校验，映射产物在返回前再次校验。
3. 支付服务会对最终结果防御性复验。任何空缺、超长或控制字符返回 `PAYMENT-1026`，只允许写入固定文案的查单失败日志，不得更新支付返回码快照或进入支付成功结算、事件和令牌处理。
4. 本契约限制单字段，不替代 gateway-access 和 HTTP 客户端的整体响应体上限、渠道状态枚举与真实报文协议校验。

### 14.49 支付入口标识与创建预付单字段契约

1. `GET /api/payments/{paymentOrderId}`、`GET /api/payments/cashier/{prepayOrderNo}`、`POST /api/payments/query` 和 `POST /api/payments/close` 的业务标识均必填、trim 后不超过 `64` 字符且不得包含 ISO 控制字符；非法请求返回 `PAYMENT-1036`，不进入数据库或查单限流。
2. `POST /api/payments/prepay` 的 `orderNo/payScene` 必填且分别不超过 `64/32` 字符；可选 `customerName/cashierTitle/remark` 分别不超过 `128/128/255` 字符。所有非空字符串 trim 后不得包含 ISO 控制字符；`remark` 会作为预付单创建事实写入支付处理日志，不改变订单、账务或钱包状态。
3. 可选 `amount` 一旦提供必须大于 `0`、最多 `16` 位整数和 `2` 位小数。虚拟支付仍要求客户名称和金额；标准订单只缺一项金额事实返回 `PAYMENT-1011`。
4. 标准订单应收必须大于 `0`、已付不得小于 `0`；否则返回 `PAYMENT-1038`。客户名称缺失或纯空白返回 `PAYMENT-1011`。标准订单剩余应付 `orderAmount - paidAmount <= 0` 时返回 `PAYMENT-1037`。这些校验均在创建账单、支付单和预付单前完成；不替代跨服务并发结算、订单锁或生产数据库隔离验证。
5. 创建预付时写入不可由调用方指定的内部来源标记：存在标准订单写 `STANDARD_ORDER`，无标准订单写 `VIRTUAL`；有效预付复用必须与当前来源类型一致，否则返回 `PAYMENT-1011`。响应不暴露该标记。增量迁移对无法证明创建来源的存量统一保留 `UNKNOWN`，不会仅凭当前订单行存在与否自动归类；上线前应核对仍活跃的存量，只有取得可信历史证据并走受控审批才能补标，否则等待其安全收口或按当前业务事实重新创建。此迁移未在目标 MySQL 8 执行或演练。

### 14.50 创建预付单并发幂等契约

1. `POST /api/payments/prepay` 在字段校验后，以规范化 `orderNo` 幂等初始化 `t_payment_prepay_creation_lock` 协调行，并在当前事务内通过 `SELECT ... FOR UPDATE` 串行化同订单请求。
2. 获取订单级行锁后才允许查询有效预付单、读取订单金额和创建账单/支付单/预付单；后到请求必须在前一事务提交后重新执行查重，命中有效预付单时返回同一 `prepayOrderNo/paymentOrderId`。
3. 协调行无法读取时返回 `PAYMENT-500` 并回滚，不得退化为无锁创建。运行库必须先执行对应增量 DDL；本契约只覆盖共享 payment-core 主库，不宣称对跨库订单更新形成全局互斥。

### 14.51 创建预付单到期时间与服务事务验证契约

1. 账单 `due_at` 使用 `TIMESTAMPADD(DAY, 1, NOW())`，预付单 `expires_at` 使用 `TIMESTAMPADD(HOUR, 1, NOW())`；两者与原 `DATE_ADD` 口径等价，并兼容 MySQL 8 与 H2 MySQL 模式。
2. 完整服务双事务验证要求后到请求在同订单锁释放前不能完成；锁释放后必须重新查重并返回首请求创建的相同 `prepayOrderNo/paymentOrderId`。
3. 验证结束时协调表、账单、支付单和预付单各只能存在一条对应记录。该契约的本地自动化证据不替代真实 MySQL 多实例、锁等待超时、死锁恢复和容量验收。

### 14.52 创建预付单幂等参数一致性契约

1. 同一 `orderNo` 存在有效预付单时，`payScene`、显式 `customerName`、显式 `amount` 与生效后的 `cashierTitle` 必须和原预付单一致；金额按数值比较，不受币种符号、千分位或尾随零影响。
2. 参数一致时接口继续返回原 `prepayOrderNo/paymentOrderId`；任一参数冲突时返回 `PAYMENT-1038`，不得读取后续订单金额、覆盖旧单或创建第二组账单/支付单/预付单。
3. 一致性校验在订单级事务锁内、查询有效预付单后执行，确保并发后到请求基于已提交事实判断。当前契约不包含上游订单版本号或跨系统请求摘要。

### 14.53 标准订单显式参数契约

1. 标准订单首次创建时，若请求显式传入 `customerName`，必须与订单当前客户一致；显式 `amount` 必须与 `orderAmount - paidAmount` 的当前剩余应付数值一致，否则返回 `PAYMENT-1038`。
2. 显式 `cashierTitle` 会写入新预付单；未传时使用“家政服务收银台”。后续同订单有效预付单复用按相同有效标题校验，不得出现首次忽略、重试冲突。
3. 客户或金额冲突在账单查询及任何支付事实写入前失败。当前读取仍是 payment-core 本地快照，不构成跨系统订单版本锁定。

### 14.54 有效预付单复用时的订单事实契约

1. 获取订单协调锁后，接口先读取当前 `orderAmount/paidAmount`，再查询同订单有效预付单；标准订单还读取当前客户，用于复核旧预付单金额与客户。
2. 旧预付单金额不等于当前剩余应付或客户不一致时返回 `PAYMENT-1038`；当前剩余应付小于等于零时返回 `PAYMENT-1037`，不得返回旧收银台。
3. 无标准订单事实时按虚拟支付处理，`customerName/amount` 每次请求均必填，缺失返回 `PAYMENT-1011`，且不查询或复用有效预付单。
4. 支付成功事务自 14.55 起纳入同一协调边界；外部订单更新仍不在本契约内，不宣称跨服务强一致。

### 14.55 支付成功收口与预付创建协调契约

1. 渠道回调或主动查单确认成功且支付单尚未成功收口时，服务必须先按支付单 `orderNo` 获取 `t_payment_prepay_creation_lock` 行锁，再抢占支付成功状态并更新订单、账单、预付单和支付尝试。
2. 获取协调锁后必须重新查询支付详情；支付单已成功时幂等返回，不重复更新资金事实或发布成功事件。协调行缺失、支付单消失或关联订单变化时失败闭合。
3. 主动查单的成功结果在获取协调锁后才允许更新支付单返回码快照，统一锁顺序为订单协调行在前、支付及订单账单事实在后；非成功查单不进入资金收口锁边界。
4. 本契约只协调 payment-core 共享数据库内的事务，不锁定外部订单服务，也不代表真实渠道、多实例 MySQL 或生产资金一致性已验收。

### 14.56 订单成功收款超额异常与日终契约

1. 异常中心按 `orderNo` 聚合标准订单下正金额 `SUCCESS` 支付单的毛成功金额；仅当合计严格大于订单 `orderAmount` 时返回 `issueType=订单成功收款超额`、`severity=P1`，异常号固定为 `ISSUE-OVERPAID-{orderNo}`。历史零额、负额或空金额不能冲抵该预警，详见 14.390。
2. 异常详情关联该订单主键最大的成功支付单，责任组为“账务清结算联动组”；正常拆单合计小于或等于应收不产生异常。可信渠道成功不得为了消除异常而改写为失败。
3. 日终成功差异按支付单 `id` 顺序累计同订单正金额成功支付；当前支付单纳入累计后超过应收时，该支付单计入差异单量，其正金额整笔 `amount` 计入差异金额。因此 `80 + 30 > 100` 的差异金额为 `30.00`，实际净超额仍为 `10.00`。非正金额仍可因回调或事件缺失进入差异笔数，但不扣减差异金额。
4. 退款、调账和差错处理是后续独立事实，不从毛成功聚合中删除原异常。接口结果用于运营定位和日终阻断，不代表真实渠道流水已核对或退款已经审批执行。

### 14.57 统一支付记录成功退款字段契约

1. `GET /api/payment-records`、`GET /api/payment-records/{paymentOrderId}` 与 CSV 导出的 `refundCount/refundedAmount` 采用同一口径：按支付单统计状态为 `SUCCESS`、成功时间非空且不存在退款终态事实不一致的退款单数量与金额合计。
2. `REVIEWING/PROCESSING/FAIL`、成功时间缺失或退款终态事实不一致的退款不计入已退次数和金额；没有可信成功退款时返回 `refundCount=0`、`refundedAmount=¥0.00`，不得用申请金额、处理中金额或待核查退款替代已退事实。
3. 支付记录仍保留原支付金额和成功状态，退款作为独立事实叠加展示，不回写或净额化原支付金额。列表聚合依赖 `(payment_order_id, status)` 索引，部署前必须应用对应增量迁移。
4. 管理端“本页已退金额”仅汇总当前接口页的 `refundedAmount`，不代表所有匹配记录的全量退款合计；真实全量指标需后续独立总览接口提供。

### 14.58 退款申请与失败重试额度契约

1. 发起退款时，服务按原支付单串行化额度判断；活动退款口径固定为 `REVIEWING/PROCESSING/SUCCESS`，其金额合计加本次申请不得超过原支付实付金额。
2. `FAIL` 退款不占活动额度，但 `POST /api/refunds/retry` 及任务中心失败退款重试在恢复为 `PROCESSING` 前必须重新校验并占用额度，不能沿用首次申请时的旧额度结论。
3. 替代退款已占满额度时，重试返回“累计退款金额不能超过原支付金额”，目标退款保持 `FAIL`，且服务不得调用退款渠道；退款额度快照异常时同样失败闭合。
4. 该契约约束 payment-core 本地事务与状态，不代表渠道退款已受理、到账或完成；最终已退展示仍只统计 `SUCCESS` 退款。

### 14.59 退款渠道下发 outbox 与幂等契约

1. 退款审核和失败重试进入 `PROCESSING` 时，必须在同一数据库事务写入或重新激活 `REFUND_SUBMIT` 事件；事件号由 `refundOrderId` 稳定派生，同一退款单不创建并列渠道下发事件。
2. 渠道调用只允许在业务事务提交后执行，并以 `refundOrderId` 作为渠道幂等键；即时派发使用独立事务，自动补偿处理到期的 `PENDING/FAILED` 事件，已 `SUCCESS` 事件直接幂等返回。
3. 派发前必须核对事件中的退款单号、支付单号与退款快照，且退款金额为正、渠道非空、退款仍为 `PROCESSING`；不一致时不得调用渠道。
4. 渠道异常或未返回有效渠道退款号属于结果未知，退款继续保持 `PROCESSING` 并占用额度，事件进入重试或死信；不得据此返回退款失败、释放额度或生成新的退款幂等键。
5. 本契约不证明真实渠道已实现幂等查询/重放，也不代表真实 MySQL 多实例、渠道退款到账、生产恢复和人工核验已经验收。

### 14.60 退款渠道受理事实字段契约

1. `GET /api/refunds/{refundOrderId}` 新增 `channelCode/channelRefundNo/channelSubmittedAt`：分别表示当前一次实际受理渠道、渠道侧退款单号和该次首次获得受理号时间；尚未受理或失败重试已重置当前轨迹时返回空值或 `-`，不得推断为失败。
2. 渠道返回的退款号必须非空、最长 `128` 字符且不含 ISO 控制字符；只允许在退款仍为 `PROCESSING` 且尚未绑定渠道退款号时首次写入，之后不得由补偿覆盖。
3. `(channelCode, channelRefundNo)` 在退款单表内唯一。事件补偿读取到既有受理号时，只有已绑定渠道与原支付渠道一致才可直接收口事件；不一致时不得再次调用渠道。
4. `channelSubmittedAt` 和 `channelRefundNo` 只表达渠道受理事实，退款是否成功仍由后续可信退款回调或主动查单确认；接口不得将受理时间作为 `successAt` 返回。

### 14.61 退款可信回调错误与幂等契约

1. `PAYMENT-1039` 表示退款回调业务字段缺失、超长或含控制字符；`PAYMENT-1042` 表示退款状态不是 `SUCCESS/FAIL`，或当前状态不能收口。
2. `PAYMENT-1040/1041` 分别表示 URL 渠道、渠道退款号与退款单已绑定事实不一致；`PAYMENT-1043` 表示回调目标与既有终态冲突；退款单不存在返回 `PAYMENT-1044`。
3. 验签、时间窗和 nonce 仍使用 `PAYMENT-1005` 至 `PAYMENT-1009`、`PAYMENT-1032` 至 `PAYMENT-1034`。nonce 唯一键跨 `PAYMENT/REFUND` 共用同一渠道范围，不能跨业务重放。
4. 相同终态使用新 nonce 重复通知时幂等返回且不重复写终态日志；相反终态失败闭合。该契约不等同真实渠道通知格式、到账或生产联调已完成。

### 14.62 退款人工主动查单接口契约

`POST /api/refunds/query`

请求体：

```json
{
  "refundOrderId": "REF202609101030000001"
}
```

1. 接口仅查询已绑定 `channelCode/channelRefundNo` 且状态为 `PROCESSING` 的退款；`SUCCESS/FAIL` 直接返回最新退款摘要，不重复调用渠道。
2. 渠道结果必须包含 `refundStatus/channelRefundNo/querySource`，状态只允许 `PROCESSING/SUCCESS/FAIL`；字段超长、含控制字符、为空或渠道退款号与已受理事实不一致时返回 `PAYMENT-1046`。
3. `PROCESSING` 只写 `CHANNEL_QUERY` 操作日志并保持状态；明确 `SUCCESS/FAIL` 在锁内复核关联键后通过 CAS 收口并写 `CHANNEL_QUERY_SUCCESS/FAIL`。
4. 未找到可用适配器返回 `PAYMENT-1045`。默认本地兜底只回显现状，`gateway-access` HTTP 适配器默认关闭；启用时必须同时配置应用编码、访问凭证、真实退款上游和允许渠道。

### 14.63 退款批量主动查单任务接口契约

`POST /api/payment-task-center/query-pending-refunds`

1. 人工任务只选择已绑定 `channelCode/channelRefundNo/channelSubmittedAt`、状态为 `PROCESSING` 且达到首次等待阈值的退款；候选受总批次与单渠道批次限制，但人工执行不受自动重试退避阻断。
2. 自动任务使用相同状态与首次等待边界，并仅根据退款操作日志中的 `CHANNEL_QUERY/CHANNEL_QUERY_FAILED` 计算指数退避；其他退款操作不得增加查单退避次数。
3. 自动调度必须同时开启 `payment.refund-query.compensation.enabled` 与 `payment.refund-query.gateway-access.enabled`。两者默认关闭，禁止使用只回显本地状态的兜底适配器制造自动查单事实。
4. 返回沿用任务中心 `PaymentTaskActionResultDTO`，记录处理数、成功收口数、仍待跟进数、查询失败数和最多三个失败退款单号；逐笔失败隔离并统一写 `REFUND_PENDING_QUERY` 任务日志。
5. 退款共享固定窗口限流按 14.64 节执行；接口存在不代表真实 `gateway-access` 退款查询、渠道到账或生产运行已经完成。

### 14.64 退款渠道查单共享固定窗口限流契约

1. `payment.refund-query.rate-limit.enabled` 与 `payment.refund-query.gateway-access.enabled` 必须同时为 `true` 才启用；任一关闭时不占用数据库桶，默认配置不会限制本地兜底回显。
2. 每次退款渠道调用前按规范化 `channelCode` 原子占用 `t_refund_channel_query_rate_bucket`；支付与退款使用独立桶，不在缺少真实渠道总额度依据时互相挤占。
3. 当前窗口额度耗尽返回 `PAYMENT-1047`，并向 `t_refund_channel_query_rate_limit_log` 写入拒绝流水号、渠道、退款单号、窗口秒数、最大次数和数据库拒绝时间。
4. 拒绝发生在渠道调用及 `CHANNEL_QUERY/CHANNEL_QUERY_FAILED` 前，不计入退款自动退避历史；任务中心将支付与退款拒绝合并统计并沿用主动查单限流告警阈值。

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `HSP_PAYMENT_REFUND_QUERY_RATE_LIMIT_ENABLED` | `false` | 退款渠道查单共享限流开关 |
| `HSP_PAYMENT_REFUND_QUERY_RATE_LIMIT_WINDOW_SECONDS` | `60` | 固定时间窗秒数，最小归一化为 `1` |
| `HSP_PAYMENT_REFUND_QUERY_RATE_LIMIT_MAX_QUERIES_PER_CHANNEL` | `100` | 单渠道每窗最大退款查单次数，最小归一化为 `1` |

### 14.65 支付与退款主动查单统一监控契约

1. 任务中心总览的 `recentQueryCount/recentQueryFailureCount/recentQueryFailureRate/queryTrends` 同时覆盖支付和退款主动查单，并沿用 `queryMonitorWindowHours` 时间窗。
2. 支付查询事实来自通知日志状态 `已查单/查单失败`；退款查询事实来自操作日志动作 `CHANNEL_QUERY/CHANNEL_QUERY_SUCCESS/CHANNEL_QUERY_FAIL/CHANNEL_QUERY_FAILED`。
3. 失败数只包含支付 `查单失败` 与退款 `CHANNEL_QUERY_FAILED`。`CHANNEL_QUERY_FAIL` 表示可信查询得到退款失败终态，不是查询调用失败；`PAYMENT-1047` 限流拒绝通过独立指标统计。
4. 支付与退款当前共用查询失败次数、最小样本、失败率和限流拒绝阈值，并复用既有监控 outbox；接口暂不返回按业务类型拆分的趋势或阈值。

### 14.66 退款主动查单失败收口契约

1. 渠道适配器业务异常、未预期运行时异常、非法结果和渠道退款号不一致统一返回退款查单失败业务码 `PAYMENT-1046`；未预期异常不会以通用 `PAYMENT-500` 直接穿透。
2. 上述失败均写最小化 `CHANNEL_QUERY_FAILED` 操作日志，供自动退避和任务中心失败统计使用；日志与受控响应不包含网关原始异常消息。
3. 限流拒绝 `PAYMENT-1047` 在外部调用前独立返回且不写失败历史；可信查询得到退款失败终态仍按 `CHANNEL_QUERY_FAIL` 收口退款状态，不属于查询调用失败。

### 14.67 支付主动查单可信成功与异常收口契约

1. 支付查单适配器业务异常、非法响应和未预期运行时异常统一返回 `PAYMENT-1026` 并写 `查单失败` 通知事实；受控响应和通知日志不包含远端异常原文。
2. 对当前非 `SUCCESS` 支付单，渠道查询返回 `SUCCESS` 时必须能从查询结果或锁后最新支付事实取得渠道流水号，否则返回 `PAYMENT-1026`，且不写返回码快照或“已查单”成功事实。
3. 渠道成功结果触发订单协调锁和支付单重读。若回调已先写入 `SUCCESS + channelTransactionNo`，查单返回最新成功详情并记录普通查询，不再次更新支付、订单、账单或发布成功事件。

### 14.68 支付回调前端调用边界契约

1. `POST /api/payments/callback/{channel}` 继续作为真实渠道签名通知入口，服务端仍按渠道、签名、时间窗、nonce、支付单和渠道交易号执行可信校验；本次前端收口不改变该接口的服务端契约。
2. App/H5/PC 和管理端不再封装或调用该接口，也不构造 `tradeStatus=SUCCESS`、模拟渠道交易号或提供模拟回调按钮；浏览器不能作为渠道通知发起方人工制造支付终态。
3. 用户端和运营端对处理中支付只提供详情刷新、主动查单和轨迹排查；只有确认未提交渠道且无渠道流水/支付尝试的支付单保留受限本地关单能力。
4. 删除前端调用面不代表真实渠道回调已联通或验收。测试、预发和生产仍必须配置渠道独立凭证，并以真实协议报文验证签名、关联键、幂等和资金事实。

### 14.69 本地模拟支付适配器环境与提交预检契约

1. `WechatH5/AlipayH5/OfflineBank/Local` 提交和查单适配器只在 Spring `demo` profile 注册；默认、测试、预发和生产 profile 不得依赖这些适配器生成渠道流水或回显本地状态。
2. 非 demo 支付提交在路由、控制策略、风控和幂等复核后调用 `ensureAdapterAvailable(channelCode)`，并在更新支付单、预付单、路由、尝试或提交并发令牌前完成。无适配器返回 `PAYMENT-1012`，原支付事实保持不变。
3. 已有相同幂等键的支付尝试仍直接复用当前预付结果，不因当前适配器配置变化重复下单；风控非 `PASS` 仍优先按风控状态收口，不进入渠道适配器预检。
4. 非 demo 主动查单只有已启用且支持目标渠道的真实查询适配器可执行；缺少适配器返回 `PAYMENT-1004`，不得降级为数据库状态回显。当前默认配置尚不代表真实 gateway-access 已实现或联通。

### 14.70 gateway-access 支付提交代理契约

1. `payment.submit.gateway-access.enabled=false` 为默认值；访问密钥为空、提交 URL 为空或目标渠道不在允许列表时，适配器不声明支持，主链路沿用 `PAYMENT-1012` 失败闭合且不写渠道提交事实。
2. 显式启用并配置完整后，支付核心携带 `X-Gateway-App-Code / X-Gateway-Access-Key` 请求头，将稳定支付单、预付单、订单、金额、路由渠道、终端、客户端 IP 和幂等键提交到 `POST /api/gateway/payment-orders/submit`。
3. 只有网关业务码为 `0`，且响应同时包含 `channelTransactionNo / attemptStatus / attemptStatusType / channelReturnCode` 时才接受结果；支付核心只保存渠道编码、渠道交易号和返回码组成的最小响应留痕，不保存共享访问密钥或网关原始报文。
4. 网关拒绝、超时、空响应和字段非法统一返回 `PAYMENT-1048`。由于外部渠道可能已经受理，既有主流程会把已准备提交的支付尝试标记为“结果未知，待查单”，禁止自动重复下单。
5. 关键配置为 `HSP_PAYMENT_SUBMIT_GATEWAY_ENABLED / HSP_PAYMENT_SUBMIT_GATEWAY_URL / HSP_PAYMENT_SUBMIT_GATEWAY_SUPPORTED_CHANNELS / HSP_PAYMENT_SUBMIT_GATEWAY_APP_CODE / HSP_PAYMENT_SUBMIT_GATEWAY_ACCESS_KEY / HSP_PAYMENT_SUBMIT_GATEWAY_TIMEOUT_MS`；开关默认关闭，访问密钥默认空。
6. 当前本地受控上游验证只证明 payment-core 与 gateway-access 的稳定 HTTP 代理契约，不代表微信、支付宝或银行真实协议、签名验签、生产密钥托管、来源 IP 白名单、灰度发布、资金到账、对账或财务验收已经完成。

### 14.71 gateway-access 退款查单代理契约

1. `payment.refund-query.gateway-access.enabled=false` 为默认值；查询 URL、应用编码、访问密钥或目标渠道配置不完整时，适配器不声明支持，退款查单返回既有 `PAYMENT-1045`，不降级为本地状态回显。
2. 配置完整后，payment-core 携带 `X-Gateway-App-Code / X-Gateway-Access-Key`，向 `POST /api/gateway/refunds/query` 发送 `refundOrderId / channelCode`；已有受理事实时同时发送 `channelRefundNo`，提交事件为 `UNKNOWN` 且尚无受理号时允许省略。默认地址为 `http://127.0.0.1:8090/api/gateway/refunds/query`，不再指向 reconciliation-system 的 `18150`。
3. gateway-access 在转发前复用应用、凭证、权限、网关、有效证书和发布路由门禁；微信、支付宝和银行退款查单上游 URL 默认均为空，未显式配置时失败闭合。
4. 只有网关业务码为 `0`，且结果包含 `refundStatus / channelRefundNo / querySource` 时才接受；状态只允许 `PROCESSING / SUCCESS / FAIL`。已有受理号时必须完全一致；未知提交无受理号时，返回号只允许在对应事件仍为 `UNKNOWN` 时首次绑定。失败统一沿用 `PAYMENT-1046` 并写最小失败历史。
5. payment-core 配置项为 `HSP_PAYMENT_REFUND_QUERY_GATEWAY_ENABLED / HSP_PAYMENT_REFUND_QUERY_GATEWAY_URL / HSP_PAYMENT_REFUND_QUERY_SUPPORTED_CHANNELS / HSP_PAYMENT_REFUND_QUERY_GATEWAY_APP_CODE / HSP_PAYMENT_REFUND_QUERY_GATEWAY_ACCESS_KEY / HSP_PAYMENT_REFUND_QUERY_TIMEOUT_MS`；gateway-access 上游配置为 `HSP_GATEWAY_WX_REFUND_QUERY_URL / HSP_GATEWAY_ALIPAY_REFUND_QUERY_URL / HSP_GATEWAY_BANK_REFUND_QUERY_URL`。
6. 本地受控上游冒烟只证明稳定 HTTP 转发、凭证拒绝和退款号关联校验，不代表真实退款渠道协议、签名验签、生产密钥托管、来源 IP 白名单、退款到账、对账或财务验收完成。

### 14.72 gateway-access 退款提交代理契约

1. 非 `demo` 环境的退款下发使用 `GatewayAccessRefundChannelSubmitServiceImpl`；`LocalRefundChannelSubmitServiceImpl` 只在 `demo` profile 注册，默认环境不再生成模拟渠道退款号。
2. `payment.refund-submit.gateway-access.enabled=false` 为默认值。开关、提交 URL、应用编码、访问密钥或渠道允许列表任一不完整时返回 `PAYMENT-1049`，退款保持 `PROCESSING`，既有 `REFUND_SUBMIT` outbox 进入可退避的失败/死信核查路径。
3. 配置完整后，payment-core 携带 `X-Gateway-App-Code / X-Gateway-Access-Key`，向 `POST /api/gateway/refunds/submit` 发送退款单、原支付单、原订单、退款金额、方式、原因与原支付渠道。gateway-access 复用应用、权限、网关、有效证书和发布路由门禁。
4. 只有网关业务码为 `0`，且结果包含 `channelRefundNo / status / statusType` 时才接受；状态只允许 `PROCESSING / SUCCESS`。payment-core 只保存渠道、渠道退款号和来源组成的最小留痕，不保存访问密钥或上游原始报文。
5. 明确发生在上游调用前的网关配置或治理拒绝按 `PAYMENT-1049` 进入失败退避；超时、空响应、未知业务码或受理字段非法表示结果未知，事件进入 `UNKNOWN` 并停止直接重试。外部渠道可能已经受理时不得据此自动重复退款。
6. payment-core 配置为 `HSP_PAYMENT_REFUND_SUBMIT_GATEWAY_ENABLED / HSP_PAYMENT_REFUND_SUBMIT_GATEWAY_URL / HSP_PAYMENT_REFUND_SUBMIT_SUPPORTED_CHANNELS / HSP_PAYMENT_REFUND_SUBMIT_GATEWAY_APP_CODE / HSP_PAYMENT_REFUND_SUBMIT_GATEWAY_ACCESS_KEY / HSP_PAYMENT_REFUND_SUBMIT_TIMEOUT_MS`；gateway-access 上游配置为 `HSP_GATEWAY_WX_REFUND_SUBMIT_URL / HSP_GATEWAY_ALIPAY_REFUND_SUBMIT_URL / HSP_GATEWAY_BANK_REFUND_SUBMIT_URL`，均默认失败闭合。
7. 本地固定响应 stub 只证明稳定 HTTP 转发、错误凭证拒绝和关联字段传递，不代表真实退款协议、签名验签、渠道幂等、生产凭证、退款到账、对账或财务验收完成。

### 14.73 退款提交结果未知事件契约

1. `REFUND_SUBMIT` 请求可能已到达上游，但因网络异常、未知网关业务码、非法受理响应或本地受理事实绑定失败无法确认结果时，事件写为 `publishStatus=UNKNOWN`、`publishStatusType=warn`，`nextRetryAt` 清空；退款单保持 `PROCESSING`。
2. `UNKNOWN` 不是退款成功或失败终态，只表示提交受理事实未知。自动任务与批量失败重试均不选择该状态，管理端禁用直接重发并提示按退款单号向渠道查单或人工核查。
3. 对没有可信渠道受理号的 `UNKNOWN` 事件调用单笔重发接口返回 `PAYMENT-1050`，不得再次调用退款渠道。该错误码表示必须先核查，不表示退款失败。
4. 若可信核查已为原退款单补录渠道退款号和一致的提交渠道，再次处理事件只校验受理事实并将事件收敛为 `SUCCESS`，不重复提交退款；退款终态仍由可信回调或主动查单单向收口。
5. 操作日志仅记录固定处置语义，不保存异常原文、访问凭证或完整渠道报文。当前实现不替代真实渠道按退款单号查询、人工审批、到账核验或财务验收。

### 14.74 退款提交结果未知异常与告警契约

1. `eventType=REFUND_SUBMIT` 且 `publishStatus=UNKNOWN` 的事件映射为独立异常类型“退款提交结果待核查”，严重等级固定为 `P1`；它不属于可重发的“下游事件发布失败”。
2. 异常编号稳定使用 `ISSUE-REFUND-SUBMIT-UNKNOWN-{eventNo}`，摘要包含退款单号、事件号和有限重试次数，处置入口跳转至支付事件页的 `UNKNOWN` 筛选，不展示异常原文或渠道完整报文。
3. 任务中心返回 `unknownRefundSubmitEventCount` 并生成 `REFUND_SUBMIT_UNKNOWN` 重点告警。存在任意一笔时即显示 P1，提示先按退款单号查单或人工核查、禁止直接重发。
4. 未知事件超过 30 分钟进入异常 SLA 超时统计和告警候选；默认责任组为账务清结算联动组，初始化及幂等迁移提供全时段 P1 值班路由。已有待确认告警继续按异常编号防重复生成。
5. 本地异常与 outbox 仅证明告警候选和派发入口存在；真实 IM、短信、邮件送达、排班身份、生产升级流程、退款到账和财务核验仍需外部环境验证。

支付详情和路由执行结果会返回 `opsConfigSnapshotVersion`。当支付提交请求缺少支付方式或渠道并从
`ops-config-system` 补齐配置时，该字段记录实际消费的配置快照版本；显式传入全部路由输入时可以为空。

当前版本重点仍是开发与联调基线，但支付主链路已经补齐第一版业务错误码体系，前端可直接展示 `code + requestId` 做问题定位。

| 错误码 | 当前含义 |
| --- | --- |
| `PAYMENT-400` | 通用参数校验失败 |
| `PAYMENT-500` | 通用系统异常 |
| `PAYMENT-1001` | 预付单不存在 |
| `PAYMENT-1002` | 支付单不存在 |
| `PAYMENT-1003` | 当前支付方式无法命中可用路由渠道 |
| `PAYMENT-1004` | 未找到可用查单适配器 |
| `PAYMENT-1005` | 回调签名/nonce 缺失、超限、含控制字符或验签失败 |
| `PAYMENT-1006` | 回调密钥未配置 |
| `PAYMENT-1007` | 回调 `nonce` 重放 |
| `PAYMENT-1008` | 回调时间戳非法或超出时间窗 |
| `PAYMENT-1009` | 验签入口回调渠道缺失或超过安全长度 |
| `PAYMENT-1010` | 当前没有启用的支付渠道 |
| `PAYMENT-1011` | 支付来源订单金额信息缺失 |
| `PAYMENT-1016` | 支付提交仍在处理中，或关单遇到同支付单活跃提交令牌；应刷新核对原支付事实，不能凭此断言渠道已受理并直接查单 |
| `PAYMENT-1026` | 外部渠道查单失败，结果为空、字段非法/不完整或无法安全收口 |
| `PAYMENT-1027` | 当前渠道共享查单时间窗配额已耗尽 |
| `PAYMENT-1028` | 支付提交最终幂等键为空、超过 128 字符、包含控制字符或无法编码为有效 UTF-8 |
| `PAYMENT-1029` | 支付已提交渠道或结果尚未确认，不能本地关闭；具备渠道受理事实时需先主动查单 |
| `PAYMENT-1030` | 回调 URL 渠道与支付单实际路由渠道不一致或支付单尚未绑定渠道 |
| `PAYMENT-1031` | 回调渠道交易号与支付单已绑定的渠道交易号不一致 |
| `PAYMENT-1032` | 渠道回调签名算法缺失或不在运行时白名单 |
| `PAYMENT-1033` | 渠道 RSA2 公钥材料存在但无法解析 |
| `PAYMENT-1034` | 渠道回调验签窗口为空/非正，或 nonce TTL 未严格覆盖完整签名有效期 |
| `PAYMENT-1035` | 支付提交请求为空，业务字段缺失、超长、含控制字符，或终端不在有限白名单 |
| `PAYMENT-1036` | 支付通用入口或预付请求为空，标识/业务字段缺失、超长、含控制字符，或金额格式越界 |
| `PAYMENT-1037` | 订单已结清或已超额支付，无需再次创建支付单 |
| `PAYMENT-1038` | 同一订单已有有效预付单，但本次支付场景、客户、金额或收银台标题不一致 |
| `PAYMENT-1068` | 预付单已过期且尚未提交渠道，需重新发起支付 |
| `PAYMENT-1069` | 支付提交幂等键属于其他支付单、同键请求内容不一致，或历史尝试缺少可核对的请求摘要 |
| `PAYMENT-1039` | 退款回调业务字段缺失、超长或含控制字符 |
| `PAYMENT-1040` | 退款回调渠道与已受理渠道不一致 |
| `PAYMENT-1041` | 退款回调渠道退款号与已受理退款号不一致 |
| `PAYMENT-1042` | 退款回调状态非法或退款当前状态不可收口 |
| `PAYMENT-1043` | 退款回调目标与既有退款终态冲突 |
| `PAYMENT-1044` | 退款单不存在 |
| `PAYMENT-1045` | 未找到可用退款渠道查单适配器 |
| `PAYMENT-1046` | 退款渠道查单失败、结果非法/不完整、关联键不一致或退款尚未具备查单条件 |
| `PAYMENT-1047` | 当前退款渠道共享查单固定窗口配额已耗尽 |
| `PAYMENT-1048` | gateway-access 支付提交失败或未返回可确认的渠道受理事实；必须先查单，禁止自动重提 |
| `PAYMENT-1049` | gateway-access 退款提交未配置、调用失败或未返回可确认的渠道受理事实；保持处理中并先查单核验 |
| `PAYMENT-1050` | 退款提交受理结果未知，必须先向渠道查单或人工核查，禁止直接重发 |
| `PAYMENT-1051` | 告警供应商回执查询的目标通道共享固定窗口额度已耗尽，本条不调用通知集成网关 |
| `PAYMENT-1052` | 退款申请幂等键为空、超长、包含 ISO 控制字符或无法编码为有效 UTF-8，服务端在 Mapper 访问前拒绝 |
| `PAYMENT-1053` | 同一支付单复用退款申请幂等键但规范化业务内容不一致 |
| `PAYMENT-1054` | 支付单没有可供回调或主动查单使用的渠道提交事实 |

### 14.75 退款提交未知结果按平台退款单号核查契约

1. 退款为 `PROCESSING`、已绑定渠道且存在对应 `REFUND_SUBMIT/UNKNOWN` 事件时，即使尚无 `channelRefundNo`，`POST /api/refunds/query` 也允许进入既有共享限流后按平台 `refundOrderId` 查询；普通待下发或缺少未知事件的退款仍返回 `PAYMENT-1046`，不调用渠道。
2. gateway-access 的 `channelRefundNo` 请求字段在此场景可省略，但成功响应仍必须包含规范化后的渠道退款号和有限退款状态。若本地已有受理号，响应必须严格一致；未知场景只允许首次绑定，不能覆盖既有事实。
3. 可信查单结果或通过既有验签的退款回调均可恢复未知受理事实。服务在锁定退款行后再次确认事件为 `UNKNOWN`，条件绑定渠道、收敛事件为 `SUCCESS` 并写固定最小操作日志，再沿用既有退款状态机处理 `PROCESSING / SUCCESS / FAIL`。
4. 绑定或未知事件收敛任一步未准确影响一行时按内部一致性失败回滚，不把部分恢复结果留在数据库。失败日志和审计不保存渠道原始报文、访问凭证或异常原文。
5. 本契约只证明内部系统允许按稳定平台退款单号代理核查；真实微信、支付宝或银行是否支持该查询键、返回字段含义、签名验签、资金到账和对账结果仍需渠道联调验证。

### 14.76 退款提交未知结果批量补查契约

1. `POST /api/payment-task-center/query-pending-refunds` 的人工候选，以及 `REFUND_PENDING_QUERY` 自动任务的到期候选，均允许选择 `PROCESSING + 已绑定渠道 + channelRefundNo 为空 + 最新 REFUND_SUBMIT/UNKNOWN` 的退款；无未知事件、事件已收敛或未绑定渠道的退款不入选。
2. 未知退款以 `COALESCE(lastPublishedAt, createdAt)` 作为首次等待基线，达到 `payment.refund-query.compensation.min-age-seconds` 后才允许批量查询；已有渠道退款号的退款继续以 `channelSubmittedAt` 为基线。
3. 未知退款查询失败会写既有 `CHANNEL_QUERY_FAILED` 最小日志，后续自动候选与普通退款共用 `retry-interval-seconds / retry-backoff-factor / max-retry-interval-seconds` 指数退避；人工候选不绕过首次等待，但不受自动退避间隔限制。
4. 未知退款与普通退款按 `channelCode` 共用 `per-channel-batch-size`，并在逐笔查询时继续进入数据库共享固定窗口限流；总批次仍受 `batch-size` 限制，不因缺少渠道退款号扩大单轮渠道压力。
5. 自动调度仍只有在 `payment.refund-query.compensation.enabled` 与 `payment.refund-query.gateway-access.enabled` 同时开启时执行，默认均为关闭。开关只代表允许调用已配置代理，不证明真实渠道支持平台退款单号查询；不支持的渠道必须失败闭合并转人工核查。

### 14.77 本地退款查单适配器环境隔离契约

1. `LocalRefundChannelQueryAdapter` 只在 Spring `demo` profile 注册，可用于本地演示回显；默认、测试、预发和生产环境不再把数据库中的退款状态与渠道退款号包装成外部查询结果。
2. 非 demo 环境仅由明确启用、配置完整且支持目标渠道的 gateway-access 适配器承接退款查单。没有可用适配器时返回 `PAYMENT-1045`，不得降级为 `LOCAL_SIMULATION_FALLBACK`。
3. `PAYMENT-1045` 仍进入既有最小化 `CHANNEL_QUERY_FAILED` 记录、任务失败样例和自动退避；它只表示没有可信查询能力，不改变退款状态、渠道退款号或未知提交事件。
4. 自动补偿继续要求补偿与 gateway-access 查询双开关同时开启；即使错误开启补偿但真实适配器不支持某渠道，也只能失败闭合，不能由本地适配器制造查询成功。
5. profile 隔离不证明 gateway-access 已配置真实渠道协议、凭证、签名或生产网络，也不构成退款到账、对账或财务验收结论。

### 14.78 告警通知器空网关失败闭合契约

1. IM、SMS、EMAIL 三类通知器在 Webhook URL 为空时默认返回 `providerDeliveryStatus=FAILED`，`providerReceiptSnapshot=CONFIGURATION_ERROR:WEBHOOK_URL_MISSING`，且 `providerReceiptNo` 为空；不得把配置缺失记录为供应商已受理。
2. 派发服务沿用现有失败判定，将上述结果写为“派发失败”并继续既有候选供应商处理，不更新为“已派发”，也不生成任何外部供应商回执号。
3. 本地模拟回执必须同时满足 Spring `demo` profile 已激活以及 `payment.issue-alert.local-receipt-enabled=true`；默认配置固定为 `false`，demo 配置显式设为 `true`。仅设置属性但未激活 demo 仍失败闭合。
4. Webhook URL 已配置时继续执行既有认证、签名、超时、业务成功码、回执号、投递状态、失败码解析与敏感字段脱敏，不因 demo 门禁改变真实 HTTP 调用路径。
5. demo 返回的 `LOCAL:<CHANNEL>:ACCEPTED` 只用于页面和流程演示，不构成真实 IM、短信或邮件供应商受理、送达、人员响应或生产稳定性证据。

### 14.79 告警供应商受理证据与回执号契约

1. Webhook 返回 HTTP `2xx` 只证明传输层收到响应，不能单独生成 `ACCEPTED`。通知器必须取得已识别的投递状态，或同时配置成功码 JSON Pointer 和期望值且响应完全匹配，才可确认业务受理。
2. 已识别状态按既有有限集合归一化为 `DELIVERED / ACCEPTED / FAILED`；空状态在成功码已明确匹配时可归为 `ACCEPTED`，否则归为 `FAILED`。未知状态即使成功码匹配也失败闭合，不推测供应商语义。
3. 成功码 Pointer 与期望值必须同时配置或同时留空，只配置一项返回 `FAILED` 并记录“成功码校验配置不完整”。HTTP 非 `2xx` 或成功码不匹配继续强制覆盖为 `FAILED`。
4. `providerReceiptNo` 只保存供应商响应中按已配置 Pointer 解析出的非空值。未配置 Pointer、响应字段缺失或传输异常时保持为空，不再根据告警号生成看似供应商回执的本地编号。
5. 空 HTTP 响应体使用 `HTTP_RESPONSE:EMPTY` 作为稳定快照；该分类与失败状态只证明本地调用结果，不证明供应商受理、最终送达或人工响应。

### 14.80 告警供应商最终送达回执查询契约

1. `PaymentIssueAlertReceiptQueryAdapter` 是按通知通道扩展的供应商回执查询接口。回查服务必须找到支持当前 `alertChannel` 的适配器并取得明确 `providerDeliveryStatus=DELIVERED`，才允许把既有 `ACCEPTED` 投递更新为已送达并确认来源告警。
2. 没有适配器、查询返回空值、状态仍为 `ACCEPTED`、状态为 `FAILED`、状态未知、查询异常或既有回执号与查询回执号冲突时，本次回查计为失败且不更新投递日志和来源告警；不得再由本地定时任务直接推导送达。
3. 明确送达时保存查询适配器返回的脱敏回执快照、供应商回执号和说明；查询未返回新快照或回执号时保留已存在的受理证据。回执号冲突必须失败闭合，不能覆盖原供应商关联键。
4. 自动回查由 `payment.issue-alert.receipt-reconcile.enabled` 控制，对应环境变量为 `HSP_PAYMENT_ISSUE_ALERT_RECEIPT_RECONCILE_ENABLED`，默认值为 `false`。手工回查即使被调用，也仍受真实查询适配器和明确送达结果约束。
5. 当前代码只提供查询扩展点，尚未实现任何 IM、SMS 或 EMAIL 真实供应商回执查询适配器。该契约和本地自动化测试不证明供应商最终送达、值班人员收到或生产链路已经验收。

### 14.81 告警回执查询通知集成网关客户端契约

1. `HttpPaymentIssueAlertReceiptQueryAdapter` 定义 payment-core 到“通知集成网关”的稳定 HTTP 客户端契约；只有 `enabled`、查询 URL、应用编码、访问密钥和目标通道允许列表全部配置有效时，才声明支持该通道。
2. 客户端携带 `X-Gateway-App-Code / X-Gateway-Access-Key` 请求头，以 `POST` 向配置地址发送 `alertNo / issueNo / alertChannel / providerCode / providerReceiptNo`；必填字段、可选字段、长度和 ISO 控制字符均在网络调用前校验。
3. 只有响应业务码为 `0`、`data` 非空且 `providerDeliveryStatus` 属于 `DELIVERED / ACCEPTED / FAILED` 有限集合时才接受。空响应、失败业务码、未知状态、非法字段和传输异常统一失败闭合，不泄露远端异常原文。
4. 返回的供应商回执快照在保存前经 `PaymentSensitiveDataMasker` 脱敏；客户端强制清空 `renderedContentSnapshot`，回执查询不得覆盖原告警正文。回执号仍由回查服务执行既有关联一致性校验。
5. 自动回查必须同时开启 `HSP_PAYMENT_ISSUE_ALERT_RECEIPT_RECONCILE_ENABLED` 与 `HSP_PAYMENT_ISSUE_ALERT_RECEIPT_QUERY_GATEWAY_ENABLED`。查询 URL、应用编码、访问密钥或通道配置任一缺失时不生效，默认两项开关均关闭。
6. 当前仓库没有该“通知集成网关”的服务端实现，也没有任何 IM、SMS 或 EMAIL 真实供应商回执查询实现。本契约及本地固定响应测试不证明真实供应商最终送达、人员响应、生产网络或稳定性验收完成。

### 14.82 告警供应商回执查询共享限流契约

1. `PaymentIssueAlertReceiptQueryRateLimitService` 在已找到支持目标通道的查询适配器后、发起 HTTP 前占用共享额度；无适配器时不消耗额度，限流拒绝时不调用通知集成网关。
2. 限流桶按规范化后的 `IM / SMS / EMAIL` 通道区分，所有 payment-core 实例通过数据库通道唯一键共享固定窗口。配置为 `HSP_PAYMENT_ISSUE_ALERT_RECEIPT_QUERY_RATE_LIMIT_ENABLED / WINDOW_SECONDS / MAX_QUERIES_PER_CHANNEL`，默认关闭、默认 `60` 秒 `100` 次。
3. 限流只有在自身开关与 `HSP_PAYMENT_ISSUE_ALERT_RECEIPT_QUERY_GATEWAY_ENABLED` 同时开启时生效；窗口和额度配置小于 `1` 时收敛为 `1`，防止非法配置绕过保护。
4. 当前窗口额度耗尽时返回内部业务错误 `PAYMENT-1051`，本条回查计为失败且不更新投递日志或来源告警；批次继续处理后续项目。拒绝事实以独立新事务保存通道、告警号和窗口配置，不写供应商报文、凭证或异常原文。
5. 数据库固定窗口实现只提供跨实例原子占额基线，存在窗口边界突发特性。本地 H2/MySQL 兼容测试不证明生产 MySQL 锁竞争、真实网关容量、供应商配额或稳定性已经验证。

### 14.83 告警供应商回执回查退避与占用契约

1. 自动回查只选择已保持 `ACCEPTED` 至少 `min-age-seconds`、未达到 `max-attempts` 且 `next_receipt_query_at` 已到期的外部通道日志；单轮最多处理 `batch-size` 条。人工回查使用相同批次上限，但允许越过自动退避时间和自动次数上限。
2. 每次调用适配器前必须按 `alertNo + receiptQueryCount` 原子 claim：次数加一，结果写为 `QUERYING`，并设置占用超时时间。候选版本已变化、仍在有效占用期或状态不再为 `ACCEPTED` 时 claim 失败，本条不调用适配器和限流服务。
3. 未送达结果只保存有限分类 `ADAPTER_MISSING / RATE_LIMITED / QUERY_ERROR / EMPTY / RECEIPT_CONFLICT / ACCEPTED / FAILED`，不保存异常原文；下一次自动时间按 `retry-interval-seconds * retry-backoff-factor^历史次数` 计算并由 `max-retry-interval-seconds` 封顶。
4. 失败完成与 `DELIVERED` 回写都必须匹配本次 claim 后的次数及 `QUERYING` 状态，过期执行者不能覆盖后继实例。达到自动次数上限后不再设置自动重试时间，人工核查仍可重新 claim。
5. 配置环境变量为 `HSP_PAYMENT_ISSUE_ALERT_RECEIPT_QUERY_MIN_AGE_SECONDS / RETRY_INTERVAL_SECONDS / RETRY_BACKOFF_FACTOR / MAX_RETRY_INTERVAL_SECONDS / MAX_ATTEMPTS / BATCH_SIZE / CLAIM_TIMEOUT_SECONDS`，默认依次为 `60 / 60 / 2 / 900 / 10 / 100 / 30`，非正值收敛为 `1`。
6. 本地自动化只证明候选 SQL、CAS、有限失败状态和退避计算；不证明生产 MySQL 在线迁移、锁等待、真实供应商查询节奏、最终送达或稳定性通过。

### 14.84 任务中心告警回执回查队列指标契约

1. `GET /api/admin/payment-tasks/overview` 在既有 `pendingIssueAlertReceiptCount` 和最老等待分钟数之外，新增 `dueIssueAlertReceiptCount / backingOffIssueAlertReceiptCount / queryingIssueAlertReceiptCount / exhaustedIssueAlertReceiptCount` 四项整数指标。
2. 四项指标只统计外部通知通道、投递状态为“已派发”、供应商状态为 `ACCEPTED` 且仍待确认的日志，并复用回查任务的首次等待、最大次数和 claim 超时配置，前端不得自行按时间计算。
3. “可执行”表示已过首次等待、未耗尽次数、下一次时间已到且不存在有效 claim；过期 `QUERYING` claim 重新归入可执行。“退避中”表示下一次时间晚于当前时间且不存在有效 claim；“查询中”只表示 claim 尚未超时；“自动次数耗尽”排除仍在执行的人工 claim。
4. 首次等待窗口内的记录仍计入待回查总数，但不计入四个执行状态；因此页面不得假设四项之和恒等于总数。自动次数耗尽不关闭人工核查入口，也不表示供应商已失败或最终送达。
5. 这些指标只反映 payment-core 数据库中的任务调度状态，不证明通知集成网关可用、真实供应商已受理或送达、人员已响应或生产稳定性通过。

### 14.85 告警明细回执回查钻取契约

1. `GET /api/payment-issues/alerts` 与 `GET /api/payment-issues/alerts/export` 新增可选参数 `receiptQueryState`；允许值为 `全部 / PENDING_RECEIPT / INITIAL_WAIT / DUE / BACKING_OFF / QUERYING / EXHAUSTED`，英文值忽略大小写，未知值返回参数错误，不能静默退化为全量查询。`PENDING_RECEIPT` 只用于查询全部待回查集合，不作为行级状态返回。
2. 五类状态由服务端使用与补偿任务同源的首次等待、最大次数和 claim 超时配置计算。有效 claim 优先归为 `QUERYING`，其后依次判定 `EXHAUSTED`、`INITIAL_WAIT`、`BACKING_OFF` 和 `DUE`；筛选谓词与返回状态保持互斥，首次等待且 next time 在未来的记录仍只归为 `INITIAL_WAIT`。
3. 列表行新增 `receiptQueryState / receiptQueryCount / lastReceiptQueryAt / nextReceiptQueryAt / receiptQueryResult`；CSV 在供应商回执号后按同一顺序输出回查队列状态、次数、最近时间、下次时间和有限结果，不导出异常原文或访问凭证。
4. 任务中心发现 `exhaustedIssueAlertReceiptCount > 0` 时，“异常告警待回查”强制提升为 `P1/danger`，并跳转 `/payment-issue-alerts?receiptQueryState=EXHAUSTED`；无耗尽记录时跳转 `/payment-issue-alerts?receiptQueryState=PENDING_RECEIPT`。该升级只表示自动尝试耗尽，不表示供应商失败或最终送达。
5. 人工确认回执后，前端必须使用当前筛选重新加载列表，避免已不满足待确认或耗尽条件的记录残留。接口与本地测试不证明真实供应商查询、通知送达、人员响应或生产稳定性。

### 14.86 单条告警回执人工回查契约

1. `POST /api/payment-task-center/reconcile-issue-alert-receipts/{alertNo}` 对指定外部通道告警执行一次人工回执回查；路径参数按 URL 组件编码，服务端去除首尾空白，空编号或不存在、不再待确认、非已派发、非 `ACCEPTED` 的记录返回参数错误且不得外呼。
2. 单条入口复用批量人工回查的同一执行链路和任务码，可越过自动首次等待、退避时间与最大次数，但不能绕过基于历史 `receiptQueryCount` 的 CAS claim、有效 `QUERYING` 占用、共享通道限流、适配器能力判断、回执号一致性校验或有限失败结果。
3. 人工与自动候选、claim、失败完成和明确送达回写均要求 `ack_status='待确认'`。回查在途时若运营已人工确认，过期执行者不能再写失败分类、改成 `DELIVERED` 或覆盖确认审计。
4. 返回继续使用 `PaymentTaskActionResultDTO`：明确送达计成功，未送达、限流、适配器缺失、查询异常、空结果或回执号冲突计失败并返回有限摘要；同时记录任务运行日志并刷新 overview，推荐路由为 `/payment-issue-alerts`。
5. 本地接口、Mapper 和服务测试只证明受控编排与并发围栏，不证明真实查询适配器已配置、供应商已送达、人员已响应、生产网络或稳定性已验证。

### 14.87 单条告警回执回查操作者契约

1. `POST /api/payment-task-center/reconcile-issue-alert-receipts/{alertNo}` 请求体固定为 `{ "operator": "支付运营" }`；`operator` 去除首尾空白后必填且最长 `64` 个字符，非法输入在候选查询、claim、限流和外呼前失败。
2. 规范化后的操作人贯穿本次任务运行日志 `triggeredBy`、明确送达外部告警的 `ackOperator` 与来源 outbox 确认记录，替代单条入口原先固定写入的 `payment-core-admin`，便于按实际发起声明联查。
3. 未送达或有限失败时不会写成确认人，但任务运行日志仍记录本次发起声明；自动回查继续使用调度器身份，既有人工批量入口继续保持原契约，不因本次调整改变。
4. `operator` 当前属于请求方自声明字段，不是从可信登录主体、权限令牌或审计网关派生。本契约提升内部可追溯性，但不证明操作者身份真实、已获授权或满足正式审计合规要求。

### 14.88 任务中心人工任务统一操作者契约

1. 任务中心的超时关单、支付主动查单、退款主动查单、查询监控告警、失败事件重发、失败退款重试、SLA 升级、告警派发、批量回执回查、单条回执回查和控制策略巡检共 `11` 个手工 `POST` 入口统一接收 `{ "operator": "支付运营" }`。
2. `operator` 在服务层去除首尾空白后必填且最长 `64` 个字符；请求体缺失、字段为空白或超长时，在读取候选、claim、占用共享限流额度、调用渠道或通知网关前返回参数错误。
3. 规范化操作人统一写入任务运行日志 `triggeredBy`，并继续传递到具体业务审计：告警派发写入派发操作者，回执明确送达时写入告警与来源 outbox 确认人。自动调度入口、调度开关和既有调度器身份保持不变。
4. 管理端任务中心以同一操作人驱动全部人工任务，浏览器会阻止空值请求；服务端校验仍是最终边界。该字段仍由请求方自声明，不构成可信认证、权限校验、正式授权或不可抵赖审计证据。

### 14.89 异常中心人工操作人前置校验契约

1. `POST /api/payment-issues/actions` 的 `operator` 改为必填，去除首尾空白后最长 `64` 个字符；不再在缺失时回退为 `assignee`，避免把被分派人记录为动作发起人。
2. `POST /api/payment-issues/alerts/{alertNo}/acknowledge` 使用同一长度边界。空白或超长确认人在读取告警前失败；即使目标告警已确认，调用方也必须提交合法操作人，保持人工写入口契约一致。
3. 合法操作人继续写入异常动作日志；`标记已处理` 时以同一操作人确认待确认告警。管理端异常中心、告警明细台和任务中心均不再预填固定操作人，必须由本次请求方显式输入。
4. 前置校验与取消默认值只提升自声明审计的准确性，不证明输入者身份真实、具有处理权限或通过正式审批；可信主体仍需后续统一认证与授权上下文提供。

### 14.90 退款人工动作操作人审计契约

1. `POST /api/refunds/apply`、`POST /api/refunds/approve`、`POST /api/refunds/retry` 与 `POST /api/refunds/query` 均要求请求体显式提交 `operator`；该字段去除首尾空白后必填且最长 `64` 个字符。
2. 退款申请、审核、失败重试和主动查单在任何退款 Mapper 访问前完成操作人校验。合法值分别写入 `APPLY / APPROVE / RETRY / CHANNEL_QUERY*` 操作日志，查单失败日志也保留同一发起人，不再回退固定 `payment-core-admin` 或 `channel-query`。
3. 任务中心人工退款查单将规范化后的人工任务操作人传入每条查单请求；自动退款查单继续显式传递 `refund-query-scheduler`，失败退款自动重试继续传递既有调度身份。退款渠道可信回调仍使用内部身份 `channel-callback`。
4. 管理端退款列表和退款详情的操作人输入默认留空，申请、审核、主动查单与失败重试在空值时不发送请求；服务端校验仍是最终边界。
5. `operator` 仍是请求方自声明，不构成可信登录、角色授权、正式审批或不可抵赖审计证据。本契约与本地测试也不证明真实退款渠道、生产环境、到账核验或稳定性通过。

### 14.91 支付日终人工触发人契约

1. `POST /api/payment-day-end/run` 是当前支付日终唯一写入口，请求体必须存在，并显式提交 `triggeredBy`；该字段去除首尾空白后必填且最长 `64` 个字符，不再缺失时回退为 `system-auto`。
2. 人工入口的 `runMode` 为空时规范化为 `MANUAL`，显式值忽略大小写后只允许 `MANUAL / RERUN`；`AUTO` 不允许由浏览器人工提交，避免把人工执行误记为自动调度。
3. 请求体、触发人和运行方式校验均先于业务日期解析及全部日终统计 Mapper 查询。当前仓库没有自动日终调度器；未来增加自动能力时应提供独立内部入口和系统身份，不复用本人工接口伪装来源。
4. `triggeredBy` 当前仍是请求方自声明，不证明登录主体可信、具备日终权限或完成审批。本接口与本地测试也不证明生产日终、真实渠道核对、正式对账、现场核验或稳定性通过。

### 14.92 支付事件重发操作人审计契约

1. `POST /api/payment-events/republish` 请求体固定为 `{ "eventNo": "EVT...", "operator": "支付运营" }`；事件号和操作人均去除首尾空白后必填且最长 `64` 个字符，非法值在事件查询、行锁和下游投递前失败。
2. 单笔人工重发、任务中心人工失败事件批量重发和自动事件重试复用既有派发链路。人工来源使用请求中的规范化操作人，自动来源使用 `payment-event-scheduler`，不再只在任务运行日志保留批量来源而丢失逐事件来源。
3. 重发入口使用事件行 `SELECT ... FOR UPDATE` 串行化同一事件的并发投递；只有派发服务返回成功且审计更新恰好命中一行时，才提交 `lastRepublishOperator / lastRepublishedAt`。投递失败、事件不存在或不支持重发时不写成成功重发审计。
4. 事件列表、详情侧栏与 CSV 新增最近成功重发操作人和时间。该事实表示 payment-core 已完成本次受控投递调用及内部状态更新，不证明账务、清分、结算或退款渠道已经消费、入账或最终处理成功。
5. `operator` 仍由请求方自声明，不构成可信认证、角色授权、审批或不可抵赖证据；本地测试也不证明生产 MySQL 迁移、真实下游网络、消费幂等或稳定性通过。

联调说明：

1. 页面侧接到失败响应时，应优先展示 `message`，并同时展示 `code` 与 `requestId`。
2. 用户端和后台当前都已按此口径展示错误信息，便于产品、开发、测试共享同一条问题线索。

### 14.93 支付查单与关单发起来源契约

1. 消费者自助入口保持 `POST /api/payments/query`、`POST /api/payments/close`，请求体最低只需 `{paymentOrderId}`。即使客户端提交 `operator`，服务端也不采信，而是固定以 `customer-self-service` 记录应用来源类别。
2. 管理端人工入口为 `POST /api/payments/admin/query`、`POST /api/payments/admin/close`，请求体为 `{paymentOrderId, operator}`。`operator` 去除首尾空白后必填、最长 `64` 个字符且不能包含控制字符；非法值在支付单读取、限流占额和渠道调用前返回 `PAYMENT_REQUEST_INVALID`。
3. 两类入口响应继续使用既有 `PaymentDetailDTO`。查单成功仅表示本次接口完成；只有渠道返回的可信状态满足既有支付状态机时才可收口 `SUCCESS`，失败及限流拒绝同样记录 `initiatedBy`，不得伪造渠道成功。
4. 任务中心不经 HTTP 管理入口，直接把人工任务的规范化 `triggeredBy` 或自动身份 `payment-query-scheduler` 传入逐笔查单。实际关单和查单成功收口事件在原 payload 增加 `initiatedBy`。
5. `/admin/*` 仅表达接口用途，不代表已实现认证授权。生产接入必须由可信身份上下文覆盖客户端自声明值，并限制管理入口访问范围。

### 14.94 退款渠道下发来源传递契约

1. 内部 `PaymentEventDispatchService.publishRefundSubmit(eventNo, refundOrderId, operator)` 与 `republish(eventNo, operator)` 必须显式接收派发来源；来源去除首尾空白后必填、最长 `64` 个字符且不能包含控制字符。
2. 退款审核、人工失败重试和任务重试使用各自已规范化的操作人或 `triggeredBy`；单笔事件人工重发使用请求操作人，自动事件重发使用 `payment-event-scheduler`。内部调用方不得在派发层回退固定人工身份。
3. 渠道返回受理号时，`CHANNEL_SUBMIT` 日志记录该来源；调用渠道前的可重试失败写入 `CHANNEL_SUBMIT_FAILED`，渠道可能受理的未知结果写入 `CHANNEL_SUBMIT_UNKNOWN`，三类日志使用同一来源语义。
4. 来源参数不改变退款提交幂等与安全边界：已存在受理事实时不得重复外呼，`UNKNOWN` 仍禁止直接重发，受理号冲突、事件状态和重试次数仍按既有规则处理。
5. 该接口是 payment-core 内部契约，不新增外部 HTTP 字段；来源也不构成可信认证、渠道审批、渠道受理或到账证明。

### 14.95 死信补偿任务人工处置契约

1. `GET /api/payment-dead-letter-tasks` 支持 `taskStatus / targetSystem / messageId / pageNo / pageSize`；返回任务状态、原目标与重放路由、处置事实以及脱敏后的 `payloadSnapshot / headerSnapshot`。
2. `POST /api/payment-dead-letter-tasks/{taskNo}/ready-to-replay` 与 `POST /api/payment-dead-letter-tasks/{taskNo}/resolve-manually` 仅接受 `PENDING_REVIEW / REPLAY_FAILED`；`POST /api/payment-dead-letter-tasks/{taskNo}/replay` 仅接受 `READY_TO_REPLAY`。三类请求体均为 `{operator, resolutionNote}`。
3. `taskNo` 去空格后必填、最长 `64`；`operator` 必填、最长 `64`；`resolutionNote` 必填、最长 `512`。三者拒绝控制字符，非法值在 Mapper 与 RabbitMQ 调用前失败。
4. 执行重放先以条件更新进入 `REPLAYING`，同时记录操作人、处置说明、重放次数和时间；只向任务持久化的原消费者路由发送原始消息体并等待发布确认，成功进入 `REPLAYED`，异常进入 `REPLAY_FAILED` 并保存脱敏失败说明。
5. `REPLAYED` 只表示 payment-core 获得消息代理发布确认，不证明目标消费者已处理、账务或清分已入账、结算已收口，也不构成正式审批或生产稳定性证据。

### 14.96 配置复核人员声明字符契约

1. 所有配置中心复核申请与批准/拒绝接口的 `requester / reviewer` 共享同一服务端规则：原始值不得包含 ISO 控制字符，去除首尾空格后必须非空且最长 `64` 个字符。
2. 非法申请人声明在候选复核记录写入前返回参数错误；非法复核人声明在配置新增、修改、启停、回滚、批量导入应用及复核完成更新前返回参数错误。
3. 控制字符检查必须在规范化前执行，既拒绝换行等可被 `trim()` 移除的字符，也拒绝 `U+0085` 等不会被 `trim()` 移除、可能造成同人声明比较绕过的字符。
4. `changeReason / reviewComment` 保持既有非空、去首尾空格和 `255` 字符上限，并允许正常多行文本；人员字段与说明字段不共用控制字符策略。
5. 以上人员值仍是请求方声明，不是认证主体或授权凭据。接口调用成功不能解释为真实人员已获授权、正式审批已完成或生产配置已发布。

### 14.97 人工操作人原始字符契约

1. 后台支付查单/关单、退款人工动作、支付事件重发、异常处理与告警确认、任务中心、告警派发/回查和日终的 `operator / triggeredBy` 原始值不得包含 ISO 控制字符。
2. 服务端校验顺序为原始字符检查、非空与首尾普通空格规范化、`64` 字符上限；因此末尾换行不会被静默裁掉，`U+0085` 等不可见字符也不能进入审计字段。
3. 非法值必须先于候选查询、任务租约、Mapper 写入、共享限流、事件派发和渠道/供应商调用失败。自动任务继续使用各自服务端固定调度类别，不采信浏览器伪造的自动来源。
4. 该契约不改变请求路径、响应结构、状态机或数据库字段。人员值仍是自声明来源，不是认证主体、授权凭据、渠道受理或业务完成证明。

### 14.98 异常批量动作容量与字段契约

1. `POST /api/payment-issues/actions` 的 `issueNos` 必填、不得为空、规范化后不得重复，单次最多 `100` 条；每个异常编号去除首尾空格后必填、最长 `128` 个字符且不得包含 ISO 控制字符。
2. `actionType` 只接受四种既有固定动作；`assignee` 必填且最长 `64`，`operator` 必填且最长 `64`，`remark` 可空且最长 `255`。原始字段不得包含 ISO 控制字符，空白备注规范化为“批量处理”。
3. 服务端必须先校验并规范化整批请求，再读取任一异常。非法批次不产生 Mapper 访问、动作日志或告警确认；合法批次仍在同一事务中逐条写动作日志，并仅在“标记已处理”时确认待确认告警。
4. `POST /api/payment-issues/alerts/{alertNo}/acknowledge` 的 `alertNo` 去除首尾空格后必填、最长 `64` 个字符且不得包含 ISO 控制字符；客户端应按 URL 组件编码路径参数。
5. 批量接口成功只表示本地动作事实已记录，不表示真实责任人已接受任务、告警已送达、异常根因已消除或生产处置已验收。

### 14.99 CSV 导出单元格安全契约

1. payment-core 全部 `15` 类 CSV 导出在写入单元格前检查原始展示值；以 `= / + / - / @`、制表、回车或换行开头时增加单引号文本标记，再执行既有双引号转义和包裹规则。
2. 防护覆盖订单、账单、支付、退款、收银台会话、支付请求、资金流、路由执行、事件、处理日志、日终、任务运行、配置快照、异常告警和结算导出，不要求调用方自行清洗。
3. 普通文本、空值、列名、列顺序、UTF-8 BOM、文件下载响应和脱敏策略保持不变；负号开头的值为安全起见按文本导出，调用方不得据此推断源数据类型发生变化。
4. 该契约只约束服务端原始导出。下载后的人工编辑、第三方拼接、导入其他系统和表格软件差异不在本地接口成功语义内，需要独立安全与兼容验证。

### 14.100 异常告警导出容量契约

1. `GET /api/payment-issues/alerts/export` 复用告警列表筛选与排序，但忽略请求中的 `pageNo / pageSize`，固定从第一页读取，避免前端不传分页时沿用列表默认 `20` 条而漏出后续匹配记录。
2. Service 向既有分页 Mapper 传入 `pageNo=1 / pageSize=1001`；Mapper SQL 通过 `LIMIT / OFFSET` 最多读取 `1001` 行，不执行无界全量查询。
3. 匹配结果不超过 `1000` 条时生成完整 CSV；返回 `1001` 条时抛出“导出数据超过1000条，请缩小筛选范围”，不得静默截断为前 `1000` 条。
4. 普通列表的 `1..100` 分页规则、告警筛选、排序、脱敏、公式防护、BOM、表头与文件名保持不变。本轮没有前端、Mapper XML、schema 或迁移变化。
5. 接口测试不构成生产数据规模、真实下载链路、导出权限、审批或稳定性证明。

### 14.101 同步运营 CSV 统一容量契约

1. 订单、账单、支付、退款、收银台会话、支付请求、资金流、路由执行、事件、处理日志、日终、任务运行、异常告警和结算共 `14` 类同步运营 CSV 最多返回 `1000` 条匹配记录。
2. `13` 个独立导出 Mapper SQL 在原筛选和排序后固定 `LIMIT 1001`；异常告警通过既有分页 SQL 请求 `pageNo=1 / pageSize=1001`。数据库单次最多返回 `1001` 行，不执行无界读取。
3. Service 在脱敏和 CSV 编码前调用共享 `CsvExportRowLimiter`。返回第 `1001` 条时统一抛出“导出数据超过1000条，请缩小筛选范围”，不得静默截断或返回部分成功文件。
4. 普通列表分页、筛选、排序、字段、BOM、文件名、脱敏和公式防护保持原契约。配置治理快照是多分区聚合导出，不纳入本条 `14` 类限制。
5. 本地接口与 SQL 测试不证明生产数据规模、执行计划、真实下载链路、导出权限、审批或稳定性。

### 14.102 配置治理快照分区查询与容量契约

1. `GET /api/payment-config/export?section=...` 的单分区请求只查询指定的 `CHANNELS / ROUTE_RULES / PROTOCOLS / RETURN_CODES / GATEWAYS / CONTROL_POLICIES / ALERT_PROVIDERS / ISSUE_DUTY_ROSTERS`，不读取其他分区或协议类型选项。
2. `section=ALL` 按固定顺序查询八个实际导出分区；每个分区独立允许最多 `1000` 条，因此全部分区合法时理论最多输出 `8000` 条。任一分区返回 `1001` 条或更多时整体抛出“导出数据超过1000条，请缩小筛选范围”，不得返回部分文件。
3. 字段、顺序、BOM、文件名、脱敏和公式防护保持原契约；配置页面总览接口继续使用原九组查询，不受导出查询裁剪影响。
4. 当前容量判断在 Mapper 返回后执行，八个配置查询 SQL 尚未增加专用 `LIMIT 1001`，不能据此声称数据库读取完全有界。
5. 本地自动化不证明生产数据规模、执行计划、真实下载、导出权限、审批或稳定性。

### 14.103 配置治理快照专用查询契约

1. 八个配置分区导出分别使用专用 Mapper 查询，并在原字段与稳定排序后固定 `LIMIT 1001`；单次数据库读取不会超过 `1001` 行。
2. Service 仍以 `1000` 条为完整导出上限，第 `1001` 行仅用于探测。`ALL` 对八个分区分别执行该规则，任一分区超限即整体失败。
3. 配置总览接口及运行路由、自检、返回码导入等业务继续使用普通查询，不附加导出固定上限；接口字段、BOM、文件名、脱敏与公式防护均不变。
4. 本轮无前端、schema 或迁移变化；本地自动化不证明生产数据规模、执行计划、真实下载、权限审批或稳定性。

### 14.104 外部渠道结果原始字符契约

1. 支付查单结果的 `tradeStatus / channelTransactionNo / querySource / channelReturnCode` 及标准化字段、支付提交结果和退款提交结果均在 `trim` 前拒绝任意位置的 ISO 控制字符；普通首尾空格仍按原契约归一化。
2. 支付查单原始结果在返回码映射查询前校验，映射结果和 PaymentService 写入前再次校验；非法结果返回 `PAYMENT-1026`，不得写返回码快照、查单成功日志或支付成功事实。
3. 支付提交非法结果返回 `PAYMENT-1048`。退款提交非法受理结果标记为结果未知，退款保持 `PROCESSING`，不得绑定渠道退款号、释放额度或推导 `FAIL`。
4. 本地自动化不证明真实渠道协议、报文签名、网络调用、资金到账、对账或稳定性。

### 14.105 gateway-access 查单原始响应契约

1. gateway-access 支付查单响应的 `tradeStatus / channelTransactionNo / querySource / channelReturnCode` 必须在适配器内按原始字符校验后才能归一化；适配器不得先 `trim` 再把结果交给 Service。
2. 支付与退款查单仅在 `querySource` 为 `null` 或空串时补 `GATEWAY_ACCESS_HTTP`；含换行等 ISO 控制字符的显式来源必须分别返回 `PAYMENT-1026` 或退款查单失败，不得被默认值覆盖。
3. 合法首尾空格继续由结果校验器归一化，合法缺省来源和状态映射不变；非法结果不得进入返回码映射、业务状态收口或持久化路径。
4. 本地适配器测试不证明真实 gateway-access 协议、签名验签、生产网络、渠道查询事实、资金到账或稳定性。

### 14.106 告警通知器结果契约

1. `PaymentIssueAlertNotifier.send` 必须返回非空结果并提供明确 `providerDeliveryStatus`；`null`、空状态或纯空白状态不得由派发 Service 推导为 `ACCEPTED`。
2. `providerDeliveryStatus / providerDeliveryMessage / providerReceiptSnapshot / providerReceiptNo / renderedContentSnapshot` 在默认值和长度截断前拒绝任意 ISO 控制字符，非法原值不得落库或返回页面。
3. 结果不满足契约时，当前候选供应商保存固定脱敏 `SEND_EXCEPTION` 失败记录并继续 provider fallback；只有明确 `ACCEPTED / DELIVERED` 才计为派发成功。
4. 本地测试不证明真实 IM、短信或邮件供应商受理、最终送达、人员响应、生产网络或稳定性。

### 14.107 告警通知状态有限集合契约

1. 通知器返回状态通过原始控制字符检查后执行首尾空格归一化和大写转换，内部仅接受 `ACCEPTED / DELIVERED / FAILED`。
2. 未知、超出有限集合或空白状态按结果契约失败处理，保存固定脱敏 `SEND_EXCEPTION` 并继续 provider fallback，不得把供应商未知原文写入接口或数据库。
3. 标准化后的 `ACCEPTED` 必须同时用于派发成功判断与后续回执候选、积压和监控查询，禁止出现成功判断忽略大小写但数据库保留非标准值的口径分裂。
4. 本地测试不证明真实供应商状态码表、受理、最终送达、人员响应、生产网络或稳定性。

### 14.108 告警回执查询适配器结果契约

1. `PaymentIssueAlertReceiptQueryAdapter.query` 返回后由 Service 统一校验；`providerDeliveryStatus` 只允许规范化后的 `ACCEPTED / DELIVERED / FAILED`，未知状态不得写入 `receipt_query_result`。
2. 回执号最长 `128`、投递说明最长 `255`、回执快照最长 `2048` 个字符，所有返回字段在去空格前拒绝 ISO 控制字符；快照在持久化前统一脱敏，渲染内容不参与回执更新。
3. 原受理记录存在 `providerReceiptNo` 时，`DELIVERED` 结果必须返回同一编号；缺失或不一致统一形成 `RECEIPT_CONFLICT`，不得确认送达或联动确认来源告警。
4. 空结果或空状态继续形成 `EMPTY`，适配器异常、未知状态及非法字段形成 `QUERY_ERROR`；本地契约测试不证明真实供应商回执、最终送达、人员响应或生产网络。

### 14.109 告警回执适配器选择失败契约

1. Service 在领取回执查询任务后调用各适配器 `supports(channelCode)`；选择过程抛出运行时异常时，本次尝试固定完成为 `QUERY_ERROR`，不向接口响应或数据库暴露异常原文。
2. 选择失败不得继续获取共享查询配额、调用 `query` 或更新 `DELIVERED`；`nextReceiptQueryAt` 按既有指数退避计算，达到最大次数后停止自动候选。
3. 所有适配器均不支持目标通道时继续返回内部分类 `ADAPTER_MISSING`，与选择实现抛错保持可区分。
4. 本地测试不证明真实 Spring Bean 装配、供应商连通性、生产任务频率或稳定性。

### 14.110 支付查单交易状态标准化契约

1. `PaymentChannelQueryAdapter.query` 返回的 `tradeStatus` 经原始控制字符和长度检查后执行 `trim + upper-case`，只允许 `PREPAY_CREATED / RISK_REVIEW / RISK_BLOCKED / RISK_REJECTED / WAIT_CALLBACK / SUCCESS / CLOSED / FAIL`。
2. `tradeStatus` 表示 payment-core 平台交易状态，`channelReturnCode` 表示渠道原始返回码；例如 `WAIT_CALLBACK + USERPAYING`，两字段不得混用，返回码标准化不得反向推断或改写平台状态。
3. 空白、未知或不在有限集合内的交易状态统一返回 `PAYMENT_CHANNEL_QUERY_FAILED`，且必须在返回码标准化查询、快照保存、支付状态更新和成功事件派发前失败。
4. 本地契约测试不证明真实 gateway-access、渠道状态码表、查询结果、资金到账、生产网络或稳定性。

### 14.111 支付提交适配器结果契约

1. `PaymentChannelSubmitAdapter.submit` 必须返回非空结果，且 `channelTransactionNo / attemptStatus / attemptStatusType / channelReturnCode` 均为必填；状态类型规范化为小写后只允许 `info / warn / danger / success`。
2. 渠道流水号最长 `128`、尝试状态和类型最长 `32`、渠道返回码最长 `64`、响应快照最长 `2048` 个字符；标准码最长 `64`，标准文案与处理建议最长 `256`，布尔文案最长 `16`，映射版本最长 `32`。所有字符串在去空格前拒绝 ISO 控制字符。
3. 共享 Service 在返回码映射前校验原始结果，并在映射后再次校验新增字段与响应快照；任一阶段失败均返回 `PAYMENT_CHANNEL_SUBMIT_FAILED`，原始非法结果不得触发返回码 Mapper 查询。
4. 渠道调用后的校验失败不能证明渠道未受理，支付主流程保持结果未知并等待主动查单，不绑定非法渠道流水或形成提交成功事实。本地测试不证明真实渠道受理、资金到账、生产网络或稳定性。

### 14.112 风控准入响应契约

1. `POST /api/risk-control/decisions/evaluate` 只有统一响应 `code` 去除首尾空格后严格等于字符串 `0` 且 `data` 非空时才视为业务成功；失败码携带的数据必须忽略。
2. `data.decision` 必填并规范化为 `PASS / REVIEW / INTERCEPT / REJECT`，`decisionType` 必填并规范化为 `info / warn / danger / success`；其他值统一返回 `PAYMENT_RISK_SERVICE_UNAVAILABLE`。
3. 决策最长 `32`、样式最长 `32`、命中编码最长 `64`、风险标签最长 `128`、复核单号和事件号最长 `64`、说明最长 `255` 个字符。所有字符串在去空格前拒绝 ISO 控制字符，可选纯空白字段归一化为 `null`。
4. 风控空结果、非法结果和 HTTP 运行时异常均返回固定脱敏错误，不进入支付尝试创建或渠道调用。本地契约测试不证明真实风控服务连通、规则正确性、人工复核或生产稳定性。

### 14.113 运营配置有效快照消费契约

1. `GET /api/ops-config/effective-snapshot` 只有统一响应 `code` 去除首尾空格后等于字符串 `0` 且 `data` 完整时才可消费；失败码携带的快照数据必须忽略。
2. `snapshotVersion` 必须大于等于 `0`，`businessCode / payType / terminalType` 必须分别与请求值匹配。三者最长 `64`，默认支付方式最长 `64`，主备渠道编码最长 `64`，路由策略最长 `128` 个字符。
3. `enabledSystemControls` 必须为非 `null` 集合且最多 `1000` 条，允许当前无启用控制项；集合内对象不得为空，编码唯一、状态只允许 `ENABLED`，样式只允许 `info / warn / danger / success`。控制编码最长 `64`、名称/范围/值最长 `128`、风险级别最长 `64`、状态和样式最长 `32`、更新时间最长 `32`。
4. 所有文本在去空格前拒绝 ISO 控制字符。非法快照与 URL/HTTP 异常按现有可选依赖契约返回 `null`；PaymentService 只在缺少必须默认值时返回 `PAYMENT_OPS_CONFIG_SNAPSHOT_INVALID`。本地测试不证明真实配置发布或跨系统一致性。

### 14.114 支付成功事件下游响应确认契约

1. HTTP 模式调用清分与账务前，客户端、两个 URL 和账务账号必须同时有效；URL 最长 `2048`、账号最长 `64` 个字符，均在去空格前拒绝 ISO 控制字符。任一配置非法时不得调用任何下游。
2. 两个下游统一响应均按 `{code,message,data,requestId}` 解析。只有 `ResponseEntity` 非空、HTTP 2xx、响应体非空、`code` 原文无控制字符且去空格后严格等于字符串 `0`，且 `data` 为事件对象并同时包含非空 `eventNo`、`eventType=PAYMENT_SUCCESS`、`bizNo={原支付单号}`、`businessChainId=CHAIN-{原支付单号}`、`eventStatus=已消费` 时，才确认该次调用成功。仅有 HTTP 2xx、`code=0` 或非空 `data` 均不足以确认归属与消费。
3. 调用顺序固定为清分后账务。清分未明确成功时不得调用账务；任一调用失败均进入既有失败计数、重试或死信流程，不得标记事件发布成功。
4. AMQP 模式要求 RabbitTemplate、序列化器、exchange、routing key 和账务账号完整，目的字段最长 `255`、账号最长 `64` 个字符。消息投递确认仍不等于下游业务处理完成。
5. HTTP 顺序重试可能在“清分成功、账务失败”后重放清分，接口消费者必须以支付单号、事件号等稳定业务标识实现幂等。本地测试不证明真实送达、清分完成、账务入账或资金到账。
6. 配置跨系统对账事实地址时，`POST /api/reconciliation/cross-system/facts` 的 `code=0` 与非空 `data` 还不足以标记支付事件完成。`data` 必须是业务链快照，`businessChainId=CHAIN-{支付单号}`，其 `facts` 中至少有一条 `businessChainId`、`systemCode=payment-core`、`factType=PAYMENT_SUCCESS`、`factNo={支付单号}`、`businessNo={原业务单号}`、`payScene`、`factStatus=SUCCESS` 和金额与本次提交完全匹配的事实。该阶段不要求整条跨系统链路已财务收口；不匹配时事件继续失败重试，HTTP 完成阶段不得推进。未配置对账地址时维持既有可选投递行为。

### 14.115 超时关单任务计数契约

1. `POST /api/payment-task-center/actions/close-expired-payments` 继续返回 `PaymentTaskActionResultDTO`；`processedCount` 为候选扫描数，`successCount` 仅为条件更新成功且整笔事务提交的实际关闭数。
2. 候选查询后支付单因并发提交等原因不再满足关闭条件时，条件更新返回 `0`，计入 `warningCount` 并在摘要中显示“并发跳过”；该结果不属于成功或失败，不产生关闭事件。
3. 单笔事务抛出运行时异常计入 `failCount`，并最多附带前三个失败支付单号；其他候选继续执行。非空正常批次满足 `processedCount = successCount + warningCount + failCount`。
4. 自动调度与人工触发共享该统计语义。warning 表示执行期资格变化，不表示渠道处理、关闭成功或资金事实；接口消费者应刷新支付详情读取最终状态。

### 14.116 超时关单批次容量契约

1. 超时关单候选查询接受服务端内部 `batchSize`，SQL 按预付单最早过期时间升序后执行参数化 `LIMIT`；该参数不作为外部 API 请求字段开放。
2. `payment.expiry-close.batch-size` 默认 `100`，可由 `HSP_PAYMENT_EXPIRY_CLOSE_BATCH_SIZE` 覆盖；服务端最终取值范围固定为 `1..1000`。
3. `processedCount` 仅表示本轮有界候选数。返回结果中的 `overview.expiredPaymentCount` 为执行后重新查询的当前积压，两者不能互相替代，也不能据单轮成功数推导全量收口。
4. 超出批次的候选留待后续轮次，按最早过期优先；本地契约不承诺生产单轮耗时、积压清零 SLA、持续失败候选公平性或真实渠道关单。

### 14.117 超时关单失败退避与总览计数契约

1. 超时关单失败后登记技术重试标记，默认首次间隔 `60` 秒、增长因子 `2`、最大间隔 `3600` 秒；对应配置可分别由 `HSP_PAYMENT_EXPIRY_CLOSE_RETRY_INTERVAL_SECONDS`、`HSP_PAYMENT_EXPIRY_CLOSE_RETRY_BACKOFF_FACTOR` 和 `HSP_PAYMENT_EXPIRY_CLOSE_MAX_RETRY_INTERVAL_SECONDS` 覆盖，服务端确保各值至少为 `1` 且最大间隔不小于首次间隔。
2. 失败登记在原关单事务回滚后使用独立新事务；登记失败不改变本轮 `failCount`，也不阻断后续候选。失败摘要最长 `512` 个字符，不作为外部异常原文契约。
3. `GET /api/payment-task-center/overview` 新增 `dueExpiredPaymentCount` 和 `backingOffExpiredPaymentCount`。`expiredPaymentCount` 仍为全部合格超时支付单去重总数并包含退避数据，前两个字段分别表示当前可执行和当前尚在失败退避窗口的数据。
4. 候选查询忽略 `next_retry_at > NOW()` 的支付单；到期后自然重入。实际关闭或执行期失去关单资格时清除标记。未提交渠道流水同时接受 `NULL` 和历史空字符串，但非空渠道流水始终不得进入关单候选。
5. 本地接口与 H2 测试不承诺生产 MySQL 时间精度、并发更新、执行计划、多实例吞吐、积压 SLA 或真实渠道关单。

### 14.118 超时关单陈旧重试标记清理契约

1. 人工或自动执行超时关单时，服务端在候选查询前调用内部清理，不新增外部 API 或请求字段；清理批次复用最终夹取到 `1..1000` 的 `payment.expiry-close.batch-size`。
2. 支付单不存在、状态不再为 `PREPAY_CREATED`、渠道流水非空、没有已过期预付单或已经产生支付尝试时，其重试标记失去作用并可删除；仍满足全部关单资格的退避标记必须保留。
3. 单轮按重试表主键最多删除一个批次，剩余陈旧标记由后续轮次继续收敛。清理异常不阻断候选查询，也不改变接口的 `processedCount / successCount / warningCount / failCount`。
4. 总览计数始终以当前支付业务资格为准，陈旧标记本身不计入总积压或退避中数量。本地契约不承诺生产 MySQL 查询成本、锁影响、清理 SLA 或多实例吞吐。

### 14.119 超时关单失败详情脱敏契约

1. 超时关单失败后的内部重试摘要固定为 `EXPIRY_CLOSE_FAILED:<异常简单类名>`；异常原始 message、cause 和调用栈不是持久化字段、任务中心响应或业务日志契约。
2. 原关单事务异常仍触发该笔回滚，并在事务外按固定分类登记失败；失败标记写入异常同样不得向 Spring 事务日志传播原始详情，登记失败只保留支付单号与异常类型 WARN。
3. `processedCount / successCount / warningCount / failCount`、最多三笔失败支付单号、退避配置和总览字段均不改变，本轮不新增或删除外部 API 字段。
4. 本契约只约束本版本新产生的数据与该服务日志路径，不承诺清理历史异常原文，也不证明生产日志配置、诊断权限或生产稳定性。

### 14.120 自动任务租约异常脱敏契约

1. `PaymentTaskLeaseService` 的初始化、获取、续期和释放异常统一转换为 `PAYMENT_TASK_LEASE_OPERATION_FAILED:<异常简单类名>`；原始 message、cause 和调用栈不是任务接口、业务日志或事务日志契约。
2. 续期线程捕获异常后仅记录任务码、内部 owner 与原异常类型，并保持既有下一周期重试行为；续期返回 `0` 时仍关闭句柄并按 owner 条件释放。
3. 本轮不新增 HTTP 字段，不改变任务中心成功、警告、失败或租约占用跳过响应，也不将稳定异常分类暴露为生产故障根因。
4. 该契约不是 fencing token 或任务中止协议，不承诺数据库网络分区、JVM 长暂停、续期线程阻塞时无重复执行；生产仍需业务幂等、失约监控与多实例故障测试。

### 14.121 任务中心告警写入异常脱敏契约

1. 异常 SLA 升级巡检内部逐笔写入失败统一转换为 `PAYMENT_ISSUE_ALERT_WRITE_FAILED:<异常简单类名>`，原始 message、cause 和调用栈不进入事务日志或任务响应。
2. 普通运行时异常回滚当前候选并计入 `failCount`，后续候选继续；响应摘要最多拼接三笔来源告警号或异常编号，不拼接底层异常详情。
3. 重复键异常仍映射为并发重复跳过并计入 `warningCount`，不计入 `failCount`。本轮不新增或删除任务中心 HTTP 字段。
4. 本地契约不承诺生产 MySQL 唯一键并发行为、真实告警送达、人员确认、日志采集配置或生产稳定性。

### 14.122 支付提交事务异常脱敏契约

1. 支付提交内部事务的未知运行时异常统一转换为 `PAYMENT_SUBMIT_TRANSACTION_FAILED:<异常简单类名>`，不保留原始 message、cause、suppressed 或调用栈；受控 `BusinessException` 保留既有错误码与安全提示。
2. 渠道调用前的准备事务失败时不得调用渠道；渠道调用后完成事务失败时，先回滚本次完成写入，再以独立事务把尝试与预付单标记为结果未知，有可信渠道流水时支付单进入 `WAIT_CALLBACK`。
3. 未知内部异常由统一异常处理返回通用系统错误，固定技术分类不新增为外部响应字段；既有支付提交请求、响应和错误码结构不变。
4. 该契约不证明真实渠道受理、资金到账或恢复事务必然成功；结果未知必须依赖后续主动查单、回调和对账确认，不允许客户端直接重试扣款。

### 14.123 失败退款重试事务异常脱敏契约

1. 失败退款批处理的逐笔事务内部运行时异常统一转换为 `REFUND_RETRY_TRANSACTION_FAILED:<异常简单类名>`，不保留原始 message、cause、suppressed 或调用栈。
2. 任务服务捕获固定分类后仍只累计 `failCount` 并记录最多三个失败退款单号；单笔事务回滚后必须继续处理后续候选。
3. 固定技术分类不新增为 HTTP 响应字段，既有任务中心处理数、成功数、失败数和摘要结构不变；不得将批次执行成功解释为渠道受理或退款到账。
4. 该契约不证明真实渠道下发、退款到账、生产事务隔离或多实例调度正确性，仍需渠道查单、回调、对账与环境验证形成最终事实。

### 14.124 告警逐笔事务异常脱敏契约

1. 告警派发与供应商回执对账的逐笔事务内部运行时异常统一转换为 `PAYMENT_ISSUE_ALERT_ITEM_TRANSACTION_FAILED:<异常简单类名>`，不保留原始 message、cause、suppressed 或调用栈。
2. 当前告警事务失败后仍按既有任务结果累计失败并记录最多三个失败告警编号，单条回滚不得中断后续派发或回执候选。
3. 固定技术分类不新增为 HTTP 响应字段；供应商返回失败、适配器缺失、回执限流和查询失败继续使用既有业务分类与任务汇总结构。
4. 该契约不证明真实通知送达、供应商回执可信或人员确认，最终送达事实仍需可信回执、运营核验和环境证据支撑。

### 14.125 支付事件重投递事务异常脱敏契约

1. 支付事件逐笔重投递事务内部运行时异常统一转换为 `PAYMENT_EVENT_RETRY_TRANSACTION_FAILED:<异常简单类名>`，不保留原始 message、cause、suppressed 或调用栈。
2. `republish=true` 仍计成功并更新审计，`republish=false` 仍作为受控失败提交；未处理异常回滚当前事件并计失败，三类结果均继续后续候选。
3. 固定技术分类不新增为 HTTP 响应字段，既有处理数、成功数、失败数和最多三个失败事件号契约不变。
4. 数据库事务回滚不证明外部 MQ/HTTP 未收到事件；重投递安全仍依赖事件幂等键、下游去重、投递审计、状态核对与跨系统对账。

### 14.126 退款即时派发失败日志边界

1. 退款状态转为处理中时，退款状态、操作日志和稳定 `REFUND_SUBMIT` outbox 事件在本地事务内写入；事务提交后才触发尽力即时派发。
2. 即时派发运行时异常被吸收并由后续事件重投递补偿，不改变退款接口既有响应、状态机或错误码契约。
3. 失败日志只允许记录稳定 `eventNo` 和异常类全名，不记录原始 message、cause、堆栈或未经控制字符校验的退款单号。
4. 该契约不证明真实渠道是否受理、事件补偿必然成功或退款到账；最终退款事实必须由可信回调、主动查单和对账确认。

### 14.127 告警网关签名与响应解析异常脱敏契约

1. 告警通知器签名失败统一为 `PAYMENT_ISSUE_ALERT_SIGNATURE_FAILED:<异常简单类名>`，非法响应 JSON 统一为 `PAYMENT_ISSUE_ALERT_RESPONSE_JSON_INVALID:<异常简单类名>`；两类异常均不保留原始 message、cause 或调用栈。
2. 派发服务捕获通知器运行时异常后，投递日志继续使用 `SEND_EXCEPTION`、`LOCAL_FAILURE:SEND_EXCEPTION:[REDACTED]` 和“通知器执行异常，transportError=[REDACTED]”，不新增或改变 HTTP 响应字段。
3. 当前供应商异常后继续尝试后续候选；供应商明确返回的投递状态、回执号和失败码仍按既有响应解析与脱敏规则处理。
4. 该契约不证明真实签名协议正确、通知送达、供应商响应可信或人员确认；真实结果仍需供应商回执、运营核验和环境证据支撑。

### 14.128 支付回调验签底层异常脱敏契约

1. 时间戳解析/溢出继续返回 `PAYMENT_CALLBACK_TIMESTAMP_INVALID`，RSA 签名编码/验证与公钥解析继续返回 `PAYMENT_CALLBACK_SIGNATURE_INVALID` 或 `PAYMENT_CALLBACK_CREDENTIAL_INVALID`；错误码和安全响应文案不变。
2. 上述业务异常不保留底层 cause；未分类的 JCA 异常转换为 `PAYMENT_CALLBACK_VERIFICATION_FAILED:<异常简单类名>`，不保留 cause、suppressed 或可写堆栈，并由统一异常处理返回通用系统错误。
3. 支付与退款共用该验签边界；失败时不得清理/写入 nonce，不得读取并更新支付或退款业务状态，也不得生成资金、事件或操作日志副作用。
4. 该契约不证明真实渠道签名向量、证书链、公钥轮换、密钥托管或网络来源可信；生产接入仍需渠道协议、网关安全与环境证据验收。

### 14.129 支付与退款主动查单异常脱敏契约

1. 支付主动查单未知运行时异常统一返回 `PAYMENT-1026` 和 `payment channel query failed unexpectedly`；退款主动查单统一返回 `PAYMENT-1046` 和 `refund channel query failed unexpectedly`，不得拼接或挂载底层异常详情。
2. 两类业务异常均关闭 cause、suppressed 和可写堆栈；已知 `BusinessException` 原样传播，查询限流、非法可信结果和关联流水不一致继续使用既有错误码与安全文案。
3. 抛错前仍分别写固定 `QUERY_FAILED` 通知快照或 `CHANNEL_QUERY_FAILED` 操作日志，保证自动退避、失败统计和后续补偿可用；不得更新返回码快照、支付/退款终态或生成成功事件。
4. 该契约不证明真实渠道查单协议、生产日志配置、补偿成功、资金到账或最终一致性；最终状态仍需可信回调、可信查询结果与对账证据确认。

### 14.130 退款渠道提交上游异常脱敏契约

1. 退款提交 HTTP 客户端或未知运行时异常统一返回 `PAYMENT-1049`、`gateway-access refund submit failed; query before retrying` 和 `resultUnknown=true`，不得拼接或挂载底层异常详情。
2. 该异常关闭 cause、suppressed 和可写堆栈；调用前配置或治理拒绝继续使用 `resultUnknown=false`，已有受控退款提交异常不得被重复改写。
3. 事件派发捕获 `resultUnknown=true` 后仍写 `CHANNEL_SUBMIT_UNKNOWN` 并将事件置为 `UNKNOWN`，退款保持 `PROCESSING`、额度保持占用，不得自动重复向渠道下发。
4. 该契约不证明请求是否实际送达或被渠道受理；最终退款事实仍需可信回调、主动查单、渠道对账和资金核验确认。

### 14.131 支付配置治理异常 cause 脱敏契约

1. 配置治理的唯一待办或运行态创建发生数据库唯一键冲突时，接口继续返回各对象既有固定 `IllegalArgumentException` 文案，不得附带 `DuplicateKeyException`、连接地址、SQL、唯一键名或候选敏感字段。
2. 回调通知地址、网关基础地址与返回码 CSV 解析失败继续返回原固定校验提示，不得附带 URI/CSV 解析器 cause 或输入上下文。
3. 去除 cause 不改变事务回滚、待办唯一性、基线指纹、版本号复验、异人声明校验、批准后发布或驳回不改运行态的契约。
4. 申请人与复核人仍为请求方自声明，不构成认证、授权、不可抵赖审批或正式签字；本地接口测试不证明生产并发、配置发布或稳定性。

### 14.132 支付渠道提交意外异常脱敏契约

1. 支付渠道适配器、提交结果校验或返回码标准化抛出非业务运行时异常时，接口统一返回 `PAYMENT-1048` 与固定安全文案，不附带渠道响应、连接地址、SQL、凭据、cause、栈或 suppressed。
2. 已明确分类的 `BusinessException` 保持原错误码与安全文案，不因统一边界丢失既有业务分类。
3. 意外异常不等于渠道明确拒绝；支付提交事实继续标记为“结果未知，待查单”，不得据此直接重复下发。
4. 本地接口测试不证明请求真实送达、渠道受理、主动查单或对账收口、资金结果和生产稳定性。

### 14.133 死信接入 ACK/NACK 契约

1. 合法死信路由只有在任务账本持久化成功后才 ACK；持久化及其他未知运行时失败执行 `basicNack(requeue=true)`，等待后续重试。
2. 无法映射到受控目标的 routing key 抛出专用永久错误，固定提示不得附带 routing key 原文，监听器执行 `basicNack(requeue=false)`，避免确定性毒消息无限循环。
3. 永久错误不得创建无合法目标的死信任务；合法任务的人工复核、定向重放和重放失败状态契约不变。
4. `requeue=false` 仅描述当前消费者动作，不证明 broker 已隔离或保留消息；真实去向取决于队列 DLX/过期/丢弃配置。

### 14.134 死信消息头持久化脱敏契约

1. 合法死信任务在调用 Mapper 前必须对序列化后的 `header_snapshot` 执行统一敏感数据脱敏；授权头、令牌、密钥、签名、私钥和 Cookie 等凭据不得以原文写入新记录。
2. `x-retry-count` 等非敏感诊断头继续保留；历史行继续由列表、待重放、人工结案和重放动作的既有出参脱敏兼容，本契约不代表历史数据已清理。
3. 定向重放只使用持久化的原始 `payload_snapshot` 作为消息体，并重建 `compensationTaskNo`、`compensationReplay` 等受控消息头，不恢复或转发原始入站消息头。
4. 本轮不改变接口字段、Mapper、schema、迁移、人工复核和任务状态机；本地测试不证明真实 RabbitMQ 消息或生产凭据保护效果。

### 14.135 外部风控决策说明最小化契约

1. 风控响应 `data.message` 仍在去空格前拒绝 ISO 控制字符并限制为最多 `255` 个字符；非法原文继续使整个响应以 `PAYMENT_RISK_SERVICE_UNAVAILABLE` 失败闭合。
2. 合法原文校验通过后必须丢弃，不得直接返回或写入支付风险事件；payment-core 按规范化决策生成固定平台说明，分别表达准入通过、需要人工复核、风控拦截和风控拒绝。
3. `decision / decisionType / hitCode / riskTag / reviewNo / eventNo` 的既有必填、白名单、容量和规范化契约不变，固定说明不得改变支付状态机或放宽渠道调用准入。
4. 本轮不改变接口字段、Mapper、schema 或迁移；固定说明仅证明代码内数据最小化，不证明真实风控策略、人工复核或生产可用性。

### 14.136 风控人工复核关联完整性契约

1. 当规范化 `data.decision=REVIEW` 时，`data.reviewNo` 由可选字段提升为条件必填字段；去除首尾空格后必须非空、最长 `64` 个字符且原文不得包含 ISO 控制字符。
2. 缺失或非法复核号统一返回 `PAYMENT_RISK_SERVICE_UNAVAILABLE`，并在支付状态、预付单状态、风险事件、支付尝试和渠道调用前失败，不得形成无法追踪的待复核事实。
3. 普通 `PASS` 可不携带复核号，人工复核通过后的 `PASS` 可返回原复核号；复用既有待审单时 `eventNo` 仍可为空，不要求风控系统为同一审核单重复创建事件。
4. 本轮不改变接口字段、Mapper、schema 或迁移；该契约不证明复核单真实存在、审核完成或跨系统生产一致性，仍需联调与运行证据确认。

### 14.137 风控拦截事件关联完整性契约

1. 当规范化 `data.decision=INTERCEPT` 时，`data.eventNo` 由可选字段提升为条件必填字段；去除首尾空格后必须非空、最长 `64` 个字符且原文不得包含 ISO 控制字符。
2. 缺失或非法事件号统一返回 `PAYMENT_RISK_SERVICE_UNAVAILABLE`，风控适配器不得把该结果交付支付主流程，因而不得据此写入支付状态、风险事件、支付尝试或发起渠道调用。
3. `REVIEW` 继续要求 `reviewNo`；当前策略直接 `REJECT` 路径可能不返回复核号、事件号或命中编码，本轮不将上述字段错误提升为拒绝决策的必填项。
4. 本轮不改变接口字段、Mapper、schema 或迁移；该契约不证明风险事件真实存在、拦截真实发生或跨系统生产一致性，仍需联调与运行证据确认。

### 14.138 风控决策与展示样式一致性契约

1. 规范化后的 `data.decision / data.decisionType` 必须满足固定组合：`PASS / success`、`REVIEW / warn`、`INTERCEPT / danger`、`REJECT / danger`。
2. 决策和样式即使分别属于各自有限集合，只要组合不匹配，仍统一返回 `PAYMENT_RISK_SERVICE_UNAVAILABLE`；不得把矛盾结果写入风险事件或继续发起渠道调用。
3. 大小写与首尾空格继续按既有规则规范化，控制字符和 `32` 字符容量边界保持不变；本轮不改变决策状态机、固定平台说明及追踪键条件。
4. 本轮不改变接口字段、前端、Mapper、schema 或迁移；固定映射不证明真实风险决策正确、处置完成或跨系统生产一致性。

### 14.139 风控拒绝来源追踪完整性契约

1. 当规范化 `data.decision=REJECT` 时，`data.reviewNo` 与 `data.eventNo` 至少一个非空，且各自继续满足原文无 ISO 控制字符、去除首尾空格和最长 `64` 个字符的边界。
2. 人工复核拒绝由 risk-control-system 返回原 `reviewNo`；策略直接拒绝在评估事务内生成或复用风险事件，返回 `eventNo + hitCode`。两类来源不互相伪造追踪字段。
3. 两类追踪号均为空或任一已提供字段非法时统一返回 `PAYMENT_RISK_SERVICE_UNAVAILABLE`，不得写入支付状态、风险事件、支付尝试或发起渠道调用。
4. 本轮不改变接口字段、前端、Mapper、schema 或迁移；该契约不证明复核单或风险事件在生产环境真实存在、处置正确或跨系统数据已对账。

### 14.140 风控事件型终态引用完整性契约

1. `decision=INTERCEPT` 时，`eventNo` 与 `hitCode` 均为必填；二者分别用于定位风险事件和命中规则，任一缺失均返回 `PAYMENT_RISK_SERVICE_UNAVAILABLE`。
2. `decision=REJECT` 且存在 `reviewNo` 时按人工拒绝处理，不强制 `eventNo / hitCode`；不存在 `reviewNo` 时按策略直接拒绝处理，必须同时返回 `eventNo + hitCode`。
3. 所有已提供字段继续执行既有控制字符与容量校验；条件必填校验发生在支付状态、风险事件、支付尝试和渠道调用之前。
4. risk-control-system 的事件型终态说明必须与决策一致：`INTERCEPT` 表达直接拦截，`REJECT` 表达直接拒绝，不得用拦截文案描述拒绝结果。
5. 本轮不新增接口字段、错误码或对外支付接口结构；本地测试与静态核对不证明真实风险事件、策略命中、人工处置、跨系统生产一致或稳定性。

### 14.141 风控启用态端点配置失败闭合契约

1. `payment.risk-control.enabled=false` 时，支付核心继续返回本地 `PASS / success` 结果，不调用独立风控中心。
2. `payment.risk-control.enabled=true` 时，`payment.risk-control.evaluate-url` 必须包含非空白地址；缺失或全空白固定抛出 `PAYMENT_RISK_SERVICE_UNAVAILABLE`，提示“风控中心评估地址未配置，请联系资金风控团队”。
3. 启用态配置校验发生在 HTTP 请求之前；支付提交编排继续保证风控异常先于支付尝试、风险事件、支付状态更新和渠道调用。
4. 地址存在时继续去除首尾空格，并沿用超时、调用异常、业务码、决策样式和追踪字段的既有失败闭合契约。
5. 本轮不新增接口字段或错误码；本地测试不证明生产配置正确、真实风控连通、策略有效性、资金结果或稳定性。

### 14.142 外部 HTTP 超时统一有界契约

1. payment-core 通过共用 HTTP 客户端工厂将 `timeout-ms` 归一化到 `1..60000`，并将同一值同时用于连接超时和读取超时；默认支付域调用 `2000ms`、通知器 `3000ms` 不变。
2. 适用范围包括 gateway-access 支付提交/查单、退款提交/查单、支付成功下游投递、风控评估、运营配置快照、告警回执查询及 IM、短信、邮件通知，共十一类 HTTP 出站路径。
3. `timeout-ms <= 0` 按 `1ms` 执行，`timeout-ms > 60000` 按 `60000ms` 执行；中间合法值保持原值，不因归一化改变各适配器的错误码、未知结果分类或重试状态机。
4. 三类通知器的失败说明使用归一化后的实际值；其他适配器继续返回各自受控业务错误，不向上游暴露底层超时异常详情。
5. 本轮不新增对外接口字段或错误码；本地测试不证明真实网络精度、外部请求未被受理、资金结果、通知送达或生产稳定性。

### 14.143 支付成功下游投递 HTTP 超时配置契约

1. `payment.event-dispatch.http-timeout-ms` 控制支付成功事件以 HTTP 模式投递清分与会计下游时的连接和读取超时，环境变量为 `HSP_PAYMENT_EVENT_DISPATCH_HTTP_TIMEOUT_MS`，默认 `3000ms`。
2. 配置值进入共享 HTTP 客户端工厂后统一限制为 `1..60000ms`：非正值按 `1ms`，超过一分钟按 `60000ms`，合法中间值保持不变。
3. 该配置不改变支付成功事件载荷、下游地址、投递状态机、失败重试语义或 AMQP 模式契约，也不新增对外接口字段和错误码。
4. 本地配置与单元测试只证明参数解析和客户端构造边界，不证明真实清分或会计系统连通、请求已送达、外部系统受理或生产稳定性。

### 14.144 AMQP publisher confirm 超时有界契约

1. `payment.amqp.publisher-confirm-timeout-ms` 同时用于支付成功 AMQP 发布和死信定向重放，环境变量为 `HSP_PAYMENT_AMQP_PUBLISHER_CONFIRM_TIMEOUT_MS`，默认 `5000ms`。
2. 配置值小于等于 `0` 时按既有兼容语义回退 `5000ms`，正值大于 `60000` 时按 `60000ms`，其余正值保持原值，并传给 `waitForConfirmsOrDie`。
3. 超时或 broker 异常继续由各自状态机收口：支付成功事件标记失败或进入死信，死信重放记录受控失败并等待人工跟进；不得据此断言消息未送达或消费者未处理。
4. 本轮不新增配置键、对外接口字段或错误码，也不改变交换机、路由键、消息头和载荷；本地测试不证明真实 broker 确认、消息送达、消费结果或生产稳定性。

### 14.145 支付成功事件派发模式失败闭合契约

1. `payment.event-dispatch.mode` 只接受 `http` 或 `amqp`，比较时忽略普通大小写和首尾空格；环境变量仍为 `HSP_PAYMENT_EVENT_DISPATCH_MODE`，默认 `http`。
2. 缺失有效文本、拼写错误或未知模式不得默认进入 HTTP，也不得尝试 AMQP；配置校验发生在两类外部客户端调用之前。
3. 非法模式沿用支付事件发布失败和最大重试后死信的状态机，不新增错误码、接口字段或自动协议降级；修复配置后通过既有重试入口继续处理。
4. 本地测试只证明分支选择和零客户端调用，不证明真实配置中心、HTTP 下游、RabbitMQ、事件送达或生产稳定性。

### 14.146 支付成功事件状态收口完整性契约

1. HTTP 清分与会计调用均成功，或 AMQP 发布及 confirm 成功后，只有 `markPublishSuccess(eventNo) == 1` 才返回派发成功。
2. 成功状态更新为 `0` 行时不得返回成功，也不得写重发成功审计；当前事件按既有重试次数进入 `FAILED` 或 `DEAD_LETTER`。
3. 该失败发生在外部调用之后，不能解释为外部请求未受理；后续重试继续使用稳定事件号、支付单号和业务链路号，并要求下游保持幂等。
4. 本轮不新增接口字段、错误码、Mapper SQL 或状态枚举；本地测试不证明真实外部受理、跨系统幂等、生产并发或最终一致性。

### 14.147 支付成功事件金额可信边界契约

1. payment-core 从支付明细展示字段构造支付成功事件时，允许人民币符号和千分位格式，但规范化结果必须为大于 `0`、最多两位小数且不超过 `9999999999999999.99` 的 `BigDecimal`。
2. 金额为空、空白、无法解析、非正、精度或容量越界时，不调用 HTTP 清分与会计接口，也不发布 AMQP 消息；事件按当前重试次数进入 `FAILED / DEAD_LETTER`。
3. 金额解析、服务者查询、派发配置校验和外部调用共用现有异常收口，不新增对外接口字段、错误码或自动修复规则。
4. 本地测试只证明参数边界与零下游调用，不证明真实支付金额、外部系统受理、跨系统一致或生产稳定性。

### 14.148 缺失支付明细事件重试收口契约

1. 支付成功事件按 `paymentOrderId` 查询不到支付明细时不得调用清分、会计或 AMQP，也不得返回派发成功。
2. 失败处理复用最大重试次数 `3`：当前次数加一未达上限时写 `FAILED`，达到上限时写 `DEAD_LETTER`；不新增错误码、接口字段或状态枚举。
3. 自动重试查询不依赖独立次数过滤，死信转换后通过既有状态自然退出自动候选集，并转由任务中心人工处置边界承接。
4. 本地测试只证明服务分支与 Mapper 调用，不证明真实数据库状态、支付明细缺失原因、人工处置或生产稳定性。

### 14.149 事件人工重发状态门禁契约

1. `republish(eventNo, operator)` 对支付成功和退款提交事件执行服务端状态校验；普通重发只允许 `PENDING / FAILED / DEAD_LETTER`。
2. `SUCCESS`、空状态或未知状态返回失败，并且不得调用 HTTP、AMQP、退款渠道或更新事件发布状态；上层仅在返回成功时写入成功重发审计，因此拒绝路径不形成虚假执行事实。
3. 退款事件的 `UNKNOWN` 保留现有专用处理分支，继续遵循 `PAYMENT-1050` 查单与人工核查契约，不等同于普通失败事件重发。
4. 本轮不新增接口字段、错误码、状态枚举、Mapper SQL 或迁移；本地测试不证明生产事件状态、真实下游调用、资金结果或稳定性。

### 14.150 支付成功事件资金终态一致性契约

1. `PAYMENT_SUCCESS` 派发在任何 HTTP 或 AMQP 调用前重新读取支付明细，并要求 `detail.paymentOrderId` 与事件指定 `paymentOrderId` 完全一致且 `detail.status = SUCCESS`。
2. 非成功状态、空状态或支付单号不一致时不得查询服务者、构造或投递成功载荷，也不得标记事件发布成功；事件按既有最大重试规则进入 `FAILED / DEAD_LETTER`。
3. 正常回调和主动查单链路仍先原子收口支付单为 `SUCCESS`，再创建并派发成功事件；本轮校验不新增状态转换、接口字段、错误码或自动数据修复。
4. 本地测试只证明服务分支与零下游调用，不证明生产支付状态、真实清分记账、消息送达、资金到账或稳定性。

### 14.151 支付成功事件渠道追踪事实门禁契约

1. `PAYMENT_SUCCESS` 派发继续复用支付主单现有 `channel / channelTransactionNo`，要求两者分别为非空且不超过 `64 / 128` 字符，并在归一化前拒绝 ISO 控制字符。
2. 任一字段不满足条件时，不查询服务者、不调用清分、会计或 AMQP、不标记事件成功，并按既有最大重试规则进入 `FAILED / DEAD_LETTER`。
3. 本轮不改变下游 HTTP 或 AMQP 载荷字段，也不自动生成、截断或修复渠道追踪值；后续若需跨系统透传应通过版本化接口契约实施。
4. 本地测试只证明支付核心分支和零下游调用，不证明渠道真实受理、资金扣付或到账、消息消费、对账完成或生产稳定性。

### 14.152 支付事件状态条件更新契约

1. 发布成功只允许从 `PENDING / FAILED / DEAD_LETTER / UNKNOWN` 更新为 `SUCCESS`；其中 `UNKNOWN -> SUCCESS` 用于保留退款渠道结果经专用核查确认后的收口能力。
2. 发布失败只允许来源为 `PENDING / FAILED`，死信和结果未知只允许来源为 `PENDING / FAILED / DEAD_LETTER`；任何状态更新都不得覆盖已有 `SUCCESS`。
3. 最近成功重发审计只在事件当前为 `SUCCESS` 时更新一行，上层仍要求更新数恰好为 `1`；状态不符时整个重发事务失败，不形成孤立成功审计。
4. 本轮不新增接口字段、错误码、状态枚举、schema 或迁移；本地测试不证明生产 MySQL 竞争、真实下游受理、资金结果或稳定性。

### 14.153 支付事件重发有界批次契约

1. 人工与自动事件重发候选查询统一接收服务端批次参数；默认值为 `100`，部署环境可通过 `HSP_PAYMENT_EVENT_RETRY_BATCH_SIZE` 覆盖，实际值限制在 `1..1000`。
2. 人工模式按 `created_at ASC, id ASC` 选择最早的 `FAILED` 事件；自动模式按相同顺序选择已到期的 `FAILED`，以及可自动首次派发的 `PENDING` 支付成功与退款提交事件。
3. `PaymentTaskActionResultDTO.processedCount / successCount / failCount` 只表示本轮候选结果；超过批次的事件不被改写，留待后续调度或人工轮次，不新增接口字段或循环执行承诺。
4. 单事件独立事务、任务租约、成功审计与失败事件摘要保持不变；本地测试不证明生产 MySQL 查询计划、批次吞吐、真实下游受理或稳定性。

### 14.154 失败退款重试有界批次契约

1. 人工与自动失败退款重试共用服务端批次容量；默认值为 `100`，部署环境可通过 `HSP_PAYMENT_REFUND_RETRY_BATCH_SIZE` 覆盖，实际值限制在 `1..1000`。
2. 每轮只选择退款单为 `FAIL`、`terminal_at` 非空、`success_at` 为空，且最新尝试为 `FAIL` 并具备 `completed_at` 的退款，按 `terminal_at ASC, id ASC` 优先处理最早形成的失败风险；超过批次的退款不改写状态，终态事实异常退款由 P1 告警阻断。
3. `PaymentTaskActionResultDTO.processedCount / successCount / failCount` 只表示本轮执行结果；单笔失败继续隔离，摘要最多展示三个失败退款单号，不新增接口字段或自动循环清空承诺。
4. 成功计数仍只代表本地重试和渠道提交链路完成，不代表渠道最终受理或退款到账；本地测试不证明生产 MySQL 吞吐、真实渠道结果或稳定性。

### 14.155 异常告警派发有界批次契约

1. 人工与自动异常告警派发共用服务端批次容量；默认值为 `100`，部署环境可通过 `HSP_PAYMENT_ISSUE_ALERT_DISPATCH_BATCH_SIZE` 覆盖，实际值限制在 `1..1000`。
2. 每轮按 `created_at ASC, id ASC` 选择最早且仍待确认的 `IN_APP_OUTBOX` 告警；超过批次的告警不改写状态，留待后续调度或人工轮次。
3. `PaymentTaskActionResultDTO.processedCount / successCount / warningCount / failCount` 只表示本轮执行结果；自动任务租约、逐告警失败隔离、成功通道防重、供应商降级和最多三个失败告警号保持不变，不新增接口字段或自动循环清空承诺。
4. 成功或部分成功计数只代表本地派发链路及已记录的供应商响应，不代表最终送达；本地测试不证明生产 MySQL 吞吐、真实供应商受理、送达、资费结果或稳定性。

### 14.156 异常 SLA 候选有界批次契约

1. `POST /api/payment-task-center/actions/escalate-overdue-issues` 及对应自动任务分别限制首次告警候选与未确认升级候选；默认每类容量为 `100`，部署环境可通过 `HSP_PAYMENT_ISSUE_SLA_BATCH_SIZE` 覆盖，实际值限制在 `1..1000`。
2. 首次告警按 `issue_sla.created_at ASC, issue_no ASC`、未确认升级按 `source_alert.created_at ASC, source_alert.id ASC` 选择最老候选；两类分别占用完整容量，默认单轮合计最多写入 `200` 条告警，超过批次的数据不改写并留待后续轮次。
3. `PaymentTaskActionResultDTO.processedCount` 保持“逾期异常存量 + 本轮未确认升级候选数”的既有口径；`successCount / warningCount / failCount` 继续分别表达本轮生成、并发重复跳过和失败结果，本轮不新增接口字段或自动循环清空承诺。
4. 自动任务 `120` 秒租约、逐候选独立事务、生成键幂等、失败隔离和内容截断保持不变；本地测试不证明生产 MySQL 查询计划、真实通知送达、人员响应、多实例调度或稳定性。

### 14.157 异常告警回执回查批次上限契约

1. 人工批量回查与自动回执补偿继续使用 `payment.issue-alert.receipt-query.compensation.batch-size`；默认值为 `100`，部署环境可通过 `HSP_PAYMENT_ISSUE_ALERT_RECEIPT_QUERY_BATCH_SIZE` 覆盖，服务端实际值限制在 `1..1000`。
2. 人工批量入口选择全部符合状态条件中的最老一批；自动入口额外保留最小年龄、最大尝试次数、下次执行时间和抢占超时条件。超过容量的日志不改写，留待后续轮次。
3. 单条回查接口继续按 `alertNo` 精确查询，不受批量容量影响；`PaymentTaskActionResultDTO.processedCount / successCount / failCount` 继续只表达本轮批量候选或该单条结果，本轮不新增字段或自动循环清空承诺。
4. 自动任务租约、逐条独立事务、共享通道限流、条件抢占、指数退避、回执关联校验和来源告警确认保持不变；本地测试不证明真实供应商送达、回执乱序、多实例调度或生产稳定性。

### 14.158 支付与退款主动查单容量上限契约

1. 支付主动查单使用 `HSP_PAYMENT_CHANNEL_QUERY_COMPENSATION_PER_CHANNEL_BATCH_SIZE / HSP_PAYMENT_CHANNEL_QUERY_COMPENSATION_BATCH_SIZE`，退款主动查单使用对应的 `HSP_PAYMENT_REFUND_QUERY_COMPENSATION_*` 配置；默认每渠道 `20`、全局 `100`，服务端均限制在 `1..1000`。
2. 人工与自动入口共用归一化容量；候选仍先按渠道内时间和主键排名，再按全局时间顺序应用 `LIMIT`。单渠道配置大于全局配置时，最终返回数仍受全局容量约束，不自动改写部署配置。
3. `PaymentTaskActionResultDTO.processedCount / successCount / warningCount / failCount` 继续只表达本轮候选结果；超过容量的数据不改写并留待后续轮次，本轮不新增接口字段或自动循环清空承诺。
4. 人工首次等待、自动指数退避、任务租约、逐笔失败隔离、共享渠道限流和既有支付/退款状态机保持不变；本地测试不证明真实渠道查询、资金终态、生产容量或稳定性。

### 14.159 支付回调 nonce 清理批次上限契约

1. 后台清理继续使用 `payment.callback.nonce-cleanup.batch-size`，环境变量为 `HSP_PAYMENT_CALLBACK_NONCE_CLEANUP_BATCH_SIZE`；默认值与服务端最大值均为 `1000`。
2. 非正配置在调度器构造阶段拒绝启动，超过 `1000` 的正值按 `1000` 执行；签名服务公开清理入口在调用 Mapper 前重复封顶，避免旁路调用扩大删除范围。
3. 该容量只限制单轮内部清理，不新增或修改外部接口字段；超过容量的过期 nonce 保持原状态，由后续 `120` 秒租约调度轮次继续处理。
4. 最早到期优先、周期续租、双层派生表删除、唯一键防重和过期复用保持不变；本地测试不证明生产 MySQL 执行计划、锁等待、数据库时钟、真实多实例、积压吞吐或稳定性。

### 14.160 渠道回调时间窗发布前一致性契约

1. 渠道候选请求字段不变；当 `candidate.enabled=true` 时，`notifySignWindowSec` 必须为正数且满足当前 `HSP_PAYMENT_CALLBACK_NONCE_TTL_SECONDS > 2 * notifySignWindowSec`。
2. 当前默认 TTL 为 `3601` 秒，因此默认最大兼容时间窗为 `1800` 秒；自定义 TTL 不改变严格大于完整对称签名有效期的规则，等于 `2W` 仍拒绝。
3. 候选提交和批准前重建均执行该校验，拒绝时不新增候选、不写渠道运行态、不追加版本或完成批准；停用态候选可保留历史参数，重新启用时必须复验。
4. 回调验签入口继续保留 `PAYMENT-1034` 运行时兜底；本轮不新增接口字段或错误码，本地测试不证明生产配置中心一致、真实审批、并发发布、渠道回调或多实例防重。

### 14.161 渠道启用与回滚待办可执行性契约

1. 渠道启用接口字段不变；当目标状态为 `ENABLED` 时，提交阶段即要求现有 `notifySignWindowSec` 满足当前 `HSP_PAYMENT_CALLBACK_NONCE_TTL_SECONDS > 2W`，失败时不新增待复核记录。
2. 渠道回滚提交按目标版本的候选状态执行同一规则：启用态目标必须兼容当前 TTL，停用态目标允许保留历史窗口；目标版本与基线检查仍先按既有规则执行。
3. 新增/编辑候选提交及批准前重建继续校验，批准阶段不会信任提交时的 TTL 快照；本轮不新增 DTO 字段、端点或错误码，既有 `IllegalArgumentException` 响应转换保持不变。
4. 运行时 `PAYMENT-1034` 仍处理历史脏数据与旁路写入；本地测试不证明生产配置中心一致、真实审批、并发发布、渠道回调、多实例防重或稳定性。

### 14.162 渠道单日限额数据库容量一致性契约

1. 渠道候选请求字段不变；`candidate.dailyLimit` 必须大于等于 `0`、有效小数位不超过 `2`，且不超过 `9999999999999999.99`，与持久化列 `DECIMAL(18,2)` 对齐。
2. `0` 继续表达既有零额度语义；尾随零不影响合法性，但服务端不会对三位以上有效小数静默舍入或截断。
3. 候选提交与批准前候选重建均执行该规则；拒绝时不新增非法候选，或不写渠道运行态、不追加版本、不完成批准。
4. 本轮不新增 DTO 字段、端点或错误码，既有 `IllegalArgumentException` 响应转换保持不变；本地测试不证明真实审批、生产 MySQL 严格模式、配置发布或渠道资金容量。

### 14.163 退款申请金额数据库容量一致性契约

1. 退款申请端点及 DTO 字段不变；`refundAmount` 必须大于 `0`、有效小数位不超过 `2`，且不超过 `9999999999999999.99`，与 `t_refund_order.refund_amount DECIMAL(18,2)` 对齐。
2. 尾随零不影响数值合法性，但服务端不会对三位以上有效小数或超上限金额静默舍入、截断或降级为较小金额。
3. 容量校验在 Mapper 访问前执行；通过后仍必须满足原支付单存在且成功、累计有效退款金额加本次金额不超过实付金额，写入初始状态仍为 `REVIEWING`。
4. 本轮不新增接口字段或错误码，既有 `IllegalArgumentException` 响应转换保持不变；本地测试不证明真实审批、生产 MySQL 严格模式、渠道受理、退款到账或资金一致。

### 14.164 退款申请文本字段持久化契约

1. `POST /api/refunds/apply` 字段不变；`paymentOrderId` 必填且最长 `64` 字符，`refundMethod` 最长 `32` 字符，`refundReason` 最长 `255` 字符，均在入库前裁剪首尾空白。
2. 三类原始输入均不得包含控制字符；退款方式为空时回填“原路退款”，退款原因为空或未传时归一化为空串，以满足 `refund_reason NOT NULL`。
3. 文本规范化通过后才查询并锁定支付单，后续金额容量、支付成功状态与累计退款额度校验保持不变；非法文本不访问 Mapper。
4. 本轮不新增 DTO 字段、端点或错误码，不收窄既有退款方式取值；本地测试不证明任何方式已完成真实下游配置、审批、渠道受理或到账核验。

### 14.165 退款动作字段持久化契约

1. 退款详情、审批、失败重试与主动查单端点及 DTO 字段不变；`refundOrderId` 必填，裁剪后最长 `64` 字符，原始输入不得包含控制字符。
2. `RefundActionRequestDTO.remark` 可选，原始输入不得包含控制字符，裁剪后最长 `255` 字符；空值或纯空白继续解析为审批、成功、失败或重试动作的既有默认备注。
3. 退款单号门禁由详情、审批、失败重试、人工主动查单及任务重试共用；动作备注在状态 CAS 前校验，拒绝时不改变退款状态。
4. 本轮不新增 DTO 字段、端点或错误码，既有 `IllegalArgumentException` 响应转换、状态机与事件派发保持不变；本地测试不证明真实身份、审批、渠道受理或退款到账。

### 14.166 退款列表与导出查询字段契约

1. `GET /api/refunds` 与 `GET /api/refunds/export` 的参数不变；`refundOrderId / paymentOrderId / refundStatus / refundMethod` 最长分别为 `64 / 64 / 32 / 32` 个字符。
2. 四类原始输入不得包含控制字符，合法首尾空白在查询前裁剪；空单号条件归一化为不筛选，空状态和方式归一化为“全部”。
3. 两个端点共用服务层规范化入口，非法参数在 Mapper 访问前拒绝；列表页码与每页 `1..100` 边界、导出最多 `1000` 行加第 `1001` 行探测规则不变。
4. 本轮不新增 DTO 字段、端点或错误码，既有 `IllegalArgumentException` 响应转换与 Mapper SQL 保持不变；本地测试不证明生产 MySQL 查询计划或真实退款资金结果。

### 14.167 退款回调原始字段控制字符契约

1. `POST /api/refunds/callback/{channel}` 的字段不变；`channel / refundOrderId / channelRefundNo / refundStatus` 的规范化后上限分别为 `64 / 64 / 128 / 32` 个字符。
2. 四类原始值必须先通过控制字符检查，再裁剪合法首尾空白；随后渠道转小写、退款状态转大写。末尾换行等控制字符不得由裁剪消除后继续处理。
3. 控制字符或超长值统一返回 `REFUND_CALLBACK_REQUEST_INVALID`，并在签名验证、退款单查询和状态更新前拒绝；合法字段仍按规范化后的值参与既有验签。
4. 本轮不新增 DTO 字段、端点或错误码，状态白名单、渠道与交易号关联、终态幂等与冲突规则保持不变；本地测试不证明真实渠道签名、回调送达或退款到账。

### 14.168 支付回调路径渠道原始控制字符契约

1. 支付回调端点与字段不变；路径 `channel` 必须先通过原始控制字符检查，再裁剪合法首尾空白、转小写并按规范化后的 `64` 字符上限校验。
2. 支付单号、交易状态和渠道交易号继续按原始值执行既有 `64 / 32 / 128` 字符与控制字符门禁；本轮不改变请求体规范化或签名原文。
3. 路径渠道含控制字符时使用既有 `IllegalArgumentException` 响应转换，并在签名验证、支付单查询与状态更新前拒绝。
4. 本轮不新增 DTO 字段、端点或错误码，渠道匹配、交易号关联、终态幂等、冲突规则和事件派发保持不变；本地测试不证明真实渠道签名、回调送达或支付入账。

### 14.169 支付请求字段原始控制字符契约

1. 支付详情、收银台、主动查单、关闭和预付创建端点与字段不变；相关支付单号、预付单号、订单号、支付场景、客户名称和收银台标题必须先通过原始控制字符检查。
2. 原始值合法后继续执行既有空值判断、首尾空白裁剪和字段长度校验；可选字段的 `null` 或纯空白语义不变，末尾换行不得由裁剪消除后继续处理。
3. 非法字段统一返回 `PAYMENT_REQUEST_INVALID`，并在 Mapper、渠道主动查询和任何状态更新前拒绝。
4. 本轮不新增 DTO 字段、端点或错误码，不改变支付查单、关闭、预付创建或收银台业务语义；本地测试不证明真实身份、渠道受理或支付入账。

### 14.170 支付列表与导出查询字段契约

1. 支付列表与 CSV 导出端点及参数不变；`paymentOrderId / orderNo / paymentMethod / status` 最长分别为 `64 / 64 / 32 / 32` 个字符。
2. 四类原始输入不得包含控制字符，合法首尾空白在查询前裁剪；空单号条件归一化为不筛选，空支付方式和状态归一化为“全部”。
3. 两个端点共用服务层规范化入口，非法参数统一返回 `PAYMENT_REQUEST_INVALID` 并在 Mapper 访问前拒绝；列表分页 `1..100` 与导出最多 `1000` 行加第 `1001` 行探测规则不变。
4. 本轮不新增 DTO 字段、端点或错误码，既有 Mapper SQL、模糊匹配和精确筛选语义保持不变；本地测试不证明生产 MySQL 查询计划或真实支付资金结果。

### 14.171 支付提交字段原始控制字符契约

1. 支付提交端点与 DTO 字段不变；预付单号、支付方式、渠道编码、来源应用标识、商户号、访问令牌、终端、客户端 IP、设备号、付款手机号及显式幂等键必须在任何裁剪或空值判断前通过原始控制字符检查。
2. 合法原值继续执行既有字段长度、终端白名单与大小写规范化；合法首尾空白、终端别名、缺省 `UNKNOWN` 及未显式传键时的自动组合幂等规则不变。
3. 一般字段非法继续返回 `PAYMENT_SUBMIT_REQUEST_INVALID`，显式幂等键非法继续返回 `PAYMENT_SUBMIT_IDEMPOTENCY_KEY_INVALID`；失败发生在首次查库、路由、风控、限流和渠道调用前。
4. 本轮不新增端点、DTO 字段或错误码，不改变支付路由、控制策略、风控、幂等恢复或渠道提交语义；本地测试不证明真实网关鉴权、渠道受理或支付入账。

### 14.172 死信人工动作字段原始控制字符契约

1. 死信任务允许重放、人工结案和执行重放端点与字段不变；路径任务号、请求处置人与处置说明必须先通过原始控制字符检查，再裁剪合法首尾空白。
2. 规范化后的长度上限继续为 `64 / 64 / 512` 个字符；非法字段使用既有参数错误响应，并在任务查询、状态 CAS 与 RabbitMQ 调用前拒绝。
3. 成功路径继续按既有状态机记录操作人与说明，执行重放只向任务持久化的原消费者路由发送原始消息体并等待发布确认。
4. 本轮不新增端点、DTO 字段或错误码，不改变重放确认、失败脱敏、响应快照遮蔽或并发状态门禁；本地测试不证明真实审批、消息送达或下游消费完成。

### 14.173 死信列表查询字段契约

1. 死信任务列表端点与参数不变；`taskStatus / targetSystem / messageId` 最长分别为 `32 / 64 / 128` 个字符，与死信任务表查询列一致。
2. 三类原始输入不得包含控制字符，合法首尾空白在查询前裁剪；`null` 或纯空白统一表示不启用对应筛选。
3. 非法参数使用既有参数错误响应并在 Mapper 访问前拒绝；页码与每页 `1..100` 边界、排序和响应快照脱敏保持不变。
4. 本轮不新增端点、DTO 字段或错误码，不改变 Mapper SQL 或死信动作状态机；本地测试不证明生产 MySQL 查询计划、真实消息积压或 RabbitMQ 投递结果。

### 14.174 支付事件查询与人工重发字段契约

1. `GET /api/payment-events`、`GET /api/payment-events/overview` 与 `GET /api/payment-events/export` 参数不变；`paymentOrderId / eventType / publishStatus / downstreamSystem / eventTopic` 最长分别为 `64 / 64 / 32 / 256 / 128` 个字符。
2. 五类原始输入不得包含控制字符，合法首尾空白在查询前裁剪；`null` 和既有空条件继续表示不启用对应筛选。
3. `POST /api/payment-events/republish` 的 `eventNo` 继续必填、裁剪后最长 `64` 个字符，原始值不得包含控制字符；非法请求在派发和审计写入前拒绝。
4. 本轮不新增端点、DTO 字段或错误码，不改变 Mapper SQL、可重发状态、分页排序、CSV 上限或载荷脱敏；本地测试不证明生产查询计划、真实事件积压或下游投递结果。

### 14.175 支付异常与告警查询字段契约

1. 异常列表与责任汇总端点参数不变；`paymentOrderId / orderNo / issueType / severity / channelCode / paymentMethod` 最长分别为 `64 / 64 / 64 / 32 / 64 / 32` 个字符。
2. 告警明细列表与 CSV 导出端点参数不变；`alertNo / issueNo / paymentOrderId / alertChannel / alertStatus / ackStatus / providerDeliveryStatus / receiptQueryState` 最长分别为 `64 / 128 / 64 / 32 / 32 / 32 / 64 / 32` 个字符。
3. 所有原始值不得包含控制字符，合法首尾空白在查询前裁剪；`receiptQueryState` 在原值校验后再大写化并校验 `INITIAL_WAIT / DUE / BACKING_OFF / QUERYING / EXHAUSTED`。
4. 本轮不新增端点、DTO 字段或错误码，不改变 Mapper SQL、默认筛选、分页、CSV 上限或告警回执脱敏；本地测试不证明生产查询计划、真实异常积压或供应商投递/回执结果。

### 14.176 服务者结算查询字段契约

1. `GET /api/settlements/workers`、`GET /api/settlements/workers/overview` 与 `GET /api/settlements/workers/export` 参数不变；`settlementOrderId / workerKeyword / settlementStatus / payoutStatus` 最长分别为 `64 / 128 / 32 / 32` 个字符。
2. 四类原始输入不得包含控制字符，合法首尾空白在查询前裁剪；`null`、空条件和 `全部` 保持既有不筛选语义。
3. 非法参数使用既有参数错误响应并在 Mapper 访问前拒绝；页码与每页 `1..100`、金额汇总、姓名脱敏和 CSV `1000` 行上限保持不变。
4. 本轮不新增端点、DTO 字段或错误码，不改变 Mapper SQL、schema 或迁移；本地测试不证明生产结算金额、真实出款、服务者收款或生产稳定性。

### 14.177 账单查询字段契约

1. `GET /api/bills`、`GET /api/bills/overview` 与 `GET /api/bills/export` 参数不变；`billNo / orderNo / customerName / billStatus` 最长分别为 `64 / 64 / 128 / 32` 个字符。
2. 四类原始输入不得包含控制字符，合法首尾空白在查询前裁剪；`null`、空条件和 `全部` 保持既有不筛选语义。
3. 非法参数使用既有参数错误响应并在 Mapper 访问前拒绝；页码与每页 `1..100`、排序、金额汇总、姓名脱敏和 CSV `1000` 行上限保持不变。
4. 本轮不新增端点、DTO 字段或错误码，不改变 Mapper SQL、schema 或迁移；本地测试不证明生产账单金额、真实收款、账务一致性或生产稳定性。

### 14.178 支付流水查询字段契约

1. `GET /api/payment-flows` 与 `GET /api/payment-flows/export` 参数不变；`paymentOrderId / orderNo / flowType / channelCode / terminal` 最长分别为 `64 / 64 / 32 / 64 / 32` 个字符。
2. `businessStatus` 最长 `128` 个字符，`keyword` 最长 `2048` 个字符；七类原始输入不得包含控制字符，合法首尾空白在查询前裁剪。
3. `null`、空条件和既有 `全部` 保持不筛选语义；非法参数使用既有参数错误响应并在 Mapper 访问前拒绝，排序、分页、脱敏和 CSV `1000` 行上限保持不变。
4. 本轮不新增端点、DTO 字段或错误码，不改变 Mapper SQL、schema 或迁移；本地测试不证明生产查询计划、渠道报文真实性、支付入账或生产稳定性。

### 14.179 支付路由执行查询字段契约

1. `GET /api/payment-routes`、`GET /api/payment-routes/overview` 与 `GET /api/payment-routes/export` 参数不变；`paymentOrderId / orderNo / routeRule / channelCode / paymentMethod / terminal / routeResult` 最长分别为 `64 / 64 / 128 / 64 / 32 / 32 / 128` 个字符。
2. 七类原始输入不得包含控制字符，合法首尾空白在查询前裁剪；`null`、空条件和既有 `全部` 保持不筛选语义。
3. 非法参数使用既有参数错误响应并在 Mapper 访问前拒绝；排序、分页、路由健康概览、敏感字段脱敏和 CSV `1000` 行上限保持不变。
4. 本轮不新增端点、DTO 字段或错误码，不改变 Mapper SQL、schema 或迁移；本地测试不证明真实路由决策、渠道健康、支付受理或生产稳定性。

### 14.180 支付处理日志查询字段契约

1. `GET /api/payment-logs`、`GET /api/payment-logs/overview` 与 `GET /api/payment-logs/export` 参数不变；`paymentOrderId / orderNo / processStage / logLevel / source` 最长分别为 `64 / 64 / 32 / 16 / 64` 个字符。
2. `keyword` 最长 `16383` 个字符；六类原始输入不得包含控制字符，合法首尾空白在查询前裁剪，`null`、空条件和既有 `全部` 保持不筛选语义。
3. 非法参数使用既有参数错误响应并在 Mapper 访问前拒绝；排序、分页、概览统计和 CSV `1000` 行上限保持不变。
4. 本轮不新增端点、DTO 字段或错误码，不改变 Mapper SQL、schema 或迁移；本地测试不证明生产日志完整性、外部检索能力、支付事实或生产稳定性。

### 14.181 收银台会话查询字段契约

1. `GET /api/cashier-sessions`、`GET /api/cashier-sessions/overview` 与 `GET /api/cashier-sessions/export` 参数不变；`sessionNo / paymentOrderId / orderNo / customerName / terminal / sessionStatus` 最长分别为 `64 / 64 / 64 / 128 / 32 / 32` 个字符。
2. 六类原始输入不得包含控制字符，合法首尾空白在查询前裁剪；`null`、空条件和既有 `全部` 保持不筛选语义。
3. 非法参数使用既有参数错误响应并在 Mapper 访问前拒绝；排序、分页、失效状态派生、临近失效统计、姓名脱敏和 CSV `1000` 行上限保持不变。
4. 本轮不新增端点、DTO 字段或错误码，不改变 Mapper SQL、schema 或迁移；本地测试不证明真实会话有效性、渠道受理、支付入账或生产稳定性。

### 14.182 订单查询字段契约

1. `GET /api/orders` 与 `GET /api/orders/export` 参数不变；`orderNo / serviceType / orderStatus` 最长分别为 `64 / 64 / 32` 个字符。
2. 三类原始输入不得包含控制字符，合法首尾空白在查询前裁剪；`null`、空条件和既有 `全部` 保持不筛选语义。
3. 非法参数使用既有参数错误响应并在 Mapper 访问前拒绝；页码与每页 `1..100`、姓名脱敏和 CSV `1000` 行上限保持不变。
4. 本轮不新增端点、DTO 字段或错误码，不改变 Mapper SQL、schema 或迁移；本地测试不证明真实订单履约、渠道受理、支付入账或生产稳定性。

### 14.183 支付请求查询字段契约

1. `GET /api/payment-requests`、`GET /api/payment-requests/overview` 与 `GET /api/payment-requests/export` 参数不变；`requestNo / paymentOrderId / orderNo / channelCode / terminal / clientIp / requestStatus` 最长分别为 `64 / 64 / 64 / 64 / 32 / 64 / 32` 个字符。
2. 七类原始输入不得包含控制字符，合法首尾空白在查询前裁剪；请求状态中文别名在校验后映射，`null`、空条件和既有 `全部` 保持不筛选语义。
3. 非法参数使用既有参数错误响应并在 Mapper 访问前拒绝；排序、分页、请求总览、敏感字段脱敏和 CSV `1000` 行上限保持不变。
4. 本轮不新增端点、DTO 字段或错误码，不改变 Mapper SQL、schema 或迁移；本地测试不证明真实渠道请求、渠道受理、支付入账或生产稳定性。

### 14.184 支付记录查询字段契约

1. `GET /api/payment-records` 参数不变；`recordType / userId / businessOrderNo / paymentType / paymentStatus / paymentChannel` 最长分别为 `16 / 128 / 64 / 32 / 32 / 64` 个字符。
2. `GET /api/payment-records/{paymentOrderId}` 的支付单号要求非空且最长 `64` 个字符；列表与详情共七类原始输入不得包含控制字符，合法首尾空白在查询前裁剪。
3. 非法列表参数使用既有参数错误响应，非法详情标识使用 `PAYMENT_REQUEST_INVALID`，且均在 Mapper 访问前拒绝；详情不存在仍使用 `PAYMENT_ORDER_NOT_FOUND`。
4. 未知记录维度兼容、默认筛选、排序、分页、敏感字段脱敏、详情轨迹和成功退款聚合保持不变；本轮不新增端点、DTO 字段、Mapper SQL、schema 或迁移。
5. 本地测试不证明真实支付、退款到账、渠道受理、账务一致性或生产稳定性。

### 14.185 任务运行日志查询字段契约

1. `GET /api/payment-task-center/task-runs` 与 `GET /api/payment-task-center/task-runs/export` 参数不变；`taskCode / runMode / taskStatus / severityLevel` 最长分别为 `64 / 32 / 32 / 32` 个字符。
2. 四类原始输入不得包含控制字符，合法首尾空白在查询前裁剪；`null`、空条件和既有 `全部` 保持不筛选语义。
3. 非法参数使用既有参数错误响应并在 Mapper 访问前拒绝；列表分页 `1..100`、严重等级与升级信息派生和 CSV `1000` 行上限保持不变。
4. 本轮不新增端点、DTO 字段或错误码，不改变 Mapper SQL、schema 或迁移；本地测试不证明生产调度、真实任务执行、补偿结果、告警送达或生产稳定性。

### 14.186 支付事件派发标识字段契约

1. 现有 HTTP 端点、DTO 和响应结构不变；人工重发请求的 `eventNo` 仍要求非空且最长 `64` 个字符，原始值不得包含 ISO 控制字符，合法首尾空白在派发前裁剪。
2. 共享服务 `publishPaymentSuccess(eventNo, paymentOrderId)` 对两个标识均执行相同门禁，`publishRefundSubmit(eventNo, refundOrderId, operator)` 对事件号和退款单号执行相同门禁；三类标识上限均为 `64`。
3. 非法标识沿用既有参数错误响应并在 Mapper、渠道适配器或下游调用前拒绝；事件状态、重发操作人、事务传播、失败退避、死信及审计字段契约不变。
4. 本地测试不证明真实渠道受理、下游投递、资金结果、账务一致性或生产稳定性，仍需正式环境核验。

### 14.187 运营配置快照请求维度字段契约

1. payment-core 调用既有 `GET /api/ops-config/effective-snapshot` 时，`businessCode / payType / terminalType` 均要求非空且最长 `64` 个字符，原始值不得包含 ISO 控制字符，合法首尾空白在 URI 构造前裁剪。
2. 非法三维请求不发起 HTTP，客户端继续返回无快照；支付请求仅在需要快照补齐缺省支付方式时使用既有 `PAYMENT_OPS_CONFIG_SNAPSHOT_INVALID` 收口，不新增端点、DTO 字段或错误码。
3. 统一响应 `code=0`、请求与响应三维忽略大小写匹配、非负快照版本、主备渠道、路由策略和启用控制项完整性契约不变。
4. 本地测试不证明真实 ops-config 发布、跨进程网络、缓存或刷新一致性、渠道受理和生产稳定性，仍需正式环境核验。

### 14.188 风控评估请求字段契约

1. `PaymentRiskControlService.evaluateSubmitRisk` 启用远端联动时要求请求非空；`businessNo / sourceSystem / sceneCode / payScene / paymentMethod / channelCode / merchantNo / terminal / clientIp` 必填，`clientDeviceId / payerPhone` 可选。
2. 上述字符串最长分别为 `64 / 64 / 64 / 32 / 32 / 64 / 128 / 32 / 64 / 128 / 32` 个字符，均在裁剪前拒绝 ISO 控制字符；可选纯空白字段归一化为 `null`。
3. `amount` 必须大于 `0`、最多两位小数且不超过 `9999999999999999.99`。任一请求字段非法均固定返回 `PAYMENT_RISK_SERVICE_UNAVAILABLE`，并在 HTTP 调用前失败关闭。
4. 合法请求以独立规范化副本发往 `POST /api/risk-control/decisions/evaluate`，不得修改调用方持有的 DTO；禁用态本地放行、启用态地址缺失错误及响应可信边界保持不变。
5. 本轮不新增端点、DTO 字段、错误码、Mapper、schema 或迁移；本地测试不证明真实风控策略、网络连通、人工复核、渠道受理、资金结果或生产稳定性。

### 14.189 gateway 支付提交请求字段契约

1. `GatewayAccessPaymentChannelSubmitAdapter.submit` 要求 `paymentOrderId / prepayOrderNo / orderNo / payScene / paymentMethod / sourceAppId / resolvedChannelCode / terminal / clientIp / idempotencyKey` 必填，`customerName / requestedChannelCode` 可选。
2. 按 DTO 顺序，十二类字符串最长分别为 `64 / 64 / 64 / 32 / 128 / 32 / 64 / 64 / 64 / 32 / 64 / 128` 个字符；原始值在裁剪前拒绝 ISO 控制字符，可选纯空白字段归一化为 `null`。
3. `amount` 必须大于 `0`、去除末尾零后的有效小数位不超过两位且不超过 `9999999999999999.99`。任一请求字段非法均返回 `PAYMENT_CHANNEL_SUBMIT_FAILED`，并在 gateway-access HTTP 调用前失败关闭。
4. 合法请求以独立规范化副本发送，响应摘要的 `channelCode` 使用同一规范化 `resolvedChannelCode`；调用方 DTO 不被修改，既有响应校验、返回码标准化和结果未知保护保持不变。
5. 本轮不新增端点、DTO 字段、错误码、Mapper、schema 或迁移；本地测试不证明真实网关、渠道受理、资金结果、账务一致性或生产稳定性。

### 14.190 gateway 支付主动查单请求字段契约

1. `GatewayAccessPaymentChannelQueryAdapter.query` 要求 `PaymentDetailDTO` 非空，`paymentOrderId / orderNo / channelCode` 必填且最长均为 `64` 个字符，`channelTransactionNo` 可选且最长为 `128` 个字符。
2. 四类原始字符串在裁剪前拒绝 ISO 控制字符，可选纯空白渠道流水号归一化为 `null`；任一请求字段非法均固定返回 `PAYMENT_CHANNEL_QUERY_FAILED`，并在 gateway-access HTTP 调用前失败关闭。
3. 合法请求以独立 `GatewayQueryRequest` 规范化副本发送，不修改调用方持有的 `PaymentDetailDTO`；既有 gateway 业务成功码、响应数据、交易状态和查询来源校验保持不变。
4. 上层 `PaymentServiceImpl.queryChannel` 继续按既有逻辑记录查单失败审计；本轮不新增端点、DTO 字段、错误码、Mapper、schema 或迁移。
5. 本地测试不证明真实网关、渠道查单、资金结果、账务一致性或生产稳定性。

### 14.191 gateway 退款主动查单请求字段契约

1. `GatewayAccessRefundChannelQueryAdapter.query` 要求 `RefundCallbackSourceDTO` 非空，`refundOrderId / channelCode` 必填且最长均为 `64` 个字符，`channelRefundNo` 可选且最长为 `128` 个字符。
2. 三类原始字符串在裁剪前拒绝 ISO 控制字符，可选纯空白渠道退款号归一化为 `null`；任一请求字段非法均固定返回 `REFUND_CHANNEL_QUERY_FAILED`，并在 gateway-access HTTP 调用前失败关闭。
3. 合法请求以独立 `GatewayRefundQueryRequest` 规范化副本发送，不修改调用方持有的 `RefundCallbackSourceDTO`；退款提交结果未知且尚无渠道退款号时仍允许按平台退款单号核查。
4. 既有 gateway 业务成功码、响应数据、退款状态、渠道退款号和查询来源校验保持不变；上层 `RefundServiceImpl.queryChannel` 继续按既有逻辑记录退款查单失败审计。
5. 本轮不新增端点、DTO 字段、错误码、Mapper、schema 或迁移；本地测试不证明真实网关、渠道退款查单、退款到账、账务一致性或生产稳定性。

### 14.192 gateway 退款提交请求字段契约

1. `GatewayAccessRefundChannelSubmitServiceImpl.submit` 要求 `refundOrderId / paymentOrderId / orderNo / refundMethod / channelCode` 必填，`customerName / refundReason` 可选。
2. 按出站 DTO 顺序，七类字符串最长分别为 `64 / 64 / 64 / 128 / 32 / 255 / 64` 个字符；原始值在裁剪前拒绝 ISO 控制字符，可选纯空白字段归一化为 `null`。
3. `refundAmount` 必须大于 `0`、去除末尾零后的有效小数位不超过两位且不超过 `9999999999999999.99`。任一请求字段非法均返回 `REFUND_CHANNEL_SUBMIT_FAILED`、`resultUnknown=false`，并在 gateway-access HTTP 调用前失败关闭。
4. 合法请求以独立 `GatewayRefundSubmitRequest` 发送且不修改调用方 DTO；该合同 DTO 不包含内部 `refundStatus / submittedChannelCode / channelRefundNo`。既有响应校验和受理结果规范化保持不变。
5. 可能已触达上游的网络、空响应或非确定拒绝继续返回 `resultUnknown=true` 并要求先查单；本轮不新增端点、外部 DTO 字段、错误码、Mapper、schema 或迁移，本地测试不证明真实退款受理或到账。

### 14.193 支付成功事件参与方字段契约

1. `PaymentEventDispatchServiceImpl` 派发支付成功事件时要求 `orderNo / customerName` 必填，最长分别为 `64 / 128` 个字符；订单查询返回的 `workerName` 可为空，非空时最长为 `128` 个字符。
2. 三类原始字段在裁剪前拒绝 ISO 控制字符；服务者名称为空时归一化为“待分配服务者”。客户与服务者名称可裁剪首尾空白；订单号须遵循 14.454 的原样身份校验，不能通过裁剪使别名合法。非法值在清分 HTTP、账务 HTTP 或 AMQP 调用前进入既有事件失败状态机。
3. 规范化值通过局部参数同时传入清分载荷、账务载荷和 AMQP 载荷，不修改查询得到的原 `PaymentDetailDTO`；三种出站路径的订单号、客户名称和服务者名称口径一致。
4. 支付单号匹配、成功状态、渠道与渠道流水号、金额、HTTP/AMQP 配置和 publisher confirm 等既有校验保持不变；HTTP 模式下清分成功后账务失败仍可能由事件重试再次调用清分。
5. 本轮不新增端点、DTO 字段、错误码、Mapper、schema 或迁移；本地测试不证明真实清分、真实入账、消息送达、资金结果、账务一致性或生产稳定性。

### 14.194 死信补偿消息元数据契约

1. `PaymentDeadLetterTaskService.intake` 要求消息与消息属性非空；死信路由键必须非空、匹配既有受控模式且最长 `128` 个字符，原始值在匹配前拒绝 ISO 控制字符。
2. 已提供的 `messageId` 与可选 `correlationId` 最长均为 `128` 个字符，原始值在裁剪前拒绝 ISO 控制字符；合法值以裁剪副本入账，消息 ID 缺失时继续由消息体生成确定性匿名 ID。
3. 非法路由抛出固定脱敏的 `UnsupportedDeadLetterRouteException`，非法消息元数据抛出固定脱敏的 `InvalidDeadLetterMessageException`；监听器对两者执行 `basicNack(requeue=false)`，其他运行时失败仍执行 `basicNack(requeue=true)`。
4. `replay` 在 broker 调用前要求持久化任务号、消息 ID、exchange 和 routing key 非空且符合 `64 / 128 / 128 / 128` 容量，可选关联 ID 最长 `128`，载荷不得为 `null`；非法 envelope 记录既有脱敏失败说明且不调用 RabbitMQ。
5. 原始载荷重放、消息头最小化、人工复核 CAS、定向路由与 publisher confirm 保持不变；本轮不新增 HTTP 端点、DTO 字段、Mapper、schema 或迁移，本地测试不证明真实消息送达或补偿结果。

### 14.195 支付监控前端消费契约

1. `GET /api/payment-monitor/overview` 端点与响应 DTO 不变；`trends` 继续按 Mapper 的 `statDate DESC` 返回，前端以数组首项作为最新趋势快照。
2. `channelMetrics[].riskLevel` 当前稳定输出 `P1 / P2 / 正常`，前端将 `P1` 计入高风险渠道，并兼容历史或替代展示值“高 / HIGH”；风险样式仍使用 `riskLevelType`。
3. `trends / channelMetrics / alerts` 允许为空数组，前端分别显示空状态；`summary` 缺失字段继续使用既有零值兜底，不新增错误码或隐式成功事实。
4. 本轮不修改 Controller、Service、DTO、Mapper SQL、schema 或迁移；本地 mock 不证明真实指标采集、告警生成、渠道状态或生产稳定性。

### 14.196 运营风险总览展示兼容契约

1. 支付交易异常、支付路由执行结果和支付配置中心继续消费各自既有接口与字段；共享样式只恢复 `overview-panel / overview-grid / overview-card / overview-title` 的展示语义。
2. `danger / warn / info` 继续作为前端视觉状态修饰类，业务风险等级、数量与说明仍由原页面数据模型提供，不通过 CSS 推导或修改接口事实。
3. 空值、加载失败和接口异常继续沿用各页面既有处理，本轮不新增端点、DTO 字段、错误码、Mapper SQL、schema 或迁移。
4. 生产构建与本地浏览器计算样式通过不代表真实异常、路由或配置治理事实已核验。

### 14.197 运营快照失败状态消费契约

1. `GET /api/payment-monitor/overview`、`GET /api/payment-routes`、`GET /api/payment-routes/overview`、`GET /api/payment-issues` 和 `GET /api/payment-issues/responsibility-summary` 的端点、参数、响应 DTO 与错误码保持不变。
2. 前端仅在页面所需请求全部业务成功后更新可展示快照；HTTP 失败、非 `code=0` 或无法解析响应不得用初始化零值或空数组伪装为成功结果。
3. 页面从未成功加载时显示无可用快照；已有成功快照时，后续失败保留旧数据并显式标记其非本次成功结果。合法成功空数组仍按普通业务空态处理。
4. 支付交易异常页面在任一当前查询错误存在期间，不得基于保留的旧列表调用 `POST /api/payment-issues/actions`；批量按钮与选择入口保持禁用。
5. 本轮不新增后端状态、缓存字段、DTO、Mapper SQL、schema 或迁移；前端保留仅限当前页面内存生命周期，不构成服务端数据版本或生产新鲜度承诺。

### 14.198 人工任务与日终前端消费契约

1. 支付任务中心总览、任务日志列表、十个人工执行端点及支付日终总览/运行端点的参数、响应 DTO、错误码和写入语义保持不变。
2. 任务中心首次展示以总览和任务日志同时业务成功为条件，任一失败不得提交部分 UI 快照；后续日志筛选失败只影响日志读模型，不反向改变已成功的人工任务 POST 结果。
3. 人工任务 POST 成功后，前端使用响应中的 `overview / processedCount / successCount / warningCount / failCount` 确认执行结果，再独立请求任务日志；日志 GET 失败必须单独提示并保留旧日志。
4. 顶部总览刷新失败时，任务中心人工执行入口与日终跑批入口保持禁用；同一任务中心页面任一人工任务未完成期间，不得并发调用其他人工任务端点。
5. 本轮不新增端点、DTO、服务端幂等键、Mapper SQL、schema 或迁移；前端门禁不替代服务端事务、租约、并发控制和真实资金事实核验。

### 14.199 告警与死信人工动作前端消费契约

1. 异常告警列表/确认/单条回查和死信任务列表/允许重放/人工结案/执行重放端点、参数、响应 DTO、错误码及服务端状态机保持不变。
2. 前端仅在列表业务成功后建立可操作快照；查询失败不得用初始化空数组或零指标表示成功，已有快照刷新失败时保留数据但禁止行级写调用和翻页。
3. 人工动作 POST 与后续列表 GET 是两个独立请求事实；POST 成功后 GET 失败必须保留动作成功结论并单独标记旧列表，不能进入 POST 失败文案。
4. 查询错误与请求参数校验、人工动作失败、回查结果中的有限失败分别展示；任一行动作等待响应时，同页其他行写入口保持禁用。
5. 本轮不新增端点、DTO、Mapper SQL、schema 或迁移；前端并发门禁不替代告警幂等、死信 CAS、RabbitMQ 发布确认或目标消费者幂等处理。

### 14.200 支付事件重发前端消费契约

1. `GET /api/payment-events`、`GET /api/payment-events/overview` 和 `POST /api/payment-events/republish` 的参数、响应 DTO、错误码和服务端重发语义保持不变；重发 POST 返回当前筛选与分页条件下的事件列表，不返回总览。
2. 首次展示以列表与总览同时业务成功为条件，任一查询失败不得提交部分 UI 快照或用初始化零值表示成功；合法空列表与零值总览仍按成功结果消费。
3. 已有快照刷新失败时，前端保留旧列表与总览但禁止重发和翻页；查询错误、请求参数校验、重发失败与成功提示使用独立状态。
4. 重发 POST 成功后先提交返回列表并更新当前事件侧栏，再独立调用总览 GET；总览 GET 失败必须保留重发成功事实并标记旧总览，不得进入重发失败分支。
5. 同页任一重发请求未完成期间不得并发调用另一事件重发；`SUCCESS / UNKNOWN` 继续禁止重发。前端门禁不替代服务端事件行锁、状态机、publisher confirm 或下游幂等，发布确认不代表消费、入账或业务完成。

### 14.201 支付单人工动作前端消费契约

1. `GET /api/payments`、`POST /api/payments/admin/query` 和 `POST /api/payments/admin/close` 的参数、响应 DTO、错误码与服务端状态机保持不变；两个人工 POST 均返回完整 `PaymentDetailDTO`，列表 GET 返回 `PageResultDTO<PaymentListItemDTO>`。
2. 前端仅在列表业务成功后建立可操作快照；首次失败不得用初始化零值或空数组表示成功，已有快照刷新失败时保留数据但禁止查单、关闭和翻页。
3. `PaymentDetailDTO` 覆盖列表行所需的支付单号、订单号、客户、金额、支付方式、渠道、渠道流水、状态、状态样式和创建时间；人工 POST 成功后可据此先更新目标行与侧栏。
4. 人工 POST 与后续列表 GET 是两个独立请求事实；POST 成功后 GET 失败必须保留动作结果并单独标记其他列表旧快照，不得进入查单或关闭失败分支。
5. 同页任一人工动作未完成期间不得并发调用其他支付单查单或关闭；前端门禁不替代服务端操作人校验、状态资格、事务、幂等与渠道查询结果核验，本轮不新增端点或 DTO 字段。

### 14.202 退款单人工动作前端消费契约

1. `GET /api/refunds`、`POST /api/refunds/apply`、`POST /api/refunds/approve`、`POST /api/refunds/query` 和 `POST /api/refunds/retry` 的参数、响应 DTO、错误码与服务端状态机保持不变；四类人工 POST 均返回完整 `RefundDetailDTO`，列表 GET 返回分页退款列表。
2. 前端仅在列表业务成功后建立可操作快照；首次失败不得用初始化零值或空数组表示成功，已有快照刷新失败时保留数据但禁止申请、审核、查单、重试和翻页。成功空列表仍允许展示申请入口，实际资格由 POST 服务端校验。
3. `RefundDetailDTO` 覆盖列表行展示所需字段；审核、查单和重试 POST 成功后可据此先更新目标行与侧栏。申请 POST 成功保留返回退款单号，但当前筛选匹配关系未知时不得在前端自行构造列表事实。
4. 四类人工 POST 与后续列表 GET 是两个独立请求事实；POST 成功后 GET 失败必须保留动作结果并单独标记列表旧快照，不得进入申请、审核、查单或重试失败分支。
5. 同页任一人工动作未完成期间不得并发调用其他退款动作或筛选加载；`REVIEWING / PROCESSING / FAIL` 的既有资格门禁不变。前端保护不替代服务端操作人校验、状态资格、事务、幂等、渠道查询与到账核验，本轮不新增端点或 DTO 字段。

### 14.203 支付与退款详情动作前端消费契约

1. 支付单详情、退款详情和支付记录详情 GET，以及支付单查单/关闭、退款审核/查单/重试 POST 的端点、参数、错误码和服务端状态机保持不变。
2. 三页仅在对应详情 GET 业务成功后建立可操作快照；首次失败禁止人工动作，已有快照刷新失败时保留数据但保持只读。人工 POST 与后续详情 GET 必须作为两个独立请求事实消费。
3. 支付单人工 POST 返回完整 `PaymentDetailDTO`，退款人工 POST 返回完整 `RefundDetailDTO`，可在成功后先更新对应领域详情；后续同类详情 GET 失败不得进入 POST 失败分支。
4. 支付记录详情 GET 返回 `PaymentRecordDetailDTO`，主动查单 POST 返回 `PaymentDetailDTO`；两者结构与语义不同，前端不得以查单响应覆盖支付记录详情，只能展示查单状态结论并独立刷新记录详情。
5. 同页任一人工动作未完成期间不得并发调用其他动作或刷新；旧快照期间继续失败关闭写入口。前端门禁不替代服务端操作人校验、状态资格、事务、幂等与渠道结果核验，本轮不新增端点或 DTO 字段。

### 14.204 订单发起支付前端消费契约

1. `GET /api/orders` 与 `POST /api/payments/prepay` 的参数、响应 DTO、错误码和服务端状态机保持不变；列表返回分页 `OrderListItemDTO`，预付单 POST 返回 `PrepayOrderDTO`。
2. 前端仅在订单列表业务成功后建立可操作快照；首次失败不得用空数组表示正常空结果，已有快照刷新失败时保留数据但禁止发起支付和翻页。合法成功空列表仍按业务空态消费。
3. 预付单 POST 与后续订单列表 GET 是两个独立请求事实；POST 成功后必须保留 `prepayOrderNo / paymentOrderId` 与收银台入口，GET 失败不得进入发起支付失败分支。
4. `PrepayOrderDTO` 不覆盖订单行的服务品类、服务者、订单/履约状态、聚合支付状态等完整字段，前端不得据此构造新的 `OrderListItemDTO` 或推导当前筛选命中。
5. 同页任一预付单请求未完成期间不得并发发起其他订单支付或改变查询快照；前端门禁不替代服务端订单资格、幂等、异参冲突、事务和并发锁，本轮不新增端点或 DTO 字段。

### 14.205 异常批量处理前端消费契约

1. `GET /api/payment-issues`、`GET /api/payment-issues/responsibility-summary` 和 `POST /api/payment-issues/actions` 的参数、响应 DTO、错误码与服务端事务语义保持不变。
2. 批量 POST 返回 `PageResultDTO<PaymentIssueRowDTO>`，但服务端当前使用默认 `PaymentIssueQueryDTO` 查询，不携带调用页面的筛选与页码；前端不得将其视为当前筛选页或直接覆盖现有列表。
3. POST 业务成功只确认所选异常动作已提交；前端随后以动作发起前的查询参数并行刷新列表与责任组统计，只有两项都成功才提交新的完整页面快照。
4. POST 与后续两项 GET 是独立请求事实；任一 GET 失败必须保留动作成功结论和旧快照，不得显示批量处理失败或推导目标行的新状态。
5. 同页任一批量动作未完成期间不得改变选择、动作表单或筛选快照；前端门禁不替代服务端批量上限、字段规范化、事务、告警确认与并发控制，本轮不新增端点或 DTO 字段。

### 14.206 配置总览前端消费契约

1. `GET /api/payment-config` 的端点、响应结构与错误码保持不变；本轮仅调整前端对成功、首次失败和已有快照刷新失败的消费方式。
2. 前端只有在总览业务成功后才建立可操作配置快照；客户端初始化数组和治理零值不得作为成功响应展示，首次失败不得开放任何配置写入或审核入口。
3. 已有快照刷新失败时保留上次成功响应，但必须整体只读；渠道、路由、协议、返回码、网关、控制策略、通知供应商和值班路由各自的配置事实不得由错误状态或其他子域响应替代。
4. 总览刷新成功后清除旧错误并重新建立操作资格；刷新请求进行中不得提交依赖当前配置基线的候选或审核动作，顶部恢复刷新仍是独立 GET。
5. 前端门禁不改变服务端 maker-checker、基线指纹、版本、事务或权限语义，本轮不新增端点或 DTO 字段；治理快照导出也不作为当前页面总览成功的替代事实。

### 14.207 渠道审核与后续读取前端消费契约

1. 渠道审核批准/驳回 POST、配置总览 GET 和渠道版本 GET 的端点、参数、响应 DTO 与错误码保持不变；三类请求作为独立事实消费。
2. 审核 POST 业务成功即确认服务端已处理申请，前端应提交返回的审核列表与动作结论；批准成功后再刷新总览及当前打开的版本历史，后续 GET 失败不得进入 POST 失败分支。
3. 驳回成功不刷新运行配置；批准后总览与版本 GET 可以并行，但各自只更新所属读域，错误也必须分域展示。
4. 总览 GET 失败触发现有配置旧快照只读门禁；版本 GET 失败不得覆盖全局审核结果，也不得据此否定批准响应或推导版本已追加。
5. 前端活动锁不改变服务端 maker-checker、可信身份、基线指纹、版本追加或事务语义，本轮不新增端点或 DTO 字段。

### 14.208 网关审核与后续读取前端消费契约

1. 网关审核批准/驳回 POST、配置总览 GET 和网关版本 GET 的端点、参数、响应 DTO 与错误码保持不变；三类请求作为独立事实消费。
2. 审核 POST 业务成功即确认服务端已处理申请，前端应提交返回的审核列表与动作结论；批准成功后再刷新总览及当前打开的网关版本历史，后续 GET 失败不得进入 POST 失败分支。
3. 驳回成功不刷新运行配置；批准后总览与版本 GET 可以并行，但各自只更新所属读域，错误也必须分域展示。
4. 总览 GET 失败触发现有配置旧快照只读门禁；网关版本 GET 失败不得覆盖全局审核结果，也不得据此否定批准响应、版本追加、证书状态或网关可用性。
5. 前端活动锁不改变服务端 maker-checker、基线指纹、版本追加、证书治理或事务语义，本轮不新增端点或 DTO 字段。

### 14.209 控制策略审核与后续读取前端消费契约

1. 控制策略审核批准/驳回 POST、配置总览 GET 和策略版本 GET 的端点、参数、响应 DTO 与错误码保持不变；三类请求独立消费。
2. 审核 POST 成功即确认申请已处理，前端先提交审核列表及动作结论；批准成功后再刷新总览及当前版本历史，后续 GET 失败不得进入 POST 失败分支。
3. 驳回成功不刷新运行配置；批准后的两个 GET 可并行，各自仅更新所属读域。策略版本加载错误不得覆盖“请重新执行自检”的动作结论。
4. 总览失败继续触发完整配置旧快照只读；版本失败不能否定审核、版本追加或自检重置事实，最新自检状态仍等待独立服务端动作确认。
5. 前端活动锁不改变服务端 maker-checker、基线指纹、版本、自检失效、限流与事务语义，本轮不新增端点或 DTO 字段。

### 14.210 路由规则审核与后续读取前端消费契约

1. 路由审核批准/驳回 POST、配置总览 GET 和规则版本 GET 的端点、参数、响应 DTO 与错误码保持不变；三类请求独立消费。
2. 审核 POST 成功即确认申请已处理，前端先提交审核列表及动作结论；批准成功后再刷新总览及当前版本历史，后续 GET 失败不得进入 POST 失败分支。
3. 驳回成功不刷新运行规则；批准后的两个 GET 可并行，各自仅更新所属读域。规则版本加载错误不得覆盖审核结果。
4. 总览失败继续触发完整配置旧快照只读；版本失败不能否定审核或版本追加事实，冲突检测也不由任何审核响应隐式推导。
5. 前端活动锁不改变服务端 maker-checker、基线指纹、表达式校验、稳定分桶、版本或事务语义，本轮不新增端点或 DTO 字段。

### 14.211 支付协议审核与后续读取前端消费契约

1. 协议审核批准/驳回 POST、配置总览 GET 和协议版本 GET 的端点、参数、响应 DTO 与错误码保持不变；三类请求独立消费。
2. 审核 POST 成功即确认申请已处理，前端先提交审核列表及动作结论；批准成功后再刷新总览及当前版本历史，后续 GET 失败不得进入 POST 失败分支。
3. 驳回成功不刷新运行协议；批准后的两个 GET 可并行，各自仅更新所属读域。协议版本加载错误不得覆盖审核结果。
4. 总览失败继续触发完整配置旧快照只读；版本失败不能否定审核或版本追加事实，也不能证明法务审批、签章或协议实际生效。
5. 前端活动锁不改变服务端 maker-checker、基线指纹、版本、模板与事务语义，本轮不新增端点或 DTO 字段。

### 14.212 返回码审核与总览读取前端消费契约

1. 单条返回码审核 POST、CSV 导入审核 POST 和配置总览 GET 的端点、参数、响应 DTO 与错误码保持不变；写请求与总览读取独立消费。
2. 两类审核 POST 成功即确认申请已处理，前端先提交对应审核列表及动作结论；批准成功后再刷新总览，后续 GET 失败不得进入 POST 失败分支。
3. 驳回成功不刷新运行映射；单条状态审核响应和批量导入审核响应各自只更新所属审核列表，不得互相替代。
4. 总览失败触发完整配置旧快照只读，但不能否定审核、批量事务或映射写入事实，也不能证明真实渠道码表正确。
5. 前端活动锁不改变服务端 maker-checker、基线指纹、批量校验、归档或事务语义，本轮不新增端点或 DTO 字段。

### 14.213 通知治理审核与总览读取前端消费契约

1. 通知供应商审核 POST、值班路由审核 POST 和配置总览 GET 的端点、参数、响应 DTO 与错误码保持不变；写请求与总览读取独立消费。
2. 两类审核 POST 成功即确认申请已处理，前端先提交对应审核列表及动作结论；批准成功后再刷新总览，后续 GET 失败不得进入 POST 失败分支。
3. 驳回成功不刷新运行配置；供应商审核响应和值班路由审核响应各自只更新所属审核列表，不得互相替代。
4. 总览失败触发完整配置旧快照只读，但不能否定审核或配置写入事实，也不能证明外部通知送达、排班接收或升级执行。
5. 前端活动锁不改变服务端 maker-checker、基线复验、唯一待办或事务语义，本轮不新增端点或 DTO 字段。

### 14.214 配置动作提示语义前端消费约束

1. 本轮不新增或修改配置中心端点、参数、响应 DTO、HTTP 状态码与业务错误码；提示分级只作用于前端展示状态。
2. 接口抛错形成的“失败”提示、成功响应中的“未通过”或“不合法”业务校验结果均按错误语义展示，不得因 HTTP 成功而显示为成功结果。
3. 编辑、文件读取、待处理冲突和已完成动作仍使用既有响应字段与消息内容，前端只选择对应信息或成功展示级别。
4. 提示角色、颜色和文本不改变审核 POST、总览 GET、活动锁或运行配置事实，也不能作为真实渠道和生产链路验收证据。

### 14.215 配置版本历史刷新前端消费约束

1. 五类版本历史 GET 的端点、参数、响应 DTO、错误码和请求序号校验保持不变；本轮只调整前端旧快照提交与空态展示。
2. 同一配置刷新失败时保留上次成功响应并明确标识其为旧快照；切换配置时清空前一配置响应，禁止跨实体复用。
3. 首次 GET 失败不等价于服务端返回空数组，前端不得显示“暂无版本记录”；只有当前请求成功返回空数组才能确认空态。
4. 关闭面板使当前请求失效并清理读模型；旧快照不参与回滚请求、审核事务或运行态配置判断。

### 14.216 配置审核列表读取状态前端消费约束

1. 九类审核列表 GET 的端点、参数、响应 DTO 与错误码保持不变；本轮只调整前端错误、空态和旧快照展示。
2. GET 失败不能等价为成功空数组；首次失败不显示“暂无申请”，刷新失败时保留上次成功列表并明确标识旧快照。
3. 各列表只消费所属错误与数组状态，不得用其他审核域或配置总览替代；旧列表不能证明服务端待办仍完整或状态未变化。
4. 展示状态不改变审核 POST、maker-checker、唯一待办、基线复验或运行态写入事实。

### 14.217 指定告警供应商回执回查路径参数约束

`POST /api/payment-task-center/reconcile-issue-alert-receipts/{alertNo}`

1. `alertNo` 必填，原始值不得包含 ISO 控制字符，裁剪首尾空格后最长 `64` 个字符；非法值在任何告警 Mapper 查询前拒绝。
2. 请求体操作人继续按既有必填、控制字符和 `64` 字符规则校验；告警编号与操作人均合法后才查询可回查的 `ACCEPTED` 投递日志。
3. 编号合法但告警不存在或状态不允许时继续返回既有业务失败；本轮不修改响应 DTO、错误包装、回查占用、限流或供应商适配器协议。
4. 接口成功只表示本次回查任务按当前适配器结果处理，不代表通知真实送达、接收人确认或生产链路验收。

### 14.218 管理端动态路径段编码约束

1. `GET /api/payment-records/{paymentOrderId}`、`GET /api/payments/{paymentOrderId}`、`GET /api/refunds/{refundOrderId}` 与 `PUT /api/payment-config/issue-duty-rosters/{rosterCode}` 的客户端业务键必须使用 URL 路径段编码。
2. 编码只保护 URL 结构，不改变端点、HTTP 方法、请求体、响应 DTO 或错误码；服务端继续按各业务键的容量与字符规则独立校验。
3. 值班路由 PUT 仍提交完整候选并进入既有审核流程，路径编码不构成配置已写入运行态的证据。
4. 详情 GET 成功只返回当前服务端记录事实，不代表支付到账、退款到账或账务一致。

### 14.219 用户端收银台动态路径段编码约束

1. `GET /api/payments/cashier/{prepayOrderNo}` 与 `GET /api/payments/{paymentOrderId}` 的用户端客户端必须对业务键使用 URL 路径段编码。
2. app、H5 和 PC 三端复用同一 app-web API 客户端；编码不改变端点、HTTP 方法、响应 DTO、错误包装或三端页面路由。
3. 服务端继续对解码后的预付单号、支付单号执行必填、原始控制字符和最长 `64` 字符校验，并独立判断记录存在性与业务状态。
4. 接口成功只返回当前服务端收银台或支付记录事实，不代表渠道扣款、资金到账、账务一致或生产链路验收。

### 14.220 用户端支付页面路由消费约束

1. `/cashier/:prepayOrderNo` 与 `/payment-result/:paymentOrderId` 属于浏览器页面路由，不新增后端 API；三端使用同名路由和结构化路由参数。
2. 页面路由参数由 Vue Router 编码，进入页面后仍由共享 API 客户端再次按 API 路径段编码；两层边界职责独立，不传递已编码文本到服务层。
3. `bizType`、`terminalVariant`、`prepayOrderNo` 与 `paymentMethod` 作为查询对象传递，不得通过手工 `?`、`&` 拼接改变字段边界。
4. 页面导航成功不改变 API 响应、支付状态机或资金事实，也不代表预付单、支付单存在或渠道处理成功。

### 14.221 管理端详情页面路由消费约束

1. `/payments/:paymentOrderId`、`/payment-records/:paymentOrderId` 与 `/refunds/:refundOrderId` 属于管理端页面路由，不新增或修改后端 API。
2. 页面入口使用命名路由参数，支付记录维度通过独立 `query.recordType` 传递；页面参数进入详情组件后再由已编码的 API 客户端请求服务端。
3. 支付单、支付记录和退款详情之间的互跳只传递业务键，不复制或推断目标详情事实；服务端继续独立执行字符、容量、存在性与状态校验。
4. 页面路由解析成功不代表详情 API 成功、支付或退款到账、账务一致或运营动作有效。

### 14.222 管理端列表筛选路由消费约束

1. 管理端 11 个列表页面使用命名浏览器路由和结构化 `query`，不新增或修改任何后端 API、DTO、错误码或查询字段。
2. 页面间传递的支付单号、订单号、账单号、异常号、支付方式、渠道、状态和流水类型保持独立字段，由 Vue Router 负责序列化与解析。
3. 目标页面从 `route.query` 读取筛选后仍调用既有列表与概览端点；导航成功只表示查询条件可传递，不表示接口成功或列表存在命中记录。
4. 筛选跳转不改变支付、退款、异常、事件或账务事实，也不构成运营处理、资金到账或生产验收证据。

### 14.223 管理端外部收银台链接消费约束

1. 管理端打开 app-web 收银台使用 `{VITE_APP_WEB_BASE_URL}/cashier/{prepayOrderNo}`，属于浏览器跨应用导航，不新增或修改后端 API。
2. 基地址只允许 HTTP(S)，部署配置可包含基础路径但不得携带到目标链接的查询或片段；未配置时回退到本地开发地址 `http://127.0.0.1:5175`。
3. `prepayOrderNo` 必填并按单一路径段编码；进入 app-web 后仍由共享 API 客户端和服务端分别执行传输与业务键校验。
4. 链接构造成功不代表目标应用可达、收银台 API 成功、预付单有效、渠道受理或资金到账。

### 14.224 支付配置动态路径业务键约束

1. 配置版本查询、回滚、更新及复核批准/驳回端点的路径业务键由客户端按单一路径段编码，服务端按解码后的原始业务值校验。
2. 路径业务键不得包含 ISO 控制字符；首尾空格裁剪后必须非空且不得超过 `64` 个字符，非法输入在 Mapper 交互前失败。
3. 该规则覆盖渠道、路由规则、支付协议、支付网关、控制策略、值班路由、通知供应商和九类复核申请编号，不改变各端点既有请求体与响应结构。
4. 业务键合法只表示可进入后续存在性、状态、版本基线和 maker-checker 校验，不代表配置存在、审核通过或运行态生效。

### 14.225 协议标记 locale 无关规范化约束

1. `GET /api/payment-config/export` 的 `section` 继续接受既有固定分区，大小写折叠使用 `Locale.ROOT`，不新增参数值或响应字段。
2. 异常告警 `notifyChannels` 中的固定通道标记使用 `Locale.ROOT` 转为大写，`IN_APP` 继续只表示站内 outbox，不进入外部通知器选择。
3. `POST /api/payments/submit` 缺少显式终端时，User-Agent 以 `Locale.ROOT` 转为小写后执行既有移动端判断；显式终端优先级不变。
4. 本轮不改变端点、DTO、错误码或状态机；规范化成功不证明请求来源可信、通知送达或导出数据完整。

### 14.226 告警派发业务时区约束

1. 异常告警外发的补发冷却、新鲜度、防重放、供应商熔断和供应商限流窗口统一以 `Asia/Shanghai` 当前时间计算，与告警记录的业务时间口径一致。
2. 既有派发入口、请求参数、响应 DTO 和错误码保持不变；窗口命中时继续返回并记录既有 `RETRY_COOLDOWN_ACTIVE`、`FRESHNESS_WINDOW_EXPIRED` 等结果。
3. 供应商熔断和限流查询起点仍按 `yyyy-MM-dd HH:mm:ss` 传给 Mapper，只将其当前时间来源固定为业务时区，不改变 SQL 或统计窗口配置。
4. 本轮不新增端点或协议字段；时间窗判断正确不证明真实通知送达、供应商容量、数据库时钟或生产稳定性。

### 14.227 日终缺省业务日期时区约束

1. 日终执行接口未提供或仅提供空白 `bizDate` 时，服务端按 `Asia/Shanghai` 当前日期的前一日补齐 `yyyy-MM-dd` 业务日期。
2. 请求显式提供 `bizDate` 时继续裁剪并严格按 `yyyy-MM-dd` 解析，合法值优先于缺省规则，非法值继续返回既有参数错误。
3. 同一解析结果用于全部日终统计 Mapper 查询和批次 `bizDate`，接口请求、响应 DTO、运行方式与错误契约保持不变。
4. 本轮不新增端点或字段；业务日期解析正确不证明源数据完整、日终对账完成、数据库时钟或生产稳定性。

### 14.228 支付成功事件批次日期时区约束

1. 支付成功事件发往清分系统的 HTTP 载荷 `batchDate` 和 AMQP 载荷同名字段统一取 `Asia/Shanghai` 当前日期，格式保持 `yyyy-MM-dd`。
2. HTTP 与 AMQP 路径复用同一业务日期生成方法，不再使用部署节点默认日期；事件重发时仍按实际投递时点生成既有批次日期。
3. 账务 HTTP 载荷既有契约不包含 `batchDate`，本轮不新增该字段，也不改变其他载荷字段、消息头、交换机、路由键或响应判断。
4. 本轮不新增对外端点、请求 DTO 或错误码；日期生成正确不证明消息发布、下游受理、清分入账或生产资金一致。

### 14.229 退款业务编号时区约束

1. 退款申请生成的 `refundOrderId` 继续由 `REF + yyyyMMddHHmmssSSS + 8位UUID` 组成，其中时间部分固定取 `Asia/Shanghai`。
2. 退款操作日志号继续由 `ROL` 加同格式上海时间前缀和 UUID 后缀组成；编号长度、字符集、响应字段与查询参数保持不变。
3. 编号只承担业务标识和追踪职责，不作为退款状态、渠道受理、完成时间或到账时间的权威事实。
4. 本轮不新增端点、DTO 或错误码；编号生成稳定不证明退款渠道提交、回调、查单或资金到账完成。

### 14.230 API 请求追踪号时区约束

1. 统一 API 响应 `requestId` 继续采用 `REQ + yyyyMMddHHmmssSSS + 8位UUID`，时间部分固定以 `Asia/Shanghai` 格式化。
2. 成功与失败响应继续由同一 `RequestIdHolder` 生成请求号，响应 JSON 字段、状态码和错误码契约保持不变。
3. 请求号用于跨层日志关联和问题定位，不作为业务发生时间、调用方身份、幂等键或审计审批凭证。
4. 本轮不新增端点、DTO 或错误码；请求号格式稳定不证明调用链完整、业务成功、请求可信或生产稳定性。

### 14.231 三端预付金额精度约束

1. `POST /api/payments/prepay` 的 `amount` 字段名称和后端 `BigDecimal` 类型保持不变；APP、H5、PC 可用 JSON 十进制字符串提交以避免浏览器浮点舍入。
2. 三端入口仅接受大于 `0`、最多 16 位整数和 2 位小数的十进制文本，不接受指数形式；前导零和省略整数位的小数在提交前规范化。
3. Spring/Jackson 对十进制字符串执行精确 `BigDecimal` 绑定，后端继续按正数、精度和容量规则做最终校验，不信任客户端校验结果。
4. 本轮不新增端点、字段或错误码；金额序列化无损不证明渠道扣款、支付成功、账务入账或生产资金一致。

### 14.232 管理端退款申请金额精度约束

1. `POST /api/refunds/apply` 的 `refundAmount` 字段名称和后端 `BigDecimal` 类型保持不变；管理端以 JSON 十进制字符串提交，避免浏览器浮点舍入。
2. 管理端入口仅接受大于 `0`、最多 16 位整数和 2 位小数的十进制文本，不接受指数形式；前导零和省略整数位的小数在提交前规范化。
3. Spring/Jackson 对十进制字符串执行精确 `BigDecimal` 绑定，后端继续按金额容量、原支付单状态和累计退款额度做最终校验，不信任客户端校验结果。
4. 本轮不新增端点、字段或错误码；金额序列化无损不证明退款渠道受理、资金退回、到账核验或生产资金一致。

### 14.233 支付渠道单日限额精度约束

1. 渠道总览响应字段 `dailyLimitValue` 的 Java 类型和来源仍为 `BigDecimal`，JSON 表示改为十进制字符串；展示字段 `dailyLimit` 保持既有带币种格式。
2. 管理端提交渠道完整候选时，`candidate.dailyLimit` 使用 JSON 十进制字符串，接受 `0` 至 `9999999999999999.99` 且最多两位有效小数，不接受负数或指数形式。
3. Spring/Jackson 将候选字符串精确绑定为 `PaymentChannelUpsertRequestDTO.dailyLimit BigDecimal`，服务端继续在提交和批准阶段复验容量，不信任客户端校验结果。
4. 仓库内 `dailyLimitValue` 唯一消费者已同步接收字符串；其他外部消费者不得再假定该字段是 JSON 数字。精度正确不证明审核可信、配置生效或渠道真实容量。

### 14.234 支付路由试算样例金额精度约束

1. 既有路由规则试算端点和 `amount` 字段保持不变；管理端以 JSON 十进制字符串提交样例金额，Spring/Jackson 精确绑定为 `PaymentRouteRuleValidationRequestDTO.amount BigDecimal`。
2. 服务端兼容 `amount=null` 并按零值参与既有表达式求值；非空金额接受 `0` 至 `9999999999999999.99` 且最多两位有效小数，拒绝负数或超出容量的输入。
3. 客户端使用同一 `16+2` 规则提供即时反馈，但服务端仍执行权威复验，不信任客户端校验结果；本轮不新增端点、字段或错误码。
4. 试算请求不持久化路由规则或支付资金事实，返回命中只表示给定本地上下文下的表达式计算结果，不证明真实支付路由、渠道受理或生产稳定性。

### 14.235 支付渠道版本限额响应精度约束

1. `GET /api/payment-config/channels/{channelCode}/versions` 响应中的 `dailyLimit` Java 类型和字段名称保持不变，JSON 表示改为十进制字符串。
2. 消费者应将该字段作为金额文本展示或使用十进制定点能力处理，不得先转换为 JavaScript `Number`；最大合法值为 `9999999999999999.99`。
3. `POST /api/payment-config/channels/{channelCode}/rollback` 仍只接收版本号及复核声明字段，服务端根据版本号读取完整不可变快照，不要求客户端回传 `dailyLimit`。
4. 本轮不新增端点或错误码，不改变版本回滚和复核契约；历史响应精确不证明版本审批可信、回滚生效或渠道真实容量。

### 14.236 管理端当前页金额合计接口消费约束

1. 支付列表、退款列表和统一支付记录接口继续返回既有字符串展示字段，包括 `¥` 前缀、千分位和两位小数；本轮不改变字段名称或服务端响应格式。
2. 管理端不得直接使用 JavaScript `Number` 对这些展示字符串解析或求和，应按十进制分为单位处理，以避免货币符号导致 `NaN` 和二进制浮点误差。
3. 页面合计仅覆盖当前分页响应的 `items`，服务端返回的 `total` 仍是记录总数而非金额总额；需要全量金额时应使用独立的服务端汇总契约。
4. 当前页合计精确不改变支付、退款或记录查询接口的业务语义，也不作为账务、对账、结算或渠道资金证明。

### 14.237 管理端金额合计分页范围展示约束

1. 支付列表、退款列表和统一支付记录继续消费既有分页接口，不新增全量金额汇总字段。
2. 管理端金额卡片标题必须显式标识“本页”，其范围仅为当前响应 `items`，不得将记录总数 `total` 解释为金额汇总范围。
3. 本轮无请求、响应、端点或错误码变化；若未来提供全量金额，应新增独立服务端汇总契约和相应权限、审计及容量控制。

### 14.238 管理端状态指标分页范围展示约束

1. 列表接口响应 `total` 表示符合筛选条件的全量记录数，`items` 只包含当前分页；两者不得混用为同一统计范围。
2. 管理端从 `items` 派生的状态计数必须在用户可见标题中标识“本页”，而记录总数继续展示 `total`。
3. 本轮无请求、响应、端点或错误码变化；若需要全量状态分布，应新增服务端聚合契约，不得在客户端根据单页样本外推。

### 14.239 退款申请客户端幂等契约

1. `POST /api/refunds/apply` 请求新增必填字符串 `idempotencyKey`，裁剪首尾空白后长度为 `1..128`，原值不得包含 ISO 控制字符；非法键返回 `PAYMENT-1052`。
2. 幂等范围为同一 `paymentOrderId`：首次成功申请持久化该键；后续同键且规范化后的 `refundAmount / refundMethod / refundReason / operator` 全部一致时返回原退款单，不新增退款单或申请日志。
3. 同一支付单使用相同键但上述任一业务字段不同，返回 `PAYMENT-1053`，调用方必须检查请求身份，不得用新键盲目绕过冲突。
4. 申请层幂等键只标识创建请求；审核后渠道提交仍以稳定 `refundOrderId` 作为渠道幂等标识。两层键不得互相替代，响应成功也不代表渠道受理或资金到账。

### 14.240 退款申请幂等响应状态契约

1. 首次退款申请成功通常返回 `REVIEWING`；同键同内容重放返回原退款单的当前快照，响应状态可能已流转为 `PROCESSING / SUCCESS / FAIL`。
2. 调用方必须读取响应中的 `refundOrderId` 和 `status` 展示实际结果，不得仅因 HTTP/业务响应成功就固定解释为“新建退款单”或“等待审核”。
3. 本轮不新增请求或响应字段、端点或错误码；该约束澄清幂等重放的既有响应语义，不改变退款状态机和渠道处理流程。

### 14.241 支付非成功回调终态并发契约

1. `POST /api/payments/callback/{channel}` 的字段、签名串和现有 `tradeStatus` 判断保持不变；本轮不新增状态白名单或错误码。
2. 非 `SUCCESS` 回调仅在数据库中支付单尚未进入 `SUCCESS / CLOSED` 时原子维持 `WAIT_CALLBACK` 并绑定渠道流水；条件不满足时返回支付单最新详情。
3. 条件更新未命中表示终态已由并发回调、主动查单或关闭路径取得，服务不得再更新支付尝试、预付单，不得写新的待确认通知处理记录或 `PAYMENT_PENDING` 事件。
4. 该契约保证平台终态不被晚到非成功回调倒退，不代表任意渠道状态文本已经完成正式协议映射；生产仍需按真实渠道文档验证乱序和重复通知。

### 14.242 支付渠道提交事实与主动查单资格契约

1. `POST /api/payments/query`、`POST /api/payments/admin/query` 和 `POST /api/payments/callback/{channel}` 在渠道、限流、资金或状态副作用前校验支付提交事实；除兼容读取 `SUCCESS / CLOSED` 外，仅 `WAIT_CALLBACK` 且存在最近支付尝试的支付单具备资格，否则返回 `PAYMENT-1054`。
2. 成功回调和主动查单成功的数据库收口只允许普通 `WAIT_CALLBACK` 且 `t_payment_attempt` 至少存在一行的支付单；`CLOSED` 仅保留既有历史过期误关恢复条件。非成功回调维持待确认也要求相同提交事实。
3. `GET /api/payments` 的 `PaymentListItemDTO` 新增可空 `latestAttemptStatus`；`GET /api/payments/cashier/{prepayOrderNo}` 的 `CashierPageDTO` 新增可空 `paymentStatus / latestAttemptStatus`，供客户端在发起主动查单前准确判断。既有字段、端点路径和请求体不变。
4. 客户端不得以按钮可点击、收银台文案或支付单号已生成推断渠道已受理；服务端校验仍是权威边界。`SUCCESS / CLOSED` 可继续查单仅用于刷新与历史兼容，不表示允许重开终态或产生新的资金事实。

### 14.243 收银台提交事实消费约束

1. `GET /api/payments/cashier/{prepayOrderNo}` 的既有 `paymentStatus / latestAttemptStatus` 除用于主动查单资格外，也用于客户端重复提交锁定和本地关闭资格；本轮不新增或修改响应字段。
2. 客户端没有最新查单详情时使用收银台快照，查单成功后优先使用 `PaymentDetailDTO`；非空尝试事实不得因收银台展示状态仍为待支付而被忽略。
3. 本轮无端点、请求、响应、错误码或服务端状态机变化；客户端判断不能替代支付提交幂等、`PAYMENT-1054` 和数据库关单条件。

### 14.244 失败退款重试当前受理轨迹契约

1. 人工退款重试和任务中心失败退款重试的端点与请求字段不变；只有当前状态为 `FAIL` 且退款额度复验通过时允许进入 `PROCESSING`。
2. 状态迁移通过专用数据库条件更新完成，并同时将上一轮当前 `channelCode / channelRefundNo / channelSubmittedAt / successAt` 置空；随后在同一事务重新激活稳定 `REFUND_SUBMIT` 事件，事务提交后才触发渠道派发。
3. 重新派发不得因上一轮渠道退款号存在而直接把 outbox 标为成功；新受理结果仍须经过既有字段校验和首次绑定条件，结果未知时继续进入 `UNKNOWN` 并停止直接重试。
4. 响应中的渠道字段表示当前一次受理轨迹，不是完整尝试历史；操作成功不代表真实渠道接受同退款单重试、退款到账或对账完成。

### 14.245 退款提交未知渠道定位契约

1. 退款提交适配器已经实际发起渠道调用且结果未知时，服务端保留请求中的实际 `channelCode`，并将对应 `REFUND_SUBMIT` 事件置为 `UNKNOWN`；端点、请求字段、响应字段和错误码不变。
2. 该路径不生成或写入 `channelRefundNo / channelSubmittedAt / successAt`，不迁移退款终态；`channelCode` 只用于后续按平台退款单号向原渠道查单，不表示渠道已收到或受理。
3. 明确在调用上游前被配置或治理门禁拒绝时不得写未知渠道，事件沿既有失败重试路径处理；结果未知事件仍不得通过事件重发接口直接再次提交。
4. 主动查单与自动补偿继续要求退款为 `PROCESSING`、渠道非空，并具备渠道退款号或最近提交事件为 `UNKNOWN`；可信查单或验签回调是补齐受理号和退款终态的唯一现有入口。

### 14.246 退款渠道尝试身份与详情契约

1. 内部退款提交接口调整为 `publishRefundSubmit(eventNo, refundAttemptNo, operator)`；`REFUND_SUBMIT` outbox 的 `biz_no` 等于 `refundAttemptNo`，不再等于 `refundOrderId`。同一事件重发复用原尝试号，退款终态失败后的业务重试创建新尝试号和新事件号。
2. payment-core 调用 `POST /api/gateway/refunds/submit` 与 `POST /api/gateway/refunds/query` 时新增必填 `refundAttemptNo`，长度上限 `64` 且拒绝 ISO 控制字符；gateway-access 原样转发给上游，作为该轮渠道侧商户退款请求身份。
3. `GET /api/refunds/{refundOrderId}` 的响应新增 `attempts` 数组；元素包含 `refundAttemptNo / channelCode / channelRefundNo / attemptStatus / attemptStatusType / submittedAt / completedAt / createdAt`。既有退款单字段保持当前尝试投影语义。
4. `UNKNOWN` 尝试没有渠道退款号时允许按 `refundAttemptNo + refundOrderId + channelCode` 主动查单；可信结果返回渠道退款号后先恢复当前尝试受理事实，再确认未知 outbox 和退款终态。
5. 回调请求必须携带当前 `refundAttemptNo`；服务端同时查找已有渠道退款号的历史尝试归属。任一身份不属于当前最新尝试时返回 `REFUND_CALLBACK_TRANSACTION_MISMATCH`，不得更新退款终态。

### 14.247 退款主动查单尝试快照一致性契约

1. `POST /api/refunds/query` 的请求与响应字段不变；服务端读取查询资格时同时固定当前 `refundAttemptNo`，渠道返回后必须在退款单行锁内再次确认该尝试仍为最新尝试。
2. 外部调用期间退款终态失败并已创建新尝试时，旧查单结果固定返回 `REFUND_CALLBACK_TRANSACTION_MISMATCH`；即使渠道退款号尚未被任何历史尝试绑定，也不得将旧结果补录到新尝试。
3. 拒绝发生在尝试受理绑定、退款单投影更新、UNKNOWN outbox 确认、退款终态更新和操作日志写入之前；共享渠道限流已在外部调用前占用的配额不回退。
4. 渠道查询成功、失败和处理中日志均关联发起查询时的 `refundAttemptNo`；自动补偿退避仅统计当前尝试的 `CHANNEL_QUERY / CHANNEL_QUERY_FAILED`，不再按时间边界推断轮次。

### 14.248 退款回调尝试身份契约

1. `POST /api/refunds/callback/{channel}` 请求新增必填 `refundAttemptNo`，规范化后长度为 `1..64` 且原值不得包含 ISO 控制字符；非法输入返回 `REFUND_CALLBACK_REQUEST_INVALID`，并在验签和数据库访问前失败。
2. 退款回调签名原文调整为 `channel|refundOrderId|refundAttemptNo|refundStatus|channelRefundNo|timestamp|nonce`；支付回调签名原文不变。渠道适配器必须按新原文生成签名，不得在验签后另行补写尝试号。
3. 验签通过后，服务在退款单行锁内要求请求尝试号等于当前最新尝试号；不一致固定返回 `REFUND_CALLBACK_TRANSACTION_MISMATCH`。该校验先于历史渠道退款号查询、UNKNOWN 受理恢复、退款投影和终态更新。
4. 因此旧 `UNKNOWN` 尝试即使从未绑定过 `channelRefundNo`，其首次晚到回调也不能把渠道受理号写入新尝试。已有渠道退款号的历史归属查询继续作为第二道一致性校验。
5. 本契约不证明真实渠道已经携带或签署 `refundAttemptNo`；上线前必须完成渠道适配、签名向量、乱序回调及生产密钥联调，旧版回调调用方需同步升级。

### 14.249 退款旧下发事件尝试隔离契约

1. `REFUND_SUBMIT` 首次派发、自动重试和人工重发的请求字段保持不变，但服务端只允许事件 `biz_no` 对应退款单当前最新 `refundAttemptNo` 时读取渠道下发快照。
2. 最新尝试统一按 `created_at DESC, id DESC` 选择；同秒多尝试不得依赖随机尝试号排序。历史尝试事件读取不到快照时必须在调用 gateway-access 或本地渠道适配器前失败闭合。
3. 旧事件拒绝不得写 `CHANNEL_SUBMIT` 日志、不得绑定渠道退款号或改变当前尝试；事件沿既有失败次数和死信规则收口，UNKNOWN 事件仍保持人工核查语义。
4. 该边界是异常状态防御，不表示可以跳过事件与尝试数据修复；本地 H2 测试不证明生产 MySQL 多实例竞态或真实渠道零调用。

### 14.250 支付成功事件提交后派发契约

1. 支付成功回调和主动查单成功的端点、请求、响应与错误码不变；`PAYMENT_SUCCESS` 事件必须与支付业务事实在同一事务提交，提交前不得调用清分、账务或 AMQP。
2. 本地事务提交后，以原 `eventNo + paymentOrderId` 触发即时派发；派发服务使用独立事务锁定事件，并校验事件类型、支付单归属和可派发状态。事件不存在、身份不一致或状态不可派发时不得读取支付详情或产生外部调用。
3. `SUCCESS` 事件的首次派发和后续重复触发均幂等停止；`PENDING / FAILED / DEAD_LETTER` 继续复用既有 HTTP/AMQP 投递、失败计数和死信规则。即时派发异常不回滚已经提交的支付与 outbox 事实。
4. 该契约只保证本地提交顺序和单事件派发互斥，不保证 HTTP 清分、账务两个下游原子成功，也不把 AMQP publisher confirm 解释为消费、入账或业务完成；下游仍须按稳定业务身份幂等。

### 14.251 支付成功 HTTP 分阶段投递契约

1. 支付事件查询项新增 `paymentSuccessHttpStage`，数据库字段为 `payment_success_http_stage`，取值为 `PENDING / CLEARING_ACCEPTED / COMPLETED`；该字段是 HTTP 双下游投递进度，不替代 `publishStatus`。
2. `PENDING` 表示尚无已提交的清分接受阶段；`CLEARING_ACCEPTED` 表示 payment-core 已收到清分成功响应且正常提交该阶段；`COMPLETED` 表示清分与账务均返回成功，事件发布状态可能仍待补齐为 `SUCCESS`。
3. HTTP 重试从持久化阶段继续：`PENDING` 依次调用清分、账务，`CLEARING_ACCEPTED` 只调用账务，`COMPLETED` 不调用下游。阶段推进失败不得把事件标为成功。
4. AMQP 模式不读取或更新 `paymentSuccessHttpStage`，继续由 `publishStatus` 与 publisher confirm 表达本地发布结果；调用方不得把 HTTP 阶段解释为 AMQP 消费进度。
5. 增量迁移将存量行默认置为 `PENDING`，不依据历史 `eventPayload`、`publishStatus` 或下游范围反推受理事实；已为 `SUCCESS` 的存量事件仍由发布终态阻止重发。
6. HTTP 成功响应只表示接口契约上的接受。若进程在清分返回成功后、事件事务提交前退出，阶段可能仍为 `PENDING`；清分和账务必须以稳定 `eventNo / paymentOrderId` 实现幂等，接口契约不承诺 exactly-once。

### 14.252 支付成功 HTTP 阶段展示与导出契约

1. 支付事件列表和详情继续直接消费查询项 `paymentSuccessHttpStage`；CSV 在“发布状态”后新增“HTTP投递阶段”列，导出原始枚举值，不用页面中文标签替代接口事实。
2. `PAYMENT_SUCCESS` 的 `PENDING / CLEARING_ACCEPTED / COMPLETED` 在管理端分别展示“待清分”“清分已受理”“双下游已受理”，排障建议分别为重发清分与账务、只重试账务、只补齐事件发布终态。
3. 非 `PAYMENT_SUCCESS` 事件的该字段显示“不适用”。非法、空白或未知值显示“阶段异常”，要求先核对数据；不得自动降级为 `PENDING` 或给出普通重发建议。
4. 当 `eventType=PAYMENT_SUCCESS`、`publishStatus=SUCCESS` 且阶段为默认 `PENDING` 时，页面显示“未记录 HTTP 阶段”，因为迁移前历史事件和 AMQP 模式都可能形成该组合；展示层不得据此断言待清分，也不得回写或修正服务端阶段。
5. 导出的原始字段和页面解释均只描述 payment-core 的持久化记录。调用方不得将标签、CSV 或排障建议解释为真实清分完成、账务入账、AMQP 消费完成或生产资金一致性证明。
6. 旧库默认值使非 `PAYMENT_SUCCESS` 事件也可能返回 `PENDING`；客户端允许该原始默认值并仍显示“不适用”，但拒绝非成功事件冒用 `CLEARING_ACCEPTED / COMPLETED`。这不是对非成功事件的 HTTP 投递进度确认，不回写历史字段。
7. `publishStatus=PENDING` 可伴随未来的 `nextRetryAt`，表示暂未到期而非已发布；管理端允许该合法时间且要求不早于创建时间，仍拒绝有最近发布时间或逆序的待发布快照，不因此把非成功事件计入自动重试候选。

### 14.253 支付成功 HTTP 非法阶段派发契约

1. HTTP 模式只接受 `paymentSuccessHttpStage=PENDING / CLEARING_ACCEPTED / COMPLETED`；空白、缺失、包含空白后仍为空或任何未知枚举均视为数据异常，不得回退为 `PENDING`。
2. 非法阶段不得调用清分或账务，不得推进 HTTP 阶段或将事件标记为 `SUCCESS`；事件按既有重试计数进入 `FAILED / DEAD_LETTER`，供运营核对并修复权威数据。
3. 正常数据库新事件仍由非空默认值 `PENDING` 起步，本轮不改变 schema、迁移、Mapper 条件更新、阶段顺序或对外请求字段。
4. AMQP 模式不解释 `paymentSuccessHttpStage`，继续忽略该字段并按 publisher confirm 收口发布状态；HTTP 非法阶段规则不得被调用方扩展成 AMQP 发布限制。
5. 失败闭合只阻止当前代码路径根据异常阶段产生 HTTP 副作用，不证明存量数据已清洗、真实下游未曾受理、资金一致或生产稳定性。

### 14.254 支付成功 HTTP 完成阶段终态恢复契约

1. 对 `eventType=PAYMENT_SUCCESS`、`publishStatus=PENDING / FAILED / DEAD_LETTER` 且 `paymentSuccessHttpStage=COMPLETED` 的 HTTP 事件，重试只执行事件发布终态更新，不再重建或校验下游载荷。
2. 该恢复不得查询支付详情或服务者，不得要求清分 URL、账务 URL、账务账号有效，也不得调用清分、账务或阶段推进 Mapper；唯一业务写入为既有条件化 `markPublishSuccess`。
3. `markPublishSuccess` 影响行数不是 `1` 时不得返回成功，事件继续沿既有失败次数和死信规则收口；调用方必须依据重试响应和刷新后的事件状态判断结果。
4. `PENDING / CLEARING_ACCEPTED` 仍必须校验支付成功事实、金额、订单、用户、服务者及 HTTP 配置后按阶段投递；AMQP 仍需重建消息载荷并等待 publisher confirm，不使用 `COMPLETED` 快捷恢复。
5. 该契约只消除本地终态补写对无关数据和配置的依赖，不把 `COMPLETED` 扩张为真实清分完成、账务入账或资金一致性证明。

### 14.255 支付事件到期重试总览口径契约

1. `GET /api/payment-events/overview` 的 `dueRetryEventCount` 表示在当前筛选范围内满足自动事件重试候选条件的记录数，不再只表示 `FAILED` 且已到显式重试时间的事件。
2. 候选条件为：`publishStatus=FAILED`，或 `publishStatus=PENDING` 且 `eventType` 为 `PAYMENT_SUCCESS / REFUND_SUBMIT`；同时 `nextRetryAt` 必须为空或不晚于服务端当前时间。
3. `PENDING` 的其他事件类型、`SUCCESS / DEAD_LETTER / UNKNOWN` 以及设置了未来 `nextRetryAt` 的事件不计入该字段；自动任务和总览必须复用同一服务端 SQL 条件。
4. 总览继续应用支付单号、事件类型、发布状态、下游系统和事件主题筛选；自动任务候选查询不应用页面筛选且受批次上限约束，因此页面值不承诺等于某次任务实际处理数。
5. 字段只描述查询时刻的候选快照，不证明任务已取得锁、执行重试、下游接受或业务完成。

### 14.256 支付事件列表与导出稳定排序契约

1. `GET /api/payment-events` 与 `GET /api/payment-events/export` 使用相同排序规则；支持的 `sortField=createdAt / retryCount / nextRetryAt` 与既有 `sortOrder=asc / desc` 请求契约不变。
2. 每个业务排序字段后必须追加数据库自增 `id` 作为同方向次排序键，即升序使用 `field ASC, id ASC`，降序使用 `field DESC, id DESC`；同秒、同重试次数或同为空时间的事件由 `id` 确定顺序。
3. 列表的 `LIMIT/OFFSET` 和 CSV 的 `LIMIT 1001` 必须引用同一排序 SQL 片段，避免相同查询条件在页面和导出中产生不同的同值记录顺序。
4. 稳定次排序只保证同一数据集合上的确定顺序，不承诺多个 HTTP 请求间的快照隔离；翻页期间发生插入或更新时，调用方不得把偏移分页解释为数据库一致性快照。
5. 本契约不改变筛选、默认排序、分页边界、导出上限或响应字段。

### 14.257 退款列表与导出稳定排序契约

1. `GET /api/refunds` 与 `GET /api/refunds/export` 使用相同排序规则，统一按申请时间降序、数据库自增 `id` 降序返回，即 `applied_at DESC, id DESC`。
2. 当多条退款具有相同秒级申请时间时，由 `id` 确定唯一顺序；列表的 `LIMIT/OFFSET` 与 CSV 的 `LIMIT 1001` 必须引用同一排序 SQL 片段。
3. 本契约不新增排序请求参数，不改变筛选、默认页码、分页大小归一、导出上限或响应字段；退款状态与资金处理也不受影响。
4. 稳定次排序只保证同一数据集合上的确定顺序，不承诺多个 HTTP 请求间的快照隔离；翻页期间发生插入或更新时，调用方不得把偏移分页解释为数据库一致性快照。

### 14.258 支付列表与导出稳定排序契约

1. `GET /api/payments` 与 `GET /api/payments/export` 使用相同排序规则，统一按创建时间降序、数据库自增 `id` 降序返回，即 `p.created_at DESC, p.id DESC`。
2. 当多条支付单具有相同秒级创建时间时，由 `id` 确定唯一顺序；列表的 `LIMIT/OFFSET` 与 CSV 的 `LIMIT 1001` 必须引用同一排序 SQL 片段。
3. 本契约不改变最近支付尝试关联、筛选、默认页码、分页大小归一、导出上限或响应字段；支付状态与资金处理也不受影响。
4. 稳定次排序只保证同一数据集合上的确定顺序，不承诺多个 HTTP 请求间的快照隔离；翻页期间发生插入或更新时，调用方不得把偏移分页解释为数据库一致性快照。

### 14.259 账单列表与导出稳定排序契约

1. 账单列表与 CSV 继续支持 `sortField=createdAt / dueAt / billAmount / unpaidAmount` 和 `sortOrder=asc / desc`；请求字段与默认值不变。
2. 每个业务排序表达式后必须追加数据库自增 `id` 作为同方向次排序键，即升序使用 `field ASC, id ASC`，降序使用 `field DESC, id DESC`。
3. 列表的 `LIMIT/OFFSET` 和 CSV 的 `LIMIT 1001` 必须引用同一排序 SQL 片段；筛选、总览口径、分页边界、导出上限和响应字段不变。
4. 稳定次排序只保证同一数据集合上的确定顺序，不承诺多个 HTTP 请求间的快照隔离，也不表示账单金额已经入账、对账或结算。

### 14.260 收银台会话列表与导出稳定排序契约

1. 收银台会话列表与 CSV 继续支持 `sortField=createdAt / expiresAt / amount` 和 `sortOrder=asc / desc`；请求字段与默认值不变。
2. 每个业务排序字段后必须追加数据库自增 `pr.id` 作为同方向次排序键，即升序使用 `field ASC, pr.id ASC`，降序使用 `field DESC, pr.id DESC`。
3. 列表的 `LIMIT/OFFSET` 和 CSV 的 `LIMIT 1001` 必须引用同一排序 SQL 片段；会话状态计算、筛选、总览、分页边界、导出上限和响应字段不变。
4. 稳定次排序只保证同一数据集合上的确定顺序，不承诺多个 HTTP 请求间的快照隔离，也不表示支付已提交、渠道已受理或资金已收口。

### 14.261 支付请求列表与导出稳定排序契约

1. 支付请求列表与 CSV 继续支持 `sortField=createdAt / channelCode / terminal` 和 `sortOrder=asc / desc`；请求字段与默认值不变。
2. 每个业务排序字段后必须追加数据库自增 `pa.id` 作为同方向次排序键，即升序使用 `field ASC, pa.id ASC`，降序使用 `field DESC, pa.id DESC`。
3. 列表的 `LIMIT/OFFSET` 和 CSV 的 `LIMIT 1001` 必须引用同一 `paymentRequestOrder` SQL 片段；支付单关联、最近路由记录选择、筛选、分页边界、导出上限和响应字段不变。
4. 稳定次排序只保证同一数据集合上的确定顺序，不承诺多个 HTTP 请求间的快照隔离，也不表示渠道已受理、支付已完成或资金已收口。

### 14.262 支付流水列表与导出稳定排序契约

1. 支付流水列表与 CSV 继续支持 `sortField=createdAt / retryCount / flowType` 和 `sortOrder=asc / desc`；请求字段、默认值和响应字段不变。
2. 四类流水来源分别使用固定内部来源序号和来源表自增 ID 组成唯一复合键；每个业务排序字段后必须追加同方向的 `flow_source_rank, flow_source_id`，不能只使用可能跨表重复的单一 `id`。
3. 列表的 `LIMIT/OFFSET` 和 CSV 的 `LIMIT 1001` 必须引用同一 `paymentFlowOrder` SQL 片段；内部排序列不得暴露到 DTO 或 CSV，计数查询无需携带这些列。
4. 来源序号只用于同值记录的确定性裁决，不表达支付尝试、回调、路由和事件之间的业务优先级；稳定次排序只保证同一数据集合上的确定顺序，不承诺多个 HTTP 请求间的快照隔离或资金处理结果。

### 14.263 订单列表与导出稳定排序契约

1. `GET /api/orders` 与 `GET /api/orders/export` 使用相同排序规则，统一按创建时间降序、数据库自增 `id` 降序返回，即 `o.created_at DESC, o.id DESC`。
2. 当多条订单具有相同秒级创建时间时，由 `o.id` 确定唯一顺序；列表的 `LIMIT/OFFSET` 与 CSV 的 `LIMIT 1001` 必须引用同一 `orderListOrder` SQL 片段。
3. 最近账单、支付单和预付单继续分别按本表 `created_at DESC, id DESC` 选择；本契约不改变筛选、分页边界、导出上限、响应字段、名称脱敏、订单状态或履约状态。
4. 稳定次排序只保证同一数据集合上的确定顺序，不承诺多个 HTTP 请求间的快照隔离，也不表示履约完成、渠道受理或资金已收口。

### 14.264 支付处理日志列表与导出稳定排序契约

1. 支付处理日志列表与 CSV 继续支持 `sortField=createdAt / logLevel / processStage` 和 `sortOrder=asc / desc`；请求字段、默认值和响应字段不变。
2. 每个业务排序字段后必须追加数据库自增 `id` 作为同方向次排序键，即升序使用 `field ASC, id ASC`，降序使用 `field DESC, id DESC`。
3. 列表的 `LIMIT/OFFSET` 和 CSV 的 `LIMIT 1001` 继续引用同一 `paymentLogOrder` SQL 片段；筛选、计数、总览、日志写入幂等语义和导出上限不变。
4. 稳定次排序只保证同一数据集合上的确定顺序，不承诺多个 HTTP 请求间的快照隔离，也不证明生产日志完整、外部告警送达、渠道处理或资金收口。

### 14.265 服务者结算列表与导出稳定排序契约

1. 服务者结算列表与 CSV 使用相同排序规则，统一按账期结束日、创建时间和数据库自增主键降序返回，即 `period_end DESC, created_at DESC, id DESC`。
2. 当多条结算单具有相同账期结束日和秒级创建时间时，由 `id` 确定唯一顺序；列表的 `LIMIT/OFFSET` 与 CSV 的 `LIMIT 1001` 必须引用同一 `workerSettlementOrder` SQL 片段。
3. 本契约不改变查询字段、筛选语义、计数、分页边界、导出上限、响应字段、金额格式或服务者姓名脱敏规则。
4. 稳定次排序只保证同一数据集合上的确定顺序，不承诺多个 HTTP 请求间的快照隔离，也不表示真实出款、到账、结算或对账已经完成。

### 14.266 支付记录列表与最近尝试确定性读取契约

1. `GET /api/payment-records` 继续支持 `sortField=createdAt / paymentAmount / paidAt` 和 `sortOrder=asc / desc`；每个业务排序字段后必须追加同方向 `p.id`，即升序使用 `field ASC, p.id ASC`，降序使用 `field DESC, p.id DESC`。
2. 该接口保持既有 `LIMIT/OFFSET` 分页、筛选、计数和响应字段，不新增 CSV 导出接口；稳定次排序只保证同一数据集合上的确定顺序，不承诺多个 HTTP 请求间的快照隔离。
3. `GET /api/payment-records/{paymentOrderId}` 的最近支付尝试必须按 `created_at DESC, id DESC` 唯一选择；不能只以 `MAX(created_at)` 关联后依赖无排序 `LIMIT 1` 裁决同秒记录。
4. 最近尝试表示 payment-core 中已记录的最新提交尝试，不等于渠道已受理、支付已成功、退款已到账或资金已完成对账。

### 14.267 支付详情三类轨迹稳定排序契约

1. `GET /api/payments/{paymentOrderId}` 与 `GET /api/payment-records/{paymentOrderId}` 返回的路由、通知和事件轨迹继续来自 `PaymentMapper.findRouteLogs / findNotifyLogs / findEventItems`。
2. 每类轨迹必须在本事实表内按 `created_at DESC, id DESC` 返回；当秒级创建时间相同时，以该表自增 `id` 作为唯一且不可变的次排序键。
3. 本契约不改变轨迹文本格式、支付单过滤范围、详情响应字段或三个轨迹集合之间的独立结构；服务层不得再次以不完整字段重排。
4. 各表 `id` 只能表达本表记录的插入先后，不能推导三类事实之间的全局因果顺序，也不表示渠道通知、事件消费、支付或资金处理已经完成。

### 14.268 超时关单候选稳定排序契约

1. 超时关单内部任务继续通过 `PaymentMapper.findExpiredPayments(batchSize)` 读取有界候选，不新增或改变对外 HTTP API、请求字段或响应字段。
2. 候选必须按 `MIN(pr.expires_at) ASC, p.payment_order_id ASC` 返回：最早预付失效时间优先，相同失效时间按支付单号升序裁决。
3. 本契约不改变 `PREPAY_CREATED` 资格、空渠道流水要求、支付尝试排除、重试到期判断、批次上限、逐笔条件更新、失败退避、任务租约或状态机。
4. 稳定读取顺序只保证同一候选集合和批次参数下的确定结果，不承诺多实例全局串行，也不表示真实渠道已经关单、支付资金已经收口或对账一致。

### 14.269 支付监控渠道指标稳定排序契约

1. `GET /api/payment-monitor/overview` 的 `channelMetrics` 继续按支付单的 `channel_code, payment_method` 分组，响应字段和统计口径不变。
2. 渠道指标必须按 `totalCount DESC, successCount DESC, channel_code ASC, payment_method ASC` 返回；总量和成功量优先，统计值相同时以完整分组键形成确定次序。
3. 服务层与前端应消费 Mapper 已确定的数组顺序，不得在缺少完整裁决字段时自行重排；本契约不改变成功率、成功金额、待处理数或风险等级计算。
4. 稳定排序只描述同一查询快照中的展示顺序，不承诺跨请求快照一致性，也不构成真实渠道成功率、渠道稳定性、支付成功、账务入账或资金对账证明。

### 14.270 支付监控告警显式优先级契约

1. `GET /api/payment-monitor/overview` 的 `alerts` 在同时命中多类告警时，固定按 `WAIT_CALLBACK_TIMEOUT`、`REFUND_FAIL`、`CHANNEL_DISABLED` 返回，对应待回调 P1、退款失败 P1、停用渠道 P2。
2. Mapper 使用内部 `alertSortNo` 表达该顺序，字段只参与 SQL 排序，不进入 `PaymentAlertItemDTO` 或 JSON 响应；现有告警类型、标题、内容、等级、影响数、建议动作和跳转路由不变。
3. 前端应按响应数组原序展示并选择首项，不得依赖 `UNION ALL` 分支书写顺序，也不得用缺少业务规则的文本排序覆盖后端优先级。
4. 固定顺序不表示告警已核验或处置完成，也不构成渠道恢复、退款到账、支付成功、账务入账或资金对账证明。

### 14.271 支付监控已关闭指标契约

1. `GET /api/payment-monitor/overview` 的 `summary` 新增整数型 `closedPaymentCount`，表示查询时点 `t_payment_order.status='CLOSED'` 的支付单数量。
2. `closedPaymentCount` 与 `pendingPaymentCount`、`successCount` 相互独立：待处理仍只统计 `PREPAY_CREATED / WAIT_CALLBACK`，成功仍只统计 `SUCCESS`。
3. 字段缺省展示按 `0` 处理；管理端以独立“已关闭支付”卡片展示，不改变摘要其他字段、渠道指标或告警数组契约。
4. `CLOSED` 是 payment-core 内部支付状态，可能来自超时关闭或人工关闭；该计数不得解释为渠道侧关单确认、支付失败、资金退回或对账完成。

### 14.272 支付监控摘要空集合零值契约

1. `GET /api/payment-monitor/overview` 的 `summary.totalCount / successCount / pendingPaymentCount / closedPaymentCount` 均为整数型数值字段；当 `t_payment_order` 没有记录时，四个字段必须返回 `0`，不得返回 `null`。
2. 同一场景下 `successRate` 返回 `0.00%`，`successAmount` 返回 `¥0.00`；该零值契约只统一空集合表示，不改变非空集合的状态分类、比率或金额算法。
3. `alertCount` 仍由待处理支付、失败退款和停用渠道三类事实独立计算；支付表为空只消除支付待处理告警，不代表失败退款或停用渠道告警必然为零。
4. 本契约不改变 API 路径、响应结构、前端缺省防御、渠道指标或告警数组；零值也不得解释为生产数据已完整核验、渠道正常、支付或资金处理已经完成。

### 14.273 支付监控告警计数一致性契约

1. `GET /api/payment-monitor/overview` 必须满足 `summary.alertCount = alerts.length`；`alertCount` 表示当前命中的告警类型数，不表示各类型 `affectedCount` 的总和。
2. 当前三类告警分别由至少一笔 `WAIT_CALLBACK` 支付、至少一笔 `FAIL` 退款和至少一个 `DISABLED` 渠道触发，因此 `alertCount` 的取值范围为 `0..3`。
3. `pendingPaymentCount` 仍统计 `PREPAY_CREATED / WAIT_CALLBACK`，但只有 `WAIT_CALLBACK` 触发 `WAIT_CALLBACK_TIMEOUT`；仅存在 `PREPAY_CREATED` 时待处理数可以大于零，告警数仍为零。
4. 本契约不改变告警顺序、等级、影响数量、建议动作或跳转路由；计数一致也不表示告警事实已核验、异常已处置或渠道与资金状态正常。

### 14.274 支付监控最近七日趋势窗口契约

1. `GET /api/payment-monitor/overview` 的 `trends` 只聚合数据库会话当天及此前六个自然日，时间范围为 `[CURDATE()-6天, CURDATE()+1天)`；窗口外历史记录和未来时间戳不进入结果。
2. 结果继续按 `statDate DESC` 返回且最多七行；每一行只表示窗口内实际存在支付事实的日期，无交易日期不生成零值点，因此数组长度可以小于七。
3. 每日 `totalCount / successCount / successAmount` 的状态和金额口径保持不变，接口路径、趋势字段和前端消费方式不变。
4. 窗口以数据库会话时区为准；调用方不得用本地浏览器日期重新裁剪结果，生产投放前应核对数据库会话时区与业务时区一致。

### 14.275 支付监控渠道提交事实契约

1. `GET /api/payment-monitor/overview` 的 `channelMetrics` 只聚合至少存在一条 `t_payment_attempt` 的支付单；无支付尝试的 `PREPAY_CREATED / RISK_REVIEW / RISK_BLOCKED / RISK_REJECTED` 以及未提交即关闭记录不进入渠道表现。
2. 同一支付单存在多条尝试时仍只计为一笔支付单；`totalCount / successCount / successRate / successAmount` 均以满足提交事实的支付单集合为基础。
3. `channelMetrics[].pendingCount` 只统计 `WAIT_CALLBACK`，P1 风险与待收口提示使用同一条件；因此管理端以 `status=WAIT_CALLBACK` 下钻不得因未提交预付单产生空结果。
4. `summary.pendingPaymentCount` 继续统计 `PREPAY_CREATED / WAIT_CALLBACK`，与渠道指标承担不同观察范围；支付尝试只证明本地提交事实，不等于渠道已受理、支付成功或资金已收口。

### 14.276 支付监控渠道下钻契约

1. `GET /api/payments` 与 `GET /api/payments/export` 新增可选查询参数 `channelCode`；非空值按渠道编码精确匹配，空值或 `全部` 不限制渠道。
2. `channelCode` 在服务层去除首尾空白，最大 64 字符且不得包含控制字符；非法值按既有支付请求参数错误契约拒绝。
3. 支付单分页列表、对应总数和 CSV 导出必须使用同一组 `paymentOrderId / orderNo / channelCode / paymentMethod / status` 筛选条件，调用方不得把总数或导出解释为未筛选全量。
4. 管理端从结构化路由查询初始化上述筛选；支付监控 `channelMetrics[].pendingCount>0` 时，下钻必须携带该项的 `channelCode`、`paymentMethod` 与 `status=WAIT_CALLBACK`。
5. 本契约只定义 payment-core 本地查询和页面导航，不证明渠道受理、支付成功、退款到账、账务入账、资金一致或生产稳定性。

### 14.277 退款告警下钻与待收口组合筛选契约

1. `GET /api/refunds` 与 `GET /api/refunds/export` 的既有 `refundStatus` 参数新增只读查询虚拟值 `PENDING`，其含义固定为退款状态属于 `REVIEWING / PROCESSING`。
2. `PENDING` 不是退款状态机状态，不得用于退款申请、审核、渠道提交、查单、回调、事件载荷或数据库状态字段；单状态筛选 `REVIEWING / PROCESSING / SUCCESS / FAIL` 保持原精确匹配语义。
3. 分页列表、对应总数和 CSV 导出必须对 `PENDING` 使用同一组合条件；管理端从路由查询初始化筛选并在路由条件变化时重新加载第一页。
4. 支付监控退款失败告警继续跳转 `/refunds?refundStatus=FAIL`；支付日终退款待收口告警跳转 `/refunds?refundStatus=PENDING`，覆盖其统计口径中的待审核与处理中退款。
5. 组合筛选只反映 payment-core 本地退款状态，不构成渠道已受理、退款到账、资金一致、对账完成或生产稳定性证明。

### 14.278 停用渠道告警下钻契约

1. `GET /api/payment-monitor/overview` 的 `CHANNEL_DISABLED` 告警 `actionRoute` 固定为 `/payment-config?channelStatus=DISABLED`，与告警按渠道配置 `status='DISABLED'` 的统计条件一致。
2. `channelStatus` 是管理端配置中心路由查询字段，当前只接受 `ENABLED / DISABLED`；缺失、未知或非标量值回退“全部”。该字段不是支付配置后端接口参数。
3. 状态筛选只作用于页面的支付渠道配置表，不改变治理总览、渠道复核申请、路由规则、协议、返回码、网关、控制策略或其他配置集合。
4. 渠道治理快照导出保持全量口径，管理端必须明确标注“导出全部渠道”，不得暗示其等于当前状态筛选结果。
5. 告警下钻只提供本地配置定位，不执行渠道启停或路由调整，也不构成真实渠道状态、生产数据完整或稳定性证明。

### 14.279 异常中心告警下钻一致性契约

1. 支付异常列表与详情中的停用渠道异常 `recommendedRoute` 固定为 `/payment-config?channelStatus=DISABLED`，与 `CHANNEL_DISABLED` 监控告警入口及 `status='DISABLED'` 的事实条件一致。
2. 异常列表与详情必须共享 `PaymentIssueMapper.issueUnion` 的异常定义；`findAll` 与 `findByIssueNo` 两条展开 SQL 不得分别维护不同的建议路由。
3. 管理端异常列表从路由查询接收 `paymentOrderId / orderNo / issueType / severity / channelCode / paymentMethod` 六个筛选字段；首次进入和同页查询变化均应按当前路由重新加载第一页。
4. 路由查询只有非空字符串可进入筛选；数组、空值和空字符串回退默认值。路由转换只影响现有列表请求，不新增或改变后端 API 参数、响应字段和异常状态。
5. 待回调异常继续由 `t_payment_order.status='WAIT_CALLBACK'` 直接聚合，不要求预先创建独立异常记录；推荐路由只辅助定位，不证明异常已处置、渠道已恢复或资金已收口。

### 14.280 异常告警明细路由查询契约

1. 管理端 `/payment-issue-alerts` 接收 `alertNo / issueNo / paymentOrderId / alertChannel / alertStatus / ackStatus / providerDeliveryStatus / receiptQueryState` 八个结构化查询字段，与既有告警列表和 CSV 查询字段一一对应。
2. 任务中心自动次数耗尽入口使用 `receiptQueryState=EXHAUSTED`；未耗尽的待回查入口使用 `providerDeliveryStatus=ACCEPTED&ackStatus=待确认`。页面首次进入及同页查询变化都必须按当前路由重新加载第一页。
3. 只有非空字符串可以从浏览器路由进入筛选；数组、空值和空字符串回退默认值。后端继续负责字段长度、控制字符和回查状态枚举校验，前端防御不替代服务端验证。
4. 路由变化时必须清除旧选中告警和操作提示，避免新筛选集合显示上一集合的详情或结果信息；CSV 导出继续复用当前筛选。
5. 本契约不新增 API 参数或响应字段，也不改变告警派发、供应商回执、人工确认和状态计算；筛选展示不能解释为真实送达或处置证明。

### 14.281 异常告警待派发组合筛选契约

1. `GET /api/payment-issues/alerts` 与 `GET /api/payment-issues/alerts/export` 的既有 `alertStatus` 参数新增只读查询虚拟值 `PENDING_DISPATCH`，其含义固定为 `alert_status` 属于已生成、部分失败或派发失败。
2. `PENDING_DISPATCH` 不是告警发送状态，不得写入 `t_payment_issue_alert_log`，也不得用于派发结果、回执或确认状态迁移；既有单状态筛选保持精确匹配。
3. 任务中心 `PAYMENT_ISSUE_ALERT_DISPATCH_BACKLOG.actionRoute` 固定为 `/payment-issue-alerts?alertChannel=IN_APP_OUTBOX&alertStatus=PENDING_DISPATCH&ackStatus=待确认`，与来源 outbox 待派发统计条件一致。
4. 分页列表、对应总数和 CSV 必须使用相同状态展开规则；管理端把虚拟值显示为“待派发（已生成 / 部分失败 / 派发失败）”，不得展示为新的业务状态。
5. 组合查询只反映 payment-core 本地告警日志，不证明实际派发已执行、供应商已接收、人员已响应或告警已处置。

### 14.282 异常告警待回查统一口径契约

1. `pendingIssueAlertReceiptCount`、`oldestPendingIssueAlertReceiptMinutes`、四类可执行状态和明细回查状态都以同一基础集合计算：`source_alert_no IS NOT NULL`、通道不是 `IN_APP_OUTBOX`、告警状态为已派发、供应商状态为 `ACCEPTED` 且回执状态为待确认。
2. `receiptQueryState=PENDING_RECEIPT` 是只读查询虚拟值，表示上述全部基础集合；列表、计数和 CSV 使用同一筛选谓词，返回行仍按时间、次数和 claim 状态投影五种具体队列状态。
3. 站内来源、缺少来源关联、非已派发、非 `ACCEPTED` 或已确认记录不得进入总量、最老等待、执行状态或 `PENDING_RECEIPT` 结果，即使其他字段碰巧满足。
4. 任务中心无耗尽记录时使用 `PENDING_RECEIPT` 下钻，存在耗尽记录时使用 `EXHAUSTED` 优先定位人工介入集合；两种路由均不改变回查调度或状态。
5. 指标与查询只反映 payment-core 数据库事实，不构成真实供应商受理、最终送达、人员响应或生产稳定性证明。

### 14.283 异常告警回执执行来源约束契约

1. 自动回查候选、按告警号人工回查读取和到期回查候选只接受同时满足以下条件的日志：`source_alert_no IS NOT NULL`、外部通道、已派发、供应商状态为 `ACCEPTED` 且回执状态为待确认。
2. 回查 claim、完成一次查询尝试和写入最终供应商回执必须再次校验同一基础条件；次数、是否首次、claim 截止时间等既有乐观并发条件继续生效。
3. 缺少来源告警号的外发日志属于孤立数据，即使通道、派发状态、供应商状态和确认状态均看似合法，也不得进入自动或人工回查，不得更新查询次数、查询结果、确认状态或确认时间。
4. 本轮不改变任何 HTTP API、请求或响应 DTO、持久化状态值及错误码；拒绝执行表现为候选不可见、按号读取为空或条件更新影响行数为 `0`。正式 Mapper 测试必须直接覆盖 claim、完成尝试和最终回执三类写入拒绝，不能只依赖正常服务调用顺序间接证明。
5. 该约束只保证 payment-core 不处理缺少本地来源链路的回执对象，不构成真实供应商受理、最终送达、人员响应或生产稳定性证明。

### 14.284 告警派发与回执状态接续契约

1. 通知器返回并经服务规范化的 `ACCEPTED` 必须落为外发日志 `alert_status=已派发`、`ack_status=待确认`、`ack_status_type=warn`，从而进入既有人工和自动回执回查；不得再写为无需回执。
2. 派发失败及配置、限流、熔断、重放等失败闭合结果继续写 `ack_status=无需回执`、`ack_status_type=info`，不得进入待回查总量、候选或状态更新。
3. 通知器首次响应已明确为 `DELIVERED` 时无需后续回查；服务必须在同一告警逐条事务中先保存外发日志、更新来源派发状态，再确认来源 outbox。来源确认影响 `0` 行时本条计失败并回滚当前事务。
4. 来源确认必须同时匹配来源 `alert_no`、外发日志 `issue_no`、`alert_channel=IN_APP_OUTBOX`、`source_alert_no IS NULL` 和 `ack_status=待确认`；错异常、升级 outbox、已确认或不存在的来源不得被联动确认。
5. 本轮不改变 HTTP API、DTO、持久化状态集合、schema、索引或迁移；本地测试结果不构成供应商真实受理、最终送达、人员响应或生产稳定性证明。

### 14.285 告警来源确认幂等裁决契约

1. 首次派发或异步回查取得明确 `DELIVERED` 后，来源确认条件更新影响行数大于 `0` 表示本次完成确认；不得忽略更新结果并直接报告成功。
2. 条件更新影响 `0` 行时，服务必须查询相同 `alertNo + issueNo`、`alert_channel=IN_APP_OUTBOX`、`source_alert_no IS NULL` 且 `ack_status=已确认` 的来源事实；仅该查询为 true 时允许按并发幂等成功。
3. 来源不存在、异常号不一致、目标为升级或嵌套 outbox、目标仍待确认或状态查询为 false 时，当前条目必须失败闭合；逐条事务存在时，已经写入的外发 `DELIVERED` 一并回滚。
4. 已由人工或另一并发执行者确认的来源不得被重复覆盖确认人和确认时间；只读复验仅判定幂等结果，不执行第二次写入。
5. 该内部 Mapper 契约不新增 HTTP API 或响应字段，也不改变持久化状态集合；本地结果不证明真实供应商送达、人工确认身份可信或生产并发稳定性。

### 14.286 告警回查候选来源完整性契约

1. 外发日志的 `source_alert_no` 非空只表示存在引用值，不足以取得回查资格；来源必须实际存在，并同时满足相同 `issue_no`、`alert_channel=IN_APP_OUTBOX`、`source_alert_no IS NULL` 和 `ack_status=待确认`。
2. 自动到期候选、人工批量候选和按告警号候选必须使用同一来源关联条件；来源缺失、跨异常、指向升级或嵌套 outbox、来源已经确认的记录均不可见。
3. 服务在执行 claim 前必须复验上述来源资格。复验失败时不得递增 `receipt_query_count`，不得调用供应商回执查询适配器，也不得占用共享限流额度。
4. 该复验用于防御候选读取后的状态变化和内部绕过，不替代 claim 的次数、时间与状态乐观条件；合法记录继续按既有退避、耗尽和人工强制规则执行。
5. 本轮只新增内部 Mapper 查询和 SQL 资格约束，不新增 HTTP API、DTO、状态值、schema、索引或迁移；本地结果不构成真实供应商受理、最终送达、人员响应或生产稳定性证明。

### 14.287 告警回执原子 claim 来源契约

1. 内部 claim 必须接收候选读取到的 `alertNo`、`sourceAlertNo` 和 `issueNo`，不得仅凭外发告警号与尝试次数占用记录。
2. 单条条件更新必须同时匹配外发行的来源号、异常号、外部通道、已派发、供应商 `ACCEPTED`、待确认及原有次数和时间条件，并验证来源为同异常顶层待确认 `IN_APP_OUTBOX`。
3. 上述条件共同构成 claim 的线性化点：来源确认先于 claim 可见时返回 `0`；claim 先取得资格时允许当前回查继续，后续来源确认按已确认幂等规则裁决。
4. claim 返回 `0` 时服务不得选择或调用供应商回执适配器，不得获取共享限流额度，也不得写入完成尝试或最终回执；任务结果继续按本条失败统计。
5. 同表来源验证采用不可合并的物化派生集合以规避 MySQL 目标表更新限制；该内部契约不改变 HTTP API、DTO、状态值、schema、索引或迁移，生产仍需验证实际 MySQL 版本与执行计划。

### 14.288 告警最终回执来源更新契约

1. 内部最终回执更新必须接收本次已 claim 候选的 `alertNo`、`sourceAlertNo` 和 `issueNo`，并同时匹配外发行保存的来源号与异常号；不得仅凭告警号、查询次数和 claim 时间写入供应商终态。
2. 更新语句必须验证来源告警真实存在、属于同一异常、通道为 `IN_APP_OUTBOX` 且自身无父来源号；缺失来源、错异常、错误来源号或嵌套来源影响行数必须为 `0`。
3. 来源的确认状态不是最终回执更新资格：合法 claim 后来源可能被人工或其他送达路径先行确认，此时仍应保存真实供应商 `DELIVERED` 证据，再由既有来源已确认复验完成幂等收口，不得覆盖原确认人和确认时间。
4. 查询次数、claim 时间、外部通道、已派发、供应商 `ACCEPTED`、外发行待确认等原有乐观并发条件继续生效；来源围栏只补强对象身份，不放宽状态机或重复更新规则。
5. 同表来源验证使用带 `DISTINCT` 的不可合并派生集合以兼容当前 H2/MySQL 共享 SQL 并规避 MySQL 1093 风险；本地通过不替代目标 MySQL 版本的物化计划、锁竞争和真实供应商回执验证。

### 14.289 告警回执尝试完成来源契约

1. 内部完成尝试更新必须接收本次已 claim 候选的 `alertNo`、`sourceAlertNo` 和 `issueNo`，同时匹配外发行保存的来源号与异常号；不得只凭告警号、尝试次数和 `QUERYING` 状态写查询结果。
2. 更新语句必须验证来源告警真实存在、属于同一异常、通道为 `IN_APP_OUTBOX` 且自身无父来源号；缺失来源、错异常、错误来源号或嵌套来源影响行数必须为 `0`。
3. 来源确认状态不参与该资格判断。合法 claim 后来源可能先被人工或其他送达路径确认，本次已经发生的 `ACCEPTED / FAILED / QUERY_ERROR / RATE_LIMITED` 等查询事实仍应保存，用于审计、退避和耗尽判断。
4. 外发行自身仍必须满足外部通道、已派发、供应商 `ACCEPTED`、待确认、尝试次数匹配和 `receipt_query_result='QUERYING'`；来源围栏不允许重复完成，也不改变现有退避或人工强制规则。
5. 同表来源验证采用带 `DISTINCT` 的不可合并派生集合；本地 H2 只证明共享 SQL 的功能路径，不替代目标 MySQL 版本的物化计划、1093 兼容性、锁竞争或真实供应商查询验证。

### 14.290 告警派发来源执行权契约

1. 自动或人工派发从候选列表取得对象后，必须在该条独立事务内、任何供应商通知调用前，以 `alertNo + issueNo` 执行来源行锁查询。
2. 可锁定对象必须同时满足 `alert_channel='IN_APP_OUTBOX'`、`source_alert_no IS NULL`、待确认，且状态为已生成、部分失败或派发失败；候选查询必须使用同一顶层来源资格，嵌套 outbox 不得进入工作集。
3. 锁查询无结果表示候选在读取后已经完成、确认或不再合法，本条必须按并发跳过：不得选择或调用通知器，不得新增外发日志，不得更新来源状态，也不得计为处理失败。
4. 取得锁后，来源状态更新必须再次匹配同一身份、顶层、待确认和原可派发状态，影响行数为 `0` 时抛出内部失败并阻止后续来源确认；成功、部分失败和全部失败的既有状态映射保持不变。
5. 该内部契约不新增 HTTP API、DTO、持久化状态、schema、索引或迁移。行锁在当前实现中覆盖外部调用，只约束正常数据库并发，不能撤销已经发出的网络请求，也不构成供应商 exactly-once 证明。

### 14.291 告警派发历史归属契约

1. 通道成功去重、失败派发次数和最近派发日志三类内部查询不得只依赖 `sourceAlertNo + alertChannel`，每条候选历史还必须验证其 `issueNo` 与所引用来源一致。
2. 被引用来源必须真实存在、通道为 `IN_APP_OUTBOX` 且 `source_alert_no IS NULL`；来源缺失、另一异常来源或嵌套 outbox 派生的日志不得影响当前来源的派发决策。
3. 来源的确认状态不参与历史归属判断。确认只改变后续业务动作资格，不应删除或隐藏此前合法发生的成功、失败和供应商响应事实。
4. 成功查询只接受合法归属的已派发记录，并叠加 14.292 的供应商成功证据条件；失败计数只接受合法归属且状态为派发失败的记录；最近日志在合法归属集合内按创建时间和主键倒序取一条。
5. 该契约不修改 HTTP API、Java Mapper 方法签名、DTO、schema、索引或迁移；生产需验证相关 `EXISTS` 的索引使用和存量畸形数据影响。

### 14.292 告警派发成功证据契约

1. 通道级成功去重必须同时满足：外发日志归属同异常顶层 `IN_APP_OUTBOX` 来源、`alert_status=已派发`，且 `provider_delivery_status` 为 `ACCEPTED` 或 `DELIVERED`。
2. `provider_delivery_status` 为空、`FAILED`、护栏错误码或未知值时，即使本地状态为已派发也不得构成成功去重。
3. 服务防重放窗口对最近日志使用相同的供应商成功集合；不得再由本地已派发状态单独启用重放保护。
4. 本契约不改变失败计数、最近日志读取和供应商容量统计的其他语义，不新增 HTTP API、DTO、状态、schema、索引或迁移。
5. 存量无成功证据的已派发日志可能因此重新补发；上线前必须审计此类记录并验证供应商基于稳定告警号的幂等行为，本地测试不构成真实送达或无重复证明。

### 14.293 告警真实派发结果与本地护栏隔离契约

1. 通道失败重试次数只接受合法来源归属、`alert_status=派发失败` 且 `provider_delivery_status` 为 `FAILED` 或 `SEND_EXCEPTION` 的日志。
2. 供应商熔断时间窗口同样只统计 `FAILED / SEND_EXCEPTION`；`PROVIDER_CIRCUIT_OPEN` 自身不得继续增加熔断失败数或延长熔断。
3. 用于冷却和防重放的最近派发日志只从 `ACCEPTED / DELIVERED / FAILED / SEND_EXCEPTION` 中选择；本地护栏拒绝不得刷新最近真实调用时间。
4. `RATE_LIMITED / RETRY_COOLDOWN_ACTIVE / RETRY_LIMIT_REACHED / REPLAY_WINDOW_ACTIVE / FRESHNESS_WINDOW_EXPIRED / PROVIDER_CIRCUIT_OPEN` 等本地结果继续作为审计日志保留，但不得冒充供应商调用失败。
5. 该内部契约不改 HTTP API、DTO、状态字段、schema、索引或迁移；供应商限流仍需后续以原子配额预留解决并发超额窗口。

### 14.294 告警供应商派发共享配额契约

1. 启用供应商限流策略时，任何通知器调用前必须先按规范化后的 `providerCode + channelCode` 原子占用一个固定窗口额度；不得再以告警成功历史的查询结果作为调用许可。
2. 窗口分钟数与最大条数继续来自供应商 `rateLimitPolicy`。分钟数转换为秒数必须检查整数溢出；无效或超出支持范围的配置闭合为 `RATE_LIMITED`，不得调用供应商。
3. 共享桶初始化采用唯一键幂等插入，占额采用单条条件更新：窗口已过期时将计数重置为 `1`，窗口有效时仅在 `used_count < maxDeliveries` 时加一；更新影响 `0` 行表示额度耗尽。
4. 占额使用独立 `REQUIRES_NEW` 事务并在外部调用前提交。供应商返回失败或通知器抛异常时不得返还额度，因为网络请求已经消耗供应商容量；额度耗尽仍记录 `RATE_LIMITED`，并按 14.293 排除出真实调用失败统计。
5. 本契约新增内部服务、Mapper、共享桶 schema 和迁移，不改变 HTTP API、前端 DTO 或告警状态字段。占额后调用前崩溃可能暂时损失一个额度，窗口到期后恢复；生产仍需验证 MySQL 锁等待、多实例容量、供应商幂等与真实限额。

### 14.295 告警供应商连续失败熔断契约

1. 供应商熔断必须基于目标 `providerCode + alertChannel` 在 10 分钟窗口内最近 3 条合法真实调用结果，不得用窗口失败总数替代连续失败序列。
2. 真实成功要求 `alert_status=已派发` 且供应商状态为 `ACCEPTED / DELIVERED`；真实失败要求 `alert_status=派发失败` 且供应商状态为 `FAILED / SEND_EXCEPTION`。本地护栏和状态矛盾组合不得进入序列。
3. 每条结果必须通过来源归属校验：`source_alert_no` 指向同一 `issue_no` 的顶层 `IN_APP_OUTBOX`。孤立、错异常和嵌套来源日志不得影响供应商熔断。
4. 最近三条不足 3 条，或其中存在 `ACCEPTED / DELIVERED` 时不得熔断；仅三条全部失败时记录 `PROVIDER_CIRCUIT_OPEN` 并继续尝试后备供应商。
5. 本契约只改变内部 Mapper 与服务判定并增加查询索引，不改变 HTTP API、DTO 或持久化状态。目标 MySQL 的排序执行计划、多实例并发失败、供应商真实恢复与送达仍需独立验证。

### 14.296 告警供应商半开探针协调契约

1. 10 分钟窗口内最近三条合法真实结果全部失败时继续保持熔断；窗口内不足三条但该供应商与通道的全量最近三条仍全部失败时，不得直接恢复为所有实例均可调用，而应进入半开探针阶段。
2. 半开探针必须按 `providerCode + alertChannel` 共享唯一数据库租约。租约任务编码采用该范围的 SHA-256 摘要并限制在 `t_payment_task_lease.task_code` 的 64 字符边界内，不得在任务编码或日志中拼接敏感配置。
3. 该版只有成功获取 30 秒探针租约的实例可以继续调用通知器；未获取租约时记录 `PROVIDER_CIRCUIT_HALF_OPEN_BUSY`。失败后持续冷却语义由 14.297 取代本条短租约约定。
4. 探针逻辑租约覆盖通知器调用和当前单条派发事务；该版事务完成后立即释放，现行实现按 14.297 停止续期并保留至自然过期。
5. `PROVIDER_CIRCUIT_HALF_OPEN_BUSY` 是本地协调结果，不得计入真实失败、重试冷却或熔断序列。探针真实失败产生 `FAILED / SEND_EXCEPTION` 并重新打开窗口；真实成功产生 `ACCEPTED / DELIVERED` 并关闭失败序列。
6. 本契约不改变 HTTP API、DTO、schema 或迁移。目标 MySQL 多实例竞争、租约超时、实例崩溃、真实供应商幂等、恢复和送达仍须在受控环境独立验证。

### 14.297 告警供应商失败探针冷却契约

1. 半开探针租约期限必须覆盖 10 分钟熔断恢复窗口，并额外保留 30 秒完成余量，当前固定为 `630` 秒；不得在失败探针事务完成后立即释放。
2. 探针事务完成后调用 `retainUntilExpiry()` 停止续期但保留数据库租约 owner 与到期时间。该操作必须幂等，随后误调用 `close()` 也不得清空已保留的冷却租约。
3. 普通自动任务不采用保留语义，继续通过 `close()` 在执行完成后释放；新增能力不得改变任务中心、回执回查、nonce 清理或其他调度租约的并发协议。
4. 探针真实失败后，全量连续失败事实与未过期租约共同阻止下一实例立即探测；探针真实成功后，最近结果包含 `ACCEPTED / DELIVERED`，正常派发不再进入半开租约判断，因此无需提前删除冷却租约。
5. 租约获取和续期仍为独立短事务，网络调用期间不得持有租约表行锁。若实例在完成前崩溃，既有租约按数据库到期时间恢复；若调用异常变长，后台续期继续保护执行权。
6. 本契约不改变 HTTP API、DTO、schema 或迁移。630 秒余量、数据库时钟、实例暂停、续期竞争与真实供应商恢复必须在目标环境校准。

### 14.298 支付日终批次状态一致性契约

1. 日终批次状态必须由四类未收口事实共同决定：支付成功差异、渠道异常、内部事件异常和待收口退款。任一计数大于零时必须持久化 `batchStatus=WARNING`、`batchStatusType=warn`。
2. 仅当四类计数全部为零时，批次才可持久化为 `batchStatus=COMPLETED`、`batchStatusType=success`；该口径必须与总览的正式对账准入判断保持一致。
3. `findOverviewSummary()` 仍以持久化 `batch_status` 汇总完成与异常批次数，因此状态写入与历史纠偏是汇总准确性的前置条件，不在查询时临时覆盖事实。
4. 历史上满足 `payment_success_gap_count > 0 AND batch_status = 'COMPLETED'` 的记录通过幂等迁移纠正状态和样式；迁移不得改变金额、数量、业务日期、备注或原始交易事实。
5. 本契约不改变 `POST /api/v1/payment-day-end/run` 和总览响应字段，也不新增前端适配。目标库迁移必须先预查命中集合，并在执行后复核完成/异常批次数与最近批次状态。

### 14.299 支付日终系统备注一致性契约

1. `summaryComment` 是日终事实的系统派生说明，必须与 `batchStatus` 和 `reconciliationReadinessStatus` 使用同一组基础计数，不得在成功差异未收口时输出可进入次日对账的全清结论。
2. 支付成功差异、渠道异常、内部事件异常和待收口退款全部为零时，允许生成固定全清文案；任一计数大于零时，备注必须列出四类计数、相关金额和继续跟进要求。
3. 历史纠偏只允许修改 `payment_success_gap_count > 0` 且仍精确等于旧版固定全清文案的 `summary_comment`；其他系统说明和人工备注均保持原样。
4. 备注迁移不改变批次状态、状态样式、金额、数量、日期或交易事实；状态纠偏继续由 14.298 对应的独立迁移承担，两份脚本均可重复执行。
5. 本契约不改变 HTTP 路径、请求 DTO 或响应字段。目标库执行后需同时核对批次列表、总览和 CSV 导出的文字结论，不得以本地 mock 代替真实迁移证据。

### 14.300 支付日终成功差异告警契约

1. `GET /api/v1/payment-day-end/overview` 的 `alerts` 必须包含 `alertType=PAYMENT_SUCCESS_GAP`，其 `affectedCount` 等于 `latestPaymentSuccessGapCount`，用于显式承接成功差异对账阻断事实。
2. 成功差异计数大于零时，告警等级固定为 `P1/danger`；计数为零时沿用通用无异常展示 `P3/success`，告警卡仍保留以维持页面结构和扫描顺序稳定。
3. `actionRoute` 固定为 `/payment-issues`。成功差异由回调未收口、内部成功事件未收口或订单累计成功收款越过应收任一条件构成，不得固定附加某一个 `issueType` 而漏掉其他来源。
4. 管理端未收口摘要必须同时展示成功差异、渠道异常、内部事件异常和待收口退款；成功差异告警排在细分告警之前，突出其对正式对账的直接阻断语义。
5. 本契约复用既有告警 DTO 与异常中心路由，不新增 HTTP 路径、字段、表或状态机。真实异常明细覆盖、责任分派和财务处置结果仍须在目标环境核验。

### 14.301 支付日终默认业务日前端契约

1. 管理端手工日终表单的默认 `bizDate` 必须表示 `Asia/Shanghai` 当前日历日的前一日，并使用 `yyyy-MM-dd`；不得使用浏览器本地时区的 `getFullYear/getMonth/getDate` 组合推导。
2. 上海日历日期通过显式 `Intl.DateTimeFormat` 时区提取，减日过程不得再次受运行环境本地时区影响；跨月、跨年时必须保持标准日历语义。
3. 默认值只用于初始化表单，操作员仍可显式选择补跑账期；提交后的 `bizDate` 继续由后端按严格日期格式解析，空值则由后端独立按上海时区兜底。
4. 该改动不改变 `POST /api/v1/payment-day-end/run` 的请求字段或响应。前后端以相同业务时区定义默认账期，但不假设客户端时钟绝对可信。
5. 生产运行应保证终端和服务端时钟同步，并在人工触发前展示和复核业务日期；本地跨时区测试不能替代真实关账确认。

### 14.302 支付日终已关闭账期契约

1. `POST /api/v1/payment-day-end/run` 的非空 `bizDate` 必须是严格有效的 `yyyy-MM-dd` 日历日期，并且早于 `Asia/Shanghai` 当前日期；上海当天及未来日期统一拒绝。
2. 空 `bizDate` 继续由服务端解析为上海当前日的前一日。无论显式或缺省，最终业务日必须是已经结束的上海日历日。
3. 日期格式、日历有效性和开放账期检查必须在任何 Mapper 查询与批次写入前完成；失败请求不得产生事实读取、批次编号持久化、告警或总览变化。
4. 管理端以 `type=date` 展示业务日，`max` 为上海最近已关闭业务日，并在提交前校验；这些客户端限制用于及时反馈，不能替代服务端权威判断。
5. 本契约不新增请求字段或状态码结构。错误通过既有参数异常通道返回；生产仍需保证服务端与终端时钟同步，并由财务确认目标账期是否具备完整数据。

### 14.303 支付日终重复执行与并发裁决契约

1. `POST /api/v1/payment-day-end/run` 以业务日作为并发范围。同一 `bizDate` 同时只允许一个请求执行；不同业务日互不阻塞。执行权使用数据库任务租约 `PAYMENT_DAY_END:{bizDate}` 裁决，不得退化为单实例内存锁或无锁先查后插。
2. 目标业务日不存在任何历史批次时，只接受 `runMode=MANUAL`；提交 `RERUN` 返回“该业务日尚无日终批次，首次执行请使用MANUAL”。运行方式空值继续按 `MANUAL` 处理。
3. 目标业务日已有任意 `AUTO / MANUAL / RERUN` 批次时，只接受 `runMode=RERUN`；提交 `MANUAL` 返回“该业务日已有日终批次，请使用RERUN重检”。每次合法 `RERUN` 新增批次，不覆盖旧快照。
4. 未获取业务日租约时返回“该业务日正在执行日终处理，请稍后重试”，且不得读取支付、渠道、事件、退款事实或写入批次。上述错误继续通过既有参数异常响应通道返回，不新增响应字段。
5. 历史批次判断、事实采集和批次写入必须处于同一事务，事务提交后才能释放租约；事务回滚或异常退出时不得留下半成品批次，租约最终按关闭或到期语义恢复。
6. 该契约提供同业务日服务入口串行化和运行方式防误用，不提供每次 `RERUN` 的请求级幂等。生产仍需验证目标 MySQL 多实例竞争、锁等待、租约续期与崩溃恢复，并通过权限控制禁止绕过服务直接写批次表。

### 14.304 自动日终内部执行契约

1. 自动日终不是新增 HTTP API。`PaymentDayEndScheduler` 只能调用内部 `PaymentDayEndService.runAutomatically()`；`POST /api/v1/payment-day-end/run` 继续只接受 `MANUAL / RERUN`，人工请求中的 `AUTO` 必须拒绝。
2. 调度默认值为 `payment.day-end.auto.enabled=false` 和 `payment.day-end.auto.cron=0 30 0 * * *`；cron 可由环境配置，但 `@Scheduled` 时区固定为 `Asia/Shanghai`，自动业务日固定取上海当前日的前一日。
3. 自动批次写入 `runMode=AUTO`、`triggeredBy=payment-day-end-scheduler`，不读取浏览器触发人或人工表单。目标业务日已有任意历史批次时返回未执行，不新增或覆盖批次。
4. 自动入口复用 `PAYMENT_DAY_END:{bizDate}` 租约。租约忙时返回未执行且不读取日终事实；获得租约后，历史判断、事实采集和插入仍位于同一事务。
5. 批次写入失败必须回滚并向调度框架抛出异常，租约随后释放或按既有到期语义恢复；不得把技术失败包装成“已有批次”或“租约忙”的幂等跳过。
6. 默认关闭不表示能力缺失，也不表示生产已经启用。启用前必须验证主机时钟、MySQL 多实例租约竞争、数据水位、关账窗口和告警观测；本地测试结果不能替代生产调度或财务确认。

### 14.305 自动日终内部结果与任务日志契约

1. `PaymentDayEndService.runAutomatically()` 的内部返回值包含 `executed / skipReason / bizDate / batchStatus`，不暴露为 HTTP 响应。`executed=true` 时 `batchStatus` 只能是 `COMPLETED / WARNING` 且不得有跳过原因。
2. `executed=false` 时 `batchStatus` 必须为空，`skipReason` 只能是 `EXISTING_BATCH / LEASE_BUSY`；空结果、空业务日、未知原因或矛盾字段组合均按技术失败处理，不得回退成正常幂等跳过。
3. 调度器通过 `PaymentTaskCenterService.runAutoPaymentDayEnd()` 执行，任务日志编码固定为 `PAYMENT_DAY_END`、方式为 `AUTO`、触发人为 `payment-day-end-scheduler`，推荐路由为 `/payment-day-end`。
4. 新批次 `COMPLETED` 记 `SUCCESS/P3`；新批次 `WARNING` 或业务日租约忙记 `WARNING/P2`；已有批次幂等跳过记 `SUCCESS/P3`；技术异常或非法结果记 `WARNING/P1`。
5. 技术失败摘要固定为 `PAYMENT_DAY_END_AUTO_RUN_FAILED`，不得记录数据库、凭证或异常原文；记录失败不能覆盖原始业务异常，原异常必须继续向 Spring 调度框架传播。
6. 日终事务和租约生命周期先于任务日志结束。任务日志不是批次存在性的唯一依据；若两者不一致，应先查询 `t_payment_day_end_batch`，再由值班人员决定是否 `RERUN`，不得自动重复生成首次快照。

### 14.306 任务日志业务日查询契约

1. `GET /api/payment-task-center/task-runs` 与 `GET /api/payment-task-center/task-runs/export` 新增可选 `bizDate`，按 `yyyy-MM-dd` 精确匹配 `t_payment_task_run_log.biz_date`；空值表示不按业务日过滤。
2. `PaymentTaskRunLogItemDTO` 和 CSV 新增 `bizDate`。日期型任务返回关联业务日，当前自动日终的成功、跳过和失败均填写；其他任务和未回填历史日志返回空值。
3. 服务端先去除首尾空格，再执行长度与真实日历日期校验；`2026-02-30`、控制字符或超过 `10` 个字符的输入必须在 Mapper 访问前拒绝。
4. 管理端任务日志日期筛选同时传给列表和导出；业务日为空时只展示 `-`，不得用开始时间推断账期。
5. `bizDate` 只建立任务日志与业务事实的关联键，不改变任务状态、严重等级、计数、事务顺序或批次权威性，也不代表批次与日志已经自动勾稽。

### 14.307 自动日终一致性巡检内部契约

1. 本能力不新增 HTTP API。`PaymentDayEndScheduler` 在 `payment.day-end.patrol.enabled=true` 时调用 `PaymentTaskCenterService.runAutoPaymentDayEndPatrol()`；默认 cron 为 `0 30 2-6 * * *`，时区固定 `Asia/Shanghai`，目标为前一业务日。
2. `PaymentDayEndService.inspectPreviousBusinessDay()` 返回 `inspected / skipReason / bizDate / batchCount / autoBatchCount`。完成检查时计数必须非负、自动批次不得多于全部批次且跳过原因必须为空；未完成时只允许 `LEASE_BUSY` 且两个计数为空，其他组合失败闭合。
3. 巡检使用 `PAYMENT_DAY_END:{bizDate}` 租约。租约忙返回处理数 `0` 且不写最终巡检日志；已检查时再统计同业务日 `PAYMENT_DAY_END` 且 `processed_count > 0 AND fail_count = 0` 的日志，表示实际创建批次的自动运行记录。
4. `batchCount=0` 记 `WARNING/P1`；`autoBatchCount` 与成功创建日志数不一致记 `WARNING/P2`；人工批次覆盖，或自动批次与成功创建日志数相等记 `SUCCESS/P3`。推荐路由统一为 `/payment-day-end`。
5. 最终日志编码为 `PAYMENT_DAY_END_PATROL`，日志号按业务日固定为 `PTL-DAY-END-PATROL-{yyyyMMdd}`；唯一键冲突表示其他实例已记录同日最终结论，不重复插入。技术失败另写随机日志号和稳定摘要 `PAYMENT_DAY_END_PATROL_FAILED`，不得复制异常原文，且原异常必须继续抛出。
6. 巡检只比较批次与任务日志数量，不自动生成或修改日终批次，不证明账期数据完整、资金一致、渠道账单一致、正式关账或发布放行。

### 14.308 自动日终批次关联契约

1. `PaymentDayEndService.runAutomatically()` 的已执行结果新增必填 `batchNo`，值为本轮实际持久化的日终批次号；批次号不得为空、空白或超过 `64` 字符。未执行结果的 `batchNo` 必须为空，矛盾组合按技术失败处理。
2. 实际创建新批次的 `PAYMENT_DAY_END / AUTO` 任务日志写入 `businessRefNo=batchNo`。已有批次跳过、租约忙和技术失败没有本轮新批次，必须保持空值；其他任务不因该字段被强制关联。
3. `PaymentTaskRunLogItemDTO` 与任务日志 CSV 新增可空 `businessRefNo`；既有列表和导出 URL 不增加查询参数。管理端空值显示 `-`，不得从摘要推断或补造关联号。
4. 巡检分别统计：缺少成功日志关联的 `AUTO` 批次、缺少 `AUTO` 批次关联的成功自动日终日志，以及重复关联同一 `AUTO` 批次的额外成功日志。任一计数大于零记 `WARNING/P2`，三者均为零才允许声明本地记录一一关联。
5. 成功自动日终日志仍要求 `processed_count > 0 AND fail_count = 0`；关联还必须满足同一业务日和相等批次号，不能只依赖全局唯一批次号或数量相等。
6. 本契约不新增外部 HTTP 入口，也不证明渠道账单、账务或资金一致。历史空关联不自动回填，目标库迁移、线上数据分布和多实例时序需另行验证。

### 14.309 自动日终关联运行方式隔离契约

1. 自动批次与任务日志勾稽的候选日志必须同时满足 `task_code=PAYMENT_DAY_END` 和 `run_mode=AUTO`；任务编码相同但运行方式为 `MANUAL / RERUN` 的日志不得构成自动运行证据。
2. 批次缺日志查询的关联子查询、日志缺批次查询的外层集合，以及重复关联查询的当前和较早日志均执行相同运行方式限制，不允许三个统计口径分叉。
3. 自动批次仍要求 `batch.run_mode=AUTO`，日志仍要求同业务日、相等 `business_ref_no / batch_no`、`processed_count > 0` 且 `fail_count = 0`；本条只收紧日志来源，不改变其他条件。
4. 人工日志即使携带与自动批次相同的业务关联号，也必须表现为自动批次缺少合法成功日志；该异常按既有巡检规则形成 `WARNING/P2`。
5. 本契约不变更 DTO、HTTP、schema、索引或前端。历史关联真实性和写入权限仍需生产审计，本地 SQL 回归不证明真实自动运行或资金一致。

### 14.310 自动日终关联证据完整性契约

1. 可关联自动批次的任务日志必须由 `triggered_by=payment-day-end-scheduler` 触发、`processed_count=1` 且 `fail_count=0`；宽松的正处理数或零失败不足以单独证明批次创建成功。
2. 正常完成日志必须同时满足 `task_status=SUCCESS`、`success_count=1`、`warning_count=0`；告警完成日志必须同时满足 `task_status=WARNING`、`success_count=0`、`warning_count=1`。
3. 以上条件与 `task_code=PAYMENT_DAY_END`、`run_mode=AUTO`、同业务日和相等批次号共同组成完整证据谓词；三类关联统计及重复检测的较早日志不得采用不同子集。
4. 状态与计数矛盾、处理数不是一、失败数非零或触发人不匹配的日志不构成关联，并按既有规则表现为自动批次缺少合法成功日志；巡检形成 `WARNING/P2`。
5. 本契约不改变 HTTP、DTO、schema、索引或前端。系统身份字符串不是认证凭证，生产仍需数据库权限与审计控制；该证据只覆盖本地批次创建留痕。

### 14.311 支付日终待收口支付事实契约

1. 日终批次新增 `pendingPaymentCount / pendingPaymentAmount`，只统计目标业务日创建且当前状态为 `PREPAY_CREATED / WAIT_CALLBACK / RISK_REVIEW` 的支付单；这是明确非终态集合，不使用“非成功”兜底推断。
2. `RISK_BLOCKED / RISK_REJECTED` 已取得明确风控终止结论，不计入待收口支付；`SUCCESS / CLOSED / FAIL` 同样不计入。该口径不改变支付状态机，也不把风控终止自动改写为 `CLOSED`。
3. 任一待收口支付会使新批次持久化为 `WARNING/warn`，并使总览与批次对账准入为禁止。总览新增 `PENDING_PAYMENT` 告警，动作路由为 `/payments?status=DAY_END_PENDING`。
4. `GET /api/payments` 与导出接口接受虚拟筛选值 `DAY_END_PENDING`，服务端将其精确展开为上述三个状态；该值只用于列表查询，不是支付单可持久化状态。
5. 数据库迁移对历史批次保留 `pending_payment_count / pending_payment_amount=NULL`，表示当时未采集。总览和批次不得把空值展示为零或“可进入对账”，必须提示使用 `RERUN` 生成完整新快照。
6. 这些字段只证明 payment-core 在跑批事务读取时的本地支付状态，不证明渠道回调已全部到齐、跨系统数据水位完整、资金到账、渠道账单一致或正式关账完成。

### 14.312 支付成功时间与日终完整性契约

1. 支付单进入 `SUCCESS` 时由渠道回调和主动查单共用的原子状态迁移写入 `success_at=COALESCE(success_at, NOW())`；该字段表示 payment-core 首次可信落成功状态的本地时间，后续重复结果不得覆盖。
2. 支付流水响应中的 `paidAt` 及 `sortField=paidAt` 均使用 `success_at`。非成功或历史缺失记录返回空值，不允许用 `createdAt` 回退伪装。
3. 日终支付成功单量/金额、渠道成功、内部事件成功、支付成功差异、渠道异常和内部事件异常均按 `DATE(success_at)=bizDate` 归属；支付总单量与待收口支付继续按创建日统计。
4. 日终批次与总览新增 `missingPaymentSuccessTimeCount / openMissingPaymentSuccessTimeCount`。统计集合为目标创建日内 `status=SUCCESS AND success_at IS NULL`；任一非零使批次为 `WARNING`、总览禁止进入对账，并生成 `PAYMENT_SUCCESS_TIME_MISSING` 告警。
5. `GET /api/payments` 与导出接口接受虚拟筛选 `SUCCESS_TIME_MISSING`，列表、计数与 CSV 均精确展开为上述缺失集合；告警动作路由固定为 `/payments?status=SUCCESS_TIME_MISSING`。
6. 历史批次未采集完整性字段时返回 `NULL` 并判定“日终事实不完整”，需要 `RERUN` 形成新快照。接口事实不证明渠道实际扣款、账单、资金、清分或账务最终一致。

### 14.313 支付监控成功事实一致性契约

1. `GET /api/payment-monitor/overview` 的 `trends[].totalCount` 按 `created_at` 所在日统计支付创建量；`successCount / successAmount` 只统计 `status=SUCCESS` 且按 `success_at` 所在日归属。
2. 最近七日窗口同时应用于创建时间和成功时间，按两类日期的并集返回；某日期只有成功事实时 `totalCount=0`，只有创建事实时 `successCount=0 / successAmount=¥0.00`。未来日期和窗口外事实不得进入。
3. `alerts` 新增 `PAYMENT_SUCCESS_TIME_MISSING`，集合为全局 `status=SUCCESS AND success_at IS NULL`，等级为 `P1/danger`，动作路由为 `/payments?status=SUCCESS_TIME_MISSING`。
4. `summary.alertCount` 纳入该告警类型，取值范围由 `0..3` 扩展为 `0..4`；服务层最终以本次响应实际 `alerts.length` 设置该值，保证单个响应内一致，不表示各项 `affectedCount` 之和。
5. `summary` 和 `channelMetrics` 仍是当前支付单状态集合的全量/渠道队列观察口径，不因趋势时间轴变化改写；调用方不得混用趋势成功发生量与创建量计算日转化率。
6. 窗口仍以数据库会话时区为准。本接口只报告 payment-core 本地成功状态，不证明渠道扣款、清分、账务入账、资金一致或告警已经人工处置。

### 14.314 退款成功时间完整性契约

1. 日终退款成功单量和金额继续按 `DATE(success_at)=bizDate` 归属；退款申请日内 `status=SUCCESS AND success_at IS NULL` 计入 `missingRefundSuccessTimeCount`，不允许回退到 `applied_at` 充当成功账期。
2. 日终总览新增 `openMissingRefundSuccessTimeCount`，最近批次和 CSV 同步返回 `missingRefundSuccessTimeCount`。任一缺失使新批次为 `WARNING`，总览和批次均禁止进入对账。
3. 历史批次未采集该字段时返回 `NULL` 并判定“日终事实不完整”，调用方必须通过 `RERUN` 形成新快照，不得将空值转换为零。
4. `GET /api/refunds` 与导出接口接受虚拟筛选 `SUCCESS_TIME_MISSING`，列表、计数与 CSV 精确展开为 `SUCCESS + success_at IS NULL`；该值不是退款状态机状态。
5. 日终与支付监控新增 `REFUND_SUCCESS_TIME_MISSING / P1` 告警，动作路由固定为 `/refunds?refundStatus=SUCCESS_TIME_MISSING`；监控 `summary.alertCount` 纳入该类型并由服务层保持与实际告警数组一致。
6. `success_at` 仅表示 payment-core 可信退款结果的本地落库时间，不证明客户到账、渠道账单、账务冲销、资金一致或告警处置完成。

### 14.315 退款终态事实一致性契约

1. 退款从 `PROCESSING` 收口到 `SUCCESS/FAIL` 时，退款单更新、最新退款尝试终态更新和操作日志写入必须处于同一事务；任一条件更新未命中时整体失败，不允许形成半成功响应。
2. 成功退款写入 `success_at=COALESCE(success_at, NOW())`，该字段一旦存在不得被重复或并发结果覆盖；失败和重试路径不得保留成功时间。
3. 回调及主动查单内部快照同时读取 `successAt / attemptStatus / attemptCompletedAt`。重复终态结果只有在退款单终态与最新尝试终态相同、状态样式符合终态、尝试完成时间存在、成功终态存在成功时间且失败终态不存在成功时间时，才可按幂等结果返回；具体样式门禁见 14.373。
4. 上述事实不一致时接口按内部一致性失败闭合，不调用渠道补查、不自动回填或推断历史时间。普通退款详情读取不因此被改造成修复接口。
5. 本契约不新增前端响应字段；快照字段仅供服务端收口校验。当前仍缺少不依赖再次触达的历史全量巡检与日终尝试一致性快照。
6. 本地终态一致只证明 payment-core 的退款单与尝试记录互相匹配，不证明渠道退款到账、渠道账单、账务冲销、资金一致或正式关账。

### 14.316 退款终态事实主动巡检契约

1. 退款终态事实不一致集合包含：退款单状态为 `SUCCESS/FAIL`，且 `terminal_at` 缺失、失败退款残留 `success_at`，或最新退款尝试不存在、尝试状态/样式不符合退款单终态、尝试 `completed_at` 为空；渠道归属和时间线补充门禁见 14.366、14.371、14.373。
2. `SUCCESS + success_at IS NULL` 由 `REFUND_SUCCESS_TIME_MISSING` 单独负责；只缺成功时间且最新成功尝试完整的退款不进入新集合，避免两个告警无条件重复。
3. 日终批次新增 `refundTerminalFactMismatchCount`，按退款单 `terminal_at` 所在业务日采集；历史终态时间缺失时按 `applied_at` 所在业务日兜底。总览新增 `openRefundTerminalFactMismatchCount`，最近批次和 CSV 同步返回。任一非零使批次 `WARNING` 并禁止进入对账。
4. 历史批次未采集该字段时返回 `NULL`，总览和批次必须显示事实不完整并要求 `RERUN`；不得使用当前扫描值回填旧批次。
5. `GET /api/refunds` 与导出接口接受虚拟筛选 `TERMINAL_FACT_MISMATCH`，列表、计数和 CSV 使用同一集合；日终和支付监控告警类型为 `REFUND_TERMINAL_FACT_MISMATCH / P1`，动作路由固定为 `/refunds?refundStatus=TERMINAL_FACT_MISMATCH`。
6. 本契约报告 payment-core 内部结构化事实一致性，不证明渠道到账、渠道账单、账务冲销、资金一致或告警处置完成。

### 14.317 支付成功终态事实一致性契约

1. `t_payment_attempt` 新增可空 `completed_at`，表示 payment-core 将该尝试写入终态的本地时间。历史值不自动回填；新增迁移必须先于包含新查询列和成功终结 SQL 的应用版本执行。
2. `POST /api/payments/callback/{channel}` 与 `POST /api/payments/query` 确认成功时，支付单状态、`success_at` 与最新支付尝试“成功”及 `completed_at` 必须在同一事务内收口；最新尝试按 `created_at DESC, id DESC` 唯一裁决。
3. 最新尝试终结未精确更新一行时接口抛出内部一致性错误，事务整体回滚，不允许继续更新订单、账单、预付单、成功事件或提交令牌。
4. 支付详情新增 `successAt` 和 `latestAttemptCompletedAt`。对于已经为 `SUCCESS` 的支付，只有 `successAt` 非空、`latestAttemptStatus=成功` 且 `latestAttemptCompletedAt` 非空时，重复成功回调或主动查单才幂等返回。
5. 任一成功事实不完整时返回内部一致性错误，不调用渠道、不自动补写历史数据。该异常当前尚无独立列表筛选、日终快照或监控告警，需由后续主动巡检契约补齐。
6. `CLOSED` 不纳入本契约：未提交人工关闭和过期关闭可以没有支付尝试，其一致性规则需要按关闭来源单独设计。

### 14.318 支付成功事实主动巡检契约

1. 支付成功事实不一致集合最初只检查 `SUCCESS` 支付的最新尝试状态和完成时间；现行集合还核对支付/预付正金额及唯一绑定、预付来源、渠道归属与成功时间线，详见 14.386—14.396 及 14.398。最新尝试完整不再足以证明可信成功。
2. `SUCCESS + success_at IS NULL` 由 `PAYMENT_SUCCESS_TIME_MISSING` 独立负责；只有成功时间非空的单据进入 `SUCCESS_FACT_MISMATCH`。`CLOSED` 不纳入本契约。
3. 日终批次新增 `paymentSuccessFactMismatchCount`，按支付单 `success_at` 所在业务日采集；总览新增 `openPaymentSuccessFactMismatchCount`，最近批次和 CSV 同步返回。任一非零使批次 `WARNING` 并禁止进入对账。
4. 历史批次未采集该字段时返回 `NULL`，总览和批次必须显示事实不完整并要求 `RERUN`；不得使用当前扫描值回填旧批次。
5. `GET /api/payments` 与导出接口接受虚拟筛选 `SUCCESS_FACT_MISMATCH`，列表、计数和 CSV 使用同一集合；日终和支付监控告警类型为 `PAYMENT_SUCCESS_FACT_MISMATCH / P1`，动作路由固定为 `/payments?status=SUCCESS_FACT_MISMATCH`。
6. 本契约报告 payment-core 内部结构化事实一致性，不证明渠道扣款、渠道账单、清分、账务入账、资金一致或告警处置完成。

### 14.319 支付日终账期半开区间契约

1. 支付日终内部统计接口继续接收严格 `yyyy-MM-dd` 业务日期；所有创建时间、成功时间和申请时间条件统一解释为数据库会话时区下 `[bizDate 00:00:00, bizDate+1 00:00:00)`。
2. 下界包含业务日零点，上界排除次日零点；不得使用 `23:59:59`、毫秒截断或浏览器时区补偿，避免高精度时间遗漏和相邻账期重复。
3. 支付总量、待收口支付和支付成功时间缺失按支付创建时间范围；支付成功、支付成功事实不一致、渠道成功、内部成功、成功差异及渠道/内部异常按支付成功时间范围。
4. 退款成功单量和金额按退款成功时间范围；退款失败单量和金额按退款终态时间范围；退款成功时间缺失和待收口退款按申请时间范围；退款终态不一致按终态时间范围，历史终态时间缺失按申请时间兜底。
5. SQL 不得在索引时间列外包 `DATE()` 后再比较业务日；日期转换和次日计算只能作用于绑定常量，以保留范围扫描可能性。
6. 本契约不保证目标数据库一定选择某个索引；上线前仍需以真实 MySQL 版本、统计信息、数据分布和 `EXPLAIN ANALYZE` 验证执行计划。

### 14.320 支付成功事实巡检账期归属契约

1. `paymentSuccessFactMismatchCount` 是支付成功发生后的结构化一致性事实，必须按 `t_payment_order.success_at` 所在业务日采集，不得按支付创建日归属。
2. 跨日成功支付在成功日检查最新支付尝试；成功日下界包含 `00:00:00`，次日 `00:00:00` 排除。支付创建日不再重复统计该成功事实。
3. `SUCCESS + success_at IS NULL` 无法归入成功日，继续由 `missingPaymentSuccessTimeCount` 按创建日采集；不得用创建时间替代缺失成功时间。
4. 本轮不改变接口字段、告警类型、下钻路由、历史批次 `NULL/RERUN` 规则或批次准入逻辑，只修正新执行批次的内部统计集合。
5. 该契约只约束 payment-core 本地账期归属，不证明渠道扣款、渠道账单、账务入账、资金一致或正式关账。

### 14.321 退款终态时间与巡检账期契约

1. `t_refund_order.terminal_at` 表示 payment-core 将退款单写入当前 `SUCCESS/FAIL` 终态的本地时间。`PROCESSING -> SUCCESS/FAIL` 必须在同一条件更新中首次写入；`FAIL -> PROCESSING` 重试必须清空。
2. 终态回调和主动查单幂等成立时，退款单 `terminalAt`、最新尝试同终态及 `attemptCompletedAt` 均必须存在；成功还要求 `successAt` 存在，失败要求 `successAt` 为空。
3. 退款详情响应新增可空字符串字段 `terminalAt`，缺失时返回 `-`；它不是渠道到账或账务冲销时间，调用方不得据此声称资金完成。
4. `TERMINAL_FACT_MISMATCH` 扩展为同时识别 `terminal_at IS NULL`。日终对非空值按终态日采集；空值按申请日兜底以支持重跑，但不得推断实际终态日。
5. 增量迁移优先以成功退款 `success_at` 回填，再以最新尝试状态匹配且 `completed_at` 非空的事实补齐；其余保持 `NULL`。应用版本必须在迁移后发布。
6. 本契约不证明历史数据均可回填、渠道真实到账、账务冲销、资金一致或正式关账。

### 14.322 日终退款失败事实快照契约

1. 日终批次新增 `refundFailCount / refundFailAmount`，只统计状态为 `FAIL` 且 `terminal_at` 位于业务日左闭右开区间的退款；无终态时间记录不猜测失败日，由终态事实不一致集合单独阻断。
2. 总览新增 `openRefundFailCount / latestRefundFailAmount`，最近批次和 CSV 同步返回失败单量与金额。管理端统一下钻 `/refunds?refundStatus=FAIL`。
3. 失败退款非零时批次状态为 `WARNING`，总览和批次对账准入均为阻断，日终告警类型为 `REFUND_FAIL / P1`；待审核和处理中退款继续使用 `PENDING_REFUND` 的有条件准入口径。
4. 全新 schema 两字段为非空默认零；增量迁移对历史批次使用可空字段且不回填，`NULL` 表示未采集，必须通过 `RERUN` 生成新快照。
5. 失败金额表示退款单申请金额的本地失败快照，不证明渠道已出款、客户未到账、账务冲销完成或最终资金损失；真实原因和补退结果仍需渠道、账务与运营证据。

### 14.323 失败退款批量重试候选契约

1. `POST /api/payment-task-center/retry-failed-refunds` 与定时失败退款重试共用候选集合：退款单为 `FAIL`、`terminal_at` 非空、`success_at` 为空，且最新退款尝试同为 `FAIL` 并具备 `completed_at`；候选按 `terminal_at ASC, id ASC` 排序并应用既有有界批次。
2. `terminal_at` 表示当前失败终态形成时间，优先级不再由 `applied_at` 决定；相同终态时间使用主键保证稳定顺序。查询复用 `(status, terminal_at)` 索引。
3. 任何命中 `TERMINAL_FACT_MISMATCH` 的失败退款均不进入批量处理，任务结果的处理数也不包含它们；记录保持原状态，由 P1 告警和既有退款下钻交给人工核查。
4. `POST /api/refunds/retry` 的单笔人工重试不调用批量候选查询，现有请求、响应和状态机契约不变；操作人员仍需基于可验证事实决定是否重试。
5. 本契约不承诺多实例间全局严格公平，也不证明渠道补退、客户到账、账务冲销或资金一致；目标 MySQL 仍需验证真实执行计划和调度行为。

### 14.324 失败退款任务中心分层契约

1. `GET /api/payment-task-center/overview` 保留 `failedRefundCount`，新增 `retryableFailedRefundCount / blockedFailedRefundCount`；三者分别表示失败总风险、完整事实批量候选和终态事实异常阻断。
2. 可重试集合与批量候选 SQL 复用同一退款终态事实谓词；阻断集合是失败退款中命中 `TERMINAL_FACT_MISMATCH` 的记录，必须满足 `failedRefundCount = retryableFailedRefundCount + blockedFailedRefundCount`。批量候选继续按失败终态时间排序并受批次上限约束，概览仅表示未截断的资格数量。
3. `focusAlerts` 新增 `REFUND_RETRY`。失败总数非零时为 P1；阻断数非零时路由终态事实不一致列表，否则路由普通失败退款列表。
4. 管理端不得将总失败数当作按钮可执行数量。按钮状态和候选提示使用 `retryableFailedRefundCount`，事实异常数即使不可批量执行也必须持续显示。
5. 新字段只描述 payment-core 当前本地结构化事实和任务资格，不证明渠道结果、客户到账、账务冲销、资金一致或正式关账。

### 14.325 主动查单人工候选展示契约

1. `pendingQueryBacklogCount` 与人工 `POST /api/payment-task-center/query-pending-payments` 使用同一基础资格：存在支付尝试、达到首次等待阈值且仍为 `WAIT_CALLBACK`；预付单过期不排除已提交交易。
2. `pendingRefundQueryBacklogCount` 与人工 `POST /api/payment-task-center/query-pending-refunds` 使用同一基础资格：退款为 `PROCESSING`、最新尝试具备渠道定位事实，并且已有渠道退款号和提交时间，或无渠道退款号但尝试及对应提交事件均为 `UNKNOWN`。
3. `duePendingQueryCount / duePendingRefundQueryCount` 在基础资格上继续应用自动查询历史退避，只用于表达当前自动到期量；管理端人工按钮不得以这两个字段决定可执行状态。
4. 管理端在对应人工候选数为零时禁用按钮，非零时展示候选数。该前端保护不替代服务端再次查询资格，也不承诺点击时处理数必然等于此前总览快照。
5. 本轮不新增或修改 HTTP 字段、请求、响应、候选 SQL 与任务状态机；页面和固定 mock 不证明真实渠道查询、回调收口、退款到账或资金一致。

### 14.326 失败事件重发资格分层契约

1. `GET /api/payment-task-center/overview` 保留 `failedEventCount`，新增 `retryableFailedEventCount / deadLetterEventCount`；分别表示 `FAILED + DEAD_LETTER` 总风险、`FAILED` 批量候选和 `DEAD_LETTER` 人工处置集合。
2. 三项必须满足 `failedEventCount = retryableFailedEventCount + deadLetterEventCount`。`POST /api/payment-task-center/republish-failed-events` 和自动重发继续只查询 `publish_status = 'FAILED'`，不得处理 `DEAD_LETTER`，也不得信任页面回传数量。
3. `focusAlerts` 的 `PAYMENT_EVENT` 告警同时返回三项数量。死信数大于零时严重等级为 `P1`、动作路由为 `/payment-events?publishStatus=DEAD_LETTER`；否则路由为 `/payment-events?publishStatus=FAILED`。
4. 管理端按钮状态与文案使用 `retryableFailedEventCount`，总风险和死信数即使不可批量执行也必须持续展示。总览与实际执行之间的状态变化由执行端重新查询候选裁决。
5. 本轮不新增数据库字段、索引或迁移。接口字段只描述 payment-core 本地事件状态和任务资格，不证明真实 MQ 已重投、下游已消费、账务已处理或资金一致。

### 14.327 超时关单当前候选展示契约

1. `GET /api/payment-task-center/overview` 的 `expiredPaymentCount / dueExpiredPaymentCount / backingOffExpiredPaymentCount` 分别表示全部合格超时积压、当前已到重试时间的候选和失败退避未到期集合，并满足总数等于两类分项之和。
2. `POST /api/payment-task-center/close-expired-payments` 与自动调度共用同一候选服务，只查询无重试标记或 `next_retry_at <= NOW()` 的记录；人工调用不越过失败退避，也不接受页面数量作为候选参数。
3. 管理端按钮状态和文案使用 `dueExpiredPaymentCount`，总积压与退避数即使不可立即执行也必须持续展示；候选为零时禁止页面触发空批次。
4. 总览读取与任务执行之间允许发生并发状态变化，实际 `processedCount` 以执行时 SQL 为准，不承诺等于此前页面快照，也不得为保持相等而锁住全部积压。
5. 本轮不新增或修改 HTTP 字段、候选 SQL、状态机、schema、索引或迁移；页面固定样例不证明真实关单、渠道状态、多实例行为或生产稳定性。

### 14.328 告警回执人工候选与有效占用契约

1. `GET /api/payment-task-center/overview` 新增 `manualIssueAlertReceiptCount`，表示当前可由人工批量回查的外发告警数；`pendingIssueAlertReceiptCount` 继续表示全部供应商已受理且待确认的风险存量，两者不要求相等。
2. 人工计数与 `POST /api/payment-task-center/reconcile-issue-alert-receipts` 的执行候选共享资格：外部通道、已派发、供应商 `ACCEPTED`、待确认，并关联同一异常下仍待确认的顶层 `IN_APP_OUTBOX` 来源。
3. 人工批量允许越过首次等待、失败退避和自动次数上限，但不得越过仍处于 claim 超时窗口内的 `QUERYING`；claim 已过期时可重新进入候选。自动到期、退避、查询中和自动耗尽字段继续保持各自既有语义。
4. 管理端以 `manualIssueAlertReceiptCount` 决定按钮文案和禁用状态，但执行接口不接收页面候选数量；实际处理集合由服务端在请求时重新查询并通过 claim CAS 裁决。
5. `POST /api/payment-task-center/reconcile-issue-alert-receipts/{alertNo}` 的指定单条人工回查请求、响应和编号校验契约不变，其最终执行仍不得抢占有效 claim。
6. 新字段与候选口径只描述 payment-core 本地任务资格；本地 H2、固定 mock 和接口响应不证明真实供应商查询、通知网关、最终送达、多实例结果、资金一致或生产稳定性。

### 14.329 异常告警派发候选按钮契约

1. `GET /api/payment-task-center/overview` 的 `pendingIssueAlertDispatchCount` 表示当前顶层、待确认且状态为已生成、部分失败或派发失败的 `IN_APP_OUTBOX` 数量，与人工派发的基础候选资格一致。
2. 管理端使用该字段决定按钮文案和禁用状态；`POST /api/payment-task-center/dispatch-issue-alerts` 不接收页面数量，执行时重新查询有界候选并逐条锁定来源。
3. 总览与执行之间允许发生并发变化，实际 `processedCount` 不承诺等于页面快照；候选为零时的前端禁用只避免主动生成空批次，不替代服务端资格与锁裁决。
4. 本轮不新增或修改 HTTP 字段、请求、响应、SQL、schema 或迁移；固定样例不证明真实供应商、通知送达、多实例结果或生产稳定性。

### 14.330 值班路由候选确定性选择契约

1. 首次 SLA 告警、未确认升级和 outbox 派发在当前时刻先按既有班次、星期、日期策略筛选启用路由，再按 `issueType + severity` 分区选择最早创建的路由。
2. 多条路由同时命中不得复制业务候选或挤占批次；无匹配路由时沿用既有默认责任人与通知通道。
3. 本轮不修改 HTTP 契约；确定性选择不等于配置侧重叠冲突已被阻断，真实排班和通知结果仍需外部证据。

### 14.331 值班路由重叠批准契约

1. 值班路由 CREATE、UPDATE、STATUS_CHANGE 候选为启用态时，批准接口在写运行态前复验同异常类型和严重等级的现有配置。
2. 星期集合、日期策略和小时窗口同时相交即返回业务错误并保持复核待办与运行态不变；工作日和非工作日互斥，`ALL_DAYS` 与两者均相交，跨午夜按实际小时集合判断。
3. 停用候选及同编码自身不冲突。接口形状不变；自声明复核和本地锁测试不构成可信审批、生产并发或真实排班证据。

### 14.332 值班路由范围批准串行化契约

1. CREATE、UPDATE、STATUS_CHANGE 的启用候选在重叠复验前，按候选 `issueType + severity` 初始化并锁定稳定协调行；即使该范围尚无运行路由，也必须取得协调行后才能继续。
2. 同范围批准在事务内串行执行，后取得锁的请求必须读取前一已提交结果并重新判断重叠；协调行获取失败时批准失败，复核待办与运行态均不得被完成或改写。
3. HTTP 路径、请求和响应保持不变，客户端不传递锁键、锁标识或等待结果。协调表唯一键只定义技术锁粒度，不把同范围合法非重叠路由收窄为单条。
4. 本地双事务测试不承诺目标 MySQL 的等待时长、错误类型或死锁行为；调用方仍应按既有统一错误契约处理失败，不得据此宣称生产审批或排班已验证。

### 14.333 控制策略巡检候选计数契约

1. `GET /api/payment-task-center/overview` 新增 `enabledControlPolicyCount`，表示当前状态为 `ENABLED`、会被批量控制策略自检扫描的策略数量；无数据时返回 `0`。
2. `controlPolicyWarningCount` 继续表示启用且最近自检状态非 PASS 的策略数。两字段允许分别为 `N/0`，不得以告警为零推断没有巡检候选。
3. 管理端只用启用数决定人工巡检按钮状态；`POST /api/payment-task-center/control-policy-self-checks` 请求仍只提交操作人，执行时重新读取启用策略，实际 `processedCount` 不承诺等于页面快照。
4. 本轮不修改巡检结果字段、任务日志结构或自动调度契约；本地 SQL 与页面样例不构成真实配置准入或生产运行证据。

### 14.334 SLA 风险与动作候选计数契约

1. `GET /api/payment-task-center/overview` 保留 `overdueIssueCount`，表示全部超过 SLA 的基础异常；新增 `pendingIssueAlertGenerationCount`、`pendingIssueAlertEscalationCount` 和 `actionableIssueSlaCount`，无数据时均返回 `0`。
2. 首次告警候选排除已有待确认告警的同一 `issueNo`；升级候选仅包含超过当前值班路由升级等待时间、尚未确认且尚未生成升级告警的来源告警；`actionableIssueSlaCount` 为两者之和。
3. 管理端只用动作合计决定人工巡检按钮状态；`POST /api/payment-task-center/escalate-overdue-issues` 请求仍只提交操作人，执行时重新查询两个候选集合，实际 `processedCount` 不承诺等于页面快照。
4. 本轮不修改 SLA 阈值、告警生成键、每类批次上限、任务日志结构或自动调度契约；本地 SQL 与页面样例不构成真实通知送达或生产运行证据。

### 14.335 查单监控告警候选计数契约

1. `GET /api/payment-task-center/overview` 新增 `actionableQueryMonitorAlertCount`，表示当前可生成的查单失败趋势和限流拒绝监控告警类别数，无候选时返回 `0`，最大返回 `2`。
2. 失败趋势候选沿用失败笔数阈值，或最小样本数与失败率组合阈值；限流候选沿用限流拒绝次数阈值。任一类别在冷却窗口内已有同类待确认 `IN_APP_OUTBOX` 来源告警时不计入候选。
3. 管理端只用候选数决定人工按钮状态；`POST /api/payment-task-center/generate-query-monitor-alerts` 请求仍只提交操作人，执行时重新读取指标和冷却状态，实际 `processedCount` 与 `successCount` 不承诺等于页面快照。
4. 本轮不修改阈值配置、冷却时长、生成键、任务日志或自动调度契约；固定样例不构成真实查询流量、生产告警生成或通知送达证据。

### 14.336 路由规则复核批准并发契约

1. 路由规则复核批准 HTTP 路径、请求体和响应结构不变；服务端对状态为启用且灰度比例大于 `0` 的候选增加冲突全集串行化，调用方无需也不得提交锁标识。
2. 批准事务固定执行审核单行锁、候选字段与渠道校验、初始化并锁定 `ENABLED_RULE_SET` 协调行、锁当前规则、当前读全部规则、冲突复验、运行态与版本写入及待办完成。
3. 协调行无法读取、基线失效或冲突复验失败时请求失败并回滚，不得写入运行态、版本或批准结果；停用和 `0%` 候选不获取冲突全集锁。
4. 页面预检和普通规则列表不是并发批准的最终裁决依据。目标 MySQL 的锁等待、超时和死锁表现仍需环境验证；本地 H2 不构成可信审批或生产放行证据。

### 14.337 路由同优先级潜在冲突确认契约

1. `POST /api/payment-config/route-rule-reviews/{requestNo}/approve` 请求体新增可选布尔字段 `priorityCollisionAcknowledged`；缺失、`null` 或 `false` 均视为未确认。驳回接口可接收该字段但不使用其放行任何候选。
2. 候选涉及 `PRIORITY_COLLISION` 且未确认时返回业务校验失败，不写运行态、版本或批准结果；字段为 `true` 时仅允许流程继续，不改变冲突分类，也不承诺表达式实际互斥。
3. `BLOCKER` 不受确认字段影响，任何取值都必须拒绝。服务端在批准事务中取得 `ENABLED_RULE_SET` 协调锁并当前读最新规则全集后裁决，客户端预检结果和页面状态不得替代该判断。
4. 现有响应结构、复核状态、异人声明、基线漂移和回滚语义不变；本轮不新增数据库字段。该自声明确认不构成可信登录身份、正式签字审批或生产放行证据。

### 14.338 路由冲突复核审计响应契约

1. 路由复核列表项新增可空布尔字段 `priorityCollisionDetected` 与 `priorityCollisionAcknowledged`。两者来自批准事务持久化快照，不由查询时重新计算。
2. 待处理、直接驳回及迁移前历史已完成记录返回 `null/null`，表示没有可证明的批准冲突复验事实；批准时未发现潜在碰撞返回 `false/false`；发现并完成显式确认返回 `true/true`。
3. 服务端不会正常生成 `true/false`，但客户端必须将该组合视为未确认异常，不得展示为已确认。`BLOCKER` 请求仍失败回滚，不形成批准状态或碰撞确认记录。
4. 批准请求字段 `priorityCollisionAcknowledged` 的输入语义不变；新增响应字段只增强事后审计。自声明值不代表可信身份、正式签字、不可抵赖凭证或表达式互斥证明。
5. 目标 MySQL 使用新响应前必须执行 `2026-09-13-add-payment-route-rule-review-conflict-audit.sql`；当前本地 H2 与 DTO 验证不代表生产迁移已经完成。

### 14.339 路由冲突确认的请求归属约束

1. `priorityCollisionAcknowledged` 仍是批准请求中的布尔字段，HTTP 结构不变；客户端必须把确认选择绑定到正在处理的 `requestNo`，不得用复核列表级共享布尔值跨申请复用。
2. 只有批准动作且当前处理申请号与已确认申请号一致时才发送 `true`；未确认、其他申请和驳回动作均发送 `false`。服务端不信任客户端预检，仍在同一批准事务内锁定并复验最新规则全集。
3. 客户端成功处理后应清除该申请的确认状态，刷新后申请已不再待办也应清除；请求失败可保留当前申请的选择用于显式重试，但不得自动迁移到另一申请。
4. 该约束只表达自声明风险接受与申请的关联，不构成可信身份、正式签字、不可抵赖凭证或表达式互斥证明。

### 14.340 路由冲突对象审计响应契约

1. 路由复核列表项新增可空字符串字段 `priorityCollisionRuleCodesJson`，内容为批准事务最终冲突复验所涉及对照规则编码的 JSON 数组；数组按规则编码稳定排序，候选自身编码不包含在内。
2. 批准且 `priorityCollisionDetected=true` 时返回非空 JSON 数组；待处理、直接驳回、迁移前历史以及批准无碰撞返回 `null`。该字段是批准时快照，查询接口不按当前规则重新计算或回填。
3. 未携带有效确认而被拒绝时，既有业务校验错误追加具体对照规则编码；请求仍失败回滚，不形成批准记录或冲突对象快照。`BLOCKER` 的无条件拒绝语义不变。
4. 客户端必须使用 JSON 解析，不得按逗号拆分；缺失或畸形值应明确暴露未知/异常状态，不得解释为未发现冲突。现有批准请求字段、路由复核路径和其余响应字段不变。
5. 目标 MySQL 读取该字段前必须执行 `2026-09-13-add-payment-route-rule-review-conflict-targets.sql`；快照只记录检测器输出与自声明确认，不构成表达式互斥、可信签字或生产放行证据。

### 14.341 路由冲突确认绑定精确集合契约

1. 路由复核列表项新增数组字段 `previewPriorityCollisionRuleCodes`。待办申请基于查询时当前运行态计算候选涉及的 `PRIORITY_COLLISION` 对照规则编码并稳定排序；已完成记录不补算，字段可为空。
2. 批准请求新增数组字段 `acknowledgedPriorityCollisionRuleCodes`，与既有 `priorityCollisionAcknowledged` 同时使用。客户端只有明确确认当前申请和完整预览集合时才传 `true + 非空数组`；驳回或未确认传 `false + []`。
3. 服务端不信任列表预览。在既有全局协调锁内当前读最新规则全集后，重新计算实际集合并与请求集合精确比较；新增、减少至空、替换、缺失或重复编码均返回业务校验错误“同优先级潜在冲突集合已变化”或对应输入错误，并要求刷新重确认。
4. 实际集合非空而布尔值未明确为 `true` 时仍按未确认拒绝；任一 `BLOCKER` 仍无条件拒绝。精确匹配成功后，数据库只保存服务端最终集合的 `priorityCollisionRuleCodesJson`，不保存客户端预览。
5. 本轮不新增端点或改变既有路径、状态码与复核状态机；两个新数组只加强待办展示和批准前置条件，不构成表达式互斥、可信身份、正式签字或生产放行证据。

### 14.342 路由冲突确认非法输入锁前拒绝契约

1. `priorityCollisionAcknowledged=true` 时，服务端先校验 `acknowledgedPriorityCollisionRuleCodes` 的每项非空、无 ISO 控制字符、最长 `64` 字符且集合内不重复，再构建候选或取得全局冲突协调锁。
2. 非法数组返回既有业务校验错误，不查询候选渠道、不初始化或获取 `ENABLED_RULE_SET` 协调锁、不锁运行态规则、不读取冲突全集，也不更新规则、版本或复核记录；目标复核申请的待办锁和异人校验仍先执行。
3. `priorityCollisionAcknowledged` 非 `true` 时，附带数组不作为风险接受证据；服务端按空确认集合处理，并在实际冲突非空时返回未确认错误。合法请求的锁内精确集合比较和最终审计快照契约不变。
4. 本轮不新增字段、端点或错误码，不改变合法请求响应；前置校验只收紧非法输入的副作用边界，不构成接口鉴权、可信审批或限流能力。

### 14.343 候选路由冲突扫描范围契约

1. 路由复核列表中的 `previewPriorityCollisionRuleCodes` 与批准事务内的冲突复验只计算候选规则和每条有效当前规则之间的冲突，不返回当前规则彼此之间、且与候选无关的冲突。
2. 有效当前规则继续限定为已启用、灰度比例大于 `0`、表达式可解析且编码不同于候选；候选表达式无效仍返回 `INVALID_EXPRESSION / BLOCKER`，既有冲突类型、严重级别和消息契约不变。
3. 全量路由冲突查询接口继续检查全部有效规则的两两组合，并保留无效表达式诊断；本轮优化不得解释为全量接口只返回候选相关结果。
4. 本轮不新增端点、字段、状态码或错误码，不改变精确确认集合、最终 JSON 审计快照和批准失败回滚语义。扫描复杂度变化是服务端内部实现边界，不构成生产性能 SLA。

### 14.344 路由复核阻断冲突预览契约

1. 路由复核列表项新增数组字段 `previewBlockingConflicts`，元素复用 `PaymentRouteRuleConflictDTO`，返回候选基于查询时当前运行态规则计算出的 `severity=BLOCKER` 冲突。
2. 待办申请返回数组，可能为空；已完成记录不按当前规则重新计算，字段可为 `null`。阻断对象包含冲突类型、严重级别、双方规则、优先级、交集场景和原因，方向保持 `currentRule -> candidate`。
3. `previewBlockingConflicts` 与 `previewPriorityCollisionRuleCodes` 来自同一次候选扫描，但用途不同：阻断不可确认绕过，同优先级潜在碰撞仅在精确集合确认后允许继续复验。
4. 列表预览不是批准凭证。客户端可用它提示或禁用按钮，服务端仍必须在批准事务的全局协调锁内当前读最新规则并拒绝任一实际 `BLOCKER`；请求不携带或回传阻断预览。
5. 本轮不新增端点、状态码、数据库字段或迁移，不改变批准失败回滚和最终碰撞审计快照契约。

### 14.345 路由复核警告冲突预览契约

1. 路由复核列表项新增数组字段 `previewWarningConflicts`，元素复用 `PaymentRouteRuleConflictDTO`，返回候选基于查询时当前运行态规则计算出的 `severity=WARNING` 冲突。
2. 字段覆盖 `DUPLICATE_RULE / OVERLAPPING_DUPLICATE / SHADOWED_RULE / PARTIAL_SHADOW`；待办申请返回数组且可能为空，已完成记录不按当前规则补算并可返回 `null`。
3. 警告、阻断和同优先级确认编码来自同一次候选扫描。警告仅为维护风险提示，不进入批准请求，不要求确认，不影响 `BLOCKER` 无条件拒绝或 `PRIORITY_COLLISION` 精确集合比较。
4. 客户端可以展示警告但不得把它解释为已经接受的风险、自动阻断或表达式覆盖证明；列表快照漂移时，服务端批准事务仍以协调锁内最新冲突复验为准。
5. 本轮不新增端点、状态码、数据库字段或迁移，不改变运行态规则、版本和碰撞审计的写入语义。

### 14.346 支付渠道流水绑定锁后复核契约

1. `POST /api/payments/callback/{channel}` 在验签后先核对支付单渠道与已绑定流水；`SUCCESS` 回调取得订单协调锁并重读支付单后必须再次执行相同核对，防止锁等待期间的绑定变化被覆盖。
2. 锁后渠道不一致返回 `PAYMENT-1030`，渠道流水不一致返回 `PAYMENT-1031`；两者均不得抢占支付成功状态、更新支付尝试/预付单、结算订单账单或创建 `PAYMENT_SUCCESS` 事件。
3. `POST /api/payments/query` 的渠道结果包含流水时，必须与初始或锁后最新支付单已绑定流水一致；不一致返回 `PAYMENT-1026`，并只写固定最小化查单失败事实，不写返回码快照或成功副作用。
4. 渠道查询未返回流水、但协调锁内重读发现并发回调已写入合法成功流水时，继续返回最新成功详情且不重复结算；渠道返回相同流水时维持既有幂等路径。
5. 本轮不新增端点、请求/响应字段、状态码或数据库结构；上述约束不能替代真实渠道订单归属、到账与对账核验。

### 14.347 支付成功精确账单结算契约

1. 支付详情中的 `billNo` 必须来自该支付单最新预付单的 `bill_no`，账单连接条件固定为 `t_bill.bill_no = t_prepay_order.bill_no`，不得按 `order_no` 任意关联同订单账单。
2. 支付成功回调与主动查单成功按 `billNo` 更新唯一绑定账单；标准订单存在时继续更新订单总额和履约前状态，虚拟订单无订单源时只使用支付金额更新绑定账单。
3. 绑定账单号缺失、标准订单更新未恰好命中一行或账单更新未恰好命中一行时，本次成功收口失败；支付单、尝试、预付单、订单、账单、通知与成功事件必须由事务整体回滚。
4. 同订单其他账单不得因本次支付成功被修改。本轮不新增端点、DTO 字段、状态码、schema 或迁移，也不声明一个订单只能存在一张账单。
5. 本地影响行数校验和 H2 多账单样例不替代生产绑定完整性、目标 MySQL 回滚、真实渠道到账与财务对账核验。

### 14.348 支付尝试历史保留契约

1. `POST /api/payments/callback/{channel}` 收到非成功终态回调并成功维持支付单 `WAIT_CALLBACK` 后，只能把该支付单最新一次支付尝试更新为“待回调 / warn”；最新尝试按 `created_at DESC, id DESC` 确定。
2. 较早支付尝试是不可被当前回调批量覆盖的历史事实。最新尝试更新必须恰好影响一行，否则返回服务端异常并由事务回滚本次通知、支付单、预付单和事件写入。
3. 成功回调继续通过独立的最新尝试成功终结逻辑写“成功 / success”及完成时间；本轮不改变成功、重复回调、并发成功或渠道流水绑定契约。
4. 超时关单只处理没有任何支付尝试和渠道提交事实的预付单，不创建虚假的“已关闭”尝试；支付单关闭、预付单失效和 `PAYMENT_EXPIRED_CLOSED` 事件契约不变。
5. 本轮不新增端点、请求/响应字段、状态码、schema 或迁移；服务端异常不等同于渠道支付失败，仍需按原支付事实排查。

### 14.349 渠道提交与快速回调并发收口契约

1. `POST /api/payments/submit` 在渠道返回后，只能对仍为 `WAIT_CALLBACK`、渠道编码与本次路由一致且渠道流水为空或等于返回流水的支付单绑定提交结果；不得把 `SUCCESS` 或 `CLOSED` 改回待回调。
2. 若同流水成功回调已先完成，提交接口返回最新预付单状态，只补写本次支付尝试的响应原文；不得覆盖尝试成功状态与完成时间，也不得再写返回码快照、待回调通知或 `PAYMENT_SUBMIT` 事件。
3. 支付尝试结果更新必须恰好影响一行。目标尝试缺失时本次提交结果事务失败，并进入既有结果未知恢复；恢复同样不能覆盖成功、关闭或不一致渠道流水。
4. 非成功渠道回调更新 `WAIT_CALLBACK` 时必须满足既有渠道流水为空或相同；条件更新失败后重读并复核绑定，不一致继续返回 `PAYMENT-1031`，不写通知、尝试、预付单或事件。
5. 本轮不新增端点、请求/响应字段、状态码、schema 或迁移；提交异常和绑定冲突不代表资金失败，仍需按渠道及对账事实核验。

### 14.350 微信小程序前端调用边界

1. 原生小程序按“业务入口 -> 创建预付单 -> 加载收银台 -> 提交支付 -> 支付详情/主动查单”的顺序调用现有接口，路由参数和每次接口响应必须与当前预付单或支付单一致。
2. 小程序支付提交固定使用来源 `housekeeping-mini-program`、支付方式 `微信支付`、demo 渠道 `WX_H5`、商户 `MCH_WX_10001` 和终端 `MINI_PROGRAM`；生产令牌、用户身份及设备上下文必须由正式认证与受控配置替换。
3. 只有收银台仍为待支付、支付单为 `PREPAY_CREATED`、没有渠道尝试、微信通道可用且 `expiresAt` 未到期时允许提交；幂等键在单次提交期间不可变，重复操作必须互斥。
4. 收银台摘要没有 `channelTransactionNo`，不得仅凭摘要判断可关单；客户端必须读取完整支付详情，并仅在未提交渠道且服务端状态允许时展示关单动作，服务端仍执行最终校验。
5. 小程序提交响应可在真实网关已完成签名时临时携带白名单 `nativePaymentParams`（`timeStamp / nonceStr / package / signType / paySign`），只允许小程序终端使用且不落库；本地 demo 适配器仍不返回真实签名参数，小程序不得调用空参数 `wx.requestPayment` 或把 `WAIT_CALLBACK` 渲染为支付成功，因此本地仍只属于联调骨架。

### 14.351 服务端 API 支付调用契约

1. 服务调用方沿用 `POST /api/payments/prepay`、`GET /api/payments/cashier/{prepayOrderNo}`、`POST /api/payments/submit`、`GET /api/payments/{paymentOrderId}`、`POST /api/payments/query` 和 `POST /api/payments/close`，不新增平行状态机或专用资金接口。
2. 提交请求的 `terminal` 固定为 `API`。本地 demo 可使用 `sourceAppId=housekeeping-api`，支付宝对应 `ALIPAY_H5 / MCH_ALI_20001`，微信对应 `WX_H5 / MCH_WX_10001`；生产配置与凭证不得复用示例值。
3. `idempotencyKey` 由调用方按业务请求稳定生成，同一次重试必须复用；预付单号和支付单号作为单一路径段编码，不能通过 `/ ? # %` 改变 API 路由结构。
4. API 调用端原样输出统一响应包。HTTP 非成功或业务码非 `0` 必须返回非零进程状态；`WAIT_CALLBACK`、`USERPAYING`、结果未知和请求异常均不得映射为支付成功。
5. 仓库 CLI 不提供持久化生产密钥、人工回调或强制成功能力。正式服务接入仍需可信身份、凭证托管、请求签名、防重放、来源控制和真实渠道验收。

### 14.352 API 终端运营查询契约

1. `GET /api/payment-routes`、`GET /api/cashier-sessions`、`GET /api/payment-requests` 与 `GET /api/payment-flows` 接收 `terminal=API`，列表、总数、总览和导出必须复用相同筛选口径。
2. 路由结果的 `paymentMethod / terminal / clientIp / idempotencyKey / requestPayload / responsePayload` 来自该路由显式关联的支付尝试，不得取同支付单的任意最新尝试。
3. 收银台列表项新增独立 `payScene`，`terminal` 表示最近一次支付尝试的规范终端；无支付尝试时 `terminal` 回退为 `payScene`，保证待提交会话仍可读取但不伪造渠道尝试。
4. 响应继续执行客户、IP、幂等键和报文脱敏。运营查询命中只证明本地事实可追踪，不表示渠道支付成功、真实身份认证或资金一致。

### 14.353 App/H5/PC 用户端实体一致性契约

1. `GET /api/payments/cashier/{prepayOrderNo}` 的响应必须包含与路径参数一致的 `prepayOrderNo`；`GET /api/payments/{paymentOrderId}` 的响应必须包含与路径参数一致的 `paymentOrderId`。空响应或单号不一致时客户端不得建立可操作页面快照。
2. `POST /api/payments/submit`、`POST /api/payments/query` 与 `POST /api/payments/close` 的成功响应必须同时匹配请求发起时的 `prepayOrderNo` 和 `paymentOrderId`；响应不完整、串单或路由已变化时不得更新页面或跳转。
3. App/H5/PC 动态路由参数变化不新增 API 调用形态，但客户端必须清除上一实体并重新读取。并发 GET 按客户端递增请求号裁决，只有当前路由的最新响应可写入。
4. 支付提交失败后的收银台刷新仍需匹配原预付单和支付单，不能把另一张会话用于解释原请求结果；支付不确定结果继续按既有状态机提示主动查单，不得推导为成功。
5. 结果页返回收银台使用详情响应中的关联预付单号，URL 中的 `prepayOrderNo` 查询参数不作为支付归属依据。本轮不修改服务端 DTO、端点、状态码或状态机。

### 14.354 App/H5/PC 支付结果自动刷新契约

1. 结果页只在详情状态为 `WAIT_CALLBACK / PAYING / RISK_REVIEW` 时自动调用 `GET /api/payments/{paymentOrderId}`；间隔 `5` 秒、最多 `60` 次，其他状态不自动请求。
2. 自动刷新与 `POST /api/payments/query` 语义严格分离：前者只读取平台当前支付投影，后者才会按服务端资格触发渠道主动查单、限流、返回码映射和业务状态处理。
3. 自动详情响应必须匹配当前支付单和已加载预付单。空响应、串单响应、旧请求、已切换路由或已启动人工动作时不得写入页面，失败不改变现有支付结果。
4. 用户执行主动查单或受限关单时，客户端取消待执行计时器并使在途自动 GET 失效；动作结束后仅在当前实体仍为可异步推进状态时恢复剩余次数内的刷新。
5. 本轮复用既有详情响应，不新增 API、字段、状态码或持久化事实。生产限流与容量必须按实际并发页面数量单独验证。

### 14.355 原生小程序支付结果自动刷新契约

1. 小程序结果页复用 `GET /api/payments/{paymentOrderId}`，仅对 `WAIT_CALLBACK / PAYING / RISK_REVIEW` 每 `5` 秒自动读取一次，单页面实体最多完成 `60` 次。
2. 页面隐藏、卸载、人工刷新、主动查单和受限关单均取消待执行计时器并递增详情请求版本；旧自动 GET 即使随后返回也不得覆盖当前结果。
3. 自动详情响应必须同时匹配当前支付单和当前有效详情关联的预付单。首次详情加载后使用响应中的 `prepayOrderNo` 替换页面传入值，后续返回收银台不信任可修改的页面参数。
4. 切后台时若首次加载或人工动作尚未完成，页面回到前台后立即重新读取当前详情；被中断动作的迟到响应不得更新页面或恢复计时器。
5. 自动详情读取与 `POST /api/payments/query` 继续保持语义隔离，不新增 API、渠道请求、支付尝试或持久化事实。

### 14.356 标准订单支付详情契约

1. `GET /api/orders/{orderNo}` 按唯一标准订单号返回订单及最近账单、最近支付单、最近预付单聚合详情；路径参数按单一路径段编码。
2. `orderNo` 必填、去除首尾空白、最长 `64` 个字符且不得包含控制字符；非法请求返回 `PAYMENT-1036`，标准订单不存在返回 `PAYMENT-1055`。
3. 最近账单、支付单和预付单分别按自身 `created_at DESC, id DESC` 稳定选择，不要求三者互相绑定为同一支付尝试，也不返回完整历史集合。
4. 响应包含订单应收、已付和最低为零的剩余应付，以及最近账单状态、最近支付状态、支付方式/渠道、最近收银台状态和失效时间；无关联事实的字段返回 `null`。
5. 客户和服务者名称由服务端生成脱敏副本，Mapper 原始 DTO 不因对外响应被修改。接口只读，不更新订单、账单、预付单、支付单或任何资金事实。
6. 该接口仅服务 payment-core 订单侧支付联查，不代表账务余额、清分结果、结算状态、渠道扣款或对账一致。

### 14.357 交易账单支付详情契约

1. `GET /api/bills/{billNo}` 按唯一账单号返回账单、最近预付单和该账单最近关联支付单；路径参数按单一路径段编码。
2. `billNo` 必填、去除首尾空白、最长 `64` 个字符且不得包含控制字符；非法请求返回 `PAYMENT-1036`，账单不存在返回 `PAYMENT-1056`。
3. 最近预付单按该账单范围内 `created_at DESC, id DESC` 稳定选择；最近支付单经 `t_prepay_order.bill_no + payment_order_id` 精确关联后，按支付单 `created_at DESC, id DESC` 选择。
4. 响应包含账单应收、已付和最低为零的待付金额，以及最近预付金额/场景/收银台状态、最近支付金额/方式/渠道/状态；无关联事实的字段返回 `null`。
5. 仅当业务订单号精确命中 `t_order`，且账单号及业务订单号精确归属至少一张原样 `STANDARD_ORDER` 来源预付单、同账单号的全部预付单均为同一订单和标准来源时返回 `standardOrderNo`；无来源、仅虚拟来源或历史标准/虚拟混合来源均不建立标准订单联查。客户名称由服务端生成脱敏副本。
6. 接口只读，不更新账单、预付单、支付单或资金事实；账单支付详情不代表账务余额、清分结果、结算状态、渠道扣款或对账一致。

### 14.358 收银台会话详情契约

1. `GET /api/cashier-sessions/{sessionNo}` 按唯一会话号返回预付单会话及订单、账单和支付单关联标识；当前会话号等于预付单号，路径参数按单一路径段编码。
2. `sessionNo` 必填、去除首尾空白、最长 `64` 个字符且不得包含控制字符；非法请求返回 `PAYMENT-1036`，会话不存在返回 `PAYMENT-1057`。
3. 会话状态和状态类型沿用列表动态投影：成功/完成保持原状态，其他已过期会话返回“已失效 / danger”。最近支付尝试按 `created_at DESC, id DESC` 选择终端，无尝试时回退 `payScene`。
4. 响应包含会话/预付单号、支付单号、业务订单号、账单号、客户、收银台标题、业务场景、终端、金额、状态和时间；客户名称由服务端生成脱敏副本。
5. 仅当预付单 `source_kind` 为原样 `STANDARD_ORDER` 且业务订单号精确命中 `t_order` 时返回 `standardOrderNo`；虚拟业务号即使碰巧同名也不伪装成标准订单。接口不复用面向用户的收银台详情响应，避免管理查询绕过脱敏边界。
6. 接口只读，不更新会话、预付单、支付单、订单、账单或资金事实，也不代表渠道扣款、账务、清分、结算或对账一致。

### 14.359 支付请求详情契约

1. `GET /api/payment-requests/{requestNo}` 按唯一支付尝试号返回请求事实；路径参数按单一路径段编码。
2. `requestNo` 必填、去除首尾空白、最长 `64` 个字符且不得包含控制字符；非法请求返回 `PAYMENT-1036`，不存在返回 `PAYMENT-1058 / 支付请求不存在`。
3. 响应包含请求号、支付单号、预付单号、业务与标准订单号、账单号、支付方式、来源应用、渠道、终端、IP、幂等键、请求状态、尝试与路由配置快照、请求/响应报文及创建/完成时间。
4. 路由编号、规则、结果和快照只通过 `route.attempt_no = attempt.attempt_no` 选择最近一条；列表与导出使用相同关联。历史空尝试号路由保持未关联，不按支付单猜测。
5. IP、幂等键、请求与响应报文由服务层复制后脱敏，不修改数据库事实或 Mapper DTO；接口只读，不产生渠道、状态或资金变化。
6. `successFactIssue` 与同支付单的列表和 CSV 使用同一数据库诊断；异常请求可用于排障，不能仅凭请求状态或日志认定支付成功，管理端禁用异常所属支付单详情入口。
7. `standardOrderNo` 仅在请求自身关联的预付单属于原样 `STANDARD_ORDER`，且预付单、支付单及标准订单的业务订单号精确一致时返回；虚拟订单号碰巧同名不建立联查。

### 14.360 支付流水详情契约

1. `GET /api/payment-flows/{flowSource}/{flowNo}` 按来源类型和来源编号返回唯一支付过程事实；来源支持 `PAYMENT_ATTEMPT`、`CHANNEL_NOTIFY`、`ROUTE_RECORD`、`BUSINESS_EVENT`。
2. `flowSource` 必填、去空白并转大写后执行白名单校验；`flowNo` 必填、去空白、最长 `64` 个字符且不得含控制字符；非法请求沿用 `PAYMENT-1036`，不存在返回 `PAYMENT-1059 / 支付流水不存在`。
3. 支付尝试直接关联预付单和请求；路由仅按 `attempt_no` 精确关联请求；回调和业务事件不按同支付单最近预付单推断归属。
4. 响应可返回确定存在的订单、标准订单、账单、支付单、预付单、请求、渠道、终端、回调、路由和事件字段；IP、幂等键和报文由服务层脱敏。
5. 接口只读，不代表账务、资金、清分、结算、对账、真实渠道或生产放行事实。
6. `standardOrderNo` 对支付尝试及路由只认精确归属的预付单，对回调只认同支付单且同业务号的标准来源预付单；业务事件另要求原样 `PAYMENT_` 前缀。来源必须为原样 `STANDARD_ORDER`，订单号必须精确匹配标准订单，缺失或虚拟来源不按同名猜测。
7. 事件流水载荷由服务端脱敏后返回；历史纯文本/非法 JSON 经安全脱敏可为原样 `[REDACTED]`，管理端将其作为安全占位符展示，不将任意非 JSON 视为合法事件载荷。

### 14.361 支付处理日志详情契约

1. `GET /api/payment-logs/{logNo}` 按唯一日志号返回单条支付处理日志详情；路径参数按单一路径段编码。
2. `logNo` 必填、去除首尾空白、最长 `64` 个字符且不得包含控制字符；非法请求沿用 `PAYMENT-1036`，不存在返回 `PAYMENT-1060 / 支付处理日志不存在`。
3. 响应包含 `logNo / paymentOrderId / orderNo / standardOrderNo / requestNo / sourceType / sourceNo / processStage / logLevel / logLevelType / message / source / createdAt`。
4. `standardOrderNo` 仅当日志业务订单号精确命中 `t_order.order_no`，且同一支付单的预付单来源为原样 `STANDARD_ORDER`、订单号与日志业务号精确一致时返回；预付单缺失或虚拟业务号碰巧同名不建立联查。
5. `sourceType + sourceNo` 仅作为来源事实标识返回；是否可跳支付流水由前端按已知来源做失败关闭，其中日志来源 `PAYMENT_EVENT` 可安全映射为流水来源 `BUSINESS_EVENT`，其他未知来源不猜测归属。
6. 接口只读，不更新日志、支付单、订单、请求、流水或资金事实，也不代表真实渠道、账务、清分、结算或对账一致。

### 14.362 支付事件详情契约

1. `GET /api/payment-events/{eventNo}` 按唯一事件号返回单条支付出站事件详情；路径参数按单一路径段编码。
2. `eventNo` 必填、去除首尾空白、最长 `64` 个字符且不得包含控制字符；非法请求沿用事件服务既有参数校验，事件不存在返回 `PAYMENT-1061 / 支付事件不存在`。
3. 响应包含 `eventNo / eventType / paymentOrderId / successFactIssue / bizNo / standardOrderNo / eventTopic / downstreamSystem / publishStatus / publishStatusType / paymentSuccessHttpStage / retryCount / lastPublishedAt / nextRetryAt / lastRepublishOperator / lastRepublishedAt / eventPayload / createdAt`；异常码与列表及导出一致，正常或无匹配支付单为 `null`。
4. `standardOrderNo` 仅当事件类型以原样 `PAYMENT_` 开头、业务单号精确命中 `t_order.order_no`，且同一支付单的预付单来源为原样 `STANDARD_ORDER`、订单号与事件业务单号精确一致时返回；接口不把虚拟业务号、退款尝试号或外部单号伪装成标准订单。预付单缺失或来源不明时保持为空，不按碰巧同名推断。
5. `eventPayload` 由服务层输出脱敏副本，不改写 Mapper 原始 DTO 或数据库事实；事件详情接口只读，不执行重发、不更新发布状态。
6. 接口返回的是 payment-core 出站事件事实，不证明下游系统真实消费、账务入账、清分结算、资金一致或生产放行。
7. 管理端必须把显式 `standardOrderNo: null` 当作合法事件详情继续展示，不能要求非空；省略字段、空白字符串或其他非法值仍按详情校验失败。只在标准订单归属已确认且当前详情快照有效时启用订单联查，空归属不得反向断言业务号不存在于标准订单表。
8. 服务端对历史纯文本或解析失败的事件载荷返回精确脱敏占位符 `[REDACTED]`；管理端列表与详情接受此固定占位符或有效 JSON 对象/数组，拒绝其他非 JSON 文本，不因安全脱敏而隐藏整条退款事件。

### 14.363 支付路由执行详情契约

1. `GET /api/payment-routes/{routeNo}` 按唯一路由记录号返回单条路由执行详情；路径参数按单一路径段编码。
2. `routeNo` 必填、去除首尾空白、最长 `64` 个字符且不得包含控制字符；非法请求沿用 `PAYMENT-1036`，不存在返回 `PAYMENT-1062 / 支付路由记录不存在`。
3. 响应包含路由记录号、支付请求号、支付单号、所属支付单成功事实异常码 `successFactIssue`、业务订单号、预付单号、客户、金额、支付方式、渠道、路由规则、路由结果、配置快照、终端、IP、幂等键、请求/响应报文和创建时间；异常码与列表及 CSV 同源，正常或无匹配支付单为 `null`。
4. 支付请求上下文只通过 `route.attempt_no = attempt.attempt_no` 精确关联；历史空尝试号路由保持请求上下文为空，不按支付单最近请求推断。
5. 客户名称、IP、幂等键和请求/响应报文由服务层输出脱敏副本，不改写 Mapper 原始 DTO 或数据库事实；接口只读，不代表真实渠道、资金或生产放行。

### 14.364 支付交易异常详情契约

1. `GET /api/payment-issues/{issueNo}` 按唯一异常编号返回单条支付交易异常详情；路径参数按单一路径段编码。
2. `issueNo` 必填、去除首尾空白、最长 `128` 个字符且不得包含控制字符；非法请求沿用 `PAYMENT-1036`，不存在返回 `PAYMENT-1063 / 支付交易异常不存在`。
3. 响应包含异常事实、责任组、处理状态、SLA、升级建议、告警派发摘要、供应商投递摘要和最近处理动作；推荐入口由服务端 `recommendedRoute` 返回。
4. 详情与列表复用同一异常聚合与推荐路由口径，避免同一异常在列表和详情中出现不同归口或不同推荐入口。
5. 客户名称由服务层输出脱敏副本，不改写 Mapper 原始 DTO 或数据库事实；接口只读，不确认回执、不执行批量处理、不重发事件、不修改支付或资金事实。

### 14.365 管理端可信支付成功指标契约

1. `GET /api/payment-metrics/summary` 与 `GET /api/payment-monitor/overview` 的摘要、最近七日趋势、渠道指标及渠道导出，只有支付单 `status=SUCCESS`、`success_at` 非空且不存在 `SUCCESS_FACT_MISMATCH` 时才计入成功笔数与金额；成功率分母仍是原全量支付单或渠道分组总量。缺成功时间、最近尝试缺失或失败、状态类型/渠道事实缺失、时间逆序均不得伪装成可信成功。
2. 趋势成功事实仍按 `success_at` 归日，创建量仍按 `created_at` 归日；渠道指标仍仅包含有支付尝试的支付单，导出与总览共享成功判断和排序。非成功状态的待处理与关闭计数不变；事实异常记录保留在总量和异常告警中，不静默删除。
3. 成功率先按小数计算并四舍五入为两位百分数；`summary.alertCount` 仍与本次返回的实际告警类型数一致。该口径只表示 payment-core 本地成功事实的一致性，不证明渠道实际扣款、清分、入账、到账或生产数据库执行计划。

### 14.366 收款记录可信退款成功聚合契约

1. 收款记录列表、唯一详情和 CSV 复用 `RefundMapper` 退款终态事实门禁：`SUCCESS` 且 `success_at` 非空、退款单与最近尝试状态样式正确、渠道受理号和时间线完整、最新退款尝试为同一成功终态且完成时间在申请、渠道受理与终态区间内，才计入 `refundCount/refundedAmount`。同秒毫秒逆序、成功时间缺失、无尝试或状态类型不符仍可通过退款单异常筛选和监控告警核查，不应以原始 `SUCCESS` 伪装成已退资金。
2. 非成功记录、支付原金额与状态、支付成功事实诊断以及筛选排序不因退款聚合改变。该金额仅是 payment-core 同源可信成功退款状态，不证明渠道实际到账、独立退款中心、账务或资金对账完成；目标 MySQL 8 执行计划和真实历史异常需另行验证。

### 14.367 收款记录待核查成功退款下钻契约

1. 收款记录列表、唯一详情和 CSV 新增 `unverifiedRefundCount`，表示同一支付单下原始 `SUCCESS` 但未满足退款终态事实门禁的退款单数量；无此类退款返回整数 `0`。可信 `refundCount/refundedAmount` 保持不变，非成功退款不计入待核查成功数；CSV 只在原末列后追加“待核查成功退款笔数”，不改变旧列顺序。
2. 管理端仅接受非负整数，缺失或非法值拒绝整条记录。正数在收款列表、快照与唯一详情显式提示“未计入已退金额”，可只读下钻 `/refunds?paymentOrderId={当前支付单}&refundStatus=UNVERIFIED_SUCCESS`；真实管理端路由必须注册供 `refundsRoute` 使用的 `refunds` 名称，刷新失败或旧快照禁止下钻。该筛选覆盖成功时间孤立缺失及其他终态事实异常，避免统计剔除后无法定位；专门的 `TERMINAL_FACT_MISMATCH` 告警筛选保持独立。
3. 该数是待人工核查的本地状态事实，不是额外已退资金，也不证明实际到账；目标 MySQL 8、生产权限、历史数据与独立退款中心跨源归属仍需单独验收。

### 14.368 退款申请支付成功事实准入

1. `POST /api/refunds/apply` 创建新退款时，在原支付单行锁内读取数据库原精度成功事实：支付状态与样式须为成功，金额为正，渠道编码与交易号非空，成功时间不早于创建时间；最近一次支付尝试须为成功终态，完成时间非空且位于支付单创建和成功时间之间。无尝试、成功时间缺失或同秒毫秒逆序均不得只凭 `status=SUCCESS` 进入退款额度计算和插入。
2. 不完整成功事实失败闭合，不新增退款单或操作日志。相同幂等键已有申请时，仍先核对申请内容并返回原申请，不把历史已创建退款重新当作新申请；该行为不代表异常支付事实已修复。
3. 本门禁只核对 payment-core 同源数据库事实，不证明渠道真实扣款或独立退款中心状态。目标 MySQL 8、历史异常记录和真实资金需另行验收。

### 14.369 退款失败重试的原支付成功事实准入

1. 管理端手工重试和任务中心失败退款重试，在锁定失败退款单后锁定原支付单；原支付单须满足 14.368 所述数据库原精度成功事实，方可核对剩余额度并将退款单重新置为处理中、创建尝试及下发事件。异常支付事实失败闭合，不应产生新尝试、操作日志或渠道下发事件。
2. 任务中心重试仍额外要求原退款失败终态及最近退款尝试的时间线一致；手工重试保留既有失败状态与额度约束。此门禁不证明实际扣款或资金到账，独立退款中心的跨源路径另行验收。

### 14.370 退款审批下发前的原支付事实复核

1. 退款审批将 `REVIEWING` 更新为 `PROCESSING` 后、创建退款尝试和渠道下发事件前，须在同一事务中锁定原支付单并复核 14.368 的原精度成功事实。事实异常或原支付单缺失时整笔审批回滚：退款仍待审核，审批日志、尝试和下发事件均不提交。
2. 审批先获取退款行锁，再获取支付行锁，沿用失败重试的锁顺序；申请新退款先锁支付行，再插入新退款单，不锁定既有退款行。本地 H2 事务回滚验证不代表目标 MySQL 8 锁竞争、渠道资金或独立退款中心路径已验收。

### 14.371 退款终态最新尝试的渠道归属

1. `SUCCESS/FAIL` 退款终态异常筛选、计数、导出和收款记录可信已退聚合，不仅检查最近尝试的状态、样式与原精度完成时间，还要求该尝试的渠道编码、渠道退款号与退款单已受理渠道及退款号逐项一致。最近尝试缺渠道归属或指向其他渠道号时，应进入终态事实待核查集合；原始 `SUCCESS` 不计入已退笔数和金额，计入待核查成功退款笔数。
2. 任务中心失败退款批量候选使用同一渠道归属约束；候选生成后执行时，锁定退款单并复核最近尝试的渠道编码和退款号，再进入状态重提。手工重试仍按其人工操作契约，不将批量候选约束强加给手工入口。
3. 该门禁只核对 payment-core 同源历史记录的一致性，不验证渠道退款真实到账；重复回调及主动查单的终态幂等读取按后续 14.372 另行核对，目标 MySQL 8 与真实异常数据仍未验收。

### 14.372 回调与主动查单的退款渠道归属复核

1. 锁定回调快照及主动查单快照同时投影退款单与最新尝试各自的渠道编码、渠道退款号。已受理尝试在处理回调或主动查单之前须与退款单的两项归属事实一致；错归属按内部终态事实不一致失败闭合，不改写退款状态、尝试或操作日志，也不得向渠道发起主动查询。
2. 终态重复回调、终态主动查单与终态详情读取复用完整终态事实校验，拒绝退款单与尝试渠道归属不一致的幂等成功假象；合法一致记录仍保持原幂等行为。未知受理且尝试尚无渠道退款号的恢复路径继续按既有 `UNKNOWN` 事件及条件更新契约处理，不把尚未绑定的号视为已受理终态。
3. 该复核只证明 payment-core 数据库内的一致性，不证明真实渠道资金、独立退款中心状态或目标 MySQL 8 的事务/锁行为。

### 14.373 退款终态最新尝试的状态样式门禁

1. `SUCCESS/FAIL` 退款终态异常筛选、计数、导出及收款记录可信已退聚合要求退款单样式分别为 `success/danger`，最新尝试状态与退款单相同且尝试样式与退款单样式相同。最新尝试样式为空或不符时，原始 `SUCCESS` 不计入可信已退金额，计入待核查成功退款笔数，并可在终态事实异常列表定位。
2. 任务中心失败退款批量候选仅选择最新尝试状态为 `FAIL` 且样式为 `danger` 的完整终态；任务执行在锁定快照中二次复核，不将候选查询作为唯一准入。手工重试保留人工操作契约。
3. 终态详情、重复回调及终态主动查单同时验证退款单与最新尝试的状态样式分别符合 `SUCCESS/success` 或 `FAIL/danger`；异常事实不能按幂等成功返回，也不调用外部渠道修复。该门禁不证明真实渠道资金、目标 MySQL 8 或独立退款中心事实。

### 14.374 退款成功尝试的原精度时间线

1. `SUCCESS` 退款最新尝试的完成时间不得晚于退款单 `success_at`，即使两项时间落在同一秒内也按数据库原始毫秒精度比较。违反时，终态详情、重复回调及主动查单拒绝将其认定为可信终态；列表的终态事实异常筛选应可定位，收款记录不计入可信已退金额。
2. 本规则仅约束 payment-core 同源记录，不证明渠道实际退款时间或资金到账。

### 14.375 退款终态跨入口统计一致性

1. 日终按终态日期（缺失时按申请日期）统计终态事实异常；监控汇总与 P1 异常告警、任务中心失败退款可重试与阻断计数复用退款列表的终态事实谓词。最近尝试的状态样式、渠道编码与退款号及原精度时间线不一致时，不得在其中一个入口被判异常、另一个入口仍视为可信。
2. 任务中心 `FAIL` 原始总数保持原状；可信失败终态计入可重试，不可信失败终态计入阻断。实际批量候选与锁定执行仍须各自复核，汇总数不能代替操作准入。H2 同源测试不构成目标 MySQL 8 或真实渠道资金验收。

### 14.376 成功时间缺失与终态异常的分类边界

1. `SUCCESS + success_at IS NULL` 固定进入 `SUCCESS_TIME_MISSING`；若退款单渠道归属、状态样式、申请/受理/终态时间及最近尝试事实均完整，**仅**缺成功时间时，不得再计入 `TERMINAL_FACT_MISMATCH`。日终、监控告警、列表与导出遵循同一分类，避免对同一孤立缺失产生两类终态告警。
2. 同时存在最近尝试错渠道、错状态样式、缺完成时间或其他独立终态异常时，仍同时进入两个异常集合。分类排重不意味着认可其成功事实：收款记录不计入可信已退，终态详情、重复回调及主动查单继续拒绝缺成功时间的异常终态。
3. 该分类只依据 payment-core 同源历史记录；目标 MySQL 8、真实渠道结果、独立退款中心及生产异常数据仍需单独验收。

### 14.377 待核查成功退款的收款记录下钻

1. 退款列表、计数与 CSV 支持虚拟筛选 `UNVERIFIED_SUCCESS`：原始状态为 `SUCCESS`，且成功时间缺失或不满足退款终态事实门禁。此集合与收款记录 `unverifiedRefundCount` 的成功退款资格口径一致，不包含失败、处理中和事实完整的成功退款。
2. 收款记录列表快照及唯一详情的“查看待核查退款”以当前支付单号和该筛选下钻；退款管理页保留 `SUCCESS_TIME_MISSING` 与 `TERMINAL_FACT_MISMATCH` 两个诊断筛选，不因下钻集合取并而重新合并告警。管理端仍拒绝旧快照或刷新失败时导航。
3. 三种诊断筛选允许展示结构完整但终态时间、样式或渠道事实异常的列表行，仍严格校验支付/退款归属、业务链标识和基本字段形状；金额异常的只读展示按 14.379 执行。诊断页仅供只读核查和导出，禁用申请、审批、主动查单、失败重试及可能被终态校验拒绝的详情跳转。普通退款列表保持既有事实一致性校验，不将诊断行当作可操作快照。
4. 这是本地同源记录的查询辅助，不修复异常终态、不证明渠道实际退款、独立退款中心归属或资金到账；目标 MySQL 8 与真实历史异常数据需要另行验收。

### 14.378 退款终态非正金额事实隔离

1. 退款终态 `SUCCESS/FAIL` 的金额必须为正。历史数据中的空值、零额或负额均属于 `TERMINAL_FACT_MISMATCH`；原始 `SUCCESS` 即使时间、渠道与最新尝试齐全，也不得计入收款记录可信已退笔数与金额，而应进入待核查成功退款集合。
2. 退款列表、计数、CSV、日终终态异常、监控告警与任务中心失败退款候选复用同一门禁；终态详情既有金额校验不放松。本门禁仅阻止非正金额被认作可信终态，不等同于验证退款金额未超过原支付金额、累计可退余额、目标 MySQL 8 或渠道资金事实。

### 14.379 异常退款金额的管理端只读诊断

1. `TERMINAL_FACT_MISMATCH` 与 `UNVERIFIED_SUCCESS` 的列表行可只读展示后端格式化的零额、负额及空金额；缺失金额显示为“—”。普通退款列表及所有退款动作仍要求合法正金额，不以诊断展示替代终态详情或渠道核验。
2. 诊断视图不对本页异常退款金额求和，以“—”替代原卡片汇总，避免把负额或缺额静默当作零；行快照、CSV 下载保留，详情跳转和写操作仍禁用。该能力只用于排查本地同源异常事实，不构成生产修复或真实资金验证。

### 14.380 退款终态原支付金额上限

1. 共用退款终态事实门禁要求能按退款单的原支付单号找到正金额支付单，且单笔退款金额不超过该支付金额。超额退款、原支付单缺失或原支付金额非正的 `SUCCESS/FAIL` 均计入 `TERMINAL_FACT_MISMATCH`；原始成功退款不进入收款记录可信已退聚合，仍进入待核查下钻。
2. 退款列表/计数/CSV、日终异常、监控告警和任务中心失败退款候选共用上述门禁，与终态详情的原支付金额校验一致；不修改原始状态及日终原始状态金额统计。本规则不验证同一支付单多笔退款的累计金额、不代表目标 MySQL 8 性能验收或渠道资金核验。

### 14.381 多笔成功退款的累计上限诊断

1. 对同一原支付单，先筛出成功时间存在且单笔终态事实完整的 `SUCCESS` 退款；若这组候选的退款金额合计超过原支付金额，整组成功退款均作为 `TERMINAL_FACT_MISMATCH` 与 `UNVERIFIED_SUCCESS` 待核查，不能任选前几笔计入收款记录可信已退。这保证本地可信成功退款聚合不超过原支付金额，也避免在无法判定哪一笔历史退款多出时作任意归因。
2. 单笔事实异常或缺成功时间的退款本已待核查，不参与累计候选求和；`FAIL` 不因其他成功退款的累计异常而被错误归为失败终态事实异常。退款申请、重试入口已有的活动退款余额准入仍独立生效。退款列表/计数/CSV、收款记录与日终/监控共用累计诊断，原始成功状态和原始状态金额统计不改写。
3. 该规则只检查 payment-core 同源历史数据；目标 MySQL 8 的相关子查询执行计划、大数据量性能、真实渠道退款和跨系统资金事实均未验收。

### 14.382 退款终态读取与累计事实门禁同源

1. 退款终态详情、终态主动查单和同状态重复回调读取当前退款快照时，同步计算 14.381 的共享终态事实异常结果；累计超额时拒绝把已有 `SUCCESS` 当成可信幂等终态，空缺事实标志也拒绝。该检查仅作用于已有终态的读取/重复确认，不在首次渠道成功回调前阻止持久化真实渠道结果。
2. 只读快照与锁定快照使用同一共享 SQL，避免列表待核查而 Java 详情放行。该门禁不自动选择哪笔历史成功退款是超额部分，也不调用渠道修复、改写原始状态或证明实际退款到账。

### 14.383 活动退款异常金额的额度准入

1. 新退款申请和失败退款手工/任务重试在原支付单锁定及支付成功事实校验后、计算剩余可退额度前，统计该支付单 `REVIEWING/PROCESSING/SUCCESS` 退款中的空值、零额和负额。任一异常即拒绝新申请或重试，不允许历史负额抵消其他活动退款占额。`FAIL` 不占活动额度，其异常金额由重试单本身的正金额校验阻断。
2. 既有幂等申请在额度检查之前按原始申请内容返回，不重新创建退款；正常活动退款仍按原正金额总额和原支付金额比较。该门禁不自动更正历史金额，也不替代渠道资金与目标 MySQL 8 验证。

### 14.384 累计超额成功退款的管理端只读下钻验证

1. 隔离 H2/demo 可用两笔单笔有效但合计超原支付的成功退款验证：收款列表和详情的可信已退保持零、待核查为两笔；`UNVERIFIED_SUCCESS` 与 `TERMINAL_FACT_MISMATCH` 均能展示两笔，诊断页金额不汇总、快照可读且详情与操作禁用。
2. 本项的浏览器验证只通过同源 GET 读取演示数据，不发送退款或渠道动作；目标 MySQL 8、真实历史数据、正式渠道和独立退款中心跨源进度仍需独立验收。

### 14.385 非正退款金额诊断的浏览器核查

1. 隔离 H2/demo 中同一原支付单的 `0.00`、`-5.00` 两笔原始成功退款，均进入终态事实异常与待核查成功退款集合。管理端收款列表快照及唯一详情可只读下钻；退款行准确展示 `¥0.00`、`¥-5.00`，本页金额卡片显示“—”而不执行误导性求和，行快照可读、详情及动作禁用。
2. 演示仅通过同源 GET 核查历史异常结构，不会创建退款或访问真实渠道；目标 MySQL 8、生产数据、实际资金与独立退款中心归属另行验收。

### 14.386 成功支付详情的最近尝试渠道归属

1. 成功支付单的详情、收银台状态、重复提交、终态查单和重复成功回调沿用成功事实门禁；最近一次成功尝试的渠道编码必须等于支付单渠道编码。归属不符时拒绝作为可信终态返回，不改写原始支付记录。
2. 本项仅收紧单据读取事实；支付单列表、日终与监控的共享异常筛选及退款申请的锁定原支付事实仍需统一渠道归属判断。目标 MySQL 8、历史异常数据和真实渠道资金另行验收。

### 14.387 成功支付跨投影渠道归属一致性

1. 支付单列表/计数/CSV 与相关运营列表复用的 `SUCCESS_FACT_MISMATCH` 谓词，要求最近一次成功尝试的渠道等于支付单渠道；错渠道成功不计入监控可信成功金额与笔数，进入异常告警。日终按成功日统计的成功事实异常独立 SQL 同样核对渠道。
2. payment-core 自身退款申请、审批与失败重试在锁定原支付单后，核对最近成功尝试的渠道归属，错渠道不允许创建或继续下发退款。原始事实仍可供诊断，目标 MySQL 8 执行计划、真实渠道和独立退款中心跨源校验需要另行验收。

### 14.388 渠道成功收口前的尝试归属门禁

1. 支付成功回调或主动查单在调用渠道查询、写支付成功状态前校验最近支付尝试渠道与支付单渠道一致；重取订单锁后再次复核。错误归属返回 `PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING`，不派发成功事件、不结算订单或账单。内部归属标志不出现在详情 JSON。
2. 数据库成功迁移、处理中回调和最新尝试成功终态更新分别加渠道一致性条件。缺有效提交尝试的历史已过期关闭支付单不能仅凭后到成功消息改成成功；需另行核实历史渠道事实。目标 MySQL 8 的锁竞争与执行计划、正式渠道回调真实性尚需验收。

### 14.389 历史非正支付金额的成功事实诊断

1. 成功支付金额为空、零或负值时，共享成功事实谓词标为 `SUCCESS_FACT_MISMATCH`；支付单列表/计数/CSV 与监控可信成功金额和笔数均排除它，日终成功事实异常按原成功日计数。原始单据状态不改，运营可从列表快照查看金额，成功详情仍拒绝把它作为可信支付结果。
2. 管理端只对带成功事实异常标记的支付单行允许格式化 `¥0.00`、`¥-5.00` 或明确空金额，且禁用详情/查单写操作；本页存在此类异常金额时不汇总“实付金额”。普通行的正金额门禁保持不变。隔离 H2/demo 的真实浏览器只读点击已覆盖两种非正金额；目标 MySQL 8 执行计划、生产历史与正式渠道资金另行验收。

### 14.390 历史负额不得抵消订单超额与日终差异

1. 订单超额异常的毛成功金额及支付单数只累计原始 `SUCCESS` 且金额严格大于零的单据；不以完整成功事实门禁替代原始状态，否则可能遗漏真实资金待核对风险。同订单负额、零额或空金额的历史单不减少正金额合计，也不成为该异常关联的最后一单。
2. 日终按支付单主键顺序判断累计超额时只加正金额成功单。差异单量仍单独检查通知和支付事件：非正金额单缺事实可计入笔数，但差异金额只加正金额，不以负数抵消其他异常金额。原始 `SUCCESS`、成功事实异常筛选和日终原始成功金额口径均不改；本项只修正超额预警与差异金额。目标 MySQL 8 与正式资金另行验收。

### 14.391 历史非正金额不得冲抵渠道日限额

1. 路由候选的当日已受理金额只累加该渠道当日创建、原始状态为 `WAIT_CALLBACK` 或 `SUCCESS` 且支付金额严格大于零的支付单。负额待回调或历史成功单、零额及空金额不占容量，也不能抵消其他正金额支付；已关闭单及其他渠道仍不计入。申请金额与该正金额累计之和超限时按既有规则拒绝强制渠道或选择兜底渠道。
2. 该口径是内部路由容量预筛，不以支付成功事实完整性代替原始受理状态；不改变历史单据及原路由响应结构，也不证明多实例并发配额预留或真实渠道余额。目标 MySQL 8、正式渠道容量与资金事实仍需独立验收。

### 14.392 已提交支付复用不重复占用渠道容量

1. 支付单已进入 `WAIT_CALLBACK` 或 `SUCCESS`，且详情有已绑定渠道时，重复提交若请求渠道为空或与已绑定渠道一致，沿用原支付渠道生成幂等键并复用当前支付状态，不再按当前日限额、路由权重或渠道健康重新选路。此路径不新建尝试或再次提交渠道；成功态仍须通过成功事实校验。
2. 复用前仍执行来源应用、商户、支付方式、原支付渠道、访问令牌和严格模式自检等现有控制策略校验，已存在幂等键仍核验预付单/支付单归属；显式请求另一渠道、未绑定渠道和其他状态仍按原路径解析。此修复不改变首次提交的容量预筛，也不提供原子配额预留或可信身份授权。

### 14.393 已受理支付的缺省字段重试沿用原事实

1. 14.392 的只读复用路径若调用方未提供支付方式或渠道，且当前支付详情已有对应的已绑定值，先从该支付事实补齐缺省字段，再考虑运营配置快照；不因首次提交后默认配置下线、变更而拒绝读取已受理状态或把幂等键换成新渠道。已提供的字段不被覆盖；仍缺支付方式等必要事实时继续按现有运营配置及必填校验处理。
2. 来源应用控制策略、商户、令牌、幂等归属和成功事实核查不因复用缺省字段而跳过。此规则不适用于首次提交或显式异渠道请求，不对渠道二次下发，也不证明生产配置或正式身份治理已完成。

### 14.394 渠道成功收口时的订单来源与欠款复验

1. 成功回调与主动查单在同订单锁后读取支付详情的内部预付来源标记。`STANDARD_ORDER` 必须仍有完整标准订单，当前应收为正、已付非负、客户与支付单一致，且当前剩余应付恰等于本次支付金额；`VIRTUAL` 必须仍无标准订单金额来源。来源为 `UNKNOWN`、缺失或与当前订单类型不一致均不能结清。
2. 来源/欠款不一致时抛出可触发事务回滚的内部异常，不把业务异常作为查单收口门禁：查单入口原本允许部分业务异常保留查询失败日志，不能因此提交已经写入的支付成功、尝试、预付或账单。拒绝路径不产生 `PAYMENT_SUCCESS` 事件；上游渠道可能已经真实受理，需按支付单与渠道流水核查，不能自动重发付款。
3. 虚拟来源且无标准订单时仍按本次支付金额结清账单；标准来源按订单应收更新订单及账单。此锁只协调遵守同一约定的本地写入，不等于跨系统原子快照、生产 MySQL 8 事务隔离或真实资金到账验收。

### 14.395 已成功支付的来源类型与退款准入口径

1. `SUCCESS` 支付详情及重复提交、重复成功回调、终态查单不仅校验预付金额绑定、最近成功尝试和时间线，还要求唯一预付的创建来源为 `STANDARD_ORDER` 或 `VIRTUAL`，且与当前订单行存在性一致。未知来源、标准来源订单消失、虚拟来源后来出现同号订单均拒绝作为可信成功事实返回；原始支付状态不自动改写。
2. 支付单运营列表的 `SUCCESS_FACT_MISMATCH` 与可信成功统计复用同一来源约束；payment-core 退款申请锁定原支付时也核对唯一预付的订单号、原精度正金额和来源存在性，不允许异常成功单继续申请或重试退款。内部 `source_kind` 不对外输出，异常单须人工核对原支付及渠道流水，不可凭当前订单行自动补标存量 `UNKNOWN`。
3. 目标 MySQL 8 执行计划、历史来源证据、真实资金与跨系统退款来源仍须独立验收。

### 14.396 日终成功事实异常的来源绑定口径

1. 日终按支付成功日统计 `paymentSuccessFactMismatchCount` 时，除支付单终态、渠道尝试和时间线外，还要求唯一预付与支付单的订单号、原精度正金额一致，且创建来源 `STANDARD_ORDER/VIRTUAL` 与当前 `t_order` 行存在性一致。未知来源、标准来源消失、虚拟来源撞号与重复预付均计入成功事实异常，不误报为可信成功。
2. 日终原始成功笔数/金额及渠道通知、内部事件差异仍保留各自的原始资金/事件口径，不因来源异常从原始统计中抹去；已持久化的历史日终批次不会自动回填，需在可信历史核验后按受控流程决定重跑。目标 MySQL 8 执行计划和生产历史另行验收。

### 14.397 成功结清的订单和账单快照条件更新

1. 成功回调与主动查单在订单协调锁内复验标准订单当前应收、已付、客户和剩余应付；回写 `t_order` 时还要求应收仍等于结清目标、已付和客户仍等于复验快照。并发或旁路写入造成的零行更新抛内部异常，回滚支付单、尝试、预付及后续账单/成功事件，不把渠道已受理结果误写成内部结清。
2. 同一事务更新 `t_bill` 时，要求账单号归属的订单号、客户、应收和此前已付金额与结清快照一致；标准订单此前已付以订单复验值为准，虚拟预付新账单此前已付为零。账单零行更新同样回滚前面的支付和订单写入。渠道可能已经成功但内部拒绝结清时需人工核查，不能自动再次扣款。
3. 条件更新只避免本事务用旧快照覆盖并发新值；它不提供外部系统全局原子性。目标 MySQL 8 隔离/锁竞争、跨库订单写入、可信历史账单和真实渠道资金须独立验收。

### 14.398 预付来源精确值与异常告警处置

1. `source_kind` 仅接受原值恰为 `STANDARD_ORDER` 或 `VIRTUAL`，大小写变化、尾部空字节和其他非法值不得在大小写不敏感的目标库中通过可信成功、日终或退款原支付校验；只读来源审计把它们归为非法，不据当前订单行推断原创建来源。
2. 监控 P1 `PAYMENT_SUCCESS_FACT_MISMATCH` 复用成功事实谓词，需覆盖预付来源不一致；仪表盘成功笔数和金额排除异常单。告警处置指向支付单号、预付绑定、订单来源、最新尝试、渠道流水与操作日志，不提示自动补标或自动退款。
3. 以上有 H2 MySQL 模式不区分大小写列回归，目标 MySQL 8 的 collation、字节比较和执行计划，以及可信历史凭证和正式资金仍需独立验收。

### 14.399 支付监控异常来源的前后端处置提示

1. `PAYMENT_SUCCESS_FACT_MISMATCH` 的 P1 告警说明及监控页本地辅助建议应同时覆盖预付绑定、订单来源、支付尝试、渠道流水与操作日志，不能只提示核对最新尝试的状态/完成时间。
2. 点击告警仍按 `/payments?status=SUCCESS_FACT_MISMATCH` 进入只读诊断列表；本地辅助建议不得指引根据当前订单行自动补标历史来源、重发付款或擅自作退款决定。真实来源和资金事实经核查与审批后再处置。

### 14.400 支付监控 P1 异常事实只读浏览器路径

1. 在明确的 `payment-core-h2-demo` 演示身份与隔离内存库下，管理端监控页应显示 `PAYMENT_SUCCESS_FACT_MISMATCH` 的 P1 文案及本地辅助建议，选中告警后两处提示同时可见。
2. 从“立即排查”跳转到 `/payments?status=SUCCESS_FACT_MISMATCH`，同源 GET 加载异常支付行；浏览器验证不得调用写接口、跨源 API、自动补标或资金操作。此模拟证据不代替真实历史来源和正式告警处置验收。

### 14.401 小写历史来源的模拟诊断边界

1. 仅在显式隔离 H2/demo 的 `IGNORECASE=TRUE` 会话和附加初始化脚本中，将原本可信的标准订单成功预付 `source_kind` 改为小写 `standard_order`，保留原始支付、尝试、订单及渠道流水；不得修改常规演示种子、生产库或存量来源。
2. 已成功支付应进入 `SUCCESS_FACT_MISMATCH`，从可信成功笔数与金额中排除，并使监控 P1 计数增加；浏览器告警下钻需显示该支付的“成功事实异常”标识。该标签只供只读排查，不证明其历史创建来源可以按当前订单行自动补标。

### 14.402 无可信支付详情时的用户结果页

1. App/H5/PC 共用结果页在详情尚未加载时显示“正在加载支付结果”，不能把未知状态当作“支付处理中”。详情读取失败且没有可信支付快照时显示“支付结果待核查”“待核查”，提示保留支付单号、不要重复付款并联系运营核查渠道与订单事实；仅保留重试加载动作，不展示付款已成功或可主动查单的错误暗示。
2. 对来源不一致的历史 `SUCCESS` 单，服务端详情失败闭合，收银台会话进入“成功事实异常”；用户端不得依据原始成功字段绕过详情门禁。这是未知状态的谨慎呈现，不推断渠道资金实际成功或失败。

### 14.403 风控复核后沿原请求继续支付

1. `GET /api/payments/cashier/{prepayOrderNo}` 在支付单为 `RISK_REVIEW` 且支付事实可读取时，新增可空 `reviewPaymentMethod`、`reviewChannelCode` 与 `channelTransactionNo`，来自支付单的方式、路由渠道及已有渠道交易号；非复核态不提供。历史缺失绑定字段时前端不得猜测原请求或允许继续提交。
2. App/H5/PC 收银台仅在方式仍可用、已选方式等于复核绑定方式且渠道编码只存在大小写差异、无渠道流水和支付尝试、收银台未失效并持有访问令牌时展示沿原支付继续提交；复核态锁定支付方式切换。小程序仅在固定微信方式和渠道与绑定事实一致时允许重试。后端复核决策、上下文指纹和支付状态机仍是放行依据，前端按钮不是审核通过凭据；待审点击仍返回 `RISK_REVIEW`，不发起渠道支付。
3. 风控新复核摘要绑定原来源应用 `sourceAppId` 及支付方式、渠道、商户、终端、设备、手机号等上下文；相同支付单换有效来源应用及令牌也不能借用待审或已批准结果。风控仅接受 `v2:` + 无填充 Base64URL SHA-256（46 字符）复核摘要作为可批准的当前版本；历史空摘要或 64 字符十六进制旧摘要均不能批准或放行，待审单仍可人工拒绝。存量须盘点并人工处置，不从本次请求自动补摘要；接口不返回摘要原文。
4. 首次准入及沿原请求重试时，风控均优先处理当时启用的黑名单和策略硬处置（`INTERCEPT/REJECT`）；软名单、大额控制项或限额复核不能遮蔽硬规则，先前待审或批准也不覆盖后来启用的硬规则。旧复核单不自动改成拒绝，新的拦截/拒绝以风控事件追踪；前端重试入口只表示可尝试，不能承诺渠道提交成功。
5. 风控新复核单按原业务单号的精确 SHA-256 二进制键唯一；并发提交撞键后只复用同上下文待审单，状态或请求不符失败闭合，不以客户端重试产生多个待办。历史空键不自动回填；需先经受控迁移创建唯一索引再部署新版本，目标 MySQL 8 与多实例并发仍待现场验收。
6. 风控新风险事件对业务单、策略、处置结果和有效来源系统组成精确摘要并按数据库唯一键原子去重；同组合可复用原事件号，跨来源不能互相借用。历史事件空键不自动补标；正式库还需独立执行事件键迁移与真实并发验收。
3. 小程序收到 `RISK_REVIEW` 提交结果时进入结果页，不调用 `wx.requestPayment`；审核通过后返回收银台刷新原预付单，再按原上下文重试。缺少绑定、变更方式/渠道或已产生渠道尝试时继续失败闭合。此契约不代替可信审核身份、目标 MySQL 8 和真实渠道资金验收。

### 14.404 显式支付渠道与支付方式路由边界

1. 路由规则目标及 fallback 仅在配置支付方式与当前请求一致时可用；若用户显式指定渠道，规则候选还必须与该渠道去空白、忽略大小写后的**完整编码**一致，不得静默替换为另一渠道或另一支付方式。不匹配规则继续检查后续规则或原显式渠道，无同方式可用候选时按原 `PAYMENT-1010`、`PAYMENT_REQUESTED_CHANNEL_INVALID` 或 `PAYMENT_ROUTE_UNSUPPORTED` 契约失败闭合。
2. `银行卡` 是现有 PC 收银台对 `银行转账` 配置方式的兼容别名，仍可将 `BANK_CARD` 映射到 `offline_bank`；该别名不允许支付宝/微信跨方式兜底。未显式指定渠道的同方式规则 fallback 与稳定加权路由保留。
3. 仓库演示规则中仍存在跨方式 fallback 配置，运行时不会选中；配置端现在拒绝新建、编辑、批准或回滚为跨方式规则，但不会自动改写存量规则，禁用仍可申请。目标 MySQL 8 存量排查、人工迁移方案与实际渠道适配仍需后续受控处理。

### 14.405 路由标识与健康聚合分离

1. 显式请求渠道与规则目标/兜底渠道按完整编码匹配，`WX_H5` 只对应 `wx_h5`，不会因同属微信渠道而代替 `wx_jsapi`；缺失目标不能冒用另一个微信/支付宝适配器。仅保留既有前端 `BANK_CARD → offline_bank` 明确别名。
2. 短窗查单健康仍可按 `wx / alipay / bank` 家族合并历史同类编码；这只影响健康统计，不用作路由身份、显式指定渠道或规则目标定位。未经目标 MySQL 8 与真实渠道验收，不将本地 H2 路由判断视为资金指令安全证明。

### 14.406 已受理支付复用拒绝显式异支付方式

`WAIT_CALLBACK` 或 `SUCCESS` 的支付单沿已绑定渠道只读复用时，调用方若显式提供与支付详情已绑定方式不同的支付方式，返回 `PAYMENT-1035`，不以原渠道返回当前状态；未提供方式时仍从原事实补齐，历史详情缺少绑定方式时维持原有必填与控制策略校验。该门禁不改变首次提交、显式异渠道处理或真实渠道结果。

### 14.407 已受理支付不以另一有效渠道复用原状态

`WAIT_CALLBACK` 或 `SUCCESS` 已绑定渠道的支付单，显式请求另一渠道仍按原路径完成路由及提交控制校验；若解析后的渠道与支付详情绑定渠道不同，即使该渠道当前可用，也返回 `PAYMENT-1035`，不把旧支付状态作为新渠道的受理结果。原渠道相同编码的大小写形式仍可只读复用；此规则不新建尝试，也不替代目标 MySQL 8、正式渠道与资金事实验收。

### 14.408 待回调支付单缺失绑定渠道不作受理复用

支付详情为 `WAIT_CALLBACK` 但渠道编码为空时，即使本次请求可以路由到某个可用渠道，提交复用也返回 `PAYMENT-1054`，不把缺失渠道归属的历史待回调状态当作本次已受理结果；不会新建尝试或重新下发。正常已绑定渠道的待回调单仍可按原方式复用。异常存量须与真实渠道流水人工核对，不通过客户端重试自动补造受理归属。

### 14.409 待回调复用要求末次尝试属于原渠道

`WAIT_CALLBACK` 即使已有渠道编码，重复提交在返回原状态前仍要求详情存在末次支付尝试状态，且 Mapper 投影的末次尝试渠道与支付单渠道按原长度及二进制值一致；缺失、错渠道或仅大小写别名不视为受理事实，返回 `PAYMENT-1054`，不二次下发渠道。正常已受理单沿用原幂等状态；历史无尝试或错归属单需人工核对真实流水，目标 MySQL 8 与实际资金仍待验收。

### 14.410 待回调与终态尝试冲突不复用

支付单仍为 `WAIT_CALLBACK`，而末次同渠道尝试状态已为 `成功 / 失败 / 支付失败` 或历史英文 `SUCCESS / FAIL / FAILED` 时，不再将旧待回调状态作为可复用的受理事实；重复提交和主动查单的受理事实门禁返回 `PAYMENT-1054`，不会因该异常状态重新提交渠道。正常 `处理中`、`待回调` 等未终结尝试仍按原流程处理。需人工核实终态尝试与支付状态为何冲突，不能从本地状态推定真实资金结果。

### 14.411 待回调与末次尝试完成时间冲突不复用

即使末次尝试状态文字仍为 `处理中` 或 `待回调`，只要支付详情投影出该尝试的非空完成时间，`WAIT_CALLBACK` 不再被视为可复用或可主动查单的开放受理事实；返回 `PAYMENT-1054`，不重新下发渠道。规范待回调尝试应保持完成时间为空。历史状态和完成时间冲突需先核查真实渠道流水，不能通过重试自行修复。

### 14.412 并发提交结果必须重新核对受理事实

支付提交期间若同一幂等键被另一请求抢先落库，或预付单由其他提交占用，返回竞争结果前须重新读取预付单和支付详情。最新状态为 `WAIT_CALLBACK` 时，要求支付单归属、已绑定渠道和支付方式与本次请求一致，且末次渠道提交尝试仍是有效的开放受理事实；缺失或矛盾分别返回 `PAYMENT-1054` 或 `PAYMENT-1035`，不重复下发渠道。最新状态为 `SUCCESS` 时还须通过已有的成功事实校验；其他非受理状态保留原有幂等/并发返回方式。响应以校验后的详情同步状态和渠道事实，不把竞争请求的异常状态当作本次受理结果。

### 14.413 成功事实诊断按原渠道编码精确匹配

成功事实诊断的末次尝试渠道与支付单渠道必须原长度相同且二进制编码一致。大小写或尾空格别名即使在宽松数据库排序规则下等值，也应列为 `SUCCESS_FACT_MISMATCH`，与支付详情的受理事实投影保持一致。支付列表/导出和监控共用诊断片段，日终使用同口径的独立 SQL；诊断仅识别异常，不自行修改支付或资金状态，异常历史需核对真实渠道流水。

### 14.414 成功尝试的创建与完成时间线

可信成功事实要求支付单创建时间不晚于末次支付尝试创建时间，尝试创建时间不晚于其完成时间，完成时间不晚于支付成功时间。支付详情验证投影、支付列表/导出与监控的诊断条件、日终成功事实统计均核对原始时间精度；不以格式化到秒的展示值推断顺序。历史时间倒置列为异常，不能仅修改本地时间来认定实际资金成功。

### 14.415 日终成功事实按精确支付单号关联

日终 `paymentSuccessFactMismatchCount` 的独立查询在校验预付单归属、唯一绑定数量与末次支付尝试时，均要求支付单号原长度相同且二进制完全一致。宽松排序规则下仅大小写或尾空格相同的预付单/尝试不得替代原单的成功事实，应计入异常；正常精确关联保持原统计，不自动修改历史单或资金记录。

### 14.416 渠道提交结果与快速成功回调的精确归属

渠道提交后若结果绑定未影响一行，可能是回调已抢先把支付单收口。此时回读支付详情必须要求已绑定渠道编码与本次解析渠道完全一致、已有支付方式与本次提交方式一致、非空渠道流水号与本次结果一致，再核对末次尝试归属与完整成功事实；不允许用大小写/空白别名借用另一受理事实。冲突返回 `PAYMENT_CHANNEL_SUBMIT_FAILED`（`PAYMENT-1048`），不把当前渠道结果补录到异归属支付尝试；渠道提交结果未知的恢复分支使用同一门禁。

### 14.417 首次路由后的并发赢家渠道必须精确一致

若本次提交从尚未受理的预付状态解析出路由渠道，但在占用预付单或幂等尝试前发现另一请求已抢先受理，返回竞争赢家的 `WAIT_CALLBACK`/`SUCCESS` 前须核对赢家绑定渠道与本次路由结果逐字符相同；仅大小写别名也返回 `PAYMENT_SUBMIT_REQUEST_INVALID`（`PAYMENT-1035`），不借用赢家状态且不再次下发渠道。14.407 的**已受理单只读复用**仍允许原渠道同编码的大小写形式；本规则只针对首次路由后的并发返回。

### 14.418 重复拉起待回调预付单的渠道受理事实

`prepay` 发现同订单活跃预付单且绑定支付单为 `WAIT_CALLBACK` 时，复用前须读取该支付单的末次渠道提交事实：支付单号精确一致、状态仍为待回调、渠道非空、末次尝试状态非空且非终态、完成时间为空，末次尝试渠道与支付单渠道须原长度及二进制精确一致。缺失或矛盾时返回 `PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING`（`PAYMENT-1054`），不创建第二笔支付单，也不把历史待回调状态当作可信受理；正常待回调单（含“结果待确认”展示）仍复用原预付单。该校验不改变已受理单提交接口的同渠道大小写只读复用契约。

### 14.419 收银台待回调展示的受理事实门禁

收银台读取预付单和轻量提交事实后，只要最终支付状态为 `WAIT_CALLBACK`，须与重复拉起预付单使用同一渠道受理事实门禁：提交事实存在、支付单号精确一致、状态仍为待回调、渠道非空、末次尝试渠道与支付单渠道精确一致，且尝试非终态、无完成时间。缺失或矛盾返回 `PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING`（`PAYMENT-1054`），不向用户展示缺乏依据的“待回调”；真实有效的待回调展示仍保留，未提交的 `PREPAY_CREATED` 和已验证成功事实沿各自规则处理。

通过 `WAIT_CALLBACK` 门禁后，收银台响应须投影已核验的原支付渠道 `channel`、`paymentMethod` 与 `channelTransactionNo`，与同一提交事实中的尝试状态一起供多端查单入口和原支付方式展示使用；已核验 `SUCCESS` 同样返回成功支付详情中的三个字段。未提交或风控复核阶段不把候选路由渠道冒充已受理渠道。用户端对待回调收银台响应若缺少非空渠道或支付方式应拒绝该快照，不仅禁用按钮；对 `SUCCESS` 还须同时具备非空渠道、原支付方式及渠道交易号，否则拒绝成功收银台快照，不能回退选中候选方式或显示缺少流水的成功状态。收银台首次主动查单前展示原渠道流水号，查单后以最新支付详情的流水号为准。

### 14.420 渠道归属投影未知时失败闭合

读取末次渠道尝试与支付单渠道一致性投影时，仅明确为 `true` 才可作为渠道受理事实；投影为 `false` 或缺失（`null`）均返回 `PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING`（`PAYMENT-1054`）。该校验沿用查单、回调、提交复用、预付单复用与收银台待回调展示的共享门禁；不能以尝试状态非空替代渠道归属证明。详情/轻量事实 Mapper 均投影原长度及二进制精确匹配结果，历史异常仍需人工核对渠道流水。

该投影现同时要求末次尝试 `payment_method` 与支付单 `payment_method` 非空白且原长度、二进制精确一致；仅渠道编码一致、方式为大小写别名、尾随空格或另一支付方式都不能证明同一笔受理，两侧同为纯空格也不放行。成功支付详情的原始事实时间线投影按相同方式绑定，避免把错绑方式的成功尝试当作可信成功。历史异常记录仍需人工核对真实渠道资金，不能通过客户端显示修正支付事实。

### 14.421 原子支付迁移排除中英双语终态尝试

服务层识别 `成功`、`支付成功`、`失败`、`支付失败`、`SUCCESS`、`FAIL`、`FAILED`（忽略首尾普通空格与英文大小写）为末次尝试终态。渠道提交适配结果携带这些终态时不作为待回调受理；适配结果进入受理前还须校验状态样式：中英成功终态只允许 `success`，中英失败终态只允许 `danger`（英文状态忽略大小写，样式规范化为小写），矛盾组合按 `PAYMENT_CHANNEL_SUBMIT_FAILED` 拒绝，不借样式推断支付成功。支付成功/待回调回调、历史关闭恢复、渠道结果绑定及结果未知恢复、末次尝试状态更新/成功收口的数据库原子条件均需按同一口径排除这些终态；读取与写入之间尝试被标记为终态时，不得以旧快照继续修改支付状态、渠道流水或尝试状态。尝试状态缺失或完成时间不为空沿现有门禁处理；其他未枚举状态的渠道语义仍需单独核对，历史异常需核对渠道资金事实。

晚到的渠道提交响应写入尝试时也受该原子门禁约束：`completed_at` 为空仅允许状态非空白且非上述终态的尝试更新响应载荷、尝试状态及样式；若尝试已是终态（即便历史数据缺完成时间）或状态为空白，更新零行，不覆盖原状态与载荷。已有完成时间的尝试保持既有兼容路径，仅补写响应载荷，不回退状态或样式；此写入资格不代表渠道资金事实已确认。

### 14.422 支付结果详情待回调须有受理事实

`GET /api/payments/{paymentOrderId}` 返回 `WAIT_CALLBACK` 前必须复用收银台及预付单的末次渠道受理事实门禁：请求支付单号精确一致、原渠道非空、末次尝试归属渠道明确为真，尝试状态非空且非终态、完成时间为空。事实缺失或矛盾返回 `PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING`（`PAYMENT-1054`），不向 App/H5/PC 结果页交付未经证实的“待回调”快照，也不继续加载路由/通知/事件详情；有效待回调详情仍正常脱敏返回。管理端可用列表与日志盘点异常，不得据此推断真实渠道资金已确认。

### 14.423 已关闭支付的有渠道尝试资格

`CLOSED` 且从未提交渠道的支付单继续按原路径只读返回，不发起渠道查单；历史已提交但因超时关闭的支付，只有末次尝试渠道与原支付渠道明确一致、尝试状态非空且非终态、完成时间为空，才允许主动查单或成功回调进入原子恢复路径。末次尝试已为 `成功`、`支付成功`、`失败`、`支付失败`、`SUCCESS`、`FAIL`、`FAILED` 或已有完成时间时，返回 `PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING`（`PAYMENT-1054`），不先调用外部渠道，不尝试将已关闭支付重开。原子 SQL 仍是最终并发裁决；历史真实资金需人工核对。

### 14.424 多端主动查单入口的尝试资格

App/H5/PC、小程序与管理端在展示主动查单操作前，应使用同一支付快照中的状态、末次尝试状态、完成时间和渠道交易号：`SUCCESS`（收款记录为“支付成功”）保留只读查询；`CLOSED`（“已关闭”）无末次尝试且无渠道交易号时保留未提交只读查询；`WAIT_CALLBACK`（“支付中”）及已有未终结尝试的 `CLOSED` 仅在末次尝试非空、非上述中英终态且完成时间为空时提供主动查单。`PAYING` 不是当前服务端查单可受理状态，不应仅因尝试文字非空就启用操作。管理端收款记录使用中文支付状态及外部交易流水号映射，不再把所有正常记录的查单入口误禁用。页面资格仅减少不可执行动作，渠道归属及资金事实仍由服务端门禁裁决；历史矛盾事实需人工核查。

对 `WAIT_CALLBACK` 和已有开放尝试的 `CLOSED`，页面还须从同一快照取得非空渠道；仅有尝试文字而缺渠道时不提供主动查单。已成功和未提交关闭单的只读查询不以渠道非空为前提。收款记录的展示渠道字段只作入口提示，精确渠道归属仍由服务端校验。

收银台页面从收银台快照判断查单资格时使用规范 `paymentStatus`，不可优先使用“支付中”等中文展示 `status` 覆盖它；查询后则使用最新支付详情的规范 `status`。两种快照均与本次读取的尝试、完成时间、交易号及渠道一起判定，避免有效 `WAIT_CALLBACK` 因展示文案而误禁用查单。

待回调收银台应保持服务端核验过的原支付方式为当前选中方式，不能因渠道列表中的首项覆盖原选择；在用户尚未主动查单时，以收银台快照展示已受理的原渠道流水号。上述字段只用于展示及客户端入口资格，渠道归属仍由服务端最终裁决。

小程序仅支持微信支付提交，但同一预付单若已在其他终端受理或成功，应展示收银台返回的原支付方式、原渠道和已有的渠道流水号，待回调/成功状态的按钮不得继续写成“确认微信支付”。风控复核中的支付方式使用原复核方式提示，不能把固定微信入口冒充原方式；是否能在小程序继续提交仍依既有场景、渠道和方式资格判定。

隔离跨系统 happy-path 在提交前收银台之外，还应于 `WAIT_CALLBACK` 和 `SUCCESS` 各读取一次同预付单收银台，逐一与当前支付详情比对支付单、业务订单、业务链、场景、金额、原支付方式、渠道编码、渠道流水和末次尝试状态/完成时间，并由 App 与小程序响应适配器再次验证。此为演练断言，不代替服务端裁决或生产渠道资金核对。

其中中文 `支付成功` 也是终态尝试，不可因缺完成时间而在 App/H5/PC 或小程序显示待回调主动查单入口；本机跨系统演练对预付、收银台和支付提交三处待处理快照也按同一口径拒绝该别名（含首尾普通空格）。此处仅约束客户端动作与演练断言，不代替服务端受理资格。

### 14.425 查单返回码快照与并发成功回调

提交与主动查单共用的返回码快照写入必须排除支付状态已为 `SUCCESS` 或成功时间非空的支付单，不能让较晚抵达的“处理中”查单响应覆盖成功回调后的诊断快照。主动查单独立事务按已提交数据读取；写入影响零行时回读支付详情：若回调已成功且成功事实有效、原渠道和查询所得非空交易号与赢家一致，仅记录本次查单轨迹并返回赢家成功快照，不把过时的处理中返回码带入响应；若交易号或渠道冲突，返回 `PAYMENT_CHANNEL_QUERY_FAILED`（`PAYMENT-1026`）并留失败轨迹；回读仍无法证实成功则失败闭合。真实多实例竞态与目标 MySQL 8 隔离行为仍需受控复核。

### 14.426 主动查单成功迁移输家复核精确渠道归属

渠道查单返回成功，但支付成功原子迁移未命中、回读到并发成功赢家时，原查询快照的渠道编码必须与赢家支付单渠道逐字符一致，同时渠道交易号精确一致，才可承认赢家并返回成功详情。不能把大小写别名渠道当成同一笔受理来源；不一致时返回 `PAYMENT_CHANNEL_QUERY_FAILED`（`PAYMENT-1026`）并留失败轨迹，不派发第二笔成功事件。此规则与提交赢家、迟到查单快照零行分支的精确渠道校验一致；生产渠道流水与真实资金仍需受控核对。

### 14.427 渠道报成功但历史关闭单未能收口

已提交渠道的历史 `CLOSED` 支付可以凭受理事实主动查单，但渠道返回 `SUCCESS` 不等于本地已经成功收口：原子成功迁移未命中、回读仍为 `CLOSED` 且预付绑定有效时，不得将“支付已关闭”作为本次查单确认结果返回。保持本地关闭状态与既有成功/失败查询轨迹，追加 `QUERY_FAILED` 诊断并返回 `PAYMENT_CHANNEL_QUERY_FAILED`（`PAYMENT-1026`）；用户端将结果标为未核实，运营应依据渠道流水与关闭事件人工核对，不得自动再支付或绕过条件强制结清。可按可信过期关闭事件恢复的支付仍走原子成功迁移，仅成功抢占者记账与派发事件；未提交渠道的关闭单依旧只读返回。

### 14.428 成功回调不能把未恢复关闭单视作已收口

验签并通过渠道绑定的 `SUCCESS` 回调在成功原子迁移未命中时，只有回读到已完成且事实一致的 `SUCCESS` 才按幂等成功响应；若仍为预付绑定有效的 `CLOSED`，不能释放提交令牌或返回成功响应。当前实现抛系统异常使该回调事务回滚，不重复记账或派发成功事件；可信过期关闭恢复成功路径不变。此前通用异常处理只返回 HTTP 200 内的错误码，现已按 14.429 将支付回调错误改为非 2xx；正式渠道 ACK、重试和实际资金核对仍须单独验收。

### 14.429 支付渠道回调错误不以 HTTP 200 确认

`POST /api/payments/callback/{channel}` 的正常收口与可信幂等成功继续返回 HTTP 200、`code=0`；该路径产生的非零 `ApiResponse.code`（含请求反序列化、业务校验、验签或内部结算错误）统一以 HTTP 503 携原错误码返回，不再用 HTTP 200 暗示成功接收。该策略仅作用于支付回调路径，其他支付 API 的统一响应体及 HTTP 状态沿原契约；路径识别基于 MVC 最佳匹配模板，避免依赖请求原始路径中的渠道值。HTTP 503 仅是本地非确认信号，不能替代不同渠道对 ACK 文本、状态码、重试间隔、签名异常和对账补偿的实际协议验收。

### 14.430 写路径和运营异常投影绑定原支付方式

支付单的成功原子迁移、待回调回写、尝试成功收口及提交结果绑定，不能仅凭渠道编码证明尝试归属；还须要求支付单与所属尝试的 `payment_method` 均非空白、原长度相同且二进制精确一致。管理端可信成功异常投影采用同一条件，方式缺失、大小写别名、尾随空格或另一方式不能被当作可信成功。该条件只收紧本地事实归属，不代替目标 MySQL 8 排序/PAD 行为、历史渠道资金与正式回调协议验收。

### 14.431 退款申请和日终成功事实须绑定原支付方式

退款申请、审核及重试读取加锁支付来源时，`paymentSuccessFactsValid` 除原支付单、唯一预付绑定、成功状态、金额、渠道及完成时间外，还要求最新成功尝试的支付方式与原支付单非空白、原长度和二进制精确一致，渠道也须原长度和二进制精确一致；尝试创建时间须不早于支付单创建时间，完成时间须不早于尝试创建时间且不晚于支付成功时间。方式漂移、渠道别名或倒置时间线均不得放行退款。日终的成功事实差异计数同样把原方式不一致或空白视作异常，而非只核对渠道。这是本地资金事实守门，不能代替目标 MySQL 8 与真实渠道资金验收。

### 14.432 日终渠道收口和内部事件须属于同一支付事实

日终的渠道成功量/金额、内部成功量/金额、渠道和内部异常数、支付成功缺口数/金额共用同一凭证归属规则：已收口 `SUCCESS` 渠道日志必须与支付单号按原长度及二进制精确匹配，且渠道编码同样精确匹配；已发布的 `PAYMENT_SUCCESS` 内部事件必须精确匹配支付单号及对应业务订单号 `biz_no`。仅大小写别名、尾随空格、其他渠道或其他业务订单的日志与事件不得充当本单的收口证明，须保留为日终异常/缺口。此计数反映本地证据，不代替渠道对账文件、内部账务核对及真实资金差异处置。

### 14.433 异常中心读写身份与告警详情回查阈值

聚合异常中的末次尝试、最近回调、待处理回调、失败/未知出站事件及停用渠道配置须与当前支付单/渠道按原长度和二进制精确归属；大小写别名或尾随空格不能借用其他单据的事实。异常列表与详情的最新动作、应用内告警、外部告警和派发通道汇总须精确匹配异常编号，告警汇总还须按原编号二进制分组，避免不敏感排序规则合并别名。单条异常及告警详情拒绝编号别名；按异常编号批量确认待回执告警、按告警编号确认回执也只更新精确编号。告警详情的回查队列状态使用与列表相同的服务端最短等待、最大次数和 claim 超时配置，不得使用缺少 `query` 参数的 SQL 绑定。未归属的历史事件/回调不会被错误指派给别名支付单；其独立孤儿治理和目标 MySQL 8 行为仍需验收。

### 14.434 告警派发与回执来源身份

站内 outbox 派发加锁、状态迁移必须按告警号和异常号原长度与二进制精确匹配。外发通道历史只能关联相同告警号和异常号的顶层站内来源；大小写或尾随空格别名不得继承已派发、失败次数、最近结果或供应商窗口统计。供应商受理回执的列表、单条、到期查询、占用、完成、送达回写与来源确认，同样按外发告警号、来源告警号及异常号精确核对；不满足来源链的日志不可回查或确认。目标 MySQL 8 的实际排序规则/PAD 仍待验收。

### 14.435 SLA 告警生成及升级候选精确排重

首次 SLA 告警生成只允许同一精确异常号的待确认告警抑制候选。升级排重同时核对异常号和顶层来源告警号的原长度、二进制内容；`source_alert_no` 存在但与候选不符时不得用自由文本兜底。仅为历史未记录来源号的升级告警保留内容排重，且内容须以原样的 `升级来源告警 <告警号> 已超过` 起始，不能使用 `LIKE` 通配匹配编号前缀或大小写别名。SLA 看板、总量、首次候选及列表中的停用渠道异常只统计精确渠道编码相符的配置，别名渠道不得制造超时风险。目标 MySQL 8 与历史告警数据仍待核验。

### 14.436 到期关单任务看板口径

概览的已到期预付笔数、当前可关单数与退避中的关单数须与执行侧 `PaymentMapper.findExpiredPayments` 使用相同资格：支付单状态为原样 `PREPAY_CREATED`、无成功时间及渠道交易号、最新的**精确同单**预付已过期，且不存在**精确同单**支付尝试。关单重试标记只归属精确支付单号；其下次重试时间分别决定待执行或退避，不得被大小写、尾随空格等别名预付/尝试/重试记录影响。目标 MySQL 8 与历史数据仍需受控验收。

### 14.437 自动日终巡检凭证与批次关联

业务日巡检只将原样 `PAYMENT_DAY_END`、`AUTO`、`payment-day-end-scheduler` 且处理数为 1、失败数为 0，终态为原样 `SUCCESS`（成功 1、告警 0）或 `WARNING`（成功 0、告警 1）的任务日志视为自动日终完成凭证。批次必须为原样 `AUTO`；任务 `business_ref_no`、批次 `batch_no` 及重复日志引用均按原长度和二进制精确核对。大小写或尾空格别名不能掩盖缺少关联的自动批次、充当已关联的成功日志，或制造/掩盖重复日志；真实财务文件、目标 MySQL 8 和历史批次仍需独立核验。

### 14.438 任务日志详情与列表口径

单条任务执行日志详情按 `task_log_no` 的原长度和二进制内容精确查找，不因大小写或尾随空格等别名返回其他日志。分页列表、总数和导出共用相同过滤规则：传入的任务编码、运行方式、任务状态、严重等级与存储值按原长度和二进制精确匹配；业务日仍按日期筛选。管理员的别名筛选不扩展为原值，原值筛选也不能夹带别名历史日志。目标 MySQL 8 排序/PAD 及历史日志仍需验收。

### 14.439 主动查单候选及历史退避归属

任务中心人工/自动支付查单列表、待查积压和到期计数只选择原样 `WAIT_CALLBACK`、存在精确同单预付且精确同单末次尝试满足最短等待的支付单。大小写、尾空格等别名尝试不改变原末次时间，也不能让缺尝试或预付的单据伪装为候选。自动查单的成功/失败查询历史须先按原长度及二进制支付单身份分组，再精确关联当前支付单计算退避；别名历史不得推迟原单补查，精确近期历史仍按递增退避控制。目标 MySQL 8 排序/PAD、渠道查单和历史数据需受控验收。

### 14.440 退款主动查单资格及尝试级退避

任务中心人工/自动处理中退款查单候选、积压及到期计数只认原样 `PROCESSING`、精确同退款单最新尝试。已拿到渠道退款号的尝试须具备提交时间；尚未拿到渠道号且状态为原样 `UNKNOWN` 的尝试须有原样 `UNKNOWN` 的退款提交事件。该事件必须与尝试号、原支付单号、`REFUND_SUBMIT` 类型、`gateway-access` 下游和 `payment.refund.submitted.v1` 主题均原长度和二进制一致；别名或其他支付单的事件不可赋予查单资格。自动退避只统计同退款单、同尝试号且动作原样为 `CHANNEL_QUERY`/`CHANNEL_QUERY_FAILED` 的操作日志，别名或其他退款单日志不能挪用，新尝试不得继承旧尝试退避。目标 MySQL 8、渠道退款事实和历史日志另行受控验收。

### 14.441 已启用渠道回调窗口启动门禁

验签开启时，应用在 Web 端口开始接收请求前读取现有渠道配置；快照为空引用或包含空记录时失败关闭；除原样 `DISABLED` 外的渠道缺失或使用非正验签窗口，或有效 `payment.callback.nonce-ttl-seconds <= 2 * notifySignWindowSec`，均拒绝启动，不等首次渠道回调才发现部署参数错误。状态大小写/尾随空格别名不能绕过门禁。验签关闭的 demo 场景不触发该存量门禁；候选提交、启用、回滚和运行时验签门禁保持不变。当前默认 TTL `3601` 秒覆盖种子渠道最大 `1800` 秒窗口；显式部署旧值 `601` 秒且保留该渠道时应在发布前调整配置。此门禁不代替真实渠道密钥/回调、生产 MySQL 与多实例验收。

### 14.442 支付记录的多预付来源诊断

支付记录列表、导出、计数和详情仅在支付单号原长度及二进制精确匹配到唯一预付单时投影 `payScene`、失效时间和有效期，并从该场景派生支付类型。若同支付单存在多张精确关联预付单，仍保留支付记录、支付/退款事实及筛选结果，但 `payScene`、`expireTime`、`validityPeriod` 为 `null`，支付类型为“未分类”，新增 `prepayBindingIssue=MULTIPLE_PREPAY_BINDINGS`；按其他具体支付类型筛选不能收纳该记录。CSV 尾列“预付归属异常类型”保留诊断。大小写/尾随空格等编号别名不构成该支付单的归属来源。管理端仅接受此明确诊断与三个空投影的一致组合，并禁用由记录推断支付单详情或主动查单的操作，仍可联查支付请求/日志。先前“最新预付”展示口径在此场景不再适用；该读路径不改写历史或资金。目标 MySQL 8 排序/PAD、历史多预付人工归属、真实渠道和资金仍待验收。

### 14.443 支付记录缺失精确预付归属

14.442 的唯一绑定判定同时覆盖零张精确关联预付单：只有大小写/尾随空格等编号别名预付也视为零张。支付记录列表、导出和详情保留支付及退款事实，`prepayBindingIssue=PREPAY_BINDING_MISSING`，`payScene`、`expireTime`、`validityPeriod` 为 `null`，`paymentType` 为“未分类”；CSV 的预付归属异常类型尾列保留该诊断。管理端只接受诊断与空来源投影的一致组合，显示“预付归属缺失，待人工核查”，并禁用依赖来源的主动查单/支付单详情动作，仍可联查请求和日志。该诊断不证明历史支付单无预付，只说明当前精确键未找到；不得自动把别名预付重绑定或回写历史资金事实。目标 MySQL 8 排序/PAD、历史数据及渠道事实仍需独立验收。

### 14.444 支付主列表与导出的预付归属异常

支付主列表和导出按原长度、二进制支付单号检查精确关联预付数：唯一时保留场景；零张返回 `prepayBindingIssue=PREPAY_BINDING_MISSING`，多张返回 `MULTIPLE_PREPAY_BINDINGS`，两种异常的 `payScene` 均为 `null`。支付单本身保留在列表/计数/CSV，导出尾列“预付归属异常类型”记录原因。管理端分别显示缺失/多预付待人工核查，保持支付及渠道事实可读，但不基于异常来源从列表执行详情跳转、主动查单或本地关闭，可联查处理日志。列表门禁不是服务端状态迁移授权；现有支付单详情 Mapper 对非成功、非待回调异常历史数据仍可能投影最新预付，需后续独立失败关闭与 HTTP 验证。目标 MySQL 8 collation/PAD、历史数据审计和真实资金验收不在本地测试结论内。

### 14.445 直接支付详情的异常预付绑定门禁

`GET /api/payments/{paymentOrderId}` 对不进入既有成功事实校验分支的支付单，在加载详情后、读取轨迹或返回来源字段前，要求 Mapper 已验证唯一精确预付、原样订单号、正数且相等金额，即 `prepayAmountBindingValid=true`；缺失、多张或金额/订单漂移一律返回 `PAYMENT-1011`，不从最新预付猜测可用场景。成功支付单保持已有成功事实校验，失败则仍以原成功事实异常处理。14.444 所述非成功详情直连缺口由此收紧；列表保留异常行和归属原因供审计，详情失败不抹除支付与退款历史事实。隔离 HTTP/H2 覆盖正常待回调、历史重复精确预付、仅有别名预付三段链路。此门禁不代表其他查单/关闭/回调入口、目标 MySQL 8、正式渠道或资金已验收。

### 14.446 非成功支付查单、关闭与回调入口的预付绑定门禁

`POST /api/payments/query`、`/api/payments/close`、`/api/payments/callback/{channel}` 与对应管理端查单/关闭路径，在读取指定支付单后要求非成功状态的 `prepayAmountBindingValid=true`；并发竞争后的支付单回读同样校验。缺失、多张精确预付，或预付与支付单订单号/金额不符时返回 `PAYMENT-1011`，不先查询渠道、关闭支付或接受回调；回调仍须先通过既有签名验证，错误回调不会以 HTTP 200 确认。已成功支付保持原有成功事实校验，不借此重新收口。列表/导出仍保留异常支付行及诊断，不自动修改历史绑定。隔离 H2/HTTP 覆盖多预付及仅别名预付下详情、查单、关闭、回调的拒绝，并以正常待回调详情及已成功支付异常事实 HTTP 用例作对照。此门禁不证明其他写入入口、目标 MySQL 8 历史数据、真实渠道资金、可信身份或正式发布已验收。

### 14.447 待回调补查候选与积压须有可信预付绑定

任务中心的人工/自动待回调支付补查候选以及待补查/已到期积压计数，只收纳精确关联到唯一预付单、预付与支付单原样订单号相同且正数金额相等的支付单；重复精确预付、只有编号别名、金额或订单号漂移不进入这四个补查口径，以免反复派发必然被 14.446 拒绝的请求或占用渠道批次额度。原支付及异常来源诊断仍由支付列表/记录保留，排除补查并不证明历史资金状态正常，也不自动修复预付绑定。回退/配额规则不变；目标 MySQL 8 排序规则、存量异常的人工核查及真实渠道查单另行验收。

### 14.448 自动过期关单须保持预付金额与订单号绑定

在 14.447 的待回调补查门禁之外，`PREPAY_CREATED` 自动过期关单的候选、原子关闭更新，以及任务中心总览/待处理/退避三类积压口径均要求唯一精确预付的订单号原长度和二进制一致、预付与支付单金额相等且均大于零。即使过期时间已到且没有渠道尝试，绑定漂移也不可自动关闭或发出过期事件；既有失败重试标记若因此不再符合条件，可由清理任务删除，绑定修复后重新按当前事实评估。支付行及异常诊断保留待人工审计，不自动修复历史归属或推断资金状态。H2/MySQL 模式验证候选、状态更新、重试清理及三个积压计数；目标 MySQL 8、真实历史数据、资金与渠道验收仍需另行确认。

### 14.449 人工关闭的原子预付绑定门禁

支付详情已通过 14.446 的预付绑定校验后，人工关闭的状态更新仍须在 SQL 更新条件内重新要求支付单正数金额、唯一精确关联预付、预付正数等额且原样订单号一致，同时保留原有状态、成功时间、渠道交易号和支付尝试限制。更新未命中时回读支付单：若非成功单的预付绑定已漂移，返回 `PAYMENT-1011`，而非笼统提示先查渠道；不更新预付状态或写关闭事件。H2 验证从有效绑定变为金额/订单号漂移或多预付后更新行数为零，Mockito 验证回读错误码；此处的 SQL 条件与本地测试不替代目标 MySQL 8 多事务并发/锁序、真实历史与资金验收。

### 14.450 运营收银台会话的精确标识与支付事实联查

`GET /api/cashier-sessions/{sessionNo}` 仅接受预付单号原长度和二进制均一致的会话，大小写或尾随空格别名不能读取原会话。会话列表、详情、总览、计数与导出保留原始预付单记录，但投影支付状态和最近支付尝试终端时，只关联与预付单支付单号原样一致的支付单及其原样同号尝试；仅有编号别名时不借用别人的支付中/成功状态或终端，而按本会话原始状态、支付场景和失效时间投影。列表的搜索条件仍是原有模糊筛选，不将运营查询解释为身份认证或精确查单。H2 宽松排序规则覆盖会话号别名、预付到支付及支付到尝试两层别名，均保留真实会话行。目标 MySQL 8 collation/PAD、历史归属、可信身份和真实资金验收仍须独立完成。

### 14.451 运营支付处理日志详情与成功事实精确归属

`GET /api/payment-logs/{logNo}` 只接受原长度和二进制均一致的日志号，不能用大小写或尾随空格别名读取原日志。日志列表、详情与导出仍保留各自原始支付单号及消息；其 `successFactIssue` 只从与日志支付单号原样相同的支付单投影，别名支付单不提供成功事实异常证明，也不能借别人的异常归属。列表按支付单号等条件的模糊搜索语义不变；异常投影为空不代表历史支付正常，需从原始编号与可信事实继续审计。H2 宽松编号验证别名详情拒绝与三处只读投影，目标 MySQL 8/PAD、历史日志和真实资金仍未验收。

### 14.452 运营账单详情与最近支付事实精确归属

`GET /api/bills/{billNo}` 仅按账单号原长度与二进制原样返回详情，大小写或尾随空格别名不读取原账单。详情中的最近预付单、最近支付单及其归属预付单，和列表/导出的最近支付场景，都只按原样账单号、支付单号关联；存在仅大小写不同的历史编号时，保留账单自身金额和状态，不借用别的预付或支付事实。原有列表模糊筛选不变，未联查到事实不等于该账单已结清或历史数据可信。H2 宽松编号验证；目标 MySQL 8/PAD、历史事实和真实资金仍须验收。

### 14.453 标准订单姓名与支付结清账单精确归属

预付创建读取订单客户姓名、成功支付事件派发读取服务人员姓名时，订单号必须同时满足原长度和二进制原样匹配；大小写或尾随空格别名不能借用标准订单的姓名。支付详情的账单号仅从预付单原样账单号联查，历史别名账单不冒充关联账单。成功收口更新账单时也要求账单号、订单号均原样一致，且保留既有客户、金额与已付快照条件；只有别名账单时更新零行并沿现有失败路径处理，不结清别人的账单。H2 宽松编号红绿验证，不替代目标 MySQL 8/PAD、真实历史资金或下游端到端验收。

### 14.454 支付成功事件的订单号不接受裁剪别名

支付成功事件出站前，事件 `bizNo` 与支付详情 `orderNo` 必须原样一致，且该订单号自身不得含首尾空白；两者裁剪后相等、或两者同样带空白，均在服务人员查询与清分、账务 HTTP/AMQP 出站前失败，进入既有事件失败/重试路径。客户及服务者名称仍可裁剪展示空白，既有合法订单号载荷不变。历史异常成功事实须核查来源而非在派发时改写订单身份；本地 Mockito 不证明下游投递或真实资金结果。

### 14.455 路由运行态只接纳原样启用的渠道和规则

支付路由读取可用渠道及启用规则时，`status` 必须原长度和二进制值均等于 `ENABLED`。历史行若仅以大小写或尾随空格别名命中宽松排序规则，不参与路由决策；其他真实启用渠道仍可按既有规则和兜底策略选择。管理端查询原始配置不受影响，异常配置需人工复核，不能据此推断真实渠道可用。H2 宽松状态回归不替代目标 MySQL 8/PAD、真实流量与渠道验收。

### 14.456 生效控制策略开关异常时提交失败关闭

当来源应用有生效控制策略时，支付提交前的 `tokenAuthRequired` 和 `strictMode` 仅接受原样 `开启` 或 `关闭`。令牌开关缺失或出现首尾空白等历史别名时返回既有访问令牌错误，不进入渠道提交；严格模式开关异常时返回既有自检阻断错误，不绕过待处理自检。收银台读取到异常严格模式开关时不展示可选支付方式。无生效策略的既有兼容路径不变；此门禁不证明调用方身份可信或历史策略事实已清理。

### 14.457 严格模式自检只接受原样通过结果

生效来源应用控制策略启用严格模式时，收银台展示方式和支付提交均只接受自检结果原样 `PASS`。历史小写 `pass`、其他别名或缺失状态不再被视作通过：收银台不展示可选方式，提交返回既有自检阻断错误且不进入渠道提交。正常 `PASS` 与无策略兼容路径不变；异常自检事实需人工复核，不能凭本地测试推断生产渠道健康。

### 14.458 退款渠道快照不借首尾空白完成收口

退款回调验签和路径渠道规范化后，持久化退款渠道编码若带首尾空白，不得再通过裁剪与回调渠道匹配，也不能恢复未知受理或更新退款终态；沿既有 `REFUND_CALLBACK_CHANNEL_MISMATCH` 拒绝。主动查单在调用渠道前拒绝该类快照；已有终态的幂等查询也不把带空白渠道当作可信受理事实。原有渠道编码大小写兼容和客户端回调路径规范化不变；异常历史单须按真实渠道流水核查，不以本地快照判断退款到账。

### 14.459 退款下发不裁剪原支付渠道身份

退款审批、人工失败重试及任务中心重试共用渠道下发入口。读取原支付单的持久化渠道编码后，若为空或含首尾空白，拒绝创建退款尝试和提交事件；既有事务中的状态变更应回滚，不把异常历史编码裁剪成正常渠道号交给派发器。原样有效编码按既有流程写入尝试并在事务提交后派发；历史异常单须核对支付和退款渠道事实，不能通过重试自动改写身份。

### 14.460 退款提交事件不借持久化尝试渠道别名

既有待派发或失败重试的退款提交事件即使绕过新申请门禁，也必须在渠道请求前校验退款尝试渠道编码非空且无首尾空白；异常快照走现有失败记录和事件重试/死信路径，未知受理事件仍要求人工核查，不把异常编码裁剪为正常渠道号写入受理或未知事实。已有受理轨迹的事件也不得凭两处相同的空白渠道别名确认为成功。正常渠道号保持原样透传与回写；该门禁不替代历史数据清理或渠道到账确认。

### 14.461 退款审批异常渠道事务回滚的本地验证

在 H2/demo 的真实 Spring HTTP 审批链路中，先创建待审核退款，再把原支付单与成功支付尝试渠道同时改为带尾空白，保持其他支付成功事实有效。审批应失败并回滚退款状态和操作日志，且无新增退款尝试、提交事件或派发调用；合法渠道的原有审批闭环保持不变。该用例仅验证本地事务及接口行为，不证明目标 MySQL 8 行锁/排序规则或生产退款资金结果。

### 14.462 旧退款受理轨迹异常时保留尝试事实

退款提交事件快照校验失败，若尝试已有渠道退款号或状态并非原样 `PENDING/FAILED`，不得强行调用仅适用于未受理尝试的失败更新；普通失败事件沿既有失败/死信路径处理，`UNKNOWN` 事件仍要求人工核查，均不得再次请求渠道或把已受理尝试改成失败。人工单笔重发返回既有失败响应，不写成功重发审计；修复未受理尝试的异常渠道后可对同一事件重新发起重发。此路径不自动判定真实受理或到账，仍须核对历史渠道流水。

### 14.463 人工重发受控失败不回滚渠道未知事实

单笔人工重发若派发器已按状态机返回受控失败，HTTP 保持既有 `PAYMENT-400`，且不写成功重发审计；外层事务不得因此回滚派发器已写入的 `FAILED/DEAD_LETTER` 或 `REFUND_SUBMIT/UNKNOWN`、退款尝试 `UNKNOWN` 和待查渠道。结果未知后再次重发返回 `PAYMENT-1050`，不得再次请求渠道。仅此受控失败异常不触发外层回滚；意外异常、未知事件专用业务拒绝和成功后审计写入失败仍遵循原有回滚边界，成功投递及审计仍在同一事务中完成。须先通过可信渠道查单或人工核查确认未知受理，不能靠接口错误码推断未退款。

### 14.464 管理端重发错误后的事件快照重读

人工重发 POST 返回失败不代表事件状态未改变；运营列表在显示失败原因后必须立即按上次成功查询条件重读事件列表及总览。读回 `UNKNOWN` 时禁用再次重发并提示核查，筛选条件排除事件时仍需提示操作者核对待核查、失败与死信状态；重读失败则将旧快照标记过期，禁用重发、导出、翻页等基于旧事实的动作，直到成功刷新。该交互不改变服务端 `PAYMENT-400/PAYMENT-1050`、审核权限或真实渠道结果。

### 14.465 管理端退款动作错误后不沿用旧状态

退款列表的申请、审核、主动查单及失败重试，与退款详情的审核/重试动作，即使接口返回失败或响应快照不一致，也不能推断退款单及渠道状态未变化。运营页面在保留原错误提示与申请幂等键的同时，按上次已加载条件重新读取列表或当前路由详情；成功读回后以服务端状态决定可用动作，读回失败则标记快照过期、禁用基于旧事实的动作。详情页跨退款单路由切换时不得用旧请求覆盖新详情。该失败关闭仅影响管理端读回，不代替渠道查单、可信运营授权或真实到账判断。

刷新失败时的提示不得把失败的 POST 描述为“结果已确认”或“结果已保留”；只能陈述当前状态未经确认、页面展示旧快照并禁用依赖旧事实的操作。手动成功刷新后才能解除过期门禁，且应以读回的新状态决定可操作项。

### 14.466 管理端支付查单与关闭请求失败后重读

运营支付列表或详情发起主动查单、本地关闭后，即使 POST 返回业务错误或无法解析动作事实，也不能推断支付单未变化。保留原错误提示，并按上次成功查询条件重读列表或当前路由详情；读回成功后以支付服务最新状态决定查单/关闭是否仍可操作。读回失败则标记旧快照过期，禁用关闭、查单、导出及依赖旧事实的详情入口，直至成功刷新。路由切换后不得用旧单响应覆盖新单；刷新失败的文案只说明状态未经确认，不应宣称动作已完成或回滚。本规则不替代真实渠道查单、可信运营授权与资金核对。

### 14.467 九服务本地主动查单变体的事实边界

`verify payment-query-chain` 仅在本地 H2/demo 隔离栈中为 gateway-access 配置本机微信查单上游和临时访问密钥；上游必须先精确绑定本轮支付单、订单号与渠道交易号，才返回模拟 `SUCCESS`。验证器通过 `POST /api/payments/admin/query` 收口，检查网关查询审计、单次上游请求、支付终态重放以及原有清分/账务/结算/退款/对账断言；默认 `verify payment-chain` 保持回调收口。两种路径仅是本地协议与状态机回归，不能取代真实渠道凭证、资金到账、可信运营身份或正式关账决策。

### 14.468 异常批量动作的精确目标响应

更新 14.205 的动作响应语义：`POST /api/payment-issues/actions` 不再使用无筛选的默认第一页作为返回值。服务端在同一事务内写入全部所选异常动作后，按原顺序逐条回读，核对异常编号、支付单与异常类型，返回仅含本次目标的 `PageResultDTO`；缺失或归属漂移即抛错回滚。原列表查询、默认每页 20 条与批量最多 100 条的限制不变。动作响应仅供确认目标连续性，前端仍须以原筛选条件读取当前页面与责任组统计。该变更不提供批量请求幂等键，响应丢失时仍需先核对服务端事实，不能盲目重试。

### 14.469 异常批量请求失败后的只读复核

`POST /api/payment-issues/actions` 返回错误或动作响应不可信时，不得由 HTTP 错误推断动作未写入；管理端清空原选择，保留错误，按发起前的查询条件并行 GET 列表和责任组统计。两项成功后仅展示已确认的新快照，需人工核对处理记录后重新选择；任一 GET 失败，旧快照不得用于批量处理、选择、下钻或分页，直到刷新恢复。该门禁不提供服务端批量幂等：即使 GET 成功且处理状态未改变，仍不能单凭页面状态盲目重复同一 POST。

### 14.470 异常批量动作请求幂等与结果重放

更新 14.468、14.469 中关于幂等的旧边界：`POST /api/payment-issues/actions` 新增可选 `idempotencyKey`（规范化后非空、最多 128 字符、不含 ISO 控制字符）。新管理端每次动作生成一个键；遇到不确定的 POST 错误仍清空选择、复核列表与责任组，待用户重新选择**同一目标和同一动作内容**时，在同一页面会复用原键。修改目标、动作、处理人、操作人或备注必须使用新键。页面重载后无法恢复未决键，必须人工核对日志，不得推断 HTTP 错误代表未写入。旧客户端不传键仍可使用，但不具备批量请求去重保障。

服务端先规范化字段，目标集合按编号排序后与动作字段组成 SHA-256 指纹；唯一请求行在动作写入和告警确认的同一数据库事务中占用。相同键与指纹重复调用不再写动作，而返回第一次提交时保存的**脱敏目标响应**，即使当前聚合异常已消失；目标顺序变化不影响指纹，重放保持首次响应顺序。相同键不同目标集合或业务字段返回 `PAYMENT-1070`，不写动作；首次执行失败则请求行及动作一起回滚。部署前先执行 `database/migrations/2026-10-10-add-issue-action-request-idempotency.sql`，现有全量 schema 已包含表。H2/正式 Mapper 并发测试已覆盖同键抢占；MySQL 8 多实例、授权身份和真实渠道资金不在本地证明范围内。

### 14.471 页面刷新后的未决批量动作键恢复

更新 14.470 的页面重载边界：管理端在发送 POST 前把 SHA-256 请求摘要和随机幂等键写入**当前标签页的 `sessionStorage`**，不保存异常编号、操作人、备注等请求原文；提交成功后只清理由本次键占用的记录。页面刷新并重新输入相同内容、选中相同目标集合后，可恢复原键请求服务端重放；仍须以最新列表与责任组为可操作前提，不自动重试，不用 POST 错误推断写入失败。不同内容产生新键；存储不可用或 Web Crypto 摘要不可用时仅保留当前页面内的内存重试能力。新的标签页或已关闭的会话不能恢复原键，仍需人工核对处理日志。

### 14.472 异常批量动作真实 HTTP 重放契约

本地 H2/demo 随机回环端口的 Spring HTTP 定向测试从实际 `POST /api/payment-issues/actions` JSON 请求进入控制器、服务与正式 Mapper：首次两条分派成功且出站姓名脱敏；删除其中一条聚合异常来源后，交换目标顺序并复用键，仍返回首次完整 `data`，仅 `requestId` 更新，动作日志保持两条；同键不同备注返回 `PAYMENT-1070` 且无额外日志，空白键返回 `PAYMENT-400`。这证明本地 HTTP/事务接线，不证明真实客户端网络中断、目标 MySQL 8 多实例锁、正式运营身份或资金渠道。

### 14.473 批量动作幂等键按原样字符区分

更新 14.470 的请求占位存储：数据库表现在仅保存去首尾空白后的幂等键 UTF-8 字节的 SHA-256 **小写十六进制摘要**，不保存原始键。这样即使目标 MySQL 的 `utf8mb4` 排序规则大小写或重音不敏感，`Case-Key` 与 `case-key` 也分别占用独立行，不能因排序规则别名重放对方的动作；同一原键和相同内容仍重放首次响应。无法编码为合法 UTF-8 的不完整 Unicode 键拒绝为 `PAYMENT-400`。全量 schema 与尚待部署的 `2026-10-10-add-issue-action-request-idempotency.sql` 已同步为 `idempotency_key_hash` 主键，不能部署旧版本增量脚本。

定向 H2/正式 Mapper 测试会先删除演示全量 schema 生成的请求表，再实际执行增量脚本，并检验同键并发与大小写变体独立；独立真实 HTTP 定向用例也验证两个键均可成功写入。H2 语法/行为不等于 MySQL 8 的真实排序规则与多实例事务锁，后者仍需环境验收。

### 14.474 管理端批量动作响应必须与目标集合精确相等

更新 14.468 的前端消费校验：管理端收到 `POST /api/payment-issues/actions` 的成功响应后，除逐行验证异常归属与状态外，还要求 `items.length`、`total`、`pageSize` 均等于提交目标数，`pageNo=1`，目标编号去空白后的集合与响应编号集合完全相等且双方无重复。服务端同键重放保留第一次的目标顺序，因此前端接受同集合的不同顺序，不要求与本次请求排列一致。多余目标、重复目标、缺失目标、空行或不匹配的分页元数据一律按 `PAYMENT-CLIENT-INVALID-ISSUE-BATCH-ACTION` 处理，按原双读回规则复核，不得显示成功。

### 14.475 用户收银台未决支付提交的同键恢复

App/H5/PC 共用收银台在 POST 支付提交前，对预付单、支付方式、渠道、来源应用、商户、访问令牌和终端组成的请求内容计算 SHA-256 摘要，仅把摘要与随机 UUID 幂等键存入当前标签页 `sessionStorage`，不保存支付请求或令牌原文。提交响应与当前支付快照匹配后清除；请求报错、响应不可信或刷新失败则保留。若重新加载后支付状态仍为可提交的 `PREPAY_CREATED`，用户以**完全相同的内容**重试会恢复原键；任何业务字段或令牌变化使用新键。无 `randomUUID` 时用 `crypto.getRandomValues` 产生 UUID，缺少安全随机源不退化为 `Math.random`；浏览器存储或摘要不可用时仅保留同页内存键。提交前异步恢复期间若路由/支付快照变化，不得发起旧请求。

错误后仍先按预付单读回，待回调或未知状态禁止重复提交；仅本机 mock 演示“POST 报错、GET 仍待支付、页面刷新、同请求体重试”这一条件分支，不能将待支付读回解释为前一次请求未被渠道受理。用户显式重置键或切换支付方式后的不确定渠道副作用仍需额外服务端事实核对；生产资金渠道、可信令牌、MySQL 多实例不在该本地证据范围内。

### 14.476 未决支付提交的换渠道与重置门禁

更新 14.475 的遗留风险：会话里的待核实提交记录现在额外保存预付单 SHA-256 作用域摘要与原支付方式（仍不存预付号、令牌或完整请求原文）。收银台每次成功读回后按预付号摘要恢复对应记录，绑定显示的原方式与幂等键；在提交响应尚未得到可信确认时，包括短暂 GET 仍显示 `PREPAY_CREATED` 的情况，所有渠道切换入口与“重置幂等键”禁用，程序调用也拒绝重置。新 POST 若同预付单的请求内容、渠道、商户、访问令牌或终端变了，即使页面状态一时仍可提交，也会在发送前拒绝并要求先核对原提交；相同内容仍可沿用原键重试。可信提交响应才清理记录，读回失败继续保留。

本地用户端单测和隔离浏览器覆盖首次 POST 报错、GET 待支付、刷新前后禁用换渠道/重置、原方式同请求体重试成功。无法从一个 `PREPAY_CREATED` 读回证明渠道无副作用；该门禁故意不提供未经运营核实的直接换渠道恢复。当前浏览器会话存储只保留一条最近待核实请求，切换另一张预付单可能覆盖前一条；跨标签页或关闭会话也不能恢复，仍属后续交付差距。实际渠道资金与可信身份未由本机 mock 证明。

### 14.477 同标签页多预付单未决键并存

更新 14.476 的单记录边界：用户端会话存储改为以预付号 SHA-256 摘要分区，每张预付单独立保存其完整提交指纹摘要、原支付方式及 UUID；不存预付号或访问令牌原文。导航到另一张预付单并保存未决提交，不再覆盖上一张的键；返回原预付单时按摘要读取并保留原渠道与重置门禁。可信成功响应仅删除当前预付单且键匹配的记录，显式允许的重置也只删除当前预付单记录，不触碰其他未决支付。重置过程异步计算存储作用域时纳入 `actionRunning` 锁，防止同时提交刚写入的键被清理；路由切换后晚到重置不改变新页面。

单测真实调用会话存储替身，验证两张预付单同时保存、单独恢复、单独清理与各自门禁；隔离浏览器还在同一标签页模拟两张预付单分别 POST 报错、返回第一张刷新后用原请求体重试成功、再次返回第二张仍恢复原键且禁用重置。会话存储不可用时同页仅保留当前预付单内存门禁；跨标签页、关闭会话、真实渠道资金和可信身份仍未验收。

### 14.478 损坏的未决支付记录不得生成新提交键

更新 14.475 的畸形记录处理：当前预付单的会话存储键**存在**但 JSON 无法解析、摘要/作用域/支付方式不合法或 UUID 损坏时，不能将该记录当作“从未提交”。读取和提交前恢复均失败闭合，保留原存储值，不产生新键或支付 POST；收银台提示联系运营核对原支付结果，刷新也不解锁提交、渠道切换和重置。其他预付单的独立分区不受影响。存储 API 本身拒绝读取时仍只能保留同页内存键；跨标签页、会话关闭及服务端真实受理事实不由该前端门禁证明。

### 14.479 损坏记录不妨碍已确认终态只读展示

更新 14.478 的收银台刷新边界：如果服务端收银台响应已按当前预付号、业务场景及已加载支付快照校验，且明确返回 `SUCCESS` 或 `CLOSED`，即使同预付单的未决提交记录损坏，也可以按服务端支付单号进入结果页，再独立 GET 支付详情核实；无需也不得发起新的支付提交。非终态及收银台响应不可信时仍保持失败闭合，不自动删除损坏记录、重置幂等键或切换渠道。本地终态显示不代表真实渠道资金或正式身份核验。

### 14.480 支付与退款幂等键的 Unicode 编码门禁

显式支付提交键及退款申请键在首次 Mapper 访问前拒绝不成对的 UTF-16 代理字符，分别返回 `PAYMENT-1028`、`PAYMENT-1052`；支付提交未传键时最终组合键仍经同一校验。合法 Unicode 和原有去首尾空白、长度及控制字符规则不变。这避免无效字符经不同数据库驱动编码为替代字节后与另一键混淆；不改变现有文本列的目标 MySQL 8 排序规则，也不证明多实例同键不同内容的全请求指纹约束。

### 14.481 支付提交同键重放需匹配持久化请求摘要

首次提交在预处理事务中，将规范化预付单/支付单/金额、支付方式、实际路由渠道、来源应用、商户号、访问令牌、终端、设备号与付款手机号组成有长度边界的 SHA-256 小写十六进制摘要，与支付尝试一起持久化。只保存摘要，不在支付尝试中保存令牌原文；可信客户端 IP 是可变化的连接事实，不纳入请求内容比较。同一键再次出现时，除原预付单/支付单归属外，摘要也须精确一致才能读取当前受理结果；不同内容返回 `PAYMENT-1069`，不新增尝试或再下发渠道。准备事务内发现另一请求抢先落库，也执行相同核验。

正式环境必须在部署此服务版本前执行 `2026-10-10-add-payment-attempt-request-fingerprint.sql`；迁移给历史支付尝试留下 `NULL`，不能从旧请求快照可靠反推访问令牌等完整内容，因此历史同键重放失败闭合为 `PAYMENT-1069`，应按原支付单查单或人工核对，不能换键盲目重付。新记录可沿原内容重放；本地 H2 迁移与调用回归不等于目标 MySQL 8 多实例、正式渠道资金或生产发布验收。

### 14.482 支付提交普通文本字段的 Unicode 编码边界

支付提交的预付号、支付方式、渠道、来源、商户、访问令牌、终端、客户端 IP、设备号与付款手机号在数据库读取或路由调用前拒绝不成对的 UTF-16 代理字符，返回 `PAYMENT-1035`；已解析的必填字段及路由渠道同样校验 UTF-8 可编码性。幂等键仍沿用独立的 `PAYMENT-1028` 门禁，合法 Unicode 的规范化与同键摘要规则不变。此处只阻断无效文本到下游，不证明目标 MySQL 8 排序规则或真实渠道接收规则。

### 14.483 同键竞争在收银台已占用分支再次核对内容

两个提交同时看到预付单待支付、随后一方先在独立事务落尝试时，后到请求可能在 `updatePrepayToPaying` 得到零行；它不可仅凭最新收银台已支付中/待回调/成功就复用赢家结果。服务先核对最新支付事实，再以锁定读读取已提交的同键尝试，精确比较预付单、支付单及规范请求摘要；缺失或不同内容均拒绝复用，不再释放赢家仍有效的提交并发令牌。同内容也只有通过当前事实核验才能复用，不触发第二次渠道提交。本地 H2 双线程验证不代替目标 MySQL 8 多实例隔离级别下的事实可见性和真实渠道并发验收。

### 14.484 跨预付单共键竞争回滚后归属裁决

不同预付单并发使用同一显式幂等键时，两条订单锁不能相互串行化；唯一键仍负责拦截第二条尝试。准备事务若因数据库唯一键冲突回滚，服务仅在事务外重新精确查到同键尝试时复核其预付单、支付单与请求摘要：归属不同返回 `PAYMENT-1069`，不调用第二次渠道，也不留下输家预付单占用/并发令牌。若数据库冲突并非该幂等键或同键归属仍匹配，保留原脱敏事务失败，不把无关数据库错误冒充业务冲突。目标 MySQL 8 排序规则可能让原文不同的键碰撞；这类场景仍需目标库单独治理，本地 H2 并发不代表正式资金验收。

### 14.485 幂等键宽松排序规则别名碰撞

提交尝试的存在性及归属查询仍必须逐字符精确，不把大小写别名当作同一个可重放请求。若新键因数据库唯一索引的宽松排序规则与旧键碰撞，准备事务回滚后先查精确键；仅当精确键不存在而数据库排序规则能命中旧键时，返回 `PAYMENT-1069`，不读取旧支付结果或再次调用渠道。其他唯一键冲突和查询失败仍保留原脱敏事务错误；此处不让别名变成两个可同时落库的独立键。H2 `VARCHAR_IGNORECASE` 只模拟大小写碰撞，目标 MySQL 8 的真实 collation/PAD、历史数据与多实例时序仍待验收。

14.485 记录迁移前原文唯一索引的过渡行为。完成 14.486 的精确字节唯一迁移后，大小写不同的合法键可各自落库，不再依赖别名碰撞判定。

### 14.486 支付尝试幂等键按完整字节精确唯一

支付尝试保留原文键供遮罩展示和检索，同时新增由原文自动生成的 `VARBINARY(512)` 列：`CAST(idempotency_key AS BINARY(512))`。数据库唯一索引改落在生成列，原文列保留非唯一索引。合法键最多 128 个 UTF-16 字符，UTF-8 编码不会超过 512 字节；提交前拒绝控制字符和无效 Unicode。生成列以零补齐，现有合法键按完整字节精确区分，大小写别名可以属于不同支付尝试；应用存在性及归属查询仍核对原长度与二进制值，不能借用另一键的结果。查询二进制宽度由 256 增至 512，避免多字节长键同前缀被误认。

部署新服务前须在目标库执行 `2026-10-10-add-payment-attempt-exact-idempotency.sql`：先新增自动计算列，再建新唯一索引与原文非唯一索引，最后移除原文唯一索引。任何步骤失败不得继续应用上线；应核对历史生成值、索引与异常历史键，并准备维护窗口和回退方案。生成列自动覆盖历史行，不猜测或回填 14.481 的请求摘要。H2 实际执行脚本及服务双单验证不代替目标 MySQL 8 DDL、索引执行计划、历史特殊字符和多实例并发验收，正式资金与可信身份仍独立待验。

### 14.487 未显式传键时的自动幂等键长度

支付提交未传幂等键时，服务使用预付单号、支付方式、实际路由渠道生成稳定键。三者以 `|` 拼接不超过 128 字符时仍保留原格式；超过时以各字段 UTF-8 字节长度分隔后计算 SHA-256，生成 `AUTO:` 加 64 位小写十六进制摘要，保证自动键适配 128 字符列且字段边界无歧义。显式键继续按原文校验并使用，不做哈希转换；相同输入可复用原尝试，不同请求内容仍受请求摘要及归属校验约束。该规则对既有短键兼容，但超长组合在升级前可能被拒绝，不能据此推断历史存在可重放尝试。本地单元测试验证长度、稳定性和分隔边界，隔离 H2/demo 配置 64 字符预付号、64 字符路由渠道和专用模拟适配器，完成自动键落库、同内容重放及同键异内容冲突；目标 MySQL 8 和正式渠道仍待验收。

### 14.488 用户端提交摘要不可用时失败闭合

更新 14.475 与 14.478 的降级边界：浏览器 SHA-256 摘要不可用时无法计算预付单作用域；`sessionStorage` 不可访问、读取异常或写入失败时也无法证明未决键已安全保存或读取。读取、生成新提交键及显式重置均不得把未知记录当作空记录；不发送支付 POST，尽可能保留存储原值并提示核对原支付结果。此前“存储或摘要不可用时仅同页内存重试”的描述不再适用于支付提交：组件内存不能证明页面刷新前是否已有渠道副作用。若收银台已按当前支付快照确认 `SUCCESS` 或 `CLOSED`，仍沿 14.479 只读进入结果页；缺安全随机源继续失败闭合。该本地门禁不代替服务端幂等、真实渠道事实或跨标签页/会话恢复。

### 14.489 管理端异常批量动作的未决键故障门禁

更新 14.471：管理端异常批量处理在没有已知同页尝试时，若 `sessionStorage` 不可访问、读取失败、已存记录 JSON/摘要/UUID 损坏、Web Crypto 摘要不可用或提交前写入失败，不得新建幂等键或发送 POST；须保留可读取的原存储值并提示先核对处理记录。若同页内存中已有**相同完整请求内容**的尝试，则仍可沿用原键，不凭新内容静默改键。与既有批量处理流程一致，可信成功后按键清理；不同目标或内容的有效存储记录仍按新请求生成独立键。此门禁是本地浏览器安全边界，不证明可信操作人身份、跨标签页恢复或服务端写入结果。

### 14.490 管理端不同批量动作的未决键并存

更新 14.489 的单槽位限制：批量动作的完整规范请求摘要同时作为 `sessionStorage` 分区后缀；同标签页依次提交不同目标/内容而结果未明时，后一次不再覆盖前一次的摘要和 UUID，刷新后各自用原请求内容恢复原键。可信成功仅清除摘要及键均匹配的分区，不误删其他未决动作。升级前保存在单键 `payment-issue-action-attempt:v1` 的合法旧记录仍可读取、按键清理；若旧记录与同摘要新分区的键冲突则失败闭合，不猜测哪个请求已写入。旧记录损坏继续拒绝新动作；其他分区损坏不影响已知正常摘要分区。仅保存摘要和随机键，不保存异常编号、操作人或备注原文。本地测试不证明跨标签页/关闭会话、可信身份及服务端结果。

### 14.491 管理端退款申请刷新后的未决键恢复

管理端在 `POST /api/refunds/apply` 前，以规范化的支付单号、金额、退款方式、退款原因、操作人计算 SHA-256 摘要，用摘要分区保存随机 UUID 幂等键到当前标签页的 `sessionStorage`，不存储业务原文。相同完整内容在响应丢失或页面刷新后重输时读取原键；不同支付单或内容保留各自未决分区，不覆盖先前申请。可信退款响应后仅清理摘要及键匹配的当前分区，响应失败保留原键。存储不可访问、读写失败、当前分区畸形、摘要或安全随机源不可用时拒绝发送申请，不退化为时间戳或普通伪随机键。后端同键同内容仍返回原退款单的当前状态，同键异内容继续返回 `PAYMENT-1053`；本次未变更接口字段。会话外/其他标签页或存储被清除时无法凭本地数据恢复申请，需先人工核对退款事实；本地浏览器回归不代替正式资金或数据库验收。

### 14.492 用户端支付未决键读回确认

补强 14.488：首次提交前 `sessionStorage.setItem` 返回不等于已持久化，必须立刻读取同一分区并与写入的完整摘要/方式/UUID 记录作字符串完全一致比对；即使存储 API 静默忽略写入，也不得发送支付 POST。显式重置调用 `removeItem` 后同样读回，若原分区仍在，不得提示已换键。读取记录时 UUID 必须为字符串，不能让 JSON 数组通过正则隐式转型后进入请求。读取、写入、删除或安全随机源异常继续统一提示未决支付无法核实；仅可信支付终态允许原有只读跳转。此浏览器门禁不代替后端幂等或真实渠道事实。

### 14.493 管理端异常批量动作未决键写入确认

补强 14.489–14.490：没有同页已知的相同完整请求尝试时，新批量动作在保存摘要分区的 UUID 后必须读回与写入记录完全一致；`sessionStorage.setItem` 静默丢写、读回失败时不得发送 `POST /api/payment-issues/actions`。已保存记录的摘要与 UUID 必须为字符串且格式合法，不能用 JSON 数组等隐式转换结果参与重放。摘要输出长度异常、生成键缺安全随机源或返回非 UUID 时，均按未决动作无法核实反馈；旧单键兼容和同页原键重试保持原行为。本机浏览器门禁不证明跨标签页、可信操作身份或服务端提交事实。

### 14.494 管理端退款申请成功后的本地清键失败

补强 14.491：`POST /api/refunds/apply` 返回经过身份校验的退款单后，清理本地申请键必须读回确认分区已删除；清理抛错或静默保留时，页面仍展示原退款单和真实状态，但同时提示本地键未清理、勿将下次同内容请求当新退款。若分区仍为原记录且写入能力可用，则将其标记为 `confirmed` 并读回确认；后续相同规范内容即使刷新也拒绝提交，而不是重放已确认退款单。若删除与标记均不可用，无法跨刷新可靠区分已确认与未决，只能明确提示人工核对支付单、退款单和渠道事实；该限制不应用新键绕过。原响应丢失时仍保留未决键供同内容重放；本次不改服务端幂等范围与退款资金状态。

### 14.495 小程序单预付单固定支付键

小程序只有微信支付、固定来源应用、商户和终端配置，支付提交显式幂等键改为 `MINI-` 加当前规范化 `prepayOrderNo`，同一预付单重进页面与响应丢失重试不再依赖时间戳、普通随机数或页面内存。合法预付号最多 64 字符时键最多 69 字符，符合服务端 128 字符上限；旧随机键无法仅凭页面恢复，必须先读取可信收银台/支付单/渠道事实，不能将 `PREPAY_CREATED` 与未知渠道状态混用。小程序不提供手动换键；支付内容需变化时应先核对原尝试，再通过业务流程创建新预付单。服务端仍按原支付单归属和规范化请求摘要校验同键重放与异内容冲突，固定键不充当认证。此变更不代表真实微信 `wx.requestPayment` 参数、资金或生产风控已验收。

隔离 H2/demo 的小程序来源提交验证同键重放只有一条渠道尝试；保持该键只改手机号时返回 `PAYMENT-1069`，不追加尝试。该本地结果不覆盖真实微信客户端、MySQL 8 排序/事务、多实例或正式资金流转。

### 14.496 API 命令行调用端支付与退款申请键

`payment-api.mjs submit` 未显式传 `--idempotency-key` 时固定使用 `API-${prepayOrderNo}`，相同规范化预付单重新运行不会因本机时钟变化而换键；不同提交内容或访问令牌会被服务端请求摘要拒绝，不能盲目换键重试。`refund-apply` 不再按支付单与时间戳生成默认键，必须由调用方提供并在业务意图生命周期内保存 `--idempotency-key`；响应未知应同键同内容重试，确需第二笔同支付单退款须先核对原退款事实并使用新的业务意图键。旧版本带时间戳的键无法从调用端恢复，仍须以支付单、退款单及渠道事实核对。隔离 H2/demo 证明 API 来源固定支付键同内容重放仅一条尝试，同键改变客户端设备号返回 `PAYMENT-1069`；CLI 缺退款键在发请求前返回参数错误。此调用端契约不更改后端接口，也不代替真实商户认证、正式资金或目标 MySQL 8 验收。

### 14.497 API 退款申请响应方式一致性

`payment-api.mjs refund-apply` 在确认金额、支付单和状态之外，要求成功响应中的 `refundMethod` 与本次申请一致；省略 `--refund-method` 时应返回默认“原路退款”。字段缺失或变成其他退款方式均按调用端响应错误处理，不输出为可信申请结果。响应不可信时仍须保留原业务意图幂等键并核对退款单及渠道事实，不得直接换键发起第二笔。此门禁不证明真实渠道退款、目标 MySQL 8 或可信运营身份。

### 14.498 API 退款摘要与详情的原支付金额字段

后端 `RefundController` 的申请、审核、主动查单和失败重试返回 `RefundListItemDTO`，不提供 `paidAmount`；退款详情返回 `RefundDetailDTO`，含原支付金额。CLI 不再错误地要求摘要包含该字段；摘要仍核验请求身份及退款金额，若意外附带 `paidAmount` 则校验其合法性及退款金额不超过原支付金额。详情必须携带有效 `paidAmount` 且金额关系正确。仅凭摘要不能证明累计退款限额或原支付成功事实，应读取详情及可信后端记录。隔离 H2/demo 的 HTTP 退款申请测试已补充退款方式断言，受本机端口沙箱与扩展审批超时影响尚未完成本轮执行；不能将编译或服务单测等同 HTTP 验收。

### 14.499 API 退款终态时间一致性

退款申请同键重放及审核、主动查单、重试返回的摘要也包含 `terminalAt`。CLI 对所有退款命令统一校验：`SUCCESS`/`FAIL` 必须提供合法终态时间，且不得早于申请时间、成功时间或已有渠道受理时间；非终态不得提前包含终态时间。原先仅详情执行这一步，可能把缺失终态时间的成功摘要作为可信结果。该客户端响应门禁不证明正式渠道到账或历史异常数据修复。

### 14.500 API 退款未派发尝试时间语义

`RefundMapper.insertRefundAttempt` 创建 `PENDING` 尝试时 `submitted_at` 为 `NULL`；渠道前拒绝转 `FAILED` 仍无提交时间，详情查询将其显示为 `-`。`UNKNOWN` 和 `ACCEPTED` 均记录首次进入渠道调用的时间，`SUCCESS`/`FAIL` 终态尝试还应有完成时间。CLI 退款详情现在按状态分别核验：`PENDING`/`FAILED` 不要求且不接受伪造的提交时间，其余状态必须有合法 `submittedAt`；时间顺序及终态完成时间仍校验。不要将渠道前失败的尝试状态 `FAILED` 混同退款单终态 `FAIL`，也不应因此直接换申请幂等键。

### 14.501 API 显式支付提交键不能退回默认值

`payment-api.mjs submit` 仅在调用方未提供 `--idempotency-key` 参数时使用 `API-${prepayOrderNo}`。显式参数为零长度或纯空白表示调用方原键缺失，不再通过通用可选参数回退逻辑生成默认键；命令在网络请求前报错并退出码 `1`。应恢复原键、核对已提交支付与渠道事实，不能因为本地配置丢失直接重试另一键。该处理只约束 CLI 调用端，不代替服务端同键摘要和目标 MySQL 8 并发验证。

### 14.502 API 命令参数白名单与拼写错误门禁

CLI 此前接受任意 `--参数`，未被业务请求消费的参数静默丢弃；支付提交若误写 `--idempotency-ke`，就可能退回默认支付键。现在每个命令只接受自身声明的字段及通用 `--base-url`，未知或属于别的命令的参数在构建请求前拒绝。显式正确键仍照原样传递；此门禁防本地误用，不替代服务端支付幂等和正式渠道事实核查。

### 14.503 demo 退款渠道号按尝试隔离

`RefundChannelSubmitRequestDTO.refundAttemptNo` 同时是本次渠道侧商户退款请求号；退款终态失败后的合法重试会产生新尝试。`demo` 的 `LocalRefundChannelSubmitServiceImpl` 原按退款单生成 `RCH-${refundOrderId}`，新旧尝试可能共享渠道退款号，导致回调归属和历史轨迹冲突。现在按尝试生成 `RCH-${refundAttemptNo}`：同一次尝试重复调用保持同号，不同尝试分离；缺失尝试号拒绝受理，模拟响应也带尝试号以便核查。该规则仅作用于 demo 本地适配器，不更改正式 `gateway-access`/上游渠道的退款号策略或历史模拟记录；真实资金与目标 MySQL 8 未验收。

### 14.504 demo 渠道号的 Mapper 历史归属复核

在隔离 H2/MyBatis 中，用正式 `findChannelSubmitRequestByAttemptNo` 快照调用 demo 适配器，再为同一退款单的两条不同尝试分别绑定其渠道号；`findAttemptNoByChannelRefundNo` 能各自查回原尝试，不借用另一笔。此用例仅验证编号与尝试历史的 Mapper 归属，不模拟完整的退款终态失败、重试准入与回调验签；后者仍需获准的 HTTP/demo 及正式渠道联调。

### 14.505 demo 退款失败重试的服务级闭环

隔离 `WebApplicationType.NONE`、H2/demo 的真实 Spring 服务事务中，按 `RefundService.apply`→`approve` 完成本地渠道受理；第一条尝试收到按演示密钥签名的 `FAIL` 回调后进入退款终态失败。`retry` 创建并受理同一退款单的新尝试，使用不同的 `RCH-${refundAttemptNo}`；针对旧尝试的新签名 `SUCCESS` 回调以 `REFUND_CALLBACK_TRANSACTION_MISMATCH` 拒绝，不改变新尝试的处理中状态；新尝试签名成功回调将退款单及新尝试收口 `SUCCESS`，旧尝试保持 `FAIL`。两个退款提交出站事件均为 `SUCCESS`。该测试不用 HTTP 端口，也不接触真实渠道；它不证明对外 HTTP 契约、目标 MySQL 8 的事务隔离、多实例或实际到账。

### 14.506 正式 gateway-access 适配器可选请求字段

payment-core 的正式 gateway-access 支付提交/查单、退款提交/查单适配器对非空的可选字段先校验原文长度和 ISO 控制字符，再将合法纯空白规范为空。分别覆盖支付提交的客户名与请求渠道编码、支付查单的渠道交易号、退款提交的客户名与退款原因、退款查单的渠道退款号；超长纯空白不得被当作省略字段，以免已知渠道号查单意图被静默改为无号查单。该门禁发生于发出 HTTP 请求前，原业务请求对象不变。离线适配器定向覆盖四路无监听分支，不证明正式网关联通、渠道支持按平台单号查单或资金最终结果。

### 14.507 支付渠道查单结果原始与映射后字段校验

支付查单结果的可选字段在进入返回码标准化之前和标准化之后，均先检查 ISO 控制字符与原文长度，再允许合法纯空白转空值。超长纯空白渠道交易号、返回码、标准化文案等不得借空白规范化绕过容量门禁。该约束只保证本地查单结果不会静默吞掉异常渠道事实或映射结果；不证明真实渠道返回值可信、MySQL 8 持久化或支付成功。

### 14.508 同步终态退款提交的安全隔离与查单收口

正式网关退款提交代理可返回 `SUCCESS / success`；支付核心出站派发仅将 `PROCESSING / warn` 作为可直接绑定的受理事实。同步终态响应先进入 `UNKNOWN`，不把它直接当作退款到账，不允许据此换尝试号重提；调用方须按原 `refundAttemptNo` 主动查单，获取同一退款尝试的可信渠道号与状态后才收口事件和退款终态。隔离 Spring/H2 测试 Bean 验证此事务路径，但不验证渠道实际支持仅凭尝试号查询或真实资金结算。

### 14.509 App/H5/PC 提交响应未推进时保留未决键

用户端 POST 支付提交即使收到业务成功包装，也不能仅凭预付号、支付单号和金额匹配就清除未决幂等键：若响应仍为 `PREPAY_CREATED`，说明提交结果尚未得到可信的状态推进确认。App/H5/PC 共用 API 客户端现将此响应判为 `PAYMENT-CLIENT-INVALID-PAYMENT-SUBMIT`，收银台沿用既有失败后 GET 复核流程，不清除当前分区记录或开放换渠道、重置键；GET 仍为待支付时只允许相同完整请求与原键重试。此门禁不替代后端按键核对渠道受理事实，也不证明跨标签页/会话、正式渠道资金、目标 MySQL 8 或可信身份。

### 14.510 小程序提交响应未推进时禁止原生拉起

小程序提交接口即使返回字段完整的业务成功响应，`paymentStatus=PREPAY_CREATED` 也不代表微信支付已受理。API 将该状态判为 `PAYMENT-MINI-INVALID-PAYMENT-SUBMIT`，收银台在调用 `wx.requestPayment` 前再次拦截；沿用现有失败后 GET 收银台复核流程，未推进时保留确定性幂等键 `MINI-${prepayOrderNo}`，不跳转结果页、不依据可能携带的 `nativePaymentParams` 发起支付。预付单创建响应仍允许 `PREPAY_CREATED`。此处只验证本地 JS 路径，不代表微信真机、正式渠道资金或服务端受理事实。

### 14.511 小程序提交响应终态的原生支付门禁

收银台在核对当前预付单、支付单、订单及金额后，按提交响应状态决定下一步：`RISK_REVIEW / SUCCESS / CLOSED` 直接进入结果页，不能因响应附带原生参数再次调用 `wx.requestPayment`；`WAIT_CALLBACK` 才可按已有原生参数和环境门禁拉起。`PAYING` 仍为处理中，按 14.512 的未推进门禁处理；`RISK_BLOCKED / RISK_REJECTED / FAIL` 等状态不拉起、不凭附带原生参数跳转，走既有失败后 GET 收银台复核流程。此约束防止本地客户端将终态或拦截态误判为待支付；不证明真实微信回调、目标 MySQL 8 或正式渠道资金结果。

### 14.512 用户端提交响应仍为 PAYING 的未决状态

服务端准备渠道提交时可先将预付单置为 `PAYING`，该状态并非可信的渠道受理完成事实。App/H5/PC 共用 API 客户端与小程序 API 均将字段完整、业务码为 `0` 但仍为 `PAYING` 的提交响应判为状态未推进；保留原提交键并使用既有 GET 收银台复核，不把它当作可信结果清理或允许新请求键。小程序页面即使被直接传入附带原生参数的 `PAYING` 响应，也不调用 `wx.requestPayment`，只有 `WAIT_CALLBACK` 可进入原生支付路径。读回仍为 `PAYING` 时应等待后续状态推进并刷新；不得仅凭本机超时推断失败或换键。此门禁不证明渠道最终受理、真实资金、目标 MySQL 8 或多实例竞态。

### 14.513 PAYING 读回后的收银台操作提示

App/H5/PC 的 GET 收银台若读回 `PAYING`，渠道受理事实尚未就绪，主动查单资格按既有门禁禁用。提交异常读回与重新加载页面均明确提示刷新收银台等待状态推进并勿重复付款，不再引导用户点击此时不可用的主动查单；原未决键与重置禁用行为保持。状态后续进入 `WAIT_CALLBACK` 时仍按服务端事实开放原主动查单路径。此处只修正本地页面操作引导，不新增自动重试或推断资金结果。

### 14.514 服务端并发提交遇 PAYING 不返回成功

同键重放或并发提交在回读阶段若预付单/收银台/支付详情仍为 `PAYING`，且详情尚非 `WAIT_CALLBACK / SUCCESS`，返回 `PAYMENT-1016`（提交处理中），不把尚未完成渠道受理的中间态当作成功响应，也不再次下发渠道或释放已有并发令牌。已受理的待回调与成功状态继续按原渠道事实、幂等归属门禁校验。客户端保留原未决键，通过收银台读回等待状态推进；不能凭此错误码换键或断言渠道失败。Mockito 定向测试覆盖同键及并发占用，不代表目标 MySQL 8 多实例锁序、真实渠道受理与资金结果。

### 14.515 渠道调用窗口的 WAIT_CALLBACK 不等于已受理

支付提交准备事务会先将支付单持久化为 `WAIT_CALLBACK`、预付单收银台置为“支付中”、尝试置为“渠道提交中”，此时渠道交易号尚未回写。该组合在渠道调用尚未返回时不能作为可信待回调结果：同一内容与幂等键的重放返回 `PAYMENT-1016`，同键不同内容先执行指纹归属校验并返回 `PAYMENT-1069`；收银台 GET 对外投影为 `PAYING / 支付中`，不开放待回调动作。渠道受理后交易号落库，才恢复 `WAIT_CALLBACK` 回读及同键复用。隔离 H2/demo 真实事务与阻塞渠道适配器覆盖准备事务提交后的并发窗口、单次渠道调用、单条尝试、令牌保留及受理后重放；未变更底层支付单状态契约，不证明目标 MySQL 8 多实例锁、真实渠道回调/资金或生产发布。

### 14.516 渠道调用窗口 HTTP 合同回归

在 14.515 的隔离事务场景中，真实 `POST /api/payments/submit` 首次请求停在渠道适配器，同一请求的第二次 HTTP POST 返回 `PAYMENT-1016`，相同键但内容变化返回 `PAYMENT-1069`；`GET /api/payments/cashier/{prepayOrderNo}` 返回 `PAYING / 支付中`。首单渠道受理完成后，GET 和原请求重放才返回 `WAIT_CALLBACK`。此回归从 HTTP JSON 入口到正式控制器、服务及 H2 Mapper；不证明客户端断网时能收到响应，也不等于 MySQL 8 多实例、真实渠道资金或正式发布验收。

### 14.517 待受理窗口的预付复用与主动查单

支付准备事务预写 `WAIT_CALLBACK` 但渠道交易号仍为空、尝试尚为“渠道提交中”时，同订单重复 `POST /api/payments/prepay` 复用原预付单但对外返回 `PAYING / 支付中`，不可提前呈现待回调完成事实；`POST /api/payments/query` 在调用渠道及写查单快照前返回 `PAYMENT-1016`。渠道受理并回写交易号后，重复预付才恢复 `WAIT_CALLBACK`。独立 H2/demo 阻塞渠道 HTTP 回归覆盖这些动作及无查单快照写入；不允许据此推断真实渠道受理、资金成功或目标 MySQL 8 多实例事务可靠性。

### 14.518 待受理窗口的支付详情只读投影

同一预写窗口中，`GET /api/payments/{paymentOrderId}` 仍会读到底层 `WAIT_CALLBACK`；向客户端的详情响应需投影 `PAYING / info`，避免用户结果页误把尚未受理的支付当成可主动查单的待回调状态。渠道交易号落库后恢复 `WAIT_CALLBACK`，只修改详情响应，不回写支付单的状态或伪造渠道交易号。隔离 H2/demo 的真实 HTTP 并发用例覆盖受理前后；不替代目标 MySQL 8、多实例、真实渠道资金及发布验收。

### 14.519 待受理窗口的手动关单提示

当支付单已预写 `WAIT_CALLBACK`、最新尝试仍为“渠道提交中”且渠道交易号未回写时，`POST /api/payments/close` 返回 `PAYMENT-1016` 并提示等待状态推进，不再返回要求“先主动查单”的 `PAYMENT-1029`；同一窗口主动查单本来就不允许。拒绝发生在尝试未提交关单更新之前，令牌与支付单保持原状。隔离 H2/demo HTTP 并发回归覆盖该分支；正式渠道、目标 MySQL 8 多实例、真实资金与发布仍待核验。

### 14.520 三端结果页区分渠道待受理和已受理待回调

支付详情返回 `PAYING / info` 时，App/H5/PC 共用结果页使用独立“支付提交处理中”状态，说明渠道受理尚未确认、等待自动或手动刷新详情，勿重复付款或换幂等键；主动查单、关单保持禁用。详情推进至 `WAIT_CALLBACK` 后才提示“已提交到渠道”并按原事实开放查单资格。三端共用同一组件与状态映射；隔离 App 浏览器/mock 覆盖两阶段切换，不代表 H5/PC 真浏览器、真实渠道受理/资金、目标 MySQL 8 或发布验收。

### 14.521 小程序结果页区分渠道提交中和待回调

支付详情返回 `PAYING` 时，小程序结果页显示“支付提交处理中”，说明渠道受理尚未确认、可刷新详情等待推进，勿重复付款；主动查单和关单保持禁用。详情推进到 `WAIT_CALLBACK` 后才展示等待渠道回调的提示，并按原有门禁恢复主动查单。此处仅调整结果页状态文案，不变更服务端支付生命周期或操作资格；本地 JS 用例不代表微信真机、正式渠道资金、目标 MySQL 8 多实例或发布验收。

### 14.522 结果待确认与不可用操作的用户指引

服务端 `FAIL` 对应“结果待确认”，不能仅凭本地状态认定资金失败，也不能把未开放的主动查单当作用户下一步。App/H5/PC 共用结果页及小程序结果页现提示保留支付单、勿重复付款，先刷新详情，仍不明确时联系服务方核查渠道与订单事实；两端未知状态兜底同样不再建议主动查单。App/H5/PC 的 `PAYING` 清单不再附带余额支付场景“优先主动查单”建议，避免与渠道受理前的操作门禁矛盾。`WAIT_CALLBACK` 仍按原状态提示与门禁处理；没有修改后端状态、接口和资金行为。此处为本地 UI/JS 回归，不替代各终端真机、真实渠道、目标 MySQL 8 多实例或发布验收。

### 14.523 渠道提交窗口的管理端列表与客户端查单门禁

支付提交准备事务可能预写底层 `WAIT_CALLBACK`，此时最近尝试仍为“渠道提交中”且渠道交易号未回写；不能仅因状态和渠道编码存在就发起主动查单。服务端原有 `PAYMENT-1016` 拒绝保持不变；管理端列表行按原持久化状态筛选和展示，同时标记“渠道受理未确认”，本页待回调计数排除该组合，快照明确建议等待状态推进、刷新详情，不再提示先查单。管理端、App/H5/PC 与小程序本地动作门禁对该组合均禁止主动查单；收到渠道交易号后仍按原资格判断。该本地防护不变更数据库状态、筛选语义或渠道调用，不能替代真实渠道、多实例或资金验收。

### 14.524 收银台操作建议必须服从当前查单资格

App/H5/PC 共用收银台的业务说明、渠道提示、支付方式对比、倒计时警示、提交异常后的提示和 PC 占位/补救文案，原有静态“先主动查单”建议在 `PAYING` 及其他不可查单状态下可能与禁用的按钮相矛盾。现上述建议依据当前收银台或详情快照的 `canQueryPayment` 资格决定：不可查单时引导刷新收银台核对状态、勿重复付款；渠道受理且满足条件时仍保留主动查单建议。PC 二维码始终只是不可支付的联调占位。本轮不变更后端状态、支付提交、查单或资金接口。

### 14.525 待回调批量查单候选排除尚未受理的提交

准备事务预写底层 `WAIT_CALLBACK` 后，最近支付尝试仍为“渠道提交中”且渠道交易号为空的支付单不具备主动查单资格。任务中心人工补跑、自动补偿候选及其积压/到期计数使用同一 SQL 受理门禁，避免排队后反复收到服务端 `PAYMENT-1016`。若后续渠道交易号回写，候选仍须符合原有预付绑定、最小等待、退避和每渠道配额规则。这里不更改支付状态机或已受理查单行为；任务中心总览、异常中心和监控中其他原始 `WAIT_CALLBACK` 统计口径仍需单独复核，不能据本轮宣称全域受理口径已统一。

### 14.526 任务中心总览待回调计数区分渠道受理窗口

任务中心总览的 `pendingCallbackCount` 不再仅按底层 `WAIT_CALLBACK` 计数：无渠道交易号且最新支付尝试仍为“渠道提交中”的预写状态不计入待回调数量；渠道交易号回写后恢复计数。仅修正总览展示口径，不更改支付单状态或渠道提交、查单行为。长期未受理的提交仍须由异常中心与监控排查，不应为了消除误报而隐藏运营可见性；这些其他口径尚未统一，也未在目标 MySQL 8 多实例、正式渠道资金或生产环境验收。

### 14.527 监控区分渠道受理未确认与已受理待回调

支付监控将底层 `WAIT_CALLBACK` 且无渠道交易号、最新支付尝试仍为“渠道提交中”的支付单列为 `CHANNEL_SUBMIT_UNCONFIRMED` 告警，不再纳入 `WAIT_CALLBACK_TIMEOUT`；监控摘要的告警类型数分别计数，混合场景保留两类告警。渠道指标新增 `unacceptedCount`，`pendingCount` 仅计已受理待回调，页面分别显示两类数量并按受理事实给出核查建议；未受理时不得引导主动查单或重复提交。摘要 `pendingPaymentCount` 仍为原有 `PREPAY_CREATED` 与底层 `WAIT_CALLBACK` 合计，并非已受理待回调专属计数。长期未受理提交保持运营可见，但异常中心独立的 `待回调未收口` 类型及建议仍待单独收口；本地 H2/前端验证不代表目标 MySQL 8 多实例、真实渠道资金或生产验收。

### 14.528 异常中心未受理提交的核查建议

异常中心保留既有 `ISSUE-WAIT-{paymentOrderId}` 编号、`待回调未收口` 分类、P1 优先级与筛选/值班归属，不静默删除渠道长时间未受理的支付单；当最新尝试为“渠道提交中”且无渠道交易号时，列表、详情及责任组提示明确标记“渠道受理未确认”，引导核对渠道受理结果与提交日志、等待状态推进，勿主动查单或重复提交。渠道交易号回写后恢复原待回调提示。此轮只修异常中心聚合 SQL 的运营建议，不更改服务端动作门禁、告警生成/派发模板或支付状态；分类名称与部分任务中心告警口径仍需进一步审查，不能视作全域统一。

### 14.529 超时异常源告警提示渠道未受理事实

异常 SLA 生成候选仍保留超时未受理支付的 `ISSUE-WAIT-{paymentOrderId}`、P1 候选、值班路由与去重规则，不因渠道尚无受理号而隐藏运营告警。无渠道交易号且最新尝试仍“渠道提交中”时，源告警 `alertContent` 改为说明“渠道受理未确认”，要求核对渠道受理与提交日志、勿主动查单或重复提交；渠道交易号回写后恢复原通用超时文案。此处只更改新生成源告警内容，既有告警日志不回写；升级通知是否沿用原告警内容取决于原记录，外部供应商自定义模板是否展示 `alertContent` 仍需联调确认，不代表已完成真实告警派发或资金验收。

### 14.530 未受理源告警派发内容防止模板覆盖安全指引

可配置的 IM/SMS/邮件供应商模板可能不包含 `{{alertContent}}`，甚至以原“待回调未收口”类型拼出错误的主动查单建议。派发服务识别 `ISSUE-WAIT-`、既有异常类型与源内容同时包含“渠道受理未确认”“勿主动查单”的新源告警时，发送给通知器的渲染内容直接采用源 `alertContent`，不允许自定义模板覆盖受理事实与操作门禁；其他告警继续按原模板渲染。只在本地 mock 通知器验证渲染结果，不改路由、重试、回执或真实供应商配置；已生成的历史源告警若缺少受理提示不会被回填，正式派发仍待联调。

### 14.531 管理端支付详情处理建议服从渠道查单门禁

支付单详情可能仍展示持久化 `WAIT_CALLBACK`，但最近尝试为“渠道提交中”且无渠道交易号时不具备主动查单资格。详情状态新增“渠道受理未确认”标记，查单动作说明与处理建议明确要求核对最近尝试和提交日志、等待状态推进，勿主动查单或重复提交；无其他可查单提交事实时建议刷新详情核对渠道受理。渠道已受理且满足原查单资格时才提示可主动查单。仅改管理端本地投影与说明，不改支付状态、服务端门禁或资金事实；本机 Chrome/mock 已覆盖待受理到受理后刷新切换与零查单 POST，但不代替正式渠道、目标 MySQL 8 或发布验收。

### 14.532 管理端支付记录详情建议遵循查单资格

支付记录详情使用支付记录本身的支付状态、最近尝试、外部交易流水和支付渠道判断查单资格。底层 `WAIT_CALLBACK`（记录展示为“支付中”）且最近尝试“渠道提交中”、外部交易流水未回写时，状态标记“渠道受理未确认”，建议核对提交日志、等待状态推进，勿主动查单或重复提交；无回调轨迹不再误导先排查回调。外部流水已回写且其余门禁满足时恢复查单建议；缺少可查单事实时引导先刷新与核对受理。异常成功事实或预付归属仍禁止查单。本地行为测试与构建通过，不代表正式后端、真实渠道、目标 MySQL 8 或生产验收。

### 14.533 管理端支付记录列表展示渠道受理事实

支付记录列表按本行的支付状态、最近尝试与外部交易流水，将“支付中”且尝试仍“渠道提交中”、无渠道流水的记录标记“渠道受理未确认”。选中快照展示最近尝试，并将一般成功后复盘场景替换为核对提交日志、等待状态推进及“勿主动查单或重复提交”；有受理号或其他状态不显示该标记。仅修改管理端展示和本地建议，不更改记录状态、导出、资金或服务端查单门禁；列表视图静态绑定测试与构建通过，真浏览器/正式后端及目标 MySQL 8 仍待验收。

### 14.534 支付记录渠道受理提示的本机浏览器回归

本机 headless Chrome + 回环 mock 在支付记录列表与详情验证预写“支付中”、最近尝试“渠道提交中”、无外部渠道流水时的受理未确认标记和安全建议；在详情点击禁用的查单按钮无 POST，同页刷新模拟渠道流水回写后标记消失、查单资格及建议恢复，整个两阶段测试无查单 POST。该回归只证明浏览器渲染与本地动作门禁，不是正式后端或渠道提交/查单验收；目标 MySQL 8 多实例、真实资金与发布仍待验证。

### 14.535 支付请求预写渠道提交中的运营查询口径

`GET /api/payment-requests/overview` 的 `processingRequestCount` 包含“处理中”“待人工确认”及预写的“渠道提交中”；筛选 `requestStatus=处理中`（兼容“请求已发起”别名）对列表、总数与 CSV 导出使用相同集合。直接筛选“渠道提交中”可精确定位受理未确认请求，后续请求推进至渠道待回调状态时从处理中迁出。管理端列表、选中快照与唯一详情标记受理未确认，提示核对提交日志、等待状态推进，勿主动查单或重复提交；API 校验接受这三类既有请求状态及对应样式，同时继续拒绝未知或不一致状态。这是支付尝试和运营投影口径，不改变支付单状态、渠道受理或服务端查单资格。H2 和本地前端验证不等于正式 MySQL 8 多实例、真实渠道或资金验收。

### 14.536 支付请求待回调实际写入状态与筛选别名

支付主流程实际写入支付尝试 `attempt_status=待回调`，历史/查询展示使用的“等待回调”也保留兼容；总览 `waitingCallbackRequestCount` 同时统计两者，`requestStatus=等待回调` 的列表、总数与 CSV 导出返回两种原始状态，直接传“待回调”在服务层归一为该筛选组。管理端列表与唯一详情校验接受“待回调 / warn”，仍拒绝类型冲突或未知状态。该修复只影响支付请求运营查询与展示，不改变支付单状态或渠道事实。

### 14.537 支付请求待回调别名的本机 HTTP 契约验证

H2 演示数据中历史“等待回调”请求经本地更新为支付主流程实际写入的“待回调”后，通过真实管理端 HTTP 接口验证：按“等待回调”筛选的总览、列表和 CSV 仍返回该笔，详情保留原始“待回调 / warn”；处理中计数不包含该笔。此测试只读 HTTP 查询并更新独立内存数据库夹具，不请求正式渠道、不改变真实资金；无法替代目标 MySQL 8 多实例或生产发布验收。

### 14.538 渠道提交中至待回调的请求诊断 HTTP 阶段契约

在隔离 H2/demo 库把同一尝试的状态先设为“渠道提交中”，再推进为“待回调”：第一阶段 `requestStatus=处理中` 的总览、列表与 CSV 包含该请求，唯一详情保留原始“渠道提交中 / warn”，等待回调筛选不包含；第二阶段 `requestStatus=等待回调` 包含原始“待回调 / warn”，处理中筛选不包含。该回归验证管理端 HTTP 查询投影，不是实际渠道提交或受理测试，也不替代目标 MySQL 8 多实例及生产资金验收。

### 14.539 支付提交成功响应后的本机未决键清理门禁

App/H5/PC 可信支付提交响应后，仅在当前预付单的存储记录仍属于本次摘要和幂等 UUID 且 `sessionStorage` 删除读回确认为空时清除内存未决键并进入结果页。删除无效、存储故障或键被替换时提示先刷新核对原支付结果，原存储记录不被误认作已清理；不自动以新键重复提交。该前端门禁不替代服务端幂等、真实渠道结果核验或跨标签页/会话协调。

### 14.540 支付未决键删除失效的浏览器动作契约

本机 Chrome/mock 回归对 `sessionStorage.removeItem` 的支付提交键注入静默无效：即使 `/api/payments/submit` 返回可信待回调，收银台也不能默默清空内存键并跳转，而应显示本机清理失败、锁定再次提交和重置；刷新后按待回调状态展示，不能额外发送支付 POST。注入只覆盖本地浏览器与 mock 响应，不等同正式渠道或真实资金验收。

### 14.541 支付请求运营投影的九服务状态收口契约

在隔离九服务 H2/demo 支付链中，同键支付提交重放后按 `paymentOrderId` 与 `requestStatus=处理中` 查询 `/api/payment-requests` 和 `/overview`，应仅有一笔原始“处理中 / info”请求，唯一详情的请求号和状态必须一致。支付回调或受控网关主动查单成功后，按 `requestStatus=成功` 查询应仍为同一请求号且原始状态为“成功 / success”，处理中筛选归零；不能因支付单已经成功而继续展示旧的处理中请求。此联调不替代真实渠道或目标 MySQL 8 多实例验收。

### 14.542 同预付单不同提交键在渠道受理前后的门禁

支付提交已预写且渠道受理尚未确认时，同键同内容重试返回 `PAYMENT-1016`，同键改内容或不同键同内容返回 `PAYMENT-1069`，均不再次调用渠道或创建支付尝试。渠道正式受理并落库后，不同键同内容重试可只读复用原有 `WAIT_CALLBACK` 事实，仍不得再次出站。本机 HTTP/H2 阻塞渠道验证只证明单实例服务端语义；客户端跨标签协调、MySQL 8 多实例与正式渠道资金需另行验收。

### 14.543 异常批量动作可信成功响应后的清键门禁

管理端 `/api/payment-issues/actions` 已返回可信成功后，只有当前摘要及 UUID 的会话未决键已删除或读回确认为空，页面才能报告成功；删除无效但存储可写时保存 `confirmed=true` 的不可重放标记，页面报告“响应已成功但本机清理失败”，同页及刷新后的相同动作均不再 POST。存储读写均失败时只能保持同页内存锁并要求人工核对，不能宣称跨会话恢复已保证。此客户端门禁不替代后端事务幂等、可信运营身份或正式环境验证。

### 14.544 用户支付可信响应后的已确认提交键契约

App/H5/PC 支付提交响应与当前支付单核对成功后，先保留当前页已确认状态，再删除本次摘要/预付单/UUID 对应的 `sessionStorage` 未决键；若删除后仍留存且内容未变，写入并读回 `confirmed=true`。已确认键允许读取收银台与主动查单，但不得再提交支付或重置为新键；当前页删写均失败也必须保留内存锁。未经可信成功响应的未决键仍可沿原内容、原键恢复，不能提前标为已确认。全页刷新且浏览器存储同时无法删除与写入时，只能人工核对支付事实，不能宣称跨会话保证。

### 14.545 未决支付提交与本地关单互斥契约

当前预付单保有未决支付提交键时，App/H5/PC 收银台不得仅凭 GET 的“待支付”、无渠道号或无尝试状态开放本地关单；已确认但清键失败的标记与尚待核对/同键重放的记录均适用。刷新收银台和满足真实渠道事实的主动查单不受此本地互斥限制。服务端仍须独立依据事务内支付尝试、令牌和渠道事实阻止竞态关单；本地按钮门禁不能替代它。

### 14.546 活跃提交令牌期间手工与超时关单拒绝

即使支付单尚显示 `PREPAY_CREATED`/可关的风控状态、无渠道流水和支付尝试，只要同一精确支付单号仍关联 `ACTIVE` 提交令牌，手工关单 `POST /api/payments/close` 与超时关单的原子状态更新均不得关闭支付单或预付单；必须先核实令牌及渠道事实，不能仅凭 TTL 过期自动判断安全。令牌已释放且其他条件允许时保持原关单资格。H2 Mapper 回归仅验证 SQL 层异常历史态，不替代目标 MySQL 8 多实例事务隔离或生产资金验收。

### 14.547 超时关单候选与活跃提交令牌的调度契约

`findExpiredPayments` 不返回精确支付单号关联活跃提交令牌的过期候选，避免这类无法更新的单长期占据有限批次。过时重试清理同步移除这类候选的重试标记，令牌释放后按正常到期条件重新入队；不同大小写但非同一精确归属的历史别名令牌不阻塞本单。关单原子更新仍是最后门禁，不能只凭候选排除推断没有并发变化。

### 14.548 活跃提交令牌的手工关单失败反馈

`POST /api/payments/close` 原子更新未影响一行时，先读回支付单终态并按既有契约返回终态结果；非终态且精确支付单号仍有 `ACTIVE` 提交令牌时返回 `PAYMENT-1016`，提示刷新结果、核对原支付事实，不建议在缺少渠道受理事实时主动查单。没有活跃令牌且其他关单条件不满足仍使用原 `PAYMENT-1029`。查询仅用于失败后的错误归因，不替代关单 SQL 原子门禁。

### 14.549 小程序收银台隐藏与恢复期间的支付响应门禁

小程序收银台隐藏时失效本轮 GET/提交响应和原生支付回调，停止倒计时并禁用旧快照操作；重新显示后按原预付单 GET 收银台核对身份与状态。原提交未结束时，即使读回仍为待支付也不得重开按钮；旧请求结束后再次只读核对。旧响应不能重新拉起原生支付或覆盖读回状态，原生支付回调迟到不能重复跳转；已进入结果页跳转时倒计时也不得重开提交。恢复过程不自动重发支付 POST，原预付单的 `MINI-${prepayOrderNo}` 幂等键保持不变。页面模拟回归不证明微信开发者工具、真机生命周期、正式渠道资金或多实例后端验收。

结果页 `wx.redirectTo` 失败时当前收银台保持待核查、禁用提交，提示手动刷新；仅在原支付身份的重新读回后重试结果页导航，不能将跳转失败当成支付未受理。旧可见周期的异步失败回调不得覆盖新一轮读回状态。

### 14.550 超时关单积压与活跃令牌阻断分区

任务中心 `expiredPaymentCount` 保留全部满足过期、唯一预付绑定、无渠道流水/支付尝试等事实条件的待关单支付；`dueExpiredPaymentCount` 与 `backingOffExpiredPaymentCount` 只统计未被同一精确支付单号 `ACTIVE` 提交令牌阻断的可处理记录，新增 `blockedExpiredPaymentCount` 单列活跃令牌阻断数。阻断项即使令牌 TTL 已过也不能自动释放或关单，`PAYMENT_EXPIRY` 重点告警提升为 `P1` 并提示人工核对原支付和渠道事实；管理端展示总量、可执行、退避与阻断，执行按钮仍只使用可执行数。大小写不同的历史令牌别名不能阻断原支付单。计数属于读取时点的运营快照，不替代关单原子更新或目标 MySQL 8 多实例验收。

### 14.551 超时支付令牌阻断只读明细

`GET /api/payment-task-center/blocked-expired-payments?pageNo=1&pageSize=10` 返回 `{ items, total, pageNo, pageSize }`，每条仅包含 `paymentOrderId / prepayOrderNo / orderNo / expiresAt`；页码规范化至 `1..1000`、每页 `1..100`。列表与 `blockedExpiredPaymentCount` 使用同一过期、唯一预付绑定、无尝试、精确支付单号活跃令牌资格，按过期时间、支付单号和主键稳定排序；不暴露令牌原文、幂等键或 IP，也不提供释放或关单动作。计数和分页分两次读取，若期间令牌状态变化，页内总数可能与当次列表不一致；运营页刷新总览后必须重新加载明细，单行仅跳转支付详情核对，不以列表结果推断原渠道支付事实。

### 14.552 过期活跃提交令牌历史盘点

`GET /api/payment-task-center/expired-active-submit-tokens?pageNo=1&pageSize=10` 只读返回 `{ items, total, pageNo, pageSize }`，每条仅含 `paymentOrderId / prepayOrderNo / sourceAppId / expireAt`，不返回占用幂等键、终端、客户端 IP 或令牌原文。页码/页大小分别规范化至 `1..1000`、`1..100`。查询筛选当前状态按系统活跃语义归一为 `ACTIVE`、但令牌 TTL 已过的行，不关联支付单或预付单，因此也能盘点孤儿、终态及不在超时关单候选中的历史令牌；按过期时间和令牌主键排序。管理端手动读取，刷新总览或执行任务后旧盘点失效；行仅可跳转支付详情尝试核对。过期不等于渠道未受理，不能仅据此释放令牌、关闭支付或重发渠道提交；计数和列表不是事务快照，正式处置仍需可信身份、渠道与资金事实复核及审批。

### 14.553 过期活跃提交令牌总览告警

`GET /api/payment-task-center/overview` 新增 `expiredActiveSubmitTokenCount`，按历史令牌盘点同一状态和 TTL 口径计数，不要求属于 `expiredPaymentCount` 或 `blockedExpiredPaymentCount`：孤儿令牌或终态支付关联令牌同样纳入。数量非零时重点告警 `PAYMENT_SUBMIT_TOKEN_EXPIRY` 为 `P1`，零时为 `P3`；告警仅引导管理端读取只读盘点，提示核对原支付与渠道事实，不把 TTL 过期视为释放、关单或渠道重发的充分条件。多项总览指标分别读取，实时状态变化下并非同一事务快照。

### 14.554 支付提交令牌再占用的状态与归属门禁

支付提交准备阶段读到同一预付单/来源应用的令牌后，先核对返回的预付单号、支付单号和来源应用均与本次请求精确一致，否则返回 `PAYMENT_SUBMIT_CONCURRENCY_BLOCKED`，不写支付尝试也不触达渠道。无已落库可信同键支付尝试时，`ACTIVE` 令牌即使占用幂等键相同也不可沿用；已 `RELEASED` 的令牌即使幂等键相同，也必须经原子条件更新重新占用。更新 SQL 要求预付单、来源应用、支付单号二进制精确归属及状态明确为 `RELEASED`；`ACTIVE` 无论 TTL 是否已过、大小写或空格变体均不可由另一请求接管。历史异常令牌继续只读盘点、人工核对渠道事实；测试中的显式状态变更仅模拟受控事实确认，不代表生产环境可以未经审批释放。目标 MySQL 8 多实例锁序、真实渠道回调/资金和可信身份仍需验收。

### 14.555 可信终态收口时活跃令牌状态变体的释放

`releaseSubmitConcurrencyToken` 保留支付单号长度及二进制精确归属，并与读侧一致按 `UPPER(TRIM(token_status)) = 'ACTIVE'` 判定可释放状态。此前带空格的大小写变体可被任务中心计为活跃，却无法随可信成功回调、主动查单成功或已收口终态释放，形成虚假的历史积压；现仅在已有可信终态收口路径调用时更新为 `RELEASED`。TTL 已过不触发该更新，不新增自动释放或人工无审批入口；不同支付单号别名仍不能释放原令牌。生产目标库的异常状态分布、多实例竞态与真实渠道资金事实仍待验收。

### 14.556 无落库尝试时同键活跃令牌禁止重新提交

支付提交先依据精确幂等键查询已落库支付尝试：存在且归属、指纹可信时复用当前状态，不重复触达渠道。仅当没有对应支付尝试才进入预写准备；此时无论请求是否与令牌持有人使用同一幂等键、令牌 TTL 是否已过，只要精确预付/支付/来源归属的令牌仍为活跃状态，就返回 `PAYMENT_SUBMIT_CONCURRENCY_BLOCKED`，提示刷新并核对原支付及渠道事实，不写新支付尝试或下发渠道。因并发隔离窗口导致本事务内尚未看到另一请求提交的同键尝试时，回滚后再读一次该键；若新出现尝试但内容/归属冲突，仍返回 `PAYMENT_SUBMIT_IDEMPOTENCY_KEY_CONFLICT`，若匹配也不在此处重新触达渠道。原有同键 `ACTIVE` 直接放行缺少已落库尝试这一事实前提；明确 `RELEASED` 的令牌仍按 14.554 原子重新占用。此门禁不意味着可以根据超时自动释放令牌；历史异常处置须有可信资金事实与审批。

### 14.557 支付提交令牌内部读取最小化

`findSubmitConcurrencyToken` 仅向服务层返回预付单号、支付单号、来源应用和令牌状态，供精确归属及活跃状态判断；不再查询或装入占用幂等键、终端、客户端 IP 和过期时间。令牌表仍保存原占用字段，原子插入、再占用和只读过期盘点的存储及查询口径不变。是否可重放由已落库支付尝试的精确幂等键、归属和请求指纹决定，不能凭令牌持有人字段放行。该调整不改变对外接口，也不构成真实渠道或目标 MySQL 8 多实例验收。

### 14.558 首次令牌插入的唯一键竞争

支付提交准备事务中，精确读取未发现令牌后，另一请求可能先插入同一预付单/来源应用范围的令牌；数据库唯一约束拒绝本次插入时，返回 `PAYMENT_SUBMIT_CONCURRENCY_BLOCKED`，要求刷新并核对原支付与渠道事实，不向客户端泄露数据库冲突详情，不重试插入或触达渠道。事务回滚后，原有同键支付尝试重查仍优先确认归属和请求指纹：新落库尝试如发生冲突，返回 `PAYMENT_SUBMIT_IDEMPOTENCY_KEY_CONFLICT`。非唯一键异常及插入行数异常不伪装为令牌竞争。此处理不以 TTL 自动接管活跃令牌；目标 MySQL 8 多实例实际竞争及真实渠道资金仍需验收。

H2 集成回归使用大小写折叠的令牌唯一键列模拟目标库的宽松唯一范围：精确读取不匹配历史大小写别名令牌，但本次插入受唯一索引拒绝，服务返回并发阻断，原令牌持有人不变，支付尝试和路由记录均不产生。这只验证真实数据库约束拒绝路径，不模拟目标 MySQL 8 多实例的同时写入、锁等待或生产历史数据。

### 14.559 同键已落库尝试优先于新风控决策

支付提交完成来源应用与访问令牌校验后，若精确幂等键已有支付尝试且归属、请求指纹一致，直接按当前支付/预付事实只读重放，不重新评估风控或写入新的风控状态；待渠道受理的 `PAYING` 返回 `PAYMENT_SUBMIT_IN_PROGRESS`，不再次触达渠道。初次检查未发现尝试但在风控评估期间另一事务落库同键尝试时，写入风控状态前再次核对并执行相同的归属、指纹与当前事实检查。新请求仍执行原有风控评估及非通过决策落库；预写事务内的同键复查继续作为最后门禁。此顺序不放宽来源鉴权或已落库尝试冲突检查，也不替代目标 MySQL 8 多实例验收。

### 14.560 渠道交易流水冲突后的支付提交恢复

支付渠道已返回受理流水，但结果事务因数据库唯一键失败时，服务在原事务回滚后只读核对同渠道流水是否已被另一支付单占用。确有外单占用才在独立恢复事务中**不绑定该冲突流水**，保留支付尝试的受理响应摘要，将尝试标记为“渠道流水归属冲突，待人工核查”、支付单标记危险样式、收银台标记“结果待确认”，并保持活跃提交令牌。原流水所属支付单不变；相同幂等键重放、主动查单和关单均不得再次触达渠道或将冲突流水当作本单资金事实。支付单详情投影为 `PAYING/danger`，收银台投影为 `PAYING/结果待确认`，便于用户和运营识别未决状态。

未发现外单占用的其他数据库异常仍按原有结果未知流程处理，不把所有唯一键冲突伪装为渠道流水冲突。正式库必须先完成渠道流水唯一键迁移及历史重复核账；本地 H2/demo 回归不证明真实渠道受理、目标 MySQL 8 多实例竞态或资金到账，冲突处置仍需人工核验和审批。

### 14.561 渠道流水归属冲突的运营诊断

最近支付尝试为“渠道流水归属冲突，待人工核查”时，支付单持久化 `WAIT_CALLBACK/danger`，唯一详情可投影 `PAYING/danger`，收银台 `PAYING/结果待确认`。管理端必须以精确尝试状态识别冲突，不以普通待回调或提交受理未确认提示替代；列表和详情提供人工核查渠道流水归属、提交日志的建议，禁用主动查单，禁止重复提交或绑定争议流水。支付监控单列 `CHANNEL_TRANSACTION_CONFLICT` P1 告警、渠道风险提示与异常中心建议；普通待回调数量、告警和自动/人工查单候选不包含冲突记录，逾期异常仍须通知人工处置。

管理端状态样式校验仅在冲突尝试（或异常摘要的明确冲突事实）存在时接受 `WAIT_CALLBACK/danger`、`PAYING/danger` 和记录 `支付中/danger`，普通待回调仍须 `warn`，普通处理中仍须 `info`；预付单的 `PAYING/结果待确认` 也须精确冲突尝试佐证。该规则只解决只读运营投影和误操作门禁，不能自动判定争议流水资金归属。目标 MySQL 8 唯一键迁移、历史重复流水核账、多实例和正式渠道仍待独立验收。

### 14.562 支付请求中的渠道流水冲突

最新支付尝试进入“渠道流水归属冲突，待人工核查”时，支付请求列表、CSV 导出及唯一详情保留原始请求状态与 `danger` 样式；“处理中”筛选和汇总纳入该未决请求，“等待回调”筛选及汇总不纳入；同名原始状态可独立筛选。管理端对该状态只接受 `danger`，标注渠道流水归属冲突并要求人工核对原始渠道事实、提交日志与流水归属，不提示等待自动推进、主动查单、重复提交或绑定争议流水。支付单和渠道的其他安全门禁保持不变。

### 14.563 用户端收银台的渠道流水冲突态

复用预付单返回 `PAYING/结果待确认/danger`，收银台页返回同一支付状态和危险样式，支付单详情返回 `PAYING/danger` 时，客户端仅在最新尝试精确为“渠道流水归属冲突，待人工核查”时接纳；普通 `PAYING/info` 及待回调 `WAIT_CALLBACK/warn` 的旧校验不放宽。用户端按 App/H5/PC 统一显示人工核查指引，禁止主动查单、重复付款或本地关闭；结果页把冲突与普通提交中分开，不再对人工未决态定时轮询，但仍允许用户手动刷新只读详情。前端仅提供只读提示，不能认定争议流水资金归属，也不执行退款或调账。

### 14.564 冲突流水的回调处理边界

最新支付尝试为“渠道流水归属冲突，待人工核查”时，完成验签的 `SUCCESS`、`PROCESSING`、`WAIT_CALLBACK` 和 `PAYING` 回调均不得直接绑定传入渠道流水或变更本单支付/预付/尝试状态，也不得落成成功或待回调通知及下游事件；返回未决阻断并交人工核查。服务在初始读取、成功锁单重读及更新失败重读时识别冲突，底层支付成功和回调待收口 SQL 也以最新尝试状态排除冲突，防止竞争窗口。渠道原始通知可由外围留存以供审计，但不能据此自动挪用已归属其他支付单的流水；只有经授权的人工作业和独立资金核验才能明确后续处置。

### 14.565 冲突支付尝试写保护

最新尝试的待回调状态更新与支付成功后的尝试收尾均不得覆盖“渠道流水归属冲突，待人工核查”；底层更新应影响零行，保留 `danger` 和未完成时间，调用方不能将零行解释为本单成功。该保护仅防止自动流程抹除争议证据，不决定已归属其他支付单的渠道流水或资金所有权；人工核查及目标库竞态验收仍是后续放行前提。

### 14.566 主动查单遇到渠道流水冲突

主动查单开始前及成功收口锁单重读时，若最新尝试已标记“渠道流水归属冲突，待人工核查”，应返回未决阻断，不进入普通自动推进。若冲突在查单请求途中出现，渠道返回码快照的原子更新不得覆盖该支付单；更新零行后回读冲突应返回相同阻断，不写普通查单通知、支付成功事实或下游事件。用户需联系运营核查原始渠道流水，不得凭本次查单结果自动认定争议资金归属。

### 14.567 争议流水不接受迟到的渠道提交结果

最新支付尝试已标记“渠道流水归属冲突，待人工核查”时，迟到的渠道提交结果不得再次绑定任意流水，也不得通过结果未知恢复或尝试响应写入将争议态改为普通待回调。支付单保持未绑定争议流水，尝试状态、样式及已留存原响应保持不变；自动写入影响零行，仍需运营核对渠道原始事实并受控处置，不能将迟到结果自动当作本单资金成功。

### 14.568 首次提交发现渠道流水归属冲突

渠道结果事务因唯一键失败、只读核对确认为同渠道流水已归属其他支付单，且独立恢复事务成功保留本单“渠道流水归属冲突，待人工核查”时，首次提交返回 `PAYMENT_SUBMIT_IN_PROGRESS` 和固定人工核查文案，不向用户泄露数据库异常或误报支付成功。已落库同键重放仍先校验来源应用、令牌、归属及请求指纹，再返回相同人工核查提示；其他失败或恢复不成功保持原异常，不得假装已完成争议保护。用户应暂停重复付款、主动查单和关闭，等待运营核对原始渠道事实。

### 14.569 小程序的渠道流水争议态

小程序预付单与收银台可接纳最新尝试精确为“渠道流水归属冲突，待人工核查”时的 `PAYING/结果待确认/danger`，支付详情和动作只在相同佐证下接纳 `PAYING/danger`；缺失或不精确的尝试状态不得放宽正常状态校验。首次提交返回未决错误后回读收银台，须展示人工核查提示并禁止再次提交或拉起原生支付；结果页不主动查单、关闭或自动轮询，允许用户手动刷新只读详情。该展示不能确定争议流水的真实资金归属，也不能替代受控人工核账。

### 14.570 小程序争议结果页的不可操作提示

结果页在精确冲突尝试的只读快照下，应把不可用的主动查单按钮显示为“人工核查中”；手动刷新失败而保留上次争议快照时，不得提示等待自动重试，应继续提示手动刷新和人工核账。首次渠道流水冲突业务错误须保留 `PAYMENT-1016`、人工核查文案及服务端请求号供安全回读，不得将错误本身当作支付成功或资金归属证明。

### 14.571 首次冲突响应的传输包络

`POST /api/payments/submit` 首次确认同渠道流水已归属其他支付单且恢复争议状态成功时，沿用普通支付 API 的 HTTP 200 统一包络，响应体 `code=PAYMENT-1016`、`message=渠道流水归属冲突，待人工核查`、非空 `requestId`；前端必须检查响应体 `code`，不可仅凭 HTTP 200 当作支付提交成功。渠道回调错误须返回非 2xx 的规则仅作用于 `/api/payments/callback/{channel}`，不能套用到提交接口。未确认恢复成功或其他异常仍按原错误契约处理。

### 14.572 App/H5/PC 争议结果页的人工核查引导

三端共用结果页识别最新尝试精确为“渠道流水归属冲突，待人工核查”时，不得因迟到的自动刷新计时器继续发起详情轮询；禁用主动查单和关单，并将查询按钮、恢复建议及 PC 常驻提示改为人工核账。若仅保留旧争议快照或手动详情刷新失败，须提示实际状态未经确认、勿重复付款或据旧快照认定资金归属，不得提示等待自动重试。保留手动只读刷新和联系运营渠道原始事实核查的路径；非争议状态仍按原规则自动刷新。

### 14.573 App/H5/PC 争议收银台的禁用操作标签

三端共用收银台在最新尝试精确为“渠道流水归属冲突，待人工核查”时，提交和主动查单仍须维持禁用，两个按钮均应显示“人工核查中”，不再以普通支付或查单标签暗示可以重复操作。人工核账指引和只读刷新入口保持可见；其他状态的原支付、风控复核和查单文案不变。

### 14.574 首次争议提交的前端追踪信息

App/H5/PC 在首次支付提交收到 `PAYMENT-1016` 且回读到有精确冲突尝试佐证的争议收银台后，不得用通用提示覆盖服务端业务错误；应保留原错误文案、业务码与 `requestId` 供运营核查，同时明确勿重复付款、主动查单或关闭支付单。错误响应本身和前端只读回查均不能证明争议资金归属。普通处理中与无法确认回读的既有提示和安全门禁保持不变。

### 14.575 支付成功事件派发的预付与时间事实门禁

`PAYMENT_SUCCESS` 在清分、账务或 AMQP 下游调用前，除了支付单、事件业务单号、渠道流水及成功尝试一致，还必须读回唯一且等额的预付绑定、规范的标准订单或虚拟来源标记，以及支付创建时间不晚于尝试完成时间、不晚于成功时间。任一事实缺失或漂移时不调用下游，将事件留在现有失败/死信重试流程，由运营核对原始支付及预付事实；不能仅凭成功状态补发资金事件。本地门禁不代替实时业务来源复核、目标 MySQL 8、多实例、真实渠道及下游受理事实验收。

### 14.576 支付成功事件派发实时复验业务来源

事件记录的来源标记之外，派发前还通过精确原样订单号查询当前业务订单的应收金额和已付金额：`STANDARD_ORDER` 两项均须存在；`VIRTUAL` 两项均须不存在。任何一项漂移都拒绝新的清分、账务或 AMQP 投递，交由运营核对真实订单和渠道事实；已进入 HTTP 清分受理阶段的事件不得自动倒退清分阶段。该复验与支付详情的来源存在性规则一致，但不是锁定业务订单或证明实际应收、已付、客户资料和资金事实不再变化；生产多实例与目标 MySQL 8 仍需单独验收。

### 14.577 支付成功 HTTP 事件载荷签名

`payment-core` 向清分与账务发送 `PAYMENT_SUCCESS` HTTP 请求时，必须配置 `HSP_PAYMENT_SUCCESS_HTTP_SECRET`；每次派发（含重试）生成当前 UTC Unix 秒数，写入 `X-HSP-Payment-Event-Timestamp`；以该秒数字符串、换行符 `\n` 和实际发送的原始 UTF-8 JSON body 拼接，计算 HMAC-SHA256/Base64，写入 `X-HSP-Payment-Event-Signature`。缺失或空白密钥时，发送端在首次派发或账务阶段重试前直接拒绝 HTTP 调用，将事件保留为失败待补偿；不会向 mock 或实际下游发未签名请求。两消费端使用相同密钥验证原始 body、规范十进制秒数和签名，接收时钟与请求时间相差超过 300 秒、时间戳缺失/异常、旧版仅签 body、签名缺失或正文篡改均返回 HTTP 403，不进入资金事件服务。隔离 `local-stack.sh verify payment-chain` 每轮随机生成一把仅供本轮三服务使用的临时密钥。协议不兼容旧版发送或接收端；不得在真实待派发 outbox 未排空时直接混合滚动升级，须有三方协调切换、回滚和时钟同步方案。300 秒内合法报文仍可重放，依赖业务幂等；生产还需独立密钥管理、TLS/网络隔离、轮换和目标环境验证。本时间窗只覆盖 HTTP 支付成功，不覆盖其他 HTTP 事件、AMQP 或人员身份。

### 14.578 支付成功 AMQP 事件载荷签名

`payment-core` 的 AMQP 模式必须配置 `HSP_PAYMENT_SUCCESS_AMQP_SECRET`，未配置时在发布前失败并沿用事件失败/重试规则。发送端在 Rabbit 消息转换后，对实际 `message.getBody()` 计算 HMAC-SHA256/Base64，写入 `X-HSP-Payment-Event-AMQP-Signature` 头；清分和账务消费者在解析 JSON 及调用业务服务前使用同一密钥验证。缺密钥、缺签名、签名错误或 body 被篡改时跳过业务重试，原消息发 DLQ 后 ACK；发送 DLQ 抛错则 NACK/requeue。该签名与 HTTP 独立，现仅覆盖 `PAYMENT_SUCCESS` 的 AMQP body；不认证可修改的 Rabbit 消息头、不提供重放窗口或 DLQ 持久发布确认。生产仍须配置 Rabbit ACL、TLS/网络隔离、密钥安全分发与轮换，并在目标 RabbitMQ/MySQL 8 多实例及真实资金环境验收。

### 14.579 死信任务已验签来源与定向重放

支付成功与清分生成消费者改为默认拒绝未认证 AMQP 消息后，旧死信任务重放的无签名副本不能再被下游接收。支付核心死信 intake 依照原死信路由分别使用 `HSP_PAYMENT_SUCCESS_AMQP_SECRET` 或 `HSP_CLEARING_GENERATED_AMQP_SECRET` 核验**原始字节**签名；仅当正文为可原样往返的 UTF-8 且签名匹配时持久化 `sourceSignatureVerified=true`。未签名、篡改、缺密钥和旧任务均保留只读与人工结案，但不得标记为可重放，直接重放也被服务端拒绝。已验签任务复核后，重放按持久化的原死信路由确认目标一致，对持久化的原始正文快照以**当前**对应密钥重新签名，并保留发布确认与下游幂等边界；消息头中的尝试次数不沿用。管理端显示来源验签状态，未验证时不提供“允许重放”或“执行重放”。本变更必须先执行 `database/migrations/2026-10-13-add-dead-letter-source-signature.sql`，再部署新服务；历史任务默认未验证，不可凭人工声明升级。签名仅覆盖 body，且源验签结果不是可信审批或生产 ACL/时间重放证明；对历史真实死信的补救需独立核对、不能绕过该门禁。

### 14.580 支付成功 AMQP 发布状态与路由确认

`PAYMENT_SUCCESS` 在 AMQP 模式下携带关联 `CorrelationData` 发布，并在有界时间内等待 broker publisher confirm；只有 ACK 且无 mandatory return 才将事件置为 `SUCCESS`。NACK、超时、发送异常或不可路由退回均沿用事件失败/重试/死信规则，不将消息仅进入 exchange 误记为目标队列可达。`publisher-confirm-type=correlated`、`publisher-returns=true` 与 `template.mandatory=true` 必须同时保持；超时配置 `payment.amqp.publisher-confirm-timeout-ms` 默认 5000ms、上限 60000ms。确认与未退回仅说明代理接受发布及当次路由，不能证明队列持久化、消费成功或清分入账；清分生成发布的代码级路由确认见清分系统接口文档，两个路径均尚需真实 RabbitMQ 验证。

### 14.581 死信人工重放发布路由确认

已验签、已复核的支付成功或清分生成死信定向重放使用关联 `CorrelationData`，等待 broker 肯定确认且无 mandatory return 后才标为 `REPLAYED`。发送抛错、NACK、超时、不可路由退回均以原有脱敏失败状态收口，不能把只被 exchange 接受的消息展示为已重放。沿用 `payment.amqp.publisher-confirm-timeout-ms` 的默认 5000ms、上限 60000ms，以及 correlated confirms、returns、mandatory 三项 Rabbit 配置。失败状态只表达本次未能确认路由，不排除 broker 已收到消息；下一次重放必须依赖下游业务幂等，不能凭失败标记断言未发生资金处理。该路径没有真实 RabbitMQ 正常、退回、断线或重投测试，也不能证明队列持久化和消费入账。

### 14.582 支付与退款回调签名字段分隔符

支付和退款回调的 HMAC-SHA256 / RSA2 待签名正文按既有协议以 `|` 连接字段。为避免同一签名字节被解析为不同的渠道流水号、时间戳或 nonce，启用验签时渠道编码、业务单号、状态、渠道流水号、退款尝试号及 nonce 均不得包含 `|`；缺失的业务签名字段也不再以字符串 `null` 拼入正文。违规回调按 `PAYMENT_CALLBACK_SIGNATURE_INVALID` 拒绝，不写入 nonce 或业务状态。该限制需要正式渠道在联调时核对原始回调字段约定；未改变既有签名串的字段顺序或正常字节格式。

### 14.583 退款回调错误响应的 HTTP 非确认语义

`POST /api/refunds/callback/{channel}` 与支付回调保持同一传输门禁：可信收口和幂等成功返回 HTTP 200、`code=0`；该路由下非零 `ApiResponse.code`，包括无效 JSON、字段校验、验签、防重放及内部处理失败，返回 HTTP 503 并保留原业务码与请求号。其他退款 API 仍使用既有 HTTP 200 响应体契约。路由按 MVC 最佳匹配模板判断，不靠原始路径字符串猜测渠道。此前 14.429 “仅支付回调”的范围在此扩展至退款回调；HTTP 503 仅表示本服务未确认本次通知，正式渠道 ACK 文本、重试策略和资金对账必须逐渠道联调，不能据此断言资金未发生。

### 14.584 用户收银台失效时间的业务时区

预付单/收银台的 `expiresAt` 仍返回数据库 `DATETIME` 的 `yyyy-MM-dd HH:mm:ss`，没有内嵌时区。业务部署须保证数据库会话时间与 `Asia/Shanghai` 一致；App/H5/PC 共用收银台和微信小程序将该字段按固定 `+08:00` 解析，用于展示倒计时和禁用本地提交入口，原始时间明确标注“北京时间”，不随用户设备时区漂移。非法日期按已失效处理；服务端 `expires_at` 与数据库 `NOW()` 的提交校验仍是最终裁决，客户端倒计时不证明服务器已接受支付。正式部署需核对数据库会话时区与终端时钟，设备时钟漂移和真实渠道资金仍须独立验收。

### 14.585 前端业务时间线的无时区比较

支付、退款、事件发布、告警、任务等响应中的 `yyyy-MM-dd HH:mm:ss` 是服务端业务壁钟时间，客户端不得先按设备所在时区转换再比较先后。App、小程序、API CLI 和运营管理端在原有严格日期有效性校验后，以不受设备时区和夏令时影响的 UTC 标尺比较字段先后；这个标尺只用于时间线排序，**不表示服务端时间实际发生在 UTC**，也不替代 14.584 中收银台失效倒计时的北京时间解析。目标 MySQL 会话时区和真实环境时钟仍须单独验收。

### 14.586 收银台响应时间的首尾空格容错

预付单与收银台响应的日期校验允许 `expiresAt` 首尾空格且要求去空格后仍是有效的 `yyyy-MM-dd HH:mm:ss`。App/H5/PC 共用收银台与小程序倒计时也在解析前去除首尾空格，避免 API 已接受的合法响应在客户端被误判为已失效并禁用提交；非法日历日期仍按已失效处理。该容错不延长服务端失效时间，不替代数据库 `NOW()` 的最终提交裁决。

### 14.587 支付成功事件自动投影退款来源

配置 `HSP_PAYMENT_DOWNSTREAM_REFUND_PROJECTION_URL` 指向退款中心 `POST /api/refunds/internal/payment-success` 时，payment-core 在支付成功事件下游派发过程中，从已校验支付详情生成支付单号、订单号、客户名称、支付金额、渠道和支付场景，使用独立 `HSP_PAYMENT_REFUND_PROJECTION_HTTP_SECRET` 对 `UTC Unix 秒 + 换行符 + UTF-8 JSON 原文` 签名。HTTP 模式在账务消费后、对账事实和完成阶段之前发送；AMQP 模式在 broker 发布确认后发送。对方须明确返回 HTTP 200 与业务码 `0`，否则事件保持失败可重试；相同事实重复投影由退款中心原幂等规则处理。未配置投影 URL 时不触发该下游；配置 URL 但缺少密钥时拒绝发送未签名请求。配置变更前已经处于完成阶段的历史事件不会自动补投，须按受控事实核验和补偿流程处置。真实 RabbitMQ、混版部署、历史补偿、目标 MySQL 8 多实例、可信身份与资金验收仍需独立验证。

投影回执还须包含 `data.paymentOrderId` 且与当前支付单号精确一致；仅 `code=0`、空数据或其他支付单号不能证明本次投影已受理，事件不得推进对账或完成阶段。旧版退款中心只回业务码，混版部署将失败闭合，应先验证双方接口和重试窗口再受控切换；AMQP 已确认的消息仍可能因投影失败重发，消费者必须保持幂等。

### 14.588 网关支付与退款四路响应传输门禁

payment-core 到 gateway-access 的支付提交、支付查单、退款提交和退款查单，仅当 HTTP 状态为 `200`、响应业务码为 `0` 且业务数据通过原有字段校验时才接受渠道事实。HTTP `202` 不能当作已经明确受理/查单完成，HTTP `3xx/4xx/5xx` 即使携带 `code=0` 和成功形态的数据也不得收口支付或退款；空传输响应同样拒绝。支付提交失败沿既有结果待确认引导先查单再重试；退款提交因为请求可能已到达上游，按 `resultUnknown=true` 进入既有未知结果核查路径，不因 HTTP 失败码直接重发；查单失败不修改原状态。网关当前控制器正常成功响应为 HTTP 200，后续若协议需要其他 2xx，须联合变更并验证双方语义。该门禁不证明真实网关、渠道资金或多实例恢复已验收。

### 14.590 退款查单结果必须归属当前尝试

正式 gateway-access 退款查单上游与网关代理回执均须包含原样的 `refundOrderId / refundAttemptNo`，payment-core 必须与当前查询请求中的平台退款单和单轮商户退款请求号精确匹配，并继续校验已知渠道退款号。未知受理号首次查询时不能只凭状态和新渠道退款号自动绑定；缺失或不匹配的身份回执按查单失败保留未决状态，不能据此判定退款成功、失败或可重提。旧版只回状态和渠道号的上游/网关会失败闭合，须协调混版切换。该门禁不替代真实渠道原生协议、签名验签、多实例并发及实际到账核验。

### 14.591 退款提交受理回执必须归属当前尝试

正式网关退款提交上游及 gateway-access 回执也必须回显原样的 `refundOrderId / refundAttemptNo`，并分别与本轮标准化请求精确匹配。payment-core 不接受仅有 `PROCESSING / warn` 和渠道退款号的无身份回执；缺失或错配按 `resultUnknown=true` 保留原尝试和出站事件待核查，不绑定渠道号、不盲目重发。网关对上游错配返回 `GATEWAY_UPSTREAM_INVALID_RESPONSE`、审计 `UNKNOWN`。只能按原尝试号取得可信查单结果后收口，不能把错误提交回执断言为未发生退款。旧版未回显身份的上下游需要协调协议映射、混版升级；本约束不等于真实渠道资金结果或多实例验收。

### 14.592 支付查单回执订单归属

正式上游与 gateway-access 支付查单回执均须原样回显 `paymentOrderId / orderNo` 并与请求精确相同，不得由代理从请求补写。若请求携带已知渠道交易号，回执在既有字段规范化后也必须与之相同；未带渠道号的首次查单不能仅凭 `SUCCESS` 与新渠道号确认付款。缺失、串单或错渠道号在网关返回 `GATEWAY_UPSTREAM_INVALID_RESPONSE`，payment-core 返回 `PAYMENT_CHANNEL_QUERY_FAILED`，原支付状态不变，不产生成功事件。旧版上游和网关需受控映射及混版切换；本校验不替代渠道签名、MySQL 8 多实例或实际资金核验。

### 14.593 支付提交受理回执同轮归属

正式渠道集成上游和 gateway-access 支付提交回执必须原样回显 `paymentOrderId / prepayOrderNo / orderNo / idempotencyKey`，并与本轮标准化请求全部精确匹配。网关不得自行从请求补造归属字段；任一缺失或串单按 `GATEWAY_UPSTREAM_INVALID_RESPONSE`、`UNKNOWN` 审计处理。payment-core 对错配返回 `PAYMENT_CHANNEL_SUBMIT_FAILED`，不把该渠道号或终端支付参数当作本轮受理结果；因上游可能已经受理，仍按既有未知结果先查单而非盲重试。旧版仅回渠道号和状态的受控上游/网关会失败闭合，须协调协议映射与混版切换；本约束不等于真实渠道签名或到账验收。

### 14.589 支付成功下游回执的同步完成语义

支付成功事件向清分、账务、对账报告事实，以及向独立退款中心投影支付来源时，仅 HTTP `200` 加业务成功码和既有回执归属/事实校验才可推进派发阶段。即使 HTTP `202` 携带 `code=0` 与完整数据，也不能代表对方已同步写入资金或可退款事实：清分未确认时不继续账务；账务或投影未确认时不继续对账；对账未确认时不标记 HTTP 完成。返回 202 或其他非 200 状态进入原失败/重试/死信状态，重投时依赖清分、账务、退款中心和对账的原业务幂等，不能据 HTTP 错误推断下游资金未发生。四个下游当前同步成功入口返回 HTTP 200；变更 ACK 协议须双方联动。AMQP 的真实 broker 投递、超时交错、混版迁移与生产资金事实仍待独立验收。

### 14.594 主动查单渠道流水归属冲突

payment-core 对渠道查单回执中的支付单号、订单号继续要求精确匹配；当回执渠道流水号已绑定其他支付单，即使回显的订单身份匹配，也在写入查单快照和成功事实前以 `PAYMENT-1026` 拒收并留 `QUERY_FAILED` 审计。原支付单继续保持待确认状态，不能凭该流水补录成功或盲重提；后续可用原支付单主动查单核验独立的正确回执。gateway-access 可将上游响应记为成功代理，此时归属冲突由支付核心拒收。数据库唯一索引仍作为并发兜底；本地预检查不等于跨实例竞态已被完全解决，真实渠道争议流水须人工核查。

### 14.595 支付回调渠道流水归属

回调验签和本单渠道/已绑定流水核对后，payment-core 还会查回调渠道流水是否已绑定另一支付单；若已归属外单，则对 `SUCCESS / PROCESSING / WAIT_CALLBACK / PAYING` 均以 `PAYMENT-1031` 拒绝，不修改本单支付状态、预付单或成功事件。回调错误保持现有非确认 HTTP 响应，渠道须按其协议重试或由运营核查；不得把另一支付单的资金事实归给本单。数据库唯一索引仍作为并发兜底，预检查不证明多实例竞态安全或真实渠道到账。

### 14.596 支付对账事实内层载荷转义

支付成功事件送往对账系统的事实仍包含 `businessChainId`、`systemCode`、`factType`、`factNo`、`businessNo`、`payScene`、`factStatus`、`amount` 和字符串 `payload`。其中 `payload` 内层 JSON 由序列化器按 `paymentOrderId/payScene` 生成，不再直接拼接字符串；引号和反斜杠等字符能正确转义，普通场景的业务字段与签名/回执确认规则不变。此项只保证代码生成的内层 JSON 可解析，不代表历史事实已经清洗，也不替代对账系统、目标 MySQL 8、正式渠道或资金验收。

### 14.597 支付主流程 JSON 字符串转义边界

支付提交的风控决策事件等由主流程生成的 JSON 字符串字段统一按 JSON 字符串规则转义，包含低位控制字符时也保持报文可解析、解析值不丢失。此项是支付核心持久化载荷的防御性格式修复，不放宽风控适配器对原始说明的校验或改变支付准入、风控状态与渠道调用规则；既有历史载荷不自动修复。

### 14.598 演示渠道回包元数据转义

`demo` 配置下，本地支付渠道模拟适配器与本地退款提交服务的响应报文均按 JSON 规则序列化字段值。支付 `channelCode` 以及退款单号、尝试号、支付单号和渠道退款号遇到引号、反斜杠或换行时，响应仍可解析并保留原值；原字段名、幂等退款号和状态语义不变。此项只覆盖演示数据，不意味着正式渠道响应已经接通或资金已到账。

### 14.599 支付收款记录模糊筛选的字面量语义

支付收款记录列表、总数和导出对 `businessOrderNo`、历史字段 `userId`（实际客户姓名）、`paymentChannel` 均按字面量子串筛选；`%`、`_`、`!`、反斜杠不由客户端充当 SQL 通配符或转义符。服务端保留原查询字段，仅在 SQL 参数中转义 LIKE 模式并显式指定 `!` 为转义符，三种查询口径相同。此约束经 H2 MySQL 模式与本机九服务演示链验证，目标 MySQL 8 和真实渠道仍待独立验收。

### 14.600 支付请求运营筛选的字面量子串

支付请求列表、计数、CSV 导出与总览共用筛选规则：`requestNo`、`paymentOrderId`、`orderNo`、`channelCode`、`clientIp` 中的 `%`、`_`、`!`、反斜杠是查询字面量，不是 SQL 通配符或转义符；服务端保留原输入，在绑定的 LIKE 参数中转义并显式指定 `ESCAPE '!'`。详情仍使用完整请求编号精确定位。本机 H2 MySQL 模式与九服务演示链通过，目标 MySQL 8 和真实渠道另需验收。

### 14.601 支付单运营查询的字面量筛选

支付单管理列表、计数与导出对 `paymentOrderId` 和 `orderNo` 使用相同的字面量子串口径，`%`、`_`、`!`、反斜杠均不作为 SQL 通配符或转义符。服务端只在绑定的 LIKE 参数中编码特殊字符，并显式使用 `ESCAPE '!'`；原输入与支付状态、渠道及支付方式的精确筛选语义不变。经 H2 MySQL 模式与本机九服务特殊订单号链验证，目标 MySQL 8 和真实渠道仍待独立验收。

## 15. 结论

这份接口文档当前已经可以直接指导前后端联调、测试编写和后续自动化补强，是 `payment-core` 一期支付交易闭环 V1 的正式接口基线。
