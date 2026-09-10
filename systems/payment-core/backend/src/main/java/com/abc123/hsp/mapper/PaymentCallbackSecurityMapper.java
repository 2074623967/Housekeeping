package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.PaymentCallbackSecurityProfileDTO;
import org.apache.ibatis.annotations.Param;

/**
 * 支付回调安全数据访问接口。
 */
public interface PaymentCallbackSecurityMapper {

    /**
     * 查询渠道回调验签安全配置。
     */
    PaymentCallbackSecurityProfileDTO findCallbackSecurityProfileByChannelCode(@Param("channelCode") String channelCode);

    /**
     * 插入回调 nonce，依赖唯一索引保证防重放。
     */
    void insertCallbackNonce(@Param("channelCode") String channelCode,
                             @Param("nonce") String nonce,
                             @Param("bizType") String bizType,
                             @Param("bizNo") String bizNo,
                             @Param("expireSeconds") long expireSeconds);

    /**
     * 清理当前渠道已过期的同名 nonce，允许过期后按协议重新使用。
     */
    int deleteExpiredNonce(@Param("channelCode") String channelCode,
                           @Param("nonce") String nonce);

    /**
     * 按有限批次清理历史过期 nonce。
     */
    int deleteExpiredNonceBatch(@Param("batchSize") int batchSize);
}
