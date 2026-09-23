const SOURCE_APP_ID = "housekeeping-mini-program";
const DEMO_ACCESS_TOKEN = "token-housekeeping-mini-program";
const PAYMENT_METHOD = "微信支付";
const CHANNEL_CODE = "WX_H5";
const MERCHANT_NO = "MCH_WX_10001";
const TERMINAL = "MINI_PROGRAM";
const PAYMENT_DETAIL_REFRESH_INTERVAL_MS = 5000;
const PAYMENT_DETAIL_REFRESH_MAX_ATTEMPTS = 60;

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
  const normalized = Number(text);
  if (!Number.isFinite(normalized) || normalized <= 0) {
    return "";
  }
  return normalized.toFixed(2);
}

function resolveScene(sceneKey) {
  return SCENES[sceneKey] || SCENES["balance-pay"];
}

function createOrderNo(scene, timestamp) {
  const resolvedScene = resolveScene(scene && scene.key ? scene.key : scene);
  return `${resolvedScene.orderPrefix}-${timestamp || Date.now()}`;
}

function createIdempotencyKey(prepayOrderNo, timestamp, randomPart) {
  const suffix = randomPart || Math.random().toString(16).slice(2);
  return `MINI-${prepayOrderNo}-${timestamp || Date.now()}-${suffix}`;
}

function canQueryPayment(status, latestAttemptStatus) {
  if (["SUCCESS", "CLOSED"].includes(status)) {
    return true;
  }
  const attemptStatus = typeof latestAttemptStatus === "string" ? latestAttemptStatus.trim() : "";
  return status === "WAIT_CALLBACK" && Boolean(attemptStatus && attemptStatus !== "-");
}

function resolveRemainingSeconds(expiresAt, now) {
  if (!expiresAt) {
    return 0;
  }
  const expiresTimestamp = new Date(String(expiresAt).replace(" ", "T")).getTime();
  const currentTimestamp = now == null ? Date.now() : now;
  if (!Number.isFinite(expiresTimestamp) || !Number.isFinite(currentTimestamp)) {
    return 0;
  }
  return Math.max(0, Math.floor((expiresTimestamp - currentTimestamp) / 1000));
}

function canSubmitPayment(cashier, now) {
  if (!cashier || !Array.isArray(cashier.channels) || !cashier.channels.includes(PAYMENT_METHOD)) {
    return false;
  }
  const attemptStatus = typeof cashier.latestAttemptStatus === "string"
    ? cashier.latestAttemptStatus.trim()
    : "";
  return cashier.status === "待支付"
    && cashier.paymentStatus === "PREPAY_CREATED"
    && (!attemptStatus || attemptStatus === "-")
    && resolveRemainingSeconds(cashier.expiresAt, now) > 0;
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

function canClosePayment(status, channelTransactionNo, latestAttemptStatus) {
  const closeableStatuses = ["PREPAY_CREATED", "RISK_REVIEW", "RISK_BLOCKED", "RISK_REJECTED"];
  const attemptStatus = typeof latestAttemptStatus === "string" ? latestAttemptStatus.trim() : "";
  return closeableStatuses.includes(status)
    && !channelTransactionNo
    && (!attemptStatus || attemptStatus === "-");
}

function shouldAutoRefreshPaymentDetail(status) {
  if (typeof status !== "string") {
    return false;
  }
  return ["WAIT_CALLBACK", "PAYING", "RISK_REVIEW"].includes(status.trim().toUpperCase());
}

function resolvePaymentDetailRefreshDelay(status, completedAttempts) {
  if (!Number.isInteger(completedAttempts)
      || completedAttempts < 0
      || completedAttempts >= PAYMENT_DETAIL_REFRESH_MAX_ATTEMPTS
      || !shouldAutoRefreshPaymentDetail(status)) {
    return null;
  }
  return PAYMENT_DETAIL_REFRESH_INTERVAL_MS;
}

function resultMeta(status) {
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
  return { title: "支付处理中", tone: "warn", hint: "请等待渠道回调，超时后可主动查单。" };
}

module.exports = {
  CHANNEL_CODE,
  DEMO_ACCESS_TOKEN,
  MERCHANT_NO,
  PAYMENT_DETAIL_REFRESH_INTERVAL_MS,
  PAYMENT_DETAIL_REFRESH_MAX_ATTEMPTS,
  PAYMENT_METHOD,
  SCENES,
  SOURCE_APP_ID,
  TERMINAL,
  canClosePayment,
  canQueryPayment,
  canSubmitPayment,
  createIdempotencyKey,
  createOrderNo,
  normalizeAmount,
  normalizeNativePaymentParams,
  requestNativePayment,
  resolvePaymentDetailRefreshDelay,
  resolveRemainingSeconds,
  resolveScene,
  resultMeta,
  shouldAutoRefreshPaymentDetail
};
