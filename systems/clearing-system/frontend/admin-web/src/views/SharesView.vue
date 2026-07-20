<script setup>
import { onMounted, ref } from "vue";
import { shareApi } from "../api/client";

const rows = ref([]);
const message = ref("");
const loading = ref(false);
const filters = ref({ clearingNo: "", shareType: "" });

async function loadRows() {
  loading.value = true;
  message.value = "";
  try {
    const result = await shareApi.getList({ ...filters.value, pageNo: 1, pageSize: 20 });
    rows.value = result.items;
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

function resetFilters() {
  filters.value = { clearingNo: "", shareType: "" };
  loadRows();
}

onMounted(loadRows);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>分账明细</h2>
        <p>按收款方追踪分账金额与待结算状态</p>
      </div>
    </div>
    <section class="panel">
      <div class="toolbar">
        <div class="field"><label>清分单号</label><input v-model="filters.clearingNo" /></div>
        <div class="field"><label>分账类型</label><input v-model="filters.shareType" /></div>
        <div class="toolbar-actions">
          <button class="button primary" @click="loadRows">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>
      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">分账明细加载中...</div>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>分账明细号</th><th>清分单号</th><th>类型</th><th>对象编号</th><th>对象名称</th><th>金额</th><th>状态</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.shareItemNo">
              <td>{{ row.shareItemNo }}</td><td>{{ row.clearingNo }}</td><td>{{ row.shareType }}</td><td>{{ row.shareTargetNo }}</td><td>{{ row.shareTargetName }}</td><td>{{ row.shareAmount }}</td>
              <td><span class="badge" :class="row.statusType">{{ row.shareStatus }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
