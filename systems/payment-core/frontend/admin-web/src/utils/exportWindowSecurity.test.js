import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const viewsDirectory = join(dirname(fileURLToPath(import.meta.url)), "../views");

test("all admin export windows prevent opener and referrer leakage", () => {
  const exportViews = [
    "PaymentTaskCenterView.vue",
    "PaymentIssueAlertsView.vue",
    "RefundsView.vue",
    "PaymentsView.vue",
    "PaymentEventsView.vue",
    "PaymentRequestsView.vue",
    "WorkerSettlementsView.vue",
    "PaymentDayEndView.vue",
    "BillsView.vue",
    "PaymentRoutesView.vue",
    "PaymentLogsView.vue",
    "OrdersView.vue",
    "PaymentFlowsView.vue",
    "CashierSessionsView.vue",
    "PaymentConfigView.vue"
  ];

  for (const view of exportViews) {
    const source = readFileSync(join(viewsDirectory, view), "utf8");
    assert.match(source, /window\.open\(exportUrl, "_blank", "noopener,noreferrer"\)/, view);
  }
});
