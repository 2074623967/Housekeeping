<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentApi, paymentRecordApi } from "../api/client";

const route = useRoute();
const router = useRouter();
const detail = ref(null);
const isLoading = ref(true);
const errorMessage = ref("");

const recordType = computed(() => route.query.recordType || "ALL");
const recordTypeLabel = computed(() => {
  if (recordType.value === "WECHAT") {
    return "微信支付宝支付记录";
  }
  if (recordType.value === "BANK_CARD") {
    return "银行卡支付记录";
  }
  return "统一支付记录";
});

async function loadDetail() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    detail.value = await paymentRecordApi.getDetail(route.params.paymentOrderId);
  } catch (error) {
    errorMessage.value = error.message;
  } finally {
    isLoading.value = false;
  }
}

function goBackToList() {
  const pathMap = {
    ALL: "/payment-records",
    WECHAT: "/payment-records/wechat-alipay",
    BANK_CARD: "/payment-records/bank-card"
  };
  router.push(pathMap[recordType.value] || "/payment-records");
}

function openPaymentDetail() {
  router.push(`/payments/${route.params.paymentOrderId}`);
}

function openPaymentRequests() {
  router.push(`/payment-requests?paymentOrderId=${route.params.paymentOrderId}`);
}

function openPaymentLogs() {
  router.push(`/payment-logs?paymentOrderId=${route.params.paymentOrderId}`);
}

function formatValue(value) {
  return value === null || value === undefined || value === "" ? "—" : value;
}

function formatTextBlock(value) {
  return value || "暂无报文数据";
}

async function handleQueryPayment() {
  try {
    await paymentApi.query(route.params.paymentOrderId);
    await loadDetail();
  } catch (error) {
    errorMessage.value = `主动查单失败：${error.message}`;
  }
}

onMounted(loadDetail);
</script>

<template>
  <div>
    <div class="topbar">
      <div>
        <h2>支付记录详情</h2>
        <p>从收款记录反查支付尝试、路由、回调、事件和报文，补齐支付运营排查视角</p>
      </div>
      <div class="toolbar-actions">
        <button class="button secondary" @click="goBackToList">返回{{ recordTypeLabel }}</button>
        <button class="button secondary" @click="openPaymentDetail">查看支付单详情</button>
        <button class="button secondary" @click="openPaymentRequests">查看支付请求</button>
        <button class="button secondary" @click="openPaymentLogs">查看处理日志</button>
        <button class="button primary" @click="handleQueryPayment">主动查单</button>
      </div>
    </div>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">支付记录详情加载失败：{{ errorMessage }}</div>
      <div v-if="isLoading" class="state-box">支付记录详情加载中...</div>
      <template v-else-if="detail">
        <div class="detail-card-grid">
          <div class="detail-card">
            <div class="detail-label">支付单号</div>
            <div class="detail-value">{{ formatValue(detail.paymentOrderId) }}</div>
          </div>
          <div class="detail-card">
            <div class="detail-label">支付状态</div>
            <div class="detail-value">
              <span :class="['badge', detail.statusType]">{{ formatValue(detail.paymentStatus) }}</span>
            </div>
          </div>
          <div class="detail-card">
            <div class="detail-label">支付方式</div>
            <div class="detail-value">{{ formatValue(detail.paymentMethod) }}</div>
          </div>
          <div class="detail-card">
            <div class="detail-label">最近一次尝试状态</div>
            <div class="detail-value">
              <span :class="['badge', detail.latestAttemptStatusType || 'info']">
                {{ formatValue(detail.latestAttemptStatus) }}
              </span>
            </div>
          </div>
        </div>

        <div class="detail-grid detail-grid-wide">
          <div><strong>业务订单号：</strong>{{ formatValue(detail.businessOrderNo) }}</div>
          <div><strong>支付请求号：</strong>{{ formatValue(detail.paymentRequestNo) }}</div>
          <div><strong>支付类型：</strong>{{ formatValue(detail.paymentType) }}</div>
          <div><strong>支付网关：</strong>{{ formatValue(detail.paymentGateway) }}</div>
          <div><strong>支付渠道：</strong>{{ formatValue(detail.paymentChannel) }}</div>
          <div><strong>外部交易流水：</strong>{{ formatValue(detail.externalTransactionNo) }}</div>
          <div><strong>用户ID：</strong>{{ formatValue(detail.userId) }}</div>
          <div><strong>用户支付渠道标识：</strong>{{ formatValue(detail.userPaymentChannelId) }}</div>
          <div><strong>商品名称：</strong>{{ formatValue(detail.productName) }}</div>
          <div><strong>支付金额：</strong>{{ formatValue(detail.paymentAmount) }}</div>
          <div><strong>已退金额：</strong>{{ formatValue(detail.refundedAmount) }}</div>
          <div><strong>退款次数：</strong>{{ formatValue(detail.refundCount) }}</div>
          <div><strong>银行名：</strong>{{ formatValue(detail.bankName) }}</div>
          <div><strong>卡号：</strong>{{ formatValue(detail.cardNo) }}</div>
          <div><strong>收款账号：</strong>{{ formatValue(detail.receivingAccount) }}</div>
          <div><strong>渠道返回码：</strong>{{ formatValue(detail.channelReturnCode) }}</div>
          <div><strong>返参类型：</strong>{{ formatValue(detail.returnParameterType) }}</div>
          <div><strong>参数值：</strong>{{ formatValue(detail.parameterValue) }}</div>
          <div><strong>回调地址：</strong>{{ formatValue(detail.notifyUrl) }}</div>
          <div><strong>回调 MQ 主题：</strong>{{ formatValue(detail.callbackMqTopic) }}</div>
          <div><strong>有效期：</strong>{{ formatValue(detail.validityPeriod) }}</div>
          <div><strong>失效时间：</strong>{{ formatValue(detail.expireTime) }}</div>
          <div><strong>创建时间：</strong>{{ formatValue(detail.createdAt) }}</div>
          <div><strong>更新时间：</strong>{{ formatValue(detail.updatedAt) }}</div>
          <div><strong>支付成功时间：</strong>{{ formatValue(detail.paidAt) }}</div>
          <div><strong>最近终端：</strong>{{ formatValue(detail.latestTerminal) }}</div>
          <div><strong>最近客户端 IP：</strong>{{ formatValue(detail.latestClientIp) }}</div>
          <div><strong>最近幂等键：</strong>{{ formatValue(detail.latestIdempotencyKey) }}</div>
        </div>

        <div class="payload-grid">
          <div>
            <strong>最近一次支付请求报文</strong>
            <pre>{{ formatTextBlock(detail.latestRequestPayload) }}</pre>
          </div>
          <div>
            <strong>最近一次支付响应报文</strong>
            <pre>{{ formatTextBlock(detail.latestResponsePayload) }}</pre>
          </div>
        </div>

        <div class="split-panels detail-panel">
          <section class="panel mini">
            <h4>路由轨迹</h4>
            <div v-for="item in detail.routeLogs || []" :key="item" class="timeline-item">{{ item }}</div>
            <div v-if="!(detail.routeLogs || []).length" class="state-box">暂无路由轨迹</div>
          </section>
          <section class="panel mini">
            <h4>回调轨迹</h4>
            <div v-for="item in detail.notifyLogs || []" :key="item" class="timeline-item">{{ item }}</div>
            <div v-if="!(detail.notifyLogs || []).length" class="state-box">暂无回调轨迹</div>
          </section>
        </div>

        <section class="panel mini detail-panel">
          <h4>事件轨迹</h4>
          <div v-for="item in detail.eventLogs || []" :key="item" class="timeline-item">{{ item }}</div>
          <div v-if="!(detail.eventLogs || []).length" class="state-box">暂无事件轨迹</div>
        </section>
      </template>
    </section>
  </div>
</template>
