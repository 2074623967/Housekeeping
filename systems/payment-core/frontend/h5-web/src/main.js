import { createApp } from "vue";
import { createRouter, createWebHistory } from "vue-router";
import App from "./App.vue";
import "../../app-web/src/styles.css";
import CashierView from "./views/CashierView.vue";
import ResultView from "./views/ResultView.vue";

const routes = [
  { path: "/", redirect: "/cashier/PRE202607190002" },
  { path: "/cashier/:prepayOrderNo", component: CashierView },
  { path: "/payment-result/:paymentOrderId", component: ResultView }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

createApp(App).use(router).mount("#app");
