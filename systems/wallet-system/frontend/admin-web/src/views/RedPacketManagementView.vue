<script setup>
import { onMounted, ref } from "vue";
import { walletApi } from "../api/client";

const rows = ref([]);
const loading = ref(false);
const message = ref("");
const form = ref({
  accountNo: "WALLET-20001",
  campaignName: "家政暑期拉新红包",
  totalAmount: "200.00",
  packetCount: "20",
  operatorName: "营销运营小李"
});

async function load() {
  loading.value = true;
  message.value = "";
  try {
    rows.value = await walletApi.getRedPackets();
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function issueRedPacket() {
  message.value = "";
  try {
    await walletApi.issueRedPacket({
      ...form.value,
      totalAmount: Number(form.value.totalAmount),
      packetCount: Number(form.value.packetCount)
    });
    await load();
    message.value = "红包发放成功";
  } catch (error) {
    message.value = error.message;
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <div class="panel">
      <h2>红包管理</h2>
      <div v-if="message" style="margin-bottom:12px;color:#1d4ed8">{{ message }}</div>
      <div class="layout">
        <div class="table-wrap">
          <div v-if="loading">加载中...</div>
          <table v-else>
            <thead>
              <tr>
                <th>批次号</th>
                <th>出资账户</th>
                <th>活动名称</th>
                <th>总金额</th>
                <th>个数</th>
                <th>状态</th>
                <th>创建时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in rows" :key="row.redPacketNo">
                <td>{{ row.redPacketNo }}</td>
                <td>{{ row.accountNo }}</td>
                <td>{{ row.campaignName }}</td>
                <td>{{ row.totalAmount }}</td>
                <td>{{ row.packetCount }}</td>
                <td><span class="badge">{{ row.status }}</span></td>
                <td>{{ row.createdAt }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="detail-grid">
          <div class="detail-card">
            <div class="detail-label">红包池发放</div>
            <div style="display:grid;gap:8px;margin-top:8px">
              <input v-model="form.accountNo" placeholder="出资钱包账户号" />
              <input v-model="form.campaignName" placeholder="活动名称" />
              <input v-model="form.totalAmount" placeholder="红包总金额" />
              <input v-model="form.packetCount" placeholder="红包个数" />
              <input v-model="form.operatorName" placeholder="操作人" />
              <button class="button" style="background:#dc2626;color:#fff" @click="issueRedPacket">发放红包池</button>
            </div>
          </div>
          <div class="detail-card">
            <div class="detail-label">发放规则</div>
            <div style="margin-top:8px;display:grid;gap:6px;color:#334155">
              <div>1. 红包总金额必须大于 0</div>
              <div>2. 红包个数必须大于 0</div>
              <div>3. 出资账户可用余额必须覆盖总金额</div>
              <div>4. 发放成功后自动生成营销资金扣减流水</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
