<script setup>
const navGroups = [
  {
    title: "总览",
    links: [{ label: "工作台", to: "/dashboard" }]
  },
  {
    title: "交易支付",
    links: [
      { label: "订单中心", to: "/orders" },
      { label: "账单中心", to: "/bills" },
      { label: "统一支付记录", to: "/payment-records" },
      { label: "微信支付宝支付记录", to: "/payment-records/wechat-alipay" },
      { label: "银行卡支付记录", to: "/payment-records/bank-card" },
      { label: "支付流水查询", to: "/payment-flows" },
      { label: "支付事件出站", to: "/payment-events" },
      { label: "收银台会话", to: "/cashier-sessions" },
      { label: "支付请求管理", to: "/payment-requests" },
      { label: "支付处理日志", to: "/payment-logs" },
      { label: "支付单管理", to: "/payments" },
      { label: "退款单管理", to: "/refunds" }
    ]
  },
  {
    title: "结算",
    links: [{ label: "服务者结算单", to: "/worker-settlements" }]
  },
  {
    title: "配置中心",
    links: [{ label: "支付渠道与路由", to: "/payment-config" }]
  },
  {
    title: "监控分析",
    links: [{ label: "支付监控分析", to: "/payment-monitor" }]
  }
];
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <h1>家政支付系统</h1>
        <p>Payment Core / Admin Web</p>
      </div>
      <div v-for="group in navGroups" :key="group.title" class="nav-group">
        <div class="nav-group-title">{{ group.title }}</div>
        <RouterLink
          v-for="link in group.links"
          :key="link.to"
          :to="link.to"
          class="nav-link"
        >
          {{ link.label }}
        </RouterLink>
      </div>
    </aside>
    <main class="main">
      <router-view />
    </main>
  </div>
</template>
