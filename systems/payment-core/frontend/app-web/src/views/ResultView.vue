<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { paymentApi } from "../api/client";

const route = useRoute();
const status = ref("pending");
const message = ref("");
const detail = ref(null);
const loading = ref(true);

const resultTitle = computed(() => {
  if (status.value === "success") {
    return "支付成功";
  }
  if (status.value === "closed") {
    return "支付已关闭";
  }
  return "支付处理中";
});

const resultHint = computed(() => {
  if (status.value === "success") {
    return "支付结果已经收口，可返回订单或继续查看支付详情。";
  }
  if (status.value === "closed") {
    return "当前支付单已关闭，如仍需支付请重新发起。";
  }
  return "渠道回调可能稍有延迟，可主动查单或模拟回调完成联调。";
});

function syncStatusByDetail() {
  if (!detail.value) {
    status.value = "pending";
    return;
  }
  status.value = detail.value.status === "SUCCESS"
    ? "success"
    : detail.value.status === "CLOSED"
      ? "closed"
      : "pending";
}

async function loadDetail() {
  loading.value = true;
  try {
    detail.value = await paymentApi.getDetail(route.params.paymentOrderId);
    syncStatusByDetail();
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

onMounted(loadDetail);

async function queryResult() {
  try {
    detail.value = await paymentApi.query({ paymentOrderId: route.params.paymentOrderId });
    syncStatusByDetail();
    message.value = `已查询支付单 ${route.params.paymentOrderId} 的最新状态。`;
  } catch (error) {
    message.value = error.message;
  }
}

async function mockSuccessCallback() {
  try {
    detail.value = await paymentApi.callback("WX_H5", {
      paymentOrderId: route.params.paymentOrderId,
      channelTransactionNo: `SIM${Date.now()}`,
      tradeStatus: "SUCCESS"
    });
    syncStatusByDetail();
    message.value = "已模拟成功回调并完成状态收口。";
  } catch (error) {
    message.value = error.message;
  }
}
</script>

<template>
  <div class="page">
    <div class="result">
      <div class="result-icon">{{ status === "success" ? "✓" : status === "closed" ? "!" : "…" }}</div>
      <h1>{{ resultTitle }}</h1>
      <p class="muted">{{ resultHint }}</p>
      <div class="result-meta">
        <div>支付单号：{{ route.params.paymentOrderId || "-" }}</div>
        <div>预付单号：{{ route.query.prepayOrderNo || "-" }}</div>
        <div>支付方式：{{ route.query.paymentMethod || "-" }}</div>
      </div>
      <div class="result-actions">
        <button class="button secondary" @click="queryResult">查询最新状态</button>
        <button class="button primary" @click="mockSuccessCallback">模拟成功回调</button>
      </div>
      <div v-if="message" class="result-message">{{ message }}</div>
      <div v-if="loading" class="result-message">支付结果加载中...</div>
      <div v-if="detail" class="result-detail">
        <div><strong>当前状态：</strong>{{ detail.status }}</div>
        <div><strong>订单号：</strong>{{ detail.orderNo }}</div>
        <div><strong>金额：</strong>{{ detail.amount }}</div>
        <div><strong>渠道：</strong>{{ detail.channel }}</div>
      </div>
    </div>
  </div>
</template>
