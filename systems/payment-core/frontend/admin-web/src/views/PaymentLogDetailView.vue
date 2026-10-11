<script setup>
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { matchesPaymentLogDetailSnapshot, paymentLogApi } from "../api/client";
import {
  orderDetailRoute,
  paymentDetailRoute,
  paymentFlowDetailRoute,
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

const currentLogNo = computed(() => typeof route.params.logNo === "string"
  ? route.params.logNo
  : "");
const isSnapshotStale = computed(() => Boolean(hasLoadedData.value && loadErrorMessage.value));
const canUseSnapshot = computed(() => Boolean(
  hasLoadedData.value
  && detail.value?.logNo === currentLogNo.value
  && !isLoading.value
  && !isSnapshotStale.value
));
const flowSourceMapping = {
  PAYMENT_ATTEMPT: "PAYMENT_ATTEMPT",
  CHANNEL_NOTIFY: "CHANNEL_NOTIFY",
  ROUTE_RECORD: "ROUTE_RECORD",
  BUSINESS_EVENT: "BUSINESS_EVENT",
  PAYMENT_EVENT: "BUSINESS_EVENT"
};
const linkedFlowSource = computed(() => detail.value?.sourceType
  ? flowSourceMapping[detail.value.sourceType]
  : "");
const canLinkFlow = computed(() => Boolean(
  linkedFlowSource.value
  && detail.value?.sourceNo
));
const relatedRoutes = computed(() => ({
  order: detail.value?.standardOrderNo ? orderDetailRoute(detail.value.standardOrderNo) : null,
  payment: detail.value?.paymentOrderId && !detail.value.successFactIssue
    ? paymentDetailRoute(detail.value.paymentOrderId) : null,
  request: detail.value?.requestNo ? paymentRequestDetailRoute(detail.value.requestNo) : null,
  flow: canLinkFlow.value ? paymentFlowDetailRoute(linkedFlowSource.value, detail.value.sourceNo) : null,
  flows: detail.value?.paymentOrderId ? paymentFlowsRoute({ paymentOrderId: detail.value.paymentOrderId }) : null,
  requests: detail.value?.paymentOrderId ? paymentRequestsRoute({ paymentOrderId: detail.value.paymentOrderId }) : null
}));
const metricCards = computed(() => detail.value ? [
  { title: "日志级别", value: detail.value.logLevel || "-", hint: detail.value.logLevelType || "未记录样式" },
  { title: "处理阶段", value: detail.value.processStage || "-", hint: detail.value.source || "来源未记录" },
  { title: "支付单号", value: detail.value.paymentOrderId || "未关联", hint: detail.value.orderNo || "订单未记录" },
  { title: "创建时间", value: detail.value.createdAt || "-", hint: detail.value.logNo }
] : []);

async function loadDetail(logNo = currentLogNo.value) {
  const requestId = ++latestLoadRequestId;
  const expectedSnapshot = logNo === currentLogNo.value && detail.value
    ? {
      logNo: detail.value.logNo,
      paymentOrderId: detail.value.paymentOrderId,
      businessChainId: detail.value.businessChainId,
      orderNo: detail.value.orderNo,
      standardOrderNo: detail.value.standardOrderNo,
      requestNo: detail.value.requestNo,
      sourceType: detail.value.sourceType,
      sourceNo: detail.value.sourceNo,
      processStage: detail.value.processStage
    }
    : null;
  isLoading.value = true;
  loadErrorMessage.value = "";
  try {
    const result = await paymentLogApi.getDetail(logNo);
    if (requestId !== latestLoadRequestId || logNo !== currentLogNo.value) {
      return;
    }
    if (!result || result.logNo !== logNo) {
      throw new Error("支付处理日志详情响应为空或与当前路由不一致");
    }
    if (expectedSnapshot && !matchesPaymentLogDetailSnapshot(expectedSnapshot, result)) {
      throw new Error("支付处理日志详情响应为空或与当前支付归属快照不一致");
    }
    detail.value = result;
    hasLoadedData.value = true;
  } catch (error) {
    if (requestId !== latestLoadRequestId || logNo !== currentLogNo.value) {
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

function formatMessage(value) {
  return value || "暂无日志消息";
}

watch(currentLogNo, (logNo) => {
  latestLoadRequestId += 1;
  detail.value = null;
  hasLoadedData.value = false;
  loadErrorMessage.value = "";
  loadDetail(logNo);
}, { immediate: true });
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付处理日志详情</h2>
        <p>按日志编号精确定位单条处理事实，核对来源、阶段、请求与支付单关联</p>
      </div>
      <div class="toolbar-actions">
        <button class="button secondary" @click="router.push(paymentLogsRoute({ paymentOrderId: detail?.paymentOrderId || '' }))">返回日志列表</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.order" @click="openRelatedRoute(relatedRoutes.order)">标准订单</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.payment" @click="openRelatedRoute(relatedRoutes.payment)">支付单</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.request" @click="openRelatedRoute(relatedRoutes.request)">支付请求</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.flow" @click="openRelatedRoute(relatedRoutes.flow)">支付流水</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.flows" @click="openRelatedRoute(relatedRoutes.flows)">同支付单流水</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.requests" @click="openRelatedRoute(relatedRoutes.requests)">同支付单请求</button>
        <button class="button primary" :disabled="isLoading" @click="loadDetail()">{{ isLoading ? "刷新中..." : "刷新" }}</button>
      </div>
    </div>

    <section class="panel">
      <div v-if="loadErrorMessage" class="error-banner">
        {{ hasLoadedData ? "支付处理日志详情刷新失败，当前展示上次成功快照：" : "支付处理日志详情加载失败：" }}{{ loadErrorMessage }}
      </div>
      <div v-if="isLoading" class="state-box">支付处理日志详情加载中...</div>
      <div v-else-if="!hasLoadedData" class="state-box">当前没有可展示的支付处理日志详情快照</div>
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
              <h3>日志事实</h3>
              <span class="meta">日志号唯一定位</span>
            </div>
            <div class="detail-grid detail-grid-wide">
              <div><strong>日志编号：</strong><span class="mono-text">{{ formatValue(detail.logNo) }}</span></div>
              <div><strong>业务链路：</strong><span class="mono-text">{{ formatValue(detail.businessChainId) }}</span></div>
              <div><strong>处理阶段：</strong>{{ formatValue(detail.processStage) }}</div>
              <div><strong>日志级别：</strong><span :class="['badge', detail.logLevelType || 'info']">{{ formatValue(detail.logLevel) }}</span></div>
              <div><strong>级别样式：</strong>{{ formatValue(detail.logLevelType) }}</div>
              <div><strong>来源系统：</strong>{{ formatValue(detail.source) }}</div>
              <div><strong>创建时间：</strong>{{ formatValue(detail.createdAt) }}</div>
            </div>
          </section>

          <section class="panel mini">
            <div class="section-title">
              <h3>关联事实</h3>
              <span class="meta">仅展示接口返回的确定归属</span>
            </div>
            <div class="detail-grid detail-grid-wide">
              <div><strong>支付单号：</strong>{{ formatValue(detail.paymentOrderId) }}</div>
              <div v-if="detail.successFactIssue"><strong>成功事实异常：</strong>{{ detail.successFactIssue === "SUCCESS_TIME_MISSING" ? "成功时间缺失" : "成功事实不一致" }}；支付详情暂不可用</div>
              <div><strong>业务订单号：</strong>{{ formatValue(detail.orderNo) }}</div>
              <div><strong>标准订单号：</strong>{{ formatValue(detail.standardOrderNo) }}</div>
              <div><strong>支付请求号：</strong>{{ formatValue(detail.requestNo) }}</div>
              <div><strong>来源类型：</strong>{{ formatValue(detail.sourceType) }}</div>
              <div><strong>来源编号：</strong><span class="mono-text">{{ formatValue(detail.sourceNo) }}</span></div>
            </div>
            <div class="ops-card detail-panel">
              <div class="ops-row"><span>支付流水联查</span><span>{{ canLinkFlow ? `可按 ${linkedFlowSource} + 来源编号精确联查` : "当前来源不可安全映射为流水详情" }}</span></div>
              <div class="ops-row"><span>标准订单联查</span><span>{{ detail.standardOrderNo ? "归属已确认" : "归属未确认，暂不可联查" }}</span></div>
              <div class="ops-row"><span>导航状态</span><span>{{ canUseSnapshot ? "当前快照有效" : "当前快照不可用于联查" }}</span></div>
            </div>
          </section>
        </div>

        <div class="payload-grid detail-panel">
          <div>
            <strong>日志消息</strong>
            <pre>{{ formatMessage(detail.message) }}</pre>
          </div>
          <div>
            <strong>排障提示</strong>
            <pre>{{ canLinkFlow ? "建议从支付流水详情继续核对同一来源事实，再回到支付请求、支付单完成闭环。" : "建议先核对支付单、支付请求和同支付单日志，避免将不可映射来源误判为流水事实。" }}</pre>
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
