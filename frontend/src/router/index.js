import { createRouter, createWebHistory } from "vue-router";
import MainLayout from "../views/MainLayout.vue";
import DashboardView from "../views/DashboardView.vue";
import OrdersView from "../views/OrdersView.vue";
import PaymentsView from "../views/PaymentsView.vue";
import RefundsView from "../views/RefundsView.vue";
import WorkerSettlementsView from "../views/WorkerSettlementsView.vue";

const routes = [
  {
    path: "/",
    component: MainLayout,
    children: [
      { path: "", redirect: "/dashboard" },
      { path: "dashboard", component: DashboardView },
      { path: "orders", component: OrdersView },
      { path: "payments", component: PaymentsView },
      { path: "refunds", component: RefundsView },
      { path: "worker-settlements", component: WorkerSettlementsView }
    ]
  }
];

export default createRouter({
  history: createWebHistory(),
  routes
});
