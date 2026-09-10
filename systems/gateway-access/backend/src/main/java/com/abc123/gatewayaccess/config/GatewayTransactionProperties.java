package com.abc123.gatewayaccess.config;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 交易代理配置。访问凭证和真实上游地址必须由部署环境显式提供。
 */
@Data
@Component
@ConfigurationProperties(prefix = "gateway.transaction")
public class GatewayTransactionProperties {

    private String appCode = "APP_PAY_CORE";
    private String permissionCode = "PERM_PAYMENT_CORE";
    private String accessKey;
    private String environment = "PROD";
    private int timeoutMs = 2000;
    private Map<String, Channel> channels = new LinkedHashMap<>();

    @Data
    public static class Channel {

        private String gatewayCode;
        private String paymentQueryUrl;
    }
}
