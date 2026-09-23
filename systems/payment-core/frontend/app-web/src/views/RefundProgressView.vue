<script setup>
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentApi } from "../api/client";

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
    hint: "退款已进入渠道处理，页面会继续刷新最新状态。",
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
let latestRequestId = 0;
let pollTimer = null;
let pollAttempts = 0;

const terminalCopy = computed(() => TERMINAL_COPY[props.terminalVariant] || TERMINAL_COPY.app);
const currentRefundOrderId = computed(() => typeof route.params.refundOrderId === "string"
  ? route.params.refundOrderId.trim()
  : "");
const statusMeta = computed(() => STATUS_META[refundDetail.value?.status] || {
  title: refundDetail.value?.status || "退款状态",
  hint: "当前退款状态已记录，请以服务方后续通知为准。",
  type: refundDetail.value?.statusType || "info"
});
const statusClass = computed(() => `status-${refundDetail.value?.statusType || statusMeta.value.type}`);
const isTerminal = computed(() => ["SUCCESS", "FAIL"].includes(refundDetail.value?.status));
const timelineItems = computed(() => {
  const logs = (refundDetail.value?.operationLogs || []).map((item) => ({
    key: `log-${item.logNo}`,
    time: item.createdAt || "-",
    title: item.actionName || item.actionCode || "退款状态更新",
    detail: `${item.fromStatus || "-"} → ${item.toStatus || "-"} / ${item.operationRemark || "无备注"}`
  }));
  const attempts = (refundDetail.value?.attempts || []).map((item) => ({
    key: `attempt-${item.refundAttemptNo}`,
    time: item.completedAt || item.submittedAt || item.createdAt || "-",
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
  if (isTerminal.value || !refundDetail.value || pollAttempts >= 5) {
    return;
  }
  pollTimer = setTimeout(() => {
    pollTimer = null;
    refreshDetail(true);
  }, 8000);
}

function hasMatchingDetail(refundOrderId, detail) {
  return Boolean(detail && detail.refundOrderId === refundOrderId);
}

async function loadDetail(refundOrderId = currentRefundOrderId.value) {
  const requestId = ++latestRequestId;
  stopPolling();
  pollAttempts = 0;
  refundDetail.value = null;
  feedbackMessage.value = "";
  loading.value = true;
  if (!refundOrderId) {
    feedbackMessage.value = "缺少退款单号，无法查询退款进度。";
    loading.value = false;
    return;
  }
  try {
    const detail = await paymentApi.getRefundDetail(refundOrderId);
    if (requestId !== latestRequestId || refundOrderId !== currentRefundOrderId.value) {
      return;
    }
    if (!hasMatchingDetail(refundOrderId, detail)) {
      throw new Error("退款详情为空或与当前退款单不一致");
    }
    refundDetail.value = detail;
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
  if (isAutomatic) {
    pollAttempts += 1;
  } else {
    refreshing.value = true;
  }
  feedbackMessage.value = "";
  try {
    const detail = await paymentApi.getRefundDetail(currentRefundOrderId.value);
    if (requestId !== latestRequestId) {
      return;
    }
    if (!hasMatchingDetail(currentRefundOrderId.value, detail)) {
      throw new Error("退款刷新响应为空或与当前退款单不一致");
    }
    refundDetail.value = detail;
    feedbackMessage.value = isAutomatic ? "退款进度已自动刷新。" : "退款进度已刷新。";
    schedulePolling();
  } catch (error) {
    if (requestId === latestRequestId && !isAutomatic) {
      feedbackMessage.value = error.message;
    }
  } finally {
    if (!isAutomatic && requestId === latestRequestId) {
      refreshing.value = false;
    }
  }
}

function backToPaymentEntry() {
  router.push("/balance-pay");
}

watch(currentRefundOrderId, (refundOrderId) => {
  loadDetail(refundOrderId);
}, { immediate: true });

onBeforeUnmount(stopPolling);
</script>

<template>
  <div class="terminal-page" :class="`terminal-${terminalVariant}`">
    <section class="terminal-hero">
      <div class="hero-copy">
        <div class="hero-label">{{ terminalCopy.heroLabel }}</div>
        <h1>{{ statusMeta.title }}</h1>
        <p>{{ statusMeta.hint }} {{ terminalCopy.heroHint }}</p>
      </div>
      <div class="hero-amount-card">
        <div class="mini-label">退款状态</div>
        <div class="hero-amount status-text">{{ refundDetail?.status || "加载中" }}</div>
        <div class="hero-status-row">
          <span class="status-pill" :class="statusClass">{{ refundDetail?.statusType || statusMeta.type }}</span>
          <span class="countdown-pill">{{ currentRefundOrderId || "-" }}</span>
        </div>
      </div>
    </section>

    <div v-if="loading" class="terminal-card terminal-state-card">退款进度加载中...</div>
    <div v-else-if="feedbackMessage && !refundDetail" class="terminal-card terminal-state-card">{{ feedbackMessage }}</div>

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
          <div class="summary-item"><span>原支付单号</span><strong>{{ refundDetail?.paymentOrderId || "-" }}</strong></div>
          <div class="summary-item"><span>原订单号</span><strong>{{ refundDetail?.orderNo || "-" }}</strong></div>
          <div class="summary-item"><span>退款方式</span><strong>{{ refundDetail?.refundMethod || "-" }}</strong></div>
          <div class="summary-item"><span>退款原因</span><strong>{{ refundDetail?.refundReason || "-" }}</strong></div>
          <div class="summary-item"><span>申请时间</span><strong>{{ refundDetail?.appliedAt || "-" }}</strong></div>
          <div class="summary-item"><span>成功时间</span><strong>{{ refundDetail?.successAt || "-" }}</strong></div>
          <div class="summary-item"><span>退款渠道</span><strong>{{ refundDetail?.channelCode || "-" }}</strong></div>
          <div class="summary-item"><span>渠道退款单号</span><strong>{{ refundDetail?.channelRefundNo || "-" }}</strong></div>
        </div>

        <div class="ops-card">
          <div class="ops-title">下一步</div>
          <p class="ops-description">
            {{ isTerminal ? (refundDetail?.status === "SUCCESS" ? "退款已进入成功终态，请关注原支付账户到账时间。" : "退款未成功，请联系服务方确认补偿或重试安排。") : "退款仍在处理，页面会继续尝试刷新；请不要重复提交退款申请。" }}
          </p>
        </div>

        <div class="terminal-actions">
          <button class="action-button primary" :disabled="refreshing" @click="refreshDetail()">
            {{ refreshing ? "刷新中..." : "刷新退款进度" }}
          </button>
          <button class="action-button secondary" :disabled="refreshing" @click="backToPaymentEntry">返回支付入口</button>
        </div>
        <p v-if="feedbackMessage" class="feedback-text">{{ feedbackMessage }}</p>
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
