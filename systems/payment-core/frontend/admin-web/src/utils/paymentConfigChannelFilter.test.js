import assert from "node:assert/strict";
import test from "node:test";
import {
  filterPaymentConfigChannels,
  paymentConfigChannelStatusFromQuery
} from "./paymentConfigChannelFilter.js";

const channels = [
  { channelCode: "wx_h5", status: "ENABLED" },
  { channelCode: "alipay_h5", status: "DISABLED" }
];

test("hydrates disabled channel alert filter from route query", () => {
  const status = paymentConfigChannelStatusFromQuery({ channelStatus: "DISABLED" });

  assert.equal(status, "DISABLED");
  assert.deepEqual(filterPaymentConfigChannels(channels, status), [channels[1]]);
});

test("falls back to all channels for unsupported route filters", () => {
  assert.equal(paymentConfigChannelStatusFromQuery({ channelStatus: ["DISABLED"] }), "全部");
  assert.equal(paymentConfigChannelStatusFromQuery({ channelStatus: "UNKNOWN" }), "全部");
  assert.deepEqual(filterPaymentConfigChannels(channels, "全部"), channels);
  assert.deepEqual(filterPaymentConfigChannels(null, "DISABLED"), []);
});
