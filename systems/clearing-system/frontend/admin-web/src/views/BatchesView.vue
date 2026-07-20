<script setup>
import { onMounted, ref } from "vue";
import { batchApi } from "../api/client";

const rows = ref([]);
const loading = ref(false);
const message = ref("");
const filters = ref({ batchDate: "", batchStatus: "" });
const form = ref({ batchDate: "2026-07-20", sourceType: "MANUAL", createdBy: "清分运营", idempotencyKey: "" });

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
  await batchApi.create({ ...form.value, idempotencyKey: form.value.idempotencyKey || `BATCH-${Date.now()}` });
  await loadRows();
}

async function rerunBatch(batchNo) {
  await batchApi.rerun(batchNo, { operatorName: "清分运营", reason: "人工补偿重跑" });
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
        <h2>清分批次</h2>
        <p>管理清分任务发起、批次状态和重跑补偿</p>
      </div>
    </div>
    <section class="panel">
      <div class="toolbar">
        <div class="field"><label>批次日期</label><input v-model="filters.batchDate" placeholder="2026-07-20" /></div>
        <div class="field"><label>批次状态</label><input v-model="filters.batchStatus" placeholder="处理中 / 已完成" /></div>
        <div class="toolbar-actions">
          <button class="button primary" @click="loadRows">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>
      <div class="toolbar">
        <div class="field"><label>批次日期</label><input v-model="form.batchDate" /></div>
        <div class="field"><label>来源类型</label><input v-model="form.sourceType" /></div>
        <div class="field"><label>创建人</label><input v-model="form.createdBy" /></div>
        <div class="toolbar-actions"><button class="button warn" @click="createBatch">发起清分</button></div>
      </div>
      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">清分批次加载中...</div>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>批次号</th><th>批次日期</th><th>来源</th><th>状态</th><th>订单数</th><th>总金额</th><th>版本</th><th>创建人</th><th>完成时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.batchNo">
              <td>{{ row.batchNo }}</td><td>{{ row.batchDate }}</td><td>{{ row.sourceType }}</td>
              <td><span class="badge" :class="row.batchStatusType">{{ row.batchStatus }}</span></td>
              <td>{{ row.totalOrderCount }}</td><td>{{ row.totalAmount }}</td><td>{{ row.versionNo }}</td><td>{{ row.createdBy }}</td><td>{{ row.finishedAt || "-" }}</td>
              <td><button class="button secondary" @click="rerunBatch(row.batchNo)">重跑</button></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
