import { createRouter, createWebHistory } from "vue-router";
import MainLayout from "../views/MainLayout.vue";
import DashboardView from "../views/DashboardView.vue";
import OrdersView from "../views/OrdersView.vue";
import BillsView from "../views/BillsView.vue";
import PaymentFlowsView from "../views/PaymentFlowsView.vue";
import PaymentRoutesView from "../views/PaymentRoutesView.vue";
import PaymentEventsView from "../views/PaymentEventsView.vue";
import PaymentIssuesView from "../views/PaymentIssuesView.vue";
import CashierSessionsView from "../views/CashierSessionsView.vue";
import PaymentRequestsView from "../views/PaymentRequestsView.vue";
import PaymentLogsView from "../views/PaymentLogsView.vue";
import PaymentRecordsView from "../views/PaymentRecordsView.vue";
import PaymentRecordDetailView from "../views/PaymentRecordDetailView.vue";
import PaymentsView from "../views/PaymentsView.vue";
import PaymentDetailView from "../views/PaymentDetailView.vue";
import RefundsView from "../views/RefundsView.vue";
import RefundDetailView from "../views/RefundDetailView.vue";
import WorkerSettlementsView from "../views/WorkerSettlementsView.vue";
import PaymentConfigView from "../views/PaymentConfigView.vue";
import PaymentMonitorView from "../views/PaymentMonitorView.vue";
import PaymentDayEndView from "../views/PaymentDayEndView.vue";
import PaymentTaskCenterView from "../views/PaymentTaskCenterView.vue";

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
      { path: "payment-routes", component: PaymentRoutesView },
      { path: "payment-events", component: PaymentEventsView },
      { path: "payment-issues", component: PaymentIssuesView },
      { path: "cashier-sessions", component: CashierSessionsView },
      { path: "payment-requests", component: PaymentRequestsView },
      { path: "payment-logs", component: PaymentLogsView },
      {
        path: "payment-records",
        component: PaymentRecordsView,
        meta: {
          pageTitle: "统一支付记录",
          recordType: "ALL"
        }
      },
      {
        path: "payment-records/wechat-alipay",
        component: PaymentRecordsView,
        meta: {
          pageTitle: "微信支付宝支付记录",
          recordType: "WECHAT"
        }
      },
      {
        path: "payment-records/bank-card",
        component: PaymentRecordsView,
        meta: {
          pageTitle: "银行卡支付记录",
          recordType: "BANK_CARD"
        }
      },
      { path: "payment-records/:paymentOrderId", component: PaymentRecordDetailView },
      { path: "payments", component: PaymentsView },
      { path: "payments/:paymentOrderId", component: PaymentDetailView },
      { path: "refunds", component: RefundsView },
      { path: "refunds/:refundOrderId", component: RefundDetailView },
      { path: "payment-config", component: PaymentConfigView },
      { path: "payment-monitor", component: PaymentMonitorView },
      { path: "payment-task-center", component: PaymentTaskCenterView },
      { path: "payment-day-end", component: PaymentDayEndView },
      { path: "worker-settlements", component: WorkerSettlementsView }
    ]
  }
];

export default createRouter({
  history: createWebHistory(),
  routes
});
