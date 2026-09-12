import assert from "node:assert/strict";
import test from "node:test";
import { paymentIssueListFiltersFromQuery } from "./paymentIssueListFilters.js";

test("hydrates payment issue alert filters from route query", () => {
  assert.deepEqual(paymentIssueListFiltersFromQuery({
    issueType: "待回调未收口",
    severity: "P1",
    channelCode: "wx_h5"
  }), {
    paymentOrderId: "",
    orderNo: "",
    issueType: "待回调未收口",
    severity: "P1",
    channelCode: "wx_h5",
    paymentMethod: "全部"
  });
});

test("ignores non-scalar payment issue route filters", () => {
  assert.deepEqual(paymentIssueListFiltersFromQuery({
    paymentOrderId: ["PAY-001"],
    orderNo: null,
    issueType: [],
    severity: "",
    paymentMethod: "支付宝"
  }), {
    paymentOrderId: "",
    orderNo: "",
    issueType: "全部",
    severity: "全部",
    channelCode: "",
    paymentMethod: "支付宝"
  });
});
