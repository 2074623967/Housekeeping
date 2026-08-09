#!/usr/bin/env bash

set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "用法: $0 <回归包标识> <基线日期>"
  echo "示例: $0 test-20260809-regression 2026-08-09"
  exit 1
fi

readonly PACK_TAG="$1"
readonly BASELINE_DATE="$2"
readonly ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
readonly EVIDENCE_DIR="$ROOT_DIR/docs/releases/evidence/$PACK_TAG"
readonly RECORD_DIR="$EVIDENCE_DIR/records"
readonly API_DIR="$EVIDENCE_DIR/api"
readonly DB_DIR="$EVIDENCE_DIR/database"
readonly EVENT_DIR="$EVIDENCE_DIR/events"
readonly SCREENSHOT_DIR="$EVIDENCE_DIR/screenshots"
readonly README_FILE="$EVIDENCE_DIR/README.md"
readonly SCENARIO_FILE="$EVIDENCE_DIR/regression-scenarios.csv"
readonly SUMMARY_FILE="$RECORD_DIR/regression-summary.md"

mkdir -p \
  "$RECORD_DIR" \
  "$API_DIR" \
  "$DB_DIR" \
  "$EVENT_DIR" \
  "$SCREENSHOT_DIR/payment-core" \
  "$SCREENSHOT_DIR/clearing-system" \
  "$SCREENSHOT_DIR/settlement-system" \
  "$SCREENSHOT_DIR/accounting-system" \
  "$SCREENSHOT_DIR/reconciliation-system"

cat > "$README_FILE" <<EOF
# ${PACK_TAG} 回归证据包

- 基线日期：\`${BASELINE_DATE}\`
- 生成时间：\`$(date '+%Y-%m-%d %H:%M:%S %z')\`
- 生成脚本：\`tools/release/init_regression_evidence_pack.sh\`

## 目录说明

- \`regression-scenarios.csv\`：统一场景台账，串联 API、数据库、事件、页面和补偿/回滚证据
- \`records/regression-summary.md\`：本轮回归结论、阻断项和是否允许推进下一阶段
- \`api/\`：接口请求/响应样例或 curl 记录
- \`database/\`：数据库事实截图、SQL 输出或导出文件
- \`events/\`：事件台账、补偿任务、重试结果
- \`screenshots/\`：页面截图，按系统归档

## 使用要求

1. 所有证据必须来自当前 \`test\` 基线或明确记录的隔离环境。
2. 每个场景至少要回填 API、数据库事实、事件台账三类证据；涉及页面的场景还要补截图。
3. 所有场景统一使用 \`REG-01 ~ REG-10\` 编号，不允许新增临时口径编号。
4. 如果场景属于补偿、回放、回滚演练，必须在 \`events/\` 或 \`database/\` 中保留执行前后对比证据。
5. 完成回归后，先运行 \`tools/release/audit_regression_pack.mjs\`，再决定是否更新发布结论。
EOF

cat > "$SCENARIO_FILE" <<'EOF'
scenario_id,owner_system,scenario_name,api_status,db_status,event_status,page_status,rollback_or_compensation_status,sample_biz_no_or_task_no,api_evidence_file,db_evidence_file,event_evidence_file,page_evidence_file,notes
REG-01,payment-core,创建预付单,TODO,TODO,NOT_APPLICABLE,TODO,TODO,,,,,,
REG-02,payment-core,提交支付请求,TODO,TODO,TODO,TODO,TODO,,,,,,
REG-03,payment-core,渠道回调成功,TODO,TODO,TODO,TODO,TODO,,,,,,
REG-04,clearing-system,支付成功自动清分,TODO,TODO,TODO,TODO,TODO,,,,,,
REG-05,settlement-system,清分结果自动结算,TODO,TODO,TODO,TODO,TODO,,,,,,
REG-06,accounting-system,清分结果自动记账,TODO,TODO,TODO,TODO,TODO,,,,,,
REG-07,reconciliation-system,跨系统链路诊断,TODO,TODO,TODO,OPTIONAL,NOT_APPLICABLE,,,,,,
REG-08,payment-core,失败派发重试与DLQ,TODO,TODO,TODO,TODO,TODO,,,,,,
REG-09,settlement-system,结算审核与出款,TODO,TODO,TODO,TODO,TODO,,,,,,
REG-10,payment-core,日终处理与运营巡检,TODO,TODO,TODO,TODO,TODO,,,,,,
EOF

cat > "$SUMMARY_FILE" <<EOF
# ${PACK_TAG} 回归汇总

- 基线日期：\`${BASELINE_DATE}\`
- 归档状态：\`TODO\`

## 1. 基线信息

| 项目 | 记录 |
| --- | --- |
| Git 分支 | \`test\` |
| Git 提交号 | |
| 环境类型 | |
| 执行人 | |
| 复核人 | |

## 2. 场景结论

| 场景编号 | 业务场景 | API | 数据库 | 事件 | 页面 | 补偿/回滚 | 结论 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| REG-01 | 创建预付单 | TODO | TODO | N/A | TODO | TODO | TODO | |
| REG-02 | 提交支付请求 | TODO | TODO | TODO | TODO | TODO | TODO | |
| REG-03 | 渠道回调成功 | TODO | TODO | TODO | TODO | TODO | TODO | |
| REG-04 | 支付成功自动清分 | TODO | TODO | TODO | TODO | TODO | TODO | |
| REG-05 | 清分结果自动结算 | TODO | TODO | TODO | TODO | TODO | TODO | |
| REG-06 | 清分结果自动记账 | TODO | TODO | TODO | TODO | TODO | TODO | |
| REG-07 | 跨系统链路诊断 | TODO | TODO | TODO | OPTIONAL | N/A | TODO | |
| REG-08 | 失败派发重试 / DLQ | TODO | TODO | TODO | TODO | TODO | TODO | |
| REG-09 | 结算审核与出款 | TODO | TODO | TODO | TODO | TODO | TODO | |
| REG-10 | 日终处理与运营巡检 | TODO | TODO | TODO | TODO | TODO | TODO | |

## 3. 阻断项

1. 
2. 
3. 

## 4. 放行结论

| 结论项 | 结果 |
| --- | --- |
| 是否允许推进 \`test -> master\` | |
| 是否允许创建 \`release/*\` | |
| 仍需补什么 | |
EOF

echo "已生成回归证据包目录: $EVIDENCE_DIR"
echo "场景台账: $SCENARIO_FILE"
echo "回归汇总模板: $SUMMARY_FILE"
