<script setup>
import { onMounted, ref } from "vue";
import { payoutApi } from "../api/client";

const rows = ref([]);
const message = ref("");
const loading = ref(false);
const filters = ref({ batchNo: "", payoutStatus: "" });
const form = ref({ batchNo: "SET10001", payoutChannel: "BANK", createdBy: "结算运营" });

async function loadRows() {
  loading.value = true;
  message.value = "";
  try {
    const result = await payoutApi.getList({ ...filters.value, pageNo: 1, pageSize: 20 });
    rows.value = result.items;
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function createPayout() {
  await payoutApi.create(form.value);
  await loadRows();
}

function resetFilters() {
  filters.value = { batchNo: "", payoutStatus: "" };
  loadRows();
}

onMounted(loadRows);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>出款批次</h2>
        <p>发起结算出款和重试失败记录</p>
      </div>
    </div>
    <section class="panel">
      <div class="toolbar">
        <div class="field"><label>批次号</label><input v-model="filters.batchNo" /></div>
        <div class="field"><label>出款状态</label><input v-model="filters.payoutStatus" /></div>
        <div class="toolbar-actions"><button class="button primary" @click="loadRows">查询</button><button class="button secondary" @click="resetFilters">重置</button></div>
      </div>
      <div class="toolbar">
        <div class="field"><label>结算批次号</label><input v-model="form.batchNo" /></div>
        <div class="field"><label>出款渠道</label><input v-model="form.payoutChannel" /></div>
        <div class="field"><label>创建人</label><input v-model="form.createdBy" /></div>
        <div class="toolbar-actions"><button class="button warn" @click="createPayout">发起出款</button></div>
      </div>
      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">出款批次加载中...</div>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>出款批次号</th><th>结算批次号</th><th>渠道</th><th>状态</th><th>笔数</th><th>成功</th><th>失败</th><th>总金额</th><th>创建人</th><th>完成时间</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.payoutBatchNo">
              <td>{{ row.payoutBatchNo }}</td><td>{{ row.batchNo }}</td><td>{{ row.payoutChannel }}</td><td><span class="badge" :class="row.payoutStatusType">{{ row.payoutStatus }}</span></td>
              <td>{{ row.payoutCount }}</td><td>{{ row.successCount }}</td><td>{{ row.failedCount }}</td><td>{{ row.totalAmount }}</td><td>{{ row.createdBy }}</td><td>{{ row.finishedAt || "-" }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
