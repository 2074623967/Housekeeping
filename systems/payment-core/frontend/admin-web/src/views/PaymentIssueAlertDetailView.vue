<script setup>
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentIssueApi } from "../api/client";
import { paymentDetailRoute } from "../router/detailRoutes";
import { paymentIssuesRoute, paymentLogsRoute } from "../router/listRoutes";

const route = useRoute();
const router = useRouter();
const detail = ref(null);
const isLoading = ref(true);
const hasLoadedData = ref(false);
const loadErrorMessage = ref("");
let latestLoadRequestId = 0;

const currentAlertNo = computed(() => typeof route.params.alertNo === "string" ? route.params.alertNo : "");
const statusLabel = (state) => ({
  INITIAL_WAIT: "首次等待",
  DUE: "当前可执行",
  BACKING_OFF: "退避中",
  QUERYING: "查询中",
  EXHAUSTED: "自动次数耗尽"
}[state] || state || "-");
const relatedRoutes = computed(() => ({
  payment: detail.value?.paymentOrderId ? paymentDetailRoute(detail.value.paymentOrderId) : null,
  issue: detail.value?.issueNo ? paymentIssuesRoute({ issueNo: detail.value.issueNo }) : null,
  logs: detail.value?.paymentOrderId ? paymentLogsRoute({ paymentOrderId: detail.value.paymentOrderId }) : null
}));
const metricCards = computed(() => detail.value ? [
  { title: "告警状态", value: detail.value.alertStatus || "-", hint: detail.value.alertChannel || "通道未记录" },
  { title: "回执状态", value: detail.value.ackStatus || "-", hint: detail.value.ackAt || "尚未确认" },
  { title: "供应商投递", value: detail.value.providerDeliveryStatus || "-", hint: detail.value.providerName || detail.value.providerCode || "供应商未记录" },
  { title: "回查次数", value: detail.value.receiptQueryCount ?? 0, hint: statusLabel(detail.value.receiptQueryState) }
] : []);

function formatValue(value) { return value == null || value === "" ? "-" : value; }
function openRoute(target) { if (target && !isLoading.value && hasLoadedData.value) router.push(target); }

async function loadDetail(alertNo = currentAlertNo.value) {
  const requestId = ++latestLoadRequestId;
  isLoading.value = true;
  loadErrorMessage.value = "";
  try {
    const result = await paymentIssueApi.getAlertDetail(alertNo);
    if (requestId !== latestLoadRequestId || alertNo !== currentAlertNo.value) return;
    if (!result || result.alertNo !== alertNo) throw new Error("告警详情响应为空或与当前路由不一致");
    detail.value = result;
    hasLoadedData.value = true;
  } catch (error) {
    if (requestId !== latestLoadRequestId || alertNo !== currentAlertNo.value) return;
    loadErrorMessage.value = error.message;
  } finally {
    if (requestId === latestLoadRequestId) isLoading.value = false;
  }
}

watch(currentAlertNo, (alertNo) => {
  latestLoadRequestId += 1;
  detail.value = null;
  hasLoadedData.value = false;
  loadErrorMessage.value = "";
  loadDetail(alertNo);
}, { immediate: true });
</script>

<template>
  <div>
    <div class="topbar">
      <div><h2>支付异常告警详情</h2><p>按告警号精确核对异常触达、供应商投递、回执回查与人工确认事实</p></div>
      <div class="toolbar-actions">
        <button class="button secondary" @click="router.push('/payment-issue-alerts')">返回告警明细</button>
        <button class="button secondary" :disabled="!hasLoadedData || !relatedRoutes.payment" @click="openRoute(relatedRoutes.payment)">支付单</button>
        <button class="button secondary" :disabled="!hasLoadedData || !relatedRoutes.issue" @click="openRoute(relatedRoutes.issue)">异常中心</button>
        <button class="button secondary" :disabled="!hasLoadedData || !relatedRoutes.logs" @click="openRoute(relatedRoutes.logs)">处理日志</button>
        <button class="button primary" :disabled="isLoading" @click="loadDetail()">{{ isLoading ? "刷新中..." : "刷新" }}</button>
      </div>
    </div>
    <section class="panel">
      <div v-if="loadErrorMessage" class="error-banner">{{ hasLoadedData ? "告警详情刷新失败，当前展示上次成功快照：" : "告警详情加载失败：" }}{{ loadErrorMessage }}</div>
      <div v-if="isLoading" class="state-box">告警详情加载中...</div>
      <div v-else-if="!hasLoadedData" class="state-box">当前没有可展示的告警详情快照</div>
      <template v-else-if="detail">
        <div class="detail-card-grid"><div v-for="card in metricCards" :key="card.title" class="detail-card"><div class="detail-label">{{ card.title }}</div><div class="detail-value">{{ card.value }}</div><div class="meta">{{ card.hint }}</div></div></div>
        <div class="split-panels detail-panel">
          <section class="panel mini"><div class="section-title"><h3>告警事实</h3><span class="meta">告警号唯一定位</span></div><div class="detail-grid detail-grid-wide">
            <div><strong>告警号：</strong><span class="mono-text">{{ formatValue(detail.alertNo) }}</span></div><div><strong>来源告警号：</strong>{{ formatValue(detail.sourceAlertNo) }}</div>
            <div><strong>异常编号：</strong>{{ formatValue(detail.issueNo) }}</div><div><strong>支付单号：</strong>{{ formatValue(detail.paymentOrderId) }}</div>
            <div><strong>告警类型：</strong>{{ formatValue(detail.issueType) }}</div><div><strong>严重等级：</strong>{{ formatValue(detail.severity) }}</div>
            <div><strong>责任组：</strong>{{ formatValue(detail.responsibilityGroup) }}</div><div><strong>告警通道：</strong>{{ formatValue(detail.alertChannel) }}</div>
            <div><strong>接收人：</strong>{{ formatValue(detail.receiver) }}</div><div><strong>触发来源：</strong>{{ formatValue(detail.triggeredBy) }}</div>
            <div><strong>创建时间：</strong>{{ formatValue(detail.createdAt) }}</div><div><strong>确认人/时间：</strong>{{ formatValue(detail.ackOperator) }} / {{ formatValue(detail.ackAt) }}</div>
          </div></section>
          <section class="panel mini"><div class="section-title"><h3>供应商与回执</h3><span class="meta">只读事实</span></div><div class="detail-grid detail-grid-wide">
            <div><strong>供应商：</strong>{{ formatValue(detail.providerName || detail.providerCode) }}</div><div><strong>端点：</strong>{{ formatValue(detail.endpointAlias) }}</div>
            <div><strong>模板：</strong>{{ formatValue(detail.templateCode) }}</div><div><strong>投递状态：</strong>{{ formatValue(detail.providerDeliveryStatus) }}</div>
            <div><strong>供应商回执号：</strong>{{ formatValue(detail.providerReceiptNo) }}</div><div><strong>回查队列：</strong>{{ statusLabel(detail.receiptQueryState) }}</div>
            <div><strong>最近回查：</strong>{{ formatValue(detail.lastReceiptQueryAt) }}</div><div><strong>下次回查：</strong>{{ formatValue(detail.nextReceiptQueryAt) }}</div>
            <div><strong>最近回查结果：</strong>{{ formatValue(detail.receiptQueryResult) }}</div><div><strong>回查次数：</strong>{{ formatValue(detail.receiptQueryCount) }}</div>
          </div></section>
        </div>
        <div class="ops-card"><div class="ops-title">告警内容</div><div class="timeline-item">{{ detail.alertContent || "-" }}</div><div v-if="detail.renderedContentSnapshot" class="timeline-item">{{ detail.renderedContentSnapshot }}</div><div class="timeline-item">{{ detail.providerDeliveryMessage || "-" }}</div></div>
        <div class="payload-grid snapshot-grid"><div><strong>供应商回执脱敏快照</strong><pre>{{ detail.providerReceiptSnapshot || "-" }}</pre></div></div>
      </template>
    </section>
  </div>
</template>

<style scoped>
.snapshot-grid { grid-template-columns: 1fr; }
.snapshot-grid pre { max-height: 320px; }
</style>
