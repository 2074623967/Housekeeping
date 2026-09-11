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
| `/api/payment-requests` | `GET` | 查询支付请求管理台 |
| `/api/payment-requests/overview` | `GET` | 查询支付请求总览指标 |
| `/api/payment-logs` | `GET` | 查询支付处理日志台 |
| `/api/payment-logs/overview` | `GET` | 查询支付处理日志总览指标 |
| `/api/payment-logs/export` | `GET` | 导出支付处理日志 CSV |
| `/api/payment-records` | `GET` | 按支付维度分页查询收款记录 |
| `/api/payment-records/{paymentOrderId}` | `GET` | 查询单笔收款记录详情 |
| `/api/payment-metrics/summary` | `GET` | 查询支付成功率、成功金额和状态分布 |
| `/api/payment-events` | `GET` | 查询支付事件出站台账，支持 `FAILED_OR_DEAD_LETTER` 联合口径 |
| `/api/payment-events/overview` | `GET` | 查询支付事件出站总览指标 |
| `/api/payment-events/republish` | `POST` | 手动重发支付事件 |
| `/api/payment-issues` | `GET` | 查询支付交易异常中心列表 |
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

导出接口：`GET /api/payment-flows/export`

1. 导出接口复用列表全部筛选参数和排序规则，不接收分页参数。
2. 导出文件名固定为 `payment-flows.csv`，以 UTF-8 BOM 返回，兼容 Excel 直接打开。
3. 导出查询严格复用当前统一流水排障台的 union SQL 口径，不再另起分页循环导出路径。
4. 导出列包含流水编号、支付单号、订单号、预付单号、流水类型、类型标签、渠道编码、终端、客户端 IP、幂等键、业务状态、状态类型、通知类型、路由规则、下游系统、事件主题、发布状态、重试次数、请求报文、响应报文、摘要、创建时间。
5. 请求报文、响应报文、摘要等文本列先执行与列表一致的脱敏，再按 CSV 双引号规则转义。

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

导出接口：`GET /api/payment-routes/export`

1. 导出接口复用列表的 `paymentOrderId / orderNo / routeRule / channelCode / paymentMethod / terminal / routeResult / sortField / sortOrder`，不接收分页参数。
2. 导出文件名固定为 `payment-routes.csv`，使用 UTF-8 BOM；查询直接返回全部匹配记录，不通过分页循环拼接。
3. 导出列覆盖路由记录号、支付单号、订单号、预付单号、客户名称、支付金额、支付方式、命中渠道、路由规则、路由结果、配置快照版本、终端、客户端 IP、幂等键、请求报文、响应报文和创建时间。
4. 客户名称、客户端 IP、幂等键及 JSON 报文使用与列表相同的脱敏规则，文本按 CSV 双引号规则转义；生产仍需补导出权限、审批、水印和下载审计。

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
6. `message` 仅返回可检索的最小必要摘要：支付提交保留支付方式和请求状态，通知保留回调类型和处理状态，事件保留事件类型；不复制客户端 IP、设备、手机号、幂等键、请求/响应报文或事件载荷。需要原始事实时按日志编号和支付单号进入对应支付请求、通知或事件查询链路。
7. 当前数据最小化不代表角色权限已经落地；支付请求详情和其他原始事实接口仍需在生产接入统一身份与字段级授权。

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
1. 重发接口会沿用当前查询条件返回最新分页结果，前端无需额外再发一次查询。
2. 当前主要支撑支付成功、支付关闭、退款等出站事件的可见化与手动补偿入口。
3. 后续若接入真实消息总线，需要继续补投递批次、订阅确认和死信处理字段。

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

## 9. 创建预付单

接口：`POST /api/payments/prepay`

请求示例：

```json
{
  "orderNo": "ORD202607190003",
  "payScene": "HOME_CLEAN"
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
  "merchantNo": "MCH_HOME_APP",
  "accessToken": "runtime-injected-token",
  "terminal": "APP_WEB",
  "idempotencyKey": "PRE1752912345678|微信支付|WX_H5"
}
```

`clientIp` 请求字段仅为兼容保留，HTTP 入口不会信任该值，而是始终按 14.46 节的连接与可信代理规则覆盖。

`channelCode` 会由支付核心统一规范化为标准编码：

| 请求渠道/支付方式 | 标准渠道编码 |
| --- | --- |
| 微信、`WX_H5`、`wx_jsapi` | `wx_h5` |
| 支付宝、`ALIPAY_H5`、`alipay_h5` | `alipay_h5` |
| 银行卡、银行转账、`BANK_CARD` | `offline_bank` |

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
4. 路由执行前会校验渠道单日限额，当前按“当日已受理金额 + 本次请求金额 <= dailyLimit”判断，超限渠道不会进入本次提交。
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
4. 最近一次支付尝试信息：终端、客户端 IP、幂等键、尝试状态、请求报文、响应报文
5. 渠道路由轨迹
6. 回调轨迹
7. 事件轨迹

出参最小化规则：

1. `customerName / latestClientIp / latestIdempotencyKey / latestRequestPayload / latestResponsePayload` 统一返回脱敏副本。
2. `GET detail`、渠道回调、主动查单和关单四类返回支付详情的接口口径一致，不因动作入口不同再次暴露原始上下文。
3. 请求/响应报文按 JSON 结构递归脱敏，非法或标量 JSON 返回 `[REDACTED]`；Mapper DTO 和数据库事实不被修改。
4. 当前能力不提供敏感原文查看入口，也不代表可信身份、角色或字段级授权和生产访问审计已经完成。

## 13. 收款记录分页查询

接口：`GET /api/payment-records`

当前补充能力：

1. 已支持 `paymentStatus`、`paymentChannel` 两类运营筛选。
2. 已支持按 `createdAt / paymentAmount / paidAt` 排序。
3. 适用于统一支付记录、微信支付宝支付记录和银行卡支付记录三类列表页共用。

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
| `userId` | 否 | 用户 ID 模糊查询 |
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
    "paymentGateway": "微信支付网关",
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
  "refundAmount": 68.00,
  "refundMethod": "原路退款",
  "refundReason": "客户取消服务"
}
```

业务约束：

1. 原支付单必须存在且状态为 `SUCCESS`。
2. `refundAmount` 必须大于 `0`。
3. 同一支付单下 `REVIEWING`、`PROCESSING`、`SUCCESS` 状态退款金额累计不能超过原支付金额。
4. 创建后状态为 `REVIEWING`，等待运营或财务审核。

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

1. 回调签名原文为 `channel|refundOrderId|refundStatus|channelRefundNo|timestamp|nonce`，渠道规范化为小写；签名算法、时间窗和 nonce 防重规则与支付回调共享。
2. 后端锁定退款单并核对实际受理渠道与渠道退款号，只有 `SUCCESS/FAIL` 可从 `PROCESSING` 收口；相同终态幂等返回，相反终态返回冲突。
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

渠道编码按小写规范化。同一渠道的 `CREATE / UPDATE / STATUS_CHANGE` 共用一条 `PENDING_REVIEW` 占位。新增时密钥必填；编辑时空密钥或公钥只表示由服务端继承当前值。签名算法仅支持 `HMAC-SHA256 / RSA2`，RSA2 必须有公钥，回调地址必须使用 HTTPS，验签窗口和退款时效为正数，限额和优先级非负，路由权重为 `0-100`。

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
10. 路由规则新增、编辑、启停和回滚均提交候选快照进入自声明 maker-checker；旧的直接运行态写入路径已关闭，复核通过前运行态不变。规则编码编辑时不可变，目标与兜底渠道必须存在且不能相同。
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
    "fallbackChannelCode": "alipay_h5",
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
3. 目标渠道和非空兜底渠道必须存在于支付渠道配置中，且二者不能相同。
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

返回示例：

```json
{
  "summary": {
    "totalCount": 268,
    "successCount": 247,
    "successRate": "92.16%",
    "successAmount": "¥18,932.00",
    "pendingPaymentCount": 12,
    "failedRefundCount": 3,
    "disabledChannelCount": 1,
    "alertCount": 4
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
| `actionType` | 是 | 支持 `分派处理人 / 标记跟进中 / 标记已处理 / 补充备注` |
| `assignee` | 是 | 当前处理人 |
| `operator` | 是 | 操作人；去除首尾空白后最长 `64` 个字符，不再回退为处理人 |
| `remark` | 否 | 处理备注，不传时默认 `批量处理` |

处理说明：

1. 后端会按 `issueNo` 回查当前聚合异常，异常不存在时拒绝处理，避免对已消失的异常写入误导性动作。
2. 处理动作会写入 `t_payment_issue_action_log`，不直接修改支付单状态，避免把运营排障动作和交易状态机混在一起。
3. 当 `actionType=标记已处理` 时，后端会同步确认该异常下仍处于 `待确认` 的告警回执。
4. 接口返回刷新后的异常中心列表，前端可直接更新页面。

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
9. `pendingQueryBacklogCount`：已达到首次主动补查等待阈值且预付单仍有效的支付单数。
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

1. `GET /api/payment-records` 将 `userId / userPaymentChannelId` 中由客户名称派生的后缀替换为 `****`；查询参数仍按原始输入匹配。
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

1. `POST /api/payment-task-center/actions/close-expired-payments` 仅关闭仍为 `PREPAY_CREATED`、渠道交易号为空、不存在支付尝试且预付单已过期的支付单；更新时再次校验条件，并发提交已开始时不写关闭事件。
2. 存在支付尝试的 `WAIT_CALLBACK` 即使预付单已过期，仍计入达到等待阈值的人工和自动查单候选；自动模式继续使用查询历史指数退避、最大间隔、渠道批次配额和限流。
3. 可信成功回调或主动查单通过同一条件更新抢占成功收口权。普通 `PAYMENT_CLOSED` 不可重开；历史上带 `PAYMENT_EXPIRED_CLOSED` 事件的关闭单允许恢复为 `SUCCESS`，且只有抢占成功者记账和发布成功事件。
4. 当前接口没有实现真实渠道关闭或撤销调用，非成功渠道结果不会被本地过期任务推断为渠道已关闭；生产接入需补渠道终态码表、关闭请求幂等、关闭与成功竞态及对账规则。

### 14.30 人工关闭支付单规则

1. `POST /api/payments/close` 仅接受尚未提交渠道的支付单：状态属于 `PREPAY_CREATED / RISK_REVIEW / RISK_BLOCKED / RISK_REJECTED`，渠道交易号为空且不存在任何支付尝试。
2. 服务端使用条件更新原子抢占关闭权。条件更新失败后若最新状态为 `SUCCESS / CLOSED` 则幂等返回最新详情；其他状态返回 `PAYMENT-1029`，要求调用方先执行主动查单。
3. `PAYMENT-1029` 路径不更新预付单、不追加关闭事件且不释放提交令牌；已提交后历史误关的 `CLOSED` 单据重复关闭时同样保留令牌和可信成功恢复入口。
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
3. `terminal` 缺失时为 `UNKNOWN`；规范值仅有 `APP/H5/PC/UNKNOWN`。兼容别名 `APP_WEB -> APP`、`H5_WEB/MOBILE_WEB -> H5`、`PC_WEB -> PC`，其他值返回 `PAYMENT-1035`，不得形成新的限流、风控或审计维度。
4. 显式 `idempotencyKey` 继续按 14.38 的 `128` 字符与控制字符规则返回 `PAYMENT-1028`；其他提交字段非法统一返回 `PAYMENT-1035`。以上限制不替代部署网关的整体 JSON 请求体上限和渠道专属字段规则。

### 14.48 渠道查单结果字段契约

1. 查单适配器必须返回非空对象，`tradeStatus` 和 `querySource` 必填且最长分别为 `32/64` 字符；`channelTransactionNo/channelReturnCode` 非空时最长分别为 `128/64` 字符。所有字符串 trim 后不得含 ISO 控制字符。
2. 标准化字段 `standardizedCode/standardizedMessage/handlingSuggestion/retryable/manualInterventionRequired/mappingVersion` 非空时最长分别为 `64/256/256/16/16/32` 字符；原始结果在映射前校验，映射产物在返回前再次校验。
3. 支付服务会对最终结果防御性复验。任何空缺、超长或控制字符返回 `PAYMENT-1026`，只允许写入固定文案的查单失败日志，不得更新支付返回码快照或进入支付成功结算、事件和令牌处理。
4. 本契约限制单字段，不替代 gateway-access 和 HTTP 客户端的整体响应体上限、渠道状态枚举与真实报文协议校验。

### 14.49 支付入口标识与创建预付单字段契约

1. `GET /api/payments/{paymentOrderId}`、`GET /api/payments/cashier/{prepayOrderNo}`、`POST /api/payments/query` 和 `POST /api/payments/close` 的业务标识均必填、trim 后不超过 `64` 字符且不得包含 ISO 控制字符；非法请求返回 `PAYMENT-1036`，不进入数据库或查单限流。
2. `POST /api/payments/prepay` 的 `orderNo/payScene` 必填且分别不超过 `64/32` 字符；可选 `customerName/cashierTitle` 分别不超过 `128/128` 字符。所有非空字符串 trim 后不得包含 ISO 控制字符。
3. 可选 `amount` 一旦提供必须大于 `0`、最多 `16` 位整数和 `2` 位小数。虚拟支付仍要求客户名称和金额；标准订单只缺一项金额事实返回 `PAYMENT-1011`。
4. 标准订单剩余应付 `orderAmount - paidAmount <= 0` 时返回 `PAYMENT-1037`，不得创建账单、支付单或预付单。该校验不替代跨服务并发结算、订单锁或生产数据库隔离验证。

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

1. 异常中心按 `orderNo` 聚合标准订单下全部 `SUCCESS` 支付单的毛成功金额；仅当合计严格大于订单 `orderAmount` 时返回 `issueType=订单成功收款超额`、`severity=P1`，异常号固定为 `ISSUE-OVERPAID-{orderNo}`。
2. 异常详情关联该订单主键最大的成功支付单，责任组为“账务清结算联动组”；正常拆单合计小于或等于应收不产生异常。可信渠道成功不得为了消除异常而改写为失败。
3. 日终成功差异按支付单 `id` 顺序累计同订单成功金额；当前支付单纳入累计后超过应收时，该支付单计入差异单量，其整笔 `amount` 计入差异金额。因此 `80 + 30 > 100` 的差异金额为 `30.00`，实际净超额仍为 `10.00`。
4. 退款、调账和差错处理是后续独立事实，不从毛成功聚合中删除原异常。接口结果用于运营定位和日终阻断，不代表真实渠道流水已核对或退款已经审批执行。

### 14.57 统一支付记录成功退款字段契约

1. `GET /api/payment-records` 与 `GET /api/payment-records/{paymentOrderId}` 的 `refundCount/refundedAmount` 采用同一口径：按支付单统计状态为 `SUCCESS` 的退款单数量与金额合计。
2. `REVIEWING/PROCESSING/FAIL` 退款不计入已退次数和金额；没有成功退款时返回 `refundCount=0`、`refundedAmount=¥0.00`，不得用申请金额或处理中金额替代实际已退事实。
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

1. `GET /api/refunds/{refundOrderId}` 新增 `channelCode/channelRefundNo/channelSubmittedAt`：分别表示实际受理渠道、渠道侧退款单号和首次获得受理号时间；尚未受理时返回空值或 `-`，不得推断为失败。
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
| `PAYMENT-1026` | 外部渠道查单失败，结果为空、字段非法/不完整或无法安全收口 |
| `PAYMENT-1027` | 当前渠道共享查单时间窗配额已耗尽 |
| `PAYMENT-1028` | 支付提交最终幂等键为空、超过 128 字符或包含控制字符 |
| `PAYMENT-1029` | 支付已提交渠道或结果尚未确认，不能本地关闭，需先主动查单 |
| `PAYMENT-1030` | 回调 URL 渠道与支付单实际路由渠道不一致或支付单尚未绑定渠道 |
| `PAYMENT-1031` | 回调渠道交易号与支付单已绑定的渠道交易号不一致 |
| `PAYMENT-1032` | 渠道回调签名算法缺失或不在运行时白名单 |
| `PAYMENT-1033` | 渠道 RSA2 公钥材料存在但无法解析 |
| `PAYMENT-1034` | 渠道回调验签窗口为空/非正，或 nonce TTL 未严格覆盖完整签名有效期 |
| `PAYMENT-1035` | 支付提交请求为空，业务字段缺失、超长、含控制字符，或终端不在有限白名单 |
| `PAYMENT-1036` | 支付通用入口或预付请求为空，标识/业务字段缺失、超长、含控制字符，或金额格式越界 |
| `PAYMENT-1037` | 订单已结清或已超额支付，无需再次创建支付单 |
| `PAYMENT-1038` | 同一订单已有有效预付单，但本次支付场景、客户、金额或收银台标题不一致 |
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

1. `GET /api/payment-issues/alerts` 与 `GET /api/payment-issues/alerts/export` 新增可选参数 `receiptQueryState`；允许值为 `全部 / INITIAL_WAIT / DUE / BACKING_OFF / QUERYING / EXHAUSTED`，英文值忽略大小写，未知值返回参数错误，不能静默退化为全量查询。
2. 五类状态由服务端使用与补偿任务同源的首次等待、最大次数和 claim 超时配置计算。有效 claim 优先归为 `QUERYING`，其后依次判定 `EXHAUSTED`、`INITIAL_WAIT`、`BACKING_OFF` 和 `DUE`；筛选谓词与返回状态保持互斥，首次等待且 next time 在未来的记录仍只归为 `INITIAL_WAIT`。
3. 列表行新增 `receiptQueryState / receiptQueryCount / lastReceiptQueryAt / nextReceiptQueryAt / receiptQueryResult`；CSV 在供应商回执号后按同一顺序输出回查队列状态、次数、最近时间、下次时间和有限结果，不导出异常原文或访问凭证。
4. 任务中心发现 `exhaustedIssueAlertReceiptCount > 0` 时，“异常告警待回查”强制提升为 `P1/danger`，并跳转 `/payment-issue-alerts?receiptQueryState=EXHAUSTED`；无耗尽记录时跳转已受理且待确认列表。该升级只表示自动尝试耗尽，不表示供应商失败或最终送达。
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
2. 两个下游统一响应均按 `{code,message,data,requestId}` 解析。只有 `ResponseEntity` 非空、HTTP 2xx、响应体与 `data` 非空、`code` 原文无控制字符且去空格后严格等于字符串 `0` 时确认该次调用成功；HTTP 2xx 本身不代表业务成功。
3. 调用顺序固定为清分后账务。清分未明确成功时不得调用账务；任一调用失败均进入既有失败计数、重试或死信流程，不得标记事件发布成功。
4. AMQP 模式要求 RabbitTemplate、序列化器、exchange、routing key 和账务账号完整，目的字段最长 `255`、账号最长 `64` 个字符。消息投递确认仍不等于下游业务处理完成。
5. HTTP 顺序重试可能在“清分成功、账务失败”后重放清分，接口消费者必须以支付单号、事件号等稳定业务标识实现幂等。本地测试不证明真实送达、清分完成、账务入账或资金到账。

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
2. 每轮按 `applied_at ASC, id ASC` 选择最早的 `FAIL` 退款；超过批次的退款不改写状态，留待后续调度或人工轮次。
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
2. 默认 TTL 为 `601` 秒，因此默认最大兼容时间窗为 `300` 秒；自定义 TTL 不改变严格大于完整对称签名有效期的规则，等于 `2W` 仍拒绝。
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

## 15. 结论

这份接口文档当前已经可以直接指导前后端联调、测试编写和后续自动化补强，是 `payment-core` 一期支付交易闭环 V1 的正式接口基线。
