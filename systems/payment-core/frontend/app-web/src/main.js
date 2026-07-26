import { createApp } from "vue";
import { createRouter, createWebHistory } from "vue-router";
import App from "./App.vue";
import "./styles.css";
import BusinessEntryView from "./views/BusinessEntryView.vue";

const routes = [
  { path: "/", redirect: "/balance-pay" },
  { path: "/recharge", component: BusinessEntryView, props: { terminalVariant: "app", sceneType: "recharge" } },
  { path: "/withdraw", component: BusinessEntryView, props: { terminalVariant: "app", sceneType: "withdraw" } },
  { path: "/transfer", component: BusinessEntryView, props: { terminalVariant: "app", sceneType: "transfer" } },
  { path: "/balance-pay", component: BusinessEntryView, props: { terminalVariant: "app", sceneType: "balance-pay" } },
  { path: "/cashier/:prepayOrderNo", component: () => import("./views/CashierView.vue") },
  { path: "/payment-result/:paymentOrderId", component: () => import("./views/ResultView.vue") }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

createApp(App).use(router).mount("#app");
