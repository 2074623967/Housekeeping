import { createApp } from "vue";
import { createRouter, createWebHistory } from "vue-router";
import App from "./App.vue";
import "./styles.css";
import BusinessEntryView from "../../shared/src/views/BusinessEntryView.vue";

const routes = [
  { path: "/", redirect: "/balance-pay" },
  { path: "/recharge", component: BusinessEntryView, props: { terminalVariant: "pc", sceneType: "recharge" } },
  { path: "/withdraw", component: BusinessEntryView, props: { terminalVariant: "pc", sceneType: "withdraw" } },
  { path: "/transfer", component: BusinessEntryView, props: { terminalVariant: "pc", sceneType: "transfer" } },
  { path: "/balance-pay", component: BusinessEntryView, props: { terminalVariant: "pc", sceneType: "balance-pay" } },
  { path: "/cashier/:prepayOrderNo", component: () => import("./views/CashierView.vue") },
  { path: "/payment-result/:paymentOrderId", component: () => import("./views/ResultView.vue") }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

createApp(App).use(router).mount("#app");
