<script setup>
import { onMounted, ref } from "vue";
import { eventApi } from "../api/client";

const rows = ref([]);
const message = ref("");
const loading = ref(false);
const filters = ref({ eventType: "", bizNo: "" });
const form = ref({
  paymentOrderId: "PAY202607200188",
  orderNo: "ORD202607200188",
  batchDate: "2026-07-20",
  customerName: "孙女士",
  merchantName: "徐汇门店",
  workerName: "陈阿姨",
  amount: 188
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
  await eventApi.consumePaymentSuccess(form.value);
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
        <h2>清分事件</h2>
        <p>消费支付成功事件并驱动清分批次、结果与分账明细生成</p>
      </div>
    </div>
    <section class="panel">
      <div class="toolbar">
        <div class="field"><label>事件类型</label><input v-model="filters.eventType" /></div>
        <div class="field"><label>业务单号</label><input v-model="filters.bizNo" /></div>
        <div class="toolbar-actions">
          <button class="button primary" @click="loadRows">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>
      <div class="toolbar">
        <div class="field"><label>支付单号</label><input v-model="form.paymentOrderId" /></div>
        <div class="field"><label>订单号</label><input v-model="form.orderNo" /></div>
        <div class="field"><label>订单金额</label><input v-model="form.amount" /></div>
        <div class="toolbar-actions"><button class="button warn" @click="consumeEvent">模拟支付成功事件</button></div>
      </div>
      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">清分事件加载中...</div>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>事件号</th><th>类型</th><th>业务单号</th><th>摘要</th><th>状态</th><th>创建时间</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.eventNo">
              <td>{{ row.eventNo }}</td><td>{{ row.eventType }}</td><td>{{ row.bizNo }}</td><td>{{ row.summary }}</td>
              <td><span class="badge" :class="row.statusType">{{ row.eventStatus }}</span></td><td>{{ row.createdAt }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
