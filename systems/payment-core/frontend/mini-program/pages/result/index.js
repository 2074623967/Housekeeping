const {
  canClosePayment,
  canQueryPayment,
  resolveScene,
  resultMeta
} = require("../../utils/payment");
const { paymentApi } = require("../../utils/request");

Page({
  data: {
    paymentOrderId: "",
    prepayOrderNo: "",
    scene: resolveScene("balance-pay"),
    detail: null,
    meta: resultMeta("WAIT_CALLBACK"),
    loading: true,
    actionName: "",
    errorMessage: "",
    successMessage: "",
    canQuery: false,
    canClose: false
  },

  onLoad(options) {
    this.setData({
      paymentOrderId: options && options.paymentOrderId ? decodeURIComponent(options.paymentOrderId) : "",
      prepayOrderNo: options && options.prepayOrderNo ? decodeURIComponent(options.prepayOrderNo) : "",
      scene: resolveScene(options && options.scene ? decodeURIComponent(options.scene) : "balance-pay")
    });
    this.loadDetail();
  },

  applyDetail(detail) {
    const isCurrent = detail && detail.paymentOrderId === this.data.paymentOrderId;
    this.setData({
      detail: isCurrent ? detail : null,
      meta: resultMeta(isCurrent ? detail.status : "WAIT_CALLBACK"),
      canQuery: Boolean(isCurrent && canQueryPayment(detail.status, detail.latestAttemptStatus)),
      canClose: Boolean(isCurrent && canClosePayment(
        detail.status,
        detail.channelTransactionNo,
        detail.latestAttemptStatus
      ))
    });
  },

  async loadDetail() {
    if (!this.data.paymentOrderId || this.data.actionName) {
      if (!this.data.paymentOrderId) {
        this.setData({ loading: false, errorMessage: "缺少支付单号，无法加载支付结果。" });
      }
      return;
    }
    const paymentOrderId = this.data.paymentOrderId;
    this.setData({ loading: true, errorMessage: "", successMessage: "" });
    try {
      const detail = await paymentApi.getDetail(paymentOrderId);
      if (!detail || detail.paymentOrderId !== paymentOrderId) {
        throw new Error("支付详情响应为空或与当前支付单不一致");
      }
      this.applyDetail(detail);
    } catch (error) {
      this.applyDetail(null);
      this.setData({ errorMessage: error.message || "支付结果加载失败" });
    } finally {
      this.setData({ loading: false });
    }
  },

  async queryPayment() {
    if (this.data.loading || this.data.actionName || !this.data.canQuery || !this.data.detail) {
      return;
    }
    const paymentOrderId = this.data.paymentOrderId;
    this.setData({ actionName: "query", errorMessage: "", successMessage: "" });
    try {
      const detail = await paymentApi.query(paymentOrderId);
      if (!detail || detail.paymentOrderId !== paymentOrderId) {
        throw new Error("查单响应为空或与当前支付单不一致");
      }
      this.applyDetail(detail);
      this.setData({ successMessage: `支付单 ${paymentOrderId} 已完成主动查单。` });
    } catch (error) {
      this.setData({ errorMessage: error.message || "主动查单失败" });
    } finally {
      this.setData({ actionName: "" });
    }
  },

  async closePayment() {
    if (this.data.loading || this.data.actionName || !this.data.canClose || !this.data.detail) {
      return;
    }
    const paymentOrderId = this.data.paymentOrderId;
    this.setData({ actionName: "close", errorMessage: "", successMessage: "" });
    try {
      const detail = await paymentApi.close(paymentOrderId);
      if (!detail || detail.paymentOrderId !== paymentOrderId) {
        throw new Error("关单响应为空或与当前支付单不一致");
      }
      this.applyDetail(detail);
      this.setData({ successMessage: `支付单 ${paymentOrderId} 已执行关闭动作。` });
    } catch (error) {
      this.setData({ errorMessage: error.message || "关闭支付单失败" });
    } finally {
      this.setData({ actionName: "" });
    }
  },

  backToCashier() {
    if (this.data.actionName || !this.data.prepayOrderNo) {
      return;
    }
    wx.redirectTo({
      url: `/pages/cashier/index?prepayOrderNo=${encodeURIComponent(this.data.prepayOrderNo)}`
        + `&scene=${encodeURIComponent(this.data.scene.key)}`
    });
  },

  backToEntry() {
    if (this.data.actionName) {
      return;
    }
    wx.reLaunch({ url: `/pages/entry/index?scene=${encodeURIComponent(this.data.scene.key)}` });
  }
});
