<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentApi, paymentRecordApi } from "../api/client";

const route = useRoute();
const router = useRouter();
const detail = ref(null);
const isLoading = ref(true);
const errorMessage = ref("");
const actionMessage = ref("");
const activeAction = ref("");

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

const metricCards = computed(() => {
  if (!detail.value) {
    return [];
  }
  return [
    {
      title: "支付金额",
      value: formatValue(detail.value.paymentAmount),
      hint: `已退 ${formatValue(detail.value.refundedAmount)} / 退款次数 ${formatValue(detail.value.refundCount)}`
    },
    {
      title: "路由轨迹数",
      value: `${(detail.value.routeLogs || []).length}`,
      hint: "用于判断路由选型与重试轨迹"
    },
    {
      title: "回调轨迹数",
      value: `${(detail.value.notifyLogs || []).length}`,
      hint: "用于判断回调是否已收口"
    },
    {
      title: "事件轨迹数",
      value: `${(detail.value.eventLogs || []).length}`,
      hint: "用于判断下游事件是否完整发布"
    }
  ];
});

const operationSuggestions = computed(() => {
  if (!detail.value) {
    return [];
  }
  const suggestions = [];
  if ((detail.value.notifyLogs || []).length === 0) {
    suggestions.push("当前无回调轨迹，优先核对渠道回调是否到达与通知地址是否配置正确。");
  }
  if ((detail.value.eventLogs || []).length === 0) {
    suggestions.push("当前无事件轨迹，建议联查支付处理日志和事件出站记录。");
  }
  if (detail.value.latestAttemptStatus && detail.value.latestAttemptStatus !== "SUCCESS") {
    suggestions.push(`最近一次支付尝试状态为 ${detail.value.latestAttemptStatus}，建议先主动查单再决定是否人工干预。`);
  }
  if (!suggestions.length) {
    suggestions.push("当前记录链路完整，可结合支付单详情继续做业务复盘。");
  }
  return suggestions;
});

async function handleQueryPayment() {
  activeAction.value = "query";
  actionMessage.value = "";
  try {
    await paymentApi.query(route.params.paymentOrderId);
    await loadDetail();
    actionMessage.value = `支付单 ${route.params.paymentOrderId} 已完成主动查单，详情数据已刷新。`;
  } catch (error) {
    errorMessage.value = `主动查单失败：${error.message}`;
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
        <h2>支付记录详情</h2>
        <p>从收款记录反查支付尝试、路由、回调、事件和报文，补齐支付运营排查视角</p>
      </div>
      <div class="toolbar-actions">
        <button class="button secondary" @click="goBackToList">返回{{ recordTypeLabel }}</button>
        <button class="button secondary" @click="openPaymentDetail">查看支付单详情</button>
        <button class="button secondary" @click="openPaymentRequests">查看支付请求</button>
        <button class="button secondary" @click="openPaymentLogs">查看处理日志</button>
        <button class="button primary" :disabled="!!activeAction" @click="handleQueryPayment">
          {{ activeAction === "query" ? "查单中..." : "主动查单" }}
        </button>
      </div>
    </div>

    <section class="panel">
      <div v-if="errorMessage" class="error-banner">支付记录详情加载失败：{{ errorMessage }}</div>
      <div v-if="actionMessage" class="state-banner">{{ actionMessage }}</div>
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

        <div class="detail-card-grid detail-panel">
          <div v-for="card in metricCards" :key="card.title" class="detail-card">
            <div class="detail-label">{{ card.title }}</div>
            <div class="detail-value">{{ card.value }}</div>
            <div class="meta">{{ card.hint }}</div>
          </div>
        </div>

        <div class="split-panels detail-panel">
          <section class="panel mini">
            <div class="section-title">
              <h3>业务快照</h3>
              <span class="meta">收款、渠道和回调关键字段</span>
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
              <div><strong>渠道返回码：</strong>{{ formatValue(detail.channelReturnCode) }}</div>
              <div><strong>回调地址：</strong>{{ formatValue(detail.notifyUrl) }}</div>
              <div><strong>回调 MQ 主题：</strong>{{ formatValue(detail.callbackMqTopic) }}</div>
            </div>
          </section>

          <section class="panel mini">
            <div class="section-title">
              <h3>排障建议</h3>
              <span class="meta">按当前记录自动整理</span>
            </div>
            <div class="ops-card">
              <div class="ops-row"><span>来源列表</span><span>{{ recordTypeLabel }}</span></div>
              <div class="ops-row"><span>支付状态</span><span>{{ formatValue(detail.paymentStatus) }}</span></div>
              <div class="ops-row"><span>最近尝试</span><span>{{ formatValue(detail.latestAttemptStatus) }}</span></div>
            </div>
            <div class="suggestion-list">
              <div v-for="item in operationSuggestions" :key="item" class="timeline-item">{{ item }}</div>
            </div>
          </section>
        </div>

        <div class="detail-grid detail-grid-wide">
          <div><strong>支付金额：</strong>{{ formatValue(detail.paymentAmount) }}</div>
          <div><strong>已退金额：</strong>{{ formatValue(detail.refundedAmount) }}</div>
          <div><strong>退款次数：</strong>{{ formatValue(detail.refundCount) }}</div>
          <div><strong>银行名：</strong>{{ formatValue(detail.bankName) }}</div>
          <div><strong>卡号：</strong>{{ formatValue(detail.cardNo) }}</div>
          <div><strong>收款账号：</strong>{{ formatValue(detail.receivingAccount) }}</div>
          <div><strong>返参类型：</strong>{{ formatValue(detail.returnParameterType) }}</div>
          <div><strong>参数值：</strong>{{ formatValue(detail.parameterValue) }}</div>
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

<style scoped>
.suggestion-list {
  display: grid;
  gap: 10px;
  margin-top: 12px;
}
</style>
