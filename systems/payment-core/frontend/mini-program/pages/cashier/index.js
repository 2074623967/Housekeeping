const {
  CHANNEL_CODE,
  DEMO_ACCESS_TOKEN,
  MERCHANT_NO,
  PAYMENT_METHOD,
  SOURCE_APP_ID,
  TERMINAL,
  canSubmitPayment,
  createIdempotencyKey,
  requestNativePayment,
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
    idempotencyKey: "",
    canSubmit: false,
    countdownSeconds: 0,
    countdownLabel: "00:00:00"
  },

  onLoad(options) {
    const prepayOrderNo = options && options.prepayOrderNo
      ? decodeURIComponent(options.prepayOrderNo)
      : "";
    const sceneKey = options && options.scene ? decodeURIComponent(options.scene) : "balance-pay";
    this.setData({
      prepayOrderNo,
      scene: resolveScene(sceneKey),
      idempotencyKey: createIdempotencyKey(prepayOrderNo)
    });
    this.loadCashier();
  },

  onUnload() {
    this.stopCountdown();
  },

  updateActionState(cashier) {
    this.setData({ canSubmit: canSubmitPayment(cashier) });
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
    if (!this.data.prepayOrderNo || this.data.actionName) {
      if (!this.data.prepayOrderNo) {
        this.setData({ loading: false, errorMessage: "缺少预付单号，无法加载收银台。" });
      }
      return;
    }
    this.setData({ loading: true, errorMessage: "" });
    try {
      const cashier = await paymentApi.getCashier(this.data.prepayOrderNo);
      if (!cashier || cashier.prepayOrderNo !== this.data.prepayOrderNo) {
        throw new Error("收银台响应为空或与当前预付单不一致");
      }
      this.setData({ cashier });
      this.updateActionState(cashier);
      this.startCountdown(cashier.expiresAt);
    } catch (error) {
      this.stopCountdown();
      this.setData({
        cashier: null,
        countdownSeconds: 0,
        countdownLabel: "00:00:00",
        errorMessage: error.message || "收银台加载失败"
      });
      this.updateActionState(null);
    } finally {
      this.setData({ loading: false });
    }
  },

  regenerateIdempotencyKey() {
    if (this.data.loading || this.data.actionName || !this.data.canSubmit) {
      return;
    }
    this.setData({
      idempotencyKey: createIdempotencyKey(this.data.prepayOrderNo),
      errorMessage: ""
    });
  },

  async submitPayment() {
    if (this.data.loading || this.data.actionName || !this.data.canSubmit || !this.data.cashier) {
      return;
    }
    const prepayOrderNo = this.data.prepayOrderNo;
    this.setData({ actionName: "submit", errorMessage: "" });
    try {
      const result = await paymentApi.submit({
        prepayOrderNo,
        paymentMethod: PAYMENT_METHOD,
        channelCode: CHANNEL_CODE,
        sourceAppId: SOURCE_APP_ID,
        merchantNo: MERCHANT_NO,
        accessToken: DEMO_ACCESS_TOKEN,
        terminal: TERMINAL,
        clientDeviceId: "wechat-mini-program",
        idempotencyKey: this.data.idempotencyKey
      });
      if (!result || result.prepayOrderNo !== prepayOrderNo || !result.paymentOrderId) {
        throw new Error("支付提交响应为空或与当前预付单不一致");
      }
      await requestNativePayment(result);
      wx.redirectTo({
        url: `/pages/result/index?paymentOrderId=${encodeURIComponent(result.paymentOrderId)}`
          + `&prepayOrderNo=${encodeURIComponent(prepayOrderNo)}`
          + `&scene=${encodeURIComponent(this.data.scene.key)}`
      });
    } catch (error) {
      this.setData({ errorMessage: error.message || "支付提交失败" });
      await this.loadCashierAfterAction();
    } finally {
      this.setData({ actionName: "" });
    }
  },

  async loadCashierAfterAction() {
    try {
      const cashier = await paymentApi.getCashier(this.data.prepayOrderNo);
      if (cashier && cashier.prepayOrderNo === this.data.prepayOrderNo) {
        this.setData({ cashier });
        this.updateActionState(cashier);
        this.startCountdown(cashier.expiresAt);
      }
    } catch (error) {
      this.stopCountdown();
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
