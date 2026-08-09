<template>
  <div>
    <header class="hero">
      <div>
        <p class="eyebrow">wallet-account / phase 1</p>
        <h1>钱包账户底座控制台</h1>
        <p class="hero__copy">先把主体、账户、余额、流水收成统一底座，再让钱包核心、提现和账务往上长。</p>
      </div>
      <nav class="top-nav">
        <router-link class="top-nav__link" to="/wallet-accounts">钱包账户</router-link>
        <router-link class="top-nav__link" to="/wallet-flows">钱包流水</router-link>
      </nav>
    </header>
    <router-view />
  </div>
</template>
