<script setup>
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { matchesPaymentRouteDetailSnapshot, paymentRouteApi } from "../api/client";
import {
  paymentDetailRoute,
  paymentFlowDetailRoute,
  paymentRequestDetailRoute
} from "../router/detailRoutes";
import { paymentFlowsRoute, paymentLogsRoute, paymentRoutesRoute } from "../router/listRoutes";

const route = useRoute();
const router = useRouter();
const detail = ref(null);
const isLoading = ref(true);
const hasLoadedData = ref(false);
const loadErrorMessage = ref("");
let latestLoadRequestId = 0;

const currentRouteNo = computed(() => typeof route.params.routeNo === "string"
  ? route.params.routeNo
  : "");
const isSnapshotStale = computed(() => Boolean(hasLoadedData.value && loadErrorMessage.value));
const canUseSnapshot = computed(() => Boolean(
  hasLoadedData.value
  && detail.value?.routeNo === currentRouteNo.value
  && !isLoading.value
  && !isSnapshotStale.value
));
const relatedRoutes = computed(() => ({
  payment: detail.value?.paymentOrderId && !detail.value.successFactIssue
    ? paymentDetailRoute(detail.value.paymentOrderId) : null,
  request: detail.value?.requestNo ? paymentRequestDetailRoute(detail.value.requestNo) : null,
  routeFlow: detail.value?.routeNo ? paymentFlowDetailRoute("ROUTE_RECORD", detail.value.routeNo) : null,
  flows: detail.value?.paymentOrderId ? paymentFlowsRoute({ paymentOrderId: detail.value.paymentOrderId }) : null,
  logs: detail.value?.paymentOrderId ? paymentLogsRoute({ paymentOrderId: detail.value.paymentOrderId }) : null
}));
const metricCards = computed(() => detail.value ? [
  { title: "路由结果", value: detail.value.routeResult || "-", hint: detail.value.routeResultType || "未记录样式" },
  { title: "命中渠道", value: detail.value.channelCode || "-", hint: detail.value.routeRule || "规则未记录" },
  { title: "支付单号", value: detail.value.paymentOrderId || "未关联", hint: detail.value.orderNo || "订单未记录" },
  { title: "创建时间", value: detail.value.createdAt || "-", hint: detail.value.routeNo }
] : []);

async function loadDetail(routeNo = currentRouteNo.value) {
  const requestId = ++latestLoadRequestId;
  const expectedSnapshot = routeNo === currentRouteNo.value && detail.value
    ? {
      routeNo: detail.value.routeNo,
      paymentOrderId: detail.value.paymentOrderId,
      businessChainId: detail.value.businessChainId,
      requestNo: detail.value.requestNo,
      orderNo: detail.value.orderNo,
      paymentMethod: detail.value.paymentMethod,
      customerName: detail.value.customerName,
      amount: detail.value.amount,
      channelCode: detail.value.channelCode
    }
    : null;
  isLoading.value = true;
  loadErrorMessage.value = "";
  try {
    const result = await paymentRouteApi.getDetail(routeNo);
    if (requestId !== latestLoadRequestId || routeNo !== currentRouteNo.value) {
      return;
    }
    if (!result || result.routeNo !== routeNo) {
      throw new Error("支付路由详情响应为空或与当前路由不一致");
    }
    if (expectedSnapshot && !matchesPaymentRouteDetailSnapshot(expectedSnapshot, result)) {
      throw new Error("支付路由详情响应为空或与当前支付关联/订单/金额快照不一致");
    }
    detail.value = result;
    hasLoadedData.value = true;
  } catch (error) {
    if (requestId !== latestLoadRequestId || routeNo !== currentRouteNo.value) {
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
  return value || "暂无路由报文";
}

watch(currentRouteNo, (routeNo) => {
  latestLoadRequestId += 1;
  detail.value = null;
  hasLoadedData.value = false;
  loadErrorMessage.value = "";
  loadDetail(routeNo);
}, { immediate: true });
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付路由执行详情</h2>
        <p>按路由记录号唯一定位渠道命中、配置快照、请求归属和支付链路联查入口</p>
      </div>
      <div class="toolbar-actions">
        <button class="button secondary" @click="router.push(paymentRoutesRoute({ paymentOrderId: detail?.paymentOrderId || '' }))">返回路由列表</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.payment" @click="openRelatedRoute(relatedRoutes.payment)">支付单</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.request" @click="openRelatedRoute(relatedRoutes.request)">支付请求</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.routeFlow" @click="openRelatedRoute(relatedRoutes.routeFlow)">路由流水</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.flows" @click="openRelatedRoute(relatedRoutes.flows)">同支付单流水</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !relatedRoutes.logs" @click="openRelatedRoute(relatedRoutes.logs)">处理日志</button>
        <button class="button primary" :disabled="isLoading" @click="loadDetail()">{{ isLoading ? "刷新中..." : "刷新" }}</button>
      </div>
    </div>

    <section class="panel">
      <div v-if="loadErrorMessage" class="error-banner">
        {{ hasLoadedData ? "支付路由详情刷新失败，当前展示上次成功快照：" : "支付路由详情加载失败：" }}{{ loadErrorMessage }}
      </div>
      <div v-if="canUseSnapshot && detail?.successFactIssue" class="error-banner">
        所属支付单成功事实异常（{{ detail.successFactIssue }}）；保留路由详情，支付单详情不可用。
      </div>
      <div v-if="isLoading" class="state-box">支付路由详情加载中...</div>
      <div v-else-if="!hasLoadedData" class="state-box">当前没有可展示的支付路由详情快照</div>
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
              <h3>路由事实</h3>
              <span class="meta">路由记录号唯一定位</span>
            </div>
            <div class="detail-grid detail-grid-wide">
              <div><strong>路由记录号：</strong><span class="mono-text">{{ formatValue(detail.routeNo) }}</span></div>
              <div><strong>支付请求号：</strong><span class="mono-text">{{ formatValue(detail.requestNo) }}</span></div>
              <div><strong>命中渠道：</strong>{{ formatValue(detail.channelCode) }}</div>
              <div><strong>支付方式：</strong>{{ formatValue(detail.paymentMethod) }}</div>
              <div><strong>路由结果：</strong><span :class="['badge', detail.routeResultType || 'info']">{{ formatValue(detail.routeResult) }}</span></div>
              <div><strong>配置快照版本：</strong>{{ formatValue(detail.opsConfigSnapshotVersion) }}</div>
              <div><strong>来源终端：</strong>{{ formatValue(detail.terminal) }}</div>
              <div><strong>创建时间：</strong>{{ formatValue(detail.createdAt) }}</div>
              <div class="detail-grid-full"><strong>路由规则：</strong>{{ formatValue(detail.routeRule) }}</div>
            </div>
          </section>

          <section class="panel mini">
            <div class="section-title">
              <h3>关联归属</h3>
              <span class="meta">仅按精确请求号补齐请求上下文</span>
            </div>
            <div class="detail-grid detail-grid-wide">
              <div><strong>支付单号：</strong>{{ formatValue(detail.paymentOrderId) }}</div>
              <div><strong>业务链路：</strong><span class="mono-text">{{ formatValue(detail.businessChainId) }}</span></div>
              <div><strong>业务订单号：</strong>{{ formatValue(detail.orderNo) }}</div>
              <div><strong>预付单号：</strong>{{ formatValue(detail.prepayOrderNo) }}</div>
              <div><strong>客户名称：</strong>{{ formatValue(detail.customerName) }}</div>
              <div><strong>支付金额：</strong>{{ formatValue(detail.amount) }}</div>
              <div><strong>客户端 IP：</strong>{{ formatValue(detail.clientIp) }}</div>
              <div class="detail-grid-full"><strong>幂等键：</strong><span class="mono-text">{{ formatValue(detail.idempotencyKey) }}</span></div>
            </div>
            <div class="ops-card detail-panel">
              <div class="ops-row"><span>请求归属</span><span>{{ detail.requestNo ? "已按尝试号精确关联" : "历史路由无尝试号，不猜测请求归属" }}</span></div>
              <div class="ops-row"><span>路由流水</span><span>按 ROUTE_RECORD + 路由记录号精确联查</span></div>
              <div class="ops-row"><span>导航状态</span><span>{{ canUseSnapshot ? "当前快照有效" : "当前快照不可用于联查" }}</span></div>
            </div>
          </section>
        </div>

        <div class="payload-grid detail-panel">
          <div>
            <strong>请求报文</strong>
            <pre>{{ formatPayload(detail.requestPayload) }}</pre>
          </div>
          <div>
            <strong>响应报文</strong>
            <pre>{{ formatPayload(detail.responsePayload) }}</pre>
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
