const DEFAULT_APP_WEB_BASE_URL = "http://127.0.0.1:5175";
const DEFAULT_RECONCILIATION_ADMIN_BASE_URL = "http://127.0.0.1:5180";

function resolveAppWebBaseUrl(baseUrl) {
  const normalizedBaseUrl = typeof baseUrl === "string" ? baseUrl.trim() : "";
  const url = new URL(normalizedBaseUrl || DEFAULT_APP_WEB_BASE_URL);
  if (url.protocol !== "http:" && url.protocol !== "https:") {
    throw new TypeError("App web base URL must use HTTP or HTTPS");
  }
  url.search = "";
  url.hash = "";
  return url;
}

export function buildAppWebCashierUrl(
  prepayOrderNo,
  baseUrl = import.meta.env?.VITE_APP_WEB_BASE_URL || DEFAULT_APP_WEB_BASE_URL
) {
  if (typeof prepayOrderNo !== "string" || !prepayOrderNo.trim()) {
    throw new TypeError("Prepay order number is required");
  }
  const url = resolveAppWebBaseUrl(baseUrl);
  const basePath = url.pathname.replace(/\/+$/, "");
  url.pathname = `${basePath}/cashier/${encodeURIComponent(prepayOrderNo)}`;
  return url.toString();
}

export function buildAppWebRefundProgressUrl(
  refundOrderId,
  baseUrl = import.meta.env?.VITE_APP_WEB_BASE_URL || DEFAULT_APP_WEB_BASE_URL
) {
  if (typeof refundOrderId !== "string" || !refundOrderId.trim()) {
    throw new TypeError("Refund order number is required");
  }
  const url = resolveAppWebBaseUrl(baseUrl);
  const basePath = url.pathname.replace(/\/+$/, "");
  url.pathname = `${basePath}/refund-progress/${encodeURIComponent(refundOrderId.trim())}`;
  return url.toString();
}

export function buildReconciliationChainUrl(
  businessChainId,
  baseUrl = import.meta.env?.VITE_RECONCILIATION_ADMIN_BASE_URL || DEFAULT_RECONCILIATION_ADMIN_BASE_URL
) {
  if (typeof businessChainId !== "string" || !businessChainId.trim()) {
    throw new TypeError("Business chain ID is required");
  }
  const url = resolveAppWebBaseUrl(baseUrl);
  url.search = `?businessChainId=${encodeURIComponent(businessChainId.trim())}`;
  return url.toString();
}
