package com.abc123.hsp.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentAlertProviderConfigDTO;
import com.abc123.hsp.dto.PaymentIssueAlertDeliveryResultDTO;
import com.abc123.hsp.dto.PaymentIssueAlertDispatchItemDTO;
import com.abc123.hsp.dto.PaymentTaskActionResultDTO;
import com.abc123.hsp.entity.PaymentIssueAlertLogEntity;
import com.abc123.hsp.entity.PaymentTaskRunLogEntity;
import com.abc123.hsp.mapper.PaymentTaskCenterMapper;
import com.abc123.hsp.service.PaymentIssueAlertNotifier;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Test
    void shouldDispatchAllChannelsSuccessfully() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM,SMS,EMAIL");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts()).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("ISSUE-001", "IM")).thenReturn(false);
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("ISSUE-001", "SMS")).thenReturn(false);
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("ISSUE-001", "EMAIL")).thenReturn(false);
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
        ).dispatchPendingAlerts();

        ArgumentCaptor<PaymentIssueAlertLogEntity> deliveryLogCaptor = ArgumentCaptor.forClass(PaymentIssueAlertLogEntity.class);
        verify(paymentTaskCenterMapper, org.mockito.Mockito.times(3)).insertIssueAlertLog(deliveryLogCaptor.capture());
        verify(paymentTaskCenterMapper).updateIssueAlertDeliveryStatus(any(PaymentIssueAlertLogEntity.class));
        verify(paymentTaskCenterMapper).insertTaskRunLog(any(PaymentTaskRunLogEntity.class));
        Assertions.assertEquals(1, result.getSuccessCount());
        Assertions.assertEquals(0, result.getWarningCount());
        Assertions.assertEquals(0, result.getFailCount());
        Assertions.assertTrue(deliveryLogCaptor.getAllValues().stream().allMatch(log -> "已派发".equals(log.getAlertStatus())));
        Assertions.assertTrue(deliveryLogCaptor.getAllValues().stream().anyMatch(log -> log.getAlertContent().contains("支付异常 ISSUE-001")));
        Assertions.assertTrue(deliveryLogCaptor.getAllValues().stream().anyMatch(log -> "企业微信告警机器人-P1".equals(log.getProviderName())));
        Assertions.assertTrue(deliveryLogCaptor.getAllValues().stream().anyMatch(log -> "TPL_PAYMENT_ISSUE_IM_P1_V1".equals(log.getTemplateCode())));
        Assertions.assertTrue(deliveryLogCaptor.getAllValues().stream().anyMatch(log -> "ACCEPTED".equals(log.getProviderDeliveryStatus())));
        Assertions.assertTrue(deliveryLogCaptor.getAllValues().stream().anyMatch(log -> "IM-RECEIPT-001".equals(log.getProviderReceiptNo())));
    }

    @Test
    void shouldMarkPartialFailureWhenOneChannelFails() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM,SMS,EMAIL");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts()).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("ISSUE-001", "IM")).thenReturn(false);
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("ISSUE-001", "SMS")).thenReturn(false);
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("ISSUE-001", "EMAIL")).thenReturn(false);
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
        PaymentTaskActionResultDTO result = service.dispatchPendingAlerts();

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
        when(paymentTaskCenterMapper.findPendingOutboxAlerts()).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("ISSUE-001", "IM")).thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM")).thenReturn(Collections.singletonList(buildProvider("ALERT_IM_WECOM", "企业微信告警机器人", "wecom-bot-alerts", "TPL_PAYMENT_ISSUE_IM_V1", "DEFAULT", 100)));
        when(imNotifier.send(any(PaymentIssueAlertDispatchItemDTO.class))).thenReturn(buildDeliveryResult("IM-RECEIPT-001", "ACCEPTED", "企业微信已接单", "[IM] ISSUE-001"));

        new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts();

        verify(imNotifier).send(any(PaymentIssueAlertDispatchItemDTO.class));
        verify(smsNotifier, org.mockito.Mockito.never()).send(any(PaymentIssueAlertDispatchItemDTO.class));
        verify(emailNotifier, org.mockito.Mockito.never()).send(any(PaymentIssueAlertDispatchItemDTO.class));
    }

    @Test
    void shouldMarkUnsupportedChannelAsFailure() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM,VOICE");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts()).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("ISSUE-001", "IM")).thenReturn(false);
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("ISSUE-001", "VOICE")).thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM")).thenReturn(Collections.singletonList(buildProvider("ALERT_IM_WECOM", "企业微信告警机器人", "wecom-bot-alerts", "TPL_PAYMENT_ISSUE_IM_V1", "DEFAULT", 100)));
        when(imNotifier.send(any(PaymentIssueAlertDispatchItemDTO.class))).thenReturn(buildDeliveryResult("IM-RECEIPT-001", "ACCEPTED", "企业微信已接单", "[IM] ISSUE-001"));
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("VOICE")).thenReturn(Collections.emptyList());

        PaymentIssueAlertDeliveryServiceImpl service = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        );
        PaymentTaskActionResultDTO result = service.dispatchPendingAlerts();

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
        when(paymentTaskCenterMapper.findPendingOutboxAlerts()).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("ISSUE-001", "IM")).thenReturn(true);
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("ISSUE-001", "SMS")).thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("SMS")).thenReturn(Collections.singletonList(buildProvider("ALERT_SMS_TENCENT", "腾讯云短信告警", "tencent-sms-alerts", "TPL_PAYMENT_ISSUE_SMS_V1", "DEFAULT", 100)));
        when(smsNotifier.send(any(PaymentIssueAlertDispatchItemDTO.class))).thenReturn(buildDeliveryResult("SMS-RECEIPT-001", "ACCEPTED", "短信供应商已接单", "[SMS] ISSUE-001"));

        new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts();

        verify(imNotifier, org.mockito.Mockito.never()).send(any(PaymentIssueAlertDispatchItemDTO.class));
        verify(smsNotifier).send(any(PaymentIssueAlertDispatchItemDTO.class));
    }

    @Test
    void shouldMarkFailureWhenProviderConfigMissingForChannel() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts()).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("ISSUE-001", "IM")).thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM")).thenReturn(Collections.emptyList());

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts();

        verify(imNotifier, org.mockito.Mockito.never()).send(any(PaymentIssueAlertDispatchItemDTO.class));
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(0, result.getWarningCount());
        Assertions.assertEquals(1, result.getFailCount());
    }

    @Test
    void shouldSkipRetryWhenFailureCountExceedsConfiguredLimit() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts()).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("ISSUE-001", "IM")).thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM")).thenReturn(Collections.singletonList(
                buildProvider("ALERT_IM_WECOM", "企业微信告警机器人", "wecom-bot-alerts", "TPL_PAYMENT_ISSUE_IM_V1", "DEFAULT", 100, "失败重试1次/间隔5分钟")
        ));
        when(paymentTaskCenterMapper.countFailedIssueAlertChannelDeliveries("ISSUE-001", "IM")).thenReturn(2);

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts();

        verify(imNotifier, org.mockito.Mockito.never()).send(any(PaymentIssueAlertDispatchItemDTO.class));
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(0, result.getWarningCount());
        Assertions.assertEquals(1, result.getFailCount());
    }

    @Test
    void shouldSkipRetryWhenCooldownWindowNotElapsed() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts()).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("ISSUE-001", "IM")).thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM")).thenReturn(Collections.singletonList(
                buildProvider("ALERT_IM_WECOM", "企业微信告警机器人", "wecom-bot-alerts", "TPL_PAYMENT_ISSUE_IM_V1", "DEFAULT", 100, "失败重试2次/间隔5分钟")
        ));
        when(paymentTaskCenterMapper.countFailedIssueAlertChannelDeliveries("ISSUE-001", "IM")).thenReturn(1);
        PaymentIssueAlertLogEntity latestLog = new PaymentIssueAlertLogEntity();
        latestLog.setAlertStatus("派发失败");
        latestLog.setCreatedAt("2026-07-25 23:59:59");
        when(paymentTaskCenterMapper.findLatestIssueAlertChannelDeliveryLog("ISSUE-001", "IM")).thenReturn(latestLog);

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts();

        verify(imNotifier, org.mockito.Mockito.never()).send(any(PaymentIssueAlertDispatchItemDTO.class));
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(0, result.getWarningCount());
        Assertions.assertEquals(1, result.getFailCount());
    }

    @Test
    void shouldBlockDispatchWhenProviderRateLimitReached() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts()).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("ISSUE-001", "IM")).thenReturn(false);
        when(paymentTaskCenterMapper.findEnabledAlertProvidersByChannel("IM")).thenReturn(Collections.singletonList(
                buildProvider("ALERT_IM_WECOM", "企业微信告警机器人", "wecom-bot-alerts", "TPL_PAYMENT_ISSUE_IM_V1", "DEFAULT", 100, null, "每分钟 1 条")
        ));
        when(paymentTaskCenterMapper.countIssueAlertProviderDeliveriesSince(org.mockito.ArgumentMatchers.eq("ALERT_IM_WECOM"), org.mockito.ArgumentMatchers.eq("IM"), any(String.class))).thenReturn(1);

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts();

        verify(imNotifier, org.mockito.Mockito.never()).send(any(PaymentIssueAlertDispatchItemDTO.class));
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(0, result.getWarningCount());
        Assertions.assertEquals(1, result.getFailCount());
    }

    @Test
    void shouldFallbackToNextProviderWhenPrimaryProviderFails() {
        PaymentIssueAlertDispatchItemDTO item = buildDispatchItem();
        item.setNotifyChannels("IN_APP,IM");
        when(paymentTaskCenterMapper.findPendingOutboxAlerts()).thenReturn(Collections.singletonList(item));
        when(imNotifier.channelCode()).thenReturn("IM");
        when(smsNotifier.channelCode()).thenReturn("SMS");
        when(emailNotifier.channelCode()).thenReturn("EMAIL");
        when(paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery("ISSUE-001", "IM")).thenReturn(false);
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
        ).dispatchPendingAlerts();

        ArgumentCaptor<PaymentIssueAlertLogEntity> logCaptor = ArgumentCaptor.forClass(PaymentIssueAlertLogEntity.class);
        verify(paymentTaskCenterMapper, org.mockito.Mockito.times(2)).insertIssueAlertLog(logCaptor.capture());
        Assertions.assertEquals(1, result.getSuccessCount());
        Assertions.assertEquals(0, result.getWarningCount());
        Assertions.assertEquals(0, result.getFailCount());
        Assertions.assertTrue(logCaptor.getAllValues().stream().anyMatch(log ->
                "企业微信告警机器人-主".equals(log.getProviderName()) && "派发失败".equals(log.getAlertStatus())));
        Assertions.assertTrue(logCaptor.getAllValues().stream().anyMatch(log ->
                "企业微信告警机器人-备".equals(log.getProviderName()) && "已派发".equals(log.getAlertStatus())));
    }

    @Test
    void shouldReconcileAcceptedDeliveryReceiptsSuccessfully() {
        PaymentIssueAlertLogEntity acceptedLog = new PaymentIssueAlertLogEntity();
        acceptedLog.setAlertNo("PIA-IM-001");
        acceptedLog.setIssueNo("ISSUE-001");
        acceptedLog.setAlertChannel("IM");
        acceptedLog.setProviderDeliveryStatus("ACCEPTED");
        when(paymentTaskCenterMapper.findAcceptedIssueAlertDeliveryLogs()).thenReturn(Collections.singletonList(acceptedLog));
        when(paymentTaskCenterMapper.updateIssueAlertProviderReceipt(any(PaymentIssueAlertLogEntity.class))).thenReturn(1);

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).reconcileDeliveryReceipts();

        ArgumentCaptor<PaymentIssueAlertLogEntity> captor = ArgumentCaptor.forClass(PaymentIssueAlertLogEntity.class);
        verify(paymentTaskCenterMapper).updateIssueAlertProviderReceipt(captor.capture());
        verify(paymentTaskCenterMapper).insertTaskRunLog(any(PaymentTaskRunLogEntity.class));
        Assertions.assertEquals("DELIVERED", captor.getValue().getProviderDeliveryStatus());
        Assertions.assertEquals("已确认", captor.getValue().getAckStatus());
        Assertions.assertEquals(1, result.getProcessedCount());
        Assertions.assertEquals(1, result.getSuccessCount());
        Assertions.assertEquals(0, result.getFailCount());
    }

    @Test
    void shouldReturnZeroWhenNoAcceptedReceiptsNeedReconcile() {
        when(paymentTaskCenterMapper.findAcceptedIssueAlertDeliveryLogs()).thenReturn(Collections.emptyList());

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).reconcileDeliveryReceipts();

        verify(paymentTaskCenterMapper).insertTaskRunLog(any(PaymentTaskRunLogEntity.class));
        Assertions.assertEquals(0, result.getProcessedCount());
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(0, result.getFailCount());
    }

    private PaymentIssueAlertDeliveryResultDTO buildDeliveryResult(String receiptNo,
                                                                   String deliveryStatus,
                                                                   String deliveryMessage,
                                                                   String renderedContent) {
        PaymentIssueAlertDeliveryResultDTO result = new PaymentIssueAlertDeliveryResultDTO();
        result.setProviderReceiptNo(receiptNo);
        result.setProviderDeliveryStatus(deliveryStatus);
        result.setProviderDeliveryMessage(deliveryMessage);
        result.setRenderedContentSnapshot(renderedContent);
        return result;
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
        item.setAlertContent("支付异常 ISSUE-001 已超过 P1 SLA，请进入异常中心处理。");
        item.setTriggeredBy("payment-issue-sla-scheduler");
        return item;
    }
}
