<script setup>
import { onMounted, ref } from "vue";
import { eventApi } from "../api/client";

const rows = ref([]);
const message = ref("");
const loading = ref(false);
const filters = ref({ eventType: "", bizNo: "" });
const form = ref({
  clearingNo: "CLO20001",
  paymentOrderId: "PAY202607200001",
  targetType: "WORKER",
  targetNo: "WRK1001",
  targetName: "李阿姨",
  shouldSettleAmount: 120,
  deductAmount: 8,
  netSettleAmount: 112
});

async function loadRows() {
  loading.value = true;
  message.value = "";
  try {
    const result = await eventApi.getList({ ...filters.value, pageNo: 1, pageSize: 20 });
    rows.value = result.items;
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function consumeEvent() {
  await eventApi.consumeClearingGenerated(form.value);
  await loadRows();
}

function resetFilters() {
  filters.value = { eventType: "", bizNo: "" };
  loadRows();
}

onMounted(loadRows);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>结算事件</h2>
        <p>消费清分结果事件并驱动结算单生成</p>
      </div>
    </div>
    <section class="panel">
      <div class="toolbar">
        <div class="field"><label>事件类型</label><input v-model="filters.eventType" /></div>
        <div class="field"><label>业务单号</label><input v-model="filters.bizNo" /></div>
        <div class="toolbar-actions"><button class="button primary" @click="loadRows">查询</button><button class="button secondary" @click="resetFilters">重置</button></div>
      </div>
      <div class="toolbar">
        <div class="field"><label>清分单号</label><input v-model="form.clearingNo" /></div>
        <div class="field"><label>对象编号</label><input v-model="form.targetNo" /></div>
        <div class="field"><label>应结金额</label><input v-model="form.shouldSettleAmount" /></div>
        <div class="toolbar-actions"><button class="button warn" @click="consumeEvent">模拟清分事件</button></div>
      </div>
      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">结算事件加载中...</div>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>事件号</th><th>类型</th><th>业务单号</th><th>摘要</th><th>状态</th><th>创建时间</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.eventNo">
              <td>{{ row.eventNo }}</td><td>{{ row.eventType }}</td><td>{{ row.bizNo }}</td><td>{{ row.summary }}</td><td><span class="badge" :class="row.statusType">{{ row.eventStatus }}</span></td><td>{{ row.createdAt }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
