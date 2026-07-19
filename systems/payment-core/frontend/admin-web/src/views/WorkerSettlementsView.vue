<script setup>
import { onMounted, ref } from "vue";
import { settlementApi } from "../api/client";

const items = ref([]);
const isLoading = ref(true);
const errorMessage = ref("");
const filters = ref({
  settlementOrderId: "",
  workerKeyword: "",
  settlementStatus: "全部",
  payoutStatus: "全部"
});

function resetFilters() {
  filters.value = {
    settlementOrderId: "",
    workerKeyword: "",
    settlementStatus: "全部",
    payoutStatus: "全部"
  };
  loadWorkerSettlements();
}

function applyFilters() {
  loadWorkerSettlements();
}

async function loadWorkerSettlements() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    items.value = await settlementApi.getWorkerList({
      settlementOrderId: filters.value.settlementOrderId,
      workerKeyword: filters.value.workerKeyword,
      settlementStatus: filters.value.settlementStatus,
      payoutStatus: filters.value.payoutStatus
    });
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    isLoading.value = false;
  }
}

onMounted(loadWorkerSettlements);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>服务者结算单</h2>
        <p>查看家政服务者的待结算、审核、出款状态</p>
      </div>
      <button class="button primary">发起结算批次</button>
    </div>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        服务者结算数据加载失败：{{ errorMessage }}
      </div>

      <div class="toolbar">
        <div class="field">
          <label>结算单号</label>
          <input v-model="filters.settlementOrderId" placeholder="请输入结算单号" />
        </div>
        <div class="field">
          <label>服务者 ID</label>
          <input v-model="filters.workerKeyword" placeholder="请输入服务者名称" />
        </div>
        <div class="field">
          <label>出款状态</label>
          <select v-model="filters.payoutStatus">
            <option>全部</option>
            <option>待出款</option>
            <option>出款中</option>
            <option>出款成功</option>
          </select>
        </div>
        <div class="field">
          <label>结算状态</label>
          <select v-model="filters.settlementStatus">
            <option>全部</option>
            <option>待审核</option>
            <option>待出款</option>
            <option>出款成功</option>
          </select>
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="applyFilters">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">服务者结算数据加载中...</div>

      <div v-else-if="!items.length" class="state-box">当前暂无符合条件的服务者结算数据</div>

      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>结算单号</th>
              <th>服务者</th>
              <th>账期</th>
              <th>应结金额</th>
              <th>已扣减金额</th>
              <th>实结金额</th>
              <th>保证金影响</th>
              <th>结算状态</th>
              <th>出款状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in items" :key="item.settlementOrderId">
              <td>{{ item.settlementOrderId }}</td>
              <td>{{ item.workerName }}</td>
              <td>{{ item.period }}</td>
              <td>{{ item.amountShouldSettle }}</td>
              <td>{{ item.deductAmount }}</td>
              <td>{{ item.amountNetSettle }}</td>
              <td>{{ item.depositImpactAmount }}</td>
              <td><span :class="['badge', item.statusType]">{{ item.status }}</span></td>
              <td><span :class="['badge', item.payoutStatusType]">{{ item.payoutStatus }}</span></td>
              <td>
                <div class="list-actions">
                  <span>详情</span>
                  <span>审核</span>
                  <span>出款</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
