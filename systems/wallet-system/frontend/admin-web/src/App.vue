<template>
  <div>
    <div class="top-nav">
      <router-link class="top-nav__link" to="/wallet-accounts">钱包账户</router-link>
      <router-link class="top-nav__link" to="/wallet-ledgers">钱包流水</router-link>
      <router-link class="top-nav__link" to="/wallet-red-packets">红包管理</router-link>
      <router-link class="top-nav__link" to="/wallet-risk-events">风控审批</router-link>
    </div>
    <router-view />
  </div>
</template>
