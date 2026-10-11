const { paymentApi } = require("../../utils/request");
const { resolveLinkedScene, resolveScene } = require("../../utils/payment");

const STATUS_META = {
  REVIEWING: { title: "等待退款审核", tone: "warn", hint: "退款申请已提交，当前等待业务侧完成审核。" },
  PROCESSING: { title: "退款处理中", tone: "warn", hint: "退款正在处理，渠道受理与最终结果仍需核对。" },
  SUCCESS: { title: "退款成功", tone: "success", hint: "退款已获得成功终态，请以原支付账户到账时间为准。" },
  FAIL: { title: "退款未成功", tone: "danger", hint: "本次退款未获得成功终态，请联系服务方处理后续安排。" }
};
const UNVERIFIED_META = {
  title: "退款进度待核查",
  tone: "warn",
  hint: "暂无法确认最新退款状态，请勿重复申请退款，保留退款单号并联系服务方核查。"
};

function resolveStatusMeta(status, attempts) {
  if (status === "PROCESSING" && attempts && attempts[0] && attempts[0].attemptStatus === "UNKNOWN") {
    return { title: "退款渠道结果待核查", tone: "warn",
      hint: "退款请求已发出，但渠道是否受理尚未确认；请勿重复申请，等待服务方查单或回调。" };
  }
  return STATUS_META[status] || { title: status || "退款状态", tone: "warn", hint: "当前退款状态已记录，请以服务方后续通知为准。" };
}

function normalizeAmount(value) {
  const normalized = String(value == null ? "" : value).replace(/[,¥]/g, "").trim();
  if (!/^(?:\d{1,16}(?:\.\d{1,2})?|\.\d{1,2})$/.test(normalized)) {
    return "";
  }
  const [integerPart, fractionPart] = normalized.split(".");
  const normalizedInteger = (integerPart || "").replace(/^0+/, "") || "0";
  const normalizedFraction = (fractionPart || "").padEnd(2, "0");
  return normalizedInteger === "0" && normalizedFraction === "00"
    ? ""
    : `${normalizedInteger}.${normalizedFraction}`;
}

function normalizeIdentifier(value) {
  return typeof value === "string" ? value.trim() : "";
}

function matchesProgressTimestamp(snapshotValue, detailValue, optional = false) {
  if (optional && (snapshotValue == null || snapshotValue === "-")) {
    return true;
  }
  return normalizeIdentifier(snapshotValue).replace("T", " ")
    === normalizeIdentifier(detailValue).replace("T", " ");
}

function formatRefundTime(value) {
  if (typeof value !== "string" || !value.trim()) {
    return "-";
  }
  return value.trim().replace("T", " ");
}

function buildTimeline(detail) {
  const logs = (detail && detail.operationLogs || []).map((item) => ({
    key: `log-${item.logNo}`,
    title: item.actionName || item.actionCode || "退款状态更新",
    time: formatRefundTime(item.createdAt),
    detail: `${item.fromStatus || "-"} → ${item.toStatus || "-"} / ${item.operationRemark || "无备注"}`
  }));
  const attempts = (detail && detail.attempts || []).map((item) => ({
    key: `attempt-${item.refundAttemptNo}`,
    title: `渠道尝试 ${item.attemptStatus || "-"}`,
    time: formatRefundTime(item.completedAt || item.submittedAt || item.createdAt),
    detail: `${item.channelCode || "-"} / 渠道退款单号 ${item.channelRefundNo || "-"}`
  }));
  return logs.concat(attempts);
}

Page({
  data: {
    refundOrderId: "",
    scene: resolveScene("balance-pay"),
    detail: null,
    meta: resolveStatusMeta(""),
    timeline: [],
    loading: true,
    refreshing: false,
    errorMessage: "",
    successMessage: "",
    staleDetail: false,
    pollingPaused: false
  },

  onLoad(options) {
    this.pageActive = true;
    this.requestVersion = 0;
    this.pollAttempts = 0;
    const refundOrderId = options && options.refundOrderId
      ? decodeURIComponent(options.refundOrderId).trim()
      : "";
    const sceneKey = options && Object.prototype.hasOwnProperty.call(options, "scene")
      ? decodeURIComponent(options.scene)
      : null;
    const payScene = options && Object.prototype.hasOwnProperty.call(options, "payScene")
      ? decodeURIComponent(options.payScene)
      : null;
    this.setData({ refundOrderId, scene: resolveLinkedScene(sceneKey, payScene) });
    this.loadDetail();
  },

  onHide() {
    this.pageActive = false;
    this.stopPolling();
    this.requestVersion += 1;
    this.setData({ loading: false, refreshing: false });
  },

  onShow() {
    const shouldReload = this.pageActive === false && Boolean(this.data.refundOrderId);
    this.pageActive = true;
    if (shouldReload) {
      this.loadDetail();
    }
  },

  onUnload() {
    this.pageActive = false;
    this.stopPolling();
    this.requestVersion += 1;
  },

  stopPolling() {
    if (this.pollTimer) {
      clearTimeout(this.pollTimer);
      this.pollTimer = null;
    }
  },

  schedulePolling() {
    this.stopPolling();
    const detail = this.data.detail;
    this.setData({ pollingPaused: Boolean(detail
      && !["SUCCESS", "FAIL"].includes(detail.status)
      && this.pollAttempts >= 5) });
    if (!this.pageActive || !detail || ["SUCCESS", "FAIL"].includes(detail.status) || this.pollAttempts >= 5) {
      return;
    }
    this.pollTimer = setTimeout(() => {
      this.pollTimer = null;
      this.refreshDetail(true);
    }, 8000);
  },

  applyDetail(detail) {
    const currentDetail = this.data.detail;
    const normalizedRefundOrderId = normalizeIdentifier(detail && detail.refundOrderId);
    const normalizedPaymentOrderId = normalizeIdentifier(detail && detail.paymentOrderId);
    const normalizedBusinessChainId = normalizeIdentifier(detail && detail.businessChainId);
    if (!detail
        || normalizedRefundOrderId !== this.data.refundOrderId
        || !normalizedPaymentOrderId
        || normalizedBusinessChainId !== `CHAIN-${normalizedPaymentOrderId}`
        || (currentDetail
          && (normalizedPaymentOrderId !== currentDetail.paymentOrderId
            || normalizedBusinessChainId !== currentDetail.businessChainId
            || (currentDetail.orderNo && detail.orderNo !== currentDetail.orderNo)
            || (currentDetail.customerName != null
              && detail.customerName != null
              && normalizeIdentifier(detail.customerName) !== normalizeIdentifier(currentDetail.customerName))
            || normalizeAmount(detail.refundAmount) !== normalizeAmount(currentDetail.refundAmount)
            || normalizeAmount(detail.paidAmount) !== normalizeAmount(currentDetail.paidAmount)
            || !matchesProgressTimestamp(currentDetail.appliedAt, detail.appliedAt)
            || !matchesProgressTimestamp(currentDetail.channelSubmittedAt, detail.channelSubmittedAt, true)
            || !matchesProgressTimestamp(currentDetail.successAt, detail.successAt, true)
            || !matchesProgressTimestamp(currentDetail.terminalAt, detail.terminalAt, true)
            || normalizeIdentifier(detail.refundMethod) !== normalizeIdentifier(currentDetail.refundMethod)
            || normalizeIdentifier(detail.refundReason) !== normalizeIdentifier(currentDetail.refundReason)
            || (currentDetail.paymentMethod != null
              && detail.paymentMethod != null
              && normalizeIdentifier(detail.paymentMethod) !== normalizeIdentifier(currentDetail.paymentMethod))
            || (currentDetail.payScene != null
              && detail.payScene != null
              && normalizeIdentifier(detail.payScene) !== normalizeIdentifier(currentDetail.payScene))
            || (currentDetail.channelCode != null
              && normalizeIdentifier(detail.channelCode) !== normalizeIdentifier(currentDetail.channelCode))
            || (currentDetail.channelRefundNo != null
              && normalizeIdentifier(detail.channelRefundNo) !== normalizeIdentifier(currentDetail.channelRefundNo))))) {
      return false;
    }
    const normalizedDetail = {
      ...detail,
      refundOrderId: normalizedRefundOrderId,
      paymentOrderId: normalizedPaymentOrderId,
      businessChainId: normalizedBusinessChainId
    };
    this.setData({
      detail: {
        ...normalizedDetail,
        appliedAtDisplay: formatRefundTime(normalizedDetail.appliedAt),
        successAtDisplay: formatRefundTime(normalizedDetail.successAt),
        terminalAtDisplay: formatRefundTime(normalizedDetail.terminalAt),
        channelSubmittedAtDisplay: formatRefundTime(normalizedDetail.channelSubmittedAt)
      },
      meta: resolveStatusMeta(normalizedDetail.status, normalizedDetail.attempts),
      staleDetail: false,
      errorMessage: "",
      successMessage: "",
      timeline: buildTimeline(normalizedDetail)
    });
    return true;
  },

  async loadDetail() {
    if (!this.data.scene.payScene) {
      this.setData({ loading: false, meta: UNVERIFIED_META,
        errorMessage: "当前小程序暂不支持该业务场景，请返回业务入口重新选择。" });
      return;
    }
    if (!this.data.refundOrderId) {
      this.setData({ loading: false, meta: UNVERIFIED_META,
        errorMessage: "缺少退款单号，无法查询退款进度。" });
      return;
    }
    this.stopPolling();
    const requestVersion = ++this.requestVersion;
    this.setData({ loading: true, refreshing: false, staleDetail: Boolean(this.data.detail),
      meta: this.data.detail ? UNVERIFIED_META : resolveStatusMeta(""),
      errorMessage: "", successMessage: "" });
    try {
      const detail = await paymentApi.getRefundDetail(
        this.data.refundOrderId,
        this.data.scene.payScene
      );
      if (!this.pageActive || requestVersion !== this.requestVersion) {
        return;
      }
      if (!this.applyDetail(detail)) {
        throw new Error("退款详情为空或与当前退款单不一致");
      }
      this.pollAttempts = 0;
    } catch (error) {
      if (!this.pageActive || requestVersion !== this.requestVersion) {
        return;
      }
      this.setData({ meta: UNVERIFIED_META, staleDetail: Boolean(this.data.detail),
        errorMessage: this.data.detail
        ? `刷新失败，当前显示上次成功查询状态：${error.message || "退款进度加载失败"}`
        : error.message || "退款进度加载失败" });
    } finally {
      if (this.pageActive && requestVersion === this.requestVersion) {
        this.setData({ loading: false });
        this.schedulePolling();
      }
    }
  },

  async refreshDetail(isAutomatic = false) {
    const automatic = isAutomatic === true;
    if (!this.pageActive || !this.data.scene.payScene || !this.data.detail
      || !this.data.refundOrderId || this.data.loading || this.data.refreshing) {
      return;
    }
    if (automatic && (this.pollAttempts >= 5 || !this.data.detail
      || ["SUCCESS", "FAIL"].includes(this.data.detail.status))) {
      return;
    }
    if (automatic) {
      this.pollAttempts += 1;
    } else {
      this.stopPolling();
      this.setData({ refreshing: true, errorMessage: "", successMessage: "" });
    }
    const requestVersion = ++this.requestVersion;
    try {
      const detail = await paymentApi.getRefundDetail(
        this.data.refundOrderId,
        this.data.scene.payScene
      );
      if (!this.pageActive || requestVersion !== this.requestVersion) {
        return;
      }
      if (!this.applyDetail(detail)) {
        throw new Error("退款刷新响应为空或与当前退款单不一致");
      }
      if (!automatic) {
        this.pollAttempts = 0;
        this.setData({ successMessage: "退款进度已刷新。" });
      }
    } catch (error) {
      if (this.pageActive && requestVersion === this.requestVersion) {
        this.setData({ meta: UNVERIFIED_META, staleDetail: Boolean(this.data.detail),
          errorMessage: automatic ? "自动刷新未能确认最新退款状态，当前仅供核对上次读取的事实。"
            : this.data.detail
              ? `刷新失败，当前显示上次成功查询状态：${error.message || "退款进度刷新失败"}`
              : error.message || "退款进度刷新失败" });
      }
    } finally {
      if (this.pageActive && requestVersion === this.requestVersion) {
        if (!automatic) {
          this.setData({ refreshing: false });
        }
        this.schedulePolling();
      }
    }
  },

  backToEntry() {
    if (this.data.refreshing || this.data.staleDetail) {
      return;
    }
    wx.reLaunch({ url: `/pages/entry/index?scene=${encodeURIComponent(this.data.scene.key)}` });
  }
});
