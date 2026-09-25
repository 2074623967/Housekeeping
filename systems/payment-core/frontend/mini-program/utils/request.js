function resolveApiBaseUrl() {
  const configured = wx.getStorageSync("paymentApiBaseUrl");
  const app = getApp();
  const baseUrl = configured || (app.globalData && app.globalData.apiBaseUrl) || "";
  return String(baseUrl).replace(/\/$/, "");
}

function createBusinessError(payload, fallbackMessage) {
  const message = payload && payload.message ? payload.message : fallbackMessage;
  const code = payload && payload.code ? payload.code : "PAYMENT-MINI";
  const requestId = payload && payload.requestId ? payload.requestId : "";
  const suffix = requestId ? ` / ${requestId}` : "";
  const error = new Error(`${message} [${code}${suffix}]`);
  error.code = code;
  error.requestId = requestId;
  return error;
}

function request(path, options) {
  const requestOptions = options || {};
  return new Promise((resolve, reject) => {
    wx.request({
      url: `${resolveApiBaseUrl()}${path}`,
      method: requestOptions.method || "GET",
      data: requestOptions.data,
      header: {
        "Content-Type": "application/json",
        "X-Terminal": "MINI_PROGRAM"
      },
      success(response) {
        const payload = response.data;
        if (response.statusCode < 200 || response.statusCode >= 300) {
          reject(createBusinessError(payload, `请求失败：HTTP ${response.statusCode}`));
          return;
        }
        if (!payload || payload.code !== "0") {
          reject(createBusinessError(payload, "支付服务返回失败"));
          return;
        }
        resolve(payload.data);
      },
      fail(error) {
        const message = error && error.errMsg ? error.errMsg : "支付服务连接失败";
        const code = /timeout|超时/i.test(message)
          ? "PAYMENT-MINI-TIMEOUT"
          : "PAYMENT-MINI-NETWORK";
        reject(createBusinessError({ code }, message));
      }
    });
  });
}

const paymentApi = {
  prepay(payload) {
    return request("/api/payments/prepay", { method: "POST", data: payload });
  },
  getCashier(prepayOrderNo) {
    return request(`/api/payments/cashier/${encodeURIComponent(prepayOrderNo)}`)
      .then(normalizeCashierPage);
  },
  getDetail(paymentOrderId) {
    return request(`/api/payments/${encodeURIComponent(paymentOrderId)}`)
      .then(normalizePaymentDetail);
  },
  getRefundDetail(refundOrderId) {
    return request(`/api/refunds/${encodeURIComponent(refundOrderId)}`)
      .then(normalizeRefundDetail);
  },
  submit(payload) {
    return request("/api/payments/submit", { method: "POST", data: payload });
  },
  query(paymentOrderId) {
    return request("/api/payments/query", { method: "POST", data: { paymentOrderId } });
  },
  close(paymentOrderId) {
    return request("/api/payments/close", { method: "POST", data: { paymentOrderId } });
  }
};

function normalizeList(value) {
  return Array.isArray(value) ? value.filter((item) => item != null) : [];
}

function normalizeCashierPage(page) {
  if (!page || typeof page !== "object" || Array.isArray(page)) {
    return page;
  }
  return { ...page, channels: normalizeList(page.channels) };
}

function normalizePaymentDetail(detail) {
  if (!detail || typeof detail !== "object" || Array.isArray(detail)) {
    return detail;
  }
  return {
    ...detail,
    routeLogs: normalizeList(detail.routeLogs),
    notifyLogs: normalizeList(detail.notifyLogs),
    eventLogs: normalizeList(detail.eventLogs)
  };
}

function normalizeRefundDetail(detail) {
  if (!detail || typeof detail !== "object" || Array.isArray(detail)) {
    return detail;
  }
  return {
    ...detail,
    attempts: normalizeList(detail.attempts),
    operationLogs: normalizeList(detail.operationLogs)
  };
}

module.exports = {
  createBusinessError,
  normalizeCashierPage,
  normalizePaymentDetail,
  normalizeRefundDetail,
  paymentApi
};
