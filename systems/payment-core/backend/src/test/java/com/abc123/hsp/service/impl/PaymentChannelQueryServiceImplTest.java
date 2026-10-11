package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentChannelQueryResultDTO;
import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.mapper.PaymentConfigMapper;
import com.abc123.hsp.service.PaymentChannelQueryAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 渠道查单适配器选择测试。
 */
class PaymentChannelQueryServiceImplTest {

    @Test
    void shouldRejectNullPaymentDetailBeforeSelectingAdapter() {
        PaymentConfigMapper mapper = Mockito.mock(PaymentConfigMapper.class);
        PaymentChannelQueryAdapter adapter = Mockito.mock(PaymentChannelQueryAdapter.class);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentChannelQueryServiceImpl(
                        Arrays.asList(adapter),
                        new PaymentChannelReturnCodeStandardizer(mapper, new ObjectMapper()))
                        .query(null));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED, exception.getCode());
        assertEquals("payment channel query request is invalid", exception.getMessage());
        verifyNoInteractions(mapper, adapter);
    }

    @Test
    void shouldTreatNullQueryAdapterCollectionAsMissingAdapter() {
        PaymentConfigMapper mapper = Mockito.mock(PaymentConfigMapper.class);
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentChannelQueryServiceImpl(
                        null,
                        new PaymentChannelReturnCodeStandardizer(mapper, new ObjectMapper()))
                        .query(paymentDetail()));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_QUERY_ADAPTER_MISSING, exception.getCode());
    }

    @Test
    void shouldIgnoreNullQueryAdapterRows() {
        PaymentChannelQueryAdapter adapter = Mockito.mock(PaymentChannelQueryAdapter.class);
        PaymentChannelReturnCodeStandardizer standardizer = Mockito.mock(
                PaymentChannelReturnCodeStandardizer.class);
        PaymentChannelQueryResultDTO result = queryResult();
        when(adapter.supports("wx_h5")).thenReturn(true);
        when(adapter.query(any())).thenReturn(result);
        when(standardizer.standardizeQuery("wx_h5", result)).thenReturn(result);

        PaymentChannelQueryResultDTO actual = new PaymentChannelQueryServiceImpl(
                Arrays.asList(null, adapter), standardizer).query(paymentDetail());

        assertEquals(result, actual);
    }

    @Test
    void shouldSelectWechatQueryAdapterByChannelCode() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setChannel("wx_h5");
        detail.setStatus("WAIT_CALLBACK");
        detail.setChannelTransactionNo("WX-001");

        PaymentChannelQueryResultDTO result = buildService().query(detail);

        assertEquals("WECHAT_SIMULATION", result.getQuerySource());
        assertEquals("WX-001", result.getChannelTransactionNo());
        assertEquals("USERPAYING", result.getChannelReturnCode());
        assertEquals("PAYMENT-CHANNEL-UNMAPPED", result.getStandardizedCode());
    }

    @Test
    void shouldSelectAlipayQueryAdapterByChannelCode() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setChannel("alipay_h5");
        detail.setStatus("WAIT_CALLBACK");
        detail.setChannelTransactionNo("ALI-001");

        PaymentChannelQueryResultDTO result = buildService().query(detail);

        assertEquals("ALIPAY_SIMULATION", result.getQuerySource());
        assertEquals("ALI-001", result.getChannelTransactionNo());
    }

    @Test
    void shouldFallbackToLocalQueryAdapterForUnknownChannel() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setChannel("unknown_channel");
        detail.setStatus("WAIT_CALLBACK");
        detail.setChannelTransactionNo("UNK-001");

        PaymentChannelQueryResultDTO result = buildService().query(detail);

        assertEquals("LOCAL_SIMULATION_FALLBACK", result.getQuerySource());
        assertEquals("UNK-001", result.getChannelTransactionNo());
    }

    @Test
    void shouldRejectOversizedRawReturnCodeBeforeStandardizationLookup() {
        PaymentConfigMapper mapper = Mockito.mock(PaymentConfigMapper.class);
        PaymentChannelQueryAdapter adapter = Mockito.mock(PaymentChannelQueryAdapter.class);
        when(adapter.supports("wx_h5")).thenReturn(true);
        PaymentChannelQueryResultDTO result = queryResult();
        result.setChannelReturnCode(repeat('R', 64) + " ");
        when(adapter.query(any())).thenReturn(result);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentChannelQueryServiceImpl(
                        Arrays.asList(adapter),
                        new PaymentChannelReturnCodeStandardizer(mapper, new ObjectMapper()))
                        .query(paymentDetail()));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED, exception.getCode());
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldRejectOversizedWhitespaceOnlyChannelTransactionNoBeforeStandardization() {
        PaymentChannelQueryAdapter adapter = Mockito.mock(PaymentChannelQueryAdapter.class);
        PaymentChannelReturnCodeStandardizer standardizer = Mockito.mock(
                PaymentChannelReturnCodeStandardizer.class);
        when(adapter.supports("wx_h5")).thenReturn(true);
        PaymentChannelQueryResultDTO result = queryResult();
        result.setChannelTransactionNo(repeat(' ', 129));
        when(adapter.query(any())).thenReturn(result);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> new PaymentChannelQueryServiceImpl(Arrays.asList(adapter), standardizer)
                        .query(paymentDetail()));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED, exception.getCode());
        verifyNoInteractions(standardizer);
    }

    @Test
    void shouldRejectRawControlCharacterBeforeStandardizationLookup() {
        PaymentConfigMapper mapper = Mockito.mock(PaymentConfigMapper.class);
        PaymentChannelQueryAdapter adapter = Mockito.mock(PaymentChannelQueryAdapter.class);
        when(adapter.supports("wx_h5")).thenReturn(true);
        PaymentChannelQueryResultDTO result = queryResult();
        result.setChannelReturnCode("USERPAYING\n");
        when(adapter.query(any())).thenReturn(result);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentChannelQueryServiceImpl(
                        Arrays.asList(adapter),
                        new PaymentChannelReturnCodeStandardizer(mapper, new ObjectMapper()))
                        .query(paymentDetail()));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED, exception.getCode());
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldRejectEmptyAdapterResultAsInvalidChannelQuery() {
        PaymentConfigMapper mapper = Mockito.mock(PaymentConfigMapper.class);
        PaymentChannelQueryAdapter adapter = Mockito.mock(PaymentChannelQueryAdapter.class);
        when(adapter.supports("wx_h5")).thenReturn(true);
        when(adapter.query(any())).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentChannelQueryServiceImpl(
                        Arrays.asList(adapter),
                        new PaymentChannelReturnCodeStandardizer(mapper, new ObjectMapper()))
                        .query(paymentDetail()));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED, exception.getCode());
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldSanitizeUnexpectedAdapterFailure() {
        PaymentConfigMapper mapper = Mockito.mock(PaymentConfigMapper.class);
        PaymentChannelQueryAdapter adapter = Mockito.mock(PaymentChannelQueryAdapter.class);
        String sensitiveDetail = "jdbc:mysql://internal/payment?password=query-secret\n"
                + "select * from payment_order where token='query-token'";
        when(adapter.supports("wx_h5")).thenReturn(true);
        when(adapter.query(any())).thenThrow(new IllegalStateException(sensitiveDetail));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentChannelQueryServiceImpl(
                        Collections.singletonList(adapter),
                        new PaymentChannelReturnCodeStandardizer(mapper, new ObjectMapper()))
                        .query(paymentDetail()));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED, exception.getCode());
        assertEquals("payment channel query failed unexpectedly; query before retrying", exception.getMessage());
        org.junit.jupiter.api.Assertions.assertFalse(exception.getMessage().contains(sensitiveDetail));
        org.junit.jupiter.api.Assertions.assertNull(exception.getCause());
        assertEquals(0, exception.getStackTrace().length);
        exception.addSuppressed(new IllegalStateException(sensitiveDetail));
        assertEquals(0, exception.getSuppressed().length);
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldSanitizeAdapterSelectionFailure() {
        PaymentChannelQueryAdapter adapter = Mockito.mock(PaymentChannelQueryAdapter.class);
        when(adapter.supports("wx_h5")).thenThrow(new IllegalStateException("query-secret"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentChannelQueryServiceImpl(
                        Collections.singletonList(adapter),
                        new PaymentChannelReturnCodeStandardizer(
                                Mockito.mock(PaymentConfigMapper.class), new ObjectMapper()))
                        .query(paymentDetail()));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED, exception.getCode());
        assertEquals("payment channel query adapter selection failed; query before retrying", exception.getMessage());
        org.junit.jupiter.api.Assertions.assertNull(exception.getCause());
        assertEquals(0, exception.getStackTrace().length);
    }

    @Test
    void shouldNormalizeSupportedRawTradeStatusBeforeStandardization() {
        PaymentChannelQueryAdapter adapter = Mockito.mock(PaymentChannelQueryAdapter.class);
        PaymentChannelReturnCodeStandardizer standardizer = Mockito.mock(
                PaymentChannelReturnCodeStandardizer.class);
        when(adapter.supports("wx_h5")).thenReturn(true);
        PaymentChannelQueryResultDTO result = queryResult();
        result.setTradeStatus(" success ");
        when(adapter.query(any())).thenReturn(result);
        when(standardizer.standardizeQuery("wx_h5", result)).thenReturn(result);

        PaymentChannelQueryResultDTO actual = new PaymentChannelQueryServiceImpl(
                Arrays.asList(adapter), standardizer).query(paymentDetail());

        assertEquals("SUCCESS", actual.getTradeStatus());
    }

    @Test
    void shouldRejectUnknownRawTradeStatusBeforeStandardization() {
        PaymentChannelQueryAdapter adapter = Mockito.mock(PaymentChannelQueryAdapter.class);
        PaymentChannelReturnCodeStandardizer standardizer = Mockito.mock(
                PaymentChannelReturnCodeStandardizer.class);
        when(adapter.supports("wx_h5")).thenReturn(true);
        PaymentChannelQueryResultDTO result = queryResult();
        result.setTradeStatus("UNKNOWN_REMOTE");
        when(adapter.query(any())).thenReturn(result);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentChannelQueryServiceImpl(Arrays.asList(adapter), standardizer)
                        .query(paymentDetail()));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED, exception.getCode());
        verifyNoInteractions(standardizer);
    }

    @Test
    void shouldRejectOversizedStandardizedMessageBeforeReturningResult() {
        PaymentChannelQueryAdapter adapter = Mockito.mock(PaymentChannelQueryAdapter.class);
        PaymentChannelReturnCodeStandardizer standardizer = Mockito.mock(
                PaymentChannelReturnCodeStandardizer.class);
        when(adapter.supports("wx_h5")).thenReturn(true);
        PaymentChannelQueryResultDTO result = queryResult();
        when(adapter.query(any())).thenReturn(result);
        when(standardizer.standardizeQuery(eq("wx_h5"), eq(result))).thenAnswer(invocation -> {
            result.setStandardizedMessage(repeat('M', 257));
            return result;
        });

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentChannelQueryServiceImpl(Arrays.asList(adapter), standardizer)
                        .query(paymentDetail()));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED, exception.getCode());
    }

    @Test
    void shouldRejectOversizedWhitespaceOnlyMessageAddedByStandardization() {
        PaymentChannelQueryAdapter adapter = Mockito.mock(PaymentChannelQueryAdapter.class);
        PaymentChannelReturnCodeStandardizer standardizer = Mockito.mock(
                PaymentChannelReturnCodeStandardizer.class);
        when(adapter.supports("wx_h5")).thenReturn(true);
        PaymentChannelQueryResultDTO result = queryResult();
        when(adapter.query(any())).thenReturn(result);
        when(standardizer.standardizeQuery(eq("wx_h5"), eq(result))).thenAnswer(invocation -> {
            result.setStandardizedMessage(repeat(' ', 257));
            return result;
        });

        BusinessException exception = assertThrows(BusinessException.class,
                () -> new PaymentChannelQueryServiceImpl(Arrays.asList(adapter), standardizer)
                        .query(paymentDetail()));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED, exception.getCode());
    }

    @Test
    void shouldRejectControlCharacterAddedByStandardization() {
        PaymentChannelQueryAdapter adapter = Mockito.mock(PaymentChannelQueryAdapter.class);
        PaymentChannelReturnCodeStandardizer standardizer = Mockito.mock(
                PaymentChannelReturnCodeStandardizer.class);
        when(adapter.supports("wx_h5")).thenReturn(true);
        PaymentChannelQueryResultDTO result = queryResult();
        when(adapter.query(any())).thenReturn(result);
        when(standardizer.standardizeQuery(eq("wx_h5"), eq(result))).thenAnswer(invocation -> {
            result.setStandardizedMessage("用户支付中\n");
            return result;
        });

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentChannelQueryServiceImpl(Arrays.asList(adapter), standardizer)
                        .query(paymentDetail()));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED, exception.getCode());
    }

    private PaymentChannelQueryServiceImpl buildService() {
        PaymentConfigMapper mapper = Mockito.mock(PaymentConfigMapper.class);
        return new PaymentChannelQueryServiceImpl(Arrays.asList(
                ordered(new WechatH5PaymentChannelQueryAdapter()),
                ordered(new AlipayH5PaymentChannelQueryAdapter()),
                ordered(new OfflineBankPaymentChannelQueryAdapter()),
                ordered(new LocalPaymentChannelQueryAdapter())
        ), new PaymentChannelReturnCodeStandardizer(mapper, new ObjectMapper()));
    }

    private PaymentChannelQueryAdapter ordered(PaymentChannelQueryAdapter adapter) {
        return adapter;
    }

    private PaymentDetailDTO paymentDetail() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setChannel("wx_h5");
        return detail;
    }

    private PaymentChannelQueryResultDTO queryResult() {
        PaymentChannelQueryResultDTO result = new PaymentChannelQueryResultDTO();
        result.setTradeStatus("WAIT_CALLBACK");
        result.setQuerySource("GATEWAY_ACCESS_HTTP");
        return result;
    }

    private String repeat(char value, int count) {
        char[] characters = new char[count];
        Arrays.fill(characters, value);
        return new String(characters);
    }
}
