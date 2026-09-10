package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentChannelSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentChannelSubmitResultDTO;
import com.abc123.hsp.dto.PaymentChannelReturnCodeConfigDTO;
import com.abc123.hsp.mapper.PaymentConfigMapper;
import com.abc123.hsp.service.PaymentChannelSubmitAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 渠道下单适配器选择测试。
 */
class PaymentChannelSubmitServiceImplTest {

    @Test
    void shouldFailClosedWhenNoSubmitAdapterSupportsChannel() {
        PaymentChannelSubmitServiceImpl service = new PaymentChannelSubmitServiceImpl(
                Collections.emptyList(),
                new PaymentChannelReturnCodeStandardizer(
                        Mockito.mock(PaymentConfigMapper.class), new ObjectMapper()));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.ensureAdapterAvailable("wx_h5"));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMIT_ADAPTER_MISSING, exception.getCode());
    }

    @Test
    void shouldSelectWechatSubmitAdapterByChannelCode() {
        PaymentChannelSubmitRequestDTO request = new PaymentChannelSubmitRequestDTO();
        request.setResolvedChannelCode("wx_h5");

        PaymentChannelSubmitResultDTO result = buildService().submit(request);

        assertEquals("处理中", result.getAttemptStatus());
        assertEquals("USERPAYING", result.getChannelReturnCode());
        assertEquals("PAYMENT-CHANNEL-1001", result.getStandardizedCode());
        assertEquals(Boolean.TRUE, result.getReturnCodeMappingMatched());
        org.junit.jupiter.api.Assertions.assertTrue(result.getResponsePayload().contains("\"gatewayCode\":\"wechat-gateway\""));
        org.junit.jupiter.api.Assertions.assertTrue(result.getResponsePayload().contains("\"mappingVersion\":\"v1\""));
    }

    @Test
    void shouldSelectOfflineBankSubmitAdapterByChannelCode() {
        PaymentChannelSubmitRequestDTO request = new PaymentChannelSubmitRequestDTO();
        request.setResolvedChannelCode("offline_bank");

        PaymentChannelSubmitResultDTO result = buildService().submit(request);

        assertEquals("待人工确认", result.getAttemptStatus());
        assertEquals("warn", result.getAttemptStatusType());
        assertEquals("PAYMENT-CHANNEL-UNMAPPED", result.getStandardizedCode());
        assertEquals(Boolean.FALSE, result.getReturnCodeMappingMatched());
        org.junit.jupiter.api.Assertions.assertTrue(result.getResponsePayload().contains("\"gatewayCode\":\"offline-bank-gateway\""));
    }

    @Test
    void shouldFallbackToLocalSubmitAdapterForUnknownChannel() {
        PaymentChannelSubmitRequestDTO request = new PaymentChannelSubmitRequestDTO();
        request.setResolvedChannelCode("unknown_channel");

        PaymentChannelSubmitResultDTO result = buildService().submit(request);

        assertEquals("处理中", result.getAttemptStatus());
        org.junit.jupiter.api.Assertions.assertTrue(result.getResponsePayload().contains("\"gatewayCode\":\"local-fallback-gateway\""));
    }

    @Test
    void shouldGenerateCollisionResistantTransactionNumbersForSimulatedAdapters() {
        assertUniqueTransactionNumbers(new WechatH5PaymentChannelSubmitAdapter(), "wx_h5", "WX-");
        assertUniqueTransactionNumbers(new AlipayH5PaymentChannelSubmitAdapter(), "alipay_h5", "ALI-");
        assertUniqueTransactionNumbers(new OfflineBankPaymentChannelSubmitAdapter(), "offline_bank", "BANK-");
        assertUniqueTransactionNumbers(new LocalPaymentChannelSubmitAdapter(), "unknown_channel", "CHN-");
    }

    private PaymentChannelSubmitServiceImpl buildService() {
        PaymentConfigMapper mapper = Mockito.mock(PaymentConfigMapper.class);
        Mockito.when(mapper.findActiveReturnCodeMapping("wx_h5", "USERPAYING"))
                .thenReturn(mapping());
        return new PaymentChannelSubmitServiceImpl(Arrays.asList(
                ordered(new WechatH5PaymentChannelSubmitAdapter()),
                ordered(new AlipayH5PaymentChannelSubmitAdapter()),
                ordered(new OfflineBankPaymentChannelSubmitAdapter()),
                ordered(new LocalPaymentChannelSubmitAdapter())
        ), new PaymentChannelReturnCodeStandardizer(mapper, new ObjectMapper()));
    }

    private PaymentChannelReturnCodeConfigDTO mapping() {
        PaymentChannelReturnCodeConfigDTO mapping = new PaymentChannelReturnCodeConfigDTO();
        mapping.setStandardizedCode("PAYMENT-CHANNEL-1001");
        mapping.setStandardizedMessage("用户支付中");
        mapping.setHandlingSuggestion("等待回调或主动查单");
        mapping.setRetryable("是");
        mapping.setManualInterventionRequired("否");
        mapping.setMappingVersion("v1");
        return mapping;
    }

    private void assertUniqueTransactionNumbers(PaymentChannelSubmitAdapter adapter,
                                                String channelCode,
                                                String prefix) {
        PaymentChannelSubmitRequestDTO request = new PaymentChannelSubmitRequestDTO();
        request.setResolvedChannelCode(channelCode);
        Set<String> transactionNos = new HashSet<String>();
        for (int index = 0; index < 1000; index++) {
            PaymentChannelSubmitResultDTO result = adapter.submit(request);
            org.junit.jupiter.api.Assertions.assertTrue(
                    result.getChannelTransactionNo().matches(prefix + "\\d{13}[0-9a-f]{32}"));
            org.junit.jupiter.api.Assertions.assertTrue(transactionNos.add(result.getChannelTransactionNo()));
            org.junit.jupiter.api.Assertions.assertTrue(
                    result.getResponsePayload().contains(
                            "\"channelTransactionNo\":\"" + result.getChannelTransactionNo() + "\""));
        }
    }

    private PaymentChannelSubmitAdapter ordered(PaymentChannelSubmitAdapter adapter) {
        return adapter;
    }
}
