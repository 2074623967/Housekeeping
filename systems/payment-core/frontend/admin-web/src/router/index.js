import { createRouter, createWebHistory } from "vue-router";
import MainLayout from "../views/MainLayout.vue";
import DashboardView from "../views/DashboardView.vue";
import OrdersView from "../views/OrdersView.vue";
import BillsView from "../views/BillsView.vue";
import PaymentFlowsView from "../views/PaymentFlowsView.vue";
import CashierSessionsView from "../views/CashierSessionsView.vue";
import PaymentsView from "../views/PaymentsView.vue";
import PaymentDetailView from "../views/PaymentDetailView.vue";
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
      { path: "bills", component: BillsView },
      { path: "payment-flows", component: PaymentFlowsView },
      { path: "cashier-sessions", component: CashierSessionsView },
      { path: "payments", component: PaymentsView },
      { path: "payments/:paymentOrderId", component: PaymentDetailView },
      { path: "refunds", component: RefundsView },
      { path: "worker-settlements", component: WorkerSettlementsView }
    ]
  }
];

export default createRouter({
  history: createWebHistory(),
  routes
});
