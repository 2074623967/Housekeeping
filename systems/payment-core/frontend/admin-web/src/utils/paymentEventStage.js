const PAYMENT_SUCCESS_HTTP_STAGES = {
  PENDING: {
    label: "待清分",
    badgeType: "warn",
    action: "重发清分与账务，并核对下游幂等结果"
  },
  CLEARING_ACCEPTED: {
    label: "清分已受理",
    badgeType: "info",
    action: "仅重试账务投递，并核对清分受理记录"
  },
  COMPLETED: {
    label: "双下游已受理",
    badgeType: "success",
    action: "补齐事件发布状态，不重复调用下游"
  }
};

export function paymentSuccessHttpStageMeta(event = {}) {
  if (event.eventType !== "PAYMENT_SUCCESS") {
    return {
      label: "不适用",
      badgeType: "info",
      action: "重发事件、核对下游系统状态"
    };
  }
  const stage = typeof event.paymentSuccessHttpStage === "string"
    ? event.paymentSuccessHttpStage.trim()
    : "";
  if (event.publishStatus === "SUCCESS" && stage === "PENDING") {
    return {
      label: "未记录 HTTP 阶段",
      badgeType: "info",
      action: "事件已发布，无需重发；按实际投递模式核对下游结果"
    };
  }
  return PAYMENT_SUCCESS_HTTP_STAGES[stage] || {
    label: "阶段异常",
    badgeType: "danger",
    action: "核对事件阶段数据，修复前不要盲目重发"
  };
}

export function paymentEventRecommendedAction(event = {}) {
  if (event.publishStatus === "UNKNOWN") {
    return "按退款单号向渠道查单，禁止直接重发";
  }
  return paymentSuccessHttpStageMeta(event).action;
}
