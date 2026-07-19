<script setup>
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentApi } from "../api/client";

const route = useRoute();
const router = useRouter();
const detail = ref(null);
const isLoading = ref(true);
const errorMessage = ref("");
const actionMessage = ref("");

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

async function handleQuery() {
  await paymentApi.query(route.params.paymentOrderId);
  actionMessage.value = `支付单 ${route.params.paymentOrderId} 已完成主动查单。`;
  await loadDetail();
}

async function handleCallback() {
  await paymentApi.callback("wx_jsapi", route.params.paymentOrderId);
  actionMessage.value = `支付单 ${route.params.paymentOrderId} 已模拟成功回调。`;
  await loadDetail();
}

async function handleClose() {
  await paymentApi.close(route.params.paymentOrderId);
  actionMessage.value = `支付单 ${route.params.paymentOrderId} 已关闭。`;
  await loadDetail();
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
        <button class="button secondary" @click="handleQuery">主动查单</button>
        <button class="button secondary" @click="handleCallback">模拟回调</button>
        <button class="button primary" @click="handleClose">关闭支付单</button>
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
          <div><strong>金额：</strong>{{ detail.amount }}</div>
          <div><strong>支付方式：</strong>{{ detail.paymentMethod }}</div>
          <div><strong>支付渠道：</strong>{{ detail.channel }}</div>
          <div><strong>渠道交易号：</strong>{{ detail.channelTransactionNo || "-" }}</div>
          <div><strong>创建时间：</strong>{{ detail.createdAt }}</div>
        </div>

        <div class="split-panels">
          <section class="panel mini">
            <h4>路由记录</h4>
            <div v-for="item in detail.routeLogs || []" :key="item" class="timeline-item">{{ item }}</div>
          </section>
          <section class="panel mini">
            <h4>回调日志</h4>
            <div v-for="item in detail.notifyLogs || []" :key="item" class="timeline-item">{{ item }}</div>
          </section>
        </div>
        <section class="panel mini">
          <h4>事件轨迹</h4>
          <div v-for="item in detail.eventLogs || []" :key="item" class="timeline-item">{{ item }}</div>
        </section>
      </template>
    </section>
  </div>
</template>
