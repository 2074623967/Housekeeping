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
}
