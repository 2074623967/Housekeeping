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
  paying: {
    title: "支付提交处理中",
    hint: "渠道受理尚未确认，请刷新支付详情并等待状态推进，勿重复付款。"
  },
  conflict: {
    title: "渠道流水归属待核查",
    hint: "渠道流水归属冲突，需人工核对渠道原始事实；请勿重复付款、主动查单或关闭支付单。"
  },
  fail: {
    title: "支付结果待核查",
    hint: "当前支付结果尚未确认，请勿重复付款；请刷新支付详情或联系服务方核查渠道与订单事实。"
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
    title: "支付结果待核查",
    hint: "当前支付状态尚未确认，请勿重复付款；请刷新支付详情并联系服务方核查。"
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

export function isChannelTransactionConflict(latestAttemptStatus) {
  return typeof latestAttemptStatus === "string"
    && latestAttemptStatus.trim() === "渠道流水归属冲突，待人工核查";
}

export function resolvePaymentResultState(paymentStatus, latestAttemptStatus) {
  if (["PAYING", "WAIT_CALLBACK"].includes(paymentStatus)
      && isChannelTransactionConflict(latestAttemptStatus)) {
    return "conflict";
  }
  if (paymentStatus === "SUCCESS") {
    return "success";
  }
  if (paymentStatus === "CLOSED") {
    return "closed";
  }
  if (paymentStatus === "PAYING") {
    return "paying";
  }
  if (paymentStatus === "FAIL") {
    return "fail";
  }
  if (paymentStatus === "WAIT_CALLBACK") {
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

export function shouldAutoRefreshPaymentDetail(paymentStatus, latestAttemptStatus) {
  if (isChannelTransactionConflict(latestAttemptStatus)) {
    return false;
  }
  if (typeof paymentStatus !== "string") {
    return false;
  }
  return ["WAIT_CALLBACK", "PAYING", "RISK_REVIEW"].includes(paymentStatus.trim().toUpperCase());
}

export function resolvePaymentDetailRefreshDelay(paymentStatus, completedAttempts, latestAttemptStatus) {
  if (!Number.isInteger(completedAttempts)
      || completedAttempts < 0
      || completedAttempts >= PAYMENT_DETAIL_REFRESH_MAX_ATTEMPTS
      || !shouldAutoRefreshPaymentDetail(paymentStatus, latestAttemptStatus)) {
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

export function resolveRemainingSeconds(expiresAt, now = Date.now()) {
  if (typeof expiresAt !== "string"
      || !/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(expiresAt)) {
    return 0;
  }
  const expiresTimestamp = Date.parse(`${expiresAt.replace(" ", "T")}+08:00`);
  if (!Number.isFinite(expiresTimestamp) || !Number.isFinite(now)) {
    return 0;
  }
  if (new Date(expiresTimestamp + 8 * 60 * 60 * 1000)
    .toISOString().slice(0, 19).replace("T", " ") !== expiresAt) {
    return 0;
  }
  return Math.max(0, Math.floor((expiresTimestamp - now) / 1000));
}

export function canShowDesktopQrPlaceholder({ terminalVariant, hasValidCashierSnapshot, canSubmit }) {
  return terminalVariant === "pc" && hasValidCashierSnapshot && canSubmit;
}

export function canQueryPaymentChannel(status, latestAttemptStatus, latestAttemptCompletedAt, channelTransactionNo, channel) {
  if (isChannelTransactionConflict(latestAttemptStatus)) {
    return false;
  }
  if (status === "SUCCESS") {
    return true;
  }
  const normalizedAttemptStatus = typeof latestAttemptStatus === "string"
    ? latestAttemptStatus.trim()
    : "";
  if (status === "WAIT_CALLBACK" && normalizedAttemptStatus === "渠道提交中"
      && !(typeof channelTransactionNo === "string"
        ? channelTransactionNo.trim() : channelTransactionNo)) {
    return false;
  }
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
