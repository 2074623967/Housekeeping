<script setup>
import { onMounted, ref } from "vue";
import { walletApi } from "../api/client";

const rows = ref([]);
const detail = ref(null);
const loading = ref(false);

async function load() {
  loading.value = true;
  rows.value = await walletApi.getAccounts();
  detail.value = rows.value[0] ? await walletApi.getDetail(rows.value[0].accountNo) : null;
  loading.value = false;
}

async function openDetail(accountNo) {
  detail.value = await walletApi.getDetail(accountNo);
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div class="panel">
      <h2>钱包账户</h2>
      <div v-if="loading">加载中...</div>
      <div v-else class="layout">
        <div class="table-wrap">
          <table>
            <thead>
              <tr><th>账户号</th><th>用户</th><th>类型</th><th>状态</th><th>可用</th><th>冻结</th></tr>
            </thead>
            <tbody>
              <tr v-for="row in rows" :key="row.accountNo" @click="openDetail(row.accountNo)" style="cursor:pointer">
                <td>{{ row.accountNo }}</td>
                <td>{{ row.ownerName }}</td>
                <td>{{ row.walletType }}</td>
                <td><span class="badge">{{ row.status }}</span></td>
                <td>{{ row.availableAmount }}</td>
                <td>{{ row.frozenAmount }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="detail-grid" v-if="detail">
          <div class="detail-card"><div class="detail-label">账户号</div><div class="detail-value">{{ detail.account.accountNo }}</div></div>
          <div class="detail-card"><div class="detail-label">用户</div><div class="detail-value">{{ detail.account.ownerName }}</div></div>
          <div class="detail-card"><div class="detail-label">可用余额</div><div class="detail-value">{{ detail.account.availableAmount }}</div></div>
          <div class="detail-card"><div class="detail-label">最近流水数</div><div class="detail-value">{{ detail.ledgers.length }}</div></div>
        </div>
      </div>
    </div>
  </div>
</template>
