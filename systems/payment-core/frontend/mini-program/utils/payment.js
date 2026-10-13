const SOURCE_APP_ID = "housekeeping-mini-program";
const PAYMENT_METHOD = "微信支付";
const CHANNEL_CODE = "WX_H5";
const MERCHANT_NO = "MCH_WX_10001";
const TERMINAL = "MINI_PROGRAM";
const PAYMENT_DETAIL_REFRESH_INTERVAL_MS = 5000;
const PAYMENT_DETAIL_REFRESH_MAX_ATTEMPTS = 60;
const CHANNEL_TRANSACTION_CONFLICT_STATUS = "渠道流水归属冲突，待人工核查";

const SCENES = {
  recharge: {
    key: "recharge",
    label: "充值",
    title: "钱包充值",
    amountLabel: "充值金额",
    defaultAmount: "99.00",
    defaultCustomerName: "张女士",
    orderPrefix: "WALLET-RECHARGE-MINI",
    payScene: "WALLET_RECHARGE_MINI_PROGRAM"
  },
  withdraw: {
    key: "withdraw",
    label: "提现",
    title: "提现补款",
    amountLabel: "补款金额",
    defaultAmount: "25.00",
    defaultCustomerName: "王先生",
    orderPrefix: "WITHDRAW-REPAY-MINI",
    payScene: "WITHDRAW_REPAY_MINI_PROGRAM"
  },
  transfer: {
    key: "transfer",
    label: "转账",
    title: "转账补款",
    amountLabel: "转账金额",
    defaultAmount: "300.00",
    defaultCustomerName: "企业客户-晨星科技",
    orderPrefix: "ACCOUNT-TRANSFER-MINI",
    payScene: "ACCOUNT_TRANSFER_MINI_PROGRAM"
  },
  "balance-pay": {
    key: "balance-pay",
    label: "余额支付",
    title: "订单尾款支付",
    amountLabel: "支付金额",
    defaultAmount: "68.00",
    defaultCustomerName: "张女士",
    orderPrefix: "BALANCE-PAY-MINI",
    payScene: "BALANCE_PAY_MINI_PROGRAM"
  }
};

function normalizeAmount(value) {
  const text = String(value == null ? "" : value).trim();
  if (!/^(0|[1-9]\d{0,15})(\.\d{1,2})?$/.test(text)) {
    return "";
  }
  const [integerPart, fractionPart] = text.split(".");
  const normalizedInteger = (integerPart || "").replace(/^0+/, "") || "0";
  const normalizedFraction = (fractionPart || "").padEnd(2, "0");
  if (normalizedInteger === "0" && normalizedFraction === "00") {
    return "";
  }
  return `${normalizedInteger}.${normalizedFraction}`;
}

function resolveScene(sceneKey) {
  return typeof sceneKey === "string" && Object.prototype.hasOwnProperty.call(SCENES, sceneKey)
    ? SCENES[sceneKey]
    : SCENES["balance-pay"];
}

function resolveLinkedScene(sceneKey, payScene = null) {
  const scene = sceneKey == null
    ? SCENES["balance-pay"]
    : typeof sceneKey === "string" && Object.prototype.hasOwnProperty.call(SCENES, sceneKey.trim())
      ? SCENES[sceneKey.trim()]
      : null;
  return scene && (payScene == null || payScene === scene.payScene)
    ? scene
    : { key: "", title: "未识别业务", payScene: "" };
}

function resolvePaymentAccessToken() {
  if (typeof getApp !== "function") {
    return "";
  }
  const app = getApp();
  const token = app && app.globalData ? app.globalData.paymentAccessToken : "";
  return typeof token === "string" ? token.trim() : "";
}

function createOrderNo(scene, timestamp) {
  const resolvedScene = resolveScene(scene && scene.key ? scene.key : scene);
  return `${resolvedScene.orderPrefix}-${timestamp || Date.now()}`;
}

function createIdempotencyKey(prepayOrderNo) {
  const normalized = typeof prepayOrderNo === "string" ? prepayOrderNo.trim() : "";
  return normalized ? `MINI-${normalized}` : "";
}

function isChannelTransactionConflict(latestAttemptStatus) {
  return latestAttemptStatus === CHANNEL_TRANSACTION_CONFLICT_STATUS;
}

function canQueryPayment(status, latestAttemptStatus, latestAttemptCompletedAt, channelTransactionNo, channel) {
  if (isChannelTransactionConflict(latestAttemptStatus)) {
    return false;
  }
  if (status === "SUCCESS") {
    return true;
  }
  const attemptStatus = typeof latestAttemptStatus === "string" ? latestAttemptStatus.trim() : "";
  if (status === "WAIT_CALLBACK" && attemptStatus === "渠道提交中"
      && !(typeof channelTransactionNo === "string"
        ? channelTransactionNo.trim() : channelTransactionNo)) {
    return false;
  }
  if (status === "CLOSED" && !attemptStatus && !channelTransactionNo) {
    return true;
  }
  return ["WAIT_CALLBACK", "CLOSED"].includes(status)
    && Boolean(attemptStatus && attemptStatus !== "-")
    && !["成功", "支付成功", "失败", "支付失败", "SUCCESS", "FAIL", "FAILED"]
      .includes(attemptStatus.toUpperCase())
    && typeof channel === "string" && Boolean(channel.trim())
    && latestAttemptCompletedAt == null;
}

function resolveRemainingSeconds(expiresAt, now) {
  if (!isValidPaymentDateTime(expiresAt)) {
    return 0;
  }
  const expiresTimestamp = Date.parse(`${expiresAt.trim().replace(" ", "T")}+08:00`);
  const currentTimestamp = now == null ? Date.now() : now;
  if (!Number.isFinite(expiresTimestamp) || !Number.isFinite(currentTimestamp)) {
    return 0;
  }
  return Math.max(0, Math.floor((expiresTimestamp - currentTimestamp) / 1000));
}

function isValidPaymentDateTime(value) {
  if (typeof value !== "string") {
    return false;
  }
  const match = /^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})$/.exec(value.trim());
  if (!match) {
    return false;
  }
  const [, year, month, day, hour, minute, second] = match.map(Number);
  const date = new Date(0);
  date.setUTCFullYear(year, month - 1, day);
  date.setUTCHours(hour, minute, second, 0);
  return date.getUTCFullYear() === year
    && date.getUTCMonth() === month - 1
    && date.getUTCDate() === day
    && date.getUTCHours() === hour
    && date.getUTCMinutes() === minute
    && date.getUTCSeconds() === second;
}

function canSubmitPayment(cashier, now) {
  if (!cashier || !Array.isArray(cashier.channels) || !cashier.channels.includes(PAYMENT_METHOD)) {
    return false;
  }
  const attemptStatus = typeof cashier.latestAttemptStatus === "string"
    ? cashier.latestAttemptStatus.trim()
    : "";
  const hasAttemptFact = cashier.latestAttemptStatus != null
    && (typeof cashier.latestAttemptStatus !== "string" || Boolean(attemptStatus && attemptStatus !== "-"));
  const hasSubmittedChannel = cashier.channelTransactionNo != null
    && (typeof cashier.channelTransactionNo !== "string" || Boolean(cashier.channelTransactionNo.trim()));
  const originalReview = cashier.status === "待风控复核"
    && cashier.paymentStatus === "RISK_REVIEW"
    && cashier.reviewPaymentMethod === PAYMENT_METHOD
    && typeof cashier.reviewChannelCode === "string"
    && cashier.reviewChannelCode.toLowerCase() === CHANNEL_CODE.toLowerCase();
  return ((cashier.status === "待支付" && cashier.paymentStatus === "PREPAY_CREATED") || originalReview)
    && !hasAttemptFact
    && !hasSubmittedChannel
    && resolveRemainingSeconds(cashier.expiresAt, now) > 0;
}

function resolveCashierStatusClass(statusType) {
  if (statusType === "success") {
    return "status-success";
  }
  if (statusType === "danger") {
    return "status-danger";
  }
  return "status-warn";
}

function normalizeNativePaymentParams(payload) {
  const source = payload && (payload.nativePaymentParams || payload.paymentParams);
  if (!source || typeof source !== "object") {
    return null;
  }
  const requiredFields = ["timeStamp", "nonceStr", "package", "signType", "paySign"];
  if (requiredFields.some((field) => typeof source[field] !== "string" || !source[field].trim())) {
    return null;
  }
  if (!/^\d+$/.test(source.timeStamp.trim())
      || !/^prepay_id=.+$/.test(source.package.trim())
      || !["RSA", "HMAC-SHA256"].includes(source.signType.trim())) {
    return null;
  }
  return requiredFields.reduce((params, field) => {
    params[field] = source[field].trim();
    return params;
  }, {});
}

function requestNativePayment(payload) {
  const params = normalizeNativePaymentParams(payload);
  if (!params) {
    return Promise.resolve({ invoked: false });
  }
  if (typeof wx === "undefined" || typeof wx.requestPayment !== "function") {
    const error = new Error("当前小程序环境不支持微信原生支付");
    error.code = "PAYMENT-MINI-NATIVE-UNAVAILABLE";
    return Promise.reject(error);
  }
  return new Promise((resolve, reject) => {
    wx.requestPayment({
      ...params,
      success: () => resolve({ invoked: true }),
      fail: (failure) => {
        const errorMessage = failure && failure.errMsg ? failure.errMsg : "微信原生支付未完成";
        const error = new Error(errorMessage);
        error.code = /cancel|取消/i.test(errorMessage)
          ? "PAYMENT-MINI-NATIVE-CANCEL"
          : "PAYMENT-MINI-NATIVE-FAILED";
        reject(error);
      }
    });
  });
}

function canRedirectAfterNativePayment(result) {
  return Boolean(result && result.invoked === true);
}

function normalizeIdentifier(value) {
  return typeof value === "string" ? value.trim() : "";
}

function matchesPreservedText(expected, actual) {
  return expected == null
    || actual == null
    || normalizeIdentifier(expected) === normalizeIdentifier(actual);
}

function matchesPaymentSubmission(
  prepayOrderNo, paymentOrderId, orderNo, result, expectedAmount, expectedBillNo, expectedCustomerName,
  expectedPaymentMethod, expectedPayScene
) {
  const normalizedPrepayOrderNo = normalizeIdentifier(prepayOrderNo);
  const normalizedPaymentOrderId = normalizeIdentifier(paymentOrderId);
  const normalizedOrderNo = normalizeIdentifier(orderNo);
  const resultPrepayOrderNo = normalizeIdentifier(result && result.prepayOrderNo);
  const resultPaymentOrderId = normalizeIdentifier(result && result.paymentOrderId);
  const resultOrderNo = normalizeIdentifier(result && result.orderNo);
  const resultBusinessChainId = normalizeIdentifier(result && result.businessChainId);
  const normalizedExpectedAmount = expectedAmount == null ? "" : normalizeAmount(expectedAmount);
  const normalizedResultAmount = result && normalizeAmount(result.amount);
  return Boolean(
    normalizedPrepayOrderNo
    && normalizedPaymentOrderId
    && normalizedOrderNo
    && result && typeof result === "object" && !Array.isArray(result)
    && resultPrepayOrderNo === normalizedPrepayOrderNo
    && resultPaymentOrderId === normalizedPaymentOrderId
    && resultBusinessChainId === `CHAIN-${normalizedPaymentOrderId}`
    && resultOrderNo === normalizedOrderNo
    && matchesPreservedText(expectedBillNo, result && result.billNo)
    && matchesPreservedText(expectedCustomerName, result && result.customerName)
    && matchesPreservedText(expectedPaymentMethod, result && result.paymentMethod)
    && (expectedPayScene == null
      || normalizeIdentifier(result && result.payScene) === normalizeIdentifier(expectedPayScene))
    && (!normalizedExpectedAmount || normalizedResultAmount === normalizedExpectedAmount)
  );
}

function matchesCashierSnapshot(prepayOrderNo, snapshot, cashier, expectedPayScene) {
  const normalizedPrepayOrderNo = normalizeIdentifier(prepayOrderNo);
  const snapshotPaymentOrderId = normalizeIdentifier(snapshot && snapshot.paymentOrderId);
  const snapshotBusinessChainId = normalizeIdentifier(snapshot && snapshot.businessChainId);
  const snapshotOrderNo = normalizeIdentifier(snapshot && snapshot.orderNo);
  const cashierPrepayOrderNo = normalizeIdentifier(cashier && cashier.prepayOrderNo);
  const cashierPaymentOrderId = normalizeIdentifier(cashier && cashier.paymentOrderId);
  const cashierBusinessChainId = normalizeIdentifier(cashier && cashier.businessChainId);
  const cashierOrderNo = normalizeIdentifier(cashier && cashier.orderNo);
  return Boolean(
    normalizedPrepayOrderNo
    && snapshot && cashier
    && cashierPrepayOrderNo === normalizedPrepayOrderNo
    && cashierPaymentOrderId === snapshotPaymentOrderId
    && snapshotBusinessChainId === `CHAIN-${snapshotPaymentOrderId}`
    && cashierBusinessChainId === snapshotBusinessChainId
    && cashierOrderNo === snapshotOrderNo
    && matchesPreservedText(snapshot && snapshot.billNo, cashier && cashier.billNo)
    && matchesPreservedText(snapshot && snapshot.customerName, cashier && cashier.customerName)
    && (expectedPayScene == null
      || normalizeIdentifier(cashier && cashier.payScene) === normalizeIdentifier(expectedPayScene))
    && normalizeAmount(cashier.amount) === normalizeAmount(snapshot.amount)
  );
}

function canClosePayment(status, channelTransactionNo, latestAttemptStatus) {
  const closeableStatuses = ["PREPAY_CREATED", "RISK_REVIEW", "RISK_BLOCKED", "RISK_REJECTED"];
  const attemptStatus = typeof latestAttemptStatus === "string" ? latestAttemptStatus.trim() : "";
  return closeableStatuses.includes(status)
    && !channelTransactionNo
    && (!attemptStatus || attemptStatus === "-");
}

function shouldAutoRefreshPaymentDetail(status, latestAttemptStatus) {
  if (typeof status !== "string") {
    return false;
  }
  return !isChannelTransactionConflict(latestAttemptStatus)
    && ["WAIT_CALLBACK", "PAYING", "RISK_REVIEW"].includes(status.trim().toUpperCase());
}

function resolvePaymentDetailRefreshDelay(status, completedAttempts, latestAttemptStatus) {
  if (!Number.isInteger(completedAttempts)
      || completedAttempts < 0
      || completedAttempts >= PAYMENT_DETAIL_REFRESH_MAX_ATTEMPTS
      || !shouldAutoRefreshPaymentDetail(status, latestAttemptStatus)) {
    return null;
  }
  return PAYMENT_DETAIL_REFRESH_INTERVAL_MS;
}

function resultMeta(status, latestAttemptStatus) {
  if (status === "PAYING" && isChannelTransactionConflict(latestAttemptStatus)) {
    return {
      title: "渠道流水归属待核查",
      tone: "danger",
      hint: "需人工核对渠道原始事实与流水归属；勿重复付款、主动查单或关闭支付单。可手动刷新结果。"
    };
  }
  if (status === "SUCCESS") {
    return { title: "支付成功", tone: "success", hint: "支付结果已经收口，可返回业务入口。" };
  }
  if (status === "CLOSED") {
    return { title: "支付已关闭", tone: "danger", hint: "如仍需支付，请重新创建预付单。" };
  }
  if (status === "RISK_REVIEW") {
    return { title: "待风控复核", tone: "warn", hint: "请等待运营或风控完成复核。" };
  }
  if (["RISK_BLOCKED", "RISK_REJECTED"].includes(status)) {
    return { title: "支付未放行", tone: "danger", hint: "请调整交易信息或联系运营核查风险原因。" };
  }
  if (status === "PREPAY_CREATED") {
    return { title: "待发起支付", tone: "warn", hint: "预付单已创建，尚未提交支付渠道。" };
  }
  if (status === "PAYING") {
    return {
      title: "支付提交处理中",
      tone: "info",
      hint: "渠道受理尚未确认，请刷新支付详情并等待状态推进，勿重复付款；当前不可主动查单或关单。"
    };
  }
  if (status === "WAIT_CALLBACK") {
    return { title: "支付处理中", tone: "warn", hint: "请等待渠道回调，超时后可主动查单。" };
  }
  return {
    title: "支付结果待核查",
    tone: status === "FAIL" ? "danger" : "warn",
    hint: "当前支付结果尚未确认，请勿重复付款；请刷新支付详情或联系服务方核查。"
  };
}

module.exports = {
  CHANNEL_CODE,
  MERCHANT_NO,
  PAYMENT_DETAIL_REFRESH_INTERVAL_MS,
  PAYMENT_DETAIL_REFRESH_MAX_ATTEMPTS,
  PAYMENT_METHOD,
  SCENES,
  SOURCE_APP_ID,
  TERMINAL,
  canClosePayment,
  canQueryPayment,
  canRedirectAfterNativePayment,
  canSubmitPayment,
  matchesPaymentSubmission,
  matchesCashierSnapshot,
  createIdempotencyKey,
  createOrderNo,
  isChannelTransactionConflict,
  normalizeAmount,
  normalizeNativePaymentParams,
  requestNativePayment,
  resolvePaymentDetailRefreshDelay,
  resolvePaymentAccessToken,
  resolveLinkedScene,
  resolveRemainingSeconds,
  resolveScene,
  resolveCashierStatusClass,
  resultMeta,
  shouldAutoRefreshPaymentDetail
};
