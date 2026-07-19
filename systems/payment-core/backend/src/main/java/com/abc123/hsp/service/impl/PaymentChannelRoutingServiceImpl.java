package com.abc123.hsp.service.impl;

import com.abc123.hsp.service.PaymentChannelRoutingService;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 支付渠道路由默认实现。
 *
 * <p>当前使用配置在代码中的家政场景默认映射，后续由 ops-config-system
 * 提供动态路由规则后替换为配置驱动实现。</p>
 */
@Service
public class PaymentChannelRoutingServiceImpl implements PaymentChannelRoutingService {

    @Override
    public String resolve(String paymentMethod, String requestedChannelCode) {
        String normalizedMethod = paymentMethod == null
                ? ""
                : paymentMethod.trim().toLowerCase(Locale.ROOT);
        String normalizedChannel = requestedChannelCode == null
                ? ""
                : requestedChannelCode.trim().toLowerCase(Locale.ROOT);

        if (!StringUtils.hasText(normalizedMethod)) {
            throw new IllegalArgumentException("paymentMethod is required");
        }
        if (normalizedChannel.contains("wx") || normalizedChannel.contains("wechat")) {
            return "wx_h5";
        }
        if (normalizedChannel.contains("ali")) {
            return "alipay_h5";
        }
        if (normalizedChannel.contains("bank")) {
            return "offline_bank";
        }
        if (normalizedMethod.contains("微信")) {
            return "wx_h5";
        }
        if (normalizedMethod.contains("支付宝") || normalizedMethod.contains("alipay")) {
            return "alipay_h5";
        }
        if (normalizedMethod.contains("银行卡") || normalizedMethod.contains("银行")) {
            return "offline_bank";
        }
        throw new IllegalArgumentException("unsupported payment channel");
    }
}
