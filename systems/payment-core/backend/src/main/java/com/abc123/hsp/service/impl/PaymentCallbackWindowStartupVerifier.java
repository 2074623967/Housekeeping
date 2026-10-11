package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentChannelConfigDTO;
import com.abc123.hsp.mapper.PaymentConfigMapper;
import java.util.List;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 验签开启时校验存量启用渠道的 nonce 保留期。
 */
@Component
public class PaymentCallbackWindowStartupVerifier implements SmartInitializingSingleton {

    private final PaymentConfigMapper paymentConfigMapper;
    private final boolean requireSignature;
    private final long nonceTtlSeconds;

    public PaymentCallbackWindowStartupVerifier(
            PaymentConfigMapper paymentConfigMapper,
            @Value("${payment.callback.require-signature:true}") boolean requireSignature,
            @Value("${payment.callback.nonce-ttl-seconds:3601}") long nonceTtlSeconds) {
        this.paymentConfigMapper = paymentConfigMapper;
        this.requireSignature = requireSignature;
        this.nonceTtlSeconds = nonceTtlSeconds;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!requireSignature) {
            return;
        }
        List<PaymentChannelConfigDTO> channels = paymentConfigMapper.findChannels();
        if (channels == null) {
            throw new IllegalStateException("payment callback channel configuration cannot be verified");
        }
        for (PaymentChannelConfigDTO channel : channels) {
            if (channel == null) {
                throw new IllegalStateException("payment callback channel configuration cannot be verified");
            }
            if ("DISABLED".equals(channel.getStatus())) {
                continue;
            }
            Integer windowSeconds = channel.getNotifySignWindowSec();
            if (windowSeconds == null || windowSeconds.intValue() <= 0
                    || nonceTtlSeconds <= windowSeconds.longValue() * 2L) {
                throw new IllegalStateException(
                        "payment callback nonce TTL must outlast every enabled channel signature window");
            }
        }
    }
}
