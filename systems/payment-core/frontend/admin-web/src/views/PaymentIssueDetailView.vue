<script setup>
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentIssueApi } from "../api/client";
import { paymentDetailRoute } from "../router/detailRoutes";
import {
  paymentEventsRoute,
  paymentFlowsRoute,
  paymentIssueAlertsRoute,
  paymentIssuesRoute,
  paymentLogsRoute
} from "../router/listRoutes";

const route = useRoute();
const router = useRouter();
const detail = ref(null);
const isLoading = ref(true);
const hasLoadedData = ref(false);
const loadErrorMessage = ref("");
let latestLoadRequestId = 0;

const currentIssueNo = computed(() => typeof route.params.issueNo === "string"
  ? route.params.issueNo
  : "");
const isSnapshotStale = computed(() => Boolean(hasLoadedData.value && loadErrorMessage.value));
const canUseSnapshot = computed(() => Boolean(
  hasLoadedData.value
  && detail.value?.issueNo === currentIssueNo.value
  && !isLoading.value
  && !isSnapshotStale.value
));
const relatedRoutes = computed(() => ({
  payment: detail.value?.paymentOrderId ? paymentDetailRoute(detail.value.paymentOrderId) : null,
  issueAlerts: detail.value?.issueNo ? paymentIssueAlertsRoute({ issueNo: detail.value.issueNo }) : null,
  logs: detail.value?.paymentOrderId ? paymentLogsRoute({ paymentOrderId: detail.value.paymentOrderId }) : null,
  flows: detail.value?.paymentOrderId ? paymentFlowsRoute({ paymentOrderId: detail.value.paymentOrderId }) : null,
  events: detail.value?.paymentOrderId ? paymentEventsRoute({ paymentOrderId: detail.value.paymentOrderId }) : null
}));
const metricCards = computed(() => detail.value ? [
  { title: "严重等级", value: detail.value.severity || "-", hint: detail.value.slaTimeLeft || "SLA 未记录" },
  { title: "异常类型", value: detail.value.issueType || "-", hint: detail.value.issueNo },
  { title: "处理状态", value: detail.value.handlingStatus || "-", hint: detail.value.assignee || "未分派" },
  { title: "告警状态", value: detail.value.alertStatus || "-", hint: detail.value.alertAckStatus || "回执未记录" }
] : []);

async function loadDetail(issueNo = currentIssueNo.value) {
  const requestId = ++latestLoadRequestId;
  isLoading.value = true;
  loadErrorMessage.value = "";
  try {
    const result = await paymentIssueApi.getDetail(issueNo);
    if (requestId !== latestLoadRequestId || issueNo !== currentIssueNo.value) {
      return;
    }
    if (!result || result.issueNo !== issueNo) {
      throw new Error("支付交易异常详情响应为空或与当前路由不一致");
    }
    detail.value = result;
    hasLoadedData.value = true;
  } catch (error) {
    if (requestId !== latestLoadRequestId || issueNo !== currentIssueNo.value) {
      return;
    }
    loadErrorMessage.value = error.message;
  } finally {
    if (requestId === latestLoadRequestId) {
      isLoading.value = false;
    }
  }
}

function openRelatedRoute(targetRoute) {
  if (!canUseSnapshot.value || !targetRoute) {
    return;
  }
  router.push(targetRoute);
}

function openRecommendedRoute() {
  if (!canUseSnapshot.value || !detail.value?.recommendedRoute) {
    return;
  }
  router.push(detail.value.recommendedRoute);
}

function formatValue(value) {
  return value == null || value === "" ? "-" : value;
}

function formatMessage(value) {
  return value || "暂无异常处置说明";
}

watch(currentIssueNo, (issueNo) => {
  latestLoadRequestId += 1;
  detail.value = null;
  hasLoadedData.value = false;
  loadErrorMessage.value = "";
  loadDetail(issueNo);
}, { immediate: true });
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付交易异常详情</h2>
        <p>按异常编号唯一定位单条支付异常，核对责任组、SLA、告警派发和建议处置路径</p>
      </div>
      <div class="toolbar-actions">
        <button class="button secondary" @click="router.push(paymentIssuesRoute({ paymentOrderId: detail?.paymentOrderId || '' }))">返回异常列表</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.payment" @click="openRelatedRoute(relatedRoutes.payment)">支付单</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.logs" @click="openRelatedRoute(relatedRoutes.logs)">处理日志</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.flows" @click="openRelatedRoute(relatedRoutes.flows)">支付流水</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.events" @click="openRelatedRoute(relatedRoutes.events)">支付事件</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.issueAlerts" @click="openRelatedRoute(relatedRoutes.issueAlerts)">告警明细</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !detail?.recommendedRoute" @click="openRecommendedRoute">建议入口</button>
        <button class="button primary" :disabled="isLoading" @click="loadDetail()">{{ isLoading ? "刷新中..." : "刷新" }}</button>
      </div>
    </div>

    <section class="panel">
      <div v-if="loadErrorMessage" class="error-banner">
        {{ hasLoadedData ? "支付交易异常详情刷新失败，当前展示上次成功快照：" : "支付交易异常详情加载失败：" }}{{ loadErrorMessage }}
      </div>
      <div v-if="isLoading" class="state-box">支付交易异常详情加载中...</div>
      <div v-else-if="!hasLoadedData" class="state-box">当前没有可展示的支付交易异常详情快照</div>
      <template v-else-if="detail">
        <div class="detail-card-grid">
          <div v-for="card in metricCards" :key="card.title" class="detail-card">
            <div class="detail-label">{{ card.title }}</div>
            <div class="detail-value">{{ card.value }}</div>
            <div class="meta">{{ card.hint }}</div>
          </div>
        </div>

        <div class="split-panels detail-panel">
          <section class="panel mini">
            <div class="section-title">
              <h3>异常事实</h3>
              <span class="meta">异常编号唯一定位</span>
            </div>
            <div class="detail-grid detail-grid-wide">
              <div><strong>异常编号：</strong><span class="mono-text">{{ formatValue(detail.issueNo) }}</span></div>
              <div><strong>支付单号：</strong>{{ formatValue(detail.paymentOrderId) }}</div>
              <div><strong>业务订单号：</strong>{{ formatValue(detail.orderNo) }}</div>
              <div><strong>客户：</strong>{{ formatValue(detail.customerName) }}</div>
              <div><strong>支付方式：</strong>{{ formatValue(detail.paymentMethod) }}</div>
              <div><strong>渠道编码：</strong>{{ formatValue(detail.channelCode) }}</div>
              <div><strong>异常类型：</strong><span :class="['badge', detail.issueTypeTag || 'info']">{{ formatValue(detail.issueType) }}</span></div>
              <div><strong>严重等级：</strong><span :class="['badge', detail.severityType || 'warn']">{{ formatValue(detail.severity) }}</span></div>
              <div><strong>支付状态：</strong><span :class="['badge', detail.paymentStatusType || 'info']">{{ formatValue(detail.paymentStatus) }}</span></div>
              <div><strong>异常时间：</strong>{{ formatValue(detail.createdAt) }}</div>
            </div>
          </section>

          <section class="panel mini">
            <div class="section-title">
              <h3>处置状态</h3>
              <span class="meta">责任归口、SLA 和告警摘要</span>
            </div>
            <div class="detail-grid detail-grid-wide">
              <div><strong>处理状态：</strong><span :class="['badge', detail.handlingStatusType || 'warn']">{{ formatValue(detail.handlingStatus) }}</span></div>
              <div><strong>当前处理人：</strong>{{ formatValue(detail.assignee) }}</div>
              <div><strong>责任组：</strong><span :class="['badge', detail.responsibilityGroupType || 'info']">{{ formatValue(detail.responsibilityGroup) }}</span></div>
              <div><strong>SLA 状态：</strong><span :class="['badge', detail.slaStatusType || 'info']">{{ formatValue(detail.slaStatus) }}</span></div>
              <div><strong>升级状态：</strong><span :class="['badge', detail.escalationStatusType || 'info']">{{ formatValue(detail.escalationStatus) }}</span></div>
              <div><strong>最新动作时间：</strong>{{ formatValue(detail.latestActionAt) }}</div>
              <div><strong>告警通知：</strong><span :class="['badge', detail.alertStatusType || 'info']">{{ formatValue(detail.alertStatus) }}</span></div>
              <div><strong>告警回执：</strong><span :class="['badge', detail.alertAckStatusType || 'info']">{{ formatValue(detail.alertAckStatus) }}</span></div>
              <div class="detail-grid-full"><strong>告警通道：</strong>{{ formatValue(detail.alertChannelSummary) }}</div>
              <div class="detail-grid-full"><strong>供应商投递：</strong>{{ formatValue(detail.latestAlertProviderSummary) }} / {{ formatValue(detail.latestAlertReceiptSummary) }}</div>
            </div>
          </section>
        </div>

        <div class="payload-grid detail-panel">
          <div>
            <strong>异常摘要</strong>
            <pre>{{ formatMessage(detail.issueSummary) }}</pre>
          </div>
          <div>
            <strong>根因提示</strong>
            <pre>{{ formatMessage(detail.rootCauseHint) }}</pre>
          </div>
          <div>
            <strong>建议动作</strong>
            <pre>{{ formatMessage(detail.recommendedAction) }}</pre>
          </div>
          <div>
            <strong>处置与升级</strong>
            <pre>{{ formatMessage(detail.responsibilityHint) }}
{{ formatMessage(detail.slaTimeLeft) }}
{{ formatMessage(detail.escalationSuggestion) }}
{{ formatMessage(detail.latestActionSummary) }}</pre>
          </div>
        </div>
      </template>
    </section>
  </div>
</template>

<style scoped>
.mono-text {
  font-family: "SFMono-Regular", Consolas, monospace;
  overflow-wrap: anywhere;
}

.detail-grid-full {
  grid-column: 1 / -1;
}
</style>
