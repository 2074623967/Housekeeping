import { createRouter, createWebHistory } from "vue-router";
import MainLayout from "../views/MainLayout.vue";
import DashboardView from "../views/DashboardView.vue";
import BatchesView from "../views/BatchesView.vue";
import OrdersView from "../views/OrdersView.vue";
import DetailView from "../views/DetailView.vue";
import AuditView from "../views/AuditView.vue";
import PayoutsView from "../views/PayoutsView.vue";
import EventsView from "../views/EventsView.vue";

const routes = [
  {
    path: "/",
    component: MainLayout,
    children: [
      { path: "", redirect: "/dashboard" },
      { path: "dashboard", component: DashboardView },
      { path: "batches", component: BatchesView },
      { path: "orders", component: OrdersView },
      { path: "detail", component: DetailView },
      { path: "audit", component: AuditView },
      { path: "payouts", component: PayoutsView },
      { path: "events", component: EventsView }
    ]
  }
];

export default createRouter({
  history: createWebHistory(),
  routes
});
