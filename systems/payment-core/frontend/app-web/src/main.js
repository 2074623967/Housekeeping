import { createApp } from "vue";
import { createRouter, createWebHistory } from "vue-router";
import App from "./App.vue";
import "./styles.css";

const routes = [
  { path: "/", redirect: "/cashier/PRE202607190002" },
  { path: "/cashier/:prepayOrderNo", component: () => import("./views/CashierView.vue") },
  { path: "/payment-result/:paymentOrderId", component: () => import("./views/ResultView.vue") }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

createApp(App).use(router).mount("#app");
