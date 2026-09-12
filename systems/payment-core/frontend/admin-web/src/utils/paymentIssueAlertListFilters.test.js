import assert from "node:assert/strict";
import test from "node:test";
import { paymentIssueAlertListFiltersFromQuery } from "./paymentIssueAlertListFilters.js";

test("hydrates payment issue alert log filters from route query", () => {
  assert.deepEqual(paymentIssueAlertListFiltersFromQuery({
    alertNo: "ALERT-001",
    alertChannel: "SMS",
    providerDeliveryStatus: "ACCEPTED",
    ackStatus: "待确认",
    receiptQueryState: "EXHAUSTED"
  }), {
    alertNo: "ALERT-001",
    issueNo: "",
    paymentOrderId: "",
    alertChannel: "SMS",
    alertStatus: "全部",
    ackStatus: "待确认",
    providerDeliveryStatus: "ACCEPTED",
    receiptQueryState: "EXHAUSTED"
  });
});

test("ignores non-scalar payment issue alert log route filters", () => {
  assert.deepEqual(paymentIssueAlertListFiltersFromQuery({
    alertNo: ["ALERT-001"],
    issueNo: null,
    paymentOrderId: "",
    alertChannel: [],
    alertStatus: "派发失败",
    ackStatus: ["待确认"],
    providerDeliveryStatus: undefined,
    receiptQueryState: ["EXHAUSTED"]
  }), {
    alertNo: "",
    issueNo: "",
    paymentOrderId: "",
    alertChannel: "全部",
    alertStatus: "派发失败",
    ackStatus: "全部",
    providerDeliveryStatus: "全部",
    receiptQueryState: "全部"
  });
});
