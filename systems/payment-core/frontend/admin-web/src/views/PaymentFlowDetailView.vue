<script setup>
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentFlowApi } from "../api/client";
import {
  billDetailRoute,
  orderDetailRoute,
  paymentDetailRoute,
  paymentRequestDetailRoute
} from "../router/detailRoutes";
import { paymentFlowsRoute, paymentLogsRoute, paymentRequestsRoute } from "../router/listRoutes";

const route = useRoute();
const router = useRouter();
const detail = ref(null);
const isLoading = ref(true);
const hasLoadedData = ref(false);
const loadErrorMessage = ref("");
let latestLoadRequestId = 0;

const currentFlowSource = computed(() => typeof route.params.flowSource === "string"
  ? route.params.flowSource
  : "");
const currentFlowNo = computed(() => typeof route.params.flowNo === "string"
  ? route.params.flowNo
  : "");
const isSnapshotStale = computed(() => Boolean(hasLoadedData.value && loadErrorMessage.value));
const canUseSnapshot = computed(() => Boolean(
  hasLoadedData.value
  && detail.value?.flowSource === currentFlowSource.value
  && detail.value?.flowNo === currentFlowNo.value
  && !isLoading.value
  && !isSnapshotStale.value
));

const relatedRoutes = computed(() => ({
  order: detail.value?.standardOrderNo ? orderDetailRoute(detail.value.standardOrderNo) : null,
  bill: detail.value?.billNo ? billDetailRoute(detail.value.billNo) : null,
  payment: detail.value?.paymentOrderId ? paymentDetailRoute(detail.value.paymentOrderId) : null,
  request: detail.value?.paymentRequestNo
    ? paymentRequestDetailRoute(detail.value.paymentRequestNo)
    : null,
  flows: detail.value?.paymentOrderId
    ? paymentFlowsRoute({ paymentOrderId: detail.value.paymentOrderId })
    : null,
  logs: detail.value?.paymentOrderId
    ? paymentLogsRoute({ paymentOrderId: detail.value.paymentOrderId })
    : null
}));

const metricCards = computed(() => detail.value ? [
  { title: "流水来源", value: detail.value.flowSource || "-", hint: detail.value.flowNo },
  { title: "业务状态", value: detail.value.businessStatus || "-", hint: detail.value.flowType || "过程事实" },
  { title: "支付单号", value: detail.value.paymentOrderId || "未关联", hint: detail.value.channelCode || "渠道未记录" },
  { title: "创建时间", value: detail.value.createdAt || "-", hint: detail.value.completedAt || "未记录完成时间" }
] : []);

async function loadDetail(flowSource = currentFlowSource.value, flowNo = currentFlowNo.value) {
  const requestId = ++latestLoadRequestId;
  isLoading.value = true;
  loadErrorMessage.value = "";
  try {
    const result = await paymentFlowApi.getDetail(flowSource, flowNo);
    if (requestId !== latestLoadRequestId
      || flowSource !== currentFlowSource.value
      || flowNo !== currentFlowNo.value) {
      return;
    }
    if (!result || result.flowSource !== flowSource || result.flowNo !== flowNo) {
      throw new Error("支付流水详情响应为空或与当前路由不一致");
    }
    detail.value = result;
    hasLoadedData.value = true;
  } catch (error) {
    if (requestId !== latestLoadRequestId) return;
    loadErrorMessage.value = error.message;
  } finally {
    if (requestId === latestLoadRequestId) isLoading.value = false;
  }
}

function openRelatedRoute(targetRoute) {
  if (canUseSnapshot.value && targetRoute) router.push(targetRoute);
}

function formatValue(value) {
  return value == null || value === "" ? "-" : value;
}

function formatPayload(value) {
  return value || "暂无报文或载荷";
}

watch(
  () => [currentFlowSource.value, currentFlowNo.value],
  ([flowSource, flowNo]) => {
    latestLoadRequestId += 1;
    detail.value = null;
    hasLoadedData.value = false;
    loadErrorMessage.value = "";
    loadDetail(flowSource, flowNo);
  },
  { immediate: true }
);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付流水详情</h2>
        <p>按来源类型与来源编号精确定位单条支付过程事实，避免跨表裸编号歧义</p>
      </div>
      <div class="toolbar-actions">
        <button class="button secondary" @click="router.push(paymentFlowsRoute())">返回支付流水</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.order" @click="openRelatedRoute(relatedRoutes.order)">标准订单</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.bill" @click="openRelatedRoute(relatedRoutes.bill)">账单</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.payment" @click="openRelatedRoute(relatedRoutes.payment)">支付单</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.request" @click="openRelatedRoute(relatedRoutes.request)">支付请求</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.flows" @click="openRelatedRoute(relatedRoutes.flows)">同支付单流水</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.logs" @click="openRelatedRoute(relatedRoutes.logs)">处理日志</button>
        <button class="button primary" :disabled="isLoading" @click="loadDetail()">{{ isLoading ? "刷新中..." : "刷新" }}</button>
      </div>
    </div>

    <section class="panel">
      <div v-if="loadErrorMessage" class="error-banner">
        {{ hasLoadedData ? "支付流水详情刷新失败，当前展示上次成功快照：" : "支付流水详情加载失败：" }}{{ loadErrorMessage }}
      </div>
      <div v-if="isLoading" class="state-box">支付流水详情加载中...</div>
      <div v-else-if="!hasLoadedData" class="state-box">当前没有可展示的支付流水详情快照</div>
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
            <div class="section-title"><h3>事实标识</h3><span class="meta">复合唯一键</span></div>
            <div class="detail-grid detail-grid-wide">
              <div><strong>来源类型：</strong>{{ formatValue(detail.flowSource) }}</div>
              <div><strong>来源编号：</strong><span class="mono-text">{{ formatValue(detail.flowNo) }}</span></div>
              <div><strong>流水类型：</strong>{{ formatValue(detail.flowType) }}</div>
              <div><strong>支付方式：</strong>{{ formatValue(detail.paymentMethod) }}</div>
              <div><strong>渠道编码：</strong>{{ formatValue(detail.channelCode) }}</div>
              <div><strong>来源应用：</strong>{{ formatValue(detail.sourceAppId) }}</div>
              <div><strong>终端：</strong>{{ formatValue(detail.terminal) }}</div>
              <div><strong>客户端 IP：</strong>{{ formatValue(detail.clientIp) }}</div>
              <div><strong>幂等键：</strong><span class="mono-text">{{ formatValue(detail.idempotencyKey) }}</span></div>
              <div><strong>运营配置快照：</strong>{{ formatValue(detail.opsConfigSnapshotVersion) }}</div>
              <div><strong>创建时间：</strong>{{ formatValue(detail.createdAt) }}</div>
              <div><strong>完成时间：</strong>{{ formatValue(detail.completedAt) }}</div>
            </div>
          </section>
          <section class="panel mini">
            <div class="section-title"><h3>关联事实</h3><span class="meta">仅展示确定存在的归属</span></div>
            <div class="detail-grid detail-grid-wide">
              <div><strong>支付单号：</strong>{{ formatValue(detail.paymentOrderId) }}</div>
              <div><strong>业务订单号：</strong>{{ formatValue(detail.orderNo) }}</div>
              <div><strong>标准订单号：</strong>{{ formatValue(detail.standardOrderNo) }}</div>
              <div><strong>账单号：</strong>{{ formatValue(detail.billNo) }}</div>
              <div><strong>支付请求号：</strong>{{ formatValue(detail.paymentRequestNo) }}</div>
              <div><strong>路由编号：</strong>{{ formatValue(detail.routeNo) }}</div>
              <div><strong>路由结果：</strong>{{ formatValue(detail.routeResult) }}</div>
              <div><strong>路由规则：</strong>{{ formatValue(detail.routeRule) }}</div>
              <div><strong>回调类型：</strong>{{ formatValue(detail.notifyType) }}</div>
              <div><strong>事件主题：</strong>{{ formatValue(detail.eventTopic) }}</div>
              <div><strong>下游系统：</strong>{{ formatValue(detail.downstreamSystem) }}</div>
              <div><strong>发布状态：</strong>{{ formatValue(detail.publishStatus) }}</div>
              <div><strong>重试次数：</strong>{{ formatValue(detail.retryCount) }}</div>
              <div><strong>导航状态：</strong>{{ canUseSnapshot ? "当前快照有效" : "当前快照不可联查" }}</div>
            </div>
          </section>
        </div>
        <div class="payload-grid detail-panel">
          <div><strong>请求报文 / 事件载荷</strong><pre>{{ formatPayload(detail.requestPayload) }}</pre></div>
          <div><strong>响应报文 / 处理结果</strong><pre>{{ formatPayload(detail.responsePayload) }}</pre></div>
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
