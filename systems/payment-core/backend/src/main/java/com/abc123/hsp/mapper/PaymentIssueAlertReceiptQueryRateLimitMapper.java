package com.abc123.hsp.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 告警供应商回执查询共享限流 Mapper。
 */
public interface PaymentIssueAlertReceiptQueryRateLimitMapper {

    /** 初始化通道限流桶。 */
    int initBucket(@Param("channelCode") String channelCode);

    /** 原子占用一个固定时间窗配额。 */
    int acquire(@Param("channelCode") String channelCode,
                @Param("windowSeconds") int windowSeconds,
                @Param("maxQueries") int maxQueries);

    /** 记录告警回执查询限流拒绝事实。 */
    int insertRejection(@Param("rejectionNo") String rejectionNo,
                        @Param("channelCode") String channelCode,
                        @Param("alertNo") String alertNo,
                        @Param("windowSeconds") int windowSeconds,
                        @Param("maxQueries") int maxQueries);
}
