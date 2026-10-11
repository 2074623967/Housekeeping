package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
    void shouldRejectNullSubmitRequestBeforeSelectingAdapter() {
        PaymentConfigMapper mapper = Mockito.mock(PaymentConfigMapper.class);
        PaymentChannelSubmitAdapter adapter = Mockito.mock(PaymentChannelSubmitAdapter.class);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service(adapter, mapper).submit(null));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED, exception.getCode());
        assertEquals("payment channel submit request is invalid", exception.getMessage());
        verifyNoInteractions(mapper, adapter);
    }

    @Test
    void shouldTreatNullSubmitAdapterCollectionAsMissingAdapter() {
        PaymentConfigMapper mapper = Mockito.mock(PaymentConfigMapper.class);
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentChannelSubmitServiceImpl(
                        null,
                        new PaymentChannelReturnCodeStandardizer(mapper, new ObjectMapper()))
                        .submit(request()));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMIT_ADAPTER_MISSING, exception.getCode());
    }

    @Test
    void shouldIgnoreNullSubmitAdapterRows() {
        PaymentChannelSubmitAdapter adapter = Mockito.mock(PaymentChannelSubmitAdapter.class);
        PaymentChannelReturnCodeStandardizer standardizer = Mockito.mock(
                PaymentChannelReturnCodeStandardizer.class);
        when(adapter.supports("wx_h5")).thenReturn(true);
        PaymentChannelSubmitResultDTO result = validResult();
        when(adapter.submit(any())).thenReturn(result);
        when(standardizer.standardizeSubmit("wx_h5", result)).thenReturn(result);

        PaymentChannelSubmitResultDTO actual = new PaymentChannelSubmitServiceImpl(
                Arrays.asList(null, adapter), standardizer).submit(request());

        assertEquals(result, actual);
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

    @Test
    void shouldRejectEmptyAdapterResultBeforeStandardizationLookup() {
        PaymentConfigMapper mapper = Mockito.mock(PaymentConfigMapper.class);
        PaymentChannelSubmitAdapter adapter = Mockito.mock(PaymentChannelSubmitAdapter.class);
        when(adapter.supports("wx_h5")).thenReturn(true);
        when(adapter.submit(any())).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service(adapter, mapper).submit(request()));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED, exception.getCode());
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldRejectRawControlCharacterBeforeStandardizationLookup() {
        PaymentConfigMapper mapper = Mockito.mock(PaymentConfigMapper.class);
        PaymentChannelSubmitAdapter adapter = Mockito.mock(PaymentChannelSubmitAdapter.class);
        when(adapter.supports("wx_h5")).thenReturn(true);
        PaymentChannelSubmitResultDTO result = validResult();
        result.setChannelReturnCode("USERPAYING\n");
        when(adapter.submit(any())).thenReturn(result);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service(adapter, mapper).submit(request()));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED, exception.getCode());
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldRejectOversizedRawReturnCodeBeforeStandardizationLookup() {
        PaymentConfigMapper mapper = Mockito.mock(PaymentConfigMapper.class);
        PaymentChannelSubmitAdapter adapter = Mockito.mock(PaymentChannelSubmitAdapter.class);
        when(adapter.supports("wx_h5")).thenReturn(true);
        PaymentChannelSubmitResultDTO result = validResult();
        result.setChannelReturnCode(repeat('R', 64) + " ");
        when(adapter.submit(any())).thenReturn(result);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service(adapter, mapper).submit(request()));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED, exception.getCode());
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldRejectUnknownAttemptStatusTypeBeforeStandardizationLookup() {
        PaymentConfigMapper mapper = Mockito.mock(PaymentConfigMapper.class);
        PaymentChannelSubmitAdapter adapter = Mockito.mock(PaymentChannelSubmitAdapter.class);
        when(adapter.supports("wx_h5")).thenReturn(true);
        PaymentChannelSubmitResultDTO result = validResult();
        result.setAttemptStatusType("REMOTE_PENDING");
        when(adapter.submit(any())).thenReturn(result);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service(adapter, mapper).submit(request()));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED, exception.getCode());
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldPreserveCompleteNativePaymentParameters() {
        PaymentChannelSubmitAdapter adapter = Mockito.mock(PaymentChannelSubmitAdapter.class);
        PaymentChannelReturnCodeStandardizer standardizer = Mockito.mock(
                PaymentChannelReturnCodeStandardizer.class);
        PaymentChannelSubmitResultDTO result = validResult();
        result.setNativePaymentParams(nativePaymentParams());
        when(adapter.supports("wx_h5")).thenReturn(true);
        when(adapter.submit(any())).thenReturn(result);
        PaymentChannelSubmitRequestDTO request = request();
        request.setTerminal("MINI_PROGRAM");
        when(standardizer.standardizeSubmit("wx_h5", result)).thenReturn(result);

        PaymentChannelSubmitResultDTO normalized = new PaymentChannelSubmitServiceImpl(
                Collections.singletonList(adapter), standardizer).submit(request);

        assertEquals("prepay_id=wx-prepay", normalized.getNativePaymentParams().get("package"));
    }

    @Test
    void shouldRejectNativePaymentParametersForNonMiniProgramTerminal() {
        PaymentChannelSubmitAdapter adapter = Mockito.mock(PaymentChannelSubmitAdapter.class);
        PaymentChannelReturnCodeStandardizer standardizer = Mockito.mock(
                PaymentChannelReturnCodeStandardizer.class);
        PaymentChannelSubmitResultDTO result = validResult();
        result.setNativePaymentParams(nativePaymentParams());
        when(adapter.supports("wx_h5")).thenReturn(true);
        when(adapter.submit(any())).thenReturn(result);

        PaymentChannelSubmitRequestDTO request = request();
        request.setTerminal("APP");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> new PaymentChannelSubmitServiceImpl(
                        Collections.singletonList(adapter), standardizer).submit(request));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED, exception.getCode());
    }

    @Test
    void shouldRejectIncompleteNativePaymentParameters() {
        PaymentChannelSubmitAdapter adapter = Mockito.mock(PaymentChannelSubmitAdapter.class);
        PaymentChannelReturnCodeStandardizer standardizer = Mockito.mock(
                PaymentChannelReturnCodeStandardizer.class);
        PaymentChannelSubmitResultDTO result = validResult();
        result.setNativePaymentParams(Collections.singletonMap("timeStamp", "1710000000"));
        when(adapter.supports("wx_h5")).thenReturn(true);
        when(adapter.submit(any())).thenReturn(result);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> new PaymentChannelSubmitServiceImpl(
                        Collections.singletonList(adapter), standardizer).submit(request()));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED, exception.getCode());
    }

    @Test
    void shouldRejectInvalidFieldAddedByReturnCodeStandardization() {
        PaymentChannelSubmitAdapter adapter = Mockito.mock(PaymentChannelSubmitAdapter.class);
        PaymentChannelReturnCodeStandardizer standardizer = Mockito.mock(
                PaymentChannelReturnCodeStandardizer.class);
        PaymentChannelSubmitRequestDTO request = request();
        PaymentChannelSubmitResultDTO result = validResult();
        when(adapter.supports("wx_h5")).thenReturn(true);
        when(adapter.submit(request)).thenReturn(result);
        when(standardizer.standardizeSubmit("wx_h5", result)).thenAnswer(invocation -> {
            result.setStandardizedMessage("invalid\nmessage");
            return result;
        });

        BusinessException exception = assertThrows(BusinessException.class,
                () -> new PaymentChannelSubmitServiceImpl(
                        Collections.singletonList(adapter), standardizer).submit(request));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED, exception.getCode());
    }

    @Test
    void shouldSanitizeUnexpectedAdapterFailure() {
        PaymentConfigMapper mapper = Mockito.mock(PaymentConfigMapper.class);
        PaymentChannelSubmitAdapter adapter = Mockito.mock(PaymentChannelSubmitAdapter.class);
        String sensitiveDetail = "jdbc:mysql://internal/payment?password=submit-secret\n"
                + "select * from payment_order where token='submit-token'";
        when(adapter.supports("wx_h5")).thenReturn(true);
        when(adapter.submit(any())).thenThrow(new IllegalStateException(sensitiveDetail));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service(adapter, mapper).submit(request()));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED, exception.getCode());
        assertEquals("payment channel submit failed unexpectedly; query before retrying", exception.getMessage());
        assertFalse(exception.getMessage().contains(sensitiveDetail));
        assertNull(exception.getCause());
        assertEquals(0, exception.getStackTrace().length);
        exception.addSuppressed(new IllegalStateException(sensitiveDetail));
        assertEquals(0, exception.getSuppressed().length);
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldSanitizeAdapterSelectionFailure() {
        PaymentChannelSubmitAdapter adapter = Mockito.mock(PaymentChannelSubmitAdapter.class);
        when(adapter.supports("wx_h5")).thenThrow(new IllegalStateException("submit-secret"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service(adapter, Mockito.mock(PaymentConfigMapper.class)).submit(request()));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED, exception.getCode());
        assertEquals("payment channel submit adapter selection failed; query before retrying", exception.getMessage());
        assertNull(exception.getCause());
        assertEquals(0, exception.getStackTrace().length);
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

    private PaymentChannelSubmitServiceImpl service(
            PaymentChannelSubmitAdapter adapter, PaymentConfigMapper mapper) {
        return new PaymentChannelSubmitServiceImpl(
                Collections.singletonList(adapter),
                new PaymentChannelReturnCodeStandardizer(mapper, new ObjectMapper()));
    }

    private PaymentChannelSubmitRequestDTO request() {
        PaymentChannelSubmitRequestDTO request = new PaymentChannelSubmitRequestDTO();
        request.setResolvedChannelCode("wx_h5");
        return request;
    }

    private PaymentChannelSubmitResultDTO validResult() {
        PaymentChannelSubmitResultDTO result = new PaymentChannelSubmitResultDTO();
        result.setChannelTransactionNo("WX-1001");
        result.setAttemptStatus("处理中");
        result.setAttemptStatusType("info");
        result.setResponsePayload("{\"code\":\"0\"}");
        result.setChannelReturnCode("USERPAYING");
        return result;
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

    private Map<String, String> nativePaymentParams() {
        Map<String, String> params = new HashMap<>();
        params.put("timeStamp", "1710000000");
        params.put("nonceStr", "nonce");
        params.put("package", "prepay_id=wx-prepay");
        params.put("signType", "RSA");
        params.put("paySign", "signature");
        return params;
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

    private String repeat(char value, int count) {
        char[] values = new char[count];
        java.util.Arrays.fill(values, value);
        return new String(values);
    }
}
