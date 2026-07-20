<script setup>
import { RouterLink, RouterView } from "vue-router";
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <h1>账户账务系统</h1>
        <p>accounting-system · second stage</p>
      </div>
      <RouterLink class="nav-link" to="/dashboard">工作台</RouterLink>
      <RouterLink class="nav-link" to="/subjects">账户主体</RouterLink>
      <RouterLink class="nav-link" to="/accounts">账户管理</RouterLink>
      <RouterLink class="nav-link" to="/ledgers">账户流水</RouterLink>
      <RouterLink class="nav-link" to="/freezes">冻结单</RouterLink>
      <RouterLink class="nav-link" to="/adjustments">调账单</RouterLink>
      <RouterLink class="nav-link" to="/events">账户事件</RouterLink>
    </aside>
    <main class="main">
      <RouterView />
    </main>
  </div>
</template>
