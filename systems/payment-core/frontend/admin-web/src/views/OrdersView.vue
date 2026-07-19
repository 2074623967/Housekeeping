<script setup>
import { computed, onMounted, ref } from "vue";
import { orderApi, paymentApi } from "../api/client";

const items = ref([]);
const isLoading = ref(true);
const errorMessage = ref("");
const actionMessage = ref("");
const launchPayload = ref(null);
const filters = ref({
  orderNo: "",
  serviceType: "全部",
  orderStatus: "全部"
});

const appWebBaseUrl = "http://127.0.0.1:5174";

const filteredItems = computed(() =>
  items.value.filter((item) => {
    const matchesOrderNo = !filters.value.orderNo || item.orderNo.includes(filters.value.orderNo.trim());
    const matchesServiceType = filters.value.serviceType === "全部" || item.serviceType === filters.value.serviceType;
    const matchesOrderStatus = filters.value.orderStatus === "全部" || item.orderStatus === filters.value.orderStatus;
    return matchesOrderNo && matchesServiceType && matchesOrderStatus;
  })
);

function resetFilters() {
  filters.value = {
    orderNo: "",
    serviceType: "全部",
    orderStatus: "全部"
  };
}

async function loadOrders() {
  try {
    items.value = await orderApi.getList();
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    isLoading.value = false;
  }
}

async function launchPayment(orderNo) {
  actionMessage.value = "";
  try {
    const prepay = await paymentApi.prepay({
      orderNo,
      payScene: "H5"
    });
    launchPayload.value = {
      orderNo,
      prepayOrderNo: prepay.prepayOrderNo,
      paymentOrderId: prepay.paymentOrderId,
      cashierUrl: `${appWebBaseUrl}/cashier/${prepay.prepayOrderNo}`
    };
    actionMessage.value = `订单 ${orderNo} 已生成预付单，可进入收银台继续支付。`;
  } catch (error) {
    actionMessage.value = `发起支付失败：${error.message}`;
  }
}

onMounted(loadOrders);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>订单中心</h2>
        <p>查看家政订单、支付状态和履约进展，并从订单侧发起支付</p>
      </div>
      <button class="button primary">导出订单</button>
    </div>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        订单数据加载失败：{{ errorMessage }}
      </div>
      <div v-if="actionMessage" class="state-banner">
        {{ actionMessage }}
      </div>

      <div class="toolbar">
        <div class="field">
          <label>订单号</label>
          <input v-model="filters.orderNo" placeholder="请输入订单号" />
        </div>
        <div class="field">
          <label>服务品类</label>
          <select v-model="filters.serviceType">
            <option>全部</option>
            <option>深度保洁</option>
            <option>月嫂套餐</option>
            <option>企业保洁</option>
          </select>
        </div>
        <div class="field">
          <label>订单状态</label>
          <select v-model="filters.orderStatus">
            <option>全部</option>
            <option>待支付</option>
            <option>待履约</option>
            <option>已完成</option>
          </select>
        </div>
        <div class="field">
          <label>当前说明</label>
          <input value="当前支持订单号、品类、状态筛选" disabled />
        </div>
        <div class="toolbar-actions">
          <button class="button primary" @click="loadOrders">刷新</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="launchPayload" class="launch-panel">
        <div>
          <strong>最新支付发起结果</strong>
          <div>订单号：{{ launchPayload.orderNo }}</div>
          <div>预付单号：{{ launchPayload.prepayOrderNo }}</div>
          <div>支付单号：{{ launchPayload.paymentOrderId }}</div>
        </div>
        <div class="launch-actions">
          <a :href="launchPayload.cashierUrl" class="button primary" target="_blank" rel="noreferrer">打开收银台</a>
          <div class="meta-link">{{ launchPayload.cashierUrl }}</div>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">订单数据加载中...</div>

      <div v-else-if="!filteredItems.length" class="state-box">当前暂无符合条件的订单数据</div>

      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>订单号</th>
              <th>用户</th>
              <th>服务品类</th>
              <th>服务者</th>
              <th>订单金额</th>
              <th>已付金额</th>
              <th>订单状态</th>
              <th>履约状态</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filteredItems" :key="item.orderNo">
              <td>{{ item.orderNo }}</td>
              <td>{{ item.customerName }}</td>
              <td>{{ item.serviceType }}</td>
              <td>{{ item.workerName }}</td>
              <td>{{ item.orderAmount }}</td>
              <td>{{ item.paidAmount }}</td>
              <td><span :class="['badge', item.orderStatusType]">{{ item.orderStatus }}</span></td>
              <td><span :class="['badge', item.fulfillmentStatusType]">{{ item.fulfillmentStatus }}</span></td>
              <td>{{ item.createdAt }}</td>
              <td>
                <div class="list-actions">
                  <span>详情</span>
                  <span>账单</span>
                  <button class="link-button" @click="launchPayment(item.orderNo)">发起支付</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
