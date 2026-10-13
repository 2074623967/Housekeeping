import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const viewPath = join(dirname(fileURLToPath(import.meta.url)), "CashierView.vue");

test("does not recommend channel queries before the cashier permits them", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /到期时间（北京时间）/);
  assert.match(source, /const queryUnavailableGuidance = computed\([\s\S]*cashier\.value\?\.paymentStatus === "PAYING"[\s\S]*PAYING_SUBMIT_HINT/);
  assert.match(source, /submissionPending\.value\) \{\s*return canQueryPayment\.value\s*\? "当前支付结果尚未确认，请先主动查单。"\s*: queryUnavailableGuidance\.value;/);
  assert.match(source, /submissionPending\.value\s*\? canQueryPayment\.value\s*\? "支付结果尚未确认，当前请求已锁定，请先主动查单。"\s*: queryUnavailableGuidance\.value/);
  assert.match(source, /<p>\{\{ canQueryPayment \? bizSceneMeta\.supportHint : queryUnavailableGuidance \}\}<\/p>/);
  assert.match(source, /<p>\{\{ canQueryPayment \? selectedChannelMeta\.settlementHint : queryUnavailableGuidance \}\}<\/p>/);
  assert.match(source, /<p>\{\{ canQueryPayment \? selectedChannelMeta\.fallbackAction : queryUnavailableGuidance \}\}<\/p>/);
  assert.match(source, /fallbackAction: canQueryPayment\.value\s*\? meta\.fallbackAction/);
  assert.match(source, /const countdownRiskHint = computed\(\(\) => \{\s*if \(submissionPending\.value && !canQueryPayment\.value\) \{\s*return queryUnavailableGuidance\.value;/);
  assert.match(source, /v-else class="desktop-qr-unavailable">\{\{ queryUnavailableGuidance \}\}<\/div>/);
  assert.match(source, /<p>\{\{ canQueryPayment \? "如浏览器关闭、扫码超时或渠道报错/);
  assert.match(source, /class="ops-description">\s*\{\{ canQueryPayment \? "扫码后若前端仍停留在支付中/);
});

test("directs channel transaction collisions to human review instead of waiting for submission", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /const queryUnavailableGuidance = computed\(\(\) => channelTransactionConflict\.value[\s\S]*人工核查渠道原始事实与流水归属/);
  assert.match(source, /if \(channelTransactionConflict\.value\) \{\s*return queryUnavailableGuidance\.value/);
  assert.match(source, /const channelTransactionConflict = computed\(\(\) => isChannelTransactionConflict\(cashier\.value\?\.latestAttemptStatus\)\)/);
  assert.match(source, /channelTransactionConflict \? "人工核查中" : reviewRetryAllowed \? "审核通过后继续原支付" : terminalMeta\.actionLabel/);
  assert.match(source, /queryLoading \? "查单中\.\.\." : channelTransactionConflict \? "人工核查中" : "主动查单"/);
  assert.match(source, /if \(cashier\.value\?\.paymentStatus === "PAYING"\) \{\s*message\.value = channelTransactionConflict\.value\s*\? `\$\{error\.message\}；\$\{queryUnavailableGuidance\.value\}`\s*: queryUnavailableGuidance\.value;/);
});

test("queries using the attempt and channel facts from one cashier snapshot", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /const paymentSnapshot = latestQueriedDetail\.value \|\| cashier\.value;[\s\S]*canQueryPaymentChannel\([\s\S]*paymentSnapshot\?\.latestAttemptStatus,[\s\S]*paymentSnapshot\?\.latestAttemptCompletedAt,[\s\S]*paymentSnapshot\?\.channelTransactionNo,[\s\S]*paymentSnapshot\?\.channel/);
  assert.match(source, /const paymentStatus = latestQueriedDetail\.value\s*\? paymentSnapshot\?\.status\s*:\s*paymentSnapshot\?\.paymentStatus;[\s\S]*canQueryPaymentChannel\(\s*paymentStatus,/);
});

test("keeps the bound payment method when a cashier is already waiting for callback", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /\["WAIT_CALLBACK", "SUCCESS"\]\.includes\(cashierPage\.paymentStatus\)[\s\S]*cashierPage\.paymentMethod/);
});

test("shows the bound channel transaction before the first manual query", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /<span>渠道流水号<\/span><span class="mono-text">\{\{ latestQueriedDetail\?\.channelTransactionNo \|\| cashier\?\.channelTransactionNo \|\| "-" \}\}<\/span>/);
});

test("offers a route-bound retry when the cashier first fails to load", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /v-else-if="message && !cashier"[\s\S]*v-if="currentPrepayOrderNo"[^>]*@click="loadCashier\(\)"[^>]*>重试加载收银台/);
  assert.match(source, /prepayOrderNo !== currentPrepayOrderNo\.value/);
});

test("clears the previous query snapshot after a cashier refresh succeeds", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(
    source,
    /cashier\.value = cashierPage;\n\s+pendingSubmitAttempt\.value = pendingAttempt;\n\s+cashierSnapshotStale\.value = false;\n\s+latestQueriedDetail\.value = null;\n\s+loadedPrepayOrderNo\.value = prepayOrderNo;/
  );
});

test("blocks mutations when cashier status cannot be reverified", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /const canSubmit = computed\([\s\S]*return hasValidCashierSnapshot\.value\s+&& !cashierSnapshotStale\.value/);
  assert.match(source, /const canClose = computed\(\(\) => hasValidCashierSnapshot\.value\s+&& !cashierSnapshotStale\.value/);
  assert.match(source, /cashierSnapshotStale\.value = true;\s+message\.value = error\.message;/);
  assert.match(source, /cashierSnapshotStale\.value = true;\s+try \{\s+const cashierPage = await paymentApi\.getCashier/);
  assert.match(source, /cashier\.value = cashierPage;\s+cashierSnapshotStale\.value = false;\s+startCountdown/);
  assert.match(source, /v-if="cashierSnapshotStale"[^>]*>当前为上次已加载的收银台快照/);
  assert.match(source, /hasValidCashierSnapshot\.value && !cashierSnapshotStale\.value && canQueryPaymentChannel/);
  assert.match(source, /:disabled="actionRunning \|\| submissionPending \|\| pendingSubmitAttempt \|\| cashierSnapshotStale" @click="regenerateIdempotencyKey"/);
});

test("keeps confirmed cleanup failures read-only after a cashier reload", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /const canSubmit = computed\([\s\S]*!pendingSubmitAttempt\.value\?\.confirmed/);
  assert.match(source, /if \(pendingSubmitAttempt\.value\?\.confirmed\) \{\s*return "支付响应已返回，但本机未决记录未能清理/);
  assert.match(source, /pendingSubmitAttempt\.value = \{ \.\.\.attempt, confirmed: true \};\s*clearPaymentSubmitAttempt\(attempt\);/);
  assert.match(source, /pendingSubmitAttempt\.value\?\.confirmed[\s\S]*\? pendingSubmitAttempt\.value\s*: savedAttempt/);
});

test("blocks local close while a payment submit key still needs reconciliation", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /const canClose = computed\(\(\) => hasValidCashierSnapshot\.value\s+&& !cashierSnapshotStale\.value\s+&& !pendingSubmitAttempt\.value/);
});

test("rechecks the cashier after a failed query or close and blocks unverified actions", () => {
  const source = readFileSync(viewPath, "utf8");
  for (const action of ["queryCurrentPayment", "closeCurrentPayment"]) {
    const actionBody = source.split(`async function ${action}()`)[1].split("\nasync function ")[0];
    assert.match(actionBody, /catch \(error\) \{\s*await readBackAfterFailedAction\(snapshot, entityVersion, error\);/);
  }
  const readbackBody = source.split("async function readBackAfterFailedAction(")[1].split("\nasync function ")[0];
  assert.match(readbackBody, /cashierSnapshotStale\.value = true;[\s\S]*paymentApi\.getCashier\([\s\S]*expectedRoutePayScene\.value/);
  assert.match(readbackBody, /if \(!matchesPaymentSnapshot\(snapshot, cashierPage\)\)/);
  assert.match(readbackBody, /cashier\.value = cashierPage;[\s\S]*latestQueriedDetail\.value = null;[\s\S]*cashierSnapshotStale\.value = false/);
  assert.match(readbackBody, /paymentStatus === "SUCCESS" \|\| cashierPage\.paymentStatus === "CLOSED"[\s\S]*buildPaymentResultRoute\(/);
  assert.match(readbackBody, /catch \{[\s\S]*支付状态未经确认，请刷新收银台，勿重复付款/);
  assert.match(source, /await loadCashier\(currentPrepayOrderNo\.value\);[\s\S]*!cashierSnapshotStale\.value[\s\S]*\["SUCCESS", "CLOSED"\][\s\S]*buildPaymentResultRoute\(/);
  assert.match(source, /:disabled="actionRunning \|\| submissionPending \|\| pendingSubmitAttempt \|\| cashierSnapshotStale \|\| cashier\?\.paymentStatus === 'RISK_REVIEW'"/);
});

test("retains the submit error and warns when its readback cannot confirm payment", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /cashierSnapshotStale\.value = true;\s+try \{\s+const cashierPage = await paymentApi\.getCashier/);
  assert.match(source, /message\.value = `\$\{error\.message\}；\$\{refreshError\.message\}；支付状态未经确认，请刷新收银台，勿重复付款。`;/);
  assert.match(source, /const canQueryPayment = computed\([\s\S]*!cashierSnapshotStale\.value/);
});

test("keeps cashier mutations locked when a saved submit attempt is corrupt", () => {
  const source = readFileSync(viewPath, "utf8");
  const submitCatch = source.split("async function pay()")[1].split("\nasync function ")[0];
  assert.match(submitCatch, /cashierSnapshotStale\.value = true;\s+if \(error instanceof UnverifiablePaymentSubmitAttemptError\) \{[\s\S]*return;/);
  assert.match(source, /cashierSnapshotStale\.value = true;\s+message\.value = error\.message;/);
});

test("opens a verified final result despite a corrupt local pending record", () => {
  const source = readFileSync(viewPath, "utf8");
  const loadBody = source.split("async function loadCashier(")[1].split("\nonBeforeUnmount(")[0];
  assert.match(loadBody, /readPaymentSubmitAttemptForPrepay\(prepayOrderNo\)[\s\S]*error instanceof UnverifiablePaymentSubmitAttemptError/);
  assert.match(loadBody, /\["SUCCESS", "CLOSED"\]\.includes\(cashierPage\.paymentStatus\)[\s\S]*router\.push\(buildPaymentResultRoute\(\s*cashierPage\.paymentOrderId/);
});

test("binds cashier loads to the route business scene", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /expectedRoutePayScene = computed/);
  assert.match(source, /paymentApi\.getCashier\([\s\S]*expectedRoutePayScene\.value/);
  assert.match(source, /paymentApi\.submit\([\s\S]*expectedRoutePayScene\.value/);
});

test("only offers an entry and scene guidance matching the loaded cashier", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /resolvePaymentBusinessEntryPath\(cashier\.value\?\.payScene\)/);
  assert.match(source, /BIZ_SCENE_META\[businessEntryPath\.value\?\.slice\(1\)\] \|\| GENERIC_SCENE_META/);
  assert.match(source, /v-if="businessEntryPath"[^>]+@click="backToBusinessEntry"/);
  assert.match(source, /v-if="!businessEntryPath && hasValidCashierSnapshot"/);
});

test("invalidates the cashier snapshot when the route business scene changes", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /watch\(\[currentPrepayOrderNo, expectedRoutePayScene\], \(\[prepayOrderNo\]\) => \{/);
  assert.match(source, /watch\(\[currentPrepayOrderNo, expectedRoutePayScene\][\s\S]*cashier\.value = null;[\s\S]*loadedPrepayOrderNo\.value = "";[\s\S]*loadCashier\(prepayOrderNo\);/);
});

test("blocks payment submission until the access token is available", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /&& accessTokenAvailable\.value\n\s+&& !submissionPending\.value/);
  assert.match(source, /if \(!accessTokenAvailable\.value\) \{[\s\S]*?安全配置后再提交/);
});

test("syncs legacy access tokens when the cashier query changes", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /watch\(\(\) => route\.query\.accessToken, syncAccessToken\);/);
});

test("routes to the result page when recovery refresh finds a final payment", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(
    source,
    /cashier\.value = cashierPage;[\s\S]*?cashierPage\.paymentStatus === "SUCCESS" \|\| cashierPage\.paymentStatus === "CLOSED"[\s\S]*?buildPaymentResultRoute\(/,
  );
});

test("uses the cashier channel transaction when no query detail exists", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(
    source,
    /latestQueriedDetail\.value\?\.channelTransactionNo\s*\|\|\s*cashier\.value\?\.channelTransactionNo/
  );
});

test("binds cashier actions to the selected payment method", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /paymentMethod: selectedPaymentMethod\.value/);
  assert.match(source, /const submitPayload = \{[\s\S]*paymentMethod,/);
  assert.match(source, /paymentApi\.submit\(\{\s*\.\.\.submitPayload,\s*idempotencyKey: attempt\.idempotencyKey/);
});

test("recovers a pending submit key before POST and drops it only after a trusted response", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /resolvePaymentSubmitAttempt\(submitPayload, idempotencyKey\.value\)/);
  assert.match(source, /const attempt = await resolvePaymentSubmitAttempt[\s\S]*?if \(!isCurrentPaymentSnapshot\(snapshot, entityVersion\)\) \{\s*return;\s*\}[\s\S]*?paymentApi\.submit/);
  assert.match(source, /if \(!matchesPaymentSnapshot\(snapshot, submitResult\)\) \{[\s\S]*?clearPaymentSubmitAttempt\(attempt\)/);
  assert.match(source, /resetLoading\.value = true;[\s\S]*?await discardPaymentSubmitAttempt\(prepayOrderNo\);[\s\S]*?idempotencyKey\.value = generateIdempotencyKey\(\)/);
  assert.match(source, /const actionRunning = computed\(\(\) => loading\.value[\s\S]*?\|\| resetLoading\.value/);
});

test("locks payment method and key reset while a submit remains unresolved", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /readPaymentSubmitAttemptForPrepay\(prepayOrderNo\)/);
  assert.match(source, /prepayOrderNo === loadedPrepayOrderNo\.value \? pendingSubmitAttempt\.value : null/);
  assert.match(source, /pendingSubmitAttempt\.value = pendingAttempt/);
  assert.match(source, /pendingAttempt\.paymentMethod/);
  assert.match(source, /&& \(!pendingSubmitAttempt\.value[\s\S]*?selectedPaymentMethod\.value\)/);
  assert.match(source, /:disabled="actionRunning \|\| submissionPending \|\| pendingSubmitAttempt \|\| cashierSnapshotStale/);
  assert.match(source, /if \(pendingSubmitAttempt\.value\) \{\s*message\.value = "本次支付提交结果尚未核实/);
});

test("binds cashier query and close actions to the route payment scene", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /paymentApi\.query\([\s\S]*expectedRoutePayScene\.value/);
  assert.match(source, /paymentApi\.close\([\s\S]*expectedRoutePayScene\.value/);
  assert.match(source, /customerName: cashier\.value\?\.customerName,[\s\S]*payScene: cashier\.value\?\.payScene/);
});

test("preserves the raw payment scene when routing to the result page", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /routeQuery\.payScene = expectedRoutePayScene\.value/);
});

test("blocks submission when cashier attempt or channel facts already exist", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /const attemptStatus = latestQueriedDetail\.value\?\.latestAttemptStatus/);
  assert.match(source, /\|\| cashier\.value\?\.latestAttemptStatus/);
  assert.match(source, /const channelTransactionNo = latestQueriedDetail\.value\?\.channelTransactionNo/);
  assert.match(source, /\|\| cashier\.value\?\.channelTransactionNo/);
  assert.match(source, /&& !hasAttemptFact\n\s+&& !hasSubmittedChannel/);
  assert.match(source, /const queriedStatus = latestQueriedDetail\.value\?\.status/);
  assert.match(source, /&& \(!queriedStatus \|\| queriedStatus === "PREPAY_CREATED"\s+\|\| \(queriedStatus === "RISK_REVIEW" && reviewRetryAllowed\.value\)\)/);
  assert.match(source, /canRetryReviewedPayment\(cashier\.value, selectedPaymentMethod\.value\)/);
  assert.match(source, /cashier\?\.paymentStatus === 'RISK_REVIEW'/);
});
