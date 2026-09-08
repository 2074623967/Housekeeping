package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentChannelQueryResultDTO;
import com.abc123.hsp.dto.PaymentChannelReturnCodeConfigDTO;
import com.abc123.hsp.dto.PaymentChannelSubmitResultDTO;
import com.abc123.hsp.mapper.PaymentConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PaymentChannelReturnCodeStandardizerTest {

    @Test
    void shouldApplyActiveMappingAndEnrichSubmitPayload() throws Exception {
        PaymentConfigMapper mapper = Mockito.mock(PaymentConfigMapper.class);
        when(mapper.findActiveReturnCodeMapping("wx_h5", "USERPAYING"))
                .thenReturn(mapping());
        PaymentChannelSubmitResultDTO result = new PaymentChannelSubmitResultDTO();
        result.setChannelReturnCode(" USERPAYING ");
        result.setResponsePayload("{\"gatewayCode\":\"wechat-gateway\"}");

        new PaymentChannelReturnCodeStandardizer(mapper, new ObjectMapper())
                .standardizeSubmit(" wx_h5 ", result);

        assertEquals("USERPAYING", result.getChannelReturnCode());
        assertEquals("PAYMENT-CHANNEL-1001", result.getStandardizedCode());
        assertEquals("v2026.07.1", result.getMappingVersion());
        assertTrue(result.getReturnCodeMappingMatched());
        java.util.Map<?, ?> payload = new ObjectMapper().readValue(result.getResponsePayload(), java.util.Map.class);
        assertEquals("wechat-gateway", payload.get("gatewayCode"));
        assertEquals("PAYMENT-CHANNEL-1001", payload.get("standardizedCode"));
        assertEquals(Boolean.TRUE, payload.get("returnCodeMappingMatched"));
    }

    @Test
    void shouldUseConservativeFallbackForUnknownQueryCode() {
        PaymentConfigMapper mapper = Mockito.mock(PaymentConfigMapper.class);
        PaymentChannelQueryResultDTO result = new PaymentChannelQueryResultDTO();
        result.setChannelReturnCode("NEW_PROVIDER_CODE");

        new PaymentChannelReturnCodeStandardizer(mapper, new ObjectMapper())
                .standardizeQuery("alipay_h5", result);

        assertEquals("PAYMENT-CHANNEL-UNMAPPED", result.getStandardizedCode());
        assertEquals("未知", result.getRetryable());
        assertEquals("是", result.getManualInterventionRequired());
        assertFalse(result.getReturnCodeMappingMatched());
    }

    @Test
    void shouldNotRequireManualInterventionForUnmappedSuccessfulQuery() {
        PaymentConfigMapper mapper = Mockito.mock(PaymentConfigMapper.class);
        PaymentChannelQueryResultDTO result = new PaymentChannelQueryResultDTO();
        result.setTradeStatus("SUCCESS");
        result.setChannelReturnCode("SUCCESS");

        new PaymentChannelReturnCodeStandardizer(mapper, new ObjectMapper())
                .standardizeQuery("wx_h5", result);

        assertEquals("PAYMENT-CHANNEL-SUCCESS", result.getStandardizedCode());
        assertEquals("否", result.getRetryable());
        assertEquals("否", result.getManualInterventionRequired());
        assertFalse(result.getReturnCodeMappingMatched());
    }

    @Test
    void shouldNotFailChannelResultWhenMappingLookupFails() {
        PaymentConfigMapper mapper = Mockito.mock(PaymentConfigMapper.class);
        when(mapper.findActiveReturnCodeMapping("wx_h5", "USERPAYING"))
                .thenThrow(new IllegalStateException("mapping database unavailable"));
        PaymentChannelSubmitResultDTO result = new PaymentChannelSubmitResultDTO();
        result.setChannelReturnCode("USERPAYING");
        result.setResponsePayload("not-json");

        new PaymentChannelReturnCodeStandardizer(mapper, new ObjectMapper())
                .standardizeSubmit("wx_h5", result);

        assertEquals("PAYMENT-CHANNEL-UNMAPPED", result.getStandardizedCode());
        assertFalse(result.getReturnCodeMappingMatched());
        assertTrue(result.getResponsePayload().contains("\"rawResponse\":\"not-json\""));
    }

    private PaymentChannelReturnCodeConfigDTO mapping() {
        PaymentChannelReturnCodeConfigDTO mapping = new PaymentChannelReturnCodeConfigDTO();
        mapping.setStandardizedCode("PAYMENT-CHANNEL-1001");
        mapping.setStandardizedMessage("用户支付中，需继续轮询或等待回调");
        mapping.setHandlingSuggestion("展示处理中并触发主动查单");
        mapping.setRetryable("是");
        mapping.setManualInterventionRequired("否");
        mapping.setMappingVersion("v2026.07.1");
        return mapping;
    }
}
