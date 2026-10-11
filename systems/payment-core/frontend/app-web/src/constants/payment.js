export const PAYMENT_METHOD_CHANNEL_MAP = {
  微信支付: "WX_H5",
  支付宝: "ALIPAY_H5",
  银行卡: "BANK_CARD"
};

export const PAYMENT_METHOD_MERCHANT_MAP = {
  微信支付: "MCH_WX_10001",
  支付宝: "MCH_ALI_20001",
  银行卡: "BANK_OFFLINE_30001"
};

export const PAYMENT_DETAIL_REFRESH_INTERVAL_MS = 5000;
export const PAYMENT_DETAIL_REFRESH_MAX_ATTEMPTS = 60;

export const PAYMENT_RESULT_STATE_META = {
  success: {
    title: "支付成功",
    hint: "支付结果已经收口，可返回订单或继续查看支付详情。"
  },
  closed: {
    title: "支付已关闭",
    hint: "当前支付单已关闭，如仍需支付请重新发起。"
  },
  wait_callback: {
    title: "支付处理中",
    hint: "支付请求已提交到渠道，当前正在等待回调或主动查单结果。"
  },
  risk_review: {
    title: "待风控复核",
    hint: "本次支付命中风控复核，请等待运营或风控审核后再继续支付。"
  },
  risk_blocked: {
    title: "支付已拦截",
    hint: "本次支付已被风控策略拦截，需调整订单、金额或支付方式后再尝试。"
  },
  risk_rejected: {
    title: "支付已拒绝",
    hint: "本次支付已被风控拒绝，请联系运营确认拒绝原因和后续处理方式。"
  },
  prepay_created: {
    title: "待发起支付",
    hint: "预付单已创建，但还未正式提交到支付渠道。"
  },
  pending: {
    title: "支付处理中",
    hint: "渠道回调可能稍有延迟，可主动查单刷新真实支付结果。"
  }
};

export function resolvePaymentChannelCode(paymentMethod) {
  return PAYMENT_METHOD_CHANNEL_MAP[paymentMethod] || "BANK_CARD";
}

export function resolvePaymentMerchantNo(paymentMethod) {
  return PAYMENT_METHOD_MERCHANT_MAP[paymentMethod] || "BANK_OFFLINE_30001";
}

export function canRetryReviewedPayment(cashier, paymentMethod) {
  return cashier?.paymentStatus === "RISK_REVIEW"
    && cashier.status === "待风控复核"
    && Array.isArray(cashier.channels)
    && cashier.channels.includes(paymentMethod)
    && cashier.reviewPaymentMethod === paymentMethod
    && typeof cashier.reviewChannelCode === "string"
    && PAYMENT_METHOD_CHANNEL_MAP[paymentMethod]?.toLowerCase() === cashier.reviewChannelCode.toLowerCase();
}

export function resolvePaymentResultState(paymentStatus) {
  if (paymentStatus === "SUCCESS") {
    return "success";
  }
  if (paymentStatus === "CLOSED") {
    return "closed";
  }
  if (paymentStatus === "WAIT_CALLBACK" || paymentStatus === "PAYING") {
    return "wait_callback";
  }
  if (paymentStatus === "RISK_REVIEW") {
    return "risk_review";
  }
  if (paymentStatus === "RISK_BLOCKED") {
    return "risk_blocked";
  }
  if (paymentStatus === "RISK_REJECTED") {
    return "risk_rejected";
  }
  if (paymentStatus === "PREPAY_CREATED") {
    return "prepay_created";
  }
  return "pending";
}

export function shouldAutoRefreshPaymentDetail(paymentStatus) {
  if (typeof paymentStatus !== "string") {
    return false;
  }
  return ["WAIT_CALLBACK", "PAYING", "RISK_REVIEW"].includes(paymentStatus.trim().toUpperCase());
}

export function resolvePaymentDetailRefreshDelay(paymentStatus, completedAttempts) {
  if (!Number.isInteger(completedAttempts)
      || completedAttempts < 0
      || completedAttempts >= PAYMENT_DETAIL_REFRESH_MAX_ATTEMPTS
      || !shouldAutoRefreshPaymentDetail(paymentStatus)) {
    return null;
  }
  return PAYMENT_DETAIL_REFRESH_INTERVAL_MS;
}

export function isPaymentSubmissionPending(cashierStatus, attemptStatus) {
  return ["支付中", "待回调", "结果待确认"].includes(cashierStatus)
    || ["渠道提交中", "结果未知，待查单"].includes(attemptStatus);
}

export function isCashierExpired(status) {
  return status === "已失效";
}

export function canShowDesktopQrPlaceholder({ terminalVariant, hasValidCashierSnapshot, canSubmit }) {
  return terminalVariant === "pc" && hasValidCashierSnapshot && canSubmit;
}

export function canQueryPaymentChannel(status, latestAttemptStatus, latestAttemptCompletedAt, channelTransactionNo, channel) {
  if (status === "SUCCESS") {
    return true;
  }
  const normalizedAttemptStatus = typeof latestAttemptStatus === "string"
    ? latestAttemptStatus.trim()
    : "";
  if (status === "CLOSED" && !normalizedAttemptStatus && !channelTransactionNo) {
    return true;
  }
  return ["WAIT_CALLBACK", "CLOSED"].includes(status)
    && Boolean(normalizedAttemptStatus && normalizedAttemptStatus !== "-")
    && !["成功", "支付成功", "失败", "支付失败", "SUCCESS", "FAIL", "FAILED"]
      .includes(normalizedAttemptStatus.toUpperCase())
    && typeof channel === "string" && Boolean(channel.trim())
    && latestAttemptCompletedAt == null;
}

export function canClosePaymentLocally(status, channelTransactionNo, attemptStatus) {
  const closeableStatuses = [
    "PREPAY_CREATED",
    "RISK_REVIEW",
    "RISK_BLOCKED",
    "RISK_REJECTED",
    "待支付",
    "已失效",
    "待风控复核",
    "风控拦截",
    "风控拒绝"
  ];
  return closeableStatuses.includes(status)
    && !channelTransactionNo
    && (!attemptStatus || attemptStatus === "-");
}
