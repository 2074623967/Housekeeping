<script setup>
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { billApi } from "../api/client";
import { orderDetailRoute, paymentDetailRoute, paymentRecordDetailRoute } from "../router/detailRoutes";
import { buildAppWebCashierUrl } from "../router/externalUrls";
import { billsRoute } from "../router/listRoutes";

const route = useRoute();
const router = useRouter();
const detail = ref(null);
const isLoading = ref(true);
const hasLoadedData = ref(false);
const loadErrorMessage = ref("");
let latestLoadRequestId = 0;

const currentBillNo = computed(() => typeof route.params.billNo === "string"
  ? route.params.billNo
  : "");
const isSnapshotStale = computed(() => Boolean(hasLoadedData.value && loadErrorMessage.value));
const canUseSnapshot = computed(() => Boolean(
  hasLoadedData.value
  && detail.value?.billNo === currentBillNo.value
  && !isLoading.value
  && !isSnapshotStale.value
));
const orderRoute = computed(() => detail.value?.standardOrderNo
  ? orderDetailRoute(detail.value.standardOrderNo)
  : null);
const paymentRoute = computed(() => detail.value?.latestPaymentOrderId
  ? paymentDetailRoute(detail.value.latestPaymentOrderId)
  : null);
const paymentRecordRoute = computed(() => detail.value?.latestPaymentOrderId
  ? paymentRecordDetailRoute(detail.value.latestPaymentOrderId)
  : null);
const cashierUrl = computed(() => detail.value?.latestPrepayOrderNo
  ? buildAppWebCashierUrl(detail.value.latestPrepayOrderNo)
  : "");
const metricCards = computed(() => detail.value ? [
  { title: "账单应收", value: detail.value.billAmount || "-", hint: "交易账单应收金额" },
  { title: "账单已付", value: detail.value.paidAmount || "-", hint: "payment-core 当前收款事实" },
  { title: "账单待付", value: detail.value.unpaidAmount || "-", hint: "应收减已付，最低为零" },
  { title: "最近支付", value: detail.value.latestPaymentStatus || "未发起", hint: detail.value.latestPaymentOrderId || "尚无关联支付单" }
] : []);

async function loadDetail(billNo = currentBillNo.value) {
  const requestId = ++latestLoadRequestId;
  isLoading.value = true;
  loadErrorMessage.value = "";
  try {
    const billDetail = await billApi.getDetail(billNo);
    if (requestId !== latestLoadRequestId || billNo !== currentBillNo.value) {
      return;
    }
    if (!billDetail || billDetail.billNo !== billNo) {
      throw new Error("账单详情响应为空或与当前路由不一致");
    }
    detail.value = billDetail;
    hasLoadedData.value = true;
  } catch (error) {
    if (requestId !== latestLoadRequestId || billNo !== currentBillNo.value) {
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

watch(currentBillNo, (billNo) => {
  latestLoadRequestId += 1;
  detail.value = null;
  hasLoadedData.value = false;
  loadErrorMessage.value = "";
  loadDetail(billNo);
}, { immediate: true });
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>账单支付详情</h2>
        <p>按唯一账单查看应收进度、最近预付单与最近关联支付单</p>
      </div>
      <div class="toolbar-actions">
        <button class="button secondary" @click="router.push(billsRoute({ billNo: currentBillNo }))">返回账单列表</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !orderRoute" @click="openRelatedRoute(orderRoute)">查看标准订单</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !paymentRoute" @click="openRelatedRoute(paymentRoute)">查看支付单</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !paymentRecordRoute" @click="openRelatedRoute(paymentRecordRoute)">查看支付记录</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !cashierUrl" @click="openCashier">打开收银台</button>
        <button class="button primary" :disabled="isLoading" @click="loadDetail()">{{ isLoading ? "刷新中..." : "刷新" }}</button>
      </div>
    </div>

    <section class="panel">
      <div v-if="loadErrorMessage" class="error-banner">
        {{ hasLoadedData ? "账单详情刷新失败，当前展示上次成功快照：" : "账单详情加载失败：" }}{{ loadErrorMessage }}
      </div>
      <div v-if="isLoading" class="state-box">账单详情加载中...</div>
      <div v-else-if="!hasLoadedData" class="state-box">当前没有可展示的账单详情快照</div>
      <template v-else-if="detail">
        <div class="detail-card-grid">
          <div v-for="card in metricCards" :key="card.title" class="detail-card">
            <div class="detail-label">{{ card.title }}</div>
            <div class="detail-value">{{ card.value }}</div>
            <div class="meta">{{ card.hint }}</div>
          </div>
        </div>

        <section class="panel mini detail-panel">
          <div class="section-title">
            <h3>账单事实</h3>
            <span class="meta">唯一账单权威快照</span>
          </div>
          <div class="detail-grid detail-grid-wide">
            <div><strong>账单号：</strong>{{ formatValue(detail.billNo) }}</div>
            <div><strong>业务订单号：</strong>{{ formatValue(detail.orderNo) }}</div>
            <div><strong>客户：</strong>{{ formatValue(detail.customerName) }}</div>
            <div><strong>账单状态：</strong><span :class="['badge', detail.billStatusType]">{{ formatValue(detail.billStatus) }}</span></div>
            <div><strong>到期时间：</strong>{{ formatValue(detail.dueAt) }}</div>
            <div><strong>创建时间：</strong>{{ formatValue(detail.createdAt) }}</div>
          </div>
        </section>

        <div class="split-panels detail-panel">
          <section class="panel mini">
            <div class="section-title">
              <h3>最近预付单</h3>
              <span class="meta">按创建时间与主键稳定选择</span>
            </div>
            <div class="detail-grid detail-grid-wide">
              <div><strong>预付单号：</strong>{{ formatValue(detail.latestPrepayOrderNo) }}</div>
              <div><strong>收银台状态：</strong><span :class="['badge', detail.latestCashierStatusType || 'info']">{{ formatValue(detail.latestCashierStatus) }}</span></div>
              <div><strong>预付金额：</strong>{{ formatValue(detail.latestPrepayAmount) }}</div>
              <div><strong>支付场景：</strong>{{ formatValue(detail.latestPayScene) }}</div>
              <div><strong>失效时间：</strong>{{ formatValue(detail.latestPrepayExpiresAt) }}</div>
            </div>
          </section>

          <section class="panel mini">
            <div class="section-title">
              <h3>最近关联支付单</h3>
              <span class="meta">经账单预付关系精确关联</span>
            </div>
            <div class="detail-grid detail-grid-wide">
              <div><strong>支付单号：</strong>{{ formatValue(detail.latestPaymentOrderId) }}</div>
              <div><strong>关联预付单：</strong>{{ formatValue(detail.latestPaymentPrepayOrderNo) }}</div>
              <div><strong>支付状态：</strong><span :class="['badge', detail.latestPaymentStatusType || 'info']">{{ formatValue(detail.latestPaymentStatus) }}</span></div>
              <div><strong>支付金额：</strong>{{ formatValue(detail.latestPaymentAmount) }}</div>
              <div><strong>支付方式：</strong>{{ formatValue(detail.latestPaymentMethod) }}</div>
              <div><strong>支付渠道：</strong>{{ formatValue(detail.latestPaymentChannel) }}</div>
              <div><strong>成功时间：</strong>{{ formatValue(detail.latestPaymentSuccessAt) }}</div>
            </div>
          </section>
        </div>
      </template>
    </section>
  </div>
</template>
