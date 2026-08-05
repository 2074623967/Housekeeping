<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentApi } from "../api/client";
import { PAYMENT_RESULT_STATE_META, resolvePaymentChannelCode, resolvePaymentResultState } from "../constants/payment";

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

const BIZ_SCENE_META = {
  recharge: {
    label: "充值",
    successHint: "支付成功后，优先核对钱包或预存账户是否已入账。",
    pendingHint: "充值场景若长时间处理中，需检查支付结果与钱包入账事件是否已发布。",
    closedHint: "旧充值支付单关闭后，建议重新发起新预付单，避免储值重复扣款。"
  },
  withdraw: {
    label: "提现补款",
    successHint: "支付成功后，可回提现系统确认手续费补扣或失败重试是否已继续推进。",
    pendingHint: "提现补款处理中时，建议同时核对支付结果和提现业务状态。",
    closedHint: "若旧补款单已关闭，请在提现侧重新发起新的补款流程。"
  },
  transfer: {
    label: "转账补款",
    successHint: "支付成功后，建议继续核对转账目标账户或后续结算单据是否已更新。",
    pendingHint: "转账场景处理中时，需同时关注支付结果与转账执行状态。",
    closedHint: "关闭后需重新拉起新的转账补款单，避免原业务单停留在待支付。"
  },
  "balance-pay": {
    label: "余额支付",
    successHint: "支付成功后，建议回订单中心确认履约、派单或上门流程是否已解锁。",
    pendingHint: "若尾款支付迟迟未收口，优先主动查单并检查订单支付状态是否同步。",
    closedHint: "关闭后需重新发起新的尾款支付单，避免订单继续挂起。"
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
const bizSceneMeta = computed(() => BIZ_SCENE_META[resolveBizType()] || BIZ_SCENE_META["balance-pay"]);
const isPcVariant = computed(() => props.terminalVariant === "pc");
const callbackChannelCode = computed(() => {
  if (paymentDetail.value?.channel) {
    return paymentDetail.value.channel;
  }
  if (paymentDetail.value?.paymentMethod) {
    return resolvePaymentChannelCode(paymentDetail.value.paymentMethod);
  }
  if (typeof route.query.paymentMethod === "string" && route.query.paymentMethod.trim()) {
    return resolvePaymentChannelCode(route.query.paymentMethod.trim());
  }
  return "WX_H5";
});
const resultTitle = computed(() => PAYMENT_RESULT_STATE_META[resultState.value].title);
const resultHint = computed(() => PAYMENT_RESULT_STATE_META[resultState.value].hint);
const resultBadgeClass = computed(() => `status-${paymentDetail.value?.statusType || "info"}`);
const canMockCallback = computed(() => {
  const status = paymentDetail.value?.status;
  return status === "WAIT_CALLBACK" || status === "PAYING";
});
const canClosePayment = computed(() => {
  const status = paymentDetail.value?.status;
  return Boolean(status) && status !== "SUCCESS" && status !== "CLOSED";
});
const nextStepChecklist = computed(() => {
  if (resultState.value === "success") {
    return [
      bizSceneMeta.value.successHint,
      "支付已成功收口，可回到订单页继续履约或查看服务进度。",
      "若后台与用户端状态不一致，优先查看事件轨迹是否已投递到账务、清分和结算链路。",
      "若是 PC 场景，建议客服或运营保留当前页，便于复核渠道流水号。"
    ];
  }
  if (resultState.value === "closed") {
    return [
      bizSceneMeta.value.closedHint,
      "当前支付单已关闭，建议返回收银台重新发起新的预付单。",
      "若用户已实际付款，请先保留凭证并联系运营核查渠道回调与支付请求。",
      "如关闭前发生重复点击，需对照幂等键和支付请求页确认是否存在重复提交流水。"
    ];
  }
  if (resultState.value === "risk_review") {
    return [
      "本次支付已进入风控人工复核，暂不建议继续重复点击支付。",
      "优先联系风控或运营核对复核单、订单金额、客户信息和设备/IP 风险特征。",
      "待风控审核通过后，再回到收银台重新提交当前预付单。",
      "如长时间未收口，需同步核查风控系统审核状态与支付单状态是否一致。"
    ];
  }
  if (resultState.value === "risk_blocked") {
    return [
      "本次支付已被策略拦截，需先调整订单、金额或支付方式后再尝试。",
      "建议后台联查风险策略、限额规则、黑名单和客户端来源信息。",
      "如确认误拦截，应先处理风控配置，再重新发起新的预付单。",
      "不要直接模拟成功回调，以免绕过真实风控闭环。"
    ];
  }
  if (resultState.value === "risk_rejected") {
    return [
      "本次支付已被风控拒绝，需由运营明确拒绝原因并通知用户。",
      "建议核对风险复核意见、订单业务背景和用户投诉处理方案。",
      "如需继续交易，应重新生成新支付单并在风控侧完成放行后再发起。",
      "不要继续对当前支付单执行成功回调联调。"
    ];
  }
  if (resultState.value === "prepay_created") {
    return [
      "当前仅完成预付单创建，尚未真正提交到渠道。",
      "可返回收银台确认支付方式、幂等键和接入令牌后重新发起支付。",
      "如后台已发起支付请求，请核对是否存在前端跳转中断或会话刷新不及时。",
      "若多次停留在待发起状态，建议联查支付请求管理页和收银台会话页。"
    ];
  }
  if (resultState.value === "wait_callback") {
    return [
      bizSceneMeta.value.pendingHint,
      "支付请求已提交成功，当前应优先等待回调或执行主动查单。",
      "若后台与用户端状态不一致，先核对支付请求、回调轨迹和事件出站状态。",
      "如需模拟联调，只在 WAIT_CALLBACK / PAYING 状态下执行成功回调。"
    ];
  }
  return [
    bizSceneMeta.value.pendingHint,
    "当前结果尚未最终收口，可先执行主动查单刷新最新状态。",
    "若渠道回调存在延迟，建议结合路由轨迹、回调轨迹和事件轨迹一起排查。",
    "若用户需要立即重试，先关闭当前支付单，再回到收银台切换支付方式发起新支付。"
  ];
});
const recoveryActions = computed(() => {
  if (resultState.value === "success") {
    return [
      "回到订单中心确认履约状态。",
      "如需补开发票或二次服务，可沿用当前订单号继续后续流程。"
    ];
  }
  if (resultState.value === "closed") {
    return [
      "重新拉起新的预付单并刷新用户收银台。",
      "同步客服说明当前旧支付单已关闭，避免用户重复付款。"
    ];
  }
  if (resultState.value === "risk_review") {
    return [
      "通知风控/运营优先处理当前复核单，再回收银台重新提交。",
      "保留支付单号、预付单号和风控复核编号，便于跨系统联查。"
    ];
  }
  if (resultState.value === "risk_blocked" || resultState.value === "risk_rejected") {
    return [
      "改走其他支付方式或调整交易参数后，再发起新的支付单。",
      "同步排查风控规则、限额与黑名单命中情况。"
    ];
  }
  if (resultState.value === "prepay_created") {
    return [
      "返回收银台完成正式支付提交。",
      "如页面长期未推进，刷新会话并核对当前预付单状态。"
    ];
  }
  return [
    "优先主动查单，再决定是否模拟回调或关闭支付单。",
    "保留支付单号、预付单号、幂等键，便于后台快速联查。"
  ];
});

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
    paymentDetail.value = await paymentApi.callback(callbackChannelCode.value, {
      paymentOrderId: route.params.paymentOrderId,
      channelTransactionNo: `SIM${Date.now()}`,
      tradeStatus: "SUCCESS"
    });
    syncStatusByDetail();
    feedbackMessage.value = `已按渠道 ${callbackChannelCode.value} 模拟成功回调并完成状态收口。`;
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
  const cashierRouteQuery = {};
  if (typeof route.query.terminalVariant === "string" && route.query.terminalVariant.trim()) {
    cashierRouteQuery.terminalVariant = route.query.terminalVariant.trim();
  }
  if (typeof route.query.accessToken === "string" && route.query.accessToken.trim()) {
    cashierRouteQuery.accessToken = route.query.accessToken.trim();
  }
  if (typeof route.query.bizType === "string" && route.query.bizType.trim()) {
    cashierRouteQuery.bizType = route.query.bizType.trim();
  }
  router.push({
    path: `/cashier/${prepayOrderNo}`,
    query: cashierRouteQuery
  });
}

function resolveBizType() {
  if (typeof route.query.bizType === "string" && route.query.bizType.trim()) {
    return route.query.bizType.trim();
  }
  return "balance-pay";
}
</script>

<template>
  <div class="terminal-page" :class="`terminal-${terminalVariant}`">
    <section class="terminal-hero">
      <div class="hero-copy">
        <div class="hero-label">{{ terminalCopy.heroLabel }}</div>
        <h1>{{ resultTitle }}</h1>
        <p>{{ resultHint }} 当前业务场景为{{ bizSceneMeta.label }}，结果页会给出对应的后续动作建议。</p>
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
            <span>业务场景</span>
            <strong>{{ bizSceneMeta.label }}</strong>
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
            <span>回调模拟渠道</span>
            <strong>{{ callbackChannelCode }}</strong>
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

        <div class="terminal-ops-grid">
          <div class="ops-card">
            <div class="ops-title">建议下一步</div>
            <ul class="result-checklist">
              <li v-for="item in nextStepChecklist" :key="item">{{ item }}</li>
            </ul>
          </div>
          <div class="ops-card">
            <div class="ops-title">补救动作</div>
            <ul class="result-checklist">
              <li v-for="item in recoveryActions" :key="item">{{ item }}</li>
            </ul>
          </div>
        </div>

        <div class="terminal-actions">
          <button class="action-button primary" :disabled="queryLoading" @click="queryResult">
            {{ queryLoading ? "查询中..." : terminalCopy.queryLabel }}
          </button>
          <button class="action-button secondary" :disabled="callbackLoading || !canMockCallback" @click="mockSuccessCallback">
            {{ callbackLoading ? "回调中..." : terminalCopy.callbackLabel }}
          </button>
          <button class="action-button ghost" :disabled="closeLoading || !canClosePayment" @click="closePayment">
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
