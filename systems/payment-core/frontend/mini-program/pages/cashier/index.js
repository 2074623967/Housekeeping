const {
  CHANNEL_CODE,
  MERCHANT_NO,
  PAYMENT_METHOD,
  SOURCE_APP_ID,
  TERMINAL,
  canSubmitPayment,
  canRedirectAfterNativePayment,
  createIdempotencyKey,
  matchesCashierSnapshot,
  matchesPaymentSubmission,
  requestNativePayment,
  resolvePaymentAccessToken,
  resolveCashierStatusClass,
  resolveLinkedScene,
  resolveRemainingSeconds,
  resolveScene
} = require("../../utils/payment");
const { paymentApi } = require("../../utils/request");

Page({
  data: {
    prepayOrderNo: "",
    scene: resolveScene("balance-pay"),
    cashier: null,
    loading: true,
    actionName: "",
    errorMessage: "",
    staleCashier: false,
    idempotencyKey: "",
    canSubmit: false,
    countdownSeconds: 0,
    countdownLabel: "00:00:00",
    statusClass: "status-warn"
  },

  onLoad(options) {
    this.pageActive = true;
    this.cashierRequestVersion = 0;
    const prepayOrderNo = options && options.prepayOrderNo
      ? decodeURIComponent(options.prepayOrderNo).trim()
      : "";
    const sceneKey = options && Object.prototype.hasOwnProperty.call(options, "scene")
      ? decodeURIComponent(options.scene)
      : null;
    const payScene = options && Object.prototype.hasOwnProperty.call(options, "payScene")
      ? decodeURIComponent(options.payScene)
      : null;
    this.setData({
      prepayOrderNo,
      scene: resolveLinkedScene(sceneKey, payScene),
      idempotencyKey: createIdempotencyKey(prepayOrderNo)
    });
    this.loadCashier();
  },

  onUnload() {
    this.pageActive = false;
    this.cashierRequestVersion += 1;
    this.stopCountdown();
  },

  updateActionState(cashier) {
    this.setData({
      canSubmit: canSubmitPayment(cashier),
      statusClass: resolveCashierStatusClass(cashier && cashier.statusType)
    });
  },

  redirectToPaymentResult(paymentOrderId, prepayOrderNo, paymentMethod = PAYMENT_METHOD) {
    wx.redirectTo({
      url: `/pages/result/index?paymentOrderId=${encodeURIComponent(paymentOrderId)}`
        + `&prepayOrderNo=${encodeURIComponent(prepayOrderNo)}`
        + `&paymentMethod=${encodeURIComponent(paymentMethod)}`
        + `&scene=${encodeURIComponent(this.data.scene.key)}`
    });
  },

  startCountdown(expiresAt) {
    this.stopCountdown();
    const update = () => {
      const countdownSeconds = resolveRemainingSeconds(expiresAt);
      const hours = String(Math.floor(countdownSeconds / 3600)).padStart(2, "0");
      const minutes = String(Math.floor((countdownSeconds % 3600) / 60)).padStart(2, "0");
      const seconds = String(countdownSeconds % 60).padStart(2, "0");
      this.setData({
        countdownSeconds,
        countdownLabel: `${hours}:${minutes}:${seconds}`,
        canSubmit: canSubmitPayment(this.data.cashier)
      });
      if (countdownSeconds <= 0) {
        this.stopCountdown();
      }
    };
    update();
    if (this.data.countdownSeconds > 0) {
      this.countdownTimer = setInterval(update, 1000);
    }
  },

  stopCountdown() {
    if (this.countdownTimer) {
      clearInterval(this.countdownTimer);
      this.countdownTimer = null;
    }
  },

  async loadCashier() {
    if (!this.data.scene.payScene) {
      this.setData({ loading: false, errorMessage: "当前小程序暂不支持该业务场景，请返回业务入口重新选择。" });
      return;
    }
    if (!this.data.prepayOrderNo || this.data.actionName) {
      if (!this.data.prepayOrderNo) {
        this.setData({ loading: false, errorMessage: "缺少预付单号，无法加载收银台。" });
      }
      return;
    }
    const requestVersion = ++this.cashierRequestVersion;
    const previousCashier = this.data.cashier;
    const wasStale = this.data.staleCashier;
    this.setData({ loading: true, errorMessage: "" });
    try {
      const cashier = await paymentApi.getCashier(
        this.data.prepayOrderNo, TERMINAL, this.data.scene.payScene
      );
      if (!this.pageActive || requestVersion !== this.cashierRequestVersion) {
        return;
      }
      if (!cashier || cashier.prepayOrderNo !== this.data.prepayOrderNo) {
        throw new Error("收银台响应为空或与当前预付单不一致");
      }
      if (previousCashier && !matchesCashierSnapshot(
        this.data.prepayOrderNo, previousCashier, cashier, this.data.scene.payScene
      )) {
        throw new Error("收银台刷新响应为空或与当前支付单不一致");
      }
      this.setData({ cashier, staleCashier: false });
      this.updateActionState(cashier);
      this.startCountdown(cashier.expiresAt);
      if (wasStale && ["WAIT_CALLBACK", "SUCCESS", "CLOSED", "RISK_REVIEW"].includes(cashier.paymentStatus)) {
        this.redirectToPaymentResult(cashier.paymentOrderId, cashier.prepayOrderNo,
          cashier.paymentMethod || cashier.reviewPaymentMethod || PAYMENT_METHOD);
      }
    } catch (error) {
      if (!this.pageActive || requestVersion !== this.cashierRequestVersion) {
        return;
      }
      this.stopCountdown();
      this.setData({
        cashier: null,
        staleCashier: false,
        countdownSeconds: 0,
        countdownLabel: "00:00:00",
        errorMessage: error.message || "收银台加载失败"
      });
      this.updateActionState(null);
    } finally {
      if (this.pageActive && requestVersion === this.cashierRequestVersion) {
        this.setData({ loading: false });
      }
    }
  },

  async submitPayment() {
    if (this.data.loading || this.data.actionName || !this.data.canSubmit || !this.data.cashier) {
      return;
    }
    const prepayOrderNo = this.data.prepayOrderNo;
    const accessToken = resolvePaymentAccessToken();
    if (!accessToken) {
      this.setData({ errorMessage: "小程序支付访问令牌未配置，请联系运营完成安全注入" });
      return;
    }
    this.setData({ actionName: "submit", errorMessage: "" });
    try {
      const result = await paymentApi.submit({
        prepayOrderNo,
        paymentMethod: PAYMENT_METHOD,
        channelCode: CHANNEL_CODE,
        sourceAppId: SOURCE_APP_ID,
        merchantNo: MERCHANT_NO,
        accessToken,
        terminal: TERMINAL,
        clientDeviceId: "wechat-mini-program",
        idempotencyKey: this.data.idempotencyKey
      }, this.data.scene.payScene);
      if (!this.pageActive) {
        return;
      }
      if (!matchesPaymentSubmission(
        prepayOrderNo,
        this.data.cashier.paymentOrderId,
        this.data.cashier.orderNo,
        result,
        this.data.cashier.amount,
        this.data.cashier.billNo,
        this.data.cashier.customerName,
        PAYMENT_METHOD,
        this.data.scene.payScene
      )) {
        throw new Error("支付提交响应为空或与当前预付单不一致");
      }
      const paymentStatus = typeof result.paymentStatus === "string"
        ? result.paymentStatus.trim().toUpperCase()
        : "";
      if (["PREPAY_CREATED", "PAYING"].includes(paymentStatus)) {
        throw new Error("支付提交状态未推进，请刷新收银台后使用原请求键重试");
      }
      if (["RISK_REVIEW", "SUCCESS", "CLOSED"].includes(paymentStatus)) {
        this.redirectToPaymentResult(result.paymentOrderId, prepayOrderNo);
        return;
      }
      if (paymentStatus !== "WAIT_CALLBACK") {
        throw new Error("支付提交状态不允许拉起微信支付，请刷新收银台核对结果");
      }
      const nativePaymentResult = await requestNativePayment(result);
      if (!this.pageActive) {
        return;
      }
      if (!canRedirectAfterNativePayment(nativePaymentResult)) {
        throw new Error("支付渠道未返回微信原生支付参数，未发起实际支付");
      }
      this.redirectToPaymentResult(result.paymentOrderId, prepayOrderNo);
    } catch (error) {
      if (!this.pageActive) {
        return;
      }
      this.setData({ errorMessage: error.message || "支付提交失败" });
      await this.loadCashierAfterAction(error);
    } finally {
      if (this.pageActive) {
        this.setData({ actionName: "" });
      }
    }
  },

  async loadCashierAfterAction(actionError) {
    const requestVersion = ++this.cashierRequestVersion;
    try {
      const cashier = await paymentApi.getCashier(
        this.data.prepayOrderNo, TERMINAL, this.data.scene.payScene
      );
      if (!this.pageActive || requestVersion !== this.cashierRequestVersion) {
        return;
      }
      if (!matchesCashierSnapshot(
        this.data.prepayOrderNo,
        this.data.cashier,
        cashier,
        this.data.scene.payScene
      )) {
        throw new Error("收银台刷新响应为空或与当前支付单不一致");
      }
      this.setData({ cashier, staleCashier: false });
      this.updateActionState(cashier);
      this.startCountdown(cashier.expiresAt);
      if (["WAIT_CALLBACK", "SUCCESS", "CLOSED", "RISK_REVIEW"].includes(cashier.paymentStatus)) {
        this.redirectToPaymentResult(cashier.paymentOrderId, cashier.prepayOrderNo,
          cashier.paymentMethod || cashier.reviewPaymentMethod || PAYMENT_METHOD);
      }
    } catch (error) {
      if (!this.pageActive || requestVersion !== this.cashierRequestVersion) {
        return;
      }
      this.stopCountdown();
      this.setData({
        staleCashier: true,
        errorMessage: `${actionError && actionError.message ? `${actionError.message}；` : ""}`
          + `${error.message || "收银台刷新失败"}；支付状态未经确认，请刷新收银台，勿重复付款。`
      });
      this.updateActionState(null);
    }
  },

  backToEntry() {
    if (this.data.actionName) {
      return;
    }
    wx.reLaunch({ url: `/pages/entry/index?scene=${encodeURIComponent(this.data.scene.key)}` });
  }
});
