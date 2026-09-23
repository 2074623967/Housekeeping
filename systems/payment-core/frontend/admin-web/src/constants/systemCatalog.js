const DEFAULT_SYSTEM_BASE_URLS = {
  paymentCore: "http://127.0.0.1:5174",
  paymentApp: "http://127.0.0.1:5175",
  paymentH5: "http://127.0.0.1:5176",
  paymentPc: "http://127.0.0.1:5177",
  walletAccount: "http://127.0.0.1:5178",
  refundCenter: "http://127.0.0.1:5179",
  reconciliation: "http://127.0.0.1:5180",
  deposit: "http://127.0.0.1:5181",
  opsConfig: "http://127.0.0.1:5182",
  riskControl: "http://127.0.0.1:5183",
  gatewayAccess: "http://127.0.0.1:5184",
  wallet: "http://127.0.0.1:5185",
  clearing: "http://127.0.0.1:5273",
  settlement: "http://127.0.0.1:5274",
  accounting: "http://127.0.0.1:5275"
};

const envBaseUrls = import.meta.env || {};

function resolveBaseUrl(envKey, fallback) {
  const value = typeof envBaseUrls[envKey] === "string" ? envBaseUrls[envKey].trim() : "";
  return value || fallback;
}

export const systemCatalog = [
  {
    group: "支付核心",
    code: "payment-core",
    name: "支付运营后台",
    description: "订单、支付单、退款、异常、任务和日终",
    baseUrl: resolveBaseUrl("VITE_PAYMENT_ADMIN_BASE_URL", DEFAULT_SYSTEM_BASE_URLS.paymentCore),
    kind: "admin",
    current: true
  },
  {
    group: "支付核心",
    code: "payment-app",
    name: "App 支付端",
    description: "充值、提现、转账、余额支付和收银台",
    baseUrl: resolveBaseUrl("VITE_PAYMENT_APP_BASE_URL", DEFAULT_SYSTEM_BASE_URLS.paymentApp),
    kind: "terminal"
  },
  {
    group: "支付核心",
    code: "payment-h5",
    name: "H5 支付端",
    description: "移动端业务入口、收银台和结果页",
    baseUrl: resolveBaseUrl("VITE_PAYMENT_H5_BASE_URL", DEFAULT_SYSTEM_BASE_URLS.paymentH5),
    kind: "terminal"
  },
  {
    group: "支付核心",
    code: "payment-pc",
    name: "PC 支付端",
    description: "桌面端收银台和支付结果页",
    baseUrl: resolveBaseUrl("VITE_PAYMENT_PC_BASE_URL", DEFAULT_SYSTEM_BASE_URLS.paymentPc),
    kind: "terminal"
  },
  {
    group: "支付核心",
    code: "payment-mini-program",
    name: "微信小程序支付端",
    description: "原生小程序业务入口、支付和退款进度",
    kind: "integration",
    entryType: "path",
    entryValue: "systems/payment-core/frontend/mini-program"
  },
  {
    group: "支付核心",
    code: "payment-api-client",
    name: "API 支付调用端",
    description: "零依赖支付与退款服务调用命令",
    kind: "integration",
    entryType: "command",
    entryValue: "cd systems/payment-core/frontend/api-client && npm test"
  },
  {
    group: "资金域",
    code: "wallet-account",
    name: "钱包账户底座",
    description: "主体、账户、余额和钱包流水",
    baseUrl: resolveBaseUrl("VITE_WALLET_ACCOUNT_BASE_URL", DEFAULT_SYSTEM_BASE_URLS.walletAccount),
    kind: "admin"
  },
  {
    group: "资金域",
    code: "accounting-system",
    name: "账务系统",
    description: "账户、流水、冻结解冻和调账审核",
    baseUrl: resolveBaseUrl("VITE_ACCOUNTING_BASE_URL", DEFAULT_SYSTEM_BASE_URLS.accounting),
    kind: "admin"
  },
  {
    group: "资金域",
    code: "clearing-system",
    name: "清分系统",
    description: "清分批次、分润、费项和事件重放",
    baseUrl: resolveBaseUrl("VITE_CLEARING_BASE_URL", DEFAULT_SYSTEM_BASE_URLS.clearing),
    kind: "admin"
  },
  {
    group: "资金域",
    code: "settlement-system",
    name: "结算系统",
    description: "结算审核、出款批次和失败重试",
    baseUrl: resolveBaseUrl("VITE_SETTLEMENT_BASE_URL", DEFAULT_SYSTEM_BASE_URLS.settlement),
    kind: "admin"
  },
  {
    group: "资金域",
    code: "refund-center",
    name: "退款中心",
    description: "退款申请、审核、回调和事件派发",
    baseUrl: resolveBaseUrl("VITE_REFUND_CENTER_BASE_URL", DEFAULT_SYSTEM_BASE_URLS.refundCenter),
    kind: "admin"
  },
  {
    group: "资金域",
    code: "reconciliation-system",
    name: "对账系统",
    description: "渠道账单、平台事实、差异和准入",
    baseUrl: resolveBaseUrl("VITE_RECONCILIATION_BASE_URL", DEFAULT_SYSTEM_BASE_URLS.reconciliation),
    kind: "admin"
  },
  {
    group: "资金域",
    code: "deposit-system",
    name: "保证金系统",
    description: "收取、冻结、扣罚、退还和欠款抵扣",
    baseUrl: resolveBaseUrl("VITE_DEPOSIT_BASE_URL", DEFAULT_SYSTEM_BASE_URLS.deposit),
    kind: "admin"
  },
  {
    group: "平台治理",
    code: "ops-config-system",
    name: "运营配置中心",
    description: "业务线、渠道、路由和配置发布",
    baseUrl: resolveBaseUrl("VITE_OPS_CONFIG_BASE_URL", DEFAULT_SYSTEM_BASE_URLS.opsConfig),
    kind: "admin"
  },
  {
    group: "平台治理",
    code: "risk-control-system",
    name: "风控中心",
    description: "策略、限额、黑名单和人工复核",
    baseUrl: resolveBaseUrl("VITE_RISK_CONTROL_BASE_URL", DEFAULT_SYSTEM_BASE_URLS.riskControl),
    kind: "admin"
  },
  {
    group: "平台治理",
    code: "gateway-access",
    name: "网关接入管理",
    description: "应用、网关、证书、权限和灰度路由",
    baseUrl: resolveBaseUrl("VITE_GATEWAY_ACCESS_BASE_URL", DEFAULT_SYSTEM_BASE_URLS.gatewayAccess),
    kind: "admin"
  },
  {
    group: "平台治理",
    code: "wallet-system",
    name: "钱包与营销资金",
    description: "余额支付、充值、提现、转账和营销资金",
    baseUrl: resolveBaseUrl("VITE_WALLET_BASE_URL", DEFAULT_SYSTEM_BASE_URLS.wallet),
    kind: "admin"
  }
];

export function systemGroups(catalog = systemCatalog) {
  return [...new Set(catalog.map((item) => item.group))];
}
