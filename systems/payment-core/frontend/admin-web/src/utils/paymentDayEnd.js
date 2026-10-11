export function resolvePaymentDayEndRunMessage(result, requestedBizDate) {
  const bizDate = result?.latestBizDate || requestedBizDate || "当前业务日";
  const batchStatus = result?.latestBatchStatus || "未知";
  const readiness = result?.reconciliationReadinessStatus || "未生成";
  if (batchStatus === "WARNING") {
    return `${bizDate} 日终处理已完成，但批次存在未收口风险（状态：WARNING，对账准入：${readiness}），请继续处理告警。`;
  }
  if (batchStatus === "COMPLETED") {
    return `${bizDate} 日终处理已完成（状态：COMPLETED，对账准入：${readiness}）。`;
  }
  return `${bizDate} 日终处理已返回结果（批次状态：${batchStatus}，对账准入：${readiness}），请核对最近批次。`;
}
