package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentIssueActionRequestDTO;
import com.abc123.hsp.dto.PaymentIssueAlertLogQueryDTO;
import com.abc123.hsp.dto.PaymentIssueAlertLogRowDTO;
import com.abc123.hsp.dto.PaymentIssueQueryDTO;
import com.abc123.hsp.dto.PaymentIssueRowDTO;
import com.abc123.hsp.mapper.PaymentIssueMapper;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付交易异常中心服务测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentIssueServiceImplTest {

    @Mock
    private PaymentIssueMapper paymentIssueMapper;

    @Test
    void shouldTrimFiltersAndNormalizePaging() {
        when(paymentIssueMapper.findAll(any(PaymentIssueQueryDTO.class))).thenReturn(Collections.emptyList());
        when(paymentIssueMapper.count(any(PaymentIssueQueryDTO.class))).thenReturn(0L);

        PaymentIssueQueryDTO query = new PaymentIssueQueryDTO();
        query.setPaymentOrderId("  PAY-001  ");
        query.setOrderNo("  ORD-001  ");
        query.setIssueType("  待回调未收口  ");
        query.setSeverity("  P1  ");
        query.setChannelCode("  alipay_h5  ");
        query.setPaymentMethod("  支付宝  ");
        query.setPageNo(0);
        query.setPageSize(200);

        new PaymentIssueServiceImpl(paymentIssueMapper).list(query);

        ArgumentCaptor<PaymentIssueQueryDTO> captor = ArgumentCaptor.forClass(PaymentIssueQueryDTO.class);
        verify(paymentIssueMapper).findAll(captor.capture());
        PaymentIssueQueryDTO normalized = captor.getValue();
        assertEquals("PAY-001", normalized.getPaymentOrderId());
        assertEquals("ORD-001", normalized.getOrderNo());
        assertEquals("待回调未收口", normalized.getIssueType());
        assertEquals("P1", normalized.getSeverity());
        assertEquals("alipay_h5", normalized.getChannelCode());
        assertEquals("支付宝", normalized.getPaymentMethod());
        assertEquals(1, normalized.getPageNo());
        assertEquals(100, normalized.getPageSize());
    }

    @Test
    void shouldNormalizeFiltersWhenQueryResponsibilitySummary() {
        when(paymentIssueMapper.responsibilitySummary(any(PaymentIssueQueryDTO.class))).thenReturn(Collections.emptyList());

        PaymentIssueQueryDTO query = new PaymentIssueQueryDTO();
        query.setPaymentOrderId(" PAY-002 ");
        query.setIssueType(" P1异常 ");
        query.setPageNo(-1);
        query.setPageSize(0);

        new PaymentIssueServiceImpl(paymentIssueMapper).responsibilitySummary(query);

        ArgumentCaptor<PaymentIssueQueryDTO> captor = ArgumentCaptor.forClass(PaymentIssueQueryDTO.class);
        verify(paymentIssueMapper).responsibilitySummary(captor.capture());
        PaymentIssueQueryDTO normalized = captor.getValue();
        assertEquals("PAY-002", normalized.getPaymentOrderId());
        assertEquals("P1异常", normalized.getIssueType());
        assertEquals(1, normalized.getPageNo());
        assertEquals(1, normalized.getPageSize());
    }

    @Test
    void shouldTrimAndNormalizeAlertLogQuery() {
        when(paymentIssueMapper.findAlertLogs(any(PaymentIssueAlertLogQueryDTO.class))).thenReturn(Collections.emptyList());
        when(paymentIssueMapper.countAlertLogs(any(PaymentIssueAlertLogQueryDTO.class))).thenReturn(0L);

        PaymentIssueAlertLogQueryDTO query = new PaymentIssueAlertLogQueryDTO();
        query.setAlertNo("  PIA-001  ");
        query.setIssueNo("  ISSUE-001  ");
        query.setPaymentOrderId("  PAY-001  ");
        query.setAlertChannel("  IM  ");
        query.setAlertStatus("  已派发  ");
        query.setAckStatus("  待确认  ");
        query.setProviderDeliveryStatus("  ACCEPTED  ");
        query.setPageNo(0);
        query.setPageSize(200);

        new PaymentIssueServiceImpl(paymentIssueMapper).listAlertLogs(query);

        ArgumentCaptor<PaymentIssueAlertLogQueryDTO> captor = ArgumentCaptor.forClass(PaymentIssueAlertLogQueryDTO.class);
        verify(paymentIssueMapper).findAlertLogs(captor.capture());
        PaymentIssueAlertLogQueryDTO normalized = captor.getValue();
        assertEquals("PIA-001", normalized.getAlertNo());
        assertEquals("ISSUE-001", normalized.getIssueNo());
        assertEquals("PAY-001", normalized.getPaymentOrderId());
        assertEquals("IM", normalized.getAlertChannel());
        assertEquals("已派发", normalized.getAlertStatus());
        assertEquals("待确认", normalized.getAckStatus());
        assertEquals("ACCEPTED", normalized.getProviderDeliveryStatus());
        assertEquals(1, normalized.getPageNo());
        assertEquals(100, normalized.getPageSize());
    }

    @Test
    void shouldReturnAlertLogPageResult() {
        PaymentIssueAlertLogRowDTO row = new PaymentIssueAlertLogRowDTO();
        row.setAlertNo("PIA-002");
        when(paymentIssueMapper.findAlertLogs(any(PaymentIssueAlertLogQueryDTO.class))).thenReturn(Collections.singletonList(row));
        when(paymentIssueMapper.countAlertLogs(any(PaymentIssueAlertLogQueryDTO.class))).thenReturn(1L);

        PaymentIssueAlertLogQueryDTO query = new PaymentIssueAlertLogQueryDTO();
        query.setPageNo(2);
        query.setPageSize(10);

        assertEquals(1, new PaymentIssueServiceImpl(paymentIssueMapper).listAlertLogs(query).getItems().size());
    }

    @Test
    void shouldBatchAssignIssueAndWriteActionLog() {
        PaymentIssueRowDTO issue = new PaymentIssueRowDTO();
        issue.setIssueNo("ISSUE-WAIT-PAY-001");
        issue.setPaymentOrderId("PAY-001");
        issue.setIssueType("待回调未收口");
        when(paymentIssueMapper.findByIssueNo("ISSUE-WAIT-PAY-001")).thenReturn(issue);
        when(paymentIssueMapper.findAll(any(PaymentIssueQueryDTO.class))).thenReturn(Collections.emptyList());
        when(paymentIssueMapper.count(any(PaymentIssueQueryDTO.class))).thenReturn(0L);

        PaymentIssueActionRequestDTO request = new PaymentIssueActionRequestDTO();
        request.setIssueNos(Arrays.asList("ISSUE-WAIT-PAY-001"));
        request.setActionType("分派处理人");
        request.setAssignee("后端值班");
        request.setOperator("支付运营");
        request.setRemark("请先主动查单并核对回调");

        new PaymentIssueServiceImpl(paymentIssueMapper).batchAction(request);

        verify(paymentIssueMapper).insertActionLog(
                anyString(),
                org.mockito.ArgumentMatchers.eq("ISSUE-WAIT-PAY-001"),
                org.mockito.ArgumentMatchers.eq("PAY-001"),
                org.mockito.ArgumentMatchers.eq("待回调未收口"),
                org.mockito.ArgumentMatchers.eq("分派处理人"),
                org.mockito.ArgumentMatchers.eq("后端值班"),
                org.mockito.ArgumentMatchers.eq("已分派"),
                org.mockito.ArgumentMatchers.eq("info"),
                org.mockito.ArgumentMatchers.eq("请先主动查单并核对回调"),
                org.mockito.ArgumentMatchers.eq("支付运营"));
    }

    @Test
    void shouldAcknowledgeAlertWhenMarkedProcessed() {
        PaymentIssueRowDTO issue = new PaymentIssueRowDTO();
        issue.setIssueNo("ISSUE-WAIT-PAY-002");
        issue.setPaymentOrderId("PAY-002");
        issue.setIssueType("待回调未收口");
        when(paymentIssueMapper.findByIssueNo("ISSUE-WAIT-PAY-002")).thenReturn(issue);
        when(paymentIssueMapper.findAll(any(PaymentIssueQueryDTO.class))).thenReturn(Collections.emptyList());
        when(paymentIssueMapper.count(any(PaymentIssueQueryDTO.class))).thenReturn(0L);
        when(paymentIssueMapper.acknowledgePendingAlerts("ISSUE-WAIT-PAY-002", "支付运营")).thenReturn(1);

        PaymentIssueActionRequestDTO request = new PaymentIssueActionRequestDTO();
        request.setIssueNos(Collections.singletonList("ISSUE-WAIT-PAY-002"));
        request.setActionType("标记已处理");
        request.setAssignee("后端值班");
        request.setOperator("支付运营");
        request.setRemark("异常已收口");

        new PaymentIssueServiceImpl(paymentIssueMapper).batchAction(request);

        verify(paymentIssueMapper).acknowledgePendingAlerts("ISSUE-WAIT-PAY-002", "支付运营");
    }

    @Test
    void shouldRejectUnsupportedIssueAction() {
        PaymentIssueActionRequestDTO request = new PaymentIssueActionRequestDTO();
        request.setIssueNos(Arrays.asList("ISSUE-WAIT-PAY-001"));
        request.setActionType("未知动作");
        request.setAssignee("后端值班");

        assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentIssueServiceImpl(paymentIssueMapper).batchAction(request)
        );
    }
}
