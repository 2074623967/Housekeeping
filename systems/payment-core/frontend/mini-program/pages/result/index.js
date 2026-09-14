const {
  canClosePayment,
  canQueryPayment,
  resolvePaymentDetailRefreshDelay,
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
    this.pageActive = true;
    this.detailRequestVersion = 0;
    this.autoRefreshAttempts = 0;
    this.setData({
      paymentOrderId: options && options.paymentOrderId ? decodeURIComponent(options.paymentOrderId) : "",
      prepayOrderNo: options && options.prepayOrderNo ? decodeURIComponent(options.prepayOrderNo) : "",
      scene: resolveScene(options && options.scene ? decodeURIComponent(options.scene) : "balance-pay")
    });
    this.loadDetail();
  },

  onShow() {
    const shouldReload = this.pageActive === false
      && Boolean(this.data.paymentOrderId)
      && (this.reloadDetailOnShow || !this.data.detail);
    this.pageActive = true;
    this.reloadDetailOnShow = false;
    if (shouldReload) {
      this.loadDetail();
      return;
    }
    this.scheduleAutoRefresh();
  },

  onHide() {
    this.reloadDetailOnShow = Boolean(this.data.loading || this.data.actionName);
    this.pageActive = false;
    this.stopAutoRefresh();
    this.detailRequestVersion += 1;
    this.setData({ loading: false, actionName: "" });
  },

  onUnload() {
    this.pageActive = false;
    this.stopAutoRefresh();
    this.detailRequestVersion += 1;
  },

  applyDetail(detail) {
    const isCurrent = detail && detail.paymentOrderId === this.data.paymentOrderId;
    this.setData({
      prepayOrderNo: isCurrent && detail.prepayOrderNo
        ? detail.prepayOrderNo
        : this.data.prepayOrderNo,
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

  stopAutoRefresh() {
    if (this.autoRefreshTimer) {
      clearTimeout(this.autoRefreshTimer);
      this.autoRefreshTimer = null;
    }
  },

  scheduleAutoRefresh() {
    this.stopAutoRefresh();
    const detail = this.data.detail;
    const delay = resolvePaymentDetailRefreshDelay(
      detail && detail.status,
      this.autoRefreshAttempts || 0
    );
    if (!this.pageActive || !detail || delay == null) {
      return;
    }
    this.autoRefreshTimer = setTimeout(() => this.refreshDetailInBackground(), delay);
  },

  async refreshDetailInBackground() {
    this.autoRefreshTimer = null;
    if (!this.pageActive || this.data.loading || this.data.actionName || !this.data.detail) {
      this.scheduleAutoRefresh();
      return;
    }
    if (resolvePaymentDetailRefreshDelay(
      this.data.detail.status,
      this.autoRefreshAttempts || 0
    ) == null) {
      return;
    }
    const snapshot = {
      paymentOrderId: this.data.paymentOrderId,
      prepayOrderNo: this.data.detail.prepayOrderNo || ""
    };
    const requestVersion = ++this.detailRequestVersion;
    this.autoRefreshAttempts += 1;
    try {
      const detail = await paymentApi.getDetail(snapshot.paymentOrderId);
      if (!this.isCurrentDetailResponse(snapshot, requestVersion, detail)) {
        return;
      }
      this.applyDetail(detail);
    } catch (error) {
      // Read-only refresh remains silent; the explicit channel query is the recovery action.
    } finally {
      if (this.pageActive && requestVersion === this.detailRequestVersion) {
        this.scheduleAutoRefresh();
      }
    }
  },

  isCurrentDetailResponse(snapshot, requestVersion, detail) {
    return this.pageActive
      && requestVersion === this.detailRequestVersion
      && snapshot.paymentOrderId === this.data.paymentOrderId
      && this.data.detail
      && this.data.detail.paymentOrderId === snapshot.paymentOrderId
      && (this.data.detail.prepayOrderNo || "") === snapshot.prepayOrderNo
      && detail
      && detail.paymentOrderId === snapshot.paymentOrderId
      && (detail.prepayOrderNo || "") === snapshot.prepayOrderNo;
  },

  async loadDetail() {
    if (!this.data.paymentOrderId || this.data.actionName) {
      if (!this.data.paymentOrderId) {
        this.setData({ loading: false, errorMessage: "缺少支付单号，无法加载支付结果。" });
      }
      return;
    }
    const paymentOrderId = this.data.paymentOrderId;
    this.stopAutoRefresh();
    const requestVersion = ++this.detailRequestVersion;
    this.setData({ loading: true, errorMessage: "", successMessage: "" });
    try {
      const detail = await paymentApi.getDetail(paymentOrderId);
      if (!this.pageActive || requestVersion !== this.detailRequestVersion) {
        return;
      }
      if (!detail || detail.paymentOrderId !== paymentOrderId) {
        throw new Error("支付详情响应为空或与当前支付单不一致");
      }
      this.applyDetail(detail);
    } catch (error) {
      if (!this.pageActive || requestVersion !== this.detailRequestVersion) {
        return;
      }
      this.applyDetail(null);
      this.setData({ errorMessage: error.message || "支付结果加载失败" });
    } finally {
      if (this.pageActive && requestVersion === this.detailRequestVersion) {
        this.setData({ loading: false });
        this.scheduleAutoRefresh();
      }
    }
  },

  async queryPayment() {
    if (this.data.loading || this.data.actionName || !this.data.canQuery || !this.data.detail) {
      return;
    }
    const snapshot = {
      paymentOrderId: this.data.paymentOrderId,
      prepayOrderNo: this.data.detail.prepayOrderNo || ""
    };
    this.stopAutoRefresh();
    const requestVersion = ++this.detailRequestVersion;
    this.setData({ actionName: "query", errorMessage: "", successMessage: "" });
    try {
      const detail = await paymentApi.query(snapshot.paymentOrderId);
      if (!this.isCurrentDetailResponse(snapshot, requestVersion, detail)) {
        if (!this.pageActive || requestVersion !== this.detailRequestVersion) {
          return;
        }
        throw new Error("查单响应为空或与当前支付单不一致");
      }
      this.applyDetail(detail);
      this.setData({ successMessage: `支付单 ${snapshot.paymentOrderId} 已完成主动查单。` });
    } catch (error) {
      if (!this.pageActive || requestVersion !== this.detailRequestVersion) {
        return;
      }
      this.setData({ errorMessage: error.message || "主动查单失败" });
    } finally {
      if (this.pageActive && requestVersion === this.detailRequestVersion) {
        this.setData({ actionName: "" });
        this.scheduleAutoRefresh();
      }
    }
  },

  async closePayment() {
    if (this.data.loading || this.data.actionName || !this.data.canClose || !this.data.detail) {
      return;
    }
    const snapshot = {
      paymentOrderId: this.data.paymentOrderId,
      prepayOrderNo: this.data.detail.prepayOrderNo || ""
    };
    this.stopAutoRefresh();
    const requestVersion = ++this.detailRequestVersion;
    this.setData({ actionName: "close", errorMessage: "", successMessage: "" });
    try {
      const detail = await paymentApi.close(snapshot.paymentOrderId);
      if (!this.isCurrentDetailResponse(snapshot, requestVersion, detail)) {
        if (!this.pageActive || requestVersion !== this.detailRequestVersion) {
          return;
        }
        throw new Error("关单响应为空或与当前支付单不一致");
      }
      this.applyDetail(detail);
      this.setData({ successMessage: `支付单 ${snapshot.paymentOrderId} 已执行关闭动作。` });
    } catch (error) {
      if (!this.pageActive || requestVersion !== this.detailRequestVersion) {
        return;
      }
      this.setData({ errorMessage: error.message || "关闭支付单失败" });
    } finally {
      if (this.pageActive && requestVersion === this.detailRequestVersion) {
        this.setData({ actionName: "" });
        this.scheduleAutoRefresh();
      }
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
