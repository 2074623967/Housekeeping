package com.abc123.hsp.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 支付提交共享限流 Mapper。
 */
public interface PaymentSubmitRateLimitMapper {

    /** 初始化限流桶。 */
    int initBucket(@Param("scopeType") String scopeType,
                   @Param("scopeKey") String scopeKey);

    /** 原子占用一个固定时间窗配额。 */
    int acquire(@Param("scopeType") String scopeType,
                @Param("scopeKey") String scopeKey,
                @Param("windowSeconds") int windowSeconds,
                @Param("maxRequests") int maxRequests);
}
