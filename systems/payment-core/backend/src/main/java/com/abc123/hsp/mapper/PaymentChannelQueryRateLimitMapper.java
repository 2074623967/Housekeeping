package com.abc123.hsp.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 支付渠道查单共享限流 Mapper。
 */
public interface PaymentChannelQueryRateLimitMapper {

    /** 初始化渠道限流桶。 */
    int initBucket(@Param("channelCode") String channelCode);

    /**
     * 原子占用一个时间窗配额。
     */
    int acquire(
            @Param("channelCode") String channelCode,
            @Param("windowSeconds") int windowSeconds,
            @Param("maxQueries") int maxQueries);

    /** 记录渠道查单限流拒绝事实。 */
    int insertRejection(@Param("rejectionNo") String rejectionNo,
                        @Param("channelCode") String channelCode,
                        @Param("paymentOrderId") String paymentOrderId,
                        @Param("windowSeconds") int windowSeconds,
                        @Param("maxQueries") int maxQueries);
}
