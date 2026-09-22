const assert = require("node:assert/strict");
const test = require("node:test");

const { paymentApi } = require("../utils/request");

function installRequestMock(result) {
  global.wx = {
    getStorageSync() {
      return "http://127.0.0.1:18081";
    },
    request(options) {
      result(options);
    }
  };
  global.getApp = () => ({ globalData: {} });
}

test("normalizes mini program network failures", async () => {
  installRequestMock((options) => options.fail({ errMsg: "request:fail network unavailable" }));

  await assert.rejects(
    paymentApi.getDetail("PAY-NETWORK"),
    (error) => error.code === "PAYMENT-MINI-NETWORK"
  );
});

test("normalizes mini program timeout failures", async () => {
  installRequestMock((options) => options.fail({ errMsg: "request:fail timeout" }));

  await assert.rejects(
    paymentApi.getDetail("PAY-TIMEOUT"),
    (error) => error.code === "PAYMENT-MINI-TIMEOUT"
  );
});
