const CHANNEL_STATUSES = new Set(["ENABLED", "DISABLED"]);

export function paymentConfigChannelStatusFromQuery(query = {}) {
  const status = query.channelStatus;
  return typeof status === "string" && CHANNEL_STATUSES.has(status) ? status : "全部";
}

export function filterPaymentConfigChannels(channels, status) {
  const items = Array.isArray(channels) ? channels : [];
  return CHANNEL_STATUSES.has(status)
    ? items.filter((item) => item.status === status)
    : items;
}
