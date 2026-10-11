package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentFlowListItemDTO;
import com.abc123.hsp.dto.PaymentFlowDetailDTO;
import com.abc123.hsp.dto.PaymentFlowQueryDTO;
import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.mapper.PaymentFlowMapper;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付流水分页查询测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentFlowServiceImplTest {

    @Mock
    private PaymentFlowMapper paymentFlowMapper;

    @Test
    void shouldNormalizeAndForwardPaymentFlowPagingQuery() {
        PaymentFlowQueryDTO query = new PaymentFlowQueryDTO();
        query.setPaymentOrderId(" PAY-001 ");
        query.setOrderNo(" ORD-001 ");
        query.setFlowType("支付尝试");
        query.setChannelCode(" wx_h5 ");
        query.setTerminal(" H5 ");
        query.setBusinessStatus(" WAIT_CALLBACK ");
        query.setKeyword(" 回调 ");
        query.setSortField(" retryCount ");
        query.setSortOrder(" ASC ");
        query.setPageNo(-3);
        query.setPageSize(0);

        when(paymentFlowMapper.findAll(query)).thenReturn(Collections.emptyList());
        when(paymentFlowMapper.count(query)).thenReturn(0L);
        new PaymentFlowServiceImpl(paymentFlowMapper).list(query);

        assertEquals(1, query.getPageNo());
        assertEquals(1, query.getPageSize());
        assertEquals("wx_h5", query.getChannelCode());
        assertEquals("H5", query.getTerminal());
        assertEquals("WAIT_CALLBACK", query.getBusinessStatus());
        assertEquals("回调", query.getKeyword());
        assertEquals("retryCount", query.getSortField());
        assertEquals("asc", query.getSortOrder());
        verify(paymentFlowMapper).findAll(query);
        verify(paymentFlowMapper).count(query);
    }

    @Test
    void shouldUseDefaultQueryWhenListingPaymentFlowsWithNullQuery() {
        when(paymentFlowMapper.findAll(org.mockito.ArgumentMatchers.any(PaymentFlowQueryDTO.class)))
                .thenReturn(Collections.emptyList());
        when(paymentFlowMapper.count(org.mockito.ArgumentMatchers.any(PaymentFlowQueryDTO.class))).thenReturn(0L);

        com.abc123.hsp.dto.PageResultDTO<PaymentFlowListItemDTO> result =
                new PaymentFlowServiceImpl(paymentFlowMapper).list(null);

        assertEquals(0, result.getTotal());
        org.mockito.ArgumentCaptor<PaymentFlowQueryDTO> captor =
                org.mockito.ArgumentCaptor.forClass(PaymentFlowQueryDTO.class);
        verify(paymentFlowMapper).findAll(captor.capture());
        assertEquals(1, captor.getValue().getPageNo());
        assertEquals(20, captor.getValue().getPageSize());
    }

    @Test
    void shouldRejectOversizedPaymentFlowQueryBeforeMapperAccess() {
        PaymentFlowQueryDTO paymentOrderQuery = new PaymentFlowQueryDTO();
        paymentOrderQuery.setPaymentOrderId(repeat("P", 65));
        PaymentFlowQueryDTO paymentOrderAfterTrimQuery = new PaymentFlowQueryDTO();
        paymentOrderAfterTrimQuery.setPaymentOrderId(repeat("P", 64) + " ");
        PaymentFlowQueryDTO orderQuery = new PaymentFlowQueryDTO();
        orderQuery.setOrderNo(repeat("O", 65));
        PaymentFlowQueryDTO flowTypeQuery = new PaymentFlowQueryDTO();
        flowTypeQuery.setFlowType(repeat("F", 33));
        PaymentFlowQueryDTO channelQuery = new PaymentFlowQueryDTO();
        channelQuery.setChannelCode(repeat("C", 65));
        PaymentFlowQueryDTO terminalQuery = new PaymentFlowQueryDTO();
        terminalQuery.setTerminal(repeat("T", 33));
        PaymentFlowQueryDTO businessStatusQuery = new PaymentFlowQueryDTO();
        businessStatusQuery.setBusinessStatus(repeat("S", 129));
        PaymentFlowQueryDTO keywordQuery = new PaymentFlowQueryDTO();
        keywordQuery.setKeyword(repeat("K", 2049));
        PaymentFlowServiceImpl service = new PaymentFlowServiceImpl(paymentFlowMapper);

        assertThrows(IllegalArgumentException.class, () -> service.list(paymentOrderQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(paymentOrderAfterTrimQuery));
        assertThrows(IllegalArgumentException.class, () -> service.exportCsv(orderQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(flowTypeQuery));
        assertThrows(IllegalArgumentException.class, () -> service.exportCsv(channelQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(terminalQuery));
        assertThrows(IllegalArgumentException.class, () -> service.exportCsv(businessStatusQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(keywordQuery));
        verifyNoInteractions(paymentFlowMapper);
    }

    @Test
    void shouldRejectControlCharactersInPaymentFlowQueryBeforeMapperAccess() {
        PaymentFlowQueryDTO paymentOrderQuery = new PaymentFlowQueryDTO();
        paymentOrderQuery.setPaymentOrderId("PAY-001\n");
        PaymentFlowQueryDTO orderQuery = new PaymentFlowQueryDTO();
        orderQuery.setOrderNo("ORD-001\n");
        PaymentFlowQueryDTO flowTypeQuery = new PaymentFlowQueryDTO();
        flowTypeQuery.setFlowType("支付尝试\n");
        PaymentFlowQueryDTO channelQuery = new PaymentFlowQueryDTO();
        channelQuery.setChannelCode("wx_h5\n");
        PaymentFlowQueryDTO terminalQuery = new PaymentFlowQueryDTO();
        terminalQuery.setTerminal("H5\n");
        PaymentFlowQueryDTO businessStatusQuery = new PaymentFlowQueryDTO();
        businessStatusQuery.setBusinessStatus("WAIT_CALLBACK\n");
        PaymentFlowQueryDTO keywordQuery = new PaymentFlowQueryDTO();
        keywordQuery.setKeyword("回调\n");
        PaymentFlowServiceImpl service = new PaymentFlowServiceImpl(paymentFlowMapper);

        assertThrows(IllegalArgumentException.class, () -> service.list(paymentOrderQuery));
        assertThrows(IllegalArgumentException.class, () -> service.exportCsv(orderQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(flowTypeQuery));
        assertThrows(IllegalArgumentException.class, () -> service.exportCsv(channelQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(terminalQuery));
        assertThrows(IllegalArgumentException.class, () -> service.exportCsv(businessStatusQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(keywordQuery));
        verifyNoInteractions(paymentFlowMapper);
    }

    @Test
    void shouldExportPaymentFlowsInBatchesWithNormalizedFilters() {
        PaymentFlowQueryDTO query = new PaymentFlowQueryDTO();
        query.setPaymentOrderId(" PAY-001 ");
        query.setOrderNo(" ORD-001 ");
        query.setFlowType("支付尝试");
        query.setChannelCode(" wx_h5 ");
        query.setTerminal(" H5 ");
        query.setBusinessStatus(" WAIT_CALLBACK ");
        query.setKeyword(" 原始报文 ");
        query.setSortField(" retryCount ");
        query.setSortOrder(" ASC ");
        query.setPageNo(3);
        query.setPageSize(500);

        when(paymentFlowMapper.findAllForExport(query)).thenReturn(Collections.singletonList(buildItem("FLOW-001", "首笔\"摘要", 1)));

        String csv = new PaymentFlowServiceImpl(paymentFlowMapper).exportCsv(query);

        assertEquals("PAY-001", query.getPaymentOrderId());
        assertEquals("ORD-001", query.getOrderNo());
        assertEquals("wx_h5", query.getChannelCode());
        assertEquals("H5", query.getTerminal());
        assertEquals("WAIT_CALLBACK", query.getBusinessStatus());
        assertEquals("原始报文", query.getKeyword());
        assertEquals("retryCount", query.getSortField());
        assertEquals("asc", query.getSortOrder());
        assertEquals(3, query.getPageNo());
        assertEquals(100, query.getPageSize());
        assertTrue(csv.startsWith("\uFEFF流水来源,流水编号,支付单号,业务链路,订单号,预付单号,支付场景,流水类型,类型标签"));
        assertTrue(csv.contains("\"CHAIN-PAY-001\""));
        assertTrue(csv.contains("\"HOME_CLEAN\""));
        assertTrue(csv.contains("\"FLOW-001\""));
        assertTrue(csv.contains("\"首笔\"\"摘要\""));
        assertTrue(csv.contains("\"info\""));
        assertTrue(csv.contains("\"WAIT_CALLBACK\""));
        verify(paymentFlowMapper).findAllForExport(query);
    }

    @Test
    void shouldMaskListAndExportWithoutMutatingMapperFacts() {
        PaymentFlowQueryDTO listQuery = new PaymentFlowQueryDTO();
        PaymentFlowListItemDTO item = buildItem("FLOW-001", "方式=微信 / 状态=处理中", 0);
        item.setIdempotencyKey("ORDER-REQUEST-20260803-ABCDEFG");
        item.setRequestPayload("{\"payerPhone\":\"13800138000\",\"nested\":{\"clientDeviceId\":\"DEVICE-12345678\"}}");
        when(paymentFlowMapper.findAll(listQuery))
                .thenReturn(java.util.Arrays.asList((PaymentFlowListItemDTO) null, item));
        when(paymentFlowMapper.count(listQuery)).thenReturn(1L);

        PaymentFlowListItemDTO result = new PaymentFlowServiceImpl(paymentFlowMapper)
                .list(listQuery)
                .getItems()
                .get(0);

        assertEquals("127.0.*.*", result.getClientIp());
        assertEquals("ORDER-****DEFG", result.getIdempotencyKey());
        assertTrue(result.getRequestPayload().contains("138****8000"));
        assertTrue(result.getRequestPayload().contains("DEVI****5678"));
        assertEquals("127.0.0.1", item.getClientIp());
        assertTrue(item.getRequestPayload().contains("13800138000"));
    }

    @Test
    void shouldNormalizeFlowSourceAndMaskDetailCopyWithoutMutatingMapperFact() {
        PaymentFlowDetailDTO fact = new PaymentFlowDetailDTO();
        fact.setFlowSource("PAYMENT_ATTEMPT");
        fact.setFlowNo("FLOW-001");
        fact.setClientIp("127.0.0.1");
        fact.setIdempotencyKey("ORDER-REQUEST-20260803-ABCDEFG");
        fact.setRequestPayload("{\"payerPhone\":\"13800138000\"}");
        when(paymentFlowMapper.findDetail("PAYMENT_ATTEMPT", "FLOW-001")).thenReturn(fact);

        PaymentFlowDetailDTO result = new PaymentFlowServiceImpl(paymentFlowMapper)
                .detail(" payment_attempt ", " FLOW-001 ");

        assertEquals("PAYMENT_ATTEMPT", result.getFlowSource());
        assertEquals("127.0.*.*", result.getClientIp());
        assertEquals("ORDER-****DEFG", result.getIdempotencyKey());
        assertTrue(result.getRequestPayload().contains("138****8000"));
        assertEquals("127.0.0.1", fact.getClientIp());
        verify(paymentFlowMapper).findDetail("PAYMENT_ATTEMPT", "FLOW-001");
    }

    @Test
    void shouldRejectInvalidFlowDetailInputsAndReturnStableNotFoundCode() {
        PaymentFlowServiceImpl service = new PaymentFlowServiceImpl(paymentFlowMapper);
        assertThrows(BusinessException.class, () -> service.detail(null, "FLOW-001"));
        assertThrows(BusinessException.class, () -> service.detail("UNKNOWN", "FLOW-001"));
        assertThrows(BusinessException.class, () -> service.detail("PAYMENT_ATTEMPT", "FLOW-001\n"));
        assertThrows(BusinessException.class, () -> service.detail("PAYMENT_ATTEMPT", repeat("F", 65)));
        assertThrows(BusinessException.class,
                () -> service.detail("PAYMENT_ATTEMPT", repeat("F", 64) + " "));

        when(paymentFlowMapper.findDetail("BUSINESS_EVENT", "MISSING")).thenReturn(null);
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.detail("business_event", "MISSING"));
        assertEquals(ErrorCode.PAYMENT_FLOW_NOT_FOUND, exception.getCode());
        verify(paymentFlowMapper).findDetail("BUSINESS_EVENT", "MISSING");
    }

    private PaymentFlowListItemDTO buildItem(String flowNo, String summary, Integer retryCount) {
        PaymentFlowListItemDTO item = new PaymentFlowListItemDTO();
        item.setFlowNo(flowNo);
        item.setPaymentOrderId("PAY-001");
        item.setOrderNo("ORD-001");
        item.setPrepayOrderNo("PRE-001");
        item.setPayScene("HOME_CLEAN");
        item.setFlowType("支付尝试");
        item.setFlowTypeTag("info");
        item.setChannelCode("wx_h5");
        item.setTerminal("H5");
        item.setClientIp("127.0.0.1");
        item.setIdempotencyKey("IDEMP-001");
        item.setBusinessStatus("WAIT_CALLBACK");
        item.setBusinessStatusType("warning");
        item.setNotifyType("支付回调");
        item.setRouteRule("默认优先");
        item.setDownstreamSystem("trade-center");
        item.setEventTopic("payment.events");
        item.setPublishStatus("SUCCESS");
        item.setRetryCount(retryCount);
        item.setRequestPayload("{\"amount\":6800}");
        item.setResponsePayload("{\"code\":\"SUCCESS\"}");
        item.setSummary(summary);
        item.setCreatedAt("2026-07-31 15:12:00");
        return item;
    }

    private String repeat(String value, int times) {
        return String.join("", Collections.nCopies(times, value));
    }
}
