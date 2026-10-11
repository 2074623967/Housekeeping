export function canRunRefundAction(status, actionName) {
  return (actionName === "query" && status === "PROCESSING")
    || (actionName === "approve" && status === "REVIEWING")
    || (actionName === "retry" && status === "FAIL");
}

export function refundOperationSuggestions(detail) {
  if (!detail) {
    return [];
  }
  const suggestions = [];
  if (detail.status === "REVIEWING") {
    suggestions.push("当前退款单仍在审核阶段，建议先核对退款原因、原支付事实和审批备注后再放行。");
  }
  if (detail.status === "PROCESSING") {
    if (detail.attempts?.[0]?.attemptStatus === "UNKNOWN") {
      suggestions.push("退款渠道提交结果未知，请优先主动查单或核对可信回调；未确认渠道结果前不得重发退款提交。");
    } else {
      suggestions.push("当前退款单已进入处理中，建议关注渠道回调和主动查单结果，勿重复提交渠道。");
    }
  }
  if (detail.status === "FAIL") {
    suggestions.push("当前退款单已失败，建议结合操作日志查看失败原因，并决定是否执行失败重试。");
  }
  if (["SUCCESS", "FAIL"].includes(detail.status)
    && (!detail.terminalAt || detail.terminalAt === "-")) {
    suggestions.push("当前退款单缺少本地终态时间，请核对可信回调、主动查单和最新退款尝试，勿用申请时间代填。");
  }
  if (!(detail.operationLogs || []).length) {
    suggestions.push("当前暂无退款操作日志，建议补充申请、审核或回调动作后再做复盘。");
  }
  if (!suggestions.length) {
    suggestions.push("当前退款状态稳定，可结合支付单详情和支付记录详情继续做资金复盘。");
  }
  return suggestions;
}
