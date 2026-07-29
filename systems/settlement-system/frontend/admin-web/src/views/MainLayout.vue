<script setup>
import { RouterLink, RouterView } from "vue-router";
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <h1>结算出款系统</h1>
        <p>settlement-system · second stage</p>
      </div>
      <RouterLink class="nav-link" to="/dashboard">工作台</RouterLink>
      <RouterLink class="nav-link" to="/batches">结算批次</RouterLink>
      <RouterLink class="nav-link" to="/orders">结算单</RouterLink>
      <RouterLink class="nav-link" to="/detail">结算详情</RouterLink>
      <RouterLink class="nav-link" to="/audit">审核页</RouterLink>
      <RouterLink class="nav-link" to="/payouts">出款批次</RouterLink>
      <RouterLink class="nav-link" to="/events">结算事件</RouterLink>
    </aside>
    <main class="main">
      <RouterView />
    </main>
  </div>
</template>
