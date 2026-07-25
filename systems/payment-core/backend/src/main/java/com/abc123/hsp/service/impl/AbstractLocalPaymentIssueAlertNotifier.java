package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentIssueAlertDeliveryResultDTO;
import com.abc123.hsp.dto.PaymentIssueAlertDispatchItemDTO;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * 本地告警通知器抽象基类，统一生成供应商投递回执。
 */
abstract class AbstractLocalPaymentIssueAlertNotifier {

    /**
     * 为外部 HTTP/Webhook 通知器配置统一的连接与读取超时，避免网关长时间阻塞任务线程。
     */
    protected static RestTemplate buildRestTemplate(int timeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        return new RestTemplate(requestFactory);
    }

    /**
     * 构造本地模拟告警供应商投递结果。
     */
    protected PaymentIssueAlertDeliveryResultDTO buildLocalDeliveryResult(PaymentIssueAlertDispatchItemDTO item,
                                                                         String providerStatus,
                                                                         String channelLabel) {
        PaymentIssueAlertDeliveryResultDTO result = new PaymentIssueAlertDeliveryResultDTO();
        result.setProviderReceiptNo(buildReceiptNo(channelLabel, item.getIssueNo()));
        result.setProviderDeliveryStatus(providerStatus);
        result.setProviderDeliveryMessage(buildDeliveryMessage(item, channelLabel));
        result.setRenderedContentSnapshot(StringUtils.hasText(item.getRenderedAlertContent())
                ? item.getRenderedAlertContent()
                : buildRenderedContent(item, channelLabel));
        return result;
    }

    private String buildReceiptNo(String channelLabel, String issueNo) {
        String issueSuffix = StringUtils.hasText(issueNo) ? issueNo.replaceAll("[^A-Za-z0-9]", "") : "UNKNOWN";
        if (issueSuffix.length() > 16) {
            issueSuffix = issueSuffix.substring(issueSuffix.length() - 16);
        }
        return channelLabel + "-" + issueSuffix;
    }

    private String buildDeliveryMessage(PaymentIssueAlertDispatchItemDTO item, String channelLabel) {
        StringBuilder builder = new StringBuilder("本地");
        builder.append(channelLabel).append("通知器已受理");
        if (StringUtils.hasText(item.getProviderName())) {
            builder.append("，供应商=").append(item.getProviderName());
        }
        if (StringUtils.hasText(item.getTemplateCode())) {
            builder.append("，模板=").append(item.getTemplateCode());
        }
        return builder.toString();
    }

    private String buildRenderedContent(PaymentIssueAlertDispatchItemDTO item, String channelLabel) {
        StringBuilder builder = new StringBuilder();
        builder.append("[").append(channelLabel).append("告警]");
        if (StringUtils.hasText(item.getSeverity())) {
            builder.append("[").append(item.getSeverity()).append("]");
        }
        if (StringUtils.hasText(item.getIssueType())) {
            builder.append(item.getIssueType()).append(" - ");
        }
        if (StringUtils.hasText(item.getAlertContent())) {
            builder.append(item.getAlertContent());
        }
        if (StringUtils.hasText(item.getScheduleTag())) {
            builder.append("（班次：").append(item.getScheduleTag()).append("）");
        }
        return builder.toString();
    }
}
