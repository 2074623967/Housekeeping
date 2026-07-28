<script setup>
import { onMounted, ref } from "vue";
import { walletApi } from "../api/client";

const rows = ref([]);
const loading = ref(false);
const message = ref("");
const filters = ref({
  accountNo: "",
  bizType: "",
  direction: ""
});

async function load() {
  loading.value = true;
  message.value = "";
  try {
    rows.value = await walletApi.getLedgers(filters.value);
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

function reset() {
  filters.value = {
    accountNo: "",
    bizType: "",
    direction: ""
  };
  load();
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div class="panel">
      <h2>钱包流水</h2>
      <div v-if="message" style="margin-bottom:12px;color:#1d4ed8">{{ message }}</div>
      <div class="toolbar">
        <input v-model="filters.accountNo" placeholder="账户号" />
        <input v-model="filters.bizType" placeholder="业务类型，如 BALANCE_PAY" />
        <select v-model="filters.direction">
          <option value="">全部方向</option>
          <option value="IN">收入</option>
          <option value="OUT">支出</option>
        </select>
        <button class="button" style="background:#1d4ed8;color:#fff" @click="load">查询</button>
        <button class="button" style="background:#e2e8f0;color:#0f172a" @click="reset">重置</button>
      </div>
      <div class="table-wrap">
        <div v-if="loading">加载中...</div>
        <table v-else>
          <thead>
            <tr>
              <th>流水号</th>
              <th>账户号</th>
              <th>业务类型</th>
              <th>业务单号</th>
              <th>金额</th>
              <th>方向</th>
              <th>创建时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.ledgerNo">
              <td>{{ row.ledgerNo }}</td>
              <td>{{ row.accountNo }}</td>
              <td>{{ row.bizType }}</td>
              <td>{{ row.bizNo }}</td>
              <td>{{ row.amount }}</td>
              <td><span class="badge">{{ row.direction }}</span></td>
              <td>{{ row.createdAt }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
