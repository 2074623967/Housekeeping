import assert from "node:assert/strict";
import test from "node:test";
import {
  paymentEventRecommendedAction,
  paymentSuccessHttpStageMeta
} from "./paymentEventStage.js";

test("maps payment success HTTP stages to operational labels", () => {
  assert.deepEqual(paymentSuccessHttpStageMeta({
    eventType: "PAYMENT_SUCCESS",
    publishStatus: "FAILED",
    paymentSuccessHttpStage: "PENDING"
  }), {
    label: "待清分",
    badgeType: "warn",
    action: "重发清分与账务，并核对下游幂等结果"
  });
  assert.equal(paymentSuccessHttpStageMeta({
    eventType: "PAYMENT_SUCCESS",
    paymentSuccessHttpStage: "CLEARING_ACCEPTED"
  }).label, "清分已受理");
  assert.equal(paymentSuccessHttpStageMeta({
    eventType: "PAYMENT_SUCCESS",
    paymentSuccessHttpStage: "COMPLETED"
  }).label, "双下游已受理");
});

test("does not present unused or invalid HTTP stages as accepted facts", () => {
  assert.equal(paymentSuccessHttpStageMeta({
    eventType: "PAYMENT_SUCCESS",
    publishStatus: "SUCCESS",
    paymentSuccessHttpStage: "PENDING"
  }).label, "未记录 HTTP 阶段");
  assert.equal(paymentSuccessHttpStageMeta({
    eventType: "REFUND_SUBMIT",
    paymentSuccessHttpStage: "PENDING"
  }).label, "不适用");
  assert.equal(paymentSuccessHttpStageMeta({
    eventType: "PAYMENT_SUCCESS",
    paymentSuccessHttpStage: "unexpected"
  }).badgeType, "danger");
  assert.equal(paymentEventRecommendedAction({
    publishStatus: "UNKNOWN"
  }), "按退款单号向渠道查单，禁止直接重发");
});
