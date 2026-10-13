import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const viewPath = join(dirname(fileURLToPath(import.meta.url)), "RefundProgressView.vue");

test("does not resume refund polling after leaving the page", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /onBeforeUnmount\(\(\) => \{\s*disposed = true;\s*latestRequestId \+= 1;\s*stopPolling\(\);/);
  assert.match(source, /function schedulePolling\(\) \{\s*stopPolling\(\);\s*if \(disposed \|\|/);
  assert.match(source, /async function refreshDetail\(isAutomatic = false\) \{\s*if \(disposed \|\|/);
});

test("offers a route-bound retry when the first refund detail request fails", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /v-else-if="feedbackMessage && !refundDetail"[\s\S]*v-if="currentRefundOrderId"[^>]*@click="loadDetail\(\)"[^>]*>重试加载退款进度/);
  assert.match(source, /refundOrderId !== currentRefundOrderId\.value/);
});

test("reschedules bounded polling after a failed manual refund refresh", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /else if \(requestId === latestRequestId && !isAutomatic\) \{\s*feedbackMessage\.value = error\.message;\s*schedulePolling\(\);/);
  assert.match(source, /function schedulePolling\(\) \{[\s\S]*isTerminal\.value \|\| !refundDetail\.value \|\| pollingPaused\.value/);
});

test("shows the authoritative channel acceptance time in refund progress", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /<span>渠道受理时间<\/span><strong>\{\{ formatRefundProgressTime\(refundDetail\?\.channelSubmittedAt\) \}\}<\/strong>/);
});

test("does not present an unknown channel submit as accepted on App, H5, or PC", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /unknownSubmission = computed\(\(\) => isUnknownRefundSubmission\(refundDetail\.value\)\)/);
  assert.match(source, /title: "退款渠道结果待核查"/);
  assert.match(source, /渠道是否受理尚未确认/);
  assert.doesNotMatch(source, /退款已进入渠道处理/);
  assert.doesNotMatch(source, /等待自动重试/);
});

test("clears the manual refresh state when loading a new refund", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(
    source,
    /refundDetail\.value = null;\n\s+staleDetail\.value = false;\n\s+feedbackMessage\.value = "";\n\s+refreshing\.value = false;\n\s+loading\.value = true;/
  );
});

test("marks failed refund refresh as unverified until a matching detail succeeds", () => {
  const source = readFileSync(viewPath, "utf8");
  const refreshBody = source.split("async function refreshDetail(isAutomatic = false)")[1].split("\nfunction backToPaymentEntry")[0];
  assert.match(source, /needsVerification = computed\(\(\) => staleDetail\.value[\s\S]*!loading\.value && !refundDetail\.value && Boolean\(feedbackMessage\.value\)/);
  assert.match(source, /title: "退款进度待核查"/);
  assert.match(source, /needsVerification \? "待核查" : refundDetail\?\.status/);
  assert.match(refreshBody, /refundDetail\.value = detail;\s*staleDetail\.value = false;[\s\S]*?catch \(error\) \{\s*if \(requestId === latestRequestId && refundDetail\.value\) \{\s*staleDetail\.value = true;/);
  assert.match(source, /:disabled="refreshing \|\| staleDetail" @click="backToPaymentEntry"/);
  assert.match(source, /v-if="staleDetail"[^>]*>最新退款进度未能确认/);
});

test("shows the preserved customer and payment method facts", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /<span>客户名称<\/span><strong>\{\{ refundDetail\?\.customerName \|\| "-" \}\}<\/strong>/);
  assert.match(source, /<span>支付方式<\/span><strong>\{\{ refundDetail\?\.paymentMethod \|\| "-" \}\}<\/strong>/);
  assert.match(source, /<span>支付场景<\/span><strong>\{\{ refundDetail\?\.payScene \|\| "-" \}\}<\/strong>/);
});

test("binds refund progress refresh snapshots to the payment scene", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /paymentMethod: refundDetail\.value\.paymentMethod,[\s\S]*payScene: refundDetail\.value\.payScene/);
});

test("derives the return entry from the authoritative payment scene", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /resolvePaymentBusinessEntryPath\(refundDetail\.value\?\.payScene\)/);
  assert.match(source, /v-if="paymentEntryPath"[^>]+@click="backToPaymentEntry"/);
  assert.match(source, /v-if="!paymentEntryPath"/);
});

test("shows the authoritative original paid amount", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /<span>原支付金额<\/span><strong>\{\{ refundDetail\?\.paidAmount \|\| "-" \}\}<\/strong>/);
});

test("warns when bounded automatic polling stops and resumes only after a successful manual refresh", () => {
  const source = readFileSync(viewPath, "utf8");
  assert.match(source, /pollingPaused = computed\(\(\) => Boolean\(refundDetail\.value[\s\S]*pollAttempts\.value >= REFUND_PROGRESS_MAX_POLL_ATTEMPTS/);
  assert.match(source, /completedPollAttempts = isAutomatic \? pollAttempts\.value \+ 1 : 0/);
  assert.match(source, /refundDetail\.value = detail;\n\s+staleDetail\.value = false;\n\s+pollAttempts\.value = completedPollAttempts;/);
  assert.match(source, /v-if="pollingPaused"[^>]*>自动刷新已暂停，当前展示上次成功查询的状态/);
});
