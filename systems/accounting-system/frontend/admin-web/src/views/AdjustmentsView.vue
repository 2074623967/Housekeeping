<script setup>
import { onMounted, ref } from "vue";
import { adjustmentApi } from "../api/client";

const rows = ref([]);
const loading = ref(false);
const message = ref("");
const filters = ref({ accountNo: "", adjustStatus: "" });
const form = ref({ accountNo: "", adjustDirection: "贷方", adjustAmount: "8.00", adjustReason: "系统补偿", createdBy: "财务小李" });

async function loadRows() {
  loading.value = true;
  try {
    const result = await adjustmentApi.getList({ ...filters.value, pageNo: 1, pageSize: 50 });
    rows.value = result.items;
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function createAdjustment() {
  await adjustmentApi.create({ ...form.value, adjustAmount: Number(form.value.adjustAmount) });
  await loadRows();
}

async function approve(adjustNo) {
  await adjustmentApi.approve(adjustNo, { approvedBy: "财务主管" });
  await loadRows();
}

onMounted(loadRows);
</script>

<template>
  <div>
    <div class="topbar"><div><h2>调账单</h2><p>人工修正与系统补偿</p></div></div>
    <section class="panel">
      <div class="toolbar">
        <div class="field"><label>账户号</label><input v-model="filters.accountNo" /></div>
        <div class="field"><label>调账状态</label><input v-model="filters.adjustStatus" /></div>
        <div class="toolbar-actions"><button class="button primary" @click="loadRows">查询</button></div>
      </div>
      <div class="toolbar">
        <div class="field"><label>账户号</label><input v-model="form.accountNo" /></div>
        <div class="field"><label>方向</label><input v-model="form.adjustDirection" /></div>
        <div class="field"><label>金额</label><input v-model="form.adjustAmount" /></div>
        <div class="toolbar-actions"><button class="button warn" @click="createAdjustment">创建调账单</button></div>
      </div>
      <div v-if="message" class="state-box">{{ message }}</div>
      <div v-else-if="loading" class="state-box">调账单加载中...</div>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>调账单号</th><th>账户号</th><th>方向</th><th>金额</th><th>原因</th><th>状态</th><th>创建人</th><th>审批人</th><th>时间</th><th>审批时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.adjustNo">
              <td>{{ row.adjustNo }}</td><td>{{ row.accountNo }}</td><td>{{ row.adjustDirection }}</td><td>{{ row.adjustAmount }}</td><td>{{ row.adjustReason }}</td><td><span class="badge" :class="row.statusType">{{ row.adjustStatus }}</span></td><td>{{ row.createdBy }}</td><td>{{ row.approvedBy || '-' }}</td><td>{{ row.createdAt }}</td><td>{{ row.approvedAt || '-' }}</td><td><button class="button secondary" @click="approve(row.adjustNo)">审批</button></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
