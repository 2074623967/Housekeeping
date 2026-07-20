<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentApi } from "../api/client";
import { PAYMENT_RESULT_STATE_META, resolvePaymentResultState } from "../constants/payment";

const props = defineProps({
  terminalVariant: {
    type: String,
    default: "app"
  }
});

const TERMINAL_COPY = {
  app: {
    heroLabel: "App 支付结果",
    homeLabel: "返回收银台",
    queryLabel: "查询最新状态",
    callbackLabel: "模拟成功回调",
    closeLabel: "关闭支付单"
  },
  h5: {
    heroLabel: "H5 支付结果",
    homeLabel: "回到支付页",
    queryLabel: "刷新支付结果",
    callbackLabel: "模拟成功回调",
    closeLabel: "结束本次支付"
  },
  pc: {
    heroLabel: "PC 支付结果",
    homeLabel: "返回 PC 收银台",
    queryLabel: "刷新桌面端支付结果",
    callbackLabel: "模拟成功回调",
    closeLabel: "关闭当前支付单"
  }
};

const route = useRoute();
const router = useRouter();
const resultState = ref("pending");
const feedbackMessage = ref("");
const paymentDetail = ref(null);
const detailLoading = ref(true);
const queryLoading = ref(false);
const callbackLoading = ref(false);
const closeLoading = ref(false);
const lastAction = ref("");

const terminalCopy = computed(() => TERMINAL_COPY[props.terminalVariant] || TERMINAL_COPY.app);
const isPcVariant = computed(() => props.terminalVariant === "pc");
const resultTitle = computed(() => PAYMENT_RESULT_STATE_META[resultState.value].title);
const resultHint = computed(() => PAYMENT_RESULT_STATE_META[resultState.value].hint);
const resultBadgeClass = computed(() => `status-${paymentDetail.value?.statusType || "info"}`);

function syncStatusByDetail() {
  if (!paymentDetail.value) {
    resultState.value = "pending";
    return;
  }
  resultState.value = resolvePaymentResultState(paymentDetail.value.status);
}

async function loadDetail() {
  detailLoading.value = true;
  feedbackMessage.value = "";
  try {
    paymentDetail.value = await paymentApi.getDetail(route.params.paymentOrderId);
    syncStatusByDetail();
  } catch (error) {
    feedbackMessage.value = error.message;
  } finally {
    detailLoading.value = false;
  }
}

onMounted(loadDetail);

async function queryResult() {
  queryLoading.value = true;
  try {
    paymentDetail.value = await paymentApi.query({ paymentOrderId: route.params.paymentOrderId });
    syncStatusByDetail();
    feedbackMessage.value = `已查询支付单 ${route.params.paymentOrderId} 的最新状态。`;
    lastAction.value = "主动查单";
  } catch (error) {
    feedbackMessage.value = error.message;
  } finally {
    queryLoading.value = false;
  }
}

async function mockSuccessCallback() {
  callbackLoading.value = true;
  try {
    paymentDetail.value = await paymentApi.callback("WX_H5", {
      paymentOrderId: route.params.paymentOrderId,
      channelTransactionNo: `SIM${Date.now()}`,
      tradeStatus: "SUCCESS"
    });
    syncStatusByDetail();
    feedbackMessage.value = "已模拟成功回调并完成状态收口。";
    lastAction.value = "模拟成功回调";
  } catch (error) {
    feedbackMessage.value = error.message;
  } finally {
    callbackLoading.value = false;
  }
}

async function closePayment() {
  closeLoading.value = true;
  try {
    paymentDetail.value = await paymentApi.close({ paymentOrderId: route.params.paymentOrderId });
    syncStatusByDetail();
    feedbackMessage.value = `支付单 ${route.params.paymentOrderId} 已执行关闭动作。`;
    lastAction.value = "关闭支付单";
  } catch (error) {
    feedbackMessage.value = error.message;
  } finally {
    closeLoading.value = false;
  }
}

function backToCashier() {
  const prepayOrderNo = route.query.prepayOrderNo;
  if (!prepayOrderNo) {
    return;
  }
  router.push(`/cashier/${prepayOrderNo}`);
}
</script>

<template>
  <div class="terminal-page" :class="`terminal-${terminalVariant}`">
    <section class="terminal-hero">
      <div class="hero-copy">
        <div class="hero-label">{{ terminalCopy.heroLabel }}</div>
        <h1>{{ resultTitle }}</h1>
        <p>{{ resultHint }}</p>
      </div>
      <div class="hero-amount-card">
        <div class="mini-label">支付状态</div>
        <div class="hero-amount status-text">{{ paymentDetail?.status || "处理中" }}</div>
        <div class="hero-status-row">
          <span class="status-pill" :class="resultBadgeClass">{{ paymentDetail?.statusType || "info" }}</span>
          <span class="countdown-pill">{{ route.params.paymentOrderId }}</span>
        </div>
      </div>
    </section>

    <div v-if="detailLoading" class="terminal-card terminal-state-card">支付结果加载中...</div>
    <div v-else-if="feedbackMessage && !paymentDetail" class="terminal-card terminal-state-card">{{ feedbackMessage }}</div>

    <div v-else class="terminal-grid">
      <section class="terminal-card">
        <div class="section-heading">
          <div>
            <h3>结果摘要</h3>
            <p>当前支付单的交易收口状态、渠道流水和联调动作都会在这里更新。</p>
          </div>
          <span class="support-tag">交易闭环已留痕</span>
        </div>

        <div class="summary-grid">
          <div class="summary-item">
            <span>支付单号</span>
            <strong>{{ paymentDetail?.paymentOrderId || route.params.paymentOrderId }}</strong>
          </div>
          <div class="summary-item">
            <span>预付单号</span>
            <strong>{{ paymentDetail?.prepayOrderNo || route.query.prepayOrderNo || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>订单号</span>
            <strong>{{ paymentDetail?.orderNo || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>客户名称</span>
            <strong>{{ paymentDetail?.customerName || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>金额</span>
            <strong>{{ paymentDetail?.amount || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>支付方式</span>
            <strong>{{ paymentDetail?.paymentMethod || route.query.paymentMethod || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>渠道编码</span>
            <strong>{{ paymentDetail?.channel || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>渠道流水号</span>
            <strong>{{ paymentDetail?.channelTransactionNo || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>最近动作</span>
            <strong>{{ lastAction || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>查单来源</span>
            <strong>{{ paymentDetail?.querySource || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>最近尝试状态</span>
            <strong>{{ paymentDetail?.latestAttemptStatus || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>发起终端</span>
            <strong>{{ paymentDetail?.latestTerminal || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>客户端 IP</span>
            <strong>{{ paymentDetail?.latestClientIp || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>幂等键</span>
            <strong class="mono-text">{{ paymentDetail?.latestIdempotencyKey || "-" }}</strong>
          </div>
        </div>

        <div class="ops-card">
          <div class="ops-title">最近支付尝试</div>
          <div class="ops-row">
            <span>尝试状态</span>
            <span :class="['status-pill', `status-${paymentDetail?.latestAttemptStatusType || 'info'}`]">
              {{ paymentDetail?.latestAttemptStatus || "-" }}
            </span>
          </div>
          <div class="ops-row"><span>最近请求报文</span><span class="mono-text">{{ paymentDetail?.latestRequestPayload || "-" }}</span></div>
          <div class="ops-row"><span>最近响应报文</span><span class="mono-text">{{ paymentDetail?.latestResponsePayload || "-" }}</span></div>
        </div>

        <div class="terminal-actions">
          <button class="action-button primary" :disabled="queryLoading" @click="queryResult">
            {{ queryLoading ? "查询中..." : terminalCopy.queryLabel }}
          </button>
          <button class="action-button secondary" :disabled="callbackLoading" @click="mockSuccessCallback">
            {{ callbackLoading ? "回调中..." : terminalCopy.callbackLabel }}
          </button>
          <button class="action-button ghost" :disabled="closeLoading" @click="closePayment">
            {{ closeLoading ? "关闭中..." : terminalCopy.closeLabel }}
          </button>
          <button class="action-button secondary" :disabled="!route.query.prepayOrderNo" @click="backToCashier">
            {{ terminalCopy.homeLabel }}
          </button>
        </div>

        <p v-if="feedbackMessage" class="feedback-text">{{ feedbackMessage }}</p>

        <div v-if="isPcVariant" class="desktop-hint-grid">
          <div class="desktop-hint-card">
            <strong>桌面端结果处理</strong>
            <p>PC 端适合由客服、运营或用户本人停留在结果页，边看轨迹边执行主动查单或关闭支付动作。</p>
          </div>
          <div class="desktop-hint-card">
            <strong>联调建议</strong>
            <p>若渠道回调延迟，可先查看路由轨迹，再执行主动查单，确保桌面端结果页与后台详情页状态一致。</p>
          </div>
        </div>
      </section>

      <section class="terminal-card">
        <div class="section-heading">
          <div>
            <h3>支付轨迹</h3>
            <p>按路由、回调、事件三个维度查看整个支付链路的收口过程。</p>
          </div>
        </div>

        <div class="timeline-cluster">
          <div class="timeline-block">
            <h4>路由轨迹</h4>
            <div v-for="item in paymentDetail?.routeLogs || []" :key="item" class="timeline-entry">{{ item }}</div>
            <div v-if="!(paymentDetail?.routeLogs || []).length" class="timeline-empty">当前暂无路由轨迹</div>
          </div>
          <div class="timeline-block">
            <h4>回调轨迹</h4>
            <div v-for="item in paymentDetail?.notifyLogs || []" :key="item" class="timeline-entry">{{ item }}</div>
            <div v-if="!(paymentDetail?.notifyLogs || []).length" class="timeline-empty">当前暂无回调轨迹</div>
          </div>
          <div class="timeline-block">
            <h4>事件轨迹</h4>
            <div v-for="item in paymentDetail?.eventLogs || []" :key="item" class="timeline-entry">{{ item }}</div>
            <div v-if="!(paymentDetail?.eventLogs || []).length" class="timeline-empty">当前暂无事件轨迹</div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>
