<script setup>
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { cashierSessionApi } from "../api/client";
import {
  billDetailRoute,
  orderDetailRoute,
  paymentDetailRoute,
  paymentRecordDetailRoute
} from "../router/detailRoutes";
import { buildAppWebCashierUrl } from "../router/externalUrls";
import { cashierSessionsRoute, paymentFlowsRoute } from "../router/listRoutes";

const route = useRoute();
const router = useRouter();
const detail = ref(null);
const isLoading = ref(true);
const hasLoadedData = ref(false);
const loadErrorMessage = ref("");
let latestLoadRequestId = 0;

const currentSessionNo = computed(() => typeof route.params.sessionNo === "string"
  ? route.params.sessionNo
  : "");
const isSnapshotStale = computed(() => Boolean(hasLoadedData.value && loadErrorMessage.value));
const canUseSnapshot = computed(() => Boolean(
  hasLoadedData.value
  && detail.value?.sessionNo === currentSessionNo.value
  && !isLoading.value
  && !isSnapshotStale.value
));
const orderRoute = computed(() => detail.value?.standardOrderNo
  ? orderDetailRoute(detail.value.standardOrderNo)
  : null);
const billRoute = computed(() => detail.value?.billNo
  ? billDetailRoute(detail.value.billNo)
  : null);
const paymentRoute = computed(() => detail.value?.paymentOrderId
  ? paymentDetailRoute(detail.value.paymentOrderId)
  : null);
const paymentRecordRoute = computed(() => detail.value?.paymentOrderId
  ? paymentRecordDetailRoute(detail.value.paymentOrderId)
  : null);
const paymentFlowRoute = computed(() => detail.value?.paymentOrderId
  ? paymentFlowsRoute({ paymentOrderId: detail.value.paymentOrderId })
  : null);
const cashierUrl = computed(() => detail.value?.prepayOrderNo
  ? buildAppWebCashierUrl(detail.value.prepayOrderNo)
  : "");
const metricCards = computed(() => detail.value ? [
  { title: "会话金额", value: detail.value.amount || "-", hint: detail.value.prepayOrderNo || "预付单未关联" },
  { title: "会话状态", value: detail.value.sessionStatus || "-", hint: `失效时间 ${detail.value.expiresAt || "-"}` },
  { title: "业务场景", value: detail.value.payScene || "-", hint: `最近终端 ${detail.value.terminal || "-"}` },
  { title: "支付关联", value: detail.value.paymentOrderId || "未关联", hint: detail.value.billNo || "账单未关联" }
] : []);

async function loadDetail(sessionNo = currentSessionNo.value) {
  const requestId = ++latestLoadRequestId;
  isLoading.value = true;
  loadErrorMessage.value = "";
  try {
    const cashierDetail = await cashierSessionApi.getDetail(sessionNo);
    if (requestId !== latestLoadRequestId || sessionNo !== currentSessionNo.value) {
      return;
    }
    if (!cashierDetail || cashierDetail.sessionNo !== sessionNo) {
      throw new Error("收银台会话详情响应为空或与当前路由不一致");
    }
    detail.value = cashierDetail;
    hasLoadedData.value = true;
  } catch (error) {
    if (requestId !== latestLoadRequestId || sessionNo !== currentSessionNo.value) {
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

watch(currentSessionNo, (sessionNo) => {
  latestLoadRequestId += 1;
  detail.value = null;
  hasLoadedData.value = false;
  loadErrorMessage.value = "";
  loadDetail(sessionNo);
}, { immediate: true });
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>收银台会话详情</h2>
        <p>按唯一预付单会话联查标准订单、账单、支付单、支付记录与支付流水</p>
      </div>
      <div class="toolbar-actions">
        <button class="button secondary" @click="router.push(cashierSessionsRoute({ sessionNo: currentSessionNo }))">返回会话列表</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !orderRoute" @click="openRelatedRoute(orderRoute)">查看标准订单</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !billRoute" @click="openRelatedRoute(billRoute)">查看账单</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !paymentRoute" @click="openRelatedRoute(paymentRoute)">查看支付单</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !paymentRecordRoute" @click="openRelatedRoute(paymentRecordRoute)">查看支付记录</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !paymentFlowRoute" @click="openRelatedRoute(paymentFlowRoute)">查看支付流水</button>
        <button class="button secondary" :disabled="!canUseSnapshot || !cashierUrl" @click="openCashier">打开用户收银台</button>
        <button class="button primary" :disabled="isLoading" @click="loadDetail()">{{ isLoading ? "刷新中..." : "刷新" }}</button>
      </div>
    </div>

    <section class="panel">
      <div v-if="loadErrorMessage" class="error-banner">
        {{ hasLoadedData ? "收银台会话详情刷新失败，当前展示上次成功快照：" : "收银台会话详情加载失败：" }}{{ loadErrorMessage }}
      </div>
      <div v-if="isLoading" class="state-box">收银台会话详情加载中...</div>
      <div v-else-if="!hasLoadedData" class="state-box">当前没有可展示的收银台会话详情快照</div>
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
              <h3>会话事实</h3>
              <span class="meta">唯一预付单会话动态投影</span>
            </div>
            <div class="detail-grid detail-grid-wide">
              <div><strong>会话号：</strong>{{ formatValue(detail.sessionNo) }}</div>
              <div><strong>预付单号：</strong>{{ formatValue(detail.prepayOrderNo) }}</div>
              <div><strong>客户：</strong>{{ formatValue(detail.customerName) }}</div>
              <div><strong>收银台标题：</strong>{{ formatValue(detail.cashierTitle) }}</div>
              <div><strong>会话状态：</strong><span :class="['badge', detail.sessionStatusType || 'info']">{{ formatValue(detail.sessionStatus) }}</span></div>
              <div><strong>会话金额：</strong>{{ formatValue(detail.amount) }}</div>
              <div><strong>业务场景：</strong>{{ formatValue(detail.payScene) }}</div>
              <div><strong>最近终端：</strong>{{ formatValue(detail.terminal) }}</div>
              <div><strong>创建时间：</strong>{{ formatValue(detail.createdAt) }}</div>
              <div><strong>失效时间：</strong>{{ formatValue(detail.expiresAt) }}</div>
            </div>
          </section>

          <section class="panel mini">
            <div class="section-title">
              <h3>关联标识</h3>
              <span class="meta">只开放真实存在的标准订单详情</span>
            </div>
            <div class="detail-grid detail-grid-wide">
              <div><strong>业务订单号：</strong>{{ formatValue(detail.orderNo) }}</div>
              <div><strong>标准订单号：</strong>{{ formatValue(detail.standardOrderNo) }}</div>
              <div><strong>账单号：</strong>{{ formatValue(detail.billNo) }}</div>
              <div><strong>支付单号：</strong>{{ formatValue(detail.paymentOrderId) }}</div>
            </div>
            <div class="ops-card detail-panel">
              <div class="ops-row"><span>标准订单详情</span><span>{{ detail.standardOrderNo ? "可联查" : "当前业务号不是标准订单" }}</span></div>
              <div class="ops-row"><span>账单与支付</span><span>{{ detail.billNo && detail.paymentOrderId ? "关联完整" : "存在未关联事实" }}</span></div>
              <div class="ops-row"><span>导航状态</span><span>{{ canUseSnapshot ? "当前快照有效" : "当前快照不可用于联查" }}</span></div>
            </div>
          </section>
        </div>
      </template>
    </section>
  </div>
</template>
