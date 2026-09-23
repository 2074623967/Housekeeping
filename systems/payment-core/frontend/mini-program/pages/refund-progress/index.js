const { paymentApi } = require("../../utils/request");

const STATUS_META = {
  REVIEWING: { title: "等待退款审核", tone: "warn", hint: "退款申请已提交，当前等待业务侧完成审核。" },
  PROCESSING: { title: "退款处理中", tone: "warn", hint: "退款已进入渠道处理，请等待最终结果。" },
  SUCCESS: { title: "退款成功", tone: "success", hint: "退款已获得成功终态，请以原支付账户到账时间为准。" },
  FAIL: { title: "退款未成功", tone: "danger", hint: "本次退款未获得成功终态，请联系服务方处理后续安排。" }
};

function resolveStatusMeta(status) {
  return STATUS_META[status] || { title: status || "退款状态", tone: "warn", hint: "当前退款状态已记录，请以服务方后续通知为准。" };
}

function buildTimeline(detail) {
  const logs = (detail && detail.operationLogs || []).map((item) => ({
    key: `log-${item.logNo}`,
    title: item.actionName || item.actionCode || "退款状态更新",
    time: item.createdAt || "-",
    detail: `${item.fromStatus || "-"} → ${item.toStatus || "-"} / ${item.operationRemark || "无备注"}`
  }));
  const attempts = (detail && detail.attempts || []).map((item) => ({
    key: `attempt-${item.refundAttemptNo}`,
    title: `渠道尝试 ${item.attemptStatus || "-"}`,
    time: item.completedAt || item.submittedAt || item.createdAt || "-",
    detail: `${item.channelCode || "-"} / 渠道退款单号 ${item.channelRefundNo || "-"}`
  }));
  return logs.concat(attempts);
}

Page({
  data: {
    refundOrderId: "",
    detail: null,
    meta: resolveStatusMeta(""),
    timeline: [],
    loading: true,
    refreshing: false,
    errorMessage: "",
    successMessage: ""
  },

  onLoad(options) {
    this.pageActive = true;
    this.requestVersion = 0;
    this.pollAttempts = 0;
    const refundOrderId = options && options.refundOrderId
      ? decodeURIComponent(options.refundOrderId)
      : "";
    this.setData({ refundOrderId });
    this.loadDetail();
  },

  onHide() {
    this.pageActive = false;
    this.stopPolling();
    this.requestVersion += 1;
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
    if (!this.pageActive || !this.data.detail || ["SUCCESS", "FAIL"].includes(this.data.detail.status) || this.pollAttempts >= 5) {
      return;
    }
    this.pollTimer = setTimeout(() => {
      this.pollTimer = null;
      this.refreshDetail(true);
    }, 8000);
  },

  applyDetail(detail) {
    if (!detail || detail.refundOrderId !== this.data.refundOrderId) {
      return false;
    }
    this.setData({
      detail,
      meta: resolveStatusMeta(detail.status),
      timeline: buildTimeline(detail)
    });
    return true;
  },

  async loadDetail() {
    if (!this.data.refundOrderId) {
      this.setData({ loading: false, errorMessage: "缺少退款单号，无法查询退款进度。" });
      return;
    }
    this.stopPolling();
    this.pollAttempts = 0;
    const requestVersion = ++this.requestVersion;
    this.setData({ loading: true, errorMessage: "", successMessage: "", detail: null, timeline: [] });
    try {
      const detail = await paymentApi.getRefundDetail(this.data.refundOrderId);
      if (!this.pageActive || requestVersion !== this.requestVersion) {
        return;
      }
      if (!this.applyDetail(detail)) {
        throw new Error("退款详情为空或与当前退款单不一致");
      }
    } catch (error) {
      if (!this.pageActive || requestVersion !== this.requestVersion) {
        return;
      }
      this.setData({ errorMessage: error.message || "退款进度加载失败" });
    } finally {
      if (this.pageActive && requestVersion === this.requestVersion) {
        this.setData({ loading: false });
        this.schedulePolling();
      }
    }
  },

  async refreshDetail(isAutomatic = false) {
    const automatic = isAutomatic === true;
    if (!this.pageActive || !this.data.refundOrderId || this.data.loading || this.data.refreshing) {
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
      const detail = await paymentApi.getRefundDetail(this.data.refundOrderId);
      if (!this.pageActive || requestVersion !== this.requestVersion) {
        return;
      }
      if (!this.applyDetail(detail)) {
        throw new Error("退款刷新响应为空或与当前退款单不一致");
      }
      if (!automatic) {
        this.setData({ successMessage: "退款进度已刷新。" });
      }
    } catch (error) {
      if (this.pageActive && requestVersion === this.requestVersion && !automatic) {
        this.setData({ errorMessage: error.message || "退款进度刷新失败" });
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
    if (this.data.refreshing) {
      return;
    }
    wx.reLaunch({ url: "/pages/entry/index?scene=balance-pay" });
  }
});
