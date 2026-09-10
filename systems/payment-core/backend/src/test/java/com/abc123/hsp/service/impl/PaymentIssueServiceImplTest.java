package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentIssueActionRequestDTO;
import com.abc123.hsp.dto.PaymentIssueAlertAcknowledgeRequestDTO;
import com.abc123.hsp.dto.PaymentIssueAlertLogQueryDTO;
import com.abc123.hsp.dto.PaymentIssueAlertLogRowDTO;
import com.abc123.hsp.dto.PaymentIssueQueryDTO;
import com.abc123.hsp.dto.PaymentIssueRowDTO;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.mapper.PaymentIssueMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
    void shouldMaskPaymentIssueListWithoutChangingMapperDto() {
        PaymentIssueRowDTO row = new PaymentIssueRowDTO();
        row.setIssueNo("ISSUE-001");
        row.setCustomerName("张女士");
        when(paymentIssueMapper.findAll(any(PaymentIssueQueryDTO.class)))
                .thenReturn(Collections.singletonList(row));
        when(paymentIssueMapper.count(any(PaymentIssueQueryDTO.class))).thenReturn(1L);

        PageResultDTO<PaymentIssueRowDTO> result =
                new PaymentIssueServiceImpl(paymentIssueMapper).list(new PaymentIssueQueryDTO());

        assertEquals("张*", result.getItems().get(0).getCustomerName());
        assertEquals("张女士", row.getCustomerName());
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
        query.setReceiptQueryState("  exhausted  ");
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
        assertEquals("EXHAUSTED", normalized.getReceiptQueryState());
        assertEquals(60, normalized.getReceiptQueryMinAgeSeconds());
        assertEquals(10, normalized.getReceiptQueryMaxAttempts());
        assertEquals(30, normalized.getReceiptQueryClaimTimeoutSeconds());
        assertEquals(1, normalized.getPageNo());
        assertEquals(100, normalized.getPageSize());
    }

    @Test
    void shouldRejectUnknownReceiptQueryState() {
        PaymentIssueAlertLogQueryDTO query = new PaymentIssueAlertLogQueryDTO();
        query.setReceiptQueryState("UNKNOWN");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentIssueServiceImpl(paymentIssueMapper).listAlertLogs(query));

        assertTrue(exception.getMessage().contains("不支持的回执回查状态"));
    }

    @Test
    void shouldReturnAlertLogPageResult() {
        PaymentIssueAlertLogRowDTO row = new PaymentIssueAlertLogRowDTO();
        row.setAlertNo("PIA-002");
        row.setProviderReceiptSnapshot(
                "HTTP_RESPONSE:{\"result\":{\"phone\":\"13800138000\",\"customerName\":\"张女士\","
                        + "\"bankCardNo\":\"6222021234567890\"}}");
        when(paymentIssueMapper.findAlertLogs(any(PaymentIssueAlertLogQueryDTO.class))).thenReturn(Collections.singletonList(row));
        when(paymentIssueMapper.countAlertLogs(any(PaymentIssueAlertLogQueryDTO.class))).thenReturn(1L);

        PaymentIssueAlertLogQueryDTO query = new PaymentIssueAlertLogQueryDTO();
        query.setPageNo(2);
        query.setPageSize(10);

        PaymentIssueAlertLogRowDTO result = new PaymentIssueServiceImpl(paymentIssueMapper)
                .listAlertLogs(query).getItems().get(0);

        assertTrue(result.getProviderReceiptSnapshot().startsWith("HTTP_RESPONSE:"));
        assertTrue(result.getProviderReceiptSnapshot().contains("138****8000"));
        assertTrue(result.getProviderReceiptSnapshot().contains("张*"));
        assertTrue(result.getProviderReceiptSnapshot().contains("622202******7890"));
        assertTrue(row.getProviderReceiptSnapshot().contains("13800138000"));
    }

    @Test
    void shouldExportAlertLogsCsv() {
        PaymentIssueAlertLogRowDTO row = new PaymentIssueAlertLogRowDTO();
        row.setAlertNo("PIA-EXPORT-001");
        row.setIssueNo("ISSUE-EXPORT-001");
        row.setPaymentOrderId("PAY-EXPORT-001");
        row.setIssueType("待回调未收口");
        row.setSeverity("P1");
        row.setResponsibilityGroup("支付后端值班组");
        row.setAlertChannel("IM");
        row.setReceiver("支付运营");
        row.setAlertStatus("已派发");
        row.setAckStatus("待确认");
        row.setProviderCode("im_provider");
        row.setProviderName("企业IM");
        row.setEndpointAlias("主Webhook");
        row.setTemplateCode("PAYMENT_ISSUE_NOTICE");
        row.setProviderDeliveryStatus("ACCEPTED");
        row.setProviderDeliveryMessage("accepted");
        row.setProviderReceiptNo("receipt-001");
        row.setReceiptQueryState("BACKING_OFF");
        row.setReceiptQueryCount(3);
        row.setLastReceiptQueryAt("2026-08-07 09:45:00");
        row.setNextReceiptQueryAt("2026-08-07 09:49:00");
        row.setReceiptQueryResult("ACCEPTED");
        row.setAckOperator("支付运营");
        row.setAckAt("2026-08-07 10:00:00");
        row.setTriggeredBy("任务中心");
        row.setCreatedAt("2026-08-07 09:30:00");
        row.setAlertContent("支付异常告警内容");
        row.setProviderReceiptSnapshot("HTTP 502: upstream body contains 13800138000");
        when(paymentIssueMapper.findAlertLogs(any(PaymentIssueAlertLogQueryDTO.class))).thenReturn(Collections.singletonList(row));

        String csv = new PaymentIssueServiceImpl(paymentIssueMapper).exportAlertLogsCsv(new PaymentIssueAlertLogQueryDTO());

        assertTrue(csv.contains("PIA-EXPORT-001"));
        assertTrue(csv.contains("支付异常告警内容"));
        assertTrue(csv.contains("BACKING_OFF,3,2026-08-07 09:45:00,2026-08-07 09:49:00,ACCEPTED"));
        assertTrue(csv.contains("[REDACTED]"));
        assertFalse(csv.contains("13800138000"));
        assertEquals("HTTP 502: upstream body contains 13800138000", row.getProviderReceiptSnapshot());
        verify(paymentIssueMapper).findAlertLogs(any(PaymentIssueAlertLogQueryDTO.class));
    }

    @Test
    void shouldAcknowledgeAlertByAlertNo() {
        PaymentIssueAlertLogRowDTO pendingRow = new PaymentIssueAlertLogRowDTO();
        pendingRow.setAlertNo("PIA-ACK-001");
        pendingRow.setSourceAlertNo("PIA-OUTBOX-001");
        pendingRow.setAckStatus("待确认");
        PaymentIssueAlertLogRowDTO acknowledgedRow = new PaymentIssueAlertLogRowDTO();
        acknowledgedRow.setAlertNo("PIA-ACK-001");
        acknowledgedRow.setAckStatus("已确认");
        acknowledgedRow.setAckOperator("支付运营");
        acknowledgedRow.setProviderReceiptSnapshot("{\"phone\":\"13800138000\"}");
        when(paymentIssueMapper.findAlertLogByAlertNo("PIA-ACK-001")).thenReturn(pendingRow, acknowledgedRow);
        when(paymentIssueMapper.acknowledgeAlertByAlertNo("PIA-ACK-001", "支付运营")).thenReturn(1);

        PaymentIssueAlertAcknowledgeRequestDTO request = new PaymentIssueAlertAcknowledgeRequestDTO();
        request.setOperator(" 支付运营 ");

        PaymentIssueAlertLogRowDTO result = new PaymentIssueServiceImpl(paymentIssueMapper)
                .acknowledgeAlert(" PIA-ACK-001 ", request);

        assertEquals("已确认", result.getAckStatus());
        assertEquals("支付运营", result.getAckOperator());
        assertTrue(result.getProviderReceiptSnapshot().contains("138****8000"));
        assertTrue(acknowledgedRow.getProviderReceiptSnapshot().contains("13800138000"));
        verify(paymentIssueMapper).acknowledgeAlertByAlertNo("PIA-ACK-001", "支付运营");
        verify(paymentIssueMapper).acknowledgeAlertByAlertNo("PIA-OUTBOX-001", "支付运营");
    }

    @Test
    void shouldRejectInvalidAcknowledgeOperatorBeforeReadingAlert() {
        PaymentIssueAlertAcknowledgeRequestDTO blankRequest = new PaymentIssueAlertAcknowledgeRequestDTO();
        blankRequest.setOperator("   ");

        assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentIssueServiceImpl(paymentIssueMapper)
                        .acknowledgeAlert("PIA-ACK-001", blankRequest));

        PaymentIssueAlertAcknowledgeRequestDTO longRequest = new PaymentIssueAlertAcknowledgeRequestDTO();
        longRequest.setOperator(String.join("", Collections.nCopies(65, "a")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentIssueServiceImpl(paymentIssueMapper)
                        .acknowledgeAlert("PIA-ACK-001", longRequest));
        PaymentIssueAlertAcknowledgeRequestDTO controlRequest = new PaymentIssueAlertAcknowledgeRequestDTO();
        controlRequest.setOperator("支付运营\n");
        assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentIssueServiceImpl(paymentIssueMapper)
                        .acknowledgeAlert("PIA-ACK-001", controlRequest));
        verifyNoInteractions(paymentIssueMapper);
    }

    @Test
    void shouldMaskAlreadyAcknowledgedAlertWithoutChangingMapperDto() {
        PaymentIssueAlertLogRowDTO acknowledgedRow = new PaymentIssueAlertLogRowDTO();
        acknowledgedRow.setAlertNo("PIA-ACKED-001");
        acknowledgedRow.setAckStatus("已确认");
        acknowledgedRow.setProviderReceiptSnapshot("provider response: 13800138000");
        when(paymentIssueMapper.findAlertLogByAlertNo("PIA-ACKED-001")).thenReturn(acknowledgedRow);
        PaymentIssueAlertAcknowledgeRequestDTO request = new PaymentIssueAlertAcknowledgeRequestDTO();
        request.setOperator(" 支付运营 ");

        PaymentIssueAlertLogRowDTO result = new PaymentIssueServiceImpl(paymentIssueMapper)
                .acknowledgeAlert("PIA-ACKED-001", request);

        assertEquals("[REDACTED]", result.getProviderReceiptSnapshot());
        assertEquals("provider response: 13800138000", acknowledgedRow.getProviderReceiptSnapshot());
    }

    @Test
    void shouldRejectAcknowledgeWhenAlertDoesNotNeedAck() {
        PaymentIssueAlertLogRowDTO noAckRow = new PaymentIssueAlertLogRowDTO();
        noAckRow.setAlertNo("PIA-NOACK-001");
        noAckRow.setAckStatus("无需回执");
        when(paymentIssueMapper.findAlertLogByAlertNo("PIA-NOACK-001")).thenReturn(noAckRow);

        PaymentIssueAlertAcknowledgeRequestDTO request = new PaymentIssueAlertAcknowledgeRequestDTO();
        request.setOperator(" 支付运营 ");

        assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentIssueServiceImpl(paymentIssueMapper).acknowledgeAlert("PIA-NOACK-001", request)
        );
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
        request.setOperator(" 支付运营 ");
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
        request.setOperator("支付运营");

        assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentIssueServiceImpl(paymentIssueMapper).batchAction(request)
        );
    }

    @Test
    void shouldRejectMissingBatchActionOperatorBeforeReadingIssues() {
        PaymentIssueActionRequestDTO request = new PaymentIssueActionRequestDTO();
        request.setIssueNos(Collections.singletonList("ISSUE-WAIT-PAY-001"));
        request.setActionType("分派处理人");
        request.setAssignee("后端值班");
        request.setOperator("   ");

        assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentIssueServiceImpl(paymentIssueMapper).batchAction(request));
        verifyNoInteractions(paymentIssueMapper);
    }

    @Test
    void shouldRejectOverlongBatchActionOperatorBeforeReadingIssues() {
        PaymentIssueActionRequestDTO request = new PaymentIssueActionRequestDTO();
        request.setIssueNos(Collections.singletonList("ISSUE-WAIT-PAY-001"));
        request.setActionType("分派处理人");
        request.setAssignee("后端值班");
        request.setOperator(String.join("", Collections.nCopies(65, "a")));

        assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentIssueServiceImpl(paymentIssueMapper).batchAction(request));
        verifyNoInteractions(paymentIssueMapper);
    }

    @Test
    void shouldRejectOversizedBatchBeforeReadingIssues() {
        PaymentIssueActionRequestDTO request = validBatchActionRequest();
        request.setIssueNos(IntStream.rangeClosed(1, 101)
                .mapToObj(index -> "ISSUE-" + index)
                .collect(Collectors.toList()));

        assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentIssueServiceImpl(paymentIssueMapper).batchAction(request));
        verifyNoInteractions(paymentIssueMapper);
    }

    @Test
    void shouldRejectInvalidBatchFieldsBeforeReadingIssues() {
        PaymentIssueActionRequestDTO overlongIssueNo = validBatchActionRequest();
        overlongIssueNo.setIssueNos(Arrays.asList("ISSUE-001", String.join("", Collections.nCopies(129, "a"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentIssueServiceImpl(paymentIssueMapper).batchAction(overlongIssueNo));

        PaymentIssueActionRequestDTO duplicateIssueNo = validBatchActionRequest();
        duplicateIssueNo.setIssueNos(Arrays.asList(" ISSUE-001 ", "ISSUE-001"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentIssueServiceImpl(paymentIssueMapper).batchAction(duplicateIssueNo));

        PaymentIssueActionRequestDTO controlAssignee = validBatchActionRequest();
        controlAssignee.setAssignee("支付\u0085运营");
        assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentIssueServiceImpl(paymentIssueMapper).batchAction(controlAssignee));

        PaymentIssueActionRequestDTO overlongRemark = validBatchActionRequest();
        overlongRemark.setRemark(String.join("", Collections.nCopies(256, "a")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentIssueServiceImpl(paymentIssueMapper).batchAction(overlongRemark));
        verifyNoInteractions(paymentIssueMapper);
    }

    @Test
    void shouldUseDefaultRemarkAndNormalizedBatchFields() {
        PaymentIssueRowDTO issue = new PaymentIssueRowDTO();
        issue.setIssueNo("ISSUE-001");
        issue.setPaymentOrderId("PAY-001");
        issue.setIssueType("待回调未收口");
        when(paymentIssueMapper.findByIssueNo("ISSUE-001")).thenReturn(issue);
        when(paymentIssueMapper.findAll(any(PaymentIssueQueryDTO.class))).thenReturn(Collections.emptyList());
        when(paymentIssueMapper.count(any(PaymentIssueQueryDTO.class))).thenReturn(0L);
        PaymentIssueActionRequestDTO request = validBatchActionRequest();
        request.setIssueNos(Collections.singletonList(" ISSUE-001 "));
        request.setAssignee(" 后端值班 ");
        request.setRemark("   ");

        new PaymentIssueServiceImpl(paymentIssueMapper).batchAction(request);

        verify(paymentIssueMapper).insertActionLog(
                anyString(),
                org.mockito.ArgumentMatchers.eq("ISSUE-001"),
                org.mockito.ArgumentMatchers.eq("PAY-001"),
                org.mockito.ArgumentMatchers.eq("待回调未收口"),
                org.mockito.ArgumentMatchers.eq("分派处理人"),
                org.mockito.ArgumentMatchers.eq("后端值班"),
                org.mockito.ArgumentMatchers.eq("已分派"),
                org.mockito.ArgumentMatchers.eq("info"),
                org.mockito.ArgumentMatchers.eq("批量处理"),
                org.mockito.ArgumentMatchers.eq("支付运营"));
    }

    @Test
    void shouldRejectInvalidAlertNoBeforeReadingAlert() {
        PaymentIssueAlertAcknowledgeRequestDTO request = new PaymentIssueAlertAcknowledgeRequestDTO();
        request.setOperator("支付运营");

        assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentIssueServiceImpl(paymentIssueMapper)
                        .acknowledgeAlert(String.join("", Collections.nCopies(65, "a")), request));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentIssueServiceImpl(paymentIssueMapper)
                        .acknowledgeAlert("PIA-\u0085-001", request));
        verifyNoInteractions(paymentIssueMapper);
    }

    private PaymentIssueActionRequestDTO validBatchActionRequest() {
        PaymentIssueActionRequestDTO request = new PaymentIssueActionRequestDTO();
        request.setIssueNos(Collections.singletonList("ISSUE-001"));
        request.setActionType("分派处理人");
        request.setAssignee("后端值班");
        request.setOperator("支付运营");
        request.setRemark("排查并跟进");
        return request;
    }
}
