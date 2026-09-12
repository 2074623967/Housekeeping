import assert from "node:assert/strict";
import test from "node:test";
import { paymentIssueAlertListFiltersFromQuery } from "./paymentIssueAlertListFilters.js";

test("hydrates payment issue alert log filters from route query", () => {
  assert.deepEqual(paymentIssueAlertListFiltersFromQuery({
    alertNo: "ALERT-001",
    alertChannel: "SMS",
    alertStatus: "PENDING_DISPATCH",
    providerDeliveryStatus: "ACCEPTED",
    ackStatus: "待确认",
    receiptQueryState: "PENDING_RECEIPT"
  }), {
    alertNo: "ALERT-001",
    issueNo: "",
    paymentOrderId: "",
    alertChannel: "SMS",
    alertStatus: "PENDING_DISPATCH",
    ackStatus: "待确认",
    providerDeliveryStatus: "ACCEPTED",
    receiptQueryState: "PENDING_RECEIPT"
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
