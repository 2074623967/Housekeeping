<script setup>
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { matchesOrderDetailSnapshot, orderApi } from "../api/client";
import { billDetailRoute, paymentDetailRoute, paymentRecordDetailRoute } from "../router/detailRoutes";
import { buildAppWebCashierUrl } from "../router/externalUrls";
import { ordersRoute } from "../router/listRoutes";

const route = useRoute();
const router = useRouter();
const detail = ref(null);
const isLoading = ref(true);
const hasLoadedData = ref(false);
const loadErrorMessage = ref("");
let latestLoadRequestId = 0;

const currentOrderNo = computed(() => typeof route.params.orderNo === "string"
  ? route.params.orderNo
  : "");
const isSnapshotStale = computed(() => Boolean(hasLoadedData.value && loadErrorMessage.value));
const canUseSnapshot = computed(() => Boolean(
  hasLoadedData.value
  && detail.value?.orderNo === currentOrderNo.value
  && !isLoading.value
  && !isSnapshotStale.value
));
const paymentRoute = computed(() => detail.value?.latestPaymentOrderId
  ? paymentDetailRoute(detail.value.latestPaymentOrderId)
  : null);
const paymentRecordRoute = computed(() => detail.value?.latestPaymentOrderId
  ? paymentRecordDetailRoute(detail.value.latestPaymentOrderId)
  : null);
const billRoute = computed(() => detail.value?.billNo
  ? billDetailRoute(detail.value.billNo)
  : null);
const cashierUrl = computed(() => detail.value?.latestPrepayOrderNo
  ? buildAppWebCashierUrl(detail.value.latestPrepayOrderNo, undefined, detail.value.latestPayScene)
  : "");
const metricCards = computed(() => detail.value ? [
  { title: "订单应收", value: detail.value.orderAmount || "-", hint: "标准订单应收金额" },
  { title: "订单已付", value: detail.value.paidAmount || "-", hint: "payment-core 当前订单支付事实" },
  { title: "剩余应付", value: detail.value.outstandingAmount || "-", hint: "应收减已付，最低为零" },
  { title: "最近支付", value: detail.value.latestPaymentStatus || "未发起", hint: detail.value.latestPaymentOrderId || "尚无支付单" }
] : []);

async function loadDetail(orderNo = currentOrderNo.value) {
  const requestId = ++latestLoadRequestId;
  const expectedSnapshot = orderNo === currentOrderNo.value && detail.value
    ? {
      orderNo: detail.value.orderNo,
      orderAmount: detail.value.orderAmount,
      latestPayScene: detail.value.latestPayScene
    }
    : null;
  isLoading.value = true;
  loadErrorMessage.value = "";
  try {
    const orderDetail = await orderApi.getDetail(orderNo);
    if (requestId !== latestLoadRequestId || orderNo !== currentOrderNo.value) {
      return;
    }
    if (!orderDetail || orderDetail.orderNo !== orderNo) {
      throw new Error("订单详情响应为空或与当前路由不一致");
    }
    if (expectedSnapshot && !matchesOrderDetailSnapshot(expectedSnapshot, orderDetail)) {
      throw new Error("订单详情响应为空或与当前订单金额快照不一致");
    }
    detail.value = orderDetail;
    hasLoadedData.value = true;
  } catch (error) {
    if (requestId !== latestLoadRequestId || orderNo !== currentOrderNo.value) {
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

function openCashier() {
  if (!canUseSnapshot.value || !cashierUrl.value) {
    return;
  }
  window.open(cashierUrl.value, "_blank", "noopener,noreferrer");
}

function formatValue(value) {
  return value == null || value === "" ? "-" : value;
}

watch(currentOrderNo, (orderNo) => {
  latestLoadRequestId += 1;
  detail.value = null;
  hasLoadedData.value = false;
  loadErrorMessage.value = "";
  loadDetail(orderNo);
}, { immediate: true });
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>订单支付详情</h2>
        <p>按标准订单聚合最近账单、支付单与收银台事实，统一完成订单侧联查</p>
      </div>
      <div class="toolbar-actions">
        <button class="button secondary" @click="router.push(ordersRoute({ orderNo: currentOrderNo }))">返回订单列表</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !paymentRoute" @click="openRelatedRoute(paymentRoute)">查看支付单</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !paymentRecordRoute" @click="openRelatedRoute(paymentRecordRoute)">查看支付记录</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !billRoute" @click="openRelatedRoute(billRoute)">查看账单</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !cashierUrl" @click="openCashier">打开收银台</button>
        <button class="button primary" :disabled="isLoading" @click="loadDetail()">{{ isLoading ? "刷新中..." : "刷新" }}</button>
      </div>
    </div>

    <section class="panel">
      <div v-if="loadErrorMessage" class="error-banner">
        {{ hasLoadedData ? "订单详情刷新失败，当前展示上次成功快照：" : "订单详情加载失败：" }}{{ loadErrorMessage }}
      </div>
      <div v-if="isLoading" class="state-box">订单详情加载中...</div>
      <div v-else-if="!hasLoadedData" class="state-box">当前没有可展示的订单详情快照</div>
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
              <h3>订单与履约</h3>
              <span class="meta">标准订单权威快照</span>
            </div>
            <div class="detail-grid detail-grid-wide">
              <div><strong>订单号：</strong>{{ formatValue(detail.orderNo) }}</div>
              <div><strong>客户：</strong>{{ formatValue(detail.customerName) }}</div>
              <div><strong>服务品类：</strong>{{ formatValue(detail.serviceType) }}</div>
              <div><strong>服务者：</strong>{{ formatValue(detail.workerName) }}</div>
              <div><strong>订单状态：</strong><span :class="['badge', detail.orderStatusType]">{{ formatValue(detail.orderStatus) }}</span></div>
              <div><strong>履约状态：</strong><span :class="['badge', detail.fulfillmentStatusType]">{{ formatValue(detail.fulfillmentStatus) }}</span></div>
              <div><strong>创建时间：</strong>{{ formatValue(detail.createdAt) }}</div>
            </div>
          </section>

          <section class="panel mini">
            <div class="section-title">
              <h3>最近账单</h3>
              <span class="meta">按创建时间与主键稳定选择</span>
            </div>
            <div class="detail-grid detail-grid-wide">
              <div><strong>账单号：</strong>{{ formatValue(detail.billNo) }}</div>
              <div><strong>账单状态：</strong><span :class="['badge', detail.billStatusType || 'info']">{{ formatValue(detail.billStatus) }}</span></div>
              <div><strong>账单应收：</strong>{{ formatValue(detail.billAmount) }}</div>
              <div><strong>账单已付：</strong>{{ formatValue(detail.billPaidAmount) }}</div>
              <div><strong>到期时间：</strong>{{ formatValue(detail.billDueAt) }}</div>
            </div>
          </section>
        </div>

        <div class="split-panels detail-panel">
          <section class="panel mini">
            <div class="section-title">
              <h3>最近支付单</h3>
              <span class="meta">支付核心域当前投影</span>
            </div>
            <div class="detail-grid detail-grid-wide">
              <div><strong>支付单号：</strong>{{ formatValue(detail.latestPaymentOrderId) }}</div>
              <div><strong>支付状态：</strong><span :class="['badge', detail.latestPaymentStatusType || 'info']">{{ formatValue(detail.latestPaymentStatus) }}</span></div>
              <div><strong>支付金额：</strong>{{ formatValue(detail.latestPaymentAmount) }}</div>
              <div><strong>支付方式：</strong>{{ formatValue(detail.latestPaymentMethod) }}</div>
              <div><strong>支付渠道：</strong>{{ formatValue(detail.latestPaymentChannel) }}</div>
              <div><strong>成功时间：</strong>{{ formatValue(detail.latestPaymentSuccessAt) }}</div>
            </div>
          </section>

          <section class="panel mini">
            <div class="section-title">
              <h3>最近收银台</h3>
              <span class="meta">只展示预付与会话事实</span>
            </div>
            <div class="detail-grid detail-grid-wide">
              <div><strong>预付单号：</strong>{{ formatValue(detail.latestPrepayOrderNo) }}</div>
              <div><strong>收银台状态：</strong><span :class="['badge', detail.latestCashierStatusType || 'info']">{{ formatValue(detail.latestCashierStatus) }}</span></div>
              <div><strong>预付金额：</strong>{{ formatValue(detail.latestPrepayAmount) }}</div>
              <div><strong>支付场景：</strong>{{ formatValue(detail.latestPayScene) }}</div>
              <div><strong>失效时间：</strong>{{ formatValue(detail.latestPrepayExpiresAt) }}</div>
            </div>
          </section>
        </div>
      </template>
    </section>
  </div>
</template>
