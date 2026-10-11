<script setup>
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentApi } from "../api/client";
import { resolvePaymentBusinessEntryPath } from "../router/paymentRoutes";
import {
  formatRefundProgressTime,
  REFUND_PROGRESS_MAX_POLL_ATTEMPTS,
  isUnknownRefundSubmission,
  matchesRefundProgressDetail,
  shouldRetryRefundProgress
} from "../constants/refundProgress";

const props = defineProps({
  terminalVariant: {
    type: String,
    default: "app"
  }
});

const TERMINAL_COPY = {
  app: {
    heroLabel: "App 退款进度",
    heroHint: "查看退款申请、渠道受理和最终退款结果，不在用户端执行审核或重试。"
  },
  h5: {
    heroLabel: "H5 退款进度",
    heroHint: "适用于短信、活动页和服务预约页中的退款状态查询。"
  },
  pc: {
    heroLabel: "PC 退款进度",
    heroHint: "适用于桌面端用户或客服协助核对退款状态与渠道流水。"
  }
};

const STATUS_META = {
  REVIEWING: {
    title: "等待退款审核",
    hint: "退款申请已提交，当前等待业务侧完成审核。",
    type: "warn"
  },
  PROCESSING: {
    title: "退款处理中",
    hint: "退款正在处理，渠道受理与最终结果仍需核对，页面会继续刷新最新状态。",
    type: "info"
  },
  SUCCESS: {
    title: "退款成功",
    hint: "退款已获得成功终态，请以原支付账户到账时间为准。",
    type: "success"
  },
  FAIL: {
    title: "退款未成功",
    hint: "本次退款未获得成功终态，请联系服务方处理后续补偿或重试。",
    type: "danger"
  }
};

const route = useRoute();
const router = useRouter();
const refundDetail = ref(null);
const loading = ref(true);
const refreshing = ref(false);
const feedbackMessage = ref("");
const staleDetail = ref(false);
let latestRequestId = 0;
let pollTimer = null;
const pollAttempts = ref(0);

const terminalCopy = computed(() => TERMINAL_COPY[props.terminalVariant] || TERMINAL_COPY.app);
const currentRefundOrderId = computed(() => typeof route.params.refundOrderId === "string"
  ? route.params.refundOrderId.trim()
  : "");
const expectedPayScene = computed(() => typeof route.query.payScene === "string"
  ? route.query.payScene.trim()
  : null);
const unknownSubmission = computed(() => isUnknownRefundSubmission(refundDetail.value));
const statusMeta = computed(() => unknownSubmission.value ? {
  title: "退款渠道结果待核查",
  hint: "退款请求已发出，但渠道是否受理尚未确认；请勿重复申请，等待服务方查单或回调。",
  type: "warn"
} : STATUS_META[refundDetail.value?.status] || {
  title: refundDetail.value?.status || "退款状态",
  hint: "当前退款状态已记录，请以服务方后续通知为准。",
  type: refundDetail.value?.statusType || "info"
});
const needsVerification = computed(() => staleDetail.value
  || (!loading.value && !refundDetail.value && Boolean(feedbackMessage.value)));
const displayStatusMeta = computed(() => needsVerification.value ? {
  title: "退款进度待核查",
  hint: "暂无法确认最新退款状态，请勿重复申请退款，保留退款单号并联系服务方核查。",
  type: "warn"
} : statusMeta.value);
const statusClass = computed(() => `status-${needsVerification.value || unknownSubmission.value
  ? "warn" : refundDetail.value?.statusType || statusMeta.value.type}`);
const isTerminal = computed(() => ["SUCCESS", "FAIL"].includes(refundDetail.value?.status));
const pollingPaused = computed(() => Boolean(refundDetail.value
  && !isTerminal.value
  && pollAttempts.value >= REFUND_PROGRESS_MAX_POLL_ATTEMPTS));
const paymentEntryPath = computed(() => resolvePaymentBusinessEntryPath(refundDetail.value?.payScene));
const timelineItems = computed(() => {
  const logs = (refundDetail.value?.operationLogs || []).map((item) => ({
    key: `log-${item.logNo}`,
    time: formatRefundProgressTime(item.createdAt),
    title: item.actionName || item.actionCode || "退款状态更新",
    detail: `${item.fromStatus || "-"} → ${item.toStatus || "-"} / ${item.operationRemark || "无备注"}`
  }));
  const attempts = (refundDetail.value?.attempts || []).map((item) => ({
    key: `attempt-${item.refundAttemptNo}`,
    time: formatRefundProgressTime(item.completedAt || item.submittedAt || item.createdAt),
    title: `渠道尝试 ${item.attemptStatus || "-"}`,
    detail: `${item.channelCode || "-"} / 渠道退款单号 ${item.channelRefundNo || "-"}`
  }));
  return [...logs, ...attempts];
});

function stopPolling() {
  if (pollTimer) {
    clearTimeout(pollTimer);
    pollTimer = null;
  }
}

function schedulePolling() {
  stopPolling();
  if (isTerminal.value || !refundDetail.value || pollingPaused.value) {
    return;
  }
  pollTimer = setTimeout(() => {
    pollTimer = null;
    refreshDetail(true);
  }, 8000);
}

function hasMatchingDetail(refundOrderId, detail) {
  return Boolean(detail
    && typeof detail.refundOrderId === "string"
    && detail.refundOrderId.trim() === refundOrderId.trim());
}

function hasMatchingSnapshot(detail) {
  if (!refundDetail.value) {
    return true;
  }
  return matchesRefundProgressDetail({
    refundOrderId: refundDetail.value.refundOrderId,
    paymentOrderId: refundDetail.value.paymentOrderId,
    businessChainId: refundDetail.value.businessChainId,
    orderNo: refundDetail.value.orderNo,
    customerName: refundDetail.value.customerName,
    paymentMethod: refundDetail.value.paymentMethod,
    payScene: refundDetail.value.payScene,
    refundAmount: refundDetail.value.refundAmount,
    paidAmount: refundDetail.value.paidAmount,
    appliedAt: refundDetail.value.appliedAt,
    channelSubmittedAt: refundDetail.value.channelSubmittedAt,
    successAt: refundDetail.value.successAt,
    terminalAt: refundDetail.value.terminalAt,
    refundMethod: refundDetail.value.refundMethod,
    refundReason: refundDetail.value.refundReason,
    channelCode: refundDetail.value.channelCode,
    channelRefundNo: refundDetail.value.channelRefundNo
  }, detail);
}

async function loadDetail(refundOrderId = currentRefundOrderId.value) {
  const requestId = ++latestRequestId;
  stopPolling();
  pollAttempts.value = 0;
  refundDetail.value = null;
  staleDetail.value = false;
  feedbackMessage.value = "";
  refreshing.value = false;
  loading.value = true;
  if (!refundOrderId) {
    feedbackMessage.value = "缺少退款单号，无法查询退款进度。";
    loading.value = false;
    return;
  }
  try {
    const detail = await paymentApi.getRefundDetail(refundOrderId, expectedPayScene.value || null);
    if (requestId !== latestRequestId || refundOrderId !== currentRefundOrderId.value) {
      return;
    }
    if (!hasMatchingDetail(refundOrderId, detail) || !hasMatchingSnapshot(detail)) {
      throw new Error("退款详情为空或与当前退款单不一致");
    }
    refundDetail.value = detail;
    staleDetail.value = false;
    schedulePolling();
  } catch (error) {
    if (requestId === latestRequestId && refundOrderId === currentRefundOrderId.value) {
      feedbackMessage.value = error.message;
    }
  } finally {
    if (requestId === latestRequestId) {
      loading.value = false;
    }
  }
}

async function refreshDetail(isAutomatic = false) {
  if (!currentRefundOrderId.value || (!isAutomatic && (loading.value || refreshing.value))) {
    return;
  }
  if (isAutomatic && refreshing.value) {
    schedulePolling();
    return;
  }
  if (!isAutomatic) {
    stopPolling();
  }
  const requestId = ++latestRequestId;
  const completedPollAttempts = isAutomatic ? pollAttempts.value + 1 : 0;
  if (!isAutomatic) {
    refreshing.value = true;
  }
  feedbackMessage.value = "";
  try {
    const detail = await paymentApi.getRefundDetail(
      currentRefundOrderId.value,
      expectedPayScene.value || null
    );
    if (requestId !== latestRequestId) {
      return;
    }
    if (!hasMatchingDetail(currentRefundOrderId.value, detail) || !hasMatchingSnapshot(detail)) {
      throw new Error("退款刷新响应为空或与当前退款单不一致");
    }
    refundDetail.value = detail;
    staleDetail.value = false;
    pollAttempts.value = completedPollAttempts;
    feedbackMessage.value = isAutomatic ? "退款进度已自动刷新。" : "退款进度已刷新。";
    schedulePolling();
  } catch (error) {
    if (requestId === latestRequestId && refundDetail.value) {
      staleDetail.value = true;
    }
    if (isAutomatic && requestId === latestRequestId) {
      pollAttempts.value = completedPollAttempts;
    }
    if (shouldRetryRefundProgress({
      automatic: isAutomatic,
      requestMatches: requestId === latestRequestId,
      isTerminal: isTerminal.value,
      attempts: pollAttempts.value
    })) {
      schedulePolling();
    } else if (requestId === latestRequestId && !isAutomatic) {
      feedbackMessage.value = error.message;
      schedulePolling();
    }
  } finally {
    if (!isAutomatic && requestId === latestRequestId) {
      refreshing.value = false;
    }
  }
}

function backToPaymentEntry() {
  if (paymentEntryPath.value && !staleDetail.value) {
    router.push(paymentEntryPath.value);
  }
}

watch([currentRefundOrderId, expectedPayScene], ([refundOrderId]) => {
  loadDetail(refundOrderId);
}, { immediate: true });

onBeforeUnmount(stopPolling);
</script>

<template>
  <div class="terminal-page" :class="`terminal-${terminalVariant}`">
    <section class="terminal-hero">
      <div class="hero-copy">
        <div class="hero-label">{{ terminalCopy.heroLabel }}</div>
        <h1>{{ displayStatusMeta.title }}</h1>
        <p>{{ displayStatusMeta.hint }} {{ terminalCopy.heroHint }}</p>
      </div>
      <div class="hero-amount-card">
        <div class="mini-label">退款状态</div>
        <div class="hero-amount status-text">{{ needsVerification ? "待核查" : refundDetail?.status || "加载中" }}</div>
        <div class="hero-status-row">
          <span class="status-pill" :class="statusClass">{{ needsVerification ? "需核查" : refundDetail?.statusType || statusMeta.type }}</span>
          <span class="countdown-pill">{{ currentRefundOrderId || "-" }}</span>
        </div>
      </div>
    </section>

    <div v-if="loading" class="terminal-card terminal-state-card">退款进度加载中...</div>
    <div v-else-if="feedbackMessage && !refundDetail" class="terminal-card terminal-state-card">
      <p>{{ feedbackMessage }}</p>
      <button v-if="currentRefundOrderId" class="action-button secondary" @click="loadDetail()">重试加载退款进度</button>
    </div>

    <div v-else class="terminal-grid">
      <section class="terminal-card">
        <div class="section-heading">
          <div>
            <h3>退款摘要</h3>
            <p>这里只展示退款进度和可核对事实，审核、重试等运营动作仍由服务方后台处理。</p>
          </div>
          <span class="support-tag">只读查询</span>
        </div>

        <div class="summary-grid">
          <div class="summary-item"><span>退款单号</span><strong>{{ refundDetail?.refundOrderId || currentRefundOrderId }}</strong></div>
          <div class="summary-item"><span>退款金额</span><strong>{{ refundDetail?.refundAmount || "-" }}</strong></div>
          <div class="summary-item"><span>原支付金额</span><strong>{{ refundDetail?.paidAmount || "-" }}</strong></div>
          <div class="summary-item"><span>原支付单号</span><strong>{{ refundDetail?.paymentOrderId || "-" }}</strong></div>
          <div class="summary-item"><span>支付场景</span><strong>{{ refundDetail?.payScene || "-" }}</strong></div>
          <div class="summary-item"><span>原订单号</span><strong>{{ refundDetail?.orderNo || "-" }}</strong></div>
          <div class="summary-item"><span>客户名称</span><strong>{{ refundDetail?.customerName || "-" }}</strong></div>
          <div class="summary-item"><span>支付方式</span><strong>{{ refundDetail?.paymentMethod || "-" }}</strong></div>
          <div class="summary-item"><span>退款方式</span><strong>{{ refundDetail?.refundMethod || "-" }}</strong></div>
          <div class="summary-item"><span>退款原因</span><strong>{{ refundDetail?.refundReason || "-" }}</strong></div>
          <div class="summary-item"><span>申请时间</span><strong>{{ formatRefundProgressTime(refundDetail?.appliedAt) }}</strong></div>
          <div class="summary-item"><span>成功时间</span><strong>{{ formatRefundProgressTime(refundDetail?.successAt) }}</strong></div>
          <div class="summary-item"><span>终态时间</span><strong>{{ formatRefundProgressTime(refundDetail?.terminalAt) }}</strong></div>
          <div class="summary-item"><span>退款渠道</span><strong>{{ refundDetail?.channelCode || "-" }}</strong></div>
          <div class="summary-item"><span>渠道退款单号</span><strong>{{ refundDetail?.channelRefundNo || "-" }}</strong></div>
          <div class="summary-item"><span>渠道受理时间</span><strong>{{ formatRefundProgressTime(refundDetail?.channelSubmittedAt) }}</strong></div>
        </div>

        <div class="ops-card">
          <div class="ops-title">下一步</div>
          <p class="ops-description">
            {{ staleDetail ? "当前仅能核对上次读取的退款事实；请勿重复申请退款，等待自动刷新或手动刷新。" : isTerminal ? (refundDetail?.status === "SUCCESS" ? "退款已进入成功终态，请关注原支付账户到账时间。" : "退款未成功，请联系服务方确认补偿或重试安排。") : "退款仍在处理，页面会继续尝试刷新；请不要重复提交退款申请。" }}
          </p>
        </div>

        <div class="terminal-actions">
          <button class="action-button primary" :disabled="refreshing" @click="refreshDetail()">
            {{ refreshing ? "刷新中..." : "刷新退款进度" }}
          </button>
          <button v-if="paymentEntryPath" class="action-button secondary" :disabled="refreshing || staleDetail" @click="backToPaymentEntry">返回支付入口</button>
        </div>
        <p v-if="!paymentEntryPath" class="feedback-text">当前场景暂无对应的统一支付入口，请从原业务订单页返回。</p>
        <p v-if="feedbackMessage" class="feedback-text">{{ feedbackMessage }}</p>
        <p v-if="staleDetail" class="feedback-text">最新退款进度未能确认，当前详情仅供核对上次读取的事实；请勿重复申请退款，等待自动刷新或手动刷新。</p>
        <p v-if="pollingPaused" class="feedback-text">自动刷新已暂停，当前展示上次成功查询的状态；请手动刷新退款进度。</p>
      </section>

      <section class="terminal-card">
        <div class="section-heading">
          <div>
            <h3>退款轨迹</h3>
            <p>按退款操作和渠道尝试展示当前可核对的处理事实。</p>
          </div>
        </div>
        <div class="timeline-cluster">
          <div v-for="item in timelineItems" :key="item.key" class="timeline-block">
            <h4>{{ item.title }}</h4>
            <div class="timeline-entry">{{ item.time }} / {{ item.detail }}</div>
          </div>
          <div v-if="!timelineItems.length" class="timeline-empty">当前暂无退款轨迹</div>
        </div>
      </section>
    </div>
  </div>
</template>
