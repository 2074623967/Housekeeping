package com.abc123.hsp.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PaymentSensitiveDataMaskerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PaymentSensitiveDataMasker masker = new PaymentSensitiveDataMasker(objectMapper);

    @Test
    void shouldMaskNestedArraysCaseVariantsAndPaymentIdentifiers() throws Exception {
        String payload = "{\"items\":[{\"PayerPhone\":\"13800138000\",\"clientDeviceId\":\"DEVICE-12345678\"}],"
                + "\"profile\":{\"CLIENTIP\":\"10.20.30.40\",\"BankCardNo\":\"6222021234567890123\"},"
                + "\"idempotencyKey\":\"ORDER-REQUEST-20260803-ABCDEFG\"}";

        JsonNode masked = objectMapper.readTree(masker.maskPayload(payload));

        assertEquals("138****8000", masked.at("/items/0/PayerPhone").asText());
        assertEquals("DEVI****5678", masked.at("/items/0/clientDeviceId").asText());
        assertEquals("10.20.*.*", masked.at("/profile/CLIENTIP").asText());
        assertEquals("622202******0123", masked.at("/profile/BankCardNo").asText());
        assertEquals("ORDER-****DEFG", masked.get("idempotencyKey").asText());
    }

    @Test
    void shouldMaskNonStringSensitiveValuesAndNestedRawPayloadFields() throws Exception {
        String payload = "{\"phone\":13800138000,\"nested\":{\"requestPayload\":{\"secret\":true}},"
                + "\"responsePayload\":[1,2,3]}";

        JsonNode masked = objectMapper.readTree(masker.maskPayload(payload));

        assertEquals("****", masked.get("phone").asText());
        assertEquals("[REDACTED]", masked.at("/nested/requestPayload").asText());
        assertEquals("[REDACTED]", masked.get("responsePayload").asText());
    }

    @Test
    void shouldFailClosedForMalformedPayload() {
        String malformed = "payerPhone=13800138000&clientDeviceId=DEVICE-12345678";

        String masked = masker.maskPayload(malformed);

        assertEquals("[REDACTED]", masked);
        assertFalse(masked.contains("13800138000"));
        assertFalse(masked.contains("DEVICE-12345678"));
    }

    @Test
    void shouldFailClosedForScalarJsonPayload() {
        assertEquals("[REDACTED]", masker.maskPayload("\"13800138000\""));
        assertEquals("[REDACTED]", masker.maskPayload("13800138000"));
    }

    @Test
    void shouldRetainOrdinaryJsonValues() {
        String masked = masker.maskPayload("{\"amount\":6800,\"code\":\"SUCCESS\"}");

        assertTrue(masked.contains("\"amount\":6800"));
        assertTrue(masked.contains("\"code\":\"SUCCESS\""));
    }

    @Test
    void shouldMaskUserReferenceWithoutRetainingNameSuffix() {
        assertEquals("user-****", masker.maskUserReference(" user-张女士 "));
        assertEquals("****", masker.maskUserReference("张女士"));
        assertEquals(null, masker.maskUserReference(null));
    }
}
