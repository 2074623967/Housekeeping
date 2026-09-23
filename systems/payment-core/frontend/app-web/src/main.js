import { createApp } from "vue";
import { createRouter, createWebHistory } from "vue-router";
import App from "./App.vue";
import "./styles.css";
import BusinessEntryView from "./views/BusinessEntryView.vue";
import RefundProgressView from "./views/RefundProgressView.vue";
import {
  businessEntryRoutes,
  cashierRoutePath,
  paymentResultRoutePath,
  refundProgressRoutePath
} from "../../shared/src/router/terminalRoutes.js";

const routes = [
  { path: "/", redirect: "/balance-pay" },
  ...businessEntryRoutes.map(({ path, sceneType }) => ({
    path,
    component: BusinessEntryView,
    props: { terminalVariant: "app", sceneType }
  })),
  { path: cashierRoutePath, name: "cashier", component: () => import("./views/CashierView.vue") },
  { path: paymentResultRoutePath, name: "payment-result", component: () => import("./views/ResultView.vue") },
  { path: refundProgressRoutePath, name: "refund-progress", component: RefundProgressView }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

createApp(App).use(router).mount("#app");
