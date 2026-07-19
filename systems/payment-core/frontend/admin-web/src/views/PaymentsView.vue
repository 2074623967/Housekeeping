<script setup>
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { paymentApi } from "../api/client";

const router = useRouter();
const items = ref([]);
const isLoading = ref(true);
const errorMessage = ref("");
const actionMessage = ref("");
const filters = ref({
  paymentOrderId: "",
  orderNo: "",
  paymentMethod: "全部",
  status: "全部"
});

const filteredItems = computed(() =>
  items.value.filter((item) => {
    const matchesPaymentOrderId = !filters.value.paymentOrderId || item.paymentOrderId.includes(filters.value.paymentOrderId.trim());
    const matchesOrderNo = !filters.value.orderNo || item.orderNo.includes(filters.value.orderNo.trim());
    const matchesMethod = filters.value.paymentMethod === "全部" || item.paymentMethod === filters.value.paymentMethod;
    const matchesStatus = filters.value.status === "全部" || item.status === filters.value.status;
    return matchesPaymentOrderId && matchesOrderNo && matchesMethod && matchesStatus;
  })
);

function resetFilters() {
  filters.value = {
    paymentOrderId: "",
    orderNo: "",
    paymentMethod: "全部",
    status: "全部"
  };
}

async function refreshList() {
  try {
    items.value = await paymentApi.getList();
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    isLoading.value = false;
  }
}

function openDetail(paymentOrderId) {
  router.push(`/payments/${paymentOrderId}`);
}

async function handleQuery(paymentOrderId) {
  await paymentApi.query(paymentOrderId);
  actionMessage.value = `支付单 ${paymentOrderId} 已发起主动查单。`;
  await refreshList();
}

async function handleClose(paymentOrderId) {
  await paymentApi.close(paymentOrderId);
  actionMessage.value = `支付单 ${paymentOrderId} 已关闭。`;
  await refreshList();
}

async function handleCallback(paymentOrderId) {
  await paymentApi.callback("WX_H5", paymentOrderId);
  actionMessage.value = `支付单 ${paymentOrderId} 已模拟成功回调。`;
  await refreshList();
}

onMounted(refreshList);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付单管理</h2>
        <p>统一查看支付单、支付方式、渠道交易和回调收敛情况</p>
      </div>
      <button class="button primary">导出支付单</button>
    </div>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        支付单数据加载失败：{{ errorMessage }}
      </div>
      <div v-if="actionMessage" class="state-banner">
        {{ actionMessage }}
      </div>

      <div class="toolbar">
        <div class="field">
          <label>支付单号</label>
          <input v-model="filters.paymentOrderId" placeholder="请输入支付单号" />
        </div>
        <div class="field">
          <label>订单号</label>
          <input v-model="filters.orderNo" placeholder="请输入订单号" />
        </div>
        <div class="field">
          <label>支付方式</label>
          <select v-model="filters.paymentMethod">
            <option>全部</option>
            <option>微信</option>
            <option>支付宝</option>
            <option>银行转账</option>
            <option>待选渠道</option>
          </select>
        </div>
        <div class="field">
          <label>支付状态</label>
          <select v-model="filters.status">
            <option>全部</option>
            <option>SUCCESS</option>
            <option>WAIT_CALLBACK</option>
            <option>FAIL</option>
            <option>CLOSED</option>
            <option>PREPAY_CREATED</option>
          </select>
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="refreshList">刷新</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">支付单数据加载中...</div>

      <div v-else-if="!filteredItems.length" class="state-box">当前暂无符合条件的支付单数据</div>

      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>支付单号</th>
              <th>订单号</th>
              <th>用户</th>
              <th>实付金额</th>
              <th>支付方式</th>
              <th>支付渠道</th>
              <th>渠道交易号</th>
              <th>支付状态</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filteredItems" :key="item.paymentOrderId">
              <td>{{ item.paymentOrderId }}</td>
              <td>{{ item.orderNo }}</td>
              <td>{{ item.customerName }}</td>
              <td>{{ item.amount }}</td>
              <td>{{ item.paymentMethod }}</td>
              <td>{{ item.channel }}</td>
              <td>{{ item.channelTransactionNo }}</td>
              <td><span :class="['badge', item.statusType]">{{ item.status }}</span></td>
              <td>{{ item.createdAt }}</td>
              <td>
                <div class="list-actions">
                  <button class="link-button" @click="openDetail(item.paymentOrderId)">详情</button>
                  <button class="link-button" @click="handleQuery(item.paymentOrderId)">查单</button>
                  <button class="link-button" @click="handleCallback(item.paymentOrderId)">回调</button>
                  <button class="link-button" @click="handleClose(item.paymentOrderId)">关闭</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
