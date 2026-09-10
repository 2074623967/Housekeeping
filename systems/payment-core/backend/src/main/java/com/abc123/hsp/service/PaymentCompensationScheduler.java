package com.abc123.hsp.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 支付失败补偿调度器。
 *
 * <p>除超时关单外，失败事件重发和失败退款重试也需要由后台统一巡检，
 * 避免完全依赖人工点击造成跨系统状态长时间不一致。</p>
 */
@Component
public class PaymentCompensationScheduler {

    private final PaymentTaskCenterService paymentTaskCenterService;
    private final boolean pendingPaymentQueryEnabled;
    private final boolean pendingRefundQueryEnabled;
    private final boolean issueAlertAutoDispatchEnabled;
    private final boolean issueAlertReceiptReconcileEnabled;

    public PaymentCompensationScheduler(PaymentTaskCenterService paymentTaskCenterService) {
        this(paymentTaskCenterService, false, false, false, false, false, false);
    }

    PaymentCompensationScheduler(
            PaymentTaskCenterService paymentTaskCenterService,
            boolean pendingPaymentQueryEnabled,
            boolean gatewayQueryEnabled) {
        this(paymentTaskCenterService, pendingPaymentQueryEnabled, gatewayQueryEnabled, false);
    }

    public PaymentCompensationScheduler(
            PaymentTaskCenterService paymentTaskCenterService,
            @Value("${payment.channel-query.compensation.enabled:false}") boolean pendingPaymentQueryEnabled,
            @Value("${payment.channel-query.gateway-access.enabled:false}") boolean gatewayQueryEnabled,
            @Value("${payment.issue-alert.auto-dispatch.enabled:false}") boolean issueAlertAutoDispatchEnabled) {
        this(paymentTaskCenterService,
                pendingPaymentQueryEnabled,
                gatewayQueryEnabled,
                false,
                false,
                issueAlertAutoDispatchEnabled,
                false);
    }

    @Autowired
    public PaymentCompensationScheduler(
            PaymentTaskCenterService paymentTaskCenterService,
            @Value("${payment.channel-query.compensation.enabled:false}") boolean pendingPaymentQueryEnabled,
            @Value("${payment.channel-query.gateway-access.enabled:false}") boolean gatewayQueryEnabled,
            @Value("${payment.refund-query.compensation.enabled:false}") boolean pendingRefundQueryEnabled,
            @Value("${payment.refund-query.gateway-access.enabled:false}") boolean gatewayRefundQueryEnabled,
            @Value("${payment.issue-alert.auto-dispatch.enabled:false}") boolean issueAlertAutoDispatchEnabled,
            @Value("${payment.issue-alert.receipt-reconcile.enabled:false}") boolean issueAlertReceiptReconcileEnabled) {
        this.paymentTaskCenterService = paymentTaskCenterService;
        this.pendingPaymentQueryEnabled = pendingPaymentQueryEnabled && gatewayQueryEnabled;
        this.pendingRefundQueryEnabled = pendingRefundQueryEnabled && gatewayRefundQueryEnabled;
        this.issueAlertAutoDispatchEnabled = issueAlertAutoDispatchEnabled;
        this.issueAlertReceiptReconcileEnabled = issueAlertReceiptReconcileEnabled;
    }

    PaymentCompensationScheduler(
            PaymentTaskCenterService paymentTaskCenterService,
            boolean pendingPaymentQueryEnabled,
            boolean gatewayQueryEnabled,
            boolean pendingRefundQueryEnabled,
            boolean gatewayRefundQueryEnabled,
            boolean issueAlertAutoDispatchEnabled) {
        this(paymentTaskCenterService, pendingPaymentQueryEnabled, gatewayQueryEnabled,
                pendingRefundQueryEnabled, gatewayRefundQueryEnabled, issueAlertAutoDispatchEnabled, false);
    }

    PaymentCompensationScheduler(
            PaymentTaskCenterService paymentTaskCenterService,
            boolean pendingPaymentQueryEnabled) {
        this(paymentTaskCenterService, pendingPaymentQueryEnabled, true, false);
    }

    /**
     * 定期主动查询等待回调的支付单，补偿渠道回调丢失场景。
     */
    @Scheduled(fixedDelayString = "${payment.channel-query.compensation.fixed-delay-ms:60000}")
    public void queryPendingPayments() {
        if (pendingPaymentQueryEnabled) {
            paymentTaskCenterService.runAutoPendingPaymentQueries();
        }
    }

    /**
     * 定期主动查询处理中的退款，补偿渠道退款回调丢失场景。
     */
    @Scheduled(fixedDelayString = "${payment.refund-query.compensation.fixed-delay-ms:60000}")
    public void queryPendingRefunds() {
        if (pendingRefundQueryEnabled) {
            paymentTaskCenterService.runAutoPendingRefundQueries();
        }
    }

    /**
     * 定期评估主动查单失败与限流拒绝阈值，并生成可派发的 outbox 告警。
     */
    @Scheduled(fixedDelayString = "${payment.channel-query.monitor.alert-fixed-delay-ms:300000}")
    public void generateQueryMonitorAlerts() {
        paymentTaskCenterService.runAutoGenerateQueryMonitorAlerts();
    }

    /**
     * 定期派发待处理 outbox 告警；默认关闭，配置真实或联调通知通道后再显式启用。
     */
    @Scheduled(fixedDelayString = "${payment.issue-alert.auto-dispatch.fixed-delay-ms:60000}")
    public void dispatchIssueAlerts() {
        if (issueAlertAutoDispatchEnabled) {
            paymentTaskCenterService.runAutoDispatchIssueAlerts();
        }
    }

    /**
     * 定期重发失败事件，优先收敛支付成功后的跨系统状态。
     */
    @Scheduled(fixedDelayString = "${payment.event-retry.fixed-delay-ms:300000}")
    public void republishFailedEvents() {
        paymentTaskCenterService.runAutoRepublishFailedEvents();
    }

    /**
     * 定期重试失败退款，减少逆向资金状态长期悬挂。
     */
    @Scheduled(fixedDelayString = "${payment.refund-retry.fixed-delay-ms:300000}")
    public void retryFailedRefunds() {
        paymentTaskCenterService.runAutoRetryFailedRefunds();
    }

    /**
     * 定期巡检超过 SLA 的支付交易异常，推动值班升级和运营跟进。
     */
    @Scheduled(fixedDelayString = "${payment.issue-sla.fixed-delay-ms:300000}")
    public void escalateOverdueIssues() {
        paymentTaskCenterService.runAutoEscalateOverdueIssues();
    }

    /**
     * 定期回查供应商已受理但尚未确认送达的异常告警，减少人工逐条跟进。
     */
    @Scheduled(fixedDelayString = "${payment.issue-alert-receipt-reconcile.fixed-delay-ms:600000}")
    public void reconcileIssueAlertReceipts() {
        if (issueAlertReceiptReconcileEnabled) {
            paymentTaskCenterService.runAutoReconcileIssueAlertReceipts();
        }
    }

    /**
     * 定期巡检启用中的支付控制策略，提前发现渠道、商户号、网关和令牌配置漂移。
     */
    @Scheduled(fixedDelayString = "${payment.control-self-check.fixed-delay-ms:600000}")
    public void runControlPolicySelfChecks() {
        paymentTaskCenterService.runAutoControlPolicySelfChecks();
    }
}
