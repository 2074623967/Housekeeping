package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentRiskDecisionRequestDTO;
import com.abc123.hsp.dto.PaymentRiskDecisionResultDTO;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.client.RestTemplate;

class PaymentRiskControlServiceImplTest {

    @Test
    void shouldReturnLocalPassResultWhenRiskControlIsDisabled() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);

        PaymentRiskDecisionResultDTO result = new PaymentRiskControlServiceImpl(
                false, "http://risk/decisions/evaluate", restTemplate)
                .evaluateSubmitRisk(new PaymentRiskDecisionRequestDTO());

        assertEquals("PASS", result.getDecision());
        assertEquals("success", result.getDecisionType());
        verify(restTemplate, never()).postForObject(any(String.class), any(), any());
    }

    @Test
    void shouldFailClosedWhenEnabledRiskControlHasNoEvaluateUrl() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentRiskControlServiceImpl(true, "   ", restTemplate)
                        .evaluateSubmitRisk(new PaymentRiskDecisionRequestDTO()));

        assertEquals(ErrorCode.PAYMENT_RISK_SERVICE_UNAVAILABLE, exception.getCode());
        assertEquals("风控中心评估地址未配置，请联系资金风控团队", exception.getMessage());
        verify(restTemplate, never()).postForObject(any(String.class), any(), any());
    }

    @Test
    void shouldRejectInvalidRequestBeforeHttpCall() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        PaymentRiskControlServiceImpl service = service(restTemplate);

        assertInvalidRiskRequest(assertThrows(
                BusinessException.class,
                () -> service.evaluateSubmitRisk(null)));

        PaymentRiskDecisionRequestDTO missingRequired = validRequest();
        missingRequired.setBusinessNo("   ");
        assertInvalidRiskRequest(assertThrows(
                BusinessException.class,
                () -> service.evaluateSubmitRisk(missingRequired)));

        PaymentRiskDecisionRequestDTO missingSourceApp = validRequest();
        missingSourceApp.setSourceAppId("   ");
        assertInvalidRiskRequest(assertThrows(
                BusinessException.class,
                () -> service.evaluateSubmitRisk(missingSourceApp)));

        PaymentRiskDecisionRequestDTO invalidOptional = validRequest();
        invalidOptional.setClientDeviceId("device\n001");
        assertInvalidRiskRequest(assertThrows(
                BusinessException.class,
                () -> service.evaluateSubmitRisk(invalidOptional)));

        PaymentRiskDecisionRequestDTO oversized = validRequest();
        oversized.setMerchantNo(repeat("M", 128) + " ");
        assertInvalidRiskRequest(assertThrows(
                BusinessException.class,
                () -> service.evaluateSubmitRisk(oversized)));

        BigDecimal[] invalidAmounts = {
                null,
                BigDecimal.ZERO,
                new BigDecimal("1.001"),
                new BigDecimal("10000000000000000.00")
        };
        for (BigDecimal invalidAmount : invalidAmounts) {
            PaymentRiskDecisionRequestDTO request = validRequest();
            request.setAmount(invalidAmount);
            assertInvalidRiskRequest(assertThrows(
                    BusinessException.class,
                    () -> service.evaluateSubmitRisk(request)));
        }

        verify(restTemplate, never()).postForObject(any(String.class), any(), any());
    }

    @Test
    void shouldSendNormalizedRequestCopy() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        stubResponse(restTemplate, "0", decision("PASS", "success"));
        PaymentRiskDecisionRequestDTO request = validRequest();
        request.setBusinessNo(" PAY-001 ");
        request.setSourceAppId(" housekeeping-app-web ");
        request.setClientDeviceId("   ");
        request.setPayerPhone(" 13800138000 ");

        service(restTemplate).evaluateSubmitRisk(request);

        ArgumentCaptor<PaymentRiskDecisionRequestDTO> captor =
                ArgumentCaptor.forClass(PaymentRiskDecisionRequestDTO.class);
        verify(restTemplate, times(1)).postForObject(
                eq("http://risk/decisions/evaluate"),
                captor.capture(),
                eq(PaymentRiskControlServiceImpl.RiskApiResponse.class));
        PaymentRiskDecisionRequestDTO sent = captor.getValue();
        assertNotSame(request, sent);
        assertEquals("PAY-001", sent.getBusinessNo());
        assertEquals("housekeeping-app-web", sent.getSourceAppId());
        assertNull(sent.getClientDeviceId());
        assertEquals("13800138000", sent.getPayerPhone());
        assertEquals(" PAY-001 ", request.getBusinessNo());
        assertEquals(" housekeeping-app-web ", request.getSourceAppId());
        assertEquals("   ", request.getClientDeviceId());
    }

    @Test
    void shouldNormalizeSuccessfulRiskDecision() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        PaymentRiskDecisionResultDTO data = decision(" pass ", " SUCCESS ");
        data.setHitCode(" POLICY_001 ");
        data.setRiskTag("   ");
        data.setReviewNo(" REVIEW-001 ");
        data.setEventNo(" RISK-EVT-001 ");
        data.setMessage(" allow payment ");
        stubResponse(restTemplate, " 0 ", data);

        PaymentRiskDecisionResultDTO result = service(restTemplate)
                .evaluateSubmitRisk(validRequest());

        assertEquals("PASS", result.getDecision());
        assertEquals("success", result.getDecisionType());
        assertEquals("POLICY_001", result.getHitCode());
        assertNull(result.getRiskTag());
        assertEquals("REVIEW-001", result.getReviewNo());
        assertEquals("RISK-EVT-001", result.getEventNo());
        assertEquals("风控准入通过", result.getMessage());
    }

    @Test
    void shouldReplaceExternalDecisionMessageWithPlatformText() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        PaymentRiskDecisionResultDTO data = decision("REVIEW", "warn");
        data.setReviewNo("REVIEW-001");
        data.setMessage("jdbc:mysql://risk.internal/payment password=risk-secret-token");
        stubResponse(restTemplate, "0", data);

        PaymentRiskDecisionResultDTO result = service(restTemplate)
                .evaluateSubmitRisk(validRequest());

        assertEquals("支付需要人工风控复核", result.getMessage());
        org.junit.jupiter.api.Assertions.assertFalse(result.getMessage().contains("risk-secret-token"));
        org.junit.jupiter.api.Assertions.assertFalse(result.getMessage().contains("jdbc:mysql"));
    }

    @Test
    void shouldRejectReviewDecisionWithoutReviewOrderReference() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        PaymentRiskDecisionResultDTO data = decision("REVIEW", "warn");
        data.setEventNo("RISK-EVT-001");
        stubResponse(restTemplate, "0", data);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service(restTemplate).evaluateSubmitRisk(validRequest()));

        assertInvalidRiskResult(exception);
    }

    @Test
    void shouldRejectInterceptDecisionWithoutRiskEventReference() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        PaymentRiskDecisionResultDTO data = decision("INTERCEPT", "danger");
        data.setHitCode("DEVICE_BLOCKLIST");
        stubResponse(restTemplate, "0", data);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service(restTemplate).evaluateSubmitRisk(validRequest()));

        assertInvalidRiskResult(exception);
    }

    @Test
    void shouldRejectInterceptDecisionWithoutHitCode() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        PaymentRiskDecisionResultDTO data = decision("INTERCEPT", "danger");
        data.setEventNo("RISK-EVT-001");
        stubResponse(restTemplate, "0", data);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service(restTemplate).evaluateSubmitRisk(validRequest()));

        assertInvalidRiskResult(exception);
    }

    @Test
    void shouldNormalizeInterceptDecisionWithRiskEventReference() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        PaymentRiskDecisionResultDTO data = decision(" intercept ", " DANGER ");
        data.setHitCode(" DEVICE_BLOCKLIST ");
        data.setEventNo(" RISK-EVT-002 ");
        stubResponse(restTemplate, "0", data);

        PaymentRiskDecisionResultDTO result = service(restTemplate)
                .evaluateSubmitRisk(validRequest());

        assertEquals("INTERCEPT", result.getDecision());
        assertEquals("danger", result.getDecisionType());
        assertEquals("DEVICE_BLOCKLIST", result.getHitCode());
        assertEquals("RISK-EVT-002", result.getEventNo());
        assertEquals("支付被风控拦截", result.getMessage());
    }

    @Test
    void shouldRejectDecisionTypeThatContradictsDecision() {
        String[][] invalidPairs = {
                {"PASS", "danger"},
                {"REVIEW", "success"},
                {"INTERCEPT", "warn"},
                {"REJECT", "info"}
        };

        for (String[] invalidPair : invalidPairs) {
            RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
            stubResponse(restTemplate, "0", decision(invalidPair[0], invalidPair[1]));

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> service(restTemplate).evaluateSubmitRisk(validRequest()));

            assertInvalidRiskResult(exception);
        }
    }

    @Test
    void shouldNormalizeRejectDecisionWithDangerType() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        PaymentRiskDecisionResultDTO data = decision(" reject ", " DANGER ");
        data.setReviewNo(" REVIEW-REJECTED-001 ");
        stubResponse(restTemplate, "0", data);

        PaymentRiskDecisionResultDTO result = service(restTemplate)
                .evaluateSubmitRisk(validRequest());

        assertEquals("REJECT", result.getDecision());
        assertEquals("danger", result.getDecisionType());
        assertEquals("REVIEW-REJECTED-001", result.getReviewNo());
        assertEquals("支付被风控拒绝", result.getMessage());
    }

    @Test
    void shouldRejectRejectDecisionWithoutTraceReference() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        stubResponse(restTemplate, "0", decision("REJECT", "danger"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service(restTemplate).evaluateSubmitRisk(validRequest()));

        assertInvalidRiskResult(exception);
    }

    @Test
    void shouldNormalizeDirectRejectDecisionWithRiskEventReference() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        PaymentRiskDecisionResultDTO data = decision(" reject ", " DANGER ");
        data.setHitCode(" POLICY_DIRECT_REJECT ");
        data.setEventNo(" RISK-EVT-REJECT-001 ");
        stubResponse(restTemplate, "0", data);

        PaymentRiskDecisionResultDTO result = service(restTemplate)
                .evaluateSubmitRisk(validRequest());

        assertEquals("REJECT", result.getDecision());
        assertEquals("POLICY_DIRECT_REJECT", result.getHitCode());
        assertEquals("RISK-EVT-REJECT-001", result.getEventNo());
        assertNull(result.getReviewNo());
        assertEquals("支付被风控拒绝", result.getMessage());
    }

    @Test
    void shouldRejectDirectRejectDecisionWithoutHitCode() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        PaymentRiskDecisionResultDTO data = decision("REJECT", "danger");
        data.setEventNo("RISK-EVT-REJECT-001");
        stubResponse(restTemplate, "0", data);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service(restTemplate).evaluateSubmitRisk(validRequest()));

        assertInvalidRiskResult(exception);
    }

    @Test
    void shouldRejectBusinessFailureEvenWhenResponseCarriesPassData() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        stubResponse(restTemplate, "RISK-FAILED", decision("PASS", "success"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service(restTemplate).evaluateSubmitRisk(validRequest()));

        assertInvalidRiskResult(exception);
    }

    @Test
    void shouldRejectUnknownDecisionOrDecisionType() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        stubResponse(restTemplate, "0", decision("ALLOW", "success"));
        BusinessException unknownDecision = assertThrows(
                BusinessException.class,
                () -> service(restTemplate).evaluateSubmitRisk(validRequest()));
        assertInvalidRiskResult(unknownDecision);

        stubResponse(restTemplate, "0", decision("PASS", "primary"));
        BusinessException unknownType = assertThrows(
                BusinessException.class,
                () -> service(restTemplate).evaluateSubmitRisk(validRequest()));
        assertInvalidRiskResult(unknownType);
    }

    @Test
    void shouldRejectControlCharactersAndOversizedFields() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        PaymentRiskDecisionResultDTO controlCharacter = decision("PASS", "success");
        controlCharacter.setMessage("allow\npayment");
        stubResponse(restTemplate, "0", controlCharacter);
        BusinessException controlException = assertThrows(
                BusinessException.class,
                () -> service(restTemplate).evaluateSubmitRisk(validRequest()));
        assertInvalidRiskResult(controlException);

        PaymentRiskDecisionResultDTO oversized = decision("PASS", "success");
        oversized.setRiskTag(repeat("A", 128) + " ");
        stubResponse(restTemplate, "0", oversized);
        BusinessException oversizedException = assertThrows(
                BusinessException.class,
                () -> service(restTemplate).evaluateSubmitRisk(validRequest()));
        assertInvalidRiskResult(oversizedException);
    }

    @Test
    void shouldUseFixedFailureWhenHttpCallThrows() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        when(restTemplate.postForObject(
                eq("http://risk/decisions/evaluate"),
                any(PaymentRiskDecisionRequestDTO.class),
                eq(PaymentRiskControlServiceImpl.RiskApiResponse.class)))
                .thenThrow(new IllegalStateException("remote secret detail"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service(restTemplate).evaluateSubmitRisk(validRequest()));

        assertEquals(ErrorCode.PAYMENT_RISK_SERVICE_UNAVAILABLE, exception.getCode());
        assertEquals("风控中心调用失败，请稍后重试或联系资金风控团队", exception.getMessage());
    }

    private PaymentRiskControlServiceImpl service(RestTemplate restTemplate) {
        return new PaymentRiskControlServiceImpl(
                true, " http://risk/decisions/evaluate ", restTemplate);
    }

    private void stubResponse(
            RestTemplate restTemplate,
            String code,
            PaymentRiskDecisionResultDTO data) {
        PaymentRiskControlServiceImpl.RiskApiResponse response =
                new PaymentRiskControlServiceImpl.RiskApiResponse();
        response.setCode(code);
        response.setData(data);
        when(restTemplate.postForObject(
                eq("http://risk/decisions/evaluate"),
                any(PaymentRiskDecisionRequestDTO.class),
                eq(PaymentRiskControlServiceImpl.RiskApiResponse.class)))
                .thenReturn(response);
    }

    private PaymentRiskDecisionResultDTO decision(String decision, String decisionType) {
        PaymentRiskDecisionResultDTO result = new PaymentRiskDecisionResultDTO();
        result.setDecision(decision);
        result.setDecisionType(decisionType);
        return result;
    }

    private void assertInvalidRiskResult(BusinessException exception) {
        assertEquals(ErrorCode.PAYMENT_RISK_SERVICE_UNAVAILABLE, exception.getCode());
        assertEquals("风控中心返回结果非法，无法确认支付准入状态", exception.getMessage());
    }

    private void assertInvalidRiskRequest(BusinessException exception) {
        assertEquals(ErrorCode.PAYMENT_RISK_SERVICE_UNAVAILABLE, exception.getCode());
        assertEquals("风控中心请求参数非法，无法发起支付准入评估", exception.getMessage());
    }

    private PaymentRiskDecisionRequestDTO validRequest() {
        PaymentRiskDecisionRequestDTO request = new PaymentRiskDecisionRequestDTO();
        request.setBusinessNo("PAY-001");
        request.setSourceSystem("payment-core");
        request.setSourceAppId("housekeeping-app-web");
        request.setSceneCode("PAY_CONSUME");
        request.setPayScene("HOME_SERVICE");
        request.setPaymentMethod("WECHAT");
        request.setChannelCode("wxpay");
        request.setMerchantNo("MERCHANT-001");
        request.setTerminal("APP");
        request.setClientIp("127.0.0.1");
        request.setClientDeviceId("DEVICE-001");
        request.setPayerPhone("13800138000");
        request.setAmount(new BigDecimal("88.00"));
        return request;
    }

    private String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
