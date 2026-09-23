function count(value) {
  const normalized = Number(value);
  return Number.isFinite(normalized) && normalized > 0 ? normalized : 0;
}

function formatCount(value) {
  return `${value} 项`;
}

function maxCount(...values) {
  return Math.max(...values.map(count));
}

function createItem(title, owner, countValue, impact, route) {
  return {
    title,
    owner,
    impact: `${formatCount(countValue)}${impact}`,
    status: "待处理",
    badgeType: "warn",
    route
  };
}

/**
 * 将任务中心真实积压转换为工作台可直接下钻的待办队列。
 */
export function buildDashboardFocusQueue(metrics = {}, taskOverview = {}) {
  const items = [];
  const hasTaskOverview = Boolean(
    taskOverview && typeof taskOverview === "object" && Object.keys(taskOverview).length
  );
  const pendingPayments = hasTaskOverview
    ? count(taskOverview.pendingCallbackCount)
    : count(metrics.pendingCount);
  const pendingQueries = maxCount(
    taskOverview.pendingQueryBacklogCount,
    taskOverview.duePendingQueryCount
  );
  const overdueIssues = maxCount(
    taskOverview.overdueIssueCount,
    taskOverview.actionableIssueSlaCount,
    taskOverview.pendingIssueAlertGenerationCount,
    taskOverview.pendingIssueAlertEscalationCount
  );
  const failedEvents = maxCount(taskOverview.failedEventCount, taskOverview.deadLetterEventCount);
  const failedRefunds = maxCount(
    taskOverview.failedRefundCount,
    taskOverview.pendingRefundQueryBacklogCount,
    taskOverview.duePendingRefundQueryCount
  );
  const warningDayEnd = count(taskOverview.warningDayEndBatchCount);
  const controlPolicyWarnings = count(taskOverview.controlPolicyWarningCount);

  if (pendingPayments > 0) {
    items.push(createItem(
      "待收口支付单",
      "支付运营",
      pendingPayments,
      "需查单或核回调",
      "/payment-issues"
    ));
  }
  if (pendingQueries > 0) {
    items.push(createItem(
      "主动查单积压",
      "支付运营",
      pendingQueries,
      "已达到补查窗口",
      "/payment-task-center"
    ));
  }
  if (overdueIssues > 0) {
    items.push(createItem(
      "超时异常与告警",
      "支付值班",
      overdueIssues,
      "需要确认责任和 SLA",
      "/payment-issues"
    ));
  }
  if (failedEvents > 0) {
    items.push(createItem(
      "失败事件与死信",
      "事件运营",
      failedEvents,
      "需要重发或人工结案",
      "/payment-dead-letter-tasks"
    ));
  }
  if (failedRefunds > 0) {
    items.push(createItem(
      "失败退款复核",
      "退款运营",
      failedRefunds,
      "核对终态后再决定重试",
      "/refunds"
    ));
  }
  if (warningDayEnd > 0) {
    items.push(createItem(
      "支付日终批次检查",
      "财务结算",
      warningDayEnd,
      "存在告警批次",
      "/payment-day-end"
    ));
  }
  if (controlPolicyWarnings > 0) {
    items.push(createItem(
      "控制策略自检",
      "支付配置",
      controlPolicyWarnings,
      "启用策略未通过自检",
      "/payment-config"
    ));
  }

  if (items.length > 0) {
    return items;
  }
  return [{
    title: "当前无待处理事项",
    owner: "支付运营",
    impact: "任务中心与异常中心均无待处置项",
    status: "已清空",
    badgeType: "success",
    route: "/payment-task-center"
  }];
}
