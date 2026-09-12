package com.abc123.hsp.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 告警供应商派发共享限流 Mapper。
 */
public interface PaymentIssueAlertDeliveryRateLimitMapper {

    /** 初始化供应商与通道维度的限流桶。 */
    int initBucket(@Param("providerCode") String providerCode,
                   @Param("channelCode") String channelCode);

    /** 原子占用一个固定时间窗派发额度。 */
    int acquire(@Param("providerCode") String providerCode,
                @Param("channelCode") String channelCode,
                @Param("windowSeconds") int windowSeconds,
                @Param("maxDeliveries") int maxDeliveries);
}
