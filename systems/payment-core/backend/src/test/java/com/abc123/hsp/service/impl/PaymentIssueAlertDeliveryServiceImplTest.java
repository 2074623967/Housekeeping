package com.abc123.hsp.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        when(paymentTaskCenterMapper.hasEnabledAlertProviderForChannel("IM")).thenReturn(true);
        when(paymentTaskCenterMapper.hasEnabledAlertProviderForChannel("SMS")).thenReturn(true);
        when(paymentTaskCenterMapper.hasEnabledAlertProviderForChannel("EMAIL")).thenReturn(true);

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
        when(paymentTaskCenterMapper.hasEnabledAlertProviderForChannel("IM")).thenReturn(true);
        when(paymentTaskCenterMapper.hasEnabledAlertProviderForChannel("SMS")).thenReturn(true);
        when(paymentTaskCenterMapper.hasEnabledAlertProviderForChannel("EMAIL")).thenReturn(true);
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
        when(paymentTaskCenterMapper.hasEnabledAlertProviderForChannel("IM")).thenReturn(true);

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
        when(paymentTaskCenterMapper.hasEnabledAlertProviderForChannel("IM")).thenReturn(true);
        when(paymentTaskCenterMapper.hasEnabledAlertProviderForChannel("VOICE")).thenReturn(false);

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
        when(paymentTaskCenterMapper.hasEnabledAlertProviderForChannel("SMS")).thenReturn(true);

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
        when(paymentTaskCenterMapper.hasEnabledAlertProviderForChannel("IM")).thenReturn(false);

        PaymentTaskActionResultDTO result = new PaymentIssueAlertDeliveryServiceImpl(
                paymentTaskCenterMapper,
                Arrays.asList(imNotifier, smsNotifier, emailNotifier)
        ).dispatchPendingAlerts();

        verify(imNotifier, org.mockito.Mockito.never()).send(any(PaymentIssueAlertDispatchItemDTO.class));
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(0, result.getWarningCount());
        Assertions.assertEquals(1, result.getFailCount());
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
