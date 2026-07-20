<script setup>
import { onMounted, ref } from "vue";
import { batchApi, feeApi, orderApi, ruleApi } from "../api/client";

const metrics = ref({
  batchCount: 0,
  orderCount: 0,
  activeRuleCount: 0,
  feeRuleCount: 0
});
const message = ref("");

async function loadDashboard() {
  message.value = "";
  try {
    const [batchResult, orderResult, ruleResult, feeResult] = await Promise.all([
      batchApi.getList({ pageNo: 1, pageSize: 50 }),
      orderApi.getList({ pageNo: 1, pageSize: 50 }),
      ruleApi.getList({ pageNo: 1, pageSize: 50 }),
      feeApi.getList({ pageNo: 1, pageSize: 50 })
    ]);
    metrics.value = {
      batchCount: batchResult.total,
      orderCount: orderResult.total,
      activeRuleCount: ruleResult.items.filter((item) => item.ruleStatus === "启用").length,
      feeRuleCount: feeResult.total
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
        <h2>清分工作台</h2>
        <p>聚焦清分批次、分账结果和规则生效状态</p>
      </div>
    </div>

    <div v-if="message" class="state-box">{{ message }}</div>

    <section v-else class="card-grid">
      <article class="card">
        <p class="card-title">清分批次数</p>
        <p class="card-value">{{ metrics.batchCount }}</p>
      </article>
      <article class="card">
        <p class="card-title">清分结果数</p>
        <p class="card-value">{{ metrics.orderCount }}</p>
      </article>
      <article class="card">
        <p class="card-title">启用规则数</p>
        <p class="card-value">{{ metrics.activeRuleCount }}</p>
      </article>
      <article class="card">
        <p class="card-title">费用规则数</p>
        <p class="card-value">{{ metrics.feeRuleCount }}</p>
      </article>
    </section>
  </div>
</template>
