<script setup>
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { paymentApi } from "../api/client";
import {
  canClosePaymentLocally,
  canQueryPaymentChannel,
  canRetryReviewedPayment,
  canShowDesktopQrPlaceholder,
  isChannelTransactionConflict,
  isCashierExpired,
  isPaymentSubmissionPending,
  resolvePaymentChannelCode,
  resolvePaymentMerchantNo
} from "../constants/payment";
import {
  persistPaymentAccessToken,
  readPaymentAccessToken,
  withoutAccessToken
} from "../constants/paymentSession";
import {
  UnverifiablePaymentSubmitAttemptError,
  clearPaymentSubmitAttempt,
  discardPaymentSubmitAttempt,
  generatePaymentSubmitKey,
  readPaymentSubmitAttemptForPrepay,
  resolvePaymentSubmitAttempt
} from "../constants/paymentSubmitAttempt";
import {
  matchesPaymentSnapshot,
  matchesPrepayOrder
} from "../constants/paymentEntity";
import { buildPaymentResultRoute, resolvePaymentBusinessEntryPath } from "../router/paymentRoutes";

const props = defineProps({
  terminalVariant: {
    type: String,
    default: "app"
  }
});

const PAYING_SUBMIT_HINT = "支付提交仍在处理中，请刷新收银台等待状态推进，暂勿重复付款。";

const TERMINAL_META = {
  app: {
    heroLabel: "家政服务 App 收银台",
    heroHint: "适用于家政用户在 App 内完成订单支付、补款和状态确认。",
    terminalCode: "APP_WEB",
    paymentMethods: ["微信支付", "支付宝"],
    actionLabel: "确认支付",
    refreshLabel: "刷新收银台",
    closeLabel: "关闭本次支付"
  },
  h5: {
    heroLabel: "家政服务 H5 收银台",
    heroHint: "适用于活动落地页、短信唤起和服务预约页中的轻量支付场景。",
    terminalCode: "H5_WEB",
    paymentMethods: ["微信支付", "支付宝"],
    actionLabel: "立即支付",
    refreshLabel: "刷新会话",
    closeLabel: "结束当前会话"
  },
  pc: {
    heroLabel: "家政服务 PC 收银台",
    heroHint: "适用于官网、运营后台代客支付和桌面浏览器场景，强调桌面端信息确认、扫码支付和失败补救动作。",
    terminalCode: "PC_WEB",
    paymentMethods: ["支付宝", "银行卡"],
    actionLabel: "确认并发起支付",
    refreshLabel: "刷新桌面收银台",
    closeLabel: "关闭当前支付单"
  }
};

const CHANNEL_META = {
  微信支付: {
    icon: "WX",
    description: "适合大多数个人用户，回调速度快，适配 H5 / App。",
    settlementHint: "预计实时回调，异常时支持主动查单。",
    recommendedScenes: "个人用户、日常保洁、到家服务",
    fallbackAction: "若停留在支付中，可等待回调 3-5 秒后主动查单。"
  },
  支付宝: {
    icon: "ALI",
    description: "适合 H5、生活服务与活动支付场景，兼容性稳定。",
    settlementHint: "适用于兜底路由和多终端支付结果收口。",
    recommendedScenes: "活动拉新、生活服务、桌面浏览器",
    fallbackAction: "若用户侧浏览器中断，可重新进入收银台并沿用当前支付单继续查单。"
  },
  银行卡: {
    icon: "BANK",
    description: "适合企业客户、大额支付和线下转账认领。",
    settlementHint: "大额订单建议保留付款凭证，便于运营复核。",
    recommendedScenes: "企业客户、大额补款、线下转账",
    fallbackAction: "若用户已转账但结果未收口，请保留凭证并联系运营核对渠道流水。"
  }
};

const BIZ_SCENE_META = {
  recharge: {
    label: "充值",
    title: "钱包充值支付",
    description: "面向会员储值、预存款补充和押金补缴场景，关注支付完成后的资金到账提示。",
    supportHint: "若支付成功但钱包未到账，优先核对支付结果、账务事件和钱包入账链路。"
  },
  withdraw: {
    label: "提现补款",
    title: "提现链路补款",
    description: "用于提现手续费补扣、银行卡校验补扣和提现失败重试支付场景。",
    supportHint: "若提现侧状态仍未推进，建议携带支付单号同步提现系统复核。"
  },
  transfer: {
    label: "转账补款",
    title: "转账补款支付",
    description: "用于商家钱包转账、服务者垫资补款和企业客户内部划转类支付。",
    supportHint: "若转账目标侧资金未变化，需同步核对支付结果与后续结算链路。"
  },
  "balance-pay": {
    label: "余额支付",
    title: "订单尾款支付",
    description: "用于订单尾款补缴、履约前二次支付和账户余额不足兜底支付场景。",
    supportHint: "若订单履约侧仍显示待支付，建议先主动查单，再回订单中心刷新支付状态。"
  }
};

const GENERIC_SCENE_META = {
  label: "业务支付",
  title: "统一支付收银台",
  description: "请核对当前业务订单与支付金额后再提交。",
  supportHint: "支付异常时请先查单，并联系原业务服务方核对订单状态。"
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
const cashier = ref(null);
const loading = ref(true);
const message = ref("");
const selectedPaymentMethod = ref("");
const submitLoading = ref(false);
const resetLoading = ref(false);
const refreshLoading = ref(false);
const closeLoading = ref(false);
const queryLoading = ref(false);
const idempotencyKey = ref("");
const pendingSubmitAttempt = ref(null);
const countdownSeconds = ref(0);
const latestQueriedDetail = ref(null);
const loadedPrepayOrderNo = ref("");
const cashierSnapshotStale = ref(false);

let countdownTimer = null;
let latestLoadRequestId = 0;
let currentEntityVersion = 0;

function buildResultRouteQuery(prepayOrderNo, paymentMethod) {
  const routeQuery = {
    prepayOrderNo,
    paymentMethod,
    terminalVariant: props.terminalVariant
  };
  const bizType = resolveBizType();
  if (bizType) {
    routeQuery.bizType = bizType;
  }
  if (expectedRoutePayScene.value) {
    routeQuery.payScene = expectedRoutePayScene.value;
  }
  return routeQuery;
}

function generateIdempotencyKey() {
  return generatePaymentSubmitKey();
}

function stopCountdown() {
  if (countdownTimer) {
    clearInterval(countdownTimer);
    countdownTimer = null;
  }
}

function startCountdown(expiresAt) {
  stopCountdown();
  if (!expiresAt) {
    countdownSeconds.value = 0;
    return;
  }
  const expireTimestamp = new Date(expiresAt.replace(" ", "T")).getTime();
  const updateCountdown = () => {
    const remainSeconds = Math.max(Math.floor((expireTimestamp - Date.now()) / 1000), 0);
    countdownSeconds.value = Number.isFinite(remainSeconds) ? remainSeconds : 0;
    if (remainSeconds <= 0) {
      stopCountdown();
    }
  };
  updateCountdown();
  countdownTimer = setInterval(updateCountdown, 1000);
}

async function loadCashier(prepayOrderNo = currentPrepayOrderNo.value) {
  const requestId = ++latestLoadRequestId;
  const expectedSnapshot = prepayOrderNo === loadedPrepayOrderNo.value && cashier.value
    ? buildCurrentPaymentSnapshot()
    : null;
  loading.value = true;
  message.value = "";
  try {
    syncAccessToken();
    const cashierPage = await paymentApi.getCashier(
      prepayOrderNo,
      terminalMeta.value.terminalCode,
      expectedRoutePayScene.value
    );
    if (requestId !== latestLoadRequestId || prepayOrderNo !== currentPrepayOrderNo.value) {
      return;
    }
    if (!matchesPrepayOrder(prepayOrderNo, cashierPage)) {
      throw new Error("收银台响应为空或与当前预付单不一致");
    }
    if (expectedSnapshot && !matchesPaymentSnapshot(expectedSnapshot, cashierPage)) {
      throw new Error("收银台响应为空或与当前支付单不一致");
    }
    let pendingAttempt;
    try {
      const savedAttempt = await readPaymentSubmitAttemptForPrepay(prepayOrderNo);
      pendingAttempt = prepayOrderNo === loadedPrepayOrderNo.value && pendingSubmitAttempt.value?.confirmed
        ? pendingSubmitAttempt.value
        : savedAttempt || (prepayOrderNo === loadedPrepayOrderNo.value ? pendingSubmitAttempt.value : null);
    } catch (error) {
      if (requestId !== latestLoadRequestId || prepayOrderNo !== currentPrepayOrderNo.value) {
        return;
      }
      if (error instanceof UnverifiablePaymentSubmitAttemptError
          && ["SUCCESS", "CLOSED"].includes(cashierPage.paymentStatus)) {
        router.push(buildPaymentResultRoute(
          cashierPage.paymentOrderId,
          buildResultRouteQuery(prepayOrderNo, cashierPage.paymentMethod || "")
        ));
        return;
      }
      throw error;
    }
    if (requestId !== latestLoadRequestId || prepayOrderNo !== currentPrepayOrderNo.value) {
      return;
    }
    cashier.value = cashierPage;
    pendingSubmitAttempt.value = pendingAttempt;
    cashierSnapshotStale.value = false;
    latestQueriedDetail.value = null;
    loadedPrepayOrderNo.value = prepayOrderNo;
    if (pendingAttempt) {
      idempotencyKey.value = pendingAttempt.idempotencyKey;
    } else if (!idempotencyKey.value) {
      idempotencyKey.value = generateIdempotencyKey();
    }
    selectedPaymentMethod.value = (cashierPage.paymentStatus === "RISK_REVIEW"
      ? cashierPage.reviewPaymentMethod
      : ["WAIT_CALLBACK", "SUCCESS"].includes(cashierPage.paymentStatus)
        ? cashierPage.paymentMethod : null) || (cashier.value?.channels || [])
      .find((paymentMethod) => terminalMeta.value.paymentMethods.includes(paymentMethod)) || "";
    if (pendingAttempt && (cashierPage.channels || []).includes(pendingAttempt.paymentMethod)) {
      selectedPaymentMethod.value = pendingAttempt.paymentMethod;
    }
    startCountdown(cashier.value?.expiresAt);
  } catch (error) {
    if (requestId !== latestLoadRequestId || prepayOrderNo !== currentPrepayOrderNo.value) {
      return;
    }
    cashierSnapshotStale.value = true;
    message.value = error.message;
  } finally {
    if (requestId === latestLoadRequestId) {
      loading.value = false;
    }
  }
}

onBeforeUnmount(stopCountdown);

const terminalMeta = computed(() => TERMINAL_META[props.terminalVariant] || TERMINAL_META.app);
const currentPrepayOrderNo = computed(() => typeof route.params.prepayOrderNo === "string"
  ? route.params.prepayOrderNo.trim()
  : "");
const hasValidCashierSnapshot = computed(() => Boolean(
  loadedPrepayOrderNo.value === currentPrepayOrderNo.value
  && matchesPrepayOrder(currentPrepayOrderNo.value, cashier.value)
));
const businessEntryPath = computed(() => resolvePaymentBusinessEntryPath(cashier.value?.payScene));
const bizSceneMeta = computed(() => BIZ_SCENE_META[businessEntryPath.value?.slice(1)] || GENERIC_SCENE_META);
const sourceAppId = computed(() => {
  if (props.terminalVariant === "pc") {
    return "housekeeping-pc-web";
  }
  if (props.terminalVariant === "h5") {
    return "housekeeping-h5-web";
  }
  return "housekeeping-app-web";
});
const merchantNo = computed(() => resolvePaymentMerchantNo(selectedPaymentMethod.value));
const accessTokenAvailable = ref(false);
const isPcVariant = computed(() => props.terminalVariant === "pc");
const actionRunning = computed(() => loading.value
  || submitLoading.value
  || resetLoading.value
  || refreshLoading.value
  || closeLoading.value
  || queryLoading.value);
const channels = computed(() => (cashier.value?.channels || [])
  .filter((paymentMethod) => terminalMeta.value.paymentMethods.includes(paymentMethod)));
const hasChannels = computed(() => channels.value.length > 0);
const selectedChannelMeta = computed(() => CHANNEL_META[selectedPaymentMethod.value] || null);
const selectedChannelCode = computed(() => resolvePaymentChannelCode(selectedPaymentMethod.value));
const reviewRetryAllowed = computed(() => channels.value.includes(selectedPaymentMethod.value)
  && canRetryReviewedPayment(cashier.value, selectedPaymentMethod.value));
const submissionPending = computed(() => {
  return isPaymentSubmissionPending(
    cashier.value?.status,
    latestQueriedDetail.value?.latestAttemptStatus || cashier.value?.latestAttemptStatus
  );
});
const channelTransactionConflict = computed(() => isChannelTransactionConflict(cashier.value?.latestAttemptStatus));
const canQueryPayment = computed(() => {
  const paymentSnapshot = latestQueriedDetail.value || cashier.value;
  const paymentStatus = latestQueriedDetail.value
    ? paymentSnapshot?.status
    : paymentSnapshot?.paymentStatus;
  return hasValidCashierSnapshot.value && !cashierSnapshotStale.value && canQueryPaymentChannel(
    paymentStatus,
    paymentSnapshot?.latestAttemptStatus,
    paymentSnapshot?.latestAttemptCompletedAt,
    paymentSnapshot?.channelTransactionNo,
    paymentSnapshot?.channel
  );
});
const queryUnavailableGuidance = computed(() => channelTransactionConflict.value
  ? "渠道流水归属冲突，需人工核查渠道原始事实与流水归属；勿主动查单、重复付款或关闭支付单。"
  : cashier.value?.paymentStatus === "PAYING" ? PAYING_SUBMIT_HINT
    : "当前不满足主动查单条件，请刷新收银台核对支付状态，未确认资金前勿重复付款。");
const statusClass = computed(() => {
  const statusType = cashier.value?.statusType;
  return statusType ? `status-${statusType}` : "status-info";
});
const canSubmit = computed(() => {
  const status = cashier.value?.status;
  const queriedStatus = latestQueriedDetail.value?.status;
  const attemptStatus = latestQueriedDetail.value?.latestAttemptStatus
    || cashier.value?.latestAttemptStatus;
  const hasAttemptFact = attemptStatus != null
    && (typeof attemptStatus !== "string" || Boolean(attemptStatus.trim() && attemptStatus.trim() !== "-"));
  const channelTransactionNo = latestQueriedDetail.value?.channelTransactionNo
    || cashier.value?.channelTransactionNo;
  const hasSubmittedChannel = channelTransactionNo != null
    && (typeof channelTransactionNo !== "string" || Boolean(channelTransactionNo.trim()));
  return hasValidCashierSnapshot.value
    && !cashierSnapshotStale.value
    && hasChannels.value
    && accessTokenAvailable.value
    && !submissionPending.value
    && !pendingSubmitAttempt.value?.confirmed
    && (!pendingSubmitAttempt.value
      || pendingSubmitAttempt.value.paymentMethod === selectedPaymentMethod.value)
    && (!queriedStatus || queriedStatus === "PREPAY_CREATED"
      || (queriedStatus === "RISK_REVIEW" && reviewRetryAllowed.value))
    && !hasAttemptFact
    && !hasSubmittedChannel
    && !isCashierExpired(status)
    && (status !== "待风控复核" || reviewRetryAllowed.value)
    && status !== "支付成功"
    && status !== "已关闭"
    && countdownSeconds.value > 0;
});
const submitBlockedHint = computed(() => {
  const status = cashier.value?.status;
  if (cashierSnapshotStale.value) {
    return "收银台状态未能重新核实，请先刷新收银台，避免重复提交。";
  }
  if (channelTransactionConflict.value) {
    return queryUnavailableGuidance.value;
  }
  if (pendingSubmitAttempt.value?.confirmed) {
    return "支付响应已返回，但本机未决记录未能清理；请查单核对原支付结果，勿重复付款。";
  }
  if (!accessTokenAvailable.value) {
    return "当前支付访问令牌未注入，请联系运营完成安全配置后再提交。";
  }
  if (isCashierExpired(status)) {
    return "当前收银台会话已失效，请重新拉起新的预付单。";
  }
  if (status === "待风控复核") {
    return "当前支付单等待风控复核；仅支持审核通过后沿原支付方式和渠道重试，请刷新收银台核对。";
  }
  if (status === "支付成功") {
    return "当前支付单已支付成功，无需再次提交。";
  }
  if (cashier.value?.paymentStatus === "PAYING") {
    return PAYING_SUBMIT_HINT;
  }
  if (submissionPending.value) {
    return canQueryPayment.value
      ? "当前支付结果尚未确认，请先主动查单。"
      : queryUnavailableGuidance.value;
  }
  if (status === "已关闭") {
    return "当前支付单已关闭，请重新发起支付。";
  }
  if (countdownSeconds.value <= 0) {
    return "当前收银台会话已过期，请关闭后重新拉起新的预付单。";
  }
  return "";
});
const desktopQrPlaceholderVisible = computed(() => {
  return canShowDesktopQrPlaceholder({
    terminalVariant: props.terminalVariant,
    hasValidCashierSnapshot: hasValidCashierSnapshot.value,
    canSubmit: canSubmit.value
  });
});
const countdownLabel = computed(() => {
  const hours = String(Math.floor(countdownSeconds.value / 3600)).padStart(2, "0");
  const minutes = String(Math.floor((countdownSeconds.value % 3600) / 60)).padStart(2, "0");
  const seconds = String(countdownSeconds.value % 60).padStart(2, "0");
  return `${hours}:${minutes}:${seconds}`;
});
const canClose = computed(() => hasValidCashierSnapshot.value
  && !cashierSnapshotStale.value
  && !pendingSubmitAttempt.value
  && Boolean(cashier.value?.paymentOrderId)
  && canClosePaymentLocally(
    cashier.value?.status,
    latestQueriedDetail.value?.channelTransactionNo || cashier.value?.channelTransactionNo,
    latestQueriedDetail.value?.latestAttemptStatus || cashier.value?.latestAttemptStatus
  ));
const countdownRiskLevel = computed(() => {
  if (countdownSeconds.value <= 0) {
    return "danger";
  }
  if (countdownSeconds.value <= 300) {
    return "warn";
  }
  return "info";
});
const countdownRiskHint = computed(() => {
  if (submissionPending.value && !canQueryPayment.value) {
    return queryUnavailableGuidance.value;
  }
  if (isCashierExpired(cashier.value?.status)) {
    return submissionPending.value
      ? "当前会话已失效，但渠道结果尚未确认，请继续主动查单。"
      : "当前会话已失效，可关闭尚未提交渠道的支付单后重新发起。";
  }
  if (countdownSeconds.value <= 0) {
    return submissionPending.value
      ? "当前会话已过期，但渠道结果尚未确认，请继续主动查单。"
      : "当前会话已过期，可关闭尚未提交渠道的支付单后重新发起。";
  }
  if (countdownSeconds.value <= 300) {
    return "当前会话剩余时间较短，建议尽快完成支付，避免支付中断后重新拉起。";
  }
  return "会话状态正常，可继续完成支付。";
});
const paymentReadinessChecklist = computed(() => {
  return [
    {
      title: "业务场景",
      detail: `${bizSceneMeta.value.label} / ${bizSceneMeta.value.title}`,
      statusType: "info"
    },
    {
      title: "支付对象",
      detail: `订单 ${cashier.value?.orderNo || "-"} / 账单 ${cashier.value?.billNo || "-"}`,
      statusType: cashier.value?.orderNo && cashier.value?.billNo ? "success" : "warn"
    },
    {
      title: "会话时效",
      detail: countdownRiskHint.value,
      statusType: countdownRiskLevel.value
    },
    {
      title: "支付方式",
      detail: `${selectedPaymentMethod.value || "-"} / ${selectedChannelCode.value || "-"}`,
      statusType: hasChannels.value ? "success" : "danger"
    }
  ];
});
const paymentMethodComparisonRows = computed(() => {
  return channels.value.map((item) => {
    const meta = CHANNEL_META[item] || {};
    return {
      paymentMethod: item,
      channelCode: resolvePaymentChannelCode(item),
      description: meta.description || "系统已接入该支付方式。",
      recommendedScenes: meta.recommendedScenes || "通用支付场景",
      fallbackAction: canQueryPayment.value
        ? meta.fallbackAction || "支付异常时先主动查单；仅未提交渠道的支付单可本地关闭。"
        : queryUnavailableGuidance.value
    };
  });
});

async function refreshCashier() {
  if (actionRunning.value) {
    return;
  }
  refreshLoading.value = true;
  try {
    await loadCashier(currentPrepayOrderNo.value);
    if (hasValidCashierSnapshot.value && !cashierSnapshotStale.value
        && ["SUCCESS", "CLOSED"].includes(cashier.value?.paymentStatus)) {
      router.push(buildPaymentResultRoute(
        cashier.value.paymentOrderId,
        buildResultRouteQuery(
          cashier.value.prepayOrderNo,
          cashier.value.paymentMethod || selectedPaymentMethod.value
        )
      ));
    }
  } finally {
    refreshLoading.value = false;
  }
}

async function readBackAfterFailedAction(snapshot, entityVersion, actionError) {
  if (!isCurrentPaymentSnapshot(snapshot, entityVersion)) {
    return;
  }
  cashierSnapshotStale.value = true;
  try {
    const cashierPage = await paymentApi.getCashier(
      snapshot.prepayOrderNo,
      terminalMeta.value.terminalCode,
      expectedRoutePayScene.value
    );
    if (!isCurrentPaymentSnapshot(snapshot, entityVersion)) {
      return;
    }
    if (!matchesPaymentSnapshot(snapshot, cashierPage)) {
      throw new Error("动作失败后的收银台与当前支付单不一致");
    }
    cashier.value = cashierPage;
    latestQueriedDetail.value = null;
    cashierSnapshotStale.value = false;
    startCountdown(cashierPage.expiresAt);
    message.value = `${actionError.message}；收银台已重新核实。`;
    if (cashierPage.paymentStatus === "SUCCESS" || cashierPage.paymentStatus === "CLOSED") {
      router.push(buildPaymentResultRoute(
        cashierPage.paymentOrderId,
        buildResultRouteQuery(
          cashierPage.prepayOrderNo,
          cashierPage.paymentMethod || selectedPaymentMethod.value
        )
      ));
    }
  } catch {
    if (isCurrentPaymentSnapshot(snapshot, entityVersion)) {
      message.value = `${actionError.message}；支付状态未经确认，请刷新收银台，勿重复付款。`;
    }
  }
}

async function queryCurrentPayment() {
  if (actionRunning.value) {
    return;
  }
  if (!hasValidCashierSnapshot.value || !cashier.value?.paymentOrderId) {
    message.value = "当前支付单尚未生成，请先发起支付后再执行主动查单。";
    return;
  }
  if (!canQueryPayment.value) {
    message.value = cashierSnapshotStale.value
      ? "收银台状态未经确认，请先刷新收银台。"
      : queryUnavailableGuidance.value;
    return;
  }
  const snapshot = buildCurrentPaymentSnapshot();
  const entityVersion = currentEntityVersion;
  queryLoading.value = true;
  message.value = "";
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
    latestQueriedDetail.value = detail;
    message.value = `已查询支付单 ${snapshot.paymentOrderId} 的最新状态：${detail.status}。`;
    if (detail.status === "SUCCESS" || detail.status === "CLOSED") {
      router.push(buildPaymentResultRoute(
        detail.paymentOrderId,
        buildResultRouteQuery(
          detail.prepayOrderNo,
          detail.paymentMethod || selectedPaymentMethod.value
        )
      ));
    }
  } catch (error) {
    await readBackAfterFailedAction(snapshot, entityVersion, error);
  } finally {
    if (entityVersion === currentEntityVersion) {
      queryLoading.value = false;
    }
  }
}

async function pay() {
  if (actionRunning.value) {
    return;
  }
  if (!hasValidCashierSnapshot.value || !cashier.value || !hasChannels.value) {
    message.value = "当前暂无可用支付方式，请稍后重试。";
    return;
  }
  if (!canSubmit.value) {
    message.value = submitBlockedHint.value || "当前状态暂不允许继续提交支付。";
    return;
  }
  const snapshot = buildCurrentPaymentSnapshot();
  const entityVersion = currentEntityVersion;
  const paymentMethod = selectedPaymentMethod.value;
  submitLoading.value = true;
  message.value = "";
  try {
    const submitPayload = {
      prepayOrderNo: snapshot.prepayOrderNo,
      paymentMethod,
      channelCode: resolvePaymentChannelCode(paymentMethod),
      sourceAppId: sourceAppId.value,
      merchantNo: merchantNo.value,
      accessToken: resolveAccessToken(),
      terminal: terminalMeta.value.terminalCode
    };
    const attempt = await resolvePaymentSubmitAttempt(submitPayload, idempotencyKey.value);
    if (!isCurrentPaymentSnapshot(snapshot, entityVersion)) {
      return;
    }
    idempotencyKey.value = attempt.idempotencyKey;
    pendingSubmitAttempt.value = attempt;
    const submitResult = await paymentApi.submit({
      ...submitPayload,
      idempotencyKey: attempt.idempotencyKey
    }, expectedRoutePayScene.value);
    if (!isCurrentPaymentSnapshot(snapshot, entityVersion)) {
      return;
    }
    if (!matchesPaymentSnapshot(snapshot, submitResult)) {
      throw new Error("支付提交响应为空或与当前支付单不一致");
    }
    pendingSubmitAttempt.value = { ...attempt, confirmed: true };
    clearPaymentSubmitAttempt(attempt);
    pendingSubmitAttempt.value = null;
    router.push(buildPaymentResultRoute(
      submitResult.paymentOrderId,
      buildResultRouteQuery(submitResult.prepayOrderNo, paymentMethod)
    ));
  } catch (error) {
    if (!isCurrentPaymentSnapshot(snapshot, entityVersion)) {
      return;
    }
    cashierSnapshotStale.value = true;
    if (error instanceof UnverifiablePaymentSubmitAttemptError) {
      message.value = error.message;
      return;
    }
    try {
      const cashierPage = await paymentApi.getCashier(
        snapshot.prepayOrderNo,
        terminalMeta.value.terminalCode,
        expectedRoutePayScene.value
      );
      if (!isCurrentPaymentSnapshot(snapshot, entityVersion)) {
        return;
      }
      if (!matchesPaymentSnapshot(snapshot, cashierPage)) {
        throw new Error("收银台刷新响应为空或与当前支付单不一致");
      }
      cashier.value = cashierPage;
      cashierSnapshotStale.value = false;
      startCountdown(cashierPage.expiresAt);
      if (cashierPage.paymentStatus === "SUCCESS" || cashierPage.paymentStatus === "CLOSED") {
        router.push(buildPaymentResultRoute(
          cashierPage.paymentOrderId,
          buildResultRouteQuery(cashierPage.prepayOrderNo, paymentMethod)
        ));
        return;
      }
    } catch (refreshError) {
      if (isCurrentPaymentSnapshot(snapshot, entityVersion)) {
        message.value = `${error.message}；${refreshError.message}；支付状态未经确认，请刷新收银台，勿重复付款。`;
      }
      return;
    }
    if (cashier.value?.paymentStatus === "PAYING") {
      message.value = queryUnavailableGuidance.value;
    } else {
      message.value = submissionPending.value
        ? canQueryPayment.value
          ? "支付结果尚未确认，当前请求已锁定，请先主动查单。"
          : queryUnavailableGuidance.value
        : error.message;
    }
  } finally {
    if (entityVersion === currentEntityVersion) {
      submitLoading.value = false;
    }
  }
}

async function closeCurrentPayment() {
  if (actionRunning.value) {
    return;
  }
  if (!hasValidCashierSnapshot.value || !cashier.value?.paymentOrderId || !canClose.value) {
    message.value = "当前支付单不满足本地关闭条件。";
    return;
  }
  const snapshot = buildCurrentPaymentSnapshot();
  const entityVersion = currentEntityVersion;
  closeLoading.value = true;
  message.value = "";
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
    message.value = "当前支付单已关闭，可回到订单中心重新发起支付。";
    await loadCashier(snapshot.prepayOrderNo);
  } catch (error) {
    await readBackAfterFailedAction(snapshot, entityVersion, error);
  } finally {
    if (entityVersion === currentEntityVersion) {
      closeLoading.value = false;
    }
  }
}

function buildCurrentPaymentSnapshot() {
  return {
    prepayOrderNo: loadedPrepayOrderNo.value,
    paymentOrderId: cashier.value?.paymentOrderId || "",
    businessChainId: cashier.value?.businessChainId || "",
    billNo: cashier.value?.billNo,
    orderNo: cashier.value?.orderNo || "",
    customerName: cashier.value?.customerName,
    payScene: cashier.value?.payScene,
    paymentMethod: selectedPaymentMethod.value,
    amount: cashier.value?.amount || ""
  };
}

function isCurrentPaymentSnapshot(snapshot, entityVersion) {
  return entityVersion === currentEntityVersion
    && hasValidCashierSnapshot.value
    && matchesPaymentSnapshot(snapshot, cashier.value);
}

async function regenerateIdempotencyKey() {
  if (actionRunning.value) {
    return;
  }
  if (cashierSnapshotStale.value) {
    message.value = "收银台状态未经确认，不能重置幂等键。";
    return;
  }
  if (submissionPending.value) {
    message.value = "支付结果尚未确认，不能重置幂等键。";
    return;
  }
  if (pendingSubmitAttempt.value) {
    message.value = "本次支付提交结果尚未核实，不能重置幂等键；请沿用原方式查单。";
    return;
  }
  const prepayOrderNo = currentPrepayOrderNo.value;
  const entityVersion = currentEntityVersion;
  resetLoading.value = true;
  try {
    await discardPaymentSubmitAttempt(prepayOrderNo);
    if (entityVersion !== currentEntityVersion || prepayOrderNo !== currentPrepayOrderNo.value) {
      return;
    }
    idempotencyKey.value = generateIdempotencyKey();
    message.value = "已重新生成本次支付的幂等键，可用于重新联调当前支付方式。";
  } catch (error) {
    if (error instanceof UnverifiablePaymentSubmitAttemptError
        && entityVersion === currentEntityVersion && prepayOrderNo === currentPrepayOrderNo.value) {
      message.value = error.message;
      return;
    }
    throw error;
  } finally {
    if (entityVersion === currentEntityVersion) {
      resetLoading.value = false;
    }
  }
}

function backToBusinessEntry() {
  if (businessEntryPath.value) {
    router.push(businessEntryPath.value);
  }
}

function resolveAccessToken() {
  return readPaymentAccessToken(sourceAppId.value);
}

function resolveBizType() {
  if (typeof route.query.bizType === "string" && route.query.bizType.trim()) {
    return route.query.bizType.trim();
  }
  return "balance-pay";
}

function syncAccessToken() {
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
  accessTokenAvailable.value = Boolean(resolveAccessToken());
}

watch(() => route.query.accessToken, syncAccessToken);

watch(selectedPaymentMethod, () => {
  if (!submissionPending.value && !pendingSubmitAttempt.value) {
    idempotencyKey.value = generateIdempotencyKey();
  }
});

watch([currentPrepayOrderNo, expectedRoutePayScene], ([prepayOrderNo]) => {
  currentEntityVersion += 1;
  latestLoadRequestId += 1;
  stopCountdown();
  cashier.value = null;
  cashierSnapshotStale.value = false;
  loadedPrepayOrderNo.value = "";
  latestQueriedDetail.value = null;
  selectedPaymentMethod.value = "";
  pendingSubmitAttempt.value = null;
  idempotencyKey.value = "";
  countdownSeconds.value = 0;
  message.value = "";
  submitLoading.value = false;
  resetLoading.value = false;
  refreshLoading.value = false;
  closeLoading.value = false;
  queryLoading.value = false;
  loadCashier(prepayOrderNo);
}, { immediate: true });
</script>

<template>
  <div class="terminal-page" :class="`terminal-${terminalVariant}`">
    <section class="terminal-hero">
      <div class="hero-copy">
        <div class="hero-label">{{ terminalMeta.heroLabel }}</div>
        <h1>{{ cashier?.title || bizSceneMeta.title }}</h1>
        <p>{{ bizSceneMeta.description }} {{ terminalMeta.heroHint }}</p>
      </div>
      <div class="hero-amount-card">
        <div class="mini-label">本次应付</div>
        <div class="hero-amount">{{ cashier?.amount || "¥0.00" }}</div>
        <div class="hero-status-row">
          <span class="status-pill" :class="statusClass">{{ cashier?.status || "待支付" }}</span>
          <span class="countdown-pill">剩余 {{ countdownLabel }}</span>
        </div>
      </div>
    </section>

    <div v-if="loading" class="terminal-card terminal-state-card">收银台加载中...</div>
    <div v-else-if="message && !cashier" class="terminal-card terminal-state-card">
      <p>{{ message }}</p>
      <button v-if="currentPrepayOrderNo" class="action-button secondary" @click="loadCashier()">重试加载收银台</button>
    </div>

    <div v-else class="terminal-grid">
      <section class="terminal-card">
        <div class="section-heading">
          <div>
            <h3>选择支付方式</h3>
            <p>系统会基于支付配置、支付场景和金额自动执行路由，以下为本次可用渠道。</p>
          </div>
          <span class="support-tag">多终端联调就绪</span>
        </div>

        <div v-if="!hasChannels" class="inline-warning">当前暂无可用支付方式，请联系运营检查支付渠道配置。</div>

        <div v-else class="channel-list">
          <button
            v-for="item in channels"
            :key="item"
            type="button"
            class="channel-card"
            :class="{ active: selectedPaymentMethod === item }"
            :disabled="actionRunning || submissionPending || pendingSubmitAttempt || cashierSnapshotStale || cashier?.paymentStatus === 'RISK_REVIEW'"
            @click="selectedPaymentMethod = item"
          >
            <div class="channel-icon">{{ CHANNEL_META[item]?.icon || "PAY" }}</div>
            <div class="channel-content">
              <strong>{{ item }}</strong>
              <span>{{ CHANNEL_META[item]?.description || "系统已接入该支付方式。" }}</span>
            </div>
            <div class="channel-check">{{ selectedPaymentMethod === item ? "已选中" : "可使用" }}</div>
          </button>
        </div>

        <div v-if="selectedChannelMeta" class="channel-tips">
          <div>
            <strong>业务说明</strong>
            <p>{{ canQueryPayment ? bizSceneMeta.supportHint : queryUnavailableGuidance }}</p>
          </div>
          <div>
            <strong>渠道说明</strong>
            <p>{{ selectedChannelMeta.description }}</p>
          </div>
          <div>
            <strong>结算提示</strong>
            <p>{{ canQueryPayment ? selectedChannelMeta.settlementHint : queryUnavailableGuidance }}</p>
          </div>
          <div>
            <strong>失败补救</strong>
            <p>{{ canQueryPayment ? selectedChannelMeta.fallbackAction : queryUnavailableGuidance }}</p>
          </div>
        </div>

        <div class="terminal-actions">
          <button class="action-button primary" :disabled="actionRunning || !canSubmit" @click="pay">
            {{ submitLoading ? "支付提交流转中..." : channelTransactionConflict ? "人工核查中" : reviewRetryAllowed ? "审核通过后继续原支付" : terminalMeta.actionLabel }}
          </button>
          <button v-if="businessEntryPath" class="action-button secondary" :disabled="actionRunning" @click="backToBusinessEntry">
            返回业务入口
          </button>
          <button class="action-button secondary" :disabled="actionRunning" @click="refreshCashier">
            {{ refreshLoading ? "刷新中..." : terminalMeta.refreshLabel }}
          </button>
          <button class="action-button secondary" :disabled="actionRunning || !cashier?.paymentOrderId || !canQueryPayment" @click="queryCurrentPayment">
            {{ queryLoading ? "查单中..." : channelTransactionConflict ? "人工核查中" : "主动查单" }}
          </button>
          <button class="action-button ghost" :disabled="actionRunning || !canClose" @click="closeCurrentPayment">
            {{ closeLoading ? "关闭中..." : terminalMeta.closeLabel }}
          </button>
          <button class="action-button secondary" :disabled="actionRunning || submissionPending || pendingSubmitAttempt || cashierSnapshotStale" @click="regenerateIdempotencyKey">
            重置幂等键
          </button>
        </div>
        <p v-if="!businessEntryPath && hasValidCashierSnapshot" class="feedback-text">当前场景暂无对应的统一支付入口，请从原业务订单页返回。</p>

        <p v-if="message" class="feedback-text">{{ message }}</p>
        <p v-else-if="submitBlockedHint" class="feedback-text">{{ submitBlockedHint }}</p>
        <p v-if="cashierSnapshotStale" class="feedback-text">当前为上次已加载的收银台快照，重新核实成功前不可提交、查单或本地关单。</p>

        <div class="terminal-ops-grid">
          <div class="ops-card">
            <div class="ops-title">支付前检查</div>
            <div v-for="item in paymentReadinessChecklist" :key="item.title" class="ops-row">
              <span>{{ item.title }}</span>
              <span :class="['status-pill', `status-${item.statusType}`]">{{ item.detail }}</span>
            </div>
          </div>
          <div class="ops-card">
            <div class="ops-title">会话风险提示</div>
            <div class="ops-row">
              <span>当前倒计时</span>
              <span :class="['status-pill', `status-${countdownRiskLevel}`]">{{ countdownLabel }}</span>
            </div>
            <p class="ops-description">{{ countdownRiskHint }}</p>
          </div>
        </div>

        <div v-if="isPcVariant" class="desktop-payment-grid">
          <div class="desktop-qr-card">
            <div class="desktop-qr-header">
              <div>
                <strong>桌面端渠道支付区</strong>
                <p>当前仅展示联调占位，不是支付凭证，不可扫码付款。</p>
              </div>
              <span class="support-tag">PC 扫码模式</span>
            </div>
            <div v-if="desktopQrPlaceholderVisible" class="desktop-qr-frame" aria-label="不可支付的联调占位">
              <div class="desktop-qr-placeholder">
                <strong>联调占位</strong>
                <span>不可扫码支付</span>
              </div>
            </div>
            <div v-else class="desktop-qr-unavailable">{{ queryUnavailableGuidance }}</div>
            <div class="desktop-qr-meta">
              <div><span>扫码方式</span><strong>{{ selectedPaymentMethod }}</strong></div>
              <div><span>渠道编码</span><strong>{{ resolvePaymentChannelCode(selectedPaymentMethod) }}</strong></div>
              <div><span>支付单号</span><strong class="mono-text">{{ cashier?.paymentOrderId || "-" }}</strong></div>
            </div>
          </div>

          <div class="desktop-hint-grid">
            <div class="desktop-hint-card">
              <strong>桌面端支付建议</strong>
              <p>{{ canQueryPayment ? "渠道受理后若结果迟迟未收口，可在当前页面主动查单。" : queryUnavailableGuidance }}</p>
            </div>
            <div class="desktop-hint-card">
              <strong>失败补救动作</strong>
              <p>{{ canQueryPayment ? "如浏览器关闭、扫码超时或渠道报错，先主动查单核实；仅未提交渠道的支付单可本地关闭。" : queryUnavailableGuidance }}</p>
            </div>
            <div class="desktop-hint-card">
              <strong>联查建议</strong>
              <p>若支付状态与后台不一致，请记录支付单号和预付单号，先核对支付请求，再查看回调与事件轨迹。</p>
            </div>
          </div>
        </div>
      </section>

      <section class="terminal-card">
        <div class="section-heading">
          <div>
            <h3>订单与支付信息</h3>
            <p>确认本次支付对象、会话状态和后续联调所需的关键编号。</p>
          </div>
        </div>

        <div class="summary-grid">
          <div class="summary-item">
            <span>订单号</span>
            <strong>{{ cashier?.orderNo || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>账单号</span>
            <strong>{{ cashier?.billNo || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>预付单号</span>
            <strong>{{ cashier?.prepayOrderNo || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>支付单号</span>
            <strong>{{ cashier?.paymentOrderId || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>客户名称</span>
            <strong>{{ cashier?.customerName || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>支付场景</span>
            <strong>{{ cashier?.payScene || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>会话状态</span>
            <strong>{{ cashier?.status || "-" }}</strong>
          </div>
          <div class="summary-item">
            <span>到期时间</span>
            <strong>{{ cashier?.expiresAt || "-" }}</strong>
          </div>
        </div>

        <div class="ops-card">
          <div class="ops-title">联调留痕</div>
          <div class="ops-row"><span>终端标识</span><span>{{ terminalMeta.terminalCode }}</span></div>
          <div class="ops-row"><span>商户号</span><span>{{ merchantNo }}</span></div>
          <div class="ops-row"><span>访问令牌</span><span>{{ accessTokenAvailable ? "已注入" : "未注入" }}</span></div>
          <div class="ops-row"><span>幂等键</span><span class="mono-text">{{ idempotencyKey || "-" }}</span></div>
          <div class="ops-row"><span>可选渠道</span><span>{{ channels.join(" / ") || "-" }}</span></div>
        </div>

        <div class="ops-card">
          <div class="ops-title">主动查单快照</div>
          <div class="ops-row">
            <span>当前支付单</span>
            <span class="mono-text">{{ cashier?.paymentOrderId || "-" }}</span>
          </div>
          <div class="ops-row">
            <span>最近查单状态</span>
            <span :class="['status-pill', `status-${latestQueriedDetail?.statusType || 'info'}`]">
              {{ latestQueriedDetail?.status || "未查单" }}
            </span>
          </div>
          <div class="ops-row"><span>查单来源</span><span>{{ latestQueriedDetail?.querySource || "-" }}</span></div>
          <div class="ops-row"><span>渠道流水号</span><span class="mono-text">{{ latestQueriedDetail?.channelTransactionNo || cashier?.channelTransactionNo || "-" }}</span></div>
          <div class="ops-row"><span>渠道返回码</span><span class="mono-text">{{ latestQueriedDetail?.channelReturnCode || "-" }}</span></div>
          <div class="ops-row"><span>标准错误码</span><span class="mono-text">{{ latestQueriedDetail?.standardizedCode || "-" }}</span></div>
          <div class="ops-row"><span>映射版本</span><span>{{ latestQueriedDetail?.returnCodeMappingVersion || "-" }}</span></div>
          <div class="ops-row"><span>处理建议</span><span>{{ latestQueriedDetail?.handlingSuggestion || "-" }}</span></div>
          <div class="ops-row"><span>最近尝试状态</span><span>{{ latestQueriedDetail?.latestAttemptStatus || "-" }}</span></div>
          <div class="ops-row"><span>最近尝试完成时间</span><span>{{ latestQueriedDetail?.latestAttemptCompletedAt || cashier?.latestAttemptCompletedAt || "-" }}</span></div>
          <p class="ops-description">
            {{ canQueryPayment ? "扫码后若前端仍停留在支付中，可先在当前收银台执行主动查单；若已成功或已关闭，会自动跳转结果页继续收口。" : queryUnavailableGuidance }}
          </p>
        </div>

        <div v-if="paymentMethodComparisonRows.length" class="ops-card">
          <div class="ops-title">支付方式对比与补救建议</div>
          <div v-for="item in paymentMethodComparisonRows" :key="item.paymentMethod" class="comparison-card">
            <div class="comparison-header">
              <strong>{{ item.paymentMethod }}</strong>
              <span class="mono-text">{{ item.channelCode }}</span>
            </div>
            <p>{{ item.description }}</p>
            <div class="comparison-meta">
              <span>推荐场景：{{ item.recommendedScenes }}</span>
              <span>补救建议：{{ item.fallbackAction }}</span>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>
