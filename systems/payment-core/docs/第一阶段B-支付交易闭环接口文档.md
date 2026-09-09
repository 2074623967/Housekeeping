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
| `/api/payments/callback/{channel}` | `POST` | 模拟渠道回调 |
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
| `/api/refunds/success` | `POST` | 模拟渠道退款成功回调 |
| `/api/refunds/fail` | `POST` | 模拟渠道退款失败回调 |
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
        "clientIp": "127.0.0.1",
        "idempotencyKey": "PRE202607190002|支付宝|alipay_h5",
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
        "summary": "方式=支付宝 / 请求={\"scene\":\"H5\",\"amount\":6800.00}",
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
2. 不同流水类型会按统一字段口径返回深度排障信息，例如终端、IP、幂等键、回调类型、路由规则、下游系统、事件主题、发布状态和重试次数。
3. 前端可直接使用 `requestPayload`、`responsePayload` 展开原始报文区，无需再拼接二次接口。

导出接口：`GET /api/payment-flows/export`

1. 导出接口复用列表全部筛选参数和排序规则，不接收分页参数。
2. 导出文件名固定为 `payment-flows.csv`，以 UTF-8 BOM 返回，兼容 Excel 直接打开。
3. 导出查询严格复用当前统一流水排障台的 union SQL 口径，不再另起分页循环导出路径。
4. 导出列包含流水编号、支付单号、订单号、预付单号、流水类型、类型标签、渠道编码、终端、客户端 IP、幂等键、业务状态、状态类型、通知类型、路由规则、下游系统、事件主题、发布状态、重试次数、请求报文、响应报文、摘要、创建时间。
5. 请求报文、响应报文、摘要等文本列统一按 CSV 双引号规则转义，避免原始 JSON 中的引号破坏文件结构。

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
        "customerName": "王先生",
        "amount": "¥2000.00",
        "paymentMethod": "支付宝",
        "channelCode": "alipay_h5",
        "routeRule": "amount>1000 => alipay",
        "routeResult": "支付宝H5",
        "routeResultType": "success",
        "terminal": "H5",
        "clientIp": "127.0.0.1",
        "idempotencyKey": "PRE202607190002|支付宝|alipay_h5",
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
5. 当前已对 `clientIp / idempotencyKey / mobile / cardNo / customerName` 等敏感字段做基础脱敏，生产环境后续继续补权限分域控制和更细粒度脱敏策略。
6. 新增 `GET /api/payment-requests/overview`，复用同一组筛选条件返回总量、成功/失败/处理中/等待回调分布、涉及渠道数、涉及终端数、重复支付单请求数、缺响应报文数和最近请求时间，方便运营先看全局再下钻列表。

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

1. 当前统一聚合支付提交、支付路由、渠道回调、业务事件四类日志来源，复用统一 union SQL 口径，避免列表、导出、总览三套统计不一致。
2. 新增 `GET /api/payment-logs/overview`，在列表筛选条件不变的前提下返回日志总数、错误/告警/信息分布、涉及阶段数、涉及来源数、渠道回调错误数、业务事件告警数、回调关键字命中数和最近日志时间。
3. 前端可先使用总览接口定位风险面，再通过列表接口按支付单号、订单号、日志来源和关键字继续下钻。
4. 导出接口复用同一套筛选条件与排序规则，输出 UTF-8 BOM CSV，便于测试留档、运营复盘和研发排障。

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
  "clientIp": "127.0.0.1",
  "idempotencyKey": "PRE1752912345678|微信支付|WX_H5"
}
```

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

## 8. 模拟回调

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

生产环境开启 `HSP_PAYMENT_CALLBACK_REQUIRE_SIGNATURE=true` 后，签名原文为：

`channel|paymentOrderId|tradeStatus|channelTransactionNo|timestamp|nonce`

使用渠道独立密钥计算 HMAC-SHA256，并将结果 Base64 编码后放入 `signature`。渠道密钥优先从支付渠道配置中读取，找不到时才回退到全局环境变量。

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

### 12.4 退款动作接口

审核通过：

```http
POST /api/refunds/approve
```

模拟成功：

```http
POST /api/refunds/success
```

模拟失败：

```http
POST /api/refunds/fail
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

状态流转：

| 动作 | 原状态 | 目标状态 |
| --- | --- | --- |
| 审核通过 | `REVIEWING` | `PROCESSING` |
| 成功回调 | `PROCESSING` | `SUCCESS` |
| 失败回调 | `PROCESSING` | `FAIL` |
| 失败重试 | `FAIL` | `PROCESSING` |

实现说明：

1. 后端使用状态条件更新，避免重复点击、重复回调导致退款状态回退。
2. `SUCCESS` 会写入 `success_at`，`retry` 会清空成功时间。
3. 退款原因会落在退款单上，操作备注会进入退款操作日志，便于财务、运营、测试和研发统一复盘。
4. 当前为本地模拟闭环，真实渠道退款请求、退款回调验签、退款渠道流水和差错补偿在后续渠道网关能力中扩展。

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
```

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
7. 支付网关新增、编辑和启停已统一纳入 V1.3 完整候选复核，关闭已知直接运行态写入入口；`requester / reviewer` 仍是自声明，`identityTrustLevel` 固定为 `SELF_DECLARED`，不代表可信身份、正式审批或不可抵赖签字。候选中的证书别名和状态只是治理台账字段，证书上传/轮换动作、真实灰度发布编排、渠道探活和真实网关适配仍未落地。
8. 支付渠道当前已支持商户应用标识、证书档案、验签时间窗、退款时效和风控标签展示，新增、编辑和启停已统一纳入 V1.3 完整候选复核；后续继续补可信身份权限、专用密钥轮换、证书上传、多商户参数模板和真实探活治理。
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
| `operator` | 否 | 操作人，不传时默认等于处理人 |
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

1. 人工补跑选择 `WAIT_CALLBACK`、预付单尚未过期且达到最小等待时长的支付单，不受自动重试退避窗口限制，便于运营立即复核。
2. 单次处理数量受批次上限约束，按支付单创建时间从早到晚执行。
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

1. `payment.channel-query.gateway-access.enabled=false` 时继续使用微信、支付宝、线下银行和本地兜底模拟适配器，不改变本地开发行为。
2. 启用 `gateway-access` 后，支付核心向配置的 `query-url` 发送 `paymentOrderId / orderNo / channelCode / channelTransactionNo`，并读取统一响应中的 `data.tradeStatus / data.channelTransactionNo / data.querySource / data.channelReturnCode`。
3. 每次有效渠道查询都会写入查询事实日志；`WAIT_CALLBACK / USERPAYING / FAIL / UNKNOWN` 等非成功状态只刷新返回快照，不修改支付、订单或账单状态。
4. 渠道明确返回 `SUCCESS` 且存在渠道流水号时，通过条件更新原子抢占成功收口权；抢占成功后同步更新支付尝试、预付单、订单、账单，生成 `PAYMENT_SUCCESS` 事件，派发下游并释放提交并发令牌。
5. 已为 `SUCCESS / CLOSED` 的支付单不会被主动查单重新打开；并发或重复查单未抢到收口权时不会重复记账、生成事件或派发下游。
6. 外部网关调用失败、返回空数据或成功结果缺少渠道流水号时返回 `PAYMENT-1026`，不会以本地状态伪装成真实渠道结果。
7. `gateway-access` 与共享限流开关同时启用时，所有直接、人工补跑和自动补偿查单在调用网关前统一占用渠道时间窗配额；配额耗尽返回 `PAYMENT-1027`，不调用外部渠道，也不计入单笔自动退避失败历史。
8. 渠道提交与主动查单结果按 `channelCode + channelReturnCode` 查询启用且未归档映射，标准码、文案、处理建议、重试和人工介入口径进入支付尝试响应报文或查单通知日志，并刷新 `t_payment_order` 最近返回码快照，供后续详情稳定读取；映射读取失败采用未匹配降级，避免渠道已受理后因配置查询异常诱发重复下单。
9. 返回码标准化只提供统一运营语义和审计事实，不改变 `tradeStatus`，也不根据 `retryable` 自动重试。未知码不得推断成成功；只有渠道交易状态明确为 `SUCCESS` 时才按既有幂等流程收口。

配置项：

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `HSP_PAYMENT_CHANNEL_QUERY_GATEWAY_ENABLED` | `false` | 是否启用 gateway-access HTTP 查单 |
| `HSP_PAYMENT_CHANNEL_QUERY_GATEWAY_URL` | `http://127.0.0.1:18150/api/gateway/payment-orders/query` | 统一查单地址 |
| `HSP_PAYMENT_CHANNEL_QUERY_SUPPORTED_CHANNELS` | `wx_h5,alipay_h5,offline_bank` | 允许交给外部网关查询的渠道编码 |
| `HSP_PAYMENT_CHANNEL_QUERY_TIMEOUT_MS` | `2000` | 连接与读取超时毫秒数 |
| `HSP_PAYMENT_CHANNEL_QUERY_RATE_LIMIT_ENABLED` | `false` | 是否启用数据库共享渠道查单限流；仅在真实网关同时启用时生效 |
| `HSP_PAYMENT_CHANNEL_QUERY_RATE_LIMIT_WINDOW_SECONDS` | `60` | 渠道共享固定时间窗秒数，最小归一化为 `1` |
| `HSP_PAYMENT_CHANNEL_QUERY_RATE_LIMIT_MAX_QUERIES_PER_CHANNEL` | `100` | 单渠道每个时间窗允许占用的最大查单次数，最小归一化为 `1` |

## 14. 错误与边界说明

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
| `PAYMENT-1005` | 回调签名缺失或验签失败 |
| `PAYMENT-1006` | 回调密钥未配置 |
| `PAYMENT-1007` | 回调 `nonce` 重放 |
| `PAYMENT-1008` | 回调时间戳非法或超出时间窗 |
| `PAYMENT-1009` | 回调渠道缺失 |
| `PAYMENT-1010` | 当前没有启用的支付渠道 |
| `PAYMENT-1011` | 支付来源订单金额信息缺失 |
| `PAYMENT-1026` | 外部渠道查单失败、结果不完整或无法安全收口 |
| `PAYMENT-1027` | 当前渠道共享查单时间窗配额已耗尽 |

联调说明：

1. 页面侧接到失败响应时，应优先展示 `message`，并同时展示 `code` 与 `requestId`。
2. 用户端和后台当前都已按此口径展示错误信息，便于产品、开发、测试共享同一条问题线索。

## 15. 结论

这份接口文档当前已经可以直接指导前后端联调、测试编写和后续自动化补强，是 `payment-core` 一期支付交易闭环 V1 的正式接口基线。
