# payment-core 四端人工回归清单

## 1. 目的

把 `payment-core` 当前 HEAD 的四端页面回归，收敛成可执行清单，避免后续只看构建不看真实页面行为。

## 2. 当前基线

- 分支：`test`
- 当前 HEAD：`7d826ef`
- 已验证构建：
  - `app-web`
  - `h5-web`
  - `pc-web`
  - `admin-web`

## 3. 真实案例

### 3.1 风控复核案例

- 支付单：`PAY1785901653979`
- 预付单：`PRE1785901653980`
- 关键状态：
  - `RISK_REVIEW`
  - `WAIT_CALLBACK`
  - `SUCCESS`

### 3.2 MQ 成功演练案例

- 支付单：`PAY1785907738212`
- 预付单：`PRE1785907738213`
- 订单号：`MQ-DRILL-20260805-001`
- 关键状态：
  - `WAIT_CALLBACK`
  - `SUCCESS`
  - `PAYMENT_SUCCESS` outbox

### 3.3 DLQ 补偿案例

- 任务号：`DLQaba240b93ae346b7b7b9`
- 任务号：`DLQ338493c729064115a467`
- 关键状态：
  - `PENDING_REVIEW`
  - `MANUAL_RESOLVED`

## 4. 四端回归范围

### 4.1 `admin-web`

必须逐页确认：

1. 订单中心
2. 支付单管理
3. 支付单详情
4. 支付事件列表
5. 支付流水列表
6. 支付监控分析
7. 支付交易异常中心
8. 收银台会话

重点核对：

1. `WAIT_CALLBACK` 是否能直接跳到支付单详情
2. `RISK_REVIEW` 是否能正确展示待风控复核状态
3. `SUCCESS` 是否可正确跳转结果页与下游台账
4. `PENDING_REVIEW / READY_TO_REPLAY / MANUAL_RESOLVED / REPLAYED` 是否在死信任务中正确显示

### 4.2 `app-web`

必须逐页确认：

1. 充值业务入口
2. 提现业务入口
3. 转账业务入口
4. 余额支付业务入口
5. 收银台
6. 支付结果页

重点核对：

1. `WAIT_CALLBACK` 时按钮与提示是否符合“等待回调/主动查单”
2. `RISK_REVIEW` 时是否提示先完成风控复核
3. `SUCCESS` 时是否展示正确收口动作
4. `CLOSED` 时是否禁止继续模拟回调

### 4.3 `h5-web`

与 `app-web` 同步确认：

1. 页面路由是否一致
2. 收银台与结果页是否复用同一套状态映射
3. 文案与按钮禁用逻辑是否一致

### 4.4 `pc-web`

与 `app-web` 同步确认：

1. 桌面扫码区域是否正常展示
2. `WAIT_CALLBACK` 和 `SUCCESS` 是否能正确跳转结果页
3. `RISK_REVIEW` 是否不允许继续重复支付

## 5. 回归判定

通过标准：

1. 三端收银台与结果页状态显示一致
2. `WAIT_CALLBACK / RISK_REVIEW / SUCCESS / CLOSED` 都有明确页面表达
3. 死信任务页面可正确显示 `PENDING_REVIEW / MANUAL_RESOLVED / REPLAYED`
4. 四端构建与页面状态一致，无回归

未通过标准：

1. 仍把关键状态折叠成“处理中”
2. 仍允许 `RISK_REVIEW` 状态继续提交支付
3. 结果页在 `SUCCESS / CLOSED` 下还可继续模拟成功回调

## 6. 2026-08-05 页面级真实回归事实

### 6.1 回归样例

#### 既有样例

- `SUCCESS`
  - 支付单：`PAY1785901653979`
  - 预付单：`PRE1785901653980`
- `RISK_REVIEW`
  - 支付单：`PAY1785811244732`
  - 预付单：`PRE1785811244734`

#### 本轮新增样例

- `WAIT_CALLBACK` App
  - 支付单：`PAY1785915556566`
  - 预付单：`PRE1785915556567`
  - 订单号：`REG-DEFAULT-APP-WAIT-CALLBACK-20260805-001`
- `WAIT_CALLBACK` PC
  - 支付单：`PAY1785915556767`
  - 预付单：`PRE1785915556768`
  - 订单号：`REG-DEFAULT-PC-WAIT-CALLBACK-20260805-001`
- `PREPAY_CREATED`
  - 支付单：`PAY1785915100360`
  - 预付单：`PRE1785915100361`
  - 订单号：`REG-WAIT-CALLBACK-20260805-001`

### 6.2 四端回归结果

| 端 | 页面 | 样例 | 结果 | 关键事实 |
| --- | --- | --- | --- | --- |
| `app-web` | `/payment-result/PAY1785901653979` | `SUCCESS` | 通过 | 页面标题为 `支付成功`；按钮状态为：`查询最新状态` 可用、`模拟成功回调` 禁用、`关闭支付单` 禁用、`返回收银台` 可用 |
| `h5-web` | `/payment-result/PAY1785811244732` | `RISK_REVIEW` | 通过 | 页面标题为 `待风控复核`；按钮状态为：`刷新支付结果` 可用、`模拟成功回调` 禁用、`结束本次支付` 可用、`回到支付页` 可用 |
| `pc-web` | `/payment-result/PAY1785915556767` | `WAIT_CALLBACK` | 通过 | 页面标题为 `支付处理中`；按钮状态为：`刷新桌面端支付结果` 可用、`模拟成功回调` 可用、`关闭当前支付单` 可用、`返回 PC 收银台` 可用 |
| `admin-web` | `/payments/PAY1785811244732` | `RISK_REVIEW` | 通过 | 详情页展示 `RISK_REVIEW`；`主动查单` 可用、`模拟回调` 禁用、`关闭支付单` 可用；处理建议区包含“当前没有回调轨迹，需关注渠道回调地址、验签和消息落库情况” |

### 6.3 样例创建事实

1. `WAIT_CALLBACK` 新样例通过真实接口创建，而不是手工改库：
   - `POST /api/payments/prepay`
   - `POST /api/payments/submit`
2. 之所以使用 `sourceAppId=default-app` 创建本轮 `WAIT_CALLBACK` 样例，是因为：
   - `housekeeping-app-web`
   - `housekeeping-h5-web`
   - `housekeeping-pc-web`
   当前控制策略处于 `strictMode=开启` 且 `selfCheckStatus=WARN`
   - 直接提交会命中 `PAYMENT-1019`
3. 这说明当前 HEAD 的真实系统结论是：
   - 页面本身的状态映射与按钮控制逻辑已可验证
   - 但正式来源应用配置仍存在治理缺口，尚不能据此判定达到冻结发布标准

### 6.4 本轮结论

1. 当前四端已经具备真实页面级状态证据，不再只有接口和构建证据。
2. `SUCCESS / RISK_REVIEW / WAIT_CALLBACK / PREPAY_CREATED` 四类核心状态已在当前 HEAD 下得到页面验证。
3. 当前仍未达到可合并 `master` 的标准，原因不是页面不可用，而是：
   - 正式来源应用的支付控制策略自检仍是 `WARN`
   - 围绕真实生产口径来源应用的页面级回归还没有全量闭环
