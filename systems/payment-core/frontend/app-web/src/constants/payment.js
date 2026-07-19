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
  return "pending";
}
