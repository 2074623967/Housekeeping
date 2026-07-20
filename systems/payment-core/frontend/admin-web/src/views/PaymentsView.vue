<script setup>
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { paymentApi } from "../api/client";

const router = useRouter();
const items = ref([]);
const isLoading = ref(true);
const errorMessage = ref("");
const actionMessage = ref("");
const activePaymentOrderId = ref("");
const activeAction = ref("");
const total = ref(0);
const pageNo = ref(1);
const pageSize = 20;
const filters = ref({
  paymentOrderId: "",
  orderNo: "",
  paymentMethod: "全部",
  status: "全部"
});

function resetFilters() {
  filters.value = {
    paymentOrderId: "",
    orderNo: "",
    paymentMethod: "全部",
    status: "全部"
  };
  pageNo.value = 1;
  refreshList();
}

async function refreshList() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const result = await paymentApi.getList({
      paymentOrderId: filters.value.paymentOrderId,
      orderNo: filters.value.orderNo,
      paymentMethod: filters.value.paymentMethod,
      status: filters.value.status,
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

function applyFilters() {
  pageNo.value = 1;
  refreshList();
}

function goToPage(nextPage) {
  if (nextPage < 1 || nextPage > Math.ceil(total.value / pageSize)) {
    return;
  }
  pageNo.value = nextPage;
  refreshList();
}

function openDetail(paymentOrderId) {
  router.push(`/payments/${paymentOrderId}`);
}

function isActionRunning(paymentOrderId, actionName) {
  return activePaymentOrderId.value === paymentOrderId && activeAction.value === actionName;
}

async function handleQuery(paymentOrderId) {
  activePaymentOrderId.value = paymentOrderId;
  activeAction.value = "query";
  try {
    const paymentDetail = await paymentApi.query(paymentOrderId);
    actionMessage.value = `支付单 ${paymentOrderId} 当前状态为 ${paymentDetail.status}。`;
    await refreshList();
  } catch (error) {
    actionMessage.value = `支付单 ${paymentOrderId} 查单失败：${error.message}`;
  } finally {
    activePaymentOrderId.value = "";
    activeAction.value = "";
  }
}

async function handleClose(paymentOrderId) {
  activePaymentOrderId.value = paymentOrderId;
  activeAction.value = "close";
  try {
    const paymentDetail = await paymentApi.close(paymentOrderId);
    actionMessage.value = paymentDetail.status === "CLOSED"
      ? `支付单 ${paymentOrderId} 已关闭。`
      : `支付单 ${paymentOrderId} 当前状态为 ${paymentDetail.status}，未执行关闭。`;
    await refreshList();
  } catch (error) {
    actionMessage.value = `支付单 ${paymentOrderId} 关闭失败：${error.message}`;
  } finally {
    activePaymentOrderId.value = "";
    activeAction.value = "";
  }
}

async function handleCallback(paymentOrderId) {
  activePaymentOrderId.value = paymentOrderId;
  activeAction.value = "callback";
  try {
    const paymentDetail = await paymentApi.callback("WX_H5", paymentOrderId);
    actionMessage.value = `支付单 ${paymentOrderId} 已模拟回调，当前状态为 ${paymentDetail.status}。`;
    await refreshList();
  } catch (error) {
    actionMessage.value = `支付单 ${paymentOrderId} 模拟回调失败：${error.message}`;
  } finally {
    activePaymentOrderId.value = "";
    activeAction.value = "";
  }
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
          <button class="button primary" @click="applyFilters">查询</button>
          <button class="button secondary" @click="resetFilters">重置</button>
        </div>
      </div>

      <div v-if="isLoading" class="state-box">支付单数据加载中...</div>

      <div v-else-if="!items.length" class="state-box">当前暂无符合条件的支付单数据</div>

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
            <tr v-for="item in items" :key="item.paymentOrderId">
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
                  <button class="link-button" :disabled="activePaymentOrderId === item.paymentOrderId" @click="handleQuery(item.paymentOrderId)">
                    {{ isActionRunning(item.paymentOrderId, "query") ? "查单中..." : "查单" }}
                  </button>
                  <button class="link-button" :disabled="activePaymentOrderId === item.paymentOrderId" @click="handleCallback(item.paymentOrderId)">
                    {{ isActionRunning(item.paymentOrderId, "callback") ? "回调中..." : "回调" }}
                  </button>
                  <button class="link-button" :disabled="activePaymentOrderId === item.paymentOrderId" @click="handleClose(item.paymentOrderId)">
                    {{ isActionRunning(item.paymentOrderId, "close") ? "关闭中..." : "关闭" }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="total > pageSize" class="pager">
        <span>共 {{ total }} 条支付单</span>
        <button class="button secondary" :disabled="pageNo === 1" @click="goToPage(pageNo - 1)">上一页</button>
        <span>第 {{ pageNo }} / {{ Math.ceil(total / pageSize) }} 页</span>
        <button class="button secondary" :disabled="pageNo >= Math.ceil(total / pageSize)" @click="goToPage(pageNo + 1)">下一页</button>
      </div>
    </section>
  </div>
</template>
