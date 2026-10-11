<script setup>
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentApi } from "../api/client";
import {
  canClosePaymentLocally,
  canQueryPaymentChannel,
  PAYMENT_DETAIL_REFRESH_MAX_ATTEMPTS,
  PAYMENT_RESULT_STATE_META,
  resolvePaymentDetailRefreshDelay,
  resolvePaymentResultState,
  shouldAutoRefreshPaymentDetail
} from "../constants/payment";
import { persistPaymentAccessToken, withoutAccessToken } from "../constants/paymentSession";
import { matchesPaymentOrder, matchesPaymentSnapshot, matchesPrepayOrder } from "../constants/paymentEntity";
import { buildCashierRoute, resolvePaymentBusinessEntryPath } from "../router/paymentRoutes";

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
    closeLabel: "关闭支付单"
  },
  h5: {
    heroLabel: "H5 支付结果",
    homeLabel: "回到支付页",
    queryLabel: "刷新支付结果",
    closeLabel: "结束本次支付"
  },
  pc: {
    heroLabel: "PC 支付结果",
    homeLabel: "返回 PC 收银台",
    queryLabel: "刷新桌面端支付结果",
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

const GENERIC_SCENE_META = {
  label: "业务支付",
  successHint: "支付成功后，请返回原业务订单页核对后续进度。",
  pendingHint: "支付仍在处理中，请以支付单和原业务订单的实际状态为准。",
  closedHint: "支付单已关闭，请从原业务订单页重新发起支付。"
};

const BIZ_PAY_SCENE = {
  recharge: { app: "WALLET_RECHARGE_APP", h5: "WALLET_RECHARGE_H5", pc: "WALLET_RECHARGE_PC" },
  withdraw: { app: "WITHDRAW_REPAY_APP", h5: "WITHDRAW_REPAY_H5", pc: "WITHDRAW_REPAY_PC" },
  transfer: { app: "ACCOUNT_TRANSFER_APP", h5: "ACCOUNT_TRANSFER_H5", pc: "ACCOUNT_TRANSFER_PC" },
  "balance-pay": { app: "BALANCE_PAY_APP", h5: "BALANCE_PAY_H5", pc: "BALANCE_PAY_PC" }
};

const route = useRoute();
const router = useRouter();
const expectedRoutePayScene = computed(() => {
  const routePayScene = typeof route.query.payScene === "string"
    ? route.query.payScene.trim()
    : "";
  return routePayScene || BIZ_PAY_SCENE[resolveBizType()]?.[props.terminalVariant] || null;
});
const resultState = ref("pending");
const feedbackMessage = ref("");
const paymentDetail = ref(null);
const detailLoading = ref(true);
const detailRefreshLoading = ref(false);
const queryLoading = ref(false);
const closeLoading = ref(false);
const lastAction = ref("");
const loadedPaymentOrderId = ref("");
const autoRefreshStale = ref(false);
let latestLoadRequestId = 0;
let currentEntityVersion = 0;
let autoRefreshTimer = null;
const autoRefreshAttempts = ref(0);

const terminalCopy = computed(() => TERMINAL_COPY[props.terminalVariant] || TERMINAL_COPY.app);
const currentPaymentOrderId = computed(() => typeof route.params.paymentOrderId === "string"
  ? route.params.paymentOrderId.trim()
  : "");
const expectedPrepayOrderNo = computed(() => typeof route.query.prepayOrderNo === "string"
  ? route.query.prepayOrderNo.trim()
  : "");
const expectedPaymentMethod = computed(() => typeof route.query.paymentMethod === "string"
  ? route.query.paymentMethod.trim()
  : "");
const hasValidPaymentSnapshot = computed(() => Boolean(
  loadedPaymentOrderId.value === currentPaymentOrderId.value
  && matchesPaymentOrder(currentPaymentOrderId.value, paymentDetail.value)
));
const hasStaleResult = computed(() => autoRefreshStale.value && hasValidPaymentSnapshot.value);
const businessEntryPath = computed(() => resolvePaymentBusinessEntryPath(paymentDetail.value?.payScene));
const bizSceneMeta = computed(() => BIZ_SCENE_META[businessEntryPath.value?.slice(1)] || GENERIC_SCENE_META);
const isPcVariant = computed(() => props.terminalVariant === "pc");
const actionRunning = computed(() => detailLoading.value || detailRefreshLoading.value
  || queryLoading.value || closeLoading.value);
const hasUnverifiedResult = computed(() => !detailLoading.value && !paymentDetail.value && Boolean(feedbackMessage.value));
const resultTitle = computed(() => hasStaleResult.value || hasUnverifiedResult.value
  ? "支付结果待核查" : detailLoading.value && !paymentDetail.value
    ? "正在加载支付结果" : PAYMENT_RESULT_STATE_META[resultState.value].title);
const resultHint = computed(() => hasStaleResult.value || hasUnverifiedResult.value
  ? "暂无法确认实际支付状态。请勿重复付款，保留支付单号并联系运营核查渠道与订单事实。"
  : PAYMENT_RESULT_STATE_META[resultState.value].hint);
const resultBadgeClass = computed(() => `status-${hasStaleResult.value ? "warn" : paymentDetail.value?.statusType || (hasUnverifiedResult.value ? "warn" : "info")}`);
const canClosePayment = computed(() => {
  return hasValidPaymentSnapshot.value && !hasStaleResult.value && canClosePaymentLocally(
    paymentDetail.value?.status,
    paymentDetail.value?.channelTransactionNo,
    paymentDetail.value?.latestAttemptStatus
  );
});
const canQueryPayment = computed(() => hasValidPaymentSnapshot.value && !hasStaleResult.value && canQueryPaymentChannel(
  paymentDetail.value?.status,
  paymentDetail.value?.latestAttemptStatus,
  paymentDetail.value?.latestAttemptCompletedAt,
  paymentDetail.value?.channelTransactionNo,
  paymentDetail.value?.channel
));
const autoRefreshPaused = computed(() => hasValidPaymentSnapshot.value
  && shouldAutoRefreshPaymentDetail(paymentDetail.value?.status)
  && autoRefreshAttempts.value >= PAYMENT_DETAIL_REFRESH_MAX_ATTEMPTS);
const sourceAppId = computed(() => {
  if (props.terminalVariant === "pc") {
    return "housekeeping-pc-web";
  }
  if (props.terminalVariant === "h5") {
    return "housekeeping-h5-web";
  }
  return "housekeeping-app-web";
});
const nextStepChecklist = computed(() => {
  if (hasStaleResult.value) {
    return [
      "当前只保留上次读取的详情，支付状态尚未重新确认。",
      "请勿重复付款或根据旧状态关单，等待自动重试或手动刷新支付详情。",
      "如仍无法核实，保留支付单号并联系运营核查渠道与订单。"
    ];
  }
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
      "不要人工改写支付结果，以免绕过真实风控闭环。"
    ];
  }
  if (resultState.value === "risk_rejected") {
    return [
      "本次支付已被风控拒绝，需由运营明确拒绝原因并通知用户。",
      "建议核对风险复核意见、订单业务背景和用户投诉处理方案。",
      "如需继续交易，应重新生成新支付单并在风控侧完成放行后再发起。",
      "不要继续对当前支付单人工制造成功终态。"
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
  if (resultState.value === "paying") {
    return [
      "渠道受理尚未确认，请刷新支付详情并等待状态推进，不要重复付款或更换幂等键。",
      "此时不可主动查单或关单；长时间未推进请保留支付单号联系运营核查。"
    ];
  }
  if (resultState.value === "fail") {
    return [
      "当前支付结果待确认，请勿重复付款，也不要将本地状态视为资金失败证明。",
      "请刷新支付详情；若仍无法核实，保留支付单号并联系运营核查渠道与订单事实。"
    ];
  }
  if (resultState.value === "wait_callback") {
    return [
      bizSceneMeta.value.pendingHint,
      "支付请求已提交成功，当前应等待真实渠道回调或执行主动查单。",
      "若后台与用户端状态不一致，先核对支付请求、回调轨迹和事件出站状态。",
      "页面不能人工指定支付成功；长时间未收口时请转运营排查渠道通知和查单记录。"
    ];
  }
  return [
    "当前状态尚未确认，请勿重复付款或自行关闭支付单。",
    "请刷新支付详情，保留支付单号并联系运营核查渠道与订单事实。"
  ];
});
const recoveryActions = computed(() => {
  if (hasStaleResult.value) {
    return [
      "优先刷新支付详情，不要依赖上次读取的状态发起新的支付。",
      "若网络持续异常，请联系服务方核查真实支付和业务订单状态。"
    ];
  }
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
  if (resultState.value === "paying") {
    return [
      "等待页面自动刷新或手动刷新支付详情，不要重复付款。",
      "不可主动查单或关单，请在状态推进后按页面可用操作继续。"
    ];
  }
  if (resultState.value === "fail") {
    return [
      "刷新支付详情并保留支付单号，勿按未核实的结果重新支付。",
      "联系运营核查渠道流水、回调和业务订单事实。"
    ];
  }
  return [
    "优先刷新支付详情，未确认资金结果前不要重复付款或关闭支付单。",
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

function stopAutoRefresh() {
  if (autoRefreshTimer) {
    clearTimeout(autoRefreshTimer);
    autoRefreshTimer = null;
  }
}

function scheduleAutoRefresh() {
  stopAutoRefresh();
  if (!hasValidPaymentSnapshot.value) {
    return;
  }
  const refreshDelay = resolvePaymentDetailRefreshDelay(
    paymentDetail.value?.status,
    autoRefreshAttempts.value
  );
  if (refreshDelay == null) {
    return;
  }
  autoRefreshTimer = setTimeout(refreshDetailInBackground, refreshDelay);
}

async function refreshDetailInBackground() {
  autoRefreshTimer = null;
  if (actionRunning.value) {
    scheduleAutoRefresh();
    return;
  }
  if (!hasValidPaymentSnapshot.value
      || resolvePaymentDetailRefreshDelay(
        paymentDetail.value?.status,
        autoRefreshAttempts.value
      ) == null) {
    return;
  }
  const snapshot = buildCurrentPaymentSnapshot();
  const entityVersion = currentEntityVersion;
  const requestId = ++latestLoadRequestId;
  try {
    const detail = await paymentApi.getDetail(
      snapshot.paymentOrderId,
      expectedRoutePayScene.value
    );
    if (requestId !== latestLoadRequestId || !isCurrentPaymentSnapshot(snapshot, entityVersion)) {
      return;
    }
    if (!matchesPaymentSnapshot(snapshot, detail)) {
      throw new Error("自动刷新响应与当前支付单不一致");
    }
    paymentDetail.value = { ...paymentDetail.value, ...detail };
    autoRefreshStale.value = false;
    syncStatusByDetail();
  } catch {
    if (requestId === latestLoadRequestId && isCurrentPaymentSnapshot(snapshot, entityVersion)) {
      autoRefreshStale.value = true;
    }
  } finally {
    if (requestId === latestLoadRequestId && entityVersion === currentEntityVersion) {
      autoRefreshAttempts.value += 1;
      scheduleAutoRefresh();
    }
  }
}

async function loadDetail(paymentOrderId = currentPaymentOrderId.value) {
  const requestId = ++latestLoadRequestId;
  detailLoading.value = true;
  feedbackMessage.value = "";
  try {
    const detail = await paymentApi.getDetail(
      paymentOrderId,
      expectedRoutePayScene.value
    );
    if (requestId !== latestLoadRequestId || paymentOrderId !== currentPaymentOrderId.value) {
      return;
    }
    if (!matchesPaymentOrder(paymentOrderId, detail)) {
      throw new Error("支付详情响应为空或与当前支付单不一致");
    }
    if (expectedPrepayOrderNo.value && !matchesPrepayOrder(expectedPrepayOrderNo.value, detail)) {
      throw new Error("支付详情响应为空或与当前预付单不一致");
    }
    if (expectedPaymentMethod.value
        && (typeof detail.paymentMethod !== "string"
          || detail.paymentMethod.trim() !== expectedPaymentMethod.value)) {
      throw new Error("支付详情响应为空或与当前支付方式不一致");
    }
    paymentDetail.value = { ...paymentDetail.value, ...detail };
    loadedPaymentOrderId.value = paymentOrderId;
    autoRefreshStale.value = false;
    syncStatusByDetail();
    scheduleAutoRefresh();
  } catch (error) {
    if (requestId !== latestLoadRequestId || paymentOrderId !== currentPaymentOrderId.value) {
      return;
    }
    feedbackMessage.value = error.message;
  } finally {
    if (requestId === latestLoadRequestId) {
      detailLoading.value = false;
    }
  }
}

function migrateLegacyAccessToken() {
  if (typeof route.query.accessToken === "string" && route.query.accessToken.trim()) {
    persistPaymentAccessToken(sourceAppId.value, route.query.accessToken.trim());
  }
  if (Object.prototype.hasOwnProperty.call(route.query, "accessToken")) {
    router.replace({
      path: route.path,
      query: withoutAccessToken(route.query),
      hash: route.hash
    });
  }
}

watch(() => route.query.accessToken, migrateLegacyAccessToken, { immediate: true });
onBeforeUnmount(stopAutoRefresh);

async function refreshPaymentDetail() {
  if (actionRunning.value || !hasValidPaymentSnapshot.value) {
    return;
  }
  const snapshot = buildCurrentPaymentSnapshot();
  const entityVersion = currentEntityVersion;
  stopAutoRefresh();
  const requestId = ++latestLoadRequestId;
  detailRefreshLoading.value = true;
  feedbackMessage.value = "";
  try {
    const detail = await paymentApi.getDetail(snapshot.paymentOrderId, expectedRoutePayScene.value);
    if (requestId !== latestLoadRequestId || !isCurrentPaymentSnapshot(snapshot, entityVersion)) {
      return;
    }
    if (!matchesPaymentSnapshot(snapshot, detail)) {
      throw new Error("支付详情刷新响应为空或与当前支付单不一致");
    }
    paymentDetail.value = { ...paymentDetail.value, ...detail };
    autoRefreshStale.value = false;
    syncStatusByDetail();
    autoRefreshAttempts.value = 0;
    feedbackMessage.value = "支付详情已刷新。";
  } catch (error) {
    if (requestId === latestLoadRequestId && isCurrentPaymentSnapshot(snapshot, entityVersion)) {
      paymentDetail.value = null;
      loadedPaymentOrderId.value = "";
      autoRefreshStale.value = false;
      syncStatusByDetail();
      feedbackMessage.value = error.message;
    }
  } finally {
    if (requestId === latestLoadRequestId && entityVersion === currentEntityVersion) {
      detailRefreshLoading.value = false;
      scheduleAutoRefresh();
    }
  }
}

async function readBackAfterFailedAction(snapshot, entityVersion, actionError) {
  if (!isCurrentPaymentSnapshot(snapshot, entityVersion)) {
    return;
  }
  try {
    const detail = await paymentApi.getDetail(snapshot.paymentOrderId, expectedRoutePayScene.value);
    if (!isCurrentPaymentSnapshot(snapshot, entityVersion)) {
      return;
    }
    if (!matchesPaymentSnapshot(snapshot, detail)) {
      throw new Error("动作失败后的支付详情与当前支付单不一致");
    }
    paymentDetail.value = { ...paymentDetail.value, ...detail };
    autoRefreshStale.value = false;
    syncStatusByDetail();
    feedbackMessage.value = `${actionError.message}；支付详情已重新读取。`;
  } catch {
    if (!isCurrentPaymentSnapshot(snapshot, entityVersion)) {
      return;
    }
    paymentDetail.value = null;
    loadedPaymentOrderId.value = "";
    syncStatusByDetail();
    feedbackMessage.value = `${actionError.message}；支付状态未经确认，请重新加载详情，勿重复付款。`;
  }
}

async function queryResult() {
  if (actionRunning.value) {
    return;
  }
  if (!hasValidPaymentSnapshot.value) {
    feedbackMessage.value = "当前支付详情尚未成功加载，不能执行主动查单。";
    return;
  }
  if (!canQueryPayment.value) {
    feedbackMessage.value = hasStaleResult.value
      ? "当前支付详情未能重新确认，请先刷新支付详情。"
      : "当前支付单尚未提交渠道，不能执行主动查单。";
    return;
  }
  const snapshot = buildCurrentPaymentSnapshot();
  const entityVersion = currentEntityVersion;
  stopAutoRefresh();
  latestLoadRequestId += 1;
  queryLoading.value = true;
  try {
    const detail = await paymentApi.query(
      { paymentOrderId: snapshot.paymentOrderId },
      snapshot,
      expectedRoutePayScene.value
    );
    if (!isCurrentPaymentSnapshot(snapshot, entityVersion)) {
      return;
    }
    if (!matchesPaymentSnapshot(snapshot, detail)) {
      throw new Error("查单响应为空或与当前支付单不一致");
    }
    paymentDetail.value = { ...paymentDetail.value, ...detail };
    syncStatusByDetail();
    feedbackMessage.value = `已查询支付单 ${snapshot.paymentOrderId} 的最新状态。`;
    autoRefreshAttempts.value = 0;
    lastAction.value = "主动查单";
  } catch (error) {
    await readBackAfterFailedAction(snapshot, entityVersion, error);
  } finally {
    if (entityVersion === currentEntityVersion) {
      queryLoading.value = false;
      scheduleAutoRefresh();
    }
  }
}

async function closePayment() {
  if (actionRunning.value) {
    return;
  }
  if (!hasValidPaymentSnapshot.value || !canClosePayment.value) {
    feedbackMessage.value = hasStaleResult.value
      ? "当前支付详情未能重新确认，请先刷新支付详情。"
      : "当前支付单不满足本地关闭条件。";
    return;
  }
  const snapshot = buildCurrentPaymentSnapshot();
  const entityVersion = currentEntityVersion;
  stopAutoRefresh();
  latestLoadRequestId += 1;
  closeLoading.value = true;
  try {
    const detail = await paymentApi.close(
      { paymentOrderId: snapshot.paymentOrderId },
      snapshot,
      expectedRoutePayScene.value
    );
    if (!isCurrentPaymentSnapshot(snapshot, entityVersion)) {
      return;
    }
    if (!matchesPaymentSnapshot(snapshot, detail)) {
      throw new Error("关单响应为空或与当前支付单不一致");
    }
    paymentDetail.value = { ...paymentDetail.value, ...detail };
    syncStatusByDetail();
    feedbackMessage.value = `支付单 ${snapshot.paymentOrderId} 已执行关闭动作。`;
    lastAction.value = "关闭支付单";
  } catch (error) {
    await readBackAfterFailedAction(snapshot, entityVersion, error);
  } finally {
    if (entityVersion === currentEntityVersion) {
      closeLoading.value = false;
      scheduleAutoRefresh();
    }
  }
}

function backToCashier() {
  if (!hasValidPaymentSnapshot.value || hasStaleResult.value || !paymentDetail.value?.prepayOrderNo) {
    return;
  }
  const prepayOrderNo = paymentDetail.value.prepayOrderNo;
  const cashierRouteQuery = {};
  if (typeof route.query.terminalVariant === "string" && route.query.terminalVariant.trim()) {
    cashierRouteQuery.terminalVariant = route.query.terminalVariant.trim();
  }
  if (typeof route.query.bizType === "string" && route.query.bizType.trim()) {
    cashierRouteQuery.bizType = route.query.bizType.trim();
  }
  if (expectedRoutePayScene.value) {
    cashierRouteQuery.payScene = expectedRoutePayScene.value;
  }
  router.push(buildCashierRoute(prepayOrderNo, cashierRouteQuery));
}

function buildCurrentPaymentSnapshot() {
  return {
    prepayOrderNo: paymentDetail.value?.prepayOrderNo || "",
    paymentOrderId: loadedPaymentOrderId.value,
    businessChainId: paymentDetail.value?.businessChainId || "",
    billNo: paymentDetail.value?.billNo,
    orderNo: paymentDetail.value?.orderNo || "",
    customerName: paymentDetail.value?.customerName,
    payScene: paymentDetail.value?.payScene,
    amount: paymentDetail.value?.amount || "",
    createdAt: paymentDetail.value?.createdAt,
    successAt: paymentDetail.value?.successAt,
    latestAttemptCompletedAt: paymentDetail.value?.latestAttemptCompletedAt,
    channel: paymentDetail.value?.channel,
    channelTransactionNo: paymentDetail.value?.channelTransactionNo,
    paymentMethod: paymentDetail.value?.paymentMethod,
    refundOrderId: paymentDetail.value?.refundOrderId
  };
}

function isCurrentPaymentSnapshot(snapshot, entityVersion) {
  return entityVersion === currentEntityVersion
    && hasValidPaymentSnapshot.value
    && matchesPaymentSnapshot(snapshot, paymentDetail.value);
}

function backToBusinessEntry() {
  if (businessEntryPath.value) {
    router.push(businessEntryPath.value);
  }
}

function resolveBizType() {
  if (typeof route.query.bizType === "string" && route.query.bizType.trim()) {
    return route.query.bizType.trim();
  }
  return "balance-pay";
}

watch([currentPaymentOrderId, expectedPrepayOrderNo, expectedPaymentMethod, expectedRoutePayScene], ([paymentOrderId]) => {
  currentEntityVersion += 1;
  latestLoadRequestId += 1;
  stopAutoRefresh();
  autoRefreshAttempts.value = 0;
  autoRefreshStale.value = false;
  paymentDetail.value = null;
  loadedPaymentOrderId.value = "";
  resultState.value = "pending";
  feedbackMessage.value = "";
  lastAction.value = "";
  detailRefreshLoading.value = false;
  queryLoading.value = false;
  closeLoading.value = false;
  loadDetail(paymentOrderId);
}, { immediate: true });
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
        <div class="hero-amount status-text">{{ hasStaleResult ? "待核查" : paymentDetail?.status || (hasUnverifiedResult ? "待核查" : "加载中") }}</div>
        <div class="hero-status-row">
          <span class="status-pill" :class="resultBadgeClass">{{ hasStaleResult ? "需核查" : paymentDetail?.statusType || (hasUnverifiedResult ? "需核查" : "加载中") }}</span>
          <span class="countdown-pill">{{ route.params.paymentOrderId }}</span>
        </div>
      </div>
    </section>

    <div v-if="detailLoading" class="terminal-card terminal-state-card">支付结果加载中...</div>
    <div v-else-if="feedbackMessage && !paymentDetail" class="terminal-card terminal-state-card">
      <p>{{ feedbackMessage }}</p>
      <button v-if="currentPaymentOrderId" class="action-button secondary" @click="loadDetail()">重试加载支付结果</button>
    </div>

    <div v-else class="terminal-grid">
      <section class="terminal-card">
        <div class="section-heading">
          <div>
            <h3>结果摘要</h3>
            <p>当前支付单的交易收口状态、渠道流水和处理动作都会在这里更新。</p>
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
            <span>账单号</span>
            <strong>{{ paymentDetail?.billNo || "-" }}</strong>
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
            <span>渠道流水号</span>
            <strong>{{ paymentDetail?.channelTransactionNo || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>支付成功时间</span>
            <strong>{{ paymentDetail?.successAt || "-" }}</strong>
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
            <span>渠道返回码</span>
            <strong>{{ paymentDetail?.channelReturnCode || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>标准错误码</span>
            <strong>{{ paymentDetail?.standardizedCode || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>标准错误说明</span>
            <strong>{{ paymentDetail?.standardizedMessage || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>映射版本</span>
            <strong>{{ paymentDetail?.returnCodeMappingVersion || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>映射命中</span>
            <strong>{{ paymentDetail?.returnCodeMappingMatched == null ? "-" : (paymentDetail.returnCodeMappingMatched ? "是" : "否") }}</strong>
          </div>
          <div class="summary-item">
            <span>是否可重试</span>
            <strong>{{ paymentDetail?.retryable || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>需要人工介入</span>
            <strong>{{ paymentDetail?.manualInterventionRequired || "-" }}</strong>
          </div>
          <div v-if="paymentDetail?.handlingSuggestion" class="summary-item">
            <span>渠道处理建议</span>
            <strong>{{ paymentDetail.handlingSuggestion }}</strong>
          </div>
          <div class="summary-item">
            <span>最近尝试状态</span>
            <strong>{{ paymentDetail?.latestAttemptStatus || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>最近尝试完成时间</span>
            <strong>{{ paymentDetail?.latestAttemptCompletedAt || "-" }}</strong>
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
          <button class="action-button secondary" :disabled="actionRunning || !hasValidPaymentSnapshot" @click="refreshPaymentDetail">
            {{ detailRefreshLoading ? "刷新中..." : "刷新支付详情" }}
          </button>
          <button class="action-button primary" :disabled="actionRunning || !canQueryPayment" @click="queryResult">
            {{ queryLoading ? "查询中..." : terminalCopy.queryLabel }}
          </button>
          <button v-if="businessEntryPath" class="action-button secondary" :disabled="actionRunning" @click="backToBusinessEntry">
            返回业务入口
          </button>
          <button class="action-button ghost" :disabled="actionRunning || !canClosePayment" @click="closePayment">
            {{ closeLoading ? "关闭中..." : terminalCopy.closeLabel }}
          </button>
          <button class="action-button secondary" :disabled="actionRunning || !hasValidPaymentSnapshot || hasStaleResult || !paymentDetail?.prepayOrderNo" @click="backToCashier">
            {{ terminalCopy.homeLabel }}
          </button>
        </div>

        <p v-if="!businessEntryPath && hasValidPaymentSnapshot" class="feedback-text">当前场景暂无对应的统一支付入口，请从原业务订单页返回。</p>

        <p v-if="feedbackMessage" class="feedback-text">{{ feedbackMessage }}</p>
        <p v-if="hasStaleResult" class="feedback-text">自动刷新未能确认最新支付状态，详情仅供核对上次读取的事实；请勿重复付款，等待自动重试或手动刷新支付详情。</p>
        <p v-if="autoRefreshPaused" class="feedback-text">自动刷新已暂停，当前展示上次成功查询的状态；请刷新支付详情或联系服务方。</p>

        <div v-if="isPcVariant" class="desktop-hint-grid">
          <div class="desktop-hint-card">
            <strong>桌面端结果处理</strong>
            <p>PC 端适合由客服、运营或用户本人停留在结果页核对轨迹；已提交渠道的支付只能继续查单。</p>
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
