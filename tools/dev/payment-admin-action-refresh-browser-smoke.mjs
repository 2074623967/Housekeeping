#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { existsSync } from "node:fs";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { createServer } from "node:http";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { normalizePaymentTaskCenterOverviewResult } from "../../systems/payment-core/frontend/admin-web/src/api/client.js";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../systems/payment-core/frontend/admin-web/dist");
const chromeBinary = process.env.HSP_CHROME_BIN
  || "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const eventNo = "EVENT-LOCAL-001";
const refundOrderId = "REF-LOCAL-001";
const appliedRefundOrderId = "REF-APPLY-LOCAL-001";
const paymentOrderId = "PAY-CLOSE-LOCAL-001";
const queryPaymentOrderId = "PAY-QUERY-LOCAL-001";
const recordPaymentOrderId = "PAY-RECORD-LOCAL-001";
const initialEvent = {
  eventNo, eventType: "PAYMENT_SUCCESS", paymentOrderId: "PAY-LOCAL-001",
  businessChainId: "CHAIN-PAY-LOCAL-001", successFactIssue: null,
  bizNo: "ORDER-LOCAL-001", eventTopic: "payment.trade.succeeded.v1",
  downstreamSystem: "ACCOUNTING", publishStatus: "FAILED", publishStatusType: "danger",
  paymentSuccessHttpStage: "PENDING", retryCount: 1,
  eventPayload: '{"paymentOrderId":"PAY-LOCAL-001"}',
  createdAt: "2026-10-09 10:00:00", lastPublishedAt: "2026-10-09 10:00:01",
  nextRetryAt: "2026-10-09 10:00:02"
};
const initialRefund = {
  refundOrderId, paymentOrderId: "PAY-LOCAL-001",
  businessChainId: "CHAIN-PAY-LOCAL-001", orderNo: "ORDER-LOCAL-001",
  paidAmount: "68.00", refundAmount: "18.00", status: "REVIEWING",
  statusType: "warn", appliedAt: "2026-10-09 10:00:00",
  attempts: [], operationLogs: []
};
const appliedRefund = {
  ...initialRefund, refundOrderId: appliedRefundOrderId,
  paymentOrderId: "PAY-APPLY-LOCAL-001", businessChainId: "CHAIN-PAY-APPLY-LOCAL-001",
  orderNo: "ORDER-APPLY-LOCAL-001", customerName: "本地申请用户",
  refundAmount: "12.00", refundMethod: "原路退款", refundReason: "服务取消"
};
const initialPayment = {
  paymentOrderId, businessChainId: `CHAIN-${paymentOrderId}`,
  prepayOrderNo: "PREPAY-CLOSE-LOCAL-001", payScene: "HOME_CLEAN",
  orderNo: "ORDER-CLOSE-LOCAL-001", customerName: "本地支付用户",
  amount: "¥68.00", paymentMethod: "微信", channel: "wx_h5",
  channelTransactionNo: null, status: "PREPAY_CREATED", statusType: "info",
  successFactIssue: null, prepayBindingIssue: null,
  latestAttemptStatus: null, latestAttemptStatusType: null,
  latestAttemptCompletedAt: null, successAt: null, createdAt: "2026-10-09 10:00:00"
};
const initialQueryPayment = {
  ...initialPayment, paymentOrderId: queryPaymentOrderId,
  businessChainId: `CHAIN-${queryPaymentOrderId}`,
  prepayOrderNo: "PREPAY-QUERY-LOCAL-001", orderNo: "ORDER-QUERY-LOCAL-001",
  channelTransactionNo: "LOCAL-TXN-001", status: "WAIT_CALLBACK", statusType: "warn",
  latestAttemptStatus: "处理中", latestAttemptStatusType: "info"
};
const initialPaymentRecord = {
  serialNo: 1, paymentOrderId: recordPaymentOrderId,
  businessChainId: `CHAIN-${recordPaymentOrderId}`,
  businessOrderNo: "ORDER-RECORD-LOCAL-001", payScene: "HOME_CLEAN",
  prepayBindingIssue: null, successFactIssue: null,
  paymentRequestNo: "REQ-RECORD-LOCAL-001", applicationSourceId: "housekeeping-admin",
  businessLineId: null, externalTransactionNo: null,
  paymentGateway: null, paymentChannel: "wx_h5", paymentMethod: "微信",
  paymentType: "消费支付", paymentStatus: "支付中", statusType: "warn",
  bankName: null, cardNo: null, receivingAccount: null, channelReturnCode: null,
  returnParameterType: null, parameterValue: null, validityPeriod: "1小时",
  paymentAmount: "¥68.00", refundCount: 0, refundedAmount: "¥0.00",
  unverifiedRefundCount: 0, productName: null, userPaymentChannelId: null,
  notifyUrl: null, callbackMqTopic: null, userId: null,
  expireTime: "2026-10-09 11:00:00", createdAt: "2026-10-09 10:00:00",
  updatedAt: "2026-10-09 10:01:00", paidAt: null,
  latestAttemptStatus: "渠道提交中", latestAttemptStatusType: "info",
  latestAttemptCompletedAt: null, routeLogs: [], notifyLogs: [], eventLogs: []
};
const taskOverview = {
  expiredPaymentCount: 1, dueExpiredPaymentCount: 1, backingOffExpiredPaymentCount: 0,
  blockedExpiredPaymentCount: 0, expiredActiveSubmitTokenCount: 0,
  pendingCallbackCount: 0, pendingQueryBacklogCount: 0, duePendingQueryCount: 0,
  pendingRefundQueryBacklogCount: 0, duePendingRefundQueryCount: 0,
  queryMonitorWindowHours: 24, recentQueryCount: 0, recentQueryFailureCount: 0,
  recentQueryFailureRate: "0.00%", queryFailureCountThreshold: 5,
  queryFailureRateThresholdPercent: 30, queryFailureRateMinimumSampleCount: 10,
  recentQueryRateLimitRejectionCount: 0, queryRateLimitRejectionCountThreshold: 10,
  actionableQueryMonitorAlertCount: 0, pendingIssueAlertDispatchCount: 0,
  failedIssueAlertDispatchCount: 0, oldestPendingIssueAlertDispatchMinutes: 0,
  issueAlertDispatchCountThreshold: 10, issueAlertDispatchTimeoutMinutes: 15,
  pendingIssueAlertReceiptCount: 0, manualIssueAlertReceiptCount: 0,
  dueIssueAlertReceiptCount: 0, backingOffIssueAlertReceiptCount: 0,
  queryingIssueAlertReceiptCount: 0, exhaustedIssueAlertReceiptCount: 0,
  oldestPendingIssueAlertReceiptMinutes: 0, issueAlertReceiptCountThreshold: 10,
  issueAlertReceiptTimeoutMinutes: 30, queryTrends: [], failedEventCount: 0,
  retryableFailedEventCount: 0, deadLetterEventCount: 0,
  unknownRefundSubmitEventCount: 0, failedRefundCount: 0,
  retryableFailedRefundCount: 0, blockedFailedRefundCount: 0,
  warningDayEndBatchCount: 0, enabledControlPolicyCount: 0,
  controlPolicyWarningCount: 0, overdueIssueCount: 0,
  pendingIssueAlertGenerationCount: 0, pendingIssueAlertEscalationCount: 0,
  actionableIssueSlaCount: 0, focusAlerts: [], recentTaskRuns: []
};
normalizePaymentTaskCenterOverviewResult(taskOverview);
const deadLetterTaskNo = "DEAD-LOCAL-001";
const initialDeadLetterTask = {
  taskNo: deadLetterTaskNo, messageId: "MESSAGE-LOCAL-001",
  correlationId: "CORRELATION-LOCAL-001", targetSystem: "clearing-system",
  deadLetterRoutingKey: "payment.success.clearing.dlq.v1", replayExchange: "payment.trade.replay",
  replayRoutingKey: "payment.success.clearing.replay.v1", sourceSignatureVerified: true,
  taskStatus: "READY_TO_REPLAY",
  taskStatusType: "warn", replayCount: 0, operator: "reviewer",
  resolutionNote: "已核对原消息", payloadSnapshot: "{}", headerSnapshot: "{}",
  createdAt: "2026-10-09 10:00:00", updatedAt: "2026-10-09 10:01:00",
  resolvedAt: "2026-10-09 10:01:00", lastReplayAt: null
};
const issueNo = "ISSUE-LOCAL-001";
const initialIssue = {
  issueNo, paymentOrderId: "PAY-ISSUE-LOCAL-001", businessChainId: "CHAIN-PAY-ISSUE-LOCAL-001",
  orderNo: "ORDER-ISSUE-LOCAL-001", customerName: "本地用户", paymentMethod: "支付宝",
  channelCode: "alipay_h5", issueType: "待回调未收口", issueTypeTag: "danger",
  severity: "P1", severityType: "danger", paymentStatus: "WAIT_CALLBACK",
  paymentStatusType: "warn", handlingStatus: "待分派", handlingStatusType: "warn",
  assignee: "未分派", responsibilityGroup: "支付后端值班组", responsibilityGroupType: "info",
  alertStatus: "未通知", alertStatusType: "info", alertAckStatus: "待确认",
  alertAckStatusType: "warn", alertReceiver: "未分配", slaStatus: "SLA内",
  slaStatusType: "success", escalationStatus: "当班跟进", escalationStatusType: "info",
  issueSummary: "支付单仍处于待回调状态", rootCauseHint: "建议主动查单",
  recommendedAction: "核对回调", recommendedRoute: "/payments/PAY-ISSUE-LOCAL-001",
  responsibilityHint: "支付后端值班组", alertChannelSummary: "未派发",
  alertDeliverySummary: "暂无派发明细", latestAlertProviderSummary: "暂无供应商派发记录",
  latestAlertReceiptSummary: "暂无投递回执", slaTimeLeft: "SLA内",
  escalationSuggestion: "当班跟进", latestActionSummary: "暂无处理动作",
  createdAt: "2026-10-09 10:00:00", latestActionAt: null
};

const delay = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));

async function waitFor(condition, label) {
  for (let attempt = 0; attempt < 100; attempt++) {
    if (await condition()) return;
    await delay(100);
  }
  throw new Error(`等待 ${label} 超时`);
}

function overview(status) {
  return {
    totalEventCount: 1, successEventCount: 0, pendingEventCount: 0,
    failedEventCount: status === "FAILED" ? 1 : 0, deadLetterEventCount: 0,
    unknownEventCount: status === "UNKNOWN" ? 1 : 0,
    failedOrDeadLetterCount: status === "FAILED" ? 1 : 0,
    distinctDownstreamCount: 1, dueRetryEventCount: status === "FAILED" ? 1 : 0,
    paymentSuccessEventCount: 0, latestPublishedAt: "2026-10-09 10:00:01"
  };
}

async function main() {
  assert.ok(existsSync(path.join(root, "index.html")), "请先构建管理端：admin-web npm run build");
  assert.ok(existsSync(chromeBinary), `未找到 Chrome：${chromeBinary}`);
  let status = "FAILED";
  let failListOnce = false;
  let refundStatus = "REVIEWING";
  let failDetailOnce = false;
  let failRefundListOnce = false;
  let holdRefundApproval = false;
  let releaseRefundApproval;
  let paymentStatus = "PREPAY_CREATED";
  let paymentScenario = "close";
  let holdPaymentAction = false;
  let releasePaymentAction;
  let querySubmissionUnaccepted = false;
  let recordSubmissionUnaccepted = true;
  let failPaymentReadbackOnce = false;
  let taskExecuted = false;
  let failTaskRunReadbackOnce = false;
  let taskOverviewLag = false;
  let deadLetterReplayed = false;
  let failDeadLetterReadbackOnce = false;
  let issueHandled = false;
  let isolateIssueActions = false;
  let holdIssueAction = false;
  let releaseIssueAction;
  let failIssueListReadbackOnce = false;
  let failIssueSummaryReadbackOnce = false;
  const issueActionRequests = [];
  const applicationRequests = [];
  const requests = [];
  const server = createServer(async (request, response) => {
    const pathname = new URL(request.url, "http://localhost").pathname;
    if (pathname.startsWith("/api/")) {
      requests.push({ method: request.method, pathname, search: new URL(request.url, "http://localhost").search });
      response.setHeader("Content-Type", "application/json; charset=utf-8");
      if (request.method === "POST" && pathname === "/api/payment-events/republish") {
        const body = await new Promise((resolve) => {
          let input = "";
          request.on("data", (chunk) => { input += chunk; });
          request.on("end", () => resolve(JSON.parse(input)));
        });
        assert.deepEqual(body, { eventNo, operator: "local-operator" });
        assert.equal(status, "FAILED", "不得按旧快照重复重发");
        status = "UNKNOWN";
        response.statusCode = 400;
        response.end(JSON.stringify({ code: "PAYMENT-400", message: "投递结果待核查" }));
      } else if (request.method === "GET" && pathname === "/api/payment-events/overview") {
        response.end(JSON.stringify({ code: "0", data: overview(status) }));
      } else if (request.method === "GET" && pathname === "/api/payment-events") {
        if (failListOnce) {
          failListOnce = false;
          response.statusCode = 503;
          response.end(JSON.stringify({ code: "PAYMENT-503", message: "临时不可用" }));
        } else {
          const item = status === "FAILED" ? initialEvent : {
            ...initialEvent, publishStatus: "UNKNOWN", publishStatusType: "warn",
            nextRetryAt: null, retryCount: 2,
            lastRepublishOperator: "local-operator", lastRepublishedAt: "2026-10-09 10:00:03",
            lastPublishedAt: "2026-10-09 10:00:03"
          };
          response.end(JSON.stringify({ code: "0", data: { items: [item], total: 1 } }));
        }
      } else if (request.method === "POST" && pathname === "/api/refunds/approve") {
        const body = await new Promise((resolve) => {
          let input = "";
          request.on("data", (chunk) => { input += chunk; });
          request.on("end", () => resolve(JSON.parse(input)));
        });
        assert.deepEqual(body, { refundOrderId, operator: "local-operator", remark: "" });
        assert.equal(refundStatus, "REVIEWING", "不得按旧快照重复审核");
        refundStatus = "PROCESSING";
        if (holdRefundApproval) {
          await new Promise((resolve) => { releaseRefundApproval = resolve; });
        }
        response.statusCode = 400;
        response.end(JSON.stringify({ code: "PAYMENT-400", message: "渠道受理状态待核查" }));
      } else if (request.method === "POST" && pathname === "/api/refunds/apply") {
        const body = await new Promise((resolve) => {
          let input = "";
          request.on("data", (chunk) => { input += chunk; });
          request.on("end", () => resolve(JSON.parse(input)));
        });
        assert.equal(body.paymentOrderId, appliedRefund.paymentOrderId);
        assert.equal(body.refundAmount, "12");
        assert.equal(body.refundReason, appliedRefund.refundReason);
        assert.equal(body.operator, "local-operator");
        assert.ok(typeof body.idempotencyKey === "string" && body.idempotencyKey.length > 0);
        if (applicationRequests.length) {
          assert.deepEqual(body, applicationRequests[0], "同一申请重试必须复用幂等键与业务指纹");
        }
        applicationRequests.push(body);
        if (applicationRequests.length === 1) {
          response.statusCode = 400;
          response.end(JSON.stringify({ code: "PAYMENT-400", message: "申请响应丢失" }));
        } else {
          response.end(JSON.stringify({ code: "0", data: appliedRefund }));
        }
      } else if (request.method === "GET" && pathname === `/api/refunds/${refundOrderId}`) {
        if (failDetailOnce) {
          failDetailOnce = false;
          response.statusCode = 503;
          response.end(JSON.stringify({ code: "PAYMENT-503", message: "临时不可用" }));
        } else {
          response.end(JSON.stringify({ code: "0", data: {
            ...initialRefund, status: refundStatus, statusType: "warn"
          } }));
        }
      } else if (request.method === "GET" && pathname === "/api/refunds") {
        if (failRefundListOnce) {
          failRefundListOnce = false;
          response.statusCode = 503;
          response.end(JSON.stringify({ code: "PAYMENT-503", message: "临时不可用" }));
        } else {
          const items = new URL(request.url, "http://localhost").searchParams.get("refundStatus") === "SUCCESS" ? [] : [{
            ...initialRefund, customerName: "本地测试用户", refundMethod: "原路退款",
            status: refundStatus, statusType: "warn"
          }];
          if (applicationRequests.length) items.push(appliedRefund);
          response.end(JSON.stringify({ code: "0", data: { items, total: items.length } }));
        }
      } else if (request.method === "POST" && pathname === "/api/payments/admin/close") {
        const body = await new Promise((resolve) => {
          let input = "";
          request.on("data", (chunk) => { input += chunk; });
          request.on("end", () => resolve(JSON.parse(input)));
        });
        assert.deepEqual(body, { paymentOrderId, operator: "local-operator" });
        assert.equal(paymentStatus, "PREPAY_CREATED", "不得按旧支付快照重复关闭");
        paymentStatus = "CLOSED";
        if (holdPaymentAction) {
          await new Promise((resolve) => { releasePaymentAction = resolve; });
        }
        response.statusCode = 400;
        response.end(JSON.stringify({ code: "PAYMENT-400", message: "关闭响应丢失" }));
      } else if (request.method === "POST" && pathname === "/api/payments/admin/query") {
        const body = await new Promise((resolve) => {
          let input = "";
          request.on("data", (chunk) => { input += chunk; });
          request.on("end", () => resolve(JSON.parse(input)));
        });
        assert.deepEqual(body, { paymentOrderId: queryPaymentOrderId, operator: "local-operator" });
        assert.equal(paymentStatus, "WAIT_CALLBACK", "不得按旧支付快照重复查单");
        paymentStatus = "FAIL";
        if (holdPaymentAction) {
          await new Promise((resolve) => { releasePaymentAction = resolve; });
        }
        response.statusCode = 400;
        response.end(JSON.stringify({ code: "PAYMENT-400", message: "查单响应丢失" }));
      } else if (request.method === "GET" && (pathname === "/api/payments"
        || pathname === `/api/payments/${paymentOrderId}`
        || pathname === `/api/payments/${queryPaymentOrderId}`)) {
        if (failPaymentReadbackOnce) {
          failPaymentReadbackOnce = false;
          response.statusCode = 503;
          response.end(JSON.stringify({ code: "PAYMENT-503", message: "临时不可用" }));
        } else {
          const item = paymentScenario === "query" ? {
            ...initialQueryPayment, status: paymentStatus,
            statusType: paymentStatus === "FAIL" ? "danger" : "warn",
            channelTransactionNo: querySubmissionUnaccepted ? null : initialQueryPayment.channelTransactionNo,
            latestAttemptStatus: paymentStatus === "FAIL" ? "失败"
              : querySubmissionUnaccepted ? "渠道提交中" : "处理中",
            latestAttemptStatusType: paymentStatus === "FAIL" ? "danger" : "info"
          } : { ...initialPayment, status: paymentStatus,
            statusType: paymentStatus === "CLOSED" ? "danger" : "info" };
          const filterStatus = new URL(request.url, "http://localhost").searchParams.get("status");
          const filteredItems = filterStatus === "SUCCESS" ? [] : [item];
          response.end(JSON.stringify({ code: "0", data: pathname === "/api/payments"
            ? { items: filteredItems, total: filteredItems.length } : item }));
        }
      } else if (request.method === "GET" && (pathname === "/api/payment-records"
        || pathname === `/api/payment-records/${recordPaymentOrderId}`)) {
        const item = {
          ...initialPaymentRecord,
          externalTransactionNo: recordSubmissionUnaccepted ? null : "WX-RECORD-ACCEPTED"
        };
        response.end(JSON.stringify({ code: "0", data: pathname === "/api/payment-records"
          ? { items: [item], total: 1 } : item }));
      } else if (request.method === "POST" && pathname === "/api/payment-task-center/close-expired-payments") {
        assert.equal(taskExecuted, false, "不得按旧候选重复执行任务");
        taskExecuted = true;
        response.statusCode = 400;
        response.end(JSON.stringify({ code: "PAYMENT-400", message: "任务响应丢失" }));
      } else if (request.method === "GET" && pathname === "/api/payment-task-center/overview") {
        response.end(JSON.stringify({ code: "0", data: {
          ...taskOverview, expiredPaymentCount: taskExecuted && !taskOverviewLag ? 0 : 1,
          dueExpiredPaymentCount: taskExecuted && !taskOverviewLag ? 0 : 1
        } }));
      } else if (request.method === "GET" && pathname === "/api/payment-task-center/task-runs") {
        if (failTaskRunReadbackOnce) {
          failTaskRunReadbackOnce = false;
          response.statusCode = 503;
          response.end(JSON.stringify({ code: "PAYMENT-503", message: "日志暂不可用" }));
        } else {
          response.end(JSON.stringify({ code: "0", data: { items: [], total: 0 } }));
        }
      } else if (request.method === "POST" && pathname === `/api/payment-dead-letter-tasks/${deadLetterTaskNo}/replay`) {
        assert.equal(deadLetterReplayed, false, "不得按旧死信快照再次重放");
        deadLetterReplayed = true;
        response.statusCode = 400;
        response.end(JSON.stringify({ code: "PAYMENT-400", message: "重放响应丢失" }));
      } else if (request.method === "GET" && pathname === "/api/payment-dead-letter-tasks") {
        if (failDeadLetterReadbackOnce) {
          failDeadLetterReadbackOnce = false;
          response.statusCode = 503;
          response.end(JSON.stringify({ code: "PAYMENT-503", message: "列表暂不可用" }));
        } else {
          const item = deadLetterReplayed ? {
            ...initialDeadLetterTask, taskStatus: "REPLAYED", taskStatusType: "success",
            replayCount: 1, lastReplayAt: "2026-10-09 10:02:00",
            updatedAt: "2026-10-09 10:02:00"
          } : initialDeadLetterTask;
          response.end(JSON.stringify({ code: "0", data: { items: [item], total: 1 } }));
        }
      } else if (request.method === "POST" && pathname === "/api/payment-issues/actions") {
        const body = await new Promise((resolve) => {
          let input = "";
          request.on("data", (chunk) => { input += chunk; });
          request.on("end", () => resolve(JSON.parse(input)));
        });
        assert.ok(typeof body.idempotencyKey === "string" && body.idempotencyKey.length > 0);
        if (isolateIssueActions) {
          issueActionRequests.push(body);
          issueHandled = true;
          response.statusCode = 400;
          response.end(JSON.stringify({ code: "PAYMENT-400", message: "批量响应丢失" }));
          return;
        }
        if (issueActionRequests.length) {
          assert.deepEqual(body, issueActionRequests[0], "同一异常动作重试必须复用请求键与内容");
        }
        issueActionRequests.push(body);
        assert.ok(issueActionRequests.length <= 2, "不得重复提交已确认的异常动作");
        issueHandled = true;
        if (holdIssueAction) {
          await new Promise((resolve) => { releaseIssueAction = resolve; });
        }
        if (issueActionRequests.length === 1) {
          response.statusCode = 400;
          response.end(JSON.stringify({ code: "PAYMENT-400", message: "批量响应丢失" }));
        } else {
          response.end(JSON.stringify({ code: "0", data: { items: [{
            ...initialIssue, handlingStatus: "已分派", handlingStatusType: "info", assignee: "支付运营",
            latestActionSummary: "已分派支付运营", latestActionAt: "2026-10-09 10:01:00"
          }], total: 1, pageNo: 1, pageSize: 1 } }));
        }
      } else if (request.method === "GET" && pathname === "/api/payment-issues/responsibility-summary") {
        if (failIssueSummaryReadbackOnce) {
          failIssueSummaryReadbackOnce = false;
          response.statusCode = 503;
          response.end(JSON.stringify({ code: "PAYMENT-503", message: "责任组暂不可用" }));
        } else {
          response.end(JSON.stringify({ code: "0", data: [{
            groupName: "支付后端值班组", groupType: "danger", totalCount: 1,
            overdueCount: 0, p1Count: 1, p2Count: 0, suggestedAction: "核对回调"
          }] }));
        }
      } else if (request.method === "GET" && pathname === "/api/payment-issues") {
        if (failIssueListReadbackOnce) {
          failIssueListReadbackOnce = false;
          response.statusCode = 503;
          response.end(JSON.stringify({ code: "PAYMENT-503", message: "异常列表暂不可用" }));
        } else {
          const item = issueHandled ? {
            ...initialIssue, handlingStatus: "已分派", handlingStatusType: "info",
            assignee: "支付运营", latestActionSummary: "已分派支付运营",
            latestActionAt: "2026-10-09 10:01:00"
          } : initialIssue;
          const severity = new URL(request.url, "http://localhost").searchParams.get("severity");
          const filteredItems = severity === "P2" ? [] : [item];
          response.end(JSON.stringify({ code: "0", data: { items: filteredItems, total: filteredItems.length } }));
        }
      } else {
        response.statusCode = 404;
        response.end(JSON.stringify({ code: "NOT_FOUND" }));
      }
      return;
    }
    const file = pathname.startsWith("/assets/")
      ? path.resolve(root, `.${pathname}`) : path.join(root, "index.html");
    if (!file.startsWith(`${root}${path.sep}`)) {
      response.writeHead(403).end();
      return;
    }
    try {
      const content = await readFile(file);
      response.setHeader("Content-Type", file.endsWith(".js") ? "text/javascript"
        : file.endsWith(".css") ? "text/css" : "text/html");
      response.end(content);
    } catch {
      response.writeHead(404).end();
    }
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const baseUrl = `http://127.0.0.1:${server.address().port}`;
  const profile = await mkdtemp(path.join(os.tmpdir(), "hsp-admin-action-refresh-"));
  const chrome = spawn(chromeBinary, [
    "--headless", "--disable-gpu", "--disable-background-networking",
    "--disable-component-update", "--no-first-run", "--no-default-browser-check",
    "--no-proxy-server", "--remote-allow-origins=*", "--remote-debugging-port=0",
    `--user-data-dir=${profile}`, "about:blank"
  ], { stdio: "ignore" });
  let socket;
  try {
    let port;
    await waitFor(async () => {
      try {
        port = (await readFile(path.join(profile, "DevToolsActivePort"), "utf8")).split("\n")[0];
        return Boolean(port);
      } catch {
        return false;
      }
    }, "Chrome 调试端口");
    const targets = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
    socket = new WebSocket(targets.find((item) => item.type === "page").webSocketDebuggerUrl);
    await new Promise((resolve, reject) => {
      socket.addEventListener("open", resolve, { once: true });
      socket.addEventListener("error", reject, { once: true });
    });
    let commandId = 0;
    const pending = new Map();
    socket.addEventListener("message", ({ data }) => {
      const message = JSON.parse(data);
      const command = pending.get(message.id);
      if (!command) return;
      pending.delete(message.id);
      message.error ? command.reject(new Error(message.error.message)) : command.resolve(message.result);
    });
    const send = (method, params = {}) => new Promise((resolve, reject) => {
      const id = ++commandId;
      pending.set(id, { resolve, reject });
      socket.send(JSON.stringify({ id, method, params }));
    });
    const evaluate = async (expression) => {
      const result = await send("Runtime.evaluate", { expression, returnByValue: true });
      if (result.exceptionDetails) throw new Error(result.exceptionDetails.text);
      return result.result.value;
    };
    const eventButton = `Array.from(document.querySelectorAll('tbody tr')).find(
      (row) => row.querySelector('td')?.textContent.trim() === '${eventNo}'
    )?.querySelector('td:last-child button:last-child')`;
    const eventState = `(() => { const button = ${eventButton}; return button && {
      text: button.textContent.trim(), disabled: button.disabled,
      status: Array.from(document.querySelectorAll('tbody tr')).find(
        (row) => row.querySelector('td')?.textContent.trim() === '${eventNo}'
      )?.querySelector('.badge')?.textContent.trim()
    }; })()`;
    await send("Page.enable");
    await send("Runtime.enable");
    for (const failReadback of [false, true]) {
      status = "FAILED";
      failListOnce = false;
      const start = requests.length;
      await send("Page.navigate", { url: `${baseUrl}/payment-events` });
      await waitFor(async () => (await evaluate(eventState))?.text === "重发", "初始可重发事件");
      assert.deepEqual(await evaluate(eventState), { text: "重发", disabled: false, status: "FAILED" });
      await evaluate(`document.querySelector('input[placeholder="请输入操作人"]').value = 'local-operator';
        document.querySelector('input[placeholder="请输入操作人"]').dispatchEvent(new Event('input', { bubbles: true }));`);
      failListOnce = failReadback;
      await evaluate(`${eventButton}.click()`);
      if (failReadback) {
        await waitFor(() => evaluate("document.body.innerText.includes('状态刷新失败，请勿按旧快照重发')"), "读回失败提示");
        assert.deepEqual(await evaluate(eventState), { text: "重发", disabled: true, status: "FAILED" });
        assert.equal(await evaluate("document.querySelector('.toolbar-actions button:last-child')?.disabled"), true);
        await evaluate(`${eventButton}.click()`);
        assert.equal(requests.slice(start).filter((item) => item.method === "POST").length, 1);
        await evaluate(`Array.from(document.querySelectorAll('button')).find(
          (button) => button.textContent.trim() === '刷新').click()`);
      }
      await waitFor(async () => (await evaluate(eventState))?.status === "UNKNOWN"
        && (await evaluate(eventState))?.disabled, "待核查事件禁用重发");
      assert.equal((await evaluate(eventState)).text, "待核查");
      assert.equal(await evaluate("document.body.innerText.includes('重发失败：投递结果待核查 [PAYMENT-400]')"), true);
      assert.equal(requests.slice(start).filter((item) => item.method === "POST").length, 1);
      assert.ok(requests.slice(start).filter((item) => item.method === "GET"
        && item.pathname === "/api/payment-events").length >= (failReadback ? 3 : 2));
      assert.ok(requests.slice(start).filter((item) => item.method === "GET"
        && item.pathname === "/api/payment-events/overview").length >= 2);
      assert.ok(requests.slice(start).every((item) => item.method === "GET"
        || item.method === "POST" && item.pathname === "/api/payment-events/republish"));
    }
    const actionButton = `(Array.from(document.querySelectorAll('button')).find(
      (button) => button.textContent.trim() === '审核通过'))`;
    const refundState = `(() => { const button = ${actionButton}; return button && {
      status: document.querySelector('.detail-card-grid .detail-card:nth-child(2) .badge')?.textContent.trim(),
      disabled: button.disabled
    }; })()`;
    for (const failReadback of [false, true]) {
      refundStatus = "REVIEWING";
      failDetailOnce = false;
      const start = requests.length;
      await send("Page.navigate", { url: `${baseUrl}/refunds/${refundOrderId}` });
      await waitFor(async () => (await evaluate(refundState))?.status === "REVIEWING", "初始可审核退款单");
      assert.deepEqual(await evaluate(refundState), { status: "REVIEWING", disabled: false });
      await evaluate(`document.querySelector('input[placeholder="请输入操作人"]').value = 'local-operator';
        document.querySelector('input[placeholder="请输入操作人"]').dispatchEvent(new Event('input', { bubbles: true }));`);
      failDetailOnce = failReadback;
      await evaluate(`${actionButton}.click()`);
      if (failReadback) {
        await waitFor(() => evaluate("document.body.innerText.includes('退款动作后详情刷新失败，当前状态未经确认')"), "退款详情读回失败提示");
        assert.equal(await evaluate("document.body.innerText.includes('请求失败：渠道受理状态待核查 [PAYMENT-400]')"), true);
        assert.deepEqual(await evaluate(refundState), { status: "REVIEWING", disabled: true });
        assert.equal(await evaluate("document.querySelector('.topbar button')?.disabled"), true);
        await evaluate(`${actionButton}.click()`);
        assert.equal(requests.slice(start).filter((item) => item.method === "POST").length, 1);
        await evaluate(`Array.from(document.querySelectorAll('button')).find(
          (button) => button.textContent.trim() === '刷新').click()`);
      }
      await waitFor(async () => (await evaluate(refundState))?.status === "PROCESSING"
        && (await evaluate(refundState))?.disabled, "退款审批后真实状态");
      if (!failReadback) {
        assert.equal(await evaluate("document.body.innerText.includes('请求失败：渠道受理状态待核查 [PAYMENT-400]')"), true);
      }
      assert.equal(requests.slice(start).filter((item) => item.method === "POST").length, 1);
      assert.ok(requests.slice(start).filter((item) => item.method === "GET"
        && item.pathname === `/api/refunds/${refundOrderId}`).length >= (failReadback ? 3 : 2));
      assert.ok(requests.slice(start).every((item) => item.method === "GET"
        || item.method === "POST" && item.pathname === "/api/refunds/approve"));
    }
    const listButton = `Array.from(document.querySelectorAll('tbody tr')).find(
      (row) => row.querySelector('td')?.textContent.trim() === '${refundOrderId}'
    )?.querySelector('.list-actions button:nth-child(4)')`;
    const listState = `(() => { const button = ${listButton}; return button && {
      status: document.querySelector('tbody tr td:nth-child(9) .badge')?.textContent.trim(),
      disabled: button.disabled
    }; })()`;
    for (const failReadback of [false, true]) {
      refundStatus = "REVIEWING";
      failRefundListOnce = false;
      const start = requests.length;
      await send("Page.navigate", { url: `${baseUrl}/refunds` });
      await waitFor(async () => (await evaluate(listState))?.status === "REVIEWING", "退款列表初始审核行");
      assert.deepEqual(await evaluate(listState), { status: "REVIEWING", disabled: false });
      await evaluate(`document.querySelector('input[placeholder="请输入操作人"]').value = 'local-operator';
        document.querySelector('input[placeholder="请输入操作人"]').dispatchEvent(new Event('input', { bubbles: true }));`);
      failRefundListOnce = failReadback;
      await evaluate(`${listButton}.click()`);
      if (failReadback) {
        await waitFor(() => evaluate("document.body.innerText.includes('退款动作后列表刷新失败，当前状态未经确认')"), "退款列表读回失败提示");
        assert.equal(await evaluate("document.body.innerText.includes('请求失败：渠道受理状态待核查 [PAYMENT-400]')"), true);
        assert.deepEqual(await evaluate(listState), { status: "REVIEWING", disabled: true });
        assert.equal(await evaluate("document.querySelector('.topbar button:last-child')?.disabled"), true);
        assert.equal(await evaluate(`Array.from(document.querySelectorAll('button')).find(
          (button) => button.textContent.trim() === '提交退款申请')?.disabled`), true);
        await evaluate(`${listButton}.click()`);
        assert.equal(requests.slice(start).filter((item) => item.method === "POST").length, 1);
        await evaluate(`Array.from(document.querySelectorAll('button')).find(
          (button) => button.textContent.trim() === '刷新').click()`);
      }
      await waitFor(async () => (await evaluate(listState))?.status === "PROCESSING"
        && (await evaluate(listState))?.disabled, "退款列表最新处理中状态");
      assert.equal(requests.slice(start).filter((item) => item.method === "POST").length, 1);
      assert.ok(requests.slice(start).filter((item) => item.method === "GET"
        && item.pathname === "/api/refunds").length >= (failReadback ? 3 : 2));
      assert.ok(requests.slice(start).every((item) => item.method === "GET"
        || item.method === "POST" && item.pathname === "/api/refunds/approve"));
    }
    refundStatus = "REVIEWING";
    const routeChangeStart = requests.length;
    await send("Page.navigate", { url: `${baseUrl}/refunds` });
    await waitFor(async () => (await evaluate(listState))?.status === "REVIEWING", "路由切换前退款列表");
    await evaluate(`document.querySelector('input[placeholder="请输入操作人"]').value = 'local-operator';
      document.querySelector('input[placeholder="请输入操作人"]').dispatchEvent(new Event('input', { bubbles: true }));`);
    holdRefundApproval = true;
    await evaluate(`${listButton}.click()`);
    await waitFor(() => Boolean(releaseRefundApproval), "待返回的退款审核");
    await evaluate("document.querySelector('#app').__vue_app__.config.globalProperties.$router.push('/refunds?refundStatus=SUCCESS')");
    await waitFor(async () => await evaluate("location.search === '?refundStatus=SUCCESS' && document.body.innerText.includes('当前暂无符合条件的退款单数据')"),
      "路由切换后的成功退款空列表");
    releaseRefundApproval();
    holdRefundApproval = false;
    releaseRefundApproval = null;
    await waitFor(() => requests.slice(routeChangeStart).filter((item) => item.method === "GET"
      && item.pathname === "/api/refunds" && new URLSearchParams(item.search).get("refundStatus") === "SUCCESS").length >= 2,
    "审核完成后当前筛选再次读回");
    assert.equal(await evaluate("document.body.innerText.includes('当前暂无符合条件的退款单数据')"), true,
      "旧退款审核响应不得覆盖新路由快照");
    assert.equal(requests.slice(routeChangeStart).filter((item) => item.method === "GET"
      && item.pathname === "/api/refunds" && new URLSearchParams(item.search).get("refundStatus") === "全部").length, 1,
    "旧审核响应不得在新路由上重新查询原筛选");
    console.log("[PASS] 管理端浏览器：退款动作期间切换路由，旧筛选不回流且新筛选重新读回");
    failRefundListOnce = false;
    const start = requests.length;
    await send("Page.navigate", { url: `${baseUrl}/refunds` });
    await waitFor(async () => (await evaluate(listState))?.status === refundStatus, "申请前退款列表");
    const fill = (selector, value) => evaluate(`(() => {
      const input = document.querySelector(${JSON.stringify(selector)});
      input.value = ${JSON.stringify(value)};
      input.dispatchEvent(new Event('input', { bubbles: true }));
    })()`);
    await fill('input[placeholder="PAY202607190001"]', appliedRefund.paymentOrderId);
    await fill('input[placeholder="请输入金额"]', appliedRefund.refundAmount);
    await fill('input[placeholder="客户取消服务、服务未履约等"]', appliedRefund.refundReason);
    await fill('input[placeholder="请输入操作人"]', "local-operator");
    failRefundListOnce = true;
    const applyButton = `Array.from(document.querySelectorAll('button')).find(
      (button) => button.textContent.trim() === '提交退款申请')`;
    await evaluate(`${applyButton}.click()`);
    await waitFor(() => evaluate("document.body.innerText.includes('退款动作后列表刷新失败，当前状态未经确认')"), "申请后列表读回失败");
    assert.equal(await evaluate("document.body.innerText.includes('发起退款请求失败：申请响应丢失 [PAYMENT-400]')"), true);
    assert.equal(await evaluate(`${applyButton}?.disabled`), true);
    assert.equal(applicationRequests.length, 1);
    assert.equal(await evaluate("document.body.innerText.includes('REF-APPLY-LOCAL-001')"), false);
    await evaluate(`${applyButton}.click()`);
    assert.equal(applicationRequests.length, 1);
    const pendingApplication = await evaluate(`(() => {
      const key = Object.keys(sessionStorage).find((item) => item.startsWith('refund-application-attempt:v1.'));
      return key && { key, value: sessionStorage.getItem(key) };
    })()`);
    assert.ok(pendingApplication);
    assert.equal(pendingApplication.value.includes(appliedRefund.paymentOrderId), false);
    await send("Page.navigate", { url: `${baseUrl}/refunds` });
    await waitFor(() => evaluate("document.querySelector('input[placeholder=\"请输入金额\"]')?.value === ''"), "申请页刷新");
    await waitFor(() => evaluate(`${applyButton} && !${applyButton}.disabled`), "申请页重新加载");
    await fill('input[placeholder="PAY202607190001"]', appliedRefund.paymentOrderId);
    await fill('input[placeholder="请输入金额"]', appliedRefund.refundAmount);
    await fill('input[placeholder="客户取消服务、服务未履约等"]', appliedRefund.refundReason);
    await fill('input[placeholder="请输入操作人"]', "local-operator");
    await evaluate(`sessionStorage.setItem(${JSON.stringify(pendingApplication.key)}, '{broken-json')`);
    await evaluate(`${applyButton}.click()`);
    await waitFor(() => evaluate("document.body.innerText.includes('未决退款申请无法核实')"), "畸形申请键拒绝提交");
    assert.equal(applicationRequests.length, 1);
    await evaluate(`sessionStorage.setItem(${JSON.stringify(pendingApplication.key)}, ${JSON.stringify(pendingApplication.value)})`);
    await waitFor(() => evaluate("document.body.innerText.includes('REF-APPLY-LOCAL-001')"), "申请事实读回");
    assert.equal(await evaluate(`${applyButton}?.disabled`), false);
    assert.equal(await evaluate("document.querySelector('input[placeholder=\"请输入金额\"]')?.value"), "12.00");
    await evaluate(`(() => {
      window.__originalRemoveItem = Storage.prototype.removeItem;
      Object.defineProperty(Storage.prototype, 'removeItem', { configurable: true, value(key) {
        if (key.startsWith('refund-application-attempt:v1.')) return;
        return window.__originalRemoveItem.call(this, key);
      } });
    })()`);
    await evaluate(`${applyButton}.click()`);
    await waitFor(() => evaluate("document.body.innerText.includes('申请已受理，当前状态为 REVIEWING')"
      + " && document.body.innerText.includes('本地申请键未能清理')"), "同键申请受理但清键失败");
    assert.equal(applicationRequests.length, 2);
    await waitFor(() => evaluate(`Array.from(document.querySelectorAll('tbody tr')).some(
      (row) => row.querySelector('td')?.textContent.trim() === '${appliedRefundOrderId}')`), "已受理退款列表读回");
    assert.equal(await evaluate(`Array.from(document.querySelectorAll('tbody tr')).filter(
      (row) => row.querySelector('td')?.textContent.trim() === '${appliedRefundOrderId}').length`), 1);
    assert.equal(await evaluate("document.querySelector('input[placeholder=\"请输入金额\"]')?.value"), "");
    assert.equal(await evaluate(`JSON.parse(sessionStorage.getItem(${JSON.stringify(pendingApplication.key)})).confirmed`), true);
    await evaluate("Object.defineProperty(Storage.prototype, 'removeItem', { configurable: true, value: window.__originalRemoveItem }); delete window.__originalRemoveItem");
    await send("Page.navigate", { url: `${baseUrl}/refunds` });
    await waitFor(() => evaluate(`${applyButton} && !${applyButton}.disabled`), "已确认申请刷新后列表");
    await fill('input[placeholder="PAY202607190001"]', appliedRefund.paymentOrderId);
    await fill('input[placeholder="请输入金额"]', appliedRefund.refundAmount);
    await fill('input[placeholder="客户取消服务、服务未履约等"]', appliedRefund.refundReason);
    await fill('input[placeholder="请输入操作人"]', "local-operator");
    await evaluate(`${applyButton}.click()`);
    await waitFor(() => evaluate("document.body.innerText.includes('未决退款申请无法核实')"), "已确认申请拒绝旧键重放");
    assert.equal(applicationRequests.length, 2);
    await evaluate(`sessionStorage.removeItem(${JSON.stringify(pendingApplication.key)})`);
    assert.equal(requests.slice(start).filter((item) => item.method === "POST").length, 2);
    assert.ok(requests.slice(start).every((item) => item.method === "GET"
      || item.method === "POST" && item.pathname === "/api/refunds/apply"));
    const paymentListButton = `document.querySelector('tbody tr .list-actions button:last-child')`;
    const paymentListState = `(() => { const button = ${paymentListButton}; return button && {
      status: document.querySelector('tbody tr td:nth-child(10) .badge')?.textContent.trim(),
      disabled: button.disabled
    }; })()`;
    const paymentDetailButton = `Array.from(document.querySelectorAll('button')).find(
      (button) => button.textContent.trim() === '关闭支付单')`;
    const paymentDetailState = `(() => { const button = ${paymentDetailButton}; return button && {
      status: Array.from(document.querySelectorAll('.detail-card')).find(
        (card) => card.querySelector('.detail-label')?.textContent.trim() === '当前状态'
      )?.querySelector('.badge')?.textContent.trim(),
      disabled: button.disabled
    }; })()`;
    for (const scenario of ["close", "query"]) {
      paymentScenario = scenario;
      paymentStatus = scenario === "query" ? "WAIT_CALLBACK" : "PREPAY_CREATED";
      querySubmissionUnaccepted = false;
      const paymentRouteStart = requests.length;
      await send("Page.navigate", { url: `${baseUrl}/payments` });
      await waitFor(async () => (await evaluate(paymentListState))?.status === paymentStatus,
        "路由切换前支付列表");
      await evaluate(`document.querySelector('input[placeholder="请输入操作人"]').value = 'local-operator';
        document.querySelector('input[placeholder="请输入操作人"]').dispatchEvent(new Event('input', { bubbles: true }));`);
      holdPaymentAction = true;
      const button = scenario === "query"
        ? "document.querySelector('tbody tr .list-actions button:nth-child(3)')" : paymentListButton;
      await evaluate(`${button}.click()`);
      await waitFor(() => Boolean(releasePaymentAction), "待返回的支付动作");
      await evaluate("document.querySelector('#app').__vue_app__.config.globalProperties.$router.push('/payments?status=SUCCESS')");
      await waitFor(() => evaluate("location.search === '?status=SUCCESS' && document.body.innerText.includes('当前暂无符合条件的支付单数据')"),
        "路由切换后的成功支付空列表");
      releasePaymentAction();
      holdPaymentAction = false;
      releasePaymentAction = null;
      await waitFor(() => requests.slice(paymentRouteStart).filter((item) => item.method === "GET"
        && item.pathname === "/api/payments" && new URLSearchParams(item.search).get("status") === "SUCCESS").length >= 2,
      "支付动作完成后当前筛选再次读回");
      assert.equal(await evaluate("document.body.innerText.includes('当前暂无符合条件的支付单数据')"), true,
        "旧支付动作不得覆盖新路由快照");
      assert.equal(requests.slice(paymentRouteStart).filter((item) => item.method === "GET"
        && item.pathname === "/api/payments" && new URLSearchParams(item.search).get("status") === "全部").length, 1,
      "旧支付动作不得在新路由上重新查询原筛选");
    }
    console.log("[PASS] 管理端浏览器：支付查单/关闭期间切换路由，旧筛选不回流且新筛选重新读回");
    for (const detail of [false, true]) {
      for (const failReadback of [false, true]) {
        paymentScenario = "close";
        paymentStatus = "PREPAY_CREATED";
        failPaymentReadbackOnce = false;
        const paymentStart = requests.length;
        const state = detail ? paymentDetailState : paymentListState;
        const button = detail ? paymentDetailButton : paymentListButton;
        await send("Page.navigate", { url: `${baseUrl}${detail ? `/payments/${paymentOrderId}` : "/payments"}` });
        await waitFor(async () => (await evaluate(state))?.status === "PREPAY_CREATED", "初始可关闭支付单");
        assert.deepEqual(await evaluate(state), { status: "PREPAY_CREATED", disabled: false });
        await evaluate(`document.querySelector('input[placeholder="请输入操作人"]').value = 'local-operator';
          document.querySelector('input[placeholder="请输入操作人"]').dispatchEvent(new Event('input', { bubbles: true }));`);
        failPaymentReadbackOnce = failReadback;
        await evaluate(`${button}.click()`);
        if (failReadback) {
          await waitFor(() => evaluate(`document.body.innerText.includes(${JSON.stringify(detail
            ? "支付动作后详情刷新失败，当前状态未经确认"
            : "支付动作后列表刷新失败，当前状态未经确认")})`), "支付单读回失败提示");
          assert.equal(await evaluate("document.body.innerText.includes('关闭响应丢失 [PAYMENT-400]')"), true);
          assert.deepEqual(await evaluate(state), { status: "PREPAY_CREATED", disabled: true });
          assert.equal(await evaluate("document.querySelector('.topbar button:last-child')?.disabled"), true);
          await evaluate(`${button}.click()`);
          assert.equal(requests.slice(paymentStart).filter((item) => item.method === "POST").length, 1);
          await evaluate(`Array.from(document.querySelectorAll('button')).find(
            (item) => item.textContent.trim() === '刷新').click()`);
        }
        await waitFor(async () => (await evaluate(state))?.status === "CLOSED"
          && (await evaluate(state))?.disabled, "关闭后的支付单最新状态");
        if (!failReadback) {
          assert.equal(await evaluate("document.body.innerText.includes('关闭响应丢失 [PAYMENT-400]')"), true);
        }
        assert.equal(requests.slice(paymentStart).filter((item) => item.method === "POST").length, 1);
        assert.ok(requests.slice(paymentStart).filter((item) => item.method === "GET"
          && item.pathname === (detail ? `/api/payments/${paymentOrderId}` : "/api/payments"))
          .length >= (failReadback ? 3 : 2));
        assert.ok(requests.slice(paymentStart).every((item) => item.method === "GET"
          || item.method === "POST" && item.pathname === "/api/payments/admin/close"));
      }
    }
    const queryListButton = `document.querySelector('tbody tr .list-actions button:nth-child(3)')`;
    const queryListState = paymentListState.replaceAll(paymentListButton, queryListButton);
    const queryDetailButton = `Array.from(document.querySelectorAll('button')).find(
      (button) => button.textContent.trim() === '主动查单')`;
    const queryDetailState = paymentDetailState.replaceAll(paymentDetailButton, queryDetailButton);
    paymentScenario = "query";
    paymentStatus = "WAIT_CALLBACK";
    querySubmissionUnaccepted = true;
    const unacceptedStart = requests.length;
    await send("Page.navigate", { url: `${baseUrl}/payments/${queryPaymentOrderId}` });
    await waitFor(async () => (await evaluate(queryDetailState))?.status === "WAIT_CALLBACK"
      && await evaluate("document.body.innerText.includes('渠道受理未确认')"), "渠道待受理详情");
    assert.deepEqual(await evaluate(queryDetailState), { status: "WAIT_CALLBACK", disabled: true });
    assert.equal(await evaluate("document.body.innerText.includes('勿主动查单或重复提交')"), true);
    assert.equal(await evaluate("document.body.innerText.includes('建议先主动查单')"), false);
    await evaluate(`${queryDetailButton}.click()`);
    assert.equal(requests.slice(unacceptedStart).filter((item) => item.method === "POST").length, 0);
    querySubmissionUnaccepted = false;
    await evaluate(`Array.from(document.querySelectorAll('button')).find(
      (button) => button.textContent.trim() === '刷新').click()`);
    await waitFor(async () => (await evaluate(queryDetailState))?.disabled === false
      && await evaluate("document.body.innerText.includes('可主动查单')"), "渠道受理后详情");
    assert.equal(await evaluate("document.body.innerText.includes('渠道受理未确认')"), false);
    assert.equal(requests.slice(unacceptedStart).filter((item) => item.method === "POST").length, 0);
    const recordQueryButton = `Array.from(document.querySelectorAll('button')).find(
      (button) => button.textContent.trim() === '主动查单')`;
    recordSubmissionUnaccepted = true;
    const recordStart = requests.length;
    await send("Page.navigate", { url: `${baseUrl}/payment-records` });
    await waitFor(() => evaluate(`location.pathname === '/payment-records'
      && document.body.innerText.includes('记录快照')
      && document.body.innerText.includes('渠道受理未确认')`), "支付记录列表待受理标记");
    assert.equal(await evaluate("document.body.innerText.includes('勿主动查单或重复提交')"), true);
    assert.equal(await evaluate("document.body.innerText.includes('最近尝试')"), true);
    await send("Page.navigate", { url: `${baseUrl}/payment-records/${recordPaymentOrderId}` });
    await waitFor(() => evaluate(`location.pathname === '/payment-records/${recordPaymentOrderId}'
      && document.querySelector('h2')?.textContent.trim() === '支付记录详情'
      && document.body.innerText.includes('渠道受理未确认')
      && document.body.innerText.includes('勿主动查单或重复提交')`), "支付记录详情待受理建议");
    assert.equal(await evaluate(`${recordQueryButton}?.disabled`), true);
    assert.equal(await evaluate("document.body.innerText.includes('建议先主动查单')"), false);
    await evaluate(`${recordQueryButton}.click()`);
    assert.equal(requests.slice(recordStart).filter((item) => item.method === "POST").length, 0);
    recordSubmissionUnaccepted = false;
    await evaluate(`Array.from(document.querySelectorAll('button')).find(
      (button) => button.textContent.trim() === '刷新').click()`);
    await waitFor(async () => await evaluate(`${recordQueryButton}?.disabled`) === false
      && await evaluate("document.body.innerText.includes('建议先主动查单')"), "支付记录详情受理后建议");
    assert.equal(await evaluate("document.body.innerText.includes('渠道受理未确认')"), false);
    assert.equal(requests.slice(recordStart).filter((item) => item.method === "POST").length, 0);
    assert.ok(requests.slice(recordStart).some((item) => item.method === "GET"
      && item.pathname === "/api/payment-records"));
    assert.ok(requests.slice(recordStart).filter((item) => item.method === "GET"
      && item.pathname === `/api/payment-records/${recordPaymentOrderId}`).length >= 2);
    console.log("[PASS] 管理端浏览器：支付记录列表/详情待受理与受理后刷新，零查单 POST");
    for (const detail of [false, true]) {
      for (const failReadback of [false, true]) {
        paymentScenario = "query";
        paymentStatus = "WAIT_CALLBACK";
        failPaymentReadbackOnce = false;
        const paymentStart = requests.length;
        const state = detail ? queryDetailState : queryListState;
        const button = detail ? queryDetailButton : queryListButton;
        await send("Page.navigate", { url: `${baseUrl}${detail ? `/payments/${queryPaymentOrderId}` : "/payments"}` });
        await waitFor(async () => (await evaluate(state))?.status === "WAIT_CALLBACK", "初始可查单支付状态");
        assert.deepEqual(await evaluate(state), { status: "WAIT_CALLBACK", disabled: false });
        await evaluate(`document.querySelector('input[placeholder="请输入操作人"]').value = 'local-operator';
          document.querySelector('input[placeholder="请输入操作人"]').dispatchEvent(new Event('input', { bubbles: true }));`);
        failPaymentReadbackOnce = failReadback;
        await evaluate(`${button}.click()`);
        if (failReadback) {
          await waitFor(() => evaluate(`document.body.innerText.includes(${JSON.stringify(detail
            ? "支付动作后详情刷新失败，当前状态未经确认"
            : "支付动作后列表刷新失败，当前状态未经确认")})`), "查单后读回失败提示");
          assert.equal(await evaluate("document.body.innerText.includes('查单响应丢失 [PAYMENT-400]')"), true);
          assert.deepEqual(await evaluate(state), { status: "WAIT_CALLBACK", disabled: true });
          assert.equal(await evaluate("document.querySelector('.topbar button:last-child')?.disabled"), true);
          await evaluate(`${button}.click()`);
          assert.equal(requests.slice(paymentStart).filter((item) => item.method === "POST").length, 1);
          await evaluate(`Array.from(document.querySelectorAll('button')).find(
            (item) => item.textContent.trim() === '刷新').click()`);
        }
        await waitFor(async () => (await evaluate(state))?.status === "FAIL"
          && (await evaluate(state))?.disabled, "查单后失败状态");
        if (!failReadback) {
          assert.equal(await evaluate("document.body.innerText.includes('查单响应丢失 [PAYMENT-400]')"), true);
        }
        assert.equal(requests.slice(paymentStart).filter((item) => item.method === "POST").length, 1);
        assert.ok(requests.slice(paymentStart).filter((item) => item.method === "GET"
          && item.pathname === (detail ? `/api/payments/${queryPaymentOrderId}` : "/api/payments"))
          .length >= (failReadback ? 3 : 2));
        assert.ok(requests.slice(paymentStart).every((item) => item.method === "GET"
          || item.method === "POST" && item.pathname === "/api/payments/admin/query"));
      }
    }
    const taskButton = `Array.from(document.querySelectorAll('.sub-panel')).find(
      (panel) => panel.querySelector('h3')?.textContent.trim() === '支付超时关单'
    )?.querySelector('button')`;
    const taskState = `(() => { const button = ${taskButton}; return button && {
      text: button.textContent.trim(), disabled: button.disabled
    }; })()`;
    for (const failReadback of [false, true]) {
      taskExecuted = false;
      taskOverviewLag = false;
      failTaskRunReadbackOnce = false;
      const start = requests.length;
      await send("Page.navigate", { url: `${baseUrl}/payment-task-center` });
      await waitFor(async () => (await evaluate(taskState))?.text.includes("执行超时关单（1）")
        && !(await evaluate(taskState))?.disabled, "初始可执行任务");
      await evaluate(`document.querySelector('input[placeholder="请输入操作人"]').value = 'local-operator';
        document.querySelector('input[placeholder="请输入操作人"]').dispatchEvent(new Event('input', { bubbles: true }));`);
      taskOverviewLag = failReadback;
      failTaskRunReadbackOnce = failReadback;
      await evaluate(`${taskButton}.click()`);
      if (failReadback) {
        await waitFor(async () => (await evaluate(taskState))?.disabled
          && await evaluate("document.body.innerText.includes('复核总览或执行日志失败')"), "任务日志读回失败门禁");
        assert.match((await evaluate(taskState)).text, /执行超时关单（1）/);
        assert.equal(await evaluate("document.body.innerText.includes('任务响应丢失 [PAYMENT-400]')"), true);
        await evaluate(`${taskButton}.click()`);
        assert.equal(requests.slice(start).filter((item) => item.method === "POST").length, 1);
        taskOverviewLag = false;
        await evaluate("document.querySelector('.topbar button:last-child').click()");
      }
      await waitFor(async () => (await evaluate(taskState))?.text === "暂无可关闭支付"
        && (await evaluate(taskState))?.disabled, "任务最新候选状态");
      assert.equal(requests.slice(start).filter((item) => item.method === "POST").length, 1);
      assert.ok(requests.slice(start).filter((item) => item.method === "GET"
        && item.pathname === "/api/payment-task-center/overview").length >= (failReadback ? 3 : 2));
      assert.ok(requests.slice(start).filter((item) => item.method === "GET"
        && item.pathname === "/api/payment-task-center/task-runs").length >= (failReadback ? 3 : 2));
    }
    const deadLetterButton = `(() => { const row = Array.from(document.querySelectorAll('tbody tr')).find(
      (row) => row.querySelector('td')?.textContent.trim() === '${deadLetterTaskNo}'
    ); return Array.from(row?.querySelectorAll('.list-actions button') || []).at(-1) })()`;
    const deadLetterState = `(() => { const row = Array.from(document.querySelectorAll('tbody tr')).find(
      (item) => item.querySelector('td')?.textContent.trim() === '${deadLetterTaskNo}'
    ); const button = ${deadLetterButton}; return row && {
      status: row.querySelector('td:nth-child(4)')?.textContent.trim(),
      action: button?.textContent.trim(), disabled: button?.disabled
    }; })()`;
    for (const failReadback of [false, true]) {
      deadLetterReplayed = false;
      failDeadLetterReadbackOnce = false;
      const start = requests.length;
      await send("Page.navigate", { url: `${baseUrl}/payment-dead-letter-tasks` });
      await waitFor(async () => (await evaluate(deadLetterState))?.status === "待重放"
        && (await evaluate(deadLetterState))?.action === "执行重放"
        && !(await evaluate(deadLetterState))?.disabled, "初始可重放死信");
      await evaluate(`document.querySelector('input[placeholder="请输入处置人"]').value = 'local-operator';
        document.querySelector('input[placeholder="请输入处置人"]').dispatchEvent(new Event('input', { bubbles: true }));
        document.querySelector('input[placeholder="请填写核对依据或结案原因"]').value = '已核对';
        document.querySelector('input[placeholder="请填写核对依据或结案原因"]').dispatchEvent(new Event('input', { bubbles: true }));`);
      failDeadLetterReadbackOnce = failReadback;
      await evaluate(`${deadLetterButton}.click()`);
      if (failReadback) {
        await waitFor(async () => (await evaluate(deadLetterState))?.disabled
          && await evaluate("document.body.innerText.includes('列表复核失败，旧快照不可操作')"), "死信读回失败门禁");
        assert.equal((await evaluate(deadLetterState)).status, "待重放");
        assert.equal(await evaluate("document.body.innerText.includes('重放响应丢失 [PAYMENT-400]')"), true);
        await evaluate(`${deadLetterButton}.click()`);
        assert.equal(requests.slice(start).filter((item) => item.method === "POST").length, 1);
        await evaluate("document.querySelector('.topbar button:last-child').click()");
      }
      await waitFor(async () => (await evaluate(deadLetterState))?.status === "已重放", "死信最新状态");
      assert.equal((await evaluate(deadLetterState)).action, "详情");
      assert.equal(requests.slice(start).filter((item) => item.method === "POST").length, 1);
      assert.ok(requests.slice(start).some((item) => item.method === "POST"
        && item.pathname === `/api/payment-dead-letter-tasks/${deadLetterTaskNo}/replay`));
      assert.ok(requests.slice(start).filter((item) => item.method === "GET"
        && item.pathname === "/api/payment-dead-letter-tasks").length >= (failReadback ? 3 : 2));
    }
    const issueRow = `Array.from(document.querySelectorAll('tbody tr')).find(
      (row) => row.querySelector('td:nth-child(2)')?.textContent.trim() === '${issueNo}'
    )`;
    const issueActionButton = `Array.from(document.querySelectorAll('button')).find(
      (button) => button.textContent.trim() === '批量处理选中异常'
    )`;
    const issueState = `(() => { const row = ${issueRow}; const checkbox = row?.querySelector('input[type="checkbox"]');
      const button = ${issueActionButton}; return row && {
        status: row.querySelector('td:nth-child(15)')?.textContent.trim(),
        selected: checkbox?.checked, disabled: checkbox?.disabled, actionDisabled: button?.disabled
      }; })()`;
    issueHandled = false;
    issueActionRequests.length = 0;
    const issueRouteStart = requests.length;
    await send("Page.navigate", { url: `${baseUrl}/payment-issues` });
    await waitFor(async () => (await evaluate(issueState))?.status === "待分派", "筛选切换前异常列表");
    await evaluate(`${issueRow}.querySelector('input[type="checkbox"]').click()`);
    await evaluate(`document.querySelector('input[placeholder="请输入操作人"]').value = 'local-operator';
      document.querySelector('input[placeholder="请输入操作人"]').dispatchEvent(new Event('input', { bubbles: true }));`);
    holdIssueAction = true;
    await evaluate(`${issueActionButton}.click()`);
    await waitFor(() => Boolean(releaseIssueAction), "待返回的异常批量动作");
    await evaluate("document.querySelector('#app').__vue_app__.config.globalProperties.$router.push('/payment-issues?severity=P2')");
    await waitFor(() => evaluate("location.search === '?severity=P2' && document.body.innerText.includes('当前暂无符合条件的支付交易异常')"),
      "异常列表切换后的空筛选");
    releaseIssueAction();
    holdIssueAction = false;
    releaseIssueAction = null;
    await waitFor(() => requests.slice(issueRouteStart).filter((item) => item.method === "GET"
      && item.pathname === "/api/payment-issues" && new URLSearchParams(item.search).get("severity") === "P2").length >= 2,
    "异常批量动作完成后当前筛选再次读回");
    assert.equal(await evaluate("document.body.innerText.includes('当前暂无符合条件的支付交易异常')"), true);
    assert.equal(requests.slice(issueRouteStart).filter((item) => item.method === "GET"
      && item.pathname === "/api/payment-issues" && new URLSearchParams(item.search).get("severity") === "全部").length, 1,
    "旧异常筛选不得回流");
    await evaluate(`Object.keys(sessionStorage).filter((key) => key.startsWith('payment-issue-action-attempt:v1'))
      .forEach((key) => sessionStorage.removeItem(key))`);
    console.log("[PASS] 管理端浏览器：异常批量动作期间切换筛选，旧列表不回流");
    issueHandled = false;
    issueActionRequests.length = 0;
    await send("Page.navigate", { url: `${baseUrl}/payment-issues` });
    await waitFor(async () => (await evaluate(issueState))?.status === "待分派", "未发送动作前异常列表");
    await evaluate(`(() => {
      const originalDigest = crypto.subtle.digest.bind(crypto.subtle);
      crypto.subtle.digest = (...args) => new Promise((resolve, reject) => {
        window.__releaseIssueDigest = () => originalDigest(...args).then(resolve, reject);
      });
    })()`);
    await evaluate(`${issueRow}.querySelector('input[type="checkbox"]').click()`);
    await evaluate(`document.querySelector('input[placeholder="请输入操作人"]').value = 'local-operator';
      document.querySelector('input[placeholder="请输入操作人"]').dispatchEvent(new Event('input', { bubbles: true }));`);
    await evaluate(`${issueActionButton}.click()`);
    await waitFor(() => evaluate("typeof window.__releaseIssueDigest === 'function'"), "待完成的批量动作指纹");
    await evaluate("document.querySelector('#app').__vue_app__.config.globalProperties.$router.push('/payment-issues?severity=P2')");
    await waitFor(() => evaluate("location.search === '?severity=P2' && document.body.innerText.includes('当前暂无符合条件的支付交易异常')"),
      "未发送动作时切换异常筛选");
    await evaluate("window.__releaseIssueDigest()");
    await waitFor(() => evaluate("document.querySelector('.topbar button')?.disabled === false"),
      "失效的异常动作指纹结束");
    assert.equal(issueActionRequests.length, 0, "路由已变更时不得发出刚完成指纹的批量 POST");
    await evaluate(`Object.keys(sessionStorage).filter((key) => key.startsWith('payment-issue-action-attempt:v1'))
      .forEach((key) => sessionStorage.removeItem(key))`);
    console.log("[PASS] 管理端浏览器：指纹准备期间切换筛选，不发送过期批量动作");
    for (const failure of ["none", "summary", "list"]) {
      issueHandled = false;
      issueActionRequests.length = 0;
      failIssueListReadbackOnce = false;
      failIssueSummaryReadbackOnce = false;
      const start = requests.length;
      await send("Page.navigate", { url: `${baseUrl}/payment-issues` });
      try {
        await waitFor(async () => (await evaluate(issueState))?.status === "待分派"
          && !(await evaluate(issueState))?.disabled, "初始可选异常");
      } catch (error) {
        throw new Error(`${error.message}：${await evaluate("document.body.innerText.slice(0, 1200)")}`);
      }
      await evaluate(`${issueRow}.querySelector('input[type="checkbox"]').click()`);
      await evaluate(`document.querySelector('input[placeholder="请输入操作人"]').value = 'local-operator';
        document.querySelector('input[placeholder="请输入操作人"]').dispatchEvent(new Event('input', { bubbles: true }));`);
      failIssueSummaryReadbackOnce = failure === "summary";
      failIssueListReadbackOnce = failure === "list";
      await evaluate(`${issueActionButton}.click()`);
      await waitFor(async () => await evaluate("document.body.innerText.includes('批量响应丢失 [PAYMENT-400]')")
        && !(await evaluate(issueState))?.selected, "批量动作错误及选择清空");
      if (failure !== "none") {
        await waitFor(async () => (await evaluate(issueState))?.actionDisabled
          && await evaluate("document.body.innerText.includes('旧快照不可操作')"), "异常双快照失败门禁");
        assert.equal((await evaluate(issueState)).status, failure === "list" ? "待分派" : "已分派");
        await evaluate(`${issueActionButton}.click()`);
        assert.equal(requests.slice(start).filter((item) => item.method === "POST").length, 1);
        await evaluate("document.querySelector('.topbar button:last-child').click()");
      }
      await waitFor(async () => (await evaluate(issueState))?.status === "已分派"
        && !(await evaluate(issueState))?.actionDisabled, "异常最新处理状态");
      assert.equal((await evaluate(issueState)).selected, false);
      await evaluate(`${issueActionButton}.click()`);
      assert.equal(requests.slice(start).filter((item) => item.method === "POST").length, 1);
      assert.ok(requests.slice(start).filter((item) => item.method === "GET"
        && item.pathname === "/api/payment-issues").length >= (failure === "none" ? 2 : 3));
      assert.ok(requests.slice(start).filter((item) => item.method === "GET"
        && item.pathname === "/api/payment-issues/responsibility-summary").length >= (failure === "none" ? 2 : 3));
      if (failure === "none") {
        await send("Page.navigate", { url: `${baseUrl}/payment-issues` });
        await waitFor(async () => (await evaluate(issueState))?.status === "已分派"
          && !(await evaluate(issueState))?.disabled, "刷新后异常最新状态");
        await evaluate(`document.querySelector('input[placeholder="请输入操作人"]').value = 'local-operator';
          document.querySelector('input[placeholder="请输入操作人"]').dispatchEvent(new Event('input', { bubbles: true }));`);
        await evaluate(`${issueRow}.querySelector('input[type="checkbox"]').click()`);
        await evaluate(`${issueActionButton}.click()`);
        await waitFor(async () => await evaluate("document.body.innerText.includes('已记录 1 条异常的批量处理动作')"),
          "刷新后相同批量动作重放成功");
        assert.equal(issueActionRequests.length, 2);
      }
    }
    isolateIssueActions = false;
    issueHandled = false;
    issueActionRequests.length = 0;
    await send("Page.navigate", { url: `${baseUrl}/payment-issues` });
    await waitFor(async () => (await evaluate(issueState))?.status === "待分派"
      && !(await evaluate(issueState))?.disabled, "清键失败前的异常列表");
    await evaluate(`document.querySelector('input[placeholder="请输入操作人"]').value = 'local-operator';
      document.querySelector('input[placeholder="请输入操作人"]').dispatchEvent(new Event('input', { bubbles: true }));`);
    await evaluate(`${issueRow}.querySelector('input[type="checkbox"]').click()`);
    await evaluate(`${issueActionButton}.click()`);
    await waitFor(() => issueActionRequests.length === 1, "清键失败前的未决动作");
    await waitFor(async () => (await evaluate(issueState))?.status === "已分派"
      && !(await evaluate(issueState))?.selected, "未决动作读回");
    await evaluate(`(() => {
      window.__originalIssueRemoveItem = Storage.prototype.removeItem;
      Object.defineProperty(Storage.prototype, 'removeItem', { configurable: true, value(key) {
        if (key.startsWith('payment-issue-action-attempt:v1')) return;
        return window.__originalIssueRemoveItem.call(this, key);
      } });
    })()`);
    await evaluate(`${issueRow}.querySelector('input[type="checkbox"]').click()`);
    await evaluate(`${issueActionButton}.click()`);
    await waitFor(async () => issueActionRequests.length === 2
      && await evaluate("document.body.innerText.includes('批量处理响应已成功，但本机未决键清理失败：')"),
    "已受理批量动作的静默清键失败");
    assert.equal(await evaluate(`Object.entries(sessionStorage).some(([key, value]) => {
      if (!key.startsWith('payment-issue-action-attempt:v1.')) return false;
      try { return JSON.parse(value).confirmed === true; } catch { return false; }
    })`), true);
    assert.equal((await evaluate(issueState)).selected, false);
    await evaluate("Object.defineProperty(Storage.prototype, 'removeItem', { configurable: true, value: window.__originalIssueRemoveItem }); delete window.__originalIssueRemoveItem");
    await send("Page.navigate", { url: `${baseUrl}/payment-issues` });
    await waitFor(async () => (await evaluate(issueState))?.status === "已分派"
      && !(await evaluate(issueState))?.disabled, "清键失败后的新快照");
    await evaluate(`document.querySelector('input[placeholder="请输入操作人"]').value = 'local-operator';
      document.querySelector('input[placeholder="请输入操作人"]').dispatchEvent(new Event('input', { bubbles: true }));`);
    await evaluate(`${issueRow}.querySelector('input[type="checkbox"]').click()`);
    await evaluate(`${issueActionButton}.click()`);
    await waitFor(() => evaluate("document.body.innerText.includes('批量处理未发送：未决批量动作无法核实')"),
      "已确认动作刷新后拒绝旧键重放");
    assert.equal(issueActionRequests.length, 2);
    await evaluate(`Object.keys(sessionStorage).filter((key) => key.startsWith('payment-issue-action-attempt:v1.'))
      .forEach((key) => sessionStorage.removeItem(key))`);
    isolateIssueActions = true;
    issueHandled = false;
    issueActionRequests.length = 0;
    await send("Page.navigate", { url: `${baseUrl}/payment-issues` });
    await waitFor(async () => (await evaluate(issueState))?.status === "待分派"
      && !(await evaluate(issueState))?.disabled, "第一笔未决批量动作");
    const fillIssueOperator = () => evaluate(`document.querySelector('input[placeholder="请输入操作人"]').value = 'local-operator';
      document.querySelector('input[placeholder="请输入操作人"]').dispatchEvent(new Event('input', { bubbles: true }));`);
    const fillIssueRemark = (remark) => evaluate(`document.querySelector('input[placeholder="请写清楚处理结论、排查方向或交接说明"]').value = ${JSON.stringify(remark)};
      document.querySelector('input[placeholder="请写清楚处理结论、排查方向或交接说明"]').dispatchEvent(new Event('input', { bubbles: true }));`);
    await fillIssueOperator();
    await evaluate(`${issueRow}.querySelector('input[type="checkbox"]').click()`);
    await evaluate(`${issueActionButton}.click()`);
    await waitFor(() => issueActionRequests.length === 1, "第一笔未决请求");
    await waitFor(async () => !(await evaluate(issueState))?.selected, "第一笔未决请求读回");
    await fillIssueRemark("第二笔独立待核查动作");
    await evaluate(`${issueRow}.querySelector('input[type="checkbox"]').click()`);
    await evaluate(`${issueActionButton}.click()`);
    await waitFor(() => issueActionRequests.length === 2, "第二笔未决请求");
    assert.notEqual(issueActionRequests[0].idempotencyKey, issueActionRequests[1].idempotencyKey);
    await waitFor(async () => !(await evaluate(issueState))?.selected, "第二笔未决请求读回");
    assert.equal(await evaluate(`Object.keys(sessionStorage).filter((key) =>
      key.startsWith('payment-issue-action-attempt:v1.')).length`), 2);
    await fillIssueRemark("第三笔静默丢写动作");
    await evaluate(`${issueRow}.querySelector('input[type="checkbox"]').click()`);
    await evaluate(`(() => {
      window.__originalSetItem = Storage.prototype.setItem;
      Object.defineProperty(Storage.prototype, 'setItem', { configurable: true, value(key, value) {
        if (key.startsWith('payment-issue-action-attempt:v1.')) return;
        return window.__originalSetItem.call(this, key, value);
      } });
    })()`);
    await evaluate(`${issueActionButton}.click()`);
    await waitFor(() => evaluate("document.body.innerText.includes('批量处理未发送：未决批量动作无法核实')"),
      "静默丢写拒绝异常动作");
    await evaluate("Object.defineProperty(Storage.prototype, 'setItem', { configurable: true, value: window.__originalSetItem }); delete window.__originalSetItem");
    assert.equal(issueActionRequests.length, 2);
    assert.equal(await evaluate(`Object.keys(sessionStorage).filter((key) =>
      key.startsWith('payment-issue-action-attempt:v1.')).length`), 2);
    await send("Page.navigate", { url: `${baseUrl}/payment-issues` });
    await waitFor(async () => (await evaluate(issueState))?.status === "已分派", "第一笔未决请求刷新");
    await fillIssueOperator();
    await evaluate(`${issueRow}.querySelector('input[type="checkbox"]').click()`);
    await evaluate(`${issueActionButton}.click()`);
    await waitFor(() => issueActionRequests.length === 3, "第一笔同键重放");
    assert.deepEqual(issueActionRequests[2], issueActionRequests[0]);
    await send("Page.navigate", { url: `${baseUrl}/payment-issues` });
    await waitFor(async () => (await evaluate(issueState))?.status === "已分派", "第二笔未决请求刷新");
    await fillIssueOperator();
    await fillIssueRemark("第二笔独立待核查动作");
    await evaluate(`${issueRow}.querySelector('input[type="checkbox"]').click()`);
    await evaluate(`${issueActionButton}.click()`);
    await waitFor(() => issueActionRequests.length === 4, "第二笔同键重放");
    assert.deepEqual(issueActionRequests[3], issueActionRequests[1]);
    isolateIssueActions = false;
    issueHandled = false;
    issueActionRequests.length = 0;
    await send("Page.navigate", { url: `${baseUrl}/payment-issues` });
    await waitFor(async () => (await evaluate(issueState))?.status === "待分派"
      && !(await evaluate(issueState))?.disabled, "畸形记录前异常列表");
    assert.equal(await evaluate(`(() => {
      sessionStorage.setItem('payment-issue-action-attempt:v1', '{broken-json');
      return sessionStorage.getItem('payment-issue-action-attempt:v1');
    })()`), "{broken-json");
    await evaluate(`${issueRow}.querySelector('input[type="checkbox"]').click()`);
    await evaluate(`document.querySelector('input[placeholder="请输入操作人"]').value = 'local-operator';
      document.querySelector('input[placeholder="请输入操作人"]').dispatchEvent(new Event('input', { bubbles: true }));`);
    await evaluate(`${issueActionButton}.click()`);
    await waitFor(() => evaluate("document.body.innerText.includes('批量处理未发送：未决批量动作无法核实')"),
      "畸形未决动作拒绝提交");
    assert.equal(issueActionRequests.length, 0);
    assert.equal(await evaluate("sessionStorage.getItem('payment-issue-action-attempt:v1')"), "{broken-json");
    console.log("[PASS] 管理端浏览器：错误读回、双动作重放、已受理清键失败门禁及畸形记录拒绝提交");
  } finally {
    releaseRefundApproval?.();
    releasePaymentAction?.();
    releaseIssueAction?.();
    socket?.close();
    chrome.kill("SIGTERM");
    await Promise.race([new Promise((resolve) => chrome.once("exit", resolve)), delay(3000)]);
    if (chrome.exitCode == null) chrome.kill("SIGKILL");
    await rm(profile, { recursive: true, force: true });
    await new Promise((resolve) => server.close(resolve));
  }
}

await main();
