<script setup>
import { ref } from "vue";
import { useRoute } from "vue-router";
import { paymentApi } from "../api/client";

const route = useRoute();
const status = ref(route.params.status);
const message = ref("");
const detail = ref(null);

async function queryResult() {
  try {
    detail.value = await paymentApi.query({ paymentOrderId: route.query.paymentOrderId });
    status.value = detail.value?.status === "SUCCESS" ? "success" : detail.value?.status === "CLOSED" ? "closed" : "pending";
    message.value = `已查询支付单 ${route.query.paymentOrderId} 的最新状态。`;
  } catch (error) {
    message.value = error.message;
  }
}

async function mockSuccessCallback() {
  try {
    detail.value = await paymentApi.callback("wx_jsapi", {
      paymentOrderId: route.query.paymentOrderId,
      channelTransactionNo: `SIM${Date.now()}`,
      tradeStatus: "SUCCESS"
    });
    status.value = "success";
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
      <h1>{{ status === "success" ? "支付成功" : status === "closed" ? "支付已关闭" : "支付处理中" }}</h1>
      <p class="muted">可继续查单、模拟回调或回到后台查看支付详情。</p>
      <div class="result-meta">
        <div>支付单号：{{ route.query.paymentOrderId || "-" }}</div>
        <div>预付单号：{{ route.query.prepayOrderNo || "-" }}</div>
        <div>支付方式：{{ route.query.paymentMethod || "-" }}</div>
      </div>
      <div class="result-actions">
        <button class="button secondary" @click="queryResult">查询最新状态</button>
        <button class="button primary" @click="mockSuccessCallback">模拟成功回调</button>
      </div>
      <div v-if="message" class="result-message">{{ message }}</div>
      <div v-if="detail" class="result-detail">
        <div><strong>当前状态：</strong>{{ detail.status }}</div>
        <div><strong>订单号：</strong>{{ detail.orderNo }}</div>
        <div><strong>金额：</strong>{{ detail.amount }}</div>
        <div><strong>渠道：</strong>{{ detail.channel }}</div>
      </div>
    </div>
  </div>
</template>
