<script setup>
import { onMounted, ref } from "vue";
import { feeApi } from "../api/client";

const rows = ref([]);
const message = ref("");
const loading = ref(false);
const filters = ref({ feeType: "", status: "" });
const form = ref({ feeName: "营销补贴费", feeType: "SUBSIDY", feeMode: "FIXED", feeRate: 0, fixedAmount: 5, feeBearer: "平台" });

async function loadRows() {
  loading.value = true;
  message.value = "";
  try {
    const result = await feeApi.getList({ ...filters.value, pageNo: 1, pageSize: 20 });
    rows.value = result.items;
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function createFee() {
  await feeApi.create(form.value);
  await loadRows();
}

function resetFilters() {
  filters.value = { feeType: "", status: "" };
  loadRows();
}

onMounted(loadRows);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>费用规则</h2>
        <p>维护平台服务费、渠道手续费和补贴配置</p>
      </div>
    </div>
    <section class="panel">
      <div class="toolbar">
        <div class="field"><label>费用类型</label><input v-model="filters.feeType" /></div>
        <div class="field"><label>状态</label><input v-model="filters.status" /></div>
        <div class="toolbar-actions">
          <button class="button primary" @click="loadRows">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>
      <div class="toolbar">
        <div class="field"><label>费用名称</label><input v-model="form.feeName" /></div>
        <div class="field"><label>费用类型</label><input v-model="form.feeType" /></div>
        <div class="field"><label>计费模式</label><input v-model="form.feeMode" /></div>
        <div class="toolbar-actions"><button class="button warn" @click="createFee">新增费用规则</button></div>
      </div>
      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">费用规则加载中...</div>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>规则号</th><th>费用名称</th><th>费用类型</th><th>模式</th><th>费率</th><th>固定金额</th><th>承担方</th><th>状态</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.feeRuleNo">
              <td>{{ row.feeRuleNo }}</td><td>{{ row.feeName }}</td><td>{{ row.feeType }}</td><td>{{ row.feeMode }}</td><td>{{ row.feeRate }}</td><td>{{ row.fixedAmount }}</td><td>{{ row.feeBearer }}</td>
              <td><span class="badge" :class="row.statusType">{{ row.status }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
