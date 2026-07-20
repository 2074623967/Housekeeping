package com.abc123.hsp.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 支付回调安全数据访问接口。
 */
public interface PaymentCallbackSecurityMapper {

    /**
     * 查询渠道回调验签密钥。
     */
    String findCallbackSecretByChannelCode(@Param("channelCode") String channelCode);

    /**
     * 插入回调 nonce，依赖唯一索引保证防重放。
     */
    void insertCallbackNonce(@Param("channelCode") String channelCode,
                             @Param("nonce") String nonce,
                             @Param("paymentOrderId") String paymentOrderId,
                             @Param("expireSeconds") long expireSeconds);

    /**
     * 清理已过期的 nonce 记录。
     */
    int deleteExpiredNonce();
}
