export const PAYMENT_METHOD_CHANNEL_MAP = {
  微信支付: "WX_H5",
  支付宝: "ALIPAY_H5",
  银行卡: "BANK_CARD"
};

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
    hint: "渠道回调可能稍有延迟，可主动查单或模拟回调完成联调。"
  }
};

export function resolvePaymentChannelCode(paymentMethod) {
  return PAYMENT_METHOD_CHANNEL_MAP[paymentMethod] || "BANK_CARD";
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
