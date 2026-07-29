import { createRouter, createWebHistory } from "vue-router";
import MainLayout from "../views/MainLayout.vue";
import DashboardView from "../views/DashboardView.vue";
import BatchesView from "../views/BatchesView.vue";
import OrdersView from "../views/OrdersView.vue";
import RulesView from "../views/RulesView.vue";
import FeesView from "../views/FeesView.vue";
import SharesView from "../views/SharesView.vue";
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
      { path: "rules", component: RulesView },
      { path: "fees", component: FeesView },
      { path: "shares", component: SharesView },
      { path: "events", component: EventsView }
    ]
  }
];

export default createRouter({
  history: createWebHistory(),
  routes
});
