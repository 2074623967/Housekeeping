import assert from "node:assert/strict";
import test from "node:test";
import { refundListFiltersFromQuery } from "./refundListFilters.js";

test("hydrates refund alert filters from structured route query", () => {
  assert.deepEqual(refundListFiltersFromQuery({ refundStatus: "FAIL" }), {
    refundOrderId: "",
    paymentOrderId: "",
    refundStatus: "FAIL",
    refundMethod: "全部"
  });
  assert.deepEqual(refundListFiltersFromQuery({ refundStatus: "PENDING" }), {
    refundOrderId: "",
    paymentOrderId: "",
    refundStatus: "PENDING",
    refundMethod: "全部"
  });
  assert.deepEqual(refundListFiltersFromQuery({ refundStatus: "SUCCESS_TIME_MISSING" }), {
    refundOrderId: "",
    paymentOrderId: "",
    refundStatus: "SUCCESS_TIME_MISSING",
    refundMethod: "全部"
  });
  assert.deepEqual(refundListFiltersFromQuery({ refundStatus: "TERMINAL_FACT_MISMATCH" }), {
    refundOrderId: "",
    paymentOrderId: "",
    refundStatus: "TERMINAL_FACT_MISMATCH",
    refundMethod: "全部"
  });
  assert.deepEqual(refundListFiltersFromQuery({ refundStatus: "UNVERIFIED_SUCCESS" }), {
    refundOrderId: "",
    paymentOrderId: "",
    refundStatus: "UNVERIFIED_SUCCESS",
    refundMethod: "全部"
  });
});

test("ignores non-scalar refund route query values", () => {
  assert.deepEqual(refundListFiltersFromQuery({
    refundOrderId: ["REF-001", "REF-002"],
    paymentOrderId: null,
    refundStatus: [],
    refundMethod: "原路退款"
  }), {
    refundOrderId: "",
    paymentOrderId: "",
    refundStatus: "全部",
    refundMethod: "原路退款"
  });
});
