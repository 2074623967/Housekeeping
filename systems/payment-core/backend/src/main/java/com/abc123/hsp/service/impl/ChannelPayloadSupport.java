package com.abc123.hsp.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 渠道模拟响应报文拼装工具，避免各适配器重复手写字符串。
 */
final class ChannelPayloadSupport {

    private ChannelPayloadSupport() {
    }

    static String buildSimulatedChannelTransactionNo(String prefix) {
        return prefix
                + System.currentTimeMillis()
                + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 构造统一 JSON 报文片段，便于后续平滑切换真实渠道响应结构。
     */
    static String buildSuccessPayload(String channelCode,
                                      String channelTransactionNo,
                                      String gatewayCode,
                                      String message) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("code", "SUCCESS");
        payload.put("channelCode", channelCode);
        payload.put("gatewayCode", gatewayCode);
        payload.put("channelTransactionNo", channelTransactionNo);
        payload.put("message", message);
        try {
            return new ObjectMapper().writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("模拟支付渠道响应序列化失败", exception);
        }
    }
}
