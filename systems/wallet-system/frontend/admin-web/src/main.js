import { createApp } from "vue";
import { createRouter, createWebHistory } from "vue-router";
import App from "./App.vue";
import "./styles.css";
import WalletAccountsView from "./views/WalletAccountsView.vue";

const routes = [
  { path: "/", redirect: "/wallet-accounts" },
  { path: "/wallet-accounts", component: WalletAccountsView }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

createApp(App).use(router).mount("#app");
