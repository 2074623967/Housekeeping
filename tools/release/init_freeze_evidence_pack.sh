#!/usr/bin/env bash

set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "用法: $0 <版本号> <基线日期>"
  echo "示例: $0 draft-test-20260808 2026-08-08"
  exit 1
fi

readonly VERSION_TAG="$1"
readonly BASELINE_DATE="$2"
readonly ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
readonly EVIDENCE_DIR="$ROOT_DIR/docs/releases/evidence/$VERSION_TAG"
readonly SCREENSHOT_DIR="$EVIDENCE_DIR/screenshots"
readonly RECORD_DIR="$EVIDENCE_DIR/records"
readonly MANIFEST_FILE="$EVIDENCE_DIR/screenshot-manifest.csv"
readonly REGRESSION_FILE="$RECORD_DIR/final-regression-checklist.md"
readonly README_FILE="$EVIDENCE_DIR/README.md"

mkdir -p \
  "$SCREENSHOT_DIR/payment-core/admin-web" \
  "$SCREENSHOT_DIR/payment-core/app-web" \
  "$SCREENSHOT_DIR/payment-core/h5-web" \
  "$SCREENSHOT_DIR/payment-core/pc-web" \
  "$SCREENSHOT_DIR/clearing-system/admin-web" \
  "$SCREENSHOT_DIR/settlement-system/admin-web" \
  "$SCREENSHOT_DIR/accounting-system/admin-web" \
  "$RECORD_DIR"

cat > "$README_FILE" <<EOF
# ${VERSION_TAG} 冻结证据包

- 基线日期：\`${BASELINE_DATE}\`
- 生成时间：\`$(date '+%Y-%m-%d %H:%M:%S %z')\`
- 生成脚本：\`tools/release/init_freeze_evidence_pack.sh\`

## 目录说明

- \`screenshots/\`：页面级截图归档
- \`records/final-regression-checklist.md\`：页面 + 接口 + 后台 + 下游事实的一体化回归记录
- \`screenshot-manifest.csv\`：截图台账，记录页面、状态、样例单号、文件名和归档状态

## 使用要求

1. 截图必须来自当前 \`test\` 基线。
2. 每张截图必须能映射到页面验收矩阵中的页面与关键状态。
3. 截图文件名建议追加业务编号，例如：
   - \`payment-detail-success-PAY1785918393972.png\`
   - \`result-risk-review-PAY1785811244732.png\`
4. 完成截图后，务必同步更新 \`screenshot-manifest.csv\` 与 \`final-regression-checklist.md\`。
EOF

cat > "$MANIFEST_FILE" <<'EOF'
system,terminal,page_key,page_name,required_state_or_action,sample_biz_no_or_task_no,screenshot_file,archive_status,notes
payment-core,admin-web,dashboard,工作台,总览指标与待处理入口,,,TODO,
payment-core,admin-web,orders,订单中心,发起支付/查看支付单/查看收银台,,,TODO,
payment-core,admin-web,payment-list,支付单管理,筛选列表与状态联查,,,TODO,
payment-core,admin-web,payment-detail-wait-callback,支付单详情,WAIT_CALLBACK,,,TODO,
payment-core,admin-web,payment-detail-risk-review,支付单详情,RISK_REVIEW,,,TODO,
payment-core,admin-web,payment-detail-success,支付单详情,SUCCESS,,,TODO,
payment-core,admin-web,payment-detail-closed,支付单详情,CLOSED,,,TODO,
payment-core,admin-web,payment-flows,支付流水查询,支付提交/回调/事件/路由联查,,,TODO,
payment-core,admin-web,payment-events,支付事件列表,PAYMENT_SUCCESS 出站与处理结果,,,TODO,
payment-core,admin-web,payment-issues,支付交易异常中心,异常聚合与 SLA,,,TODO,
payment-core,admin-web,cashier-sessions,收银台会话,会话状态与支付单关联,,,TODO,
payment-core,admin-web,dead-letter-pending-review,死信补偿任务,PENDING_REVIEW,,,TODO,
payment-core,admin-web,dead-letter-ready-replay,死信补偿任务,READY_TO_REPLAY,,,TODO,
payment-core,admin-web,dead-letter-manual-resolved,死信补偿任务,MANUAL_RESOLVED,,,TODO,
payment-core,admin-web,dead-letter-replayed,死信补偿任务,REPLAYED,,,TODO,
payment-core,app-web,recharge-entry,充值业务入口,创建预付单入口,,,TODO,
payment-core,app-web,withdraw-entry,提现业务入口,创建预付单入口,,,TODO,
payment-core,app-web,transfer-entry,转账业务入口,创建预付单入口,,,TODO,
payment-core,app-web,balance-pay-entry,余额支付业务入口,创建预付单入口,,,TODO,
payment-core,app-web,cashier-prepay-created,收银台,PREPAY_CREATED,,,TODO,
payment-core,app-web,cashier-wait-callback,收银台,WAIT_CALLBACK,,,TODO,
payment-core,app-web,cashier-risk-review,收银台,RISK_REVIEW,,,TODO,
payment-core,app-web,result-success,支付结果页,SUCCESS,,,TODO,
payment-core,app-web,result-closed,支付结果页,CLOSED,,,TODO,
payment-core,app-web,result-risk-review,支付结果页,RISK_REVIEW,,,TODO,
payment-core,app-web,result-wait-callback,支付结果页,WAIT_CALLBACK,,,TODO,
payment-core,h5-web,recharge-entry,充值业务入口,创建预付单入口,,,TODO,
payment-core,h5-web,withdraw-entry,提现业务入口,创建预付单入口,,,TODO,
payment-core,h5-web,transfer-entry,转账业务入口,创建预付单入口,,,TODO,
payment-core,h5-web,balance-pay-entry,余额支付业务入口,创建预付单入口,,,TODO,
payment-core,h5-web,cashier-wait-callback,收银台,WAIT_CALLBACK,,,TODO,
payment-core,h5-web,cashier-risk-review,收银台,RISK_REVIEW,,,TODO,
payment-core,h5-web,result-success,支付结果页,SUCCESS,,,TODO,
payment-core,h5-web,result-closed,支付结果页,CLOSED,,,TODO,
payment-core,h5-web,result-risk-review,支付结果页,RISK_REVIEW,,,TODO,
payment-core,h5-web,result-wait-callback,支付结果页,WAIT_CALLBACK,,,TODO,
payment-core,pc-web,recharge-entry,充值业务入口,创建预付单入口,,,TODO,
payment-core,pc-web,withdraw-entry,提现业务入口,创建预付单入口,,,TODO,
payment-core,pc-web,transfer-entry,转账业务入口,创建预付单入口,,,TODO,
payment-core,pc-web,balance-pay-entry,余额支付业务入口,创建预付单入口,,,TODO,
payment-core,pc-web,cashier-wait-callback,收银台,WAIT_CALLBACK,,,TODO,
payment-core,pc-web,cashier-risk-review,收银台,RISK_REVIEW,,,TODO,
payment-core,pc-web,result-success,支付结果页,SUCCESS,,,TODO,
payment-core,pc-web,result-closed,支付结果页,CLOSED,,,TODO,
payment-core,pc-web,result-risk-review,支付结果页,RISK_REVIEW,,,TODO,
payment-core,pc-web,result-wait-callback,支付结果页,WAIT_CALLBACK,,,TODO,
clearing-system,admin-web,events,清分事件页,publishStatus/retryCount/replayHint,,,TODO,
settlement-system,admin-web,orders,结算单页,结算单状态与清分承接事实,,,TODO,
settlement-system,admin-web,payouts,出款批次页,出款记录与失败重试事实,,,TODO,
accounting-system,admin-web,events,账务事件页,PAYMENT_SUCCESS/CLEARING_GENERATED,,,TODO,
EOF

cat > "$REGRESSION_FILE" <<EOF
# ${VERSION_TAG} 最终人工回归归档

- 基线日期：\`${BASELINE_DATE}\`
- 归档状态：\`TODO\`

## 1. 基线信息

| 项目 | 记录 |
| --- | --- |
| Git 分支 | \`test\` |
| Git 提交号 | |
| 执行人 | |
| 复核人 | |
| 主链路样例单号 | |
| 风控样例单号 | |
| 关闭单样例单号 | |
| 补偿样例任务号 | |

## 2. 页面归档核验

| 端/系统 | 页面 | 样例编号 | 状态/动作 | 截图文件 | 结果 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| payment-core/admin-web | 订单中心 | | 发起支付/联查 | | TODO | |
| payment-core/admin-web | 支付单详情 | | WAIT_CALLBACK | | TODO | |
| payment-core/admin-web | 支付单详情 | | RISK_REVIEW | | TODO | |
| payment-core/admin-web | 支付单详情 | | SUCCESS | | TODO | |
| payment-core/admin-web | 支付单详情 | | CLOSED | | TODO | |
| payment-core/app-web | 收银台 | | WAIT_CALLBACK | | TODO | |
| payment-core/app-web | 支付结果页 | | SUCCESS | | TODO | |
| payment-core/app-web | 支付结果页 | | CLOSED | | TODO | |
| payment-core/app-web | 支付结果页 | | RISK_REVIEW | | TODO | |
| payment-core/h5-web | 收银台/结果页 | | WAIT_CALLBACK/RISK_REVIEW/SUCCESS/CLOSED | | TODO | |
| payment-core/pc-web | 收银台/结果页 | | WAIT_CALLBACK/RISK_REVIEW/SUCCESS/CLOSED | | TODO | |
| clearing-system/admin-web | 清分事件页 | | publish/replay | | TODO | |
| settlement-system/admin-web | 结算单/出款页 | | 清分承接/出款事实 | | TODO | |
| accounting-system/admin-web | 账务事件页 | | PAYMENT_SUCCESS/CLEARING_GENERATED | | TODO | |

## 3. 页面 + 接口 + 后台 + 下游事实串联

| 链路 | 样例编号 | 页面事实 | 接口事实 | 后台事实 | 下游事实 | 结果 |
| --- | --- | --- | --- | --- | --- | --- |
| 正向支付成功 | | | | | | TODO |
| 风控复核 | | | | | | TODO |
| 关闭支付单 | | | | | | TODO |
| payment.success 补偿 | | | | | | TODO |
| clearing.generated 补偿 | | | | | | TODO |

## 4. 剩余问题

1. 
2. 
3. 

## 5. 放行建议

| 结论项 | 结果 |
| --- | --- |
| 是否允许推进 \`test -> master\` | |
| 是否允许创建 \`release/*\` | |
| 仍需补什么 | |
EOF

echo "已生成冻结证据包目录: $EVIDENCE_DIR"
echo "截图台账: $MANIFEST_FILE"
echo "人工回归模板: $REGRESSION_FILE"
