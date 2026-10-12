export function isChannelSubmissionUnaccepted(status, latestAttemptStatus, channelTransactionNo) {
  return ["WAIT_CALLBACK", "支付中"].includes(status)
    && typeof latestAttemptStatus === "string"
    && latestAttemptStatus.trim() === "渠道提交中"
    && !(typeof channelTransactionNo === "string"
      ? channelTransactionNo.trim() : channelTransactionNo);
}

export function isChannelTransactionConflict(latestAttemptStatus) {
  return typeof latestAttemptStatus === "string"
    && latestAttemptStatus.trim() === "渠道流水归属冲突，待人工核查";
}

export function canQueryPaymentChannel(status, latestAttemptStatus, latestAttemptCompletedAt, channelTransactionNo, channel) {
  if (isChannelTransactionConflict(latestAttemptStatus)) {
    return false;
  }
  if (["SUCCESS", "支付成功"].includes(status)) {
    return true;
  }
  const normalizedAttemptStatus = typeof latestAttemptStatus === "string"
    ? latestAttemptStatus.trim()
    : "";
  if (isChannelSubmissionUnaccepted(status, latestAttemptStatus, channelTransactionNo)) {
    return false;
  }
  if (["CLOSED", "已关闭"].includes(status) && !normalizedAttemptStatus && !channelTransactionNo) {
    return true;
  }
  return ["WAIT_CALLBACK", "支付中", "CLOSED", "已关闭"].includes(status)
    && Boolean(normalizedAttemptStatus && normalizedAttemptStatus !== "-")
    && !["成功", "支付成功", "失败", "支付失败", "SUCCESS", "FAIL", "FAILED"]
      .includes(normalizedAttemptStatus.toUpperCase())
    && typeof channel === "string" && Boolean(channel.trim())
    && latestAttemptCompletedAt == null;
}

export function canClosePaymentLocally(status, channelTransactionNo, latestAttemptStatus) {
  const hasChannelTransaction = typeof channelTransactionNo === "string"
    ? Boolean(channelTransactionNo.trim())
    : Boolean(channelTransactionNo);
  const hasChannelAttempt = typeof latestAttemptStatus === "string"
    ? Boolean(latestAttemptStatus.trim() && latestAttemptStatus.trim() !== "-")
    : Boolean(latestAttemptStatus);
  return ["PREPAY_CREATED", "RISK_REVIEW", "RISK_BLOCKED", "RISK_REJECTED"].includes(status)
    && !hasChannelTransaction
    && !hasChannelAttempt;
}

export function paymentDetailOperationSuggestions(detail) {
  if (!detail) {
    return [];
  }
  const suggestions = [];
  const conflict = isChannelTransactionConflict(detail.latestAttemptStatus);
  const unaccepted = isChannelSubmissionUnaccepted(
    detail.status, detail.latestAttemptStatus, detail.channelTransactionNo
  );
  if (conflict) {
    suggestions.push("渠道流水归属冲突，需人工核查渠道原始事实与流水归属；勿主动查单、重复提交或绑定争议流水。");
  } else if (detail.status === "WAIT_CALLBACK") {
    if (unaccepted) {
      suggestions.push("渠道受理未确认，先核对最近支付尝试与提交日志，等待状态推进；勿主动查单或重复提交。");
    } else if (canQueryPaymentChannel(
      detail.status, detail.latestAttemptStatus, detail.latestAttemptCompletedAt,
      detail.channelTransactionNo, detail.channel
    )) {
      suggestions.push("当前支付单等待回调，可主动查单；确认渠道已成功但未回调时，再进入异常中心排查。");
    } else {
      suggestions.push("当前支付单缺少可查单的渠道提交事实，先刷新详情并核对支付尝试与渠道受理情况，勿重复提交。");
    }
  }
  if (detail.status === "PAYING" && !conflict) {
    suggestions.push("当前支付单处理中，建议优先查看最近一次支付尝试和渠道响应报文，避免重复提交。");
  }
  if (detail.status === "CLOSED") {
    suggestions.push("当前支付单已关闭，如业务仍需付款，应从订单侧重新发起预付单，禁止直接恢复旧单。");
  }
  if (detail.status === "SUCCESS") {
    suggestions.push("当前支付单已成功，建议联查订单、账单、支付记录和下游事件是否全部收口。");
  }
  if (!(detail.notifyLogs || []).length && !unaccepted && !conflict) {
    suggestions.push("当前没有回调轨迹，需关注渠道回调地址、验签和消息落库情况。");
  }
  if (!suggestions.length) {
    suggestions.push("当前状态可结合路由、回调和事件轨迹继续完成支付链路复盘。");
  }
  return suggestions;
}

export function paymentRecordQuerySuggestion(detail) {
  if (!detail) {
    return null;
  }
  if (isChannelTransactionConflict(detail.latestAttemptStatus)) {
    return "渠道流水归属冲突，需人工核查渠道原始事实与流水归属；勿主动查单、重复提交或绑定争议流水。";
  }
  if (isChannelSubmissionUnaccepted(
    detail.paymentStatus, detail.latestAttemptStatus, detail.externalTransactionNo
  )) {
    return "渠道受理未确认，先核对最近支付尝试与提交日志，等待状态推进；勿主动查单或重复提交。";
  }
  if (detail.successFactIssue || detail.prepayBindingIssue) {
    return null;
  }
  if (canQueryPaymentChannel(
    detail.paymentStatus, detail.latestAttemptStatus, detail.latestAttemptCompletedAt,
    detail.externalTransactionNo, detail.paymentChannel
  ) && detail.latestAttemptStatus && detail.latestAttemptStatus !== "成功") {
    return `最近一次支付尝试状态为 ${detail.latestAttemptStatus}，建议先主动查单再决定是否人工干预。`;
  }
  if (["WAIT_CALLBACK", "支付中"].includes(detail.paymentStatus)) {
    return "当前支付记录缺少可查单的渠道提交事实，先刷新详情并核对支付尝试与渠道受理情况，勿重复提交。";
  }
  return null;
}
