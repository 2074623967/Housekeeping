import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const source = fs.readFileSync(new URL("./PaymentEventsView.vue", import.meta.url), "utf8");

test("keeps anomalous events readable without a rejected payment-detail link", () => {
  assert.match(source, /v-if="item\.successFactIssue"[^>]*> · \{\{ item\.successFactIssue \}\}/);
  assert.match(source, /v-if="selectedItem\.successFactIssue"[^>]*><span>支付成功事实/);
  assert.match(source, /:disabled="!canUseSnapshot \|\| !!selectedItem\.successFactIssue"[^>]*@click="openDrillDown\(paymentDetailRoute\(selectedItem\.paymentOrderId\)\)"/);
  assert.match(source, /openEventDetail\(selectedItem\.eventNo\)/);
  assert.match(source, /paymentLogsRoute\(\{ paymentOrderId: selectedItem\.paymentOrderId \}\)/);
});

test("refreshes the event snapshot after a failed manual republish", () => {
  const handler = source.match(/async function republish\(item\) \{([\s\S]*?)\n\}\n\nfunction goToPage/)?.[1];
  assert.ok(handler);
  assert.match(handler, /catch \(error\) \{[\s\S]*?activeEventNo\.value = "";\s*const refreshed = await loadEvents\(\{ \.\.\.loadedQuery\.value \}\);[\s\S]*?actionErrorMessage\.value = refreshed/);
  assert.match(source, /:disabled="!canUseSnapshot \|\| \['SUCCESS', 'UNKNOWN'\]\.includes\(item\.publishStatus\)"/);
});

test("keeps event republish responses off changed routes and unmounted views", () => {
  assert.match(source, /onBeforeUnmount\(\(\) => \{\s*viewActive = false;\s*latestLoadRequestId \+= 1;/);
  assert.match(source, /eventRouteVersion \+= 1;[\s\S]*?filters\.value = filtersFromRoute\(\);[\s\S]*?loadEvents\(buildQuery\(\), true\);\s*\},\s*\{ flush: "sync" \}/);
  assert.match(source, /const result = await paymentEventApi\.republish[\s\S]*?if \(!isCurrentEventRoute\(routeVersion\)\) \{\s*await refreshCurrentEventRoute\(\);\s*return;/);
  assert.match(source, /const refreshed = await loadEvents\(\{ \.\.\.loadedQuery\.value \}\);\s*if \(!isCurrentEventRoute\(routeVersion\)\) \{\s*return;/);
  assert.match(source, /const overviewResult = await paymentEventApi\.getOverview\(query\);\s*if \(!isCurrentEventRoute\(routeVersion\)\) \{\s*return;/);
});
