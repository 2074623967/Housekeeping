<script setup>
import { onMounted, ref } from "vue";
import { ledgerApi } from "../api/client";

const rows = ref([]);
const total = ref(0);
const loading = ref(false);
const filters = ref({ accountNo: "", bizNo: "", bizType: "" });

async function loadRows() {
  loading.value = true;
  const result = await ledgerApi.getList({ ...filters.value, pageNo: 1, pageSize: 50 });
  rows.value = result.items;
  total.value = result.total;
  loading.value = false;
}

onMounted(() => {
  const query = new URLSearchParams(window.location.search);
  filters.value.accountNo = query.get("accountNo") || "";
  loadRows();
});
</script>

<template>
  <div>
    <div class="topbar"><div><h2>账户流水</h2><p>所有入账、出账、冻结、解冻、调账动作留痕</p></div></div>
    <section class="panel">
      <div class="toolbar">
        <div class="field"><label>账户号</label><input v-model="filters.accountNo" /></div>
        <div class="field"><label>业务单号</label><input v-model="filters.bizNo" /></div>
        <div class="field"><label>业务类型</label><input v-model="filters.bizType" /></div>
        <div class="toolbar-actions"><button class="button primary" @click="loadRows">查询</button></div>
      </div>
      <div v-if="loading" class="state-box">流水加载中...</div>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>流水号</th><th>账户号</th><th>业务类型</th><th>业务单号</th><th>方向</th><th>金额</th><th>前余额</th><th>后余额</th><th>状态</th><th>时间</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.ledgerNo">
              <td>{{ row.ledgerNo }}</td><td>{{ row.accountNo }}</td><td>{{ row.bizType }}</td><td>{{ row.bizNo }}</td><td>{{ row.direction }}</td><td>{{ row.amount }}</td><td>{{ row.beforeBalance }}</td><td>{{ row.afterBalance }}</td><td><span class="badge" :class="row.statusType">{{ row.ledgerStatus }}</span></td><td>{{ row.createdAt }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
