<script setup>
import { onMounted, ref } from "vue";
import { freezeApi } from "../api/client";

const rows = ref([]);
const loading = ref(false);
const message = ref("");
const filters = ref({ accountNo: "", freezeStatus: "" });
const form = ref({ accountNo: "", bizNo: "", freezeType: "人工冻结", freezeReason: "风险确认", operatorName: "运营小王", freezeAmount: "10.00" });

async function loadRows() {
  loading.value = true;
  try {
    const result = await freezeApi.getList({ ...filters.value, pageNo: 1, pageSize: 50 });
    rows.value = result.items;
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function createFreeze() {
  await freezeApi.create({ ...form.value, freezeAmount: Number(form.value.freezeAmount) });
  await loadRows();
}

async function unfreeze(freezeNo) {
  await freezeApi.unfreeze(freezeNo, { operatorName: "运营小王", unfreezeReason: "审核通过" });
  await loadRows();
}

onMounted(loadRows);
</script>

<template>
  <div>
    <div class="topbar"><div><h2>冻结单</h2><p>冻结、解冻与原因留痕</p></div></div>
    <section class="panel">
      <div class="toolbar">
        <div class="field"><label>账户号</label><input v-model="filters.accountNo" /></div>
        <div class="field"><label>冻结状态</label><input v-model="filters.freezeStatus" /></div>
        <div class="toolbar-actions"><button class="button primary" @click="loadRows">查询</button></div>
      </div>
      <div class="toolbar">
        <div class="field"><label>账户号</label><input v-model="form.accountNo" /></div>
        <div class="field"><label>业务单号</label><input v-model="form.bizNo" /></div>
        <div class="field"><label>金额</label><input v-model="form.freezeAmount" /></div>
        <div class="toolbar-actions"><button class="button warn" @click="createFreeze">创建冻结单</button></div>
      </div>
      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">冻结单加载中...</div>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>冻结单号</th><th>账户号</th><th>业务单号</th><th>类型</th><th>原因</th><th>金额</th><th>状态</th><th>操作人</th><th>时间</th><th>解冻时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.freezeNo">
              <td>{{ row.freezeNo }}</td><td>{{ row.accountNo }}</td><td>{{ row.bizNo }}</td><td>{{ row.freezeType }}</td><td>{{ row.freezeReason }}</td><td>{{ row.freezeAmount }}</td><td><span class="badge" :class="row.statusType">{{ row.freezeStatus }}</span></td><td>{{ row.operatorName }}</td><td>{{ row.createdAt }}</td><td>{{ row.unfrozenAt || '-' }}</td><td><button class="button secondary" @click="unfreeze(row.freezeNo)">解冻</button></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
