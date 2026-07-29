package com.abc123.hsp.service.impl;

import java.util.StringJoiner;

/**
 * 渠道模拟响应报文拼装工具，避免各适配器重复手写字符串。
 */
final class ChannelPayloadSupport {

    private ChannelPayloadSupport() {
    }

    /**
     * 构造统一 JSON 报文片段，便于后续平滑切换真实渠道响应结构。
     */
    static String buildSuccessPayload(String channelCode,
                                      String channelTransactionNo,
                                      String gatewayCode,
                                      String message) {
        return new StringJoiner(",", "{", "}")
                .add("\"code\":\"SUCCESS\"")
                .add("\"channelCode\":\"" + channelCode + "\"")
                .add("\"gatewayCode\":\"" + gatewayCode + "\"")
                .add("\"channelTransactionNo\":\"" + channelTransactionNo + "\"")
                .add("\"message\":\"" + message + "\"")
                .toString();
    }
}
