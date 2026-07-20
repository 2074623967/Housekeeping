<script setup>
import { onMounted, ref } from "vue";
import { orderApi } from "../api/client";

const rows = ref([]);
const message = ref("");
const loading = ref(false);
const filters = ref({ batchNo: "", orderNo: "", clearingStatus: "" });

async function loadRows() {
  loading.value = true;
  message.value = "";
  try {
    const result = await orderApi.getList({ ...filters.value, pageNo: 1, pageSize: 20 });
    rows.value = result.items;
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

function resetFilters() {
  filters.value = { batchNo: "", orderNo: "", clearingStatus: "" };
  loadRows();
}

onMounted(loadRows);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>清分结果</h2>
        <p>查看每笔支付单的应分、实分和费用拆解</p>
      </div>
    </div>
    <section class="panel">
      <div class="toolbar">
        <div class="field"><label>批次号</label><input v-model="filters.batchNo" /></div>
        <div class="field"><label>订单号</label><input v-model="filters.orderNo" /></div>
        <div class="field"><label>清分状态</label><input v-model="filters.clearingStatus" /></div>
        <div class="toolbar-actions">
          <button class="button primary" @click="loadRows">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>
      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">清分结果加载中...</div>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>清分单号</th><th>支付单号</th><th>订单号</th><th>订单金额</th><th>商家</th><th>服务者</th><th>平台</th><th>渠道费</th><th>状态</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.clearingNo">
              <td>{{ row.clearingNo }}</td><td>{{ row.paymentOrderId }}</td><td>{{ row.orderNo }}</td><td>{{ row.orderAmount }}</td><td>{{ row.merchantAmount }}</td><td>{{ row.workerAmount }}</td><td>{{ row.platformAmount }}</td><td>{{ row.channelFeeAmount }}</td>
              <td><span class="badge" :class="row.clearingStatusType">{{ row.clearingStatus }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
