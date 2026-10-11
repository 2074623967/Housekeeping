import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";
import { canQueryPaymentChannel, shouldAutoRefreshPaymentDetail } from "../constants/payment.js";

const viewPath = join(dirname(fileURLToPath(import.meta.url)), "ResultView.vue");

test("checks completion time and channel transaction before offering a query", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /canQueryPaymentChannel\([\s\S]*paymentDetail\.value\?\.latestAttemptStatus,[\s\S]*paymentDetail\.value\?\.latestAttemptCompletedAt,[\s\S]*paymentDetail\.value\?\.channelTransactionNo/);
});

test("offers a route-bound retry when the payment result first fails to load", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /v-else-if="feedbackMessage && !paymentDetail"[\s\S]*v-if="currentPaymentOrderId"[^>]*@click="loadDetail\(\)"[^>]*>重试加载支付结果/);
  assert.match(source, /paymentOrderId !== currentPaymentOrderId\.value/);
});

test("does not present an unverified payment detail as a processing payment", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /hasUnverifiedResult = computed\(\(\) => !detailLoading\.value && !paymentDetail\.value && Boolean\(feedbackMessage\.value\)\)/);
  assert.match(source, /hasUnverifiedResult\.value\s*\? "支付结果待核查"/);
  assert.match(source, /请勿重复付款，保留支付单号并联系运营核查渠道与订单事实/);
  assert.match(source, /paymentDetail\?\.status \|\| \(hasUnverifiedResult \? "待核查" : "加载中"\)/);
  assert.match(source, /v-else-if="feedbackMessage && !paymentDetail"/);
});

test("invalidates the previously trusted result when a manual detail refresh fails", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /async function refreshPaymentDetail\(\)[\s\S]*?catch \(error\) \{[\s\S]*?isCurrentPaymentSnapshot\(snapshot, entityVersion\)[\s\S]*?paymentDetail\.value = null;[\s\S]*?loadedPaymentOrderId\.value = "";[\s\S]*?feedbackMessage\.value = error\.message;/);
  assert.match(source, /v-else-if="feedbackMessage && !paymentDetail"/);
});

test("re-reads failed actions and invalidates results when the readback fails", () => {
  const source = readFileSync(viewPath, "utf8");
  for (const action of ["queryResult", "closePayment"]) {
    const actionBody = source.split(`async function ${action}()`)[1].split("\nasync function ")[0];
    assert.match(actionBody, /catch \(error\) \{\s*await readBackAfterFailedAction\(snapshot, entityVersion, error\);/);
  }
  const readbackBody = source.split("async function readBackAfterFailedAction(")[1].split("\nasync function ")[0];
  assert.match(readbackBody, /paymentApi\.getDetail\(snapshot\.paymentOrderId, expectedRoutePayScene\.value\)/);
  assert.match(readbackBody, /if \(!matchesPaymentSnapshot\(snapshot, detail\)\)/);
  assert.match(readbackBody, /paymentDetail\.value = \{ \.\.\.paymentDetail\.value, \.\.\.detail \};[\s\S]*?syncStatusByDetail\(\);[\s\S]*?支付详情已重新读取/);
  assert.match(readbackBody, /catch \{[\s\S]*?isCurrentPaymentSnapshot\(snapshot, entityVersion\)[\s\S]*?paymentDetail\.value = null;[\s\S]*?loadedPaymentOrderId\.value = "";[\s\S]*?syncStatusByDetail\(\);[\s\S]*?支付状态未经确认，请重新加载详情，勿重复付款/);
  assert.match(source, /v-else-if="feedbackMessage && !paymentDetail"/);
});

test("marks failed background refresh as stale without stopping bounded retries", () => {
  const source = readFileSync(viewPath, "utf8");
  const refreshBody = source.split("async function refreshDetailInBackground()")[1].split("\nasync function ")[0];
  assert.match(refreshBody, /if \(!matchesPaymentSnapshot\(snapshot, detail\)\) \{\s*throw new Error/);
  assert.match(refreshBody, /autoRefreshStale\.value = false;[\s\S]*?catch \{[\s\S]*?requestId === latestLoadRequestId && isCurrentPaymentSnapshot\(snapshot, entityVersion\)[\s\S]*?autoRefreshStale\.value = true;[\s\S]*?autoRefreshAttempts\.value \+= 1;\s*scheduleAutoRefresh\(\);/);
  assert.match(source, /hasStaleResult\.value \|\| hasUnverifiedResult\.value\s*\? "支付结果待核查"/);
  assert.match(source, /canQueryPayment = computed\(\(\) => hasValidPaymentSnapshot\.value && !hasStaleResult\.value/);
  assert.match(source, /hasValidPaymentSnapshot\.value && !hasStaleResult\.value && canClosePaymentLocally/);
  assert.match(source, /hasStaleResult \? "待核查" : paymentDetail\?\.status/);
  assert.match(source, /v-if="hasStaleResult"[^>]*>自动刷新未能确认最新支付状态/);
  assert.match(source, /autoRefreshStale\.value = false;\s*syncStatusByDetail\(\);\s*autoRefreshAttempts\.value = 0;/);
});

test("reloads the result when the route payment identity changes", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(
    source,
    /watch\(\[currentPaymentOrderId, expectedPrepayOrderNo, expectedPaymentMethod, expectedRoutePayScene\], \(\[paymentOrderId\]\) => \{/,
  );
});

test("invalidates result actions and refresh when the route business scene changes", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /watch\(\[currentPaymentOrderId, expectedPrepayOrderNo, expectedPaymentMethod, expectedRoutePayScene\][\s\S]*currentEntityVersion \+= 1;[\s\S]*stopAutoRefresh\(\);[\s\S]*paymentDetail\.value = null;[\s\S]*loadDetail\(paymentOrderId\);/);
});

test("migrates legacy access tokens when the route query changes", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(
    source,
    /watch\(\(\) => route\.query\.accessToken, migrateLegacyAccessToken, \{ immediate: true \}\);/
  );
});

test("binds payment detail loads to the route business scene", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /expectedRoutePayScene = computed/);
  assert.match(source, /paymentApi\.getDetail\([\s\S]*expectedRoutePayScene\.value/);
});

test("only offers an entry and scene guidance matching the loaded payment", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /resolvePaymentBusinessEntryPath\(paymentDetail\.value\?\.payScene\)/);
  assert.match(source, /BIZ_SCENE_META\[businessEntryPath\.value\?\.slice\(1\)\] \|\| GENERIC_SCENE_META/);
  assert.match(source, /v-if="businessEntryPath"[^>]+@click="backToBusinessEntry"/);
  assert.match(source, /v-if="!businessEntryPath && hasValidPaymentSnapshot"/);
});

test("binds result actions to the route business scene", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /paymentApi\.query\([\s\S]*expectedRoutePayScene\.value/);
  assert.match(source, /paymentApi\.close\([\s\S]*expectedRoutePayScene\.value/);
});

test("preserves the raw payment scene when returning to the cashier", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /cashierRouteQuery\.payScene = expectedRoutePayScene\.value/);
});

test("binds the initial result detail to the payment method in the route", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /const expectedPaymentMethod = computed\(\(\) => typeof route\.query\.paymentMethod === "string"/);
  assert.match(source, /支付详情响应为空或与当前支付方式不一致/);
  assert.match(source, /detail\.paymentMethod\.trim\(\) !== expectedPaymentMethod\.value/);
});

test("keeps the full result snapshot when actions and detail refresh return updates", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.equal(
    (source.match(/paymentDetail\.value = \{ \.\.\.paymentDetail\.value, \.\.\.detail \};/g) || []).length,
    6
  );
  assert.doesNotMatch(source, /paymentDetail\.value = detail;/);
});

test("shows the latest payment attempt completion time", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /<span>最近尝试完成时间<\/span>[\s\S]*paymentDetail\?\.latestAttemptCompletedAt/);
});

test("shows the preserved bill and customer facts", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /<span>账单号<\/span>[\s\S]*paymentDetail\?\.billNo/);
  assert.match(source, /<span>客户名称<\/span>[\s\S]*paymentDetail\?\.customerName/);
});

test("shows the server-provided standardized error message", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /<span>标准错误说明<\/span>[\s\S]*paymentDetail\?\.standardizedMessage/);
});

test("shows whether the return-code mapping matched", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /<span>映射命中<\/span>[\s\S]*paymentDetail\?\.returnCodeMappingMatched/);
  assert.match(source, /paymentDetail\.returnCodeMappingMatched \? "是" : "否"/);
});

test("warns when bounded payment polling stops and resumes after a successful manual query", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /autoRefreshPaused = computed\(\(\) => hasValidPaymentSnapshot\.value[\s\S]*autoRefreshAttempts\.value >= PAYMENT_DETAIL_REFRESH_MAX_ATTEMPTS/);
  assert.match(source, /已查询支付单 \$\{snapshot\.paymentOrderId\} 的最新状态。`;\n\s+autoRefreshAttempts\.value = 0;/);
  assert.match(source, /v-if="autoRefreshPaused"[^>]*>自动刷新已暂停，当前展示上次成功查询的状态/);
});

test("offers read-only detail refresh when risk review cannot query the channel", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.equal(shouldAutoRefreshPaymentDetail("RISK_REVIEW"), true);
  assert.equal(canQueryPaymentChannel("RISK_REVIEW", null), false);
  assert.match(source, /@click="refreshPaymentDetail"/);
  assert.match(source, /paymentApi\.getDetail\(snapshot\.paymentOrderId, expectedRoutePayScene\.value\)/);
  assert.match(source, /if \(!matchesPaymentSnapshot\(snapshot, detail\)\) \{/);
  assert.match(source, /autoRefreshAttempts\.value = 0;\n\s+feedbackMessage\.value = "支付详情已刷新。"/);
  assert.match(source, /自动刷新已暂停，当前展示上次成功查询的状态；请刷新支付详情或联系服务方。/);
});
