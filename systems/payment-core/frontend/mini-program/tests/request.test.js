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

test("filters null rows from mini program detail responses", async () => {
  let call = 0;
  installRequestMock((options) => {
    const data = call++ === 0
      ? { prepayOrderNo: "PRE-001", channels: [null, "微信支付"] }
      : call === 2
        ? { paymentOrderId: "PAY-001", routeLogs: [null, "路由完成"], notifyLogs: null, eventLogs: ["事件完成"] }
        : { refundOrderId: "REF-001", attempts: [null, { refundAttemptNo: "ATT-001" }], operationLogs: [null, "申请退款"] };
    options.success({ statusCode: 200, data: { code: "0", data } });
  });

  const cashier = await paymentApi.getCashier("PRE-001");
  const payment = await paymentApi.getDetail("PAY-001");
  const refund = await paymentApi.getRefundDetail("REF-001");
  assert.deepEqual(cashier.channels, ["微信支付"]);
  assert.deepEqual(payment.routeLogs, ["路由完成"]);
  assert.deepEqual(payment.notifyLogs, []);
  assert.deepEqual(refund.attempts, [{ refundAttemptNo: "ATT-001" }]);
  assert.deepEqual(refund.operationLogs, ["申请退款"]);
});
