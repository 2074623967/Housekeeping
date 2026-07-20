import { createRouter, createWebHistory } from "vue-router";
import MainLayout from "../views/MainLayout.vue";
import DashboardView from "../views/DashboardView.vue";
import SubjectsView from "../views/SubjectsView.vue";
import AccountsView from "../views/AccountsView.vue";
import AccountDetailView from "../views/AccountDetailView.vue";
import LedgersView from "../views/LedgersView.vue";
import FreezesView from "../views/FreezesView.vue";
import AdjustmentsView from "../views/AdjustmentsView.vue";
import EventsView from "../views/EventsView.vue";

const routes = [
  {
    path: "/",
    component: MainLayout,
    children: [
      { path: "", redirect: "/dashboard" },
      { path: "dashboard", component: DashboardView },
      { path: "subjects", component: SubjectsView },
      { path: "accounts", component: AccountsView },
      { path: "accounts/:accountNo", component: AccountDetailView },
      { path: "ledgers", component: LedgersView },
      { path: "freezes", component: FreezesView },
      { path: "adjustments", component: AdjustmentsView },
      { path: "events", component: EventsView }
    ]
  }
];

export default createRouter({
  history: createWebHistory(),
  routes
});
