package com.abc123.hsp.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PaymentChannelAmountJsonTest {

    private static final BigDecimal MAXIMUM_AMOUNT = new BigDecimal("9999999999999999.99");

    @Test
    void shouldSerializeExactChannelDailyLimitAsString() {
        PaymentChannelConfigDTO channel = new PaymentChannelConfigDTO();
        channel.setDailyLimitValue(MAXIMUM_AMOUNT);

        JsonNode payload = new ObjectMapper().valueToTree(channel);

        Assertions.assertTrue(payload.get("dailyLimitValue").isTextual());
        Assertions.assertEquals("9999999999999999.99", payload.get("dailyLimitValue").asText());
    }

    @Test
    void shouldSerializeExactChannelVersionDailyLimitAsString() {
        PaymentChannelVersionDTO version = new PaymentChannelVersionDTO();
        version.setDailyLimit(MAXIMUM_AMOUNT);

        JsonNode payload = new ObjectMapper().valueToTree(version);

        Assertions.assertTrue(payload.get("dailyLimit").isTextual());
        Assertions.assertEquals("9999999999999999.99", payload.get("dailyLimit").asText());
    }

    @Test
    void shouldDeserializeExactChannelDailyLimitFromString() throws Exception {
        PaymentChannelReviewSubmitRequestDTO request = new ObjectMapper().readValue(
                "{\"candidate\":{\"dailyLimit\":\"9999999999999999.99\"}}",
                PaymentChannelReviewSubmitRequestDTO.class);

        Assertions.assertEquals(MAXIMUM_AMOUNT, request.getCandidate().getDailyLimit());
    }
}
