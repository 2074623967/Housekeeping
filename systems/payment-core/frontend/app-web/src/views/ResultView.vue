<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { paymentApi } from "../api/client";
import { PAYMENT_RESULT_STATE_META, resolvePaymentResultState } from "../constants/payment";

const route = useRoute();
const resultState = ref("pending");
const feedbackMessage = ref("");
const paymentDetail = ref(null);
const detailLoading = ref(true);
const queryLoading = ref(false);
const callbackLoading = ref(false);
const lastAction = ref("");

const resultTitle = computed(() => PAYMENT_RESULT_STATE_META[resultState.value].title);
const resultHint = computed(() => PAYMENT_RESULT_STATE_META[resultState.value].hint);

function syncStatusByDetail() {
  if (!paymentDetail.value) {
    resultState.value = "pending";
    return;
  }
  resultState.value = resolvePaymentResultState(paymentDetail.value.status);
}

async function loadDetail() {
  detailLoading.value = true;
  feedbackMessage.value = "";
  try {
    paymentDetail.value = await paymentApi.getDetail(route.params.paymentOrderId);
    syncStatusByDetail();
  } catch (error) {
    feedbackMessage.value = error.message;
  } finally {
    detailLoading.value = false;
  }
}

onMounted(loadDetail);

async function queryResult() {
  queryLoading.value = true;
  try {
    paymentDetail.value = await paymentApi.query({ paymentOrderId: route.params.paymentOrderId });
    syncStatusByDetail();
    feedbackMessage.value = `已查询支付单 ${route.params.paymentOrderId} 的最新状态。`;
    lastAction.value = "query";
  } catch (error) {
    feedbackMessage.value = error.message;
  } finally {
    queryLoading.value = false;
  }
}

async function mockSuccessCallback() {
  callbackLoading.value = true;
  try {
    paymentDetail.value = await paymentApi.callback("WX_H5", {
      paymentOrderId: route.params.paymentOrderId,
      channelTransactionNo: `SIM${Date.now()}`,
      tradeStatus: "SUCCESS"
    });
    syncStatusByDetail();
    feedbackMessage.value = "已模拟成功回调并完成状态收口。";
    lastAction.value = "callback";
  } catch (error) {
    feedbackMessage.value = error.message;
  } finally {
    callbackLoading.value = false;
  }
}
</script>

<template>
  <div class="page">
    <div class="result">
      <div class="result-icon">{{ resultState === "success" ? "✓" : resultState === "closed" ? "!" : "…" }}</div>
      <h1>{{ resultTitle }}</h1>
      <p class="muted">{{ resultHint }}</p>
      <div class="result-meta">
        <div>支付单号：{{ route.params.paymentOrderId || "-" }}</div>
        <div>预付单号：{{ route.query.prepayOrderNo || "-" }}</div>
        <div>支付方式：{{ route.query.paymentMethod || "-" }}</div>
      </div>
      <div class="result-actions">
        <button class="button secondary" :disabled="queryLoading" @click="queryResult">
          {{ queryLoading ? "查询中..." : "查询最新状态" }}
        </button>
        <button class="button primary" :disabled="callbackLoading" @click="mockSuccessCallback">
          {{ callbackLoading ? "回调中..." : "模拟成功回调" }}
        </button>
      </div>
      <div v-if="feedbackMessage" class="result-message">{{ feedbackMessage }}</div>
      <div v-if="detailLoading" class="result-message">支付结果加载中...</div>
      <div v-if="paymentDetail" class="result-detail">
        <div><strong>当前状态：</strong>{{ paymentDetail.status }}</div>
        <div><strong>订单号：</strong>{{ paymentDetail.orderNo }}</div>
        <div><strong>金额：</strong>{{ paymentDetail.amount }}</div>
        <div><strong>渠道：</strong>{{ paymentDetail.channel }}</div>
        <div><strong>最近动作：</strong>{{ lastAction || "-" }}</div>
        <div><strong>查单来源：</strong>{{ paymentDetail.querySource || "-" }}</div>
        <div><strong>支付方式：</strong>{{ paymentDetail.paymentMethod || "-" }}</div>
        <div><strong>渠道流水号：</strong>{{ paymentDetail.channelTransactionNo || "-" }}</div>
      </div>
      <div v-if="paymentDetail" class="timeline-panel">
        <div class="timeline-section">
          <h3>路由轨迹</h3>
          <div v-for="item in paymentDetail.routeLogs || []" :key="item" class="timeline-item">{{ item }}</div>
        </div>
        <div class="timeline-section">
          <h3>回调轨迹</h3>
          <div v-for="item in paymentDetail.notifyLogs || []" :key="item" class="timeline-item">{{ item }}</div>
        </div>
        <div class="timeline-section">
          <h3>事件轨迹</h3>
          <div v-for="item in paymentDetail.eventLogs || []" :key="item" class="timeline-item">{{ item }}</div>
        </div>
      </div>
    </div>
  </div>
</template>
