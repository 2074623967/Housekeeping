<script setup>
import { onMounted, ref } from "vue";
import { accountApi, adjustmentApi, eventApi, freezeApi, ledgerApi, subjectApi } from "../api/client";

const summary = ref([]);
const loading = ref(true);

async function loadSummary() {
  loading.value = true;
  const [subjects, accounts, ledgers, freezes, adjustments, events] = await Promise.all([
    subjectApi.getList({ pageSize: 1 }),
    accountApi.getList({ pageSize: 1 }),
    ledgerApi.getList({ pageSize: 1 }),
    freezeApi.getList({ pageSize: 1 }),
    adjustmentApi.getList({ pageSize: 1 }),
    eventApi.getList({ pageSize: 1 })
  ]);
  summary.value = [
    { title: "账户主体", value: subjects.total, badge: "主体建档" },
    { title: "账户数量", value: accounts.total, badge: "账户开立" },
    { title: "账户流水", value: ledgers.total, badge: "账务留痕" },
    { title: "冻结 / 调账 / 事件", value: `${freezes.total} / ${adjustments.total} / ${events.total}`, badge: "风险动作" }
  ];
  loading.value = false;
}

onMounted(loadSummary);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>工作台</h2>
        <p>账务、余额、流水和冻结调账的统一入口</p>
      </div>
    </div>
    <section class="panel">
      <div v-if="loading" class="state-box">工作台加载中...</div>
      <div v-else class="card-grid">
        <div v-for="item in summary" :key="item.title" class="card">
          <p class="card-title">{{ item.title }}</p>
          <p class="card-value">{{ item.value }}</p>
          <span class="badge info">{{ item.badge }}</span>
        </div>
      </div>
    </section>
  </div>
</template>
