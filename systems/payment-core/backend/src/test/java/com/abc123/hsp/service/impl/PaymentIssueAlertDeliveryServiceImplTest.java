package com.abc123.hsp.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentAlertProviderConfigDTO;
import com.abc123.hsp.dto.PaymentIssueAlertDeliveryResultDTO;
import com.abc123.hsp.dto.PaymentIssueAlertDispatchItemDTO;
import com.abc123.hsp.dto.PaymentTaskActionResultDTO;
import com.abc123.hsp.entity.PaymentIssueAlertLogEntity;
import com.abc123.hsp.entity.PaymentTaskRunLogEntity;
import com.abc123.hsp.mapper.PaymentTaskCenterMapper;
import com.abc123.hsp.service.PaymentIssueAlertNotifier;
import com.abc123.hsp.service.PaymentIssueAlertDeliveryRateLimitService;
import com.abc123.hsp.service.PaymentIssueAlertReceiptQueryAdapter;
import com.abc123.hsp.service.PaymentIssueAlertReceiptQueryRateLimitService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 支付交易异常告警派发服务测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentIssueAlertDeliveryServiceImplTest {

    @Mock
    private PaymentTaskCenterMapper paymentTaskCenterMapper;
    @Mock
    private PaymentIssueAlertNotifier imNotifier;
    @Mock
    private PaymentIssueAlertNotifier smsNotifier;
    @Mock
    private PaymentIssueAlertNotifier emailNotifier;
    @Mock
    private PaymentIssueAlertReceiptQueryAdapter receiptQueryAdapter;
    @Mock
    private PaymentIssueAlertReceiptQueryRateLimitService receiptQueryRateLimitService;
    @Mock
    private PaymentIssueAlertDeliveryRateLimitService deliveryRateLimitService;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;

    @BeforeEach
    void allowMatchingPendingSourceAlertByDefault() {
        org.mockito.Mockito.lenient().when(paymentTaskCenterMapper.isMatchingPendingSourceIssueAlert(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(true);
        org.mockito.Mockito.lenient().when(paymentTaskCenterMapper.lockPendingOutboxAlert(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(true);
        org.mockito.Mockito.lenient().when(paymentTaskCenterMapper.updateIssueAlertDeliveryStatus(
                any(PaymentIssueAlertLogEntity.class))).thenReturn(1);
    }

    @Test
    void shouldSkipStaleDispatchCandidateBeforeCallingProvider() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IM");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100))
                .thenReturn(Collections.singletonList(item));
        when(paymentTaskCenterMapper.lockPendingOutboxAlert("PIA-OUTBOX-001", "ISSUE-001"))
                .thenReturn(false);
        when(imNotifier.channelCode()).thenReturn("IM");

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts("支付运营");

        verify(imNotifier, org.mockito.Mockito.never()).send(any(PaymentIssueAlertDispatchItemDTO.class));
        verify(paymentTaskCenterMapper, org.mockito.Mockito.never())
                .insertIssueAlertLog(any(PaymentIssueAlertLogEntity.class));
        verify(paymentTaskCenterMapper, org.mockito.Mockito.never())
                .updateIssueAlertDeliveryStatus(any(PaymentIssueAlertLogEntity.class));
        Assertions.assertEquals(0, result.getProcessedCount());
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(0, result.getFailCount());
    }

    @Test
    void shouldFailClosedWhenSourceDispatchStatusUpdateLosesEligibility() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IM");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100))
                .thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "IM"))
                .thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM"))
                .thenReturn(Collections.singletonList(buildProvider(
                        "ALERT_IM_WECOM", "企业微信告警机器人", "wecom-bot-alerts",
                        "TPL_PAYMENT_ISSUE_IM_V1", "DEFAULT", 100)));
        when(imNotifier.send(any(PaymentIssueAlertDispatchItemDTO.class))).thenReturn(
                buildDeliveryResult("IM-RECEIPT", "DELIVERED", "企业微信已送达", "[IM] ISSUE-001"));
        when(paymentTaskCenterMapper.updateIssueAlertDeliveryStatus(any(PaymentIssueAlertLogEntity.class)))
                .thenReturn(0);

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts("支付运营");

        verify(paymentTaskCenterMapper, org.mockito.Mockito.never())
                .updateSourceIssueAlertAcknowledgement(any(PaymentIssueAlertLogEntity.class));
        Assertions.assertEquals(1, result.getProcessedCount());
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(1, result.getFailCount());
    }

    @Test
    void shouldDispatchAllChannelsSuccessfully() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM,SMS,EMAIL");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100)).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "IM")).thenReturn(false);
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "SMS")).thenReturn(false);
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "EMAIL")).thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM")).thenReturn(Arrays.asList(
                buildProvider("ALERT_IM_WECOM_P1", "企业微信告警机器人-P1", "wecom-bot-alerts", "TPL_PAYMENT_ISSUE_IM_P1_V1", "severity=P1", 10),
                buildProvider("ALERT_IM_WECOM_DEFAULT", "企业微信告警机器人-默认", "wecom-bot-alerts-default", "TPL_PAYMENT_ISSUE_IM_V1", "DEFAULT", 100)
        ));
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("SMS")).thenReturn(Collections.singletonList(buildProvider("ALERT_SMS_TENCENT", "腾讯云短信告警", "tencent-sms-alerts", "TPL_PAYMENT_ISSUE_SMS_V1", "DEFAULT", 100)));
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("EMAIL")).thenReturn(Collections.singletonList(buildProvider("ALERT_EMAIL_SENDCLOUD", "SendCloud 邮件告警", "sendcloud-payment-alerts", "TPL_PAYMENT_ISSUE_EMAIL_V1", "DEFAULT", 100)));
        when(imNotifier.send(any(PaymentIssueAlertDispatchItemDTO.class))).thenReturn(buildDeliveryResult("IM-RECEIPT-001", "ACCEPTED", "企业微信已接单", "[IM] ISSUE-001"));
        when(smsNotifier.send(any(PaymentIssueAlertDispatchItemDTO.class))).thenReturn(buildDeliveryResult("SMS-RECEIPT-001", "ACCEPTED", "短信供应商已接单", "[SMS] ISSUE-001"));
        when(emailNotifier.send(any(PaymentIssueAlertDispatchItemDTO.class))).thenReturn(buildDeliveryResult("EMAIL-RECEIPT-001", "ACCEPTED", "邮件供应商已接单", "[EMAIL] ISSUE-001"));

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts(" 支付运营 ");

        ArgumentCaptor<PaymentIssueAlertLogEntity> deliveryLogCaptor = ArgumentCaptor.forClass(PaymentIssueAlertLogEntity.class);
        verify(paymentTaskCenterMapper, org.mockito.Mockito.times(3)).insertIssueAlertLog(deliveryLogCaptor.capture());
        verify(paymentTaskCenterMapper).updateIssueAlertDeliveryStatus(any(PaymentIssueAlertLogEntity.class));
        verify(paymentTaskCenterMapper, org.mockito.Mockito.never())
                .updateSourceIssueAlertAcknowledgement(any(PaymentIssueAlertLogEntity.class));
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                log -> "支付运营".equals(log.getTriggeredBy())));
        Assertions.assertEquals(1, result.getSuccessCount());
        Assertions.assertEquals(0, result.getWarningCount());
        Assertions.assertEquals(0, result.getFailCount());
        Assertions.assertTrue(deliveryLogCaptor.getAllValues().stream().allMatch(log -> "已派发".equals(log.getAlertStatus())));
        Assertions.assertTrue(deliveryLogCaptor.getAllValues().stream().anyMatch(log -> log.getAlertContent().contains("支付异常 ISSUE-001")));
        Assertions.assertTrue(deliveryLogCaptor.getAllValues().stream().anyMatch(log -> "企业微信告警机器人-P1".equals(log.getProviderName())));
        Assertions.assertTrue(deliveryLogCaptor.getAllValues().stream().anyMatch(log -> "TPL_PAYMENT_ISSUE_IM_P1_V1".equals(log.getTemplateCode())));
        Assertions.assertTrue(deliveryLogCaptor.getAllValues().stream().anyMatch(log -> "ACCEPTED".equals(log.getProviderDeliveryStatus())));
        Assertions.assertTrue(deliveryLogCaptor.getAllValues().stream().allMatch(log ->
                "待确认".equals(log.getAckStatus()) && "warn".equals(log.getAckStatusType())));
        Assertions.assertTrue(deliveryLogCaptor.getAllValues().stream().anyMatch(log -> "IM-RECEIPT-001".equals(log.getProviderReceiptNo())));
        Assertions.assertTrue(deliveryLogCaptor.getAllValues().stream().allMatch(log -> org.springframework.util.StringUtils.hasText(log.getProviderReceiptSnapshot())));
        Assertions.assertTrue(deliveryLogCaptor.getAllValues().stream().anyMatch(log -> log.getProviderReceiptSnapshot().contains("ACCEPTED")));
        Assertions.assertTrue(deliveryLogCaptor.getAllValues().stream().allMatch(
                log -> "支付运营".equals(log.getTriggeredBy())));
    }

    @Test
    void shouldConfirmMatchingSourceWhenProviderImmediatelyReportsDelivered() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100))
                .thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "IM"))
                .thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM"))
                .thenReturn(Collections.singletonList(buildProvider(
                        "ALERT_IM_WECOM", "企业微信告警机器人", "wecom-bot-alerts",
                        "TPL_PAYMENT_ISSUE_IM_V1", "DEFAULT", 100)));
        when(imNotifier.send(any(PaymentIssueAlertDispatchItemDTO.class))).thenReturn(
                buildDeliveryResult(
                        "IM-RECEIPT-DELIVERED", "DELIVERED", "企业微信已送达", "[IM] ISSUE-001"));
        when(paymentTaskCenterMapper.updateSourceIssueAlertAcknowledgement(
                any(PaymentIssueAlertLogEntity.class))).thenReturn(1);

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts(" 支付运营 ");

        ArgumentCaptor<PaymentIssueAlertLogEntity> deliveryLogCaptor =
                ArgumentCaptor.forClass(PaymentIssueAlertLogEntity.class);
        verify(paymentTaskCenterMapper).insertIssueAlertLog(deliveryLogCaptor.capture());
        Assertions.assertEquals("DELIVERED", deliveryLogCaptor.getValue().getProviderDeliveryStatus());
        Assertions.assertEquals("无需回执", deliveryLogCaptor.getValue().getAckStatus());
        org.mockito.InOrder sourceUpdates = org.mockito.Mockito.inOrder(paymentTaskCenterMapper);
        sourceUpdates.verify(paymentTaskCenterMapper).updateIssueAlertDeliveryStatus(any(PaymentIssueAlertLogEntity.class));
        sourceUpdates.verify(paymentTaskCenterMapper).updateSourceIssueAlertAcknowledgement(
                org.mockito.ArgumentMatchers.argThat(source ->
                        "PIA-OUTBOX-001".equals(source.getAlertNo())
                                && "ISSUE-001".equals(source.getIssueNo())
                                && "已确认".equals(source.getAckStatus())
                                && "success".equals(source.getAckStatusType())
                                && "支付运营".equals(source.getAckOperator())
                                && "支付运营".equals(source.getTriggeredBy())));
        Assertions.assertEquals(1, result.getSuccessCount());
        Assertions.assertEquals(0, result.getFailCount());
    }

    @Test
    void shouldFailDirectDeliveryWhenMatchingSourceCannotBeConfirmed() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100))
                .thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "IM"))
                .thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM"))
                .thenReturn(Collections.singletonList(buildProvider(
                        "ALERT_IM_WECOM", "企业微信告警机器人", "wecom-bot-alerts",
                        "TPL_PAYMENT_ISSUE_IM_V1", "DEFAULT", 100)));
        when(imNotifier.send(any(PaymentIssueAlertDispatchItemDTO.class))).thenReturn(
                buildDeliveryResult(
                        "IM-RECEIPT-DELIVERED", "DELIVERED", "企业微信已送达", "[IM] ISSUE-001"));
        when(paymentTaskCenterMapper.updateSourceIssueAlertAcknowledgement(
                any(PaymentIssueAlertLogEntity.class))).thenReturn(0);

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts("支付运营");

        verify(paymentTaskCenterMapper).updateIssueAlertDeliveryStatus(any(PaymentIssueAlertLogEntity.class));
        verify(paymentTaskCenterMapper).updateSourceIssueAlertAcknowledgement(any(PaymentIssueAlertLogEntity.class));
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(1, result.getFailCount());
        Assertions.assertTrue(result.getSummaryComment().contains("PIA-OUTBOX-001"));
    }

    @Test
    void shouldAcceptDirectDeliveryWhenMatchingSourceWasAlreadyConfirmed() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100))
                .thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "IM"))
                .thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM"))
                .thenReturn(Collections.singletonList(buildProvider(
                        "ALERT_IM_WECOM", "企业微信告警机器人", "wecom-bot-alerts",
                        "TPL_PAYMENT_ISSUE_IM_V1", "DEFAULT", 100)));
        when(imNotifier.send(any(PaymentIssueAlertDispatchItemDTO.class))).thenReturn(
                buildDeliveryResult(
                        "IM-RECEIPT-DELIVERED", "DELIVERED", "企业微信已送达", "[IM] ISSUE-001"));
        when(paymentTaskCenterMapper.updateSourceIssueAlertAcknowledgement(
                any(PaymentIssueAlertLogEntity.class))).thenReturn(0);
        when(paymentTaskCenterMapper.isMatchingSourceIssueAlertAcknowledged(
                "PIA-OUTBOX-001", "ISSUE-001")).thenReturn(true);

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts("支付运营");

        verify(paymentTaskCenterMapper).isMatchingSourceIssueAlertAcknowledged(
                "PIA-OUTBOX-001", "ISSUE-001");
        Assertions.assertEquals(1, result.getSuccessCount());
        Assertions.assertEquals(0, result.getFailCount());
    }

    @Test
    void shouldRecognizeInAppChannelIndependentOfDefaultLocale() {
        Locale previousLocale = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
            item.setNotifyChannels("in_app");
            when(paymentTaskCenterMapper.findPendingOutboxAlerts(100))
                    .thenReturn(Collections.singletonList(item));

            PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                    paymentTaskCenterMapper,
                    Arrays.asList(imNotifier, smsNotifier, emailNotifier)
            ).dispatchPendingAlerts("支付运营");

            Assertions.assertEquals(1, result.getSuccessCount());
            verify(paymentTaskCenterMapper, org.mockito.Mockito.never())
                    .findEnabledAlertProvidersByChannel(any());
        } finally {
            Locale.setDefault(previousLocale);
        }
    }

    @Test
    void shouldSkipAutoDispatchWhenLeaseHeldByAnotherInstance() {
        when(paymentTaskCenterMapper.acquireTaskLease(
                eq("PAYMENT_ISSUE_ALERT_DISPATCH"),
                leaseOwner("payment-issue-alert-scheduler"),
                eq(120)))
                .thenReturn(0);

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).autoDispatchPendingAlerts();

        verify(paymentTaskCenterMapper).initTaskLease("PAYMENT_ISSUE_ALERT_DISPATCH");
        verify(paymentTaskCenterMapper).insertTaskRunLog(any(PaymentTaskRunLogEntity.class));
        verify(paymentTaskCenterMapper, org.mockito.Mockito.never()).findPendingOutboxAlerts(100);
        Assertions.assertEquals("PAYMENT_ISSUE_ALERT_DISPATCH", result.getTaskCode());
        Assertions.assertTrue(result.getSummaryComment().contains("任务租约"));
    }

    @Test
    void shouldUseDefaultAndClampDispatchBatchSize() {
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100)).thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(1)).thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(1000)).thenReturn(Collections.emptyList());
        PaymentIssueAlertDeliveryServiceImpl service = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        );

        service.dispatchPendingAlerts("payment-core-admin");
        service.configureDispatchBatchSize(0);
        service.dispatchPendingAlerts("payment-core-admin");
        service.configureDispatchBatchSize(1001);
        service.dispatchPendingAlerts("payment-core-admin");

        verify(paymentTaskCenterMapper).findPendingOutboxAlerts(100);
        verify(paymentTaskCenterMapper).findPendingOutboxAlerts(1);
        verify(paymentTaskCenterMapper).findPendingOutboxAlerts(1000);
    }

    @Test
    void shouldUseDefaultAndClampReceiptQueryBatchSizeForManualAndAutoRuns() {
        when(paymentTaskCenterMapper.findAcceptedIssueAlertDeliveryLogs(100))
                .thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.findAcceptedIssueAlertDeliveryLogs(1))
                .thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.findAcceptedIssueAlertDeliveryLogs(1000))
                .thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.acquireTaskLease(
                eq("PAYMENT_ISSUE_ALERT_RECEIPT_RECONCILE"),
                leaseOwner("payment-issue-alert-receipt-scheduler"),
                eq(120)))
                .thenReturn(1);
        when(paymentTaskCenterMapper.findDueAcceptedIssueAlertDeliveryLogs(60, 10, 30, 1000))
                .thenReturn(Collections.emptyList());
        PaymentIssueAlertDeliveryServiceImpl service = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        );

        service.reconcileDeliveryReceipts("payment-core-admin");
        service.configureReceiptQueryCompensation(60, 60, 2, 900, 10, 0, 30);
        service.reconcileDeliveryReceipts("payment-core-admin");
        service.configureReceiptQueryCompensation(60, 60, 2, 900, 10, 1001, 30);
        service.reconcileDeliveryReceipts("payment-core-admin");
        service.autoReconcileDeliveryReceipts();

        verify(paymentTaskCenterMapper).findAcceptedIssueAlertDeliveryLogs(100);
        verify(paymentTaskCenterMapper).findAcceptedIssueAlertDeliveryLogs(1);
        verify(paymentTaskCenterMapper).findAcceptedIssueAlertDeliveryLogs(1000);
        verify(paymentTaskCenterMapper).findDueAcceptedIssueAlertDeliveryLogs(60, 10, 30, 1000);
        verify(paymentTaskCenterMapper).releaseTaskLease(
                eq("PAYMENT_ISSUE_ALERT_RECEIPT_RECONCILE"),
                leaseOwner("payment-issue-alert-receipt-scheduler"));
    }

    @Test
    void shouldReleaseLeaseAfterAutoReceiptReconcile() {
        PaymentIssueAlertLogEntity acceptedLog = new PaymentIssueAlertLogEntity();
        acceptedLog.setAlertNo("PIA-IM-001");
        acceptedLog.setSourceAlertNo("PIA-OUTBOX-001");
        acceptedLog.setIssueNo("ISSUE-001");
        acceptedLog.setAlertChannel("IM");
        acceptedLog.setProviderReceiptNo("IM-RECEIPT-001");
        when(paymentTaskCenterMapper.acquireTaskLease(
                eq("PAYMENT_ISSUE_ALERT_RECEIPT_RECONCILE"),
                leaseOwner("payment-issue-alert-receipt-scheduler"),
                eq(120)))
                .thenReturn(1);
        when(paymentTaskCenterMapper.findDueAcceptedIssueAlertDeliveryLogs(60, 10, 30, 100))
                .thenReturn(Collections.singletonList(acceptedLog));
        when(paymentTaskCenterMapper.claimIssueAlertReceiptQuery(
                "PIA-IM-001", "PIA-OUTBOX-001", "ISSUE-001", 0, false, 10, 30)).thenReturn(1);
        when(paymentTaskCenterMapper.updateIssueAlertProviderReceipt(any(PaymentIssueAlertLogEntity.class))).thenReturn(1);
        when(receiptQueryAdapter.supports("IM")).thenReturn(true);
        when(receiptQueryAdapter.query(acceptedLog)).thenReturn(
                buildDeliveryResult("IM-RECEIPT-001", "DELIVERED", "供应商查询确认送达", ""));

        new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier),
                Collections.singletonList(receiptQueryAdapter)
        ).autoReconcileDeliveryReceipts();

        verify(paymentTaskCenterMapper).releaseTaskLease(
                eq("PAYMENT_ISSUE_ALERT_RECEIPT_RECONCILE"),
                leaseOwner("payment-issue-alert-receipt-scheduler")
        );
        verify(paymentTaskCenterMapper).updateSourceIssueAlertAcknowledgement(any(PaymentIssueAlertLogEntity.class));
    }

    @Test
    void shouldMatchCompositeProviderRouteRuleByScheduleAndEscalationLevel() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM");
        item.setEscalationLevel("L2");
        item.setScheduleTag("交易链路白班");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100)).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "IM")).thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM")).thenReturn(Arrays.asList(
                buildProvider("ALERT_IM_NIGHT", "企业微信夜班机器人", "wecom-bot-night", "TPL_PAYMENT_ISSUE_IM_NIGHT", "scheduleTag=交易链路夜班&escalationLevel=L2", 10),
                buildProvider("ALERT_IM_DAY", "企业微信白班机器人", "wecom-bot-day", "TPL_PAYMENT_ISSUE_IM_DAY", "scheduleTag=交易链路白班&escalationLevel=L2", 20),
                buildProvider("ALERT_IM_DEFAULT", "企业微信默认机器人", "wecom-bot-default", "TPL_PAYMENT_ISSUE_IM_DEFAULT", "DEFAULT", 100)
        ));
        when(imNotifier.send(any(PaymentIssueAlertDispatchItemDTO.class))).thenReturn(buildDeliveryResult("IM-RECEIPT-DAY", "ACCEPTED", "白班机器人已接单", "[IM-DAY] ISSUE-001"));

        new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts("payment-core-admin");

        ArgumentCaptor<PaymentIssueAlertDispatchItemDTO> dispatchCaptor = ArgumentCaptor.forClass(PaymentIssueAlertDispatchItemDTO.class);
        verify(imNotifier).send(dispatchCaptor.capture());
        Assertions.assertEquals("ALERT_IM_DAY", dispatchCaptor.getValue().getProviderCode());
        Assertions.assertEquals("企业微信白班机器人", dispatchCaptor.getValue().getProviderName());
        Assertions.assertEquals("TPL_PAYMENT_ISSUE_IM_DAY", dispatchCaptor.getValue().getTemplateCode());
    }

    @Test
    void shouldMarkPartialFailureWhenOneChannelFails() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM,SMS,EMAIL");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100)).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "IM")).thenReturn(false);
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "SMS")).thenReturn(false);
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "EMAIL")).thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM")).thenReturn(Collections.singletonList(buildProvider("ALERT_IM_WECOM", "企业微信告警机器人", "wecom-bot-alerts", "TPL_PAYMENT_ISSUE_IM_V1", "DEFAULT", 100)));
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("SMS")).thenReturn(Collections.singletonList(buildProvider("ALERT_SMS_TENCENT", "腾讯云短信告警", "tencent-sms-alerts", "TPL_PAYMENT_ISSUE_SMS_V1", "DEFAULT", 100)));
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("EMAIL")).thenReturn(Collections.singletonList(buildProvider("ALERT_EMAIL_SENDCLOUD", "SendCloud 邮件告警", "sendcloud-payment-alerts", "TPL_PAYMENT_ISSUE_EMAIL_V1", "DEFAULT", 100)));
        when(imNotifier.send(any(PaymentIssueAlertDispatchItemDTO.class))).thenReturn(buildDeliveryResult("IM-RECEIPT-001", "ACCEPTED", "企业微信已接单", "[IM] ISSUE-001"));
        when(emailNotifier.send(any(PaymentIssueAlertDispatchItemDTO.class))).thenReturn(buildDeliveryResult("EMAIL-RECEIPT-001", "ACCEPTED", "邮件供应商已接单", "[EMAIL] ISSUE-001"));
        doThrow(new RuntimeException("sms down")).when(smsNotifier).send(any(PaymentIssueAlertDispatchItemDTO.class));

        PaymentIssueAlertDeliveryServiceImpl service = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        );
        PaymentTaskActionResultDTO result = service.dispatchPendingAlerts("payment-core-admin");

        ArgumentCaptor<PaymentIssueAlertLogEntity> sourceCaptor = ArgumentCaptor.forClass(PaymentIssueAlertLogEntity.class);
        verify(paymentTaskCenterMapper).updateIssueAlertDeliveryStatus(sourceCaptor.capture());
        Assertions.assertEquals("部分失败", sourceCaptor.getValue().getAlertStatus());
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(1, result.getWarningCount());
        Assertions.assertEquals(0, result.getFailCount());
    }

    @Test
    void shouldDispatchOnlyConfiguredChannels() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100)).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "IM")).thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM")).thenReturn(Collections.singletonList(buildProvider("ALERT_IM_WECOM", "企业微信告警机器人", "wecom-bot-alerts", "TPL_PAYMENT_ISSUE_IM_V1", "DEFAULT", 100)));
        when(imNotifier.send(any(PaymentIssueAlertDispatchItemDTO.class))).thenReturn(buildDeliveryResult("IM-RECEIPT-001", "ACCEPTED", "企业微信已接单", "[IM] ISSUE-001"));

        new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts("payment-core-admin");

        verify(imNotifier).send(any(PaymentIssueAlertDispatchItemDTO.class));
        verify(smsNotifier, org.mockito.Mockito.never()).send(any(PaymentIssueAlertDispatchItemDTO.class));
        verify(emailNotifier, org.mockito.Mockito.never()).send(any(PaymentIssueAlertDispatchItemDTO.class));
    }

    @Test
    void shouldRenderExtendedTemplateVariablesAndFallbackUnknownPlaceholder() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM,SMS");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100)).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "IM")).thenReturn(false);
        PaymentAlertProviderConfigDTO provider = buildProvider(
                "ALERT_IM_WECOM",
                "企业微信告警机器人",
                "wecom-bot-alerts",
                "TPL_PAYMENT_ISSUE_IM_V1",
                "DEFAULT",
                100
        );
        provider.setTemplateBody("{{providerName}}|{{endpointAlias}}|{{notifyChannels}}|{{escalationLevel}}|{{unknownField}}");
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM")).thenReturn(Collections.singletonList(provider));
        when(imNotifier.send(any(PaymentIssueAlertDispatchItemDTO.class))).thenReturn(buildDeliveryResult("IM-RECEIPT-001", "ACCEPTED", "企业微信已接单", "[IM] ISSUE-001"));

        new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts("payment-core-admin");

        ArgumentCaptor<PaymentIssueAlertDispatchItemDTO> dispatchCaptor = ArgumentCaptor.forClass(PaymentIssueAlertDispatchItemDTO.class);
        verify(imNotifier).send(dispatchCaptor.capture());
        Assertions.assertEquals(
                "企业微信告警机器人|wecom-bot-alerts|IN_APP,IM,SMS|L2|-",
                dispatchCaptor.getValue().getRenderedAlertContent()
        );
    }

    @Test
    void shouldMarkUnsupportedChannelAsFailure() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM,VOICE");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100)).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "IM")).thenReturn(false);
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "VOICE")).thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM")).thenReturn(Collections.singletonList(buildProvider("ALERT_IM_WECOM", "企业微信告警机器人", "wecom-bot-alerts", "TPL_PAYMENT_ISSUE_IM_V1", "DEFAULT", 100)));
        when(imNotifier.send(any(PaymentIssueAlertDispatchItemDTO.class))).thenReturn(buildDeliveryResult("IM-RECEIPT-001", "ACCEPTED", "企业微信已接单", "[IM] ISSUE-001"));
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("VOICE")).thenReturn(Collections.emptyList());

        PaymentIssueAlertDeliveryServiceImpl service = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        );
        PaymentTaskActionResultDTO result = service.dispatchPendingAlerts("payment-core-admin");

        ArgumentCaptor<PaymentIssueAlertLogEntity> sourceCaptor = ArgumentCaptor.forClass(PaymentIssueAlertLogEntity.class);
        verify(paymentTaskCenterMapper).updateIssueAlertDeliveryStatus(sourceCaptor.capture());
        Assertions.assertEquals("部分失败", sourceCaptor.getValue().getAlertStatus());
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(1, result.getWarningCount());
        Assertions.assertEquals(0, result.getFailCount());
    }

    @Test
    void shouldSkipChannelAlreadyDispatchedSuccessfullyWhenRetrying() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM,SMS");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100)).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "IM")).thenReturn(true);
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "SMS")).thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("SMS")).thenReturn(Collections.singletonList(buildProvider("ALERT_SMS_TENCENT", "腾讯云短信告警", "tencent-sms-alerts", "TPL_PAYMENT_ISSUE_SMS_V1", "DEFAULT", 100)));
        when(smsNotifier.send(any(PaymentIssueAlertDispatchItemDTO.class))).thenReturn(buildDeliveryResult("SMS-RECEIPT-001", "ACCEPTED", "短信供应商已接单", "[SMS] ISSUE-001"));

        new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts("payment-core-admin");

        verify(imNotifier, org.mockito.Mockito.never()).send(any(PaymentIssueAlertDispatchItemDTO.class));
        verify(smsNotifier).send(any(PaymentIssueAlertDispatchItemDTO.class));
    }

    @Test
    void shouldBlockReplayWhenRecentSuccessfulDeliveryStillInProtectionWindow() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100)).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "IM")).thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM")).thenReturn(Collections.singletonList(
                buildProvider("ALERT_IM_WECOM", "企业微信告警机器人", "wecom-bot-alerts", "TPL_PAYMENT_ISSUE_IM_V1", "DEFAULT", 100,
                        "防重放窗口10分钟/时间窗5分钟")
        ));
        PaymentIssueAlertLogEntity latestLog = new PaymentIssueAlertLogEntity();
        latestLog.setAlertStatus("已派发");
        latestLog.setProviderDeliveryStatus("ACCEPTED");
        latestLog.setCreatedAt("2099-12-31 23:59:59");
        when(paymentTaskCenterMapper.findLatestIssueAlertChannelDeliveryLog("PIA-OUTBOX-001", "IM")).thenReturn(latestLog);

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts("payment-core-admin");

        ArgumentCaptor<PaymentIssueAlertLogEntity> logCaptor = ArgumentCaptor.forClass(PaymentIssueAlertLogEntity.class);
        verify(paymentTaskCenterMapper).insertIssueAlertLog(logCaptor.capture());
        verify(imNotifier, org.mockito.Mockito.never()).send(any(PaymentIssueAlertDispatchItemDTO.class));
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(0, result.getWarningCount());
        Assertions.assertEquals(1, result.getFailCount());
        Assertions.assertEquals("REPLAY_WINDOW_ACTIVE", logCaptor.getValue().getProviderDeliveryStatus());
        Assertions.assertTrue(logCaptor.getValue().getProviderDeliveryMessage().contains("10 分钟"));
    }

    @Test
    void shouldRetryWhenRecentDispatchedLogHasNoSuccessfulProviderEvidence() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100)).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "IM")).thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM")).thenReturn(Collections.singletonList(
                buildProvider("ALERT_IM_WECOM", "企业微信告警机器人", "wecom-bot-alerts", "TPL_PAYMENT_ISSUE_IM_V1", "DEFAULT", 100,
                        "防重放窗口10分钟/时间窗5分钟")
        ));
        PaymentIssueAlertLogEntity latestLog = new PaymentIssueAlertLogEntity();
        latestLog.setAlertStatus("已派发");
        latestLog.setProviderDeliveryStatus("FAILED");
        latestLog.setCreatedAt("2099-12-31 23:59:59");
        when(paymentTaskCenterMapper.findLatestIssueAlertChannelDeliveryLog("PIA-OUTBOX-001", "IM")).thenReturn(latestLog);
        when(imNotifier.send(any(PaymentIssueAlertDispatchItemDTO.class)))
                .thenReturn(buildDeliveryResult("IM-RETRY-001", "ACCEPTED", "供应商已接单", "[IM] ISSUE-001"));

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts("payment-core-admin");

        verify(imNotifier).send(any(PaymentIssueAlertDispatchItemDTO.class));
        ArgumentCaptor<PaymentIssueAlertLogEntity> logCaptor = ArgumentCaptor.forClass(PaymentIssueAlertLogEntity.class);
        verify(paymentTaskCenterMapper).insertIssueAlertLog(logCaptor.capture());
        Assertions.assertEquals(1, result.getSuccessCount());
        Assertions.assertEquals(0, result.getFailCount());
        Assertions.assertEquals("已派发", logCaptor.getValue().getAlertStatus());
        Assertions.assertEquals("ACCEPTED", logCaptor.getValue().getProviderDeliveryStatus());
    }

    @Test
    void shouldBlockDispatchWhenOutboxExpiredByFreshnessWindow() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM");
        item.setCreatedAt("2026-07-29 09:00:00");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100)).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "IM")).thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM")).thenReturn(Collections.singletonList(
                buildProvider("ALERT_IM_WECOM", "企业微信告警机器人", "wecom-bot-alerts", "TPL_PAYMENT_ISSUE_IM_V1", "DEFAULT", 100,
                        "失败重试3次/间隔5分钟/时间窗10分钟")
        ));

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts("payment-core-admin");

        ArgumentCaptor<PaymentIssueAlertLogEntity> logCaptor = ArgumentCaptor.forClass(PaymentIssueAlertLogEntity.class);
        verify(paymentTaskCenterMapper).insertIssueAlertLog(logCaptor.capture());
        verify(imNotifier, org.mockito.Mockito.never()).send(any(PaymentIssueAlertDispatchItemDTO.class));
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(0, result.getWarningCount());
        Assertions.assertEquals(1, result.getFailCount());
        Assertions.assertEquals("FRESHNESS_WINDOW_EXPIRED", logCaptor.getValue().getProviderDeliveryStatus());
        Assertions.assertTrue(logCaptor.getValue().getProviderDeliveryMessage().contains("10 分钟"));
    }

    @Test
    void shouldEvaluateFreshnessWindowInBusinessTimezone() {
        TimeZone previousTimeZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
            item.setNotifyChannels("IN_APP,IM");
            item.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
                    .minusMinutes(20)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            when(paymentTaskCenterMapper.findPendingOutboxAlerts(100))
                    .thenReturn(Collections.singletonList(item));
            when(imNotifier.channelCode()).thenReturn("IM");
            when(smsNotifier.channelCode()).thenReturn("SMS");
            when(emailNotifier.channelCode()).thenReturn("EMAIL");
            when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "IM"))
                    .thenReturn(false);
            when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM"))
                    .thenReturn(Collections.singletonList(buildProvider(
                            "ALERT_IM_WECOM",
                            "企业微信告警机器人",
                            "wecom-bot-alerts",
                            "TPL_PAYMENT_ISSUE_IM_V1",
                            "DEFAULT",
                            100,
                            "失败重试3次/间隔5分钟/时间窗10分钟")));

            PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                    paymentTaskCenterMapper,
                    Arrays.asList(imNotifier, smsNotifier, emailNotifier)
            ).dispatchPendingAlerts("payment-core-admin");

            ArgumentCaptor<PaymentIssueAlertLogEntity> logCaptor =
                    ArgumentCaptor.forClass(PaymentIssueAlertLogEntity.class);
            verify(paymentTaskCenterMapper).insertIssueAlertLog(logCaptor.capture());
            verify(imNotifier, org.mockito.Mockito.never()).send(any(PaymentIssueAlertDispatchItemDTO.class));
            Assertions.assertEquals(1, result.getFailCount());
            Assertions.assertEquals("FRESHNESS_WINDOW_EXPIRED",
                    logCaptor.getValue().getProviderDeliveryStatus());
        } finally {
            TimeZone.setDefault(previousTimeZone);
        }
    }

    @Test
    void shouldDispatchEscalationOutboxEvenWhenOriginalIssueAlreadyDispatched() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setAlertNo("PIA-ESCALATION-001");
        item.setNotifyChannels("IN_APP,IM");
        item.setReceiver("支付技术负责人");
        item.setAlertContent("升级来源告警 PIA-OUTBOX-001 已超过 30 分钟未确认，请升级跟进。");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100)).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-ESCALATION-001", "IM")).thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM")).thenReturn(Collections.singletonList(
                buildProvider("ALERT_IM_WECOM_P1", "企业微信告警机器人-P1", "wecom-bot-alerts", "TPL_PAYMENT_ISSUE_IM_P1_V1", "severity=P1", 10)
        ));
        when(imNotifier.send(any(PaymentIssueAlertDispatchItemDTO.class))).thenReturn(buildDeliveryResult("IM-ESC-001", "ACCEPTED", "升级告警已接单", "[IM] ESCALATION"));

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts("payment-core-admin");

        ArgumentCaptor<PaymentIssueAlertLogEntity> logCaptor = ArgumentCaptor.forClass(PaymentIssueAlertLogEntity.class);
        verify(paymentTaskCenterMapper).insertIssueAlertLog(logCaptor.capture());
        Assertions.assertEquals(1, result.getSuccessCount());
        Assertions.assertEquals("PIA-ESCALATION-001", logCaptor.getValue().getSourceAlertNo());
        Assertions.assertEquals("支付技术负责人", logCaptor.getValue().getReceiver());
        Assertions.assertTrue(logCaptor.getValue().getAlertContent().contains("PIA-OUTBOX-001"));
    }

    @Test
    void shouldSkipDispatchWhenSourceAlertAlreadyAcknowledged() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM,SMS");
        item.setAckStatus("已确认");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100)).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts("payment-core-admin");

        verify(imNotifier, org.mockito.Mockito.never()).send(any(PaymentIssueAlertDispatchItemDTO.class));
        verify(smsNotifier, org.mockito.Mockito.never()).send(any(PaymentIssueAlertDispatchItemDTO.class));
        verify(emailNotifier, org.mockito.Mockito.never()).send(any(PaymentIssueAlertDispatchItemDTO.class));
        verify(paymentTaskCenterMapper, org.mockito.Mockito.never()).insertIssueAlertLog(any(PaymentIssueAlertLogEntity.class));
        verify(paymentTaskCenterMapper, org.mockito.Mockito.never()).updateIssueAlertDeliveryStatus(any(PaymentIssueAlertLogEntity.class));
        Assertions.assertEquals(0, result.getProcessedCount());
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(0, result.getWarningCount());
        Assertions.assertEquals(0, result.getFailCount());
    }

    @Test
    void shouldMarkFailureWhenProviderConfigMissingForChannel() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100)).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "IM")).thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM")).thenReturn(Collections.emptyList());

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts("payment-core-admin");

        verify(imNotifier, org.mockito.Mockito.never()).send(any(PaymentIssueAlertDispatchItemDTO.class));
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(0, result.getWarningCount());
        Assertions.assertEquals(1, result.getFailCount());
    }

    @Test
    void shouldSkipRetryWhenFailureCountExceedsConfiguredLimit() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100)).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "IM")).thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM")).thenReturn(Collections.singletonList(
                buildProvider("ALERT_IM_WECOM", "企业微信告警机器人", "wecom-bot-alerts", "TPL_PAYMENT_ISSUE_IM_V1", "DEFAULT", 100, "失败重试1次/间隔5分钟")
        ));
        when(paymentTaskCenterMapper.countFailedIssueAlertChannelDeliveries("PIA-OUTBOX-001", "IM")).thenReturn(2);

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts("payment-core-admin");

        verify(imNotifier, org.mockito.Mockito.never()).send(any(PaymentIssueAlertDispatchItemDTO.class));
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(0, result.getWarningCount());
        Assertions.assertEquals(1, result.getFailCount());
    }

    @Test
    void shouldSkipRetryWhenCooldownWindowNotElapsed() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100)).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "IM")).thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM")).thenReturn(Collections.singletonList(
                buildProvider("ALERT_IM_WECOM", "企业微信告警机器人", "wecom-bot-alerts", "TPL_PAYMENT_ISSUE_IM_V1", "DEFAULT", 100, "失败重试2次/间隔5分钟")
        ));
        when(paymentTaskCenterMapper.countFailedIssueAlertChannelDeliveries("PIA-OUTBOX-001", "IM")).thenReturn(1);
        PaymentIssueAlertLogEntity latestLog = new PaymentIssueAlertLogEntity();
        latestLog.setAlertStatus("派发失败");
        latestLog.setCreatedAt("2099-12-31 23:59:59");
        when(paymentTaskCenterMapper.findLatestIssueAlertChannelDeliveryLog("PIA-OUTBOX-001", "IM")).thenReturn(latestLog);

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts("payment-core-admin");

        verify(imNotifier, org.mockito.Mockito.never()).send(any(PaymentIssueAlertDispatchItemDTO.class));
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(0, result.getWarningCount());
        Assertions.assertEquals(1, result.getFailCount());
    }

    @Test
    void shouldApplyExponentialRetryCooldownWhenBackoffConfigured() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100)).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "IM")).thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM")).thenReturn(Collections.singletonList(
                buildProvider("ALERT_IM_WECOM", "企业微信告警机器人", "wecom-bot-alerts", "TPL_PAYMENT_ISSUE_IM_V1", "DEFAULT", 100,
                        "失败重试3次/间隔5分钟/退避系数2倍/最大间隔12分钟")
        ));
        when(paymentTaskCenterMapper.countFailedIssueAlertChannelDeliveries("PIA-OUTBOX-001", "IM")).thenReturn(3);
        PaymentIssueAlertLogEntity latestLog = new PaymentIssueAlertLogEntity();
        latestLog.setAlertStatus("派发失败");
        latestLog.setCreatedAt("2099-12-31 23:59:59");
        when(paymentTaskCenterMapper.findLatestIssueAlertChannelDeliveryLog("PIA-OUTBOX-001", "IM")).thenReturn(latestLog);

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts("payment-core-admin");

        ArgumentCaptor<PaymentIssueAlertLogEntity> logCaptor = ArgumentCaptor.forClass(PaymentIssueAlertLogEntity.class);
        verify(paymentTaskCenterMapper).insertIssueAlertLog(logCaptor.capture());
        verify(imNotifier, org.mockito.Mockito.never()).send(any(PaymentIssueAlertDispatchItemDTO.class));
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(0, result.getWarningCount());
        Assertions.assertEquals(1, result.getFailCount());
        Assertions.assertEquals("RETRY_COOLDOWN_ACTIVE", logCaptor.getValue().getProviderDeliveryStatus());
        Assertions.assertTrue(logCaptor.getValue().getProviderDeliveryMessage().contains("12 分钟"));
    }

    @Test
    void shouldBlockDispatchWhenProviderRateLimitReached() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100)).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "IM")).thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM")).thenReturn(Collections.singletonList(
                buildProvider("ALERT_IM_WECOM", "企业微信告警机器人", "wecom-bot-alerts", "TPL_PAYMENT_ISSUE_IM_V1", "DEFAULT", 100, null, "每分钟 1 条")
        ));
        when(deliveryRateLimitService.tryAcquire("ALERT_IM_WECOM", "IM", 60, 1)).thenReturn(false);

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier),
                deliveryRateLimitService
        ).dispatchPendingAlerts("payment-core-admin");

        verify(imNotifier, org.mockito.Mockito.never()).send(any(PaymentIssueAlertDispatchItemDTO.class));
        verify(deliveryRateLimitService).tryAcquire("ALERT_IM_WECOM", "IM", 60, 1);
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(0, result.getWarningCount());
        Assertions.assertEquals(1, result.getFailCount());
    }

    @Test
    void shouldReserveQuotaBeforeCallingProviderThatFails() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100)).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "IM"))
                .thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM")).thenReturn(Collections.singletonList(
                buildProvider("ALERT_IM_WECOM", "企业微信告警机器人", "wecom-bot-alerts",
                        "TPL_PAYMENT_ISSUE_IM_V1", "DEFAULT", 100, null, "每分钟 10 条")
        ));
        when(deliveryRateLimitService.tryAcquire("ALERT_IM_WECOM", "IM", 60, 10)).thenReturn(true);
        when(imNotifier.send(any(PaymentIssueAlertDispatchItemDTO.class)))
                .thenThrow(new RuntimeException("provider unavailable"));

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier),
                deliveryRateLimitService
        ).dispatchPendingAlerts("payment-core-admin");

        org.mockito.InOrder callOrder = org.mockito.Mockito.inOrder(deliveryRateLimitService, imNotifier);
        callOrder.verify(deliveryRateLimitService).tryAcquire("ALERT_IM_WECOM", "IM", 60, 10);
        callOrder.verify(imNotifier).send(any(PaymentIssueAlertDispatchItemDTO.class));
        Assertions.assertEquals(1, result.getFailCount());
    }

    @Test
    void shouldFailClosedWhenProviderRateLimitWindowOverflowsSeconds() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100)).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "IM"))
                .thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM")).thenReturn(Collections.singletonList(
                buildProvider("ALERT_IM_WECOM", "企业微信告警机器人", "wecom-bot-alerts",
                        "TPL_PAYMENT_ISSUE_IM_V1", "DEFAULT", 100, null, "每40000000分钟 1 条")
        ));

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier),
                deliveryRateLimitService
        ).dispatchPendingAlerts("payment-core-admin");

        verify(deliveryRateLimitService, org.mockito.Mockito.never()).tryAcquire(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
        verify(imNotifier, org.mockito.Mockito.never()).send(any(PaymentIssueAlertDispatchItemDTO.class));
        Assertions.assertEquals(1, result.getFailCount());
    }

    @Test
    void shouldFallbackToNextProviderWhenPrimaryProviderFails() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100)).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "IM")).thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM")).thenReturn(Arrays.asList(
                buildProvider("ALERT_IM_PRIMARY", "企业微信告警机器人-主", "wecom-bot-primary", "TPL_PAYMENT_ISSUE_IM_V1", "DEFAULT", 10),
                buildProvider("ALERT_IM_BACKUP", "企业微信告警机器人-备", "wecom-bot-backup", "TPL_PAYMENT_ISSUE_IM_V2", "DEFAULT", 20)
        ));
        when(imNotifier.send(any(PaymentIssueAlertDispatchItemDTO.class)))
                .thenThrow(new RuntimeException("primary down"))
                .thenReturn(buildDeliveryResult("IM-RECEIPT-BACKUP", "ACCEPTED", "备用供应商已接单", "[IM-BACKUP] ISSUE-001"));

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts("payment-core-admin");

        ArgumentCaptor<PaymentIssueAlertLogEntity> logCaptor = ArgumentCaptor.forClass(PaymentIssueAlertLogEntity.class);
        verify(paymentTaskCenterMapper, org.mockito.Mockito.times(2)).insertIssueAlertLog(logCaptor.capture());
        Assertions.assertEquals(1, result.getSuccessCount());
        Assertions.assertEquals(0, result.getWarningCount());
        Assertions.assertEquals(0, result.getFailCount());
        Assertions.assertTrue(logCaptor.getAllValues().stream().anyMatch(log ->
                "企业微信告警机器人-主".equals(log.getProviderName())
                        && "派发失败".equals(log.getAlertStatus())
                        && log.getProviderDeliveryMessage().contains("transportError=[REDACTED]")
                        && !log.getProviderDeliveryMessage().contains("primary down")
                        && "LOCAL_FAILURE:SEND_EXCEPTION:[REDACTED]".equals(log.getProviderReceiptSnapshot())));
        Assertions.assertTrue(logCaptor.getAllValues().stream().anyMatch(log ->
                "企业微信告警机器人-备".equals(log.getProviderName()) && "已派发".equals(log.getAlertStatus())));
    }

    @Test
    void shouldFallbackToNextProviderWhenPrimaryProviderReturnsFailedStatus() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100)).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "IM")).thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM")).thenReturn(Arrays.asList(
                buildProvider("ALERT_IM_PRIMARY", "企业微信告警机器人-主", "wecom-bot-primary", "TPL_PAYMENT_ISSUE_IM_V1", "DEFAULT", 10),
                buildProvider("ALERT_IM_BACKUP", "企业微信告警机器人-备", "wecom-bot-backup", "TPL_PAYMENT_ISSUE_IM_V2", "DEFAULT", 20)
        ));
        when(imNotifier.send(any(PaymentIssueAlertDispatchItemDTO.class)))
                .thenReturn(buildDeliveryResult("IM-PRIMARY-001", "FAILED", "供应商拒绝，failureCode=IM_429", "[IM-PRIMARY] ISSUE-001"))
                .thenReturn(buildDeliveryResult("IM-BACKUP-001", "ACCEPTED", "备用供应商已接单", "[IM-BACKUP] ISSUE-001"));

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts("payment-core-admin");

        ArgumentCaptor<PaymentIssueAlertLogEntity> logCaptor = ArgumentCaptor.forClass(PaymentIssueAlertLogEntity.class);
        verify(paymentTaskCenterMapper, org.mockito.Mockito.times(2)).insertIssueAlertLog(logCaptor.capture());
        Assertions.assertEquals(1, result.getSuccessCount());
        Assertions.assertTrue(logCaptor.getAllValues().stream().anyMatch(log ->
                "企业微信告警机器人-主".equals(log.getProviderName())
                        && "派发失败".equals(log.getAlertStatus())
                        && "FAILED".equals(log.getProviderDeliveryStatus())
                        && "无需回执".equals(log.getAckStatus())
                        && "info".equals(log.getAckStatusType())));
        Assertions.assertTrue(logCaptor.getAllValues().stream().anyMatch(log ->
                "企业微信告警机器人-备".equals(log.getProviderName())
                        && "已派发".equals(log.getAlertStatus())
                        && "ACCEPTED".equals(log.getProviderDeliveryStatus())
                        && "待确认".equals(log.getAckStatus())
                        && "warn".equals(log.getAckStatusType())));
    }

    @Test
    void shouldBoundCustomNotifierResultBeforePersistingAndFallback() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100)).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "IM")).thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM")).thenReturn(Arrays.asList(
                buildProvider("ALERT_IM_PRIMARY", "企业微信告警机器人-主", "wecom-bot-primary", "TPL_PAYMENT_ISSUE_IM_V1", "DEFAULT", 10),
                buildProvider("ALERT_IM_BACKUP", "企业微信告警机器人-备", "wecom-bot-backup", "TPL_PAYMENT_ISSUE_IM_V2", "DEFAULT", 20)
        ));
        when(imNotifier.send(any(PaymentIssueAlertDispatchItemDTO.class)))
                .thenReturn(buildDeliveryResult(
                        repeated("R", 256),
                        " failed ",
                        repeated("M", 512),
                        repeated("C", 2048),
                        repeated("P", 4096)
                ))
                .thenReturn(buildDeliveryResult("IM-BACKUP-001", "ACCEPTED", "备用供应商已接单", "[IM-BACKUP] ISSUE-001"));

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts("payment-core-admin");

        ArgumentCaptor<PaymentIssueAlertLogEntity> logCaptor = ArgumentCaptor.forClass(PaymentIssueAlertLogEntity.class);
        verify(paymentTaskCenterMapper, org.mockito.Mockito.times(2)).insertIssueAlertLog(logCaptor.capture());
        PaymentIssueAlertLogEntity primaryLog = logCaptor.getAllValues().get(0);
        Assertions.assertEquals(1, result.getSuccessCount());
        Assertions.assertEquals(128, primaryLog.getProviderReceiptNo().length());
        Assertions.assertEquals("FAILED", primaryLog.getProviderDeliveryStatus());
        Assertions.assertEquals(255, primaryLog.getProviderDeliveryMessage().length());
        Assertions.assertEquals(1024, primaryLog.getRenderedContentSnapshot().length());
        Assertions.assertEquals(2048, primaryLog.getProviderReceiptSnapshot().length());
    }

    @Test
    void shouldRejectMissingAndControlOnlyNotifierStatusWithoutFalseAcceptance() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100)).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "IM"))
                .thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM")).thenReturn(Arrays.asList(
                buildProvider("ALERT_IM_PRIMARY", "企业微信告警机器人-主", "wecom-bot-primary",
                        "TPL_PAYMENT_ISSUE_IM_V1", "DEFAULT", 10),
                buildProvider("ALERT_IM_SECONDARY", "企业微信告警机器人-次", "wecom-bot-secondary",
                        "TPL_PAYMENT_ISSUE_IM_V2", "DEFAULT", 20),
                buildProvider("ALERT_IM_TERTIARY", "企业微信告警机器人-三", "wecom-bot-tertiary",
                        "TPL_PAYMENT_ISSUE_IM_V3", "DEFAULT", 30),
                buildProvider("ALERT_IM_BACKUP", "企业微信告警机器人-备", "wecom-bot-backup",
                        "TPL_PAYMENT_ISSUE_IM_V4", "DEFAULT", 40)
        ));
        when(imNotifier.send(any(PaymentIssueAlertDispatchItemDTO.class)))
                .thenReturn(null)
                .thenReturn(buildDeliveryResult(
                        "IM-SECONDARY-001", "\n", "非法状态不应被默认受理", "[IM-SECONDARY] ISSUE-001"))
                .thenReturn(buildDeliveryResult(
                        "IM-TERTIARY-001", "UNKNOWN_REMOTE", "未知状态不应落库", "[IM-TERTIARY] ISSUE-001"))
                .thenReturn(buildDeliveryResult(
                        "IM-BACKUP-001", " accepted ", "备用供应商已接单", "[IM-BACKUP] ISSUE-001"));

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts("payment-core-admin");

        ArgumentCaptor<PaymentIssueAlertLogEntity> logCaptor =
                ArgumentCaptor.forClass(PaymentIssueAlertLogEntity.class);
        verify(paymentTaskCenterMapper, org.mockito.Mockito.times(4)).insertIssueAlertLog(logCaptor.capture());
        PaymentIssueAlertLogEntity primaryLog = logCaptor.getAllValues().get(0);
        PaymentIssueAlertLogEntity secondaryLog = logCaptor.getAllValues().get(1);
        PaymentIssueAlertLogEntity tertiaryLog = logCaptor.getAllValues().get(2);
        Assertions.assertEquals(1, result.getSuccessCount());
        Assertions.assertEquals("派发失败", primaryLog.getAlertStatus());
        Assertions.assertEquals("SEND_EXCEPTION", primaryLog.getProviderDeliveryStatus());
        Assertions.assertEquals("派发失败", secondaryLog.getAlertStatus());
        Assertions.assertEquals("SEND_EXCEPTION", secondaryLog.getProviderDeliveryStatus());
        Assertions.assertFalse(secondaryLog.getProviderDeliveryMessage().contains("非法状态"));
        Assertions.assertEquals("派发失败", tertiaryLog.getAlertStatus());
        Assertions.assertEquals("SEND_EXCEPTION", tertiaryLog.getProviderDeliveryStatus());
        Assertions.assertFalse(tertiaryLog.getProviderDeliveryMessage().contains("未知状态"));
        Assertions.assertTrue(logCaptor.getAllValues().stream().anyMatch(log ->
                "企业微信告警机器人-备".equals(log.getProviderName())
                        && "已派发".equals(log.getAlertStatus())
                        && "ACCEPTED".equals(log.getProviderDeliveryStatus())));
    }

    @Test
    void shouldFallbackToNextProviderWhenPrimaryProviderReturnsTransportFailureResult() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100)).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "IM")).thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM")).thenReturn(Arrays.asList(
                buildProvider("ALERT_IM_PRIMARY", "企业微信告警机器人-主", "wecom-bot-primary", "TPL_PAYMENT_ISSUE_IM_V1", "DEFAULT", 10),
                buildProvider("ALERT_IM_BACKUP", "企业微信告警机器人-备", "wecom-bot-backup", "TPL_PAYMENT_ISSUE_IM_V2", "DEFAULT", 20)
        ));
        when(imNotifier.send(any(PaymentIssueAlertDispatchItemDTO.class)))
                .thenReturn(buildDeliveryResult("IM-PRIMARY-TRANSPORT-001", "FAILED", "IM HTTP 网关请求失败，timeout=4500ms，transportError=Read timed out", "[IM-PRIMARY] ISSUE-001"))
                .thenReturn(buildDeliveryResult("IM-BACKUP-001", "ACCEPTED", "备用供应商已接单", "[IM-BACKUP] ISSUE-001"));

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts("payment-core-admin");

        ArgumentCaptor<PaymentIssueAlertLogEntity> logCaptor = ArgumentCaptor.forClass(PaymentIssueAlertLogEntity.class);
        verify(paymentTaskCenterMapper, org.mockito.Mockito.times(2)).insertIssueAlertLog(logCaptor.capture());
        Assertions.assertEquals(1, result.getSuccessCount());
        Assertions.assertTrue(logCaptor.getAllValues().stream().anyMatch(log ->
                "企业微信告警机器人-主".equals(log.getProviderName())
                        && "派发失败".equals(log.getAlertStatus())
                        && "FAILED".equals(log.getProviderDeliveryStatus())
                        && log.getProviderDeliveryMessage().contains("transportError=[REDACTED]")
                        && !log.getProviderDeliveryMessage().contains("Read timed out")));
        Assertions.assertTrue(logCaptor.getAllValues().stream().anyMatch(log ->
                "企业微信告警机器人-备".equals(log.getProviderName())
                        && "已派发".equals(log.getAlertStatus())
                        && "ACCEPTED".equals(log.getProviderDeliveryStatus())));
    }

    @Test
    void shouldFallbackToBackupProviderWhenPrimaryProviderCircuitOpen() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100)).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("PIA-OUTBOX-001", "IM")).thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM")).thenReturn(Arrays.asList(
                buildProvider("ALERT_IM_PRIMARY", "企业微信告警机器人-主", "wecom-bot-primary", "TPL_PAYMENT_ISSUE_IM_V1", "DEFAULT", 10),
                buildProvider("ALERT_IM_BACKUP", "企业微信告警机器人-备", "wecom-bot-backup", "TPL_PAYMENT_ISSUE_IM_V2", "DEFAULT", 20)
        ));
        when(paymentTaskCenterMapper.countIssueAlertProviderFailedDeliveriesSince(org.mockito.ArgumentMatchers.eq("ALERT_IM_PRIMARY"), org.mockito.ArgumentMatchers.eq("IM"), any(String.class))).thenReturn(3);
        when(paymentTaskCenterMapper.countIssueAlertProviderFailedDeliveriesSince(org.mockito.ArgumentMatchers.eq("ALERT_IM_BACKUP"), org.mockito.ArgumentMatchers.eq("IM"), any(String.class))).thenReturn(0);
        when(imNotifier.send(any(PaymentIssueAlertDispatchItemDTO.class))).thenReturn(buildDeliveryResult("IM-RECEIPT-BACKUP", "ACCEPTED", "备用供应商已接单", "[IM-BACKUP] ISSUE-001"));

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts("payment-core-admin");

        ArgumentCaptor<PaymentIssueAlertLogEntity> logCaptor = ArgumentCaptor.forClass(PaymentIssueAlertLogEntity.class);
        verify(paymentTaskCenterMapper, org.mockito.Mockito.times(2)).insertIssueAlertLog(logCaptor.capture());
        Assertions.assertEquals(1, result.getSuccessCount());
        Assertions.assertTrue(logCaptor.getAllValues().stream().anyMatch(log ->
                "企业微信告警机器人-主".equals(log.getProviderName()) && "派发失败".equals(log.getAlertStatus())
                        && "PROVIDER_CIRCUIT_OPEN".equals(log.getProviderDeliveryStatus())));
        Assertions.assertTrue(logCaptor.getAllValues().stream().anyMatch(log ->
                "企业微信告警机器人-备".equals(log.getProviderName()) && "已派发".equals(log.getAlertStatus())));
    }

    @Test
    void shouldReconcileAcceptedDeliveryReceiptsSuccessfully() {
        PaymentIssueAlertLogEntity acceptedLog = new PaymentIssueAlertLogEntity();
        acceptedLog.setAlertNo("PIA-IM-001");
        acceptedLog.setSourceAlertNo("PIA-OUTBOX-001");
        acceptedLog.setIssueNo("ISSUE-001");
        acceptedLog.setAlertChannel("IM");
        acceptedLog.setProviderReceiptNo("IM-RECEIPT-001");
        acceptedLog.setProviderDeliveryStatus("ACCEPTED");
        when(paymentTaskCenterMapper.findAcceptedIssueAlertDeliveryLogs(100))
                .thenReturn(Collections.singletonList(acceptedLog));
        when(paymentTaskCenterMapper.claimIssueAlertReceiptQuery(
                "PIA-IM-001", "PIA-OUTBOX-001", "ISSUE-001", 0, true, 10, 30)).thenReturn(1);
        when(paymentTaskCenterMapper.updateIssueAlertProviderReceipt(any(PaymentIssueAlertLogEntity.class))).thenReturn(1);
        when(paymentTaskCenterMapper.updateSourceIssueAlertAcknowledgement(
                any(PaymentIssueAlertLogEntity.class))).thenReturn(1);
        when(receiptQueryAdapter.supports("IM")).thenReturn(true);
        when(receiptQueryAdapter.query(acceptedLog)).thenReturn(
                buildDeliveryResult(
                        "IM-RECEIPT-001",
                        "DELIVERED",
                        "供应商查询确认送达",
                        "",
                        "CUSTOM:{\"accessKey\":\"secret\",\"status\":\"DELIVERED\"}"));

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier),
                Collections.singletonList(receiptQueryAdapter),
                receiptQueryRateLimitService
        ).reconcileDeliveryReceipts(" 支付运营 ");

        verify(receiptQueryRateLimitService).acquire("IM", "PIA-IM-001");
        ArgumentCaptor<PaymentIssueAlertLogEntity> captor = ArgumentCaptor.forClass(PaymentIssueAlertLogEntity.class);
        verify(paymentTaskCenterMapper).updateIssueAlertProviderReceipt(captor.capture());
        verify(paymentTaskCenterMapper).updateSourceIssueAlertAcknowledgement(org.mockito.ArgumentMatchers.argThat(
                entity -> "PIA-OUTBOX-001".equals(entity.getAlertNo())
                        && "ISSUE-001".equals(entity.getIssueNo())
                        && "已确认".equals(entity.getAckStatus())
                        && "支付运营".equals(entity.getAckOperator())
        ));
        verify(paymentTaskCenterMapper).insertTaskRunLog(any(PaymentTaskRunLogEntity.class));
        Assertions.assertEquals("DELIVERED", captor.getValue().getProviderDeliveryStatus());
        Assertions.assertEquals("PIA-OUTBOX-001", captor.getValue().getSourceAlertNo());
        Assertions.assertEquals("ISSUE-001", captor.getValue().getIssueNo());
        Assertions.assertEquals("IM-RECEIPT-001", captor.getValue().getProviderReceiptNo());
        Assertions.assertEquals("供应商查询确认送达", captor.getValue().getProviderDeliveryMessage());
        Assertions.assertEquals(
                "CUSTOM:{\"accessKey\":\"[REDACTED]\",\"status\":\"DELIVERED\"}",
                captor.getValue().getProviderReceiptSnapshot());
        Assertions.assertEquals("已确认", captor.getValue().getAckStatus());
        Assertions.assertEquals("支付运营", captor.getValue().getAckOperator());
        Assertions.assertEquals(Integer.valueOf(1), captor.getValue().getReceiptQueryCount());
        Assertions.assertEquals(1, result.getProcessedCount());
        Assertions.assertEquals(1, result.getSuccessCount());
        Assertions.assertEquals(0, result.getFailCount());
    }

    @Test
    void shouldFailCurrentReceiptWithoutCallingGatewayWhenSharedQuotaIsExhausted() {
        PaymentIssueAlertLogEntity acceptedLog = buildAcceptedLog("PIA-IM-LIMITED", "PIA-OUTBOX-LIMITED");
        when(paymentTaskCenterMapper.findAcceptedIssueAlertDeliveryLogs(100))
                .thenReturn(Collections.singletonList(acceptedLog));
        when(paymentTaskCenterMapper.claimIssueAlertReceiptQuery(
                "PIA-IM-LIMITED", "PIA-OUTBOX-LIMITED", "ISSUE-001", 0, true, 10, 30)).thenReturn(1);
        when(paymentTaskCenterMapper.completeIssueAlertReceiptQueryAttempt(
                "PIA-IM-LIMITED", "PIA-OUTBOX-LIMITED", "ISSUE-001",
                1, "RATE_LIMITED", 60, 10)).thenReturn(1);
        when(receiptQueryAdapter.supports("IM")).thenReturn(true);
        doThrow(new BusinessException(
                ErrorCode.PAYMENT_ISSUE_ALERT_RECEIPT_QUERY_RATE_LIMITED,
                "rate limited"))
                .when(receiptQueryRateLimitService).acquire("IM", "PIA-IM-LIMITED");

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier),
                Collections.singletonList(receiptQueryAdapter),
                receiptQueryRateLimitService
        ).reconcileDeliveryReceipts("payment-core-admin");

        verify(receiptQueryAdapter, org.mockito.Mockito.never()).query(acceptedLog);
        verify(paymentTaskCenterMapper, org.mockito.Mockito.never())
                .updateIssueAlertProviderReceipt(any(PaymentIssueAlertLogEntity.class));
        Assertions.assertEquals(1, result.getProcessedCount());
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(1, result.getFailCount());
    }

    @Test
    void shouldReconcileSpecifiedReceiptWithPersistedAttemptCount() {
        PaymentIssueAlertLogEntity acceptedLog = buildAcceptedLog("PIA-IM-SINGLE", "PIA-OUTBOX-SINGLE");
        acceptedLog.setReceiptQueryCount(4);
        when(paymentTaskCenterMapper.findAcceptedIssueAlertDeliveryLogByAlertNo("PIA-IM-SINGLE"))
                .thenReturn(acceptedLog);
        when(paymentTaskCenterMapper.claimIssueAlertReceiptQuery(
                "PIA-IM-SINGLE", "PIA-OUTBOX-SINGLE", "ISSUE-001", 4, true, 10, 30))
                .thenReturn(1);
        when(paymentTaskCenterMapper.updateIssueAlertProviderReceipt(any(PaymentIssueAlertLogEntity.class)))
                .thenReturn(1);
        when(paymentTaskCenterMapper.updateSourceIssueAlertAcknowledgement(
                any(PaymentIssueAlertLogEntity.class))).thenReturn(1);
        when(receiptQueryAdapter.supports("IM")).thenReturn(true);
        when(receiptQueryAdapter.query(acceptedLog)).thenReturn(
                buildDeliveryResult("IM-RECEIPT-SINGLE", "DELIVERED", "供应商查询确认送达", ""));

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier),
                Collections.singletonList(receiptQueryAdapter),
                receiptQueryRateLimitService
        ).reconcileDeliveryReceipt(" PIA-IM-SINGLE ", " 支付运营 ");

        verify(paymentTaskCenterMapper).claimIssueAlertReceiptQuery(
                "PIA-IM-SINGLE", "PIA-OUTBOX-SINGLE", "ISSUE-001", 4, true, 10, 30);
        verify(receiptQueryRateLimitService).acquire("IM", "PIA-IM-SINGLE");
        ArgumentCaptor<PaymentTaskRunLogEntity> taskLogCaptor =
                ArgumentCaptor.forClass(PaymentTaskRunLogEntity.class);
        verify(paymentTaskCenterMapper).insertTaskRunLog(taskLogCaptor.capture());
        Assertions.assertEquals("支付运营", taskLogCaptor.getValue().getTriggeredBy());
        ArgumentCaptor<PaymentIssueAlertLogEntity> receiptCaptor =
                ArgumentCaptor.forClass(PaymentIssueAlertLogEntity.class);
        verify(paymentTaskCenterMapper).updateIssueAlertProviderReceipt(receiptCaptor.capture());
        Assertions.assertEquals("支付运营", receiptCaptor.getValue().getAckOperator());
        Assertions.assertEquals(1, result.getProcessedCount());
        Assertions.assertEquals(1, result.getSuccessCount());
    }

    @Test
    void shouldRejectSpecifiedReceiptThatIsNotPendingConfirmation() {
        when(paymentTaskCenterMapper.findAcceptedIssueAlertDeliveryLogByAlertNo("PIA-ACKED"))
                .thenReturn(null);
        PaymentIssueAlertDeliveryServiceImpl service = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        );

        IllegalArgumentException exception = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.reconcileDeliveryReceipt("PIA-ACKED", "支付运营"));

        Assertions.assertTrue(exception.getMessage().contains("当前状态不允许"));
        verify(paymentTaskCenterMapper, org.mockito.Mockito.never()).claimIssueAlertReceiptQuery(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void shouldRejectSpecifiedReceiptWithoutOperatorBeforeQueryingCandidate() {
        PaymentIssueAlertDeliveryServiceImpl service = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        );

        IllegalArgumentException exception = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.reconcileDeliveryReceipt("PIA-IM-SINGLE", " "));

        Assertions.assertTrue(exception.getMessage().contains("人工任务操作人不能为空"));
        verify(paymentTaskCenterMapper, org.mockito.Mockito.never())
                .findAcceptedIssueAlertDeliveryLogByAlertNo(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldRejectSpecifiedReceiptWithOversizedOperatorBeforeQueryingCandidate() {
        PaymentIssueAlertDeliveryServiceImpl service = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        );

        IllegalArgumentException exception = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.reconcileDeliveryReceipt(
                        "PIA-IM-SINGLE", String.join("", Collections.nCopies(65, "A"))));
        IllegalArgumentException controlException = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.reconcileDeliveryReceipt("PIA-IM-SINGLE", "finance-ops\n"));

        Assertions.assertTrue(exception.getMessage().contains("不能超过64个字符"));
        Assertions.assertTrue(controlException.getMessage().contains("不能包含控制字符"));
        verify(paymentTaskCenterMapper, org.mockito.Mockito.never())
                .findAcceptedIssueAlertDeliveryLogByAlertNo(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldRejectInvalidSpecifiedReceiptAlertNumberBeforeQueryingCandidate() {
        PaymentIssueAlertDeliveryServiceImpl service = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        );

        IllegalArgumentException blankException = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.reconcileDeliveryReceipt(" ", "支付运营"));
        IllegalArgumentException oversizedException = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.reconcileDeliveryReceipt(
                        String.join("", Collections.nCopies(65, "A")), "支付运营"));
        IllegalArgumentException controlException = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.reconcileDeliveryReceipt("PIA-IM-001\n", "支付运营"));

        Assertions.assertTrue(blankException.getMessage().contains("告警编号不能为空"));
        Assertions.assertTrue(oversizedException.getMessage().contains("告警编号不能超过64个字符"));
        Assertions.assertTrue(controlException.getMessage().contains("告警编号不能包含控制字符"));
        verify(paymentTaskCenterMapper, org.mockito.Mockito.never())
                .findAcceptedIssueAlertDeliveryLogByAlertNo(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldFailClosedWhenNoReceiptQueryAdapterIsAvailable() {
        PaymentIssueAlertLogEntity acceptedLog = buildAcceptedLog("PIA-IM-001", "PIA-OUTBOX-001");
        when(paymentTaskCenterMapper.findAcceptedIssueAlertDeliveryLogs(100))
                .thenReturn(Collections.singletonList(acceptedLog));
        when(paymentTaskCenterMapper.claimIssueAlertReceiptQuery(
                "PIA-IM-001", "PIA-OUTBOX-001", "ISSUE-001", 0, true, 10, 30)).thenReturn(1);
        when(paymentTaskCenterMapper.completeIssueAlertReceiptQueryAttempt(
                "PIA-IM-001", "PIA-OUTBOX-001", "ISSUE-001",
                1, "ADAPTER_MISSING", 60, 10)).thenReturn(1);

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).reconcileDeliveryReceipts("payment-core-admin");

        verify(paymentTaskCenterMapper, org.mockito.Mockito.never())
                .updateIssueAlertProviderReceipt(any(PaymentIssueAlertLogEntity.class));
        verify(paymentTaskCenterMapper, org.mockito.Mockito.never())
                .updateSourceIssueAlertAcknowledgement(any(PaymentIssueAlertLogEntity.class));
        Assertions.assertEquals(1, result.getProcessedCount());
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(1, result.getFailCount());
    }

    @Test
    void shouldRejectReceiptWithMismatchedSourceBeforeClaimingOrCallingProvider() {
        PaymentIssueAlertLogEntity acceptedLog = buildAcceptedLog(
                "PIA-IM-WRONG-SOURCE", "PIA-OUTBOX-OTHER-ISSUE");
        when(paymentTaskCenterMapper.findAcceptedIssueAlertDeliveryLogs(100))
                .thenReturn(Collections.singletonList(acceptedLog));
        when(paymentTaskCenterMapper.isMatchingPendingSourceIssueAlert(
                "PIA-OUTBOX-OTHER-ISSUE", "ISSUE-001")).thenReturn(false);

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier),
                Collections.singletonList(receiptQueryAdapter),
                receiptQueryRateLimitService
        ).reconcileDeliveryReceipts("payment-core-admin");

        verify(paymentTaskCenterMapper, org.mockito.Mockito.never()).claimIssueAlertReceiptQuery(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
        verify(receiptQueryAdapter, org.mockito.Mockito.never()).query(any(PaymentIssueAlertLogEntity.class));
        verify(receiptQueryRateLimitService, org.mockito.Mockito.never())
                .acquire(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        Assertions.assertEquals(1, result.getProcessedCount());
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(1, result.getFailCount());
    }

    @Test
    void shouldCompleteReceiptAttemptWhenAdapterSelectionFails() {
        PaymentIssueAlertLogEntity acceptedLog = buildAcceptedLog("PIA-IM-SELECT-ERROR", "PIA-OUTBOX-001");
        acceptedLog.setReceiptQueryCount(2);
        when(paymentTaskCenterMapper.findAcceptedIssueAlertDeliveryLogs(100))
                .thenReturn(Collections.singletonList(acceptedLog));
        when(paymentTaskCenterMapper.claimIssueAlertReceiptQuery(
                "PIA-IM-SELECT-ERROR", "PIA-OUTBOX-001", "ISSUE-001", 2, true, 10, 30)).thenReturn(1);
        when(paymentTaskCenterMapper.completeIssueAlertReceiptQueryAttempt(
                "PIA-IM-SELECT-ERROR", "PIA-OUTBOX-001", "ISSUE-001",
                3, "QUERY_ERROR", 240, 10)).thenReturn(1);
        when(receiptQueryAdapter.supports("IM"))
                .thenThrow(new IllegalStateException("adapter selection failed"));

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier),
                Collections.singletonList(receiptQueryAdapter),
                receiptQueryRateLimitService
        ).reconcileDeliveryReceipts("payment-core-admin");

        verify(paymentTaskCenterMapper).completeIssueAlertReceiptQueryAttempt(
                "PIA-IM-SELECT-ERROR", "PIA-OUTBOX-001", "ISSUE-001",
                3, "QUERY_ERROR", 240, 10);
        verify(receiptQueryAdapter, org.mockito.Mockito.never())
                .query(any(PaymentIssueAlertLogEntity.class));
        verify(receiptQueryRateLimitService, org.mockito.Mockito.never())
                .acquire(any(String.class), any(String.class));
        Assertions.assertEquals(1, result.getProcessedCount());
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(1, result.getFailCount());
    }

    @Test
    void shouldRejectReceiptQueryResultForDifferentProviderReceipt() {
        PaymentIssueAlertLogEntity acceptedLog = buildAcceptedLog("PIA-IM-001", "PIA-OUTBOX-001");
        acceptedLog.setProviderReceiptNo("IM-RECEIPT-001");
        when(paymentTaskCenterMapper.findAcceptedIssueAlertDeliveryLogs(100))
                .thenReturn(Collections.singletonList(acceptedLog));
        when(paymentTaskCenterMapper.claimIssueAlertReceiptQuery(
                "PIA-IM-001", "PIA-OUTBOX-001", "ISSUE-001", 0, true, 10, 30)).thenReturn(1);
        when(paymentTaskCenterMapper.completeIssueAlertReceiptQueryAttempt(
                "PIA-IM-001", "PIA-OUTBOX-001", "ISSUE-001",
                1, "RECEIPT_CONFLICT", 60, 10)).thenReturn(1);
        when(receiptQueryAdapter.supports("IM")).thenReturn(true);
        when(receiptQueryAdapter.query(acceptedLog)).thenReturn(
                buildDeliveryResult("IM-RECEIPT-OTHER", "DELIVERED", "供应商查询确认送达", ""));

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier),
                Collections.singletonList(receiptQueryAdapter)
        ).reconcileDeliveryReceipts("payment-core-admin");

        verify(paymentTaskCenterMapper, org.mockito.Mockito.never())
                .updateIssueAlertProviderReceipt(any(PaymentIssueAlertLogEntity.class));
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(1, result.getFailCount());
    }

    @Test
    void shouldRejectDeliveredReceiptWithoutPersistedProviderReceiptCorrelation() {
        PaymentIssueAlertLogEntity acceptedLog = buildAcceptedLog("PIA-IM-001", "PIA-OUTBOX-001");
        acceptedLog.setProviderReceiptNo("IM-RECEIPT-001");
        when(paymentTaskCenterMapper.findAcceptedIssueAlertDeliveryLogs(100))
                .thenReturn(Collections.singletonList(acceptedLog));
        when(paymentTaskCenterMapper.claimIssueAlertReceiptQuery(
                "PIA-IM-001", "PIA-OUTBOX-001", "ISSUE-001", 0, true, 10, 30)).thenReturn(1);
        when(paymentTaskCenterMapper.completeIssueAlertReceiptQueryAttempt(
                "PIA-IM-001", "PIA-OUTBOX-001", "ISSUE-001",
                1, "RECEIPT_CONFLICT", 60, 10)).thenReturn(1);
        when(receiptQueryAdapter.supports("IM")).thenReturn(true);
        when(receiptQueryAdapter.query(acceptedLog)).thenReturn(
                buildDeliveryResult(null, "DELIVERED", "供应商查询确认送达", ""));

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier),
                Collections.singletonList(receiptQueryAdapter)
        ).reconcileDeliveryReceipts("payment-core-admin");

        verify(paymentTaskCenterMapper, org.mockito.Mockito.never())
                .updateIssueAlertProviderReceipt(any(PaymentIssueAlertLogEntity.class));
        verify(paymentTaskCenterMapper).completeIssueAlertReceiptQueryAttempt(
                "PIA-IM-001", "PIA-OUTBOX-001", "ISSUE-001",
                1, "RECEIPT_CONFLICT", 60, 10);
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(1, result.getFailCount());
    }

    @Test
    void shouldNormalizeAndValidateReceiptQueryResultsAtServiceBoundary() {
        PaymentIssueAlertLogEntity acceptedLog = buildAcceptedLog("PIA-IM-001", "PIA-OUTBOX-001");
        when(paymentTaskCenterMapper.findAcceptedIssueAlertDeliveryLogs(100))
                .thenReturn(Collections.singletonList(acceptedLog));
        when(paymentTaskCenterMapper.claimIssueAlertReceiptQuery(
                "PIA-IM-001", "PIA-OUTBOX-001", "ISSUE-001", 0, true, 10, 30))
                .thenReturn(1, 1, 1, 1);
        when(paymentTaskCenterMapper.completeIssueAlertReceiptQueryAttempt(
                eq("PIA-IM-001"), eq("PIA-OUTBOX-001"), eq("ISSUE-001"),
                eq(1), org.mockito.ArgumentMatchers.anyString(), eq(60), eq(10)))
                .thenReturn(1);
        when(receiptQueryAdapter.supports("IM")).thenReturn(true);
        when(receiptQueryAdapter.query(acceptedLog)).thenReturn(
                buildDeliveryResult("IM-RECEIPT-001", " UNKNOWN_REMOTE ", "未知状态", ""),
                buildDeliveryResult("IM-RECEIPT-001", "ACCEPTED", "污染字段\n", ""),
                buildDeliveryResult("IM-RECEIPT-001", "ACCEPTED", repeated("M", 256), ""),
                buildDeliveryResult(" IM-RECEIPT-001 ", " accepted ", " 供应商仍在处理 ", ""));
        PaymentIssueAlertDeliveryServiceImpl service = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier),
                Collections.singletonList(receiptQueryAdapter)
        );

        PaymentTaskActionResultDTO unknownResult = service.reconcileDeliveryReceipts("payment-core-admin");
        PaymentTaskActionResultDTO pollutedResult = service.reconcileDeliveryReceipts("payment-core-admin");
        PaymentTaskActionResultDTO oversizedResult = service.reconcileDeliveryReceipts("payment-core-admin");
        PaymentTaskActionResultDTO normalizedResult = service.reconcileDeliveryReceipts("payment-core-admin");

        verify(paymentTaskCenterMapper, times(3)).completeIssueAlertReceiptQueryAttempt(
                "PIA-IM-001", "PIA-OUTBOX-001", "ISSUE-001", 1, "QUERY_ERROR", 60, 10);
        verify(paymentTaskCenterMapper).completeIssueAlertReceiptQueryAttempt(
                "PIA-IM-001", "PIA-OUTBOX-001", "ISSUE-001", 1, "ACCEPTED", 60, 10);
        verify(paymentTaskCenterMapper, org.mockito.Mockito.never())
                .updateIssueAlertProviderReceipt(any(PaymentIssueAlertLogEntity.class));
        Assertions.assertEquals(1, unknownResult.getFailCount());
        Assertions.assertEquals(1, pollutedResult.getFailCount());
        Assertions.assertEquals(1, oversizedResult.getFailCount());
        Assertions.assertEquals(1, normalizedResult.getFailCount());
    }

    @Test
    void shouldRejectReceiptQueryResultsThatDoNotConfirmDelivery() {
        PaymentIssueAlertLogEntity acceptedLog = buildAcceptedLog("PIA-IM-001", "PIA-OUTBOX-001");
        when(paymentTaskCenterMapper.findAcceptedIssueAlertDeliveryLogs(100))
                .thenReturn(Collections.singletonList(acceptedLog));
        when(paymentTaskCenterMapper.claimIssueAlertReceiptQuery(
                "PIA-IM-001", "PIA-OUTBOX-001", "ISSUE-001", 0, true, 10, 30))
                .thenReturn(1, 1);
        when(paymentTaskCenterMapper.completeIssueAlertReceiptQueryAttempt(
                eq("PIA-IM-001"), eq("PIA-OUTBOX-001"), eq("ISSUE-001"),
                eq(1), org.mockito.ArgumentMatchers.anyString(), eq(60), eq(10))).thenReturn(1);
        when(receiptQueryAdapter.supports("IM")).thenReturn(true);
        when(receiptQueryAdapter.query(acceptedLog)).thenReturn(
                buildDeliveryResult("IM-RECEIPT-001", "ACCEPTED", "供应商仍在处理", ""),
                buildDeliveryResult("IM-RECEIPT-001", "FAILED", "供应商确认投递失败", ""));
        PaymentIssueAlertDeliveryServiceImpl service = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier),
                Collections.singletonList(receiptQueryAdapter)
        );

        PaymentTaskActionResultDTO acceptedResult = service.reconcileDeliveryReceipts("payment-core-admin");
        PaymentTaskActionResultDTO failedResult = service.reconcileDeliveryReceipts("payment-core-admin");

        verify(paymentTaskCenterMapper).completeIssueAlertReceiptQueryAttempt(
                "PIA-IM-001", "PIA-OUTBOX-001", "ISSUE-001", 1, "ACCEPTED", 60, 10);
        verify(paymentTaskCenterMapper).completeIssueAlertReceiptQueryAttempt(
                "PIA-IM-001", "PIA-OUTBOX-001", "ISSUE-001", 1, "FAILED", 60, 10);
        verify(paymentTaskCenterMapper, org.mockito.Mockito.never())
                .updateIssueAlertProviderReceipt(any(PaymentIssueAlertLogEntity.class));
        verify(paymentTaskCenterMapper, org.mockito.Mockito.never())
                .updateSourceIssueAlertAcknowledgement(any(PaymentIssueAlertLogEntity.class));
        Assertions.assertEquals(0, acceptedResult.getSuccessCount());
        Assertions.assertEquals(1, acceptedResult.getFailCount());
        Assertions.assertEquals(0, failedResult.getSuccessCount());
        Assertions.assertEquals(1, failedResult.getFailCount());
    }

    @Test
    void shouldCapReceiptQueryBackoffAfterRepeatedQueryErrors() {
        PaymentIssueAlertLogEntity acceptedLog = buildAcceptedLog("PIA-IM-BACKOFF", "PIA-OUTBOX-BACKOFF");
        acceptedLog.setReceiptQueryCount(4);
        when(paymentTaskCenterMapper.findAcceptedIssueAlertDeliveryLogs(100))
                .thenReturn(Collections.singletonList(acceptedLog));
        when(paymentTaskCenterMapper.claimIssueAlertReceiptQuery(
                "PIA-IM-BACKOFF", "PIA-OUTBOX-BACKOFF", "ISSUE-001", 4, true, 10, 30)).thenReturn(1);
        when(paymentTaskCenterMapper.completeIssueAlertReceiptQueryAttempt(
                "PIA-IM-BACKOFF", "PIA-OUTBOX-BACKOFF", "ISSUE-001",
                5, "QUERY_ERROR", 900, 10)).thenReturn(1);
        when(receiptQueryAdapter.supports("IM")).thenReturn(true);
        when(receiptQueryAdapter.query(acceptedLog)).thenThrow(new IllegalStateException("provider unavailable"));
        PaymentIssueAlertDeliveryServiceImpl service = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier),
                Collections.singletonList(receiptQueryAdapter)
        );
        service.configureReceiptQueryCompensation(60, 60, 2, 900, 10, 100, 30);

        PaymentTaskActionResultDTO result = service.reconcileDeliveryReceipts("payment-core-admin");

        verify(paymentTaskCenterMapper).completeIssueAlertReceiptQueryAttempt(
                "PIA-IM-BACKOFF", "PIA-OUTBOX-BACKOFF", "ISSUE-001",
                5, "QUERY_ERROR", 900, 10);
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(1, result.getFailCount());
    }

    @Test
    void shouldNotCallReceiptQueryAdapterWhenClaimFails() {
        PaymentIssueAlertLogEntity acceptedLog = buildAcceptedLog("PIA-IM-CLAIMED", "PIA-OUTBOX-CLAIMED");
        when(paymentTaskCenterMapper.findAcceptedIssueAlertDeliveryLogs(100))
                .thenReturn(Collections.singletonList(acceptedLog));
        when(paymentTaskCenterMapper.claimIssueAlertReceiptQuery(
                "PIA-IM-CLAIMED", "PIA-OUTBOX-CLAIMED", "ISSUE-001", 0, true, 10, 30)).thenReturn(0);

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier),
                Collections.singletonList(receiptQueryAdapter),
                receiptQueryRateLimitService
        ).reconcileDeliveryReceipts("payment-core-admin");

        verify(receiptQueryAdapter, org.mockito.Mockito.never()).supports(any(String.class));
        verify(receiptQueryAdapter, org.mockito.Mockito.never()).query(any(PaymentIssueAlertLogEntity.class));
        verify(receiptQueryRateLimitService, org.mockito.Mockito.never())
                .acquire(any(String.class), any(String.class));
        verify(paymentTaskCenterMapper, org.mockito.Mockito.never()).completeIssueAlertReceiptQueryAttempt(
                any(String.class), any(String.class), any(String.class),
                org.mockito.ArgumentMatchers.anyInt(), any(String.class),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(1, result.getFailCount());
    }

    @Test
    void shouldReturnZeroWhenNoAcceptedReceiptsNeedReconcile() {
        when(paymentTaskCenterMapper.findAcceptedIssueAlertDeliveryLogs(100)).thenReturn(Collections.emptyList());

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).reconcileDeliveryReceipts("payment-core-admin");

        verify(paymentTaskCenterMapper).insertTaskRunLog(any(PaymentTaskRunLogEntity.class));
        Assertions.assertEquals(0, result.getProcessedCount());
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(0, result.getFailCount());
    }

    @Test
    void shouldGenerateUniqueTaskLogNumbersWithoutMillisecondCollision() {
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100)).thenReturn(Collections.emptyList());
        PaymentIssueAlertDeliveryServiceImpl service = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        );

        service.dispatchPendingAlerts("payment-core-admin");
        service.dispatchPendingAlerts("payment-core-admin");

        ArgumentCaptor<PaymentTaskRunLogEntity> captor = ArgumentCaptor.forClass(PaymentTaskRunLogEntity.class);
        verify(paymentTaskCenterMapper, times(2)).insertTaskRunLog(captor.capture());
        String firstTaskLogNo = captor.getAllValues().get(0).getTaskLogNo();
        String secondTaskLogNo = captor.getAllValues().get(1).getTaskLogNo();
        Assertions.assertTrue(firstTaskLogNo.matches("TL[0-9A-F]{16}"));
        Assertions.assertTrue(secondTaskLogNo.matches("TL[0-9A-F]{16}"));
        Assertions.assertNotEquals(firstTaskLogNo, secondTaskLogNo);
    }

    @Test
    void shouldRollbackFailedAlertDispatchAndContinueWithNextAlert() {
        PaymentIssueAlertDispatchItemDTO first = buildDispatchItem();
        first.setAlertNo("PIA-OUTBOX-FAIL");
        first.setNotifyChannels("IM");
        PaymentIssueAlertDispatchItemDTO second = buildDispatchItem();
        second.setAlertNo("PIA-OUTBOX-NEXT");
        second.setNotifyChannels("IM");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts(100)).thenReturn(Arrays.asList(first, second));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM"))
                .thenReturn(Collections.singletonList(buildProvider(
                        "ALERT_IM_WECOM", "企业微信告警机器人", "wecom-bot-alerts",
                        "TPL_PAYMENT_ISSUE_IM_V1", "DEFAULT", 100)));
        when(imNotifier.send(any(PaymentIssueAlertDispatchItemDTO.class)))
                .thenReturn(buildDeliveryResult("IM-RECEIPT", "ACCEPTED", "企业微信已接单", "[IM] ISSUE-001"));
        when(paymentTaskCenterMapper.updateIssueAlertDeliveryStatus(any(PaymentIssueAlertLogEntity.class)))
                .thenAnswer(invocation -> {
                    PaymentIssueAlertLogEntity update = invocation.getArgument(0);
                    if ("PIA-OUTBOX-FAIL".equals(update.getAlertNo())) {
                        throw new IllegalStateException("source status update failed");
                    }
                    return 1;
                });
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier),
                transactionManager
        ).dispatchPendingAlerts("payment-core-admin");

        Assertions.assertEquals(2, result.getProcessedCount());
        Assertions.assertEquals(1, result.getSuccessCount());
        Assertions.assertEquals(1, result.getFailCount());
        Assertions.assertTrue(result.getSummaryComment().contains("PIA-OUTBOX-FAIL"));
        verify(imNotifier, times(2)).send(any(PaymentIssueAlertDispatchItemDTO.class));
        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void shouldRollbackFailedReceiptReconcileAndContinueWithNextReceipt() {
        String sensitiveMessage = "jdbc:mysql://internal/payment password=receipt-secret\ninvalid SQL";
        PaymentIssueAlertLogEntity first = buildAcceptedLog("PIA-IM-FAIL", "PIA-OUTBOX-FAIL");
        PaymentIssueAlertLogEntity second = buildAcceptedLog("PIA-IM-NEXT", "PIA-OUTBOX-NEXT");
        when(paymentTaskCenterMapper.findAcceptedIssueAlertDeliveryLogs(100)).thenReturn(Arrays.asList(first, second));
        when(paymentTaskCenterMapper.claimIssueAlertReceiptQuery(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                eq("ISSUE-001"), eq(0), eq(true), eq(10), eq(30))).thenReturn(1);
        when(receiptQueryAdapter.supports("IM")).thenReturn(true);
        when(receiptQueryAdapter.query(any(PaymentIssueAlertLogEntity.class))).thenReturn(
                buildDeliveryResult(null, "DELIVERED", "供应商查询确认送达", ""));
        when(paymentTaskCenterMapper.updateIssueAlertProviderReceipt(any(PaymentIssueAlertLogEntity.class)))
                .thenReturn(1);
        when(paymentTaskCenterMapper.updateSourceIssueAlertAcknowledgement(any(PaymentIssueAlertLogEntity.class)))
                .thenAnswer(invocation -> {
                    PaymentIssueAlertLogEntity update = invocation.getArgument(0);
                    if ("PIA-OUTBOX-FAIL".equals(update.getAlertNo())) {
                        throw new IllegalStateException(sensitiveMessage);
                    }
                    return 1;
                });
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);

        Logger transactionLogger = (Logger) org.slf4j.LoggerFactory.getLogger(TransactionTemplate.class);
        ListAppender<ILoggingEvent> transactionAppender = new ListAppender<>();
        transactionAppender.start();
        transactionLogger.addAppender(transactionAppender);
        try {
            PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                    paymentTaskCenterMapper,
                    Arrays.asList(imNotifier, smsNotifier, emailNotifier),
                    Collections.singletonList(receiptQueryAdapter),
                    transactionManager
            ).reconcileDeliveryReceipts("payment-core-admin");

            Assertions.assertEquals(2, result.getProcessedCount());
            Assertions.assertEquals(1, result.getSuccessCount());
            Assertions.assertEquals(1, result.getFailCount());
            Assertions.assertTrue(result.getSummaryComment().contains("PIA-IM-FAIL"));
            verify(paymentTaskCenterMapper, times(2))
                    .updateIssueAlertProviderReceipt(any(PaymentIssueAlertLogEntity.class));
            verify(transactionManager).rollback(transactionStatus);
            verify(transactionManager).commit(transactionStatus);
            Assertions.assertTrue(transactionAppender.list.stream().anyMatch(event ->
                    event.getThrowableProxy() != null
                            && "PAYMENT_ISSUE_ALERT_ITEM_TRANSACTION_FAILED:IllegalStateException"
                            .equals(event.getThrowableProxy().getMessage())
                            && event.getThrowableProxy().getCause() == null
                            && event.getThrowableProxy().getStackTraceElementProxyArray().length == 0));
            Assertions.assertTrue(transactionAppender.list.stream().noneMatch(event ->
                    event.getFormattedMessage().contains("receipt-secret")
                            || (event.getThrowableProxy() != null
                            && String.valueOf(event.getThrowableProxy().getMessage()).contains("receipt-secret"))));
        } finally {
            transactionLogger.detachAppender(transactionAppender);
            transactionAppender.stop();
        }
    }

    private PaymentIssueAlertLogEntity buildAcceptedLog(String alertNo, String sourceAlertNo) {
        PaymentIssueAlertLogEntity acceptedLog = new PaymentIssueAlertLogEntity();
        acceptedLog.setAlertNo(alertNo);
        acceptedLog.setSourceAlertNo(sourceAlertNo);
        acceptedLog.setIssueNo("ISSUE-001");
        acceptedLog.setAlertChannel("IM");
        acceptedLog.setProviderDeliveryStatus("ACCEPTED");
        return acceptedLog;
    }

    private PaymentIssueAlertDeliveryResultDTO buildDeliveryResult(String receiptNo,
                                                                   String deliveryStatus,
                                                                   String deliveryMessage,
                                                                   String renderedContent) {
        return buildDeliveryResult(receiptNo, deliveryStatus, deliveryMessage, renderedContent,
                "LOCAL:TEST:" + deliveryStatus);
    }

    private PaymentIssueAlertDeliveryResultDTO buildDeliveryResult(String receiptNo,
                                                                   String deliveryStatus,
                                                                   String deliveryMessage,
                                                                   String renderedContent,
                                                                   String receiptSnapshot) {
        PaymentIssueAlertDeliveryResultDTO result = new PaymentIssueAlertDeliveryResultDTO();
        result.setProviderReceiptSnapshot(receiptSnapshot);
        result.setProviderReceiptNo(receiptNo);
        result.setProviderDeliveryStatus(deliveryStatus);
        result.setProviderDeliveryMessage(deliveryMessage);
        result.setRenderedContentSnapshot(renderedContent);
        return result;
    }

    private String repeated(String value, int count) {
        return String.join("", Collections.nCopies(count, value));
    }

    private PaymentAlertProviderConfigDTO buildProvider(String providerCode,
                                                        String providerName,
                                                        String endpointAlias,
                                                        String templateCode,
                                                        String routeRule,
                                                        Integer routePriority) {
        return buildProvider(providerCode, providerName, endpointAlias, templateCode, routeRule, routePriority, null, null);
    }

    private PaymentAlertProviderConfigDTO buildProvider(String providerCode,
                                                        String providerName,
                                                        String endpointAlias,
                                                        String templateCode,
                                                        String routeRule,
                                                        Integer routePriority,
                                                        String retryPolicy) {
        return buildProvider(providerCode, providerName, endpointAlias, templateCode, routeRule, routePriority, retryPolicy, null);
    }

    private PaymentAlertProviderConfigDTO buildProvider(String providerCode,
                                                        String providerName,
                                                        String endpointAlias,
                                                        String templateCode,
                                                        String routeRule,
                                                        Integer routePriority,
                                                        String retryPolicy,
                                                        String rateLimitPolicy) {
        PaymentAlertProviderConfigDTO provider = new PaymentAlertProviderConfigDTO();
        provider.setProviderCode(providerCode);
        provider.setProviderName(providerName);
        provider.setEndpointAlias(endpointAlias);
        provider.setTemplateCode(templateCode);
        provider.setTemplateBody("[{{severity}}] {{issueType}} {{issueNo}} {{paymentOrderId}} {{alertContent}}");
        provider.setRouteRule(routeRule);
        provider.setRoutePriority(routePriority);
        provider.setRetryPolicy(retryPolicy);
        provider.setRateLimitPolicy(rateLimitPolicy);
        return provider;
    }

    private PaymentIssueAlertDispatchItemDTO buildDispatchItem() {
        PaymentIssueAlertDispatchItemDTO item = new PaymentIssueAlertDispatchItemDTO();
        item.setAlertNo("PIA-OUTBOX-001");
        item.setIssueNo("ISSUE-001");
        item.setPaymentOrderId("PAY-001");
        item.setIssueType("待回调未收口");
        item.setSeverity("P1");
        item.setResponsibilityGroup("支付后端值班组");
        item.setReceiver("支付后端值班");
        item.setEscalationLevel("L2");
        item.setScheduleTag("交易链路白班");
        item.setAckStatus("待确认");
        item.setAlertContent("支付异常 ISSUE-001 已超过 P1 SLA，请进入异常中心处理。");
        item.setCreatedAt("2099-12-31 23:59:59");
        item.setTriggeredBy("payment-issue-sla-scheduler");
        return item;
    }

    private String leaseOwner(String prefix) {
        return org.mockito.ArgumentMatchers.argThat(
                owner -> owner != null && owner.startsWith(prefix + ":"));
    }
}
