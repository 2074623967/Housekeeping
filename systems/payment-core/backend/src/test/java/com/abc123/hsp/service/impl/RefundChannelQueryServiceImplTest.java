package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.RefundCallbackSourceDTO;
import com.abc123.hsp.dto.RefundChannelQueryResultDTO;
import com.abc123.hsp.service.RefundChannelQueryAdapter;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class RefundChannelQueryServiceImplTest {

    @Test
    void shouldUseFirstSupportingAdapterAndNormalizeResult() {
        RefundChannelQueryAdapter skipped = mock(RefundChannelQueryAdapter.class);
        RefundChannelQueryAdapter selected = mock(RefundChannelQueryAdapter.class);
        RefundCallbackSourceDTO refund = refund("WX_H5");
        RefundChannelQueryResultDTO raw = result(" success ", " CHANNEL-REF-001 ", " TEST ");
        when(skipped.supports("WX_H5")).thenReturn(false);
        when(selected.supports("WX_H5")).thenReturn(true);
        when(selected.query(refund)).thenReturn(raw);

        RefundChannelQueryResultDTO actual = new RefundChannelQueryServiceImpl(Arrays.asList(skipped, selected))
                .query(refund);

        assertEquals("SUCCESS", actual.getRefundStatus());
        assertEquals("CHANNEL-REF-001", actual.getChannelRefundNo());
        assertEquals("TEST", actual.getQuerySource());
        verify(selected).query(refund);
    }

    @Test
    void shouldRejectMissingAdapterAndInvalidResult() {
        RefundCallbackSourceDTO refund = refund("wx_h5");
        BusinessException missing = assertThrows(BusinessException.class,
                () -> new RefundChannelQueryServiceImpl(Collections.emptyList()).query(refund));
        assertEquals(ErrorCode.REFUND_CHANNEL_QUERY_ADAPTER_MISSING, missing.getCode());

        RefundChannelQueryAdapter adapter = mock(RefundChannelQueryAdapter.class);
        when(adapter.supports("wx_h5")).thenReturn(true);
        when(adapter.query(refund)).thenReturn(result("UNKNOWN", "CHANNEL-REF-001", "TEST"));
        BusinessException invalid = assertThrows(BusinessException.class,
                () -> new RefundChannelQueryServiceImpl(Collections.singletonList(adapter)).query(refund));
        assertEquals(ErrorCode.REFUND_CHANNEL_QUERY_FAILED, invalid.getCode());
    }

    @Test
    void shouldRejectNullRefundBeforeSelectingAdapter() {
        RefundChannelQueryAdapter adapter = mock(RefundChannelQueryAdapter.class);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new RefundChannelQueryServiceImpl(Arrays.asList(null, adapter)).query(null));

        assertEquals(ErrorCode.REFUND_CHANNEL_QUERY_FAILED, exception.getCode());
        assertEquals("refund channel query request is invalid", exception.getMessage());
        verifyNoInteractions(adapter);
    }

    @Test
    void shouldTreatNullAdapterCollectionAsMissingAdapter() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new RefundChannelQueryServiceImpl(null).query(refund("wx_h5")));

        assertEquals(ErrorCode.REFUND_CHANNEL_QUERY_ADAPTER_MISSING, exception.getCode());
    }

    @Test
    void shouldRejectUnsafeChannelQueryFields() {
        RefundCallbackSourceDTO refund = refund("wx_h5");
        RefundChannelQueryAdapter adapter = mock(RefundChannelQueryAdapter.class);
        when(adapter.supports("wx_h5")).thenReturn(true);
        when(adapter.query(refund)).thenReturn(result("SUCCESS", "CHANNEL-REF-001\n", "TEST"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> new RefundChannelQueryServiceImpl(Collections.singletonList(adapter)).query(refund));

        assertEquals(ErrorCode.REFUND_CHANNEL_QUERY_FAILED, exception.getCode());
    }

    @Test
    void shouldRejectOversizedRawChannelQueryFieldsBeforeNormalization() {
        RefundCallbackSourceDTO refund = refund("wx_h5");
        RefundChannelQueryAdapter adapter = mock(RefundChannelQueryAdapter.class);
        when(adapter.supports("wx_h5")).thenReturn(true);
        when(adapter.query(refund)).thenReturn(
                result("SUCCESS", repeat('R', 128) + " ", "TEST"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> new RefundChannelQueryServiceImpl(Collections.singletonList(adapter)).query(refund));

        assertEquals(ErrorCode.REFUND_CHANNEL_QUERY_FAILED, exception.getCode());
    }

    @Test
    void shouldSanitizeUnexpectedAdapterFailure() {
        RefundCallbackSourceDTO refund = refund("wx_h5");
        RefundChannelQueryAdapter adapter = mock(RefundChannelQueryAdapter.class);
        String sensitiveDetail = "jdbc:mysql://internal/payment?password=refund-query-secret\n"
                + "select * from refund_order where token='refund-query-token'";
        when(adapter.supports("wx_h5")).thenReturn(true);
        when(adapter.query(refund)).thenThrow(new IllegalStateException(sensitiveDetail));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new RefundChannelQueryServiceImpl(Collections.singletonList(adapter)).query(refund));

        assertEquals(ErrorCode.REFUND_CHANNEL_QUERY_FAILED, exception.getCode());
        assertEquals("refund channel query failed unexpectedly; query before retrying", exception.getMessage());
        org.junit.jupiter.api.Assertions.assertFalse(exception.getMessage().contains(sensitiveDetail));
        org.junit.jupiter.api.Assertions.assertNull(exception.getCause());
        assertEquals(0, exception.getStackTrace().length);
        exception.addSuppressed(new IllegalStateException(sensitiveDetail));
        assertEquals(0, exception.getSuppressed().length);
    }

    @Test
    void shouldSanitizeAdapterSelectionFailure() {
        RefundCallbackSourceDTO refund = refund("wx_h5");
        RefundChannelQueryAdapter adapter = mock(RefundChannelQueryAdapter.class);
        when(adapter.supports("wx_h5")).thenThrow(new IllegalStateException("refund-query-secret"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new RefundChannelQueryServiceImpl(Collections.singletonList(adapter)).query(refund));

        assertEquals(ErrorCode.REFUND_CHANNEL_QUERY_FAILED, exception.getCode());
        assertEquals("refund channel query adapter selection failed; query before retrying", exception.getMessage());
        assertNull(exception.getCause());
        assertEquals(0, exception.getStackTrace().length);
    }

    private RefundCallbackSourceDTO refund(String channel) {
        RefundCallbackSourceDTO refund = new RefundCallbackSourceDTO();
        refund.setRefundOrderId("REF-001");
        refund.setChannelCode(channel);
        refund.setChannelRefundNo("CHANNEL-REF-001");
        refund.setStatus("PROCESSING");
        return refund;
    }

    private RefundChannelQueryResultDTO result(String status, String channelRefundNo, String source) {
        RefundChannelQueryResultDTO result = new RefundChannelQueryResultDTO();
        result.setRefundStatus(status);
        result.setChannelRefundNo(channelRefundNo);
        result.setQuerySource(source);
        return result;
    }

    private String repeat(char value, int count) {
        char[] values = new char[count];
        java.util.Arrays.fill(values, value);
        return new String(values);
    }
}
