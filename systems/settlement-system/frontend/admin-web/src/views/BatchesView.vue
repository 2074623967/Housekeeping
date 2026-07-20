<script setup>
import { onMounted, ref } from "vue";
import { batchApi } from "../api/client";

const rows = ref([]);
const message = ref("");
const loading = ref(false);
const filters = ref({ batchDate: "", batchStatus: "" });
const form = ref({ batchDate: "2026-07-20", settlementType: "MANUAL", createdBy: "结算运营", idempotencyKey: "" });

async function loadRows() {
  loading.value = true;
  message.value = "";
  try {
    const result = await batchApi.getList({ ...filters.value, pageNo: 1, pageSize: 20 });
    rows.value = result.items;
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function createBatch() {
  await batchApi.create({ ...form.value, idempotencyKey: form.value.idempotencyKey || `SET-${Date.now()}` });
  await loadRows();
}

function resetFilters() {
  filters.value = { batchDate: "", batchStatus: "" };
  loadRows();
}

onMounted(loadRows);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>结算批次</h2>
        <p>管理结算任务发起、批次状态和处理进度</p>
      </div>
    </div>
    <section class="panel">
      <div class="toolbar">
        <div class="field"><label>批次日期</label><input v-model="filters.batchDate" /></div>
        <div class="field"><label>批次状态</label><input v-model="filters.batchStatus" /></div>
        <div class="toolbar-actions"><button class="button primary" @click="loadRows">查询</button><button class="button secondary" @click="resetFilters">重置</button></div>
      </div>
      <div class="toolbar">
        <div class="field"><label>批次日期</label><input v-model="form.batchDate" /></div>
        <div class="field"><label>结算类型</label><input v-model="form.settlementType" /></div>
        <div class="field"><label>创建人</label><input v-model="form.createdBy" /></div>
        <div class="toolbar-actions"><button class="button warn" @click="createBatch">发起结算</button></div>
      </div>
      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">结算批次加载中...</div>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>批次号</th><th>日期</th><th>类型</th><th>状态</th><th>总数</th><th>总金额</th><th>审核数</th><th>出款数</th><th>创建人</th><th>完成时间</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.batchNo">
              <td>{{ row.batchNo }}</td><td>{{ row.batchDate }}</td><td>{{ row.settlementType }}</td><td><span class="badge" :class="row.batchStatusType">{{ row.batchStatus }}</span></td>
              <td>{{ row.totalCount }}</td><td>{{ row.totalAmount }}</td><td>{{ row.auditedCount }}</td><td>{{ row.payoutCount }}</td><td>{{ row.createdBy }}</td><td>{{ row.finishedAt || "-" }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
