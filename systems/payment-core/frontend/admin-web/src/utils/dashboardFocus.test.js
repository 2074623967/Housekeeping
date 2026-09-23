import assert from "node:assert/strict";
import test from "node:test";
import { buildDashboardFocusQueue } from "./dashboardFocus.js";

test("prioritizes real task-center backlog and keeps drill-down routes", () => {
  const queue = buildDashboardFocusQueue(
    { pendingCount: 99 },
    {
      pendingCallbackCount: 2,
      pendingQueryBacklogCount: 1,
      duePendingQueryCount: 2,
      failedEventCount: 1,
      deadLetterEventCount: 1
    }
  );

  assert.deepEqual(queue.map((item) => item.title), [
    "待收口支付单",
    "主动查单积压",
    "失败事件与死信"
  ]);
  assert.equal(queue[0].impact, "2 项需查单或核回调");
  assert.equal(queue[1].impact, "2 项已达到补查窗口");
  assert.equal(queue[2].route, "/payment-dead-letter-tasks");
});

test("trusts an available task overview even when its count is zero", () => {
  const queue = buildDashboardFocusQueue(
    { pendingCount: 99 },
    { pendingCallbackCount: 0 }
  );

  assert.equal(queue[0].title, "当前无待处理事项");
});

test("returns a truthful empty state when no backlog exists", () => {
  const queue = buildDashboardFocusQueue(
    { pendingCount: 0 },
    { pendingCallbackCount: 0 }
  );

  assert.equal(queue.length, 1);
  assert.equal(queue[0].title, "当前无待处理事项");
  assert.equal(queue[0].status, "已清空");
  assert.equal(queue[0].badgeType, "success");
});
