import assert from "node:assert/strict";
import test from "node:test";
import {
  routeFallbackChannelIssue,
  routeFallbackChannelOptions
} from "./paymentRouteFallbackChannels.js";

const channels = [
  { channelCode: "wx_h5", paymentMethod: "微信支付" },
  { channelCode: "wx_jsapi", paymentMethod: "WECHAT_PAY" },
  { channelCode: "alipay_h5", paymentMethod: "支付宝" },
  { channelCode: "bank", paymentMethod: "银行卡" },
  { channelCode: "bank_backup", paymentMethod: "银行转账" }
];

test("only offers other channels of the target payment method", () => {
  assert.deepEqual(routeFallbackChannelOptions(channels, "wx_h5", "")
    .map((channel) => channel.channelCode), ["wx_jsapi"]);
  assert.deepEqual(routeFallbackChannelOptions(channels, "bank", "")
    .map((channel) => channel.channelCode), ["bank_backup"]);
});

test("keeps a historical cross-method selection visible but disabled", () => {
  assert.deepEqual(routeFallbackChannelOptions(channels, "wx_h5", "alipay_h5")
    .map(({ channelCode, incompatible }) => [channelCode, incompatible]),
  [["wx_jsapi", false], ["alipay_h5", true]]);
  assert.deepEqual(routeFallbackChannelOptions(channels, "wx_h5", "removed")
    .map(({ channelCode, incompatible }) => [channelCode, incompatible]),
  [["wx_jsapi", false], ["removed", true]]);
});

test("identifies historical fallback issues without flagging valid bank aliases or no fallback", () => {
  const rule = { targetChannelCode: "wx_h5", fallbackChannelCode: "alipay_h5" };
  assert.equal(routeFallbackChannelIssue(channels, rule), "目标与兜底支付方式不一致");
  assert.equal(routeFallbackChannelIssue(channels, { ...rule, fallbackChannelCode: "removed" }),
    "目标或兜底渠道未加载或已删除");
  assert.equal(routeFallbackChannelIssue(channels, { ...rule, fallbackChannelCode: "wx_h5" }),
    "目标与兜底渠道相同");
  assert.equal(routeFallbackChannelIssue(channels, { ...rule, fallbackChannelCode: "wx_jsapi" }), null);
  assert.equal(routeFallbackChannelIssue(channels, { ...rule, fallbackChannelCode: "" }), null);
  assert.equal(routeFallbackChannelIssue(channels, {
    targetChannelCode: "bank", fallbackChannelCode: "bank_backup"
  }), null);
  assert.equal(routeFallbackChannelIssue([{ channelCode: "wx_h5" }, channels[1]], {
    ...rule, fallbackChannelCode: "wx_jsapi"
  }), "目标或兜底支付方式缺失");
});
