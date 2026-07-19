<script setup>
import { onMounted, ref } from "vue";
import { billApi } from "../api/client";

const items = ref([]);
const isLoading = ref(true);
const errorMessage = ref("");
const total = ref(0);
const pageNo = ref(1);
const pageSize = 20;
const filters = ref({
  billNo: "",
  orderNo: "",
  billStatus: "全部"
});

function resetFilters() {
  filters.value = {
    billNo: "",
    orderNo: "",
    billStatus: "全部"
  };
  pageNo.value = 1;
  loadBills();
}

function applyFilters() {
  pageNo.value = 1;
  loadBills();
}

async function loadBills() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const result = await billApi.getList({
      billNo: filters.value.billNo,
      orderNo: filters.value.orderNo,
      billStatus: filters.value.billStatus,
      pageNo: pageNo.value,
      pageSize
    });
    items.value = result.items;
    total.value = result.total;
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    isLoading.value = false;
  }
}

function goToPage(nextPage) {
  if (nextPage < 1 || nextPage > Math.ceil(total.value / pageSize)) {
    return;
  }
  pageNo.value = nextPage;
  loadBills();
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
          <input value="当前已接入后端筛选，不承接账务会计口径" disabled />
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="applyFilters">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">账单数据加载中...</div>

      <div v-else-if="!items.length" class="state-box">当前暂无符合条件的账单数据</div>

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
            <tr v-for="item in items" :key="item.billNo">
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
      <div v-if="total > pageSize" class="pager">
        <span>共 {{ total }} 条账单</span>
        <button class="button secondary" :disabled="pageNo === 1" @click="goToPage(pageNo - 1)">上一页</button>
        <span>第 {{ pageNo }} / {{ Math.ceil(total / pageSize) }} 页</span>
        <button class="button secondary" :disabled="pageNo >= Math.ceil(total / pageSize)" @click="goToPage(pageNo + 1)">下一页</button>
      </div>
    </section>
  </div>
</template>
