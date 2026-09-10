package com.abc123.hsp.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 退款渠道查单共享限流 Mapper。
 */
public interface RefundChannelQueryRateLimitMapper {

    int initBucket(@Param("channelCode") String channelCode);

    int acquire(@Param("channelCode") String channelCode,
                @Param("windowSeconds") int windowSeconds,
                @Param("maxQueries") int maxQueries);

    int insertRejection(@Param("rejectionNo") String rejectionNo,
                        @Param("channelCode") String channelCode,
                        @Param("refundOrderId") String refundOrderId,
                        @Param("windowSeconds") int windowSeconds,
                        @Param("maxQueries") int maxQueries);
}
