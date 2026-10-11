function normalizePaymentMethod(paymentMethod) {
  const value = String(paymentMethod || "").trim();
  if (value === "银行卡" || value === "银行转账") return "BANK_CARD";
  if (value === "微信支付" || value.toUpperCase().includes("WX")) return "WECHAT_PAY";
  if (value === "支付宝" || value.toUpperCase().includes("ALI")) return "ALIPAY";
  return value.toUpperCase();
}

export function routeFallbackChannelOptions(channels, targetCode, selectedCode) {
  const items = Array.isArray(channels) ? channels : [];
  const target = items.find((channel) => channel.channelCode === targetCode);
  const targetMethod = normalizePaymentMethod(target?.paymentMethod);
  const options = items.filter((channel) => {
    const method = normalizePaymentMethod(channel.paymentMethod);
    return channel.channelCode === selectedCode || (targetMethod && method === targetMethod
      && channel.channelCode !== targetCode);
  }).map((channel) => ({
    ...channel,
    incompatible: !targetMethod || channel.channelCode === targetCode
      || normalizePaymentMethod(channel.paymentMethod) !== targetMethod
  }));
  if (selectedCode && !options.some((channel) => channel.channelCode === selectedCode)) {
    options.push({ channelCode: selectedCode, channelName: "历史渠道未加载或已删除", incompatible: true });
  }
  return options;
}

export function routeFallbackChannelIssue(channels, rule) {
  if (!rule?.fallbackChannelCode) return null;
  const items = Array.isArray(channels) ? channels : [];
  if (rule.targetChannelCode === rule.fallbackChannelCode) return "目标与兜底渠道相同";
  const target = items.find((channel) => channel.channelCode === rule.targetChannelCode);
  const fallback = items.find((channel) => channel.channelCode === rule.fallbackChannelCode);
  if (!target || !fallback) return "目标或兜底渠道未加载或已删除";
  const targetMethod = normalizePaymentMethod(target.paymentMethod);
  const fallbackMethod = normalizePaymentMethod(fallback.paymentMethod);
  if (!targetMethod || !fallbackMethod) return "目标或兜底支付方式缺失";
  return targetMethod === fallbackMethod ? null : "目标与兜底支付方式不一致";
}
