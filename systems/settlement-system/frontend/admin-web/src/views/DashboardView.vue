<script setup>
import { onMounted, ref } from "vue";
import { batchApi, eventApi, orderApi, payoutApi } from "../api/client";

const metrics = ref({ batchCount: 0, orderCount: 0, payoutCount: 0, eventCount: 0 });
const message = ref("");

async function loadDashboard() {
  message.value = "";
  try {
    const [batchResult, orderResult, payoutResult, eventResult] = await Promise.all([
      batchApi.getList({ pageNo: 1, pageSize: 50 }),
      orderApi.getList({ pageNo: 1, pageSize: 50 }),
      payoutApi.getList({ pageNo: 1, pageSize: 50 }),
      eventApi.getList({ pageNo: 1, pageSize: 50 })
    ]);
    metrics.value = {
      batchCount: batchResult.total,
      orderCount: orderResult.total,
      payoutCount: payoutResult.total,
      eventCount: eventResult.total
    };
  } catch (error) {
    message.value = error.message;
  }
}

onMounted(loadDashboard);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>结算工作台</h2>
        <p>聚焦结算批次、结算单、出款与事件消费状态</p>
      </div>
    </div>
    <div v-if="message" class="state-box">{{ message }}</div>
    <section v-else class="card-grid">
      <article class="card"><p class="card-title">结算批次数</p><p class="card-value">{{ metrics.batchCount }}</p></article>
      <article class="card"><p class="card-title">结算单数</p><p class="card-value">{{ metrics.orderCount }}</p></article>
      <article class="card"><p class="card-title">出款批次数</p><p class="card-value">{{ metrics.payoutCount }}</p></article>
      <article class="card"><p class="card-title">结算事件数</p><p class="card-value">{{ metrics.eventCount }}</p></article>
    </section>
  </div>
</template>
