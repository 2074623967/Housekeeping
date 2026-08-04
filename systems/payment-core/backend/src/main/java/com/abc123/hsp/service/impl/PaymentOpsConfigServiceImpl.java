package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentOpsConfigSnapshotDTO;
import com.abc123.hsp.service.PaymentOpsConfigService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 运营配置快照 HTTP 联动实现。
 */
@Service
public class PaymentOpsConfigServiceImpl implements PaymentOpsConfigService {

    private final boolean enabled;
    private final String baseUrl;
    private final RestTemplate restTemplate;

    public PaymentOpsConfigServiceImpl(
            @Value("${payment.ops-config.enabled:false}") boolean enabled,
            @Value("${payment.ops-config.snapshot-url:}") String baseUrl,
            @Value("${payment.ops-config.timeout-ms:2000}") int timeoutMs) {
        this(enabled, baseUrl, AbstractLocalPaymentIssueAlertNotifier.buildRestTemplate(timeoutMs));
    }

    PaymentOpsConfigServiceImpl(boolean enabled, String baseUrl, RestTemplate restTemplate) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.restTemplate = restTemplate;
    }

    @Override
    public PaymentOpsConfigSnapshotDTO loadEffectiveSnapshot(String businessCode, String payType, String terminalType) {
        if (!enabled || !StringUtils.hasText(baseUrl) || !StringUtils.hasText(businessCode)
                || !StringUtils.hasText(payType) || !StringUtils.hasText(terminalType)) {
            return null;
        }
        String requestUrl = UriComponentsBuilder.fromHttpUrl(baseUrl.trim())
                .queryParam("businessCode", businessCode.trim())
                .queryParam("payType", payType.trim())
                .queryParam("terminalType", terminalType.trim())
                .build()
                .toUriString();
        try {
            OpsConfigApiResponse response = restTemplate.getForObject(requestUrl, OpsConfigApiResponse.class);
            return response == null ? null : response.getData();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * 对齐运营配置系统统一响应结构。
     */
    @Data
    public static class OpsConfigApiResponse {

        /** 返回码。 */
        private String code;
        /** 返回消息。 */
        private String message;
        /** 快照数据。 */
        private PaymentOpsConfigSnapshotDTO data;
    }
}
