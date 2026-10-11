<script setup>
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { matchesPaymentEventDetailSnapshot, paymentEventApi } from "../api/client";
import {
  orderDetailRoute,
  paymentDetailRoute,
  paymentFlowDetailRoute
} from "../router/detailRoutes";
import { paymentEventsRoute, paymentFlowsRoute, paymentLogsRoute } from "../router/listRoutes";
import {
  paymentEventRecommendedAction,
  paymentSuccessHttpStageMeta
} from "../utils/paymentEventStage";

const route = useRoute();
const router = useRouter();
const detail = ref(null);
const isLoading = ref(true);
const hasLoadedData = ref(false);
const loadErrorMessage = ref("");
let latestLoadRequestId = 0;

const currentEventNo = computed(() => typeof route.params.eventNo === "string"
  ? route.params.eventNo
  : "");
const isSnapshotStale = computed(() => Boolean(hasLoadedData.value && loadErrorMessage.value));
const canUseSnapshot = computed(() => Boolean(
  hasLoadedData.value
  && detail.value?.eventNo === currentEventNo.value
  && !isLoading.value
  && !isSnapshotStale.value
));
const httpStageMeta = computed(() => paymentSuccessHttpStageMeta(detail.value || {}));
const relatedRoutes = computed(() => ({
  order: detail.value?.standardOrderNo ? orderDetailRoute(detail.value.standardOrderNo) : null,
  payment: detail.value?.paymentOrderId && !detail.value.successFactIssue
    ? paymentDetailRoute(detail.value.paymentOrderId) : null,
  eventFlow: detail.value?.eventNo ? paymentFlowDetailRoute("BUSINESS_EVENT", detail.value.eventNo) : null,
  flows: detail.value?.paymentOrderId ? paymentFlowsRoute({ paymentOrderId: detail.value.paymentOrderId }) : null,
  logs: detail.value?.paymentOrderId ? paymentLogsRoute({ paymentOrderId: detail.value.paymentOrderId }) : null
}));
const metricCards = computed(() => detail.value ? [
  { title: "发布状态", value: detail.value.publishStatus || "-", hint: detail.value.publishStatusType || "未记录样式" },
  { title: "事件类型", value: detail.value.eventType || "-", hint: detail.value.eventTopic || "主题未记录" },
  { title: "下游系统", value: detail.value.downstreamSystem || "-", hint: httpStageMeta.value.label },
  { title: "重试次数", value: detail.value.retryCount ?? "-", hint: detail.value.nextRetryAt || "无下次重试" }
] : []);

async function loadDetail(eventNo = currentEventNo.value) {
  const requestId = ++latestLoadRequestId;
  const expectedSnapshot = eventNo === currentEventNo.value && detail.value
    ? {
      eventNo: detail.value.eventNo,
      eventType: detail.value.eventType,
      paymentOrderId: detail.value.paymentOrderId,
      businessChainId: detail.value.businessChainId,
      bizNo: detail.value.bizNo,
      standardOrderNo: detail.value.standardOrderNo,
      eventTopic: detail.value.eventTopic,
      downstreamSystem: detail.value.downstreamSystem
    }
    : null;
  isLoading.value = true;
  loadErrorMessage.value = "";
  try {
    const result = await paymentEventApi.getDetail(eventNo);
    if (requestId !== latestLoadRequestId || eventNo !== currentEventNo.value) {
      return;
    }
    if (!result || result.eventNo !== eventNo) {
      throw new Error("支付事件详情响应为空或与当前路由不一致");
    }
    if (expectedSnapshot && !matchesPaymentEventDetailSnapshot(expectedSnapshot, result)) {
      throw new Error("支付事件详情响应为空或与当前支付归属快照不一致");
    }
    detail.value = result;
    hasLoadedData.value = true;
  } catch (error) {
    if (requestId !== latestLoadRequestId || eventNo !== currentEventNo.value) {
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

function formatValue(value) {
  return value == null || value === "" ? "-" : value;
}

function formatPayload(value) {
  return value || "暂无事件载荷";
}

watch(currentEventNo, (eventNo) => {
  latestLoadRequestId += 1;
  detail.value = null;
  hasLoadedData.value = false;
  loadErrorMessage.value = "";
  loadDetail(eventNo);
}, { immediate: true });
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付事件详情</h2>
        <p>按事件号精确核对出站事件、下游投递状态、重试信息和支付链路归属</p>
      </div>
      <div class="toolbar-actions">
        <button class="button secondary" @click="router.push(paymentEventsRoute({ paymentOrderId: detail?.paymentOrderId || '' }))">返回事件列表</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.order" @click="openRelatedRoute(relatedRoutes.order)">标准订单</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.payment" @click="openRelatedRoute(relatedRoutes.payment)">支付单</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.eventFlow" @click="openRelatedRoute(relatedRoutes.eventFlow)">事件流水</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.flows" @click="openRelatedRoute(relatedRoutes.flows)">同支付单流水</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.logs" @click="openRelatedRoute(relatedRoutes.logs)">处理日志</button>
        <button class="button secondary" :disabled="!canUseSnapshot" @click="openRelatedRoute('/payment-dead-letter-tasks')">死信补偿</button>
        <button class="button primary" :disabled="isLoading" @click="loadDetail()">{{ isLoading ? "刷新中..." : "刷新" }}</button>
      </div>
    </div>

    <section class="panel">
      <div v-if="loadErrorMessage" class="error-banner">
        {{ hasLoadedData ? "支付事件详情刷新失败，当前展示上次成功快照：" : "支付事件详情加载失败：" }}{{ loadErrorMessage }}
      </div>
      <div v-if="isLoading" class="state-box">支付事件详情加载中...</div>
      <div v-else-if="!hasLoadedData" class="state-box">当前没有可展示的支付事件详情快照</div>
      <template v-else-if="detail">
        <div v-if="detail.successFactIssue" class="error-banner">
          所属支付单成功事实异常（{{ detail.successFactIssue }}）；保留事件详情，支付单详情不可用。
        </div>
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
              <h3>事件事实</h3>
              <span class="meta">事件号唯一定位</span>
            </div>
            <div class="detail-grid detail-grid-wide">
              <div><strong>事件号：</strong><span class="mono-text">{{ formatValue(detail.eventNo) }}</span></div>
              <div><strong>事件类型：</strong>{{ formatValue(detail.eventType) }}</div>
              <div><strong>事件主题：</strong>{{ formatValue(detail.eventTopic) }}</div>
              <div><strong>下游系统：</strong>{{ formatValue(detail.downstreamSystem) }}</div>
              <div><strong>发布状态：</strong><span :class="['badge', detail.publishStatusType || 'info']">{{ formatValue(detail.publishStatus) }}</span></div>
              <div><strong>HTTP 投递阶段：</strong><span :class="['badge', httpStageMeta.badgeType]">{{ httpStageMeta.label }}</span></div>
              <div><strong>创建时间：</strong>{{ formatValue(detail.createdAt) }}</div>
              <div><strong>最近发布时间：</strong>{{ formatValue(detail.lastPublishedAt) }}</div>
              <div><strong>下次重试时间：</strong>{{ formatValue(detail.nextRetryAt) }}</div>
              <div><strong>重试次数：</strong>{{ formatValue(detail.retryCount) }}</div>
              <div><strong>最近重发操作人：</strong>{{ formatValue(detail.lastRepublishOperator) }}</div>
              <div><strong>最近重发时间：</strong>{{ formatValue(detail.lastRepublishedAt) }}</div>
            </div>
          </section>

          <section class="panel mini">
            <div class="section-title">
              <h3>关联与处置</h3>
              <span class="meta">只读排障入口</span>
            </div>
            <div class="detail-grid detail-grid-wide">
              <div><strong>支付单号：</strong>{{ formatValue(detail.paymentOrderId) }}</div>
              <div><strong>业务链路：</strong>{{ formatValue(detail.businessChainId) }}</div>
              <div><strong>业务单号：</strong>{{ formatValue(detail.bizNo) }}</div>
              <div><strong>标准订单号：</strong>{{ formatValue(detail.standardOrderNo) }}</div>
              <div><strong>事件流水来源：</strong>BUSINESS_EVENT</div>
            </div>
            <div class="ops-card detail-panel">
              <div class="ops-row"><span>标准订单联查</span><span>{{ detail.standardOrderNo ? "归属已确认" : "归属未确认，暂不可联查" }}</span></div>
              <div class="ops-row"><span>建议动作</span><span>{{ paymentEventRecommendedAction(detail) }}</span></div>
              <div class="ops-row"><span>事件流水</span><span>按 BUSINESS_EVENT + 事件号精确联查</span></div>
              <div class="ops-row"><span>导航状态</span><span>{{ canUseSnapshot ? "当前快照有效" : "当前快照不可用于联查" }}</span></div>
            </div>
          </section>
        </div>

        <div class="payload-grid detail-panel">
          <div>
            <strong>事件载荷</strong>
            <pre>{{ formatPayload(detail.eventPayload) }}</pre>
          </div>
          <div>
            <strong>处置边界</strong>
            <pre>详情页只展示事件事实和可信联查，不执行重发、不修改发布状态。需要人工重发时返回事件列表，在刷新成功且输入操作人后执行。</pre>
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
</style>
