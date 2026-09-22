import { createApp } from "vue";
import { createRouter, createWebHistory } from "vue-router";
import App from "./App.vue";
import "../../shared/src/styles/terminal.css";
import BusinessEntryView from "../../shared/src/views/BusinessEntryView.vue";
import CashierView from "./views/CashierView.vue";
import ResultView from "./views/ResultView.vue";
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
    props: { terminalVariant: "h5", sceneType }
  })),
  { path: cashierRoutePath, name: "cashier", component: CashierView },
  { path: paymentResultRoutePath, name: "payment-result", component: ResultView }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

createApp(App).use(router).mount("#app");
