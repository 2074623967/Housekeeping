<script setup>
import { onMounted, ref } from "vue";
import { orderApi } from "../api/client";

const rows = ref([]);
const message = ref("");
const loading = ref(false);
const filters = ref({ batchNo: "", targetType: "", settlementStatus: "" });

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
  filters.value = { batchNo: "", targetType: "", settlementStatus: "" };
  loadRows();
}

onMounted(loadRows);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>结算单</h2>
        <p>查看应结、扣减、实结、审核和出款状态</p>
      </div>
    </div>
    <section class="panel">
      <div class="toolbar">
        <div class="field"><label>批次号</label><input v-model="filters.batchNo" /></div>
        <div class="field"><label>对象类型</label><input v-model="filters.targetType" /></div>
        <div class="field"><label>结算状态</label><input v-model="filters.settlementStatus" /></div>
        <div class="toolbar-actions"><button class="button primary" @click="loadRows">查询</button><button class="button secondary" @click="resetFilters">重置</button></div>
      </div>
      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">结算单加载中...</div>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>结算单号</th><th>批次号</th><th>对象类型</th><th>对象编号</th><th>对象名称</th><th>应结</th><th>扣减</th><th>实结</th><th>结算状态</th><th>审核状态</th><th>出款状态</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.settlementNo">
              <td>{{ row.settlementNo }}</td><td>{{ row.batchNo }}</td><td>{{ row.targetType }}</td><td>{{ row.targetNo }}</td><td>{{ row.targetName }}</td><td>{{ row.shouldSettleAmount }}</td><td>{{ row.deductAmount }}</td><td>{{ row.netSettleAmount }}</td>
              <td><span class="badge" :class="row.settlementStatusType">{{ row.settlementStatus }}</span></td><td>{{ row.auditStatus }}</td><td><span class="badge" :class="row.payoutStatusType">{{ row.payoutStatus }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
