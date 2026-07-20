<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentApi } from "../api/client";

const route = useRoute();
const router = useRouter();
const detail = ref(null);
const isLoading = ref(true);
const errorMessage = ref("");
const actionMessage = ref("");
const activeAction = ref("");

const paymentRecordDetailRoute = computed(() => {
  if (!detail.value?.paymentOrderId) {
    return "";
  }
  return `/payment-records/${detail.value.paymentOrderId}?recordType=ALL`;
});

const orderRoute = computed(() => {
  if (!detail.value?.orderNo) {
    return "";
  }
  return `/orders?orderNo=${detail.value.orderNo}`;
});

const billRoute = computed(() => {
  if (!detail.value?.billNo && !detail.value?.orderNo) {
    return "";
  }
  const billNo = detail.value?.billNo || "";
  const orderNo = detail.value?.orderNo || "";
  return `/bills?billNo=${billNo}&orderNo=${orderNo}`;
});

const cashierUrl = computed(() => {
  if (!detail.value?.prepayOrderNo) {
    return "";
  }
  return `http://127.0.0.1:5175/cashier/${detail.value.prepayOrderNo}`;
});

async function loadDetail() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    detail.value = await paymentApi.getDetail(route.params.paymentOrderId);
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    isLoading.value = false;
  }
}

function isActionRunning(actionName) {
  return activeAction.value === actionName;
}

async function handleQuery() {
  activeAction.value = "query";
  try {
    const paymentDetail = await paymentApi.query(route.params.paymentOrderId);
    actionMessage.value = `支付单 ${route.params.paymentOrderId} 当前状态为 ${paymentDetail.status}。`;
    await loadDetail();
  } catch (error) {
    actionMessage.value = `主动查单失败：${error.message}`;
  } finally {
    activeAction.value = "";
  }
}

async function handleCallback() {
  activeAction.value = "callback";
  try {
    const paymentDetail = await paymentApi.callback("WX_H5", route.params.paymentOrderId);
    actionMessage.value = `支付单 ${route.params.paymentOrderId} 已模拟回调，当前状态为 ${paymentDetail.status}。`;
    await loadDetail();
  } catch (error) {
    actionMessage.value = `模拟回调失败：${error.message}`;
  } finally {
    activeAction.value = "";
  }
}

async function handleClose() {
  activeAction.value = "close";
  try {
    const paymentDetail = await paymentApi.close(route.params.paymentOrderId);
    actionMessage.value = paymentDetail.status === "CLOSED"
      ? `支付单 ${route.params.paymentOrderId} 已关闭。`
      : `支付单 ${route.params.paymentOrderId} 当前状态为 ${paymentDetail.status}，未执行关闭。`;
    await loadDetail();
  } catch (error) {
    actionMessage.value = `关闭支付单失败：${error.message}`;
  } finally {
    activeAction.value = "";
  }
}

onMounted(loadDetail);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付单详情</h2>
        <p>查看支付主链路、路由轨迹、回调轨迹和事件轨迹</p>
      </div>
      <div class="toolbar-actions">
        <button class="button secondary" @click="router.push('/payments')">返回列表</button>
        <button class="button secondary" @click="router.push(`/payment-requests?paymentOrderId=${route.params.paymentOrderId}`)">
          查看支付请求
        </button>
        <button
          class="button secondary"
          :disabled="!paymentRecordDetailRoute"
          @click="router.push(paymentRecordDetailRoute)"
        >
          查看支付记录
        </button>
        <button class="button secondary" @click="router.push(`/payment-logs?paymentOrderId=${route.params.paymentOrderId}`)">
          查看处理日志
        </button>
        <button class="button secondary" :disabled="!!activeAction" @click="handleQuery">
          {{ isActionRunning("query") ? "查单中..." : "主动查单" }}
        </button>
        <button class="button secondary" :disabled="!!activeAction" @click="handleCallback">
          {{ isActionRunning("callback") ? "回调中..." : "模拟回调" }}
        </button>
        <button class="button primary" :disabled="!!activeAction" @click="handleClose">
          {{ isActionRunning("close") ? "关闭中..." : "关闭支付单" }}
        </button>
      </div>
    </div>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">
        支付单详情加载失败：{{ errorMessage }}
      </div>
      <div v-if="actionMessage" class="state-banner">
        {{ actionMessage }}
      </div>
      <div v-if="isLoading" class="state-box">支付单详情加载中...</div>
      <template v-else-if="detail">
        <div class="detail-card-grid">
          <div class="detail-card">
            <div class="detail-label">支付单号</div>
            <div class="detail-value">{{ detail.paymentOrderId }}</div>
          </div>
          <div class="detail-card">
            <div class="detail-label">预付单号</div>
            <div class="detail-value">{{ detail.prepayOrderNo || "-" }}</div>
          </div>
          <div class="detail-card">
            <div class="detail-label">账单号</div>
            <div class="detail-value">{{ detail.billNo || "-" }}</div>
          </div>
          <div class="detail-card">
            <div class="detail-label">当前状态</div>
            <div class="detail-value"><span :class="['badge', detail.statusType]">{{ detail.status }}</span></div>
          </div>
        </div>

        <div class="detail-grid detail-grid-wide">
          <div><strong>订单号：</strong>{{ detail.orderNo }}</div>
          <div><strong>客户：</strong>{{ detail.customerName }}</div>
          <div><strong>账单号：</strong>{{ detail.billNo || "-" }}</div>
          <div><strong>金额：</strong>{{ detail.amount }}</div>
          <div><strong>支付方式：</strong>{{ detail.paymentMethod }}</div>
          <div><strong>支付渠道：</strong>{{ detail.channel }}</div>
          <div><strong>渠道交易号：</strong>{{ detail.channelTransactionNo || "-" }}</div>
          <div><strong>查单来源：</strong>{{ detail.querySource || "-" }}</div>
          <div><strong>创建时间：</strong>{{ detail.createdAt }}</div>
        </div>

        <section class="panel mini">
          <h4>联查入口</h4>
          <div class="list-actions">
            <RouterLink v-if="detail.orderNo" class="link-button" :to="orderRoute">回到订单中心</RouterLink>
            <RouterLink v-if="detail.billNo || detail.orderNo" class="link-button" :to="billRoute">查看账单中心</RouterLink>
            <RouterLink v-if="paymentRecordDetailRoute" class="link-button" :to="paymentRecordDetailRoute">查看支付记录详情</RouterLink>
            <a v-if="cashierUrl" class="link-button" :href="cashierUrl" target="_blank" rel="noreferrer">打开当前收银台</a>
          </div>
        </section>

        <div class="split-panels">
          <section class="panel mini">
            <h4>路由记录</h4>
            <div v-for="item in detail.routeLogs || []" :key="item" class="timeline-item">{{ item }}</div>
            <div v-if="!(detail.routeLogs || []).length" class="state-box">当前暂无路由记录</div>
          </section>
          <section class="panel mini">
            <h4>回调日志</h4>
            <div v-for="item in detail.notifyLogs || []" :key="item" class="timeline-item">{{ item }}</div>
            <div v-if="!(detail.notifyLogs || []).length" class="state-box">当前暂无回调日志</div>
          </section>
        </div>
        <section class="panel mini">
          <h4>事件轨迹</h4>
          <div v-for="item in detail.eventLogs || []" :key="item" class="timeline-item">{{ item }}</div>
          <div v-if="!(detail.eventLogs || []).length" class="state-box">当前暂无事件轨迹</div>
        </section>
      </template>
    </section>
  </div>
</template>
