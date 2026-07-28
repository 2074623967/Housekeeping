import { createApp } from "vue";
import { createRouter, createWebHistory } from "vue-router";
import App from "./App.vue";
import "./styles.css";
import WalletAccountsView from "./views/WalletAccountsView.vue";
import WalletLedgersView from "./views/WalletLedgersView.vue";
import RedPacketManagementView from "./views/RedPacketManagementView.vue";
import RiskEventManagementView from "./views/RiskEventManagementView.vue";
import MarketingFundDashboardView from "./views/MarketingFundDashboardView.vue";

const routes = [
  { path: "/", redirect: "/wallet-accounts" },
  { path: "/wallet-accounts", component: WalletAccountsView },
  { path: "/wallet-ledgers", component: WalletLedgersView },
  { path: "/wallet-marketing-funds", component: MarketingFundDashboardView },
  { path: "/wallet-red-packets", component: RedPacketManagementView },
  { path: "/wallet-risk-events", component: RiskEventManagementView }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

createApp(App).use(router).mount("#app");
