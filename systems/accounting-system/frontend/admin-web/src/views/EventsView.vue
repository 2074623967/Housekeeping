<script setup>
import { onMounted, ref } from "vue";
import { eventApi } from "../api/client";

const rows = ref([]);
const loading = ref(false);
const message = ref("");
const filters = ref({ eventType: "", bizNo: "" });
const form = ref({ accountNo: "", paymentOrderId: "", orderNo: "", customerName: "张女士", amount: "18.00", clearingOrderNo: "", bizNo: "", summary: "" });

async function loadRows() {
  loading.value = true;
  try {
    const result = await eventApi.getList({ ...filters.value, pageNo: 1, pageSize: 50 });
    rows.value = result.items;
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function consumePaymentSuccess() {
  await eventApi.consumePaymentSuccess({ ...form.value, amount: Number(form.value.amount) });
  await loadRows();
}

async function consumeClearingGenerated() {
  await eventApi.consumeClearingGenerated({ ...form.value, amount: Number(form.value.amount), summary: form.value.summary || "清分结果入账" });
  await loadRows();
}

onMounted(loadRows);
</script>

<template>
  <div>
    <div class="topbar"><div><h2>账户事件</h2><p>支付成功、清分结果与补偿事件消费</p></div></div>
    <section class="panel">
      <div class="toolbar">
        <div class="field"><label>事件类型</label><input v-model="filters.eventType" /></div>
        <div class="field"><label>业务单号</label><input v-model="filters.bizNo" /></div>
        <div class="toolbar-actions"><button class="button primary" @click="loadRows">查询</button></div>
      </div>
      <div class="toolbar">
        <div class="field"><label>账户号</label><input v-model="form.accountNo" placeholder="支付入账账户" /></div>
        <div class="field"><label>支付单号</label><input v-model="form.paymentOrderId" /></div>
        <div class="field"><label>金额</label><input v-model="form.amount" /></div>
        <div class="toolbar-actions">
          <button class="button warn" @click="consumePaymentSuccess">消费支付成功事件</button>
          <button class="button secondary" @click="consumeClearingGenerated">消费清分结果事件</button>
        </div>
      </div>
      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">事件加载中...</div>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>事件号</th><th>类型</th><th>业务单号</th><th>状态</th><th>摘要</th><th>载荷</th><th>时间</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.eventNo">
              <td>{{ row.eventNo }}</td><td>{{ row.eventType }}</td><td>{{ row.bizNo }}</td><td><span class="badge" :class="row.statusType">{{ row.eventStatus }}</span></td><td>{{ row.summary }}</td><td>{{ row.payload }}</td><td>{{ row.createdAt }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
