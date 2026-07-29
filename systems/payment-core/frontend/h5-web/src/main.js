import { createApp } from "vue";
import { createRouter, createWebHistory } from "vue-router";
import App from "./App.vue";
import "../../app-web/src/styles.css";
import BusinessEntryView from "../../app-web/src/views/BusinessEntryView.vue";
import CashierView from "./views/CashierView.vue";
import ResultView from "./views/ResultView.vue";

const routes = [
  { path: "/", redirect: "/balance-pay" },
  { path: "/recharge", component: BusinessEntryView, props: { terminalVariant: "h5", sceneType: "recharge" } },
  { path: "/withdraw", component: BusinessEntryView, props: { terminalVariant: "h5", sceneType: "withdraw" } },
  { path: "/transfer", component: BusinessEntryView, props: { terminalVariant: "h5", sceneType: "transfer" } },
  { path: "/balance-pay", component: BusinessEntryView, props: { terminalVariant: "h5", sceneType: "balance-pay" } },
  { path: "/cashier/:prepayOrderNo", component: CashierView },
  { path: "/payment-result/:paymentOrderId", component: ResultView }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

createApp(App).use(router).mount("#app");
