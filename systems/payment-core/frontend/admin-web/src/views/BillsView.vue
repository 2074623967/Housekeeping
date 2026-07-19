<script setup>
import { computed, onMounted, ref } from "vue";
import { billApi } from "../api/client";

const items = ref([]);
const isLoading = ref(true);
const errorMessage = ref("");
const filters = ref({
  billNo: "",
  orderNo: "",
  billStatus: "全部"
});

const filteredItems = computed(() =>
  items.value.filter((item) => {
    const normalizedBillNo = filters.value.billNo.trim();
    const normalizedOrderNo = filters.value.orderNo.trim();
    const matchesBillNo = !normalizedBillNo || item.billNo.includes(normalizedBillNo);
    const matchesOrderNo = !normalizedOrderNo || item.orderNo.includes(normalizedOrderNo);
    const matchesStatus = filters.value.billStatus === "全部" || item.billStatus === filters.value.billStatus;
    return matchesBillNo && matchesOrderNo && matchesStatus;
  })
);

function resetFilters() {
  filters.value = {
    billNo: "",
    orderNo: "",
    billStatus: "全部"
  };
}

async function loadBills() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    items.value = await billApi.getList();
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    isLoading.value = false;
  }
}

onMounted(loadBills);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>账单中心</h2>
        <p>按交易账单视角查看订单应收、已收和待收进展，为支付排查提供中间桥梁</p>
      </div>
      <button class="button primary">导出账单</button>
    </div>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        账单数据加载失败：{{ errorMessage }}
      </div>

      <div class="toolbar">
        <div class="field">
          <label>账单号</label>
          <input v-model="filters.billNo" placeholder="请输入账单号" />
        </div>
        <div class="field">
          <label>订单号</label>
          <input v-model="filters.orderNo" placeholder="请输入订单号" />
        </div>
        <div class="field">
          <label>账单状态</label>
          <select v-model="filters.billStatus">
            <option>全部</option>
            <option>待支付</option>
            <option>已结清</option>
          </select>
        </div>
        <div class="field">
          <label>当前说明</label>
          <input value="当前以交易账单查询为主，不承接账务会计口径" disabled />
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="loadBills">刷新</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">账单数据加载中...</div>

      <div v-else-if="!filteredItems.length" class="state-box">当前暂无符合条件的账单数据</div>

      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>账单号</th>
              <th>订单号</th>
              <th>客户</th>
              <th>账单应收</th>
              <th>已支付</th>
              <th>待支付</th>
              <th>账单状态</th>
              <th>到期时间</th>
              <th>创建时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filteredItems" :key="item.billNo">
              <td>{{ item.billNo }}</td>
              <td>{{ item.orderNo }}</td>
              <td>{{ item.customerName }}</td>
              <td>{{ item.billAmount }}</td>
              <td>{{ item.paidAmount }}</td>
              <td>{{ item.unpaidAmount }}</td>
              <td><span :class="['badge', item.billStatusType]">{{ item.billStatus }}</span></td>
              <td>{{ item.dueAt }}</td>
              <td>{{ item.createdAt }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
