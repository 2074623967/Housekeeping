package com.abc123.riskcontrol.service.impl;

import com.abc123.riskcontrol.dto.RiskOpsConfigSnapshotDTO;
import com.abc123.riskcontrol.service.RiskOpsConfigService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 风控系统读取运营配置有效快照的 HTTP 实现。
 */
@Service
public class RiskOpsConfigServiceImpl implements RiskOpsConfigService {

    private final boolean enabled;
    private final String baseUrl;
    private final RestTemplate restTemplate;

    @Autowired
    public RiskOpsConfigServiceImpl(
            @Value("${risk-control.ops-config.enabled:false}") boolean enabled,
            @Value("${risk-control.ops-config.snapshot-url:}") String baseUrl,
            @Value("${risk-control.ops-config.timeout-ms:2000}") int timeoutMs) {
        this(enabled, baseUrl, buildRestTemplate(timeoutMs));
    }

    RiskOpsConfigServiceImpl(boolean enabled, String baseUrl, RestTemplate restTemplate) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.restTemplate = restTemplate;
    }

    @Override
    public RiskOpsConfigSnapshotDTO loadEffectiveSnapshot(String businessCode, String payType, String terminalType) {
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

    private static RestTemplate buildRestTemplate(int timeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        return new RestTemplate(requestFactory);
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
        private RiskOpsConfigSnapshotDTO data;
    }
}
