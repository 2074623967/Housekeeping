package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentRiskDecisionRequestDTO;
import com.abc123.hsp.dto.PaymentRiskDecisionResultDTO;
import com.abc123.hsp.service.PaymentRiskControlService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * 风控中心 HTTP 联动实现。
 */
@Service
public class PaymentRiskControlServiceImpl implements PaymentRiskControlService {

    private final boolean enabled;
    private final String evaluateUrl;
    private final RestTemplate restTemplate;

    public PaymentRiskControlServiceImpl(
            @Value("${payment.risk-control.enabled:false}") boolean enabled,
            @Value("${payment.risk-control.evaluate-url:}") String evaluateUrl,
            @Value("${payment.risk-control.timeout-ms:2000}") int timeoutMs) {
        this(enabled, evaluateUrl, AbstractLocalPaymentIssueAlertNotifier.buildRestTemplate(timeoutMs));
    }

    PaymentRiskControlServiceImpl(boolean enabled, String evaluateUrl, RestTemplate restTemplate) {
        this.enabled = enabled;
        this.evaluateUrl = evaluateUrl;
        this.restTemplate = restTemplate;
    }

    @Override
    public PaymentRiskDecisionResultDTO evaluateSubmitRisk(PaymentRiskDecisionRequestDTO request) {
        if (!enabled || !StringUtils.hasText(evaluateUrl)) {
            return passResult("未启用独立风控中心联动，当前按本地放行口径继续处理");
        }
        try {
            RiskApiResponse response = restTemplate.postForObject(
                    evaluateUrl.trim(),
                    request,
                    RiskApiResponse.class);
            if (response == null || response.getData() == null) {
                throw new BusinessException(ErrorCode.PAYMENT_RISK_SERVICE_UNAVAILABLE, "风控中心返回空结果，无法确认支付准入状态");
            }
            return response.getData();
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.PAYMENT_RISK_SERVICE_UNAVAILABLE, "风控中心调用失败，请稍后重试或联系资金风控团队");
        }
    }

    private PaymentRiskDecisionResultDTO passResult(String message) {
        PaymentRiskDecisionResultDTO result = new PaymentRiskDecisionResultDTO();
        result.setDecision("PASS");
        result.setDecisionType("success");
        result.setMessage(message);
        return result;
    }

    /**
     * 对齐风控中心统一响应结构。
     */
    public static class RiskApiResponse {

        /** 返回码。 */
        private String code;
        /** 返回消息。 */
        private String message;
        /** 决策结果。 */
        private PaymentRiskDecisionResultDTO data;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public PaymentRiskDecisionResultDTO getData() {
            return data;
        }

        public void setData(PaymentRiskDecisionResultDTO data) {
            this.data = data;
        }
    }
}
