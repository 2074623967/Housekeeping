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
      { label: "支付单管理", to: "/payments" },
      { label: "退款单管理", to: "/refunds" }
    ]
  },
  {
    title: "结算",
    links: [{ label: "服务者结算单", to: "/worker-settlements" }]
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
