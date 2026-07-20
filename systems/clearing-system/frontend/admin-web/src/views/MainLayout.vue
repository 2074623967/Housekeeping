<script setup>
import { RouterLink, RouterView } from "vue-router";
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <h1>清分清算系统</h1>
        <p>clearing-system · second stage</p>
      </div>
      <RouterLink class="nav-link" to="/dashboard">工作台</RouterLink>
      <RouterLink class="nav-link" to="/batches">清分批次</RouterLink>
      <RouterLink class="nav-link" to="/orders">清分结果</RouterLink>
      <RouterLink class="nav-link" to="/rules">清分规则</RouterLink>
      <RouterLink class="nav-link" to="/fees">费用规则</RouterLink>
      <RouterLink class="nav-link" to="/shares">分账明细</RouterLink>
      <RouterLink class="nav-link" to="/events">清分事件</RouterLink>
    </aside>
    <main class="main">
      <RouterView />
    </main>
  </div>
</template>
