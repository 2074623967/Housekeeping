const {
  canClosePayment,
  canQueryPayment,
  normalizeAmount,
  PAYMENT_DETAIL_REFRESH_MAX_ATTEMPTS,
  resolvePaymentDetailRefreshDelay,
  resolveLinkedScene,
  resolveScene,
  resultMeta,
  shouldAutoRefreshPaymentDetail
} = require("../../utils/payment");
const { paymentApi } = require("../../utils/request");

const LOADING_META = {
  title: "正在加载支付结果",
  tone: "warn",
  hint: "正在核对当前支付单的实际状态。"
};
const UNVERIFIED_META = {
  title: "支付结果待核查",
  tone: "warn",
  hint: "暂无法确认实际支付状态。请勿重复付款，保留支付单号并联系服务方核查。"
};

function normalizeIdentifier(value) {
  return typeof value === "string" ? value.trim() : "";
}

function matchesPaymentTimestamp(snapshotValue, detailValue) {
  if (snapshotValue == null || snapshotValue === "-") {
    return true;
  }
  return normalizeIdentifier(snapshotValue).replace("T", " ")
    === normalizeIdentifier(detailValue).replace("T", " ");
}

function matchesPreservedText(snapshotValue, detailValue) {
  return snapshotValue == null
    || detailValue == null
    || normalizeIdentifier(snapshotValue) === normalizeIdentifier(detailValue);
}

function normalizePaymentDetail(detail) {
  if (!detail || typeof detail !== "object" || Array.isArray(detail)) {
    return detail;
  }
  return {
    ...detail,
    paymentOrderId: normalizeIdentifier(detail.paymentOrderId),
    businessChainId: normalizeIdentifier(detail.businessChainId),
    prepayOrderNo: normalizeIdentifier(detail.prepayOrderNo),
    orderNo: normalizeIdentifier(detail.orderNo)
  };
}

Page({
  data: {
    paymentOrderId: "",
    prepayOrderNo: "",
    expectedPaymentMethod: "",
    scene: resolveScene("balance-pay"),
    detail: null,
    meta: LOADING_META,
    loading: true,
    actionName: "",
    errorMessage: "",
    successMessage: "",
    staleDetail: false,
    canQuery: false,
    canClose: false,
    autoRefreshPaused: false
  },

  onLoad(options) {
    this.pageActive = true;
    this.detailRequestVersion = 0;
    this.autoRefreshAttempts = 0;
    const sceneKey = options && Object.prototype.hasOwnProperty.call(options, "scene")
      ? decodeURIComponent(options.scene)
      : null;
    const payScene = options && Object.prototype.hasOwnProperty.call(options, "payScene")
      ? decodeURIComponent(options.payScene)
      : null;
    this.setData({
      paymentOrderId: options && options.paymentOrderId ? decodeURIComponent(options.paymentOrderId).trim() : "",
      prepayOrderNo: options && options.prepayOrderNo ? decodeURIComponent(options.prepayOrderNo).trim() : "",
      expectedPaymentMethod: options && options.paymentMethod
        ? decodeURIComponent(options.paymentMethod).trim()
        : "",
      scene: resolveLinkedScene(sceneKey, payScene)
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
    const normalizedDetail = normalizePaymentDetail(detail);
    const isCurrent = normalizedDetail
      && normalizedDetail.paymentOrderId === this.data.paymentOrderId
      && normalizedDetail.businessChainId === `CHAIN-${normalizedDetail.paymentOrderId}`;
    const mergedDetail = isCurrent
      ? normalizePaymentDetail({ ...(this.data.detail || {}), ...normalizedDetail })
      : null;
    this.setData({
      prepayOrderNo: mergedDetail && mergedDetail.prepayOrderNo
        ? mergedDetail.prepayOrderNo
        : this.data.prepayOrderNo,
      detail: mergedDetail,
      meta: mergedDetail ? resultMeta(mergedDetail.status, mergedDetail.latestAttemptStatus) : UNVERIFIED_META,
      staleDetail: false,
      errorMessage: "",
      successMessage: "",
      canQuery: Boolean(mergedDetail && canQueryPayment(
        mergedDetail.status, mergedDetail.latestAttemptStatus,
        mergedDetail.latestAttemptCompletedAt, mergedDetail.channelTransactionNo,
        mergedDetail.channel
      )),
      canClose: Boolean(mergedDetail && canClosePayment(
        mergedDetail.status,
        mergedDetail.channelTransactionNo,
        mergedDetail.latestAttemptStatus
      ))
    });
  },

  markUnverifiedResult(message) {
    this.setData({
      meta: UNVERIFIED_META,
      staleDetail: Boolean(this.data.detail),
      canQuery: false,
      canClose: false,
      errorMessage: message,
      successMessage: ""
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
    this.setData({
      autoRefreshPaused: Boolean(detail
        && shouldAutoRefreshPaymentDetail(detail.status, detail.latestAttemptStatus)
        && this.autoRefreshAttempts >= PAYMENT_DETAIL_REFRESH_MAX_ATTEMPTS)
    });
    const delay = resolvePaymentDetailRefreshDelay(
      detail && detail.status,
      this.autoRefreshAttempts || 0,
      detail && detail.latestAttemptStatus
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
      this.autoRefreshAttempts || 0,
      this.data.detail.latestAttemptStatus
    ) == null) {
      return;
    }
    const snapshot = {
      paymentOrderId: this.data.paymentOrderId,
      businessChainId: this.data.detail.businessChainId || "",
      prepayOrderNo: this.data.detail.prepayOrderNo || "",
      billNo: this.data.detail.billNo,
      orderNo: this.data.detail.orderNo || "",
      customerName: this.data.detail.customerName,
      payScene: this.data.detail.payScene,
      amount: normalizeAmount(this.data.detail.amount),
      createdAt: this.data.detail.createdAt,
      successAt: this.data.detail.successAt,
      latestAttemptCompletedAt: this.data.detail.latestAttemptCompletedAt,
      channel: this.data.detail.channel,
      channelTransactionNo: this.data.detail.channelTransactionNo,
      paymentMethod: this.data.detail.paymentMethod,
      refundOrderId: this.data.detail.refundOrderId
    };
    const requestVersion = ++this.detailRequestVersion;
    this.autoRefreshAttempts += 1;
    try {
      const detail = await paymentApi.getDetail(snapshot.paymentOrderId, this.data.scene.payScene);
      if (!this.pageActive || requestVersion !== this.detailRequestVersion) {
        return;
      }
      if (!this.isCurrentDetailResponse(snapshot, requestVersion, detail)) {
        throw new Error("自动刷新响应为空或与当前支付单不一致");
      }
      this.applyDetail(detail);
    } catch {
      if (this.pageActive && requestVersion === this.detailRequestVersion) {
        this.markUnverifiedResult("自动刷新未能确认最新支付状态，当前仅供核对上次读取的事实。");
      }
    } finally {
      if (this.pageActive && requestVersion === this.detailRequestVersion) {
        this.scheduleAutoRefresh();
      }
    }
  },

  isCurrentDetailResponse(snapshot, requestVersion, detail) {
    const normalizedSnapshot = normalizePaymentDetail(snapshot);
    const normalizedCurrentDetail = normalizePaymentDetail(this.data.detail);
    const normalizedDetail = normalizePaymentDetail(detail);
    return this.pageActive
      && requestVersion === this.detailRequestVersion
      && normalizedSnapshot.paymentOrderId === this.data.paymentOrderId
      && normalizedSnapshot.businessChainId === `CHAIN-${normalizedSnapshot.paymentOrderId}`
      && normalizedCurrentDetail
      && normalizedCurrentDetail.paymentOrderId === normalizedSnapshot.paymentOrderId
      && normalizedCurrentDetail.businessChainId === normalizedSnapshot.businessChainId
      && (normalizedCurrentDetail.prepayOrderNo || "") === normalizedSnapshot.prepayOrderNo
      && (!normalizedSnapshot.orderNo || (normalizedCurrentDetail.orderNo || "") === normalizedSnapshot.orderNo)
      && normalizedDetail
      && normalizedDetail.paymentOrderId === normalizedSnapshot.paymentOrderId
      && normalizedDetail.businessChainId === normalizedSnapshot.businessChainId
      && (normalizedDetail.prepayOrderNo || "") === normalizedSnapshot.prepayOrderNo
      && (!normalizedSnapshot.orderNo || (normalizedDetail.orderNo || "") === normalizedSnapshot.orderNo)
      && (!normalizedSnapshot.amount || normalizeAmount(normalizedDetail.amount) === normalizedSnapshot.amount)
      && matchesPaymentTimestamp(normalizedSnapshot.createdAt, normalizedCurrentDetail.createdAt)
      && matchesPaymentTimestamp(normalizedSnapshot.createdAt, normalizedDetail.createdAt)
      && matchesPaymentTimestamp(normalizedSnapshot.successAt, normalizedCurrentDetail.successAt)
      && matchesPaymentTimestamp(normalizedSnapshot.successAt, normalizedDetail.successAt)
      && matchesPaymentTimestamp(normalizedSnapshot.latestAttemptCompletedAt, normalizedCurrentDetail.latestAttemptCompletedAt)
      && matchesPaymentTimestamp(normalizedSnapshot.latestAttemptCompletedAt, normalizedDetail.latestAttemptCompletedAt)
      && (normalizedSnapshot.refundOrderId == null
        || normalizeIdentifier(normalizedDetail.refundOrderId)
          === normalizeIdentifier(normalizedSnapshot.refundOrderId))
      && (normalizedSnapshot.channel == null
        || normalizeIdentifier(normalizedDetail.channel) === normalizeIdentifier(normalizedSnapshot.channel))
      && (normalizedSnapshot.channelTransactionNo == null
        || normalizeIdentifier(normalizedDetail.channelTransactionNo)
          === normalizeIdentifier(normalizedSnapshot.channelTransactionNo))
      && (normalizedSnapshot.paymentMethod == null
        || normalizeIdentifier(normalizedDetail.paymentMethod)
          === normalizeIdentifier(normalizedSnapshot.paymentMethod))
      && matchesPreservedText(normalizedSnapshot.billNo, normalizedDetail.billNo)
      && matchesPreservedText(normalizedSnapshot.customerName, normalizedDetail.customerName)
      && (!normalizedSnapshot.payScene
        || normalizeIdentifier(normalizedDetail.payScene) === normalizedSnapshot.payScene);
  },

  async loadDetail() {
    if (!this.data.scene.payScene) {
      this.markUnverifiedResult("当前小程序暂不支持该业务场景，请返回业务入口重新选择。");
      this.setData({ loading: false });
      return;
    }
    if (!this.data.paymentOrderId || this.data.actionName) {
      if (!this.data.paymentOrderId) {
        this.markUnverifiedResult("缺少支付单号，无法加载支付结果。");
        this.setData({ loading: false });
      }
      return;
    }
    const paymentOrderId = this.data.paymentOrderId;
    const previousDetail = this.data.detail;
    this.stopAutoRefresh();
    const requestVersion = ++this.detailRequestVersion;
    this.setData({ loading: true, staleDetail: Boolean(previousDetail),
      meta: previousDetail ? UNVERIFIED_META : LOADING_META,
      canQuery: false, canClose: false, errorMessage: "", successMessage: "" });
    try {
      const detail = await paymentApi.getDetail(paymentOrderId, this.data.scene.payScene);
      const normalizedDetail = normalizePaymentDetail(detail);
      if (!this.pageActive || requestVersion !== this.detailRequestVersion) {
        return;
      }
      if (!normalizedDetail || normalizedDetail.paymentOrderId !== paymentOrderId) {
        throw new Error("支付详情响应为空或与当前支付单不一致");
      }
      if (normalizedDetail.businessChainId !== `CHAIN-${paymentOrderId}`) {
        throw new Error("支付详情响应为空或与当前业务链路不一致");
      }
      if (this.data.prepayOrderNo && normalizedDetail.prepayOrderNo !== this.data.prepayOrderNo) {
        throw new Error("支付详情响应为空或与当前预付单不一致");
      }
      if (this.data.expectedPaymentMethod
        && normalizedDetail.paymentMethod !== this.data.expectedPaymentMethod) {
        throw new Error("支付详情响应为空或与当前支付方式不一致");
      }
      if (previousDetail && !this.isCurrentDetailResponse(previousDetail, requestVersion, normalizedDetail)) {
        throw new Error("支付详情刷新响应为空或与当前支付单不一致");
      }
      this.applyDetail(normalizedDetail);
      this.autoRefreshAttempts = 0;
    } catch (error) {
      if (!this.pageActive || requestVersion !== this.detailRequestVersion) {
        return;
      }
      if (!previousDetail) {
        this.applyDetail(null);
      }
      this.markUnverifiedResult(previousDetail
        ? `刷新失败，当前显示上次成功查询状态：${error.message || "支付结果加载失败"}`
        : error.message || "支付结果加载失败");
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
      businessChainId: this.data.detail.businessChainId || "",
      prepayOrderNo: this.data.detail.prepayOrderNo || "",
      billNo: this.data.detail.billNo,
      orderNo: this.data.detail.orderNo || "",
      customerName: this.data.detail.customerName,
      payScene: this.data.detail.payScene,
      amount: normalizeAmount(this.data.detail.amount),
      createdAt: this.data.detail.createdAt,
      successAt: this.data.detail.successAt,
      latestAttemptCompletedAt: this.data.detail.latestAttemptCompletedAt,
      channel: this.data.detail.channel,
      channelTransactionNo: this.data.detail.channelTransactionNo,
      paymentMethod: this.data.detail.paymentMethod,
      refundOrderId: this.data.detail.refundOrderId
    };
    this.stopAutoRefresh();
    const requestVersion = ++this.detailRequestVersion;
    this.setData({ actionName: "query", errorMessage: "", successMessage: "" });
    try {
      const detail = await paymentApi.query(
        snapshot.paymentOrderId, snapshot, this.data.scene.payScene
      );
      if (!this.isCurrentDetailResponse(snapshot, requestVersion, detail)) {
        if (!this.pageActive || requestVersion !== this.detailRequestVersion) {
          return;
        }
        throw new Error("查单响应为空或与当前支付单不一致");
      }
      this.applyDetail(detail);
      this.autoRefreshAttempts = 0;
      this.setData({ successMessage: `支付单 ${snapshot.paymentOrderId} 已完成主动查单。` });
    } catch (error) {
      if (!this.pageActive || requestVersion !== this.detailRequestVersion) {
        return;
      }
      await this.readBackAfterFailedAction(snapshot, requestVersion, error, "主动查单失败");
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
      businessChainId: this.data.detail.businessChainId || "",
      prepayOrderNo: this.data.detail.prepayOrderNo || "",
      billNo: this.data.detail.billNo,
      orderNo: this.data.detail.orderNo || "",
      customerName: this.data.detail.customerName,
      payScene: this.data.detail.payScene,
      amount: normalizeAmount(this.data.detail.amount),
      createdAt: this.data.detail.createdAt,
      successAt: this.data.detail.successAt,
      latestAttemptCompletedAt: this.data.detail.latestAttemptCompletedAt,
      channel: this.data.detail.channel,
      channelTransactionNo: this.data.detail.channelTransactionNo,
      paymentMethod: this.data.detail.paymentMethod,
      refundOrderId: this.data.detail.refundOrderId
    };
    this.stopAutoRefresh();
    const requestVersion = ++this.detailRequestVersion;
    this.setData({ actionName: "close", errorMessage: "", successMessage: "" });
    try {
      const detail = await paymentApi.close(
        snapshot.paymentOrderId, snapshot, this.data.scene.payScene
      );
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
      await this.readBackAfterFailedAction(snapshot, requestVersion, error, "关闭支付单失败");
    } finally {
      if (this.pageActive && requestVersion === this.detailRequestVersion) {
        this.setData({ actionName: "" });
        this.scheduleAutoRefresh();
      }
    }
  },

  async readBackAfterFailedAction(snapshot, requestVersion, actionError, fallbackMessage) {
    this.markUnverifiedResult(actionError.message || fallbackMessage);
    try {
      const detail = await paymentApi.getDetail(snapshot.paymentOrderId, this.data.scene.payScene);
      if (!this.pageActive || requestVersion !== this.detailRequestVersion) {
        return;
      }
      if (!this.isCurrentDetailResponse(snapshot, requestVersion, detail)) {
        throw new Error("动作失败后的支付详情与当前支付单不一致");
      }
      this.applyDetail(detail);
      this.setData({ errorMessage: `${actionError.message || fallbackMessage}；支付详情已重新读取。` });
    } catch (refreshError) {
      if (this.pageActive && requestVersion === this.detailRequestVersion) {
        this.markUnverifiedResult(`${actionError.message || fallbackMessage}；`
          + `${refreshError.message || "支付详情刷新失败"}；支付状态未经确认，请刷新支付详情，勿重复付款。`);
      }
    }
  },

  backToCashier() {
    if (this.data.actionName || this.data.staleDetail || !this.data.prepayOrderNo) {
      return;
    }
    wx.redirectTo({
      url: `/pages/cashier/index?prepayOrderNo=${encodeURIComponent(this.data.prepayOrderNo)}`
        + `&scene=${encodeURIComponent(this.data.scene.key)}`
    });
  },

  backToEntry() {
    if (this.data.actionName || this.data.staleDetail) {
      return;
    }
    wx.reLaunch({ url: `/pages/entry/index?scene=${encodeURIComponent(this.data.scene.key)}` });
  }
});
