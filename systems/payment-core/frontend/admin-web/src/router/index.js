import { createRouter, createWebHistory } from "vue-router";
import MainLayout from "../views/MainLayout.vue";

const DashboardView = () => import("../views/DashboardView.vue");
const OrdersView = () => import("../views/OrdersView.vue");
const OrderDetailView = () => import("../views/OrderDetailView.vue");
const BillsView = () => import("../views/BillsView.vue");
const BillDetailView = () => import("../views/BillDetailView.vue");
const PaymentFlowsView = () => import("../views/PaymentFlowsView.vue");
const PaymentFlowDetailView = () => import("../views/PaymentFlowDetailView.vue");
const PaymentRoutesView = () => import("../views/PaymentRoutesView.vue");
const PaymentRouteDetailView = () => import("../views/PaymentRouteDetailView.vue");
const PaymentEventsView = () => import("../views/PaymentEventsView.vue");
const PaymentEventDetailView = () => import("../views/PaymentEventDetailView.vue");
const PaymentIssuesView = () => import("../views/PaymentIssuesView.vue");
const PaymentIssueDetailView = () => import("../views/PaymentIssueDetailView.vue");
const PaymentIssueAlertsView = () => import("../views/PaymentIssueAlertsView.vue");
const PaymentIssueAlertDetailView = () => import("../views/PaymentIssueAlertDetailView.vue");
const CashierSessionsView = () => import("../views/CashierSessionsView.vue");
const CashierSessionDetailView = () => import("../views/CashierSessionDetailView.vue");
const PaymentRequestsView = () => import("../views/PaymentRequestsView.vue");
const PaymentRequestDetailView = () => import("../views/PaymentRequestDetailView.vue");
const PaymentLogsView = () => import("../views/PaymentLogsView.vue");
const PaymentLogDetailView = () => import("../views/PaymentLogDetailView.vue");
const PaymentRecordsView = () => import("../views/PaymentRecordsView.vue");
const PaymentRecordDetailView = () => import("../views/PaymentRecordDetailView.vue");
const PaymentsView = () => import("../views/PaymentsView.vue");
const PaymentDetailView = () => import("../views/PaymentDetailView.vue");
const RefundsView = () => import("../views/RefundsView.vue");
const RefundDetailView = () => import("../views/RefundDetailView.vue");
const WorkerSettlementsView = () => import("../views/WorkerSettlementsView.vue");
const PaymentConfigView = () => import("../views/PaymentConfigView.vue");
const PaymentMonitorView = () => import("../views/PaymentMonitorView.vue");
const PaymentDayEndView = () => import("../views/PaymentDayEndView.vue");
const PaymentDayEndDetailView = () => import("../views/PaymentDayEndDetailView.vue");
const PaymentTaskCenterView = () => import("../views/PaymentTaskCenterView.vue");
const PaymentTaskRunDetailView = () => import("../views/PaymentTaskRunDetailView.vue");
const PaymentDeadLetterTasksView = () => import("../views/PaymentDeadLetterTasksView.vue");
const PaymentDeadLetterTaskDetailView = () => import("../views/PaymentDeadLetterTaskDetailView.vue");

const routes = [
  {
    path: "/",
    component: MainLayout,
    children: [
      { path: "", redirect: "/dashboard" },
      { path: "dashboard", component: DashboardView },
      { path: "orders", name: "orders", component: OrdersView },
      { path: "orders/:orderNo", name: "order-detail", component: OrderDetailView },
      { path: "bills", name: "bills", component: BillsView },
      { path: "bills/:billNo", name: "bill-detail", component: BillDetailView },
      { path: "payment-flows", name: "payment-flows", component: PaymentFlowsView },
      {
        path: "payment-flows/:flowSource/:flowNo",
        name: "payment-flow-detail",
        component: PaymentFlowDetailView
      },
      { path: "payment-routes", name: "payment-routes", component: PaymentRoutesView },
      { path: "payment-routes/:routeNo", name: "payment-route-detail", component: PaymentRouteDetailView },
      { path: "payment-events", name: "payment-events", component: PaymentEventsView },
      { path: "payment-events/:eventNo", name: "payment-event-detail", component: PaymentEventDetailView },
      { path: "payment-issues", name: "payment-issues", component: PaymentIssuesView },
      { path: "payment-issues/:issueNo", name: "payment-issue-detail", component: PaymentIssueDetailView },
      { path: "payment-issue-alerts", name: "payment-issue-alerts", component: PaymentIssueAlertsView },
      {
        path: "payment-issue-alerts/:alertNo",
        name: "payment-issue-alert-detail",
        component: PaymentIssueAlertDetailView
      },
      { path: "cashier-sessions", name: "cashier-sessions", component: CashierSessionsView },
      {
        path: "cashier-sessions/:sessionNo",
        name: "cashier-session-detail",
        component: CashierSessionDetailView
      },
      { path: "payment-requests", name: "payment-requests", component: PaymentRequestsView },
      {
        path: "payment-requests/:requestNo",
        name: "payment-request-detail",
        component: PaymentRequestDetailView
      },
      { path: "payment-logs", name: "payment-logs", component: PaymentLogsView },
      { path: "payment-logs/:logNo", name: "payment-log-detail", component: PaymentLogDetailView },
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
      { path: "payment-records/:paymentOrderId", name: "payment-record-detail", component: PaymentRecordDetailView },
      { path: "payments", name: "payments", component: PaymentsView },
      { path: "payments/:paymentOrderId", name: "payment-detail", component: PaymentDetailView },
      { path: "refunds", component: RefundsView },
      { path: "refunds/:refundOrderId", name: "refund-detail", component: RefundDetailView },
      { path: "payment-config", component: PaymentConfigView },
      { path: "payment-monitor", component: PaymentMonitorView },
      { path: "payment-task-center", component: PaymentTaskCenterView },
      { path: "payment-task-center/task-runs/:taskLogNo", name: "payment-task-run-detail", component: PaymentTaskRunDetailView },
      { path: "payment-dead-letter-tasks", component: PaymentDeadLetterTasksView },
      {
        path: "payment-dead-letter-tasks/:taskNo",
        name: "payment-dead-letter-task-detail",
        component: PaymentDeadLetterTaskDetailView
      },
      { path: "payment-day-end", component: PaymentDayEndView },
      { path: "payment-day-end/batches/:batchNo", name: "payment-day-end-detail", component: PaymentDayEndDetailView },
      { path: "worker-settlements", component: WorkerSettlementsView }
    ]
  }
];

export default createRouter({
  history: createWebHistory(),
  routes
});
