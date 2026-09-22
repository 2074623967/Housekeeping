import { createApp } from "vue";
import { createRouter, createWebHistory } from "vue-router";
import App from "./App.vue";
import "./styles.css";
import BusinessEntryView from "../../shared/src/views/BusinessEntryView.vue";
import {
  businessEntryRoutes,
  cashierRoutePath,
  paymentResultRoutePath
} from "../../shared/src/router/terminalRoutes.js";

const routes = [
  { path: "/", redirect: "/balance-pay" },
  ...businessEntryRoutes.map(({ path, sceneType }) => ({
    path,
    component: BusinessEntryView,
    props: { terminalVariant: "pc", sceneType }
  })),
  { path: cashierRoutePath, name: "cashier", component: () => import("./views/CashierView.vue") },
  { path: paymentResultRoutePath, name: "payment-result", component: () => import("./views/ResultView.vue") }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

createApp(App).use(router).mount("#app");
