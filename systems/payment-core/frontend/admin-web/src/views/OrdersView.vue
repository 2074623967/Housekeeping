<script setup>
import { onMounted, ref } from "vue";
import { orderApi, paymentApi } from "../api/client";

const items = ref([]);
const isLoading = ref(true);
const errorMessage = ref("");
const actionMessage = ref("");
const launchPayload = ref(null);
const activeOrderNo = ref("");
const total = ref(0);
const pageNo = ref(1);
const pageSize = 20;
const filters = ref({
  orderNo: "",
  serviceType: "全部",
  orderStatus: "全部"
});

const appWebBaseUrl = "http://127.0.0.1:5175";

function resetFilters() {
  filters.value = {
    orderNo: "",
    serviceType: "全部",
    orderStatus: "全部"
  };
  pageNo.value = 1;
  loadOrders();
}

function applyFilters() {
  pageNo.value = 1;
  loadOrders();
}

async function loadOrders() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const result = await orderApi.getList({
      orderNo: filters.value.orderNo,
      serviceType: filters.value.serviceType,
      orderStatus: filters.value.orderStatus,
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

function canLaunchPayment(orderItem) {
  return orderItem.orderStatus === "待支付";
}

function goToPage(nextPage) {
  if (nextPage < 1 || nextPage > Math.ceil(total.value / pageSize)) {
    return;
  }
  pageNo.value = nextPage;
  loadOrders();
}

async function launchPayment(orderNo) {
  activeOrderNo.value = orderNo;
  actionMessage.value = "";
  try {
    const prepay = await paymentApi.prepay({
      orderNo,
      payScene: "HOME_CLEAN"
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
  } finally {
    activeOrderNo.value = "";
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

      <div v-else-if="!items.length" class="state-box">当前暂无符合条件的订单数据</div>

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
            <tr v-for="item in items" :key="item.orderNo">
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
                  <span>{{ item.orderStatus === "待支付" ? "待发起" : "已生成支付链路" }}</span>
                  <span>{{ item.paidAmount === item.orderAmount ? "账单已结清" : "账单待支付" }}</span>
                  <button
                    class="link-button"
                    :disabled="activeOrderNo === item.orderNo || !canLaunchPayment(item)"
                    @click="launchPayment(item.orderNo)"
                  >
                    {{
                      activeOrderNo === item.orderNo
                        ? "发起中..."
                        : canLaunchPayment(item)
                          ? "发起支付"
                          : "无需发起"
                    }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="total > pageSize" class="pager">
        <span>共 {{ total }} 条订单</span>
        <button class="button secondary" :disabled="pageNo === 1" @click="goToPage(pageNo - 1)">上一页</button>
        <span>第 {{ pageNo }} / {{ Math.ceil(total / pageSize) }} 页</span>
        <button class="button secondary" :disabled="pageNo >= Math.ceil(total / pageSize)" @click="goToPage(pageNo + 1)">下一页</button>
      </div>
    </section>
  </div>
</template>
